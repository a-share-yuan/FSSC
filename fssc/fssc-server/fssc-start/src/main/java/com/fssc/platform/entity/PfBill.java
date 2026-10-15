package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.PlatformBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 租户账单（pf_bill）。
 * 【平台表-不参与租户改写】ref_tenant_id 为业务外键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pf_bill")
public class PfBill extends PlatformBaseEntity {

    /** 账单号（全局唯一） */
    private String billNo;
    /** 租户ID【业务外键,非隔离字段】 */
    private Long refTenantId;
    private Long orderId;
    /** 账期 yyyy-MM */
    private String billPeriod;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    /** UNPAID未结清/PAID已结清/OVERDUE已逾期/VOID已作废 */
    private String status;
    private LocalDate dueDate;
    private String remark;
}
