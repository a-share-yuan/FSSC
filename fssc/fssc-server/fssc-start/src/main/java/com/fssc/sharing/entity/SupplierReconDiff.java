// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

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
 * 供应商对账差异调节表（supplier_recon_diff）。
 * 余额调节表 + 差异过账调整。
 * 记录对账过程中发现的每一项差异及其处理方式。
 */
@Data
@TableName("supplier_recon_diff")
public class SupplierReconDiff implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 所属对账单ID */
    private Long reconId;
    /** 差异类型：AMOUNT_DIFF 金额差异 / MISSING_INVOICE 缺发票 / MISSING_PAYMENT 缺付款 / TIMING_DIFF 时间性差异 / OTHER 其他 */
    private String diffType;
    /** 我方金额 */
    private BigDecimal ourAmount;
    /** 供应商金额 */
    private BigDecimal supplierAmount;
    /** 差异金额 */
    private BigDecimal diffAmount;
    /** 差异原因 */
    private String reason;
    /** 调整方式：NONE 无需调整 / ADJUST_INVOICE 调整发票 / ADJUST_PAYMENT 调整付款 / WRITE_OFF 核销 */
    private String adjustmentType;
    /** 调整凭证号 */
    private String adjustmentVoucherNo;
    /** 处理状态：PENDING 待处理 / RESOLVED 已处理 */
    private String status;
    private String remark;
    // 同资金四表一样是裸字段：不标 fill 则 MP 的 INSERT/UPDATE 列清单不含该列，MetaObjectHandler 填值无效
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
