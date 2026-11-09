// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import ErrorBoundary from './ErrorBoundary.vue'

/**
 * 只在首次挂载抛错：模拟「上一页崩了、换到下一页错误源消失」。
 * 用于验证边界实例复用（不再每次换页重建）后，resetKey 仍能清掉兜底态。
 */
function makeFlaky() {
  let first = true
  return defineComponent({
    render() {
      if (first) {
        first = false
        throw new Error('页面渲染失败')
      }
      return h('div', { class: 'ok-child' }, '页面内容')
    },
  })
}

describe('ErrorBoundary 按页复位', () => {
  it('子组件抛错显示兜底；resetKey 变化后恢复到正常页面', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const Flaky = makeFlaky()
      const w = mount(ErrorBoundary, {
        props: { resetKey: '/page-a' },
        slots: { default: () => h(Flaky) },
      })
      await nextTick()
      expect(w.find('.error-boundary').exists()).toBe(true)
      expect(w.find('.ok-child').exists()).toBe(false)

      await w.setProps({ resetKey: '/page-b' })
      await nextTick()
      expect(w.find('.error-boundary').exists()).toBe(false)
      expect(w.find('.ok-child').text()).toBe('页面内容')
    } finally {
      spy.mockRestore()
    }
  })

  it('未传 resetKey 的旧用法仍能捕获错误（重建式边界不受影响）', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const Flaky = makeFlaky()
      const w = mount(ErrorBoundary, {
        slots: { default: () => h(Flaky) },
      })
      await nextTick()
      expect(w.find('.error-boundary').exists()).toBe(true)
    } finally {
      spy.mockRestore()
    }
  })
})
