// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.facade.ImageFacade;
import com.fssc.framework.facade.dto.ImageFileDTO;
import com.fssc.framework.facade.dto.InvoiceParseResultDTO;
import com.fssc.image.channel.OcrLocalParsers;
import com.fssc.image.channel.OcrResult;
import com.fssc.image.entity.ImgBatch;
import com.fssc.image.entity.ImgFile;
import com.fssc.image.mapper.ImgBatchMapper;
import com.fssc.image.mapper.ImgFileMapper;
import com.fssc.image.service.OcrService;
import com.fssc.system.entity.SysFile;
import com.fssc.system.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ImageFacade 实现：影像域内部直接访问 ImgFileMapper 和 FileService，
 * 对外只暴露接口方法，实现依赖反转。
 *
 * 分库改造：类级路由 fssc_image 库（img_file 所在库）；读方法 NOT_SUPPORTED
 * 挂起调用方（可能为主域）事务后取 image 连接；moveFileToArchive 操作
 * sys_file（master 表），方法级覆盖路由回 master。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("image")
public class ImageFacadeImpl implements ImageFacade {

    /** 图片类发票扩展名：本地版式解析不支持，走图像 OCR 通道 */
    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "bmp", "webp", "tif", "tiff");

    private final ImgFileMapper imgFileMapper;
    private final ImgBatchMapper imgBatchMapper;
    private final FileService fileService;
    private final OcrService ocrService;
    /** 票面键/票号查重走 ocr_record；类级路由已置 image 库，SQL 不再带库名前缀 */
    private final JdbcTemplate jdbcTemplate;

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

    @Override
    public boolean isInvoiceCandidate(String fileName) {
        return OcrLocalParsers.isLocalFormat(fileName) || isImageFile(fileName);
    }

    @Override
    public InvoiceParseResultDTO parseInvoice(byte[] bytes, String fileName, Long fileId) {
        OcrResult r = ocrService.recognizeInvoiceBytes(bytes, fileName, fileId);
        InvoiceParseResultDTO d = new InvoiceParseResultDTO();
        d.setOk(r.isOk());
        d.setErrorMsg(r.getErrorMsg());
        d.setInPool(r.getInPool());
        d.setInvoiceCode(r.getInvoiceCode());
        d.setInvoiceType(r.getInvoiceType());
        d.setInvoiceDate(r.getInvoiceDate());
        d.setInvoiceNo(r.getInvoiceNo());
        d.setAmount(r.getAmount());
        d.setTaxAmount(r.getTaxAmount());
        d.setNetAmount(r.getNetAmount());
        d.setSellerName(r.getSellerName());
        d.setSellerTaxNo(r.getSellerTaxNo());
        d.setBuyerName(r.getBuyerName());
        d.setBuyerTaxNo(r.getBuyerTaxNo());
        d.setPrimaryItemName(r.getPrimaryItemName());
        d.setRawText(r.getRawText());
        return d;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public Map<String, Object> latestInvoiceKeysByFileIds(Long tenantId, List<Long> fileIds) {
        if (tenantId == null || fileIds == null || fileIds.isEmpty()) {
            return null;
        }
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.addAll(fileIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select invoice_no, invoice_code, invoice_type, amount from ocr_record "
                        + "where tenant_id = ? and invoice_no is not null and invoice_no <> '' and file_id in ("
                        + placeholders(fileIds) + ") order by id desc limit 1", params.toArray());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> r = rows.get(0);
        Map<String, Object> out = new HashMap<>();
        out.put("invoiceNo", r.get("invoice_no"));
        out.put("invoiceCode", r.get("invoice_code"));
        out.put("invoiceType", r.get("invoice_type"));
        out.put("amount", r.get("amount"));
        return out;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public Set<String> invoiceNosByFileIds(Long tenantId, List<Long> fileIds) {
        if (tenantId == null || fileIds == null || fileIds.isEmpty()) {
            return Set.of();
        }
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.addAll(fileIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select distinct invoice_no from ocr_record "
                        + "where tenant_id = ? and invoice_no is not null and invoice_no <> '' and file_id in ("
                        + placeholders(fileIds) + ")", params.toArray());
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object no = row.get("invoice_no");
            if (no != null && !no.toString().isBlank()) {
                out.add(no.toString());
            }
        }
        return out;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public List<Long> fileIdsByInvoiceNo(Long tenantId, String invoiceNo) {
        if (tenantId == null || invoiceNo == null || invoiceNo.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select distinct file_id from ocr_record "
                        + "where tenant_id = ? and invoice_no = ? and file_id is not null",
                tenantId, invoiceNo);
        List<Long> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object id = row.get("file_id");
            if (id instanceof Number n) {
                out.add(n.longValue());
            }
        }
        return out;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public long countByInvoiceNo(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            return 0L;
        }
        Long cnt = jdbcTemplate.queryForObject(
                "select count(*) from ocr_record where invoice_no = ?", Long.class, invoiceNo);
        return cnt == null ? 0L : cnt;
    }

    private static String placeholders(List<?> items) {
        return items.stream().map(x -> "?").collect(Collectors.joining(","));
    }

    private static boolean isImageFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
        return IMAGE_EXTS.contains(ext);
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
