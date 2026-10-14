// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

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
  // 应付域主数据（2026-09-24 补：wf_model.biz_type 有 SUPPLIER_ADD 但字典缺，页面上直显英文码）
  SUPPLIER_ADD: '供应商新增准入',
  // 非流程域的业务码（2026-09-28 枚举直显普查补）：出现在 audit_trail.biz_type／op_gl_entry.biz_type，
  // 但从未在 wf_model 里建模，故原先字典查不到 ⇒ 审计轨迹、总账分录联查直显英文。
  ARCHIVE: '档案', BANK_RECON: '银企对账', PURCHASE: '采购', TREASURY_PAY: '资金付款',
  // 采购订单审批（D-DEC-265 补：wf_instance.biz_type=PURCHASE_ORDER_REVIEW 有 7 行实例，字典缺键 ⇒ 联查抽屉头直显英文域码；
  // 该域在 wf_model 里挂的是 model_key=PTP02-02，标签取其 model_name）
  PURCHASE_ORDER_REVIEW: '采购订单审批',
  // 编号型需求模板落库的 biz_type（wf_model.biz_type 列注释即「业务单据类型(模板模型)」）：
  // 标签取 model_name；与上面 INV_*/GL_* 的正式编码是两套并行编码，勿混用（模板版仅 1 节点，属早期简化流程）。
  'FA11-01': '存货领用出库（模板）', 'FA11-04': '存货处置（模板）', 'GL6-7': '税金计提与支付（模板）',
  'GL8': '薪酬计提与发放（模板）', 'OTC06': '坏账计提与确认（模板）', 'PTP02-02': '采购订单（模板）',
  'TR01-02': '银行印鉴变更（模板）',
  // 工作流引擎自检用的模型（P1 会签 / P2 超时 / G4 自由驳回 批次留下的），并不是业务单据类型；
  // 登记在此只为了让流程配置页不再直显英文码。标签一律带「引擎测试」后缀，勿拿去做业务分流。
  MI_SEQ: '顺序会签（引擎测试）', MI_TEST: '会签（引擎测试）', MI_VOTE: '票签（引擎测试）',
  P2_TO: '超时升级（引擎测试）', P4_G4: '自由驳回复验（引擎测试）',
  // R5-D-056/150（2026-10-11 补）：自动凭证 biz_type 直显英文普查——op_gl_entry.biz_type 实测
  // RECEIPT_INVOICE/ESTIMATE_REVERSE（凭证台账同列）与 ASSET_DEPRECIATION/ESTIMATE/EXPENSE_SETTLE（分录页），
  // 标签与凭证摘要既有措辞对齐（发票入账-xxx／暂估冲回-xxx），勿另造词。
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

export const TASK_TYPE = {
  AUDIT: '审核', BOOKKEEP: '记账', PAY: '付款', RECONCILE: '对账', ARCHIVE: '归档',
  ENTRY: '录入', REVIEW: '复核', VOUCHER: '凭证', IMAGE: '影像', SCAN: '扫描',
}
/**
 * 档案类型（op_archive.archive_type）。历史库内仅 BILL/EXPENSE（31 行），但归档码表实集更宽：
 * ArchiveBoard 八键词表（D-DEC-255 主面）含 VOUCHER/CONTRACT/INVOICE/REPORT/OTHER，
 * 本表被 ArchivePage/BillTraceDrawer 的 enumFormatter 复用（D-DEC-255 残口：VOUCHER 行曾直显英文）。
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
 * 合同类型（cont_contract.contract_type）。词表以库内现值为准（PURCHASE/SERVICE/MAINT/FRAMEWORK/OUTSOURCE）
 * 再加各页历史下拉里的 LEASE/CONSTRUCTION/OTHER 兜底存量显示。
 */
export const CONTRACT_TYPE = {
  PURCHASE: '采购合同', SERVICE: '服务合同', LEASE: '租赁合同', CONSTRUCTION: '工程合同',
  MAINT: '维保合同', FRAMEWORK: '框架合同', OUTSOURCE: '外包合同', OTHER: '其他',
}
/** 资产状态（md_asset.status）。原为 AssetCardTab 本地 map，并入共用以免各页词表分叉。 */
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
/** 应收账款状态（rc_receivable.status ∪ 催收/核销各页历史词表）。库内现值 NONE/NORMAL。 */
export const RC_STATUS = {
  NONE: '未开始', NORMAL: '正常', INIT: '初始化', CONFIRMED: '已确认', PARTIAL: '部分收款',
  DONE: '已结清', OVERDUE: '逾期', WRITE_OFF: '已核销', APPLIED: '已核销',
  // D-DEC-423⑤：收款单登记腿写的是 WRITTEN_OFF（rc_receipt.status），原词表只有 WRITE_OFF/APPLIED
  // 两个同义词、独独缺这一个 ⇒ 「收款核销」tab 状态列直显英文 WRITTEN_OFF（枚举直显族）。
  // 补同义词而不是改后端写值：存量行零订正，读侧即刻出中文。
  WRITTEN_OFF: '已核销',
  PROVISIONED: '已计提', CONFIRMED_WO: '已确认坏账', REVERSED: '已转回',
  RESOLVED: '已解决', PENDING: '待处理',
}
/** 税种（gl_tax_provision.tax_type）。库内现值 INCOME/SURCHARGE/VAT。 */
export const TAX_TYPE = { INCOME: '企业所得税', SURCHARGE: '附加税费', VAT: '增值税', OTHER: '其他' }

export const ACTION = {
  APPROVE: '审批通过', REJECT: '驳回/撤回', WITHDRAW: '撤回', UPDATE: '修改',
  INSERT: '新增', DELETE: '删除', CHECKSUM: '校验失败', PAY: '付款', URGE: '催办',
  TRANSFER: '转办', ADDSIGN: '加签',
}

export const DIRECTION = { DEBIT: '借', CREDIT: '贷', IN: '入库', OUT: '出库' }

export const PERIOD_TYPE = { ANNUAL: '年度', QUARTER: '季度', MONTHLY: '月度', MONTH: '月度', YEAR: '年度', WEEKLY: '周度' }
export const DAY_TYPE = { WORKDAY: '工作日', REST: '休息日', HOLIDAY: '节假日' }
/** 预算/费控的控制方式。后端写入点 BudgetService:564-567 只认 HARD/SOFT/WARN（非法值 fail-closed 成 HARD）；
 *  STRICT/FLEX/WARNING 是早期种子与部分页面下拉用的同义旧码，两套都留着才能覆盖库内实值。 */
export const CONTROL_TYPE = { HARD: '硬控', SOFT: '软控', WARN: '预警', STRICT: '刚性控制', FLEX: '柔性控制', WARNING: '预警控制' }
export const ADJUST_TYPE = { INCREASE: '调增', DECREASE: '调减', TRANSFER: '调剂' }
export const OPER_TYPE = { APPLY: '申请', OCCUPY: '占用', RELEASE: '释放', CONFIRM: '确认', CANCEL: '取消' }
export const ORG_SCOPE = { ORG: '指定公司', ALL: '全集团' }
/**
 * 流程模型业务域（wf_model.category）。2026-09-24 归域后库内 10 种：
 * FA 23 / GL 15 / TR 11 / OTC 10 / EXPENSE 8 / PTP 9 / FM 4 / OTHER 5 / BUDGET 1 / CONTRACT 1。
 *  该列的 DB 注释（EXPENSE/BUDGET/CONTRACT/PAYMENT）与数据长期不一致，历史下拉也只给了
 * EXPENSE/LOAN/PAY/OTHER 四项——LOAN/PAY 一行都用不到，其余 8 种取值当时全是英文。
 *  `TEMPLATE` 不是业务域：WfModelService 从 wf_business_flow 批量建模型时硬编码
 * `m.setCategory("TEMPLATE")`，把**来源**列的取值写进了**分类**列。本账套 18 行已于 2026-09-24
 * 归回真实业务域（权限治理/seed_wfmodel_category_domain_fix_20260924.sql，备份点
 * _bak_wfmodel_category_domain_20260924），后端写入侧同步改为留空。
 * 此键保留是给别的账套/历史数据兜底显示，别再往这列写它。
 * BUDGET/CONTRACT/PAYMENT/OTHER 为归域后可用的取值。
 */
export const WF_MODEL_CATEGORY = {
  EXPENSE: '费用', BUDGET: '预算', CONTRACT: '合同', PAYMENT: '付款',
  FA: '资产·存货', FM: '会计档案', GL: '总账', TR: '资金',
  PTP: '采购付款', OTC: '销售收款',
  TEMPLATE: '未归类（模板导入）', OTHER: '其他',
}
/** 模型来源（wf_model.source）：TEMPLATE 需求模板导入 / USER 用户在流程配置里新建 */
export const WF_MODEL_SOURCE = { TEMPLATE: '需求模板', USER: '用户自定义' }
export const modelCategoryLabel = (v) => (v ? enumLabel(WF_MODEL_CATEGORY, v) : '未归类')
export const modelSourceLabel = (v) => enumLabel(WF_MODEL_SOURCE, v)
// ASSIGN_TYPE（wf_node_config.assign_type）：词表以 DB 列注释为基准——
// USER/ROLE/POST/DEPT_LEADER/DEPT_LEADER_UP/INITIATOR/SHARE_POOL/EXPRESSION。
// DEPT_HEAD / LEADER 两个旧拼写在 DB 里并不存在（节点表单也从未提供），仅作存量展示兜底，勿再写入。
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
// MULTI_TYPE（wf_node_config.multi_type）：**后端已真实消费**（2026-09-23 P1 落地）。
// WfBpmnTemplateService.build() 对 ALL/SEQ/VOTE 生成 BPMN multiInstanceLoopCharacteristics
// （isSequential / completionCondition），WfEngineFacade 启动期注入 <nodeKey>_assignees 候选集合并计票。
// 词表严格对齐 wf_node_config.multi_type 列注释与 WfNodeConfigService.MULTI_TYPES 写入闸：
// 曾误写 SEQUENTIAL（后端不识别 ⇒ 静默退化为单人审批），已移除该拼写，勿再使用。
export const MULTI_TYPE = {
  ANY: '任一通过（单人办理）',
  ALL: '会签（全员通过）',
  SEQ: '顺序会签（逐个办理）',
  VOTE: '票签（按比例通过）',
}
// REJECT_TO：后端无消费方，节点表单已摘除；映射仅为存量数据详情兜底展示，勿再接入编辑控件
export const REJECT_TO = { INITIATOR: '发起人', PREV: '上一节点' }
// 超时动作三档真语义（G3-1）：服务端 WfTimeoutJob.normalizeAction 把下列所有拼法归一后执行——
// 催办（NOTIFY/AUTO_REMIND/REMIND/URGE）、自动通过（AUTO_PASS，写审批历史并给下一环节取人）、
// 升级转办（AUTO_TRANSFER/TRANSFER/ESCALATE/AUTO_ESCALATE/REDISTRIBUTE，沿汇报线派给真实上级）。
// 存量库里只有 NOTIFY 一种值；配置页下拉写回的也是这三个规范值。
// <b>批次 1 ②（白名单制）</b>：AUTO_PASS 还需同时满足 sys_config.wf.timeout.autopass.nodes 含本节点
// （modelKey:nodeKey，缺省为空＝不放行）、单据金额 ≤ wf.timeout.autopass.max_amount、节点非任务池/共享池
// 作业档、非多实例会签，四条门槛任一不过即降级为催办。所以"配了 AUTO_PASS 但没进白名单"是预期行为。
export const TIMEOUT_ACTION = {
  NOTIFY: '催办通知', AUTO_REMIND: '催办通知', REMIND: '催办通知', URGE: '催办通知',
  AUTO_PASS: '超时自动通过',
  AUTO_TRANSFER: '超时升级转办', TRANSFER: '超时升级转办', ESCALATE: '超时升级转办',
  AUTO_ESCALATE: '超时升级转办', REDISTRIBUTE: '超时升级转办',
  // 阶梯档（2026-09-23）：先催办，累计满 sys_config.wf.timeout.escalate.after_urges
  // （缺省 3）次仍无人办理才沿汇报线按 up_level 升级。与 ESCALATE（SLA 一到立即升）的区别＝有缓冲期。
  // 别名 ESCALATE_AFTER_URGES / URGE_THEN_ESCALATE / REMIND_THEN_ESCALATE 同义。
  ESCALATE_AFTER_URGES: '催办N次后升级', URGE_THEN_ESCALATE: '催办N次后升级',
  REMIND_THEN_ESCALATE: '催办N次后升级',
}
export const CFG_TYPE = { THRESHOLD: '金额阈值', TIMEOUT: '超时处理', ACTION: '审批动作', INTEGRATION: '外部集成' }
export const CHECK_ITEM = { VOUCHER: '凭证核对', PAYMENT: '付款核对', STATUS: '状态核对' }
/**
 * 外部接口分类（ext_interface_config.category ＝ integration_call_log.category）。
 * 词表＝后端写入闸 ExtInterfaceConfigService.VALID_CATEGORIES ∪ 库内现值：
 * SMS / TAX_CERTIFY / TAX_DECLARE 三行是白名单机制落地前种子进来的历史值，仍会在列表里出现，故一并登记。
 * 这里出现 SAP/用友/金蝶/EBS 等是**接口通道的厂商取值**（配置页 tab 与 provider 下拉早就这么显示），
 */
export const EXT_INTERFACE_CATEGORY = {
  INVOICE_VERIFY: '发票验真', INVOICE_CERTIFY: '发票认证', OCR: 'OCR 影像识别', AI_MODEL: 'AI 大模型',
  LINKU: 'LinkU 供应商协同', BANK: '银企直连', SMS: '短信服务',
  TAX_CERTIFY: '税务认证', TAX_DECLARE: '税务申报', GOLDEN_TAX: '金税系统',
  ERP: 'ERP 总账对接', GL_PUSH: '总账凭证推送', EKP: 'EKP 审批回调', ECDS: 'ECDS 电票',
  EBS: 'EBS 通道', SAP: 'SAP 通道', YONYOU: '用友通道', KINGDEE: '金蝶通道',
}
/** {PROVIDER}_{ACTION}＝某个 ERP 品牌的具体业务通道（见 ErpChannelResolver），拼成「品牌 动作 通道」。 */
const ERP_BRAND = { EBS: 'EBS', SAP: 'SAP', YONYOU: '用友', KINGDEE: '金蝶', CUSTOM: '自定义' }
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
// 发票验真：后端两套词表并存（OCR 侧 PASS/FAIL，验真服务侧 SUCCESS/FAILED，影像侧 NONE/REPLACED）
export const VERIFY_STATUS = {
  NONE: '未验真', PENDING: '验真中', PASS: '验真通过', SUCCESS: '验真通过',
  FAIL: '验真失败', FAILED: '验真失败', REJECT: '验真未通过', REPLACED: '已被替换',
}
/** 验真状态取中文：空值才是"未验真"，字典外的编码原样显示（BillDetailPanel 曾把未知码一律说成"未验真"）。 */
export const verifyStatusLabel = (v) => (v == null || v === '' || v === 'NONE' ? '未验真' : enumLabel(VERIFY_STATUS, v))
/**
 * 凭证来源类型（op_voucher.source_type）。词表＝DB 列注释全集 + 库内实际出现的 ESTIMATE
 * （列注释里没写它，但暂估凭证确实往这列写 ESTIMATE）。
 * 另注意 CorpPayVoucher 的同名字段存的是**单据类型**（EXPENSE_PAY/EXP_TRAVEL/…），走 BILL_TYPE 口径。
 */
export const VOUCHER_SOURCE = {
  EXPENSE: '费用报账', EXPENSE_PAY: '费用付款', PURCHASE: '采购入库', RECEIVABLE: '应收确认',
  RECEIVABLE_COLLECT: '应收收款', TREASURY_PAY: '出纳付款', BANK_RECON: '银行对账',
  ASSET_DEPRECIATION: '资产折旧', CONTRACT: '合同', ESTIMATE: '暂估',
  // 2026-09-28 只读普查（op_voucher.source_type 实测 15 个取值，缺键即直显英文）：
  // 报销族走单据类型口径，GL 族由 glex 各 Service 写入。
  EXP_LOAN: '借款', EXP_REPAY: '还款', INVOICE_MATCH: '发票校验',
  GL_ACCRUAL: '费用预提', GL_DEFER: '费用待摊', GL_TAX_PROV: '税金计提', GL_TAX_PAY: '税金支付',
  GL_SALARY_PROV: '工资计提', GL_SALARY_PAY: '工资发放',
  // R5-D-150（2026-10-11 补）：凭证台账「来源类型」列实测 RECEIPT_INVOICE/ESTIMATE_REVERSE 直显英文，
  // EXPENSE_SETTLE 同域预留；措辞与 BIZ_TYPE 同名键对齐（发票入账／暂估冲回／费用结算）。
  RECEIPT_INVOICE: '发票入账', ESTIMATE_REVERSE: '暂估冲回', EXPENSE_SETTLE: '费用结算',
}
export const voucherSourceLabel = (v) => enumLabel(VOUCHER_SOURCE, v)

/**
 * 工作流审批动作（ACT_HI / wf_task_history.action）。
 * 注意与 ACTION（操作日志 operType）不是一套词表：这里是流程节点上的动作。
 */
export const WF_ACTION = {
  // D-DEC-363：APPROVED 是实例级终态动作码（SRV WfQueryService.TERMINAL_ACTIONS/INSTANCE_LEVEL_ACTIONS），
  // 与人工票 APPROVE 不是一回事——旧词表两者同显「同意」同色 success，审批轨迹里终态行与投票行混成一列。
  SUBMIT: '提交', APPROVE: '同意', APPROVED: '流程终态·已通过', REJECT: '驳回', TRANSFER: '转办', ADDSIGN: '加签',
  // D-DEC-065：加签审结行自此与发起行分码（SRV WfQueryService.ACTION_ADDSIGN_DONE）。
  // 词表必须先备好：存量老行仍是 ADDSIGN（显「加签」），新行的审结才显「加签审结」。
  ADDSIGN_DONE: '加签审结',
  CANCEL: '撤销', URGE: '催办', AUTO_PASS: '自动通过', TERMINATE: '终止', ASSIGN: '指派',
  REASSIGN: '改派', ESCALATE: '超时升级', WITHDRAW: '撤回',
  // 批次 1：节点空策略兜底的两类系统动作 + 任务池领取/释放 + 作业任务超时回收
  AUTO_SKIP: '自动跳过', CLAIM: '领取任务', UNCLAIM: '释放任务', TIMEOUT_RELEASE: '超时回收',
  // D-DEC-275：待办视图作废（实例终止/会签兄弟清理时的未办理任务）——不是办理、也不是用户撤销
  VOID: '作废',
  // 自由驳回（G4）两段留痕：REJECT_TO＝搬执行前的试探记录（带目标节点），REJECT_TO_DONE＝搬迁已完成
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

const warned = new Set()

/** 通用取值：命中返回中文，未命中返回原码（不吞值）并在开发期告警 */
export function enumLabel(map, v) {
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
 * 单据/业务类型统一取中文：待办的 billType 实际存的是流程 bizType（如 FA_DISPOSE），
 * 报销类又走 BILL_TYPE 词表，故先查 BILL_TYPE 再查 BIZ_TYPE，避免落到「原样显示英文」。
 */
export function docTypeLabel(v) {
  if (v == null || v === '') return ''
  return BILL_TYPE[v] || BIZ_TYPE[v] || enumLabel(BIZ_TYPE, v)
}

/* ============================================================================
 * 域内状态/类型词表（2026-09-28 枚举直显英文普查后并入）
 * 背景：这些编码原先在 2~3 个页面各写一份本地 map，取值漏一个就静默露出英文
 * （Workbench 的 taskType 露 "BOOKKEEP" 即此根因）。词表以「后端写入点 ∪ DB 实测取值」为准，
 * 新增取值须同步补这里，不要再回到页面里手写。
 * ==========================================================================*/

/** 付款状态（exp_bill_payment.pay_status＝PAID/WAITING；op_pay_batch_detail.pay_status＝WAITING/SUCCESS/FAILED） */
export const PAY_STATUS = {
  WAITING: '待付款', PAID: '已付款', SUCCESS: '成功', FAILED: '失败', PARTIAL: '部分付款',
}
export const payStatusLabel = (v) => enumLabel(PAY_STATUS, v)

/** 内部往来抵消方向（gl_inter_offset.inter_flag，写入闸 GlInterOffsetService:141） */
export const INTER_FLAG = { RECEIVABLE: '应收抵消', PAYABLE: '应付抵消' }
export const interFlagLabel = (v) => enumLabel(INTER_FLAG, v)

/**
 * 发票类型（inv_invoice.invoice_type）。R5-D-121：池／认证／验真三页共用一处定义，
 * 禁止组件内局部映射（Invoice.vue 原局部 map 漏 VAT/OTHER 两档导致直显英文）。
 * NORMAL/VATN 为普票历史拼法、SPECIAL 为专票简写，一并登记防回流。
 */
export const INVOICE_TYPE = {
  VAT_SPECIAL: '增值税专用', VAT_NORMAL: '增值税普通', SPECIAL: '增值税专用', NORMAL: '增值税普通',
  ELECTRONIC: '电子发票', TOLL: '通行费', VATN: '增值税普通', VAT: '增值税普通', OTHER: '其他',
}
export const invoiceTypeLabel = (v) => enumLabel(INVOICE_TYPE, v)

/**
 * 发票验真渠道（inv_verify_log.channel）。R5-D-122：调用方写入值有 REALTIME（实时验真）、
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

/** 应收服务申请类型（ar_claim.claim_type，写入点 ArClaimBoard 表单 ∪ ArFlowController 查询） */
export const AR_CLAIM_TYPE = {
  CLAIM_IN: '客户索赔', CLAIM_OUT: '向客户索赔', REVENUE_EST: '收入暂估', THING_ADJ: '应收事物调整',
  AR_AP_OFFSET: '应收应付对冲', NOTE_DISCOUNT: '汇票贴息', OTHER_REV: '其它业务收入',
  OTHER_EXP: '其它业务支出', OTHER_OFFSET: '其它业务对冲', ORDER_UNFRZ: '订单解挂',
}
export const arClaimTypeLabel = (v) => enumLabel(AR_CLAIM_TYPE, v)

/** 应收服务申请流转状态（ar_claim.status） */
export const AR_CLAIM_STATUS = {
  EKP_REVIEW: 'EKP审批中', PENDING_DISPATCH: '待派工', DISPATCHED: '已派工',
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

/** 退货单状态（pur/sal_return.status）——与单据主生命周期不同：这里是「待审核/已审核」口径 */
export const RETURN_STATUS = {
  DRAFT: '草稿', PENDING: '待审核', APPROVED: '已审核', REJECTED: '已驳回', VOIDED: '已作废',
}
export const returnStatusLabel = (v) => enumLabel(RETURN_STATUS, v)

/** 退货方式（pur/sal_return.return_mode） */
export const RETURN_MODE = { REFUND: '退货退款', EXCHANGE: '退货补货' }
export const returnModeLabel = (v) => enumLabel(RETURN_MODE, v)

/** 抽检类型（op_quality.check_type ＝ 质检计划检查项，写入点 QualityCheckService） */
export const checkItemLabel = (v) => enumLabel(CHECK_ITEM, v)

/** 总账共享主数据类别/动作（gl_shared_master.type / .action，写入点 GlSharedMasterService） */
export const MASTER_TYPE = { SUBJECT: '科目', COST_CENTER: '成本中心', BANK: '银行', OTHER: '其他' }
export const MASTER_ACTION = { ADD: '新增', MODIFY: '变更', DELETE: '停用' }
export const masterTypeLabel = (v) => enumLabel(MASTER_TYPE, v)
export const masterActionLabel = (v) => enumLabel(MASTER_ACTION, v)

/**
 * 审计轨迹动作（audit_trail.action）。与流程动作（wf_task_history.action，走 WF_ACTION）是两套词表：
 * 这里是数据变更留痕，UPDATE 由 AuditTrailService.recordUpdate 写死，CHECKSUM 由 recordChecksumFailure 写，
 * VOUCHER_GEN_FAILED 由 ExpenseWfEventListener 写。审计轨迹页此前把原码直显。
 */
export const AUDIT_ACTION = {
  UPDATE: '变更', APPROVE: '同意', REJECT: '驳回', CHECKSUM: '校验和封存', VOUCHER_GEN_FAILED: '凭证生成失败',
}
export const auditActionLabel = (v) => enumLabel(AUDIT_ACTION, v)

/** 站内消息类型（sys_message.msg_type，写入点 notifyUser/notifyService 各调用方） */
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
export const CUSTOMER_TYPE = { CUSTOMER: '客户', SUPPLIER: '供应商', VENDOR: '服务商', CORPORATE: '企业法人', CORP: '企业法人' }
export const customerTypeLabel = (v) => enumLabel(CUSTOMER_TYPE, v)

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

