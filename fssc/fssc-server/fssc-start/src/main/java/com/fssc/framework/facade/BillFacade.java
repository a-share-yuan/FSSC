// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade;

import com.fssc.common.core.PageResult;
import com.fssc.framework.facade.dto.BillArchiveDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单据 Facade：影像域与档案域通过此接口访问核心域单据数据，
 * 不直接依赖 ExpBillMapper / ExpBillAttachMapper。
 *
 * 断点1：updateImageStatus / markArchived 供事件监听器和归档流程调用
 * 断点3：findPendingArchiveBills / findPendingBillsForScheduledArchive 替代直接查询 ExpBill 表
 */
public interface BillFacade {

    /**
     * 分页查询待归档单据（断点3，UI 入口）。
     * 条件：审批完成（APPROVED/PAID/CLOSED）+ 影像完整（PARTIAL/COMPLETE）+ 按归档状态筛选。
     *
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param billType 单据类型（可空）
     * @param keyword 关键词（单号/标题，可空）
     * @param archiveStatus 归档状态：空＝只看未归档（原行为），ARCHIVED＝只看已归档，ALL＝不限（/B1）
     * @return 待归档单据 DTO 分页
     */
    PageResult<BillArchiveDTO> findPendingArchiveBills(long pageNum, long pageSize,
                                                         String billType, String keyword,
                                                         String archiveStatus);

    /**
     * 查询单条单据详情用于归档（断点3，doArchive 调用）。
     *
     * @param billId 单据 ID
     * @return 单据归档 DTO，不存在时返回 null
     */
    BillArchiveDTO findBillForArchive(Long billId);

    /**
     * 按业务单号查单据（影像域使用：上传影像只给单号时补齐单据归属）。
     * 与 {@link #findPendingArchiveBills} 同一公司范围；单号查不到返回 null。
     *
     * @param billNo 业务单号
     * @return 单据归档 DTO，不存在时返回 null
     */
    BillArchiveDTO findBillByNo(String billNo);

    /**
     * 标记单据已归档（断点3，doArchive 调用）。
     *
     * @param billId 单据 ID
     * @param userId 归档操作人
     * @param now 归档时间
     */
    void markArchived(Long billId, Long userId, LocalDateTime now);

    /**
     * 查询定时归档待处理单据（断点3，scheduledArchive 调用）。
     * 跨租户查询，返回的 DTO 包含 tenantId 用于按租户分组执行。
     *
     * @param limit 最大数量
     * @return 待归档单据 DTO 列表
     */
    List<BillArchiveDTO> findPendingBillsForScheduledArchive(int limit);

    /**
     * 查询业务单据附件文件 ID 列表（断点2 辅助）。
     *
     * @param billId 单据 ID
     * @return 去重后的附件文件 ID 列表
     */
    List<Long> getAttachFileIdsByBillId(Long billId);
}
