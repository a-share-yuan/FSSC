// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

// 字典读取：同步、只读内存缓存，不发请求。
// 依赖方向：enums.js → useDict.js → api.js，useDict 不反向引用本文件，无循环依赖。
import { dictLabelOf } from '@/composables/useDict.js'

/**
 * 业务枚举 → 中文标签（全系统共用，禁止各页重复定义）。
 * 与 constants/billStatus.js（状态类枚举）并列：本文件收「非状态」枚举 —— 单据类型、业务类型、
 * 作业项、审批动作、分派方式等。缺键时 enumLabel 仍返回原码，但开发期告警，避免英文静默泄露。
 */

export const BIZ_TYPE = {
  APPLY: '事前申请', CLAIM: '费用报销', TRAVEL: '差旅报销', TRAIN: '培训', PAY: '对公付款',
  LOAN: '借款', REPAY: '还款', PREPAY: '预付款', SETTLE: '结算', INVOICE: '发票',
  CONTRACT: '合同', PO: '采购订单', GRN: '收货单', CORP: '对公', EXPENSE: '费用组',
  FUND: '资金组', AR: '应收组', AP: '应付组', GL: '总账', TAX: '税务', TREASURY: '资金',
  ASSET: '资产', BUDGET: '预算', AUDIT: '审核组', OTHER: '其他',
  EXP_CLAIM: '费用报销', EXP_APPLY: '事前申请', EXP_PAY: '对公付款', EXP_LOAN: '借款',
  EXP_REPAY: '员工还款', AGENCY: '代收代付', AIR: '机票结算', MEETING: '会议事前申请',
  // 事前申请三场景：后端 RequisitionWorkbenchService 的场景码 / MyDocumentService 的费用族筛选键
  // 用的就是这三个（不是裸 APPLY），工作台「我的单据」按类型筛选与不支持类型提示都会回显出它们。
  APPLY_MEETING: '会议申请', APPLY_TRAVEL: '出差申请', APPLY_TRAIN: '培训申请',
  CLAIM_TRAVEL: '差旅培训报销', CLAIM_G1: '招待费付款', CONTRACT_APPROVE: '合同审批',
  // 流程模型业务类型（wf_model.model_key 全集，标签取自 wf_model.model_name，去「审批流程」后缀）
  AR_CLAIM: 'AR服务申请', AR_RECON: '应收对账', AR_REPORT: '应收分析报告',
  BAD_DEBT_CONFIRM: '坏账确认', BAD_DEBT_PROV: '坏账计提', BG_SCHEME: '预算方案',
  CUSTOMER_MAINT: '客户主数据维护',
  FA_ADD: '资产新增', FA_ALIENATE: '资产处置（对外销售）', FA_CER: '资本性支出申请',
  FA_CHECK_DIFF: '资产盘点差异', FA_CIP_TO_ASSET: '在建工程转固', FA_DEPRECIATION: '资产折旧摊销',
  FA_DISPOSE: '资产处置（报废/卖出）', FA_IMPAIRMENT: '资产减值准备',
  FA_TRANSFER: '资产调拨（部门间）', FA_TRANSFER_CROSS: '资产调拨（跨法人）', FA_VALUE_ADJUST: '资产原值调整',
  FM_ARCHIVE: '凭证归档', FM_BORROW: '会计凭证借阅', FM_DESTROY: '会计凭证销毁', FM_DOC_FLOW: '单据流转',
  GL_ACCRUAL: '费用预提', GL_DEFER: '费用待摊', GL_MANUAL_VOUCHER: '总账手工凭证', GL_MERGE: '合并报表',
  GL_MONTH_CLOSE: '月结', GL_OFFSET: '内部往来抵消', GL_OTHER_RECEIPT: '其他收款核算',
  GL_SALARY_PAY: '工资发放', GL_SALARY_PROV: '工资计提', GL_SHARED_MASTER: '总账主数据维护',
  GL_TAX_PAY: '税金支付', GL_TAX_PROV: '税金计提', GL_TAX_REFUND: '税务退税',
  INV_DEVAL: '存货跌价准备', INV_DISPOSAL_APPLY: '存货处置申请', INV_DISPOSAL_CHECK: '存货状态确认',
  INV_DISPOSAL_EXEC: '存货处置执行与账务', INV_DISPOSE: '存货报废', INV_INTERNAL_SALE: '存货内部销售',
  INV_ISSUE_NONPROJECT: '存货领用出库（项目外）', INV_ISSUE_PROJECT: '存货领用出库（项目内）',
  INV_PHYSICAL_CHECK: '存货盘点差异',
  INVOICE_ISSUE: '发票开具及收入确认', INVOICE_MATCH: '发票校验异常', INVOICE_MATCH_MANUAL: '发票校验（手工）',
  PAY_PLAN_BASE: '基地采购付款计划', PAY_PLAN_PLATE: '板块采购付款计划',
  RECEIVABLE: '应收收款', RECEIVABLE_COLLECT: '应收催收',
  RECON_ACTIVE: '供应商主动对账', RECON_PASSIVE: '供应商被动对账确认',
  TR_BANK_MASTER: '银行主数据', TR_BANK_RECON: '银行对账', TR_BILL: '票据结算',
  TR_PAY: '资金付款', TR_PAY_FAIL: '支付失败处理', TR_PLAN: '资金计划编制', TR_PLAN_ADJUST: '资金计划调整',
  TR_RECEIPT: '资金收款确认', TR_TRANSFER: '资金上存/调拨', TR_UPLOAD: '资金上存',
  // 应付域主数据：wf_model.biz_type 有 SUPPLIER_ADD，字典缺这个键就会在页面上直接显示英文码。
  SUPPLIER_ADD: '供应商新增准入',
  // 非流程域的业务码：出现在 audit_trail.biz_type／op_gl_entry.biz_type，但从未在 wf_model 里建模。
  // 字典里没有它们，审计轨迹与总账分录联查就会直接显示英文。
  ARCHIVE: '档案', BANK_RECON: '银企对账', PURCHASE: '采购', TREASURY_PAY: '资金付款',
  // 审计轨迹页「业务类型」还有三枚无键值的码（RC_RECEIVABLE、SALE_RETURN、PUR_RETURN）；
  // 标签取业务单据名，不另造域分组词。
  RC_RECEIVABLE: '应收单据', SALE_RETURN: '销售退货', PUR_RETURN: '采购退货',
  // wf_event_action_config.biz_type 的引擎侧业务类型（各 1 行）：收尾动作配置页主标签回落原码，按既有 GL_* 族补全
  GL_CHECK_DIFF: '总账勾稽差异', GL_FX_REVAL: '汇兑重估', GL_SALARY: '工资', GL_TAX: '税金',
  // 采购订单审批（补：wf_instance.biz_type=PURCHASE_ORDER_REVIEW 有 7 行实例，字典缺键 ⇒ 联查抽屉头直接显示英文域码；
  // 该域在 wf_model 里挂的是 model_key=PTP02-02，标签取其 model_name）
  PURCHASE_ORDER_REVIEW: '采购订单审批',
  // 编号型需求模板写入数据库的 biz_type（wf_model.biz_type 列注释即「业务单据类型(模板模型)」）：
  // 标签取 model_name；与上面 INV_*/GL_* 的正式编码是两套并行编码，勿混用（模板版仅 1 节点，属早期简化流程）。
  'FA11-01': '存货领用出库（模板）', 'FA11-04': '存货处置（模板）', 'GL6-7': '税金计提与支付（模板）',
  'GL8': '薪酬计提与发放（模板）', 'OTC06': '坏账计提与确认（模板）', 'PTP02-02': '采购订单（模板）',
  'TR01-02': '银行印鉴变更（模板）',
  // 工作流引擎自检用的模型（会签／超时／自由驳回），不是业务单据类型；
  // 登记在此只为了让流程配置页不再直接显示英文码。标签一律带「引擎测试」后缀，勿拿去做业务分流。
  MI_SEQ: '顺序会签（引擎测试）', MI_TEST: '会签（引擎测试）', MI_VOTE: '票签（引擎测试）',
  P2_TO: '超时升级（引擎测试）', P4_G4: '自由驳回复验（引擎测试）',
  // 自动凭证 biz_type：op_gl_entry.biz_type 上的这几个取值字典里没有就会直显英文，
  // RECEIPT_INVOICE/ESTIMATE_REVERSE（凭证台账同列）与 ASSET_DEPRECIATION/ESTIMATE/EXPENSE_SETTLE（分录页），
  // 标签与凭证摘要的既有措辞对齐（发票入账-xxx／暂估冲回-xxx），勿另造词。
  RECEIPT_INVOICE: '发票入账', ESTIMATE_REVERSE: '暂估冲回', ASSET_DEPRECIATION: '资产折旧',
  ESTIMATE: '暂估', EXPENSE_SETTLE: '费用结算',
}

export const BILL_TYPE = {
  APPLY: '事前申请', CLAIM: '费用报销', TRAVEL: '差旅报销', TRAIN: '培训', PAY: '对公付款',
  LOAN: '借款', REPAY: '还款', PREPAY: '预付款', SETTLE: '结算', CORP: '对公',
  EXP_CLAIM: '费用报销', EXP_TRAVEL: '差旅报销', EXP_LOAN: '借款', EXPENSE_PAY: '对公付款',
  VOUCHER: '凭证',
}
/**
 * 单据子类（exp_bill.bill_sub_type）。列注释即「TRAVEL差旅/DAILY日常/ENTERTAIN招待/TRAIN培训等(取字典)」，
 * 另外企业付款/预付链路会写 PREPAY/SETTLE/AGENCY/AIR，事前申请写 MEETING/TRAIN。
 * ExpenseBillListTab.vue 曾各自手写一份同样的映射（全仓禁止重复定义），已并入这里。
 */
export const BILL_SUB_TYPE = {
  TRAVEL: '差旅', DAILY: '日常', ENTERTAIN: '招待', TRAIN: '培训', MEETING: '会议',
  PREPAY: '预付', SETTLE: '结算', AGENCY: '代收代付', AIR: '机票',
  LOAN: '借款', REPAY: '还款',
}

/**
 * 费用类型（expense_policy.expense_type）。
 * 费用政策页「费用类型」列直接取库里的编码，不翻译就露出 ENTERTAIN/OFFICE/TRAVEL；
 * 取值域＝expense_policy 表现存编码（ENTERTAIN/OFFICE/TRAVEL）∪ 单据子类的同名字段。
 * 注意与 BILL_SUB_TYPE 不是同一列：那边是 exp_bill.bill_sub_type（差旅/日常/招待/培训/会议），
 * 这里多 OFFICE，两者措辞一律用「XX费/XX用品」的名词式。
 */
export const EXPENSE_TYPE = {
  TRAVEL: '差旅费', OFFICE: '办公用品', ENTERTAIN: '业务招待', TRAIN: '培训费',
  MEETING: '会议费', DAILY: '日常费用', PREPAY: '预付', SETTLE: '结算', OTHER: '其他',
}
/** 费用类型取中文；字典外编码不原样输出英文 */
export const expenseTypeLabel = (v) => {
  if (v == null || v === '') return ''
  const label = enumLabel(EXPENSE_TYPE, v)
  return /^[A-Za-z][A-Za-z0-9_]*$/.test(label) ? '其他' : label
}

export const TASK_TYPE = {
  AUDIT: '审核', BOOKKEEP: '记账', PAY: '付款', RECONCILE: '对账', ARCHIVE: '归档',
  ENTRY: '录入', REVIEW: '复核', VOUCHER: '凭证', IMAGE: '影像', SCAN: '扫描',
}
/**
 * 档案类型（op_archive.archive_type）。历史库内仅 BILL/EXPENSE（31 行），但归档码表实集更宽：
 * ArchiveBoard 八键名称表（主面）含 VOUCHER/CONTRACT/INVOICE/REPORT/OTHER，
 * 本表被 ArchivePage/BillTraceDrawer 的 enumFormatter 复用（残口：VOUCHER 行曾直接显示英文）。
 * 增量补齐，不改既有键值。
 */
export const ARCHIVE_TYPE = { BILL: '单据', EXPENSE: '费用', VOUCHER: '凭证', CONTRACT: '合同', INVOICE: '发票', REPORT: '报表', OTHER: '其他' }
/** 任务池状态（op_audit_task.pool_status）。WAITING=待派工池、FINISHED=已办结。 */
export const POOL_STATUS = { WAITING: '待认领', RECEIVED: '已认领', FINISHED: '已完成', CANCELLED: '已取消' }
/** 影像齐套状态（exp_bill.image_status / 档案侧同源）。库内现值 COMPLETE/NONE。 */
export const IMAGE_STATUS = { COMPLETE: '影像齐全', PARTIAL: '部分影像', MISSING: '无影像', NONE: '无影像', PENDING: '采集中' }
/** 实物交接状态（exp_bill.delivery_status）。库内现值 UNSIGNED/SIGNED。 */
export const DELIVERY_STATUS = { UNSIGNED: '未签收', SIGNED: '已签收' }
/**
 * 派工方式（op_dispatch_rule.mode / op_group.task_mode）。
 * 库内现值 SYSTEM/AUTO 与 SYSTEM/MANUAL，两套拼法并存，均登记。
 */
export const DISPATCH_MODE = { SYSTEM: '系统自动', AUTO: '自动派工', MANUAL: '手工派工' }
/**
 * 合同类型（cont_contract.contract_type）。名称表以库内现值为准（PURCHASE/SERVICE/SALES/MAINT/FRAMEWORK/OUTSOURCE）
 * 再加各页历史下拉里的 LEASE/CONSTRUCTION/OTHER，兜住历史数据。SALES（销售合同）在库里有数据、
 * 名称表却没键，报表整列露出英文，故补在正式取值里。
 */
export const CONTRACT_TYPE = {
  PURCHASE: '采购合同', SERVICE: '服务合同', SALES: '销售合同', LEASE: '租赁合同',
  CONSTRUCTION: '工程合同', MAINT: '维保合同', FRAMEWORK: '框架合同', OUTSOURCE: '外包合同', OTHER: '其他',
}
/** 资产状态（md_asset.status）。定义从 AssetCardTab 的本地 map 收上来，避免各页名称表分叉。 */
export const ASSET_STATUS = {
  INIT: '草拟', CIP: '在建工程', USING: '使用中', SCRAP: '已报废',
  DISPOSED: '已处置', TRANSFERRED: '已调拨',
}
/**
 * 银行勾对/对账状态（op_bank_statement.recon_status ∪ bank_reconcile.match_status）。
 * 两表对"未达/未对账"用不同拼法（UNRECON vs UNMATCHED），均登记，勿在后端强行归一。
 */
export const RECON_STATUS = {
  MATCHED: '已勾对', UNMATCHED: '未勾对', UNRECON: '未对账', PENDING: '待处理',
  IGNORED: '已忽略', CONFIRMED: '已确认',
}
/** 应收账款状态（rc_receivable.status ∪ 催收/核销各页历史名称表）。库内现值 NONE/NORMAL。 */
export const RC_STATUS = {
  NONE: '未开始', NORMAL: '正常', INIT: '初始化', CONFIRMED: '已确认', PARTIAL: '部分收款',
  DONE: '已结清', OVERDUE: '逾期', WRITE_OFF: '已核销', APPLIED: '已核销',
  // 收款单登记环节写的是 WRITTEN_OFF（rc_receipt.status），名称表只有 WRITE_OFF/APPLIED
  // 两个同义词，独独缺这一个 ⇒ 「收款核销」tab 状态列直接显示英文 WRITTEN_OFF。
  // 补同义词而不是改后端写值：历史行零订正，读侧即刻出中文。
  WRITTEN_OFF: '已核销',
  PROVISIONED: '已计提', CONFIRMED_WO: '已确认坏账', REVERSED: '已转回',
  // 催收待办（rc_collect_todo.status 注释即「DOING催收中/RESOLVED已解决」）：名称表只有 RESOLVED，
  // 缺 DOING ⇒ 应收单 › 催收待办 tab 状态列直接显示英文，而 DOING 正是这张表的主状态。
  DOING: '催收中',
  RESOLVED: '已解决', PENDING: '待处理',
  // 应收单全额收款后 rc_receivable.status 写 RECEIVED，名称表只有 DONE（已结清）而无 RECEIVED
  // ⇒ 应收列表状态列直接显示英文。
  // 与 DONE 同为「款项已收齐」最终状态，措辞取「已收款」以区别于核销规则的「已结清」。
  RECEIVED: '已收款',
}
/** 税种（gl_tax_provision.tax_type）。库内现值 INCOME/SURCHARGE/VAT。 */
export const TAX_TYPE = { INCOME: '企业所得税', SURCHARGE: '附加税费', VAT: '增值税', OTHER: '其他' }

export const ACTION = {
  APPROVE: '审批通过', REJECT: '驳回/撤回', WITHDRAW: '撤回', UPDATE: '修改',
  INSERT: '新增', DELETE: '删除', CHECKSUM: '校验失败', PAY: '付款', URGE: '催办',
  TRANSFER: '转办', ADDSIGN: '加签',
  // 费用单还款到账时登记的收款事实（ExpenseWfEventListener 写 audit_trail.action）
  FUND_RECEIPT_REGISTER: '收款登记',
}

export const DIRECTION = { DEBIT: '借', CREDIT: '贷', IN: '入库', OUT: '出库' }

export const PERIOD_TYPE = { ANNUAL: '年度', QUARTER: '季度', MONTHLY: '月度', MONTH: '月度', YEAR: '年度', WEEKLY: '周度' }
export const DAY_TYPE = { WORKDAY: '工作日', REST: '休息日', HOLIDAY: '节假日' }
/** 预算/费控的控制方式。后端 BudgetService 的写入校验只认 HARD/SOFT/WARN（非法值 fail-closed 成 HARD）；
 *  STRICT/FLEX/WARNING 是早期种子与部分页面下拉用的同义旧码，两套都留着才能覆盖库内实值。 */
export const CONTROL_TYPE = { HARD: '硬控', SOFT: '软控', WARN: '预警', STRICT: '刚性控制', FLEX: '柔性控制', WARNING: '预警控制' }
export const ADJUST_TYPE = { INCREASE: '调增', DECREASE: '调减', TRANSFER: '调剂' }
export const OPER_TYPE = { APPLY: '申请', OCCUPY: '占用', RELEASE: '释放', CONFIRM: '确认', CANCEL: '取消' }
export const ORG_SCOPE = { ORG: '指定公司', ALL: '全集团' }
/**
 * 流程模型业务域（wf_model.category）。库内在用的 10 种：
 * FA / GL / TR / OTC / EXPENSE / PTP / FM / OTHER / BUDGET / CONTRACT。
 * 该列的 DB 注释（EXPENSE/BUDGET/CONTRACT/PAYMENT）与数据长期不一致，历史下拉也只给了
 * EXPENSE/LOAN/PAY/OTHER 四项——LOAN/PAY 一行都用不到，其余 8 种取值全都会直接露出英文。
 * `TEMPLATE` 不是业务域：WfModelService 从 wf_business_flow 批量建模型时硬编码
 * `m.setCategory("TEMPLATE")`，把来源列的取值写进了分类列。库里这批行已归回真实
 * 业务域，后端写入侧也改为留空。
 * 此键保留是给别的账套/历史数据兜底显示，别再往这列写它。
 * BUDGET/CONTRACT/PAYMENT/OTHER 为归域后可用的取值。
 */
export const WF_MODEL_CATEGORY = {
  EXPENSE: '费用', BUDGET: '预算', CONTRACT: '合同', PAYMENT: '付款',
  FA: '资产·存货', FM: '会计档案', GL: '总账', TR: '资金',
  PTP: '采购付款', OTC: '销售收款',
  // wf_model.category 另有 MD（主数据）没有键 ⇒ 流程配置页模型分类直接显示英文
  MD: '主数据',
  TEMPLATE: '未归类', OTHER: '其他',
}
/** 模型来源（wf_model.source）：TEMPLATE 需求模板导入 / USER 用户在流程配置里新建 */
export const WF_MODEL_SOURCE = { TEMPLATE: '需求模板', USER: '用户自定义' }
export const modelCategoryLabel = (v) => (v ? enumLabel(WF_MODEL_CATEGORY, v) : '未归类')
export const modelSourceLabel = (v) => enumLabel(WF_MODEL_SOURCE, v)
/** 寄件常用快递公司（应收邮包与应付协同共用一份，不要各页面各抄一遍） */
export const COURIER_COMPANIES = ['顺丰速运', '京东物流', '中通快递', '圆通速递', 'EMS', '德邦快递']
// ASSIGN_TYPE（wf_node_config.assign_type）：名称表以 DB 列注释为基准——
// USER/ROLE/POST/DEPT_LEADER/DEPT_LEADER_UP/INITIATOR/SHARE_POOL/EXPRESSION。
// DEPT_HEAD / LEADER 两个旧拼写在 DB 里并不存在（节点表单也从未提供），仅作历史展示兜底，勿再写入。
// DEPT_LEADER_UP 已由 WfEngineFacade.applyNodeConfig 真实消费（沿汇报线取第 up_level 级负责人），
// DEPT_LEADER 同分支（固定 1 级）。
export const ASSIGN_TYPE = {
  USER: '指定用户', ROLE: '指定角色', POST: '指定岗位',
  DEPT_LEADER: '部门负责人', DEPT_LEADER_UP: '逐级上级',
  INITIATOR: '发起人', SHARE_POOL: '共享池', EXPRESSION: '表达式',
  DEPT_HEAD: '部门负责人', LEADER: '发起人上级',
}
/** 节点取人方式（wf_node_config.handling_mode）：与 assign_type（取人范围）正交。POOL 待后续版本。 */
export const HANDLING_MODE = { AUTO: '自动指派', PICK: '上一环节指定', POOL: '任务池领取' }
// MULTI_TYPE（wf_node_config.multi_type）：后端会真实消费这个值。
// WfBpmnTemplateService.build() 对 ALL/SEQ/VOTE 生成 BPMN multiInstanceLoopCharacteristics
// （isSequential / completionCondition），WfEngineFacade 启动期注入 <nodeKey>_assignees 候选集合并计票。
// 名称表严格对齐 wf_node_config.multi_type 列注释与 WfNodeConfigService.MULTI_TYPES 写入校验：
// SEQUENTIAL 这个拼写后端不识别（会静默退化为单人审批），名称表不收录，勿再使用。
export const MULTI_TYPE = {
  ANY: '任一通过（单人办理）',
  ALL: '会签（全员通过）',
  SEQ: '顺序会签（逐个办理）',
  VOTE: '票签（按比例通过）',
}
// REJECT_TO：后端无消费方，节点表单已摘除；映射仅为历史数据详情兜底展示，勿再接入编辑控件
export const REJECT_TO = { INITIATOR: '发起人', PREV: '上一节点' }
// 超时动作三档真语义：服务端 WfTimeoutJob.normalizeAction 把下列所有拼法归一后执行——
// 催办（NOTIFY/AUTO_REMIND/REMIND/URGE）、自动通过（AUTO_PASS，写审批历史并给下一环节取人）、
// 升级转办（AUTO_TRANSFER/TRANSFER/ESCALATE/AUTO_ESCALATE/REDISTRIBUTE，沿汇报线派给真实上级）。
// 历史库里只有 NOTIFY 一种值；配置页下拉写回的也是这三个规范值。
// <b>白名单制</b>：AUTO_PASS 还需同时满足 sys_config.wf.timeout.autopass.nodes 含本节点
// （modelKey:nodeKey，缺省为空＝不放行）、单据金额 ≤ wf.timeout.autopass.max_amount、节点非任务池/共享池
// 作业档、非多实例会签，四条门槛任一不过即降级为催办。所以"配了 AUTO_PASS 但没进白名单"是预期行为。
export const TIMEOUT_ACTION = {
  NOTIFY: '催办通知', AUTO_REMIND: '催办通知', REMIND: '催办通知', URGE: '催办通知',
  AUTO_PASS: '超时自动通过',
  AUTO_TRANSFER: '超时升级转办', TRANSFER: '超时升级转办', ESCALATE: '超时升级转办',
  AUTO_ESCALATE: '超时升级转办', REDISTRIBUTE: '超时升级转办',
  // 阶梯档：先催办，累计满 sys_config.wf.timeout.escalate.after_urges
  // 缺省 3 次仍无人办理才沿汇报线按 up_level 升级。与 ESCALATE（SLA 一到立即升）的区别＝有缓冲期。
  // 别名 ESCALATE_AFTER_URGES / URGE_THEN_ESCALATE / REMIND_THEN_ESCALATE 同义。
  ESCALATE_AFTER_URGES: '催办N次后升级', URGE_THEN_ESCALATE: '催办N次后升级',
  REMIND_THEN_ESCALATE: '催办N次后升级',
}
export const CFG_TYPE = { THRESHOLD: '金额阈值', TIMEOUT: '超时处理', ACTION: '审批动作', INTEGRATION: '外部集成' }
export const CHECK_ITEM = { VOUCHER: '凭证核对', PAYMENT: '付款核对', STATUS: '状态核对' }
/**
 * 质检抽检方式（op_quality.check_type）。名称表与共享质检页创建表单同源
 * （SharingQuality.vue 的 RANDOM/FULL/KEY 下拉），报表页不得各自直接显示英文码。
 */
export const CHECK_SAMPLING = { RANDOM: '随机抽检', FULL: '全检', KEY: '关键检查' }
/**
 * 外部接口分类（ext_interface_config.category ＝ integration_call_log.category）。
 * 名称表＝后端写入校验 ExtInterfaceConfigService.VALID_CATEGORIES ∪ 库内现值：
 * SMS / TAX_CERTIFY / TAX_DECLARE 三行是白名单机制落地前种子进来的历史值，仍会在列表里出现，故一并登记。
 * 其余英文缩写是接口通道的外部系统取值（配置页 tab 与 provider 下拉按取值映射为中性名称显示），
 * 属取值类例外，不计入文案规则。
 */
export const EXT_INTERFACE_CATEGORY = {
  INVOICE_VERIFY: '发票验真', INVOICE_CERTIFY: '发票认证', OCR: 'OCR 影像识别', AI_MODEL: 'AI 大模型',
  LINKU: 'LinkU 供应商协同', BANK: '银企直连', SMS: '短信服务',
  TAX_CERTIFY: '税务认证', TAX_DECLARE: '税务申报', GOLDEN_TAX: '金税系统',
  ERP: 'ERP 总账对接', GL_PUSH: '总账凭证推送', EKP: 'OA 审批回调', ECDS: 'ECDS 电票',
  EBS: '外部财务系统（标准）', SAP: '外部财务系统（备用一）', YONYOU: '外部财务系统（备用二）', KINGDEE: '外部财务系统（备用三）',
}
/** {PROVIDER}_{ACTION}＝某个 ERP 品牌的具体业务通道（见 ErpChannelResolver），拼成「品牌 动作 通道」。 */
const ERP_BRAND = { EBS: '标准', SAP: '备用一', YONYOU: '备用二', KINGDEE: '备用三', CUSTOM: '自定义' }
/** ERP 对接商取值 → 界面中性名称（取值入库不变） */
export const ERP_PROVIDER_LABEL = { EBS: '标准通道', SAP: '备用通道一', YONYOU: '备用通道二', KINGDEE: '备用通道三', CUSTOM: '自定义' }
export function erpProviderLabel(p) {
  return ERP_PROVIDER_LABEL[p] || p || ''
}
const ERP_ACTION = { AP_BLUE: '应付（蓝图）', AP: '应付', ORDER_GET: '订单查询' }
export function extCategoryLabel(v) {
  if (!v) return ''
  if (EXT_INTERFACE_CATEGORY[v]) return EXT_INTERFACE_CATEGORY[v]
  for (const [action, actionLabel] of Object.entries(ERP_ACTION)) {
    if (v.endsWith('_' + action)) {
      const brand = ERP_BRAND[v.slice(0, -(action.length + 1))]
      if (brand) return `${brand} ${actionLabel}通道`
    }
  }
  return enumLabel(EXT_INTERFACE_CATEGORY, v)
}
// 发票验真：后端两套名称表并存（OCR 侧 PASS/FAIL，验真服务侧 SUCCESS/FAILED，影像侧 NONE/REPLACED）
export const VERIFY_STATUS = {
  NONE: '未验真', PENDING: '验真中', PASS: '验真通过', SUCCESS: '验真通过',
  FAIL: '验真失败', FAILED: '验真失败', REJECT: '验真未通过', REPLACED: '已被替换',
  // 历史取值：inv_invoice 现存 3,327 行 verify_status=VERIFIED（verify_time/verify_msg 全空，
  // 当前代码已无写入方）。原样显示英文会被当成系统故障；但它没有验真时间与验真日志佐证，
  // 不能并入「验真通过」，故单列并标注历史。
  VERIFIED: '已验真（历史）',
}
/** 验真状态取中文：空值才是"未验真"；字典外编码一律显示「其他状态」，不再原样输出英文（VERIFIED 曾直显过英文）。 */
export const verifyStatusLabel = (v) => {
  if (v == null || v === '' || v === 'NONE') return '未验真'
  const label = enumLabel(VERIFY_STATUS, v)
  // enumLabel 对未登记值会退回原码；这里挡住英文回显，具体编码在 DEV 控制台仍有 [enums] 告警可查
  return /^[A-Za-z][A-Za-z0-9_]*$/.test(label) ? '其他状态' : label
}
/**
 * 凭证来源类型（op_voucher.source_type）。名称表＝DB 列注释全集 + 库内实际出现的 ESTIMATE
 * （列注释里没写它，但暂估凭证确实往这列写 ESTIMATE）。
 * 另注意 CorpPayVoucher 的同名字段存的是单据类型（EXPENSE_PAY/EXP_TRAVEL/…），走 BILL_TYPE 规则。
 */
export const VOUCHER_SOURCE = {
  EXPENSE: '费用报账', EXPENSE_PAY: '费用付款', PURCHASE: '采购入库', RECEIVABLE: '应收确认',
  RECEIVABLE_COLLECT: '应收收款', TREASURY_PAY: '出纳付款', BANK_RECON: '银行对账',
  ASSET_DEPRECIATION: '资产折旧', CONTRACT: '合同', ESTIMATE: '暂估',
  // op_voucher.source_type 上出现的取值必须都有键，缺键即直接显示英文：
  // 报销族走单据类型规则，GL 族由 glex 各 Service 写入。
  EXP_LOAN: '借款', EXP_REPAY: '还款', INVOICE_MATCH: '发票校验',
  GL_ACCRUAL: '费用预提', GL_DEFER: '费用待摊', GL_TAX_PROV: '税金计提', GL_TAX_PAY: '税金支付',
  GL_SALARY_PROV: '工资计提', GL_SALARY_PAY: '工资发放',
  // 凭证台账「来源类型」列会遇到 RECEIPT_INVOICE/ESTIMATE_REVERSE，没键就直接显示英文，
  // EXPENSE_SETTLE 同域预留；措辞与 BIZ_TYPE 同名键对齐（发票入账／暂估冲回／费用结算）。
  RECEIPT_INVOICE: '发票入账', ESTIMATE_REVERSE: '暂估冲回', EXPENSE_SETTLE: '费用结算',
  // 资产凭证的 source_type 直写 bizType（VoucherService.generateAssetVoucher），
  // 三条资产族出口都会落 op_voucher，没有键 ⇒ 来源类型列直接显示英文。
  // 措辞取后端凭证摘要 prefix 同字，不另造词。
  // 本键集不是唯一出处：凭证台账（pay/CorpPayVoucher）与总账（gl/GlLedger）取词走
  //   utils/bizEntry.js 的 VOUCHER_SOURCE_LABEL，补键必须两边同时补，否则界面上照旧直接显示英文。
  // 两张表另有几处措辞不一致
  //   （EXPENSE/INVOICE_MATCH/GL_ACCRUAL/GL_SALARY_PROV/GL_SALARY_PAY），统一措辞属规则待定项，不要在此擅改。
  FA_ADD: '资产新增', FA_CIP_TO_ASSET: '在建工程转固', FA_DISPOSE: '资产处置',
  // 同族补齐：产品代码里没有写这个 source_type 的出口，摘要一律「费用重分类」
  // ⇒ 判为报表侧模拟写入的数据侧枚举，措辞从数据；详见 bizEntry.js 同名键注释。
  EXPENSE_REIM: '费用重分类',
  // 与 utils/bizEntry.js 的 VOUCHER_SOURCE_LABEL 同步补齐的差集（10-31 实测：凭证生成统计
  INVOICE_MATCH_MANUAL: '手工匹配', GL_MONTH_CLOSE: '月末结转',
  BAD_DEBT_WRITEOFF: '坏账核销', BAD_DEBT_REVERSE: '坏账转回', BAD_DEBT_PROV: '坏账计提',
  AR_CLAIM: '应收认领', AR_NOTE_DISCOUNT: '票据贴现', AR_MONTH_CLOSE: 'AR月末结转',
  FX_REVALUATION: '汇兑重估', FX_REVAL_REVERSE: '重估冲销',
  TR_RECEIPT: '票据收票', TR_BILL: '票据开立', TR_TRANSFER: '资金调拨', TR_UPLOAD: '资金上存',
  STOCK_COST_SETTLE: '库存成本结算', FA_DEPRECIATION: '资产折旧', FA_CER: '资产立项',
  // op_voucher.source_doc_type 会写 PO_RECEIPT/INV_INVOICE，没有键 ⇒ 凭证台账来源列直接显示英文
  PO_RECEIPT: '采购收货', INV_INVOICE: '发票入账',
}
export const voucherSourceLabel = (v) => enumLabel(VOUCHER_SOURCE, v)

/**
 * 凭证字号（op_voucher.voucher_word）。
 * op_voucher.voucher_word 存的是 'JOURNAL'，溯源抽屉凭证卡片按
 * `${voucherWord}-${voucherNo}` 拼接 ⇒ 屏幕上显示成「JOURNAL-JZ2026102700016」。
 * 会计口径的凭证字号是「记／付／转／收」等单字，不是英文枚举，故此处做展示层翻译，
 * 不改后端落库值（历史行已是 JOURNAL，改后端只能覆盖新数据）。
 * 字典外编码不原样输出英文，按 verifyStatusLabel 同款规则退「其他」。
 */
export const VOUCHER_WORD = {
  JOURNAL: '记', GENERAL: '记', RECEIPT: '收', PAYMENT: '付', TRANSFER: '转',
  CASH: '现', BANK: '银', SALARY: '薪', TAX: '税', ASSET: '资',
}
export const voucherWordLabel = (v) => {
  if (v == null || v === '') return '记'
  const label = enumLabel(VOUCHER_WORD, v)
  return /^[A-Za-z][A-Za-z0-9_]*$/.test(label) ? '其他' : label
}

/**
 * 工作流审批动作（ACT_HI / wf_task_history.action）。
 * 注意与 ACTION（操作日志 operType）不是一套名称表：这里是流程节点上的动作。
 */
export const WF_ACTION = {
  // APPROVED 是实例级最终状态动作码（SRV WfQueryService.TERMINAL_ACTIONS/INSTANCE_LEVEL_ACTIONS），
  // 与人工票 APPROVE 不是一回事——旧名称表两者同显「同意」同色 success，审批轨迹里最终状态行与投票行混成一列。
  SUBMIT: '提交', APPROVE: '同意', APPROVED: '流程结束·已通过', REJECT: '驳回', TRANSFER: '转办', ADDSIGN: '加签',
  // 加签审结行自此与发起行分码（SRV WfQueryService.ACTION_ADDSIGN_DONE）。
  // 名称表必须先备好：历史老行仍是 ADDSIGN（显「加签」），新行的审结才显「加签审结」。
  ADDSIGN_DONE: '加签审结',
  CANCEL: '撤销', URGE: '催办', AUTO_PASS: '自动通过', TERMINATE: '终止', ASSIGN: '指派',
  REASSIGN: '改派', ESCALATE: '超时升级', WITHDRAW: '撤回',
  // 节点空策略兜底的两类系统动作 + 任务池领取/释放 + 作业任务超时回收
  AUTO_SKIP: '自动跳过', CLAIM: '领取任务', UNCLAIM: '释放任务', TIMEOUT_RELEASE: '超时回收',
  // 待办视图作废（实例终止/会签兄弟清理时的未办理任务）——不是办理、也不是用户撤销
  VOID: '作废',
  // 自由驳回的两段记录：REJECT_TO＝搬迁执行前的试探记录（带目标节点），REJECT_TO_DONE＝搬迁已完成
  REJECT_TO: '驳回至指定节点', REJECT_TO_DONE: '驳回搬迁完成',
}
export const WF_ACTION_TYPE = {
  SUBMIT: 'primary', APPROVE: 'success', APPROVED: 'info', REJECT: 'danger', TRANSFER: 'warning',
  ADDSIGN: 'warning', ADDSIGN_DONE: 'warning',
  CANCEL: 'info', URGE: 'info', AUTO_PASS: 'success', TERMINATE: 'danger', ASSIGN: 'primary',
  REASSIGN: 'warning', ESCALATE: 'warning', WITHDRAW: 'info',
  AUTO_SKIP: 'info', CLAIM: 'primary', UNCLAIM: 'info', TIMEOUT_RELEASE: 'warning',
  VOID: 'info',
  REJECT_TO: 'danger', REJECT_TO_DONE: 'danger',
}

// 容器对象 → 展示字典 dict_type 的注册表。
// 惰性构建：容器有的定义在本文件后半段（如 MSG_TYPE/RULE_ACTION），直接 new Map 会踩 TDZ，
// 故放在函数体内首次调用时才建。未注册的容器（如样式映射 WF_ACTION_TYPE）返回 undefined，
// enumLabel 直接走本地常量 —— 样式值不是中文标签，本就不该进字典。
/** 本文件已注册的展示字典 dict_type 全集，供入口统一预热。
 *  新增容器时请同步登记到上面的 DICT_OF 与这里的列表，否则该枚举仍走本地常量（不会报错，但改词仍需发版）。
 */
export const ENUM_DICT_TYPES = [
  'enum_action', 'enum_adjust_type', 'enum_ap_mc_status', 'enum_ap_mc_trace', 'enum_ap_req_status', 'enum_ap_req_type',
  'enum_archive_type', 'enum_ar_baddebt_status', 'enum_ar_billing_status', 'enum_ar_claim_status', 'enum_ar_claim_type', 'enum_ar_mc_status',
  'enum_ar_note_status', 'enum_asset_status', 'enum_assign_type', 'enum_audit_action', 'enum_bill_sub_type', 'enum_bill_type',
  'enum_biz_type', 'enum_cfg_type', 'enum_check_item', 'enum_contract_type', 'enum_control_type', 'enum_customer_type',
  'enum_day_type', 'enum_delivery_status', 'enum_direction', 'enum_dispatch_mode', 'enum_ext_interface_category', 'enum_handling_mode',
  'enum_image_status', 'enum_integrity_status', 'enum_inter_flag', 'enum_invoice_channel', 'enum_invoice_type', 'enum_master_action',
  'enum_master_type', 'enum_mc_type', 'enum_msg_type', 'enum_multi_type', 'enum_oper_business_type', 'enum_oper_type',
  'enum_org_scope', 'enum_payee_type', 'enum_pay_status', 'enum_period_type', 'enum_pool_status', 'enum_project_category',
  'enum_rc_status', 'enum_recon_status', 'enum_reject_to', 'enum_return_mode', 'enum_return_status', 'enum_risk_level',
  'enum_rule_action', 'enum_rule_category', 'enum_rule_type', 'enum_task_type', 'enum_tax_type', 'enum_timeout_action',
  'enum_verify_status', 'enum_voucher_source', 'enum_wf_action', 'enum_wf_model_category', 'enum_wf_model_source', 'enum_writeoff_type'
]

let DICT_OF = null
function dictTypeOf(map) {
  if (!DICT_OF) {
    DICT_OF = new Map([
      [ACTION, 'enum_action'],
      [ADJUST_TYPE, 'enum_adjust_type'],
      [AP_MC_STATUS, 'enum_ap_mc_status'],
      [AP_MC_TRACE, 'enum_ap_mc_trace'],
      [AP_REQ_STATUS, 'enum_ap_req_status'],
      [AP_REQ_TYPE, 'enum_ap_req_type'],
      [ARCHIVE_TYPE, 'enum_archive_type'],
      [AR_BADDEBT_STATUS, 'enum_ar_baddebt_status'],
      [AR_BILLING_STATUS, 'enum_ar_billing_status'],
      [AR_CLAIM_STATUS, 'enum_ar_claim_status'],
      [AR_CLAIM_TYPE, 'enum_ar_claim_type'],
      [AR_MC_STATUS, 'enum_ar_mc_status'],
      [AR_NOTE_STATUS, 'enum_ar_note_status'],
      [ASSET_STATUS, 'enum_asset_status'],
      [ASSIGN_TYPE, 'enum_assign_type'],
      [AUDIT_ACTION, 'enum_audit_action'],
      [BILL_SUB_TYPE, 'enum_bill_sub_type'],
      [BILL_TYPE, 'enum_bill_type'],
      [BIZ_TYPE, 'enum_biz_type'],
      [CFG_TYPE, 'enum_cfg_type'],
      [CHECK_ITEM, 'enum_check_item'],
      [CONTRACT_TYPE, 'enum_contract_type'],
      [CONTROL_TYPE, 'enum_control_type'],
      [CUSTOMER_TYPE, 'enum_customer_type'],
      [DAY_TYPE, 'enum_day_type'],
      [DELIVERY_STATUS, 'enum_delivery_status'],
      [DIRECTION, 'enum_direction'],
      [DISPATCH_MODE, 'enum_dispatch_mode'],
      [EXT_INTERFACE_CATEGORY, 'enum_ext_interface_category'],
      [HANDLING_MODE, 'enum_handling_mode'],
      [IMAGE_STATUS, 'enum_image_status'],
      [INTEGRITY_STATUS, 'enum_integrity_status'],
      [INTER_FLAG, 'enum_inter_flag'],
      [INVOICE_CHANNEL, 'enum_invoice_channel'],
      [INVOICE_TYPE, 'enum_invoice_type'],
      [MASTER_ACTION, 'enum_master_action'],
      [MASTER_TYPE, 'enum_master_type'],
      [MC_TYPE, 'enum_mc_type'],
      [MSG_TYPE, 'enum_msg_type'],
      [MULTI_TYPE, 'enum_multi_type'],
      [OPER_BUSINESS_TYPE, 'enum_oper_business_type'],
      [OPER_TYPE, 'enum_oper_type'],
      [ORG_SCOPE, 'enum_org_scope'],
      [PAYEE_TYPE, 'enum_payee_type'],
      [PAY_STATUS, 'enum_pay_status'],
      [PERIOD_TYPE, 'enum_period_type'],
      [POOL_STATUS, 'enum_pool_status'],
      [PROJECT_CATEGORY, 'enum_project_category'],
      [RC_STATUS, 'enum_rc_status'],
      [RECON_STATUS, 'enum_recon_status'],
      [REJECT_TO, 'enum_reject_to'],
      [RETURN_MODE, 'enum_return_mode'],
      [RETURN_STATUS, 'enum_return_status'],
      [RISK_LEVEL, 'enum_risk_level'],
      [RULE_ACTION, 'enum_rule_action'],
      [RULE_CATEGORY, 'enum_rule_category'],
      [RULE_TYPE, 'enum_rule_type'],
      [TASK_TYPE, 'enum_task_type'],
      [TAX_TYPE, 'enum_tax_type'],
      [TIMEOUT_ACTION, 'enum_timeout_action'],
      [VERIFY_STATUS, 'enum_verify_status'],
      [VOUCHER_SOURCE, 'enum_voucher_source'],
      [WF_ACTION, 'enum_wf_action'],
      [WF_MODEL_CATEGORY, 'enum_wf_model_category'],
      [WF_MODEL_SOURCE, 'enum_wf_model_source'],
      [WRITEOFF_TYPE, 'enum_writeoff_type'],
    ])
  }
  return DICT_OF.get(map)
}
const warned = new Set()

/** 通用取值：命中返回中文，未命中返回原码（不吞值）并在开发期告警 */
export function enumLabel(map, v) {
  // ① 字典（运营可维护的权威源，同步读 useDict 内存缓存）
  const dt = dictTypeOf(map)
  if (dt) {
    const fromDict = dictLabelOf(dt, v)
    if (fromDict) return fromDict
  }
  // ② 本地常量兜底：字典未预热/未部署时按本文件的 map 出中文
  const hit = map[v]
  if (hit) return hit
  if (v == null || v === '') return ''
  if (import.meta.env.DEV) {
    const tag = Object.keys(map).join('/')
    if (!warned.has(tag + ':' + v)) {
      warned.add(tag + ':' + v)
      console.warn(`[enums] 未登记的枚举值「${v}」将原样显示为英文，请补入对应字典（已登记：${tag}）`)
    }
  }
  return String(v)
}

/**
 * el-table-column 的 :formatter 适配器，使枚举列无需再各写一个 #default 模板。
 * 用法：`<el-table-column prop="taskType" :formatter="enumFormatter(TASK_TYPE)" />`
 */
export const enumFormatter = (map) => (_row, _col, cellValue) => enumLabel(map, cellValue)

export const bizTypeLabel = (v) => enumLabel(BIZ_TYPE, v)
export const billTypeLabel = (v) => enumLabel(BILL_TYPE, v)
export const billSubTypeLabel = (v) => enumLabel(BILL_SUB_TYPE, v)
export const taskTypeLabel = (v) => enumLabel(TASK_TYPE, v)
export const archiveTypeLabel = (v) => enumLabel(ARCHIVE_TYPE, v)
export const poolStatusLabel = (v) => enumLabel(POOL_STATUS, v)
export const imageStatusLabel = (v) => enumLabel(IMAGE_STATUS, v)
export const deliveryStatusLabel = (v) => enumLabel(DELIVERY_STATUS, v)
export const dispatchModeLabel = (v) => enumLabel(DISPATCH_MODE, v)
export const contractTypeLabel = (v) => enumLabel(CONTRACT_TYPE, v)
export const assetStatusLabel = (v) => enumLabel(ASSET_STATUS, v)
export const reconStatusLabel = (v) => enumLabel(RECON_STATUS, v)
export const rcStatusLabel = (v) => enumLabel(RC_STATUS, v)
export const taxTypeLabel = (v) => enumLabel(TAX_TYPE, v)
export const directionLabel = (v) => enumLabel(DIRECTION, v)
export const actionLabel = (v) => enumLabel(ACTION, v)
export const wfActionLabel = (v) => enumLabel(WF_ACTION, v)
export const wfActionType = (v) => WF_ACTION_TYPE[v] || 'info'

/**
 * 审批链节点状态名称表（/wf/query/chain 的 state 取值域）。
 * 取值域＝后端 WfQueryService.chain() 产出的节点状态；与填单侧 FlowProgressPanel 同一套，
 * 故收在本文件（枚举治理约定：中文名不在各页各写一份）。
 */
export const WF_NODE_STATE = {
  DONE: '已通过', CURRENT: '当前节点', REJECTED: '曾驳回', VOID: '已作废',
  TODO: '尚未到达', SKIPPED: '未经过（他档分支）', ADDSIGN: '加签（临时插一道）',
}
export const WF_NODE_STATE_TYPE = {
  DONE: 'success', CURRENT: 'primary', REJECTED: 'danger', VOID: 'warning',
  TODO: 'info', SKIPPED: 'info', ADDSIGN: 'warning',
}
export const wfNodeStateLabel = (v) => enumLabel(WF_NODE_STATE, v)
export const wfNodeStateType = (v) => WF_NODE_STATE_TYPE[v] || 'info'

/**
 * 单据/业务类型统一取中文：待办的 billType 实际存的是流程 bizType（如 FA_DISPOSE），
 * 报销类又走 BILL_TYPE 名称表，故先查 BILL_TYPE 再查 BIZ_TYPE，避免落到「原样显示英文」。
 */
export function docTypeLabel(v) {
  if (v == null || v === '') return ''
  return BILL_TYPE[v] || BIZ_TYPE[v] || enumLabel(BIZ_TYPE, v)
}

/* ============================================================================
 * 域内状态/类型名称表
 * 背景：这些编码若在各页面各写一份本地 map，取值漏一个就静默露出英文
 * （Workbench 的 taskType 露 "BOOKKEEP" 就是一例）。名称表以「后端写入位置 ∪ DB 实有取值」为准，
 * 新增取值须同步补这里，不要再回到页面里手写。

/** 付款状态（exp_bill_payment.pay_status＝PAID/WAITING；op_pay_batch_detail.pay_status＝WAITING/SUCCESS/FAILED） */
export const PAY_STATUS = {
  WAITING: '待付款', PAID: '已付款', SUCCESS: '成功', FAILED: '失败', PARTIAL: '部分付款',
}
export const payStatusLabel = (v) => enumLabel(PAY_STATUS, v)

/** 内部往来抵消方向（gl_inter_offset.inter_flag，写入校验在 GlInterOffsetService） */
export const INTER_FLAG = { RECEIVABLE: '应收抵消', PAYABLE: '应付抵消' }
export const interFlagLabel = (v) => enumLabel(INTER_FLAG, v)

/**
 * 发票类型（inv_invoice.invoice_type）。池／认证／验真三页共用一处定义，
 * 禁止组件内局部映射：页面自建 map 漏 VAT/OTHER 两档就会直接显示英文。
 * NORMAL/VATN 为普票历史拼法、SPECIAL 为专票简写，一并登记以便回显旧数据。
 */
/**
 * 发票类型。只列 8 型就是没跟上 OCR 扩展的过时残本：
 * 高铁/火车票/机票/出租车/网约车/定额/停车/医疗 8 类缺失，enumLabel 兜底把 RAIL_HIGH 等
 * 英文码直接显示给用户；且 VAT_SPECIAL 作「增值税专用」，与 invoiceType.js 下拉的
 * 「增值税专用发票」不一致 —— 同一条数据下拉一套词、列表另一套词。
 * 已按字典 enum_invoice_type 补全，13 正式型 + 6 历史码逐字一致。
 * 本常量只作字典不可用时的兜底；正常路径 enumLabel() 会优先读字典。
 */
export const INVOICE_TYPE = {
  VAT_SPECIAL: '增值税专用发票', VAT_NORMAL: '增值税普通发票', ELECTRONIC: '电子发票',
  RAIL_HIGH: '高铁 / 动车', TRAIN: '火车票', AIR: '航空运输电子客票', TAXI: '出租车',
  RIDE_HALLING: '网约车', QUOTA: '定额发票', TOLL: '通行费 / ETC', PARKING: '停车费',
  MEDICAL: '医疗收费票据', OTHER: '其他',
  // 历史兼容码：仅回显旧数据（VAT 取 invoiceType.js 规则＝专票，与后端归一逻辑一致）
  SPECIAL: '增值税专用发票', NORMAL: '增值税普通发票', VAT: '增值税专票', VATN: '增值税普票',
  增专: '增值税专用发票', 增普: '增值税普通发票',
}
export const invoiceTypeLabel = (v) => enumLabel(INVOICE_TYPE, v)

/**
 * 发票验真渠道（inv_verify_log.channel）。调用方写入值有 REALTIME（实时验真）、
 * TAX（税局网关）、TAX_BUREAU（历史拼法）、SYSTEM（定时任务）、MANUAL、THIRD_PARTY，
 * 一处定义供验真记录页与详情共用。
 */
export const INVOICE_CHANNEL = {
  TAX_BUREAU: '税局查验', TAX: '税局查验', REALTIME: '实时验真', MANUAL: '人工', SYSTEM: '系统', THIRD_PARTY: '第三方',
}
export const invoiceChannelLabel = (v) => enumLabel(INVOICE_CHANNEL, v)

/** 档案完整性校验结果（op_archive.integrity_status＝SKIP/VERIFIED） */
export const INTEGRITY_STATUS = { SKIP: '未校验', VERIFIED: '已校验', FAILED: '校验失败' }
export const integrityStatusLabel = (v) => enumLabel(INTEGRITY_STATUS, v)

/** 应收服务申请类型（ar_claim.claim_type，写入位置 ArClaimBoard 表单 ∪ ArFlowController 查询） */
export const AR_CLAIM_TYPE = {
  CLAIM_IN: '客户索赔', CLAIM_OUT: '向客户索赔', REVENUE_EST: '收入暂估', THING_ADJ: '应收事物调整',
  AR_AP_OFFSET: '应收应付对冲', NOTE_DISCOUNT: '汇票贴息', OTHER_REV: '其它业务收入',
  OTHER_EXP: '其它业务支出', OTHER_OFFSET: '其它业务对冲', ORDER_UNFRZ: '订单解挂',
}
export const arClaimTypeLabel = (v) => enumLabel(AR_CLAIM_TYPE, v)

/** 应收服务申请流转状态（ar_claim.status） */
export const AR_CLAIM_STATUS = {
  EKP_REVIEW: 'OA审批中', PENDING_DISPATCH: '待派工', DISPATCHED: '已派工',
  POSTED: '已过账', REJECTED: '已驳回', COMPLETED: '已完成',
}
export const arClaimStatusLabel = (v) => enumLabel(AR_CLAIM_STATUS, v)

/** 核销方式（ar_writeoff / rc 核销记录的 writeoff_type、派工规则 claim_type 共用这一套） */
export const WRITEOFF_TYPE = { RECEIPT: '收款核销', NOTE: '票据核销', OFFSET: '对冲核销' }
export const writeoffTypeLabel = (v) => enumLabel(WRITEOFF_TYPE, v)

/** 应收开票前置状态（ar_billing / 开票月报 status） */
export const AR_BILLING_STATUS = {
  TO_INVOICE: '待开票', BIZ_CONFIRMING: '商务确认中', PUSHED: '已推送金税',
  INVOICED: '已开票', VOIDED: '已作废', RED_INVOICED: '已红冲',
}
export const arBillingStatusLabel = (v) => enumLabel(AR_BILLING_STATUS, v)

/**
 * 应收账款对账单状态（ar_statement.status，实体注释即 IMPORTED/RECONCILED/ABNORMAL）。
 * 对账单本身没有状态列，但 EBS 调整单的「关联对账单」下拉把 status 拼进了选项文案，
 * 缺键时下拉里直接出现 IMPORTED。
 */
export const AR_STATEMENT_STATUS = { IMPORTED: '已导入', RECONCILED: '已核对', ABNORMAL: '异常' }
export const arStatementStatusLabel = (v) => enumLabel(AR_STATEMENT_STATUS, v)

/** 应收票据状态（ar_note.status） */
export const AR_NOTE_STATUS = {
  REGISTERED: '待审单', IN_ACCOUNT: '已入账', CUSTODY: '已移交出纳', ENDORSED: '已背书',
  DISCOUNTED: '已贴现', COLLECTING: '待托收', COMPLETED: '已完成', VOIDED: '已作废',
}
export const arNoteStatusLabel = (v) => enumLabel(AR_NOTE_STATUS, v)

/** 坏账处理状态（ar_bad_debt.status / 应收月报） */
export const AR_BADDEBT_STATUS = { PROVISIONED: '已计提', REVERSED: '已转回', WRITTEN_OFF: '已核销' }
export const arBaddebtStatusLabel = (v) => enumLabel(AR_BADDEBT_STATUS, v)

/** 月结类型（ArApReportBoard 月结 tab 的 closeType） */
export const MC_TYPE = { RECEIVABLE: '应收月结', COST: '成本月结', AP_CLOSE: 'AP月末关账' }
export const mcTypeLabel = (v) => enumLabel(MC_TYPE, v)

/** 应收月结检查项状态（ar_month_close.status，含 AP 侧接口清理/扛数各档） */
export const AR_MC_STATUS = {
  INIT: '未开始', DISPATCHED: '已派工', CHECKING: '检查处理中', INTERFACE_CLEARED: '已清接口',
  DATA_PULLED: '已扛数', ADJUSTED: '已调整', GL_POSTED: '已过总账', RECONCILED: '核对通过',
  CLOSED: '账期已关闭', VOID: '已作废',
}
export const arMcStatusLabel = (v) => enumLabel(AR_MC_STATUS, v)

/** AP 月末关账状态（ap_month_close.status，与应收月结是两套档位，勿混用） */
export const AP_MC_STATUS = { INIT: '已发起', DISPATCHED: '已派工', CHECKING: '检查处理中', CLOSED: '关账完成', VOID: '已作废' }
export const apMcStatusLabel = (v) => enumLabel(AP_MC_STATUS, v)

/** 关账追踪动作（ap_month_close 轨迹行 traceType） */
export const AP_MC_TRACE = {
  INIT: '发起关账', DISPATCH: '组长派工', AUTO_CHECK: '前置自动检查', ITEM_OK: '检查项通过',
  ITEM_REJECTED: '检查项退回', ITEM_SUSPENDED: '检查项挂起', ITEM_PENDING: '检查项重开',
  CLOSE: '总账关账', REOPEN: '反关账',
}
export const apMcTraceLabel = (v) => enumLabel(AP_MC_TRACE, v)

/** 应付请求类型（ap_request.req_type） */
export const AP_REQ_TYPE = {
  PREPAY: '预付款', PREPAY_WRITEOFF: '预付款核销', PLAN_PAYMENT: '计划内付款',
  RELATED_PAYMENT: '关联方付款', URGENT_PAYMENT: '紧急付款',
}
export const apReqTypeLabel = (v) => enumLabel(AP_REQ_TYPE, v)

/** 应付请求流转状态（ap_request.status） */
export const AP_REQ_STATUS = {
  PENDING_DISPATCH: '待派工', DISPATCHED: '审单中', GROUP_REVIEW: '组长审核',
  POSTED: '已过账', PAID: '已付款', VOIDED: '已作废', COMPLETED: '已完成',
}
export const apReqStatusLabel = (v) => enumLabel(AP_REQ_STATUS, v)

/** 退货单状态（pur/sal_return.status）——与单据主生命周期不同：这里是「待审核/已审核」规则 */
export const RETURN_STATUS = {
  DRAFT: '草稿', PENDING: '待审核', APPROVED: '已审核', REJECTED: '已驳回', VOIDED: '已作废',
}
export const returnStatusLabel = (v) => enumLabel(RETURN_STATUS, v)

/** 退货方式（pur/sal_return.return_mode） */
export const RETURN_MODE = { REFUND: '退货退款', EXCHANGE: '退货补货' }
export const returnModeLabel = (v) => enumLabel(RETURN_MODE, v)

/** 核对项（CHECK_ITEM 名称表，非 op_quality.check_type） */
export const checkItemLabel = (v) => enumLabel(CHECK_ITEM, v)
export const checkSamplingLabel = (v) => enumLabel(CHECK_SAMPLING, v)

/** 总账共享主数据类别/动作（gl_shared_master.type / .action，写入位置 GlSharedMasterService） */
export const MASTER_TYPE = { SUBJECT: '科目', COST_CENTER: '成本中心', BANK: '银行', OTHER: '其他' }
export const MASTER_ACTION = { ADD: '新增', MODIFY: '变更', DELETE: '停用' }
export const masterTypeLabel = (v) => enumLabel(MASTER_TYPE, v)
export const masterActionLabel = (v) => enumLabel(MASTER_ACTION, v)

/**
 * 审计轨迹动作（audit_trail.action）。与流程动作（wf_task_history.action，走 WF_ACTION）是两套名称表：
 * 这里是数据变更记录，UPDATE 由 AuditTrailService.recordUpdate 写死，CHECKSUM 由 recordChecksumFailure 写，
 * VOUCHER_GEN_FAILED 由 ExpenseWfEventListener 写。审计轨迹页直接展示原码，缺键即露出英文。
 */
export const AUDIT_ACTION = {
  UPDATE: '变更', APPROVE: '同意', REJECT: '驳回', CHECKSUM: '校验和封存', VOUCHER_GEN_FAILED: '凭证生成失败',
}
export const auditActionLabel = (v) => enumLabel(AUDIT_ACTION, v)

/** 站内消息类型（sys_message.msg_type，写入位置 notifyUser/notifyService 各调用方） */
export const MSG_TYPE = {
  APPROVAL: '审批通知', APPROVAL_URGE: '审批催办', ALERT: '预警',
  TIMEOUT_REMINDER: '超时催办', TIMEOUT_ESCALATED: '超时升级', WF_STUCK_ALERT: '卡单预警',
  WF_ADDSIGN: '加签通知', DELEGATE_NEW: '委托生效', DELEGATE_DONE: '委托办结', DELEGATE_TAKEOVER: '委托接管',
  CC_NOTICE: '流程抄送',
  ABNORMAL_INVOICE: '异常发票', ARCHIVE_EXPIRE: '档案到期', DUP_GLOBAL: '全局重复', RC_OVERDUE: '应收逾期',
}
export const msgTypeLabel = (v) => enumLabel(MSG_TYPE, v)

/** 收款方类型（exp_bill_payment.payee_type / md_payee.payee_type） */
export const PAYEE_TYPE = { EMPLOYEE: '员工', SUPPLIER: '供应商', CUSTOMER: '客户' }
export const payeeTypeLabel = (v) => enumLabel(PAYEE_TYPE, v)

/** 往来主体类型（crm_customer.customer_type，库内实值 CUSTOMER/SUPPLIER/CORPORATE/CORP） */
export const CUSTOMER_TYPE = {
  CUSTOMER: '客户', SUPPLIER: '供应商', VENDOR: '服务商', CORPORATE: '企业法人', CORP: '企业法人',
  // crm_customer.customer_type 还有 GENERAL（历史种子数据）⇒ 客户管理「类型」列直接显示英文
  GENERAL: '通用往来',
}
export const customerTypeLabel = (v) => enumLabel(CUSTOMER_TYPE, v)

/** 开票规则拆票维度（ar_invoice_rule.split_dim；库内现值 PO，10/10 行） */
export const AR_SPLIT_DIM = { PO: '采购订单' }
export const arSplitDimLabel = (v) => enumLabel(AR_SPLIT_DIM, v)

/** 共享服务目录类别（service_catalog.category；库内现值 IT，3 行） */
export const SERVICE_CATEGORY = { IT: '信息技术' }
export const serviceCategoryLabel = (v) => enumLabel(SERVICE_CATEGORY, v)

/** 文件存储区域（sys_file.storage_zone；TEMP 6 / ARCHIVE 3 / BUSINESS 2） */
export const FILE_STORAGE_ZONE = { TEMP: '临时区', BUSINESS: '业务区', ARCHIVE: '归档区' }
export const fileStorageZoneLabel = (v) => enumLabel(FILE_STORAGE_ZONE, v)

/** 金融机构类型（md_bank.bank_type；种子 61 行存 BANK，运营新建时可填中文，中文原样放行） */
export const FIN_INST_TYPE = { BANK: '银行' }
export const finInstTypeLabel = (v) => enumLabel(FIN_INST_TYPE, v)

/** 发票识别通道（fssc_image.ocr_record.channel_name；后端 fssc.ocr.channel 取值 gateway/mock/http/paddle，库内为小写） */
export const OCR_CHANNEL = { gateway: '能力网关', mock: '模拟通道', http: '直连通道', paddle: '本地识别' }
export const ocrChannelLabel = (v) => enumLabel(OCR_CHANNEL, v == null ? v : String(v).trim().toLowerCase())

/** 派单规则条件维度（op_dispatch_rule.dispatch_dimension / RuleHit.dimension；库内现值 TYPE 2 行） */
export const DISPATCH_DIMENSION = {
  TYPE: '单据类型', AMOUNT: '金额', LEGAL: '法人主体', COMPLEXITY: '复杂度', REGION: '区域',
  COMPANY: '公司', EXPENSE_ITEM: '费用项', CURRENCY: '币种', TASK_TYPE: '任务类型', ALL: '全部',
}
export const dispatchDimensionLabel = (v) => enumLabel(DISPATCH_DIMENSION, v)

/** 对账明细单据类型（bcp_recon_detail.item_type；现有 PAYABLE/PUR_RECEIPT/PAYMENT/RECEIVABLE/RECEIPT/PRE_INVOICE，FSSC 侧另有 INVOICE/PUR_RETURN/SALE_RETURN） */
export const RECON_ITEM_TYPE = {
  PAYABLE: '应付单', PUR_RECEIPT: '采购入库单', PAYMENT: '付款单', RECEIVABLE: '应收单', RECEIPT: '收款单',
  PRE_INVOICE: '预开票', INVOICE: '发票', PUR_RETURN: '采购退货单', SALE_RETURN: '销售退货单',
}
export const reconItemTypeLabel = (v) => enumLabel(RECON_ITEM_TYPE, v)

/** 操作日志的业务动作（sys_oper_log.business_type，库内实值含 PUBLISH/SUBMIT/VOID，种子与运行时两套码并存） */
export const OPER_BUSINESS_TYPE = {
  INSERT: '新增', UPDATE: '修改', DELETE: '删除', QUERY: '查询', APPROVE: '审批', EXPORT: '导出',
  IMPORT: '导入', PUBLISH: '发布', SUBMIT: '提交', VOID: '作废', OTHER: '其他',
}
export const operBusinessTypeLabel = (v) => enumLabel(OPER_BUSINESS_TYPE, v)

/** 在建工程类别（md_cip.project_category；库内另有 3 行直接存了中文「基建」，属数据侧码值混杂） */
export const PROJECT_CATEGORY = {
  CONSTRUCTION: '建筑安装', EQUIPMENT: '设备', DEFERRED: '待摊', INTEREST: '资本化利息', TECH_REVAMP: '技改工程',
}
export const projectCategoryLabel = (v) => enumLabel(PROJECT_CATEGORY, v)

/** 智能审单规则分类（op_review_rule.rule_category） */
export const RULE_CATEGORY = { BUSINESS: '业务规则', AMOUNT: '金额规则', INVOICE: '发票规则', DEFAULT: '默认规则' }
export const ruleCategoryLabel = (v) => enumLabel(RULE_CATEGORY, v)

/** 合规规则类别/风险档/处置动作（md_compliance_rule.rule_type / .risk_level / .action） */
export const RULE_TYPE = { DUPLICATE_INVOICE: '重复发票', EXPENSE_MISMATCH: '费用不一致', OVER_STANDARD: '超标', BLACKLIST: '黑名单' }
export const RISK_LEVEL = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '严重' }
export const RULE_ACTION = { BLOCK: '拦截', WARN: '警告', REVIEW: '转人工', PASS: '仅提示' }
export const ruleTypeLabel = (v) => enumLabel(RULE_TYPE, v)
export const riskLevelLabel = (v) => enumLabel(RISK_LEVEL, v)
export const ruleActionLabel = (v) => enumLabel(RULE_ACTION, v)

