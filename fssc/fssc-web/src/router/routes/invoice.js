// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/** 发票域路由（发票管理/进项认证）
 *  G13: 菜单按进项/销项分组，新增 input-pool / output-pool 深链路由
 *  通过 direction prop 预设发票池过滤方向
 */
export default [
  { path: 'invoice', redirect: '/invoice/query' },
  // 发票域路由补 meta.perms（router.js 全局守卫 beforeEach 统一消费；后端 @RequiresPerm 不动）。
  // 无对应权限的账号 URL 直达时挡回工作台并提示，不再渲染空壳列表。
  { path: 'invoice/certify', component: () => import('../../views/invoice/InvoiceCertify.vue'), meta: { title: '进项发票认证', perms: ['inv:certify'] } },
  // RA-PAGE-21 2.7：/invoice/query 为发票工作台宿主（全页签 pool/create/issue/verify/certify 门控），不传 tab；
  // 其余为成员深链路由（单页模式），菜单仅保留本宿主。
  { path: 'invoice/query', component: () => import('../../views/invoice/Invoice.vue'), meta: { title: '发票管理', perms: ['inv:list', 'inv:create', 'inv:issue', 'inv:verify:list', 'inv:certify'] } },
  // G13: 进项/销项发票池深链（direction prop 预设过滤）
  { path: 'invoice/input-pool', component: () => import('../../views/invoice/Invoice.vue'), props: { tab: 'pool', direction: 'INPUT' }, meta: { title: '进项发票池', perms: ['inv:list'] } },
  { path: 'invoice/output-pool', component: () => import('../../views/invoice/Invoice.vue'), props: { tab: 'pool', direction: 'OUTPUT' }, meta: { title: '销项发票池', perms: ['inv:list'] } },
  { path: 'invoice/create', component: () => import('../../views/invoice/Invoice.vue'), props: { tab: 'create' }, meta: { title: '发票补录', perms: ['inv:create'] } },
  // OTC02 开票审批流(INVOICE_ISSUE)触发入口：登记销售发票并发起开票审批（此前 issue tab 组件已有但无路由暴露，UI 无"提交开票申请"入口）
  { path: 'invoice/issue', component: () => import('../../views/invoice/Invoice.vue'), props: { tab: 'issue' }, meta: { title: '销售开票', perms: ['inv:issue'] } },
  { path: 'invoice/verify', component: () => import('../../views/invoice/Invoice.vue'), props: { tab: 'verify' }, meta: { title: '发票验真记录', perms: ['inv:verify:list', 'inv:verify'] } },
]
