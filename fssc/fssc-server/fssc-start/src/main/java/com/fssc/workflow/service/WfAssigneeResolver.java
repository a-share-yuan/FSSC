package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.system.entity.SysDept;
import com.fssc.system.entity.SysPost;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysDeptMapper;
import com.fssc.system.mapper.SysPostMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.workflow.entity.WfAssigneeDim;
import com.fssc.workflow.entity.WfAssigneeScheme;
import com.fssc.workflow.entity.WfInstance;
import com.fssc.workflow.mapper.WfAssigneeDimMapper;
import com.fssc.workflow.mapper.WfAssigneeSchemeMapper;
import com.fssc.workflow.mapper.WfInstanceMapper;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 节点参与人解析器（G2「维度组合 ＋ 参与人方案」）—— {@code wf_assignee_scheme} / {@code wf_assignee_dim}
 * 的<b>唯一运行时消费点</b>。
 *
 * <p><b>为什么要有它</b>：{@code wf_node_config.assign_type} 只实现了 USER/ROLE/INITIATOR/DEPT_LEADER/
 * DEPT_LEADER_UP 五档，POST/EXPRESSION/SHARE_POOL 等配置落进 {@code default} 分支后被<b>静默 no-op</b>
 * ——配置者以为配了岗位取人，运行时其实没有任何人被指派，单据只能靠"往主数据里铺角色/补岗"绕行。
 * 本解析器把取人从"单一枚举"升级为「多套方案（优先级＋条件）＋ 方案内多维度组合」：
 *
 * <ol>
 *   <li><b>方案降级</b>：按 {@code priority} 升序（同优先级按 {@code sort}、{@code id}）逐套试，
 *       条件不满足或解析不出人的方案被跳过，直到某一套解析出人；</li>
 *   <li><b>全不中＝失败并冒泡</b>：所有方案都解析不出 ⇒ 抛 {@link BizException}。这一条是刻意的：
 *       静默留一个没人可办的候选组，等于把"配置错误"变成"单据永久滞留"，比报错更难发现；</li>
 *   <li><b>完全没配方案＝零变化</b>：{@link #resolve} 返回 {@code configured=false}，调用方保持原行为
 *       逐字不变（这是默认零变化的硬约束，也是回滚路径）。</li>
 * </ol>
 *
 * <p><b>取人铁律</b>：所有维度解析出的人都要过 {@link #isRealUser}（在职 NORMAL、非 {@code is_admin=1}、
 * 非 {@code user_name='admin'}）——与 {@code NodeOrgResolver} 同一规则，admin 不得成为业务审批节点的办理人。
 *
 * <p><b>汇报关系复用既有能力</b>：向上方向（直接/间接上级）直接复用
 * {@link NodeOrgResolver#escalateTarget(String, Integer)}（沿 {@code sys_dept.leader_id} 向上 ≤10 级的升级链，
 * 跳过发起人本人、跳过 admin、级数不足取已到最高级），不另造一套"往上报"的规则；只有既有链未覆盖的
 * 平级/下级两个方向由本类按 {@code sys_dept.parent_id} / {@code sys_dept.leader_id} 补充实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfAssigneeResolver {

    private final WfAssigneeSchemeMapper schemeMapper;
    private final WfAssigneeDimMapper dimMapper;
    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysPostMapper sysPostMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final RuntimeService runtimeService;
    private final TaskService flowTaskService;
    private final NodeOrgResolver nodeOrgResolver;

    /** 条件表达式的最小文法（写入侧与预览接口共用同一份说明，避免两处规则漂移） */
    public static final String CONDITION_GRAMMAR =
            "变量：amount/bizType/subType/companyId/deptId/startUserId 及流程实例变量原名；"
                    + "比较：== != > >= < <=（右值可写数字或 '单引号字符串'）；组合：&&（与）||（或）。"
                    + "留空＝无条件适用；取不到的变量按「该比较为假」处理。";

    /** 解析结果：{@code configured=false} 表示该节点根本没配方案（调用方须保持原行为，不得改动） */
    public record Resolution(boolean configured, List<Long> userIds) {
    }

    /**
     * 解析某节点的办理人（维度并集/交集/排除，多方案按优先级降级）。
     *
     * @param modelKey 流程定义 key（与 wf_node_config.model_key 同源）
     * @param nodeKey 节点 key（BPMN userTask id）
     * @param procInstId 流程实例 ID（维度解析的组织上下文；可空，空则退化为"无单据上下文"规则）
     * @param task 当前任务（EXCLUDE 组合模式需要它取候选组；可空）
     * @return {@code configured=false} ⇒ 该节点无方案，调用方必须保持原行为；
     *         {@code configured=true} ⇒ {@code userIds} 必然非空
     * @throws BizException 配了方案但全部方案都解析不出人（禁止静默留候选组）
     */
    public Resolution resolve(String modelKey, String nodeKey, String procInstId, Task task) {
        List<WfAssigneeScheme> schemes = enabledSchemes(modelKey, nodeKey);
        if (schemes.isEmpty()) {
            return new Resolution(false, List.of());
        }
        Map<String, Object> vars = variables(procInstId);
        List<String> rejected = new ArrayList<>();
        for (WfAssigneeScheme s : schemes) {
            if (!conditionMatched(s.getConditionExpr(), vars)) {
                rejected.add("方案#" + s.getId() + "(priority=" + s.getPriority() + ")条件不满足");
                continue;
            }
            List<WfAssigneeDim> dims = dimsOf(s.getId());
            if (dims.isEmpty()) {
                rejected.add("方案#" + s.getId() + "(priority=" + s.getPriority() + ")未配维度");
                continue;
            }
            List<Long> ids = combine(s, dims, procInstId, task, vars);
            if (!ids.isEmpty()) {
                log.info("参与人方案命中: model={} node={} scheme={} priority={} -> {}",
                        modelKey, nodeKey, s.getId(), s.getPriority(), ids);
                return new Resolution(true, ids);
            }
            rejected.add("方案#" + s.getId() + "(priority=" + s.getPriority() + ")解析不出人");
        }
        throw new BizException(ErrorCode.PARAM_INVALID,
                "节点「" + nodeKey + "」参与人解析失败：已配置 " + schemes.size() + " 套方案，但均未解析出可办理人（"
                        + String.join("；", rejected) + "）。请检查方案的条件/维度配置，"
                        + "或补齐对应岗位、角色、部门下的在岗人员（admin 不作为业务节点办理人）。");
    }

    /**
     * 只读解析预览：回答"这个节点现在会解析到谁"。<b>不修改任何数据、不抛业务异常</b>——
     * 预览的职责是让人在配置页看清结果，失败也要以"看得懂的原因"呈现，而不是把异常甩给界面。
     */
    public Map<String, Object> preview(String modelKey, String nodeKey, String procInstId, Task task) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modelKey", modelKey);
        out.put("nodeKey", nodeKey);
        out.put("procInstId", procInstId);
        out.put("configured", false);
        out.put("resolved", false);
        out.put("userIds", List.of());
        out.put("users", List.of());
        out.put("schemes", List.of());
        out.put("conditionGrammar", CONDITION_GRAMMAR);
        List<WfAssigneeScheme> schemes = enabledSchemes(modelKey, nodeKey);
        if (schemes.isEmpty()) {
            out.put("notice", "该节点未配置参与人方案：运行时保持 wf_node_config.assign_type 的既有取人行为"
                    + "（解析器不介入，行为与改造前逐字一致）。");
            return out;
        }
        out.put("configured", true);
        Map<String, Object> vars = variables(procInstId);
        List<Map<String, Object>> schemeRows = new ArrayList<>();
        List<Long> hit = null;
        for (WfAssigneeScheme s : schemes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("schemeId", s.getId());
            row.put("priority", s.getPriority());
            row.put("combineMode", s.getCombineMode() == null ? "OR" : s.getCombineMode());
            row.put("conditionExpr", s.getConditionExpr());
            boolean cond = conditionMatched(s.getConditionExpr(), vars);
            row.put("conditionMatched", cond);
            List<WfAssigneeDim> dims = dimsOf(s.getId());
            List<Map<String, Object>> dimRows = new ArrayList<>();
            for (WfAssigneeDim d : dims) {
                Map<String, Object> dr = new LinkedHashMap<>();
                dr.put("dimType", d.getDimType());
                dr.put("dimValue", d.getDimValue());
                dr.put("relation", d.getRelation());
                dr.put("upLevel", d.getUpLevel());
                List<Long> one = resolveDim(d, procInstId, vars);
                dr.put("userIds", one);
                dr.put("count", one.size());
                dimRows.add(dr);
            }
            row.put("dims", dimRows);
            List<Long> ids = cond ? combine(s, dims, procInstId, task, vars) : List.of();
            row.put("userIds", ids);
            row.put("count", ids.size());
            row.put("hit", !ids.isEmpty());
            if (hit == null && !ids.isEmpty()) {
                hit = ids;
            }
            schemeRows.add(row);
        }
        out.put("schemes", schemeRows);
        if (hit != null) {
            out.put("resolved", true);
            out.put("userIds", hit);
            out.put("users", userNames(hit));
        } else {
            out.put("notice", "已配置方案，但当前条件与维度下解析不出任何可办理人：运行时会抛业务异常"
                    + "（不允许静默留候选组），请先补齐数据或调整方案。");
        }
        return out;
    }

    /**
     * 只读解析预览（按 {@code taskId} 取当前任务）—— 配置页/待办页的调用入口。
     *
     * <p>与 {@link #preview(String, String, String, Task)} 的差异只有一处：任务由本方法自己按 ID 查。
     * 之所以由解析器查而不是让 Controller 直接碰 Flowable TaskService：本项目有"业务域禁止直接用
     * Flowable API"的红线，取任务也属于该规则（NodeOrgResolver 同样是这么做的）。
     *
     * <p>{@code modelKey}/{@code nodeKey} 留空时按任务的 {@code processDefinitionId} 与
     * {@code taskDefinitionKey} 反推（BPMN 生成的任务 id 形如 {@code Task_N}，与配置表的 node_key
     * 同形，故反推可靠）；任务查不到（已办结/传错 ID）则按"无任务"规则解析，仅影响 EXCLUDE 组合模式。
     */
    public Map<String, Object> previewByTask(String modelKey, String nodeKey, String procInstId, String taskId) {
        Task task = null;
        if (taskId != null && !taskId.isBlank()) {
            try {
                task = EngineTenantScope.taskQuery(flowTaskService).taskId(taskId).singleResult();
            } catch (Exception ex) {
                log.warn("参与人预览：按 taskId 查任务失败 taskId={}: {}", taskId, ex.getMessage());
            }
        }
        String mk = modelKey;
        String nk = nodeKey;
        String pi = procInstId;
        if (task != null) {
            if (pi == null || pi.isBlank()) {
                pi = task.getProcessInstanceId();
            }
            if (nk == null || nk.isBlank()) {
                nk = task.getTaskDefinitionKey();
            }
            if (mk == null || mk.isBlank()) {
                String defId = task.getProcessDefinitionId();
                mk = defId == null ? null : defId.split(":")[0];
            }
        }
        return preview(mk, nk, pi, task);
    }

    // 方案 / 维度装载

    private List<WfAssigneeScheme> enabledSchemes(String modelKey, String nodeKey) {
        if (modelKey == null || modelKey.isBlank() || nodeKey == null || nodeKey.isBlank()) {
            return List.of();
        }
        try {
            return schemeMapper.selectList(new LambdaQueryWrapper<WfAssigneeScheme>()
                    .eq(WfAssigneeScheme::getModelKey, modelKey)
                    .eq(WfAssigneeScheme::getNodeKey, nodeKey)
                    .eq(WfAssigneeScheme::getEnabled, 1)
                    .orderByAsc(WfAssigneeScheme::getPriority)
                    .orderByAsc(WfAssigneeScheme::getSort)
                    .orderByAsc(WfAssigneeScheme::getId));
        } catch (Exception ex) {
            log.warn("参与人方案查询失败（按未配置处理，保持既有取人行为）model={} node={}: {}",
                    modelKey, nodeKey, ex.getMessage());
            return List.of();
        }
    }

    private List<WfAssigneeDim> dimsOf(Long schemeId) {
        if (schemeId == null) {
            return List.of();
        }
        return dimMapper.selectList(new LambdaQueryWrapper<WfAssigneeDim>()
                .eq(WfAssigneeDim::getSchemeId, schemeId)
                .orderByAsc(WfAssigneeDim::getSort)
                .orderByAsc(WfAssigneeDim::getId));
    }

    // 组合

    private List<Long> combine(WfAssigneeScheme scheme, List<WfAssigneeDim> dims,
                               String procInstId, Task task, Map<String, Object> vars) {
        String mode = scheme.getCombineMode() == null ? "OR" : scheme.getCombineMode().trim().toUpperCase();
        if ("EXCLUDE".equals(mode)) {
            // 排除集：基集＝本节点 BPMN 候选组解析出的人（无候选组＝无法构造基集，本方案降级）
            List<Long> base = candidateGroupUsers(task, procInstId);
            if (base.isEmpty()) {
                log.warn("EXCLUDE 方案 #{} 无法构造基集（节点无候选组或候选组解析不出人），降级到下一优先级",
                        scheme.getId());
                return List.of();
            }
            base.removeAll(union(dims, procInstId, vars));
            return base;
        }
        if ("AND".equals(mode)) {
            List<Long> acc = null;
            for (WfAssigneeDim d : dims) {
                List<Long> one = resolveDim(d, procInstId, vars);
                if (one.isEmpty()) {
                    return List.of();
                }
                if (acc == null) {
                    acc = new ArrayList<>(one);
                } else {
                    acc.retainAll(one);
                }
                if (acc.isEmpty()) {
                    return List.of();
                }
            }
            return acc == null ? List.of() : acc;
        }
        return union(dims, procInstId, vars);
    }

    private List<Long> union(List<WfAssigneeDim> dims, String procInstId, Map<String, Object> vars) {
        Set<Long> out = new LinkedHashSet<>();
        for (WfAssigneeDim d : dims) {
            out.addAll(resolveDim(d, procInstId, vars));
        }
        return new ArrayList<>(out);
    }

    // 单维度解析

    private List<Long> resolveDim(WfAssigneeDim dim, String procInstId, Map<String, Object> vars) {
        String type = dim.getDimType() == null ? "" : dim.getDimType().trim().toUpperCase();
        try {
            return switch (type) {
                case "USER" -> realUsers(idList(dim.getDimValue()));
                case "ROLE" -> roleUsers(dim.getDimValue(), procInstId);
                case "POST" -> postUsers(dim.getDimValue());
                case "DEPT" -> deptUsers(idList(dim.getDimValue()),
                        dim.getIncludeChildren() != null && dim.getIncludeChildren() == 1);
                case "DEPT_TREE" -> deptUsers(idList(dim.getDimValue()), true);
                case "DEPT_LEADER" -> deptLeaders(dim.getDimValue(), procInstId);
                case "REPORT_TO" -> reportTo(dim, procInstId);
                case "VARIABLE" -> realUsers(idList(stringVar(vars, dim.getDimValue())));
                default -> {
                    log.warn("参与人维度类型未识别（本维度不产出人）: dimId={} dimType={}", dim.getId(), dim.getDimType());
                    yield List.of();
                }
            };
        } catch (BizException be) {
            throw be;
        } catch (Exception ex) {
            log.warn("参与人维度解析异常（本维度按空处理，交由方案降级）dimId={} dimType={}: {}",
                    dim.getId(), dim.getDimType(), ex.getMessage());
            return List.of();
        }
    }

    /** 角色维度：规则完全复用 {@code NodeOrgResolver.listRoleCandidates}（GROUP 跨公司 / COMPANY 按单据公司 / 排除 admin） */
    private List<Long> roleUsers(String csv, String procInstId) {
        Set<Long> out = new LinkedHashSet<>();
        for (String code : splitCsv(csv)) {
            out.addAll(nodeOrgResolver.listRoleCandidates(procInstId, code));
        }
        return new ArrayList<>(out);
    }

    /** 岗位维度：dimValue 可写岗位 ID 或岗位编码；岗位自带公司归属，不再叠加公司过滤 */
    private List<Long> postUsers(String csv) {
        Set<Long> out = new LinkedHashSet<>();
        for (String token : splitCsv(csv)) {
            SysPost post = null;
            try {
                post = sysPostMapper.selectById(Long.parseLong(token));
            } catch (NumberFormatException ignored) {
                // 非数字 ⇒ 按岗位编码查
            }
            if (post == null) {
                List<SysPost> byCode = sysPostMapper.selectList(new LambdaQueryWrapper<SysPost>()
                        .eq(SysPost::getPostCode, token).last("limit 1"));
                post = byCode.isEmpty() ? null : byCode.get(0);
            }
            if (post == null || post.getId() == null) {
                log.warn("参与人岗位维度取不到岗位（ID/编码都不存在）: {}", token);
                continue;
            }
            for (SysUser u : sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPostId, post.getId())
                    .eq(SysUser::getStatus, "NORMAL")
                    .eq(SysUser::getIsAdmin, 0)
                    .ne(SysUser::getUserName, "admin")
                    .orderByAsc(SysUser::getId))) {
                out.add(u.getId());
            }
        }
        return new ArrayList<>(out);
    }

    /** 部门维度：includeChildren=true 时含全部子孙部门（按 parent_id 向下展开） */
    private List<Long> deptUsers(List<Long> deptIds, boolean includeChildren) {
        if (deptIds.isEmpty()) {
            return List.of();
        }
        Set<Long> scope = new LinkedHashSet<>(deptIds);
        if (includeChildren) {
            scope.addAll(descendantDeptIds(scope, Integer.MAX_VALUE));
        }
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getDeptId, scope)
                .eq(SysUser::getStatus, "NORMAL")
                .eq(SysUser::getIsAdmin, 0)
                .ne(SysUser::getUserName, "admin")
                .orderByAsc(SysUser::getId));
        List<Long> out = new ArrayList<>();
        for (SysUser u : users) {
            out.add(u.getId());
        }
        return out;
    }

    /** 部门负责人维度：dimValue 留空时取发起人所在部门的负责人 */
    private List<Long> deptLeaders(String csv, String procInstId) {
        List<Long> deptIds = idList(csv);
        if (deptIds.isEmpty()) {
            Long subject = resolveSubject(procInstId);
            SysUser su = subject == null ? null : sysUserMapper.selectById(subject);
            if (su == null || su.getDeptId() == null) {
                return List.of();
            }
            deptIds = List.of(su.getDeptId());
        }
        List<Long> leaders = new ArrayList<>();
        for (Long deptId : deptIds) {
            SysDept d = sysDeptMapper.selectById(deptId);
            if (d != null && d.getLeaderId() != null && !leaders.contains(d.getLeaderId())) {
                leaders.add(d.getLeaderId());
            }
        }
        return realUsers(leaders);
    }

    /**
     * 汇报关系维度。
     *
     * <p>向上两档（{@code DIRECT_UP}/{@code INDIRECT_UP}）复用 {@code NodeOrgResolver.escalateTarget}：
     * 主体固定为<b>发起人</b>（与该方法的既有规则一致——它读 {@code wf_instance.start_user_id}），
     * 级数取 {@code up_level}。因此 {@code dim_value} 对向上方向不生效（写了也只作记录），
     * 这是"能复用就复用、不另造一套往上报规则"的直接结果。
     *
     * <p>平级/向下两档既有链未覆盖，按部门树实现：平级＝同父部门的其它负责人；向下＝本人任负责人的
     * 部门成员（间接下级再向下展开 {@code up_level-1} 层）。
     */
    private List<Long> reportTo(WfAssigneeDim dim, String procInstId) {
        String rel = dim.getRelation() == null ? "DIRECT_UP" : dim.getRelation().trim().toUpperCase();
        boolean indirect = "INDIRECT_UP".equals(rel) || "INDIRECT_DOWN".equals(rel);
        int level = dim.getUpLevel() == null ? (indirect ? 2 : 1) : Math.max(1, dim.getUpLevel());
        return switch (rel) {
            case "DIRECT_UP", "INDIRECT_UP" -> {
                Long target = nodeOrgResolver.escalateTarget(procInstId, level);
                yield target == null ? List.of() : List.of(target);
            }
            case "PEER" -> peerLeaders(subjectOf(dim, procInstId));
            case "DIRECT_DOWN" -> subordinates(subjectOf(dim, procInstId), 1);
            case "INDIRECT_DOWN" -> subordinates(subjectOf(dim, procInstId), level);
            default -> {
                log.warn("汇报关系取值未识别（本维度不产出人）: relation={}（合法值 DIRECT_UP/INDIRECT_UP/PEER/DIRECT_DOWN/INDIRECT_DOWN）", rel);
                yield List.of();
            }
        };
    }

    private Long subjectOf(WfAssigneeDim dim, String procInstId) {
        List<Long> explicit = idList(dim.getDimValue());
        return explicit.isEmpty() ? resolveSubject(procInstId) : explicit.get(0);
    }

    /** 平级：与本人所在部门同父（parent_id 相同）的其它部门的负责人 */
    private List<Long> peerLeaders(Long subject) {
        if (subject == null) {
            return List.of();
        }
        SysUser su = sysUserMapper.selectById(subject);
        if (su == null || su.getDeptId() == null) {
            return List.of();
        }
        SysDept own = sysDeptMapper.selectById(su.getDeptId());
        if (own == null || own.getParentId() == null) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (SysDept sib : sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, own.getParentId()))) {
            Long leader = sib.getLeaderId();
            if (leader != null && !leader.equals(subject) && !out.contains(leader)) {
                out.add(leader);
            }
        }
        return realUsers(out);
    }

    /** 下级：本人任负责人的部门成员（不含本人）；level>1 时再向下展开 level-1 层子孙部门 */
    private List<Long> subordinates(Long subject, int level) {
        if (subject == null) {
            return List.of();
        }
        List<SysDept> led = sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getLeaderId, subject));
        if (led.isEmpty()) {
            return List.of();
        }
        Set<Long> scope = new LinkedHashSet<>();
        for (SysDept d : led) {
            scope.add(d.getId());
        }
        int extra = Math.max(0, level - 1);
        if (extra > 0) {
            scope.addAll(descendantDeptIds(scope, extra));
        }
        List<Long> members = new ArrayList<>(deptUsers(new ArrayList<>(scope), false));
        members.remove(subject);
        return members;
    }

    /** 候选组（角色）解析出的候选人：EXCLUDE 组合模式的基集 */
    private List<Long> candidateGroupUsers(Task task, String procInstId) {
        if (task == null) {
            return new ArrayList<>();
        }
        Set<Long> out = new LinkedHashSet<>();
        try {
            for (IdentityLink il : flowTaskService.getIdentityLinksForTask(task.getId())) {
                if ("candidate".equals(il.getType()) && il.getGroupId() != null) {
                    out.addAll(nodeOrgResolver.listRoleCandidates(procInstId, il.getGroupId()));
                }
            }
        } catch (Exception ex) {
            log.warn("候选组解析失败（EXCLUDE 方案将降级）task={}: {}", task.getId(), ex.getMessage());
            return new ArrayList<>();
        }
        return new ArrayList<>(out);
    }

    // 组织数据

    /** 按 parent_id 向下展开 depth 层子孙部门 */
    private Set<Long> descendantDeptIds(Set<Long> roots, int depth) {
        Set<Long> out = new LinkedHashSet<>();
        List<SysDept> all = sysDeptMapper.selectList(new LambdaQueryWrapper<SysDept>());
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        for (SysDept d : all) {
            if (d.getParentId() != null && d.getId() != null) {
                childrenOf.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d.getId());
            }
        }
        Deque<Long> queue = new ArrayDeque<>(roots);
        Set<Long> seen = new HashSet<>(roots);
        for (int lv = 0; lv < depth && !queue.isEmpty(); lv++) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                Long cur = queue.poll();
                for (Long child : childrenOf.getOrDefault(cur, List.of())) {
                    if (seen.add(child)) {
                        out.add(child);
                        queue.add(child);
                    }
                }
            }
        }
        return out;
    }

    /** 发起人（单据提交人优先，取不到退流程实例的 start_user_id） */
    private Long resolveSubject(String procInstId) {
        if (procInstId == null) {
            return null;
        }
        try {
            Long applicant = nodeOrgResolver.resolveApplyUserId(procInstId);
            if (applicant != null) {
                return applicant;
            }
        } catch (Exception ex) {
            log.debug("提交人解析失败，退 start_user_id proc={}: {}", procInstId, ex.getMessage());
        }
        try {
            WfInstance inst = wfInstanceMapper.selectOne(new LambdaQueryWrapper<WfInstance>()
                    .eq(WfInstance::getProcInstId, procInstId).last("limit 1"));
            return inst == null ? null : inst.getStartUserId();
        } catch (Exception ex) {
            log.warn("流程实例查询失败 proc={}: {}", procInstId, ex.getMessage());
            return null;
        }
    }

    /** 在职真实账号过滤：NORMAL 且非 admin（is_admin=1 / user_name='admin' 一律排除，守"节点禁入 admin"铁律） */
    private List<Long> realUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> byId = new HashMap<>();
        try {
            for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
                byId.put(u.getId(), u);
            }
        } catch (Exception ex) {
            log.warn("参与人候选查询失败，本维度按空处理: {}", ex.getMessage());
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || out.contains(id)) {
                continue;
            }
            SysUser u = byId.get(id);
            if (u != null && "NORMAL".equals(u.getStatus())
                    && (u.getIsAdmin() == null || u.getIsAdmin() == 0)
                    && !"admin".equals(u.getUserName())) {
                out.add(id);
            }
        }
        return out;
    }

    // 条件表达式

    private static final Pattern PREDICATE = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+?)\\s*$");

    /**
     * 条件表达式求值（文法见 {@link #CONDITION_GRAMMAR}）。
     *
     * <p><b>为什么不做通用脚本求值</b>：配置表是运营可改的，把 SpEL/JS 之类通用求值器接上去，
     * 配置面就变成了代码执行面（可读环境、可调用任意静态方法）。这里刻意只支持"变量 比较 字面量"，
     * 语法错误或取不到的变量一律按"条件不满足"处理（降级到下一优先级），并留 warn —— 不抛异常，
     * 因为一个写坏的条件不应该把整条审批链断掉（真正需要断链的是"全不中"，那由 resolve 抛）。
     */
    private boolean conditionMatched(String expr, Map<String, Object> vars) {
        if (expr == null || expr.isBlank()) {
            return true;
        }
        try {
            for (String orGroup : expr.split("\\|\\|")) {
                boolean all = true;
                for (String pred : orGroup.split("&&")) {
                    if (pred.isBlank()) {
                        continue;
                    }
                    if (!predicate(pred, vars)) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("参与人方案条件表达式解析失败（按条件不满足处理，降级到下一优先级）: expr={} err={}",
                    expr, ex.getMessage());
            return false;
        }
    }

    private boolean predicate(String text, Map<String, Object> vars) {
        Matcher m = PREDICATE.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("无法识别的条件片段: " + text);
        }
        String key = m.group(1);
        String op = m.group(2);
        String raw = m.group(3);
        if (!vars.containsKey(key)) {
            return false;
        }
        Object left = vars.get(key);
        String right = (raw.length() >= 2 && raw.startsWith("'") && raw.endsWith("'"))
                ? raw.substring(1, raw.length() - 1) : raw;
        BigDecimal ln = number(left);
        BigDecimal rn = number(right);
        if (ln != null && rn != null) {
            int c = ln.compareTo(rn);
            return compare(c, op);
        }
        String ls = left == null ? "" : String.valueOf(left);
        int c = ls.compareTo(right);
        return compare(c, op);
    }

    private static boolean compare(int c, String op) {
        return switch (op) {
            case "==" -> c == 0;
            case "!=" -> c != 0;
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            default -> false;
        };
    }

    private static BigDecimal number(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // 小工具

    private Map<String, Object> variables(String procInstId) {
        if (procInstId == null || procInstId.isBlank()) {
            return Map.of();
        }
        try {
            return runtimeService.getVariables(procInstId);
        } catch (Exception ex) {
            log.warn("流程变量读取失败（条件表达式将取不到变量）proc={}: {}", procInstId, ex.getMessage());
            return Map.of();
        }
    }

    private static List<String> splitCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) {
            return out;
        }
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static List<Long> idList(String csv) {
        List<Long> out = new ArrayList<>();
        for (String t : splitCsv(csv)) {
            try {
                out.add(Long.parseLong(t));
            } catch (NumberFormatException ex) {
                log.warn("参与人维度取值不是数字ID，已跳过: {}", t);
            }
        }
        return out;
    }

    private static String stringVar(Map<String, Object> vars, String name) {
        if (name == null || name.isBlank() || vars == null || !vars.containsKey(name.trim())) {
            return null;
        }
        Object v = vars.get(name.trim());
        if (v == null) {
            return null;
        }
        if (v instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder();
            for (Object o : c) {
                if (o != null) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append(o);
                }
            }
            return sb.toString();
        }
        return String.valueOf(v);
    }

    private List<Map<String, Object>> userNames(List<Long> ids) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        Map<Long, SysUser> byId = new HashMap<>();
        try {
            for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
                byId.put(u.getId(), u);
            }
        } catch (Exception ex) {
            log.warn("预览用户名查询失败: {}", ex.getMessage());
        }
        for (Long id : ids) {
            SysUser u = byId.get(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", id);
            m.put("userName", u == null ? null
                    : (u.getNickName() == null || u.getNickName().isBlank() ? u.getUserName() : u.getNickName()));
            out.add(m);
        }
        return out;
    }
}
