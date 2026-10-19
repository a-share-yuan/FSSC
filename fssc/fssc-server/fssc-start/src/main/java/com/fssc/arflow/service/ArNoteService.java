package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.arflow.entity.ArNote;
import com.fssc.arflow.mapper.ArNoteMapper;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 应收票据服务（蓝图票据管理 9.5，流程18/19/20，状态机4.4）：
 * 登记（纸质必须影像扫描）→ 审单入账 →（纸质移交出纳）→ 背书转让/汇票贴现/到期提示 → 托收到账 → 归档。
 * 贴现利息 = 票面额 × 年利率% × 贴现天数 / 360，贴现款与利息单独记账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArNoteService {

    private final ArNoteMapper mapper;
    private final com.fssc.system.service.PeriodLockService periodLockService;
    private final ArFlowVoucherService voucherService;
    /** R5-D-095：客户主数据解析（登记按客户名解析 customer_code，解析失败显式拦截） */
    private final CrmCustomerMapper crmCustomerMapper;

    /** P4-3 审计扩面：单据状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    /** 票据登记：生成服务请求语义的 REGISTERED 待审单；纸质票据强制影像扫描（流程19规则）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArNote register(ArNote n) {

        // 蓝图规则6.5：账期已被月结关闭时禁止录入当月业务
        periodLockService.assertNotLocked(null, java.time.YearMonth.now().toString(), "登记票据");
        if (!StringUtils.hasText(n.getNoteNo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据编号必填");
        }
        Long dup = mapper.selectCount(new LambdaQueryWrapper<ArNote>()
                .eq(ArNote::getNoteNo, n.getNoteNo()));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据编号 " + n.getNoteNo() + " 已登记，不可重复登记");
        }
        if (n.getAmount() == null || n.getAmount().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据金额必须大于0");
        }
        if (n.getDueDate() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "到期日必填");
        }
        if (n.getIssueDate() != null && n.getIssueDate().isAfter(n.getDueDate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "出票日不能晚于到期日");
        }
        boolean paper = ArNote.T_PAPER.equals(n.getNoteType());
        if (!paper && !ArNote.T_ELECTRONIC.equals(n.getNoteType())) {
            n.setNoteType(ArNote.T_ELECTRONIC);
        }
        if (paper && !StringUtils.hasText(n.getImageRef())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "纸质票据必须完成影像扫描上传（蓝图流程19）");
        }
        // R5-D-095：登记落 customer_code——按客户名解析客户主数据；解析失败显式拦截（不落悬空票据）
        if (StringUtils.hasText(n.getCustomerName())) {
            CrmCustomer cust = crmCustomerMapper.selectOne(new LambdaQueryWrapper<CrmCustomer>()
                    .eq(CrmCustomer::getCustomerName, n.getCustomerName()).last("LIMIT 1"));
            if (cust == null) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "客户档案不存在：" + n.getCustomerName() + "，请先在客户主数据建档后再登记票据");
            }
            n.setCustomerCode(cust.getCustomerCode());
        }
        n.setId(null);
        n.setTenantId(TenantContext.getTenantId());
        n.setStatus(ArNote.ST_REGISTERED);
        mapper.insert(n);
        log.info("[arflow] 票据 {} 登记待审单（{}票面 {}，到期 {}）", n.getNoteNo(),
                paper ? "纸质" : "电子", n.getAmount(), n.getDueDate());
        return n;
    }

    /** 审单入账：REGISTERED → IN_ACCOUNT，生成 借1121/贷1122 入账凭证。 */
    @Transactional(rollbackFor = Exception.class)
    public ArNote review(Long id) {
        ArNote n = require(id);
        if (!ArNote.ST_REGISTERED.equals(n.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据 " + n.getNoteNo() + " 当前状态不可审单入账");
        }
        String voucherNo = voucherService.generateNoteVoucher(n.getTenantId(), companyId(), n.getAmount(),
                n.getCustomerName(), n.getNoteNo(), n.getId());
        String auditOldStatus = n.getStatus();
        String auditOldVoucherNo = n.getVoucherNo();
        n.setVoucherNo(voucherNo);
        n.setStatus(ArNote.ST_IN_ACCOUNT);
        mapper.updateById(n);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("voucherNo", auditOldVoucherNo);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", n.getStatus());
        auditAfter.put("voucherNo", n.getVoucherNo());
        auditTrailService.recordUpdate("AR_NOTE", n.getId(), n.getNoteNo(), "ar_note",
                String.valueOf(n.getId()), auditBefore, auditAfter, "审单入账");
        return n;
    }

    /** 移交出纳保管（纸质票据，流程19规则）：IN_ACCOUNT → CUSTODY。 */
    @Transactional(rollbackFor = Exception.class)
    public ArNote handover(Long id, String keeper) {
        ArNote n = require(id);
        if (!ArNote.T_PAPER.equals(n.getNoteType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "电子票据无需移交出纳保管");
        }
        if (!ArNote.ST_IN_ACCOUNT.equals(n.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据 " + n.getNoteNo() + " 当前状态不可移交出纳");
        }
        String auditOldStatus = n.getStatus();
        String auditOldRemark = n.getRemark();
        if (StringUtils.hasText(keeper)) {
            n.setRemark(appendNote(n.getRemark(), "移交出纳：" + keeper));
        }
        n.setStatus(ArNote.ST_CUSTODY);
        mapper.updateById(n);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("remark", auditOldRemark);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", n.getStatus());
        auditAfter.put("remark", n.getRemark());
        auditTrailService.recordUpdate("AR_NOTE", n.getId(), n.getNoteNo(), "ar_note",
                String.valueOf(n.getId()), auditBefore, auditAfter, "移交出纳保管");
        return n;
    }

    /** 背书转让给银行（流程18）：IN_ACCOUNT/CUSTODY → ENDORSED。 */
    @Transactional(rollbackFor = Exception.class)
    public ArNote endorse(Long id, String transferee) {
        ArNote n = require(id);
        if (!ArNote.ST_IN_ACCOUNT.equals(n.getStatus()) && !ArNote.ST_CUSTODY.equals(n.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据 " + n.getNoteNo() + " 当前状态不可背书转让");
        }
        if (!StringUtils.hasText(transferee)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "被背书人必填");
        }
        String auditOldStatus = n.getStatus();
        String auditOldTransferee = n.getTransferee();
        n.setTransferee(transferee);
        n.setStatus(ArNote.ST_ENDORSED);
        mapper.updateById(n);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("transferee", auditOldTransferee);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", n.getStatus());
        auditAfter.put("transferee", n.getTransferee());
        auditTrailService.recordUpdate("AR_NOTE", n.getId(), n.getNoteNo(), "ar_note",
                String.valueOf(n.getId()), auditBefore, auditAfter, "背书转让");
        return n;
    }

    /**
     * 汇票贴现（流程20）：IN_ACCOUNT/CUSTODY → DISCOUNTED。
     * 贴现利息=票面额×年利率%×贴现天数/360；净额+利息单独记账（借1002+借660302/贷1121）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArNote discount(Long id, BigDecimal annualRatePct, LocalDate discountDate) {
        ArNote n = require(id);
        if (!ArNote.ST_IN_ACCOUNT.equals(n.getStatus()) && !ArNote.ST_CUSTODY.equals(n.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据 " + n.getNoteNo() + " 当前状态不可贴现");
        }
        if (annualRatePct == null || annualRatePct.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "贴现利率（年%）必须大于0");
        }
        LocalDate dDate = discountDate == null ? LocalDate.now() : discountDate;
        if (!dDate.isAfter(LocalDate.now().minusDays(1))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "贴现日期不能早于今天");
        }
        if (!dDate.isBefore(n.getDueDate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "贴现日期必须早于票据到期日");
        }
        int days = (int) ChronoUnit.DAYS.between(dDate, n.getDueDate());
        BigDecimal interest = n.getAmount()
                .multiply(annualRatePct).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(360), 2, RoundingMode.HALF_UP);
        BigDecimal net = n.getAmount().subtract(interest);
        String voucherNo = voucherService.generateNoteDiscountVoucher(n.getTenantId(), companyId(),
                n.getAmount(), net, interest, n.getCustomerName(), n.getNoteNo(), n.getId());
        String auditOldStatus = n.getStatus();
        BigDecimal auditOldDiscountAmount = n.getDiscountAmount();
        BigDecimal auditOldDiscountInterest = n.getDiscountInterest();
        String auditOldDiscountVoucherNo = n.getDiscountVoucherNo();
        n.setDiscountRate(annualRatePct);
        n.setDiscountDate(dDate);
        n.setDiscountDays(days);
        n.setDiscountInterest(interest);
        n.setDiscountAmount(net);
        n.setDiscountVoucherNo(voucherNo);
        n.setStatus(ArNote.ST_DISCOUNTED);
        mapper.updateById(n);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("discountAmount", auditOldDiscountAmount);
        auditBefore.put("discountInterest", auditOldDiscountInterest);
        auditBefore.put("discountVoucherNo", auditOldDiscountVoucherNo);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", n.getStatus());
        auditAfter.put("discountAmount", n.getDiscountAmount());
        auditAfter.put("discountInterest", n.getDiscountInterest());
        auditAfter.put("discountVoucherNo", n.getDiscountVoucherNo());
        auditTrailService.recordUpdate("AR_NOTE", n.getId(), n.getNoteNo(), "ar_note",
                String.valueOf(n.getId()), auditBefore, auditAfter, "汇票贴现");
        log.info("[arflow] 票据 {} 贴现：天数 {} 利息 {} 净额 {}", n.getNoteNo(), days, interest, net);
        return n;
    }

    /** 到期提示：IN_ACCOUNT/CUSTODY 且到期日已到 → COLLECTING 待托收。 */
    @Transactional(rollbackFor = Exception.class)
    public ArNote markDue(Long id) {
        ArNote n = require(id);
        if (!ArNote.ST_IN_ACCOUNT.equals(n.getStatus()) && !ArNote.ST_CUSTODY.equals(n.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据 " + n.getNoteNo() + " 当前状态不可做到期提示");
        }
        String auditOldStatus = n.getStatus();
        n.setStatus(ArNote.ST_COLLECTING);
        mapper.updateById(n);
        auditTrailService.recordUpdate("AR_NOTE", n.getId(), n.getNoteNo(), "ar_note",
                String.valueOf(n.getId()), java.util.Collections.singletonMap("status", auditOldStatus),
                java.util.Collections.singletonMap("status", n.getStatus()), "到期提示转待托收");
        return n;
    }

    /** 托收到账：COLLECTING → COMPLETED，生成 借1002/贷1121 入账凭证。 */
    @Transactional(rollbackFor = Exception.class)
    public ArNote collect(Long id) {
        ArNote n = require(id);
        if (!ArNote.ST_COLLECTING.equals(n.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据 " + n.getNoteNo() + " 未进入待托收状态");
        }
        String voucherNo = voucherService.generateNoteCollectVoucher(n.getTenantId(), companyId(),
                n.getAmount(), n.getCustomerName(), n.getNoteNo(), n.getId());
        String auditOldStatus = n.getStatus();
        String auditOldCollectVoucherNo = n.getCollectVoucherNo();
        n.setCollectVoucherNo(voucherNo);
        n.setStatus(ArNote.ST_COMPLETED);
        mapper.updateById(n);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("collectVoucherNo", auditOldCollectVoucherNo);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", n.getStatus());
        auditAfter.put("collectVoucherNo", n.getCollectVoucherNo());
        auditTrailService.recordUpdate("AR_NOTE", n.getId(), n.getNoteNo(), "ar_note",
                String.valueOf(n.getId()), auditBefore, auditAfter, "托收到账");
        return n;
    }

    /** 作废（仅待审单可作废）。 */
    @Transactional(rollbackFor = Exception.class)
    public ArNote voidNote(Long id, String reason) {
        ArNote n = require(id);
        if (!ArNote.ST_REGISTERED.equals(n.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅待审单票据可作废，当前 " + n.getStatus());
        }
        // 作废原因写进备注供追索/审计复盘，留空的作废无从解释
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作废原因必填");
        }
        String auditOldStatus = n.getStatus();
        String auditOldRemark = n.getRemark();
        n.setStatus(ArNote.ST_VOIDED);
        n.setRemark(appendNote(n.getRemark(), "作废原因：" + (reason == null ? "" : reason)));
        mapper.updateById(n);
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        auditBefore.put("status", auditOldStatus);
        auditBefore.put("remark", auditOldRemark);
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("status", n.getStatus());
        auditAfter.put("remark", n.getRemark());
        auditTrailService.recordUpdate("AR_NOTE", n.getId(), n.getNoteNo(), "ar_note",
                String.valueOf(n.getId()), auditBefore, auditAfter, "票据作废");
        return n;
    }

    /** 查询（票据查询页 9.1 序号14）：状态/类型/编号/出票人/客户 组合过滤。 */
    public List<ArNote> list(String status, String noteType, String keyword) {
        LambdaQueryWrapper<ArNote> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(ArNote::getStatus, status);
        }
        if (noteType != null && !noteType.isBlank()) {
            q.eq(ArNote::getNoteType, noteType);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(ArNote::getNoteNo, keyword)
                    .or().like(ArNote::getDrawer, keyword)
                    .or().like(ArNote::getCustomerName, keyword));
        }
        return mapper.selectList(q.orderByDesc(ArNote::getId));
    }

    /** 到期未托收清单（到期提示工作项：已入账/已保管 且到期日≤今天）。 */
    public List<ArNote> dueList() {
        return mapper.selectList(new LambdaQueryWrapper<ArNote>()
                .in(ArNote::getStatus, ArNote.ST_IN_ACCOUNT, ArNote.ST_CUSTODY)
                .le(ArNote::getDueDate, LocalDate.now())
                .orderByAsc(ArNote::getDueDate));
    }

    private ArNote require(Long id) {
        ArNote n = mapper.selectById(id);
        if (n == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "票据不存在 id=" + id);
        }
        return n;
    }

    private Long companyId() {
        return 1L;
    }

    private String appendNote(String origin, String add) {
        String base = origin == null ? "" : origin;
        return base.isBlank() ? add : base + "；" + add;
    }
}
