// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai;

import com.fssc.ai.entity.AiInspectRule;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.trace.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 入站网关（唯一入站口，方案 §4-②）。
 *
 * <p>路径 {@code /api/ai/gateway/**} 不在 JwtAuthFilter 白名单内 ⇒ 必须携带真人登录 token；
 * 本系统 token 一律由真人登录换取（不存在 API-key/服务账号通道），"身份透传、权限自动继承"由此成立。
 * 网关本身不做业务判断，业务判断全部发生在回环转发的 {@code /api/tenant/**} 原链路上。
 *
 * <p>治理说明：票据"确认"动作刻意放在租户侧（{@code /api/tenant/ai/confirm/**}，见 AiConfirmController），
 * 走正常的权限/审计体系——AI 可以出示票据号，但永远不能替人按下确认键。
 */
@RestController
@RequestMapping("/api/ai/gateway")
@RequiredArgsConstructor
public class AiGatewayController {

    private final AiGatewayService gatewayService;
    private final AiInspectService inspectService;

    /** 动作发现：AI 的"眼睛"。只回目录里登记的、且默认只回 AI 可调用的动作。 */
    @GetMapping("/actions")
    public Result<List<Map<String, Object>>> actions(@RequestParam(required = false) String kind,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "true") boolean aiOnly) {
        requireTenant();
        // aiOnly 下推到服务层过滤（原实现在 limit 之后过滤 ⇒ 返回条数与真实可调用数不符）
        return Result.success(gatewayService.catalog(kind, keyword, aiOnly).stream()
                .map(a -> Map.<String, Object>of(
                        "code", a.getCode(),
                        "url", a.getUrl(),
                        "method", a.getMethod(),
                        "permCode", String.valueOf(a.getPermCode()),
                        "kind", a.getKind(),
                        "riskLevel", a.getRiskLevel(),
                        "idempotency", String.valueOf(a.getIdempotency()),
                        "preconditions", String.valueOf(a.getPreconditions()),
                        "aiAllowed", a.isAiAllowed()))
                .toList());
    }

    /** 执行一个登记动作。未登记/红线/无权限/缺唯一键/缺票据 → 403/400，且全部记录。 */
    @PostMapping("/execute")
    public Result<Map<String, Object>> execute(@RequestBody AiGatewayService.GatewayRequest req,
                                               HttpServletRequest http) {
        LoginUser user = requireTenant();
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        AiGatewayService.GatewayResult r = gatewayService.execute(req, http, user, traceId);
        if (r.bizCode() != null && r.bizCode() != 0) {
            // 下游业务拒绝如实上抛语义（保留 traceId 供回放）
            return Result.fail(r.bizCode(), r.errorMsg() == null ? "下游业务拒绝" : r.errorMsg());
        }
        return Result.success(Map.of(
                "traceId", String.valueOf(r.traceId()),
                "action", String.valueOf(r.actionCode()),
                "data", r.data() == null ? Map.of() : r.data()));
    }

    /** 高风险写的"待确认"票据申请：AI 出参数摘要，人按确认键。 */
    @PostMapping("/confirm-request")
    public Result<Map<String, Object>> confirmRequest(@RequestBody ConfirmRequestBody body, HttpServletRequest http) {
        LoginUser user = requireTenant();
        AiAction a = gatewayService.actionByCode(body.actionCode());
        if (a == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "动作未登记，无法申请确认票据：" + body.actionCode());
        }
        var c = gatewayService.requestConfirm(a, body.args(), body.instructionId(), user);
        return Result.success(Map.of(
                "confirmId", c.getId(),
                "status", c.getStatus(),
                "paramsDigest", c.getParamsDigest(),
                "expireAt", String.valueOf(c.getExpireAt()),
                "next", "请由其他有权限真人在 /api/tenant/ai/confirm/{id} 确认后方可执行"));
    }

    /** 巡检规则：登记/查询/停用。规则只能引用只读动作，命中后进人工确认队列。 */
    @GetMapping("/inspect-rules")
    public Result<List<AiInspectRule>> rules() {
        requireTenant();
        return Result.success(inspectService.list(UserContext.getRequired()));
    }

    @PostMapping("/inspect-rules")
    public Result<AiInspectRule> createRule(@RequestBody RuleBody body) {
        requireTenant();
        return Result.success(inspectService.create(body.instruction(), body.rule(), UserContext.getRequired()));
    }

    /** 自然语言→规则草稿（可能返回 null=翻译不出来，前端引导人工填表）。 */
    @PostMapping("/inspect-rules/suggest")
    public Result<AiInspectRule> suggestRule(@RequestBody Map<String, String> body) {
        requireTenant();
        return Result.success(inspectService.suggest(body.get("instruction"), UserContext.getRequired()));
    }

    @PostMapping("/inspect-rules/{id}/toggle")
    public Result<Void> toggleRule(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean on) {
        requireTenant();
        inspectService.toggle(id, on, UserContext.getRequired());
        return Result.success();
    }

    private LoginUser requireTenant() {
        LoginUser u;
        try {
            u = UserContext.getRequired();
        } catch (Exception ex) {
            // 匿名/失效 token 打 AI 入口：留 WARN 痕迹（JwtAuthFilter 已拦下，这里只是让攻击面可见）
            LoggerFactory.getLogger(AiGatewayController.class)
                    .warn("AI 网关匿名访问被拒: {}", ex.getMessage());
            throw ex;
        }
        if (!u.isTenant()) {
            throw new BizException(ErrorCode.FORBIDDEN, "AI 网关仅面向租户真人会话（平台账号不下业务单据）");
        }
        return u;
    }

    public record ConfirmRequestBody(String instructionId, String actionCode, Map<String, Object> args) {
    }

    public record RuleBody(String instruction, AiInspectRule rule) {
    }
}
