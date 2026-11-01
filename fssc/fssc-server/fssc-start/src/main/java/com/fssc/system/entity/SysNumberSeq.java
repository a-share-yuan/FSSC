// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单据编号流水（sys_number_seq，带 tenant_id）。
 * 并发取号：UPDATE ... SET current_val = current_val + 1 行锁保证唯一；
 * 无 create_by/update_by/del_flag，按真实列独立声明。
 */
@Data
@TableName("sys_number_seq")
public class SysNumberSeq implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 规则编码 */
    private String ruleCode;
    /** 周期键，如 20260811 / 202608 / 2026 / "" */
    private String cycleKey;
    /** 当前流水值 */
    private Long currentVal;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
