// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.image.entity.OcrRecord;
import com.fssc.image.mapper.OcrRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * P2-5 OCR 识别结果查询。
 * 提供按批次/发票号查询 OCR 写入数据库记录的能力。
 * 分库改造：直连 OcrRecordMapper，类级路由 fssc_image 库。
 */
@RestController
@RequestMapping("/api/tenant/ocr-records")
@RequiredArgsConstructor
@FsscDataSource("image")
public class OcrRecordController {

    private final OcrRecordMapper ocrRecordMapper;
    private final com.fssc.system.service.FileService fileService;

    /** 分页查询 OCR 记录（带批次号和文件名） */
    @GetMapping
    public Result<PageResult<com.fssc.image.vo.OcrRecordVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String invoiceNo) {
        com.baomidou.mybatisplus.core.metadata.IPage<com.fssc.image.vo.OcrRecordVO> p = 
            ocrRecordMapper.selectPageWithRelations(Page.of(pageNum, pageSize), batchId, invoiceNo);
        fillFileNames(p.getRecords());
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize));
    }

    /** 文件名在 master 库的 sys_file（本类路由在 image 库）⇒ 单列回填，跨库 JOIN（D-F1R-11） */
    private void fillFileNames(java.util.List<com.fssc.image.vo.OcrRecordVO> records) {
        if (records == null || records.isEmpty()) return;
        java.util.Set<Long> ids = records.stream()
                .map(com.fssc.image.vo.OcrRecordVO::getFileId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) return;
        java.util.Map<Long, String> names = fileService.listByIds(ids).stream()
                .filter(f -> f.getId() != null && f.getFileName() != null)
                .collect(java.util.stream.Collectors.toMap(
                        com.fssc.system.entity.SysFile::getId,
                        com.fssc.system.entity.SysFile::getFileName,
                        (a, b) -> a));
        records.forEach(r -> r.setFileName(names.get(r.getFileId())));
    }

    /** 按批次查询全部 OCR 记录 */
    @GetMapping("/batch/{batchId}")
    public Result<?> listByBatch(@PathVariable Long batchId) {
        return Result.success(ocrRecordMapper.selectList(
                new LambdaQueryWrapper<OcrRecord>()
                        .eq(OcrRecord::getBatchId, batchId)
                        .orderByAsc(OcrRecord::getId)));
    }
}
