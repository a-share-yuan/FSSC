// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.security;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;

/**
 * 当前登录用户上下文（线程级）。在 JwtAuthFilter 解析 Token 后写入，
 * 业务/切面通过它取权限码、功能码、数据权限范围。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> TL = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        TL.set(user);
    }

    public static LoginUser get() {
        return TL.get();
    }

    public static LoginUser getRequired() {
        LoginUser u = TL.get();
        if (u == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return u;
    }

    public static void clear() {
        TL.remove();
    }
}
