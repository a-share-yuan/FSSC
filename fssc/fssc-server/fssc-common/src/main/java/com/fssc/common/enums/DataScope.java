// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 数据权限范围（data_scope）。值越小权限越宽，多角色取最宽（min）；
 * 查不到角色时默认最窄的 5（本人）—— fail-closed 原则。
 */
public enum DataScope {

    TENANT(1, "本租户全部"),
    COMPANY(2, "本公司（纳税主体）"),
    DEPT_AND_CHILD(3, "本部门及以下"),
    DEPT(4, "本部门"),
    SELF(5, "仅本人");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    DataScope(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 取最宽（值最小）的数据范围 */
    public static int widest(int a, int b) {
        return Math.min(a, b);
    }

    public static DataScope of(int code) {
        for (DataScope d : values()) {
            if (d.code == code) {
                return d;
            }
        }
        return SELF;
    }
}
