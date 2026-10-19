package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 税务退税三场景（tax_refund，带 tenant_id）。
 * 场景：EXPORT_REFUND 出口免抵退 / CREDIT_REFUND 留抵退税 / IMMEDIATE_REFUND 即征即退。
 * 状态：DRAFT → APPLYING → APPROVED → REFUNDING → REFUNDED（驳回 REJECTED）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tax_refund")
public class TaxRefund extends TenantAuditBase {

    /** 公司ID */
    private Long companyId;
    /** 退税申请单号 */
    private String refundNo;
    /** EXPORT_REFUND/CREDIT_REFUND/IMMEDIATE_REFUND */
    private String refundType;
    /** 所属期 yyyyMM */
    private String period;
    /** 计税基数：出口免抵退=出口销售额 / 即征即退=已缴增值税额 */
    private BigDecimal baseAmount;
    /** 退税率（百分数） */
    private BigDecimal rate;
    /** 可退税额 */
    private BigDecimal taxAmount;
    /** 已到账金额 */
    private BigDecimal refundedAmount;
    /** 留抵退税：可退留抵税额上限 */
    private BigDecimal avgQuota;
    /** DRAFT/APPLYING/APPROVED/REFUNDING/REFUNDED/REJECTED */
    private String status;
    /** 申请用户 */
    private Long applyBy;
    /** 申请时间 */
    private LocalDateTime applyTime;
    /** 备注 */
    private String remark;
}