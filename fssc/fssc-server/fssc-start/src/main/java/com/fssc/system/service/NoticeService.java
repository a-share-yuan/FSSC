// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.system.entity.SysNotice;
import com.fssc.system.mapper.SysNoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 通知公告（sys_notice，带 tenant_id）。 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final SysNoticeMapper noticeMapper;

    public PageResult<SysNotice> page(long pageNum, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<SysNotice> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(SysNotice::getNoticeTitle, keyword);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(SysNotice::getStatus, status);
        }
        qw.orderByDesc(SysNotice::getId);
        Page<SysNotice> p = noticeMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public SysNotice detail(Long id) {
        SysNotice n = noticeMapper.selectById(id);
        if (n == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告不存在: " + id);
        }
        return n;
    }

    public Long create(SysNotice notice) {
        notice.setId(null);
        if (notice.getNoticeType() == null) {
            notice.setNoticeType("NOTICE");
        }
        if (notice.getStatus() == null) {
            notice.setStatus("DRAFT");
        }
        // 发布时自动补发布时间
        if ("PUBLISHED".equals(notice.getStatus()) && notice.getPublishTime() == null) {
            notice.setPublishTime(LocalDateTime.now());
        }
        noticeMapper.insert(notice);
        return notice.getId();
    }

    public void update(SysNotice notice) {
        if (noticeMapper.selectById(notice.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告不存在: " + notice.getId());
        }
        noticeMapper.updateById(notice);
    }

    /** 发布：DRAFT → PUBLISHED（自动补发布时间） */
    public void publish(Long id) {
        SysNotice exist = noticeMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告不存在: " + id);
        }
        SysNotice n = new SysNotice();
        n.setId(id);
        n.setStatus("PUBLISHED");
        if (exist.getPublishTime() == null) {
            n.setPublishTime(LocalDateTime.now());
        }
        noticeMapper.updateById(n);
    }

    public void close(Long id) {
        if (noticeMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告不存在: " + id);
        }
        SysNotice n = new SysNotice();
        n.setId(id);
        n.setStatus("CLOSED");
        noticeMapper.updateById(n);
    }

    public void delete(Long id) {
        noticeMapper.deleteById(id);
    }

    /** 获取已发布的有效公告列表（用于弹窗提醒） */
    public List<SysNotice> getActiveNotices() {
        return noticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, "PUBLISHED")
                .orderByDesc(SysNotice::getPublishTime));
    }
}
