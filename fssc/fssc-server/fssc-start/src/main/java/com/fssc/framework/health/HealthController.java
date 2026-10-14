// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.health;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 免鉴权健康探活端点（P0-B1）。
 *
 * <p>与 Actuator 的分工：
 * <ul>
 *   <li>{@code /api/health}（本类）：业务自描述的轻量探活，回显 DB/Redis 组件状态与版本，
 *       字段口径由业务定义，可直接给人看；</li>
 *   <li>{@code /actuator/health}：标准 Actuator 探活 + k8s liveness/readiness 探针。</li>
 * </ul>
 * 两者都已在 JwtAuthFilter 白名单放行，无需 Token（探针不可能持有业务 Token）。
 *
 * <p>响应约定：沿用全平台「HTTP 200 + body.code」，健康时 code=0，不健康时 code=500，
 * 探针按 body.code 判定即可（与项目既有约定一致，不引入裸 HTTP 5xx）。
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> detail = healthService.check();
        boolean up = "UP".equals(detail.get("status"));
        return up
                ? Result.success(detail)
                : Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), "服务不健康：" + detail.get("components"));
    }
}
