// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.connector.core;

import com.fssc.connector.adapter.ChannelAdapter;
import com.fssc.connector.adapter.ChannelAdapterRegistry;
import com.fssc.connector.api.ChannelRequest;
import com.fssc.connector.api.ChannelResponse;
import com.fssc.connector.api.ChannelType;
import com.fssc.connector.api.ConnectorFacade;
import com.fssc.connector.resilience.ResilienceGuard;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.system.gateway.ErpChannelResolver;
import com.fssc.system.gateway.GatewayResponse;
import com.fssc.system.gateway.IntegrationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 集成连接服务默认实现（形态 B <b>过渡适配层</b>）。
 *
 * <p>调用路由：先按能力域查 {@link ChannelAdapterRegistry}——已注册专属适配器的能力域
 * （如税局 {@code TaxInvoiceAdapter}）交给适配器；其余回落既有 {@code IntegrationGateway}
 * （已具备凭据托管、幂等、重试、超时、留痕能力）。业务侧改依赖 {@link ConnectorFacade} 即可，
 * 行为与既有完全一致（零回归）。</p>
 *
 * <p><b>非破坏保证</b>：本类只读既有网关/解析器的公开返回值并做字段搬运，不改变任何鉴权头语义、
 * 错误识别、超时或重试逻辑。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultConnectorFacade implements ConnectorFacade {

    private final IntegrationGateway gateway;
    private final ErpChannelResolver erpChannelResolver;
    private final ChannelAdapterRegistry adapterRegistry;
    private final ResilienceGuard resilience;

    @Override
    public ChannelResponse invoke(ChannelRequest request) {
        if (request == null || request.capability() == null || request.capability().isBlank()) {
            return ChannelResponse.fail("capability 不能为空");
        }
        String capability = request.capability();
        if (!resilience.allow(capability)) {
            return ChannelResponse.fail("连接治理拦截(" + capability + ")：熔断或限流中，请稍后重试");
        }
        ChannelType type = request.resolvedType();
        ChannelAdapter adapter = adapterRegistry.find(type);
        ChannelResponse resp;
        if (adapter != null) {
            resp = adapter.invoke(request);
        } else {
            GatewayResponse r = "GET".equals(request.method())
                    ? gateway.get(capability)
                    : gateway.post(capability, request.payload(), request.idempotencyKey());
            resp = ChannelResponseMapper.adapt(r, type);
        }
        if (resp != null && resp.isOk()) {
            resilience.recordSuccess(capability);
        } else {
            resilience.recordFailure(capability);
        }
        return resp;
    }

    @Override
    public ChannelResponse invokeRead(String capability, Object payload) {
        return invoke(ChannelRequest.read(capability, payload));
    }

    @Override
    public ChannelResponse invokeWrite(String capability, Object payload, String idempotencyKey) {
        return invoke(ChannelRequest.write(capability, payload, idempotencyKey));
    }

    @Override
    public boolean isConfigured(String capability) {
        return capability != null && !capability.isBlank() && gateway.getConfig(capability) != null;
    }

    @Override
    public ExtInterfaceConfig getConfig(String capability) {
        return gateway.getConfig(capability);
    }

    @Override
    public boolean hasErpInstance() {
        return erpChannelResolver.hasErpInstance();
    }

    @Override
    public String resolveErpChannel(String bizAction) {
        return erpChannelResolver.resolve(bizAction);
    }
}
