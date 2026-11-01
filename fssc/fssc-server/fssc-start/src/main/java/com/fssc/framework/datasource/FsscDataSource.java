// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.datasource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据源路由注解（分库改造）。
 * <p>
 * 标注在 Service/Facade/Controller 类或方法上，由 {@link DataSourceAspect} 在进入方法前
 * 把路由键写入 {@link DataSourceContextHolder}，{@link FsscRoutingDataSource} 据此选择连接池；
 * 方法结束后自动恢复上一层路由键（支持嵌套调用）。
 * <ul>
 *   <li>方法级优先于类级；未标注时走默认数据源 master（fssc 主库）。</li>
 *   <li>当前取值：master（fssc 主库）/ image（fssc_image 影像库）/ replica（报表只读副本，
 *       不论是否独立库都套只读守卫）/ document / invoice（档案、发票域自治库，未拆库时别名 master）。
 *       新增库时在 DataSourceConfig 注册。</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FsscDataSource {

    String value() default "master";
}
