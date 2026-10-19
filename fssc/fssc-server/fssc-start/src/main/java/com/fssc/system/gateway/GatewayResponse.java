// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.gateway;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Unified response from IntegrationGateway calls.
 * Carries HTTP status, body, duration, and error info.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GatewayResponse {

    /** HTTP status code (0 if connection failed) */
    private int statusCode;

    /** Response body as string */
    private String body;

    /** Call duration in ms */
    private long durationMs;

    /** true if HTTP 2xx */
    private boolean success;

    /** Error message if connection failed or non-2xx */
    private String error;

    /** P0-2 幂等命中回放：true 表示本次未真实外呼，直接回放首次结果 */
    private boolean duplicate;

    /**
     * 5 参构造（保持既有 10 处调用点零改动）：{@code duplicate} 默认 false。
     * {@code @AllArgsConstructor} 仍生成 6 参构造供需要显式置位的调用方使用。
     */
    public GatewayResponse(int statusCode, String body, long durationMs, boolean success, String error) {
        this(statusCode, body, durationMs, success, error, false);
    }

    /** Convenience: response is HTTP 200 */
    public boolean isOk() {
        return success && statusCode == 200;
    }

    /** Truncate body for logging (max 4000 chars) */
    public String bodyForLog() {
        if (body == null) return "";
        return body.length() > 4000 ? body.substring(0, 4000) : body;
    }
}
