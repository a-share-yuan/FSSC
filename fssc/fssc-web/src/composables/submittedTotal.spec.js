// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect } from 'vitest'
import { submittedTotal } from './useBill.js'

/**
 * 表头总金额必须只加「会进本次载荷的行」（已选费用类型）。
 * 反置法：把 submittedTotal 的 filter 换回旧口径 `l.itemId || amount>0`，第 2 条即转红。
 */
describe('submittedTotal — 表头金额与落库行同源', () => {
  it('三行都已选费用类型：与界面合计同值', () => {
    const lines = [
      { itemId: 403, amount: 2400 },
      { itemId: 401, amount: 1200 },
      { itemId: 404, amount: 300 },
    ]
    expect(submittedTotal(lines)).toBe(3900)
  })

  it('有一行未选费用类型（该行不进载荷）：表头不得替它留金额', () => {
    const lines = [
      { itemId: 403, amount: 2400 },
      { itemId: null, amount: 1200 },   // 载荷会丢掉这行
      { itemId: 404, amount: 300 },
    ]
    expect(submittedTotal(lines)).toBe(2700)
    expect(submittedTotal(lines)).not.toBe(3900)
  })

  it('借款行由 syncLoanLine 自动带费用项：口径改动不影响借款单', () => {
    expect(submittedTotal([{ itemId: 542, amount: 4000 }])).toBe(4000)
  })

  it('没有明细行时金额为 0（不进 NaN）', () => {
    expect(submittedTotal([])).toBe(0)
    expect(submittedTotal([{ itemId: 403, amount: null }])).toBe(0)
  })
})
