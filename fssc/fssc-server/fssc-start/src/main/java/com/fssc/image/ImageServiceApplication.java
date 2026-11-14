package com.fssc.image;

import com.fssc.system.service.ConfigService;
import com.fssc.system.service.FileService;
import com.fssc.system.service.MessageService;
import com.fssc.system.service.NumberRuleService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 影像服务独立启动入口：与 fssc-server 同一个可执行 jar、不同进程。
 *
 * <p>启动方式（PropertiesLauncher 覆盖 Start-Class，无需二次打包）：</p>
 * <pre>
 * java -Dloader.main=com.fssc.image.ImageServiceApplication \
 *      -cp fssc-server.jar PropertiesLauncher \
 *      --spring.profiles.active=image,dev
 * </pre>
 *
 * <p>扫描面故意只含 image / framework / common：不加载 workflow、expense、invoice 等业务域，
 * 因此不注册流程引擎、定时作业与其他域控制器。system 包只按需 @Import 四个被影像域引用的服务
 * 文件、配置、编号、站内信，不整包扫描。</p>
 *
 * <p>健康检查：{@code /actuator/health}（含 imageDomain 组件，见 fssc.image.health.enabled）、
 * {@code /actuator/health/liveness}、{@code /actuator/health/readiness}。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.fssc.image", "com.fssc.framework", "com.fssc.common"})
@MapperScan({
        "com.fssc.image.mapper",
        "com.fssc.system.mapper",
        "com.fssc.platform.mapper",
        "com.fssc.framework.job.mapper"
})
@Import({FileService.class, ConfigService.class, NumberRuleService.class, MessageService.class})
public class ImageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageServiceApplication.class, args);
    }
}
