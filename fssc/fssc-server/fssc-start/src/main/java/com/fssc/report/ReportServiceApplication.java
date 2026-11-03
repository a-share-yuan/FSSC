// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report;

import com.fssc.system.service.MessageService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 报表/追溯只读服务启动入口：与 fssc-server 同一个可执行 jar、不同进程。
 *
 * <p>启动方式（PropertiesLauncher 覆盖 Start-Class，无需二次打包）：</p>
 * <pre>
 * java -Dloader.main=com.fssc.report.ReportServiceApplication \
 *      -cp fssc-server.jar PropertiesLauncher \
 *      --spring.profiles.active=report,dev
 * </pre>
 *
 * <p>扫描面只含 report / tracing / framework / common：不加载工作流、费用、发票等业务域的
 * 控制器与流程引擎，因此本进程<b>没有任何写业务表的入口</b>。所需读服务（读库路由、
 * 租户/权限拦截、操作日志、站内信）由 framework 与 MessageService 提供。</p>
 *
 * <p>只读性有两道保证：</p>
 * <ol>
 *   <li>路由层：报表/追溯类统一 {@code @FsscDataSource("replica")}，指向只读副本；
 *       未配置独立副本时别名 master，但同样套只读守卫；</li>
 *   <li>连接层：replica 路由被 {@code ReadOnlyGuardDataSource} 包住，任何非 SELECT 语句
 *       在发出前即被拒（{@code reportDomain.writeGuard} 自检每次 health 都做一次真拒绝试探）。</li>
 * </ol>
 *
 * <p>写动作刻意不在本进程：报表目录收藏、预聚合刷新属管理动作，仍由单体 :8089 提供。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "com.fssc.report", "com.fssc.tracing", "com.fssc.framework", "com.fssc.common"})
@MapperScan("com.fssc.**.mapper")
@Import({MessageService.class})
public class ReportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportServiceApplication.class, args);
    }
}
