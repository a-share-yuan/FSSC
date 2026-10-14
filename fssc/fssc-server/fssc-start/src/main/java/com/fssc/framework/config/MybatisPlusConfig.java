// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.fssc.framework.tenant.FsscTenantLineHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。最关键的是【拦截器注册顺序】：
 * <pre>
 *   interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(handler)); // ① 租户：必须先
 *   interceptor.addInnerInterceptor(new PaginationInnerInterceptor(MYSQL));   // ② 分页：必须后
 * </pre>
 * 顺序反了会导致分页 count 语句不带 tenant_id —— 列表只有本租户数据，总数却是全库的，
 * 总数本身就已经泄露了其他租户的数据规模（高危坑，已在架构文档 2.2 标注）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(FsscTenantLineHandler tenantLineHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // ① 多租户：所有业务表 SQL 自动追加 tenant_id 过滤（数据防线）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        // ② 分页：必须在租户拦截器之后，否则 count 不带 tenant_id
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
