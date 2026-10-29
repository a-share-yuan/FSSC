// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.entity.SysDept;
import com.fssc.system.entity.SysRole;
import com.fssc.system.entity.SysUser;
import com.fssc.system.entity.SysUserRole;
import com.fssc.system.mapper.SysCompanyMapper;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.mapper.SysDeptMapper;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.mapper.SysUserRoleMapper;
import com.fssc.workflow.entity.WfDelegate;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.entity.WfTaskHistory;
import com.fssc.workflow.mapper.WfDelegateMapper;
import com.fssc.workflow.mapper.WfInstanceMapper;
import com.fssc.workflow.mapper.WfModelMapper;
import com.fssc.workflow.mapper.WfTaskMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.engine.TaskService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 多分子公司审批路由解析器。
 *
 * <p>标准 FSSC"前端分散受理、后端集中共享"结构在任务路由上的落地：
 * <ul>
 *   <li>业务审批段角色（sys_role.org_scope=COMPANY：部门经理/中心总监/基地总经理…）——
 *       只在"单据所属公司"内解析与可见，A 公司的经理看不到 B 公司的单；</li>
 *   <li>共享作业段与集团垂直角色（org_scope=GROUP：共享会计/主管/出纳/总账/税务/档案/CFO/总裁…）——
 *       跨公司集中办理，不做公司过滤；</li>
 *   <li>集团级账号（sys_user.company_id 为 NULL）不受过滤限制（fail-open，兼容演示/集团岗）；</li>
 *   <li>「直线上级」节点（DIRECT_SUPERVISOR）沿部门汇报线解析：发起人所在部门的负责人
 *       sys_dept.leader_id 直接指派为办理人。</li>
 * </ul>
 * 所有解析失败一律回退"候选组"原机制（fail-open），绝不因组织数据缺失而卡流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeOrgResolver {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysDeptMapper sysDeptMapper;
    /** 角色持有人矩阵取公司名（配置态体检只读，见 {@link #roleHolderMatrix}） */
    private final SysCompanyMapper sysCompanyMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TaskService flowTaskService;
    private final WfDelegateMapper wfDelegateMapper;
    /**
     * 节点配置解析器：空策略兜底需读 wf_node_config.empty_strategy。
     * 经它解析而非直接按 node_key 精确查——BPMN 生成的 taskDefinitionKey 为 {@code Task_N}，
     * 与配置表的业务节点编码不同形，精确匹配恒不命中（见 WfNodeConfigResolver 类注释）。
     */
    private final WfNodeConfigResolver nodeConfigResolver;
    /**
     * 空策略 SKIP 的处理要直接调 {@code flowTaskService.complete()} 推引擎、绕开
     * {@code WfEngineFacade.complete()}，镜像（wf_task / wf_task_history）会缺账。
     * 本解析器是最了解该旁路上下文的地方，故由它补齐镜像办结与记录。
     */
    private final WfQueryService wfQueryService;
    private final WfMirrorDefectRecorder mirrorDefectRecorder;
    /**
     * 取人策略开关与委托总开关读 {@code sys_config}：直连 mapper、不带租户过滤，
     * 与 {@code approve.pick_mode.*} / {@code approve.transfer_strict.*} / 齐套校验同一规则。
     */
    private final SysConfigMapper sysConfigMapper;
    /** LEAST_LOADED 的负载规则：{@code wf_task} 当前 PENDING 快照（与待办视图同一张镜像表） */
    private final WfTaskMapper wfTaskMapper;
    /** {@code wf_model.enable_delegate} 读取（该列为 0 的流程不支持委托代办） */
    private final WfModelMapper wfModelMapper;

    /** 取人策略（sys_config）：{@code FIRST_ID|LEAST_LOADED|ROUND_ROBIN}，未配置＝FIRST_ID */
    static final String ASSIGN_STRATEGY_KEY = "wf.assign.strategy";
    /** 取人策略灰度清单（逗号分隔 modelKey）：<b>空＝全库不生效</b>，与齐套校验同一灰度范式 */
    static final String ASSIGN_MODELS_KEY = "wf.assign.strategy.models";
    /**
     * 委托语义：{@code BORROW}＝借资格（缺省）；
     * {@code REASSIGN}＝改派（委托生效即把委托人历史待办改指受托人，期内新任务直接落受托人）。
     * 界面承诺"待办自动转给代理人"，而 BORROW 只是把委托人的待办<b>并入</b>受托人视图、
     * 委托人自己照样能办——本键是让"自动转到受托人"这条承诺可以被真正兑现的唯一出口。
     */
    static final String DELEGATE_MODE_KEY = "wf.delegate.mode";
    /** 委托改派灰度清单（逗号分隔 modelKey）：<b>空＝全库不生效</b>（与取人策略同一范式，防止一刀切改动记录归属） */
    static final String DELEGATE_MODE_MODELS_KEY = "wf.delegate.mode.models";
    /** 委托到期回跳参数（A3）：{@code REVERT}（缺省）／{@code KEEP}，见 {@link #delegateRevertOnEndEnabled()} */
    static final String DELEGATE_REVERT_ON_END_KEY = "wf.delegate.revert.on.end";
    /** {@link #DELEGATE_MODE_KEY} 的取值 */
    public static final String DELEGATE_MODE_BORROW = "BORROW";
    public static final String DELEGATE_MODE_REASSIGN = "REASSIGN";
    /** 配置快照 TTL：待办/取人是在线路径，不能每次指派查两遍 sys_config */
    private static final long CONFIG_TTL_MILLIS = 5_000L;
    /** ROUND_ROBIN 游标：key = 租户|modelKey|roleCode。见 {@link #roundRobin} 的多实例说明 */
    private static final ConcurrentHashMap<String, AtomicInteger>
            ROUND_ROBIN_CURSOR = new ConcurrentHashMap<>();

    /** 配置快照（策略 + 灰度清单一起换，避免读到半新半旧的组合） */
    private record AssignConfig(AssignStrategy strategy, Set<String> models) {
    }

    private volatile AssignConfig assignConfig;
    private volatile long assignConfigAt;
    /** {@code wf_model.enable_delegate=0} 清单快照+ 更新时间戳 */
    private volatile Set<String> delegateOffModels;
    private volatile long delegateOffAt;
    /** 委托语义快照：模式 + 灰度清单 + 生效中的「委托人 → 受托人」映射，一起换避免半新半旧 */
    private record DelegateMode(String mode, Set<String> models, Map<Long, Long> fromTo) {
    }

    private volatile DelegateMode delegateMode;
    private volatile long delegateModeAt;

    /**
     * 候选组任务按"角色范围 × 单据公司"过滤。
     *
     * @param tasks 当前用户角色命中的候选组任务
     * @param userId 查询用户
     * @param userRoleCodes 该用户持有的全部角色编码
     */
    public List<Task> filterGroupTasks(List<Task> tasks, Long userId, List<String> userRoleCodes) {
        if (tasks == null || tasks.isEmpty()) {
            return tasks == null ? List.of() : tasks;
        }
        SysUser u;
        try {
            u = sysUserMapper.selectById(userId);
        } catch (Exception e) {
            return tasks;
        }
        // 集团级账号（未挂公司）不受限——演示全能审批人与集团岗照常全可见
        if (u == null || u.getCompanyId() == null) {
            return tasks;
        }
        Map<String, String> scopes = roleScopes(userRoleCodes);
        // 单据公司一次批量装载：逐条 computeIfAbsent(procInstId, this::resolveCompany) 会为每条任务
        // 发两次往返（查 wf_instance + 查单据表 company_id），上千条候选组任务就是数千条 SQL/请求，
        // 单请求会退化到几十秒并把库压满。批量后只剩几条。
        // 语义不变：批量没解析出来的（无实例/映射外/单据 company_id 为空）仍走 resolveCompany 逐条兜底。
        Map<String, Long> companyCache = companiesByProc(tasks);
        // 候选组同样一次 IN 批量装载：逐任务 getIdentityLinksForTask 是每条任务一次往返
        // （或让上层被迫 includeIdentityLinks() 走 Flowable「任务+关联实体」连表，单次就要几百毫秒）。
        Map<String, Set<String>> groupsByTask = candidateGroupsByTasks(tasks);
        List<Task> out = new ArrayList<>(tasks.size());
        for (Task t : tasks) {
            Set<String> hit = groupsByTask.getOrDefault(t.getId(), Set.of())
                    .stream().filter(userRoleCodes::contains).collect(Collectors.toSet());
            boolean groupWide = hit.stream().anyMatch(g -> "GROUP".equalsIgnoreCase(scopes.getOrDefault(g, "COMPANY")));
            if (groupWide) {
                out.add(t);
                continue;
            }
            Long billCompany = companyCache.computeIfAbsent(t.getProcessInstanceId(), this::resolveCompany);
            if (billCompany == null || billCompany.equals(u.getCompanyId())) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * 审批越权守卫（fail-closed）：判定某用户是否为该任务的合法办理人。
     * 判定顺序：
     * <ol>
     *   <li>直接办理人：{@code task.assignee == userId} → true；</li>
     *   <li>POOL 软锁：作业节点（{@code handling_mode=POOL}）已被他人领取时，候选组通道
     *       对该任务<b>关闭</b>——只有领取人本人、或领取人正式委托的受托人可办；</li>
     *   <li>候选组命中：候选组 ∩ 用户角色（查法同 todo：sys_user_role→sys_role）为空 → false；
     *       命中角色任一为 GROUP 范围 → true；否则按单据公司过滤——单据公司为 null（无公司维度）→ true，
     *       用户公司为 null（集团级）→ true，相等 → true；</li>
     *   <li>委托代理：当前用户作为受托人（wf_delegate.to_user=userId、NORMAL、时间区间内）时，
     *       任一委托人命中 assignee/候选 → true。</li>
     * </ol>
     * <b>fail-closed 铁律</b>：登录校验已在门面（complete）保证，故此处不再对
     * {@code task==null / userId==null} 或任何解析异常做 fail-open 放行——一律返回 false（拒绝），
     * 并记 error 记录。原则：无法判定归属时，宁可拒办，不可越权放行。
     */
    public boolean canOperate(Task task, Long userId) {
        if (task == null || userId == null) {
            log.error("canOperate 参数缺失（task={}, userId={}），fail-closed 拒绝",
                    task == null ? null : task.getId(), userId);
            return false;
        }
        try {
            String uid = String.valueOf(userId);
            // 1) 直接办理人
            if (uid.equals(task.getAssignee())) {
                return true;
            }
            // 1.4) 改派档排他：这行已按委托从"我"名下转到"我的受托人"名下 ⇒ 我不该再能办它，
            // 否则改派只是搬了个显示归属，候选组角色命中仍能办回（「委托人侧不再重复可办」不成立）。
            // 缺省（REASSIGN 未开或灰度清单空）时这里恒返回 false，不影响既有行为。
            if (delegateTakenOverFrom(task, userId)) {
                log.info("委托改派排他拒绝: task={} 请求人={} 现办理人=其受托人", task.getId(), userId);
                return false;
            }
            // 1.5) POOL 软锁：作业节点已被同池他人领取 → 关闭候选组通道，只认领取人（及其受托人）。
            // AUTO/PICK 与未领取态不受影响；"任务被打开处理时其他用户看到锁定状态"。
            String lockOwner = poolLockOwner(task);
            if (lockOwner != null) {
                for (Long fromId : delegateFromUserIds(task, userId)) {
                    if (lockOwner.equals(String.valueOf(fromId))) {
                        return true;
                    }
                }
                log.info("POOL 任务锁定拒绝越权办理: task={} 领取人={} 请求人={}",
                        task.getId(), lockOwner, userId);
                return false;
            }
            // 2) 候选组 ∩ 用户角色（按公司维度）
            if (roleHit(task, userId, userRoleCodes(userId))) {
                return true;
            }
            // 3) 委托代理：受托人代办委托人的任务
            for (Long fromId : delegateFromUserIds(task, userId)) {
                if (String.valueOf(fromId).equals(task.getAssignee())) {
                    return true;
                }
                if (roleHit(task, fromId, userRoleCodes(fromId))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("canOperate 校验异常 task={} user={}，fail-closed 拒绝: {}",
                    task.getId(), userId, e.getMessage(), e);
            return false;
        }
    }

    /** 单据提交人（apply_user_id）：bizType→表白名单同 resolveCompany；无映射/列缺失/查不到回落实例发起人 */
    public Long resolveApplyUserId(String procInstId) {
        WfInstance inst = findInstance(procInstId);
        if (inst == null || inst.getBizType() == null || inst.getBizId() == null) {
            return null;
        }
        String table = applyTable(inst.getBizType());
        if (table == null) {
            // 不能静默返回 null：那会让 deny_self 在整域失效且零痕迹。
            // 提升为 warn，让"内控规则取不到数"在运维侧可见；校验侧按最严规则 fail-closed。
            log.warn("提交人不可解析：bizType={} 无单据表取数映射（companyTable 未登记、单据主表亦无已核对的提交人列），"
                    + "回落实例发起人 proc={}", inst.getBizType(), procInstId);
            return inst.getStartUserId();
        }
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT " + applyColumn(table) + " FROM `" + table + "` WHERE id = ?", Long.class, inst.getBizId());
            Long applyUser = ids.isEmpty() ? null : ids.get(0);
            // 列取到空值（如 op_archive.filing_user_id 未回填）同样回落发起人，
            // 否则校验会把"兜底路径"说成"取不到提交人列"，用户无法据理由自查。
            // 0 也要回落——{@code FsscMetaObjectHandler.fillAudit} 在无登录上下文时
            // 把 create_by 填成 0L 而非 NULL，0 是占位不是人；若当成提交人交给 deny_self，
            // 校验会拿"用户 0"去比候选⇒永不命中，静默失效（比 NULL 回落更坏）。
            return applyUser != null && applyUser != 0L ? applyUser : inst.getStartUserId();
        } catch (Exception e) {
            // debug→warn，"查不存在的列/查不到行"必须记录（静默失效的主因之一）
            log.warn("解析单据提交人失败 proc={} table={}: {}，回落实例发起人", procInstId, table, e.getMessage());
            return inst.getStartUserId();
        }
    }

    /**
     * 提交人查询表：公司解析表（{@link #companyTable}）优先；未登记时回落该 bizType 的<b>单据主表</b>，
     * 且只对已核对过列名的表回落。
     *
     * <p>为什么不复用公司面白名单：{@code TR_BANK_RECON} 的 companyTable 刻意留空
     * （补值＝收窄候选面，属授权口径待定，见 {@link BillRouteRegistry} 类注释），但 deny_self 仍需知道
 * "这单是谁登记的"。两件事共用一张表会让内控校验被授权口径连带绑死。
     */
    private static String applyTable(String bizType) {
        String table = companyTable(bizType);
        if (table != null) {
            return table;
        }
        BillRouteRegistry.Route r = BillRouteRegistry.of(bizType);
        String home = r == null ? null : r.homeTable();
        return home != null && APPLY_COLUMN.containsKey(home) ? home : null;
    }

    /**
     * 提交人列名按表而定：GL 域各表都没有 apply_user_id 列，恒查失败 →
     * resolveApplyUserId 曾返回 null → 「禁止审批自己提交」(deny_self) 在整个总账域拿不到提交人而失效。
     * 退税单有专列 apply_by；其余总账单据的发起人就是 create_by（glex 各 service 用 userId() 写入）。
     * 列取不到值/查不存在的列，现由 {@link #resolveApplyUserId} 回落实例发起人。
     */
    private static String applyColumn(String table) {
        return APPLY_COLUMN.getOrDefault(table, "apply_user_id");
    }

    /**
     * 已核对过真实列名的单据表（列名取自各表实体类，未核对的表按 {@code apply_user_id} 默认列查）。
     * 新增一域只补一行；漏补的后果是 deny_self 与自审回避查询一起失效。
     */
    private static final Map<String, String> APPLY_COLUMN = Map.ofEntries(
            Map.entry("tax_refund", "apply_by"),
            // 档案销毁申请单的申请人列既不是 apply_user_id 也不是 create_by；借阅流水同理是 requester_id
            Map.entry("archive_destroy_approval", "applicant_id"),
            Map.entry("archive_access_log", "requester_id"),
            // op_archive 既无 apply_user_id 也无 create_by（实体 OpArchive 已核），立卷人＝filing_user_id。
            // 查错列会让每张单抛一次 bad SQL grammar、日志反复刷，而校验只能按最严规则拦。
            Map.entry("op_archive", "filing_user_id"),
            // bank_reconcile 是勾对流水平，无登记人列；编制人＝提单人＝match_by
            // （BankReconcileService 同一 userId 既写 match_by 又作 start 的 approver）。
            Map.entry("bank_reconcile", "match_by"),
            // 这批业务表没有 apply_user_id 列，
            // 发起人在 create_by（glex / 合同 / 预算 / 主数据各 service 都用 userId() 写入）。
            // 不登记 ⇒ resolveApplyUserId 恒 null ⇒「禁止审批自己提交」deny_self 静默失效。
            Map.entry("gl_journal_entry", "create_by"), Map.entry("gl_salary_run", "create_by"),
            Map.entry("gl_tax_provision", "create_by"), Map.entry("gl_month_close", "create_by"),
            Map.entry("cont_contract", "create_by"), Map.entry("bg_scheme", "create_by"),
            Map.entry("crm_customer", "create_by"), Map.entry("md_supplier", "create_by"),
            Map.entry("asset_depr_batch", "create_by"), Map.entry("gl_inter_offset", "create_by"),
            Map.entry("gl_consolidate", "create_by"), Map.entry("gl_shared_master", "create_by"),
            Map.entry("op_pay_batch", "create_by"), Map.entry("md_bank_account", "create_by"),
            Map.entry("md_asset", "create_by"), Map.entry("md_inventory", "create_by"),
            // 采购订单没有 apply_user_id 列，建单人在 create_by（PurchaseService 用 userId() 写入，
            // information_schema 已核 po_order 只有 create_by）。不登记 ⇒ resolveApplyUserId 恒 null ⇒
            // 采购域 deny_self（创建者无权自审）物理不存在。取值与 BillRouteRegistry.TABLE_OWNER 一致。
            Map.entry("po_order", "create_by"),
            // 资金四表的提交人只在 create_by（tr_bill_settle/tr_credit_bill/tr_transfer/tr_fund_plan 的
            // create_by 是后续补出的列），不登记 ⇒ 恒查不存在的 apply_user_id ⇒ 只能回落实例发起人。
            // 登记本臂后，资金族（TR_BILL/TR_RECEIPT/TR_TRANSFER/TR_UPLOAD/TR_PLAN/TR_PLAN_ADJUST）的
            // deny_self 与自审回避才有真实查询来源。
            Map.entry("tr_bill_settle", "create_by"), Map.entry("tr_credit_bill", "create_by"),
            Map.entry("tr_transfer", "create_by"), Map.entry("tr_fund_plan", "create_by"),
            // 应收/发票/对账这一族的表同样没有 apply_user_id 列，未登记 ⇒ 每个节点解析提交人都抛一次
            // bad SQL grammar（SELECT apply_user_id FROM `rc_receivable` …），deny_self 在 AR/发票域
            // 只能按最严规则拦。归属列取各表 service 实际写入的人（create_by），
            // pay_plan 例外——它连 create_by 都没有，提单列就是 applicant_id。
            Map.entry("rc_receivable", "create_by"), Map.entry("rc_bad_debt", "create_by"),
            Map.entry("po_receipt", "create_by"), Map.entry("inv_invoice", "create_by"),
            Map.entry("supplier_recon", "create_by"), Map.entry("ar_recon_record", "create_by"),
            Map.entry("pay_plan", "applicant_id"));

    /**
     * 对任务执行办理人解析（BPMN candidateGroups 生成任务的统一入口，start 首节点与 complete 后续节点共用）。
     * 已有 assignee 跳过；解析成功仅"设置首选办理人"，保留候选组——集团级账号/同公司同角色仍可办理（兜底不断流）。
     */
    public void applyResolution(Task task) {
        try {
            if (task == null || task.getAssignee() != null) {
                return;
            }
            Set<String> groups = candidateGroups(task);
            if (groups.isEmpty()) {
                log.info("节点办理人动态解析跳过: task={} 无候选组", task == null ? null : task.getId());
                return;
            }
            // POOL 任务池档：不做"取角色首个持有人"的自动指派，任务停在仅候选组态，由候选人主动领取锁定。
            // 与 WfEngineFacade.applyNodeConfig 的 ROLE+POOL 分支同一规则，两处都要拦：前者负责把候选组装上，
            // 此处负责<b>别把它又指派掉</b>（纯 BPMN 候选组任务不走节点配置，只能在这里拦）。
            if (isPoolTask(task)) {
                log.info("节点办理人动态解析跳过（POOL 任务池，待领取）task={} groups={}", task.getId(), groups);
                return;
            }
            for (String g : groups) {
                Long resolved = tryResolveAssignee(task.getProcessInstanceId(), g);
                if (resolved != null) {
                    flowTaskService.setAssignee(task.getId(), String.valueOf(resolved));
                    log.info("节点办理人动态解析: task={} role={} -> userId={}", task.getId(), g, resolved);
                    return;
                }
            }
            log.warn("节点办理人解析未命中: task={} groups={}（维持候选组）", task.getId(), groups);
        } catch (Exception e) {
            log.warn("节点办理人解析异常 task={}: {}", task == null ? null : task.getId(), e.getMessage());
        }
    }

    /**
     * 节点是否配置为 {@code handling_mode=POOL}（任务池/抢单）。只读判定，不参与指派以外的逻辑；
     * 解析异常一律按"非 POOL"返回 false，保持既有自动指派行为不变（fail-open 到旧语义）。
     */
    public boolean isPoolTask(Task task) {
        try {
            if (task == null) {
                return false;
            }
            String defId = task.getProcessDefinitionId();
            String modelKey = defId == null ? null : defId.split(":")[0];
            if (modelKey == null || modelKey.isBlank()) {
                return false;
            }
            WfNodeConfig cfg = nodeConfigResolver.resolve(modelKey, task.getTaskDefinitionKey());
            return cfg != null && "POOL".equalsIgnoreCase(
                    cfg.getHandlingMode() == null ? "" : cfg.getHandlingMode().trim());
        } catch (Exception e) {
            log.debug("POOL 任务池判定异常 task={}: {}", task == null ? null : task.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * POOL 软锁持有者：作业节点（{@code handling_mode=POOL}）已有办理人时返回该办理人
     * 用户 ID 字符串，否则返回 {@code null}（= 未锁定）。
     *
     * <p>只覆盖 POOL：<b>AUTO/PICK 不设锁</b>——自动指派的节点一旦锁死，同角色同事就失去了
     * {@code canOperate} 的候选组通道，而这条通道正是「禁止审批自己提交」在现网已验证的绕行出口
     * （见 {@code deny_self} 拦截后的处理路径）。同样只在作业型任务加锁，审批型走"择一/抢占"。
     */
    public String poolLockOwner(Task task) {
        if (task == null) {
            return null;
        }
        String a = task.getAssignee();
        if (a == null || a.isBlank()) {
            return null;
        }
        return isPoolTask(task) ? a.trim() : null;
    }

    /**
     * 待办列表的 POOL 软锁批量标注：返回 {@code taskId -> 领取人用户ID}，
     * 只收录「作业节点 + 已被他人领取」的行。前端据此把「去处理」灰显并显示"XX 正在办理"，
     * 与 {@link #canOperate} 的拒办规则一致（列表可见≠可办，同为"可见但锁定"）。
     *
     * <p>成本控制：一次载入全部 POOL 节点键，不逐行查配置表；判定异常返回空表（不加锁标注，
     * 服务端 {@code canOperate} 仍是强制校验）。
     */
    public Map<String, Long> poolLockedByOthers(List<Task> tasks, Long viewerId) {
        Map<String, Long> out = new HashMap<>();
        if (tasks == null || tasks.isEmpty() || viewerId == null) {
            return out;
        }
        try {
            Set<String> poolKeys = nodeConfigResolver.poolNodeKeys();
            if (poolKeys.isEmpty()) {
                return out;
            }
            for (Task t : tasks) {
                String a = t.getAssignee();
                if (a == null || a.isBlank()) {
                    continue;
                }
                String defId = t.getProcessDefinitionId();
                String modelKey = defId == null ? null : defId.split(":")[0];
                if (modelKey == null || !poolKeys.contains(modelKey + ":" + t.getTaskDefinitionKey())) {
                    continue;
                }
                try {
                    long owner = Long.parseLong(a.trim());
                    if (owner != viewerId) {
                        out.put(t.getId(), owner);
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字办理人（外部系统账号）不参与软锁
                }
            }
        } catch (Exception e) {
            log.warn("POOL 软锁批量标注失败（按无锁返回，服务端仍会拒办）: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 零 admin 铁律判定：该用户是否为系统管理员账号（{@code user_name='admin'} 或
     * {@code is_admin=1}）。超时自动通过等机器路径的记录办理人不得是这类账号。
     * 查询异常一律按"是 admin"返回（fail-closed：宁可少自动过一单，不可让 admin 出现在节点上）。
     */
    public boolean isAdministrator(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            SysUser u = sysUserMapper.selectById(userId);
            return u != null && ("admin".equals(u.getUserName()) || Integer.valueOf(1).equals(u.getIsAdmin()));
        } catch (Exception e) {
            log.warn("admin 身份判定异常 userId={}，按 admin 处理（fail-closed）: {}", userId, e.getMessage());
            return true;
        }
    }

    /**
     * 空策略兜底，处置"节点找不到审批人"。
     *
     * <p><b>为什么必须有它</b>：BPMN 动态生成的 userTask 只带 {@code flowable:candidateGroups}
     * 角色，运行时待办按「角色范围 × 单据公司」过滤（见 {@link #filterGroupTasks}）。当某公司
     * 在该角色下无任何成员时——例如 {@code BASE_FIN_MANAGER}（基地财务经理）只对集团总部铺开，
     * 而 {@code org_scope=COMPANY} 使其在子公司不可见——候选组就成了空集：任务既无 assignee、
     * 也无任何合法办理人。只打一行 WARN 就维持候选组的话，单据会<b>静默永久滞留</b>，
     * 没有任何出口告警。{@code wf_node_config.empty_strategy} 正是为这一场景设计
     * （建表注释：「找不到审批人时:ADMIN转管理员/SKIP自动跳过/ERROR报错阻断」）。
     *
     * <p><b>判定规则</b>：仅当任务无 assignee，且其候选组<b>在本单据公司内一个可办人都解析不出</b>
     * 时触发；条条候选组都解析不出才算空（候选组为空视为无约束节点，不处置）。守门逻辑与
     * {@link #tryResolveAssignee} 一致——GROUP 范围角色跨公司可见，返回首个持有人，故不会误触发。
     *
     * <p>"解析不出"有两种成因，别只当一种查：① 该公司在该角色下确实无人（主数据缺口，
     * 补人即可）；② <b>角色有人、但唯一持有人就是本单提单人</b>——{@code tryResolveAssignee} 会
     * 先剔提单人（AUTO 指派命中提单人会让该节点谁都办不动），剔完为空同样落到本兜底。
     * 记录里 ESCALATE 的相当比例属②，是<b>设计行为不是取人 bug</b>；把它当①去补人会凭空多出岗位权限。
     * 因此下面的日志/记录文案统一写"无可办理的持有人（含自审回避）"，不写"无候选办理人"。
     *
     * <p><b>处置</b>：SKIP 自动完成该节点；ERROR（<b>缺省</b>：所有流程节点不允许 admin 参与，
     * 必须由真实岗位办理）抛业务异常阻断，把问题在审批入口暴露给用户而非静默滞留；
     * ESCALATE 沿提交人部门汇报线向上按节点 {@code up_level} 取真实负责人（缺省 1 级）、再退单据公司
     * 组织负责人（ORG_HEAD），<b>绝不落到 admin</b>，
     * 定位不到即按 ERROR 抛异常；ADMIN 转租户管理员兜底仅在节点配置显式声明时允许（不推荐，违反「节点禁入 admin」规则）。
     *
     * @return 实际执行的动作：{@code ADMIN} / {@code ESCALATE} / {@code SKIP} / 无需处置或处置失败返回 {@code null}
     */
    public String applyEmptyStrategy(String modelKey, Task task) {
        try {
            if (task == null || task.getAssignee() != null) {
                return null;
            }
            Set<String> groups = candidateGroups(task);
            if (groups.isEmpty()) {
                return null;
            }
            // 条条候选组都解析不出办理人 → 该节点对任何人都不可见
            boolean anyResolvable = false;
            for (String g : groups) {
                if (tryResolveAssignee(task.getProcessInstanceId(), g) != null) {
                    anyResolvable = true;
                    break;
                }
            }
            if (anyResolvable) {
                return null;
            }
            // 作业池（POOL）不因"唯一在岗持有人＝提单人"（自审回避）而升级/跳过/报错：任务应停在候选组态，
            // 由本人领取（POOL 不走 deny_self，提单人是合法候选人就能办；
            // 复核控制点在下一节点的另一岗）。指派人出去反而破坏"池内谁做、记录到人"。
            // 岗位在本公司确实无人时仍按下面的 empty_strategy 处置，不给卡单留新出口。
            if (isPoolTask(task) && onlyHolderIsApplicant(task.getProcessInstanceId(), groups)) {
                log.info("空策略不适用（作业池＋自审回避）：节点「{}」唯一在岗持有人即提单人，留池待领取 task={} groups={}",
                        task.getName(), task.getId(), groups);
                return null;
            }
            WfNodeConfig cfg = nodeConfigResolver.resolve(modelKey, task.getTaskDefinitionKey());
            String strategy = (cfg == null || cfg.getEmptyStrategy() == null || cfg.getEmptyStrategy().isBlank())
                    ? "ERROR" : cfg.getEmptyStrategy().trim().toUpperCase();
            switch (strategy) {
                case "SKIP" -> {
                    log.warn("空策略 SKIP：节点「{}」无候选办理人，自动跳过 task={} groups={}",
                            task.getName(), task.getId(), groups);
                    // 先补镜像（wf_task 置 DONE + wf_task_history 记录）再推引擎。
                    // 顺序不能反——complete() 一旦让流程结束，PROCESS_COMPLETED 监听器会把该实例的
                    // PENDING 行统一置 CANCELLED，届时再补就分不清"被跳过"与"被取消"。
                    // 镜像写失败即冒泡（BizException 由本方法外层 catch 原样抛出），缺陷已落 sys_oper_log。
                    try {
                        wfQueryService.recordEngineBypass(task.getProcessInstanceId(), task.getId(),
                                task.getTaskDefinitionKey(), task.getName(),
                                // action 复用既有枚举语义「自动通过」：空策略跳过的本质就是系统代通过
                                "AUTO_PASS", "空策略 SKIP：本单据公司内无候选办理人，系统自动跳过该节点");
                    } catch (Exception ex) {
                        mirrorDefectRecorder.record(WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED,
                                "NodeOrgResolver.applyEmptyStrategy.SKIP",
                                null, "taskId=" + task.getId() + " procInstId=" + task.getProcessInstanceId(), ex);
                        throw new BizException(ErrorCode.SYSTEM_ERROR,
                                "空策略跳过节点时同步更新失败[" + WfMirrorDefectRecorder.CODE_MIRROR_WRITE_FAILED
                                        + "]，已阻止引擎推进以避免双账漂移：taskId=" + task.getId());
                    }
                    flowTaskService.complete(task.getId());
                    return "SKIP";
                }
                case "ERROR" -> throw new BizException(ErrorCode.PARAM_INVALID, missingAssigneeHint(task, groups, null));
                case "ADMIN" -> {
                    Long admin = tenantAdminId();
                    if (admin == null) {
                        log.error("空策略 ADMIN 兜底失败：租户内未找到管理员账号，节点「{}」仍无人可办 task={} groups={}",
                                task.getName(), task.getId(), groups);
                        return null;
                    }
                    flowTaskService.setAssignee(task.getId(), String.valueOf(admin));
                    log.warn("空策略 ADMIN：节点「{}」在本单据公司内无候选办理人，已转租户管理员 userId={} 兜底 task={} groups={}",
                            task.getName(), admin, task.getId(), groups);
                    return "ADMIN";
                }
                case "ESCALATE" -> {
                    // 与超时升级同一规则，按节点 up_level 上收第 N 级（固定 1 级会让 up_level 成死列）
                    Long target = escalateTarget(task.getProcessInstanceId(), cfg == null ? null : cfg.getUpLevel());
                    if (target == null) {
                        log.error("空策略 ESCALATE 兜底失败：节点「{}」沿汇报线及公司组织负责人均未定位到真实办理人 task={} groups={}",
                                task.getName(), task.getId(), groups);
                        throw new BizException(ErrorCode.PARAM_INVALID,
                                missingAssigneeHint(task, groups, "且沿汇报线、本公司组织负责人均未定位到可升级的真实负责人"));
                    }
                    flowTaskService.setAssignee(task.getId(), String.valueOf(target));
                    // 成因写清：②自审回避剔完唯一持有人 与 ①公司确实没人 的处置动作相反
                    // 前者补岗位或改派，后者补人，日志含糊一句会让人按错的那条去改主数据。
                    log.warn("空策略 ESCALATE：节点「{}」{}，已升级至真实负责人 userId={} task={} groups={}",
                            task.getName(), escalateCause(task.getProcessInstanceId(), groups),
                            target, task.getId(), groups);
                    return "ESCALATE";
                }
                default -> {
                    log.warn("空策略 {} 未识别（仅支持 ADMIN/ESCALATE/SKIP/ERROR），节点「{}」维持候选组 task={}",
                            strategy, task.getName(), task.getId());
                    return null;
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("空策略兜底异常 task={}: {}", task == null ? null : task.getId(), e.getMessage());
            return null;
        }
    }

    /** 租户管理员（兜底办理人）：优先 is_admin=1，其次 user_name='admin'；均无返回 null */
    private Long tenantAdminId() {
        try {
            List<SysUser> admins = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getIsAdmin, 1)
                    .eq(SysUser::getStatus, "NORMAL")
                    .orderByAsc(SysUser::getId)
                    .last("limit 1"));
            if (!admins.isEmpty() && admins.get(0).getId() != null) {
                return admins.get(0).getId();
            }
            List<SysUser> fallback = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUserName, "admin")
                    .orderByAsc(SysUser::getId)
                    .last("limit 1"));
            return fallback.isEmpty() ? null : fallback.get(0).getId();
        } catch (Exception e) {
            log.warn("查询租户管理员失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * ESCALATE 升级对象（绝不落到 admin），<b>固定 1 级</b>规则：先沿提交人所在部门的汇报线向上取
     * 首个真实负责人（跳过提交人本人、跳过 admin、防环最多 10 级）；取不到再退"单据所属公司的组织负责人"
     * ORG_HEAD 角色持有人，按公司维度。全部落空返回 null，由调用方按 ERROR 抛异常处理。
     *
     * <p>public：超时自动转办（{@code WfTimeoutJob} 的 AUTO_TRANSFER）复用同一升级规则，
     * 避免两处各写一套"往上报"的规则。
     *
     * <p>保留此单参重载仅为向后兼容：需要按节点配置 {@code up_level} 升到第 N 级的调用方，
     * 请用 {@link #escalateTarget(String, Integer)}。
     */
    public Long escalateTarget(String procInstId) {
        return escalateTarget(procInstId, 1);
    }

    /**
     * ESCALATE 升级对象（带层级，{@code wf_node_config.up_level} 的消费点）。
     *
     * <p><b>为什么需要 up_level</b>：升级规则原来固定"沿汇报线取<b>第一个</b>真实负责人"，
     * 而 {@code wf_node_config.up_level} 有 607 行配 1 级、3 行配 2 级、2 行配 3 级——后 5 行配的是
     * "往上第 2/3 级"，引擎却一律升到第 1 级，等于那几行配置<b>从未生效</b>（死列）。
     * 典型危害：某节点超时本应升到分管副总，实际只升到部门经理 → 部门经理同样无权处置，
     * 单子在汇报线上原地打转，且因 {@code alreadyEscalated} 只升一次，再也不会往上走。
     *
     * <p>语义：先沿提交人部门汇报线自下而上收集"真实负责人"（跳过提交人本人、跳过 admin、
     * 同一人只计一次、防环最多 10 跳），取<b>第 upLevel 级</b>；级数不足时取已收到的最高一级
     * （best-effort：配了 3 级但只有 2 级可升，升到第 2 级，而不是放弃升级退化为催办）。
     * 汇报线一级都取不到时再退"单据所属公司的组织负责人 ORG_HEAD"，全部落空返回 null，
     * 由调用方按"无可用对象"降级处理。
     *
     * @param upLevel 升级层级；null / &lt;1 一律按 1 级处理（与旧规则逐字一致，向后兼容）
     */
    public Long escalateTarget(String procInstId, Integer upLevel) {
        int level = (upLevel == null || upLevel < 1) ? 1 : upLevel;
        try {
            WfInstance inst = findInstance(procInstId);
            Long applicant = inst == null ? null : inst.getStartUserId();
            if (applicant != null) {
                SysUser au = sysUserMapper.selectById(applicant);
                Long deptId = au == null ? null : au.getDeptId();
                Set<Long> visited = new HashSet<>();
                // 沿汇报线自下而上收集真实负责人（含首跳）：命中首个即返回取不到第 N 级，
                // 所以先收链再按 up_level 取人。
                List<Long> chain = new ArrayList<>();
                for (int i = 0; i < 10 && deptId != null && visited.add(deptId); i++) {
                    SysDept dept = sysDeptMapper.selectById(deptId);
                    if (dept == null) {
                        break;
                    }
                    Long leader = dept.getLeaderId();
                    if (leader != null && !leader.equals(applicant) && !chain.contains(leader)
                            && isRealApprover(leader)) {
                        chain.add(leader);
                    }
                    deptId = dept.getParentId();
                }
                if (!chain.isEmpty()) {
                    int idx = Math.min(level, chain.size()) - 1;
                    return chain.get(idx);
                }
            }
            Long company = resolveCompany(procInstId);
            if (company != null) {
                // 组织负责人同样要跳过发起人：deny_self 开着时把单子升级回提交人自己＝静默死锁，
                // 不如按"定位不到"处理，走 ERROR 分支给出明确的补岗提示。
                for (Long head : userIdsInCompanyWithRole(company, "ORG_HEAD")) {
                    if (!head.equals(applicant)) {
                        return head;
                    }
                }
            }
            // 提单人＝本公司唯一 ORG_HEAD（顶级部门/公司负责人自己提单）时，上面被跳过；
            // 若集团内其他公司（如基地公司）也有 ORG_HEAD 持有人，按公司维度过滤会把他一并排除——
            // 兜底彻底落空、单据静默卡死。
            // 放宽为集团级 ORG_HEAD（仍排除发起人、仅认真实在岗人），与手册规则一致：
            // 申请人＝部门负责人本人时，直属上级节上收给集团内单位/基地负责人。
            for (Long head : userIdsGlobalWithRole("ORG_HEAD")) {
                if (!head.equals(applicant) && isRealApprover(head)) {
                    log.warn("ESCALATE 本公司无可用组织负责人，放宽至集团级 ORG_HEAD 兜底: proc={} -> userId={}",
                            procInstId, head);
                    return head;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("ESCALATE 升级对象解析失败 proc={}: {}", procInstId, e.getMessage());
            return null;
        }
    }

    /**
     * 「解析不出人」是否只因自审回避：候选角色在本公司/集团内确有在岗持有人，
     * 但持有人<b>全部</b>＝本单提单人 ⇒ {@code tryResolveAssignee} 按 剔完提单人后为空。
     *
     * <p>只用于把成因写进日志与提示，不参与任何判定；查询异常一律按"不是自审回避"返回 false
     * （fail-safe 回落到原判定，绝不因旁支信息盖掉主判定）。
     */
    private boolean onlyHolderIsApplicant(String procInstId, Set<String> groups) {
        try {
            Long applicant = resolveApplyUserId(procInstId);
            if (applicant == null || groups == null || groups.isEmpty()) {
                return false;
            }
            Long company = resolveCompany(procInstId);
            for (String g : groups) {
                if (g == null || g.isBlank() || "DIRECT_SUPERVISOR".equals(g)) {
                    continue;
                }
                String scope = roleScopes(Collections.singletonList(g)).getOrDefault(g, "COMPANY");
                List<Long> holders = "GROUP".equalsIgnoreCase(scope)
                        ? userIdsGlobalWithRole(g)
                        : (company == null ? List.of() : userIdsInCompanyWithRole(company, g));
                if (!holders.isEmpty() && holders.stream().allMatch(applicant::equals)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.debug("自审回避成因判定失败 proc={}: {}", procInstId, e.getMessage());
            return false;
        }
    }

    /** ESCALATE 日志的成因短句（两种成因的处置动作相反，不能混写） */
    private String escalateCause(String procInstId, Set<String> groups) {
        return onlyHolderIsApplicant(procInstId, groups)
                ? "候选岗位唯一在岗持有人即提单人本人（自审回避剔除）"
                : "在本单据公司内该候选岗位无在岗持有人";
    }

    /**
     * "节点取不到人"的可执行提示。只说"未配置到可办理人员"，用户看完不知道该找谁；
     * 本方法把三件事讲清楚：<b>缺哪个岗</b>（角色中文名 + 编码）、<b>哪家公司的</b>（单据所属公司名）、
     * <b>找谁补</b>（本公司组织负责人姓名，取不到就指向流程管理员）。
     *
     * <p>整个方法体裹在 try 里：拼提示属于旁支信息，任何查询失败都不能盖掉真正的业务异常。
     *
     * @param extra 追加说明（如 ESCALATE 档定位升级对象也失败的分支），可为 null
     */
    private String missingAssigneeHint(Task task, Set<String> groups, String extra) {
        StringBuilder sb = new StringBuilder();
        sb.append("节点「").append(task.getName()).append("」");
        if (extra != null && !extra.isBlank()) {
            sb.append(extra);
        }
        // 成因分档：岗位有人但唯一持有人＝提单人本人，是"自审回避"而非"缺人"。
        // 两者给用户的动作相反（前者等本人办结/找他人改派，后者去补岗位在岗人员），文案混为一谈会让人白改主数据。
        if (onlyHolderIsApplicant(task.getProcessInstanceId(), groups)) {
            sb.append("：岗位 ").append(roleLabels(groups))
                    .append(" 在本单据所属公司内仅在岗 1 人，且正是该单据的提交人——按自审回避不得自审，请等待其办结或由主管改派他人。");
        } else {
            sb.append("：本单据所属公司内缺少岗位 ").append(roleLabels(groups)).append(" 的在岗人员。");
        }
        String company = null;
        String heads = null;
        try {
            Long cid = resolveCompany(task.getProcessInstanceId());
            if (cid == null) {
                company = "（本单据未挂公司维度）";
            } else {
                SysCompany c = sysCompanyMapper.selectById(cid);
                company = "（公司：" + (c == null || c.getCompanyName() == null ? "ID " + cid : c.getCompanyName()) + "）";
                List<Long> headIds = userIdsInCompanyWithRole(cid, "ORG_HEAD");
                if (!headIds.isEmpty()) {
                    List<String> ns = new ArrayList<>();
                    for (SysUser u : sysUserMapper.selectBatchIds(headIds.subList(0, Math.min(headIds.size(), 3)))) {
                        ns.add(u.getNickName() == null || u.getNickName().isBlank() ? u.getUserName() : u.getNickName());
                    }
                    heads = String.join("、", ns);
                }
            }
        } catch (Exception e) {
            log.debug("补岗提示的公司/负责人解析失败 task={}: {}", task.getId(), e.getMessage());
        }
        sb.append(company == null ? "" : company);
        sb.append(heads == null
                ? "请由流程管理员在「系统管理 > 角色授权」为该岗补人（本节点未配置到真人前不可办理），"
                    + "或在「我的待办 > 改派」直接指定办理人。"
                : "请联系本公司组织负责人 " + heads + " 在「系统管理 > 角色授权」为该岗补人，"
                    + "或由流程管理员在「我的待办 > 改派」直接指定办理人。");
        return sb.toString();
    }

    /** 角色编码 → "中文名(编码)" 逗号串；查不到角色的编码原样列出（候选组也可能不是角色码） */
    private String roleLabels(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "（无候选组）";
        }
        Map<String, String> nameByCode = new HashMap<>();
        try {
            for (SysRole r : sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                    .in(SysRole::getRoleCode, codes))) {
                nameByCode.put(r.getRoleCode(), r.getRoleName());
            }
        } catch (Exception e) {
            log.debug("角色名查询失败: {}", e.getMessage());
        }
        return codes.stream()
                .map(c -> {
                    String n = nameByCode.get(c);
                    return n == null || n.isBlank() ? c : n + "(" + c + ")";
                })
                .collect(Collectors.joining("、"));
    }

    /** 真实可办理账号：NORMAL 且非 admin（is_admin=1 或 user_name='admin' 一律排除，守铁律）。 */
    private boolean isRealApprover(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser u = sysUserMapper.selectById(userId);
        return u != null
                && "NORMAL".equals(u.getStatus())
                && (u.getIsAdmin() == null || u.getIsAdmin() == 0)
                && !"admin".equals(u.getUserName());
    }

    /**
     * ROLE 节点尝试解析出唯一办理人（在 addCandidateGroup 之前调用）。
     * 解析不出（多人候选/无公司归属/GROUP 角色/数据缺失）返回 null，维持候选组机制。
     *
     * <p><b>不把节点指派给提单人本人</b>：deny_self 是 fail-closed 的，一旦 AUTO 指派
     * 命中"角色首个持有人 = 提单人"，该节点就没人能从待办里办（已指派态不写 candidate_ids，同角色
     * 其他人的待办里看不到这张单），只能靠人工改派绕行。这里改为取"首个非提单人持有人"；该角色只有
     * 提单人一人时返回 null，落回候选组 + 节点 empty_strategy 处置（ESCALATE 沿汇报线上收）。
     */
    public Long tryResolveAssignee(String procInstId, String roleCode) {
        try {
            WfInstance inst = findInstance(procInstId);
            if (inst == null) {
                return null;
            }
            if ("DIRECT_SUPERVISOR".equals(roleCode)) {
                // 直线上级：沿部门汇报线取部门负责人
                return directSupervisorOf(inst.getStartUserId());
            }
            Long applicant = resolveApplyUserId(procInstId);
            String scope = roleScopes(Collections.singletonList(roleCode))
                    .getOrDefault(roleCode, "COMPANY");
            if ("GROUP".equalsIgnoreCase(scope)) {
                // 集团/共享角色：跨公司候选组。当前运行模式下候选组待办对持有人不可见
                // （Flowable 7 候选组查询未命中），改为"全局首个持有人"直接指派，保证流程按节点推进不断流；
                // 候选组仍保留可兜底。先本公司、本公司无人再回落全网（见 companyFirst）。
                return pickByStrategy(inst.getModelKey(), roleCode, companyFirst(
                        userIdsGlobalWithRole(roleCode), resolveCompany(procInstId), roleCode), applicant);
            }
            Long company = resolveCompany(procInstId);
            if (company == null) {
                return null;
            }
            List<Long> userIds = userIdsInCompanyWithRole(company, roleCode);
            // 不能只在"恰好 1 人"时才指派：候选组待办不可见会让流程卡死，
            // 故一律按用户 id 取首个持有人确定性指派（候选组仍保留，其余持有人仍可依权办理）。
            // 多持有人时的落点可由 wf.assign.strategy 改为负载均衡/轮询，缺省仍是 id 升序首个。
            return pickByStrategy(inst.getModelKey(), roleCode, userIds, applicant);
        } catch (Exception e) {
            log.warn("节点办理人解析失败 proc={} role={}: {}", procInstId, roleCode, e.getMessage());
            return null;
        }
    }

    /** 取列表中第一个不是提单人的人（applicant 为 null 时即原"首个持有人"语义） */
    private static Long firstExcept(List<Long> ids, Long applicant) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        if (applicant == null) {
            return ids.get(0);
        }
        return ids.stream().filter(x -> x != null && !applicant.equals(x)).findFirst().orElse(null);
    }

    /**
     * GROUP 档"本公司优先"重排：把单据所属公司的持有人排到全网持有人名单前面。
     *
     * <p><b>只改顺序、不改成员</b>——资格名单仍是跨公司全集，因此本公司有人时首个指派落在本公司，
     * 本公司无人（或单据没有公司范围）时顺序保持原样，不会造出"无人可办"的死节点。
     * 同档内保持 {@code userIdsGlobalWithRole} 的 id 升序（List.sort 稳定）。
     */
    private List<Long> companyFirst(List<Long> holders, Long companyId, String roleCode) {
        if (holders == null || holders.size() < 2 || companyId == null) {
            return holders == null ? List.of() : holders;
        }
        try {
            Set<Long> local = new HashSet<>(userIdsInCompanyWithRole(companyId, roleCode));
            if (local.isEmpty() || local.containsAll(holders)) {
                return holders;
            }
            List<Long> out = new ArrayList<>(holders);
            out.sort((a, b) -> Integer.compare(local.contains(a) ? 0 : 1, local.contains(b) ? 0 : 1));
            log.info("GROUP 取人本公司优先: role={} company={} 全网={} 本公司={} 首选={}",
                    roleCode, companyId, holders, local, out.get(0));
            return out;
        } catch (Exception e) {
            log.warn("GROUP 取人本公司优先排序失败，按原全网顺序处理 role={} company={}: {}",
                    roleCode, companyId, e.getMessage());
            return holders;
        }
    }

    /**
     * 按 {@code wf.assign.strategy} 从角色持有人里挑指派对象。
     *
     * <p>三条不变量，缺省路径逐条守：
     * <ol>
     *  <li><b>先剔提单人</b>：所有策略都在"非提单人"子集里选，与 {@code firstExcept} 同一规则
     *      （AUTO 指派命中提单人会让该节点谁都办不动）；剔完为空返回 null，
     *      落回候选组 + {@code empty_strategy} 兜底，行为不变；</li>
     *  <li><b>不改候选资格</b>：只改"给谁"，不改"谁能办"，{@code canOperate} 与候选组一律不动；</li>
     *  <li><b>缺省＝现行为</b>：策略为 FIRST_ID、模型不在灰度清单、候选人 ≤1、或读取配置失败，
     *      一律走 id 升序首个——负载均衡是叠加能力，不是新风险面。</li>
     * </ol>
     */
    private Long pickByStrategy(String modelKey, String roleCode, List<Long> userIds, Long applicant) {
        List<Long> eligible = userIds == null ? List.of()
                : (applicant == null ? userIds
                        : userIds.stream().filter(x -> x != null && !applicant.equals(x)).toList());
        if (eligible.isEmpty()) {
            return null;
        }
        if (eligible.size() == 1) {
            return eligible.get(0);
        }
        AssignConfig cfg = assignConfig();
        if (cfg.strategy() == AssignStrategy.FIRST_ID
                || modelKey == null || !cfg.models().contains(modelKey)) {
            return eligible.get(0);
        }
        Long picked = switch (cfg.strategy()) {
            case LEAST_LOADED -> leastLoaded(modelKey, eligible);
            case ROUND_ROBIN -> roundRobin(modelKey, roleCode, eligible);
            default -> eligible.get(0);
        };
        log.info("取人策略生效: model={} role={} strategy={} 候选={} -> 指派={}",
                modelKey, roleCode, cfg.strategy(), eligible, picked);
        return picked;
    }

    /** 负载最轻者：按 {@code wf_task} 当前 PENDING 数一次批量取，并列时按 id 升序（＝FIRST_ID 规则） */
    private Long leastLoaded(String modelKey, List<Long> eligible) {
        Map<Long, Integer> load = new HashMap<>();
        for (Long id : eligible) {
            load.put(id, 0);
        }
        try {
            List<WfTask> rows = wfTaskMapper.selectList(new LambdaQueryWrapper<WfTask>()
                    .select(WfTask::getAssigneeId)
                    .eq(WfTask::getStatus, "PENDING")
                    .in(WfTask::getAssigneeId, eligible));
            if (rows != null) {
                for (WfTask t : rows) {
                    if (t.getAssigneeId() != null && load.containsKey(t.getAssigneeId())) {
                        load.merge(t.getAssigneeId(), 1, Integer::sum);
                    }
                }
            }
        } catch (Exception e) {
            // 负载读不到就退回原规则：宁可压在老落点上，也不因统计失败把单子派给不确定的人
            log.warn("取人负载统计失败，回落 FIRST_ID 口径 model={}: {}", modelKey, e.getMessage());
            return eligible.get(0);
        }
        return eligible.stream()
                .min(Comparator.comparingInt((Long id) -> load.getOrDefault(id, 0))
                        .thenComparing(Function.identity()))
                .orElse(eligible.get(0));
    }

    /**
     * 轮询：同一「租户 + 流程 + 角色」上一次派给谁，这次从下一位开始。
     *
     * <p>游标放进程内而不走 {@code DistributedState}：该 SPI 只有缓存/限流/标记/锁四种语义，
     * 没有"自增并返回"的计数语义（{@code tryAcquireRate} 返回的是放行与否，拿不到序号），
     * 而现网单实例部署（{@code fssc.redis.enabled=false}）。重启后游标归零只影响下一轮起点，
     * 不影响正确性；多实例部署时轮询退化为"各自轮询"，仍不会削弱候选规则。
     */
    private Long roundRobin(String modelKey, String roleCode, List<Long> eligible) {
        String key = TenantContext.getTenantId() + "|" + modelKey + "|" + roleCode;
        int idx = Math.floorMod(
                ROUND_ROBIN_CURSOR.computeIfAbsent(key, k -> new AtomicInteger())
                        .getAndIncrement(),
                eligible.size());
        return eligible.get(idx);
    }

    /** 取人策略配置快照（5 秒 TTL）：任何异常都按"未生效"处理＝FIRST_ID + 空清单。 */
    private AssignConfig assignConfig() {
        long now = System.currentTimeMillis();
        AssignConfig cached = assignConfig;
        if (cached != null && now - assignConfigAt < CONFIG_TTL_MILLIS) {
            return cached;
        }
        AssignConfig fresh = new AssignConfig(
                AssignStrategy.parse(readConfig(ASSIGN_STRATEGY_KEY)),
                csvToSet(readConfig(ASSIGN_MODELS_KEY)));
        assignConfig = fresh;
        assignConfigAt = now;
        return fresh;
    }

    private String readConfig(String key) {
        try {
            SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, key).last("limit 1"));
            return c == null || c.getConfigValue() == null ? null : c.getConfigValue().trim();
        } catch (Exception ex) {
            log.error("读取取人/委托配置异常，按未生效处理 {}: {}", key, ex.getMessage());
            return null;
        }
    }

    private static Set<String> csvToSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split("[,，;；\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 只读：某角色在当前单据规则下的<b>全部</b>有效候选人。规则与
     * {@link #tryResolveAssignee} 完全同源（GROUP 跨公司 / COMPANY 按单据公司 / 排除停用与 admin），
     * 唯一区别是返回整份名单而非第一个。
     *
     * <p>存在的意义：现指派逻辑只取首个持有人，审批人看不到"这个节点本来有谁能办"，
     * 于是出现"单子不知道流到谁手上"。此方法把名单显式暴露给待办接口，不参与任何指派判断。
     */
    public List<Long> listRoleCandidates(String procInstId, String roleCode) {
        try {
            WfInstance inst = findInstance(procInstId);
            if (inst == null) {
                return listRoleCandidatesInScope(null, null, roleCode);
            }
            return listRoleCandidatesInScope(companyIdOfBill(inst.getBizType(), inst.getBizId()),
                    inst.getStartUserId(), roleCode);
        } catch (Exception e) {
            log.warn("节点候选人查询失败 proc={} role={}: {}", procInstId, roleCode, e.getMessage());
            return List.of();
        }
    }

    /**
     * 无运行实例规则的节点候选人（提单态"下一环节给谁"用）：公司取草稿单据所属公司、直线上级取提单人，
     * 其余与 {@link #listRoleCandidates} 完全同源（GROUP 跨公司 / COMPANY 按公司 / 排除停用与 admin）。
     *
     * <p>为什么不能直接复用按 {@code procInstId} 的那份：点名发生在 {@code startProcessInstance} <b>之前</b>，
     * 此刻既没有流程实例也没有 {@code wf_instance}，只能从单据本身取组织上下文。
     */
    public List<Long> listRoleCandidatesInScope(Long companyId, Long applicantUserId, String roleCode) {
        try {
            if (roleCode == null || roleCode.isBlank()) {
                return List.of();
            }
            if ("DIRECT_SUPERVISOR".equals(roleCode)) {
                Long leader = directSupervisorOf(applicantUserId);
                return leader == null ? List.of() : List.of(leader);
            }
            String scope = roleScopes(Collections.singletonList(roleCode))
                    .getOrDefault(roleCode, "COMPANY");
            if ("GROUP".equalsIgnoreCase(scope)) {
                // 与指派同一规则的"本公司优先"排序，名单仍是全网全集（不收窄资格）。
                return companyFirst(userIdsGlobalWithRole(roleCode), companyId, roleCode);
            }
            return companyId == null ? List.of() : userIdsInCompanyWithRole(companyId, roleCode);
        } catch (Exception e) {
            log.warn("节点候选人查询失败 company={} role={}: {}", companyId, roleCode, e.getMessage());
            return List.of();
        }
    }

    /**
     * 直线上级：申请人所在部门的负责人（负责人=申请人本人时视为无上级，避免自审）。
     *
     * <p><b>只取 {@code sys_dept.leader_id} 唯一一人是不够的</b>：leader 为提单人或未配时若直接返回 null，
     * {@code wf_node_config.assign_value=DIRECT_SUPERVISOR} 的节点
     * （如 {@code INVOICE_ISSUE:Task_2「直属上级审核【部门负责人】」}）就<b>拿不到任何办理人</b> ⇒
     * 流程在该节点<b>必然死锁</b>（AUTO 档指派失败即断流）。公司只有一名 {@code ORG_HEAD}、
     * 而他本人提交单据时，就是这种形态。
     *
     * <p>做法（三级兜底，仍尊重自审）：
     * <ol>
     *   <li>正常：部门 leader，且 <b>不是提单人本人</b>；</li>
     *   <li>降级①：leader 缺失或恰为提单人 ⇒ 沿部门汇报线向上找第一个<b>不是提单人</b>的 leader；</li>
     *   <li>降级②：汇报线上仍无 ⇒ 取<b>同公司其他</b>的单位负责人（{@code ORG_HEAD}），
     *       排除提单人本人与 admin —— 保证「同公司至少有一个人能办」，
     *       <b>不跨公司</b>（避免把单派给无关组织的人）。</li>
     * </ol>
     * 三级都落空才返回 null（此时确无任何人可依职责办理，属真实组织缺失，交 empty_strategy 处置）。
     */
    private Long directSupervisorOf(Long applicantUserId) {
        if (applicantUserId == null) {
            return null;
        }
        SysUser au = sysUserMapper.selectById(applicantUserId);
        if (au == null) {
            return null;
        }
        // ① 正常路径：本部门 leader（非提单人）
        if (au.getDeptId() != null) {
            SysDept dept = sysDeptMapper.selectById(au.getDeptId());
            if (dept != null && dept.getLeaderId() != null && !dept.getLeaderId().equals(applicantUserId)) {
                return dept.getLeaderId();
            }
        }
        // 降级①：沿汇报线向上找第一个「不是提单人」的 leader
        Long byChain = firstLeaderAlongChain(au.getDeptId(), applicantUserId);
        if (byChain != null) {
            log.info("直线上级降级①：部门 leader 缺失或即提单人，沿汇报线上收 applicant={} → {}", applicantUserId, byChain);
            return byChain;
        }
        // 降级②：同公司其他单位负责人（避免节点因「独苗负责人自审」而无人可办）
        Long byOrgHead = otherOrgHeadOf(au.getCompanyId(), applicantUserId);
        if (byOrgHead != null) {
            log.info("直线上级降级②：汇报线无可用负责人，改派同公司其他单位负责人 applicant={} → {}", applicantUserId, byOrgHead);
            return byOrgHead;
        }
        return null;
    }

    /** 沿部门汇报线自下而上取第一个「非提单人」的 leader（防环，最多 10 跳）。 */
    private Long firstLeaderAlongChain(Long startDeptId, Long applicantUserId) {
        if (startDeptId == null) {
            return null;
        }
        Set<Long> visited = new HashSet<>();
        Long deptId = startDeptId;
        for (int i = 0; i < 10 && deptId != null && visited.add(deptId); i++) {
            SysDept dept = sysDeptMapper.selectById(deptId);
            if (dept == null) {
                break;
            }
            Long leader = dept.getLeaderId();
            if (leader != null && !leader.equals(applicantUserId) && isRealApprover(leader)) {
                return leader;
            }
            deptId = dept.getParentId();
        }
        return null;
    }

    /** 同公司（不含跨公司）其他单位/基地负责人，排除提单人本人与 admin。 */
    private Long otherOrgHeadOf(Long companyId, Long applicantUserId) {
        if (companyId == null) {
            return null;
        }
        try {
            List<Long> heads = userIdsInCompanyWithRole(companyId, "ORG_HEAD");
            for (Long id : heads) {
                if (id != null && !id.equals(applicantUserId) && isRealApprover(id)) {
                    return id;
                }
            }
        } catch (Exception e) {
            log.warn("直线上级降级②：查同公司单位负责人失败 companyId={}: {}", companyId, e.getMessage());
        }
        return null;
    }

    /**
     * 本节点<b>实际可办理</b>的真人名单：任务的候选组逐个走 {@link #listRoleCandidates}（同公司范围、
     * 排除停用与 admin）后取并集。与 {@link #canOperate} 的角色分支同源，因此"名单里的人"＝
     * "改派/转办过去之后一定办得动的人"，不会出现派过去却无人可办的悬空任务。
     *
     * <p>用途：转办弹窗的可选名单与目标校验。
     * 名单为空即"本节点没有别人可办"，出口是流程管理员改派，不是放开全局选人。
     */
    public List<Long> listOperableUserIds(Task task) {
        List<Long> out = new ArrayList<>();
        if (task == null) {
            return out;
        }
        for (String group : candidateGroups(task)) {
            for (Long id : listRoleCandidates(task.getProcessInstanceId(), group)) {
                if (id != null && !out.contains(id)) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    /**
     * 转办（L1）目标名单：{@link #listOperableUserIds} 的<b>规则补全</b>版，与 {@link #canOperate} 对齐。
     *
     * <p>缺陷原貌：TR_PAY「唯一办理人」节点里候选岗位在单据公司只有操作人自己，窄名单去掉本人后为空 ⇒
     * {@code approve.transfer_strict} 开启时弹窗显示"本节点没有其他人可办理"、服务端拒绝任何目标，
     * 待办列表却照旧渲染「转办」按钮（点得动、转不成）。窄名单漏掉的正是 canOperate 本来就放行的三类人：
     * ① 单据公司解析不到时（{@code roleHit} 的"无公司维度放行"）COMPANY 范围角色的全网持有人；
     * ② {@code company_id} 为空的集团级持有人（{@code roleHit} 的"集团级放行"）；
     * ③ 名单内人员与操作人<b>当前生效委托</b>的受托人（canOperate 第 3 步的借资格通道，
     * 同受 {@code wf_model.enable_delegate} 那道校验约束）。
     *
     * <p>不会因此造出死节点：转办落点是 {@code setAssignee}，目标人之后走"直接办理人"通道，必办得动。
     * 领取（claim）与智能推送、改派（L2）仍用窄名单——它们各自是收权规则，不随本方法放宽。
     */
    public List<Long> listTransferTargets(Task task, Long operator) {
        List<Long> out = new ArrayList<>(listOperableUserIds(task));
        if (task == null) {
            return out;
        }
        try {
            Set<String> groups = candidateGroups(task);
            if (!groups.isEmpty()) {
                Map<String, String> scopes = roleScopes(new ArrayList<>(groups));
                Long billCompany = resolveCompany(task.getProcessInstanceId());
                for (String g : groups) {
                    if (g == null || "GROUP".equalsIgnoreCase(scopes.getOrDefault(g, "COMPANY"))) {
                        continue; // GROUP 域窄名单已是跨公司范围
                    }
                    for (Long id : (billCompany == null ? userIdsGlobalWithRole(g)
                            : userIdsWithNoCompanyAndRole(g))) {
                        if (id != null && !out.contains(id)) {
                            out.add(id);
                        }
                    }
                }
            }
            if (delegateAllowedFor(task)) {
                List<Long> principals = new ArrayList<>(out);
                if (operator != null && !principals.contains(operator)) {
                    principals.add(operator);
                }
                for (Long pid : principals) {
                    for (Long agent : delegateAgents(pid).keySet()) {
                        if (agent != null && !out.contains(agent)) {
                            out.add(agent);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("转办目标名单补齐失败 task={}: {}", task.getId(), e.getMessage());
        }
        // 剔除本节点已投票者——转办/加签弹窗落给刚投过票的人＝同一节点二次出票，
        // 会签独立性受损。查询失败不静默放行（放出去的就是本缺陷），直接冒泡拒答名单。
        Set<Long> voted = new LinkedHashSet<>();
        for (WfTaskHistory h : wfQueryService.listHistory(
                new LambdaQueryWrapper<WfTaskHistory>()
                        .eq(WfTaskHistory::getProcInstId, task.getProcessInstanceId())
                        .eq(WfTaskHistory::getNodeKey, task.getTaskDefinitionKey())
                        .in(WfTaskHistory::getAction, "APPROVE", "REJECT", "AUTO_PASS"))) {
            if (h.getOperUserId() != null) {
                voted.add(h.getOperUserId());
            }
        }
        if (!voted.isEmpty()) {
            out.removeIf(voted::contains);
        }
        return out;
    }

    /** 集团级持有人（{@code company_id} 为空）：与 {@code roleHit} 的"集团级放行"同一规则，任何公司的单据都能办。 */
    private List<Long> userIdsWithNoCompanyAndRole(String roleCode) {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode).last("limit 1"));
        if (role == null) {
            return List.of();
        }
        List<Long> userIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getId())).stream()
                .map(SysUserRole::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
                        .isNull(SysUser::getCompanyId)
                        .eq(SysUser::getStatus, "NORMAL")
                        .ne(SysUser::getUserName, "admin")
                        .eq(SysUser::getIsAdmin, 0)
                        .orderByAsc(SysUser::getId)).stream()
                .map(SysUser::getId).toList();
    }

    /**
     * 配置态体检：某角色<b>在各公司的在职非 admin 持有人矩阵</b>。只读，不参与任何指派判断。
     *
     * <p>为什么做这个而不是"候选人人数预览"：ROLE 节点的候选人常有多个，
     * {@code COMMON_EMP} 这类大角色被当审批人挂在很多节点上，配置时谁都没发现——落点由
     * {@code sys_user.id} 升序取首个决定。本接口把同一规则变成配置页保存时的默认提示：哪些公司 0 人
     * （单据会走 empty_strategy）、哪些公司 ≥2 人（落点靠主键顺序）。不需要单据上下文，
     * 因此绕开了"配置弹窗没有公司范围"的死结。
     *
     * <p>持有人规则与 {@link #userIdsGlobalWithRole} 一致：status=NORMAL、排除 admin 与 is_admin=1。
     */
    public Map<String, Object> roleHolderMatrix(String roleCode) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roleCode", roleCode);
        out.put("companies", List.of());
        out.put("total", 0);
        out.put("coveredCompanies", 0);
        out.put("multiHolderCompanies", 0);
        if (roleCode == null || roleCode.isBlank()) {
            out.put("roleExists", false);
            out.put("note", "未指定角色编码");
            return out;
        }
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode.trim()).last("limit 1"));
        if (role == null) {
            out.put("roleExists", false);
            out.put("note", "角色编码在 sys_role 中不存在：挂这个码的节点任何单据都不会有人可办");
            return out;
        }
        out.put("roleExists", true);
        out.put("roleName", role.getRoleName());
        String scope = role.getOrgScope() == null ? "COMPANY" : role.getOrgScope();
        out.put("orgScope", scope);
        List<Long> userIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getId())).stream()
                .map(SysUserRole::getUserId).distinct().toList();
        List<SysUser> holders = userIds.isEmpty() ? List.of() : sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
                        .eq(SysUser::getStatus, "NORMAL")
                        .eq(SysUser::getIsAdmin, 0)
                        .ne(SysUser::getUserName, "admin")
                        .orderByAsc(SysUser::getId));
        out.put("total", holders.size());
        out.put("assignedTotal", userIds.size());

        Map<Long, List<SysUser>> byCompany = new LinkedHashMap<>();
        for (SysUser u : holders) {
            byCompany.computeIfAbsent(u.getCompanyId(), k -> new ArrayList<>()).add(u);
        }
        List<Long> companyIds = byCompany.keySet().stream().filter(Objects::nonNull).toList();
        Map<Long, SysCompany> companyInfo = new HashMap<>();
        if (!companyIds.isEmpty()) {
            try {
                for (SysCompany c : sysCompanyMapper.selectBatchIds(companyIds)) {
                    companyInfo.put(c.getId(), c);
                }
            } catch (Exception e) {
                log.warn("角色持有人矩阵：公司名查询失败 role={}: {}", roleCode, e.getMessage());
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int multi = 0;
        for (Map.Entry<Long, List<SysUser>> e : byCompany.entrySet()) {
            Long cid = e.getKey();
            List<SysUser> list = e.getValue();
            if (list.size() > 1) {
                multi++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("companyId", cid);
            SysCompany c = cid == null ? null : companyInfo.get(cid);
            row.put("companyCode", c == null ? null : c.getCompanyCode());
            row.put("companyName", cid == null ? "集团级（不分公司）"
                    : (c == null ? "公司 " + cid + "（已停用或已删除）" : c.getCompanyName()));
            row.put("holders", list.size());
            row.put("names", list.stream().limit(8)
                    .map(u -> u.getNickName() == null || u.getNickName().isBlank() ? u.getUserName() : u.getNickName())
                    .toList());
            row.put("namesTruncated", list.size() > 8);
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare(
                a.get("companyId") == null ? Long.MAX_VALUE : ((Long) a.get("companyId")),
                b.get("companyId") == null ? Long.MAX_VALUE : ((Long) b.get("companyId"))));
        out.put("companies", rows);
        out.put("coveredCompanies", companyIds.size());
        out.put("multiHolderCompanies", multi);
        try {
            out.put("activeCompanies", sysCompanyMapper.selectCount(new LambdaQueryWrapper<SysCompany>()
                    .eq(SysCompany::getStatus, "NORMAL")));
        } catch (Exception e) {
            // 活跃公司查询失败降级为 null（调用方按「无数据」渲染），不因单点查询失败中断节点解析
            out.put("activeCompanies", null);
        }
        return out;
    }

    // 内部

    /** 候选组 ∩ 用户角色，并按 GROUP 范围/单据公司维度判定是否命中（越权守卫的角色分支）。 */
    private boolean roleHit(Task task, Long userId, List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        // DIRECT_SUPERVISOR 是「取人方式」不是岗位：节点办理人由 tryResolveAssignee 沿部门汇报线
        // 精确解析为"发起人的直属上级"。若在此处放行，则任何绑定了该角色的账号都能凭 taskId
        // 办理任意发起人的单据（跨人越权）。故候选组命中判定必须排除它——
        // 即便数据层再次误绑，本层也不给越权通道（纵深防御）。
        Set<String> hit = candidateGroups(task).stream()
                .filter(roleCodes::contains)
                .filter(g -> !"DIRECT_SUPERVISOR".equals(g))
                .collect(Collectors.toSet());
        if (hit.isEmpty()) {
            return false;
        }
        Map<String, String> scopes = roleScopes(new ArrayList<>(hit));
        boolean groupWide = hit.stream().anyMatch(g -> "GROUP".equalsIgnoreCase(scopes.getOrDefault(g, "COMPANY")));
        if (groupWide) {
            return true;
        }
        Long billCompany = resolveCompany(task.getProcessInstanceId());
        if (billCompany == null) {
            return true; // 无公司维度放行
        }
        SysUser u = sysUserMapper.selectById(userId);
        if (u == null || u.getCompanyId() == null) {
            return true; // 集团级放行
        }
        return billCompany.equals(u.getCompanyId());
    }

    /** 用户全部角色编码（查法同 todo()：sys_user_role→sys_role）。 */
    private List<String> userRoleCodes(Long userId) {
        try {
            List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getUserId, userId)).stream()
                    .map(SysUserRole::getRoleId).distinct().toList();
            if (roleIds.isEmpty()) {
                return List.of();
            }
            return sysRoleMapper.selectBatchIds(roleIds).stream()
                    .map(SysRole::getRoleCode).filter(Objects::nonNull).toList();
        } catch (Exception e) {
            log.warn("查询用户角色失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 委托通道取委托人（接真）：先过 {@code wf_model.enable_delegate} 这道流程级开关，
     * 关掉＝该流程不支持委托代办，第 3 步与 POOL 锁定豁免一并跳过。
     *
     * <p>这个开关的意义是让它从假开关变成真开关；当前数据里该列全为 1，所以行为与不读它时一致。
     */
    private List<Long> delegateFromUserIds(Task task, Long toUserId) {
        return delegateAllowedFor(task) ? activeDelegateFromUserIds(toUserId) : List.of();
    }

    /** 本任务所属流程是否允许委托代办；清单为空（常态）时不查实例，零额外开销。 */
    public boolean delegateAllowedFor(Task task) {
        Set<String> off = delegateDisabledModels();
        if (off.isEmpty()) {
            return true;
        }
        if (task == null) {
            return true;
        }
        WfInstance inst = findInstance(task.getProcessInstanceId());
        // 解析不到实例/模型时按"允许"处理：这是功能开关而非权限判定，不该因查询失败收紧可办人
        return inst == null || inst.getModelKey() == null || !off.contains(inst.getModelKey());
    }

    /**
     * {@code wf_model.enable_delegate = 0} 的流程清单（5 秒快照）。
     * 读取失败返回空集＝所有流程都支持委托（开关不该因一次查询失败收紧可办人）。
     */
    public Set<String> delegateDisabledModels() {
        long now = System.currentTimeMillis();
        Set<String> cached = delegateOffModels;
        if (cached != null && now - delegateOffAt < CONFIG_TTL_MILLIS) {
            return cached;
        }
        Set<String> fresh = Set.of();
        try {
            fresh = wfModelMapper.selectList(new LambdaQueryWrapper<WfModel>()
                            .select(WfModel::getModelKey)
                            .eq(WfModel::getEnableDelegate, false)).stream()
                    .map(WfModel::getModelKey)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (Exception e) {
            log.warn("读取 wf_model.enable_delegate 失败，按全流程支持委托处理: {}", e.getMessage());
        }
        delegateOffModels = fresh;
        delegateOffAt = now;
        return fresh;
    }

    /** 当前生效委托的委托人 ID（受托人=userId、NORMAL、时间区间内）。 */
    private List<Long> activeDelegateFromUserIds(Long toUserId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            return wfDelegateMapper.selectList(new LambdaQueryWrapper<WfDelegate>()
                            .eq(WfDelegate::getToUserId, toUserId)
                            .eq(WfDelegate::getStatus, "NORMAL")
                            .le(WfDelegate::getBeginTime, now)
                            .ge(WfDelegate::getEndTime, now)).stream()
                    .map(WfDelegate::getFromUserId)
                    .filter(Objects::nonNull).distinct().toList();
        } catch (Exception e) {
            log.warn("查询生效委托失败 toUserId={}: {}", toUserId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 委托语义快照（5 秒 TTL）：模式 + 灰度清单 + 当前生效的「委托人 → 受托人」映射。
     *
     * <p>三者一起换：改派判定既要"模式为 REASSIGN、模型在灰度清单内"，也要"此刻确有生效委托"，
     * 分三次查会读到跨时刻的半新半旧组合（委托刚被取消却仍按旧映射替换取人）。
     *
     * <p>任何异常按 {@code BORROW + 空清单 + 空映射} 处理（借资格、不改派），不改动既有可办性。
     */
    private DelegateMode delegateModeSnapshot() {
        long now = System.currentTimeMillis();
        DelegateMode cached = delegateMode;
        if (cached != null && now - delegateModeAt < CONFIG_TTL_MILLIS) {
            return cached;
        }
        String mode = readConfig(DELEGATE_MODE_KEY);
        DelegateMode fresh = new DelegateMode(
                mode == null || mode.isBlank() ? DELEGATE_MODE_BORROW : mode.trim().toUpperCase(),
                csvToSet(readConfig(DELEGATE_MODE_MODELS_KEY)),
                activeDelegateMap());
        delegateMode = fresh;
        delegateModeAt = now;
        return fresh;
    }

    /** 本流程是否走"改派"语义：模式为 REASSIGN <b>且</b>该模型在灰度清单内（清单空＝全库不生效）。 */
    public boolean delegateReassignEnabled(String modelKey) {
        DelegateMode m = delegateModeSnapshot();
        // enable_delegate=0 的流程"根本不支持委托代办"（刚接真的开关）：改派档也不能替它做委托，
        // 否则灰度一开就绕过那条开关。
        return DELEGATE_MODE_REASSIGN.equals(m.mode())
                && modelKey != null && m.models().contains(modelKey)
                && !delegateDisabledModels().contains(modelKey);
    }

    /**
     * 改派档下的<b>排他</b>判定（委托人侧不再重复可办）：
     * 我的在办任务已被我的受托人接管（当前办理人＝我的受托人）⇒ 我不再可办，
     * 候选组角色命中与借资格通道一并关闭。
     *
     * <p>缺省（模式非 REASSIGN 或灰度清单为空）在读快照后立刻返回 false，
     * <b>不查实例、不改任何可办性</b>；只在灰度模型内生效，与取人替换同一份规则。
     */
    private boolean delegateTakenOverFrom(Task task, Long userId) {
        DelegateMode m = delegateModeSnapshot();
        if (!DELEGATE_MODE_REASSIGN.equals(m.mode()) || m.models().isEmpty()) {
            return false;
        }
        Long to = m.fromTo().get(userId);
        if (to == null || task.getAssignee() == null || !String.valueOf(to).equals(task.getAssignee())) {
            return false;
        }
        String modelKey = modelKeyOf(task);
        return modelKey != null && m.models().contains(modelKey) && !delegateDisabledModels().contains(modelKey);
    }

    /** 任务所属模型 key（排他判定用）；解析不到实例/模型返回 null＝不排他（功能开关不该因查询失败收紧可办人）。 */
    private String modelKeyOf(Task task) {
        try {
            WfInstance inst = task == null ? null : findInstance(task.getProcessInstanceId());
            return inst == null ? null : inst.getModelKey();
        } catch (Exception e) {
            log.warn("取任务模型 key 失败 task={}: {}", task == null ? null : task.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 改派档是否<b>整体</b>启用（模式=REASSIGN 且灰度清单非空）：定时作业的前置短路。
     *
     * <p>缺省（库里没有 {@code wf.delegate.mode} 这一行）返回 false ⇒ 作业连待办都不查；
     * 逐模型收紧仍由 {@link #delegateReassignEnabled(String)} 在改派时判定。
     */
    public boolean delegateReassignActive() {
        DelegateMode m = delegateModeSnapshot();
        return DELEGATE_MODE_REASSIGN.equals(m.mode()) && !m.models().isEmpty();
    }

    /**
     * 委托到期后"未办结任务是否返还原办理人"——该行为由参数控制：
     * 「委托结束后，受托人未处理完的任务是否还返还给原处理人，<b>通过参数来控制</b>…
     * "任务委托结束后未处理任务返还原处理人"」。
     *
     * <p>取值：{@code REVERT}（缺省）＝返还原办理人／{@code KEEP}＝留在受托人名下不再回跳。
     *
     * <p><b>默认值属我方自定</b>：资料只写"有这一参数"，未给缺省取值。我方取保守侧——
     * 到期后单子回到委托人待办里，绝不因"参数没配"而永久滞留在别人的收件箱
     * （那正是"委托变吞单"的病相）。
     *
     * <p>只管到期：人工撤销（{@code WfDelegateService.cancel}）不受本参数控制——那是一次显式收回
     * 授权的动作，"把还压在受托人名下的待办收回来"是撤销语义本身，不给配成不收回的口子。
     */
    public boolean delegateRevertOnEndEnabled() {
        String v = readConfig(DELEGATE_REVERT_ON_END_KEY);
        return v == null || v.isBlank() || !"KEEP".equalsIgnoreCase(v.trim());
    }

    /** 当前生效的「委托人 → 受托人」映射（同一委托人有多条生效委托时取 id 最大的一条，与 {@code activeDelegateTo} 同一规则）。 */
    private Map<Long, Long> activeDelegateMap() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<WfDelegate> ds = wfDelegateMapper.selectList(new LambdaQueryWrapper<WfDelegate>()
                    .eq(WfDelegate::getStatus, "NORMAL")
                    .le(WfDelegate::getBeginTime, now)
                    .ge(WfDelegate::getEndTime, now)
                    .orderByAsc(WfDelegate::getId));
            Map<Long, Long> map = new HashMap<>();
            for (WfDelegate d : ds) {
                if (d.getFromUserId() != null && d.getToUserId() != null
                        && !d.getFromUserId().equals(d.getToUserId())) {
                    // 按 id 升序覆盖 ⇒ 最终留下最新建立的一条
                    map.put(d.getFromUserId(), d.getToUserId());
                }
            }
            return Map.copyOf(map);
        } catch (Exception e) {
            log.warn("读取生效委托映射失败，按不改派处理: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 取人结果的委托替换（"期内新任务"）：某人被解析为本节点办理人、而他在本流程灰度内
     * 有生效委托时，直接把办理人换成受托人——委托期内不再让委托人手上凭空多一节"谁也以为是别人办"的待办。
     *
     * <p>三重收窄，任一不满足都原样返回：
     * 模式非 REASSIGN / 该模型不在灰度清单 / 该人此刻没有生效委托。
     *
     * <p><b>刻意不做的事</b>：不改候选组规则（{@link #canOperate} 的委托通道仍按"借资格"放行），
     * 也不因为改派而放宽任何守卫——受托人办理时 {@code deny_self} 依旧按单据提单人判定，
     * 跨公司委托依旧 fail-closed 拦在建立环节。
     */
    public Long delegateSubstitute(String modelKey, Long resolvedUserId) {
        if (resolvedUserId == null || !delegateReassignEnabled(modelKey)) {
            return resolvedUserId;
        }
        Long to = delegateModeSnapshot().fromTo().get(resolvedUserId);
        return to == null || to.equals(resolvedUserId) ? resolvedUserId : to;
    }

    /** 待办标注用：本人作为受托人时的「委托人 → 姓名」映射（<b>只读</b>，不含模式判定，借资格档同样要能看出是代办）。 */
    public Map<Long, String> delegatePrincipals(Long toUserId) {
        Map<Long, String> out = new HashMap<>();
        if (toUserId == null) {
            return out;
        }
        try {
            List<Long> fromIds = activeDelegateFromUserIds(toUserId);
            if (fromIds.isEmpty()) {
                return out;
            }
            for (SysUser u : sysUserMapper.selectBatchIds(fromIds)) {
                out.put(u.getId(), displayName(u));
            }
        } catch (Exception e) {
            log.warn("委托代办标识取数失败 toUserId={}: {}", toUserId, e.getMessage());
        }
        return out;
    }

    /** 待办标注用：本人作为委托人时的「受托人 → 姓名」映射（借资格档需要让委托人看见"这单已交给谁代管"）。 */
    public Map<Long, String> delegateAgents(Long fromUserId) {
        Map<Long, String> out = new HashMap<>();
        if (fromUserId == null) {
            return out;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            List<WfDelegate> ds = wfDelegateMapper.selectList(new LambdaQueryWrapper<WfDelegate>()
                    .eq(WfDelegate::getFromUserId, fromUserId)
                    .eq(WfDelegate::getStatus, "NORMAL")
                    .le(WfDelegate::getBeginTime, now)
                    .ge(WfDelegate::getEndTime, now));
            if (ds.isEmpty()) {
                return out;
            }
            List<Long> toIds = ds.stream().map(WfDelegate::getToUserId)
                    .filter(Objects::nonNull).distinct().toList();
            for (SysUser u : sysUserMapper.selectBatchIds(toIds)) {
                out.put(u.getId(), displayName(u));
            }
        } catch (Exception e) {
            log.warn("委托代管标识取数失败 fromUserId={}: {}", fromUserId, e.getMessage());
        }
        return out;
    }

    /** 用户展示名：姓名优先，退登录名，再退用户 ID 字符串。 */
    private String displayName(SysUser u) {
        if (u == null) {
            return "";
        }
        if (u.getNickName() != null && !u.getNickName().isBlank()) {
            return u.getNickName();
        }
        return u.getUserName() == null ? String.valueOf(u.getId()) : u.getUserName();
    }

    private List<Long> userIdsInCompanyWithRole(Long companyId, String roleCode) {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode).last("limit 1"));
        if (role == null) {
            return List.of();
        }
        List<Long> userIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getId())).stream()
                .map(SysUserRole::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
                        .eq(SysUser::getCompanyId, companyId)
                        .eq(SysUser::getStatus, "NORMAL")
                        .ne(SysUser::getUserName, "admin")
                        .eq(SysUser::getIsAdmin, 0)
                        .orderByAsc(SysUser::getId)).stream()
                .map(SysUser::getId).toList();
    }

    /**
     * 集团/共享角色（GROUP 域）：跨公司解析全部有效持有人（按用户 id 升序，保证确定性指派）。
     *
     * <p>公开给超时卡单聚合告警取"某角色全网在岗真人"（收件人＝流程管理员 WF_ADMIN，
     * 该角色 {@code org_scope=COMPANY}，走 {@link #listRoleCandidatesInScope} 会因没有公司范围而空）。
     */
    public List<Long> userIdsGlobalWithRole(String roleCode) {
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode).last("limit 1"));
        if (role == null) {
            return List.of();
        }
        List<Long> userIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getId())).stream()
                .map(SysUserRole::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
                        .eq(SysUser::getStatus, "NORMAL")
                        .ne(SysUser::getUserName, "admin")
                        .eq(SysUser::getIsAdmin, 0)
                        .orderByAsc(SysUser::getId)).stream()
                .map(SysUser::getId).toList();
    }

    private Set<String> candidateGroups(Task t) {
        try {
            return flowTaskService.getIdentityLinksForTask(t.getId()).stream()
                    .filter(i -> "candidate".equals(i.getType()) && i.getGroupId() != null)
                    .map(IdentityLink::getGroupId).collect(Collectors.toSet());
        } catch (Exception e) {
            return Set.of();
        }
    }

    /**
     * 按任务批量取候选组（一次 IN 查询，500 个一批）。
     *
     * <p>取值口径与 {@link #candidateGroups(Task)} 逐字同源：只认 {@code TYPE_='candidate'}
     * 且 {@code GROUP_ID_} 非空的身份链接；查询失败的分批按"该批无候选组"处理
     * （上层 COMPANY 范围过滤会把它当无 GROUP 范围角色，与逐条查询失败时的行为一致）。
     */
    private Map<String, Set<String>> candidateGroupsByTasks(List<Task> tasks) {
        Map<String, Set<String>> out = new HashMap<>();
        List<String> ids = new ArrayList<>(tasks.size());
        for (Task t : tasks) {
            if (t != null && t.getId() != null) {
                ids.add(t.getId());
            }
        }
        for (int i = 0; i < ids.size(); i += 500) {
            List<String> chunk = ids.subList(i, Math.min(i + 500, ids.size()));
            String in = chunk.stream().map(x -> "?").collect(Collectors.joining(","));
            try {
                jdbcTemplate.query(
                        "SELECT TASK_ID_, GROUP_ID_ FROM ACT_RU_IDENTITYLINK"
                                + " WHERE TYPE_ = 'candidate' AND GROUP_ID_ IS NOT NULL AND TASK_ID_ IN (" + in + ")",
                        rs -> {
                            out.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2));
                        },
                        chunk.toArray());
            } catch (Exception e) {
                log.warn("候选组批量装载失败（本批按无候选组处理）: {}", e.getMessage());
            }
        }
        return out;
    }

    /**
     * 按任务批量解析「流程实例 → 单据公司」（一次 IN 查询，500 个一批）。
     *
     * <p>口径与 {@link #resolveCompany(String)} 同源：实例 bizType 经 {@code companyTable} 映射到单据表，
     * 取该行 {@code company_id}；实例缺失／映射外／company_id 为空的一律<b>不放入结果</b>，
     * 由调用方退回逐条 {@code resolveCompany}（含 creatorCompany 兜底），保证过滤口径一字不变。
     */
    private Map<String, Long> companiesByProc(List<Task> tasks) {
        Map<String, Long> out = new HashMap<>();
        List<String> procs = new ArrayList<>(tasks.size());
        Set<String> seen = new HashSet<>();
        for (Task t : tasks) {
            String p = t == null ? null : t.getProcessInstanceId();
            if (p != null && seen.add(p)) {
                procs.add(p);
            }
        }
        if (procs.isEmpty()) {
            return out;
        }
        // (表, bizId) → 公司：同一张单据被多条任务引用时只查一次
        Map<String, Map<Long, Long>> companyByTable = new HashMap<>();
        Map<String, String[]> billByProc = new HashMap<>();
        for (int i = 0; i < procs.size(); i += 500) {
            List<String> chunk = procs.subList(i, Math.min(i + 500, procs.size()));
            List<WfInstance> rows;
            try {
                rows = wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                        .select(WfInstance::getProcInstId, WfInstance::getBizType, WfInstance::getBizId)
                        .in(WfInstance::getProcInstId, chunk));
            } catch (Exception e) {
                log.warn("批量解析单据公司失败（wf_instance 段，本批走逐条兜底）: {}", e.getMessage());
                continue;
            }
            for (WfInstance r : rows) {
                if (r.getProcInstId() == null || r.getBizType() == null || r.getBizId() == null) {
                    continue;
                }
                String table = companyTable(r.getBizType());
                if (table == null) {
                    continue;
                }
                billByProc.put(r.getProcInstId(), new String[]{table, String.valueOf(r.getBizId())});
                companyByTable.computeIfAbsent(table, k -> new HashMap<>()).putIfAbsent(r.getBizId(), null);
            }
        }
        for (Map.Entry<String, Map<Long, Long>> e : companyByTable.entrySet()) {
            String table = e.getKey();
            List<Long> ids = new ArrayList<>(e.getValue().keySet());
            for (int i = 0; i < ids.size(); i += 500) {
                List<Long> chunk = ids.subList(i, Math.min(i + 500, ids.size()));
                String in = chunk.stream().map(x -> "?").collect(Collectors.joining(","));
                try {
                    jdbcTemplate.query("SELECT id, company_id FROM `" + table + "` WHERE id IN (" + in + ")",
                            rs -> {
                                long company = rs.getLong(2);
                                if (!rs.wasNull()) {
                                    e.getValue().put(rs.getLong(1), company);
                                }
                            },
                            chunk.toArray());
                } catch (Exception ex) {
                    log.warn("批量解析单据公司失败 table={}（该批走逐条兜底）: {}", table, ex.getMessage());
                }
            }
        }
        for (Map.Entry<String, String[]> e : billByProc.entrySet()) {
            Map<Long, Long> m = companyByTable.get(e.getValue()[0]);
            Long company = m == null ? null : m.get(Long.valueOf(e.getValue()[1]));
            if (company != null) {
                out.put(e.getKey(), company);
            }
        }
        return out;
    }

    private Map<String, String> roleScopes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        try {
            return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                            .in(SysRole::getRoleCode, codes)
                            .eq(SysRole::getStatus, "NORMAL")).stream()
                    .filter(r -> r.getRoleCode() != null)
                    .collect(Collectors.toMap(SysRole::getRoleCode,
                            r -> r.getOrgScope() == null ? "COMPANY" : r.getOrgScope(), (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** procInst 转 单据所属公司：bizType→表 白名单（表名硬编码，无注入面）；映射外/查不到返回 null（不过滤） */
    private Long resolveCompany(String procInstId) {
        WfInstance inst = findInstance(procInstId);
        if (inst == null) {
            return null;
        }
        return companyIdOfBill(inst.getBizType(), inst.getBizId());
    }

    /** 单据所属公司（提单态无实例也能解析）：bizType 不在白名单内或查不到返回 null */
    public Long companyIdOfBill(String bizType, Long bizId) {
        if (bizType == null || bizType.isBlank() || bizId == null) {
            return null;
        }
        String table = companyTable(bizType);
        if (table == null) {
            return null;
        }
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT company_id FROM `" + table + "` WHERE id = ?", Long.class, bizId);
            if (!ids.isEmpty() && ids.get(0) != null) {
                return ids.get(0);
            }
        } catch (Exception e) {
            log.debug("按 company_id 解析单据公司失败 bizType={} bizId={} table={}：{}",
                    bizType, bizId, table, e.getMessage());
        }
        return creatorCompany(bizType, table, bizId);
    }

    /**
     * 建单人所属公司兜底：`asset_depr_batch` / `crm_customer` / `md_supplier` /
     * `gl_inter_offset` / `gl_consolidate` / `gl_shared_master` 这批表<b>根本没有 company_id 列</b>，
     * 单据本身不区分公司，只有建单人有；解析成 null 会让 COMPANY 范围角色取人为空、
     * 且 {@link #canOperate} 在"单据公司为 null"时按放行处理（候选面被放宽到全租户）。
     * 改从 create_by 反查建单人公司，让"公司维度"在这些域真实存在。
     * 仍解析不到（表连 create_by 都没有，如 bank_reconcile / archive_access_log /
     * archive_destroy_approval）时返回 null 并留 warn——这类域<b>不得</b>使用 COMPANY 范围角色。
     * tr_bill_settle / tr_credit_bill 两表既有 company_id 也有 create_by，走上面的主路径即可解析；
     * TR_BILL 的 companyTable 登记见 {@link BillRouteRegistry}。
     */
    private Long creatorCompany(String bizType, String table, Long bizId) {
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT u.company_id FROM `" + table + "` t JOIN sys_user u ON u.id = t.create_by WHERE t.id = ?",
                    Long.class, bizId);
            Long company = ids.isEmpty() ? null : ids.get(0);
            if (company != null) {
                log.info("单据公司按建单人兜底解析 bizType={} bizId={} table={} company={}",
                        bizType, bizId, table, company);
                return company;
            }
            log.warn("单据公司解析失败（{} 表既无 company_id 也取不到 create_by 的公司）bizType={} bizId={}："
                    + "该域 COMPANY 范围角色将取人为空、候选面按放行处理", table, bizType, bizId);
            return null;
        } catch (Exception e) {
            log.warn("单据公司按建单人兜底失败 bizType={} bizId={} table={}: {}",
                    bizType, bizId, table, e.getMessage());
            return null;
        }
    }

    /**
     * bizType → 单据表（有 company_id 列，或可由 create_by 反查建单人公司）；不在表白名单内返回 null。
     *
     * <p>这张表只能有一份：本方法、{@code WfEngineFacade.BIZ_NO_TABLES}（起流单号）与
     * {@code WfTaskController.fillBillInfoBatch}（待办回填）各写一遍，漂移的结果就是同一个
     * bizType 在三处指向不同的表。故统一到 {@link BillRouteRegistry}，改一个 bizType 只改一行。
     *
     * <p>两件事不要顺手做：①<b>"返回 null 的 bizType" 不要顺手补值</b>——null 时 {@code canOperate()}
     * 对 null 公司放宽放行（候选面＝全租户），填上表即收窄候选面，属授权口径待定（注册表里逐行注明了现状）；
     * ②档案四链（FM_BORROW/FM_DESTROY/FM_DOC_FLOW）与无 company_id 的几张 {@code gl_*} 表靠 create_by 兜底，
     * 结构上取不到公司的表不得挂 COMPANY 范围角色。
     */
    private static String companyTable(String bt) {
        return BillRouteRegistry.companyOf(bt);
    }

    private WfInstance findInstance(String procInstId) {
        return wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                .eq(WfInstance::getProcInstId, procInstId).last("limit 1"));
    }
}
