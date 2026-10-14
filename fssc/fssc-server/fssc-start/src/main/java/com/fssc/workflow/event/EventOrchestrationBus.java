// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.workflow.entity.WfEventActionConfig;
import com.fssc.workflow.mapper.WfEventActionConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件编排总线（P0-1）。
 * <p>
 * 替代散落在各业务域的 {@code @EventListener}，作为 {@link WfProcessEndedEvent} 的
 * <b>唯一统一监听入口</b>，按 bizType 策略路由到对应的 {@link WfBizAction} 收尾动作。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>表驱动配置：{@code wf_event_action_config} 记录 bizType → actionBean 映射、
 *       触发时机（APPROVED/REJECTED/BOTH）、排序、启用/禁用</li>
 *   <li>自动发现：启动时扫描所有 {@link WfBizAction} Spring Bean，与配置表交叉校验</li>
 *   <li>零代码接入：新流程仅需在配置表 INSERT 一行 + 实现 WfBizAction Bean</li>
 *   <li>原子化执行：每个 Action 独立 try-catch，单个失败不中断其他动作</li>
 * </ul>
 *
 * @see WfBizAction
 * @see WfProcessEndedEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventOrchestrationBus {

    private final ApplicationContext applicationContext;
    private final WfEventActionConfigMapper configMapper;

    /** 分发映射表：bizType → 有序 Action 列表（启动时构建，只读） */
    private final Map<String, List<ActionEntry>> dispatchMap = new ConcurrentHashMap<>();

    /** 所有已注册的 WfBizAction Bean：beanName → 实例 */
    private Map<String, WfBizAction> actionBeans = Collections.emptyMap();

    // ------------------------------------------------------------------ init

    @PostConstruct
    public void init() {
        refreshDispatchMap();
    }

    /**
     * 刷新分发映射表（可在运行时调用以热加载新配置）。
     * <p>
     * 逻辑：
     * 1. 扫描 Spring 容器中所有 WfBizAction Bean
     * 2. 查询 wf_event_action_config 表（enabled=1）
     * 3. 按 bizType 分组，按 sortOrder 升序排序
     * 4. 若某 bizType 一条显式行都没有 → 由声明它的 Bean 按默认配置自动注册
     *    （判定阶＝bizType；一个 Bean 只要部分 bizType 有显式行，其余 bizType 仍会拿到兜底腿）
     */
    public synchronized void refreshDispatchMap() {
        // 1. 扫描所有 WfBizAction Bean
        Map<String, WfBizAction> beans = new LinkedHashMap<>();
        for (Map.Entry<String, WfBizAction> entry : applicationContext.getBeansOfType(WfBizAction.class).entrySet()) {
            beans.put(entry.getKey(), entry.getValue());
        }
        actionBeans = Collections.unmodifiableMap(beans);

        // 2. 查询配置表（启动期无租户上下文，用 ignore 模式查全量配置）
        final List<WfEventActionConfig> configs = new ArrayList<>();
        TenantContext.runIgnoreTenant(() -> {
            configs.addAll(configMapper.selectList(new LambdaQueryWrapper<WfEventActionConfig>()
                    .eq(WfEventActionConfig::getEnabled, 1)));
        });

        // 3. 按 bizType 分组
        Map<String, List<ActionEntry>> map = new HashMap<>();
        for (WfEventActionConfig cfg : configs) {
            WfBizAction action = beans.get(cfg.getActionBean());
            if (action == null) {
                log.warn("EventOrchestrationBus: actionBean '{}' 不存在于 Spring 容器（bizType={}），跳过", cfg.getActionBean(), cfg.getBizType());
                continue;
            }
            if (!action.bizTypes().contains(cfg.getBizType())) {
                log.warn("EventOrchestrationBus: actionBean '{}' 的 bizTypes()={} 不包含 {}，跳过", cfg.getActionBean(), action.bizTypes(), cfg.getBizType());
                continue;
            }
            map.computeIfAbsent(cfg.getBizType(), k -> new ArrayList<>())
                    .add(new ActionEntry(cfg.getActionBean(), action, cfg.getTriggerWhen(), cfg.getSortOrder()));
        }

        // 4. 对配置表尚未覆盖的 bizType，按默认配置自动注册。
        //    判定阶＝bizType，不是 bean：本方法上方已建好显式 map，此处只为"还没有任何一条腿"的
        //    bizType 兜底。旧实现按 bean 名整体 continue，使"半配置 bean"（有若干有效行、另一些
        //    bizType 漏配）声明的其余 bizType 既拿不到显式行也拿不到自动注册，收尾动作数静默归零；
        //    同时它又会让"全无配置的 bean"与"显式行"叠在同一个 bizType 上造成双发。
        //    bean 名排序遍历＝同一 bizType 被多个未配置 bean 声明时归属可复现。
        for (String beanName : new TreeSet<>(beans.keySet())) {
            WfBizAction action = beans.get(beanName);
            for (String bizType : action.bizTypes()) {
                if (map.containsKey(bizType)) {
                    // 已有显式行或已有兜底腿 ⇒ 不覆盖、不叠加（防双发）
                    continue;
                }
                map.computeIfAbsent(bizType, k -> new ArrayList<>())
                        .add(new ActionEntry(beanName, action, "BOTH", 999));
                log.info("EventOrchestrationBus: 自动注册 bizType={} → actionBean={}（默认配置）", bizType, beanName);
            }
        }

        // 5. 每个 bizType 内按 sortOrder 升序排序
        for (List<ActionEntry> list : map.values()) {
            list.sort(Comparator.comparingInt(e -> e.sortOrder));
        }

        dispatchMap.clear();
        dispatchMap.putAll(map);

        int bizTypeCount = map.size();
        int actionCount = map.values().stream().mapToInt(List::size).sum();
        log.info("EventOrchestrationBus 初始化完成: {} 个 bizType, {} 个动作配置", bizTypeCount, actionCount);
    }

    // ------------------------------------------------------------- dispatch

    /**
     * 统一事件监听入口（Spring @EventListener）。
     * <p>
     * 接收 {@link WfProcessEndedEvent}，按 bizType 查分发映射表，
     * 按 triggerWhen 过滤后逐个执行收尾动作。
     * 每个 Action 独立 try-catch，单个失败仅记录日志不中断其他动作。
     */
    @EventListener
    public void onProcessEnded(WfProcessEndedEvent event) {
        List<ActionEntry> entries = dispatchMap.get(event.getBizType());
        if (entries == null || entries.isEmpty()) {
            log.debug("EventOrchestrationBus: bizType={} 无收尾动作配置，跳过", event.getBizType());
            return;
        }
        boolean approved = Boolean.TRUE.equals(event.getApproved());
        // P0-1 架构修复（铁律）：流程结束事件由 Flowable 引擎线程异步发布，
        // 不继承发起请求的租户上下文（TenantContext 为线程级 ThreadLocal）。
        // 收尾动作涉及业务表 UPDATE/INSERT（含凭证 op_voucher 的 tenant_id/company_id NOT NULL），
        // 必须在正确的租户上下文内执行，否则：
        //   - TenantContext.getTenantId() 因 fail-closed 抛 TENANT_CONTEXT_MISSING（凭证生成失败）
        //   - 或 MP 租户拦截器在无上下文时跳过 WHERE，造成串户风险。
        // 故在此据事件携带的 tenantId 重建上下文；tenantId 缺失则用 runIgnoreTenant 兜底（仍不串户）。
        Long tenantId = event.getTenantId();
        Runnable dispatch = () -> {
            for (ActionEntry entry : entries) {
                if (!entry.shouldTrigger(approved)) {
                    continue;
                }
                try {
                    entry.action.execute(event);
                } catch (Exception ex) {
                    log.error("EventOrchestrationBus: 收尾动作执行失败 bizType={}, bean={}, error={}",
                            event.getBizType(), entry.beanName, ex.getMessage(), ex);
                }
            }
        };
        if (tenantId != null) {
            TenantContext.callAs(tenantId, dispatch);
        } else {
            log.warn("EventOrchestrationBus: bizType={} 事件未携带 tenantId，以 runIgnoreTenant 兜底执行（请核查 WfEngineFacade 填充）",
                    event.getBizType());
            TenantContext.runIgnoreTenant(dispatch);
        }
    }

    // ------------------------------------------------------------- helpers

    /** 获取当前分发映射表快照（只读，供管理接口查看） */
    public Map<String, List<ActionEntry>> getDispatchMap() {
        return Collections.unmodifiableMap(dispatchMap);
    }

    /**
     * 单个动作配置条目。
     */
    public static class ActionEntry {
        public final String beanName;
        public final WfBizAction action;
        public final String triggerWhen;
        public final int sortOrder;

        ActionEntry(String beanName, WfBizAction action, String triggerWhen, Integer sortOrder) {
            this.beanName = beanName;
            this.action = action;
            this.triggerWhen = triggerWhen != null ? triggerWhen : "BOTH";
            this.sortOrder = sortOrder != null ? sortOrder : 0;
        }

        boolean shouldTrigger(boolean approved) {
            if ("BOTH".equals(triggerWhen)) return true;
            if ("APPROVED".equals(triggerWhen)) return approved;
            if ("REJECTED".equals(triggerWhen)) return !approved;
            return true;
        }
    }
}
