// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.facade.BillFacade;
import com.fssc.framework.facade.ImageFacade;
import com.fssc.framework.facade.dto.BillArchiveDTO;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.image.entity.ImgBatch;
import com.fssc.image.entity.ImgFile;
import com.fssc.image.service.ImageService;
import com.fssc.image.service.OcrService;
import java.util.Map;
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
import org.springframework.beans.factory.ObjectProvider;

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
    /** 影像统计（报表只读服务的远程取数面；ImageFacade extends ImageStatsFacade） */
    private final ImageFacade imageFacade;
    private final OcrService ocrService;
    /** 单号→单据归属回查（仅单体进程有实现；独立影像服务下为空，见 resolveBill） */
    private final ObjectProvider<BillFacade> billFacadeProvider;

    /** 影像上传允许格式：图片 + PDF/OFD/XML（本地版式解析）；其余一律 BizException 拒绝 */
    private static final Set<String> IMAGE_ALLOWED =
            Set.of("jpg", "jpeg", "png", "bmp", "webp", "pdf", "ofd", "xml");

    @RequiresPerm("image:batch:upload")
    @OperLog(module = "IMAGE", operType = "INSERT", desc = "上传影像批次")
    @PostMapping("/batch/upload")
    public Result<Long> upload(@RequestBody UploadReq req) {
        BillRef ref = resolveBill(req.getBizType(), req.getBizId(), req.getBizNo());
        Long id = imageService.uploadBatch(ref.bizType(), ref.bizId(), ref.bizNo(), req.getPages());
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
        BillRef ref = resolveBill(bizType, bizId, bizNo);
        Long id = imageService.uploadBatchMulti(files, ref.bizType(), ref.bizId(), ref.bizNo(), zone);
        autoOcr(id);
        return Result.success(id);
    }

    /**
     * 上传影像只给业务单号时，按单号回查单据把归属（bizId/bizType）补齐。
     * 缺这一步时批次的 biz_no 只是一串字符、批次不挂单据：ImageService 发布影像状态事件时
     * 因 bizId 为空直接返回，单据 image_status 永远停在 NONE，档案「待立卷候选」也就取不到
     * 这张单——影像传了却永远进不了归档。单号查不到（非 exp_bill 域/已删除）时保持原样。
     * <p>
     * 回查必须在本方法内完成：ImageService 的上传是 REQUIRES_NEW 且路由 fssc_image，
     * 事务一开连接就绑在影像库上，此时再走主库 Facade 只会复用那条连接而查不到 exp_bill。
     */
    private BillRef resolveBill(String bizType, Long bizId, String bizNo) {
        if (bizId != null || bizNo == null || bizNo.isBlank()) {
            return new BillRef(bizType, bizId, bizNo);
        }
        // 独立影像服务（profile=image）不加载费用域，BillFacade 实现不存在。
        // 这里是「按单号补归属」的锦上添花，缺实现时按无归属继续（ 真机启动发现的硬依赖）。
        BillFacade billFacade = billFacadeProvider.getIfAvailable();
        if (billFacade == null) {
            log.debug("影像服务未装配 BillFacade（独立进程），按单号 {} 回查归属跳过", bizNo);
            return new BillRef(bizType, bizId, bizNo);
        }
        try {
            BillArchiveDTO bill = billFacade.findBillByNo(bizNo.trim());
            if (bill != null) {
                String type = bill.getBillType() == null || bill.getBillType().isBlank()
                        ? bizType : bill.getBillType();
                String no = bill.getBillNo() == null || bill.getBillNo().isBlank()
                        ? bizNo.trim() : bill.getBillNo();
                return new BillRef(type, bill.getId(), no);
            }
        } catch (Exception ex) {
            log.warn("影像上传按单号回查单据失败 bizNo={}（按无归属继续）: {}", bizNo, ex.toString());
        }
        return new BillRef(bizType, bizId, bizNo);
    }

    private record BillRef(String bizType, Long bizId, String bizNo) {
    }

    /** 上传后自动触发 OCR（识别失败不阻塞上传，页面可手动重试） */
    private void autoOcr(Long batchId) {
        try {
            // 未识别现在是 warning（页态 FAILED＋异常池记录），不再以抛错收场 ⇒ 日志如实记生成了几张
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

    // 影像任务（创建/查询/封单/状态）

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

    /**
     * 影像批次总数（报表只读服务经此取数；HTTP 端点＝Facade 的远程实现面）。
     * 权限：影像岗位（image:batch:list）或报表查看（report:view）任一即可 ——
     * 与原先"报表内核直接调 Facade"的可见面一致，不额外收紧。
     */
    @RequiresPerm({"image:batch:list", "report:view"})
    @GetMapping("/stats/batch-count")
    public Result<Long> batchCount() {
        return Result.success(imageFacade.countBatches());
    }

    /** 按业务类型统计影像批次（同上，报表只读服务的远程取数面） */
    @RequiresPerm({"image:batch:list", "report:view"})
    @GetMapping("/stats/by-biz-type")
    public Result<List<Map<String, Object>>> statsByBizType() {
        return Result.success(imageFacade.statBatchByBizType());
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
