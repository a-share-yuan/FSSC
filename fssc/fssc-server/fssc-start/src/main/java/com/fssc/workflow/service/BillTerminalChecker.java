package com.fssc.workflow.service;

import com.fssc.purchase.entity.PoOrder;
import com.fssc.purchase.mapper.PoOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    /** 采购订单审批流程 bizType（{@code wf_task}/{@code wf_instance}.biz_type 的实态取值） */
    public static final String BIZ_PURCHASE_ORDER_REVIEW = "PURCHASE_ORDER_REVIEW";

    /**
     * po_order 终态集（与 {@code PurchaseService} 终态机守卫同口径）：
     * VOID 已作废 / CLOSED 已关闭 / ARCHIVED 已归档——三类单据流程均已收尾，不应再挂待办。
     */
    public static final Set<String> PO_TERMINAL_STATUSES = Set.of("VOID", "CLOSED", "ARCHIVED");

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
        if (!BIZ_PURCHASE_ORDER_REVIEW.equals(bizType.trim())) {
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
}
