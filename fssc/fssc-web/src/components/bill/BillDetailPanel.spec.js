// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * 回归：单据详情面板必须把审批人关心的字段真正渲染出来。
 *
 * 背景：整改前「去处理」把审批人送到业务模块的新建表单页，审批人看不到附件、OCR 结果与审批历史，
 * 且部分待办行「标题/单号/金额」三空 —— 属实质盲审。本用例锁死「详情可见」这一底线：
 *   1) 单号、标题、金额、明细行、附件、审批历史都要出现在 DOM；
 *   2) 后端契约是 { bill, lines, attaches }，若错取 detail.billNo（少了 .bill 一层）本用例会红；
 *   3) 审批历史查询必须带 bizId（传 billId 会导致后端不过滤 → 返回全租户历史）。
 */
vi.mock('@/api', () => ({
  billApi: {
    detail: vi.fn(() => Promise.resolve({
      bill: {
        id: 901, billNo: 'BX202609120001', title: 'DEV08 回归专用单', billType: 'CLAIM', billSubType: 'TRAVEL',
        status: 'APPROVING', applyUserName: '张三', applyDate: '2026-09-12',
        totalAmount: 1234.56, taxAmount: 69.88, netAmount: 1164.68, currencyCode: 'CNY',
        flowName: '费用报销流程', currentNode: '直接上级审核',
      },
      lines: [{ lineNo: 1, itemName: '市内交通费', amount: 1234.56, taxRate: 6, taxAmount: 69.88, overStd: 0 }],
      attaches: [{ id: 77, fileId: 555, attachType: 'INVOICE', createTime: '2026-09-12 07:00:00' }],
    })),
  },
  flowApi: {
    historyPage: vi.fn(() => Promise.resolve({
      list: [{ id: 1, nodeName: '提交报账单', action: 'SUBMIT', operUserName: '张三', operTime: '2026-09-12 07:00:00', comment: 'DEV08 意见' }],
    })),
  },
  imageApi: { ocrByFile: vi.fn(() => Promise.resolve(1)) },
  invoiceApi: {
    billInvoices: vi.fn(() => Promise.resolve([{ id: 1, billId: 901, invoiceId: 8001, amount: 1234.56 }])),
    page: vi.fn(() => Promise.resolve({ list: [{ id: 8001, invoiceNo: '24312000000099998888', invoiceDate: '2026-09-10', sellerName: '示例供应商', amount: 1234.56, taxAmount: 69.88, verifyStatus: 'PASS', lockStatus: 'LOCKED_IN_USE' }] })),
  },
  fileApi: { detail: vi.fn(() => Promise.resolve({ fileName: 'a.pdf', fileSuffix: 'pdf' })) },
  // 组件按权限决定是否请求审批历史/关联发票（utils/perm 读 store.user.perms），
  // mock 必须一并给出 store，否则 setup 里 hasPerm 取不到导出直接抛错。
  store: { user: { perms: ['wf:history:list', 'inv:list'] } },
}))

import BillDetailPanel from './BillDetailPanel.vue'

describe('BillDetailPanel 详情渲染', () => {
  beforeEach(() => vi.clearAllMocks())

  it('渲染单号/标题/金额/明细/附件/审批历史（不允许空壳）', async () => {
    const w = mount(BillDetailPanel, { props: { billId: 901, billType: 'CLAIM' } })
    await flushPromises()
    await flushPromises()
    const text = w.text()
    expect(text).toContain('BX202609120001')
    expect(text).toContain('DEV08 回归专用单')
    expect(text).toContain('1,234.56')
    expect(text).toContain('市内交通费')
    expect(text).toContain('发票')          // 附件类型标签
    expect(text).toContain('提交报账单')    // 审批历史节点
    expect(text).toContain('DEV08 意见')    // 审批意见
    expect(text).not.toContain('无法加载单据详情')
  })

  it('契约校验：detail 返回 { bill } 嵌套结构，不是扁平对象', async () => {
    const { billApi } = await import('@/api')
    mount(BillDetailPanel, { props: { billId: 901 } })
    await flushPromises()
    expect(billApi.detail).toHaveBeenCalledWith(901)
  })

  it('审批历史查询必须带 bizId（传 billId 会被后端忽略过滤条件）', async () => {
    const { flowApi } = await import('@/api')
    mount(BillDetailPanel, { props: { billId: 901, billType: 'CLAIM' } })
    await flushPromises()
    expect(flowApi.historyPage).toHaveBeenCalledWith(expect.objectContaining({ bizId: 901, bizType: 'CLAIM' }))
    const arg = flowApi.historyPage.mock.calls[0][0]
    expect(arg.billId).toBeUndefined()
  })

  it('billId 为空时不请求后端（避免无意义 404）', async () => {
    const { billApi } = await import('@/api')
    mount(BillDetailPanel, { props: { billId: null } })
    await flushPromises()
    expect(billApi.detail).not.toHaveBeenCalled()
  })
})
