// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.UserContext;
import com.fssc.workflow.service.WfEngineFacade;
import org.springframework.validation.annotation.Validated;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量审批后端接口。
 *
 * <p>POST /api/tenant/workflow/task/batch-approve，请求体：
 * <pre>{ "taskIds": ["...", "..."], "comment": "同意" }</pre>
 *
 * <p>逐条复用现有单任务审批逻辑（{@link WfEngineFacade#complete(String, boolean, String, Long)}，
 * 与 /api/tenant/wf/approve 完全同链路同权鉴权：登录校验 + 节点办理人/候选人/受托人守卫 + 自审禁止）；
 * 逐条独立事务/独立 try-catch，单条失败不影响其余，逐条返回失败原因。
 *
 * <p>响应：{@code { "code": 0, "msg": "成功", "data": { "success": 2, "fail": [ {"taskId": "...", "reason": "..."} ] } }}
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/workflow/task")
@RequiredArgsConstructor
@Validated
public class WfTaskBatchController {

    private final WfEngineFacade wfEngineFacade;

    /** 单次批量审批条数硬上限，防止超大请求拖垮审批链路 */
    private static final int MAX_BATCH_SIZE = 100;

    @OperLog(module = "WF_TASK", operType = "UPDATE", desc = "批量审批")
    @PostMapping("/batch-approve")
    public Result<Map<String, Object>> batchApprove(@RequestBody BatchApproveReq req) {
        if (req == null || req.getTaskIds() == null || req.getTaskIds().isEmpty()) {
            return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "taskIds 不能为空");
        }
        if (req.getTaskIds().size() > MAX_BATCH_SIZE) {
            return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                    "单次批量审批不能超过 " + MAX_BATCH_SIZE + " 条");
        }
        Long operUserId = UserContext.getRequired().getUserId();
        boolean approved = req.getApproved() == null || req.getApproved();
        if (!approved && (req.getComment() == null || req.getComment().trim().length() < 2)) {
            return Result.fail(ErrorCode.PARAM_INVALID.getCode(),
                    "批量驳回必须填写原因（不少于 2 个字）");
        }
        // 通过动作可不留意见，缺省补「批量审批」以便记录；驳回意见一律原样透传，
        // 绝不由后端代填（否则审批历史里会出现并非审批人写的「批量审批」驳回理由）。
        String comment = approved && (req.getComment() == null || req.getComment().isBlank())
                ? "批量审批" : req.getComment();

        int success = 0;
        List<Map<String, Object>> failList = new ArrayList<>();
        for (String taskId : req.getTaskIds()) {
            Map<String, Object> fail = null;
            if (taskId == null || taskId.isBlank()) {
                fail = failItem(taskId, "taskId 不能为空");
            } else {
                try {
                    // 与单任务审批 /api/tenant/wf/approve 完全同权：complete 内含
                    // 办理人/候选人/受托人守卫（canOperate）+ 自审禁止开关，逐条事务提交
                    wfEngineFacade.complete(taskId.trim(), approved, comment, operUserId);
                    success++;
                } catch (Exception ex) {
                    log.warn("批量审批单条失败 taskId={}: {}", taskId, ex.getMessage());
                    fail = failItem(taskId, ex.getMessage() == null || ex.getMessage().isBlank()
                            ? ex.getClass().getSimpleName() : ex.getMessage());
                }
            }
            if (fail != null) {
                failList.add(fail);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", success);
        data.put("fail", failList);
        return Result.success(data);
    }

    private Map<String, Object> failItem(String taskId, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", taskId);
        m.put("reason", reason);
        return m;
    }

    @Data
    public static class BatchApproveReq {
        /** 待审批任务ID列表（Flowable taskId） */
        private List<String> taskIds;
        /** 审批意见，缺省「批量审批」 */
        private String comment;
        /** 是否通过，缺省 true（批量通过）；false = 批量驳回 */
        private Boolean approved;
    }
}
