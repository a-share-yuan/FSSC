/**
 * 集成连接服务域包（形态 B）。
 *
 * <p>背景：OCR / 银企直联 / 税局开票 / ERP 记账 / 凭证推送等第三方通道原先散落在
 * {@code com.fssc.system.gateway}、{@code com.fssc.image.channel}、{@code com.fssc.bank.gateway}、
 * {@code com.fssc.sharing.channel} 四处，业务侧直接依赖各自 SPI，缺少统一的凭据托管、
 * 重试/限流/熔断与原始报文记录。</p>
 *
 * <p>形态 B 的目标：把上述通道收敛为<b>一个"集成连接服务"域</b>——统一内部 API（本包
 * {@link com.fssc.connector.api.ConnectorFacade}）、统一请求/响应契约
 * （{@link com.fssc.connector.api.ChannelRequest}/{@link com.fssc.connector.api.ChannelResponse}）、
 * 统一能力分类与治理规则（{@link com.fssc.connector.api.ChannelType}）。业务侧只调内部 API，
 * 不感知通道与协议；仍然是<b>单体部署</b>（不拆进程、不拆库），属于"域包 + 内部 API"级别的收敛，
 * 不是微服务化。</p>
 *
 * <p>包结构约定：</p>
 * <ul>
 *   <li>{@code api}：对外契约（请求/响应/能力枚举/Facade），业务侧唯一依赖面；</li>
 *   <li>{@code core}：域内实现与过渡适配层（当前委托既有 {@code IntegrationGateway}）；</li>
 *   <li>后续 {@code adapter}：各通道适配实现（银行/税局/ERP/OCR/凭证推送），
 *       业务包不得 import {@code com.fssc.connector.adapter.*}（将由 CI 规则 9 强制）。</li>
 * </ul>
 */
package com.fssc.connector;
