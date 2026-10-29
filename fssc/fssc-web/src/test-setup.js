// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { config } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { i18n } from './i18n'

/**
 * Vitest 全局 setup。
 *
 * 通过注册真实 Element Plus 来解析 el-* 组件。
 * 注意：不要用 compilerOptions.isCustomElement 把 el-* 当自定义元素 ——
 * 在 @vitejs/plugin-vue 下会导致 "Codegen node is missing for element/if/for node" 编译失败。
 *
 * 同时注册应用自己的 i18n 实例：页面 setup 里用 useI18n()，不装会在 mount 当场抛
 * "Need to install with `app.use` function"，与页面逻辑无关。
 */
config.global.plugins = [ElementPlus, i18n]

// jsdom 未实现 matchMedia / ResizeObserver，部分组件（表格、图表）会用到
if (typeof window !== 'undefined') {
  if (!window.matchMedia) {
    window.matchMedia = () => ({
      matches: false,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
    })
  }
  if (!window.ResizeObserver) {
    window.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  }
}
