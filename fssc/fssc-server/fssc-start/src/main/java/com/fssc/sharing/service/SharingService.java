// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.compliance.service.FraudDetectionService;
import com.fssc.compliance.service.SmartReviewService;
import com.fssc.compliance.service.SmartReviewService.ReviewResult;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.invoice.entity.InvBillInvoice;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.framework.facade.InvoiceDomainFacade;
import com.fssc.sharing.entity.OpAuditTask;
import com.fssc.sharing.mapper.OpAuditTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享运营派工服务。
 * 流程：审批通过 → createChain（按业务规则生成作业链，首环节 WAITING 可认领）→ receive 认领 → finish 完成（自动激活下一环节）。
 * 作业链（业务规则表）：
 *   员工费用报销 CLAIM/CLAIM_TRAVEL：AUDIT 票据审核/制度校验 → PAY 付款审核 → BOOKKEEP 记账归档
 *   对公付款 PREPAY/SETTLE/AGENCY/AIR：AUDIT 单据发票三单匹配 → PAY 付款 → BOOKKEEP 记账
 *   员工借款 LOAN：AUDIT 合规审核 → PAY 资金支付 → BOOKKEEP 账务处理
 *   员工还款 REPAY：AUDIT 收款核对 → BOOKKEEP 冲销账务归档
 *   事前申请/合同订立/预算调整：不进共享中心（不生成任务）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharingService {

    private final OpAuditTaskMapper opAuditTaskMapper;
    private final ExpBillLineMapper expBillLineMapper;
    private final InvoiceDomainFacade invoiceFacade;
    private final com.fssc.expense.mapper.ExpBillMapper expBillMapper;
    private final SmartReviewService smartReviewService;
    private final FraudDetectionService fraudDetectionService;
    private final DeliveryService deliveryService;

    /** 按业务类型生成共享作业链（仅纳入共享中心的单据调用）。返回首环节任务ID。 */
    public Long createChain(ExpBill bill, String bizType) {
        java.util.List<String[]> chain = chainOf(bizType);
        if (chain.isEmpty()) {
            return null;
        }

        // ★ 智能审单：审批通过进入审核环节前，先跑规则引擎自动裁决
        ReviewResult review = runSmartReview(bill);
        if ("AUTO_REJECT".equals(review.getAction())) {
            // 高风险/欺诈：自动驳回，不入作业池
            log.warn("智能审单自动驳回 billNo={}, 原因={}", bill.getBillNo(), review.getRemark());
            updateBillRejected(bill.getId(), "智能审单自动驳回：" + review.getRemark());
            return null;
        }

        Long firstId = buildChain(bill, chain);

        // BUG-08 修复：电子发票单据工单激活 —— 关联发票全部为电子介质时自动豁免实物签收
        // （置 paperless=Y / deliveryStatus=NOT_REQUIRED），首环节工单创建即 activeFlag=Y 可认领
        if (!"Y".equals(bill.getPaperless()) && allInvoicesElectronic(bill.getId())) {
            ExpBill upd = new ExpBill();
            upd.setId(bill.getId());
            upd.setPaperless("Y");
            upd.setDeliveryStatus("NOT_REQUIRED");
            expBillMapper.updateById(upd);
            bill.setPaperless("Y");
            log.info("单据 {} 关联发票均为电子介质，自动无纸化豁免实物签收", bill.getBillNo());
        }

        // V1.4.92：档案非集中管理公司整段实物流不发生（原件留原单位），单据侧记录 NOT_REQUIRED。
        // 走 registerIfNeeded 的既有豁免分支，不另写一套状态写入。
        if (deliveryService.isNonCentralArchive(bill.getCompanyId())) {
            deliveryService.registerIfNeeded(bill);
        }

        // P0-5 实物流门控（TC12）：无纸化单据免签收；需实物且未签收 → 登记签收单并挂起首环节
        boolean sendable = deliveryService.canEnterSharedPool(bill.getId());
        if (!sendable) {
            deliveryService.registerIfNeeded(bill);
            // 首环节审核任务保持不可认领，等待实物签收后由 DeliveryService.sign 激活
            OpAuditTask first = opAuditTaskMapper.selectById(firstId);
            if (first != null) {
                OpAuditTask deact = new OpAuditTask();
                deact.setId(first.getId());
                deact.setActiveFlag("N");
                opAuditTaskMapper.updateById(deact);
            }
            log.info("单据 {} 需实物签收，共享作业链挂起等待签收", bill.getBillNo());
            return firstId;
        }

        // 自动通过：首环节（AUDIT）智能审单直接通过，激活下一环节，等价于"90%自动审核"
        if ("AUTO_PASS".equals(review.getAction())) {
            OpAuditTask first = opAuditTaskMapper.selectById(firstId);
            if (first != null) {
                autoFinishTask(first, "智能审单自动通过");
            }
        }
        return firstId;
    }

    /** 单据关联发票是否全部为电子介质（invoiceKind=ELECTRONIC、票种 电子发票/数电铁路票/电子客票，或数电票特征：20 位票号且无发票代码）。无关联发票返回 false（保守按需实物）。 */
    private boolean allInvoicesElectronic(Long billId) {
        try {
            List<Long> invIds = invoiceFacade.findLinksByBillId(billId)
                    .stream().map(InvBillInvoice::getInvoiceId).filter(java.util.Objects::nonNull).distinct().toList();
            if (invIds.isEmpty()) return false;
            List<InvInvoice> invs = invoiceFacade.findByIds(invIds);
            if (invs.isEmpty()) return false;
            return invs.stream().allMatch(this::electronicMedia);
        } catch (Exception ex) {
            log.warn("发票介质判定失败(按需实物处置): {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 单张发票是否电子介质。invoice_type 只表达专票/普票抵扣属性（数电票也解析为 VAT_SPECIAL/VAT_NORMAL），
     * 不能用于判定介质；数电票（全电发票）的权威特征是 20 位发票号码且无发票代码。
     */
    private boolean electronicMedia(InvInvoice iv) {
        if ("ELECTRONIC".equalsIgnoreCase(iv.getInvoiceKind())) return true;
        String type = iv.getInvoiceType() == null ? "" : iv.getInvoiceType().toUpperCase();
        if (type.equals("ELECTRONIC") || type.equals("RAIL_HIGH") || type.equals("AIR")) return true;
        String no = iv.getInvoiceNo() == null ? "" : iv.getInvoiceNo().trim();
        String code = iv.getInvoiceCode() == null ? "" : iv.getInvoiceCode().trim();
        return no.length() == 20 && no.chars().allMatch(java.lang.Character::isDigit) && code.isEmpty();
    }

    /** 组装审单输入并调用智能审单引擎。 */
    private ReviewResult runSmartReview(ExpBill bill) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("billId", bill.getId());
            data.put("billNo", bill.getBillNo());
            data.put("billType", bill.getBillType());
            data.put("totalAmount", bill.getTotalAmount() == null ? BigDecimal.ZERO : bill.getTotalAmount());
            // P1 冒烟发现：审单规则表达式（op_review_rule.match_expression）按惯例用 amount，
            // 缺该键会让 `amount > 50000` 求值异常（求值引擎 fail-open 返回 true）误杀单据，补别名
            data.put("amount", bill.getTotalAmount() == null ? BigDecimal.ZERO : bill.getTotalAmount());
            data.put("applyUserId", bill.getApplyUserId());
            data.put("expenseType", bill.getBillType());

            List<ExpBillLine> lines = expBillLineMapper.selectList(
                    new LambdaQueryWrapper<ExpBillLine>().eq(ExpBillLine::getBillId, bill.getId()));
            data.put("itemCount", lines.size());
            boolean overStd = lines.stream().anyMatch(l -> l.getOverStd() != null && l.getOverStd() == 1);
            data.put("overStd", overStd);

            long invCnt = invoiceFacade.countActiveLinksByBillId(bill.getId());
            data.put("hasInvoice", invCnt > 0);

            boolean fraudHigh = fraudDetectionService.listByBill(bill.getId()).stream()
                    .anyMatch(f -> "HIGH".equals(f.getDetectionResult()));
            data.put("fraudHigh", fraudHigh);

            return smartReviewService.executeReview(bill.getId(), bill.getBillNo(), data);
        } catch (Exception e) {
            log.warn("智能审单执行异常（降级为人工审核）billNo={}: {}", bill.getBillNo(), e.getMessage());
            ReviewResult fallback = new ReviewResult();
            fallback.setAction("MANUAL_REVIEW");
            fallback.setRiskLevel("LOW");
            fallback.setRemark("审单引擎异常，转人工审核");
            return fallback;
        }
    }

    /** 生成作业链（首环节 WAITING 可认领）。 */
    private Long buildChain(ExpBill bill, java.util.List<String[]> chain) {
        Long firstId = null;
        int total = chain.size();
        for (int i = 0; i < total; i++) {
            String[] seg = chain.get(i); // {taskType, taskName}
            // R5-D-242：链生成去重——同 bill_id+task_type 已有未最终状态（WAITING/RECEIVED）任务即跳过重建
            // （此前 3 张单各出现两套 AUDIT/PAY/BOOKKEEP，认领量/SLA/质量报表分母虚高；
            //  DDL 唯一索引 uk(bill_id,task_type,chain_seq,del_flag) 已注释态待放行 M-20）
            Long dup = opAuditTaskMapper.selectCount(new LambdaQueryWrapper<OpAuditTask>()
                    .eq(OpAuditTask::getBillId, bill.getId())
                    .eq(OpAuditTask::getTaskType, seg[0])
                    .in(OpAuditTask::getPoolStatus, "WAITING", "RECEIVED"));
            if (dup != null && dup > 0) {
                log.info("作业链环节已存在，幂等跳过: billNo={} taskType={}", bill.getBillNo(), seg[0]);
                if (i == 0 && firstId == null) {
                    OpAuditTask exist = opAuditTaskMapper.selectOne(new LambdaQueryWrapper<OpAuditTask>()
                            .eq(OpAuditTask::getBillId, bill.getId())
                            .eq(OpAuditTask::getTaskType, seg[0])
                            .in(OpAuditTask::getPoolStatus, "WAITING", "RECEIVED")
                            .orderByAsc(OpAuditTask::getChainSeq).last("limit 1"));
                    if (exist != null) {
                        firstId = exist.getId();
                    }
                }
                continue;
            }
            OpAuditTask task = new OpAuditTask();
            task.setTenantId(TenantContext.getTenantId());
            task.setBillId(bill.getId());
            task.setBillNo(bill.getBillNo());
            task.setBillType(bill.getBillType());
            task.setTitle(seg[1]);
            task.setAmount(bill.getTotalAmount());
            task.setDeptId(bill.getDeptId());
            task.setApplyUserId(bill.getApplyUserId());
            task.setTaskType(seg[0]);
            task.setPoolStatus("WAITING");
            task.setChainSeq(i + 1);
            task.setChainTotal(total);
            task.setActiveFlag(i == 0 ? "Y" : "N"); // 首环节立即可认领
            task.setPriority(5);
            task.setSlaTime(LocalDateTime.now().plusHours(24));
            task.setCreateTime(LocalDateTime.now());
            opAuditTaskMapper.insert(task);
            if (i == 0) {
                firstId = task.getId();
            }
        }
        return firstId;
    }

    /**
     * TRC-E：exp_bill.audit_time（共享审核时刻）建表以来从未赋值。
     * 语义按 DDL 注释＝共享中心 AUDIT 环节完成时刻，与"审批通过"（approve_time）是两个事件，
     * 故只在 taskType=AUDIT 落 FINISHED 时写；智能审单自动通过与人工完成同走此口子。
     */
    private void markAuditTime(OpAuditTask task) {
        if (task == null || task.getBillId() == null || !"AUDIT".equals(task.getTaskType())) {
            return;
        }
        com.fssc.expense.entity.ExpBill cur = expBillMapper.selectById(task.getBillId());
        if (cur == null || cur.getAuditTime() != null) {
            return;
        }
        com.fssc.expense.entity.ExpBill upd = new com.fssc.expense.entity.ExpBill();
        upd.setId(task.getBillId());
        upd.setAuditTime(LocalDateTime.now());
        // D-DEC-179：audit_user_id 同刻随审核人落值；自动完成环节无认领人时留空不冒充
        if (task.getAssigneeId() != null) {
            upd.setAuditUserId(task.getAssigneeId());
        }
        expBillMapper.updateById(upd);
    }

    /** 自动完成某作业环节并激活下一环节（无需人工认领）。 */
    private void autoFinishTask(OpAuditTask task, String remark) {
        OpAuditTask upd = new OpAuditTask();
        upd.setId(task.getId());
        upd.setPoolStatus("FINISHED");
        upd.setFinishTime(LocalDateTime.now());
        upd.setRemark(remark);
        opAuditTaskMapper.updateById(upd);
        markAuditTime(task);
        OpAuditTask next = opAuditTaskMapper.selectOne(new LambdaQueryWrapper<OpAuditTask>()
                .eq(OpAuditTask::getBillId, task.getBillId())
                .eq(OpAuditTask::getChainSeq, task.getChainSeq() + 1)
                .last("limit 1"));
        if (next != null) {
            OpAuditTask act = new OpAuditTask();
            act.setId(next.getId());
            act.setActiveFlag("Y");
            opAuditTaskMapper.updateById(act);
        }
    }

    /**
     * ⑨（D-V4R-49，2026-10-16 晚间场实测）：单据到达业务最终状态时，办结其共享作业链上所有未办结任务。
     *
     * <p>缺陷现场：BX202610160002 已 APPROVED（凭证已过账、借款冲抵已核销），派工池仍挂着
     * 「审核/待认领」——全库 11 条池任务全部是最终状态单上的僵尸行（0 认领 0 办结），且
     * {@link #createChain} 的去重查重（R5-D-242，只认 WAITING/RECEIVED）会让这些残留
     * 永久占位。根因：业务侧的「钱/账/核销」闭环（付款批次执行、还款入账、无现金环节平账、
     * 驳回/撤回）与共享作业链之间没有任何联动办结点。
     *
     * <p>办结规则：WAITING/RECEIVED 一并置 FINISHED（已认领未办结的说明办理人已接手，
     * 单据最终状态后继续办理已无业务意义，remark 记录原因与原备注）。已 FINISHED 的不动。
     * 不激活任何下一环节——办结即整链终止。
     *
     * @return 实际办结的任务条数（供调用方记录）
     */
    public int closeOnBillTerminal(Long billId, String reason) {
        if (billId == null) {
            return 0;
        }
        List<OpAuditTask> open = opAuditTaskMapper.selectList(new LambdaQueryWrapper<OpAuditTask>()
                .eq(OpAuditTask::getBillId, billId)
                .in(OpAuditTask::getPoolStatus, "WAITING", "RECEIVED"));
        if (open.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (OpAuditTask t : open) {
            OpAuditTask upd = new OpAuditTask();
            upd.setId(t.getId());
            upd.setPoolStatus("FINISHED");
            upd.setFinishTime(now);
            String remark = reason + "（单据结束时自动办结）";
            if (t.getRemark() != null && !t.getRemark().isBlank()) {
                remark = remark + "；原备注：" + t.getRemark();
            }
            upd.setRemark(remark);
            opAuditTaskMapper.updateById(upd);
        }
        log.info("单据 billId={} 到达业务终态，共享作业链自动收口 {} 条未办结任务：{}",
                billId, open.size(), reason);
        return open.size();
    }

    /** 智能审单驳回：回写单据状态为 REJECTED。 */
    private void updateBillRejected(Long billId, String remark) {
        com.fssc.expense.entity.ExpBill upd = new com.fssc.expense.entity.ExpBill();
        upd.setId(billId);
        upd.setStatus("REJECTED");
        upd.setAuditRemark(remark);
        expBillMapper.updateById(upd);
    }

    /** 业务类型 → 共享作业链（按业务规则表）。空列表 = 不进共享中心。 */
    private java.util.List<String[]> chainOf(String bizType) {
        java.util.List<String[]> chain = new java.util.ArrayList<>();
        switch (bizType) {
            case "EXP_CLAIM" -> {
                chain.add(new String[]{"AUDIT", "票据审核/制度校验"});
                chain.add(new String[]{"PAY", "付款审核"});
                chain.add(new String[]{"BOOKKEEP", "记账归档"});
            }
            case "EXP_PAY" -> {
                chain.add(new String[]{"AUDIT", "单据发票三单匹配"});
                chain.add(new String[]{"PAY", "付款"});
                chain.add(new String[]{"BOOKKEEP", "记账"});
            }
            case "EXP_LOAN" -> {
                chain.add(new String[]{"AUDIT", "合规审核"});
                chain.add(new String[]{"PAY", "资金支付"});
                chain.add(new String[]{"BOOKKEEP", "账务处理"});
            }
            case "EXP_REPAY" -> {
                chain.add(new String[]{"AUDIT", "收款核对"});
                chain.add(new String[]{"BOOKKEEP", "冲销账务归档"});
            }
            default -> { /* 事前申请/合同订立/预算调整：不进共享中心 */ }
        }
        return chain;
    }

    /** 审批通过后自动生成审核派工单（兼容旧调用，仅报销单单环节，保留） */
    public Long createAuditTask(ExpBill bill) {
        OpAuditTask task = new OpAuditTask();
        task.setTenantId(TenantContext.getTenantId());
        task.setBillId(bill.getId());
        task.setBillNo(bill.getBillNo());
        task.setBillType(bill.getBillType());
        task.setTitle("票据审核/制度校验");
        task.setAmount(bill.getTotalAmount());
        task.setDeptId(bill.getDeptId());
        task.setApplyUserId(bill.getApplyUserId());
        task.setTaskType("AUDIT");
        task.setPoolStatus("WAITING");
        task.setChainSeq(1);
        task.setChainTotal(1);
        task.setActiveFlag("Y");
        task.setPriority(5);
        task.setSlaTime(LocalDateTime.now().plusHours(24));
        task.setCreateTime(LocalDateTime.now());
        opAuditTaskMapper.insert(task);
        return task.getId();
    }

    /** 认领（入池任务 → 归属当前用户） */
    public void receive(Long taskId) {
        LoginUser u = UserContext.getRequired();
        OpAuditTask task = require(taskId);
        if (!"WAITING".equals(task.getPoolStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅待认领任务可认领，当前: " + task.getPoolStatus());
        }
        if (!"Y".equals(task.getActiveFlag())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该作业环节前序尚未完成，暂不可认领");
        }
        OpAuditTask update = new OpAuditTask();
        update.setId(taskId);
        update.setAssigneeId(u.getUserId());
        update.setPoolStatus("RECEIVED");
        update.setReceiveTime(LocalDateTime.now());
        opAuditTaskMapper.updateById(update);
    }

    /** 完成（审核通过；串行作业链自动激活下一环节）。旧签名保留，等价于无备注办结。 */
    public void finish(Long taskId) {
        finish(taskId, null);
    }

    /**
     * 完成（审核通过；串行作业链自动激活下一环节）。
     * D-DEC-024/D-DEC-030：办结备注（审计口语）必须落 op_audit_task.remark——此前请求不带备注、
     * 服务端也不写列，三张工单（AUDIT/PAY/BOOKKEEP）办结后 remark 恒 NULL，记录全丢。
     * 备注为选填：空白不覆盖既有值（避免把退单原因等历史记录擦掉）。
     */
    public void finish(Long taskId, String remark) {
        LoginUser u = UserContext.getRequired();
        OpAuditTask task = require(taskId);
        if (!"RECEIVED".equals(task.getPoolStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已认领任务可完成，当前: " + task.getPoolStatus());
        }
        if (!task.getAssigneeId().equals(u.getUserId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED, "只能完成自己认领的任务");
        }
        String note = normalizeFinishRemark(remark);
        OpAuditTask update = new OpAuditTask();
        update.setId(taskId);
        update.setPoolStatus("FINISHED");
        update.setFinishTime(LocalDateTime.now());
        if (note != null) {
            update.setRemark(note);
        }
        opAuditTaskMapper.updateById(update);
        markAuditTime(task);
        // 串行作业链：激活下一环节
        Integer seq = task.getChainSeq() == null ? 1 : task.getChainSeq();
        OpAuditTask next = opAuditTaskMapper.selectOne(new LambdaQueryWrapper<OpAuditTask>()
                .eq(OpAuditTask::getBillId, task.getBillId())
                .eq(OpAuditTask::getChainSeq, seq + 1)
                .last("limit 1"));
        if (next != null) {
            OpAuditTask act = new OpAuditTask();
            act.setId(next.getId());
            act.setActiveFlag("Y");
            opAuditTaskMapper.updateById(act);
        }
    }

    /** 任务池：WAITING 待认领（仅激活环节） */
    public PageResult<OpAuditTask> pool(long pageNum, long pageSize) {
        Page<OpAuditTask> p = opAuditTaskMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<OpAuditTask>()
                        .eq(OpAuditTask::getPoolStatus, "WAITING")
                        .eq(OpAuditTask::getActiveFlag, "Y")
                        .orderByAsc(OpAuditTask::getPriority).orderByAsc(OpAuditTask::getId));
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 我的任务：已认领未完成 */
    public PageResult<OpAuditTask> mine(long pageNum, long pageSize) {
        LoginUser u = UserContext.getRequired();
        Page<OpAuditTask> p = opAuditTaskMapper.selectPage(Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<OpAuditTask>()
                        .eq(OpAuditTask::getAssigneeId, u.getUserId())
                        .eq(OpAuditTask::getPoolStatus, "RECEIVED")
                        .orderByAsc(OpAuditTask::getId));
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 任务池管理（需求 8.2.2）：全量任务，支持状态/类型/关键字筛选；R5-D-236 增 activeFlag（未激活值域）+ R5-D-237 回填单据状态 */
    public PageResult<OpAuditTask> manage(long pageNum, long pageSize, String poolStatus, String taskType,
                                          String keyword, String activeFlag) {
        LambdaQueryWrapper<OpAuditTask> w = new LambdaQueryWrapper<>();
        if (poolStatus != null && !poolStatus.isBlank()) w.eq(OpAuditTask::getPoolStatus, poolStatus);
        if (taskType != null && !taskType.isBlank()) w.eq(OpAuditTask::getTaskType, taskType);
        // R5-D-236：状态筛选值域补「未激活（待实物签收）」——前端以 activeFlag=N 定位该值域
        if (activeFlag != null && !activeFlag.isBlank()) w.eq(OpAuditTask::getActiveFlag, activeFlag);
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(OpAuditTask::getBillNo, keyword).or().like(OpAuditTask::getTitle, keyword));
        }
        // 排序：最新任务排在最前面（按创建时间降序 + ID降序）
        w.orderByDesc(OpAuditTask::getCreateTime).orderByDesc(OpAuditTask::getId);
        Page<OpAuditTask> p = opAuditTaskMapper.selectPage(Page.of(pageNum, pageSize), w);
        // R5-D-237：分页 VO 补 billStatus（UI「单据状态」列此前全「—」，同页同批办结）
        enrichBillStatus(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** R5-D-237：批量回填关联单据状态（仅出参，零加列） */
    private void enrichBillStatus(List<OpAuditTask> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> billIds = rows.stream().map(OpAuditTask::getBillId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (billIds.isEmpty()) {
            return;
        }
        Map<Long, String> statusMap = expBillMapper.selectBatchIds(billIds).stream()
                .collect(java.util.stream.Collectors.toMap(ExpBill::getId,
                        b -> b.getStatus() == null ? "" : b.getStatus(), (a, b) -> a));
        for (OpAuditTask r : rows) {
            if (r.getBillId() != null) {
                r.setBillStatus(statusMap.get(r.getBillId()));
            }
        }
    }

    /** 撤回（需求 8.2.2）：已认领 → 回到任务池（WAITING），清除认领人 */
    public void withdraw(Long taskId) {
        OpAuditTask task = require(taskId);
        if (!"RECEIVED".equals(task.getPoolStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已认领任务可撤回，当前: " + task.getPoolStatus());
        }
        // 必须用 UpdateWrapper 显式 set null：updateById 的实体在默认 NOT_NULL 字段策略下会跳过 null 字段，
        // 表现是"撤回成功、状态回池了，但认领人还挂在原人名下"（我的任务列表继续显示、抢单统计也错）。
        opAuditTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update
                .LambdaUpdateWrapper<OpAuditTask>()
                .set(OpAuditTask::getPoolStatus, "WAITING")
                .set(OpAuditTask::getAssigneeId, null)
                .set(OpAuditTask::getReceiveTime, null)
                .eq(OpAuditTask::getId, taskId));
    }

    /** 重新分配（需求 8.2.2）：改派给指定作业员（assigneeId），状态保持 RECEIVED */
    public void reassign(Long taskId, Long assigneeId) {
        OpAuditTask task = require(taskId);
        // 必填办结（批次 2C，与前端 TaskPool「分配给人」同一规则）：留空会把任务置为 RECEIVED 却无人认领
        AssertUtil.notNull(assigneeId, ErrorCode.PARAM_INVALID, "请选择作业员");
        if (task.getPoolStatus().equals("FINISHED")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已办结任务不能重新分配");
        }
        OpAuditTask update = new OpAuditTask();
        update.setId(taskId);
        update.setAssigneeId(assigneeId);
        update.setPoolStatus("RECEIVED");
        update.setReceiveTime(LocalDateTime.now());
        opAuditTaskMapper.updateById(update);
    }

    /** 退单（需求 8.2.3）：工单处理人退回业务组任务池（RECEIVED → WAITING + 退单备注） */
    public void returnTask(Long taskId, String reason) {
        OpAuditTask task = require(taskId);
        if (!"RECEIVED".equals(task.getPoolStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已认领任务可退单，当前: " + task.getPoolStatus());
        }
        // R5-D-235：退单原因必填双校验（与前端红星+行内校验同一规则）——此前直连接口可空原因退单
        AssertUtil.notBlank(reason, ErrorCode.PARAM_INVALID, "请填写退单原因");
        // 与 withdraw 同一规则（R5-D-234）：updateById 的实体在默认 NOT_NULL 字段策略下会跳过 null 字段，
        // 表现是"退单成功、状态回池了，但认领人/认领时间还挂在原人名下"（任务池同行同时显示「待认领」＋认领人）。
        opAuditTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update
                .LambdaUpdateWrapper<OpAuditTask>()
                .set(OpAuditTask::getPoolStatus, "WAITING")
                .set(OpAuditTask::getAssigneeId, null)
                .set(OpAuditTask::getReceiveTime, null)
                .set(OpAuditTask::getRemark, "退单: " + reason.trim())
                .eq(OpAuditTask::getId, taskId));
    }

    /**
     * 办结备注归一（D-DEC-024/D-DEC-030）：去首尾空白、加「办结: 」前缀与「退单: 」记录区分；
     * op_audit_task.remark 为 varchar(500)，超限明确拒绝而不是静默截断（截断会让审计口语变味）。
     */
    private String normalizeFinishRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String t = remark.trim();
        if (t.isEmpty()) {
            return null;
        }
        String note = "办结: " + t;
        if (note.length() > 500) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "完成备注过长（含前缀上限 500 字），当前 " + note.length() + " 字，请精简后重试");
        }
        return note;
    }

    private OpAuditTask require(Long taskId) {
        OpAuditTask task = opAuditTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "派工单不存在: " + taskId);
        }
        return task;
    }
}
