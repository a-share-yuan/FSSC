// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/** 系统设置与主数据域路由 */
export default [
  // 方案B 配置工作区（页面Tab化重构册 §乙）：一工作区一菜单，页内 tab 复用通用表格组件；
  // 下方原「一表一页」路由全部保留（历史 URL、深链与 F 按钮权限不受影响）。
  { path: 'cfg/sharing', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'sharing' }, meta: { title: '共享运营配置' } },
  { path: 'cfg/system', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'system' }, meta: { title: '系统参数' } },
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
  { path: 'system/dict', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'dict' }, meta: { title: '字典管理' } },
  { path: 'system/file', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-file' }, meta: { title: '文件管理' } },
  { path: 'system/number', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-number' }, meta: { title: '编号规则' } },
  { path: 'system/notice', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-notice' }, meta: { title: '公告管理' } },
  { path: 'system/message', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-message' }, meta: { title: '消息中心' } },
  { path: 'system/supplier', component: () => import('../../views/master/Supplier.vue'), meta: { title: '供应商维护' } },
  { path: 'system/period', component: () => import('../../views/system/PeriodManage.vue'), meta: { title: '会计期间' } },
  { path: 'system/config', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-config' }, meta: { title: '全局参数' } },
  { path: 'system/policy', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'sys-policy' }, meta: { title: '费用政策' } },
  { path: 'system/log', component: () => import('../../views/system/SysLog.vue'), meta: { title: '系统日志' } },
  { path: 'system/status', component: () => import('../../views/system/RunStatus.vue'), meta: { title: '运行状态' } },
  { path: 'system/job-heartbeat', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'job-heartbeat' }, meta: { title: '定时任务健康' } },
  { path: 'system/sod-check', component: () => import('../../views/system/SoDCheck.vue'), meta: { title: 'SoD合规检查' } },
  // D-DEC-320 流程收尾动作配置（wf_event_action_config）：常规入口在「运行监控与合规」工作区页签，本行为深链保留
  { path: 'system/wf-event-action', component: () => import('../../views/workflow/WfEventActionConfig.vue'), meta: { title: '流程收尾动作配置' } },
  // OCR通道配置已合并至「外部接口配置」→ OCR 通道设置 Tab（2026-09-17）
  { path: 'system/ext-interface', component: () => import('../../views/system/ExtInterfaceConfig.vue'), meta: { title: '外部接口配置' } },
  { path: 'system/integration-logs', component: () => import('../../views/system/IntegrationLogs.vue'), meta: { title: '集成调用日志' } },
  // 主数据
  { path: 'master/currency', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'currency' }, meta: { title: '币种管理' } },
  { path: 'master/project', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'project' }, meta: { title: '项目管理' } },
  { path: 'master/bank', component: () => import('../../views/master/BankAccount.vue'), meta: { title: '银行账户' } },
  { path: 'master/expense-item', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'expense-item' }, meta: { title: '费用类型' } },
  { path: 'master/invoice-rule', component: () => import('../../views/master/InvoiceRule.vue'), meta: { title: '发票智能匹配规则' } },
  { path: 'master/subject', component: () => import('../../views/master/MasterDataTree.vue'), props: { mode: 'subject', title: '会计科目' }, meta: { title: '会计科目' } },
  { path: 'master/cost-center', component: () => import('../../views/master/MasterDataTree.vue'), props: { mode: 'costCenter', title: '成本中心' }, meta: { title: '成本中心' } },
  { path: 'master/city', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'city' }, meta: { title: '城市等级' } },
  { path: 'master/tax-rate', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'tax-rate' }, meta: { title: '税码税率' } },
  { path: 'master/expense-standard', component: () => import('../../views/master/ExpenseStandard.vue'), meta: { title: '报销标准' } },
  { path: 'master/payee', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'payee' }, meta: { title: '收款方' } },
  { path: 'master/exchange-rate', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'exchange-rate' }, meta: { title: '汇率管理' } },
  // 基础数据-配置类（需求 2 章）
  { path: 'master/form', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'form' }, meta: { title: '表单详情配置' } },
  { path: 'master/loanLimit', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'loanLimit' }, meta: { title: '借款额度' } },
  { path: 'master/loanInitial', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'loanInitial' }, meta: { title: '初始借款余额' } },
  { path: 'master/orgType', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'orgType' }, meta: { title: '组织类型' } },
  { path: 'master/cashFlow', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'cashFlow' }, meta: { title: '现金流量码' } },
  { path: 'master/itemCashMap', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'itemCashMap' }, meta: { title: '费用类型-现金流量码映射' } },
  { path: 'master/itemBudgetMap', component: () => import('../../views/budget/BudgetConfigTable.vue'), props: { configType: 'itemBudgetMap', title: '费用类型-预算科目映射' }, meta: { title: '费用类型-预算科目映射' } },
  { path: 'master/costCenterMap', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'costCenterMap' }, meta: { title: '成本中心-预算组织映射' } },
  { path: 'master/vehicle', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'vehicle' }, meta: { title: '交通工具' } },
  { path: 'master/bank-inst', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'bank-inst' }, meta: { title: '金融机构' } },
  { path: 'master/employeeLevel', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'employeeLevel' }, meta: { title: '员工等级' } },
  { path: 'master/glConfig', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'glConfig' }, meta: { title: '记账配置' } },
  { path: 'master/incomeExpense', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'incomeExpense' }, meta: { title: '收支设置' } },
  { path: 'master/loanPayType', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'loanPayType' }, meta: { title: '借款付款类型' } },
  { path: 'master/cip', component: () => import('../../views/cip/CipBoard.vue'), meta: { title: '在建工程' } },
  // 合规风控（RA-PAGE-21：反欺诈+合规规则收敛为一个工作区，逐页签门控；原两页路由保留作深链）
  { path: 'compliance/board', component: () => import('../../views/master/ConfigWorkspace.vue'), props: { group: 'compliance' }, meta: { title: '合规风控' } },
  { path: 'compliance/review-rules', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'review-rules' }, meta: { title: '智能审单规则' } },
  { path: 'compliance/fraud', component: () => import('../../views/compliance/FraudBoard.vue'), meta: { title: '反欺诈风控' } },
  { path: 'compliance/service-catalog', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'service-catalog' }, meta: { title: '服务目录' } },
  { path: 'compliance/satisfaction', component: () => import('../../views/master/MasterConfigTable.vue'), props: { configKey: 'satisfaction' }, meta: { title: '满意度调查' } },
  { path: 'compliance/rule', component: () => import('../../views/master/ComplianceRule.vue'), meta: { title: '合规规则' } },
  // 费用标准（expense_policy 主表 + expense_policy_standard 明细 + expense_policy_version 版本三层，
  // 2026-10-17 新增专用页；此前只有 system/policy 复用通用表组件，只覆盖主表 3/18 字段且无子表）
  { path: 'compliance/expense-policy', component: () => import('../../views/compliance/ExpensePolicyManage.vue'), meta: { title: '费用标准' } },
  // 审计
  { path: 'audit/reconcile', component: () => import('../../views/audit/AuditBoard.vue'), props: { activeTab: 'reconcile' }, meta: { title: '数据一致性' } },
  { path: 'audit/trail', component: () => import('../../views/audit/AuditBoard.vue'), props: { activeTab: 'trail' }, meta: { title: '审计轨迹' } },
]
