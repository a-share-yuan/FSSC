// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * useFormRules - 表单联动校验 composable
 * 封装跨字段联动校验规则
 * 统一表单校验规则
 *
 * 用法：
 *   import { useFormRules } from '@/composables/useFormRules'
 *   const { rules, validateForm, validateField } = useFormRules('EXPENSE_CLAIM')
 *   // 在 el-form :rules="rules"
 */

import { ref, computed } from 'vue'
import { i18n } from '../i18n'

function isBlank(v) {
  return v === null || v === undefined || (typeof v === 'string' && !v.trim())
}

// 必填判定一律走自定义 validator：async-validator 只在 rule 的键恰好只剩 required 时才用类型无关的
// required 校验器，多任何一个键（trigger / whitespace / min）它就改走 string 校验器，于是下拉选中的
// 年份与申请人 ID（数字）、开关选"否"（布尔）这些合法值会被判为不合法，表单永远提交不过。
// 自定义 validator 既保留红星（EP 只看 rule.required 标志），又能顺手判空白文本。
const requiredValidator = (msg) => (rule, value, cb) => (isBlank(value) ? cb(new Error(msg)) : cb())

// 通用校验规则（页面级 el-form 的 rules 从这里取，导出以便弹窗直接拼字段→规则映射）
export const commonRules = {
  required: (msg = '此字段为必填') => [{ required: true, message: msg, validator: requiredValidator(msg) }],
  amount: (msg = '金额必须大于 0') => [
    { required: true, type: 'number', min: 0.01, message: msg },
  ],
  // 金额绑在 el-input 上时值是字符串，type:number 会把 "1000" 判成非法，故按文本判数字形态
  amountText: (msg = '请输入金额') => [{
    required: true,
    message: msg,
    validator: (rule, value, cb) => {
      if (isBlank(value)) return cb(new Error(msg))
      if (!isFinite(Number(value)) || Number(value) <= 0) return cb(new Error(`${msg}（须为大于 0 的数字）`))
      cb()
    },
  }],
  // 允许 0 与负数的数字文本（税额可为 0/红字为负），只拦"没填"和"不是数字"
  numberText: (msg = '请输入数字') => [{
    required: true,
    message: msg,
    validator: (rule, value, cb) => {
      if (isBlank(value)) return cb(new Error(msg))
      if (!isFinite(Number(value))) return cb(new Error(`${msg}（须为数字）`))
      cb()
    },
  }],
  amountAllowZero: () => [
    { type: 'number', min: 0, message: '金额不能为负' },
  ],
  // 多选下拉：空数组也算未填（required 校验器只认 null/''，数组要单独判）
  multiSelect: (msg = '请选择') => [{
    required: true,
    message: msg,
    validator: (rule, value, cb) => {
      const empty = Array.isArray(value) ? value.length === 0 : isBlank(value)
      if (empty) return cb(new Error(msg))
      cb()
    },
  }],
  date: (msg = '请选择日期') => [{ required: true, message: msg, validator: requiredValidator(msg) }],
  select: (msg = '请选择') => [{ required: true, message: msg, validator: requiredValidator(msg) }],
  invoiceNo: () => [
    { pattern: /^[0-9]{8,20}$/, message: '发票号应为 8-20 位数字', trigger: 'blur' },
  ],
  phone: () => [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  email: () => [
    { type: 'email', message: '请输入正确的邮箱', trigger: 'blur' },
  ],
  bankAccount: () => [
    { pattern: /^\d{6,30}$/, message: '银行账号应为 6-30 位数字', trigger: 'blur' },
  ],
  maxLength: (n, msg) => [{ max: n, message: msg || `最多 ${n} 个字符`, trigger: 'blur' }],
  // 联行号（选填，填了就必须是 12 位数字）——async-validator 的 pattern 对空串也会判负，
  // 故选填字段一律走自定义 validator：空值放行，非空才校验格式。
  bankCode: (msg = '联行号应为 12 位数字') => [{
    trigger: 'blur',
    validator: (rule, value, cb) => {
      if (isBlank(value)) return cb()
      if (!/^\d{12}$/.test(String(value).trim())) return cb(new Error(msg))
      cb()
    },
  }],
  // 统一社会信用代码（18 位，GB 32100 字符集：数字 + 除 I/O/Z/S/V 外大写字母）——选填同 bankCode 处理
  taxNo: (msg = '请输入正确的统一社会信用代码（18 位）') => [{
    trigger: 'blur',
    validator: (rule, value, cb) => {
      if (isBlank(value)) return cb()
      if (!/^[0-9A-HJ-NPQRTUWXY]{18}$/.test(String(value).trim().toUpperCase())) return cb(new Error(msg))
      cb()
    },
  }],
  // 纳税人识别号：15 位（旧版）/18 位（信用代码）/20 位（部分行业）——选填，填了才校验
  taxNoAny: (msg = '税号应为 15/18/20 位数字或大写字母') => [{
    trigger: 'blur',
    validator: (rule, value, cb) => {
      if (isBlank(value)) return cb()
      if (!/^[0-9A-Z]{15}$|^[0-9A-HJ-NPQRTUWXY]{18}$|^[0-9A-Z]{20}$/.test(String(value).trim().toUpperCase())) return cb(new Error(msg))
      cb()
    },
  }],
  // 电话（手机或固话）：手机 1[3-9]xxxxxxxxx；固话 区号-号码（区号 3-4 位，号码 6-8 位，允许分机）
  phoneOrTel: (msg = '请输入正确的电话号码') => [{
    trigger: 'blur',
    validator: (rule, value, cb) => {
      if (isBlank(value)) return cb()
      const v = String(value).trim()
      if (!/^1[3-9]\d{9}$/.test(v) && !/^0\d{2,3}-?\d{6,8}(-\d{1,5})?$/.test(v)) return cb(new Error(msg))
      cb()
    },
  }],
  // 条件必填 / 依赖校验基元：predicate() 为真且字段为空才报错。
  // 用法：rules.xxx = commonRules.requiredWhen(() => form.warehouse === 'SCRAP', '报废仓必须填写报废原因')
  requiredWhen: (predicate, msg = '此字段在当前条件下为必填') => [{
    validator: (rule, value, cb) => (predicate() && isBlank(value)) ? cb(new Error(msg)) : cb(),
  }],
  // 数值范围校验（金额/数量通用）：空值放行（是否必填由调用方另配 required），越界报错
  amountRange: (min = 0, max = null, msg) => [{
    validator: (rule, value, cb) => {
      if (isBlank(value)) return cb()
      const n = Number(value)
      if (!isFinite(n)) return cb(new Error('请输入数字'))
      if (n < min) return cb(new Error(msg || `不能小于 ${min}`))
      if (max != null && n > max) return cb(new Error(msg || `不能大于 ${max}`))
      cb()
    },
  }],
}

// 按表单类型的联动规则
const formTypeRules = {
  EXPENSE_CLAIM: {
    applyUserId: commonRules.select('请选择申请人'),
    currencyCode: commonRules.select('请选择币种'),
    periodYear: commonRules.select('请选择预算年度'),
    totalAmount: commonRules.amount('请输入报销金额'),
  },
  EXPENSE_TRAVEL: {
    applyUserId: commonRules.select('请选择申请人'),
    currencyCode: commonRules.select('请选择币种'),
    periodYear: commonRules.select('请选择预算年度'),
    totalAmount: commonRules.amount('请输入差旅金额'),
  },
  LOAN: {
    applyUserId: commonRules.select('请选择借款人'),
    currencyCode: commonRules.select('请选择币种'),
    loanAmount: commonRules.amount('请输入借款金额'),
  },
  PREAPPLY: {
    applyUserId: commonRules.select('请选择申请人'),
    currencyCode: commonRules.select('请选择币种'),
    periodYear: commonRules.select('请选择预算年度'),
  },
  EXPENSE: {
    title: commonRules.required('请输入单据标题'),
    applyDate: commonRules.date('请选择申请日期'),
    currencyCode: commonRules.select('请选择币种'),
    // 收款对象/收款账号只在付款类单据渲染（v-if），未挂载时 EP 不校验
    payTarget: commonRules.select('请选择收款对象'),
    payeeAccountNo: commonRules.required('请输入收款账号（防付错）'),
  },
  CORP_PAY: {
    payeeName: commonRules.required('请输入收款方名称'),
    payeeAccount: commonRules.bankAccount(),
    payeeBank: commonRules.required('请输入收款银行'),
    amount: commonRules.amount('请输入付款金额'),
  },
  CONTRACT: {
    contractName: commonRules.required('请输入合同名称'),
    contractType: commonRules.select('请选择合同类型'),
    partyBName: commonRules.required('请输入乙方名称'),
    itemId: commonRules.select('请选择费用类型'),
  },
  BUDGET_ADJUST: {
    schemeId: commonRules.select('请选择预算方案'),
    reason: commonRules.required('请输入调整原因'),
  },
  BUDGET_SCHEME: {
    schemeName: commonRules.required('请输入方案名称'),
    budgetYear: commonRules.required('请输入预算年度'),
    dimension: commonRules.required('请至少选择一个预算维度'),
  },
  BUDGET_ITEM: {
    schemeId: commonRules.select('请选择预算方案'),
    budgetYear: commonRules.required('请输入预算年度'),
    itemId: commonRules.select('请选择费用项'),
    totalAmount: [{ required: true, type: 'number', min: 0, message: i18n.global.t('useFormRules.main.Please') }],
  },
  // 三场景条件必输（出口/即征即退填计税基数+退税率，留抵退税填申请税额），
  // 页面用 v-if 挂载对应表单项，未挂载的字段天然不参与校验，故此处一律按必输登记。
  TAX_REFUND: {
    companyId: commonRules.select('请选择公司'),
    refundType: commonRules.select('请选择退税类型'),
    period: commonRules.date('请选择所属期'),
    baseAmount: commonRules.amount('请输入计税基数（须大于 0）'),
    rate: commonRules.amount('请输入退税率（须大于 0，不超过 100）'),
    taxAmount: commonRules.amount('请输入申请税额（须大于 0）'),
  },
  INVOICE: {
    // 发票号码＝外部票据号（规则 Q1 例外，人工必输）；发票代码在数电票下不存在，故不设为必填
    invoiceNo: [{ required: true, message: '请输入发票号码', validator: requiredValidator('请输入发票号码') },
      { pattern: /^[0-9]{8,20}$/, message: '发票号应为 8-20 位数字' }],
    invoiceDate: commonRules.date('请选择开票日期'),
    sellerName: commonRules.required('请输入销售方名称'),
    // 购买方名称＝发票抬头（字段名沿用实体的 invoiceTitle）
    invoiceTitle: commonRules.required('请输入购买方名称（发票抬头）'),
    currencyCode: commonRules.select('请选择币种'),
    // 金额三元组：后端会校验"价税合计 = 未税 + 税额"，三项都必须有值
    amount: commonRules.amount('请输入价税合计（须大于 0）'),
    netAmount: [{ required: true, type: 'number', min: 0, message: '请输入未税金额（可为 0，不能为负）' }],
    taxAmount: [{ required: true, type: 'number', min: 0, message: '请输入税额（可为 0，不能为负）' }],
    // 生成报销草稿：费用项必选（标题留空由后端按"发票报销-票号"兜底）
    itemId: commonRules.select('请选择报销费用项'),
  },
  INVOICE_BIND: {
    billId: commonRules.select('请选择要关联的业务单据'),
    occupyAmount: commonRules.amount('请输入本次覆盖/占用金额（须大于 0）'),
  },
  PAY_PLAN: {
    planType: commonRules.select('请选择计划类型'),
    supplierId: commonRules.select('请选择供应商'),
    // 选择器虽已 value-format=YYYYMM 且 :editable=false，仍补一道格式闸
    // （防其它入口回填出 2026-11 之类值被后端静默拒绝＝弹窗不关、无红字）。
    // 形状照本文件 GLEX_CONSOLIDATE.period：commonRules.required() 本身返回数组，不能嵌套塞。
    period: [{ required: true, message: '请选择计划期间', validator: requiredValidator('请选择计划期间') },
      { pattern: /^\d{6}$/, message: '期间格式应为 YYYYMM（如 202611）', trigger: 'blur' }],

    amount: commonRules.amount('请输入计划金额（须大于 0）'),
    purpose: commonRules.required('请填写用途说明'),
  },
  PO_ORDER: {
    orderDate: commonRules.date('请选择下单日期'),
    supplierId: commonRules.select('请选择供应商'),
    amount: commonRules.amount('请输入订单金额（须大于 0）'),
    currency: commonRules.select('请选择币种'),
    companyId: commonRules.select('请选择法人主体'),
  },
  PO_RECEIPT: {
    receiptDate: commonRules.date('请选择收货日期'),
    supplierId: commonRules.select('请选择供应商'),
    amount: commonRules.amount('请输入收货金额（须大于 0）'),
  },
  SALES_RETURN: {
    customerCode: commonRules.required('请输入客户代码'),
    billDate: commonRules.date('请选择退货日期'),
    amount: commonRules.amountText('请输入退货金额（须大于 0）'),
    returnMode: commonRules.select('请选择退货方式'),
  },
  PUR_RETURN: {
    vendorCode: commonRules.required('请输入供应商代码'),
    billDate: commonRules.date('请选择退货日期'),
    amount: commonRules.amountText('请输入退货金额（须大于 0）'),
    returnMode: commonRules.select('请选择退货方式'),
  },
  RECEIVABLE: {
    customerId: commonRules.select('请选择往来单位'),
    amount: commonRules.amount('请输入应收金额（须大于 0）'),
    currencyCode: commonRules.select('请选择币种'),
    // 到期日决定逾期计提能否扫到这张单（accrueOverdueBadDebt 用 isNotNull(dueDate) 过滤），留空即永不过期
    dueDate: commonRules.date('请选择到期日'),
  },
  RECEIPT: {
    customerId: commonRules.select('请选择往来单位'),
    amount: commonRules.amount('请输入收款金额（须大于 0）'),
    receiptDate: commonRules.date('请选择收款日期'),
  },
  // 资产卡片：资产编号属主数据编码，按规则 Q1 系统维护、人工只读 ⇒ 不登记为必填；
  // 残值/税法年限/供应商/发票号可空（自建、受赠、CIP 转固等来源本就没有采购发票）。
  ASSET_CARD: {
    assetName: commonRules.required('请输入资产名称'),
    categoryId: commonRules.select('请选择资产类别'),
    deptId: commonRules.select('请选择使用部门'),
    companyId: commonRules.select('请选择所属公司'),
    purchaseDate: commonRules.date('请选择购置日期'),
    originalAmount: commonRules.amount('请输入原值（须大于 0）'),
    depreciationMethod: commonRules.select('请选择折旧方法'),
    // 折旧月数决定月折旧额（computeDepr 里 ≤0 直接抛错），必须是正整数
    depreciationMonths: [{ required: true, type: 'number', min: 1, message: '请输入折旧月数（须大于 0）' }],
  },
  ASSET_CAPITAL: {
    amount: commonRules.amount('请输入支出金额（须大于 0）'),
    assetName: commonRules.required('请输入资产/项目名称'),
    deptId: commonRules.select('请选择使用部门'),
    companyId: commonRules.select('请选择所属公司'),
    remark: commonRules.required('请输入申请说明'),
  },
  ASSET_TRANSFER: {
    targetDeptId: commonRules.select('请选择目标部门'),
  },
  // 资产原值调整：原因必输——原因写死成一句固定文案传给后端的话，
  // 写入数据库的"原因"与真实成因无关，事后追溯看到的是同一句话。金额绑 el-input，值为字符串⇒用 amountText。
  ASSET_ADJUST: {
    newOriginalAmount: commonRules.amountText('请输入调整后原值（须大于 0）'),
    reason: commonRules.required('请填写调整原因'),
  },
  ASSET_CATEGORY: {
    // 默认折旧月数/残值率是「带出规则」而非「入账规则」（资产卡片自身已强制必填），
    // 且历史类别多数为空——登记为必填会让任何一次编辑都被拦下，故只锁编码与名称。
    categoryCode: commonRules.required('请输入类别编码'),
    categoryName: commonRules.required('请输入类别名称'),
  },
  // 存货 7 种操作（入库/出库/领用/处置三段/内部销售）共用一个弹窗：操作专属字段用 v-if 挂载，
  // 未挂载的表单项 EP 不校验，所以这里按「该字段一出现就必输」统一登记即可，无需分操作建 key。
  INVENTORY_OP: {
    itemCode: commonRules.required('请输入物料编码'),
    itemName: commonRules.required('请输入物料名称'),
    warehouseId: commonRules.select('请选择仓库'),
    priceMethod: commonRules.select('请选择计价方式'),
    // 数量为 0 时后端 writeOff 直接抛错（且移动平均下 0 成本核销会污染结存单价），一律锁正值
    qty: [{ required: true, type: 'number', min: 0.01, message: '请输入数量（须大于 0）' }],
    price: [{ required: true, type: 'number', min: 0, message: '请输入入库单价（不能为负）' }],
    // 领用用途 / 处置说明：同一个 extra 字段按操作换文案，审批人靠它判断去向
    extra: commonRules.required('请填写领用用途 / 处置说明'),
  },
  DEPR_BATCH: {
    // 计提期间留空时，服务层会静默按「当前月」补值——用户在跨月补提时以为提的是上月，实际落在本月
    period: commonRules.required('请选择计提期间（格式 yyyy-MM）'),
  },
  // 在建工程卡片：编码是人工维护的主数据编码（非系统取号），与后端「编码唯一/必填」断言对应
  CIP_CARD: {
    cipCode: commonRules.required('请输入编码'),
    cipName: commonRules.required('请输入名称'),
    companyId: commonRules.select('请选择所属公司'),
  },
  // 在建工程转固：转固日期即资产购置日（折旧起算规则），留空会落一张没有购置日的卡片
  CIP_TRANSFER: {
    transferDate: commonRules.date('请选择转固日期'),
    categoryId: commonRules.select('请选择固定资产类别'),
    deptId: commonRules.select('请选择使用部门'),
    depreciationMonths: [{ required: true, type: 'number', min: 1, message: '请输入折旧月数（须大于 0）' }],
  },
  // 资产卡片页的转固入口（/asset/{id}/cipToAsset）：只有转固日期一项人工输入，备注可空
  ASSET_CIP_TRANSFER: {
    transferDate: commonRules.date('请选择转固日期'),
  },
  // 在建工程成本归集（工具行式录入）：类型与金额必输；发生日期可空（后端不补值，留空即无归集日期）
  CIP_COST: {
    costType: commonRules.select('请选择归集类型'),
    amount: commonRules.amount('请输入归集金额（须大于 0）'),
  },
  BANK_CLAIM: {
    claimReceivableId: commonRules.select('请选择应收单'),
  },
  BANK_SLIP: {
    // 归档＝把流水状态推到 ARCHIVED（最终状态），没有影像文件的归档等于凭空造一条"已回单"记录
    slipFileId: commonRules.required('请选择回单影像文件'),
  },
  ARCHIVE_ACCESS: {
    archiveId: commonRules.select('请选择调阅档案'),
    reason: commonRules.required('请填写申请理由'),
  },
  ARCHIVE_DESTROY: {
    archiveIds: commonRules.multiSelect('请选择要销毁的档案'),
    destroyReason: commonRules.required('请填写销毁理由'),
  },
  ARCHIVE_DISPATCH: {
    // 单据标识＝业务单据主键（数字），非自动编号字段，由人工填写；DA-04：非数字后端 500
    billId: [{ required: true, message: '请输入单据标识', validator: requiredValidator('请输入单据标识') },
      { pattern: /^\d+$/, message: '单据标识必须为数字' }],
    billType: commonRules.select('请选择单据类型'),
    // expense_type / amount 在 dispatch_task 表为 NOT NULL，留空/未填直接 500
    expenseType: commonRules.required('请输入费项'),
    amount: [{ required: true, type: 'number', min: 0, message: '请输入金额（不能为负）' }],
    legalEntityId: commonRules.select('请选择法人主体'),
  },
  ARCHIVE_CREATE: {
    archiveType: commonRules.select('请选择档案类型'),
    archiveYear: [{ required: true, type: 'number', min: 2000, message: '请输入年度' }],
    archivePeriod: [{ required: true, message: '请输入期间', validator: requiredValidator('请输入期间') },
      { pattern: /^\d{4}-\d{2}$/, message: '期间格式应为 yyyy-MM' }],
    retentionPeriod: commonRules.select('请选择保管期限'),
  },
  // 进项发票认证：工具条上的所属期/申报期间是必输项（后端 InvoiceService.certify 同一规则拦下）
  INVOICE_CERTIFY: {
    certifyPeriod: commonRules.date('请选择认证所属期'),
  },
  // 合并底稿新建：期间 value-format=YYYYMM（6 位数字），后端 consolidateCreate 同一规则要求非空
  GLEX_CONSOLIDATE: {
    period: [{ required: true, message: '请选择期间', validator: requiredValidator('请选择期间') },
      { pattern: /^\d{6}$/, message: '期间格式应为 YYYYMM（如 202610）', trigger: 'blur' }],
  },
  INVOICE_FILING: {
    summaryPeriod: commonRules.date('请选择申报期间'),
  },
  // 三单匹配容差参数（按公司维护）：容差本身可为 0（0＝不启用该档），故只判「是否填写」
  MATCH_TOLERANCE: {
    companyId: commonRules.select('请选择公司'),
    amountPct: [{ required: true, message: '请填写金额容差百分比' }],
    amountAbs: [{ required: true, message: '请填写绝对值容差' }],
  },
  // 差异化容差规则（维度/公司/供应商）：容差值可为 0（0＝严格按金额一致）
  PAY_TOL_SCOPE: {
    dimensionCode: commonRules.select('请选择维度'),
    toleranceType: commonRules.select('请选择容差类型'),
    toleranceValue: [{ required: true, type: 'number', min: 0, message: '请输入容差值' }],
  },
  // 主数据：供应商 / 客户 / 供应商子表
  // 供应商基本信息 Tab：编码/名称必填；信用代码与税号「选填但填了就必须合规」——
  // jakarta/前端 pattern 对空串天然放行，故基座侧只挂格式校验，必填由页面 rules 另配。
  SUPPLIER: {
    supplierCode: [{ required: true, message: '请输入供应商编码', validator: requiredValidator('请输入供应商编码') },
      { max: 64, message: '供应商编码最多 64 个字符', trigger: 'blur' }],
    supplierName: [{ required: true, message: '请输入供应商名称', validator: requiredValidator('请输入供应商名称') },
      { max: 128, message: '供应商名称最多 128 个字符', trigger: 'blur' }],
    creditCode: commonRules.taxNo('统一社会信用代码应为 18 位（数字+大写字母）'),
    taxNo: commonRules.taxNoAny('税号应为 15/18/20 位数字或大写字母'),
    tel: commonRules.phoneOrTel('请输入正确的电话号码（固话需含区号，如 0731-88888888）'),
    address: commonRules.maxLength(255),
  },
  // 供应商子表-银行账户：账号格式 6-30 位数字（与 md_supplier_bank 后端 @Pattern 同一规则）
  SUPPLIER_BANK: {
    bankName: [{ required: true, message: '请输入开户行', validator: requiredValidator(i18n.global.t('useFormRules.main.Please2')) }],
    bankAccount: [{ required: true, message: '请输入银行账号', validator: requiredValidator(i18n.global.t('useFormRules.main.Enter')) },
      { pattern: /^\d{6,30}$/, message: '银行账号应为 6-30 位数字', trigger: 'blur' }],
  },
  // 供应商子表-联系人：电话/邮箱选填但填了就必须合规
  SUPPLIER_CONTACT: {
    contactName: [{ required: true, message: '请输入联系人姓名', validator: requiredValidator('请输入联系人姓名') }],
    phone: commonRules.phoneOrTel('请输入正确的联系电话'),
    email: commonRules.email('请输入正确的邮箱'),
  },
  // 供应商子表-资质证照：有效期至不得早于生效日（跨字段勾稽，闭包由页面注入）
  SUPPLIER_QUAL: {
    qualName: [{ required: true, message: '请输入证照名称', validator: requiredValidator('请输入证照名称') }],
  },
  // 客户/往来单位：编码系统生成只读；名称/类型必填；电话邮箱选填但校验格式
  CUSTOMER: {
    customerName: [{ required: true, message: '请输入客户名称', validator: requiredValidator('请输入客户名称') },
      { max: 128, message: '客户名称最多 128 个字符', trigger: 'blur' }],
    customerType: commonRules.select('请选择往来类型'),
    contactPhone: commonRules.phoneOrTel('请输入正确的联系电话'),
    email: commonRules.email('请输入正确的邮箱'),
  },
}

/**
 * 校验失败时把视线带到第一个出错的字段。
 *
 * 存在理由：EP 的 validate() reject 之后只是给表单项加 .is-error 红字，页面该停在哪它不管。
 * 字段一多（尤其是弹窗里滚到底才看到提交按钮），人只看到"还有未填项"却找不到是哪一项——
 * 通行做法是保存失败直接定位到第一个不合格字段，这里补齐这一步。
 * @returns {{label: string, prop: string} | null}
 */
export function focusFirstError(formRef) {
  const inst = formRef && typeof formRef.validate === 'function' ? formRef : formRef && formRef.value
  const root = inst && inst.$el
  if (!root || typeof root.querySelector !== 'function') return null
  const item = root.querySelector('.el-form-item.is-error')
  if (!item) return null
  const label = (item.querySelector('.el-form-item__label') || {}).textContent || ''
  const prop = item.getAttribute('data-prop') || ''
  if (typeof item.scrollIntoView === 'function') {
    item.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }
  const input = item.querySelector('input:not([type="hidden"]), textarea, .el-select__wrapper')
  if (input && typeof input.focus === 'function') input.focus({ preventScroll: true })
  return { label: String(label).replace(/[:：*＊\s]+$/, ''), prop }
}

/** 等 Vue 把 .is-error 刷到 DOM 上再定位（validate reject 时 DOM 还没更新）。 */
function nextFrame() {
  return new Promise((resolve) => {
    if (typeof requestAnimationFrame === 'function') requestAnimationFrame(() => resolve())
    else setTimeout(resolve, 16)
  })
}

/**
 * 跑 el-form 校验，失败时错误自动落到对应表单项（EP 行为），并滚到第一处。
 * @returns {Promise<boolean>} true=通过；false=有字段不合法（已向用户展示）
 * 与 BCP / 零售 formRules.js 的 validateForm 同构（三仓同一套 API）。
 */
export async function validateForm(formRef) {
  if (!formRef) return true
  const inst = typeof formRef.validate === 'function' ? formRef : formRef.value
  if (!inst) return true
  try {
    await inst.validate()
    return true
  } catch {
    await nextFrame()
    await nextFrame()
    focusFirstError(inst)
    return false
  }
}

/**
 * 发票价税勾稽：价税合计 = 未税金额 + 税额，容差 1 分。
 * 后端 InvoiceService.requireManualHeader/create 已有同一规则拦截，这里是第一道校验——
 * 录入当时就告知，避免提交后拿 400。三元组互为依赖，勾稽 validator 同时挂三个字段，
 * 任一项 blur 都会连带重校（async-validator 的 validator 拿不到 form 其他字段，故用闭包注入）。
 */
export function invoiceAmountMatch(getForm, msg) {
  const check = (rule, value, cb) => {
    const f = getForm() || {}
    const a = Number(f.amount)
    const n = Number(f.netAmount)
    const t = Number(f.taxAmount)
    if (![a, n, t].every((x) => Number.isFinite(x))) return cb() // 缺项交给各字段必填规则报
    if (Math.abs(a - (n + t)) > 0.01) {
      return cb(new Error(msg || `价税合计应为 未税金额+税额 = ${(n + t).toFixed(2)}，当前 ${a.toFixed(2)}`))
    }
    cb()
  }
  return {
    amount: [{ validator: check }],
    netAmount: [{ validator: check }],
    taxAmount: [{ validator: check }],
  }
}

export function useFormRules(formType) {
  const customRules = computed(() => formTypeRules[formType] || {})

  function getRule(field) {
    return customRules.value[field] || []
  }

  // 预算超限联动校验
  function budgetOverrunValidator(value, budget, callback) {
    if (value && budget && Number(value) > Number(budget)) {
      callback(new Error(`金额 ${value} 超出预算 ${budget}`))
    } else {
      callback()
    }
  }

  // 金额匹配校验
  function amountMatchValidator(invoiceAmount, orderAmount, callback) {
    if (invoiceAmount && orderAmount && Math.abs(Number(invoiceAmount) - Number(orderAmount)) > 0.01) {
      callback(new Error(`发票金额 ${invoiceAmount} 与订单金额 ${orderAmount} 不一致`))
    } else {
      callback()
    }
  }

  return {
    rules: customRules,
    getRule,
    commonRules,
    budgetOverrunValidator,
    amountMatchValidator,
  }
}
