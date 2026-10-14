// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 折旧计提批单（asset_depr_batch，FA_DEPRECIATION 审批载体）。
 *
 * <p>月度折旧计提不直接落账，而是生成批单并发起 FA_DEPRECIATION 审批流；
 * 审批通过后（AssetWfEventListener 监听）执行实际计提并置 DONE。
 *
 * <p>状态 status：DRAFT 草稿 / APPROVING 审批中 / DONE 已完成 / REJECTED 已驳回。
 */
@Data
@TableName("asset_depr_batch")
public class AssetDeprBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 批号 */
    private String batchNo;
    /** 计提期间(YYYY-MM) */
    private String period;
    /** 待计提资产数 */
    private Integer assetCount;
    /** 应计提总额 */
    private BigDecimal totalAmount;
    /** DRAFT/APPROVING/DONE/REJECTED */
    private String status;
    /** 执行结果明细(JSON) */
    private String resultJson;
    /** 创建人 */
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新人 */
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    /** 删除标记 */
    private Integer delFlag;
}