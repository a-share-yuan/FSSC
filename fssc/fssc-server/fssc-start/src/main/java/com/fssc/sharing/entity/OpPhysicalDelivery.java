// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实物流签收单（op_physical_delivery，带 tenant_id）。
 * 行业特性 TC12：电子流 + 实物流双到达。传统交单模式下，
 * 实物未签收 → 共享会计待办不显示对应审核任务。
 */
@Data
@TableName("op_physical_delivery")
public class OpPhysicalDelivery implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;
    private String billNo;
    private String billType;
    /** 交付人（实物经办） */
    private Long deliverBy;
    private String deliverName;
    /** 签收人（共享会计/档案岗） */
    private Long signBy;
    private String signName;
    /** 领用/交接方式：POST 邮寄 / HANDOVER 当面交接 / OTHER */
    private String deliveryType;
    /** 备注 */
    private String remark;
    /** 快递公司 */
    private String expressCompany;
    /** 运单号 */
    private String expressNo;
    /** 条码值（条码枪扫描） */
    private String barcode;
    /** 件数 */
    private Integer packageCount;
    /** 拒签原因 */
    private String rejectReason;
    /** 签收备注 */
    private String signRemark;
    /** PENDING待签收 / SIGNED已签收 / REJECTED已拒签 */
    private String status;
    private LocalDateTime deliverTime;
    private LocalDateTime signTime;

    /** 关联单据状态（仅出参，page 时批量填充；DRAFT/CANCELLED 时 UI 隐藏签收/拒签并给原因） */
    @TableField(exist = false)
    private String billStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}