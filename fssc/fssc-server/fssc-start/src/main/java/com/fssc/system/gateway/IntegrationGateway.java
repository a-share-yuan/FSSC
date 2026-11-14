package com.fssc.system.gateway;

import com.fssc.system.entity.ExtInterfaceConfig;

/**
 * Unified Integration Gateway abstraction ().
 *
 * <p>Replaces scattered HTTP calls in AiReviewService, OcrService, ExtInterfaceConfigService.testConnection()
 * with a single gateway that reads config from {@code ext_interface_config} by category, handles
 * authentication, timeout, retry, and logs every call to {@code integration_call_log}.</p>
 *
 * <p>Business services only depend on this interface; switching external providers is zero-touch
 * (just update ext_interface_config in the admin UI).</p>
 */
public interface IntegrationGateway {

    /**
     * POST JSON to the external interface configured under the given category.
     *
     * @param category ext_interface_config.category (AI_MODEL / OCR / INVOICE_VERIFY / ...)
     * @param body request body object (will be serialized to JSON)
     * @return gateway response with status, body, duration
     */
    GatewayResponse post(String category, Object body);

    /**
     * GET the external interface configured under the given category.
     *
     * @param category ext_interface_config.category
     * @return gateway response
     */
    GatewayResponse get(String category);

    /**
     * Get the active (enabled + default) interface config for a category.
     * Returns null if no enabled config exists (caller should handle gracefully).
     *
     * @param category ext_interface_config.category
     * @return config or null
     */
    ExtInterfaceConfig getConfig(String category);

    /**
     * Test connection to a specific ext_interface_config record (used by admin UI).
     * Performs a simple GET to the configured URL and returns the result.
     *
     * @param configId ext_interface_config.id
     * @return gateway response
     */
    GatewayResponse testConnection(Long configId);

    /**
     * 去重调用：带唯一键的外呼。同 (租户, 唯一键) 已有首次记录时不再外呼，
     * 直接回放首次结果，并落一条 {@code is_duplicate=1} 的记录。
     * 默认实现退化为无去重（{@code post(category, body)}），保持既有实现零改动。
     *
     * @param idempotencyKey {domain}:{tenantId}:{businessNo}[:{detail}]，为空则等价无去重
     */
    default GatewayResponse post(String category, Object body, String idempotencyKey) {
        return post(category, body);
    }

    /**
     * 失败外呼重投（供扫描 Job 调用）：对近期 success=0 且未达重试上限的记录逐个重投，
     * 返回本轮重投成功数。默认实现不重试，保持既有实现零改动。
     *
     * @param maxRetry 单条记录最大重投次数
     * @param lookbackHours 只处理最近 N 小时内失败的记录
     */
    default int retryFailed(int maxRetry, int lookbackHours) {
        return 0;
    }
}
