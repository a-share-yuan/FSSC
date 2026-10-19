// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.GlJournalEntry;
import com.fssc.glex.mapper.GlJournalEntryMapper;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 一般总账记账服务（gl_journal_entry）。四类记账挂接四条独立审批流：
 * ACCRUAL→GL_ACCRUAL、DEFER→GL_DEFER、MANUAL→GL_MANUAL_VOUCHER、OTHER_RECEIPT→GL_OTHER_RECEIPT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlJournalService {

    private final GlJournalEntryMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final com.fssc.framework.org.OrgGuard orgGuard;
    /** D-DEC-128：记账域冲销走凭证引擎的统一出口 */
    private final com.fssc.sharing.service.VoucherService voucherService;
    /** P4-3 审计扩面：冲销这类状态跃迁须留痕（与审批回写同口径） */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    /** 默认借贷科目（仅展示用，非外键约束；前端可用 md_subject 传入真实科目id）。 */
    private static final Long DEFAULT_DEBIT = 6602L;
    private static final Long DEFAULT_CREDIT = 2241L;

    public PageResult<GlJournalEntry> page(long pageNum, long pageSize, String period, String entryType, String keyword) {
        LambdaQueryWrapper<GlJournalEntry> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(period)) {
            qw.eq(GlJournalEntry::getPeriod, period);
        }
        if (StringUtils.hasText(entryType)) {
            qw.eq(GlJournalEntry::getEntryType, entryType);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(GlJournalEntry::getEntryNo, keyword).or().like(GlJournalEntry::getSummary, keyword)
                    .or().like(GlJournalEntry::getVoucherNo, keyword));
        }
        Long scopeC = orgGuard.enforceCompany();
        if (scopeC != null) {
            qw.eq(GlJournalEntry::getCompanyId, scopeC);
        }
        qw.orderByDesc(GlJournalEntry::getId);
        Page<GlJournalEntry> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 生成一般总账记账并置 APPROVING（不预造凭证号），随后按 entryType 发起对应审批流；
     * 审批办结才由 {@code GlBizAction} 置 POSTED 并回写凭证对象上的真实凭证号。
     */
    @Transactional(rollbackFor = Exception.class)
    public GlJournalEntry generate(String period, String entryType, String summary, BigDecimal amount,
                                   Long debitSubjectId, Long creditSubjectId) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        if (!StringUtils.hasText(entryType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "记账类型不能为空");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "金额必须大于0");
        }
        String bizType = resolveFlowKey(entryType);
        if (bizType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的记账类型: " + entryType);
        }
        Long tenantId = TenantContext.getTenantId();
        long seq = countByPeriod(period) + 1;
        GlJournalEntry e = new GlJournalEntry();
        e.setTenantId(tenantId);
        e.setPeriod(period);
        e.setEntryType(entryType);
        // 已存在未驳回的记账单（APPROVING/APPROVED/POSTED）时拒绝重复生成——防重复提单重复入账；
        // 被驳回(REJECTED)的单不占闸，可修正后重新生成。
        GlJournalEntry dup = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GlJournalEntry>()
                .eq(GlJournalEntry::getPeriod, period)
                .eq(GlJournalEntry::getEntryType, entryType)
                .in(GlJournalEntry::getStatus, "APPROVING", "APPROVED", "POSTED")
                .orderByDesc(GlJournalEntry::getId).last("LIMIT 1"));
        if (dup != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "期间 " + period + " 已存在「" + typeName(entryType)
                    + "」记账单 " + dup.getEntryNo() + "（状态 " + dup.getStatus() + "）——一个会计期间一笔（期间幂等），"
                    + "如需重新生成请先驳回原单");
        }
        e.setSummary(StringUtils.hasText(summary) ? summary : defaultSummary(entryType, period));
        e.setDebitSubjectId(debitSubjectId != null ? debitSubjectId : DEFAULT_DEBIT);
        e.setCreditSubjectId(creditSubjectId != null ? creditSubjectId : DEFAULT_CREDIT);
        e.setAmount(amount);
        e.setEntryNo("JRN" + period + String.format("%03d", seq));
        // D-DEC-291/292：发起≠记账。凭证号只能由办结时生成的凭证对象给出，此处不预造号、不置 POSTED。
        e.setStatus("APPROVING");
        if (e.getCompanyId() == null) {
            e.setCompanyId(orgGuard.currentCompany());
        }
        orgGuard.assertCompanyAllowed(e.getCompanyId());
        mapper.insert(e);
        // G1：一般总账记账即发起对应记账审批流（总账会计制单→共享主管复核→财务经理阈值审批）
        try {
            // ENG-02(20260921)：传入公司上下文变量，避免 COMPANY 范围角色（财务审核等）解析"无成员"卡死
            java.util.Map<String, Object> extraVars = new java.util.HashMap<>();
            extraVars.put("companyId", e.getCompanyId());
            wfEngineFacade.start(bizType, e.getId(), userId(),
                    typeName(entryType) + "-" + period + " " + e.getSummary(), amount, null, extraVars);
        } catch (Exception ex) {
            log.error("一般总账记账 {} 发起 {} 审批失败: {}", period, bizType, ex.getMessage(), ex);
            throw ex;  // ROOT-2: 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return e;
    }

    /**
     * D-DEC-128：一般总账记账（费用预提/待摊/手工凭证/其他收款）冲销出口。
     *
     * <p>此前记账域只有 list/generate 两个动作：预提单办结后凭证停在 DRAFT，
     * 而全系统唯一的冲销入口是凭证台账的「红字冲销」按钮，其前置条件是
     * {@code op_voucher.status==='POSTED'}——草稿凭证直接置灰（证据 12-28:75，本单凭证 id=24）。
     * 结果错账无路可走，只能留着或靠人直接改库。
     *
     * <p>口径：仅已入账（POSTED）的记账单可冲销；具体由凭证引擎分流——
     * 凭证已过账出红字反向凭证（借贷对调、等额、直接过账落总账），
     * 凭证仍是草稿则作废原凭证（账面本无此笔，红字反而造两张空凭证）。
     * 冲销后单据置 REVERSED，期间幂等闸（APPROVING/APPROVED/POSTED）随之放开，可重新生成正确的一笔。
     *
     * @param id     记账单主键
     * @param reason 冲销原因（必填，落凭证备注与审计留痕）
     * @return 冲销结果（action=REVERSE|VOID、原凭证与红字凭证号、单据号与状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public java.util.Map<String, Object> reverse(Long id, String reason) {
        GlJournalEntry e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "总账记账不存在: " + id);
        }
        orgGuard.assertVisible(e.getCompanyId(), null);
        if (!"POSTED".equals(e.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已记账（POSTED）的记账单可冲销，当前状态 " + e.getStatus()
                            + (e.getStatus() != null && "APPROVED".equals(e.getStatus())
                            ? "（该单办结未出证，账面本无此笔，无需冲销）" : ""));
        }
        String bizType = resolveFlowKey(e.getEntryType());
        if (bizType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的记账类型: " + e.getEntryType());
        }
        java.util.Map<String, Object> ret =
                new java.util.LinkedHashMap<>(voucherService.reverseBySource(bizType, id, userId(), reason));
        String oldStatus = e.getStatus();
        GlJournalEntry upd = new GlJournalEntry();
        upd.setId(id);
        upd.setStatus("REVERSED");
        mapper.updateById(upd);
        java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
        before.put("status", oldStatus);
        java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("status", upd.getStatus());
        after.put("reverse", ret.get("action"));
        after.put("voucherNo", ret.get("action").equals("REVERSE") ? ret.get("reverseVoucherNo") : ret.get("originVoucherNo"));
        auditTrailService.recordUpdate(bizType, id, e.getEntryNo(), "gl_journal_entry", String.valueOf(id),
                before, after, "记账冲销：" + reason);
        ret.put("entryNo", e.getEntryNo());
        ret.put("entryStatus", upd.getStatus());
        log.info("总账记账 {}（{}）冲销完成：{} 原因={}", e.getEntryNo(), bizType, ret.get("action"), reason);
        return ret;
    }

    /** 记账类型 → 流程 bizType */
    private String resolveFlowKey(String entryType) {
        return switch (entryType) {
            case "ACCRUAL" -> "GL_ACCRUAL";
            case "DEFER" -> "GL_DEFER";
            case "MANUAL" -> "GL_MANUAL_VOUCHER";
            case "OTHER_RECEIPT" -> "GL_OTHER_RECEIPT";
            // D-DEC-323（批次2 B-12）：盘点差异结转做账档——资产/存货盘点差异结转凭证统一走总账凭证载体
            case "CHECK_DIFF" -> "GL_CHECK_DIFF";
            default -> null;
        };
    }

    private String defaultSummary(String entryType, String period) {
        return switch (entryType) {
            case "ACCRUAL" -> "费用预提-" + period;
            case "DEFER" -> "费用待摊-" + period;
            case "MANUAL" -> "手工凭证-" + period;
            case "OTHER_RECEIPT" -> "其他收款核算-" + period;
            case "CHECK_DIFF" -> "盘点差异结转-" + period;
            default -> "一般总账记账-" + period;
        };
    }

    private String typeName(String entryType) {
        return switch (entryType) {
            case "ACCRUAL" -> "费用预提";
            case "DEFER" -> "费用待摊";
            case "MANUAL" -> "手工凭证";
            case "OTHER_RECEIPT" -> "其他收款";
            case "CHECK_DIFF" -> "盘点差异结转";
            default -> entryType;
        };
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    private long countByPeriod(String period) {
        return mapper.selectCount(new LambdaQueryWrapper<GlJournalEntry>().eq(GlJournalEntry::getPeriod, period));
    }
}