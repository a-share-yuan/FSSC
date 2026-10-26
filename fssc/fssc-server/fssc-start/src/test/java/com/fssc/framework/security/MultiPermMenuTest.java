// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.security;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.enums.SubjectType;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.StrUtil;
import com.fssc.framework.security.annotation.RequiresPerm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 维度10-任务10-7：多权限码菜单解析单元测试。
 *
 * <p>sys_menu 中 6 个菜单的 perms 为逗号分隔多码。AuthService 装载 LoginUser 时
 * 经 StrUtil.expandPerms 拆分为独立权限码；本测试覆盖拆分逻辑与
 * PermissionAspect 对拆分后集合的校验行为（全部拥有/部分拥有/无权限/通配）。</p>
 */
class MultiPermMenuTest {

    /** sys_menu 中实际存在的 6 个多权限码菜单（2026-09 盘点） */
    private static final List<String> MULTI_PERM_MENUS = List.of(
            "wf:bizflow:list,wf:instance:list",
            "treasury:fundplan:list,treasury:creditbill:list",
            "compliance:archiveaccess:list,compliance:archivedestroy:list",
            "gl:taxprov:list,gl:salary:list",
            "gl:taxrefund:list,gl:taxrefund:create",
            "gl:taxrefund:list,gl:taxrefund:create,gl:taxrefund:approve");

    private static LoginUser userWithPerms(Set<String> perms) {
        LoginUser u = new LoginUser();
        u.setSubjectType(SubjectType.TENANT);
        u.setPerms(perms);
        return u;
    }

    // ------------------------------------------------ 拆分逻辑

    @Test
    void expandPerms_拆分6个多权限码菜单() {
        for (String menuPerms : MULTI_PERM_MENUS) {
            Set<String> expanded = StrUtil.expandPerms(List.of(menuPerms));
            String[] codes = menuPerms.split(",");
            assertEquals(codes.length, expanded.size(), "拆分后码数应一致：" + menuPerms);
            for (String code : codes) {
                assertTrue(expanded.contains(code.trim()), "应包含独立码 " + code + "（来自 " + menuPerms + "）");
            }
        }
    }

    @Test
    void expandPerms_空白与重复项清理() {
        Set<String> expanded = StrUtil.expandPerms(List.of("a:b:list, a:b:list , ,b:c:list", "", "  "));
        assertEquals(Set.of("a:b:list", "b:c:list"), expanded);
    }

    @Test
    void expandPerms_空集合安全() {
        assertTrue(StrUtil.expandPerms(null).isEmpty());
        assertTrue(StrUtil.expandPerms(List.of()).isEmpty());
    }

    // ------------------------------------------------ PermissionAspect × 多权限码场景

    private boolean allowed(Set<String> userPerms, String required) {
        UserContext.set(userWithPerms(userPerms));
        try {
            new PermissionAspect().aroundPerm(new PermissionAspectTest.FakePjp().build(),
                    new RequiresPerm() {
                        @Override
                        public String[] value() {
                            return new String[]{ required };
                        }

                        @Override
                        public Class<? extends java.lang.annotation.Annotation> annotationType() {
                            return RequiresPerm.class;
                        }
                    });
            return true;
        } catch (BizException e) {
            assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), e.getCode(), "非权限异常不应出现");
            return false;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void 多码菜单_拥有全部码则每个码都放行() {
        Set<String> perms = StrUtil.expandPerms(
                List.of("gl:taxrefund:list,gl:taxrefund:create,gl:taxrefund:approve"));
        assertTrue(allowed(perms, "gl:taxrefund:list"));
        assertTrue(allowed(perms, "gl:taxrefund:create"));
        assertTrue(allowed(perms, "gl:taxrefund:approve"));
    }

    @Test
    void 多码菜单_仅拥有部分码时其余码被拦截() {
        // 用户只有 list 码（如"查看退税"角色），不应通过 create/approve 校验
        Set<String> perms = Set.of("gl:taxrefund:list");
        assertTrue(allowed(perms, "gl:taxrefund:list"));
        assertTrue(!allowed(perms, "gl:taxrefund:create"), "未持有的 create 码应拦截");
        assertTrue(!allowed(perms, "gl:taxrefund:approve"), "未持有的 approve 码应拦截");
    }

    @Test
    void 多码菜单_无任何码则拦截() {
        Set<String> perms = Set.of("system:user:list");
        assertTrue(!allowed(perms, "wf:bizflow:list"), "无相关码应拦截");
        assertTrue(!allowed(perms, "wf:instance:list"), "无相关码应拦截");
    }

    @Test
    void 多码菜单_未拆分的逗号串不能通过精确校验() {
        // 防回归：若装载层忘记 expandPerms，逗号整串无法通过任何单码校验
        Set<String> raw = Set.of("wf:bizflow:list,wf:instance:list");
        assertTrue(!allowed(raw, "wf:bizflow:list"));
        assertTrue(!allowed(raw, "wf:instance:list"));
    }

    @Test
    void 通配权限仍对多码菜单生效() {
        Set<String> perms = Set.of("*:*:*");
        assertTrue(allowed(perms, "wf:bizflow:list"));
        assertTrue(allowed(perms, "treasury:creditbill:list"));
    }

    @Test
    void 全部6个多码菜单_拆分后首个码均放行() {
        for (String menuPerms : MULTI_PERM_MENUS) {
            Set<String> perms = StrUtil.expandPerms(List.of(menuPerms));
            String first = menuPerms.split(",")[0];
            assertTrue(allowed(perms, first), "首个码应放行：" + menuPerms);
        }
    }
}
