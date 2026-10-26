package com.fssc.ai;

import com.fssc.masterdata.service.ExpenseItemService;
import com.fssc.system.service.CompanyService;
import com.fssc.system.service.DeptService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * AI 服务独立启动入口：与 fssc-server 同一个可执行 jar、不同进程。
 *
 * <p>启动方式（PropertiesLauncher 覆盖 Start-Class，无需二次打包）：</p>
 * <pre>
 * java -Dloader.main=com.fssc.ai.AiServiceApplication \
 *      -cp fssc-server.jar org.springframework.boot.loader.launch.PropertiesLauncher \
 *      --spring.profiles.active=ai,dev
 * </pre>
 *
 * <p><b>回环→真 HTTP</b>：AI 网关（{@link AiGatewayService}）从不直接调业务 Service，
 * 一律带用户原 token 转发到 {@code fssc.ai.gateway.base-url} 指向的业务服务（单体 :8089）。
 * 独立进程下该地址必须指向<b>外部</b>业务服务，不允许指回自己（见 AiDomainHealthIndicator 的 loopbackSelf 判据）。</p>
 *
 * <p>扫描面只含 ai / connector / framework / common：connector 是 AI 模型通道的出站适配器（无状态、读系统集成配置），
 * 与其同进程以避免引入内部服务凭证；system/masterdata 只按需 @Import 实体解析用到的三个服务并扫描其 mapper，
 * 不加载 workflow / expense / invoice / report 等业务域，因此不注册流程引擎、定时作业与其他域控制器。</p>
 *
 * <p>巡检作业（AiInspectService#runDueRules）需要同进程内有业务控制器可供反射调用，
 * 因此在独立 AI 服务内关闭（fssc.ai.inspect.enabled=false），仍由单体运行，避免双进程重复出票。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "com.fssc.ai",
        "com.fssc.connector",
        "com.fssc.framework",
        "com.fssc.common",
        "com.fssc.system.gateway"
})
@MapperScan({
        "com.fssc.ai.mapper",
        "com.fssc.system.mapper",
        "com.fssc.masterdata.mapper",
        "com.fssc.platform.mapper",
        "com.fssc.framework.job.mapper"
})
@Import({CompanyService.class, DeptService.class, ExpenseItemService.class,
        com.fssc.system.service.ExtInterfaceConfigService.class,
        com.fssc.system.service.MessageService.class})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
