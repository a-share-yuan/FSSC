// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.connector.adapter.tax;

import com.fssc.connector.adapter.ChannelAdapter;
import com.fssc.connector.api.ChannelRequest;
import com.fssc.connector.api.ChannelResponse;
import com.fssc.connector.api.ChannelType;
import com.fssc.connector.core.ChannelResponseMapper;
import com.fssc.system.gateway.GatewayResponse;
import com.fssc.system.gateway.IntegrationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 税务通道适配器（形态 B · 域2）。
 *
 * <p><b>本类只做通道语义，不做报文翻译。</b>四组能力（识别 / 查验 / 认证抵扣 / 数电开票）
 * 已由 <b>InvoiceGateway</b> 封装为自家中性契约 {@code /api/tax/v1/**}；本类对应的
 * {@link ChannelType#TAX_INVOICE} 就是该网关通道在连接器里的注册点。</p>
 *
 * <p><b>职责边界（严格，对齐 InvoiceGateway 交付说明的三条不变量）</b>：
 * <ol>
 *   <li><b>报文形态归网关</b>：本类<b>原样透传</b>业务侧 payload（中性契约字段，如
 *       {@code orderNumber}/{@code invoiceTypeCode}/{@code seller}/{@code buyer}/{@code lines}），
 *       <b>不翻译成任何上游格式</b>——企享云/乐企的报文与签名只在网关进程内存在；</li>
 *   <li><b>上游凭证归网关</b>：接口密钥（{@code QXY_APP_KEY} 等）与乐企税控盘证书
 *       <b>绝不进入 FSSC</b> 的代码/配置/日志/数据库；本类只经
 *       {@code ext_interface_config} 的通道配置指向网关地址；</li>
 *   <li><b>无凭据不给结论</b>：网关缺上游凭据时返回 <b>503 {@code UPSTREAM_NOT_CONFIGURED}</b>，
 *       本类必须原样透传为失败，<b>不得</b>回落 mock 票面、不得写 PASS/SUCCESS/CERTIFIED。</li>
 * </ol>
 *
 * <p><b>关于乐企</b>：乐企是 InvoiceGateway 的<b>第二上游</b>（第一上游为企享云/其象云），
 * 其 XML 报文、签名验签、税控盘交互全部在网关内部实现。<b>业务系统无感</b>——
 * 新增/切换上游只改网关内部，业务侧只加一条 {@code ext_interface_config} 通道配置。</p>
 *
 * <p><b>不得抛穿异常</b>（{@link ChannelAdapter} 契约）：税务失败以 {@code isOk()=false} 返回，
 * 由业务侧决定标失败或人工复核；请求/响应原文经网关记录于 {@code integration_call_log}，
 * 作为失败时的证据链。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaxInvoiceAdapter implements ChannelAdapter {

    private final IntegrationGateway gateway;

    @Override
    public ChannelType type() {
        return ChannelType.TAX_INVOICE;
    }

    @Override
    public ChannelResponse invoke(ChannelRequest request) {
        String capability = request.capability();
        try {
            // payload 原样透传：报文与签名归网关，业务侧只提供中性契约字段。
            GatewayResponse r = gateway.post(capability, request.payload(), request.idempotencyKey());

            ChannelResponse resp = ChannelResponseMapper.adapt(r, type());
            if (resp != null && !resp.isOk()) {
                // 缺上游凭据（503 UPSTREAM_NOT_CONFIGURED）与业务失败同样透传，
                // 只记日志不默认处理：不伪造票面、不给正向结论（对齐交付说明不变量 3）。
                log.warn("[税务] 通道调用未成功 capability={} status={} key={} err={}",
                        capability, resp.getStatusCode(), request.idempotencyKey(), resp.getError());
            }
            return resp;
        } catch (Exception e) {
            log.error("[税务] 通道调用异常 capability={} key={}", capability, request.idempotencyKey(), e);
            return ChannelResponse.fail("税务通道调用异常：" + e.getMessage());
        }
    }
}
