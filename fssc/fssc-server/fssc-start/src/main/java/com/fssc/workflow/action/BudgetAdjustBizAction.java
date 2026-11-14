package com.fssc.workflow.action;

import com.fssc.budget.entity.BgAdjust;
import com.fssc.budget.mapper.BgAdjustMapper;
import com.fssc.budget.service.BudgetService;
import com.fssc.workflow.event.EventOrchestrationBus;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import java.util.Collections;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预算调整单审批收尾。
 *
 * <p>配置侧 {@code wf_process_config} 早有 {@code BG_ADJUST → BG_SCHEME} 路由行，但代码从不发起，
 * 调整单只有一颗「审批」按钮做 DRAFT→APPROVED 一次翻面。本 Action 补齐流程侧收尾：
 * <ul>
 *   <li>审批通过 → APPOVING → APPROVED，并把调整额并入预算（{@link BudgetService#applyApprovedAdjust}）</li>
 *   <li>驳回 / 撤回 → 回退 DRAFT（可改后重提）</li>
 * </ul>
 *
 * <p>额度落账只在 {@code applyApprovedAdjust} 一处实现（与历史直批 {@code adjustApprove} 共用），
 * 防止「流程收尾」与「按钮直批」各算一遍把同一张单加两次。
 *
 * <p>事件分发由 {@link EventOrchestrationBus} 自动注册（Bean 未在 wf_event_action_config 显式配置时
 * 按默认 BOTH + 999 排序接入）。
 */
@Slf4j
@Component("budgetAdjustBizAction")
@RequiredArgsConstructor
public class BudgetAdjustBizAction implements WfBizAction {

    private final BgAdjustMapper bgAdjustMapper;
    private final BudgetService budgetService;

    @Override
    public Set<String> bizTypes() {
        return Collections.singleton("BG_ADJUST");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(WfProcessEndedEvent event) {
        Long adjustId = event.getBizId();
        if (adjustId == null || !"BG_ADJUST".equals(event.getBizType())) {
            return;
        }
        BgAdjust adjust = bgAdjustMapper.selectById(adjustId);
        if (adjust == null) {
            log.warn("预算调整审批收尾: 不存在的调整单 {}", adjustId);
            return;
        }
        if (Boolean.TRUE.equals(event.getApproved())) {
            budgetService.applyApprovedAdjust(adjustId);
            log.info("预算调整单 {} 审批通过 → APPROVED（额度已并入预算）", adjust.getAdjustNo());
            return;
        }
        // 驳回/撤回：只回退仍在审批中的单，避免把已通过的调整又改回草稿
        if (!"APPROVING".equals(adjust.getStatus())) {
            log.info("预算调整单 {} 状态已为 {}，跳过驳回回退", adjust.getAdjustNo(), adjust.getStatus());
            return;
        }
        BgAdjust upd = new BgAdjust();
        upd.setId(adjustId);
        upd.setStatus("DRAFT");
        bgAdjustMapper.updateById(upd);
        log.info("预算调整单 {} 审批驳回/撤回（APPROVING → DRAFT）：{}", adjust.getAdjustNo(), event.getComment());
    }
}
