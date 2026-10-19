// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.exception;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 过滤器级错误渲染。@RestControllerAdvice 只管 Controller 调用链上的异常，
 * 而 JwtAuthFilter / TenantFilter 抛出的 BizException 不会经过它，会变成裸 500。
 * 本组件在过滤器 catch 后直接写 JSON，与全局约定保持一致（HTTP 200 + body.code）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FrameworkErrorRenderer {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, BizException ex) throws IOException {
        log.warn("过滤器拦截 uri 未完成, code={} msg={}", ex.getCode(), ex.getMessage());
        doWrite(response, Result.fail(ex.getCode(), ex.getMessage()));
    }

    public void writeSystemError(HttpServletResponse response, Exception ex) throws IOException {
        log.error("过滤器未预期异常", ex);
        doWrite(response, Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
    }

    private void doWrite(HttpServletResponse response, Result<?> result) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), result);
    }
}
