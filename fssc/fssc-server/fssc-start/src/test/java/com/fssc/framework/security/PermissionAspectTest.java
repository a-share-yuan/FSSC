package com.fssc.framework.security;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.enums.SubjectType;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.annotation.PlatformOnly;
import com.fssc.framework.security.annotation.RequiresFeature;
import com.fssc.framework.security.annotation.RequiresPerm;
import java.lang.annotation.Annotation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 权限防线单测：@RequiresPerm / @RequiresFeature / @PlatformOnly 的放行与拦截。
 *
 * <p>纯逻辑测试，不启动 Spring 上下文；也不使用 Mockito
 * （JDK 26 下 byte-buddy agent 自附加受限），改用 JDK 动态代理伪造 ProceedingJoinPoint。</p>
 */
class PermissionAspectTest {

    private final PermissionAspect aspect = new PermissionAspect();

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    // 伪 ProceedingJoinPoint

    /** 记录 proceed() 调用次数的最小实现 */
    static final class FakePjp implements InvocationHandler {
        int calls = 0;
        Object next = "ok";

        ProceedingJoinPoint build() {
            return (ProceedingJoinPoint) Proxy.newProxyInstance(
                    ProceedingJoinPoint.class.getClassLoader(),
                    new Class<?>[]{ProceedingJoinPoint.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "proceed":
                    calls++;
                    return next;
                case "toString":
                    return "FakePjp";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }
    }

    private static LoginUser user(SubjectType type, Set<String> perms, Set<String> features) {
        LoginUser u = new LoginUser();
        u.setSubjectType(type);
        u.setPerms(perms);
        u.setFeatures(features);
        return u;
    }

    private static RequiresPerm perm(String code) {
        return new RequiresPerm() {
            @Override
            public String[] value() {
                return new String[]{ code };
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RequiresPerm.class;
            }
        };
    }

    private static RequiresFeature feature(String code) {
        return new RequiresFeature() {
            @Override
            public String value() {
                return code;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RequiresFeature.class;
            }
        };
    }

    private static PlatformOnly platformOnly() {
        return new PlatformOnly() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return PlatformOnly.class;
            }
        };
    }

    // @RequiresPerm

    @Test
    void 有权限码则放行() throws Throwable {
        UserContext.set(user(SubjectType.TENANT, Set.of("system:user:list"), Set.of()));
        FakePjp fake = new FakePjp();
        Object r = aspect.aroundPerm(fake.build(), perm("system:user:list"));
        assertEquals("ok", r);
        assertEquals(1, fake.calls, "应放行并执行目标方法");
    }

    @Test
    void 缺权限码则4030() throws Throwable {
        UserContext.set(user(SubjectType.TENANT, Set.of("system:user:list"), Set.of()));
        FakePjp fake = new FakePjp();
        try {
            aspect.aroundPerm(fake.build(), perm("system:user:delete"));
            fail("缺少权限码应拦截");
        } catch (BizException e) {
            assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), e.getCode());
        }
        assertEquals(0, fake.calls, "拦截时目标方法不得执行");
    }

    @Test
    void 通配权限星号等同全部权限() throws Throwable {
        // 平台超管 / 租户管理员 perms=[*:*:*]
        UserContext.set(user(SubjectType.TENANT, Set.of("*:*:*"), Set.of()));
        assertEquals("ok", aspect.aroundPerm(new FakePjp().build(), perm("任意:权限:码")));
    }

    @Test
    void 未登录则抛UNAUTHORIZED() throws Throwable {
        UserContext.clear();
        FakePjp fake = new FakePjp();
        try {
            aspect.aroundPerm(fake.build(), perm("x"));
            fail("未登录应抛出异常");
        } catch (BizException e) {
            assertEquals(ErrorCode.UNAUTHORIZED.getCode(), e.getCode());
        }
        assertEquals(0, fake.calls);
    }

    // @RequiresFeature

    @Test
    void 套餐含功能码则放行() throws Throwable {
        UserContext.set(user(SubjectType.TENANT, Set.of(), Set.of("F_EXPENSE_CLAIM")));
        assertEquals("ok", aspect.aroundFeature(new FakePjp().build(), feature("F_EXPENSE_CLAIM")));
    }

    @Test
    void 套餐未开通功能则4031() throws Throwable {
        UserContext.set(user(SubjectType.TENANT, Set.of("*:*:*"), Set.of("F_EXPENSE_APPLY")));
        try {
            aspect.aroundFeature(new FakePjp().build(), feature("F_EXPENSE_CLAIM"));
            fail("套餐未开通应拦截");
        } catch (BizException e) {
            assertEquals(ErrorCode.FEATURE_DISABLED.getCode(), e.getCode());
        }
    }

    @Test
    void features为空则一律拦截() throws Throwable {
        UserContext.set(user(SubjectType.TENANT, Set.of("*:*:*"), null));
        try {
            aspect.aroundFeature(new FakePjp().build(), feature("F_LOAN"));
            fail("features 为空应拦截");
        } catch (BizException e) {
            assertEquals(ErrorCode.FEATURE_DISABLED.getCode(), e.getCode());
        }
    }

    // @PlatformOnly

    @Test
    void 平台主体放行() throws Throwable {
        UserContext.set(user(SubjectType.PLATFORM, Set.of("*:*:*"), Set.of()));
        assertEquals("ok", aspect.aroundPlatform(new FakePjp().build(), platformOnly()));
    }

    @Test
    void 租户主体访问平台接口则4032() throws Throwable {
        UserContext.set(user(SubjectType.TENANT, Set.of("*:*:*"), Set.of()));
        try {
            aspect.aroundPlatform(new FakePjp().build(), platformOnly());
            fail("租户主体访问平台接口应拦截");
        } catch (BizException e) {
            assertEquals(ErrorCode.PLATFORM_ONLY.getCode(), e.getCode());
        }
    }
}
