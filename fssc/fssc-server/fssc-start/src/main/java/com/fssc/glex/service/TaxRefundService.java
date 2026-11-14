package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.TaxRefund;
import com.fssc.glex.mapper.TaxRefundMapper;
import com.fssc.invoice.entity.InvInvoice;
import com.fssc.framework.facade.InvoiceDomainFacade;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 税务退税三场景（tax_refund）：
 * <ul>
 *   <li>EXPORT_REFUND 出口免抵退：可退税额 = 出口销售额 × 免抵退率 / 100（税目下不重复退）。</li>
 *   <li>CREDIT_REFUND 留抵退税：申请退税额 ≤ 认证进项可留抵余额 avg_quota（Σ inv_invoice.deduct_amount 已认证 − 已退）。</li>
 *   <li>IMMEDIATE_REFUND 即征即退：可退税额 = 已缴增值税 × 即征即退比例 / 100。</li>
 * </ul>
 * 状态：DRAFT → APPLYING → APPROVED → REFUNDING → REFUNDED（驳回 REJECTED）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxRefundService {

    private static final String NO_PREFIX = "TRF";
    private static final Set<String> TYPES = Set.of("EXPORT_REFUND", "CREDIT_REFUND", "IMMEDIATE_REFUND");

    private final TaxRefundMapper mapper;
    private final InvoiceDomainFacade invoiceFacade;
    private final WfEngineFacade wfEngineFacade;
    private final OrgGuard orgGuard;
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    public PageResult<TaxRefund> page(long pageNum, long pageSize, String refundType, String status, String period, String keyword) {
        LambdaQueryWrapper<TaxRefund> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(refundType)) {
            qw.eq(TaxRefund::getRefundType, refundType);
        }
        if (StringUtils.hasText(status)) {
            qw.eq(TaxRefund::getStatus, status);
        }
        if (StringUtils.hasText(period)) {
            qw.eq(TaxRefund::getPeriod, period);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(TaxRefund::getRefundNo, keyword)
                    .or().eq(TaxRefund::getPeriod, keyword));
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(TaxRefund::getCompanyId, scopeC);
        }
        qw.orderByDesc(TaxRefund::getId);
        Page<TaxRefund> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 创建退税申请：按三场景差异化强校验后落 DRAFT，并生成退税单号。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(TaxRefund t) {
        if (t.getRefundType() == null || !TYPES.contains(t.getRefundType())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "退税类型必须为 EXPORT_REFUND/CREDIT_REFUND/IMMEDIATE_REFUND");
        }
        if (!StringUtils.hasText(t.getPeriod())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "所属期(YYYYMM)不能为空");
        }
        t.setId(null);
        t.setTenantId(TenantContext.getTenantId());
        if (t.getCompanyId() == null) {
            t.setCompanyId(orgGuard.currentCompany());
        }
        // 公司维度必填：退税主体决定进哪本账，集团岗未挂公司时无法默认推断，必须由建单人显式选择。
        if (t.getCompanyId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公司不能为空（请选择退税主体公司）");
        }
        orgGuard.assertCompanyAllowed(t.getCompanyId());
        t.setRefundNo(genNo());
        t.setStatus("DRAFT");
        t.setRefundedAmount(t.getRefundedAmount() == null ? BigDecimal.ZERO : t.getRefundedAmount());
        // 三场景差异化金额校验与（容错）自动算税
        t.setTaxAmount(differ(t));
        // 留抵退税：锁定可退上限
        if ("CREDIT_REFUND".equals(t.getRefundType())) {
            BigDecimal quota = creditQuota();
            if (t.getAvgQuota() == null || t.getAvgQuota().compareTo(quota) > 0) {
                t.setAvgQuota(quota);
            }
        }
        mapper.insert(t);
        return t.getId();
    }

    /** 提交退税申请：DRAFT → APPLYING，并发起税务退税审批流（GL_TAX_REFUND）。 */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        TaxRefund t = require(id);
        orgGuard.assertVisible(t.getCompanyId(), t.getApplyBy() != null ? t.getApplyBy() : t.getCreateBy());
        if (!"DRAFT".equals(t.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿可提交，当前状态不允许该操作");
        }
        TaxRefund u = new TaxRefund();
        u.setId(id);
        u.setStatus("APPLYING");
        u.setApplyBy(userId());
        u.setApplyTime(LocalDateTime.now());
        mapper.updateById(u);
        try {
            // 申请额=可退税额 taxAmount（GL_TAX_REFUND 当前无金额网关 ⇒ 只影响实例金额展示/统计，不改路由）
            wfEngineFacade.start("GL_TAX_REFUND", id, userId(),
                    "税务退税-" + t.getRefundType() + "-" + t.getPeriod(), t.getTaxAmount());
        } catch (Exception ex) {
            log.error("退税单 {} 发起审批失败: {}", t.getRefundNo(), ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须抛出去，不能静默降级成没有流程的孤儿单
        }
        // 审计记录：状态跃迁 DRAFT → APPLYING，并记录申请人与申请时间
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", t.getStatus());
        auditBefore.put("applyBy", t.getApplyBy());
        auditBefore.put("applyTime", t.getApplyTime());
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", "APPLYING");
        auditAfter.put("applyBy", u.getApplyBy());
        auditAfter.put("applyTime", u.getApplyTime());
        auditTrailService.recordUpdate("TAX_REFUND", id, t.getRefundNo(), "tax_refund",
                String.valueOf(id), auditBefore, auditAfter, "提交退税申请");
    }

    /** 状态流转：仅同租户、遵循合法状态迁移；到账(REFUNDED)须登记实际到账金额，最终状态不可再变更。 */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String to, String remark, BigDecimal refundedAmount) {
        TaxRefund t = require(id);
        orgGuard.assertVisible(t.getCompanyId(), t.getApplyBy() != null ? t.getApplyBy() : t.getCreateBy());
        if ("REFUNDED".equals(t.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退税单已到账，已结束，不可再变更");
        }
        if (!StringUtils.hasText(to)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择目标状态");
        }
        if (!allowed(t.getStatus(), to)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "状态不允许从 " + t.getStatus() + " 流转到 " + to);
        }
        // 过审闸：退税中/已退税只能从「已审批」起跳。此前只校验 FLOW_FORWARD 的先后次序，
        // 于是「审批中(APPLYING)」可一步跳到「已退税(REFUNDED)」，把整条审批链绕过，
        // 且流程实例还挂在首节不终止。
        if (("REFUNDING".equals(to) || "REFUNDED".equals(to)) && !"APPROVED".equals(t.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "退税单 " + t.getRefundNo() + " 当前为 " + t.getStatus()
                            + "，必须先完成审批（→已审批）才能流转到「退税中／已退税」");
        }
        TaxRefund u = new TaxRefund();
        u.setId(id);
        u.setStatus(to);
        u.setRemark(StringUtils.hasText(remark) ? remark : t.getRemark());
        if ("REFUNDED".equals(to)) {
            BigDecimal tax = t.getTaxAmount() == null ? BigDecimal.ZERO : t.getTaxAmount();
            if (refundedAmount == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "退税到账必须登记实际到账金额");
            }
            if (refundedAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "实际到账金额不能为负");
            }
            if (refundedAmount.compareTo(tax) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "实际到账金额 " + refundedAmount + " 不能超过可退税额 " + tax);
            }
            u.setRefundedAmount(refundedAmount);
        }
        mapper.updateById(u);
        // 审计记录：状态跃迁（到账时并记实际到账金额变化）
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        auditBefore.put("status", t.getStatus());
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("status", to);
        if ("REFUNDED".equals(to)) {
            auditBefore.put("refundedAmount", t.getRefundedAmount());
            auditAfter.put("refundedAmount", refundedAmount);
        }
        auditTrailService.recordUpdate("TAX_REFUND", id, t.getRefundNo(), "tax_refund",
                String.valueOf(id), auditBefore, auditAfter, "退税状态流转（" + t.getStatus() + "→" + to + "）");
    }

    // 三场景金额强校验

    private BigDecimal differ(TaxRefund t) {
        return switch (t.getRefundType()) {
            case "EXPORT_REFUND" -> exportTax(t);
            case "CREDIT_REFUND" -> creditRefund(t);
            case "IMMEDIATE_REFUND" -> immediateTax(t);
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知退税类型");
        };
    }

    /** 出口免抵退：要求 出口销售额>0、免抵退率>0；tax = 出口销售额 × 免抵退率 / 100 */
    private BigDecimal exportTax(TaxRefund t) {
        if (t.getBaseAmount() == null || t.getBaseAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "出口销售额(计税基数)必须大于 0");
        }
        if (t.getRate() == null || t.getRate().compareTo(BigDecimal.ZERO) <= 0 || t.getRate().compareTo(new BigDecimal("100")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "免抵退率须在 (0,100] 区间");
        }
        return percent(t.getBaseAmount(), t.getRate());
    }

    /** 留抵退税：要求 留抵额度>0；tax>0 且 ≤ 认证进项可留抵余额（超限抛业务异常） */
    private BigDecimal creditRefund(TaxRefund t) {
        BigDecimal quota = creditQuota();
        if (quota.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "留抵退税：当前无可退留抵额度(留抵上限须 > 0)");
        }
        if (t.getTaxAmount() == null || t.getTaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "留抵退税申请税额必须 > 0");
        }
        if (t.getTaxAmount().compareTo(quota) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "申请退税额 " + t.getTaxAmount() + " 超过可退留抵余额 " + quota);
        }
        return t.getTaxAmount();
    }

    /** 即征即退：要求 已缴增值税>0、比例>0；校验 tax = 基数 × 比例 / 100（容差 0.01） */
    private BigDecimal immediateTax(TaxRefund t) {
        if (t.getBaseAmount() == null || t.getBaseAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已缴增值税(计税基数)必须大于 0");
        }
        if (t.getRate() == null || t.getRate().compareTo(BigDecimal.ZERO) <= 0 || t.getRate().compareTo(new BigDecimal("100")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "即征即退比例须在 (0,100] 区间");
        }
        BigDecimal expected = percent(t.getBaseAmount(), t.getRate());
        BigDecimal req = t.getTaxAmount();
        if (req != null && req.compareTo(BigDecimal.ZERO) > 0
                && req.subtract(expected).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "即征即退税额校验失败：应为 已缴增值税 × 比例 / 100 = " + expected);
        }
        return expected;
    }

    /** 计税基数 × 税率 / 100，保留 2 位。 */
    private static BigDecimal percent(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate)
                .divide(new BigDecimal("100"), MathContext.DECIMAL128)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 可退留抵余额 = Σ 认证可抵扣进项税额(deduct_amount, CERTIFIED) − Σ 已审批留抵退税(非 DRAFT/REJECTED)。
     * 演示规则：以已认证专票可抵税额为上限。
     */
    public BigDecimal creditQuota() {
        BigDecimal certified = invoiceFacade.findByCertifyStatus("CERTIFIED")
                .stream().map(inv -> inv.getDeductAmount() == null ? BigDecimal.ZERO : inv.getDeductAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = mapper.selectList(new LambdaQueryWrapper<TaxRefund>()
                        .eq(TaxRefund::getRefundType, "CREDIT_REFUND")
                        .notIn(TaxRefund::getStatus, "DRAFT", "REJECTED"))
                .stream().map(r -> r.getTaxAmount() == null ? BigDecimal.ZERO : r.getTaxAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return certified.subtract(refunded).max(BigDecimal.ZERO);
    }

    // 辅助

    /** 正向流转顺序：DRAFT → APPLYING → APPROVED → REFUNDING → REFUNDED */
    private static final List<String> FLOW_FORWARD = List.of(
            "DRAFT", "APPLYING", "APPROVED", "REFUNDING", "REFUNDED");

    private boolean allowed(String from, String to) {
        if ("REJECTED".equals(to)) {
            return !"REFUNDED".equals(from); // 已到账后不可再驳回
        }
        return FLOW_FORWARD.contains(from) && FLOW_FORWARD.contains(to)
                && FLOW_FORWARD.indexOf(to) > FLOW_FORWARD.indexOf(from);
    }

    private TaxRefund require(Long id) {
        TaxRefund t = mapper.selectById(id);
        if (t == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "退税单不存在: " + id);
        }
        return t;
    }

    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    private String genNo() {
        long seq = mapper.selectCount(new LambdaQueryWrapper<TaxRefund>().isNotNull(TaxRefund::getId)) + 1;
        String base = NO_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", seq % 1000);
        // refundNo 租户内唯一保护：先 SELECT 查重，命中重复则追加序号，仍冲突则报错重试
        String no = base;
        int guard = 0;
        while (existsRefundNo(no)) {
            if (++guard > 20) {
                throw new BizException(ErrorCode.PARAM_INVALID, "退税单号生成冲突，请稍后重试");
            }
            no = base + "-" + guard;
        }
        return no;
    }

    /** 退税单号是否已存在（多租户拦截器自动加 tenant_id，故为租户内查重）。 */
    private boolean existsRefundNo(String refundNo) {
        if (!StringUtils.hasText(refundNo)) return false;
        Long c = mapper.selectCount(new LambdaQueryWrapper<TaxRefund>().eq(TaxRefund::getRefundNo, refundNo));
        return c != null && c > 0;
    }
}