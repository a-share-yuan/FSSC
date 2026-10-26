// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.ai.AiGatewayService;
import com.fssc.ai.entity.AiActionConfirm;
import com.fssc.ai.mapper.AiActionConfirmMapper;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 确认票据的人侧入口（挂在 /api/tenant/** 下，与真人链路同鉴权同审计）。
 *
 * <p>设计裁决点：AI 网关可以"申请"票据（POST /api/ai/gateway/confirm-request），
 * 但确认动作只能由真人在这里做 —— 决策属人（方案 §6）。
 * 权限码 ai:confirm:approve 需入角色目录（待裁决项，见 P0-修复说明与待裁决清单）。
 */
@RestController
@RequestMapping("/api/tenant/ai/confirm")
@RequiredArgsConstructor
public class AiConfirmController {

    private final AiGatewayService gatewayService;
    private final AiActionConfirmMapper confirmMapper;

    /**
     * 待确认队列：本人可见（同租户），确认人不应看到自己申请的票据为可确认态（服务层已挡自确）。
     *
     * <p>D-N13：过期即废是票据的既有语义（{@code useConfirmTicket} 一直是这么判的），
     * 但读侧没跟上 —— 过期票长期以 PENDING 挂在队列里，确认人面对一堆无法放行的死票。
     * 这里只过滤不回写状态：审计表 insert-only，"死票"由读取时刻的 TTL 决定即可。
     */
    @GetMapping("/pending")
    @RequiresPerm("ai:confirm:list")
    public Result<List<AiActionConfirm>> pending() {
        LoginUser u = UserContext.getRequired();
        return Result.success(confirmMapper.selectList(new LambdaQueryWrapper<AiActionConfirm>()
                .eq(AiActionConfirm::getStatus, "PENDING")
                .gt(AiActionConfirm::getExpireAt, LocalDateTime.now())
                .ne(AiActionConfirm::getApplicantUserId, u.getUserId())
                .orderByDesc(AiActionConfirm::getId)));
    }

    /**
     * 我申请的票据（含各状态）：下令侧轮询用 —— 出票后不再只能凭 confirmId 盲等（D-N05/A-4 补配）。
     * 无 {@code @RequiresPerm}：可见范围由 applicant_user_id=本人 天然圈死，属"个人域读"，不扩权。
     * 未过期却仍未消耗的票按 TTL 事实回显为 EXPIRED（只改写返回值、不写入数据库），否则前端轮询链会永远等一张死票。
     */
    @GetMapping("/mine")
    public Result<List<AiActionConfirm>> mine() {
        LoginUser u = UserContext.getRequired();
        List<AiActionConfirm> rows = confirmMapper.selectList(new LambdaQueryWrapper<AiActionConfirm>()
                .eq(AiActionConfirm::getApplicantUserId, u.getUserId())
                .orderByDesc(AiActionConfirm::getId)
                .last("limit 20"));
        LocalDateTime now = LocalDateTime.now();
        rows.forEach(r -> {
            if (("PENDING".equals(r.getStatus()) || "CONFIRMED".equals(r.getStatus()))
                    && r.getExpireAt() != null && r.getExpireAt().isBefore(now)) {
                r.setStatus("EXPIRED");
            }
        });
        return Result.success(rows);
    }

    @PostMapping("/{id}")
    @RequiresPerm("ai:confirm:approve")
    @OperLog(module = "AI治理", operType = "APPROVE", desc = "确认/驳回 AI 高风险动作票据")
    public Result<AiActionConfirm> confirm(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return Result.success(gatewayService.confirmTicket(id, approve, reason, UserContext.getRequired()));
    }

    /**
     * 申请人撤回自己尚未被确认的票（会话修正「改成1200」、或用户主动取消）。
     * 无 {@code @RequiresPerm}：可见/可动范围由 applicant_user_id=本人 圈死，属个人域操作。
     */
    @PostMapping("/{id}/cancel")
    @OperLog(module = "AI治理", operType = "UPDATE", desc = "申请人撤回 AI 动作票据")
    public Result<AiActionConfirm> cancel(@PathVariable Long id) {
        return Result.success(gatewayService.cancelTicket(id, "申请人撤回", UserContext.getRequired()));
    }
}
