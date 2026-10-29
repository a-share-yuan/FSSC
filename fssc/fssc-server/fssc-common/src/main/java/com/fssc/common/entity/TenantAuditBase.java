// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 主数据（md_*）实体基类。
 * 与 BaseEntity 的差异：md_* 表 create_by/update_by 为【bigint】（记录用户ID），
 * 而 sys_* 表为 varchar（记录用户名）。FsscMetaObjectHandler 已按字段类型自适应填充。
 * 带 tenantId（标 fill=INSERT 触发 MetaObjectHandler，业务代码亦显式 set 双保险）。
 */
@Data
public class TenantAuditBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 系统列只读——序列化照旧输出，反序列化（@RequestBody）一律丢弃 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 必须标 fill=INSERT，否则 MP 生成的 INSERT 列清单不含 create_by，MetaObjectHandler 填值无效 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 必须标 fill=INSERT_UPDATE，否则 MP 不将该列纳入 INSERT/UPDATE，审计人无法写入数据库 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
