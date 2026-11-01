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
 * 租户角色-菜单（sys_role_menu，带 tenant_id）。
 * 【关联表无 del_flag】：删除用物理 DELETE（租户拦截器自动追加 tenant_id 条件）。
 * 不继承 BaseEntity（BaseEntity 的 @TableLogic 会把 delete 变成 del_flag=1 逻辑删 → 列不存在报错）。
 * tenantId 标注 fill=INSERT：MP 才会调用 MetaObjectHandler（无 fill 字段则 insertFill 不被调用）。
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
    private Long roleId;
    private Long menuId;
    private LocalDateTime createTime;
}
