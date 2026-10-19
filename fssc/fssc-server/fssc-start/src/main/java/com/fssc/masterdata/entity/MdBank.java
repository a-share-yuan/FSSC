package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 金融机构（银行主数据）（需求 2.10）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_bank")
public class MdBank extends TenantAuditBase {
    private static final long serialVersionUID = 1L;
    /** 银行编码 */
    private String bankCode;
    /** 银行名称 */
    private String bankName;
    /** 银行类型（国有/股份/外资等） */
    private String bankType;
    /** 状态 */
    private String status;
    /** 备注 */
    private String remark;
}
