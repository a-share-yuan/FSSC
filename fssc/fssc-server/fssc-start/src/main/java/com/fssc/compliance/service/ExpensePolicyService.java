package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.ExpensePolicy;
import com.fssc.compliance.entity.ExpensePolicyStandard;
import com.fssc.compliance.mapper.ExpensePolicyMapper;
import com.fssc.workflow.entity.WfFlowConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpensePolicyService {

    private final ExpensePolicyMapper expensePolicyMapper;
    private final ExpensePolicyStandardService expensePolicyStandardService;
    private final ApprovalRuleService approvalRuleService;
    private final ObjectMapper objectMapper;

    public PageResult<ExpensePolicy> page(long pageNum, long pageSize, String expenseType, Integer status) {
        LambdaQueryWrapper<ExpensePolicy> qw = new LambdaQueryWrapper<>();
        if (expenseType != null && !expenseType.isBlank()) {
            qw.eq(ExpensePolicy::getExpenseType, expenseType);
        }
        if (status != null) {
            qw.eq(ExpensePolicy::getStatus, status);
        }
        qw.orderByDesc(ExpensePolicy::getId);
        Page<ExpensePolicy> p = expensePolicyMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public ExpensePolicy getById(Long id) {
        ExpensePolicy policy = expensePolicyMapper.selectById(id);
        if (policy == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "费用政策不存在: " + id);
        }
        return policy;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ExpensePolicy policy) {
        policy.setStatus(1);
        expensePolicyMapper.insert(policy);
        return policy.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ExpensePolicy policy) {
        ExpensePolicy exist = expensePolicyMapper.selectById(policy.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "费用政策不存在: " + policy.getId());
        }
        expensePolicyMapper.updateById(policy);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        ExpensePolicy policy = expensePolicyMapper.selectById(id);
        if (policy == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "费用政策不存在: " + id);
        }
        policy.setStatus(1);
        expensePolicyMapper.updateById(policy);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        ExpensePolicy policy = expensePolicyMapper.selectById(id);
        if (policy == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "费用政策不存在: " + id);
        }
        policy.setStatus(0);
        expensePolicyMapper.updateById(policy);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExpensePolicy policy = expensePolicyMapper.selectById(id);
        if (policy == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "费用政策不存在: " + id);
        }
        expensePolicyMapper.deleteById(id);
        log.info("删除费用政策: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchEnable(List<Long> ids) {
        ids.forEach(this::enable);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDisable(List<Long> ids) {
        ids.forEach(this::disable);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        ids.forEach(this::delete);
    }

    public List<ExpensePolicy> listEffective(String expenseType, Long orgId) {
        LambdaQueryWrapper<ExpensePolicy> qw = new LambdaQueryWrapper<>();
        qw.eq(ExpensePolicy::getStatus, 1);
        if (expenseType != null && !expenseType.isBlank()) {
            qw.eq(ExpensePolicy::getExpenseType, expenseType);
        }
        if (orgId != null) {
            qw.eq(ExpensePolicy::getOrgId, orgId);
        }
        LocalDate today = LocalDate.now();
        qw.and(w -> w.isNull(ExpensePolicy::getEffectiveStart).or().le(ExpensePolicy::getEffectiveStart, today));
        qw.and(w -> w.isNull(ExpensePolicy::getEffectiveEnd).or().ge(ExpensePolicy::getEffectiveEnd, today));
        qw.orderByDesc(ExpensePolicy::getPriority);
        return expensePolicyMapper.selectList(qw);
    }

    public ExpensePolicy getEffectivePolicy(String expenseType, Long orgId) {
        List<ExpensePolicy> policies = listEffective(expenseType, orgId);
        if (policies.isEmpty()) {
            log.warn("未找到费用类型 {} 的生效政策", expenseType);
            return null;
        }
        return policies.get(0);
    }

    public Map<String, Object> evaluatePolicy(String expenseType, BigDecimal amount, Long orgId,
                                                String employeeLevel, boolean isOverseas, boolean isOvertime) {
        Map<String, Object> result = new HashMap<>();
        result.put("passed", true);
        result.put("warnings", new ArrayList<String>());
        result.put("approvalRoute", new ArrayList<>());

        ExpensePolicy policy = getEffectivePolicy(expenseType, orgId);
        if (policy == null) {
            result.put("passed", true);
            result.put("message", "未找到适用政策，按默认规则处理");
            return result;
        }

        result.put("policyId", policy.getId());
        result.put("policyName", policy.getPolicyName());

        if (amount != null) {
            List<ExpensePolicyStandard> standards = expensePolicyStandardService.listByPolicy(policy.getId());
            for (ExpensePolicyStandard std : standards) {
                if ("AMOUNT".equals(std.getStandardType()) && std.getStandardValueMax() != null) {
                    if (amount.compareTo(std.getStandardValueMax()) > 0) {
                        String overtimeAction = std.getOvertimeAction();
                        if ("BLOCK".equals(overtimeAction)) {
                            result.put("passed", false);
                            ((List<String>) result.get("warnings")).add(
                                    String.format("金额超出标准: 标准上限%s，申请%s",
                                            std.getStandardValueMax(), amount));
                        } else if ("WARN".equals(overtimeAction)) {
                            ((List<String>) result.get("warnings")).add(
                                    String.format("金额超出标准: 标准上限%s，申请%s，已标记警告",
                                            std.getStandardValueMax(), amount));
                        }
                    }
                }
            }
        }

        log.info("政策评估完成: expenseType={}, amount={}, passed={}", expenseType, amount, result.get("passed"));
        return result;
    }

    public BigDecimal getAvailableQuota(String expenseType, Long orgId) {
        ExpensePolicy policy = getEffectivePolicy(expenseType, orgId);
        if (policy == null) {
            return null;
        }

        BigDecimal totalQuota = BigDecimal.ZERO;
        List<ExpensePolicyStandard> standards = expensePolicyStandardService.listByPolicy(policy.getId());
        for (ExpensePolicyStandard std : standards) {
            if ("QUOTA".equals(std.getStandardType()) && std.getStandardValueMax() != null) {
                totalQuota = totalQuota.add(std.getStandardValueMax());
            }
        }
        return totalQuota;
    }

    public List<WfFlowConfig> calculateApprovalRoute(String expenseType, BigDecimal amount,
                                                              Long orgId, String employeeLevel,
                                                              boolean isOverseas, boolean isOvertime) {
        List<WfFlowConfig> route = new ArrayList<>();

        ExpensePolicy policy = getEffectivePolicy(expenseType, orgId);
        if (policy == null) {
            log.warn("未找到适用政策，返回默认审批路由");
            return route;
        }

        List<WfFlowConfig> rules = approvalRuleService.listByPolicy(policy.getId());
        for (WfFlowConfig rule : rules) {
            if (matchesRule(rule, amount, employeeLevel, isOverseas, isOvertime)) {
                route.add(rule);
            }
        }

        route.sort((a, b) -> Integer.compare(
                b.getSort() != null ? b.getSort() : 0,
                a.getSort() != null ? a.getSort() : 0));

        log.info("审批路由计算完成: expenseType={}, routeSize={}", expenseType, route.size());
        return route;
    }

    private boolean matchesRule(WfFlowConfig rule, BigDecimal amount, String employeeLevel,
                                boolean isOverseas, boolean isOvertime) {
        String ruleType = getJsonStr(rule.getCfgJson(), "ruleType");
        String ruleValue = getJsonStr(rule.getCfgJson(), "ruleValue");
        String ruleOperator = getJsonStr(rule.getCfgJson(), "ruleOperator");

        if (ruleType == null || ruleValue == null) {
            return true;
        }

        if ("AMOUNT".equals(ruleType) && amount != null) {
            try {
                BigDecimal threshold = new BigDecimal(ruleValue);
                if (">=".equals(ruleOperator)) {
                    return amount.compareTo(threshold) >= 0;
                } else if (">".equals(ruleOperator)) {
                    return amount.compareTo(threshold) > 0;
                } else if ("<=".equals(ruleOperator)) {
                    return amount.compareTo(threshold) <= 0;
                } else if ("<".equals(ruleOperator)) {
                    return amount.compareTo(threshold) < 0;
                } else if ("==".equals(ruleOperator)) {
                    return amount.compareTo(threshold) == 0;
                }
            } catch (NumberFormatException e) {
                log.warn("规则值解析失败: ruleId={}, value={}", rule.getId(), ruleValue);
            }
        }

        if ("LEVEL".equals(ruleType) && employeeLevel != null) {
            return ruleValue.equals(employeeLevel);
        }

        if ("OVERSEAS".equals(ruleType)) {
            return isOverseas == Boolean.parseBoolean(ruleValue);
        }

        if ("OVERTIME".equals(ruleType)) {
            return isOvertime == Boolean.parseBoolean(ruleValue);
        }

        return true;
    }

    private String getJsonStr(String cfgJson, String key) {
        JsonNode node = getJsonNode(cfgJson);
        return node != null && node.has(key) && !node.get(key).isNull() ? node.get(key).asText() : null;
    }

    private JsonNode getJsonNode(String cfgJson) {
        try {
            return objectMapper.readTree(cfgJson);
        } catch (Exception e) {
            return null;
        }
    }
}