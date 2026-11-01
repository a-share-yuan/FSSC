// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.sharing.entity.OpBarcode;
import com.fssc.sharing.mapper.OpBarcodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 条码管理服务。
 * 支持条码生成、打印、扫描签收、上架。
 * 条码管理、条码追溯与批次条码的处理规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BarcodeService {

    private final OpBarcodeMapper barcodeMapper;

    /**
     * 批量生成条码。
     * @param bizType BILL/ARCHIVE/BOX
     * @param refIds 关联业务ID列表
     * @param refNos 关联业务编号列表（与refIds一一对应）
     * @param printBy 打印人ID
     * @param printName 打印人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> generateBarcodes(String bizType, List<Long> refIds, List<String> refNos,
                                        Long printBy, String printName) {
        return doGenerate(bizType, refIds, refNos, printBy, printName).stream()
                .map(OpBarcode::getId)
                .toList();
    }

    /**
     * 为单个业务对象生成条码并返回实体（交单登记自动生成用）。
     * 需要回写业务表 barcode 列（如 op_physical_delivery.barcode）时用本方法；
     * 编码规则与 {@link #generateBarcodes} 一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public OpBarcode generateForRef(String bizType, Long refId, String refNo, Long printBy, String printName) {
        List<OpBarcode> list = doGenerate(bizType, List.of(refId),
                refNo == null ? null : List.of(refNo), printBy, printName);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<OpBarcode> doGenerate(String bizType, List<Long> refIds, List<String> refNos,
                                        Long printBy, String printName) {
        List<OpBarcode> list = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String prefix = switch (bizType) {
            case "BILL" -> "BL";
            case "ARCHIVE" -> "AR";
            case "BOX" -> "BX";
            default -> "BC";
        };
        for (int i = 0; i < refIds.size(); i++) {
            OpBarcode bc = new OpBarcode();
            bc.setTenantId(TenantContext.getTenantId());
            // 条码值: 前缀 + 时间戳 + 序号 (如 BL20250904120001001)
            String barcodeVal = prefix + LocalDateTime.now().format(fmt) + String.format("%03d", i + 1);
            bc.setBarcode(barcodeVal);
            bc.setBarcodeType("CODE128");
            bc.setBizType(bizType);
            bc.setRefId(refIds.get(i));
            bc.setRefNo(refNos != null && i < refNos.size() ? refNos.get(i) : null);
            bc.setStatus(printBy != null ? "PRINTED" : "GENERATED");
            bc.setPrintBy(printBy);
            bc.setPrintName(printName);
            bc.setPrintTime(printBy != null ? LocalDateTime.now() : null);
            barcodeMapper.insert(bc);
            list.add(bc);
        }
        log.info("Generated {} barcodes for bizType={}", list.size(), bizType);
        return list;
    }

    /**
     * 扫描条码签收：根据条码值查找待签收签收单并完成签收。
     * @param barcodeValue 条码枪扫描出的值
     * @return 匹配到的签收单，null=未找到
     */
    public OpBarcode findByBarcode(String barcodeValue) {
        return barcodeMapper.selectOne(new LambdaQueryWrapper<OpBarcode>()
                .eq(OpBarcode::getBarcode, barcodeValue)
                .last("limit 1"));
    }

    /**
     * 记录扫描动作（更新条码状态为 SCANNED）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordScan(String barcodeValue, Long scanBy, String scanName) {
        OpBarcode bc = findByBarcode(barcodeValue);
        if (bc == null) {
            return;
        }
        OpBarcode upd = new OpBarcode();
        upd.setId(bc.getId());
        upd.setStatus("SCANNED");
        upd.setScanBy(scanBy);
        upd.setScanName(scanName);
        upd.setScanTime(LocalDateTime.now());
        barcodeMapper.updateById(upd);
    }

    /**
     * 档案上架：更新条码状态和位置。
     */
    @Transactional(rollbackFor = Exception.class)
    public void shelve(String barcodeValue, String location, Long shelveBy, String shelveName) {
        OpBarcode bc = findByBarcode(barcodeValue);
        if (bc == null) {
            return;
        }
        OpBarcode upd = new OpBarcode();
        upd.setId(bc.getId());
        upd.setStatus("SHELVED");
        upd.setLocation(location);
        upd.setShelveTime(LocalDateTime.now());
        barcodeMapper.updateById(upd);
        log.info("Barcode {} shelved at {}", barcodeValue, location);
    }

    /** 分页查询条码 */
    public PageResult<OpBarcode> page(long pageNum, long pageSize, String bizType, String status, String keyword) {
        LambdaQueryWrapper<OpBarcode> w = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            w.eq(OpBarcode::getBizType, bizType);
        }
        if (status != null && !status.isBlank()) {
            w.eq(OpBarcode::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(OpBarcode::getBarcode, keyword).or().like(OpBarcode::getRefNo, keyword));
        }
        w.orderByDesc(OpBarcode::getId);
        Page<OpBarcode> p = barcodeMapper.selectPage(Page.of(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }
}
