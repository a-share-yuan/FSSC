// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 版本化系统标题。
 *
 * 一套代码对应三个能力档位，产品名不能固定为「集团财务共享平台」：
 * 只启用报销或费控能力的组织，浏览器标题与主框架标题应体现自己实际使用的产品。
 *
 * 档位唯一真相是 pf_tenant.op_mode：登录接口把它放在响应顶层（不写进 JWT），
 * 前端落一份到 localStorage 供刷新后恢复；切换版本会要求重新登录并覆盖。
 * 名称口径与后端 presets/tenant_presets.json 的 label 保持一致，集中在此处一处可改，
 * 不散落到各个页面、也不按特性码反推档位。
 */

const EDITION_KEY = 'fssc_op_mode'

export const EDITION_BRANDS = {
  EXPENSE: {
    zh: 'FSSC 费用报销系统', en: 'FSSC Expense Management',
    shortZh: '费用报销', shortEn: 'Expense',
  },
  CONTROL: {
    zh: 'FSSC 费控管理系统', en: 'FSSC Expense Control',
    shortZh: '费控管理', shortEn: 'Expense Control',
  },
  SHARED: {
    zh: 'FSSC 集团财务共享平台', en: 'FSSC Financial Shared Service Center',
    shortZh: '财务共享', shortEn: 'Shared Services',
  },
}

// 未选档 / 旧会话 / 未知档位：用中性名，不默认假装是共享版
export const DEFAULT_BRAND = {
  zh: 'FSSC 财务管理系统', en: 'FSSC Finance Management',
  shortZh: '财务管理', shortEn: 'Finance',
}

function isEn(locale) {
  return String(locale || '').toLowerCase().startsWith('en')
}

export function brandOf(mode) {
  return EDITION_BRANDS[String(mode || '').toUpperCase()] || DEFAULT_BRAND
}

export function getEdition() {
  try { return localStorage.getItem(EDITION_KEY) || '' } catch { return '' }
}

/** 写入档位并广播，供根组件即时刷新 document.title */
export function setEdition(mode) {
  const v = String(mode || '').toUpperCase()
  try {
    v ? localStorage.setItem(EDITION_KEY, v) : localStorage.removeItem(EDITION_KEY)
    window.dispatchEvent(new CustomEvent('fssc:edition', { detail: { mode: v } }))
  } catch { /* 非浏览器环境（单测等）忽略 */ }
  return v
}

export function clearEdition() {
  return setEdition('')
}

export function editionTitle(locale) {
  const b = brandOf(getEdition())
  return isEn(locale) ? b.en : b.zh
}

export function editionShortTitle(locale) {
  const b = brandOf(getEdition())
  return isEn(locale) ? b.shortEn : b.shortZh
}

export function applyEditionTitle(locale) {
  const title = editionTitle(locale)
  try { document.title = title } catch { /* 非浏览器环境忽略 */ }
  return title
}
