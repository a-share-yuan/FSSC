// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.budget.mapper.BgOccupyMapper;
import com.fssc.budget.service.BudgetService;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.entity.OpPayBatchDetail;
import com.fssc.sharing.mapper.OpPayBatchDetailMapper;
import com.fssc.sharing.mapper.OpPayBatchMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 付款批次审批收尾（P0-1 事件编排总线 + P0-2 凭证引擎接线）。
 * <p>
 * TR_PAY 审批通过 → op_pay_batch.status: DRAFT → APPROVED + 生成付款出纳凭证
 * （待出纳执行付款）
 * <p>
 * D-DEC-34（fail-closed 谓词）：本类只在批次处于 DRAFT/CONFIRMED/REJECTED（款项尚未发生）时改写状态；
 * PAID/FAILED/CLOSED/ARCHIVED/VOID 及未知态一律不回退——批次被写回 APPROVED 就等于"已付批次重新可打款"，
 * 是重复付款敞口的唯一入口。审批终态后到（先付后审）时状态不动，但凭证/预算转执行/实付入账仍会补齐
 * （D-DEC-199，见 {@link #closeAccountingForLateApproval}）。
 * <p>
 * 【修复 2026-09-14】资金付款（TR_PAY）审批通过 → 预算占用转执行（writeOff）：
 * <ul>
 *  <li>批次明细 → exp_bill_payment → 底层费用单据（CLAIM/PAY 等），按单据费用行的预算科目
 *      （显式 budget_item_id，缺省按 md_item_budget_map 费用类型→预算科目映射解析）逐行 writeOff，
 *      使预算执行数随付款审批变化；</li>
 *  <li>幂等/防重复核销：底层单据已存在任一执行核销流水（EXP_CLAIM 报销审批核销 /
 *      EXP_PAY 对公付款审批核销 / TR_PAY 本口径）则跳过——报销/对公付款审批通过时已完成
 *      占用转执行的，支付批次不再重复核销；仅对尚未核销的单据（历史在途、免审单据等）补核销；</li>
 *  <li>超预算硬控在提交占用节点生效（bg_scheme.control_type=HARD 时 occupy 直接拒绝提交）；
 *      支付节点为占用→执行的确认，不重复硬拦。</li>
 * </ul>
 */
@Slf4j
@Component("payBatchBizAction")
@RequiredArgsConstructor
public class PayBatchBizAction implements WfBizAction {

    private final OpPayBatchMapper opPayBatchMapper;
    private final OpPayBatchDetailMapper opPayBatchDetailMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final ExpBillMapper expBillMapper;
    private final ExpBillLineMapper expBillLineMapper;
    private final BudgetService budgetService;
    private final BgOccupyMapper bgOccupyMapper;
    private final VoucherService voucherService;
    /** 批次挂 CIP（source_doc_type=CIP）时自动生成在建工程付款凭证 */
    private final com.fssc.masterdata.mapper.MdCipMapper mdCipMapper;
    /** P4-3 审计扩面：审批终态状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;
    /** D-DEC-199：先付后审时补"实付入账"过账（独立事务，失败不影响批次收尾） */
    private final com.fssc.sharing.service.VoucherAutoPostService voucherAutoPostService;
    /** D-DEC-114③：读/补 op_voucher 与本审批流的对应关系（只补空值与留痕，不改写既有流号） */
    private final com.fssc.sharing.mapper.OpVoucherMapper opVoucherMapper;

    /**
     * 审批终态事件"可以改写批次状态"的前置状态白名单（D-DEC-34 fail-closed 口径）：
     * <ul>
     *  <li>DRAFT 草稿、CONFIRMED 已确认待审批、REJECTED 已驳回——三种状态下款项尚未发生，
     *      审批通过写 APPROVED、驳回写 REJECTED 都是正常流转；</li>
     *  <li>其余状态一律不可改写：PAID 已付款（回退＝二次资金指令敞口）、
     *      FAILED 付款失败（须由 TR_PAY_FAIL 失败处理通道核准，见 TreasuryBizAction.handlePayFail）、
     *      CLOSED/ARCHIVED/VOID 已了结，以及任何未知或为空的状态。</li>
     * </ul>
     * 注意：CONFIRMED 必须在白名单内，否则 DRAFT→CONFIRMED→APPROVED→PAID 主链路断在审批这一步。
     */
    private static final Set<String> PRE_SETTLEMENT_STATES = Set.of("DRAFT", "CONFIRMED", "REJECTED");

    @Override
    public Set<String> bizTypes() {
        return Set.of("TR_PAY");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long batchId = event.getBizId();
        if (batchId == null) return;
        OpPayBatch batch = opPayBatchMapper.selectById(batchId);
        if (batch == null) {
            log.warn("付款批次收尾: 不存在的批次 {}", batchId);
            return;
        }
        try {
            // P4-3 审计扩面：改状态前采集旧值
            String oldStatus = batch.getStatus();
            // 终态守卫（D-DEC-34 / 方案 F1-7，白名单 fail-closed）：只有"钱还没动"的批次才允许被审批终态改写状态。
            // 现网实证：批次 84 在 16:32:06 已 PAID，17:20:38 又被本类无条件写回 APPROVED——
            // 而 PayBatchService.execute 的白名单含 APPROVED（出纳正常执行通道），于是"已付批次"重新变成可付款，
            // 防超付预检按 payable 恰好放行＝二次资金指令。状态倒退是本缺陷的唯一入口，掐掉它即可。
            // 口径由黑名单改白名单的原因（D-DEC-34 复验）：黑名单 {PAID,CLOSED,ARCHIVED} 漏了
            //   FAILED（须由 TR_PAY_FAIL 失败处理通道核准回 APPROVED，见 TreasuryBizAction.handlePayFail，
            //   被 TR_PAY 直接覆写＝绕过失败复核再次打款）与 VOID，且任何新增/未知状态默认落在"可回退"一侧。
            //   资金域不能靠"没想到"的状态放行，故改为白名单之外一律不动（含状态为空的脏数据）。
            // 09-28 更新（D-DEC-287）：execute 前置已另行收窄为 {APPROVED, FAILED}（CONFIRMED 不再可打款），
            //   本守卫仍是"已付批次被重新打开"的主入口封堵；出纳正常通道＝审批通过写 APPROVED 后执行。
            if (!isPreSettlement(oldStatus)) {
                java.util.Map<String, Object> settledBefore = new java.util.LinkedHashMap<>();
                settledBefore.put("status", oldStatus);
                auditTrailService.recordUpdate(event.getBizType(), batchId, batch.getBatchNo(), "op_pay_batch",
                        String.valueOf(batchId), settledBefore, settledBefore,
                        "审批终态事件到达但批次状态 " + oldStatus + " 不在可改写白名单（DRAFT/CONFIRMED/REJECTED）内，"
                                + "拒绝回退状态（D-DEC-34 fail-closed 守卫），请人工核对是否重复收尾");
                log.warn("付款批次 {} 状态 {} 不在可改写白名单内，审批{}事件不回退状态（D-DEC-34 fail-closed）",
                        batch.getBatchNo(), oldStatus, Boolean.TRUE.equals(event.getApproved()) ? "通过" : "驳回");
                // D-DEC-199 回写半（先付后审的账务闭合）：状态不在白名单（PAID/APPROVED/FAILED/…）时一律不动状态，
                // 但"审批通过后本应发生的账务动作"不能跟着一起丢——12-31 X15 批量审批后 6 支批次
                // op_pay_batch 零回写，凭证/预算执行/实付入账全部悬空。以下各步幂等，重复到达不会重复入账。
                if (Boolean.TRUE.equals(event.getApproved())) {
                    closeAccountingForLateApproval(batch, event);
                }
                return;
            }
            OpPayBatch upd = new OpPayBatch();
            upd.setId(batchId);
            upd.setStatus(Boolean.TRUE.equals(event.getApproved()) ? "APPROVED" : "REJECTED");
            opPayBatchMapper.updateById(upd);
            java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            auditTrailService.recordUpdate(event.getBizType(), batchId, batch.getBatchNo(), "op_pay_batch",
                    String.valueOf(batchId), auditBefore, auditAfter,
                    (Boolean.TRUE.equals(event.getApproved()) ? "审批通过" : "审批驳回")
                            + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("付款批次 {} 审批{}: DRAFT → {}", batch.getBatchNo(),
                    Boolean.TRUE.equals(event.getApproved()) ? "通过" : "驳回",
                    Boolean.TRUE.equals(event.getApproved()) ? "APPROVED" : "REJECTED");
            // P0-2: 生成付款出纳凭证
            if (Boolean.TRUE.equals(event.getApproved())) {
                try {
                    // D-DEC-36：把本次审批实例号带进凭证，"提单→归档"链上凭证才可反查
                    Long voucherId = voucherService.generateTreasuryPayVoucher(batch, event.getProcInstId());
                    // D-DEC-114③：无论本批次出自己的证、还是被 P0-5 幂等跳过改由单据级凭证承载，
                    // 都把"本次 TR_PAY 实例 ↔ 承载凭证"这条对应关系落到数据上（见 linkVoucherToFlow）
                    linkVoucherToFlow(batch, event.getProcInstId(), voucherId);
                } catch (Exception vex) {
                    log.error("付款批次 {} 凭证生成失败（不影响状态更新）: {}", batch.getBatchNo(), vex.getMessage(), vex);
                }
                // 批次显式关联在建工程（source_doc_type=CIP 且 source_doc_id=md_cip.id）时，
                // 审批通过自动生成「借 在建工程(1604) / 贷 银行存款(1002)」凭证并回写 md_cip.paid_amount。
                // 幂等：source_type=CIP_PAY_BATCH + source_doc_id=batchId；失败仅记日志不阻断审批收尾。
                // 【D-DEC-104】审批通过不再做预算占用转已用：APPROVED 只代表"可以打款"，
                // 钱还没出去（execute 才是付款指令发出点）。原 writeOffBatchBudget 调用已下线，
                // 转消耗改由 PayBatchService.execute 打款成功后按底层单据确认（settleOccupyOnPaid）。
                generateCipPayVoucherIfLinked(batch);
            }
        } catch (Exception ex) {
            log.error("付款批次 {} 审批收尾失败: {}", batchId, ex.getMessage(), ex);
        }
    }

    /** 批次状态是否仍处于"可被审批终态改写"的白名单内（false＝fail-closed，不动状态） */
    private static boolean isPreSettlement(String status) {
        return status != null && PRE_SETTLEMENT_STATES.contains(status.trim().toUpperCase());
    }

    /**
     * D-DEC-199：先付后审（批次已 PAID 而 TR_PAY 审批终态后到）时的账务闭合。
     * <p>状态不回退，但"审批通过后本应发生的账务动作"必须补上，否则钱已付、账上无凭证、预算未转执行
     * （12-31 X15 批量审批后 6 支批次 op_pay_batch 零回写即此）。三步各自幂等，重复到达不会重复入账：
     * <ol>
     *  <li>批次出纳凭证：{@code generateTreasuryPayVoucher} 自带 P0-5 双层判重（批次级 + 单据级），
     *      重复到达只记日志不重复生成；APPROVED 态重放也走这里，等于给"上次制证失败"补一次重试；</li>
     *  <li>CIP 付款凭证（批次挂在建工程时）与预算占用转执行：{@code writeOffBatchBudget}
     *      按单据已核销流水判重，同一单据只确认执行一次；</li>
     *  <li>实付入账：仅 PAID（钱确实付出）才把底层单据名下 DRAFT 凭证过账
     *      （{@code VoucherAutoPostService} 独立事务、逐张隔离失败，绝不影响批次与付款记录）。
     *      D-DEC-036 后半：批次出纳凭证以 {@code bill_id=批次id} 落账，逐单据通道够不到，
     *      故本通道额外按批次 id 再过一次批次级 DRAFT。
     *      APPROVED＝尚未打款，过账留给 {@code PayBatchService.execute} 成功后的 P1-3 通道。
     *      D-DEC-093 消费腿后过账人取事件 {@code endOperUserId}（引擎线程无登录态，取不到才落 0），
     *      工作台仍可批量记账补账。</li>
     * </ol>
     * FAILED/CLOSED/ARCHIVED/VOID 等款项未定的状态一律不补（这些通道各有自己的收尾动作）。
     */
    private void closeAccountingForLateApproval(OpPayBatch batch, WfProcessEndedEvent event) {
        boolean paid = "PAID".equals(batch.getStatus());
        if (!paid && !"APPROVED".equals(batch.getStatus())) {
            // FAILED/CLOSED/ARCHIVED/VOID/未知态：款项是否付出尚未确定，不补凭证、不过账
            log.info("付款批次 {} 审批终态后到但批次状态为 {}（款项未定），不补账务凭证与过账",
                    batch.getBatchNo(), batch.getStatus());
            return;
        }
        try {
            Long voucherId = voucherService.generateTreasuryPayVoucher(batch, event.getProcInstId());
            // D-DEC-114③：先付后审的补账通道同样要落"审批流 ↔ 凭证"的对应关系（12-25 实测的正是这一路）
            linkVoucherToFlow(batch, event.getProcInstId(), voucherId);
        } catch (Exception vex) {
            log.error("付款批次 {} 审批终态后到补凭证失败（不影响批次状态）: {}",
                    batch.getBatchNo(), vex.getMessage(), vex);
        }
        generateCipPayVoucherIfLinked(batch);
        if (!paid) {
            // D-DEC-104：APPROVED＝钱还没出去，既不过账也不转预算消耗，
            // 占用转已用留待 execute 成功后由 PayBatchService 触发。
            return; // 实付入账留待 execute 成功后由 PayBatchService 触发
        }
        // 先付后审（批次已 PAID 而审批终态后到）：钱确实已经付出，这一路要补上预算占用转已用。
        writeOffBatchBudget(batch);
        for (Long billId : billIdsOfBatch(batch)) {
            try {
                // D-DEC-093 消费腿：事件已带终态操作人（endOperUserId），过账人不再传 null 冒充系统
                voucherAutoPostService.autoPostDraftByBill(billId, batch.getBatchNo(), event.getEndOperUserId());
            } catch (Exception ex) {
                log.warn("付款批次 {} 单据 {} 审批终态后到自动过账异常（可在凭证工作台批量记账补账）: {}",
                        batch.getBatchNo(), billId, ex.getMessage());
            }
        }
        // D-DEC-036 后半：逐单据通道够不到 bill_id = 批次id 的批次出纳凭证（TREASURY_PAY），
        // 先付后审补账时同样要把批次级 DRAFT 凭证带进实付入账。
        // D-DEC-288：走批次作用域通道，避免批次 id 与同号单据的单据级凭证互相串号过账。
        try {
            voucherAutoPostService.autoPostDraftByBatch(batch.getId(), batch.getBatchNo(), event.getEndOperUserId()); // D-DEC-093 消费腿
        } catch (Exception ex) {
            log.warn("付款批次 {} 审批终态后到自动过账批次凭证异常（可在凭证工作台批量记账补账）: {}",
                    batch.getBatchNo(), ex.getMessage());
        }
    }

    /**
     * D-DEC-114③：把「本次资金付款审批（TR_PAY）实例 ↔ 承载本次付款腿的凭证」落到数据上。
     * <p>缺陷实测（12-25:78，本库当前 7 支批次复现 5 支）：批次审批流与凭证之间零关联——
     * {@code generateTreasuryPayVoucher} 命中 P0-5 <b>跨类型</b>幂等时直接 {@code return 单据级凭证id}，
     * 批次级不出证，而那张单据级凭证的 {@code op_voucher.proc_inst_id} 是<b>源单据</b>的 EXPENSE_PAY 流
     * （D-DEC-36 语义，正确、不可改写），于是按本 TR_PAY 实例反查凭证恒 0 行，
     * {@code BillTraceChainController} 的「凭证」段（{@code source_doc_type=bizType} 或 {@code proc_inst_id IN 本单流程}）
     * 对批次域也取不到任何东西——「付款审批流 ↔ 资金凭证」在数据上完全无从互查。
     * <p>本方法<b>不改表结构、不改写任何既有流号</b>，只做两件加法，且各自幂等：
     * <ol>
     *  <li>批次级凭证（{@code source_type='TREASURY_PAY'} 且 {@code source_doc_id=批次id}）：
     *      只在 {@code proc_inst_id} <b>为空</b>时补上本次实例号（D-DEC-36 上线前出的历史证即此形态），
     *      已有值一律不动——补空是恢复本应有的关联，覆盖才是造假日；</li>
     *  <li>跨类型跳过（付款腿由单据级凭证承载）：批次表没有凭证列、凭证表没有批次列（属 DDL 裁决，
     *      见 {@code OpPayBatch} 类注释的注释态脚本），先把这条对应关系落进 {@code audit_trail}
     *      （bizType=TR_PAY + bizId=批次id ⇄ objType=op_voucher + objId=凭证id），审批流→凭证当场可查；
     *      同 D-DEC-34 白名单守卫的留痕做法（前后值同态、以说明文字承载结论）。</li>
     * </ol>
     * 全方法吞异常：收尾链路的账不能因为一条关联留痕而中断（同 {@link #generateCipPayVoucherIfLinked} 口径）。
     */
    private void linkVoucherToFlow(OpPayBatch batch, String procInstId, Long voucherId) {
        if (batch == null || batch.getId() == null || voucherId == null) {
            return;
        }
        try {
            com.fssc.sharing.entity.OpVoucher v = opVoucherMapper.selectById(voucherId);
            if (v == null) {
                log.warn("付款批次 {} 收尾凭证 id={} 查不到，跳过审批流↔凭证关联落点（D-DEC-114③）",
                        batch.getBatchNo(), voucherId);
                return;
            }
            boolean batchLevel = "TREASURY_PAY".equalsIgnoreCase(
                    v.getSourceType() == null ? "" : v.getSourceType().trim())
                    && batch.getId().equals(v.getSourceDocId());
            boolean flowKnown = procInstId != null && !procInstId.isBlank();
            if (batchLevel) {
                boolean voucherBlank = v.getProcInstId() == null || v.getProcInstId().isBlank();
                if (voucherBlank && flowKnown) {
                    // 只补空值：批次级出纳凭证的"来源流程"本就是这张批次的 TR_PAY 审批流
                    com.fssc.sharing.entity.OpVoucher upd = new com.fssc.sharing.entity.OpVoucher();
                    upd.setId(v.getId());
                    upd.setProcInstId(procInstId);
                    opVoucherMapper.updateById(upd);
                    log.info("付款批次 {} 凭证 {} 原缺流程号，已补本批次 TR_PAY 实例 {}（D-DEC-114③，只补空、不覆盖）",
                            batch.getBatchNo(), v.getVoucherNo(), procInstId);
                }
                return; // 已带号＝双向本就可查，无需重复留痕
            }
            // 单据级凭证承载：证上的流号属于源单据流程，不能挪；批次的付款腿↔本审批流改落审计流水
            java.util.Map<String, Object> link = new java.util.LinkedHashMap<>();
            link.put("voucherId", v.getId());
            link.put("voucherNo", v.getVoucherNo());
            link.put("carrierSourceType", v.getSourceType());
            link.put("carrierBillId", v.getBillId());
            link.put("carrierProcInstId", v.getProcInstId());
            link.put("payProcInstId", flowKnown ? procInstId : null);
            link.put("payBatchId", batch.getId());
            auditTrailService.recordUpdate("TR_PAY", batch.getId(), batch.getBatchNo(), "op_voucher",
                    String.valueOf(v.getId()), link, link,
                    "付款批次 " + batch.getBatchNo() + " 的付款腿由单据级凭证 " + v.getVoucherNo() + "（id=" + v.getId()
                            + "）承载，P0-5 跨类型幂等故批次级不再出证；该凭证的 proc_inst_id 属源单据流程、不可改写，"
                            + "故本 TR_PAY 实例↔凭证的关系落这条流水（D-DEC-114③）");
            log.info("付款批次 {} 付款腿由单据级凭证 id={} 承载，已落 TR_PAY 实例 {}↔凭证 的审计关联（D-DEC-114③）",
                    batch.getBatchNo(), v.getId(), flowKnown ? procInstId : "（事件未带实例号）");
        } catch (Exception ex) {
            log.warn("付款批次 {} 审批流↔凭证关联落点失败（不影响收尾，D-DEC-114③）: {}",
                    batch.getBatchNo(), ex.getMessage());
        }
    }

    /** 批次明细 → 付款记录 → 底层单据 ID 去重集合（与预算核销同一条回溯链） */
    private List<Long> billIdsOfBatch(OpPayBatch batch) {
        List<Long> billIds = new java.util.ArrayList<>();
        try {
            List<OpPayBatchDetail> details = opPayBatchDetailMapper.selectList(
                    new LambdaQueryWrapper<OpPayBatchDetail>()
                            .eq(OpPayBatchDetail::getBatchId, batch.getId())
                            .eq(OpPayBatchDetail::getDelFlag, 0));
            for (OpPayBatchDetail d : details) {
                if (d.getBillPaymentId() == null) {
                    continue;
                }
                ExpBillPayment pay = expBillPaymentMapper.selectById(d.getBillPaymentId());
                if (pay != null && pay.getBillId() != null && !billIds.contains(pay.getBillId())) {
                    billIds.add(pay.getBillId());
                }
            }
        } catch (Exception ex) {
            log.warn("付款批次 {} 回溯底层单据失败（跳过过账）: {}", batch.getBatchNo(), ex.getMessage());
        }
        return billIds;
    }

    /**
     * 批次挂 CIP（source_doc_type=CIP）时生成 CIP 付款凭证并回写已付款金额。
     * 与  成本归集（aggregateFromPayBatch）同触发点、互不影响；全链路兜底不抛出。
     */
    private void generateCipPayVoucherIfLinked(OpPayBatch batch) {
        try {
            if (batch.getSourceDocId() == null
                    || !"CIP".equalsIgnoreCase(batch.getSourceDocType() == null ? "" : batch.getSourceDocType().trim())) {
                return;
            }
            com.fssc.masterdata.entity.MdCip cip = mdCipMapper.selectById(batch.getSourceDocId());
            if (cip == null) {
                log.warn("[CIP付款] 付款批次 {} sourceDocId={} 指向的在建工程不存在，跳过 CIP 凭证",
                        batch.getBatchNo(), batch.getSourceDocId());
                return;
            }
            voucherService.generateCipPaymentVoucher(cip, batch.getTotalAmount(), batch.getId(), "CIP_PAY_BATCH");
        } catch (Exception ex) {
            log.error("[CIP付款] 付款批次 {} CIP 凭证处理失败（不影响审批收尾）: {}", batch.getBatchNo(), ex.getMessage(), ex);
        }
    }

    /**
     * 批次审批通过 → 逐明细回溯底层单据做预算执行核销（writeOff）。
     * 明细无关联付款记录（独立资金付款）或单据/预算科目无法解析时记日志跳过，不阻断审批收尾。
     */
    private void writeOffBatchBudget(OpPayBatch batch) {
        try {
            List<OpPayBatchDetail> details = opPayBatchDetailMapper.selectList(
                    new LambdaQueryWrapper<OpPayBatchDetail>()
                            .eq(OpPayBatchDetail::getBatchId, batch.getId())
                            .eq(OpPayBatchDetail::getDelFlag, 0));
            for (OpPayBatchDetail d : details) {
                if (d.getBillPaymentId() == null) {
                    log.info("付款批次 {} 明细 {} 无关联业务付款记录（独立资金付款），无单据预算科目可核销，跳过",
                            batch.getBatchNo(), d.getId());
                    continue;
                }
                ExpBillPayment pay = expBillPaymentMapper.selectById(d.getBillPaymentId());
                if (pay == null || pay.getBillId() == null) {
                    continue;
                }
                ExpBill bill = expBillMapper.selectById(pay.getBillId());
                if (bill == null) {
                    continue;
                }
                // 幂等/防重复：该单据已发生过执行核销（报销审批 EXP_CLAIM / 付款审批 EXP_PAY /
                // 其他批次 TR_PAY）则跳过，同一单据的预算只确认执行一次
                if (bgOccupyMapper.countWriteOffForBill(bill.getId()) > 0) {
                    log.info("付款批次 {} 明细 {} 底层单据 {} 已发生过预算执行核销，跳过（幂等）",
                            batch.getBatchNo(), d.getId(), bill.getBillNo());
                    continue;
                }
                boolean any = false;
                List<ExpBillLine> lines = expBillLineMapper.selectList(
                        new LambdaQueryWrapper<ExpBillLine>().eq(ExpBillLine::getBillId, bill.getId()));
                for (ExpBillLine line : lines) {
                    if (line.getAmount() == null) {
                        continue;
                    }
                    Long budgetItemId = budgetService.resolveOccupyItem(line.getBudgetItemId(), line.getItemId(),
                            bill.getCompanyId(), bill.getDeptId(), bill.getPeriodYear(), false);
                    if (budgetItemId == null) {
                        log.warn("付款批次 {} 底层单据 {} 行 {} 未解析到预算科目，跳过执行核销",
                                batch.getBatchNo(), bill.getBillNo(), line.getId());
                        continue;
                    }
                    budgetService.writeOff(budgetItemId, line.getAmount(),
                            "TR_PAY", bill.getId(), batch.getBatchNo());
                    any = true;
                }
                if (any) {
                    log.info("付款批次 {} 支付审批通过，底层单据 {} 预算占用已转执行（TR_PAY writeOff，付款金额 {}）",
                            batch.getBatchNo(), bill.getBillNo(), d.getAmount());
                }
            }
        } catch (Exception ex) {
            log.error("付款批次 {} 预算执行核销失败（不影响审批收尾）: {}", batch.getBatchNo(), ex.getMessage(), ex);
        }
    }

}
