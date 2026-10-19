// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfTaskMapper;
import com.fssc.workflow.service.BillRouteRegistry;
import com.fssc.workflow.service.WfEngineFacade;
import com.fssc.workflow.service.WfNodeConfigResolver;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.HashMap;

/** 流程任务（/api/tenant/wf/**）：我的待办 + 审批（经 WfEngineFacade，业务不直调 Flowable） */
@Slf4j
@RestController
@RequestMapping("/api/tenant/wf")
@RequiredArgsConstructor
@Validated
public class WfTaskController {

    private final WfEngineFacade wfEngineFacade;
    private final ExpBillMapper expBillMapper;
    private final JdbcTemplate jdbcTemplate;
    private final WfTaskMapper wfTaskMapper;
    private final WfNodeConfigResolver wfNodeConfigResolver;
    /** 催办的发信/限频/留痕通道（超时作业与发起人催办共用同一实现，见 E-10 / F1-5） */
    private final com.fssc.workflow.job.WfTimeoutJob wfTimeoutJob;
    /** 待办代办标识（F4-1）：委托关系读取走取人解析器的同一份口径 */
    private final com.fssc.workflow.service.NodeOrgResolver nodeOrgResolver;
    /** D-DEC-229：只读预览端点的归属闸（换 ID 即读他人单据全链，写路径的 canOperate 管不到读路径） */
    private final com.fssc.workflow.service.WfReadGuard wfReadGuard;
    /** D-DEC-065：待办「加签」标识的读侧数据源（wf_add_sign 只读，不参与任何指派/回跳判定） */
    private final com.fssc.workflow.service.WfAddSignService wfAddSignService;

    /**
     * 我的待办（兼容版，返回数组）。工作台徽标 / 个人中心 / AI 助手等取前 N 条或仅计数的场景使用。
     *
     * <p>性能约定（P0 优化）：
     * <ol>
     *  <li>单据补齐走【批量查询】（按 bizType 分组 IN 查询），杜绝逐条 selectById 的 N+1；</li>
     *  <li>限制返回条数上限，避免待办堆积时把全量数据拉回前端。</li>
     * </ol>
     */
    @GetMapping("/todo")
    public Result<List<Map<String, Object>>> todo(@RequestParam(defaultValue = "200") int limit) {
        if (limit < 1) limit = 200;
        if (limit > MAX_PAGE_SIZE) limit = MAX_PAGE_SIZE;
        List<Task> tasks = wfEngineFacade.todo(UserContext.getRequired().getUserId());
        List<Task> slice = tasks.size() > limit ? tasks.subList(0, limit) : tasks;
        return Result.success(buildTodoItems(slice, false));
    }

    /**
     * 我的待办（服务端分页，审批工作台使用）。
     *
     * <p>先取 Flowable 任务，再按【当前页】切片，只对本页任务补齐业务单据信息；
     * 补齐走批量查询，因此数据量增长后单页成本恒定，不会随待办总数线性劣化。
     */
    @GetMapping("/todo/page")
    public Result<PageResult<Map<String, Object>>> todoPage(@RequestParam(defaultValue = "1") int pageNum,
                                                            @RequestParam(defaultValue = "20") int pageSize) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > MAX_PAGE_SIZE) pageSize = MAX_PAGE_SIZE;

        List<Task> tasks = wfEngineFacade.todo(UserContext.getRequired().getUserId());
        long total = tasks.size();
        int from = (int) Math.min((long) (pageNum - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        List<Task> pageTasks = (from >= to) ? List.of() : tasks.subList(from, to);

        return Result.success(PageResult.of(buildTodoItems(pageTasks, true), total, pageNum, pageSize));
    }

    /** 待办每页条数硬上限，防止超大页把整表拉回 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 构建待办行：先铺基础字段，再【批量】回填单据信息；withCandidates=true 时额外解析节点候选办理人 */
    private List<Map<String, Object>> buildTodoItems(List<Task> tasks, boolean withCandidates) {
        List<Map<String, Object>> list = new ArrayList<>(tasks.size());
        // bizType -> (bizId -> 行)，用于分组批量查询
        Map<String, Map<Long, Map<String, Object>>> needFill = new HashMap<>();
        // procInstId -> 行（旧数据无 bizId，按流程实例回退）
        Map<String, Map<String, Object>> needFallback = new HashMap<>();
        // 运行态变量缺失时的历史变量兜底（业务行被清理 / 变量被删的场景）
        Map<String, Map<String, Object>> hisVars = loadHistoricalVars(tasks);
        // POOL 软锁标注：一次判定（一次节点配置查询），不在按行循环里反复查库
        Map<String, Long> locks;
        try {
            locks = wfEngineFacade.poolLockedByOthers(tasks, UserContext.getRequired().getUserId());
        } catch (Exception ex) {
            log.warn("待办列表 POOL 软锁标注失败（仅影响灰显，服务端仍会拦）: {}", ex.getMessage());
            locks = Map.of();
        }

        for (Task t : tasks) {
            Map<String, Object> vars = t.getProcessVariables();
            Map<String, Object> hv = hisVars.getOrDefault(t.getProcessInstanceId(), Map.of());
            Object title = firstNonNull(vars == null ? null : vars.get("title"), hv.get("title"));
            Object bizType = firstNonNull(vars == null ? null : vars.get("bizType"), hv.get("bizType"));
            Object bizId = firstNonNull(vars == null ? null : vars.get("bizId"), hv.get("bizId"));
            Map<String, Object> m = new HashMap<>();
            m.put("taskId", t.getId());
            m.put("processInstanceId", t.getProcessInstanceId());
            m.put("taskName", t.getName());
            m.put("nodeKey", t.getTaskDefinitionKey());
            // D-DEC-160②：模型键随行带出（Flowable 定义 ID 形如 modelKey:version:deployId，
            // 与 NodeOrgResolver.isPoolTask 的取法逐字同源）。零次查库——待办每页最多 200 行，
            // 若这里再走 loadModelKeys 的 wf_instance IN 查询，徽标轮询（/todo）就要多打一次库。
            m.put("modelKey", t.getProcessDefinitionId() == null
                    ? null : t.getProcessDefinitionId().split(":")[0]);
            m.put("createTime", String.valueOf(t.getCreateTime()));
            m.put("title", title == null ? "" : title);
            m.put("billType", bizType);
            m.put("billId", bizId);
            m.put("billNo", "");
            m.put("totalAmount", null);
            // 当前真实办理人（Flowable assignee）。为空表示该任务只挂了候选组/未被指派。
            m.put("assigneeId", t.getAssignee());
            // 作业节点已被同池他人领取 → 该行仍在列表内（可见），但「去处理」必须禁用，
            m.put("lockedByOther", locks.containsKey(t.getId()));
            // 业务行是否解析成功：由各批量回填方法置 true；为 false 时前端禁用「去处理」并提示
            m.put("bizResolved", false);
            list.add(m);

            if (bizId != null && bizType != null) {
                needFill.computeIfAbsent(String.valueOf(bizType), k -> new HashMap<>())
                        .put(((Number) bizId).longValue(), m);
            } else if (t.getProcessInstanceId() != null) {
                needFallback.put(t.getProcessInstanceId(), m);
            }
        }
        fillBillInfoBatch(needFill, needFallback);
        enrichHandlers(list, withCandidates);
        annotateAddSignGate(list);
        annotateAddSignTodo(list);
        annotateDelegation(list);
        // 收口：仍无法识别单据的行给出可读标题

        for (Map<String, Object> m : list) {
            if (isBlank(m.get("title"))) {
                m.put("title", "未关联业务单据（" + m.get("taskName") + "）");
            }
        }
        return list;
    }

    /**
     * 补齐"人"的信息：① 当前在办人姓名（Flowable 真实 assignee）；② 该节点按 {@code wf_node_config}
     * 本可以从哪些人里挑（候选人名单）。
     *
     * <p>动机：节点取人为 ROLE 时，运行态是"取角色首个持有人"直接指派，审批人在列表里只看得到节点名、
     * 看不到也猜不到单子落在谁手上。这里只读地把人暴露出来，<b>不改变任何指派行为</b>。
     *
     * <p>成本控制：model_key 与姓名各一次 IN 批量查询；候选人按「模型+节点+流程实例」缓存复用，
     * 且仅在审批工作台分页场景解析，行数超过 {@link #MAX_CANDIDATE_ROWS} 时整体跳过
     * （返回体里 {@code candidateNames} 为 null，前端按"未提供"显示而不是显示空名单）。
     */
    private void enrichHandlers(List<Map<String, Object>> rows, boolean withCandidates) {
        Map<Long, String> names = new HashMap<>();
        Map<String, List<Long>> byTask = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        for (Map<String, Object> m : rows) {
            Long a = toUserId(m.get("assigneeId"));
            if (a != null) ids.add(a);
        }
        if (withCandidates && rows.size() <= MAX_CANDIDATE_ROWS) {
            Map<String, String> modelKeys = loadModelKeys(rows);
            Map<String, List<Long>> cache = new HashMap<>();
            for (Map<String, Object> m : rows) {
                String proc = str(m.get("processInstanceId"));
                String nodeKey = str(m.get("nodeKey"));
                String modelKey = modelKeys.get(proc);
                if (proc == null || nodeKey == null || modelKey == null) continue;
                String key = modelKey + "|" + nodeKey + "|" + proc;
                List<Long> cands = cache.computeIfAbsent(key,
                        k -> wfEngineFacade.listNodeCandidates(modelKey, nodeKey, proc));
                byTask.put(str(m.get("taskId")), cands);
                ids.addAll(cands);
            }
        }
        // D-DEC-160②：节点取人方式（handling_mode）一次查完，供「释放」按钮按行标注。
        // 判据与 NodeOrgResolver.isPoolTask 逐字一致（poolNodeKeys() 就是该判据的批量形态，
        // 同一份 handling_mode=POOL 口径；见 WfNodeConfigResolver.poolNodeKeys 注释「待办列表批量判定」）。
        // 失败退空集 = 全部按"非池"标注，按钮少给不误给，服务端 unclaim 仍会硬拦。
        Set<String> poolKeys;
        try {
            poolKeys = wfNodeConfigResolver.poolNodeKeys();
        } catch (Exception ex) {
            log.warn("待办节点取人方式读取失败（仅影响「释放」按钮显隐，服务端仍会拦）: {}", ex.getMessage());
            poolKeys = Set.of();
        }
        names.putAll(loadUserNames(ids));
        Long me = com.fssc.framework.security.UserContext.get() != null
                ? com.fssc.framework.security.UserContext.get().getUserId() : null;
        for (Map<String, Object> m : rows) {
            Long a = toUserId(m.get("assigneeId"));
            m.put("assigneeName", a == null ? "" : names.getOrDefault(a, UNKNOWN_USER));
            // 任务池按钮：本列表已按「办理人/候选组 × 公司范围 × 委托」过滤过（todo() 的口径），
            // 因此出现在列表里就代表"我有资格办"；无人认领的行即待领取，服务端 claim 再校验一次。
            boolean pool = isPoolNode(poolKeys, str(m.get("modelKey")), str(m.get("nodeKey")));
            m.put("poolTask", pool);
            m.put("canClaim", a == null);
            // D-DEC-160（②按钮腿，与门面 unclaim 同判据）：原先只看 Flowable assignee 是否等于我，
            // 于是 AUTO/PICK 节点「按配置直接指派到我」也回 canUnclaim=true——这类节点本没有任务池，
            // 点「释放」只会把已指派到人的单子打回无人可办（①领取后徽标不刷新属另一腿，见回报）。
            m.put("canUnclaim", a != null && a.equals(me) && pool);
            // 被他人锁定的作业任务：把领取人姓名给前端做灰显提示（批次 1 ①）
            if (Boolean.TRUE.equals(m.get("lockedByOther"))) {
                m.put("lockOwnerName", names.getOrDefault(a, UNKNOWN_USER));
            }
            List<Long> cands = byTask.get(str(m.get("taskId")));
            if (cands == null) {
                // 未解析（徽标计数等待办场景 / 行数超上限）：给 null，前端不显示"0 人"这种误导值
                m.put("candidateNames", null);
                m.put("candidateCount", null);
            } else {
                List<String> ns = new ArrayList<>(cands.size());
                for (Long id : cands) ns.add(names.getOrDefault(id, UNKNOWN_USER));
                m.put("candidateNames", ns);
                m.put("candidateCount", ns.size());
            }
        }
    }

    /**
     * 本行节点是否任务池（{@code handling_mode=POOL}）。
     *
     * <p>D-DEC-160②：键形态 {@code modelKey:nodeKey} 与 {@link com.fssc.workflow.service.WfNodeConfigResolver#poolNodeKeys()}、
     * {@code NodeOrgResolver.isPoolTask(Task)} 完全同源（同一配置列、同一 trim + equalsIgnoreCase 口径），
     * 只是列表侧手上没有 Flowable {@code Task}、只有行字段，故走批量集合而不是逐行 resolve。
     *
     * <p><b>刻意不含 {@code assign_type=SHARE_POOL}</b>：那是 {@code WfTimeoutJob.isWorkNode} 的「作业档」双条件，
     * 与本处的「有没有任务池」不是同一件事；若把它并进 {@code isPoolTask} 的判据，会连带改动
     * claim() 的 {@code deny_self} 自审闸语义（POOL 不走 deny_self 是 09-26 裁决，不得回退）。
     * 现网 {@code wf_node_config} 亦无 SHARE_POOL 行（AUTO/POOL/PICK × ROLE/USER/INITIATOR）。
     */
    private static boolean isPoolNode(Set<String> poolKeys, String modelKey, String nodeKey) {
        return modelKey != null && nodeKey != null
                && poolKeys != null && poolKeys.contains(modelKey + ":" + nodeKey);
    }

    /** 候选人解析的行数上限：再往上单页就要按行查角色成员，宁可不给名单也不能拖慢待办 */
    private static final int MAX_CANDIDATE_ROWS = 60;
    private static final String UNKNOWN_USER = "（账号已停用或注销）";

    /**
     * 「加签闸口」标注（F3-3 / UR-12）：总闸 {@code approve.addsign_gate.enabled} 打开后，
     * 灰度模型内 {@code allow_addsign=0} 的待办行下发 {@code addSignAllowed=false}，前端据此隐藏「加签」按钮。
     *
     * <p>缺省（总闸关）时<b>一个字段都不下发、一次库都不查</b>——前端拿不到该字段即按"可加签"渲染，
     * 与改造前逐字一致。服务端 {@code addSign()} 里的拒绝判定读的是同一份配置，不会出现"看得见却办不动"。
     */
    private void annotateAddSignGate(List<Map<String, Object>> rows) {
        if (rows.isEmpty() || !wfEngineFacade.isAddSignGateEnabled()) {
            return;
        }
        try {
            java.util.Set<String> grey = wfEngineFacade.addSignGateModels();
            if (grey.isEmpty()) {
                return;
            }
            java.util.Set<String> disabled = wfNodeConfigResolver.addsignDisabledNodeKeys();
            Map<String, String> modelKeys = loadModelKeys(rows);
            for (Map<String, Object> m : rows) {
                String modelKey = modelKeys.get(String.valueOf(m.get("processInstanceId")));
                if (modelKey == null || !grey.contains(modelKey)) {
                    continue;
                }
                m.put("addSignAllowed", !disabled.contains(modelKey + ":" + m.get("nodeKey")));
            }
        } catch (Exception ex) {
            log.warn("待办加签闸口标注失败（仅影响按钮显隐，服务端仍会拦）: {}", ex.getMessage());
        }
    }

    /**
     * 待办「加签」标识（D-DEC-065 症状②）：这一行是别人临时插的一道审——前加签被改派到我名下、
     * 或后加签在推进后落在我名下。改造前它与普通环节<b>视觉完全同一</b>，于是加签人不知道自己
     * 这一签只代表"加签这道审完了"（签完回跳原办理人、不推进流程），按普通环节的心态去点通过，
     * 事后既说不清自己签过什么，也说不清这单为什么还在原来那个人手上。
     *
     * <p><b>真值源</b>＝{@code wf_add_sign} 上绑定本任务且 {@code status=ACTIVE} 的记录，与
     * {@code complete} 里的加签回跳钩子（{@code WfAddSignService.activeByTaskAndUser}）同一张表、
     * 同一状态口径，不另立第二套判据。审结/取消后记录转 DONE/CANCELLED ⇒ 角标随之消失，
     * 原办理人的待办回到"普通环节"呈现。
     *
     * <p><b>缺省零变化</b>：本页没有任何进行中的加签时，一个字段都不下发，返回体与改造前逐字一致。
     * 成本控制：一次 {@code task_id IN (本页)} 批量查询（与 POOL 软锁标注同型），
     * 失败退化为"不标注"——只影响角标，指派/回跳/服务端校验一律不受影响。
     */
    private void annotateAddSignTodo(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        try {
            Set<String> taskIds = new HashSet<>();
            for (Map<String, Object> m : rows) {
                String tid = str(m.get("taskId"));
                if (tid != null && !tid.isBlank()) {
                    taskIds.add(tid);
                }
            }
            if (taskIds.isEmpty()) {
                return;
            }
            List<com.fssc.workflow.entity.WfAddSign> recs = wfAddSignService.activeByTaskIds(taskIds);
            if (recs == null || recs.isEmpty()) {
                return;
            }
            Map<String, com.fssc.workflow.entity.WfAddSign> byTask = new HashMap<>();
            Set<Long> initiatorIds = new HashSet<>();
            for (com.fssc.workflow.entity.WfAddSign r : recs) {
                // activeByTaskIds 已按 id 倒序 ⇒ 同任务多条（连环加签）时取最近一条
                if (r.getTaskId() != null) {
                    byTask.putIfAbsent(r.getTaskId(), r);
                }
                if (r.getCreateUserId() != null) {
                    initiatorIds.add(r.getCreateUserId());
                }
            }
            Map<Long, String> names = loadUserNames(initiatorIds);
            for (Map<String, Object> m : rows) {
                com.fssc.workflow.entity.WfAddSign r = byTask.get(str(m.get("taskId")));
                if (r == null) {
                    continue;
                }
                m.put("addSignTodo", true);
                m.put("addSignPosition", r.getPosition());
                m.put("addSignFromUserId", r.getCreateUserId());
                m.put("addSignFromName", r.getCreateUserId() == null
                        ? "" : names.getOrDefault(r.getCreateUserId(), UNKNOWN_USER));
                m.put("addSignReason", r.getReason());
            }
        } catch (Exception ex) {
            log.warn("待办加签标识标注失败（仅影响角标显示，加签回跳钩子不受影响）: {}", ex.getMessage());
        }
    }

    /**
     * 「代办标识」标注（F4-1，治 D-DEC-120 归因失真）：委托期内待办归属对两边都说清楚——
     * <ul>
     *   <li>受托人侧：本行的真实办理人是我的某位<b>委托人</b> ⇒ 下发 {@code delegatedFrom / delegatedFromName}
     *       （前端渲染「代 张三 办理」）。借资格档下这行同时挂在委托人名下，不标注就会出现
     *       "两边都以为是对方在办"的空洞；</li>
     *   <li>委托人侧：本行办理人是我、而我此刻有生效委托 ⇒ 下发 {@code delegatedToNames}
     *       （「已委托给 李四 代管」），改派档下另有 {@code delegatedTo} 单个受托人 ID。</li>
     * </ul>
     *
     * <p><b>缺省零变化</b>：本人名下没有任何生效委托时，两份映射都为空 ⇒ <b>一个字段都不下发</b>，
     * 返回体与改造前逐字一致。标注的是"谁委托给谁"这一既有事实，不改变任何可办性判定——
     * 能不能办仍由 {@code NodeOrgResolver.canOperate} 与服务端守卫说了算。
     *
     * <p>刻意<b>不</b>按 {@code wf.delegate.mode} 收窄：借资格档（现网默认）才是归属最容易混的档位，
     * 标注在两种模式下语义相同。取数走 {@code wf_delegate} 的按人索引查询，与待办过滤同一份口径。
     */
    private void annotateDelegation(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        try {
            Long me = UserContext.getRequired().getUserId();
            Map<Long, String> principals = nodeOrgResolver.delegatePrincipals(me);
            Map<Long, String> agents = nodeOrgResolver.delegateAgents(me);
            if (principals.isEmpty() && agents.isEmpty()) {
                return;
            }
            for (Map<String, Object> m : rows) {
                Long assignee = toUserId(m.get("assigneeId"));
                if (assignee == null) {
                    continue;
                }
                if (assignee.equals(me)) {
                    if (!agents.isEmpty()) {
                        m.put("delegatedToNames", new ArrayList<>(agents.values()));
                    }
                } else if (principals.containsKey(assignee)) {
                    m.put("delegatedFrom", String.valueOf(assignee));
                    m.put("delegatedFromName", principals.get(assignee));
                }
            }
        } catch (Exception ex) {
            log.warn("待办代办标识标注失败（仅影响标签显示，不影响可办性）: {}", ex.getMessage());
        }
    }

    /** 一次性取流程实例对应的模型 key（候选人解析要按 model_key + node_key 读节点配置） */
    private Map<String, String> loadModelKeys(List<Map<String, Object>> rows) {
        List<Object> procs = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            Object p = m.get("processInstanceId");
            if (p != null && !procs.contains(p)) procs.add(String.valueOf(p));
        }
        if (procs.isEmpty()) return Map.of();
        String ph = procs.stream().map(x -> "?").collect(Collectors.joining(","));
        Map<String, String> out = new HashMap<>();
        jdbcTemplate.query("SELECT proc_inst_id, model_key FROM wf_instance WHERE proc_inst_id IN (" + ph + ")",
                rs -> { out.put(rs.getString(1), rs.getString(2)); }, procs.toArray());
        return out;
    }

    /** 一次性取用户显示名：姓名（工号），工号为空时只给姓名 */
    private Map<Long, String> loadUserNames(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<Object> args = new ArrayList<>(ids);
        String ph = args.stream().map(x -> "?").collect(Collectors.joining(","));
        Map<Long, String> out = new HashMap<>();
        jdbcTemplate.query("SELECT id, IFNULL(nick_name, user_name) AS nm, IFNULL(emp_no, '') AS no"
                        + " FROM sys_user WHERE del_flag = 0 AND id IN (" + ph + ")",
                rs -> {
                    String nm = rs.getString("nm");
                    String no = rs.getString("no");
                    out.put(rs.getLong("id"), no.isBlank() ? nm : nm + "（" + no + "）");
                }, args.toArray());
        return out;
    }

    private static Long toUserId(Object v) {
        if (v == null) return null;
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 历史变量兜底：运行态变量（ACT_RU_VARIABLE）缺失时，回读 ACT_HI_VARINST 里的
     * bizType / bizId / title。
     *
     * <p>典型场景：流程实例仍在跑，但业务行被清理（测试数据删除 / 归档搬运），
     * 运行态变量随之被清，历史变量仍在。此时待办行会三空，审批人无法识别单据。
     * 只在确实缺失时才发这 1 次查询，正常待办零额外开销。
     */
    private Map<String, Map<String, Object>> loadHistoricalVars(List<Task> tasks) {
        List<String> procIds = new ArrayList<>();
        for (Task t : tasks) {
            Map<String, Object> vars = t.getProcessVariables();
            boolean missing = vars == null
                    || vars.get("bizType") == null || vars.get("bizId") == null || isBlank(vars.get("title"));
            if (missing && t.getProcessInstanceId() != null) procIds.add(t.getProcessInstanceId());
        }
        if (procIds.isEmpty()) return Map.of();
        Map<String, Map<String, Object>> result = new HashMap<>();
        try {
            String placeholders = procIds.stream().map(x -> "?").collect(Collectors.joining(","));
            String sql = "SELECT PROC_INST_ID_, NAME_, TEXT_, LONG_ FROM ACT_HI_VARINST "
                    + "WHERE NAME_ IN ('bizType','bizId','title') AND PROC_INST_ID_ IN (" + placeholders + ")";
            for (Map<String, Object> row : jdbcTemplate.queryForList(sql, procIds.toArray())) {
                Object pid = row.get("PROC_INST_ID_");
                Object name = row.get("NAME_");
                if (pid == null || name == null) continue;
                Object val = row.get("TEXT_") != null ? row.get("TEXT_") : row.get("LONG_");
                if (val == null) continue;
                result.computeIfAbsent(String.valueOf(pid), k -> new HashMap<>()).put(String.valueOf(name), val);
            }
        } catch (Exception ex) {
            log.warn("历史变量兜底查询失败 procInstCount={}", procIds.size(), ex);
        }
        return result;
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    /** 批量补齐单据信息：每类业务表最多 1 次查询（原来是每条待办 1 次） */
    private void fillBillInfoBatch(Map<String, Map<Long, Map<String, Object>>> needFill,
                                   Map<String, Map<String, Object>> needFallback) {
        // 1) 费用类统一走 exp_bill：一次 selectBatchIds
        // 注意：流程变量 bizType 带 EXP_ 前缀（EXP_CLAIM/EXP_LOAN/...），白名单为不带前缀的旧值，
        // 需成对收集；回填时按库中 bill_type 双向匹配，避免待办池单据号/标题为空。
        List<Long> expIds = new ArrayList<>();
        for (String type : BillRouteRegistry.EXP_BILL_FILLED) {
            Map<Long, Map<String, Object>> g = needFill.get(type);
            if (g != null) expIds.addAll(g.keySet());
            String expType = "EXP_" + type;
            g = needFill.get(expType);
            if (g != null) expIds.addAll(g.keySet());
        }
        if (!expIds.isEmpty()) {
            try {
                for (ExpBill bill : expBillMapper.selectBatchIds(expIds)) {
                    if (bill == null || bill.getBillType() == null) continue;
                    String bt = bill.getBillType();
                    Map<Long, Map<String, Object>> g = needFill.get(bt);
                    if (g == null) g = needFill.get("EXP_" + bt);
                    if (g == null) continue;
                    Map<String, Object> m = g.get(bill.getId());
                    if (m != null) putBill(m, bill.getId(), bill.getBillType(), bill.getBillNo(), bill.getTotalAmount(), bill.getTitle());
                }
            } catch (Exception ex) {
                log.warn("批量补齐费用单据失败 ids={}", expIds.size(), ex);
            }
        }
        // 2) 其它业务类型：每个 bizType 各一次 IN 查询。
        // 批次3 R3-B（D-B3-02 收口）：这里原先是 50 多行手写的 fillBySql，与 {@code NodeOrgResolver.companyTable()}
        // 的 117 行 switch、{@code WfEngineFacade.BIZ_NO_TABLES} 三处各写一遍"bizType→表/列"，
        // round 1 实测的三处漂移（RECEIVABLE_COLLECT/FA_CER/FA_DEPRECIATION 取人侧与取数侧不一致）就是这么来的。
        // 现在唯一真值在 {@link BillRouteRegistry}，本方法只负责遍历；表/列名与改前一一对应（差异见注册表逐行注释）。
        // 跳过两类：fillTable=null（改前不回填，或由 fillBadDebt 这类特例承担）、费用族（上面 mapper 成对匹配已覆盖）。
        // 遗留死映射 TREASURY→treasury_transfer、ASSET→asset_card 目标表在库里不存在（D-B3-05，全库无发起点），
        // 原样保留、不猜表；改后由 fillBySql 的"表不存在"分支一次 warn 后跳过，不再抛 Unknown table。
        for (Map.Entry<String, BillRouteRegistry.Route> e : BillRouteRegistry.exactRoutes().entrySet()) {
            String bt = e.getKey();
            BillRouteRegistry.Route r = e.getValue();
            if (r.fillTable() == null) continue;
            if (BillRouteRegistry.EXP_BILL_FILLED.contains(bt) || bt.startsWith("EXP_")) continue;
            fillBySql(needFill, bt, r.fillTable(), r.noCol(), r.amtCol(), r.titleCol());
        }
        // 3) BAD_DEBT 需要 JOIN rc_receivable 获取单据号
        fillBadDebt(needFill);
        // 4) 前缀匹配批量回填：只服务未显式登记的族（存货 INV_*、资产 FA_*）。
        // TR 系列【禁止】按前缀统一映射：各类型目标表不同、id 空间互不相干（旧写法 fillByPrefixSql("TR",
        // "tr_transfer") 会让 TR_PAY/TR_RECEIPT 等按 id 借用调拨单号）⇒ 全部走上面的显式行。
        // 批次3 R3-B 刻意更正②：前缀查询现在给显式行让路（{@code hasExact}）——改前 "INV" 前缀会把
        // INVOICE_ISSUE/INVOICE_MATCH_MANUAL 已按 inv_invoice/exp_bill 精确回填好的三项，
        // 用 md_inventory 里<b>同 id 的另一张存货</b>覆盖掉（发票 id 撞进存货 id 空间时；D-FAM4-01 同一类，
        // 错值比空值危险）。FA_CER/FA_DEPRECIATION 同理改走各自本表。
        fillByPrefixSql(needFill, "FA", "md_asset", "asset_no", "original_amount", "asset_name");
        fillByPrefixSql(needFill, "INV", "md_inventory", "item_code", "out_amount", "item_name");

        // 5) 回退：按 procInstId 一次 IN 查询 exp_bill
        if (!needFallback.isEmpty()) {
            try {
                List<ExpBill> bills = expBillMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExpBill>()
                                .in(ExpBill::getProcInstId, needFallback.keySet()));
                for (ExpBill bill : bills) {
                    if (bill == null || bill.getProcInstId() == null) continue;
                    Map<String, Object> m = needFallback.get(bill.getProcInstId());
                    if (m != null) putBill(m, bill.getId(), bill.getBillType(), bill.getBillNo(), bill.getTotalAmount(), bill.getTitle());
                }
            } catch (Exception ex) {
                log.warn("按流程实例回退补齐失败", ex);
            }
        }
    }

    /**
     * 通用批量回填：单次 IN 查询补齐 billNo/totalAmount/title。
     * 表名与列名均为本类硬编码常量（非用户输入），不存在 SQL 注入面。
     *
     * 批次3 R3-A（D-B3-01 根治）：单号/金额/标题三项原先共用一条 SQL，任一列不存在即整条抛
     * {@code Unknown column}，被下面的 catch 吞成一行不带列名的 warn ⇒ 该域<b>三项全丢</b>
     * （"合同待办没有单号"这类分别上报的症状共享同一个根因）。现按 information_schema
     * 逐列校验，缺列只把<b>该项</b>置为 NULL 常量，并把表名/列名打进 warn（同列只报一次）。
     */
    private void fillBySql(Map<String, Map<Long, Map<String, Object>>> needFill, String bizType,
                           String table, String noCol, String amtCol, String titleCol) {
        Map<Long, Map<String, Object>> g = needFill.get(bizType);
        if (g == null || g.isEmpty()) return;
        if (columnsOf(table) != null && columnsOf(table).isEmpty()) {
            warnOnce("T" + table, "待办回填跳过：表不存在 {}（bizType={}）", table, bizType);
            return;
        }
        try {
            String placeholders = g.keySet().stream().map(x -> "?").collect(Collectors.joining(","));
            String sql = "SELECT id, " + columnExpr(table, noCol, "billNo", bizType)
                    + ", " + columnExpr(table, amtCol, "totalAmount", bizType)
                    + ", " + columnExpr(table, titleCol, "title", bizType)
                    + " FROM " + table + " WHERE id IN (" + placeholders + ")";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, g.keySet().toArray());
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                if (id == null) continue;
                Map<String, Object> m = g.get(((Number) id).longValue());
                if (m == null) continue;
                m.put("billNo", row.get("billNo"));
                m.put("totalAmount", row.get("totalAmount"));
                m.put("bizResolved", true);
                if (isBlank(m.get("title"))) m.put("title", row.get("title"));
            }
        } catch (Exception ex) {
            log.warn("批量补齐失败 bizType={} 表={}", bizType, table, ex);
        }
    }

    /** 表列元数据缓存（每表一次 information_schema 查询）；null＝元数据查不到，按旧行为不过滤 */
    private final Map<String, Set<String>> tableColumns = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> warnedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 表在当前库中的列名集合（小写）；表不存在返回空集；元数据查询失败返回 null（不过滤、保持旧行为） */
    private Set<String> columnsOf(String table) {
        if (table == null || table.isBlank()) return null;
        Set<String> cached = tableColumns.get(table);
        if (cached != null) return cached;
        Set<String> cols;
        try {
            cols = new java.util.HashSet<>();
            for (String c : jdbcTemplate.queryForList(
                    "SELECT LOWER(column_name) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND LOWER(table_name) = LOWER(?)",
                    String.class, table)) {
                if (c != null) cols.add(c);
            }
        } catch (Exception ex) {
            log.warn("待办回填列元数据查询失败 表={}（本轮不校验列名）", table, ex);
            return null;
        }
        tableColumns.put(table, cols);
        return cols;
    }

    /** 列存在则原样取，缺列（或刻意传 null）则退化为 NULL 常量列——缺一项不再连累同一条 SQL 的其余两项 */
    private String columnExpr(String table, String col, String alias, String bizType) {
        if (col == null || col.isBlank()) return "NULL AS " + alias;
        Set<String> cols = columnsOf(table);
        if (cols == null) return col + " AS " + alias;
        if (cols.contains(col.toLowerCase())) return col + " AS " + alias;
        warnOnce(table + "." + col, "待办回填缺列：表 {} 无列 {}（bizType={}，{} 置空）",
                table, col, bizType, alias);
        return "NULL AS " + alias;
    }

    /** 同一个缺列只在首次出现时告警：待办列表每次翻页都会走这里，逐次打点会淹掉日志 */
    private void warnOnce(String key, String template, Object... args) {
        if (warnedKeys.add(key)) log.warn(template, args);
    }

    /**
     * 批量回填前缀匹配类型：所有 bizType 前缀匹配的都会走该表查询。
     * 例如 bt.startsWith("FA_") → 走 md_asset 表。
     *
     * <p>批次3 R3-B：已登记<b>显式行</b>的 bizType 一律让路（改前只有 FA_CER 一处手工 continue，
     * 于是 INVOICE_ISSUE/INVOICE_MATCH_MANUAL 被 "INV" 前缀二次覆盖成另一张存货的单号）。
     */
    private void fillByPrefixSql(Map<String, Map<Long, Map<String, Object>>> needFill,
                                String prefix, String table, String noCol, String amtCol, String titleCol) {
        List<Long> allIds = new ArrayList<>();
        for (String bt : needFill.keySet()) {
            if (bt.startsWith(prefix) && !BillRouteRegistry.hasExact(bt)) {
                Map<Long, Map<String, Object>> g = needFill.get(bt);
                if (g != null) allIds.addAll(g.keySet());
            }
        }
        if (allIds.isEmpty()) return;
        Set<String> tblCols = columnsOf(table);
        if (tblCols != null && tblCols.isEmpty()) {
            warnOnce("T" + table, "待办回填跳过：表不存在 {}（prefix={}）", table, prefix);
            return;
        }
        try {
            String placeholders = allIds.stream().map(x -> "?").collect(Collectors.joining(","));
            String sql = "SELECT id, " + columnExpr(table, noCol, "billNo", prefix + "*")
                    + ", " + columnExpr(table, amtCol, "totalAmount", prefix + "*")
                    + ", " + columnExpr(table, titleCol, "title", prefix + "*")
                    + " FROM " + table + " WHERE id IN (" + placeholders + ")";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, allIds.toArray());
            for (Map<String, Object> row : rows) {
                Object idObj = row.get("id");
                if (idObj == null) continue;
                Long id = ((Number) idObj).longValue();
                // 需要找到这个 id 属于哪个 bizType
                for (String bt : needFill.keySet()) {
                    if (bt.startsWith(prefix) && !BillRouteRegistry.hasExact(bt)) {
                        Map<Long, Map<String, Object>> g = needFill.get(bt);
                        if (g != null && g.containsKey(id)) {
                            Map<String, Object> m = g.get(id);
                            if (m != null) {
                                m.put("billNo", row.get("billNo"));
                                m.put("totalAmount", row.get("totalAmount"));
                                m.put("bizResolved", true);
                                if (isBlank(m.get("title"))) m.put("title", row.get("title"));
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("批量补齐失败 prefix={} 表={}", prefix, table, ex);
        }
    }

    /** BAD_DEBT 坏账计提：rc_bad_debt 无单据号，需 JOIN rc_receivable 获取 bill_no */
    private void fillBadDebt(Map<String, Map<Long, Map<String, Object>>> needFill) {
        Map<Long, Map<String, Object>> g = needFill.get("BAD_DEBT");
        if (g == null || g.isEmpty()) return;
        try {
            String placeholders = g.keySet().stream().map(x -> "?").collect(Collectors.joining(","));
            String sql = "SELECT rbd.id, rr.bill_no AS billNo, rbd.amount AS totalAmount, "
                    + "rr.bill_no AS title FROM rc_bad_debt rbd "
                    + "LEFT JOIN rc_receivable rr ON rbd.receivable_id = rr.id "
                    + "WHERE rbd.id IN (" + placeholders + ")";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, g.keySet().toArray());
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                if (id == null) continue;
                Map<String, Object> m = g.get(((Number) id).longValue());
                if (m == null) continue;
                m.put("billNo", row.get("billNo"));
                m.put("totalAmount", row.get("totalAmount"));
                m.put("bizResolved", true);
                if (isBlank(m.get("title"))) m.put("title", row.get("title"));
            }
        } catch (Exception ex) {
            log.warn("批量补齐失败 BAD_DEBT 表=rc_bad_debt JOIN rc_receivable", ex);
        }
    }

    private static void putBill(Map<String, Object> m, Long billId, String billType,
                                String billNo, Object amount, String title) {
        m.put("billId", billId);
        m.put("billType", billType);
        m.put("billNo", billNo);
        m.put("totalAmount", amount);
        m.put("bizResolved", true);
        if (isBlank(m.get("title"))) m.put("title", title);
    }

    private static boolean isBlank(Object v) {
        return v == null || v.toString().isBlank();
    }

    /** 审批：approved=true 通过 / false 驳回 */
    @OperLog(module = "WF_TASK", operType = "UPDATE", desc = "审批")
    @PostMapping("/approve")
    public Result<Void> approve(@Validated @RequestBody ApproveReq req) {
        // G2 传阅：ccUserIds 为审批人临时指定的抄送人（可空），与节点配置取并集后投递到 wf_cc
        // D-V3-148：数组/逗号串两形态在 req.ccUserIdsNormalized() 归一，门面仍收逗号分隔串
        // G4 自由驳回：rejectToNodeKey 为驳回目标节点（可空）；空 ⇒ 原「终止本流程」路径逐字不变
        // G2 ①层审批项：approvalItemCode 为节点上配置的自定义审批结果（可空）；空 ⇒ 逐字不变
        wfEngineFacade.complete(req.getTaskId(), req.isApproved(), req.getComment(),
                UserContext.getRequired().getUserId(), req.getNextAssigneeUserId(), req.ccUserIdsNormalized(),
                req.getRejectToNodeKey(), req.getApprovalItemCode());
        return Result.success();
    }

    /**
     *
     * <p>审批弹窗打开时调用；返回空数组即不渲染自定义按钮（与改造前完全一致）。
     * 与 {@code /approve} 的 {@code approvalItemCode} 同源同一解析器，不存在"看得见点不动"。
     */
    @GetMapping("/approval-items")
    public Result<java.util.List<com.fssc.workflow.entity.WfApprovalItem>> approvalItems(@RequestParam String taskId) {
        wfReadGuard.assertTaskReadable(taskId);
        return Result.success(wfEngineFacade.approvalItemsOf(taskId));
    }

    /**
     * 会签/票签节点进度（只读）：审批页与流程痕迹据此显示「已投 x/n · 赞成 a 反对 r」，
     * 票签额外给出阈值与"还需几票通过"。非多实例节点返回 {@code multi=false}，前端不渲染进度。
     *
     * <p>逐票明细取自 {@code wf_task_history}（每次办理已写 APPROVE/REJECT + 操作人 + 意见），
     * 不另建计票表——同一事实只留一处痕，避免日后漂移。
     */
    @GetMapping("/mi-progress")
    public Result<Map<String, Object>> miProgress(@RequestParam String taskId) {
        wfReadGuard.assertTaskReadable(taskId);
        return Result.success(wfEngineFacade.miProgress(taskId));
    }

    /**
     * 「自由驳回」只读预览（P4 G4）：给定当前待办任务，列出可退回的上游节点及各自影响面
     * （退回到它将作废哪些后续节点），<b>不修改任何流程数据</b>。
     *
     * <p>与 {@code /withdraw-preview} 的区别：撤销是发起人视角（退回后自己重办），
     * 本接口是审批人视角（驳回时选级次）。候选集两条口径同源（{@code wfQueryService.chain}），
     * 不允许分叉。前端仅在 {@code enabled=true} 时渲染级次选择器（开关未开时连 UI 都不出现，
     * 避免再造悬空配置）。
     */
    @GetMapping("/reject-targets")
    public Result<Map<String, Object>> rejectTargets(@RequestParam String taskId) {
        wfReadGuard.assertTaskReadable(taskId);
        return Result.success(wfEngineFacade.rejectTargets(taskId));
    }

    /**
     * 「已办撤销回原节点」只读预览（P3 G3）：列出可回退的已办节点与各自的影响面，
     * <b>不修改任何流程数据</b>。
     *
     * <p>执行面尚未开放 —— Flowable 8 没有 {@code ProcessInstanceModification}，等价原语
     * {@code ChangeActivityStateBuilder.moveActivityIdTo} 属「搬执行」，会真实改动在办单据本体。
     * 故本端点只提供核对依据；开关 {@code sys_config.wf.withdraw.done.enabled} 缺省 0（fail-closed）。
     *
     * <p>入参三者取其一：{@code procInstId} 直接用；{@code taskId} 反查实例（含已办结任务）；
     * {@code keyword} 自动识别（实例 ID 与任务 ID 都是数字串，无法按格式区分）。
     */
    @GetMapping("/withdraw-preview")
    public Result<Map<String, Object>> withdrawPreview(@RequestParam(required = false) String procInstId,
                                                      @RequestParam(required = false) String taskId,
                                                      @RequestParam(required = false) String keyword) {
        String pi = procInstId;
        if ((pi == null || pi.isBlank()) && taskId != null && !taskId.isBlank()) {
            pi = wfEngineFacade.procInstIdOfTask(taskId);
        }
        if (pi == null || pi.isBlank()) {
            pi = wfEngineFacade.resolvePreviewTarget(keyword);
        }
        if (pi == null || pi.isBlank()) {
            return Result.fail(com.fssc.common.core.ErrorCode.PARAM_INVALID.getCode(),
                    "请提供 procInstId、taskId 或 keyword 之一");
        }
        wfReadGuard.assertInstanceReadable(pi);
        return Result.success(wfEngineFacade.withdrawPreview(pi));
    }

    /**
     * 下一环节候选办理人（审批弹窗打开时调用）：返回下一人工节点的取人方式（AUTO/PICK）、
     * 候选人名单与 required 标志，前端据此决定是否显示必选下拉。
     *
     * <p>与 {@code /approve} 同一套校验口径（{@code WfEngineFacade.nextCandidates} 内做 canOperate 校验），
     * 因此即使有人绕开弹窗直接提交点名，也只会在 complete 时被拒绝，不会越权指派。
     */
    @GetMapping("/next-candidates")
    public Result<Map<String, Object>> nextCandidates(@RequestParam String taskId) {
        return Result.success(wfEngineFacade.nextCandidates(taskId, UserContext.getRequired().getUserId()));
    }

    /**
     * 提单态首审环节候选办理人（方案 G2-3）：提单页据此决定是否显示"第一道审批给谁"下拉。
     *
     * <p>只读、按配置驱动：首审节点不是 PICK 档（含灰度未放行）时返回 mode=AUTO，前端不显示任何选人控件。
     * 需要单据已保存（{@code bizId} 必填）——候选人按单据所属公司过滤，而公司只能从单据行取。
     */
    @GetMapping("/start-candidates")
    public Result<Map<String, Object>> startCandidates(@RequestParam String bizType,
                                                       @RequestParam Long bizId,
                                                       @RequestParam(required = false) String subType,
                                                       @RequestParam(required = false) java.math.BigDecimal amount) {
        return Result.success(wfEngineFacade.startCandidates(bizType, bizId, subType, amount,
                UserContext.getRequired().getUserId()));
    }

    /**
     *  批次3 R2：路由试算（只读，不写库、不发起实例）。返回绑定表命中值与最终落点，
     * 用于流程绑定镜像迁移的改前/改后比对，也回答运维"这张单点提交会落到哪条流程"。
     */
    @GetMapping("/routing-preview")
    public Result<Map<String, Object>> routingPreview(@RequestParam String bizType,
                                                      @RequestParam(required = false) String subType,
                                                      @RequestParam(required = false) java.math.BigDecimal amount) {
        return Result.success(wfEngineFacade.previewRouting(bizType, subType, amount));
    }

    /**
     * P0 治理闸③：分支变量实算透出（只读，不写库、不发起实例）。给定 bizType + bizId，
     * 返回该单会注入引擎的全部分支变量真值（amount / orgType / expenseType / hasLoan / outOfPlan /
     * overLimit / hasContract / abroad / ...），便于运维在设计器外核查"这张单的分支会怎么走"——
     * 尤其是此前被写死 FALSE 的 4 个变量，现在是否真实生效一目了然。
     */
    @GetMapping("/branch-vars")
    public Result<Map<String, Object>> branchVars(@RequestParam String bizType,
                                                  @RequestParam Long bizId,
                                                  @RequestParam(required = false) String subType) {
        return Result.success(wfEngineFacade.resolveBranchVars(bizType, bizId, subType));
    }

    /** 按业务单据审批：bizType + bizId → 查找活跃任务 → 完成（列表页通用审批入口） */
    @OperLog(module = "WF_TASK", operType = "UPDATE", desc = "按单据审批")
    @PostMapping("/approve-bill")
    public Result<Void> approveByBill(@RequestParam String bizType,
                                       @RequestParam Long bizId,
                                       @RequestParam(defaultValue = "true") boolean approved,
                                       @RequestParam(required = false) String comment) {
        wfEngineFacade.completeByBill(bizType, bizId, approved, comment,
                UserContext.getRequired().getUserId());
        return Result.success();
    }

    /**  转办：把当前待办改派给其他用户办理（受理人变更，留痕 TRANSFER） */
    @OperLog(module = "WF_TASK", operType = "UPDATE", desc = "转办")
    @PostMapping("/transfer")
    public Result<Void> transfer(@Validated @RequestBody TransferReq req) {
        wfEngineFacade.transfer(req.getTaskId(), req.getTargetUserId(), req.getComment(),
                UserContext.getRequired().getUserId());
        return Result.success();
    }

    /**
     * 加签人办完<b>自动回跳</b>，不改变流程走向。
     *
     * <p>与 {@code /transfer} 的区别：转办是「受理人永久变更」；加签是「临时插一道」——
     * 加签人在待办里看到的是同一个任务，但他的「同意」只代表加签这一道审完，流程不会往前推。
     * 故前端对加签人显示的是「加签审批」语义，请勿与转办混用。
     */
    @OperLog(module = "WF_TASK", operType = "UPDATE", desc = "加签")
    @PostMapping("/add-sign")
    public Result<Map<String, Object>> addSign(@Validated @RequestBody AddSignReq req) {
        com.fssc.workflow.entity.WfAddSign rec = wfEngineFacade.addSign(
                req.getTaskId(), req.getPosition(), req.getSignUsers(), req.getReason());
        Map<String, Object> out = new HashMap<>();
        out.put("id", rec.getId());
        out.put("position", rec.getPosition());
        out.put("signUsers", rec.getSignUsers());
        out.put("status", rec.getStatus());
        return Result.success(out);
    }

    /** 取消加签：加签发起人或当期加签人本人可取消；进行中的加签取消时任务先交还回跳目标 */
    @OperLog(module = "WF_TASK", operType = "UPDATE", desc = "取消加签")
    @PostMapping("/add-sign/{id}/cancel")
    public Result<Void> cancelAddSign(@org.springframework.web.bind.annotation.PathVariable Long id) {
        wfEngineFacade.cancelAddSign(id);
        return Result.success();
    }

    /**
     * 该流程实例上仍在进行或待生效的加签（只读）：前端据此显示"当前在加签：X 等待办理"并给取消入口。
     * 不传 {@code procInstId} 而传 {@code taskId} 时，自动用该任务所属实例查询。
     */
    @GetMapping("/add-sign/open")
    public Result<List<com.fssc.workflow.entity.WfAddSign>> openAddSign(
            @RequestParam(required = false) String procInstId,
            @RequestParam(required = false) String taskId) {
        return Result.success(wfEngineFacade.openAddSign(procInstId, taskId));
    }

    /**
     * 因此弹窗里能选到的人，提交时一定通过。名单为空时前端提示"请联系流程管理员改派"。
     */
    @GetMapping("/task-candidates")
    public Result<Map<String, Object>> taskCandidates(@RequestParam String taskId) {
        return Result.success(wfEngineFacade.taskCandidates(taskId, UserContext.getRequired().getUserId()));
    }

    /**
     * G13 字段级节点权限（P3，2026-09-23）：按任务解析当前节点 {@code wf_node_config.form_perm}，
     * 返回 {@code field -> H|R|W}（H 隐藏 / R 只读 / W 可编辑），供审批页渲染（隐藏/禁用字段）。
     *
     * <p>口径与 {@code /task-candidates} 一致——本节点可办场景才谈得上字段权限；解析逻辑集中在
     * {@link WfNodeConfigResolver#resolveFormFieldPerm}（取严不取宽 H&gt;R&gt;W，fail-open：
     * 未配置/损坏返回空 Map，即不裁剪，保持历史行为）。此前该列只有实体字段、零消费。
     */
    @GetMapping("/task-form-perm")
    public Result<Map<String, Object>> taskFormPerm(@RequestParam String taskId) {
        // B1 扩点（2026-10-14）：补读护栏，与 /approval-items、/mi-progress、/reject-targets 同口径——
        // 本端点可读出节点字段级权限结构（哪些字段被隐藏/只读），属任务敏感信息，
        // 仅本节点可办场景（办理人/候选人）可读，杜绝越权探知他人节点的表单裁剪配置。
        wfReadGuard.assertTaskReadable(taskId);
        Map<String, Object> out = new HashMap<>();
        out.put("taskId", taskId);
        WfTask t = wfTaskMapper.selectOne(new LambdaQueryWrapper<WfTask>()
                .eq(WfTask::getTaskId, taskId)
                .eq(WfTask::getStatus, "PENDING")
                .last("LIMIT 1"));
        if (t == null || t.getModelKey() == null || t.getNodeKey() == null) {
            // 任务已办结/不存在/镜像缺列：fail-open 空 Map，前端不裁剪
            out.put("fieldPerm", Map.of());
            return Result.success(out);
        }
        out.put("modelKey", t.getModelKey());
        out.put("nodeKey", t.getNodeKey());
        out.put("fieldPerm", wfNodeConfigResolver.resolveFormFieldPerm(t.getModelKey(), t.getNodeKey()));
        return Result.success(out);
    }

    /**
     * 领取任务（POOL 任务池）：候选人把"仅候选组"态的任务锁定到自己名下。并发抢单由 Flowable claim
     * 保证只有一人成功，败者收到"任务已被他人领取"提示后刷新列表即可。
     */
    @OperLog(module = "WF_TASK", operType = "UPDATE", desc = "领取任务")
    @PostMapping("/claim")
    public Result<Void> claim(@RequestParam String taskId) {
        wfEngineFacade.claim(taskId, UserContext.getRequired().getUserId());
        return Result.success();
    }

    /** 释放任务（取消领取）：退回候选组态，同池其他人可再领取；只有领取人本人可释放 */
    @OperLog(module = "WF_TASK", operType = "UPDATE", desc = "释放任务")
    @PostMapping("/unclaim")
    public Result<Void> unclaim(@RequestParam String taskId) {
        wfEngineFacade.unclaim(taskId, UserContext.getRequired().getUserId());
        return Result.success();
    }

    /**
     * 改派（L2，流程管理员通道）：把在办任务改指给任意可办理的真人，用于审批人离职/换岗/本节点无人可办。
     * 授权靠权限点 wf:manage:intervene / wf:task:reassign（多码 OR，不要求操作人是该节点候选人），
     * 目标与原因由门面二次硬校验。
     */
    @RequiresPerm({"wf:manage:intervene", "wf:task:reassign"})
    @OperLog(module = "WF_TASK", operType = "UPDATE", desc = "流程改派")
    @PostMapping("/reassign")
    public Result<Void> reassign(@Validated @RequestBody ReassignReq req) {
        wfEngineFacade.reassign(req.getTaskId(), req.getTargetUserId(), req.getReason(),
                UserContext.getRequired().getUserId());
        return Result.success();
    }

    /**
     * 发起人催办（体检 E-10 / 方案 F1-5）：提单人问一句"我这单卡在哪"。
     *
     * <p>此前催办只有超时作业会触发，提单人侧零入口；审批工作台的催办视图又是流程管理员视角。
     * 鉴权、24 小时限频、站内信投递与 {@code URGE} 留痕都在 {@code WfTimeoutJob.urgeAsInitiator}，
     * 与超时催办共用同一发信通道，口径不漂移。命中限频时以业务异常返回（界面需要明确文案，
     * 而不是"催办成功"却什么都没发）。
     *
     * @return 本次实际催办的任务数（并行/会签多环节时 &gt; 1）
     */
    @OperLog(module = "WF_TASK", operType = "UPDATE", desc = "发起人催办")
    @PostMapping("/instance/urge-initiator")
    public Result<Integer> urgeAsInitiator(@RequestParam @NotBlank(message = "procInstId 必填") String procInstId) {
        com.fssc.framework.security.LoginUser u = UserContext.getRequired();
        String label = u.getRealName() == null || u.getRealName().isBlank() ? u.getUsername() : u.getRealName();
        return Result.success(wfTimeoutJob.urgeAsInitiator(procInstId, u.getUserId(), label));
    }

    @Data
    public static class ApproveReq {
        @NotBlank(message = "taskId 必填")
        private String taskId;
        private boolean approved = true;
        private String comment;
        /** 取人方式 PICK 的下一节点必填：点名由谁来办下一环节。其余节点忽略此字段。 */
        private Long nextAssigneeUserId;
        /**
         * G2 传阅：本次审批通过后要抄送给谁（用户ID，逗号分隔），可空。
         *
         * <p>与节点配置 {@code wf_node_config.cc_users/cc_roles} 取<b>并集</b>后写入 {@code wf_cc}，
         * 由前端「审批弹窗 → 抄送给」多选产生。不传＝只按节点配置抄送（节点未配则不产生任何抄送），
         * 故对该字段的历史调用方<b>完全向后兼容</b>。
         *
         * <p>D-V3-148：前端（useApprovalDialog/ApprovalDialog）传的是 <b>JSON 数组</b>
         * （数字或字符串元素均有），按 String 反序列化直接 HttpMessageNotReadable → 500，
         * 传阅功能整链不可用。改为 List 反序列化、入口处归一为逗号分隔串，
         * 字符串逗号分隔的旧调用方同样兼容（Jackson 对 String→List 单元素也兼容，
         * 统一在 ccUserIdsNormalized() 收口）。
         */
        private java.util.List<Object> ccUserIds;

        /** D-V3-148：数组/逗号分隔串两种形态归一为门面期望的 "1,2,3" 串；空返回 null。 */
        public String ccUserIdsNormalized() {
            if (ccUserIds == null || ccUserIds.isEmpty()) {
                return null;
            }
            String joined = ccUserIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(v -> String.valueOf(v).trim())
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining(","));
            return joined.isEmpty() ? null : joined;
        }
        /**
         * G4 自由驳回：驳回时指定的目标节点 key（仅 approved=false 时生效），可空。
         *
         * <p>为空 ⇒ 走原「终止本流程」兜底，行为与改造前逐字一致；非空且开关
         * {@code sys_config.wf.reject.free.enabled}=1 时，改由引擎把执行退回该上游节点，
         * 候选集从 {@code GET /wf/reject-targets} 只读预览获取。
         */
        private String rejectToNodeKey;
        /**
         * G2 ①层「审批项」：本次审批采用的自定义审批结果编码（{@code wf_approval_item.item_code}），可空。
         *
         * <p>为空 ⇒ 完全不走审批项分支（不读表、不写 {@code ActionResult} 结果变量、意见文案逐字不变），
         * 因此对该字段的历史调用方<b>完全向后兼容</b>。
         *
         * <p>非空时审批项决定结果类别（正向/驳回/终止）并<b>覆盖</b> {@code approved}：
         * 点「终止」按钮时前端仍传 {@code approved=true}，由服务端按审批项类别反转为终止语义——
         * 避免"按钮文案与引擎指令"两处各表一套真值。编码在本节点解析不到时接口明确报错。
         * 候选集从 {@code GET /wf/approval-items?taskId=} 获取。
         */
        private String approvalItemCode;
    }

    @Data
    public static class TransferReq {
        @NotBlank(message = "taskId 必填")
        private String taskId;
        @jakarta.validation.constraints.NotNull(message = "targetUserId 必填")
        private Long targetUserId;
        private String comment;
    }

    /** 加签请求：position=BEFORE 前加签 / AFTER 后加签；signUsers 为逗号分隔的用户ID（多人=链式依次签） */
    @Data
    public static class AddSignReq {
        @NotBlank(message = "taskId 必填")
        private String taskId;
        @NotBlank(message = "请选择加签位置（BEFORE 前加签 / AFTER 后加签）")
        private String position;
        @NotBlank(message = "请至少选择一位加签人")
        private String signUsers;
        private String reason;
    }

    @Data
    public static class ReassignReq {
        @NotBlank(message = "taskId 必填")
        private String taskId;
        @jakarta.validation.constraints.NotNull(message = "targetUserId 必填")
        private Long targetUserId;
        @NotBlank(message = "请填写改派原因")
        private String reason;
    }
}
