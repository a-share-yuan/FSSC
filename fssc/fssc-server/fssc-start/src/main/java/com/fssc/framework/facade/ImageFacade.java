package com.fssc.framework.facade;

import com.fssc.framework.facade.dto.ImageFileDTO;
import com.fssc.system.entity.SysFile;

import java.util.List;
import java.util.Map;

/**
 * 影像中心 Facade：核心域与档案域通过此接口访问影像能力，
 * 不直接依赖 ImgFileMapper / FileService。
 *
 * 断点2：getFileIdsByBillId 替代 ArchiveService 直接查询 ImgFileMapper
 * 断点4：moveFileToArchive 替代 ArchiveService 直接调用 FileService.moveZone
 * 断点5：findImageFilesByBizId 替代 RequisitionWorkbenchService 直接查询 ImgFileMapper
 * 分库改造：countBatches / statBatchByBizType 替代 ReportMapper 跨库直查 img_batch
 */
public interface ImageFacade {

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
     * 统计影像批次总数（分库改造：替代 ReportMapper.opsStatistics 中 img_batch 跨库 UNION 分支）。
     *
     * @return 未删除批次总数（del_flag 与 tenant_id 由 MP 拦截器自动注入）
     */
    Long countBatches();

    /**
     * 按业务类型统计影像批次及文档数（分库改造：替代 ReportMapper.imageStatistics 跨库直查）。
     *
     * @return 每行含 bizType/batchCnt/totalDocs/reviewedCnt/sealedCnt，按 batchCnt 降序
     */
    List<Map<String, Object>> statBatchByBizType();
}
