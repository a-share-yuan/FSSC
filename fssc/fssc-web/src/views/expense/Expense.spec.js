// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: {}, path: '/expense', meta: {} }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), alert: vi.fn(() => Promise.resolve()) },
}))

vi.mock('@/api', () => {
  const page = () => Promise.resolve({ code: 0, data: { list: [], total: 0, pages: 0, pageNum: 1, pageSize: 20 } })
  const noop = () => Promise.resolve({ code: 0, data: null })
  const api = new Proxy({}, { get: () => page })
  return {
    store: { token: 'T', user: { userId: 1 }, perms: ['*:*:*'], features: [] },
    backend: { online: true, msg: '' },
    onBackendChange: () => {},
    http: { get: page, post: noop, put: noop, delete: noop },
    userApi: api, orgApi: api, masterApi: api, wfApi: api, billApi: api, flowApi: api,
    payApi: api, sharingApi: api, dashApi: api, profileApi: api, numberApi: api,
    noticeApi: api, messageApi: api, bizFlowApi: api, flowConfigApi: api, designerApi: api,
    fileApi: api, getMenuTree: page, getPerfLog: () => [],
    login: noop, logout: noop, registerTenant: noop, BACKEND_TARGET: 'x',
  }
})

import Expense from './Expense.vue'

describe('Expense.vue 组件测试', () => {
  beforeEach(() => vi.clearAllMocks())

  it('可挂载并渲染页签容器', async () => {
    const w = shallowMount(Expense)
    await flushPromises()
    expect(w.exists()).toBe(true)
    expect(w.html()).toContain('el-tabs')
  })

  it('挂载过程中不抛未捕获异常', async () => {
    const errors = []
    shallowMount(Expense, { global: { config: { errorHandler: (e) => errors.push(e) } } })
    await flushPromises()
    expect(errors).toHaveLength(0)
  })
})
