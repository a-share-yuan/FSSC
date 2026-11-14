package com.fssc.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.auth.AuthService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.enums.SubjectType;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 版本切换：把「装配档」这个选择落成对本租户能力集合的非破坏性裁剪。
 *
 * <p>一套系统服务一个客户，安装完成时租户就带有全量的组织、岗位、流程模型与路由，
 * 不存在运行时新建空租户。切换档位只做三件事：换套餐绑定（决定登录 JWT 里的特性码）、
 * 补齐本档流程模板、增删本服务写入的流程路由。安装时的组织主数据、业务单据、
 * 全量流程模型与全量路由一律不动；sys_menu 是全局表，装配阶段绝不修改。</p>
 *
 * <p>档位只是能力集合 —— 差异只落在特性码（pf_tenant_package）与流程路由
 * （wf_process_config），差异只描述能力集合，不涉及任何规模、用量或售卖口径。</p>
 *
 * <p>共享档就是安装后的全量默认态：沿用已有流程模型，不部署新模板；清掉本服务写入的
 * 档位路由后，安装时的全量路由自然生效，无需任何「装配」动作。</p>
 *
 * <p>档位路由以 {@code remark='preset=&lt;档位&gt;'} 标记归属，切档只增删带此标记的行，
 * 绝不遍历 config_name 删除，避免误伤安装时的同名业务路由。</p>
 *
 * <p>事务边界：不套外层 {@code @Transactional}。Flowable 部署有自己的事务边界，且装配各步
 * 全部按业务键幂等；每一步独立提交，把 {@code pf_tenant.op_mode} 放在最后一步写，
 * 它就是「完成标记」——任一步失败则标记不落、可安全重试，不需要长事务回滚。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardService {

    /** 档位路由的归属标记，切档清理只认这个前缀 */
    private static final String ROUTE_TAG = "preset=";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RepositoryService repositoryService;

    private volatile JsonNode cachedPresets;

    // ------------------------------------------------------------------ 对外接口

    /** 引导页状态：当前版本、完成时间、是否需要选档、可选档位清单 */
    public Map<String, Object> status() {
        LoginUser u = UserContext.getRequired();
        long tid = requireTenant(u);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tenant_code, tenant_name, op_mode, onboard_time FROM pf_tenant WHERE id=?", tid);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "企业不存在");
        }
        Map<String, Object> row = rows.get(0);
        Object op = row.get("op_mode");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenantCode", row.get("tenant_code"));
        out.put("tenantName", row.get("tenant_name"));
        out.put("opMode", op);
        out.put("onboardTime", row.get("onboard_time"));
        out.put("needOnboard", op == null || String.valueOf(op).isBlank());
        // 已选过档的企业管理员仍可回到引导页更换；能力裁剪是幂等的，不改动已有业务数据
        Set<String> perms = u.getPerms();
        out.put("canSwitch", perms != null && perms.contains(AuthService.WILDCARD_PERM));
        out.put("presets", presetList());
        return out;
    }

    /** 切换版本。同档重跑用于补齐缺项，切到另一档走同一条幂等路径。 */
    public Map<String, Object> apply(String presetKey) {
        LoginUser u = UserContext.getRequired();
        long tid = requireTenant(u);
        requireAdmin(u);
        JsonNode root = presets();
        JsonNode p = root.path("presets").get(presetKey == null ? "" : presetKey);
        if (p == null || !p.isObject()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知装配档: " + presetKey);
        }
        assemble(tid, presetKey, p);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("preset", presetKey);
        out.put("label", p.path("label").asText());
        out.put("packageCode", p.path("packageCode").asText());
        out.put("modelKey", p.path("flow").path("modelKey").asText(""));
        out.put("opMode", presetKey);
        // 特性码是登录时写入 JWT 的快照，切换后必须重新登录才能看到新菜单
        out.put("needRelogin", true);
        return out;
    }

    // ------------------------------------------------------------------ 切换主流程

    private void assemble(long tid, String presetKey, JsonNode p) {
        log.info("切换版本 tenantId={} preset={}", tid, presetKey);

        // ① 套餐绑定：决定登录 JWT 里的特性码集合。原地更新既有绑定行，不删行、不改主键。
        bindPackage(tid, p.path("packageCode").asText(), presetKey);

        // ② 流程模板：共享档沿用安装时的全量模型；其余档按业务键补齐本档模板。
        JsonNode flow = p.path("flow");
        if (!flow.path("inherit").asBoolean(false)) {
            deployTemplate(tid, flow);
        }

        // ③ 路由：只增删本服务写入的档位路由，安装时的全量路由永远保留。
        ensureRoutes(tid, presetKey, flow);

        // ④ 完成标记放最后；不写 remark，避免覆盖企业的备注字段
        jdbc.update("UPDATE pf_tenant SET op_mode=?, onboard_time=NOW() WHERE id=?",
                presetKey, tid);
        log.info("版本切换完成 tenantId={} preset={}", tid, presetKey);
    }

    private void bindPackage(long tid, String packageCode, String presetKey) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM pf_package WHERE package_code=? AND del_flag=0 LIMIT 1",
                Long.class, packageCode);
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "装配档未登记: " + packageCode);
        }
        // 生效时间取数据库时钟。应用侧 LocalDateTime 带毫秒，写进无小数的 DATETIME 列会被
        // 四舍五入进位到下一秒；selectEnabledFeatures 的 start_time <= now() 于是在切换后
        // 最多 1 秒内为 false，重新登录拿到空特性码、菜单几乎全空。
        List<Long> bound = jdbc.queryForList(
                "SELECT id FROM pf_tenant_package WHERE ref_tenant_id=? AND del_flag=0 "
                        + "ORDER BY id LIMIT 1", Long.class, tid);
        if (!bound.isEmpty()) {
            jdbc.update("UPDATE pf_tenant_package SET package_id=?, start_time=NOW(), "
                            + "end_time=DATE_ADD(NOW(), INTERVAL 1 YEAR), status='ACTIVE', "
                            + "remark=? WHERE id=?",
                    ids.get(0), ROUTE_TAG + presetKey, bound.get(0));
            return;
        }
        jdbc.update("INSERT INTO pf_tenant_package "
                        + "(ref_tenant_id,package_id,start_time,end_time,status,del_flag,remark) "
                        + "VALUES (?,?,NOW(),DATE_ADD(NOW(), INTERVAL 1 YEAR),'ACTIVE',0,?)",
                tid, ids.get(0), ROUTE_TAG + presetKey);
    }

    // ------------------------------------------------------------------ 流程模板与路由

    private void deployTemplate(long tid, JsonNode flow) {
        String key = flow.path("modelKey").asText();
        String modelName = flow.path("modelName").asText(key);
        String bpmn = readBpmn(flow.path("bpmn").asText());
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, bpmn_xml, version, status, deploy_id FROM wf_model "
                        + "WHERE tenant_id=? AND model_key=?", tid, key);
        long modelId;
        if (rows.isEmpty()) {
            // 首次装配：按模板建模型并部署一版可执行定义。
            modelId = upsert("wf_model", new LinkedHashMap<>(), vals(
                    "tenant_id", tid, "model_key", key, "model_name", modelName,
                    "bpmn_xml", bpmn, "version", 1, "status", "DRAFT",
                    "source", "IMPORT", "org_scope", "ALL",
                    "biz_type", "EXP_CLAIM", "category", "EXPENSE",
                    "del_flag", 0, "create_time", now));
            int version = 2;
            Deployment dep = repositoryService.createDeployment()
                    .addString(key + ".bpmn20.xml", bpmn)
                    .name(modelName)
                    .tenantId(String.valueOf(tid))
                    .deploy();
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(dep.getId()).singleResult();
            jdbc.update("UPDATE wf_model SET bpmn_xml=?, model_name=?, deploy_id=?, proc_def_id=?, "
                            + "version=?, status='DEPLOYED' WHERE id=?",
                    bpmn, modelName, dep.getId(), pd == null ? null : pd.getId(), version, modelId);
            log.info("流程模板已部署 tenantId={} key={} version={} deploymentId={}",
                    tid, key, version, dep.getId());
        } else {
            // 模型已存在：企业可能已自行调整流程图或节点审批人。切换版本只改变能力集合，
            // 不覆盖模型定义、不重新部署，也不把节点配置重置回预设值。
            modelId = ((Number) rows.get(0).get("id")).longValue();
        }

        jdbc.update("UPDATE wf_model SET biz_type='EXP_CLAIM', category='EXPENSE', "
                + "org_scope='ALL' WHERE id=?", modelId);
        // 节点配置只补齐缺失行，已有配置一律保留企业当前值。
        int sort = 1;
        for (JsonNode n : flow.path("nodes")) {
            // node_key 必须逐字对齐已部署 BPMN 里的 userTask id
            String nodeKey = n.path("nodeKey").asText();
            String nodeName = n.path("nodeName").asText();
            String role = n.path("role").asText();
            List<Map<String, Object>> old = jdbc.queryForList(
                    "SELECT id FROM wf_node_config WHERE tenant_id=? AND model_key=? AND node_key=? "
                            + "ORDER BY id LIMIT 1", tid, key, nodeKey);
            if (old.isEmpty()) {
                jdbc.update("INSERT INTO wf_node_config "
                                + "(tenant_id,model_key,node_key,node_name,assign_type,assign_value,"
                                + "handling_mode,sort,del_flag) VALUES (?,?,?,?,'ROLE',?,'AUTO',?,0)",
                        tid, key, nodeKey, nodeName, role, sort);
            }
            sort++;
        }
    }

    private void ensureRoutes(long tid, String presetKey, JsonNode flow) {
        // 只回收本服务写入的档位路由；安装时的全量业务路由（含同名可能）一律不动。
        jdbc.update("DELETE FROM wf_process_config WHERE tenant_id=? AND biz_type='EXP_CLAIM' "
                + "AND remark LIKE 'preset=%'", tid);
        String modelKey = flow.path("modelKey").asText();
        for (JsonNode r : flow.path("routes")) {
            jdbc.update("INSERT INTO wf_process_config "
                            + "(tenant_id,biz_type,sub_type,config_name,model_key,company_id,"
                            + "amount_min,amount_max,priority,status,remark,del_flag) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,'NORMAL',?,0)",
                    tid, r.path("bizType").asText(), txt(r, "subType"),
                    r.path("configName").asText(), modelKey, lng(r, "companyId"),
                    dec(r, "amountMin"), dec(r, "amountMax"), r.path("priority").asInt(0),
                    ROUTE_TAG + presetKey);
        }
    }

    // ------------------------------------------------------------------ 通用工具

    /** 按业务键幂等写入：命中则复用 id 并刷新重映射列，否则新建并回读自增 id。 */
    private long upsert(String table, LinkedHashMap<String, Object> keys,
                        LinkedHashMap<String, Object> vals, String... refreshCols) {
        List<Long> ids = keys.isEmpty() ? Collections.emptyList() : jdbc.query(
                "SELECT id FROM " + table + " WHERE "
                        + keys.keySet().stream().map(c -> c + "=?").collect(Collectors.joining(" AND "))
                        + " ORDER BY id LIMIT 1",
                (rs, i) -> rs.getLong(1), keys.values().toArray());
        if (!ids.isEmpty()) {
            long id = ids.get(0);
            if (refreshCols.length > 0) {
                String sets = Arrays.stream(refreshCols).map(c -> c + "=?")
                        .collect(Collectors.joining(","));
                Object[] args = Arrays.stream(refreshCols).map(vals::get).toArray();
                Object[] all = Arrays.copyOf(args, args.length + 1);
                all[args.length] = id;
                jdbc.update("UPDATE " + table + " SET " + sets + " WHERE id=?", all);
            }
            return id;
        }
        String sql = "INSERT INTO " + table + " (" + String.join(",", vals.keySet())
                + ") VALUES (" + placeholders(vals.size()) + ")";
        Object[] args = vals.values().toArray();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, kh);
        Number k = kh.getKey();
        if (k == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "装配写入未返回主键: " + table);
        }
        return k.longValue();
    }

    private JsonNode presets() {
        JsonNode n = cachedPresets;
        if (n == null) {
            synchronized (this) {
                n = cachedPresets;
                if (n == null) {
                    try (InputStream in = OnboardService.class
                            .getResourceAsStream("/presets/tenant_presets.json")) {
                        if (in == null) {
                            throw new BizException(ErrorCode.PARAM_INVALID, "装配配方缺失");
                        }
                        n = mapper.readTree(in);
                        cachedPresets = n;
                    } catch (IOException e) {
                        throw new BizException(ErrorCode.PARAM_INVALID, "装配配方读取失败: " + e.getMessage());
                    }
                }
            }
        }
        return n;
    }

    private String readBpmn(String rel) {
        String name = rel.contains("/") ? rel.substring(rel.lastIndexOf('/') + 1) : rel;
        try (InputStream in = OnboardService.class.getResourceAsStream("/presets/" + name)) {
            if (in == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "流程模板缺失: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程模板读取失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> presetList() {
        List<Map<String, Object>> out = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = presets().path("presets").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode p = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", e.getKey());
            m.put("label", p.path("label").asText(e.getKey()));
            // 配方用 audience 描述适用对象；保留 description 作为可选更详细文案
            String desc = p.path("description").asText(null);
            m.put("description", desc == null ? p.path("audience").asText("") : desc);
            m.put("packageCode", p.path("packageCode").asText(""));
            m.put("modelKey", p.path("flow").path("modelKey").asText(""));
            m.put("domains", p.path("expectedMenu").path("domains").asInt(0));
            m.put("pages", p.path("expectedMenu").path("pages").asInt(0));
            out.add(m);
        }
        return out;
    }

    private long requireTenant(LoginUser u) {
        if (u.getSubjectType() != SubjectType.TENANT || u.getTenantId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅企业用户可以访问版本引导");
        }
        return u.getTenantId();
    }

    private void requireAdmin(LoginUser u) {
        Set<String> perms = u.getPerms();
        if (perms == null || !perms.contains(AuthService.WILDCARD_PERM)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅企业管理员可以切换版本");
        }
    }

    private static LinkedHashMap<String, Object> vals(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    private static Object txt(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static Object lng(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return (v == null || v.isNull()) ? null : v.asLong();
    }

    private static Object dec(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return (v == null || v.isNull()) ? null : new BigDecimal(v.asText());
    }
}
