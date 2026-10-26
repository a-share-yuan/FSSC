// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.ops.dto.HandoverRequest;
import com.fssc.workflow.ops.entity.OpTransferLog;
import com.fssc.workflow.ops.service.TaskHandoverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * G3（2026-09-24）流程批量移交 ＋ 移交日志（{@code /api/tenant/wf/handover/**}）。
 *
 * <p>典型场景：员工离职/换岗，把名下在办审批任务批量交给接手人。逐单独立成败，
 * 失败明细照实返回并落 {@code op_transfer_log.failed_detail}（见 {@link TaskHandoverService} 类注释）。
 *
 * <p>权限：G4 治理三权分立后归<b>流程管理中心</b>——{@code wf:manage:intervene}（移交/改派/在途干预）
 * 与 {@code wf:manage:instance}（管理读视图）。同时保留旧码 {@code wf:task:reassign} /
 * {@code wf:instance:list}（多码 OR），故历史授权（IT_ADMIN）上线后不会 403；
 * 新码授权到位后再摘旧码，避免"因新造权限码导致大面积 403"。
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/wf/handover")
@RequiredArgsConstructor
@Validated
public class WfHandoverController {

    private final TaskHandoverService handoverService;

    /**
     * 批量移交：{@code taskIds} 与 {@code fromUserId} 二选一（都不传则报错）。
     * 操作人取当前登录用户；操作人即原办理人时走转办通道，否则走改派通道。
     */
    @RequiresPerm({"wf:manage:intervene", "wf:task:reassign"})
    @OperLog(module = "WF_OPS", operType = "UPDATE", desc = "流程批量移交")
    @PostMapping("/execute")
    public Result<Map<String, Object>> execute(@RequestBody HandoverRequest req) {
        return Result.success(handoverService.handover(req, UserContext.getRequired().getUserId()));
    }

    /** 移交日志分页（可按原办理人/接收人过滤） */
    @RequiresPerm({"wf:manage:instance", "wf:instance:list"})
    @GetMapping("/log/page")
    public Result<PageResult<OpTransferLog>> logPage(PageQuery q,
                                                    @RequestParam(required = false) Long fromUserId,
                                                    @RequestParam(required = false) Long toUserId) {
        return Result.success(handoverService.logPage(q, fromUserId, toUserId));
    }
}
