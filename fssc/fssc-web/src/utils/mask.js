// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 账号/卡号展示层脱敏。
 *
 * 只改屏上呈现，不改接口原值：列表列绑这个函数，编辑表单与导出仍是全号，
 * 前端打码可被绕过（DevTools／直接调接口），后端出参侧的同类办结是本条目的剩余环节。
 */

/** 保留的段长：首尾各 4 位——够辨认是哪户，又不足以拼出可用全号 */
const LEAD_KEEP = 4
const TAIL_KEEP = 4
const MASK_CHAR = '*'
/** 掩码段定长上限：号再长也不把真实位数暴露到屏上 */
const MAX_MASK_RUN = 8

/**
 * 前 4 位 + 掩码 + 后 4 位。
 * 空值（null/undefined/空白串）返回 ''；任何入参都不抛异常（非数字、对象、超长串一律安全降级）。
 * @param {any} value 账号原值
 * @returns {string} 打码后的展示串
 */
export function maskAccount(value) {
  let s = ''
  try {
    s = value == null ? '' : String(value).trim()
  } catch {
    // 入参自带会抛的 toString：按空值处理，绝不让单元格渲染中断
    return ''
  }
  if (!s) return ''
  const chars = Array.from(s)
  let lead = LEAD_KEEP
  let tail = TAIL_KEEP
  if (chars.length <= lead + tail) {
    lead = 1
    tail = 1
  }
  const hidden = chars.length - lead - tail
  // 短到留不出中段（≤2 位）时没什么可打码的，原样返回也比返回空好
  if (hidden <= 0) return s
  return chars.slice(0, lead).join('') + MASK_CHAR.repeat(Math.min(hidden, MAX_MASK_RUN)) + chars.slice(chars.length - tail).join('')
}
