/** 流程中心（跨域统一流程门户）路由：流程概览 / 我的抄送 / 已办撤销预览 */
export default [
  { path: 'flow-center', component: () => import('../../views/workflow/FlowCenter.vue'), meta: { title: '流程概览' } },
  { path: 'flow-center/cc', component: () => import('../../views/workflow/MyCc.vue'), meta: { title: '我的抄送' } },
  // P3 G3：已办撤销回原节点 —— 只读预览（执行面未开放，Flowable 8 无 ProcessInstanceModification）
  { path: 'flow-center/withdraw-preview', component: () => import('../../views/workflow/WithdrawPreview.vue'), meta: { title: '已办撤销预览' } },
  // G3（2026-09-24）流程日志中心（/api/tenant/wf/process-log/**）+ 归档预览（/wf/perf/archive-preview）
  { path: 'flow-center/log', component: () => import('../../views/workflow/FlowLogCenter.vue'), meta: { title: '流程日志中心' } },
  // G3 流程批量移交（/api/tenant/wf/handover/**）
  { path: 'flow-center/handover', component: () => import('../../views/workflow/Handover.vue'), meta: { title: '流程移交' } },
]
