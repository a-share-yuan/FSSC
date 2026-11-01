// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.skill;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fssc.ai.AiGatewayService;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能注册表条目（方案 §5.2 /）。
 *
 * <p>技能是"业务语言"与"目录动作"之间的一层策展：一个技能可绑定一个或多个 backing 动作码，
 * 但执行仍走 {@link AiGatewayService} 的同一套红线/权限/票据校验——技能只改"怎么理解"，
 * 不改"能不能做"。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Skill {

    private String name;
    /** read / write / knowledge / nav */
    private String type;
    private String title;
    private String description;
    private List<String> examples = new ArrayList<>();
    /** 确定性路由：问题须至少命中一个 */
    private List<String> keywords = new ArrayList<>();
    /** 确定性路由：非空时还须至少命中一个动词/查询词 */
    private List<String> verbs = new ArrayList<>();
    /** 命中任一即否决（防止"报销流程"被读技能抢走） */
    private List<String> exclude = new ArrayList<>();
    private int priority;
    private List<String> backing = new ArrayList<>();
    /** 基础查询参数（分页等） */
    private Map<String, Object> args = new LinkedHashMap<>();

    /** none / range / period / yearMonth */
    private String dateStyle = "none";
    private String dateStartField;
    private String dateEndField;
    private String yearField;
    private String monthField;
    /** monthPeriod 用：YYYYMM */
    private String periodField;
    private String statusField;
    private String keywordField;
    /** 条件参数：问题含 contains 时置 field=value（如「逾期借款」→ onlyOverdue=true）。 */
    private List<Map<String, Object>> flags = new ArrayList<>();
    /** 读技能固定单据类型（bill.myPage 的 billType） */
    private String billType;
    /** 只有日期表达式、没有查询动词时也允许命中（如「2025年12月的报销单」） */
    private boolean allowWhenDated;
    /** 写技能是否必须有金额 */
    private boolean amountRequired;
    /** 写技能草稿标题前缀（如 借款-1,000.00） */
    private String titlePrefix;

    private List<String> topics = new ArrayList<>();
    private Map<String, String> routes = new LinkedHashMap<>();

    /** 构建期从 backing 动作派生的权限/风险（仅展示与排障用，执行仍以目录+网关为准）。 */
    @JsonProperty("derived")
    private Derived derived;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Derived {
        private List<String> permCodes = new ArrayList<>();
        private List<String> risk = new ArrayList<>();
        private List<String> urls = new ArrayList<>();
        private List<String> kinds = new ArrayList<>();
    }

    public boolean isRead() { return "read".equals(type); }
    public boolean isWrite() { return "write".equals(type); }
    public boolean isKnowledge() { return "knowledge".equals(type); }
    public boolean isNav() { return "nav".equals(type); }

    /** 确定性路由的否决词检查。 */
    public boolean excluded(String q) {
        for (String e : exclude) {
            if (e != null && !e.isBlank() && q.contains(e)) {
                return true;
            }
        }
        return false;
    }
}
