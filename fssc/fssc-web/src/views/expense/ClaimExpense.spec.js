import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: {}, path: '/expense/claim', meta: {} }),
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
    store: { token: 'T', user: { userId: 1, realName: '测试', deptId: 10003 }, perms: ['*:*:*'], features: ['F_EXPENSE_CLAIM'] },
    backend: { online: true, msg: '' },
    onBackendChange: () => {},
    http: { get: page, post: noop, put: noop, delete: noop },
    userApi: api, orgApi: api, masterApi: api, wfApi: api, billApi: api, flowApi: api,
    payApi: api, sharingApi: api, dashApi: api, profileApi: api, numberApi: api,
    noticeApi: api, messageApi: api, bizFlowApi: api, flowConfigApi: api, designerApi: api,
    fileApi: api, getMenuTree: page, getPerfLog: () => [],
    login: noop, logout: noop, registerTenant: noop,
    BACKEND_TARGET: '127.0.0.1:8089',
  }
})

import ClaimExpense from './ClaimExpense.vue'

describe('L1-F-01 ClaimExpense.vue 组件测试', () => {
  beforeEach(() => vi.clearAllMocks())

  it('可挂载并渲染单据工作台外壳', async () => {
    const w = shallowMount(ClaimExpense, {
      global: { stubs: { BillWorkbench: { template: '<div class="bill-workbench-stub" />' } } },
    })
    await flushPromises()
    expect(w.exists()).toBe(true)
    expect(w.find('.bill-workbench-stub').exists()).toBe(true)
  })

  it('挂载与取数过程中不抛未捕获异常', async () => {
    const errors = []
    const w = shallowMount(ClaimExpense, {
      global: {
        stubs: { BillWorkbench: { template: '<div />' } },
        config: { errorHandler: (e) => errors.push(e) },
      },
    })
    await flushPromises()
    expect(errors).toHaveLength(0)
    expect(w.exists()).toBe(true)
  })
})
