// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.image.entity.OcrRecord;
import com.fssc.image.mapper.OcrRecordMapper;
import com.fssc.image.vo.OcrRecordVO;
import com.fssc.system.entity.SysFile;
import com.fssc.system.service.FileService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * OCR 识别结果查询。
 * 提供按批次/发票号查询 OCR 写入数据库记录的能力。
 * 分库改造：直连 OcrRecordMapper，类级路由 fssc_image 库。
 */
@RestController
@RequestMapping("/api/tenant/ocr-records")
@RequiredArgsConstructor
@FsscDataSource("image")
public class OcrRecordController {

    private final OcrRecordMapper ocrRecordMapper;
    private final FileService fileService;

    /** 分页查询 OCR 记录（带批次号和文件名） */
    @GetMapping
    public Result<PageResult<OcrRecordVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String invoiceNo) {
        IPage<OcrRecordVO> p =
            ocrRecordMapper.selectPageWithRelations(Page.of(pageNum, pageSize), batchId, invoiceNo);
        fillFileNames(p.getRecords());
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize));
    }

    /** 文件名在 master 库的 sys_file（本类路由在 image 库）⇒ 单列回填，不要跨库 JOIN */
    private void fillFileNames(List<OcrRecordVO> records) {
        if (records == null || records.isEmpty()) return;
        Set<Long> ids = records.stream()
                .map(OcrRecordVO::getFileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        Map<Long, String> names = fileService.listByIds(ids).stream()
                .filter(f -> f.getId() != null && f.getFileName() != null)
                .collect(Collectors.toMap(
                        SysFile::getId,
                        SysFile::getFileName,
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
