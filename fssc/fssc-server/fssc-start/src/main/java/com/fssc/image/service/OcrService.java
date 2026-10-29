// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.facade.InvoiceFacade;
import com.fssc.framework.facade.dto.InvoicePoolDTO;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.image.channel.OcrChannel;
import com.fssc.image.channel.OcrChannelRouter;
import com.fssc.image.channel.OcrLocalParsers;
import com.fssc.image.channel.OcrResult;
import com.fssc.image.entity.ImgBatch;
import com.fssc.image.entity.ImgException;
import com.fssc.image.entity.ImgFile;
import com.fssc.image.entity.OcrRecord;
import com.fssc.image.mapper.ImgBatchMapper;
import com.fssc.image.mapper.ImgExceptionMapper;
import com.fssc.image.mapper.ImgFileMapper;
import com.fssc.image.mapper.OcrRecordMapper;
import com.fssc.system.entity.SysFile;
import com.fssc.system.service.ConfigService;
import com.fssc.system.service.FileService;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * OCR 识别服务：批次识别（页面 NONE→PENDING→SUCCESS/NEED_REVIEW/FAILED），
 * 识别结果回填 ocr_text 并自动创建发票入池，全批都有结论时批次才置 CHECKED。
 * 乙：识别不出＝warning 级处置（页态 FAILED ＋ image_exception 转人工），
 * 不当成功返回、也不整批抛错回滚；（甲-1）：已识别但未生成单据的孤儿态合法，解析结果留在 ocr_record。
 * OCR 通道可插拔（image/channel/OcrChannel SPI：mock 演示 / http 真通道，按 fssc.ocr.channel 切换）。
 * 入池经 InvoiceFacade 跨域调用（服务化断点，主域侧仍复用 InvoiceService.create 全校验链：
 * 价税必填、全局查重预警、异常检测），
 * 并叠加 #15 票面自洽标记：不一致 → 页面 NEED_REVIEW、票不入池，转人工复核。
 *
 * 分库改造：类级路由 fssc_image 库（ocr_record/img_file/img_batch 所在库）；
 * recognizeBatch REQUIRES_NEW 独立事务绑定 image 连接，体内跨库调用
 * （sys_config 读取、sys_file、inv_invoice 入池均走 master）由各自方法级路由切换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FsscDataSource("image")
public class OcrService {

    private final ImgBatchMapper imgBatchMapper;
    private final ImgFileMapper imgFileMapper;
    private final OcrRecordMapper ocrRecordMapper;
    private final ImgExceptionMapper imgExceptionMapper;
    /** 跨域入池走 facade（服务化断点）：不再直连主域 InvoiceService 与 InvInvoice 实体 */
    private final InvoiceFacade invoiceFacade;
    /** 通道运行时选择：sys_config ocr.channel 优先，空回退 fssc.ocr.channel（默认 mock），改配置即生效 */
    private final OcrChannelRouter ocrChannelRouter;
    private final ObjectMapper objectMapper;
    /** 读取上传件字节（PDF/OFD 本地版式解析需从磁盘按 filePath 拼存储根读字节） */
    private final FileService fileService;
    /** 读取 sys_config：ocr.local_parse_enabled 本地版式解析总开关（异常时可关闭回退 OCR 通道）。
     * 分库改造：不再直连 SysConfigMapper（master 表），改经 ConfigService.getValue（方法级 master 路由）。 */
    private final ConfigService configService;

    /** 本地版式解析开关 config_key（1=开启，缺省开启；0=关闭后 PDF/OFD 也走可插拔通道） */
    private static final String CFG_LOCAL_PARSE = "ocr.local_parse_enabled";

    /** #15 OCR 票面金额容差 */
    private static final BigDecimal OCR_AMT_TOLERANCE = new BigDecimal("0.01");

    /** X-30，乙：真路之一＝手工录入（与影像页提示同一规则，一处办结） */
    private static final String MANUAL_ENTRY_HINT = "；或在「发票管理 › 发票补录」手工录入发票要素";

    /** X-30，乙：识别不出时真路只有这两条（换版式文件重传 / 手工录入要素），失败文案在此办结 */
    private static final String NO_INVOICE_HINT = "；可改传 XML/OFD 版式文件重新上传" + MANUAL_ENTRY_HINT;

    /** 演示通道不产出票面要素——它给的"确定性票面"是造出来的，不得计入已识别 */
    private static final String DEMO_CHANNEL_HINT = "当前 OCR 通道为演示通道，不产出票面要素（不以假票面冒充已识别）";

    /** 识别批次下全部页面，自动创建发票入池 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int recognizeBatch(Long batchId) {
        ImgBatch batch = imgBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "影像批次不存在: " + batchId);
        }
        List<ImgFile> pages = imgFileMapper.selectList(new LambdaQueryWrapper<ImgFile>()
                .eq(ImgFile::getBatchId, batchId).orderByAsc(ImgFile::getPageNo));
        if (pages.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "批次无页面可识别");
        }
        Long tenantId = TenantContext.getTenantId();
        String channelName = ocrChannelRouter.currentChannel();
        OcrChannel channel = ocrChannelRouter.current();
        boolean localParseEnabled = isLocalParseEnabled();
        int created = 0;
        // 需要人工处置的页（未识别／金额不自洽／识别成功但入池失败）——批次不再一律盖"已质检"
        int needManual = 0;
        for (ImgFile page : pages) {
            // NONE → PENDING
            page.setOcrStatus("PENDING");
            imgFileMapper.updateById(page);
            try {
                OcrResult result = recognizeOne(page, channelName, channel, localParseEnabled);
                if (!result.isOk()) {
                    // 乙：识别不出＝本页未识别，按 warning 处置不抛穿——页态 FAILED 让前端给出两条真路，
                    // 原因进异常池（转人工），批内其余页继续，已识别页的结果不再被回滚。
                    // ocr_text 有意不写：影像页的"识别失败"面板判据就是无 OCR 数据，塞失败 JSON 会把它盖掉。
                    needManual++;
                    page.setOcrStatus("FAILED");
                    page.setVerifyStatus("FAIL");
                    imgFileMapper.updateById(page);
                    // ②：说明列只放业务话术（网关 JSON/环境变量名/traceId 只进下方 log.warn）
                    recordException(batch, "OCR_NOT_RECOGNIZED", page.getPageNo(), String.format(
                            "第 %d 页未识别出正式发票票面%s（技术详情见运维日志）", page.getPageNo(), NO_INVOICE_HINT));
                    // 失败也落 ocr_record（recognize_status=FAILED），OCR 记录页才可复核"识别过、没识别出来"
                    saveOcrFailure(page, null, channelName, String.format(
                            "第 %d 页未识别出正式发票票面%s", page.getPageNo(), NO_INVOICE_HINT));
                    log.warn("页面 {} OCR 未识别（通道 {}）：{}", page.getId(), channelName, nvl(result.getErrorMsg()));
                    continue;
                }
                page.setOcrText(objectMapper.writeValueAsString(result));
                // #15 OCR 票面一致性校验：价税合计 = 不含税 + 税额（容差 0.01）；强制校验：
                //    关键要素（发票号/开票日期/购方名称/价税三项）任一缺失一律判不自洽。
                //    OCR 结果无明细行，故以"价税三项自洽"等价于"行合计 vs 封面合计"校验；
                //    超差或要素缺失说明识别有误 → 标记 NEED_REVIEW 进人工复核，不再自动置 PASS 直接入库。
                boolean consistent = checkAmountConsistent(result);
                // OCR 结果写入数据库（结构化票面字段持久化到 ocr_record，不再仅存 JSON blob）
                saveOcrRecord(page, null, result, channelName, consistent);
                if (result.getInvoiceNo() != null) {
                    if (consistent) {
                        // 自动入池：复用 create 全校验链（价税强校验、租户内硬查重、全局跨租户查重预警、异常检测）。
                        // 入池失败（如发票号撞 uk_tenant_inv 查重）只影响发票台账，不得回滚 ocr_record 识别记录——
                        // 否则重复上传同一票面时，写入数据库记录被连带吞掉，写入数据库语义失效。
                        try {
                            invoiceFacade.poolFromOcr(toPoolDto(page, result, tenantId));
                            created++;
                        } catch (Exception poolEx) {
                            needManual++;
                            log.warn("页面 {} OCR 票面识别成功但发票入池失败（不回滚识别记录）：{}",
                                    page.getId(), poolEx.getMessage());
                            // 异常不丢弃：入池失败必须落异常池，供人工复核/重试（image_exception 与 ocr_record
                            // 同在 image 库，同库同事务写入；这张"已识别未生成单据"的孤儿票由反写位区分——）
                            // ②：说明列业务话术化，底层报错只进上方 log.warn
                            recordException(batch, "OCR_POOL_FAILED", page.getPageNo(), String.format(
                                    "第 %d 页发票【%s】识别成功但暂未入发票池，可在发票池复核重试或联系管理员（技术详情见运维日志）",
                                    page.getPageNo(), nvl(result.getInvoiceNo())));
                        }
                    } else {
                        needManual++;
                        log.warn("页面 {} OCR 票面金额不自洽或关键要素缺失，发票不入池，转人工复核：价税合计={} 不含税={} 税额={}",
                                page.getId(), result.getAmount(), result.getNetAmount(), result.getTaxAmount());
                        recordException(batch, "OCR_AMOUNT_INCONSISTENT", page.getPageNo(), String.format(
                                "第 %d 页发票【%s】票面金额不自洽或关键要素缺失，未入池：价税合计=%s 不含税=%s 税额=%s",
                                page.getPageNo(), nvl(result.getInvoiceNo()), result.getAmount(),
                                result.getNetAmount(), result.getTaxAmount()));
                    }
                }
                page.setOcrStatus(consistent ? "SUCCESS" : "NEED_REVIEW");
                page.setVerifyStatus(consistent ? "PASS" : "FAIL");
                imgFileMapper.updateById(page);
            } catch (Exception ex) {
                log.warn("页面 {} OCR 识别失败: {}", page.getId(), ex.getMessage());
                needManual++;
                page.setOcrStatus("FAILED");
                page.setVerifyStatus("FAIL");
                imgFileMapper.updateById(page);
                // ②：异常原文只进上方 log.warn，说明列用业务话术
                recordException(batch, "OCR_NOT_RECOGNIZED", page.getPageNo(), String.format(
                        "第 %d 页识别异常，未识别出正式发票票面%s（技术详情见运维日志）", page.getPageNo(), NO_INVOICE_HINT));
                // 识别抛异常同样落 ocr_record 失败行（异常原文只进运维日志）
                saveOcrFailure(page, null, channelName, String.format(
                        "第 %d 页识别异常，未识别出正式发票票面%s（技术详情见运维日志）", page.getPageNo(), NO_INVOICE_HINT));
            }
        }
        // X-25，甲-1：改前"0 张即抛错"会连带回滚整批 REQUIRES_NEW 事务里刚落的 ocr_record 与页态 ⇒
        // 「已识别但未生成单据」这一孤儿态在库里根本留不下来，解析结果没有承载体。现裁准为合法孤儿态：
        // 识别结果留在 ocr_record，票入 inv_invoice 池，"是否已生成单据"由池表既有反写位（is_used/used_count＋
        // source_doc_type/source_doc_id，由 InvoiceService.recalcUsedCount 维护）区分，业务表零加列。
        if (created == 0) {
            // CHAOS-02 的"不得静默"这半条保留：0 张必留 warn 级结论，且批次不置已质检（不再把 0 当成功）
            log.warn("影像批次 {}（通道 {}）本次未生成任何发票：{} 页中 {} 页需人工处置，解析结果已保留",
                    batchId, channelName, pages.size(), needManual);
        }
        if (needManual == 0) {
            ImgBatch update = new ImgBatch();
            update.setId(batchId);
            update.setStatus("CHECKED");
            imgBatchMapper.updateById(update);
        } else {
            log.info("影像批次 {} 有 {} 页待人工处置，批次状态保持 {}（不置已质检）",
                    batchId, needManual, batch.getStatus());
        }
        return created;
    }

    /**
     * 业务附件直传图片发票的识别入口（费用报销 /expense/attach/upload 的图片分支等）。
     *
     * <p>报销单上传 .jpg/.png 发票时无影像批次/页面，此前 attachType 恒 OTHER、不做任何识别
     * 此前 IMG 域图片发票 OCR 为空。本方法调可插拔图像通道（paddle→本地 PaddleOCR
     * /invoice_ocr）结构化识别，并走与 {@link #recognizeBatch} 相同的写入数据库链：</p>
     * <ul>
     *  <li>ocr_record 结构化写入数据库（batch_id/img_file_id 为空，file_id=附件 sys_file.id）；</li>
     *  <li>票面金额自洽且有发票号 → 自动入池 inv_invoice（撞 uk_tenant_inv 查重等只 log 不阻断上传）；</li>
     * </ul>
     * 失败不抛穿：ok=false + errorMsg，由上传接口以 invoice.ok=false 提示手工补录。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public OcrResult recognizeImageBytes(byte[] image, String fileName, Long fileId) {
        String channelName = ocrChannelRouter.currentChannel();
        if ("mock".equals(channelName)) {
            // 失败文案必须把两条真路都给全（换版式文件 / 手工录入要素）
            return OcrResult.fail("图片发票需配置图像识别通道（当前为 mock 演示通道）；"
                    + "请在 系统管理→OCR通道配置 切换 PaddleOCR/HTTP 通道，或改传 PDF/OFD/数电票 XML"
                    + "；或在「发票管理 › 发票补录」手工录入发票要素");
        }
        OcrResult result = ocrChannelRouter.current().recognize(image, fileName);
        return recognizeAndRecord(result, fileId, channelName, "图片发票");
    }

    /**
     * 识别结果写入数据库链：ocr_record 结构化写入数据库 ＋ 票面自洽且有发票号则自动入池 inv_invoice。
     * 识别不成功时原样返回失败结果，不写库。
     *
     * <p>图片发票与「扫描版 PDF（本地解析渲染出的首页 PNG）」共用这一条写入数据库链 ⇒ 报销附件环节上
     * 两条 OCR 分支的写入数据库／入池规则合一，不再出现"图片识别会入池、扫描票识别了不入池"的第二套事实。
     *
     * <p><b>（"已识别"≠"已入池"⇒ 结论如实透出）</b>：改前入池抛错被 catch 吞成一条 {@code log.warn}，
     * 方法仍返回 {@code ok=true} ⇒ {@code ExpenseAttachController} 把"成功"交给前端、界面弹「已识别发票」，
     * 而 {@code inv_invoice} 里根本没有这张票（实测 18:41:47／18:45:15 两笔＝{@code ocr_record} 有票、
     * {@code inv_invoice} 恒 12 行）。现在入池结论一律写进 {@link OcrResult#setInPool}，
     * 未入池的真实原因写进 {@code errorMsg}；前端只读 {@code ok}／{@code inPool}／{@code errorMsg} 三格，
     * 不在前端重算金额自洽（判据两源会漂移）。
     *
     * <p><b>（去重环节）</b>：本环节是「上传＋识别」同一请求串行，而 {@code REQUIRES_NEW} 让客户端断开
     * 并不终止服务端写入（实测 18:33:27 浏览器已关，18:33:33 仍落 ocr_record、18:33:37 仍入池＝零响应幽灵票）。
     * 现按两把键收敛：①同一 {@code fileId} 已有 {@code ocr_record} ⇒ 复用、不再叠记；
     * ②入池前按「发票代码＋号码」查池，命中 ⇒ 跳过 {@code poolFromOcr} 并按"已在池"如实透出
     * （改前这条会撞 {@code assertNotDuplicateEntry} 抛"已存在于发票池"，被本方法当入池失败记成坏票）。
     *
     * @param what 日志主体标签（图片发票／扫描版PDF／版式文件），仅影响记录文案
     */
    private OcrResult recognizeAndRecord(OcrResult result, Long fileId, String channelName, String what) {
        if (!result.isOk()) {
            result.setInPool(null);
            // 附件直传环节识别失败此前只回一个 fail 结果、本表零行 ⇒ OCR 记录页看不出"识别过没有"。
            // 现失败也落一行（recognize_status=FAILED）；同一附件已有任何记录时不叠记（与成功环节同一把唯一键），
            // 技术详情仍只进运维日志。
            if (!hasOcrRecordForFile(fileId, false)) {
                saveOcrFailure(null, fileId, channelName,
                        what + "识别失败：未识别出正式发票票面" + NO_INVOICE_HINT + "（技术详情见运维日志）");
            }
            log.warn("{} fileId={} 识别失败：{}", what, fileId, nvl(result.getErrorMsg()));
            return result;
        }
        boolean consistent = checkAmountConsistent(result);
        if (hasOcrRecordForFile(fileId, true)) {
            log.info("{} fileId={} 已有 OCR 识别记录，按 fileId 幂等复用（不重复写 ocr_record）", what, fileId);
        } else {
            saveOcrRecord(null, fileId, result, channelName, consistent);
        }
        String no = result.getInvoiceNo();
        if (no == null || no.isBlank()) {
            result.setInPool(Boolean.FALSE);
            result.setErrorMsg("未取到发票号码，票面未入发票池；可核对票面后重传，"
                    + "或在「发票管理 › 发票补录」手工录入要素");
            log.warn("{} fileId={} 识别成功但未取到发票号码，不入池", what, fileId);
            return result;
        }
        if (!consistent) {
            result.setInPool(Boolean.FALSE);
            result.setErrorMsg(String.format(
                    "票面金额不自洽或关键要素缺失（价税合计=%s，不含税=%s，税额=%s），未入发票池；"
                            + "可核对后重传，或在「发票管理 › 发票补录」手工录入要素",
                    result.getAmount(), result.getNetAmount(), result.getTaxAmount()));
            log.warn("{} {} fileId={} 票面金额不自洽或关键要素缺失，不入池", what, no, fileId);
            return result;
        }
        InvoicePoolDTO dto = toPoolDto(null, result, TenantContext.getTenantId());
        if (invoiceFacade.existsInPool(dto)) {
            result.setInPool(Boolean.TRUE);
            log.info("{} {} fileId={} 已在发票池，按票面号幂等跳过重复入池", what, no, fileId);
            return result;
        }
        try {
            invoiceFacade.poolFromOcr(dto);
            result.setInPool(Boolean.TRUE);
        } catch (Exception poolEx) {
            result.setInPool(Boolean.FALSE);
            result.setErrorMsg("发票入池失败："
                    + (poolEx.getMessage() == null || poolEx.getMessage().isBlank()
                        ? poolEx.getClass().getSimpleName() : poolEx.getMessage())
                    + "；可核对票面后重传，或在「发票管理 › 发票补录」手工录入要素");
            log.warn("{} {} 识别成功但未入池（不阻断上传，结论已按 inPool=false 透出）: {}",
                    what, no, poolEx.getMessage());
        }
        return result;
    }

    /**
     * 去重环节①：同一附件（{@code ocr_record.file_id}）是否已有识别记录。
     *
     * <p>租户维度由 {@code FsscTenantLineHandler} 自动追加，此处不要手工拼 tenant_id。
     * 问不出结果时（查询异常）一律按"没有"处置＝退回改前"必写一条"的行为，
     * 不得让去重判定本身把上传打断（{@code saveOcrRecord} 自身也是 catch-only 的记录环节）。
     *
     * @param onlySuccess true＝只数成功行（{@code recognize_status<>'FAILED'}），供<b>成功环节</b>用：
     *                    之后失败也落行，若把 FAILED 行当去重命中，"先失败后重传成功"那次的
     *                    票面就永远不写入数据库（识别记录停在失败态）；
     *                    false＝任何行都算，供<b>失败环节</b>用：同一附件反复重传失败不叠记多条。
     *                    （recognize_status 由 建为 NOT NULL DEFAULT 'SUCCESS'，无 NULL 行）
     */
    private boolean hasOcrRecordForFile(Long fileId, boolean onlySuccess) {
        if (fileId == null) {
            return false;
        }
        try {
            Long hits = ocrRecordMapper.selectCount(new LambdaQueryWrapper<OcrRecord>()
                    .eq(OcrRecord::getFileId, fileId)
                    .ne(onlySuccess, OcrRecord::getRecognizeStatus, "FAILED"));
            return hits != null && hits > 0;
        } catch (Exception e) {
            log.warn("OCR 幂等判定失败 fileId={}: {}（按无记录处置，仍写一条 ocr_record）", fileId, e.getMessage());
            return false;
        }
    }

    /**
     * 发票文件字节 → 结构化票面的**统一分派**（报销附件环节与影像批次环节同一优先级次序）。
     * <ol>
     *  <li>图片发票（{@link #IMAGE_EXTS}）→ 交可插拔图像 OCR 通道，维持 {@link #recognizeImageBytes} 原规则；</li>
     *  <li>PDF／OFD／数电票 XML 且 {@code ocr.local_parse_enabled} 开启（缺省开）⇒ <b>先本地版式解析，全程不发 OCR 请求</b>；</li>
     *  <li><b>扫描版 PDF</b>（无文本层）：解析器已渲染首页 PNG ⇒ <b>转图像 OCR 通道</b>真识别，
     *      并与图片发票走同一条写入数据库链（ocr_record ＋ 入池）。
     *      改前这步回退只存在于影像批次环节 {@code recognizeOne}，报销附件环节把已经渲染好的 PNG 直接丢了
     *      （＝断环节：同一张扫描票两个入口结果劈裂，用户被引导去"电脑端补录"，而电脑端走同一个环节）；</li>
     *  <li>总开关关闭时版式文件也交可插拔通道（带真实字节，不要像 {@code recognizeOne} 那样送 null）；</li>
     *  <li>其余解析失败（含数电票 XML 机器文件）⇒ 返回失败结果，不降级假票面。</li>
     * </ol>
     *
     * @param fileName 原始文件名，仅用于扩展名判定；转图像通道时按 {@code fileId.png} 重新命名——
     *                 服务端（YonOcr {@code /invoice_ocr}）按扩展名分流，拿 {@code .pdf} 名字送 PNG 字节会被再当版式文件解。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public OcrResult recognizeInvoiceBytes(byte[] bytes, String fileName, Long fileId) {
        String channelName = ocrChannelRouter.currentChannel();
        if (isImageInvoice(fileName)) {
            return recognizeImageBytes(bytes, fileName, fileId);
        }
        if (!OcrLocalParsers.isLocalFormat(fileName)) {
            return OcrResult.fail("非发票文件，未做票面识别: " + fileName);
        }
        if (!isLocalParseEnabled()) {
            log.info("本地版式解析总开关已关闭，版式文件 {} 交可插拔OCR通道({})识别，{} 字节",
                    fileName, channelName, bytes == null ? 0 : bytes.length);
            return recognizeAndRecord(ocrChannelRouter.current().recognize(bytes, fileName),
                    fileId, channelName, "版式文件");
        }
        OcrResult local;
        try {
            local = OcrLocalParsers.parse(bytes, fileName);
        } catch (Throwable t) {
            // 用 Throwable 默认处理：解析异常不得把 500「系统异常」抛穿给用户（与上传接口原规则一致）
            return OcrResult.fail("上传成功但解析失败：" + t.getMessage());
        }
        if (local.isOk()) {
            log.info("本地版式解析成功 fileId={} fileName={} invoiceNo={}", fileId, fileName, local.getInvoiceNo());
            return local;
        }
        byte[] png = local.getRenderedImageBytes();
        if (local.isScanned() && png != null && png.length > 0) {
            // 扫描版 PDF：解析器算好了回退物料（首页 PNG），这里才是"执行那条指令"的地方
            if ("mock".equals(channelName)) {
                log.warn("扫描版PDF fileId={} 渲染出首页但当前为 mock 通道，按未识别处置（不取假票面）", fileId);
                return OcrResult.fail("扫描版 PDF 需图像识别通道后才能取要素，" + DEMO_CHANNEL_HINT + NO_INVOICE_HINT);
            }
            log.info("扫描版PDF(报销附件) fileId={} 已渲染首页PNG({}B)，转图像OCR通道({})",
                    fileId, png.length, channelName);
            return recognizeAndRecord(ocrChannelRouter.current().recognize(png, fileId + ".png"),
                    fileId, channelName, "扫描版PDF");
        }
        if (OcrLocalParsers.EXT_XML.equals(OcrLocalParsers.extOf(fileName))) {
            log.warn("数电票 XML 本地解析失败 fileId={} fileName={}（{}）：机器文件不降级假票面",
                    fileId, fileName, local.getErrorMsg());
        } else {
            log.warn("本地版式解析失败 fileId={} fileName={}（{}）：按未识别处置，不降级为假票面",
                    fileId, fileName, local.getErrorMsg());
        }
        return local;
    }

    /**
     * 按附件（sys_file）ID 触发 OCR：附件挂在影像批次的页上，先定位其批次再走整批识别。
     *
     * <p>前端附件列表持有的是 fileId，而识别入口原先是 batchId（{@code POST /image/batch/{id}/ocr}），
     * 两者语义不同——直接拿 fileId 当 batchId 调用必然 "影像批次不存在" 而静默失败。
     * 这里补齐 fileId → batchId 的解析，让"对某个附件单独识别"成为受支持的调用方式。
     *
     * @return 本次新入池的发票数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int recognizeByFile(Long fileId) {
        if (fileId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "附件 ID 不能为空");
        }
        List<ImgFile> pages = imgFileMapper.selectList(new LambdaQueryWrapper<ImgFile>()
                .eq(ImgFile::getFileId, fileId).orderByAsc(ImgFile::getPageNo));
        if (pages.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "附件 " + fileId + " 未纳入影像批次，无法识别；请确认该附件已完成影像采集");
        }
        Long batchId = pages.get(0).getBatchId();
        if (batchId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "附件 " + fileId + " 未关联影像批次，无法识别");
        }
        // 同类自调用不经过代理，事务传播沿用本方法已开启的 REQUIRES_NEW（整批识别在同一 image 事务内完成）
        return recognizeBatch(batchId);
    }

    /**
     * 单页识别分派：PDF/OFD/数电票 XML 走本地版式解析（读磁盘字节），图片维持可插拔 OCR 通道。
     * <ul>
     *  <li>本地解析成功 → 直接返回；</li>
     *  <li>扫描版 PDF（无文本层）：渲染首页 PNG 后交图像通道真识别；当前是 mock 演示通道时按未识别返回失败
     *      不再拿它的确定性假票面当"已识别"；</li>
     *  <li>PDF/OFD 本地解析失败：一律返回失败结果（页级 FAILED ＋ 异常池转人工），<b>不再降级 mock</b>
     *      假票面入池即污染台账；</li>
     *  <li>数电票 XML 解析失败：机器文件，任何通道都不降级 mock，直接返回失败结果置页级 FAILED；</li>
     *  <li>读文件异常（记录/磁盘缺失）→ 真通道回退可插拔通道；mock 回退即造假 ⇒ 按未识别返回失败。</li>
     * </ul>
     */
    private OcrResult recognizeOne(ImgFile page, String channelName, OcrChannel channel, boolean localParseEnabled) {
        Long fileId = page.getFileId();
        if (fileId != null) {
            try {
                SysFile sf = fileService.getById(fileId);
                String fileName = sf.getFileName();
                // 图片发票：本地版式解析只覆盖 PDF/OFD/数电票 XML，图片必须走图像 OCR 通道。
                // mock 是演示通道、不具备图像识别能力 —— 此时明确失败并给出可操作提示，
                // 而不是回落到"确定性假票面"（假发票入池会污染台账，与数电票 XML 不降级 mock 同一原则）。
                if (isImageInvoice(fileName) && "mock".equals(channelName)) {
                    log.warn("图片发票 {} 在 mock 通道下无法识别，返回明确提示（请在 系统配置→OCR 配置 启用图像识别通道）", fileName);
                    return OcrResult.fail("图片发票需配置图像识别通道（当前为 mock 演示通道，不支持图像识别）；"
                            + "PDF / OFD / 数电票 XML 可直接解析" + MANUAL_ENTRY_HINT);
                }
                // 图片发票：携带影像字节交可插拔通道（paddle=http 本地 PaddleOCR / http=通用真通道）真识别。
                // 此前图片恒以 image=null 调通道，真通道拿到的是空字节——图片发票 OCR 恒不可用。
                if (isImageInvoice(fileName)) {
                    byte[] bytes = fileService.readBytes(sf);
                    log.info("图片发票 {} 交图像OCR通道({})识别，{} 字节", fileName, channelName, bytes.length);
                    return channel.recognize(bytes, fileName);
                }
                if (localParseEnabled && OcrLocalParsers.isLocalFormat(fileName)) {
                    byte[] bytes = fileService.readBytes(sf);
                    OcrResult local = OcrLocalParsers.parse(bytes, fileName);
                    if (local.isOk()) {
                        log.info("本地版式解析成功 fileId={} fileName={} invoiceNo={}",
                                fileId, fileName, local.getInvoiceNo());
                        return local;
                    }
                    // 扫描版 PDF：解析器已渲染首页 PNG → 交图像 OCR 通道真识别（不再当解析失败降级假票面）
                    if (local.isScanned() && local.getRenderedImageBytes() != null
                            && local.getRenderedImageBytes().length > 0) {
                        // 渲染图只有图像通道认得了，mock 是演示通道、拿到的根本不是票面 ⇒
                        // 不再把它的确定性结果当"已识别"（假票面一旦入池即污染台账）。
                        if ("mock".equals(channelName)) {
                            log.warn("扫描版PDF fileId={} 渲染出首页但当前为 mock 通道，按未识别处置（不取假票面）", fileId);
                            return OcrResult.fail("扫描版 PDF 需图像识别通道后才能取要素，" + DEMO_CHANNEL_HINT);
                        }
                        log.info("扫描版PDF fileId={} 已渲染首页PNG({}B)，转图像OCR通道({})",
                                fileId, local.getRenderedImageBytes().length, channelName);
                        return channel.recognize(local.getRenderedImageBytes(), String.valueOf(fileId));
                    }
                    // 数电票 XML 是机器文件（结构化），解析失败必须显性暴露：直接返回失败结果（页级 FAILED），
                    // 不降级 mock——用假票面掩盖真实解析错误会污染台账。
                    if (OcrLocalParsers.EXT_XML.equals(OcrLocalParsers.extOf(fileName))) {
                        log.warn("数电票 XML 本地解析失败 fileId={} fileName={}（{}）：机器文件不降级 mock，页面置 FAILED",
                                fileId, fileName, local.getErrorMsg());
                        return local;
                    }
                    // 乙：改前此处降级 mock 拿一张"确定性假票面"当识别成功（保证演示不断流）⇒ 撤销，
                    // 版式解析失败的 PDF/OFD 一律按未识别处置（页态 FAILED＋异常池转人工），出路只有换文件/手工录入。
                    log.warn("本地版式解析失败 fileId={} fileName={}（{}）：按未识别处置，不降级为假票面",
                            fileId, fileName, local.getErrorMsg());
                    return local;
                }
            } catch (Exception e) {
                log.warn("读取影像文件失败 fileId={}，回退可插拔 OCR 通道: {}", fileId, e.getMessage());
            }
        }
        // 走到这里意味着没有可用字节（无影像记录／读盘失败／非版式格式），
        // mock 通道此时返回的是凭空造的票面 ⇒ 同样不得计入已识别。
        if ("mock".equals(channelName)) {
            return OcrResult.fail("未取得可识别的票面字节，" + DEMO_CHANNEL_HINT);
        }
        return channel.recognize(null, String.valueOf(fileId));
    }

    /** 本地版式解析总开关：sys_config ocr.local_parse_enabled，缺省/读取异常一律开启（1=开，0=关）。
     * 分库改造：sys_config 在 master 库，经 ConfigService.getValue 读取（NOT_SUPPORTED 挂起 image 事务）。 */
    private boolean isLocalParseEnabled() {
        try {
            String v = configService.getValue(CFG_LOCAL_PARSE);
            return v == null || !"0".equals(v.trim());
        } catch (Exception e) {
            log.warn("读取 {} 失败，默认开启本地版式解析", CFG_LOCAL_PARSE, e);
            return true;
        }
    }

    /** 图片类发票扩展名：本地版式解析不支持，只能交图像 OCR 通道 */
    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "bmp", "webp", "tif", "tiff");

    private boolean isImageInvoice(String fileName) {
        return fileName != null && IMAGE_EXTS.contains(OcrLocalParsers.extOf(fileName));
    }

    /** OCR 结果 → 发票实体（价税自洽校验通过后入池；verifyStatus 由 #15 标记，一致即 PASS） */
    /**
     * 识别结果 → 入池 DTO（跨域传输对象）。
     * 票据状态（NORMAL/PASS）由主域在 InvoiceFacadeImpl 中补齐，影像域不持有主域状态语义。
     */
    /**
     * 落影像异常池（image_exception），让"没能入池的票"可查、可重试、可人工处理。
     *
     * <p>设计要点：
     * <ul>
     *  <li><b>异常不丢弃</b>：OCR 入池是可补偿的业务动作，失败不等于作废，必须留下痕迹——
     *      这是票据自动化链路上的通行约定（票据池 + 异常队列 + 人工默认处理），
     *      它们同样<b>不使用分布式事务</b>来保证这一步。</li>
     *  <li><b>同库写入</b>：image_exception 与 ocr_record 同在 image 库，随 recognizeBatch
     *      的 image 事务提交，不引入跨库事务。</li>
     *  <li><b>不阻断主流程</b>：落池自身失败只记 error，不得把已识别成功的页面改判为 FAILED。</li>
     * </ul>
     */
    private void recordException(ImgBatch batch, String type, Integer pageNo, String desc) {
        try {
            // ①：按（批次＋类型＋页码）去重去重——同一次失败重复执行不再写两行；
            // 页码编码在说明列前缀「第 N 页」（image_exception 无页码列，零加列规则）
            if (pageNo != null) {
                Long dup = imgExceptionMapper.selectCount(new LambdaQueryWrapper<ImgException>()
                        .eq(ImgException::getImgBatchId, batch.getId())
                        .eq(ImgException::getExceptionType, type)
                        .eq(ImgException::getStatus, "OPEN")
                        .likeRight(ImgException::getExceptionDesc, "第 " + pageNo + " 页"));
                if (dup != null && dup > 0) {
                    log.info("影像异常已存在，幂等跳过: batchId={} type={} pageNo={}", batch.getId(), type, pageNo);
                    return;
                }
            }
            ImgException e = new ImgException();
            e.setImgBatchId(batch.getId());
            e.setBatchNo(batch.getBatchNo());
            e.setBizNo(batch.getBizNo());
            e.setExceptionType(type);
            e.setExceptionDesc(desc == null ? type : (desc.length() > 500 ? desc.substring(0, 500) : desc));
            e.setStatus("OPEN");
            imgExceptionMapper.insert(e);
        } catch (Exception ex) {
            log.error("入池异常落 image_exception 失败 batchId={} type={}: {}",
                    batch.getId(), type, ex.getMessage());
        }
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private InvoicePoolDTO toPoolDto(ImgFile page, OcrResult r, Long tenantId) {
        InvoicePoolDTO dto = new InvoicePoolDTO();
        // 缺陷修复（H-0）：携带影像页ID，主域入池时回写 inv_invoice.img_file_id
        dto.setImgFileId(page == null ? null : page.getId());
        dto.setTenantId(tenantId);
        dto.setInvoiceNo(r.getInvoiceNo());
        dto.setInvoiceCode(r.getInvoiceCode());
        dto.setInvoiceType(r.getInvoiceType());
        dto.setSellerName(r.getSellerName());
        dto.setSellerTaxNo(r.getSellerTaxNo());
        dto.setBuyerName(r.getBuyerName());
        dto.setBuyerTaxNo(r.getBuyerTaxNo());
        dto.setInvoiceDate(parseDate(r.getInvoiceDate()));
        dto.setNetAmount(r.getNetAmount());
        dto.setTaxAmount(r.getTaxAmount());
        dto.setAmount(r.getAmount());
        return dto;
    }

    /** OCR 识别结果结构化写入数据库到 ocr_record 表（batch/imgFile 可空——业务附件直传场景无影像批次） */
    private void saveOcrRecord(ImgFile page, Long fileId, OcrResult result, String channelName, boolean consistent) {
        try {
            OcrRecord record = new OcrRecord();
            record.setBatchId(page == null ? null : page.getBatchId());
            record.setImgFileId(page == null ? null : page.getId());
            record.setFileId(page == null ? fileId : (page.getFileId() != null ? page.getFileId() : fileId));
            record.setInvoiceCode(result.getInvoiceCode());
            record.setInvoiceNo(result.getInvoiceNo());
            record.setInvoiceType(result.getInvoiceType());
            record.setInvoiceDate(result.getInvoiceDate());
            record.setNetAmount(result.getNetAmount());
            record.setTaxAmount(result.getTaxAmount());
            record.setAmount(result.getAmount());
            record.setSellerName(result.getSellerName());
            record.setSellerTaxNo(result.getSellerTaxNo());
            record.setBuyerName(result.getBuyerName());
            record.setBuyerTaxNo(result.getBuyerTaxNo());
            record.setRawText(result.getRawText());
            record.setChannelName(channelName);
            record.setConsistent(consistent ? 1 : 0);
            // 本表自此同时承载成功与失败两类行，成功环节显式落 SUCCESS（列缺省值也是 SUCCESS）
            record.setRecognizeStatus("SUCCESS");
            // OcrRecord 为独立实体（非 TenantAuditBase），MetaObjectHandler 不自动填时间列，须显式赋值
            record.setCreatedAt(LocalDateTime.now());
            ocrRecordMapper.insert(record);
        } catch (Exception e) {
            // 附件直传路径 page 恒为 null，改前此处取 page.getId() 必抛 NPE，
            // 把"写入数据库失败记录"升级成"整个上传失败"——解析结果反而彻底没了。
            log.warn("OCR 结果落库失败 imgFileId={} fileId={}: {}",
                    page == null ? null : page.getId(), fileId, e.getMessage());
        }
    }

    /**
     * 识别**失败**也落一行 ocr_record（recognize_status=FAILED + fail_reason），
     * 使「OCR 识别记录」页对失败同样可复核——此前失败只写 log 与异常池，本表恒 0 行
     * （2026-10-24 H7 实测 img_batch=1／img_file=1(ocr_status=FAILED)／ocr_record=0）。
     * <p>记录环节自身 catch-only：列尚未建（未执行）或库不通时只落一条 warn，
     * 不得把"失败记录"升级成"识别流程再抛一次"（与 {@link #saveOcrRecord} 同一处置规则）。
     * <p>{@code img_file.ocr_text} 仍**不写**失败 JSON：影像页「识别失败」面板的判据就是无 OCR 数据
     * 既有裁定，本环节不动它。
     * <p>fail_reason 只写业务话术，网关 JSON／环境变量名／traceId 等技术详情仍只进运维日志（②）。
     */
    private void saveOcrFailure(ImgFile page, Long fileId, String channelName, String reason) {
        try {
            OcrRecord record = new OcrRecord();
            record.setBatchId(page == null ? null : page.getBatchId());
            record.setImgFileId(page == null ? null : page.getId());
            record.setFileId(page == null ? fileId : (page.getFileId() != null ? page.getFileId() : fileId));
            record.setChannelName(channelName);
            record.setConsistent(0);
            record.setRecognizeStatus("FAILED");
            record.setFailReason(reason == null ? null : (reason.length() > 500 ? reason.substring(0, 500) : reason));
            record.setCreatedAt(LocalDateTime.now());
            ocrRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("OCR 失败留痕未落库 imgFileId={} fileId={}: {}",
                    page == null ? null : page.getId(), fileId, e.getMessage());
        }
    }

    /**
     * #15 票面金额自洽校验：|（不含税 + 税额）− 价税合计| ≤ 0.01。
     * 强制校验：关键要素（发票号码/开票日期/购方名称/不含税/税额/价税合计）任一缺失即不得判「通过」，
     * 防止字段错位或残缺的记录（如 invoice_date 存成发票号、buyer_name 存成单字符、价税为 NULL）
     * 被 consistent=1 放行入池；改前此分支"字段不全返回 true"，正是错乱数据仍显示「通过」的放行口。
     */
    private boolean checkAmountConsistent(OcrResult r) {
        if (r == null) {
            return false;
        }
        if (isBlank(r.getInvoiceNo()) || isBlank(r.getInvoiceDate()) || isBlank(r.getBuyerName())
                || r.getNetAmount() == null || r.getTaxAmount() == null || r.getAmount() == null) {
            return false;
        }
        BigDecimal sum = r.getNetAmount().add(r.getTaxAmount());
        return sum.subtract(r.getAmount()).abs().compareTo(OCR_AMT_TOLERANCE) <= 0;
    }

    /** 强制校验配套：票面关键要素的缺失面，null 与空串同判 */
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 通道约定日期格式 yyyy-MM-dd；异常格式返回 null（不阻断入池） */
    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
