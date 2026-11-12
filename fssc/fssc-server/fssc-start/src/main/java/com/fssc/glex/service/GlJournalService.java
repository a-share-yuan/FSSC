// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.audit.service.AuditTrailService;
import com.fssc.budget.service.BudgetService;
import com.fssc.budget.service.BudgetSwitchService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.glex.entity.GlJournalEntry;
import com.fssc.glex.mapper.GlJournalEntryMapper;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import com.fssc.masterdata.mapper.MdSubjectMapper;
import com.fssc.masterdata.service.PostableSubjectResolver;
import com.fssc.sharing.service.VoucherService;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
    /** 按科目编码解析真实主键，并校验传入科目存在性（默认科目不再是幽灵 ID）。 */
    private final MdSubjectMapper subjectMapper;
    /** 借方科目缺省时的第二来源：费用项目映射 */
    private final MdExpenseItemMapper expenseItemMapper;
    /** 科目末级落地（含唯一末级自动下沉、多末级父科目拒绝） */
    private final PostableSubjectResolver postableSubjectResolver;
    private final WfEngineFacade wfEngineFacade;
    private final OrgGuard orgGuard;
    /** 记账域冲销走凭证引擎的统一出口 */
    private final VoucherService voucherService;
    /** 审计扩面：冲销这类状态跃迁须记录（与审批回写同一规则） */
    private final AuditTrailService auditTrailService;
    /** 预提/待摊的预算「申请占用」与「预算冲回」入口 */
    private final BudgetService budgetService;
    /** 口径开关：预提/待摊是否入占用由租户自行配置（缺省开） */
    private final BudgetSwitchService budgetSwitchService;

    /**
     * 默认借贷科目的<b>科目编码</b>（不是主键！）。
     *
     * <p>把 6602/2241 当主键写死（{@code DEFAULT_DEBIT=6602L}、{@code DEFAULT_CREDIT=2241L}）会错：
     * 而 md_subject 里 6602 的主键是 919、1002 是 967，<b>2241 更是查无此行</b>（幽灵 ID）。
     * 结果未传科目的手工凭证会落成「借 6602 管理费用 / 贷 2241 其他应付款」——账实不符。
     * 注释写「仅展示用，非外键约束」与实现不符：凭证引擎直接拿这两个值生成分录。
     *
     * <p>改为按编码查库解析真实主键，租户/公司换了 id 也不会错。
     * <p>借方不再有默认编码：6602 下设 30+ 明细，任何"猜一个"都会把费用记错明细，
     * 未指定时必须由费用项目映射提供，两者都没有即拒绝。
     */
    private static final String DEFAULT_CREDIT_CODE = "1002";
    /** 费用预提的默认贷方：计提负债（2241 其他应付款），不默认落银行存款——见 generate 内注释 */
    private static final String DEFAULT_ACCRUAL_CREDIT_CODE = "2241";

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
     * 会计期间归一化为 {@code yyyyMM}（底表列宽只有 6 位）。
     * <p>容忍 {@code yyyy-MM} 输入并去掉横杠；月份越界或格式非法时抛 400 业务异常，
     * 而不是让它一路走到 INSERT 变成「列长不足」的 HTTP 500。
     */
    private String normalizePeriod(String period) {
        String s = period.trim();
        if (s.matches("\\d{4}-\\d{2}")) {
            s = s.replace("-", "");
        }
        if (!s.matches("\\d{6}")) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "会计期间格式不正确：" + period + "（应为 yyyyMM 如 202610，或 yyyy-MM 如 2026-10）");
        }
        int mm = Integer.parseInt(s.substring(4));
        if (mm < 1 || mm > 12) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间月份非法：" + s.substring(4) + "（应在 01~12）");
        }
        return s;
    }

    /**
     * 把科目主键解析成「库里真实存在的那个」。
     * <p>{@code subjectId} 为空时按科目编码取默认科目（贷方 1002 银行存款）；
     * 非空时必须能在 md_subject 命中，否则 400 拒绝——避免幽灵 ID 静默写入数据库、直到生成凭证才暴露。
     */
    private Long resolveSubjectId(Long subjectId, String defaultCode, String side) {
        MdSubject s = subjectId != null ? subjectById(subjectId) : subjectByCode(defaultCode);
        if (s == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, subjectId != null
                    ? side + "科目不存在（id=" + subjectId + "），请从会计科目表中选择"
                    : side + "默认科目编码 " + defaultCode + " 在会计科目表中不存在，请显式指定科目");
        }
        // 末级校验前移到这里：制证时才由 PostableSubjectResolver 拒绝，后果是「审批全过、状态 APPROVED、
        // 一张凭证都出不来」——用户走完五六个节点才发现科目选错。制单即拦，代价一行，收益是即时可改。
        if (s.getIsLeaf() != null && s.getIsLeaf() == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    side + "科目 " + s.getSubjectCode() + " " + s.getSubjectName()
                            + " 不是末级科目（下设明细，无法确定用哪一个），无法制证；请在制单页选择末级明细科目");
        }
        return s.getId();
    }

    /**
     * 借方科目：显式科目 → 费用项目映射 → 报错。
     * <p>不再默认 6602：管理费用下设 30+ 明细，默认到父科目要么被末级校验拒绝、
     * 要么（放宽校验时）把费用记到错误明细，两种都不能接受。
     */
    private Long resolveDebitSubjectId(Long debitSubjectId, Long expenseItemId) {
        if (debitSubjectId != null) {
            return resolveSubjectId(debitSubjectId, null, "借方");
        }
        if (expenseItemId != null) {
            MdExpenseItem item = expenseItemMapper.selectById(expenseItemId);
            if (item != null && item.getSubjectId() != null) {
                return postableSubjectResolver.requireExpenseSubject(item, null, null).getId();
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                "借方会计科目未指定：请选择末级借方科目，或为所用费用项目配置对应会计科目（基础资料 › 费用项目）");
    }

    private MdSubject subjectById(Long id) {
        return subjectMapper.selectById(id);
    }

    private MdSubject subjectByCode(String code) {
        return subjectMapper.selectOne(new LambdaQueryWrapper<MdSubject>()
                .eq(MdSubject::getSubjectCode, code)
                .last("LIMIT 1"));
    }

    private String subjectName(Long id) {
        MdSubject s = subjectMapper.selectById(id);
        return s == null ? ("id=" + id) : (s.getSubjectCode() + " " + s.getSubjectName());
    }

    /**
     * 生成一般总账记账并置 APPROVING（不预造凭证号），随后按 entryType 发起对应审批流；
     * 审批办结才由 {@code GlBizAction} 置 POSTED 并回写凭证对象上的真实凭证号。
     *
     * <p>兼容旧调用：不指定预算维度（预提/待摊按映射表兜底定位预算行）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public GlJournalEntry generate(String period, String entryType, String summary, BigDecimal amount,
                                   Long debitSubjectId, Long creditSubjectId) {
        return generate(period, entryType, summary, amount, debitSubjectId, creditSubjectId, null, null, null);
    }

    /**
     * 生成一般总账记账并置 APPROVING（不预造凭证号），随后按 entryType 发起对应审批流；
     * 审批办结才由 {@code GlBizAction} 置 POSTED 并回写凭证对象上的真实凭证号。
     *
     * <p><b> 预提/待摊进预算闸口</b>（口径＝预算影响类型「申请占用」，见
     * {@link BudgetService#occupyAccrualBudget}）：</p>
     * <ul>
     *   <li>ACCRUAL（费用预提）/ DEFER（费用待摊）在<b>提交（本方法）</b>即按「申请占用」扣减预算，
     *       与费用域的提交时点一致；冲销/驳回/撤回按「预算冲回」释放；</li>
     *   <li>定位不到预算行时<b>不阻断预提、也不臆造预算行</b>：{@code budget_occupy_status=NONE}
     *       如实落库 + WARN 日志，避免「有预算却提示无预算」或反过来「没占上却看不出来」；</li>
     *   <li>超预算按方案档位（HARD 拒提 / SOFT 需 {@code overrunReason} / WARN 留痕放行）；</li>
     *   <li>MANUAL / OTHER_RECEIPT / CHECK_DIFF 不是预提类，不占预算（{@code budget_occupy_status} 留空）。</li>
     * </ul>
     *
     * <p><b>已知边界</b>：本版没有「预提 → 报销」下推关系，常规做法靠「单据下推回写/冲销源单字段」释放
     * 已下推部分；我方语义等价于「关闭释放」——预提占着，直到被冲销。因此<b>同一笔费用若先预提后报销
     * 且不冲销预提，会占用两次</b>。正确做法：报销前先冲销对应预提（本页「冲销」按钮即释放额度）。</p>
     *
     * @param expenseItemId  费用项目（md_expense_item.id），预提/待摊的预算维度来源，可空
     * @param deptId         承担部门（sys_dept.id），可空
     * @param overrunReason  软控方案下的超支理由，可空
     */
    @Transactional(rollbackFor = Exception.class)
    public GlJournalEntry generate(String period, String entryType, String summary, BigDecimal amount,
                                   Long debitSubjectId, Long creditSubjectId,
                                   Long expenseItemId, Long deptId, String overrunReason) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间不能为空");
        }
        // period 列是 varchar(6)=yyyyMM，传 yyyy-MM 会因列长不足触发 SQL 异常被包成 HTTP 500，
        // 调用方无从判断是格式问题（与 同源）。先归一化，非法格式显式 400 拒绝。
        period = normalizePeriod(period);
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
        // （期间去重："一个会计期间只能生成一张凭证"）：同期间同类型
        // 已存在未驳回的记账单（APPROVING/APPROVED/POSTED）时拒绝重复生成——防重复提单重复入账；
        // 被驳回(REJECTED)的单不占校验，可修正后重新生成。
        GlJournalEntry dup = mapper.selectOne(new LambdaQueryWrapper<GlJournalEntry>()
                .eq(GlJournalEntry::getPeriod, period)
                .eq(GlJournalEntry::getEntryType, entryType)
                .in(GlJournalEntry::getStatus, "APPROVING", "APPROVED", "POSTED")
                .orderByDesc(GlJournalEntry::getId).last("LIMIT 1"));
        if (dup != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "期间 " + period + " 已存在「" + typeName(entryType)
                    + "」记账单 " + dup.getEntryNo() + "（状态 " + dup.getStatus() + "）——一个会计期间只能有一笔，"
                    + "如需重新生成请先驳回原单");
        }
        e.setSummary(StringUtils.hasText(summary) ? summary : defaultSummary(entryType, period));
        e.setDebitSubjectId(resolveDebitSubjectId(debitSubjectId, expenseItemId));
        // 预提的贷方是「计提出来的负债」，不是银行存款：默认落 1002 会把一笔尚未付款的预提
        // 记成已付款，账面现金凭空减少。故 ACCRUAL 默认贷 2241 其他应付款；待摊/手工/其他收款仍用 1002。
        String defCreditCode = "ACCRUAL".equals(entryType) ? DEFAULT_ACCRUAL_CREDIT_CODE : DEFAULT_CREDIT_CODE;
        e.setCreditSubjectId(resolveSubjectId(creditSubjectId, defCreditCode, "贷方"));
        if (e.getDebitSubjectId().equals(e.getCreditSubjectId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "借方与贷方科目不能相同（当前都是「"
                    + subjectName(e.getDebitSubjectId()) + "」），请选择两个不同的会计科目");
        }
        e.setAmount(amount);
        e.setEntryNo("JRN" + period + String.format("%03d", seq));
        // 292：发起≠记账。凭证号只能由办结时生成的凭证对象给出，此处不预造号、不置 POSTED。
        e.setStatus("APPROVING");
        if (e.getCompanyId() == null) {
            e.setCompanyId(orgGuard.currentCompany());
        }
        orgGuard.assertCompanyAllowed(e.getCompanyId());
        mapper.insert(e);
        // 预提/待摊「申请占用」预算。插库后才占用——占用流水 biz_id 需要记账单主键。
        // 若占用抛错（HARD 超预算 / 软控缺理由），本方法事务整体回滚，不会留下「已占额度但没有单据」的幽灵占用。
        if ("ACCRUAL".equals(entryType) || "DEFER".equals(entryType)) {
            e.setExpenseItemId(expenseItemId);
            e.setDeptId(deptId);
            // 口径开关（租户可配，缺省开）：关掉时只记维度不占额度，状态落 DISABLED，
            // 与「NONE＝该占却没找到预算行」区分开——一个是配置选择，一个是数据缺口。
            boolean occupyOn = budgetSwitchService.accrualOccupyEnabled();
            Long occupyItem = null;
            if (occupyOn) {
                Integer bizYear = Integer.valueOf(period.substring(0, 4));
                occupyItem = budgetService.occupyAccrualBudget(bizType, e.getId(), e.getEntryNo(),
                        expenseItemId, e.getCompanyId(), deptId, bizYear, amount, overrunReason);
            } else {
                log.info("预提/待摊 {} 未占预算：租户已关闭「预提/待摊占用预算」开关（{}）",
                        e.getEntryNo(), BudgetSwitchService.KEY_ACCRUAL_OCCUPY);
            }
            e.setBudgetItemId(occupyItem);
            e.setBudgetOccupyStatus(!occupyOn ? "DISABLED" : (occupyItem == null ? "NONE" : "OCCUPIED"));
            GlJournalEntry budgetUpd = new GlJournalEntry();
            budgetUpd.setId(e.getId());
            budgetUpd.setExpenseItemId(expenseItemId);
            budgetUpd.setDeptId(deptId);
            budgetUpd.setBudgetItemId(occupyItem);
            budgetUpd.setBudgetOccupyStatus(e.getBudgetOccupyStatus());
            mapper.updateById(budgetUpd);
        }
        // G1：一般总账记账即发起对应记账审批流（总账会计制单→共享主管复核→财务经理阈值审批）
        try {
            // ENG-02(20260921)：传入公司上下文变量，避免 COMPANY 范围角色（财务审核等）解析"无成员"卡死
            Map<String, Object> extraVars = new HashMap<>();
            extraVars.put("companyId", e.getCompanyId());
            wfEngineFacade.start(bizType, e.getId(), userId(),
                    typeName(entryType) + "-" + period + " " + e.getSummary(), amount, null, extraVars);
        } catch (Exception ex) {
            log.error("一般总账记账 {} 发起 {} 审批失败: {}", period, bizType, ex.getMessage(), ex);
            throw ex;  // 流程发起失败必须冒泡，不得静默降级成无流程孤儿单
        }
        return e;
    }

    /**
     * 一般总账记账（费用预提/待摊/手工凭证/其他收款）冲销出口。
     *
     * <p>记账域只有 list/generate 两个动作时：预提单办结后凭证停在 DRAFT，
     * 而全系统唯一的冲销入口是凭证台账的「红字冲销」按钮，其前置条件是
     * {@code op_voucher.status==='POSTED'}——草稿凭证直接置灰。
     * 结果错账无路可走，只能留着或靠人直接改库。
     *
     * <p>规则：仅已入账（POSTED）的记账单可冲销；具体由凭证引擎分流——
     * 凭证已过账出红字反向凭证（借贷对调、等额、直接过账落总账），
     * 凭证仍是草稿则作废原凭证（账面本无此笔，红字反而造两张空凭证）。
     * 冲销后单据置 REVERSED，期间去重校验（APPROVING/APPROVED/POSTED）随之放开，可重新生成正确的一笔。
     *
     * @param id 记账单主键
     * @param reason 冲销原因（必填，落凭证备注与审计记录）
     * @return 冲销结果（action=REVERSE|VOID、原凭证与红字凭证号、单据号与状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reverse(Long id, String reason) {
        GlJournalEntry e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "总账记账不存在: " + id);
        }
        orgGuard.assertVisible(e.getCompanyId(), null);
        if (!"POSTED".equals(e.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅已记账的记账单可冲销，当前状态不允许该操作"
                            + (e.getStatus() != null && "APPROVED".equals(e.getStatus())
                            ? "（该单办结未出证，账面本无此笔，无需冲销）" : ""));
        }
        String bizType = resolveFlowKey(e.getEntryType());
        if (bizType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的记账类型: " + e.getEntryType());
        }
        Map<String, Object> ret =
                new LinkedHashMap<>(voucherService.reverseBySource(bizType, id, userId(), reason));
        String oldStatus = e.getStatus();
        GlJournalEntry upd = new GlJournalEntry();
        upd.setId(id);
        upd.setStatus("REVERSED");
        // 冲销＝「预算冲回」——预提/待摊占的额度随冲销一起放回可申请池。
        // 与状态回写同事务：不允许「凭证已红字反向、预算还占着」。
        if (("ACCRUAL".equals(e.getEntryType()) || "DEFER".equals(e.getEntryType()))
                && !"RELEASED".equals(e.getBudgetOccupyStatus())) {
            int released = budgetService.releaseAccrualOccupy(bizType, id, e.getEntryNo());
            if (released > 0 || "OCCUPIED".equals(e.getBudgetOccupyStatus())) {
                upd.setBudgetOccupyStatus("RELEASED");
            }
        }
        mapper.updateById(upd);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", oldStatus);
        Map<String, Object> after = new LinkedHashMap<>();
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
            // 盘点差异结转做账档——资产/存货盘点差异结转凭证统一走总账凭证载体
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
