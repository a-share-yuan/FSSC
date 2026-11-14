package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 税金计提规则（tax_provision_rule）。
 *
 * <p>：把原先写死在 {@code GlTaxProvisionService} 里的「税种／计税基数／税率／借贷科目」
 * 三条常量搬进可配置表——换客户只改数据、不改码。
 *
 * <p>本表同时是 （缴税走对公付款通道时借方取 2221 明细）与 （申报确认出应缴凭证）
 * 的**唯一取数口**：两条腿的科目都从这里取，不再各自硬编码一份。
 *
 * <p>科目一律存 {@code md_subject.subject_code}（不存 id）：科目表按公司维度各建一套，
 * 同一科目码在不同公司下 id 不同，存 id 会让「规则跟着公司漂」。运行时按 code 解析 id。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tax_provision_rule")
public class TaxProvisionRule extends TenantAuditBase {

    /** 税种码：VAT/SURCHARGE/INCOME/STAMP/PROPERTY/LAND/VEHICLE/IIT */
    private String taxType;
    /** 规则名（展示用，如「增值税-月度」） */
    private String ruleName;
    /** 计税基数来源：MANUAL 手工录入 / DECLARATION 按已申报数汇总 / INVOICE 按发票池可抵扣额汇总 */
    private String baseSource;
    /** 手工基数来源时的缺省计税基数 */
    private BigDecimal baseAmount;
    /** 税率（百分数，13.00 表示 13%） */
    private BigDecimal taxRate;
    /** 计提凭证借方科目码（6403 税金及附加 / 6801 所得税费用） */
    private String debitSubjectCode;
    /** 计提凭证贷方科目码（应交税费明细，如 22210101） */
    private String creditSubjectCode;
    /** 申报凭证贷方科目码（未交税金，通常 2221）；与借方同码时申报出证自动跳过 */
    private String declareCreditSubjectCode;
    /** 缴纳凭证借方科目码（通常 2221） */
    private String payDebitSubjectCode;
    /** 纳税周期：MONTHLY/QUARTERLY/HALF_YEARLY/ANNUAL/PER_TIME */
    private String periodType;
    /** 申报截止日（月内第几日， 到期提醒用） */
    private Integer declareDeadlineDay;
    /** 缴款截止日（月内第几日） */
    private Integer payDeadlineDay;
    private Integer sort;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
