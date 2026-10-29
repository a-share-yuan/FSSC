// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内消息/待办提醒（sys_message，带 tenant_id）。
 * 类型：TODO待办/DONE已办/NOTICE通知/ALERT预警。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message")
public class SysMessage extends TenantAuditBase {

    /** 接收人用户ID */
    private Long receiverId;
    /** TODO/DONE/NOTICE/ALERT */
    private String msgType;
    private String title;
    private String content;
    /** 业务类型，如 EXP_CLAIM */
    private String bizType;
    /** 业务主键 */
    private Long bizId;
    /** 跳转地址（PC/H5共用相对路径） */
    private String linkUrl;
    /** 是否已读：0否 1是 */
    private Integer isRead;
    /** 阅读时间 */
    private LocalDateTime readTime;
}
