// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 邮包邮件发送记录：每次通知/重发留存，供邮件管理与追溯。 */
@Data
@TableName("ar_mail_notify_log")
public class ArMailNotifyLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long packageId;
    private String packageNo;
    private Long recipientId;
    private String receiver;
    private String email;
    private String subject;
    private String content;
    /** SENT=已发送 / SIMULATED=邮件通道未配置（模拟）/ FAILED=发送失败 */
    private String sendStatus;
    private String errorMsg;
    private LocalDateTime sendTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
}
