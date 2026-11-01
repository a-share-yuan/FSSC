// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.util;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.StrUtil;

/**
 * 断言工具。在 Service / 切面里替代散落的 if-throw，统一抛 BizException。
 * 例：AssertUtil.notNull(user, ErrorCode.PERMISSION_DENIED, "用户不存在");
 */
public final class AssertUtil {

    private AssertUtil() {
    }

    public static void notNull(Object obj, ErrorCode code, String detail) {
        if (obj == null) {
            throw new BizException(code, detail);
        }
    }

    public static void isTrue(boolean condition, ErrorCode code, String detail) {
        if (!condition) {
            throw new BizException(code, detail);
        }
    }

    public static void notBlank(String s, ErrorCode code, String detail) {
        if (StrUtil.isBlank(s)) {
            throw new BizException(code, detail);
        }
    }
}
