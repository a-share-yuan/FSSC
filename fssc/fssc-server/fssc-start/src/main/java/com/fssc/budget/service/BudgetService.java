// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.budget.entity.BgAdjust;
import com.fssc.budget.entity.BgAdjustLine;
import com.fssc.budget.entity.BgItem;
import com.fssc.budget.entity.BgOccupy;
import com.fssc.budget.entity.BgScheme;
import com.fssc.budget.mapper.BgAdjustLineMapper;
import com.fssc.budget.mapper.BgAdjustMapper;
import com.fssc.budget.mapper.BgItemMapper;
import com.fssc.budget.mapper.BgOccupyMapper;
import com.fssc.budget.mapper.BgSchemeMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.org.OrgGuard;
import com.fssc.framework.security.DataScopeKit;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 预算服务。
 * <p>
 * 占用模型：可用额度 = total_amount + adjust_amount - occupy_amount - used_amount。
 * 流水（bg_occupy）只增不改，bg_item 的汇总值在同一事务内同步。
 * 控制策略（bg_scheme.control_type）：HARD 硬控（超预算拒绝）/ SOFT 软控（超预算需超支理由）/ WARN 预警（仅提示）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    /**
     * 费用/付款域的占用类型（其 biz_id 均为 exp_bill 主键）。
     * 核销归户时可在本集合内跨类型互认（申请占用 EXP_CLAIM → 报销核销 EXP_APPLY）；
     * 集合外（FA_CER、EXP_CONTRACT 等）的 biz_id 与 exp_bill 主键会撞号，必须各自独立归户。
     * 与 {@code BgOccupyMapper} 两处 SQL 的 IN 列表保持一致。
     */
    private static final java.util.Set<String> EXPENSE_BIZ_TYPES = java.util.Set.of(
            "EXP_CLAIM", "EXP_PAY", "EXP_APPLY", "EXP_LOAN", "EXP_REPAY", "TR_PAY");

    private final BgSchemeMapper bgSchemeMapper;
    private final BgItemMapper bgItemMapper;
    private final BgOccupyMapper bgOccupyMapper;
    private final BgAdjustMapper bgAdjustMapper;
    private final BgAdjustLineMapper bgAdjustLineMapper;
    /** 组织合法性守卫（V1.3.20 安全审计①）：读路径公司隔离 + 写路径公司归属校验 */
    private final OrgGuard orgGuard;
    private final com.fssc.system.service.NumberRuleService numberRuleService;
    /** 方案 company_id 默认处理解析（bg_scheme.company_id NOT NULL） */
    private final com.fssc.system.mapper.SysCompanyMapper sysCompanyMapper;
    /** 执行页名称解析（2026-09-17：itemName 不再显示"预算项#id"） */
    private final com.fssc.system.mapper.SysDeptMapper sysDeptMapper;
    private final com.fssc.masterdata.mapper.MdExpenseItemMapper mdExpenseItemMapper;
    /** 费用类型→预算明细映射（D-P2-01 主体化解析的默认处理来源） */
    private final com.fssc.masterdata.mapper.MdItemBudgetMapMapper mdItemBudgetMapMapper;
    /** 预算方案审批流发起（架构红线：业务域禁止直用 Flowable API，一律经门面） */
    private final com.fssc.workflow.service.WfEngineFacade wfEngineFacade;
    /** P4-3 审计扩面：单据状态跃迁记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    // ---------------- 方案与明细 ----------------

    @DataScope("预算方案列表数据范围")
    public PageResult<BgScheme> schemePage(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<BgScheme> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(BgScheme::getSchemeName, keyword).or().like(BgScheme::getSchemeCode, keyword));
        }
        // ③ data_scope 五级：依据登录人 data_scope 注入 公司/部门/本人 过滤（TENANT 不加条件）
        DataScopeKit.apply(qw, BgScheme::getCompanyId, null, null);
        qw.orderByDesc(BgScheme::getId);
        Page<BgScheme> p = bgSchemeMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 【修复 2026-09-14】预算方案保存 500 根因：
     * bg_scheme.company_id 为 NOT NULL 且无默认值，而前端保存未传 company_id，
     * insert 缺列直接抛 "Field 'company_id' doesn't have a default value"（S01/S08 复现一致）。
     * 修复：company_id 缺失时按当前登录人公司默认处理，再退租户首家公司；
     * scheme_code 缺失自动取号、budget_year 缺省当年、scheme_name 必填前置校验，
     * 消除其余会导致同接口 500 的 NOT NULL 缺列路径。
     */
    public Long schemeCreate(BgScheme scheme) {
        scheme.setId(null);
        if (scheme.getSchemeName() == null || scheme.getSchemeName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "方案名称必填");
        }
        assertSchemeCreateRequired(scheme);
        if (scheme.getCompanyId() == null) {
            scheme.setCompanyId(defaultCompanyId());
        }
        if (scheme.getSchemeCode() == null || scheme.getSchemeCode().isBlank()) {
            scheme.setSchemeCode(nextSchemeCode());
        }
        if (scheme.getStatus() == null) {
            scheme.setStatus("DRAFT");
        }
        bgSchemeMapper.insert(scheme);
        // P4-3 规则：新建写入数据库无旧值，不接入审计（不伪造 before），故此处刻意不写 audit_trail
        return scheme.getId();
    }

    /** 公司默认处理：登录人公司 → 租户首家公司（id 最小，与 VoucherService.resolveCompanyId 同一规则） */
    private Long defaultCompanyId() {
        com.fssc.framework.security.LoginUser u = UserContext.get();
        if (u != null && u.getCompanyId() != null) {
            return u.getCompanyId();
        }
        try {
            Long tenantId = TenantContext.getTenantId();
            com.fssc.system.entity.SysCompany c = sysCompanyMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.fssc.system.entity.SysCompany>()
                            .eq(com.fssc.system.entity.SysCompany::getTenantId, tenantId)
                            .orderByAsc(com.fssc.system.entity.SysCompany::getId)
                            .last("limit 1"));
            if (c != null && c.getId() != null) {
                return c.getId();
            }
        } catch (Exception ex) {
            log.warn("解析租户默认公司失败 tenantId={}: {}", TenantContext.getTenantId(), ex.getMessage());
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "无法确定方案所属公司，请选择公司后保存");
    }

    /** 方案编码取号：BG_SCHEME 规则优先，取号失败回退时间戳号，避免 NOT NULL 缺列 500 */
    private String nextSchemeCode() {
        try {
            return numberRuleService.next("BG_SCHEME");
        } catch (Exception e) {
            return "BGS" + System.currentTimeMillis();
        }
    }

    /** 预算维度：决定 bg_item 按哪些维度归集额度，空维度方案无法生成预算明细。 */
    private void assertSchemeDimension(BgScheme scheme) {
        if (scheme.getDimension() != null && scheme.getDimension().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算维度至少选择一项（部门/费用项/项目）");
        }
    }

    /** 预算年度：范围校验（防止 0/负数/未来离谱年份把额度挂到不存在的期间上）。 */
    private void assertSchemeYear(BgScheme scheme) {
        Integer y = scheme.getBudgetYear();
        if (y != null && (y < 2000 || y > 2100)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算年度应在 2000-2100 之间，当前: " + y);
        }
    }

    /**
     * 新建方案的必填守卫：与前端 BudgetScheme.vue 的红星同一规则——年度为空（null）也要拦。
     * 编辑走 {@link #assertSchemeYear}/{@link #assertSchemeDimension}（updateById 跳过 null，null＝不改），
     * 所以不能把这两处放宽，否则 API 直调能建出无年度、无维度的方案，预算明细永远挂不上去。
     */
    private void assertSchemeCreateRequired(BgScheme scheme) {
        if (scheme.getBudgetYear() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入预算年度");
        }
        if (scheme.getDimension() == null || scheme.getDimension().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请至少选择一个预算维度");
        }
        assertSchemeYear(scheme);
    }

    public void schemeUpdate(BgScheme scheme) {
        if (scheme.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "方案 ID 不能为空");
        }
        if (bgSchemeMapper.selectById(scheme.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算方案不存在: " + scheme.getId());
        }
        // updateById 跳过 null 字段，因此显式传空串＝用户清空，必须拒绝；name/dimension 与新建同一规则。
        if (scheme.getSchemeName() != null && scheme.getSchemeName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "方案名称必填");
        }
        assertSchemeDimension(scheme);
        assertSchemeYear(scheme);
        bgSchemeMapper.updateById(scheme);
    }

    public void schemeEnable(Long id, boolean enable) {
        // BUG-14 修复：启用必须基于已审批下达(ISSUED)的方案，堵住 DRAFT 直启绕过审批的口子
        BgScheme cur = bgSchemeMapper.selectById(id);
        if (cur == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "预算方案不存在: " + id);
        }
        BgScheme u = new BgScheme();
        u.setId(id);
        if (enable) {
            if (!"ISSUED".equals(cur.getStatus()) && !"APPROVED".equals(cur.getStatus())) {
                throw new com.fssc.common.exception.BizException(
                        com.fssc.common.core.ErrorCode.PARAM_INVALID,
                        "仅已审批下达(ISSUED)的方案可启用，当前状态: " + cur.getStatus() + "，请先提交审批");
            }
            u.setStatus("ACTIVE");
        } else {
            u.setStatus("ISSUED");
        }
        u.setUpdateBy(null);
        bgSchemeMapper.updateById(u);
        // P4-3 审计：状态跃迁（启用 ACTIVE / 停用回退 ISSUED）
        auditTrailService.recordUpdate("BUDGET", id, cur.getSchemeCode(), "bg_scheme",
                String.valueOf(id),
                java.util.Collections.singletonMap("status", cur.getStatus()),
                java.util.Collections.singletonMap("status", u.getStatus()),
                enable ? "启用预算方案" : "停用预算方案");
    }

    // ---------------- 预算方案审批流与下达（2026-09-14） ----------------

    /**
     * 提交审批：DRAFT → APPROVING，并启动预算方案审批流
     * （wf_business_flow: flow_code=BG_APPROVE / biz_type=BG_SCHEME / model_key=BG_APPROVE，
     * 两级节点：预算管理员初审 → 财务总监终审；启动时自动部署，见 WfEngineFacade.deployBizFlows）。
     * 流程结束由 {@link com.fssc.workflow.action.BudgetSchemeBizAction} 收尾：
     * 审批通过 → ISSUED（记录下达时间/人）；驳回/撤回 → 回退 DRAFT。
     * 发起失败回滚状态为 DRAFT 并抛业务异常，避免方案滞留"审批中"无人处理。
     */
    public void schemeSubmit(Long schemeId) {
        BgScheme scheme = bgSchemeMapper.selectById(schemeId);
        if (scheme == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算方案不存在: " + schemeId);
        }
        if (!"DRAFT".equals(scheme.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅草稿(DRAFT)方案可提交审批，当前状态: " + scheme.getStatus());
        }
        BgScheme upd = new BgScheme();
        upd.setId(schemeId);
        upd.setStatus("APPROVING");
        bgSchemeMapper.updateById(upd);
        // P4-3 审计：状态跃迁（草稿 → 审批中）
        auditTrailService.recordUpdate("BUDGET", schemeId, scheme.getSchemeCode(), "bg_scheme",
                String.valueOf(schemeId),
                java.util.Collections.singletonMap("status", scheme.getStatus()),
                java.util.Collections.singletonMap("status", "APPROVING"), "提交预算方案审批");
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
        try {
            wfEngineFacade.start("BG_SCHEME", schemeId, userId,
                    "预算方案审批-" + (scheme.getSchemeName() == null ? schemeId : scheme.getSchemeName()),
                    null);
        } catch (Exception ex) {
            // 发起失败回退 DRAFT（状态机不留悬挂态），错误上抛给前端
            BgScheme back = new BgScheme();
            back.setId(schemeId);
            back.setStatus("DRAFT");
            bgSchemeMapper.updateById(back);
            // P4-3 审计：状态回退（审批中 → 草稿，发起流程失败补偿）
            auditTrailService.recordUpdate("BUDGET", schemeId, scheme.getSchemeCode(), "bg_scheme",
                    String.valueOf(schemeId),
                    java.util.Collections.singletonMap("status", "APPROVING"),
                    java.util.Collections.singletonMap("status", "DRAFT"), "发起审批失败回退草稿");
            log.error("预算方案 {} 发起 BG_SCHEME 审批流失败: {}", schemeId, ex.getMessage(), ex);
            throw new BizException(ErrorCode.PARAM_INVALID, "发起预算方案审批流失败: " + ex.getMessage());
        }
        log.info("预算方案 {} 已提交审批（DRAFT → APPROVING）", scheme.getSchemeCode());
    }

    /**
     * 下达（ 内部方法）：置 status=ISSUED 并记录下达时间/人。
     * 规则：APPROVED（审批通过后下达，两段式主路径）或 DRAFT（免审直下达，兼容既有直接启用习惯）均可下达；
     * APPROVING（审批中，历史规则保留兼容）/ACTIVE（历史启用）亦放行；
     * ISSUED/CLOSED 拒绝重复下达。既有 ACTIVE 方案不受影响（保持有效）。
     */
    public void schemeIssue(Long schemeId) {
        BgScheme scheme = bgSchemeMapper.selectById(schemeId);
        if (scheme == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算方案不存在: " + schemeId);
        }
        String st = scheme.getStatus();
        if ("ISSUED".equals(st)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "方案 " + scheme.getSchemeCode() + " 已下达，请勿重复操作");
        }
        if ("CLOSED".equals(st)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "方案 " + scheme.getSchemeCode() + " 已关闭，不可下达");
        }
        if (!"APPROVING".equals(st) && !"DRAFT".equals(st) && !"ACTIVE".equals(st) && !"APPROVED".equals(st)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当前状态(" + st + ")不可下达");
        }
        BgScheme upd = new BgScheme();
        upd.setId(schemeId);
        upd.setStatus("ISSUED");
        upd.setIssueTime(LocalDateTime.now());
        upd.setIssueBy(UserContext.get() == null ? null : UserContext.get().getUserId());
        bgSchemeMapper.updateById(upd);
        // P4-3 审计：状态跃迁（→ 已下达 ISSUED）
        auditTrailService.recordUpdate("BUDGET", schemeId, scheme.getSchemeCode(), "bg_scheme",
                String.valueOf(schemeId),
                java.util.Collections.singletonMap("status", st),
                java.util.Collections.singletonMap("status", "ISSUED"), "预算方案下达");
        log.info("预算方案 {} 已下达（{} → ISSUED，下达人={}）",
                scheme.getSchemeCode(), st, upd.getIssueBy());
    }

    /** R5-D-266：存活方案 id 集合（bg_scheme 带 @TableLogic，selectList 自动过滤 del_flag=0），
     *  供执行读数与管理列表内连存活方案；孤儿行（scheme_id 不在此集）分别出只读清单交人工裁决。 */
    private java.util.List<Long> liveSchemeIds() {
        return bgSchemeMapper.selectList(new LambdaQueryWrapper<BgScheme>().select(BgScheme::getId))
                .stream().map(BgScheme::getId).collect(java.util.stream.Collectors.toList());
    }

    @DataScope("预算明细列表数据范围")
    public PageResult<BgItem> itemPage(long pageNum, long pageSize, Long schemeId, Long deptId, Long itemId,
                                       java.util.Collection<Long> ids) {
        // R5-D-266：内连存活方案——scheme_id 不在存活方案中的孤儿行不进管理列表（现网 4 行 scheme_id=100 孤儿）
        java.util.List<Long> liveSchemes = liveSchemeIds();
        if (liveSchemes.isEmpty()) {
            return PageResult.of(java.util.Collections.emptyList(), 0, pageNum, pageSize);
        }
        LambdaQueryWrapper<BgItem> qw = new LambdaQueryWrapper<>();
        qw.in(BgItem::getSchemeId, liveSchemes);
        if (schemeId != null) {
            qw.eq(BgItem::getSchemeId, schemeId);
        }
        if (deptId != null) {
            qw.eq(BgItem::getDeptId, deptId);
        }
        if (itemId != null) {
            qw.eq(BgItem::getItemId, itemId);
        }
        // D-BUD-1：按预算明细 id 精确查询，供单据页对"自动映射科目未在前 200 行预载内"的
        // 预算占用预览回填真实额度（否则 avail 误按 0 计 → 大额被误拦）。
        if (ids != null && !ids.isEmpty()) {
            qw.in(BgItem::getId, ids);
        }
        // ③ data_scope 五级：依据登录人 data_scope 注入 公司/部门/本人 过滤（TENANT 不加条件）
        // R5-D-262：部门档改走 applyWithNullDept——dept_id=NULL 的公司级下达行对同公司人员可见
        //（原 apply 的 dept_id=本人部门 规则把 8/9 行 NULL 部门行全部滤掉，BUDGET_MANAGER 面板恒 0 行）
        DataScopeKit.applyWithNullDept(qw, BgItem::getCompanyId, BgItem::getDeptId, null);
        qw.orderByAsc(BgItem::getDimKey);
        Page<BgItem> p = bgItemMapper.selectPage(Page.of(pageNum, pageSize), qw);
        // R5-D-264：执行率统一由后端单点计算填充（前端不再自算/封顶，与预警页规则互引）
        p.getRecords().forEach(bi -> bi.setExecRate(execRateOf(bi)));
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * D-DEC-042：单据发起页「预算科目」引用选择规则（申请人可选科目，非预算管理列表）。
     *
     * <p><b>为什么必须另开规则</b>：{@link #itemPage} 走 data_scope 五级过滤，而现网
     * {@code sys_role.data_scope} 分布为 普通员工 COMMON_EMP＝4（本部门，151 人）、
     * 绝大多数岗＝5（仅本人）；同时 {@code bg_item.dept_id} 实测 8/9 行为 NULL（预算按公司下达、
     * 未按部门拆）。于是 DEPT 档注入 {@code dept_id = 登录人部门} 直接命中 0 行，SELF 档无本人列
     * 降级本公司——同一个"科目下拉"在不同人手里忽空忽满，制单页红星永远选不出科目。</p>
     *
     * <p><b>本规则的边界（不是放开全租户口）</b>：预算明细属于"引用型主数据"，申请人需要的是
     * <b>法人/公司维度</b>的可选集合，故只按 {@link OrgGuard#enforceCompany()} 收成登录人本公司
     * （集团级账号 companyId 为空＝不限），跨公司依旧隔离；部门维度改为可选过滤参数，
     * 本部门行排在前面（不隐藏他部门行，避免额度被同公司其它部门"看不见地"重复占用）。</p>
     *
     * <p>管理侧列表（{@code budgetApi.itemPage} → {@code /item/page}）保持 {@code @DataScope} 不变
     * （R5-D-262 已把其部门档细化为「dept_id=本人部门 OR dept_id IS NULL」），
     * 本方法只服务单据发起页的查询，写操作权限不受影响。</p>
     */
    public PageResult<BgItem> itemPickPage(long pageNum, long pageSize, Long schemeId, Long deptId, Long itemId,
                                           java.util.Collection<Long> ids, Integer budgetYear) {
        LambdaQueryWrapper<BgItem> qw = new LambdaQueryWrapper<>();
        // 公司维度硬边界：集团级账号（enforceCompany 返回 null）不加条件，其余一律只认本公司
        Long company = orgGuard.enforceCompany();
        if (company != null) {
            qw.eq(BgItem::getCompanyId, company);
        } else if (itemId == null && (ids == null || ids.isEmpty())) {
            // 无公司且非精确查询（既没选费用项也没按 id 回填）：不给"全租户摊开"的默认处理，
            // 只放行下达态方案下的行，避免集团级账号一次拉出全部主体预算。
            log.debug("预算科目引用选择：登录人无公司维度（集团级），不加公司过滤");
        }
        if (schemeId != null) {
            qw.eq(BgItem::getSchemeId, schemeId);
        }
        if (deptId != null) {
            qw.eq(BgItem::getDeptId, deptId);
        }
        if (itemId != null) {
            qw.eq(BgItem::getItemId, itemId);
        }
        if (budgetYear != null) {
            qw.eq(BgItem::getBudgetYear, budgetYear);
        }
        if (ids != null && !ids.isEmpty()) {
            qw.in(BgItem::getId, ids);
        }
        // 本部门行优先、其次年度行（period=0 全年）在前，同维度键序稳定（与 matchItem 的选取偏好一致）
        qw.orderByAsc(BgItem::getBudgetYear).orderByAsc(BgItem::getBudgetPeriod).orderByAsc(BgItem::getDimKey);
        Page<BgItem> p = bgItemMapper.selectPage(Page.of(pageNum, pageSize), qw);
        // D-DEC-372：带出控制方式（HARD/SOFT/WARN），前端预算校验据此分档拦截/索取超支理由
        fillControlTypes(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    // ---------------- 预算明细解析（占用主体规则） ----------------

    /**
     * 解析本行应占用哪条预算明细（D-P2-01）。
     *
     * <p>历史规则是「全租户单值」映射：{@code md_item_budget_map} 按费用项 {@code limit 1} 取一条
     * {@code bg_item} 主键直接用。该主键可能属于其它公司/部门（实测 行政办公费→id 20 属于
     * company 33 / dept 1311），于是：占用记到别的主体；制单人在自己的 data_scope 内看不到该行，
     * 下拉退化显示原始主键。这里改为以<b>单据主体</b>为准：</p>
     * <ol>
     *   <li>调用方已给 {@code wantedItemId} 且与单据同公司 → 原样使用（尊重人工选择）；</li>
     *   <li>跨公司 → 按费用项在单据主体内重定位；重定位不到则<b>拒绝</b>（不把占用记到别的公司预算线）；</li>
     *   <li>未给 → 按 费用项 + 公司 + 部门 + 业务年度 命中真实预算行，再回退映射表主键（仍要求同公司）。</li>
     * </ol>
     *
     * @param wantedItemId   前端/调用方给定的预算明细主键，可为空
     * @param expenseItemId  费用项 id（{@code md_expense_item.id}，即 {@code bg_item.item_id}）
     * @param companyId      单据公司，可为空（空则不做主体约束）
     * @param deptId         单据部门，可为空（仅作优先级）
     * @param bizYear        单据业务年度（{@code exp_bill.period_year} / 合同签订日期年份），可为空；
     *                       用于在多年度预算行中就近命中，空则不区分年度
     * @return 预算明细主键；{@code null} 表示未能定位（由调用方决定跳过还是提示）
     */
    public Long resolveOccupyItem(Long wantedItemId, Long expenseItemId, Long companyId, Long deptId, Integer bizYear) {
        return resolveOccupyItem(wantedItemId, expenseItemId, companyId, deptId, bizYear, true);
    }

    /**
     * @param strict true=跨主体且无法重定位时抛异常（提交占用，钱不能记错主体）；
     *               false=返回 null 由调用方跳过（核销/释放等收尾链路，不阻断审批）
     */
    public Long resolveOccupyItem(Long wantedItemId, Long expenseItemId, Long companyId, Long deptId,
                                  Integer bizYear, boolean strict) {
        if (wantedItemId != null) {
            BgItem wanted = bgItemMapper.selectById(wantedItemId);
            if (wanted == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + wantedItemId);
            }
            if (companyId == null || companyId.equals(wanted.getCompanyId())) {
                return wantedItemId;
            }
            Long relocated = matchItem(expenseItemId, companyId, deptId, bizYear);
            if (relocated != null) {
                log.warn("预算明细 {} 属公司 {}，与单据公司 {} 不符，已按费用项 {} 重定位为 {}",
                        wantedItemId, wanted.getCompanyId(), companyId, expenseItemId, relocated);
                return relocated;
            }
            if (!strict) {
                log.warn("预算明细 {} 属公司 {}，与单据公司 {} 不符且按费用项 {} 未能重定位，跳过",
                        wantedItemId, wanted.getCompanyId(), companyId, expenseItemId);
                return null;
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "预算科目(预算明细 " + wantedItemId + ") 属于公司 "
                    + wanted.getCompanyId() + "，与单据公司 " + companyId + " 不一致，请在制单页重新选择预算科目");
        }
        Long bySubject = matchItem(expenseItemId, companyId, deptId, bizYear);
        if (bySubject != null) {
            return bySubject;
        }
        if (expenseItemId == null) {
            return null;
        }
        // 回退：映射表的固定主键，仍要求与单据同公司（跨主体一律不认）
        com.fssc.masterdata.entity.MdItemBudgetMap map = mdItemBudgetMapMapper.selectOne(
                new LambdaQueryWrapper<com.fssc.masterdata.entity.MdItemBudgetMap>()
                        .eq(com.fssc.masterdata.entity.MdItemBudgetMap::getItemId, expenseItemId)
                        .last("limit 1"));
        if (map == null || map.getBudgetItemId() == null) {
            return null;
        }
        BgItem mapped = bgItemMapper.selectById(map.getBudgetItemId());
        if (mapped == null || (companyId != null && !companyId.equals(mapped.getCompanyId()))) {
            log.warn("费用类型 {} 映射的预算明细 {} 不属于公司 {}，跳过（映射表为全租户单值，需按主体重配）",
                    expenseItemId, map.getBudgetItemId(), companyId);
            return null;
        }
        return mapped.getId();
    }

    /** 按 费用项 + 公司 命中预算明细行；部门精确者优先，业务年度就近优先，年度行(period=0)优先于分期行，年份新者优先 */
    private Long matchItem(Long expenseItemId, Long companyId, Long deptId, Integer bizYear) {
        if (expenseItemId == null) {
            return null;
        }
        LambdaQueryWrapper<BgItem> qw = new LambdaQueryWrapper<BgItem>()
                .eq(BgItem::getItemId, expenseItemId)
                .eq(companyId != null, BgItem::getCompanyId, companyId)
                .last("limit 200");
        List<BgItem> rows = bgItemMapper.selectList(qw);
        return rows.stream()
                .min(java.util.Comparator
                        .comparing((BgItem b) -> deptId != null && deptId.equals(b.getDeptId()) ? 0 : 1)
                        .thenComparing(b -> yearGap(b.getBudgetYear(), bizYear))
                        .thenComparing(b -> b.getBudgetPeriod() == null ? 0 : Math.abs(b.getBudgetPeriod()))
                        .thenComparing(b -> b.getBudgetYear() == null ? 0 : -b.getBudgetYear())
                        .thenComparing(BgItem::getDimKey, java.util.Comparator.nullsLast(String::compareTo)))
                .map(BgItem::getId).orElse(null);
    }

    /**
     * 预算行年度与单据业务年度的距离：同年 0，跨年取绝对差（缺同年度时退而取最近的同主体行，
     * 不再无条件"年份最大"，否则 2026 的占用会全部记到 2027 行、期间控制失效）；
     * {@code bizYear} 为空（历史单据无会计期间）时一律 0，退回旧规则。
     */
    private static int yearGap(Integer rowYear, Integer bizYear) {
        if (bizYear == null) {
            return 0;
        }
        return rowYear == null ? Integer.MAX_VALUE / 4 : Math.abs(rowYear - bizYear);
    }

    /**
     * 录入/编辑预算明细（bg_item）。
     * <p>
     * 维度键一律由维度字段推导（与 Excel 导入 {@link BudgetItemExcelService} 同一规则：
     * {@code 年度-COMPANY{公司id}-DEPT{部门id}-ITEM{费用项id}}），不接受前端原样回传的 dim_key——
     * 手工录入弹窗此前不带 dim_key，直接写入数据库会写入 dim_key=NULL 的孤儿行，页面「维度键」列为空且去重失效。
     * 唯一性按 DB 约束规则 {@code (tenant_id, scheme_id, budget_period, dim_key)} 判定，
     * 少带 budget_period 会把按月录入的 1 月行覆盖成 2 月行。
     */
    public void itemUpsert(BgItem item) {
        assertItemRequired(item);
        BgScheme scheme = bgSchemeMapper.selectById(item.getSchemeId());
        if (scheme == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算方案不存在: " + item.getSchemeId());
        }
        if (item.getCompanyId() == null) {
            item.setCompanyId(scheme.getCompanyId());
        }
        if (item.getBudgetPeriod() == null) {
            item.setBudgetPeriod(0);
        }
        // R5-D-267：期间范围以 bg_scheme.period_type 为唯一权威——ANNUAL 方案统一落 budget_period=0；
        // MONTH 方案保留 0~12（0=全年仍有历史语义，页面按「全年」展示；严格 1~12 的收紧待名称表修复后另议）。
        if ("ANNUAL".equalsIgnoreCase(scheme.getPeriodType())) {
            item.setBudgetPeriod(0);
        }
        String dimKey = item.getBudgetYear() + "-COMPANY" + item.getCompanyId()
                + "-DEPT" + item.getDeptId() + "-ITEM" + item.getItemId();
        item.setDimKey(dimKey);
        BgItem sameKey = bgItemMapper.selectOne(new LambdaQueryWrapper<BgItem>()
                .eq(BgItem::getSchemeId, item.getSchemeId())
                .eq(BgItem::getBudgetPeriod, item.getBudgetPeriod())
                .eq(BgItem::getDimKey, dimKey));
        if (item.getId() != null) {
            if (bgItemMapper.selectById(item.getId()) == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + item.getId());
            }
            if (sameKey != null && !sameKey.getId().equals(item.getId())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "该「方案 + 年度 + 期间 + 维度」的预算明细已存在（ID " + sameKey.getId() + "），请直接编辑那条记录或改维度/期间");
            }
            bgItemMapper.updateById(item);
            return;
        }
        if (sameKey != null) {
            item.setId(sameKey.getId());
            bgItemMapper.updateById(item);
        } else {
            item.setId(null);
            bgItemMapper.insert(item);
        }
    }

    /** 预算明细必填校验（与前端 BudgetItem 录入弹窗红星同一规则）：方案/年度/期间/费用项/总额缺一不可。 */
    private void assertItemRequired(BgItem item) {
        if (item == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算明细信息不能为空");
        }
        if (item.getSchemeId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择预算方案");
        }
        if (item.getBudgetYear() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入预算年度");
        }
        if (item.getBudgetYear() < 2000 || item.getBudgetYear() > 2100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算年度应在 2000-2100 之间，当前: " + item.getBudgetYear());
        }
        if (item.getItemId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择费用项");
        }
        if (item.getBudgetPeriod() != null && (item.getBudgetPeriod() < 0 || item.getBudgetPeriod() > 12)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算期间应为 0(全年) 或 1-12，当前: " + item.getBudgetPeriod());
        }
        if (item.getTotalAmount() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入预算总额");
        }
        if (item.getTotalAmount().signum() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算总额不能为负，调减请走预算调整单");
        }
    }

    // ---------------- 预算校验与占用 ----------------

    /** 可用额度 */
    public BigDecimal available(BgItem item) {
        if (item == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = nvl(item.getTotalAmount());
        BigDecimal adjust = nvl(item.getAdjustAmount());
        BigDecimal occupy = nvl(item.getOccupyAmount());
        BigDecimal used = nvl(item.getUsedAmount());
        return total.add(adjust).subtract(occupy).subtract(used);
    }

    /**
     * D-DEC-092：按预算明细主键读可用额度（只读、不加行锁），供付款侧的控制点复核。
     * <p>与 {@link #checkOccupyResult} 的差别：后者是"占用前的准入判定"（D-DEC-424 乙后超出只出结论不拦提交），
     * 本方法只回数，调用方自己决定记录还是拒绝——付款执行前的超预算复核属观察位，不该由额度工具抛业务异常。
     *
     * @return 可用额度；明细不存在返回 {@code null}（区别于 0，调用方据此不臆判）
     */
    public BigDecimal availableOf(Long budgetItemId) {
        if (budgetItemId == null) {
            return null;
        }
        BgItem item = bgItemMapper.selectById(budgetItemId);
        return item == null ? null : available(item);
    }

    /**
     * 解析预算明细所属方案的控制方式。
     * <p>
     * 合法值：HARD（硬控，超预算禁止提交）/ SOFT（软控，超预算需填写超支理由）/ WARN（预警，超预算仅提醒）。
     * <b>脏数据 fail-closed</b>：空值、未知值（例如历史脚本误写入的 "[object Promise]"）一律按 HARD 处理，
     * 保证控制方式不可被非法值绕过。
     * <p>
     * 硬控引用生效规则：仅 status IN ('ACTIVE','ISSUED') 的方案按其 control_type 生效；
     * 其余状态（DRAFT/APPROVING/CLOSED/脏数据）一律按 HARD 默认处理（fail-closed，不因方案未下达而放松控制）。
     */
    public String controlTypeOf(BgItem item) {
        if (item == null) {
            return "HARD";
        }
        BgScheme scheme = item.getSchemeId() == null ? null : bgSchemeMapper.selectById(item.getSchemeId());
        return resolveControlType(scheme);
    }

    /**
     * D-DEC-372：controlTypeOf 的方案侧判定拆出为纯函数，供 pick 接口按批量取回的方案同源解析
     * （前端预算校验需要按 HARD/SOFT/WARN 分档，判据必须与服务端占用校验一致，勿另写一份）。
     */
    private String resolveControlType(BgScheme scheme) {
        if (scheme == null) {
            return "HARD";
        }
        // 未下达/未启用的方案不作为硬控引用依据（规则 status IN ('ACTIVE','ISSUED')）
        String st = scheme.getStatus() == null ? "" : scheme.getStatus().trim().toUpperCase();
        if (!"ACTIVE".equals(st) && !"ISSUED".equals(st)) {
            log.debug("预算方案 {} 状态 {} 不在生效口径(ACTIVE/ISSUED)，硬控按 HARD 兜底", scheme.getSchemeCode(), scheme.getStatus());
            return "HARD";
        }
        String ct = scheme.getControlType();
        if (ct == null || ct.isBlank()) {
            return "HARD";
        }
        return switch (ct.trim().toUpperCase()) {
            case "HARD" -> "HARD";
            case "SOFT" -> "SOFT";
            case "WARN" -> "WARN";
            default -> "HARD"; // 非法值 fail-closed
        };
    }

    /** D-DEC-372：批量带出控制方式（一次 selectBatchIds，避免 pick 页 200 行 N+1 反查方案）。 */
    private void fillControlTypes(java.util.List<BgItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        java.util.Set<Long> schemeIds = new java.util.HashSet<>();
        for (BgItem it : items) {
            if (it.getSchemeId() != null) {
                schemeIds.add(it.getSchemeId());
            }
        }
        java.util.Map<Long, BgScheme> byId = new java.util.HashMap<>();
        if (!schemeIds.isEmpty()) {
            for (BgScheme s : bgSchemeMapper.selectBatchIds(schemeIds)) {
                byId.put(s.getId(), s);
            }
        }
        for (BgItem it : items) {
            it.setControlType(resolveControlType(it.getSchemeId() == null ? null : byId.get(it.getSchemeId())));
        }
    }

    /**
     * D-DEC-424 乙（预算默认处理 vs 强制校验档位）：预算<b>准入位</b>判定只出「超出」结论与金额，不再打挂提交。
     * <p>档位语义一字未改（HARD/SOFT/WARN 仍由 {@code bg_scheme.control_type} 决定），
     * 真实扣减校验仍留在 {@link #occupy} 内按档位执行——HARD 超预算照样拒绝占用。
     * <p>为何只降准入位：{@link #controlTypeOf} 对未下达/未启用方案与脏值一律默认处理成 HARD，
     * 而准入校验又常被传 {@code hard=true}（资本性支出发起即校验），于是「发起即失败」，
     * 单据连提交机会都没有，预算校验成了配置缺失的替罪羊。
     * <p>★超出不得静默：结论里带超出额，WARN 日志记录，扣减环节另有 {@code bg_occupy.remark} 记录。
     *
     * @param budgetItemId 预算明细（bg_item.id），不存在仍按参数错误抛出（不是预算问题）
     * @param amount       本次申请额
     */
    public OccupyCheckResult checkOccupyResult(Long budgetItemId, BigDecimal amount) {
        BgItem item = bgItemMapper.selectById(budgetItemId);
        if (item == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + budgetItemId);
        }
        BigDecimal apply = nvl(amount);
        BigDecimal avail = available(item);
        String controlType = controlTypeOf(item);
        boolean over = apply.compareTo(avail) > 0;
        BigDecimal overAmount = over ? apply.subtract(avail) : BigDecimal.ZERO;
        if (over) {
            log.warn("预算准入判定放行（D-DEC-424 乙）：明细 {}（公司 {} / 年度 {}）控制方式 {} 可用 {} < 本次 {}，超出 {}",
                    budgetItemId, item.getCompanyId(), item.getBudgetYear(), controlType, avail, apply, overAmount);
        }
        return new OccupyCheckResult(budgetItemId, controlType, avail, apply, over, overAmount);
    }

    /**
     * D-DEC-424 乙：预算准入判定结论（超出＝给结论与金额，提交可继续）。
     * 只有「明细不存在」会抛错，本对象一旦返回即代表放行。
     */
    public static class OccupyCheckResult {

        private final Long budgetItemId;
        /** 方案档位（HARD/SOFT/WARN），只作展示与判据说明，不决定本位是否放行 */
        private final String controlType;
        /** 可用额度（可为负＝预算线已被突破） */
        private final BigDecimal available;
        private final BigDecimal applyAmount;
        private final boolean over;
        /** 超出额 = 本次申请 − 可用（未超出为 0） */
        private final BigDecimal overAmount;

        public OccupyCheckResult(Long budgetItemId, String controlType, BigDecimal available,
                                 BigDecimal applyAmount, boolean over, BigDecimal overAmount) {
            this.budgetItemId = budgetItemId;
            this.controlType = controlType;
            this.available = available;
            this.applyAmount = applyAmount;
            this.over = over;
            this.overAmount = overAmount;
        }

        public Long getBudgetItemId() {
            return budgetItemId;
        }

        public String getControlType() {
            return controlType;
        }

        public BigDecimal getAvailable() {
            return available;
        }

        public BigDecimal getApplyAmount() {
            return applyAmount;
        }

        public boolean isOver() {
            return over;
        }

        public BigDecimal getOverAmount() {
            return overAmount;
        }

        /** 准入位结论：超出也放行（真实扣减校验在 {@link BudgetService#occupy} 内按档位执行） */
        public boolean isPassed() {
            return true;
        }

        public String getMessage() {
            return over
                    ? "预算超出（控制方式 " + controlType + "，可继续提交）：可用 " + available
                            + "，本次申请 " + applyAmount + "，超出 " + overAmount
                    : "预算充足：可用 " + available + "，本次申请 " + applyAmount;
        }
    }

    /** 占用（事务：行锁 + 写流水 + 原子汇总更新），超支理由缺省为空 */
    @Transactional(rollbackFor = Exception.class)
    public void occupy(Long budgetItemId, BigDecimal amount, String bizType, Long bizId, String bizNo) {
        occupy(budgetItemId, amount, bizType, bizId, bizNo, null);
    }

    /**
     * 占用（事务：行锁 + 写流水 + 原子汇总更新）。
     * <p>
     * 控制方式由方案 {@code bg_scheme.control_type} 决定（BUD-01 修复：不再硬编码 hard=true）：
     * <ul>
     *  <li>HARD：超预算直接拒绝提交</li>
     *  <li>SOFT：超预算需填写超支理由（{@code overrunReason} 非空），放行并在占用流水记录</li>
     *  <li>WARN：超预算仅提醒，放行并在占用流水记录</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public void occupy(Long budgetItemId, BigDecimal amount, String bizType, Long bizId, String bizNo,
                       String overrunReason) {
        // 关键：必须用 FOR UPDATE 的「当前读」结果做可用额判定。
        // 若改成先 selectForUpdate 再 selectById，MySQL RR 隔离下 selectById 是快照读，
        // 会读不到其他并发事务已提交的占用，导致并发超占（实测 8 并发 × 200 可占出 1600 > 额度 1000）。
        BgItem item = bgItemMapper.selectForUpdate(budgetItemId);
        if (item == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + budgetItemId);
        }
        // R5-D-265：提交链路一致性校验——表头 occupy_amount vs 全行流水净额（bg_occupy 只增不改＝真相账本）。
        // 台账修法为「不等即拒绝」，但现网 bg_item 表头含无流水对应的历史/导入占用（本会话只读勾稽：
        // 9 行表头 occupy 多为非零而 bg_occupy 空），双向硬拦会把历史行全部锁死——按现状适配为：
        //   ① 流水净额 > 表头占用（可用被高估，继续放行将按虚高额度出款）＝危险方向，硬拦＋error 记录；
        //   ② 表头占用 > 流水净额（历史背离，可用被低估，偏保守）＝放行但 error 记录，交人工按流水裁决。
        // 待人工按 R5-D-265 台账重算历史行后，可升级为严格双向相等校验（见 _w20_pk07_report.md 转人工栏）。
        BigDecimal flowNetAll = nvl(bgOccupyMapper.sumNetOccupyByItem(budgetItemId));
        BigDecimal headOccupyNow = nvl(item.getOccupyAmount());
        if (flowNetAll.compareTo(headOccupyNow) > 0) {
            log.error("R5-D-265 提交链路一致性闸：预算行 {} 流水净占用 {} > 表头占用 {}（可用将被高估），拒绝本次占用并留痕",
                    budgetItemId, flowNetAll, headOccupyNow);
            throw new BizException(ErrorCode.PARAM_INVALID, "预算占用账实不一致（流水净占用大于表头，可用余额将被高估），"
                    + "为防超占已拒绝提交；请联系管理员核对预算行 " + budgetItemId);
        }
        if (flowNetAll.compareTo(headOccupyNow) < 0) {
            log.error("R5-D-265 提交链路一致性提示：预算行 {} 表头占用 {} > 流水净占用 {}（历史背离/无流水存量行，可用被低估），"
                    + "本次放行但留痕，待人工按流水裁决重算", budgetItemId, headOccupyNow, flowNetAll);
        }
        BigDecimal avail = available(item);
        String remark = null;
        if (amount.compareTo(avail) > 0) {
            // D-DEC-105 附带点：该预算明细「下达额 total_amount=0.00」时，任何金额都算超支，
            // 而 WARN/SOFT 方案会静默放行，流水备注与"真超支"混成一句，事后无从区分
            // 「额度确实被突破」和「这个科目压根没下达过预算」。现把两种成因在备注里分开记录。
            // （零下达科目是否应当硬拦＝产品规则，需人工裁决，此处不擅自改判定。）
            boolean neverIssued = nvl(item.getTotalAmount()).signum() == 0;
            String cause = neverIssued ? "该明细未下达预算额度（total_amount=0）" : "超预算";
            String msg = "预算不足：" + cause + "，可用 " + avail + "，本次申请 " + amount;
            switch (controlTypeOf(item)) {
                case "HARD" -> throw new BizException(ErrorCode.PARAM_INVALID, msg);
                case "SOFT" -> {
                    if (overrunReason == null || overrunReason.isBlank()) {
                        throw new BizException(ErrorCode.PARAM_INVALID, msg + "；当前为软控方案，需填写超支理由后方可提交");
                    }
                    remark = (neverIssued ? "零下达额度软控通过" : "超预算软控通过")
                            + "（理由：" + overrunReason + "）";
                }
                default -> {
                    remark = (neverIssued ? "零下达额度预警通过" : "超预算预警通过")
                            + "（控制方式：WARN）";
                    log.warn("预算占用放行但额度异常：明细 {}（公司 {} / 年度 {}）本次 {} > 可用 {}，{}",
                            budgetItemId, item.getCompanyId(), item.getBudgetYear(), amount, avail, cause);
                }
            }
        }
        appendFlow(budgetItemId, "OCCUPY", amount, bizType, bizId, bizNo, remark);
        addAmount(budgetItemId, "occupy", amount);
    }

    /**
     * 释放（撤销占用，事务）。P1-1：行锁防止与占用/核销交错。
     * <p><b>D-DEC-425 甲案（占用/核销互斥）</b>：原实现按调用方给的金额盲减
     * {@code bg_item.occupy_amount}，去重「由调用方保证」而调用方并未保证——同一单据被反审批回退
     * 两次，就会把「已经转成已用」的那一笔再冲一遍占用，形成
     * 「同一笔既在 occupy_amount 又在 used_amount」的双记（实测 bg_item id=2
     * total 100,000／occupy 56,500／used 56,500 ⇒ 可用 −13,000，该预算线对任何正金额都必拦）。
     * 现与 {@link #writeOff} 同源改判：释放额以 {@code bg_occupy} 账本上
     * 「本单据(biz_id) 在本预算行的净占用」为上限，净额 ≤ 0（已全额转实或已释放）即整体跳过。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(Long budgetItemId, BigDecimal amount, String bizType, Long bizId, String bizNo) {
        BgItem cur = bgItemMapper.selectForUpdate(budgetItemId);
        if (cur == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + budgetItemId);
        }
        BigDecimal take = amount == null ? BigDecimal.ZERO : amount;
        if (take.signum() > 0 && bizId != null) {
            BigDecimal net = nvl(bgOccupyMapper.sumNetOccupyByItemAndBiz(
                    budgetItemId, bizId, bizType, EXPENSE_BIZ_TYPES.contains(bizType)));
            if (net.signum() <= 0) {
                log.info("预算行 {} 上单据 {}（{}）已无剩余占用（净额 {}），跳过本次释放（D-DEC-425 防占用/已用双记）",
                        budgetItemId, bizNo, bizType, net);
                return;
            }
            if (take.compareTo(net) > 0) {
                log.info("预算行 {} 单据 {} 释放 {} 超出其净占用 {}，按净占用截断（D-DEC-425）",
                        budgetItemId, bizNo, take, net);
                take = net;
            }
        }
        appendFlow(budgetItemId, "RELEASE", take.negate(), bizType, bizId, bizNo, null);
        addAmount(budgetItemId, "occupy", take.negate());
    }

    // ---------------- D-DEC-105 还款冲减借款占用 ----------------

    /** 还款冲减/回补流水的业务类型标记（原全库 EXP_REPAY 0 行的载体） */
    public static final String BIZ_TYPE_REPAY = "EXP_REPAY";

    /** 借款占用流水的业务类型标记 */
    public static final String BIZ_TYPE_LOAN = "EXP_LOAN";

    /**
     * 单据类型 → 预算占用流水 biz_type（D-DEC-105 附带点：还款/借款的占用一律被记成 EXP_CLAIM，
     * 于是 {@code bg_occupy} 里 EXP_LOAN／EXP_REPAY 永远 0 行，占用台账按业务类型读数全部失真）。
     * <p>提交占用、驳回释放、反审批回冲三处必须同用一个规则，否则释放流水与占用流水类型不一致。
     * <p>返回值均落在 {@link #EXPENSE_BIZ_TYPES} 内，{@code writeOff} 的跨类型归户（按 biz_id）不受影响。
     */
    public static String occupyBizTypeOf(String billType) {
        return switch (billType == null ? "CLAIM" : billType) {
            case "APPLY" -> "EXP_APPLY";
            case "LOAN" -> BIZ_TYPE_LOAN;
            case "REPAY" -> BIZ_TYPE_REPAY;
            case "PAY" -> "EXP_PAY";
            default -> "EXP_CLAIM";
        };
    }

    /**
     * 还款单对「借款单既有占用」的等额冲减（D-DEC-105，铁律：只增不改）。
     *
     * <p><b>为什么不是 occupy 也不是 release</b>：还款在业务上是资金回流，预算占用应当被<b>释放</b>，
     * 而不是再占用一次。实测 12-24 还款单提交即写 {@code bg_occupy} OCCUPY 80,000（id=45），
     * 借款单原始占用（id=33）仍在 ⇒ 同一笔借款双重占用。
     * 本方法追加的是 <b>RELEASE 负数流水</b>，且：
     * <ul>
     *  <li>归户在<b>借款单</b> biz_id 上（占用本来就是借款单的），biz_no 记还款单号，
     *      biz_type={@code EXP_REPAY}——冲减额直接从借款单在该行的<b>净占用</b>里扣，
     *      借款净占用为 0 的行不再冲减，天然不可能把别人的占用冲掉；</li>
     *  <li>去重：先查本还款单已冲减合计（{@link BgOccupyMapper#selectRepayOffsetLedger}），
     *      只补差额 ⇒ 提单冲一次、结清再复核一次不会冲成两笔；</li>
     *  <li>上限＝本次还款金额，绝不超额释放。</li>
     * </ul>
     *
     * @param loanBillId      被冲减占用的借款单 id（exp_bill 主键）
     * @param amount          本次还款金额（冲减上限）
     * @param repayBillNo     还款单号（去重判据 + 台账可读性）
     * @param candidateItems  还款行解析出的预算科目（与借款单曾占用的科目取并集后逐行冲减），可为空
     * @param remark          流水备注
     * @return 实际冲减金额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal offsetLoanOccupy(Long loanBillId, BigDecimal amount, String repayBillNo,
                                       java.util.Collection<Long> candidateItems, String remark) {
        if (loanBillId == null || amount == null || amount.signum() <= 0
                || repayBillNo == null || repayBillNo.isBlank()) {
            return BigDecimal.ZERO;
        }
        java.util.LinkedHashSet<Long> items = new java.util.LinkedHashSet<>();
        if (candidateItems != null) {
            for (Long id : candidateItems) {
                if (id != null) {
                    items.add(id);
                }
            }
        }
        // 借款单实际占用过的行（还款行解析到的科目可能与借款科目不同）
        List<Long> loanItems = bgOccupyMapper.selectOccupiedItemsByBill(loanBillId);
        if (loanItems != null) {
            items.addAll(loanItems.stream().filter(java.util.Objects::nonNull).toList());
        }
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        // 已净冲减 = 已冲减(RELEASE) − 已回补(OCCUPY)：驳回/撤回后重提必须能再冲一次，
        // 故判据取净额而非 RELEASE 总额（否则重提会被判成「已足额冲减」而静默跳过）。
        BigDecimal already = BigDecimal.ZERO;
        for (java.util.Map<String, Object> row : asList(bgOccupyMapper
                .selectRepayOffsetLedger(BIZ_TYPE_REPAY, loanBillId, repayBillNo))) {
            already = already.add(dec(row.get("offsetAmount")).subtract(dec(row.get("restoreAmount"))));
        }
        BigDecimal remaining = amount.subtract(already);
        if (remaining.signum() <= 0) {
            // 本还款单已足额冲减过（提单环节与结清环节重放）
            return BigDecimal.ZERO;
        }
        BigDecimal done = BigDecimal.ZERO;
        for (Long itemId : items) {
            if (remaining.signum() <= 0) {
                break;
            }
            BgItem item = bgItemMapper.selectForUpdate(itemId);
            if (item == null) {
                continue;
            }
            BigDecimal loanNet = nvl(bgOccupyMapper.sumNetOccupyByItemAndBiz(
                    itemId, loanBillId, BIZ_TYPE_LOAN, true));
            if (loanNet.signum() <= 0) {
                continue;
            }
            BigDecimal take = loanNet.min(remaining);
            appendFlow(itemId, "RELEASE", take.negate(), BIZ_TYPE_REPAY, loanBillId, repayBillNo, remark);
            addAmount(itemId, "occupy", take.negate());
            remaining = remaining.subtract(take);
            done = done.add(take);
        }
        if (done.signum() == 0) {
            log.info("还款单 {} 对借款单 {} 无可冲减的预算占用（借款净占用为 0 或未占用），未写流水",
                    repayBillNo, loanBillId);
        } else {
            log.info("还款单 {} 冲减借款单 {} 的预算占用 {}（还款金额 {}）", repayBillNo, loanBillId, done, amount);
        }
        return done;
    }

    /**
     * 还款单驳回/撤回：把本还款单已做的冲减<b>等额回补</b>（D-DEC-105 释放钩子的反向环节）。
     *
     * <p>还款没办成，预算就不该少——否则可用额度被一张死单永久吃掉。回补按
     * {@link BgOccupyMapper#selectRepayOffsetLedger} 的逐行「已冲减 − 已回补」差额追加 OCCUPY 流水，
     * 因此驳回重提、重复事件都不会回补出第二笔占用。
     *
     * @return 实际回补金额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal restoreLoanOccupy(Long loanBillId, String repayBillNo, String remark) {
        if (loanBillId == null || repayBillNo == null || repayBillNo.isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal done = BigDecimal.ZERO;
        for (java.util.Map<String, Object> row : asList(bgOccupyMapper
                .selectRepayOffsetLedger(BIZ_TYPE_REPAY, loanBillId, repayBillNo))) {
            Long itemId = lng(row.get("budgetItemId"));
            if (itemId == null) {
                continue;
            }
            BigDecimal delta = dec(row.get("offsetAmount")).subtract(dec(row.get("restoreAmount")));
            if (delta.signum() <= 0) {
                continue;
            }
            bgItemMapper.selectForUpdate(itemId);
            appendFlow(itemId, "OCCUPY", delta, BIZ_TYPE_REPAY, loanBillId, repayBillNo, remark);
            addAmount(itemId, "occupy", delta);
            done = done.add(delta);
        }
        if (done.signum() > 0) {
            log.info("还款单 {} 驳回/撤回，回补借款单 {} 的预算占用 {}", repayBillNo, loanBillId, done);
        }
        return done;
    }

    /**
     * 还款单驳回/撤回的自寻址回补：从 {@code bg_occupy} 反查本还款单当初冲减过哪些借款单，逐单回补。
     * <p>调用方（{@code ExpenseWfEventListener} 驳回分支）只需给出还款单号，不必重算 FIFO 分配，
     * 冲减/回补同源同一规则 ⇒ 不会出现「回补了没冲过的单子」这类二次失真。
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal restoreLoanOccupyByRepayNo(String repayBillNo, String remark) {
        if (repayBillNo == null || repayBillNo.isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal done = BigDecimal.ZERO;
        for (Long loanBillId : asListLong(bgOccupyMapper.selectOffsetLoanBillsByRepayNo(repayBillNo))) {
            done = done.add(restoreLoanOccupy(loanBillId, repayBillNo, remark));
        }
        return done;
    }

    /**
     * 结清对齐（D-DEC-105 释放钩子的「结清」环节）：把本还款单对某借款单的<b>净冲减额</b>
     * 校准为审批后的实际冲抵额。
     *
     * <p>提单环节按当时的在贷余额做 FIFO 预分配，审批期借款池可能已被其它还款/核销改动，
     * 实际冲抵额（{@code exp_loan_offset}）与预分配可能不等。本方法先全额回补、再按实际额冲减，
     * 使台账净额＝实际额；两者已相等时不追加任何流水（去重，收尾重放不产生抖动）。
     *
     * @param exactAmount 实际冲抵额（null 表示不调整）
     * @return 本次净变化（正=追加冲减，负=回补）
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal alignLoanOccupy(Long loanBillId, BigDecimal exactAmount, String repayBillNo,
                                      java.util.Collection<Long> candidateItems, String remark) {
        if (loanBillId == null || repayBillNo == null || repayBillNo.isBlank() || exactAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal net = BigDecimal.ZERO;
        for (java.util.Map<String, Object> row : asList(bgOccupyMapper
                .selectRepayOffsetLedger(BIZ_TYPE_REPAY, loanBillId, repayBillNo))) {
            net = net.add(dec(row.get("offsetAmount")).subtract(dec(row.get("restoreAmount"))));
        }
        if (net.compareTo(exactAmount) == 0) {
            return BigDecimal.ZERO;
        }
        if (net.signum() > 0) {
            restoreLoanOccupy(loanBillId, repayBillNo, remark + "（结清对齐）");
        }
        if (exactAmount.signum() > 0) {
            offsetLoanOccupy(loanBillId, exactAmount, repayBillNo, candidateItems, remark);
        }
        return exactAmount.subtract(net);
    }

    private static List<java.util.Map<String, Object>> asList(List<java.util.Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private static List<Long> asListLong(List<Long> rows) {
        return rows == null ? List.of() : rows.stream().filter(java.util.Objects::nonNull).toList();
    }

    private static BigDecimal dec(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }

    private static Long lng(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 核销（占用转已用，事务）。
     * <p>
     * P0-C2（整改 2026-09-21）：占用转出量改为按 {@code bg_occupy} 账本推导「本单据(biz_id)
     * 在该预算行的净占用」，不再用 {@code bg_item.occupy_amount}（全预算行汇总）截断。
     * 原规则下 A 单占用 100、B 单核销 800 会把 A 的占用一并转走并给 B 记 800 已用，
     * 造成占用/已用同时失真。
     * <p>
     * 单据在该行从未占用（线下直报）→ 只加已用、不动占用；已无剩余占用仍被重放核销 → 整体跳过。
     */
    @Transactional(rollbackFor = Exception.class)
    public void writeOff(Long budgetItemId, BigDecimal amount, String bizType, Long bizId, String bizNo) {
        BgItem cur = bgItemMapper.selectForUpdate(budgetItemId);
        if (cur == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + budgetItemId);
        }
        BigDecimal net;
        BigDecimal everOccupied;
        if (bizId == null) {
            // 无单据归属的手工核销（占用流水无法归户）：退回全行汇总方式约束，至少不产生负占用
            net = nvl(cur.getOccupyAmount());
            everOccupied = net;
        } else {
            boolean crossExpense = EXPENSE_BIZ_TYPES.contains(bizType);
            net = nvl(bgOccupyMapper.sumNetOccupyByItemAndBiz(budgetItemId, bizId, bizType, crossExpense));
            everOccupied = nvl(bgOccupyMapper.sumOccupiedByItemAndBiz(budgetItemId, bizId, bizType, crossExpense));
        }
        if (everOccupied.signum() > 0 && net.signum() <= 0) {
            log.info("预算行 {} 上单据 {}（{}）已无剩余占用（历史占用 {}），跳过本次核销（P0-C2 防重复核销虚增已用）",
                    budgetItemId, bizNo, bizType, everOccupied);
            return;
        }
        BigDecimal occupyDelta = net.min(amount);
        if (occupyDelta.compareTo(BigDecimal.ZERO) > 0) {
            appendFlow(budgetItemId, "WRITE_OFF", occupyDelta.negate(), bizType, bizId, bizNo, null);
            addAmount(budgetItemId, "occupy", occupyDelta.negate());
        }
        if (occupyDelta.compareTo(amount) < 0) {
            log.warn("预算行 {} 单据 {} 核销 {} 超过其净占用 {}，差额按直报口径仅计已用",
                    budgetItemId, bizNo, amount, occupyDelta);
        }
        addAmount(budgetItemId, "used", amount);
    }

    /**
     * 反审批/退回修改：回冲已核销的预算（对称 {@link #writeOff}）。
     * <p>
     * 单据反审批退回可编辑态时，此前核销转出的已用额度必须等额回冲，否则
     * 「可用额度」被幽灵占用，重提时预算校验必然误拦（D-V3-135 首跑实证）。
     * <p><b>D-DEC-425 甲案</b>：去重原「由调用方保证」而调用方并未保证——同一 bizId 重放回冲
     * 会把已用额记成负数，与本行真实占用互相错身，最终留下
     * 「同一笔既占用又核销」的双记（实测 bg_item id=2 可用 −13,000）。
     * 现按汇总侧不变量办结：回冲额以本行 {@code used_amount} 为上限，已用 ≤ 0 即整体跳过；
     * 「账本净额 vs 汇总列」的日终对账作业（乙案）另列移交项，本方法不擅自改账。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reverseWriteOff(Long budgetItemId, BigDecimal amount, String bizType, Long bizId, String bizNo) {
        BgItem cur = bgItemMapper.selectForUpdate(budgetItemId);
        if (cur == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + budgetItemId);
        }
        BigDecimal take = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal used = nvl(cur.getUsedAmount());
        if (take.signum() <= 0) {
            return;
        }
        if (used.signum() <= 0) {
            log.info("预算行 {} 已用额为 {}，单据 {}（{}）无已核销可回冲，跳过（D-DEC-425 防重复回冲记负已用）",
                    budgetItemId, used, bizNo, bizType);
            return;
        }
        if (take.compareTo(used) > 0) {
            log.info("预算行 {} 单据 {} 回冲核销 {} 超出本行已用 {}，按已用截断（D-DEC-425）",
                    budgetItemId, bizNo, take, used);
            take = used;
        }
        appendFlow(budgetItemId, "RELEASE", take.negate(), bizType, bizId, bizNo, "反审批/退回修改回冲已核销预算");
        addAmount(budgetItemId, "used", take.negate());
    }

    // ---------------- D-DEC-104 消耗确认时点 ----------------

    /**
     * D-DEC-104：单行「消耗确认」入参。
     *
     * @param budgetItemId  行上显式选择的预算科目（bg_item 主键），可为空
     * @param expenseItemId 费用项 id（budgetItemId 为空时按 费用项+主体 就近解析，与提交占用同一规则）
     * @param amount        本行确认消耗的金额
     */
    public record SettleLine(Long budgetItemId, Long expenseItemId, BigDecimal amount) {
    }

    /**
     * D-DEC-104：预算「占用 → 已用（消耗）」的确认时点＝<b>实际付款成功</b>（或无现金环节的结清），
     * <b>不再是审批通过</b>。
     *
     * <p>旧规则把转换挂在审批通过钩子上（EXP_CLAIM/EXP_PAY 单据收尾、TR_PAY 批次审批收尾三处），
     * 于是「款未付、票未得」的费用已经计进 {@code bg_item.used_amount}：预算执行报表提前报满，
     * 而后续付款失败/退票时这笔消耗没人回冲（{@code reverseWriteOff} 只覆盖反审批一路）。
     * 现在审批通过只保留占用（OCCUPIED，额度继续被本单预留住，不会失控），
     * 钱真的出去（{@code PayBatchService.execute} 打款成功）才转已用。</p>
     *
     * <p><b>去重</b>：本单据名下已存在任一条执行核销流水（{@link BgOccupyMapper#countWriteOffForBill}，
     * 规则限费用/付款/资金三域、biz_id=exp_bill 主键）→ 整单跳过，重放事件/重试付款不会二次转消耗。
     * 与 D-DEC-105 {@code offsetLoanOccupy}/{@code alignLoanOccupy} 同为「按台账净额判重、只补差额」的风格。</p>
     *
     * <p><b>不参与付款事务</b>：{@code REQUIRES_NEW} 独立提交，预算行被锁/科目被删等异常只回滚本次确认，
     * 绝不把已经发出去的银行指令一起回滚（调用方另有记录）。</p>
     *
     * <p><b>不做的事</b>：借款单（EXP_LOAN）与还款单（EXP_REPAY）不在本规则内——借款的额度本来就应由
     * 还款冲减（{@code offsetLoanOccupy}）或报销单接续，打款转已用会把同一笔钱计两次消耗。
     * 调用方须自行限定单据类型。</p>
     *
     * @return 实际确认消耗的行数（0＝已核销过 / 全部未定位到预算科目）
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public int settleOccupyOnPaid(Long billId, String billNo, String billType, Long companyId, Long deptId,
                                  Integer periodYear, java.util.Collection<SettleLine> lines) {
        if (billId == null || lines == null || lines.isEmpty()) {
            return 0;
        }
        // 单据类型校验（D-DEC-104）：只有"真的花掉钱"的单据才转消耗。
        // 借款单（LOAN）放款、还款单（REPAY）收款、事前申请（APPLY）都各有自己的账环节——
        // 借款占用由还款 offsetLoanOccupy 冲减或由报销单接续，此处再转一次已用＝同一笔钱计两次消耗。
        String bt = billType == null ? "" : billType.trim().toUpperCase();
        if ("LOAN".equals(bt) || "REPAY".equals(bt) || "APPLY".equals(bt)) {
            log.info("单据 {}（{}）不属于消耗型单据，不做占用转已用（D-DEC-104 口径）", billNo, bt);
            return 0;
        }
        String bizType = occupyBizTypeOf(billType);
        if (bgOccupyMapper.countWriteOffForBill(billId) > 0) {
            log.info("单据 {}（{}）已发生过预算执行核销，跳过本次消耗确认（D-DEC-104 幂等）", billNo, bizType);
            return 0;
        }
        int done = 0;
        for (SettleLine line : lines) {
            if (line == null || line.amount() == null || line.amount().signum() <= 0) {
                continue;
            }
            Long itemId = resolveOccupyItem(line.budgetItemId(), line.expenseItemId(), companyId, deptId,
                    periodYear, false);
            if (itemId == null) {
                // 与 D-DEC-145 同一规则：定位不到科目就如实记录，不臆造预算行
                log.warn("单据 {} 行（费用项 {}）未解析到预算科目（显式/费用项映射均未命中），跳过消耗确认",
                        billNo, line.expenseItemId());
                continue;
            }
            writeOff(itemId, line.amount(), bizType, billId, billNo);
            done++;
        }
        log.info("单据 {}（{}）付款/结清 → 预算占用已转已用 {} 行（D-DEC-104：转换时点已从审批通过钩子后移）",
                billNo, bizType, done);
        return done;
    }

    /**
     * 本单据是否已发生过预算执行核销（writeOff）——反审核选择回冲规则的判据。
     * 复用 {@link BgOccupyMapper#countWriteOffForBill}（仅费用/付款/资金三域，biz_id=exp_bill 主键）。
     */
    public boolean hasWriteOff(Long billId) {
        return bgOccupyMapper.countWriteOffForBill(billId) > 0;
    }

    // ---------------- D-DEC-145 薪酬预算占用 ----------------

    /** 薪酬域占用流水的业务类型键（biz_id = gl_salary_run.id，与费用域单据主键空间不同，故一律按本键归户） */
    public static final String BIZ_TYPE_SALARY = "GL_SALARY";

    /**
     * D-DEC-145：薪酬<b>计提</b>即占用预算 —— 人工成本此前全程不进预算（12-30:173）。
     *
     * <p>证据：{@code gl_salary_run} 单行 202611 批次应发 390,000.00，而 {@code bg_occupy} 里
     * 没有任何薪酬域的流水；{@code bg_item} 全表 9 行的费用项只有 行政办公费/差旅费/招待费/修理费/劳动保护费，
     * <b>没有一条薪酬预算明细</b>；{@code md_item_budget_map} 把 352 工资薪酬 映射到 budget_item_id=98887，
     * 而 bg_item 最大主键为 9（映射表 211 条孤儿行）。所以本方法在现网数据下会走到「占用落空」分支，
     * 这是<b>如实记录</b>而不是静默通过 —— 薪酬科目是否单独建预算线、走哪条控制方式＝人工裁决项。</p>
     *
     * <p><b>规则与费用域一致</b>：占用量取计提凭证的成本额（{@code VoucherService.generateSalaryVoucher}
     * 借 6602 的金额＝{@code salary_amount} 应发工资总额，扣除/社保/个税是应发内部的代扣，不重复占额度）；
     * 科目解析复用 {@link #resolveOccupyItem}(wanted=null, strict=false)——先按 费用项+公司+部门+年度 命中真实预算行，
     * 再回退映射表（仍要求同主体），定位不到就返回 null 交由调用方记录，<b>绝不臆造预算行</b>。</p>
     *
     * <p><b>超支只记录不阻断</b>：{@code bg_scheme.control_type=HARD} 且额度不足时，费用域是直接拒提；
     * 故本版本按 brief 拦成 WARN 记录 + 报告，未占用（不写半条流水），是否改为硬拦见交付说明的待裁点。</p>
     *
     * <p><b>去重</b>：本批次名下已存在 OCCUPY 流水（{@link BgOccupyMapper#selectOccupiedItemsByBiz}）即整体跳过，
     * 重放核算/重试起流不会二次占额。事务传播 REQUIRED：与薪酬核算同生共死，
     * {@code wfEngineFacade.start} 抛错回滚时占用流水一并回滚，不留幽灵占用。</p>
     *
     * @param expenseItemId 薪酬费用项（{@code md_expense_item.id}），为空表示未配置薪酬预算科目规则
     * @param bizYear       预算年度（从 {@code gl_salary_run.period} yyyyMM 前 4 位取）
     * @return 实际占用的预算明细主键；{@code null}＝未占用（已占过 / 定位不到科目 / 硬控超支记录）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long occupySalaryBudget(Long runId, String runNo, Long expenseItemId, Long companyId, Long deptId,
                                   Integer bizYear, BigDecimal amount) {
        if (runId == null) {
            return null;
        }
        if (amount == null || amount.signum() <= 0) {
            log.info("薪酬批次 {} 应发金额为 {}，无额度可占用（D-DEC-145）", runNo, amount);
            return null;
        }
        if (!bgOccupyMapper.selectOccupiedItemsByBiz(BIZ_TYPE_SALARY, runId).isEmpty()) {
            log.info("薪酬批次 {} 名下已有占用流水，跳过重复占用（D-DEC-145 幂等）", runNo);
            return null;
        }
        Long itemId = resolveOccupyItem(null, expenseItemId, companyId, deptId, bizYear, false);
        if (itemId == null) {
            log.warn("薪酬批次 {} 应发 {} 未定位到预算科目（费用项 {}／公司 {}／年度 {} 下无预算明细行，" +
                    "映射表指向的预算明细不存在）——占用落空，本批次不计入预算执行，不臆造科目（D-DEC-145）",
                    runNo, amount, expenseItemId, companyId, bizYear);
            return null;
        }
        BgItem item = bgItemMapper.selectById(itemId);
        BigDecimal avail = available(item);
        if (amount.compareTo(avail) > 0 && "HARD".equals(controlTypeOf(item))) {
            log.warn("薪酬批次 {} 超预算未占用：预算明细 {}（公司 {} / 年度 {}）可用 {}，本次应发 {}，方案为硬控——" +
                    "D-DEC-145 本版只留痕不阻断薪酬核算，人工成本超支是否硬拦＝待人工裁决",
                    runNo, itemId, companyId, bizYear, avail, amount);
            return null;
        }
        // 预检与占用之间理论上可被并发插队（此刻再撞上 HARD 拦截），故默认处理 catch：
        // occupy 是本类自调用（不过代理），异常不会把外层事务标成 rollback-only，薪酬核算不受牵连。
        try {
            occupy(itemId, amount, BIZ_TYPE_SALARY, runId, runNo,
                    amount.compareTo(avail) > 0 ? "薪酬计提超支记录（D-DEC-145：暂不阻断薪酬核算）" : null);
        } catch (BizException ex) {
            log.warn("薪酬批次 {} 预算占用被拒（并发下额度已被占满）：{}，本批次未计入预算执行（D-DEC-145）",
                    runNo, ex.getMessage());
            return null;
        }
        log.info("薪酬批次 {} 计提占用预算：明细 {} 金额 {}（可用余额 {}）——D-DEC-145 人工成本首次进预算闸口",
                runNo, itemId, amount, avail);
        return itemId;
    }

    /**
     * D-DEC-145 ＋ D-DEC-104：薪酬<b>发放成功</b>（GL_SALARY_PAY 末节点通过且凭证已出）才把占用转已用。
     * 计提链通过（POSTED）只保留占用——那时尚未付出一分钱，与 D-DEC-104 的消耗确认时点同一规则。
     *
     * <p>逐行取「本批次在该预算行的净占用」{@link BgOccupyMapper#sumNetOccupyByItemAndBiz}（跨类型关闭：
     * 薪酬 biz_id 与 exp_bill 主键会撞号），有净占用才核销，重放发放自动归零跳过。</p>
     *
     * <p>{@code REQUIRES_NEW}：预算侧异常（明细被删、行锁等待）只回滚本次消耗确认，
     * 绝不把已办结的发放凭证与状态回写一起带回滚。</p>
     *
     * @return 实际确认消耗的预算行数（0＝无占用 / 已核销过）
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public int settleSalaryOnPaid(Long runId, String runNo) {
        if (runId == null) {
            return 0;
        }
        List<Long> items = bgOccupyMapper.selectOccupiedItemsByBiz(BIZ_TYPE_SALARY, runId);
        int done = 0;
        for (Long itemId : items) {
            BigDecimal net = nvl(bgOccupyMapper.sumNetOccupyByItemAndBiz(itemId, runId, BIZ_TYPE_SALARY, false));
            if (net.signum() <= 0) {
                continue;
            }
            writeOff(itemId, net, BIZ_TYPE_SALARY, runId, runNo);
            done++;
        }
        if (done == 0) {
            log.info("薪酬批次 {} 名下无可转已用的净占用（占用落空或已核销），本次不做消耗确认（D-DEC-145）", runNo);
        } else {
            log.info("薪酬批次 {} 发放成功 → 预算占用转已用 {} 行（D-DEC-145／消耗确认时点见 D-DEC-104）", runNo, done);
        }
        return done;
    }

    /**
     * D-DEC-145：计提审批被驳回/撤回 → 等额释放本批次占用，额度即刻回到可申请池。
     * <p>释放量同样按账本净额推导（不写死 {@code salary_amount}）：批次金额被人工改过也不会释放出错。
     * 与核销同为 {@code REQUIRES_NEW}，收尾链路的预算异常不外溢成审批失败。
     * 注意：本方法在「占用与驳回同处一个未提交事务」的极端场景（零节点直达结束的薪酬计提流）下
     * 读不到未提交的 OCCUPY 流水，此时只留一行 info，不做补偿性猜测。
     *
     * @return 实际释放的预算行数
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public int releaseSalaryOccupy(Long runId, String runNo) {
        if (runId == null) {
            return 0;
        }
        List<Long> items = bgOccupyMapper.selectOccupiedItemsByBiz(BIZ_TYPE_SALARY, runId);
        int done = 0;
        for (Long itemId : items) {
            BigDecimal net = nvl(bgOccupyMapper.sumNetOccupyByItemAndBiz(itemId, runId, BIZ_TYPE_SALARY, false));
            if (net.signum() <= 0) {
                continue;
            }
            release(itemId, net, BIZ_TYPE_SALARY, runId, runNo);
            done++;
        }
        if (done == 0) {
            log.info("薪酬批次 {} 名下无待释放的净占用（占用落空／占用流水尚未提交），不释放（D-DEC-145）", runNo);
        } else {
            log.info("薪酬批次 {} 计提被驳回 → 释放预算占用 {} 行（D-DEC-145）", runNo, done);
        }
        return done;
    }

    // ---------------- 调整单 ----------------

    /**
     * 调整单必填项服务端校验（与前端 BudgetAdjust 弹窗同一规则）：
     * 类型/方案/原因、至少一条明细、明细的调入预算明细与大于 0 的调整金额；调剂还需调出明细且不得与调入相同。
     * 写入数据库前必须过这道校验：bg_adjust_line 无约束，缺字段会写入半张调整单，审批时按空 item 更新额度直接抛 SQL 异常。
     */
    private void assertAdjustRequired(BgAdjust adjust, List<BgAdjustLine> lines) {
        if (adjust == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整单信息不能为空");
        }
        String type = adjust.getAdjustType();
        if (type == null || type.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择调整类型（增拨/调减/调剂）");
        }
        if (!"ADD".equals(type) && !"CUT".equals(type) && !"TRANSFER".equals(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整类型非法: " + type);
        }
        if (adjust.getSchemeId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择预算方案");
        }
        if (bgSchemeMapper.selectById(adjust.getSchemeId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算方案不存在: " + adjust.getSchemeId());
        }
        if (adjust.getReason() == null || adjust.getReason().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写调整原因");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请至少添加一条调整明细");
        }
        int i = 1;
        for (BgAdjustLine line : lines) {
            String tag = "第 " + i + " 行调整明细";
            if (line.getBudgetItemId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, tag + "：请选择调入预算明细");
            }
            if (line.getAmount() == null || line.getAmount().signum() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, tag + "：调整金额必须大于 0");
            }
            if ("TRANSFER".equals(type)) {
                if (line.getOutItemId() == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, tag + "：调剂需选择调出预算明细");
                }
                if (line.getOutItemId().equals(line.getBudgetItemId())) {
                    throw new BizException(ErrorCode.PARAM_INVALID, tag + "：调入与调出不能是同一预算明细");
                }
            }
            i++;
        }
    }

    /**
     * 调整单登记：头（bg_adjust）＋行（bg_adjust_line）两表成对写，Controller 直连无外层事务。
     * 半写会留下"有调整单、一行都没有"的空头单据（ROOT-11 已裁规则：跨表写成对者补边界；自定，无外部依据）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long adjustCreate(BgAdjust adjust, List<BgAdjustLine> lines) {
        assertAdjustRequired(adjust, lines);
        adjust.setId(null);
        adjust.setAdjustNo(nextAdjustNo());
        // 缺陷14(20260918)：bg_adjust.apply_user_id / apply_date 均为 NOT NULL 无默认值，
        // 前端创建弹窗未携带申请人字段 → insert 直接 SQL 异常"Field 'apply_user_id' doesn't have a default value"，
        // 任何用户都无法经 UI 创建预算调整单。此处按当前登录人补齐申请人/部门/申请日期。
        com.fssc.framework.security.LoginUser _cu = com.fssc.framework.security.UserContext.get();
        if (adjust.getApplyUserId() == null && _cu != null) {
            adjust.setApplyUserId(_cu.getUserId());
            adjust.setApplyDeptId(_cu.getDeptId());
        }
        if (adjust.getApplyDate() == null) {
            adjust.setApplyDate(java.time.LocalDate.now());
        }
        adjust.setStatus("DRAFT");
        bgAdjustMapper.insert(adjust);
        Long tenantId = TenantContext.getTenantId();
        for (BgAdjustLine line : lines) {
            line.setId(null);
            line.setAdjustId(adjust.getId());
            line.setTenantId(tenantId); // 自定义 @Insert 不经 MetaObjectHandler，必须显式设置
            bgAdjustLineMapper.insert(line);
        }
        // P4-3 规则：新建写入数据库无旧值，不接入审计（不伪造 before），故此处刻意不写 audit_trail
        return adjust.getId();
    }

    /** 草稿调整单编辑保存：仅 DRAFT 可改；明细行全删全插；主表可改类型/方案/原因 */
    @Transactional(rollbackFor = Exception.class)
    public void adjustUpdate(BgAdjust adjust, List<BgAdjustLine> lines) {
        BgAdjust db = bgAdjustMapper.selectById(adjust.getId());
        if (db == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整单不存在: " + adjust.getId());
        }
        if (!"DRAFT".equals(db.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿状态可编辑，当前: " + db.getStatus());
        }
        // 编辑保存只带改动字段时，用库中现值默认处理后再整体过必填校验（避免半空载荷绕过校验）
        if (adjust.getAdjustType() == null || adjust.getAdjustType().isBlank()) {
            adjust.setAdjustType(db.getAdjustType());
        }
        if (adjust.getSchemeId() == null) {
            adjust.setSchemeId(db.getSchemeId());
        }
        if (adjust.getReason() == null || adjust.getReason().isBlank()) {
            adjust.setReason(db.getReason());
        }
        assertAdjustRequired(adjust, lines);
        // 写路径公司归属校验（与审批一致）
        Long schemeId = adjust.getSchemeId() != null ? adjust.getSchemeId() : db.getSchemeId();
        BgScheme scheme = bgSchemeMapper.selectById(schemeId);
        if (scheme != null) {
            orgGuard.assertVisible(scheme.getCompanyId(), null);
        }
        bgAdjustLineMapper.deleteByAdjustId(adjust.getId());
        Long tenantId = TenantContext.getTenantId();
        for (BgAdjustLine line : lines) {
            line.setId(null);
            line.setAdjustId(adjust.getId());
            line.setTenantId(tenantId);
            if (line.getCreateTime() == null) {
                line.setCreateTime(java.time.LocalDateTime.now());
            }
            bgAdjustLineMapper.insert(line);
        }
        BgAdjust u = new BgAdjust();
        u.setId(adjust.getId());
        u.setAdjustType(adjust.getAdjustType());
        u.setSchemeId(adjust.getSchemeId());
        u.setReason(adjust.getReason());
        bgAdjustMapper.updateById(u);
        // P4-3 审计：单据级字段变更（仅本次实际变化的字段）
        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        if (!java.util.Objects.equals(db.getAdjustType(), u.getAdjustType())) {
            auditBefore.put("adjustType", db.getAdjustType());
            auditAfter.put("adjustType", u.getAdjustType());
        }
        if (!java.util.Objects.equals(db.getSchemeId(), u.getSchemeId())) {
            auditBefore.put("schemeId", db.getSchemeId());
            auditAfter.put("schemeId", u.getSchemeId());
        }
        if (!java.util.Objects.equals(db.getReason(), u.getReason())) {
            auditBefore.put("reason", db.getReason());
            auditAfter.put("reason", u.getReason());
        }
        if (!auditAfter.isEmpty()) {
            auditTrailService.recordUpdate("BUDGET", adjust.getId(), db.getAdjustNo(), "bg_adjust",
                    String.valueOf(adjust.getId()), auditBefore, auditAfter, "编辑预算调整单");
        }
    }

    /** 调整单明细行查询（供查看/编辑弹窗） */
    public List<BgAdjustLine> adjustLines(Long adjustId) {
        return bgAdjustLineMapper.selectByAdjustId(adjustId);
    }

    /** 调整单审批通过：将调整额并入预算（ADD 增拨 / CUT 调减 / TRANSFER 调剂） */
    @Transactional(rollbackFor = Exception.class)
    public void adjustApprove(Long adjustId) {
        BgAdjust adjust = bgAdjustMapper.selectById(adjustId);
        if (adjust == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整单不存在: " + adjustId);
        }
        if (!"DRAFT".equals(adjust.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿调整单可审批，当前: " + adjust.getStatus());
        }
        // ① 写路径公司归属校验：调整单所属方案公司必须等于登录人公司
        if (adjust.getSchemeId() != null) {
            BgScheme scheme = bgSchemeMapper.selectById(adjust.getSchemeId());
            if (scheme != null) {
                orgGuard.assertVisible(scheme.getCompanyId(), null);
            }
        }
        List<BgAdjustLine> lines = bgAdjustLineMapper.selectByAdjustId(adjustId);
        String type = adjust.getAdjustType();
        // P4-3 审计扩面（D 类·预算调整）：先采集本次涉及的预算明细「调整前 adjust_amount」
        java.util.Set<Long> touchedItems = new java.util.LinkedHashSet<>();
        for (BgAdjustLine line : lines) {
            if ("TRANSFER".equals(type) && line.getOutItemId() != null) {
                touchedItems.add(line.getOutItemId());
            }
            touchedItems.add(line.getBudgetItemId());
        }
        java.util.Map<String, Object> budgetBefore = new java.util.LinkedHashMap<>();
        for (Long itemId : touchedItems) {
            if (itemId == null) continue;
            BgItem it = bgItemMapper.selectById(itemId);
            if (it != null) budgetBefore.put(String.valueOf(itemId), it.getAdjustAmount());
        }
        for (BgAdjustLine line : lines) {
            BigDecimal signed = "CUT".equals(type) ? line.getAmount().negate() : line.getAmount();
            if ("TRANSFER".equals(type) && line.getOutItemId() != null) {
                addAmount(line.getOutItemId(), "adjust", line.getAmount().negate());
            }
            addAmount(line.getBudgetItemId(), "adjust", signed);
        }
        BgAdjust update = new BgAdjust();
        update.setId(adjustId);
        update.setStatus("APPROVED");
        bgAdjustMapper.updateById(update);
        // P4-3 审计：状态跃迁（草稿 → 已审批）
        auditTrailService.recordUpdate("BUDGET", adjustId, adjust.getAdjustNo(), "bg_adjust",
                String.valueOf(adjustId),
                java.util.Collections.singletonMap("status", adjust.getStatus()),
                java.util.Collections.singletonMap("status", "APPROVED"), "审批预算调整单");
        // P4-3 审计扩面（D 类·预算调整）：记录各预算明细「调整前后预算」（adjust_amount 增减）
        java.util.Map<String, Object> budgetAfter = new java.util.LinkedHashMap<>();
        for (Long itemId : touchedItems) {
            if (itemId == null) continue;
            BgItem it = bgItemMapper.selectById(itemId);
            if (it != null) budgetAfter.put(String.valueOf(itemId), it.getAdjustAmount());
        }
        if (!budgetAfter.isEmpty()) {
            auditTrailService.recordUpdate("BUDGET", adjustId, adjust.getAdjustNo(), "bg_item",
                    String.valueOf(adjustId), budgetBefore, budgetAfter,
                    "调整额并入预算（明细 adjust_amount 变更）");
        }
    }

    // ---------------- 内部工具 ----------------

    /** 追加占用流水（只增不改） */
    private void appendFlow(Long budgetItemId, String operType, BigDecimal amount,
                            String bizType, Long bizId, String bizNo, String remark) {
        BgOccupy o = new BgOccupy();
        o.setTenantId(TenantContext.getTenantId());
        o.setBudgetItemId(budgetItemId);
        o.setOperType(operType);
        o.setAmount(amount);
        o.setBizType(bizType);
        o.setBizId(bizId);
        o.setBizNo(bizNo);
        o.setOperUserId(UserContext.get() == null ? null : UserContext.get().getUserId());
        o.setRemark(remark);
        o.setCreateTime(LocalDateTime.now());
        bgOccupyMapper.insert(o);
    }

    /** 汇总字段增量更新（occupy/used/adjust） */
    /** 原子汇总更新（P1-1：直接 UPDATE += 增量，避免读-改-写丢失更新；调用方须已持有行锁） */
    private void addAmount(Long budgetItemId, String field, BigDecimal delta) {
        int rows = switch (field) {
            case "occupy" -> bgItemMapper.addOccupy(budgetItemId, delta);
            case "used" -> bgItemMapper.addUsed(budgetItemId, delta);
            case "adjust" -> bgItemMapper.addAdjust(budgetItemId, delta);
            default -> throw new IllegalArgumentException("unknown field: " + field);
        };
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + budgetItemId);
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String nextAdjustNo() {
        try {
            return numberRuleService.next("BUDGET_ADJUST");
        } catch (Exception e) {
            return "ADJ" + System.currentTimeMillis();
        }
    }

    // 预算执行明细
    private static java.math.BigDecimal orZero(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    /**
     * R5-D-264：执行率单点计算（%），明细页/其它读面统一取该规则。
     *
     * <p>分子 = occupy_amount + used_amount（与「可用余额」列同轴：总额＋调整−占用−核销）；
     * 分母 = total_amount + adjust_amount；scale=1 HALF_UP；<b>不封顶</b>（>100 即超支）；
     * 分母 &lt;= 0 时返回 null（无预算基数，前端显示「—」）。</p>
     */
    static java.math.BigDecimal execRateOf(com.fssc.budget.entity.BgItem bi) {
        if (bi == null) {
            return null;
        }
        java.math.BigDecimal base = orZero(bi.getTotalAmount()).add(orZero(bi.getAdjustAmount()));
        if (base.signum() <= 0) {
            return null;
        }
        java.math.BigDecimal numerator = orZero(bi.getOccupyAmount()).add(orZero(bi.getUsedAmount()));
        return numerator.multiply(java.math.BigDecimal.valueOf(100))
                .divide(base, 1, java.math.RoundingMode.HALF_UP);
    }

    public java.util.List<java.util.Map<String, Object>> executionSummary(String period, Long deptId) {
        String month = null;
        if (period != null && period.length() >= 6) {
            try { month = String.valueOf(Integer.parseInt(period.substring(4))); } catch (NumberFormatException e) { month = null; /* 期间格式异常按全量规则处理，不报错 */ }
        }
        List<com.fssc.budget.entity.BgItem> items = bgItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.fssc.budget.entity.BgItem>()
                        // R5-D-266：内连存活方案（del_flag=0 自动过滤），孤儿方案行不进执行读数
                        .in(com.fssc.budget.entity.BgItem::getSchemeId, liveSchemeIds())
                        .orderByDesc(com.fssc.budget.entity.BgItem::getId));
        // 名称解析（2026-09-17）：itemName 显示费用项真实名称，并附公司/部门名称供前端筛选展示
        java.util.Map<Long, com.fssc.system.entity.SysCompany> compMap = new java.util.HashMap<>();
        java.util.Map<Long, com.fssc.system.entity.SysDept> deptMap = new java.util.HashMap<>();
        java.util.Map<Long, com.fssc.masterdata.entity.MdExpenseItem> itemMap = new java.util.HashMap<>();
        for (com.fssc.budget.entity.BgItem bi : items) {
            if (bi.getCompanyId() != null && !compMap.containsKey(bi.getCompanyId())) {
                compMap.put(bi.getCompanyId(), sysCompanyMapper.selectById(bi.getCompanyId()));
            }
            if (bi.getDeptId() != null && !deptMap.containsKey(bi.getDeptId())) {
                deptMap.put(bi.getDeptId(), sysDeptMapper.selectById(bi.getDeptId()));
            }
            if (bi.getItemId() != null && !itemMap.containsKey(bi.getItemId())) {
                itemMap.put(bi.getItemId(), mdExpenseItemMapper.selectById(bi.getItemId()));
            }
        }
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (com.fssc.budget.entity.BgItem bi : items) {
            if (deptId != null && !deptId.equals(bi.getDeptId())) continue;
            if (month != null && bi.getBudgetPeriod() != null && bi.getBudgetPeriod() != 0
                    && bi.getBudgetPeriod() != Integer.parseInt(month)) continue;
            java.math.BigDecimal total = orZero(bi.getTotalAmount()).add(orZero(bi.getAdjustAmount()));
            java.math.BigDecimal occupy = orZero(bi.getOccupyAmount());
            java.math.BigDecimal used = orZero(bi.getUsedAmount());
            com.fssc.system.entity.SysCompany comp = bi.getCompanyId() == null ? null : compMap.get(bi.getCompanyId());
            com.fssc.system.entity.SysDept dept = bi.getDeptId() == null ? null : deptMap.get(bi.getDeptId());
            com.fssc.masterdata.entity.MdExpenseItem mi = bi.getItemId() == null ? null : itemMap.get(bi.getItemId());
            String itemName = mi != null
                    ? nvlStr(mi.getItemCode()) + " " + nvlStr(mi.getItemName())
                    : "预算项#" + bi.getId();
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("budgetItemId", bi.getId());
            m.put("itemName", itemName);
            m.put("itemId", bi.getItemId());
            m.put("companyId", bi.getCompanyId());
            m.put("companyName", comp == null ? "" : nvlStr(comp.getCompanyName()));
            m.put("deptId", bi.getDeptId());
            m.put("deptName", dept == null ? "" : nvlStr(dept.getDeptName()));
            m.put("dimKey", bi.getDimKey());
            m.put("period", bi.getBudgetPeriod() == null || bi.getBudgetPeriod() == 0 ? "全年" : String.valueOf(bi.getBudgetPeriod()));
            m.put("total", total);
            m.put("occupy", occupy);
            m.put("writeOff", used);
            m.put("balance", total.subtract(occupy).subtract(used));
            out.add(m);
        }
        return out;
    }

    private String nvlStr(String v) {
        return v == null ? "" : v;
    }
}
