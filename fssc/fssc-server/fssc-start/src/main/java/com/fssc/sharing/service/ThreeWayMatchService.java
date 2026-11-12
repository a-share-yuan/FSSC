// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.contract.entity.ContContract;
import com.fssc.contract.mapper.ContContractMapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.framework.security.UserContext;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.invoice.entity.InvBillInvoice;
import com.fssc.framework.facade.InvoiceDomainFacade;
import com.fssc.masterdata.entity.MdMatchTolerance;
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.mapper.MdMatchToleranceMapper;
import com.fssc.masterdata.mapper.MdSupplierMapper;
import com.fssc.match.entity.MatchTolerance;
import com.fssc.match.service.MatchToleranceService;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.entity.PoOrderLine;
import com.fssc.purchase.entity.PoReceipt;
import com.fssc.purchase.mapper.PoOrderLineMapper;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.purchase.mapper.PoReceiptMapper;
import com.fssc.asset.entity.StockMove;
import com.fssc.asset.mapper.StockMoveMapper;
import com.fssc.workflow.service.WfEngineFacade;
import com.fssc.system.service.ConfigService;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对公付款三单匹配（采购订单 PO ↔ 收货单 ↔ 发票 ↔ 付款单）。
 * 业务语义：采购订单 → 到货收货（收货 ≤ 订单剩余）→ 供应商开票（发票 ≤ 收货单金额）→ 付款（付款 ≤ 发票未核销）。
 * 校验规则：
 * 1. 本次付款 ≤ 关联发票价税合计，否则 FAILED 无发票覆盖（禁止付款）；
 * 2. 发票 ≤ 收货单金额（发票超收货 WARN）；收货 ≤ 采购订单剩余（收货超单 WARN）；
 * 3. 未关联收货单/PO → NO_PO 提示；供应商对账未确认 → WARN；
 * 4. 合同是四单之一环节：付款单未挂合同且采购订单也未挂 → NO_CONTRACT（WARN，提示补挂，不冻结付款）；
 *    金额链之外另有数量链：订购数量（po_order_line）vs 已收数量（stock_move RECEIVE）。
 * 5. 主体链：付款单收款方 vs 采购订单供应商 → PAYEE_NOT_SUPPLIER（WARN，<b>只记录提示、不冻结付款</b>；
 *    升级为硬拦属校验策略，须与付款执行期的强制校验拦口一并判定）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreeWayMatchService {

    private final ExpBillMapper expBillMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final ContContractMapper contractMapper;
    private final InvoiceDomainFacade invoiceFacade;
    private final PoOrderMapper poOrderMapper;
    private final PoReceiptMapper poReceiptMapper;
    /** 量侧：订购数量来自订单行，已收数量来自存货移动台账（stock_move） */
    private final PoOrderLineMapper poOrderLineMapper;
    private final StockMoveMapper stockMoveMapper;
    private final SupplierReconService supplierReconService;
    private final MdMatchToleranceMapper toleranceMapper;
    /** 付款行 {@code payee_id} 回写要按收款方名称精确命中供应商档案（与 {@code PayBatchService.resolveSupplierArchiveId} 同源反查） */
    private final MdSupplierMapper mdSupplierMapper;
    /** 收货 vs 发票容差：match_tolerance 参数表（公司级，百分比+绝对值），无配置默认 0.5%/100 元 */
    private final MatchToleranceService matchToleranceService;
    private final WfEngineFacade wfEngineFacade;
    /** 租户参数读取（预付款比例上限 prepay.max.ratio；非 wf./approve. 前缀，租户可维护） */
    private final ConfigService configService;

    /** 对公付款单分页 + 匹配状态 */
    public PageResult<MatchRow> page(long pageNum, long pageSize, String keyword, String matchStatus) {
        LambdaQueryWrapper<ExpBill> qw = new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getBillType, "PAY")
                .ne(ExpBill::getStatus, "DRAFT")
                .ne(ExpBill::getStatus, "VOID");
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(ExpBill::getBillNo, keyword).or().like(ExpBill::getTitle, keyword));
        }
        qw.orderByDesc(ExpBill::getId);
        Page<ExpBill> p = expBillMapper.selectPage(Page.of(pageNum, pageSize), qw);
        List<MatchRow> rows = new ArrayList<>();
        for (ExpBill b : p.getRecords()) {
            MatchRow m = check(b);
            if (matchStatus != null && !matchStatus.isBlank() && !matchStatus.equals(m.getMatchStatus())) {
                continue;
            }
            rows.add(m);
        }
        return PageResult.of(rows, p.getTotal(), pageNum, pageSize);
    }

    /**
     * 预付款专用档（{@code bill_sub_type=EXP_PREPAY}）。
     *
     * <p><b>为什么要另立一档</b>：预付款是「先款后货」——付款时点收货单尚未产生、发票也未开，
     * 通用档的四单齐套（合同/PO/收货/发票）对它结构性不可达，任何预付款都会被
     * {@code NO_RECEIPT}／{@code PAY_EXCEED_INVOICE} 冻死。
     * 预付款真正要控的是「有没有依据」和「付多了没有」：</p>
     * <ul>
     *   <li><b>ERROR</b> 无依据：既无合同也无 PO —— 无依据预付直接拦；</li>
     *   <li><b>WARN</b> 只有 PO 没挂合同：提示补挂，不阻断（实务中框架协议下按 PO 预付常见）；</li>
     *   <li><b>ERROR</b> 超比例：本次付款 &gt; 合同额（无合同取订单额）× {@code prepay.max.ratio}
     *       （缺省 0.30；置 0 即等于"任何预付款都拦"，可作为回滚开关）。</li>
     * </ul>
     *
     * <p>不校验收货单与发票覆盖——那不是预付款的缺件，是本业务的正常形态；
     * 核销由后续【预付冲销】按实际到货与发票冲销。</p>
     */
    private MatchRow checkPrepay(ExpBill bill, MatchRow r, ExpBillPayment pay) {
        List<MatchIssue> issues = new ArrayList<>();
        boolean hasError = false;

        // 依据：合同优先，其次采购订单（付款单未挂合同时回退 PO 上的合同，与通用档同源）
        PoOrder po = bill.getPoId() == null ? null : poOrderMapper.selectById(bill.getPoId());
        if (po != null) {
            r.setPoId(po.getId());
            r.setPoNo(po.getPoNo());
            r.setPoAmount(nz(po.getAmount()));
        }
        Long contractId = bill.getContractId() != null ? bill.getContractId()
                : (po == null ? null : po.getContractId());
        ContContract contract = contractId == null ? null : contractMapper.selectById(contractId);
        if (contract != null) {
            r.setContractId(contract.getId());
            r.setContractNo(contract.getContractNo());
            r.setContractName(contract.getContractName());
            r.setContractAmount(nz(contract.getAmount()));
        }
        if (contract == null && po == null) {
            hasError = true;
            issues.add(new MatchIssue("PREPAY_NO_BASIS", "ERROR",
                    "预付款无依据：既未关联采购合同，也未关联采购订单——预付款必须凭合同或订单支付，"
                            + "请先在单据上补挂合同/PO 后重试"));
        } else if (contract == null) {
            issues.add(new MatchIssue("NO_CONTRACT", "WARN",
                    "未关联采购合同（仅有采购订单）：预付款比例按订单额核定，建议补挂合同"));
        }

        // 比例上限
        BigDecimal base = contract != null ? nz(contract.getAmount()) : (po == null ? BigDecimal.ZERO : nz(po.getAmount()));
        BigDecimal ratio = prepayMaxRatio();
        BigDecimal cap = base.multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP);
        if (base.signum() > 0 && r.getPayAmount().compareTo(cap) > 0) {
            hasError = true;
            issues.add(new MatchIssue("PREPAY_OVER_RATIO", "ERROR",
                    "预付款超比例上限：本次付款 " + r.getPayAmount() + " > " + (contract != null ? "合同额 " : "订单额 ")
                            + base + " × 比例 " + ratio.stripTrailingZeros().toPlainString() + " = " + cap
                            + "（上限参数 prepay.max.ratio，可在【全局参数】按租户调整）"));
        }

        // 收款方（沿用通用档的提示口径：只记录不拦截）
        String payeeName = firstNonBlank(pay == null ? null : pay.getPayeeName(), bill.getPayeeName());
        String payeeBankAccount = firstNonBlank(pay == null ? null : pay.getBankAccount(), bill.getPayeeAccountNo());
        r.setPayeeName(payeeName);
        r.setPayeeBankAccount(payeeBankAccount);
        r.setSupplierName(payeeName);
        r.setPoSupplierName(po == null ? null : firstNonBlank(po.getSupplierName()));
        r.setReconStatus("NONE"); // 预付款不校验收付款对账确认，此列仅供页面展示，不代表异常

        r.setMatchStatus(hasError ? "FAILED" : (issues.isEmpty() ? "MATCHED" : "WARN"));
        r.setFrozen(hasError);
        r.setMatchCode(issues.isEmpty() ? "PREPAY_EXEMPT" : issues.get(0).getCode());
        r.setIssues(issues);
        r.setMsg("预付款档：不校验收货单与发票覆盖（先款后货），按合同/订单与预付款比例上限（"
                + ratio.stripTrailingZeros().toPlainString() + "）管控；核销走【预付冲销】");
        return r;
    }

    /** 预付款比例上限（sys_config: prepay.max.ratio，缺省 0.30；非法值回落缺省，0 表示一律不放行） */
    private BigDecimal prepayMaxRatio() {
        String v = null;
        try {
            v = configService.getValue("prepay.max.ratio");
        } catch (Exception e) {
            log.warn("[三单匹配] 读取 prepay.max.ratio 失败，按缺省 0.30 处理: {}", e.getMessage());
        }
        if (v == null || v.isBlank()) {
            return new BigDecimal("0.30");
        }
        try {
            BigDecimal r = new BigDecimal(v.trim());
            if (r.signum() < 0) {
                return BigDecimal.ZERO;
            }
            return r.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : r;
        } catch (NumberFormatException e) {
            return new BigDecimal("0.30");
        }
    }

    /** 单张付款单三单匹配检查 */
    public MatchRow check(Long billId) {
        ExpBill b = expBillMapper.selectById(billId);
        if (b == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在");
        }
        return check(b);
    }

    /**
     * 发票校验异常发起人工复审（PTP03 INVOICE_MATCH）。
     * 仅当匹配非 MATCHED（FAILED/WARN）才允许进入复审审批；procInstId 由 wf_instance 留存。
     * @return 流程实例ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String review(Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在");
        }
        MatchRow r = check(bill);
        if ("MATCHED".equals(r.getMatchStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "三单一致无需复审（当前 " + r.getMatchStatus() + "）");
        }
        guardReEntry(bill);
        Long u = UserContext.get() == null ? null : UserContext.get().getUserId();
        return wfEngineFacade.start("INVOICE_MATCH", billId, u,
                "发票校验异常复审-" + (bill.getBillNo() == null ? billId : bill.getBillNo()), bill.getTotalAmount());
    }

    /**
     * 在途判重 + 应付确认去重。
     * 两条链共用同一个最终状态钩子 {@code invoiceMatchBizAction}，它会把 po_order 无条件置 INVOICED
     * 并生成应付确认凭证，所以"同一单再起一条链"的真实代价是重复制证，必须在发起侧就拦住。
     */
    private void guardReEntry(ExpBill bill) {
        Long billId = bill.getId();
        if (wfEngineFacade.hasActiveTask("INVOICE_MATCH", billId)
                || wfEngineFacade.hasActiveTask("INVOICE_MATCH_MANUAL", billId)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该单据已有在途的三单匹配审批，请等待办结");
        }
        PoOrder order = bill.getPoId() == null ? null : poOrderMapper.selectById(bill.getPoId());
        if (order != null && "INVOICED".equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "采购订单 " + order.getPoNo() + " 已完成应付确认，不得重复发起三单匹配审批");
        }
    }

    /**
     * 手工三单匹配审批（INVOICE_MATCH_MANUAL）。自动化校验不适用/需人工复核时，
     * 由用户发起该手工匹配审批流走「发票合规审核 → 手工三单匹配确认」人工链路。
     * 三单已一致(MATCHED)也允许走人工链，但必须给人工判定理由——
     * 手工链的价值就在自动判定不可信时，一刀切拒绝会把它该干的活也关掉；理由随实例变量留存。
     * @return 流程实例ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String manualReview(Long billId, String reason) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在");
        }
        MatchRow r = check(bill);
        String manualReason = reason == null ? "" : reason.trim();
        boolean matched = "MATCHED".equals(r.getMatchStatus());
        if (matched && manualReason.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "三单自动判定为一致，发起手工匹配需先填写人工判定理由");
        }
        guardReEntry(bill);
        Long u = UserContext.get() == null ? null : UserContext.get().getUserId();
        Map<String, Object> vars = new HashMap<>();
        if (!manualReason.isEmpty()) vars.put("manualMatchReason", manualReason);
        return wfEngineFacade.start("INVOICE_MATCH_MANUAL", billId, u,
                "手工三单匹配-" + (bill.getBillNo() == null ? billId : bill.getBillNo())
                        + (matched ? "（人工判定）" : ""),
                bill.getTotalAmount(), null, vars);
    }

    /** 三单匹配异常 → 责任岗位路由表（差异类型决定派给谁处理） */
    private static final Map<String, String> ISSUE_ROUTE = Map.of(
            "NO_PO", "PURCHASE_MANAGER",                  // 缺采购订单 → 采购
            "NO_CONTRACT", "PURCHASE_MANAGER",            // 缺合同环节（四单不齐）→ 采购补挂合同
            "RECEIPT_OVER_PO", "PURCHASE_MANAGER",        // 收货超订单 → 采购
            "RECEIPT_IN_TOLERANCE", "PURCHASE_MANAGER",   // 收货超订单但在容差内 → 采购
            "RECEIPT_QTY_OVER_PO", "PURCHASE_MANAGER",    // 收货数量超订购数量 → 采购
            "NO_RECEIPT", "WH_KEEPER",                    // 缺收货单 → 仓储
            "PAY_EXCEED_INVOICE", "FIN_AP",               // 超付 → 应付会计
            "INVOICE_OVER_RECEIPT", "FIN_AP",             // 发票超收货 → 应付会计
            "INVOICE_IN_TOLERANCE", "FIN_AP",             // 发票超收货但在容差内 → 应付会计
            "RECON_UNCONFIRMED", "RECON_CLERK");          // 供应商未对账 → 对账专员

    private static final Map<String, String> ROLE_NAME = Map.of(
            "PURCHASE_MANAGER", "采购经理",
            "WH_KEEPER", "仓库管理员",
            "FIN_AP", "应付会计",
            "RECON_CLERK", "对账专员");

    /**
     * 三单匹配异常按类型路由派单。
     *
     * <p>异常只冻结付款时，谁来改、改什么全靠线下协调，差异容易悬而未决。
     * 故按差异类型归并到责任岗位（价格/金额差异→应付会计，收货/订单差异→采购，
     * 缺收货单→仓储，未对账→对账专员），并发起 INVOICE_MATCH 异常复审流程派单；
     * 硬异常（超付/超容差）在解决前保持冻结（PayBatchService 已按 freezePay 拦截）。
     *
     * @return 路由结果：责任岗位 → 待处理差异明细，及是否冻结付款、流程实例ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> route(Long billId) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在");
        }
        MatchRow r = check(bill);
        if (r.getIssues() == null || r.getIssues().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "三单一致，无需派单");
        }
        Map<String, List<String>> byRole = new LinkedHashMap<>();
        for (MatchIssue it : r.getIssues()) {
            String role = ISSUE_ROUTE.getOrDefault(it.getCode(), "FIN_AP");
            byRole.computeIfAbsent(role, k -> new ArrayList<>())
                    .add(("ERROR".equals(it.getLevel()) ? "[硬异常]" : "") + it.getMsg());
        }
        String mainRole = byRole.keySet().iterator().next();
        Long u = UserContext.get() == null ? null : UserContext.get().getUserId();
        // 差异明细若只进日志与 HTTP 响应，办理人在待办里只见标题、不知"为何被派"，
        // wf_task.title／历史也只落标题。这里把主责岗待处理差异（byRole 值取自 it.getMsg()，已是中文说明、
        // 非枚举码，故不触发枚举直接显示）拼进发起标题——标题随 start() 一并下发到待办并落 wf_task.title 记录，
        // 是派单当时唯一可见的下发面（comment 变量要到审批人出票时才有值）。宽度约束＝wf_task.title／
        // wf_instance.title 均为 varchar(300)，故整段摘要限长 ≤160 字、超限截断，
        // 绝不因标题超长把派单发起本身顶成 "Data too long"（那才会造出无人可办的死单）。
        String dispatchReason = buildDispatchReason(byRole.get(mainRole));
        String procInstId = wfEngineFacade.start("INVOICE_MATCH", billId, u,
                "三单匹配异常派单-" + ROLE_NAME.getOrDefault(mainRole, mainRole)
                        + "-" + (bill.getBillNo() == null ? billId : bill.getBillNo())
                        + dispatchReason,
                bill.getTotalAmount());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("billId", billId);
        res.put("billNo", bill.getBillNo());
        res.put("matchStatus", r.getMatchStatus());
        res.put("frozen", r.isFrozen());
        res.put("routing", byRole);
        res.put("procInstId", procInstId);
        log.info("[三单匹配派单] billId={} 主责={} 共{}个责任岗 冻结付款={}",
                billId, mainRole, byRole.size(), r.isFrozen());
        return res;
    }

    /**
     * 把主责岗的待处理差异拼成<b>有界</b>的下发摘要（前缀"｜派单原因：…"，随标题写入数据库并下发待办）。
     * 单条最多 40 字、最多 3 条、整段最多 160 字，超出以省略号收尾；差异为空时返回空串（标题逐字保持原样）。
     */
    private static String buildDispatchReason(List<String> issues) {
        if (issues == null || issues.isEmpty()) {
            return "";
        }
        final int perIssue = 40;
        final int maxItems = 3;
        final int maxTotal = 160;
        StringBuilder body = new StringBuilder();
        int shown = Math.min(issues.size(), maxItems);
        for (int i = 0; i < shown; i++) {
            String msg = issues.get(i) == null ? "" : issues.get(i).trim();
            if (msg.isEmpty()) {
                continue;
            }
            if (msg.length() > perIssue) {
                msg = msg.substring(0, perIssue) + "…";
            }
            if (body.length() > 0) {
                body.append("；");
            }
            body.append(msg);
        }
        if (issues.size() > shown) {
            body.append("（等 ").append(issues.size()).append(" 项）");
        }
        if (body.length() == 0) {
            return "";
        }
        String tail = body.length() > maxTotal ? body.substring(0, maxTotal) + "…" : body.toString();
        return "｜派单原因：" + tail;
    }

    /**
     * 手工关联采购订单/收货单到付款单。
     * 发票校验场景下由用户在界面上手工选择 PO 和收货单，作为该付款单的匹配依据。
     */
    /**
     * 手工关联采购订单/收货单到付款单（向后兼容的两参重载）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String link(Long billId, Long poId, Long poReceiptId) {
        return link(billId, poId, poReceiptId, null);
    }

    /**
     * 手工关联采购订单/收货单/采购合同到付款单。
     * <p>支持补挂合同（contractId），使合同环节在付款单上有可达的补挂入口（详见 Controller 注释）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String link(Long billId, Long poId, Long poReceiptId, Long contractId) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在");
        }
        if (poId != null) {
            PoOrder po = poOrderMapper.selectById(poId);
            if (po == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在");
            }
            bill.setPoId(poId);
        }
        if (poReceiptId != null) {
            PoReceipt rc = poReceiptMapper.selectById(poReceiptId);
            if (rc == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "收货单不存在");
            }
            bill.setPoReceiptId(poReceiptId);
        }
        // 合同环节补挂：传 null 表示本次不改合同（保持向后兼容）；传值则校验合同真实存在。
        if (contractId != null) {
            ContContract c = contractMapper.selectById(contractId);
            if (c == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "采购合同不存在");
            }
            bill.setContractId(contractId);
        }
        // 手工关联改的是单据的匹配依据（po_id/po_receipt_id），属状态写点，操作人如实取登录人；
        // 无上下文（理论上不该发生，本入口只暴露给人工）则留 NULL 不顶替。
        bill.setUpdateBy(currentUser());
        bill.setUpdateTime(LocalDateTime.now());
        expBillMapper.updateById(bill);
        backfillPayeeId(bill);
        // 后补关联收货单时补齐勾稽列：发票往往早于「手工关联」就已登记，否则 po_receipt.invoice_amount 永久 0
        if (poReceiptId != null) {
            syncReceiptInvoiceColumn(poReceiptId);
        }
        // 关联到 PO 时，该 PO 名下<b>其它</b>已关联付款单的收货单也要重算一遍——
        // 否则只有本次点到的那张收货单被补，同 PO 的其它收货单会一直挂 0。
        if (poId != null) {
            for (PoReceipt rc : poReceiptMapper.selectList(new LambdaQueryWrapper<PoReceipt>()
                    .eq(PoReceipt::getPoId, poId))) {
                syncReceiptInvoiceColumn(rc.getId());
            }
        }
        log.info("[三单匹配-手工关联] billId={} poId={} poReceiptId={} contractId={}", billId, poId, poReceiptId, contractId);
        return "关联成功";
    }

    /**
     * 手工关联之后补一次付款行的 {@code payee_id}。
     *
     * <p>付款记录在审批通过那一刻就解析过一次收款方档案（{@code ExpenseWfEventListener.createPaymentRecord}），
     * 但「手工关联」是<b>之后</b>才把 PO 挂上单的那一步，当时取到什么就永久留在什么上，仓内没有任何写点回头补
     * ⇒ 标 {@code SUPPLIER} 的付款行 {@code payee_id} 可能全为 NULL，付款侧三道校验（禁用/黑名单、
     * 账号核对、主体比对）只能退化成按名称现算。</p>
     *
     * <p>查询范围＝{@code payee_name} 在 {@code md_supplier} 的<b>唯一</b>精确命中；零命中或多命中都留 NULL
     * （同名多家＝哪一条都不是"这一家"的证据，与凭证侧「不猜一家」同一规则）。
     * 也不应按 {@code po_id → po_order.supplier_id} 回填：那条链答的是"向谁买"，与本列的"付给谁"可以不同主体——
     * 按 PO 回填会把"资金流主体与票据流主体分离"直接洗平成相等。</p>
     *
     * <p>去重：只改 {@code payee_id IS NULL} 的行，已有值一律不覆盖。</p>
     */
    private void backfillPayeeId(ExpBill bill) {
        List<ExpBillPayment> rows = expBillPaymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getBillId, bill.getId())
                .eq(ExpBillPayment::getPayeeType, "SUPPLIER")
                .isNull(ExpBillPayment::getPayeeId));
        for (ExpBillPayment row : rows) {
            String payeeName = row.getPayeeName();
            if (payeeName == null || payeeName.isBlank()) {
                continue;
            }
            List<MdSupplier> hits = mdSupplierMapper.selectList(new LambdaQueryWrapper<MdSupplier>()
                    .eq(MdSupplier::getSupplierName, payeeName));
            if (hits.size() != 1) {
                log.info("[三单匹配-手工关联] 单据 {} 付款行 {} 收款方[{}] 在 md_supplier 命中 {} 家，payee_id 保持留空",
                        bill.getBillNo(), row.getId(), payeeName, hits.size());
                continue;
            }
            ExpBillPayment update = new ExpBillPayment();
            update.setId(row.getId());
            update.setPayeeId(hits.get(0).getId());
            expBillPaymentMapper.updateById(update);
            log.info("[三单匹配-手工关联] 单据 {} 付款行 {} payee_id 回写＝md_supplier {}（收款方名称精确唯一命中）",
                    bill.getBillNo(), row.getId(), hits.get(0).getId());
        }
    }

    /** 当前登录人；无请求上下文（引擎线程/定时线程）返回 null——如实留空，不伪造操作人 */
    private static Long currentUser() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    // 手工匹配最终状态写入数据库

    /**
     * ext_json 里存放三单匹配<b>人工结论</b>的键。
     *
     * <p>为什么写在 {@code exp_bill.ext_json}：库里没有 three_way_match 表，
     * exp_bill 也没有 match_status / match_result 列。
     * 而 ext_json 是既有 text 列，同族已有 voucherPending / abroadFlag / overrunReason /
     * autoAuditSample / cipId 五个键在用——匹配结论沿用同一载体＝<b>零 DDL</b>，
     * 不为此再造一张表、也不改共享列语义。
     *
     * <p>写入数据库内容（结论对象）：结论、来源流程（INVOICE_MATCH_MANUAL）、
     * 流程实例号、人工判定理由、办结时间、操作人。
     * {@code decidedBy} 在引擎线程收尾（{@code InvoiceMatchBizAction}）取不到登录人时<b>如实留 null</b>，
     * 由 procInstId 反查审批链补齐，不拿提单人顶替（同一规则）。
     */
    public static final String EXT_MATCH_VERDICT_KEY = "matchVerdict";

    /** 人工判定通过的最终状态结论值（写入 ext_json.matchVerdict.result） */
    public static final String VERDICT_MANUAL_MATCHED = "MANUAL_MATCHED";

    /**
     * 登记三单匹配的人工结论（去重：同单重复办结只覆盖结论，不追加）。任何异常只 warn，不阻断审批收尾。
     *
     * @param billId 付款单 id（起流 bizId）
     * @param bizType 流程类型（INVOICE_MATCH / INVOICE_MATCH_MANUAL）
     * @param procInstId 流程实例号（结论可反查的唯一抓手）
     * @param verdict 结论：{@link #VERDICT_MANUAL_MATCHED} 或 null＝未通过
     * @param reason 人工判定理由（manualReview 传入的流程变量）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordMatchVerdict(Long billId, String bizType, String procInstId, String verdict, String reason) {
        if (billId == null || verdict == null) {
            return;
        }
        try {
            ExpBill bill = expBillMapper.selectById(billId);
            if (bill == null) {
                return;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("result", verdict);
            node.put("flowKey", bizType);
            node.put("procInstId", procInstId);
            node.put("reason", reason == null ? "" : reason);
            node.put("decidedAt", LocalDateTime.now().toString());
            // 引擎线程无登录上下文 ⇒ 如实 null（由 procInstId 反查审批人补齐），不用 apply_user_id 冒充
            node.put("decidedBy", currentUser());
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> ext = new LinkedHashMap<>();
            if (bill.getExtJson() != null && !bill.getExtJson().isBlank()) {
                try {
                    ext = om.readValue(bill.getExtJson(),
                            new TypeReference<LinkedHashMap<String, Object>>() {
                            });
                } catch (Exception parse) {
                    log.warn("[三单匹配] ext_json 解析失败，本次仅重写结论键 billId={}: {}", billId, parse.getMessage());
                }
            }
            ext.put(EXT_MATCH_VERDICT_KEY, node);
            ExpBill upd = new ExpBill();
            upd.setId(billId);
            upd.setExtJson(om.writeValueAsString(ext));
            upd.setUpdateBy(currentUser());
            upd.setUpdateTime(LocalDateTime.now());
            expBillMapper.updateById(upd);
            log.info("[三单匹配] 付款单 {} 终态结论落库 result={} flow={} proc={}",
                    bill.getBillNo(), verdict, bizType, procInstId);
        } catch (Exception ex) {
            log.warn("[三单匹配] 人工结论落库失败 billId={}: {}", billId, ex.getMessage());
        }
    }

    /** 读取已写入数据库的人工匹配结论（无/解析失败＝无结论），供 {@link #check(ExpBill)} 叠盖自动判定。 */
    private Map<String, String> readMatchVerdict(ExpBill bill) {
        if (bill == null || bill.getExtJson() == null || bill.getExtJson().isBlank()) {
            return null;
        }
        try {
            JsonNode n =
                    new ObjectMapper().readTree(bill.getExtJson())
                            .get(EXT_MATCH_VERDICT_KEY);
            if (n == null || !n.isObject()) {
                return null;
            }
            Map<String, String> m = new LinkedHashMap<>();
            n.fieldNames().forEachRemaining(k -> m.put(k, n.get(k).asText()));
            return m;
        } catch (Exception ex) {
            log.debug("[三单匹配] 读取人工结论失败 bill={}: {}", bill.getId(), ex.getMessage());
            return null;
        }
    }

    /**
     * 强制校验容差：收货单「累计开票 ≤ 收货额 + 容差」的容差上限（元）。
     * 按小数额度设：仅吸收分位舍入噪声，参照付款金额容差先例（BankReconcileService.AMOUNT_DIFF_TOLERANCE＝0.01 元）。
     * 故意不复用 match_tolerance 配置（其 0.5%／100 元档是「净额 vs 收货额」的匹配松紧判定，
     * 而本校验拦的是开票额超出收货额本身，沿用会把万元级超额也放行）。
     */
    public static final BigDecimal INVOICE_OVER_RECEIPT_TOLERANCE = new BigDecimal("0.01");

    /** 强制校验判据：开票额是否超出「收货额 + 容差」。两处回写点（PurchaseService.markInvoiced
     *  与本类 {@link #syncReceiptInvoiceColumn}）共用同一判据，避免双规则漂移；空值按 0 视之。 */
    public static boolean invoiceExceedsReceiptCap(BigDecimal invoiceTotal, BigDecimal receiptAmount) {
        BigDecimal cap = (receiptAmount == null ? BigDecimal.ZERO : receiptAmount)
                .add(INVOICE_OVER_RECEIPT_TOLERANCE);
        return (invoiceTotal == null ? BigDecimal.ZERO : invoiceTotal).compareTo(cap) > 0;
    }

    /** 按勾稽明细重算并回写 po_receipt.invoice_amount（0 时状态回退 RECEIVED） */
    private void syncReceiptInvoiceColumn(Long receiptId) {
        try {
            PoReceipt rc = poReceiptMapper.selectById(receiptId);
            if (rc == null) {
                return;
            }
            // 回写列与判据都取「勾稽净额（不含税）」。po_receipt.amount 是不含税入账额，
            // 而 inv_bill_invoice.amount 是价税合计——两者直接相比，
            // 标准组合「净额收货＋价税开票」会被判成超收货（差额恰为税额）。
            // 与 check() 的 invoiceNetAmt 同尺：勾稽价税合计 − 关联发票税额。
            BigDecimal linked = sumLinkedInvoiceNetAmount(receiptId);
            // 与 PurchaseService.markInvoiced 同一绝对值重算规则（两者都只认 inv_bill_invoice 明细为真相源），
            // 互不叠加，因此两条路径都跑也不会把勾稽额算重；差别仅在 markInvoiced 还兜"明细为空时按入参累加"。
            if (nz(rc.getInvoiceAmount()).compareTo(linked) == 0
                    && (linked.signum() == 0 || "INVOICED".equals(rc.getStatus()))) {
                return; // 已一致，不产生空写与噪声记录
            }
            // 强制校验：勾稽重算后累计开票超出「收货额 + 容差」→ 拒绝回写（库内值保持原样）并派 INVOICE_MATCH 异常单。
            // 规则同 PurchaseService.markInvoiced（同一判据方法）；"拒写"而非抛异常：本方法在人工关联路径事务内，
            // 抛出会污染调用方事务，得不偿失；拒绝后留 error 日志 + 派单兜底，人工据单纠偏。
            if (invoiceExceedsReceiptCap(linked, rc.getAmount())) {
                try {
                    dispatchInvoiceOverReceipt(receiptId, rc.getReceiptNo(), linked, nz(rc.getAmount()));
                } catch (Exception ex) {
                    // 派单失败不得反噬"拒绝回写"本身（拒绝是主诉求，派单是兜底）
                    log.warn("收货单 {} 超限派单失败（拒绝回写不受影响）: {}", rc.getReceiptNo(), ex.getMessage());
                }
                log.error("收货单 {} 勾稽开票额 {} 超收货额 {} + 容差 {}（硬闸），拒绝回写",
                        rc.getReceiptNo(), linked, nz(rc.getAmount()), INVOICE_OVER_RECEIPT_TOLERANCE);
                return;
            }
            PoReceipt upd = new PoReceipt();
            upd.setId(receiptId);
            upd.setInvoiceAmount(linked);
            if (linked.signum() > 0 && "RECEIVED".equals(rc.getStatus())) {
                upd.setStatus("INVOICED");
            } else if (linked.signum() == 0 && "INVOICED".equals(rc.getStatus())) {
                upd.setStatus("RECEIVED");
            }
            // 本方法只在人工路径上被调用（link＝手工关联，有登录上下文），操作人如实取；
            // 无上下文时留 NULL，不用付款单提单人或建单人顶替。
            upd.setUpdateBy(currentUser());
            upd.setUpdateTime(LocalDateTime.now());
            poReceiptMapper.updateById(upd);
            log.info("[三单匹配] 收货单 {} 勾稽开票额回写 {}", rc.getReceiptNo(), linked);
        } catch (Exception ex) {
            log.warn("[三单匹配] 回写收货单开票额失败 receiptId={}: {}", receiptId, ex.getMessage());
        }
    }

    /**
     * 开票回写超收货额（强制校验触发）→ 复用三单匹配异常派单链，差异类型 INVOICE_OVER_RECEIPT
     * 固定路由应付会计（ISSUE_ROUTE/ROLE_NAME 中已登记），发起 INVOICE_MATCH 异常复审流程。
     * 故意不加事务注解：调用方（两处回写路径）均已在事务内，派单失败只须记录，不得反噬"拒绝回写"；
     * 关联单取该收货单名下勾稽额最大的付款单（并列取小 id，查询已按 id 升序），并以 hasActiveTask 防重
     * （同一付款单已有在办 INVOICE_MATCH 派单则不再发起）。
     *
     * @return 流程实例ID；名下无付款单或已有在办派单时返回 null（均有日志记录）
     */
    public String dispatchInvoiceOverReceipt(Long receiptId, String receiptNo, BigDecimal invoiceTotal, BigDecimal receiptAmount) {
        List<ExpBill> bills = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                .eq(ExpBill::getPoReceiptId, receiptId)
                .orderByAsc(ExpBill::getId));
        if (bills.isEmpty()) {
            log.warn("收货单 {} 超限但名下无付款单，无处派 INVOICE_MATCH（补挂后下次回写再触发）", receiptNo);
            return null;
        }
        ExpBill anchor = null;
        BigDecimal anchorLinked = BigDecimal.ZERO;
        for (ExpBill b : bills) {
            BigDecimal s = invoiceFacade.findLinksByBillId(b.getId())
                    .stream().map(x -> nz(x.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (anchor == null || s.compareTo(anchorLinked) > 0) { // 并列取先遍历到的小 id
                anchor = b;
                anchorLinked = s;
            }
        }
        if (wfEngineFacade.hasActiveTask("INVOICE_MATCH", anchor.getId())) {
            log.info("付款单 {} 已有在办 INVOICE_MATCH 派单，去重不重复发起", anchor.getId());
            return null;
        }
        String role = ISSUE_ROUTE.getOrDefault("INVOICE_OVER_RECEIPT", "FIN_AP");
        Long u = UserContext.get() == null ? null : UserContext.get().getUserId();
        String billNo = anchor.getBillNo() == null ? String.valueOf(anchor.getId()) : anchor.getBillNo();
        String procInstId = wfEngineFacade.start("INVOICE_MATCH", anchor.getId(), u,
                "三单匹配异常派单-" + ROLE_NAME.getOrDefault(role, role) + "-" + billNo
                        + "｜派单原因：开票回写超收货额（收货单 " + receiptNo + " 累计开票 " + invoiceTotal
                        + " 超收货额 " + receiptAmount + " 加容差上限，回写已拒绝）",
                invoiceTotal);
        log.warn("开票回写超收货额派单：收货单 {} 累计开票 {} > 收货额 {} + 容差，锚付款单 {} 流程 {}",
                receiptNo, invoiceTotal, receiptAmount, billNo, procInstId);
        return procInstId;
    }

    /**
     * 查询付款单关联的发票明细列表（供三单匹配明细展示）。
     */
    public List<Map<String, Object>> listInvoices(Long billId) {
        List<InvBillInvoice> links = invoiceFacade.findLinksByBillId(billId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (InvBillInvoice link : links) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("linkId", link.getId());
            m.put("invoiceId", link.getInvoiceId());
            m.put("amount", nz(link.getAmount()));
            if (link.getInvoiceId() != null) {
                InvInvoice inv = invoiceFacade.findById(link.getInvoiceId());
                if (inv != null) {
                    m.put("invoiceNo", inv.getInvoiceNo());
                    m.put("invoiceCode", inv.getInvoiceCode());
                    m.put("sellerName", inv.getSellerName());
                    m.put("invoiceDate", inv.getInvoiceDate());
                    m.put("netAmount", nz(inv.getNetAmount()));
                    m.put("taxAmount", nz(inv.getTaxAmount()));
                    m.put("totalAmount", nz(inv.getAmount()));
                    m.put("status", inv.getStatus());
                }
            }
            result.add(m);
        }
        return result;
    }

    private MatchRow check(ExpBill bill) {
        MatchRow r = new MatchRow();
        r.setBillId(bill.getId());
        r.setBillNo(bill.getBillNo());
        r.setTitle(bill.getTitle());
        r.setPayAmount(nz(bill.getTotalAmount()));
        r.setContractId(bill.getContractId());
        r.setBillStatus(bill.getStatus());

        // 三单匹配（PO/收货/发票）仅适用于对公付款单（采购循环 PTP）。
        // 员工报销(CLAIM)/借还款等单据没有 PO 语义，若按「未关联采购订单+超付」校验只会误拦，故直接放行。
        if (!"PAY".equals(bill.getBillType())) {
            r.setMatchStatus("MATCHED");
            r.setMsg("非对公付款单（" + bill.getBillType() + "），不适用三单匹配");
            return r;
        }

        // 供应商对账联动（独立计算）
        // 只认付款记录 payee_name 一级时，取不到会把字面量 '无'（连引号）拼进 WARN 文案，
        // 对账专员无从判断该对哪一家。故按三级回退取名（付款记录 → 单据 → 采购订单），
        // 并把 reconStatus 的计算放到 PO 环节解析之后（见下方「供应商对账」段）。
        ExpBillPayment pay = expBillPaymentMapper.selectOne(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getBillId, bill.getId())
                .last("limit 1"));

        // 预付款（bill_type=PAY + bill_sub_type=EXP_PREPAY）走专用档：
        // 预付款是「先款后货」，收货单与发票在付款时点尚不存在，四单齐套校验结构性不可达——
        // 只要走通用档，任何预付款都必报「未关联收货单」「关联发票 0」而被冻死。
        // 预付款的正解是按合同/订单＋预付款比例上限管控，核销由后续【预付冲销】承担。
        if ("EXP_PREPAY".equalsIgnoreCase(bill.getBillSubType())) {
            return checkPrepay(bill, r, pay);
        }

        List<MatchIssue> issues = new ArrayList<>();
        boolean hasError = false;

        // 1) 采购订单（PO）侧
        PoOrder poLeg = null;
        if (bill.getPoId() != null) {
            PoOrder po = poOrderMapper.selectById(bill.getPoId());
            if (po != null) {
                poLeg = po;
                r.setPoId(po.getId());
                r.setPoNo(po.getPoNo());
                r.setPoAmount(nz(po.getAmount()));
                r.setPoReceived(nz(po.getReceivedAmount()));
                r.setPoQty(sumOrderedQty(po.getId()));
                // RECEIPT ≤ PO：收货超订单。
                // 超收货本身不冻结——先收货后补订单、分批到货溢装在实务中是常态，
                // 整体拦会逼出"为过校验而压低收货"的反向作弊。
                // 真正的资金风险是为超收那部分付了钱：钱出去了、订单上没有对应义务。
                // 故这里保持 WARN（提示去补订单／办溢收手续），并把超收金额记下来，
                // 由 {@link #assertPayNotBeyondOrder} 作为独立的 ERROR 强制校验承担拦截。
                if (r.getPoReceived().compareTo(r.getPoAmount()) > 0) {
                    BigDecimal over = r.getPoReceived().subtract(r.getPoAmount());
                    r.setReceiptOverPoAmount(over);
                    BigDecimal allow = tolerance("RECEIPT_VS_PO", r.getPoAmount(), bill.getCompanyId(), null);
                    if (over.compareTo(allow) > 0) {
                        issues.add(new MatchIssue("RECEIPT_OVER_PO", "WARN",
                                "收货超订单：" + po.getPoNo() + " 累计收货 " + r.getPoReceived() + " > 订单 " + r.getPoAmount()
                                        + "（超 " + over + "，超容差部分须补订单或办溢收手续；"
                                        + "付款金额不得超过订单额 " + r.getPoAmount() + "）"));
                    } else {
                        issues.add(new MatchIssue("RECEIPT_IN_TOLERANCE", "WARN",
                                "收货超订单但在容差内：" + po.getPoNo() + " 超 " + over + "（容差 " + allow
                                        + "；付款金额不得超过订单额 " + r.getPoAmount() + "）"));
                    }
                }
            }
        } else {
            r.setPoNo(null);
            issues.add(new MatchIssue("NO_PO", "WARN", "未关联采购订单（PO）"));
        }

        // 2) 收货单侧（三单匹配核心：发票 ≤ 收货 ≤ PO）
        if (bill.getPoReceiptId() != null) {
            PoReceipt rc = poReceiptMapper.selectById(bill.getPoReceiptId());
            if (rc != null) {
                r.setReceiptId(rc.getId());
                r.setReceiptNo(rc.getReceiptNo());
                r.setReceiptAmount(nz(rc.getAmount()));
                // 收货↔发票勾稽：以 inv_bill_invoice 明细现算为准（回写列可能是绑定早于关联的历史欠账）
                // 与回写列同用不含税净额，避免同屏「已开票」显示价税、判据用净额的双规则
                BigDecimal liveInvoice = sumLinkedInvoiceNetAmount(rc.getId());
                r.setReceiptInvoice(liveInvoice.signum() > 0 ? liveInvoice : nz(rc.getInvoiceAmount()));
                r.setReceiptQty(sumReceivedQty(rc.getId()));
                if (r.getPoQty() != null && r.getPoQty().signum() > 0
                        && r.getReceiptQty().compareTo(r.getPoQty()) > 0) {
                    issues.add(new MatchIssue("RECEIPT_QTY_OVER_PO", "WARN",
                            "收货数量超订单：PO " + (r.getPoNo() == null ? "" : r.getPoNo())
                                    + " 订购 " + r.getPoQty() + " < 本单已收 " + r.getReceiptQty()
                                    + "（数量溢出不单独冻结付款，金额侧由「付款不得超订单额」强制校验把关）"));
                }
                if (r.getPoNo() == null) {
                    r.setPoNo(rc.getPoNo());
                }
            }
        } else {
            r.setReceiptNo(null);
            issues.add(new MatchIssue("NO_RECEIPT", "WARN", "未关联收货单"));
        }

        // 3) 发票侧（付款单关联发票合计）
        List<InvBillInvoice> links = invoiceFacade.findLinksByBillId(bill.getId());
        BigDecimal invoiceAmt = links.stream().map(x -> nz(x.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 价税规则归一。收货单（po_receipt.amount）是不含税入账，而关联发票的
        // 覆盖金额是价税合计——直接比较会把"净额收货+价税开票"的标准组合误判为超收货
        // （差额恰为税额）。这里累计关联发票的税额，
        // "发票 ≤ 收货"按不含税规则比较：invoiceAmt − linkedTax ≤ receiptAmount。
        // 发票无税额信息（tax_amount=0/NULL，如非增值税票）时退化为价税合计（向后兼容）。
        BigDecimal linkedTax = linkedTaxOf(links);
        BigDecimal invoiceNetAmt = invoiceAmt.subtract(linkedTax).max(BigDecimal.ZERO);
        r.setInvoiceAmount(invoiceAmt);
        r.setInvoiceNetAmount(invoiceNetAmt);

        // 4) 匹配判定（正确语义：付款 ≤ 发票 ≤ 收货 ≤ PO；容差参数化）
        //   付款 ≤ 发票：PAY_VS_INVOICE 容差参数化（无配置默认 0，即硬约束不允许超付）
        BigDecimal payAllow = tolerance("PAY_VS_INVOICE", invoiceAmt, bill.getCompanyId(), null);
        if (invoiceAmt.add(payAllow).compareTo(r.getPayAmount()) < 0) {
            hasError = true;
            issues.add(new MatchIssue("PAY_EXCEED_INVOICE", "ERROR",
                    "超付：关联发票 " + invoiceAmt + " + 容差 " + payAllow + " < 本次付款 " + r.getPayAmount() + "（无发票覆盖）"));
        }
        // 付款不得超过采购订单额。
        // 上游把「收货超订单」定为 WARN 是有意的（超收是实务常态），但仅靠 WARN 等于
        // "为超收那部分照样付钱"没有任何校验——钱出账、订单无对应义务，正是本项要堵的资金风险。
        // 判据取 PO 订单额（非收货额）：收货额已被超收污染，用它当上限等于用超收数给自己批额度。
        // 容差复用 RECEIPT_VS_PO（与超收同一把尺子，避免出现"超容差按收货拦、按订单不拦"的自相矛盾）。
        // 规则归一：与上方「发票 ≤ 收货」属同一类规则错配——
        //   po_order.amount 是<b>不含税</b>入账额，而付款单 exp_bill.total_amount 是<b>价税合计</b>
        //   = 关联发票价税合计，拿价税付款直接比不含税订单，会把标准组合「净额订单 + 价税付款」
        //   恒判超付（差额恰为税额），付款链在办结放行时被强制校验永久锁死（HTTP 400）。
        //   故这里把付款额折算到<b>不含税规则</b>再比，与发票/收货同尺：
        //   付款净额 = 付款价税 − 关联发票税额（发票无税额信息时 linkedTax=0，退化为价税合计，向后兼容）。
        BigDecimal payNetAmount = r.getPayAmount().subtract(linkedTax).max(BigDecimal.ZERO);
        if (r.getPoAmount() != null && r.getPoAmount().signum() > 0
                && payNetAmount.compareTo(r.getPoAmount()) > 0) {
            BigDecimal poAllow = tolerance("RECEIPT_VS_PO", r.getPoAmount(), bill.getCompanyId(), null);
            if (payNetAmount.compareTo(r.getPoAmount().add(poAllow)) > 0) {
                hasError = true;
                issues.add(new MatchIssue("PAY_EXCEED_PO", "ERROR",
                        "超付订单额（不含税金额）：本次付款净额 " + payNetAmount
                                + "（价税 " + r.getPayAmount() + " − 税 " + linkedTax + "）> 采购订单 "
                                + (r.getPoNo() == null ? "" : r.getPoNo()) + " 金额 " + r.getPoAmount()
                                + (r.getReceiptOverPoAmount() != null && r.getReceiptOverPoAmount().signum() > 0
                                        ? "（该订单收货已超单 " + r.getReceiptOverPoAmount() + "，超出部分无订单义务支撑）"
                                        : "")
                                + " + 容差 " + poAllow
                                + " —— 须先补订单或办溢收手续后调整付款金额，不得为无订单覆盖的部分付款"));
            }
        }
        //   发票 ≤ 收货：容差参数化——收货金额 vs 发票金额的容差取自 match_tolerance 参数表
        // （公司级，amount_pct 百分比 + amount_abs 绝对值，容差金额 = max(收货×pct/100, abs)；
        //   无公司配置/停用时默认 0.5% / 100 元）。超容差 → ERROR 冻结，容差内 → WARN。
        if (r.getReceiptAmount() != null && invoiceNetAmt.compareTo(r.getReceiptAmount()) > 0) {
            BigDecimal over = invoiceNetAmt.subtract(r.getReceiptAmount());
            MatchTolerance tol = matchToleranceService.getTolerance(bill.getCompanyId());
            BigDecimal allow = matchToleranceService.allowedOver(tol, r.getReceiptAmount());
            if (over.compareTo(allow) > 0) {
                hasError = true;
                issues.add(new MatchIssue("INVOICE_OVER_RECEIPT", "ERROR",
                        "发票超收货超容差（不含税金额）：发票净额 " + invoiceNetAmt + "（价税 " + invoiceAmt
                                + " − 税 " + linkedTax + "）> 收货单 " + r.getReceiptAmount() + "，超 " + over + "（容差 " + allow + "）"));
            } else {
                issues.add(new MatchIssue("INVOICE_IN_TOLERANCE", "WARN",
                        "发票超收货但在容差内（不含税金额）：超 " + over + "（容差 " + allow + "）"));
            }
        }
        // 合同环节（四单齐全：合同/PO/收货/发票）：付款单未挂时回退采购订单上的合同，两环节皆无则报缺合同
        Long contractId = bill.getContractId() != null ? bill.getContractId()
                : (poLeg == null ? null : poLeg.getContractId());
        if (contractId != null) {
            ContContract c = contractMapper.selectById(contractId);
            if (c != null) {
                r.setContractId(c.getId());
                r.setContractNo(c.getContractNo());
                r.setContractName(c.getContractName());
                r.setContractAmount(nz(c.getAmount()));
            } else {
                issues.add(new MatchIssue("NO_CONTRACT", "WARN",
                        "关联合同不存在（合同 id=" + contractId + "），四单缺合同"));
            }
        } else {
            issues.add(new MatchIssue("NO_CONTRACT", "WARN",
                    "未关联采购合同：四单缺合同（合同/PO/收货/发票），请补挂合同或说明豁免依据"));
        }
        // 供应商对账（取名三级回退；查询失败时文案如实说明，不把字面量 '无' 带引号拼进 WARN）
        String supplierName = firstNonBlank(pay == null ? null : pay.getPayeeName(),
                bill.getPayeeName(), poLeg == null ? null : poLeg.getSupplierName());
        Long supplierId = (pay != null && "SUPPLIER".equalsIgnoreCase(pay.getPayeeType()) && pay.getPayeeId() != null)
                ? pay.getPayeeId()
                : (poLeg == null ? null : poLeg.getSupplierId());
        // 按名称查不到时，必须回退按供应商档案 id 再查一次。
        //   门户对账单同步过来的 AUTO 行（recon_type='AUTO'、linku_recon_id 非空）只写了 supplier_id，
        //   supplier_name 为 NULL；而 hasConfirmedByName 是按名字精确匹配 ⇒ 这类行即使
        //   status='CONFIRMED' 也永远查不到，匹配台恒挂一条「应付对账（函）未确认」WARN，
        //   正常单因此永远判不到「三单一致」。
        //   若回退只在 supplierId 已有值时才生效，则「无 PO 环节且付款记录未按 SUPPLIER 记 payee_id」
        //   的单据两侧键同时落空 ⇒ 仍恒挂误报。
        //   故补一层「名称 → 供应商档案 id」桥（md_supplier 按名精确反查），把两端键打通后再查确认记录。
        boolean reconConfirmed = supplierName != null && supplierReconService.hasConfirmedByName(supplierName);
        if (!reconConfirmed) {
            Long sid = supplierId;
            if (sid == null && supplierName != null) {
                try {
                    MdSupplier archive = mdSupplierMapper.selectOne(new LambdaQueryWrapper<MdSupplier>()
                            .eq(MdSupplier::getSupplierName, supplierName).last("limit 1"));
                    if (archive != null) {
                        sid = archive.getId();
                    }
                } catch (Exception ex) {
                    // 档案反查属兜底桥，失败不得反噬对账判定本身，仅记录
                    log.warn("[三单匹配] 供应商档案按名反查失败（supplierName={}）: {}", supplierName, ex.getMessage());
                }
            }
            if (sid != null) {
                reconConfirmed = supplierReconService.hasConfirmed(sid);
            }
        }
        r.setReconStatus(reconConfirmed ? "CONFIRMED" : "NONE");
        r.setSupplierName(supplierName);
        if (!reconConfirmed) {
            issues.add(new MatchIssue("RECON_UNCONFIRMED", "WARN",
                    supplierName != null
                            ? "供应商对账未确认（供应商：" + supplierName + "）——请到 应付对账 确认该供应商本期对账单"
                            : "供应商对账状态无法判定：付款记录、单据、采购订单三处均未登记收款方名称"
                                    + (supplierId == null ? "" : "（仅取到供应商档案 id=" + supplierId + "，按档案查亦无已确认对账单）")
                                    + "，请先补全收款方后再核对对账单"));
        }

        // 收款方 vs 供应商：只做「记录＋界面提示」，不拦截付款
        // 以上各环节比对了 金额链（付款≤发票≤收货≤PO）、数量链（订购 vs 已收）、
        // 合同环节、对账环节，<b>唯独没比对「付款单收款方」与「采购订单供应商」是否同一主体</b>。
        // 上方 supplierName 是 firstNonBlank(付款记录名 → 单据名 → 采购订单供应商名) 合成的
        // <b>单一</b>变量（取名三级回退），三者被当成同义词使用、从未互相比较，
        // 于是「钱付给 A、票与货走 B」在名称不一致时不产生任何差异行。
        // 故此处把付款单收款方与采购订单供应商分列，两侧都取得到且确实不符时记一条 WARN。
        // 级别取 WARN 且<b>刻意不置 hasError</b>：付款执行环节的刚性拦截只认 "FAILED"，
        // 本差异不冻结付款、不进执行期拦口（该拦口本身无解拦出口，属待定事项）。
        // 「升级为硬拦（收款方≠供应商即拒付）」＝需产品确认。
        // 收款<b>账号</b>与主数据的核对不在此重复：控制点③ warnIfPayeeWithoutMasterAccount
        // 已在付款执行环节承担该规则，避免第二处真值。
        String payeeName = firstNonBlank(pay == null ? null : pay.getPayeeName(), bill.getPayeeName());
        String payeeBankAccount = firstNonBlank(pay == null ? null : pay.getBankAccount(), bill.getPayeeAccountNo());
        Long payeeSupplierId = (pay != null && "SUPPLIER".equalsIgnoreCase(pay.getPayeeType())) ? pay.getPayeeId() : null;
        String poSupplierName = poLeg == null ? null : firstNonBlank(poLeg.getSupplierName());
        Long poSupplierId = poLeg == null ? null : poLeg.getSupplierId();
        r.setPayeeName(payeeName);
        r.setPayeeBankAccount(payeeBankAccount);
        r.setPoSupplierName(poSupplierName);
        r.setPoSupplierId(poSupplierId);
        // 只在两侧名称/档案 id <b>都取得到</b>且确实不符时报差异（一侧缺＝无从比对，不硬判）。
        // 收款方名完全缺失的情形也不在此重复报：上方 RECON_UNCONFIRMED 的兜底文案已如实报「三处均未登记收款方名称」。
        boolean payeeNameDiffers = payeeName != null && poSupplierName != null && !payeeName.equals(poSupplierName);
        boolean payeeIdDiffers = payeeSupplierId != null && poSupplierId != null
                && !payeeSupplierId.equals(poSupplierId);
        if (payeeNameDiffers || payeeIdDiffers) {
            issues.add(new MatchIssue("PAYEE_NOT_SUPPLIER", "WARN",
                    "收款方与采购订单供应商不是同一主体（提示项，不拦截付款）：收款方[" + payeeName
                            + (payeeBankAccount == null ? "" : " ／ 收款账号 " + payeeBankAccount) + "]"
                            + (payeeSupplierId == null ? "" : " ／ 档案 id " + payeeSupplierId)
                            + " vs 供应商[" + poSupplierName
                            + (poSupplierId == null ? "" : " ／ 档案 id " + poSupplierId) + "]"
                            + "——资金流主体与票据流主体分离，请确认是否属委托付款／第三方代收且有书面依据；"
                            + "若否，请更正付款单收款方或采购订单供应商后再付款"));
        }

        if (hasError) {
            r.setMatchStatus("FAILED");
            r.setFrozen(true);
        } else if (!issues.isEmpty()) {
            r.setMatchStatus("WARN");
            // 仅硬异常(超付/超容差)才冻结付款；缺单、未对账仅提示不冻结
            r.setFrozen(issues.stream().anyMatch(i -> "ERROR".equals(i.getLevel())));
        } else {
            r.setMatchStatus("MATCHED");
            r.setFrozen(false);
        }
        // 叠盖已办结的人工结论——若结论没有写入载体，check() 每次现算现判，
        // 手工匹配链审结后匹配结果仍是 FAILED，人工判定等于白做。
        // 故从 ext_json.matchVerdict 读结论（零 DDL 载体，见 {@link #EXT_MATCH_VERDICT_KEY} 注释）：
        // 手工通过 ⇒ matchStatus=MANUAL_MATCHED、解除冻结；差异明细 issues 与 msg <b>原样保留</b>，
        // 只改"这条差异现在处于什么处置态"，不抹事实。放在 matchCode/msg 定型之后，避免被现算值覆盖。
        r.setMatchCode(issues.isEmpty() ? "MATCHED" : issues.get(0).getCode());
        r.setMsg(issues.stream().map(MatchIssue::getMsg).reduce((a, b) -> a + "；" + b).orElse(""));
        r.setIssues(issues);
        Map<String, String> verdict = readMatchVerdict(bill);
        if (verdict != null && VERDICT_MANUAL_MATCHED.equals(verdict.get("result"))) {
            String before = r.getMatchStatus();
            r.setMatchStatus("MANUAL_MATCHED");
            r.setFrozen(false);
            r.setManualVerdict(verdict.get("reason"));
            r.setManualProcInstId(verdict.get("procInstId"));
            if (hasError) {
                log.warn("[三单匹配] 付款单 {} 自动判定为硬异常({})，已由人工链 {} 判定通过并解除冻结；"
                                + "差异明细仍展示，硬拦开关是否允许人工放行 ERROR 维持不变",
                        bill.getBillNo(), r.getMatchCode(), verdict.get("flowKey"));
            }
            r.setMsg(("〔人工匹配已办结" + (r.getManualVerdict() == null || r.getManualVerdict().isBlank()
                    ? "" : "：" + r.getManualVerdict()) + "，原自动判定 " + before + "〕")
                    + (r.getMsg() == null || r.getMsg().isBlank() ? "" : "；" + r.getMsg()));
        }
        return r;
    }

    /**
     * 参数化容差解析：同维度下按「公司匹配+供应商匹配」特异性取最优先、
     * 且处于生效窗口内的配置；全 NULL 为通用兜底。无命中返回 null（调用方按 0 严格处理）。
     */
    private MdMatchTolerance resolveTolerance(String dimension, Long companyId, Long supplierId) {
        LocalDate today = LocalDate.now();
        List<MdMatchTolerance> all = toleranceMapper.selectList(new LambdaQueryWrapper<MdMatchTolerance>()
                .eq(MdMatchTolerance::getDimensionCode, dimension)
                .and(w -> w.isNull(MdMatchTolerance::getCompanyId).or().eq(MdMatchTolerance::getCompanyId, companyId))
                .and(w -> w.isNull(MdMatchTolerance::getSupplierId).or().eq(MdMatchTolerance::getSupplierId, supplierId)));
        MdMatchTolerance best = null;
        int bestScore = -1;
        for (MdMatchTolerance t : all) {
            if (t.getEffectDate() != null && t.getEffectDate().isAfter(today)) continue; // 未生效
            if (t.getExpireDate() != null && t.getExpireDate().isBefore(today)) continue; // 已失效
            int score = (t.getCompanyId() != null ? 2 : 0) + (t.getSupplierId() != null ? 1 : 0);
            if (score > bestScore) {
                best = t;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * 计算某维度允许容差金额：PERCENT=base×value/100；FIXED=value；无配置或非法=0（严格）。
     * 支持按组织/供应商差异化配置+生效期，特异性优先。
     */
    private BigDecimal tolerance(String dimension, BigDecimal base, Long companyId, Long supplierId) {
        MdMatchTolerance t = resolveTolerance(dimension, companyId, supplierId);
        if (t == null || t.getToleranceValue() == null) {
            return BigDecimal.ZERO;
        }
        if ("FIXED".equalsIgnoreCase(t.getToleranceType())) {
            return t.getToleranceValue();
        }
        BigDecimal b = base == null ? BigDecimal.ZERO : base;
        return b.multiply(t.getToleranceValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 按顺序取第一个非空白串，全空返回 null（供应商名三级回退，取不到就如实报缺，不拼占位值） */
    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c.trim();
            }
        }
        return null;
    }

    /** 订购数量＝该订单有效行项目数量合计 */
    private BigDecimal sumOrderedQty(Long poId) {
        return poOrderLineMapper.selectList(new LambdaQueryWrapper<PoOrderLine>()
                        .eq(PoOrderLine::getPoId, poId))
                .stream().map(x -> nz(x.getQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 已收数量＝存货移动台账（stock_move·RECEIVE）按收货单合计 */
    private BigDecimal sumReceivedQty(Long receiptId) {
        return stockMoveMapper.selectList(new LambdaQueryWrapper<StockMove>()
                        .eq(StockMove::getMoveType, "RECEIVE")
                        .eq(StockMove::getSourceDocType, "PO_RECEIPT")
                        .eq(StockMove::getSourceDocId, receiptId))
                .stream().map(x -> nz(x.getQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 收货单已开票金额＝指向该收货单的付款单所勾稽的发票金额合计（勾稽明细为真相源） */
    private BigDecimal sumLinkedInvoiceAmount(Long receiptId) {
        return linkedInvoicesOf(receiptId).stream()
                .map(x -> nz(x.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 收货单已开票<b>净额</b>（不含税）＝勾稽发票价税合计 − 关联发票税额。
     * <p>po_receipt.amount 是不含税入账额，回写列与「开票 ≤ 收货」强制校验都必须用同一把不含税尺子；
     * 发票无税额信息（tax_amount=0/NULL）时退化为价税合计（向后兼容）。
     * 供本类 syncReceiptInvoiceColumn 与 PurchaseService.markInvoiced 共用，避免两处各算一套。
     */
    public BigDecimal sumLinkedInvoiceNetAmount(Long receiptId) {
        List<InvBillInvoice> links = linkedInvoicesOf(receiptId);
        BigDecimal gross = links.stream().map(x -> nz(x.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return gross.subtract(linkedTaxOf(links)).max(BigDecimal.ZERO);
    }

    /** 指向该收货单的付款单所勾稽的发票明细行 */
    private List<InvBillInvoice> linkedInvoicesOf(Long receiptId) {
        List<Long> billIds = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                        .eq(ExpBill::getPoReceiptId, receiptId))
                .stream().map(ExpBill::getId).filter(Objects::nonNull).toList();
        if (billIds.isEmpty()) {
            return List.of();
        }
        return invoiceFacade.findLinksByBillIds(billIds);
    }

    /** 勾稽明细对应发票的税额合计（发票缺失或无税额信息按 0） */
    private BigDecimal linkedTaxOf(List<InvBillInvoice> links) {
        BigDecimal tax = BigDecimal.ZERO;
        for (InvBillInvoice link : links) {
            if (link.getInvoiceId() == null) continue;
            InvInvoice inv = invoiceFacade.findById(link.getInvoiceId());
            if (inv != null && inv.getTaxAmount() != null) {
                tax = tax.add(inv.getTaxAmount());
            }
        }
        return tax;
    }

    // 容差配置（键值型，前端可调整）

    /** 全部容差配置（3 个维度） */
    public List<MdMatchTolerance> listTolerances() {
        return toleranceMapper.selectList(new LambdaQueryWrapper<MdMatchTolerance>()
                .orderByAsc(MdMatchTolerance::getDimensionCode));
    }

    /** 新增或更新某维度容差（按 tenant_id + dimension_code upsert；不改租户） */
    @Transactional(rollbackFor = Exception.class)
    public MdMatchTolerance upsertTolerance(MdMatchTolerance in) {
        if (in == null || in.getDimensionCode() == null || in.getDimensionCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "维度编码不能为空");
        }
        // 必填守卫（与前端 PAY_TOL_SCOPE 登记表同一规则）：容差类型/容差值是规则本身的内容，
        // 若静默补默认值 PERCENT / 0，等于凭默认造出一条用户没写的容差规则。
        if (in.getToleranceType() == null || in.getToleranceType().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择容差类型");
        }
        if (in.getToleranceValue() == null || in.getToleranceValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入容差值");
        }
        // upsert 按「维度+公司+供应商」定位（通用行三字段均 NULL）
        MdMatchTolerance exist = toleranceMapper.selectOne(new LambdaQueryWrapper<MdMatchTolerance>()
                .eq(MdMatchTolerance::getDimensionCode, in.getDimensionCode())
                .eq(in.getCompanyId() != null, MdMatchTolerance::getCompanyId, in.getCompanyId())
                .isNull(in.getCompanyId() == null, MdMatchTolerance::getCompanyId)
                .eq(in.getSupplierId() != null, MdMatchTolerance::getSupplierId, in.getSupplierId())
                .isNull(in.getSupplierId() == null, MdMatchTolerance::getSupplierId)
                .last("limit 1"));
        if (exist == null) {
            MdMatchTolerance nu = new MdMatchTolerance();
            nu.setDimensionCode(in.getDimensionCode());
            nu.setCompanyId(in.getCompanyId());
            nu.setSupplierId(in.getSupplierId());
            nu.setMaterialGroup(in.getMaterialGroup());
            nu.setToleranceType(in.getToleranceType());
            nu.setToleranceValue(in.getToleranceValue());
            nu.setRemark(in.getRemark());
            nu.setEffectDate(in.getEffectDate());
            nu.setExpireDate(in.getExpireDate());
            // 唯一键换代(tenant,dimension,scope_key)后并发/软删残留仍可能撞键 —— 转义为可诊断业务异常
            try {
                toleranceMapper.insert(nu);
            } catch (DuplicateKeyException dk) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "该维度下同作用域的容差规则已存在（或存在已删除残留行），请改用编辑或调整公司/供应商作用域");
            }
            return nu;
        }
        exist.setToleranceType(in.getToleranceType());
        exist.setToleranceValue(in.getToleranceValue());
        exist.setRemark(in.getRemark());
        exist.setEffectDate(in.getEffectDate());
        exist.setExpireDate(in.getExpireDate());
        toleranceMapper.updateById(exist);
        return exist;
    }

    /** 删除容差配置行（逻辑删） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTolerance(Long id) {
        toleranceMapper.deleteById(id);
    }

    @Data
    public static class MatchIssue {
        private String code;
        private String level;
        private String msg;

        public MatchIssue(String code, String level, String msg) {
            this.code = code;
            this.level = level;
            this.msg = msg;
        }
    }

    @Data
    public static class MatchRow {
        private Long billId;
        private String billNo;
        private String title;
        private BigDecimal payAmount;
        // 采购订单
        private Long poId;
        private String poNo;
        private BigDecimal poAmount;
        private BigDecimal poReceived;
        /** 订购数量（po_order_line 合计） */
        private BigDecimal poQty;
        /**
         * 收货超出订单的金额（{@code poReceived − poAmount}，无超收时为 null）。
         * 供「付款不得超订单额」强制校验（{@code PAY_EXCEED_PO}）在文案里指明超收规模 ——
         * 只报"付款 > 订单"用户不知道该去补哪一笔，报出超收额才能定位到要补的订单/溢收手续。
         */
        private BigDecimal receiptOverPoAmount;
        // 收货单
        private Long receiptId;
        private String receiptNo;
        private BigDecimal receiptAmount;
        private BigDecimal receiptInvoice;
        /** 已收数量（stock_move·RECEIVE 合计） */
        private BigDecimal receiptQty;
        // 发票
        private BigDecimal invoiceAmount;

        /** 关联发票价税合计 − 税额（不含税规则，与收货单同一规则比较用） */
        private BigDecimal invoiceNetAmount;
        // 合同（展示维度）
        private Long contractId;
        private String contractNo;
        private String contractName;
        private BigDecimal contractAmount;
        /** MATCHED 三单一致 / WARN 缺收货/缺发票/未对账/容差内 / FAILED 超付或超容差
         *  / MANUAL_MATCHED 人工匹配链已办结通过（结论落 ext_json.matchVerdict，自动判定被叠盖） */
        private String matchStatus;
        /** 主异常码：MATCHED / PAY_EXCEED_INVOICE / INVOICE_OVER_RECEIPT / RECEIPT_OVER_PO / NO_PO / NO_RECEIPT / RECON_UNCONFIRMED ... */
        private String matchCode;
        /** 人工判定理由（写入数据库于 exp_bill.ext_json.matchVerdict.reason），无人工结论时为 null */
        private String manualVerdict;
        /** 办结该结论的流程实例号（反查审批人/审批链的抓手） */
        private String manualProcInstId;
        /** 是否冻结付款（FAILED 或超容差 ERROR） */
        private boolean frozen;
        /** 类型化异常明细 */
        private List<MatchIssue> issues = new ArrayList<>();
        /** CONFIRMED 供应商对账已确认 / NONE 未确认 */
        private String reconStatus;
        /** 对账对象的供应商名（付款记录→单据→采购订单三级回退后的实际查询结果，取不到为 null） */
        private String supplierName;
        /**
         * 付款单侧登记的收款方名（付款记录 → 单据两级回退）。
         * 与 {@link #supplierName} <b>分列</b>存放：若用 firstNonBlank 合成为同一个变量，
         * 界面与判据都只看到一个名字，「收款方≠供应商」既比不出来也无从展示。
         */
        private String payeeName;
        /** 收款方银行账号（付款记录 bank_account 优先，退回单据 payee_account_no） */
        private String payeeBankAccount;
        /** 采购订单上的供应商名＝与收款方比对的另一侧（未关联 PO 时为 null，不硬判） */
        private String poSupplierName;
        /** 采购订单供应商档案 id（md_supplier.id） */
        private Long poSupplierId;
        private String msg;
        private String billStatus;
    }
}
