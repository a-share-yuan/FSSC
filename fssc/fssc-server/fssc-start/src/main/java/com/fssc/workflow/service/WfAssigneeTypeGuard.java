// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfAssigneeScheme;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.mapper.WfAssigneeSchemeMapper;
import com.fssc.workflow.mapper.WfNodeConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A4：assign_type 可解析性 fast-fail 守卫。
 *
 * <p><b>背景</b>：{@code WfEngineFacade.applyNodeConfig} 的 switch 只为
 * USER / ROLE / INITIATOR / DEPT_LEADER / DEPT_LEADER_UP <b>五档</b>写了专用分支；
 * POST / EXPRESSION / SHARE_POOL 及任何未识别取值一律落进 {@code default} 分支，转交
 * {@link WfAssigneeResolver} 按「参与人方案」（{@code wf_assignee_scheme}）取人——
 * <b>完全没配方案时保持静默 no-op</b>（这是 G2 接入时刻意保留的"默认零变化"硬约束）。
 * 于是"配了 EXPRESSION 却无默认方案"的节点：配置者以为配了取人规则，运行时没有任何人被指派，
 * 单据在该节点永久滞留且无任何报错——比报错更难发现。
 *
 * <p><b>本类的职责</b>（v2.1 方案 §4 WS-1③「EXPRESSION/POST 降级为启动期 fast-fail」）：
 * <ul>
 *   <li><b>发布/重部署期 hard-fail</b>：{@link #assertModelAssigneesResolvable} 在
 *       {@code WfModelService.deployToEngine} 落地前调用——模型任一存活节点配了
 *       非五档 assign_type 且无启用方案 ⇒ 抛 {@link BizException} 拒绝加载，
 *       错误信息逐节点列清（model/node/type），配置者当场看到，不会把坏配置部署进引擎；</li>
 *   <li><b>启动期 loud 扫描</b>：{@link #scanAtStartup} 全量扫描历史节点配置，违规行
 *       <b>逐条 log.error</b>（含 model/node/type），不抛异常中断启动——
 *       一行坏数据不该把整个 FSSC 拖下线（那比静默 no-op 更糟），但必须在日志里显眼；
 *       修复入口即上面的发布期校验。</li>
 * </ul>
 *
 * <p><b>规则与运行时的关系</b>：本类的"五档"集合与 {@code applyNodeConfig} 的 switch
 * 分支<b>逐字对应</b>；方案存在性判定（{@code enabled=1}）与 {@code WfAssigneeResolver
 * #enabledSchemes} 同一规则。两边脱节时以运行时为准修订本类，不允许第三套规则。
 *
 * <p><b>现网影响</b>：现查 {@code wf_node_config} 存活 176 行 assign_type
 * <b>全为 ROLE</b> ⇒ 发布校验与启动扫描当前零命中，接入后零行为差异。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfAssigneeTypeGuard {

    /**
     * 运行时真正有专用分支的 assign_type（与 WfEngineFacade.applyNodeConfig switch 对应）。
     * 其余取值（POST / EXPRESSION / SHARE_POOL / 拼错的值 / null 之外的一切）都依赖
     * wf_assignee_scheme 兜底，无方案即静默 no-op。
     */
    private static final Set<String> NATIVE_TYPES = Set.of(
            "USER", "ROLE", "INITIATOR", "DEPT_LEADER", "DEPT_LEADER_UP");

    private final WfNodeConfigMapper nodeConfigMapper;
    private final WfAssigneeSchemeMapper schemeMapper;

    /**
     * 发布/重部署前校验：模型任一存活节点"配了非五档 assign_type 且无启用方案" ⇒ 拒绝加载。
     *
     * @param modelKey 待发布模型的流程定义 key（wf_model.model_key）
     * @throws BizException 存在不可解析节点时（错误信息逐节点列清）
     */
    public void assertModelAssigneesResolvable(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return;
        }
        List<String> violations = collectViolations(modelKey);
        if (!violations.isEmpty()) {
            String detail = String.join("；", violations);
            log.warn("A4 fast-fail 拒绝发布 modelKey={}：{} 个节点配了无兜底方案的 assign_type：{}",
                    modelKey, violations.size(), detail);
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "发布被拒绝（A4 取人 fast-fail）：以下 " + violations.size()
                            + " 个节点配置了 POST/EXPRESSION 等无专用分支的取人类型，且未配置参与人方案"
                            + "（wf_assignee_scheme），运行时会静默无人可办：" + detail
                            + "。请为该节点配置参与人方案，或改用 USER/ROLE/INITIATOR/"
                            + "DEPT_LEADER/DEPT_LEADER_UP 五种受支持的取人类型");
        }
    }

    /** 收集指定模型的违规节点（存活配置中 assign_type 非五档且无启用方案的行）。 */
    private List<String> collectViolations(String modelKey) {
        List<WfNodeConfig> cfgs;
        try {
            cfgs = nodeConfigMapper.selectList(new LambdaQueryWrapper<WfNodeConfig>()
                    .eq(WfNodeConfig::getModelKey, modelKey));
        } catch (Exception ex) {
            // 配置表不可读时不阻断发布（与 WfAssigneeResolver 的"无方案兜底"同一规则：
            // 读不到 ≠ 配错；真配错由发布路径上的正常查询暴露）。
            log.warn("A4 校验跳过（节点配置读取失败）modelKey={}: {}", modelKey, ex.getMessage());
            return List.of();
        }
        List<String> violations = new ArrayList<>();
        for (WfNodeConfig cfg : cfgs) {
            String type = cfg.getAssignType();
            if (type == null || type.isBlank()) {
                continue; // 未配置取人类型 ⇒ 保持 BPMN 默认，历史行为
            }
            if (NATIVE_TYPES.contains(type.trim().toUpperCase(Locale.ROOT))) {
                continue; // 五档专用分支，运行时真取人
            }
            if (hasEnabledScheme(modelKey, cfg.getNodeKey())) {
                continue; // 配了参与人方案 ⇒ default 分支有出路
            }
            violations.add("节点 " + cfg.getNodeKey()
                    + (cfg.getNodeName() == null ? "" : "(" + cfg.getNodeName() + ")")
                    + " assign_type=" + type.trim());
        }
        return violations;
    }

    /** 启用方案存在性（与 WfAssigneeResolver.enabledSchemes 同一规则：model_key + node_key + enabled=1）。 */
    private boolean hasEnabledScheme(String modelKey, String nodeKey) {
        if (modelKey == null || nodeKey == null || nodeKey.isBlank()) {
            return false;
        }
        try {
            Long cnt = schemeMapper.selectCount(new LambdaQueryWrapper<WfAssigneeScheme>()
                    .eq(WfAssigneeScheme::getModelKey, modelKey)
                    .eq(WfAssigneeScheme::getNodeKey, nodeKey)
                    .eq(WfAssigneeScheme::getEnabled, 1));
            return cnt != null && cnt > 0;
        } catch (Exception ex) {
            log.warn("A4 方案存在性查询失败 model={} node={}: {}", modelKey, nodeKey, ex.getMessage());
            return false;
        }
    }

    /**
     * 启动期 loud 扫描：历史坏配置逐条 log.error，不中断启动。
     *
     * <p>为什么不在启动期抛异常：一行绕过发布路径直插库的坏配置，若让整个应用起不来，
     * 代价是全部在途单据停摆——比"一个节点静默滞留"更糟。启动期只负责<b>让问题在日志里
     * 无处可藏</b>；真正的拦截在发布路径（{@link #assertModelAssigneesResolvable}）。
     */
    @PostConstruct
    public void scanAtStartup() {
        try {
            TenantContext.runIgnoreTenant(() -> {
                List<WfNodeConfig> all = nodeConfigMapper.selectList(null);
                int violations = 0;
                for (WfNodeConfig cfg : all) {
                    String type = cfg.getAssignType();
                    if (type == null || type.isBlank()
                            || NATIVE_TYPES.contains(type.trim().toUpperCase(Locale.ROOT))) {
                        continue;
                    }
                    if (hasEnabledScheme(cfg.getModelKey(), cfg.getNodeKey())) {
                        continue;
                    }
                    violations++;
                    log.error("A4 启动扫描发现不可解析节点：modelKey={} nodeKey={}({}) assign_type={} "
                                    + "——该节点运行时静默无人可办；请配置参与人方案或改用五种受支持类型，"
                                    + "修复后经「发布」路径生效",
                            cfg.getModelKey(), cfg.getNodeKey(), cfg.getNodeName(), type.trim());
                }
                if (violations == 0) {
                    log.info("A4 启动扫描完成：存活节点 {} 个，assign_type 全部可解析（0 违规）", all.size());
                } else {
                    log.error("A4 启动扫描完成：存活节点 {} 个，不可解析节点 {} 个（详见上方逐条 ERROR）",
                            all.size(), violations);
                }
            });
        } catch (Exception ex) {
            // 启动扫描自身失败不得影响应用启动（与 WfModelService.initSync 同一规则）
            log.warn("A4 启动扫描异常（不影响启动）: {}", ex.getMessage());
        }
    }
}
