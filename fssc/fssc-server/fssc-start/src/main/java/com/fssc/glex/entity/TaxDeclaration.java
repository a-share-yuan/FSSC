package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 全税种申报表（tax_declaration）。
 * 覆盖增值税/企业所得税/个税/印花税/房产税/土地使用税/车船税等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tax_declaration")
public class TaxDeclaration extends TenantAuditBase {

    private Long companyId;
    private String declarationNo;
    /** VAT/CIT/IIT/STAMP/PROPERTY/LAND/VEHICLE */
    private String taxType;
    /** VAT-GENERAL/VAT-SMALL */
    private String taxSubtype;
    private String period;
    /** MONTHLY/QUARTERLY/ANNUAL */
    private String declareType;
    private BigDecimal baseAmount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    /** DRAFT/PENDING/APPROVED/FILED/PAID/CANCELLED */
    private String status;
    private LocalDate declareDate;
    private LocalDate payDeadline;
    private Long sourceVoucherId;
    private String attachmentUrl;
    private String remark;

    /**
     * 申报性质：NORMAL 正常申报 / ADJUST 调整（更正）申报。
     * 与「申报周期」分开表达；原先只有 {@link #declareType}（月/季/年，是申报周期不是性质），
     * 两个概念混在一列会让「按季的正常申报」与「年度的更正申报」无法区分。
     */
    private String declarationKind;

    /** 更正申报关联的原申报单 id（仅 declarationKind=ADJUST 时有值），用于勾稽「改了哪张表」 */
    private Long relatedId;
}
