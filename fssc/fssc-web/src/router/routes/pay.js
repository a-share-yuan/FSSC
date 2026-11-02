// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/** 付款与对账域路由（出纳/三单匹配/对公支付/供应商对账） */
import { i18n } from '../../i18n'
export default [
  { path: 'pay', component: () => import('../../views/pay/Pay.vue'), meta: { title: i18n.global.t('router.pay.Cashier') } },
  // 对公三单匹配（匹配操作+容差收敛为一个工作区；/pay/match-tolerance 深链保留）
  { path: 'pay/match', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'match' }, meta: { title: '对公三单匹配' } },
  { path: 'pay/match-tolerance', component: () => import('../../views/pay/MatchTolerance.vue'), meta: { title: '三单匹配容差' } },
  { path: 'pay/recon', component: () => import('../../views/pay/SupplierRecon.vue'), meta: { title: '应付对账（函）' } },
  { path: 'corp-pay', redirect: '/corp-pay/prepay' },
  // 一 TAB = 一菜单：对公支付各类型拆为独立菜单（CorpPay.vue 深链隐藏类型切换按钮）；父路径重定向到首个类型
  { path: 'corp-pay/prepay', component: () => import('../../views/pay/CorpPay.vue'), props: { subType: 'PREPAY' }, meta: { title: '费用预付款' } },
  { path: 'corp-pay/settle', component: () => import('../../views/pay/CorpPay.vue'), props: { subType: 'SETTLE' }, meta: { title: '对公结算' } },
  // (b)：代收代付(AGENCY)/机票结算(AIR) 两子类型与深链路由一并下线——
  // BillRouteRegistry/ExpenseWfEventListener 零登记（一旦写入数据库会并档到 EXP_PAY 链，规则全错），且库内 0 行无历史包袱。
  // 菜单侧 261/262/263 本为 del_flag=1 软删（深链可直达为遗留后门，统一治理面转人工登记）。
  // 对公付款凭证与总账落账
  { path: 'corp-pay/voucher', component: () => import('../../views/pay/CorpPayVoucher.vue'), meta: { title: '对公付款凭证·总账' } },
  { path: 'contract/pay-query', component: () => import('../../views/pay/ContractPay.vue'), meta: { title: '合同付款查询' } },
]
