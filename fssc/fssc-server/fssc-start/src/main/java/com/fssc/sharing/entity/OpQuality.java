// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 共享质检记录（op_quality，带 tenant_id）。
 * 监控会计作业质量。无 create_by/update_by，按真实列独立声明。
 */
@Data
@TableName("op_quality")
public class OpQuality implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonIgnore
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;
    private String billNo;
    /** 原审核人 */
    private Long auditorId;
    /** 质检员 */
    private Long checkerId;
    /** RANDOM抽检/FULL全检/KEY关键 */
    private String checkType;
    /** 质检得分（百分制） */
    private Integer score;
    /** 扣分/问题说明 */
    private String deductReason;
    /** PASS合格/BACK退回整改 */
    private String result;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime checkTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
