package com.fssc.workflow.action;

import com.fssc.framework.facade.ArchiveFacade;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 档案管理审批收尾（P0-1 事件编排总线）。
 * <p>
 * P1 修复 bizId 错位（原实现把三种 bizType 的 bizId 都当 op_archive.id 查，FM_DESTROY/FM_BORROW 永远空转）：
 * <ul>
 *   <li>FM_ARCHIVE：bizId = op_archive.id → status: ARCHIVED</li>
 *   <li>FM_DESTROY：bizId = archive_destroy_approval.id → approvalStatus APPROVED/REJECTED + 档案 DESTROYED</li>
 *   <li>FM_BORROW：bizId = archive_access_log.id → approvalStatus APPROVED/REJECTED</li>
 * </ul>
 * 断点9：本类只做事件分发，档案域数据访问全部经 ArchiveFacade，
 * 不再直接依赖 OpArchiveMapper / ArchiveDestroyApprovalMapper / ArchiveAccessLogMapper。
 */
@Slf4j
@Component("archiveBizAction")
@RequiredArgsConstructor
public class ArchiveBizAction implements WfBizAction {

    private final ArchiveFacade archiveFacade;

    @Override
    public Set<String> bizTypes() {
        return Set.of("FM_ARCHIVE", "FM_DESTROY", "FM_BORROW");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long bizId = event.getBizId();
        if (bizId == null) return;
        boolean approved = Boolean.TRUE.equals(event.getApproved());
        try {
            switch (event.getBizType()) {
                case "FM_ARCHIVE" -> archiveFacade.handleArchiveApproval(bizId, approved);
                case "FM_DESTROY" -> archiveFacade.handleDestroyApproval(bizId, approved);
                case "FM_BORROW" -> archiveFacade.handleBorrowApproval(bizId, approved, event.getEndOperUserId());
                default -> log.warn("档案收尾: 未知 bizType {}", event.getBizType());
            }
        } catch (Exception ex) {
            log.error("档案收尾失败: bizType={}, bizId={}", event.getBizType(), bizId, ex);
        }
    }
}
