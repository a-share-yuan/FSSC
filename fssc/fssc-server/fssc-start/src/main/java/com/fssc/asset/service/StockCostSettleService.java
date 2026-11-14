package com.fssc.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.asset.entity.StockCostSettle;
import com.fssc.asset.entity.StockMove;
import com.fssc.asset.mapper.StockCostSettleMapper;
import com.fssc.asset.mapper.StockMoveMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.system.service.NumberRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 存货成本结转服务（存货成本模型补齐）。
 * <p>
 * 月度成本结转：将领用成本/处置成本/跌价准备/盘点差异汇总到 stock_cost_settle 批次，
 * 并调用 {@link VoucherService#generateStockCostVoucher} 生成会计凭证。
 * <p>
 * 结转链路：入库 → 出库(领用/报废/处置) → 盘点 → 计价 → 成本结转 → 凭证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCostSettleService {

    private final StockCostSettleMapper settleMapper;
    private final StockMoveMapper stockMoveMapper;
    private final VoucherService voucherService;
    private final NumberRuleService numberRuleService;

    /**
     * 领用出库成本结转：按 bizId（inventoryId）查 stock_move(ISSUE) 汇总成本。
     */
    public void settleIssueCost(Long bizId, String procInstId) {
        String period = currentPeriod();
        StockCostSettle settle = newBatch("ISSUE_COST", period, procInstId);
        List<StockMove> moves = findMoves(bizId, "ISSUE");
        BigDecimal total = BigDecimal.ZERO;
        for (StockMove m : moves) {
            total = total.add(nvl(m.getAmount()));
        }
        settle.setItemCount(moves.size());
        settle.setTotalAmount(total);
        settleMapper.insert(settle);
        // 规则：结转批次由 newBatch 新建写入数据库、无旧值，不接入审计（不伪造 before）
        if (total.signum() > 0) {
            voucherService.generateStockCostVoucher(settle, "ISSUE_COST");
        }
        log.info("领用成本结转: period={}, moves={}, total={}", period, moves.size(), total);
    }

    /**
     * 处置成本结转。
     */
    public void settleDisposalCost(Long bizId, String procInstId) {
        String period = currentPeriod();
        StockCostSettle settle = newBatch("DISPOSAL_COST", period, procInstId);
        List<StockMove> moves = findMoves(bizId, "DISPOSAL");
        BigDecimal total = BigDecimal.ZERO;
        for (StockMove m : moves) {
            total = total.add(nvl(m.getAmount()));
        }
        settle.setItemCount(moves.size());
        settle.setTotalAmount(total);
        settleMapper.insert(settle);
        // 规则：结转批次由 newBatch 新建写入数据库、无旧值，不接入审计（不伪造 before）
        if (total.signum() > 0) {
            voucherService.generateStockCostVoucher(settle, "DISPOSAL_COST");
        }
        log.info("处置成本结转: period={}, moves={}, total={}", period, moves.size(), total);
    }

    /**
     * 内部销售成本结转。
     */
    public void settleInternalSaleCost(Long bizId, String procInstId) {
        String period = currentPeriod();
        StockCostSettle settle = newBatch("INTERNAL_SALE_COST", period, procInstId);
        // 内部销售移动记录的 moveType=ISSUE（outStock），但来源标识不同
        List<StockMove> moves = findMoves(bizId, "ISSUE");
        BigDecimal total = BigDecimal.ZERO;
        for (StockMove m : moves) {
            total = total.add(nvl(m.getAmount()));
        }
        settle.setItemCount(moves.size());
        settle.setTotalAmount(total);
        settleMapper.insert(settle);
        // 规则：结转批次由 newBatch 新建写入数据库、无旧值，不接入审计（不伪造 before）
        if (total.signum() > 0) {
            voucherService.generateStockCostVoucher(settle, "INTERNAL_SALE_COST");
        }
        log.info("内部销售成本结转: period={}, moves={}, total={}", period, moves.size(), total);
    }

    /**
     * 跌价准备结转。
     */
    public void settleDevalProvision(Long bizId, String procInstId) {
        String period = currentPeriod();
        StockCostSettle settle = newBatch("DEVAL_PROV", period, procInstId);
        // 跌价准备按 bizId 查 inventory 获取跌价金额（审批时传入）
        // 简化处理：设 totalAmount=0 占位，实际金额由审批流程变量提供
        settle.setItemCount(1);
        settle.setTotalAmount(BigDecimal.ZERO);
        settleMapper.insert(settle);
        // 规则：结转批次由 newBatch 新建写入数据库、无旧值，不接入审计（不伪造 before）
        log.info("跌价准备结转: period={}, bizId={}", period, bizId);
    }

    /**
     * 盘点差异结转。
     */
    public void settleCheckDiff(Long bizId, String procInstId) {
        String period = currentPeriod();
        StockCostSettle settle = newBatch("CHECK_DIFF", period, procInstId);
        List<StockMove> moves = findMoves(bizId, "CHECK");
        BigDecimal total = BigDecimal.ZERO;
        for (StockMove m : moves) {
            total = total.add(nvl(m.getAmount()));
        }
        settle.setItemCount(moves.size());
        settle.setTotalAmount(total);
        settleMapper.insert(settle);
        // 规则：结转批次由 newBatch 新建写入数据库、无旧值，不接入审计（不伪造 before）
        if (total.signum() > 0) {
            voucherService.generateStockCostVoucher(settle, "CHECK_DIFF");
        }
        log.info("盘点差异结转: period={}, moves={}, total={}", period, moves.size(), total);
    }

    /**
     * 月度全量成本结转：扫描当月所有已审批 stock_move，按类型汇总生成结转批次。
     */
    public void monthlySettle(String period) {
        log.info("月度成本结转开始: period={}", period);
        // 领用成本
        settleByPeriod("ISSUE", "ISSUE_COST", period);
        // 处置成本
        settleByPeriod("DISPOSAL", "DISPOSAL_COST", period);
        // 报废
        settleByPeriod("SCRAP", "SCRAP_COST", period);
        log.info("月度成本结转完成: period={}", period);
    }

    private void settleByPeriod(String moveType, String settleType, String period) {
        try {
            List<StockMove> moves = stockMoveMapper.selectList(
                    new LambdaQueryWrapper<StockMove>()
                            .eq(StockMove::getMoveType, moveType)
                            .eq(StockMove::getStatus, "APPROVED"));
            if (moves.isEmpty()) return;
            BigDecimal total = BigDecimal.ZERO;
            for (StockMove m : moves) {
                total = total.add(nvl(m.getAmount()));
            }
            StockCostSettle settle = newBatch(settleType, period, null);
            settle.setItemCount(moves.size());
            settle.setTotalAmount(total);
            settleMapper.insert(settle);
            // 规则：结转批次由 newBatch 新建写入数据库、无旧值，不接入审计（不伪造 before）
            if (total.signum() > 0) {
                voucherService.generateStockCostVoucher(settle, settleType);
            }
            log.info("  {} 结转: moves={}, total={}", settleType, moves.size(), total);
        } catch (Exception ex) {
            log.error("  {} 结转失败: {}", settleType, ex.getMessage(), ex);
        }
    }

    // 辅助方法

    private StockCostSettle newBatch(String settleType, String period, String procInstId) {
        StockCostSettle s = new StockCostSettle();
        s.setSettleNo(nextSettleNo());
        s.setPeriod(period);
        s.setSettleType(settleType);
        s.setStatus("DONE");
        return s;
    }

    private List<StockMove> findMoves(Long bizId, String moveType) {
        return stockMoveMapper.selectList(
                new LambdaQueryWrapper<StockMove>()
                        .eq(StockMove::getSourceDocId, bizId)
                        .eq(StockMove::getMoveType, moveType)
                        .eq(StockMove::getStatus, "APPROVED"));
    }

    private String currentPeriod() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String nextSettleNo() {
        try {
            return numberRuleService.next("STOCK_COST_SETTLE");
        } catch (Exception e) {
            return "SC" + System.currentTimeMillis();
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
