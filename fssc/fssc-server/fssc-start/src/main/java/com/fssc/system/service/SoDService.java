// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SodPermConflict;
import com.fssc.system.entity.SodRoleConflict;
import com.fssc.system.entity.SysRole;
import com.fssc.system.entity.SysUser;
import com.fssc.system.entity.SysUserRole;
import com.fssc.system.mapper.SodPermConflictMapper;
import com.fssc.system.mapper.SodRoleConflictMapper;
import com.fssc.system.mapper.SysGroupRoleMapper;
import com.fssc.system.mapper.SysMenuMapper;
import com.fssc.system.mapper.SysRoleMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.system.mapper.SysUserRoleMapper;
import java.util.HashMap;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SoD 不相容职务分离（Segregation of Duties）内控校验。
 * <ul>
 *   <li>{@link #assertNoConflict} 角色授予前置校验：把【新角色 ∪ 用户已有角色】合并后逐对比对互斥表，命中即抛异常。</li>
 *   <li>{@link #scanViolations} 历史合规巡检：按当前租户查出所有持有冲突角色组合的用户（演示全能审批人必然命中，属预期）。</li>
 * </ul>
 * 互斥规则来源 {@code sod_role_conflict}（平台级 tenant_id NULL + 租户自定义），全局表不受租户拦截器过滤，手工取规则。
 *
 * <p>（内控实况登记，数据与配置面）：现库 32 条规则下命中 7 对冲突组合／多角色用户 112 人，
 * 其中 FIN_GL × FSSC_AUDITOR（记账×审核，HIGH 语义）1 人；处置＝逐组出<b>待确认清单</b>
 * 撤兼任或书面豁免，本包<b>不改任何授权数据、不执行 DB 写</b>；呈现缺陷另见。
 * 赋权入口已接线：UserService 授予角色前调 {@code assertNoUserPermConflict}（本行即该面登记）。</p>
 */
@Service
@RequiredArgsConstructor
public class SoDService {

    /** C-3：在役不相容职责（L2）需要跨表读流程历史，直接走 JDBC 避开实体映射。 */
    private final JdbcTemplate jdbc;


    private final SodRoleConflictMapper conflictMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SodPermConflictMapper permConflictMapper;
    private final SysGroupRoleMapper groupRoleMapper;
    private final SysMenuMapper menuMapper;

    /** 单条冲突命中结果 */
    public static class SodViolation {
        private Long userId;
        private String userName;
        private String empNo;
        private List<String> conflicts;

        public SodViolation() {
        }

        public SodViolation(Long userId, String userName, String empNo, List<String> conflicts) {
            this.userId = userId;
            this.userName = userName;
            this.empNo = empNo;
            this.conflicts = conflicts;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getEmpNo() { return empNo; }
        public void setEmpNo(String empNo) { this.empNo = empNo; }
        public List<String> getConflicts() { return conflicts; }
        public void setConflicts(List<String> conflicts) { this.conflicts = conflicts; }
    }

    // SoD 检查结果行（列契约与业务用语对齐，前端可直接渲染）
    public static class SodCheckRow {
        private Long userId;
        private String userName;
        private String empNo;
        private String roleName;
        private String conflictDesc;
        private String severity;
        private String dimension;
        private String ruleCode;
        private String detectTime;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getEmpNo() { return empNo; }
        public void setEmpNo(String empNo) { this.empNo = empNo; }
        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public String getConflictDesc() { return conflictDesc; }
        public void setConflictDesc(String conflictDesc) { this.conflictDesc = conflictDesc; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getDimension() { return dimension; }
        public void setDimension(String dimension) { this.dimension = dimension; }
        public String getRuleCode() { return ruleCode; }
        public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
        public String getDetectTime() { return detectTime; }
        public void setDetectTime(String detectTime) { this.detectTime = detectTime; }
    }

    // 权限码互斥规则的命中明细（保留角色出处，供结果列表标注"谁与谁"）
    private static class PermHit {
        private final SodPermConflict rule;
        private final List<Long> roleIdsA = new ArrayList<>();
        private final List<Long> roleIdsB = new ArrayList<>();

        private PermHit(SodPermConflict rule) {
            this.rule = rule;
        }
    }

    // 角色维度扫描中间态
    private static class RoleScan {
        private final Map<Long, Set<String>> codesByUser = new HashMap<>();
        private final Map<String, String> nameByCode = new HashMap<>();
    }

    private static final DateTimeFormatter SOD_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DIM_ROLE = "ROLE";
    private static final String DIM_PERM = "PERM";
    /**
     * DUTY 细分：原先把「首节点自办」与「真自审」混成一个 DUTY 维度，
     * 导致守卫按设计豁免的正常提交被报成冲突。
     * 现按守卫口径拆两支——SELF 才有风险，SERIAL 是连办。
     */
    private static final String DIM_DUTY_SELF = "DUTY_SELF";
    private static final String DIM_DUTY_SERIAL = "DUTY_SERIAL";
    // 岗位互斥规则原文即"不得由同一人担任"，属硬约束，严重度恒记高
    private static final String SEVERITY_ROLE_CONFLICT = "高";

    /**
     * 执行职责分离检查（两维度一次跑完），返回 UI 就绪的结果行。
     * 结果不写入数据库：sod_role_conflict / sod_perm_conflict 是【规则定义表】而非结果表，
     * 检查为无状态实时计算，检测时间取本次执行时刻。
     */
    public List<SodCheckRow> checkAll() {
        String detectTime = LocalDateTime.now().format(SOD_TS);
        List<SodCheckRow> rows = new ArrayList<>(roleConflictRows(detectTime));
        rows.addAll(permConflictRows(detectTime));
        // ── C-3：把「在役的不相容职责校验」纳入 SoD 报告 ──
        // SoD 分三层：L1 互斥岗（本类已有的 role/perm 冲突表）、
        // L2 不相容职责（同一单据内经办轨迹）、L3 轮岗。
        // 本系统真正在拦 L2 的是两个校验——approve.deny_self（自审）与
        // approve.deny_serial_same_handler（同人连办），它们若不在 SoD 报告里，
        // 导致「报告 0 冲突」与「实际有单被拦」两套规则打架。
        // 这里按「在途实例上的真实经办轨迹」反查，把校验已拦/将拦的情形显式列出。
        rows.addAll(activeDutySeparationRows(detectTime));
        return rows;
    }

    /**
     * C-3：在役「不相容职责」（L2）实况——从流程历史反查真实经办轨迹。
     *
     * <p>判定规则（与 {@code WfEngineFacade} 的两道校验完全一致，不另立标准）：
     * <ul>
     *   <li><b>自审</b>：同一流程实例内，某人既是<b>发起人</b>又<b>办过至少一个节点</b>；</li>
     *   <li><b>同人连办</b>：同一流程实例内，某人办过 <b>≥2 个不同 taskDefinitionKey</b> 的节点。</li>
     * </ul>
     * 两者均为「已实际发生」的历史轨迹（引擎当时放行或事后补办），
     * 与配置层的 role/perm 冲突表互为补充：表是「可能冲突」，这里是「真的发生过」。
     *
     * <p>只读查询，不改任何流程数据。
     */
    private List<SodCheckRow> activeDutySeparationRows(String detectTime) {
        return scanDuty(detectTime).rows;
    }

    /** DUTY 扫描结果：冲突行 + 被豁免的「首节点自办」条数（不计入冲突，仅供分层展示） */
    private static class DutyScan {
        private final List<SodCheckRow> rows = new ArrayList<>();
        private long exemptedFirstNode;
    }

    private DutyScan scanDuty(String detectTime) {
        DutyScan out = new DutyScan();
        try {
            // 同一实例内「人 → 不同节点 key 集合」
            Map<String, Map<Long, Set<String>>> byInstance = new LinkedHashMap<>();
            // 同一实例「最先出现」的节点 key ＝ 首节点（SQL 按 START_TIME_ 排序，putIfAbsent 取第一条）
            Map<String, String> firstNodeKeyByInstance = new HashMap<>();
            // 同一实例内的发起人
            Map<String, Long> starterByInstance = new HashMap<>();
            // 实例 → model_key（判定 POOL 需要，act_hi_taskinst 本身不带模型）
            Map<String, String> instModel = new HashMap<>();
            // POOL 节点集合（model#node）：守卫对 POOL 不走 deny_self，SoD 侧也必须同步豁免
            java.util.Set<String> poolKeys = new java.util.HashSet<>();
            // 不要再 JOIN act_ru_execution：它是 Flowable 的「运行中执行」表，
            // 流程一结束就清空，而历史轨迹全在 act_hi_taskinst。
            // 直接用 act_hi_taskinst 自带的 PROC_INST_ID_ 分组即可。
            for (Map<String, Object> h : jdbc.queryForList(
                    "/* tenant-ok: proc_inst_id/start_user_id 全局唯一，租户内调用 */ SELECT PROC_INST_ID_ AS inst, ASSIGNEE_ AS uid, TASK_DEF_KEY_ AS dk "
                            + "FROM act_hi_taskinst "
                            + "WHERE ASSIGNEE_ IS NOT NULL AND TASK_DEF_KEY_ IS NOT NULL "
                            + "ORDER BY PROC_INST_ID_, START_TIME_")) {
                // JdbcTemplate 返回的列名按驱动/库大小写不定（MySQL 会返回大写 INST/UID/DK），
                //   直接 get("inst") 会拿到 null ⇒ 全部数据聚到 "null" 键上，判据永远不命中。
                String inst = String.valueOf(col(h, "inst"));
                long uid = asLong(col(h, "uid"));
                String dk = String.valueOf(col(h, "dk"));
                byInstance.computeIfAbsent(inst, k -> new LinkedHashMap<>())
                        .computeIfAbsent(uid, k -> new LinkedHashSet<>()).add(dk);
                firstNodeKeyByInstance.putIfAbsent(inst, dk);
            }
            // wf_instance 没有 del_flag 列（流程实例表靠 status 表达最终状态，不做逻辑删除），
            //   带上它会直接 Unknown column 抛错、被外层 catch 吞掉 → 本项永远 0 条。
            for (Map<String, Object> w : jdbc.queryForList(
                    "/* tenant-ok: proc_inst_id/start_user_id 全局唯一，租户内调用 */ SELECT proc_inst_id AS inst, start_user_id AS uid, model_key AS mk FROM wf_instance")) {
                String wi = String.valueOf(col(w, "inst"));
                String mk = col(w, "mk") == null ? null : String.valueOf(col(w, "mk"));
                if (mk != null) {
                    instModel.put(wi, mk);
                }
                Object su = col(w, "uid");
                if (su != null) {
                    starterByInstance.put(wi, asLong(su));
                }
            }
            // POOL 节点清单：守卫 complete() 里 `isDenySelfEnabled() && !isPoolTask(task)`，
            // 即 POOL 作业池根本不走自审校验 —— SoD 若不算这一条，会把「发起人在池里领取执行」
            // 误报成自审（实际存在多条误报）。
            for (Map<String, Object> r : jdbc.queryForList(
                    "/* tenant-ok: 配置表全局，租户内读取 */ SELECT model_key AS mk, node_key AS nk FROM wf_node_config "
                            + "WHERE del_flag = 0 AND UPPER(handling_mode) = 'POOL'")) {
                Object mk = col(r, "mk");
                Object nk = col(r, "nk");
                if (mk != null && nk != null) {
                    poolKeys.add(mk + "#" + nk);
                }
            }
            for (Map.Entry<String, Map<Long, Set<String>>> e : byInstance.entrySet()) {
                String inst = e.getKey();
                Long starter = starterByInstance.get(inst);
                String firstDk = firstNodeKeyByInstance.get(inst);
                for (Map.Entry<Long, Set<String>> per : e.getValue().entrySet()) {
                    long uid = per.getKey();
                    Set<String> keys = per.getValue();
                    // 与守卫 WfEngineFacade.complete() 同口径，两条豁免都要还原：
                    //   ① doneTasks > 0 才拦 ⇒ 只办首节点的不算自审；
                    //   ② POOL 作业池不走 deny_self（代码 `!isPoolTask(task)`）⇒ POOL 节点不算自审。
                    String mk = instModel.get(inst);
                    boolean hasNonFirstNonPool = firstDk != null && keys.stream().anyMatch(k ->
                            !k.equals(firstDk) && !poolKeys.contains(mk + "#" + k));
                    if (starter != null && starter == uid) {
                        if (hasNonFirstNonPool) {
                            out.rows.add(dutyRow(uid, "发起人在已有完成节点后仍办理审批（非首节点自审）",
                                    "L2-不相容职责/自审（approve.deny_self）", detectTime, DIM_DUTY_SELF));
                        } else {
                            // 首节点自办＝提交即办结，守卫豁免；单列计数，不进冲突数
                            out.exemptedFirstNode++;
                        }
                    }
                    // 同人连办：办过 ≥2 个不同节点
                    if (keys.size() >= 2) {
                        out.rows.add(dutyRow(uid, "同一流程内办过 " + keys.size() + " 个不同节点（" + keys + "）",
                                "L2-不相容职责/连办（approve.deny_serial_same_handler 作用域内）", detectTime, DIM_DUTY_SERIAL));
                    }
                }
            }
        } catch (Exception ex) {
            // fail-open：体检类查询异常不得让整个 SoD 报告失败；但必须记录，否则"永远 0 条"无从排查
            LoggerFactory.getLogger(SoDService.class)
                    .warn("SoD 不相容职责(L2)实况查询失败（本次该项返回空，不影响其它维度）: {}", ex.toString());
            return out;
        }
        return out;
    }

    /**
     * 分层计数：把三类分开给前端，不再用一个 total 混算。
     * <ul>
     *   <li>{@code staticConflicts} = ROLE + PERM，配置层冲突，&gt;0 即需整改；</li>
     *   <li>{@code runtimeSelfAudit} = DUTY_SELF，真·非首节点自审，应恒为 0；</li>
     *   <li>{@code runtimeSerial} = DUTY_SERIAL，同人连办，需配置治理或裁决；</li>
     *   <li>{@code exemptedFirstNode} = 首节点自办，守卫豁免，仅作提示不计数。</li>
     * </ul>
     */
    public Map<String, Object> summarize() {
        String detectTime = LocalDateTime.now().format(SOD_TS);
        List<SodCheckRow> staticRows = new ArrayList<>(roleConflictRows(detectTime));
        staticRows.addAll(permConflictRows(detectTime));
        DutyScan duty = scanDuty(detectTime);
        long role = staticRows.stream().filter(r -> DIM_ROLE.equals(r.getDimension())).count();
        long perm = staticRows.stream().filter(r -> DIM_PERM.equals(r.getDimension())).count();
        long self = duty.rows.stream().filter(r -> DIM_DUTY_SELF.equals(r.getDimension())).count();
        long serial = duty.rows.stream().filter(r -> DIM_DUTY_SERIAL.equals(r.getDimension())).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roleConflicts", role);
        m.put("permConflicts", perm);
        m.put("staticConflicts", role + perm);
        m.put("runtimeSelfAudit", self);
        m.put("runtimeSerial", serial);
        m.put("exemptedFirstNode", duty.exemptedFirstNode);
        m.put("total", role + perm + self + serial);
        m.put("detectTime", detectTime);
        return m;
    }

    /**
     * 安全转long：{@code act_hi_taskinst.ASSIGNEE_} 是 varchar，驱动可能返回 String，
     * 直接 {@code (Number)} 强转会 ClassCastException ⇒ 整段被外层 catch 吞掉、永远 0 条。
     */
    private static long asLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? 0L : Long.parseLong(s);
    }

    /** JdbcTemplate 取值：列名大小写不敏感（MySQL 常返回大写，H2/PG 返回小写）。 */
    private static Object col(Map<String, Object> row, String name) {
        Object v = row.get(name);
        if (v == null) {
            v = row.get(name.toUpperCase());
        }
        if (v == null) {
            v = row.get(name.toLowerCase());
        }
        return v;
    }

    private SodCheckRow dutyRow(long uid, String conflictDesc, String roleName, String detectTime, String dimension) {
        SodCheckRow r = new SodCheckRow();
        r.setUserId(uid);
        SysUser u = userMapper.selectById(uid);
        if (u != null) {
            r.setUserName(u.getNickName());
            r.setEmpNo(u.getEmpNo());
        }
        r.setRoleName(roleName);
        r.setConflictDesc(conflictDesc);
        r.setDetectTime(detectTime);
        r.setDimension(dimension);
        return r;
    }

    /** 检查结果分页（维度／关键词过滤在内存完成，总量恒为本租户规则） */
    public PageResult<SodCheckRow> checkPage(String dimension, String keyword, long pageNum, long pageSize) {
        long size = pageSize <= 0 ? 20L : Math.min(pageSize, 500L);
        long page = pageNum <= 0 ? 1L : pageNum;
        List<SodCheckRow> all = filterCheckRows(checkAll(), dimension, keyword);
        int from = (int) Math.min((page - 1) * size, all.size());
        int to = (int) Math.min(from + size, all.size());
        return PageResult.of(new ArrayList<>(all.subList(from, to)), all.size(), page, size);
    }

    private List<SodCheckRow> filterCheckRows(List<SodCheckRow> rows, String dimension, String keyword) {
        String dim = dimension == null || dimension.isBlank() || "ALL".equalsIgnoreCase(dimension)
                ? null : dimension.trim().toUpperCase();
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase();
        List<SodCheckRow> out = new ArrayList<>();
        for (SodCheckRow r : rows) {
            // DUTY 已细分为 DUTY_SELF / DUTY_SERIAL：筛选 "DUTY" 时按前缀同时命中两支，
            // 避免前端「运行时轨迹」页签因为精确等值匹配而查不到行。
            boolean dimHit = dim == null || dim.equals(r.getDimension())
                    || ("DUTY".equals(dim) && r.getDimension() != null && r.getDimension().startsWith("DUTY"));
            if (!dimHit) {
                continue;
            }
            if (kw != null && !containsAny(kw, r.getUserName(), r.getEmpNo(), r.getRoleName(),
                    r.getConflictDesc(), r.getRuleCode())) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    private boolean containsAny(String kw, String... values) {
        for (String v : values) {
            if (v != null && v.toLowerCase().contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /** 角色维度＝一人兼两岗，逐（用户×命中规则）出行 */
    private List<SodCheckRow> roleConflictRows(String detectTime) {
        List<SodRoleConflict> rules = loadActiveRules();
        List<SodCheckRow> rows = new ArrayList<>();
        if (rules.isEmpty()) {
            return rows;
        }
        RoleScan scan = scanRoleBindings(rules);
        if (scan.codesByUser.isEmpty()) {
            return rows;
        }
        Map<Long, List<SodRoleConflict>> hitsByUser = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<String>> e : scan.codesByUser.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }
            List<SodRoleConflict> matched = new ArrayList<>();
            for (SodRoleConflict r : rules) {
                if (e.getValue().contains(r.getRoleCodeA()) && e.getValue().contains(r.getRoleCodeB())) {
                    matched.add(r);
                }
            }
            if (!matched.isEmpty()) {
                hitsByUser.put(e.getKey(), matched);
            }
        }
        Map<Long, SysUser> userById = loadUsers(hitsByUser.keySet());
        for (Map.Entry<Long, List<SodRoleConflict>> e : hitsByUser.entrySet()) {
            SysUser u = userById.get(e.getKey());
            for (SodRoleConflict r : e.getValue()) {
                SodCheckRow row = new SodCheckRow();
                row.setUserId(e.getKey());
                row.setUserName(u == null ? null : u.getNickName());
                row.setEmpNo(u == null ? null : u.getEmpNo());
                row.setRoleName(roleLabel(scan, r.getRoleCodeA()) + " × " + roleLabel(scan, r.getRoleCodeB()));
                row.setConflictDesc(safeReason(r));
                row.setSeverity(SEVERITY_ROLE_CONFLICT);
                row.setDimension(DIM_ROLE);
                row.setDetectTime(detectTime);
                rows.add(row);
            }
        }
        return rows;
    }

    /** 权限码维度＝跨角色累积出的互斥操作权限，逐（用户×命中规则）出行 */
    private List<SodCheckRow> permConflictRows(String detectTime) {
        List<SodCheckRow> rows = new ArrayList<>();
        List<SodPermConflict> rules = loadActivePermRules();
        for (SysUser u : userMapper.selectList(new LambdaQueryWrapper<>())) {
            Set<Long> roleIds = collectEffectiveRoleIds(u.getId(), null);
            Map<Long, Set<String>> perRole = permsByRole(roleIds);
            List<PermHit> hits = permConflictMatches(perRole, rules);
            if (hits.isEmpty()) {
                continue;
            }
            Map<Long, String> nameByRoleId = roleNameByRoleId(roleIds);
            for (PermHit h : hits) {
                SodCheckRow row = new SodCheckRow();
                row.setUserId(u.getId());
                row.setUserName(u.getNickName());
                row.setEmpNo(u.getEmpNo());
                row.setRoleName(joinRoleNames(nameByRoleId, h.roleIdsA) + " × "
                        + joinRoleNames(nameByRoleId, h.roleIdsB));
                row.setConflictDesc(safeName(h.rule) + "（" + safeNote(h.rule) + "）");
                row.setSeverity(h.rule.getRisk() == null || h.rule.getRisk().isBlank() ? "中" : h.rule.getRisk());
                row.setDimension(DIM_PERM);
                row.setRuleCode(h.rule.getRuleCode());
                row.setDetectTime(detectTime);
                rows.add(row);
            }
        }
        return rows;
    }

    /** 当前租户内的角色名（sys_role 受租户拦截器过滤） */
    private Map<Long, String> roleNameByRoleId(Collection<Long> roleIds) {
        Map<Long, String> out = new HashMap<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return out;
        }
        for (SysRole r : roleMapper.selectBatchIds(roleIds)) {
            out.put(r.getId(), r.getRoleName() == null || r.getRoleName().isBlank() ? r.getRoleCode() : r.getRoleName());
        }
        return out;
    }

    private String joinRoleNames(Map<Long, String> nameByRoleId, List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return "跨岗位累积";
        }
        return roleIds.stream().map(id -> nameByRoleId.getOrDefault(id, "已授权岗位")).distinct()
                .collect(Collectors.joining("、"));
    }

    private String roleLabel(RoleScan scan, String roleCode) {
        return scan.nameByCode.getOrDefault(roleCode, roleCode);
    }

    /**
     * 角色授予前置校验：合并新角色与用户已有角色后逐对检查互斥表，命中抛 {@link BizException}。
     * 只在时拦截，历史演示绑定不受影响（本方法不改动 sys_user_role）。
     *
     * @param userId 被授权用户
     * @param roleIdsToAssign 本次要授予的角色ID集合
     */
    public void assertNoConflict(Long userId, Collection<Long> roleIdsToAssign) {
        List<SodRoleConflict> rules = loadActiveRules();
        if (rules.isEmpty()) {
            return;
        }
        // 合并：用户已有角色 + 本次新增角色（覆盖式授权后最终角色集可能小于合并集，但按内控“逐对检查”从严合并）
        Set<Long> mergedRoleIds = new HashSet<>();
        if (userId != null) {
            mergedRoleIds.addAll(userRoleMapper.selectRoleIdsByUser(userId));
        }
        if (roleIdsToAssign != null) {
            roleIdsToAssign.stream().filter(Objects::nonNull).forEach(mergedRoleIds::add);
        }
        if (mergedRoleIds.size() < 2) {
            return;
        }
        // roleId -> 角色
        Map<Long, SysRole> roleById = roleMapper.selectBatchIds(mergedRoleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, Function.identity(), (x, y) -> x));
        Set<String> codes = roleById.values().stream()
                .map(SysRole::getRoleCode).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 逐对检查
        List<String> hits = new ArrayList<>();
        for (SodRoleConflict rule : rules) {
            if (codes.contains(rule.getRoleCodeA()) && codes.contains(rule.getRoleCodeB())) {
                String nameA = roleName(roleById, rule.getRoleCodeA());
                String nameB = roleName(roleById, rule.getRoleCodeB());
                hits.add(nameA + "与" + nameB + "不得由同一人担任（" + safeReason(rule) + "）");
            }
        }
        if (!hits.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "不相容职务分离校验不通过：" + String.join("；", hits));
        }
    }

    /**
     * 【用户级 SoD 实时校验 · 权限码并集维度】把【直接角色 ∪ 用户组派生角色 ∪ 本次新增角色】
     * 的权限码合并后，逐条比对 {@code sod_perm_conflict} 的正则互斥规则，命中即抛异常。
     * 与 {@link #assertNoConflict}（角色码两两互斥）互补：能捕获"单看每个角色都合法、
     * 组合起来违规"的隐蔽场景（如 出纳角色+总账角色的权限码并集）。
     * 授予入口（用户授权/用户组授权）必须先调本方法再写入数据库。
     */
    public void assertNoUserPermConflict(Long userId, Collection<Long> roleIdsToAssign) {
        Set<Long> roleIds = collectEffectiveRoleIds(userId, roleIdsToAssign);
        Map<Long, Set<String>> perRole = permsByRole(roleIds);
        List<String> hits = permConflictHits(perRole);
        if (!hits.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "不相容职务分离校验不通过（跨角色权限组合）：" + String.join("；", hits));
        }
    }

    /**
     * 【用户级 SoD 历史巡检 · 权限码并集维度】按当前租户返回所有命中互斥规则的用户。
     * 与 {@link #assertNoUserPermConflict} 同一规则：单角色自带互斥（inherent 既定岗位）不计入，
     * 只有跨角色累积出的互斥组合才算违规，供定期复核（recertification）与 CI 巡检消费。
     */
    public List<SodViolation> scanPermViolations() {
        List<SodViolation> result = new ArrayList<>();
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<>());
        for (SysUser u : users) {
            Map<Long, Set<String>> perRole = permsByRole(collectEffectiveRoleIds(u.getId(), null));
            List<String> hits = permConflictHits(perRole);
            if (!hits.isEmpty()) {
                result.add(new SodViolation(u.getId(), u.getNickName(), u.getEmpNo(), hits));
            }
        }
        return result;
    }

    /** 生效角色ID集合：直接角色 ∪ 用户组派生角色（∪ 本次待授予角色） */
    private Set<Long> collectEffectiveRoleIds(Long userId, Collection<Long> roleIdsToAssign) {
        Set<Long> roleIds = new HashSet<>();
        if (userId != null) {
            roleIds.addAll(userRoleMapper.selectRoleIdsByUser(userId));
            roleIds.addAll(groupRoleMapper.selectRoleIdsByUserId(userId));
        }
        if (roleIdsToAssign != null) {
            roleIdsToAssign.stream().filter(Objects::nonNull).forEach(roleIds::add);
        }
        return roleIds;
    }

    /** 每个角色 -> 该角色自身权限码集合（sys_menu.perms 逗号拆分）；保留角色维度出处以判定 inherent */
    private Map<Long, Set<String>> permsByRole(Collection<Long> roleIds) {
        Map<Long, Set<String>> perRole = new LinkedHashMap<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return perRole;
        }
        for (Long roleId : roleIds) {
            Set<String> perms = new HashSet<>();
            for (String p : menuMapper.selectPermsByRoleIds(List.of(roleId))) {
                for (String c : String.valueOf(p).split(",")) {
                    if (!c.isBlank()) {
                        perms.add(c.trim());
                    }
                }
            }
            perRole.put(roleId, perms);
        }
        return perRole;
    }

    /** 并集（用于跨角色累积判定），各角色集合摊平 */
    private Set<String> flatten(Collection<Set<String>> sets) {
        Set<String> out = new HashSet<>();
        for (Set<String> s : sets) {
            out.addAll(s);
        }
        return out;
    }

    /**
     * 平台级 + 当前租户的 ACTIVE 规则逐条正则比对，返回命中描述。
     * 跨角色累积规则：某规则两侧虽在并集里都命中，但若存在
     * <b>单个角色</b>自身就同时含两侧权限（inherent，属该角色定义的既定岗位职责），则不计为违规——
     * 一用户一角色下这类角色必须可授予；仅当互斥组合<b>只能由 ≥2 个角色合并</b>产生时才拦截。
     */
    private List<String> permConflictHits(Map<Long, Set<String>> perRole) {
        List<String> hits = new ArrayList<>();
        for (PermHit h : permConflictMatches(perRole)) {
            hits.add(h.rule.getRuleCode() + " " + safeName(h.rule) + "（" + safeNote(h.rule) + "）");
        }
        return hits;
    }

    /** 与 {@link #permConflictHits} 同判据，额外保留命中两侧的角色出处 */
    private List<PermHit> permConflictMatches(Map<Long, Set<String>> perRole) {
        return permConflictMatches(perRole, loadActivePermRules());
    }

    /** 平台级（tenant_id NULL）+ 当前租户的 ACTIVE 权限码互斥规则 */
    private List<SodPermConflict> loadActivePermRules() {
        Long tenantId = TenantContext.getTenantId();
        return permConflictMapper.selectList(new LambdaQueryWrapper<SodPermConflict>()
                .eq(SodPermConflict::getStatus, "ACTIVE")
                .and(w -> w.isNull(SodPermConflict::getTenantId)
                        .or().eq(SodPermConflict::getTenantId, tenantId)));
    }

    /** 规则集外提，逐用户巡检时同一份规则只取一次 */
    private List<PermHit> permConflictMatches(Map<Long, Set<String>> perRole, List<SodPermConflict> rules) {
        List<PermHit> hits = new ArrayList<>();
        if (perRole == null || perRole.isEmpty()) {
            return hits;
        }
        Set<String> union = flatten(perRole.values());
        for (SodPermConflict r : rules) {
            if (matchAll(union, r.getPatternA()).isEmpty() || matchAll(union, r.getPatternB()).isEmpty()) {
                continue;
            }
            if (inherentInAnyRole(perRole.values(), r.getPatternA(), r.getPatternB())) {
                continue;
            }
            PermHit hit = new PermHit(r);
            for (Map.Entry<Long, Set<String>> e : perRole.entrySet()) {
                if (!matchAll(e.getValue(), r.getPatternA()).isEmpty()) {
                    hit.roleIdsA.add(e.getKey());
                }
                if (!matchAll(e.getValue(), r.getPatternB()).isEmpty()) {
                    hit.roleIdsB.add(e.getKey());
                }
            }
            hits.add(hit);
        }
        return hits;
    }

    /** 是否存在单个角色自身即同时命中规则两侧（inherent 既定岗位，不计跨角色违规） */
    private boolean inherentInAnyRole(Collection<Set<String>> perRoleSets, String patternA, String patternB) {
        for (Set<String> perms : perRoleSets) {
            if (!matchAll(perms, patternA).isEmpty() && !matchAll(perms, patternB).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<String> matchAll(Set<String> perms, String pattern) {
        List<String> out = new ArrayList<>();
        if (pattern == null || pattern.isBlank()) {
            return out;
        }
        try {
            Pattern p = Pattern.compile(pattern);
            for (String c : perms) {
                if (p.matcher(c).matches()) {
                    out.add(c);
                }
            }
        } catch (Exception ignore) {
            // 非法正则按"不命中"处理，避免规则错误阻断全部授权
        }
        return out;
    }

    private String safeName(SodPermConflict r) {
        return r.getRuleName() == null || r.getRuleName().isBlank() ? r.getRuleCode() : r.getRuleName();
    }

    private String safeNote(SodPermConflict r) {
        return r.getNote() == null || r.getNote().isBlank() ? "职责不相容" : r.getNote();
    }

    /**
     * 历史合规巡检（按当前租户）：查出所有持有冲突角色组合的用户。
     * 演示全能审批人（user_id=1 持有全部角色）必然命中，属预期，不排除。
     */
    public List<SodViolation> scanViolations() {
        List<SodViolation> result = new ArrayList<>();
        List<SodRoleConflict> rules = loadActiveRules();
        if (rules.isEmpty()) {
            return result;
        }
        // 参与互斥的角色编码 / 当前租户内的角色 / 用户持有的互斥角色编码（sys_role、sys_user_role 均租户过滤）
        RoleScan scan = scanRoleBindings(rules);
        if (scan.codesByUser.isEmpty()) {
            return result;
        }
        // 命中互斥对的用户
        Map<Long, List<String>> conflictsByUser = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<String>> e : scan.codesByUser.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }
            List<String> hits = new ArrayList<>();
            for (SodRoleConflict r : rules) {
                if (e.getValue().contains(r.getRoleCodeA()) && e.getValue().contains(r.getRoleCodeB())) {
                    hits.add(r.getRoleCodeA() + "×" + r.getRoleCodeB());
                }
            }
            if (!hits.isEmpty()) {
                conflictsByUser.put(e.getKey(), hits);
            }
        }
        if (conflictsByUser.isEmpty()) {
            return result;
        }
        // 补齐用户姓名/工号（sys_user 租户过滤）
        Map<Long, SysUser> userById = loadUsers(conflictsByUser.keySet());
        for (Map.Entry<Long, List<String>> e : conflictsByUser.entrySet()) {
            SysUser u = userById.get(e.getKey());
            result.add(new SodViolation(e.getKey(),
                    u == null ? null : u.getNickName(),
                    u == null ? null : u.getEmpNo(),
                    e.getValue()));
        }
        return result;
    }

    /** 角色维度扫描的公共走查（scanViolations 与 checkAll 同一规则，避免两份判据） */
    private RoleScan scanRoleBindings(List<SodRoleConflict> rules) {
        RoleScan scan = new RoleScan();
        Set<String> conflictCodes = new HashSet<>();
        for (SodRoleConflict r : rules) {
            conflictCodes.add(r.getRoleCodeA());
            conflictCodes.add(r.getRoleCodeB());
        }
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getRoleCode, conflictCodes));
        if (roles.isEmpty()) {
            return scan;
        }
        Map<Long, String> codeByRoleId = new HashMap<>();
        for (SysRole r : roles) {
            codeByRoleId.put(r.getId(), r.getRoleCode());
            scan.nameByCode.put(r.getRoleCode(),
                    r.getRoleName() == null || r.getRoleName().isBlank() ? r.getRoleCode() : r.getRoleName());
        }
        List<SysUserRole> bindings = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getRoleId, codeByRoleId.keySet()));
        for (SysUserRole b : bindings) {
            String code = codeByRoleId.get(b.getRoleId());
            if (code != null) {
                scan.codesByUser.computeIfAbsent(b.getUserId(), k -> new LinkedHashSet<>()).add(code);
            }
        }
        return scan;
    }

    private Map<Long, SysUser> loadUsers(Collection<Long> userIds) {
        Map<Long, SysUser> out = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (SysUser u : userMapper.selectBatchIds(userIds)) {
            out.put(u.getId(), u);
        }
        return out;
    }

    /** 当前生效的互斥规则：平台级（tenant_id NULL）+ 当前租户自定义 */
    private List<SodRoleConflict> loadActiveRules() {
        Long tenantId = TenantContext.getTenantId();
        return conflictMapper.selectList(new LambdaQueryWrapper<SodRoleConflict>()
                .and(w -> w.isNull(SodRoleConflict::getTenantId)
                        .or().eq(SodRoleConflict::getTenantId, tenantId)));
    }

    private String roleName(Map<Long, SysRole> roleById, String code) {
        return roleById.values().stream()
                .filter(r -> code != null && code.equals(r.getRoleCode()))
                .map(SysRole::getRoleName)
                .filter(Objects::nonNull)
                .findFirst().orElse(code);
    }

    private String safeReason(SodRoleConflict rule) {
        return rule.getReason() == null || rule.getReason().isBlank() ? "职责不相容" : rule.getReason();
    }
}
