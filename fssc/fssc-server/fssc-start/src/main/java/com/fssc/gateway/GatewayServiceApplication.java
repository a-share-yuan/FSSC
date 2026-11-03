// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.gateway;

import com.fssc.system.service.ExtInterfaceConfigService;
import com.fssc.system.service.MessageService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 集成网关服务独立启动入口（openapi / bcp / connector 三件套同进程）。
 * 与 fssc-server 同一个可执行 jar、不同进程：PropertiesLauncher 覆盖 Start-Class，无需二次打包。
 *
 * <pre>
 * java -Dloader.main=com.fssc.gateway.GatewayServiceApplication \
 *      -cp fssc-server.jar PropertiesLauncher \
 *      --spring.profiles.active=gateway,dev
 * </pre>
 *
 * <p>为什么这三件套一起拆：</p>
 * <ul>
 *   <li><b>openapi</b>（/api/open/linku/**）：BCP → FSSC 入站，HMAC 验签后直接读写主库业务表；
 *       零入边（没有其他域调用它），零自有表（只写 linku_sync_log 与既有业务表）。</li>
 *   <li><b>bcp</b>（/api/tenant/bcp-objection/**）：FSSC → BCP 门户出站，HMAC 签名；零自有表。</li>
 *   <li><b>connector</b>（ChannelAdapter SPI）：外部通道适配与凭据解密，零自有表（读 ext_interface_config）。</li>
 * </ul>
 *
 * <p>进程边界：不加载 workflow / expense / invoice / report 等业务域，因此不注册流程引擎、定时作业
 * 与其他域控制器；master 库连接照常（openapi 的入站写入与 connector 的调用日志需要）。</p>
 *
 * <p>健康检查：{@code /actuator/health}（含 gatewayDomain 组件，见 fssc.gateway.health.enabled）、
 * {@code /actuator/health/liveness}、{@code /actuator/health/readiness}。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "com.fssc.openapi",
        "com.fssc.bcp",
        "com.fssc.connector",
        "com.fssc.gateway",
        "com.fssc.framework",
        "com.fssc.common",
        "com.fssc.system.gateway"
})
@MapperScan({
        "com.fssc.openapi.linku.mapper",
        "com.fssc.system.mapper",
        "com.fssc.platform.mapper",
        "com.fssc.framework.job.mapper"
})
@Import({ExtInterfaceConfigService.class,
        MessageService.class})
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
