package com.fssc.image.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.entity.SysConfig;
import com.fssc.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * OCR 通道运行时路由器：让「用哪个 OCR 通道」成为管理员可配置项，改后即生效、无需重启。
 *
 * <p>取值优先级：sys_config {@code ocr.channel}（系统管理→OCR通道配置 页面维护，
 * 值 gateway/mock/http/paddle）→ 为空时回退配置项 {@code fssc.ocr.channel}（yml 默认 <b>gateway</b>，
 * 即发票能力网关 InvoiceGateway，需求「发票 OCR 识别默认使用我们封装的 API」）。
 * 未知值一律安全回退 gateway→mock，绝不因配置错误导致识别链路不可用。</p>
 *
 * <p>【网关可整体回退】{@code invoice.gateway.enabled=false}（yml {@code fssc.gateway.enabled}
 * 或 sys_config）时 {@link QxyGatewayOcrChannel} 直接返回失败结果，不参与降级；
 * 若要退回改造前的模拟识别，把 {@code ocr.channel} 显式设为 mock 即可。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrChannelRouter {

    public static final String CFG_KEY = "ocr.channel";

    private final MockOcrChannel mockChannel;
    private final HttpOcrChannel httpChannel;
    private final PaddleOcrChannel paddleChannel;
    private final QxyGatewayOcrChannel gatewayChannel;
    private final SysConfigMapper configMapper;

    /** yml 默认通道（sys_config 未设置时生效）；默认 gateway=发票能力网关 */
    @Value("${fssc.ocr.channel:gateway}")
    private String defaultChannel;

    /** 当前生效通道名（gateway/mock/http/paddle），供配置页回显。
     *  分库改造：sys_config 在 master 库；NOT_SUPPORTED 挂起外层（可能为 image 库）事务后取 master 连接。 */
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public String currentChannel() {
        String v = readConfig();
        if (v == null || v.isBlank()) {
            v = defaultChannel;
        }
        v = v == null ? "gateway" : v.trim().toLowerCase();
        return switch (v) {
            case "gateway" -> gatewayChannel.enabled() ? "gateway" : "mock";
            case "http" -> "http";
            case "paddle" -> "paddle";
            default -> "mock";
        };
    }

    /** 分库改造：同 currentChannel，NOT_SUPPORTED 保证体内 this 调用走 master 连接。 */
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public OcrChannel current() {
        return switch (currentChannel()) {
            case "gateway" -> gatewayChannel;
            case "http" -> httpChannel;
            case "paddle" -> paddleChannel;
            default -> mockChannel;
        };
    }

    private String readConfig() {
        try {
            Long tenantId = TenantContext.getTenantId();
            SysConfig c = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, CFG_KEY)
                    .eq(SysConfig::getTenantId, tenantId)
                    .last("limit 1"));
            return c == null ? null : c.getConfigValue();
        } catch (Exception e) {
            log.warn("读取 ocr.channel 失败，回退默认通道", e);
            return null;
        }
    }
}
