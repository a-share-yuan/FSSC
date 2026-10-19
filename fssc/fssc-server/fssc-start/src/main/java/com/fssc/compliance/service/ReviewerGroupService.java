// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.compliance.entity.Reviewer;
import com.fssc.compliance.mapper.ReviewerMapper;
import com.fssc.sharing.entity.ShGroup;
import com.fssc.sharing.mapper.ShGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewerGroupService {

    private final ShGroupMapper shGroupMapper;
    private final ReviewerMapper reviewerMapper;

    public List<ShGroup> listAll() {
        LambdaQueryWrapper<ShGroup> qw = new LambdaQueryWrapper<>();
        qw.eq(ShGroup::getStatus, "NORMAL")
                .orderByAsc(ShGroup::getPriority);
        return shGroupMapper.selectList(qw);
    }

    public ShGroup getById(Long id) {
        ShGroup group = shGroupMapper.selectById(id);
        if (group == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核组不存在: " + id);
        }
        return group;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ShGroup group) {
        group.setStatus("NORMAL");
        shGroupMapper.insert(group);
        log.info("创建审核组: {}", group.getGroupName());
        return group.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ShGroup group) {
        ShGroup existing = shGroupMapper.selectById(group.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核组不存在: " + group.getId());
        }
        shGroupMapper.updateById(group);
        log.info("更新审核组: {}", group.getGroupName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ShGroup group = shGroupMapper.selectById(id);
        if (group == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核组不存在: " + id);
        }
        LambdaQueryWrapper<Reviewer> memberQw = new LambdaQueryWrapper<>();
        memberQw.eq(Reviewer::getReviewerGroupId, id);
        Long memberCount = reviewerMapper.selectCount(memberQw);
        if (memberCount > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核组下还有成员，无法删除");
        }
        group.setStatus("DISABLED");
        shGroupMapper.updateById(group);
        log.info("删除审核组: {}", group.getGroupName());
    }

    public List<Reviewer> listMembers(Long groupId) {
        LambdaQueryWrapper<Reviewer> qw = new LambdaQueryWrapper<>();
        qw.eq(Reviewer::getReviewerGroupId, groupId)
                .eq(Reviewer::getStatus, 1);
        return reviewerMapper.selectList(qw);
    }
}
