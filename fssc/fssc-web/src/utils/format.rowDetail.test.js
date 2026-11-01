// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect } from 'vitest'
import { rowDetailItems } from './format'

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
