// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/** 采购域路由（采购订单/付款计划/收货单/三单匹配容差） */
export default [
  { path: 'purchase/order', component: () => import('../../views/purchase/PoOrder.vue'), meta: { title: '采购订单' } },
  { path: 'purchase/pay-plan', component: () => import('../../views/purchase/PayPlan.vue'), meta: { title: '采购付款计划' } },
  { path: 'purchase/receipt', component: () => import('../../views/purchase/PoReceipt.vue'), meta: { title: '收货单' } },
  // 三单匹配容差唯一权威收敛到 md_match_tolerance（pay 侧分级引擎，被 /pay/match 实际消费）。
  // 原采购侧 /match-tolerance 页（match_tolerance 公司级单条表）下线，旧深链一律重定向到 /pay/match-tolerance，
  // 杜绝「两套容差表/两套接口/两页同名」的双规则；菜单侧 /pay/match-tolerance 恢复授权属 DB 治理（转人工）。
  { path: 'match-tolerance', redirect: '/pay/match-tolerance' },
]
