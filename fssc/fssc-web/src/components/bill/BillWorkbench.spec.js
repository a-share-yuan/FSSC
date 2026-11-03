// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

/**
 * PC 单据台 · 提交审批的重复点击闸。
 *
 * 与 H5 那份判据成对（见 H5ExpenseCreate.spec.js，反置探针下连点一次会落 3 条 saveDraft）。
 * 这里守的是 b.submitting 罩不到的两处：
 *   ① 点下去到 submitting 落下之间（本页 billInfoForm 校验＋beforeSubmit 钩子）——
 *      反置探针下连点一次会落进 2 条 saveDraft；
 *   ② 失败路径放闸：被服务端「请勿重复提交」打回后按钮必须立刻恢复可点，
 *      否则闸就把页面焊死，用户只能刷新。
 * 同样不能真提交（会落草稿＋起流程），所以把 saveDraft 挂成永不返回。
 */

vi.mock('../../api', () => {
  // 不要这两枚必须定义在 factory 里：vi.mock 会被提到文件最顶，模块级常量此时还没初始化
  const never = () => new Promise(() => {})
  const ok = (v) => () => Promise.resolve(v)
  return {
    billApi: { saveDraft: vi.fn(never), submit: vi.fn(never), detail: vi.fn(ok({})), myPage: vi.fn(ok({ list: [], total: 0 })),
      withdraw: vi.fn(ok(null)), voidBill: vi.fn(ok(null)), unaudit: vi.fn(ok(null)), editForResubmit: vi.fn(ok(null)),
      closeApply: vi.fn(ok(null)), terminateApply: vi.fn(ok(null)) },
    mdApi: { expenseItems: vi.fn(ok([])), budgets: vi.fn(ok({ list: [] })), costCenters: vi.fn(ok([])), projects: vi.fn(ok([])),
      payees: vi.fn(ok([])), cities: vi.fn(ok([])), invoiceRuleMatch: vi.fn(ok([])) },
    detailApi: { travels: vi.fn(ok([])), shares: vi.fn(ok([])), relations: vi.fn(ok([])), loanOffsets: vi.fn(ok([])),
      loanCandidates: vi.fn(ok([])), saveTravels: vi.fn(ok(null)), replaceShares: vi.fn(ok(null)), addRelation: vi.fn(ok(null)),
      addLoanOffset: vi.fn(ok(null)) },
    fileApi: { upload: vi.fn(ok(null)), createToken: vi.fn(ok('TK')), attachUpload: vi.fn(ok({})) },
    imageApi: { ocrByFile: vi.fn(ok({})) },
    sharingApi: { voucherPage: vi.fn(ok({ list: [], total: 0 })), voucherLines: vi.fn(ok([])), voucherPost: vi.fn(ok(null)) },
    contractApi: { page: vi.fn(ok({ list: [], total: 0 })) },
    customerApi: { candidates: vi.fn(ok([])) },
    complianceApi: { precheck: vi.fn(ok([])) },
    orgApi: { companyTree: vi.fn(ok([])), deptTree: vi.fn(ok([])) },
    flowApi: { chain: vi.fn(ok([])), historyPage: vi.fn(ok({ list: [], total: 0 })) },
    wfApi: { startCandidates: vi.fn(ok({ mode: 'AUTO', candidates: [] })), urgeInitiator: vi.fn(ok(null)),
      approve: vi.fn(ok(null)), transfer: vi.fn(ok(null)), addSign: vi.fn(ok(null)) },
    masterCfgApi: { itemBudgetMap: { options: vi.fn(ok([])) } },
    dictApi: { builtinDataList: vi.fn(ok({ code: 0, data: [] })), dataList: vi.fn(ok({ code: 0, data: [] })),
      builtinDataBatch: vi.fn(ok({ code: 0, data: [] })) },
    isRequestCancelled: () => false,
    store: { token: 'T', user: { userId: 3795, username: '100001', realName: '蔡海燕', perms: ['*:*:*'] }, perms: ['*:*:*'], features: [] },
  }
})

import { billApi } from '../../api'
import BillWorkbench from './BillWorkbench.vue'

const CHILD_STUBS = {
  UploadButton: true, FileViewer: true, FlowProgressPanel: true,
  BillTraceDrawer: true, CandidatePickerDialog: true, PrintDialog: true,
}

function mountBench() {
  return mount(BillWorkbench, {
    props: { billType: 'CLAIM', subType: '', claim: true },
    global: { plugins: [ElementPlus], stubs: CHILD_STUBS },
    attachTo: document.body,
  })
}

/** 底部操作栏：[保存草稿, 提交审批, (新建空白单)] */
function footer(w) {
  const cards = [...w.element.querySelectorAll('.el-button')]
  const draft = cards.find((b) => /保存草稿/.test(b.textContent))
  const submit = cards.find((b) => /提交审批/.test(b.textContent))
  return { draft, submit }
}

/** 把 constants/billRequired.js 登记的 CLAIM 必输项灌满（走 defineExpose 的 b，不去戳 12 个 el-input） */
function fillValid(w) {
  const b = w.vm.b
  Object.assign(b.form.bill, {
    applyUserId: 3795, title: '十月客户现场支持', applyDate: '2026-10-20', currencyCode: 'CNY',
    companyId: 1, deptId: 1003, periodYear: 2026, payTarget: 'EMPLOYEE',
    payeeAccountNo: '6222020000000001', remark: '客户现场支持三天',
  })
  b.form.bill.extJson = { paperAttachCount: 1, payMethod: 'BANK', accountName: '蔡海燕' }
  b.form.lines.length = 0
  b.form.lines.push({ lineNo: 1, itemId: 7001, amount: 1200, expenseDate: '2026-10-18', taxRate: 0 })
}

async function settle(w) { await flushPromises(); await w.vm.$nextTick(); await flushPromises() }

describe('PC 单据台 · 提交审批的重复点击闸', () => {
  beforeEach(() => {
    vi.clearAllMocks() // 计数必须清零：桩是模块级单例，不清就把上一条用例的调用算进来
    billApi.saveDraft.mockImplementation(() => new Promise(() => {}))
    billApi.submit.mockImplementation(() => new Promise(() => {}))
  })

  it('必输项没填齐：不发请求，也不把自己锁死（点完还能再点）', async () => {
    const w = mountBench()
    await settle(w)
    const { submit } = footer(w)
    expect(submit).toBeTruthy()
    submit.click()
    await settle(w)
    expect(billApi.saveDraft).not.toHaveBeenCalled()
    expect(submit.disabled).toBe(false)
    w.unmount()
  })

  it('同一拍连点三次【提交审批】＋补一下【保存草稿】⇒ 只发一条 saveDraft，两个出口同时 inert', async () => {
    const w = mountBench()
    await settle(w)
    fillValid(w)
    await settle(w)
    const { draft, submit } = footer(w)
    submit.click()
    submit.click()
    submit.click()
    draft.click()
    await settle(w)
    expect(billApi.saveDraft).toHaveBeenCalledTimes(1)
    expect(submit.disabled).toBe(true)
    expect(draft.disabled).toBe(true)
    w.unmount()
  })

  it('提交被服务端拒掉后闸必须立刻松开：不能把页面焊死（这条也防 finally 漏写）', async () => {
    // 原缺陷的另一半：闸只在成功路径上放得开的话，用户被「请勿重复提交」「仅草稿可提交」打回之后
    // 就再也点不动了。失败路径必须恢复可点，且失败原因落成常驻横幅而不是闪过即没的 toast。
    billApi.saveDraft.mockResolvedValue(9001)
    billApi.submit.mockRejectedValue({ message: '请勿重复提交：该单据正在或已经提交', code: 400 })
    const w = mountBench()
    await settle(w)
    fillValid(w)
    footer(w).submit.click()
    await settle(w)
    expect(billApi.submit).toHaveBeenCalledTimes(1)
    const live = footer(w) // 失败路径不切页签 ⇒ 按钮还在原位，重新取一次再判
    expect(live.submit.disabled).toBe(false)
    expect(live.draft.disabled).toBe(false)
    // 松开之后必须还能再点（真的可以再点一次，说明状态机回到了初始态）
    live.submit.click()
    await settle(w)
    expect(billApi.saveDraft).toHaveBeenCalledTimes(2)
    w.unmount()
  })
})
