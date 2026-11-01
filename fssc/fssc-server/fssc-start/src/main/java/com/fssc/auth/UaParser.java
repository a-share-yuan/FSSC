// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.auth;

import org.springframework.stereotype.Component;

/**
 * 简易 User-Agent 解析器，提取浏览器和操作系统信息用于登录日志。
 * 不依赖第三方 UA 解析库，基于常见关键字匹配。
 */
@Component
public class UaParser {

    /** 从 User-Agent 中提取浏览器名称 */
    public String parseBrowser(String ua) {
        if (ua == null || ua.isBlank()) return "";
        if (ua.contains("Edg/") || ua.contains("Edge/")) return "Edge";
        if (ua.contains("Chrome/") && !ua.contains("Edg/")) return "Chrome";
        if (ua.contains("Safari/") && !ua.contains("Chrome/")) return "Safari";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Trident/") || ua.contains("MSIE")) return "IE";
        if (ua.contains("Opera/") || ua.contains("OPR/")) return "Opera";
        return "Other";
    }

    /** 从 User-Agent 中提取操作系统名称 */
    public String parseOs(String ua) {
        if (ua == null || ua.isBlank()) return "";
        if (ua.contains("Windows NT 10")) return "Windows 10";
        if (ua.contains("Windows NT 11")) return "Windows 11";
        if (ua.contains("Windows NT 6.3")) return "Windows 8.1";
        if (ua.contains("Windows NT 6.2")) return "Windows 8";
        if (ua.contains("Windows NT 6.1")) return "Windows 7";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Linux")) return "Linux";
        return "Other";
    }
}