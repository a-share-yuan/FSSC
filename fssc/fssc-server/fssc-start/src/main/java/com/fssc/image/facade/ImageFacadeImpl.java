// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.facade.ImageFacade;
import com.fssc.framework.facade.dto.ImageFileDTO;
import com.fssc.image.entity.ImgBatch;
import com.fssc.image.entity.ImgFile;
import com.fssc.image.mapper.ImgBatchMapper;
import com.fssc.image.mapper.ImgFileMapper;
import com.fssc.system.entity.SysFile;
import com.fssc.system.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ImageFacade 实现：影像域内部直接访问 ImgFileMapper 和 FileService，
 * 对外只暴露接口方法，实现依赖反转。
 *
 * 分库改造：类级路由 fssc_image 库（img_file 所在库）；读方法 NOT_SUPPORTED
 * 挂起调用方（可能为主域）事务后取 image 连接；moveFileToArchive 操作
 * sys_file（master 表），方法级覆盖路由回 master。
 */
@Service
@RequiredArgsConstructor
@FsscDataSource("image")
public class ImageFacadeImpl implements ImageFacade {

    private final ImgFileMapper imgFileMapper;
    private final ImgBatchMapper imgBatchMapper;
    private final FileService fileService;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public List<Long> getFileIdsByBillId(Long billId) {
        return imgFileMapper.selectList(new LambdaQueryWrapper<ImgFile>()
                        .eq(ImgFile::getBizId, billId))
                .stream()
                .map(ImgFile::getFileId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @FsscDataSource("master")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SysFile moveFileToArchive(Long fileId) {
        return fileService.moveZone(fileId, "ARCHIVE");
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public List<ImageFileDTO> findImageFilesByBizId(Long bizId) {
        return imgFileMapper.selectList(new LambdaQueryWrapper<ImgFile>()
                        .eq(ImgFile::getBizId, bizId))
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public Long countBatches() {
        // del_flag=0 与 tenant_id 由 @TableLogic / 租户拦截器自动注入
        return imgBatchMapper.selectCount(null);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public List<Map<String, Object>> statBatchByBizType() {
        // 与原 ReportMapper.imageStatistics SQL 逐列等价；del_flag/tenant_id 由拦截器自动注入
        return imgBatchMapper.selectMaps(new QueryWrapper<ImgBatch>()
                .select("biz_type as bizType, count(*) as batchCnt, sum(doc_count) as totalDocs, "
                        + "sum(case when review_status = 'PASSED' then 1 else 0 end) as reviewedCnt, "
                        + "sum(case when seal_status = 'DONE' then 1 else 0 end) as sealedCnt")
                .groupBy("biz_type")
                .orderByDesc("batchCnt"));
    }

    private ImageFileDTO toDTO(ImgFile f) {
        ImageFileDTO dto = new ImageFileDTO();
        dto.setId(f.getId());
        dto.setBatchId(f.getBatchId());
        dto.setFileId(f.getFileId());
        dto.setPageType(f.getPageType());
        dto.setPageNo(f.getPageNo());
        dto.setOcrStatus(f.getOcrStatus());
        dto.setOcrText(f.getOcrText());
        dto.setBizType(f.getBizType());
        dto.setBizId(f.getBizId());
        dto.setVerifyStatus(f.getVerifyStatus());
        dto.setRemark(f.getRemark());
        dto.setCreateTime(f.getCreateTime());
        return dto;
    }
}
