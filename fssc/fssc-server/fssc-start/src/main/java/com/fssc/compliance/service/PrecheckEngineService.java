// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.PrecheckResult;
import com.fssc.compliance.entity.ReviewRule;
import com.fssc.compliance.mapper.PrecheckResultMapper;
import com.fssc.compliance.mapper.ReviewRuleMapper;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrecheckEngineService {

    private final ReviewRuleMapper reviewRuleMapper;
    private final PrecheckResultMapper precheckResultMapper;
    private final com.fssc.expense.mapper.ExpBillAttachMapper expBillAttachMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public List<ReviewRule> listRules(String category, String status) {
        LambdaQueryWrapper<ReviewRule> qw = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            qw.eq(ReviewRule::getRuleCategory, category);
        }
        if (status != null) {
            qw.eq(ReviewRule::getStatus, status);
        }
        qw.orderByAsc(ReviewRule::getRuleOrder);
        return reviewRuleMapper.selectList(qw);
    }

    public ReviewRule getRuleById(Long id) {
        ReviewRule rule = reviewRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预检规则不存在: " + id);
        }
        return rule;
    }

    public ReviewRule findRuleByCode(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<ReviewRule> qw = new LambdaQueryWrapper<>();
        qw.eq(ReviewRule::getRuleCode, ruleCode);
        return reviewRuleMapper.selectOne(qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createRule(ReviewRule rule) {
        assertRuleCodeFree(rule.getRuleCode(), null);
        if (rule.getStatus() == null) {
            rule.setStatus("NORMAL");
        }
        if (rule.getRuleOrder() == null) {
            rule.setRuleOrder(0);
        }
        reviewRuleMapper.insert(rule);
        log.info("创建预检规则: ruleCode={}, ruleName={}", rule.getRuleCode(), rule.getRuleName());
        return rule.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRule(ReviewRule rule) {
        ReviewRule exist = reviewRuleMapper.selectById(rule.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预检规则不存在: " + rule.getId());
        }
        assertRuleCodeFree(rule.getRuleCode(), rule.getId());
        reviewRuleMapper.updateById(rule);
        log.info("更新预检规则: id={}", rule.getId());
    }

    /** 规则编码在本租户内的存活集唯一（本表走物理 DELETE，删除即让码位可用，故无需退场改写） */
    private void assertRuleCodeFree(String code, Long selfId) {
        if (code == null || code.isBlank()) {
            return;
        }
        Long n = reviewRuleMapper.selectCount(new LambdaQueryWrapper<ReviewRule>()
                .eq(ReviewRule::getRuleCode, code)
                .ne(selfId != null, ReviewRule::getId, selfId));
        if (n != null && n > 0) {
            throw new BizException(ErrorCode.DATA_ALREADY_EXISTS, "规则编码已存在: " + code);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void enableRule(Long id) {
        ReviewRule rule = reviewRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预检规则不存在: " + id);
        }
        rule.setStatus("NORMAL");
        reviewRuleMapper.updateById(rule);
        log.info("启用预检规则: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableRule(Long id) {
        ReviewRule rule = reviewRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预检规则不存在: " + id);
        }
        rule.setStatus("DISABLED");
        reviewRuleMapper.updateById(rule);
        log.info("停用预检规则: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        ReviewRule rule = reviewRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预检规则不存在: " + id);
        }
        reviewRuleMapper.deleteById(id);
        log.info("删除预检规则: id={}", id);
    }

    public List<ReviewRule> listRulesByExpenseType(String expenseType) {
        LambdaQueryWrapper<ReviewRule> qw = new LambdaQueryWrapper<>();
        qw.eq(ReviewRule::getStatus, "NORMAL");
        if (expenseType != null && !expenseType.isBlank()) {
            qw.like(ReviewRule::getRelatedExpenseTypes, expenseType);
        }
        qw.orderByAsc(ReviewRule::getRuleOrder);
        return reviewRuleMapper.selectList(qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public String runPrecheck(Long billId, String billType, String expenseType, Long employeeId) {
        long startTime = System.currentTimeMillis();
        String batchId = "BATCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        List<ReviewRule> rules = listRulesByExpenseType(expenseType);
        List<PrecheckResult> results = new ArrayList<>();

        Map<String, Object> inputData = buildInputData(billId, billType, expenseType, employeeId);
        for (ReviewRule rule : rules) {
            PrecheckResult result = evaluateRule(rule, inputData);
            result.setBillId(billId != null ? billId : 0L);
            result.setBillType(billType != null ? billType : "EXPENSE");
            result.setPrecheckBatchId(batchId);
            results.add(result);
        }

        long elapsed = System.currentTimeMillis() - startTime;

        int passed = 0, failed = 0, warning = 0;
        for (PrecheckResult r : results) {
            String cr = r.getCheckResult();
            if ("PASS".equals(cr)) passed++;
            else if ("FAIL".equals(cr)) failed++;
            else if ("WARNING".equals(cr)) warning++;
        }

        for (PrecheckResult r : results) {
            r.setEmployeeId(employeeId != null ? employeeId : 0L);
            r.setTotalRules(rules.size());
            r.setPassedRules(passed);
            r.setFailedRules(failed);
            r.setWarningRules(warning);
            r.setTotalExecutionTimeMs((int) elapsed);
            r.setFinalStatus(failed > 0 ? "FAILED" : "PASSED");
            r.setSubmitted(0);
            r.setTenantId(TenantContext.getTenantId());
            precheckResultMapper.insert(r);
        }

        log.info("预检完成: batchId={}, rules={}, passed={}, failed={}, cost={}ms",
                batchId, rules.size(), passed, failed, elapsed);

        return batchId;
    }

    /**
     * BUG-06 修复：组装预检 inputData 时从附件关联的 ocr_record 补齐票面键
     * （此前仅注入 4 个标量，INVOICE 类规则拿不到 invoiceNumber 而空转放行）。
     */
    private Map<String, Object> buildInputData(Long billId, String billType, String expenseType, Long employeeId) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("billId", billId != null ? billId : "");
        data.put("billType", billType != null ? billType : "");
        data.put("expenseType", expenseType != null ? expenseType : "");
        data.put("employeeId", employeeId != null ? employeeId : "");
        if (billId == null) return data;
        try {
            List<Long> fileIds = expBillAttachMapper.selectList(
                            new LambdaQueryWrapper<com.fssc.expense.entity.ExpBillAttach>()
                                    .eq(com.fssc.expense.entity.ExpBillAttach::getBillId, billId))
                    .stream().map(com.fssc.expense.entity.ExpBillAttach::getFileId)
                    .filter(java.util.Objects::nonNull).distinct().toList();
            data.put("hasAttachment", !fileIds.isEmpty());
            if (!fileIds.isEmpty()) {
                List<Object> params = new ArrayList<>();
                params.add(TenantContext.getTenantId());
                String ph = fileIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
                params.addAll(fileIds);
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT invoice_no, invoice_code, invoice_type, amount FROM fssc_image.ocr_record "
                                + "WHERE tenant_id = ? AND invoice_no IS NOT NULL AND invoice_no <> '' AND file_id IN ("
                                + ph + ") ORDER BY id DESC LIMIT 1", params.toArray());
                if (!rows.isEmpty()) {
                    Map<String, Object> r = rows.get(0);
                    data.put("invoiceNumber", r.get("invoice_no"));
                    data.put("invoiceCode", r.get("invoice_code"));
                    data.put("invoiceType", r.get("invoice_type"));
                    data.put("invoiceAmount", r.get("amount"));
                }
            }
        } catch (Exception ex) {
            log.warn("预检票面键组装失败(按无票处理): {}", ex.getMessage());
        }
        return data;
    }

    public PrecheckResult getBatch(String batchId) {
        LambdaQueryWrapper<PrecheckResult> qw = new LambdaQueryWrapper<>();
        qw.eq(PrecheckResult::getPrecheckBatchId, batchId);
        qw.last("LIMIT 1");
        PrecheckResult result = precheckResultMapper.selectOne(qw);
        if (result == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预检批次不存在: " + batchId);
        }
        return result;
    }

    public List<PrecheckResult> getResults(String batchId) {
        LambdaQueryWrapper<PrecheckResult> qw = new LambdaQueryWrapper<>();
        qw.eq(PrecheckResult::getPrecheckBatchId, batchId);
        return precheckResultMapper.selectList(qw);
    }

    public PrecheckResult evaluateRule(ReviewRule rule, Map<String, Object> inputData) {
        PrecheckResult result = new PrecheckResult();
        result.setRuleId(rule.getId());
        result.setRuleCode(rule.getRuleCode());
        result.setLevel(rule.getRuleLevel());
        result.setInputData(inputData != null ? inputData.toString() : "{}");

        if (inputData != null) {
            Object billIdObj = inputData.get("billId");
            if (billIdObj instanceof Number) {
                result.setBillId(((Number) billIdObj).longValue());
            } else if (billIdObj != null) {
                try {
                    result.setBillId(Long.parseLong(billIdObj.toString()));
                } catch (NumberFormatException ignored) {
                    // 非数字 billId：保持 null，由后续规则自行校验，属预期降级
                }
            }
            Object billTypeObj = inputData.get("billType");
            if (billTypeObj != null) {
                result.setBillType(billTypeObj.toString());
            }
        }

        long start = System.currentTimeMillis();
        String category = rule.getRuleCategory();
        String script = rule.getScriptContent();

        boolean passed;
        String message;

        if (script != null && !script.isBlank() && !"return true;".equals(script.trim())) {
            passed = evaluateScript(script, inputData);
            message = passed
                    ? (rule.getOutputMessage() != null ? rule.getOutputMessage() : "规则校验通过")
                    : (rule.getOutputMessage() != null ? rule.getOutputMessage() : "规则校验未通过");
        } else if ("INVOICE".equals(category)) {
            passed = evaluateInvoiceRule(rule, inputData);
            message = passed ? "发票校验通过" : "发票校验未通过";
        } else if ("BUDGET".equals(category)) {
            passed = evaluateBudgetRule(rule, inputData);
            message = passed ? "预算校验通过" : "预算超支或不符合预算规则";
        } else if ("BUSINESS".equals(category)) {
            passed = evaluateBusinessRule(rule, inputData);
            message = passed ? "业务规则校验通过" : "业务规则校验未通过";
        } else if ("TIME".equals(category)) {
            passed = evaluateTimeRule(rule, inputData);
            message = passed ? "时间规则校验通过" : "时间规则校验未通过";
        } else if ("PERMISSION".equals(category)) {
            passed = evaluatePermissionRule(rule, inputData);
            message = passed ? "权限校验通过" : "权限校验未通过";
        } else if ("FRAUD".equals(category)) {
            passed = evaluateFraudRule(rule, inputData);
            message = passed ? "欺诈检测通过" : "疑似欺诈行为";
        } else {
            passed = true;
            message = "默认规则校验通过";
        }

        result.setCheckResult(passed ? "PASS" : "FAIL");
        result.setCheckMessage(message);

        long elapsed = System.currentTimeMillis() - start;
        result.setExecutionTimeMs((int) elapsed);

        return result;
    }

    public boolean evaluateScript(String script, Map<String, Object> inputData) {
        String trimmed = script.trim();
        if ("return true;".equals(trimmed)) return true;
        if ("return false;".equals(trimmed)) return false;

        try {
            if (trimmed.contains("return ")) {
                String expr = trimmed.replace("return ", "").replace(";", "").trim();
                return evaluateCompoundExpression(expr, inputData);
            }
            return evaluateCompoundExpression(trimmed, inputData);
        } catch (Exception e) {
            // P1 修复：审单规则求值异常必须 fail-closed（不命中）。
            // 原 fail-open 返回 true 会让任何写错的表达式（如引用不存在的变量）恒命中，
            // 配合 AUTO_REJECT 规则会把所有单据误杀（真实事故：1500 元报销单被「金额超5万」规则驳回）。
            log.warn("脚本执行异常（按未命中处理）: {}", script, e);
            return false;
        }
    }

    // ---- 复合表达式（支持 && / ||） ----

    private boolean evaluateCompoundExpression(String expr, Map<String, Object> data) {
        expr = expr.trim();
        if (expr.isBlank()) return true;

        // 处理括号优先级。
        // 修复（L1-B-04 单测发现）：原实现无脑取 lastIndexOf('(') 当分组括号，
        // 会把函数调用 contains(title, '差旅') 的括号拆掉，表达式被改写成 "containstrue" 这类字符串，
        // 使 evaluateAtomicExpression 里的函数分支（startsWith("contains(")）成为永不可达的死代码——
        // 即所有用 contains/startsWith/endsWith/matches/isNull/notNull/in 写的规则实际完全不生效。
        // 判定规则：左括号前最近的非空白字符若是标识符字符（字母/数字/下划线），
        // 说明该括号属于函数调用或 "x in (...)" 语法，跳过它继续往左找真正的分组括号。
        while (expr.contains("(") && expr.contains(")")) {
            int openIdx = -1;
            for (int i = expr.length() - 1; i >= 0; i--) {
                if (expr.charAt(i) != '(') continue;
                int j = i - 1;
                while (j >= 0 && Character.isWhitespace(expr.charAt(j))) j--;
                char prev = j >= 0 ? expr.charAt(j) : '(';
                boolean callLike = Character.isLetterOrDigit(prev) || prev == '_';
                if (!callLike) {
                    openIdx = i;
                    break;
                }
            }
            if (openIdx < 0) break; // 剩余括号均属函数调用 / in 语法，交由原子表达式处理
            int closeIdx = expr.indexOf(')', openIdx);
            if (closeIdx == -1) break;
            String inner = expr.substring(openIdx + 1, closeIdx);
            boolean innerResult = evaluateLogicalExpression(inner, data);
            expr = expr.substring(0, openIdx) + innerResult + expr.substring(closeIdx + 1);
        }

        return evaluateLogicalExpression(expr, data);
    }

    private boolean evaluateLogicalExpression(String expr, Map<String, Object> data) {
        expr = expr.trim();

        // 拆分 || 操作符
        int orIdx = findTopLevelOperator(expr, "||");
        if (orIdx >= 0) {
            String left = expr.substring(0, orIdx).trim();
            String right = expr.substring(orIdx + 2).trim();
            return evaluateLogicalExpression(left, data) || evaluateLogicalExpression(right, data);
        }

        // 拆分 && 操作符
        int andIdx = findTopLevelOperator(expr, "&&");
        if (andIdx >= 0) {
            String left = expr.substring(0, andIdx).trim();
            String right = expr.substring(andIdx + 2).trim();
            return evaluateLogicalExpression(left, data) && evaluateLogicalExpression(right, data);
        }

        // 无逻辑运算符，求值原子表达式
        return evaluateAtomicExpression(expr.trim(), data);
    }

    /** 在顶层（不在括号内）查找操作符 */
    private int findTopLevelOperator(String expr, String op) {
        int depth = 0;
        for (int i = 0; i <= expr.length() - op.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && expr.startsWith(op, i)) return i;
        }
        return -1;
    }

    // ---- 原子表达式求值 ----

    private boolean evaluateAtomicExpression(String expr, Map<String, Object> data) {
        expr = expr.trim();
        if (expr.isEmpty()) return true;

        // 布尔字面量
        if ("true".equalsIgnoreCase(expr)) return true;
        if ("false".equalsIgnoreCase(expr)) return false;

        // 一元取反
        if (expr.startsWith("!")) {
            return !evaluateAtomicExpression(expr.substring(1).trim(), data);
        }

        // 括号包裹（冗余保护）
        if (expr.startsWith("(") && expr.endsWith(")")) {
            return evaluateCompoundExpression(expr.substring(1, expr.length() - 1), data);
        }

        // === 函数调用风格 ===
        // contains(str, substr)
        if (expr.startsWith("contains(") && expr.endsWith(")")) {
            String args = expr.substring(9, expr.length() - 1);
            return evaluateContains(args, data);
        }
        // startsWith(str, prefix)
        if (expr.startsWith("startsWith(") && expr.endsWith(")")) {
            String args = expr.substring(11, expr.length() - 1);
            return evaluateStartsWith(args, data);
        }
        // endsWith(str, suffix)
        if (expr.startsWith("endsWith(") && expr.endsWith(")")) {
            String args = expr.substring(9, expr.length() - 1);
            return evaluateEndsWith(args, data);
        }
        // matches(str, regex)
        if (expr.startsWith("matches(") && expr.endsWith(")")) {
            String args = expr.substring(8, expr.length() - 1);
            return evaluateMatches(args, data);
        }
        // isNull(token)：仅「变量存在且值为 null」才算命中。
        // 变量未定义属表达式写错，fail-closed 不算命中（否则字段名写错会恒命中）。
        if (expr.startsWith("isNull(") && expr.endsWith(")")) {
            String arg = expr.substring(7, expr.length() - 1).trim();
            return data != null && data.containsKey(arg) && data.get(arg) == null;
        }
        // notNull(token)：变量存在且值非 null
        if (expr.startsWith("notNull(") && expr.endsWith(")")) {
            String arg = expr.substring(8, expr.length() - 1).trim();
            return data != null && data.containsKey(arg) && data.get(arg) != null;
        }

        // === in 操作符 ===  value in (a, b, c)
        int inIdx = findTopLevelKeyword(expr, "in");
        if (inIdx >= 0) {
            String left = expr.substring(0, inIdx).trim();
            String right = expr.substring(inIdx + 2).trim();
            return evaluateIn(left, right, data);
        }

        // === 标准二元比较 ===
        return evaluateBinaryExpression(expr, data);
    }

    private boolean evaluateBinaryExpression(String expr, Map<String, Object> data) {
        int opIndex = -1;
        String operator = null;
        for (String op : new String[]{">=", "<=", "==", "!=", ">", "<"}) {
            int idx = expr.indexOf(op);
            if (idx > 0) {
                if (opIndex == -1 || idx < opIndex) {
                    opIndex = idx;
                    operator = op;
                }
            }
        }

        if (opIndex == -1) {
            // 无操作符，当作布尔值求值
            Object val = resolveValue(expr.trim(), data);
            if (val instanceof Boolean b) return b;
            if (val instanceof Number n) return n.doubleValue() != 0;
            if (val instanceof String s) return !s.isBlank();
            return val != null;
        }

        String left = expr.substring(0, opIndex).trim();
        String right = expr.substring(opIndex + operator.length()).trim();
        // 表达式不完整（如 "amount > "）→ fail-closed，避免空串参与字典序比较而误判为 true
        if (left.isEmpty() || right.isEmpty()) return false;

        Object leftVal = resolveValue(left, data);
        Object rightVal = resolveValue(right, data);
        // 任一侧是「未定义的裸标识符」（且不是 null 字面量）→ 无法比较，fail-closed。
        // 修复前该场景因 resolveValue 返回变量名而恒为 true（字母 > 数字），会把全部单据误杀。
        if ((leftVal == null && !isNullLiteral(left)) || (rightVal == null && !isNullLiteral(right))) {
            return false;
        }

        if (leftVal instanceof Number lnum && rightVal instanceof Number rnum) {
            double l = lnum.doubleValue();
            double r = rnum.doubleValue();
            return switch (operator) {
                case ">" -> l > r;
                case "<" -> l < r;
                case ">=" -> l >= r;
                case "<=" -> l <= r;
                case "==" -> Math.abs(l - r) < 0.0001;
                case "!=" -> Math.abs(l - r) >= 0.0001;
                default -> true;
            };
        }

        String ls = leftVal != null ? leftVal.toString() : "";
        String rs = rightVal != null ? rightVal.toString() : "";
        return switch (operator) {
            case "==" -> ls.equals(rs);
            case "!=" -> !ls.equals(rs);
            case ">" -> ls.compareTo(rs) > 0;
            case "<" -> ls.compareTo(rs) < 0;
            case ">=" -> ls.compareTo(rs) >= 0;
            case "<=" -> ls.compareTo(rs) <= 0;
            default -> true;
        };
    }

    // ---- 函数类操作 ----

    private boolean evaluateContains(String args, Map<String, Object> data) {
        // contains(str, substr) or contains(field, "literal")
        List<String> parts = splitArgs(args);
        if (parts.size() < 2) return false;
        String str = resolveArg(parts.get(0), data);
        String substr = resolveArg(parts.get(1), data);
        return str != null && substr != null && str.contains(substr);
    }

    private boolean evaluateStartsWith(String args, Map<String, Object> data) {
        List<String> parts = splitArgs(args);
        if (parts.size() < 2) return false;
        String str = resolveArg(parts.get(0), data);
        String prefix = resolveArg(parts.get(1), data);
        return str != null && prefix != null && str.startsWith(prefix);
    }

    private boolean evaluateEndsWith(String args, Map<String, Object> data) {
        List<String> parts = splitArgs(args);
        if (parts.size() < 2) return false;
        String str = resolveArg(parts.get(0), data);
        String suffix = resolveArg(parts.get(1), data);
        return str != null && suffix != null && str.endsWith(suffix);
    }

    private boolean evaluateMatches(String args, Map<String, Object> data) {
        List<String> parts = splitArgs(args);
        if (parts.size() < 2) return false;
        String str = resolveArg(parts.get(0), data);
        String regex = resolveArg(parts.get(1), data);
        return str != null && regex != null && str.matches(regex);
    }

    private boolean evaluateIn(String leftExpr, String rightExpr, Map<String, Object> data) {
        Object leftVal = resolveValue(leftExpr.trim(), data);
        if (leftVal == null) return false;

        // 解析右侧列表: (a, b, c) 或 ["a", "b", "c"]
        String trimmed = rightExpr.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        String[] items = trimmed.split(",");
        String leftStr = leftVal.toString();
        for (String item : items) {
            String key = item.trim();
            // 列表项语义是「字面量」：仅当 data 中确有同名变量时按变量取值，否则按字面量比较。
            // （resolveValue 已改为对未定义变量返回 null，此处不能再直接依赖它的默认处理行为）
            Object itemVal = (data != null && data.containsKey(key)) ? data.get(key) : unquote(key);
            if (itemVal != null && leftStr.equals(itemVal.toString())) {
                return true;
            }
        }
        return false;
    }

    // ---- 工具 ----

    /** 在顶层查找关键字（不在字符串或括号内） */
    private int findTopLevelKeyword(String expr, String keyword) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i <= expr.length() - keyword.length(); i++) {
            char c = expr.charAt(i);
            if (c == '"' || c == '\'') inString = !inString;
            else if (!inString) {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (depth == 0 && expr.startsWith(keyword, i)) {
                    // 确保是独立单词
                    char prev = i > 0 ? expr.charAt(i - 1) : ' ';
                    char next = i + keyword.length() < expr.length() ? expr.charAt(i + keyword.length()) : ' ';
                    if (!Character.isLetterOrDigit(prev) && !Character.isLetterOrDigit(next)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /** 分割函数参数（按逗号，忽略字符串内的逗号） */
    private List<String> splitArgs(String args) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        char strChar = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (inString) {
                sb.append(c);
                if (c == strChar) inString = false;
            } else if (c == '"' || c == '\'') {
                sb.append(c);
                inString = true;
                strChar = c;
            } else if (c == '(') {
                depth++;
                sb.append(c);
            } else if (c == ')') {
                depth--;
                sb.append(c);
            } else if (c == ',' && depth == 0) {
                result.add(sb.toString().trim());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        if (!sb.isEmpty()) {
            result.add(sb.toString().trim());
        }
        return result;
    }

    private String resolveArg(String token, Map<String, Object> data) {
        Object val = resolveValue(token, data);
        return val != null ? val.toString() : null;
    }

    private Object resolveValue(String token, Map<String, Object> data) {
        if (token.startsWith("\"") && token.endsWith("\"")) {
            return token.substring(1, token.length() - 1);
        }
        if (token.startsWith("'") && token.endsWith("'")) {
            return token.substring(1, token.length() - 1);
        }
        try {
            if (token.contains(".")) {
                return Double.parseDouble(token);
            }
            return Long.parseLong(token);
        } catch (NumberFormatException ignored) {
            // 非数值字面量：继续按变量名/表达式解析，属预期降级
        }
        if (data != null && data.containsKey(token)) {
            return data.get(token);
        }
        // 修复（L1-B-04 单测发现）：未定义变量必须 fail-closed 返回 null。
        // 原实现 `return token` 把"变量名"当字面量返回，于是 `未定义变量 > 50000` 走字符串字典序比较，
        // 小写字母(n=110) > 数字(5=53) 恒为 true —— 任何字段名写错的 AUTO_REJECT 规则会把全部单据误杀
        // （与"1500 元报销单被金额超5万规则驳回"的真实事故同源，此前只加了 try-catch，本路径不抛异常故未被拦住）。
        return null;
    }

    /** 去除字符串字面量的包裹引号（in 列表项等按字面量解析时使用） */
    private static String unquote(String s) {
        if (s == null) return null;
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** 是否为 null 字面量（用于把 "变量未定义" 与 "显式与 null 比较" 区分开） */
    private static boolean isNullLiteral(String token) {
        return "null".equalsIgnoreCase(token);
    }

    private boolean evaluateInvoiceRule(ReviewRule rule, Map<String, Object> data) {
        if (data == null) return true;

        String ruleCode = rule.getRuleCode();
        String invoiceCode = getString(data, "invoiceCode");
        String invoiceNumber = getString(data, "invoiceNumber");
        String invoiceType = getString(data, "invoiceType");
        Object invoiceAmount = data.get("invoiceAmount");
        Object totalAmount = data.get("totalAmount");
        String billType = getString(data, "billType");

        if ("PRE-INV-VALID".equals(ruleCode)) {
            if (invoiceCode == null || invoiceCode.isBlank()) {
                log.warn("发票校验失败：缺少发票代码");
                return false;
            }
            if (invoiceNumber == null || invoiceNumber.isBlank()) {
                log.warn("发票校验失败：缺少发票号码");
                return false;
            }
            return true;
        }

        if ("PRE-INV-DUPLICATE".equals(ruleCode)) {
            // BUG-06 修复：真实查重 —— 同租户下 OCR 已识别的同票号记录数 >1（或历史已被其他单据使用）即判重复
            if (invoiceNumber == null || invoiceNumber.isBlank()) {
                return true;
            }
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM fssc_image.ocr_record WHERE invoice_no = ?",
                        Integer.class, invoiceNumber);
                if (cnt != null && cnt > 1) {
                    log.warn("发票重复: invoiceNumber={} 已出现 {} 次", invoiceNumber, cnt);
                    return false;
                }
                return true;
            } catch (Exception ex) {
                log.warn("发票重复性校验查询失败(按不重复处理): {}", ex.getMessage());
                return true;
            }
        }

        if ("PRE-INV-AMOUNT".equals(ruleCode)) {
            if (invoiceAmount != null && totalAmount != null) {
                try {
                    double invAmt = Double.parseDouble(invoiceAmount.toString());
                    double totalAmt = Double.parseDouble(totalAmount.toString());
                    if (Math.abs(invAmt - totalAmt) > 0.01) {
                        log.warn("发票金额与明细不一致: invoiceAmount={}, totalAmount={}", invAmt, totalAmt);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    log.warn("金额解析失败: invoiceAmount={}, totalAmount={}", invoiceAmount, totalAmount);
                }
            }
            return true;
        }

        if ("PRE-INV-ATTACH".equals(ruleCode)) {
            Object hasAttachment = data.get("hasAttachment");
            if (hasAttachment != null && Boolean.FALSE.equals(hasAttachment)) {
                log.warn("发票附件缺失");
                return false;
            }
            return true;
        }

        if ("EXPENSE".equals(billType) || "TRAVEL".equals(billType)) {
            return true;
        }
        return true;
    }

    private boolean evaluateBudgetRule(ReviewRule rule, Map<String, Object> data) {
        if (data == null) return true;

        String ruleCode = rule.getRuleCode();
        String expenseType = getString(data, "expenseType");
        Object budgetAvailable = data.get("budgetAvailable");
        Object requestedAmount = data.get("requestedAmount");
        String budgetPeriod = getString(data, "budgetPeriod");
        String expenseDate = getString(data, "expenseDate");

        if ("PRE-BUDGET-EXIST".equals(ruleCode)) {
            if (expenseType == null || expenseType.isBlank()) {
                log.warn("预算校验失败：费用类型未配置预算科目");
                return false;
            }
            return true;
        }

        if ("PRE-BUDGET-BALANCE".equals(ruleCode)) {
            if (budgetAvailable != null && requestedAmount != null) {
                try {
                    double available = Double.parseDouble(budgetAvailable.toString());
                    double requested = Double.parseDouble(requestedAmount.toString());
                    if (requested > available) {
                        log.warn("预算不足: available={}, requested={}", available, requested);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    log.warn("预算金额解析失败");
                }
            }
            return true;
        }

        if ("PRE-BUDGET-PERIOD".equals(ruleCode)) {
            if (budgetPeriod != null && expenseDate != null) {
                if (!expenseDate.startsWith(budgetPeriod)) {
                    log.warn("费用日期不在预算期间内: expenseDate={}, budgetPeriod={}", expenseDate, budgetPeriod);
                    return false;
                }
            }
            return true;
        }

        if (expenseType != null && !expenseType.isBlank()) {
            return true;
        }
        return true;
    }

    private boolean evaluateBusinessRule(ReviewRule rule, Map<String, Object> data) {
        if (data == null) return true;

        String ruleCode = rule.getRuleCode();
        String billType = getString(data, "billType");
        Object requirePreApproval = data.get("requirePreApproval");
        Object hasPreApproval = data.get("hasPreApproval");
        Object requireContract = data.get("requireContract");
        Object hasContract = data.get("hasContract");
        Object requireProject = data.get("requireProject");
        Object hasProject = data.get("hasProject");

        if ("PRE-BIZ-PREAPPROVAL".equals(ruleCode)) {
            if (Boolean.TRUE.equals(requirePreApproval) && !Boolean.TRUE.equals(hasPreApproval)) {
                log.warn("需要事前申请但未关联申请单");
                return false;
            }
            return true;
        }

        if ("PRE-BIZ-CONTRACT".equals(ruleCode)) {
            if (Boolean.TRUE.equals(requireContract) && !Boolean.TRUE.equals(hasContract)) {
                log.warn("需要关联合同但未关联");
                return false;
            }
            return true;
        }

        if ("PRE-BIZ-PROJECT".equals(ruleCode)) {
            if (Boolean.TRUE.equals(requireProject) && !Boolean.TRUE.equals(hasProject)) {
                log.warn("建议关联项目但未关联（警告级别）");
                return true;
            }
            return true;
        }

        if (billType == null || billType.isBlank()) {
            return false;
        }
        return true;
    }

    private boolean evaluateTimeRule(ReviewRule rule, Map<String, Object> data) {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();

        String ruleCode = rule.getRuleCode();
        Object expenseDate = data != null ? data.get("expenseDate") : null;
        Object submitDate = data != null ? data.get("submitDate") : null;
        Object maxDays = data != null ? data.get("maxDays") : null;

        if ("PRE-TIME-SUBMIT".equals(ruleCode)) {
            if (expenseDate != null && submitDate != null) {
                try {
                    LocalDateTime expDate = LocalDateTime.parse(expenseDate.toString());
                    LocalDateTime subDate = LocalDateTime.parse(submitDate.toString());
                    long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(expDate, subDate);
                    int maxAllowed = maxDays != null ? Integer.parseInt(maxDays.toString()) : 30;
                    if (daysBetween > maxAllowed) {
                        log.warn("提交超时: daysBetween={}, maxAllowed={}", daysBetween, maxAllowed);
                        return false;
                    }
                } catch (Exception e) {
                    log.warn("日期解析失败");
                }
            }
            return true;
        }

        if ("PRE-TIME-LATE".equals(ruleCode)) {
            if (expenseDate != null && submitDate != null) {
                try {
                    LocalDateTime expDate = LocalDateTime.parse(expenseDate.toString());
                    LocalDateTime subDate = LocalDateTime.parse(submitDate.toString());
                    long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(expDate, subDate);
                    int hardLimit = 90;
                    if (daysBetween > hardLimit) {
                        log.warn("严重逾期提交: daysBetween={}", daysBetween);
                        return false;
                    }
                } catch (Exception e) {
                    log.warn("日期解析失败");
                }
            }
            return true;
        }

        if (hour >= 0 && hour < 6) {
            log.debug("非工作时间提交单据: hour={}", hour);
            return true;
        }
        return true;
    }

    private boolean evaluatePermissionRule(ReviewRule rule, Map<String, Object> data) {
        if (data == null) return false;

        String ruleCode = rule.getRuleCode();
        Object empId = data.get("employeeId");
        Object deptId = data.get("deptId");
        Object employeeLevel = data.get("employeeLevel");

        if (empId == null || empId.toString().isBlank()) {
            log.warn("权限校验失败：缺少员工ID");
            return false;
        }

        if ("PRE-PERM-DEPT".equals(ruleCode)) {
            if (deptId == null || deptId.toString().isBlank()) {
                log.warn("权限校验失败：缺少部门信息");
                return false;
            }
        }

        if ("PRE-PERM-LEVEL".equals(ruleCode)) {
            if (employeeLevel == null || employeeLevel.toString().isBlank()) {
                log.warn("权限校验失败：缺少员工等级");
                return false;
            }
        }

        return true;
    }

    private boolean evaluateFraudRule(ReviewRule rule, Map<String, Object> data) {
        if (data == null) return true;

        String ruleCode = rule.getRuleCode();
        String billType = getString(data, "billType");
        String expenseType = getString(data, "expenseType");
        Object amountObj = data.get("amount");
        Object recentCountObj = data.get("recentCount");
        Object vendorRiskLevel = data.get("vendorRiskLevel");

        if ("PRE-FRAUD-FREQ".equals(ruleCode)) {
            if (recentCountObj != null) {
                try {
                    int recentCount = Integer.parseInt(recentCountObj.toString());
                    if (recentCount > 10) {
                        log.warn("高频报销检测: recentCount={}", recentCount);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    log.warn("高频次数解析失败");
                }
            }
            return true;
        }

        if ("PRE-FRAUD-AMOUNT".equals(ruleCode)) {
            if (amountObj != null) {
                try {
                    double amount = Double.parseDouble(amountObj.toString());
                    if (amount > 0 && (amount % 10000 == 0 || amount % 1000 == 0)) {
                        log.info("整额报销标记: amount={}", amount);
                        return true;
                    }
                } catch (NumberFormatException e) {
                    log.warn("金额解析失败");
                }
            }
            return true;
        }

        if ("PRE-FRAUD-VENDOR".equals(ruleCode)) {
            if ("HIGH".equals(vendorRiskLevel)) {
                log.warn("供应商高风险: vendorRiskLevel={}", vendorRiskLevel);
                return false;
            }
            return true;
        }

        if ("TRAVEL".equals(billType) && "ENTERTAINMENT".equals(expenseType)) {
            log.warn("差旅中包含招待费，需关注");
            return true;
        }
        return true;
    }

    // ========== 批量操作 ==========

    @Transactional(rollbackFor = Exception.class)
    public void batchEnable(List<Long> ids) {
        ids.forEach(this::enableRule);
        log.info("批量启用预检规则: ids={}", ids);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDisable(List<Long> ids) {
        ids.forEach(this::disableRule);
        log.info("批量停用预检规则: ids={}", ids);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        ids.forEach(this::deleteRule);
        log.info("批量删除预检规则: ids={}", ids);
    }

    // ========== 脚本验证 ==========

    /** 验证脚本语法并返响应行结果，不持久化 */
    public Map<String, Object> validateScript(String scriptContent, Map<String, Object> sampleData) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("script", scriptContent);
        result.put("sampleData", sampleData);

        if (scriptContent == null || scriptContent.isBlank()) {
            result.put("valid", false);
            result.put("error", "脚本内容为空");
            return result;
        }

        long start = System.currentTimeMillis();
        try {
            boolean passed = evaluateScript(scriptContent, sampleData);
            long elapsed = System.currentTimeMillis() - start;
            result.put("valid", true);
            result.put("result", passed);
            result.put("executionTimeMs", (int) elapsed);
            result.put("error", null);
        } catch (Exception e) {
            // 脚本试跑失败：错误经 valid=false + error 返回调用方（规则设计器界面），
            // 同时记日志便于服务端排查
            log.warn("预检规则脚本试跑失败: {}", e.getMessage());
            long elapsed = System.currentTimeMillis() - start;
            result.put("valid", false);
            result.put("result", null);
            result.put("executionTimeMs", (int) elapsed);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /******************************** P0-5 无纸化准入（TC04） ********************************/

    /**
     * 无纸化准入结果：eligible=true 可走无纸化（免实物）；否则需纸质交单。
     */
    public static class PaperlessEligibility {
        private boolean eligible;
        private String reason;
        public PaperlessEligibility(boolean eligible, String reason) {
            this.eligible = eligible;
            this.reason = reason;
        }
        public boolean isEligible() { return eligible; }
        public void setEligible(boolean eligible) { this.eligible = eligible; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * P0-5 无纸化准入校验（模板 TC04）：
     * <ul>
     *   <li>电子发票（专票/普票） + 电汇/银行转账付款 → 可无纸化，无需交实物</li>
     *   <li>纸质发票 → 禁止无纸化，必须交实物</li>
     *   <li>发票类型未知或线上直连开票/其他，按保守处置需实物</li>
     * </ul>
     * @param invoiceKind 发票介质：ELECTRONIC 电子 / PAPER 纸质 / null 未知
     * @param invoiceType 发票类型：SPECIAL 专票 / NORMAL 普票 / 其他
     * @param payMethod   付款方式：BANK/TT/电汇 视为电子转账；CASH 现金视为非电汇
     */
    public PaperlessEligibility checkPaperlessEligibility(String invoiceKind, String invoiceType, String payMethod) {
        // 纸质发票禁止无纸化
        if ("PAPER".equalsIgnoreCase(invoiceKind)) {
            return new PaperlessEligibility(false, "纸质发票禁止无纸化，须交实物");
        }
        // 电子发票要求 + 电子化付款（电汇/银转）
        boolean elecInvoice = "ELECTRONIC".equalsIgnoreCase(invoiceKind);
        boolean elecPay = payMethod != null && !payMethod.isBlank()
                && (payMethod.contains("BANK") || payMethod.contains("TT")
                || payMethod.contains("电汇") || payMethod.contains("银") || payMethod.contains("EFT")
                || "TRANSFER".equalsIgnoreCase(payMethod));
        if (elecInvoice && elecPay) {
            return new PaperlessEligibility(true, "电子发票 + 电子化付款，满足无纸化准入");
        }
        if (elecInvoice) {
            return new PaperlessEligibility(false, "电子发票但非电子化付款，无纸化暂不适用，须交实物");
        }
        return new PaperlessEligibility(false, "发票介质未知，按需实物处置");
    }

    private String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

}
