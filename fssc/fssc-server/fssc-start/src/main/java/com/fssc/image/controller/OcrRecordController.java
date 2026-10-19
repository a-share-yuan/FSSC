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
 * 提供按批次/发票号查询 OCR 落库记录的能力。
 * 分库改造：直连 OcrRecordMapper，类级路由 fssc_image 库。
 */
@RestController
@RequestMapping("/api/tenant/ocr-records")
@RequiredArgsConstructor
@FsscDataSource("image")
public class OcrRecordController {

    private final OcrRecordMapper ocrRecordMapper;

    /** 分页查询 OCR 记录 */
    @GetMapping
    public Result<PageResult<OcrRecord>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String invoiceNo) {
        LambdaQueryWrapper<OcrRecord> qw = new LambdaQueryWrapper<>();
        if (batchId != null) qw.eq(OcrRecord::getBatchId, batchId);
        if (invoiceNo != null && !invoiceNo.isBlank()) qw.eq(OcrRecord::getInvoiceNo, invoiceNo);
        qw.orderByDesc(OcrRecord::getId);
        Page<OcrRecord> p = ocrRecordMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize));
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
