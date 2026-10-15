package com.fssc.expense.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.image.channel.OcrLocalParsers;
import com.fssc.image.channel.OcrResult;
import com.fssc.system.entity.SysFile;
import com.fssc.system.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 报销/付款单的「发票 / 附件」上传（/api/tenant/expense/attach/**）。
 *
 * <p>与通用 /tenant/system/file/upload 的区别：上传后立即对发票类文件做结构化识别，
 * 返回票面字段，供前端「已解析发票清单 → 勾选 → 填入报销单费用明细行」。</p>
 *
 * <ul>
 *  <li>发票版式文件（.ofd/.xml/.pdf）：attachType=INVOICE；<b>先本地版式解析，全程不发 OCR 请求</b>
 *      <b>再转图像 OCR 通道</b>真识别；仍解析不出（如数电票 XML 机器文件解坏）时 invoice.ok=false，由用户手工补录，
 *      不以假票面冒充已识别（D-DEC-009）。</li>
 *  <li>图片发票（.jpg/.png 等，）：attachType=INVOICE；走可插拔图像 OCR 通道
 *      （paddle=本地 PaddleOCR /invoice_ocr）结构化识别，识别结果同时落 ocr_record +
 *      自动入池 inv_invoice（见 OcrService.recognizeImageBytes／recognizeInvoiceBytes）。通道为 mock 或识别失败时
 *      invoice.ok=false，不阻断上传，由用户手工补录。</li>
 *  <li>其余格式：attachType=OTHER，invoice 为 null。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tenant/expense/attach")
@RequiredArgsConstructor
public class ExpenseAttachController {

    /** 图片类发票扩展名：本地版式解析不支持，走图像 OCR 通道（与 OcrService.IMAGE_EXTS 口径一致） */
    private static final java.util.Set<String> IMAGE_EXTS = java.util.Set.of(
            "jpg", "jpeg", "png", "bmp", "webp", "tif", "tiff");

    private final FileService fileService;
    private final com.fssc.image.service.OcrService ocrService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @OperLog(module = "EXPENSE_ATTACH", operType = "INSERT", desc = "上传并解析报销发票附件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(name = "billId", required = false) Long billId) {
        SysFile f = fileService.upload(file, "INVOICE", billId, "TEMP");
        // 缺陷修复（H-0）：单据附件上传后原路径不发布 BillImageEvent，
        // exp_bill.image_status 恒 NONE。此处对已保存单据补发影像事件
        // （AFTER_COMMIT 监听回写 exp_bill.image_status，枚举沿用 NONE/PARTIAL/COMPLETE）。
        if (billId != null) {
            try {
                eventPublisher.publishEvent(
                        new com.fssc.framework.facade.event.BillImageEvent(billId, "COMPLETE", "EXP_CLAIM"));
            } catch (Exception ex) {
                // 影像状态回写失败不阻断附件上传主流程
            }
        }
        String fileName = f.getFileName();
        Map<String, Object> result = new HashMap<>();
        result.put("id", f.getId());
        result.put("fileName", fileName);
        result.put("attachType", "OTHER");
        result.put("invoice", null);

        if (OcrLocalParsers.isLocalFormat(fileName) || isImageInvoice(fileName)) {
            // D-DEC-474：票面识别统一交 OcrService.recognizeInvoiceBytes ——
            // 本地版式解析（PDF／OFD／数电票 XML）优先、全程不发 OCR 请求，解析不出（扫描版 PDF 无文本层）
            // 才把解析器渲染好的首页 PNG 转图像 OCR 通道，与影像批次腿同一优先级次序。
            // 改前本腿只判扩展名：扫描版 PDF 的 PNG 被直接丢弃 ⇒ 停在"未识别"，用户被引导去电脑端补录，
            // 而电脑端走的正是同一条腿（两端同一接口）。
            OcrResult r;
            try {
                r = ocrService.recognizeInvoiceBytes(file.getBytes(), fileName, f.getId());
            } catch (Throwable t) {
                // 用 Throwable 兜底：识别失败绝不把 500「系统异常」抛给用户，
                // 而是按发票处理失败返回 OcrResult.fail，由前端提示手工补录。
                r = OcrResult.fail("上传成功但解析失败：" + t.getMessage());
            }
            result.put("attachType", "INVOICE");
            result.put("invoice", r);
        }
        return Result.success(result);
    }

    private static boolean isImageInvoice(String fileName) {
        if (fileName == null) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
        return IMAGE_EXTS.contains(ext);
    }
}