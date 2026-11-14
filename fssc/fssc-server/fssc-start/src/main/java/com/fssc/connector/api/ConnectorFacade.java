package com.fssc.connector.api;

import com.fssc.system.entity.ExtInterfaceConfig;

/**
 * 集成连接服务统一入口（形态 B）。
 *
 * <p>业务侧只依赖本接口，不感知具体通道/协议。所有外呼（OCR / 银企 / 税局 / ERP / 凭证推送）
 * 都应经由 {@link #invoke(ChannelRequest)} 或便捷方法进入，由连接域统一负责：</p>
 * <ul>
 *   <li>凭据托管（从 {@code ext_interface_config} 读配置，凭据真值只在连接域内流转）；</li>
 *   <li>去重 / 重试 / 超时 / 记录（底层网关实现，契约已外露 {@code duplicate}/{@code retryable}）；</li>
 *   <li>能力分类与治理规则（只读可重试、变更需唯一键）；</li>
 *   <li>多 ERP 通道解析（业务只传业务动作，不硬编码 ERP 品牌）。</li>
 * </ul>
 */
public interface ConnectorFacade {

    /** 统一调用入口：按请求的能力域与 method 路由到底层通道。 */
    ChannelResponse invoke(ChannelRequest request);

    /** 只读能力便捷调用（无副作用，可安全重试）。 */
    ChannelResponse invokeRead(String capability, Object payload);

    /** 变更能力便捷调用（有外部副作用，调用方应携带唯一键）。 */
    ChannelResponse invokeWrite(String capability, Object payload, String idempotencyKey);

    /** 该能力是否已配置可用的真实通道（未配置时业务侧走 MOCK/兜底）。 */
    boolean isConfigured(String capability);

    /**
     * 读取该能力当前生效通道配置；未配置返回 {@code null}。
     * 语义与既有 {@code IntegrationGateway.getConfig} 一致（引擎侧需读取 modelName / status 等真实字段）。
     */
    ExtInterfaceConfig getConfig(String capability);

    /** 是否已配置真实 ERP 实例（未配置时业务侧应走 MOCK）。 */
    boolean hasErpInstance();

    /**
     * 业务动作 → ERP 实际通道分类（{@code {PROVIDER}_{ACTION}}）。
     * 业务代码只传业务动作（如 AP / ORDER_GET），不感知 ERP 品牌。
     */
    String resolveErpChannel(String bizAction);
}
