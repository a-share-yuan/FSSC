// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * useAutoFill - 表单智能预填 composable
 * 根据历史数据、当前上下文自动预填表单字段
 *
 * 用法：
 *   import { useAutoFill } from '@/composables/useAutoFill'
 *   const { autofill, getHistoryValue, suggestFields } = useAutoFill()
 *   autofill(form, 'EXPENSE')
 */

import { ref } from 'vue'
import { billApi, store } from '../api'

const STORAGE_PREFIX = 'fssc_form_history_'

export function useAutoFill() {
  const suggestions = ref([])
  const loading = ref(false)

  /**
   * 从 localStorage 获取历史表单值
   * @param {string} formType - 表单类型如 'EXPENSE'
   * @returns {Object} 历史值对象
   */
  function getHistoryValue(formType) {
    try {
      const key = STORAGE_PREFIX + formType
      const data = JSON.parse(localStorage.getItem(key) || '{}')
      return data
    } catch { return {} }
  }

  /**
   * 保存表单值到 localStorage 作为历史
   * @param {string} formType
   * @param {Object} values
   */
  function saveFormHistory(formType, values) {
    try {
      const key = STORAGE_PREFIX + formType
      const existing = getHistoryValue(formType)
      // 合并：保留最近3条记录
      const merged = { ...existing, ...values, _lastUsed: new Date().toISOString() }
      localStorage.setItem(key, JSON.stringify(merged))
    } catch { /* 忽略 */ }
  }

  /**
   * 智能预填表单
   * @param {Object} form - 表单对象（reactive/ref）
   * @param {string} formType - 表单类型
   * @param {Object} options - 预填选项
   * @returns {Object} 预填的字段名数组
   */
  function autofill(form, formType, options = {}) {
    const filled = []
    const history = getHistoryValue(formType)
    const target = form.value ? form.value : form

    // 1. 从历史填入
    const FIELDS = [
      'deptId', 'deptName', 'costCenter', 'projectId',
      'payeeName', 'payeeAccount', 'payeeBank',
      'currency', 'expenseType', 'budgetItemId',
    ]

    FIELDS.forEach(field => {
      if (!target[field] && history[field]) {
        target[field] = history[field]
        filled.push(field)
      }
    })

    // 2. 从用户信息预填
    if (store.user) {
      if (!target.applicantId) {
        target.applicantId = store.user.userId ?? store.user.id
        filled.push('applicantId')
      }
      if (!target.applicantName && store.user.realName) {
        target.applicantName = store.user.realName
        filled.push('applicantName')
      }
      if (!target.deptId && store.user.deptId) {
        target.deptId = store.user.deptId
        target.deptName = store.user.deptName || ''
        filled.push('deptId', 'deptName')
      }
    }

    // 3. 默认值
    if (!target.currency) {
      target.currency = 'CNY'
      filled.push('currency')
    }
    if (!target.exchangeRate) {
      target.exchangeRate = 1.0
      filled.push('exchangeRate')
    }
    if (!target.billDate) {
      // D-DEC-361（补漏腿）：这里原先用 toISOString() 取「今天」，那是 UTC 串——东八区恒差 −8h，
      // 0~8 点之间制单会把单据日期预填成"昨天"，与后端 LocalDateTime.now() 落库钟口不一致。
      // 与 composables/useBill.js 的 localDateParts 同口径（本地时钟，零依赖，不引日期库）。
      const d = new Date()
      const p = (n) => String(n).padStart(2, '0')
      target.billDate = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
      filled.push('billDate')
    }

    // 4. 选项自定义默认值
    if (options.defaults) {
      Object.entries(options.defaults).forEach(([k, v]) => {
        if (!target[k]) {
          target[k] = v
          filled.push(k)
        }
      })
    }

    return { filled: [...new Set(filled)], form: target }
  }

  /**
   * 获取字段建议（基于历史频率）
   * @param {string} formType
   * @param {string} fieldName
   * @returns {Array} 建议值列表
   */
  function suggestFields(formType, fieldName) {
    const history = getHistoryValue(formType)
    const val = history[fieldName]
    if (val == null) return []
    return Array.isArray(val) ? val : [val]
  }

  /**
   * 从后端加载历史单据数据用于预填
   * @param {string} billType
   * @param {number} limit
   */
  async function loadHistoryForFill(billType, limit = 5) {
    loading.value = true
    try {
      const res = await billApi.myPage({ pageNum: 1, pageSize: limit, billType })
      const list = res.list || []
      // 提取高频字段值
      const fieldStats = {}
      list.forEach(bill => {
        Object.keys(bill).forEach(key => {
          if (!fieldStats[key]) fieldStats[key] = {}
          const val = bill[key]
          if (val != null && typeof val !== 'object') {
            fieldStats[key][val] = (fieldStats[key][val] || 0) + 1
          }
        })
      })
      // 取每个字段最高频的值
      suggestions.value = Object.entries(fieldStats).map(([field, counts]) => {
        const top = Object.entries(counts).sort((a, b) => b[1] - a[1])[0]
        return { field, value: top[0], count: top[1] }
      }).filter(s => s.count > 1)
      return suggestions.value
    } catch { return [] } finally { loading.value = false }
  }

  return {
    suggestions, loading,
    autofill, getHistoryValue, saveFormHistory,
    suggestFields, loadHistoryForFill,
  }
}
