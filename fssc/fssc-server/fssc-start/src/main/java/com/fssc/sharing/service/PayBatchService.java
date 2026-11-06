// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.budget.service.BudgetService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.service.FraudDetectionService;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.entity.ExpBillLine;
import com.fssc.expense.entity.ExpBillPayment;
import com.fssc.expense.mapper.ExpBillLineMapper;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.expense.mapper.ExpBillPaymentMapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.masterdata.entity.MdBankAccount;
import com.fssc.masterdata.entity.MdPayee;
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.entity.MdSupplierBank;
import com.fssc.masterdata.mapper.MdBankAccountMapper;
import com.fssc.masterdata.mapper.MdPayeeMapper;
import com.fssc.masterdata.mapper.MdSupplierBankMapper;
import com.fssc.masterdata.mapper.MdSupplierMapper;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.masterdata.service.MdCipService;
import com.fssc.payplan.service.PlanWritebackService;
import com.fssc.sharing.entity.OpPayBatch;
import com.fssc.sharing.entity.OpPayBatchDetail;
import com.fssc.sharing.entity.OpVoucher;
import com.fssc.sharing.mapper.OpPayBatchDetailMapper;
import com.fssc.sharing.mapper.OpPayBatchMapper;
import com.fssc.bank.gateway.BankGateway;
import com.fssc.bank.gateway.BankResult;
import com.fssc.sharing.mapper.OpVoucherMapper;
import com.fssc.system.service.NumberRuleService;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.service.BillCompletenessGuard;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 出纳批量付款服务。
 * 链路：单据付款记录（WAITING）→ 建批（DRAFT）→ 加明细 → 确认（CONFIRMED）→
 * 执行付款（走 BankGateway，默认模拟银行；生产换实现即接银企直联）→ 回写付款记录与单据 paid/payable。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayBatchService {

    private final OpPayBatchMapper opPayBatchMapper;
    private final OpPayBatchDetailMapper opPayBatchDetailMapper;
    private final ExpBillPaymentMapper expBillPaymentMapper;
    private final ExpBillMapper expBillMapper;
    private final MdSupplierMapper mdSupplierMapper;
    private final MdPayeeMapper mdPayeeMapper;
    private final BankGateway bankGateway;
    private final ThreeWayMatchService threeWayMatchService;
    /**
     * 反欺诈：硬阻断位从"提单"下移到"付款指令执行前"——高风险单据在打款前必须经人工复审结案
     * （{@code fraud_detection_log.reviewed_by} 非空或处置 RELEASE），否则整批拒付。
     */
    private final FraudDetectionService fraudDetectionService;
    private final WfEngineFacade wfEngineFacade;
    private final NumberRuleService numberRuleService;
    private final VoucherAutoPostService voucherAutoPostService;
    /** 批次列表「凭证/记账」列补充读侧（只读，不参与付款事务） */
    private final OpVoucherMapper opVoucherMapper;
    /** 付款批次关联在建工程（source_doc_type=CIP）时的成本自动归集 */
    private final MdCipService mdCipService;
    /** 付款批次打款成功→采购付款计划核销回写（D6："付款完成回写计划已付款"） */
    private final PlanWritebackService planWritebackService;
    /**
     * F2 资料齐套校验。两层调用点：{@code confirm}（CONFIRM）与 {@code execute}（EXECUTE 二次校验，
     * 产品确认 RA-F2d／REF-B「两点都拦」——确认时点不撤，打款前再查一次）。
     */
    private final BillCompletenessGuard billCompletenessGuard;
    /** 自动组批时取本公司默认出账账户，不再写死不存在的外键 */
    private final MdBankAccountMapper mdBankAccountMapper;
    /** ⑨：单据全额付讫（APPROVED→PAID）时办结其共享作业链残留池任务 */
    private final SharingService sharingService;
    /**
     * 收款账号查询收敛——供应商档案开户信息（{@code md_supplier_bank}）
     * 与 {@code md_payee} 一起构成"账号有没有档案支撑"的判据来源（只读）。
     */
    private final MdSupplierBankMapper mdSupplierBankMapper;
    /**
     * ①：批次列表补充时解析<b>批次自身</b>的资金付款审批（TR_PAY）实例（只读，wf_instance 权威映射）。
     * 不动 {@code op_pay_batch.proc_inst_id}——那一列按 存的是源单据流，挪用即拆源单据追溯链。
     */
    private final WfInstanceMapper wfInstanceMapper;
    /**
     * 预算「占用 → 已用」的确认出口。打款成功（本类 {@code execute}）才是消耗时点，
     * 不再是单据/批次审批通过。
     */
    private final BudgetService budgetService;
    /** 付款成功后按单据明细行确认预算消耗，需要读 exp_bill_line */
    private final ExpBillLineMapper expBillLineMapper;

    /**
     * 批次名下凭证的批次级来源族——出纳付款凭证（TREASURY_PAY）与 CIP 批次付款凭证
     * CIP_PAY_BATCH。与列表「凭证/记账」列的批次级查询同源，一律按
     * source_doc_id 成对精确匹配批次主键，不与单据级 bill_id 混用（跨域裸主键会撞号）。
     */
    private static final Set<String> BATCH_LEVEL_VOUCHER_SOURCES =
            Set.of("TREASURY_PAY", "CIP_PAY_BATCH");

    /**
     * PAID 收尾校验未过时落在 fail_reason 的异常记录规则（列表红色标记另见 voucherAnomaly）。
     * 刻意不回退为 FAILED（会被"可重试"通道再次打款）也不新造状态值（groupWaiting 的
     * 防重复组批只认 status='PAID'，新值会让已付单据重新可组批）——款项事实保留，异常显式记录并禁付。
     */
    private static final String PAID_NO_VOUCHER_NOTE =
            "账证不符：打款已成功（款项已付出），但批次名下查无任何有效凭证（单据级/批次级皆无）。"
                    + "不得静默视为正常已付款——请到凭证工作台核对并补制付款凭证后人工核销";

    // 付款记录

    /** 从已审批单据生成付款记录（WAITING） */
    public Long createPayment(Long billId, ExpBillPayment req) {
        ExpBill bill = expBillMapper.selectById(billId);
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据不存在: " + billId);
        }
        if (!"APPROVED".equals(bill.getStatus()) && !"PAY".equals(bill.getBillType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已审批付款单可生成付款记录");
        }
        String payeeType = req.getPayeeType() == null ? "SUPPLIER" : req.getPayeeType();
        // 供应商黑名单对公付款强拦：收方为供应商且命中黑名单 → 禁止发起付款
        assertSupplierNotBlacklisted(payeeType, req.getPayeeId());
        // 付款账号与主数据一致性校验：快照账号须在 md_payee 中登记且未停用（防付错）
        assertPayeeAccountConsistent(payeeType, req.getPayeeId(), req.getBankAccount());
        ExpBillPayment p = new ExpBillPayment();
        p.setTenantId(TenantContext.getTenantId());
        p.setBillId(billId);
        p.setLineNo(1);
        p.setPayeeType(payeeType);
        p.setPayeeId(req.getPayeeId());
        p.setPayeeName(req.getPayeeName());
        p.setBankName(req.getBankName());
        p.setBankAccount(req.getBankAccount());
        p.setPayMethod("BANK");
        p.setCurrencyCode("CNY");
        p.setAmount(req.getAmount() == null ? bill.getPayableAmount() : req.getAmount());
        p.setPayStatus("WAITING");
        p.setCreateTime(LocalDateTime.now());
        p.setProcInstId(bill.getProcInstId());
        expBillPaymentMapper.insert(p);
        return p.getId();
    }

    public List<ExpBillPayment> payments(Long billId) {
        return expBillPaymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getBillId, billId));
    }

    /**
     * 供应商黑名单对公付款强拦：仅当收方类型为供应商（对公付款）时校验，
     * 命中黑名单（blacklist=1）一律禁止发起付款。employee/other 不受影响。
     */
    private void assertSupplierNotBlacklisted(String payeeType, Long payeeId) {
        if (!"SUPPLIER".equalsIgnoreCase(payeeType) || payeeId == null) {
            return;
        }
        MdSupplier supplier = mdSupplierMapper.selectById(payeeId);
        if (supplier != null && Integer.valueOf(1).equals(supplier.getBlacklist())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "供应商[" + supplier.getSupplierName() + "(" + supplier.getSupplierCode() + ")] 已列入黑名单，禁止对公付款");
        }
    }

    /**
     * #14 付款账号与主数据一致性校验（§六）：付款快照中的银行账号必须能在收款方主数据
     * md_payee 中命中且未停用。
     *
     * <p>付款记录存的是 payee 快照，若主数据侧账号已变更/停用而快照仍是旧值，
     * 按快照付款会直接付错对象。故发起付款前与主数据比对：账号未登记 → 拦截；
     * 账号已停用 → 拦截。主数据中无该收款方任何账号记录时跳过（历史数据兼容，避免一刀切阻断）。
     */
    private void assertPayeeAccountConsistent(String payeeType, Long payeeId, String bankAccount) {
        if (payeeId == null || bankAccount == null || bankAccount.isBlank()) {
            return;
        }
        List<MdPayee> accounts = mdPayeeMapper.selectList(new LambdaQueryWrapper<MdPayee>()
                .eq(MdPayee::getPayeeType, payeeType)
                .eq(MdPayee::getRefId, payeeId));
        if (accounts.isEmpty()) {
            return; // 该收款方未登记任何主数据账号，无从比对
        }
        MdPayee hit = accounts.stream()
                .filter(a -> bankAccount.equals(a.getBankAccount()))
                .findFirst().orElse(null);
        if (hit == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "付款账号与主数据不一致：账号未在收款方主数据中登记，禁止付款（防付错）");
        }
        if ("DISABLED".equalsIgnoreCase(String.valueOf(hit.getStatus()))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款账号已停用，禁止付款");
        }
    }

    // 付款侧控制点

    /**
     * 控制点「禁用供应商」：付款指令发出前的供应商状态校验。
     *
     * <p>只认 {@code md_supplier.blacklist} 不足以表达"禁用"——禁用是用 {@code status='DISABLED'}
     * 写的，不并查状态列，"禁用供应商不得付款"这条控制在付款侧等于不存在。
     * 本校验把二者并集视为不可付（黑名单或状态非 NORMAL/ACTIVE）。</p>
     *
     * <p>解析规则：{@code payee_id} 优先，为空时按 {@code payee_name} 精确反查
     * （{@code exp_bill_payment.payee_id} 常为空，只按 ID 查这道校验形同虚设）。
     * 反查不到＝收款方不在供应商主数据里（历史单据/员工付款/名称变更），<b>不臆断成"已禁用"</b>，
     * 只留 WARN 痕；主数据侧补齐后自然收敛为硬拦。</p>
     */
    private void assertSupplierNotDisabled(String payeeType, Long payeeId, String payeeName) {
        if (!"SUPPLIER".equalsIgnoreCase(payeeType)) {
            return; // 员工/其他收款方不走供应商通道
        }
        MdSupplier supplier = null;
        if (payeeId != null) {
            supplier = mdSupplierMapper.selectById(payeeId);
        }
        if (supplier == null && payeeName != null && !payeeName.isBlank()) {
            supplier = mdSupplierMapper.selectOne(new LambdaQueryWrapper<MdSupplier>()
                    .eq(MdSupplier::getSupplierName, payeeName)
                    .last("limit 1"));
        }
        if (supplier == null) {
            log.warn("付款控制留痕：收款方[{}](payeeId={}) 在 md_supplier 中不存在，"
                    + "供应商状态无从判定，本次不按猜测拒付；请补录供应商主数据后该笔即受状态闸约束",
                    payeeName, payeeId);
            return;
        }
        String st = supplier.getStatus() == null ? "" : supplier.getStatus().trim().toUpperCase();
        // fail-closed：除 NORMAL/ACTIVE 外（含空值与未知脏值）一律视为不可付
        boolean disabled = !("NORMAL".equals(st) || "ACTIVE".equals(st));
        if (Integer.valueOf(1).equals(supplier.getBlacklist()) || disabled) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "付款控制拦截（供应商不可付）：" + supplier.getSupplierName()
                            + "(" + supplier.getSupplierCode() + ") 状态="
                            + (Integer.valueOf(1).equals(supplier.getBlacklist()) ? "黑名单" : st)
                            + "，禁止对外付款。"
                            + "处理出口：到 供应商主数据 恢复状态/解除黑名单，或改派其他合规供应商后重新组批。");
        }
    }

    /**
     * 控制点「收款方主数据无账户」：账号未经主数据核对的记录位。
     * <p>{@code assertPayeeAccountConsistent} 在收款方 {@code md_payee} 零账户时直接 return
     * （历史数据兼容），于是"无账户"与"账户已核对"在台账上长得一样。付款执行前补一条如实记录，
     * 不改变放行结论（硬拦会把尚未建账户的收款方一并锁死，分级属校验策略，交回产品确认）。</p>
     */
    private void warnIfPayeeWithoutMasterAccount(String payeeType, Long payeeId, String payeeName, Long billId) {
        if (payeeId == null) {
            log.warn("付款控制留痕：单据 {} 收款方[{}] 未关联主数据 ID，账号无法与 md_payee 核对",
                    billId, payeeName);
            return;
        }
        long cnt = mdPayeeMapper.selectCount(new LambdaQueryWrapper<MdPayee>()
                .eq(MdPayee::getPayeeType, payeeType)
                .eq(MdPayee::getRefId, payeeId));
        if (cnt == 0) {
            log.warn("付款控制留痕：收款方[{}](id={}, type={}) 在 md_payee 无任何账户登记，"
                    + "本次付款账号未经主数据核对（单据 {}）", payeeName, payeeId, payeeType, billId);
        }
    }

    // 收款账号查询收敛

    /**
     * 解析收款方对应的<b>供应商档案</b> id。
     * <p>规则与 {@link #assertSupplierNotDisabled} 同源——{@code payee_id} 优先，为空按 {@code payee_name}
     * 精确反查（{@code exp_bill_payment.payee_id} 对供应商行常为 NULL，只认 ID 这道收不住）。
     * 员工/其他收款方不走供应商档案（工资卡另有规则），返回 null 即不参与本判据。
     */
    private Long resolveSupplierArchiveId(String payeeType, Long payeeId, String payeeName) {
        if (!"SUPPLIER".equalsIgnoreCase(payeeType)) {
            return null;
        }
        if (payeeId != null && mdSupplierMapper.selectById(payeeId) != null) {
            return payeeId;
        }
        if (payeeName != null && !payeeName.isBlank()) {
            MdSupplier supplier = mdSupplierMapper.selectOne(new LambdaQueryWrapper<MdSupplier>()
                    .eq(MdSupplier::getSupplierName, payeeName)
                    .last("limit 1"));
            if (supplier != null) {
                return supplier.getId();
            }
        }
        return null;
    }

    /**
     * 供应商档案登记在案的开户账号集合 = md_supplier_bank ∪ md_payee(SUPPLIER)，
     * 两处均只取未停用行（{@code del_flag} 由 {@code @TableLogic} 自动过滤）。
     * <p>返回空集＝该供应商压根没有开户信息（现网实证：东昇 id=53 有档案无账户行、宏远无档案）。
     */
    private Set<String> archivedPayeeAccounts(Long supplierId) {
        Set<String> accounts = new LinkedHashSet<>();
        if (supplierId == null) {
            return accounts;
        }
        for (MdSupplierBank b : mdSupplierBankMapper.selectList(
                new LambdaQueryWrapper<MdSupplierBank>()
                        .eq(MdSupplierBank::getSupplierId, supplierId))) {
            if (accountUsable(b.getStatus()) && b.getBankAccount() != null && !b.getBankAccount().isBlank()) {
                accounts.add(b.getBankAccount().trim());
            }
        }
        for (MdPayee p : mdPayeeMapper.selectList(new LambdaQueryWrapper<MdPayee>()
                .eq(MdPayee::getPayeeType, "SUPPLIER")
                .eq(MdPayee::getRefId, supplierId))) {
            if (accountUsable(p.getStatus()) && p.getBankAccount() != null && !p.getBankAccount().isBlank()) {
                accounts.add(p.getBankAccount().trim());
            }
        }
        return accounts;
    }

    /** 档案账户可用性：status 空值按未停用处理（历史数据不臆断成已停用），DISABLED/CLOSED 视为不可用。 */
    private static boolean accountUsable(String status) {
        String st = status == null ? "" : status.trim().toUpperCase();
        return !("DISABLED".equals(st) || "CLOSED".equals(st) || "VOID".equals(st));
    }

    /**
     * <b>唯一一处</b>账号完整性判据——付款账号是否有该供应商档案开户信息支撑。
     *
     * <p>不做字符集/长度强制校验——那是把合法账号一次性洗掉的写法，真正的风险是"账号无出处"而不是"账号不好看"：
     * 一个长度与字符都合法、却没有任何档案能解释来源的账号，照样过不了本判据，
     * 所以判据落在"档案里查得到/查不到"。
     *
     * <p>只读、不抛错、不改状态；档案读取异常时<b>放行</b>（不因主数据抖动锁死付款）。
     *
     * @return null＝可提交银企（非供应商收款方、档案读取异常亦含在此）；非空＝给人看的缺档说明
     */
    private String payeeAccountArchiveIssue(String payeeType, Long payeeId, String payeeName, String bankAccount) {
        if (!"SUPPLIER".equalsIgnoreCase(payeeType)) {
            return null;
        }
        if (bankAccount == null || bankAccount.isBlank()) {
            return null; // 账号为空由执行侧「收款账户缺失」控制点管，此处不写第二道判据
        }
        try {
            Long supplierId = resolveSupplierArchiveId(payeeType, payeeId, payeeName);
            if (supplierId == null) {
                return "收款方[" + payeeName + "] 不在供应商档案中，付款账号 " + bankAccount
                        + " 无档案开户信息可核对";
            }
            Set<String> archived = archivedPayeeAccounts(supplierId);
            if (archived.isEmpty()) {
                return "供应商[" + payeeName + "] 档案未登记开户账号，付款账号 " + bankAccount
                        + " 无档案开户信息可核对";
            }
            String target = bankAccount.trim();
            if (archived.contains(target)) {
                return null;
            }
            String first = archived.iterator().next();
            return "付款账号 " + target + " 与供应商[" + payeeName + "] 档案开户账号不一致（档案登记 "
                    + archived.size() + " 个，如 " + first + "）";
        } catch (Exception ex) {
            log.warn("收款账号档案核对异常（本次放行）：收款方[{}] {}", payeeName, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 档案默认开户账号——md_supplier_bank 的默认行优先，其次该表 / md_payee
     * 的任一可用行。档案无账号时返回 null，由调用方回落到单据快照。
     */
    private String archivedDefaultAccount(String payeeType, Long payeeId, String payeeName) {
        try {
            Long supplierId = resolveSupplierArchiveId(payeeType, payeeId, payeeName);
            if (supplierId == null) {
                return null;
            }
            List<MdSupplierBank> banks = mdSupplierBankMapper.selectList(
                    new LambdaQueryWrapper<MdSupplierBank>()
                            .eq(MdSupplierBank::getSupplierId, supplierId)
                            .orderByDesc(MdSupplierBank::getIsDefault)
                            .orderByAsc(MdSupplierBank::getId));
            for (MdSupplierBank b : banks) {
                if (accountUsable(b.getStatus()) && b.getBankAccount() != null && !b.getBankAccount().isBlank()) {
                    return b.getBankAccount().trim();
                }
            }
            Set<String> accounts = archivedPayeeAccounts(supplierId);
            return accounts.isEmpty() ? null : accounts.iterator().next();
        } catch (Exception ex) {
            log.warn("档案开户账号取数异常（回落单据快照）：收款方[{}] {}", payeeName, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 控制点④「超预算」：打款前按底层单据的预算行复核可用额度，<b>只记录不硬拦</b>。
     *
     * <p>规则说明（为什么不硬拦）：本单据自己的占用早已计在 {@code bg_item.occupy_amount} 里，
     * 付款转已用是"占用−/已用+"等额互换，不改可用额度；所以此处 {@code available < 0} 的真实含义是
             <b>这条预算线已经被整体突破</b>（含他单占用），现网实证 bg_item id=2 可用 = −13,000。
     * 把它做成付款强制校验＝把"制单时按 SOFT/WARN 方案合法放行"的单据在出纳环节成片锁死，
     * 分级（硬拦/软拦/仅记录）属校验策略，需产品确认。本批按 WARN 记录落，报告已列。</p>
     */
    private void warnIfOverBudgetOnExecute(String batchNo, List<OpPayBatchDetail> details) {
        try {
            Set<Long> billIds = new LinkedHashSet<>();
            for (OpPayBatchDetail d : details) {
                if (d.getBillPaymentId() == null) {
                    continue;
                }
                ExpBillPayment p = expBillPaymentMapper.selectById(d.getBillPaymentId());
                if (p != null && p.getBillId() != null) {
                    billIds.add(p.getBillId());
                }
            }
            for (Long billId : billIds) {
                ExpBill bill = expBillMapper.selectById(billId);
                if (bill == null) {
                    continue;
                }
                for (ExpBillLine line : expBillLineMapper.selectList(
                        new LambdaQueryWrapper<ExpBillLine>()
                                .eq(ExpBillLine::getBillId, billId))) {
                    Long itemId = budgetService.resolveOccupyItem(line.getBudgetItemId(), line.getItemId(),
                            bill.getCompanyId(), bill.getDeptId(), bill.getPeriodYear(), false);
                    if (itemId == null) {
                        continue; // 无预算科目的行不属本控制点（提交侧已按 control_type 判定过）
                    }
                    BigDecimal avail = budgetService.availableOf(itemId);
                    if (avail != null && avail.signum() < 0) {
                        log.warn("付款控制留痕（超预算，未硬拦）：批次 {} 底层单据 {} 的预算明细 {} "
                                + "可用额度已为 {}（预算线整体被打穿），本次仍按现行产品确认口径放行付款",
                                batchNo, bill.getBillNo(), itemId, avail);
                    }
                }
            }
        } catch (Exception ex) {
            // 控制点是观察位，异常绝不影响付款主链路
            log.error("付款批次 {} 超预算复核异常（不放行判定、仅留痕）: {}", batchNo, ex.getMessage(), ex);
        }
    }

    // 消耗确认（付款成功时点）

    /**
     * 打款成功 → 预算「占用转已用」按底层单据确认。
     *
     * <p>时点必须是钱出去之后：审批通过钩子上的转换（{@code ExpenseWfEventListener} 报销/付款收尾、
     * {@code PayBatchBizAction} 批次审批收尾）在钱还没出去时就计消耗，必然失真——转换点只留在这里。
     * 本方法在银行指令确认成功后调用；{@code settleOccupyOnPaid} 为 {@code REQUIRES_NEW} 独立事务
     * 且自带去重判重（已核销过的单据整单跳过），异常只回滚本次确认，绝不回滚已发出的付款。</p>
     */
    private void settleBudgetOnPaid(OpPayBatch batch, List<OpPayBatchDetail> details) {
        Set<Long> billIds = new LinkedHashSet<>();
        for (OpPayBatchDetail d : details) {
            if (d.getBillPaymentId() == null) {
                continue;
            }
            ExpBillPayment p = expBillPaymentMapper.selectById(d.getBillPaymentId());
            if (p != null && p.getBillId() != null) {
                billIds.add(p.getBillId());
            }
        }
        for (Long billId : billIds) {
            try {
                ExpBill bill = expBillMapper.selectById(billId);
                if (bill == null) {
                    log.warn("付款批次 {} 明细指向不存在的单据 {}，跳过预算消耗确认", batch.getBatchNo(), billId);
                    continue;
                }
                List<BudgetService.SettleLine> lines = new ArrayList<>();
                for (ExpBillLine line : expBillLineMapper.selectList(
                        new LambdaQueryWrapper<ExpBillLine>()
                                .eq(ExpBillLine::getBillId, billId))) {
                    lines.add(new BudgetService.SettleLine(
                            line.getBudgetItemId(), line.getItemId(), line.getAmount()));
                }
                if (lines.isEmpty()) {
                    log.info("付款批次 {} 单据 {} 无明细行，无预算科目可确认消耗",
                            batch.getBatchNo(), bill.getBillNo());
                    continue;
                }
                int done = budgetService.settleOccupyOnPaid(billId, bill.getBillNo(), bill.getBillType(),
                        bill.getCompanyId(), bill.getDeptId(), bill.getPeriodYear(), lines);
                log.info("付款批次 {} 打款成功 → 单据 {} 预算占用转已用 {} 行",
                        batch.getBatchNo(), bill.getBillNo(), done);
            } catch (Exception ex) {
                log.error("付款批次 {} 单据 {} 预算占用转已用失败（钱已付出，可经 预算>占用核销 手工补）: {}",
                        batch.getBatchNo(), billId, ex.getMessage(), ex);
            }
        }
    }

    // 批次

    /**
     * 自动组批：审批通过产生的 WAITING 付款记录，按「收款方 + 出账账户」自动归组为 DRAFT 批次。
     * 出纳打开付款页即见待付批次（确认→执行即可），无需手动新建/选单；去重（已入批的记录跳过）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AutoGroupResult autoGroup() {
        List<ExpBillPayment> waiting = expBillPaymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getPayStatus, "WAITING")
                .notInSql(ExpBillPayment::getId,
                        "select bill_payment_id from op_pay_batch_detail where bill_payment_id is not null"));
        return groupWaiting(waiting);
    }

    /**
     * 代码半：结算/付款审批通过即组批的服务端入口——只处理本单名下未入批的 WAITING 付款记录。
     *
     * <p>组批不能挂在出纳打开付款页的读动作上（{@code Pay.vue} 的 {@code load()} 里调
     * {@code POST /batch/auto-group}）：这样批次生成时点与审批时点解耦，
     * 且只有持 {@code pay:batch:create} 的人打开页面才会建批，无该权限的出纳永远看不到批次——
     * 「审批通过后付款行未进任何批次、出纳无入口」的真因就在这里，不在查询
     * （组批查询本身覆盖 PAY/SETTLE，卡的是触发点＋权限）。
     * 建批因此挪到审批收尾后由服务端触发（{@code PayBatchAutoGroupListener} 监听单据最终状态事件，
     * AFTER_COMMIT 时点付款记录已写入数据库可见），付款页不再隐式建批。
     *
     * <p>权限补授（CASHIER 3259/3264 不给 pay:batch:create）是权限治理口径，不属本改动范围。
     */
    @Transactional(rollbackFor = Exception.class)
    public AutoGroupResult autoGroupForBill(Long billId) {
        if (billId == null) {
            return new AutoGroupResult(new ArrayList<>());
        }
        List<ExpBillPayment> waiting = expBillPaymentMapper.selectList(new LambdaQueryWrapper<ExpBillPayment>()
                .eq(ExpBillPayment::getBillId, billId)
                .eq(ExpBillPayment::getPayStatus, "WAITING")
                .notInSql(ExpBillPayment::getId,
                        "select bill_payment_id from op_pay_batch_detail where bill_payment_id is not null"));
        return groupWaiting(waiting);
    }

    /**
     * 组批主体（{@link #autoGroup()} 与 {@link #autoGroupForBill(Long)} 共用，两条入口规则必须一致）：
     * 甄别可组批的付款记录 → 按「收款方 + 出账账户」归组 → 建 DRAFT 批次并挂明细。
     */
    private AutoGroupResult groupWaiting(List<ExpBillPayment> waiting) {
        AutoGroupResult result = new AutoGroupResult(new ArrayList<>());
        // 剔除源单据已驳回/撤回等无效状态的孤儿付款记录（智能审单驳回后残留），
        // 防止被组批进真实批次后触发三单匹配拦截/误付款
        List<ExpBillPayment> validWaiting = new ArrayList<>();
        for (ExpBillPayment p : waiting) {
            if (p.getBillId() == null) continue;
            ExpBill src = expBillMapper.selectById(p.getBillId());
            if (src == null) continue;
            String st = src.getStatus();
            if (!"APPROVED".equals(st) && !"PARTIAL_REPAID".equals(st) && !"PAID".equals(st)) {
                log.warn("自动组批跳过无效付款记录 id={}（源单据 {} 状态={}）", p.getId(), src.getBillNo(), st);
                continue;
            }
            // 重提轮会按审批重新生成 WAITING 付款记录；若该单据的上一笔付款已经
            // 执行成功（存在 PAID 批次指向本单，或同单已有 PAID 付款记录），则本次记录不再组批——
            // 否则同一笔业务出现第二个批次（空壳 PAID 之后又被组出一批），
            // 构成双付风险。PAID 付款记录/PAID 批次的甄别以批次溯源列与付款记录双保险为准。
            boolean paidBatchExists = opPayBatchMapper.selectCount(new LambdaQueryWrapper<OpPayBatch>()
                    .eq(OpPayBatch::getSourceDocId, p.getBillId())
                    .eq(OpPayBatch::getStatus, "PAID")) > 0;
            boolean paidPaymentExists = !paidBatchExists && expBillPaymentMapper.selectCount(
                    new LambdaQueryWrapper<ExpBillPayment>()
                            .eq(ExpBillPayment::getBillId, p.getBillId())
                            .eq(ExpBillPayment::getPayStatus, "PAID")
                            .ne(p.getId() != null, ExpBillPayment::getId, p.getId())) > 0;
            if (paidBatchExists || paidPaymentExists) {
                log.warn("自动组批跳过付款记录 id={}（单据 {} 已有支付成功批次/记录，防双付）",
                        p.getId(), src.getBillNo());
                continue;
            }
            validWaiting.add(p);
        }
        waiting = validWaiting;
        if (waiting.isEmpty()) return result;
        Map<String, List<ExpBillPayment>> groups = waiting.stream().collect(Collectors.groupingBy(
                p -> p.getPayeeName() + "|" + (p.getPayAccountId() == null ? "" : p.getPayAccountId())));
        int count = 0;
        for (Map.Entry<String, List<ExpBillPayment>> e : groups.entrySet()) {
            ExpBillPayment first = e.getValue().get(0);
            OpPayBatch b = new OpPayBatch();
            // 公司（从付款记录关联单据取）——先算出来，出账账户要按公司取默认账户，来源分类也按它判定
            ExpBill b1 = first.getBillId() == null ? null : expBillMapper.selectById(first.getBillId());
            Long companyId = b1 == null || b1.getCompanyId() == null ? 1L : b1.getCompanyId();
            // 溯源：单源付款（组内全部来自同一报销单）回填 source_doc_id，便于"付款批次→源单据"联查；
            // 多源（不同报销单合并付款）保持 AUTO_GROUP，不强行填单 id（批次级汇总无法单值溯源）
            boolean singleSource = e.getValue().stream().map(ExpBillPayment::getBillId)
                    .allMatch(id -> id != null && id.equals(first.getBillId()));
            if (singleSource && first.getBillId() != null) {
                b.setSourceDocId(first.getBillId());
                // 来源分类按业务来源赋枚举。写死 EXP_CLAIM 会把对公结算
                // （bill_type=PAY／bill_sub_type=SETTLE）也标成费用报销，
                // 按来源统计与银企报文组装全部串类。
                b.setSourceDocType(sourceDocTypeOf(b1));
            }
            // pay_account_id 为空时不能写死常量。银行账户主数据 id 从 108 起、没有 1 号，
            // 写死 1 会给自动组出的批次挂上悬空外键，确认环节的外键校验必拒。
            // 改为只认真值：付款记录指定的账户 → 本公司默认账户（is_default=1）；
            // 两者都没有就不建批（宁缺不造），由出纳在付款页显式选账户新建。
            Long bankAccountId = first.getPayAccountId() != null
                    ? first.getPayAccountId() : defaultBankAccountId(companyId);
            if (bankAccountId == null) {
                // 缺默认出账账户这件事必须让出纳看到：只写 log 的话，界面上"批次没变多"却没有任何说明，
                // 只能回源码或翻日志才知道原因。这里把同一句话升成返回体文案，由付款页提示出来。
                // 建批与否的判定不变（宁缺不造），仍不后退成"任一账户"兜底。
                String reason = "待付记录（收款方 " + first.getPayeeName() + "）未指定出账账户，且本公司未配置默认出账账户，"
                        + "本次未建批；请到 主数据 > 公司银行账户 将一条状态正常的账户设为默认，"
                        + "或用本页「选待付记录成批」显式选择出账账户。";
                log.warn("自动组批跳过一组：{}（公司 {}）", reason, companyId);
                if (!result.getSkippedReasons().contains(reason)) {
                    result.getSkippedReasons().add(reason);
                }
                continue;
            }
            b.setBankAccountId(bankAccountId);
            b.setCompanyId(companyId);
            // 单源批次随源单据写流程实例；多源合并批次不猜（批次级汇总无单一审批链）
            if (singleSource) {
                b.setProcInstId(first.getProcInstId() != null ? first.getProcInstId()
                        : (b1 == null ? null : b1.getProcInstId()));
            }
            b.setPayMethod("BANK");
            Long batchId = insertBatchHeader(b);
            for (ExpBillPayment p : e.getValue()) {
                addDetail(batchId, p.getId());
            }
            count++;
        }
        log.info("自动组批完成：{} 组批次（{} 笔待付）", count, waiting.size());
        result.setBatchCount(count);
        return result;
    }

    /**
     * 本公司默认出账账户（{@code md_bank_account.is_default=1} 且状态 NORMAL）。
     *
     * <p>只认显式的默认账户，<b>不</b>退化成"该公司任一账户"——出账账户决定钱从哪个银行账户付出，
     * 属业务事实而非可推断项；未配置默认账户时返回 null，由调用方跳过建批。
     */
    private Long defaultBankAccountId(Long companyId) {
        if (companyId == null) {
            return null;
        }
        MdBankAccount acc = mdBankAccountMapper.selectOne(new LambdaQueryWrapper<MdBankAccount>()
                .eq(MdBankAccount::getCompanyId, companyId)
                .eq(MdBankAccount::getIsDefault, 1)
                .eq(MdBankAccount::getStatus, "NORMAL")
                .and(w -> w.eq(MdBankAccount::getDelFlag, 0).or().isNull(MdBankAccount::getDelFlag))
                .orderByAsc(MdBankAccount::getId)
                .last("limit 1"));
        return acc == null ? null : acc.getId();
    }

    /**
     * 批次来源分类枚举 —— 取源单据 {@code bill_type} 对应的业务单据类型编码，
     * 与 {@code ExpenseBillService} 的 bill_type→单据类型（编号规则/预算占用同一规则）保持一致：
     * APPLY→EXP_APPLY、LOAN→EXP_LOAN、REPAY→EXP_REPAY、PAY→EXP_PAY（对公结算 SETTLE 亦属此类）、
     * 其余（CLAIM/CLAIM_TRAVEL/TRAVEL…）→EXP_CLAIM。
     *
     * <p>源单据查不到时退回 {@code EXP_BILL_PAYMENT}（付款记录本身），不冒充费用报销。
     * 取值均属 exp_bill 域，故同步加入 {@link #BILL_DOMAIN_SOURCE_TYPES}，
     * 否则批次列表的「单据状态/凭证」补充列会因来源不认而重新变空。
     */
    private static String sourceDocTypeOf(ExpBill bill) {
        if (bill == null || bill.getBillType() == null || bill.getBillType().isBlank()) {
            return "EXP_BILL_PAYMENT";
        }
        return switch (bill.getBillType().trim().toUpperCase()) {
            case "APPLY" -> "EXP_APPLY";
            case "LOAN" -> "EXP_LOAN";
            case "REPAY" -> "EXP_REPAY";
            case "PAY" -> "EXP_PAY";
            default -> "EXP_CLAIM";
        };
    }

    /**
     * 待付记录清单——WAITING 且尚未入任何批次，按源单据带出单号/公司。
     * 查询谓词与 {@link #autoGroup()} 完全一致，保证"页面看到的待付"与"自动组批吃的待付"是同一批。
     */
    public PageResult<Map<String, Object>> waitingPayments(long pageNum, long pageSize, String keyword) {
        Page<ExpBillPayment> p =
                expBillPaymentMapper.selectPage(
                        Page.of(pageNum, pageSize),
                        new LambdaQueryWrapper<ExpBillPayment>()
                                .eq(ExpBillPayment::getPayStatus, "WAITING")
                                .notInSql(ExpBillPayment::getId,
                                        "select bill_payment_id from op_pay_batch_detail where bill_payment_id is not null")
                                .orderByAsc(ExpBillPayment::getId));
        List<ExpBillPayment> records = p.getRecords();
        Map<Long, ExpBill> billById = new HashMap<>();
        List<Long> billIds = records.stream().map(ExpBillPayment::getBillId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (!billIds.isEmpty()) {
            for (ExpBill b : expBillMapper.selectBatchIds(billIds)) {
                billById.put(b.getId(), b);
            }
        }
        String kw = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ExpBillPayment pay : records) {
            ExpBill b = pay.getBillId() == null ? null : billById.get(pay.getBillId());
            if (!kw.isEmpty()) {
                String hay = (b == null ? "" : b.getBillNo() + " " + (b.getTitle() == null ? "" : b.getTitle()))
                        + " " + (pay.getPayeeName() == null ? "" : pay.getPayeeName());
                if (!hay.contains(kw)) continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", pay.getId());
            row.put("billId", pay.getBillId());
            row.put("billNo", b == null ? null : b.getBillNo());
            row.put("title", b == null ? null : b.getTitle());
            row.put("companyId", b == null ? null : b.getCompanyId());
            row.put("payeeName", pay.getPayeeName());
            row.put("bankName", pay.getBankName());
            row.put("bankAccount", pay.getBankAccount());
            row.put("amount", pay.getAmount());
            row.put("currencyCode", pay.getCurrencyCode());
            row.put("payAccountId", pay.getPayAccountId());
            row.put("createTime", pay.getCreateTime());
            list.add(row);
        }
        // keyword 在内存侧过滤时，total 仍按整页命中数给（该框只是辅助筛选，分页规则以库侧为准）
        return PageResult.of(list, p.getTotal(), pageNum, pageSize);
    }

    /**
     * 勾选待付记录一步成批。
     *
     * <p>不走「先建空批次、再逐笔挂明细」的手工路径：那条路径的挂明细入口
     * （{@code payApi.addDetail}）在全仓 {@code .vue} 里零调用点，手工批次建出来永远是 0 明细，
     * 确认时又被空批校验拒掉，等于没有出口。
     * 现在一次请求即完成"建批＋挂明细"，且成批前把成员资格校验清楚：同公司、同币别、同出账账户，
     * 即「付款申请单按同一结算账户/币别归批」的归批规则。
     *
     * @param billPaymentIds 勾选的待付付款记录 ID
     * @param bankAccountId 出账账户（可空：空则取本公司默认账户）
     * @param companyId 付款公司（可空：空则按勾选记录源单据所属公司推出）
     * @return 新建批次 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createBatchFromPayments(List<Long> billPaymentIds, Long bankAccountId, Long companyId) {
        if (billPaymentIds == null || billPaymentIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请至少勾选一条待付记录再成批");
        }
        List<Long> ids = billPaymentIds.stream().filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        List<ExpBillPayment> pays = expBillPaymentMapper.selectBatchIds(ids);
        if (pays.size() != ids.size()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "部分付款记录已不存在（共选 " + ids.size() + " 条、有效 " + pays.size() + " 条），请刷新待付清单后重试");
        }
        for (ExpBillPayment pay : pays) {
            if (!"WAITING".equals(pay.getPayStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "付款记录 " + pay.getId()
                        + " 当前状态为 " + pay.getPayStatus() + "，仅待付（WAITING）记录可成批");
            }
        }
        List<OpPayBatchDetail> already = opPayBatchDetailMapper.selectList(
                new LambdaQueryWrapper<OpPayBatchDetail>().in(OpPayBatchDetail::getBillPaymentId, ids));
        if (!already.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "所选记录中有 " + already.size()
                    + " 条已在其他批次内，请刷新待付清单后重新勾选（待付清单只列未入批记录）");
        }
        Map<Long, ExpBill> billById = new HashMap<>();
        List<Long> billIds = pays.stream().map(ExpBillPayment::getBillId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (!billIds.isEmpty()) {
            for (ExpBill b : expBillMapper.selectBatchIds(billIds)) {
                billById.put(b.getId(), b);
            }
        }
        Set<Long> companyIds = pays.stream()
                .map(x -> x.getBillId() == null ? null : (billById.get(x.getBillId()) == null
                        ? null : billById.get(x.getBillId()).getCompanyId()))
                .filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
        Long targetCompany = companyId != null ? companyId : (companyIds.isEmpty() ? null : companyIds.iterator().next());
        if (companyIds.size() > 1 && companyId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "同一批次只能有一个付款公司，当前所选记录跨 " + companyIds.size() + " 个公司，请分批勾选或指定付款公司");
        }
        Set<String> currencies = pays.stream()
                .map(x -> x.getCurrencyCode() == null || x.getCurrencyCode().isBlank()
                        ? "CNY" : x.getCurrencyCode().trim().toUpperCase())
                .collect(Collectors.toCollection(TreeSet::new));
        if (currencies.size() > 1) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "同一批次只能有一个币别，当前所选记录含 " + String.join("、", currencies) + "，请分批勾选");
        }
        Set<Long> recordAccounts = pays.stream().map(ExpBillPayment::getPayAccountId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
        if (recordAccounts.size() > 1 && bankAccountId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "所选待付记录指定了不同的出账账户，请在成批前指定一个出账账户");
        }
        Long payAccount = resolvePayAccount(targetCompany, bankAccountId, recordAccounts);

        OpPayBatch batch = new OpPayBatch();
        batch.setCompanyId(targetCompany);
        batch.setBankAccountId(payAccount);
        batch.setPayMethod("BANK");
        batch.setCurrencyCode(currencies.iterator().next());
        boolean singleSource = pays.stream().map(ExpBillPayment::getBillId)
                .allMatch(bid -> bid != null && bid.equals(pays.get(0).getBillId()));
        if (singleSource && pays.get(0).getBillId() != null) {
            batch.setSourceDocId(pays.get(0).getBillId());
            // 与 autoGroup 同一规则按业务来源赋枚举，不再写死 EXP_CLAIM
            batch.setSourceDocType(sourceDocTypeOf(billById.get(pays.get(0).getBillId())));
            batch.setProcInstId(pays.get(0).getProcInstId());
        }
        Long batchId = insertBatchHeader(batch);
        for (ExpBillPayment pay : pays) {
            addDetail(batchId, pay.getId());
        }
        log.info("选待付记录成批：批次 {} 共 {} 笔（公司 {}、出账账户 {}、币别 {}）",
                batch.getBatchNo(), pays.size(), targetCompany, payAccount, currencies);
        return batchId;
    }

    /**
     * 出账账户解析：显式传入 → 付款记录自带 → 本公司默认账户；三者皆无则拒（不建批）。
     *
     * <p>既不做"默认账户硬必填"，也不后退成"本公司任一账户"兜底——
     * 出账账户决定钱从哪个银行账户付出，属业务事实。付款账户本身在参数配置里是「必填=否」，
     * 条件必录走 CS 系列参数化配置，校验点在提交而非保存，
     * 所以这里保持"可选＋缺失即拒"，并把缺失原因写成前端可读文案。
     */
    private Long resolvePayAccount(Long companyId, Long requested, Set<Long> recordAccounts) {
        Long candidate = requested != null ? requested
                : (recordAccounts.size() == 1 ? recordAccounts.iterator().next() : null);
        if (candidate != null) {
            MdBankAccount acc = mdBankAccountMapper.selectById(candidate);
            boolean dead = acc == null
                    || (acc.getDelFlag() != null && acc.getDelFlag() == 1)
                    || !"NORMAL".equalsIgnoreCase(String.valueOf(acc.getStatus()));
            if (dead) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "出账账户不存在或已停用/注销，请重新选择本公司状态正常的银行账户后再成批");
            }
            if (companyId != null && !companyId.equals(acc.getCompanyId())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "出账账户与付款公司不匹配：该账户不属于本批次付款公司，请换本公司名下的银行账户");
            }
            return acc.getId();
        }
        Long defaultId = defaultBankAccountId(companyId);
        if (defaultId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, MISS_DEFAULT_ACCOUNT_HINT);
        }
        return defaultId;
    }

    private static final String MISS_DEFAULT_ACCOUNT_HINT =
            "本公司未配置默认出账账户，未生成批次：请到 主数据 > 公司银行账户 将一条状态正常的账户设为默认，"
                    + "或在成批时显式选择出账账户。";

    /** 自动组批返回体：批次数量＋跳过的原因（原因面向出纳，不再只留在日志） */
    public static class AutoGroupResult {
        private int batchCount;
        private final List<String> skippedReasons;

        public AutoGroupResult(List<String> skippedReasons) {
            this.skippedReasons = skippedReasons;
        }

        public int getBatchCount() {
            return batchCount;
        }

        public void setBatchCount(int batchCount) {
            this.batchCount = batchCount;
        }

        public List<String> getSkippedReasons() {
            return skippedReasons;
        }
    }

    /**
     * 新建付款批次（对外入口）。
     * 0 笔空批次在生成侧即拒绝——本入口入参不携带任何明细，经它建出的必是空批次
     * （空批次无明细可回溯、凭证环节恒空，会派生出"假账证不符"）。
     * 手工成批请走「选待付记录成批」或「立即组批」，内部在成批事务里先建头、同批挂明细（insertBatchHeader），不走本口。
     */
    public Long createBatch(OpPayBatch batch) {
        throw new BizException(ErrorCode.PARAM_INVALID,
                "不建 0 笔空批次：付款批次必须由待付记录成批生成——请在待付清单勾选记录后用「选待付记录成批」一步成批");
    }

    /**
     * 批次头写入数据库通道（仅限成批事务内部调用：groupWaiting / createBatchFromPayments 先建头、
     * 同事务逐笔挂明细后批次必有明细）。
     */
    private Long insertBatchHeader(OpPayBatch batch) {
        batch.setId(null);
        batch.setTenantId(TenantContext.getTenantId());
        // TR-O2：companyId 缺省按当前登录人公司回填；出账账户 NOT NULL 必填给明确业务提示（不再写入数据库报 ERROR 1364）
        if (batch.getCompanyId() == null) {
            LoginUser u = UserContext.get();
            if (u != null) {
                batch.setCompanyId(u.getCompanyId());
            }
        }
        if (batch.getCompanyId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款公司不能为空：当前登录人未配置公司归属，请显式传入 companyId");
        }
        if (batch.getBankAccountId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "出账账户(bankAccountId)不能为空，请先选择公司银行账户");
        }
        batch.setBatchNo(Optional.ofNullable(batch.getBatchNo()).orElseGet(() -> {
            try { return numberRuleService.next("PAY_BATCH"); }
            catch (Exception e) { return "FK" + System.currentTimeMillis(); }
        }));
        batch.setStatus("DRAFT");
        batch.setPayMethod(batch.getPayMethod() == null ? "BANK" : batch.getPayMethod());
        // C1：选单成批会按币别校验后传入批次币别，此处不再无条件覆盖成 CNY
        batch.setCurrencyCode(batch.getCurrencyCode() == null || batch.getCurrencyCode().isBlank()
                ? "CNY" : batch.getCurrencyCode());
        batch.setTotalAmount(BigDecimal.ZERO);
        batch.setDetailCount(0);
        // 仅当调用方未显式指定来源时才默认 AUTO_GROUP
        // （单源付款批在组批时按业务来源指定 EXP_CLAIM／EXP_PAY／EXP_LOAN… ＋ sourceDocId，见）
        if (batch.getSourceDocType() == null) {
            batch.setSourceDocType("AUTO_GROUP");
        }
        opPayBatchMapper.insert(batch);
        return batch.getId();
    }

    /** 加明细：从付款记录带入收款信息 */
    @Transactional(rollbackFor = Exception.class)
    public Long addDetail(Long batchId, Long billPaymentId) {
        OpPayBatch batch = requireBatch(batchId);
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿批次可加明细");
        }
        ExpBillPayment p = expBillPaymentMapper.selectById(billPaymentId);
        if (p == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款记录不存在: " + billPaymentId);
        }
        if ("PAID".equals(p.getPayStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款记录已支付");
        }
        // 组批入口由「仅付款页加载」变为「审批收尾服务端触发 ＋ 付款页/手工成批」多条，
        // 同一付款记录被两个入口挂进不同批次即双付。入批前按 bill_payment_id 复查（与查询侧 notInSql 同一规则）。
        // 注：并发同时读的窄口要靠 op_pay_batch_detail.bill_payment_id 唯一索引才彻底封死，属 DDL 变更，需另行确认。
        Long alreadyInBatch = opPayBatchDetailMapper.selectCount(new LambdaQueryWrapper<OpPayBatchDetail>()
                .eq(OpPayBatchDetail::getBillPaymentId, billPaymentId)
                .eq(OpPayBatchDetail::getDelFlag, 0));
        if (alreadyInBatch != null && alreadyInBatch > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "付款记录 " + billPaymentId + " 已在其他批次内，禁止重复入批（防双付）");
        }
        // 组批前校验收款信息：收款方与账号缺失禁止入批，防止“已付款但不知付给谁”的孤儿付款
        if (p.getPayeeName() == null || p.getPayeeName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款记录缺少收款方名称，请先在单据中补全收款信息");
        }
        // 账号缺失时第一顺位取供应商档案开户信息（md_supplier_bank 默认账户 → md_payee），
        // 单据上的自由文本快照退为第二顺位。已有快照不做静默覆盖——改快照＝改已核对过的付款要素，属主数据变更面。
        if ((p.getBankAccount() == null || p.getBankAccount().isBlank())) {
            String archived = archivedDefaultAccount(p.getPayeeType(), p.getPayeeId(), p.getPayeeName());
            if (archived != null) {
                p.setBankAccount(archived);
                expBillPaymentMapper.updateById(p);
                log.info("付款记录 id={} 按供应商档案开户信息回填收款账号 {}", p.getId(), archived);
            }
        }
        // 容错回填：单据在审批通过后补全/变更过收款账号时，付款记录（生成时的快照）不会自动同步；
        // 组批前以源单据为准回填，避免“单据已补账号、付款记录仍为空”被误拦截（防付错的同时不阻断正常组批）
        if ((p.getBankAccount() == null || p.getBankAccount().isBlank())
                && p.getBillId() != null) {
            ExpBill src = expBillMapper.selectById(p.getBillId());
            if (src != null && src.getPayeeAccountNo() != null && !src.getPayeeAccountNo().isBlank()) {
                p.setBankAccount(src.getPayeeAccountNo());
                expBillPaymentMapper.updateById(p);
                log.info("付款记录 id={} 按源单据 {} 回填收款账号", p.getId(), src.getBillNo());
            }
        }
        if (p.getBankAccount() == null || p.getBankAccount().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "付款记录 [" + p.getPayeeName() + "] 缺少收款账号，请先在单据中补全收款账号（防付错）");
        }
        // 组批侧留痕：账号无档案支撑时如实记一笔，本处不硬拦（执行期才决定进不进银企报文）
        String archiveIssueOnAdd = payeeAccountArchiveIssue(p.getPayeeType(), p.getPayeeId(),
                p.getPayeeName(), p.getBankAccount());
        if (archiveIssueOnAdd != null) {
            log.warn("组批留痕：批次 {} 收款方[{}] {}（执行付款时该明细不进银企报文，补齐档案后原地重试）",
                    batch.getBatchNo(), p.getPayeeName(), archiveIssueOnAdd);
        }
        OpPayBatchDetail d = new OpPayBatchDetail();
        d.setTenantId(TenantContext.getTenantId());
        d.setBatchId(batchId);
        d.setBillPaymentId(billPaymentId);
        d.setPayeeName(p.getPayeeName());
        // 勾稽键从名称文本换档案 id。id 只认付款记录上已有的 payee_id（收款方类型为供应商时），
        // 拿不到就留 NULL——不在此按 payee_name 反查 md_supplier 补 id，否则勾稽键又回到名称文本。
        // 存在性校验必需：payee_id 的第二条来源环节是 cont_contract.customer_id（crm_customer 空间），
        // 而 pay_plan.supplier_id 是 md_supplier 空间，两张表各自独立分配 id、数值可能撞号，不校验会假阳勾稽。
        if ("SUPPLIER".equalsIgnoreCase(p.getPayeeType()) && p.getPayeeId() != null
                && mdSupplierMapper.selectById(p.getPayeeId()) != null) {
            d.setSupplierId(p.getPayeeId());
        }
        d.setBankName(p.getBankName());
        d.setBankAccount(p.getBankAccount());
        d.setAmount(p.getAmount());
        d.setCurrencyCode(p.getCurrencyCode());
        d.setStatus("WAITING");
        d.setCreateTime(LocalDateTime.now());
        opPayBatchDetailMapper.insert(d);

        // 同步批次汇总
        OpPayBatch update = new OpPayBatch();
        update.setId(batchId);
        update.setTotalAmount(batch.getTotalAmount().add(p.getAmount()));
        update.setDetailCount((batch.getDetailCount() == null ? 0 : batch.getDetailCount()) + 1);
        opPayBatchMapper.updateById(update);
        return d.getId();
    }

    /**
     * 批次明细若同属一次来源审批则返回该审批实例ID。
     * 多源合并或明细无来源实例时返回 null——批次级汇总不猜归属。
     */
    private String soleSourceProcInst(Long batchId) {
        List<Long> payIds = opPayBatchDetailMapper.selectList(new LambdaQueryWrapper<OpPayBatchDetail>()
                        .eq(OpPayBatchDetail::getBatchId, batchId))
                .stream().map(OpPayBatchDetail::getBillPaymentId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (payIds.isEmpty()) {
            return null;
        }
        Set<String> instances = expBillPaymentMapper.selectBatchIds(payIds).stream()
                .map(ExpBillPayment::getProcInstId)
                .filter(pi -> pi != null && !pi.isBlank())
                .collect(Collectors.toSet());
        return instances.size() == 1 ? instances.iterator().next() : null;
    }

    /**
     * 作废：DRAFT → VOID。
     * 沿用「直改状态型单据收尾须收尾下游」的规则——
     * 批次作废必须<b>同批</b>把其名下凭证置 VOID，不得留下「VOID 批次挂 POSTED 活凭证」的账证不符组合。
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidBatch(Long batchId) {
        OpPayBatch batch = requireBatch(batchId);
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿批次可作废");
        }
        OpPayBatch update = new OpPayBatch();
        update.setId(batchId);
        update.setStatus("VOID");
        opPayBatchMapper.updateById(update);
        int vouchersVoided = voidBatchOwnVouchers(batchId);
        log.info("付款批次 {} 作废 DRAFT→VOID，同批作废名下凭证 {} 张", batch.getBatchNo(), vouchersVoided);
    }

    /**
     * 把批次「自己名下」的凭证同批置 VOID（DRAFT/POSTED 活凭证皆办结）。
     * <p>范围只取批次级凭证（{@code source_type ∈ TREASURY_PAY/CIP_PAY_BATCH 且 source_doc_id = 批次主键}）：
     * 单据级费用族凭证属源单据、不属批次，不动它——避免误伤仍在存活的源单据账务（同理，
     * source_doc_id 是跨域裸主键，必须与 source_type 成对精确匹配，不与 bill_id 混用）。
     * 但批次级凭证即使已 POSTED 也必须办结，否则「VOID＋POSTED」组合就是账证不符。</p>
     *
     * @return 本次办结的凭证张数（供日志记录）
     */
    private int voidBatchOwnVouchers(Long batchId) {
        List<OpVoucher> own = opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getSourceDocId, batchId)
                .in(OpVoucher::getSourceType, BATCH_LEVEL_VOUCHER_SOURCES)
                .ne(OpVoucher::getStatus, "VOID"));
        int voided = 0;
        for (OpVoucher vou : own) {
            OpVoucher vu = new OpVoucher();
            vu.setId(vou.getId());
            vu.setStatus("VOID");
            opVoucherMapper.updateById(vu);
            voided++;
        }
        return voided;
    }

    /**
     * 批次名下的「活」凭证清单（与列表「凭证/记账」列查询同源）。
     * 批次级：{@code source_doc_id = 批次主键 且 source_type ∈ BATCH_LEVEL_VOUCHER_SOURCES}；
     * 单据级：{@code bill_id ∈ 明细回溯的源单据id 且 source_type ∈ VoucherService.EXPENSE_BILL_SOURCES}。
     * 已 VOID 的凭证不再算活凭证（与列表过滤规则一致）。
     */
    private List<OpVoucher> activeVouchersOfBatch(OpPayBatch batch) {
        List<OpVoucher> active = new ArrayList<>();
        if (batch == null || batch.getId() == null) {
            return active;
        }
        active.addAll(opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                .eq(OpVoucher::getSourceDocId, batch.getId())
                .in(OpVoucher::getSourceType, BATCH_LEVEL_VOUCHER_SOURCES)
                .ne(OpVoucher::getStatus, "VOID")));
        Set<Long> billIds = billIdsOfBatch(batch);
        if (!billIds.isEmpty()) {
            active.addAll(opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                    .in(OpVoucher::getBillId, billIds)
                    .in(OpVoucher::getSourceType, VoucherService.EXPENSE_BILL_SOURCES)
                    .ne(OpVoucher::getStatus, "VOID")));
        }
        return active;
    }

    /** ②：批次名下是否存在任一活凭证（两环节皆无 ⇒ false）。 */
    private boolean hasAnyVoucherForBatch(OpPayBatch batch) {
        return !activeVouchersOfBatch(batch).isEmpty();
    }

    /**
     * 批次明细 → 付款记录 → 源单据 id 的反向回溯（规则同 fillDisplayFields：明细全覆盖；
     * 明细为空时只有费用族来源才把 source_doc_id 当 exp_bill 主键解释——source_doc_id 是跨域裸主键，不猜归属）。
     */
    private Set<Long> billIdsOfBatch(OpPayBatch batch) {
        Set<Long> billIds = new HashSet<>();
        if (batch == null || batch.getId() == null) {
            return billIds;
        }
        List<OpPayBatchDetail> details = opPayBatchDetailMapper.selectList(
                new LambdaQueryWrapper<OpPayBatchDetail>().eq(OpPayBatchDetail::getBatchId, batch.getId()));
        Set<Long> paymentIds = details.stream().map(OpPayBatchDetail::getBillPaymentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!paymentIds.isEmpty()) {
            for (ExpBillPayment pay : expBillPaymentMapper.selectBatchIds(paymentIds)) {
                if (pay.getBillId() != null) {
                    billIds.add(pay.getBillId());
                }
            }
        }
        if (billIds.isEmpty() && batch.getSourceDocId() != null && batch.getSourceDocType() != null
                && BILL_DOMAIN_SOURCE_TYPES.contains(batch.getSourceDocType().trim().toUpperCase())) {
            billIds.add(batch.getSourceDocId());
        }
        return billIds;
    }

    /**
     * 账证不符两组合的判据（列表红标与写侧禁付共用同一规则，防两头漂移）：
     * <ul>
     *   <li>{@code VOID_WITH_POSTED_VOUCHER}：批次已作废（VOID）但名下仍有已记账（POSTED）活凭证——
     *       作废收尾没把凭证带下去，账上还站着这笔款；</li>
     *   <li>{@code PAID_WITHOUT_VOUCHER}：批次已付款（PAID）但名下查无任何活凭证（单据级/批次级皆无）——
     *       钱已出去而账上无凭无据。</li>
     * </ul>
     * 无异常返回 {@code null}。
     */
    private String voucherAnomalyOf(OpPayBatch batch) {
        if (batch == null) {
            return null;
        }
        List<OpVoucher> active = activeVouchersOfBatch(batch);
        if ("VOID".equals(batch.getStatus())
                && active.stream().anyMatch(v -> "POSTED".equals(v.getStatus()))) {
            return "VOID_WITH_POSTED_VOUCHER";
        }
        if ("PAID".equals(batch.getStatus()) && active.isEmpty()) {
            return "PAID_WITHOUT_VOUCHER";
        }
        return null;
    }

    /**
     * 驳回批次编辑重提 REJECTED → DRAFT。
     * 驳回侧只把状态落 REJECTED，没有复活通道的话批次就停在那里；
     * 回到草稿后出纳可增删明细，再经 confirm 重新发起 TR_PAY 审批流。
     */
    @Transactional(rollbackFor = Exception.class)
    public void returnToEdit(Long batchId) {
        OpPayBatch batch = requireBatch(batchId);
        if (!"REJECTED".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已驳回批次可编辑重提，当前状态: " + batch.getStatus());
        }
        OpPayBatch update = new OpPayBatch();
        update.setId(batchId);
        update.setStatus("DRAFT");
        opPayBatchMapper.updateById(update);
        log.info("付款批次 {} 驳回后转编辑重提: REJECTED → DRAFT", batch.getBatchNo());
    }

    /** 确认：DRAFT → CONFIRMED（出纳确认后提交银行） */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long batchId) {
        OpPayBatch batch = requireBatchForUpdate(batchId); // 行锁：确认与执行互斥（本方法已入事务，FOR UPDATE 才有效）
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿批次可确认");
        }
        // 齐套校验·确认层：确认只看状态不够，缺附件/缺合同/账户悬空都要在这里拦下。
        // 账户外键恒硬拦、不吃灰度开关；其余两档按 sys_config 灰度规则生效。
        // 这一档不与执行侧的 EXECUTE 二次校验重复，两处都留。
        billCompletenessGuard.assertComplete("TR_PAY",
                BillCompletenessGuard.STAGE_CONFIRM, "TR_PAY", batchId);
        OpPayBatch update = new OpPayBatch();
        update.setId(batchId);
        update.setStatus("CONFIRMED");
        update.setSubmitUserId(UserContext.getRequired().getUserId());
        update.setSubmitTime(LocalDateTime.now());
        // 确认前是最后一次改明细的机会，此刻按明细回溯来源审批实例——
        // 全部明细同属一次审批才回填（多源合并批次不猜），与 autoGroup 组批规则一致，
        // 使手工新建的批次也能"批次→审批链"联查（op_pay_batch.proc_inst_id 存的是来源单据审批流，非本批次 TR_PAY 流）
        if (batch.getProcInstId() == null) {
            String sourcePi = soleSourceProcInst(batchId);
            if (sourcePi != null) {
                update.setProcInstId(sourcePi);
            }
        }
        opPayBatchMapper.updateById(update);
        // 批次显式关联在建工程（source_doc_type=CIP）时，确认即自动归集成本（去重）
        mdCipService.aggregateFromPayBatch(batch.getSourceDocType(), batch.getSourceDocId(),
                batch.getBatchNo(), batch.getTotalAmount());
        // 出纳确认付款批次即发起 TR_PAY 资金付款(网银)审批流（出纳经办→资金岗复核→财务经理/财务总监阈值审批）
        // 去重：同批次已有在办 TR_PAY 则不再重复发起（防双击/并发确认产生多实例）
        if (wfEngineFacade.hasActiveTask("TR_PAY", batchId)) {
            log.info("付款批次 {} 已存在在办 TR_PAY 审批实例，跳过重复发起", batchId);
            return;
        }
        try {
            // start() 的返回值就是本批次自己的资金付款审批实例号，不能丢：
            // 批次行上唯一可查的流号只有 op_pay_batch.proc_inst_id（＝源单据流程，语义不可挪用），
            // 单靠它「从批次点追溯」会落到另一条流程上。
            // 实例号写进批次列 pay_proc_inst_id 需 DDL（注释态脚本见 OpPayBatch 类注释），本次不改表；
            // 读侧按 (biz_type='TR_PAY', biz_id=batchId) 从 wf_instance 解析（fillDisplayFields），
            // 这里再留一条把实例号与批次号绑死的日志，保证运维当场可核对，不用反查。
            String payProcInstId = wfEngineFacade.start("TR_PAY", batchId, userId(),
                    "资金付款批次-" + batch.getBatchNo(), batch.getTotalAmount());
            log.info("付款批次 {} 已发起资金付款审批：TR_PAY 实例 {}（批次自身流；op_pay_batch.proc_inst_id 仍是源单据流，两者不同属设计）",
                    batch.getBatchNo(), payProcInstId);
        } catch (Exception ex) {
            log.error("付款批次 {} 发起付款审批失败: {}", batchId, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
    }

    /**
     * 执行付款被拦时，逐条透出「到底缺哪一单」的结构化拦口报告。
     *
     * <p>本条的真缺陷不是校验本身错，而是校验无出口：批次审批全绿后，{@link #execute} 在
     * 「付款指令发出前」的硬阻断位（收款账户缺失 / 黑名单 / 禁用 / 反欺诈 / <b>三单匹配 FAILED</b>）
     * 抛 {@link BizException} 回滚整批，前端只拿到一条拼好的 {@code msg} 字符串走全局 toast
     * api.js:127-129，操作人看完无法知道「是这一批的哪一条明细、缺的是哪一环节、该去哪个界面补」，
     * 页面上也没有解拦出口 ⇒ 批次原地锁死在 APPROVED。拦口文案长这样：
     * 「发票 81000.00 > 收货单 80000.00，超 1000.00（容差 400.00）；供应商对账未确认」／
     * 「未关联采购订单（PO）；未关联收货单；超付：关联发票 0＋容差 0 &lt; 本次付款 600000.00」——
     * 读到的只是结论，读不出该去补哪一条。
     *
     * <p><b>不写第二处判据</b>：本方法不新增任何校验规则，全部拦口都调用 {@code execute()} 同一条
     * 判断（{@link ThreeWayMatchService#check(Long)} 与同一批 mapper 读），只是把「遇第一条即抛」
     * 换成「逐条收集后原样返回」，并把 {@code issues[]} 的类型化差异码翻成人能读懂的缺环节清单。
     *
     * <p><b>只读</b>：不改批次状态、不写任何表、不发起流程；{@code assertSupplierNotBlacklisted}
     * 类断言以 try/catch 取其消息（该方法本身只有 selectById，无写点）。
     * 因此本端点被反复调用不会带来副作用，可安全挂在 {@code pay:batch:list} 读权限上。
     *
     * <p><b>放宽校验（允许财务主管解拦放行）不在本条处置面</b>——属校验策略／权限待定范围，需单独确认。
     *
     * @return 每个存在拦口的明细一行；全部可执行时返回空列表
     */
    public List<Map<String, Object>> executeBlockReport(Long batchId) {
        OpPayBatch batch = opPayBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款批次不存在: " + batchId);
        }
        List<OpPayBatchDetail> details = opPayBatchDetailMapper.selectList(
                new LambdaQueryWrapper<OpPayBatchDetail>().eq(OpPayBatchDetail::getBatchId, batchId));
        List<Map<String, Object>> report = new ArrayList<>();
        for (OpPayBatchDetail d : details) {
            List<String> blockers = new ArrayList<>();
            List<Map<String, Object>> reasons = new ArrayList<>();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("detailId", d.getId());
            entry.put("billPaymentId", d.getBillPaymentId());
            entry.put("payeeName", d.getPayeeName());
            entry.put("bankName", d.getBankName());
            entry.put("bankAccount", d.getBankAccount());
            entry.put("amount", d.getAmount());
            entry.put("status", d.getStatus());

            // 拦口①收款账户缺失（execute:961，控制点②）
            if (d.getBankAccount() == null || d.getBankAccount().isBlank()) {
                blockers.add("BANK_ACCOUNT_MISSING");
                reasons.add(blockReason("BANK_ACCOUNT_MISSING", "ERROR",
                        "收款银行账号为空，付款指令不知道该汇到哪里",
                        "到 收款方主数据 补录该收款方账号后重新组批，或在本批次里删除这条明细"));
            }

            if (d.getBillPaymentId() != null) {
                ExpBillPayment pay = expBillPaymentMapper.selectById(d.getBillPaymentId());
                if (pay != null && pay.getBillId() != null) {
                    entry.put("billId", pay.getBillId());
                    ExpBill bill = expBillMapper.selectById(pay.getBillId());
                    entry.put("billNo", bill == null ? null : bill.getBillNo());
                    entry.put("billStatus", bill == null ? null : bill.getStatus());

                    // 拦口②③供应商黑名单 / 禁用（execute:972/:976，二者本就只抛不改）
                    try {
                        assertSupplierNotBlacklisted(pay.getPayeeType(), pay.getPayeeId());
                    } catch (BizException bx) {
                        // 黑名单命中＝预期阻塞项：已落 blocker+可读理由，本明细不提交（其余明细继续校验），不上抛
                        blockers.add("SUPPLIER_BLACKLISTED");
                        reasons.add(blockReason("SUPPLIER_BLACKLISTED", "ERROR", bx.getMessage(),
                                "黑名单供应商不可付款；如属误列，请到 供应商主数据 撤销黑名单标记"));
                    }
                    try {
                        assertSupplierNotDisabled(pay.getPayeeType(), pay.getPayeeId(), pay.getPayeeName());
                    } catch (BizException bx) {
                        // 供应商停用＝预期阻塞项：已落 blocker+可读理由，本明细不提交，不上抛
                        blockers.add("SUPPLIER_DISABLED");
                        reasons.add(blockReason("SUPPLIER_DISABLED", "ERROR", bx.getMessage(),
                                "请到 供应商主数据 确认该档案状态；确需付款请先恢复为可用状态"));
                    }

                    // 拦口③b收款账号无档案支撑（execute 的 账号完整性校验，同一条判据不重写第二处）
                    String acctIssue = payeeAccountArchiveIssue(pay.getPayeeType(), pay.getPayeeId(),
                            d.getPayeeName(), d.getBankAccount());
                    if (acctIssue != null) {
                        blockers.add("PAYEE_ACCOUNT_NOT_ARCHIVED");
                        reasons.add(blockReason("PAYEE_ACCOUNT_NOT_ARCHIVED", "ERROR", acctIssue,
                                "到 主数据 > 供应商 > 银行账户 补录或更正该供应商的开户账号，使档案账号与付款账号一致"
                                        + "后回本批次原地重试执行（本拦口不置失败、不发起支付失败处理流）"));
                    }

                    // 拦口④反欺诈未复审结案（execute:983-988；paymentBlockReason 是纯查询，影子模式下返回 null）
                    String fraudReason = fraudDetectionService.paymentBlockReason(pay.getBillId());
                    if (fraudReason != null) {
                        blockers.add("FRAUD_OPEN");
                        reasons.add(blockReason("FRAUD_OPEN", "ERROR", fraudReason,
                                "到 风控 > 欺诈检测 把该高风险记录复审结案后回本批次重试"));
                    }

                    // 三单匹配拦口——同一条 check()，批次锁死在 APPROVED 的主因
                    ThreeWayMatchService.MatchRow m = threeWayMatchService.check(pay.getBillId());
                    entry.put("matchStatus", m.getMatchStatus());
                    entry.put("frozen", m.isFrozen());
                    entry.put("payAmount", m.getPayAmount());
                    entry.put("invoiceAmount", m.getInvoiceAmount());
                    entry.put("receiptAmount", m.getReceiptAmount());
                    entry.put("poAmount", m.getPoAmount());
                    entry.put("contractAmount", m.getContractAmount());
                    entry.put("poNo", m.getPoNo());
                    entry.put("receiptNo", m.getReceiptNo());
                    entry.put("contractNo", m.getContractNo());
                    entry.put("reconStatus", m.getReconStatus());
                    // 这两列让页面能把「收款方」与「供应商」并排显示，而不是只看一个合成名
                    entry.put("payeeNameOnBill", m.getPayeeName());
                    entry.put("poSupplierName", m.getPoSupplierName());
                    List<String> missingLegs = new ArrayList<>();
                    List<Map<String, Object>> matchIssues = new ArrayList<>();
                    for (ThreeWayMatchService.MatchIssue it : m.getIssues()) {
                        Map<String, Object> im = new LinkedHashMap<>();
                        im.put("code", it.getCode());
                        im.put("level", it.getLevel());
                        im.put("msg", it.getMsg());
                        im.put("missingLeg", missingLegLabel(it.getCode()));
                        matchIssues.add(im);
                        if (!missingLegLabel(it.getCode()).isEmpty() && !missingLegs.contains(it.getCode())) {
                            missingLegs.add(it.getCode());
                        }
                        if ("ERROR".equals(it.getLevel())) {
                            reasons.add(blockReason(it.getCode(), "ERROR", it.getMsg(),
                                    matchExit(it.getCode())));
                        }
                    }
                    entry.put("issues", matchIssues);
                    entry.put("missingLegCodes", missingLegs);
                    entry.put("missingLegs", missingLegs.stream().map(PayBatchService::missingLegLabel).toList());
                    if ("FAILED".equals(m.getMatchStatus())) {
                        blockers.add("THREE_WAY_MATCH_FAILED");
                    }
                }
            }

            if (blockers.isEmpty()) {
                continue;
            }
            entry.put("blockers", blockers);
            entry.put("blocking", true);
            entry.put("reasons", reasons);
            report.add(entry);
        }
        log.info("[三单匹配拦口透出] 批次 {} 共 {} 条明细存在执行期拦口", batch.getBatchNo(), report.size());
        return report;
    }

    /** 拦口原因的统一结构：码 / 级别 / 原因 / 处理出口（页面据此渲染「缺哪一单 + 去哪儿补」）。 */
    private static Map<String, Object> blockReason(String code, String level, String msg, String exit) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", code);
        r.put("level", level);
        r.put("msg", msg);
        r.put("exit", exit);
        return r;
    }

    /**
     * 差异码 → 人可读的缺环节名。与 {@link ThreeWayMatchService#check} 的 issue 码一一对应，
     * 刻意用 switch 而非新增 Map（同族 ISSUE_ROUTE 已占 Map.of 的 10 项上限，不宜再挤）。
     */
    private static String missingLegLabel(String code) {
        return switch (code == null ? "" : code) {
            case "NO_PO" -> "缺采购订单（PO）";
            case "NO_RECEIPT" -> "缺收货单";
            case "NO_CONTRACT" -> "缺采购合同（四单缺合同）";
            case "PAY_EXCEED_PO" -> "本次付款超采购订单金额（不含税金额）";
            case "PAY_EXCEED_INVOICE" -> "发票覆盖不足（本次付款 > 已关联发票＋容差）";
            case "INVOICE_OVER_RECEIPT" -> "发票金额超收货单且超容差";
            case "INVOICE_IN_TOLERANCE" -> "发票超收货（容差内，仅提示）";
            case "RECEIPT_OVER_PO" -> "累计收货超采购订单金额且超容差";
            case "RECEIPT_IN_TOLERANCE" -> "收货超订单（容差内，仅提示）";
            case "RECEIPT_QTY_OVER_PO" -> "已收数量超订购数量";
            case "RECON_UNCONFIRMED" -> "供应商本期对账单未确认";
            case "PAYEE_NOT_SUPPLIER" -> "收款方与采购订单供应商不是同一主体（仅提示）";
            default -> "";
        };
    }

    /** 差异码 → 处理出口（438「无解拦出口」的直接对策：每条拦口都配一句「去哪儿补」）。 */
    private static String matchExit(String code) {
        return switch (code == null ? "" : code) {
            case "PAY_EXCEED_INVOICE", "INVOICE_OVER_RECEIPT" ->
                    "到 发票池 →「关联单据」为该付款单补录／改挂发票，使发票覆盖额 ≥ 本次付款额，"
                            + "然后回本批次直接重试执行（批次仍停在已审批态，无需重建）";
            case "NO_PO" -> "到 三单匹配（手工关联）为该付款单关联采购订单，或由采购补单后重新组批";
            case "NO_RECEIPT" -> "到 三单匹配（手工关联）为该付款单关联收货单；确无实物到货请先补做收货";
            case "NO_CONTRACT" -> "到 三单匹配 / 采购订单 补挂合同，或按豁免情形记录";
            case "RECEIPT_OVER_PO", "RECEIPT_QTY_OVER_PO" -> "请采购核对收货是否超订单，冲减多收或追加订单后重试";
            case "RECON_UNCONFIRMED" -> "到 应付对账 确认该供应商本期对账单";
            case "PAYEE_NOT_SUPPLIER" -> "本项为提示项、不拦付款：确认是否属委托付款／第三方代收并留书面依据，"
                    + "否则更正付款单收款方或采购订单供应商";
            default -> "按上述原因在对应界面补齐后回本批次重试执行";
        };
    }

    /**
     * 执行付款（经 BankGateway：默认模拟，生产接银企直联）→ 回写付款记录与单据。批次行锁防并发重复执行。
     * <p>返回值：{@code PAID}＝已提交并付讫；{@code FAILED}＝银行拒绝（批次转 FAILED 并发起 TR_PAY_FAIL 处理流）；
     * {@code DEFERRED}＝收款账号未经供应商档案核对，整批<b>未提交银企</b>、批次状态未动、不发起任何流程，
     * 逐条缺档说明落在 {@code op_pay_batch_detail.fail_reason}（批次明细弹窗可见），补齐档案后原地重试即可。
     */
    @Transactional(rollbackFor = Exception.class)
    public String execute(Long batchId) {
        OpPayBatch batch = requireBatchForUpdate(batchId); // 行锁：并发执行串行化，第二个读到 PAID 被拦截
        // 「禁止再次付款」写侧校验：列表已红标的两种账证不符组合（VOID＋仍挂 POSTED 活凭证、
        // PAID＋无任何活凭证）在此显式拒付——账证不符状态下的再次打款＝在错误账务基础上再付一笔，
        // 先到 凭证工作台 核对该批次名下凭证、人工补平后再放行（与前端按钮双保险）。
        String voucherAnomaly = voucherAnomalyOf(batch);
        if (voucherAnomaly != null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "账证不符拦截：付款批次 " + batch.getBatchNo() + "（状态 " + batch.getStatus()
                            + "，异常码 " + voucherAnomaly + "）凭证与批次状态不勾稽，禁止再次付款。"
                            + "处理出口：到 凭证工作台 核对该批次名下凭证（作废并补办/补制付款凭证）后重试");
        }
        // 前置条件只认"审批已办结"。CONFIRMED 只说明出纳已确认、TR_PAY 仍在途，
        // 此时打款＝钱已出去而流程没批完。
        // 失败重试仍放行：FAILED 直接重试；TR_PAY_FAIL 审批通过后改置 APPROVED（见 TreasuryBizAction）。
        if (!"APPROVED".equals(batch.getStatus()) && !"FAILED".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已审批或付款失败的批次可执行付款");
        }
        // 顺序校验（先审后付，fail-closed）：状态写着 APPROVED/FAILED 不等于"付款审批链已走完"——
        // ①TR_PAY_FAIL 审批通过也会把批次改置 APPROVED（TreasuryBizAction.handlePayFail），此时原 TR_PAY
        //   可能仍在途；②历史批次可能被前置校验上线前的版本打成 PAID，而审批链没走完。
        // 打款指令发出前按流程变量再确认一次：本批次名下不允许存在在办 TR_PAY 任务。
        if (wfEngineFacade.hasActiveTask("TR_PAY", batchId)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "付款批次 " + batch.getBatchNo() + " 的付款审批（TR_PAY）仍有在办节点，"
                            + "须待审批办结后才能对外付出款项（先审后付）");
        }
        // 齐套校验·执行层：确认时点的校验保留不动，这里在真的对外打款之前再查一次——
        // 确认与执行之间可能隔了几天，附件/合同/出账账户都可能变化，且 FAILED 重试路径不经过 confirm。
        // 与下面的反欺诈、三单匹配、黑名单同属"付款指令发出前"的硬阻断位。
        // 差异：外部资料只有"动作发生前的条件检查"通式、无"付款执行前再校验齐套"的成文条目，此校验为我方加严。
        billCompletenessGuard.assertComplete("TR_PAY",
                BillCompletenessGuard.STAGE_EXECUTE, "TR_PAY", batchId);
        List<OpPayBatchDetail> details = opPayBatchDetailMapper.selectList(
                new LambdaQueryWrapper<OpPayBatchDetail>().eq(OpPayBatchDetail::getBatchId, batchId));
        String batchNo = batch.getBatchNo();
        LocalDateTime now = LocalDateTime.now();

        // 三单匹配刚性拦截：批次内任一明细关联单据超付（FAILED）则整批禁止执行
        for (OpPayBatchDetail d : details) {
            // 控制点②「收款账户缺失」：打款指令里没有收方账号，钱根本不知道该汇到哪——
            // 原来这一段只查单据/反欺诈/三单匹配，账户为空的明细照样提交给银行网关（现网 7/7 有值，
            // 但校验不存在＝脏数据/人工漏填即静默出款）。fail-closed，逐条拒付。
            if (d.getBankAccount() == null || d.getBankAccount().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "付款控制拦截（收款账户缺失）：批次 " + batchNo + " 明细 " + d.getId()
                                + " 收款方[" + (d.getPayeeName() == null ? "未填" : d.getPayeeName())
                                + "] 的银行账号为空，禁止提交付款指令。"
                                + "处理出口：到 收款方主数据 补录该收款方账号并重新组批，或在批次里删除本条明细。");
            }
            if (d.getBillPaymentId() == null) continue;
            ExpBillPayment pay = expBillPaymentMapper.selectById(d.getBillPaymentId());
            if (pay != null && pay.getBillId() != null) {
                // 供应商黑名单兜底：批次执行（打款前）再拦一次
                assertSupplierNotBlacklisted(pay.getPayeeType(), pay.getPayeeId());
                // 「禁用供应商」：只认 md_supplier.blacklist 不够——禁用实际靠 status='DISABLED' 表达，
                // 不查状态列，"禁用供应商"在付款侧就是空的。黑名单与禁用二者其一即拒付。
                assertSupplierNotDisabled(pay.getPayeeType(), pay.getPayeeId(), pay.getPayeeName());
                // 控制点③：收款方在 md_payee 无任何账户登记（或付款记录压根没关联 payee_id）
                // 时记录——账号从未与主数据核对过的付款，事后无从追溯"付给了谁"。
                warnIfPayeeWithoutMasterAccount(pay.getPayeeType(), pay.getPayeeId(), pay.getPayeeName(),
                        pay.getBillId());
                // 反欺诈硬阻断位放在付款指令执行前（提单侧只打标不挡流程）。
                // 影子模式 fraud.shadow_mode.enabled=1 时只记日志不拒付，供演练期观察误拦率。
                String fraudReason = fraudDetectionService.paymentBlockReason(pay.getBillId());
                if (fraudReason != null) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "反欺诈拦截：单据 " + pay.getBillId() + " 存在未复审结案的高风险记录，禁止付款："
                                    + fraudReason + "（请到 风控>欺诈检测 复审结案后重试）");
                }
                ThreeWayMatchService.MatchRow m = threeWayMatchService.check(pay.getBillId());
                if ("FAILED".equals(m.getMatchStatus())) {
                    // 刚性拦截只在这里抛错，必须把业务出口一并讲清楚，否则批次就被执行环节锁死。
                    // 本校验不放宽（超付仍是硬约束，放行与否属校验策略、待产品确认），这里只把业务出口讲清楚。
                    // 批次状态此刻未被改写（本方法整体在同一事务内、抛错即回滚），补足发票后可原地重试。
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "三单匹配拦截：付款单 " + m.getBillNo() + " 超付，禁止执行付款（" + m.getMsg() + "）。"
                                    + "处理出口：到 发票池 →「关联单据」为该付款单补录并关联发票，使发票覆盖额 ≥ 本次付款额"
                                    + "（该弹窗的候选单据已支持按单号/提单人检索本公司他人提的单据，不再只限本人）；"
                                    + "补齐后回到本批次直接重试执行即可，批次仍停在已审批态、无需重建。"
                                    + "若业务上确需「票未到先付」，请改走付款计划/预付申请，而非绕过本次校验。");
                }
            }
        }

        // 防超付预检（调银行前拦截，避免银行已提交后回滚无法撤销）：
        // 已付 = Σ(PAID 付款记录)；拟付 = Σ(本批次归属该单据)；已付+拟付 ≤ 应付(payableAmount=核定-冲抵)。
        // 应付未设上限（payableAmount 为空）时不做强校验，避免误拦无借款冲抵的单据。
        Map<Long, BigDecimal> plannedByBill = new HashMap<>();
        for (OpPayBatchDetail d : details) {
            if (d.getBillPaymentId() == null) continue;
            ExpBillPayment pay = expBillPaymentMapper.selectById(d.getBillPaymentId());
            if (pay != null && pay.getBillId() != null && d.getAmount() != null) {
                plannedByBill.merge(pay.getBillId(), d.getAmount(), BigDecimal::add);
            }
        }
        for (Map.Entry<Long, BigDecimal> e : plannedByBill.entrySet()) {
            ExpBill b = expBillMapper.selectById(e.getKey());
            // 跳过判据不能只认 null：exp_bill.payable_amount 的默认值是 0，不是 null。
            // 「应付=0 却有 WAITING 付款记录」不是业务上的超付，而是单据应付/放款上限从未回写的数据缺陷
            // （典型：借款放款环节，createLoanDisbursementPayment 已按借款额落了付款记录，
            //   而 EXP_LOAN 审批分支不写 payable_amount）。
            // 按「超过应付 0.00」报，操作人会误判成业务超付而放弃付款，钱永远付不出去。
            if (b == null || b.getPayableAmount() == null || b.getPayableAmount().signum() <= 0) {
                log.warn("付款批次 {} 单据 {} 应付金额为空或为 0，跳过防超付校验"
                                + "（该单已落地一或多条付款记录，属应付未回写的数据缺陷，非业务超付）",
                        batchNo, b == null ? e.getKey() : b.getBillNo());
                continue;
            }
            BigDecimal paid = expBillPaymentMapper.selectList(
                            new LambdaQueryWrapper<ExpBillPayment>()
                                    .eq(ExpBillPayment::getBillId, e.getKey())
                                    .eq(ExpBillPayment::getPayStatus, "PAID"))
                    .stream()
                    .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal projected = paid.add(e.getValue());
            if (projected.subtract(b.getPayableAmount()).compareTo(new BigDecimal("0.01")) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "防超付拦截：单据 " + b.getBillNo() + " 已付 " + paid + "，本批拟付 " + e.getValue()
                                + "，合计 " + projected + " 超过应付 " + b.getPayableAmount());
            }
        }

        // 控制点④「超预算」：打款前复核底层单据预算行可用额度（现按 WARN 记录，不硬拦）
        warnIfOverBudgetOnExecute(batchNo, details);

        // 提交银企前的账号完整性校验：账号没有供应商档案开户信息支撑的明细<b>不进银企报文</b>。
        // 规则说明：
        //  · 不做字符集/长度强制校验——脏值的问题在"无出处"，不在"格式不对"；
        //  · 不抛 BizException、不把批次改成 FAILED（＝整批失败）——批次停在原态，档案补齐后原地重试执行即可；
        //  · 逐条把缺档说明落到 op_pay_batch_detail.fail_reason（批次明细弹窗可见），只提示不阻断其它环节；
        //  · 存在缺档行时整批暂缓、不按子集拆分报文：批次出纳凭证（TREASURY_PAY）金额取 op_pay_batch.total_amount
        //    全额，付一部分留一部分会造成本金凭证与实付不一致——拆分规则待主窗判定后再放开。
        List<OpPayBatchDetail> unarchivedLines = new ArrayList<>();
        Map<Long, String> archiveIssues = new HashMap<>();
        for (OpPayBatchDetail d : details) {
            ExpBillPayment pay = d.getBillPaymentId() == null ? null : expBillPaymentMapper.selectById(d.getBillPaymentId());
            if (pay == null) {
                continue; // 非费用域明细（计划域对接等）无付款记录，收款方类型无从判定，不套本判据
            }
            String issue = payeeAccountArchiveIssue(pay.getPayeeType(), pay.getPayeeId(),
                    d.getPayeeName(), d.getBankAccount());
            if (issue != null) {
                unarchivedLines.add(d);
                archiveIssues.put(d.getId(), issue);
            }
        }
        if (!unarchivedLines.isEmpty()) {
            for (OpPayBatchDetail d : unarchivedLines) {
                String issue = archiveIssues.get(d.getId());
                opPayBatchDetailMapper.update(null,
                        new LambdaUpdateWrapper<OpPayBatchDetail>()
                                .eq(OpPayBatchDetail::getId, d.getId())
                                .set(OpPayBatchDetail::getFailReason,
                                        issue.length() > 480 ? issue.substring(0, 480) : issue)
                                .set(OpPayBatchDetail::getUpdateTime, now));
                log.warn("付款暂缓（收款账号无档案支撑）：批次 {} 明细 {} 收款方[{}] {}",
                        batchNo, d.getId(), d.getPayeeName(), issue);
            }
            log.info("付款批次 {} 有 {} 条明细的收款账号未经供应商档案核对，本次不提交银企（批次状态保持 {}）",
                    batchNo, unarchivedLines.size(), batch.getStatus());
            return "DEFERRED";
        }

        // 执行前锁定账户行并核余额，不足整批拒付——避免「钱付出去了、账户余额页还是旧数字」。
        MdBankAccount payAccount = lockPayAccountForDebit(batch);
        assertEnoughPayBalance(payAccount, batch);

        // 调用银行网关（默认模拟成功；生产替换 DefaultBankGateway 为真实银企直联实现）
        List<BankGateway.PayItem> items = details.stream().map(d -> new BankGateway.PayItem(
                d.getPayeeName(), d.getBankName(), d.getBankAccount(), d.getAmount(), d.getPostscript())).toList();
        BankResult bankResult = bankGateway.submitBatch(batchNo, items);

        // 支付失败闭环：失败不抛异常回滚，而是标记 FAILED + 失败原因记录（前端可见、可重试）
        if (!bankResult.isSuccess()) {
            OpPayBatch failUpdate = new OpPayBatch();
            failUpdate.setId(batchId);
            failUpdate.setStatus("FAILED");
            failUpdate.setFailReason(bankResult.getFailReason());
            failUpdate.setFailTime(now);
            failUpdate.setBankResp(bankResult.getResp());
            opPayBatchMapper.updateById(failUpdate);
            // 支付失败即发起 TR_PAY_FAIL 支付失败处理审批流（出纳登记→资金主管复核失败处理方案）
            try {
                wfEngineFacade.start("TR_PAY_FAIL", batchId, userId(),
                        "支付失败处理-" + batchNo, batch.getTotalAmount());
            } catch (Exception ex) {
                log.error("付款批次 {} 发起支付失败处理审批失败: {}", batchId, ex.getMessage(), ex);
                throw ex;  // 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
            }
            return "FAILED";
        }
        List<String> serials = bankResult.getSerialNos();
        // 银行已受理 → 同事务扣减出账账户余额（付款/放款共用本通道）
        debitPayAccount(payAccount, batch);
        for (int i = 0; i < details.size(); i++) {
            OpPayBatchDetail d = details.get(i);
            String serial = serials != null && serials.size() > i ? serials.get(i) : "SER" + now.getNano() + i;
            OpPayBatchDetail du = new OpPayBatchDetail();
            du.setId(d.getId());
            du.setStatus("SUCCESS");
            du.setBankSerial(serial);
            du.setUpdateTime(now);
            opPayBatchDetailMapper.updateById(du);
            writeBackPayment(d, batchNo, serial, now);
        }
        // 本批已整批提交银企并付讫，清掉上一轮暂缓留下的缺档说明（updateById 忽略 null，需显式 set）
        opPayBatchDetailMapper.update(null,
                new LambdaUpdateWrapper<OpPayBatchDetail>()
                        .eq(OpPayBatchDetail::getBatchId, batchId)
                        .isNotNull(OpPayBatchDetail::getFailReason)
                        .set(OpPayBatchDetail::getFailReason, null));
        OpPayBatch update = new OpPayBatch();
        update.setId(batchId);
        update.setStatus("PAID");
        update.setFinishTime(now);
        update.setBankResp(bankResult.getResp());
        opPayBatchMapper.updateById(update);
        // D6：打款成功按收款方归集核销采购付款计划（paid_amount 累加、全额置 DONE；去重见 op_plan_paid_log）
        try {
            planWritebackService.writeBackOnBatchPaid(batch.getCompanyId(), batchNo, details);
        } catch (Exception ex) {
            log.error("付款批次 {} 计划核销回写失败（不影响付款结果）: {}", batchNo, ex.getMessage(), ex);
        }
        // 付款完成后兜底归集（确认时已归集则去重跳过）
        mdCipService.aggregateFromPayBatch(batch.getSourceDocType(), batch.getSourceDocId(),
                batch.getBatchNo(), batch.getTotalAmount());
        // 重试成功后清空失败记录（updateById 忽略 null 字段，需显式 set）
        opPayBatchMapper.update(null, new LambdaUpdateWrapper<OpPayBatch>()
                .eq(OpPayBatch::getId, batchId)
                .set(OpPayBatch::getFailReason, null)
                .set(OpPayBatch::getFailTime, null));
        // 钱此刻才真的出去 —— 预算「占用 → 已用」在这一步确认，不在审批通过时。
        // 放在付款主链路全部写入数据库之后、且用独立事务（REQUIRES_NEW）＋逐单 try/catch：
        // 预算侧任何异常都不允许回滚已提交的银行指令。
        settleBudgetOnPaid(batch, details);
        // 后半：批次出纳凭证（TREASURY_PAY）按设计以 bill_id = 批次id 写入数据库
        // （VoucherService.generateTreasuryPayVoucher），上面逐单据的 autoPostDraftByBill 永远够不到它，
        // 于是"批次 PAID 而凭证永停 DRAFT"。此处补一次批次级过账，同走独立事务通道。
        // 走批次作用域通道：单据级通道的谓词是 bill_id = ?，而批次 id 与单据 id 同段自增、数值会重叠，
        // 拿批次 id 去查单据级凭证就会连带把"同号单据"的凭证一起过账并盖上本批次号＝那张凭证的钱根本没在本批次付出。
        try {
            voucherAutoPostService.autoPostDraftByBatch(batchId, batchNo, userId());
        } catch (Exception ex) {
            log.error("付款批次 {} 付讫自动过账批次凭证异常，可手工批量记账补账: {}", batchNo, ex.getMessage(), ex);
        }
        // PAID 收尾必须校验凭证存在——钱已出去而批次名下（单据级/批次级）查无任何活凭证时，
        // 不得静默视为正常付讫（「PAID 却零张凭证」）。此处不做状态回退
        // （FAILED 可重试再打款、PAID 是防重复组批规则的唯一关联点），以 fail_reason 留异常痕，
        // 由列表侧 voucherAnomaly 红标＋人工补制凭证办结。
        if (!hasAnyVoucherForBatch(batch)) {
            OpPayBatch anomalyUpdate = new OpPayBatch();
            anomalyUpdate.setId(batchId);
            anomalyUpdate.setFailReason(PAID_NO_VOUCHER_NOTE);
            opPayBatchMapper.updateById(anomalyUpdate);
            log.error("付款批次 {} 打款成功但名下无任何有效凭证（单据级/批次级皆无），已留账证不符异常痕，需人工补制付款凭证", batchNo);
        }
        return "PAID";
    }

    /** 回写付款记录 + 单据已付金额 */
    private void writeBackPayment(OpPayBatchDetail d, String batchNo, String serial, LocalDateTime now) {
        // 非费用域明细（bill_payment_id 为空，如计划域对接明细）：无付款记录可回写，仅核销计划（PlanWritebackService）
        if (d.getBillPaymentId() == null) {
            log.info("付款批次 {} 明细 {} 无关联付款记录（非费用域明细），跳过单据回写", batchNo, d.getId());
            return;
        }
        ExpBillPayment pu = new ExpBillPayment();
        pu.setId(d.getBillPaymentId());
        pu.setPayStatus("PAID");
        pu.setPayTime(now);
        pu.setPayBatchNo(batchNo);
        pu.setPaySerialNo(serial);
        pu.setUpdateTime(now);
        expBillPaymentMapper.updateById(pu);

        ExpBillPayment p = expBillPaymentMapper.selectById(d.getBillPaymentId());
        if (p != null && p.getBillId() != null) {
            ExpBill bill = expBillMapper.selectById(p.getBillId());
            if (bill != null) {
                ExpBill bu = new ExpBill();
                bu.setId(bill.getId());
                // 已付 = Σ(PAID 付款记录)：重算而非累加，可自愈历史脏累计
                BigDecimal paid = expBillPaymentMapper.selectList(
                                new LambdaQueryWrapper<ExpBillPayment>()
                                        .eq(ExpBillPayment::getBillId, bill.getId())
                                        .eq(ExpBillPayment::getPayStatus, "PAID"))
                        .stream()
                        .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                bu.setPaidAmount(paid);
                // 状态机：单据全部付款完成→APPROVED→PAID
                // 例外：借款单（EXP_LOAN）放款付讫≠借款结清。借款的生命周期是
                // 已批未放(APPROVED) → 已放款在贷(仍 APPROVED，loan_balance 挂账) → (部分/全额还款 → REPAID)。
                // 放款成功就把这笔借款迁成 PAID，等于宣告借款已了结：借款台账、逾期/限额判定、
                // 还款核销 FIFO 一律按 status ∈ {APPROVED, PARTIAL_REPAID} 认「在贷借款」，
                // 于是 loan_balance 还挂着钱却无人可还（这笔借款从在贷池消失）。
                // 借款的了结只有一个出口：repayLoan 按 loan_balance 归零置 REPAID。
                // 注意 exp_bill.bill_type 库内取值是无前缀的 'LOAN'（'EXP_LOAN' 是流程侧 biz_type），
                // 两种写法都判，避免将来单据类型编码换规则时静默失效。
                String billType = bill.getBillType();
                boolean isLoan = "LOAN".equalsIgnoreCase(billType) || "EXP_LOAN".equalsIgnoreCase(billType);
                boolean billPaidNow = false;
                if (!isLoan && bill.getPayableAmount() != null && paid.compareTo(bill.getPayableAmount()) >= 0
                        && "APPROVED".equals(bill.getStatus())) {
                    bu.setStatus("PAID");
                    // exp_bill.pay_time 没有别的写入点，随 APPROVED→PAID 同写（去重：已有值不覆盖）
                    bu.setPayTime(bill.getPayTime() == null ? now : bill.getPayTime());
                    billPaidNow = true;
                    log.info("单据 {} 全额付款完成 APPROVED→PAID", bill.getBillNo());
                }
                expBillMapper.updateById(bu);
                if (billPaidNow) {
                    // 钱已打出、单据付讫是最终状态，作业链（票据审核/付款审核/记账归档）
                    // 不会再有人办——办结未办结池任务，杜绝「已付款仍挂审核待认领」。
                    // 借款单不置 PAID，其作业链由还款结清判定（settleLoanAfterRepay）办结。
                    sharingService.closeOnBillTerminal(bill.getId(), "付款批次已执行、单据全额付讫");
                }
                // 实付入账：单据付讫（含刚刚迁移 PAID）后自动过账关联 DRAFT 凭证。
                // 独立事务执行，凭证失败不回滚付款；失败可到凭证工作台批量记账补账。
                try {
                    voucherAutoPostService.autoPostDraftByBill(bill.getId(), batchNo, userId());
                } catch (Exception ex) {
                    log.error("单据 {} 付款完成自动过账凭证异常，可手工批量记账补充: {}", bill.getBillNo(), ex.getMessage(), ex);
                }
            }
        }
    }

    public PageResult<OpPayBatch> batchPage(long pageNum, long pageSize) {
        Page<OpPayBatch> p = opPayBatchMapper.selectPage(
                Page.of(pageNum, pageSize),
                new LambdaQueryWrapper<OpPayBatch>().orderByDesc(OpPayBatch::getId));
        fillDisplayFields(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 批次列表的「单据状态 / 凭证记账」三列必须在后端补：接口直出实体时这几列压根不在返回键里，
     * 只改前端绑定拿不到数据。
     * 规则：批次 → op_pay_batch_detail → exp_bill_payment.bill_id → exp_bill / op_voucher.bill_id（明细侧全覆盖，
     * 来源侧的 AUTO_GROUP 合并批次 source_doc_id 按设计为 NULL）；仅在明细缺失时按 source_doc_id 兜底，
     * 且只有费用族才把它当 exp_bill 主键解释——source_doc_id 是跨域裸主键（CIP 批次指向 md_cip），
     * 跨域撞号同理，不能只看值不看类型。
     * <p>另外两处同源补充也在这里办结，不另开接口：
     * 批次<b>自身</b>的 TR_PAY 审批流（{@code payProcInstId}/{@code payFlowStatus}，按
     * {@code wf_instance(biz_type='TR_PAY', biz_id=批次主键)} 解析）——与 {@code procInstId}（源单据流）
     * 分列并存，前端追溯入口（{@code Pay.vue} 已声明 {@code biz-type="TR_PAY"}）才拿得到正确对象；
     * 批次级出纳凭证（{@code source_type='TREASURY_PAY' AND source_doc_id=批次主键}）并入「凭证记账」两列，
     * 原来的单据域 join 结构上取不到它。列的物理写入数据库需 DDL，见 {@code OpPayBatch} 类注释的注释态脚本。
     */
    private static final Set<String> BILL_DOMAIN_SOURCE_TYPES =
            Set.of("EXP_CLAIM", "EXP_BILL_PAYMENT", "EXP_PAY", "EXP_LOAN", "EXP_REPAY", "EXP_APPLY");

    private void fillDisplayFields(List<OpPayBatch> batches) {
        if (batches == null || batches.isEmpty()) {
            return;
        }
        List<Long> batchIds = batches.stream().map(OpPayBatch::getId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, Set<Long>> billIdsByBatch = new HashMap<>();
        if (!batchIds.isEmpty()) {
            List<OpPayBatchDetail> details = opPayBatchDetailMapper.selectList(
                    new LambdaQueryWrapper<OpPayBatchDetail>().in(OpPayBatchDetail::getBatchId, batchIds));
            Set<Long> paymentIds = details.stream().map(OpPayBatchDetail::getBillPaymentId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, Long> billIdByPayment = new HashMap<>();
            if (!paymentIds.isEmpty()) {
                for (ExpBillPayment pay : expBillPaymentMapper.selectBatchIds(paymentIds)) {
                    if (pay.getBillId() != null) {
                        billIdByPayment.put(pay.getId(), pay.getBillId());
                    }
                }
            }
            for (OpPayBatchDetail d : details) {
                Long billId = d.getBillPaymentId() == null ? null : billIdByPayment.get(d.getBillPaymentId());
                if (billId != null) {
                    billIdsByBatch.computeIfAbsent(d.getBatchId(), k -> new HashSet<>()).add(billId);
                }
            }
        }
        for (OpPayBatch b : batches) {
            boolean noDetailBill = billIdsByBatch.getOrDefault(b.getId(), Collections.emptySet()).isEmpty();
            if (noDetailBill && b.getSourceDocId() != null && b.getSourceDocType() != null
                    && BILL_DOMAIN_SOURCE_TYPES.contains(b.getSourceDocType().trim().toUpperCase())) {
                billIdsByBatch.computeIfAbsent(b.getId(), k -> new HashSet<>()).add(b.getSourceDocId());
            }
        }
        Set<Long> billIds = billIdsByBatch.values().stream()
                .flatMap(Collection::stream).collect(Collectors.toSet());
        Map<Long, String> statusByBill = new HashMap<>();
        Map<Long, List<OpVoucher>> vouchersByBill = new HashMap<>();
        if (!billIds.isEmpty()) {
            for (ExpBill bill : expBillMapper.selectBatchIds(billIds)) {
                statusByBill.put(bill.getId(), bill.getStatus());
            }
            // 反向按 bill_id 查凭证同样要过来源域校验：op_voucher.bill_id 是跨域裸主键，
            // 非费用族凭证（合同/采购/资金调拨…）同号会串到付款批次上（同 VoucherService 的规则）
            for (OpVoucher v : opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                    .in(OpVoucher::getBillId, billIds)
                    .in(OpVoucher::getSourceType, VoucherService.EXPENSE_BILL_SOURCES)
                    .orderByAsc(OpVoucher::getId))) {
                vouchersByBill.computeIfAbsent(v.getBillId(), k -> new ArrayList<>()).add(v);
            }
        }
        // 批次级出纳凭证（source_type=TREASURY_PAY，bill_id 与 source_doc_id 都是批次主键）。
        // 上面那条按「单据 bill_id + 费用族来源」的规则结构上取不到它——批次级凭证的 bill_id 是批次号，
        // 而 op_pay_batch.id 从不出现在 billIds 里，于是那张出纳凭证在批次列表上一直是空的
        // （批次级凭证只覆盖按批次整体出账的批次，其余批次的出纳环节由单据级 EXPENSE_PAY 凭证承载）。
        // 按 source_doc_id 成对精确匹配查询，不与 billIds 混用，避免跨域串号。
        Map<Long, List<OpVoucher>> vouchersByBatch = new HashMap<>();
        // 批次自身的 TR_PAY 审批流（与 op_pay_batch.proc_inst_id 那个源单据流是两回事）。
        // 批次行上不落这个实例号，追溯入口只能靠 wf_instance 权威映射。
        Map<Long, WfInstance> flowByBatch = new HashMap<>();
        if (!batchIds.isEmpty()) {
            // 批次级环节查询与作废办结范围共用同一来源集合 BATCH_LEVEL_VOUCHER_SOURCES
            // （TREASURY_PAY/CIP_PAY_BATCH），读收两侧规则一致防漂移。
            for (OpVoucher v : opVoucherMapper.selectList(new LambdaQueryWrapper<OpVoucher>()
                    .in(OpVoucher::getSourceType, BATCH_LEVEL_VOUCHER_SOURCES)
                    .in(OpVoucher::getSourceDocId, batchIds)
                    .orderByAsc(OpVoucher::getId))) {
                vouchersByBatch.computeIfAbsent(v.getSourceDocId(), k -> new ArrayList<>()).add(v);
            }
            // 升序遍历、同批次后写覆盖前写 ⇒ 留下的是最新一次发起（驳回重提 REJECTED→DRAFT→再 confirm 会有多条实例）
            for (WfInstance w : wfInstanceMapper.selectList(
                    new LambdaQueryWrapper<WfInstance>()
                            .eq(WfInstance::getBizType, "TR_PAY")
                            .in(WfInstance::getBizId, batchIds)
                            .orderByAsc(WfInstance::getId))) {
                if (w.getBizId() != null) {
                    flowByBatch.put(w.getBizId(), w);
                }
            }
        }
        for (OpPayBatch b : batches) {
            Set<Long> ids = billIdsByBatch.getOrDefault(b.getId(), Collections.emptySet());
            Set<String> billStatuses = new TreeSet<>();
            Set<String> voucherNos = new LinkedHashSet<>();
            Set<String> voucherStatuses = new HashSet<>();
            for (Long billId : ids) {
                String st = statusByBill.get(billId);
                if (st != null) {
                    billStatuses.add(st);
                }
                for (OpVoucher v : vouchersByBill.getOrDefault(billId, Collections.emptyList())) {
                    // 作废凭证不算活凭证、不进「凭证/记账」列。不过滤的话下面的归并规则
                    // "非 DRAFT 即 POSTED" 会把 VOID 误映成"已记账"，把作废印成记账
                    if ("VOID".equals(v.getStatus())) {
                        continue;
                    }
                    if (v.getVoucherNo() != null) {
                        voucherNos.add(v.getVoucherNo());
                    }
                    if (v.getStatus() != null) {
                        voucherStatuses.add(v.getStatus());
                    }
                }
            }
            // 批次级出纳凭证并入同一组「凭证记账」列，与单据级凭证共用下面两行的归并规则
            for (OpVoucher v : vouchersByBatch.getOrDefault(b.getId(), Collections.emptyList())) {
                if ("VOID".equals(v.getStatus())) { // 同上，作废凭证不列
                    continue;
                }
                if (v.getVoucherNo() != null) {
                    voucherNos.add(v.getVoucherNo());
                }
                if (v.getStatus() != null) {
                    voucherStatuses.add(v.getStatus());
                }
            }
            b.setBillStatus(billStatuses.isEmpty() ? null
                    : billStatuses.size() == 1 ? billStatuses.iterator().next() : "MIXED");
            b.setVoucherNo(voucherNos.isEmpty() ? null : String.join("、", voucherNos));
            // 凭证只有 DRAFT/POSTED 两态：批次内任一张未过账即「待记账」
            b.setVoucherStatus(voucherStatuses.isEmpty() ? null
                    : voucherStatuses.contains("DRAFT") ? "DRAFT" : "POSTED");
            // ③：账证勾稽红标——两组合判据与写侧 voucherAnomalyOf 同源（防两头漂移）。
            // 判定放在活凭证归并之后：VOID 已过滤，故此处 POSTED 即"确有已记账活凭证"、voucherNos 为空即"确无活凭证"。
            if ("VOID".equals(b.getStatus()) && voucherStatuses.contains("POSTED")) {
                b.setVoucherAnomaly("VOID_WITH_POSTED_VOUCHER");
            } else if ("PAID".equals(b.getStatus()) && voucherNos.isEmpty()) {
                b.setVoucherAnomaly("PAID_WITHOUT_VOUCHER");
            } else {
                b.setVoucherAnomaly(null);
            }
            WfInstance own = flowByBatch.get(b.getId());
            b.setPayProcInstId(own == null ? null : own.getProcInstId());
            b.setPayFlowStatus(own == null ? null : own.getStatus());
        }
    }

    public List<OpPayBatchDetail> details(Long batchId) {
        return opPayBatchDetailMapper.selectList(new LambdaQueryWrapper<OpPayBatchDetail>()
                .eq(OpPayBatchDetail::getBatchId, batchId));
    }

    private OpPayBatch requireBatch(Long batchId) {
        OpPayBatch batch = opPayBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款批次不存在: " + batchId);
        }
        return batch;
    }

    /** 行锁读取批次（须在事务内调用；付款去重） */
    private OpPayBatch requireBatchForUpdate(Long batchId) {
        OpPayBatch batch = opPayBatchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "付款批次不存在: " + batchId);
        }
        return batch;
    }

    /**
     * 出账账户：执行付款/放款前行锁（FOR UPDATE）读取，余额闸与扣减在同一事务内完成。
     * 返回 null 表示不管理余额（历史批次未绑定账户，或账户/批次币种不一致）。
     */
    private MdBankAccount lockPayAccountForDebit(OpPayBatch batch) {
        if (batch.getBankAccountId() == null) {
            log.warn("付款批次 {} 未绑定出账账户，本次不扣减银行余额（历史批次兼容）", batch.getBatchNo());
            return null;
        }
        MdBankAccount acc = mdBankAccountMapper.selectOne(new LambdaQueryWrapper<MdBankAccount>()
                .eq(MdBankAccount::getId, batch.getBankAccountId())
                .last("FOR UPDATE"));
        if (acc == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "出账账户不存在（id=" + batch.getBankAccountId() + "），禁止执行付款");
        }
        if (acc.getCurrencyCode() != null && batch.getCurrencyCode() != null
                && !acc.getCurrencyCode().equalsIgnoreCase(batch.getCurrencyCode())) {
            log.warn("付款批次 {} 出账账户币种 {} 与批次币种 {} 不一致，本次跳过余额校验与扣减",
                    batch.getBatchNo(), acc.getCurrencyCode(), batch.getCurrencyCode());
            return null;
        }
        return acc;
    }

    /** 余额不足整批拒付（与资金调拨 execute 同口径）。须在提交银企之前调用。 */
    private void assertEnoughPayBalance(MdBankAccount acc, OpPayBatch batch) {
        if (acc == null) return;
        BigDecimal amount = batch.getTotalAmount();
        if (amount == null || amount.signum() <= 0) return;
        BigDecimal balance = acc.getBalance() == null ? BigDecimal.ZERO : acc.getBalance();
        if (balance.compareTo(amount) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "出账账户余额不足：账户[" + acc.getAccountName() + "/" + acc.getBankAccount() + "]当前余额 "
                            + balance + "，本批应付 " + amount + "，禁止执行付款。请先入账资金或更换出账账户");
        }
    }

    /** 银行受理后扣减出账账户余额（付款/借款放款共用）. */
    private void debitPayAccount(MdBankAccount acc, OpPayBatch batch) {
        if (acc == null) return;
        BigDecimal amount = batch.getTotalAmount();
        if (amount == null || amount.signum() <= 0) return;
        BigDecimal balance = acc.getBalance() == null ? BigDecimal.ZERO : acc.getBalance();
        MdBankAccount upd = new MdBankAccount();
        upd.setId(acc.getId());
        upd.setBalance(balance.subtract(amount));
        mdBankAccountMapper.updateById(upd);
        log.info("付款批次 {} 付讫，出账账户 {} 余额 {} → {}", batch.getBatchNo(),
                acc.getId(), balance, balance.subtract(amount));
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    // 状态机：PAID→CLOSED→ARCHIVED

    /** 付款批次 PAID→CLOSED（关账，不可再修改） */
    @Transactional(rollbackFor = Exception.class)
    public void closeBatch(Long batchId) {
        OpPayBatch batch = requireBatch(batchId);
        if (!"PAID".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已付款批次可关账，当前状态: " + batch.getStatus());
        }
        OpPayBatch update = new OpPayBatch();
        update.setId(batchId);
        update.setStatus("CLOSED");
        opPayBatchMapper.updateById(update);
        log.info("付款批次 {} 关账 PAID→CLOSED", batch.getBatchNo());
    }

    /** 付款批次 CLOSED→ARCHIVED（归档，最终状态） */
    @Transactional(rollbackFor = Exception.class)
    public void archiveBatch(Long batchId) {
        OpPayBatch batch = requireBatch(batchId);
        if (!"CLOSED".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已关账批次可归档，当前状态: " + batch.getStatus());
        }
        OpPayBatch update = new OpPayBatch();
        update.setId(batchId);
        update.setStatus("ARCHIVED");
        opPayBatchMapper.updateById(update);
        log.info("付款批次 {} 归档 CLOSED→ARCHIVED", batch.getBatchNo());
    }
}
