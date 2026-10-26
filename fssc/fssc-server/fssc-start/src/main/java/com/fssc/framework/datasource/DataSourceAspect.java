// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@link FsscDataSource} 注解的 AOP 实现。
 * <p>
 * 必须 @Order(HIGHEST_PRECEDENCE) 排在事务拦截器（默认 LOWEST_PRECEDENCE）之前：
 * 事务管理器在事务开启时即绑定连接，路由键必须先于事务就位，事务才能拿到正确库的连接。
 * 退出时恢复上一层路由键，支持主域方法 → 影像域方法 → 主域方法的嵌套调用。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataSourceAspect {

    @Around("@within(com.fssc.framework.datasource.FsscDataSource)"
            + " || @annotation(com.fssc.framework.datasource.FsscDataSource)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        FsscDataSource ds = resolveAnnotation(pjp);
        if (ds == null) {
            return pjp.proceed();
        }
        String previous = DataSourceContextHolder.get();
        DataSourceContextHolder.set(ds.value());
        try {
            return pjp.proceed();
        } finally {
            if (previous != null) {
                DataSourceContextHolder.set(previous);
            } else {
                DataSourceContextHolder.clear();
            }
        }
    }

    /** 方法级注解优先，类级注解默认处理（取目标类而非代理类，避免 CGLIB 代理丢注解） */
    private FsscDataSource resolveAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        FsscDataSource ds = method.getAnnotation(FsscDataSource.class);
        if (ds == null) {
            ds = pjp.getTarget().getClass().getAnnotation(FsscDataSource.class);
        }
        return ds;
    }
}
