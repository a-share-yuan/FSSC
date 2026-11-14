package com.fssc.workflow.ops.dto;

import java.util.List;

/**
 * G3流程批量移交请求。
 *
 * @param fromUserId 原办理人（不传 {@code taskIds} 时，按其名下在办任务批量移交）
 * @param toUserId 接收人（必填）
 * @param taskIds 指定移交的任务 ID（Flowable taskId）；为空则按 {@code fromUserId} 反查在办任务
 * @param reason 移交原因（必填：改派通道要求写原因，离职/换岗/工作交接等）
 * @param remark 备注（可选，落 op_transfer_log.remark）
 */
public record HandoverRequest(
        Long fromUserId,
        Long toUserId,
        List<String> taskIds,
        String reason,
        String remark) {
}
