package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 打印模板头（套打平台 / 单据设计器）。
 *
 * <p><b>为什么建这张表</b>：打印模板原先硬编码在
 * {@code fssc-web/src/composables/usePrintTemplate.js} 的 TEMPLATES 常量里。
 * 那意味着「新增一种单据的打印样式」＝改前端代码＋发版，模板不是数据而是代码，
 * 业务人员无法自助调整版式。本表把版式参数写入数据库，把「模板」变回可配置数据。
 *
 * <p><b>版式规则</b>：{@code grid_rows} × {@code grid_cols} 构成设计网格，
 * 字段项在网格上占位（见 {@link MdPrintTemplateItem}），
 * 前端渲染引擎按毫米把网格换算成绝对坐标，支持 A4/A5/自定义纸张与横纵向。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_print_template")
public class MdPrintTemplate extends TenantAuditBase {

    /** 模板编码（租户内唯一，含版本维度） */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /**
     * 业务单据类型：EXPENSE_CLAIM 费用报销 / CORP_PAY 对公付款 / CONTRACT 合同 /
     * INVOICE 发票 / LOAN 借款 / VOUCHER 记账凭证 / AR_STATEMENT 对账单 /
     * AP_REQUEST 付款申请。
     * <p>新增单据类型只需在此登记 + 建模板，<b>不需要改代码</b>。
     */
    private String bizType;

    /** 纸张：A4 / A5 / 自定义 */
    private String paperSize;

    /** 方向：PORTRAIT 纵向 / LANDSCAPE 横向 */
    private String orientation;

    private BigDecimal widthMm;

    private BigDecimal heightMm;

    private BigDecimal marginTopMm;

    private BigDecimal marginBottomMm;

    private BigDecimal marginLeftMm;

    private BigDecimal marginRightMm;

    /** 设计网格行数（字段项 row_index 的上界） */
    private Integer gridRows;

    /** 设计网格列数（字段项 col_index 的上界） */
    private Integer gridCols;

    /** 明细表跨页是否重复表头 */
    private Integer repeatHeader;

    /** 版本号：改模板不覆盖历史版本（同一 template_code 可多版本并存） */
    private Integer version;

    /** 是否系统预置：1-是（禁止删除）0-否（租户自建） */
    private Integer isSystem;

    /** ENABLED 启用 / DISABLED 停用 */
    private String status;

    private String remark;
}
