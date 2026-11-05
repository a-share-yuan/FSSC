// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * H5 审批详情页 · 卡片折叠回归护栏。
 *
 * 为什么用组件测试而不是真机：本仓库 exp_bill 清库后为 0 行，
 * 真机要看这一页就得先造一张单（不用 admin 账号、不造单、库面零写入）⇒ 这里用桩数据把
 * 「折叠后审批人还能不能核对关键数」这条底线锁死：
 *   ① 五段信息卡只默认展开第一段（单据概要），页面不再顺排六七屏；
 *   ② 收起态标题行必须给摘要（明细行数与合计、附件份数、发票张数与金额、留痕条数与最后一条），
 *      否则等于把决策依据全藏进二次点击；
 *   ③ 缺权 / 加载失败照实写进摘要（不要渲染成"暂无"＝假空）；
 *   ④ 下一环节办理人与审批意见是办理动作区，不折叠——收起会让人在"请先指定下一环节办理人"
 *      的红字前找不到选人入口、把必填意见框藏起来；
 *   ⑤ 点一次标题就能展开留痕（证据仍在一击之内）。
 */

const route = { params: { id: '9001' }, query: {}, path: '/h5/approval/9001', meta: {} }

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useRoute: () => route,
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), closeAll: vi.fn() },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), prompt: vi.fn(() => Promise.resolve({ value: 'x' })) },
}))

vi.mock('../../api', () => ({
  billApi: { detail: vi.fn() },
  invoiceApi: { billInvoices: vi.fn() },
  flowApi: { chain: vi.fn() },
  fileApi: { createToken: vi.fn() },
  wfApi: {
    nextCandidates: vi.fn(), taskCandidates: vi.fn(),
    approve: vi.fn(), transfer: vi.fn(), addSign: vi.fn(),
  },
  store: { token: 'T', user: { userId: 3807, realName: '赵勇', perms: ['*:*:*'] }, perms: ['*:*:*'], features: [] },
}))

import { billApi, invoiceApi, flowApi, fileApi, wfApi } from '../../api'
import H5ApprovalDetail from './H5ApprovalDetail.vue'

const BILL = {
  id: 9001, billNo: 'BX202610270001', status: 'APPROVING', title: '十月上海出差报销',
  totalAmount: 12400, billType: 'CLAIM', billSubType: 'TRAVEL',
  applyUserName: '蔡海燕', applyDate: '2026-10-20', companyId: 1, deptId: 1003,
  remark: '客户现场支持三天', currentNode: 'Task_3', procInstId: 'PI-9001',
}
const LINES = [
  { itemName: '交通费', amount: 4000, expenseDate: '2026-10-18', taxAmount: 360 },
  { itemName: '住宿费', amount: 6000, expenseDate: '2026-10-19', taxAmount: 0 },
  { itemName: '餐费', amount: 2400, expenseDate: '2026-10-19', taxAmount: 0 },
]
const ATTACHES = [
  { fileId: 11, fileName: 'invoice.jpg', attachType: 'INVOICE' },
  { fileId: 12, fileName: 'itinerary.pdf', attachType: 'OTHER' },
]
const INVOICES = [
  { id: 1, invoiceNo: '24312000000012345678', sellerName: '沪通客运', amount: 4000, invoiceDate: '2026-10-18', verifyStatus: 'PASS' },
  { id: 2, invoiceNo: '24312000000087654321', sellerName: '申城酒店', amount: 6000, invoiceDate: '2026-10-19', verifyStatus: 'PASS' },
]
const CHAIN = [
  { nodeKey: 'Task_1', nodeName: '部门经理审批', handler: '路海英', action: 'APPROVE', time: '2026-10-21 09:12:00', comment: '同意' },
  { nodeKey: 'Task_2', nodeName: '财务审核', handler: '孙燕飞', action: 'APPROVE', time: '2026-10-21 14:03:00' },
  { nodeKey: 'Task_3', nodeName: '共享会计复核', handler: '赵勇', state: 'CURRENT' },
]

/** 折叠卡按页面顺序：单据概要 / 费用明细 / 附件 / 关联发票 / 审批记录 */
function folds(w) { return [...w.element.querySelectorAll('.h5-card--fold')] }
function sumOf(card) { return card.querySelector('.h5-fold__sum')?.textContent || '' }
async function settle(w) { await flushPromises(); await w.vm.$nextTick(); await flushPromises() }

function mountPage(query = {}) {
  route.query = query
  return mount(H5ApprovalDetail, {
    global: { stubs: { 'el-tag': { template: '<span class="el-tag-stub"><slot /></span>' } } },
    attachTo: document.body,
  })
}

describe('H5 审批详情页 · 卡片折叠', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    billApi.detail.mockResolvedValue({ bill: BILL, lines: LINES, attaches: ATTACHES })
    invoiceApi.billInvoices.mockResolvedValue(INVOICES)
    flowApi.chain.mockResolvedValue(CHAIN)
    fileApi.createToken.mockResolvedValue('TK-1')
    wfApi.nextCandidates.mockResolvedValue({ mode: 'PICK', nodeName: 'Task_4', candidates: [{ userId: 3806, name: '蔡海燕' }] })
    wfApi.taskCandidates.mockResolvedValue({ candidates: [] })
  })

  // 全量并行跑测时本用例与其它套件争 CPU 会偶发过 5s 默认超时（单跑恒通过），单独放宽
  it('五段信息卡：只默认展开第一段，其余收起且标题行带摘要', { timeout: 15000 }, async () => {
    const w = mountPage()
    await settle(w)
    const cs = folds(w)
    expect(cs).toHaveLength(5)
    expect(cs.map((c) => c.classList.contains('is-open'))).toEqual([true, false, false, false, false])
    // 摘要＝审批人不展开也能核对的关键数
    expect(sumOf(cs[1])).toBe('3 行 · ￥12,400.00')
    expect(sumOf(cs[2])).toContain('2 份')
    expect(sumOf(cs[3])).toBe('2 张 · ￥10,000.00')
    expect(sumOf(cs[4])).toContain('3 条')
    expect(sumOf(cs[4])).toContain('赵勇')
    // 展开态摘要让位给正文（不与正文里的同一数字重复），金额在正文里照旧可读
    expect(cs[0].querySelector('.h5-fold__body').style.display).not.toBe('none')
    expect(cs[0].querySelector('.h5-fold__sum')).toBeNull()
    expect(cs[0].querySelector('.h5-fold__body').textContent).toContain('12,400.00')
    expect(w.element.textContent).toContain(BILL.billNo)
    // 首卡收起后摘要接管（合计不会因为收起而消失）
    await cs[0].querySelector('.h5-fold__head').click()
    expect(sumOf(cs[0])).toContain('12,400.00')
    expect(sumOf(cs[0])).toContain('费用报销')
    w.unmount()
  })

  it('点一次标题即可展开审批记录（证据仍在一击之内）', async () => {
    const w = mountPage()
    await settle(w)
    const hist = folds(w)[4]
    expect(hist.querySelector('.h5-fold__body').style.display).toBe('none')
    await hist.querySelector('.h5-fold__head').click()
    expect(hist.classList.contains('is-open')).toBe(true)
    expect(hist.querySelector('.h5-fold__body').style.display).not.toBe('none')
    expect(hist.textContent).toContain('路海英')
    expect(hist.textContent).toContain('部门经理审批')
    w.unmount()
  })

  it('缺权 / 加载失败写进摘要，不渲染成"暂无"', async () => {
    invoiceApi.billInvoices.mockRejectedValueOnce({ code: 4030, msg: '您没有「发票·list」权限，请联系管理员开通' })
    flowApi.chain.mockRejectedValueOnce(new Error('boom'))
    const w = mountPage()
    await settle(w)
    const cs = folds(w)
    expect(sumOf(cs[3])).toContain('不开放')
    expect(cs[3].querySelector('.h5-fold__sum').className).toContain('h5-fold__sum--warn')
    expect(sumOf(cs[4])).toBe('加载失败')
    expect(cs[4].querySelector('.h5-fold__sum').className).toContain('h5-fold__sum--err')
    w.unmount()
  })

  it('可办理时：下一环节办理人与审批意见不折叠（动作区收起会断掉办理）', async () => {
    const w = mountPage({ taskId: 'TASK-UUID-1', billType: 'CLAIM' })
    await settle(w)
    expect(folds(w)).toHaveLength(5)
    const plain = [...w.element.querySelectorAll('.h5-card:not(.h5-card--fold)')]
    expect(plain).toHaveLength(2)
    expect(plain[0].textContent).toContain('下一环节')
    expect(plain[0].textContent).toContain('需指定办理人')
    expect(plain[1].textContent).toContain('审批意见')
    // 意见框与选人按钮都在可见流里（没有被折叠正文藏起来）
    expect(w.element.querySelector('.h5-textarea')).toBeTruthy()
    expect(plain[0].querySelector('.h5-pick')).toBeTruthy()
    w.unmount()
  })

  it('折叠交互全程无未捕获异常', async () => {
    const errors = []
    const w = mountPage({ taskId: 'TASK-UUID-2', billType: 'CLAIM' })
    w.vm.$.appContext.config.errorHandler = (e) => errors.push(e)
    await settle(w)
    for (const c of folds(w)) { await c.querySelector('.h5-fold__head').click() }
    await settle(w)
    expect(errors).toHaveLength(0)
    w.unmount()
  })
})
