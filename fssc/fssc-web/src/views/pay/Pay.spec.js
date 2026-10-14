// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: {}, path: '/pay', meta: {} }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), alert: vi.fn(() => Promise.resolve()) },
}))

const H = vi.hoisted(() => {
  const mk = () => vi.fn(() => Promise.resolve({ list: [], total: 0, pages: 0, pageNum: 1, pageSize: 20 }))
  return {
    user: { userId: 16, realName: '财务经理', perms: [] },
    batchPage: mk(),
    autoGroup: vi.fn(() => Promise.resolve(0)),
    details: vi.fn(() => Promise.resolve([])),
    createBatch: vi.fn(() => Promise.resolve(1)),
  }
})

vi.mock('@/api', () => {
  const noop = () => Promise.resolve({ code: 0, data: null })
  return {
    store: { token: 'T', get user() { return H.user }, logout() {} },
    backend: { online: true, msg: '' },
    onBackendChange: () => {},
    http: { get: noop, post: noop, put: noop, delete: noop },
    payApi: { batchPage: H.batchPage, autoGroup: H.autoGroup, details: H.details, createBatch: H.createBatch },
    orgApi: { companies: noop },
    masterApi: { bankAccounts: noop },
    wfApi: { approveByBill: noop },
    getMenuTree: noop,
    getPerfLog: () => [],
    login: noop, logout: noop, registerTenant: noop, BACKEND_TARGET: 'x',
  }
})

import Pay from './Pay.vue'

/**
 * 回归：「出纳付款」菜单是 op:pay:list，但页面首屏真实调用 pay:batch:* 接口，
 * 两套码不一致导致 FIN_MANAGER 等 5 个角色进页即弹「缺少权限：pay:batch:list」，
 * CFO 少 pay:batch:create 也会因自动组批报错。此处锁死按接口权限收敛的行为。
 *
 * D-DEC-099（触发点错位）追加口径：进页/刷新一律纯读，不再调 auto-group——
 * 组批改由服务端在单据审批通过收尾时触发（PayBatchAutoGroupListener），
 * 页面只有显式点「立即组批」（仍限 pay:batch:create）才产生写入。
 */
describe('Pay.vue 权限收敛回归（op:pay:list 与 pay:batch:* 错配）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('无 pay:batch:list：不请求批次列表、不触发自动组批', async () => {
    // 财务经理 FIN_MANAGER：有 op:pay:list 能进页，但无任何 pay:batch:* 权限
    H.user = { userId: 16, realName: '财务经理', perms: ['op:pay:list', 'exp:pay:list'] }
    shallowMount(Pay)
    await flushPromises()
    expect(H.batchPage).not.toHaveBeenCalled()
    expect(H.autoGroup).not.toHaveBeenCalled()
  })

  it('有 pay:batch:list 但无 pay:batch:create：只查列表，不触发自动组批（CFO 场景）', async () => {
    H.user = { userId: 45, realName: '财务总监', perms: ['op:pay:list', 'pay:batch:list'] }
    shallowMount(Pay)
    await flushPromises()
    expect(H.batchPage).toHaveBeenCalled()
    expect(H.autoGroup).not.toHaveBeenCalled()
  })

  it('有 pay:batch:list + create（出纳）：首屏仍为纯读，只有显式组批才写', async () => {
    H.user = { userId: 59, realName: '出纳', perms: ['op:pay:list', 'pay:batch:list', 'pay:batch:create'] }
    const wrapper = shallowMount(Pay)
    await flushPromises()
    expect(H.batchPage).toHaveBeenCalled()
    // D-DEC-099：进页不再组批（原实现每次打开付款页都改数据，批次生成时点与审批时点解耦）
    expect(H.autoGroup).not.toHaveBeenCalled()
    H.autoGroup.mockResolvedValue({ batchCount: 1, skippedReasons: [] })
    await wrapper.vm.groupNow()
    await flushPromises()
    expect(H.autoGroup).toHaveBeenCalledTimes(1)
  })
})
