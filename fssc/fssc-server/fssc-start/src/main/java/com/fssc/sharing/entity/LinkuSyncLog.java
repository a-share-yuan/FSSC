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

    /** 处理结果: SUCCESS / FAILED */
    private String responseStatus;

    /** 错误信息 */
    private String errorMsg;

    /** 处理耗时(毫秒) */
    private Integer durationMs;

    /** 接收时间 */
    private LocalDateTime createTime;
}
