// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect } from 'vitest'
import { shareBudgetToLine } from './useBill.js'

/**
 * 事前申请页唯一的预算科目入口在「分摊信息」，而服务端预算占用/核销只读费用明细行的科目
 * （ExpBillShare 全仓只有 CRUD 四个引用点＝分摊行的科目没有任何消费方）⇒ 屏幕选招待费、系统占差旅费。
 * 修法＝一屏一尺：分摊行科目写进那条承载金额的行；多科目/多行落不下一条预算线时返回 conflict 交页面拒提。
 * 反置法：把 `ls[0].budgetItemId = items[0]` 删掉 ⇒ 第 1/2 条转红；
 *         把 conflict 条件里的 `items.length > 1` 去掉 ⇒ 第 3 条转红；
 *         把 `ls.length > 1` 去掉 ⇒ 第 4 条转红；把 `!items.length` 早退还删 ⇒ 第 5 条转红。
 */
describe('shareBudgetToLine — 分摊行科目与占用科目同源', () => {
  it('整单一条分摊行（招待费）＋合成的一条承载行：行科目＝分摊行科目', () => {
    const lines = [{ itemId: 335, amount: 3000, budgetItemId: null }]
    const shares = [{ budgetItemId: 7, shareAmount: 3000 }]
    expect(shareBudgetToLine(lines, shares)).toEqual({ applied: true, conflict: false, budgetItemId: 7 })
    expect(lines[0].budgetItemId).toBe(7)
  })

  it('两枚分摊行但科目相同（只拆成本中心）：照写，不算冲突', () => {
    const lines = [{ itemId: 335, amount: 6000, budgetItemId: 6 }]
    const shares = [{ budgetItemId: 7, shareAmount: 3000 }, { budgetItemId: 7, shareAmount: 3000 }]
    expect(shareBudgetToLine(lines, shares).applied).toBe(true)
    expect(lines[0].budgetItemId).toBe(7)
  })

  it('两枚分摊行科目不同：conflict，不得静默择一把整单金额占进其中一条预算线', () => {
    const lines = [{ itemId: 335, amount: 6000, budgetItemId: 6 }]
    const shares = [{ budgetItemId: 7 }, { budgetItemId: 5 }]
    expect(shareBudgetToLine(lines, shares).conflict).toBe(true)
    expect(lines[0].budgetItemId).toBe(6)
  })

  it('一页多条费用明细行（报销单形状）：不得替所有行择一科目，交回原口径', () => {
    const lines = [{ budgetItemId: null }, { budgetItemId: null }]
    const shares = [{ budgetItemId: 7 }]
    expect(shareBudgetToLine(lines, shares).conflict).toBe(true)
    expect(lines.map((l) => l.budgetItemId)).toEqual([null, null])
  })

  it('分摊行没选科目（历史草稿/仅成本中心分摊）：不动行上已有科目，服务端按费用项兜底解析', () => {
    const lines = [{ itemId: 335, amount: 3000, budgetItemId: 6 }]
    expect(shareBudgetToLine(lines, [{ budgetItemId: null }])).toEqual({ applied: false, conflict: false, budgetItemId: null })
    expect(lines[0].budgetItemId).toBe(6)
  })

  it('没有分摊行或没有明细行：不报错、不改写', () => {
    expect(shareBudgetToLine([{ budgetItemId: 6 }], []).applied).toBe(false)
    expect(shareBudgetToLine([], [{ budgetItemId: 7 }]).applied).toBe(false)
    expect(shareBudgetToLine(null, null).conflict).toBe(false)
  })
})
