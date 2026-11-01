// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.ExpensePolicy;
import com.fssc.compliance.mapper.ExpensePolicyMapper;
import com.fssc.workflow.entity.WfFlowConfig;
import com.fssc.workflow.mapper.WfFlowConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalRuleService {

    private final WfFlowConfigMapper wfFlowConfigMapper;
    private final ExpensePolicyMapper expensePolicyMapper;
    private final ObjectMapper objectMapper;

    public List<WfFlowConfig> listByPolicy(Long policyId) {
        LambdaQueryWrapper<WfFlowConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(WfFlowConfig::getCfgType, "APPROVAL_RULE")
                .orderByAsc(WfFlowConfig::getSort);
        List<WfFlowConfig> configs = wfFlowConfigMapper.selectList(qw);
        return configs.stream()
                .filter(c -> {
                    Long pid = getJsonLong(c.getCfgJson(), "policyId");
                    return policyId.equals(pid);
                })
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createRule(WfFlowConfig rule) {
        rule.setStatus("NORMAL");
        rule.setCfgType("APPROVAL_RULE");
        wfFlowConfigMapper.insert(rule);
        return rule.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRule(WfFlowConfig rule) {
        WfFlowConfig exist = wfFlowConfigMapper.selectById(rule.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审批规则不存在: " + rule.getId());
        }
        wfFlowConfigMapper.updateById(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        WfFlowConfig exist = wfFlowConfigMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审批规则不存在: " + id);
        }
        wfFlowConfigMapper.deleteById(id);
    }

    public WfFlowConfig getMatchedRule(String expenseType, BigDecimal amount, Long orgId) {
        LambdaQueryWrapper<ExpensePolicy> policyQw = new LambdaQueryWrapper<>();
        policyQw.eq(ExpensePolicy::getStatus, 1);
        if (expenseType != null && !expenseType.isBlank()) {
            policyQw.eq(ExpensePolicy::getExpenseType, expenseType);
        }
        if (orgId != null) {
            policyQw.eq(ExpensePolicy::getOrgId, orgId);
        }
        policyQw.orderByDesc(ExpensePolicy::getPriority).last("limit 1");
        ExpensePolicy policy = expensePolicyMapper.selectOne(policyQw);
        if (policy == null) {
            return null;
        }

        LambdaQueryWrapper<WfFlowConfig> ruleQw = new LambdaQueryWrapper<>();
        ruleQw.eq(WfFlowConfig::getCfgType, "APPROVAL_RULE")
                .eq(WfFlowConfig::getStatus, "NORMAL")
                .orderByAsc(WfFlowConfig::getSort);
        List<WfFlowConfig> rules = wfFlowConfigMapper.selectList(ruleQw);

        for (WfFlowConfig rule : rules) {
            Long rulePolicyId = getJsonLong(rule.getCfgJson(), "policyId");
            if (!policy.getId().equals(rulePolicyId)) {
                continue;
            }
            String ruleType = getJsonStr(rule.getCfgJson(), "ruleType");
            if ("AMOUNT".equals(ruleType) && isAmountRuleMatched(rule, amount)) {
                return rule;
            }
        }

        for (WfFlowConfig rule : rules) {
            Long rulePolicyId = getJsonLong(rule.getCfgJson(), "policyId");
            if (!policy.getId().equals(rulePolicyId)) {
                continue;
            }
            Integer isDefault = getJsonInt(rule.getCfgJson(), "isDefault");
            if (isDefault != null && isDefault == 1) {
                return rule;
            }
        }

        return null;
    }

    private boolean isAmountRuleMatched(WfFlowConfig rule, BigDecimal amount) {
        String ruleValue = getJsonStr(rule.getCfgJson(), "ruleValue");
        String ruleOperator = getJsonStr(rule.getCfgJson(), "ruleOperator");
        if (amount == null || ruleValue == null) {
            return false;
        }
        try {
            BigDecimal threshold = new BigDecimal(ruleValue);
            if (">=".equals(ruleOperator)) {
                return amount.compareTo(threshold) >= 0;
            } else if ("<=".equals(ruleOperator)) {
                return amount.compareTo(threshold) <= 0;
            } else if ("==".equals(ruleOperator)) {
                return amount.compareTo(threshold) == 0;
            } else if (">".equals(ruleOperator)) {
                return amount.compareTo(threshold) > 0;
            } else if ("<".equals(ruleOperator)) {
                return amount.compareTo(threshold) < 0;
            }
        } catch (NumberFormatException e) {
            log.warn("规则值解析失败: ruleId={}, value={}", rule.getId(), ruleValue);
        }
        return false;
    }

    private String getJsonStr(String cfgJson, String field) {
        try {
            JsonNode node = objectMapper.readTree(cfgJson);
            return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getJsonInt(String cfgJson, String field) {
        try {
            JsonNode node = objectMapper.readTree(cfgJson);
            return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long getJsonLong(String cfgJson, String field) {
        try {
            JsonNode node = objectMapper.readTree(cfgJson);
            return node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}