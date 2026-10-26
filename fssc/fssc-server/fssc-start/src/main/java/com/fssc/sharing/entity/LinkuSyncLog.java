// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * LinkU 回调同步日志。
 * 记录每次 LinkU Webhook 回调的事件类型、请求体、处理结果。
 */
@Data
@TableName("linku_sync_log")
public class LinkuSyncLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件类型: RECON_CONFIRM / RECON_MATCH / DIFF_RESOLVED / RECON_CONFIRMED / INVOICE_VERIFIED */
    private String eventType;

    /** LinkU侧对账单ID */
    private Long linkuReconId;

    /** FSSC侧供应商ID */
    private Long supplierId;

    /** 回调请求体JSON */
    private String requestBody;

    /**
     * 唯一键（D-V5R-03 修复）：与 event_type 合成生成列 active_idem_key
     * （{@code event_type:ifnull(idempotency_key,'')}），唯一索引 uk_linku_sync_log_idem 建在它上面。
     *
     * <p>修复前本字段在实体里缺位 ⇒ 回调写入时恒为 NULL ⇒ 生成列退化为
     * {@code "RECON_MATCH:"}（事件类型冒号后为空），第二条成功记录必撞唯一键，
     * DuplicateKeyException 被吞掉，整条记录丢失。</p>
     */
    private String idempotencyKey;

    /** 处理结果: SUCCESS / FAILED */
    private String responseStatus;

    /** 错误信息 */
    private String errorMsg;

    /** 处理耗时(毫秒) */
    private Integer durationMs;

    /** 接收时间 */
    private LocalDateTime createTime;
}
