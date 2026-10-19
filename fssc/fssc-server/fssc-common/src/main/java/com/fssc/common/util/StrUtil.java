package com.fssc.common.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 字符串小工具。仅封装高频用法，避免业务代码里散落 null 判断。
 */
public final class StrUtil {

    private StrUtil() {
    }

    public static boolean isBlank(String s) {
        return StringUtils.isBlank(s);
    }

    public static boolean isNotBlank(String s) {
        return StringUtils.isNotBlank(s);
    }

    /** 把 "a,b,c" 拆成去重后的 Set（权限码/功能码常用）。恒返回可变集合，调用方可安全再做增删过滤。 */
    public static Set<String> splitToSet(String csv) {
        if (isBlank(csv)) {
            return new java.util.HashSet<>();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(java.util.HashSet::new));
    }

    public static String join(Collection<String> items) {
        return items == null ? "" : String.join(",", items);
    }

    /**
     * 展开权限码集合：sys_menu.perms 存在逗号分隔多码（如 "wf:bizflow:list,wf:instance:list"），
     * 装载进 LoginUser 前必须拆成独立权限码，否则 @RequiresPerm 精确匹配会失败。
     */
    public static Set<String> expandPerms(Collection<String> items) {
        if (items == null || items.isEmpty()) {
            return new java.util.HashSet<>();
        }
        return items.stream()
                .filter(StringUtils::isNotBlank)
                .flatMap(s -> java.util.Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }
}
