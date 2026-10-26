// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdIoTemplate;
import com.fssc.masterdata.entity.MdIoTemplateField;
import com.fssc.masterdata.service.IoTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 统一导入导出引擎对外接口（V1.4.83/V1.4.84，闭环差距②后半「导入导出无统一引擎」）。
 *
 * <p>路径 {@code /api/tenant/masterdata/io/**}。
 *
 * <p><b>与手写 ExcelService 的关系</b>：6 支手写 Service 的列头已逐字迁为模板数据，
 * 本引擎按模板解释器统一执行。新增导入模板 = 插两行数据，不需要再写 Service。
 */
@RestController
@RequestMapping("/api/tenant/masterdata/io")
@RequiredArgsConstructor
public class IoTemplateController {

    private final IoTemplateService ioService;

    /** 模板列表（direction=IMPORT/EXPORT 可选过滤） */
    @GetMapping("/template/list")
    public Result<List<MdIoTemplate>> list(@RequestParam(required = false) String direction) {
        return Result.success(ioService.listAll(direction));
    }

    /** 模板字段定义（模板设计页用） */
    @GetMapping("/template/{templateCode}/fields")
    public Result<List<MdIoTemplateField>> fields(@PathVariable String templateCode) {
        MdIoTemplate t = ioService.resolveTemplate(templateCode);
        return Result.success(ioService.fieldsOf(t.getId()));
    }

    /**
     * 下载空白导入模板（带表头与一行示例）。
     * 落地「引入模板在系统后台自动生成」：用户不用再找 Excel 模板文件。
     */
    @GetMapping("/template/{templateCode}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String templateCode) {
        byte[] body = ioService.buildBlankTemplate(templateCode);
        String filename = templateCode + ".xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(body));
    }

    /**
     * 按模板解析上传的 Excel（只解析不写入数据库，返回逐行数据与逐行错误）。
     *
     * <p>不中断整批：单行出错只记录，其余行继续解析，便于用户一次改完。
     */
    @PostMapping("/import/parse")
    @RequiresPerm("md:io:template:import")
    @OperLog(module = "导入解析", operType = "IMPORT", desc = "按模板解析上传Excel")
    public Result<IoTemplateService.ImportResult> parse(
            @RequestParam String templateCode,
            @RequestParam("file") MultipartFile file) {
        return Result.success(ioService.importExcel(templateCode, file));
    }

    /** 按模板导出 Excel */
    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export(
            @RequestParam String templateCode,
            @RequestParam(required = false) Map<String, Object> conditions) {
        byte[] body = ioService.exportExcel(templateCode, conditions);
        String filename = templateCode + "_export.xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(body));
    }
}
