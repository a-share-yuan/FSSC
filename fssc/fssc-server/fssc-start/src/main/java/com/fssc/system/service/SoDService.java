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
import lombok.RequiredArgsConstructor;
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
 *   <li>{@link #scanViolations} 存量合规巡检：按当前租户查出所有持有冲突角色组合的用户（演示全能审批人必然命中，属预期）。</li>
 * </ul>
 * 互斥规则来源 {@code sod_role_conflict}（平台级 tenant_id NULL + 租户自定义），全局表不受租户拦截器过滤，手工取规则。
 *
 * <p>R5-D-276（内控实况登记，数据与配置面）：现库 32 条规则下命中 7 对冲突组合／多角色用户 112 人，
 * 其中 FIN_GL × FSSC_AUDITOR（记账×审核，HIGH 语义）1 人；处置＝逐组出<b>人工裁决清单</b>
 * （撤兼任或书面豁免），本包<b>不改任何授权数据、不执行 DB 写</b>；呈现缺陷另见 R5-D-034。
 * 赋权入口已接线：UserService 授予角色前调 {@code assertNoUserPermConflict}（本行即该面登记）。</p>
 */
@Service
@RequiredArgsConstructor
public class SoDService {

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

    // R5-D-034：SoD 检查结果行（列契约与业务用语对齐，前端可直接渲染）
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

    // R5-D-034：权限码互斥规则的命中明细（保留角色出处，供结果列表标注"谁与谁"）
    private static class PermHit {
        private final SodPermConflict rule;
        private final List<Long> roleIdsA = new ArrayList<>();
        private final List<Long> roleIdsB = new ArrayList<>();

        private PermHit(SodPermConflict rule) {
            this.rule = rule;
        }
    }

    // R5-D-034：角色维度扫描中间态
    private static class RoleScan {
        private final Map<Long, Set<String>> codesByUser = new java.util.HashMap<>();
        private final Map<String, String> nameByCode = new java.util.HashMap<>();
    }

    private static final DateTimeFormatter SOD_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DIM_ROLE = "ROLE";
    private static final String DIM_PERM = "PERM";
    // R5-D-034：岗位互斥规则原文即"不得由同一人担任"，属硬约束，严重度恒记高
    private static final String SEVERITY_ROLE_CONFLICT = "高";

    /**
     * R5-D-034：执行职责分离检查（两维度一次跑完），返回 UI 就绪的结果行。
     * 结果不落库：sod_role_conflict / sod_perm_conflict 是【规则定义表】而非结果表，
     * 检查为无状态实时计算，检测时间取本次执行时刻。
     */
    public List<SodCheckRow> checkAll() {
        String detectTime = LocalDateTime.now().format(SOD_TS);
        List<SodCheckRow> rows = new ArrayList<>(roleConflictRows(detectTime));
        rows.addAll(permConflictRows(detectTime));
        return rows;
    }

    /** R5-D-034：检查结果分页（维度／关键词过滤在内存完成，总量恒为本租户口径） */
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
            if (dim != null && !dim.equals(r.getDimension())) {
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

    /** R5-D-034：角色维度＝一人兼两岗，逐（用户×命中规则）出行 */
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

    /** R5-D-034：权限码维度＝跨角色累积出的互斥操作权限，逐（用户×命中规则）出行 */
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
        Map<Long, String> out = new java.util.HashMap<>();
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
     * 只在【新增/变更授权】时拦截，存量演示绑定不受影响（本方法不改动 sys_user_role）。
     *
     * @param userId            被授权用户
     * @param roleIdsToAssign   本次要授予的角色ID集合
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
            roleIdsToAssign.stream().filter(java.util.Objects::nonNull).forEach(mergedRoleIds::add);
        }
        if (mergedRoleIds.size() < 2) {
            return;
        }
        // roleId -> 角色
        Map<Long, SysRole> roleById = roleMapper.selectBatchIds(mergedRoleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, Function.identity(), (x, y) -> x));
        Set<String> codes = roleById.values().stream()
                .map(SysRole::getRoleCode).filter(java.util.Objects::nonNull)
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
     * 授予入口（用户授权/用户组授权）必须先调本方法再落库。
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
     * 【用户级 SoD 存量巡检 · 权限码并集维度】按当前租户返回所有命中互斥规则的用户。
     * 与 {@link #assertNoUserPermConflict} 同口径：单角色自带互斥（inherent 既定岗位）不计入，
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
            roleIdsToAssign.stream().filter(java.util.Objects::nonNull).forEach(roleIds::add);
        }
        return roleIds;
    }

    /** 每个角色 -> 该角色自身权限码集合（sys_menu.perms 逗号拆分）；保留角色维度出处以判定 inherent */
    private Map<Long, Set<String>> permsByRole(Collection<Long> roleIds) {
        Map<Long, Set<String>> perRole = new java.util.LinkedHashMap<>();
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
     * <b>跨角色累积口径（2026-09，D-SOD-1 修复）</b>：某规则两侧虽在并集里都命中，但若存在
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

    /** R5-D-034：与 {@link #permConflictHits} 同判据，额外保留命中两侧的角色出处 */
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

    /** R5-D-034：规则集外提，逐用户巡检时同一份规则只取一次 */
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
     * 存量合规巡检（按当前租户）：查出所有持有冲突角色组合的用户。
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

    /** R5-D-034：角色维度扫描的公共走查（scanViolations 与 checkAll 同口径，避免两份判据） */
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
        Map<Long, String> codeByRoleId = new java.util.HashMap<>();
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
        Map<Long, SysUser> out = new java.util.HashMap<>();
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
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(code);
    }

    private String safeReason(SodRoleConflict rule) {
        return rule.getReason() == null || rule.getReason().isBlank() ? "职责不相容" : rule.getReason();
    }
}
