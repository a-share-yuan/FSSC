/**
 * 单据类型 -> 业务处理页 分发器。
 *
 * 待办 / 我发起 / 派工池 / 抄送 / 任务池等列表共用：
 * 按 billType（兼容 EXP_ 前缀与裸类型）解析到对应业务模块路由，
 * 并携带 billNo / billId 查询参数，落地页据此自动定位目标记录。
 */

/**
 * 精确映射（类型统一转大写、去 EXP_ 前缀后命中）
 * 注：CLAIM/LOAN/REPAY/PAY 统一直落「我的单据」列表（/expense/my）。
 *     不要指向 /expense —— 该路径会 redirect 到 /expense/claim，那是「员工费用报销」**新建表单**页，
 *     既无列表也无查询，待办点「去处理」会得到一张空白新建单（历史缺陷 的根因之一）。
 *     /expense/my 已支持 ?billNo= 定位与 ?billId= 直接打开详情抽屉。
 *
 * 【为什么要逐类型登记，而不是靠下面的前缀兜底（D-DEC-127/139/148/153 的共同根因）】
 * 前缀兜底只看 bizType 的头两个字母，于是
 *   GL_TAX_PROV / GL_SALARY_PROV 没有 GL 前缀项 ⇒ 解析不到路径 ⇒「去处理」按空白处理；
 *   TR_BANK_RECON / TR_PLAN / TRAIN / TRAVEL 全都以 TR 开头 ⇒ 一律被 ['TR','/treasury'] 吞掉，
 *   而 /treasury 又 redirect 到 /treasury/plan ⇒ 五个不相干的单据共用一个资金计划页，
 *   且落地页拿不到本单上下文（12-30:183 韩雪梅还在该页吃了「缺少权限：treasury:creditbill:list」）。
 * 类型 → 页面 的唯一真值在后端 `BillRouteRegistry.TABLE_LEDGER`（表 → 台账页），
 * 本表是它在「审批工作台·去处理」这条链路上的镜像：**同一判据、两处独立**（与 D-DEC-244 的
 * 凭证来源列同一处置方式——后端 `VoucherService.EXPENSE_BILL_SOURCES` ＋ 前端镜像判定）。
 * 新增业务域时两处都要登记；改这里请顺手核对 `TABLE_LEDGER` 的对应行是否同页。
 */
const BILL_TYPE_ROUTE = {
  CLAIM: '/expense/my',
  APPLY: '/expense/apply',
  LOAN: '/expense/my',
  REPAY: '/expense/my',
  PAY: '/expense/my',
  TREASURY: '/treasury',
  ASSET: '/asset',
  CONTRACT: '/contract',
  PO: '/purchase/order',
  PURCHASE: '/purchase/order',
  PURCHASE_ORDER_REVIEW: '/purchase/order',
  BUDGET: '/budget/adjust',
  RECEIVABLE: '/receivable',
  RECEIVABLE_COLLECT: '/receivable',
  BAD_DEBT: '/receivable',
  INVOICE: '/invoice',
  INVOICE_ISSUE: '/invoice',
  RECON: '/pay/recon',
  RECON_ACTIVE: '/pay/recon',
  ARCHIVE: '/archive',
  FM_ARCHIVE: '/archive',
  FM_BORROW: '/archive',
  IMAGE: '/image',

  // —— 费用族补全：这些类型都以 exp_bill 为载体，直落「我的单据」（同 CLAIM/PAY 口径）。
  //    写在这里的首要目的是**阻断 TR/INV 前缀误吞**——TRAIN/TRAVEL 不是资金单，
  //    INVOICE_MATCH 不是销项发票单（它的单据载体是 exp_bill，见 BillRouteRegistry:308）。
  CLAIM_TRAVEL: '/expense/my',
  TRAVEL: '/expense/my',
  TRAIN: '/expense/my',
  PREPAY: '/expense/my',
  SETTLE: '/expense/my',
  MEETING: '/expense/apply',

  // —— 三单匹配／发票校验派单链（载体＝exp_bill，作业页＝对公三单匹配工作台）——
  INVOICE_MATCH: '/pay/match',
  INVOICE_MATCH_MANUAL: '/pay/match',
  PTP03: '/pay/match',

  // —— GL 总账域（D-DEC-127/139/148：此前整体无登记项 ⇒「去处理」解析不到路径）——
  // 一般记账四合一（gl_journal_entry 一张表按 entryType 分 5 个流程域）⇒ 同一页签，靠 ?billNo= 定位本单。
  GL_ACCRUAL: '/glex/special/journal',
  GL_DEFER: '/glex/special/journal',
  GL_MANUAL_VOUCHER: '/glex/special/journal',
  GL_OTHER_RECEIPT: '/glex/special/journal',
  GL_CHECK_DIFF: '/glex/special/journal',
  GL_TAX_PROV: '/glex/special/tax',
  GL_TAX_PAY: '/glex/special/tax',
  // 计提与发放共用 gl_salary_run 同一行（D-DEC-239 已挂账）⇒ 同页签，链由各自 bizType 区分。
  GL_SALARY_PROV: '/glex/special/salary',
  GL_SALARY_PAY: '/glex/special/salary',
  GL_OFFSET: '/glex/special/inter',
  GL_MONTH_CLOSE: '/glex/special/monthclose',
  GL_MERGE: '/glex/special/consolidate',
  GL_SHARED_MASTER: '/glex/special/master',
  GL_TAX_REFUND: '/glex/tax-refund',

  // —— TR 资金域逐类型登记（D-DEC-153：不再让 TR 前缀把它们全塞进 /treasury/plan）——
  TR_PLAN: '/treasury/plan',
  TR_PLAN_ADJUST: '/treasury/plan',
  TR_RECEIPT: '/treasury/credit',
  TR_TRANSFER: '/treasury/transfer',
  TR_UPLOAD: '/treasury/transfer',
  TR_BILL: '/treasury/settle',
  // 出纳付款的载体是 op_pay_batch（付款批次），与资金计划无关。
  TR_PAY: '/pay',
  TR_PAY_FAIL: '/pay',
  // 对账（bank_reconcile）／银行主数据与印鉴（md_bank_account）：与后端 TABLE_LEDGER 同页。
  TR_BANK_RECON: '/bank/reconcile',
  TR_BANK_MASTER: '/master/bank',
  TR_SEAL_CHANGE: '/master/bank',

  // —— FA 资产域：折旧批的单据表是 asset_depr_batch，不是资产卡片 ——
  FA_DEPRECIATION: '/asset/depr-batch',
  FA_CER: '/asset/asset',

  // —— 其余「后端 TABLE_LEDGER 已登记台账页、此前靠前缀兜底」的类型 ——
  BG_SCHEME: '/budget/scheme',
  BG_APPROVE: '/budget/scheme',
  PAY_PLAN_BASE: '/purchase/pay-plan',
  PAY_PLAN_PLATE: '/purchase/pay-plan',
  SUPPLIER_ADD: '/system/supplier',
  CUSTOMER_MAINT: '/customer',
  CONTRACT_APPROVE: '/contract',
}

// 前缀映射（如 FA03 / BG_ADJUST / RC_XXX / INV_XXX 等复合类型）
// 这只是**未登记类型的兜底**：判据是 bizType 的字符串头部，不是单据归属的表。
//    任何以已登记类型（上面的精确表）命中的都不走这里，所以修跨域误跳的正确做法
//    是**在上面补一行精确项**，而不是加/调前缀（D-DEC-153 的教训）。
const BILL_TYPE_PREFIX = [
  ['FA', '/asset'],
  ['BG', '/budget/adjust'],
  ['RC', '/receivable'],
  ['INV', '/invoice'],
  ['FM', '/archive'],
  ['TR', '/treasury'],
  ['CONT', '/contract'],
]

/** 规范化单据类型：去 EXP_ 前缀、转大写 */
export function normalizeBillType(t) {
  if (!t) return ''
  let s = String(t).trim().toUpperCase()
  if (s.startsWith('EXP_')) s = s.slice(4)
  return s
}

/** 按单据类型解析业务处理页路径；未命中返回 '' */
export function resolveBillRoute(billType) {
  const t = normalizeBillType(billType)
  if (!t) return ''
  if (BILL_TYPE_ROUTE[t]) return BILL_TYPE_ROUTE[t]
  for (const [prefix, route] of BILL_TYPE_PREFIX) {
    if (t.startsWith(prefix)) return route
  }
  return ''
}

/**
 * 跳转到单据对应的业务处理页。
 * @param {object} router vue-router 实例
 * @param {object} row 列表行（兼容 billType/bizType、billNo/bizNo、billId/bizId/id 命名）
 * @returns {boolean} 是否命中路由并已跳转
 */
export function goBizPage(router, row) {
  const billType = row?.billType || row?.bizType || ''
  const path = resolveBillRoute(billType)
  if (!path) return false
  const billNo = row?.billNo || row?.bizNo || ''
  const billId = row?.billId ?? row?.bizId ?? null
  const query = {}
  if (billNo) query.billNo = billNo
  if (billId != null && billId !== '') query.billId = billId
  router.push({ path, query })
  return true
}

/**
 * 读取「去处理／我的单据」深链携带的本单上下文（与 {@link goBizPage} 写出的查询参数成对）。
 *
 * <p>D-DEC-127/139/148/153 的后半段都是同一件事：路径对了，落地页却把 URL 上的
 * billNo/billId 丢掉，列表照旧从第一页全量显示 ⇒ 办理人仍然看不到被审对象本身。
 * 各业务页统一按本函数取参，再各按自己的查询形态落地：
 * <ul>
 *   <li>{@code billNo} — 有服务端 keyword 过滤的列表（GL 专项各页签、银行账户）直接作为
 *       keyword 入参，命中即"只剩这一张"；只有客户端过滤的页（银企对账）写入搜索框变量。</li>
 *   <li>{@code billId} — 页面能按主键取详情时用于自动打开抽屉/表单；否则退化为"落在同一页签"，
 *       并在报告里如实登记该域缺 detail 端点。</li>
 * </ul>
 * 两个都拿不到时返回全空 ⇒ 调用方保持既有默认查询，不做任何跳转态特殊处理。
 *
 * @param {object} route vue-router 的 useRoute() 结果
 * @returns {{billNo: string, billId: number|null, hasAny: boolean}}
 */
export function readBillDeepLink(route) {
  const q = route?.query || {}
  const one = (v) => (Array.isArray(v) ? v[0] : v)
  const billNo = String(one(q.billNo) ?? '').trim()
  const rawId = one(q.billId)
  const asNum = rawId == null || rawId === '' ? NaN : Number(rawId)
  return {
    billNo,
    billId: Number.isFinite(asNum) ? asNum : null,
    hasAny: billNo !== '' || Number.isFinite(asNum),
  }
}

/**
 * 凭证「来源类型」(op_voucher.source_type / op_gl_entry.biz_type) -> 流程单据域 (wf_instance.biz_type)。
 * 全链路联查接口按声明的域取数：费用族的 bill_id 就是 exp_bill 主键 ⇒ 回 null 走费用锚点；
 * 来源词与流程侧 biz_type 不同名的（CONTRACT/TREASURY_PAY/PURCHASE/INVOICE_MATCH）必须换算；
 * 其余（GL_*、EXP_* 等）本身就是 biz_type 字面值，原样回传；
 * 词表里没有的（如 ESTIMATE/PO_RECEIPT）也原样回传，让后端如实报「本域无流程实例」，
 * 而不是回落到按裸主键猜单据——那会把别家公司的费用单当成这张凭证的原始单据。
 *
 * INVOICE_MATCH 归采购域而不是费用族：这张凭证的 bill_id 是 po_order 主键
 * （VoucherService.createInvoiceMatchVoucher 由采购订单直接制证），与 PURCHASE 凭证同一坐标系。
 * 此前按来源词面把它当费用族 ⇒ 撞号读到无关费用单（实证：凭证 19/20 的 bill_id=10
 * 既指向 PO1790334463862，也是费用单 BX202609240006「9月出差打车」的主键）。
 * INVOICE_MATCH_MANUAL 没有制证入口（库内 0 行凭证用它），故不声明域、按字面回传。
 */
const VOUCHER_SOURCE_DOMAIN = {
  CONTRACT: 'CONTRACT_APPROVE',
  TREASURY_PAY: 'TR_PAY',
  PURCHASE: 'PURCHASE_ORDER_REVIEW',
  INVOICE_MATCH: 'PURCHASE_ORDER_REVIEW',
}
const VOUCHER_SOURCE_EXPENSE = ['EXPENSE', 'EXPENSE_PAY', 'EXPENSE_REIM', 'EXPENSE_LOAN', 'EXPENSE_REPAY']

export function bizTypeOfVoucherSource(sourceType) {
  const s = String(sourceType || '').trim().toUpperCase()
  if (!s) return null
  if (VOUCHER_SOURCE_DOMAIN[s]) return VOUCHER_SOURCE_DOMAIN[s]
  if (VOUCHER_SOURCE_EXPENSE.includes(s) || s.startsWith('EXP_')) return null
  return s
}

/**
 * 凭证「来源类型」中文口径（列表列用）。bill_id 是跨域裸主键，只有费用族能解析成费用单号，
 * 其余来源列里显示的是「来源域＋主键」，避免把同号的费用单号当成本张凭证的原始单据（D-DEC-244）。
 */
export const VOUCHER_SOURCE_LABEL = {
  EXPENSE: '费用报销', EXPENSE_PAY: '费用付款', EXP_LOAN: '借款', EXP_REPAY: '还款',
  PURCHASE: '采购入库', INVOICE_MATCH: '三单匹配', INVOICE_MATCH_MANUAL: '手工匹配',
  RECEIVABLE: '应收确认', RECEIVABLE_COLLECT: '应收收款', TREASURY_PAY: '出纳付款',
  BANK_RECON: '银行对账', ASSET_DEPRECIATION: '资产折旧', CONTRACT: '合同', ESTIMATE: '暂估',
  // R5-D-150（2026-10-11 补）：凭证台账来源列实测 RECEIPT_INVOICE/ESTIMATE_REVERSE 直显英文
  // （含「来源单据」列回落成 RECEIPT_INVOICE #12 的形态）；EXPENSE_SETTLE 同域预留。
  RECEIPT_INVOICE: '发票入账', ESTIMATE_REVERSE: '暂估冲回', EXPENSE_SETTLE: '费用结算',
  GL_ACCRUAL: '预提', GL_TAX_PROV: '税金计提', GL_SALARY_PROV: '薪酬计提', GL_SALARY_PAY: '薪酬发放',
  GL_MONTH_CLOSE: '月末结转', BAD_DEBT_WRITEOFF: '坏账核销', BAD_DEBT_REVERSE: '坏账转回',
  AR_CLAIM: '应收认领', AR_NOTE_DISCOUNT: '票据贴现', FX_REVALUATION: '汇兑重估',
  FX_REVAL_REVERSE: '重估冲销', TR_RECEIPT: '票据收票', TR_BILL: '票据开立',
  TR_TRANSFER: '资金调拨', TR_UPLOAD: '资金上存',
  // R5-D-077（2026-10-11 补）：库内 source_type 已长到 20+ 类，逐类补齐；口径＝各制证出口
  // （setSourceType 字面量 / createVoucher sourceType 实参）全量比对，差集必须为空。
  AR_MONTH_CLOSE: 'AR月末结转', BAD_DEBT_PROV: '坏账计提', STOCK_COST_SETTLE: '库存成本结算',
  FA_DEPRECIATION: '资产折旧', FA_CER: '资产立项',
}

export function voucherSourceLabel(sourceType) {
  const s = String(sourceType || '').trim().toUpperCase()
  if (!s) return '未知来源'
  return VOUCHER_SOURCE_LABEL[s] || s
}

/**
 * 与后端 VoucherService.EXPENSE_BILL_SOURCES 同口径：仅这些来源的 bill_id 才是 exp_bill 主键。
 * 后端闸口上线前，列表以此判定 sourceBillNo 是否可信 —— 非费用族即便后端回了单号也不显示，
 * 因为那只是同号费用单被错认成本张凭证的原始单据（D-DEC-244 的 4 例串号）。
 */
const EXPENSE_BILL_SOURCES = ['EXPENSE', 'EXPENSE_PAY', 'EXP_LOAN', 'EXP_REPAY']

export function isExpenseFamilyVoucherSource(sourceType) {
  return EXPENSE_BILL_SOURCES.includes(String(sourceType || '').trim().toUpperCase())
}
