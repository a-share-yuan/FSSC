// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect } from 'vitest'
import { requiredOf, billRules, missingBillField } from './billRequired'

/** 表头必填登记表的三条不变式：闸门/红星同源、子类型限定生效、alt 落点等效 */
describe('constants/billRequired 单据表头必填登记表', () => {
  const base = (over = {}) => ({
    applyUserId: 7, title: '测试单', applyDate: '2026-09-24', currencyCode: 'CNY', ...over,
  })

  it('通用四件套缺任一项即被闸门拦下', () => {
    const req = requiredOf('CLAIM', '')
    for (const f of ['applyUserId', 'title', 'applyDate', 'currencyCode']) {
      const bill = base()
      delete bill[f]
      expect(missingBillField(bill, req)?.field).toBe(f)
    }
    // CLAIM 子类型专属：纸质附件张数/收款对象/收款方式/账户名称/收款账号/单据说明
    expect(missingBillField(base(), req)?.field).toBe('periodYear')
  })

  it('单据编号是系统取号，不参与人工必填判定', () => {
    expect(requiredOf('PAY', 'PREPAY').some((r) => r.field === 'billNo')).toBe(false)
  })

  it('带 sub 的规则只在对应子类型下生效', () => {
    const prepay = requiredOf('PAY', 'PREPAY')
    const agency = requiredOf('PAY', 'AGENCY')
    expect(prepay.some((r) => r.field === 'extJson.payType')).toBe(true)
    expect(agency.some((r) => r.field === 'extJson.payType')).toBe(false)
    expect(agency.some((r) => r.field === 'extJson.invoiceCount')).toBe(true)
    expect(prepay.some((r) => r.field === 'extJson.invoiceCount')).toBe(false)
  })

  it('收款账号写在 extJson 上也算已填（alt 落点等效）', () => {
    const req = requiredOf('PAY', 'AGENCY')
    const bill = base({
      periodYear: 2026, extJson: { invoiceCount: 2, payMethod: 'BANK', payeeAccountNo: '6222001' },
      payTarget: 'SUPPLIER', payeeName: '测试供应商', remark: '代收代付',
    })
    expect(missingBillField(bill, req)).toBe(null)
    // 两处都空才算漏填，且提示指向登记项自身
    bill.extJson.payeeAccountNo = ''
    expect(missingBillField(bill, req)?.field).toBe('payeeAccountNo')
  })

  it('数字类以 0 为已填，字符串以空格为未填', () => {
    const req = requiredOf('PAY', 'AGENCY')
    const bill = base({
      periodYear: 2026, extJson: { invoiceCount: 0, payMethod: 'BANK', payeeAccountNo: '6222001' },
      payTarget: 'SUPPLIER', payeeName: '测试供应商', remark: ' ',
    })
    expect(missingBillField(bill, req)?.field).toBe('remark')
  })

  it('红星规则与闸门取同一份登记表，且 extJson 落点各注册一键', () => {
    const rules = billRules('PAY', 'PREPAY')
    const req = requiredOf('PAY', 'PREPAY')
    for (const r of req) {
      expect(rules[r.field]?.[0]?.required).toBe(true)
      if (r.alt) expect(rules[r.alt]?.[0]?.required).toBe(true)
    }
    // 自定义 validator 存在时 async-validator 不再跑内置 required，故提示语必须自带
    expect(rules.title[0].message).toBe('请输入单据标题')
  })

  it('未登记类型只按通用四件套判定', () => {
    expect(requiredOf('LOAN', '').map((r) => r.field))
      .toEqual(['applyUserId', 'title', 'applyDate', 'currencyCode'])
    expect(missingBillField(base(), requiredOf('LOAN', ''))).toBe(null)
  })
})
