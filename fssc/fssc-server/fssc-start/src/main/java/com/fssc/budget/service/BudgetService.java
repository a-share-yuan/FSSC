// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.audit.service.AuditTrailService;
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
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.DataScope;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.entity.MdItemBudgetMap;
import com.fssc.masterdata.entity.MdBudgetPeriod;
import com.fssc.masterdata.mapper.MdBudgetPeriodMapper;
import com.fssc.masterdata.mapper.MdBudgetTypeMapper;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import com.fssc.masterdata.mapper.MdItemBudgetMapMapper;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.entity.SysDept;
import com.fssc.system.mapper.SysCompanyMapper;
import com.fssc.system.mapper.SysDeptMapper;
import com.fssc.system.service.NumberRuleService;
import com.fssc.workflow.action.BudgetSchemeBizAction;
import com.fssc.workflow.service.WfEngineFacade;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

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
    private static final Set<String> EXPENSE_BIZ_TYPES = Set.of(
            "EXP_CLAIM", "EXP_PAY", "EXP_APPLY", "EXP_LOAN", "EXP_REPAY", "TR_PAY");

    private final BgSchemeMapper bgSchemeMapper;
    private final BgItemMapper bgItemMapper;
    private final BgOccupyMapper bgOccupyMapper;
    private final BgAdjustMapper bgAdjustMapper;
    private final BgAdjustLineMapper bgAdjustLineMapper;
    /** 组织合法性守卫（安全审计①）：读路径公司隔离 + 写路径公司归属校验 */
    private final OrgGuard orgGuard;
    private final NumberRuleService numberRuleService;
    /** 方案 company_id 兜底解析（bg_scheme.company_id NOT NULL） */
    private final SysCompanyMapper sysCompanyMapper;
    /** 执行页名称解析（itemName 显示真实名称，不回退"预算项#id"） */
    private final SysDeptMapper sysDeptMapper;
    private final MdExpenseItemMapper mdExpenseItemMapper;
    /** 费用类型→预算明细映射（主体化解析的兜底来源） */
    private final MdItemBudgetMapMapper mdItemBudgetMapMapper;
    /** 预算期间主数据：期间起止日期与期次归属 */
    private final MdBudgetPeriodMapper mdBudgetPeriodMapper;
    /** 预算类型主数据：项目预算/常规预算 */
    private final MdBudgetTypeMapper mdBudgetTypeMapper;
    /** 预算方案审批流发起（架构红线：业务域禁止直用 Flowable API，一律经门面） */
    private final WfEngineFacade wfEngineFacade;
    /** 审计扩面：单据状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    // 方案与明细

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
     * 预算方案保存：bg_scheme.company_id 为 NOT NULL 且无默认值，而前端保存可能不带 company_id，
     * insert 缺列会直接抛 "Field 'company_id' doesn't have a default value"。
     * 故 company_id 缺失时按当前登录人公司补齐，再退租户首家公司；
     * scheme_code 缺失自动取号、budget_year 缺省当年、scheme_name 必填前置校验，
     * 消除其余会因 NOT NULL 缺列而 500 的路径。
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
        // 规则：新建写入数据库无旧值，不接入审计（不伪造 before），故此处刻意不写 audit_trail
        return scheme.getId();
    }

    /** 公司兜底：登录人公司 → 租户首家公司（id 最小，与 VoucherService.resolveCompanyId 同一规则） */
    private Long defaultCompanyId() {
        LoginUser u = UserContext.get();
        if (u != null && u.getCompanyId() != null) {
            return u.getCompanyId();
        }
        try {
            Long tenantId = TenantContext.getTenantId();
            SysCompany c = sysCompanyMapper.selectOne(
                    new LambdaQueryWrapper<SysCompany>()
                            .eq(SysCompany::getTenantId, tenantId)
                            .orderByAsc(SysCompany::getId)
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
        // 启用必须基于已审批下达(ISSUED)的方案，堵住 DRAFT 直启绕过审批的口子
        BgScheme cur = bgSchemeMapper.selectById(id);
        if (cur == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "预算方案不存在: " + id);
        }
        BgScheme u = new BgScheme();
        u.setId(id);
        if (enable) {
            if (!"ISSUED".equals(cur.getStatus()) && !"APPROVED".equals(cur.getStatus())) {
                throw new BizException(
                        ErrorCode.PARAM_INVALID,
                        "仅已审批下达(ISSUED)的方案可启用，当前状态: " + cur.getStatus() + "，请先提交审批");
            }
            u.setStatus("ACTIVE");
        } else {
            u.setStatus("ISSUED");
        }
        u.setUpdateBy(null);
        bgSchemeMapper.updateById(u);
        // 审计：状态跃迁（启用 ACTIVE / 停用回退 ISSUED）
        auditTrailService.recordUpdate("BUDGET", id, cur.getSchemeCode(), "bg_scheme",
                String.valueOf(id),
                Collections.singletonMap("status", cur.getStatus()),
                Collections.singletonMap("status", u.getStatus()),
                enable ? "启用预算方案" : "停用预算方案");
    }

    // 预算方案审批流与下达

    /**
     * 提交审批：DRAFT → APPROVING，并启动预算方案审批流
     * （wf_business_flow: flow_code=BG_APPROVE / biz_type=BG_SCHEME / model_key=BG_APPROVE，
     * 两级节点：预算管理员初审 → 财务总监终审；启动时自动部署，见 WfEngineFacade.deployBizFlows）。
     * 流程结束由 {@link BudgetSchemeBizAction} 收尾：
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
        // 审计：状态跃迁（草稿 → 审批中）
        auditTrailService.recordUpdate("BUDGET", schemeId, scheme.getSchemeCode(), "bg_scheme",
                String.valueOf(schemeId),
                Collections.singletonMap("status", scheme.getStatus()),
                Collections.singletonMap("status", "APPROVING"), "提交预算方案审批");
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
            // 审计：状态回退（审批中 → 草稿，发起流程失败补偿）
            auditTrailService.recordUpdate("BUDGET", schemeId, scheme.getSchemeCode(), "bg_scheme",
                    String.valueOf(schemeId),
                    Collections.singletonMap("status", "APPROVING"),
                    Collections.singletonMap("status", "DRAFT"), "发起审批失败回退草稿");
            log.error("预算方案 {} 发起 BG_SCHEME 审批流失败: {}", schemeId, ex.getMessage(), ex);
            throw new BizException(ErrorCode.PARAM_INVALID, "发起预算方案审批流失败: " + ex.getMessage());
        }
        log.info("预算方案 {} 已提交审批（DRAFT → APPROVING）", scheme.getSchemeCode());
    }

    /**
     * 下达：置 status=ISSUED 并记录下达时间/人。
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
        // 免审直下达必须换用独立权限点。DRAFT 直达 ISSUED 原本与「提交审批」共用
        // budget:scheme:update——有编辑权的人可以完全绕过审批链。独立权限点后，只有初始化导入
        // 场景的岗位才被授予 budget:scheme:issue，其余一律走审批。
        if ("DRAFT".equals(st)) {
            LoginUser _u = UserContext.get();
            if (_u == null || !_u.hasPerm("budget:scheme:issue")) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "草稿方案不可直接下达（免审直下达需 budget:scheme:issue 权限，仅限初始化导入场景）；请先提交审批");
            }
        }
        // （便宜的那一条）：下达前校验方案下至少有一条额度行——下达一张空方案等于给业务
        // 一个「已下达」的假象，受控单据提交时又查不到预算，问题会推迟到制单环节才暴露。
        Long itemCount = bgItemMapper.selectCount(new LambdaQueryWrapper<BgItem>()
                .eq(BgItem::getSchemeId, schemeId));
        if (itemCount == null || itemCount == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "方案 " + scheme.getSchemeCode() + " 下没有任何预算明细，不可下达；请先录入/导入额度");
        }
        BgScheme upd = new BgScheme();
        upd.setId(schemeId);
        upd.setStatus("ISSUED");
        upd.setIssueTime(LocalDateTime.now());
        upd.setIssueBy(UserContext.get() == null ? null : UserContext.get().getUserId());
        bgSchemeMapper.updateById(upd);
        // 审计：状态跃迁（→ 已下达 ISSUED）
        auditTrailService.recordUpdate("BUDGET", schemeId, scheme.getSchemeCode(), "bg_scheme",
                String.valueOf(schemeId),
                Collections.singletonMap("status", st),
                Collections.singletonMap("status", "ISSUED"), "预算方案下达");
        log.info("预算方案 {} 已下达（{} → ISSUED，下达人={}）",
                scheme.getSchemeCode(), st, upd.getIssueBy());
    }

    /** 存活方案 id 集合（bg_scheme 带 @TableLogic，selectList 自动过滤 del_flag=0），
     *  供执行读数与管理列表内连存活方案；孤儿行（scheme_id 不在此集）分别出只读清单交产品确认。 */
    private List<Long> liveSchemeIds() {
        return bgSchemeMapper.selectList(new LambdaQueryWrapper<BgScheme>().select(BgScheme::getId))
                .stream().map(BgScheme::getId).collect(Collectors.toList());
    }

    @DataScope("预算明细列表数据范围")
    public PageResult<BgItem> itemPage(long pageNum, long pageSize, Long schemeId, Long deptId, Long itemId,
                                       Collection<Long> ids) {
        // 内连存活方案——scheme_id 不在存活方案中的孤儿行不进管理列表
        List<Long> liveSchemes = liveSchemeIds();
        if (liveSchemes.isEmpty()) {
            return PageResult.of(Collections.emptyList(), 0, pageNum, pageSize);
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
        // 按预算明细 id 精确查询，供单据页对"自动映射科目未在前 200 行预载内"的
        // 预算占用预览回填真实额度（否则 avail 误按 0 计 → 大额被误拦）。
        if (ids != null && !ids.isEmpty()) {
            qw.in(BgItem::getId, ids);
        }
        // ③ data_scope 五级：依据登录人 data_scope 注入 公司/部门/本人 过滤（TENANT 不加条件）
        // 部门档走 applyWithNullDept——dept_id=NULL 的公司级下达行对同公司人员可见
        // （若按 dept_id=本人部门 过滤，公司级下达行会被全部滤掉，BUDGET_MANAGER 面板恒 0 行）
        DataScopeKit.applyWithNullDept(qw, BgItem::getCompanyId, BgItem::getDeptId, null);
        qw.orderByAsc(BgItem::getDimKey);
        Page<BgItem> p = bgItemMapper.selectPage(Page.of(pageNum, pageSize), qw);
        // 执行率统一由后端单点计算填充（前端不自算/封顶，与预警页规则互引）
        p.getRecords().forEach(bi -> bi.setExecRate(execRateOf(bi)));
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 单据发起页「预算科目」引用选择规则（申请人可选科目，非预算管理列表）。
     *
     * <p><b>为什么必须另开规则</b>：{@link #itemPage} 走 data_scope 五级过滤，而
     * {@code sys_role.data_scope} 的常见档位是 普通员工 COMMON_EMP＝4（本部门）、
     * 其余岗位多为 5（仅本人）；同时 {@code bg_item.dept_id} 大多为 NULL（预算按公司下达、
     * 未按部门拆）。于是 DEPT 档注入 {@code dept_id = 登录人部门} 直接命中 0 行，SELF 档无本人列
     * 降级本公司——同一个"科目下拉"在不同人手里忽空忽满，制单页红星永远选不出科目。</p>
     *
     * <p><b>本规则的边界（不是放开全租户口）</b>：预算明细属于"引用型主数据"，申请人需要的是
     * <b>法人/公司维度</b>的可选集合，故只按 {@link OrgGuard#enforceCompany()} 收成登录人本公司
     * （集团级账号 companyId 为空＝不限），跨公司依旧隔离；部门维度作为可选过滤参数，
     * 本部门行排在前面（不隐藏他部门行，避免额度被同公司其它部门"看不见地"重复占用）。</p>
     *
     * <p>管理侧列表（{@code budgetApi.itemPage} → {@code /item/page}）保持 {@code @DataScope} 不变
     * （已把其部门档细化为「dept_id=本人部门 OR dept_id IS NULL」），
     * 本方法只服务单据发起页的查询，写操作权限不受影响。</p>
     */
    public PageResult<BgItem> itemPickPage(long pageNum, long pageSize, Long schemeId, Long deptId, Long itemId,
                                           Collection<Long> ids, Integer budgetYear) {
        LambdaQueryWrapper<BgItem> qw = new LambdaQueryWrapper<>();
        // 公司维度硬边界：集团级账号（enforceCompany 返回 null）不加条件，其余一律只认本公司
        Long company = orgGuard.enforceCompany();
        if (company != null) {
            qw.eq(BgItem::getCompanyId, company);
        } else if (itemId == null && (ids == null || ids.isEmpty())) {
            // 无公司且非精确查询（既没选费用项也没按 id 回填）：不给"全租户摊开"的兜底，
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
        // 带出控制方式（HARD/SOFT/WARN），前端预算校验据此分档拦截/索取超支理由
        fillControlTypes(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    // 预算明细解析（占用主体规则）

    /**
     * 解析本行应占用哪条预算明细。
     *
     * <p>映射表 {@code md_item_budget_map} 是全租户单值：按费用项 {@code limit 1} 取一条
     * {@code bg_item} 主键直接用。该主键可能属于其它公司/部门，于是：占用记到别的主体；
     * 制单人在自己的 data_scope 内看不到该行，下拉退化显示原始主键。故以<b>单据主体</b>为准：</p>
     * <ol>
     *   <li>调用方已给 {@code wantedItemId} 且与单据同公司 → 原样使用（尊重人工选择）；</li>
     *   <li>跨公司 → 按费用项在单据主体内重定位；重定位不到则<b>拒绝</b>（不把占用记到别的公司预算线）；</li>
     *   <li>未给 → 按 费用项 + 公司 + 部门 + 业务年度 命中真实预算行，再回退映射表主键（仍要求同公司）。</li>
     * </ol>
     *
     * @param wantedItemId 前端/调用方给定的预算明细主键，可为空
     * @param expenseItemId 费用项 id（{@code md_expense_item.id}，即 {@code bg_item.item_id}）
     * @param companyId 单据公司，可为空（空则不做主体约束）
     * @param deptId 单据部门，可为空（仅作优先级）
     * @param bizYear 单据业务年度（{@code exp_bill.period_year} / 合同签订日期年份），可为空；
     *                       用于在多年度预算行中就近命中，空则不区分年度
     * @return 预算明细主键；{@code null} 表示未能定位（由调用方决定跳过还是提示）
     */
    public Long resolveOccupyItem(Long wantedItemId, Long expenseItemId, Long companyId, Long deptId, Integer bizYear) {
        return resolveOccupyItem(wantedItemId, expenseItemId, companyId, deptId, bizYear, null, true);
    }

    /** 带项目维度的解析：制单页/单据行带项目时按项目归集，不带时行为与旧签名一致 */
    public Long resolveOccupyItem(Long wantedItemId, Long expenseItemId, Long companyId, Long deptId,
                                  Integer bizYear, Long projectId) {
        return resolveOccupyItem(wantedItemId, expenseItemId, companyId, deptId, bizYear, projectId, true);
    }

    /**
     * @param strict true=跨主体且无法重定位时抛异常（提交占用，钱不能记错主体）；
     *               false=返回 null 由调用方跳过（核销/释放等收尾链路，不阻断审批）
     */
    public Long resolveOccupyItem(Long wantedItemId, Long expenseItemId, Long companyId, Long deptId,
                                  Integer bizYear, boolean strict) {
        return resolveOccupyItem(wantedItemId, expenseItemId, companyId, deptId, bizYear, null, strict);
    }

    /**
     * 六参全量解析：{@code projectId} 参与命中，给了项目就只认该项目行与通用行。
     */
    public Long resolveOccupyItem(Long wantedItemId, Long expenseItemId, Long companyId, Long deptId,
                                  Integer bizYear, Long projectId, boolean strict) {
        if (wantedItemId != null) {
            BgItem wanted = bgItemMapper.selectById(wantedItemId);
            if (wanted == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + wantedItemId);
            }
            if (companyId == null || companyId.equals(wanted.getCompanyId())) {
                return wantedItemId;
            }
            Long relocated = matchItem(expenseItemId, companyId, deptId, bizYear, projectId);
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
        Long bySubject = matchItem(expenseItemId, companyId, deptId, bizYear, projectId);
        if (bySubject != null) {
            return bySubject;
        }
        if (expenseItemId == null) {
            return null;
        }
        // 回退：映射表的固定主键，仍要求与单据同公司（跨主体一律不认）
        MdItemBudgetMap map = mdItemBudgetMapMapper.selectOne(
                new LambdaQueryWrapper<MdItemBudgetMap>()
                        .eq(MdItemBudgetMap::getItemId, expenseItemId)
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
        return matchItem(expenseItemId, companyId, deptId, bizYear, null);
    }

    /**
     * 按 费用项 + 公司 + 项目 命中预算明细行。
     *
     * <p>项目维度参与命中：给了 {@code projectId} 时，<b>只认可该项目行与不带项目的通用行</b>
     * （同费用项下其它项目的预算行一律排除，否则项目 A 的支出会占到项目 B 的额度上）；
     * 两者都在时项目行优先（更具体者胜）。未给项目时行为与改造前完全一致。
     */
    private Long matchItem(Long expenseItemId, Long companyId, Long deptId, Integer bizYear, Long projectId) {
        if (expenseItemId == null) {
            return null;
        }
        LambdaQueryWrapper<BgItem> qw = new LambdaQueryWrapper<BgItem>()
                .eq(BgItem::getItemId, expenseItemId)
                .eq(companyId != null, BgItem::getCompanyId, companyId)
                .last("limit 200");
        List<BgItem> rows = bgItemMapper.selectList(qw);
        if (projectId != null) {
            rows = rows.stream()
                    .filter(b -> b.getProjectId() == null || projectId.equals(b.getProjectId()))
                    .collect(Collectors.toList());
        }
        return rows.stream()
                .min(Comparator
                        .comparing((BgItem b) -> projectId != null && projectId.equals(b.getProjectId()) ? 0 : 1)
                        .thenComparing(b -> deptId != null && deptId.equals(b.getDeptId()) ? 0 : 1)
                        .thenComparing(b -> yearGap(b.getBudgetYear(), bizYear))
                        .thenComparing(b -> b.getBudgetPeriod() == null ? 0 : Math.abs(b.getBudgetPeriod()))
                        .thenComparing(b -> b.getBudgetYear() == null ? 0 : -b.getBudgetYear())
                        .thenComparing(BgItem::getDimKey, Comparator.nullsLast(String::compareTo)))
                .map(BgItem::getId).orElse(null);
    }

    /**
     * 预算行年度与单据业务年度的距离：同年 0，跨年取绝对差（缺同年度时退而取最近的同主体行；
     * 若无条件取"年份最大"的行，早期单据的占用会全部记到未来年度上，期间控制失效）；
     * {@code bizYear} 为空（历史单据无会计期间）时一律 0，按最近年度兜底。
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
     * 录入弹窗不传 dim_key，原样落库会留下 dim_key=NULL 的孤儿行，页面「维度键」列为空且去重失效。
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
        // 期间范围以 bg_scheme.period_type 为唯一权威——按年方案统一落 budget_period=0；
        // MONTH 方案保留 0~12（0=全年，页面按「全年」展示）。
        // 界面提交 YEAR、服务判 ANNUAL 的错位让这条规则永不生效，故两值同认（见 normalizePeriodType）。
        String pt = normalizePeriodType(scheme.getPeriodType());
        if ("ANNUAL".equals(pt)) {
            item.setBudgetPeriod(0);
        }
        // 期间主数据接入。带 periodId 时以其年度/期次为准（整数两列降级为派生缓存），
        // 起止日期在占用匹配时优先于裸整数（见 matchItem）。
        if (item.getBudgetPeriodId() != null) {
            MdBudgetPeriod period = mdBudgetPeriodMapper.selectById(item.getBudgetPeriodId());
            if (period == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "预算期间不存在: " + item.getBudgetPeriodId());
            }
            if (period.getPeriodYear() != null && item.getBudgetYear() == null) {
                item.setBudgetYear(period.getPeriodYear());
            }
            if ("ANNUAL".equals(pt)) {
                item.setBudgetPeriod(0);
            } else if (period.getPeriodNo() != null) {
                item.setBudgetPeriod(period.getPeriodNo());
            }
        }
        // 维度「勾了才可填」——未勾选的维度不允许填值，否则维度键里出现方案根本没控制的段，
        // 同一方案下会产生「看着有项目、实际按非项目归集」的混合行。
        assertDimensionAllowed(scheme.getDimension(), "PROJECT", item.getProjectId(), "项目");
        String dimKey = buildDimKey(item);
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

    // 预算校验与占用

    /**
     * 周期类型归一：界面提交的是 {@code YEAR}，服务原先判的是 {@code ANNUAL}，
     * 两条分支永不相等 ⇒「按年方案统一落 period=0」整条规则失效。这里把同义值收敛成一个口径，
     * 调用方不再各自比字符串。未知值按 MONTH（最细粒度，不会把月度行误当年度行）。
     */
    static String normalizePeriodType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MONTH";
        }
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "YEAR", "ANNUAL" -> "ANNUAL";
            case "QUARTER", "QUARTERLY" -> "QUARTER";
            case "MONTH", "MONTHLY" -> "MONTH";
            default -> "MONTH";
        };
    }

    /**
     * 维度键：项目段只在<b>确有项目</b>时拼入。
     * 无条件拼 {@code -PROJECTnull} 会让存量 108 行全部换键、与新行并列成"两条同维度预算"；
     * 只在有项目时追加，既让项目预算各行独立，又与存量键完全兼容。
     */
    static String buildDimKey(BgItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getBudgetYear()).append("-COMPANY").append(item.getCompanyId())
                .append("-DEPT").append(item.getDeptId()).append("-ITEM").append(item.getItemId());
        if (item.getProjectId() != null) {
            sb.append("-PROJECT").append(item.getProjectId());
        }
        return sb.toString();
    }

    /**
     * 维度白名单：方案未勾选的维度不允许填值。
     * {@code dimension} 为空视为未配置（历史方案），不拦——否则存量方案一编辑就报错。
     */
    private static void assertDimensionAllowed(String dimension, String code, Object value, String label) {
        if (value == null || dimension == null || dimension.isBlank()) {
            return;
        }
        for (String part : dimension.split(",")) {
            if (part.trim().equalsIgnoreCase(code)) {
                return;
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                "本方案的预算维度未包含「" + label + "」，不允许填写该维度值；请先在方案上勾选该维度");
    }

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
     * 按预算明细主键读可用额度（只读、不加行锁），供付款侧的控制点复核。
     * <p>与 {@link #checkOccupyResult} 的差别：后者是"占用前的准入判定"（超出只出结论、不拦提交），
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
     * 其余状态（DRAFT/APPROVING/CLOSED/脏数据）一律按 HARD 兜底（fail-closed，不因方案未下达而放松控制）。
     */
    public String controlTypeOf(BgItem item) {
        if (item == null) {
            return "HARD";
        }
        // 映射级档位优先（md_item_budget_map.control_type），为空才回落到方案级。
        // 档位下沉到费用项后，同一方案里「差旅硬控、办公费只提示」这类诉求不必再拆方案。
        String mapped = itemControlTypeOf(item);
        if (mapped != null) {
            return mapped;
        }
        BgScheme scheme = item.getSchemeId() == null ? null : bgSchemeMapper.selectById(item.getSchemeId());
        return resolveControlType(scheme);
    }

    /**
     * 费用项映射级档位：只在映射行<b>同时命中费用项与预算明细</b>时生效。
     * 只按 item_id 取会有「同费用项映射到别的预算行」的串档，故两个键都要对上。
     *
     * @return 合法档位值；无映射行/值为空/非法值一律返回 null（交回方案级判定）
     */
    private String itemControlTypeOf(BgItem item) {
        if (item.getItemId() == null || item.getId() == null) {
            return null;
        }
        try {
            MdItemBudgetMap map = mdItemBudgetMapMapper.selectOne(new LambdaQueryWrapper<MdItemBudgetMap>()
                    .eq(MdItemBudgetMap::getItemId, item.getItemId())
                    .eq(MdItemBudgetMap::getBudgetItemId, item.getId())
                    .last("limit 1"));
            if (map == null || map.getControlType() == null || map.getControlType().isBlank()) {
                return null;
            }
            String ct = map.getControlType().trim().toUpperCase();
            return switch (ct) {
                case "HARD", "SOFT", "WARN", "NONE" -> ct;
                default -> null;
            };
        } catch (Exception ex) {
            log.debug("读取费用项映射档位失败，回落方案级: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * controlTypeOf 的方案侧判定拆出为纯函数，供 pick 接口按批量取回的方案同源解析
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

    /** 批量带出控制方式（一次 selectBatchIds，避免 pick 页 200 行 N+1 反查方案）。 */
    private void fillControlTypes(List<BgItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<Long> schemeIds = new HashSet<>();
        for (BgItem it : items) {
            if (it.getSchemeId() != null) {
                schemeIds.add(it.getSchemeId());
            }
        }
        Map<Long, BgScheme> byId = new HashMap<>();
        if (!schemeIds.isEmpty()) {
            for (BgScheme s : bgSchemeMapper.selectBatchIds(schemeIds)) {
                byId.put(s.getId(), s);
            }
        }
        for (BgItem it : items) {
            it.setControlType(resolveControlType(it.getSchemeId() == null ? null : byId.get(it.getSchemeId())));
        }
        // 映射级档位覆盖方案级（一次批量取，避免 200 行 N+1）。
        // 与 itemControlTypeOf 同一判定：费用项 + 预算明细双键命中才生效。
        Set<Long> itemIds = new HashSet<>();
        for (BgItem it : items) {
            if (it.getItemId() != null) {
                itemIds.add(it.getItemId());
            }
        }
        if (itemIds.isEmpty()) {
            return;
        }
        Map<String, String> mappedTier = new HashMap<>();
        for (MdItemBudgetMap m : mdItemBudgetMapMapper.selectList(new LambdaQueryWrapper<MdItemBudgetMap>()
                .in(MdItemBudgetMap::getItemId, itemIds))) {
            if (m.getBudgetItemId() == null || m.getControlType() == null || m.getControlType().isBlank()) {
                continue;
            }
            String ct = m.getControlType().trim().toUpperCase();
            if (ct.equals("HARD") || ct.equals("SOFT") || ct.equals("WARN") || ct.equals("NONE")) {
                mappedTier.put(m.getItemId() + ":" + m.getBudgetItemId(), ct);
            }
        }
        if (mappedTier.isEmpty()) {
            return;
        }
        for (BgItem it : items) {
            String hit = mappedTier.get(it.getItemId() + ":" + it.getId());
            if (hit != null) {
                it.setControlType(hit);
            }
        }
    }

    /**
     * 预算<b>准入位</b>判定：只出「超出」结论与金额，不拦提交。
     * <p>档位语义不变（HARD/SOFT/WARN 仍由 {@code bg_scheme.control_type} 决定），
     * 真实扣减校验留在 {@link #occupy} 内按档位执行——HARD 超预算照样拒绝占用。
     * <p>为何准入位不拦：{@link #controlTypeOf} 对未下达/未启用方案与脏值一律按 HARD 兜底，
     * 而准入校验又常被传 {@code hard=true}（资本性支出发起即校验），若此处也拦就成了「发起即失败」，
     * 单据连提交机会都没有，预算校验成了配置缺失的替罪羊。
     * <p>超出不得静默：结论里带超出额，WARN 日志记录，扣减环节另有 {@code bg_occupy.remark} 记录。
     *
     * @param budgetItemId 预算明细（bg_item.id），不存在仍按参数错误抛出（不是预算问题）
     * @param amount 本次申请额
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
            log.warn("预算准入判定放行：明细 {}（公司 {} / 年度 {}）控制方式 {} 可用 {} < 本次 {}，超出 {}",
                    budgetItemId, item.getCompanyId(), item.getBudgetYear(), controlType, avail, apply, overAmount);
        }
        return new OccupyCheckResult(budgetItemId, controlType, avail, apply, over, overAmount);
    }

    /**
     * 预算准入判定结论（超出＝给结论与金额，提交可继续）。
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
     * 控制方式由方案 {@code bg_scheme.control_type} 决定（不写死 hard=true）：
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
        // 会读不到其他并发事务已提交的占用，导致并发超占（同一额度会被多个事务同时占走）。
        BgItem item = bgItemMapper.selectForUpdate(budgetItemId);
        if (item == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + budgetItemId);
        }
        // 冻结行不接新增占用（释放/核销仍放行——冻结的是"再花钱"，不是"清算已发生"）
        if (item.getFreeze() != null && item.getFreeze() == 1) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "预算明细 " + budgetItemId + " 已冻结，不可新增占用；如需解冻请到预算科目页操作");
        }
        // 提交链路一致性校验——表头 occupy_amount vs 全行流水净额（bg_occupy 只增不改＝真相账本）。
        // 最严格的做法是「不等即拒绝」，但 bg_item 表头含无流水对应的历史/导入占用，
        // 双向硬拦会把这类历史行全部锁死——故按方向分级：
        //   ① 流水净额 > 表头占用（可用被高估，继续放行将按虚高额度出款）＝危险方向，硬拦＋error 记录；
        //   ② 表头占用 > 流水净额（历史背离，可用被低估，偏保守）＝放行但 error 记录，交人工按流水判定。
        // 历史行按流水重算后，可收紧为严格双向相等校验。
        BigDecimal flowNetAll = nvl(bgOccupyMapper.sumNetOccupyByItem(budgetItemId));
        BigDecimal headOccupyNow = nvl(item.getOccupyAmount());
        if (flowNetAll.compareTo(headOccupyNow) > 0) {
            log.error("提交链路一致性闸：预算行 {} 流水净占用 {} > 表头占用 {}（可用将被高估），拒绝本次占用并留痕",
                    budgetItemId, flowNetAll, headOccupyNow);
            throw new BizException(ErrorCode.PARAM_INVALID, "预算占用账实不一致（流水净占用大于表头，可用余额将被高估），"
                    + "为防超占已拒绝提交；请联系管理员核对预算行 " + budgetItemId);
        }
        if (flowNetAll.compareTo(headOccupyNow) < 0) {
            log.error("提交链路一致性提示：预算行 {} 表头占用 {} > 流水净占用 {}（历史背离/无流水存量行，可用被低估），"
                    + "本次放行但留痕，待人工按流水判定重算", budgetItemId, headOccupyNow, flowNetAll);
        }
        BigDecimal avail = available(item);
        String remark = null;
        if (amount.compareTo(avail) > 0) {
            // 该预算明细「下达额 total_amount=0.00」时任何金额都算超支，而 WARN/SOFT 方案会放行；
            // 若与"真超支"共用一句备注，事后无从区分「额度确实被突破」和「这个科目压根没下达过预算」。
            // 故备注按成因分开记录。（零下达科目是否应当硬拦＝产品规则，需产品确认，此处不擅自改判定。）
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
                case "NONE" -> {
                    // 映射级显式"不控制"——放行且不写超支备注（写了会被当成已发生的超支留痕）
                    log.info("预算行 {} 费用项映射档位为 NONE（不控制），本次 {} 超可用 {} 仍放行",
                            budgetItemId, amount, avail);
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
     * 释放（撤销占用，事务）。行锁防止与占用/核销交错。
     * <p><b>占用/核销互斥</b>：若按调用方给的金额直接扣减
     * {@code bg_item.occupy_amount}，去重就全靠调用方保证——同一单据被反审批回退
     * 两次，会把「已经转成已用」的那一笔再冲一遍占用，形成
     * 「同一笔既在 occupy_amount 又在 used_amount」的双记，可用额被压成负数，
     * 该预算线此后对任何正金额都必拦。
     * 故与 {@link #writeOff} 同源：释放额以 {@code bg_occupy} 账本上
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
                log.info("预算行 {} 上单据 {}（{}）已无剩余占用（净额 {}），跳过本次释放（防占用/已用双记）",
                        budgetItemId, bizNo, bizType, net);
                return;
            }
            if (take.compareTo(net) > 0) {
                log.info("预算行 {} 单据 {} 释放 {} 超出其净占用 {}，按净占用截断",
                        budgetItemId, bizNo, take, net);
                take = net;
            }
        }
        appendFlow(budgetItemId, "RELEASE", take.negate(), bizType, bizId, bizNo, null);
        addAmount(budgetItemId, "occupy", take.negate());
    }

    // 还款冲减借款占用

    /** 还款冲减/回补流水的业务类型标记（EXP_REPAY） */
    public static final String BIZ_TYPE_REPAY = "EXP_REPAY";

    /** 借款占用流水的业务类型标记 */
    public static final String BIZ_TYPE_LOAN = "EXP_LOAN";

    /**
     * 单据类型 → 预算占用流水 biz_type。还款/借款的占用若统一记成 EXP_CLAIM，
     * {@code bg_occupy} 里 EXP_LOAN／EXP_REPAY 就永远没有流水，占用台账按业务类型读数全部失真。
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
     * 还款单对「借款单既有占用」的等额冲减（铁律：只增不改）。
     *
     * <p><b>为什么不是 occupy 也不是 release</b>：还款在业务上是资金回流，预算占用应当被<b>释放</b>，
     * 而不是再占用一次；否则借款单的原始占用仍在、还款单又记一笔，同一笔借款被双重占用。
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
     * @param loanBillId 被冲减占用的借款单 id（exp_bill 主键）
     * @param amount 本次还款金额（冲减上限）
     * @param repayBillNo 还款单号（去重判据 + 台账可读性）
     * @param candidateItems 还款行解析出的预算科目（与借款单曾占用的科目取并集后逐行冲减），可为空
     * @param remark 流水备注
     * @return 实际冲减金额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal offsetLoanOccupy(Long loanBillId, BigDecimal amount, String repayBillNo,
                                       Collection<Long> candidateItems, String remark) {
        if (loanBillId == null || amount == null || amount.signum() <= 0
                || repayBillNo == null || repayBillNo.isBlank()) {
            return BigDecimal.ZERO;
        }
        LinkedHashSet<Long> items = new LinkedHashSet<>();
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
            items.addAll(loanItems.stream().filter(Objects::nonNull).toList());
        }
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        // 已净冲减 = 已冲减(RELEASE) − 已回补(OCCUPY)：驳回/撤回后重提必须能再冲一次，
        // 故判据取净额而非 RELEASE 总额（否则重提会被判成「已足额冲减」而静默跳过）。
        BigDecimal already = BigDecimal.ZERO;
        for (Map<String, Object> row : asList(bgOccupyMapper
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
     * 还款单驳回/撤回：把本还款单已做的冲减<b>等额回补</b>（释放钩子的反向环节）。
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
        for (Map<String, Object> row : asList(bgOccupyMapper
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
     * 结清对齐（释放钩子的「结清」环节）：把本还款单对某借款单的<b>净冲减额</b>
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
                                      Collection<Long> candidateItems, String remark) {
        if (loanBillId == null || repayBillNo == null || repayBillNo.isBlank() || exactAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal net = BigDecimal.ZERO;
        for (Map<String, Object> row : asList(bgOccupyMapper
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

    private static List<Map<String, Object>> asList(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private static List<Long> asListLong(List<Long> rows) {
        return rows == null ? List.of() : rows.stream().filter(Objects::nonNull).toList();
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
     * 占用转出量按 {@code bg_occupy} 账本推导「本单据(biz_id) 在该预算行的净占用」，
     * 不用 {@code bg_item.occupy_amount}（全预算行汇总）截断：按汇总截断时，
     * 一张单据核销会把同预算行上其它单据的占用一并转走并记到本单已用，
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
            log.info("预算行 {} 上单据 {}（{}）已无剩余占用（历史占用 {}），跳过本次核销（防重复核销虚增已用）",
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
     * 单据反审批退回可编辑态时，已核销转出的已用额度必须等额回冲，否则
     * 「可用额度」被幽灵占用，重提时预算校验必然误拦。
     * <p><b>去重</b>：若把去重交给调用方——同一 bizId 重放回冲
     * 会把已用额记成负数，与本行真实占用互相错身，最终留下
     * 「同一笔既占用又核销」的双记。
     * 故按汇总侧不变量处理：回冲额以本行 {@code used_amount} 为上限，已用 ≤ 0 即整体跳过；
     * 「账本净额 vs 汇总列」的日终对账另作独立作业，本方法不擅自改账。
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
            log.info("预算行 {} 已用额为 {}，单据 {}（{}）无已核销可回冲，跳过（防重复回冲记负已用）",
                    budgetItemId, used, bizNo, bizType);
            return;
        }
        if (take.compareTo(used) > 0) {
            log.info("预算行 {} 单据 {} 回冲核销 {} 超出本行已用 {}，按已用截断",
                    budgetItemId, bizNo, take, used);
            take = used;
        }
        appendFlow(budgetItemId, "RELEASE", take.negate(), bizType, bizId, bizNo, "反审批/退回修改回冲已核销预算");
        addAmount(budgetItemId, "used", take.negate());
    }

    // 消耗确认时点

    /**
     * 单行「消耗确认」入参。
     *
     * @param budgetItemId 行上显式选择的预算科目（bg_item 主键），可为空
     * @param expenseItemId 费用项 id（budgetItemId 为空时按 费用项+主体 就近解析，与提交占用同一规则）
     * @param amount 本行确认消耗的金额
     */
    public record SettleLine(Long budgetItemId, Long expenseItemId, BigDecimal amount) {
    }

    /**
     * 预算「占用 → 已用（消耗）」的确认时点＝<b>实际付款成功</b>（或无现金环节的结清），
     * <b>不是审批通过</b>。
     *
     * <p>若在审批通过时（EXP_CLAIM/EXP_PAY 单据收尾、TR_PAY 批次审批收尾三处）就转已用，
     * 「款未付、票未得」的费用会提前计进 {@code bg_item.used_amount}：预算执行报表提前报满，
     * 而后续付款失败/退票时这笔消耗没人回冲（{@code reverseWriteOff} 只覆盖反审批一路）。
     * 故审批通过只保留占用（OCCUPIED，额度继续被本单预留住，不会失控），
     * 钱真的出去（{@code PayBatchService.execute} 打款成功）才转已用。</p>
     *
     * <p><b>去重</b>：本单据名下已存在任一条执行核销流水（{@link BgOccupyMapper#countWriteOffForBill}，
     * 规则限费用/付款/资金三域、biz_id=exp_bill 主键）→ 整单跳过，重放事件/重试付款不会二次转消耗。
     * 与 {@code offsetLoanOccupy}/{@code alignLoanOccupy} 同为「按台账净额判重、只补差额」的风格。</p>
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
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public int settleOccupyOnPaid(Long billId, String billNo, String billType, Long companyId, Long deptId,
                                  Integer periodYear, Collection<SettleLine> lines) {
        if (billId == null || lines == null || lines.isEmpty()) {
            return 0;
        }
        // 单据类型校验：只有"真的花掉钱"的单据才转消耗。
        // 借款单（LOAN）放款、还款单（REPAY）收款、事前申请（APPLY）都各有自己的账环节——
        // 借款占用由还款 offsetLoanOccupy 冲减或由报销单接续，此处再转一次已用＝同一笔钱计两次消耗。
        String bt = billType == null ? "" : billType.trim().toUpperCase();
        if ("LOAN".equals(bt) || "REPAY".equals(bt) || "APPLY".equals(bt)) {
            log.info("单据 {}（{}）不属于消耗型单据，不做占用转已用", billNo, bt);
            return 0;
        }
        String bizType = occupyBizTypeOf(billType);
        if (bgOccupyMapper.countWriteOffForBill(billId) > 0) {
            log.info("单据 {}（{}）已发生过预算执行核销，跳过本次消耗确认（幂等）", billNo, bizType);
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
                // 定位不到科目就如实记录，不臆造预算行（与提交占用同一规则）
                log.warn("单据 {} 行（费用项 {}）未解析到预算科目（显式/费用项映射均未命中），跳过消耗确认",
                        billNo, line.expenseItemId());
                continue;
            }
            writeOff(itemId, line.amount(), bizType, billId, billNo);
            done++;
        }
        log.info("单据 {}（{}）付款/结清 → 预算占用已转已用 {} 行（转换时点已从审批通过钩子后移）",
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

    // 薪酬预算占用

    /** 薪酬域占用流水的业务类型键（biz_id = gl_salary_run.id，与费用域单据主键空间不同，故一律按本键归户） */
    public static final String BIZ_TYPE_SALARY = "GL_SALARY";

    /**
     * 薪酬<b>计提</b>即占用预算 —— 人工成本也要进预算。
     *
     * <p>薪酬费用项在 {@code md_item_budget_map} 里可能映射到并不存在的 {@code bg_item} 行，
     * 或该主体下根本没有薪酬预算明细，此时解析会走到「占用落空」分支：本方法如实记录
     * WARN 而不静默通过 —— 薪酬科目是否单独建预算线、走哪条控制方式＝待确认项。</p>
     *
     * <p><b>规则与费用域一致</b>：占用量取计提凭证的成本额（{@code VoucherService.generateSalaryVoucher}
     * 借 6602 的金额＝{@code salary_amount} 应发工资总额，扣除/社保/个税是应发内部的代扣，不重复占额度）；
     * 科目解析复用 {@link #resolveOccupyItem}(wanted=null, strict=false)——先按 费用项+公司+部门+年度 命中真实预算行，
     * 再回退映射表（仍要求同主体），定位不到就返回 null 交由调用方记录，<b>绝不臆造预算行</b>。</p>
     *
     * <p><b>超支只记录不阻断</b>：{@code bg_scheme.control_type=HARD} 且额度不足时，费用域是直接拒提；
     * 故此处对硬控超支只记 WARN 且不写占用流水，是否改为硬拦需产品确认。</p>
     *
     * <p><b>去重</b>：本批次名下已存在 OCCUPY 流水（{@link BgOccupyMapper#selectOccupiedItemsByBiz}）即整体跳过，
     * 重放核算/重试起流不会二次占额。事务传播 REQUIRED：与薪酬核算同生共死，
     * {@code wfEngineFacade.start} 抛错回滚时占用流水一并回滚，不留幽灵占用。</p>
     *
     * @param expenseItemId 薪酬费用项（{@code md_expense_item.id}），为空表示未配置薪酬预算科目规则
     * @param bizYear 预算年度（从 {@code gl_salary_run.period} yyyyMM 前 4 位取）
     * @return 实际占用的预算明细主键；{@code null}＝未占用（已占过 / 定位不到科目 / 硬控超支记录）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long occupySalaryBudget(Long runId, String runNo, Long expenseItemId, Long companyId, Long deptId,
                                   Integer bizYear, BigDecimal amount) {
        if (runId == null) {
            return null;
        }
        if (amount == null || amount.signum() <= 0) {
            log.info("薪酬批次 {} 应发金额为 {}，无额度可占用", runNo, amount);
            return null;
        }
        if (!bgOccupyMapper.selectOccupiedItemsByBiz(BIZ_TYPE_SALARY, runId).isEmpty()) {
            log.info("薪酬批次 {} 名下已有占用流水，跳过重复占用（幂等）", runNo);
            return null;
        }
        Long itemId = resolveOccupyItem(null, expenseItemId, companyId, deptId, bizYear, false);
        if (itemId == null) {
            log.warn("薪酬批次 {} 应发 {} 未定位到预算科目（费用项 {}／公司 {}／年度 {} 下无预算明细行，" +
                    "映射表指向的预算明细不存在）——占用落空，本批次不计入预算执行，不臆造科目",
                    runNo, amount, expenseItemId, companyId, bizYear);
            return null;
        }
        BgItem item = bgItemMapper.selectById(itemId);
        BigDecimal avail = available(item);
        if (amount.compareTo(avail) > 0 && "HARD".equals(controlTypeOf(item))) {
            log.warn("薪酬批次 {} 超预算未占用：预算明细 {}（公司 {} / 年度 {}）可用 {}，本次应发 {}，方案为硬控——" +
                    "本版只留痕不阻断薪酬核算，人工成本超支是否硬拦＝待产品确认",
                    runNo, itemId, companyId, bizYear, avail, amount);
            return null;
        }
        // 预检与占用之间理论上可被并发插队（此刻再撞上 HARD 拦截），故用 catch 兜住：
        // occupy 是本类自调用（不过代理），异常不会把外层事务标成 rollback-only，薪酬核算不受牵连。
        try {
            occupy(itemId, amount, BIZ_TYPE_SALARY, runId, runNo,
                    amount.compareTo(avail) > 0 ? "薪酬计提超支记录（暂不阻断薪酬核算）" : null);
        } catch (BizException ex) {
            log.warn("薪酬批次 {} 预算占用被拒（并发下额度已被占满）：{}，本批次未计入预算执行",
                    runNo, ex.getMessage());
            return null;
        }
        log.info("薪酬批次 {} 计提占用预算：明细 {} 金额 {}（可用余额 {}）——人工成本首次进预算闸口",
                runNo, itemId, amount, avail);
        return itemId;
    }

    /**
     * 薪酬<b>发放成功</b>（GL_SALARY_PAY 末节点通过且凭证已出）才把占用转已用。
     * 计提链通过（POSTED）只保留占用——那时尚未付出一分钱，与费用域消耗确认时点同一规则。
     *
     * <p>逐行取「本批次在该预算行的净占用」{@link BgOccupyMapper#sumNetOccupyByItemAndBiz}（跨类型关闭：
     * 薪酬 biz_id 与 exp_bill 主键会撞号），有净占用才核销，重放发放自动归零跳过。</p>
     *
     * <p>{@code REQUIRES_NEW}：预算侧异常（明细被删、行锁等待）只回滚本次消耗确认，
     * 绝不把已办结的发放凭证与状态回写一起带回滚。</p>
     *
     * @return 实际确认消耗的预算行数（0＝无占用 / 已核销过）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
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
            log.info("薪酬批次 {} 名下无可转已用的净占用（占用落空或已核销），本次不做消耗确认", runNo);
        } else {
            log.info("薪酬批次 {} 发放成功 → 预算占用转已用 {} 行", runNo, done);
        }
        return done;
    }

    /**
     * 计提审批被驳回/撤回 → 等额释放本批次占用，额度即刻回到可申请池。
     * <p>释放量同样按账本净额推导（不写死 {@code salary_amount}）：批次金额被人工改过也不会释放出错。
     * 与核销同为 {@code REQUIRES_NEW}，收尾链路的预算异常不外溢成审批失败。
     * 注意：本方法在「占用与驳回同处一个未提交事务」的极端场景（零节点直达结束的薪酬计提流）下
     * 读不到未提交的 OCCUPY 流水，此时只留一行 info，不做补偿性猜测。
     *
     * @return 实际释放的预算行数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
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
            log.info("薪酬批次 {} 名下无待释放的净占用（占用落空／占用流水尚未提交），不释放", runNo);
        } else {
            log.info("薪酬批次 {} 计提被驳回 → 释放预算占用 {} 行", runNo, done);
        }
        return done;
    }

    // 预提/待摊的预算「申请占用」

    /** 费用预提的占用流水业务类型＝流程 bizType（GL_ACCRUAL），与费用域 EXP_* 不互认，biz_id 不会撞号 */
    public static final String BIZ_TYPE_ACCRUAL = "GL_ACCRUAL";
    /** 费用待摊的占用流水业务类型 */
    public static final String BIZ_TYPE_DEFER = "GL_DEFER";

    /**
     *
     * <p><b>为什么是「申请占用」而不是「预算执行」</b>：业界预算控制规则把预算影响类型
     * 分为三类 —— ①<b>申请占用</b>「一般对应业务申请单……费用并没有实际执行」，「是对预算数的扣减」；
     * ②<b>预算执行</b>「费用报销时，费用实际发生」；③<b>预算冲回</b>「一般对应业务发生的冲回/负数」。
     * 同册《维度汇总预算执行分析》给出恒等式：<br>
     * {@code 实际执行 = 申请占用 − 申请冲回 + 预算执行 − 执行冲回}；
     * {@code 可用预算 = 调整后预算数 − 实际执行}。<br>
     * 预提是「费用已确认、钱还没付、票可能还没到」，落在 ①；冲销预提是对它的负数，落在 ③。
     * 手册还明确「申请占用和预算执行，对应受控单据将执行预算控制检查」——即申请占用是<b>真控制</b>，
     * 不是只做展示，所以这里的超预算判定与费用域共用同一套档位（HARD 拒 / SOFT 要理由 / WARN 留痕）。
     * 本方法的 {@code occupy}/{@code release} 把 ① 记成 +occupy、③ 记成 −occupy，
     * 与月度 {@code available() = 总额+调整−净占用−已用} 天然同轴。</p>
     *
     * <p><b>定位不到预算行时不臆造、不阻断</b>：与 {@code occupySalaryBudget} 同一处理——
     * 返回 {@code null} 并 WARN，由调用方把 {@code budget_occupy_status} 记成 {@code NONE}，
     * 让「这张预提没占预算」在单据上看得见，而不是静默当成功。</p>
     *
     * <p><b>超支按方案档位</b>：HARD 直接抛（整张记账单回滚，用户改金额或走预算调整）；
     * SOFT 需 {@code overrunReason}；WARN 留痕放行。</p>
     *
     * <p><b>去重</b>：本单名下已有 OCCUPY 流水即跳过（重放不二次占额）。</p>
     *
     * @param bizType        占用流水业务类型，只接受 {@link #BIZ_TYPE_ACCRUAL} / {@link #BIZ_TYPE_DEFER}
     * @param expenseItemId  费用项目（md_expense_item.id），可空；空且未给预算明细时按映射表兜底
     * @param companyId      记账公司（决定预算主体）
     * @param deptId         承担部门，可空
     * @param bizYear        预算年度，取会计期间前 4 位
     * @param amount         本次预提/待摊金额
     * @param overrunReason  软控超支理由，可空
     * @return 实际占用的预算明细主键；{@code null}＝未占用（金额非正 / 已占过 / 定位不到预算行）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long occupyAccrualBudget(String bizType, Long bizId, String bizNo, Long expenseItemId,
                                    Long companyId, Long deptId, Integer bizYear, BigDecimal amount,
                                    String overrunReason) {
        if (!BIZ_TYPE_ACCRUAL.equals(bizType) && !BIZ_TYPE_DEFER.equals(bizType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预提/待摊占用只接受 GL_ACCRUAL / GL_DEFER，实际 " + bizType);
        }
        if (bizId == null) {
            return null;
        }
        if (amount == null || amount.signum() <= 0) {
            log.info("预提/待摊 {} 金额为 {}，无额度可占用", bizNo, amount);
            return null;
        }
        if (!bgOccupyMapper.selectOccupiedItemsByBiz(bizType, bizId).isEmpty()) {
            log.info("预提/待摊 {} 名下已有占用流水，跳过重复占用（幂等）", bizNo);
            return null;
        }
        // strict=false：定位不到预算行的成因归调用方（预提不一定落在已建预算的科目上），
        // 不能让「没建预算」变成「不能预提」——但必须留痕，不能静默放行。
        Long itemId = resolveOccupyItem(null, expenseItemId, companyId, deptId, bizYear, false);
        if (itemId == null) {
            log.warn("预提/待摊 {} 金额 {} 未定位到预算明细（费用项目 {}／公司 {}／年度 {} 下无可用预算行）——" +
                            "本次不计入预算占用，单据 budget_occupy_status 记 NONE，需人工确认是否补建预算",
                    bizNo, amount, expenseItemId, companyId, bizYear);
            return null;
        }
        occupy(itemId, amount, bizType, bizId, bizNo, overrunReason);
        log.info("预提/待摊 {} 申请占用预算：明细 {} 金额 {}（业务类型 {}）", bizNo, itemId, amount, bizType);
        return itemId;
    }

    /**
     * 预提/待摊<b>「预算冲回」</b>：驳回/撤回/红字冲销时等额释放本单占用（「关闭释放」腿）。
     *
     * <p>参与调用方事务：与「记账单置 REJECTED/REVERSED」同生共死——冲销后凭证已红字反向，
     * 而预算还占着（或反之）会让账与预算两本账对不上。收尾链路（流程已结束）请改用
     * {@link #releaseAccrualOccupyIsolated}。</p>
     *
     * @return 实际释放的预算行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int releaseAccrualOccupy(String bizType, Long bizId, String bizNo) {
        return doReleaseAccrual(bizType, bizId, bizNo, "冲回");
    }

    /**
     * 同 {@link #releaseAccrualOccupy}，但独立事务（{@code REQUIRES_NEW}）。
     *
     * <p>用于审批驳回/撤回的收尾：流程已经结束，预算侧异常（行锁等待、明细被删）只该留下
     * 一条 error 供人工补偿，绝不能把「流程已结束」这个事实回滚掉。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int releaseAccrualOccupyIsolated(String bizType, Long bizId, String bizNo) {
        return doReleaseAccrual(bizType, bizId, bizNo, "驳回");
    }

    private int doReleaseAccrual(String bizType, Long bizId, String bizNo, String cause) {
        if (bizId == null) {
            return 0;
        }
        List<Long> items = bgOccupyMapper.selectOccupiedItemsByBiz(bizType, bizId);
        int done = 0;
        for (Long itemId : items) {
            BigDecimal net = nvl(bgOccupyMapper.sumNetOccupyByItemAndBiz(itemId, bizId, bizType, false));
            if (net.signum() <= 0) {
                continue;
            }
            release(itemId, net, bizType, bizId, bizNo);
            done++;
        }
        if (done == 0) {
            log.info("预提/待摊 {} 名下无待冲回的净占用（未占预算／已冲回／流水尚未提交），不释放", bizNo);
        } else {
            log.info("预提/待摊 {} {} → 释放预算占用 {} 行", bizNo, cause, done);
        }
        return done;
    }

    // 调整单

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
        // 录入/编辑时就校验调出（调减）方余额，不等审批通过才炸
        assertAdjustAvailable(adjust, lines);
    }

    /**
     * 调整单登记：头（bg_adjust）＋行（bg_adjust_line）两表成对写，Controller 直连无外层事务。
     * 半写会留下"有调整单、一行都没有"的空头单据，故必须在同一事务内成对写入。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long adjustCreate(BgAdjust adjust, List<BgAdjustLine> lines) {
        assertAdjustRequired(adjust, lines);
        adjust.setId(null);
        adjust.setAdjustNo(nextAdjustNo());
        // bg_adjust.apply_user_id / apply_date 均为 NOT NULL 且无默认值，
        // 而创建弹窗不带申请人字段 → insert 直接抛 "Field 'apply_user_id' doesn't have a default value"，
        // UI 上无人能建预算调整单。故按当前登录人补齐申请人/部门/申请日期。
        LoginUser _cu = UserContext.get();
        if (adjust.getApplyUserId() == null && _cu != null) {
            adjust.setApplyUserId(_cu.getUserId());
            adjust.setApplyDeptId(_cu.getDeptId());
        }
        if (adjust.getApplyDate() == null) {
            adjust.setApplyDate(LocalDate.now());
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
        // 规则：新建写入数据库无旧值，不接入审计（不伪造 before），故此处刻意不写 audit_trail
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
        // 编辑保存只带改动字段时，用库中现值兜底后再整体过必填校验（避免半空载荷绕过校验）
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
                line.setCreateTime(LocalDateTime.now());
            }
            bgAdjustLineMapper.insert(line);
        }
        BgAdjust u = new BgAdjust();
        u.setId(adjust.getId());
        u.setAdjustType(adjust.getAdjustType());
        u.setSchemeId(adjust.getSchemeId());
        u.setReason(adjust.getReason());
        bgAdjustMapper.updateById(u);
        // 审计：单据级字段变更（仅本次实际变化的字段）
        Map<String, Object> auditBefore = new LinkedHashMap<>();
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        if (!Objects.equals(db.getAdjustType(), u.getAdjustType())) {
            auditBefore.put("adjustType", db.getAdjustType());
            auditAfter.put("adjustType", u.getAdjustType());
        }
        if (!Objects.equals(db.getSchemeId(), u.getSchemeId())) {
            auditBefore.put("schemeId", db.getSchemeId());
            auditAfter.put("schemeId", u.getSchemeId());
        }
        if (!Objects.equals(db.getReason(), u.getReason())) {
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

    /**
     * 调整单提交审批：DRAFT → APPROVING，发起 {@code BG_ADJUST} 审批流。
     *
     * <p>配置表 {@code wf_process_config} 里 {@code BG_ADJUST → BG_SCHEME} 的路由行早已在盘，
     * 而 {@code BG_ADJUST} 在 Java 侧只出现在注释里——「配置有路由、代码不发起」是另一种形态的配了不用。
     * 这里补齐发起；不新建流程模型（沿用方案那条流的同族收尾件），收尾见
     * {@code BudgetAdjustBizAction} → {@link #applyApprovedAdjust}。
     *
     * <p>提交前就跑一次余额校验（{@link #assertAdjustAvailable}）：等到审批通过才炸，
     * 审批人的时间是白花的，且单子会卡在「已批但改不动」的两难里。
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustSubmit(Long adjustId) {
        BgAdjust adjust = requireAdjust(adjustId);
        if (!"DRAFT".equals(adjust.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿调整单可提交，当前: " + adjust.getStatus());
        }
        List<BgAdjustLine> lines = bgAdjustLineMapper.selectByAdjustId(adjustId);
        if (lines == null || lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整单无明细，不可提交");
        }
        assertAdjustAvailable(adjust, lines);
        BgAdjust upd = new BgAdjust();
        upd.setId(adjustId);
        upd.setStatus("APPROVING");
        bgAdjustMapper.updateById(upd);
        auditTrailService.recordUpdate("BUDGET", adjustId, adjust.getAdjustNo(), "bg_adjust",
                String.valueOf(adjustId),
                Collections.singletonMap("status", adjust.getStatus()),
                Collections.singletonMap("status", "APPROVING"), "提交预算调整审批");
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
        try {
            String instId = wfEngineFacade.start("BG_ADJUST", adjustId, userId,
                    "预算调整审批-" + adjust.getAdjustNo(), null);
            if (instId != null) {
                BgAdjust link = new BgAdjust();
                link.setId(adjustId);
                link.setProcInstId(instId);
                bgAdjustMapper.updateById(link);
            }
        } catch (Exception ex) {
            BgAdjust back = new BgAdjust();
            back.setId(adjustId);
            back.setStatus("DRAFT");
            bgAdjustMapper.updateById(back);
            log.error("预算调整单 {} 发起 BG_ADJUST 审批流失败，已回退草稿: {}", adjust.getAdjustNo(), ex.getMessage(), ex);
            throw new BizException(ErrorCode.PARAM_INVALID, "发起预算调整审批失败: " + ex.getMessage());
        }
        log.info("预算调整单 {} 已提交审批（DRAFT → APPROVING）", adjust.getAdjustNo());
    }

    /** 撤回审批中的调整单：流程撤回 + 状态回草稿，可改后重提 */
    @Transactional(rollbackFor = Exception.class)
    public void adjustWithdraw(Long adjustId) {
        BgAdjust adjust = requireAdjust(adjustId);
        if (!"APPROVING".equals(adjust.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅审批中的调整单可撤回，当前: " + adjust.getStatus());
        }
        Long userId = UserContext.get() == null ? null : UserContext.get().getUserId();
        if (adjust.getProcInstId() != null && !adjust.getProcInstId().isBlank()) {
            boolean ok = wfEngineFacade.withdraw(adjust.getProcInstId(), userId);
            if (!ok) {
                throw new BizException(ErrorCode.PARAM_INVALID, "流程撤回失败（可能已有节点办理），请到流程中心处理");
            }
        }
        BgAdjust upd = new BgAdjust();
        upd.setId(adjustId);
        upd.setStatus("DRAFT");
        bgAdjustMapper.updateById(upd);
        auditTrailService.recordUpdate("BUDGET", adjustId, adjust.getAdjustNo(), "bg_adjust",
                String.valueOf(adjustId),
                Collections.singletonMap("status", adjust.getStatus()),
                Collections.singletonMap("status", "DRAFT"), "撤回预算调整审批");
        log.info("预算调整单 {} 已撤回（APPROVING → DRAFT）", adjust.getAdjustNo());
    }

    private BgAdjust requireAdjust(Long adjustId) {
        BgAdjust adjust = bgAdjustMapper.selectById(adjustId);
        if (adjust == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调整单不存在: " + adjustId);
        }
        return adjust;
    }

    /**
     * 调整额可用性校验。
     *
     * <p>改造前只校验「调入≠调出」，调减/调出直接 {@code addAmount(..., -amount)}，
     * 可以把调出方调成负额度（可用＝总额＋调整−占用−已用 < 0），此后该预算线对任何正金额必拦。
     * 现按<b>同一预算行累计需求</b>与可用余额比对（多行调同一行时逐行比会各自通过、合计超支）。
     *
     * <p>口径与 {@code available()} 完全一致（total + adjust − occupy − used），不另算一份。
     */
    private void assertAdjustAvailable(BgAdjust adjust, List<BgAdjustLine> lines) {
        String type = adjust.getAdjustType();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // 调出/调减方 → 累计需求额
        Map<Long, BigDecimal> demand = new LinkedHashMap<>();
        BigDecimal inSum = BigDecimal.ZERO;
        BigDecimal outSum = BigDecimal.ZERO;
        for (BgAdjustLine line : lines) {
            BigDecimal amt = line.getAmount() == null ? BigDecimal.ZERO : line.getAmount();
            inSum = inSum.add(amt);
            if ("TRANSFER".equals(type)) {
                outSum = outSum.add(amt);
                if (line.getOutItemId() != null) {
                    demand.merge(line.getOutItemId(), amt, BigDecimal::add);
                }
            } else if ("CUT".equals(type)) {
                demand.merge(line.getBudgetItemId(), amt, BigDecimal::add);
            }
        }
        // 调剂总额必须为零（等额调剂）：借贷两侧金额不等的调剂单会把预算总量凭空放大/缩小
        if ("TRANSFER".equals(type) && inSum.compareTo(outSum) != 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "调剂单调入合计 " + inSum + " 与调出合计 " + outSum + " 不相等，调剂必须等额（总额为零）");
        }
        for (Map.Entry<Long, BigDecimal> en : demand.entrySet()) {
            Long itemId = en.getKey();
            if (itemId == null) {
                continue;
            }
            BgItem item = bgItemMapper.selectById(itemId);
            if (item == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "调出/调减预算明细不存在: " + itemId);
            }
            BigDecimal avail = available(item);
            if (avail.compareTo(en.getValue()) < 0) {
                String who = "TRANSFER".equals(type) ? "调出方" : "调减方";
                throw new BizException(ErrorCode.PARAM_INVALID,
                        who + "预算明细 " + itemId + " 可用余额不足：可用 " + avail + "，本次需 " + en.getValue()
                                + "；调整后将为负额度，请先增拨或改小调整额");
            }
        }
    }

    /**
     * 审批通过：将调整额并入预算（ADD 增拨 / CUT 调减 / TRANSFER 调剂）。
     *
     * <p>两个入口、一份实现：
     * <ul>
     *   <li>{@link #adjustApprove}：历史单点 REST 直批（DRAFT 起手，兼容既有页面/脚本）；</li>
     *   <li>{@link #applyApprovedAdjust}：{@code BG_ADJUST} 流程办结回调（APPROVING 起手）。</li>
     * </ul>
     * 金额计算与审计只有这一份，防两条腿各算一遍导致同一张单被加两次。
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustApprove(Long adjustId) {
        BgAdjust adjust = requireAdjust(adjustId);
        if (!"DRAFT".equals(adjust.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅草稿调整单可在此直批，当前: " + adjust.getStatus() + "（审批中的单由流程办结回写）");
        }
        // ① 写路径公司归属校验：调整单所属方案公司必须等于登录人公司
        if (adjust.getSchemeId() != null) {
            BgScheme scheme = bgSchemeMapper.selectById(adjust.getSchemeId());
            if (scheme != null) {
                orgGuard.assertVisible(scheme.getCompanyId(), null);
            }
        }
        List<BgAdjustLine> lines = bgAdjustLineMapper.selectByAdjustId(adjustId);
        assertAdjustAvailable(adjust, lines);
        applyAdjustAmounts(adjust, lines, adjust.getStatus());
    }

    /**
     * 流程办结回调：APPROVING → APPROVED 并落额度。
     * 幂等：状态已不是 APPROVING 即跳过（撤回/驳回后再收到事件不会二次加额度）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyApprovedAdjust(Long adjustId) {
        BgAdjust adjust = requireAdjust(adjustId);
        if (!"APPROVING".equals(adjust.getStatus())) {
            log.info("预算调整单 {} 当前状态 {}，跳过流程办结落额度（幂等）", adjust.getAdjustNo(), adjust.getStatus());
            return;
        }
        if (adjust.getSchemeId() != null) {
            BgScheme scheme = bgSchemeMapper.selectById(adjust.getSchemeId());
            if (scheme != null) {
                orgGuard.assertVisible(scheme.getCompanyId(), null);
            }
        }
        List<BgAdjustLine> lines = bgAdjustLineMapper.selectByAdjustId(adjustId);
        assertAdjustAvailable(adjust, lines);
        applyAdjustAmounts(adjust, lines, adjust.getStatus());
    }

    /** 落额度 + 写状态与审计（调用方已完成状态守卫与余额校验） */
    private void applyAdjustAmounts(BgAdjust adjust, List<BgAdjustLine> lines, String fromStatus) {
        Long adjustId = adjust.getId();
        String type = adjust.getAdjustType();
        // 审计：先采集本次涉及的预算明细「调整前 adjust_amount」
        Set<Long> touchedItems = new LinkedHashSet<>();
        for (BgAdjustLine line : lines) {
            if ("TRANSFER".equals(type) && line.getOutItemId() != null) {
                touchedItems.add(line.getOutItemId());
            }
            touchedItems.add(line.getBudgetItemId());
        }
        Map<String, Object> budgetBefore = new LinkedHashMap<>();
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
        // 审计：状态跃迁（草稿/审批中 → 已审批）
        auditTrailService.recordUpdate("BUDGET", adjustId, adjust.getAdjustNo(), "bg_adjust",
                String.valueOf(adjustId),
                Collections.singletonMap("status", fromStatus),
                Collections.singletonMap("status", "APPROVED"), "审批预算调整单");
        // 审计：记录各预算明细「调整前后预算」（adjust_amount 增减）
        Map<String, Object> budgetAfter = new LinkedHashMap<>();
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
        log.info("预算调整单 {}（{}）已生效：{} 行额度已并入预算", adjust.getAdjustNo(), type, lines.size());
    }

    // 实际数导入 / 结余结转 / 冻结

    /**
     * 实际数导入。
     *
     * <p>客户现系统在用的功能：《爱共享预算实际数导入指南》「预算管理 → 实际数导入 → 模板下载 → 导入 →
     * 保存 → 审核即扣减对应预算」，并限定「仅扣减末级费用类型」「未匹配预算的导入记录不进费用统计报表」。
     * 我方此前 {@code bg_item.used_amount} 只能由 {@code writeOff} 累加，全仓无"实际数"入口——
     * 这是交付倒退项，优先级按客户培训面排。
     *
     * <p>实现口径：逐行解析预算行（未给 budgetItemId 时按 费用项+公司+部门 命中），
     * 命中即 {@link #writeOff}(bizId=null) 累加已用并写 WRITE_OFF 流水（只增不改的账本照旧留痕）；
     * 未命中的行进失败清单返回给调用方，<b>不静默丢弃</b>（手册明确这类记录不进统计，等于人间蒸发）。
     *
     * <p>不做取数公式引擎（ACCT/SJdata 那套），只做"导入即扣减"。
     *
     * @param rows 每行 {budgetItemId?, itemId?, companyId?, deptId?, projectId?, budgetYear?, period?, amount}
     * @param bizNo 导入批次号（写进占用流水 biz_no，便于按批撤销与对账）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importActual(List<Map<String, Object>> rows, String bizNo) {
        if (rows == null || rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "导入数据为空");
        }
        String batchNo = StringUtils.hasText(bizNo) ? bizNo : "ACT" + System.currentTimeMillis();
        int ok = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            String tag = "第 " + (i + 1) + " 行";
            BigDecimal amt = toAmount(r.get("amount"));
            if (amt == null || amt.signum() <= 0) {
                failed.add(tag + "：金额为空或非正数");
                continue;
            }
            Long itemId = toLong(r.get("budgetItemId"));
            Long expenseItemId = toLong(r.get("itemId"));
            Long companyId = toLong(r.get("companyId"));
            Long deptId = toLong(r.get("deptId"));
            Long projectId = toLong(r.get("projectId"));
            Integer year = toInt(r.get("budgetYear"));
            if (itemId == null) {
                if (expenseItemId == null) {
                    failed.add(tag + "：既未给预算明细，也未给费用项，无法定位预算行");
                    continue;
                }
                itemId = resolveOccupyItem(null, expenseItemId, companyId, deptId, year, projectId, false);
            }
            if (itemId == null) {
                failed.add(tag + "：未匹配到预算明细（按手册，此类记录不进统计报表）");
                continue;
            }
            // 行级 try/catch：一行非法（明细不存在/已冻结）不应把整批 1000 行导入全部回滚，
            // 也不应静默跳过——逐行进 failed 清单，调用方按行修正后重跑
            try {
                writeOff(itemId, amt, "ACTUAL_IMPORT", null, batchNo);
            } catch (Exception ex) {
                failed.add(tag + "：" + ex.getMessage());
                continue;
            }
            ok++;
            total = total.add(amt);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("batchNo", batchNo);
        res.put("successCount", ok);
        res.put("failedCount", failed.size());
        res.put("successAmount", total);
        res.put("failures", failed);
        log.info("实际数导入 {}：成功 {} 行 / {} 元，失败 {} 行", batchNo, ok, total, failed.size());
        return res;
    }

    /**
     * 结余结转：把「上年可用余额」写进下年同维度行，并留 WRITE_OFF/OCCUPY 流水痕迹。
     *
     * <p>{@code bg_scheme.carry_over} 此前只有实体列与界面开关，全仓无任何消费点——
     * 勾了等于没勾。本方法做的是年末作业那一版：不做跨年累计控制引擎，只搬余额。
     *
     * <p>口径：可用余额 = 总额 + 调整 − 占用 − 已用；只有 {@code carry_over=1} 且未关闭的方案参与；
     * 上一年度行必须 period=0（年度行），否则同一维度会有 12 条结转来源、金额重复。
     * 下年行不存在则新建（total_amount=结转额），已存在则把结转额并入 adjust_amount（不覆盖原额度）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> carryOver(int fromYear, int toYear) {
        if (toYear != fromYear + 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结余结转只能逐年执行：fromYear=" + fromYear + " 的下一期应为 " + (fromYear + 1));
        }
        List<BgScheme> schemes = bgSchemeMapper.selectList(new LambdaQueryWrapper<BgScheme>()
                .eq(BgScheme::getBudgetYear, fromYear)
                .eq(BgScheme::getCarryOver, 1));
        int created = 0;
        int merged = 0;
        BigDecimal totalCarried = BigDecimal.ZERO;
        List<String> skipped = new ArrayList<>();
        for (BgScheme scheme : schemes) {
            List<BgItem> items = bgItemMapper.selectList(new LambdaQueryWrapper<BgItem>()
                    .eq(BgItem::getSchemeId, scheme.getId())
                    .eq(BgItem::getBudgetYear, fromYear)
                    .eq(BgItem::getBudgetPeriod, 0));
            for (BgItem it : items) {
                BigDecimal avail = available(it);
                if (avail.signum() <= 0) {
                    continue;
                }
                BgItem target = bgItemMapper.selectOne(new LambdaQueryWrapper<BgItem>()
                        .eq(BgItem::getSchemeId, scheme.getId())
                        .eq(BgItem::getBudgetYear, toYear)
                        .eq(BgItem::getBudgetPeriod, 0)
                        .eq(BgItem::getDimKey, it.getDimKey())
                        .last("limit 1"));
                if (target != null) {
                    addAmount(target.getId(), "adjust", avail);
                    merged++;
                } else {
                    BgItem copy = new BgItem();
                    copy.setSchemeId(scheme.getId());
                    copy.setCompanyId(it.getCompanyId());
                    copy.setBudgetYear(toYear);
                    copy.setBudgetPeriod(0);
                    copy.setDeptId(it.getDeptId());
                    copy.setItemId(it.getItemId());
                    copy.setProjectId(it.getProjectId());
                    copy.setBudgetTypeId(it.getBudgetTypeId());
                    copy.setDimKey(it.getDimKey() == null ? buildDimKey(it) : it.getDimKey());
                    copy.setTotalAmount(avail);
                    copy.setAdjustAmount(BigDecimal.ZERO);
                    copy.setOccupyAmount(BigDecimal.ZERO);
                    copy.setUsedAmount(BigDecimal.ZERO);
                    copy.setCurrencyCode(it.getCurrencyCode());
                    copy.setRemark("由 " + fromYear + " 年结余结转（源明细 " + it.getId() + "）");
                    bgItemMapper.insert(copy);
                    created++;
                }
                totalCarried = totalCarried.add(avail);
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("fromYear", fromYear);
        res.put("toYear", toYear);
        res.put("schemeCount", schemes.size());
        res.put("createdRows", created);
        res.put("mergedRows", merged);
        res.put("carriedAmount", totalCarried);
        res.put("skipped", skipped);
        log.info("预算结余结转 {}→{}：新建 {} 行、并入 {} 行，合计 {} 元", fromYear, toYear, created, merged, totalCarried);
        return res;
    }

    /**
     * 冻结/解冻预算行：冻结后 occupy 拒绝、释放与核销照常。
     *
     * @param reason 操作原因，冻结时必填（防止再出现「谁冻的、为什么冻」无从追溯的遗留态）；
     *               解冻可空，但界面同样要求填。原因落审计轨迹的描述字段。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setFreeze(Long itemId, boolean freeze, String reason) {
        BgItem cur = bgItemMapper.selectById(itemId);
        if (cur == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预算明细不存在: " + itemId);
        }
        String trimmed = reason == null ? "" : reason.trim();
        if (freeze && trimmed.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "冻结预算行必须填写原因");
        }
        if (cur.getFreeze() != null && cur.getFreeze() == (freeze ? 1 : 0)) {
            // 状态未变：不写库、不记审计，避免重复点击刷出一串无用轨迹
            log.info("预算明细 {} 已是{}状态，忽略本次请求", itemId, freeze ? "冻结" : "解冻");
            return;
        }
        BgItem upd = new BgItem();
        upd.setId(itemId);
        upd.setFreeze(freeze ? 1 : 0);
        bgItemMapper.updateById(upd);
        String action = freeze ? "冻结预算行" : "解冻预算行";
        auditTrailService.recordUpdate("BUDGET", itemId, String.valueOf(itemId), "bg_item",
                String.valueOf(itemId),
                Collections.singletonMap("freeze", cur.getFreeze()),
                Collections.singletonMap("freeze", freeze ? 1 : 0),
                trimmed.isEmpty() ? action : action + "：" + trimmed);
        log.info("预算明细 {} 已{}{}", itemId, freeze ? "冻结" : "解冻",
                trimmed.isEmpty() ? "" : "（原因：" + trimmed + "）");
    }

    private static BigDecimal toAmount(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(v).replace(",", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    // 内部工具

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
    /** 原子汇总更新（直接 UPDATE += 增量，避免读-改-写丢失更新；调用方须已持有行锁） */
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
    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 执行率单点计算（%），明细页/其它读面统一取该规则。
     *
     * <p>分子 = occupy_amount + used_amount（与「可用余额」列同轴：总额＋调整−占用−核销）；
     * 分母 = total_amount + adjust_amount；scale=1 HALF_UP；<b>不封顶</b>（>100 即超支）；
     * 分母 &lt;= 0 时返回 null（无预算基数，前端显示「—」）。</p>
     */
    static BigDecimal execRateOf(BgItem bi) {
        if (bi == null) {
            return null;
        }
        BigDecimal base = orZero(bi.getTotalAmount()).add(orZero(bi.getAdjustAmount()));
        if (base.signum() <= 0) {
            return null;
        }
        BigDecimal numerator = orZero(bi.getOccupyAmount()).add(orZero(bi.getUsedAmount()));
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(base, 1, RoundingMode.HALF_UP);
    }

    public List<Map<String, Object>> executionSummary(String period, Long deptId) {
        String month = null;
        if (period != null && period.length() >= 6) {
            try { month = String.valueOf(Integer.parseInt(period.substring(4))); } catch (NumberFormatException e) { month = null; /* 期间格式异常按全量规则处理，不报错 */ }
        }
        List<BgItem> items = bgItemMapper.selectList(
                new LambdaQueryWrapper<BgItem>()
                        // 内连存活方案（del_flag=0 自动过滤），孤儿方案行不进执行读数
                        .in(BgItem::getSchemeId, liveSchemeIds())
                        .orderByDesc(BgItem::getId));
        // 名称解析：itemName 显示费用项真实名称，并附公司/部门名称供前端筛选展示
        Map<Long, SysCompany> compMap = new HashMap<>();
        Map<Long, SysDept> deptMap = new HashMap<>();
        Map<Long, MdExpenseItem> itemMap = new HashMap<>();
        for (BgItem bi : items) {
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
        List<Map<String, Object>> out = new ArrayList<>();
        for (BgItem bi : items) {
            if (deptId != null && !deptId.equals(bi.getDeptId())) continue;
            if (month != null && bi.getBudgetPeriod() != null && bi.getBudgetPeriod() != 0
                    && bi.getBudgetPeriod() != Integer.parseInt(month)) continue;
            BigDecimal total = orZero(bi.getTotalAmount()).add(orZero(bi.getAdjustAmount()));
            BigDecimal occupy = orZero(bi.getOccupyAmount());
            BigDecimal used = orZero(bi.getUsedAmount());
            SysCompany comp = bi.getCompanyId() == null ? null : compMap.get(bi.getCompanyId());
            SysDept dept = bi.getDeptId() == null ? null : deptMap.get(bi.getDeptId());
            MdExpenseItem mi = bi.getItemId() == null ? null : itemMap.get(bi.getItemId());
            String itemName = mi != null
                    ? nvlStr(mi.getItemCode()) + " " + nvlStr(mi.getItemName())
                    : "预算项#" + bi.getId();
            Map<String, Object> m = new LinkedHashMap<>();
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
