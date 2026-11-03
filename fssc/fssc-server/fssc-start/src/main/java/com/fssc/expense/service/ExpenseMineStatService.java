// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import java.util.Objects;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 移动端（H5）「我的」页只读聚合统计。
 *
 * <p><b>它解决什么：</b>原先该页的数字由前端「分页拉数据 + 本地累加」得出
 * （{@code H5Workbench.vue#loadMine} → {@code billApi.myPage(pageSize=200)} 再 {@code reduce} 求和），
 * 加总面最多只能覆盖 pageSize 条（方案册 U 记的原始值是 50 条），单量一多余额就偏小且无从察觉。
 * 本类把每个指标改成一条 SQL 聚合（{@code SUM(CASE WHEN ...)}，与
 * {@code GlService#trialBalance}/{@code DashboardService#expenseByStatus} 同族写法），
 * 与页面上分页能取到多少条彻底解耦——**不受任何分页条数截断影响**。</p>
 *
 * <p><b>人范围与 {@link ExpenseBillService#myPage} 完全一致</b>（源码 :1777-1807）：
 * {@code apply_user_id = 登录人 ∪ agent_user_id = 登录人}（甲：报销人 ∪ 该单登记的代理人）。
 * 只有这一个环节一致，「我的」页与「单据」tab 才对得上——代理人代填的单在两处都算他的。
 * 其余两道条件也不由本类手写：
 * <ul>
 *   <li>{@code del_flag = 0} —— 实体基类 {@code @TableLogic}（TenantAuditBase#delFlag）由 MP 自动追加，
 *       与 myPage 的 {@code selectPage} 同一道校验；</li>
 *   <li>{@code tenant_id} —— {@code TenantLineInnerInterceptor} 自动注入（MybatisPlusConfig，租户拦截器先于分页注册）。</li>
 * </ul>
 *
 * <p><b>不做数据范围放宽：</b>myPage 本身不走 {@code DataScopeKit}（它是"看自己的单"的入口，
 * 不是管理列表），所以本类同样不接公司/部门维度过滤、不接受任何外部入参。
 * 登录人只从 {@link UserContext} 取——**接口没有 userId 参数，前端无法越权统计别人的数据**。
 * 只读：本类不含任何 INSERT/UPDATE/DELETE，也不落 {@code @OperLog}。</p>
 */
@Service
@RequiredArgsConstructor
public class ExpenseMineStatService {

    private final ExpBillMapper expBillMapper;

    /**
     * 借款"未冲销完毕"的状态面。
     * 名单与仓内既有实现同源：{@code ExpenseWfEventListener} 的
     * {@code .in(status, APPROVED, PARTIAL_REPAID, REPAID)} 与 PC 侧
     * {@code LoanApply.vue#LOAN_OPEN_STATUSES} 逐字相同；
     * 提单期预分配环节（{@code ExpenseBillService}）也含 PARTIAL_REPAID。
     * 结清最终状态由 {@code ExpenseWfEventListener#settleLoanAfterRepay} 落定（REPAID／PARTIAL_REPAID）。
     * <p>为什么不能只筛 {@code status='APPROVED'}（＝H5 老算法的查询条件）：
     * 借款单一旦挂上冲抵/还款就从 APPROVED 迁档到 PARTIAL_REPAID，只认 APPROVED 会把"仍在贷、真有钱可冲"
     * 的单整片读成看不见——还款环节当年踩的就是这个洞。
     * 库里现有借款单没有一张停在 APPROVED（多是 REPAID／WITHDRAWN／PARTIAL_REPAID）
     * ⇒ 老算法的「借款余额」对任何人都恒显示 ￥0.00，
     * 而在贷余额其实落在 PARTIAL_REPAID 那一张上。
     * <p>真余额判据仍是 {@code loan_balance > 0}（对齐 myPage 的 {@code minBalance} 参数）；
     * REPAID 纳入名单只为与 PC 侧同一份枚举，余额归零后它自己就进不了求和。
     */
    private static final List<String> LOAN_OPEN_STATUSES = List.of("APPROVED", "PARTIAL_REPAID", "REPAID");

    /**
     * "在途"＝已提交、流程在办、尚未出结论的两个状态。
     * 判据沿用仓内既有同族写法：{@code ExpenseBillService#approveDeduct}（:1179 以 APPROVING ‖ SUBMITTED
     * 认定"单据在办、可被核减"）与 {@code BizProgressBoard.vue} 的"在途＝APPROVING"（本表 SUBMITTED 当前 0 行，
     * 但 SUBMITTED 是会落状态的历史枚举码，漏算就把在途读小，故一并纳入）。
     */
    private static final List<String> IN_TRANSIT_STATUSES = List.of("SUBMITTED", "APPROVING");

    /** 「我的」页的四个数（字段与页面展示位一一对应，见 {@link MineStat}）。 */
    public MineStat stat() {
        LoginUser u = UserContext.getRequired();

        // 人范围拆两臂各查一次再相加（"报销人是我" ／ "我是该单代理人但不是我自己的单"）。
        // 写成一条 (apply_user_id=? OR agent_user_id=?) 时优化器不做 index merge，
        // 退化成扫完整租户 34 万行（实测 583ms）；拆开后两臂各走
        // idx_tenant_applyuser / idx_tenant_agentuser，合计约 1ms。
        // 两臂不重叠（代理臂排除本人提单）⇒ 相加与并集口径逐值相同。
        Map<String, Object> byApply = sumRow(u.getUserId(), false);
        Map<String, Object> byAgent = sumRow(u.getUserId(), true);

        MineStat s = new MineStat();
        s.setLoanBalance(toDecimal(byApply.get("loan_bal")).add(toDecimal(byAgent.get("loan_bal"))));
        s.setLoanCount(toLong(byApply.get("loan_cnt")) + toLong(byAgent.get("loan_cnt")));
        s.setInTransitCount(toLong(byApply.get("transit_cnt")) + toLong(byAgent.get("transit_cnt")));
        s.setRejectedCount(toLong(byApply.get("rejected_cnt")) + toLong(byAgent.get("rejected_cnt")));
        return s;
    }

    /** 一条 SQL 出四个数：SUM(CASE WHEN ...) 条件聚合，分桶在库里做，Java 侧只搬运结果。 */
    private Map<String, Object> sumRow(Long userId, boolean agentArm) {
        QueryWrapper<ExpBill> qw = mineScope(userId, agentArm).select(
                "SUM(CASE WHEN bill_type='LOAN' AND status IN (" + placeholders(LOAN_OPEN_STATUSES)
                        + ") AND loan_balance > 0 THEN 1 ELSE 0 END) AS loan_cnt",
                "SUM(CASE WHEN bill_type='LOAN' AND status IN (" + placeholders(LOAN_OPEN_STATUSES)
                        + ") AND loan_balance > 0 THEN loan_balance ELSE 0 END) AS loan_bal",
                "SUM(CASE WHEN status IN (" + placeholders(IN_TRANSIT_STATUSES)
                        + ") THEN 1 ELSE 0 END) AS transit_cnt",
                "SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_cnt");

        List<Map<String, Object>> rows = expBillMapper.selectMaps(qw);
        // 这里必须挡"元素本身为 null"，只挡 rows.isEmpty() 是不够的（10-04 01:10 运行期实证）：
        // 名下零单时 SQL 仍回一行、但四个 SUM 全为 NULL，MP 的 selectMaps 对这种全 NULL 行不做映射，
        // 直接往 List 里塞一个 null 元素（启动日志 `Row: null, null, null, null / Total: 1`）
        // ⇒ rows 非空、rows.get(0) 为 null ⇒ 原写法在 row.get(...) 处 NPE、接口 500。
        // 兜法＝取首个非 null 行，取不到就用空 Map 走下面的 toDecimal/toLong，二者都已把 null 兜成 0。
        return rows.stream().filter(Objects::nonNull).findFirst().orElse(Map.of());
    }

    /**
     * 人范围谓词：与 {@code ExpenseBillService#myPage} 的 and(apply_user_id = 登录人 or agent_user_id = 登录人) 同一个环节。
     * 两臂是同一个集合的拆分：代理臂排除"本人既是报销人又是代理人"的单，否则同一张单会被计两遍。
     */
    private QueryWrapper<ExpBill> mineScope(Long userId, boolean agentArm) {
        QueryWrapper<ExpBill> qw = new QueryWrapper<>();
        if (agentArm) {
            qw.eq("agent_user_id", userId)
                    .and(w -> w.isNull("apply_user_id").or().ne("apply_user_id", userId));
        } else {
            qw.eq("apply_user_id", userId);
        }
        return qw;
    }

    /** {@code SUM(CASE WHEN ...)} 是原生片段，状态名单只能内联成字面量；值全部来自类内常量，不含任何外部入参。 */
    private static String placeholders(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('\'').append(values.get(i)).append('\'');
        }
        return sb.toString();
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v instanceof BigDecimal ? (BigDecimal) v : new BigDecimal(v.toString());
    }

    private static long toLong(Object v) {
        // SUM() 在零行结果集上返回 NULL，库里已用 ELSE 0 兜底，这里再兜一次，页面显示 0 而不是崩溃。
        return v == null ? 0L : ((Number) v).longValue();
    }

    /**
     * 出参：字段名与 H5「我的」页现有展示位一一对应（{@code H5Workbench.vue} 的 loanBalance / loanCount /
     * 打回列表），外加方案册 点名但页面上尚未落卡的「在途」。
     * <ul>
     *   <li>{@code loanBalance} —— 「借款余额」大数字：未冲销完毕借款的 Σ loan_balance（库里聚合，非前 N 条）</li>
     *   <li>{@code loanCount} —— 同一规则下的借款笔数（页面括号里的「N 笔」）</li>
     *   <li>{@code inTransitCount} —— 在途单数（我的单据里流程在办的张数）</li>
     *   <li>{@code rejectedCount} —— 被打回单数（页面「打回的单据」区块应显示的总数；
     *       老算法受 pageSize 截断且无「更多」入口，用户无法察觉列表其实没拉全）</li>
     * </ul>
     * 页面原先的 {@code loanPartial}（"按最近 N 笔合计"的截断免责声明）在本规则下恒为 false：
     * 结果来自 SUM 聚合，与一次能取回多少条明细无关，前端接上后可直接把该标记摘掉。
     */
    @Data
    public static class MineStat {
        private BigDecimal loanBalance = BigDecimal.ZERO;
        private long loanCount;
        private long inTransitCount;
        private long rejectedCount;
    }
}
