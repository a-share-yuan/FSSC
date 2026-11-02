// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/** 财务核算域路由（总账/资产/应收/资金/银行/档案/合同/报表） */
import { i18n } from '../../i18n'
export default [
  // FA 资产与存货：父路径重定向到首个 tab，不渲染汇总的 hub 页
  { path: 'asset', redirect: '/asset/asset' },
  { path: 'asset/asset', component: () => import('../../views/asset/AssetBoard.vue'), props: { tab: 'asset' }, meta: { title: i18n.global.t('router.finance.Asset') } },
  { path: 'asset/category', component: () => import('../../views/asset/AssetBoard.vue'), props: { tab: 'category' }, meta: { title: i18n.global.t('router.finance.Asset2') } },
  { path: 'asset/inventory', component: () => import('../../views/asset/AssetBoard.vue'), props: { tab: 'inventory' }, meta: { title: i18n.global.t('router.finance.Inventory') } },
  { path: 'asset/taxDiff', component: () => import('../../views/asset/AssetBoard.vue'), props: { tab: 'taxDiff' }, meta: { title: i18n.global.t('router.finance.Tax') } },
  { path: 'asset/depr-batch', component: () => import('../../views/asset/DeprBatch.vue'), meta: { title: '折旧计提批' } },
  // TR 资金管理：父路径重定向到首个 tab，不渲染汇总的 hub 页
  // treasury 七路由补 meta.perms（router.js 全局守卫消费；读码与后端 @RequiresPerm 逐一对齐：
  // treasury:fundplan/creditbill/transfer/billsettle/position、bank=md:bankaccount:list、bankRecon=bank:reconcile:list）。
  // 无权限账号 URL 直达不再渲染带写按钮的空壳；后端守卫保持现状。
  { path: 'treasury', redirect: '/treasury/plan' },
  { path: 'treasury/plan', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'plan' }, meta: { title: '资金计划', perms: ['treasury:fundplan:list'] } },
  { path: 'treasury/credit', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'credit' }, meta: { title: '资金收款', perms: ['treasury:creditbill:list'] } },
  { path: 'treasury/transfer', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'transfer' }, meta: { title: '资金调拨', perms: ['treasury:transfer:list'] } },
  { path: 'treasury/settle', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'settle' }, meta: { title: '票据结算', perms: ['treasury:billsettle:list'] } },
  { path: 'treasury/bank', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'bank' }, meta: { title: '银行主数据', perms: ['md:bankaccount:list'] } },
  { path: 'treasury/bankRecon', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'bankRecon' }, meta: { title: '银行对账', perms: ['bank:reconcile:list'] } },
  { path: 'treasury/position', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'position' }, meta: { title: '资金头寸', perms: ['treasury:position:list', 'treasury:fundplan:list', 'treasury:creditbill:list'] } },
  // OTC 应收管理：父路径重定向到首个 tab，不渲染汇总的 hub 页
  { path: 'receivable', redirect: '/receivable/receivable' },
  { path: 'receivable/receivable', component: () => import('../../views/receivable/ReceivableBoard.vue'), props: { tab: 'receivable' }, meta: { title: i18n.global.t('router.finance.Receivable') } },
  { path: 'receivable/receipt', component: () => import('../../views/receivable/ReceivableBoard.vue'), props: { tab: 'receipt' }, meta: { title: i18n.global.t('router.finance.Receipt') } },
  { path: 'receivable/collect', component: () => import('../../views/receivable/ReceivableBoard.vue'), props: { tab: 'collect' }, meta: { title: i18n.global.t('router.finance.Collection') } },
  { path: 'receivable/badDebt', component: () => import('../../views/receivable/ReceivableBoard.vue'), props: { tab: 'badDebt' }, meta: { title: i18n.global.t('router.finance.Bad') } },
  // FM 档案：档案管理（/sharing/archive）四个页签＝归档整理/档案库/借阅利用/鉴定销毁；
  // 以下为单页深链，旧书签仍可落地
  { path: 'archive', redirect: '/archive/access' },
  { path: 'archive/access', component: () => import('../../views/archive/ArchiveBoard.vue'), props: { tab: 'access' }, meta: { title: i18n.global.t('router.finance.Retrieval') } },
  { path: 'archive/destroy', component: () => import('../../views/archive/ArchiveBoard.vue'), props: { tab: 'destroy' }, meta: { title: i18n.global.t('router.finance.Appraise') } },
  // 单据流转＝共享运营派单作业，已移入「共享运营配置」页签；旧深链改道，不再挂在档案侧
  { path: 'archive/dispatch', redirect: '/cfg/sharing?tab=dispatch' },
  { path: 'archive/archive', component: () => import('../../views/archive/ArchiveBoard.vue'), props: { tab: 'archive' }, meta: { title: '档案目录' } },
  // 合同（台账+付款查询收敛为一个工作区；/contract/pay-query 深链保留）
  { path: 'contract', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'contract' }, meta: { title: i18n.global.t('contract.main.Contract') } },
  { path: 'contract/type', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'contractType' }, meta: { title: i18n.global.t('router.finance.Contract') } },
  // 总账：父路径重定向到首个 tab，不渲染汇总的 hub 页
  // /op/gl 暂不并为工作区：总账分录、科目余额、试算平衡三个页签的权限码不是互相的子集，
  // 直接并会砍访问或增权（总账分录还带 gl:entry:list、md:subject:list）——先裁授权面再并。
  { path: 'op/gl', redirect: '/op/gl/entry' },
  // 总账各页签拆为独立菜单（见下）
  { path: 'op/gl/entry', component: () => import('../../views/gl/GlLedger.vue'), props: { tab: 'entry' }, meta: { title: '总账分录' } },
  { path: 'op/gl/balance', component: () => import('../../views/gl/GlLedger.vue'), props: { tab: 'balance' }, meta: { title: '科目余额表' } },
  { path: 'op/gl/trial', component: () => import('../../views/gl/GlLedger.vue'), props: { tab: 'trial' }, meta: { title: i18n.global.t('router.finance.Trial') } },
  // 凭证台账（GL 凭证列表：过账状态 + 批量过账 + 制审分离）
  { path: 'op/gl/voucher', component: () => import('../../views/gl/GlLedger.vue'), props: { tab: 'voucher' }, meta: { title: '凭证台账' } },
  { path: 'glex/special', redirect: '/glex/special/tax' },
  // GL专项各页签拆为独立菜单（见下）
  { path: 'glex/special/tax', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'tax' }, meta: { title: '税金计提' } },
  { path: 'glex/special/salary', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'salary' }, meta: { title: '薪酬发放' } },
  { path: 'glex/special/inter', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'inter' }, meta: { title: '内部往来抵消' } },
  { path: 'glex/special/journal', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'journal' }, meta: { title: '一般记账' } },
  { path: 'glex/special/monthclose', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'monthclose' }, meta: { title: '月结' } },
  // 批5 后续：合并报表底稿入口（GL_MERGE 作业化后归 GL 作业页签）
  { path: 'glex/consolidate', component: () => import('../../views/gl/GlConsolidate.vue'), meta: { title: '合并报表底稿' } },
  { path: 'glex/special/consolidate', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'consolidate' }, meta: { title: '合并报表' } },
  { path: 'glex/special/master', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'master' }, meta: { title: 'GL主数据' } },
  { path: 'glex/tax-refund', component: () => import('../../views/gl/TaxRefund.vue'), meta: { title: '税务退税三场景' } },
  { path: 'glex/fx-reval', component: () => import('../../views/finance/FxRevalBoard.vue'), meta: { title: '汇兑重估' } },
  // 银企对账（对账/回单认领/调节表收敛为一个工作区；/bank/slip、/bank/reconcile-sheet 深链保留）
  { path: 'bank/reconcile', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'bankRecon' }, meta: { title: i18n.global.t('router.finance.Bank') } },
  { path: 'bank/slip', component: () => import('../../views/bank/BankSlip.vue'), meta: { title: '银企回单·收款认领' } },
  { path: 'bank/reconcile-sheet', component: () => import('../../views/bank/ReconcileSheet.vue'), meta: { title: i18n.global.t('bank.reconcileSheet.Unreconciled') } },
  // 预算（科目/方案/调整/占用收敛为一个工作区，逐页签按 budget 码门控；
  // 宿主直挂根（sys_menu parent=0）＝绕开预算目录 13 的 budget:item:list 授权，修复 315 等 4 角色游离不可见问题）
  { path: 'budget/workbench', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'budgetManage' }, meta: { title: '预算管理' } },
  { path: 'budget/item', component: () => import('../../views/budget/BudgetItem.vue'), meta: { title: '预算明细', perms: ['budget:item:list'] } }, // 声明读权；页面内按 hasPerm 显式空态门控（router 暂无 meta.perms 全局消费）
  { path: 'budget/type', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'budgetType' }, meta: { title: i18n.global.t('router.finance.Budget') } },
  { path: 'budget/category', component: () => import('../../views/budget/BudgetConfigTable.vue'), props: { configType: 'budgetCategory', title: '预算类别' }, meta: { title: '预算类别' } },
  { path: 'budget/org', component: () => import('../../views/budget/BudgetConfigTable.vue'), props: { configType: 'budgetOrg', title: '预算组织' }, meta: { title: '预算组织' } },
  { path: 'budget/period', component: () => import('../../views/budget/BudgetConfigTable.vue'), props: { configType: 'budgetPeriod', title: i18n.global.t('router.finance.Budget2') }, meta: { title: i18n.global.t('router.finance.Budget2') } },
  { path: 'budget/scheme', component: () => import('../../views/budget/BudgetScheme.vue'), meta: { title: i18n.global.t('budget.budgetScheme.Budget') } },
  { path: 'budget/adjust', component: () => import('../../views/budget/BudgetAdjust.vue'), meta: { title: i18n.global.t('budget.budgetAdjust.Budget') } },
  { path: 'budget/occupy', component: () => import('../../views/budget/BudgetOccupy.vue'), meta: { title: i18n.global.t('dashboard.processNavMap.Budget') } },
  // 预算执行明细查询（期间/部门/科目 → 占用/核销/余额；路由可达即可，菜单可不挂侧边栏）
  { path: 'budget/execution', component: () => import('../../views/budget/BudgetExecution.vue'), meta: { title: i18n.global.t('budget.budgetExecution.Budget') } },
  // 报表
  { path: 'report/summary', component: () => import('../../views/finance/ReportSummaryBoard.vue'), meta: { title: '报表预聚合' } },
  // /reports 为报表工作台宿主（25 页签），不传 tab；成员深链 /reports/* 保留单页模式
  { path: 'reports', component: () => import('../../views/report/Reports.vue'), meta: { title: '管理报表工作台' } },
  { path: 'reports/summary', component: () => import('../../views/report/Reports.vue'), props: { tab: 'summary' }, meta: { title: i18n.global.t('router.finance.Expense') } },
  { path: 'reports/trend', component: () => import('../../views/report/Reports.vue'), props: { tab: 'trend' }, meta: { title: i18n.global.t('router.finance.Monthly') } },
  { path: 'reports/composition', component: () => import('../../views/report/Reports.vue'), props: { tab: 'composition' }, meta: { title: i18n.global.t('router.finance.Expense2') } },
  { path: 'reports/loan', component: () => import('../../views/report/Reports.vue'), props: { tab: 'loan' }, meta: { title: i18n.global.t('report.main.Loan') } },
  { path: 'reports/budget', component: () => import('../../views/report/Reports.vue'), props: { tab: 'budget' }, meta: { title: i18n.global.t('report.main.Budget') } },
  { path: 'reports/contract', component: () => import('../../views/report/Reports.vue'), props: { tab: 'contract' }, meta: { title: i18n.global.t('router.finance.Contract2') } },
  { path: 'reports/detail', component: () => import('../../views/report/Reports.vue'), props: { tab: 'detail' }, meta: { title: i18n.global.t('router.finance.Expense3') } },
  { path: 'reports/ops', component: () => import('../../views/report/Reports.vue'), props: { tab: 'ops' }, meta: { title: i18n.global.t('router.finance.Operational') } },
  { path: 'reports/dept', component: () => import('../../views/report/Reports.vue'), props: { tab: 'dept' }, meta: { title: i18n.global.t('report.main.Department') } },
  { path: 'reports/eff', component: () => import('../../views/report/Reports.vue'), props: { tab: 'eff' }, meta: { title: i18n.global.t('report.main.Approval') } },
  { path: 'reports/ap-aging', component: () => import('../../views/report/Reports.vue'), props: { tab: 'apAging' }, meta: { title: i18n.global.t('router.finance.Payable') } },
  { path: 'reports/ar-aging', component: () => import('../../views/report/Reports.vue'), props: { tab: 'arAging' }, meta: { title: i18n.global.t('router.finance.Receivable2') } },
  { path: 'reports/cash-flow', component: () => import('../../views/report/Reports.vue'), props: { tab: 'cashFlow' }, meta: { title: i18n.global.t('router.finance.Cash') } },
  { path: 'reports/bank-balance', component: () => import('../../views/report/Reports.vue'), props: { tab: 'bankBalance' }, meta: { title: i18n.global.t('router.finance.Cash2') } },
  { path: 'reports/supplier-pay', component: () => import('../../views/report/Reports.vue'), props: { tab: 'supplierPay' }, meta: { title: i18n.global.t('router.finance.Supplier') } },
  { path: 'reports/input-invoice', component: () => import('../../views/report/Reports.vue'), props: { tab: 'inputInvoice' }, meta: { title: i18n.global.t('router.finance.Input') } },
  { path: 'reports/output-invoice', component: () => import('../../views/report/Reports.vue'), props: { tab: 'outputInvoice' }, meta: { title: i18n.global.t('router.finance.Output') } },
  { path: 'reports/tax-burden', component: () => import('../../views/report/Reports.vue'), props: { tab: 'taxBurden' }, meta: { title: i18n.global.t('router.finance.Tax2') } },
  { path: 'reports/ops-eff', component: () => import('../../views/report/Reports.vue'), props: { tab: 'opsEff' }, meta: { title: i18n.global.t('router.finance.Operational2') } },
  { path: 'reports/quality', component: () => import('../../views/report/Reports.vue'), props: { tab: 'quality' }, meta: { title: i18n.global.t('router.finance.Inspection') } },
  { path: 'reports/image', component: () => import('../../views/report/Reports.vue'), props: { tab: 'image' }, meta: { title: i18n.global.t('router.finance.Image') } },
  { path: 'reports/contract-exec', component: () => import('../../views/report/Reports.vue'), props: { tab: 'contractExec' }, meta: { title: i18n.global.t('router.finance.Contract3') } },
  { path: 'reports/budget-alert', component: () => import('../../views/report/Reports.vue'), props: { tab: 'budgetAlert' }, meta: { title: i18n.global.t('router.finance.Budget3') } },
  { path: 'reports/loan-overdue', component: () => import('../../views/report/Reports.vue'), props: { tab: 'loanOverdue' }, meta: { title: i18n.global.t('router.finance.Loan') } },
  { path: 'reports/voucher-stats', component: () => import('../../views/report/Reports.vue'), props: { tab: 'voucherStats' }, meta: { title: i18n.global.t('router.finance.Voucher') } },
  // 客户
  { path: 'customer', component: () => import('../../views/master/Customer.vue'), meta: { title: '客户管理' } },
]
