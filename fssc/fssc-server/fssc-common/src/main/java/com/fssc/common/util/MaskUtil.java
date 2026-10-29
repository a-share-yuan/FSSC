// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.util;

/**
 * 敏感数据脱敏工具。
 *
 * <p>统一全系统脱敏规则：列表/导出默认脱敏，明文仅在「授权查看」接口返回。
 * 脱敏规则参考金融行业惯例（保留首尾、中间以 * 覆盖）。</p>
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 是否为已脱敏串（含 * 号），用于避免二次脱敏 / 判断是否需拉取明文。 */
    public static boolean masked(String v) {
        return v != null && v.indexOf('*') >= 0;
    }

    /**
     * 手机号：138****8000（保留前 3 后 4）。
     * 长度不足 7 位时整体以 * 覆盖。
     */
    public static String phone(String v) {
        if (v == null || v.isEmpty()) {
            return v;
        }
        String s = v.trim();
        if (s.length() < 7) {
            return stars(s.length());
        }
        return s.substring(0, 3) + "****" + s.substring(s.length() - 4);
    }

    /**
     * 邮箱：abcdef@x.com -> ab***@x.com（本地部分保留前 2 位，域名保留）。
     */
    public static String email(String v) {
        if (v == null || v.isEmpty()) {
            return v;
        }
        String s = v.trim();
        int at = s.indexOf('@');
        if (at <= 0) {
            return stars(s.length());
        }
        String local = s.substring(0, at);
        String domain = s.substring(at);
        if (local.length() <= 2) {
            return stars(local.length()) + domain;
        }
        return local.substring(0, 2) + "***" + domain;
    }

    /**
     * 银行账号：保留前 4 后 4，中间以 * 覆盖（长度不足 8 位只保留后 4）。
     */
    public static String bankAccount(String v) {
        if (v == null || v.isEmpty()) {
            return v;
        }
        String s = v.replaceAll("\\s", "");
        if (s.length() <= 8) {
            return stars(s.length());
        }
        return s.substring(0, 4) + stars(s.length() - 8) + s.substring(s.length() - 4);
    }

    /**
     * 身份证号：保留前 6 后 4。
     */
    public static String idCard(String v) {
        if (v == null || v.isEmpty()) {
            return v;
        }
        String s = v.trim();
        if (s.length() <= 10) {
            return stars(s.length());
        }
        return s.substring(0, 6) + stars(s.length() - 10) + s.substring(s.length() - 4);
    }

    /**
     * 姓名：张* / 张*三（两字保留首字，多字保留首尾）。
     */
    public static String name(String v) {
        if (v == null || v.isEmpty()) {
            return v;
        }
        String s = v.trim();
        if (s.length() == 1) {
            return s;
        }
        if (s.length() == 2) {
            return s.charAt(0) + "*";
        }
        return s.charAt(0) + stars(s.length() - 2) + s.charAt(s.length() - 1);
    }

    private static String stars(int n) {
        if (n <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append('*');
        }
        return sb.toString();
    }
}
