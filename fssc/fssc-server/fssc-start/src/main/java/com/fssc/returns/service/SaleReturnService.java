package com.fssc.returns.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArStatement;
import com.fssc.arflow.mapper.ArStatementMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.receivable.entity.RcReceivable;
import com.fssc.receivable.mapper.RcReceivableMapper;
import com.fssc.returns.entity.SaleReturn;
import com.fssc.returns.entity.SaleReturnLine;
import com.fssc.returns.mapper.SaleReturnLineMapper;
import com.fssc.returns.mapper.SaleReturnMapper;
import com.fssc.returns.support.ReturnLegMapping;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.service.NumberRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 销售退货单服务（AR 退货退款冲减应收）。
 * 状态机：DRAFT → PENDING → APPROVED → VOIDED；REJECTED 可改后重新提交。
 * 金额表内存正数；退货方式 REFUND 进对账、EXCHANGE 换货补货不进对账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleReturnService {

    private static final String RULE_CODE = "SALE_RETURN";
    private static final BigDecimal TOL = new BigDecimal("0.01");

    private final SaleReturnMapper mapper;
    private final SaleReturnLineMapper lineMapper;
    private final NumberRuleService numberRuleService;
    /** 租户参数（sys_config）：审核/驳回/作废读 approve.deny_self 自审禁止开关，与 Flowable 路径共用同一开关 */
    private final SysConfigMapper sysConfigMapper;
    /** P4-3 审计扩面：单据状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;
    /** R5-D-089 下游腿：REFUND 退货退款 → 应收域（红字贷项 + FIFO 冲减 + 客户对账基础表），EXCHANGE 不写 */
    private final RcReceivableMapper rcReceivableMapper;
    private final ArStatementMapper arStatementMapper;
    /** R5-D-089 客户解析：customer_code → crm_customer（应收单以 customer_id 挂账） */
    private final CrmCustomerMapper crmCustomerMapper;

    // ---------------- 创建 ----------------

    @Transactional(rollbackFor = Exception.class)
    public SaleReturn create(SaleReturn r) {
        if (r.getAmount() == null || r.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货金额必须大于0");
        }
        if (r.getCustomerCode() == null || r.getCustomerCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客户代码必填");
        }
        if (r.getBillDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货日期必填");
        }
        if (r.getReturnMode() == null || r.getReturnMode().isBlank()) {
            r.setReturnMode(SaleReturn.MODE_REFUND);
        }
        List<SaleReturnLine> lines = normalizeLines(r);
        validateLineSum(r.getAmount(), lines);
        // R5-D-091：表头税额按明细整算回写（不信任前端 taxAmount）
        r.setTaxAmount(deriveTaxAmount(lines));

        r.setId(null);
        r.setBillNo(numberRuleService.next(RULE_CODE));
        r.setStatus(SaleReturn.ST_DRAFT);
        r.setLineCount(lines.size());
        mapper.insert(r);

        saveLines(r.getId(), lines);
        r.setLines(lines);
        log.info("[returns] 销售退货单 {} 创建", r.getBillNo());
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public SaleReturn update(SaleReturn r) {
        SaleReturn old = require(r.getId());
        if (!List.of(SaleReturn.ST_DRAFT, SaleReturn.ST_REJECTED).contains(old.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿/驳回态可修改：" + old.getBillNo());
        }
        if (r.getAmount() == null || r.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货金额必须大于0");
        }
        // 编辑是整单覆盖（下面直接 set 到 old），不校验就会把 NOT NULL 的 customer_code/bill_date 置空，
        // 变成 500 而不是友好提示——与 create 同口径补齐。
        if (r.getCustomerCode() == null || r.getCustomerCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客户代码必填");
        }
        if (r.getBillDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货日期必填");
        }
        List<SaleReturnLine> lines = normalizeLines(r);
        validateLineSum(r.getAmount(), lines);
        // R5-D-091：编辑整单覆盖时税额同样按明细整算回写
        r.setTaxAmount(deriveTaxAmount(lines));

        old.setCompanyId(r.getCompanyId());
        old.setCompanyCode(r.getCompanyCode());
        old.setCompanyName(r.getCompanyName());
        old.setCustomerCode(r.getCustomerCode());
        old.setCustomerName(r.getCustomerName());
        old.setCcy(r.getCcy());
        old.setBillDate(r.getBillDate());
        old.setAmount(r.getAmount());
        old.setTaxAmount(deriveTaxAmount(lines));
        old.setReturnMode(r.getReturnMode() == null || r.getReturnMode().isBlank() ? SaleReturn.MODE_REFUND : r.getReturnMode());
        old.setSourceDocType(r.getSourceDocType());
        old.setSourceDocId(r.getSourceDocId());
        old.setSourceBillNo(r.getSourceBillNo());
        old.setReturnReason(r.getReturnReason());
        old.setRemark(r.getRemark());
        old.setLineCount(lines.size());
        mapper.updateById(old);

        lineMapper.delete(new LambdaQueryWrapper<SaleReturnLine>().eq(SaleReturnLine::getReturnId, old.getId()));
        saveLines(old.getId(), lines);
        old.setLines(lines);
        return old;
    }

    // ---------------- 状态机 ----------------

    @Transactional(rollbackFor = Exception.class)
    public SaleReturn submit(Long id) {
        SaleReturn r = require(id);
        if (!List.of(SaleReturn.ST_DRAFT, SaleReturn.ST_REJECTED).contains(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 当前状态不可提交");
        }
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        r.setStatus(SaleReturn.ST_PENDING);
        mapper.updateById(r);
        auditTrailService.recordUpdate("SALE_RETURN", id, r.getBillNo(), "sale_return",
                String.valueOf(id),
                auditBefore, java.util.Collections.singletonMap("status", SaleReturn.ST_PENDING), "提交");
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public SaleReturn approve(Long id) {
        SaleReturn r = require(id);
        if (!SaleReturn.ST_PENDING.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 仅待审核态可审核");
        }
        // 第二道防线：单据级「审批人 ≠ 制单人」（与角色隔离互补，齐平 Flowable 的 approve.deny_self）
        assertNotSelf(r, "审核");
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("approver", r.getApprover());
        auditBefore.put("approveTime", r.getApproveTime());
        r.setStatus(SaleReturn.ST_APPROVED);
        r.setApprover(currentUserName());
        r.setApproveTime(LocalDateTime.now());
        mapper.updateById(r);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("approver", r.getApprover());
        auditAfter.put("approveTime", r.getApproveTime());
        auditTrailService.recordUpdate("SALE_RETURN", id, r.getBillNo(), "sale_return",
                String.valueOf(id), auditBefore, auditAfter, "审核通过");
        // R5-D-089（D-V4-005 边界）：APPROVED 收尾下游腿——按 return_mode 分流（ReturnLegMapping 为
        // 采购/销售两域共用映射表）：REFUND 退货退款 ⇒ 生成红字应收（贷项，amount 取负）并 FIFO 冲减
        // 对应 rc_receivable.remain_amount，「进对账」写入客户对账基础表（ar_statement 且置
        // check_status=UNCHECKED 待核对）；EXCHANGE 退货补货 ⇒ 不进对账。与审核同事务收尾
        // （复用 D-DEC-334 收尾事件口径），不另建定时任务。
        if (ReturnLegMapping.entersRecon(r.getReturnMode())) {
            postRefundToAr(r);
        }
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public SaleReturn reject(Long id, String reason) {
        SaleReturn r = require(id);
        if (!SaleReturn.ST_PENDING.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 仅待审核态可驳回");
        }
        // 驳回与审核同属「审批决定」：自己驳回自己等于绕过撤回，一并拦截
        assertNotSelf(r, "驳回");
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回必须填写原因");
        }
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("voidReason", r.getVoidReason());
        r.setStatus(SaleReturn.ST_REJECTED);
        r.setVoidReason(reason);
        mapper.updateById(r);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("voidReason", r.getVoidReason());
        auditTrailService.recordUpdate("SALE_RETURN", id, r.getBillNo(), "sale_return",
                String.valueOf(id), auditBefore, auditAfter, "驳回");
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public SaleReturn voidBill(Long id, String reason) {
        SaleReturn r = require(id);
        if (!List.of(SaleReturn.ST_DRAFT, SaleReturn.ST_PENDING, SaleReturn.ST_REJECTED, SaleReturn.ST_APPROVED)
                .contains(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 当前状态不可作废");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作废必须填写原因（退出对账口径留痕）");
        }
        // 作废「已审核」单 = 反审核（抹掉一条已生效的冲减记录），禁止本人操作；
        // 草稿/在途/驳回态本人作废属流程内清理，无控制被绕过，故不拦。
        if (SaleReturn.ST_APPROVED.equals(r.getStatus())) {
            assertNotSelf(r, "作废");
        }
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("voidReason", r.getVoidReason());
        r.setStatus(SaleReturn.ST_VOIDED);
        r.setVoidReason(reason);
        mapper.updateById(r);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("voidReason", r.getVoidReason());
        auditTrailService.recordUpdate("SALE_RETURN", id, r.getBillNo(), "sale_return",
                String.valueOf(id), auditBefore, auditAfter, "作废");
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SaleReturn r = require(id);
        if (!SaleReturn.ST_DRAFT.equals(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿可删除：" + r.getBillNo());
        }
        lineMapper.delete(new LambdaQueryWrapper<SaleReturnLine>().eq(SaleReturnLine::getReturnId, id));
        mapper.deleteById(id);
    }

    // ---------------- 查询 ----------------

    public SaleReturn get(Long id) {
        SaleReturn r = require(id);
        r.setLines(lineMapper.selectList(new LambdaQueryWrapper<SaleReturnLine>()
                .eq(SaleReturnLine::getReturnId, id).orderByAsc(SaleReturnLine::getLineNo)));
        return r;
    }

    /**
     * 退货单分页查询（列表页与 {@code GET /export} 共用同一查询）。
     *
     * <p>D-N22：原实现只按入参过滤，既无数据范围也不封顶；{@code /export} 传
     * {@code Integer.MAX_VALUE} 调用本方法，等于"凭一个 companyId 就能整表拉走"。
     * 现按 data_scope 五级收口（退货单无部门/经办人列，故只到公司级，其余级别降级本公司）。</p>
     */
    @DataScope("销售退货列表数据范围")
    public PageResult<SaleReturn> page(long pageNum, long pageSize, Long companyId, String customerCode,
                                       String status, String billDateStart, String billDateEnd, String keyword) {
        LambdaQueryWrapper<SaleReturn> q = new LambdaQueryWrapper<>();
        if (companyId != null) q.eq(SaleReturn::getCompanyId, companyId);
        if (customerCode != null && !customerCode.isBlank()) q.eq(SaleReturn::getCustomerCode, customerCode);
        if (status != null && !status.isBlank()) q.eq(SaleReturn::getStatus, status);
        if (billDateStart != null && !billDateStart.isBlank()) q.ge(SaleReturn::getBillDate, billDateStart);
        if (billDateEnd != null && !billDateEnd.isBlank()) q.le(SaleReturn::getBillDate, billDateEnd);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(SaleReturn::getBillNo, keyword).or().like(SaleReturn::getCustomerName, keyword));
        }
        DataScopeKit.apply(q, SaleReturn::getCompanyId, null, null);
        q.orderByDesc(SaleReturn::getId);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SaleReturn> p =
                mapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize), q);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    // ---------------- 内部工具 ----------------

    private List<SaleReturnLine> normalizeLines(SaleReturn r) {
        List<SaleReturnLine> lines = r.getLines();
        if (lines == null) lines = new ArrayList<>();
        List<SaleReturnLine> out = new ArrayList<>();
        int no = 1;
        for (SaleReturnLine ln : lines) {
            if (ln == null) continue;
            ln.setId(null);
            ln.setReturnId(r.getId());
            if (ln.getLineNo() == null) ln.setLineNo(no);
            if (ln.getAmount() == null) ln.setAmount(BigDecimal.ZERO);
            out.add(ln);
            no++;
        }
        return out;
    }

    private void saveLines(Long returnId, List<SaleReturnLine> lines) {
        for (SaleReturnLine ln : lines) {
            ln.setReturnId(returnId);
            lineMapper.insert(ln);
        }
    }

    /** R5-D-091：税额＝Σ 明细行 amount×taxRate/100（四舍五入 2 位），create/update 双入口整算回写。 */
    private BigDecimal deriveTaxAmount(List<SaleReturnLine> lines) {
        BigDecimal tax = BigDecimal.ZERO;
        for (SaleReturnLine ln : lines) {
            if (ln.getAmount() == null || ln.getTaxRate() == null) continue;
            tax = tax.add(ln.getAmount().multiply(ln.getTaxRate())
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP));
        }
        return tax.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void validateLineSum(BigDecimal headerAmount, List<SaleReturnLine> lines) {
        BigDecimal sum = lines.stream().map(l -> l.getAmount() == null ? BigDecimal.ZERO : l.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.subtract(headerAmount).abs().compareTo(TOL) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "明细行金额合计(" + sum + ")与表头金额(" + headerAmount + ")不一致（允许尾差≤0.01）");
        }
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货单至少包含一行明细");
        }
    }

    // ---------------- R5-D-089 下游腿（APPROVED 收尾按 return_mode 分流） ----------------

    /**
     * REFUND 退货退款 → 应收域（与采购侧 R5-D-084 同一套 ReturnLegMapping 映射）：
     * 1）红字应收（贷项）：rc_receivable 新增负向行（amount 取负、sourceDocType=SALE_RETURN）；
     * 2）冲减：对该客户未清应收按到期日 FIFO 冲减 remain_amount（减为 0 置 DONE，口径同收款核销）；
     * 3）进对账：写入客户对账基础表 ar_statement 并置 check_status=UNCHECKED（待核对）。
     * EXCHANGE 不进入本方法。幂等：同退货单已生成红字贷项行则跳过。
     */
    private void postRefundToAr(SaleReturn r) {
        CrmCustomer cust = resolveCustomer(r);
        if (cust == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "退货单 " + r.getBillNo() + " 客户主数据不存在：" + r.getCustomerCode() + "（无法挂账进对账）");
        }
        Long dup = rcReceivableMapper.selectCount(new LambdaQueryWrapper<RcReceivable>()
                .eq(RcReceivable::getSourceDocType, "SALE_RETURN")
                .eq(RcReceivable::getSourceDocId, r.getId()));
        if (dup != null && dup > 0) {
            log.info("[returns] 退货单 {} 已生成红字应收，跳过", r.getBillNo());
            return;
        }
        BigDecimal amount = ReturnLegMapping.negate(r.getAmount());
        // —— 2) 冲减对应未清应收（收款核销同口径：客户 + remain>0 + 非 DONE + 到期日/ID 升序）
        BigDecimal rest = r.getAmount() == null ? BigDecimal.ZERO : r.getAmount();
        BigDecimal offset = BigDecimal.ZERO;
        List<RcReceivable> open = rcReceivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .eq(RcReceivable::getCustomerId, cust.getId())
                .gt(RcReceivable::getRemainAmount, BigDecimal.ZERO)
                .ne(RcReceivable::getStatus, "DONE")
                .orderByAsc(RcReceivable::getDueDate)
                .orderByAsc(RcReceivable::getId));
        for (RcReceivable rec : open) {
            if (rest.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal balance = nz(rec.getRemainAmount());
            BigDecimal cut = balance.compareTo(rest) <= 0 ? balance : rest;
            BigDecimal newRemain = balance.subtract(cut);
            BigDecimal newReceived = nz(rec.getReceivedAmount()).add(cut);
            java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
            auditBefore.put("remainAmount", rec.getRemainAmount());
            auditBefore.put("receivedAmount", rec.getReceivedAmount());
            auditBefore.put("status", rec.getStatus());
            java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("remainAmount", newRemain);
            auditAfter.put("receivedAmount", newReceived);
            RcReceivable u = new RcReceivable();
            u.setId(rec.getId());
            u.setRemainAmount(newRemain);
            u.setReceivedAmount(newReceived);
            if (newRemain.compareTo(BigDecimal.ZERO) == 0) {
                auditAfter.put("status", "DONE");
                u.setStatus("DONE");
            }
            rcReceivableMapper.updateById(u);
            auditTrailService.recordUpdate("RC_RECEIVABLE", rec.getId(), rec.getBillNo(), "rc_receivable",
                    String.valueOf(rec.getId()), auditBefore, auditAfter, "销售退货 " + r.getBillNo() + " 冲减应收余额");
            offset = offset.add(cut);
            rest = rest.subtract(cut);
        }
        // —— 1) 红字应收（贷项）：amount 取负；已冲减部分计「已收」、remain=金额+已冲减（未冲减余额以负值挂账，
        // 恒等式 remain = amount − received 成立，避免与 2) 的冲减双重计数）
        RcReceivable credit = new RcReceivable();
        credit.setTenantId(TenantContext.getTenantId());
        credit.setBillNo(r.getBillNo() + "-R");
        credit.setCustomerId(cust.getId());
        credit.setBillDate(r.getBillDate());
        credit.setCurrencyCode(r.getCcy() == null || r.getCcy().isBlank() ? "CNY" : r.getCcy());
        credit.setAmount(amount);
        credit.setReceivedAmount(ReturnLegMapping.negate(offset));
        credit.setRemainAmount(amount.add(offset));
        credit.setStatus(credit.getRemainAmount().compareTo(BigDecimal.ZERO) == 0 ? "DONE" : "NONE");
        credit.setPeriod(r.getBillDate() == null ? null : r.getBillDate().toString().substring(0, 7));
        credit.setCompanyId(r.getCompanyId());
        credit.setSourceDocType("SALE_RETURN");
        credit.setSourceDocId(r.getId());
        rcReceivableMapper.insert(credit);
        // —— 3) 客户对账基础表：写入红字对账行并置 check_status（UNCHECKED 待核对）
        ArStatement stmt = new ArStatement();
        stmt.setTenantId(TenantContext.getTenantId());
        stmt.setBillNo("ARB" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        stmt.setCompanyId(r.getCompanyId());
        stmt.setCustomerCode(r.getCustomerCode());
        stmt.setCustomerName(r.getCustomerName());
        stmt.setBillType("DOMESTIC");
        stmt.setCcy(credit.getCurrencyCode());
        BigDecimal tax = ReturnLegMapping.negate(r.getTaxAmount() == null ? BigDecimal.ZERO : r.getTaxAmount());
        stmt.setAmount(amount);
        stmt.setTaxAmount(tax);
        stmt.setTotalAmount(amount.add(tax));
        if ("CNY".equalsIgnoreCase(credit.getCurrencyCode())) {
            stmt.setRmbAmount(amount);
            stmt.setRmbTaxAmount(tax);
        }
        stmt.setPreInvoiceFlag(0);
        stmt.setCheckStatus("UNCHECKED");
        stmt.setStatus(ArStatement.ST_IMPORTED);
        stmt.setImportUser(currentUserName());
        stmt.setRemark("销售退货单 " + r.getBillNo() + "（REFUND 退货退款进对账"
                + (r.getSourceBillNo() == null || r.getSourceBillNo().isBlank() ? "" : "，源单 " + r.getSourceBillNo())
                + "；已冲减应收 " + offset + "）");
        arStatementMapper.insert(stmt);
        log.info("[returns] 销售退货单 {} REFUND 分流：红字应收 {}（冲减应收 {}）、对账基础表 {} check_status=UNCHECKED",
                r.getBillNo(), credit.getBillNo(), offset, stmt.getBillNo());
    }

    /** customer_code → crm_customer（应收单以 customer_id 挂账；编码优先、名称兜底） */
    private CrmCustomer resolveCustomer(SaleReturn r) {
        CrmCustomer cust = crmCustomerMapper.selectOne(new LambdaQueryWrapper<CrmCustomer>()
                .eq(CrmCustomer::getCustomerCode, r.getCustomerCode())
                .last("limit 1"));
        if (cust == null && r.getCustomerName() != null && !r.getCustomerName().isBlank()) {
            cust = crmCustomerMapper.selectOne(new LambdaQueryWrapper<CrmCustomer>()
                    .eq(CrmCustomer::getCustomerName, r.getCustomerName())
                    .last("limit 1"));
        }
        return cust;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private SaleReturn require(Long id) {
        SaleReturn r = mapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "销售退货单不存在 id=" + id);
        }
        return r;
    }

    private String currentUserName() {
        try {
            LoginUser u = UserContext.get();
            return u == null ? null : u.getUserName();
        } catch (Exception e) {
            return null;
        }
    }

    private Long currentUserId() {
        try {
            LoginUser u = UserContext.get();
            return u == null ? null : u.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 自审禁止开关（与 {@code WfEngineFacade#isDenySelfEnabled} 同一开关、同一语义，fail-closed）：
     * <ul>
     *  <li>{@code sys_config.approve.deny_self = '1'} → 开启；</li>
     *  <li>配置缺失 / 读取异常 → <b>默认开启</b>（宁可禁止，不静默放行）；</li>
     *  <li>显式 {@code '0'} → 关闭（兼容演示数据）。</li>
     * </ul>
     * 内控依据：审批人必须与制单人分离（方案 §5.5「审核权限授予共享审核岗」）。
     */
    private boolean isDenySelfEnabled() {
        try {
            SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, "approve.deny_self")
                    .last("limit 1"));
            if (c == null || c.getConfigValue() == null) {
                return true;
            }
            return "1".equals(c.getConfigValue().trim());
        } catch (Exception ex) {
            log.error("[returns] 读取 approve.deny_self 异常，fail-closed 默认禁止自审: {}", ex.getMessage(), ex);
            return true;
        }
    }

    /**
     * 单据级「操作人 ≠ 制单人」拦截 —— 第二道防线，与「角色审批/制单分离」互补。
     * <p>角色隔离挡的是「授权时把两侧码塞给同一角色/同一人」；本方法挡的是「运行期单据归属」，
     * 两者缺一不可：超级权限（{@code *:*:*}）与未来授权回退都会绕过前者。
     * <p>{@code create_by} 为空（历史/外部导入数据）时无法判定归属，放行以免误伤存量数据。
     */
    private void assertNotSelf(SaleReturn r, String action) {
        if (!isDenySelfEnabled()) {
            return;
        }
        Long me = currentUserId();
        if (me == null || r.getCreateBy() == null) {
            return;
        }
        if (me.equals(r.getCreateBy())) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "禁止" + action + "自己制单的单据（" + r.getBillNo() + "），请由他人办理");
        }
    }
}
