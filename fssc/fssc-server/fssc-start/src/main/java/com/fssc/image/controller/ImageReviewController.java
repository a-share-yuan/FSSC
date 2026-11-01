// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.image.entity.ImgBatch;
import com.fssc.image.entity.ImgException;
import com.fssc.image.entity.ImgReview;
import com.fssc.image.mapper.ImgBatchMapper;
import com.fssc.image.mapper.ImgExceptionMapper;
import com.fssc.image.mapper.ImgReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 影像调阅评价与异常处理（需求 6.5/6.7）。
 * 分库改造：直连 image_review/image_exception/img_batch Mapper，类级路由 fssc_image 库。
 */
@RestController
@RequestMapping("/api/tenant/image")
@RequiredArgsConstructor
@FsscDataSource("image")
public class ImageReviewController {

    private final ImgReviewMapper reviewMapper;
    private final ImgExceptionMapper exceptionMapper;
    private final ImgBatchMapper batchMapper;

    // 影像调阅评价

    @RequiresPerm("image:review")
    @GetMapping("/review/page")
    public Result<PageResult<ImgReview>> reviewPage(PageQuery q, String status, String keyword) {
        LambdaQueryWrapper<ImgReview> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq(ImgReview::getReviewStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(ImgReview::getBatchNo, keyword).or().like(ImgReview::getBizNo, keyword));
        }
        w.orderByDesc(ImgReview::getId);
        Page<ImgReview> p = reviewMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    /** 影像评价合法动作（与前端 evaluate 调用同一规则） */
    private static final Set<String> REVIEW_ACTIONS = Set.of("OK", "RE_COLLECT", "RESCAN", "REJECT");

    @RequiresPerm("image:review")
    @OperLog(module = "IMAGE", operType = "UPDATE", desc = "影像调阅评价")
    @PostMapping("/review/evaluate")
    public Result<Void> evaluate(@RequestBody ReviewReq req) {
        // 动作值必须落在白名单内，否则直写 review_status 会产生静默坏数据
        if (req.getAction() == null || !REVIEW_ACTIONS.contains(req.getAction())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "评价动作非法：" + req.getAction() + "，仅允许 OK/RE_COLLECT/RESCAN/REJECT");
        }
        ImgReview review = req.getId() != null ? reviewMapper.selectById(req.getId()) : null;
        if (review == null) {
            // 从批次生成待评价记录
            ImgBatch batch = batchMapper.selectById(req.getBatchId());
            if (batch == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "影像批次不存在: " + req.getBatchId());
            }
            review = new ImgReview();
            review.setImgBatchId(batch.getId());
            review.setBatchNo(batch.getBatchNo());
            review.setBizId(batch.getBizId());
            review.setBizType(batch.getBizType());
            review.setReviewStatus("PENDING");
            reviewMapper.insert(review);
        } else if (!"PENDING".equals(review.getReviewStatus())) {
            // 状态机：已评价记录不可重复评价（前端只在 PENDING 行渲染评价按钮，服务端同一规则拦截）
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该影像记录已评价为「" + review.getReviewStatus() + "」，不可重复评价；如需更正请走整单重采集/补采集");
        }
        review.setReviewStatus(req.getAction());
        review.setReviewTime(LocalDateTime.now());
        // 评价记录回填评价人（否则 review_user_id 恒 NULL，审核责任不可追溯）
        review.setReviewUserId(UserContext.getRequired().getUserId());
        review.setRemark(req.getRemark());
        reviewMapper.updateById(review);
        // 同步批次状态（REJECT 整单作废）
        if (review.getImgBatchId() != null) {
            ImgBatch up = new ImgBatch();
            up.setId(review.getImgBatchId());
            up.setStatus("REJECT".equals(req.getAction()) ? "VOID" : "REVIEWED");
            batchMapper.updateById(up);
        }
        return Result.success();
    }

    @RequiresPerm("image:review")
    @OperLog(module = "IMAGE", operType = "INSERT", desc = "录入待评价影像")
    @PostMapping("/review/create")
    public Result<Long> reviewCreate(@RequestBody ImgReview review) {
        // 服务端非空校验（与前端 reviewRules 同一规则，双侧失效→双侧办结）
        AssertUtil.notBlank(review.getBatchNo(), ErrorCode.PARAM_INVALID, "请填写影像批次号");
        AssertUtil.notBlank(review.getBizNo(), ErrorCode.PARAM_INVALID, "请填写业务单号");
        review.setReviewStatus("PENDING");
        reviewMapper.insert(review);
        return Result.success(review.getId());
    }

    @RequiresPerm("image:review")
    @OperLog(module = "IMAGE", operType = "DELETE", desc = "删除影像评价")
    @PostMapping("/review/{id}/delete")
    public Result<Void> reviewDelete(@PathVariable Long id) {
        ImgReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "影像评价记录不存在: " + id);
        }
        // 已评价记录＝评价记录，只允许删未评价的录入（与前端按钮校验同一规则，W3 配套）
        if (!"PENDING".equals(review.getReviewStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该记录已评价为「" + review.getReviewStatus() + "」，评价结果属于记录，不可删除；仅待评价记录可删");
        }
        reviewMapper.deleteById(id);
        return Result.success();
    }

    // 影像异常处理

    @RequiresPerm("image:review")
    @GetMapping("/exception/page")
    public Result<PageResult<ImgException>> exceptionPage(PageQuery q, String status) {
        LambdaQueryWrapper<ImgException> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq(ImgException::getStatus, status);
        w.orderByDesc(ImgException::getId);
        Page<ImgException> p = exceptionMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("image:review")
    @OperLog(module = "IMAGE", operType = "INSERT", desc = "上报影像异常")
    @PostMapping("/exception/create")
    public Result<Long> exceptionCreate(@RequestBody ImgException e) {
        // 服务端非空校验（业务单号为空不能入库）
        AssertUtil.notBlank(e.getBatchNo(), ErrorCode.PARAM_INVALID, "请填写影像批次号");
        AssertUtil.notBlank(e.getBizNo(), ErrorCode.PARAM_INVALID, "请填写业务单号");
        AssertUtil.notBlank(e.getExceptionType(), ErrorCode.PARAM_INVALID, "请选择异常类型");
        e.setStatus("OPEN");
        exceptionMapper.insert(e);
        return Result.success(e.getId());
    }

    @RequiresPerm("image:review")
    @OperLog(module = "IMAGE", operType = "UPDATE", desc = "处理影像异常")
    @PostMapping("/exception/{id}/fix")
    public Result<Void> exceptionFix(@PathVariable Long id, String remark) {
        ImgException e = exceptionMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "影像异常记录不存在: " + id);
        }
        if ("FIXED".equals(e.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该异常已处理，不可重复处理");
        }
        e.setStatus("FIXED");
        e.setHandleTime(LocalDateTime.now());
        // 处理记录回填处理人（否则 handle_user_id 恒 NULL）
        e.setHandleUserId(UserContext.getRequired().getUserId());
        if (remark != null) e.setRemark(remark);
        exceptionMapper.updateById(e);
        return Result.success();
    }

    @RequiresPerm("image:review")
    @OperLog(module = "IMAGE", operType = "DELETE", desc = "删除影像异常")
    @PostMapping("/exception/{id}/delete")
    public Result<Void> exceptionDelete(@PathVariable Long id) {
        if (exceptionMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "影像异常记录不存在: " + id);
        }
        exceptionMapper.deleteById(id);
        return Result.success();
    }

    @lombok.Data
    public static class ReviewReq {
        private Long id;
        private Long batchId;
        /** OK通过/RE_COLLECT整单重采集/RESCAN补采集/REJECT整单作废 */
        private String action;
        private String remark;
    }
}
