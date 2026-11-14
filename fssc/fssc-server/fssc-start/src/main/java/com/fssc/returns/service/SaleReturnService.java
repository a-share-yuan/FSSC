package com.fssc.returns.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.arflow.entity.ArStatement;
import com.fssc.arflow.mapper.ArStatementMapper;
import com.fssc.audit.service.AuditTrailService;
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
import com.fssc.sharing.service.VoucherService;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysCompanyMapper;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.service.NumberRuleService;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;
    /** 下游环节：REFUND 退货退款 → 应收域（红字贷项 + FIFO 冲减 + 客户对账基础表），EXCHANGE 不写 */
    private final RcReceivableMapper rcReceivableMapper;
    private final ArStatementMapper arStatementMapper;
    /** 客户解析：customer_code → crm_customer（应收单以 customer_id 挂账） */
    private final CrmCustomerMapper crmCustomerMapper;
    /** 红字应收（贷项）走应收族同一制证钩子——RECEIVABLE 族本就有制证写入位置
     *  （应收审批收尾 ReceivableBizAction、开票收尾 InvoiceIssueBizAction 都出证），
     *  唯独本环节自建的红字应收不制证 ⇒ 应收侧账面净额与总账会差一笔退货额。 */
    private final VoucherService voucherService;
    /** company_code/company_name 按 company_id 补充（原两列恒 NULL） */
    private final SysCompanyMapper sysCompanyMapper;
    /** 历史行：读时把 approver 工号换成真名 */
    private final SysUserMapper sysUserMapper;

    // 创建

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
        assertCustomerInMaster(r);
        fillCompanyCoords(r);
        List<SaleReturnLine> lines = normalizeLines(r);
        validateLineSum(r.getAmount(), lines);
        // 表头税额按明细整算回写（不信任前端 taxAmount）
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
        // 变成 500 而不是友好提示——与 create 同一规则补齐。
        if (r.getCustomerCode() == null || r.getCustomerCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客户代码必填");
        }
        if (r.getBillDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退货日期必填");
        }
        assertCustomerInMaster(r);   // （与 create 同校验：编辑改客户代码同样要现有）
        fillCompanyCoords(r);
        List<SaleReturnLine> lines = normalizeLines(r);
        validateLineSum(r.getAmount(), lines);
        // 编辑整单覆盖时税额同样按明细整算回写
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

    // 状态机

    @Transactional(rollbackFor = Exception.class)
    public SaleReturn submit(Long id) {
        SaleReturn r = require(id);
        if (!List.of(SaleReturn.ST_DRAFT, SaleReturn.ST_REJECTED).contains(r.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单据 " + r.getBillNo() + " 当前状态不可提交");
        }
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        r.setStatus(SaleReturn.ST_PENDING);
        mapper.updateById(r);
        auditTrailService.recordUpdate("SALE_RETURN", id, r.getBillNo(), "sale_return",
                String.valueOf(id),
                auditBefore, Collections.singletonMap("status", SaleReturn.ST_PENDING), "提交");
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
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("approver", r.getApprover());
        auditBefore.put("approveTime", r.getApproveTime());
        r.setStatus(SaleReturn.ST_APPROVED);
        r.setApprover(currentUserName());
        r.setApproveTime(LocalDateTime.now());
        mapper.updateById(r);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", r.getStatus());
        auditAfter.put("approver", r.getApprover());
        auditAfter.put("approveTime", r.getApproveTime());
        auditTrailService.recordUpdate("SALE_RETURN", id, r.getBillNo(), "sale_return",
                String.valueOf(id), auditBefore, auditAfter, "审核通过");
        // 边界：APPROVED 收尾下游环节——按 return_mode 分流（ReturnLegMapping 为
        // 采购/销售两域共用映射表）：REFUND 退货退款 ⇒ 生成红字应收（贷项，amount 取负）并 FIFO 冲减
        // 对应 rc_receivable.remain_amount，「进对账」写入客户对账基础表（ar_statement 且置
        // check_status=UNCHECKED 待核对）；EXCHANGE 退货补货 ⇒ 不进对账。与审核同事务收尾
        // 复用 收尾事件规则，不另建定时任务。
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
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("voidReason", r.getVoidReason());
        r.setStatus(SaleReturn.ST_REJECTED);
        r.setVoidReason(reason);
        mapper.updateById(r);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
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
            throw new BizException(ErrorCode.PARAM_INVALID, "作废必须填写原因（退出对账需记录）");
        }
        // 作废「已审核」单 = 反审核（抹掉一条已生效的冲减记录），禁止本人操作；
        // 草稿/在途/驳回态本人作废属流程内清理，无控制被绕过，故不拦。
        if (SaleReturn.ST_APPROVED.equals(r.getStatus())) {
            assertNotSelf(r, "作废");
        }
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", r.getStatus());
        auditBefore.put("voidReason", r.getVoidReason());
        r.setStatus(SaleReturn.ST_VOIDED);
        r.setVoidReason(reason);
        mapper.updateById(r);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
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

    // 查询

    public SaleReturn get(Long id) {
        SaleReturn r = require(id);
        r.setLines(lineMapper.selectList(new LambdaQueryWrapper<SaleReturnLine>()
                .eq(SaleReturnLine::getReturnId, id).orderByAsc(SaleReturnLine::getLineNo)));
        fillApproverNames(Collections.singletonList(r));
        return r;
    }

    /**
     * 退货单分页查询（列表页与 {@code GET /export} 共用同一查询）。
     *
     * <p>只按入参过滤的话，既无数据范围也不封顶；{@code /export} 传
     * {@code Integer.MAX_VALUE} 调用本方法，等于"凭一个 companyId 就能整表拉走"。
     * 现按 data_scope 五级办结（退货单无部门/经办人列，故只到公司级，其余级别降级本公司）。</p>
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
        Page<SaleReturn> p =
                mapper.selectPage(new Page<>(pageNum, pageSize), q);
        fillApproverNames(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    // 内部工具

    /**
     * 历史行补齐：读取时把 approver 的工号批量换成真名。
     * <p>写入侧 {@link #currentUserName()} 只对"本次审批"生效；审批发生在升级前建的老单据
     * 仍存工号，列表「审核人」列因此直接显示数字。
     * 与应付域 {@code ApRequestService.fillDisplayNames} 同一读时解析规则，写入数据库值不动。</p>
     */
    private void fillApproverNames(List<SaleReturn> rows) {
        if (rows == null || rows.isEmpty()) return;
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (SaleReturn r : rows) {
            if (r.getApprover() != null && !r.getApprover().isBlank()) codes.add(r.getApprover().trim());
        }
        if (codes.isEmpty()) return;
        Map<String, String> display = new HashMap<>();
        for (SysUser u : sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getUserName, codes))) {
            if (u.getNickName() != null && !u.getNickName().isBlank()) {
                display.put(u.getUserName(), u.getNickName());
            }
        }
        if (display.isEmpty()) return;
        for (SaleReturn r : rows) {
            if (r.getApprover() != null) {
                r.setApprover(display.getOrDefault(r.getApprover().trim(), r.getApprover()));
            }
        }
    }

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
            // 单位由界面明细行带入（销售域无订单行模型可派生：so_order/so_order_line 没有数据），
            // 这里只做去空白归一，避免写入数据库成 ' ' 这种"看着有值其实没有"的脏数据。
            if (ln.getUom() != null) {
                String u = ln.getUom().trim();
                ln.setUom(u.isEmpty() ? null : u);
            }
            out.add(ln);
            no++;
        }
        return out;
    }

    /**
     * 客户代码必须在客户主数据（{@code crm_customer}）现有，名称一律以主数据为准回填。
     * <p>与采购侧同缺陷同修：原实现代码是纯输入框、名称手填 ⇒ 任意字符串可入库，
     * {@code customer_name} 落空串，列表「客户」列空白，下游对账/函证缺关键字段。
     * <p>原先只有审核收尾环节 {@link #postRefundToAr} 才做客户解析（解析不到就报"无法挂账进对账"），
     * 等于把校验推迟到审核那一刻——草稿期就该拦下来。
     */
    private void assertCustomerInMaster(SaleReturn r) {
        CrmCustomer cust = crmCustomerMapper.selectOne(new LambdaQueryWrapper<CrmCustomer>()
                .eq(CrmCustomer::getCustomerCode, r.getCustomerCode())
                .last("limit 1"));
        if (cust == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "客户代码 " + r.getCustomerCode() + " 不在客户主数据中：请先在【客户管理】建档，或改用在册代码");
        }
        r.setCustomerName(cust.getCustomerName());
    }

    /**
     * 按 company_id 补充 company_code/company_name（原两列恒 NULL，台账/导出只有 id 无从对号）。
     * 公司 id 不现有时如实报错，不静默留空。
     */
    private void fillCompanyCoords(SaleReturn r) {
        if (r.getCompanyId() == null) return;
        SysCompany c = sysCompanyMapper.selectById(r.getCompanyId());
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "所属公司不存在: " + r.getCompanyId());
        }
        r.setCompanyCode(c.getCompanyCode());
        r.setCompanyName(c.getCompanyName());
    }

    private void saveLines(Long returnId, List<SaleReturnLine> lines) {
        for (SaleReturnLine ln : lines) {
            ln.setReturnId(returnId);
            lineMapper.insert(ln);
        }
    }

    /** ＋税额＝Σ 明细行 amount×taxRate/(100+taxRate)（行金额为价税合计），create/update 双入口整算回写。 */
    private BigDecimal deriveTaxAmount(List<SaleReturnLine> lines) {
        BigDecimal tax = BigDecimal.ZERO;
        for (SaleReturnLine ln : lines) {
            if (ln.getAmount() == null || ln.getTaxRate() == null) continue;
            BigDecimal rate = ln.getTaxRate();
            if (rate.signum() <= 0) continue;
            tax = tax.add(ln.getAmount().multiply(rate)
                    .divide(new BigDecimal("100").add(rate), 2, RoundingMode.HALF_UP));
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
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

    // 下游环节（APPROVED 收尾按 return_mode 分流）

    /**
     * REFUND 退货退款 → 应收域（与采购侧 同一套 ReturnLegMapping 映射）：
     * 1）红字应收（贷项）：rc_receivable 新增负向行（amount 取负、sourceDocType=SALE_RETURN）；
     * 2）冲减：对该客户未清应收按到期日 FIFO 冲减 remain_amount（减为 0 置 DONE，规则同收款核销）；
     * 3）进对账：写入客户对账基础表 ar_statement 并置 check_status=UNCHECKED（待核对）。
     * EXCHANGE 不进入本方法。去重：同退货单已生成红字贷项行则跳过。
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
        // —— 2) 冲减对应未清应收（收款核销同一规则：客户 + remain>0 + 非 DONE + 到期日/ID 升序）
        BigDecimal rest = r.getAmount() == null ? BigDecimal.ZERO : r.getAmount();
        BigDecimal offset = BigDecimal.ZERO;
        List<RcReceivable> open = rcReceivableMapper.selectList(new LambdaQueryWrapper<RcReceivable>()
                .eq(RcReceivable::getCustomerId, cust.getId())
                // 同族：冲减候选限定在本退货单所属法人主体内。客户主数据全租户共享，
                // 不限主体就会拿本公司的一笔退货去冲他公司的应收（与收款环节同一形态的串账）。
                .eq(r.getCompanyId() != null, RcReceivable::getCompanyId, r.getCompanyId())
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
            Map<String, Object> auditBefore = new LinkedHashMap<>();
            auditBefore.put("remainAmount", rec.getRemainAmount());
            auditBefore.put("receivedAmount", rec.getReceivedAmount());
            auditBefore.put("status", rec.getStatus());
            Map<String, Object> auditAfter = new LinkedHashMap<>();
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
        // —— 红字应收出证（借 1122 应收账款／贷 6001 主营业务收入，金额取负＝红字冲销）。
        // 走应收族既有钩子 generateReceivableVoucher：它按 rec 的 companyId/币种/单号建头、
        // 分录金额直接取 amount（负数即红字），凭证四键落在派生应收单上 ⇒ 应收侧联查能取到这张证。
        // 出证失败不回滚退货审核（该环节是 REQUIRES_NEW，且退货本身已合规成立），但必须记录不得静默：
        // 写审计流水 VOUCHER_GEN_FAIL，单据追溯抽屉与运营巡检都能看到并事后补生成。
        try {
            Long voucherId = voucherService.generateReceivableVoucher(credit);
            log.info("[returns] 销售退货单 {} 红字应收 {} 已出证 voucherId={}", r.getBillNo(), credit.getBillNo(), voucherId);
        } catch (Exception vex) {
            log.error("[returns] 销售退货单 {} 红字应收 {} 出证失败（退货审核不回滚，已留痕待补生成）: {}",
                    r.getBillNo(), credit.getBillNo(), vex.getMessage(), vex);
            // afterJson 手拼（不引 ObjectMapper）：只放固定键，异常文本一律走 remark，避免转义把 JSON 拼坏
            try {
                auditTrailService.record("RECEIVABLE", credit.getId(), credit.getBillNo(), "op_voucher",
                        String.valueOf(credit.getId()), "VOUCHER_GEN_FAIL", null,
                        "{\"voucherGenerated\":false,\"sourceType\":\"RECEIVABLE\",\"sourceDocId\":" + credit.getId() + "}",
                        "销售退货红字应收自动生成凭证未出证：" + vex.getMessage());
            } catch (Exception tex) {
                log.warn("[returns] 红字应收 {} 出证失败留痕写入失败: {}", credit.getBillNo(), tex.getMessage());
            }
        }
        // —— 3) 客户对账基础表：写入红字对账行并置 check_status（UNCHECKED 待核对）
        ArStatement stmt = new ArStatement();
        stmt.setTenantId(TenantContext.getTenantId());
        stmt.setBillNo("ARB" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        stmt.setCompanyId(r.getCompanyId());
        stmt.setCustomerCode(r.getCustomerCode());
        stmt.setCustomerName(r.getCustomerName());
        stmt.setBillType("DOMESTIC");
        stmt.setCcy(credit.getCurrencyCode());
        BigDecimal tax = ReturnLegMapping.negate(r.getTaxAmount() == null ? BigDecimal.ZERO : r.getTaxAmount());
        // 表头 amount 是「价税合计」（数量×单价，与红字应收同值），税额是从中拆出来的一部分，
        // 不要再往合计上加一次。修前写 total=amount+tax ⇒ 对账行 -5,650 比红字应收 -5,000 多出一整个税额。
        // 本表按「amount＝不含税额、total＝价税合计」落数：total 仍等于 amount+tax（等式不破），
        // 但合计取的是这笔退货的钱，与应收侧红字一致。
        BigDecimal netAmount = amount.subtract(tax);
        stmt.setAmount(netAmount);
        stmt.setTaxAmount(tax);
        stmt.setTotalAmount(netAmount.add(tax));
        if ("CNY".equalsIgnoreCase(credit.getCurrencyCode())) {
            stmt.setRmbAmount(netAmount);
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
            if (u == null) return null;
            // 审核人列直接显示工号——真名在 nickName，
            // getUserName() 是登录账号；与应付域 ApRequestService、采购退货侧同一规则。
            String real = u.getNickName();
            return real != null && !real.isBlank() ? real : u.getUserName();
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
     * <p>{@code create_by} 为空（历史/外部导入数据）时无法判定归属，放行以免误伤历史数据。
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
