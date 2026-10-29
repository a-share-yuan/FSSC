// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade;

import com.fssc.framework.facade.dto.ImageFileDTO;
import com.fssc.framework.facade.dto.InvoiceParseResultDTO;
import com.fssc.system.entity.SysFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 影像中心 Facade：核心域与档案域通过此接口访问影像能力，
 * 不直接依赖 ImgFileMapper / FileService。
 *
 * 断点2：getFileIdsByBillId 替代 ArchiveService 直接查询 ImgFileMapper
 * 断点4：moveFileToArchive 替代 ArchiveService 直接调用 FileService.moveZone
 * 断点5：findImageFilesByBizId 替代 RequisitionWorkbenchService 直接查询 ImgFileMapper
 * 分库改造：countBatches / statBatchByBizType 替代 ReportMapper 跨库直查 img_batch
 * （两者收在只读父接口 {@link ImageStatsFacade}，供报表服务远程实现）
 */
public interface ImageFacade extends ImageStatsFacade {

    /**
     * 查询业务单据关联的影像文件 ID 列表（断点2）。
     *
     * @param billId 业务单据 ID
     * @return 去重后的文件 ID 列表
     */
    List<Long> getFileIdsByBillId(Long billId);

    /**
     * 将文件迁移到归档存储区 ARCHIVE（断点4）。
     * 等价于 FileService.moveZone(fileId, "ARCHIVE")。
     *
     * @param fileId 文件 ID
     * @return 迁移后的文件记录（含新路径和存储区），文件不存在时返回 null
     */
    SysFile moveFileToArchive(Long fileId);

    /**
     * 查询业务单据关联的影像页列表（断点5）。
     *
     * @param bizId 业务单据 ID（img_file.biz_id）
     * @return 影像页 DTO 列表
     */
    List<ImageFileDTO> findImageFilesByBizId(Long bizId);

    /**
     * 该附件是否需要票面识别（版式文件 ofd/xml/pdf 或图片发票）。
     * 替代调用方直接引用 image.channel.OcrLocalParsers 判扩展名。
     */
    boolean isInvoiceCandidate(String fileName);

    /**
     * 发票附件结构化识别：本地版式解析优先，扫描版 PDF 转图像 OCR。
     * 替代 ExpenseAttachController 直接注入 OcrService。
     *
     * @param fileId 已落 sys_file 的附件 ID（可为 null）
     */
    InvoiceParseResultDTO parseInvoice(byte[] bytes, String fileName, Long fileId);

    /**
     * 按附件 fileId 取最近一条 OCR 票面键（预检组装 inputData 用）。
     *
     * @return 含 invoiceNo/invoiceCode/invoiceType/amount 的 Map；无记录返回 null
     */
    Map<String, Object> latestInvoiceKeysByFileIds(Long tenantId, List<Long> fileIds);

    /**
     * 按附件 fileId 取已识别票号（去空、去重）。
     */
    Set<String> invoiceNosByFileIds(Long tenantId, List<Long> fileIds);

    /**
     * 按票号取关联附件 fileId 列表（同租户）。
     */
    List<Long> fileIdsByInvoiceNo(Long tenantId, String invoiceNo);

    /**
     * 同票号 OCR 记录计数（全库口径，与原先 SQL 一致，不做租户过滤）。
     */
    long countByInvoiceNo(String invoiceNo);
}
