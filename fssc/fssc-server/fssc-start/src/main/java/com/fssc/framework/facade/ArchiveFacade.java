// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade;

import com.fssc.framework.facade.dto.ArchiveInfoDTO;
import com.fssc.framework.facade.dto.ArchiveRegisterDTO;

import java.util.List;

/**
 * 档案中心 Facade：其他域通过此接口访问档案能力，
 * 不直接依赖 OpArchiveMapper / ArchiveDestroyApprovalMapper / ArchiveAccessLogMapper。
 *
 * 断点5：findLatestByRefId 替代 RequisitionWorkbenchService 直接查询 OpArchiveMapper
 * 断点6：findByBillId 替代 BillTraceChainController 直接查询 OpArchiveMapper
 * 断点8：registerBankSlipArchive 替代 BankReconcileService 直接插入 op_archive
 * 断点9：三个审批收尾方法替代 ArchiveBizAction 直接操作档案域三张表
 */
public interface ArchiveFacade {

    /**
     * 查询关联单据的最新一条档案（断点5）。
     *
     * @param refId 关联单据 ID（op_archive.ref_id）
     * @return 最新档案 DTO，不存在时返回 null
     */
    ArchiveInfoDTO findLatestByRefId(Long refId);

    /**
     * 按单据 ID 查询档案列表（断点6，op_archive.bill_id 直挂）。
     *
     * <p>D-TRC-06：{@code doArchive} 归档时只写 ref_id、不写 bill_id，故并口收
     * {@code archive_type='BILL' AND ref_id=?}；两处都是本域内的裸主键，不跨域借用。
     *
     * @param billId 单据 ID
     * @return 档案 DTO 列表，按 ID 正序
     */
    List<ArchiveInfoDTO> findByBillId(Long billId);

    /**
     * 按审批流程实例反查档案（D-TRC-05 回溯列，非费用域联查唯一可用的档案锚点）。
     *
     * @param procInstIds 本单据各轮审批的流程实例 ID
     * @return 档案 DTO 列表，按 ID 正序；入参为空时返回空列表
     */
    List<ArchiveInfoDTO> findByProcInstIds(java.util.Collection<String> procInstIds);

    /**
     * 按档案主键取一行（D-DEC-240，「档案即单据」的域专用）。
     *
     * <p>归档审批流（FM_ARCHIVE）的 {@code bizId} 就是 {@code op_archive.id}，而该表的
     * {@code proc_inst_id} 存的是<b>来源单据</b>的审批流程实例、不是归档审批本身的流程实例 ⇒
     * 用本域流程回溯 {@link #findByProcInstIds} 必然 0 命中。主键取自身仍是本域内关联，
     * 不涉及跨域裸主键。
     *
     * @param archiveId 档案 ID
     * @return 档案 DTO，不存在时返回 null
     */
    ArchiveInfoDTO findById(Long archiveId);

    /**
     * 登记银行回单档案（断点8）。
     *
     * @param dto 登记参数
     * @return 新档案 ID
     */
    Long registerBankSlipArchive(ArchiveRegisterDTO dto);

    /**
     * 归档审批收尾：bizId = op_archive.id，审批通过置 ARCHIVED（断点9）。
     *
     * @param archiveId 档案 ID
     * @param approved  是否审批通过
     */
    void handleArchiveApproval(Long archiveId, boolean approved);

    /**
     * 销毁审批收尾：bizId = archive_destroy_approval.id，
     * 审批通过置审批单 APPROVED 并将涉及档案全部置 DESTROYED（断点9）。
     *
     * @param approvalId 销毁审批单 ID
     * @param approved   是否审批通过
     */
    void handleDestroyApproval(Long approvalId, boolean approved);

    /**
     * 借阅审批收尾：bizId = archive_access_log.id（断点9）。
     *
     * @param accessLogId 借阅单 ID
     * @param approved    是否审批通过
     */
    void handleBorrowApproval(Long accessLogId, boolean approved);

    /**
     * D-DEC-443：带终审操作人的重载——引擎收尾线程无登录态，审批人只能由事件带回。
     * 默认回落两参行为（不写 approver_id），实现方覆盖后才落"谁批的"。
     *
     * @param endOperUserId 审批终态最后操作人 userId；null＝系统代做/取不到，留空不猜
     */
    default void handleBorrowApproval(Long accessLogId, boolean approved, Long endOperUserId) {
        handleBorrowApproval(accessLogId, approved);
    }
}
