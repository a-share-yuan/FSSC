// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.image.entity.ImgBatch;
import com.fssc.image.entity.ImgFile;
import com.fssc.image.service.ImageService;
import com.fssc.image.service.OcrService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/** 影像管理（/api/tenant/image/**）：批次上传 + 页面 + OCR 识别 + 单据影像状态回写。
 * 分库改造：类级路由 fssc_image 库（防御性，实际路由由 Service 层注解决定）。 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/image")
@RequiredArgsConstructor
@FsscDataSource("image")
public class ImageController {

    private final ImageService imageService;
    private final OcrService ocrService;

    /** 影像上传允许格式：图片 + PDF/OFD/XML（本地版式解析）；其余一律 BizException 拒绝 */
    private static final Set<String> IMAGE_ALLOWED =
            Set.of("jpg", "jpeg", "png", "bmp", "webp", "pdf", "ofd", "xml");

    @RequiresPerm("image:batch:upload")
    @OperLog(module = "IMAGE", operType = "INSERT", desc = "上传影像批次")
    @PostMapping("/batch/upload")
    public Result<Long> upload(@RequestBody UploadReq req) {
        Long id = imageService.uploadBatch(req.getBizType(), req.getBizId(), req.getBizNo(), req.getPages());
        autoOcr(id);
        return Result.success(id);
    }

    /** 标准 MultipartFile 影像上传（前端 el-upload 链路；zone=TEMP/BUSINESS/ARCHIVE，默认 BUSINESS） */
    @RequiresPerm("image:batch:upload")
    @OperLog(module = "IMAGE", operType = "INSERT", desc = "上传影像批次(多文件)")
    @PostMapping(value = "/batch/upload-multi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Long> uploadMulti(@RequestParam("files") MultipartFile[] files,
                                    @RequestParam(required = false) String bizType,
                                    @RequestParam(required = false) Long bizId,
                                    @RequestParam(required = false) String bizNo,
                                    @RequestParam(required = false) String zone) {
        validateImageFormats(files);
        Long id = imageService.uploadBatchMulti(files, bizType, bizId, bizNo, zone);
        autoOcr(id);
        return Result.success(id);
    }

    /** 上传后自动触发 OCR（识别失败不阻塞上传，页面可手动重试） */
    private void autoOcr(Long batchId) {
        try {
            // D-DEC-009：未识别现在是 warning（页态 FAILED＋异常池记录），不再以抛错收场 ⇒ 日志如实记生成了几张
            int pooled = ocrService.recognizeBatch(batchId);
            log.info("影像批次 {} 上传后自动OCR完成：生成发票 {} 张", batchId, pooled);
        } catch (Exception e) {
            log.warn("上传后自动OCR失败 batchId={}（可手动重试）", batchId, e);
        }
    }

    @RequiresPerm("image:batch:list")
    @GetMapping("/batch/list")
    public Result<List<ImgBatch>> batches(@RequestParam(required = false) String bizType,
                                          @RequestParam(required = false) Long bizId) {
        return Result.success(imageService.batches(bizType, bizId));
    }

    // ---------- 影像任务（需求 6.4：创建/查询/封单/状态） ----------

    @RequiresPerm("image:batch:list")
    @GetMapping("/task/page")
    public Result<PageResult<ImgBatch>> taskPage(PageQuery query, String bizType, String keyword,
                                                 String sealStatus, String status) {
        return Result.success(imageService.taskPage(query.getPageNum(), query.getPageSize(),
                bizType, keyword, sealStatus, status));
    }

    @RequiresPerm("image:batch:update")
    @OperLog(module = "IMAGE", operType = "UPDATE", desc = "影像任务封单/解锁")
    @PostMapping("/task/{id}/seal")
    public Result<Void> seal(@PathVariable Long id, @RequestParam Boolean sealed) {
        imageService.seal(id, Boolean.TRUE.equals(sealed));
        return Result.success();
    }

    @RequiresPerm("image:batch:update")
    @OperLog(module = "IMAGE", operType = "UPDATE", desc = "影像任务调阅审核回写")
    @PostMapping("/task/{id}/review-status")
    public Result<Void> reviewStatus(@PathVariable Long id, @RequestParam String reviewStatus) {
        imageService.setReviewStatus(id, reviewStatus);
        return Result.success();
    }

    @RequiresPerm("image:batch:list")
    @GetMapping("/batch/{id}/pages")
    public Result<List<ImgFile>> pages(@PathVariable Long id) {
        return Result.success(imageService.pages(id));
    }

    /** 触发 OCR 识别批次（页面 → 发票，可插拔引擎） */
    @RequiresPerm("image:ocr:run")
    @OperLog(module = "IMAGE", operType = "UPDATE", desc = "OCR识别影像批次")
    @PostMapping("/batch/{id}/ocr")
    public Result<Integer> ocr(@PathVariable Long id) {
        return Result.success(ocrService.recognizeBatch(id));
    }

    /**
     * 按附件触发 OCR：附件列表持有的是 fileId，前端不应把它当 batchId 传。
     * 后端按 fileId 解析出所属影像批次后执行识别。
     */
    @RequiresPerm("image:ocr:run")
    @OperLog(module = "IMAGE", operType = "UPDATE", desc = "OCR识别附件")
    @PostMapping("/file/{fileId}/ocr")
    public Result<Integer> ocrByFile(@PathVariable Long fileId) {
        return Result.success(ocrService.recognizeByFile(fileId));
    }

    @RequiresPerm("image:batch:delete")
    @OperLog(module = "IMAGE", operType = "DELETE", desc = "删除影像批次")
    @PostMapping("/batch/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        imageService.deleteBatch(id);
        return Result.success();
    }

    /** 影像格式白名单校验：仅允许图片(jpg/jpeg/png/bmp/webp) + PDF/OFD/XML */
    private void validateImageFormats(MultipartFile[] files) {
        if (files == null) {
            return;
        }
        for (MultipartFile f : files) {
            String name = f == null ? null : f.getOriginalFilename();
            String ext = "";
            if (name != null) {
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && dot < name.length() - 1) {
                    ext = name.substring(dot + 1).toLowerCase();
                }
            }
            if (!IMAGE_ALLOWED.contains(ext)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "不支持的影像格式（." + ext + "），仅允许 jpg/jpeg/png/bmp/webp/pdf/ofd/xml");
            }
        }
    }

    @Data
    public static class UploadReq {
        private String bizType;
        private Long bizId;
        private String bizNo;
        private List<ImageService.PageReq> pages;
    }
}
