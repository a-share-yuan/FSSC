// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * billStatus.spec.js - 统一状态枚举单元测试 (R5-09)
 */
import { describe, it, expect } from 'vitest'
import { BillStatus as STATUS, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from './billStatus'

describe('billStatus constants', () => {
  it('STATUS enum has lifecycle statuses', () => {
    expect(STATUS.DRAFT).toBe('DRAFT')
    expect(STATUS.APPROVING).toBe('APPROVING')
    expect(STATUS.APPROVED).toBe('APPROVED')
    expect(STATUS.PAID).toBe('PAID')
    expect(STATUS.POSTED).toBe('POSTED')
    expect(STATUS.ARCHIVED).toBe('ARCHIVED')
  })

  it('STATUS_LABEL maps all statuses', () => {
    expect(STATUS_LABEL.DRAFT).toBe('草稿')
    expect(STATUS_LABEL.APPROVED).toBe('已审批')
    expect(STATUS_LABEL.PAID).toBe('已付款')
    expect(STATUS_LABEL.POSTED).toBe('已记账')
    expect(STATUS_LABEL.ARCHIVED).toBe('已归档')
  })

  it('STATUS_TYPE assigns correct tag types', () => {
    expect(STATUS_TYPE.DRAFT).toBe('info')
    expect(STATUS_TYPE.APPROVED).toBe('success')
    expect(STATUS_TYPE.REJECTED).toBe('danger')
  })

  // LIFECYCLE_STEPS / getLifecycleStep 随 status 驱动步进条一并删除，
  // 相应断言（含 `getLifecycleStep('ARCHIVED') === 5` 那条把「永灰」写成基线的用例）不再存在。

  it('statusLabel returns Chinese label', () => {
    expect(statusLabel('DRAFT')).toBe('草稿')
    expect(statusLabel('APPROVED')).toBe('已审批')
  })

  it('statusLabel returns original string for unknown', () => {
    expect(statusLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  it('statusType returns info for unknown', () => {
    expect(statusType('UNKNOWN')).toBe('info')
  })
})
