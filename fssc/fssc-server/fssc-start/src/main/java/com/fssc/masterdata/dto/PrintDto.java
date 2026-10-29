// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.dto;

import java.util.Map;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 打印引擎输出 DTO。
 *
 * <p>后端把「模板 + 数据 + 版式换算」一次性算好下发，前端只做绘制与打印，
 * 避免把毫米换算/必录校验规则散落到每个前端页面。
 */
public class PrintDto {

    /** 模板完整视图（含字段项与绝对坐标） */
    @Data
    public static class TemplateView {
        private Long templateId;
        private String templateCode;
        private String templateName;
        private String bizType;
        private String paperSize;
        private String orientation;
        private BigDecimal widthMm;
        private BigDecimal heightMm;
        private BigDecimal marginTopMm;
        private BigDecimal marginBottomMm;
        private BigDecimal marginLeftMm;
        private BigDecimal marginRightMm;
        private Integer gridRows;
        private Integer gridCols;
        private Integer repeatHeader;
        private Integer version;
        private Integer isSystem;
        private String status;
        private List<ItemView> items;
    }

    /** 字段项视图（已换算为毫米绝对坐标，前端可直接定位） */
    @Data
    public static class ItemView {
        private Long itemId;
        private String itemKey;
        private String label;
        private String dataSource;
        private String itemType;
        private Integer rowIndex;
        private Integer colIndex;
        private Integer rowSpan;
        private Integer colSpan;
        /** 相对纸张左上角的绝对坐标（mm），由引擎算好 */
        private BigDecimal xMm;
        /** 绝对纵坐标（mm） */
        private BigDecimal yMm;
        private BigDecimal widthMm;
        private BigDecimal heightMm;
        private Integer fontSize;
        private String fontWeight;
        private String align;
        private Integer required;
        private Integer locked;
        private Integer visible;
        private String formatType;
        private Integer decimalPlaces;
        private Integer sortOrder;
        /** 已按 data_source 绑定好的值（渲染引擎直接用，不必再解析路径） */
        private String boundValue;
    }

    /** 渲染结果（含必录校验结论） */
    @Data
    public static class RenderResult {
        private TemplateView template;
        /** 渲染后的完整 HTML（服务端拼好，前端直接打印） */
        private String html;
        /** 必录校验：缺失的必录字段清单，空数组＝校验通过 */
        private List<MissingField> missingRequired;
        /** 是否有必录字段缺失（true 时前端应拦截，不放行打印） */
        private Boolean blocked;
    }

    /** 必录缺失项 */
    @Data
    public static class MissingField {
        private String itemKey;
        private String label;
        private String dataSource;

        public MissingField() {
        }

        public MissingField(String itemKey, String label, String dataSource) {
            this.itemKey = itemKey;
            this.label = label;
            this.dataSource = dataSource;
        }
    }

    /** 保存模板请求（套打设计器提交） */
    @Data
    public static class SaveTemplateRequest {
        private Long templateId;
        private String templateCode;
        private String templateName;
        private String bizType;
        /** 版本号：改模板走新版本而非覆盖历史 */
        private Integer version;
        private String paperSize;
        private String orientation;
        private BigDecimal widthMm;
        private BigDecimal heightMm;
        private BigDecimal marginTopMm;
        private BigDecimal marginBottomMm;
        private BigDecimal marginLeftMm;
        private BigDecimal marginRightMm;
        private Integer gridRows;
        private Integer gridCols;
        private Integer repeatHeader;
        private String status;
        private String remark;
        private List<ItemRequest> items;
    }

    /** 字段项保存请求 */
    @Data
    public static class ItemRequest {
        private String itemKey;
        private String label;
        private String dataSource;
        private String itemType;
        private Integer rowIndex;
        private Integer colIndex;
        private Integer rowSpan;
        private Integer colSpan;
        private BigDecimal widthMm;
        private BigDecimal heightMm;
        private Integer fontSize;
        private String fontWeight;
        private String align;
        private Integer required;
        private Integer locked;
        private Integer visible;
        private String formatType;
        private Integer decimalPlaces;
        private Integer sortOrder;
        private String remark;
    }

    /** 渲染请求 */
    @Data
    public static class RenderRequest {
        /** 模板编码（与 bizType 二选一，优先 templateCode） */
        private String templateCode;
        /** 业务单据类型（按 bizType 取该类型下启用的模板） */
        private String bizType;
        /** 模板套编码（传则按套内顺序渲染多个模板） */
        private String setCode;
        /** 查询对象：{head:{...}, lines:[...]} */
        private Map<String, Object> data;
        /** 是否服务端直接渲染 HTML（默认 true；false 只返回模板与坐标，前端自绘） */
        private Boolean renderHtml;
    }

    /** 模板套视图 */
    @Data
    public static class SetView {
        private Long setId;
        private String setCode;
        private String setName;
        private String bizType;
        private String status;
        private List<SetMember> members;
    }

    /** 模板套成员视图 */
    @Data
    public static class SetMember {
        private Integer pageOrder;
        private Integer copies;
        private TemplateView template;
    }
}
