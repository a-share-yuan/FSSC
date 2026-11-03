// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { store } from './api'
import { OPEN_PATHS, pathDenyReason, ensureMenuPaths } from './router/pathAccess'

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
import { i18n } from './i18n'

const routes = [
  { path: '/login', component: () => import('./views/common/Login.vue'), meta: { public: true } },
  // 平台管理员独立登录页（与企业员工登录页分离）
  { path: '/platform/login', component: () => import('./views/platform/PlatformLogin.vue'), meta: { public: true } },
  { path: '/profile', component: () => import('./views/common/Profile.vue'), meta: { title: i18n.global.t('router.main.Profile') } },
  // H5 独立移动端工作台（无侧边栏布局，窄屏专用）
  // 移动报销端 meta.keepAlive ＝ 交给 App.vue 的 <keep-alive>，从「提报/审批」返回
  // 「单据/待办」时列表与滚动位置、已填内容都在（原先每次返回都整页重建 ⇒ UX-06「数据会丢，
  // 而且不告诉你」）。本地草稿是第二重保险，keep-alive 被回收或刷新页面时接管。
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
      { path: 'dashboard', component: () => import('./views/dashboard/Dashboard.vue'), meta: { title: i18n.global.t('router.main.Report') } },
      // 角色化首页（用户下拉入口已下架，路由保留供直接访问；恢复入口的前置条件是补齐后端聚合规则）
      { path: 'role-dashboard', component: () => import('./views/dashboard/RoleDashboard.vue'), meta: { title: '角色化首页' } },
      // 流程监控大屏
      { path: 'flow-monitor', component: () => import('./views/monitor/FlowMonitor.vue'), meta: { title: i18n.global.t('router.main.Workflow') } },
      // 业务实时进度看板
      { path: 'biz-progress', component: () => import('./views/dashboard/BizProgressBoard.vue'), meta: { title: '业务进度看板' } },
      // 流程导航地图：7 大域端到端可点击流程（解"菜单多一头懵"），入口仅在我的工作台卡片，不新增侧边栏菜单
      { path: 'process-nav', component: () => import('./views/dashboard/ProcessNavMap.vue'), meta: { title: i18n.global.t('dashboard.processNavMap.Workflow') } },
      // 月结看板
      { path: 'month-end', component: () => import('./views/sharing/MonthEndBoard.vue'), meta: { title: '月结看板' } },
      // 权限治理：我的委托代理 —— 曾是「流程管理」下的一个页签，
      // 但委托代理属员工自助功能，而「流程管理」属系统配置域（已从全员基础角色收回），
      // 故拆为独立入口，保证普通员工仍能自助设置审批委托。
      { path: 'my/delegate', component: () => import('./views/common/MyDelegate.vue'), meta: { title: i18n.global.t('common.myDelegate.My') } },
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
  // 兜底: 未匹配路由显示 404 页面(整改 EXC-02, 原先是静默重定向至工作台)
  { path: '/:pathMatch(.*)*', component: () => import('./views/common/NotFound.vue'), meta: { title: '页面不存在' } },
]
const router = createRouter({ history: createWebHashHistory(), routes })

// 全站页面级权限拦截
// 背景：守卫只消费 meta.perms 的话，大量未声明的路由就成漏网 ⇒ 无权限账号可 URL 直达
// （如普通员工直达 /system/ext-interface，页面可渲染，仅靠页内接口 4030 兜底）。
// 判定链（任一命中即放行）：
//   ① 超管（*:*:* / isAdmin）放行；
//   ② meta.perms（路由自声明，最高优先级，历史规则不变）；
//   ③ ROUTE_PERMS[to.path]（sys_menu 权威映射：perms 多码任一 / feature_code 功能开关），
//      映射由 fssc 库 sys_menu(status=1) 生成，与后端菜单树过滤规则一致；
//   ④ 菜单树兜底：登录后 /tenant/system/menu/tree 返回的路径集合（角色-菜单授权判定，
//      覆盖映射表 menuOnly 路径）。拉取失败 fail-open（可用性优先，后端 API 权限校验仍在）。
// 放行例外：H5 端页面（深链详情由后端按单据参与人校验）、工作台（拦截回落点，必须可达）、
// 个人中心 / 流程导航地图（工作台卡片全员入口）。
// 「我的待办/已办/我发起的/我的抄送」属本人视角自助页：对应后端端点（/tenant/workbench/**、
// wf/query/cc/mine）按当前登录人过滤、不设权限码；其中 todo/done/cc 的 sys_menu 行已软删，
// 菜单树兜底永不命中 ⇒ 非超管一律被拦。页面级无权可收，数据面由后端本人视角兜底。
function denyToWorkbench(msg) {
  ElMessage.warning(msg || '无权限访问该页面，请联系管理员')
  return '/workbench'
}

router.beforeEach(async (to) => {
  // 已登录再访登录页——直接回工作台并提示（避免二次登录后会话被顶）
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
  // 页面级权限判定：判据在 router/pathAccess.js，与页内入口卡共用同一把钥匙
  await ensureMenuPaths()
  const deny = pathDenyReason(to.path, to.meta)
  if (!deny) return true
  return denyToWorkbench(deny === 'feature' ? '当前账号未开通该功能，已返回工作台' : undefined)
})

export default router
