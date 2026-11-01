// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 税务风险指标（tax_risk_indicator）。
 * 风险评分/等级/预警：进销项比对异常、税负率偏低、发票疑点等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tax_risk_indicator")
public class TaxRiskIndicator extends TenantAuditBase {

    private Long companyId;
    private String indicatorCode;
    private String indicatorName;
    /** INPUT_OUTPUT_RATIO/TAX_BURDEN/INVOICE_ANOMALY/DECLARE_GAP */
    private String riskType;
    private String period;
    private BigDecimal thresholdValue;
    private BigDecimal actualValue;
    private Integer riskScore;
    /** NORMAL/ATTENTION/WARNING/HIGH */
    private String riskLevel;
    private String description;
    private String suggestion;
    private Integer resolved;
    private Long resolvedBy;
    private LocalDateTime resolvedTime;
    private String resolvedRemark;
}
