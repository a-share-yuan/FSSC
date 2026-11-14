package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 币种与本位币。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_currency")
public class GlCurrency extends TenantAuditBase {

    private String currencyCode;

    private String currencyName;

    private String symbol;

    /** 小数位（JPY=0, CNY=2） */
    private Integer scale;

    /** 汇率来源：sys_config ar.fx.{ccy} / HMDA / 手工 */
    private String rateSource;

    /** 是否本位币 */
    private Integer isBase;

    private String status;

    private String remark;
}
