package com.fssc.framework.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮/接口权限。value 为权限码，如 {@code exp:claim:submit}。
 * 最终权限 = 角色权限(perms) ∩ 套餐功能码(features)，见 PermissionAspect。
 *
 * <p>多码语义（OR）：支持一次声明多个权限码，
 * 持有其中<b>任意一个</b>即可通过。用于「权限码收敛/迁移」的过渡期——
 * 例如把粗粒度 {@code md:config} 拆成细粒度码时，可先声明
 * {@code @RequiresPerm({"md:supplier:list", "md:config"})}，
 * 使新旧两套码各自可通行，待授权数据平滑迁移后再移除旧码，期间零停机、零报错。
 *
 * <p>单值写法 {@code @RequiresPerm("x")} 为 Java 数组单元素简写，语义不变，
 * 因此本次改动对既有全部调用点<b>源码兼容</b>。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPerm {
    String[] value();
}
