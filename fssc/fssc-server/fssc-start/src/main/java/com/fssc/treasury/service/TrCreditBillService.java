package com.fssc.treasury.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.receivable.entity.RcReceipt;
import com.fssc.receivable.entity.RcReceiptLine;
import com.fssc.receivable.mapper.RcReceiptLineMapper;
import com.fssc.receivable.mapper.RcReceiptMapper;
import com.fssc.sharing.entity.OpBankStatement;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpBankStatementMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.system.service.NumberRuleService;
import com.fssc.treasury.entity.TrCreditBill;
import com.fssc.treasury.mapper.TrCreditBillMapper;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.service.WfEngineFacade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 资金收款单服务。
 * receiveRecord（渠道流水认领生成应收收款 PENDING）-> autoRecon（与银行流水自动对账 RECONCILED）-> confirmRecon（人工核销）。
 * 收款认领即发起 TR_RECEIPT 资金收款确认审批流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrCreditBillService {

    /** 收款方向银行流水的交易类型 */
    private static final List<String> IN_TYPES = List.of("CREDIT", "RECEIPT", "IN");

    private final TrCreditBillMapper creditMapper;
    private final OpBankStatementMapper statementMapper;
    private final WfEngineFacade wfEngineFacade;
    private final NumberRuleService numberRuleService;
    private final OrgGuard orgGuard;
    /**
     * 丙：到账环节 ↔ 核销环节 勾稽体检的三张只读 mapper。
     * 跨域引用与本包既有惯例一致（本类已引 sharing 域的 OpBankStatement/Mapper；
     * bank 域的 BankReconcileService:133 也直接读 rc_receipt）。仅用于 {@link #reconTrace(Long)}
     * 的 SELECT，本类所有写方法不触碰它们。
     */
    private final OpVoucherMapper voucherMapper;
    private final RcReceiptMapper rcReceiptMapper;
    private final RcReceiptLineMapper rcReceiptLineMapper;
    /**
     * 丙：只读取 TR_RECEIPT 流程实例的发起人当"建单人"——tr_credit_bill 无建单列，
     * 而 {@code receiveRecord()} 起流时已把登录人写进 wf_instance.start_user_id，是现成记录。
     * 本字段仅 SELECT，不写流程表、不改审批环节。
     */
    private final WfInstanceMapper wfInstanceMapper;

    /** 渠道流水认领，生成应收收款（PENDING 已认领） */
    public Long receiveRecord(TrCreditBill bill) {
        if (bill.getAmount() == null || bill.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收款金额必须大于 0");
        }
        if (!StringUtils.hasText(bill.getCurrencyCode())) bill.setCurrencyCode("CNY");
        if (!StringUtils.hasText(bill.getBillNo())) bill.setBillNo(nextCreditBillNo());
        bill.setStatus("PENDING");
        bill.setReconFlag(Boolean.FALSE);
        bill.setReconTime(null);
        bill.setId(null);
        bill.setTenantId(TenantContext.getTenantId());
        if (bill.getCompanyId() == null || bill.getCompanyId() <= 0) {
            bill.setCompanyId(orgGuard.currentCompany());
        }
        orgGuard.assertCompanyAllowed(bill.getCompanyId());
        creditMapper.insert(bill);
        // 收款认领即发起 TR_RECEIPT 资金收款确认审批流（出纳获取到款→资金岗确认→共享会计核销）
        try {
            String procInstId = wfEngineFacade.start("TR_RECEIPT", bill.getId(), userId(),
                    "资金收款-" + bill.getBillNo(), bill.getAmount());
            TrCreditBill flowUpd = new TrCreditBill();
            flowUpd.setId(bill.getId());
            flowUpd.setProcInstId(procInstId);
            creditMapper.updateById(flowUpd);
        } catch (Exception ex) {
            log.error("收款单 {} 发起收款确认审批失败: {}", bill.getId(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return bill.getId();
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    /**
     * 与银行流水自动对账：对 PENDING 收款单按金额匹配未对账的收款方向银行流水，
     * 命中则标记收款单 RECONCILED 并回写银行流水 recon_status=MATCHED。返回核销条数。
     */
    @Transactional
    public int autoRecon() {
        List<TrCreditBill> pending = creditMapper.selectList(new LambdaQueryWrapper<TrCreditBill>()
                .eq(TrCreditBill::getStatus, "PENDING")
                .last("limit 200"));
        int count = 0;
        // 本人建单—本人核销的单据号（批量端点只回条数，提示位落日志）
        List<String> selfReconNos = new ArrayList<>();
        Map<Long, Long> creators = creatorsOf(pending.stream().map(TrCreditBill::getId).toList());
        Long operator = userId();
        for (TrCreditBill bill : pending) {
            OpBankStatement st = findStatement(bill.getAmount());
            if (st == null) continue;
            bill.setStatus("RECONCILED");
            bill.setReconFlag(Boolean.TRUE);
            bill.setReconTime(LocalDateTime.now());
            creditMapper.updateById(bill);
            if (selfReconWarning(creators.get(bill.getId()), operator) != null) {
                selfReconNos.add(bill.getBillNo() == null ? "id=" + bill.getId() : bill.getBillNo());
            }

            OpBankStatement upd = new OpBankStatement();
            upd.setId(st.getId());
            upd.setReconStatus("MATCHED");
            upd.setReconBillId(bill.getId());
            // 本链写的是 tr_credit_bill.id，而银企勾对链往同一列写 rc_receipt.id（两个自增序列会撞号）
            // ⇒ 判别列随行同写，反查时才有载体可依，不必再靠调用方上下文猜表
            upd.setReconBillType("TR_CREDIT_BILL");
            upd.setReconTime(LocalDateTime.now());
            statementMapper.updateById(upd);
            count++;
        }
        if (!selfReconNos.isEmpty()) {
            log.warn("资金收款自动核销复核提示：{} 条由建单人本人核销（不阻断，建议他人复核）：{}",
                    selfReconNos.size(), String.join("、", selfReconNos));
        }
        log.info("资金收款自动对账完成：{} 条", count);
        return count;
    }

    /**
     * 人工核销（确认对账）。
     * <p>丙档：只做提示校验——确认核销的人与该单建单人相同时，返回非阻断的复核提示位
     * （{@code selfRecon}／{@code warning}），核销照常完成、不抛异常、不动审批环节与权限。
     * <p>返回体由 Void 改为结构化 Map 是<b>增量</b>：该端点原 data 恒为 null，现调用方
     * 资金收款看板 忽略返回值，查询改从 data 里读不影响既有行为。
     */
    @Transactional
    public Map<String, Object> confirmRecon(Long id) {
        TrCreditBill bill = requireExist(id);
        Long creator = creatorsOf(List.of(id)).get(id);
        Long operator = userId();
        if (!"RECONCILED".equals(bill.getStatus())) {
            bill.setStatus("RECONCILED");
            bill.setReconFlag(Boolean.TRUE);
            bill.setReconTime(LocalDateTime.now());
            creditMapper.updateById(bill);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("creditBillId", bill.getId());
        res.put("billNo", bill.getBillNo());
        res.put("status", bill.getStatus());
        res.put("reconFlag", bill.getReconFlag());
        res.put("reconTime", bill.getReconTime());
        String warning = selfReconWarning(creator, operator);
        res.put("selfRecon", warning != null);
        res.put("warning", warning);
        if (warning != null) {
            log.warn("资金收款单 {} 复核提示：{}", bill.getBillNo(), warning);
        }
        return res;
    }

    /**
     * 丙：建单人一次批量取（避开自动核销的逐单查流程表）。
     *
     * @return 收款单 id → 建单人（无流程实例／发起人为 0 时该键缺位，一律按"判不出"处理，不猜人）
     */
    private Map<Long, Long> creatorsOf(List<Long> billIds) {
        Map<Long, Long> map = new HashMap<>();
        if (billIds == null || billIds.isEmpty()) return map;
        List<WfInstance> ins = wfInstanceMapper.selectList(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBizType, "TR_RECEIPT")
                        .in(WfInstance::getBizId, billIds)
                        .orderByAsc(WfInstance::getId));
        for (WfInstance wi : ins) {
            if (wi.getBizId() == null || wi.getStartUserId() == null || wi.getStartUserId() == 0L) continue;
            map.putIfAbsent(wi.getBizId(), wi.getStartUserId());
        }
        return map;
    }

    /**
     * 丙：同人建单并同人确认核销的复核提示；判不出人或判不出单时为 null（宁缺不猜）。
     * <p>制单↔审批的分岗要求由流程节点候选承担，本提示只覆盖"建单—确认核销"这条不经审批的旁路。
     */
    private String selfReconWarning(Long creator, Long operator) {
        if (creator == null || operator == null || !creator.equals(operator)) return null;
        return "该笔收款由本人建单并本人确认核销，未经他人复核，建议由其他资金岗复核本笔核销与到账一致性。";
    }

    /** 按金额匹配未对账的收款方向银行流水 */
    private OpBankStatement findStatement(BigDecimal amount) {
        if (amount == null) return null;
        List<OpBankStatement> hit = statementMapper.selectList(new LambdaQueryWrapper<OpBankStatement>()
                .and(w -> w.isNull(OpBankStatement::getReconStatus)
                        .or().in(OpBankStatement::getReconStatus, "UNRECON", "UNMATCHED"))
                .in(OpBankStatement::getTransType, IN_TYPES)
                .eq(OpBankStatement::getAmount, amount)
                .orderByAsc(OpBankStatement::getId)
                .last("limit 1"));
        return hit.isEmpty() ? null : hit.get(0);
    }

    public TrCreditBill detail(Long id) {
        return requireExist(id);
    }

    public PageResult<TrCreditBill> page(long pageNum, long pageSize, String status) {
        LambdaQueryWrapper<TrCreditBill> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) qw.eq(TrCreditBill::getStatus, status);
        qw.orderByDesc(TrCreditBill::getId);
        Page<TrCreditBill> p = creditMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    private TrCreditBill requireExist(Long id) {
        TrCreditBill bill = creditMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收款单不存在：" + id);
        }
        return bill;
    }

    // 丙档：到账环节 ↔ 核销环节 勾稽预警与联查提示（只读，不改账）

    /**
     * 到账环节与核销环节的勾稽体检（<b>纯读</b>：不写 tr_credit_bill、不写 rc_receipt、不出证、不冲减余额）。
     *
     * <h3>两环节现状（读准的结论，均有 file:line 与现库读数支撑）</h3>
     * <ul>
     *   <li><b>到账环节</b>：{@code receiveRecord()} 建 tr_credit_bill 并起 TR_RECEIPT 流
     *       本类 :43-64；审批收尾在 {@code TreasuryBizAction:145-150} 置 RECONCILED、
     *       {@code TreasuryBizAction:164} 调 {@code VoucherService.generateReceiptVoucher}
     *       （{@code VoucherService:2571+}，借 1002 银行存款／贷 1122 应收账款）。
     *       出证，但零核销——全链路不碰 rc_receipt / rc_receivable。</li>
     *   <li><b>核销环节</b>：{@code RcReceiptService.create()}
     *       建 rc_receipt ＋ rc_receipt_line 并同事务冲减 rc_receivable.received/remain。
     *       核销，但零出证——{@code RcReceiptService} 全文无 {@code voucherService} 引用
     *       代码里没有任何 {@code voucherService} 引用，{@code op_voucher} 按 source_doc_type 分组<b>也没有 RC_RECEIPT 这一类</b>：
     *       收款核销这一环核销了金额，却不落凭证。</li>
     *   <li><b>共同键：没有</b>。{@code tr_credit_bill} 与 {@code rc_receipt} 互不引用对方主键，
     *       两侧只剩「customer_id ＋ amount ＋ 日期」这组业务同义维度可对齐——
     *       只能"靠同日同额人眼对齐"。两表的对应关系任何一侧都查不到对方的 id。</li>
     *   <li><b>唯一的名义抓手是死列</b>：{@code rc_receipt.bank_serial}（注释即"勾对的银行流水号"）
     *       当前全是空值、bank_matched 也全为 0；登记环节 {@code RcReceiptService.create()}
     *       从不赋值它，只有银企自动勾对（{@code BankReconcileService}）会回写。
     *       因此它不能作为两环节的公共外键使用。</li>
     *   <li><b>反向指针还撞车</b>：{@code op_bank_statement.recon_bill_id} 是单个 bigint、<b>无类型判别列</b>
     *       （op_bank_statement 全列没有 recon_bill_type），却被两条自动勾对链写入<b>不同的表</b>：
     *       本类 {@code autoRecon()} 写 {@code tr_credit_bill.id}，
     *       {@code BankReconcileService} 写 {@code rc_receipt.id}。
     *       表里暂无数据 ⇒ 尚未发作，属潜伏面（本方法如实报告，不擅自"猜表"解析）。</li>
     * </ul>
     *
     * <h3>为什么只落丙档</h3>
     * 甲（到账环节审批通过时按金额自动核销）＝错配风险，同额多客户/一客户多笔同额时会把 A 的到账冲到 B 的应收上；
     * 乙（核销环节补出证）＝与到账环节「贷 1122」方向冲突，会产生双份银行凭证。
     * 两者都属跨域打通＋账务规则待定，交产品确认。本方法只加预警与联查提示，
     * <b>不改账、不改任何状态、不新建流程</b>，与联查读侧 {@code BillTraceChainController}
     * 亦不冲突——该类全文不含 tr_credit_bill／rc_receipt／TR_RECEIPT 任何一处，
     * 这两个环节本就不在它覆盖面内（且该类禁改，故新方法另起于本域）。
     *
     * @param id tr_credit_bill 主键
     * @return 结构化体检结果：到账环节凭证态、核销环节候选（按客户＋金额现算）、无共同键声明、流水指针多义性
     */
    public Map<String, Object> reconTrace(Long id) {
        TrCreditBill bill = requireExist(id);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("creditBillId", bill.getId());
        res.put("creditBillNo", bill.getBillNo());
        res.put("customerId", bill.getCustomerId());
        res.put("amount", bill.getAmount());
        res.put("receiveDate", bill.getReceiveDate());
        res.put("status", bill.getStatus());
        res.put("reconFlag", bill.getReconFlag());
        res.put("reconTime", bill.getReconTime());

        // ① 到账环节出证侧：TR_RECEIPT 凭证（四键维持收款单坐标，已裁不可挪用）
        List<OpVoucher> vouchers = voucherMapper.selectList(
                new LambdaQueryWrapper<OpVoucher>()
                        .eq(OpVoucher::getSourceDocType, "TR_RECEIPT")
                        .eq(OpVoucher::getSourceDocId, id)
                        .orderByAsc(OpVoucher::getId));
        List<Map<String, Object>> voucherRows = new ArrayList<>();
        for (OpVoucher v : vouchers) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("voucherId", v.getId());
            vm.put("voucherNo", v.getVoucherNo());
            vm.put("summary", v.getSummary());
            vm.put("debitTotal", v.getDebitTotal());
            vm.put("status", v.getStatus());
            voucherRows.add(vm);
        }
        res.put("receiptVouchers", voucherRows);
        res.put("hasVoucher", !voucherRows.isEmpty());

        // ② 核销环节候选：同客户＋同金额的 rc_receipt。这是启发式现算，不是勾稽事实。
        List<RcReceipt> cands = rcReceiptMapper.selectList(
                new LambdaQueryWrapper<RcReceipt>()
                        .eq(bill.getCustomerId() != null,
                                RcReceipt::getCustomerId, bill.getCustomerId())
                        .eq(bill.getAmount() != null,
                                RcReceipt::getAmount, bill.getAmount())
                        .orderByAsc(RcReceipt::getId));
        List<Map<String, Object>> rcRows = new ArrayList<>();
        for (RcReceipt rc : cands) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("receiptId", rc.getId());
            rm.put("receiptBillNo", rc.getBillNo());
            rm.put("amount", rc.getAmount());
            rm.put("customerId", rc.getCustomerId());
            rm.put("receiptDate", rc.getReceiptDate());
            rm.put("status", rc.getStatus());
            // 名义勾稽列现状：登记环节从不写，只有银企自动勾对会写 ⇒ 全 NULL 属预期，不是数据损坏
            rm.put("bankSerial", rc.getBankSerial());
            rm.put("bankMatched", rc.getBankMatched());
            List<RcReceiptLine> lines = rcReceiptLineMapper.selectList(
                    new LambdaQueryWrapper<RcReceiptLine>()
                            .eq(RcReceiptLine::getReceiptId, rc.getId()));
            rm.put("offsetReceivableNos", lines.stream()
                    .map(RcReceiptLine::getReceivableNo)
                    .filter(Objects::nonNull).toList());
            rcRows.add(rm);
        }
        res.put("writeoffCandidates", rcRows);
        res.put("writeoffCandidateCount", rcRows.size());

        // ③ 共同键声明：两环节之间不存在外键，候选只是"客户＋金额"现算
        res.put("hasCommonForeignKey", false);
        res.put("matchBasis", "customer_id ＋ amount 现算（无外键，非勾稽事实）");
        boolean sameDay = rcRows.stream().anyMatch(x -> bill.getReceiveDate() != null
                && bill.getReceiveDate().equals(x.get("receiptDate")));
        List<String> warns = new ArrayList<>();
        if (rcRows.isEmpty()) {
            warns.add("到账记录没有同客户同金额的收款核销单：本笔到账只在资金业务中记录，"
                    + "应收子账（rc_receivable.remain_amount）从未因此冲减，账证与子账可能不符");
        } else {
            if (rcRows.size() > 1) {
                warns.add("同客户同金额的收款核销单有 " + rcRows.size() + " 张，无法判定本笔到账对应哪一张——"
                        + "两条记录没有共同单号，此处仅并列提示，不构成勾稽结论");
            }
            if (sameDay) {
                warns.add("存在与到账日期同日的核销单：属「同日同额」人眼对齐特征，"
                        + "一旦跨日或多笔同额即失效，请人工确认后再据此调账");
            }
        }
        if (!voucherRows.isEmpty() && rcRows.isEmpty()) {
            warns.add("到账记录已生成凭证（贷 1122 应收账款）而核销记录无对应单：总账已 relief 应收、"
                    + "应收子账未冲减，属总账与子账两套数据并存，需人工核对");
        }
        res.put("warnings", warns);

        // ④ 银行流水反向指针多义性（如实报告，不猜表解析）
        List<OpBankStatement> claimed = statementMapper.selectList(new LambdaQueryWrapper<OpBankStatement>()
                .eq(OpBankStatement::getReconBillId, id));
        res.put("statementPointedById", claimed.size());
        if (!claimed.isEmpty()) {
            res.put("reconBillIdAmbiguity", "op_bank_statement.recon_bill_id 无类型判别列，"
                    + "本类 autoRecon() 写入 tr_credit_bill.id、银企勾对写入 rc_receipt.id，"
                    + "同一列指向两张表，反查时不可默认为本收款单");
        }
        return res;
    }

    private String nextCreditBillNo() {
        try {
            return numberRuleService.next("TREASURY_CREDIT");
        } catch (Exception e) {
            return "CR" + System.currentTimeMillis();
        }
    }
}