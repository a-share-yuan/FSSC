// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), alert: vi.fn(() => Promise.resolve()) },
}))

// vi.hoisted：mock 工厂被提升到 import 之前，spy 与 perms 需处于同一提升层
const H = vi.hoisted(() => {
  const page = () => Promise.resolve({ list: [], total: 0 })
  return {
    user: { userId: 1, perms: [] },
    // 模型列表返回 1 条 —— 触发 selectModel() → loadAllDetails() 这条首屏链路
    modelPage: vi.fn(() => Promise.resolve({
      list: [{ id: 1, modelKey: 'CLAIM', modelName: '员工费用报销', status: 'PUBLISHED' }],
      total: 1,
    })),
    nodeList: vi.fn(page),
    processPage: vi.fn(page),
    instancePage: vi.fn(page),
    delegatePage: vi.fn(page),
  }
})

vi.mock('@/api', () => {
  const noop = () => Promise.resolve({ code: 0, data: null })
  return {
    // 用 getter 暴露 user，使测试改 H.user.perms 后组件在 setup 期读到最新权限
    store: { token: 'T', get user() { return H.user }, logout() {} },
    backend: { online: true, msg: '' },
    onBackendChange: () => {},
    http: { get: noop, post: noop, put: noop, delete: noop },
    flowApi: {
      modelPage: H.modelPage,
      modelCreate: noop, modelUpdate: noop, modelDelete: noop,
      processPage: H.processPage, processCreate: noop, processUpdate: noop, processDelete: noop,
      nodeList: H.nodeList, nodeCreate: noop, nodeUpdate: noop, nodeDelete: noop,
      delegatePage: H.delegatePage, delegateCreate: noop, delegateCancel: noop,
      instancePage: H.instancePage,
      historyPage: noop, myTodo: noop, myCc: noop,
    },
    flowConfigApi: { page: noop, create: noop, update: noop, delete: noop },
    designerApi: { simulate: noop, validate: noop },
    getMenuTree: noop, login: noop, logout: noop,
  }
})

import FlowConfig from './FlowConfig.vue'

/**
 * 回归：用户 Y009394 打开「流程管理」(/expense/flow) 后连报
 * "无该功能/按钮权限：缺少权限：wf:instance:list"（2 次）与 "wf:node:list"（2 次）。
 *
 * 根因：本页是六页签聚合页，菜单 104 只挂了 wf:model:list 一个码，
 * 但 selectModel() 会无条件 Promise.all 拉取 节点/绑定/实例/委托 四份数据，
 * 各自需要 wf:node:list / wf:config:list / wf:instance:list / wf:delegate:list。
 * 修复后：按各页签真实权限码门控，无权限则不发请求（且隐藏页签）。
 */
describe('FlowConfig.vue 六页签权限门控回归（wf:node:list / wf:instance:list 报错）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    H.user = { userId: 1, perms: [] }
  })

  it('仅有 wf:model:list（普通员工）：只拉模型列表，不请求节点/绑定/实例/委托', async () => {
    H.user = { userId: 3910, perms: ['wf:model:list'] }
    shallowMount(FlowConfig)
    await flushPromises()

    expect(H.modelPage).toHaveBeenCalled()
    expect(H.nodeList).not.toHaveBeenCalled()
    expect(H.instancePage).not.toHaveBeenCalled()
    expect(H.processPage).not.toHaveBeenCalled()
    expect(H.delegatePage).not.toHaveBeenCalled()
  })

  it('仅有 wf:model:list + wf:delegate:list：只补拉委托代理，仍不碰节点/实例', async () => {
    H.user = { userId: 3910, perms: ['wf:model:list', 'wf:delegate:list'] }
    shallowMount(FlowConfig)
    await flushPromises()

    expect(H.delegatePage).toHaveBeenCalled()
    expect(H.nodeList).not.toHaveBeenCalled()
    expect(H.instancePage).not.toHaveBeenCalled()
  })

  it('配置管理员（齐备 wf 权限）：四个页签数据均正常加载', async () => {
    H.user = {
      userId: 1,
      perms: ['wf:model:list', 'wf:node:list', 'wf:config:list', 'wf:instance:list', 'wf:delegate:list'],
    }
    shallowMount(FlowConfig)
    await flushPromises()

    expect(H.modelPage).toHaveBeenCalled()
    expect(H.nodeList).toHaveBeenCalled()
    expect(H.instancePage).toHaveBeenCalled()
    expect(H.processPage).toHaveBeenCalled()
    expect(H.delegatePage).toHaveBeenCalled()
  })

  it('缺 wf:instance:list 时：实例页签不发请求（即不报错），其余有权限页签照常加载', async () => {
    H.user = { userId: 3910, perms: ['wf:model:list', 'wf:node:list', 'wf:config:list', 'wf:delegate:list'] }
    shallowMount(FlowConfig)
    await flushPromises()

    expect(H.nodeList).toHaveBeenCalled()
    expect(H.processPage).toHaveBeenCalled()
    expect(H.instancePage).not.toHaveBeenCalled()
  })
})
