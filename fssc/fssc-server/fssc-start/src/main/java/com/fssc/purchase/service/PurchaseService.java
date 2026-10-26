// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.entity.PoOrderLine;
import com.fssc.purchase.entity.PoReceipt;
import com.fssc.purchase.mapper.PoOrderLineMapper;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.purchase.mapper.PoReceiptMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购三单域服务：采购订单（PO）+ 收货单（GRN）。
 * P1：采购订单创建走订单审批（PTP02-02 PURCHASE_ORDER_REVIEW），创建后自动发起流程。
 * 收货单挂 PO（supplier/amount），创建时回写 PO 累计收货金额；收货 ≤ 订单剩余校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PoOrderMapper orderMapper;
    private final PoOrderLineMapper orderLineMapper;
    private final PoReceiptMapper receiptMapper;
    /**  作废校验：下游报销/付款单挂 PO（exp_bill.po_id）引用检查 */
    private final com.fssc.expense.mapper.ExpBillMapper expBillMapper;
    private final WfEngineFacade wfEngineFacade;
    private final com.fssc.framework.org.OrgGuard orgGuard;
    private final com.fssc.system.service.NumberRuleService numberRuleService;
    /** P1-1: @Lazy 避免与资产域循环依赖（AssetDeprBatchService 间接依赖 PurchaseService 的情况） */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.fssc.asset.service.MdAssetService assetService;
    /** 收货成功自动生成暂估凭证（VoucherService 内部 REQUIRES_NEW + 全量默认处理，不阻断收货主流程） */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.fssc.sharing.service.VoucherService voucherService;
    /** 收货挂 CIP 时自动归集在建工程成本（@Lazy 避免经 MdAssetService 与资产域循环依赖） */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.fssc.masterdata.service.MdCipService mdCipService;
    /** 采购订单挂 CIP 的存在性校验（md_cip 主数据） */
    private final com.fssc.masterdata.mapper.MdCipMapper cipMapper;
    /** P4-3 审计扩面：单据状态跃迁记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;
    /** 收货→存货移动台账（stock_move）写入位置 */
    private final com.fssc.asset.mapper.StockMoveMapper stockMoveMapper;
    /** R5-D-070：开票回写超收货额时派 INVOICE_MATCH 异常单（ThreeWayMatchService 侧复用 #17 派单链）。
     *  @Lazy 打断 purchase ↔ sharing 潜在循环依赖（sharing 侧亦装配本域 Mapper 与 WfEngineFacade）。 */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.fssc.sharing.service.ThreeWayMatchService threeWayMatchService;

    // ---------------- 采购订单 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(PoOrder o) {
        orgGuard.assertCompanyAllowed(o.getCompanyId());
        validateSupplier(o);
        assertOrderHeaderRequired(o);
        // 前端传 cipId 时保存到订单，校验在建工程主数据存在
        validateCip(o.getCipId());
        o.setId(null);
        o.setTenantId(TenantContext.getTenantId());
        if (o.getPoNo() == null || o.getPoNo().isBlank()) o.setPoNo(nextPoNo());
        if (o.getStatus() == null) o.setStatus("OPEN");
        if (o.getReceivedAmount() == null) o.setReceivedAmount(BigDecimal.ZERO);
        o.setCreateBy(userId());
        o.setCreateTime(LocalDateTime.now());
        o.setUpdateTime(LocalDateTime.now());
        o.setDelFlag(0);
        // 多分子公司审批路由 V1.3.17：前端已传不覆盖，否则取当前登录用户所属公司
        if (o.getCompanyId() == null) {
            com.fssc.framework.security.LoginUser lu = UserContext.get();
            if (lu != null) o.setCompanyId(lu.getCompanyId());
        }
        // 公司维度必填（放在登录人默认处理之后）：采购入库/三单匹配/付款都要认账簿，
        // 集团岗未挂公司时无法推断，必须由建单人显式选择法人主体。
        if (o.getCompanyId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择法人主体（公司不能为空）");
        }
        orderMapper.insert(o);
        BigDecimal sumLine = saveLines(o.getId(), o.getTenantId(), o.getLines());
        assertHeaderLineAmountConsistent(o, sumLine);
        // P1：采购订单审批（PTP02-02），amount 传入供"金额>200000 总经理审批"分支。
        // 显式 DRAFT（草稿）不发起审批——草稿仅占号，可编辑/删除，提交后再走审批。
        boolean draft = "DRAFT".equals(o.getStatus());
        if (!draft) {
            try {
                wfEngineFacade.start("PURCHASE_ORDER_REVIEW", o.getId(), userId(),
                        "采购订单审批-" + o.getPoNo(), o.getAmount());
            } catch (Exception ex) {
                log.error("采购订单 {} 发起审批失败: {}", o.getId(), ex.getMessage(), ex);
                throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
            }
        } else {
            log.info("采购订单 {} 保存为草稿（DRAFT），未发起审批", o.getPoNo());
        }
        return o.getId();
    }

    /**
     * 删除采购订单：仅草稿(DRAFT)且无下游关联才允许，逻辑删除（del_flag=1）。
     * 校验：存在下游收货单 / 已有累计收货金额 → 拦截；非 DRAFT 状态 → 拦截。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long id) {
        PoOrder o = orderMapper.selectById(id);
        if (o == null || (o.getDelFlag() != null && o.getDelFlag() == 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在: " + id);
        }
        if (!"DRAFT".equals(o.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅草稿(DRAFT)状态订单可删除，当前状态: " + o.getStatus());
        }
        long receipts = receiptMapper.selectCount(new LambdaQueryWrapper<PoReceipt>().eq(PoReceipt::getPoId, id));
        if (receipts > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "订单已存在 " + receipts + " 张下游收货单，不可删除");
        }
        if (o.getReceivedAmount() != null && o.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "订单已有累计收货金额 " + o.getReceivedAmount() + "，不可删除");
        }
        PoOrder upd = new PoOrder();
        upd.setId(id);
        upd.setDelFlag(1);
        // D-DEC-051：删除也是状态写点，操作人必须写入数据库（草稿删除只可能来自登录用户，取不到即上下文异常）
        upd.setUpdateBy(userId());
        upd.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(upd);
        log.info("采购订单 {} 已逻辑删除（DRAFT）", o.getPoNo());
    }

    /**
     * 作废采购订单：无作废能力补齐（区别于草稿删除 deleteOrder，作废保留 del_flag=0 可追溯）。
     * 校验：订单存在；未发生已收货（po_receipt 引用）或下游付款关联（报销/付款单挂 PO、累计收货金额、
     * PAID/CLOSED/ARCHIVED/INVOICED 状态）则允许，否则拒绝并返回明确错误。
     * 通过后 status 置 'VOID'（与现有状态机 OPEN/APPROVED/PAID/CLOSED/ARCHIVED 同为状态字段取值），
     * remark 追加作废原因+时间+操作人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidOrder(Long orderId, String reason) {
        PoOrder o = orderMapper.selectById(orderId);
        if (o == null || (o.getDelFlag() != null && o.getDelFlag() == 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在: " + orderId);
        }
        if ("VOID".equals(o.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单 " + o.getPoNo() + " 已作废，请勿重复操作");
        }
        long receipts = receiptMapper.selectCount(new LambdaQueryWrapper<PoReceipt>().eq(PoReceipt::getPoId, orderId));
        if (receipts > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "订单已存在 " + receipts + " 张下游收货单，不可作废");
        }
        if (o.getReceivedAmount() != null && o.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "订单已有累计收货金额 " + o.getReceivedAmount() + "，不可作废");
        }
        String status = o.getStatus();
        if ("PAID".equals(status) || "CLOSED".equals(status) || "ARCHIVED".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "订单已付款/关账/归档（当前状态: " + status + "），不可作废");
        }
        if ("INVOICED".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "订单已进入开票/三单匹配（当前状态: INVOICED），不可作废");
        }
        // 下游报销/付款单挂此 PO（付款关联）：存在即拒绝
        Long downBills = expBillMapper.selectCount(new LambdaQueryWrapper<com.fssc.expense.entity.ExpBill>()
                .eq(com.fssc.expense.entity.ExpBill::getPoId, orderId));
        if (downBills != null && downBills > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "订单已被 " + downBills + " 张下游报销/付款单引用，不可作废");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作废原因必填");
        }
        // remark 追加：作废原因 + 时间 + 操作人（remark 上限 500，超长截断保护）
        String operator = "unknown";
        try {
            com.fssc.framework.security.LoginUser lu = UserContext.get();
            if (lu != null) {
                operator = lu.getRealName() == null || lu.getRealName().isBlank()
                        ? lu.getUsername() : lu.getRealName();
            }
        } catch (Exception ignored) { /* 无用户上下文（系统操作） */ }
        String suffix = "；【作废】原因：" + reason.trim() + "；操作人：" + operator
                + "；时间：" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String baseRemark = o.getRemark() == null ? "" : o.getRemark();
        String newRemark = baseRemark + suffix;
        if (newRemark.length() > 500) {
            newRemark = newRemark.substring(newRemark.length() - 500);
        }
        PoOrder upd = new PoOrder();
        upd.setId(orderId);
        upd.setStatus("VOID");
        upd.setRemark(newRemark);
        upd.setUpdateBy(userId());
        upd.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(upd);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", o.getStatus());
        auditBefore.put("remark", o.getRemark());
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "VOID");
        auditAfter.put("remark", newRemark);
        auditTrailService.recordUpdate("PURCHASE", orderId, o.getPoNo(), "po_order",
                String.valueOf(orderId), auditBefore, auditAfter, "作废采购订单：" + reason.trim());
        // R5-D-068 最终状态收尾（D-DEC-334 规则「直改状态型单据收尾须终止流程」）：作废只改单据态不终止流程，
        // 会让 wf_instance 滞留 RUNNING、wf_task 滞留 PENDING，催办扫描持续打扰（实测 po_order 14/15 作废后
        // 实例 130/131 仍 RUNNING）。此处同批终止该 biz_type+biz_id 的 RUNNING 实例并把 PENDING 待办置
        // CANCELLED；与发起审批同一规则：流程收尾失败必须冒泡（ROOT-2），事务整体回滚。
        wfEngineFacade.cancelRunningInstancesByBiz("PURCHASE_ORDER_REVIEW", orderId,
                "采购订单作废（" + o.getPoNo() + "）：" + reason.trim());
        log.info("采购订单 {} 已作废（原因: {}，操作人: {}）", o.getPoNo(), reason.trim(), operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(PoOrder o) {
        orgGuard.assertCompanyAllowed(o.getCompanyId());
        PoOrder exist = orderMapper.selectById(o.getId());
        if (exist != null) {
            orgGuard.assertVisible(exist.getCompanyId(), exist.getCreateBy());
        }
        // X-20 最终状态机守卫：最终状态单（VOID/CLOSED/ARCHIVED）是收尾真相，整单编辑不得把它改回生效态。
        if (exist != null && ("VOID".equals(exist.getStatus())
                || "CLOSED".equals(exist.getStatus()) || "ARCHIVED".equals(exist.getStatus()))) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "订单已终止/关账/归档（当前状态: " + exist.getStatus() + "），不可再编辑");
        }
        // 状态跃迁只走专用入口（提交审批/审批回写/收货回写/批处理最终状态/终止/归档）：
        // 整单编辑把客户端传来的 status 置空，MP NOT_NULL 策略即跳过该列，堵住「编辑直接把状态写成 CLOSED」的后门。
        o.setStatus(null);
        validateSupplier(o);
        assertOrderHeaderRequired(o);
        // 前端传 cipId 时保存到订单，校验在建工程主数据存在（传 null 不清空既有挂接）
        if (o.getCipId() != null) {
            validateCip(o.getCipId());
        }
        o.setUpdateBy(userId());
        o.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(o);
        // lines=null（老调用方不带行）时不动既有行；带行则整批重登（逻辑删除 + 重插）
        BigDecimal sumLine = saveLines(o.getId(), exist == null ? null : exist.getTenantId(), o.getLines());
        assertHeaderLineAmountConsistent(o, sumLine);
    }

    /**
     * 草稿采购订单提交审批：DRAFT → OPEN + 发起 PURCHASE_ORDER_REVIEW。
     * 补齐批次1 断链：草稿单此前只能靠新建时非 DRAFT 才起流程，驳回/草稿单没有任何提交出口。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitOrder(Long orderId) {
        PoOrder o = orderMapper.selectById(orderId);
        if (o == null || (o.getDelFlag() != null && o.getDelFlag() == 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在: " + orderId);
        }
        if (!"DRAFT".equals(o.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿采购订单可提交，当前状态: " + o.getStatus());
        }
        assertOrderHeaderRequired(o);
        if (o.getSupplierName() == null || o.getSupplierName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商必填");
        }
        if (wfEngineFacade.hasActiveTask("PURCHASE_ORDER_REVIEW", orderId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请勿重复提交：该采购订单已有在办审批");
        }
        PoOrder upd = new PoOrder();
        upd.setId(orderId);
        upd.setStatus("OPEN");
        upd.setUpdateBy(userId());
        upd.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(upd);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", o.getStatus());
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "OPEN");
        auditTrailService.recordUpdate("PURCHASE", orderId, o.getPoNo(), "po_order",
                String.valueOf(orderId), auditBefore, auditAfter, "提交审批");
        try {
            wfEngineFacade.start("PURCHASE_ORDER_REVIEW", orderId, userId(),
                    "采购订单审批-" + o.getPoNo(), o.getAmount());
        } catch (Exception ex) {
            log.error("采购订单 {} 发起审批失败: {}", orderId, ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡（事务回滚，状态还原 DRAFT）
        }
    }

    /** 批次1：驳回采购订单编辑重提 REJECTED → DRAFT（修改后经 submitOrder 重新起流程，对齐合同/费用单规则） */
    @Transactional(rollbackFor = Exception.class)
    public void returnToEditOrder(Long orderId) {
        PoOrder o = orderMapper.selectById(orderId);
        if (o == null || (o.getDelFlag() != null && o.getDelFlag() == 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在: " + orderId);
        }
        if (!"REJECTED".equals(o.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已驳回采购订单可编辑重提，当前状态: " + o.getStatus());
        }
        PoOrder upd = new PoOrder();
        upd.setId(orderId);
        upd.setStatus("DRAFT");
        upd.setUpdateBy(userId());
        upd.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(upd);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", o.getStatus());
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "DRAFT");
        auditTrailService.recordUpdate("PURCHASE", orderId, o.getPoNo(), "po_order",
                String.valueOf(orderId), auditBefore, auditAfter, "驳回后编辑重提");
    }

    /** 收货环节唯一可挂的订单状态：审批通过(APPROVED)与已开票(INVOICED，允许分批续收) */
    private static final java.util.Set<String> RECEIVABLE_PO_STATUS = java.util.Set.of("APPROVED", "INVOICED");

    public PageResult<PoOrder> orderPage(long pageNum, long pageSize, String keyword, String status) {
        return orderPage(pageNum, pageSize, keyword, status, false);
    }

    /** @param receivableOnly 收货选单用：只出「审批通过/已开票且有剩余可收金额」的订单 */
    public PageResult<PoOrder> orderPage(long pageNum, long pageSize, String keyword, String status,
                                         boolean receivableOnly) {
        LambdaQueryWrapper<PoOrder> qw = new LambdaQueryWrapper<>();
        // 逻辑删除行不出现在列表（PoOrder 未用 @TableLogic，显式过滤）
        qw.eq(PoOrder::getDelFlag, 0);
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(PoOrder::getPoNo, keyword).or().like(PoOrder::getSupplierName, keyword));
        }
        if (status != null && !status.isBlank()) qw.eq(PoOrder::getStatus, status);
        if (receivableOnly) {
            qw.in(PoOrder::getStatus, RECEIVABLE_PO_STATUS);
            qw.apply("amount > IFNULL(received_amount, 0)");
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(PoOrder::getCompanyId, scopeC);
        }
        qw.orderByDesc(PoOrder::getId);
        Page<PoOrder> p = orderMapper.selectPage(Page.of(pageNum, pageSize), qw);
        attachLines(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 保存订单行项目：整批重登（既有有效行逻辑删除，再按本次提交重插）。
     * 与全系统"更正不物理删"的规则一致——改行留历史版本，供事后追溯"当初下的是什么"。
     */
    private BigDecimal saveLines(Long poId, Long tenantId, List<PoOrderLine> lines) {
        if (poId == null || lines == null) {
            return BigDecimal.ZERO;
        }
        if (!lines.isEmpty()) {
            orderLineMapper.update(null, new LambdaUpdateWrapper<PoOrderLine>()
                    .eq(PoOrderLine::getPoId, poId)
                    .set(PoOrderLine::getDelFlag, 1)
                    .set(PoOrderLine::getUpdateBy, userId())
                    .set(PoOrderLine::getUpdateTime, LocalDateTime.now()));
        }
        int lineNo = 0;
        BigDecimal sumAmount = BigDecimal.ZERO;
        for (PoOrderLine line : lines) {
            if (line == null || isBlankLine(line)) {
                continue;
            }
            lineNo++;
            // D-V4R-91：逐行硬校验。原先本方法只跳过"全空行"，对半填的行照单全收——
            // 自动化脚本把 60000 填进「物料名称」、数量/单价留默认值(1/0) 时，
            // materialName 非空即判为有效行，写入数据库出 amount=0 的垃圾数据，
            // 且表头 60000 与行合计 0 的勾稽断裂一路放过，直到三单匹配才暴露。
            assertOrderLine(line, lineNo);
            line.setId(null);
            line.setPoId(poId);
            line.setTenantId(tenantId);
            line.setLineNo(lineNo);
            line.setDelFlag(0);
            if (line.getAmount() == null) {
                line.setAmount(nz(line.getQuantity()).multiply(nz(line.getUnitPrice())));
            }
            sumAmount = sumAmount.add(nz(line.getAmount()));
            orderLineMapper.insert(line);
        }
        log.info("采购订单 {} 行项目落库 {} 行", poId, lineNo);
        return sumAmount;
    }

    /**
     * D-V4R-91：行项目逐行校验（与前端 PoOrder.vue 的行内红星同一规则，后端为最终校验）。
     * 四道断言：
     * ① 物料名称必填 —— 只填了物料编码也算缺（名称是打印/对账/匹配的规则来源）
     * ② 物料名称不得为纯数字 —— 防"金额误填进名称字段"这类字段串位
     * ③ 数量 > 0、单价 > 0 —— 原规则允许 0，直通"行金额恒为 0"
     * ④ 量价勾稽：数量 × 单价 必须等于行金额（容差 0.01），防三者各填各的
     */
    private void assertOrderLine(PoOrderLine line, int lineNo) {
        String pos = "第 " + lineNo + " 行";
        if (isBlank(line.getMaterialName())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    pos + "物料名称必填（只有物料编码不够，行项目名称是打印与对账的依据）");
        }
        String name = line.getMaterialName().trim();
        if (name.matches("^[0-9][0-9,]*(\\.[0-9]+)?$")) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    pos + "物料名称「" + name + "」是纯数字，疑似把数量/金额填进了名称栏，请填实际品名");
        }
        if (nz(line.getQuantity()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, pos + "数量必须大于 0（当前 "
                    + line.getQuantity() + "）");
        }
        if (nz(line.getUnitPrice()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, pos + "单价必须大于 0（当前 "
                    + line.getUnitPrice() + "），无价物料请走费用报销而非采购订单");
        }
        BigDecimal expect = nz(line.getQuantity()).multiply(nz(line.getUnitPrice()));
        BigDecimal actual = nz(line.getAmount());
        if (actual.compareTo(BigDecimal.ZERO) <= 0) {
            actual = expect; // 金额未填时按量价推导后再判勾稽，下方 saveLines 会落该值
            line.setAmount(expect);
        }
        if (expect.subtract(actual).abs().compareTo(LINE_AMT_TOLERANCE) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    pos + "量价勾稽不符：数量 × 单价 = " + expect.stripTrailingZeros().toPlainString()
                            + "，行金额 = " + actual.stripTrailingZeros().toPlainString()
                            + "（允许尾差 ≤ " + LINE_AMT_TOLERANCE.toPlainString() + "）");
        }
    }

    /** 列表/详情回填行项目：一次 IN 查询按订单分组，不做 N+1。 */
    private void attachLines(List<PoOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> ids = orders.stream().map(PoOrder::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, List<PoOrderLine>> byPo = orderLineMapper.selectList(
                        new LambdaQueryWrapper<PoOrderLine>()
                                .in(PoOrderLine::getPoId, ids)
                                .orderByAsc(PoOrderLine::getLineNo))
                .stream().collect(Collectors.groupingBy(PoOrderLine::getPoId));
        orders.forEach(o -> o.setLines(byPo.getOrDefault(o.getId(), new ArrayList<>())));
    }

    /** 空行 = 前端"添加行"后未填任何内容，不写入数据库。 */
    private boolean isBlankLine(PoOrderLine line) {
        return isBlank(line.getMaterialCode()) && isBlank(line.getMaterialName())
                && nz(line.getQuantity()).compareTo(BigDecimal.ZERO) == 0
                && nz(line.getAmount()).compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 收货单挂 PO 时校验供应商一致 + 订单审批状态 + 收货未超订单剩余 */
    private void validateReceiptAgainstOrder(PoReceipt r, PoOrder o) {
        if (o == null) return;
        // ROOT-6 / C-9（P0 控制缺口）：此前收货只看供应商和余额，已作废/驳回/尚在审批中的订单照样能收货，
        // 于是「下单→(审批断链)→收货→暂估应付→三单匹配→付款」全程不要求任何人批过。
        if (!RECEIVABLE_PO_STATUS.contains(o.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单 " + o.getPoNo() + " 当前状态 "
                    + o.getStatus() + " 不可收货：仅审批通过(APPROVED)/已开票(INVOICED)的订单允许收货");
        }
        if (!r.getSupplierName().equals(o.getSupplierName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收货供应商与订单供应商不一致");
        }
        BigDecimal remain = o.getAmount().subtract(nz(o.getReceivedAmount()));
        if (r.getAmount().compareTo(remain) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "收货超订单：订单 " + o.getPoNo() + " 剩余可收 " + remain + " < 本次收货 " + r.getAmount());
        }
    }

    // ---------------- 收货单 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Long createReceipt(PoReceipt r) {
        validateSupplier(r);
        // 与前端 PoReceipt.vue 红星同一规则：收货日期、收货金额>0（金额未填时 DDL 默认 0.00，
        // 会静默落一张零金额收货单并回写订单累计收货，故必须在前置就拦）。收货单号留空自动取号，不列入必填。
        if (r.getReceiptDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择收货日期");
        }
        if (r.getAmount() == null || r.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入收货金额（须大于 0）");
        }
        // 送货单号是自由文本且非必填，前端无长度约束时会超长直落 varchar(200) → 数据库截断异常 500
        String deliveryNote = r.getDeliveryNote() == null ? null : r.getDeliveryNote().trim();
        if (deliveryNote != null && deliveryNote.length() > 200) {
            throw new BizException(ErrorCode.PARAM_INVALID, "送货单号过长（最多 200 字符）");
        }
        r.setDeliveryNote(deliveryNote);
        r.setId(null);
        r.setTenantId(TenantContext.getTenantId());
        // 留空自动取号时校验唯一键（uk_receipt_no: tenant_id+receipt_no，含 del_flag=1 历史行），
        // 撞号则递增重试（最多 5 次），避免 Duplicate entry → 500
        if (r.getReceiptNo() == null || r.getReceiptNo().isBlank()) {
            r.setReceiptNo(nextReceiptNoUnique());
        }
        if (r.getStatus() == null) r.setStatus("RECEIVED");
        if (r.getInvoiceAmount() == null) r.setInvoiceAmount(BigDecimal.ZERO);
        // 挂 PO：校验 + 回写累计收货
        PoOrder o = null;
        if (r.getPoId() != null) {
            o = orderMapper.selectById(r.getPoId());
            if (o == null) throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在: " + r.getPoId());
            r.setPoNo(o.getPoNo());
            r.setSupplierName(o.getSupplierName());
            validateReceiptAgainstOrder(r, o);
            // 收货单 cip_id 从采购订单继承（前端显式传入则以传入为准）
            if (r.getCipId() == null) {
                r.setCipId(o.getCipId());
            }
        }
        r.setCreateBy(userId());
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        r.setDelFlag(0);
        // 插入级撞号默认处理——DuplicateKey 时按编号规则重新取号重试（最多 3 次）。
        // R5-D-069：原实现在此处拼「原号+System.currentTimeMillis()」（GRN2470671790959329398、RCP1790877084488
        // 即由此产生）——单号形态不可预期、无法按前缀对账；改为重走 nextReceiptNoUnique()（rule 取号+重试）。
        for (int insertAttempt = 0; ; insertAttempt++) {
            try {
                receiptMapper.insert(r);
                break;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                if (insertAttempt >= 2) throw e;
                r.setReceiptNo(nextReceiptNoUnique());
                log.warn("收货单号撞唯一键，按编号规则 PURCHASE_RECEIPT 重取为 {}", r.getReceiptNo());
            }
        }
        if (o != null) {
            PoOrder upd = new PoOrder();
            upd.setId(o.getId());
            upd.setReceivedAmount(nz(o.getReceivedAmount()).add(r.getAmount()));
            // D-DEC-051：金额回写必须盖操作人。收货由登录人发起，此处的 update_by 就是收货经办人；
            // 原实现只写金额不写人，po_order.update_by 恒 NULL，追溯面无法回答"这笔累计收货是谁做的"。
            upd.setUpdateBy(userId());
            upd.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(upd);
            // P1-1: 投资到资产循环——资本化采购收货即自动建资产卡片(INIT/CIP)
            if (o.getCapitalFlag() != null && o.getCapitalFlag() == 1) {
                try {
                    createAssetFromReceipt(r, o);
                } catch (Exception ex) {
                    log.error("收货 {} 自动建资产卡片失败: {}", r.getReceiptNo(), ex.getMessage(), ex);
                }
            }
        }
        // 收货即写存货移动台账（stock_move）：凭证已动 1405，物的侧必须有逐笔记录
        recordReceiptStockMoves(r, o);
        // 收货挂 CIP（从订单继承）时自动归集在建工程成本（内部去重 + 全量默认处理，不阻断收货）
        if (r.getCipId() != null) {
            mdCipService.aggregateFromReceipt(r.getReceiptNo(), r.getCipId(), r.getAmount());
        }
        // 收货成功自动生成暂估凭证（借 存货1405 / 贷 暂估应付2203；去重，科目缺失/异常仅 warn 不阻断）
        // 收货单挂 CIP 时暂估凭证借方自动改走 在建工程1604（cip 随收货单对象传入凭证服务）
        try {
            voucherService.generateEstimateOnReceipt(r);
        } catch (Exception ex) {
            log.error("收货 {} 自动暂估凭证异常（已兜底，不影响收货）: {}", r.getReceiptNo(), ex.getMessage(), ex);
        }
        return r.getId();
    }

    /**
     * P1-1: 采购收货转固建卡——资本化采购订单收货后自动创建资产卡片。
     * 资产初始状态为 CIP(在建工程)，需人工确认转固(FA_CIP_TO_ASSET)后开始折旧。
     */
    private void createAssetFromReceipt(PoReceipt r, PoOrder o) {
        com.fssc.asset.entity.MdAsset asset = new com.fssc.asset.entity.MdAsset();
        asset.setAssetName("采购资产-" + o.getPoNo());
        asset.setSupplierId(o.getSupplierId());
        asset.setCompanyId(o.getCompanyId());
        asset.setPurchaseDate(r.getReceiptDate());
        asset.setOriginalAmount(r.getAmount());
        asset.setNetAmount(r.getAmount());
        asset.setResidualValue(BigDecimal.ZERO);
        asset.setDepreciationMethod("LINEAR");
        asset.setDepreciationMonths(60);
        asset.setAccumDepr(BigDecimal.ZERO);
        asset.setStatus("CIP");
        asset.setInvoiceNo(r.getReceiptNo());
        assetService.create(asset);
        log.info("收货 {} 自动建资产卡片 assetId={} (CIP, 待转固)", r.getReceiptNo(), asset.getId());
    }

    /**
     * 收货 → 存货移动台账（stock_move，move_type=RECEIVE）：此前全库 0 行，凭证已借 1405 而物侧无逐笔记录。
     * 按采购订单行逐笔落行，收货额不足整单时按行顺序分摊、末行按金额比例折算数量，保证 Σ移动额＝收货金额；
     * 订单未填行项目时整笔落一行（数量留空，由台账补录，不臆造数量）。
     * 写台账失败不阻断收货（与暂估凭证/CIP 归集同一规则）。
     */
    private void recordReceiptStockMoves(PoReceipt r, PoOrder o) {
        try {
            long existed = stockMoveMapper.selectCount(new LambdaQueryWrapper<com.fssc.asset.entity.StockMove>()
                    .eq(com.fssc.asset.entity.StockMove::getSourceDocType, "PO_RECEIPT")
                    .eq(com.fssc.asset.entity.StockMove::getSourceDocId, r.getId()));
            if (existed > 0) {
                return;
            }
            Long companyId = o == null ? null : o.getCompanyId();
            List<PoOrderLine> lines = o == null ? new ArrayList<>() : orderLineMapper.selectList(
                    new LambdaQueryWrapper<PoOrderLine>()
                            .eq(PoOrderLine::getPoId, o.getId())
                            .orderByAsc(PoOrderLine::getLineNo));
            BigDecimal remaining = nz(r.getAmount());
            int seq = 0;
            for (PoOrderLine line : lines) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal lineAmount = nz(line.getAmount());
                if (lineAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal taken = lineAmount.compareTo(remaining) <= 0 ? lineAmount : remaining;
                BigDecimal qty = nz(line.getQuantity()).multiply(taken)
                        .divide(lineAmount, 4, java.math.RoundingMode.HALF_UP);
                BigDecimal unitCost = qty.signum() > 0
                        ? taken.divide(qty, 6, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
                insertStockMove(r, companyId, ++seq, line.getMaterialCode(), line.getMaterialName(),
                        qty, unitCost, taken, null);
                remaining = remaining.subtract(taken);
            }
            if (seq == 0 || remaining.compareTo(BigDecimal.ZERO) > 0) {
                insertStockMove(r, companyId, seq + 1, null, null,
                        BigDecimal.ZERO, BigDecimal.ZERO, remaining,
                        lines.isEmpty() ? "订单未填行项目，收货数量未登记" : "订单行金额未覆盖收货额，差额整笔入账");
            }
            log.info("收货 {} 写存货移动台账 {} 行，金额 {}", r.getReceiptNo(), seq == 0 ? 1 : seq, r.getAmount());
        } catch (Exception ex) {
            log.error("收货 {} 写存货移动台账失败（不阻断收货）: {}", r.getReceiptNo(), ex.getMessage(), ex);
        }
    }

    private void insertStockMove(PoReceipt r, Long companyId, int seq, String itemCode, String itemName,
                                 BigDecimal qty, BigDecimal unitCost, BigDecimal amount, String extraRemark) {
        com.fssc.asset.entity.StockMove m = new com.fssc.asset.entity.StockMove();
        m.setMoveNo("SMR" + r.getId() + "-" + seq);
        m.setMoveType("RECEIVE");
        // stock_move.item_code 是 NOT NULL 列（现库 SHOW COLUMNS 实测），而 po_order_line.material_code 可空、
        // 且现存行绝大多数录的是<b>空串</b>而非编码——直接传 null 会让整条 insert 报
        // "Column 'item_code' cannot be null"，被外层 catch 吞掉 ⇒ 台账继续 0 行（D-DEC-057 的真实卡点）。
        // 规则：缺编码就落空串（与 po_order_line 现存数据同形态），并在 remark 如实写明"物料编码未登记"，
        // 不拿物料名称充当编码，也不臆造一个 md_item 里不存在的编码。
        m.setItemCode(itemCode == null ? "" : itemCode);
        m.setItemName(itemName);
        m.setCompanyId(companyId);
        m.setQty(qty);
        m.setUnitCost(unitCost);
        m.setAmount(amount);
        m.setSourceDocType("PO_RECEIPT");
        m.setSourceDocId(r.getId());
        m.setStatus("APPROVED");
        // D-DEC-057 库位规则：warehouse_id 需要 md_warehouse 主数据，而本库<b>查无仓库主数据表</b>
        // （SHOW TABLES LIKE '%warehouse%' 0 行），收货单上的 warehouse 只是自由文本。
        // 按 P2-B 现有规则＝留空 + 文本进 remark，不臆造仓库 id、不建默认仓库。
        m.setWarehouseId(null);
        StringBuilder rmk = new StringBuilder();
        if (itemCode == null || itemCode.isBlank()) {
            rmk.append("物料编码未登记；");
        }
        rmk.append("仓库：").append(r.getWarehouse() == null || r.getWarehouse().isBlank() ? "未填" : r.getWarehouse());
        if (extraRemark != null) {
            rmk.append("；").append(extraRemark);
        }
        m.setRemark(rmk.length() > 500 ? rmk.substring(rmk.length() - 500) : rmk.toString());
        m.setCreateBy(userId());
        m.setUpdateBy(userId());
        stockMoveMapper.insert(m);
    }

    /**
     * @param poId 可选：按采购订单收口。付款勾稽「手工关联收货单」按 PO 取候选集时必传，
     *             否则会拿到全租户收货单列表（前端只取前 100 条），存在关联错单的风险。
     */
    public PageResult<PoReceipt> receiptPage(long pageNum, long pageSize, String keyword, String status, Long poId) {
        LambdaQueryWrapper<PoReceipt> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(PoReceipt::getReceiptNo, keyword).or().like(PoReceipt::getSupplierName, keyword)
                    .or().like(PoReceipt::getPoNo, keyword).or().like(PoReceipt::getDeliveryNote, keyword));
        }
        if (status != null && !status.isBlank()) qw.eq(PoReceipt::getStatus, status);
        if (poId != null) qw.eq(PoReceipt::getPoId, poId);
        qw.orderByDesc(PoReceipt::getId);
        Page<PoReceipt> p = receiptMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 送货单号重复预检（同供应商 + 同号）。只提示不拦截：一张送货单分次收货是合法业务，
     * 故不能加唯一键；这里把已有同号单据交回前端，由录入人判断是"分次收货"还是"重复录入"。
     */
    public List<PoReceipt> findDeliveryNoteDuplicates(Long supplierId, String deliveryNote) {
        if (supplierId == null || deliveryNote == null || deliveryNote.isBlank()) return List.of();
        return receiptMapper.selectList(new LambdaQueryWrapper<PoReceipt>()
                .eq(PoReceipt::getSupplierId, supplierId)
                .eq(PoReceipt::getDeliveryNote, deliveryNote.trim())
                .orderByDesc(PoReceipt::getId)
                .last("LIMIT 20"));
    }

    /**
     * 开票回写：收货单累计开票金额。
     * <p>以勾稽明细（inv_bill_invoice ⋈ exp_bill.po_receipt_id）为真相源<b>重算绝对值</b>，
     * 使绑定/反核销/后补关联三条路径都能收敛；勾稽环节缺失时（调用方单据尚未关联收货单）退回按入参累加。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markInvoiced(Long receiptId, BigDecimal invoiceAmount) {
        PoReceipt r = receiptMapper.selectById(receiptId);
        if (r == null) return;
        // G#39：勾稽重算取不含税净额（勾稽价税合计 − 关联发票税额），与 po_receipt.amount 同尺；
        // 旧实现拿价税合计直接比不含税收货额，标准组合「净额收货＋价税开票」被恒判超收货。
        BigDecimal linked = threeWayMatchService.sumLinkedInvoiceNetAmount(receiptId);
        BigDecimal newInvoiceAmount = linked.signum() > 0 || nz(invoiceAmount).signum() == 0
                ? linked : nz(r.getInvoiceAmount()).add(invoiceAmount);
        // R5-D-070 强制校验：累计开票超出「收货额 + 容差」→ 拒绝回写（库内值保持原样）+ 审计记录 + 派 INVOICE_MATCH 异常单。
        // 为何"拒写"而不抛异常：本方法 @Transactional 且参与调用方（InvoiceService.bind/unbind 等）事务，
        // 抛出会让共享事务被标记 rollback-only，调用方 try/catch 也救不回，绑定/反核销整链回滚；
        // 而反核销恰是已超开票收货单的唯一纠偏入口，堵死它便再无收敛路径——故只拒绝本次写、不抛出。
        if (com.fssc.sharing.service.ThreeWayMatchService.invoiceExceedsReceiptCap(newInvoiceAmount, r.getAmount())) {
            boolean dispatched = false;
            try {
                dispatched = threeWayMatchService.dispatchInvoiceOverReceipt(
                        receiptId, r.getReceiptNo(), newInvoiceAmount, nz(r.getAmount())) != null;
            } catch (Exception ex) {
                // 派单失败不得反噬"拒绝回写"本身（拒绝是主诉求，派单是默认处理）
                log.error("[R5-D-070] 收货单 {} 超限派单失败（拒绝回写不受影响）: {}", r.getReceiptNo(), ex.getMessage());
            }
            java.util.Map<String, Object> refuseBefore = new java.util.LinkedHashMap<>();
            refuseBefore.put("status", r.getStatus());
            refuseBefore.put("invoiceAmount", r.getInvoiceAmount());
            auditTrailService.recordUpdate("PURCHASE", receiptId, r.getReceiptNo(), "po_receipt",
                    String.valueOf(receiptId), refuseBefore, new java.util.LinkedHashMap<>(refuseBefore),
                    "开票回写被拒（R5-D-070 强制校验）：本次应回写 " + newInvoiceAmount + " 超收货额 " + r.getAmount()
                            + " 加容差上限，未改动库内值"
                            + (dispatched ? "，已派 INVOICE_MATCH 异常单" : "（派单未发起/去重，详见日志）"));
            log.error("[R5-D-070] 收货单 {} 开票回写被拒：累计开票 {} > 收货额 {} + 容差（硬闸），未回写并已派 INVOICE_MATCH 异常单",
                    r.getReceiptNo(), newInvoiceAmount, r.getAmount());
            return;
        }
        String newStatus = r.getStatus();
        if (newInvoiceAmount.compareTo(BigDecimal.ZERO) > 0 && "RECEIVED".equals(r.getStatus())) {
            newStatus = "INVOICED";
        } else if (newInvoiceAmount.compareTo(BigDecimal.ZERO) == 0 && "INVOICED".equals(r.getStatus())) {
            newStatus = "RECEIVED";
        }
        PoReceipt upd = new PoReceipt();
        upd.setId(receiptId);
        upd.setInvoiceAmount(newInvoiceAmount);
        upd.setStatus(newStatus);
        // D-DEC-051：勾稽额与状态跃迁都是回写，必须带操作人。
        // 规则：调用方是「发票绑定/反核销/手工关联」三条人工路径（InvoiceService.bind/rebind、
        // ThreeWayMatchService.link），均有登录上下文，取 userId()；
        // 若被引擎线程调用（无 UserContext）则不伪造工号——但写入数据库值不是 NULL：PoReceipt.updateBy
        // 带 @TableField(fill = FieldFill.INSERT_UPDATE)，MP 自动填充会把 null 补成哨兵值 0，
        // 与本文件 P0-5 段注释同一规则（详见该段）。
        upd.setUpdateBy(userId());
        upd.setUpdateTime(LocalDateTime.now());
        receiptMapper.updateById(upd);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("invoiceAmount", r.getInvoiceAmount());
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", newStatus);
        auditAfter.put("invoiceAmount", newInvoiceAmount);
        auditTrailService.recordUpdate("PURCHASE", receiptId, r.getReceiptNo(), "po_receipt",
                String.valueOf(receiptId), auditBefore, auditAfter, "收货单开票回写");
    }

    /** 按 ID 查收货单（发票匹配冲回时取单号，不存在返回 null） */
    public PoReceipt receiptById(Long receiptId) {
        return receiptId == null ? null : receiptMapper.selectById(receiptId);
    }

    private void validateSupplier(PoOrder o) {
        if (o.getSupplierName() == null || o.getSupplierName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商必填");
        }
    }

    /**
     * D-V4R-91：金额勾稽尾差容差（元）。行金额与表头金额均为两位小数，
     * 逐行累加的浮点误差上界远小于 0.01，故 0.01 即为最紧且不会误伤的规则
     * （与 PurReturnService.validateLineSum / ExpenseBillService.validateAmountReconcile 同值）。
     */
    private static final BigDecimal LINE_AMT_TOLERANCE = new BigDecimal("0.01");

    /**
     * D-V4R-91：表头金额与行项目合计的一致性校验。
     * 判据：只要提交了行项目，就必须与表头订单金额对得上（容差 0.01）。
     * 无行项目时不校验 —— 采购订单允许只录表头金额（历史规则，行项目为选填）。
     *
     * 背景：表头 60000 / 行合计 0 的勾稽断裂原先无人拦截，一路同步到 BCP 门户，
     * 直到三单匹配阶段才以"匹配不上"的形式暴露，返工需重建整条单据链。
     */
    private void assertHeaderLineAmountConsistent(PoOrder o, BigDecimal sumLine) {
        if (sumLine == null || sumLine.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal header = nz(o.getAmount());
        if (header.subtract(sumLine).abs().compareTo(LINE_AMT_TOLERANCE) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "订单金额(" + header.toPlainString() + ") ≠ 行项目合计("
                            + sumLine.stripTrailingZeros().toPlainString() + ")"
                            + "（允许尾差 ≤ " + LINE_AMT_TOLERANCE.toPlainString()
                            + "）；若表头是对的，请逐行检查数量与单价是否漏填");
        }
    }

    /**
     * 采购订单表头必填守卫，与前端 PoOrder.vue 的红星同一规则（下单日期/订单金额>0/币种）。
     * 订单号是系统取号（留空走 nextPoNo），按治理规则 Q1 不列入人工必填。
     * 新增与编辑都走整表头提交（前端表单必带这三项），故两条路径同样硬拦。
     */
    private void assertOrderHeaderRequired(PoOrder o) {
        if (o.getOrderDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择下单日期");
        }
        if (o.getAmount() == null || o.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入订单金额（须大于 0）");
        }
        if (o.getCurrency() == null || o.getCurrency().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择币种");
        }
    }

    /** 采购订单挂 CIP 校验——传了 cipId 必须存在对应在建工程主数据（md_cip） */
    private void validateCip(Long cipId) {
        if (cipId == null) {
            return;
        }
        com.fssc.masterdata.entity.MdCip cip = cipMapper.selectById(cipId);
        if (cip == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "关联的在建工程不存在: " + cipId);
        }
    }

    private void validateSupplier(PoReceipt r) {
        if (r.getSupplierName() == null || r.getSupplierName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商必填");
        }
    }

    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /**
     * 收货单号：一律走编号规则 PURCHASE_RECEIPT（GRN 前缀＋期间＋定长序号）。
     * R5-D-069：原「规则缺失降级 RCP+时间戳」默认处理已删除——降级号形态不可预期、无法对账；
     * 规则缺失应显式报错去修规则，而不是静默拼时间戳（判据：收货单号形态只有 rule 一种）。
     */
    private String nextReceiptNo() {
        try {
            return numberRuleService.next("PURCHASE_RECEIPT");
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "收货单自动取号失败：编号规则 PURCHASE_RECEIPT 缺失或停用，请先配置规则再建单");
        }
    }

    /**
     * 取唯一收货单号。取号后校验 po_receipt（含 del_flag=1 的历史行，逻辑删除仍占唯一键）
     * 是否已存在同号，存在则递增重试，最多 5 次；全部冲突则抛明确业务异常（不再裸抛 500）。
     */
    private String nextReceiptNoUnique() {
        for (int i = 0; i <= 5; i++) {
            String candidate = nextReceiptNo();
            Long tid = TenantContext.getTenantId();
            long dup = (tid == null) ? 0L : receiptMapper.countByReceiptNo(tid, candidate);
            if (dup == 0) {
                return candidate;
            }
            log.warn("收货单号 {} 与存量记录冲突（含逻辑删除行），递增重试 {}/5", candidate, i + 1);
        }
        // R5-D-069：原「默认处理第二级＝规则号+随机 4 位尾号 50 次」已删除——尾号拼接同样产生不可预期单号；
        // 连续冲突说明编号规则序号与历史脱节，显式报错交人工修规则（不再静默造号）
        throw new BizException(ErrorCode.PARAM_INVALID,
                "收货单自动取号失败：连续取号均与已有单号冲突，请检查编号规则 PURCHASE_RECEIPT 的当前序号");
    }

    /** 采购订单号：同上默认策略（PO_ORDER 规则未预置时降级为时间戳，避免 NOT NULL 插入失败） */
    private String nextPoNo() {
        try {
            return numberRuleService.next("PO_ORDER");
        } catch (Exception e) {
            return "PO" + System.currentTimeMillis();
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ---------------- P0-5 状态机：APPROVED/INVOICED→PAID→CLOSED→ARCHIVED ----------------
    // P0-5 最终状态机分治（X-20 裁定甲＋乙）：本族跃迁按性质分两路，不再一律等人工按钮。
    //   甲＝累计量达标（PAID/CLOSED）由 {@link #autoAdvanceFinalStates()} 批处理推进，不给人工入口；
    //   乙＝人工决策（终止/归档）留显式服务方法＋端点 {@link #terminateOrder}/{@link #archiveOrder}，
    //        鉴权走写专码 po:order:update（写读分面治理，不复用 po:order:list）。
    //   ARCHIVED 归档域（document/**）只写 op_archive/archive_status，不写 po_order.status，
    //   故 po_order 侧的 ARCHIVED 跃迁由本类 {@link #archiveOrder}＋人工归档端点办结（X-20）。
    // D-DEC-051 操作人规则（本族状态写点统一）：update_by 取 userId()＝当前登录人。
    // 批处理线程无 UserContext、这里 userId() 也如实返回 null，但 PoOrder.updateBy 带
    // @TableField(fill = FieldFill.INSERT_UPDATE)，FsscMetaObjectHandler.updateFill→fillAudit
    // 见值为 null 会补一个 <b>0L</b>——所以库里落的是 update_by=0，<b>不是 NULL</b>。
    // 现库实测同型：exp_bill update_by=0 有 2 行、NULL 0 行；inv_invoice 0 有 3 行、NULL 0 行。
    // 结论：0 是「无登录上下文」的全局哨兵值，人工路径写真实 userId，两者可区分；
    // 追溯面按 0 判"系统/引擎所为"即可；仍不用 create_by 或随便一个工号冒充操作人。

    /** P0-5：采购订单 APPROVED/INVOICED→PAID（累计付款达标） */
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(Long orderId) {
        PoOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在: " + orderId);
        }
        // P0-5 最终状态机分治（X-20 裁定甲＋乙）：前置态补 INVOICED——三单匹配收尾把订单写成 INVOICED
        // （InvoiceMatchBizAction:85），原判据只认 APPROVED ⇒ PAID 永远进不去，最终状态机空转。
        String from = order.getStatus();
        if (!"APPROVED".equals(from) && !"INVOICED".equals(from)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已审批/已匹配订单可标记已付款，当前状态: " + from);
        }
        PoOrder update = new PoOrder();
        update.setId(orderId);
        update.setStatus("PAID");
        update.setUpdateBy(userId());
        update.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(update);
        auditTrailService.recordUpdate("PURCHASE", orderId, order.getPoNo(), "po_order",
                String.valueOf(orderId),
                java.util.Collections.singletonMap("status", order.getStatus()),
                java.util.Collections.singletonMap("status", "PAID"), "标记已付款");
        log.info("采购订单 {} 状态 {}→PAID", order.getPoNo(), from);
    }

    /** P0-5：采购订单 PAID→CLOSED（关账） */
    @Transactional(rollbackFor = Exception.class)
    public void closeOrder(Long orderId) {
        PoOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在: " + orderId);
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已付款订单可关账，当前状态: " + order.getStatus());
        }
        PoOrder update = new PoOrder();
        update.setId(orderId);
        update.setStatus("CLOSED");
        update.setUpdateBy(userId());
        update.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(update);
        auditTrailService.recordUpdate("PURCHASE", orderId, order.getPoNo(), "po_order",
                String.valueOf(orderId),
                java.util.Collections.singletonMap("status", order.getStatus()),
                java.util.Collections.singletonMap("status", "CLOSED"), "关账");
        log.info("采购订单 {} 状态 PAID→CLOSED", order.getPoNo());
    }

    /**
     * P0-5：采购订单 CLOSED→ARCHIVED（归档，最终状态）。
     * P0-5 最终状态机分治·乙（X-20 裁定）：归档无累计量判据、属人工结案决定，入口为
     * {@code POST /api/tenant/purchase/order/{id}/archive}（写专码 po:order:update）。
     * 已归档/已终止单不可再归档（状态前置即守卫）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void archiveOrder(Long orderId) {
        PoOrder order = orderMapper.selectById(orderId);
        if (order == null || (order.getDelFlag() != null && order.getDelFlag() == 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在: " + orderId);
        }
        if (!"CLOSED".equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已关账订单可归档，当前状态: " + order.getStatus());
        }
        PoOrder update = new PoOrder();
        update.setId(orderId);
        update.setStatus("ARCHIVED");
        update.setUpdateBy(userId());
        update.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(update);
        auditTrailService.recordUpdate("PURCHASE", orderId, order.getPoNo(), "po_order",
                String.valueOf(orderId),
                java.util.Collections.singletonMap("status", order.getStatus()),
                java.util.Collections.singletonMap("status", "ARCHIVED"), "归档");
        log.info("采购订单 {} 状态 CLOSED→ARCHIVED", order.getPoNo());
    }

    /**
     * P0-5 最终状态机分治·甲（X-20 裁定）：累计量达标型最终状态由批处理推进，不给人工按钮。
     * <ul>
     *  <li>APPROVED/INVOICED → PAID：本单下游付款单（exp_bill.po_id 挂接、status=PAID）累计已付 ≥ 订单额；</li>
     *  <li>PAID → CLOSED（业务关闭）：付款已达标（PAID 即成立）且累计收货 ≥ 订单额、下游无未收货完的收货单。</li>
     * </ul>
     * 去重：判据全是「绝对量比较 + 状态前置」，同一轮/多轮重跑结果一致（已达标的已进最终状态，不再入选）。
     * 逐单隔离：单据级异常只记日志不影响其余订单，批处理线程无租户上下文，由调用方按租户 {@code callAs} 包好。
     *
     * @return 本轮推进计数 {@code paid=N closed=M}（供 Job 日志与只读复验）
     */
    public String autoAdvanceFinalStates() {
        List<PoOrder> candidates = orderMapper.selectList(new LambdaQueryWrapper<PoOrder>()
                .eq(PoOrder::getDelFlag, 0)
                .in(PoOrder::getStatus, "APPROVED", "INVOICED", "PAID"));
        int paid = 0;
        int closed = 0;
        for (PoOrder o : candidates) {
            try {
                if ("APPROVED".equals(o.getStatus()) || "INVOICED".equals(o.getStatus())) {
                    BigDecimal amount = nz(o.getAmount());
                    BigDecimal paidSum = paidAmountOf(o.getId());
                    if (amount.signum() > 0 && paidSum.compareTo(amount) >= 0) {
                        markPaid(o.getId());
                        paid++;
                        o.setStatus("PAID");
                    } else {
                        continue;
                    }
                }
                if ("PAID".equals(o.getStatus())) {
                    BigDecimal amount = nz(o.getAmount());
                    BigDecimal received = nz(o.getReceivedAmount());
                    // 业务关闭判据（纯累计量）：付款已达标（PAID 即成立）＋ 收货累计 ≥ 订单额。
                    // 不看发票/收货单状态——po_order.status 已是 PAID，收货与付款两笔累计量就是达标真相。
                    if (amount.signum() > 0 && received.compareTo(amount) >= 0) {
                        closeOrder(o.getId());
                        closed++;
                    }
                }
            } catch (Exception ex) {
                log.error("采购订单 {} 终态推进失败（不影响本轮其余订单）: {}", o.getPoNo(), ex.getMessage(), ex);
            }
        }
        log.info("[采购终态批处理] 候选 {} 单，推进 PAID {} 单、CLOSED {} 单", candidates.size(), paid, closed);
        return "paid=" + paid + " closed=" + closed;
    }

    /** 本订单下游已付款累计（exp_bill.po_id 挂接且 status=PAID，取实付额、实付额缺失回退单据额） */
    private BigDecimal paidAmountOf(Long orderId) {
        List<com.fssc.expense.entity.ExpBill> bills = expBillMapper.selectList(
                new LambdaQueryWrapper<com.fssc.expense.entity.ExpBill>()
                        .eq(com.fssc.expense.entity.ExpBill::getPoId, orderId)
                        .eq(com.fssc.expense.entity.ExpBill::getStatus, "PAID")
                        .eq(com.fssc.expense.entity.ExpBill::getDelFlag, 0));
        BigDecimal sum = BigDecimal.ZERO;
        for (com.fssc.expense.entity.ExpBill b : bills) {
            BigDecimal paidAmt = nz(b.getPaidAmount());
            sum = sum.add(paidAmt.signum() > 0 ? paidAmt : nz(b.getTotalAmount()));
        }
        return sum;
    }

    /**
     * P0-5 最终状态机分治·乙（X-20 裁定）：终止采购订单＝人工决策，留显式入口（服务方法＋端点，
     * 鉴权写专码 {@code po:order:update}，菜单授权由治理轨另行发放）。
     * <p>与 {@link #voidOrder} 的分工：作废只处理「还没发生下游动作」的单；终止处理
     * 已经收货/挂付款、但业务上不再继续履约的单（剩余未执行部分就此了断），
     * 两者同落既有最终状态 {@code VOID}（不新增状态值），原因＋操作人＋时间随 remark 记录。
     * <p>已达标的单请走批处理自然办结（{@link #autoAdvanceFinalStates}），已归档单不可终止。
     */
    @Transactional(rollbackFor = Exception.class)
    public void terminateOrder(Long orderId, String reason) {
        PoOrder o = orderMapper.selectById(orderId);
        if (o == null || (o.getDelFlag() != null && o.getDelFlag() == 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在: " + orderId);
        }
        String status = o.getStatus();
        if ("VOID".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单 " + o.getPoNo() + " 已终止/作废，请勿重复操作");
        }
        if ("CLOSED".equals(status) || "ARCHIVED".equals(status) || "PAID".equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "订单已付款/关账/归档（当前状态: " + status + "），无须终止；付款未达标请先处理下游单");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "终止原因必填");
        }
        String operator = "unknown";
        try {
            com.fssc.framework.security.LoginUser lu = UserContext.get();
            if (lu != null) {
                operator = lu.getRealName() == null || lu.getRealName().isBlank()
                        ? lu.getUsername() : lu.getRealName();
            }
        } catch (Exception ignored) { /* 无用户上下文（系统操作） */ }
        String suffix = "；【终止】原因：" + reason.trim() + "；操作人：" + operator
                + "；时间：" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String newRemark = (o.getRemark() == null ? "" : o.getRemark()) + suffix;
        if (newRemark.length() > 500) {
            newRemark = newRemark.substring(newRemark.length() - 500);
        }
        PoOrder upd = new PoOrder();
        upd.setId(orderId);
        upd.setStatus("VOID");
        upd.setRemark(newRemark);
        upd.setUpdateBy(userId());
        upd.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(upd);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", status);
        auditBefore.put("remark", o.getRemark());
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", "VOID");
        auditAfter.put("remark", newRemark);
        auditTrailService.recordUpdate("PURCHASE", orderId, o.getPoNo(), "po_order",
                String.valueOf(orderId), auditBefore, auditAfter, "终止采购订单：" + reason.trim());
        // R5-D-068 最终状态收尾（同 voidOrder）：终止同落 VOID，必须把该单据名下的 RUNNING 流程实例一并
        // 终止、PENDING 待办置 CANCELLED，避免「单已终止、流程还活」的僵尸待办继续被催办。
        wfEngineFacade.cancelRunningInstancesByBiz("PURCHASE_ORDER_REVIEW", orderId,
                "采购订单终止（" + o.getPoNo() + "）：" + reason.trim());
        log.info("采购订单 {} 已终止（{}→VOID，原因: {}，操作人: {}）", o.getPoNo(), status, reason.trim(), operator);
    }
}
