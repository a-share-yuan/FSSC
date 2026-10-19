package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.PlatformBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订购续费订单（pf_order）。
 * 【平台表-不参与租户改写】ref_tenant_id 为业务外键（记录租户归属），绝不叫 tenant_id。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pf_order")
public class PfOrder extends PlatformBaseEntity {

    /** 订单号（全局唯一） */
    private String orderNo;
    /** 租户ID【业务外键,非隔离字段】 */
    private Long refTenantId;
    private Long packageId;
    /** NEW新购/RENEW续费/UPGRADE升级 */
    private String orderType;
    /** 时长（月） */
    private Integer duration;
    private BigDecimal amount;
    private BigDecimal payAmount;
    /** UNPAID/PAID/REFUNDED/CANCELLED */
    private String payStatus;
    private LocalDateTime payTime;
    /** OFFLINE/ALIPAY/WECHAT */
    private String payMethod;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;
}
