package com.fssc.framework.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限。标记在需要按 data_scope 过滤的查询方法上。
 * 切面会把当前用户的数据范围（1-5）与对应的 companyId/deptId 写入
 * {@code DataScopeHolder}，供 Mapper 的 XML/注解 SQL 拼接条件使用。
 * <p>
 * 多角色取最宽（min）；查不到角色默认最窄 5（本人）—— fail-closed。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {
    /** 别名，便于日志排查 */
    String value() default "";
}
