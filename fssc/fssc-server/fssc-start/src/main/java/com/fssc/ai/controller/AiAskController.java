package com.fssc.ai.controller;

import com.fssc.ai.AiAskService;
import com.fssc.ai.AiGatewayService;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.trace.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 人侧问数入口：挂在 /api/tenant/** 下复用真人会话鉴权。
 * 它只是 AIAssistant 到网关之间的编排壳——候选动作剪枝＋动作选择＋执行一律经
 * {@link AiGatewayService}，本类零业务判断。
 */
@RestController
@RequestMapping("/api/tenant/ai/ask")
@RequiredArgsConstructor
public class AiAskController {

    private final AiAskService askService;

    @PostMapping
    public Result<AiAskService.AskResult> ask(@RequestBody(required = false) Map<String, Object> body,
                                              HttpServletRequest http) {
        LoginUser u = UserContext.getRequired();
        return Result.success(askService.ask(requireQuestion(body), http, u,
                MDC.get(TraceIdFilter.MDC_KEY), sessionId(body)));
    }

    /**
     * 指令通道：自然语言 → 目录内动作。只读低风险立即执行；
     * 写／较高风险只出一张待确认票据，执行仍要另一真人背书后由前端调 /api/ai/gateway/execute。
     */
    @PostMapping("/instruct")
    public Result<AiAskService.InstructResult> instruct(@RequestBody(required = false) Map<String, Object> body,
                                                        HttpServletRequest http) {
        LoginUser u = UserContext.getRequired();
        return Result.success(askService.instruct(requireQuestion(body), http, u,
                MDC.get(TraceIdFilter.MDC_KEY), sessionId(body)));
    }

    /**
     * 入参判空（O-3）：本接口只认键名 {@code question}。原实现 {@code body.get("question")} 不判空，
     * 传成 {@code instruction} 之类别名时 {@code null} 会一路穿到下游触发 NPE，
     * 落到默认分支回 {@code code=500「系统异常」}——把"我把参数名写错了"显示成"服务端坏了"，
     * 与「路径不存在」那支同族，故一并在此显式回 400。
     */
    private static String requireQuestion(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("question");
        String q = raw == null ? null : String.valueOf(raw);
        if (q == null || q.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "缺少 question 字段：本接口入参只认键名 question（不是 instruction/prompt），"
                            + "示例：{\"question\":\"查一下本月待审批的报销单\"}");
        }
        return q;
    }

    /** 会话号：可选；不传则每轮独立（无跨轮继承），传了由服务端按 租户+用户 圈定。 */
    private static String sessionId(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("sessionId");
        return raw == null || String.valueOf(raw).isBlank() ? null : String.valueOf(raw).trim();
    }

    /** 动作发现（当前用户视角）：AI 的"眼睛"，只含本人有权限的只读 LOW 动作。 */
    @GetMapping("/actions")
    public Result<List<Map<String, Object>>> actions() {
        return Result.success(askService.discover(UserContext.getRequired()));
    }
}
