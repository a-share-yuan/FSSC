// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 家族：候选选择器的金额校验规则（抽成纯函数，便于确定性单测）。
 *
 * <p>为什么单独成文件：金额合法性是本组件的<b>业务校验</b>（支持部分冲抵 ⇒ 金额必须 > 0 且 ≤ 可冲抵余额），
 * 而 el-input-number 的 {@code :max} 只能拦"用户敲键盘"这一条路径；草稿回填、接口回写、
 * 浏览器自动填充都可能绕过它。把规则独立出来，组件与单测共用同一份实现，
 * 避免"组件里改了一处、单测锁的是另一处"。</p>
 */

/**
 * 金额是否合法：非空、有限、正数、不超上限（1e-9 容差吃掉浮点尾差）。
 *
 * @param {*} amount 用户输入/回填的金额
 * @param {*} max 可冲抵上限（借款未还余额 / 预付可用额 / 申请金额）
 */
export function isAmountValid(amount, max) {
  if (amount == null || amount === '') return false
  const a = Number(amount)
  const m = Number(max)
  if (!Number.isFinite(a)) return false
  return a > 0 && a <= m + 1e-9
}

/** 把金额钳进 {@code (0, max]}；非法输入回落为上限（即"全额"，与旧行为一致但可被用户改）。 */
export function clampAmount(amount, max) {
  const m = Number(max)
  const cap = Number.isFinite(m) && m > 0 ? m : 0
  const a = Number(amount)
  if (!Number.isFinite(a) || a <= 0) return Number(cap.toFixed(2))
  return Number(Math.min(a, cap).toFixed(2))
}
