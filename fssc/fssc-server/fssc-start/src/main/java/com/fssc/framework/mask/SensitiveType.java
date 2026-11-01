// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.mask;

/** 敏感字段类型。 */
public enum SensitiveType {
    /** 手机号：138****8000 */
    PHONE,
    /** 邮箱：ab***@example.com */
    EMAIL,
    /** 银行账号：6222********1234 */
    BANK_CARD,
    /** 身份证号：110101********1234 */
    ID_CARD,
    /** 姓名：张* / 张*三 */
    NAME
}
