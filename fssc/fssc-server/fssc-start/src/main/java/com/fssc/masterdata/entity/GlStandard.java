package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 会计准则（V1.4.87）。多准则出多套核算数据的基础。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_standard")
public class GlStandard extends TenantAuditBase {

    /** 准则编码：ASBE 企业会计准则 / SME 小企业会计准则 / GOV 政府会计制度 */
    private String standardCode;

    private String standardName;

    private LocalDate effectiveDate;

    private LocalDate expireDate;

    /** 该准则下的本位币 */
    private String baseCurrency;

    private String status;

    private String remark;
}
