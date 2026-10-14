// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: {}, path: '/dashboard', meta: {} }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), alert: vi.fn(() => Promise.resolve()) },
}))

// jsdom 无 canvas 实现：图表库整体打桩，只验证组件逻辑（取数/装配/销毁）。
// 注意：vi.mock 工厂会被提升到文件顶部，不能在工厂内引用外部变量（会触发 TDZ）。
vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: vi.fn(() => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
    on: vi.fn(),
    off: vi.fn(),
    clear: vi.fn(),
  })),
}))
vi.mock('echarts/charts', () => ({ PieChart: {}, BarChart: {}, LineChart: {} }))
vi.mock('echarts/components', () => ({ TooltipComponent: {}, LegendComponent: {}, GridComponent: {} }))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

vi.mock('@/api', () => {
  const d = () => Promise.resolve({ code: 0, data: { list: [], total: 0, amount: 0, count: 0 } })
  const api = new Proxy({}, { get: () => d })
  return {
    store: { token: 'T', user: { userId: 1, realName: '测试' }, perms: ['*:*:*'], features: [] },
    backend: { online: true, msg: '' },
    onBackendChange: () => {},
    http: { get: d, post: d, put: d, delete: d },
    dashApi: api, userApi: api, orgApi: api, masterApi: api, wfApi: api, billApi: api,
    flowApi: api, payApi: api, sharingApi: api, profileApi: api, numberApi: api,
    noticeApi: api, messageApi: api, bizFlowApi: api, flowConfigApi: api, designerApi: api,
    fileApi: api, getMenuTree: d, getPerfLog: () => [],
    login: d, logout: d, registerTenant: d, BACKEND_TARGET: 'x',
  }
})

import Dashboard from './Dashboard.vue'

describe('L1-F-07 Dashboard.vue 组件测试', () => {
  beforeEach(() => vi.clearAllMocks())

  it('可挂载并初始化图表容器', async () => {
    const w = shallowMount(Dashboard, { attachTo: document.body })
    await flushPromises()
    expect(w.exists()).toBe(true)
    w.unmount()
  })

  it('挂载过程中不抛未捕获异常', async () => {
    const errors = []
    const w = shallowMount(Dashboard, { global: { config: { errorHandler: (e) => errors.push(e) } } })
    await flushPromises()
    expect(errors).toHaveLength(0)
    w.unmount()
  })

  it('卸载时不因图表实例已释放而报错', async () => {
    const w = shallowMount(Dashboard)
    await flushPromises()
    expect(() => w.unmount()).not.toThrow()
  })
})
