// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: {}, path: '/login', meta: {} }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), alert: vi.fn(() => Promise.resolve()) },
}))

vi.mock('@/api', () => ({
  login: vi.fn(),
  registerTenant: vi.fn(),
  store: { token: '', user: null },
  // vitest 对 mock 缺失导出是"访问即抛"而非 undefined：Login.vue 的失败分支还会读这两个
  // 导出（C-3 验证码判定），工厂漏供会让 doLogin 直接抛（段H 回归实案，`|| []` 兜不住）
  getCaptcha: vi.fn(),
  CAPTCHA_ERROR_CODES: [4011, 4012],
  // 登录成功后清浮层（错误提示/弹窗残留），doLogin 直接调用
  clearGlobalOverlays: vi.fn(),
}))

import Login from './Login.vue'
import { login, store } from '@/api'

describe('Login.vue 组件测试', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    store.token = ''
    store.user = null
  })

  it('渲染企业编码 / 账号 / 密码三个输入项', () => {
    const w = mount(Login)
    const html = w.html()
    expect(html).toContain('企业员工登录')
    expect(html).toContain('请输入企业编码')
    expect(html).toContain('请输入工号或用户名')
    expect(html).toContain('请输入登录密码')
  })

  it('登录按钮存在且默认不是 loading', () => {
    const w = mount(Login)
    const btn = w.find('.submit-btn')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toContain('登')
  })

  it('登录成功：写入 token/user 并跳转 /dashboard', async () => {
    // USG-01 权限分流：带 dashboard:view 权限才落报表看板
    login.mockResolvedValue({ token: 'T-E2E-1', user: { userId: 9, realName: '测试', perms: ['dashboard:view'] } })
    const w = mount(Login)
    await w.find('.submit-btn').trigger('click')
    await flushPromises()

    expect(login).toHaveBeenCalledTimes(1)
    // 关键契约：登录必须带 subjectType=TENANT（缺省会报「subjectType 必填」）
    expect(login.mock.calls[0][0]).toMatchObject({ subjectType: 'TENANT' })
    expect(store.token).toBe('T-E2E-1')
    expect(pushMock).toHaveBeenCalledWith('/dashboard')
  })

  it('登录成功：无 dashboard:view 权限落地工作台（USG-01 分流）', async () => {
    login.mockResolvedValue({ token: 'T-E2E-2', user: { userId: 10, realName: '普通员工', perms: ['workbench:view'] } })
    const w = mount(Login)
    await w.find('.submit-btn').trigger('click')
    await flushPromises()
    expect(pushMock).toHaveBeenCalledWith('/workbench')
  })

  it('登录失败：展示后端返回的错误信息，不写入 token', async () => {
    login.mockRejectedValue(new Error('账号或密码错误'))
    const w = mount(Login)
    await w.find('.submit-btn').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('账号或密码错误')
    expect(store.token).toBe('')
    expect(pushMock).not.toHaveBeenCalled()
  })
})
