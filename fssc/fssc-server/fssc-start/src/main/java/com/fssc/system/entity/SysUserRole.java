// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户用户-角色（sys_user_role，带 tenant_id）。
 * 【关联表无 del_flag】：删除用物理 DELETE（租户拦截器自动追加 tenant_id 条件）。
 * 不继承 BaseEntity（理由同 SysRoleMenu）。
 * tenantId 标注 fill=INSERT：MP 才会调用 MetaObjectHandler（无 fill 字段则 insertFill 不被调用）。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
    private Long userId;
    private Long roleId;
    private LocalDateTime createTime;
}
