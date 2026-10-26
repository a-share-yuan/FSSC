// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.bank.gateway;

import com.fssc.system.entity.ExtInterfaceConfig;

import java.util.List;
import java.util.Map;

/**
 * 银行报文编解码器（域3 · R3 多银行 provider 分派）。
 *
 * <p><b>解决的问题</b>：R1/R2 把银企直联收编进 connector 后，通道语义（去重/凭据/记录/熔断）
 * 已统一，但<b>报文形态仍是各家银行一套</b>。R3 的目标是让「新增一家银行」不再需要改
 * {@code HttpBankGateway} 里的那段 if/else。</p>
 *
 * <p><b>分派维度</b>：{@code ext_interface_config.provider}（该列已存在，当前值 {@code GENERIC}）。
 * 由 {@link BankChannelResolver} 解析出当前生效的 provider，再取对应编解码器。</p>
 *
 * <p><b>实现约定</b>：
 * <ul>
 *   <li>实现类注册为 Spring Bean（{@code @Component}），{@link #provider()} 返回大写品牌码；</li>
 *   <li><b>不需要改业务码</b>——{@link BankGateway} 与 {@code PayBatchService} 始终不变；</li>
 *   <li>报文字段名<b>不得臆造</b>：真实字段名以各家银行对接文档为准。
 *       本仓库只提供 {@link GenericBankCodec}（通用形态，= 收编前行为，零回归）与
 *       {@link TemplateBankCodec}（由 {@code extra_config} 配置字段映射，接入新银行通常只改配置）。</li>
 * </ul>
 */
public interface BankProviderCodec {

    /** 通用形态（收编前的默认行为，保证零回归）。 */
    String PROVIDER_GENERIC = "GENERIC";

    /** 配置驱动形态：字段映射写在 extra_config，新增银行只改配置不改码。 */
    String PROVIDER_TEMPLATE = "TEMPLATE";

    /**
     * 当前编解码器对应的 provider 码（大写）。
     *
     * @return 如 {@code GENERIC} / {@code TEMPLATE} / {@code ICBC}
     */
    String provider();

    /**
     * 按该银行形态组装付款批次报文底稿。
     *
     * @param batchNo 付款批次号（唯一键组成部分）
     * @param items   付款明细
     * @param cfg     当前通道配置（可读 {@code extraConfig} 做字段映射/开关）
     * @return 交给通用网关外呼的请求体
     */
    Map<String, Object> buildPayload(String batchNo, List<BankGateway.PayItem> items,
                                     ExtInterfaceConfig cfg);

    /**
     * 从银行响应体解析各笔流水号。
     *
     * <p>最小契约：能解析出逐笔流水号则返回等长列表；解析不出时返回 {@code null}，
     * 由调用方降级为「整批一个流水号 + 序号」（与收编前行为一致）。</p>
     *
     * @param body      响应体原文
     * @param itemCount 本批笔数
     * @return 逐笔流水号；无法解析返回 {@code null}
     */
    List<String> parseSerialNos(String body, int itemCount);
}
