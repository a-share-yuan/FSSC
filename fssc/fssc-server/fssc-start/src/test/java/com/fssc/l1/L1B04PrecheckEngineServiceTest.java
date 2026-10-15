package com.fssc.l1;

import com.fssc.compliance.entity.PrecheckResult;
import com.fssc.compliance.entity.ReviewRule;
import com.fssc.compliance.service.PrecheckEngineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L1-B-04 PrecheckEngineService 单元测试：审单规则表达式引擎。
 *
 * <p>重点回归 <b>fail-closed</b>：表达式引用不存在的变量时必须判为「未命中(false)」，
 * 不能 fail-open 返回 true —— 否则配合 AUTO_REJECT 规则会把所有单据误杀。
 */
@DisplayName("L1-B-04 PrecheckEngineService 规则引擎")
class L1B04PrecheckEngineServiceTest {

    private final PrecheckEngineService svc = new PrecheckEngineService(null, null, null);

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("字面量脚本: return true / return false")
    void 字面量脚本() {
        assertTrue(svc.evaluateScript("return true;", data()));
        assertFalse(svc.evaluateScript("return false;", data()));
    }

    @Test
    @DisplayName("二元比较: 金额阈值上下两侧")
    void 二元比较() {
        assertTrue(svc.evaluateScript("amount > 50000", data("amount", 60000)));
        assertFalse(svc.evaluateScript("amount > 50000", data("amount", 3000)));
        assertTrue(svc.evaluateScript("amount >= 50000", data("amount", 50000)));
        assertFalse(svc.evaluateScript("amount >= 50000", data("amount", 49999)));
        assertTrue(svc.evaluateScript("amount <= 500", data("amount", 500)));
        assertTrue(svc.evaluateScript("status == 'DRAFT'", data("status", "DRAFT")));
        assertTrue(svc.evaluateScript("status != 'DRAFT'", data("status", "APPROVED")));
    }

    @Test
    @DisplayName("复合表达式: && / || / 括号优先级")
    void 复合表达式() {
        assertTrue(svc.evaluateScript("amount > 100 && amount < 1000", data("amount", 500)));
        assertFalse(svc.evaluateScript("amount > 100 && amount < 1000", data("amount", 5000)));
        assertTrue(svc.evaluateScript("amount > 50000 || deptId == 10003", data("amount", 10, "deptId", 10003)));
        assertFalse(svc.evaluateScript("amount > 50000 || deptId == 10003", data("amount", 10, "deptId", 1)));
        assertTrue(svc.evaluateScript("(amount > 100 || deptId == 1) && status == 'DRAFT'",
                data("amount", 10, "deptId", 1, "status", "DRAFT")));
    }

    @Test
    @DisplayName("【缺陷-待修】函数式表达式(contains/startsWith/isNull/in) 在 evaluateScript 路径下失效")
    void 函数式表达式失效() {
        // 根因：evaluateCompoundExpression 先做「括号优先」预处理，
        // 把 contains(title, '差旅') 的括号当成分组拆掉，表达式被改写成 "containstrue" 这类字符串，
        // 于是 evaluateAtomicExpression 中的函数分支（startsWith("contains(")）成为永不可达的死代码。
        // 影响：所有使用 contains/startsWith/endsWith/matches/isNull/notNull/in 写法的审单规则实际不生效。
        // 注意：命中场景会因表达式被改写成 "containstrue" 而"侥幸"返回 true，
        // 必须用【未命中场景】验证，才能真正暴露该缺陷。
        assertFalse(svc.evaluateScript("contains(title, '招待')", data("title", "差旅报销单")),
                "contains 未命中场景应返回 false");
        assertFalse(svc.evaluateScript("billType in (CLAIM, LOAN)", data("billType", "REPAY")),
                "in 未命中场景应返回 false");
    }

    @Test
    @DisplayName("【缺陷-待修】未定义变量 fail-open：resolveValue 返回变量名字符串而非 null")
    void failClosed不误杀() {
        // 根因：PrecheckEngineService.resolveValue 对「引号字面量/数字/Map 命中」之外的情况
        // 直接 return token（变量名字符串）。于是 "notExistVar > 50000" 走字符串字典序比较，
        // 小写字母(n=110) > 数字(5=53) → 恒为 true。
        // 后果：任何字段名写错的 AUTO_REJECT 规则会把全部单据误杀（与"金额超5万规则杀掉1500元单据"事故同源）。
        assertFalse(svc.evaluateScript("notExistVar > 50000", data("amount", 1500)),
                "变量缺失时必须 fail-closed 返回 false");
        assertFalse(svc.evaluateScript("amount > ", data("amount", 1500)),
                "语法不完整的表达式必须 fail-closed 返回 false");
    }

    @Test
    @DisplayName("evaluateRule: checkResult 语义 = 脚本求值结果（true→PASS 命中规则，false→FAIL 未命中）")
    void evaluateRule结果() {
        ReviewRule rule = new ReviewRule();
        rule.setId(1L);
        rule.setRuleCode("RR_AMOUNT_HIGH");
        rule.setRuleCategory("BUSINESS");
        rule.setScriptContent("amount > 50000");
        rule.setOutputMessage("金额超过5万，需人工复核");
        rule.setRuleLevel("HIGH");

        // 注意语义：checkResult 只承载"脚本表达式是否为真"，最终处置由 rule.action(AUTO_REJECT 等) 决定。
        PrecheckResult hit = svc.evaluateRule(rule, data("billId", 101L, "billType", "CLAIM", "amount", 60000));
        assertEquals("PASS", hit.getCheckResult(), "表达式为真 → PASS(规则命中)");
        assertEquals(101L, hit.getBillId());
        assertEquals("CLAIM", hit.getBillType());

        PrecheckResult miss = svc.evaluateRule(rule, data("billId", 102L, "billType", "CLAIM", "amount", 1500));
        assertEquals("FAIL", miss.getCheckResult(), "表达式为假 → FAIL(规则未命中)");
    }

    @Test
    @DisplayName("evaluateRule: 无脚本的规则按类目默认通过")
    void evaluateRule默认通过() {
        ReviewRule rule = new ReviewRule();
        rule.setId(2L);
        rule.setRuleCode("RR_DEFAULT");
        rule.setRuleCategory("BUSINESS");
        rule.setScriptContent(null);
        // BUSINESS 类目无脚本时会走 evaluateBusinessRule；此处仅断言不抛异常且结果非 null
        PrecheckResult r = svc.evaluateRule(rule, data("billId", 1L));
        assertTrue(r != null && r.getCheckResult() != null);
    }
}
