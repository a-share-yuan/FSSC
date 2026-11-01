// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.audit.util.ChecksumUtil;
import com.fssc.budget.mapper.BgOccupyMapper;
import com.fssc.budget.service.BudgetService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.Backoff;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.entity.ExpBillReceipt;
import com.fssc.expense.entity.ExpBillRelation;
import com.fssc.expense.entity.ExpLoanOffset;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.expense.mapper.ExpBillReceiptMapper;
import com.fssc.expense.mapper.ExpBillRelationMapper;
import com.fssc.expense.mapper.ExpLoanOffsetMapper;
import com.fssc.contract.entity.ContPayRecord;
import com.fssc.contract.mapper.ContPayRecordMapper;
import com.fssc.framework.facade.event.BillTerminalEvent;
import com.fssc.framework.log.OperLogWriter;
import com.fssc.framework.notify.OpsAlertService;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.masterdata.entity.MdCip;
import com.fssc.masterdata.mapper.MdCipMapper;
import com.fssc.sharing.service.SharingService;
import com.fssc.sharing.service.VoucherService;
import com.fssc.system.entity.SysOperLog;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.workflow.event.EventOrchestrationBus;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 报销域流程结束收尾（事件编排总线策略实现），按单据类型分流：
 * <ul>
 *  <li>EXP_CLAIM 报销：通过→核销占用+派工；驳回/撤回→释放占用</li>
 *  <li>EXP_LOAN 借款：通过→记借款余额（待还）；驳回/撤回→置状态</li>
 *  <li>EXP_REPAY 还款：通过→核销对应借款单余额；驳回/撤回→置状态</li>
 *  <li>EXP_APPLY 事前申请：通过→APPROVED（供转报销单冲抵）；驳回/撤回→置状态</li>
 *  <li>EXP_PAY 付款单：通过→APPROVED + 自动生成付款记录(WAITING)；驳回/撤回→置状态</li>
 * </ul>
 * <p>
 * 原 @EventListener 已移除，由 {@link EventOrchestrationBus} 统一分发。
 */
@Slf4j
@Component("expenseBizAction")
@RequiredArgsConstructor
public class ExpenseWfEventListener implements WfBizAction {

    private final ExpBillMapper expBillMapper;
    private final ExpBillLineMapper expBillLineMapper;
    private final ExpLoanOffsetMapper expLoanOffsetMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final ExpBillRelationMapper expBillRelationMapper;
    private final ContPayRecordMapper contPayRecordMapper;
    private final BudgetService budgetService;
    private final SharingService sharingService;
    private final VoucherService voucherService;
    private final AuditTrailService auditTrailService;
    private final ApplicationEventPublisher eventPublisher;
    /** 付款审批通过预算执行核销所需（执行流水去重判定） */
    private final BgOccupyMapper bgOccupyMapper;
    /** 付款单挂 CIP（extJson.cipId）时自动生成在建工程付款凭证 */
    private final MdCipMapper mdCipMapper;
    /**
     * 判定「收款方是否申请人本人」时按姓名反查。
     * 直接比 {@code payeeName.equals(applyUserName)} 恒为 false——{@code apply_user_name} 存的是工号，
     * 未指定 pay_target 的报销单会全部被打成供应商款，付错对象。
     */
    private final SysUserMapper sysUserMapper;
    /** 凭证自动生成失败的流程/系统侧记录（sys_oper_log，与 WfMirrorDefectRecorder 同一规则） */
    private final OperLogWriter operLogWriter;
    /** 凭证自动生成失败的运维侧主动告警（企微/邮件，未配置则退化为日志） */
    private final OpsAlertService opsAlertService;
    /**
     * 还款资金流入侧唯一载体 {@code exp_bill_receipt}。
     * <p>{@code rc_receipt} 那条旧环节已撤除：员工还款写进那张表只能出「无客户收款单」的噪声，
     * 且两个环节唯一键分叉（{@code bill_no} vs {@code bill_id}），同一笔到账会落两行。
     * 报表与联查不得把两侧相加。
     */
    private final ExpBillReceiptMapper expBillReceiptMapper;

    @Override
    public Set<String> bizTypes() {
        return Set.of("EXP_CLAIM", "EXP_LOAN", "EXP_REPAY", "EXP_APPLY", "EXP_PAY");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        String bizType = event.getBizType();
        if (!"EXP_CLAIM".equals(bizType) && !"EXP_LOAN".equals(bizType) && !"EXP_REPAY".equals(bizType)
                && !"EXP_APPLY".equals(bizType) && !"EXP_PAY".equals(bizType)) {
            return;
        }
        Long billId = event.getBizId();
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            log.warn("流程结束事件指向不存在的单据: {}", billId);
            return;
        }
        try {
            if (Boolean.TRUE.equals(event.getApproved())) {
                // 金额防篡改：审批通过前校验关键字段校验和。
                // fail-closed：校验不通过一律拦截（置 REJECTED + 记录），绝不进入通过分支。
                // 校验异常若被外层 catch 吞掉，篡改后流程照样走完，防篡改就形同虚设。
                try {
                    verifyChecksum(bill);
                } catch (BizException ce) {
                    log.error("单据 {} 防篡改校验未通过，审批已拦截: {}", billId, ce.getMessage());
                    rejectTamperedBill(bill, ce.getMessage());
                    return;
                }
                handleApproved(bizType, bill);
                // 断点7：审批通过后发布单据最终状态事件，档案域 BillTerminalEventListener 接收
                publishTerminalEventIfTerminal(billId);
            } else {
                // 驳回时记录驳回类型（THROW_BACK 漏单驳回 / GRADE_BY_GRADE 逐级驳回）
                // 撤回判据读事件上的显式 endType（event.withdrawn()），不要去比 comment 文案。
                // 拿给人看的文案当业务开关，文案一改（改个字、将来允许撤回填原因）撤回就被记成驳回：
                // 单据状态、reject_type 落库、记录动作三处一起错，而且不报错。
                handleRejected(bizType, bill, event.withdrawn(), event.getRejectType(), event.getComment());
            }
            // 审计记录：流程结束（审批通过/驳回/撤回）
            boolean approved = Boolean.TRUE.equals(event.getApproved());
            auditTrailService.record(bill.getBillType(), bill.getId(), bill.getBillNo(), "exp_bill",
                    String.valueOf(bill.getId()),
                    approved ? "APPROVE" : (event.withdrawn() ? "WITHDRAW" : "REJECT"),
                    null, null,
                    "流程结束：" + (approved ? "审批通过" : (event.withdrawn() ? "申请人撤回" : "审批驳回"))
                            + (event.getRejectType() == null ? ""
                            : ("【驳回类型=" + ("THROW_BACK".equals(event.getRejectType()) ? "漏单驳回" : "逐级驳回") + "】"))
                            + (event.getComment() == null ? "" : "（" + event.getComment() + "）"));
            log.info("单据 {} 流程收尾: bizType={}, approved={}", billId, bizType, event.getApproved());
        } catch (Exception ex) {
            // 事件收尾失败要记录（后续可对账重放），不让异常中断
            log.error("单据 {} 流程收尾失败: {}", billId, ex.getMessage(), ex);
        }
    }

    /**
     * 防篡改 fail-closed：校验和不通过时把单据置为 REJECTED 并记录，阻断"篡改后照常通过"。
     * 不抛异常（避免被外层"收尾异常不中断"分支吞掉），但状态与审计可见。
     */
    private void rejectTamperedBill(ExpBill bill, String reason) {
        ExpBill u = new ExpBill();
        u.setId(bill.getId());
        u.setStatus("REJECTED");
        u.setAuditRemark("防篡改拦截：" + reason);
        expBillMapper.updateById(u);
        // ⑨：防篡改拦截同样是死亡最终状态，作业链一并办结
        sharingService.closeOnBillTerminal(bill.getId(), "防篡改拦截，流程已结束");
        auditTrailService.record(bill.getBillType(), bill.getId(), bill.getBillNo(), "exp_bill",
                String.valueOf(bill.getId()), "TAMPER_REJECT", null, null, "防篡改拦截：" + reason);
    }

    /** 校验关键字段校验和（提交时生成，审批通过时校验，防止金额/收款被篡改） */
    private void verifyChecksum(ExpBill bill) {
        try {
            String stored = bill.getAmountChecksum();
            if (stored == null || stored.isBlank()) {
                auditTrailService.recordChecksumFailure(bill.getBillType(), bill.getId(), bill.getBillNo(),
                        "审批通过时校验和缺失，疑似绕过提交流程");
                throw new BizException(ErrorCode.PARAM_INVALID, "单据校验和缺失，疑似绕过提交流程，审批已拦截");
            }
            String calc = ChecksumUtil.sha256(
                    bill.getTotalAmount(), bill.getPayeeName(), bill.getBillNo(), bill.getTitle());
            if (!ChecksumUtil.safeEquals(stored, calc)) {
                auditTrailService.recordChecksumFailure(bill.getBillType(), bill.getId(), bill.getBillNo(),
                        "审批通过时校验和不一致：金额/收款人等关键字段疑似被篡改");
                throw new BizException(ErrorCode.PARAM_INVALID, "单据关键字段已被篡改，审批已拦截");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // fail-closed：校验和计算异常同样拦截审批，避免“异常即放行”绕过防篡改
            auditTrailService.recordChecksumFailure(bill.getBillType(), bill.getId(), bill.getBillNo(),
                    "审批通过时校验和计算异常，疑似数据异常或环境问题");
            log.error("校验和校验异常 billId={}", bill.getId(), e);
            throw new BizException(ErrorCode.PARAM_INVALID, "单据校验和校验异常，审批已拦截");
        }
    }

    /** 审批通过分支 */
    private void handleApproved(String bizType, ExpBill bill) {
        switch (bizType) {
            case "EXP_LOAN" -> {
                // 借款通过：loan_balance = 借款金额（待还）+ 共享作业链（合规审核→资金支付→账务处理）。
                // 借款审批通过即自动制单（借 1221 其他应收款 / 贷 1002 银行存款，模板 TPL_LOAN）。
                // 时序铁律：generateByTemplate 是 REQUIRES_NEW 且内部回写 exp_bill.voucher_no，
                // 必须排在首次 UPDATE exp_bill 之前。反过来的话内层回写会被外层行锁阻塞，
                // Lock wait timeout 整体回滚，表现为「生成凭证节点通过后 op_voucher 无记录」。
                Long loanVoucherId = null;
                try {
                    loanVoucherId = voucherService.existingVoucherIdOf("EXP_LOAN", bill.getId());
                    if (loanVoucherId == null) {
                        loanVoucherId = voucherService.generateByTemplate(bill.getId(), "EXP_LOAN");
                    }
                    log.info("借款单 {} 审批通过自动生成借款凭证: {}", bill.getBillNo(), loanVoucherId);
                } catch (Exception vex) {
                    // 失败已由 handleVoucherGenFailure 统一记录（置失败态+原因），此处不再上抛以免打断审批收尾
                    handleVoucherGenFailure(bill, "EXP_LOAN", vex);
                }
                ExpBill upd = new ExpBill();
                upd.setId(bill.getId());
                upd.setStatus("APPROVED");
                upd.setLoanBalance(nvl(bill.getTotalAmount()));
                // 借款放款上限回写 payable_amount（＝借款本金）。
                // 本分支不能只写 loan_balance：PayBatchService 的防超付校验跳过判据是
                // payableAmount 为空而非等于 0，payable_amount 留在 0 就会变成
                // 「已付 0 ＋ 拟付 4000 ＞ 应付 0.00」，放款永久拦死；
                // 而借款凭证已经过账（贷 1002 银行存款），账上记了支出、实际零支出。
                // 两个字段分工：payable_amount 承载「放款上限」（资金校验语义），在贷余额仍由 loan_balance 承载。
                // 状态机不扩——放款付讫不得把借款单迁成 PAID（那等于借款已结清、会从在贷池消失），
                // 由 PayBatchService#writeBackPayment 对 EXP_LOAN 豁免 PAID 迁移来保证。
                upd.setPayableAmount(nvl(bill.getTotalAmount()));
                upd.setExpectRepayDate(bill.getExpectRepayDate() == null
                        ? LocalDate.now().plusMonths(3) : bill.getExpectRepayDate());
                expBillMapper.updateById(upd);
                // 借款放款链路：审批通过除记账凭证外，还要落一条 WAITING「放款」付款记录，
                // 出纳才有条目可组批付出（真实放款通道＝TR_PAY 资金付款批次）。
                createLoanDisbursementPayment(bill);
                sharingService.createChain(bill, "EXP_LOAN");
            }
            case "EXP_REPAY" -> {
                // 还款通过：核销借款人借款余额（减记），落冲抵流水 + 共享作业链（收款核对→冲销账务归档）。
                // 还款审批通过即自动制单（借 1002 银行存款 / 贷 1221 其他应收款，模板 TPL_REPAY）。
                // 同 EXP_LOAN：先凭证（REQUIRES_NEW 回写 voucher_no）再外层 UPDATE，防行锁死锁；判重防重放出双凭证。
                Long repayVoucherId = null;
                try {
                    repayVoucherId = voucherService.existingVoucherIdOf("EXP_REPAY", bill.getId());
                    if (repayVoucherId == null) {
                        repayVoucherId = voucherService.generateByTemplate(bill.getId(), "EXP_REPAY");
                    }
                    log.info("还款单 {} 审批通过自动生成还款凭证: {}", bill.getBillNo(), repayVoucherId);
                } catch (Exception vex) {
                    // 同上：借款退款凭证生成失败经 handleVoucherGenFailure 记录，不重复抛出
                    handleVoucherGenFailure(bill, "EXP_REPAY", vex);
                }
                ExpBill upd = new ExpBill();
                upd.setId(bill.getId());
                upd.setStatus("APPROVED");
                expBillMapper.updateById(upd);
                repayLoan(bill);
                // 还款资金事实唯一载体＝exp_bill_receipt；rc_receipt 那条旧环节已撤除
                registerRepayFundReceipt(bill);
                sharingService.createChain(bill, "EXP_REPAY");
                // 还款链的「收款核对→冲销账务归档」在上方 repayLoan（余额核销+结清判定）
                // 与 registerRepayFundReceipt（收款登记）里已全部自动办结，凭证也已自动生成。
                // 这两节再留池任务，只会成为最终状态单上的僵尸待认领。
                // 创建即办结：留 FINISHED 痕迹证明链走过，池里不再出现无意义工单。
                sharingService.closeOnBillTerminal(bill.getId(), "还款已自动核销入账");
            }
            case "EXP_APPLY" -> {
                // 事前申请通过：状态 APPROVED（事后报销可转单冲抵申请金额）。
                // 按业务规则：事前申请不进共享中心（共享仅在报销时关联申请做校验）
                updateStatus(bill.getId(), "APPROVED");
            }
            case "EXP_PAY" -> {
                // 付款单通过：状态 APPROVED + 待付款记录 + 共享作业链（三单匹配→付款→记账）
                // 回写 payable_amount：核定付款优先，否则按总额；付款批次防超付以该规则预检
                // 对公结算(SETTLE)冲销预付：应付 = 核定/总额 − 冲销预付合计，
                // 否则冲销形同虚设会按全额重复付款（预付已付 + 结算又全额付）。同时回写被冲销预付单
                // offset_amount，防止预付被再次冲销。
                BigDecimal prepayOffset = sumPrepayOffsets(bill);
                BigDecimal baseAmt = (bill.getApprovedPayAmount() != null && bill.getApprovedPayAmount().signum() > 0)
                        ? bill.getApprovedPayAmount() : bill.getTotalAmount();
                BigDecimal payable = (baseAmt == null ? BigDecimal.ZERO : baseAmt).subtract(prepayOffset);
                if (payable.signum() < 0) payable = BigDecimal.ZERO;
                // 生成凭证必须排在首次 UPDATE exp_bill 之前：
                // generatePtpPayment 是 REQUIRES_NEW 独立事务，事务内要 UPDATE exp_bill 回写 voucher_no；
                // 外层事务（Flowable complete 事务）若已更新过 exp_bill（status/payable），
                // 内层回写 voucher_no 会被外层行锁阻塞 50s，Lock wait timeout 整体回滚——
                // 表现为「生成凭证节点通过后 op_voucher 无记录、单据 voucher_no 为空」。
                // 与 EXP_CLAIM 同一时序：先在内存置状态/应付并生成凭证，再统一写入数据库。
                bill.setStatus("APPROVED");
                bill.setPayableAmount(payable);
                // 对公付款单自动生成对公付款凭证（借 应付账款/贷 银行存款），POSTED 后入总账。
                // 结算单（PAY/SETTLE）此处出的是「借 6602 费用／贷 2202 应付账款」计提应付凭证，
                // 付款环节由出纳批次凭证出。
                // 失败记录的 sourceType 必须与实际会落的来源一致，否则「记账失败补生成」视图
                // 按唯一键回查永远显示未补生成（VoucherGenFailureService#loadVouchers）。
                try {
                    Long vid = voucherService.generatePtpPayment(bill);
                    log.info("对公付款单 {} 审批通过自动生成付款凭证: {}", bill.getBillNo(), vid);
                } catch (Exception ex) {
                    // 同上：付款/结算凭证生成失败经 handleVoucherGenFailure 记录，不重复抛出
                    boolean settle = "SETTLE".equalsIgnoreCase(bill.getBillSubType());
                    handleVoucherGenFailure(bill,
                            settle ? VoucherService.SOURCE_SETTLE : "EXPENSE_PAY", ex);
                }
                ExpBill payUpd = new ExpBill();
                payUpd.setId(bill.getId());
                payUpd.setStatus("APPROVED");
                payUpd.setPayableAmount(payable);
                expBillMapper.updateById(payUpd);
                // 付款单挂 CIP（extJson.cipId，与 extJson.poId 并列的可选挂载）时，
                // 审批通过自动生成「借 在建工程(1604) / 贷 银行存款(1002)」凭证并回写 md_cip.paid_amount。
                // 去重：source_type=CIP_PAY + source_doc_id=billId；失败仅记日志不阻断审批收尾。
                generateCipPayVoucherIfLinked(bill, payable);
                // 回写被冲销预付单的已冲销金额（核销）
                writeBackPrepayOffsets(bill);
                // 用回写后的最新单据生成付款记录，保证付款记录金额与防超付规则一致
                ExpBill freshPay = expBillMapper.selectById(bill.getId());
                createPaymentRecord(freshPay != null ? freshPay : bill);
                sharingService.createChain(bill, "EXP_PAY");
                // 审批通过此处不把占用转已用：付款单这时钱还没走（exp_bill_payment 只是 WAITING，
                // 批次仍在出纳手上），此刻计消耗就是「款未付即计消耗」。
                // 转已用下移到 PayBatchService.execute 打款成功后按单确认——占用仍在（提交时已 occupy），
                // 额度不会失控；反审批回冲由 ExpenseBillService 的 hasWriteOff 双态规则
                // 自动选 release（未付）/ reverseWriteOff（已付）。
            }
            default -> {
                // EXP_CLAIM：自动生成凭证（可预览修改）→ 核销占用 + 共享作业链（票据审核→付款审核→记账归档）。
                // generate 要求 status ∈ {APPROVED, PAID, CLOSED}，而库里此时还是 APPROVING（末尾才置 APPROVED），
                // 不先改内存状态，generate 必然校验失败并被 catch 吞掉，凭证就不生成了。
                // 这里只改内存里的状态；真正落库的 APPROVED 仍由下方 claimUpd.updateById 统一完成。
                bill.setStatus("APPROVED");
                try {
                    Long vid = voucherService.generate(bill);
                    log.info("报销单 {} 审批通过自动生成凭证: {}", bill.getBillNo(), vid);
                } catch (Exception ex) {
                    // EXP_CLAIM 是六段链路里最容易断在 VOUCHER 的一环，
                    // 失败必须留下单据/流程侧可见痕迹与可重试入口，不能只留一行 log。
                    handleVoucherGenFailure(bill, "EXPENSE", ex);
                }
                // 审批通过不再把预算占用直接转已用（原规则＝款未付票未得即计消耗）。
                // 占用在提交时已按行 occupy（ExpenseBillService），审批通过只让它继续"挂着"，
                // 转已用的动作下移到 PayBatchService.execute 打款成功（settleOccupyOnPaid）。
                // 例外：本单没有任何现金环节（全额由借款冲抵平账 / 报销<借款只产生退款记录）时，
                // 钱已经在借款那一环节出去，此刻就地确认消耗，否则占用会永久悬空（见下方 payable 判定）。
                // 分多次报销（需求 4.2.2 页面规则 7/8）：按关联金额核销事前申请占用，
                // 累计已报销达到申请金额 → 申请单 COMPLETED（报销完毕）
                writeOffApplyOccupy(bill);
                // 报销 vs 借款 3 种差额（平账/补付/退款）：核销关联借款单余额，
                // 差额>0 自动补付付款单；差额<0 自动创建退款；差额=0 平账不付款。
                // 返回值 = 本报销单应付现金（报销核定额 − 借款冲抵），供报销→付款转换链使用
                BigDecimal payable = handleLoanClaimDiff(bill);
                // 共享作业链先行：智能审单可能 AUTO_REJECT（置 REJECTED 且不入池），
                // 审单通过后才生成付款记录，避免被驳回的单据残留孤儿付款
                sharingService.createChain(bill, "EXP_CLAIM");
                ExpBill fresh = expBillMapper.selectById(bill.getId());
                if (fresh != null && ("REJECTED".equals(fresh.getStatus()) || "WITHDRAWN".equals(fresh.getStatus()))) {
                    log.warn("报销单 {} 被智能审单驳回/撤回（status={}），跳过付款记录生成",
                            bill.getBillNo(), fresh.getStatus());
                } else {
                    if (payable.signum() > 0) {
                        // 报销→付款转换链：审批通过即核定应付并生成待付款记录（WAITING），
                        // 出纳在付款工作台组批执行；全额付讫后由 PayBatchService 回写 APPROVED→PAID
                        createClaimPayment(bill, payable);
                } else {
                    // 无现金环节分支：本单不会产生待付记录 ⇒ 此刻确认预算消耗
                    // 但<b>不能就在此刻执行</b>，见 settleBudgetOnNoCashLeg 的方法注释：
                    // 必须等外层审批事务提交后（行锁已释放）再跑，否则内层 REQUIRES_NEW 自锁 50s 超时。
                    scheduleSettleBudgetOnNoCashLeg(bill);
                    // 无现金环节＝钱不经付款批次（全额借款冲抵/退款了结），单据业务到此结束，
                    // 上面刚建的作业链（票据审核/付款审核/记账归档）不会再有人办——立即办结，
                    // 否则就会留下「已核销平账却仍挂审核待认领」的僵尸工单。
                    sharingService.closeOnBillTerminal(bill.getId(), "报销无实际付款（全额冲抵/退款），业务已了结");
                }
                    ExpBill claimUpd = new ExpBill();
                    claimUpd.setId(bill.getId());
                    claimUpd.setStatus("APPROVED");
                    if (payable.signum() > 0) {
                        // 应付现金写回：付款批次防超付预检 + 全额付讫自动迁移 PAID 都依赖该字段
                        claimUpd.setPayableAmount(payable);
                    }
                    expBillMapper.updateById(claimUpd);
                }
            }
        }
        // approve_time 在分支收尾统一回填一次：五个 case 各自只写 status，不在这里补就永远是 NULL。
        // 以库内最新状态为准——EXP_CLAIM 可能被智能审单驳回/撤回，不能盲写。
        markApproveTime(bill.getId());
        // 同意环节（61「付款后金额确认列仍 NULL」）：与 approve_time 同一个收尾点，
        // 把"审批人直接同意、没做核减"这一路的核定报销额落进库里。见方法注释的判据与排除项。
        confirmApprovedAmountOnAgree(bizType, bill.getId());
    }

    /** 仅当单据确为 APPROVED 且该列仍为空时写入审批通过时刻（重放事件不改写首时刻） */
    private void markApproveTime(Long billId) {
        ExpBill cur = expBillMapper.selectById(billId);
        if (cur == null || cur.getApproveTime() != null || !"APPROVED".equals(cur.getStatus())) {
            return;
        }
        ExpBill upd = new ExpBill();
        upd.setId(billId);
        upd.setApproveTime(LocalDateTime.now());
        expBillMapper.updateById(upd);
    }

    /**
     * 同意环节：审批通过而<b>无人做过核减</b>时，把核定报销金额按申请金额落列。
     *
     * <p><b>为什么要在这里补</b>：{@code exp_bill.approved_amount} 全仓只有两个写点——人工核减口
     * {@code ExpenseBillService#approveDeduct} 与 {@code writeOffApplyOccupy}
     * （后者写的是申请单累计，语义不同，见下方排除项）。审批人在审批台点「同意」而不改金额时，
     * 前者根本不触发，付款之后「金额确认列」就是一片空白，核定金额、申请金额、实付金额三者无法对账。
     *
     * <p><b>判据＝未核减时核定金额＝申请金额</b>，不是"造一个数"：系统本来就是这么算的——
     * 下游取报销核定额的三处（{@code :797}/{@code :1075}/{@code :1140}）与
     * {@code ExpenseDetailService:247/:412} 写的都是
     * {@code approved_amount != null ? approved_amount : total_amount}，即"列空＝按申请金额走"。
     * 补上 {@code total_amount} 后这些取值<b>逐字不变</b>，只是库里从此看得见、可追责。
     * 有核减值绝不覆盖——只补 NULL，金额只认办理时点的真值，不伪造。
     *
     * <p><b>刻意排除事前申请（EXP_APPLY / billType=APPLY）</b>：同一列在申请单上是
     * <b>累计已报销额</b>而非核定额——{@code writeOffApplyOccupy :1042-1048} 以
     * {@code approved_amount += 本次关联额} 累加，并据此把满额者判 {@code COMPLETED}。
     * 若给申请单也填 {@code total_amount}，它一通过就「报销完毕」，分次报销的累计判据被整体顶掉。
     * 这一列在两型单据上的双语义本身就是隐患，扩大适用范围前要先拆掉这层歧义。
     *
     * <p><b>复核人（{@code audit_user_id}）这里不写</b>：{@code ExpBill} 实体没有映射这一列，
     * 全仓也没有写点；而且这里拿不到「当次办理人」——{@code WfProcessEndedEvent} 没有操作人字段，
     * 从 {@code wf_task_history} 反查又会踩「末节 APPROVE 行晚于事件落库」的时序坑，取到的只是上一节的人。
     *
     * <p>去重：只在该列为空时写；重读库内最新行，避免覆盖并发核减；异常只降级不影响审批收尾主链路。
     */
    private void confirmApprovedAmountOnAgree(String bizType, Long billId) {
        try {
            if ("EXP_APPLY".equals(bizType)) {
                return;
            }
            ExpBill cur = expBillMapper.selectById(billId);
            if (cur == null || !"APPROVED".equals(cur.getStatus()) || "APPLY".equals(cur.getBillType())) {
                return;
            }
            if (cur.getApprovedAmount() != null || cur.getTotalAmount() == null) {
                return;
            }
            ExpBill upd = new ExpBill();
            upd.setId(billId);
            upd.setApprovedAmount(cur.getTotalAmount());
            expBillMapper.updateById(upd);
            log.info("单据 {} 审批通过未核减，核定报销金额按申请金额 {} 落列",
                    cur.getBillNo(), cur.getTotalAmount());
        } catch (Exception ex) {
            // 附加列补写：失败只影响这一列的可见性，绝不把审批收尾（状态/付款/凭证）带崩
            log.warn("单据 {} 核定金额同意腿补写失败: {}", billId, ex.getMessage());
        }
    }

    // 凭证自动生成失败必须留下可查的痕迹，不能静默吞掉

    /** 错误码：单据自动生成凭证失败（可补生成）；供「系统日志」页筛选与巡检脚本按码统计 */
    public static final String CODE_VOUCHER_GEN_FAILED = "EXP-VOUCHER-001";
    /** 补生成入口提示（与 SharingController#generate 同源，去重：已生成不会重复） */
    private static final String VOUCHER_RETRY_ENTRY =
            "共享中心→记账凭证→生成凭证（POST /api/tenant/sharing/voucher/generate/{billId}）";
    /** sys_oper_log.error_msg 列宽 varchar(2000)，留出余量（与 WfMirrorDefectRecorder 同一规则） */
    /** exp_bill.audit_remark 列宽 varchar(1000)（驳回意见回写用，超长截断） */
    private static final int AUDIT_REMARK_MAX = 1000;
    private static final int OPER_LOG_MSG_MAX = 1900;

    /**
     * 凭证自动生成失败的统一记录——<b>不再是一行 log 就消失</b>。
     *
     * <p><b>为什么不按方案原文"冒泡"</b>：本方法由 {@code EventOrchestrationBus} 在 Flowable
     * 引擎线程上异步分发，且总线对每个 Action 自带 try/catch（单个失败只记日志、不中断其它动作）。
     * 也就是说：冒泡既不会回滚审批（分发侧本就没有调用方事务可供回滚），又会在 {@code handleApproved}
     * 中途抛出——把它之后的预算核销、付款记录、共享作业链等全部收尾动作一并跳过，<b>让链条断得更多</b>。
     * 故此处按"失败必须留下可见、可查、可重试的痕迹"落地，而不是抛异常。
     *
     * <p>三条记录（同一事实的三个视角，缺一不可）：
     * <ol>
     *   <li><b>单据侧</b>：{@code exp_bill.ext_json.voucherPending} 标记（错误码/来源/原因/时间），
     *       单据详情与全链路联查返回的 bill 行直接带出；并写一条
     *       {@code audit_trail}(action=VOUCHER_GEN_FAILED) 供按单号查审计轨迹；</li>
     *   <li><b>流程/系统侧</b>：{@code sys_oper_log}（status=1 失败 + 固定错误码），
     *       在「系统日志」页可查、可被巡检脚本按码统计（与 {@code WfMirrorDefectRecorder} 同一规则）；</li>
     *   <li><b>运维侧</b>：{@link OpsAlertService} 主动告警（企微/邮件，未配置则退化为 ERROR 日志）。</li>
     * </ol>
     *
     * <p>本方法自身<b>绝不抛异常</b>：任一条记录失败只降级为日志，收尾链必须继续走完。
     */
    private void handleVoucherGenFailure(ExpBill bill, String sourceType, Exception ex) {
        String reason = voucherFailReason(bill, ex);
        log.error("[{}] 单据 {} 自动生成凭证失败（sourceType={}，可补生成）: {}",
                CODE_VOUCHER_GEN_FAILED, bill.getBillNo(), sourceType, reason, ex);

        markVoucherPending(bill, sourceType, reason);
        recordVoucherFailAudit(bill, sourceType, reason);
        writeVoucherFailOperLog(bill, sourceType, reason);
        alertVoucherFail(bill, sourceType, reason);
    }

    /**
     * 失败原因归一。<b>「会计期间已结账」单独成一类</b>——它是可操作的规则（反结账或改落期），
     * 不能把 {@code 参数校验失败：会计期间 2026-09 已结账，禁止操作} 原样抛给用户自己猜。
     */
    private String voucherFailReason(ExpBill bill, Exception ex) {
        String msg = ex == null ? null : ex.getMessage();
        if (msg != null && msg.contains("已结账")) {
            return "会计期间 " + periodLabel(bill) + " 已结账，凭证未生成。处理方式："
                    + "①「主数据→期间管理」对该期间反结账（恢复开账）后，再经补生成入口重制；"
                    + "②或按公司当期开账期间重新落期（VoucherService 按期次前滚的规则）后重制。"
                    + "两种处置均不影响单据已完成的审批结论，仅为补记总账。";
        }
        if (msg == null || msg.isBlank()) {
            return ex == null ? "未知原因" : ex.getClass().getSimpleName();
        }
        return msg;
    }

    private String periodLabel(ExpBill bill) {
        if (bill.getPeriodYear() == null || bill.getPeriodMonth() == null) {
            return "（单据未指定期间）";
        }
        return bill.getPeriodYear() + "-" + String.format("%02d", bill.getPeriodMonth());
    }

    /**
     * 单据侧标记：{@code ext_json.voucherPending}。<b>保留既有键</b>（prepayOffsets/cipId/poId…）只增不改。
     *
     * <p>标记不主动清除：判定规则＝{@code voucher_no 为空 且 该标记存在}——补生成成功后
     * {@code voucher_no} 有值，标记即自然失效（无需回写，避免多写点同步）。
     */
    private void markVoucherPending(ExpBill bill, String sourceType, String reason) {
        try {
            String raw = bill.getExtJson();
            ObjectNode root;
            if (raw == null || raw.isBlank()) {
                root = OM.createObjectNode();
            } else {
                JsonNode parsed = OM.readTree(raw);
                root = (parsed != null && parsed.isObject()) ? (ObjectNode) parsed : OM.createObjectNode();
            }
            ObjectNode pending = OM.createObjectNode();
            pending.put("code", CODE_VOUCHER_GEN_FAILED);
            pending.put("sourceType", sourceType);
            pending.put("reason", reason);
            pending.put("time", LocalDateTime.now().toString());
            root.set("voucherPending", pending);
            ExpBill upd = new ExpBill();
            upd.setId(bill.getId());
            upd.setExtJson(OM.writeValueAsString(root));
            expBillMapper.updateById(upd);
        } catch (Exception markEx) {
            // 标记写失败不得反过来阻断收尾链（审计/系统日志/告警三处记录此时已完成）
            log.warn("单据 {} 写 ext_json.voucherPending 标记失败: {}", bill.getBillNo(), markEx.getMessage());
        }
    }

    /** 单据侧可查：审计轨迹（按单号 bizNo 可检索，remark 自带错误码与补生成入口） */
    private void recordVoucherFailAudit(ExpBill bill, String sourceType, String reason) {
        try {
            auditTrailService.record(bill.getBillType(), bill.getId(), bill.getBillNo(), "exp_bill",
                    String.valueOf(bill.getId()), "VOUCHER_GEN_FAILED", null, null,
                    "[" + CODE_VOUCHER_GEN_FAILED + "] 自动生成凭证失败 sourceType=" + sourceType
                            + "；" + reason + "；补生成入口：" + VOUCHER_RETRY_ENTRY);
        } catch (Exception auditEx) {
            log.warn("单据 {} 凭证失败审计留痕写入异常: {}", bill.getBillNo(), auditEx.getMessage());
        }
    }

    /** 流程/系统侧可查：{@code sys_oper_log}（走 OperLogWriter，异步且自带 try/catch，不阻断主链路） */
    private void writeVoucherFailOperLog(ExpBill bill, String sourceType, String reason) {
        Long tenantId = (TenantContext.hasContext() && !TenantContext.isIgnoreTenant())
                ? TenantContext.getTenantId() : null;
        if (tenantId == null) {
            // sys_oper_log.tenant_id NOT NULL：总线以 runIgnoreTenant 兜底时无法写入数据库，只留文件日志
            log.warn("[{}] 无租户上下文，凭证失败未落 sys_oper_log，单据 {}（billId={}）",
                    CODE_VOUCHER_GEN_FAILED, bill.getBillNo(), bill.getId());
            return;
        }
        try {
            String msg = "[" + CODE_VOUCHER_GEN_FAILED + "] sourceType=" + sourceType
                    + " billNo=" + bill.getBillNo() + " billId=" + bill.getId()
                    + " companyId=" + bill.getCompanyId() + " | " + reason;
            SysOperLog row = new SysOperLog();
            row.setTenantId(tenantId);
            row.setTitle("单据自动生成凭证失败");
            row.setBusinessType("OTHER");
            row.setMethod("ExpenseWfEventListener#handleApproved");
            row.setStatus(1);
            row.setErrorMsg(msg.length() <= OPER_LOG_MSG_MAX ? msg : msg.substring(0, OPER_LOG_MSG_MAX));
            row.setCostTime(0L);
            row.setOperTime(LocalDateTime.now());
            operLogWriter.writeSys(row);
        } catch (Exception ignore) {
            // 审计写入失败不得影响收尾链
        }
    }

    /** 运维侧：主动告警（限流由 OpsAlertService 负责；告警本身绝不抛出） */
    private void alertVoucherFail(ExpBill bill, String sourceType, String reason) {
        try {
            opsAlertService.alert("ERROR", "单据自动生成凭证失败 " + CODE_VOUCHER_GEN_FAILED,
                    "单据 " + bill.getBillNo() + "（billId=" + bill.getId() + "，类型 " + bill.getBillType()
                            + "，companyId=" + bill.getCompanyId() + "）审批通过后自动生成凭证失败。\n"
                            + "凭证来源 sourceType=" + sourceType + "\n"
                            + "原因：" + reason + "\n"
                            + "影响：该单总账缺凭证，全链路时间线断在 VOUCHER 段。\n"
                            + "处置：处理后经「" + VOUCHER_RETRY_ENTRY + "」补生成。");
        } catch (Exception ignore) {
            // 告警失败不得影响收尾链
        }
    }

    /** 驳回/撤回分支 */
    private void handleRejected(String bizType, ExpBill bill, boolean withdrawn, String rejectType, String comment) {
        // 还款单的预算环节与其余单据相反：提交时它不占用，而是<b>冲减借款单的占用</b>，
        // 所以驳回/撤回要做的是把那次冲减<b>等额回补</b>——还款没办成，额度不该少。
        // 按行 release(biz_id=还款单) 什么也释放不出来（还款单从未以自身 biz_id 占用过），
        // 还会把一笔不存在的占用记成负数流水。
        // 回补对象从 bg_occupy 反查（当初冲在哪些借款单上），不重算 FIFO，与提交环节同源。
        if ("REPAY".equals(bill.getBillType())) {
            budgetService.restoreLoanOccupyByRepayNo(bill.getBillNo(),
                    withdrawn ? "还款单撤回回补借款占用" : "还款单驳回回补借款占用");
        } else {
            // 释放必须对所有单据类型一视同仁：提交时占用是按 billType 映射记的，
            // 只在 bizType=EXP_CLAIM 时释放，APPLY/LOAN/PAY 单被驳回或撤回后占用就永久悬挂，额度还不回去。
            // bizType 是流程类型，与占用类型不同名，还原占用规则必须走 billType。
            // 占用类型统一由 BudgetService.occupyBizTypeOf 给出：各写一套的话，
            // LOAN 单占用记 EXP_LOAN、释放记 EXP_CLAIM，台账按类型读就对不上。
            String occupyBizType = BudgetService.occupyBizTypeOf(bill.getBillType());
            for (ExpBillLine line : linesOf(bill.getId())) {
                if (line.getBudgetItemId() != null && line.getAmount() != null) {
                    budgetService.release(line.getBudgetItemId(), line.getAmount(),
                            occupyBizType, bill.getId(), bill.getBillNo());
                }
            }
        }
        // 结清只在 addLoanOffset（提单期）写、驳回/撤回不回补的话，借款单余额会永久停在 0。
        // 此处按本还款单已落的 exp_loan_offset(REPAY) 反向回补余额与状态。
        if ("REPAY".equals(bill.getBillType())) {
            restoreLoanBalanceOnReject(bill, withdrawn);
        }
        // 报销单驳回/撤回同样要释放借款冲抵占用。
        // 在途量登记在 exp_bill_relation(LOAN_CLAIM)（按目标单状态判占用，转入驳回/撤回即释放）；
        // 这里另清「提单期就落了 CLAIM 流水并扣余额」的历史单，否则这类单的余额照样回不来。
        if ("CLAIM".equals(bill.getBillType())) {
            releaseLoanClaimOnReject(bill, withdrawn);
        }
        // 驳回类型写入数据库（漏单驳回=电子流直送我，只能重传影像/改简字段；逐级驳回=字段可全改）
        ExpBill upd = new ExpBill();
        upd.setId(bill.getId());
        upd.setStatus(withdrawn ? "WITHDRAWN" : "REJECTED");
        if (!withdrawn) {
            upd.setRejectType(rejectType != null
                    ? ("THROW_BACK".equals(rejectType) ? "THROW_BACK" : "GRADE_BY_GRADE")
                    : "GRADE_BY_GRADE");
            // 驳回意见必须落到单据本体：只进流程历史与审计记录的话，移动端卡片显示「审批意见：—」。
            // remark 原有的两个写点（核减、防篡改拦截）都不走驳回路径。
            upd.setAuditRemark(remarkOf(comment));
        }
        expBillMapper.updateById(upd);
        // 驳回/撤回即死亡最终状态。正常时序下作业链要等审批通过才建（createChain 在 handleApproved 内），
        // 此处通常无可办结；但反审批/补账等旁路可能留下残留池任务——
        // 一并办结，杜绝「单已死、工单还挂着等人认领」的孤儿。
        sharingService.closeOnBillTerminal(bill.getId(),
                withdrawn ? "单据已撤回" : "单据已驳回");
    }

    /** audit_remark 是 varchar(1000)，超长必须截，否则收尾整链（含状态）一起失败 */
    private static String remarkOf(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.length() <= AUDIT_REMARK_MAX ? comment : comment.substring(0, AUDIT_REMARK_MAX);
    }

    /**
     * 还款核销（流程最终状态回写）：把借款人的借款余额减掉、落 {@code exp_loan_offset(offsetType=REPAY)} 流水，
     * 并统一判定 REPAID／PARTIAL_REPAID 最终状态。
     *
     * <p><b>冲抵流水与余额减记的唯一写点在这里</b>。
     * 提单期若另有一处写点（{@code ExpenseDetailService.addLoanOffset} 的 REPAY 分支，
     * 登记流水＋增量减记＋APPROVED→PARTIAL_REPAID 一次做完），审批环节就会被
     * 「已登记满额 ⇒ remain=0」顶成空转：流水早在提单那一下落好，三个审批节点对「已结清」没有任何控制力。
     * 所以提交环节只登记在途占用（{@code exp_bill_relation(LOAN_CLAIM).use_amount}），
     * <b>本方法与 {@code offsetLoansByClaim} 是冲抵流水与余额减记的唯一写点</b>：
     * ①读现存流水 → ②-a 先按提交期登记的那张借款单补差落流水（用户勾的是哪张就冲哪张，不再被 FIFO 换单）
     * → ②-b 登记缺失的历史/只填金额路径退回溯 FIFO → ③最终状态判定与预算对齐。
     * 报销冲抵与还款冲抵由此同一个环节、同一算式（关系＝在途应冲额、流水＝已生效额、余额＝Σ流水）。
     *
     * <p><b>结清时点</b>：
     * <ol>
     *  <li><b>候选状态判据</b>：只认 {@code APPROVED} 会把提单期已迁到在途中间态（PARTIAL_REPAID）／
     *      归零态（REPAID）的借款单全部漏掉，本方法对已提交的还款单一律空转，
     *      借款是否结清实际由制单人提单那一下决定。判据是 {@code APPROVED／PARTIAL_REPAID／REPAID}，
     *      末尾由本方法（最终状态）统一判定 REPAID／PARTIAL_REPAID。</li>
     *  <li><b>去重</b>：先读本还款单已登记的 {@code exp_loan_offset(REPAY)} 台账，只对
     *      「还款额 − 已登记额」的差额补冲抵。不这么做会对未空转的单再插一批冲抵，
     *      把已经扣过的余额<b>二次减记</b>。</li>
     *  <li><b>不新增枚举</b>：在途中间态复用既有 {@code PARTIAL_REPAID}，REPAID 只在这里（链路最终状态）落。</li>
     * </ol>
     *
     * <p><b>结清环节</b>：提单期的预算冲减按当时在贷余额做 FIFO 预分配，审批期借款池可能已变；
     * 此处按实际冲抵额调 {@code BudgetService.alignLoanOccupy} 校准台账净额，
     * 相等时不追加流水（去重）。
     */
    private void repayLoan(ExpBill repayBill) {
        if (repayBill.getApplyUserId() == null) {
            return;
        }
        // ① 已登记的冲抵台账（本方法上次收尾重放写入的，或早期提交期写入的历史）
        Map<Long, BigDecimal> bookedByLoan = new LinkedHashMap<>();
        BigDecimal booked = BigDecimal.ZERO;
        for (ExpLoanOffset o : expLoanOffsetMapper.selectList(new LambdaQueryWrapper<ExpLoanOffset>()
                .eq(ExpLoanOffset::getBillId, repayBill.getId())
                .eq(ExpLoanOffset::getOffsetType, "REPAY"))) {
            if (o.getLoanBillId() == null) {
                continue;
            }
            bookedByLoan.merge(o.getLoanBillId(), nvl(o.getOffsetAmount()), BigDecimal::add);
            booked = booked.add(nvl(o.getOffsetAmount()));
        }
        // ② 差额补冲抵（提单期未登记冲抵的老路径、或部分登记时在这里补齐）
        BigDecimal remain = nvl(repayBill.getTotalAmount()).subtract(booked);
        if (remain.signum() < 0) {
            log.warn("还款单 {} 已登记冲抵合计({}) 大于还款金额({})，不再补冲抵",
                    repayBill.getBillNo(), booked, repayBill.getTotalAmount());
            remain = BigDecimal.ZERO;
        }
        // ②-a 提交期登记的在途占用优先（甲①后 REPAY 环节提单期只剩这一份证据）：
        //      应冲额取关系行 use_amount，扣掉已登记流水后补差，冲的就是用户勾的那张借款单。
        if (remain.signum() > 0) {
            List<ExpBillRelation> rels = expBillRelationMapper.selectList(new LambdaQueryWrapper<ExpBillRelation>()
                    .eq(ExpBillRelation::getTargetBillId, repayBill.getId())
                    .eq(ExpBillRelation::getRelationType, "LOAN_CLAIM")
                    .orderByAsc(ExpBillRelation::getId));
            for (ExpBillRelation rel : rels) {
                if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                ExpBill loan = rel.getSourceBillId() == null ? null : expBillMapper.selectById(rel.getSourceBillId());
                if (loan == null || !"LOAN".equals(loan.getBillType())) {
                    continue;
                }
                BigDecimal already = bookedByLoan.getOrDefault(loan.getId(), BigDecimal.ZERO);
                BigDecimal gap = nvl(rel.getUseAmount()).subtract(already);
                if (gap.signum() <= 0) {
                    continue;   // 该登记已足额落成流水（收尾重放/发布边界前的提交期历史），去重跳过
                }
                BigDecimal loanBal = nvl(loan.getLoanBalance());
                BigDecimal plan = gap.min(remain).min(loanBal.signum() < 0 ? BigDecimal.ZERO : loanBal);
                if (plan.signum() <= 0) {
                    // 在途不锁台账 ⇒「提交时够、通过时不够」是可能的（同一笔借款已被先批掉的
                    // 报销/还款冲走）。此处一笔不冲，但登记额仍进表头 offset_amount ⇒ 必须 WARN 供对账。
                    log.warn("还款单 {} 登记冲销借款单 {} 应冲 {}，但该借款单台账余额为 {}（已被其他还款/报销冲抵），"
                            + "本登记零冲抵，请核对是否少核销",
                            repayBill.getBillNo(), loan.getBillNo(), gap, loanBal);
                    continue;
                }
                ExpLoanOffset o = new ExpLoanOffset();
                o.setBillId(repayBill.getId());
                o.setLoanBillId(loan.getId());
                o.setLoanBillNo(loan.getBillNo());
                o.setOffsetAmount(plan);
                o.setOffsetType("REPAY");
                o.setOffsetDate(LocalDate.now());
                // 登记人取当前登录人（审批链办理人）；取不到（异步收尾线程/无会话）回落借款申请人。
                // 该列没有自动填充注解，不显式 set 就是 NULL。
                o.setCreateBy(currentUserOr(loan.getApplyUserId()));
                expLoanOffsetMapper.insert(o);
                recalcLoanBalance(loan.getId());
                bookedByLoan.merge(loan.getId(), plan, BigDecimal::add);
                remain = remain.subtract(plan);
                log.info("还款单 {} 按提交期登记对借款单 {} 冲抵 {}（{} → 台账重算）",
                        repayBill.getBillNo(), loan.getBillNo(), plan, loanBal);
            }
        }
        // ②-b 登记缺失（历史单/未选借款单只填金额）时，按申请人在贷的 LOAN 单 id 升序 FIFO 补齐（原规则）
        if (remain.signum() > 0) {
            var loans = expBillMapper.selectList(new LambdaQueryWrapper<ExpBill>()
                    .eq(ExpBill::getApplyUserId, repayBill.getApplyUserId())
                    .eq(ExpBill::getBillType, "LOAN")
                    .in(ExpBill::getStatus, "APPROVED", "PARTIAL_REPAID", "REPAID")
                    .orderByAsc(ExpBill::getId));
            for (ExpBill loan : loans) {
                if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal loanBal = nvl(loan.getLoanBalance());
                if (loanBal.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal offset = remain.min(loanBal);
                ExpLoanOffset o = new ExpLoanOffset();
                o.setBillId(repayBill.getId());
                o.setLoanBillId(loan.getId());
                o.setLoanBillNo(loan.getBillNo());
                o.setOffsetAmount(offset);
                o.setOffsetType("REPAY");
                o.setOffsetDate(LocalDate.now());
                o.setCreateBy(currentUserOr(loan.getApplyUserId()));
                // 只登记人缺失，本方法是新增环节，直接带上经办人
                expLoanOffsetMapper.insert(o);
                // 余额按台账绝对量重算（/391 甲②）；结清最终状态仍由 ③ 的 settleLoanAfterRepay 判定
                recalcLoanBalance(loan.getId());
                bookedByLoan.merge(loan.getId(), offset, BigDecimal::add);
                remain = remain.subtract(offset);
            }
            if (remain.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("还款 {} 超出在贷余额 {}，余量未核销", repayBill.getBillNo(), remain);
            }
        }
        // ③ 最终状态判定 + 预算冲减对齐（只有走到这里才有权置 REPAID）
        for (Map.Entry<Long, BigDecimal> en : bookedByLoan.entrySet()) {
            settleLoanAfterRepay(en.getKey(), repayBill, en.getValue());
        }
    }

    /**
     * 结清最终状态判定：按借款单当前余额把状态定为 REPAID（已结清）／PARTIAL_REPAID（仍在贷），
     * 并把本还款单对该借款单的预算冲减对齐到实际冲抵额（结清环节）。
     */
    private void settleLoanAfterRepay(Long loanBillId, ExpBill repayBill, BigDecimal actualOffset) {
        ExpBill loan = expBillMapper.selectById(loanBillId);
        if (loan == null) {
            return;
        }
        BigDecimal bal = nvl(loan.getLoanBalance());
        // 只回写「已结清／在途」两态，VOID/CLOSED 等非在贷态不复活
        if ("REPAID".equals(loan.getStatus()) || "PARTIAL_REPAID".equals(loan.getStatus())
                || "APPROVED".equals(loan.getStatus())) {
            String target = bal.signum() == 0 ? "REPAID" : "PARTIAL_REPAID";
            if (!target.equals(loan.getStatus())) {
                ExpBill loanUpd = new ExpBill();
                loanUpd.setId(loanBillId);
                loanUpd.setStatus(target);
                expBillMapper.updateById(loanUpd);
                log.info("借款单 {} 结清终态判定：{} → {}（余额 {}）", loan.getBillNo(), loan.getStatus(), target, bal);
                if ("REPAID".equals(target)) {
                    // ⑨：借款结清=业务最终状态，合规审核/资金支付/账务处理链不会再有人办，全部办结
                    //（借款的钱环节走付款批次但 规则下借款单不置 PAID，批次侧办结管不到它，只能在此收）
                    sharingService.closeOnBillTerminal(loanBillId, "借款已结清");
                }
            }
        }
        budgetService.alignLoanOccupy(loanBillId, actualOffset, repayBill.getBillNo(), null,
                "还款单结清对齐预算冲减");
    }

    /**
     * 还款单驳回/撤回：回补本单已登记的冲抵（缺失的回退路径）。
     *
     * <p>提单期 {@code addLoanOffset} 会把 {@code loan_balance} 减记，还款没办成时没人加回来，
     * 余额就永久停在 0——这是提交环节减记时的形态。
     * <b>改成提交环节只登记占用之后</b>：不再落流水也不减记，还款单被驳回时按定义就没有
     * 已生效冲抵可回退（本方法对这类单读到 0 行 ⇒ 空转，正确）；它现在兜的是发布边界前的历史，
     * 与在途占用的自然释放（关系行随单据状态退出 DRAFT/APPROVING 即出 {@code transitLoanOccupancy} 规则）
     * 分两路，两路都不双回补。
     *
     * <p>表头 {@code offset_amount} 只在真回退了流水时才回退（甲①后表头额＝提交期登记额，
     * 与关系行同源，回退它会让「编辑重提」的差额算式再扣一遍）。
     */
    private void restoreLoanBalanceOnReject(ExpBill repayBill, boolean withdrawn) {
        String reason = withdrawn ? "还款单撤回，冲抵作废" : "还款单驳回，冲抵作废";
        BigDecimal restored = invalidateLoanOffsetFlows(repayBill, "REPAY", reason);
        // 还款单表头的 offset_amount 同步回退：addLoanOffset 是「累加」写表头的，
        // 不回退则驳回后重提会再加一次（offset_amount 翻倍 → payable_amount 被打成 0）。
        if (restored.signum() > 0) {
            BigDecimal back = nvl(repayBill.getOffsetAmount()).subtract(restored);
            ExpBill self = new ExpBill();
            self.setId(repayBill.getId());
            self.setOffsetAmount(back.signum() < 0 ? BigDecimal.ZERO : back);
            expBillMapper.updateById(self);
        }
    }

    /**
     * 冲抵流水的逻辑作废 + 余额按台账重算（甲③唯一回退算式；驳回、撤回、反审核三条逆环节共用，
     * 与 {@code repayLoan}/{@code offsetLoansByClaim} 两条扣环节一一对应）。
     *
     * <p><b>去重</b>：以「逻辑作废该条冲抵流水」为认领校验——
     * {@code exp_loan_offset} 无回退列（已记：只有 create_by，无 update_by/状态列，
     * 也没有 {@code @TableField(fill=...)} 的更新审计列），用其自带 {@code del_flag} 做原子认领
     * （MP 逻辑删除带 {@code del_flag=0} 条件，收尾重放/并发时第二次 {@code deleteById} 返回 0 行即跳过），
     * 行仍在库里（del_flag=1）不丢审计。
     *
     * <p>余额与状态一律交 {@link #recalcLoanBalance} 按台账重算（甲②）：删掉这些流水后，
     * 有存活冲抵→PARTIAL_REPAID、一笔不剩→APPROVED（未还），判据收在同一处，不在本方法里做增量加法。
     *
     * @param reason 作废记录文案前缀（写进流水 remark，界面与审计轨迹可见）
     * @return 本单据被作废的冲抵额合计（0＝没有已生效流水可反冲）
     */
    private BigDecimal invalidateLoanOffsetFlows(ExpBill bill, String offsetType, String reason) {
        List<ExpLoanOffset> booked = expLoanOffsetMapper.selectList(new LambdaQueryWrapper<ExpLoanOffset>()
                .eq(ExpLoanOffset::getBillId, bill.getId())
                .eq(ExpLoanOffset::getOffsetType, offsetType));
        if (booked.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal invalidated = BigDecimal.ZERO;
        Set<Long> loans = new LinkedHashSet<>();
        for (ExpLoanOffset o : booked) {
            if (o.getLoanBillId() == null || o.getId() == null) {
                continue;
            }
            // 记录先行（updateById 走 del_flag=0 条件，作废后就改不动了）
            ExpLoanOffset mark = new ExpLoanOffset();
            mark.setId(o.getId());
            mark.setRemark(reason + "（" + bill.getBillNo() + "）");
            expLoanOffsetMapper.updateById(mark);
            if (expLoanOffsetMapper.deleteById(o.getId()) != 1) {
                continue;   // 已被其它重放认领
            }
            loans.add(o.getLoanBillId());
            invalidated = invalidated.add(nvl(o.getOffsetAmount()));
        }
        for (Long loanId : loans) {
            recalcLoanBalance(loanId);
        }
        log.info("单据 {} {}：冲抵流水 {} 笔中认领作废 {} 笔、合计 {}，涉及借款单 {} 张，余额已按台账重算",
                bill.getBillNo(), reason, booked.size(), loans.size(), invalidated, loans.size());
        return invalidated;
    }

    /**
     * 反审核（APPROVED→DRAFT）环节的结清环节反冲（扣与冲必须同一条链路一一对应）。
     *
     * <p>减记从提交环节挪到通过环节之后，{@code ExpenseBillService.unaudit} 就成了通过环节的逆操作：
     * 单据退回草稿，而 {@code exp_loan_offset} 流水与 {@code loan_balance} 减记、REPAID/PARTIAL_REPAID
     * 翻档却留在原地 ⇒ 「单未生效、账已结清」这个形态又在反审核环节上长出来。
     * 故反审核必须按本单已生效流水逐条作废并重算余额，与 {@code handleRejected} 同一算式
     * （{@link #invalidateLoanOffsetFlows}），不另造一套。
     *
     * <p>不动的两处，理由如实写：
     * ①表头 {@code offset_amount} 不回退——提交期登记额与 {@code exp_bill_relation(LOAN_CLAIM)} 同源，
     *   关系行保留（无 del_flag，删行＝销毁追溯证据），单据回到 DRAFT 后占用规则自动恢复"在途"，
     *   重提走差额算式不会重复计；
     * ②预算环节与收款单环节不走这条规则（还款单的预算冲减按 {@code bg_occupy} 净额自校验、
     *   {@code rc_receipt} 是资金事实），仍按 {@code unaudit} 原注释只记录。
     *
     * @return 被反冲的冲抵额合计（0＝本单无已生效冲抵，或该单据类型不走借款台账）
     */
    public BigDecimal rollbackLoanLedgerOnUnaudit(ExpBill bill) {
        if (bill == null || bill.getId() == null) {
            return BigDecimal.ZERO;
        }
        if ("REPAY".equals(bill.getBillType())) {
            return invalidateLoanOffsetFlows(bill, "REPAY", "还款单反审核，冲抵作废");
        }
        if ("CLAIM".equals(bill.getBillType())) {
            return invalidateLoanOffsetFlows(bill, "CLAIM", "报销单反审核，冲抵作废");
        }
        return BigDecimal.ZERO;
    }

    /** 收尾线程内的当前登录人（同步事件在 HTTP 线程上跑，取不到时回落到兜底 userId） */
    private Long currentUserOr(Long fallback) {
        LoginUser u = UserContext.get();
        return u != null && u.getUserId() != null ? u.getUserId() : fallback;
    }

    /**
     * 借款余额绝对量重算（/391 甲②）：<b>余额 = 借款额 − Σ该借款单现存冲抵流水</b>
     * （{@code exp_loan_offset}，含 CLAIM／REPAY 两类，逻辑删除行由 {@code @TableLogic} 自动排除）。
     *
     * <p>写点从「增量减记」改成「按台账重算」，为的是：同一事件重复回调结果恒定（去重），
     * 且「{@code loan_balance} = {@code exp_loan_offset} 净额」这条不变式在提交/审批/驳回三个环节上同时成立
     * ——原三处 {@code setLoanBalance(旧值±本次)} 各自只看自己那一笔，任何一处漏配（双扣、驳回不回补）
     * 都会把余额永久留在错值上，而错值无从自证。
     *
     * <p>状态沿用既有三态名称表，不新增枚举：无现存冲抵流水 → APPROVED（未还）、有且归零 → REPAID（结清）、
     * 有且未归零 → PARTIAL_REPAID（在贷）。只在 {APPROVED, PARTIAL_REPAID, REPAID} 三态内迁移，
     * VOID/CLOSED 等非在贷态不复活（与 {@code settleLoanAfterRepay} 同判据）。
     * 重算出现负余额时如实写负值并告警——归零会把台账失真藏起来，负余额在界面可查。
     *
     * @return 重算后的余额；借款单不存在/非 LOAN 型时返回 null
     */
    private BigDecimal recalcLoanBalance(Long loanBillId) {
        ExpBill loan = loanBillId == null ? null : expBillMapper.selectById(loanBillId);
        if (loan == null || !"LOAN".equals(loan.getBillType())) {
            return null;
        }
        BigDecimal booked = BigDecimal.ZERO;
        for (ExpLoanOffset o : expLoanOffsetMapper.selectList(new LambdaQueryWrapper<ExpLoanOffset>()
                .eq(ExpLoanOffset::getLoanBillId, loanBillId))) {
            booked = booked.add(nvl(o.getOffsetAmount()));
        }
        BigDecimal bal = nvl(loan.getTotalAmount()).subtract(booked);
        if (bal.signum() < 0) {
            log.warn("借款单 {} 冲抵台账({}) 已超出借款额({})，余额按台账写为 {}，请核对该单冲抵流水",
                    loan.getBillNo(), booked, loan.getTotalAmount(), bal);
        }
        ExpBill upd = new ExpBill();
        upd.setId(loanBillId);
        upd.setLoanBalance(bal);
        String cur = loan.getStatus();
        if ("APPROVED".equals(cur) || "PARTIAL_REPAID".equals(cur) || "REPAID".equals(cur)) {
            String target = booked.signum() == 0 ? "APPROVED" : (bal.signum() == 0 ? "REPAID" : "PARTIAL_REPAID");
            if (!target.equals(cur)) {
                upd.setStatus(target);
            }
        }
        expBillMapper.updateById(upd);
        log.info("借款单 {} 余额按台账重算：{} → {}（现存冲抵流水合计 {}，状态 {}）",
                loan.getBillNo(), loan.getLoanBalance(), bal, booked, upd.getStatus() == null ? cur + "(不变)" : upd.getStatus());
        return bal;
    }

    /**
     * 报销单驳回/撤回时释放在途冲抵占用。
     *
     * <p>①提交环节把 CLAIM 的在途量登记在 {@code exp_bill_relation(LOAN_CLAIM).use_amount}，
     * 关系行<b>保留不删</b>：本表无 {@code del_flag}，物理删就是销毁「这张报销单勾过哪张借款单」的
     * 追溯证据（追溯链七档规则）。占用判据只看未生效在途态（{@code ExpenseDetailService.transitLoanOccupancy}
     * 计 DRAFT/APPROVING，{@code assertRelationUseAmount} 排除 DRAFT/REJECTED/WITHDRAWN/CANCELLED/VOID/CLOSED）
     * ⇒ 单据转入 REJECTED/WITHDRAWN 即在途占用自动释放，无需删行。
     *
     * <p>要清的是<b>提单期已落下的冲抵流水</b>：提交环节会落 {@code exp_loan_offset(CLAIM)} 流水
     * 并扣减余额，而驳回侧只有 {@code restoreLoanBalanceOnReject} 回补 REPAY 环节；
     * 这类流水不在此作废就会永久占着借款单余额。处理按本单 CLAIM 流水逐条逻辑作废
     * （{@code del_flag} 原子认领，重放不双回补）+ 绝对量重算余额。
     * 报销单从未通过审批 ⇒ 其 CLAIM 流水按定义就不是"已生效冲抵"，作废它与新规则自洽；
     * 驳回后重提再由审批环节按关系行重新登记，算式不变。
     */
    private void releaseLoanClaimOnReject(ExpBill claim, boolean withdrawn) {
        String reason = withdrawn ? "报销单撤回，在途冲抵作废" : "报销单驳回，在途冲抵作废";
        invalidateLoanOffsetFlows(claim, "CLAIM", reason);
    }

    /**
     * 还款到账的收款事实写入数据库（表 {@code exp_bill_receipt}）。
     * <p>{@code rc_receipt} 是应收收款语义（customer_id + 应收核销），员工还款在那张表里无处挂账。
     * 资金流入侧需要一个按单据（bill_id）而非按往来单位组织的载体，追溯链「收款」环节据此读数。
     * <p>还款单表单没有付款人账号字段，{@code bank_account} 留空由出纳补录；
     * 去重按 bill_id（收尾事件可能重放）；写入时显式 {@code setCreateBy}（该列无自动填充注解，不设即落 NULL）。
     */
    private void registerRepayFundReceipt(ExpBill repayBill) {
        try {
            if (repayBill.getId() == null) {
                return;
            }
            BigDecimal amt = repayBill.getApprovedAmount() != null
                    && repayBill.getApprovedAmount().signum() > 0
                    ? repayBill.getApprovedAmount() : nvl(repayBill.getTotalAmount());
            if (amt.signum() <= 0) {
                log.warn("还款单 {} 金额为 0，不登记收款事实", repayBill.getBillNo());
                return;
            }
            Long exists = expBillReceiptMapper.selectCount(new LambdaQueryWrapper<ExpBillReceipt>()
                    .eq(ExpBillReceipt::getBillId, repayBill.getId()));
            if (exists != null && exists > 0) {
                log.info("还款单 {} 已有 {} 条收款事实，跳过重复登记（幂等）", repayBill.getBillNo(), exists);
                return;
            }
            Long operatorId = currentUserOr(repayBill.getApplyUserId());
            ExpBillReceipt r = new ExpBillReceipt();
            r.setTenantId(TenantContext.getTenantId());
            r.setBillId(repayBill.getId());
            r.setLineNo(1);
            r.setPayerType("EMPLOYEE");
            r.setPayerId(repayBill.getApplyUserId());
            r.setPayerName(resolveEmployeeName(repayBill));
            r.setReceiveMethod("BANK");
            r.setCurrencyCode(repayBill.getCurrencyCode() == null ? "CNY" : repayBill.getCurrencyCode());
            r.setAmount(amt);
            r.setReceiptStatus("RECEIVED");
            r.setReceiptTime(LocalDateTime.now());
            r.setProcInstId(repayBill.getProcInstId());
            r.setCreateBy(operatorId);
            r.setRemark("还款审批通过登记的收款事实（资金流入侧载体，待与银行流水勾对）");
            expBillReceiptMapper.insert(r);
            log.info("还款单 {} 审批通过，登记收款事实 exp_bill_receipt id={} 金额 {} 还款人[{}]",
                    repayBill.getBillNo(), r.getId(), amt, r.getPayerName());
            auditTrailService.record(repayBill.getBillType(), repayBill.getId(), repayBill.getBillNo(),
                    "exp_bill_receipt", String.valueOf(r.getId()), "FUND_RECEIPT_REGISTER", null, null,
                    "还款到账收款事实：" + amt + " 元，还款人 " + r.getPayerName()
                            + "，经办人 userId=" + operatorId);
        } catch (Exception ex) {
            log.error("还款单 {} 收款事实登记失败: {}", repayBill.getBillNo(), ex.getMessage(), ex);
        }
    }

    /**
     * 借款放款链路：借款单审批通过 → 生成 WAITING「放款」付款记录，出纳经付款批次（TR_PAY）付出。
     * <p><b>为什么要这一步</b>：EXP_LOAN 通过分支只做「凭证 + loan_balance + 作业链」的话，
     * 全库就没有放款动作了，而组批只消费 {@code exp_bill_payment(WAITING)} ⇒
     * 已批借款无处付出，只能停在 PAY 工单、BOOKKEEP 工单悬待。
     * 真实放款通道是 TR_PAY 资金付款批次：批次付讫后由 {@code PayBatchService.writeBackPayment} 回写 paid_amount/pay_time，
     * 并把借款凭证（TPL_LOAN 借 1221 / 贷 1002）从 DRAFT 自动过账（实付入账）。
     *
     * <p><b>为什么不写 payable_amount</b>：借款的在贷规则由 {@code loan_balance} 承载，还款核销 FIFO 与
     * 逾期/限额判定（{@code repayLoan}、合规预检、借款台账）都按 status ∈ {APPROVED, PARTIAL_REPAID}
     * 认"在贷借款"。若把应付上限写成借款金额，付讫时 writeBackPayment 会把状态迁成 PAID，
     * 该笔借款随即从在贷池消失、还款冲不掉余额（等于用一个资金缺陷换另一个）。
     * 放款上限要不要用 payable_amount 表达、「已放款」怎么建态，属产品规则，这里不擅自扩状态机。
     *
     * <p><b>去重</b>：同一借款单已存在任意付款记录时不再生成（防收尾重放打出第二条放款指令）。
     */
    private void createLoanDisbursementPayment(ExpBill loan) {
        try {
            BigDecimal amt = nvl(loan.getTotalAmount());
            if (amt.signum() <= 0) {
                log.info("借款单 {} 金额为空/为零，不生成放款付款记录", loan.getBillNo());
                return;
            }
            Long exists = expBillPaymentMapper.selectCount(new LambdaQueryWrapper<ExpBillPayment>()
                    .eq(ExpBillPayment::getBillId, loan.getId()));
            if (exists != null && exists > 0) {
                log.info("借款单 {} 已有 {} 条付款记录，跳过重复生成放款记录（幂等）", loan.getBillNo(), exists);
                return;
            }
            ExpBillPayment p = new ExpBillPayment();
            p.setTenantId(TenantContext.getTenantId());
            p.setBillId(loan.getId());
            p.setLineNo(1);
            // 放款付给借款人本人（员工），不是往来单位——组批按「收款方+出账账户」归组，收款方要落到人名
            String payeeName = loan.getPayeeName();
            if (payeeName == null || payeeName.isBlank()) {
                payeeName = resolveEmployeeName(loan);
            }
            p.setPayeeType("EMPLOYEE");
            p.setPayeeId(loan.getApplyUserId());
            p.setPayeeName(payeeName);
            p.setBankAccount(loan.getPayeeAccountNo());
            inheritPayMethodAndBank(loan, p); // 收款方式与开户行取自单据，未选缺省 BANK
            p.setCurrencyCode(loan.getCurrencyCode() == null ? "CNY" : loan.getCurrencyCode());
            p.setAmount(amt);
            p.setPayStatus("WAITING");
            p.setCreateTime(LocalDateTime.now());
            p.setProcInstId(loan.getProcInstId());
            p.setRemark("借款审批通过自动生成放款记录（放款经出纳付款批次执行）");
            expBillPaymentMapper.insert(p);
            log.info("借款单 {} 审批通过，自动生成放款付款记录 id={} 金额={} 借款人[{}]",
                    loan.getBillNo(), p.getId(), amt, payeeName);
        } catch (Exception ex) {
            // 放款记录生成失败不阻断审批收尾（凭证与在贷余额已成立），出纳可在付款工作台手工补建
            log.error("借款单 {} 自动生成放款记录失败: {}", loan.getBillNo(), ex.getMessage(), ex);
        }
    }

    /** 放款收款人姓名：优先 sys_user 姓名，反查失败回退单据存名或「员工+ID」占位（出纳在付款环节核对） */
    private String resolveEmployeeName(ExpBill loan) {
        try {
            if (loan.getApplyUserId() != null) {
                SysUser u = sysUserMapper.selectById(loan.getApplyUserId());
                if (u != null && u.getNickName() != null && !u.getNickName().isBlank()) {
                    return u.getNickName();
                }
            }
        } catch (Exception ex) {
            log.warn("借款单 {} 放款收款人姓名反查失败: {}", loan.getBillNo(), ex.getMessage());
        }
        return loan.getApplyUserName() == null || loan.getApplyUserName().isBlank()
                ? "员工" + loan.getApplyUserId() : loan.getApplyUserName();
    }

    /**
     * 付款单审批通过后自动生成待付款记录（WAITING），出纳可直接组批执行。
     * 金额取 payable_amount，无则 total_amount；收款方信息由出纳在付款环节补全。
     */
    private void createPaymentRecord(ExpBill bill) {
        try {
            ExpBillPayment p = new ExpBillPayment();
            p.setTenantId(TenantContext.getTenantId());
            p.setBillId(bill.getId());
            p.setLineNo(1);
            p.setPayeeType("SUPPLIER");
            // 收款方名称优先取单据填写的往来单位名称，否则用单据标题占位（出纳可在付款环节补全）
            String payeeName = (bill.getPayeeName() != null && !bill.getPayeeName().isBlank())
                    ? bill.getPayeeName()
                    : (bill.getTitle() != null && !bill.getTitle().isBlank() ? bill.getTitle() : "待定收款方");
            p.setPayeeName(payeeName);
            // 证据 12-25:87：payee_id 自建以来恒 NULL，只留一列名称文本
            // ⇒ 付款/对账/风控一律退化成按名称匹配。按既有主数据链回填供应商 ID
            // （poId→po_order.supplier_id，无订单退 contractId→cont_contract.customer_id，
            //   与凭证行辅助同源同一规则，取不到就留 NULL，不按 payeeName 猜一家）。
            p.setPayeeId(voucherService.billSupplierIdOf(bill));
            // 关联往来单位与合同，便于出纳核对与后续对账
            p.setCustomerId(bill.getCustomerId());
            p.setContractId(bill.getContractId());
            inheritPayMethodAndBank(bill, p); // 收款方式与开户行取自单据，未选缺省 BANK
            p.setCurrencyCode(bill.getCurrencyCode() == null ? "CNY" : bill.getCurrencyCode());
            BigDecimal amt = (bill.getPayableAmount() != null && bill.getPayableAmount().compareTo(BigDecimal.ZERO) > 0)
                    ? bill.getPayableAmount() : bill.getTotalAmount();
            p.setAmount(amt);
            p.setPayStatus("WAITING");
            p.setCreateTime(LocalDateTime.now());
            p.setProcInstId(bill.getProcInstId());
            expBillPaymentMapper.insert(p);
            log.info("付款单 {} 审批通过，自动生成付款记录 id={} 金额={}", bill.getBillNo(), p.getId(), amt);
            // 台账需求：支付记录在对公付款单引用合同后自动更新（线下支付可手工添加）
            if (bill.getContractId() != null) {
                ContPayRecord rec = new ContPayRecord();
                rec.setTenantId(TenantContext.getTenantId());
                rec.setContractId(bill.getContractId());
                rec.setPayAmount(amt);
                rec.setBillNo(bill.getBillNo());
                rec.setBillType("PAY");
                rec.setStatus("PAID");
                rec.setPayDate(LocalDate.now());
                contPayRecordMapper.insert(rec);
                log.info("合同 {} 支付记录自动更新: 付款单 {} 金额 {}", bill.getContractId(), bill.getBillNo(), amt);
            }
        } catch (Exception ex) {
            // 付款记录生成失败不影响单据状态，出纳可手工补建
            log.error("付款单 {} 自动生成付款记录失败: {}", bill.getBillNo(), ex.getMessage(), ex);
        }
    }

    private static final ObjectMapper OM = new ObjectMapper();

    /**
     * 把单据 ext_json 里用户选好的收款方式（payMethod）与开户行（bankName）继承到付款行。
     * 四条付款记录生成环节都不带这两个值时，付款方式一律写死 BANK、bank_name 没人填，
     * 现金/支票单在出纳面就恒显银行转账。
     * 未选/解析不了默认 BANK，开户行缺则留空不造；户名 payeeName 由前端镜像（useBill.js 载荷环节）。
     */
    private void inheritPayMethodAndBank(ExpBill bill, ExpBillPayment p) {
        String payMethod = null;
        String bankName = null;
        String accountNo = null;
        try {
            if (bill != null && bill.getExtJson() != null && !bill.getExtJson().isBlank()) {
                JsonNode ext = OM.readTree(bill.getExtJson());
                JsonNode pm = ext.get("payMethod");
                JsonNode bn = ext.get("bankName");
                JsonNode an = ext.get("accountNo");
                if (pm != null && pm.isTextual() && !pm.asText().isBlank()) {
                    payMethod = pm.asText().trim();
                }
                if (bn != null && bn.isTextual() && !bn.asText().isBlank()) {
                    bankName = bn.asText().trim();
                }
                if (an != null && an.isTextual() && !an.asText().isBlank()) {
                    accountNo = an.asText().trim();
                }
            }
        } catch (Exception ignore) {
            // ext_json 解析失败回落默认方式，不阻断付款记录生成
        }
        p.setPayMethod(payMethod != null ? payMethod : "BANK");
        if (bankName != null && (p.getBankName() == null || p.getBankName().isBlank())) {
            p.setBankName(bankName);
        }
        // 借款页的「账号」落在 ext_json.accountNo，而组批的「防付错」校验读付款记录 bank_account
        // 并要求非空。不补进来，放款付款记录的 bank_account 恒空，自动组批被拒并留下 0.00/0 笔空壳批次。
        // 与户名镜像同一规则，一并补上。
        if (accountNo != null && (p.getBankAccount() == null || p.getBankAccount().isBlank())) {
            p.setBankAccount(accountNo);
        }
    }

    /**
     * 求和 extJson.prepayOffsets[].offsetAmount（对公结算冲销预付合计）。
     */
    private BigDecimal sumPrepayOffsets(ExpBill bill) {
        BigDecimal sum = BigDecimal.ZERO;
        try {
            if (bill.getExtJson() == null || bill.getExtJson().isBlank()) return sum;
            JsonNode arr = OM.readTree(bill.getExtJson()).get("prepayOffsets");
            if (arr != null && arr.isArray()) {
                for (JsonNode o : arr) {
                    JsonNode amt = o.get("offsetAmount");
                    if (amt == null || amt.isNull()) continue;
                    if (amt.isNumber()) sum = sum.add(amt.decimalValue());
                    else if (amt.isTextual() && !amt.asText().isBlank()) sum = sum.add(new BigDecimal(amt.asText()));
                }
            }
        } catch (Exception ex) {
            log.warn("解析单据 {} 冲销预付明细失败: {}", bill.getBillNo(), ex.getMessage());
        }
        return sum;
    }

    /**
     * 回写被冲销预付单的 offset_amount（累计已冲销金额），防止预付被再次冲销（重复核销）。
     */
    private void writeBackPrepayOffsets(ExpBill bill) {
        try {
            if (bill.getExtJson() == null || bill.getExtJson().isBlank()) return;
            JsonNode arr = OM.readTree(bill.getExtJson()).get("prepayOffsets");
            if (arr == null || !arr.isArray()) return;
            for (JsonNode o : arr) {
                JsonNode bid = o.get("billId");
                JsonNode amt = o.get("offsetAmount");
                if (bid == null || bid.isNull() || amt == null || amt.isNull()) continue;
                long prepayId = bid.asLong();
                BigDecimal offset = amt.isNumber() ? amt.decimalValue()
                        : (amt.isTextual() && !amt.asText().isBlank() ? new BigDecimal(amt.asText()) : BigDecimal.ZERO);
                if (prepayId <= 0 || offset.signum() <= 0) continue;
                ExpBill prepay = expBillMapper.selectById(prepayId);
                if (prepay == null) continue;
                BigDecimal newOffset = (prepay.getOffsetAmount() == null ? BigDecimal.ZERO : prepay.getOffsetAmount()).add(offset);
                ExpBill upd = new ExpBill();
                upd.setId(prepayId);
                upd.setOffsetAmount(newOffset);
                expBillMapper.updateById(upd);
                log.info("对公结算 {} 冲销预付单 {} 金额 {}，预付累计已冲销 {}", bill.getBillNo(), prepay.getBillNo(), offset, newOffset);
            }
        } catch (Exception ex) {
            log.error("回写单据 {} 冲销预付失败: {}", bill.getBillNo(), ex.getMessage(), ex);
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 分多次报销：报销单审批通过后，按关联金额核销事前申请占用的预算（writeOff=min(实际占用,金额)），
     * 并累计申请单已报销金额；累计达到申请总额 → 状态 COMPLETED（报销完毕）。
     */
    private void writeOffApplyOccupy(ExpBill claim) {
        List<ExpBillRelation> rels = expBillRelationMapper.selectList(new LambdaQueryWrapper<ExpBillRelation>()
                .eq(ExpBillRelation::getTargetBillId, claim.getId())
                .eq(ExpBillRelation::getRelationType, "APPLY_CLAIM"));
        for (ExpBillRelation rel : rels) {
            ExpBill apply = expBillMapper.selectById(rel.getSourceBillId());
            if (apply == null || !"APPLY".equals(apply.getBillType())) {
                continue;
            }
            BigDecimal used = nvl(rel.getUseAmount());
            if (used.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            for (ExpBillLine line : linesOf(apply.getId())) {
                if (line.getBudgetItemId() != null && line.getAmount() != null) {
                    budgetService.writeOff(line.getBudgetItemId(), used,
                            "EXP_APPLY", apply.getId(), apply.getBillNo());
                }
            }
            BigDecimal approved = nvl(apply.getApprovedAmount()).add(used);
            ExpBill upd = new ExpBill();
            upd.setId(apply.getId());
            upd.setApprovedAmount(approved);
            if (approved.compareTo(nvl(apply.getTotalAmount())) >= 0) {
                upd.setStatus("COMPLETED");
                log.info("事前申请 {} 累计报销 {} 达申请额，报销完毕 COMPLETED", apply.getBillNo(), approved);
            }
            expBillMapper.updateById(upd);
        }
    }

    /**
     * 报销 vs 借款 3 种差额处理（费用报销通用规则）：
     * <pre>
     *  报销单审批通过时，与其关联的借款单（exp_bill_relation.LOAN_CLAIM）一起结算：
     *    - 先用报销金额 FIFO 冲抵各借款单余额（落 exp_loan_offset，offsetType=CLAIM）；
     *    - 差额 diff = 报销核定金额 − 关联借款金额：
     *         diff == 0 → 平账：不产生付款单，仅欠款结清；
     *         diff &gt; 0 → 补付：应付现金＝差额，由主链路统一生成一条付款记录；
     *         diff &lt; 0 → 退款：员工应还多借部分，<b>只留日志</b>——应还额本就体现在
     *                  借款单 {@code loan_balance>0 / status=PARTIAL_REPAID} 上，收款出口＝还款登记
     *                  RP_1【出纳确认还款到账】→RP_2【还款核销审核】；不再建 WAITING 付款记录，
     *                  因为该表没有方向列，组批与执行会把"该收"当"该付"打出去。
     * </pre>
     * 无关联借款单（普通报销）直接返回，不产生任何差额动作。
     *
     * @return 本报销单应付现金 = max(报销核定额 − 借款冲抵, 0)；
     *        无借款关联时 = 报销核定额；差额≤0（平账/需退款）时 = 0（无现金流出）
     */
    private BigDecimal handleLoanClaimDiff(ExpBill claim) {
        try {
            if (claim.getId() == null || claim.getApplyUserId() == null) {
                return BigDecimal.ZERO;
            }
            // 报销核定金额优先取审批核定，否则取单据总额
            BigDecimal claimAmt = nvl(claim.getApprovedAmount()) != null && nvl(claim.getApprovedAmount()).compareTo(BigDecimal.ZERO) > 0
                    ? claim.getApprovedAmount() : nvl(claim.getTotalAmount());
            if (claimAmt.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            // 找该报销单关联的全部借款单（relationType=LOAN_CLAIM，sourceBillId=借款单）
            List<ExpBillRelation> rels = expBillRelationMapper.selectList(new LambdaQueryWrapper<ExpBillRelation>()
                    .eq(ExpBillRelation::getTargetBillId, claim.getId())
                    .eq(ExpBillRelation::getRelationType, "LOAN_CLAIM"));
            // 关联借款金额（按关联记录 useAmount 累计，即本报销单对应的借款占用额）
            BigDecimal borrowTotal = BigDecimal.ZERO;
            if (!rels.isEmpty()) {
                for (ExpBillRelation rel : rels) {
                    borrowTotal = borrowTotal.add(nvl(rel.getUseAmount()));
                }
                // 用报销金额 FIFO 冲抵各借款单余额并落冲抵流水
                offsetLoansByClaim(claim, rels);
            } else {
                // E2E-001 修复：前端正常冲借款仅调 addLoanOffset（落 exp_loan_offset），
                // 不建 LOAN_CLAIM 关系；此处回退读取冲抵流水作为关联借款金额，
                // 避免查不到关系就退化成"普通报销全额付款"导致多付。
                List<ExpLoanOffset> offsets = expLoanOffsetMapper.selectList(new LambdaQueryWrapper<ExpLoanOffset>()
                        .eq(ExpLoanOffset::getBillId, claim.getId())
                        .eq(ExpLoanOffset::getOffsetType, "CLAIM"));
                for (ExpLoanOffset o : offsets) {
                    borrowTotal = borrowTotal.add(nvl(o.getOffsetAmount()));
                }
                if (borrowTotal.signum() > 0) {
                    log.info("报销单 {} 无 LOAN_CLAIM 关系，回退读取冲抵流水 {} 笔，关联借款合计 {}",
                            claim.getBillNo(), offsets.size(), borrowTotal);
                }
            }
            if (borrowTotal.signum() <= 0) {
                // 无借款关联，普通报销：应付现金 = 报销核定额
                return claimAmt;
            }
            BigDecimal diff = claimAmt.subtract(borrowTotal);
            int cmp = diff.compareTo(BigDecimal.ZERO);
            if (cmp == 0) {
                log.info("报销单 {} 与借款差额=0，平账结清（关联借款 {}，无需付款）", claim.getBillNo(), borrowTotal);
                return BigDecimal.ZERO;
            } else if (cmp > 0) {
                // 补付：报销金额 > 借款占用 → 返回差额，由主链路统一生成一条付款记录
                // （不在此另建，否则与 createClaimPayment 重复生成两条 WAITING）
                log.info("报销单 {} 差额补付: 报销 {} > 借款 {}，补付金额 {}", claim.getBillNo(), claimAmt, borrowTotal, diff);
                return diff;
            } else {
    // 顺带判定：报销 < 借款 ⇒ 员工应还差额。<b>不产 WAITING「差额退款」付款记录</b>。
                // WHY：exp_bill_payment 没有方向列，"该收"与"该付"同表同状态，而组批/执行只认
                // {@code pay_status='WAITING'}＋收款方归组（PayBatchService.groupWaiting）——把
                // 「员工归还多借部分，出纳收款待确认到账」记成付款记录，会被自动组进待执行<b>付款</b>批次，
                // 点执行就是把员工该还给公司的钱付给员工；且它在全仓没有任何"收款确认"消费方，
                // 而同一笔钱已有正规出口＝还款登记 RP_1【出纳确认还款到账】→RP_2【财务审核】。
                // 应还额本来就在借款单上（loan_balance>0、status=PARTIAL_REPAID），不需要第二条环节。
                BigDecimal refundAmt = diff.negate();
                log.warn("报销单 {} 差额退款：报销 {} < 借款 {}，员工应还 {}，留在借款单余额上走 还款登记（RP_1/RP_2）收，不再建付款记录",
                        claim.getBillNo(), claimAmt, borrowTotal, refundAmt);
                return BigDecimal.ZERO;
            }
        } catch (Exception ex) {
            // 差额处理失败不影响报销单状态，留日志供对账
            log.error("报销单 {} 借款差额处理失败: {}", claim.getBillNo(), ex.getMessage(), ex);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 报销审批通过环节（结清环节）：按 {@code LOAN_CLAIM} 关系登记的应冲额落 {@code exp_loan_offset}
     * offsetType=CLAIM，并把借款单余额交 {@link #recalcLoanBalance} 按台账重算
     * （余额归零 → REPAID 结清，仍有余额 → PARTIAL_REPAID 在贷）。
     *
     * <p><b>只认「应冲额 − 已登记额」的差额</b>：草稿期与审批通过环节各减一次余额、各落一条流水，
     * 对同一 (报销单, 借款单) 组合就会扣两次（80,000 → 72,000 → 64,000，流水两条）。
     * 差额规则与本类 {@code repayLoan} 同源、不另立判据：已登记满额的关联走下面的 skip 分支，
     * 一条流水也不追加。
     *
     * <p><b>分工</b>：提单期不落 CLAIM/REPAY 流水、不动 {@code loan_balance}
     * （{@code ExpenseDetailService.registerLoanOccupancy} 只把占用量登记在
     * {@code exp_bill_relation(LOAN_CLAIM).use_amount}，报销环节与还款环节同一处登记）⇒
     * <b>本方法与 {@code repayLoan} 是冲抵台账仅有的两个流水写点</b>：
     * 关系＝在途应冲额，流水＝已生效额，余额＝Σ流水。差额规则与 skip 分支同样用于
     * 兼容已带 CLAIM 流水的在途草稿。
     */
    private void offsetLoansByClaim(ExpBill claim, List<ExpBillRelation> rels) {
        BigDecimal claimAmt = nvl(claim.getApprovedAmount()) != null && nvl(claim.getApprovedAmount()).compareTo(BigDecimal.ZERO) > 0
                ? claim.getApprovedAmount() : nvl(claim.getTotalAmount());
        // ① 本报销单已登记的冲抵台账（收尾重放写入；提单期不写，读到的只可能是历史流水）
        Map<Long, BigDecimal> bookedByLoan = new LinkedHashMap<>();
        BigDecimal booked = BigDecimal.ZERO;
        for (ExpLoanOffset o : expLoanOffsetMapper.selectList(new LambdaQueryWrapper<ExpLoanOffset>()
                .eq(ExpLoanOffset::getBillId, claim.getId())
                .eq(ExpLoanOffset::getOffsetType, "CLAIM"))) {
            if (o.getLoanBillId() == null) {
                continue;
            }
            bookedByLoan.merge(o.getLoanBillId(), nvl(o.getOffsetAmount()), BigDecimal::add);
            booked = booked.add(nvl(o.getOffsetAmount()));
        }
        // ② 报销额对借款冲抵的总额校验，先扣掉已登记部分（同 repayLoan 的差额规则）
        BigDecimal remain = claimAmt.subtract(booked);
        if (remain.signum() < 0) {
            log.warn("报销单 {} 已登记冲抵合计({}) 大于报销金额({})，不再补冲抵",
                    claim.getBillNo(), booked, claimAmt);
            remain = BigDecimal.ZERO;
        }
        for (ExpBillRelation rel : rels) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            ExpBill loan = expBillMapper.selectById(rel.getSourceBillId());
            if (loan == null || !"LOAN".equals(loan.getBillType())) {
                continue;
            }
            BigDecimal loanBal = nvl(loan.getLoanBalance());
            BigDecimal already = bookedByLoan.getOrDefault(loan.getId(), BigDecimal.ZERO);
            if (loanBal.compareTo(BigDecimal.ZERO) <= 0) {
                // 在途不锁台账（提单期只登记占用、不扣余额），所以"提交时够、通过时不够"是可能的：
                // 同一名下多张报销单前后脚通过、或借款单被还款单先结清。此处一笔都冲不掉，
                // 但关系行上的应冲额仍会进入 handleLoanClaimDiff 的差额算式 ⇒ 必须留 WARN 供对账，不得静默。
                if (nvl(rel.getUseAmount()).subtract(already).compareTo(BigDecimal.ZERO) > 0) {
                    log.warn("报销单 {} 关联借款单 {} 应冲 {}，但该借款单台账余额已为 {}（已被其他报销/还款冲抵），"
                                    + "本关联零冲抵；差额算式仍按关系口径计，请核对是否少付/平账判错",
                            claim.getBillNo(), loan.getBillNo(), rel.getUseAmount(), loanBal);
                }
                continue;
            }
            // 本关联应冲抵额（关系规则，报销额封顶）− 已登记额 ＝ 待补净额，且不超过当前借款余额
            BigDecimal gap = nvl(rel.getUseAmount()).min(claimAmt).subtract(already);
            BigDecimal plan = gap.min(remain).min(loanBal);
            if (plan.compareTo(BigDecimal.ZERO) <= 0) {
                if (already.signum() > 0 && gap.signum() <= 0) {
                    log.info("报销单 {} 对借款单 {} 的冲抵 {} 已有登记流水，本节不重算余额/追加流水",
                            claim.getBillNo(), loan.getBillNo(), already);
                }
                continue;
            }
            // 落冲抵流水（=已生效冲抵台账的唯一写点）
            ExpLoanOffset o = new ExpLoanOffset();
            o.setBillId(claim.getId());
            o.setLoanBillId(loan.getId());
            o.setLoanBillNo(loan.getBillNo());
            o.setOffsetAmount(plan);
            o.setOffsetType("CLAIM");
            o.setOffsetDate(LocalDate.now());
            // ③ 规则统一到本环节：登记人＝当次办理人（审批链在 HTTP 线程上跑时取登录人），
            // 取不到回落申请人；该列无 fill 注解，不显式 set 就是 NULL。
            o.setCreateBy(currentUserOr(claim.getApplyUserId()));
            expLoanOffsetMapper.insert(o);
            // 借款单余额 + 状态：按台账绝对量重算，不做"旧值 − 本次"增量减记。
            BigDecimal newBal = recalcLoanBalance(loan.getId());
            bookedByLoan.merge(loan.getId(), plan, BigDecimal::add);
            remain = remain.subtract(plan);
            log.info("报销单 {} 对借款单 {} 冲抵 {}（{} → {}）", claim.getBillNo(), loan.getBillNo(), plan,
                    loanBal, newBal);
        }
    }

    /**
     * 报销→付款转换链：报销单审批通过后按应付现金自动生成待付款记录（WAITING），
     * 出纳在付款工作台 autoGroup 按收款方归组批次 → 确认 → 执行打款；
     * 全额付讫后 PayBatchService.writeBackPayment 自动回写 paid_amount 并迁移 APPROVED→PAID。
     *
     * <p><b>收款方判定</b>：
     * ① {@code pay_target}（代理报销的显式收款对象）<b>优先</b>——EMPLOYEE→付给申请人，
     *    SUPPLIER/CUSTOMER→付给往来单位；
     * ② 未指定 pay_target 的单据回退字符串比对。
     *
     * <p><b>回退比对必须同时认「工号 / 姓名」</b>：{@code apply_user_name} 存的是<b>工号</b>，
     * 只拿"收款方姓名"去等它必然 false ⇒ 所有未指定 pay_target 的报销单都被打成 SUPPLIER，
     * 待付款直接付给往来单位（付错对象，属资金安全事故面）。姓名经 sys_user 反查，任一命中即认定付给本人。
     */
    private void createClaimPayment(ExpBill claim, BigDecimal amount) {
        try {
            ExpBillPayment p = new ExpBillPayment();
            p.setTenantId(TenantContext.getTenantId());
            p.setBillId(claim.getId());
            p.setLineNo(1);
            String payeeName = claim.getPayeeName();
            boolean toEmployee = resolvePayToEmployee(claim, payeeName);
            p.setPayeeType(toEmployee ? "EMPLOYEE" : "SUPPLIER");
            // 付给往来单位时 payee_id 也留过空（只有一列名称文本）——同 createPaymentRecord，
            // 按单据上的采购订单/合同回填供应商 ID；报销单多半两者皆无，取不到即保持 NULL，不猜。
            p.setPayeeId(toEmployee ? claim.getApplyUserId() : voucherService.billSupplierIdOf(claim));
            p.setPayeeName(toEmployee
                    ? (claim.getApplyUserName() == null || claim.getApplyUserName().isBlank()
                            ? "员工" + claim.getApplyUserId() : claim.getApplyUserName())
                    : payeeName);
            // 收款账号取单据填写信息，可空（出纳在付款环节补全）
            p.setBankAccount(claim.getPayeeAccountNo());
            inheritPayMethodAndBank(claim, p); // 收款方式与开户行取自单据，未选缺省 BANK
            p.setCurrencyCode(claim.getCurrencyCode() == null ? "CNY" : claim.getCurrencyCode());
            p.setAmount(amount);
            p.setPayStatus("WAITING");
            p.setCreateTime(LocalDateTime.now());
            p.setProcInstId(claim.getProcInstId());
            p.setRemark("报销审批通过自动生成待付款记录（报销→付款转换链）");
            expBillPaymentMapper.insert(p);
            log.info("报销单 {} 审批通过，自动生成待付款记录 id={} 应付 {} 收款方[{}] payTarget={}",
                    claim.getBillNo(), p.getId(), amount, p.getPayeeName(), claim.getPayTarget());
        } catch (Exception ex) {
            // 付款记录生成失败不阻断审批收尾（状态已置 APPROVED），出纳可在付款工作台手工补建
            log.error("报销单 {} 自动生成待付款记录失败: {}", claim.getBillNo(), ex.getMessage(), ex);
        }
    }

    /**
     * F9 修复：判定本次付款是否付给申请人本人。
     *
     * <p>顺序铁律：<b>显式 {@code pay_target} 优先</b>，缺失才回退字符串比对——
     * 反过来会让"业务明确选了供应商"的单子被姓名字符串巧合翻成 EMPLOYEE。
     *
     * <p>回退比对认三种写法（任一命中即本人）：申请人<b>工号</b>（{@code apply_user_name} 的实际存储规则）、
     * 申请人<b>登录账号</b>、申请人<b>姓名</b>（经 sys_user 反查）。
     * 只比"姓名 vs 工号"时，恒为 false ⇒ 一律打成供应商款。
     * 收款方为空同样视为付给本人（员工垫付后打款给本人的既有语义）。
     */
    private boolean resolvePayToEmployee(ExpBill claim, String payeeName) {
        String target = claim.getPayTarget() == null ? null : claim.getPayTarget().trim().toUpperCase();
        if ("SUPPLIER".equals(target) || "CUSTOMER".equals(target)) {
            return false;
        }
        if ("EMPLOYEE".equals(target)) {
            return true;
        }
        if (payeeName == null || payeeName.isBlank()) {
            return true;
        }
        String v = payeeName.trim();
        if (v.equals(claim.getApplyUserName())) {
            return true;
        }
        if (claim.getApplyUserId() != null && v.equals(String.valueOf(claim.getApplyUserId()))) {
            return true;
        }
        try {
            if (claim.getApplyUserId() != null) {
                SysUser u = sysUserMapper.selectById(claim.getApplyUserId());
                if (u != null) {
                    if (v.equals(u.getNickName()) || v.equals(u.getUserName()) || v.equals(u.getEmpNo())) {
                        return true;
                    }
                }
            }
        } catch (Exception ex) {
            // 反查失败不得改变判定方向：保守按"不是本人"（＝走供应商核对通道），
            // 宁可让出纳在付款工作台核对一次，也不静默把供应商款打成员工款
            log.warn("收款方姓名反查失败（按非本人处理）billId={} payee={}: {}",
                    claim.getId(), payeeName, ex.getMessage());
        }
        return false;
    }

    /**
     * 无现金环节单据的预算消耗确认（替代原 {@code writeOffPayBillBudget} 的审批期核销）。
     *
     * <p>转换时点已从"审批通过钩子"后移到"实际付款成功"（{@code PayBatchService#settleBudgetOnPaid}），
     * 但有一类单据<b>永远不会产生待付款记录</b>：报销全额被借款冲抵（平账）或报销&lt;借款（只产生退款记录）。
     * 这类单据的钱已经在借款那一环节付出，若不在审批收尾时确认消耗，占用就会永久悬空、把额度吃死。
     * 故只在这一个分支保留即时核销，其余一律等付款。</p>
     *
     * <p><b>执行时机：必须挂到外层审批事务提交之后</b>。根因：
     * 本方法与同一条收尾链上的 {@code writeOffApplyOccupy}/{@code handleLoanClaimDiff} 同处外层事务，
     * 而那些步骤已经动过同一枚 {@code bg_item} 行；{@code settleOccupyOnPaid} 是
     * {@code REQUIRES_NEW}，要 {@code SELECT ... FOR UPDATE} 同一行 —— 外层未提交 ⇒ 内层<b>自锁</b>，
     * 每次等满 {@code innodb_lock_wait_timeout}(50s) 才超时。日志实证：
     * 23:55:41 收尾 → 23:56:31 撞锁① → 23:57:21 撞锁② → 23:58:12 放弃，
     * 3 次共耗约 2 分半，占用仍悬空 3,900。加重试在这里治不了病——锁住它的正是自己。
     * 挂 afterCommit 后外层一提交行锁即释放，内层立刻拿得到锁，正常一次成功。</p>
     *
     * <p>去重与失败规则与付款侧同一套：{@code settleOccupyOnPaid} 内部按 {@code bg_occupy}
     * 已核销流水判重；异常仅记录，不阻断审批收尾（与凭证生成失败同处理）。</p>
     */
    private void scheduleSettleBudgetOnNoCashLeg(ExpBill bill) {
        // 注册前把需要的数据抽成标量：bill 是事务内实体，提交后不保证仍可用（游离态/延迟加载）
        final Long billId = bill.getId();
        final String billNo = bill.getBillNo();
        final String billType = bill.getBillType();
        final Long companyId = bill.getCompanyId();
        final Long deptId = bill.getDeptId();
        final Integer periodYear = bill.getPeriodYear();
        // 租户上下文同样要在注册前抓住：MyBatis 租户拦截器靠 TenantContext 的 ThreadLocal 拼 tenant_id，
        // afterCommit 跑在同一线程但请求链随时可能清理，届时不显式恢复会查询不到数据。
        final Long tenantId = TenantContext.getTenantId();
        // 事务外（事件重放/单测直调）没有同步器可用时，退化为立即执行，行为与原先一致
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("报销单 {} 无事务同步上下文，预算消耗确认改为立即执行（D-F1R-06）", billNo);
            TenantContext.callAs(tenantId, () ->
                    doSettleBudgetOnNoCashLeg(billId, billNo, billType, companyId, deptId, periodYear));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                TenantContext.callAs(tenantId, () ->
                        doSettleBudgetOnNoCashLeg(billId, billNo, billType, companyId, deptId, periodYear));
            }
        });
        log.info("报销单 {} 无现金腿（平账/报销<借款退款），预算消耗确认已挂到审批事务提交后执行（D-F1R-06）", billNo);
    }

    /** {@link #scheduleSettleBudgetOnNoCashLeg} 的实际执行体（跑在事务提交后）。 */
    private void doSettleBudgetOnNoCashLeg(Long billId, String billNo, String billType,
                                           Long companyId, Long deptId, Integer periodYear) {
        try {
            List<BudgetService.SettleLine> lines = new ArrayList<>();
            for (ExpBillLine line : linesOf(billId)) {
                lines.add(new BudgetService.SettleLine(
                        line.getBudgetItemId(), line.getItemId(), line.getAmount()));
            }
            if (lines.isEmpty()) {
                return;
            }
            // 提交后执行已消除自锁，重试仅作兜底（偶发的其它长事务仍可能撞锁）。
            // 有界重试是安全的：settleOccupyOnPaid 自带 countWriteOffForBill>0 即跳过的去重校验
            // （BudgetService.java:1254），失败那趟整个事务回滚，不会补半次消耗。
            Exception last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    int done = budgetService.settleOccupyOnPaid(billId, billNo, billType,
                            companyId, deptId, periodYear, lines);
                    log.info("报销单 {} 无现金腿（平账/报销<借款退款），确认预算消耗 {} 行（第 {} 次尝试）",
                            billNo, done, attempt);
                    return;
                } catch (Exception ex) {
                    last = ex;
                    if (!isLockWait(ex) || attempt == 3) {
                        break;
                    }
                    // 退避给足。原 400ms×attempt 相对于 50s 的行锁等待等于没退，
                    // 且重试本身每次仍要等满一次 50s 超时才返回，纯属空耗审批线程。
                    long backoff = 1000L * attempt;
                    log.warn("报销单 {} 预算消耗确认撞行锁（第 {} 次），{}ms 后重试：{}",
                            billNo, attempt, backoff, ex.getMessage());
                    Backoff.pauseMillis(backoff);
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                }
            }
            log.error("报销单 {} 预算消耗确认失败（不影响审批收尾，请到 预算›占用 对该单点【核销】手工补）: {}",
                    billNo, last == null ? "unknown" : last.getMessage(), last);
        } catch (Exception ex) {
            log.error("报销单 {} 预算消耗确认失败（不影响审批收尾，请到 预算›占用 对该单点【核销】手工补）: {}", billNo, ex.getMessage(), ex);
        }
    }

    /** 行锁等待/死锁类异常（只有这一类值得重试，其余重试只会拖慢审批） */
    private static boolean isLockWait(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            String msg = c.getMessage();
            if (msg != null && (msg.contains("Lock wait timeout") || msg.contains("Deadlock found"))) {
                return true;
            }
            String cn = c.getClass().getName();
            if (cn.contains("TransactionRollbackException") || cn.contains("DeadlockLoserDataAccessException")
                    || cn.contains("CannotAcquireLockException")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 付款单挂 CIP（extJson.cipId）时生成 CIP 付款凭证并回写已付款金额。
     * 去重：VoucherService 内按 source_type=CIP_PAY + source_doc_id=billId 判重；全链路兜底不抛出。
     */
    private void generateCipPayVoucherIfLinked(ExpBill bill, BigDecimal payable) {
        try {
            if (bill.getExtJson() == null || bill.getExtJson().isBlank()) {
                return;
            }
            JsonNode root = OM.readTree(bill.getExtJson());
            JsonNode cipNode = root.get("cipId");
            if (cipNode == null || cipNode.isNull()) {
                return;
            }
            long cipId = cipNode.isNumber() ? cipNode.asLong()
                    : (cipNode.isTextual() && !cipNode.asText().isBlank() ? Long.parseLong(cipNode.asText()) : 0L);
            if (cipId <= 0) {
                return;
            }
            MdCip cip = mdCipMapper.selectById(cipId);
            if (cip == null) {
                log.warn("[CIP付款] 付款单 {} extJson.cipId={} 指向的在建工程不存在，跳过 CIP 凭证",
                        bill.getBillNo(), cipId);
                return;
            }
            voucherService.generateCipPaymentVoucher(cip, payable, bill.getId(), "CIP_PAY");
        } catch (Exception ex) {
            log.error("[CIP付款] 付款单 {} CIP 凭证处理失败（不影响审批收尾）: {}", bill.getBillNo(), ex.getMessage(), ex);
        }
    }

    private List<ExpBillLine> linesOf(Long billId) {
        return expBillLineMapper.selectList(new LambdaQueryWrapper<ExpBillLine>()
                .eq(ExpBillLine::getBillId, billId));
    }

    private void updateStatus(Long billId, String status) {
        ExpBill update = new ExpBill();
        update.setId(billId);
        update.setStatus(status);
        expBillMapper.updateById(update);
    }

    /**
     * 断点7：审批通过后重新查询单据，若状态为最终状态（APPROVED/PAID/CLOSED）则发布 BillTerminalEvent。
     * 重新查询是因为 handleApproved 中各 case 通过 updateById 更新数据库，
     * 内存中的 bill 对象状态未同步；且 EXP_CLAIM 可能被智能审单驳回（REJECTED），
     * 需要以数据库最新状态为准判断是否真正到达最终状态。
     */
    private void publishTerminalEventIfTerminal(Long billId) {
        ExpBill fresh = expBillMapper.selectById(billId);
        if (fresh == null) {
            return;
        }
        String status = fresh.getStatus();
        if ("APPROVED".equals(status) || "PAID".equals(status) || "CLOSED".equals(status)) {
            eventPublisher.publishEvent(new BillTerminalEvent(
                    fresh.getId(), fresh.getBillNo(), fresh.getTitle(), fresh.getBillType(),
                    fresh.getStatus(), fresh.getCompanyId(), fresh.getPeriodYear(),
                    fresh.getPeriodMonth(), fresh.getTenantId(), fresh.getImageStatus()));
            log.info("发布单据终态事件: billId={}, billNo={}, status={}", fresh.getId(), fresh.getBillNo(), fresh.getStatus());
        }
    }
}
