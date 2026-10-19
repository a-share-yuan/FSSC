// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 凭证推送外部系统日志（op_voucher_push_log，带 tenant_id）。
 * P1-8：替代原 VoucherPushService 的进程内 Map，推送轨迹可持久化、可审计、多实例一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_voucher_push_log")
public class OpVoucherPushLog extends TenantAuditBase {

    /** 凭证ID */
    private Long voucherId;
    /** 凭证号（冗余） */
    private String voucherNo;
    /** 推送目标：NC_SYSTEM / IFS_SYSTEM */
    private String target;
    /** 推送通道：local / http */
    private String channel;
    /** 推送结果：SUCCESS / FAILED */
    private String status;
    /** 外部系统回执单号 */
    private String externalNo;
    /** 推送时间 */
    private LocalDateTime pushTime;
    /** 失败原因 */
    private String errorMsg;
    /** 推送请求体快照 */
    private String requestBody;
    /** 外部响应快照 */
    private String responseBody;
}
