// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.framework.cache.TenantCache;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.entity.WfTask;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程节点配置解析器（wf_node_config）——超时判定 / 催办链路的唯一入口。
 *
 * <p><b>契约（起）</b>：{@code wf_node_config.node_key} 必须与该模型 BPMN 的 userTask
 * {@code taskDefinitionKey} <b>逐字符一致</b>，即精确键 {@code modelKey:nodeKey}。
 * 解析器只做精确匹配，匹配不到即返回 {@code null}（该节点无配置 → 超时/空策略不生效，
 * 行为等同默认 ERROR/不催办），<b>不再做任何位置兜底</b>。
 *
 * <p><b>历史沿革（为什么删掉兜底）</b>：早期 BPMN 由 {@code WfBpmnTemplateService} 统一生成
 * {@code Task_1..Task_N}，而 {@code wf_node_config.node_key} 却是业务编码
 * （{@code CLAIM_T1 / FA_DISPOSE_N01 …}），两者"永远匹配不上"，曾用一条
 * 「{@code Task_N → 该 model 按 sort 升序第 N 个配置}」的<b>位置兜底桥</b>权宜凑合。
 * 该桥<b>脆弱且危险</b>：两表行数/顺序一旦漂移，策略与超时就会落到<b>错误的节点</b>上
 * （流程数量不符、语义错配，且 ROLE 分支是"追加候选组"→ 直接造成越权放行）。
 * 现网 {@code node_key} 已按 BPMN 实际 id 重建并<b>移除该兜底</b>；
 * 写入侧由 {@link WfNodeConfigService} 校验 node_key ∈ 该模型 BPMN userTask 集合，杜绝复发。
 *
 * <p><b>单一实现</b>：Controller（列表/统计）与 Job（催办执行）共用本类，
 * 避免两处各写一套映射规则造成"统计说超时了、催办却不动"的漂移。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfNodeConfigResolver {

    private final WfNodeConfigMapper wfNodeConfigMapper;

    /**
     * 全表节点配置的缓存位。一次 /wf/approve 会把这整张表载入 6 遍、
     * /wf/start-candidates 2 遍；写侧 WfNodeConfigService / WfAssigneeReplaceService 均挂 evictAll。
     * key 由 TenantCache 统一带租户前缀，置 fssc.redis.enabled=true 即切集中式缓存（多实例一致）。
     */
    public static String allCacheKey() {
        return TenantCache.mdKey("wf:nodeConfig:all");
    }

    /** 供其它读取方（如 WfPerfService 统计腿）复用同一缓存位，不要各建各的 */
    public static List<WfNodeConfig> loadAllCachedWith(Supplier<List<WfNodeConfig>> loader) {
        return TenantCache.get(allCacheKey(), loader);
    }

    /** 节点配置有任何写入即整位失效：代价只是一次全表回源，换来的是不要按旧档位派工 */
    public static void evictAll() {
        TenantCache.evict(allCacheKey());
    }

    private List<WfNodeConfig> loadAllCached() {
        return loadAllCachedWith(() -> wfNodeConfigMapper.selectList(new LambdaQueryWrapper<>()));
    }

    /** 按待办列表批量构建 {@code modelKey:nodeKey -> WfNodeConfig} 索引 */
    public Map<String, WfNodeConfig> index(Collection<WfTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (WfTask t : tasks) {
            if (t.getModelKey() != null && t.getNodeKey() != null) {
                keys.add(t.getModelKey() + ":" + t.getNodeKey());
            }
        }
        return indexByKeys(keys);
    }

    /** 单条解析：{@code modelKey + nodeKey} → 节点配置；无配置返回 null */
    public WfNodeConfig resolve(String modelKey, String nodeKey) {
        if (modelKey == null || nodeKey == null) {
            return null;
        }
        return indexByKeys(Set.of(modelKey + ":" + nodeKey)).get(modelKey + ":" + nodeKey);
    }

    /**
     * 全部 {@code handling_mode=POOL}（任务池/抢单作业节点）的节点键集合，形如 {@code modelKey:nodeKey}。
     *
     * <p>待办列表要按行标注"已被他人领取"的软锁，逐行 {@code resolve()} 会把整张配置表
     * 载入 N 次；这里一次查完，供列表批量判定。
     */
    public Set<String> poolNodeKeys() {
        Set<String> out = new LinkedHashSet<>();
        for (WfNodeConfig nc : loadAllCached()) {
            if (nc.getModelKey() == null || nc.getNodeKey() == null) {
                continue;
            }
            String mode = nc.getHandlingMode() == null ? "" : nc.getHandlingMode().trim();
            if ("POOL".equalsIgnoreCase(mode)) {
                out.add(nc.getModelKey() + ":" + nc.getNodeKey());
            }
        }
        return out;
    }

    /**
     * AUTO（按角色自动指派）档节点的 {@code modelKey:nodeKey -> assign_value 角色码} 映射。
     *
     * <p>为什么需要：Flowable 的 {@code taskCandidateGroupIn} 只回「未指派」的任务，而 AUTO 档在节点
     * 激活时就把 assignee 写死（{@code act_ru_task.ASSIGNEE_} 有值、按候选组查不到它）。
     * 于是同岗位的其他人（如 FIN_AUDIT 的 B 角色）{@code /wf/todo} 里**看不到**这条任务，
     * 但服务端 {@code approve} 只按登录人权限与两道校验放行、**不校验 assignee** ⇒
     * 「能办但看不见」，真实用户打开工作台一片空白，单子静默停摆且无任何提示。
     *
     * <p>本映射供待办回捞使用：凡「AUTO 档 + 节点角色 ∈ 我的角色 + assignee 不是我」的任务
     * 都要在我的待办里可见（可否办理仍由 {@code approve} 的两道校验与 canOperate 决定）。
     */
    public Map<String, String> autoNodeRoleMap() {
        Map<String, String> out = new HashMap<>();
        for (WfNodeConfig nc : loadAllCached()) {
            if (nc.getModelKey() == null || nc.getNodeKey() == null) {
                continue;
            }
            String mode = nc.getHandlingMode() == null ? "" : nc.getHandlingMode().trim();
            if ("POOL".equalsIgnoreCase(mode)) {
                continue; // POOL 已有 poolTasksClaimedByOthers 专门规则，不重复回捞
            }
            String role = nc.getAssignValue() == null ? "" : nc.getAssignValue().trim();
            if (!role.isEmpty()) {
                out.put(nc.getModelKey() + ":" + nc.getNodeKey(), role);
            }
        }
        return out;
    }

    /**
     * 全部 {@code allow_addsign=0}（未开放加签）的节点键集合，形如 {@code modelKey:nodeKey}。
     *
     * <p>与 {@link #poolNodeKeys()} 同一做法：待办列表要按行决定「加签」按钮是否隐藏，
     * 逐行 resolve 会把整张配置表查 N 遍。仅加签校验总开关打开时才被调用（缺省关，零额外查询）。
     */
    public Set<String> addsignDisabledNodeKeys() {
        Set<String> out = new LinkedHashSet<>();
        for (WfNodeConfig nc : wfNodeConfigMapper.selectList(new LambdaQueryWrapper<WfNodeConfig>()
                .eq(WfNodeConfig::getAllowAddsign, 0))) {
            if (nc.getModelKey() != null && nc.getNodeKey() != null) {
                out.add(nc.getModelKey() + ":" + nc.getNodeKey());
            }
        }
        return out;
    }

    /**
     * 字段级节点权限（form_perm）解析 —— {@code wf_node_config.form_perm} 的<b>唯一运行时消费点</b>
     * （该列必须有运行时消费点，否则就是「字段有、运行时不生效」）。
     *
     * <p><b>既定 schema</b>（建列注释规则）：扁平 {@code {"field":"H|R|W"}} —— H=隐藏 / R=只读 / W=可编辑。
     * 解析规则（与 UniERP {@code resolveFormFieldPerm} 同语义，便于后续引擎级迁移对齐）：
     * <ul>
     *  <li><b>取严不取宽</b>：同一字段出现多档归一结果时取最严（H &gt; R &gt; W）——权限体系宁可保守；</li>
     *  <li><b>fail-open 边界</b>：未配置 / 配置损坏 → 返回<b>空 Map</b>（不裁剪，保持历史行为），
     *      即「没配就没有限制」，因此限制性规则必须把字段写全；</li>
     *  <li>档位大小写/别名归一（h/hidden→H，r/ro/readonly→R，w/write/editable→W），单字段非法只跳过该字段，不整表失败。</li>
     * </ul>
     *
     * <p>消费方式：{@code WfTaskController#/task-form-perm} 按任务解析后交前端渲染（隐藏/只读）；
     * 与 timeout/cc* 同一条规则——经 {@link #resolve} 按 {@code modelKey:nodeKey} 精确匹配，孤儿配置行不生效。
     */
    public Map<String, String> resolveFormFieldPerm(String modelKey, String nodeKey) {
        WfNodeConfig cfg = resolve(modelKey, nodeKey);
        if (cfg == null || cfg.getFormPerm() == null || cfg.getFormPerm().isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new HashMap<>();
        try {
            Map<String, Object> raw = new ObjectMapper()
                    .readValue(cfg.getFormPerm(), new TypeReference<Map<String, Object>>() { });
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                String field = e.getKey() == null ? "" : e.getKey().trim();
                String code = normalizePerm(e.getValue());
                if (field.isEmpty() || code == null) {
                    continue; // 脏配置单字段跳过，不整表失败
                }
                String prev = out.get(field);
                if (prev == null || severityOf(code) > severityOf(prev)) {
                    out.put(field, code);
                }
            }
        } catch (Exception ex) {
            log.warn("form_perm 解析失败，fail-open 不裁剪 modelKey={} nodeKey={}: {}", modelKey, nodeKey, ex.getMessage());
            return Collections.emptyMap();
        }
        return out;
    }

    /** 档位归一：接受 H/R/W 及常见别名，非法返回 null（由调用方跳过该字段） */
    private static String normalizePerm(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim().toUpperCase();
        return switch (s) {
            case "H", "HIDDEN" -> "H";
            case "R", "RO", "READONLY", "READ_ONLY" -> "R";
            case "W", "WRITE", "EDIT", "EDITABLE" -> "W";
            default -> null;
        };
    }

    /** 严重度：H(3) &gt; R(2) &gt; W(1)，用于「取严不取宽」合并 */
    private static int severityOf(String code) {
        return switch (code) {
            case "H" -> 3;
            case "R" -> 2;
            default -> 1;
        };
    }

    /**
     * 按 key 集合构建索引：一次性载入全部节点配置（量级数百行，与原实现一致），
     * <b>仅注册精确键</b> {@code modelKey:nodeKey}。已移除 {@code Task_N} 位置兜底。
     */
    private Map<String, WfNodeConfig> indexByKeys(Set<String> keys) {
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<WfNodeConfig> allConfigs = loadAllCached();
        Map<String, WfNodeConfig> map = new HashMap<>();
        for (WfNodeConfig nc : allConfigs) {
            if (nc.getModelKey() == null || nc.getNodeKey() == null) {
                continue;
            }
            map.put(nc.getModelKey() + ":" + nc.getNodeKey(), nc);
        }
        return map;
    }
}
