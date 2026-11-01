// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 价税计算方式（含税价内税）。
 *
 * 发票票面的「价税合计」是含税金额，而税率是价外税率，因此：
 *   不含税 = 含税 ÷ (1 + 税率)
 *   税额 = 含税 − 不含税
 *
 * 常见错误写法「税额 = 含税 × 税率」在 6% 场景会把 539 元算成 32.34，
 * 而票面/税务规则应为 30.51（508.49 + 30.51）——规则不一致会导致前端明细行
 * 与解析出的票面金额对不上、进而污染发票池登记。
 */

/** 四舍五入到分 */
export function round2(v) {
  return Math.round(Number(v || 0) * 100) / 100
}

/**
 * 含税金额按税率拆分价税。
 * @param {number|string} amount 含税金额（价税合计）
 * @param {number|string} taxRatePercent 税率（百分数，如 13 表示 13%）
 * @returns {{amount:number, netAmount:number, taxAmount:number}}
 */
export function splitTaxInclusive(amount, taxRatePercent) {
  const amt = round2(amount)
  const rate = Number(taxRatePercent || 0) / 100
  if (!(rate > 0) || amt === 0) {
    return { amount: amt, netAmount: amt, taxAmount: 0 }
  }
  const net = round2(amt / (1 + rate))
  return { amount: amt, netAmount: net, taxAmount: round2(amt - net) }
}

/**
 * 由「含税 + 不含税」反推税率（百分数，保留 2 位）。
 * 用于发票解析记录回填场景：票面已有准确的不含税/税额，税率只是展示用。
 */
export function taxRateFromAmounts(amount, netAmount) {
  const amt = Number(amount || 0)
  const net = Number(netAmount || 0)
  if (!(net > 0) || !(amt > 0)) return 0
  return Math.round(((amt - net) / net) * 10000) / 100
}
