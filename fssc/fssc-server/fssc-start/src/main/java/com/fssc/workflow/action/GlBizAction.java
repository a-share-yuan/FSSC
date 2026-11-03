// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.budget.service.BudgetService;
import com.fssc.glex.entity.GlInterOffset;
import com.fssc.glex.entity.GlJournalEntry;
import com.fssc.glex.entity.GlSalaryRun;
import com.fssc.glex.entity.GlTaxProvision;
import com.fssc.glex.entity.TaxRefund;
import com.fssc.glex.mapper.GlInterOffsetMapper;
import com.fssc.glex.mapper.GlJournalEntryMapper;
import com.fssc.glex.mapper.GlSalaryRunMapper;
import com.fssc.glex.mapper.GlTaxProvisionMapper;
import com.fssc.glex.mapper.TaxRefundMapper;
import com.fssc.glex.service.GlMonthCloseService;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.service.VoucherService;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 总账域流程收尾（事件编排总线 + 凭证引擎接线）。
 * <p>
 * 覆盖 GL_* 十四类总账流程：
 * <ul>
 *   <li><b>总账记账族</b>（gl_journal_entry）：GL_ACCRUAL 费用预提 / GL_DEFER 费用待摊 /
 *       GL_MANUAL_VOUCHER 手工凭证 / GL_OTHER_RECEIPT 其他收款。
 *       审批通过 → 依记账单自身借贷科目生成 op_voucher（零臆测科目映射）。</li>
 *   <li><b>税金族</b>（gl_tax_provision）：GL_TAX_PROV 计提（借 6403/6801 贷 2221）/
 *       GL_TAX_PAY 缴纳（借 2221 贷 1002）。</li>
 *   <li><b>薪酬族</b>（gl_salary_run）：GL_SALARY_PROV 计提（借 6602 贷 2211）/
 *       GL_SALARY_PAY 发放（借 2211 贷 1002）。</li>
 *   <li><b>内部往来抵消</b>（gl_inter_offset）：GL_OFFSET（借 2202 贷 1122）。</li>
 *   <li><b>税务退税</b>（tax_refund）：GL_TAX_REFUND 核准（借 1221 其他应收款 贷 2221 应交税费，
 *       按权责发生制确认应收退税款）；状态 APPLYING→APPROVED/REJECTED。</li>
 *   <li><b>月结</b>（gl_month_close）：GL_MONTH_CLOSE。<b>起本类是关账时点的唯一回写口</b>：
 *       发起只建在途单（RUNNING）、期间保持开账，末节点通过才 CLOSED＋md_period 联动关账，
 *       驳回/撤回落 REJECTED（判定逻辑在 GlMonthCloseService.applyApprovalOutcome）。</li>
 *   <li><b>其余总账业务</b>（GL_MERGE 合并报表 / GL_SHARED_MASTER 主数据 /
 *       GL_FX_REVAL 汇兑重估）：业务实体在发起前已置业务态，
 *       审批即为后置确认，收尾仅记录闭环（避免臆测借贷科目污染总账）。</li>
 * </ul>
 * 上述税金/薪酬/内部往来/退税凭证依赖科目字典已补录
 * （见 db/sql/V1.3.22_gl_std_subjects.sql、db/sql/V1.3.23_gl_tax_refund_flow.sql）。
 */
@Slf4j
@Component("glBizAction")
@RequiredArgsConstructor
public class GlBizAction implements WfBizAction {

    /** 总账记账族（依记账单自身科目生成凭证）；含盘点差异结转档 */
    private static final Set<String> JOURNAL_TYPES =
            Set.of("GL_ACCRUAL", "GL_DEFER", "GL_MANUAL_VOUCHER", "GL_OTHER_RECEIPT", "GL_CHECK_DIFF");

    /** 税金族（gl_tax_provision） */
    private static final Set<String> TAX_TYPES = Set.of("GL_TAX_PROV", "GL_TAX_PAY");

    /** 薪酬族（gl_salary_run） */
    private static final Set<String> SALARY_TYPES = Set.of("GL_SALARY_PROV", "GL_SALARY_PAY");

    private final GlJournalEntryMapper glJournalEntryMapper;
    private final GlTaxProvisionMapper glTaxProvisionMapper;
    private final GlSalaryRunMapper glSalaryRunMapper;
    private final GlInterOffsetMapper glInterOffsetMapper;
    private final TaxRefundMapper taxRefundMapper;
    private final VoucherService voucherService;
    // 凭证号回写改由 VoucherService.readVoucherCommitted（独立事务）读取，
    // 原 opVoucherMapper.selectById 直读受父事务 REPEATABLE READ 快照隔离，读不到出证环节刚提交的凭证行
    /** 审计扩面：审批最终状态状态跃迁记录 */
    private final AuditTrailService auditTrailService;
    /** 月结「末节点通过才关账」的回写落点（业务判定全在 GlMonthCloseService，本类只做路由） */
    private final GlMonthCloseService glMonthCloseService;
    /** 薪酬计提占用／发放转已用／驳回释放的预算侧入口 */
    private final BudgetService budgetService;

    @Override
    public Set<String> bizTypes() {
        return Set.of(
                // 总账记账族（GL_CHECK_DIFF=盘点差异结转档）
                "GL_ACCRUAL", "GL_DEFER", "GL_MANUAL_VOUCHER", "GL_OTHER_RECEIPT", "GL_CHECK_DIFF",
                // 税金族 / 薪酬族 / 内部往来抵消 / 税务退税
                "GL_TAX_PROV", "GL_TAX_PAY", "GL_SALARY_PROV", "GL_SALARY_PAY", "GL_OFFSET",
                "GL_TAX_REFUND",
                // 其余总账业务（收尾确认闭环）
                "GL_MERGE", "GL_MONTH_CLOSE", "GL_SHARED_MASTER", "GL_FX_REVAL",
                // 遗留流程定义（wf_business_flow 中仅有定义、无代码发起；_PROV/_PAY 拆分前的旧模板）
                // 注册为兜底以免将来被发起时静默无收尾
                "GL_TAX", "GL_SALARY");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        String bizType = event.getBizType();
        boolean approved = Boolean.TRUE.equals(event.getApproved());
        Long id = event.getBizId();
        if (JOURNAL_TYPES.contains(bizType)) {
            handleJournal(event, approved);
            return;
        }
        if (TAX_TYPES.contains(bizType)) {
            handleTax(event, approved);
            return;
        }
        if (SALARY_TYPES.contains(bizType)) {
            handleSalary(event, approved);
            return;
        }
        if ("GL_OFFSET".equals(bizType)) {
            handleOffset(event, approved);
            return;
        }
        if ("GL_TAX_REFUND".equals(bizType)) {
            handleTaxRefund(event, approved);
            return;
        }
        if ("GL_MONTH_CLOSE".equals(bizType)) {
            handleMonthClose(event, approved);
            return;
        }
        // 其余总账业务：实体已在发起前置业务态，收尾仅记录闭环（不臆测科目、不动状态）
        log.info("总账域 {} 流程结束（bizId={}）：approved={}，业务实体已前置置态，收尾确认闭环",
                bizType, id, approved);
    }

    /**
     * 月结（GL_MONTH_CLOSE）审批最终状态回写 ——。
     * <p>
     * 发起审批时月结单只是 RUNNING、md_period 依旧 NORMAL；真正的「关账」必须等最后一个节点通过。
     * 判定与写库都在 {@code GlMonthCloseService.applyApprovalOutcome}，本方法只做路由，
     * 并且回写失败要响亮记 error——「审批通过却没关账」绝不能静默。
     */
    private void handleMonthClose(WfProcessEndedEvent event, boolean approved) {
        try {
            glMonthCloseService.applyApprovalOutcome(event.getBizId(), approved,
                    event.getComment(), event.getEndType(), event.getProcInstId(), event.getEndOperUserId());
        } catch (Exception ex) {
            log.error("月结审批回写失败 closeId={} approved={} procInst={}：gl_month_close 与 md_period 将不一致，" +
                    "需人工核对期间状态后重新月结（必要时先反结账）",
                    event.getBizId(), approved, event.getProcInstId(), ex);
        }
    }

    /** 总账记账族：审批通过 → 生成 op_voucher（借/贷取记账单自身科目）→ 置 POSTED 并回写真实凭证号 */
    private void handleJournal(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) {
            return;
        }
        GlJournalEntry e = glJournalEntryMapper.selectById(id);
        if (e == null) {
            log.warn("总账记账收尾: 不存在的记账单 {}", id);
            return;
        }
        String prev = e.getStatus();
        if (!approved) {
            // 发起时已不再预置 POSTED，非通过最终状态须显性落到"不可入账"，界面才看得出单子死在哪一步。
            // 撤回（申请人自己收回）与驳回（审批人否决）同落 REJECTED —— 该态在期间去重校验
            // （APPROVING/APPROVED/POSTED）之外，修正后可重新制单；但日志与审计必须分开写，
            // 把"我撤回了"记成"被驳回了"会让审批轨迹失真。
            boolean withdrawn = event.withdrawn();
            GlJournalEntry rej = new GlJournalEntry();
            rej.setId(id);
            rej.setStatus("REJECTED");
            // 预提/待摊被驳回/撤回 → 按「预算冲回」等额释放，额度立刻回到可申请池。
            // 放在状态回写之前、同一次 update 里带上状态位，避免「单据已 REJECTED、预算还占着」。
            if (("ACCRUAL".equals(e.getEntryType()) || "DEFER".equals(e.getEntryType()))
                    && !"RELEASED".equals(e.getBudgetOccupyStatus())) {
                try {
                    // 用独立事务：REQUIRED 版抛异常会把当前事务标 rollback-only，
                    // 即使此处 catch 住，外层收尾提交时仍会 UnexpectedRollbackException
                    int released = budgetService.releaseAccrualOccupyIsolated(event.getBizType(), id, e.getEntryNo());
                    if (released > 0 || "OCCUPIED".equals(e.getBudgetOccupyStatus())) {
                        rej.setBudgetOccupyStatus("RELEASED");
                    }
                } catch (Exception ex) {
                    // 流程已结束，预算侧异常不能把这次收尾一起带回滚——留 error 交人工补偿
                    log.error("预提/待摊 {} 驳回后释放预算占用失败（单据仍置 REJECTED，预算额度需人工核对）：{}",
                            e.getEntryNo(), ex.getMessage(), ex);
                }
            }
            glJournalEntryMapper.updateById(rej);
            recordStatusAudit(event, id, e.getEntryNo(), "gl_journal_entry", prev, rej.getStatus(),
                    (withdrawn ? "申请人撤回，记账单退回可重办" : "审批驳回，记账单退回可重办") + withComment(event));
            log.info("总账记账 {}（{}）审批{} → REJECTED", e.getEntryNo(), event.getBizType(),
                    withdrawn ? "被撤回" : "被驳回");
            return;
        }
        Long voucherId = null;
        try {
            voucherId = voucherService.generateGlJournalVoucher(e, event.getBizType());
        } catch (Exception ex) {
            log.error("总账记账 {}（{}）凭证生成失败（不影响流程闭环）: {}",
                    e.getEntryNo(), event.getBizType(), ex.getMessage(), ex);
        }
        // 办结才记账；凭证号取凭证对象上的真号回写，两轨号并存＋不可回查的问题在此消解。
        // 出证失败＝没记成账，停在 APPROVED（已审批·未记账），不冒充 POSTED。
        GlJournalEntry upd = new GlJournalEntry();
        upd.setId(id);
        upd.setStatus(voucherId == null ? "APPROVED" : "POSTED");
        String postedFlag = "";
        if (voucherId != null) {
            OpVoucher v = voucherService.readVoucherCommitted(voucherId);
            if (v != null && v.getVoucherNo() != null && !v.getVoucherNo().isBlank()) {
                upd.setVoucherNo(v.getVoucherNo());
            }
            // 凭证默认落 DRAFT，过账由记账岗在凭证工作台完成（制审分离的人工控制点，
            // 月结侧 GlMonthCloseService.applyApprovalOutcome 会以"存在未过账凭证"拦关账）。
            // 记录须写明账面此刻有没有这笔数，否则单据 POSTED 会被读成"总账里已经有了"。
            postedFlag = v == null ? "（凭证对象不可读）"
                    : "DRAFT".equals(v.getStatus()) ? "·凭证待过账" : "·凭证已过账";
        }
        glJournalEntryMapper.updateById(upd);
        recordStatusAudit(event, id, e.getEntryNo(), "gl_journal_entry", prev, upd.getStatus(),
                (voucherId == null ? "审批通过，但出证失败·未入账"
                        : "审批通过·已记账" + postedFlag) + withComment(event));
        log.info("总账记账 {}（{}）审批通过 → {}{}{}", e.getEntryNo(), event.getBizType(), upd.getStatus(),
                upd.getVoucherNo() == null ? (voucherId == null ? "（未出证）" : "") : " 凭证号=" + upd.getVoucherNo(),
                voucherId == null ? "" : postedFlag);
    }

    /**
     * 税金族：末节点通过 → 生成 op_voucher（计提/缴纳两套分录）→ 回写最终状态与真实凭证号。
     *
     * <p>（与 /291/292 同一回写机制）：计提记录在 {@code GlTaxProvisionService.provision}
     * 的 insert 前就硬置 POSTED 并预造 {@code GLTX…} 号，流程随后才起 —— 提交瞬间即最终状态、审批链零回写。
     * 该文件属 tax 计提 job（他人独占面），本收尾不动它的置位语句，只把「状态置位」与「制证」解耦，
     * 在末节点回写钩子上做实最终状态：
     * <ul>
     *   <li>计提链通过且出证成功 → POSTED ＋ 用凭证对象上的真号（JZ…）替换预造号（两轨号并存问题在此消解）；</li>
     *   <li>计提链通过但出证失败 → 退回 APPROVED（已审批·未入账），不冒充 POSTED；</li>
     *   <li>计提链驳回/撤回 → REJECTED（原逻辑保留，撤回与驳回在日志与审计里分开表述）；</li>
     *   <li>缴纳链通过 → PAID（已缴纳）并出缴纳凭证，计提凭证号列不动；缴纳链驳回/撤回 → 回退计提链办结态
     *       （按计提凭证是否存在取 POSTED／APPROVED），已入账的计提不因缴款被驳而被抹成 REJECTED。</li>
     * </ul>
     * gl_tax_provision 无 approve_time/audit_time/proc_inst_id 列，审批时间与实例号无法回写（DDL 待确认项）。
     */
    private void handleTax(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) {
            return;
        }
        GlTaxProvision e = glTaxProvisionMapper.selectById(id);
        if (e == null) {
            log.warn("税金收尾: 不存在的计提记录 {}", id);
            return;
        }
        boolean payChain = "GL_TAX_PAY".equals(event.getBizType());
        String prev = e.getStatus();
        boolean withdrawn = event.withdrawn();
        if (!approved) {
            // 驳回/撤回办结：落前置态，不留"无凭证却顶着入账态"
            GlTaxProvision rej = new GlTaxProvision();
            rej.setId(id);
            if (payChain) {
                // 缴款被驳/被撤回：计提账仍在，回退到计提链的办结态。中间态无处可存（表只有一列 status），
                // 故按"计提凭证在不在"判定回退目标——有凭证＝已入账 POSTED，无凭证＝办结未出证 APPROVED；
                // 一律写 POSTED 会把"计提其实没出证"的税金显示成已入账。
                rej.setStatus(voucherService.existingVoucherIdOf("GL_TAX_PROV", id) == null ? "APPROVED" : "POSTED");
                glTaxProvisionMapper.updateById(rej);
                recordTaxAudit(event, id, e.getProvisionNo(), prev, rej.getStatus(), false);
                log.info("税金 {}（GL_TAX_PAY）缴款审批{}，计提状态回退保持 {}",
                        e.getProvisionNo(), withdrawn ? "被撤回" : "被驳回", rej.getStatus());
                return;
            }
            rej.setStatus("REJECTED");
            glTaxProvisionMapper.updateById(rej);
            recordTaxAudit(event, id, e.getProvisionNo(), prev, rej.getStatus(), false);
            log.info("税金计提 {}（{}）审批{}，状态 {} → REJECTED", e.getProvisionNo(), event.getBizType(),
                    withdrawn ? "被撤回" : "被驳回", prev);
            return;
        }
        Long voucherId = null;
        try {
            voucherId = voucherService.generateTaxVoucher(e, event.getBizType());
        } catch (Exception ex) {
            log.error("税金 {}（{}）凭证生成失败（不影响流程闭环）: {}",
                    e.getProvisionNo(), event.getBizType(), ex.getMessage(), ex);
        }
        if (payChain) {
            if (voucherId == null) {
                log.warn("税金 {}（GL_TAX_PAY）缴款凭证未生成，状态保持 {}（不冒充已缴纳）", e.getProvisionNo(), prev);
                return;
            }
            GlTaxProvision upd = new GlTaxProvision();
            upd.setId(id);
            upd.setStatus("PAID");
            glTaxProvisionMapper.updateById(upd);
            recordTaxAudit(event, id, e.getProvisionNo(), prev, upd.getStatus(), true);
            OpVoucher v = voucherService.readVoucherCommitted(voucherId);
            String payVoucherNo = v == null || v.getVoucherNo() == null ? String.valueOf(voucherId) : v.getVoucherNo();
            log.info("税金 {}（GL_TAX_PAY）缴款审批通过 → PAID，缴纳凭证 {}；计提凭证号列保持 {}",
                    e.getProvisionNo(), payVoucherNo, e.getVoucherNo());
            return;
        }
        // 计提链：办结才入账；出证失败＝没记成账，停在 APPROVED
        GlTaxProvision upd = new GlTaxProvision();
        upd.setId(id);
        upd.setStatus(voucherId == null ? "APPROVED" : "POSTED");
        if (voucherId != null) {
            OpVoucher v = voucherService.readVoucherCommitted(voucherId);
            if (v != null && v.getVoucherNo() != null && !v.getVoucherNo().isBlank()) {
                upd.setVoucherNo(v.getVoucherNo());
            }
        }
        glTaxProvisionMapper.updateById(upd);
        recordTaxAudit(event, id, e.getProvisionNo(), prev, upd.getStatus(), true);
        log.info("税金计提 {}（{}）审批通过 → {}{}", e.getProvisionNo(), event.getBizType(), upd.getStatus(),
                upd.getVoucherNo() == null
                        ? (voucherId == null ? "（未出证）" : "（沿用原号 " + e.getVoucherNo() + "）")
                        : " 凭证号=" + upd.getVoucherNo());
    }

    /** 审计扩面：税金族状态跃迁记录（变更前/变更后只带 status，不整实体 JSON） */
    private void recordTaxAudit(WfProcessEndedEvent event, Long id, String bizNo, String before, String after, boolean approved) {
        recordStatusAudit(event, id, bizNo, "gl_tax_provision", before, after,
                (approved ? "审批通过" : "审批驳回") + withComment(event));
    }

    /**
     * 审计扩面：审批最终状态引起的单据状态跃迁统一记录（总账记账族/薪酬族原先只有税金有，
     * 三族同走一个回写钩子却记录不齐，事后核查只能看出"变过"看不出"被谁审批变成这样"）。
     *
     * @param before 原先状态（取事件到达时读到的单据原值）
     * @param after 改后状态
     * @param note 跃迁事由（含撤回/驳回/通过·出证结果，审批意见原文附后）
     */
    private void recordStatusAudit(WfProcessEndedEvent event, Long id, String bizNo, String table,
                                   String before, String after, String note) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("status", before);
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("status", after);
        auditTrailService.recordUpdate(event.getBizType(), id, bizNo, table, String.valueOf(id), b, a, note);
    }

    /** 记录附加审批意见（可空；与税金族既有记录规则一致） */
    private static String withComment(WfProcessEndedEvent event) {
        String c = event.getComment();
        return c == null || c.isBlank() ? "" : "：" + c;
    }

    /**
     * 薪酬族：末节点通过 → 生成 op_voucher（计提/发放两套分录）→ 回写最终状态。
     *
     * <p>（同一回写机制）：计提写入数据库即 payStatus=PAID、发放提交瞬间又置 PAID，
     * 两链共 8 节审批对单据零回写。现规则：
     * <ul>
     *   <li>计提链（GL_SALARY_PROV）通过且出证成功 → POSTED（已计提入账·未发放）；出证失败 → APPROVED；驳回 → REJECTED；</li>
     *   <li>发放链（GL_SALARY_PAY）通过且出证成功 → PAID，并成对补出代扣缴纳凭证（
     *       2211 的应发/实发差额自此有出口）；出证失败 → 状态原地不动（不冒充已付款）；
     *       驳回/撤回 → 回退计提链办结态（按计提凭证是否存在取 POSTED／APPROVED）。
     *       两链每次跃迁均落 状态审计（表无 approve_time/proc_inst_id 列，时间与实例号只能看审计与流程史）。</li>
     * </ul>
     * <p>硬约束：gl_salary_run 一行同时承载计提与发放两条链（无 proc_inst_id、无 voucher_no、
     * 无第二状态列），两条链在单据上只能各落一个状态值，中间态会互相覆盖 —— 拆行/补列属 DDL 待确认，
     * 本回写按"发放是计提的下一步"的既有语义串行取态。
     */
    private void handleSalary(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) {
            return;
        }
        GlSalaryRun e = glSalaryRunMapper.selectById(id);
        if (e == null) {
            log.warn("薪酬收尾: 不存在的发放批次 {}", id);
            return;
        }
        boolean payChain = "GL_SALARY_PAY".equals(event.getBizType());
        String prev = e.getPayStatus();
        boolean withdrawn = event.withdrawn();
        if (!approved) {
            GlSalaryRun rej = new GlSalaryRun();
            rej.setId(id);
            if (payChain) {
                // 发放被驳/被撤回：计提账仍在，回退到计提链的办结态，可修正后重新发起发放；
                // 把已计提批次抹成 REJECTED 会让计提凭证在单据侧"凭空失效"。
                // 回退目标按"计提凭证在不在"判定：有＝POSTED（已入账），无＝APPROVED（办结未出证）。
                String back = voucherService.existingVoucherIdOf("GL_SALARY_PROV", id) == null ? "APPROVED" : "POSTED";
                rej.setPayStatus("REJECTED".equals(prev) ? prev : back);
                glSalaryRunMapper.updateById(rej);
                recordStatusAudit(event, id, e.getRunNo(), "gl_salary_run", prev, rej.getPayStatus(),
                        (withdrawn ? "发放审批被申请人撤回，回退计提办结态" : "发放审批被驳回，回退计提办结态")
                                + withComment(event));
                log.info("薪酬批次 {}（GL_SALARY_PAY）发放审批{}，payStatus {} → {}（计提账保留）",
                        e.getRunNo(), withdrawn ? "被撤回" : "被驳回", prev, rej.getPayStatus());
                return;
            }
            rej.setPayStatus("REJECTED");
            glSalaryRunMapper.updateById(rej);
            recordStatusAudit(event, id, e.getRunNo(), "gl_salary_run", prev, rej.getPayStatus(),
                    (withdrawn ? "薪酬计提审批被申请人撤回，批次退回可重办" : "薪酬计提审批被驳回，批次退回可重办")
                            + withComment(event));
            // 计提批次作废 → 等额释放它在预算上占的额度（否则被驳的批次长期挂着可用额度）
            releaseSalaryBudgetQuietly(id, e.getRunNo());
            log.info("薪酬批次 {}（{}）审批{}，payStatus {} → REJECTED", e.getRunNo(), event.getBizType(),
                    withdrawn ? "被撤回" : "被驳回", prev);
            return;
        }
        Long voucherId = null;
        try {
            voucherId = voucherService.generateSalaryVoucher(e, event.getBizType());
        } catch (Exception ex) {
            log.error("薪酬批次 {}（{}）凭证生成失败（不影响流程闭环）: {}",
                    e.getRunNo(), event.getBizType(), ex.getMessage(), ex);
        }
        if (payChain) {
            if (voucherId == null) {
                log.warn("薪酬批次 {}（GL_SALARY_PAY）发放凭证未生成，payStatus 保持 {}（不冒充已付款）",
                        e.getRunNo(), prev);
                return;
            }
            // 代扣（个税 + 代扣社保 + 其他扣除）承接凭证，缺它 2211 借贷差额永久挂账
            Long withholdId = null;
            try {
                withholdId = voucherService.generateSalaryWithholdingVoucher(e);
            } catch (Exception ex) {
                log.error("薪酬批次 {} 代扣缴纳凭证生成失败（不影响发放闭环）: {}；GL 域暂无制证失败补生成入口" +
                        "（结果视图仅覆盖费用族），需人工按 source_doc_id={} 复核后重跑发放办结",
                        e.getRunNo(), ex.getMessage(), id, ex);
            }
            GlSalaryRun upd = new GlSalaryRun();
            upd.setId(id);
            upd.setPayStatus("PAID");
            glSalaryRunMapper.updateById(upd);
            recordStatusAudit(event, id, e.getRunNo(), "gl_salary_run", prev, upd.getPayStatus(),
                    "发放审批通过·已付款" + (withholdId == null ? "（代扣凭证未出）" : "，代扣凭证 id=" + withholdId)
                            + withComment(event));
            OpVoucher v = voucherService.readVoucherCommitted(voucherId);
            String withholdDesc = withholdId == null
                    ? "无代扣金额或代扣凭证未出（代扣部分仍挂 2211，须补出证）"
                    : "代扣凭证 id=" + withholdId;
            log.info("薪酬批次 {}（GL_SALARY_PAY）审批通过 → PAID，发放凭证 {}；{}",
                    e.getRunNo(), v == null ? String.valueOf(voucherId) : v.getVoucherNo(), withholdDesc);
            // 薪酬的预算「占用 → 已用」挂在这个时点——工资真发出去了（发放凭证已出、状态 PAID）
            // 才确认消耗，计提链通过（POSTED）时只保留占用。时点规则与 一致。
            settleSalaryBudgetQuietly(id, e.getRunNo());
            return;
        }
        if (voucherId == null) {
            GlSalaryRun upd = new GlSalaryRun();
            upd.setId(id);
            upd.setPayStatus("APPROVED");
            glSalaryRunMapper.updateById(upd);
            recordStatusAudit(event, id, e.getRunNo(), "gl_salary_run", prev, upd.getPayStatus(),
                    "计提审批通过，但出证失败·未入账" + withComment(event));
            log.warn("薪酬批次 {}（{}）审批通过但计提凭证未生成 → APPROVED（已审批·未入账），不冒充 POSTED",
                    e.getRunNo(), event.getBizType());
            return;
        }
        GlSalaryRun upd = new GlSalaryRun();
        upd.setId(id);
        upd.setPayStatus("POSTED");
        glSalaryRunMapper.updateById(upd);
        recordStatusAudit(event, id, e.getRunNo(), "gl_salary_run", prev, upd.getPayStatus(),
                "计提审批通过·已入账" + withComment(event));
        // 计提通过 → 预算侧仍只是占用（OCCUPIED），本步骤刻意不做消耗确认：
        // 账上认了成本、钱却还没出去，转已用就是 要修的旧规则。
        OpVoucher v = voucherService.readVoucherCommitted(voucherId);
        String provVoucherNo = v == null || v.getVoucherNo() == null ? String.valueOf(voucherId) : v.getVoucherNo();
        // gl_salary_run 无 voucher_no 列：真实凭证号只能按 op_voucher.source_doc_id 反查（补列＝DDL 待确认项）
        log.info("薪酬批次 {}（{}）审批通过 → POSTED，计提凭证 {}（单据无凭证号列，按 source_doc_id 反查）",
                e.getRunNo(), event.getBizType(), provVoucherNo);
    }

    /**
     * 发放办结后的预算消耗确认（占用转已用）。
     * <p>{@code BudgetService.settleSalaryOnPaid} 是 {@code REQUIRES_NEW} 独立事务，
     * 因此这里的异常不会把外层审批收尾事务标成 rollback-only，catch 掉即可：
     * 预算侧算不动，也不能把已经发出去的工资回退成未审批。
     */
    private void settleSalaryBudgetQuietly(Long runId, String runNo) {
        try {
            budgetService.settleSalaryOnPaid(runId, runNo);
        } catch (Exception ex) {
            log.error("薪酬批次 {} 发放后预算消耗确认失败（不影响发放闭环，需人工按 biz_type=GL_SALARY 复核流水）: {}",
                    runNo, ex.getMessage(), ex);
        }
    }

    /** 计提驳回后的预算占用释放，隔离理由同 {@link #settleSalaryBudgetQuietly} */
    private void releaseSalaryBudgetQuietly(Long runId, String runNo) {
        try {
            budgetService.releaseSalaryOccupy(runId, runNo);
        } catch (Exception ex) {
            log.error("薪酬批次 {} 驳回后预算占用释放失败（额度可能被被驳批次长期占用）: {}",
                    runNo, ex.getMessage(), ex);
        }
    }

    /** 内部往来抵消：审批通过 → 生成 op_voucher（借应付账款/贷应收账款） */
    private void handleOffset(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) {
            return;
        }
        GlInterOffset e = glInterOffsetMapper.selectById(id);
        if (e == null) {
            log.warn("内部往来抵消收尾: 不存在的抵消分录 {}", id);
            return;
        }
        if (!approved) {
            // 驳回/撤回办结：自动对生成即 OFFSET（语义"已抵消"），驳回后无凭证却顶着已抵消态；
            // 手工录入行为 DRAFT。统一落 REJECTED。成对录入的另一行（同期间、方向对换、同金额）
            // 能唯一定位时一并回收；定位不到唯一行只标本行并告警——不凭猜测改第二行。
            String prev = e.getOffsetStatus();
            boolean withdrawn = event.withdrawn();
            e.setOffsetStatus("REJECTED");
            glInterOffsetMapper.updateById(e);
            // 本族收尾记录补齐——记账族/税金族/薪酬族的最终状态跃迁早已统一走 recordStatusAudit，
            // 内部往来抵消是同一个钩子里唯一"改了单据却零审计行"的一支（audit_trail 里没有任何 GL_OFFSET 行），
            // 事后按审计表复核抵消轨迹只能回查 wf_task_history。只增审计行，不动单据语义。
            recordStatusAudit(event, id, e.getOffsetNo(), "gl_inter_offset", prev, "REJECTED",
                    (withdrawn ? "申请人撤回，抵消分录作废" : "审批驳回，抵消分录作废") + withComment(event));
            try {
                List<GlInterOffset> twins = glInterOffsetMapper.selectList(new LambdaQueryWrapper<GlInterOffset>()
                        .eq(GlInterOffset::getPeriod, e.getPeriod())
                        .eq(GlInterOffset::getFromCompanyId, e.getToCompanyId())
                        .eq(GlInterOffset::getToCompanyId, e.getFromCompanyId())
                        .eq(GlInterOffset::getAmount, e.getAmount())
                        .ne(GlInterOffset::getId, e.getId())
                        .in(GlInterOffset::getOffsetStatus, "OFFSET", "DRAFT"));
                if (twins.size() == 1) {
                    GlInterOffset twin = twins.get(0);
                    String twinPrev = twin.getOffsetStatus();
                    twin.setOffsetStatus("REJECTED");
                    glInterOffsetMapper.updateById(twin);
                    // 成对行同样是本单据的写库动作，各记各的审计行（obj_id 不同，不混成一条）
                    recordStatusAudit(event, twin.getId(), twin.getOffsetNo(), "gl_inter_offset",
                            twinPrev, "REJECTED",
                            (withdrawn ? "申请人撤回，成对抵消分录一并作废（本行 " : "审批驳回，成对抵消分录一并作废（本行 ")
                                    + e.getOffsetNo() + "）");
                    log.info("内部往来抵消 {} 审批驳回，成对行 {} 一并 REJECTED", e.getOffsetNo(), twin.getOffsetNo());
                } else {
                    log.warn("内部往来抵消 {} 审批驳回：成对行定位到 {} 行（期望 1），仅本行落 REJECTED",
                            e.getOffsetNo(), twins.size());
                }
            } catch (Exception ex) {
                log.warn("内部往来抵消 {} 审批驳回：成对行回收失败（本行已落 REJECTED）: {}",
                        e.getOffsetNo(), ex.getMessage());
            }
            log.info("内部往来抵消 {} 审批驳回，状态 {} → REJECTED", e.getOffsetNo(), prev);
            return;
        }
        // （88 的"自写号 vs 真号"双轨在内部往来域同样成立）：
        // gl_inter_offset.voucher_no 由 GlInterOffsetService 自写（GLIO+期间+序号），真实凭证号
        // 另由 VoucherService.nextVoucherNo() 发一套 ⇒ 界面那串号在凭证台账按号查 0 命中。
        // 按 /136（记账族/税金族）既有回写规则，用凭证对象上的真号替换自写号；
        // 出证失败就保持原号不动——既不冒充已出证，也不把已有号抹成空。
        Long voucherId = null;
        try {
            voucherId = voucherService.generateInterOffsetVoucher(e);
        } catch (Exception ex) {
            log.error("内部往来抵消 {} 凭证生成失败（不影响流程闭环）: {}",
                    e.getOffsetNo(), ex.getMessage(), ex);
        }
        if (voucherId == null) {
            return;
        }
        OpVoucher v = voucherService.readVoucherCommitted(voucherId);
        if (v == null || v.getVoucherNo() == null || v.getVoucherNo().isBlank()) {
            return;
        }
        GlInterOffset noUpd = new GlInterOffset();
        noUpd.setId(e.getId());
        noUpd.setVoucherNo(v.getVoucherNo());
        glInterOffsetMapper.updateById(noUpd);
        // 这一支改的是单据列（自写号→真号），同样要有审计行；跃迁字段记 voucherNo 而非 status
        Map<String, Object> noBefore = new LinkedHashMap<>();
        noBefore.put("voucherNo", e.getVoucherNo());
        Map<String, Object> noAfter = new LinkedHashMap<>();
        noAfter.put("voucherNo", v.getVoucherNo());
        auditTrailService.recordUpdate(event.getBizType(), id, e.getOffsetNo(), "gl_inter_offset",
                String.valueOf(id), noBefore, noAfter,
                "审批通过·抵消凭证已出，回写真实凭证号" + withComment(event));
        log.info("内部往来抵消 {} 回写真实凭证号 {}（替换自写号 {}）",
                e.getOffsetNo(), v.getVoucherNo(), e.getVoucherNo());
    }

    /**
     * 税务退税（GL_TAX_REFUND）：审批通过 APPLYING→APPROVED 并生成退税凭证；
     * 驳回 APPLYING→REJECTED。仅当当前状态为 APPLYING 时才流转，避免覆盖 REFUNDING/REFUNDED 最终状态。
     */
    private void handleTaxRefund(WfProcessEndedEvent event, boolean approved) {
        Long id = event.getBizId();
        if (id == null) {
            return;
        }
        TaxRefund e = taxRefundMapper.selectById(id);
        if (e == null) {
            log.warn("税务退税收尾: 不存在的退税单 {}", id);
            return;
        }
        String target = approved ? "APPROVED" : "REJECTED";
        if ("APPLYING".equals(e.getStatus())) {
            // 审计扩面：改状态前采集旧值
            String oldStatus = e.getStatus();
            TaxRefund u = new TaxRefund();
            u.setId(id);
            u.setStatus(target);
            taxRefundMapper.updateById(u);
            Map<String, Object> auditBefore = new LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            Map<String, Object> auditAfter = new LinkedHashMap<>();
            auditAfter.put("status", u.getStatus());
            auditTrailService.recordUpdate(event.getBizType(), id, e.getRefundNo(), "tax_refund",
                    String.valueOf(id), auditBefore, auditAfter,
                    (approved ? "审批通过" : "审批驳回")
                            + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("退税单 {} 流程结束 → {}", e.getRefundNo(), target);
        } else {
            log.info("退税单 {} 流程结束（approved={}），当前状态 {} 非 APPLYING，跳过状态流转",
                    e.getRefundNo(), approved, e.getStatus());
        }
        if (!approved) {
            return;
        }
        try {
            voucherService.generateTaxRefundVoucher(e);
        } catch (Exception ex) {
            log.error("退税单 {} 凭证生成失败（不影响流程闭环）: {}", e.getRefundNo(), ex.getMessage(), ex);
        }
    }
}
