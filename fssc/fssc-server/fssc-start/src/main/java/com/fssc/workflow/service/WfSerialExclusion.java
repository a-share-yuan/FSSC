// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysConfigMapper;
import com.fssc.system.mapper.SysUserMapper;
import com.fssc.workflow.entity.WfNodeConfig;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

/**
 * 同一流程实例内「谁不能办当前节点」的唯一判据源。
 *
 * <p>改造前存在两套口径：派发侧 {@code NodeOrgResolver#preferFreshHandlers} 读 {@code wf_task}
 * 镜像表且只做软重排，而 {@code WfEngineFacade#complete} 的连办总闸与节点互斥组闸读
 * {@code act_hi_taskinst} 并直接拒办。AUTO 节点按 id 升序把同一人派到多个节点后，
 * 用户点「通过」才收到 403，此时 assignee 已写死、同池他人领不到，节点自锁只能靠转办救火。
 *
 * <p>本组件把「整链连办闸」与「节点互斥组闸」的判据收敛到一处，供两处共用：
 * <ul>
 *  <li>{@link #blockedUsers} —— 派发侧（AUTO 取人）在指派<b>之前</b>硬剔除必然被拒的人；</li>
 *  <li>{@link #serialDenied} / {@link #handledInGroup} / {@link #hasHandledInInstance}
 *      —— {@code WfEngineFacade} 原有校验与预检的委托实现，行为逐字不变。</li>
 * </ul>
 *
 * <p>刻意<b>不做</b>的事：不实现「连岗自动跳过／自动去重」。互斥组（如「复核↔制证」、
 * 「付款编制↔执行」）是故意设置的岗位分离，自动跳过等于把复核环节废掉，内控风险高于 403。
 * 这里只做「换人／落到空策略兜底」，不改变任何人的办理资格与审批结果。
 *
 * <p>成本：绝大多数节点既不在连办闸作用域、也没有互斥组，{@link #blockedUsers} 直接返回空集，
 * 不查历史表；只有命中约束的节点才读 {@code act_hi_taskinst}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfSerialExclusion {

    public static final String CONFIG_KEY = "approve.deny_serial_same_handler";

    /** 仅在 sys_config 读取异常时作为 fail-closed 收窄依据，与改造前 WfEngineFacade 同值 */
    private static final Set<String> ENFORCED_BIZTYPES = Set.of("GL_MONTH_CLOSE");

    private final HistoryService historyService;
    private final WfNodeConfigResolver wfNodeConfigResolver;
    private final SysConfigMapper sysConfigMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 连办总闸开关：与 {@code complete()} 内 {@code isSerialSameHandlerDenied} 逐字同一。
     * 缺行 fail-closed 全量拦截；空/0 不拦；1/ALL 全量；逗号分隔 bizType 白名单；
     * 读取异常只对代码层显式登记的链拦截，避免一次 DB 抖动把全量在途审批打成 403。
     */
    public boolean serialDenied(String bizType) {
        if (bizType == null) {
            return false;
        }
        try {
            SysConfig c = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, CONFIG_KEY)
                    .last("limit 1"));
            if (c == null || c.getConfigValue() == null) {
                log.warn("{} 无配置行，按统一缺省方向 fail-closed 拦截 bizType={}", CONFIG_KEY, bizType);
                return true;
            }
            String v = c.getConfigValue().trim();
            if (v.isEmpty() || "0".equals(v)) {
                return false;
            }
            if ("1".equalsIgnoreCase(v) || "ALL".equalsIgnoreCase(v)) {
                return true;
            }
            for (String t : v.split(",")) {
                if (t.trim().equalsIgnoreCase(bizType)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.error("读取 {} 异常，fail-closed 仅对显式登记流程链拦截 bizType={}: {}",
                    CONFIG_KEY, bizType, ex.getMessage(), ex);
            return ENFORCED_BIZTYPES.contains(bizType);
        }
    }

    /**
     * 本实例已办结节点的用户集合。{@code excludeNodeKey} 非空时排除该 defKey 的历史行
     * ——与 {@code complete()} 里「同节点重办放行」的过滤逐字同一。
     *
     * <p>数据源固定为 {@code act_hi_taskinst}（Flowable 权威历史），不读 {@code wf_task} 镜像：
     * 镜像开关关闭或双写失败时可能缺行，会造成「预检放行、审批被拒」。
     */
    public Set<Long> handledUsers(String procInstId, String excludeNodeKey) {
        Set<Long> out = new LinkedHashSet<>();
        if (procInstId == null) {
            return out;
        }
        try {
            for (HistoricTaskInstance h : EngineTenantScope.historicTaskQuery(historyService)
                    .processInstanceId(procInstId).finished().list()) {
                if (excludeNodeKey != null && Objects.equals(h.getTaskDefinitionKey(), excludeNodeKey)) {
                    continue;
                }
                Long uid = assigneeId(h);
                if (uid != null) {
                    out.add(uid);
                }
            }
        } catch (Exception ex) {
            log.warn("本实例已办人查询失败 proc={}: {}", procInstId, ex.getMessage());
        }
        return out;
    }

    /**
     * 本实例内已办过「同互斥组其他节点」的人：供 {@code WfEngineFacade#handledInSameSerialGroup}
     * 与派发侧共用，判据与 {@code complete()} 的互斥组硬闸一致。
     */
    public Set<Long> handledInGroup(String modelKey, String procInstId, String nodeKey, String group) {
        Set<Long> out = new LinkedHashSet<>();
        if (group == null || group.isBlank() || procInstId == null) {
            return out;
        }
        try {
            for (HistoricTaskInstance h : EngineTenantScope.historicTaskQuery(historyService)
                    .processInstanceId(procInstId).finished().list()) {
                if (Objects.equals(h.getTaskDefinitionKey(), nodeKey)) {
                    continue;
                }
                WfNodeConfig c = wfNodeConfigResolver.resolve(modelKey, h.getTaskDefinitionKey());
                if (c != null && group.equals(c.getSerialExclusiveGroup())) {
                    Long uid = assigneeId(h);
                    if (uid != null) {
                        out.add(uid);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("互斥组已办人查询失败 proc={} group={}: {}", procInstId, group, ex.getMessage());
        }
        return out;
    }

    /**
     * L1 硬约束：不能办理当前节点的人 = 整链连办闸命中者 ∪ 同互斥组已办者。
     *
     * <p>系统自动审核身份（sysauto）与两道硬闸一样豁免。返回空集表示无约束，调用方行为不变。
     */
    public Set<Long> blockedUsers(String modelKey, String bizType, String procInstId, String nodeKey) {
        Set<Long> out = new LinkedHashSet<>();
        if (procInstId == null) {
            return out;
        }
        boolean serial = serialDenied(bizType);
        WfNodeConfig cur = nodeKey == null ? null : wfNodeConfigResolver.resolve(modelKey, nodeKey);
        String group = cur == null ? null : cur.getSerialExclusiveGroup();
        boolean groupOn = group != null && !group.isBlank();
        if (!serial && !groupOn) {
            return out;
        }
        if (serial) {
            for (Long uid : handledUsers(procInstId, nodeKey)) {
                if (!isSystemAutoAuditor(uid)) {
                    out.add(uid);
                }
            }
        }
        if (groupOn) {
            for (Long uid : handledInGroup(modelKey, procInstId, nodeKey, group)) {
                if (!isSystemAutoAuditor(uid)) {
                    out.add(uid);
                }
            }
        }
        return out;
    }

    /**
     * 该用户在本实例办结过任何节点？供 {@code WfEngineFacade#handledNodesInInstance} 委托，
     * 与连办校验 {@code count() > 0} 逐字同一；读取失败按「没办过」处理（只放宽推送范围）。
     */
    public boolean hasHandledInInstance(String procInstId, Long userId) {
        if (procInstId == null || userId == null) {
            return false;
        }
        try {
            return EngineTenantScope.historicTaskQuery(historyService)
                    .processInstanceId(procInstId)
                    .taskAssignee(String.valueOf(userId)).finished().count() > 0;
        } catch (Exception ex) {
            log.debug("连办预检查历史失败 procInstId={} userId={}: {}", procInstId, userId, ex.getMessage());
            return false;
        }
    }

    private boolean isSystemAutoAuditor(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            SysUser u = sysUserMapper.selectById(userId);
            return u != null && "sysauto".equals(u.getUserName());
        } catch (Exception ex) {
            return false;
        }
    }

    private static Long assigneeId(HistoricTaskInstance h) {
        String uid = h.getAssignee();
        if (uid == null || !uid.trim().matches("\\d+")) {
            return null;
        }
        return Long.parseLong(uid.trim());
    }
}
