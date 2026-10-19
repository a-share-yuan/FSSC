import { config } from '@vue/test-utils'
import ElementPlus from 'element-plus'

/**
 * Vitest 全局 setup。
 *
 * 通过注册真实 Element Plus 来解析 el-* 组件。
 * 注意：不要用 compilerOptions.isCustomElement 把 el-* 当自定义元素 ——
 * 在 @vitejs/plugin-vue 下会导致 "Codegen node is missing for element/if/for node" 编译失败。
 */
config.global.plugins = [ElementPlus]

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
