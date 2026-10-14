// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
//
// i18n 基座（域8A / 任务 T0.5）
// ---------------------------------------------------------------------------
// 目标：一处配置、全局可切、语言包按 locale 分目录（新增语种=加目录，不改业务码）。
//
// 设计要点（对应方案 §8.2「抽取纪律」）：
//  1. **静态 key 强制**：语言包 key 一律静态字面量，调用侧写 $t('xxx.yyy')；
//     严禁 $t('menu.' + name) 动态拼接——否则残留扫描/键对齐 CI 全部失效。
//  2. **缺失兜底回落 zh-CN**：缺 key 时回落中文并 DEV 环境告警，避免白屏。
//  3. **数字/日期/币种统一 Intl**：不在业务里手写 toLocaleString，统一走 $n/$d。
//  4. 语言持久化：localStorage 记住选择，刷新不丢；首访按浏览器语言探测。
//
// 注意：本文件只做「基座 + 通用键」。业务页面文案属域8B 存量抽取范围，
//       按域分批迁移，届时只往 locales/*.js 追加键，不改本文件结构。

import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

export const SUPPORT_LOCALES = [
  { value: 'zh-CN', label: '简体中文', short: '中' },
  { value: 'en-US', label: 'English', short: 'EN' },
]

export const DEFAULT_LOCALE = 'zh-CN'
const STORAGE_KEY = 'fssc-locale'

/** 探测初始语言：localStorage > 浏览器语言 > 默认 */
export function detectLocale() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved && SUPPORT_LOCALES.some((l) => l.value === saved)) return saved
  } catch {
    /* localStorage 不可用（隐私模式/SSR）→ 继续探测 */
  }
  const nav = typeof navigator !== 'undefined' ? navigator.language || '' : ''
  if (nav.toLowerCase().startsWith('en')) return 'en-US'
  return DEFAULT_LOCALE
}

export const i18n = createI18n({
  legacy: false, // Composition API（与项目 Vue 3 + <script setup> 写法一致）
  globalInjection: true, // 允许模板里直接用 $t，无需每页 import
  locale: detectLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  // 缺失 key 静默回落 + DEV 提示，避免生产环境控制台噪音
  missingWarn: import.meta.env?.DEV ?? false,
  fallbackWarn: false,
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

// 首访即把 <html lang> 同步为当前语言（无障碍朗读 / 浏览器翻译识别）；
// 后续切换由 setLocale 负责。
if (typeof document !== 'undefined') {
  document.documentElement.setAttribute('lang', i18n.global.locale.value)
}

/** 切换语言并持久化。返回实际生效的 locale。 */
export function setLocale(locale) {
  if (!SUPPORT_LOCALES.some((l) => l.value === locale)) return i18n.global.locale.value
  i18n.global.locale.value = locale
  try {
    localStorage.setItem(STORAGE_KEY, locale)
  } catch {
    /* 忽略：仅丢失偏好记忆，不影响切换 */
  }
  // 同步 <html lang>，供无障碍朗读与浏览器翻译识别
  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('lang', locale)
  }
  return locale
}

export function getLocale() {
  return i18n.global.locale.value
}

/** 供 CI 键对齐校验复用：列出某语言包的全部扁平 key */
export function flattenKeys(obj, prefix = '') {
  return Object.keys(obj || {}).flatMap((k) => {
    const key = prefix ? `${prefix}.${k}` : k
    const v = obj[k]
    return v && typeof v === 'object' && !Array.isArray(v) ? flattenKeys(v, key) : [key]
  })
}

export default i18n
