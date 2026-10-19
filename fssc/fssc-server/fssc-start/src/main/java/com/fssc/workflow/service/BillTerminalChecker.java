package com.fssc.workflow.service;

import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.mapper.PoOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务单据「终态」判定——<b>唯一真值表</b>（P4：一个概念只在一处定义）。
 *
 * <p>背景（真跑业务-V4 实测）：作废等收尾动作只改了业务单据状态，若流程终止入口漏走、
 * 或存在历史脏数据，{@code wf_instance} 会滞留 RUNNING、{@code wf_task} 镜像行滞留 PENDING；
 * 而待办取数（{@code WfQueryService.myTodo} / {@code WfEngineFacade.todo}）只按任务状态取数、
 * 不回查业务单据，于是<b>作废单会同时挂在角标 / 审批工作台 / 工作台「我的待办」三处</b>，
 * 点「去处理」还只看到空状态。
 *
 * <p>本类把「单据是否已收尾」收口成一处口径，供三处待办取数兜底剔除，也供超时催办闸复用
 * （{@code WfTimeoutJob}）——避免同一判定在多处各写一遍而静默漂移。
 *
 * <p>现只按采购订单面落表（{@link #BIZ_PURCHASE_ORDER_REVIEW} → {@code po_order}）；
 * 其余 bizType 返回 false＝维持改造前行为（放行）。扩面时在此逐 bizType 加一条映射，不改调用方。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillTerminalChecker {

    private final PoOrderMapper poOrderMapper;

    /**通用单据终态探测：走 JdbcTemplate，避免为每个域都注入一个 Mapper。*/
    private final JdbcTemplate jdbcTemplate;

    /** 采购订单审批流程 bizType（{@code wf_task}/{@code wf_instance}.biz_type 的实态取值） */
    public static final String BIZ_PURCHASE_ORDER_REVIEW = "PURCHASE_ORDER_REVIEW";

    /**
     * po_order 终态集（与 {@code PurchaseService} 终态机守卫同口径）：
     * VOID 已作废 / CLOSED 已关闭 / ARCHIVED 已归档——三类单据流程均已收尾，不应再挂待办。
     */
    public static final Set<String> PO_TERMINAL_STATUSES = Set.of("VOID", "CLOSED", "ARCHIVED");

    /**
     * D-WF-14（2026-10-19 人工测试 G 册实测）：除采购订单面外，补齐实测已确认会脱节的 bizType 映射。
     *
     * <p>背景：本次实测发现 6 条流程与单据状态脱节的实例（3 条「单据已 PAID、INVOICE_MATCH 派单仍 RUNNING」
     * ＋ 3 条「单据仍 DRAFT、流程却已发起」）。根因是<b>单据从别的路径走到终态时没有任何一方回调流程</b>：
     * 作废/回退类收尾都调了 {@code cancelRunningInstancesByBiz}，但「付款批次直接走通」这条路没人管。
     *
     * <p>本表只收<b>已实测脱节</b>的bizType，未列的仍返回 false（fail-open，维持改造前行为），
     * 避免把「猜错的表名/状态列」变成线上误杀。
     *
     * <p> <b>声明位置铁律</b>：本字段必须声明在<b>所有 {@code *_TERMINAL} 常量之后</b>。
     * 静态初始化按声明顺序执行，若本行在终态集之前，{@link #buildProbes()} 读到的是
     * 尚未初始化的 null（实测踩过：所有探测规则 terminalStatuses 全null，判定直接 NPE）。
     */
    public static final Map<String, BillProbe> BILL_PROBES;

    /**
     * 单据终态探测规则：{@code table}（物理表）、{@code statusCol}（状态列）、
     * {@code terminalStatuses}（该表的终态集，<b>按表各自定义，不共用一套</b>）。
     */
    public record BillProbe(String table, String statusCol, Set<String> terminalStatuses) {
        /** 白名单表名/列名：全部来自代码内常量，不接受外部拼接，杜绝 SQL 注入面。 */
        static BillProbe of(String table, String statusCol, Set<String> terminalStatuses) {
            return new BillProbe(table, statusCol, terminalStatuses);
        }
    }

    /** 费用单据终态（exp_bill）：已付/已作废/已驳回/已撤回/已关闭等，业务上不应再挂待办。 */
    public static final Set<String> EXP_BILL_TERMINAL = Set.of(
            "PAID", "VOID", "REJECTED", "WITHDRAWN", "CANCELLED", "CLOSED");

    /** 合同终态（cont_contract）。 */
    public static final Set<String> CONTRACT_TERMINAL = Set.of("APPROVED", "VOID", "REJECTED", "CLOSED");

    /** 应收受理单终态（ar_claim）。 */
    public static final Set<String> AR_CLAIM_TERMINAL = Set.of("COMPLETED", "VOID", "REJECTED", "CANCELLED");

    /** 资金调拨终态（tr_transfer）。 */
    public static final Set<String> TR_TRANSFER_TERMINAL = Set.of("DONE", "PAID", "VOID", "REJECTED", "CANCELLED");

    /** 银承票据终态（tr_credit_bill）。 */
    public static final Set<String> TR_CREDIT_BILL_TERMINAL = Set.of("DONE", "CONFIRMED", "VOID", "REJECTED", "CANCELLED");

    private static Map<String, BillProbe> buildProbes() {
        Map<String, BillProbe> m = new LinkedHashMap<>();
        // exp_bill 面：EXP_PAY / INVOICE_MATCH / EXP_LOAN 等都落在同一张表
        for (String bt : Set.of("EXP_PAY", "INVOICE_MATCH", "EXP_LOAN", "EXP_CLAIM", "EXP_REIMBURSE")) {
            m.put(bt, BillProbe.of("exp_bill", "status", EXP_BILL_TERMINAL));
        }
        m.put("CONTRACT_APPROVE", BillProbe.of("cont_contract", "status", CONTRACT_TERMINAL));
        m.put("AR_CLAIM", BillProbe.of("ar_claim", "status", AR_CLAIM_TERMINAL));
        m.put("TR_TRANSFER", BillProbe.of("tr_transfer", "status", TR_TRANSFER_TERMINAL));
        m.put("TR_RECEIPT", BillProbe.of("tr_credit_bill", "status", TR_CREDIT_BILL_TERMINAL));
        return Map.copyOf(m);
    }

    /**
     * 静态块赋值（放在所有 {@code *_TERMINAL} 常量声明之后）：
     * 保证 {@link #buildProbes()} 执行时终态集已初始化完成。
     */
    static {
        BILL_PROBES = buildProbes();
    }

    /**
     * 终态采购订单 id 子查询（供 {@code wf_task} 分页列表按 {@code biz_id} 就地排除，
     */
    public static final String PO_TERMINAL_IDS_SQL =
            "SELECT id FROM po_order WHERE del_flag = 1 OR status IN ("
                    + PO_TERMINAL_STATUSES.stream().map(s -> "'" + s + "'").collect(Collectors.joining(","))
                    + ")";

    /**
     * 业务单据是否已收尾（终态）。读取失败按非终态放行（fail-open，不阻断待办取数）；
     * bizType 落在采购订单面之外、或 bizId 缺失时一律返回 false（维持改造前行为）。
     */
    public boolean isBillTerminal(String bizType, Long bizId) {
        if (bizId == null || bizType == null) {
            return false;
        }
        String bt = bizType.trim();
        // 通用探测面（D-WF-14 扩面）
        BillProbe probe = BILL_PROBES.get(bt.toUpperCase(Locale.ROOT));
        if (probe != null) {
            String st = readStatus(probe, bizId);
            return st != null && probe.terminalStatuses().contains(st.toUpperCase(Locale.ROOT));
        }
        if (!BIZ_PURCHASE_ORDER_REVIEW.equals(bt)) {
            return false;
        }
        try {
            PoOrder o = poOrderMapper.selectById(bizId);
            if (o == null) {
                return false;
            }
            if (o.getDelFlag() != null && o.getDelFlag() == 1) {
                return true;
            }
            return PO_TERMINAL_STATUSES.contains(o.getStatus());
        } catch (Exception e) {
            log.warn("单据终态读取失败，按非终态放行 bizType={}, bizId={}: {}", bizType, bizId, e.getMessage());
            return false;
        }
    }

    /**
     * D-WF-14 B 类：单据是否<b>还没进入流程态</b>（草稿/待提交）。
     *
     * <p>实测发现 3 条「单据 status=DRAFT 却已有 RUNNING 流程实例」的脱节。
     * 业务含义：这张单用户还没提交，挂在待办里点了也是空的，<b>应当撤销而不是等它提交</b>。
     *
     * <p>与 {@link #isBillTerminal} 严格区分：
     * 终态＝「已经办完了，别再催」；未提交＝「还没开始办，更不该催」。
     * 两者都要让流程收口，但原因不同、留痕措辞也不同。
     *
     * @return true＝该单仍未提交（status ∈ 草稿态集）；读不到/未登记映射时返回 false（fail-open，不误杀）
     */
    public boolean isBillNotSubmitted(String bizType, Long bizId) {
        if (bizId == null || bizType == null) {
            return false;
        }
        BillProbe probe = BILL_PROBES.get(bizType.trim().toUpperCase(Locale.ROOT));
        if (probe == null) {
            return false;
        }
        String st = readStatus(probe, bizId);
        return st != null && DRAFT_STATUSES.contains(st.toUpperCase(Locale.ROOT));
    }

    /** 草稿/未提交态（各表共用的「尚未进入流程」词表）。 */
    public static final Set<String> DRAFT_STATUSES = Set.of("DRAFT", "PENDING_SUBMIT", "UNSUBMITTED");

    /**
     * 按 {@link BillProbe} 读单据状态。失败一律返回 null（上层按 fail-open放行），
     * <b>绝不因读不到就判定为终态</b>——误杀正在办的单比留一个僵尸实例严重得多。
     */
    private String readStatus(BillProbe probe, Long bizId) {
        try {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT " + probe.statusCol() + " FROM " + probe.table() + " WHERE id = ? LIMIT 1",
                    String.class, bizId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("单据状态读取失败，按非终态放行 table={}, bizId={}: {}",
                    probe.table(), bizId, e.getMessage());
            return null;
        }
    }
}
