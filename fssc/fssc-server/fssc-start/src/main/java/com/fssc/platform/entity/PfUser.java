// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fssc.common.entity.PlatformBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 平台用户（pf_user） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pf_user")
public class PfUser extends PlatformBaseEntity {

    private String username;
    /** BCrypt 加密，序列化不输出（密码绝不外泄） */
    @JsonIgnore
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    /** 1 超级管理员（拥有全部权限） */
    private Integer isSuper;
    /** 1 启用 0 禁用 */
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
}
