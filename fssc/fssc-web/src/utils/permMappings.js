// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 权限码中文映射共享模块
 * 供 Profile.vue 使用
 */

// 模块中文映射（权限码第一段）
export const MODULE_MAP = {
  exp: '费用报销', bill: '单据管理', wf: '工作流审批', pay: '付款管理',
  md: '主数据', gl: '总账核算', asset: '资产存货', inv: '发票管理',
  system: '系统管理', op: '运营管理', sharing: '共享运营', compliance: '合规风控',
  budget: '预算管理', contract: '合同管理', purchase: '采购管理', treasury: '资金管理',
  report: '报表统计', audit: '审计内控', bank: '银企互联', image: '影像管理',
  archive: '档案管理', receivable: '应收管理', corp: '对公支付', glex: 'GL专项',
  tenant: '租户管理', auth: '认证授权', workbench: '工作台', dashboard: '看板',
  inventory: '存货管理', platform: '平台管理', demo: '演示数据', po: '采购订单',
  policy: '费用政策', voucher: '凭证管理', customer: '客户管理', payplan: '付款计划',
  invoice: '发票管理',
  // 按 sys_menu/后端 @RequiresPerm 实际码审计出的缺失模块
  ai: 'AI 智能', apflow: '应付流程', ar: '应收管理', arflow: '应收流程',
  bcp: '业财协同', collab: '外部协同', returns: '退货管理',
}

// 资源中文映射（权限码第二段）
export const RESOURCE_MAP = {
  bill: '单据', share: '共享单据', trace: '单据轨迹', batch: '付款批次',
  payment: '付款单', match: '发票匹配', recon: '对账', plan: '计划',
  ocr: 'OCR识别', setting: '配置', supplier: '供应商', subject: '会计科目',
  entry: '分录', voucher: '凭证', asset: '资产', inventory: '存货',
  invoice: '发票', verify: '核验', issue: '开票', menu: '菜单',
  role: '角色', user: '用户', org: '组织', dept: '部门',
  flow: '流程', task: '任务', rule: '规则', budget: '预算',
  contract: '合同', order: '订单', report: '报表', archive: '档案',
  bank: '银行账户', bankaccount: '银行账户', account: '账户', treasury: '资金',
  image: '影像', workbench: '工作台', dashboard: '看板', notice: '公告',
  log: '日志', config: '配置', company: '公司', currency: '币种',
  period: '期间', project: '项目', cost: '成本中心', approval: '审批',
  delegate: '委派', transfer: '资金划转', fundplan: '资金计划',
  creditbill: '信贷单据', billsettle: '账单结算', dispatch: '派单',
  archiveaccess: '档案借阅', archivedestroy: '档案销毁', quality: '质检',
  reconcile: '银企对账',
  tenant: '租户', template: '模板', package: '套餐', stat: '平台统计',
  feature: '功能特性', cc: '抄送', bizflow: '业务流程', node: '审批节点',
  model: '流程模型', instance: '流程实例', history: '审批历史',
  consolidate: '合并报表', salary: '薪资', taxprov: '税务计提',
  taxrefund: '退税', taxrate: '税率', tax: '税务', monthclose: '月结',
  trial: '试算平衡', balance: '科目余额', journal: '日记账', inter: '内部往来',
  sharedmaster: '共享主表', exchange: '汇率', exchangerate: '汇率',
  'exchange-rate': '汇率', 'expense-item': '费用项目', 'expense-standard': '费用标准',
  expenseitem: '费用项目', expensestd: '费用标准', costcenter: '成本中心',
  'cost-center': '成本中心', payee: '收款方', city: '城市', post: '岗位',
  dict: '数据字典', file: '文件', job: '定时任务', message: '消息',
  number: '编号规则', status: '系统状态', sod: '职责分离',
  catalog: '合规目录', fraud: '反欺诈', precheck: '事前检查', review: '复核',
  reviewer: '复核员', reviewergroup: '复核组', approvalrule: '审批规则',
  policy: '费用政策', policyversion: '政策版本', policystandard: '政策标准',
  archiveattachment: '档案附件', smartfill: '智能填充', sla: '服务级别',
  satisfaction: '满意度', delivery: '交付', receipt: '入库单',
  pay: '付款', summary: '汇总', adjust: '调整',
  occupy: '预算占用', item: '预算项', scheme: '预算方案',
  ops: '运营看板', compliance: '合规',
  list: '列表', line: '明细行', trail: '审计轨迹', fxreval: '汇兑重估',
  // 审计出的 59 个缺失资源段（左＝权限码第二段，右＝界面上要显示的中文）
  // 报表域子类
  ar: '应收分析', audit: '审计阅示', cash: '资金收支', exp: '费用分析', gl: '凭证统计',
  p2p: '采购到付款', sharing: '运营统计',
  // 主数据域
  book: '账簿', budgetcategory: '预算类别', budgetorg: '预算组织', budgetperiod: '预算期间',
  budgettype: '预算类型', cashflow: '现金流量码', cip: '在建工程', contracttype: '合同类型',
  costCenter: '成本中心', costcentermap: '成本中心映射', employeelevel: '员工等级',
  fininst: '金融机构', form: '表单类型', formmeta: '表单元数据', glconfig: '记账配置',
  incomeexpense: '收支设置', invoiceRule: '发票匹配规则', io: '导入导出模板',
  itembudgetmap: '预算科目映射', itemcashmap: '现金流量码映射', loaninitial: '初始借款余额',
  loanlimit: '借款额度', loanpaytype: '借款付款类型', orgtype: '组织类型', print: '打印模板',
  rate: '费率', std: '费用标准', vehicle: '交通工具',
  // 系统域
  extiface: '外部接口', group: '用户组',
  // 应付/应收流程域
  baseconfig: '基础配置', billing: '开票链', claim: '应收服务申请', close: '月末关账',
  request: '付款服务申请',
  note: '票据', writeoff: '收款核销', baddebt: '坏账',
  // 外部协同 / 退货域
  ccp: '发运协同', scp: '供应链协同', freeze: '冻结状态', objection: '异议审核',
  pur: '采购退货', sale: '销售退货',
  // 流程 / 资金 / 其他
  design: '流程设计', manage: '流程管理', credit: '员工信用', position: '资金头寸',
  taxcertify: '进项认证', taxdecl: '纳税申报', taxrisk: '税务风险', confirm: 'AI票据确认',
}

// 操作中文映射（权限码第三段）
export const ACTION_MAP = {
  list: '查询', view: '查看', detail: '详情', save: '保存', submit: '提交',
  audit: '审批', approve: '审批通过', reject: '驳回', create: '新增',
  add: '新增', update: '修改', edit: '编辑', delete: '删除', remove: '删除',
  withdraw: '撤回', cancel: '取消', void: '作废', execute: '执行',
  confirm: '确认', review: '复核', check: '核对', run: '运行',
  upload: '上传', download: '下载', export: '导出', import: '导入',
  print: '打印', assign: '分配', grant: '授权', revoke: '撤销',
  enable: '启用', disable: '停用', lock: '锁定', unlock: '解锁',
  reset: '重置', refresh: '刷新', sync: '同步', migrate: '迁移',
  all: '全部操作', manage: '管理', operate: '操作', handle: '处理',
  assignMenu: '分配菜单', assignRole: '分配角色', resetPwd: '重置密码',
  freeze: '冻结', unfreeze: '冻结恢复', detect: '检测', offset: '抵消',
  provision: '计提', calculate: '计算', generate: '生成', resolve: '解决',
  sign: '签收', receive: '接收', finish: '完成', send: '发送',
  pay: '支付', post: '过账', close: '关闭', next: '获取下一号',
  bind: '绑定', certify: '认证', destroy: '销毁', archive: '归档',
  request: '申请', recon: '对账', report: '报表', overdue: '逾期查看',
  page: '分页查询', pending: '待处理', model: '模型管理', config: '配置',
  secret: '密钥', adjust: '调整', consolidate: '合并', log: '日志',
  use: '使用',
  // 审计出的 28 个缺失动作码 + 3 个四段式复合动作
  admin: '管理员操作', cashier: '出纳处理', correct: '更正', deduct: '扣减',
  dispatch: '派单', do: '执行', 'dry-run': '试运行', ekp: 'EKP 同步',
  file: '文件', 'force-appr': '强制匹配审批', 'force-appr-l2': '强制匹配二级审批',
  instance: '实例管理', intervene: '干预', issue: '开票', item: '检查项维护',
  manual: '手工处理', query: '查询', reassign: '改派', redflush: '红冲',
  register: '登记', reopen: '重新打开', replace: '替换', scan: '扫描',
  supplier: '供应商操作', test: '连接测试', transfer: '划转', unbind: '解绑', verify: '核验',
  // 四段式权限码（md:formmeta:field:edit 这类）的末两段复合动作
  'field:edit': '字段编辑', 'template:edit': '模板编辑', 'template:import': '模板导入',
}

/**
 * 将权限码（如 exp:bill:audit）翻译为中文描述
 * @param {string} code - 权限码
 * @returns {string} 中文描述，如 "费用报销 → 单据 → 审批"
 */
export function translatePermCode(code) {
  if (!code || typeof code !== 'string') return code
  const parts = code.split(':')
  if (parts.length === 1) return MODULE_MAP[parts[0]] || parts[0]
  if (parts.length === 2) {
    const modLabel = MODULE_MAP[parts[0]] || parts[0]
    const actLabel = ACTION_MAP[parts[1]] || parts[1]
    return `${modLabel} - ${actLabel}`
  }
  // 3段及以上 module:resource:action[:…]。四段式（md:formmeta:field:edit）末两段是复合动作，
  // 原实现只取 parts[2] ⇒ 退化成英文 'field'，必须整段拼回再查名称表。
  const modLabel = MODULE_MAP[parts[0]] || parts[0]
  const resLabel = RESOURCE_MAP[parts[1]] || MODULE_MAP[parts[1]] || parts[1]
  const actLabel = ACTION_MAP[parts.slice(2).join(':')] || parts.slice(2).join(':')
  return `${modLabel} - ${resLabel} - ${actLabel}`
}

/**
 * 判断字符串是否为权限码格式（包含冒号且全英文小写+数字+连字符）
 */
export function isPermCode(str) {
  if (!str || typeof str !== 'string') return false
  return /^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*/.test(str)
}

/**
 * 将菜单树节点的 menuName 翻译为中文
 * 如果 menuName 本身就是中文，直接返回
 * 如果 menuName 是权限码格式，翻译为中文
 */
export function translateMenuName(menuName) {
  if (!menuName) return menuName
  // 已经是中文（含中文字符），直接返回
  if (/[\u4e00-\u9fa5]/.test(menuName)) return menuName
  // 权限码格式，翻译
  if (isPermCode(menuName)) return translatePermCode(menuName)
  // 其他英文，尝试作为模块键翻译
  return MODULE_MAP[menuName] || menuName
}

/**
 * 递归翻译菜单树所有节点的 menuName
 * 返回新树，不修改原数据
 */
export function translateMenuTree(nodes) {
  if (!Array.isArray(nodes)) return nodes
  return nodes.map(n => {
    const translated = { ...n, menuName: translateMenuName(n.menuName) }
    if (n.children && n.children.length) {
      translated.children = translateMenuTree(n.children)
    }
    return translated
  })
}
