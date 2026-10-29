// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台操作日志（pf_oper_log）。
 * 【不可删除】：无 del_flag，Mapper 不提供 delete —— 满足审计铁律。
 *
 * <p>本表不是空壳，0 行 ≠ 无写入者：写入链路完整——
 * {@code OperLogAspect:66-83} 按登录主体分轨，平台用户（{@code LoginUser.isPlatform()}）走
 * {@code OperLogWriter.writePf} → {@code PfOperLogMapper.insert}；平台主体由
 * {@code AuthService:209} / {@code AuthController:47} 赋 {@code SubjectType.PLATFORM}。
 * 本库 0 行的真实含义是<b>"从未发生过平台侧带 {@code @OperLog} 的操作"</b>（平台接口
 * {@code /api/platform/**} 无人调用），而非"没有写入位置"。审计表排查时勿据此列删除本表或改接
 * {@code sys_oper_log}（后者带 tenant_id，平台侧无归属租户）。
 */
@Data
@TableName("pf_oper_log")
public class PfOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String module;
    /** INSERT/UPDATE/DELETE/QUERY/EXPORT/IMPORT/GRANT/OTHER */
    private String operType;
    private String operDesc;
    private String method;
    private String requestMethod;
    private String requestUri;
    /** 请求参数（已脱敏） */
    private String requestParam;
    /** 响应结果（截断） */
    private String responseData;
    private Long operUserId;
    private String operUsername;
    private String operIp;
    private String operLocation;
    /** 1 成功 0 失败 */
    private Integer status;
    private String errorMsg;
    private Long costMs;
    private LocalDateTime operTime;
}
