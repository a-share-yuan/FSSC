// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 打印模板字段项。
 *
 * <p><b>字段属性对齐通行规则</b>：单据类型支持「布局、字段必录和锁定属性、业务流程以及打印的设置」。
 * 本表承载布局（row/col/span/mm）、必录（required）、锁定（locked）三项，
 * 打印引擎据此在打印前做必录校验、对锁定字段灰显处理。
 *
 * <p><b>数据源路径语法</b>（由前端渲染引擎解析，与后端查询解耦）：
 * <ul>
 *   <li>{@code head.billNo} —— 查询对象 head 的 billNo 属性</li>
 *   <li>{@code lines[].amount} —— 明细数组每一行的 amount（由 LINE_DETAIL 区域驱动循环）</li>
 *   <li>{@code system.today} —— 系统变量（今天/当前用户/公司名等）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_print_template_item")
public class MdPrintTemplateItem extends TenantAuditBase {

    /** 所属模板 */
    private Long templateId;

    /** 字段标识（模板内唯一） */
    private String itemKey;

    /** 显示标题 */
    private String label;

    /** 数据源路径：head.xxx / lines[].yyy / system.zzz */
    private String dataSource;

    /**
     * 字段类型：FIELD 普通字段 / LINE_DETAIL 明细行区 /
     * QR_CODE 二维码 / BARCODE 条码 / IMAGE 图片 / DIVIDER 分隔线。
     */
    private String itemType;

    /** 起始行（基于 template.grid_rows） */
    private Integer rowIndex;

    /** 起始列（基于 template.grid_cols） */
    private Integer colIndex;

    /** 跨行 */
    private Integer rowSpan;

    /** 跨列 */
    private Integer colSpan;

    /** 宽(mm)，为空按跨列自动均分 */
    private BigDecimal widthMm;

    /** 高(mm) */
    private BigDecimal heightMm;

    /** 字号(pt) */
    private Integer fontSize;

    /** NORMAL 常规 / BOLD 加粗 */
    private String fontWeight;

    /** LEFT / CENTER / RIGHT */
    private String align;

    /** 必录属性：1-必录（缺值时打印前拦截，不放行） */
    private Integer required;

    /** 锁定属性：1-打印时灰显不可改 */
    private Integer locked;

    /** 是否显示 */
    private Integer visible;

    /** TEXT 文本 / NUMBER 金额 / DATE 日期 / PERCENT 百分比 */
    private String formatType;

    /** 小数位（金额） */
    private Integer decimalPlaces;

    private Integer sortOrder;

    private String remark;
}
