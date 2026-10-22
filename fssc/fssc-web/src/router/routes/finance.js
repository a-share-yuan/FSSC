/** 财务核算域路由（总账/资产/应收/资金/银行/档案/合同/报表） */
export default [
  // P0-7 FA 资产与存货：父路径渲染 Board 宿主（全页签可见），成员深链 /asset/* 保留单页模式
  // D-BIZ-05 修复：原 redirect → /asset/asset（带 tab prop）导致页签条永久隐藏，用户无法切换子视图
  { path: 'asset', component: () => import('../../views/asset/AssetBoard.vue'), meta: { title: '资产与存货管理' } },
  { path: 'asset/asset', component: () => import('../../views/asset/AssetBoard.vue'), props: { tab: 'asset' }, meta: { title: '资产卡片' } },
  { path: 'asset/category', component: () => import('../../views/asset/AssetBoard.vue'), props: { tab: 'category' }, meta: { title: '资产类别' } },
  { path: 'asset/inventory', component: () => import('../../views/asset/AssetBoard.vue'), props: { tab: 'inventory' }, meta: { title: '存货收发存' } },
  { path: 'asset/taxDiff', component: () => import('../../views/asset/AssetBoard.vue'), props: { tab: 'taxDiff' }, meta: { title: '税会差异' } },
  { path: 'asset/depr-batch', component: () => import('../../views/asset/DeprBatch.vue'), meta: { title: '折旧计提批' } },
  // P0-8 TR 资金管理：父路径渲染 Board 宿主（全页签可见），成员深链 /treasury/* 保留单页模式
  // D-BIZ-05 修复：原 redirect → /treasury/plan（带 tab prop）导致页签条永久隐藏，用户无法切换子视图
  // R5-D-186：treasury 七路由补 meta.perms（router.js 全局守卫消费；读码与后端 @RequiresPerm 逐一对齐：
  // treasury:fundplan/creditbill/transfer/billsettle/position、bank=md:bankaccount:list、bankRecon=bank:reconcile:list）。
  // 无权限账号 URL 直达不再渲染带写按钮的空壳；后端守卫保持现状。
  { path: 'treasury', component: () => import('../../views/treasury/TreasuryBoard.vue'), meta: { title: '资金管理' } },
  { path: 'treasury/plan', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'plan' }, meta: { title: '资金计划', perms: ['treasury:fundplan:list'] } },
  { path: 'treasury/credit', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'credit' }, meta: { title: '资金收款', perms: ['treasury:creditbill:list'] } },
  { path: 'treasury/transfer', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'transfer' }, meta: { title: '资金调拨', perms: ['treasury:transfer:list'] } },
  { path: 'treasury/settle', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'settle' }, meta: { title: '票据结算', perms: ['treasury:billsettle:list'] } },
  { path: 'treasury/bank', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'bank' }, meta: { title: '银行主数据', perms: ['md:bankaccount:list'] } },
  { path: 'treasury/bankRecon', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'bankRecon' }, meta: { title: '银行对账', perms: ['bank:reconcile:list'] } },
  { path: 'treasury/position', component: () => import('../../views/treasury/TreasuryBoard.vue'), props: { tab: 'position' }, meta: { title: '资金头寸', perms: ['treasury:position:list', 'treasury:fundplan:list', 'treasury:creditbill:list'] } },
  // P0-6 OTC 应收管理：父路径渲染 Board 宿主（全页签可见），成员深链 /receivable/* 保留单页模式
  // D-BIZ-05 修复：原 redirect → /receivable/receivable（带 tab prop）导致页签条永久隐藏，用户无法切换子视图
  { path: 'receivable', component: () => import('../../views/receivable/ReceivableBoard.vue'), meta: { title: '应收管理' } },
  { path: 'receivable/receivable', component: () => import('../../views/receivable/ReceivableBoard.vue'), props: { tab: 'receivable' }, meta: { title: '应收单' } },
  { path: 'receivable/receipt', component: () => import('../../views/receivable/ReceivableBoard.vue'), props: { tab: 'receipt' }, meta: { title: '收款核销' } },
  { path: 'receivable/collect', component: () => import('../../views/receivable/ReceivableBoard.vue'), props: { tab: 'collect' }, meta: { title: '催收待办' } },
  { path: 'receivable/badDebt', component: () => import('../../views/receivable/ReceivableBoard.vue'), props: { tab: 'badDebt' }, meta: { title: '坏账计提' } },
  // P3 FM 档案借阅/销毁：父路径重定向到首个 tab，不渲染汇总的 hub 页
  { path: 'archive', redirect: '/archive/access' },
  { path: 'archive/access', component: () => import('../../views/archive/ArchiveBoard.vue'), props: { tab: 'access' }, meta: { title: '档案调阅' } },
  { path: 'archive/destroy', component: () => import('../../views/archive/ArchiveBoard.vue'), props: { tab: 'destroy' }, meta: { title: '档案销毁' } },
  { path: 'archive/dispatch', component: () => import('../../views/archive/ArchiveBoard.vue'), props: { tab: 'dispatch' }, meta: { title: '单据流转' } },
  { path: 'archive/archive', component: () => import('../../views/archive/ArchiveBoard.vue'), props: { tab: 'archive' }, meta: { title: '凭证归档' } },
  // 合同（RA-PAGE-21：台账+付款查询收敛为一个工作区；/contract/pay-query 深链保留）
  { path: 'contract', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'contract' }, meta: { title: '合同管理' } },
  { path: 'contract/type', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'contractType' }, meta: { title: '合同类型' } },
  // P2 总账：父路径重定向到首个 tab，不渲染汇总的 hub 页
  //  RA-PAGE-22 缓办（2026-09-29 实测）：3066 有 {58,99007}、3082 有 {61,68,99013} 不在 3065 并集内，
  // 直接并会砍访问或增权（3065 带 gl:entry:list,md:subject:list）——随流程批次先裁授权面再并。
  { path: 'op/gl', redirect: '/op/gl/entry' },
  // 总账各页签拆为独立菜单（见下）
  { path: 'op/gl/entry', component: () => import('../../views/gl/GlLedger.vue'), props: { tab: 'entry' }, meta: { title: '总账分录' } },
  { path: 'op/gl/balance', component: () => import('../../views/gl/GlLedger.vue'), props: { tab: 'balance' }, meta: { title: '科目余额表' } },
  { path: 'op/gl/trial', component: () => import('../../views/gl/GlLedger.vue'), props: { tab: 'trial' }, meta: { title: '试算平衡' } },
  // 凭证台账（GL 凭证列表：过账状态 + 批量过账 + 制审分离）
  { path: 'op/gl/voucher', component: () => import('../../views/gl/GlLedger.vue'), props: { tab: 'voucher' }, meta: { title: '凭证台账' } },
  { path: 'glex/special', redirect: '/glex/special/tax' },
  // GL专项各页签拆为独立菜单（见下）
  { path: 'glex/special/tax', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'tax' }, meta: { title: '税金计提' } },
  { path: 'glex/special/salary', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'salary' }, meta: { title: '薪酬发放' } },
  { path: 'glex/special/inter', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'inter' }, meta: { title: '内部往来抵消' } },
  { path: 'glex/special/journal', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'journal' }, meta: { title: '一般记账' } },
  { path: 'glex/special/monthclose', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'monthclose' }, meta: { title: '月结' } },
  // D-DEC-295（批5 后续）：合并报表底稿入口（GL_MERGE 作业化后归 GL 作业页签）
  { path: 'glex/consolidate', component: () => import('../../views/gl/GlConsolidate.vue'), meta: { title: '合并报表底稿' } },
  { path: 'glex/special/consolidate', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'consolidate' }, meta: { title: '合并报表' } },
  { path: 'glex/special/master', component: () => import('../../views/gl/GlSpecial.vue'), props: { tab: 'master' }, meta: { title: 'GL主数据' } },
  { path: 'glex/tax-refund', component: () => import('../../views/gl/TaxRefund.vue'), meta: { title: '税务退税三场景' } },
  { path: 'glex/fx-reval', component: () => import('../../views/finance/FxRevalBoard.vue'), meta: { title: '汇兑重估' } },
  // P2 银企对账（RA-PAGE-21：对账/回单认领/调节表收敛为一个工作区；/bank/slip、/bank/reconcile-sheet 深链保留）
  { path: 'bank/reconcile', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'bankRecon' }, meta: { title: '银企对账' } },
  { path: 'bank/slip', component: () => import('../../views/bank/BankSlip.vue'), meta: { title: '银企回单·收款认领' } },
  { path: 'bank/reconcile-sheet', component: () => import('../../views/bank/ReconcileSheet.vue'), meta: { title: '未达账项调节表' } },
  // 预算（RA-PAGE-21：科目/方案/调整/占用收敛为一个工作区，逐页签按 budget 码门控；
  // 宿主直挂根（sys_menu parent=0）＝绕开预算目录 13 的 budget:item:list 授权，修复 315 等 4 角色游离不可见问题）
  { path: 'budget/workbench', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'budgetManage' }, meta: { title: '预算管理' } },
  { path: 'budget/item', component: () => import('../../views/budget/BudgetItem.vue'), meta: { title: '预算明细', perms: ['budget:item:list'] } }, // R5-D-263：声明读权；页面内按 hasPerm 显式空态门控（router 暂无 meta.perms 全局消费）
  { path: 'budget/type', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'budgetType' }, meta: { title: '预算类型' } },
  { path: 'budget/category', component: () => import('../../views/budget/BudgetConfigTable.vue'), props: { configType: 'budgetCategory', title: '预算类别' }, meta: { title: '预算类别' } },
  { path: 'budget/org', component: () => import('../../views/budget/BudgetConfigTable.vue'), props: { configType: 'budgetOrg', title: '预算组织' }, meta: { title: '预算组织' } },
  { path: 'budget/period', component: () => import('../../views/budget/BudgetConfigTable.vue'), props: { configType: 'budgetPeriod', title: '预算期间' }, meta: { title: '预算期间' } },
  { path: 'budget/scheme', component: () => import('../../views/budget/BudgetScheme.vue'), meta: { title: '预算方案' } },
  { path: 'budget/adjust', component: () => import('../../views/budget/BudgetAdjust.vue'), meta: { title: '预算调整' } },
  { path: 'budget/occupy', component: () => import('../../views/budget/BudgetOccupy.vue'), meta: { title: '预算占用' } },
  // 预算执行明细查询（期间/部门/科目 → 占用/核销/余额；路由可达即可，菜单可不挂侧边栏）
  { path: 'budget/execution', component: () => import('../../views/budget/BudgetExecution.vue'), meta: { title: '预算执行明细' } },
  // 报表
  { path: 'report/summary', component: () => import('../../views/finance/ReportSummaryBoard.vue'), meta: { title: '报表预聚合' } },
  // RA-PAGE-15（批次1 A1）：/reports 为报表工作台宿主（25 页签），不传 tab；成员深链 /reports/* 保留单页模式
  { path: 'reports', component: () => import('../../views/report/Reports.vue'), meta: { title: '管理报表工作台' } },
  { path: 'reports/summary', component: () => import('../../views/report/Reports.vue'), props: { tab: 'summary' }, meta: { title: '费用发生汇总' } },
  { path: 'reports/trend', component: () => import('../../views/report/Reports.vue'), props: { tab: 'trend' }, meta: { title: '费用月度变动' } },
  { path: 'reports/composition', component: () => import('../../views/report/Reports.vue'), props: { tab: 'composition' }, meta: { title: '费用构成情况' } },
  { path: 'reports/loan', component: () => import('../../views/report/Reports.vue'), props: { tab: 'loan' }, meta: { title: '借款余额汇总' } },
  { path: 'reports/budget', component: () => import('../../views/report/Reports.vue'), props: { tab: 'budget' }, meta: { title: '预算执行' } },
  { path: 'reports/contract', component: () => import('../../views/report/Reports.vue'), props: { tab: 'contract' }, meta: { title: '合同汇总' } },
  { path: 'reports/detail', component: () => import('../../views/report/Reports.vue'), props: { tab: 'detail' }, meta: { title: '费用明细表' } },
  { path: 'reports/ops', component: () => import('../../views/report/Reports.vue'), props: { tab: 'ops' }, meta: { title: '运营数据统计' } },
  { path: 'reports/dept', component: () => import('../../views/report/Reports.vue'), props: { tab: 'dept' }, meta: { title: '部门报销排名' } },
  { path: 'reports/eff', component: () => import('../../views/report/Reports.vue'), props: { tab: 'eff' }, meta: { title: '审批效率' } },
  { path: 'reports/ap-aging', component: () => import('../../views/report/Reports.vue'), props: { tab: 'apAging' }, meta: { title: '应付账龄分析' } },
  { path: 'reports/ar-aging', component: () => import('../../views/report/Reports.vue'), props: { tab: 'arAging' }, meta: { title: '应收账龄分析' } },
  { path: 'reports/cash-flow', component: () => import('../../views/report/Reports.vue'), props: { tab: 'cashFlow' }, meta: { title: '资金收支汇总' } },
  { path: 'reports/bank-balance', component: () => import('../../views/report/Reports.vue'), props: { tab: 'bankBalance' }, meta: { title: '资金余额表' } },
  { path: 'reports/supplier-pay', component: () => import('../../views/report/Reports.vue'), props: { tab: 'supplierPay' }, meta: { title: '供应商付款汇总' } },
  { path: 'reports/input-invoice', component: () => import('../../views/report/Reports.vue'), props: { tab: 'inputInvoice' }, meta: { title: '进项发票统计' } },
  { path: 'reports/output-invoice', component: () => import('../../views/report/Reports.vue'), props: { tab: 'outputInvoice' }, meta: { title: '销项发票统计' } },
  { path: 'reports/tax-burden', component: () => import('../../views/report/Reports.vue'), props: { tab: 'taxBurden' }, meta: { title: '税负分析' } },
  { path: 'reports/ops-eff', component: () => import('../../views/report/Reports.vue'), props: { tab: 'opsEff' }, meta: { title: '运营效率分析' } },
  { path: 'reports/quality', component: () => import('../../views/report/Reports.vue'), props: { tab: 'quality' }, meta: { title: '质检合格率' } },
  { path: 'reports/image', component: () => import('../../views/report/Reports.vue'), props: { tab: 'image' }, meta: { title: '影像统计' } },
  { path: 'reports/contract-exec', component: () => import('../../views/report/Reports.vue'), props: { tab: 'contractExec' }, meta: { title: '合同执行明细' } },
  { path: 'reports/budget-alert', component: () => import('../../views/report/Reports.vue'), props: { tab: 'budgetAlert' }, meta: { title: '预算超支预警' } },
  { path: 'reports/loan-overdue', component: () => import('../../views/report/Reports.vue'), props: { tab: 'loanOverdue' }, meta: { title: '借款逾期分析' } },
  { path: 'reports/voucher-stats', component: () => import('../../views/report/Reports.vue'), props: { tab: 'voucherStats' }, meta: { title: '凭证生成统计' } },
  // 客户
  { path: 'customer', component: () => import('../../views/master/Customer.vue'), meta: { title: '客户管理' } },
]
