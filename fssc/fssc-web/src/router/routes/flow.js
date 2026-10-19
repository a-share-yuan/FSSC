/** 应收应付蓝图落地域路由（付款服务申请/月末关账/开票链/应收服务申请/核销工作台/票据/坏账/月结/报表/基础配置） */
export default [
  // 方案 A：蓝图 10 页 → 两域工作台（菜单挂这两条；下列旧子路由保留＝历史 URL/bizEntry 深链不断）
  { path: 'arflow', component: () => import('../../views/flow/ArFlowWorkspace.vue'), meta: { title: '应收工作台' } },
  { path: 'apflow', component: () => import('../../views/flow/ApFlowWorkspace.vue'), meta: { title: '应付工作台' } },
  { path: 'apflow/ap-request', component: () => import('../../views/flow/ApRequestBoard.vue'), meta: { title: '付款服务申请' } },
  { path: 'apflow/ap-month-close', component: () => import('../../views/flow/CloseWorkbench.vue'), meta: { title: '统一关账工作台' } },
  { path: 'arflow/billing', component: () => import('../../views/flow/ArBillingBoard.vue'), meta: { title: '开票链工作台' } },
  { path: 'arflow/claims', component: () => import('../../views/flow/ArClaimBoard.vue'), meta: { title: '应收服务申请' } },
  // 蓝图缺口落地（核对报告 P0~P2）
  { path: 'arflow/writeoff', component: () => import('../../views/flow/ArWriteoffBoard.vue'), meta: { title: '核销工作台' } },
  { path: 'arflow/notes', component: () => import('../../views/flow/ArNoteBoard.vue'), meta: { title: '票据管理' } },
  { path: 'arflow/bad-debt', component: () => import('../../views/flow/ArBadDebtBoard.vue'), meta: { title: '坏账管理' } },
  { path: 'arflow/ar-month-close', component: () => import('../../views/flow/CloseWorkbench.vue'), meta: { title: '统一关账工作台' } },
  { path: 'arflow/reports', component: () => import('../../views/flow/ArApReportBoard.vue'), meta: { title: '应收统计报表' } },
  { path: 'arflow/base-config', component: () => import('../../views/flow/ArBaseConfigPage.vue'), meta: { title: '汇率与核对容差' } },
  { path: 'arflow/collab', component: () => import('../../views/flow/ArApCollabBoard.vue'), meta: { title: 'SCP/CCP协同打通' } },
  // BCP 门户行级异议审核（供应商 AP / 客户 AR 提起的数量、单价异议）。
  // 独立于 apflow/arflow：两个域的岗都要用，故不按域归属路径，菜单由 sys_menu 种子决定挂点。
  { path: 'bcp-objection', component: () => import('../../views/flow/BcpObjectionBoard.vue'),
    meta: { title: 'BCP异议审核', perms: ['bcp:objection:list'] } },
  // 后端 /api/tenant/wf/timeout/** 三端点早有、界面无（D-V4R-62）；path 与测试手册写的兜底地址一致
  { path: 'wf/timeout/stats', component: () => import('../../views/flow/WfTimeoutStatsBoard.vue'), meta: { title: '超时升级统计' } },
]
