// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 单据表头必填登记表 —— 红星、前端规则、提交闸门的唯一口径来源（必填项治理 P1）。
 *
 * 为什么要登记表：单据页（报销/差旅/借款/事前申请/对公付款…）共用 BillWorkbench + useBill，
 * 但"提交"按钮在 workbench 里、表头字段在各页面自己的 el-form 上，两边此前各说各话——
 * 页面上的红星只是装饰，真正的提交闸门只在 useBill.submit() 里查了明细行和合规项。
 * 这里把"哪些表头字段必输"抽成一份数据：页面用 billRules() 绑到 :rules（红星由此渲染），
 * useBill.submit() 用 requiredOf() + missingBillField() 拦下未填项（闸门由此判定），一处改动全页面生效。
 *
 * 取值口径与 exp_bill 的 NOT NULL 列一致：title / apply_user_id / apply_date 在库里就是非空列。
 * 单据编号 bill_no 由后端编号规则取号（人工只读），所以不在必填表里——它不该有红星。
 */

const F = (field, label, kind, tip, sub = null, alt = null) => ({ field, label, kind, tip, sub, alt })

/** 每种单据在通用四件套之外的额外必输表头；sub 限定只在该子类型下生效 */
const BY_TYPE = {
  CLAIM: [
    F('periodYear', '预算费用年度', 'select', '请选择预算费用年度'),
    F('extJson.paperAttachCount', '纸质附件张数', 'number', '请填写纸质附件张数'),
    // 后端 assertPayeeAccount：报销单金额>0 时付款去向是资金安全底线字段
    F('payTarget', '收款对象', 'select', '请选择收款对象（员工/供应商/客户）'),
    F('extJson.payMethod', '收款方式', 'select', '请选择收款方式'),
    F('extJson.accountName', '账户名称', 'input', '请输入账户名称'),
    F('payeeAccountNo', '收款账号', 'input', '请输入收款账号（防付错）', null, 'extJson.payeeAccountNo'),
    F('remark', '单据说明', 'input', '请输入单据说明'),
  ],
  PAY: [
    F('periodYear', '预算费用年度', 'select', '请选择预算费用年度'),
    F('extJson.payType', '付款类型', 'select', '请选择付款类型', ['PREPAY', 'SETTLE']),
    F('customerId', '往来单位', 'select', '请选择往来单位（供应商/客户）', ['PREPAY', 'SETTLE']),
    F('extJson.invoiceCount', '发票张数', 'number', '请输入发票张数', ['AGENCY', 'AIR']),
    F('payTarget', '收款对象', 'select', '请选择收款对象（员工/供应商/客户）'),
    F('extJson.payMethod', '收款方式', 'select', '请选择收款方式'),
    F('payeeName', '收款单位/账户名称', 'select', '请选择收款单位'),
    // 后端 assertPayeeAccount：收款对象非空则收款账号必填（防付错）
    F('payeeAccountNo', '收款账号', 'input', '请输入收款方银行账号', null, 'extJson.payeeAccountNo'),
    F('remark', '单据说明', 'input', '请输入单据说明'),
  ],
  APPLY: [
    F('periodYear', '预算费用年度', 'select', '请选择预算费用年度'),
  ],
  LOAN: [],
  REPAY: [],
}

const COMMON = [
  F('applyUserId', '申请人', 'select', '请选择申请人'),
  F('title', '标题', 'input', '请输入单据标题'),
  F('applyDate', '申请日期', 'date', '请选择申请日期'),
  F('currencyCode', '币种', 'select', '请选择币种'),
]

/** 该单据类型（含子类型）当前的必输表头清单 */
export function requiredOf(billType, subType = '') {
  const extra = (BY_TYPE[billType] || []).filter((r) => !r.sub || r.sub.includes(subType))
  return [...COMMON, ...extra]
}

/** 绑到 el-form 的 :rules —— 红星由这份数据渲染，不再手写 required 属性 */
export function billRules(billType, subType = '') {
  const rules = {}
  for (const r of requiredOf(billType, subType)) {
    const rule = {
      required: true,
      message: r.tip,
      trigger: r.kind === 'input' ? 'blur' : 'change',
      // 与 missingBillField 同口径：主路径为空但 alt 落点已填时不算漏填
      // async-validator 第 4 参是整个 model，从中按点路径取 alt
      validator: (rule, value, cb, model) => {
        if (filledAt(value, r.kind)) return cb()
        if (r.alt && model && filledAt(pathValue(model, r.alt), r.kind)) return cb()
        cb(new Error(r.tip))
      },
    }
    // alt 也注册一份：页面把 prop 绑到 extJson.xxx 时同样能出红星并校验
    for (const key of [r.field, r.alt].filter(Boolean)) rules[key] = [rule]
  }
  return rules
}

function filledAt(v, kind) {
  // 数字类只看是否空值，0 算已填
  if (kind === 'number') return v !== null && v !== undefined
  return !(v === null || v === undefined || (typeof v === 'string' && !v.trim()))
}

function pathValue(obj, path) {
  return path.split('.').reduce((o, k) => (o == null ? undefined : o[k]), obj)
}

/** 返回第一个未填写的必输项（null 表示全部合格）；主路径与 alt 落点任一处已填即算合格 */
export function missingBillField(bill, reqList) {
  for (const r of reqList) {
    if (filledAt(pathValue(bill, r.field), r.kind)) continue
    if (r.alt && filledAt(pathValue(bill, r.alt), r.kind)) continue
    return r
  }
  return null
}
