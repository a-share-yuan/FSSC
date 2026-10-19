package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 内部往来抵消（gl_inter_offset，带 tenant_id）。公司间应收/应付往来生成抵消分录，DRAFT → OFFSET。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_inter_offset")
public class GlInterOffset extends TenantAuditBase {

    /** 抵消单号 */
    private String offsetNo;
    /** 会计期间 yyyyMM */
    private String period;
    /** 来账公司 sys_company.id */
    private Long fromCompanyId;
    /** 往账公司 sys_company.id */
    private Long toCompanyId;
    /** RECEIVABLE应收/PAYABLE应付 */
    private String interFlag;
    /** 抵消金额 */
    private BigDecimal amount;
    /** DRAFT/OFFSET */
    private String offsetStatus;
    /** 记账凭证号 */
    private String voucherNo;
}