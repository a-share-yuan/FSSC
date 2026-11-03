// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/** 系统设置与主数据域路由 */
import { i18n } from '../../i18n'
export default [
  // 方案B 配置工作区（页面Tab化重构册 §乙）：一工作区一菜单，页内 tab 复用通用表格组件；
  // 下方原「一表一页」路由全部保留（历史 URL、深链与 F 按钮权限不受影响）。
  { path: 'cfg/sharing', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'sharing' }, meta: { title: '共享运营配置' } },
  { path: 'cfg/system', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'system' }, meta: { title: i18n.global.t('router.system.System') } },
  { path: 'cfg/expense', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'expense' }, meta: { title: '费用与单据配置' } },
  { path: 'cfg/loan', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'loan' }, meta: { title: '借款规则配置' } },
  { path: 'cfg/ops', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'ops' }, meta: { title: '运行监控与合规' } },
  { path: 'cfg/finance', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'finance' }, meta: { title: '财税主数据' } },
  { path: 'cfg/org', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'org' }, meta: { title: '组织人事主数据' } },
  { path: 'cfg/budget', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'budget' }, meta: { title: '预算规则配置' } },
  // 系统设置
  { path: 'system/user', component: () => import('../../views/system/UserManage.vue'), meta: { title: '员工管理' } },
  { path: 'system/role', component: () => import('../../views/system/RoleManage.vue'), meta: { title: '角色权限' } },
  { path: 'system/org', component: () => import('../../views/system/Org.vue'), meta: { title: '组织架构' } },
  { path: 'system/post', component: () => import('../../views/system/PositionManage.vue'), meta: { title: '职位' } },
  { path: 'system/dict', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'dict' }, meta: { title: i18n.global.t('router.system.Dictionary') } },
  { path: 'system/file', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-file' }, meta: { title: i18n.global.t('router.system.File') } },
  { path: 'system/number', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-number' }, meta: { title: i18n.global.t('router.system.Numbering') } },
  { path: 'system/notice', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-notice' }, meta: { title: i18n.global.t('router.system.Announcement') } },
  { path: 'system/message', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-message' }, meta: { title: i18n.global.t('router.system.Notification') } },
  { path: 'system/supplier', component: () => import('../../views/master/Supplier.vue'), meta: { title: '供应商维护' } },
  { path: 'system/period', component: () => import('../../views/system/PeriodManage.vue'), meta: { title: '会计期间' } },
  { path: 'system/config', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-config' }, meta: { title: i18n.global.t('router.system.Global') } },
  { path: 'system/policy', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-policy' }, meta: { title: i18n.global.t('router.system.Expense') } },
  { path: 'system/log', component: () => import('../../views/system/SysLog.vue'), meta: { title: '系统日志' } },
  { path: 'system/status', component: () => import('../../views/system/RunStatus.vue'), meta: { title: '运行状态' } },
  { path: 'system/job-heartbeat', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'job-heartbeat' }, meta: { title: i18n.global.t('router.system.Scheduled') } },
  { path: 'system/sod-check', component: () => import('../../views/system/SoDCheck.vue'), meta: { title: '不相容职责检查' } },
  // 流程收尾动作配置（wf_event_action_config）：常规入口在「运行监控与合规」工作区页签，本行为深链保留
  { path: 'system/wf-event-action', component: () => import('../../views/workflow/WfEventActionConfig.vue'), meta: { title: i18n.global.t('workflow.wfEventAction.Workflow') } },
  // OCR通道配置已合并至「外部接口配置」→ OCR 通道设置 Tab
  { path: 'system/ext-interface', component: () => import('../../views/system/ExtInterfaceConfig.vue'), meta: { title: '外部接口配置' } },
  { path: 'system/integration-logs', component: () => import('../../views/system/IntegrationLogs.vue'), meta: { title: '集成调用日志' } },
  // 主数据
  { path: 'master/currency', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'currency' }, meta: { title: '币种管理' } },
  { path: 'master/project', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'project' }, meta: { title: '项目管理' } },
  { path: 'master/bank', component: () => import('../../views/master/BankAccount.vue'), meta: { title: i18n.global.t('bank.reconcileSheet.Bank') } },
  { path: 'master/expense-item', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'expense-item' }, meta: { title: i18n.global.t('master.expenseStandard.Expense') } },
  { path: 'master/invoice-rule', component: () => import('../../views/master/InvoiceRule.vue'), meta: { title: '发票智能匹配规则' } },
  { path: 'master/subject', component: () => import('../../views/master/MasterDataTree.vue'), props: { mode: 'subject', title: '会计科目' }, meta: { title: '会计科目' } },
  { path: 'master/cost-center', component: () => import('../../views/master/MasterDataTree.vue'), props: { mode: 'costCenter', title: i18n.global.t('router.system.Cost') }, meta: { title: i18n.global.t('router.system.Cost') } },
  { path: 'master/city', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'city' }, meta: { title: i18n.global.t('master.expenseStandard.City') } },
  { path: 'master/tax-rate', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'tax-rate' }, meta: { title: i18n.global.t('router.system.Tax') } },
  { path: 'master/expense-standard', component: () => import('../../views/master/ExpenseStandard.vue'), meta: { title: i18n.global.t('router.system.Expense2') } },
  { path: 'master/payee', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'payee' }, meta: { title: i18n.global.t('router.system.Payee') } },
  { path: 'master/exchange-rate', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'exchange-rate' }, meta: { title: '汇率管理' } },
  // 基础数据-配置类（章）
  { path: 'master/form', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'form' }, meta: { title: '表单详情配置' } },
  { path: 'master/loanLimit', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'loanLimit' }, meta: { title: i18n.global.t('router.system.Loan') } },
  { path: 'master/loanInitial', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'loanInitial' }, meta: { title: i18n.global.t('router.system.Opening') } },
  { path: 'master/orgType', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'orgType' }, meta: { title: i18n.global.t('router.system.Organisation') } },
  { path: 'master/cashFlow', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'cashFlow' }, meta: { title: i18n.global.t('router.system.Cash') } },
  { path: 'master/itemCashMap', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'itemCashMap' }, meta: { title: i18n.global.t('router.system.Expense3') } },
  { path: 'master/itemBudgetMap', component: () => import('../../views/budget/BudgetConfigTable.vue'), props: { configType: 'itemBudgetMap', title: '费用类型-预算科目映射' }, meta: { title: '费用类型-预算科目映射' } },
  { path: 'master/costCenterMap', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'costCenterMap' }, meta: { title: i18n.global.t('router.system.Cost2') } },
  { path: 'master/vehicle', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'vehicle' }, meta: { title: i18n.global.t('router.system.Transport') } },
  { path: 'master/bank-inst', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'bank-inst' }, meta: { title: i18n.global.t('router.system.Financial') } },
  { path: 'master/employeeLevel', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'employeeLevel' }, meta: { title: i18n.global.t('router.system.Employee') } },
  { path: 'master/glConfig', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'glConfig' }, meta: { title: i18n.global.t('router.system.Posting') } },
  { path: 'master/incomeExpense', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'incomeExpense' }, meta: { title: i18n.global.t('router.system.Receipt') } },
  { path: 'master/loanPayType', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'loanPayType' }, meta: { title: i18n.global.t('router.system.Loan2') } },
  { path: 'master/cip', component: () => import('../../views/cip/CipBoard.vue'), meta: { title: i18n.global.t('router.system.Construction') } },
  // 合规风控（反欺诈+合规规则收敛为一个工作区，逐页签门控；原两页路由保留作深链）
  { path: 'compliance/board', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'compliance' }, meta: { title: '合规风控' } },
  { path: 'compliance/review-rules', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'review-rules' }, meta: { title: i18n.global.t('router.system.Intelligent') } },
  { path: 'compliance/fraud', component: () => import('../../views/compliance/FraudBoard.vue'), meta: { title: i18n.global.t('compliance.fraud.Anti') } },
  { path: 'compliance/service-catalog', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'service-catalog' }, meta: { title: i18n.global.t('router.system.Service') } },
  { path: 'compliance/satisfaction', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'satisfaction' }, meta: { title: i18n.global.t('router.system.Satisfaction') } },
  { path: 'compliance/rule', component: () => import('../../views/master/ComplianceRule.vue'), meta: { title: '合规规则' } },
  // 费用标准（expense_policy 主表 + expense_policy_standard 明细 + expense_policy_version 版本三层，
  // 专用页；原先只有 system/policy 复用通用表组件，只覆盖部分主表字段且无子表）
  { path: 'compliance/expense-policy', component: () => import('../../views/compliance/ExpensePolicyManage.vue'), meta: { title: '费用标准' } },
  // 审计
  { path: 'audit/reconcile', component: () => import('../../views/audit/AuditBoard.vue'), props: { activeTab: 'reconcile' }, meta: { title: i18n.global.t('audit.main.Data') } },
  { path: 'audit/trail', component: () => import('../../views/audit/AuditBoard.vue'), props: { activeTab: 'trail' }, meta: { title: i18n.global.t('router.system.Audit') } },
]
