// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.asset.entity.MdAsset;
import com.fssc.asset.entity.StockCostSettle;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.bank.entity.BankReconcile;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.entity.ExpLoanOffset;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.expense.mapper.ExpLoanOffsetMapper;
import com.fssc.framework.facade.InvoiceDomainFacade;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.gl.service.GlService;
import com.fssc.glex.entity.GlInterOffset;
import com.fssc.glex.entity.GlJournalEntry;
import com.fssc.glex.entity.GlSalaryRun;
import com.fssc.glex.entity.GlTaxProvision;
import com.fssc.glex.entity.TaxRefund;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.masterdata.entity.MdCip;
import com.fssc.masterdata.entity.MdCostCenter;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.entity.MdGlConfig;
import com.fssc.masterdata.entity.MdPeriod;
import com.fssc.masterdata.entity.MdProject;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.mapper.MdCipMapper;
import com.fssc.masterdata.mapper.MdCostCenterMapper;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import com.fssc.masterdata.mapper.MdGlConfigMapper;
import com.fssc.masterdata.mapper.MdProjectMapper;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import com.fssc.masterdata.mapper.MdSupplierMapper;
import com.fssc.masterdata.service.PeriodService;
import com.fssc.masterdata.service.PostableSubjectResolver;
import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.entity.PoReceipt;
import com.fssc.purchase.mapper.PoOrderMapper;
import com.fssc.receivable.entity.RcBadDebt;
import com.fssc.receivable.entity.RcReceipt;
import com.fssc.receivable.entity.RcReceiptLine;
import com.fssc.glex.entity.TaxDeclaration;
import com.fssc.glex.entity.TaxProvisionRule;
import com.fssc.glex.service.TaxProvisionRuleService;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceiptLineMapper;
import com.fssc.receivable.mapper.RcReceiptMapper;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.entity.OpPayBatchDetail;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.entity.OpVoucherLine;
import com.fssc.sharing.mapper.OpPayBatchDetailMapper;
import com.fssc.sharing.mapper.OpPayBatchMapper;
import com.fssc.sharing.mapper.OpVoucherLineMapper;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.sharing.mapper.VoucherTemplateMapper;
import com.fssc.sharing.entity.VoucherTemplate;
import com.fssc.contract.mapper.ContContractMapper;
import com.fssc.contract.entity.ContContract;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysCompanyMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.service.ConfigService;
import com.fssc.system.service.NumberRuleService;
import com.fssc.treasury.entity.TrBillSettle;
import com.fssc.treasury.entity.TrCreditBill;
import com.fssc.treasury.entity.TrTransfer;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.mapper.WfInstanceMapper;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记账凭证服务：审核完成后按费用明细生成借贷分录。
 * 简化规则（演示）：借方=费用行金额（科目取行科目/费用项绑定科目，末级落地，缺配置即报错）；
 * 贷方=合计（其他应付款 2241）。完整凭证规则（税/往来/辅助核算）尚未实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final OpVoucherMapper opVoucherMapper;
    private final OpVoucherLineMapper opVoucherLineMapper;
    private final ExpBillLineMapper expBillLineMapper;
    private final ExpBillMapper expBillMapper;
    /** 按「借款冲抵」明细（而非表头混合规则的 offset_amount）定位真实冲借款金额 */
    private final ExpLoanOffsetMapper expLoanOffsetMapper;
    /** 对公结算计提凭证按 bill.poId 反查供应商，补齐凭证行辅助核算 */
    private final PoOrderMapper poOrderMapper;
    /** aux_json 里带供应商名称（只读主档，不改供应商任何数据） */
    private final MdSupplierMapper mdSupplierMapper;
    private final MdExpenseItemMapper mdExpenseItemMapper;
    private final MdSubjectMapper mdSubjectMapper;
    private final PostableSubjectResolver postableSubjectResolver;
    private final MdGlConfigMapper mdGlConfigMapper;
    private final MdCostCenterMapper mdCostCenterMapper;
    private final MdProjectMapper mdProjectMapper;
    private final PeriodService periodService;
    private final VoucherTemplateMapper voucherTemplateMapper;
    private final ContContractMapper contContractMapper;
    private final VoucherTemplateService voucherTemplateService;
    private final VoucherPushService voucherPushService;
    private final GlService glService;
    private final NumberRuleService numberRuleService;
    /** 审计扩面（D 类）：凭证修改/作废路径记录 */
    private final AuditTrailService auditTrailService;

    /**
     * 审计扩面：分录明细快照（只取变更相关字段，避免整实体 JSON 冗余/敏感）。
     * 供 {@link #updateLines} 的 before/after 复用。
     */
    private List<Map<String, Object>> lineSnapshot(List<OpVoucherLine> lines) {
        List<Map<String, Object>> snap = new ArrayList<>();
        if (lines == null) {
            return snap;
        }
        for (OpVoucherLine l : lines) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.getLineNo());
            m.put("subjectCode", l.getSubjectCode());
            m.put("direction", l.getDirection());
            m.put("amount", l.getAmount());
            m.put("summary", l.getSummary());
            snap.add(m);
        }
        return snap;
    }
    /** 统一编号规则取号（fallback 旧逻辑） */
    private String nextVoucherNo() {
        try { return numberRuleService.next("VOUCHER"); }
        catch (Exception e) { return "JZ" + System.currentTimeMillis(); }
    }
    private String nextStockVoucherNo() {
        try { return numberRuleService.next("STOCK_COST_SETTLE"); }
        catch (Exception e) { return "KC" + System.currentTimeMillis(); }
    }

    /** 同源凭证判重：按 source_type + source_doc_id 查询既有凭证（VOID 除外），返回最早一张的 ID；无则 null */
    private Long existingVoucherId(String sourceType, Long sourceDocId) {
        if (sourceDocId == null) {
            return null;
        }
        OpVoucher one = opVoucherMapper.selectOne(
                new LambdaQueryWrapper<OpVoucher>()
                        .eq(OpVoucher::getSourceType, sourceType)
                        .eq(OpVoucher::getSourceDocId, sourceDocId)
                        .ne(OpVoucher::getStatus, "VOID")
                        .orderByAsc(OpVoucher::getId)
                        .last("limit 1"));
        return one == null ? null : one.getId();
    }

    /**
     * 正式入账凭证的成对唯一键（发票 × 收货单）。
     * <p>与 {@link #existingVoucherId} 的区别只在多带一格 {@code bill_id}（入账凭证写入数据库时写的是
     * 收货单ID，见 {@code reverseEstimateOnInvoiceMatch} ②）。一张发票分两次部分关联到两张收货单时，
     * 两环节各自成账；同一对（同发票同收货单）重放仍命中去重。</p>
     */
    private Long existingInvoiceBookingId(Long invoiceId, Long receiptId) {
        if (invoiceId == null || receiptId == null) {
            return null;
        }
        OpVoucher one = opVoucherMapper.selectOne(
                new LambdaQueryWrapper<OpVoucher>()
                        .eq(OpVoucher::getSourceType, "RECEIPT_INVOICE")
                        .eq(OpVoucher::getSourceDocId, invoiceId)
                        .eq(OpVoucher::getBillId, receiptId)
                        .ne(OpVoucher::getStatus, "VOID")
                        .orderByAsc(OpVoucher::getId)
                        .last("limit 1"));
        return one == null ? null : one.getId();
    }

    /** 缺陷E：同源凭证判重的对外只读包装（借款/还款审批事件制单前防重放生成双凭证） */
    public Long existingVoucherIdOf(String sourceType, Long sourceDocId) {
        return existingVoucherId(sourceType, sourceDocId);
    }

    /**
     * 取本单据名下仍为 DRAFT 的凭证 id（供自动驳回腿作废）。
     * <p>只回 DRAFT——已过账凭证不得由驳回腿撤销，更正只能走红字冲销（与 {@link #voidVoucher} 同一道闸）。
     */
    public List<Long> draftVoucherIdsOfBill(Long billId) {
        List<Long> ids = new ArrayList<>();
        if (billId == null) {
            return ids;
        }
        for (OpVoucher v : opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getBillId, billId)
                .eq(OpVoucher::getStatus, "DRAFT")
                .orderByAsc(OpVoucher::getId))) {
            ids.add(v.getId());
        }
        return ids;
    }

    /**
     * 以独立事务读取已提交凭证。
     * <p>审批收尾（{@code GlBizAction}）运行在「流程结束事件」的事务里；MySQL 默认 REPEATABLE READ 下，
     * 该父事务的快照早于出证环节（{@code REQUIRES_NEW}）的提交。若在父事务内直接 {@code selectById}，
     * 恒读不到刚出证的凭证行 → voucher_no 回写在「凭证对象不可读」分支被跳过，
     * 表现就是单据 status=POSTED 而 voucher_no 为空。
     * 另起一个 REQUIRES_NEW 事务读取，其快照晚于出证提交，才能取到真实凭证号。
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true)
    public OpVoucher readVoucherCommitted(Long voucherId) {
        return voucherId == null ? null : opVoucherMapper.selectById(voucherId);
    }

    /**
     * 公司归属兜底：部分资金域实体（tr_credit_bill / tr_bill_settle）无 company_id 字段，
     * 但 op_voucher.company_id 为 NOT NULL。当实体缺 companyId 时，按租户取该租户首家公司
     * id 最小 作为凭证归属公司；仍取不到则回退 1L（演示租户 DEMO1 主公司）。
     * <p>须在租户上下文内调用（bus 已 callAs(tenantId)），否则 sys_company 查询不命中租户。
     */
    private Long resolveCompanyId(Long tenantId) {
        try {
            SysCompany c = sysCompanyMapper.selectOne(
                    new LambdaQueryWrapper<SysCompany>()
                            .eq(SysCompany::getTenantId, tenantId)
                            .orderByAsc(SysCompany::getId)
                            .last("limit 1"));
            if (c != null && c.getId() != null) {
                return c.getId();
            }
        } catch (Exception ex) {
            log.warn("解析租户默认公司失败 tenantId={}: {}", tenantId, ex.getMessage());
        }
        return 1L;
    }

    /**
     * 凭证落期统一规则：所有 generate* 的落期都不直接用裸 {@code LocalDate.now()}。
     *
     * <ul>
     *   <li>单据自带显式期间（periodYear/periodMonth）→ 用该期间，并强制 {@code assertOpen}（已关即拒）；
     *       凭证日期落在该期间内（当期→今天，否则→期间首日）。</li>
     *   <li>无显式期间 → 从当期起前滚（跨年，至多 24 期）找第一个开账期间落账；
     *       期间行不存在视同开账（与 {@link PeriodService#assertOpen} 同一规则）。永不向后落期。</li>
     *   <li>未来 24 期全部已关 → 拒绝生成。</li>
     * </ul>
     * companyId 为 null 时不做期间校验（与原行为一致，仅回退当天）。
     */
    private LocalDate landingDate(Long companyId, Integer periodYear, Integer periodMonth) {
        LocalDate now = LocalDate.now();
        if (periodYear != null && periodMonth != null) {
            if (companyId != null) {
                periodService.assertOpen(companyId, periodYear, periodMonth);
            }
            return (now.getYear() == periodYear && now.getMonthValue() == periodMonth)
                    ? now : LocalDate.of(periodYear, periodMonth, 1);
        }
        if (companyId == null) {
            return now;
        }
        int y = now.getYear();
        int m = now.getMonthValue();
        for (int i = 0; i < 24; i++) {
            MdPeriod p = periodService.periodOf(companyId, y, m);
            if (p == null || !"CLOSED".equals(p.getStatus())) {
                return (y == now.getYear() && m == now.getMonthValue()) ? now : LocalDate.of(y, m, 1);
            }
            m++;
            if (m > 12) {
                m = 1;
                y++;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                "公司 " + companyId + " 当期及未来 24 个会计期间均已结账，禁止生成凭证");
    }

    /**
     * GL 域 String period 解析为 [year, month]，非法/空返回 null（交给前滚兜底）。
     * GL 域的期间字段是无分隔的 yyyyMM（前端 ^\d{6}$ 强校验、月结服务亦按 length()==6 判定）。
     * 若按分隔符 split 再要求 parts>=2，"202612" 会恒解析为 null 并前滚落到系统当期，凭证整月落错期间。
     */
    private int[] parsePeriodYm(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        try {
            String trimmed = period.trim();
            String[] parts = trimmed.split("[^0-9]+");
            if (parts.length < 1 || parts[0].isEmpty()) {
                return null;
            }
            if (parts.length == 1 && parts[0].length() == 6) {
                parts = new String[]{parts[0].substring(0, 4), parts[0].substring(4)};
            }
            if (parts.length >= 2) {
                int y = Integer.parseInt(parts[0]);
                int mo = Integer.parseInt(parts[1]);
                if (y > 1900 && mo >= 1 && mo <= 12) {
                    return new int[]{y, mo};
                }
            }
        } catch (Exception ignored) {
            // 期间字符串无法解析（空/非数字/越界）→ 返回 null，由调用方按「无期间」处理
        }
        return null;
    }

    /** 组织合法性守卫（安全审计①）：读路径公司可见性 + 写路径公司归属校验 */
    private final OrgGuard orgGuard;
    /** 批次凭证去重：批次明细→付款记录→单据，跨类型校验单据是否已有单据级凭证 */
    private final OpPayBatchDetailMapper opPayBatchDetailMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    /** 租户默认公司解析：源实体缺 companyId 时（如 tr_credit_bill/tr_bill_settle）据租户回退 */
    private final SysCompanyMapper sysCompanyMapper;
    /** CIP 付款回写已付款金额（md_cip.paid_amount） */
    private final MdCipMapper cipMapper;
    /** 凭证列表制单人姓名回填（create_by 存用户 ID） */
    private final SysUserMapper sysUserMapper;
    /**
     * 给「由别的单据派生出来的凭证」补流程回链时，只读回查 wf_instance
     * （biz_type + biz_id ⇒ proc_inst_id）。本服务对 wf_instance <b>只 SELECT、零写入</b>，
     * 判据与单据联查取凭证的那条环节（{@code source_doc_type+source_doc_id} 或 {@code proc_inst_id}）同源。
     */
    private final WfInstanceMapper wfInstanceMapper;
    /**
     * 开票派生的应收确认凭证要按<b>票面真值</b>拆价税环节，只读 inv_invoice 的
     * net_amount/tax_amount；发票域的数据本服务一律不改。
     */
    /** 发票池只读经发票域 Facade（分库断点）；本类不再直连 inv_invoice */
    private final InvoiceDomainFacade invoiceFacade;
    /**
     * 出纳参数「付款完成后才允许生成付款环节凭证」走既有配置通道
     * （sys_config / {@code ConfigService.getValue}），本服务<b>只读</b>该参数，不写参数、不插参数行。
     */
    private final ConfigService configService;
    /** 批次级付款环节的实付证据只读回查 op_pay_batch.status（零写入） */
    private final OpPayBatchMapper opPayBatchMapper;
    /**
     * 核销环节出证前的「到账载体」判据只读 rc_receipt / rc_receipt_line，
     * 收款域的数据本服务一律不改（与只读 inv_invoice 同一处置法）。
     */
    private final RcReceiptMapper rcReceiptMapper;
    private final RcReceiptLineMapper rcReceiptLineMapper;
    /** 税金计提规则：申报出证与缴税付款凭证的科目都从这里取，不另写默认值 */
    private final TaxProvisionRuleService taxRuleService;

    /**
     * 出纳参数键——「付款完成后才允许生成付款环节凭证」。
     * <p>值 {@code 1/true/Y/是}＝是；其余（含读不到、读失败）＝<b>否</b>，即审批通过回调照长出
     * 付款环节 DRAFT 凭证。库里不插这行参数，所以默认恒为否。
     */
    private static final String CFG_PAY_LEG_AFTER_PAYMENT = "voucher.generate.pay-leg-after-payment";

    /** 受 {@link #CFG_PAY_LEG_AFTER_PAYMENT} 约束的付款环节来源族（贷 1002 银行存款）。 */
    private static final Set<String> PAY_LEG_SOURCE_TYPES =
            Set.of("EXPENSE_PAY", "CIP_PAY", "TREASURY_PAY", "CIP_PAY_BATCH");

    /**
     * 出纳参数是否要求"付款完成后才生成付款环节凭证"。
     * <p>读不到 / 读异常 ＝ 否 ⇒ 增量零影响（默认值方向必须是"否"）。
     */
    private boolean payLegRequiresSettledPayment() {
        try {
            String v = configService.getValue(CFG_PAY_LEG_AFTER_PAYMENT);
            if (v == null || v.isBlank()) {
                return false;
            }
            String s = v.trim();
            return "1".equals(s) || "true".equalsIgnoreCase(s) || "Y".equalsIgnoreCase(s) || "是".equals(s);
        } catch (Exception e) {
            log.warn("出纳参数 {} 读取失败，按默认值「否」执行（不改变现网制证时点）: {}",
                    CFG_PAY_LEG_AFTER_PAYMENT, e.getMessage());
            return false;
        }
    }

    /**
     * 款项是否已经真的付出。
     * <p>批次级族（TREASURY_PAY／CIP_PAY_BATCH）看 {@code op_pay_batch.status=PAID}；
     * 单据级族（EXPENSE_PAY／CIP_PAY）看本单 {@code exp_bill_payment} 里有无 PAID 环节
     * （一条付款记录都没有＝钱还没走，判为未付出，这正是本参数要拦的那一格）。
     * <p>证据读不到（异常）时按"已付出"处理——本参数是收紧项，不能让读库抖动反过来挡进出证。
     */
    private boolean paymentAlreadySettled(String sourceType, Long docId) {
        try {
            if ("TREASURY_PAY".equals(sourceType) || "CIP_PAY_BATCH".equals(sourceType)) {
                OpPayBatch batch =
                        docId == null ? null : opPayBatchMapper.selectById(docId);
                return batch == null || "PAID".equalsIgnoreCase(String.valueOf(batch.getStatus()));
            }
            if (docId == null) {
                return true;
            }
            List<ExpBillPayment> pays = expBillPaymentMapper.selectList(
                    new LambdaQueryWrapper<ExpBillPayment>()
                            .eq(ExpBillPayment::getBillId, docId));
            return pays.stream().anyMatch(p ->
                    "PAID".equalsIgnoreCase(String.valueOf(p.getPayStatus())));
        } catch (Exception e) {
            log.warn("付款腿实付证据读取失败（按已付出处理，不挡出证）{} {}: {}", sourceType, docId, e.getMessage());
            return true;
        }
    }

    /**
     * 付款环节出证前置校验——参数为"否"（默认）时恒放行＝现网行为逐字不变；
     * 参数为"是"且款项未付出时<b>不出证</b>，只留结构化痕（付款完成后可由补生成入口/批次通道补出）。
     *
     * @return true＝本次应跳过出证
     */
    private boolean payLegNotYetPayable(String sourceType, Long docId, String bizNo) {
        if (!PAY_LEG_SOURCE_TYPES.contains(sourceType) || !payLegRequiresSettledPayment()) {
            return false;
        }
        if (paymentAlreadySettled(sourceType, docId)) {
            return false;
        }
        log.info("出纳参数「付款完成后才允许生成付款腿凭证」＝是，{} {} 款项尚未付出，本次不生成付款腿凭证",
                sourceType, bizNo);
        recordGenFailureTrace(sourceType, docId, bizNo,
                "按出纳参数「付款完成后才允许生成付款凭证」暂不出证：款项尚未实际付出；"
                        + "付款执行完成后到 凭证工作台/补生成入口 重推即可");
        return true;
    }

    /**
     * 核销环节出证前置校验——先有到账载体，后有「借 银行存款／贷 应收账款」。
     * <p>载体＝挂在本次核销所属应收单上的收款核销单（{@code rc_receipt_line.receivable_id}
     * 或旧规则的 {@code rc_receipt.receivable_ids} 逗号串），与 {@code ArWriteoffService}
     * 的核销写入数据库环节同一张表；只 SELECT、零写入，也不回改历史凭证。
     */
    private boolean hasArrivalCarrier(Long receivableId) {
        if (receivableId == null) {
            return false;
        }
        try {
            Long byLine = rcReceiptLineMapper.selectCount(new LambdaQueryWrapper<RcReceiptLine>()
                    .eq(RcReceiptLine::getReceivableId, receivableId));
            if (byLine != null && byLine > 0) {
                return true;
            }
            Long byHeader = rcReceiptMapper.selectCount(new LambdaQueryWrapper<RcReceipt>()
                    .like(RcReceipt::getReceivableIds, String.valueOf(receivableId)));
            return byHeader != null && byHeader > 0;
        } catch (Exception e) {
            // 载体判据读不到时按"有"处理：本条是收紧项，不能让读库抖动把正常出证一并封死
            log.warn("核销腿到账载体（rc_receipt）读数失败，本次不据此拒出凭证 应收单 {}: {}",
                    receivableId, e.getMessage());
            return true;
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generate(ExpBill bill) {
        // 驳回/草稿/撤回等非有效单据禁生成凭证，防止污染总账
        if (bill.getStatus() == null
                || !List.of("APPROVED", "PAID", "CLOSED").contains(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已审批通过的单据可生成凭证，当前状态: " + (bill.getStatus() == null ? "null" : bill.getStatus()));
        }
        // 内存态校验之外再做库内真值复核（防事件重放「内存 APPROVED／库内已 VOID」残留活凭证）
        assertBillGeneratable(bill);
        if (bill.getCompanyId() != null && bill.getPeriodYear() != null && bill.getPeriodMonth() != null) {
            periodService.assertOpen(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        }
        // 同源判重：审批回调重放一次就会产生双凭证双计，所有 generate* 入口都必须查重。
        // 判重放在期间校验之后、取明细之前，这样「已制证 + 期间已关」的重放也能去重返回既有凭证。
        Long existed = existingVoucherId("EXPENSE", bill.getId());
        if (existed != null) {
            log.warn("单据 {} 已存在凭证 id={}，跳过重复生成（幂等）", bill.getBillNo(), existed);
            // 兜底回写凭证号：历史 REQUIRES_NEW 回滚会留下「凭证在、单据号为空」的悬挂态
            if (bill.getVoucherNo() == null || bill.getVoucherNo().isBlank()) {
                OpVoucher v0 = opVoucherMapper.selectById(existed);
                if (v0 != null && v0.getVoucherNo() != null && !v0.getVoucherNo().isBlank()) {
                    ExpBill billUpdate = new ExpBill();
                    billUpdate.setId(bill.getId());
                    billUpdate.setVoucherNo(v0.getVoucherNo());
                    billUpdate.setVoucherDate(v0.getVoucherDate() == null ? LocalDate.now() : v0.getVoucherDate());
                    expBillMapper.updateById(billUpdate);
                    log.info("单据 {} 兜底回写凭证号 {}", bill.getBillNo(), v0.getVoucherNo());
                }
            }
            return existed;
        }
        List<ExpBillLine> lines = expBillLineMapper.selectList(
                new LambdaQueryWrapper<ExpBillLine>().eq(ExpBillLine::getBillId, bill.getId()));
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据无费用明细，无法生成凭证");
        }
        Long tenantId = TenantContext.getTenantId();
        // 预取并缓存所有需要的主数据（成本中心/项目/费用项），避免 N+1
        Map<Long, MdCostCenter> ccCache = loadCostCenters(lines);
        Map<Long, MdProject> projCache = loadProjects(lines);
        Map<Long, MdExpenseItem> itemCache = loadItems(lines);

        LocalDate today = landingDate(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(bill.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(bill.getId());
        v.setSourceType("EXPENSE");
        v.setSourceDocType("EXPENSE");
        v.setSourceDocId(bill.getId());
        v.setProcInstId(bill.getProcInstId());
        // #3b：摘要取收款人主体名（服务端反查写入数据库），收款人为空才回落标题；标题是手填文本、不跟往来单位变
        v.setSummary(payeeNameOrTitle(bill));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        // 先按「待付款」生成所有明细分录
        // dim_status 在规则里：部分行要求"待付款/已付款"两条都出现；凭证生成默认按待付款规则。
        String billStatus = dimStatus(bill);
        String travelDim = travelFromBill(bill);
        int no = 1;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        List<MdGlConfig> rulePool = mdGlConfigMapper.queryByDims(
                tenantId,
                new HashSet<>(itemCache.keySet()),
                dimTypeCandidates(lines, ccCache, projCache),
                ccTypeCandidates(lines, ccCache),
                travelCandidates(travelDim),
                specialCandidates(lines, projCache),
                statusCandidates(billStatus),
                Arrays.asList("DEBIT", "CREDIT"));
        log.debug("[VOUCHER] bill={} pool={} items={} travel={} status={}",
                bill.getBillNo(), rulePool.size(), itemCache.keySet().size(), travelDim, billStatus);

        for (ExpBillLine line : lines) {
            BigDecimal amt = line.getAmount() == null ? BigDecimal.ZERO : line.getAmount();
            if (amt.signum() == 0) continue;
            MdExpenseItem item = itemCache.get(line.getItemId());
            String dimType = dimTypeOf(line);
            String ccType = ccTypeOf(line, ccCache);
            String travel = travelOf(line, item, travelDim);
            String special = specialOf(line, projCache);
            // 借方：按 md_gl_config 最佳匹配；未命中则按旧兜底
            MdGlConfig bestDr = bestMatch(rulePool, line, item, "DEBIT", dimType, ccType, travel, special, billStatus);
            Long drSubjectId; String drCode; String drName;
            if (bestDr != null && debitSubjectId(bestDr) != null) {
                // 规则池同样是制证科目来源：统一过末级/停用/跨公司校验，不能绕过费用项那套闸
                MdSubject resolvedDr = postableSubjectResolver.requireSubject(
                        debitSubjectId(bestDr), bill.getCompanyId(),
                        "记账规则（配置ID " + bestDr.getId() + "）", "【基础资料›记账配置】");
                drSubjectId = resolvedDr.getId();
                // 配置行可能只写了 subject_id（界面新建时仅选科目），code/name 为空：
                // 以 id 指向的主数据为准回填，避免分录文本回落成 "6602"/"管理费用"。
                String[] coords = subjectCoords(drSubjectId,
                        pickNotEmpty(bestDr.getSubjectCode(), bestDr.getAuxRule(), "6602"),
                        pickNotEmpty(bestDr.getSubjectName(), bestDr.getAuxRule(), "管理费用"));
                drCode = coords[0];
                drName = coords[1];
            } else {
                drSubjectId = resolveSubjectId(line.getSubjectId(), item, bill.getCompanyId());
                MdSubject s = drSubjectId == null ? null : mdSubjectMapper.selectById(drSubjectId);
                drCode = s == null ? "6602" : pickNotEmpty(s.getSubjectCode(), "6602");
                drName = s == null ? "管理费用" : pickNotEmpty(s.getSubjectName(), s.getSubjectCode(), "管理费用");
            }
            OpVoucherLine dr = buildLine(tenantId, v.getId(), no++,
                    item == null ? line.getRemark() : item.getItemName(),
                    drSubjectId, drCode, drName, "DEBIT", amt,
                    line.getDeptId() != null ? line.getDeptId() : bill.getDeptId());
            opVoucherLineMapper.insert(dr);
            debit = debit.add(amt);

            // 贷方：优先按 md_gl_config 行内 CREDIT 规则（一条借方对应一条贷方，不汇总）；
            //    若规则未提供，则按旧行为汇总后一笔贷记 2241 兜底（由循环外处理）。
            MdGlConfig bestCr = bestMatch(rulePool, line, item, "CREDIT", dimType, ccType, travel, special, billStatus);
            if (bestCr != null && creditSubjectId(bestCr) != null) {
                MdSubject resolvedCr = postableSubjectResolver.requireSubject(
                        creditSubjectId(bestCr), bill.getCompanyId(),
                        "记账规则（配置ID " + bestCr.getId() + "）", "【基础资料›记账配置】");
                String[] crCoords = subjectCoords(resolvedCr.getId(),
                        pickNotEmpty(bestCr.getSubjectCode(), bestCr.getAuxRule(), "2241"),
                        pickNotEmpty(bestCr.getSubjectName(), bestCr.getAuxRule(), "其他应付款"));
                String crCode = crCoords[0];
                String crName = crCoords[1];
                OpVoucherLine crLine = buildLine(tenantId, v.getId(), no++,
                        pickNotEmpty(bestCr.getSubjectName(), crName),
                        resolvedCr.getId(), crCode, crName, "CREDIT", amt,
                        line.getDeptId() != null ? line.getDeptId() : bill.getDeptId());
                opVoucherLineMapper.insert(crLine);
                credit = credit.add(amt);
            }
        }

        // 贷方兜底（当规则池没有任何 CREDIT 行命中或部分行未命中时，将差额汇总贷记应付科目）
        // 其中「已用借款冲抵」的那部分<b>不得</b>贷记应付报销款。那笔钱在借款时就已付出、
        // 挂在「1221 其他应收款-员工借款」名下，报销冲抵的会计实质是<b>冲销应收</b>，不是新增一笔对员工的负债。
        // 旧规则全额贷 2241 的后果（10-15 F4 实证）：报销单全额借款冲抵、公司一分现金未付，
        // 账上却新增 3900 应付报销款，且与 （付款凭证借的是 2202 而非 2241）叠加后 2241 只增不减，
        // 表现为"应付员工报销款"永久挂账、与实际资金流完全脱节。
        // 查询用 exp_loan_offset(offset_type='CLAIM') 明细而非表头 offset_amount：
        // 后者是借款冲抵与预付冲销的混合规则，预付对应科目是 1123 而非 1221，混算会串科目。
        BigDecimal creditGap = debit.subtract(credit);
        if (creditGap.signum() > 0) {
            BigDecimal offset = loanOffsetAmountOf(bill.getId());
            // 冲抵额不得超过待贷差额（超额冲抵或历史脏数据时按下限计，避免贷方出现负数行）
            BigDecimal offsetPart = offset.min(creditGap);
            BigDecimal payablePart = creditGap.subtract(offsetPart);
            if (offsetPart.signum() > 0) {
                Long loanSubjectId = findSubjectIdByCode("1221");
                OpVoucherLine offLine = buildLine(tenantId, v.getId(), no++,
                        "其他应收款-借款冲抵", loanSubjectId,
                        "1221", "其他应收款", "CREDIT", offsetPart, bill.getDeptId());
                opVoucherLineMapper.insert(offLine);
                credit = credit.add(offsetPart);
            }
            if (payablePart.signum() > 0) {
                Long creditSubjectId = findSubjectIdByCode("2241");
                if (creditSubjectId == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "报销单 " + bill.getBillNo() + " 无法贷记应付报销款：会计科目 2241 其他应付款缺失，请先维护会计科目表");
                }
                OpVoucherLine fallback = buildLine(tenantId, v.getId(), no++,
                        "其他应付款-应付报销款", creditSubjectId,
                        "2241", "其他应付款", "CREDIT", payablePart, bill.getDeptId());
                opVoucherLineMapper.insert(fallback);
                credit = credit.add(payablePart);
            }
            log.info("[VOUCHER] bill={} 贷方兜底：借款冲抵 {} 贷1221，应付报销款 {} 贷2241",
                    bill.getBillNo(), offsetPart, payablePart);
        }

        OpVoucher update = new OpVoucher();
        update.setId(v.getId());
        update.setDebitTotal(debit);
        update.setCreditTotal(credit);
        update.setIsBalanced(1);
        opVoucherMapper.updateById(update);

        ExpBill billUpdate = new ExpBill();
        billUpdate.setId(bill.getId());
        billUpdate.setVoucherNo(v.getVoucherNo());
        billUpdate.setVoucherDate(today);
        expBillMapper.updateById(billUpdate);
        return v.getId();
    }

    /**
     * 对公付款单（EXP_PAY, billType=PAY）生成对公付款凭证。
     * 与 EER 报销凭证语义不同：这是向供应商支付货款，不做费用行分摊，
     * 固定 借 应付账款(2202)，贷 银行存款(1002)，金额取应付金额（优先 payable_amount，否则总额）。
     * 凭证统一落 OpVoucher/OpVoucherLine，POSTED 后入总账 / 推送外部系统。
     *
     * <p>对公结算（billSubType=SETTLE）不走本模板——审批通过那一刻只确认了应付，
     * 单据 paid_amount=0、pay_time=NULL，直接贷记银行存款属"钱未付先减银行"；结算单改由
     * {@link #generateSettlementAccrual} 出计提应付凭证，付款环节交给出纳批次凭证
     * {@link #generateTreasuryPayVoucher}（借 2202／贷 1002）。
     *
     * @param bill 已审批通过的对公付款单
     * @return 凭证ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generatePtpPayment(ExpBill bill) {
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在");
        }
        // 出证前库内真值复核——VOID 单据禁止生成凭证（防事件重放残留活凭证）
        assertBillGeneratable(bill);
        // 结算单语义是"计提应付"，不是"付款"
        if (isSettlementBill(bill)) {
            return generateSettlementAccrual(bill);
        }
        // 对公付款凭证判重：同源已存在凭证（VOID 除外）去重返回，防止单据级/批次级重复入账
        Long existed = existingVoucherId("EXPENSE_PAY", bill.getId());
        if (existed != null) {
            log.warn("对公付款单 {} 已存在凭证 id={}，跳过重复生成（幂等）", bill.getBillNo(), existed);
            // 去重返回前兜底回写 voucher_no：
            // 历史缺陷（REQUIRES_NEW 行锁超时回滚）可能留下"凭证已在、单据 voucher_no 为空"的悬挂态
            if (bill.getId() != null && (bill.getVoucherNo() == null || bill.getVoucherNo().isBlank())) {
                try {
                    OpVoucher v = opVoucherMapper.selectById(existed);
                    if (v != null && v.getVoucherNo() != null && !v.getVoucherNo().isBlank()) {
                        ExpBill billUpdate = new ExpBill();
                        billUpdate.setId(bill.getId());
                        billUpdate.setVoucherNo(v.getVoucherNo());
                        billUpdate.setVoucherDate(v.getVoucherDate() == null ? LocalDate.now() : v.getVoucherDate());
                        expBillMapper.updateById(billUpdate);
                        log.info("对公付款单 {} 兜底回写凭证号 {}", bill.getBillNo(), v.getVoucherNo());
                    }
                } catch (Exception bex) {
                    log.warn("对公付款单 {} 兜底回写凭证号失败: {}", bill.getBillNo(), bex.getMessage());
                }
            }
            return existed;
        }
        // 出纳参数＝"是"时，款项未付出先不出这张付款环节凭证（借 2202／贷 1002）；
        // 参数读不到＝否＝默认，此处恒放行，制证时点与原先逐字一致（调用点不搬）。
        if (payLegNotYetPayable("EXPENSE_PAY", bill.getId(), bill.getBillNo())) {
            return null;
        }
        if (bill.getCompanyId() != null && bill.getPeriodYear() != null && bill.getPeriodMonth() != null) {
            periodService.assertOpen(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        }
        BigDecimal amt = (bill.getPayableAmount() != null && bill.getPayableAmount().signum() > 0)
                ? bill.getPayableAmount()
                : (bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO);
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款金额为 0，无法生成对公付款凭证");
        }
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(bill.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(bill.getId());
        v.setSourceType("EXPENSE_PAY");
        v.setSourceDocType("EXPENSE_PAY");
        v.setSourceDocId(bill.getId());
        v.setProcInstId(bill.getProcInstId());
        // #3b：摘要前缀不变（与金额/期间无关），取名改为收款人优先、为空回落标题
        v.setSummary("对公付款-" + payeeNameOrTitle(bill));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        // 借：应付账款 2202（对公付款冲减应付款；科目缺失则仅以编码/名称呈现）
        Long paySubjectId = findSubjectIdByCode("2202");
        // 这张付款凭证的两行辅助维度不能留空：2202 应付账款是供应商往来科目，
        // 不带供应商辅助就等于应付账龄与核销无法按供应商追溯。
        Long payeeSupplierId = resolvePayeeSupplierId(bill);
        OpVoucherLine payLine = buildLine(tenantId, v.getId(), 10,
                "应付账款-供应商货款", paySubjectId, "2202", "应付账款", "DEBIT", amt, bill.getDeptId());
        payLine.setSupplierId(payeeSupplierId);
        payLine.setProjectId(bill.getProjectId());
        stampAuxJson(payLine);
        opVoucherLineMapper.insert(payLine);
        // 贷：银行存款 1002（银行行不带往来辅助，对手方挂在应付行上；此处只把部门维度落进 aux_json）
        Long bankSubjectId = findSubjectIdByCode("1002");
        OpVoucherLine bankLine = buildLine(tenantId, v.getId(), 20,
                "银行存款-对公付款", bankSubjectId, "1002", "银行存款", "CREDIT", amt, bill.getDeptId());
        stampAuxJson(bankLine);
        opVoucherLineMapper.insert(bankLine);

        OpVoucher update = new OpVoucher();
        update.setId(v.getId());
        update.setDebitTotal(amt);
        update.setCreditTotal(amt);
        update.setIsBalanced(1);
        opVoucherMapper.updateById(update);

        ExpBill billUpdate = new ExpBill();
        billUpdate.setId(bill.getId());
        billUpdate.setVoucherNo(v.getVoucherNo());
        billUpdate.setVoucherDate(today);
        expBillMapper.updateById(billUpdate);

        log.info("对公付款单 {} 生成付款凭证 {} 借应付账款({})/贷银行存款({}) 金额={}",
                bill.getBillNo(), v.getVoucherNo(), "2202", "1002", amt);
        return v.getId();
    }

    /** 对公结算单判据（billType=PAY 且 billSubType=SETTLE） */
    private boolean isSettlementBill(ExpBill bill) {
        return bill != null && "PAY".equals(bill.getBillType())
                && "SETTLE".equalsIgnoreCase(bill.getBillSubType());
    }

    /**
     * #3b：凭证摘要的取名规则——收款人优先、为空回落标题（全仓各摘要位共用一份）。
     *
     * <p>摘要要说清"这笔钱涉及谁"，查询只能落在服务端反查主数据后写进
     * {@code exp_bill.payee_name} 的那个户名上；单据标题（{@code title}）是用户手填文本、
     * 页面换往来单位时不跟着变，一旦进摘要就会让凭证指向另一家的往来账。
     * 仅当收款人为空（无往来单位也无采购订单的老单据/子类型，或按合同生成的伪单据）时
     * 才回落标题，保证摘要非空、且这类单据的行为与本规则原先逐字一致。
     */
    private static String payeeNameOrTitle(ExpBill bill) {
        String payee = bill == null ? null : bill.getPayeeName();
        if (payee != null && !payee.isBlank()) {
            return payee;
        }
        String title = bill == null ? null : bill.getTitle();
        return title == null ? "" : title;
    }

    /**
     * 对公结算「计提应付」凭证：审批通过＝确认应付，钱还没付。
     * <ul>
     *  <li>借：费用科目——逐条费用行取行科目/费用项绑定科目（末级落地，缺配置即报错）；
     *      无行或行合计≠应付规则（核定核减、含税差）时按唯一费用项映射出一笔，保证借贷必等于应付额</li>
     *  <li>贷：应付账款(2202)，金额 = payable_amount（已扣预付冲销）否则 total_amount</li>
     *  <li>辅助核算：凭证行补 project_id（单据/费用行）与 supplier_id（bill.poId → po_order.supplier_id，取不到留空不猜）</li>
     * </ul>
     * 付款环节（借 2202／贷 1002）由出纳批次凭证 {@link #generateTreasuryPayVoucher} 承担，
     * 与 paid_amount/pay_time 的实际支付时点一致。
     *
     * <p>source_type 用独立值 {@code EXPENSE_SETTLE}：批次凭证的跨类型判重按
     * {@code source_type='EXPENSE_PAY' + source_doc_id=billId} 命中即跳过批次出证
     * （{@code generateTreasuryPayVoucher}），若计提应付也写 EXPENSE_PAY，
     * 结算单的付款环节会被永久吞掉。source_doc_type 仍写 {@code EXPENSE_PAY}，
     * 单据→凭证联查（按 source_doc_type 过滤）规则不变。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateSettlementAccrual(ExpBill bill) {
        // 出证前库内真值复核——VOID 单据禁止生成凭证（防事件重放残留活凭证）
        assertBillGeneratable(bill);
        // 同源判重（计提应付专用唯一键）
        Long existed = existingVoucherId(SOURCE_SETTLE, bill.getId());
        if (existed != null) {
            log.warn("对公结算单 {} 已存在计提应付凭证 id={}，跳过重复生成（幂等）", bill.getBillNo(), existed);
            if (bill.getId() != null && (bill.getVoucherNo() == null || bill.getVoucherNo().isBlank())) {
                try {
                    OpVoucher v0 = opVoucherMapper.selectById(existed);
                    if (v0 != null && v0.getVoucherNo() != null && !v0.getVoucherNo().isBlank()) {
                        ExpBill billUpdate = new ExpBill();
                        billUpdate.setId(bill.getId());
                        billUpdate.setVoucherNo(v0.getVoucherNo());
                        billUpdate.setVoucherDate(v0.getVoucherDate() == null ? LocalDate.now() : v0.getVoucherDate());
                        expBillMapper.updateById(billUpdate);
                        log.info("对公结算单 {} 兜底回写凭证号 {}", bill.getBillNo(), v0.getVoucherNo());
                    }
                } catch (Exception bex) {
                    log.warn("对公结算单 {} 兜底回写凭证号失败: {}", bill.getBillNo(), bex.getMessage());
                }
            }
            return existed;
        }
        if (bill.getCompanyId() != null && bill.getPeriodYear() != null && bill.getPeriodMonth() != null) {
            periodService.assertOpen(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        }
        BigDecimal amt = (bill.getPayableAmount() != null && bill.getPayableAmount().signum() > 0)
                ? bill.getPayableAmount()
                : (bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO);
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结算金额为 0，无法生成计提应付凭证");
        }
        Long payableSubjectId = findSubjectIdByCode("2202");
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(bill.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(bill.getId());
        v.setSourceType(SOURCE_SETTLE);
        // 联查按 source_doc_type 反查，保持 EXPENSE_PAY 域不变（D-TRC 链路不裂）
        v.setSourceDocType("EXPENSE_PAY");
        v.setSourceDocId(bill.getId());
        v.setProcInstId(bill.getProcInstId());
        // 摘要取收款人主体名（复用 #3b 规则）：标题是用户手填的文本，可能沿用上一次打开的单据没改，
        // 而计提凭证要说清"这笔应付是谁家的"，只能取服务端写入数据库的收款人名；收款人为空才回落标题。
        String accrualPayee = payeeNameOrTitle(bill);
        v.setSummary("对公结算计提应付-" + accrualPayee);
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        Long supplierId = resolveSettleSupplierId(bill);
        List<ExpBillLine> lines = expBillLineMapper.selectList(
                new LambdaQueryWrapper<ExpBillLine>().eq(ExpBillLine::getBillId, bill.getId()));
        Map<Long, MdExpenseItem> itemCache = loadItems(lines);
        BigDecimal lineSum = BigDecimal.ZERO;
        for (ExpBillLine l : lines) {
            lineSum = lineSum.add(l.getAmount() == null ? BigDecimal.ZERO : l.getAmount());
        }
        int no = 1;
        BigDecimal debit = BigDecimal.ZERO;
        if (!lines.isEmpty() && lineSum.compareTo(amt) == 0) {
            // 行合计恰等于应付：逐行出借方，保留费用行科目/部门/项目颗粒
            for (ExpBillLine line : lines) {
                BigDecimal lamt = line.getAmount() == null ? BigDecimal.ZERO : line.getAmount();
                if (lamt.signum() == 0) continue;
                MdExpenseItem item = itemCache.get(line.getItemId());
                Long drSubjectId = resolveSubjectId(line.getSubjectId(), item, bill.getCompanyId());
                MdSubject s = drSubjectId == null ? null : mdSubjectMapper.selectById(drSubjectId);
                String drCode = s == null ? "6602" : pickNotEmpty(s.getSubjectCode(), "6602");
                String drName = s == null ? "管理费用" : pickNotEmpty(s.getSubjectName(), s.getSubjectCode(), "管理费用");
                OpVoucherLine dr = buildLine(tenantId, v.getId(), no++,
                        item == null ? line.getRemark() : item.getItemName(),
                        drSubjectId, drCode, drName, "DEBIT", lamt,
                        line.getDeptId() != null ? line.getDeptId() : bill.getDeptId());
                dr.setProjectId(line.getProjectId() != null ? line.getProjectId() : bill.getProjectId());
                dr.setSupplierId(supplierId);
                stampAuxJson(dr);   // 辅助核算维度连同名称落 aux_json
                opVoucherLineMapper.insert(dr);
                debit = debit.add(lamt);
            }
        } else {
            // 无费用行 / 行合计与应付规则不一致（核定核减、税差、预付冲销）：按单据规则一笔计提，避免借贷不平。
            // 借方科目只能来自费用项目映射：多项目混杂时无法确定用哪一个，直接报错而不是回落 6602。
            Long expenseSubjectId = singleExpenseSubjectOf(lines, bill);
            MdSubject s = mdSubjectMapper.selectById(expenseSubjectId);
            OpVoucherLine dr = buildLine(tenantId, v.getId(), no++,
                    "结算计提应付-" + accrualPayee,
                    expenseSubjectId, s == null ? "6602" : pickNotEmpty(s.getSubjectCode(), "6602"),
                    s == null ? "管理费用" : pickNotEmpty(s.getSubjectName(), s.getSubjectCode(), "管理费用"),
                    "DEBIT", amt, bill.getDeptId());
            dr.setProjectId(bill.getProjectId());
            dr.setSupplierId(supplierId);
            stampAuxJson(dr);   // 同上
            opVoucherLineMapper.insert(dr);
            debit = amt;
            if (!lines.isEmpty()) {
                log.info("对公结算单 {} 费用行合计 {} 与应付口径 {} 不一致，借方按费用项映射一笔计提（科目 {}）",
                        bill.getBillNo(), lineSum, amt, s.getSubjectCode());
            }
        }
        String payableName = subjectNameOf(payableSubjectId);
        // 供应商辅助必须挂在 2202 应付账款这条贷方行上，而不是费用借方行：
        // 应付账龄/核销要看的正是这条「按供应商挂账」的正主行。同时补 supplier_id ＋ aux_json。
        OpVoucherLine payableLine = buildLine(tenantId, v.getId(), no,
                "应付账款-对公结算", payableSubjectId, "2202",
                payableName.isBlank() ? "应付账款" : payableName, "CREDIT", debit, bill.getDeptId());
        payableLine.setSupplierId(supplierId);
        payableLine.setProjectId(bill.getProjectId());
        stampAuxJson(payableLine);
        opVoucherLineMapper.insert(payableLine);

        OpVoucher update = new OpVoucher();
        update.setId(v.getId());
        update.setDebitTotal(debit);
        update.setCreditTotal(debit);
        update.setIsBalanced(1);
        opVoucherMapper.updateById(update);

        ExpBill billUpdate = new ExpBill();
        billUpdate.setId(bill.getId());
        billUpdate.setVoucherNo(v.getVoucherNo());
        billUpdate.setVoucherDate(today);
        expBillMapper.updateById(billUpdate);

        log.info("对公结算单 {} 生成计提应付凭证 {} 借费用科目/贷应付账款(2202) 金额={} 供应商辅助={} 项目辅助={}",
                bill.getBillNo(), v.getVoucherNo(), debit, supplierId, bill.getProjectId());
        return v.getId();
    }

    /**
     * 结算凭证供应商辅助核算查询：exp_bill 无供应商列（结算单的对手方挂在采购订单上），
     * 故按 bill.poId → po_order.supplier_id 间接取；poId 为空或订单查不到一律返回 null，
     * 不用 payeeName/contractId 猜一个填上去（错填辅助项比留空更难看）。
     */
    private Long resolveSettleSupplierId(ExpBill bill) {
        if (bill == null || bill.getPoId() == null) {
            return null;
        }
        try {
            PoOrder order = poOrderMapper.selectById(bill.getPoId());
            return order == null ? null : order.getSupplierId();
        } catch (Exception ex) {
            log.warn("对公结算单 {} 按 poId={} 取供应商失败，凭证行供应商辅助留空: {}",
                    bill.getBillNo(), bill.getPoId(), ex.getMessage());
            return null;
        }
    }

    /**
     * 证据 12-25:87：付款单凭证的供应商辅助查询。
     * <ul>
     *   <li>① {@code bill.poId → po_order.supplier_id}——与 结算凭证同一个环节；</li>
     *   <li>② 无采购订单时退 {@code bill.contractId → cont_contract.customer_id}。合同侧「往来单位」
     *       在本仓库就是按供应商维度使用的（合同凭证 {@code generateByTemplate} 写的就是
     *       {@code setSupplierId(contract.getCustomerId())}），付款记录也按同一列回查合同，
     *       所以这是<b>沿用既有映射</b>而不是新造一条；</li>
     *   <li>③ 两条都取不到＝留空，绝不按 {@code payee_name} 文本猜一个供应商挂上去（083 同一规则）。</li>
     * </ul>
     * 与 {@link #resolveSettleSupplierId} 刻意分成两个方法：结算凭证（083 现有行为）只认采购订单那一个环节，
     * 不受本条扩环节影响。
     */
    private Long resolvePayeeSupplierId(ExpBill bill) {
        if (bill == null) {
            return null;
        }
        Long byPo = resolveSettleSupplierId(bill);
        if (byPo != null) {
            return byPo;
        }
        if (bill.getContractId() == null) {
            return null;
        }
        try {
            ContContract c = contContractMapper.selectById(bill.getContractId());
            return c == null ? null : c.getCustomerId();
        } catch (Exception ex) {
            log.warn("单据 {} 按 contractId={} 取往来单位失败，凭证行供应商辅助留空: {}",
                    bill.getBillNo(), bill.getContractId(), ex.getMessage());
            return null;
        }
    }

    /**
     * 单据 → 供应商 ID 的对外只读规则（付款记录 {@code exp_bill_payment.payee_id} 回填用，
     * 见 {@code ExpenseWfEventListener.createPaymentRecord}）。取不到返回 null，调用方保持留空。
     */
    public Long billSupplierIdOf(ExpBill bill) {
        return resolvePayeeSupplierId(bill);
    }

    /** 供应商名称（只进 aux_json，不进 supplier_id 那列）；查不到就只给 ID，不编名字 */
    private String supplierNameOf(Long supplierId) {
        if (supplierId == null) {
            return null;
        }
        try {
            MdSupplier s = mdSupplierMapper.selectById(supplierId);
            return s == null ? null : s.getSupplierName();
        } catch (Exception ex) {
            log.warn("凭证行辅助核算取供应商名称失败 supplierId={}: {}", supplierId, ex.getMessage());
            return null;
        }
    }

    /** 项目名称（同上，aux_json 里带上名称才谈得上"自解释"） */
    private String projectNameOf(Long projectId) {
        if (projectId == null) {
            return null;
        }
        try {
            MdProject p = mdProjectMapper.selectById(projectId);
            return p == null ? null : p.getProjectName();
        } catch (Exception ex) {
            log.warn("凭证行辅助核算取项目名称失败 projectId={}: {}", projectId, ex.getMessage());
            return null;
        }
    }

    /**
     * 把分录上<b>已解析出的</b>辅助核算维度连同名称写进 {@code op_voucher_line.aux_json}
     * （库表第 19 列，实体列映射与唯一写入点都在这里）。
     * 键名沿用 {@code md_subject.aux_items} 的名称表（dept/project/supplier/customer/employee），
     * 值形如 {@code {"id":9,"name":"上海恒润精密机械有限公司"}}；只写实际有值的维度。
     *
     * <p>定位：纯<b>记录附加列</b>——权威辅助仍是 dept_id/project_id/supplier_id 那几列，
     * 本列只让"这行辅助取的是哪个往来单位/项目"在凭证行上自解释，免于回查单据与主数据。
     * 一个维度都没有时<b>保持 NULL</b>（不写空 JSON），序列化失败也只记日志，绝不影响出证。
     * {@code dept} 只给 ID 不给名称（本服务未注入部门主档，为此新添一条依赖不值）。
     */
    private void stampAuxJson(OpVoucherLine l) {
        Map<String, Object> aux = new LinkedHashMap<>();
        if (l.getDeptId() != null) {
            aux.put("dept", auxEntry(l.getDeptId(), null));
        }
        if (l.getProjectId() != null) {
            aux.put("project", auxEntry(l.getProjectId(), projectNameOf(l.getProjectId())));
        }
        if (l.getSupplierId() != null) {
            aux.put("supplier", auxEntry(l.getSupplierId(), supplierNameOf(l.getSupplierId())));
        }
        if (l.getCustomerId() != null) {
            aux.put("customer", auxEntry(l.getCustomerId(), null));
        }
        if (aux.isEmpty()) {
            return;
        }
        try {
            l.setAuxJson(AUX_OM.writeValueAsString(aux));
        } catch (Exception ex) {
            log.warn("凭证行辅助核算 JSON 序列化失败（不影响出证）: {}", ex.getMessage());
        }
    }

    private Map<String, Object> auxEntry(Long id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        if (name != null && !name.isBlank()) {
            m.put("name", name);
        }
        return m;
    }

    /** aux_json 序列化器（无状态，复用一份即可） */
    private static final ObjectMapper AUX_OM =
            new ObjectMapper();

    /**
     * 出纳批次凭证的供应商辅助——批次内付款记录指向<b>唯一</b>收款方时才落 supplier_id，
     * 跨供应商合并组批则留空（与 {@code op_pay_batch.proc_inst_id}「多实例不可归一 ⇒ 留 NULL」同一规则，
     * 不把多家的账混挂到一家名下）。收款方 ID 取付款记录的 {@code payee_id}（SUPPLIER 类型）。
     */
    private Long batchSupplierIdOf(
            List<ExpBillPayment> pays) {
        if (pays == null || pays.isEmpty()) {
            return null;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (ExpBillPayment p : pays) {
            if (p != null && "SUPPLIER".equalsIgnoreCase(p.getPayeeType()) && p.getPayeeId() != null) {
                ids.add(p.getPayeeId());
            }
        }
        return ids.size() == 1 ? ids.iterator().next() : null;
    }

    // 多辅助维度规则匹配工具

    /**
     * 每条明细行构建一条 OpVoucherLine（不含写入数据库）。
     * 科目先落末级再写行——非末级科目过不了账，制单时就得定下来；
     * 行的 id/编码/名称三者同取主数据解析结果，避免「id 指向明细、编码却写父级」的错账。
     */
    private OpVoucherLine buildLine(Long tenantId, Long vId, int no, String summary,
                                    Long subjectId, String code, String name, String dir,
                                    BigDecimal amt, Long deptId) {
        MdSubject postable = postableSubject(subjectId, code);
        OpVoucherLine l = new OpVoucherLine();
        l.setTenantId(tenantId);
        l.setVoucherId(vId);
        l.setLineNo(no);
        l.setSummary(summary);
        l.setSubjectId(postable == null ? subjectId : postable.getId());
        l.setSubjectCode(postable == null ? code : pickNotEmpty(postable.getSubjectCode(), code));
        l.setSubjectName(postable == null ? name : pickNotEmpty(postable.getSubjectName(), name));
        l.setDirection(dir);
        l.setAmount(amt);
        l.setCurrencyCode("CNY");
        l.setDeptId(deptId);
        l.setCreateTime(LocalDateTime.now());
        return l;
    }

    private Long debitSubjectId(MdGlConfig c)  { return c == null ? null : c.getDebitSubjectId(); }
    private Long creditSubjectId(MdGlConfig c) { return c == null ? null : c.getCreditSubjectId(); }

    /** 在预取规则池中按维度匹配度最高的一条返回（维度精确匹配越多越好；其次配置的维度越具体越好） */
    private MdGlConfig bestMatch(List<MdGlConfig> pool, ExpBillLine line, MdExpenseItem item,
                                 String direction, String dimType, String ccType, String travel,
                                 String special, String status) {
        if (pool == null || pool.isEmpty()) return null;
        List<MdGlConfig> dir = pool.stream().filter(c -> direction.equals(c.getDirection())).toList();
        if (dir.isEmpty()) return null;
        // 1) 同费用项优先；2) 每个维度：精确匹配 +3 / 规则维度为空(通配) +1 / 不匹配 0
        record Score(MdGlConfig c, int v) {}
        List<Score> scored = new ArrayList<>();
        Set<Long> itemCandidates = new HashSet<>();
        if (item != null) itemCandidates.add(item.getId());
        if (line.getItemId() != null) itemCandidates.add(line.getItemId());
        for (MdGlConfig c : dir) {
            if (c.getItemId() != null) {
                if (!itemCandidates.contains(c.getItemId())) continue; // 费用项绑定不匹配直接剔除
            }
            // 维度「精确不匹配」必须硬剔除，不能只扣负分：单靠 dimScore 的 −1000 抵消不了
            // "同费用项命中"的 +1000，两者正好抵平（住宿费那条算下来还是 35 分，照样通过）。
            // 于是 dim_status='已付款' 的「贷 银行存款」规则会压着一张一分钱没付的报销单命中，
            // 全额借款冲抵、公司未付现金的单子，凭证贷方竟挂银行存款，账实矛盾。
            // 这里按「任一配置维度精确不匹配直接剔除」的本意改为硬剔除。
            if (!dimMatches(c.getDimType(), dimType)) continue;
            if (!dimMatches(c.getDimCcType(), ccType)) continue;
            if (!dimMatches(c.getDimTravel(), travel)) continue;
            if (!dimMatches(c.getDimSpecial(), special)) continue;
            if (!dimMatches(c.getDimStatus(), status)) continue;
            int s = 0;
            if (c.getItemId() != null) s += 1000; // 同费用项优先
            // 每个维度：规则维度未配置=通配(+1 避免裸 0)，规则维度配置后精确匹配 +10。
            s += dimScore(c.getDimType(), dimType);
            s += dimScore(c.getDimCcType(), ccType);
            s += dimScore(c.getDimTravel(), travel);
            s += dimScore(c.getDimSpecial(), special);
            s += dimScore(c.getDimStatus(), status);
            // 维度越具体优先级越高（配置越多，通配越少）
            int configured = configured(c);
            s += configured;
            scored.add(new Score(c, s));
        }
        return scored.stream()
                .max(Comparator.comparingInt(Score::v))
                .map(Score::c).orElse(null);
    }

    private int dimScore(String ruleDim, String actualDim) {
        if (actualDim == null) actualDim = "";
        if (ruleDim == null || ruleDim.isEmpty()) return 1;       // 通配（弱正）
        if (ruleDim.equals(actualDim)) return 10;                 // 精确匹配
        return -1000;                                              // 配置维度不匹配 → 淘汰
    }

    /**
     * 维度硬匹配：规则未配置该维度＝通配（放行）；配置了就必须与单据实际维度精确相等。
     * <p>与 {@link #dimScore} 的区别：dimScore 用于打分排序（不匹配只扣负分，会被"同费用项 +1000"抵消），
     * 本方法用于<b>准入</b>——不通过即整条规则作废，不再参与排序。
     */
    private boolean dimMatches(String ruleDim, String actualDim) {
        if (ruleDim == null || ruleDim.isEmpty()) return true;    // 通配
        return ruleDim.equals(actualDim == null ? "" : actualDim);
    }

    private int configured(MdGlConfig c) {
        int n = 0;
        if (c.getItemId() != null) n++;
        if (c.getDimType() != null && !c.getDimType().isEmpty()) n++;
        if (c.getDimCcType() != null && !c.getDimCcType().isEmpty()) n++;
        if (c.getDimTravel() != null && !c.getDimTravel().isEmpty()) n++;
        if (c.getDimSpecial() != null && !c.getDimSpecial().isEmpty()) n++;
        if (c.getDimStatus() != null && !c.getDimStatus().isEmpty()) n++;
        return n;
    }

    private String pickNotEmpty(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return fallback;
    }
    private String pickNotEmpty(String a, String fallback) { return pickNotEmpty(a, null, fallback); }

    /**
     * 辅助核算强校验：科目 auxItems（逗号分隔，如 "dept,project,supplier"）声明需要哪些辅助维度，
     * 分录对应字段缺失即拦截。缺失维度为去重后的未满足项。
     */
    private void assertAux(MdSubject subj, OpVoucherLine l) {
        String aux = subj.getAuxItems();
        if (aux == null || aux.isBlank()) {
            return; // 科目未要求辅助核算
        }
        List<String> missing = new ArrayList<>();
        for (String item : aux.split(",")) {
            String it = item.trim();
            if (it.isEmpty()) continue;
            switch (it.toLowerCase()) {
                case "dept"      -> { if (l.getDeptId() == null) missing.add("部门"); }
                case "project"   -> { if (l.getProjectId() == null) missing.add("项目"); }
                case "supplier"  -> { if (l.getSupplierId() == null) missing.add("供应商"); }
                default -> { /* 其他辅助维度如员工/客户未在分录建模，跳过 */ }
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "科目 " + subj.getSubjectCode() + " " + subj.getSubjectName()
                            + " 缺少辅助核算项 " + String.join("、", missing));
        }
    }

    // 维度推断

    private String dimTypeOf(ExpBillLine l) {
        if (l.getProjectId() != null) return "项目";
        if (l.getCostCenterId() != null) return "成本中心";
        return "";
    }
    private String ccTypeOf(ExpBillLine l, Map<Long, MdCostCenter> cache) {
        MdCostCenter c = l.getCostCenterId() == null ? null : cache.get(l.getCostCenterId());
        if (c == null) return "";
        // 表中 ccType 形如 "MANAGEMENT"；规则中是"管理类/销售类/制造类"，映射转换
        String t = c.getCcType();
        if (t == null) return "";
        if (t.equalsIgnoreCase("MANAGEMENT")) return "管理类";
        if (t.equalsIgnoreCase("SALES")) return "销售类";
        if (t.equalsIgnoreCase("MANUFACTURE") || t.equalsIgnoreCase("MANUFACTURING")) return "制造类";
        return t;
    }
    private String specialOf(ExpBillLine l, Map<Long, MdProject> cache) {
        MdProject p = l.getProjectId() == null ? null : cache.get(l.getProjectId());
        if (p == null) return "否"; // 未关联项目 → 非专项
        String type = p.getProjectType() == null ? "" : p.getProjectType();
        return type.contains("专项") ? "是" : "否";
    }
    private String travelOf(ExpBillLine line, MdExpenseItem item, String billTravel) {
        if (billTravel != null && !billTravel.isBlank()) return billTravel;
        String join = ((item == null ? "" : item.getItemName()) + " " + (line.getRemark() == null ? "" : line.getRemark()));
        if (join.contains("国外") || join.contains("境外") || join.contains("签证")) return "国外";
        if (join.contains("三地")) return "三地";
        if (join.contains("国内") || join.contains("差旅") || join.contains("差")) return "国内";
        return "";
    }
    private String travelFromBill(ExpBill bill) {
        String sub = bill.getBillSubType();
        String title = bill.getTitle() == null ? "" : bill.getTitle();
        if ("TRAVEL".equals(sub) || title.contains("差旅") || title.contains("出差")) {
            if (title.contains("国外") || title.contains("境外")) return "国外";
            if (title.contains("三地")) return "三地";
            return "国内";
        }
        if ("TRAIN".equals(sub) || title.contains("培训")) return "";
        return "";
    }
    // 借款冲抵额

    /**
     * 报销单已发生的「借款冲抵」合计（凭证贷方据此拆出应冲销的 1221 应收部分）。
     * <p>规则＝{@code exp_loan_offset} 中 {@code bill_id=本单} 且 {@code offset_type='CLAIM'} 的金额合计
     * —— {@code bill_id} 侧存放的是<b>使用方</b>单据（报销单/还款单），
     * {@code offset_type='REPAY'} 的行属于还款资金侧，不计入本单的费用冲抵。
     * <p>取不到时返回 0：辅助查询失败不能阻断出证，此时按"无借款冲抵"处理（＝原先行为，全额贷 2241）。
     */
    private BigDecimal loanOffsetAmountOf(Long billId) {
        if (billId == null) return BigDecimal.ZERO;
        try {
            List<ExpLoanOffset> offs = expLoanOffsetMapper.selectList(
                    new LambdaQueryWrapper<ExpLoanOffset>()
                            .eq(ExpLoanOffset::getBillId, billId)
                            .eq(ExpLoanOffset::getOffsetType, "CLAIM"));
            BigDecimal sum = BigDecimal.ZERO;
            if (offs != null) {
                for (ExpLoanOffset o : offs) {
                    sum = sum.add(o.getOffsetAmount() == null ? BigDecimal.ZERO : o.getOffsetAmount());
                }
            }
            return sum;
        } catch (Exception ex) {
            log.warn("借款冲抵额查询失败 billId={}，按无冲抵处理: {}", billId, ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private String dimStatus(ExpBill bill) {
        if (bill.getPaidAmount() != null && bill.getPaidAmount().signum() > 0
                && bill.getPayableAmount() != null && bill.getPaidAmount().compareTo(bill.getPayableAmount()) >= 0) {
            return "已付款";
        }
        return "待付款";
    }

    // 候选集合（用于宽松查询 IN）

    private Collection<String> dimTypeCandidates(List<ExpBillLine> lines, Map<Long, MdCostCenter> cc, Map<Long, MdProject> pr) {
        Set<String> s = new HashSet<>();
        for (ExpBillLine l : lines) s.add(dimTypeOf(l));
        s.add(""); s.add("COSTCENTER"); s.add("PROJECT");
        return s;
    }
    private Collection<String> ccTypeCandidates(List<ExpBillLine> lines, Map<Long, MdCostCenter> cc) {
        Set<String> s = new HashSet<>();
        for (ExpBillLine l : lines) s.add(ccTypeOf(l, cc));
        s.add("");
        return s;
    }
    private Collection<String> travelCandidates(String travelDim) {
        Set<String> s = new HashSet<>();
        if (travelDim != null) s.add(travelDim);
        s.add(""); s.add("国内"); s.add("国外"); s.add("三地");
        return s;
    }
    private Collection<String> specialCandidates(List<ExpBillLine> lines, Map<Long, MdProject> pr) {
        Set<String> s = new HashSet<>();
        for (ExpBillLine l : lines) s.add(specialOf(l, pr));
        s.add("");
        s.add("是"); s.add("否");
        return s;
    }
    private Collection<String> statusCandidates(String billStatus) {
        Set<String> s = new HashSet<>();
        s.add("");
        if (billStatus != null) s.add(billStatus);
        s.add("待付款"); s.add("已付款");
        return s;
    }

    // 预加载

    private Map<Long, MdCostCenter> loadCostCenters(List<ExpBillLine> lines) {
        Set<Long> ids = new HashSet<>();
        for (ExpBillLine l : lines) if (l.getCostCenterId() != null) ids.add(l.getCostCenterId());
        if (ids.isEmpty()) return Collections.emptyMap();
        List<MdCostCenter> list = mdCostCenterMapper.selectBatchIds(ids);
        Map<Long, MdCostCenter> m = new LinkedHashMap<>();
        list.forEach(x -> m.put(x.getId(), x));
        return m;
    }
    private Map<Long, MdProject> loadProjects(List<ExpBillLine> lines) {
        Set<Long> ids = new HashSet<>();
        for (ExpBillLine l : lines) if (l.getProjectId() != null) ids.add(l.getProjectId());
        if (ids.isEmpty()) return Collections.emptyMap();
        List<MdProject> list = mdProjectMapper.selectBatchIds(ids);
        Map<Long, MdProject> m = new LinkedHashMap<>();
        list.forEach(x -> m.put(x.getId(), x));
        return m;
    }
    private Map<Long, MdExpenseItem> loadItems(List<ExpBillLine> lines) {
        Set<Long> ids = new HashSet<>();
        for (ExpBillLine l : lines) if (l.getItemId() != null) ids.add(l.getItemId());
        if (ids.isEmpty()) return Collections.emptyMap();
        List<MdExpenseItem> list = mdExpenseItemMapper.selectBatchIds(new ArrayList<>(ids));
        Map<Long, MdExpenseItem> m = new LinkedHashMap<>();
        list.forEach(x -> m.put(x.getId(), x));
        return m;
    }

    /** 记账：DRAFT → POSTED（人工过账，受制审分离约束） */
    public void post(Long voucherId, Long userId) {
        post(voucherId, userId, false);
    }

    /**
     * 记账：DRAFT → POSTED。
     * <p><b>制审分离改为后端强制</b>：本方法原有序列做了 8 项校验
     * （凭证存在、公司可见、非 POSTED、期间开账、分录非空、末级科目、辅助核算、借贷平衡），
     * 独缺"过账人 ≠ 制单人"。原先该校验只存在于浏览器（`GlLedger.vue` 的 canPostRow/postableRows
     * 纯客户端过滤），任何持 `sharing:voucher:post` 的人直接调
     * `POST /api/tenant/sharing/voucher/{id}/post`、`POST .../voucher/batch-post` 即可自提自过。
     * 现在后端拦截，前端过滤降级为"少点几次按钮"的体验优化。
     *
     * @param systemInitiated 系统自动过账豁免（见 {@link #postSystem}）。<b>只能由调用方显式声明，
     *                        不从"操作人为空"反推</b>——否则伪造一个无上下文调用就能绕过校验。
     */
    public void post(Long voucherId, Long userId, boolean systemInitiated) {
        OpVoucher v = opVoucherMapper.selectById(voucherId);
        if (v == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证不存在: " + voucherId);
        }
        orgGuard.assertVisible(v.getCompanyId(), null); // ① 写路径公司归属校验
        if ("POSTED".equals(v.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证已记账");
        }
        // ② 制审分离：制单人不可自审自过；系统过账豁免必须记录
        if (systemInitiated) {
            log.warn("凭证 {} 走系统自动过账，豁免制审分离闸门（操作人 {}）——" +
                    "仅限损益结转/实付入账等无人工审核环节的路径", v.getVoucherNo(), userId);
        } else {
            assertMakerCheckerSeparation(v, userId);
        }
        if (v.getCompanyId() != null && v.getPeriodYear() != null && v.getPeriodMonth() != null) {
            periodService.assertOpen(v.getCompanyId(), v.getPeriodYear(), v.getPeriodMonth());
        }
        List<OpVoucherLine> lines = opVoucherLineMapper.selectByVoucherId(voucherId);
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证无分录，禁止记账");
        }
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        for (OpVoucherLine l : lines) {
            BigDecimal amt = l.getAmount() == null ? BigDecimal.ZERO : l.getAmount();
            if ("DEBIT".equals(l.getDirection())) debit = debit.add(amt);
            else credit = credit.add(amt);
            // 末级科目强校验：仅末级科目可记账（防挂非末级导致明细汇总失真）
            if (l.getSubjectId() != null) {
                MdSubject subj = mdSubjectMapper.selectById(l.getSubjectId());
                if (subj == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "科目不存在: " + l.getSubjectId());
                }
                if (subj.getIsLeaf() != null && subj.getIsLeaf() == 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "科目 " + subj.getSubjectCode() + " " + subj.getSubjectName() + " 非末级科目，禁止记账");
                }
                // 辅助核算强校验：科目要求辅助项时，分录对应字段必须带出
                assertAux(subj, l);
            }
        }
        if (debit.compareTo(credit) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "凭证借贷不平衡，禁止记账: 借 " + debit + " / 贷 " + credit);
        }
        OpVoucher update = new OpVoucher();
        update.setId(voucherId);
        update.setStatus("POSTED");
        // 85 后半：凭证状态已是 POSTED、过账人却查无其人。
        // 系统自动过账通道（VoucherAutoPostService→batchPost，以及 PayBatchBizAction:216/225 直传 null）
        // 的 userId 就是 null，而 MP 的 updateById 会忽略实体里的 null 字段 ⇒ post_time 写了、
        // post_user_id 恒 NULL，"谁记的账"这一列在自动过账的凭证上永远空着。
        // 规则：优先调用方显式传入的过账人；没有则取当前登录人；再没有按本仓库系统作业惯例记 0
        // （与 FsscMetaObjectHandler.fillAudit 的 0/"system" 同一约定）——留 0 是"系统干的"这个事实，
        // 留 NULL 才是缺陷。人工过账口（SharingController /post、/batch-post）传的是真人，不受影响。
        Long poster = userId != null ? userId : systemPosterId();
        update.setPostUserId(poster);
        update.setPostTime(LocalDateTime.now());
        update.setDebitTotal(debit);
        update.setCreditTotal(credit);
        update.setIsBalanced(1);
        opVoucherMapper.updateById(update);
        // POSTED 凭证落账总账（EER 报销 / PTP 对公付款统一入账）
        try {
            // 非系统路径把操作人带进分录；系统自动过账（postSystem，userId 可为空）走 1 参重载，
            // 让 GlService 回退凭证头 post_user_id——把 null 当参数传下去只会掩盖这条回退环节。
            if (userId == null) {
                glService.postVoucherToGl(voucherId);
            } else {
                glService.postVoucherToGl(voucherId, userId);
            }
        } catch (Exception ex) {
            log.error("凭证 {} 落账总账失败（凭证已 POSTED，可调 /op/gl/repost 补落账）: {}",
                    v.getVoucherNo(), ex.getMessage(), ex);
        }
    }

    /**
     * 系统自动过账（豁免制审分离校验）。
     * <p>仅用于<b>没有人工审核环节参与</b>的自动生成凭证入账路径：
     * <ul>
     *   <li>{@code GlProfitCarryService} 损益结转——结转凭证由总账会计当场生成并当场入账，
     *       本身是月结作业的系统动作，另派他人过账无会计意义；</li>
     *       <li>{@code VoucherAutoPostService} 实付入账——付款审批与付款执行已在流程侧完成职责分离，
     *       付款成功是入账触发条件，若因制单人恰为付款执行人而拒过账，钱已付出而账不落，损失更大。</li>
     * </ul>
     * 走此路径必留 warn 日志（含凭证号与操作人，见 {@code post} 内的豁免分支），
     * 供事后核查"系统豁免"被用得对不对；
     * 人工过账入口（{@code SharingController} 的 /post、/batch-post）一律不走这里。
     */
    public void postSystem(Long voucherId, Long userId) {
        post(voucherId, userId, true);
    }

    /**
     * 制审分离校验：过账人等于凭证制单人（op_voucher.create_by）即拒绝。
     * <p>规则与流程侧「禁止审批自己提交的单据」一致；制单人记录缺失（历史数据/系统生成凭证）时
     * 无从判定，放行并告警——不能把"缺失"当作"另有其人"的证据，也不能因数据缺痕把账堵死。
     */
    private void assertMakerCheckerSeparation(OpVoucher v, Long userId) {
        Long maker = v.getCreateBy();
        if (maker == null) {
            log.warn("凭证 {} 无制单人留痕(create_by 为空)，制审分离无法判定，本次放行（过账人 {}）",
                    v.getVoucherNo(), userId);
            return;
        }
        if (maker.equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "制审分离：凭证 " + v.getVoucherNo() + " 由本人制单，不可由本人过账，请交由其他会计过账");
        }
    }

    /**
     * 过账人兜底规则：调用方没显式传 userId 时，先取当前登录人，
     * 仍无登录态（定时/自动过账通道）则按本仓库系统作业惯例记 0——
     * 与 {@code FsscMetaObjectHandler.fillAudit} 对 Long 型审计列填 0、String 型填 "system" 同一约定，
     * 不是"造一个操作人"，而是把"这次是系统干的"写成事实而不是留 NULL。
     * <p>注意：本方法只填 post_user_id（记录列），不参与 制审分离判定
     * （那条校验看的是 create_by，系统豁免走 {@link #postSystem} 并留 warn 日志）。
     */
    private static Long systemPosterId() {
        LoginUser u = UserContext.get();
        return (u != null && u.getUserId() != null) ? u.getUserId() : 0L;
    }

    /**
     * 批量过账：逐张 DRAFT→POSTED。
     * 与单张 post 同一套强校验（公司归属/关账/末级科目/辅助核算/借贷平衡/制审分离），
     * 单张失败不影响其余（返回每张的结果与原因），由调用方汇总提示——
     * 因此"自制自过"在批量口同样被拦，原因逐张回显而非整批失败。
     */
    /**
     * 凭证作废——仅 DRAFT 可作废，作废记录不可逆
     *
     * <p>业务写＋记录写成对（跨表写成对者补边界）：缺边界时"凭证已 VOID 而查不到谁作废的"。
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidVoucher(Long voucherId, Long userId) {
        OpVoucher v = opVoucherMapper.selectById(voucherId);
        if (v == null) throw new BizException(ErrorCode.PARAM_INVALID, "凭证不存在: " + voucherId);
        if (!"DRAFT".equals(v.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿凭证可作废，当前状态: " + v.getStatus());
        }
        // 审计扩面（D 类·凭证反审核/作废）：原先采集旧值
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", v.getStatus());
        v.setStatus("VOID");
        opVoucherMapper.updateById(v);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", v.getStatus());
        auditTrailService.recordUpdate("VOUCHER", voucherId, v.getVoucherNo(), "op_voucher",
                String.valueOf(voucherId), auditBefore, auditAfter, "凭证作废（DRAFT → VOID）");
        // 作废凭证联动清源单表头 voucher_no 冗余引用——否则反审核前置校验
        // （ExpenseBillService.unaudit 按 exp_bill.voucher_no 非空判"已制证"）恒拦，
        // "作废凭证→反审核"链在单据侧断头（通行规则：凭证先行作废后反审核应可走通）。
        // 仅清"表头引用仍指向本凭证"的单据，避免误伤已重制证挂新凭证的单。
        clearSourceBillVoucherRef(v);
    }

    /** 按 source_doc_id 回清源单表头 voucher_no（仅当引用仍指向本凭证时）。 */
    private void clearSourceBillVoucherRef(OpVoucher v) {
        try {
            if (v.getSourceDocId() == null || v.getVoucherNo() == null || v.getVoucherNo().isBlank()) {
                return;
            }
            // 费用/支付族单据（EXPENSE/EXP_PAY/CLAIM/PAY 等落 exp_bill 的域）才有表头冗余列；
            // RECEIVABLE/PURCHASE 等其他域表头无 voucher_no，天然不受影响。
            String docType = v.getSourceDocType() == null ? "" : v.getSourceDocType();
            if (!docType.startsWith("EXP") && !"CLAIM".equals(docType) && !"PAY".equals(docType)) {
                return;
            }
            LambdaUpdateWrapper<ExpBill> upd =
                    new LambdaUpdateWrapper<>();
            upd.eq(ExpBill::getId, v.getSourceDocId())
               .eq(ExpBill::getVoucherNo, v.getVoucherNo())
               .set(ExpBill::getVoucherNo, null);
            expBillMapper.update(null, upd);
        } catch (Exception ex) {
            // 联动清引用失败不阻断作废主链路（与 auditTrail 同级容错），留错误日志供排查
            LoggerFactory.getLogger(VoucherService.class)
                    .error("作废凭证回清源单 voucher_no 失败 voucherId={} sourceDocId={}", v.getId(), v.getSourceDocId(), ex);
        }
    }

    /**
     * 红字冲销——基于原凭证生成一张方向相反、金额相等的红字凭证并直接过账
     *
     * <p>红字头（op_voucher）＋红字行（op_voucher_line）两表成对写：缺边界会生成"有头无行"的不平衡凭证。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long reverseVoucher(Long voucherId, Long userId, String reason) {
        OpVoucher src = opVoucherMapper.selectById(voucherId);
        if (src == null) throw new BizException(ErrorCode.PARAM_INVALID, "凭证不存在: " + voucherId);
        if (!"POSTED".equals(src.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已过账凭证可冲销，当前状态: " + src.getStatus());
        }
        OpVoucher rev = new OpVoucher();
        rev.setTenantId(src.getTenantId());
        rev.setVoucherNo(src.getVoucherNo() + "-R");
        rev.setCompanyId(src.getCompanyId());
        rev.setPeriodYear(src.getPeriodYear());
        rev.setPeriodMonth(src.getPeriodMonth());
        rev.setVoucherDate(LocalDate.now());
        rev.setSourceDocType(src.getSourceDocType());
        rev.setSourceDocId(src.getSourceDocId());
        // 红字冲销凭证继承被冲销凭证的流程实例，保证"冲哪张"也能反查到那次审批
        rev.setProcInstId(src.getProcInstId());
        rev.setSummary("红字冲销:" + (src.getSummary() == null ? "" : src.getSummary()));
        rev.setDebitTotal(src.getDebitTotal());
        rev.setCreditTotal(src.getCreditTotal());
        rev.setStatus("POSTED");
        rev.setRemark(reason == null || reason.isBlank() ? "红字冲销原凭证 " + src.getVoucherNo() : "红字冲销：" + reason);
        // 红字凭证同样是"直接 POSTED"的建行，过账人缺登录态时与本方法上方 post() 同一规则
        // 兜到当前登录人／系统作业 0，不再留一张"已记账却无人过账"的红字。
        rev.setPostUserId(userId != null ? userId : systemPosterId());
        rev.setPostTime(LocalDateTime.now());
        rev.setIsBalanced(1);
        Long alreadyReversed = opVoucherMapper.selectCount(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getTenantId, src.getTenantId())
                .eq(OpVoucher::getVoucherNo, rev.getVoucherNo()));
        if (alreadyReversed != null && alreadyReversed > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该凭证已做过红字冲销，禁止重复冲销");
        }
        opVoucherMapper.insert(rev);
        List<OpVoucherLine> srcLines = opVoucherLineMapper.selectByVoucherId(voucherId);
        for (OpVoucherLine line : srcLines) {
            OpVoucherLine rl = new OpVoucherLine();
            rl.setVoucherId(rev.getId());
            rl.setLineNo(line.getLineNo());
            rl.setSummary("冲销-" + (line.getSummary() == null ? "" : line.getSummary()));
            rl.setSubjectId(line.getSubjectId());
            rl.setSubjectCode(line.getSubjectCode());
            rl.setSubjectName(line.getSubjectName());
            // 方向相反、金额相等（红字冲销规则）
            rl.setDirection("DEBIT".equals(line.getDirection()) ? "CREDIT" : "DEBIT");
            rl.setAmount(line.getAmount());
            rl.setCurrencyCode(line.getCurrencyCode());
            opVoucherLineMapper.insert(rl);
        }
        // 红字凭证同步落总账（与 post 同一规则），否则冲销分录不进余额
        try {
            glService.postVoucherToGl(rev.getId());
        } catch (Exception ex) {
            log.error("红字冲销凭证 {} 落账总账失败（可 /op/gl/repost 补落账）: {}", rev.getVoucherNo(), ex.getMessage());
        }
        return rev.getId();
    }

    /**
     * 域级冲销的统一出口（记账域先用，后续同族域可复用）。
     * <p>按来源单据（source_type + source_doc_id）定位它那张有效凭证，再按凭证当时状态分两走：
     * <ul>
     *   <li><b>已记账（POSTED）</b>：账面已有数，出红字反向凭证（借贷方向对调、等额，直接过账并落总账）
     *       —— 即 {@link #reverseVoucher} 既有规则；</li>
     *   <li><b>仍是草稿（DRAFT）</b>：办结已出证、记账岗尚未过账，账面上根本没有这笔数，
     *       此时红字反向等于凭空造两张不进余额的凭证；正确动作是把草稿凭证作废
     *       （{@link #voidVoucher} DRAFT→VOID，审计已记录），单据回到可重办状态。</li>
     * </ul>
     * 凭证台账的「红字冲销」按钮前置 {@code status==='POSTED'}，草稿凭证一律置灰
     * （证据 12-28:75：预提单凭证 id=24 停在 DRAFT → 全系统无任何冲销出口），
     * 本方法把这段缺口在记账域侧补上。
     *
     * @param sourceType 来源类型（凭证 source_type，如 GL_ACCRUAL）
     * @param sourceDocId 来源单据主键
     * @param userId 操作人（红字凭证记 post_user_id）
     * @param reason 冲销原因（必填，落红字凭证 remark 与作废审计说明）
     * @return action=REVERSE|VOID、originVoucherId/originVoucherNo/originStatus、
     *         reverseVoucherId/reverseVoucherNo（VOID 场景为 null）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reverseBySource(String sourceType, Long sourceDocId, Long userId, String reason) {
        if (sourceType == null || sourceType.isBlank() || sourceDocId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "冲销须指定来源类型与来源单据");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "冲销原因不能为空（审计要求可追人）");
        }
        OpVoucher src = opVoucherMapper.selectOne(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getSourceType, sourceType)
                .eq(OpVoucher::getSourceDocId, sourceDocId)
                .ne(OpVoucher::getStatus, "VOID")
                .orderByAsc(OpVoucher::getId)
                .last("limit 1"));
        if (src == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "来源单据未生成有效凭证（source_type=" + sourceType + ", id=" + sourceDocId + "），无可冲销账");
        }
        orgGuard.assertVisible(src.getCompanyId(), null); // ① 写路径公司归属校验（与 post 同一规则）
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("originVoucherId", src.getId());
        ret.put("originVoucherNo", src.getVoucherNo());
        ret.put("originStatus", src.getStatus());
        if ("POSTED".equals(src.getStatus())) {
            if (src.getCompanyId() != null && src.getPeriodYear() != null && src.getPeriodMonth() != null) {
                periodService.assertOpen(src.getCompanyId(), src.getPeriodYear(), src.getPeriodMonth());
            }
            Long revId = reverseVoucher(src.getId(), userId, reason);
            OpVoucher rev = opVoucherMapper.selectById(revId);
            ret.put("action", "REVERSE");
            ret.put("reverseVoucherId", revId);
            ret.put("reverseVoucherNo", rev == null ? null : rev.getVoucherNo());
        } else if ("DRAFT".equals(src.getStatus())) {
            voidVoucher(src.getId(), userId);
            ret.put("action", "VOID");
            ret.put("reverseVoucherId", null);
            ret.put("reverseVoucherNo", null);
        } else {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "凭证 " + src.getVoucherNo() + " 当前状态 " + src.getStatus() + "，既非草稿也非已过账，无法冲销");
        }
        log.info("来源 {}#{} 冲销：原凭证 {}（{}）→ {}", sourceType, sourceDocId,
                src.getVoucherNo(), src.getStatus(), ret.get("action"));
        return ret;
    }

    /**
     * 作废即级联：源单作废时，同批办结它名下的全部非 VOID 凭证。
     *
     * <p>抬头形态：exp_bill 作成 VOID 后仍挂活凭证（DRAFT/POSTED），/audit/reconcile 的
     * 「凭证一致性」就会报「借贷合计 vs 单据 0」这类账实差异，且单据已死、凭证仍上账。
     * 由 {@code ExpenseBillService.voidBill} 在同一事务内调用，逐张分派到本类既有机制，不另造第二套：
     * <ul>
     *   <li>DRAFT → {@link #voidVoucher}（DRAFT→VOID，auditTrail 记录，并联动回清源单 voucher_no）；</li>
     *   <li>POSTED → {@link #reverseVoucher}（账面已有数：出红字反向并落总账。直接置 VOID 会留下
     *       未冲销的分录，与 {@link #reverseBySource} 的 POSTED 环节同一规则）；</li>
     *   <li>其余状态（理论不可达）→ 抛错 fail-loud，绝不放任何一张哑状态凭证继续挂着。</li>
     * </ul>
     * 查询按 bill_id 且仅限 {@link #EXPENSE_BILL_SOURCES} 族来源（串号校验同源），
     * 不误伤同号他域凭证、也不会二次命中冲销产生的红字凭证（红字不写 bill_id）。
     *
     * @param bill 已作成 VOID 的源单（调用方已在同事务内落状态）
     * @param userId 操作人（作废审计／红字凭证 post_user_id）
     * @return 办结的凭证张数（去重：重复调用时已无非 VOID 凭证，自然为空操作）
     */
    @Transactional(rollbackFor = Exception.class)
    public int cascadeVoidBySourceBill(ExpBill bill, Long userId) {
        if (bill == null || bill.getId() == null) {
            return 0;
        }
        List<OpVoucher> actives = opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getBillId, bill.getId())
                .in(OpVoucher::getSourceType, EXPENSE_BILL_SOURCES)
                .ne(OpVoucher::getStatus, "VOID")
                .orderByAsc(OpVoucher::getId));
        int done = 0;
        for (OpVoucher v : actives) {
            if ("DRAFT".equals(v.getStatus())) {
                voidVoucher(v.getId(), userId);
            } else if ("POSTED".equals(v.getStatus())) {
                // 与 reverseBySource 的 POSTED 环节同校验：落期已关时不冲销（否则红字记进已关期间）
                if (v.getCompanyId() != null && v.getPeriodYear() != null && v.getPeriodMonth() != null) {
                    periodService.assertOpen(v.getCompanyId(), v.getPeriodYear(), v.getPeriodMonth());
                }
                reverseVoucher(v.getId(), userId, "源单 " + bill.getBillNo() + " 作废级联红字冲销");
            } else {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "凭证 " + v.getVoucherNo() + " 状态异常（" + v.getStatus() + "），源单作废级联中止");
            }
            done++;
        }
        if (done > 0) {
            log.info("源单 {} 作废级联：处理凭证 {} 张（DRAFT→VOID／POSTED→红字冲销）", bill.getBillNo(), done);
        }
        return done;
    }

    /**
     * 写侧校验（生成侧·正面清单）：非有效状态单据一律禁止出证。
     *
     * <p>除与 {@link #generate} 的 同一规则外，再以<b>库内真值</b>复核一次：审批回调会把
     * 内存状态先置 APPROVED 再过校验（见 ExpenseWfEventListener 的时序注释），事件重放时库里
     * 单据可能已 VOID——只信入参就会放行出证，造出「VOID 单＋活凭证」残留（本校验的正面对象）。
     */
    private void assertBillGeneratable(ExpBill bill) {
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在，无法生成凭证");
        }
        if (bill.getStatus() == null
                || !List.of("APPROVED", "PAID", "CLOSED").contains(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已审批通过的单据可生成凭证，当前状态: " + (bill.getStatus() == null ? "null" : bill.getStatus()));
        }
        if (bill.getId() != null) {
            ExpBill dbState = expBillMapper.selectById(bill.getId());
            if (dbState != null && "VOID".equals(dbState.getStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "单据 " + bill.getBillNo() + " 已作废（VOID），禁止生成凭证");
            }
        }
    }

    /**
     * 写侧校验（模板出证口·负面清单）：库内已死状态禁止再出证。
     * <p>与 {@link #assertBillGeneratable} 分开：EXP_LOAN/EXP_REPAY 在审批回调里出证，此刻库内
     * 状态仍是 APPROVING（APPROVED 的回写在外层事务之后），故本口只负面排除死态，
     * 不套正面清单，避免把合法时序一并拦掉。
     */
    private void assertBillNotDead(ExpBill bill) {
        if (bill != null && bill.getStatus() != null
                && Set.of("VOID", "REJECTED", "WITHDRAWN", "TERMINATED").contains(bill.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单据 " + bill.getBillNo() + " 已作废或终断（" + bill.getStatus() + "），禁止生成凭证");
        }
    }

    public Map<Long, String> batchPost(Collection<Long> voucherIds, Long userId) {
        return batchPost(voucherIds, userId, false);
    }

    /**
     * 批量过账（与单张同一道制审分离校验）。
     *
     * @param systemInitiated 系统自动过账豁免，语义同 {@link #postSystem}；
     *                        仅供实付入账等无人工审核环节的路径显式声明，人工入口一律传 false。
     */
    public Map<Long, String> batchPost(Collection<Long> voucherIds, Long userId,
                                                 boolean systemInitiated) {
        if (voucherIds == null || voucherIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long id : voucherIds) {
            if (id == null) {
                result.put(id, "凭证ID为空");
                continue;
            }
            try {
                post(id, userId, systemInitiated);
                result.put(id, "OK");
            } catch (Exception ex) {
                String msg = ex.getMessage();
                msg = (msg == null || msg.isBlank()) ? ex.getClass().getSimpleName() : msg;
                // 批量记账逐张隔离：失败原因随结果返回；同时记日志便于服务端排查
                log.warn("批量记账失败 voucherId={}: {}", id, msg);
                result.put(id, msg);
            }
        }
        return result;
    }

    @DataScope("凭证列表数据范围")
    public PageResult<OpVoucher> page(long pageNum, long pageSize, String status, String sourceType) {
        return page(pageNum, pageSize, status, sourceType, null, null, null);
    }

    /**
     * 凭证台账的「公司／年度／月度」三个筛选条件。
     *
     * <p>重载只做加法：原 4 参签名不动（外部引用不改），参数全部可空，空＝不加条件。
     * <ul>
     *  <li>{@code companyId} 只作<b>收窄</b>——与 {@link DataScopeKit} 注入的 data_scope 五级条件是 AND，
     *     选了范围外的公司只会得到空列表，不会因此放宽可见性；</li>
     *  <li>{@code periodYear}/{@code periodMonth} 按 {@code op_voucher.period_year/period_month} 等值匹配，
     *     越界月份（如 0/13）自然零命中，不做猜测式纠正；</li>
     *  <li>数据范围校验 {@code @DataScope} 与 {@code DataScopeKit.apply} 的 AND 语义原样保留（本方法同样挂注解，
     *     控制器直接调它时照样过校验）。</li>
     * </ul>
     */
    @DataScope("凭证列表数据范围")
    public PageResult<OpVoucher> page(long pageNum, long pageSize, String status, String sourceType,
                                      Long companyId, Integer periodYear, Integer periodMonth) {
        return page(pageNum, pageSize, status, sourceType, companyId, periodYear, periodMonth, null, null);
    }

    /**
     * 同上，另加列头服务端排序。
     *
     * <p>列头若是裸 {@code sortable}，element-plus 只对「当前页 20 行」做前端排序，翻页即乱序。
     * {@code orderBy} 取前端列 prop（camelCase），<b>仅白名单列</b>映射到实体 getter
     * （{@link #sortColumnOf}），非白名单值忽略、回退 id 倒序；{@code order} 仅识别 desc（其余按 asc）。
     * 排序值不进任何 SQL 字面量拼接，无注入面。
     */
    @DataScope("凭证列表数据范围")
    public PageResult<OpVoucher> page(long pageNum, long pageSize, String status, String sourceType,
                                      Long companyId, Integer periodYear, Integer periodMonth,
                                      String orderBy, String order) {
        LambdaQueryWrapper<OpVoucher> qw = new LambdaQueryWrapper<>();
        // 过账状态/来源筛选必须后端绑定，否则筛选参数被忽略、只按 id 倒序，筛了等于没筛
        if (status != null && !status.isBlank()) {
            qw.eq(OpVoucher::getStatus, status.trim());
        }
        if (sourceType != null && !sourceType.isBlank()) {
            // 来源族支持逗号分隔（如对公付款族 EXPENSE_PAY,EXPENSE_SETTLE,TREASURY_PAY）→ IN 过滤，
            // 单值仍走 eq。值全部为参数绑定，无 SQL 拼接注入面
            List<String> sourceTypes = Arrays.stream(sourceType.trim().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (sourceTypes.size() > 1) {
                qw.in(OpVoucher::getSourceType, sourceTypes);
            } else if (sourceTypes.size() == 1) {
                qw.eq(OpVoucher::getSourceType, sourceTypes.get(0));
            }
        }
        // 公司/年度/月度三个筛选参数（null＝不参与过滤，保持原规则）
        if (companyId != null) {
            qw.eq(OpVoucher::getCompanyId, companyId);
        }
        if (periodYear != null) {
            qw.eq(OpVoucher::getPeriodYear, periodYear);
        }
        if (periodMonth != null) {
            qw.eq(OpVoucher::getPeriodMonth, periodMonth);
        }
        // 列头排序（白名单：凭证号 voucherNo / 摘要 summary；缺省 id 倒序＝历史规则）
        SFunction<OpVoucher, ?> sortCol = sortColumnOf(orderBy);
        if (sortCol != null) {
            boolean asc = !"desc".equalsIgnoreCase(order);
            qw.orderBy(true, asc, sortCol);
            // 排序列同值（如摘要）时以 id 倒序作稳定次级序，避免翻页出现重复/漂移
            qw.orderByDesc(OpVoucher::getId);
        } else {
            qw.orderByDesc(OpVoucher::getId);
        }
        // ③ data_scope 五级：依据登录人 data_scope 注入 公司/部门/本人 过滤（TENANT 不加条件）
        DataScopeKit.apply(qw, OpVoucher::getCompanyId, null, null);
        // 深翻页走两段式：先只查 id，再按 id 取整行。
        //   ① 只查 id：WHERE 列都在 idx_tenant_company_del_id(tenant_id,company_id,del_flag,id) 上，
        //      深翻只扫索引项、零回表；整行回表则要为 LIMIT 之前的所有行取全列，翻到页码上千时
        //      是秒级开销；
        //   ② 再按这一页的 id 取整行（selectBatchIds 走主键）。
        // 分页语义、total、排序（含 orderBy 白名单列）全部不变；records 按 id 序还原，
        // 因为 selectBatchIds 按主键返回、不保序。
        Page<OpVoucher> p = opVoucherMapper.selectPage(Page.of(pageNum, pageSize), qw.select(OpVoucher::getId));
        List<Long> pageIds = new ArrayList<>(p.getRecords().size());
        for (OpVoucher r : p.getRecords()) {
            if (r != null && r.getId() != null) {
                pageIds.add(r.getId());
            }
        }
        List<OpVoucher> records = new ArrayList<>(pageIds.size());
        if (!pageIds.isEmpty()) {
            Map<Long, OpVoucher> byId = new HashMap<>();
            for (OpVoucher v : opVoucherMapper.selectBatchIds(pageIds)) {
                byId.put(v.getId(), v);
            }
            for (Long id : pageIds) {
                OpVoucher v = byId.get(id);
                if (v != null) {
                    records.add(v);
                }
            }
        }
        p.setRecords(records);
        // 列表冗余来源单据号/状态（transient），便于逐张核对来源单据
        fillSourceBill(p.getRecords());
        fillCreator(p.getRecords());
        // 落账态一次聚合回填（glEntryCount）——整页一条 GROUP BY SQL，
        // 取代前端逐张 glApi.posted 的 N+1 串行探测（探测失败曾降级显示成「未落账」）
        fillGlEntryCount(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** orderBy 前端列 prop（camelCase）→ 实体列白名单；白名单外的值返回 null（回退默认 id 倒序）。 */
    private static SFunction<OpVoucher, ?> sortColumnOf(String orderBy) {
        if (orderBy == null) {
            return null;
        }
        return switch (orderBy.trim()) {
            case "voucherNo" -> OpVoucher::getVoucherNo;
            case "summary" -> OpVoucher::getSummary;
            default -> null;
        };
    }

    /**
     * 列表侧落账态批量回填——一页一次 SQL（op_gl_entry 按 voucher_id 分组计数），
     * 一次性给出整页「已落账（&gt;0）／未落账（0）」读数，取代页面逐张 glApi.posted /op/gl/posted
     * 的 N+1 串行探测（该探测失败还被降级显示成「未落账」）。
     * <p>聚合没回行的凭证显式落 0（＝库内无分录，与逐张探测得 0 行同义）；列表没带出本字段（null）
     * 才是「校验失败」态，由前端显式标注并可重试——绝不把探测失败降级显示成「未落账」。
     */
    private void fillGlEntryCount(List<OpVoucher> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (OpVoucher v : vouchers) {
            if (v != null && v.getId() != null) {
                ids.add(v.getId());
            }
        }
        Map<Long, Integer> countById = glService.countGlEntriesByVoucherIds(ids);
        for (OpVoucher v : vouchers) {
            if (v == null) {
                continue;
            }
            Integer cnt = countById.get(v.getId());
            v.setGlEntryCount(cnt == null ? 0 : cnt);
        }
    }

    /** 制单人姓名回填：op_voucher.create_by 走 TenantAuditBase 的 Long 规则（存用户 ID），列表需要人名 */
    private void fillCreator(List<OpVoucher> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        for (OpVoucher v : vouchers) {
            if (v.getCreateBy() != null) {
                userIds.add(v.getCreateBy());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = new HashMap<>();
        for (SysUser u : sysUserMapper.selectBatchIds(userIds)) {
            nameById.put(u.getId(), u.getNickName() == null || u.getNickName().isBlank()
                    ? u.getUserName() : u.getNickName());
        }
        for (OpVoucher v : vouchers) {
            v.setCreateByName(v.getCreateBy() == null ? null : nameById.get(v.getCreateBy()));
        }
    }

    /**
     * 对公结算计提应付凭证的来源类型（bill_id/source_doc_id 仍是 exp_bill 主键）。
     * <p>不复用 EXPENSE_PAY：批次出纳凭证按 {@code source_type='EXPENSE_PAY' + source_doc_id=billId}
     * 跨类型判重（{@code generateTreasuryPayVoucher}），复用会让结算单的付款环节永久不出证。
     */
    public static final String SOURCE_SETTLE = "EXPENSE_SETTLE";

    /**
     * bill_id 是跨域裸主键（合同凭证写 contractId、采购入库/应付确认写 po_order.id、资金调拨写 tr_transfer.id、
     * 期末类写各业务表自身主键），只有下列费用族来源的 bill_id 才是 exp_bill 主键。
     * 不加这道校验，非费用域凭证会拿同号的费用单号顶上（4 例串号）。
     * 同一规则也供出纳付款批次补充反向按 bill_id 查凭证使用（PayBatchService），避免两处集合各改一半。
     */
    public static final Set<String> EXPENSE_BILL_SOURCES = Set.of(
            "EXPENSE", "EXPENSE_PAY", SOURCE_SETTLE, "EXP_LOAN", "EXP_REPAY");

    /** 凭证列表冗余来源单据号/状态：仅费用族按 billId 查 exp_bill，其余来源不解析（列改显来源域） */
    private void fillSourceBill(List<OpVoucher> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return;
        }
        Set<Long> billIds = new HashSet<>();
        for (OpVoucher v : vouchers) {
            if (v.getBillId() != null && EXPENSE_BILL_SOURCES.contains(v.getSourceType())) {
                billIds.add(v.getBillId());
            }
        }
        if (billIds.isEmpty()) {
            return;
        }
        Map<Long, ExpBill> byId = new HashMap<>();
        for (ExpBill b : expBillMapper.selectBatchIds(billIds)) {
            byId.put(b.getId(), b);
        }
        for (OpVoucher v : vouchers) {
            ExpBill b = v.getBillId() == null || !EXPENSE_BILL_SOURCES.contains(v.getSourceType())
                    ? null : byId.get(v.getBillId());
            v.setSourceBillNo(b == null ? null : b.getBillNo());
            v.setSourceBillStatus(b == null ? null : b.getStatus());
        }
    }

    /**
     * 科目解析：行科目 → 费用项绑定科目 → 末级落地。
     * <p>两者都没有时直接报错并点名费用项与配置入口，不再静默兜底 6602——
     * 兜底会让「费用项未配科目」的单据悄悄记进管理费用，直到月结才发现科目错配。
     */
    private Long resolveSubjectId(Long lineSubjectId, MdExpenseItem item, Long companyId) {
        return postableSubjectResolver.requireExpenseSubject(item, lineSubjectId, companyId).getId();
    }

    /**
     * 结算单整体一笔计提时的借方科目：要求所有非零费用行指向同一费用项。
     * 行合计与应付口径不一致时借方只能出一笔，多费用项混杂就无法确定科目，宁可不生成也不能猜。
     */
    private Long singleExpenseSubjectOf(List<ExpBillLine> lines, ExpBill bill) {
        Long itemId = null;
        for (ExpBillLine l : lines) {
            if (l.getAmount() == null || l.getAmount().signum() == 0) continue;
            if (itemId == null) {
                itemId = l.getItemId();
            } else if (!itemId.equals(l.getItemId())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "对公结算单 " + bill.getBillNo()
                                + " 费用行合计与应付金额不一致，且含多个费用项目，无法确定借方会计科目；请核对费用行金额后重新生成");
            }
        }
        if (itemId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "对公结算单 " + bill.getBillNo() + " 缺少费用行，无法确定借方会计科目；请补录费用行后重新生成");
        }
        return postableSubjectResolver.requireExpenseSubject(
                mdExpenseItemMapper.selectById(itemId), null, bill.getCompanyId()).getId();
    }

    /** 按科目编码查 id（无则 null） */
    private Long findSubjectIdByCode(String code) {
        List<MdSubject> list = mdSubjectMapper.selectList(
                new LambdaQueryWrapper<MdSubject>().eq(MdSubject::getSubjectCode, code).last("limit 1"));
        return list.isEmpty() ? null : list.get(0).getId();
    }

    /** 制证科目落末级（口径见 {@link PostableSubjectResolver}）；科目不存在时原样返回 null */
    private MdSubject postableSubject(Long subjectId, String codeForMsg) {
        if (subjectId == null) {
            return null;
        }
        return postableSubjectResolver.resolve(mdSubjectMapper.selectById(subjectId), codeForMsg);
    }

    /**
     * 科目坐标同源：凭证行上的 {@code subject_code}/{@code subject_name}
     * 一律取<b>主数据解析结果</b>，不再写方法体内的字面量。
     * <p>同一行上的三个值必须互相自洽。{@code subject_id} 指向的科目与
     * {@code subject_code} 字面量一旦不同（例如 id 指向 1122、编码却写 1131），
     * 按日志按编码对账的运维就会落到一个主数据里并不存在的科目上，错一整条科目。
     * 主数据查不到时回落字面量——出证行为与未取主数据时一致，
     * 科目缺失由过账侧的「末级科目／科目存在」校验拦（{@link #post}）。
     *
     * @return 长度为 2 的数组：[0]=科目编码，[1]=科目名称
     */
    private String[] subjectCoords(Long subjectId, String fallbackCode, String fallbackName) {
        if (subjectId == null) {
            return new String[]{fallbackCode, pickNotEmpty(fallbackName, fallbackCode)};
        }
        MdSubject s = mdSubjectMapper.selectById(subjectId);
        if (s == null || s.getSubjectCode() == null || s.getSubjectCode().isBlank()) {
            return new String[]{fallbackCode, pickNotEmpty(fallbackName, fallbackCode)};
        }
        return new String[]{s.getSubjectCode(),
                pickNotEmpty(s.getSubjectName(), pickNotEmpty(fallbackName, s.getSubjectCode()))};
    }

    /**
     * 凭证流程回链：按「来源单据域 + 来源单据主键」只读回查 wf_instance，取最近一条的
     * {@code proc_inst_id}。用于<b>派生单据的出证环节</b>——凭证的直接对象是新建的派生单（应收单/应付确认），
     * 而它自己没有流程实例，审批链挂在<b>源单据</b>上（开票单／付款单），于是按源单据联查永远取不到凭证。
     * <p>单据级凭证回链的就是<b>触发它的那条源单据流</b>，不是猜一条别的链。
     * 查不到、或解析异常一律返回 null（无实例时留 NULL，不猜测归属）。
     */
    private String resolveProcInstIdOf(Long bizId, String... bizTypes) {
        if (bizId == null || bizTypes == null || bizTypes.length == 0) {
            return null;
        }
        try {
            List<WfInstance> hits = wfInstanceMapper.selectList(
                    new LambdaQueryWrapper<WfInstance>()
                            .eq(WfInstance::getBizId, bizId)
                            .in(WfInstance::getBizType, Arrays.asList(bizTypes))
                            .orderByDesc(WfInstance::getId));
            return hits.isEmpty() ? null : hits.get(0).getProcInstId();
        } catch (Exception ex) {
            log.warn("回查源单据 {}#{} 的流程实例号失败（凭证流程回链留空，不影响出证）: {}",
                    Arrays.toString(bizTypes), bizId, ex.getMessage());
            return null;
        }
    }

    /**
     * 应收单是否为「开票审批派生」——判据只用应收单自己登记的来源列
     * （{@code rc_receivable.source_doc_type/source_doc_id}，由开票收尾环节写入），不按单号格式猜。
     */
    private static final String SOURCE_INVOICE_ISSUE = "INVOICE_ISSUE";

    /** 只读取应收单派生自的那张发票（非开票派生／查不到 ⇒ null，凭证退回全额单环节的既有行为） */
    private InvInvoice sourceInvoiceOf(RcReceivable rec) {
        if (rec == null || !SOURCE_INVOICE_ISSUE.equals(rec.getSourceDocType()) || rec.getSourceDocId() == null) {
            return null;
        }
        try {
            InvInvoice inv = invoiceFacade.findById(rec.getSourceDocId());
            if (inv == null || (inv.getDelFlag() != null && inv.getDelFlag() == 1)) {
                return null;
            }
            return inv;
        } catch (Exception ex) {
            log.warn("应收单 {} 回查来源发票 {} 失败，收入凭证按全额单腿出证: {}",
                    rec.getBillNo(), rec.getSourceDocId(), ex.getMessage());
            return null;
        }
    }

    /**
     * PO 域凭证的「付款单侧坐标」——{@code bill_id} 是 exp_bill 规则的单据外键
     * （见 {@link #EXPENSE_BILL_SOURCES} 与），只有按 {@code exp_bill.po_id} 反查<b>唯一命中</b>
     * 时才认得出这张 PO 对应的付款单；多条或零条一律 null（不猜，维持原坐标）。
     */
    private ExpBill uniquePayBillOfPo(Long poId) {
        if (poId == null) {
            return null;
        }
        try {
            List<ExpBill> hits = expBillMapper.selectList(
                    new LambdaQueryWrapper<ExpBill>()
                            .eq(ExpBill::getPoId, poId)
                            .eq(ExpBill::getDelFlag, 0));
            return hits.size() == 1 ? hits.get(0) : null;
        } catch (Exception ex) {
            log.warn("按采购订单 {} 反查付款单失败（凭证 bill_id 保持原坐标）: {}", poId, ex.getMessage());
            return null;
        }
    }

    /**
     * 非费用域制证失败的结构化记录：这些分支历来只有一条 {@code log.*}（跑在服务端日志里，
     * 界面看不见、点数说不清）。本方法<b>只加记录、不改控制流</b>——原有的 warn/info/error 一律保留，
     * 原有的抛错分支<b>不接这里</b>：出证环节带 {@code REQUIRES_NEW}，抛错时整个内层事务回滚，
     * 里写的 audit_trail 一起消失（{@link AuditTrailService} 自身无事务传播），
     * 那类失败的结构化记录只能在调用方的 catch 里补（已列移交项）。
     * <p>因此本方法只服务于<b>吞掉异常／静默跳过</b>这两类"库没写、事务照提交"的分支。
     * <p>载体复用既存结构化面 {@code audit_trail}（{@link AuditTrailService} 已注入，
     * 且其写入失败只 warn、绝不反噬出证）；费用域视图（{@code VoucherGenFailureService}）覆盖不到非费用域，
     * 本记录与之互补，不冒充它的规则。
     *
     * @param voucherGenerated 凭证本身是否已经出：{@code false}＝整张未出证；{@code true}＝凭证出了但<b>缺一个环节</b>
     *                         如价税分离环节未拆，如实分开记，免把"部分出证"点成"零出证"
     */
    private void recordGenFailureTrace(String sourceType, Long sourceDocId, String bizNo,
                                       boolean voucherGenerated, String reason) {
        try {
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("voucherGenerated", voucherGenerated);
            after.put("sourceType", sourceType);
            after.put("sourceDocId", sourceDocId);
            after.put("reason", reason);
            auditTrailService.record(sourceType, sourceDocId, bizNo, "op_voucher", null,
                    voucherGenerated ? "VOUCHER_LEG_MISSING" : "VOUCHER_GEN_FAIL", null,
                    AUX_OM.writeValueAsString(after),
                    (voucherGenerated ? "自动生成凭证缺少分录：" : "自动生成凭证未出证：") + reason);
        } catch (Exception ex) {
            log.warn("制证失败留痕写入失败 sourceType={} sourceDocId={}: {}",
                    sourceType, sourceDocId, ex.getMessage());
        }
    }

    /** 整张凭证未出证（{@code voucherGenerated=false}）的简写入口。 */
    private void recordGenFailureTrace(String sourceType, Long sourceDocId, String bizNo, String reason) {
        recordGenFailureTrace(sourceType, sourceDocId, bizNo, false, reason);
    }

    /**
     * 凭证分录查询（① 修 lines(id) IDOR）：先取凭证头校验公司可见性，
     * 非本公司且非集团级账号一律拒绝（防止凭 voucherId 遍历他公司分录）。
     */
    public List<OpVoucherLine> lines(Long voucherId) {
        OpVoucher v = opVoucherMapper.selectById(voucherId);
        if (v == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证不存在: " + voucherId);
        }
        orgGuard.assertVisible(v.getCompanyId(), null);
        return opVoucherLineMapper.selectByVoucherId(voucherId);
    }

    /**
     * 工单处理（.3）：修改凭证分录（科目/金额/方向/辅助信息），凭证未记账才可改
     *
     * <p>本方法是"先删全部分录→逐行插入→回写表头合计→记录"四段写，缺事务边界时中途失败会留下
     * <b>分录被清空却插了一半</b>的凭证（比"头有行无"更糟：表头合计与行合计不再勾稽，却能过账）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateLines(Long voucherId, List<OpVoucherLine> lines) {
        OpVoucher voucher = opVoucherMapper.selectById(voucherId);
        if (voucher == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证不存在: " + voucherId);
        }
        orgGuard.assertVisible(voucher.getCompanyId(), null); // ① 写路径公司归属校验
        if ("POSTED".equals(voucher.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已记账凭证不能修改分录");
        }
        // 期间锁定：凭证期间已结账则禁止修改分录
        if (voucher.getCompanyId() != null && voucher.getPeriodYear() != null && voucher.getPeriodMonth() != null) {
            periodService.assertOpen(voucher.getCompanyId(), voucher.getPeriodYear(), voucher.getPeriodMonth());
        }
        if (lines == null || lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分录不能为空");
        }
        // 校验借贷平衡
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        for (OpVoucherLine l : lines) {
            if (l.getSubjectId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "分录科目不能为空");
            }
            // 科目末级强校验：仅末级科目可记账（防挂非末级导致明细汇总失真）
            MdSubject subj = mdSubjectMapper.selectById(l.getSubjectId());
            if (subj == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "科目不存在: " + l.getSubjectId());
            }
            if (subj.getIsLeaf() != null && subj.getIsLeaf() == 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "科目 " + subj.getSubjectCode() + " " + subj.getSubjectName() + " 非末级科目，禁止记账");
            }
            // 辅助核算强校验：科目要求辅助项时，分录对应字段必须带出
            assertAux(subj, l);
            BigDecimal amt = l.getAmount() == null ? BigDecimal.ZERO : l.getAmount();
            if ("DEBIT".equals(l.getDirection())) debit = debit.add(amt);
            else credit = credit.add(amt);
        }
        if (debit.compareTo(credit) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "借贷不平衡: 借 " + debit + " / 贷 " + credit);
        }
        // 审计扩面（D 类·凭证修改）：分录整体替换，原先先采集旧分录明细
        List<Map<String, Object>> auditBeforeLines = lineSnapshot(opVoucherLineMapper.selectByVoucherId(voucherId));
        // 先删后插（分录行整体替换）；自定义 @Insert 不走 MetaObjectHandler，tenantId 必须显式 set
        opVoucherLineMapper.deleteByVoucherId(voucherId);
        Long tenantId = TenantContext.getTenantId();
        int seq = 10;
        for (OpVoucherLine l : lines) {
            l.setId(null);
            l.setVoucherId(voucherId);
            l.setTenantId(tenantId);
            if (l.getLineNo() == null) l.setLineNo(seq);
            seq += 10;
            opVoucherLineMapper.insert(l);
        }
        // #11 回写头行：借贷合计 + 平衡标记
        OpVoucher vh = new OpVoucher();
        vh.setId(voucherId);
        vh.setDebitTotal(debit);
        vh.setCreditTotal(credit);
        vh.setIsBalanced(1);
        opVoucherMapper.updateById(vh);
        // 审计扩面（D 类·凭证修改）：记变更的凭证明细字段（before=旧分录 / after=新分录）
        auditTrailService.recordUpdate("VOUCHER", voucherId, voucher.getVoucherNo(), "op_voucher",
                String.valueOf(voucherId), auditBeforeLines, lineSnapshot(lines),
                "修改凭证分录（借贷合计 借 " + debit + " / 贷 " + credit + "）");
    }

    // 凭证断号检测

    /**
     * 凭证断号检测：按 期间+凭证字 分析凭证号重复与缺失。
     * 凭证号如含数字序号（"记-000123"）则做连续性检查；纯时间戳号仅查重复。
     */
    public Map<String, Object> gapCheck(Integer periodYear, Integer periodMonth) {
        List<OpVoucher> list = opVoucherMapper.selectList(
                new LambdaQueryWrapper<OpVoucher>()
                        .eq(OpVoucher::getPeriodYear, periodYear)
                        .eq(OpVoucher::getPeriodMonth, periodMonth)
                        .orderByAsc(OpVoucher::getVoucherNo));
        Map<String, List<String>> byWord = new LinkedHashMap<>();
        for (OpVoucher v : list) {
            byWord.computeIfAbsent(v.getVoucherWord() == null ? "" : v.getVoucherWord(),
                    k -> new ArrayList<>()).add(v.getVoucherNo());
        }
        List<Map<String, Object>> issues = new ArrayList<>();
        for (var e : byWord.entrySet()) {
            List<String> nos = e.getValue();
            // 重复检测
            Map<String, Integer> cnt = new HashMap<>();
            for (String n : nos) cnt.merge(n, 1, Integer::sum);
            for (var c : cnt.entrySet()) {
                if (c.getValue() > 1) {
                    issues.add(Map.of("word", e.getKey(), "type", "DUPLICATE",
                            "detail", "凭证号重复 " + c.getKey() + " × " + c.getValue()));
                }
            }
            // 连续序号检测（仅当可提查询字序号；时间戳号如 13 位毫秒无连续性意义，跳过）
            List<Long> seqs = new ArrayList<>();
            Matcher m = Pattern.compile("(\\d+)").matcher(String.join(",", nos));
            while (m.find()) {
                String num = m.group(1);
                if (num.length() > 8) continue; // 超长数字（毫秒时间戳等）跳过连续检测
                seqs.add(Long.parseLong(num));
            }
            if (seqs.size() >= 2 && seqs.size() == nos.size()) {
                Collections.sort(seqs);
                for (int i = 1; i < seqs.size(); i++) {
                    if (seqs.get(i) - seqs.get(i - 1) > 1) {
                        issues.add(Map.of("word", e.getKey(), "type", "GAP",
                                "detail", "凭证号缺失 " + (seqs.get(i - 1) + 1) + " ~ " + (seqs.get(i) - 1)));
                    }
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("period", (periodYear == null ? "" : periodYear) + (periodMonth == null ? "" : String.format("%02d", periodMonth == null ? 0 : periodMonth)));
        result.put("total", list.size());
        result.put("issues", issues);
        return result;
    }

    // 模板驱动生成

    /**
     * 基于模板驱动的凭证生成方法。
     * 1. 根据billId查询ExpBill
     * 2. 调用VoucherTemplateService.resolveTemplate获取匹配模板
     * 3. 如果无模板则回退到原有generate()逻辑
     * 4. 根据模板配置生成凭证
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateByTemplate(Long billId, String sourceType) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在: " + billId);
        }
        // 模板口（EXP_LOAN/EXP_REPAY 审批回调，库内或仍 APPROVING）只拦死态，
        // 不套正面清单，确保合法出证时序不受影响
        assertBillNotDead(bill);

        // 获取匹配模板
        VoucherTemplate template = voucherTemplateService.resolveTemplate(sourceType, bill.getTotalAmount());
        if (template == null) {
            log.info("无匹配模板，回退到原有generate逻辑: billId={}, sourceType={}", billId, sourceType);
            return generate(bill);
        }

        // 期间结账校验
        if (bill.getCompanyId() != null && bill.getPeriodYear() != null && bill.getPeriodMonth() != null) {
            periodService.assertOpen(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        }

        LocalDate today = landingDate(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        OpVoucher v = new OpVoucher();
        v.setTenantId(TenantContext.getTenantId());
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(bill.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(bill.getId());
        v.setSourceType(sourceType);
        v.setSourceDocType(sourceType);
        v.setSourceDocId(bill.getId());
        v.setProcInstId(bill.getProcInstId());
        // 摘要 = 替换模板中的变量
        String summary = template.getSummaryTemplate();
        if (summary != null) {
            summary = summary.replace("{billTitle}", payeeNameOrTitle(bill));
        } else {
            summary = payeeNameOrTitle(bill);
        }
        v.setSummary(summary);
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        Long tenantId = TenantContext.getTenantId();
        BigDecimal amount = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;

        // 模板分录必须回填 subject_id：op_voucher_line.subject_id 是 NOT NULL 列，
        // 而模板只存科目编码/名称，必须按编码反查主数据主键。
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10, summary,
                findSubjectIdByCode(template.getDebitSubjectCode()),
                template.getDebitSubjectCode(), template.getDebitSubjectName(),
                "DEBIT", amount, bill.getDeptId()));

        // 贷方分录
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20, summary,
                findSubjectIdByCode(template.getCreditSubjectCode()),
                template.getCreditSubjectCode(), template.getCreditSubjectName(),
                "CREDIT", amount, null));

        // 更新凭证借贷合计
        OpVoucher update = new OpVoucher();
        update.setId(v.getId());
        update.setDebitTotal(amount);
        update.setCreditTotal(amount);
        update.setIsBalanced(1);
        opVoucherMapper.updateById(update);

        // 回写单据凭证号
        ExpBill billUpdate = new ExpBill();
        billUpdate.setId(bill.getId());
        billUpdate.setVoucherNo(v.getVoucherNo());
        billUpdate.setVoucherDate(today);
        expBillMapper.updateById(billUpdate);

        return v.getId();
    }

    /**
     * 按合同生成凭证。
     * 查询合同，使用sourceType="CONTRACT"的模板，借方=预付账款/费用科目，贷方=应付账款。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateByContract(Long contractId) {
        return generateByContract(contractId, null);
    }

    /**
     * 审批收尾环节把流程实例号带进来，回填凭证 {@code proc_inst_id} 回溯列。
     * 该列不写，追溯链的「proc_inst_id」与「别名」两条取数臂会同时落空，凭证就成了断头。
     * {@code procInstId} 为空（如生效直调通道）保持原行为留 NULL，不猜测归属。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateByContract(Long contractId, String procInstId) {
        ContContract contract = contContractMapper.selectById(contractId);
        if (contract == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "合同不存在: " + contractId);
        }

        String sourceType = "CONTRACT";
        BigDecimal amount = contract.getAmount() != null ? contract.getAmount() : BigDecimal.ZERO;

        VoucherTemplate template = voucherTemplateService.resolveTemplate(sourceType, amount);

        // 构建一个模拟的ExpBill用于generateByTemplate
        ExpBill bill = new ExpBill();
        bill.setId(contractId);
        bill.setTitle(contract.getContractName());
        bill.setCompanyId(contract.getCompanyId());
        bill.setDeptId(contract.getDeptId());
        bill.setTotalAmount(amount);
        if (procInstId != null && !procInstId.isBlank()) {
            bill.setProcInstId(procInstId.trim());
        }
        // 模拟单据的期间取落期真值（当期已关则前滚到首个开账期间），不再裸写 LocalDate.now()
        LocalDate land = landingDate(contract.getCompanyId(), null, null);
        bill.setPeriodYear(land.getYear());
        bill.setPeriodMonth(land.getMonthValue());

        if (template == null) {
            // 无模板时使用默认科目：借方=预付账款，贷方=应付账款
            log.info("合同模板未匹配，使用默认科目生成凭证: contractId={}", contractId);
            return generateByTemplateWithDefault(bill, sourceType, contract);
        }

        return generateByTemplate(bill, sourceType, template, contract);
    }

    /** 合同凭证生成：使用匹配模板 */
    private Long generateByTemplate(ExpBill bill, String sourceType, VoucherTemplate template, ContContract contract) {
        LocalDate today = landingDate(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        OpVoucher v = new OpVoucher();
        v.setTenantId(TenantContext.getTenantId());
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(bill.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(bill.getId());
        v.setSourceType(sourceType);
        v.setSourceDocType(sourceType);
        v.setSourceDocId(bill.getId());
        // 合同伪单据无流程实例时自然留 NULL，不猜测归属（宁缺勿错）
        v.setProcInstId(bill.getProcInstId());
        String summary = template.getSummaryTemplate();
        if (summary != null) {
            summary = summary.replace("{billTitle}", payeeNameOrTitle(bill));
        } else {
            summary = payeeNameOrTitle(bill);
        }
        v.setSummary(summary);
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        Long tenantId = TenantContext.getTenantId();
        BigDecimal amount = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;

        // 借方分录：预付账款/费用科目 + 供应商辅助核算
        OpVoucherLine debitLine = buildLine(tenantId, v.getId(), 10, summary,
                findSubjectIdByCode(template.getDebitSubjectCode()),
                template.getDebitSubjectCode(), template.getDebitSubjectName(),
                "DEBIT", amount, null);
        debitLine.setSupplierId(contract.getCustomerId());
        opVoucherLineMapper.insert(debitLine);

        // 贷方分录：应付账款 + 供应商辅助核算
        OpVoucherLine creditLine = buildLine(tenantId, v.getId(), 20, summary,
                findSubjectIdByCode(template.getCreditSubjectCode()),
                template.getCreditSubjectCode(), template.getCreditSubjectName(),
                "CREDIT", amount, null);
        creditLine.setSupplierId(contract.getCustomerId());
        opVoucherLineMapper.insert(creditLine);

        updateVoucherTotals(v.getId(), amount);
        return v.getId();
    }

    /** 合同凭证生成：使用默认科目（无模板回退） */
    private Long generateByTemplateWithDefault(ExpBill bill, String sourceType, ContContract contract) {
        LocalDate today = landingDate(bill.getCompanyId(), bill.getPeriodYear(), bill.getPeriodMonth());
        OpVoucher v = new OpVoucher();
        v.setTenantId(TenantContext.getTenantId());
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(bill.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(bill.getId());
        v.setSourceType(sourceType);
        v.setSourceDocType(sourceType);
        v.setSourceDocId(bill.getId());
        v.setProcInstId(bill.getProcInstId());
        // #3b：合同伪单据无收款人，回落标题（＝合同名）行为与原先逐字一致
        v.setSummary(payeeNameOrTitle(bill));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        Long tenantId = TenantContext.getTenantId();
        BigDecimal amount = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;

        // 借方：预付账款（1123）
        OpVoucherLine debitLine = buildLine(tenantId, v.getId(), 10, "预付账款-" + contract.getContractName(),
                findSubjectIdByCode("1123"), "1123", "预付账款", "DEBIT", amount, null);
        debitLine.setSupplierId(contract.getCustomerId());
        opVoucherLineMapper.insert(debitLine);

        // 贷方：应付账款（2202）
        OpVoucherLine creditLine = buildLine(tenantId, v.getId(), 20, "应付账款-" + contract.getContractName(),
                findSubjectIdByCode("2202"), "2202", "应付账款", "CREDIT", amount, null);
        creditLine.setSupplierId(contract.getCustomerId());
        opVoucherLineMapper.insert(creditLine);

        updateVoucherTotals(v.getId(), amount);
        return v.getId();
    }

    /** 更新凭证借贷合计 */
    private void updateVoucherTotals(Long voucherId, BigDecimal amount) {
        OpVoucher update = new OpVoucher();
        update.setId(voucherId);
        update.setDebitTotal(amount);
        update.setCreditTotal(amount);
        update.setIsBalanced(1);
        opVoucherMapper.updateById(update);
    }

    // 多业务域凭证生成

    /**
     * 采购入库凭证。
     * 借：库存商品(1405) 贷：应付账款(2202)，金额取 po_order.amount。
     * source_type = PURCHASE
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generatePurchaseVoucher(PoOrder order) {
        if (order == null || order.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在");
        }
        BigDecimal amt = order.getAmount() == null ? BigDecimal.ZERO : order.getAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单金额为 0，无法生成凭证");
        }
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(order.getCompanyId(), null, null);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(order.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(order.getId());
        v.setSourceType("PURCHASE");
        v.setSourceDocType("PURCHASE");
        v.setSourceDocId(order.getId());
        v.setSummary("采购入库-" + (order.getPoNo() == null ? "" : order.getPoNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        Long inventorySubjectId = findSubjectIdByCode("1405");
        Long payableSubjectId = findSubjectIdByCode("2202");
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "库存商品-采购入库", inventorySubjectId, "1405", "库存商品", "DEBIT", amt, null));
        OpVoucherLine creditLine = buildLine(tenantId, v.getId(), 20,
                "应付账款-采购", payableSubjectId, "2202", "应付账款", "CREDIT", amt, null);
        creditLine.setSupplierId(order.getSupplierId());
        opVoucherLineMapper.insert(creditLine);

        updateVoucherTotals(v.getId(), amt);
        log.info("采购订单 {} 生成入库凭证 {} 借库存商品({})/贷应付账款({}) 金额={}",
                order.getPoNo(), v.getVoucherNo(), "1405", "2202", amt);
        return v.getId();
    }

    /**
     * 应收确认凭证（开票派生场景见同名重载）。
     * 借：应收账款(1122) 贷：主营业务收入(6001)，金额取 rc_receivable.amount。
     * source_type = RECEIVABLE
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateReceivableVoucher(RcReceivable rec) {
        return generateReceivableVoucher(rec, null);
    }

    /**
     * 应收确认凭证。
     *
     * <p><b>联查断在「单据→凭证」</b>：开票审批的收尾环节先新建应收单、再就地出证，
     * 凭证四键里 {@code source_type/source_doc_type/source_doc_id/bill_id} 全是<b>派生应收单</b>的坐标，
     * 而开票单抽屉按 {@code INVOICE_ISSUE + inv_invoice.id} 查询 ⇒ 发票抽屉永远「记账凭证 0 张」，
     * 展开体文案「无凭证（未审核通过或非凭证域单据）」两个判据同时为假。
     * 那四键的归属不能改（应收才是这笔收入确认的入账对象，应收侧联查也按它查询），
     * 要补的是联查已有的第二条环节：{@code proc_inst_id} 回链<b>触发出证的那条源单据流</b>（＝开票审批流），
     * 载体是为此增设的列；发票侧与应收侧两条臂由此同时命中。
     *
     * <p><b>价税未分离</b>：全额贷 6001 会让主营业务收入虚增一个税额、销项税零入账，
     * 且两侧同取全额 ⇒ {@code is_balanced} 恒为 1、掩盖问题。按<b>票面真值</b>拆两环节
     * （贷 6001＝{@code net_amount}／贷 应交税费＝{@code tax_amount}），三条硬约束保证不外溢：
     * ①只有应收单自己登记了派生来源（{@code source_doc_type=INVOICE_ISSUE}）才回查发票；
     * ②票面 {@code 未税+税额} 与应收金额<b>分文不差</b>才拆（对不上就按原样全额出证并记录）；
     * ③销项科目在主数据真实现有才拆（优先 {@code 2221} 下的销项税明细科目；库内通常只有
     * {@code 2221 应交税费} 这一条末级可过账科目，即落它）。非开票派生的应收单行为逐字不变。
     *
     * <p><b>借方科目码不能写死</b>：{@code md_subject} 里应收账款是 1122，没有 1131 这条编码。
     * 编码与名称一律取解析结果（{@link #subjectCoords}），日志同源——否则同一行的
     * id、编码、日志会指向三个不同的值，按编码对账的运维直接错一整条科目。
     * 核销环节 {@link #generateCollectVoucher} 只错日志、不落库，本方法才是唯一写库的错位点。
     *
     * @param procInstId 触发本凭证的流程实例号；调用方（开票／应收收尾环节）手头有最终状态事件时<b>显式传入</b>，
     *                   传 null 则由本法按「{@code INVOICE_ISSUE} + 发票主键」只读回查补链；
     *                   仍查不到＝留 NULL（宁缺勿错关联）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateReceivableVoucher(RcReceivable rec, String procInstId) {
        if (rec == null || rec.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "应收单不存在");
        }
        BigDecimal amt = rec.getAmount() == null ? BigDecimal.ZERO : rec.getAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "应收金额为 0，无法生成凭证");
        }
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(rec.getCompanyId(), null, null);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(rec.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(rec.getId());
        v.setSourceType("RECEIVABLE");
        v.setSourceDocType("RECEIVABLE");
        v.setSourceDocId(rec.getId());
        // 回链触发本次出证的源单据流（开票审批流），使发票抽屉与应收抽屉都能取到这张凭证
        v.setProcInstId(procInstId != null && !procInstId.isBlank() ? procInstId.trim()
                : resolveProcInstIdOf(rec.getSourceDocId(), SOURCE_INVOICE_ISSUE));
        v.setSummary("应收确认-" + (rec.getBillNo() == null ? "" : rec.getBillNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        Long arSubjectId = findSubjectIdByCode("1122");
        String[] ar = subjectCoords(arSubjectId, "1122", "应收账款");           // 真实科目码，不写 1131
        Long revenueSubjectId = findSubjectIdByCode("6001");
        String[] rev = subjectCoords(revenueSubjectId, "6001", "主营业务收入");
        OpVoucherLine drLine = buildLine(tenantId, v.getId(), 10,
                "应收账款-确认", arSubjectId, ar[0], ar[1], "DEBIT", amt, null);
        drLine.setCustomerId(rec.getCustomerId());
        stampAuxJson(drLine);   // 辅助维度连同键名落 aux_json
        opVoucherLineMapper.insert(drLine);

        // 价税分离环节——只有票面「未税＋税额＝应收金额」分文不差、且销项科目现有才拆
        BigDecimal[] vat = splitVatOf(rec, amt);
        BigDecimal tax = vat == null ? null : vat[0];
        BigDecimal net = vat == null ? null : vat[1];
        MdSubject taxSubject = vat == null ? null : findSubjectByAnyCode("22210101", "22210102", "222101", "2221");
        if (tax != null && taxSubject != null) {
            String[] taxCoords = subjectCoords(taxSubject.getId(), "2221", "应交税费");
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                    "主营业务收入", revenueSubjectId, rev[0], rev[1], "CREDIT", net, null));
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 30,
                    taxCoords[1] + "-销项税额", taxSubject.getId(), taxCoords[0], taxCoords[1], "CREDIT", tax, null));
        } else {
            if (tax != null) {
                log.warn("应收单 {} 票面税额 {} 可拆，但销项税科目（2221 应交税费）不在册，收入凭证退回全额单腿（贷 {} {}）",
                        rec.getBillNo(), tax, rev[0], rev[1]);
                recordGenFailureTrace("RECEIVABLE", rec.getId(), rec.getBillNo(), true,
                        "价税分开入账未完成：销项税科目（2221 应交税费/销项税明细）不在册，主营业务收入按全额 " + amt + " 入账");
            }
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                    "主营业务收入", revenueSubjectId, rev[0], rev[1], "CREDIT", amt, null));
        }

        updateVoucherTotals(v.getId(), amt);
        log.info("应收单 {} 生成确认凭证 {} 借{}({})/贷{}({}){} 金额={}",
                rec.getBillNo(), v.getVoucherNo(), ar[1], ar[0], rev[1], rev[0],
                tax == null || taxSubject == null ? "" : "/贷销项税(" + subjectCoords(taxSubject.getId(), "2221", "应交税费")[0] + " " + tax + ")",
                amt);
        return v.getId();
    }

    /**
     * 从票面取价税拆分值——{@code [0]=税额, [1]=未税}；不满足拆分条件时返回 null（调用方按全额单环节出证）。
     * <p>判据全部来自写入数据库事实：应收单自己登记的派生来源 + 发票自己的 net/tax 列 + 与应收金额的分文不差。
     */
    private BigDecimal[] splitVatOf(RcReceivable rec, BigDecimal amt) {
        InvInvoice inv = sourceInvoiceOf(rec);
        if (inv == null) {
            return null;
        }
        BigDecimal tax = inv.getTaxAmount();
        BigDecimal net = inv.getNetAmount();
        if (tax == null || net == null || tax.signum() <= 0 || net.signum() <= 0
                || net.add(tax).compareTo(amt) != 0) {
            if (tax != null && tax.signum() > 0) {
                log.warn("应收单 {} 的来源发票 {} 价税（未税 {}＋税额 {}）与应收金额 {} 不等，收入凭证按全额单腿出证",
                        rec.getBillNo(), inv.getInvoiceNo(), net, tax, amt);
            }
            return null;
        }
        return new BigDecimal[]{tax, net};
    }


    /**
     * 收款核销凭证。
     * 借：银行存款 贷：应收账款，金额取<b>本次核销金额</b>；两环节科目码一律取主数据真码。
     * source_type = RECEIVABLE_COLLECT
     *
     * <p>核销额由调用方（收款认领收尾环节）按应收余额算好传入，本方法只负责出证。
     * 本方法不能自己去取 {@code rec.getAmount()}：那是票面全额，部分收款或事件重放都会把
     * 出证金额放大到全额一次。
     *
     * <p>同一应收单已有未作废凭证时不再重复出证（与 {@code generateTreasuryPayVoucher} 同一判重口径）。
     * 本域与到账环节（{@link #generateReceiptVoucher}）科目方向相同，审批事件重放或两个环节并行
     * 都会留下「同笔两份借银行存款」。
     *
     * <p>日志必须与写库同源：编码经 {@link #subjectCoords} 从 {@code md_subject} 解析出来，
     * 写库与打印共用同一份读数。否则会出现库里是 1122、日志打 1131 这种错位，
     * 按日志对账会错一整条科目；主数据改码时也不会再出现两套规则。
     *
     * <p>出证前必须先在 {@code rc_receipt}/{@code rc_receipt_line} 里查到挂本应收单的到账记录
     * （{@link #hasArrivalCarrier}），否则抛业务异常「尚无到账记录，不能生成核销凭证」。
     * 少了这道闸，钱还没到账就凭空记进银行存款。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateCollectVoucher(RcReceivable rec, BigDecimal collectAmount) {
        if (rec == null || rec.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "应收单不存在");
        }
        Long existed = existingVoucherId("RECEIVABLE_COLLECT", rec.getId());
        if (existed != null) {
            log.warn("应收单 {} 已存在收款核销凭证 id={}，跳过重复生成（幂等）", rec.getBillNo(), existed);
            return existed;
        }
        BigDecimal amt = collectAmount == null ? BigDecimal.ZERO : collectAmount;
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "本次核销金额为 0，无法生成凭证");
        }
        // 先有到账载体，再出「借 1002／贷 1122」。闸只校验本次新出证，不回改历史凭证；
        // 已存在的同源凭证由上面的去重环节原样返回。调用方（收款认领收尾环节）已 try/catch 本异常，
        // 拒出证只落一条业务原因，不会把审批回调变成 500。
        if (!hasArrivalCarrier(rec.getId())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "应收单 " + (rec.getBillNo() == null ? rec.getId() : rec.getBillNo())
                            + " 尚无到账记录，不能生成核销凭证；请先登记收款核销单（到账载体）后再出证");
        }
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(rec.getCompanyId(), null, null);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(rec.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(rec.getId());
        v.setSourceType("RECEIVABLE_COLLECT");
        v.setSourceDocType("RECEIVABLE_COLLECT");
        v.setSourceDocId(rec.getId());
        v.setSummary("收款核销-" + (rec.getBillNo() == null ? "" : rec.getBillNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        Long bankSubjectId = findSubjectIdByCode("1002");
        Long arSubjectId = findSubjectIdByCode("1122");
        String[] bank = subjectCoords(bankSubjectId, "1002", "银行存款");
        String[] ar = subjectCoords(arSubjectId, "1122", "应收账款");
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "银行存款-收款", bankSubjectId, bank[0], bank[1], "DEBIT", amt, null));
        OpVoucherLine crLine = buildLine(tenantId, v.getId(), 20,
                "应收账款-核销", arSubjectId, ar[0], ar[1], "CREDIT", amt, null);
        crLine.setCustomerId(rec.getCustomerId());
        stampAuxJson(crLine);   // 辅助维度连同键名落 aux_json
        opVoucherLineMapper.insert(crLine);

        updateVoucherTotals(v.getId(), amt);
        log.info("收款核销 {} 生成凭证 {} 借{}({})/贷{}({}) 金额={}",
                rec.getBillNo(), v.getVoucherNo(), bank[1], bank[0], ar[1], ar[0], amt);
        return v.getId();
    }

    /**
     * 付款出纳凭证。
     * 借：应付账款(2202) 贷：银行存款(1002)，金额取 op_pay_batch.totalAmount。
     * source_type = TREASURY_PAY
     *
     * <p>{@code procInstId} 是触发本凭证的 <b>TR_PAY 流程实例号</b>：由审批最终状态事件原样带过来，
     * 凭证据此回链审批链。不要改从 {@code op_pay_batch.proc_inst_id} 取——批次上那一列存的是源单据的实例，
     * 不是资金付款审批的实例（同一批缺陷里已记录）。缺省 null＝不携带（历史行为不变）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateTreasuryPayVoucher(OpPayBatch batch, String procInstId) {
        if (batch == null || batch.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款批次不存在");
        }
        // 批次凭证判重：同一批次已有凭证（VOID 除外）时不重复生成
        Long existed = existingVoucherId("TREASURY_PAY", batch.getId());
        if (existed != null) {
            log.warn("付款批次 {} 已存在凭证 id={}，跳过重复生成（幂等）", batch.getBatchNo(), existed);
            return existed;
        }
        // 跨类型判重：批次内单据若已自动生成单据级 EXPENSE_PAY 凭证（ExpenseWfEventListener EXP_PAY 分支），
        // 批次级不再重复生成，防止一笔付款两张凭证
        List<OpPayBatchDetail> details = opPayBatchDetailMapper.selectList(
                new LambdaQueryWrapper<OpPayBatchDetail>()
                        .eq(OpPayBatchDetail::getBatchId, batch.getId())
                        .eq(OpPayBatchDetail::getDelFlag, 0));
        List<Long> payIds = new ArrayList<>();
        for (OpPayBatchDetail d : details) {
            if (d.getBillPaymentId() != null) {
                payIds.add(d.getBillPaymentId());
            }
        }
        // 批次内付款记录（既用于跨类型判重，也用于凭证行的供应商辅助）
        List<ExpBillPayment> pays = new ArrayList<>();
        if (!payIds.isEmpty()) {
            pays = expBillPaymentMapper.selectBatchIds(payIds);
            Set<Long> billIds = new HashSet<>();
            for (ExpBillPayment p : pays) {
                if (p != null && p.getBillId() != null) {
                    billIds.add(p.getBillId());
                }
            }
            if (!billIds.isEmpty()) {
                OpVoucher billVoucher = opVoucherMapper.selectOne(
                        new LambdaQueryWrapper<OpVoucher>()
                                .eq(OpVoucher::getSourceType, "EXPENSE_PAY")
                                .in(OpVoucher::getSourceDocId, billIds)
                                .ne(OpVoucher::getStatus, "VOID")
                                .orderByAsc(OpVoucher::getId)
                                .last("limit 1"));
                if (billVoucher != null) {
                    log.warn("付款批次 {} 内单据已有单据级对公付款凭证 id={}，跳过批次级重复生成（幂等）",
                            batch.getBatchNo(), billVoucher.getId());
                    return billVoucher.getId();
                }
            }
        }
        BigDecimal amt = batch.getTotalAmount() == null ? BigDecimal.ZERO : batch.getTotalAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款金额为 0，无法生成凭证");
        }
        // 批次出纳付款环节同受出纳参数约束（默认＝否＝此处恒放行，行为不变）。
        // 批次状态以库内读数为准——调用方（TR_PAY 收尾环节）传的是回写前的批次行，状态仍是审批中。
        if (payLegNotYetPayable("TREASURY_PAY", batch.getId(), batch.getBatchNo())) {
            return null;
        }
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(batch.getCompanyId(), null, null);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(batch.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(batch.getId());
        v.setSourceType("TREASURY_PAY");
        v.setSourceDocType("TREASURY_PAY");
        v.setSourceDocId(batch.getId());
        // 审批链 → 凭证的反查依赖这一列，付款批次这条腿必须与其余出证路径一样带上它
        v.setProcInstId(procInstId);
        v.setSummary("付款出纳-" + (batch.getBatchNo() == null ? "" : batch.getBatchNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        // 付款冲销科目必须与「该笔钱当初挂账时贷记的科目」同源。
        // 旧规则批次凭证固定借 2202 应付账款，而员工报销单的费用凭证贷的是「2241 其他应付款-应付报销款」
        // ⇒ 钱付出去了，2241 那笔贷方却始终没有对应借方来核销，应付报销款只增不减（10-15 F6 实证）。
        // 现按批次内每条付款记录所属单据类型拆行：CLAIM（员工报销）→ 2241；其余（对公付款/预付/借款等）→ 2202。
        Map<Long, BigDecimal> amtByPayment = new HashMap<>();
        for (OpPayBatchDetail d : details) {
            if (d.getBillPaymentId() != null && d.getAmount() != null) {
                amtByPayment.merge(d.getBillPaymentId(), d.getAmount(), BigDecimal::add);
            }
        }
        BigDecimal claimAmt = BigDecimal.ZERO;
        // 缴税走对公付款通道，借方必须落在应交税费(2221)明细，不能落 2202 应付账款。
        // 判据是付款单据上的「缴纳税种」——带该字段即视为税款付款，科目按税种从规则表取。
        Map<String, BigDecimal> taxBySubject = new LinkedHashMap<>();
        for (ExpBillPayment p : pays) {
            if (p == null || p.getBillId() == null) continue;
            BigDecimal lineAmt = amtByPayment.getOrDefault(p.getId(), BigDecimal.ZERO);
            if (lineAmt.signum() == 0) continue;
            ExpBill pb = expBillMapper.selectById(p.getBillId());
            if (pb != null && StringUtils.hasText(pb.getTaxType())) {
                TaxProvisionRule rule = taxRuleService.effectiveRuleOf(pb.getTaxType());
                String code = rule != null && StringUtils.hasText(rule.getPayDebitSubjectCode())
                        ? rule.getPayDebitSubjectCode() : "2221";
                taxBySubject.merge(code, lineAmt, BigDecimal::add);
                continue;
            }
            if (pb != null && "CLAIM".equalsIgnoreCase(pb.getBillType())) {
                claimAmt = claimAmt.add(lineAmt);
            }
        }
        // 明细归集与批次总额不一致时（部分明细无付款记录等）以批次额为准：差额并入对公侧，保证借贷平衡
        if (claimAmt.compareTo(amt) > 0) claimAmt = amt;
        BigDecimal taxAmt = taxBySubject.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (claimAmt.add(taxAmt).compareTo(amt) > 0) {
            // 归集额超过批次额：先压税款侧（税款付款通常整批单税种，压缩后仍可直接对账），再压报销侧
            BigDecimal room = amt.subtract(claimAmt);
            if (room.signum() < 0) {
                room = BigDecimal.ZERO;
            }
            if (taxAmt.compareTo(room) > 0) {
                log.warn("付款批次 {} 税款归集 {} 超过批次可分配额 {}，按可分配额截断", batch.getBatchNo(), taxAmt, room);
                BigDecimal ratio = room.compareTo(taxAmt) == 0 ? BigDecimal.ONE
                        : room.divide(taxAmt, 8, RoundingMode.HALF_UP);
                Map<String, BigDecimal> scaled = new LinkedHashMap<>();
                BigDecimal left = room;
                int i = 0;
                for (Map.Entry<String, BigDecimal> en : taxBySubject.entrySet()) {
                    BigDecimal part = (++i == taxBySubject.size()) ? left
                            : en.getValue().multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                    if (part.signum() > 0) {
                        scaled.put(en.getKey(), part);
                        left = left.subtract(part);
                    }
                }
                taxBySubject = scaled;
                taxAmt = room;
            }
        }
        BigDecimal otherAmt = amt.subtract(claimAmt).subtract(taxAmt);

        Long bankSubjectId = findSubjectIdByCode("1002");
        // 出纳付款凭证同样要带供应商辅助；跨供应商合并组批取不到唯一收款方＝留空（不猜）
        Long batchSupplierId = batchSupplierIdOf(pays);
        int lineNo = 10;
        if (claimAmt.signum() > 0) {
            Long claimedSubjectId = findSubjectIdByCode("2241");
            OpVoucherLine empLine = buildLine(tenantId, v.getId(), lineNo,
                    "其他应付款-应付报销款", claimedSubjectId, "2241", "其他应付款", "DEBIT", claimAmt, null);
            opVoucherLineMapper.insert(empLine);
            lineNo += 10;
        }
        // 税款付款：借方按税种取应交税费明细。这条腿是全仓唯一能产出
        // 「借 应交税费／贷 银行存款」的路径——此前付款凭证借方只有 2241/2202，税永远挂不上账。
        for (Map.Entry<String, BigDecimal> en : taxBySubject.entrySet()) {
            if (en.getValue() == null || en.getValue().signum() <= 0) {
                continue;
            }
            Long taxSubjectId = findSubjectIdByCode(en.getKey());
            if (taxSubjectId == null) {
                taxSubjectId = findSubjectIdByCode("2221");
                log.warn("缴税付款借方科目 {} 不存在于科目表，回落 2221 应交税费", en.getKey());
            }
            OpVoucherLine taxLine = buildLine(tenantId, v.getId(), lineNo,
                    "应交税费-缴税", taxSubjectId, en.getKey(), "应交税费", "DEBIT", en.getValue(), null);
            opVoucherLineMapper.insert(taxLine);
            lineNo += 10;
        }
        if (otherAmt.signum() > 0) {
            Long payableSubjectId = findSubjectIdByCode("2202");
            OpVoucherLine globLine = buildLine(tenantId, v.getId(), lineNo,
                    "应付账款-付款", payableSubjectId, "2202", "应付账款", "DEBIT", otherAmt, null);
            // 供应商辅助只落在对公侧：员工报销的收款方是员工本人，挂供应商辅助会串辅助核算
            globLine.setSupplierId(batchSupplierId);
            stampAuxJson(globLine);
            opVoucherLineMapper.insert(globLine);
        }

        // 贷方：银行存款（资金流出侧，与借方拆分金额合计恒等于批次总额）
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 100,
                "银行存款-付款", bankSubjectId, "1002", "银行存款", "CREDIT", amt, null));

        updateVoucherTotals(v.getId(), amt);
        log.info("付款批次 {} 生成出纳凭证 {} 借应付报销款(2241){}/应交税费(2221){}/应付账款(2202){}/贷银行存款(1002) 金额={}",
                batch.getBatchNo(), v.getVoucherNo(), claimAmt, taxAmt, otherAmt, amt);
        return v.getId();
    }

    /**
     * 银行对账差异凭证。
     * 对账产生差异时（如银行手续费/利息），借：财务费用-手续费(660301) 贷：银行存款(1002)。
     * 金额取 bank_reconcile.amount（差异金额）。
     * source_type = BANK_RECON
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateBankReconVoucher(BankReconcile recon) {
        if (recon == null || recon.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "对账记录不存在");
        }
        BigDecimal amt = recon.getAmount() == null ? BigDecimal.ZERO : recon.getAmount();
        if (amt.signum() == 0) {
            log.info("银行对账 {} 差异金额为 0，跳过凭证生成", recon.getId());
            return null;
        }
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(resolveCompanyId(tenantId), null, null);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(recon.getId());
        v.setSourceType("BANK_RECON");
        v.setSourceDocType("BANK_RECON");
        v.setSourceDocId(recon.getId());
        v.setSummary("银行对账差异-" + (recon.getSerialNo() == null ? String.valueOf(recon.getId()) : recon.getSerialNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        Long feeSubjectId = findSubjectIdByCode("660301");
        Long bankSubjectId = findSubjectIdByCode("1002");
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "财务费用-手续费", feeSubjectId, "660301", "财务费用-手续费", "DEBIT", amt, null));
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                "银行存款-对账差异", bankSubjectId, "1002", "银行存款", "CREDIT", amt, null));

        updateVoucherTotals(v.getId(), amt);
        log.info("银行对账 {} 生成差异凭证 {} 借财务费用({})/贷银行存款({}) 金额={}",
                recon.getId(), v.getVoucherNo(), "660301", "1002", amt);
        return v.getId();
    }

    /**
     * 资金调拨/上存凭证（流程最终状态生成凭证扩域）。
     * TR_TRANSFER / TR_UPLOAD 审批通过 → 借 银行存款(1002, 转入方) 贷 银行存款(1002, 转出方)。
     * source_type = bizType（TR_TRANSFER / TR_UPLOAD），金额取 tr_transfer.amount。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateTransferVoucher(TrTransfer transfer, String bizType) {
        if (transfer == null || transfer.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资金调拨单不存在");
        }
        BigDecimal amt = transfer.getAmount() == null ? BigDecimal.ZERO : transfer.getAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调拨金额为 0，无法生成凭证");
        }
        // 同源判重：同一调拨单 + 同一业务类型已有凭证（VOID 除外）则不重复生成
        Long existed = existingVoucherId(bizType, transfer.getId());
        if (existed != null) {
            log.warn("资金调拨单 {} 业务类型 {} 已存在凭证 id={}，跳过重复生成（幂等）",
                    transfer.getId(), bizType, existed);
            return existed;
        }
        Long bankSubjectId = findSubjectIdByCode("1002");
        if (bankSubjectId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "银行存款科目(1002)缺失，请先在科目字典维护");
        }
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(transfer.getCompanyId() != null ? transfer.getCompanyId() : resolveCompanyId(tenantId), null, null);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(transfer.getCompanyId() != null ? transfer.getCompanyId() : resolveCompanyId(tenantId));
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(transfer.getId());
        v.setSourceType(bizType);
        v.setSourceDocType(bizType);
        v.setSourceDocId(transfer.getId());
        String prefix = "TR_UPLOAD".equals(bizType) ? "资金上存" : "资金调拨";
        v.setSummary(prefix + "-" + (transfer.getTransferNo() == null ? transfer.getId() : transfer.getTransferNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "银行存款-转入", bankSubjectId, "1002", "银行存款", "DEBIT", amt, null));
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                "银行存款-转出", bankSubjectId, "1002", "银行存款", "CREDIT", amt, null));

        updateVoucherTotals(v.getId(), amt);
        log.info("资金调拨单 {} 生成{}凭证 {} 借/贷银行存款(1002) 金额={}",
                transfer.getId(), prefix, v.getVoucherNo(), amt);
        return v.getId();
    }

    /**
     * 资金收款凭证（流程最终状态生成凭证扩域）。
     * TR_RECEIPT 审批通过（核销）→ 借 银行存款 贷 应收账款，科目码取主数据真码。
     * source_type = TR_RECEIPT，金额取 tr_credit_bill.amount。
     *
     * <p>兼容入口：不带流程实例号（历史行为），proc_inst_id 由本方法只读回查 {@code wf_instance} 补齐。
     */
    public Long generateReceiptVoucher(TrCreditBill bill) {
        return generateReceiptVoucher(bill, null);
    }

    /**
     * 收款凭证：贷 1122，客户辅助与流程回链在这里一起补上。
     *
     * <p>①<b>辅助维度</b>：贷 1122 行必须落 {@code customer_id}，并用 {@link #stampAuxJson}
     * 把维度连同键名写进 {@code aux_json}（只写实际有值的维度，取不到留空、不猜）。
     * 收款单本身带着 {@code tr_credit_bill.customer_id}，有客户却不带，按客户／按单据查 1122 明细账
     * 就取不到这笔。借方银行存款行<b>不挂</b>客户辅助——银行科目按客户挂账无据。
     *
     * <p>②<b>流程回链</b>：{@code proc_inst_id} 为空时流程侧与账侧就断了。{@code tr_credit_bill} 本身没有该列，
     * 因此按「单据级凭证回链源单据流」的语义，取触发本凭证的那条 TR_RECEIPT 审批流实例号：
     * 调用方（资金域通过环节）传入优先，缺省时用 {@code bill.id} 只读回查 {@code wf_instance}；
     * 查不到保持 NULL（宁缺勿错关联），不影响出证。
     *
     * <p>③<b>落期口径</b>：按 {@code landingDate} 的「系统当期＋已结账则前滚」落期。
     * 收款单的 {@code receive_date} 与账期不一致时要换算期间，那得先解决 {@code assertOpen} 的结账硬拦，
     * 属于跨期间的口径问题，不在这里处理。
     *
     * <p>四键中 {@code bill_id/source_type/source_doc_type/source_doc_id} 维持收款单坐标不变：
     * 资金收款抽屉（bizType=TR_RECEIPT）的读环节就按 {@code source_doc_type/id} 匹配，改指被核销的应收单
     * 会让这条读路径取不到凭证。
     *
     * @param procInstId 触发本凭证的 TR_RECEIPT 流程实例号；null／空＝本服务只读回查补齐
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateReceiptVoucher(TrCreditBill bill, String procInstId) {
        if (bill == null || bill.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收款单不存在");
        }
        BigDecimal amt = bill.getAmount() == null ? BigDecimal.ZERO : bill.getAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收款金额为 0，无法生成凭证");
        }
        Long existed = existingVoucherId("TR_RECEIPT", bill.getId());
        if (existed != null) {
            log.warn("收款单 {} 已存在凭证 id={}，跳过重复生成（幂等）", bill.getId(), existed);
            return existed;
        }
        Long bankSubjectId = findSubjectIdByCode("1002");
        Long arSubjectId = findSubjectIdByCode("1122");
        if (bankSubjectId == null || arSubjectId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "银行存款(1002)/应收账款(1122)科目缺失，请先在科目字典维护");
        }
        String[] bank = subjectCoords(bankSubjectId, "1002", "银行存款");
        String[] ar = subjectCoords(arSubjectId, "1122", "应收账款");
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(resolveCompanyId(tenantId), null, null);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setCompanyId(resolveCompanyId(tenantId));
        v.setBillId(bill.getId());
        v.setSourceType("TR_RECEIPT");
        v.setSourceDocType("TR_RECEIPT");
        v.setSourceDocId(bill.getId());
        v.setProcInstId(procInstId != null && !procInstId.isBlank() ? procInstId.trim()
                : resolveProcInstIdOf(bill.getId(), "TR_RECEIPT"));
        v.setSummary("资金收款-" + (bill.getBillNo() == null ? bill.getId() : bill.getBillNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "银行存款-收款", bankSubjectId, bank[0], bank[1], "DEBIT", amt, null));
        OpVoucherLine arLine = buildLine(tenantId, v.getId(), 20,
                "应收账款-收款核销", arSubjectId, ar[0], ar[1], "CREDIT", amt, null);
        arLine.setCustomerId(bill.getCustomerId());
        stampAuxJson(arLine);   // 辅助维度连同键名落 aux_json
        opVoucherLineMapper.insert(arLine);

        updateVoucherTotals(v.getId(), amt);
        log.info("收款单 {} 生成收款凭证 {} 借{}({})/贷{}({}) 客户辅助={} 流程回链={} 金额={}",
                bill.getId(), v.getVoucherNo(), bank[1], bank[0], ar[1], ar[0],
                bill.getCustomerId(), v.getProcInstId(), amt);
        return v.getId();
    }

    /**
     * 票据结算凭证（流程最终状态生成凭证扩域）。
     * TR_BILL 审批通过（票据入托/登记确认）→ 借 应收票据(1121) 贷 应收账款(1122)。
     * source_type = TR_BILL，金额取 tr_bill_settle.amount。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateBillSettleVoucher(TrBillSettle settle) {
        if (settle == null || settle.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据不存在");
        }
        BigDecimal amt = settle.getAmount() == null ? BigDecimal.ZERO : settle.getAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据金额为 0，无法生成凭证");
        }
        Long existed = existingVoucherId("TR_BILL", settle.getId());
        if (existed != null) {
            log.warn("票据 {} 已存在凭证 id={}，跳过重复生成（幂等）", settle.getId(), existed);
            return existed;
        }
        Long noteSubjectId = findSubjectIdByCode("1121");
        Long arSubjectId = findSubjectIdByCode("1122");
        if (noteSubjectId == null || arSubjectId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "应收票据(1121)/应收账款(1122)科目缺失，请先在科目字典维护");
        }
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(resolveCompanyId(tenantId), null, null);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setCompanyId(resolveCompanyId(tenantId));
        v.setBillId(settle.getId());
        v.setSourceType("TR_BILL");
        v.setSourceDocType("TR_BILL");
        v.setSourceDocId(settle.getId());
        v.setSummary("票据结算-" + (settle.getTicketNo() == null ? settle.getId() : settle.getTicketNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "应收票据-入托", noteSubjectId, "1121", "应收票据", "DEBIT", amt, null));
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                "应收账款-票据冲抵", arSubjectId, "1122", "应收账款", "CREDIT", amt, null));

        updateVoucherTotals(v.getId(), amt);
        log.info("票据 {} 生成结算凭证 {} 借应收票据(1121)/贷应收账款(1122) 金额={}",
                settle.getId(), v.getVoucherNo(), amt);
        return v.getId();
    }

    /**
     * 一般总账记账凭证（流程最终状态生成凭证扩域）。
     * GL_ACCRUAL 费用预提 / GL_DEFER 费用待摊 / GL_MANUAL_VOUCHER 手工凭证 / GL_OTHER_RECEIPT 其他收款。
     * <p>借贷科目直接取 gl_journal_entry 自身已存的 debit_subject_id / credit_subject_id
     * 演示数据存的是科目编码，经 md_subject 解析为科目 id；金额取 entry.amount。零臆测。
     * source_type = bizType，source_doc_id = gl_journal_entry.id。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateGlJournalVoucher(GlJournalEntry e, String bizType) {
        if (e == null || e.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "总账记账不存在");
        }
        BigDecimal amt = e.getAmount() == null ? BigDecimal.ZERO : e.getAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "记账金额为 0，无法生成凭证");
        }
        Long existed = existingVoucherId(bizType, e.getId());
        if (existed != null) {
            log.warn("总账记账 {} 已存在凭证 id={}，跳过重复生成（幂等）", e.getId(), existed);
            return existed;
        }
        String dCode = String.valueOf(e.getDebitSubjectId());
        String cCode = String.valueOf(e.getCreditSubjectId());
        Long dId = findSubjectIdByCode(dCode);
        Long cId = findSubjectIdByCode(cCode);
        if (dId == null) dId = e.getDebitSubjectId();
        if (cId == null) cId = e.getCreditSubjectId();
        if (dId == null || cId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "借贷科目缺失（debit=" + dCode + ", credit=" + cCode + "），请先在科目字典维护");
        }
        Long tenantId = TenantContext.getTenantId();
        int[] ym = parsePeriodYm(e.getPeriod());
        LocalDate today = landingDate(e.getCompanyId() != null ? e.getCompanyId() : resolveCompanyId(tenantId), ym == null ? null : ym[0], ym == null ? null : ym[1]);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(e.getCompanyId() != null ? e.getCompanyId() : resolveCompanyId(tenantId));
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(e.getId());
        v.setSourceType(bizType);
        v.setSourceDocType(bizType);
        v.setSourceDocId(e.getId());
        v.setSummary(e.getSummary() != null && !e.getSummary().isBlank() ? e.getSummary()
                : "总账记账-" + (e.getEntryNo() == null ? e.getId() : e.getEntryNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        String dName = subjectNameOf(dId);
        String cName = subjectNameOf(cId);
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "借方-" + (dName.isBlank() ? dCode : dName), dId, dCode, dName.isBlank() ? dCode : dName, "DEBIT", amt, null));
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                "贷方-" + (cName.isBlank() ? cCode : cName), cId, cCode, cName.isBlank() ? cCode : cName, "CREDIT", amt, null));

        updateVoucherTotals(v.getId(), amt);
        log.info("总账记账 {}（{}）生成凭证 {} 借{}/贷{} 金额={}",
                e.getId(), bizType, v.getVoucherNo(), dCode, cCode, amt);
        return v.getId();
    }

    /**
     * 税金凭证（流程最终状态生成凭证扩域，补科目 2221/6403/6801）。
     * <ul>
     *  <li><b>GL_TAX_PROV 税金计提</b>：借 税金及附加(6403)【企业所得税 INCOME → 所得税费用(6801)】
     *      　贷 应交税费(2221)，金额 = gl_tax_provision.tax_amount</li>
     * </ul>
     * 税金缴纳不经独立流程，走对公付款通道 ⇒ 原 GL_TAX_PAY 缴纳环节作废，本方法仅受理计提环节。
     * 说明：演示简化，未细分增值税"转出未交增值税"及附加税明细子目。
     * source_type = bizType（仅 GL_TAX_PROV），source_doc_id = gl_tax_provision.id。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateTaxVoucher(GlTaxProvision e, String bizType) {
        if (e == null || e.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "税金计提记录不存在");
        }
        // 税金缴纳不经独立流程，走对公付款通道（付款批次执行后出付款环节凭证），此处不再出缴纳凭证
        if ("GL_TAX_PAY".equals(bizType)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "税金缴纳不经独立缴纳流程，请通过对公付款通道发起付款");
        }
        BigDecimal amt = e.getTaxAmount() == null ? BigDecimal.ZERO : e.getTaxAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "税金金额为 0，无法生成凭证");
        }
        Long existed = existingVoucherId(bizType, e.getId());
        if (existed != null) {
            log.warn("税金记录 {} 已存在凭证 id={}，跳过重复生成（幂等）", e.getId(), existed);
            return existed;
        }
        // /：借贷科目一律按「税种计提规则」取，不再写死 2221/6403/6801。
        // 写死 2221 的实际后果是：2221 是父科目（下设明细），制证时被 PostableSubjectResolver 拒
        // （"不是末级科目，无法确定用哪一个"），计提凭证永远出不来、单据永远停在 APPROVED。
        TaxProvisionRule rule = taxRuleService.effectiveRuleOf(e.getTaxType());
        String creditCode = rule != null && StringUtils.hasText(rule.getCreditSubjectCode())
                ? rule.getCreditSubjectCode()
                : ("INCOME".equals(e.getTaxType()) ? "22210201" : "22210101");
        String debitCode = rule != null && StringUtils.hasText(rule.getDebitSubjectCode())
                ? rule.getDebitSubjectCode()
                : ("INCOME".equals(e.getTaxType()) ? "6801" : "6403");
        Long taxPayableId = taxRuleService.subjectIdOf(creditCode);
        Long expenseId = taxRuleService.subjectIdOf(debitCode);
        if (taxPayableId == null || expenseId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "税金凭证科目缺失（需 " + creditCode + " 应交税费明细 + " + debitCode
                            + " 损益），请先维护科目字典或在「税金计提规则」中改科目");
        }
        Long tenantId = TenantContext.getTenantId();
        int[] ym = parsePeriodYm(e.getPeriod());
        LocalDate today = landingDate(e.getCompanyId() != null ? e.getCompanyId() : resolveCompanyId(tenantId), ym == null ? null : ym[0], ym == null ? null : ym[1]);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(e.getCompanyId() != null ? e.getCompanyId() : resolveCompanyId(tenantId));
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(e.getId());
        v.setSourceType(bizType);
        v.setSourceDocType(bizType);
        v.setSourceDocId(e.getId());
        v.setSummary("税金计提-" + e.getPeriod() + "-" + e.getTaxType());
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        String eCode = debitCode;
        String eName = subjectNameOf(expenseId);
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "计提-" + (eName.isBlank() ? eCode : eName), expenseId, eCode,
                eName.isBlank() ? eCode : eName, "DEBIT", amt, null));
        String cName = subjectNameOf(taxPayableId);
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                "应交税费-计提" + e.getTaxType(), taxPayableId, creditCode,
                cName.isBlank() ? "应交税费" : cName, "CREDIT", amt, null));
        updateVoucherTotals(v.getId(), amt);
        log.info("税金 {}（{}）生成凭证 {} 金额={}", e.getProvisionNo(), bizType, v.getVoucherNo(), amt);
        return v.getId();
    }

    /**
     * 申报凭证：申报确认（FILED）后按税种出一张「应缴」凭证，把计提时贷记的
     * 应交税费明细<b>转出到未交税金</b>，与计提凭证成对。
     * <ul>
     *  <li>借：规则 {@code credit_subject_code}（计提时的贷方，如 22210101 销项税额）</li>
     *  <li>贷：规则 {@code declare_credit_subject_code}（未交税金，缺省 2221）</li>
     * </ul>
     * 借贷同码时整笔是空转（SURCHARGE 等未细分科目的税种就是这种情形），此时<b>不生成</b>凭证并返回 null，
     * 由调用方决定是否告警——生成一张借贷同科目的凭证只会污染总账。
     *
     * <p>{@code source_type = TAX_DECLARATION}，{@code source_doc_id = tax_declaration.id}；
     * 凭证号由调用方回写 {@code tax_declaration.source_voucher_id}（该列此前全仓零读写）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateDeclarationVoucher(TaxDeclaration d) {
        if (d == null || d.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "税务申报记录不存在");
        }
        BigDecimal amt = d.getTaxAmount() == null ? BigDecimal.ZERO : d.getTaxAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申报税额为 0，无需生成应缴凭证");
        }
        Long existed = existingVoucherId("TAX_DECLARATION", d.getId());
        if (existed != null) {
            log.warn("申报 {} 已存在凭证 id={}，跳过重复生成（幂等）", d.getDeclarationNo(), existed);
            return existed;
        }
        TaxProvisionRule rule = taxRuleService.effectiveRuleOf(d.getTaxType());
        if (rule == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "税种 " + d.getTaxType() + " 未配置计提规则，无法确定申报凭证科目，请先在「税金计提规则」中维护");
        }
        String debitCode = rule.getCreditSubjectCode();
        String creditCode = StringUtils.hasText(rule.getDeclareCreditSubjectCode())
                ? rule.getDeclareCreditSubjectCode() : "2221";
        if (!StringUtils.hasText(debitCode) || debitCode.equals(creditCode)) {
            log.info("申报 {}（{}）借贷科目同为 {}，属空转分录，不生成应缴凭证",
                    d.getDeclarationNo(), d.getTaxType(), creditCode);
            return null;
        }
        Long debitId = taxRuleService.subjectIdOf(debitCode);
        Long creditId = taxRuleService.subjectIdOf(creditCode);
        if (debitId == null || creditId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "申报凭证科目缺失（需 " + debitCode + " + " + creditCode + "），请先维护科目字典");
        }
        Long tenantId = TenantContext.getTenantId();
        int[] ym = parsePeriodYm(d.getPeriod());
        Long companyId = d.getCompanyId() != null ? d.getCompanyId() : resolveCompanyId(tenantId);
        LocalDate today = landingDate(companyId, ym == null ? null : ym[0], ym == null ? null : ym[1]);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(companyId);
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(d.getId());
        v.setSourceType("TAX_DECLARATION");
        v.setSourceDocType("TAX_DECLARATION");
        v.setSourceDocId(d.getId());
        v.setSummary("申报应缴-" + d.getPeriod() + "-" + d.getTaxType());
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);
        String dName = subjectNameOf(debitId);
        String cName = subjectNameOf(creditId);
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "转出应交-" + d.getTaxType(), debitId, debitCode,
                dName.isBlank() ? debitCode : dName, "DEBIT", amt, null));
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                "未交税金-" + d.getTaxType(), creditId, creditCode,
                cName.isBlank() ? creditCode : cName, "CREDIT", amt, null));
        updateVoucherTotals(v.getId(), amt);
        log.info("税务申报 {}（{}）生成应缴凭证 {} 借{}/贷{} 金额={}",
                d.getDeclarationNo(), d.getTaxType(), v.getVoucherNo(), debitCode, creditCode, amt);
        return v.getId();
    }

    /**
     * 按「单据类型 + 方向」取记账配置科目码。
     *
     * <p>配置表 {@code md_gl_config} 此前只被费用报销族消费（28 个生成器中 1 族可配、其余写死，
     * 72 处按科目码取数、27 个科目字面量）。本方法把「单据类型＋方向 → 科目」扩到薪酬族：
     * 有配置用配置，无配置用调用方给的 fallback（＝改造前的硬编码值），保证零配置时行为不变。
     *
     * <p>没有建模板引擎：只是一列 doc_type 加一条按类型取数，不引入分类条件/分录合并等 A20 能力。
     */
    private String configSubjectCode(String docType, String direction, String fallback) {
        try {
            MdGlConfig cfg = mdGlConfigMapper.selectOne(new LambdaQueryWrapper<MdGlConfig>()
                    .eq(MdGlConfig::getDocType, docType)
                    .eq(MdGlConfig::getDirection, direction)
                    .eq(MdGlConfig::getStatus, "NORMAL")
                    .orderByAsc(MdGlConfig::getId)
                    .last("limit 1"));
            if (cfg == null) {
                return fallback;
            }
            String code = "DEBIT".equalsIgnoreCase(direction)
                    ? pickNotEmpty(cfg.getDebitSubjectCode(), cfg.getSubjectCode())
                    : pickNotEmpty(cfg.getCreditSubjectCode(), cfg.getSubjectCode());
            return StringUtils.hasText(code) ? code : fallback;
        } catch (Exception ex) {
            log.debug("读取记账配置失败 type={} dir={}，用缺省 {}: {}", docType, direction, fallback, ex.getMessage());
            return fallback;
        }
    }

    /**
     * 薪酬凭证（流程最终状态生成凭证扩域，补科目 2211）。
     * <ul>
     *  <li><b>GL_SALARY_PROV 薪酬计提</b>：借 管理费用-工资薪酬(660225)　贷 应付职工薪酬(2211)，
     *      金额 = gl_salary_run.salary_amount（应发工资规则）</li>
     *  <li><b>GL_SALARY_PAY 薪酬发放</b>：借 应付职工薪酬(2211)　贷 银行存款(1002)，
     *      金额 = gl_salary_run.total_amount（实发规则，差额为代扣社保/个税）</li>
     * </ul>
     * 说明：演示简化，计提借方统一计入管理费用，未按部门拆分成本/费用。
     * source_type = bizType（GL_SALARY_PROV / GL_SALARY_PAY），source_doc_id = gl_salary_run.id。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateSalaryVoucher(GlSalaryRun e, String bizType) {
        if (e == null || e.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "薪酬发放批次不存在");
        }
        boolean pay = "GL_SALARY_PAY".equals(bizType);
        BigDecimal amt = pay ? e.getTotalAmount() : e.getSalaryAmount();
        if (amt == null) {
            amt = e.getTotalAmount();
        }
        if (amt == null || amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "薪酬金额为 0，无法生成凭证");
        }
        Long existed = existingVoucherId(bizType, e.getId());
        if (existed != null) {
            log.warn("薪酬批次 {} 已存在凭证 id={}，跳过重复生成（幂等）", e.getId(), existed);
            return existed;
        }
        // 薪酬族科目改为可配（md_gl_config.doc_type + direction），无配置时沿用原硬编码
        String provDocType = "GL_SALARY_PAY".equals(bizType) ? "GL_SALARY_PAY" : "GL_SALARY_PROV";
        String payableCode = configSubjectCode(provDocType, "CREDIT", "2211");
        String bankCode = configSubjectCode("GL_SALARY_PAY", "DEBIT", "1002");
        String expenseCode = configSubjectCode("GL_SALARY_PROV", "DEBIT", "660225");
        Long payableId = findSubjectIdByCode(payableCode);
        Long bankId = findSubjectIdByCode(bankCode);
        Long expenseId = findSubjectIdByCode(expenseCode);
        if (payableId == null || (pay ? bankId == null : expenseId == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "薪酬凭证科目缺失（需 " + payableCode + " 应付职工薪酬"
                            + (pay ? " + " + bankCode + " 银行存款" : " + " + expenseCode + " 管理费用")
                            + "），请先维护科目字典/记账配置");
        }
        Long tenantId = TenantContext.getTenantId();
        int[] ym = parsePeriodYm(e.getPeriod());
        LocalDate today = landingDate(e.getCompanyId() != null ? e.getCompanyId() : resolveCompanyId(tenantId), ym == null ? null : ym[0], ym == null ? null : ym[1]);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(e.getCompanyId() != null ? e.getCompanyId() : resolveCompanyId(tenantId));
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(e.getId());
        v.setSourceType(bizType);
        v.setSourceDocType(bizType);
        v.setSourceDocId(e.getId());
        v.setSummary((pay ? "薪酬发放-" : "薪酬计提-") + e.getPeriod() + "-" + (e.getRunNo() == null ? e.getId() : e.getRunNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        if (pay) {
            String dName = subjectNameOf(payableId);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                    "应付职工薪酬-发放", payableId, "2211", dName.isBlank() ? "应付职工薪酬" : dName, "DEBIT", amt, null));
            String cName = subjectNameOf(bankId);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                    "银行存款-工资发放", bankId, "1002", cName.isBlank() ? "银行存款" : cName, "CREDIT", amt, null));
        } else {
            String dName = subjectNameOf(expenseId);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                    "管理费用-职工薪酬", expenseId, "660225", dName.isBlank() ? "管理费用-职工薪酬" : dName, "DEBIT", amt, null));
            String cName = subjectNameOf(payableId);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                    "应付职工薪酬-计提", payableId, "2211", cName.isBlank() ? "应付职工薪酬" : cName, "CREDIT", amt, null));
        }
        updateVoucherTotals(v.getId(), amt);
        log.info("薪酬批次 {}（{}）生成凭证 {} 金额={}", e.getRunNo(), bizType, v.getVoucherNo(), amt);
        return v.getId();
    }

    /**
     * 薪酬代扣缴纳凭证（发放链末节点通过后，与发放凭证成对生成）。
     * <p>缺陷：计提按<b>应发</b>全额贷记 2211，发放只借<b>实发</b>（total_amount），
     * 中间的代扣（个税 tax_amount + 代扣社保 social_security_amount + 其他扣除 deduction_amount）
     * 没有任何一张凭证承接，2211 的贷借差额永久挂账（证据 12-30:170：80,000 计提 − 65,000 发放 ＝ 15,000 无出口）。
     * <ul>
     *  <li>借 应付职工薪酬(2211) ＝ 代扣合计</li>
     *  <li>贷 应交税费(2221) ＝ 代扣个税（待申报缴纳，缴纳环节走对公付款通道）</li>
     *  <li>贷 其他应付款(2241) ＝ 代扣社保 + 其他扣除（个人承担部分，待转付社保机构/发还）</li>
     * </ul>
     * 承接科目 2221/2241 在 md_subject 种子中已存在（id 1592/920），但无代扣专用子目
     * （如 222105 应交个税、2241xx 代扣社保）——子目细分属科目配置口径，此处只用总账级科目，不臆造编码。
     * <p>代扣合计为 0 时不出证（返回 null）；科目缺失抛 {@link BizException} 由调用方记日志，不阻断发放链。
     * <p>source_type = GL_SALARY_WITHHOLD，source_doc_type = GL_SALARY_PAY（与发放凭证同域，联查同屏），
     * source_doc_id = gl_salary_run.id。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateSalaryWithholdingVoucher(GlSalaryRun e) {
        if (e == null || e.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "薪酬发放批次不存在");
        }
        BigDecimal tax = nvlAmount(e.getTaxAmount());
        BigDecimal social = nvlAmount(e.getSocialSecurityAmount());
        BigDecimal deduction = nvlAmount(e.getDeductionAmount());
        BigDecimal withheld = tax.add(social).add(deduction);
        if (withheld.signum() == 0) {
            log.info("薪酬批次 {}（{}）无代扣金额，跳过代扣缴纳凭证", e.getRunNo(), "GL_SALARY_PAY");
            return null;
        }
        String bizType = "GL_SALARY_WITHHOLD";
        Long existed = existingVoucherId(bizType, e.getId());
        if (existed != null) {
            log.warn("薪酬批次 {} 已存在代扣缴纳凭证 id={}，跳过重复生成（幂等）", e.getId(), existed);
            return existed;
        }
        Long payableId = findSubjectIdByCode("2211");
        Long taxPayableId = tax.signum() > 0 ? findSubjectIdByCode("2221") : null;
        BigDecimal otherAmt = social.add(deduction);
        Long otherPayableId = otherAmt.signum() > 0 ? findSubjectIdByCode("2241") : null;
        if (payableId == null || (tax.signum() > 0 && taxPayableId == null)
                || (otherAmt.signum() > 0 && otherPayableId == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "薪酬代扣凭证科目缺失（需 2211 应付职工薪酬"
                            + (tax.signum() > 0 ? " + 2221 应交税费" : "")
                            + (otherAmt.signum() > 0 ? " + 2241 其他应付款" : "") + "），请先维护科目字典");
        }
        Long tenantId = TenantContext.getTenantId();
        int[] ym = parsePeriodYm(e.getPeriod());
        LocalDate today = landingDate(e.getCompanyId() != null ? e.getCompanyId() : resolveCompanyId(tenantId),
                ym == null ? null : ym[0], ym == null ? null : ym[1]);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(e.getCompanyId() != null ? e.getCompanyId() : resolveCompanyId(tenantId));
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(e.getId());
        v.setSourceType(bizType);
        v.setSourceDocType("GL_SALARY_PAY");
        v.setSourceDocId(e.getId());
        v.setSummary("薪酬代扣-" + e.getPeriod() + "-" + (e.getRunNo() == null ? e.getId() : e.getRunNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        int no = 1;
        String payableName = subjectNameOf(payableId);
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), no++,
                "应付职工薪酬-代扣转出", payableId, "2211",
                payableName.isBlank() ? "应付职工薪酬" : payableName, "DEBIT", withheld, e.getDeptId()));
        if (tax.signum() > 0) {
            String taxName = subjectNameOf(taxPayableId);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), no++,
                    "代扣个人所得税", taxPayableId, "2221",
                    taxName.isBlank() ? "应交税费" : taxName, "CREDIT", tax, e.getDeptId()));
        }
        if (otherAmt.signum() > 0) {
            String otherName = subjectNameOf(otherPayableId);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), no,
                    "代扣社保及住房公积金/其他代扣款", otherPayableId, "2241",
                    otherName.isBlank() ? "其他应付款" : otherName, "CREDIT", otherAmt, e.getDeptId()));
        }
        updateVoucherTotals(v.getId(), withheld);
        log.info("薪酬批次 {} 生成代扣缴纳凭证 {} 借2211 {}/贷 2221 {} + 2241 {}",
                e.getRunNo(), v.getVoucherNo(), withheld, tax, otherAmt);
        return v.getId();
    }

    private static BigDecimal nvlAmount(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 内部往来抵消凭证（GL_OFFSET 流程最终状态生成凭证扩域）。
     * <p>抵销内部应收应付：借 应付账款(2202)　贷 应收账款(1122)，金额 = gl_inter_offset.amount。
     * <p>bizId 指向往账(PAYABLE)分录；同一公司对的来账(RECEIVABLE)金额由
     * {@code GlInterOffsetService.assertPairEqual} 强制相等，故单边金额即可配平。
     * source_type = GL_OFFSET，source_doc_id = gl_inter_offset.id。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateInterOffsetVoucher(GlInterOffset e) {
        if (e == null || e.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "内部往来抵消分录不存在");
        }
        BigDecimal amt = e.getAmount() == null ? BigDecimal.ZERO : e.getAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "抵消金额为 0，无法生成凭证");
        }
        String bizType = "GL_OFFSET";
        Long existed = existingVoucherId(bizType, e.getId());
        if (existed != null) {
            log.warn("抵消分录 {} 已存在凭证 id={}，跳过重复生成（幂等）", e.getId(), existed);
            return existed;
        }
        Long payableId = findSubjectIdByCode("2202");
        Long receivableId = findSubjectIdByCode("1122");
        if (payableId == null || receivableId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "内部往来抵消科目缺失（需 2202 应付账款 / 1122 应收账款），请先维护科目字典");
        }
        Long tenantId = TenantContext.getTenantId();
        Long companyId = e.getFromCompanyId() != null ? e.getFromCompanyId() : resolveCompanyId(tenantId);
        int[] ym = parsePeriodYm(e.getPeriod());
        LocalDate today = landingDate(companyId, ym == null ? null : ym[0], ym == null ? null : ym[1]);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(companyId);
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(e.getId());
        v.setSourceType(bizType);
        v.setSourceDocType(bizType);
        v.setSourceDocId(e.getId());
        v.setSummary("内部往来抵消-" + e.getPeriod() + "-" + (e.getOffsetNo() == null ? e.getId() : e.getOffsetNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        String dName = subjectNameOf(payableId);
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "内部应付" + e.getFromCompanyId() + "→" + e.getToCompanyId() + "-抵销",
                payableId, "2202", dName.isBlank() ? "应付账款" : dName, "DEBIT", amt, null));
        String cName = subjectNameOf(receivableId);
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                "内部应收" + e.getToCompanyId() + "→" + e.getFromCompanyId() + "-抵销",
                receivableId, "1122", cName.isBlank() ? "应收账款" : cName, "CREDIT", amt, null));

        updateVoucherTotals(v.getId(), amt);
        log.info("内部往来抵消 {} 生成凭证 {} 借应付账款(2202)/贷应收账款(1122) 金额={}",
                e.getOffsetNo(), v.getVoucherNo(), amt);
        return v.getId();
    }

    /**
     * 退税凭证（GL_TAX_REFUND 流程最终状态生成凭证扩域，补流程定义）。
     * <p>税务核准退税（增值税三场景：出口免抵退 EXPORT_REFUND / 留抵退税 CREDIT_REFUND /
     * 即征即退 IMMEDIATE_REFUND）：借 其他应收款-应收退税款(1221)　贷 应交税费(2221)，
     * 金额 = tax_refund.tax_amount。
     * <p>规则说明：按权责发生制，<b>核准（APPROVED）时确认应收退税款</b>；
     * 实际到账（REFUNDED）时另需 借 银行存款(1002) 贷 其他应收款(1221) 一笔，
     * 该笔由到账登记（changeStatus→REFUNDED）触发，不属于流程最终状态收尾。
     * source_type = GL_TAX_REFUND，source_doc_id = tax_refund.id。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateTaxRefundVoucher(TaxRefund e) {
        if (e == null || e.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退税单不存在");
        }
        BigDecimal amt = e.getTaxAmount() == null ? BigDecimal.ZERO : e.getTaxAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退税金额为 0，无法生成凭证");
        }
        String bizType = "GL_TAX_REFUND";
        Long existed = existingVoucherId(bizType, e.getId());
        if (existed != null) {
            log.warn("退税单 {} 已存在凭证 id={}，跳过重复生成（幂等）", e.getId(), existed);
            return existed;
        }
        Long otherReceivableId = findSubjectIdByCode("1221");
        Long taxPayableId = findSubjectIdByCode("2221");
        if (otherReceivableId == null || taxPayableId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "退税凭证科目缺失（需 1221 其他应收款 / 2221 应交税费），请先维护科目字典");
        }
        Long tenantId = TenantContext.getTenantId();
        int[] ym = parsePeriodYm(e.getPeriod());
        LocalDate today = landingDate(e.getCompanyId() != null ? e.getCompanyId() : resolveCompanyId(tenantId), ym == null ? null : ym[0], ym == null ? null : ym[1]);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(e.getCompanyId() != null ? e.getCompanyId() : resolveCompanyId(tenantId));
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(e.getId());
        v.setSourceType(bizType);
        v.setSourceDocType(bizType);
        v.setSourceDocId(e.getId());
        v.setSummary("税务退税核准-" + e.getRefundType() + "-" + e.getPeriod());
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        String dName = subjectNameOf(otherReceivableId);
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "应收退税款-" + e.getRefundType(), otherReceivableId, "1221",
                dName.isBlank() ? "其他应收款" : dName, "DEBIT", amt, null));
        String cName = subjectNameOf(taxPayableId);
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                "应交税费-退税冲减", taxPayableId, "2221",
                cName.isBlank() ? "应交税费" : cName, "CREDIT", amt, null));

        updateVoucherTotals(v.getId(), amt);
        log.info("退税单 {}（{}）生成凭证 {} 借其他应收款(1221)/贷应交税费(2221) 金额={}",
                e.getRefundNo(), e.getRefundType(), v.getVoucherNo(), amt);
        return v.getId();
    }

    /** 科目名称查询（失败返回空串，不阻断凭证生成） */
    private String subjectNameOf(Long subjectId) {
        if (subjectId == null) return "";
        try {
            MdSubject s = mdSubjectMapper.selectById(subjectId);
            return s == null || s.getSubjectName() == null ? "" : s.getSubjectName();
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * 坏账凭证（流程最终状态生成凭证的扩域）。
     * <ul>
     *  <li>BAD_DEBT_PROV 计提：借 信用减值损失(6113) 贷 坏账准备(1231)</li>
     *  <li>BAD_DEBT_CONFIRM 核销：借 坏账准备(1231) 贷 应收账款(1122)（挂客户辅助核算）</li>
     * </ul>
     * source_type = bizType（BAD_DEBT_PROV / BAD_DEBT_CONFIRM）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateBadDebtVoucher(RcBadDebt bd, String bizType) {
        if (bd == null || bd.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "坏账单不存在");
        }
        BigDecimal amt = bd.getAmount() == null ? BigDecimal.ZERO : bd.getAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "坏账金额为 0，无法生成凭证");
        }
        boolean provision = "BAD_DEBT_PROV".equals(bizType);
        Long lossId = findSubjectIdByCode("6113");
        Long bdrId = findSubjectIdByCode("1231");
        Long arId = findSubjectIdByCode("1122");
        if (bdrId == null || (provision ? lossId == null : arId == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "坏账凭证科目缺失（6113/1231/1122），请先在科目字典维护");
        }
        Long tenantId = TenantContext.getTenantId();
        int[] ym = parsePeriodYm(bd.getPeriod());
        LocalDate today = landingDate(bd.getCompanyId(), ym == null ? null : ym[0], ym == null ? null : ym[1]);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(bd.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(bd.getId());
        v.setSourceType(bizType);
        v.setSourceDocType(bizType);
        v.setSourceDocId(bd.getId());
        v.setSummary((provision ? "坏账计提-" : "坏账核销-")
                + (bd.getPeriod() == null ? "" : bd.getPeriod()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        if (provision) {
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                    "信用减值损失-坏账计提", lossId, "6113", "信用减值损失", "DEBIT", amt, null));
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                    "坏账准备-计提", bdrId, "1231", "坏账准备", "CREDIT", amt, null));
        } else {
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                    "坏账准备-核销", bdrId, "1231", "坏账准备", "DEBIT", amt, null));
            OpVoucherLine crLine = buildLine(tenantId, v.getId(), 20,
                    "应收账款-核销转销", arId, "1122", "应收账款", "CREDIT", amt, null);
            crLine.setCustomerId(bd.getCustomerId());
            opVoucherLineMapper.insert(crLine);
        }
        updateVoucherTotals(v.getId(), amt);
        log.info("坏账单 {} 生成{}凭证 {} 金额={}", bd.getId(), provision ? "计提" : "核销", v.getVoucherNo(), amt);
        return v.getId();
    }

    /**
     * 三单匹配应付确认凭证（采购循环 PTP：采购→收货→三单匹配→应付）。
     * 借 库存商品 贷 应付账款（挂供应商辅助核算），金额取采购订单金额。
     * source_type = INVOICE_MATCH。
     *
     * <p>兼容入口：不带付款单坐标与流程实例号，由本服务只读反查补齐（见
     * {@link #generateInvoiceMatchVoucher(PoOrder, Long, String)}）。
     */
    public Long generateInvoiceMatchVoucher(PoOrder order) {
        return generateInvoiceMatchVoucher(order, null, null);
    }

    /**
     * 应付确认凭证的 {@code bill_id} 不再塞采购订单主键。
     *
     * <p>本凭证在单据侧的挂靠对象是<b>付款单</b>（{@code exp_bill}）——{@code bill_id} 那一列的规则就是
     * exp_bill 主键（见 {@link #EXPENSE_BILL_SOURCES}／只有费用族来源的 bill_id 才是费用单主键，
     * 别的域写进去就是跨域撞号）。把 {@code po_order.id} 写进这一列，读数上就是一张凭证
     * 挂到了<b>别的采购单的付款单</b>上：追溯抽屉「记账凭证 0 张」，而凭证其实早已平衡写进库。
     *
     * <p>所以 {@code bill_id} 写付款单 id（本单链有唯一确定值时），<b>PO 坐标留在
     * {@code source_doc_type/source_doc_id}</b>（那是本凭证真正的来源单据）。
     * 反查判据只用 {@code exp_bill.po_id}，<b>唯一命中</b>才认；零条或多条一律留 NULL（不猜关联），
     * 留空只是少一条可读挂靠，绝不再挂错单。
     *
     * <p>{@code proc_inst_id} 取<b>三单匹配审批流</b>的实例号（单据级凭证回链触发它的源单据流）：
     * 该流的 {@code wf_instance.biz_id} 就是付款单 id，故调用方传入优先，缺省按付款单 id 只读回查
     * {@code INVOICE_MATCH／INVOICE_MATCH_MANUAL}。这一列是抽屉读环节里唯一能命中本凭证的臂
     * （费用域白名单 {@code VOUCHER_SOURCE_EXPENSE} 不含 INVOICE_MATCH，见移交项）。
     *
     * @param payBillId 本次三单匹配所依据的付款单（exp_bill）主键；null＝按 {@code exp_bill.po_id} 反查唯一命中
     * @param procInstId 三单匹配流程实例号；null／空＝本服务只读回查补齐
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateInvoiceMatchVoucher(PoOrder order, Long payBillId, String procInstId) {
        if (order == null || order.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单不存在");
        }
        BigDecimal amt = order.getAmount() == null ? BigDecimal.ZERO : order.getAmount();
        if (amt.signum() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "采购订单金额为 0，无法生成应付确认凭证");
        }
        Long invId = findSubjectIdByCode("1405");
        Long apId = findSubjectIdByCode("2202");
        if (invId == null || apId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "应付确认凭证科目缺失（1405/2202），请先在科目字典维护");
        }
        // 付款单坐标——调用方给的优先，否则按 po_id 反查唯一命中
        Long anchorBillId = payBillId;
        if (anchorBillId == null) {
            ExpBill pay = uniquePayBillOfPo(order.getId());
            anchorBillId = pay == null ? null : pay.getId();
            if (anchorBillId == null) {
                log.warn("采购订单 {} 未反查到唯一付款单（exp_bill.po_id 零条或多条），凭证 bill_id 留空而不是挂 PO 主键",
                        order.getPoNo());
            }
        }
        Long tenantId = TenantContext.getTenantId();
        // 同一采购订单的应付确认凭证只允许存在一张。每条 INVOICE_MATCH / INVOICE_MATCH_MANUAL 链
        // 办结都会走到这里，不判重就会按链的数量各建一张，批量过账即重复入账。
        // 判重键＝source_doc_type + source_doc_id + tenant，命中即复用原凭证。
        OpVoucher existing = opVoucherMapper.selectOne(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getTenantId, tenantId)
                .eq(OpVoucher::getSourceDocType, "INVOICE_MATCH")
                .eq(OpVoucher::getSourceDocId, order.getId())
                .eq(OpVoucher::getDelFlag, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("采购订单 {} 的应付确认凭证已存在（{}），不重复生成",
                    order.getPoNo(), existing.getVoucherNo());
            return existing.getId();
        }
        LocalDate today = landingDate(order.getCompanyId(), null, null);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(order.getCompanyId());
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(anchorBillId);
        v.setSourceType("INVOICE_MATCH");
        v.setSourceDocType("INVOICE_MATCH");
        v.setSourceDocId(order.getId());
        v.setProcInstId(procInstId != null && !procInstId.isBlank() ? procInstId.trim()
                : resolveProcInstIdOf(anchorBillId, "INVOICE_MATCH", "INVOICE_MATCH_MANUAL"));
        v.setSummary("三单匹配应付确认-" + (order.getPoNo() == null ? "" : order.getPoNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "库存商品-收货入库", invId, "1405", "库存商品", "DEBIT", amt, null));
        OpVoucherLine crLine = buildLine(tenantId, v.getId(), 20,
                "应付账款-发票确认", apId, "2202", "应付账款", "CREDIT", amt, null);
        crLine.setSupplierId(order.getSupplierId());
        stampAuxJson(crLine);   // 已解析出的辅助维度连同名称落 aux_json
        opVoucherLineMapper.insert(crLine);
        updateVoucherTotals(v.getId(), amt);
        log.info("采购订单 {} 三单匹配生成应付确认凭证 {} 金额={} 付款单挂靠={} 流程回链={}",
                order.getPoNo(), v.getVoucherNo(), amt, v.getBillId(), v.getProcInstId());
        return v.getId();
    }

    /**
     * 资产折旧凭证。
     * 借：管理费用-折旧费(660203) 贷：累计折旧(1602)，金额取 md_asset.monthlyDepr。
     * source_type = ASSET_DEPRECIATION
     *
     * <p>落期取<b>批单期间</b>，不用系统当期：传 null/null 走前滚兜底时，
     * 本月批的折旧会落到上一期去。期间不可解析时才回退前滚；
     * 凭证日期取期间末日（折旧按月末计提）。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateDepreciationVoucher(MdAsset asset, String period) {
        if (asset == null || asset.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资产卡片不存在");
        }
        BigDecimal amt = asset.getMonthlyDepr() == null ? BigDecimal.ZERO : asset.getMonthlyDepr();
        if (amt.signum() == 0) {
            log.info("资产 {} 月折旧额为 0，跳过凭证生成", asset.getAssetNo());
            return null;
        }
        Long tenantId = TenantContext.getTenantId();
        int[] ym = parsePeriodYm(period);
        // 已关账期间由 landingDate 内的 assertOpen 拒绝，与其余 generate* 同一规则
        LocalDate landing = landingDate(asset.getCompanyId(), ym == null ? null : ym[0], ym == null ? null : ym[1]);
        LocalDate voucherDate = ym == null ? landing
                : LocalDate.of(ym[0], ym[1], 1).plusMonths(1).minusDays(1);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(asset.getCompanyId());
        v.setPeriodYear(voucherDate.getYear());
        v.setPeriodMonth(voucherDate.getMonthValue());
        v.setVoucherDate(voucherDate);
        v.setBillId(asset.getId());
        v.setSourceType("ASSET_DEPRECIATION");
        v.setSourceDocType("ASSET_DEPRECIATION");
        v.setSourceDocId(asset.getId());
        // 无资产编号（盘点导入等遗漏卡片）时摘要回落「#ID 名称」，不再出现裸尾"资产折旧-"
        String assetNoForSummary = asset.getAssetNo() == null || asset.getAssetNo().isBlank()
                ? ("#" + asset.getId()
                        + (asset.getAssetName() == null || asset.getAssetName().isBlank() ? "" : " " + asset.getAssetName()))
                : asset.getAssetNo();
        v.setSummary("资产折旧-" + assetNoForSummary);
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        Long expenseSubjectId = findSubjectIdByCode("660203");
        Long depSubjectId = findSubjectIdByCode("1602");
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                "管理费用-折旧", expenseSubjectId, "660203", "管理费用-折旧费", "DEBIT", amt, asset.getDeptId()));
        opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                "累计折旧", depSubjectId, "1602", "累计折旧", "CREDIT", amt, asset.getDeptId()));

        updateVoucherTotals(v.getId(), amt);
        log.info("资产 {} 生成折旧凭证 {} 借管理费用({})/贷累计折旧({}) 金额={}",
                asset.getAssetNo(), v.getVoucherNo(), "660203", "1602", amt);
        return v.getId();
    }

    /**
     * 合规资产域凭证（流程最终状态生成凭证扩域）。
     * <ul>
     *  <li>FA_ADD 通过 → 借 固定资产(1601) 贷 银行存款(1002)，金额=资产原值（购置新增规则）</li>
     *  <li>FA_CIP_TO_ASSET 通过 → 借 固定资产(1601) 贷 在建工程(1604，兼容 160400000)，金额=资产原值</li>
     *  <li>FA_DISPOSE 通过（报废处置）→ 借 累计折旧(1602) + 借 资产处置损益(6117，缺省回落 6711/6721
     *      营业外收支规则，金额=原值-累计折旧净值差额) 贷 固定资产(1601)，金额=资产原值</li>
     * </ul>
     * source_type = bizType，source_doc_id = md_asset.id，existingVoucherId 同源判重防重复生成。
     * 科目缺失（findSubjectIdByCode 为 null）时记 WARN 跳过凭证生成，不阻断审批收尾。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long generateAssetVoucher(MdAsset asset, String bizType) {
        if (asset == null || asset.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资产卡片不存在");
        }
        BigDecimal amt = asset.getOriginalAmount() == null ? BigDecimal.ZERO : asset.getOriginalAmount();
        if (amt.signum() == 0) {
            log.info("资产 {} 原值为 0，跳过{}凭证生成", asset.getAssetNo(), bizType);
            return null;
        }
        Long existed = existingVoucherId(bizType, asset.getId());
        if (existed != null) {
            log.warn("资产 {} 业务类型 {} 已存在凭证 id={}，跳过重复生成（幂等）",
                    asset.getId(), bizType, existed);
            return existed;
        }
        Long fixedSubjectId = findSubjectIdByCode("1601");
        if (fixedSubjectId == null) {
            log.warn("固定资产科目(1601)缺失，资产 {} 跳过{}凭证生成（请先在科目字典维护）", asset.getAssetNo(), bizType);
            // 静默跳过要有结构化记录
            recordGenFailureTrace(bizType, asset.getId(), asset.getAssetNo(),
                    "固定资产科目 1601 缺失，" + bizType + " 凭证未出");
            return null;
        }
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = landingDate(asset.getCompanyId() != null ? asset.getCompanyId() : resolveCompanyId(tenantId), null, null);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(asset.getCompanyId() != null ? asset.getCompanyId() : resolveCompanyId(tenantId));
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(asset.getId());
        v.setSourceType(bizType);
        v.setSourceDocType(bizType);
        v.setSourceDocId(asset.getId());
        String prefix = switch (bizType) {
            case "FA_CIP_TO_ASSET" -> "在建工程转固";
            case "FA_DISPOSE" -> "资产处置";
            default -> "资产新增";
        };
        v.setSummary(prefix + "-" + (asset.getAssetNo() == null ? String.valueOf(asset.getId()) : asset.getAssetNo()));
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        String fixedName = subjectNameOf(fixedSubjectId);
        int no = 10;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        if ("FA_DISPOSE".equals(bizType)) {
            // 报废处置：借 累计折旧(已提足部分) + 借 处置损益(净值差额=原值-累计折旧) 贷 固定资产(原值)
            Long deprSubjectId = findSubjectIdByCode("1602");
            if (deprSubjectId == null) {
                log.warn("累计折旧科目(1602)缺失，资产 {} 跳过处置凭证生成", asset.getAssetNo());
                recordGenFailureTrace(bizType, asset.getId(), asset.getAssetNo(),
                        "累计折旧科目 1602 缺失，处置凭证未出（凭证头已回退删除）");
                opVoucherMapper.deleteById(v.getId());
                return null;
            }
            BigDecimal accum = asset.getAccumDepr() == null ? BigDecimal.ZERO : asset.getAccumDepr();
            if (accum.compareTo(amt) > 0) {
                accum = amt;
            }
            BigDecimal loss = amt.subtract(accum);
            String deprName = subjectNameOf(deprSubjectId);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), no,
                    "累计折旧-处置转销", deprSubjectId, "1602", deprName.isBlank() ? "累计折旧" : deprName,
                    "DEBIT", accum, asset.getDeptId()));
            debit = debit.add(accum);
            no += 10;
            if (loss.signum() > 0) {
                MdSubject lossSubject = findSubjectByAnyCode("6117", "6711", "6721");
                if (lossSubject == null) {
                    log.warn("资产处置损益/营业外支出科目(6117/6711/6721)均缺失，资产 {} 跳过处置凭证生成", asset.getAssetNo());
                    recordGenFailureTrace(bizType, asset.getId(), asset.getAssetNo(),
                            "处置损益科目 6117/6711/6721 均缺失，处置凭证未出（凭证头已回退删除）");
                    opVoucherMapper.deleteById(v.getId());
                    return null;
                }
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), no,
                        "处置净损失", lossSubject.getId(), lossSubject.getSubjectCode(),
                        lossSubject.getSubjectName() == null || lossSubject.getSubjectName().isBlank()
                                ? "资产处置损益" : lossSubject.getSubjectName(),
                        "DEBIT", loss, asset.getDeptId()));
                debit = debit.add(loss);
                no += 10;
            }
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), no,
                    "固定资产-处置转销", fixedSubjectId, "1601", fixedName.isBlank() ? "固定资产" : fixedName,
                    "CREDIT", amt, asset.getDeptId()));
            credit = amt;
        } else if ("FA_CIP_TO_ASSET".equals(bizType)) {
            // 转固：借 固定资产(1601) 贷 在建工程(1604，兼容历史 160400000)
            Long cipSubjectId = findFirstSubjectIdByCode("1604", "160400000");
            if (cipSubjectId == null) {
                log.warn("在建工程科目(1604/160400000)缺失，资产 {} 跳过转固凭证生成", asset.getAssetNo());
                recordGenFailureTrace(bizType, asset.getId(), asset.getAssetNo(),
                        "在建工程科目 1604/160400000 缺失，转固凭证未出（凭证头已回退删除）");
                opVoucherMapper.deleteById(v.getId());
                return null;
            }
            String cipName = subjectNameOf(cipSubjectId);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), no,
                    "固定资产-转固入账", fixedSubjectId, "1601", fixedName.isBlank() ? "固定资产" : fixedName,
                    "DEBIT", amt, asset.getDeptId()));
            debit = amt;
            no += 10;
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), no,
                    "在建工程-转固结转", cipSubjectId, "1604", cipName.isBlank() ? "在建工程" : cipName,
                    "CREDIT", amt, asset.getDeptId()));
            credit = amt;
        } else {
            // FA_ADD：借 固定资产(1601) 贷 银行存款(1002)
            Long bankSubjectId = findSubjectIdByCode("1002");
            if (bankSubjectId == null) {
                log.warn("银行存款科目(1002)缺失，资产 {} 跳过新增凭证生成", asset.getAssetNo());
                recordGenFailureTrace(bizType, asset.getId(), asset.getAssetNo(),
                        "银行存款科目 1002 缺失，资产新增凭证未出（凭证头已回退删除）");
                opVoucherMapper.deleteById(v.getId());
                return null;
            }
            String bankName = subjectNameOf(bankSubjectId);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), no,
                    "固定资产-新增入账", fixedSubjectId, "1601", fixedName.isBlank() ? "固定资产" : fixedName,
                    "DEBIT", amt, asset.getDeptId()));
            debit = amt;
            no += 10;
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), no,
                    "银行存款-购置付款", bankSubjectId, "1002", bankName.isBlank() ? "银行存款" : bankName,
                    "CREDIT", amt, asset.getDeptId()));
            credit = amt;
        }

        OpVoucher update = new OpVoucher();
        update.setId(v.getId());
        update.setDebitTotal(debit);
        update.setCreditTotal(credit);
        update.setIsBalanced(debit.compareTo(credit) == 0 ? 1 : 0);
        opVoucherMapper.updateById(update);
        log.info("资产 {} 生成{}凭证 {} 借/贷合计={}（）",
                asset.getAssetNo(), prefix, v.getVoucherNo(), debit);
        return v.getId();
    }

    /** 按候选科目编码顺序取第一个存在<b>且可制证（末级）</b>的科目 id（全缺则 null） */
    private Long findFirstSubjectIdByCode(String... codes) {
        for (String code : codes) {
            MdSubject s = findSubjectByAnyCode(code);
            if (s == null) {
                continue;
            }
            // 非末级候选直接跳过、继续试下一个：主数据里存在但过不了账的科目不算命中
            if (s.getIsLeaf() != null && s.getIsLeaf() == 0) {
                continue;
            }
            return s.getId();
        }
        return null;
    }

    /** 按候选科目编码顺序取第一个存在的科目实体（全缺则 null） */
    private MdSubject findSubjectByAnyCode(String... codes) {
        for (String code : codes) {
            List<MdSubject> list = mdSubjectMapper.selectList(
                    new LambdaQueryWrapper<MdSubject>().eq(MdSubject::getSubjectCode, code).last("limit 1"));
            if (!list.isEmpty()) {
                return list.get(0);
            }
        }
        return null;
    }

    /**
     * 存货成本结转凭证生成。
     * <p>
     * 按结转类型生成不同科目的会计凭证：
     * <ul>
     *  <li>ISSUE_COST: 借管理费用-物料消耗(660227) / 贷存货(1405)</li>
     *  <li>DISPOSAL_COST: 借资产处置损益(6711) / 贷存货(1405)</li>
     *  <li>SCRAP_COST: 借营业外支出(6721) / 贷存货(1405)</li>
     *  <li>INTERNAL_SALE_COST: 借主营业务成本(6401) / 贷存货(1405)</li>
     *  <li>DEVAL_PROV: 借资产减值损失(6701) / 贷存货跌价准备(1471)</li>
     *  <li>CHECK_DIFF: 借/贷 存货(1405) / 贷/借 待处理财产损溢(1901)</li>
     * </ul>
     */
    public Long generateStockCostVoucher(StockCostSettle settle, String settleType) {
        if (settle == null || settle.getTotalAmount() == null || settle.getTotalAmount().signum() == 0) {
            log.info("存货成本结转 {} 金额为0，跳过凭证生成", settleType);
            return null;
        }
        BigDecimal amt = settle.getTotalAmount();
        Long tenantId = TenantContext.getTenantId();
        int[] ym = parsePeriodYm(settle.getPeriod());
        LocalDate today = landingDate(1L, ym == null ? null : ym[0], ym == null ? null : ym[1]);
        OpVoucher v = new OpVoucher();
        v.setTenantId(tenantId);
        v.setVoucherNo(nextStockVoucherNo());
        v.setVoucherWord("JOURNAL");
        v.setCompanyId(1L);
        v.setPeriodYear(today.getYear());
        v.setPeriodMonth(today.getMonthValue());
        v.setVoucherDate(today);
        v.setBillId(settle.getId());
        v.setSourceType("STOCK_COST_SETTLE");
        v.setSourceDocType("STOCK_COST_SETTLE");
        v.setSourceDocId(settle.getId());
        v.setSummary("存货成本结转-" + settleType + "-" + settle.getPeriod());
        v.setStatus("DRAFT");
        opVoucherMapper.insert(v);

        Long inventorySubjectId = findSubjectIdByCode("1405");
        switch (settleType) {
            case "ISSUE_COST": {
                Long expenseSubjectId = findSubjectIdByCode("660227");
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                        "管理费用-领用", expenseSubjectId, "660227", "管理费用-物料消耗", "DEBIT", amt, null));
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                        "库存商品", inventorySubjectId, "1405", "存货", "CREDIT", amt, null));
                break;
            }
            case "DISPOSAL_COST":
            case "SCRAP_COST": {
                Long lossSubjectId = findSubjectIdByCode("6711");
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                        "资产处置损益", lossSubjectId, "6711", "处置损失", "DEBIT", amt, null));
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                        "库存商品", inventorySubjectId, "1405", "存货", "CREDIT", amt, null));
                break;
            }
            case "INTERNAL_SALE_COST": {
                Long cogsSubjectId = findSubjectIdByCode("6401");
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                        "主营业务成本", cogsSubjectId, "6401", "销售成本", "DEBIT", amt, null));
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                        "库存商品", inventorySubjectId, "1405", "存货", "CREDIT", amt, null));
                break;
            }
            case "DEVAL_PROV": {
                Long impairmentSubjectId = findSubjectIdByCode("6701");
                Long provisionSubjectId = findSubjectIdByCode("1471");
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                        "资产减值损失", impairmentSubjectId, "6701", "减值损失", "DEBIT", amt, null));
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                        "存货跌价准备", provisionSubjectId, "1471", "跌价准备", "CREDIT", amt, null));
                break;
            }
            case "CHECK_DIFF": {
                Long pendingSubjectId = findSubjectIdByCode("1901");
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                        "待处理财产损溢", pendingSubjectId, "1901", "待处理", "DEBIT", amt, null));
                opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                        "库存商品", inventorySubjectId, "1405", "存货", "CREDIT", amt, null));
                break;
            }
            default:
                log.warn("未知的存货结转类型: {}", settleType);
        }
        updateVoucherTotals(v.getId(), amt);
        log.info("存货成本结转 {} 生成凭证 {} 金额={}", settleType, v.getVoucherNo(), amt);
        settle.setSourceVoucherId(v.getId());
        return v.getId();
    }

    // 双向关联查询

    /**
     * 按单据ID查询关联凭证。
     */
    public List<OpVoucher> getVoucherByBillId(Long billId) {
        LambdaQueryWrapper<OpVoucher> qw = new LambdaQueryWrapper<OpVoucher>().eq(OpVoucher::getBillId, billId);
        Long mine = orgGuard.enforceCompany(); // ① 读路径公司隔离
        if (mine != null) {
            qw.eq(OpVoucher::getCompanyId, mine);
        }
        return opVoucherMapper.selectList(qw);
    }

    /**
     * 归档写环节取凭证 ID：bill_id 与 voucher_no 双条件唯一命中才返回。
     * op_voucher.bill_id 是跨域裸主键，单靠它会把别的域的凭证配过来；
     * 命中 0 条或多条一律返回 null，由调用方留空而不是猜。
     */
    public Long findVoucherIdForArchive(Long billId, String voucherNo, Long tenantId) {
        if (billId == null || voucherNo == null || voucherNo.isBlank()) {
            return null;
        }
        List<OpVoucher> hits = opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getBillId, billId)
                .eq(OpVoucher::getVoucherNo, voucherNo));
        if (hits.size() != 1) {
            return null;
        }
        OpVoucher v = hits.get(0);
        if (tenantId != null && !tenantId.equals(v.getTenantId())) {
            return null;
        }
        return v.getId();
    }

    /**
     * 按凭证ID查询关联单据（① 公司可见性校验）。
     */
    public ExpBill getBillByVoucherId(Long voucherId) {
        OpVoucher voucher = opVoucherMapper.selectById(voucherId);
        if (voucher == null || voucher.getBillId() == null) {
            return null;
        }
        orgGuard.assertVisible(voucher.getCompanyId(), null);
        return expBillMapper.selectById(voucher.getBillId());
    }

    // 外部推送接口

    /**
     * 委托VoucherPushService推送凭证至外部系统。
     */
    public Map<String, Object> pushToExternal(Long voucherId) {
        return voucherPushService.pushToExternal(voucherId);
    }

    // 收货自动暂估与发票冲回

    /** 存货科目编码（暂估借方/冲回贷方） */
    private static final String SUBJ_INVENTORY = "1405";
    /** 暂估应付科目编码（科目表未预置时暂估整体跳过并 warn，见 sql/impl-cl-20260914.sql DML） */
    private static final String SUBJ_ESTIMATE_PAYABLE = "2203";
    /** 应付账款（正式入账贷方） */
    private static final String SUBJ_PAYABLE = "2202";

    /**
     * 收货暂估凭证：PurchaseService.createReceipt 成功后调用。
     * 借 存货(1405) / 贷 暂估应付(2203)，金额=收货金额；source_type='ESTIMATE'，source_doc_id=收货单ID。
     * 收货单挂 CIP（cipId 从采购订单继承）时借方改为在建工程(1604)，成本自动归集 CIP。
     * <ul>
     *  <li>去重：同一收货单已存在 ESTIMATE 凭证（VOID 除外）则跳过；</li>
     *  <li>金额为 0 跳过；暂估/存货科目不存在时跳过并 warn（不阻断收货主流程）；</li>
     *  <li>所有内部异常吞掉仅记日志——凭证缺失可人工补录，不影响收货单本身。</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void generateEstimateOnReceipt(PoReceipt receipt) {
        try {
            if (receipt == null || receipt.getId() == null) {
                return;
            }
            BigDecimal amt = receipt.getAmount() == null ? BigDecimal.ZERO : receipt.getAmount();
            if (amt.signum() == 0) {
                log.info("[暂估] 收货单 {} 金额为 0，跳过暂估凭证", receipt.getReceiptNo());
                return;
            }
            Long existed = existingVoucherId("ESTIMATE", receipt.getId());
            if (existed != null) {
                log.info("[暂估] 收货单 {} 已存在暂估凭证 id={}，跳过（幂等）", receipt.getReceiptNo(), existed);
                return;
            }
            Long inventoryId = findSubjectIdByCode(SUBJ_INVENTORY);
            Long estimateId = findSubjectIdByCode(SUBJ_ESTIMATE_PAYABLE);
            if (inventoryId == null || estimateId == null) {
                log.warn("[暂估] 收货单 {} 暂估凭证跳过：科目缺失（{}存货={} / {}暂估应付={}），请先维护科目（见 sql/impl-cl-20260914.sql）",
                        receipt.getReceiptNo(), SUBJ_INVENTORY, inventoryId, SUBJ_ESTIMATE_PAYABLE, estimateId);
                // 静默跳过也要有结构化记录（本分支不抛错、事务照提交）
                recordGenFailureTrace("ESTIMATE", receipt.getId(), receipt.getReceiptNo(),
                        "收货暂估凭证未出：科目缺失（" + SUBJ_INVENTORY + "存货/" + SUBJ_ESTIMATE_PAYABLE + "暂估应付）");
                return;
            }
            // 收货单挂 CIP（从采购订单继承）时，借方由存货(1405)改为在建工程(1604)，成本自动归集 CIP；
            // 科目 1604 缺失则回退存货科目并 warn（不阻断）。贷方仍为暂估应付(2203)。
            Long debitSubjectId = inventoryId;
            String debitSubjectCode = SUBJ_INVENTORY;
            String debitSubjectName = "库存商品";
            String debitSummary = "存货-收货暂估";
            if (receipt.getCipId() != null) {
                Long cipSubjectId = findSubjectIdByCode(SUBJ_CIP);
                if (cipSubjectId != null) {
                    debitSubjectId = cipSubjectId;
                    debitSubjectCode = SUBJ_CIP;
                    debitSubjectName = "在建工程";
                    debitSummary = "在建工程-收货暂估(CIP)";
                } else {
                    log.warn("[暂估] 收货单 {} 挂 CIP 但在建工程科目 {} 缺失，借方回退存货科目 {}",
                            receipt.getReceiptNo(), SUBJ_CIP, SUBJ_INVENTORY);
                }
            }
            Long tenantId = TenantContext.getTenantId();
            LocalDate today = landingDate(resolveCompanyId(tenantId), null, null);
            Long companyId = resolveCompanyId(tenantId);
            OpVoucher v = new OpVoucher();
            v.setTenantId(tenantId);
            v.setVoucherNo(nextVoucherNo());
            v.setVoucherWord("JOURNAL");
            v.setCompanyId(companyId);
            v.setPeriodYear(today.getYear());
            v.setPeriodMonth(today.getMonthValue());
            v.setVoucherDate(today);
            v.setBillId(receipt.getId());
            v.setSourceType("ESTIMATE");
            v.setSourceDocType("PO_RECEIPT");
            v.setSourceDocId(receipt.getId());
            v.setSummary("收货暂估-" + (receipt.getReceiptNo() == null ? "" : receipt.getReceiptNo()));
            v.setStatus("DRAFT");
            opVoucherMapper.insert(v);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                    debitSummary, debitSubjectId, debitSubjectCode, debitSubjectName, "DEBIT", amt, null));
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                    "暂估应付-收货", estimateId, SUBJ_ESTIMATE_PAYABLE, "暂估应付", "CREDIT", amt, null));
            updateVoucherTotals(v.getId(), amt);
            log.info("[暂估] 收货单 {} 生成暂估凭证 {} 借{}({})/贷暂估应付({}) 金额={} cipId={}",
                    receipt.getReceiptNo(), v.getVoucherNo(), debitSubjectName, debitSubjectCode,
                    SUBJ_ESTIMATE_PAYABLE, amt, receipt.getCipId());
        } catch (Exception ex) {
            log.error("[暂估] 收货单 {} 自动暂估凭证生成失败（不阻断收货主流程）: {}",
                    receipt == null ? null : receipt.getReceiptNo(), ex.getMessage(), ex);
            // 异常在本环节被吞掉（不反噬收货），但"吞掉"不等于"无痕"——异常同样落结构化记录。
            // 注意：此 catch 已是本方法的最后一环、不再抛错，故 audit_trail 能随收货事务提交。
            if (receipt != null) {
                recordGenFailureTrace("ESTIMATE", receipt.getId(), receipt.getReceiptNo(),
                        "收货暂估凭证生成异常：" + ex.getClass().getSimpleName() + " " + ex.getMessage());
            }
        }
    }

    /**
     * 发票与收货单匹配时的冲回+正式入账（PurchaseService.markInvoiced 触发）：
     * <ol>
     *  <li>红字冲回暂估（借 暂估应付(2203) / 贷 存货(1405)）：source_type='ESTIMATE_REVERSE'，
     *      source_doc_id=收货单ID，同一收货单仅冲回一次（去重）；</li>
     *  <li>正式入账（借 存货(1405) / 贷 应付账款(2202)）：source_type='RECEIPT_INVOICE'，
     *      source_doc_id=发票ID × bill_id=收货单ID，同一「发票×收货单」仅入账一次（去重）；
     *      金额均取本次发票匹配金额。</li>
     * </ol>
     * <p>入账唯一键是「发票×收货单」成对，不是「同一发票仅入账一次」：一张票分两次
     * 部分关联到两张收货单时，按发票判重会让第二次只出暂估冲回、不出入账，
     * 该票未入账的部分就不进存货借方/应付贷方，账实不符。
     * 成对判重下两张收货单各自成账，同一对重放仍去重（与冲回环节「按收货单」判重对称）。</p>
     * 暂估冲回要求暂估科目存在；不存在时仅生成正式入账分录并 warn。全部异常吞掉记日志，不阻断发票绑定主流程。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void reverseEstimateOnInvoiceMatch(Long receiptId, String receiptNo, Long invoiceId,
                                              String invoiceNo, BigDecimal matchedAmount) {
        try {
            BigDecimal amt = matchedAmount == null ? BigDecimal.ZERO : matchedAmount;
            if (receiptId == null || invoiceId == null || amt.signum() == 0) {
                return;
            }
            Long tenantId = TenantContext.getTenantId();
            LocalDate today = landingDate(resolveCompanyId(tenantId), null, null);
            Long companyId = resolveCompanyId(tenantId);
            Long inventoryId = findSubjectIdByCode(SUBJ_INVENTORY);
            Long estimateId = findSubjectIdByCode(SUBJ_ESTIMATE_PAYABLE);
            if (inventoryId == null) {
                log.warn("[暂估冲回] 收货单 {} 存货科目 {} 缺失，跳过暂估冲回/正式入账", receiptNo, SUBJ_INVENTORY);
                // 静默跳过要有结构化记录
                recordGenFailureTrace("ESTIMATE_REVERSE", receiptId, receiptNo,
                        "存货科目 " + SUBJ_INVENTORY + " 缺失，暂估冲回与发票正式入账两笔分录均未生成");
                return;
            }
            // ① 红字冲回暂估（同一收货单仅一次）
            Long reversed = existingVoucherId("ESTIMATE_REVERSE", receiptId);
            if (reversed == null) {
                if (estimateId == null) {
                    log.warn("[暂估冲回] 暂估应付科目 {} 缺失，收货单 {} 无法生成红字冲回凭证（见 sql/impl-cl-20260914.sql）",
                            SUBJ_ESTIMATE_PAYABLE, receiptNo);
                    // 只缺冲回这一张，正式入账照走 ⇒ 按"未出证"记在冲回来源上
                    recordGenFailureTrace("ESTIMATE_REVERSE", receiptId, receiptNo,
                            "红字冲回凭证未出：暂估应付科目 " + SUBJ_ESTIMATE_PAYABLE + " 缺失");
                } else {
                    OpVoucher rv = new OpVoucher();
                    rv.setTenantId(tenantId);
                    rv.setVoucherNo(nextVoucherNo());
                    rv.setVoucherWord("JOURNAL");
                    rv.setCompanyId(companyId);
                    rv.setPeriodYear(today.getYear());
                    rv.setPeriodMonth(today.getMonthValue());
                    rv.setVoucherDate(today);
                    rv.setBillId(receiptId);
                    rv.setSourceType("ESTIMATE_REVERSE");
                    rv.setSourceDocType("PO_RECEIPT");
                    rv.setSourceDocId(receiptId);
                    rv.setSummary("暂估冲回-" + (receiptNo == null ? String.valueOf(receiptId) : receiptNo)
                            + "（发票 " + (invoiceNo == null ? String.valueOf(invoiceId) : invoiceNo) + " 匹配）");
                    rv.setStatus("DRAFT");
                    opVoucherMapper.insert(rv);
                    opVoucherLineMapper.insert(buildLine(tenantId, rv.getId(), 10,
                            "暂估应付-冲回", estimateId, SUBJ_ESTIMATE_PAYABLE, "暂估应付", "DEBIT", amt, null));
                    opVoucherLineMapper.insert(buildLine(tenantId, rv.getId(), 20,
                            "存货-暂估冲回", inventoryId, SUBJ_INVENTORY, "库存商品", "CREDIT", amt, null));
                    updateVoucherTotals(rv.getId(), amt);
                    log.info("[暂估冲回] 收货单 {} 生成红字冲回凭证 {} 借暂估应付({})/贷存货({}) 金额={}",
                            receiptNo, rv.getVoucherNo(), SUBJ_ESTIMATE_PAYABLE, SUBJ_INVENTORY, amt);
                }
            } else {
                log.info("[暂估冲回] 收货单 {} 已存在冲回凭证 id={}，跳过（幂等）", receiptNo, reversed);
            }
            // ② 正式入账（同一「发票×收货单」仅一次）
            Long formal = existingInvoiceBookingId(invoiceId, receiptId);
            if (formal != null) {
                log.info("[正式入账] 发票 {} × 收货单 {} 已存在正式入账凭证 id={}，跳过（幂等）",
                        invoiceNo, receiptNo, formal);
                return;
            }
            Long payableId = findSubjectIdByCode(SUBJ_PAYABLE);
            if (payableId == null) {
                log.warn("[正式入账] 发票 {} 应付账款科目 {} 缺失，跳过正式入账", invoiceNo, SUBJ_PAYABLE);
                // 静默跳过要有结构化记录
                recordGenFailureTrace("RECEIPT_INVOICE", invoiceId, invoiceNo,
                        "发票正式入账凭证未出：应付账款科目 " + SUBJ_PAYABLE + " 缺失");
                return;
            }
            OpVoucher fv = new OpVoucher();
            fv.setTenantId(tenantId);
            fv.setVoucherNo(nextVoucherNo());
            fv.setVoucherWord("JOURNAL");
            fv.setCompanyId(companyId);
            fv.setPeriodYear(today.getYear());
            fv.setPeriodMonth(today.getMonthValue());
            fv.setVoucherDate(today);
            fv.setBillId(receiptId);
            fv.setSourceType("RECEIPT_INVOICE");
            fv.setSourceDocType("INV_INVOICE");
            fv.setSourceDocId(invoiceId);
            fv.setSummary("发票入账-" + (invoiceNo == null ? String.valueOf(invoiceId) : invoiceNo)
                    + "（收货单 " + (receiptNo == null ? String.valueOf(receiptId) : receiptNo) + "）");
            fv.setStatus("DRAFT");
            opVoucherMapper.insert(fv);
            opVoucherLineMapper.insert(buildLine(tenantId, fv.getId(), 10,
                    "存货-发票入账", inventoryId, SUBJ_INVENTORY, "库存商品", "DEBIT", amt, null));
            opVoucherLineMapper.insert(buildLine(tenantId, fv.getId(), 20,
                    "应付账款-发票", payableId, SUBJ_PAYABLE, "应付账款", "CREDIT", amt, null));
            updateVoucherTotals(fv.getId(), amt);
            log.info("[正式入账] 发票 {} 生成正式入账凭证 {} 借存货({})/贷应付账款({}) 金额={}",
                    invoiceNo, fv.getVoucherNo(), SUBJ_INVENTORY, SUBJ_PAYABLE, amt);
        } catch (Exception ex) {
            log.error("[暂估冲回] 收货单 {} 发票 {} 匹配后凭证生成失败（不阻断发票绑定主流程）: {}",
                    receiptNo, invoiceNo, ex.getMessage(), ex);
            // 异常被吞掉也要有痕（本 catch 是方法末环、不再抛错）
            recordGenFailureTrace("ESTIMATE_REVERSE", receiptId, receiptNo,
                    "暂估冲回/正式入账生成异常：" + ex.getClass().getSimpleName() + " " + ex.getMessage());
        }
    }

    // CIP 付款自动凭证

    /** 在建工程科目（CIP 付款借方） */
    private static final String SUBJ_CIP = "1604";

    /**
     * CIP 付款凭证：付款单/付款批次引用在建工程（exp_bill.extJson.cipId 或
     * 付款批次 source_doc_type=CIP）且付款审批通过后调用。
     * 借 在建工程(1604) / 贷 银行存款(1002)，并回写 md_cip.paid_amount。
     * 去重：sourceType+sourceDocId 已有凭证（VOID 除外）则跳过；失败仅记日志不阻断审批收尾。
     *
     * @param cip 在建工程项目
     * @param amount 本次付款金额
     * @param sourceDocId 源单据ID（付款单=exp_bill.id / 付款批次=op_pay_batch.id）
     * @param sourceType 唯一键类型（CIP_PAY 付款单 / CIP_PAY_BATCH 付款批次）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void generateCipPaymentVoucher(MdCip cip, BigDecimal amount,
                                          Long sourceDocId, String sourceType) {
        try {
            if (cip == null || cip.getId() == null || sourceDocId == null || sourceType == null) {
                return;
            }
            BigDecimal amt = amount == null ? BigDecimal.ZERO : amount;
            if (amt.signum() == 0) {
                log.info("[CIP付款] 在建工程 {} 付款金额为 0，跳过凭证", cip.getCipCode());
                return;
            }
            Long existed = existingVoucherId(sourceType, sourceDocId);
            if (existed != null) {
                log.info("[CIP付款] 源单据 {} 已存在 CIP 付款凭证 id={}，跳过（幂等）", sourceDocId, existed);
                return;
            }
            // CIP 付款环节（借 1604／贷 1002）同受出纳参数约束，默认＝否＝恒放行
            if (payLegNotYetPayable(sourceType, sourceDocId, cip.getCipCode())) {
                return;
            }
            Long cipSubjectId = findSubjectIdByCode(SUBJ_CIP);
            Long bankSubjectId = findSubjectIdByCode("1002");
            if (cipSubjectId == null || bankSubjectId == null) {
                log.warn("[CIP付款] 在建工程 {} 凭证跳过：科目缺失（{}在建工程={} / 1002银行存款={}）",
                        cip.getCipCode(), SUBJ_CIP, cipSubjectId, bankSubjectId);
                // 静默跳过要有结构化记录
                recordGenFailureTrace(sourceType, sourceDocId, cip.getCipCode(),
                        "在建工程付款凭证未出：科目缺失（" + SUBJ_CIP + "在建工程/1002银行存款）");
                return;
            }
            Long tenantId = TenantContext.getTenantId();
            LocalDate today = landingDate(cip.getCompanyId() != null ? cip.getCompanyId() : resolveCompanyId(tenantId), null, null);
            OpVoucher v = new OpVoucher();
            v.setTenantId(tenantId);
            v.setVoucherNo(nextVoucherNo());
            v.setVoucherWord("JOURNAL");
            v.setCompanyId(cip.getCompanyId() != null ? cip.getCompanyId() : resolveCompanyId(tenantId));
            v.setPeriodYear(today.getYear());
            v.setPeriodMonth(today.getMonthValue());
            v.setVoucherDate(today);
            v.setBillId(sourceDocId);
            v.setSourceType(sourceType);
            v.setSourceDocType("MD_CIP");
            v.setSourceDocId(sourceDocId);
            v.setSummary("在建工程付款-" + cip.getCipCode() + " " + (cip.getCipName() == null ? "" : cip.getCipName()));
            v.setStatus("DRAFT");
            opVoucherMapper.insert(v);
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 10,
                    "在建工程-" + cip.getCipCode(), cipSubjectId, SUBJ_CIP, "在建工程", "DEBIT", amt, cip.getDeptId()));
            opVoucherLineMapper.insert(buildLine(tenantId, v.getId(), 20,
                    "银行存款-CIP付款", bankSubjectId, "1002", "银行存款", "CREDIT", amt, null));
            updateVoucherTotals(v.getId(), amt);
            log.info("[CIP付款] 在建工程 {} 生成付款凭证 {} 借在建工程({})/贷银行存款(1002) 金额={}",
                    cip.getCipCode(), v.getVoucherNo(), SUBJ_CIP, amt);
            // 回写 CIP 项目已付款金额（去重由上方凭证判重保证）
            cipPaymentWriteBack(cip.getId(), amt);
        } catch (Exception ex) {
            log.error("[CIP付款] 在建工程 {} CIP 付款凭证生成失败（不阻断付款审批收尾）: {}",
                    cip == null ? null : cip.getCipCode(), ex.getMessage(), ex);
            // 异常被吞掉也要有痕
            if (cip != null) {
                recordGenFailureTrace(sourceType, sourceDocId, cip.getCipCode(),
                        "在建工程付款凭证生成异常：" + ex.getClass().getSimpleName() + " " + ex.getMessage());
            }
        }
    }

    /** 回写在建工程已付款金额（paid_amount += amount，列见 sql/impl-cl-20260914.sql DDL） */
    private void cipPaymentWriteBack(Long cipId, BigDecimal amount) {
        try {
            MdCip fresh = cipMapper.selectById(cipId);
            if (fresh == null) {
                return;
            }
            MdCip upd = new MdCip();
            upd.setId(cipId);
            upd.setPaidAmount((fresh.getPaidAmount() == null ? BigDecimal.ZERO : fresh.getPaidAmount()).add(amount));
            cipMapper.updateById(upd);
            log.info("[CIP付款] 在建工程 {} 已付款金额回写 {}（累计 {}）",
                    fresh.getCipCode(), amount, upd.getPaidAmount());
        } catch (Exception ex) {
            log.error("[CIP付款] 在建工程 {} 已付款金额回写失败: {}", cipId, ex.getMessage(), ex);
        }
    }
}
