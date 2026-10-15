package com.fssc.connector.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一连接响应（形态 B 契约层）。
 *
 * <p>核心字段对齐既有 {@code GatewayResponse}：{@code isOk() = success && statusCode == 200}。
 * 另带两个域语义字段：</p>
 * <ul>
 *   <li>{@code duplicate}：幂等命中回放——本次<b>未真实外呼</b>，直接回放首次结果；</li>
 *   <li>{@code retryable}：该次调用是否可安全重试，由能力域只读/变更属性决定。</li>
 * </ul>
 *
 * <p>本类刻意做成带 getter 的 POJO（而非 record），以便既有调用点的 {@code resp.getBody()}
 * 等取值方式零改动迁移。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResponse {

    /** 业务是否成功（HTTP 2xx 且通道未回错误信封） */
    private boolean success;

    /** HTTP 状态码（0 表示连接失败/未配置） */
    private int statusCode;

    /** 响应体原文 */
    private String body;

    /** 失败原因（连接失败或非 2xx） */
    private String error;

    /** 调用耗时（毫秒） */
    private long durationMs;

    /** 幂等命中回放：true 表示本次未真实外呼 */
    private boolean duplicate;

    /** 该次调用是否可安全重试（只读能力为 true） */
    private boolean retryable;

    /** HTTP 200 且业务成功。 */
    public boolean isOk() {
        return success && statusCode == 200;
    }

    /** 截断响应体供留痕（最多 4000 字符）。 */
    public String bodyForLog() {
        if (body == null) {
            return "";
        }
        return body.length() > 4000 ? body.substring(0, 4000) : body;
    }

    /** 连接失败 / 未配置 / 入参非法的兜底响应。 */
    public static ChannelResponse fail(String error) {
        return new ChannelResponse(false, 0, null, error, 0L, false, false);
    }
}
