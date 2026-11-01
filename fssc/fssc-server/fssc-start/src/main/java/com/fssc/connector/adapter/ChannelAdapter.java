// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.connector.adapter;

import com.fssc.connector.api.ChannelRequest;
import com.fssc.connector.api.ChannelResponse;
import com.fssc.connector.api.ChannelType;
import com.fssc.connector.api.ConnectorFacade;

/**
 * 通道适配 SPI（形态 B）。
 *
 * <p>每个能力域（税局 / 银行 / ERP / OCR / 凭证推送）一个实现，封装该域专属的报文构造、
 * 签名、协议与错误处理细节。业务包<b>不得</b>直接依赖本包（由 CI 规则 9 强制）；
 * 业务只经 {@code ConnectorFacade} 调用。</p>
 *
 * <p>未提供适配器的能力域，由 {@code DefaultConnectorFacade} 直接走通用网关（行为不变）。</p>
 */
public interface ChannelAdapter {

    /** 本适配器负责的能力域。 */
    ChannelType type();

    /** 执行一次调用。实现不得向调用方抛穿异常，失败以 {@link ChannelResponse#isOk()}=false 返回。 */
    ChannelResponse invoke(ChannelRequest request);
}
