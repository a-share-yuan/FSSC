// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.system.entity.SysMessage;
import com.fssc.system.mapper.SysMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 站内消息/待办提醒（sys_message，带 tenant_id）。 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final SysMessageMapper messageMapper;

    public PageResult<SysMessage> page(long pageNum, long pageSize, Long receiverId, String msgType, Integer isRead) {
        LambdaQueryWrapper<SysMessage> qw = new LambdaQueryWrapper<>();
        if (receiverId != null) {
            qw.eq(SysMessage::getReceiverId, receiverId);
        }
        if (msgType != null && !msgType.isBlank()) {
            qw.eq(SysMessage::getMsgType, msgType);
        }
        if (isRead != null) {
            qw.eq(SysMessage::getIsRead, isRead);
        }
        qw.orderByDesc(SysMessage::getId);
        Page<SysMessage> p = messageMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 发送消息给指定用户（业务链路调用，如审批完成通知） */
    public Long send(Long receiverId, String msgType, String title, String content,
                     String bizType, Long bizId, String linkUrl) {
        SysMessage m = new SysMessage();
        m.setReceiverId(receiverId);
        m.setMsgType(msgType == null ? "NOTICE" : msgType);
        m.setTitle(title);
        m.setContent(content);
        m.setBizType(bizType);
        m.setBizId(bizId);
        m.setLinkUrl(linkUrl);
        m.setIsRead(0);
        messageMapper.insert(m);
        return m.getId();
    }

    /**
     * 按主键读单条（D-DEC-149/187 已读回写的归属判据用）。
     *
     * <p>与 {@link #markRead(Long)} 不同，本方法<b>不抛</b>「不存在」——控制器要拿它区分
     * 「行不存在(404)」与「不是你的消息(403)」两种结果，判空即可。
     * 租户条件由 MyBatis-Plus 拦截器注入，跨租户天然读不到。
     */
    public SysMessage find(Long id) {
        return id == null ? null : messageMapper.selectById(id);
    }

    /** 标记已读 */
    public void markRead(Long id) {
        SysMessage exist = messageMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "消息不存在: " + id);
        }
        SysMessage m = new SysMessage();
        m.setId(id);
        m.setIsRead(1);
        m.setReadTime(LocalDateTime.now());
        messageMapper.updateById(m);
    }

    /** 全部标记已读（指定接收人） */
    public void markAllRead(Long receiverId) {
        messageMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysMessage>()
                        .eq(SysMessage::getReceiverId, receiverId)
                        .eq(SysMessage::getIsRead, 0)
                        .set(SysMessage::getIsRead, 1)
                        .set(SysMessage::getReadTime, LocalDateTime.now()));
    }

    /** 未读数 */
    public long unreadCount(Long receiverId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getReceiverId, receiverId)
                .eq(SysMessage::getIsRead, 0));
    }

    public void delete(Long id) {
        messageMapper.deleteById(id);
    }
}
