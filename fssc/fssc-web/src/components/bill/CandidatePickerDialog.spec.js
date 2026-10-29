// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import CandidatePickerDialog from './CandidatePickerDialog.vue'

/**
 * 候选选择器回归护栏：必须「勾选生效 + 金额可改 + 确定才入账」。
 *
 * 改前形态（缺陷本体）：表格只有 @row-click，点任意一行立刻按「可冲抵余额全额」push 进表单并关窗，
 * 无确认、金额不可调、键盘不可达、无可发现性提示。本用例把底线逐条锁死：
 *   ① 未勾选时【确定】禁用（空提交被拦，且不再能靠"点行"绕过）；
 *   ② 勾选后出现金额输入、默认＝可冲抵余额；
 *   ③ 金额可改并原样带出（部分冲抵的前提，不再写死全额）；
 *   ④ 金额超上限时【确定】禁用（前端闸；后端 registerLoanOccupancy 再兜一层）；
 *   ⑤ 已在表单里的候选行不可再勾选（防同一张单重复入账）。
 */
const rows = [
  { id: 11, billNo: 'JK202610190003', title: '差旅借款', totalAmount: 5000, loanBalance: 5000 },
  { id: 12, billNo: 'JK202610190004', title: '备用金借款', totalAmount: 8000, loanBalance: 3000 },
]

/** el-table 首帧只渲染表头、行要再等一拍才挂上（与其它 el-table 用例同口径） */
async function settle(w) {
  await w.vm.$nextTick()
  await w.vm.$nextTick()
  await new Promise((r) => setTimeout(r, 60))
  await w.vm.$nextTick()
}

function mountPicker(props = {}) {
  return mount(CandidatePickerDialog, {
    props: { modelValue: true, title: '选择借款单冲抵', rows, ...props },
    global: { plugins: [ElementPlus] },
  })
}

function checkboxes(w) {
  return w.findAll('.el-table__body-wrapper input[type="checkbox"]')
}

function confirmBtn(w) {
  const btns = w.findAll('.el-dialog__footer button')
  return btns[btns.length - 1]
}

function disabled(w) {
  // el-button / el-checkbox 的禁用态是 disabled=""（空串属性），必须按"属性是否存在"判，不能按真值
  const btn = confirmBtn(w)
  return btn.attributes('disabled') !== undefined || btn.classes().includes('is-disabled')
}

function rowCheckboxDisabled(w, idx) {
  const cb = w.findAll('.el-table__body-wrapper tbody tr')[idx].find('input[type="checkbox"]')
  return cb.attributes('disabled') !== undefined || cb.classes().includes('is-disabled')
}

describe('CandidatePickerDialog 勾选+金额+确定', () => {
  // 全量并行跑测时本用例与其它套件争 CPU 会偶发过 5s 默认超时（单跑恒通过），单独放宽
  it('① 未勾选任何行时【确定】禁用（空提交被拦）', { timeout: 15000 }, async () => {
    const w = mountPicker()
    await settle(w)
    expect(confirmBtn(w).text()).toContain('确定')
    expect(disabled(w)).toBe(true)
  })

  it('未勾选的行给出「勾选后可输入」提示，不再暗示"行可点"', async () => {
    const w = mountPicker()
    await settle(w)
    expect(w.text()).toContain('勾选后可输入')
  })

  it('②③ 勾选后金额输入出现且默认=可冲抵余额；改成部分金额后原样随 confirm 带出', async () => {
    const w = mountPicker()
    await settle(w)
    expect(checkboxes(w).length).toBe(2)

    await checkboxes(w)[0].setValue(true)
    await settle(w)
    expect(disabled(w)).toBe(false)   // 勾选后【确定】可用

    const amountInput = w.findAll('.el-table__body-wrapper input[role="spinbutton"], .el-table__body-wrapper input.el-input__inner')[0]
    expect(amountInput).toBeTruthy()
    expect(Number(amountInput.element.value)).toBe(5000)   // 默认＝未还余额

    await amountInput.setValue('4600')                      // 部分冲抵（G1 剧情：4,600 冲 5,000）
    await settle(w)
    await confirmBtn(w).trigger('click')

    const emitted = w.emitted('confirm')
    expect(emitted).toBeTruthy()
    expect(emitted[0][0]).toHaveLength(1)
    expect(emitted[0][0][0].amount).toBe(4600)
    expect(emitted[0][0][0].row.billNo).toBe('JK202610190003')
    // 确定后自动关窗
    expect(w.emitted('update:modelValue').at(-1)[0]).toBe(false)
  })

  it('④a 超额输入被 el-input-number 的 :max 钳制到可冲抵余额（用户输不进超额值）', async () => {
    const w = mountPicker()
    await settle(w)
    await checkboxes(w)[1].setValue(true)     // 第二行可冲抵余额只有 3000
    await settle(w)
    expect(disabled(w)).toBe(false)

    const amountInput = w.findAll('.el-table__body-wrapper input[role="spinbutton"], .el-table__body-wrapper input.el-input__inner')[0]
    await amountInput.setValue('9999')
    await settle(w)
    expect(Number(w.vm.amounts[12])).toBeLessThanOrEqual(3000)
    expect(Number(amountInput.element.value)).toBeLessThanOrEqual(3000)
  })

  it('④b 可冲抵余额为 0 的候选（余额被在途占满）整行不可选，确认按钮保持禁用', async () => {
    const zeroRows = [
      { id: 31, billNo: 'JK202610190009', title: '已占满借款', totalAmount: 1000, loanBalance: 0 },
      { id: 32, billNo: 'JK202610190010', title: '可用借款', totalAmount: 2000, loanBalance: 800 },
    ]
    const w = mount(CandidatePickerDialog, {
      props: { modelValue: true, rows: zeroRows, maxField: 'loanBalance' },
      global: { plugins: [ElementPlus] },
    })
    await settle(w)
    expect(rowCheckboxDisabled(w, 0)).toBe(true)   // 余额 0 → 不可选
    expect(rowCheckboxDisabled(w, 1)).toBe(false)  // 有余额 → 可选
    expect(disabled(w)).toBe(true)                  // 没有可确认的行
    await confirmBtn(w).trigger('click')
    expect(w.emitted('confirm')).toBeFalsy()
  })

  it('⑤ 已在表单里的候选（pickedIds）不可再勾选，防重复入账', async () => {
    const w = mountPicker({ pickedIds: [11] })
    await settle(w)
    expect(rowCheckboxDisabled(w, 0)).toBe(true)
    expect(rowCheckboxDisabled(w, 1)).toBe(false)
  })

  it('取消勾选后清掉金额、【确定】重新禁用（不留脏值、不产生 confirm）', async () => {
    const w = mountPicker()
    await settle(w)
    await checkboxes(w)[0].setValue(true)
    await settle(w)
    expect(disabled(w)).toBe(false)

    await checkboxes(w)[0].setValue(false)
    await settle(w)
    expect(disabled(w)).toBe(true)
    expect(w.emitted('confirm')).toBeFalsy()
  })

  it('申请关联场景（showMaxColumn=false）不渲染可冲抵余额列', async () => {
    const applyRows = [{ id: 21, billNo: 'AP202610190001', title: '会议申请', totalAmount: 4600 }]
    const w = mount(CandidatePickerDialog, {
      // tip 置空：否则提示文案里含「默认=可冲抵余额」字样，会与"列不存在"的断言打架
      props: { modelValue: true, rows: applyRows, maxField: 'totalAmount', showMaxColumn: false, tip: '' },
      global: { plugins: [ElementPlus] },
    })
    await settle(w)
    expect(w.text()).not.toContain('可冲抵余额')
    expect(checkboxes(w).length).toBe(1)
  })
})
