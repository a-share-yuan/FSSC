// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.collab.entity;

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
 * SCP 供应商对账单（AP 蓝图 SCC_AP_01/02、外网菜单8.1 对账单管理、页面9.1 序号1~3）：
 * 收货/折扣/索赔信息导入（E1）→ 创建预制对账单（E4）→ 异议反馈（E5）→ 确认对账单（E6，
 * 选增专/增普 R-30，确认完成即打通点1 三单匹配数据源）→ 申请开票（打通点2 生成 ScpBill）。
 * 状态机：PRELIM 预制待确认 → CONFIRMED 最终对账单；VOIDED 已作废。
 */
@Data
@TableName("collab_scp_recon")
public class ScpRecon implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 对账单号（三单匹配最小单位，打通点1） */
    private String reconNo;
    private String supplierCode;
    private String supplierName;
    /** 购方公司代码 */
    private String buyerCompanyCode;
    /** 专票/普票（确认对账单时选择，R-30） */
    private String invoiceType;
    private String ccy;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    /** 收货/折扣/索赔/退货明细行 JSON（E1 导入） */
    private String linesJson;
    /** 异议反馈（E5，供应商提交后内网处理） */
    private String objectionNote;
    /** NONE/SUBMITTED/RESOLVED */
    private String objectionStatus;
    /** PRELIM 预制待确认/CONFIRMED 最终对账单/VOIDED 已作废 */
    private String status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static final String ST_PRELIM = "PRELIM";
    public static final String ST_CONFIRMED = "CONFIRMED";
    public static final String ST_VOIDED = "VOIDED";

    public static final String OBJ_NONE = "NONE";
    public static final String OBJ_SUBMITTED = "SUBMITTED";
    public static final String OBJ_RESOLVED = "RESOLVED";
}
