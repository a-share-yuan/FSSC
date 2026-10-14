// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.connector.core;

import com.fssc.connector.api.ChannelResponse;
import com.fssc.connector.api.ChannelType;
import com.fssc.system.gateway.GatewayResponse;

/**
 * 共享网关响应 → 域契约响应的唯一映射点（避免多处重复搬运字段）。
 */
public final class ChannelResponseMapper {

    private ChannelResponseMapper() {
    }

    /** 把共享网关响应适配为域契约响应，不改变任何既有语义。 */
    public static ChannelResponse adapt(GatewayResponse r, ChannelType type) {
        if (r == null) {
            return ChannelResponse.fail("通道返回为空");
        }
        ChannelResponse cr = new ChannelResponse();
        cr.setSuccess(r.isSuccess());
        cr.setStatusCode(r.getStatusCode());
        cr.setBody(r.getBody());
        cr.setError(r.getError());
        cr.setDurationMs(r.getDurationMs());
        cr.setDuplicate(r.isDuplicate());
        cr.setRetryable(type != null && type.isReadOnly());
        return cr;
    }
}
