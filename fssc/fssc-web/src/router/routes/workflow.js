// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/** 流程中心（跨域统一流程门户）路由：流程概览 / 我的抄送 / 已办撤销预览 */
import { i18n } from '../../i18n'
export default [
  { path: 'flow-center', component: () => import('../../views/workflow/FlowCenter.vue'), meta: { title: '流程概览' } },
  { path: 'flow-center/cc', component: () => import('../../views/workflow/MyCc.vue'), meta: { title: i18n.global.t('workflow.myCc.My') } },
  // 已办撤销回原节点 —— 只读预览（执行面未开放，Flowable 8 无 ProcessInstanceModification）
  { path: 'flow-center/withdraw-preview', component: () => import('../../views/workflow/WithdrawPreview.vue'), meta: { title: i18n.global.t('workflow.withdrawPreview.Completed') } },
  // G3流程日志中心（/api/tenant/wf/process-log/**）+ 归档预览（/wf/perf/archive-preview）
  { path: 'flow-center/log', component: () => import('../../views/workflow/FlowLogCenter.vue'), meta: { title: i18n.global.t('workflow.flowLog.Workflow') } },
  // G3 流程批量移交（/api/tenant/wf/handover/**）
  { path: 'flow-center/handover', component: () => import('../../views/workflow/Handover.vue'), meta: { title: i18n.global.t('workflow.handover.Workflow') } },
]
