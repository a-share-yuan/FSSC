package com.fssc.framework.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 报表业务域校验（P1 报表权限整改）。
 *
 * <p>标注在报表查询接口上，与 {@code @RequiresPerm("report:view")} 叠加使用：
 * 前者校验"是否有报表基础码"，本注解校验"是否持有对应业务域的判据权限码"——
 * 即「报表权限 = 业务域权限的投影」在接口层的落地。
 * 持有 {@code report:audit:all}（审计阅示）或 {@code *:*:*}（超管）放行。
 *
 * <p>取值为 {@link com.fssc.framework.security.ReportDomain} 的 key，如 "EXP"、"AR"。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReportDomain {

    /** 业务域 key（ReportDomain 枚举 key） */
    String value();
}
