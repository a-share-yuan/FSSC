package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** md_expense_standard（带 tenant_id，租户拦截器自动隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_expense_standard")
public class MdExpenseStandard extends TenantAuditBase {

    private String stdCode;
    private String stdName;
    private Long itemId;
    private Integer postLevelMin;
    private Integer postLevelMax;
    private String cityLevel;
    private Long companyId;
    private String stdType;
    private BigDecimal amount;
    private String currencyCode;
    private String controlType;
    private LocalDate effectDate;
    private LocalDate expireDate;
    private Integer priority;
    /** 1=境外标准档（海外报账②）；0/NULL=国内通用 */
    private Integer abroad;
    /** 国家档位：DEV(发达)/EMG(新兴)/OTH，NULL=境外通用档 */
    private String countryTier;
    /** 补贴/日（本位币，海外报账③：出差补贴按天自动核算查询） */
    private BigDecimal allowanceDaily;
}
