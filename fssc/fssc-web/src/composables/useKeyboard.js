// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * useKeyboard - 全局快捷键 composable
 * 支持注册全局快捷键，自动清理监听
 *
 * 用法：
 *   import { useKeyboard } from '@/composables/useKeyboard'
 *   // 方式1：单键注册
 *   useKeyboard('ctrl+k', () => { ... })
 *   // 方式2：批量注册
 *   useKeyboard({
 *     'ctrl+/': () => { ... },
 *     'ctrl+d': () => { ... },
 *   })
 */

const handlers = new Map()
let listenerInstalled = false

function normalize(key) {
  return key.toLowerCase().replace(/\s+/g, '')
}

function buildKey(e) {
  const parts = []
  if (e.ctrlKey || e.metaKey) parts.push('ctrl')
  if (e.shiftKey) parts.push('shift')
  if (e.altKey) parts.push('alt')
  let k = e.key.toLowerCase()
  if (k === ' ') k = 'space'
  if (k === 'escape') k = 'esc'
  if (k === 'enter') k = 'enter'
  if (k === 'arrowup') k = 'arrowup'
  if (k === 'arrowdown') k = 'arrowdown'
  if (k === '/') k = '/'
  parts.push(k)
  return parts.join('+')
}

function installListener() {
  if (listenerInstalled) return
  listenerInstalled = true
  document.addEventListener('keydown', (e) => {
    const tag = e.target?.tagName?.toLowerCase()
    const editable = tag === 'input' || tag === 'textarea' || e.target?.isContentEditable
    const key = buildKey(e)
    if (editable && !key.startsWith('ctrl') && !key.startsWith('alt')) return
    const fn = handlers.get(key)
    if (fn) {
      e.preventDefault()
      fn()
    }
  })
}

/**
 * 注册快捷键
 * @param {string|Object} keyOrMap - 快捷键字符串，或 { key: handler } 映射对象
 * @param {Function} [handler] - 触发函数（仅单键模式时需要）
 */
export function useKeyboard(keyOrMap, handler) {
  installListener()
  if (typeof keyOrMap === 'object' && keyOrMap !== null) {
    // 批量注册
    for (const [key, fn] of Object.entries(keyOrMap)) {
      handlers.set(normalize(key), fn)
    }
  } else {
    // 单键注册
    handlers.set(normalize(keyOrMap), handler)
  }
}

/**
 * 快捷键帮助说明
 */
export const KEYBOARD_SHORTCUTS = [
  { key: 'Ctrl + K', desc: '全局搜索' },
  { key: 'Ctrl + /', desc: '快捷键帮助' },
  { key: 'Ctrl + D', desc: '收藏当前页' },
  { key: 'Ctrl + B', desc: '收藏夹' },
  { key: 'Ctrl + E', desc: '效率统计' },
  { key: 'Ctrl + H', desc: '切换深/浅色模式' },
  { key: 'Alt + 1~4', desc: '快速导航' },
  { key: 'Esc', desc: '关闭弹窗' },
]
