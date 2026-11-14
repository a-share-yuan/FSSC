package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** md_exchange_rate（带 tenant_id，租户拦截器自动隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_exchange_rate")
public class MdExchangeRate extends TenantAuditBase {

    private String fromCurrency;
    private String toCurrency;
    private String rateType;
    private BigDecimal rate;
    private LocalDate effectDate;
    private LocalDate expireDate;
    private String source;
}
