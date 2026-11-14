package com.fssc.sharing.facade;

import com.fssc.document.service.BillArchiveService;
import com.fssc.framework.facade.event.BillTerminalEvent;
import com.fssc.framework.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 单据最终状态事件监听器（断点3 + 断点7 激活）。
 *
 * 当核心域 ExpenseWfEventListener 发布 BillTerminalEvent 时，档案域接收并判断：
 * - 影像完整（PARTIAL/COMPLETE）→ 记录为可归档候选，等待 scheduledArchive 或人工归档处理
 * - 影像不完整/无影像（ 电子单据：无纸质件）→ 自动执行「电子归档」闭环：
 *  生成 op_archive 档案目录记录（单据+凭证号+附件清单）并回写单据归档标记，
 *  保证 /archive 档案管理页面可检索（BillArchiveService.eArchiveBill，去重）。
 *
 * 当前事件驱动与定时查询（BillFacade.findPendingBillsForScheduledArchive）并行存在，
 * 后续可将待归档候选写入本地队列表，替代定时反查 ExpBill 表。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillTerminalEventListener {

    private final BillArchiveService billArchiveService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBillTerminalEvent(BillTerminalEvent event) {
        String imageStatus = event.getImageStatus();
        boolean imageComplete = "PARTIAL".equals(imageStatus) || "COMPLETE".equals(imageStatus);
        if (imageComplete) {
            log.info("收到可归档单据终态事件: billId={}, billNo={}, status={}, imageStatus={}，待归档",
                    event.getBillId(), event.getBillNo(), event.getStatus(), imageStatus);
            return;
        }
        log.info("收到单据终态事件（无纸质影像电子单据）: billId={}, billNo={}, status={}, imageStatus={}，执行电子归档",
                event.getBillId(), event.getBillNo(), event.getStatus(), imageStatus);
        // 电子单据审批通过 → 自动电子归档（档案目录 + 单据归档标记），失败不阻断事件链
        try {
            Long tid = event.getTenantId();
            if (tid == null) {
                billArchiveService.eArchiveBill(event.getBillId());
            } else {
                TenantContext.callAs(tid,
                        () -> billArchiveService.eArchiveBill(event.getBillId()));
            }
        } catch (Exception e) {
            log.error("电子归档执行失败 billId={}, billNo={}", event.getBillId(), event.getBillNo(), e);
        }
    }
}
