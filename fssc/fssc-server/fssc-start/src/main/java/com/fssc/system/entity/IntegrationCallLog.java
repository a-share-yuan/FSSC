package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Integration Gateway unified call log (integration_call_log).
 * Every outbound call through IntegrationGateway is logged here for audit and monitoring.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("integration_call_log")
public class IntegrationCallLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** ext_interface_config.category: AI_MODEL / OCR / INVOICE_VERIFY / INVOICE_CERTIFY / LINKU / BANK */
    private String category;

    /** ext_interface_config.interface_code */
    private String interfaceCode;

    /** GET / POST */
    private String method;

    /** Actual URL called */
    private String requestUrl;

    /** JSON request body (truncated 4000 chars) */
    private String requestBody;

    /** JSON response body (truncated 4000 chars) */
    private String responseBody;

    /** HTTP status code */
    private Integer statusCode;

    /** 1=success, 0=fail */
    private Integer success;

    /** Call duration in ms */
    private Integer durationMs;

    /** Error message if failed */
    private String errorMsg;

    /** P0-2 幂等键：{domain}:{tenantId}:{businessNo}[:{detail}]，同 (tenant,key) 重复调用不重复外呼 */
    private String idempotencyKey;

    /** P0-2 1=本条为幂等命中回放（未真实外呼） */
    private Integer isDuplicate;

    /** P0-3 已重试/重投次数（含 Job 重投），用于封顶 */
    private Integer retryCount;

    /** P0-3 链路追踪号（TraceIdFilter 的 MDC traceId），串联业务日志排障 */
    private String traceId;
}
