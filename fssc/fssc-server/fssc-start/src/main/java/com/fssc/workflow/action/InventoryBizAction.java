package com.fssc.workflow.action;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fssc.asset.entity.MdInventory;
import com.fssc.asset.entity.StockMove;
import com.fssc.asset.mapper.MdInventoryMapper;
import com.fssc.asset.mapper.StockMoveMapper;
import com.fssc.asset.service.StockCostSettleService;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 存货域审批收尾（存货成本模型补齐）。
 * <p>
 * 覆盖 11 个存货流程审批通过后的收尾动作：
 * <ul>
 *   <li>INV_ISSUE_PROJECT / INV_ISSUE_NONPROJECT: 领用出库 → 标记 stock_move APPROVED + 成本结转</li>
 *   <li>INV_DISPOSE: 报废 → 标记 stock_move APPROVED</li>
 *   <li>INV_DISPOSAL_APPLY: 处置申请 → 标记 stock_move APPROVED</li>
 *   <li>INV_DISPOSAL_CHECK: 处置状态确认 → 不改库存</li>
 *   <li>INV_DISPOSAL_EXEC: 处置执行账务 → 成本结转 + 凭证</li>
 *   <li>INV_INTERNAL_SALE: 内部销售 → 标记 stock_move APPROVED + 成本结转</li>
 *   <li>INV_PHYSICAL_CHECK: 盘点差异 → 差异入 stock_move + 调整</li>
 *   <li>INV_DEVAL: 跌价准备 → 跌价凭证</li>
 *   <li>INV_RECEIVE: 入库 → 标记 stock_move APPROVED</li>
 *   <li>INV_TRANSFER: 调拨 → 标记 stock_move APPROVED</li>
 *   <li>INV_RETURN: 退库 → 标记 stock_move APPROVED</li>
 * </ul>
 */
@Slf4j
@Component("inventoryBizAction")
@RequiredArgsConstructor
public class InventoryBizAction implements WfBizAction {

    private final MdInventoryMapper inventoryMapper;
    private final StockMoveMapper stockMoveMapper;
    private final StockCostSettleService stockCostSettleService;

    @Override
    public Set<String> bizTypes() {
        return Set.of(
                "INV_ISSUE_PROJECT", "INV_ISSUE_NONPROJECT",
                "INV_DISPOSE", "INV_DISPOSAL_APPLY", "INV_DISPOSAL_CHECK", "INV_DISPOSAL_EXEC",
                "INV_INTERNAL_SALE", "INV_PHYSICAL_CHECK", "INV_DEVAL",
                "INV_RECEIVE", "INV_TRANSFER", "INV_RETURN"
        );
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long bizId = event.getBizId();
        if (bizId == null) return;
        boolean approved = Boolean.TRUE.equals(event.getApproved());
        String bizType = event.getBizType();

        if (!approved) {
            log.info("存货流程 {} 驳回, bizId={}", bizType, bizId);
            // 驳回: 标记 stock_move REJECTED
            try {
                StockMove upd = new StockMove();
                upd.setSourceDocId(bizId);
                upd.setStatus("REJECTED");
                // 按来源单据ID更新移动单状态
                stockMoveMapper.update(upd,
                        new LambdaUpdateWrapper<StockMove>()
                                .eq(StockMove::getSourceDocId, bizId)
                                .eq(StockMove::getStatus, "PENDING"));
            } catch (Exception ex) {
                log.warn("存货流程驳回时更新移动单状态失败: {}", ex.getMessage());
            }
            return;
        }

        // 审批通过: 标记 stock_move APPROVED
        try {
            StockMove upd = new StockMove();
            upd.setStatus("APPROVED");
            stockMoveMapper.update(upd,
                    new LambdaUpdateWrapper<StockMove>()
                            .eq(StockMove::getSourceDocId, bizId)
                            .eq(StockMove::getStatus, "PENDING"));
        } catch (Exception ex) {
            log.warn("存货流程通过时更新移动单状态失败: {}", ex.getMessage());
        }

        // 按流程类型执行收尾
        try {
            switch (bizType) {
                case "INV_ISSUE_PROJECT":
                case "INV_ISSUE_NONPROJECT":
                    // 领用出库审批通过 → 生成领用成本结转凭证
                    stockCostSettleService.settleIssueCost(bizId, event.getProcInstId());
                    break;
                case "INV_DISPOSAL_EXEC":
                    // 处置执行 → 生成处置成本结转凭证
                    stockCostSettleService.settleDisposalCost(bizId, event.getProcInstId());
                    break;
                case "INV_INTERNAL_SALE":
                    // 内部销售 → 生成销售成本结转凭证
                    stockCostSettleService.settleInternalSaleCost(bizId, event.getProcInstId());
                    break;
                case "INV_DEVAL":
                    // 跌价准备 → 生成跌价准备凭证
                    stockCostSettleService.settleDevalProvision(bizId, event.getProcInstId());
                    break;
                case "INV_PHYSICAL_CHECK":
                    // 盘点差异 → 生成盘点差异结转（存货规则；FA_CHECK_DIFF 资产规则结转走 GL_VOUCHER 盘点结转档，见）
                    stockCostSettleService.settleCheckDiff(bizId, event.getProcInstId());
                    break;
                default:
                    log.info("存货流程 {} 审批通过, bizId={}, 无额外成本结转动作", bizType, bizId);
            }
        } catch (Exception ex) {
            log.error("存货流程 {} 成本结转失败, bizId={}: {}", bizType, bizId, ex.getMessage(), ex);
        }
    }
}
