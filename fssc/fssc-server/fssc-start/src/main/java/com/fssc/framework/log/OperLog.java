// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解。标记在 Controller 方法上，由 OperLogAspect 写入双轨审计日志：
 * <ul>
 *   <li>平台用户操作 → pf_oper_log（平台操作日志，无 tenant_id，不可删）</li>
 *   <li>租户用户操作 → sys_oper_log（租户操作日志，带 tenant_id，不可删）</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 模块，如 TENANT/PACKAGE/EXPENSE */
    String module();

    /** 操作类型：INSERT/UPDATE/DELETE/QUERY/EXPORT/IMPORT/GRANT/OTHER */
    String operType();

    /** 操作描述 */
    String desc();
}
