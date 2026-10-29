// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.service;

/**
 * ROLE 节点取人策略（修复方案 / 体检 E-04）。
 *
 * <p>现网 355 个有效节点里 354 个取人为 ROLE，而 ROLE 多持有人时一律「按 sys_user.id 升序取首个」
 * （{@code NodeOrgResolver.firstExcept}）。这带来一个静默的负载倾斜：同一个角色下排在最前的那个人
 * 承接了全部单据：待办最多的那个人身上堆着一串，其余同角色持有人只有零星几条。
 *
 * <p>本枚举只改「指派给谁」，不改「谁有资格办」——候选组、{@code canOperate} 的规则一律不动，
 * 因此自审拦截的既有绕行通道（同角色另一人可办）不被削弱。
 *
 * <p>缺省策略 {@link #FIRST_ID} 与原先<b>逐字一致</b>；切换需同时满足
 * {@code sys_config.wf.assign.strategy} 与灰度清单 {@code wf.assign.strategy.models}
 * 清单为空＝全库不生效，见 {@code 流程引擎问题修复方案_20260926.md} §4.1。
 */
public enum AssignStrategy {

    /** 按用户 id 升序取首个非提单人持有人（＝原先行为，缺省） */
    FIRST_ID,

    /** 取当前 PENDING 待办最少的候选人；并列时回落 {@link #FIRST_ID} 规则（按 id 升序） */
    LEAST_LOADED,

    /** 轮询：同一 {@code modelKey|roleCode} 上一次派给谁，这一次从下一位开始 */
    ROUND_ROBIN;

    /** 未配置或拼写不认识一律回 {@link #FIRST_ID}——配置脏值绝不能悄悄改变指派结果。 */
    public static AssignStrategy parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return FIRST_ID;
        }
        String v = raw.trim().toUpperCase(java.util.Locale.ROOT);
        for (AssignStrategy s : values()) {
            if (s.name().equals(v)) {
                return s;
            }
        }
        return FIRST_ID;
    }
}
