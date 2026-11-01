// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect } from 'vitest'
import { isAmountValid, clampAmount } from './candidateAmount'

/**
 * 金额闸的纯函数单测。
 * 这条规则是"支持部分冲抵"的底线：金额必须 > 0 且 ≤ 可冲抵余额，
 * 非法值拦住、超额值拦住、浮点尾差放行。
 */
describe('isAmountValid 部分冲抵金额闸', () => {
  it('空/未填 → 不合法', () => {
    expect(isAmountValid(null, 5000)).toBe(false)
    expect(isAmountValid(undefined, 5000)).toBe(false)
    expect(isAmountValid('', 5000)).toBe(false)
  })

  it('0 与负数 → 不合法（防"象征性核销"）', () => {
    expect(isAmountValid(0, 5000)).toBe(false)
    expect(isAmountValid(-1, 5000)).toBe(false)
  })

  it('部分金额 → 合法（G1 剧情：4,600 冲 5,000）', () => {
    expect(isAmountValid(4600, 5000)).toBe(true)
    expect(isAmountValid(0.01, 5000)).toBe(true)
  })

  it('等于上限 → 合法（全额冲抵仍是合法业务）', () => {
    expect(isAmountValid(5000, 5000)).toBe(true)
  })

  it('超上限 → 不合法（前端第一道闸，后端 registerLoanOccupancy 兜第二道）', () => {
    expect(isAmountValid(5000.01, 5000)).toBe(false)
    expect(isAmountValid(9999, 3000)).toBe(false)
  })

  it('NaN/Infinity → 不合法', () => {
    expect(isAmountValid(NaN, 5000)).toBe(false)
    expect(isAmountValid(Infinity, 5000)).toBe(false)
  })

  it('浮点尾差（0.1+0.2 形态）不误杀', () => {
    expect(isAmountValid(0.30000000000000004, 0.3)).toBe(true)
  })
})

describe('clampAmount 钳制', () => {
  it('超上限 → 收到上限', () => {
    expect(clampAmount(9999, 3000)).toBe(3000)
  })

  it('非法输入 → 回落上限（与旧"全额"默认一致，但用户随后可改）', () => {
    expect(clampAmount(null, 3000)).toBe(3000)
    expect(clampAmount(0, 3000)).toBe(3000)
    expect(clampAmount('abc', 3000)).toBe(3000)
  })

  it('合法部分金额原样保留，保留 2 位小数', () => {
    expect(clampAmount(4600, 5000)).toBe(4600)
    expect(clampAmount(1234.567, 5000)).toBe(1234.57)
  })

  it('上限为 0（无可冲抵余额）→ 钳到 0，随后 isAmountValid 必拦', () => {
    expect(clampAmount(500, 0)).toBe(0)
    expect(isAmountValid(clampAmount(500, 0), 0)).toBe(false)
  })
})
