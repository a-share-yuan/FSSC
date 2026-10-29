// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.bank.entity.BankBalance;
import com.fssc.bank.entity.BankReconcile;
import com.fssc.bank.mapper.BankBalanceMapper;
import com.fssc.bank.entity.BankReconItem;
import com.fssc.bank.mapper.BankReconItemMapper;
import com.fssc.bank.mapper.BankReconcileMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.framework.facade.ArchiveFacade;
import com.fssc.framework.facade.dto.ArchiveRegisterDTO;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.masterdata.entity.MdBankAccount;
import com.fssc.masterdata.mapper.MdBankAccountMapper;
import com.fssc.receivable.entity.RcReceipt;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceiptMapper;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.sharing.entity.OpBankStatement;
import com.fssc.sharing.mapper.OpBankStatementMapper;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.Collections;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 银企直连对账（需求文档 12.7）。
 * 银行流水 ↔ 付款记录自动勾对：按 银行流水号 精确匹配；未命中按 金额+账号 二次匹配。
 * 勾对成功标记流水 recon_status=MATCHED 并写 bank_reconcile 记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankReconcileService {

    private static final List<String> PAID_STATUS = List.of("PAID", "SUCCESS");

    /**
     * 实测值集：op_bank_statement.trans_type 中属于「收款（贷方）方向」的取值。
     * <p>实测本仓库对该列的三处判定同源：本类 {@link #isCreditStatement}、本类 computeSheet 的
     * bankIn/bankOut 分流、资金域收款单服务的 IN_TYPES，均为 {@code CREDIT / RECEIPT / IN}
     * （大小写不敏感，列注释另记 DEBIT＝借方/付款方向）。
     * <p>值集收敛到这一处，方向校验与自动勾对、调节表分流不会各自漂移。
     */
    private static final List<String> CREDIT_TRANS_TYPES = List.of("CREDIT", "RECEIPT", "IN");

    private final OpBankStatementMapper statementMapper;
    private final ExpBillPaymentMapper paymentMapper;
    /** payment_no 落 exp_bill 单号需要按 bill_id 反查单据 */
    private final ExpBillMapper expBillMapper;
    private final BankReconcileMapper reconcileMapper;
    private final MdBankAccountMapper bankAccountMapper;
    private final RcReceivableMapper receivableMapper;
    private final RcReceiptMapper receiptMapper;
    private final CrmCustomerMapper customerMapper;
    private final BankBalanceMapper bankBalanceMapper;
    private final ArchiveFacade archiveFacade;
    private final BankReconItemMapper reconItemMapper;
    private final WfEngineFacade wfEngineFacade;
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    /**
     * 自动勾对：返回本轮结果 {scanned,matched,unmatched,ambiguous,foreignSerialBlocked,
     * unmatchedSerials,matchedAmount,processStarted}。
     * <p>未勾对＝仍会进未达账项，必须当场反馈给出纳，而不是等调节表环节才被发现；
     * 反馈体里带上未勾对的流水号与拒配原因（多候选 / 同金额单据已归属他行流水号），操作员一眼能核对漏了几条。
     * <p>模糊命中唯一性：同金额的候选不止一条、或候选已归属本轮另一条流水号 ⇒ 一律拒配转人工（见
     * {@link #matchCandidates}／{@link #findReceiptMatches}），杜绝两笔"金额相同、流水号不同"互相顶掉。
     */
    public Map<String, Object> run() {
        List<OpBankStatement> un = statementMapper.selectList(new LambdaQueryWrapper<OpBankStatement>()
                .and(w -> w.isNull(OpBankStatement::getReconStatus)
                        .or().in(OpBankStatement::getReconStatus, "UNRECON", "UNMATCHED"))
                .orderByAsc(OpBankStatement::getId));
        int count = 0;
        int ambiguous = 0;
        // 本轮因"同金额的付款/核销单已归属另一条银行流水号"而被剔除的流水条数（结果反馈规则）
        int foreignBlocked = 0;
        // 模糊命中唯一性——本轮未勾对流水的"流水号 → 流水"归属表（零额外查询，直接用 un）。
        // 付款记录自带银行流水号时，它就属于那条流水；本轮另一条流水不得按金额把它抢走（互顶）。
        Map<String, Long> serialOwner = new HashMap<>();
        for (OpBankStatement s : un) {
            if (s.getBankSerial() != null && !s.getBankSerial().isBlank()) {
                serialOwner.putIfAbsent(s.getBankSerial().trim(), s.getId());
            }
        }
        List<String> unmatchedSerials = new ArrayList<>();
        // 丙：本轮"本人建单—本人勾对核销"的收款核销单标识（非阻断，只提示）
        List<String> selfMatchedReceipts = new ArrayList<>();
        Long reconOperator = userId();
        // 一次勾对可能命中多条流水 ⇒ 逐条收集「记录行 id → 本行金额」，
        // 勾对完成后一记录行起一个 TR_BANK_RECON 实例（bank_reconcile 行 : 实例 = 1:1），
        // 不再用「末行 id + 全批累计金额」单起一批（单实例多行会让审批明细与流程金额都失真）。
        Map<Long, BigDecimal> reconFlowSeeds = new LinkedHashMap<>();
        // 本轮已占用的付款记录/收款核销单：同一轮内两条流水不得顶掉同一条
        Set<Long> consumed = new HashSet<>();
        Set<Long> consumedReceipts = new HashSet<>();
        // 批次3 R3-D：本轮勾对累计金额——批次汇总反馈规则（后流程金额按行取本行金额，本累计不再充当流程金额）
        BigDecimal matchedAmount = BigDecimal.ZERO;
        for (OpBankStatement st : un) {
            // 收款方向：贷方流水按 金额相等 + 往来单位名相等 保守勾对 rc_receipt（企业已收银行未收 → 已达）
            if (isCreditStatement(st)) {
                List<String> rcForeign = new ArrayList<>();
                List<RcReceipt> rcCands = findReceiptMatches(st, consumedReceipts, rcForeign);
                if (rcCands.size() > 1) {
                    ambiguous++;
                    unmatchedSerials.add(label(st) + "（同金额同往来候选 " + rcCands.size() + " 条，拒绝模糊顶替）");
                    continue;
                }
                if (rcCands.isEmpty()) {
                    if (!rcForeign.isEmpty()) foreignBlocked++;
                    unmatchedSerials.add(label(st) + foreignSuffix(rcForeign));
                    continue;
                }
                RcReceipt rc = rcCands.get(0);
                consumedReceipts.add(rc.getId());
                // 丙：收款核销单由本人建单、又由本人勾对核销 ⇒ 复核提示（不拦勾对）
                if (reconOperator != null && rc.getCreateBy() != null && rc.getCreateBy() != 0L
                        && reconOperator.equals(rc.getCreateBy())) {
                    selfMatchedReceipts.add((rc.getBillNo() == null ? "id=" + rc.getId() : rc.getBillNo()));
                }
                // 标记流水已勾对
                OpBankStatement cupd = new OpBankStatement();
                cupd.setId(st.getId());
                cupd.setReconStatus("MATCHED");
                cupd.setReconBillId(rc.getId());
                // rc 是 rc_receipt：判别列必须与指针同写，否则下游拿到 id 只能猜表
                cupd.setReconBillType("RC_RECEIPT");
                cupd.setReconTime(LocalDateTime.now());
                statementMapper.updateById(cupd);
                // 审计记录：流水对账状态跃迁 → MATCHED
                auditTrailService.recordUpdate("BANK_RECON", st.getId(), st.getBankSerial(), "op_bank_statement",
                        String.valueOf(st.getId()),
                        Collections.singletonMap("reconStatus", st.getReconStatus()),
                        Collections.singletonMap("reconStatus", "MATCHED"),
                        "银企自动勾对：收款流水勾对");
                // 回写收款核销单勾稽字段（银行已到账，出未达调节表）
                RcReceipt ru = new RcReceipt();
                ru.setId(rc.getId());
                ru.setBankSerial(st.getBankSerial());
                ru.setBankMatched(1);
                receiptMapper.updateById(ru);
                // 勾对记录（复用 bank_reconcile）
                BankReconcile cr = new BankReconcile();
                cr.setStatementId(st.getId());
                cr.setSourceDocType("BANK_STATEMENT");
                cr.setSourceDocId(st.getId());
                cr.setPaymentId(null);
                cr.setPaymentNo(rc.getBillNo());
                cr.setSerialNo(st.getBankSerial());
                cr.setAmount(st.getAmount());
                cr.setAccountNo(st.getOppositeAccount());
                cr.setMatchStatus("MATCHED");
                cr.setMatchTime(LocalDateTime.now());
                try {
                    cr.setMatchBy(UserContext.getRequired().getUserId());
                } catch (Exception ignored) {
                    // 无登录上下文（系统自动对账）时不记录匹配人，属预期降级
                }
                reconcileMapper.insert(cr);
                reconFlowSeeds.put(cr.getId(), st.getAmount());
                count++;
                matchedAmount = matchedAmount.add(st.getAmount() == null ? BigDecimal.ZERO : st.getAmount());
                continue;
            }
            List<String> foreign = new ArrayList<>();
            List<ExpBillPayment> cands = matchCandidates(st, consumed, serialOwner, foreign);
            if (cands.size() > 1) {
                ambiguous++;
                unmatchedSerials.add(label(st) + "（同金额候选 " + cands.size() + " 条且流水号不一致，拒绝模糊顶替）");
                continue;
            }
            if (cands.isEmpty()) {
                if (!foreign.isEmpty()) foreignBlocked++;
                unmatchedSerials.add(label(st) + foreignSuffix(foreign));
                continue;
            }
            ExpBillPayment pay = cands.get(0);
            consumed.add(pay.getId());
            // 标记流水已勾对
            OpBankStatement upd = new OpBankStatement();
            upd.setId(st.getId());
            upd.setReconStatus("MATCHED");
            upd.setReconBillId(pay.getBillId());
            // 这一环节指向付款记录所属的费用单据（exp_bill），不在 tr_credit_bill／rc_receipt 两类之内 ⇒
            // 判别列留 NULL（不自创第三种码），读取侧继续按上下文推断，与本列上线前同一规则
            upd.setReconTime(LocalDateTime.now());
            statementMapper.updateById(upd);
            // 审计记录：流水对账状态跃迁 → MATCHED
            auditTrailService.recordUpdate("BANK_RECON", st.getId(), st.getBankSerial(), "op_bank_statement",
                    String.valueOf(st.getId()),
                    Collections.singletonMap("reconStatus", st.getReconStatus()),
                    Collections.singletonMap("reconStatus", "MATCHED"),
                    "银企自动勾对：付款流水勾对");
            // 勾对记录
            BankReconcile r = new BankReconcile();
            r.setStatementId(st.getId());
            r.setSourceDocType("BANK_STATEMENT");
            r.setSourceDocId(st.getId());
            r.setPaymentId(pay.getId());
            r.setPaymentNo(paymentBillNo(pay));
            r.setSerialNo(st.getBankSerial());
            r.setAmount(st.getAmount());
            r.setAccountNo(st.getOppositeAccount());
            r.setMatchStatus("MATCHED");
            r.setMatchTime(LocalDateTime.now());
            try {
                r.setMatchBy(UserContext.getRequired().getUserId());
            } catch (Exception ignored) {
                // 无登录上下文（系统自动对账）时不记录匹配人，属预期降级
            }
            reconcileMapper.insert(r);
            reconFlowSeeds.put(r.getId(), st.getAmount());
            count++;
            matchedAmount = matchedAmount.add(st.getAmount() == null ? BigDecimal.ZERO : st.getAmount());
        }
        // T1：对账勾对完成后发起 TR_BANK_RECON 银行对账审批流（共享会计勾对→财务经理复核未达账）
        // 逐条起流程（bank_reconcile 记录行 : 流程实例 = 1:1），金额取本行流水金额；
        // 批内任一条发起失败一律冒泡（ROOT-2），不得静默降级成无流程孤儿单。
        boolean processStarted = false;
        for (Map.Entry<Long, BigDecimal> fe : reconFlowSeeds.entrySet()) {
            try {
                wfEngineFacade.start("TR_BANK_RECON", fe.getKey(), userId(),
                        "银行对账-自动勾对（本批共" + count + "条）", fe.getValue() == null ? BigDecimal.ZERO : fe.getValue());
                processStarted = true;
            } catch (Exception ex) {
                log.error("银行对账发起对账审批失败: {}", ex.getMessage(), ex);
                throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
            }
        }
        log.info("银企自动勾对完成：勾对 {} 条，未勾对 {} 条（其中同金额多候选拒配 {} 条、同金额付款已归属他行流水号 {} 条）",
                count, unmatchedSerials.size(), ambiguous, foreignBlocked);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", un.size());
        result.put("matched", count);
        result.put("unmatched", unmatchedSerials.size());
        result.put("ambiguous", ambiguous);
        result.put("foreignSerialBlocked", foreignBlocked);
        result.put("unmatchedSerials", unmatchedSerials);
        result.put("matchedAmount", matchedAmount);
        result.put("processStarted", processStarted);
        // 丙：新增提示位（原键一字未动，界面按既有键查询不受影响）
        result.put("selfMatchedReceipts", selfMatchedReceipts);
        result.put("selfMatchWarning", selfMatchedReceipts.isEmpty() ? null
                : "本轮 " + selfMatchedReceipts.size() + " 张收款核销单（"
                        + String.join("、", selfMatchedReceipts)
                        + "）由建单人本人勾对核销，建议由他人复核到账与核销是否一致");
        if (!selfMatchedReceipts.isEmpty()) {
            log.warn("银企收款勾对 D-DEC-420 复核提示：{} 张核销单由本人建单并本人勾对：{}",
                    selfMatchedReceipts.size(), String.join("、", selfMatchedReceipts));
        }
        return result;
    }

    /** 未勾对流水的界面标识：优先银行流水号，缺失时退化为对手名/ID */
    private static String label(OpBankStatement st) {
        if (st.getBankSerial() != null && !st.getBankSerial().isBlank()) {
            return st.getBankSerial();
        }
        return (st.getOppositeName() == null ? "" : st.getOppositeName()) + "#id=" + st.getId();
    }

    /** 结果反馈后缀——同金额的对方单据已被另一条银行流水号占走，出纳当场就能看到"为什么没勾上" */
    private static String foreignSuffix(List<String> foreignSerials) {
        if (foreignSerials == null || foreignSerials.isEmpty()) {
            return "";
        }
        return "（同金额单据归属流水号 " + String.join("/", foreignSerials) + "，拒绝顶替）";
    }

    /** 金额一致容差（分位，与前端提示规则一致；服务端按自身读数复算，不采信前端传来的 diff） */
    private static final BigDecimal AMOUNT_DIFF_TOLERANCE = new BigDecimal("0.01");

    /**
     * 人工勾对（办结）：入参改为「银行流水 id（op_bank_statement.id）＋付款记录 id（exp_bill_payment.id）」
     * 各一条——前端【待勾对】面板必须从这两处真身数据源查询；<b>禁止再传 bank_reconcile 记录行的 id</b>：
     * 三张表各自自增、数字相近即串表，此前把记录行 id 当流水 id / 付款 id 用，一旦提交会把无关流水改写状态并落伪记录。
     * <p>服务端守卫（缺一即拒，与前端校验双层）：
     * <ol>
     *   <li>流水、付款记录必须存在（不按数字 id 猜表）；</li>
     *   <li>重复勾对校验：流水已勾对（MATCHED／RECONED／MANUAL）拒；付款已进过勾对记录（bank_reconcile.payment_id 命中）拒；</li>
     *   <li>金额一致校验：|流水金额 − 付款金额| &gt; {@link #AMOUNT_DIFF_TOLERANCE} 拒；流水金额缺失/非正数拒；</li>
     *   <li>付款状态校验：仅已付款（PAID／SUCCESS）的付款记录可参与勾对。</li>
     * </ol>
     * 写入数据库：按流水对账识别键（recon 键＝statement_id，bank_reconcile 记录行由此定位）有则原位改 MANUAL、无则插入，
     * 不再叠伪记录；同步回写流水 recon_status=MATCHED 与 recon_bill_id／recon_time（与自动勾对同列同一规则），
     * 记录行回填 payment_id／payment_no，并写 审计记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualMatch(List<Long> statementIds, List<Long> paymentIds, BigDecimal diff) {
        if (statementIds == null || statementIds.size() != 1 || paymentIds == null || paymentIds.size() != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "人工勾对一次需且仅需各选一条银行流水与一条付款记录");
        }
        OpBankStatement st = statementMapper.selectById(statementIds.get(0));
        if (st == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "银行流水不存在: " + statementIds.get(0));
        }
        if ("MATCHED".equals(st.getReconStatus()) || "RECONED".equals(st.getReconStatus())
                || "MANUAL".equals(st.getReconStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该流水已勾对，不可重复勾对：" + label(st));
        }
        ExpBillPayment pay = paymentMapper.selectById(paymentIds.get(0));
        if (pay == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款记录不存在: " + paymentIds.get(0));
        }
        if (pay.getPayStatus() == null || !PAID_STATUS.contains(pay.getPayStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已付款（PAID/SUCCESS）的付款记录可参与人工勾对");
        }
        // 重复勾对校验（付款侧）：该付款记录已进过勾对记录即不得再占（与 matchCandidates 的剔除规则同源）
        Long used = reconcileMapper.selectCount(new LambdaQueryWrapper<BankReconcile>()
                .eq(BankReconcile::getPaymentId, pay.getId()));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该付款记录已勾对过，不可重复勾对：payment_id=" + pay.getId());
        }
        // 金额一致校验：服务端读数复算，超差拒绝
        BigDecimal bankAmt = nz(st.getAmount());
        if (bankAmt.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流水金额缺失或非正数，无法勾对：" + label(st));
        }
        BigDecimal payAmt = nz(pay.getAmount());
        if (bankAmt.subtract(payAmt).abs().compareTo(AMOUNT_DIFF_TOLERANCE) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "金额不一致（银行流水 " + bankAmt + " / 付款记录 " + payAmt + "），不满足勾对条件");
        }
        Long operUserId = userId();
        LocalDateTime now = LocalDateTime.now();
        // 回写流水：recon_status=MATCHED（勾对完成规则），指针与自动勾对同列（recon_bill_id=付款所属单据）
        OpBankStatement upd = new OpBankStatement();
        upd.setId(st.getId());
        upd.setReconStatus("MATCHED");
        upd.setReconBillId(pay.getBillId());
        upd.setReconTime(now);
        statementMapper.updateById(upd);
        // 审计记录：流水对账状态跃迁 → MATCHED（人工勾对）
        auditTrailService.recordUpdate("BANK_RECON", st.getId(), st.getBankSerial(), "op_bank_statement",
                String.valueOf(st.getId()),
                Collections.singletonMap("reconStatus", st.getReconStatus()),
                Collections.singletonMap("reconStatus", "MATCHED"),
                "银行对账：人工勾对（付款记录 " + pay.getId() + "）");
        // 记录行按 recon 键（statement_id）定位：已有原位改 MANUAL（回填 payment_id/payment_no），无则插入
        String paymentNo = paymentBillNo(pay);
        BankReconcile row = locateReconRow(st);
        if (row != null) {
            row.setPaymentId(pay.getId());
            row.setPaymentNo(paymentNo);
            row.setAmount(st.getAmount());
            row.setAccountNo(st.getOppositeAccount());
            row.setMatchStatus("MANUAL");
            row.setMatchTime(now);
            row.setMatchBy(operUserId);
            reconcileMapper.updateById(row);
        } else {
            BankReconcile r = new BankReconcile();
            r.setStatementId(st.getId());
            r.setSourceDocType("BANK_STATEMENT");
            r.setSourceDocId(st.getId());
            r.setPaymentId(pay.getId());
            r.setPaymentNo(paymentNo);
            r.setSerialNo(st.getBankSerial());
            r.setAmount(st.getAmount());
            r.setAccountNo(st.getOppositeAccount());
            r.setMatchStatus("MANUAL");
            r.setMatchTime(now);
            r.setMatchBy(operUserId);
            reconcileMapper.insert(r);
        }
        log.info("人工勾对完成：银行流水 {}（id={}）↔ 付款记录 id={}，金额 {}", label(st), st.getId(), pay.getId(), bankAmt);
    }

    /** 按银行流水定位 bank_reconcile 记录行（recon 键：statement_id，取最新一行；无则 null） */
    private BankReconcile locateReconRow(OpBankStatement st) {
        List<BankReconcile> rows = reconcileMapper.selectList(new LambdaQueryWrapper<BankReconcile>()
                .eq(BankReconcile::getStatementId, st.getId())
                .orderByDesc(BankReconcile::getId)
                .last("limit 1"));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * bank_reconcile.payment_no 统一写「付款所属费用单（exp_bill）单号」，自动环节与人工环节共用。
     * <p>此前该列写的是 exp_bill_payment 主键数字（billId 的字符串化），
     * 与收款环节的 rc_receipt.billNo（单号文本）同列异义，出纳按列核对时会把主键当单号。
     * 单据缺失（bill_id 空或 exp_bill 行已不存在）时返回 null ⇒ 列留空，不混入主键数字。
     */
    private String paymentBillNo(ExpBillPayment pay) {
        if (pay == null || pay.getBillId() == null) {
            return null;
        }
        ExpBill bill = expBillMapper.selectById(pay.getBillId());
        return bill == null ? null : bill.getBillNo();
    }

    /**
     * 匹配付款记录：先按银行流水号精确匹配，未命中按 金额+已付款 二次匹配。
     * <p>二次环节返回<b>全部</b>候选而不是 limit 1 —— 同金额不同流水号的多笔候选必须由调用方拒配，
     * 否则两条流水会互相顶掉同一条付款。已被本轮占用/已进过 bank_reconcile 的付款一律剔除。
     * <p><b>流水号唯一性（补齐的半）</b>：付款记录自带 pay_serial_no，且该号是<b>本轮另一条</b>
     * 未勾对流水的银行流水号（{@code serialOwner}）时，这笔付款归属那条流水，当前流水不得按金额抢走；
     * 剔除后无可匹配候选即报"未勾对"，把原因（归属哪条流水号）经 {@code foreign} 带回给界面。
     * <p>已付款规则与 {@link #computeSheet} 的"企业已付银行未付"统一为 {@link #PAID_STATUS}
     * PAID/SUCCESS 两值并存 ——只认 PAID 会把 SUCCESS 的同号付款看成"不存在"，
     * 反而让它躲过唯一性检查、由同金额的另一笔错顶。
     *
     * @param serialOwner 本轮未勾对流水的 银行流水号 → 流水 id（零额外查询，由 {@link #run()} 现算）
     * @param foreign 出参：被归属剔除的付款流水号，用于结果反馈文案
     */
    private List<ExpBillPayment> matchCandidates(OpBankStatement st, Set<Long> consumed,
                                                 Map<String, Long> serialOwner, List<String> foreign) {
        if (st.getBankSerial() != null && !st.getBankSerial().isBlank()) {
            List<ExpBillPayment> bySerial = paymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                    .eq(ExpBillPayment::getPaySerialNo, st.getBankSerial())
                    .in(ExpBillPayment::getPayStatus, PAID_STATUS)
                    .notInSql(ExpBillPayment::getId, ALREADY_RECONCILED)
                    .orderByAsc(ExpBillPayment::getId));
            List<ExpBillPayment> free = new ArrayList<>();
            for (ExpBillPayment p : bySerial) {
                if (!consumed.contains(p.getId())) free.add(p);
            }
            // 流水号精确命中：同流水号即同一笔资金指令，多行按首行（自增序）认，不算模糊
            if (!free.isEmpty()) return free.subList(0, 1);
        }
        if (st.getAmount() == null) return List.of();
        List<ExpBillPayment> byAmt = paymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .in(ExpBillPayment::getPayStatus, PAID_STATUS)
                .eq(ExpBillPayment::getAmount, st.getAmount())
                .notInSql(ExpBillPayment::getId, ALREADY_RECONCILED)
                .orderByAsc(ExpBillPayment::getId));
        List<ExpBillPayment> free = new ArrayList<>();
        for (ExpBillPayment p : byAmt) {
            if (consumed.contains(p.getId())) continue;
            String ps = p.getPaySerialNo();
            if (ps != null && !ps.isBlank()) {
                Long owner = serialOwner.get(ps.trim());
                if (owner != null && !owner.equals(st.getId())) {
                    // 这笔付款的银行流水号属于另一条未勾对流水——同金额不得互顶
                    foreign.add(ps.trim());
                    continue;
                }
            }
            free.add(p);
        }
        return free;
    }

    /** 已进过勾对记录的付款记录（不得被二次勾对重复占用；与 {@link #computeSheet} 的"未勾对付款"同一规则） */
    private static final String ALREADY_RECONCILED =
            "select payment_id from bank_reconcile where payment_id is not null";

    /** 是否银行贷方（收款）流水：取值见 {@link #CREDIT_TRANS_TYPES}（CREDIT/RECEIPT/IN）→ 银行已收 */
    private boolean isCreditStatement(OpBankStatement st) {
        String t = st.getTransType();
        return t != null && CREDIT_TRANS_TYPES.stream().anyMatch(ct -> ct.equalsIgnoreCase(t));
    }

    /**
     * 收款方向匹配：金额相等的未勾对收款核销单中，取往来单位名与流水对方名相等者。
     * <p>返回全部合格候选（含同名多笔），由调用方在"多于一笔"时拒配，避免同金额互相顶掉。
     * <p>唯一性（补齐的半）：核销单上已带<b>别的</b>银行流水号（bank_serial 非空且与本流水不符）
     * 时，它归属那条流水，本流水不得按"金额＋同名"把它顶走；经 {@code foreign} 把归属号带回界面。
     * <p>N+1 批量化 → 先收集所有 customerId，一次批量查询后内存查找
     *
     * @param foreign 出参：被归属剔除的核销单已带流水号，用于结果反馈文案
     */
    private List<RcReceipt> findReceiptMatches(OpBankStatement st, Set<Long> consumed, List<String> foreign) {
        if (st.getAmount() == null) return List.of();
        List<RcReceipt> cands = receiptMapper.selectList(new LambdaQueryWrapper<RcReceipt>()
                .eq(RcReceipt::getBankMatched, 0)
                .eq(RcReceipt::getAmount, st.getAmount()));
        if (cands.isEmpty()) return List.of();

        // N+1 → 批量查询：收集所有非空 customerId，一次 IN 查询，内存匹配
        List<Long> customerIds = cands.stream()
                .map(RcReceipt::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> customerNameMap = customerIds.isEmpty() ? Collections.emptyMap()
                : customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(CrmCustomer::getId, CrmCustomer::getCustomerName));

        List<RcReceipt> hit = new ArrayList<>();
        String mySerial = st.getBankSerial() == null ? null : st.getBankSerial().trim();
        for (RcReceipt rc : cands) {
            if (consumed.contains(rc.getId())) continue;
            String rs = rc.getBankSerial();
            if (rs != null && !rs.isBlank() && !rs.trim().equals(mySerial)) {
                // 该核销单已带另一条银行流水号——同金额同往来也不得互顶
                foreign.add(rs.trim());
                continue;
            }
            String name = customerNameMap.get(rc.getCustomerId());
            if (name != null && st.getOppositeName() != null && name.equals(st.getOppositeName())) {
                hit.add(rc);
            }
        }
        return hit;
    }

    /** 往来单位名称（crm_customer.customer_name） */
    private String customerName(Long customerId) {
        if (customerId == null) return null;
        CrmCustomer c = customerMapper.selectById(customerId);
        return c == null ? null : c.getCustomerName();
    }

    /**
     * 勾对记录列表。match_status 同批并存 MATCHED（自动勾对）、CONFIRMED
     * 审批定稿只写末行那张、CLAIMED（收款认领核销只写认领那张）——按单值 eq 过滤即漏行
     * ⇒ 「已勾对」规则取三者并集（与前端统计/文案/勾选态同一规则，四处不得各自漂移）。
     */
    public PageResult<BankReconcile> page(long pageNum, long pageSize, String matchStatus) {
        LambdaQueryWrapper<BankReconcile> qw = new LambdaQueryWrapper<>();
        if (matchStatus != null && !matchStatus.isBlank()) {
            if ("MATCHED".equalsIgnoreCase(matchStatus.trim())) {
                qw.in(BankReconcile::getMatchStatus, "MATCHED", "CONFIRMED", "CLAIMED");
            } else {
                qw.eq(BankReconcile::getMatchStatus, matchStatus.trim());
            }
        }
        qw.orderByDesc(BankReconcile::getId);
        Page<BankReconcile> p = reconcileMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 月份规则归一。库内 bank_recon_item.month 与调节表算法用 yyyyMM，
     * 界面/查询参数常见 yyyy-MM 或 yyyy/MM —— 不转换则按 '2026-12' 查未达账项恒 0 行。
     */
    static String normalizeMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        String m = month.trim().replace('/', '-');
        if (m.length() >= 7 && m.charAt(4) == '-') {
            m = m.substring(0, 4) + m.substring(5, 7);
        }
        if (m.length() != 6 || !m.chars().allMatch(Character::isDigit)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月份格式不正确（应为 yyyyMM 或 yyyy-MM）: " + month);
        }
        int mm = Integer.parseInt(m.substring(4));
        if (mm < 1 || mm > 12) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月份格式不正确（应为 yyyyMM 或 yyyy-MM）: " + month);
        }
        return m;
    }

    // 未达账项调节表

    /**
     * 未达账项调节表（月度）：银行对账单余额 + 四类未达项 → 调节后余额 vs 企业账面余额。
     * 等式：银行余额 + 企业已收银行未收 − 企业已付银行未付 = 企业账面 + 银行已收企业未收 − 银行已付企业未付
     * 企业已收银行未收(ENT_IN)：当月未与银行流水勾对(bank_matched=0)的收款核销单 rc_receipt。
     */
    public SheetResult sheet(Long bankAccountId, String month) {
        String ym = normalizeMonth(month);
        if (ym == null) {
            ym = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        }
        SheetResult r = computeSheet(bankAccountId, ym);
        // 未达账差异明细写入数据库记录（衔接 TR05 余额调节表）。
        // 改去重 upsert——同键 (账户, 月份, 来源引用) 不重建，既有行 id/create_by/清结标注保留；
        // 重复调用行 id 不再漂移（此前全月 hardDelete+重建：首轮 16~30、次轮 31~45，抹掉人工维护状态）。
        MdBankAccount acct = bankAccountId == null ? null : bankAccountMapper.selectById(bankAccountId);
        saveReconItems(bankAccountId, acct == null ? null : acct.getBankAccount(), ym,
                r.getBankIn(), "BANK_IN", r.getBankOut(), "BANK_OUT",
                r.getEntOut(), "ENT_OUT", r.getEntIn(), "ENT_IN");
        return r;
    }

    /** 纯读计算（不写入数据库）：W2 定稿校验 {@link #reconDiff} 复用，避免审批收尾链路触发明细重建。 */
    private SheetResult computeSheet(Long bankAccountId, String month) {
        SheetResult r = new SheetResult();
        MdBankAccount account = bankAccountId == null ? null : bankAccountMapper.selectById(bankAccountId);
        r.setBankAccountId(bankAccountId);
        r.setBankAccountName(account == null ? null : account.getAccountName());
        r.setMonth(month);

        // 银行对账单余额：该账户当月**最后一条有余额快照的**流水 balance；全都没有则取账户维护余额。
        // 原写法是「只要当月有流水就一律取 stmts.get(0).getBalance()，不再回落」，
        // 而银行流水导入的 balance 是可选字段（可为 NULL）⇒ nz(NULL)=0
        // ⇒ 调节表「银行余额」取 0，实测调节差额 −2,464,354（账户维护余额 2,491,474）。
        // 0 是一个业务上极不可能的余额，会直接掩盖一切差异，故必须回落。
        String start = monthStart(month);
        String endEx = monthStart(nextMonth(month));
        List<OpBankStatement> stmts = statementMapper.selectList(new LambdaQueryWrapper<OpBankStatement>()
                .eq(bankAccountId != null, OpBankStatement::getBankAccountId, bankAccountId)
                .ge(OpBankStatement::getTransDate, start)
                .lt(OpBankStatement::getTransDate, endEx)
                .orderByDesc(OpBankStatement::getTransDate));
        BigDecimal bankBalance = null;
        for (OpBankStatement s : stmts) {
            // 按交易时间倒序遍历，取第一条 balance 非空的快照
            if (s.getBalance() != null) {
                bankBalance = s.getBalance();
                break;
            }
        }
        if (bankBalance == null) {
            // 本期无任何余额快照（无流水，或流水的 balance 全为空）⇒ 用账户维护余额
            bankBalance = account == null ? BigDecimal.ZERO : nz(account.getBalance());
        }
        r.setBankBalance(bankBalance);

        // 银行已收企业未收 / 银行已付企业未付：当月未勾对流水（recon_status != MATCHED）
        List<Item> bankIn = new ArrayList<>();
        List<Item> bankOut = new ArrayList<>();
        for (OpBankStatement s : stmts) {
            // recon_status 同域二源——自动勾对落 MATCHED、共享运营手工对账标记落 RECONED，
            // 只认 MATCHED 会把已对账流水重复计成未达项，调节表金额随之虚增。
            if ("MATCHED".equals(s.getReconStatus()) || "RECONED".equals(s.getReconStatus())) continue;
            Item it = new Item(s.getBankSerial(), s.getOppositeName(), s.getTransDate() == null ? null : s.getTransDate().toLocalDate(),
                    nz(s.getAmount()), s.getSummary(), s.getReconStatus(), "STATEMENT:" + s.getId());
            // 方向分流统一到 {@link #isCreditStatement}，同一实测值集不在两处各写一份
            (isCreditStatement(s) ? bankIn : bankOut).add(it);
        }
        r.setBankIn(bankIn);   // 银行已收企业未收
        r.setBankOut(bankOut); // 银行已付企业未付
        BigDecimal bankInAmt = bankIn.stream().map(Item::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bankOutAmt = bankOut.stream().map(Item::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 企业已付银行未付：PAID 付款记录未勾对（无 bank_reconcile 记录）
        List<ExpBillPayment> unmatched = paymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .in(ExpBillPayment::getPayStatus, PAID_STATUS)
                .notInSql(ExpBillPayment::getId, "select payment_id from bank_reconcile where payment_id is not null"));
        List<Item> entOut = new ArrayList<>();
        for (ExpBillPayment p : unmatched) {
            entOut.add(new Item(p.getPaySerialNo(), p.getPayeeName(), p.getPayTime() == null ? null : p.getPayTime().toLocalDate(),
                    nz(p.getAmount()), "企业已付银行未付", p.getPayStatus(), "PAYMENT:" + p.getId()));
        }
        r.setEntOut(entOut); // 企业已付银行未付
        BigDecimal entOutAmt = entOut.stream().map(Item::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 企业已收银行未收：当月**尚未全额与银行流水勾对**的收款核销单 rc_receipt。
        // 原写法是「bank_matched=0 ⇒ 整张 amount 进未达」，但认领时
        //   claimReceipt 从不回写 rc_receipt ⇒ 已认领勾对的收款单仍被整张列为未达
        //   （实测：146,900 中已认领 65,540，未达项虚增 65,540）。
        // 关键约束：不能只用布尔位判定。本例是「部分勾对」——146,900 只认领了 65,540，
        //   若简单置 bank_matched=1 ⇒ 未认领的 81,360 会被误标为已勾对，反而漏计真实未达。
        //   ⇒ 按「已勾对金额」算未达额：未达额 = amount − bank_matched_amount，
        //     未达额 ≤ 0 的整条不进未达清单。
        List<RcReceipt> unmatchedReceipts = receiptMapper.selectList(new LambdaQueryWrapper<RcReceipt>()
                .ge(RcReceipt::getReceiptDate, LocalDate.parse(start))
                .lt(RcReceipt::getReceiptDate, LocalDate.parse(endEx)));
        // N+1 → 批量查询往来单位名称
        List<Long> receiptCustomerIds = unmatchedReceipts.stream()
                .map(RcReceipt::getCustomerId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> receiptCustomerNameMap = receiptCustomerIds.isEmpty() ? Collections.emptyMap()
                : customerMapper.selectBatchIds(receiptCustomerIds).stream()
                        .collect(Collectors.toMap(CrmCustomer::getId, CrmCustomer::getCustomerName));
        List<Item> entIn = new ArrayList<>();
        for (RcReceipt rc : unmatchedReceipts) {
            BigDecimal receiptAmount = nz(rc.getAmount());
            BigDecimal matchedAmount = nz(rc.getBankMatchedAmount());
            BigDecimal unmatchedAmount = receiptAmount.subtract(matchedAmount);
            if (unmatchedAmount.signum() <= 0) {
                // 已全额勾对 ⇒ 不再是未达项
                continue;
            }
            // 摘要注明已勾对部分，便于对账人员解释（部分勾对场景）
            String memo = matchedAmount.signum() > 0
                    ? "企业已收银行未收（已勾对 " + matchedAmount.toPlainString() + "，未达 " + unmatchedAmount.toPlainString() + "）"
                    : "企业已收银行未收";
            entIn.add(new Item(rc.getBillNo(), receiptCustomerNameMap.get(rc.getCustomerId()), rc.getReceiptDate(),
                    unmatchedAmount, memo, rc.getStatus(), "RECEIPT:" + rc.getId()));
        }
        r.setEntIn(entIn); // 企业已收银行未收
        BigDecimal entInAmt = entIn.stream().map(Item::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 企业账面余额（账户维护余额）
        BigDecimal enterpriseBalance = account == null ? BigDecimal.ZERO : nz(account.getBalance());
        r.setEnterpriseBalance(enterpriseBalance);
        // 调节后余额 = 银行余额 − 企业已付未付 + 企业已收未收 + 银行已付未付 − 银行已收未收
        BigDecimal adjust = bankBalance.subtract(entOutAmt).add(entInAmt).add(bankOutAmt).subtract(bankInAmt);
        r.setAdjustBalance(adjust);
        r.setDiff(adjust.subtract(enterpriseBalance));

        return r;
    }

    /**
     * W2：定位某条对账记录所属账户当月的余额调节差额。
     * 定位不到（无流水／无账户）返回 null＝不拦，宁可不判也不臆造判据。
     */
    public BigDecimal reconDiff(Long reconId) {
        BankReconcile recon = reconcileMapper.selectById(reconId);
        if (recon == null || recon.getStatementId() == null) return null;
        OpBankStatement st = statementMapper.selectById(recon.getStatementId());
        if (st == null || st.getBankAccountId() == null || st.getTransDate() == null) return null;
        String month = st.getTransDate().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return computeSheet(st.getBankAccountId(), month).getDiff();
    }

    /**
     * 未达账差异明细查询：按账户+月份+类型过滤，返回全部未达账项（写入数据库记录，可追溯）。
     */
    public List<BankReconItem> reconItems(Long bankAccountId, String month, String itemType) {
        String ym = normalizeMonth(month);
        return reconItemMapper.selectList(new LambdaQueryWrapper<BankReconItem>()
                .eq(bankAccountId != null, BankReconItem::getAccountId, bankAccountId)
                .eq(ym != null, BankReconItem::getMonth, ym)
                .eq(itemType != null && !itemType.isBlank(), BankReconItem::getItemType, itemType)
                .orderByAsc(BankReconItem::getId));
    }

    /**
     * 当月未达账明细去重刷新（upsert），替代「全月 hardDelete + 重建」。
     * <p>同键＝(accountId, month, sourceRef)，逐键处理：
     * <ul>
     *   <li>同键命中：原位更新业务列，既有行 id / create_by / create_time 一字不动，CLEARED 清结标注不被洗回 OPEN；</li>
     *   <li>新键：插入新行（补 tenantId、status=OPEN）；</li>
     *   <li>本次快照已不含的旧行（来源已勾对/核销/移出当月）：按行 id 精确移除（逻辑删，记录可溯）。</li>
     * </ul>
     * 落码前每次读接口都换一批新 id 并抹掉人工维护状态——本方法保证反复调用同月同账户行为去重、行 id 稳定。
     */
    private void saveReconItems(Long accountId, String accountNo, String month,
                                List<Item> bankIn, String inType,
                                List<Item> bankOut, String outType,
                                List<Item> entOut, String entOutType,
                                List<Item> entIn, String entInType) {
        if (accountId == null) return;
        Long tenantId = TenantContext.getTenantId();
        List<BankReconItem> existing = reconItemMapper.selectList(new LambdaQueryWrapper<BankReconItem>()
                .eq(BankReconItem::getAccountId, accountId)
                .eq(BankReconItem::getMonth, month)
                .orderByAsc(BankReconItem::getId));
        Map<String, BankReconItem> byRef = new LinkedHashMap<>();
        for (BankReconItem old : existing) {
            if (old.getSourceRef() != null) byRef.putIfAbsent(old.getSourceRef(), old);
        }
        List<BankReconItem> items = new ArrayList<>();
        collect(items, accountId, accountNo, month, bankIn, inType);
        collect(items, accountId, accountNo, month, bankOut, outType);
        collect(items, accountId, accountNo, month, entOut, entOutType);
        collect(items, accountId, accountNo, month, entIn, entInType);
        Set<Long> keptIds = new HashSet<>();
        int reused = 0;
        for (BankReconItem e : items) {
            BankReconItem old = e.getSourceRef() == null ? null : byRef.get(e.getSourceRef());
            // 上面的 existing 是进入本方法时的快照。同一账户同一月份被重复触发
            // （连点两次【调节表】，实测 bank_recon_item id=700000051/700000052 相差 1 秒）时，
            // 两次调用都读不到对方刚插的行 ⇒ 同一 source_ref 落两行，勾对面出现两条可勾对的同一付款。
            // 落库前按 (account_id, month, source_ref) 现读一次：命中即复用旧行，不新增（不引新锁、不改表结构）。
            if (old == null && e.getSourceRef() != null) {
                old = reconItemMapper.selectOne(new LambdaQueryWrapper<BankReconItem>()
                        .eq(BankReconItem::getAccountId, accountId)
                        .eq(BankReconItem::getMonth, month)
                        .eq(BankReconItem::getSourceRef, e.getSourceRef())
                        .orderByAsc(BankReconItem::getId)
                        .last("limit 1"));
                if (old != null) {
                    byRef.put(old.getSourceRef(), old);
                }
            }
            if (old != null) {
                // 同键复用：id / create_by / create_time 不动，仅刷新业务列与状态
                old.setAccountNo(accountNo);
                old.setItemType(e.getItemType());
                old.setSerialNo(e.getSerialNo());
                old.setOppositeName(e.getOppositeName());
                old.setBizDate(e.getBizDate());
                old.setAmount(e.getAmount());
                old.setSummary(e.getSummary());
                if (!"CLEARED".equals(old.getStatus())) {
                    old.setStatus("OPEN");
                }
                reconItemMapper.updateById(old);
                keptIds.add(old.getId());
                reused++;
            } else {
                e.setTenantId(tenantId);
                e.setStatus("OPEN");
                reconItemMapper.insert(e);
                keptIds.add(e.getId());
            }
        }
        // 仅移除「本次快照已不含」的旧行（其来源已勾对/核销）；仍在快照中的行一律保号
        int removed = 0;
        for (BankReconItem old : existing) {
            if (!keptIds.contains(old.getId())) {
                reconItemMapper.deleteById(old.getId());
                removed++;
            }
        }
        log.info("未达账差异明细幂等刷新：账户={} 月份={} 条数={}（同键复用 {}、新增 {}、失效移除 {}）",
                accountId, month, items.size(), reused, items.size() - reused, removed);
    }

    /**
     * 对账审批定稿后关闭当批未达账项——bank_recon_item: OPEN → CLEARED（写 update_by/update_time）。
     * 此前对账域的回写全部发生在起流之前，办结后 ④ 格在整条链上没有任何结论落点。
     *
     * @return 本次清结的未达账项条数；定位不到账户/月份时返回 0（不臆造结论）
     */
    @Transactional(rollbackFor = Exception.class)
    public int closeReconItems(Long reconId, Long operator) {
        BankReconcile recon = reconcileMapper.selectById(reconId);
        if (recon == null || recon.getStatementId() == null) return 0;
        OpBankStatement st = statementMapper.selectById(recon.getStatementId());
        if (st == null || st.getBankAccountId() == null || st.getTransDate() == null) return 0;
        String month = st.getTransDate().format(DateTimeFormatter.ofPattern("yyyyMM"));
        BankReconItem u = new BankReconItem();
        u.setStatus("CLEARED");
        if (operator != null) {
            u.setUpdateBy(operator);
        }
        u.setUpdateTime(LocalDateTime.now());
        int n = reconItemMapper.update(u, new LambdaQueryWrapper<BankReconItem>()
                .eq(BankReconItem::getAccountId, st.getBankAccountId())
                .eq(BankReconItem::getMonth, month)
                .eq(BankReconItem::getStatus, "OPEN"));
        log.info("银行对账 {} 定稿：账户 {} 月份 {} 未达账项清结 {} 条", reconId, st.getBankAccountId(), month, n);
        return n;
    }

    private void collect(List<BankReconItem> target, Long accountId, String accountNo, String month,
                         List<Item> src, String itemType) {
        for (Item it : src) {
            BankReconItem e = new BankReconItem();
            e.setAccountId(accountId);
            e.setAccountNo(accountNo);
            e.setMonth(month);
            e.setItemType(itemType);
            e.setSerialNo(it.getSerialNo());
            e.setOppositeName(it.getOppositeName());
            e.setBizDate(it.getTransDate());
            e.setAmount(it.getAmount());
            e.setSummary(it.getSummary());
            e.setSourceRef(it.getSourceRef());
            target.add(e);
        }
    }

    // 银企直连闭环：收款认领核销 + 回单归档

    /** 银行流水分页查询 */
    public PageResult<OpBankStatement> statementPage(long pageNum, long pageSize, String reconStatus) {
        LambdaQueryWrapper<OpBankStatement> qw = new LambdaQueryWrapper<>();
        if (reconStatus != null && !reconStatus.isBlank() && !"ALL".equalsIgnoreCase(reconStatus)) {
            qw.eq(OpBankStatement::getReconStatus, reconStatus);
        }
        qw.orderByAsc(OpBankStatement::getId);
        Page<OpBankStatement> p = statementMapper.selectPage(Page.of(pageNum, pageSize), qw);
        List<OpBankStatement> records = p.getRecords();
        // 勾对状态（recon_status）与认领状态（bank_reconcile 记录 match_status=CLAIMED）分离后，
        // 列表按 CLAIMED 记录派生补充 claimed 标志（实体字段 @TableField(exist=false)，只读不落表）——
        // 前端「勾对状态 / 认领状态」两列各有其源，MATCHED 不再被当成"已认领"。
        if (records != null && !records.isEmpty()) {
            List<Long> stIds = records.stream().map(OpBankStatement::getId).collect(Collectors.toList());
            Set<Long> claimedIds = reconcileMapper.selectList(new LambdaQueryWrapper<BankReconcile>()
                            .in(BankReconcile::getStatementId, stIds)
                            .eq(BankReconcile::getMatchStatus, "CLAIMED"))
                    .stream().map(BankReconcile::getStatementId).collect(Collectors.toSet());
            for (OpBankStatement r2 : records) {
                r2.setClaimed(claimedIds.contains(r2.getId()));
            }
        }
        return PageResult.of(records, p.getTotal(), pageNum, pageSize);
    }

    /**
     * 收款认领核销：银行贷方流水 → 关联应收单并冲减应收余额。
     * <p>强勾稽（内控红线，两条都在服务端成立，界面只是体验层）：
     * <ol>
     *  <li><b>方向校验</b>：仅收款（贷方）方向流水可认领。借方（付款）流水本身是资金流出，
     *      让它冲减应收单余额等于凭空核销一笔应收，必须当场拒绝；</li>
     *  <li><b>余额校验</b>：认领金额（取流水金额的服务端读数，不采信前端回传）不得超过该应收单
     *      <b>当前余额</b>（rc_receivable.remain_amount）——且余额必须是指针行的「当前读」，
     *      否则两张流水并发认领同一张单会双双通过，合计冲减额突破余额。</li>
     * </ol>
     * 该流水只能被认领一次。
     */
    @Transactional(rollbackFor = Exception.class)
    public ClaimResult claimReceipt(Long statementId, Long receivableId) {
        OpBankStatement st = statementMapper.selectById(statementId);
        if (st == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "银行流水不存在: " + statementId);
        }
        // 方向校验（服务端）——非收款（贷方）方向流水一律不得做收款认领核销。
        // 放在取应收单之前、重复认领校验之上：先判这个动作本身能不能做，再谈重复与金额。
        if (!isCreditStatement(st)) {
            log.warn("收款认领方向闸拦截：流水 {} 方向 {} 非收款方向，已拒绝认领",
                    st.getBankSerial(), st.getTransType());
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该流水不是收款方向，不可收款认领核销：" + label(st));
        }
        // 勾对（recon_status=MATCHED）与认领（bank_reconcile 记录 match_status=CLAIMED）是两件事，
        // 两者都意味着该流水已被占用、不能再认领 ⇒ 文案按双事实如实并列，不再单称"已认领"。
        Long claimedRows = reconcileMapper.selectCount(new LambdaQueryWrapper<BankReconcile>()
                .eq(BankReconcile::getStatementId, st.getId())
                .eq(BankReconcile::getMatchStatus, "CLAIMED"));
        if ("MATCHED".equals(st.getReconStatus()) || (claimedRows != null && claimedRows > 0)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "流水 " + st.getBankSerial() + " 已勾对或已认领，不可重复操作");
        }
        // 余额校验读的是「当前读」——事务内 FOR UPDATE 锁住该行后再判金额
        // （与预算占用 BudgetService.occupy 的规则一致：快照读看不到并发已提交的冲减，会超领）。
        Long recId = receivableId == null ? -1L : receivableId;
        RcReceivable rec = receivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .eq(RcReceivable::getId, recId)
                .last("FOR UPDATE")).stream().findFirst().orElse(null);
        if (rec == null) {
            // 必填守卫（批次 2B）：应收单必选——此前 null 直接进 selectById 会抛 MP 的 "id can not be null"（500）
            throw new BizException(ErrorCode.PARAM_INVALID, receivableId == null ? "请选择应收单" : "应收单不存在: " + receivableId);
        }
        BigDecimal amt = nz(st.getAmount());
        // 余额校验的前置：金额缺失（null/0）时「认领多少」无从谈起，放行只会把流水标成已认领而分文未冲减
        if (amt.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该流水金额缺失或非正数，无法确定认领金额：" + label(st));
        }
        BigDecimal balance = nz(rec.getRemainAmount());
        if (balance.compareTo(amt) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "认领金额(" + amt + ")大于应收单余额(" + balance + ")，禁止超额认领");
        }

        // 标记流水已认领并关联应收单
        OpBankStatement upd = new OpBankStatement();
        upd.setId(st.getId());
        upd.setReconStatus("MATCHED");
        upd.setReconBillId(receivableId);
        // 认领环节指向应收单 rc_receivable（同上：不在这两类之内 ⇒ 判别列留 NULL，宁缺不猜）
        upd.setReconTime(LocalDateTime.now());
        statementMapper.updateById(upd);
        // 审计记录：流水对账状态跃迁 → MATCHED（收款认领核销）
        auditTrailService.recordUpdate("BANK_RECON", st.getId(), st.getBankSerial(), "op_bank_statement",
                String.valueOf(st.getId()),
                Collections.singletonMap("reconStatus", st.getReconStatus()),
                Collections.singletonMap("reconStatus", "MATCHED"),
                "收款认领核销（应收单 " + (rec.getBillNo() == null ? rec.getId() : rec.getBillNo()) + "）");

        // 冲减应收单已收/余额
        RcReceivable recUpd = new RcReceivable();
        recUpd.setId(rec.getId());
        recUpd.setReceivedAmount(nz(rec.getReceivedAmount()).add(amt));
        recUpd.setRemainAmount(balance.subtract(amt));
        if (balance.subtract(amt).signum() == 0 && !"DONE".equals(rec.getStatus())) {
            recUpd.setStatus("DONE");
        }
        receivableMapper.updateById(recUpd);

        // 回写收款单的「已勾对金额」。
        // 认领环节挂在应收单（rc_receivable）上，而调节表 ENT_IN 取的是收款单（rc_receipt），
        // 两者靠 rc_receipt.receivable_ids（核销的应收单 id 列表）关联。
        // 此前 claimReceipt 只动 rc_receivable、不动 rc_receipt ⇒ 调节表把整张收款单
        // 含已认领部分 都算作未达，实测虚增 65,540。
        // 按「金额」而非「布尔位」回写：一张收款单可能被多次部分认领
        //   （本次 146,900 只认领 65,540），置布尔位会把未认领部分误标为已勾对。
        markReceiptMatched(rec, amt, st);

        // 勾对记录
        BankReconcile r = new BankReconcile();
        r.setStatementId(st.getId());
        r.setSourceDocType("BANK_STATEMENT");
        r.setSourceDocId(st.getId());
        r.setPaymentId(null);
        r.setPaymentNo(rec.getBillNo());
        r.setSerialNo(st.getBankSerial());
        r.setAmount(amt);
        r.setAccountNo(st.getOppositeAccount());
        r.setMatchStatus("CLAIMED");
        r.setMatchTime(LocalDateTime.now());
        try {
            r.setMatchBy(UserContext.getRequired().getUserId());
        } catch (Exception ignored) {
            // 无登录上下文（系统自动认领）时不记录匹配人，属预期降级
        }
        reconcileMapper.insert(r);

        ClaimResult rs = new ClaimResult();
        rs.setStatementId(st.getId());
        rs.setBankSerial(st.getBankSerial());
        rs.setReceivableId(rec.getId());
        rs.setReceivableNo(rec.getBillNo());
        rs.setReceivableBalance(balance.subtract(amt));
        // 丙：应收单由本人建单、又由本人认领核销 ⇒ 非阻断复核提示（认领照常完成）
        Long claimOperator = userId();
        if (claimOperator != null && rec.getCreateBy() != null && rec.getCreateBy() != 0L
                && claimOperator.equals(rec.getCreateBy())) {
            rs.setWarning("该应收单由本人建单并由本人认领核销，建议由他人复核到账与冲减是否一致");
            log.warn("收款认领核销 D-DEC-420 复核提示：应收单 {} 由本人建单并本人核销", rec.getBillNo());
        }
        return rs;
    }

    /**
     * 认领成功后回写收款单（rc_receipt）的「已勾对金额」。
     *
     * <p><b>为什么必须回写</b>：认领环节挂在应收单（rc_receivable）上，
     * 而调节表「企业已收银行未收」取的是收款单（rc_receipt），
     * 两者靠 {@code rc_receipt.receivable_ids}（该收款单核销的应收单 id 列表）关联。
     * 此前认领只动应收单、不动收款单 ⇒ 已认领的收款单仍被整张列为未达项
     * （实测 146,900 中已认领 65,540 ⇒ 未达项虚增 65,540）。
     *
     * <p><b>为什么按金额而非布尔位</b>：一张收款单可能被多次<b>部分</b>认领
     * （本次 146,900 只认领了 65,540）。若只置 {@code bank_matched=1}，
     * 未认领的 81,360 会被误标为已勾对 ⇒ 由「虚增 65,540」变成「漏计 81,360」，更糟。
     * 故累计 {@code bank_matched_amount}，未达额 = amount − bank_matched_amount。
     *
     * <p><b>找不到对应收款单时不报错</b>：应收单可能由开票自动挂账（source_doc_type=INVOICE_ISSUE），
     * 未经收款单核销 ⇒ 没有可回写的收款单，属正常情形（不阻断认领）。
     */
    private void markReceiptMatched(RcReceivable receivable, BigDecimal amount, OpBankStatement statement) {
        Long receivableId = receivable.getId();
        if (receivableId == null || amount == null || amount.signum() <= 0) {
            return;
        }
        // receivable_ids 形如 "700000176,700000177"（逗号分隔的应收单 id）
        List<RcReceipt> candidates = receiptMapper.selectList(new LambdaQueryWrapper<RcReceipt>()
                .like(RcReceipt::getReceivableIds, String.valueOf(receivableId)));
        for (RcReceipt rc : candidates) {
            if (!receiptCovers(rc, receivableId)) {
                continue; // like 是模糊匹配，必须逐条精确核对 id 边界
            }
            BigDecimal before = nz(rc.getBankMatchedAmount());
            BigDecimal after = before.add(amount);
            RcReceipt upd = new RcReceipt();
            upd.setId(rc.getId());
            upd.setBankMatchedAmount(after);
            // bank_serial 记最近一次勾对流水号（便于溯源；多笔时以最近一次为准）
            upd.setBankSerial(statement.getBankSerial());
            // 派生布尔位：全额勾对才置 1；部分勾对保持 0（语义＝「未勾完」）
            BigDecimal total = nz(rc.getAmount());
            upd.setBankMatched(after.compareTo(total) >= 0 ? 1 : 0);
            receiptMapper.updateById(upd);
            log.info("D-G5-04 收款单已勾对金额回写：receipt={} 单号={} 已勾对 {} / 合计 {}",
                    rc.getId(), rc.getBillNo(), after, total);
        }
    }

    /**
     * 判断收款单是否核销了指定应收单（精确按逗号分隔的 id 列表比对，
     * 避免 like 模糊匹配把 70000017 误判为核销了 700000176）。
     */
    private boolean receiptCovers(RcReceipt receipt, Long receivableId) {
        String ids = receipt.getReceivableIds();
        if (ids == null || ids.isBlank()) {
            return false;
        }
        String target = String.valueOf(receivableId);
        for (String part : ids.split(",")) {
            if (target.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 回单归档：标记流水回单状态已下载/已归档并关联影像文件。
     * 同时在电子会计档案(op_archive)登记一条 BANK_SLIP 档案记录，全文可溯。
     */
    @Transactional(rollbackFor = Exception.class)
    public void archiveSlip(Long statementId, Long fileId) {
        OpBankStatement st = statementMapper.selectById(statementId);
        if (st == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "银行流水不存在: " + statementId);
        }
        // 必填守卫（批次 2B）：归档的语义是"回单影像已入档案"，fileId 留空原先会静默降级成 DOWNLOADED
        // 且跳过 op_archive 登记——用户点了「归档」却什么都没归档。前端已必输，此处默认处理。
        if (fileId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择回单影像文件");
        }
        OpBankStatement upd = new OpBankStatement();
        upd.setId(st.getId());
        upd.setSlipFileId(fileId);
        upd.setSlipStatus("ARCHIVED");
        statementMapper.updateById(upd);
        // 审计记录：回单状态跃迁 → ARCHIVED
        auditTrailService.recordUpdate("BANK_RECON", st.getId(), st.getBankSerial(), "op_bank_statement",
                String.valueOf(st.getId()),
                Collections.singletonMap("slipStatus", st.getSlipStatus()),
                Collections.singletonMap("slipStatus", "ARCHIVED"),
                "银行回单归档");

        // P1 剩余：回单影像并入电子会计档案（op_archive），挂来源链与完整性哈希占位
        // 断点8：通过 ArchiveFacade 登记，不再直接依赖 OpArchiveMapper
        if (fileId != null) {
            ArchiveRegisterDTO dto = new ArchiveRegisterDTO();
            dto.setArchiveNo("BS" + st.getId() + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
            dto.setArchiveType("BANK_SLIP");
            dto.setRefId(st.getId());
            // 立卷入口从流水带公司（档案读面按公司数据域办结），登记写入数据库字段不再空悬
            dto.setCompanyId(st.getCompanyId());
            dto.setBillId(st.getId());
            dto.setRefNo(st.getBankSerial());
            dto.setTitle("银企回单-" + st.getBankSerial());
            dto.setFileId(fileId);
            dto.setFilingUserId(userId());
            dto.setArchiveStatus("ARCHIVED");
            dto.setStatus("FILED");
            dto.setIntegrityStatus("UNKNOWN");
            dto.setRemark("P1 银企直连回单自动归档");
            archiveFacade.registerBankSlipArchive(dto);
        }
    }

    // P1 剩余：余额快照 & 待认领池

    /**
     * 保存账户余额快照（同一账户同一日期去重覆盖，衔接未达账项调节表与 TR05）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveBalance(Long accountId, LocalDate balanceDate, BigDecimal balance, String remark) {
        if (accountId == null || balanceDate == null || balance == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账户/日期/余额必填");
        }
        String accountNo = null;
        MdBankAccount acct = accountId == null ? null : bankAccountMapper.selectById(accountId);
        if (acct != null) {
            accountNo = acct.getBankAccount();
        }
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<BankBalance> qw = new LambdaQueryWrapper<BankBalance>()
                .eq(BankBalance::getAccountId, accountId)
                .eq(BankBalance::getBalanceDate, balanceDate);
        BankBalance exist = bankBalanceMapper.selectOne(qw);
        if (exist != null) {
            exist.setBalance(balance);
            exist.setAccountNo(accountNo);
            exist.setRemark(remark);
            bankBalanceMapper.updateById(exist);
            return exist.getId();
        }
        BankBalance bb = new BankBalance();
        bb.setTenantId(tenantId);
        bb.setAccountId(accountId);
        bb.setAccountNo(accountNo);
        bb.setBalanceDate(balanceDate);
        bb.setBalance(balance);
        bb.setReconStatus("SAVED");
        bb.setRemark(remark);
        bankBalanceMapper.insert(bb);
        return bb.getId();
    }

    /** 余额快照列表（按账户，兼顾最新在前） */
    public List<BankBalance> balanceList(Long accountId) {
        LambdaQueryWrapper<BankBalance> qw = new LambdaQueryWrapper<BankBalance>()
                .eq(accountId != null, BankBalance::getAccountId, accountId)
                .orderByDesc(BankBalance::getBalanceDate);
        return bankBalanceMapper.selectList(qw);
    }

    /**
     * 待认领池：未勾对（recon_status != MATCHED）<b>且为收款（贷方）方向</b>的银行流水，供资金岗人工认领核销应收。
     * <p>池子本身就只放收款方向流水（SQL 侧 {@code trans_type in CREDIT_TRANS_TYPES}），
     * 与 {@link #claimReceipt} 的方向校验同一规则——池子里挑不到借方流水，写侧仍独立再判一次，
     * 任何一侧被改动都不放开非法动作。
     */
    public PageResult<OpBankStatement> pendingClaim(long pageNum, long pageSize) {
        LambdaQueryWrapper<OpBankStatement> qw = new LambdaQueryWrapper<OpBankStatement>()
                .and(w -> w.isNull(OpBankStatement::getReconStatus)
                        .or().in(OpBankStatement::getReconStatus, "UNRECON", "UNMATCHED"))
                .in(OpBankStatement::getTransType, CREDIT_TRANS_TYPES)
                .orderByAsc(OpBankStatement::getId);
        Page<OpBankStatement> p = statementMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 可认领应收单清单（<b>银行域专用</b>，权限随域走）。
     *
     * <p><b>为什么要有这个端点</b>：认领弹层（{@code views/bank/BankSlip.vue}）原先借
     * <b>应收域</b>的通用分页接口 {@code GET /api/tenant/receivable/page} 取清单，
     * 而该端点标 {@code @RequiresPerm("receivable:list")}（应收域）；
     * 认领动作本身 {@code POST /api/tenant/bank/reconcile/claim} 只要求
     * {@code bank:reconcile:list}（银行域）⇒ 后端放行、前端因跨域查询被 403 卡住，
     * <b>纯银企对账岗做不了收款认领</b>（实测潘燕 100012 认领弹层恒空）。
     *
     * <p><b>本端点的规则</b>：只返回「有未收余额、可被流水认领」的应收单，
     * 且按<b>可认领金额降序</b>（让大额单排在前面，便于优先核销）。
     * 方向与金额的最终裁定权仍在服务端 {@code claimReceipt}（余额校验 + 方向校验），
     * 本端点只做「候选清单」，不放开任何非法动作。
     *
     * <p>权限：{@code bank:reconcile:list}（银企对账域）——查询权限随域走，
     * 符合「银行对账岗的应收清单是银行域的视图」。
     */
    public PageResult<RcReceivable> claimableReceivables(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<RcReceivable> qw = new LambdaQueryWrapper<RcReceivable>()
                .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                .ne(RcReceivable::getStatus, "DONE");
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            // 客户名不在本表 ⇒ 先按客户名反查客户 id，再按 id 集合过滤；
            // 不用 like(customerId, kw)（Long 列 like 无意义且会全表扫）。
            List<Long> custIds = customerMapper.selectList(new LambdaQueryWrapper<CrmCustomer>()
                            .like(CrmCustomer::getCustomerName, kw))
                    .stream().map(CrmCustomer::getId).filter(Objects::nonNull).collect(Collectors.toList());
            qw.and(w -> {
                w.like(RcReceivable::getBillNo, kw);
                if (!custIds.isEmpty()) {
                    w.or().in(RcReceivable::getCustomerId, custIds);
                }
            });
        }
        qw.orderByDesc(RcReceivable::getRemainAmount).orderByAsc(RcReceivable::getId);
        Page<RcReceivable> p = receivableMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    @Data
    public static class ClaimResult {
        private Long statementId;
        private String bankSerial;
        private Long receivableId;
        private String receivableNo;
        private BigDecimal receivableBalance;
        /** 丙：同人建单—同人核销的复核提示位（无提示时为 null，不阻断业务） */
        private String warning;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    private String nextMonth(String month) {
        int y = Integer.parseInt(month.substring(0, 4));
        int m = Integer.parseInt(month.substring(4));
        m++;
        if (m > 12) { m = 1; y++; }
        return y + (m < 10 ? "0" : "") + m;
    }

    /** yyyyMM → yyyy-MM-01 */
    private String monthStart(String month) {
        return month.substring(0, 4) + "-" + month.substring(4, 6) + "-01";
    }

    @Data
    public static class Item {
        private String serialNo;
        private String oppositeName;
        private LocalDate transDate;
        private BigDecimal amount;
        private String summary;
        private String status;
        private String sourceRef;

        public Item(String serialNo, String oppositeName, LocalDate transDate, BigDecimal amount,
                    String summary, String status, String sourceRef) {
            this.serialNo = serialNo;
            this.oppositeName = oppositeName;
            this.transDate = transDate;
            this.amount = amount;
            this.summary = summary;
            this.status = status;
            this.sourceRef = sourceRef;
        }
    }

    @Data
    public static class SheetResult {
        private Long bankAccountId;
        private String bankAccountName;
        private String month;
        private BigDecimal bankBalance;
        private BigDecimal enterpriseBalance;
        private BigDecimal adjustBalance;
        private BigDecimal diff;
        private List<Item> bankIn = new ArrayList<>();
        private List<Item> bankOut = new ArrayList<>();
        private List<Item> entOut = new ArrayList<>();
        private List<Item> entIn = new ArrayList<>();
    }
}
