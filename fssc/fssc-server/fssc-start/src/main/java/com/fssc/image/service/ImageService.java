// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.facade.event.BillImageEvent;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.image.entity.ImgBatch;
import com.fssc.image.entity.ImgFile;
import com.fssc.image.mapper.ImgBatchMapper;
import com.fssc.image.mapper.ImgFileMapper;
import com.fssc.system.entity.SysFile;
import com.fssc.system.service.FileService;
import com.fssc.system.service.NumberRuleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 影像服务：批次上传（批次+页面）→ 发布影像状态变更事件（BillImageEvent）。
 * 批次上传完成即标记单据影像 COMPLETE；OCR 能力尚未接入。
 *
 * 断点1 改造：移除 ExpBillMapper 依赖，改为通过 ApplicationEventPublisher
 * 发布 BillImageEvent，由核心域 BillImageEventListener 监听回写 exp_bill.image_status。
 *
 * 分库改造：类级路由 fssc_image 库（img_batch/img_file 所在库）；
 * 写方法 REQUIRES_NEW 独立事务绑定 image 连接，体内跨库调用
 * （FileService/NumberRuleService 走 master）由各自方法级路由切换。
 */
@Service
@Slf4j
@RequiredArgsConstructor
@FsscDataSource("image")
public class ImageService {

    private final ImgBatchMapper imgBatchMapper;
    private final ImgFileMapper imgFileMapper;
    private final FileService fileService;
    private final NumberRuleService numberRuleService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * MultipartFile 影像上传（前端标准链路）：
     * 每个文件经 FileService 落盘（分层逻辑 Key + 存储区域 zone）→ 建批次 + 页记录。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long uploadBatchMulti(MultipartFile[] files, String bizType, Long bizId, String bizNo, String zone) {
        if (files == null || files.length == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未选择影像文件");
        }
        Long tenantId = TenantContext.getTenantId();
        ImgBatch batch = new ImgBatch();
        batch.setTenantId(tenantId);
        batch.setBatchNo(nextBatchNo());
        batch.setScanUserId(UserContext.getRequired().getUserId());
        batch.setSource("PC");
        batch.setDocCount(files.length);
        batch.setBizType(bizType);
        batch.setBizId(bizId);
        batch.setBizNo(bizNo);
        batch.setStatus("UPLOADED");
        batch.setSealStatus("UNSEALED");
        batch.setReviewStatus("PENDING");
        batch.setCreateTime(LocalDateTime.now());
        imgBatchMapper.insert(batch);

        int pageNo = 1;
        for (MultipartFile file : files) {
            SysFile saved = fileService.upload(file, bizType == null ? "IMAGE" : bizType, bizId, zone);
            ImgFile f = new ImgFile();
            f.setTenantId(tenantId);
            f.setBatchId(batch.getId());
            f.setFileId(saved.getId());
            f.setPageType("NORMAL");
            f.setPageNo(pageNo++);
            f.setOcrStatus("NONE");
            f.setVerifyStatus("NONE");
            f.setBizType(bizType);
            f.setBizId(bizId);
            f.setCreateTime(LocalDateTime.now());
            imgFileMapper.insert(f);
        }
        publishBillImageEvent(bizType, bizId, "COMPLETE");
        return batch.getId();
    }

    /** 创建影像批次并登记页面，发布影像状态变更事件 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long uploadBatch(String bizType, Long bizId, String bizNo, List<PageReq> pages) {
        Long tenantId = TenantContext.getTenantId();
        ImgBatch batch = new ImgBatch();
        batch.setTenantId(tenantId);
        batch.setBatchNo(nextBatchNo());
        batch.setScanUserId(UserContext.getRequired().getUserId());
        batch.setSource("PC");
        batch.setDocCount(pages == null ? 0 : pages.size());
        batch.setBizType(bizType);
        batch.setBizId(bizId);
        batch.setBizNo(bizNo);
        batch.setStatus("UPLOADED");
        batch.setSealStatus("UNSEALED");
        batch.setReviewStatus("PENDING");
        batch.setCreateTime(LocalDateTime.now());
        imgBatchMapper.insert(batch);

        if (pages != null) {
            for (PageReq page : pages) {
                ImgFile f = new ImgFile();
                f.setTenantId(tenantId);
                f.setBatchId(batch.getId());
                f.setFileId(page.getFileId());
                f.setPageType(page.getPageType() == null ? "NORMAL" : page.getPageType());
                f.setPageNo(page.getPageNo() == null ? 1 : page.getPageNo());
                f.setOcrStatus("NONE");
                f.setVerifyStatus("NONE");
                f.setBizType(bizType);
                f.setBizId(bizId);
                f.setCreateTime(LocalDateTime.now());
                imgFileMapper.insert(f);
            }
        }
        publishBillImageEvent(bizType, bizId, "COMPLETE");
        return batch.getId();
    }

    /**
     * 发布影像状态变更事件（断点1）。
     * 替代原 writeBackBillImage() 直接操作 exp_billMapper.updateById()。
     * 核心域 BillImageEventListener 监听后回写 exp_bill.image_status。
     */
    private void publishBillImageEvent(String bizType, Long bizId, String imageStatus) {
        // 缺陷修复（H-0）：原仅 bizType=EXP_CLAIM 发布事件，报销/付款/申请等单据
        // （CLAIM/TRAVEL/TRAIN/PREPAY/SETTLE/LOAN/REPAY/PAY/APPLY...）影像上传后
        // exp_bill.image_status 恒 NONE。现对所有 exp_bill 域单据类型发布（去重：监听器仍按 billId 回写）。
        if (bizId == null || !isExpBillBizType(bizType)) {
            return;
        }
        eventPublisher.publishEvent(new BillImageEvent(bizId, imageStatus, bizType));
    }

    /** 该 bizType 的业务单据是否落在 exp_bill 表（与 WfTaskController.EXP_BILL_TYPES 同一规则，另含 EXP_CLAIM 别名） */
    public static boolean isExpBillBizType(String bizType) {
        if (bizType == null) {
            return false;
        }
        return switch (bizType) {
            case "EXP_CLAIM", "CLAIM", "CLAIM_TRAVEL", "LOAN", "REPAY", "PAY",
                 "TRAVEL", "TRAIN", "PREPAY", "SETTLE", "APPLY", "EXP_APPLY",
                 "EXP_LOAN", "EXP_REPAY", "EXP_PAY" -> true;
            default -> false;
        };
    }

    public List<ImgBatch> batches(String bizType, Long bizId) {
        return imgBatchMapper.selectList(new LambdaQueryWrapper<ImgBatch>()
                .eq(bizType != null, ImgBatch::getBizType, bizType)
                .eq(bizId != null, ImgBatch::getBizId, bizId)
                .orderByDesc(ImgBatch::getId));
    }

    /** 影像任务分页：业务类型/单号/封单状态/任务状态/来源 筛选 */
    public PageResult<ImgBatch> taskPage(long pageNum, long pageSize, String bizType, String keyword,
                                         String sealStatus, String status) {
        LambdaQueryWrapper<ImgBatch> w = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) w.eq(ImgBatch::getBizType, bizType);
        if (sealStatus != null && !sealStatus.isBlank()) w.eq(ImgBatch::getSealStatus, sealStatus);
        if (status != null && !status.isBlank()) w.eq(ImgBatch::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(ImgBatch::getBatchNo, keyword)
                    .or().like(ImgBatch::getBizNo, keyword));
        }
        w.orderByDesc(ImgBatch::getId);
        Page<ImgBatch> p =
                imgBatchMapper.selectPage(Page.of(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 封单/解锁（封单状态更新） */
    public void seal(Long batchId, boolean sealed) {
        ImgBatch batch = imgBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "影像任务不存在: " + batchId);
        }
        ImgBatch update = new ImgBatch();
        update.setId(batchId);
        update.setSealStatus(sealed ? "SEALED" : "UNSEALED");
        imgBatchMapper.updateById(update);
    }

    /** 调阅审核合法最终状态/回退值（与影像任务页前端、replaceScan 回写同一规则） */
    private static final List<String> REVIEW_STATUS_ALLOWED = List.of("PENDING", "OK", "REJECTED");

    /**
     * 调阅审核状态更新（审核后回写）。
     * 三条硬校验：
     * ① 枚举白名单校验——任意字符串直写 review_status 会静默产生坏数据；
     * ② 存在性校验——批次不存在时不再"静默成功"；
     * ③ 状态机——OK/REJECTED 只允许从「待审核」判定，已判定批次不可重复判定
     *    （需更正请走影像替扫/重采集，replaceScan 会回写 PENDING 重新进入待审核）；
     *    PENDING 回退仅内部链路使用，回退不产生最终状态语义，放行；
     * ④ 记录——判定时记录审核人/审核时间（checkUserId/checkTime），
     *    操作层另有 @OperLog 影像任务调阅审核回写日志。
     */
    public void setReviewStatus(Long batchId, String reviewStatus) {
        if (reviewStatus == null || !REVIEW_STATUS_ALLOWED.contains(reviewStatus)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "审核状态取值非法：" + reviewStatus + "，仅允许 PENDING/OK/REJECTED");
        }
        ImgBatch batch = imgBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "影像任务不存在: " + batchId);
        }
        String current = batch.getReviewStatus() == null || batch.getReviewStatus().isBlank()
                ? "PENDING" : batch.getReviewStatus();
        if (("OK".equals(reviewStatus) || "REJECTED".equals(reviewStatus)) && !"PENDING".equals(current)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该影像任务已判定为「" + ("OK".equals(current) ? "已审核" : "已拒绝") + "」，不可重复判定；"
                            + "如需更正请执行影像替扫/重采集，回待审核后重新判定");
        }
        ImgBatch update = new ImgBatch();
        update.setId(batchId);
        update.setReviewStatus(reviewStatus);
        if (!"PENDING".equals(reviewStatus)) {
            update.setCheckUserId(UserContext.getRequired().getUserId());
            update.setCheckTime(LocalDateTime.now());
        }
        imgBatchMapper.updateById(update);
        log.info("调阅审核回写: batchId={} {}→{} operator={}", batchId, current, reviewStatus,
                "PENDING".equals(reviewStatus) ? "internal" : String.valueOf(update.getCheckUserId()));
    }

    public List<ImgFile> pages(Long batchId) {
        return imgFileMapper.selectList(new LambdaQueryWrapper<ImgFile>()
                .eq(ImgFile::getBatchId, batchId).orderByAsc(ImgFile::getPageNo));
    }

    /**
     * 影像替扫（TC10）：扫描件错误时的替换链路。
     * 旧影像页面标记 REPLACED（文件本体保留在文件存储，记录可审计），
     * 新影像上传后以相同批次/页码插入，供审批流后续可见。
     * @return 新影像页 ID；@param toReplaceId 被替换的旧影像页 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long replaceScan(Long toReplaceId, MultipartFile newFile, String zone) {
        ImgFile old = imgFileMapper.selectById(toReplaceId);
        if (old == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "待替换影像页不存在: " + toReplaceId);
        }
        if (newFile == null || newFile.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未选择新的影像文件");
        }
        // 1. 旧影像记录：置 REPLACED，保留原 fileId 指向的原文件（不物理删除）
        ImgFile oldUpd = new ImgFile();
        oldUpd.setId(old.getId());
        oldUpd.setVerifyStatus("REPLACED");
        oldUpd.setRemark("影像替扫：被 " + newFile.getOriginalFilename() + " 替换");
        imgFileMapper.updateById(oldUpd);

        // 2. 新影像落盘 + 建同批次(同页码)新页
        Long tenantId = TenantContext.getTenantId();
        SysFile saved = fileService.upload(newFile,
                old.getBizType() == null ? "IMAGE" : old.getBizType(), old.getBizId(), zone);
        ImgFile f = new ImgFile();
        f.setTenantId(tenantId);
        f.setBatchId(old.getBatchId());
        f.setFileId(saved.getId());
        f.setPageType(old.getPageType() == null ? "NORMAL" : old.getPageType());
        f.setPageNo(old.getPageNo());
        f.setOcrStatus("NONE");
        f.setVerifyStatus("NONE");
        f.setBizType(old.getBizType());
        f.setBizId(old.getBizId());
        f.setRemark("影像替扫（新），原页 id=" + old.getId() + " 已 REPLACED");
        f.setCreateTime(LocalDateTime.now());
        imgFileMapper.insert(f);
        // 批次可重新进入复核
        if (old.getBatchId() != null) {
            setReviewStatus(old.getBatchId(), "PENDING");
        }
        log.info("影像替扫完成: 旧页 {} → 新页 {}，批次 {}", old.getId(), f.getId(), old.getBatchId());
        return f.getId();
    }

    /** 删除影像批次（逻辑删：批次 + 批次内页面；发布影像状态回退事件） */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteBatch(Long batchId) {
        ImgBatch batch = imgBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "影像批次不存在: " + batchId);
        }
        imgFileMapper.delete(new LambdaQueryWrapper<ImgFile>().eq(ImgFile::getBatchId, batchId));
        imgBatchMapper.deleteById(batchId);
        // 关联单据影像状态回退（断点1：改为发布事件，不再直接操作 ExpBillMapper）
        if (batch.getBizId() != null) {
            publishBillImageEvent(batch.getBizType(), batch.getBizId(), "NONE");
        }
    }

    @Data
    public static class PageReq {
        private Long fileId;
        private String pageType;
        private Integer pageNo;
    }

    private String nextBatchNo() {
        try {
            return numberRuleService.next("IMAGE_BATCH");
        } catch (Exception e) {
            return "IMG" + System.currentTimeMillis();
        }
    }
}
