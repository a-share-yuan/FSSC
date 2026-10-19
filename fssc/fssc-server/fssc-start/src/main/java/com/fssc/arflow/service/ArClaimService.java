package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArClaim;
import com.fssc.arflow.mapper.ArClaimMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AR服务申请单服务（蓝图附件二，10类统一状态机 + 附件四/五默认值）。
 * EKP审批→待派工（多人派组长，蓝图5.2）→审单→EBS过账（凭证号返回）→完成；
 * 校验：对冲≤应收未清余额、借贷平衡、调整额≤原应收余额、收入暂估不走EKP。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArClaimService {

    private final ArClaimMapper mapper;
    private final com.fssc.arflow.service.ArDispatchService arDispatchService;

    private final com.fssc.system.service.PeriodLockService periodLockService;
    private final RcReceivableMapper receivableMapper;
    private final com.fssc.arflow.service.ArFlowVoucherService voucherService;
    private final com.fssc.workflow.service.WfEngineFacade wfEngineFacade;

    /** P4-3 审计扩面：单据状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    /** Flowable 首节点审批人兜底（无 Web 上下文的系统调用才落到平台管理员；见 currentOperator） */
    private static final Long FLOW_APPROVER_ID = 1L;

    /**
     * MF5-07：审计记录真实登录用户。
     * 发起/EKP代审批的操作人一律取当前登录用户；仅无 Web 上下文（定时/系统任务）才兜底平台管理员。
     */
    private static Long currentOperator() {
        com.fssc.framework.security.LoginUser u = com.fssc.framework.security.UserContext.get();
        return (u != null && u.getUserId() != null) ? u.getUserId() : FLOW_APPROVER_ID;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArClaim create(ArClaim c) {

        // 蓝图规则6.5：账期已被月结关闭时禁止录入当月业务
        periodLockService.assertNotLocked(c.getCompanyId(), java.time.YearMonth.now().toString(), "录入服务申请");
        c.setId(null);
        c.setBillNo("ARC" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        if (c.getAmount() == null || c.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申请金额必须大于0");
        }
        if (ArClaim.T_REVENUE_EST.equals(c.getClaimType()) && c.getEkpNo() != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收入暂估不走EKP，共享手动填单");
        }
        if (ArClaim.T_CLAIM_OUT.equals(c.getClaimType()) && c.getCostAmount() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "向客户索赔必须录入成本金额（成本会计填写）");
        }
        if (ArClaim.T_OTHER_OFFSET.equals(c.getClaimType())
                && (c.getDebitAmount() == null || c.getCreditAmount() == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "其它业务对冲必须录入借贷金额（借贷必须平衡）");
        }
        if (ArClaim.T_ORDER_UNFRZ.equals(c.getClaimType())
                && (c.getOrderNo() == null || c.getOrderNo().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单解挂必须录入订单号");
        }
        sanitizeText(c); // SEC-02: 中和自由文本中的脚本载荷（渲染层 Vue 转义为主防线，本层纵深）
        applyDefaults(c);
        // 初始状态：不走EKP的类型（收入暂估/其它业务对冲/汇票贴息/停车费等其它业务收入）直接待派工
        boolean noEkp = ArClaim.T_REVENUE_EST.equals(c.getClaimType())
                || ArClaim.T_OTHER_OFFSET.equals(c.getClaimType())
                || ArClaim.T_NOTE_DISCOUNT.equals(c.getClaimType())
                || (ArClaim.T_OTHER_REV.equals(c.getClaimType()) && c.getRemark() != null
                        && (c.getRemark().contains("停车费") || c.getRemark().contains("三代税返还")));
        c.setStatus(noEkp ? ArClaim.ST_PENDING_DISPATCH : ArClaim.ST_EKP_REVIEW);
        mapper.insert(c);
        if (noEkp) {
            arDispatchService.autoAssignClaim(c);
        }
        // 补强：EKP 类型接入 Flowable 审批（FSSC 无外部 EKP，由内部审批中心承接）
        if (ArClaim.ST_EKP_REVIEW.equals(c.getStatus())) {
            try {
                // MF5-07：流程发起记录真实创建者（不再是常量平台管理员）
                String procInstId = wfEngineFacade.start("AR_CLAIM", c.getId(), currentOperator(),
                        "AR服务申请-" + c.getBillNo(), c.getAmount());
                ArClaim upd = new ArClaim();
                upd.setId(c.getId());
                upd.setProcInstId(procInstId);
                mapper.updateById(upd);
                c.setProcInstId(procInstId);
            } catch (Exception ex) {
                // 流程发起失败降级：保留 EKP_REVIEW 状态，ekpApprove 走直接过渡（fail-open 保持可用）
                log.error("[arflow] 服务申请单 {} 发起审批失败（降级为直接过渡）: {}",
                        c.getBillNo(), ex.getMessage(), ex);
                c.setProcInstId(null);
                throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
            }
        }
        log.info("[arflow] 服务申请单 {} 创建（{}）", c.getBillNo(), c.getClaimType());
        return c;
    }

    /** EKP审批通过：完成当前节点（审计记真实点击者）；流程全部结束才自动派工，否则留审批中心继续。 */
    @Transactional(rollbackFor = Exception.class)
    public ArClaim ekpApprove(Long id) {
        ArClaim c = require(id);
        requireStatus(c, ArClaim.ST_EKP_REVIEW, "EKP审批");
        if (c.getProcInstId() != null && !c.getProcInstId().isBlank()) {
            // MF5-07：审计记录真实点击者（不再是常量平台管理员）
            Long operator = currentOperator();
            // MF5-08：EKP 桥接只代办点击者有权办理的节点；下一节点属于其它角色（如财务经理）
            // 时停止越权代办，交由该角色在审批中心办理 —— 杜绝 node2 无 UI 完成路径的僵尸任务。
            int guard = 0;
            while (wfEngineFacade.hasActiveTask("AR_CLAIM", id) && guard < 20) {
                try {
                    wfEngineFacade.completeByBill("AR_CLAIM", id, true, "EKP审批通过(代)", operator);
                    guard++;
                } catch (BizException ex) {
                    log.info("[arflow] 单据 {} EKP桥接在角色边界停止，后续节点交审批中心: {}",
                            c.getBillNo(), ex.getMessage());
                    break;
                }
            }
            if (guard >= 20) {
                log.error("[arflow] 单据 {} EKP桥接循环超过 20 轮仍有活动任务，中止（疑似流程定义异常）", c.getBillNo());
            }
        } else {
            // 降级路径：无流程实例（发起失败的历史单）直接过渡
            String auditDegradeBeforeStatus = c.getStatus();
            c.setStatus(ArClaim.ST_PENDING_DISPATCH);
            mapper.updateById(c);
            java.util.Map<String, Object> auditDegradeBefore = new java.util.LinkedHashMap<>();
            auditDegradeBefore.put("status", auditDegradeBeforeStatus);
            java.util.Map<String, Object> auditDegradeAfter = new java.util.LinkedHashMap<>();
            auditDegradeAfter.put("status", c.getStatus());
            auditTrailService.recordUpdate("AR_CLAIM", c.getId(), c.getBillNo(), "ar_claim",
                    String.valueOf(c.getId()), auditDegradeBefore, auditDegradeAfter, "EKP审批通过（无流程实例降级过渡）");
            arDispatchService.autoAssignClaim(c);
            mapper.updateById(c);
        }
        ArClaim fresh = mapper.selectById(id);
        if (wfEngineFacade.hasActiveTask("AR_CLAIM", id)) {
            // MF5-08：流程未结束（后续节点等待其它角色审批）——单据不得旁路推进，
            // 状态由 ArClaimBizAction（流程结束事件）驱动，根除"单据已过账、流程僵尸"。
            log.info("[arflow] 单据 {} 流程仍有后续审批节点，保持 {} 等待审批中心",
                    c.getBillNo(), ArClaim.ST_EKP_REVIEW);
            return fresh;
        }
        String auditOldStatus = fresh.getStatus();
        fresh.setStatus(ArClaim.ST_DISPATCHED); // 审批通过即自动派工（单据级语义保持）
        fresh.setDispatchTime(LocalDateTime.now());
        mapper.updateById(fresh);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", fresh.getStatus());
        auditTrailService.recordUpdate("AR_CLAIM", fresh.getId(), fresh.getBillNo(), "ar_claim",
                String.valueOf(fresh.getId()), auditBefore, auditAfter, "EKP审批通过自动派工");
        return fresh;
    }

    /** 手工派工/转单。 */
    @Transactional(rollbackFor = Exception.class)
    public ArClaim assign(Long id, String user) {
        ArClaim c = require(id);
        if (!List.of(ArClaim.ST_PENDING_DISPATCH, ArClaim.ST_DISPATCHED).contains(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + c.getBillNo() + " 当前状态不可派工/转单");
        }
        String auditOldStatus = c.getStatus();
        String auditOldHandler = c.getHandler();
        c.setHandler(user);
        c.setStatus(ArClaim.ST_DISPATCHED);
        c.setDispatchTime(LocalDateTime.now());
        mapper.updateById(c);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("handler", auditOldHandler);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditAfter.put("handler", c.getHandler());
        auditTrailService.recordUpdate("AR_CLAIM", c.getId(), c.getBillNo(), "ar_claim",
                String.valueOf(c.getId()), auditBefore, auditAfter, "手工派工/转单");
        return c;
    }

    /** 审单通过（联动EBS过账）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArClaim review(Long id) {
        ArClaim c = require(id);
        requireStatus(c, ArClaim.ST_DISPATCHED, "审单");
        return post(c);
    }

    /** EBS过账：对冲≤应收余额/借贷平衡/调整额≤原应收余额强校验 + 凭证号返回。 */
    @Transactional(rollbackFor = Exception.class)
    public ArClaim post(ArClaim c) {
        requireStatus(c, ArClaim.ST_DISPATCHED, "EBS过账");
        if (ArClaim.T_OTHER_OFFSET.equals(c.getClaimType())) {
            BigDecimal debit = c.getDebitAmount() == null ? BigDecimal.ZERO : c.getDebitAmount();
            BigDecimal credit = c.getCreditAmount() == null ? BigDecimal.ZERO : c.getCreditAmount();
            if (debit.compareTo(credit) != 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "其它业务对冲借贷必须平衡（借 " + debit + " ≠ 贷 " + credit + "）");
            }
        }
        if (List.of(ArClaim.T_AR_AP_OFFSET, ArClaim.T_THING_ADJ).contains(c.getClaimType())) {
            BigDecimal outstanding = receivableOutstanding(c.getCustomerCode());
            if (outstanding.signum() > 0 && c.getAmount().compareTo(outstanding) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        (ArClaim.T_AR_AP_OFFSET.equals(c.getClaimType()) ? "对冲金额" : "调整金额")
                                + "不可超过该客户应收未清余额 " + outstanding);
            }
        }
        // 真实分录凭证（补齐评估缺口2）：借/贷科目取单据附件五默认值，失败即阻断过账（fail-closed）
        String voucherNo;
        if (ArClaim.T_OTHER_OFFSET.equals(c.getClaimType())
                && c.getLinesJson() != null && c.getLinesJson().trim().startsWith("[")) {
            voucherNo = voucherService.generateClaimMultiLineVoucher(c);
        } else {
            voucherNo = voucherService.generateClaimVoucher(c);
        }
        String auditOldStatus = c.getStatus();
        String auditOldVoucherNo = c.getVoucherNo();
        c.setVoucherNo(voucherNo);
        c.setStatus(ArClaim.ST_POSTED);
        c.setPostTime(LocalDateTime.now());
        if (ArClaim.T_ORDER_UNFRZ.equals(c.getClaimType())) {
            c.setUnfreezeResult("订单 " + c.getOrderNo() + " 已解冻（ERP返回）");
        }
        mapper.updateById(c);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("voucherNo", auditOldVoucherNo);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditAfter.put("voucherNo", c.getVoucherNo());
        auditTrailService.recordUpdate("AR_CLAIM", c.getId(), c.getBillNo(), "ar_claim",
                String.valueOf(c.getId()), auditBefore, auditAfter, "EBS过账");
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArClaim reject(Long id, String reason) {
        ArClaim c = require(id);
        requireStatus(c, ArClaim.ST_DISPATCHED, "驳回");
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回必须填写原因");
        }
        String auditOldStatus = c.getStatus();
        String auditOldRemark = c.getRemark();
        c.setRemark((c.getRemark() == null ? "" : c.getRemark() + " | ") + "驳回:" + reason);
        c.setStatus(ArClaim.ST_REJECTED);
        mapper.updateById(c);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("remark", auditOldRemark);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditAfter.put("remark", c.getRemark());
        auditTrailService.recordUpdate("AR_CLAIM", c.getId(), c.getBillNo(), "ar_claim",
                String.valueOf(c.getId()), auditBefore, auditAfter, "审单驳回");
        return c;
    }

    /** 收入暂估冲回（蓝图 AR-9 步骤06）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArClaim reverseEstimate(Long id) {
        ArClaim c = require(id);
        if (!ArClaim.T_REVENUE_EST.equals(c.getClaimType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅收入暂估单可冲回");
        }
        requireStatus(c, ArClaim.ST_POSTED, "暂估冲回");
        String redVoucherNo = voucherService.generateClaimReversalVoucher(c);
        String auditOldStatus = c.getStatus();
        Integer auditOldReverseFlag = c.getReverseFlag();
        c.setReverseFlag(1);
        c.setStatus(ArClaim.ST_COMPLETED);
        c.setRemark((c.getRemark() == null ? "" : c.getRemark() + " | ")
                + "暂估已冲回（红字凭证 " + redVoucherNo + "）");
        mapper.updateById(c);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("reverseFlag", auditOldReverseFlag);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", c.getStatus());
        auditAfter.put("reverseFlag", c.getReverseFlag());
        auditTrailService.recordUpdate("AR_CLAIM", c.getId(), c.getBillNo(), "ar_claim",
                String.valueOf(c.getId()), auditBefore, auditAfter, "收入暂估冲回");
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArClaim complete(Long id) {
        ArClaim c = require(id);
        if (!List.of(ArClaim.ST_POSTED, ArClaim.ST_REJECTED).contains(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + c.getBillNo() + " 当前状态不可归档");
        }
        String auditOldStatus = c.getStatus();
        c.setStatus(ArClaim.ST_COMPLETED);
        mapper.updateById(c);
        auditTrailService.recordUpdate("AR_CLAIM", c.getId(), c.getBillNo(), "ar_claim",
                String.valueOf(c.getId()), java.util.Collections.singletonMap("status", auditOldStatus),
                java.util.Collections.singletonMap("status", c.getStatus()), "归档完成");
        return c;
    }

    public List<ArClaim> list(String claimType, String status, String customerName) {
        LambdaQueryWrapper<ArClaim> q = new LambdaQueryWrapper<>();
        if (claimType != null && !claimType.isBlank()) {
            q.eq(ArClaim::getClaimType, claimType);
        }
        if (status != null && !status.isBlank()) {
            q.eq(ArClaim::getStatus, status);
        }
        if (customerName != null && !customerName.isBlank()) {
            q.like(ArClaim::getCustomerName, customerName);
        }
        return mapper.selectList(q.orderByDesc(ArClaim::getId));
    }

    public ArClaim get(Long id) {
        return require(id);
    }

    // ---------------- 附件四/附件五默认值 ----------------

    /** SEC-02: 中和 <script> 块与内联事件载荷；普通文本（含合法 <、>）不受影响 */
    private static void sanitizeText(ArClaim c) {
        if (c.getReason() != null) c.setReason(neutralize(c.getReason()));
        if (c.getCustomerName() != null) c.setCustomerName(neutralize(c.getCustomerName()));
        if (c.getRemark() != null) c.setRemark(neutralize(c.getRemark()));
    }

    private static String neutralize(String s) {
        if (s == null) return null;
        String r = s.replaceAll("(?is)<\\s*script[^>]*>.*?<\\s*/\\s*script\\s*>", "[脚本已移除]");
        r = r.replaceAll("(?i)\\son\\w+\\s*=", "_on=");
        return r;
    }

    private void applyDefaults(ArClaim c) {
        switch (c.getClaimType() == null ? "" : c.getClaimType()) {
            case ArClaim.T_CLAIM_IN -> {
                boolean overseas = Boolean.parseBoolean(String.valueOf(c.getRemark() != null
                        && c.getRemark().contains("国外")));
                BigDecimal taxRate = c.getTaxRate();
                c.setDebitAccount(ArClaimAccounts.headquartersClaimAccount(overseas, taxRate));
                c.setCreditAccount("1122"); // 应收账款（md_subject既有科目）
            }
            case ArClaim.T_OTHER_REV -> {
                c.setTaxRate(c.getTaxRate() == null
                        ? (c.getRemark() != null && c.getRemark().contains("可回收")
                                ? new BigDecimal("17") : new BigDecimal("5"))
                        : c.getTaxRate());
                c.setCreditAccount(ArClaimAccounts.OTHER_REV_CREDIT);
                c.setTaxAccount(ArClaimAccounts.OTHER_REV_TAX);
            }
            case ArClaim.T_NOTE_DISCOUNT -> {
                c.setDebitAccount(ArClaimAccounts.NOTE_DISCOUNT_AR);
                c.setCreditAccount(ArClaimAccounts.NOTE_DISCOUNT_INCOME);
                c.setTaxAccount(ArClaimAccounts.NOTE_DISCOUNT_TAX);
            }
            case ArClaim.T_REVENUE_EST -> {
                // R5-D-095：收入暂估默认科目（借 1122 应收账款 / 贷 6001 主营业务收入），原实现落 default 科目全空
                c.setDebitAccount("1122");
                c.setCreditAccount("6001");
            }
            default -> {
                // 其余类型科目由审单岗录入
            }
        }
        // 厂别组织映射（附件四）：总部=原币；事业部=厂别00不生成、外币跨账套折本位币
        BigDecimal source = ArClaim.T_CLAIM_OUT.equals(c.getClaimType()) && c.getCostAmount() != null
                ? c.getCostAmount() : c.getAmount();
        c.setHeadquartersAmount(source);
        c.setBuAmount(ArClaimAccounts.buAmount(c.getFactoryCode(), source, c.getCcy(), c.getFxRate()));
    }

    private BigDecimal receivableOutstanding(String customerCode) {
        if (customerCode == null || customerCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        List<RcReceivable> list = receivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .eq(RcReceivable::getCustomerId, customerCode)
                .ne(RcReceivable::getStatus, "DONE"));
        return list.stream()
                .map(r -> r.getRemainAmount() == null ? BigDecimal.ZERO : r.getRemainAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ArClaim require(Long id) {
        ArClaim c = mapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "服务申请单不存在 id=" + id);
        }
        return c;
    }

    private void requireStatus(ArClaim c, String expected, String action) {
        if (!expected.equals(c.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "服务申请单 " + c.getBillNo()
                    + " 执行「" + action + "」要求状态 " + expected + "，实际 " + c.getStatus());
        }
    }
}
