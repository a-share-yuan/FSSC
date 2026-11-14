package com.fssc.connector.core;

import com.fssc.connector.adapter.ChannelAdapter;
import com.fssc.connector.adapter.ChannelAdapterRegistry;
import com.fssc.connector.api.ChannelRequest;
import com.fssc.connector.api.ChannelResponse;
import com.fssc.connector.api.ChannelType;
import com.fssc.connector.resilience.ResilienceGuard;
import com.fssc.system.entity.ExtInterfaceConfig;
import com.fssc.system.gateway.ErpChannelResolver;
import com.fssc.system.gateway.GatewayResponse;
import com.fssc.system.gateway.IntegrationGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultConnectorFacade} 契约适配单测：不依赖 Spring / DB，用桩网关验证路由、
 * 幂等键透传、可重试标记、配置读取、ERP 通道解析与适配器路由。
 */
class DefaultConnectorFacadeTest {

    /** 记录调用并返回预设响应的桩网关。 */
    private static final class StubGateway implements IntegrationGateway {
        String lastCategory;
        Object lastBody;
        String lastIdemKey;
        String lastGetCategory;
        boolean postCalled;
        boolean getCalled;
        GatewayResponse response = new GatewayResponse(200, "{\"ok\":true}", 12L, true, null);
        ExtInterfaceConfig config;
        /** 非空时仅对该分类返回 config，用于验证「无配置分类返回 null」 */
        String configCategory;

        @Override
        public GatewayResponse post(String category, Object body) {
            return post(category, body, null);
        }

        @Override
        public GatewayResponse post(String category, Object body, String idempotencyKey) {
            this.postCalled = true;
            this.lastCategory = category;
            this.lastBody = body;
            this.lastIdemKey = idempotencyKey;
            return response;
        }

        @Override
        public GatewayResponse get(String category) {
            this.getCalled = true;
            this.lastGetCategory = category;
            return response;
        }

        @Override
        public ExtInterfaceConfig getConfig(String category) {
            if (config == null) {
                return null;
            }
            return configCategory == null || configCategory.equals(category) ? config : null;
        }

        @Override
        public GatewayResponse testConnection(Long configId) {
            return response;
        }
    }

    /** 记录一次调用的适配器桩。 */
    private static final class RecordingAdapter implements ChannelAdapter {
        private final ChannelType type;
        ChannelRequest seen;

        RecordingAdapter(ChannelType type) {
            this.type = type;
        }

        @Override
        public ChannelType type() {
            return type;
        }

        @Override
        public ChannelResponse invoke(ChannelRequest request) {
            this.seen = request;
            return new ChannelResponse(true, 200, "{\"via\":\"adapter\"}", null, 1L, false, false);
        }
    }

    private static DefaultConnectorFacade facade(StubGateway gw) {
        return new DefaultConnectorFacade(gw, new ErpChannelResolver(gw),
                new ChannelAdapterRegistry(List.of()), new ResilienceGuard(false, 5, 30, 0));
    }

    @Test
    void invokeRead_mapsToPost_withoutIdempotencyKey_andMarksRetryable() {
        StubGateway gw = new StubGateway();

        ChannelResponse r = facade(gw).invokeRead("OCR", Map.of("file", "a.jpg"));

        assertTrue(gw.postCalled, "只读调用应走 POST");
        assertEquals("OCR", gw.lastCategory);
        assertNull(gw.lastIdemKey, "只读调用不应带幂等键");
        assertTrue(r.isOk());
        assertTrue(r.isRetryable(), "OCR 为只读能力，应标记可重试");
        assertFalse(r.isDuplicate());
    }

    @Test
    void invokeWrite_carriesIdempotencyKey_andMutatingNotRetryable() {
        StubGateway gw = new StubGateway();

        ChannelResponse r = facade(gw).invokeWrite("EBS_AP", Map.of("no", "P1"), "SCP_AP:1:P1");

        assertEquals("EBS_AP", gw.lastCategory);
        assertEquals("SCP_AP:1:P1", gw.lastIdemKey, "变更调用应透传幂等键");
        assertFalse(r.isRetryable(), "ERP 记账为变更类，不应标记可重试");
    }

    @Test
    void invoke_getMethod_routesToGet() {
        StubGateway gw = new StubGateway();

        facade(gw).invoke(new ChannelRequest("EBS_ORDER_GET", null, "GET", null, null));

        assertTrue(gw.getCalled, "GET 方法应路由到网关 get");
        assertEquals("EBS_ORDER_GET", gw.lastGetCategory);
    }

    @Test
    void invoke_blankCapability_returnsFail() {
        ChannelResponse r = facade(new StubGateway()).invoke(new ChannelRequest("   ", null, "POST", null, null));

        assertFalse(r.isOk());
        assertFalse(r.isRetryable());
    }

    @Test
    void invoke_routesToRegisteredAdapter_withoutTouchingGateway() {
        StubGateway gw = new StubGateway();
        RecordingAdapter tax = new RecordingAdapter(ChannelType.TAX_INVOICE);
        DefaultConnectorFacade facade = new DefaultConnectorFacade(
                gw, new ErpChannelResolver(gw), new ChannelAdapterRegistry(List.of(tax)),
                new ResilienceGuard(false, 5, 30, 0));

        ChannelResponse r = facade.invokeWrite("GOLDEN_TAX", Map.of("no", "P1"), "k1");

        assertNotNull(tax.seen, "税局能力域应路由到专属适配器");
        assertEquals("GOLDEN_TAX", tax.seen.capability());
        assertEquals("k1", tax.seen.idempotencyKey());
        assertEquals("{\"via\":\"adapter\"}", r.getBody());
        assertFalse(gw.postCalled, "路由到适配器时不应再走通用网关");
    }

    @Test
    void isConfigured_reflectsGatewayConfig() {
        StubGateway gw = new StubGateway();
        DefaultConnectorFacade facade = facade(gw);

        assertFalse(facade.isConfigured("OCR"), "无配置时应为 false");
        assertFalse(facade.isConfigured(null));

        gw.config = new ExtInterfaceConfig();
        assertTrue(facade.isConfigured("OCR"));
    }

    @Test
    void getConfig_returnsRawConfig_andNullWhenAbsent() {
        StubGateway gw = new StubGateway();
        ExtInterfaceConfig cfg = new ExtInterfaceConfig();
        cfg.setProvider("BAIDU");
        cfg.setModelName("ERNIE-4.0");
        gw.config = cfg;
        gw.configCategory = "AI_MODEL";
        DefaultConnectorFacade facade = facade(gw);

        ExtInterfaceConfig got = facade.getConfig("AI_MODEL");
        assertNotNull(got);
        assertEquals("BAIDU", got.getProvider());
        assertEquals("ERNIE-4.0", got.getModelName());
        assertNull(facade.getConfig("VOUCHER"));
    }

    @Test
    void resolveErpChannel_defaultsToEbs_andReflectsConfiguredProvider() {
        StubGateway gw = new StubGateway();
        DefaultConnectorFacade facade = facade(gw);

        assertFalse(facade.hasErpInstance());
        assertEquals("EBS_AP", facade.resolveErpChannel("AP"), "未配置 ERP 实例时回落 EBS");

        ExtInterfaceConfig erp = new ExtInterfaceConfig();
        erp.setProvider("SAP");
        gw.config = erp;
        assertTrue(facade.hasErpInstance());
        assertEquals("SAP_ORDER_GET", facade.resolveErpChannel("ORDER_GET"));
    }
}
