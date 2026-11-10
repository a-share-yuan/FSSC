// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织健康体检（OrgHealthChecker）——把「上线前必须固化的 4 条红线」<b>变成可自动执行的校验</b>。
 *
 * <h3>为什么要有这个类</h3>
 * 红线条目原先只是文档里的「注意事项」，没人真的去查，于是：
 * <b>绝大多数公司不满足红线1</b>（单位负责人不足 2 人）、
 * <b>红线2 同样断裂</b>（部门树全是 parent_id=0，汇报线断在第一层）。
 * 靠人工逐公司补人/补部门树不可持续——<b>每新增一家公司都会重犯</b>。
 * ⇒ 只能做成<b>每日自动巡检 + 可查询的体检报告</b>。
 *
 * <h3>五条红线（判据规则办结在此，一处定义）</h3>
 * <ol>
 *   <li><b>R1 岗位独苗</b>：凡有 {@code assign_value='DIRECT_SUPERVISOR'} 节点的流程，
 *       所在公司的 {@code ORG_HEAD} 人数须 ≥ 2 —— 否则「leader 恰为提单人」时节点无办理人、必然死锁。
 *       推广规则：每个公司只要配了 leader_id，就至少要有 2 名负责人。</li>
 *   <li><b>R2 汇报线断裂</b>：公司内部门<b>不得全部 {@code parent_id=0}</b> ——
 *       断在第一层时 {@code escalateTarget} 与 {@code empty_strategy=ESCALATE} 全部失效。</li>
 *   <li><b>R3 leader 即提单人</b>：某部门 leader 若同时是该部门主要提单人，
 *       他本人发起的单在该节点必然无人可办（本项目已由 三级降级兜底，
 *       但组织上仍应避免，属<b>提醒级</b>）。</li>
 *   <li><b>R4 leader 跨公司</b>：{@code sys_dept.leader_id} 指向的人不属于本公司 ⇒
 *       「直属上级」跨公司，本地公司的事务由外公司人审批。<b>本项目代码当前不校验此项（R4 属组织治理规则）</b>，
 *       但体检要报出来供判定。</li>
 *   <li><b>R5 节点名一致性</b>（E-1 新增）：流程已部署的节点显示名与
 *       {@code wf_node_config.node_name} 失配。引擎按 {@code taskDefinitionKey} 取人故
 *       <b>功能不受影响</b>，但按显示名排查必然取错角色 ⇒ 只报警、不自动改
 *       BPMN 已部署，改任一侧都牵动另一侧。</li>
 * </ol>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>只读</b>：本类不改任何数据，只出体检报告（修复由治理动作或页面完成）；</li>
 *   <li><b>fail-open</b>：任何查询异常都归入「体检失败」而<b>不是</b>「不合规」，
 *       避免体检本身成为故障源；</li>
 *   <li><b>等级分明</b>：{@code ERROR}（必然死锁，必须改）／{@code WARN}（可降级运行，建议改）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgHealthChecker {

    private final JdbcTemplate jdbc;

    /** model_key → node_key 集合，巡检时判断「引擎侧是否已有该 key」。 */
    private volatile Map<String, Set<String>> keyCache;

    /** 巡检入口：跑一次全量体检，返回报告（与 HTTP 层 org-health/overview 同源）。 */
    public Map<String, Object> check() {
        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> companies = new ArrayList<>();
        int checked = 0;
        try {
            // 只体检「启用中」的公司：status='DISABLED' 表示主体已建但尚未启用（无部门无人员），
            // 纳入体检必然全红，属噪声。与 CompanyService.tree() 的规则保持一致。
            List<Map<String, Object>> comps = jdbc.queryForList(
                    "/* tenant-ok: 组织巡检作业跨租户扫描 */ SELECT id, company_name FROM sys_company WHERE status = 'NORMAL' ORDER BY id");
            checked = comps.size();
            for (Map<String, Object> c : comps) {
                long cid = num(c.get("id"));
                String cname = str(c.get("company_name"));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("companyId", cid);
                row.put("companyName", cname);
                long deptCnt = count("/* tenant-ok: 组织巡检作业跨租户扫描 */ SELECT COUNT(*) c FROM sys_dept WHERE company_id = ?", cid);
                long rootCnt = count("/* tenant-ok: 组织巡检作业跨租户扫描 */ SELECT COUNT(*) c FROM sys_dept WHERE company_id = ? AND parent_id = 0", cid);
                long leaderCnt = count("/* tenant-ok: 组织巡检作业跨租户扫描 */ SELECT COUNT(*) c FROM sys_dept WHERE company_id = ? AND leader_id IS NOT NULL", cid);
                long headCnt = countRole(cid, "ORG_HEAD");
                row.put("deptCnt", deptCnt);
                row.put("rootDeptCnt", rootCnt);
                row.put("leaderCnt", leaderCnt);
                row.put("orgHeadCnt", headCnt);
                companies.add(row);

                // ── R1 岗位独苗（ERROR）──
                if (leaderCnt > 0 && headCnt < 2) {
                    issues.add(issue("R1", "ERROR", cname,
                            "公司有 " + leaderCnt + " 名部门负责人，但单位/基地负责人角色（ORG_HEAD）只有 "
                                    + headCnt + " 人：负责人恰好为提单人时「直属上级审核」节点无人可办，必然死锁",
                            "为该公司补 ORG_HEAD 角色至 ≥2 人（勿授予 is_admin 账号；一人只补一个岗）"));
                }
                // ── R2 汇报线断裂（ERROR）──
                if (deptCnt > 1 && rootCnt == deptCnt) {
                    issues.add(issue("R2", "ERROR", cname,
                            "公司 " + deptCnt + " 个部门全部 parent_id=0，无公司级汇总节点，汇报线断在第一层",
                            "建 1 个公司级汇总部门（parent_id=0, dept_level=0）并把其余部门挂到它下面"));
                }
            }
            // ── R3 leader 即提单人（WARN）──
            for (Map<String, Object> r : jdbc.queryForList(
                    "/* tenant-ok: 组织巡检作业跨租户扫描 */ SELECT d.company_id, d.id dept_id, d.dept_name, u.nick_name, u.emp_no "
                            + "FROM sys_dept d JOIN sys_user u ON u.id = d.leader_id "
                            + "WHERE d.leader_id IS NOT NULL AND d.del_flag = 0 AND u.del_flag = 0 "
                            + "AND EXISTS (SELECT 1 FROM exp_bill b WHERE b.create_by = u.id "
                            + "            AND b.dept_id = d.id AND b.del_flag = 0 "
                            + "            AND b.create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY))")) {
                issues.add(issue("R3", "WARN", str(r.get("dept_name")),
                        "部门负责人「" + str(r.get("nick_name")) + "」(" + str(r.get("emp_no"))
                                + ") 近 30 天在本部门发起过单据：他本人发起的单在该节点无人可办",
                        "已由三级降级备用处理，不阻断流程；组织上建议由副职代发，或将该节点改派给上级"));
            }
            // ── R4 leader 跨公司（WARN）──
            for (Map<String, Object> r : jdbc.queryForList(
                    "/* tenant-ok: 组织巡检作业跨租户扫描 */ SELECT d.company_id, d.dept_name, u.company_id leader_co, u.nick_name, u.emp_no "
                            + "FROM sys_dept d JOIN sys_user u ON u.id = d.leader_id "
                            + "WHERE d.leader_id IS NOT NULL AND d.del_flag = 0 AND u.del_flag = 0 "
                            + "AND u.company_id <> d.company_id")) {
                issues.add(issue("R4", "WARN", str(r.get("dept_name")),
                        "「" + str(r.get("nick_name")) + "」(" + str(r.get("emp_no")) + ") 是本部门负责人，"
                                + "但其公司归属为 " + num(r.get("leader_co")) + "，与部门所属公司 "
                                + num(r.get("company_id")) + " 不一致：「直属上级」跨公司",
                        "改派为本公司负责人（本项目代码当前不校验此项，属组织管理规则，请业务决定）"));
            }
            // ── R5 节点名一致性（E-1，WARN）──
            issues.addAll(nodeNameMismatchIssues());
        } catch (Exception ex) {
            // fail-open：体检异常不得让接口 500，也不得被读成「不合规」
            log.warn("组织健康体检执行异常（本次结果不作合规判定）: {}", ex.getMessage());
        }

        int errorCount = 0;
        int warnCount = 0;
        for (Map<String, Object> i : issues) {
            if ("ERROR".equals(i.get("level"))) {
                errorCount++;
            } else {
                warnCount++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("checkedCompanies", checked);
        summary.put("totalIssues", issues.size());
        summary.put("errorCount", errorCount);
        summary.put("warnCount", warnCount);
        summary.put("passed", errorCount == 0);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("redLines", redLineCatalog());
        out.put("issues", issues);
        out.put("companies", companies);
        return out;
    }

    /**
     * E-1：流程已部署节点名 与 {@code wf_node_config.node_name} 的一致性巡检。
     *
     * <p>流程设计器里「节点名称」（给人看）与「处理人角色」（给引擎）是两个字段，
     * 但导出部署时必做一致性校验。本项目缺这道校验，界面上就对不上：
     * {@code GK_1} 运行时显示「…【共享会计】」而配置角色是 {@code FIN_GL}、
     * {@code GK_3} 运行时叫「总账记账复核」而配置叫「总账记账」。
     * 引擎按 {@code taskDefinitionKey} 取人故功能不受影响，
     * 但自动化脚本/运维按显示名取人会拿到错误角色 ⇒ <b>只报警、不改任何一侧</b>。
     *
     * <p>2026-11-10 修复两处盲区：① 用 {@code wf_instance.model_key} 而非 {@code biz_type}
     * 关联配置（两者不同名时原来整条模型被跳过）；② 在办 ∪ 近 30 天历史都扫
     * （原来只扫 {@code act_ru_task}，办结即消失，失配永远查不到）。
     */
    private List<Map<String, Object>> nodeNameMismatchIssues() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Map<String, Set<String>> nameByModel = new LinkedHashMap<>();
            // key → 配置名：报「运行时 X vs 配置 Y」时能给出真实的 Y（原来只打「(按 key 命中)」）
            Map<String, Map<String, String>> nameByKey = new LinkedHashMap<>();
            for (Map<String, Object> n : jdbc.queryForList(
                    "/* tenant-ok: 组织巡检作业跨租户扫描 */ SELECT model_key mk, node_key nk, node_name nm FROM wf_node_config WHERE del_flag = 0")) {
                String mk = str(n.get("mk"));
                nameByModel.computeIfAbsent(mk, k -> new HashSet<>()).add(str(n.get("nm")));
                nameByKey.computeIfAbsent(mk, k -> new LinkedHashMap<>()).put(str(n.get("nk")), str(n.get("nm")));
            }
            Map<String, Set<String>> keyByModel = keyIndex();
            // 两处兼容处理：
            //  ① 原用 w.biz_type 当 model_key —— 但二者并非总是同名：
            //     FA_TRANSFER_CROSS→FA_TRANSFER、TR_BANK_MASTER/TR_SEAL_CHANGE→MD_MASTER，
            //     用 biz_type 查 wf_node_config 会查不到，整条模型被 continue 跳过（该检查形同虚设）。
            //  ② 原只扫 act_ru_task（在办）—— 流程一办结任务即消失，已办结流程的名称失配永远查不到
            //     （GL_MERGE 的 GMG_2 失配就是这样漏掉的）。
            //     改为「在办 ∪ 近 30 天历史」，按 (model_key, task_def_key, name) 去重。
            for (Map<String, Object> d : jdbc.queryForList(
                    "/* tenant-ok: 组织巡检作业跨租户扫描 */ SELECT DISTINCT w.model_key bt, a.NAME_ nm, a.TASK_DEF_KEY_ dk "
                            + "FROM act_ru_task a JOIN wf_instance w ON w.proc_inst_id = a.PROC_INST_ID_ "
                            + "WHERE a.NAME_ IS NOT NULL AND a.TASK_DEF_KEY_ IS NOT NULL "
                            + "UNION "
                            + "SELECT DISTINCT w.model_key bt, h.NAME_ nm, h.TASK_DEF_KEY_ dk "
                            + "FROM act_hi_taskinst h JOIN wf_instance w ON w.proc_inst_id = h.PROC_INST_ID_ "
                            + "WHERE h.NAME_ IS NOT NULL AND h.TASK_DEF_KEY_ IS NOT NULL "
                            + "AND h.START_TIME_ >= DATE_SUB(NOW(), INTERVAL 30 DAY)")) {
                String bt = String.valueOf(col(d, "bt"));
                String nm = String.valueOf(col(d, "nm"));
                String dk = String.valueOf(col(d, "dk"));
                Set<String> names = nameByModel.get(bt);
                if (names == null || names.isEmpty()) {
                    continue;   // 该模型没有生效配置（如历史遗留模型），不报
                }
                Set<String> keys = keyByModel.get(bt);
                boolean nameHit = names.contains(nm);
                boolean keyHit = keys != null && keys.contains(dk);
                if (!nameHit && !keyHit) {
                    out.add(issue("R5", "WARN", bt,
                            "流程已部署节点名与配置不一致：运行时「" + nm + "」(taskDefinitionKey=" + dk
                                    + ") 在 wf_node_config(model_key=" + bt + ", del_flag=0) 中"
                                    + "既无同名 node_name 也无同 key 的 node_key",
                            "引擎按 taskDefinitionKey 取人故功能不受影响；但按显示名排查会取错角色。"
                                    + "请业务确认哪一侧名称为准后人工同步部署流程与节点配置（两侧都勿由脚本自动改）"));
                } else if (!nameHit) {
                    String cfgName = nameByKey.getOrDefault(bt, java.util.Collections.emptyMap())
                            .getOrDefault(dk, "(该 key 未配节点名)");
                    out.add(issue("R5", "WARN", bt,
                            "节点显示名与配置不一致（功能正常，仅是排查标准不同）：运行时「" + nm
                                    + "」vs 配置「" + cfgName
                                    + "」(key=" + dk + ")",
                            "引擎按 key 取人故不受影响；建议在下次流程发布时把 BPMN 节点名与 wf_node_config.node_name 对齐"));
                }
            }
        } catch (Exception ex) {
            log.warn("R5 节点名一致性巡检异常（跳过该项）: {}", ex.getMessage());
        }
        return out;
    }

    /** R5 辅助：model_key → node_key 集合（一次加载多次复用）。 */
    private Map<String, Set<String>> keyIndex() {
        Map<String, Set<String>> c = keyCache;
        if (c != null) {
            return c;
        }
        Map<String, Set<String>> built = new LinkedHashMap<>();
        try {
            for (Map<String, Object> n : jdbc.queryForList(
                    "/* tenant-ok: 组织巡检作业跨租户扫描 */ SELECT model_key mk, node_key nk FROM wf_node_config WHERE del_flag = 0")) {
                built.computeIfAbsent(String.valueOf(col(n, "mk")), k -> new HashSet<>())
                        .add(String.valueOf(col(n, "nk")));
            }
        } catch (Exception ignored) {
            // fail-open：取不到 key 索引时退化为「只看名称」
        }
        keyCache = built;
        return built;
    }

    /** 红线目录：判据说明，供前端直接展示「这体检在查什么」。 */
    public List<Map<String, Object>> redLineCatalog() {
        List<Map<String, Object>> c = new ArrayList<>();
        c.add(rule("R1", "岗位独苗", "ERROR",
                "凡配了 leader_id 的公司，单位/基地负责人（ORG_HEAD）须 ≥ 2 人",
                "负责人只有 1 人时，他本人发起的单在「直属上级审核」节点无办理人，必然死锁"));
        c.add(rule("R2", "汇报线断裂", "ERROR",
                "公司内部门不得全部 parent_id=0（须有公司级汇总节点）",
                "汇报线断在第一层时 escalateTarget 与 empty_strategy=ESCALATE 全部失效"));
        c.add(rule("R3", "leader 即提单人", "WARN",
                "部门 leader 近 30 天在本部门发起过单据",
                "已由三级降级备用处理，不阻断流程，属组织管理提醒"));
        c.add(rule("R4", "leader 跨公司", "WARN",
                "sys_dept.leader_id 指向的人应属本公司",
                "「直属上级」跨公司，本地公司的事务由外公司人审批（本项目代码不校验，属管理规则）"));
        c.add(rule("R5", "节点名一致性", "WARN",
                "流程已部署节点名与 wf_node_config.node_name 应一致（按 key 或 name 至少一处命中）",
                "节点名与处理人角色分离，但导出时仍应做一致性校验；本项只报警不改"));
        return c;
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0L : v;
    }

    private long countRole(long companyId, String roleCode) {
        Long v = jdbc.queryForObject(
                "/* tenant-ok: 组织巡检作业跨租户扫描 */ SELECT COUNT(DISTINCT u.id) FROM sys_user u "
                        + "JOIN sys_user_role ur ON ur.user_id = u.id "
                        + "JOIN sys_role r ON r.id = ur.role_id "
                        + "WHERE r.role_code = ? AND u.company_id = ? AND u.del_flag = 0 "
                        + "AND (u.is_admin IS NULL OR u.is_admin = 0)", Long.class, roleCode, companyId);
        return v == null ? 0L : v;
    }

    private Map<String, Object> rule(String code, String name, String level, String rule, String why) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("level", level);
        m.put("rule", rule);
        m.put("why", why);
        return m;
    }

    private Map<String, Object> issue(String code, String level, Object scope, String detail, String advice) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("redLine", code);
        m.put("level", level);
        m.put("scope", scope);
        m.put("detail", detail);
        m.put("advice", advice);
        return m;
    }

    /** JdbcTemplate 取值：列名大小写不敏感（MySQL 常返回大写）。 */
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

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
