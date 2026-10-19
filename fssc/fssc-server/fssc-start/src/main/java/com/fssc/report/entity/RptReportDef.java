// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.report.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报表注册目录（rpt_report_def）。
 * 预置分析报表体系元数据：编码、名称、分类、API路径、刷新策略、推荐图表类型。
 */
@Data
@TableName("rpt_report_def")
public class RptReportDef implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 报表编码（唯一） */
    private String reportCode;
    /** 报表名称 */
    private String reportName;
    /** 分类: EXPENSE/BUDGET/RECEIVABLE/TREASURY/ASSET/TAX/COMPLIANCE/OPS */
    private String category;
    /** 报表说明 */
    private String description;
    /** 后端API路径 */
    private String apiPath;
    /** 刷新类型: REALTIME/HOURLY/DAILY/MONTHLY */
    private String refreshType;
    /** 推荐图表: TABLE/BAR/LINE/PIE/COMBO */
    private String chartType;
    /** ACTIVE/INACTIVE */
    private String status;
    /** 同分类内排序 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
