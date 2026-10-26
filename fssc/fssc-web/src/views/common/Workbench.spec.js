// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: {}, path: '/workbench', meta: {} }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), alert: vi.fn(() => Promise.resolve()) },
}))

// vi.hoisted：mock 工厂会被提升到 import 之前执行，spy 与 perms 必须在同一提升层创建
const H = vi.hoisted(() => {
  const mk = () => vi.fn(() => Promise.resolve({ list: [], total: 0, pages: 0, pageNum: 1, pageSize: 20 }))
  return {
    user: { userId: 1, realName: '测试', perms: [] },
    pool: mk(),
    batchPage: mk(),
    todoPage: mk(),
    myPage: mk(),
    httpGet: vi.fn(() => Promise.resolve({ records: [], total: 0 })),
    myCc: mk(),
  }
})

vi.mock('@/api', () => {
  const page = () => Promise.resolve({ list: [], total: 0 })
  const noop = () => Promise.resolve({ code: 0, data: null })
  return {
    // 用 getter 暴露 user，使测试改 H.user.perms 后组件在 setup 期能读到最新权限
    store: { token: 'T', get user() { return H.user }, logout() {} },
    backend: { online: true, msg: '' },
    onBackendChange: () => {},
    http: { get: H.httpGet, post: noop, put: noop, delete: noop },
    wfApi: { todoPage: H.todoPage },
    billApi: { myPage: H.myPage },
    flowApi: { myCc: H.myCc },
    payApi: { batchPage: H.batchPage },
    sharingApi: { pool: H.pool, receive: noop, finish: noop },
    getMenuTree: page,
    getPerfLog: () => [],
    login: noop, logout: noop, registerTenant: noop, BACKEND_TARGET: 'x',
  }
})

import Workbench from './Workbench.vue'

describe('L1-F-06 Workbench.vue 组件测试', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    H.user = { userId: 1, realName: '测试', perms: [] }
  })

  it('可挂载（各业务卡片为空数据时不崩溃）', async () => {
    const w = shallowMount(Workbench)
    await flushPromises()
    expect(w.exists()).toBe(true)
  })

  it('挂载过程中不抛未捕获异常', async () => {
    const errors = []
    shallowMount(Workbench, { global: { config: { errorHandler: (e) => errors.push(e) } } })
    await flushPromises()
    expect(errors).toHaveLength(0)
  })
})

/**
 * 回归：某「普通员工兼部门经理」账号登录进「我的工作台」时弹出
 * "无该功能/按钮权限：缺少权限：sharing:task:list / pay:batch:list"。
 * 根因是工作台作为全员落地页，却无条件调用了两个模块专属接口。
 * 修复后：无权限的角色既不渲染卡片、也不发起请求。
 */
describe('Workbench.vue 权限门控回归（普通员工兼部门经理）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    H.user = { userId: 3910, realName: '示例员工', perms: ['exp:bill:list', 'exp:bill:submit', 'workbench:view'] }
  })

  it('无 sharing:task:list 权限：不请求任务池接口', async () => {
    shallowMount(Workbench)
    await flushPromises()
    expect(H.pool).not.toHaveBeenCalled()
  })

  it('无 pay:batch:list 权限：不请求付款批次接口', async () => {
    shallowMount(Workbench)
    await flushPromises()
    expect(H.batchPage).not.toHaveBeenCalled()
  })

  it('有对应权限：正常请求任务池与付款批次', async () => {
    H.user = { userId: 59, realName: '出纳', perms: ['sharing:task:list', 'pay:batch:list'] }
    shallowMount(Workbench)
    await flushPromises()
    expect(H.pool).toHaveBeenCalled()
    expect(H.batchPage).toHaveBeenCalled()
  })

  it('工作台自用的待办/我的单据统计不受权限门控影响（始终加载）', async () => {
    shallowMount(Workbench)
    await flushPromises()
    expect(H.todoPage).toHaveBeenCalled()
    // D-DEC-406：「我发起的单据」不再走 billApi.myPage，改由跨域接口取数
    expect(H.httpGet).toHaveBeenCalledWith('/tenant/workbench/my-documents', expect.anything())
    expect(H.myCc).toHaveBeenCalled()
  })
})
