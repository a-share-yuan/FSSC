// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 主体类型（两级管理）：
 * <ul>
 *   <li>PLATFORM：平台超级管理员 / 运营人员，tenantId 恒为 null，只能碰 pf_* 表。</li>
 *   <li>TENANT：租户内用户（管理员/财务/共享作业/员工），tenantId 有值。</li>
 * </ul>
 * 编码进 JWT，服务端签发并验签，前端无法篡改 —— 这是四级防线中"身份防线"的根。
 */
public enum SubjectType {

    PLATFORM("PLATFORM", "平台"),
    TENANT("TENANT", "租户");

    @EnumValue
    @JsonValue
    private final String code;

    private final String desc;

    SubjectType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static SubjectType of(String code) {
        for (SubjectType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }
}
