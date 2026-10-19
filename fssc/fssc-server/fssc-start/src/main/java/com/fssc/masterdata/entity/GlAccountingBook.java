package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会计账簿（V1.4.87）——闭环差距③「多账簿零代码」。
 *
 * <p><b>对齐口径</b>：
 * <ul>
 *       ⇒ 本表的 {@code uk_gab_main} 唯一索引（company+standard+year+book_type）
 *       是这条规则的<b>数据库级兜底</b>，不靠应用层 if-else；</li>
 *       ⇒ 同一 company 在不同 standard 下各有一本 MAIN 账簿，互不冲突。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_accounting_book")
public class GlAccountingBook extends TenantAuditBase {

    private String bookCode;

    private String bookName;

    /** MAIN 主账簿 / SUB 副账簿 */
    private String bookType;

    /** 核算组织 sys_company.id */
    private Long companyId;

    /** 会计准则 gl_standard.id */
    private Long standardId;

    /** 本位币 gl_currency.id */
    private Long baseCurrencyId;

    private Integer accountingYear;

    /** 会计年度起始日（非 1 月 1 日时用，如 4 月制） */
    private LocalDate fiscalStart;

    private LocalDate fiscalEnd;

    /** OPEN 启用 / CLOOSED 已关账 */
    private String status;

    private LocalDateTime closedTime;

    private String remark;
}
