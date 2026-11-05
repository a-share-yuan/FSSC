// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect } from 'vitest'
import { rowDetailItems, COMMON_LABELS } from './format'

// 全表护栏：COMMON_LABELS 是 42 个行详情弹窗共用的唯一中文来源（多数页面不传自己的 labels），
// 这里逐枚断言"标签必须是中文"，谁再往里写成英文或漏登记，单测直接红。
describe('COMMON_LABELS - 行详情弹窗中文标签全表', () => {
  const keys = Object.keys(COMMON_LABELS)

  it('每条标签都含中文', () => {
    const bad = keys.filter((k) => !/[一-鿿]/.test(COMMON_LABELS[k]))
    expect(bad, `英文标签: ${bad.join(', ')}`).toEqual([])
  })

  it('按这些键名摊开成行对象时，没有一条标签是 prettyKey() 生成的英文标题', () => {
    const prettyKey = (k) => {
      const s = k.replace(/([a-z\d])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim()
      return s.charAt(0).toUpperCase() + s.slice(1)
    }
    const row = Object.fromEntries(keys.map((k) => [k, 'x']))
    const got = Object.fromEntries(rowDetailItems(row))
    // 判据＝标签里必须有中文；"单据ID / SLA 截止"这类带行业缩写的算合格，
    // 而 "Bank Matched / Flow Status Text" 这种整词英文（＝prettyKey 回落）算不合格
    const leaked = keys.filter((k) => got[k] !== undefined && !/[一-鿿]/.test(got[k]))
    expect(leaked, `英文回落: ${leaked.map((k) => k + '→' + got[k]).join(', ')}`).toEqual([])
    const viaFallback = keys.filter((k) => got[k] === prettyKey(k))
    expect(viaFallback, `标签等于回落英文标题: ${viaFallback.join(', ')}`).toEqual([])
  })

  it('收款核销 / 应收单 / 催收待办三张实体的字段有中文标签', () => {
    const fields = ['receiptDate', 'payMethod', 'bankSerial', 'bankMatched', 'bankMatchedAmount',
      'flowStatus', 'flowStatusText', 'contactName', 'contactPhone', 'overdueDays', 'note', 'multiRoleWarning']
    const missing = fields.filter((f) => !COMMON_LABELS[f])
    expect(missing, `未登记: ${missing.join(', ')}`).toEqual([])
  })
})

// 用「行程信息」这一行确认渲染文本里不再出现英文键名与裸码值
describe('rowDetailItems - 出差行程行', () => {
  const row = {
    id: 99,
    travelerName: '丁宇轩',
    fromCityId: 605,
    toCityId: 610,
    beginDate: '2026-10-21',
    endDate: '2026-10-23',
    days: 3,
    trafficType: 'RAIL',
    trafficClass: 'SECOND',
    hotelAmount: 0,
    trafficAmount: 2400,
    allowanceAmount: 0,
    otherAmount: 0,
    totalAmount: 2400,
    overStd: 0,
    roundTrip: true,
  }

  it('标签全中文，无英文键名外泄', () => {
    const items = rowDetailItems(row)
    for (const [k] of items) {
      expect(k, `字段名含英文: ${k}`).not.toMatch(/[A-Za-z]/)
    }
  })

  it('枚举/布尔码翻译成中文', () => {
    const items = rowDetailItems(row, {
      enums: { trafficType: () => '', trafficClass: () => '' }, // 模拟字典未加载，走内置兜底
    })
    const m = Object.fromEntries(items)
    expect(m['交通方式']).toBe('高铁')
    expect(m['舱位/席别']).toBe('二等座')
    expect(m['是否往返']).toBe('是')
    expect(m['是否超标']).toBe('合规')
    expect(m['天数']).toBe('3')
    expect(m['长途交通费']).toBe('2,400.00')
  })

  it('页面字典优先于内置兜底', () => {
    const items = rowDetailItems(row, { enums: { trafficType: () => '高铁（字典）' } })
    expect(Object.fromEntries(items)['交通方式']).toBe('高铁（字典）')
  })

  it('技术性字段（主键/城市ID）不裸露', () => {
    const keys = rowDetailItems(row).map(([k]) => k)
    expect(keys).not.toContain('Id')
    expect(keys.join('|')).not.toMatch(/CityId/)
  })

  it('人员行 name → 姓名（页面显式传标签）', () => {
    const items = rowDetailItems(
      { name: '丁宇轩', startDate: '2026-10-21', endDate: '2026-10-23', days: 3, trainFee: 1000 },
      { labels: { name: '姓名' } },
    )
    const keys = items.map(([k]) => k)
    expect(keys).toContain('姓名')
    expect(keys.every((k) => !/[A-Za-z]/.test(k))).toBe(true)
  })
})
