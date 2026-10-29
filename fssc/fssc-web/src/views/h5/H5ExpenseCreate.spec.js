import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * H5 提单页 · 提交审批的重复点击闸。
 *
 * 为什么只能用桩：这张腿一跑通就会真落草稿＋真起流程（写库），而测试要求库面零写入。
 * 于是把 billApi.saveDraft 挂成一条永不返回的请求，模拟"提交正在路上"那几秒
 * （服务端要几秒才回），再点第二次看它进不进得去。
 * 闸如果只挂在 doSaveDraft 那一刻，前面的校验／软控弹窗／预算判据都漏在闸外。
 */

const route = { params: {}, query: {}, path: '/h5/expense/new', meta: {} }

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
  useRoute: () => route,
  onBeforeRouteLeave: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), closeAll: vi.fn() },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), prompt: vi.fn(() => Promise.resolve({ value: '额度调剂说明' })) },
}))

vi.mock('../../api', () => ({
  billApi: { detail: vi.fn(), editForResubmit: vi.fn(), saveDraft: vi.fn(), submit: vi.fn() },
  mdApi: { expenseItems: vi.fn(), budgets: vi.fn() },
  // 真件是 masterCfgApi.itemBudgetMap.options()（api.js:1287），桩形状必须同构，否则 loadMd 直接抛
  masterCfgApi: { itemBudgetMap: { options: vi.fn() } },
  orgApi: { companyTree: vi.fn(), deptTree: vi.fn() },
  invoiceApi: { page: vi.fn(), billInvoices: vi.fn(), bind: vi.fn(), unbind: vi.fn() },
  fileApi: { attachUpload: vi.fn() },
  wfApi: { startCandidates: vi.fn() },
  flowApi: { chain: vi.fn() },
  // useDict/useBaseDict 走这三条取字典；回空 ⇒ 落回 PAY_METHOD_LOCAL／PAYEE_TYPE_LOCAL
  dictApi: { builtinDataList: vi.fn(), dataList: vi.fn(), builtinDataBatch: vi.fn() },
  isRequestCancelled: vi.fn(() => false),
  store: { token: 'T', user: { userId: 3795, username: '100001', realName: '蔡海燕', perms: ['*:*:*'] }, perms: ['*:*:*'], features: [] },
}))

import { billApi, mdApi, masterCfgApi, orgApi, invoiceApi, fileApi, wfApi, flowApi, dictApi } from '../../api'
import H5ExpenseCreate from './H5ExpenseCreate.vue'

const ITEMS = [{ id: 7001, itemName: '市内交通费', needInvoice: 0, taxRate: 0 }]

function firstOptionValue(el) {
  const o = Array.from(el.options).find((x) => x.value !== '')
  return o ? o.value : null
}

/** 把 CLAIM 的必输项按 constants/billRequired.js 登记表填满（缺一都会被 validateForm 拦在第一关） */
async function fillValid(w) {
  await w.find('#h5-f-title').setValue('十月客户现场支持')
  await w.find('#h5-f-applyDate').setValue('2026-10-20')
  await w.find('#h5-f-remark').setValue('客户现场支持三天，高铁与住宿')
  await w.find('#h5-f-periodYear').setValue(firstOptionValue(w.find('#h5-f-periodYear').element))
  await w.find('#h5-f-paperAttachCount').setValue('1')
  await w.find('#h5-f-payTarget').setValue(firstOptionValue(w.find('#h5-f-payTarget').element))
  await w.find('#h5-f-payMethod').setValue(firstOptionValue(w.find('#h5-f-payMethod').element))
  await w.find('#h5-f-accountName').setValue('蔡海燕')
  await w.find('#h5-f-payeeAccountNo').setValue('6222020000000001')
  await w.find('#h5-f-line0-itemId').setValue('7001')
  await w.find('#h5-f-line0-amount').setValue('1200')
}

function actionBar(w) { return [...w.element.querySelectorAll('.h5-actionbar button')] }
const submitBtn = (w) => actionBar(w).find((b) => /提交审批|重新提交|处理中/.test(b.textContent))
const draftBtn = (w) => actionBar(w).find((b) => /存草稿/.test(b.textContent))

async function settle(w) { await flushPromises(); await w.vm.$nextTick(); await flushPromises() }

function mountPage() {
  return mount(H5ExpenseCreate, { attachTo: document.body })
}

describe('H5 提单页 · 提交审批的重复点击闸', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mdApi.expenseItems.mockResolvedValue(ITEMS)
    mdApi.budgets.mockResolvedValue({ list: [] })
    masterCfgApi.itemBudgetMap.options.mockResolvedValue([])
    orgApi.companyTree.mockResolvedValue([])
    orgApi.deptTree.mockResolvedValue([])
    dictApi.builtinDataList.mockResolvedValue({ code: 0, data: [] })
    dictApi.dataList.mockResolvedValue({ code: 0, data: [] })
    dictApi.builtinDataBatch.mockResolvedValue({ code: 0, data: [] })
    invoiceApi.page.mockResolvedValue({ list: [], total: 0 })
    invoiceApi.billInvoices.mockResolvedValue([])
    flowApi.chain.mockResolvedValue([])
    fileApi.attachUpload.mockResolvedValue({ fileId: 1, fileName: 'a.jpg', attachType: 'OTHER' })
    wfApi.startCandidates.mockResolvedValue({ mode: 'AUTO', candidates: [] })
    billApi.submit.mockResolvedValue(undefined)
  })

  it('表头必填没填完时不发任何请求，也不把自己锁死（点完还能再点）', async () => {
    const w = mountPage()
    await settle(w)
    submitBtn(w).click()
    await settle(w)
    expect(billApi.saveDraft).not.toHaveBeenCalled()
    expect(submitBtn(w).disabled).toBe(false)
    w.unmount()
  })

  it('提交在路上时第二次点击被吞掉：saveDraft 只发一次，两个出口同时 inert', async () => {
    billApi.saveDraft.mockImplementation(() => new Promise(() => {})) // 永不返回＝请求还在路上
    const w = mountPage()
    await settle(w)
    await fillValid(w)
    await settle(w)
    expect(billApi.saveDraft).not.toHaveBeenCalled() // 前置校验关没放过非法表单

    // 同一拍连点两下＋补一下存草稿：真实的双击就是这个节奏（中间不给事件循环喘气的机会）
    submitBtn(w).click()
    submitBtn(w).click()
    draftBtn(w).click()
    await flushPromises()
    expect(billApi.saveDraft).toHaveBeenCalledTimes(1)
    expect(submitBtn(w).disabled).toBe(true)
    expect(draftBtn(w).disabled).toBe(true)
    expect(submitBtn(w).textContent).toContain('处理中')

    submitBtn(w).click()
    draftBtn(w).click()
    await flushPromises()
    expect(billApi.saveDraft).toHaveBeenCalledTimes(1)
    expect(billApi.submit).not.toHaveBeenCalled()
    w.unmount()
  })

  it('提交成功后按钮不会再放开第二次提交：同一条链上只允许一次 submit', async () => {
    let release
    billApi.saveDraft.mockImplementation(() => new Promise((r) => { release = r }))
    billApi.submit.mockImplementation(() => new Promise(() => {}))
    const w = mountPage()
    await settle(w)
    await fillValid(w)
    submitBtn(w).click()
    await flushPromises()
    release(9001)                       // 草稿回包，链子继续走到 submit
    await flushPromises()
    expect(billApi.submit).toHaveBeenCalledTimes(1)
    submitBtn(w).click()
    await flushPromises()
    expect(billApi.submit).toHaveBeenCalledTimes(1)
    w.unmount()
  })
})
