// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件下载一次性令牌（sys_file_token）。
 * 无 create_by/update_by/del_flag，按真实列独立声明（不继承审计基类，select 不会映射不存在的列）。
 * token 全局唯一（uk_token），下载接口以 token 为身份凭据，尚未建立租户上下文。
 */
@Data
@TableName("sys_file_token")
public class SysFileToken implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long tenantId;

    /** 一次性下载令牌（全局唯一） */
    private String token;
    private Long fileId;
    /** 申领人用户ID */
    private Long userId;
    /** 过期时间（默认5分钟） */
    private LocalDateTime expireTime;
    /** 是否已使用：0否 1是 */
    private Integer used;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;
}
