package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户操作日志（sys_oper_log，带 tenant_id）。不可删除，无 del_flag。
 * 【列名与平台表 pf_oper_log 不同】：title/business_type/oper_url/oper_param/json_result/cost_time。
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 链路追踪 ID（X-Trace-Id / MDC），与 ai_tool_call_log.trace_id 串联人机双链路审计 */
    private String traceId;

    /** 模块标题 */
    private String title;
    /** INSERT/UPDATE/DELETE/EXPORT/IMPORT/GRANT/SUBMIT/APPROVE/REJECT/OTHER */
    private String businessType;
    private String method;
    private String requestMethod;
    /** 请求URL */
    private String operUrl;
    private String operIp;
    private String operLocation;
    private Long operUserId;
    private String operUserName;
    private String operDeptName;
    /** 请求参数（脱敏后） */
    private String operParam;
    /** 返回结果（截断） */
    private String jsonResult;
    /** 0 成功 1 失败 */
    private Integer status;
    private String errorMsg;
    /** 耗时毫秒 */
    private Long costTime;
    private LocalDateTime operTime;
}
