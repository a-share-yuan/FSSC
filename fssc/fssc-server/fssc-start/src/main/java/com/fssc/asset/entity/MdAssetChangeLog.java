// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产变动台账（md_asset_change_log，带 tenant_id）＝变动原因与幅度的载体。
 * 没有这张表的话，原值调整/盘点差异只起流不建业务行，原因拼进流程标题、卡片只留末值，前后镜像无从回查。
 * <p>{@code createBy/updateBy} 为裸字段（无 fill 注解）⇒ 写入方必须显式 {@code setCreateBy(...)}，否则落 DB NULL。
 */
@Data
@TableName("md_asset_change_log")
public class MdAssetChangeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long assetId;
    private String assetNo;
    private Long companyId;
    private Long deptId;
    /** VALUE_ADJUST 原值调整/CHECK_DIFF 盘点差异/IMPAIR 减值/DISPOSE 处置/TRANSFER 调拨 */
    private String changeType;
    /** 变动原因（人工输入） */
    private String changeReason;
    /** 变动方式：WF=经审批流变动 / DIRECT=不经审批直接改账（写入侧按 proc_inst_id 有无派生，见 MdAssetService.writeChangeLog） */
    private String changeMethod;
    private BigDecimal beforeOriginalAmount;
    private BigDecimal afterOriginalAmount;
    private BigDecimal beforeNetAmount;
    private BigDecimal afterNetAmount;
    /** 幅度＝after-before；盘点差异＝差异额（负＝盘亏） */
    private BigDecimal deltaAmount;
    /** APPROVING 在途/APPROVED 通过/REJECTED 驳回 */
    private String status;
    private String remark;
    /** Flowable 流程实例号（列型 varchar(64)），最终状态回写按此键定位 */
    private String procInstId;
    private Long createBy;
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
