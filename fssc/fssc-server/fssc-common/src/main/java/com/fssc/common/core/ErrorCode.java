// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.common.core;

/**
 * 平台统一错误码。约定：
 * 0 = 成功
 * 4xx = 客户端/权限类（400 参数、401 未登录、403 无权限、404 端点不存在、405 动词不支持）
 * 4010 = 租户上下文缺失（多租户铁律触发，fail-closed）
 * 401x = 认证链细分（4011 需携带图形验证码、4012 图形验证码无效；均仍属客户端可自解的 401 族）
 * 403x = 权限/功能开关/两级路由类
 * 4090 = 唯一键冲突（数据已存在，客户端可自解，非服务端故障）
 * 5xx = 服务端异常
 * 5001 = 工作流引擎异常（流程定义缺失/损坏，管理员介入，重试无效）
 */
public enum ErrorCode {

    SUCCESS(0, "成功"),

    PARAM_INVALID(400, "参数校验失败"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    NOT_FOUND(404, "端点不存在"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),

    TENANT_CONTEXT_MISSING(4010, "租户上下文缺失，拒绝执行"),
    PERMISSION_DENIED(4030, "无该功能/按钮权限"),
    FEATURE_DISABLED(4031, "当前套餐未开通该功能"),
    PLATFORM_ONLY(4032, "仅平台超级管理员可访问"),
    TENANT_ONLY(4033, "仅租户用户可访问"),
    DATA_SCOPE_EMPTY(4034, "数据权限范围未配置"),

    /**
     * 唯一键冲突（DuplicateKeyException）专用码。此前落默认处理 500「系统异常」，
     * 调用方无法区分"我重复提交了"与"服务端坏了"，会自动重试；对外文案只说业务含义，
     * 不回显 SQL 语句、索引名或 mapper 路径（详细原因由 log.warn 落服务端日志）。
     */
    DATA_ALREADY_EXISTS(4090, "该记录已存在，请勿重复提交；可在列表中核对已有记录"),

    /**
     * C-3 登录图形验证码（失败阈值触发）：该身份的连续凭据失败已达
     * {@code fssc.login.captcha-fail-threshold}，本次登录必须携带 captchaId/captchaCode。
     * 前端据此显示验证码框并自动拉图（GET /api/auth/captcha）。
     */
    CAPTCHA_REQUIRED(4011, "连续登录失败次数已达阈值，请输入图形验证码后重试"),
    /** 验证码不匹配/已过期/已被使用（一次性消费），需重新取图；不区分原因，防探测。 */
    CAPTCHA_INVALID(4012, "图形验证码错误或已失效，请重新输入"),

    /**
     * 工作流引擎运行异常（FlowableException）专用码。
     * 此前落默认处理 500「系统异常，请稍后重试」，操作人无法区分"我填错了"与"系统坏了"，
     * 于是反复重试同一笔。实测最典型的一次是清库误删 {@code act_ge_bytearray}（BPMN 资源字节流），
     * 引擎起流程时读不到流程定义 → {@code deployment 'X' didn't put process definition 'Y' in the cache}，
     * 界面只给一句"系统异常"，排查成本极高。
     * 5xx 族但语义明确：<b>不是用户能靠重试解决的</b>，须管理员介入（重新部署流程或恢复引擎资源表）。
     */
    WORKFLOW_ENGINE_ERROR(5001, "审批流程启动失败：流程定义缺失或损坏，需管理员重新部署流程定义后重试（重试无效）"),

    SYSTEM_ERROR(500, "系统异常，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
