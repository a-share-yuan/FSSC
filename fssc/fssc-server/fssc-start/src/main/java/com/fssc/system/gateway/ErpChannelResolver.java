// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.gateway;

import com.fssc.system.entity.ExtInterfaceConfig;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ERP 通道解析器（多 ERP 适配层，V1.4）。
 *
 * <p>业务诉求：未来对接的 ERP 不止一类，还可能是外部 ERP / 自研 ERP，
 * 业务代码不得硬编码 ERP 通道。本解析器的通道解析规则如下：</p>
 * <ul>
 *   <li><b>集成平台</b>：业务系统只投递消息到集成引擎，
 *       对端系统由「通信通道（Communication Channel）」配置决定，业务零感知；</li>
 *   <li><b>行业 ERP WebAPI</b>：统一登录 + 统一操作集（Save/Submit/Audit/ExecuteBillQuery），
 *       按组织/账套切换连接实例；</li>
 *   <li><b>ERP 开放接口</b>：统一 API 网关（app_key/app_secret 签名）+ 租户级路由。</li>
 * </ul>
 *
 * <p>落到 FSSC：租户在「外部接口配置」新增分类 {@code ERP}（每租户一条启用默认实例），
 * {@code provider} 声明 ERP 通道（取值由租户配置，含 CUSTOM），
 * {@code apiUrl/apiKey/apiSecret/extraConfig} 声明连接参数。业务代码只传<b>业务动作</b>，
 * 本解析器按「{PROVIDER}_{ACTION}」拼出实际通道分类：
 * 例如 provider=ERP_A、动作 AP → 通道分类 {@code ERP_A_AP}（此时在 ext_interface_config
 * 再配置一条 category=ERP_A_AP 的接口实例即可生效；未配置时网关默认使用模拟通道，行为安全）。</p>
 *
 * <p>兼容策略：租户未配置 ERP 实例时默认使用既有通道（蓝图默认对接对象），
 * 现网已配置的默认通道完全不受影响，历史行为零变化。</p>
 */
@Component
@RequiredArgsConstructor
public class ErpChannelResolver {

    /** ERP 实例配置分类（ext_interface_config.category，每租户一条启用默认） */
    public static final String CATEGORY_ERP = "ERP";

    /** 蓝图默认对接对象（未配置 ERP 实例时回落，保证历史行为不变） */
    public static final String DEFAULT_PROVIDER = "EBS";

    /** 支持的 ERP 品牌（与前端配置页下拉一致） */
    public static final Set<String> SUPPORTED_PROVIDERS =
            Set.of("EBS", "SAP", "YONYOU", "KINGDEE", "CUSTOM");

    private final IntegrationGateway integrationGateway;

    /**
     * 当前生效的 ERP 品牌（大写）。
     * 读取 ext_interface_config 中 category=ERP 的启用默认实例的 provider；
     * 未配置时返回 ERP（蓝图默认）。
     */
    public String activeProvider() {
        ExtInterfaceConfig cfg = integrationGateway.getConfig(CATEGORY_ERP);
        if (cfg != null && cfg.getProvider() != null && !cfg.getProvider().isBlank()) {
            return cfg.getProvider().toUpperCase();
        }
        return DEFAULT_PROVIDER;
    }

    /**
     * 是否已配置真实 ERP 实例（区别于蓝图回落）。
     * 未配置时业务侧应走 MOCK 落日志（离线可跑）。
     */
    public boolean hasErpInstance() {
        return integrationGateway.getConfig(CATEGORY_ERP) != null;
    }

    /**
     * 业务动作 → 实际通道分类（{PROVIDER}_{ACTION}）。
     *
     * @param bizAction 业务动作码，如 AP（三单匹配记账）/ AP_BLUE（蓝字专用）/ ORDER_GET（订单获取）
     * @return 通道分类，如 {PROVIDER}_AP / {PROVIDER}_ORDER_GET
     */
    public String resolve(String bizAction) {
        return activeProvider() + "_" + (bizAction == null ? "" : bizAction);
    }

    /**
     * 当前生效 ERP 实例的完整配置（可读 apiUrl/extraConfig 等连接参数）。
     * 未配置返回 null（调用方走 MOCK）。
     */
    public ExtInterfaceConfig activeInstance() {
        return integrationGateway.getConfig(CATEGORY_ERP);
    }
}
