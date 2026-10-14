// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { store } from './api'
import { hasAnyPerm } from './utils/perm'

// 按业务域拆分的路由模块
import expenseRoutes from './router/routes/expense'
import payRoutes from './router/routes/pay'
import purchaseRoutes from './router/routes/purchase'
import invoiceRoutes from './router/routes/invoice'
import sharingRoutes from './router/routes/sharing'
import systemRoutes from './router/routes/system'
import financeRoutes from './router/routes/finance'
import flowRoutes from './router/routes/flow'
import returnsRoutes from './router/routes/returns'
import workflowRoutes from './router/routes/workflow'

const routes = [
  { path: '/login', component: () => import('./views/common/Login.vue'), meta: { public: true } },
  // 平台管理员独立登录页（与企业员工登录页分离）
  { path: '/platform/login', component: () => import('./views/platform/PlatformLogin.vue'), meta: { public: true } },
  { path: '/profile', component: () => import('./views/common/Profile.vue'), meta: { title: '个人中心' } },
  // H5 独立移动端工作台（无侧边栏布局，窄屏专用）
  // 移动报销端 P0-8：meta.keepAlive ＝ 交给 App.vue 的 <keep-alive>，从「提报/审批」返回
  // 「单据/待办」时列表与滚动位置、已填内容都在（改造前每次返回都整页重建 ⇒ UX-06「数据会丢，
  // 而且不告诉你」）。本地草稿（P1-4）是第二重保险，keep-alive 被回收或刷新页面时接管。
  {
    path: '/h5',
    component: () => import('./views/h5/H5Workbench.vue'),
    meta: { keepAlive: true, terminal: 'H5' },
  },
  // H5 子页面：审批详情 / 报账创建
  { path: '/h5/approval/:id', component: () => import('./views/h5/H5ApprovalDetail.vue'), meta: { keepAlive: true, terminal: 'H5' } },
  { path: '/h5/expense/new', component: () => import('./views/h5/H5ExpenseCreate.vue'), meta: { keepAlive: true, terminal: 'H5' } },
  { path: '/h5/expense/:id/edit', component: () => import('./views/h5/H5ExpenseCreate.vue'), meta: { keepAlive: true, terminal: 'H5' } },
  // 平台运营控制台：独立整页（平台 Token 禁入 /api/tenant/**，无法复用租户 Layout 菜单树）
  { path: '/platform/console', component: () => import('./views/platform/PlatformConsole.vue'), meta: { title: '平台控制台', platform: true } },
  {
    path: '/',
    component: () => import('./views/common/Layout.vue'),
    children: [
      { path: '', redirect: '/workbench' }, // USG-01: 默认落地工作台(全员可用),报表看板走菜单按权限进入
      { path: 'dashboard', component: () => import('./views/dashboard/Dashboard.vue'), meta: { title: '报表看板' } },
      // P0-5 角色化首页（D-DEC-302：用户下拉入口已下架，路由保留供直接访问；恢复入口的前置条件是补齐后端聚合口径）
      { path: 'role-dashboard', component: () => import('./views/dashboard/RoleDashboard.vue'), meta: { title: '角色化首页' } },
      // P0-4 流程监控大屏
      { path: 'flow-monitor', component: () => import('./views/monitor/FlowMonitor.vue'), meta: { title: '流程监控大屏' } },
      // P0-7 业务实时进度看板
      { path: 'biz-progress', component: () => import('./views/dashboard/BizProgressBoard.vue'), meta: { title: '业务进度看板' } },
      // R4-09 流程导航地图：7 大域端到端可点击流程（解"菜单多一头懵"），入口仅在我的工作台卡片，不新增侧边栏菜单
      { path: 'process-nav', component: () => import('./views/dashboard/ProcessNavMap.vue'), meta: { title: '流程导航地图' } },
      // P2-6 月结看板
      { path: 'month-end', component: () => import('./views/sharing/MonthEndBoard.vue'), meta: { title: '月结看板' } },
      // P0-4 权限治理：我的委托代理 —— 原为「流程管理」下的一个页签，
      // 但委托代理属员工自助功能，而「流程管理」属系统配置域（已从全员基础角色收回），
      // 故拆为独立入口，保证普通员工仍能自助设置审批委托。
      { path: 'my/delegate', component: () => import('./views/common/MyDelegate.vue'), meta: { title: '我的委托代理' } },
      // 按业务域聚合子路由
      ...expenseRoutes,
      ...payRoutes,
      ...purchaseRoutes,
      ...invoiceRoutes,
      ...sharingRoutes,
      ...systemRoutes,
      ...financeRoutes,
      ...flowRoutes,
      ...returnsRoutes,
      ...workflowRoutes,
    ],
  },
  // 兜底: 未匹配路由显示 404 页面(整改 EXC-02, 原为静默重定向至工作台)
  { path: '/:pathMatch(.*)*', component: () => import('./views/common/NotFound.vue'), meta: { title: '页面不存在' } },
]
const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  // R5-D-053：已登录再访登录页——直接回工作台并提示（避免二次登录后会话被顶）
  if ((to.path === '/login' || to.path === '/platform/login') && store.token) {
    ElMessage.warning('已登录，已跳转工作台')
    return '/workbench'
  }
  if (!to.meta.public && !store.token) return '/login'
  // 平台专属页（/platform/console）：仅 subjectType=PLATFORM 可见；租户用户回工作台
  if (to.meta.platform && store.user?.subjectType !== 'PLATFORM') {
    ElMessage.warning('该页面仅平台管理员可用，已返回工作台')
    return store.user ? '/workbench' : '/login'
  }
  // R5-D-251/258②：全局消费 meta.perms —— 此前守卫只看 public/token/platform，
  // 路由上声明的 perms 是死元数据（finance.js 亦有注释确认），无权限账号可直达页面，
  // 再被页内每个请求连环 4030 顶回。这里做页面级前置闸：未命中任一码即挡回工作台并提示。
  const need = to.meta?.perms
  if (Array.isArray(need) && need.length && !hasAnyPerm(need)) {
    ElMessage.warning('无权限访问该页面，请联系管理员')
    return '/workbench'
  }
  return true
})

export default router
