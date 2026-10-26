// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 列表页行内审批（InlineActions 的 ✓ / ✗）统一入口。
 *
 * 取代原先 11 个列表页各写一遍的两处反模式：
 *   - 「确认审批通过？」confirm + 硬编码 comment:'审批通过'（意见等于没写，记录无价值）
 *   - 无校验的「请输入驳回原因」prompt（空串也放行）
 *
 * 页面接入三步：
 *   import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'
 *   const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: load })
 *   模板里挂一个 <ApprovalDialog ref="approvalRef" />
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { wfApi } from '../api'
import { useApprovalScope } from './useApprovalScope'

export function useApprovalDialog({ taskIdOf, reload }) {
  const approvalRef = ref(null)
  const { refresh } = useApprovalScope()

  async function run(row, decision) {
    if (!approvalRef.value) { ElMessage.error('审批弹窗未就绪，请刷新页面后重试'); return }
    const taskId = taskIdOf ? taskIdOf(row) : null
    if (!taskId) {
      ElMessage.warning('该单据不在您的待办中（可能已被他人办理），请刷新列表后重试')
      refresh()
      return
    }
    const res = await approvalRef.value.open({
      row, decision, taskId,
      // D-DEC-022：把审批请求交给弹窗执行 —— 成功才关窗；被服务端业务异常拒绝（如"禁止审批自己
      // 提交的单据""会计期间已结账"）时弹窗保持打开、意见保留、原因就地显示，可原地改后重试。
      // 原手感是先关窗再发请求，失败只剩一条飘过顶部的红条，审批人既丢了意见也不知卡在哪。
      submit: async (payload) => {
        // G2 传阅 / G4 自由驳回 / ①层审批项：弹窗返回的非空字段一并透传；
        // 三项都是"空即不生效"，故未使用这些能力时请求体与改造前逐字一致。
        await wfApi.approve({
          taskId,
          approved: payload.approved,
          comment: payload.comment,
          nextAssigneeUserId: payload.nextAssigneeUserId,
          ccUserIds: payload.ccUserIds || undefined,
          rejectToNodeKey: payload.rejectToNodeKey || undefined,
          approvalItemCode: payload.approvalItemCode || undefined,
        })
        ElMessage.success(payload.approved ? '已通过' : '已驳回')
        refresh() // 待办缓存立即对齐，避免已办结的行还挂着审批按钮
        if (reload) reload()
      },
    })
    if (!res) return // 用户取消，或提交失败后放弃（失败时弹窗不会 resolve）
  }

  return {
    approvalRef,
    handleApprove: (row) => run(row, 'approve'),
    handleReject: (row) => run(row, 'reject'),
  }
}
