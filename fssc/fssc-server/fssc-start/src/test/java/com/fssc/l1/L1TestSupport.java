package com.fssc.l1;

import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * L1 单元测试支撑工具。
 *
 * <p>JDK 26 下 Mockito 默认 inline mock maker 需要 self-attach agent，已被 JDK 禁止
 * （ByteBuddyAgent.install 抛 IllegalState）。本项目已在
 * {@code src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker}
 * 切换为 {@code mock-maker-subclass}，无需 agent 即可 mock 接口与非 final 类。
 */
public final class L1TestSupport {

    private L1TestSupport() {
    }

    /**
     * 用 Mockito 桩构造 Service：{@code @RequiredArgsConstructor} 生成的构造器只做字段赋值，
     * 全部填 mock 后即可调用方法并对依赖做 verify 交互断言。
     */
    @SuppressWarnings("unchecked")
    public static <T> T mockService(Class<T> clazz) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructors()[0];
            Class<?>[] types = ctor.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                args[i] = safeMock(types[i]);
            }
            ctor.setAccessible(true);
            return (T) ctor.newInstance(args);
        } catch (Exception e) {
            throw new IllegalStateException("构造 Service 失败: " + clazz.getName(), e);
        }
    }

    static Object safeMock(Class<?> t) {
        if (t.isPrimitive()) {
            if (t == boolean.class) return false;
            if (t == long.class) return 0L;
            if (t == double.class) return 0d;
            if (t == float.class) return 0f;
            return 0;
        }
        try {
            return Mockito.mock(t);
        } catch (Throwable ignore) {
            // final 类 / 无法子类化的类型：降级为 null，被测方法若不触碰该依赖则无影响
            return null;
        }
    }

    /** 反射注入字段，覆盖构造器注入的 mock。 */
    public static void inject(Object target, String field, Object value) {
        try {
            Class<?> c = target.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            throw new NoSuchFieldException(field);
        } catch (Exception e) {
            throw new IllegalStateException("字段注入失败: " + field, e);
        }
    }

    /**
     * 反射调用私有方法（用于覆盖仅内部使用的核心校验逻辑）。
     * 业务异常原样抛出，不包装 —— 否则调用方无法用 assertThrows(BizException.class) 断言。
     */
    public static Object call(Object target, String method, Class<?>[] types, Object... args) {
        try {
            Class<?> c = target.getClass();
            while (c != null) {
                try {
                    Method m = c.getDeclaredMethod(method, types);
                    m.setAccessible(true);
                    return m.invoke(target, args);
                } catch (NoSuchMethodException e) {
                    c = c.getSuperclass();
                }
            }
            throw new NoSuchMethodException(method);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new IllegalStateException("调用私有方法失败: " + method, cause);
        } catch (Exception e) {
            throw new IllegalStateException("调用私有方法失败: " + method, e);
        }
    }
}
