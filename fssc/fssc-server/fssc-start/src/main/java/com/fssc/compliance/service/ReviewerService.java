// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.Reviewer;
import com.fssc.compliance.mapper.ReviewerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewerService {

    private final ReviewerMapper reviewerMapper;

    public List<Reviewer> listAllActive() {
        LambdaQueryWrapper<Reviewer> qw = new LambdaQueryWrapper<>();
        qw.eq(Reviewer::getStatus, 1);
        return reviewerMapper.selectList(qw);
    }

    public List<Reviewer> listByGroup(Long groupId) {
        LambdaQueryWrapper<Reviewer> qw = new LambdaQueryWrapper<>();
        qw.eq(Reviewer::getReviewerGroupId, groupId)
                .eq(Reviewer::getStatus, 1);
        return reviewerMapper.selectList(qw);
    }

    public Reviewer getByUserId(Long userId) {
        LambdaQueryWrapper<Reviewer> qw = new LambdaQueryWrapper<>();
        qw.eq(Reviewer::getUserId, userId)
                .last("limit 1");
        return reviewerMapper.selectOne(qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Reviewer reviewer) {
        reviewer.setStatus(1);
        if (reviewer.getCurrentPending() == null) {
            reviewer.setCurrentPending(0);
        }
        if (reviewer.getCompletedToday() == null) {
            reviewer.setCompletedToday(0);
        }
        reviewerMapper.insert(reviewer);
        log.info("创建审核员: {}", reviewer.getReviewerName());
        return reviewer.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Reviewer reviewer) {
        Reviewer existing = reviewerMapper.selectById(reviewer.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核员不存在: " + reviewer.getId());
        }
        reviewerMapper.updateById(reviewer);
        log.info("更新审核员: {}", reviewer.getReviewerName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void incrementCompleted(Long reviewerId) {
        LambdaUpdateWrapper<Reviewer> uw = new LambdaUpdateWrapper<>();
        uw.eq(Reviewer::getId, reviewerId)
                .setSql("completed_today = completed_today + 1");
        reviewerMapper.update(null, uw);
    }

    @Transactional(rollbackFor = Exception.class)
    public void incrementPending(Long reviewerId) {
        LambdaUpdateWrapper<Reviewer> uw = new LambdaUpdateWrapper<>();
        uw.eq(Reviewer::getId, reviewerId)
                .setSql("current_pending = current_pending + 1");
        reviewerMapper.update(null, uw);
    }

    @Transactional(rollbackFor = Exception.class)
    public void decrementPending(Long reviewerId) {
        LambdaUpdateWrapper<Reviewer> uw = new LambdaUpdateWrapper<>();
        uw.eq(Reviewer::getId, reviewerId)
                .setSql("current_pending = GREATEST(current_pending - 1, 0)");
        reviewerMapper.update(null, uw);
    }

    public Reviewer getById(Long id) {
        Reviewer reviewer = reviewerMapper.selectById(id);
        if (reviewer == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核员不存在: " + id);
        }
        return reviewer;
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        Reviewer reviewer = reviewerMapper.selectById(id);
        if (reviewer == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核员不存在: " + id);
        }
        reviewer.setStatus(1);
        reviewerMapper.updateById(reviewer);
        log.info("启用审核员: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        Reviewer reviewer = reviewerMapper.selectById(id);
        if (reviewer == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核员不存在: " + id);
        }
        reviewer.setStatus(0);
        reviewerMapper.updateById(reviewer);
        log.info("停用审核员: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Reviewer reviewer = reviewerMapper.selectById(id);
        if (reviewer == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核员不存在: " + id);
        }
        reviewerMapper.deleteById(id);
        log.info("删除审核员: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchEnable(List<Long> ids) {
        ids.forEach(this::enable);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDisable(List<Long> ids) {
        ids.forEach(this::disable);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        ids.forEach(this::delete);
    }
}
