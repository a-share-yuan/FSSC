// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.controller;

import com.fssc.ai.AiAskService;
import com.fssc.ai.entity.AiFeedback;
import com.fssc.ai.mapper.AiFeedbackMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 运营面（P0-8）：能力地图、健康检查、答案反馈。
 *
 * <p>三者都不改变执行面：技能地图会被权限剪枝；健康检查不返回任何密钥；
 * 反馈只写 ai_feedback 供评测，不参与任何放行判断。
 */
@RestController
@RequestMapping("/api/tenant/ai")
@RequiredArgsConstructor
public class AiInsightController {

    private final AiAskService askService;
    private final AiFeedbackMapper feedbackMapper;

    @GetMapping("/skills")
    public Result<List<Map<String, Object>>> skills() {
        return Result.success(askService.skills(UserContext.getRequired()));
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(askService.health());
    }

    @PostMapping("/feedback")
    public Result<Long> feedback(@RequestBody(required = false) Map<String, Object> body) {
        LoginUser u = UserContext.getRequired();
        int rating = intOf(body == null ? null : body.get("rating"), 0);
        if (rating != 1 && rating != -1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "rating 只接受 1（）或 -1（）");
        }
        AiFeedback f = new AiFeedback();
        f.setTenantId(u.getTenantId());
        f.setUserId(u.getUserId());
        f.setTraceId(text(body, "traceId", 64));
        f.setSessionId(text(body, "sessionId", 64));
        f.setSkill(text(body, "skill", 64));
        f.setQuestion(text(body, "question", 1000));
        f.setRating(rating);
        f.setReason(text(body, "reason", 200));
        f.setAnswerExcerpt(text(body, "answer", 500));
        f.setCreateTime(LocalDateTime.now());
        feedbackMapper.insert(f);
        return Result.success(f.getId());
    }

    private static int intOf(Object v, int fallback) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignore) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String text(Map<String, Object> body, String key, int max) {
        Object v = body == null ? null : body.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
