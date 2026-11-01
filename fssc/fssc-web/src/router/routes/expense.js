// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/** 费用报账域路由（事前申请/报销/借款/报账工作台） */
export default [
  // 统一收单工作台（统一收单大厅）
  { path: 'requisition', redirect: '/requisition/mine' },
  // 报账工作台各页签拆为独立菜单（见下）；父路径重定向到首个 tab，不再渲染"汇总全部 tab"的 hub 页
  { path: 'requisition/mine', component: () => import('../../views/expense/RequisitionWorkbench.vue'), props: { tab: 'mine' }, meta: { title: '我的单据' } },
  { path: 'requisition/todo', component: () => import('../../views/expense/RequisitionWorkbench.vue'), props: { tab: 'todo' }, meta: { title: '我的待办' } },
  { path: 'requisition/done', component: () => import('../../views/expense/RequisitionWorkbench.vue'), props: { tab: 'done' }, meta: { title: '我的已办' } },
  { path: 'workbench', component: () => import('../../views/common/Workbench.vue'), meta: { title: '我的工作台' } },
  { path: 'expense', redirect: '/expense/claim' },
  // 各类型单据已有独立菜单（员工费用报销/差旅培训报销/对公支付等）；发起单据整合页冗余，重定向到员工费用报销
  { path: 'expense/create', redirect: '/expense/claim' },
  { path: 'expense/my', component: () => import('../../views/expense/Expense.vue'), props: { tab: 'my' }, meta: { title: '我的单据' } },
  // 孤儿路由 `expense/voucher` 已删除——Expense.vue 无 'voucher' 窗格，直链为空白页；
  // 凭证查看由单据工作台/BillWorkbench 的凭证抽屉（onViewVoucher）承载，无需独立菜单（sys_menu 亦无该路由）。
  // 员工报销类（需求文档 4.2）
  { path: 'expense/claim', component: () => import('../../views/expense/ClaimExpense.vue'), meta: { title: '员工费用报销' } },
  { path: 'expense/claim-travel', component: () => import('../../views/expense/ClaimTravel.vue'), meta: { title: '差旅培训报销' } },
  { path: 'expense/air-query', component: () => import('../../views/expense/AirTicketQuery.vue'), meta: { title: '机票结算申请' } },
  // 事前申请（原 meeting/travel/train 三个独立菜单收敛为一个工作区，成员页路由保留作深链）
  { path: 'expense/apply', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'preapply' }, meta: { title: '事前申请' } },
  { path: 'expense/apply/meeting', component: () => import('../../views/expense/ApplyMeeting.vue'), meta: { title: '会议申请' } },
  { path: 'expense/apply/travel', component: () => import('../../views/expense/ApplyTravel.vue'), meta: { title: '出差申请' } },
  { path: 'expense/apply/train', component: () => import('../../views/expense/ApplyTrain.vue'), meta: { title: '培训申请' } },
  // MF5-04: 通用事前申请深链路由已删除（孤儿路由）；PreApply 组件保留——被会议/出差/培训三个专用页复用
  // 借款 / 还款（需求文档 4.4 / 4.5）
  { path: 'expense/loan', component: () => import('../../views/expense/LoanApply.vue'), meta: { title: '借款管理' } },
  { path: 'expense/repay', component: () => import('../../views/expense/LoanApply.vue'), props: { mode: 'REPAY' }, meta: { title: '还款管理' } },
  { path: 'expense/flow', component: () => import('../../views/system/FlowConfig.vue'), meta: { title: '流程配置' } },
  { path: 'approval', component: () => import('../../views/approval/Approval.vue'), meta: { title: '审批工作台' } },
  // 审批流看板（BPMN 定义 + 分支 + 实例监控）
  { path: 'wf/board', redirect: '/wf/board/def' },
  // 审批流看板各页签拆为独立菜单（见下）；父路径重定向到首个 tab"流程定义"，不渲染汇总的 hub 页
  { path: 'wf/board/def', component: () => import('../../views/approval/ApprovalFlowBoard.vue'), props: { tab: 'def' }, meta: { title: '流程定义' } },
  { path: 'wf/board/inst', component: () => import('../../views/approval/ApprovalFlowBoard.vue'), props: { tab: 'inst' }, meta: { title: '流程实例监控' } },
  // D-1 观测归一：254 流程实例脱离通用配置组件，改指真实例查询面（筛选/分页/导出/干预同在 ApprovalFlowBoard）
  { path: 'wf/instance', component: () => import('../../views/approval/ApprovalFlowBoard.vue'), props: { tab: 'inst' }, meta: { title: '流程实例' } },
]
