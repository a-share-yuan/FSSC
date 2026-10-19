package com.fssc.connector.api;

/**
 * 统一连接请求（形态 B：业务侧只投递本对象，不感知厂商与协议）。
 *
 * <p>字段与既有 {@code IntegrationGateway} 语义一一对应，只是收敛到域契约：
 * {@code capability} 即 {@code ext_interface_config.category}（如 OCR / GOLDEN_TAX / EBS_AP），
 * {@code method} 为空按 POST，{@code payload} 交由通道序列化为 JSON。</p>
 */
public record ChannelRequest(String capability, ChannelType type, String method,
                             Object payload, String idempotencyKey) {

    private static final String POST = "POST";

    /** 规范化 method：空值按 POST，统一大写。 */
    public ChannelRequest {
        method = (method == null || method.isBlank()) ? POST : method.trim().toUpperCase();
    }

    /** 只读能力调用（POST + 业务体，无幂等键）。 */
    public static ChannelRequest read(String capability, Object payload) {
        return new ChannelRequest(capability, null, POST, payload, null);
    }

    /** 变更能力调用（POST + 业务体 + 幂等键）。 */
    public static ChannelRequest write(String capability, Object payload, String idempotencyKey) {
        return new ChannelRequest(capability, null, POST, payload, idempotencyKey);
    }

    /** 生效能力域：显式指定优先，否则由 capability 推导。 */
    public ChannelType resolvedType() {
        return type != null ? type : ChannelType.of(capability);
    }

    /** 是否变更类调用（无幂等键时应由治理层拒绝自动重试）。 */
    public boolean isMutating() {
        return resolvedType().isMutating();
    }

    /** 是否携带幂等键。 */
    public boolean hasIdempotencyKey() {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }
}
