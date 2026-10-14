// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * useFormRules.spec.js - useFormRules composable 单元测试 (R5-09)
 */
import { describe, it, expect } from 'vitest'
import { useFormRules } from './useFormRules'

describe('useFormRules', () => {
  it('returns rules for EXPENSE_CLAIM form type', () => {
    const { rules } = useFormRules('EXPENSE_CLAIM')
    expect(rules.value).toBeDefined()
    expect(rules.value.applyUserId).toBeDefined()
    expect(rules.value.totalAmount).toBeDefined()
  })

  it('returns empty object for unknown form type', () => {
    const { rules } = useFormRules('UNKNOWN_TYPE')
    expect(rules.value).toEqual({})
  })

  it('returns commonRules with required, amount, phone validators', () => {
    const { commonRules } = useFormRules('LOAN')
    expect(commonRules.required()).toBeDefined()
    expect(commonRules.amount()).toBeDefined()
    expect(commonRules.phone()).toBeDefined()
    expect(commonRules.required()[0].required).toBe(true)
  })

  it('budgetOverrunValidator triggers when value > budget', () => {
    const { budgetOverrunValidator } = useFormRules('EXPENSE_CLAIM')
    let called = false
    budgetOverrunValidator(200, 100, (err) => {
      called = true
      expect(err).toBeInstanceOf(Error)
    })
    expect(called).toBe(true)
  })

  it('budgetOverrunValidator passes when value <= budget', () => {
    const { budgetOverrunValidator } = useFormRules('EXPENSE_CLAIM')
    // Element 校验器约定：无论通过与否都必须调用 callback，通过时传 undefined。
    // 不调用 callback 会让表单校验 Promise 永远挂起。
    let called = false
    let received = 'unset'
    budgetOverrunValidator(50, 100, (err) => {
      called = true
      received = err
    })
    expect(called).toBe(true)
    expect(received).toBeUndefined()
  })

  it('getRule returns rules for specific field', () => {
    const { getRule } = useFormRules('LOAN')
    const loanAmountRules = getRule('loanAmount')
    expect(loanAmountRules).toBeDefined()
    expect(loanAmountRules.length).toBeGreaterThan(0)
  })
})
