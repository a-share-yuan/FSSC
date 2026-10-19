package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 借款付款类型（需求 2.26）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_loan_pay_type")
public class MdLoanPayType extends TenantAuditBase {
    private static final long serialVersionUID = 1L;
    /** 付款类型编码 */
    private String typeCode;
    /** 付款类型名称 */
    private String typeName;
    /** 状态 */
    private String status;
    /** 备注 */
    private String remark;
}
