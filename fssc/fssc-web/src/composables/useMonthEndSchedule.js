// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * useMonthEndSchedule - 月结调度自动化 composable (R5-11)
 *
 * 提供月结任务编排、自动检查、调度提醒功能：
 * - 按期间(YYYY-MM)管理月结任务清单
 * - 任务支持前置依赖（A完成后B才能开始）
 * - 自动检查任务完成状态
 * - 接近月末/月初时触发提醒
 *
 * 用法：
 *   import { useMonthEndSchedule } from '@/composables/useMonthEndSchedule'
 *   const { tasks, checkAll, runAutoSchedule } = useMonthEndSchedule()
 */

import { ref, computed } from 'vue'
import { http } from '@/api'

// 月结标准任务模板（按依赖排序）
const STANDARD_TASKS = [
  { id: 'M1', name: '录入并审核当月所有费用报销单', dept: '报销组', deps: [] },
  { id: 'M2', name: '录入并审核当月所有付款申请', dept: '应付组', deps: [] },
  { id: 'M3', name: '录入并审核当月所有采购入库单', dept: '采购组', deps: [] },
  { id: 'M4', name: '录入并审核当月所有销售出库单', dept: '销售组', deps: [] },
  { id: 'M5', name: '计提固定资产折旧', dept: '资产管理', deps: ['M3', 'M4'] },
  { id: 'M6', name: '计提存货跌价准备', dept: '成本核算', deps: ['M3'] },
  { id: 'M7', name: '核对银行对账单', dept: '出纳', deps: ['M2'] },
  { id: 'M8', name: '核对往来账款余额', dept: '总账', deps: ['M1', 'M2', 'M3', 'M4'] },
  { id: 'M9', name: '计提税金并申报', dept: '税务', deps: ['M8'] },
  { id: 'M10', name: '生成记账凭证', dept: '总账', deps: ['M5', 'M6', 'M7', 'M8'] },
  { id: 'M11', name: '期末结转损益', dept: '总账', deps: ['M10'] },
  { id: 'M12', name: '出具月度财务报表', dept: '财务经理', deps: ['M11'] },
  { id: 'M13', name: '月结封账', dept: '财务总监', deps: ['M12'] },
]

export function useMonthEndSchedule() {
  const currentPeriod = ref('')
  const tasks = ref([])
  const loading = ref(false)
  const autoTimer = ref(null)

  // 任务完成状态
  const completedCount = computed(() => tasks.value.filter((t) => t.status === 'done').length)
  const totalCount = computed(() => tasks.value.length)
  const progress = computed(() => totalCount.value ? Math.round((completedCount.value / totalCount.value) * 100) : 0)
  const isAllDone = computed(() => completedCount.value === totalCount.value && totalCount.value > 0)

  // 获取当前期间 (YYYY-MM)
  function getCurrentPeriod() {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  }

  // 初始化月结任务清单
  async function initPeriod(period) {
    currentPeriod.value = period || getCurrentPeriod()
    loading.value = true
    try {
      // 尝试从后端获取已保存的任务
      const saved = await http.get('/tenant/monthend/tasks', { params: { period: currentPeriod.value } }).catch(() => null)
      if (saved && Array.isArray(saved) && saved.length > 0) {
        tasks.value = saved
      } else {
        // 初始化标准任务模板
        tasks.value = STANDARD_TASKS.map((t) => ({
          ...t,
          status: 'pending', // pending / running / done / skipped
          assignee: '',
          startedAt: null,
          finishedAt: null,
          remark: '',
        }))
      }
    } finally {
      loading.value = false
    }
    return tasks.value
  }

  // 检查任务依赖是否满足
  function canStart(taskId) {
    const task = tasks.value.find((t) => t.id === taskId)
    if (!task || !task.deps || task.deps.length === 0) return true
    return task.deps.every((depId) => {
      const dep = tasks.value.find((t) => t.id === depId)
      return dep && dep.status === 'done'
    })
  }

  // 标记任务开始
  function startTask(taskId) {
    const task = tasks.value.find((t) => t.id === taskId)
    if (!task) return
    if (!canStart(taskId)) {
      const blockedDeps = task.deps.filter((d) => {
        const dep = tasks.value.find((t) => t.id === d)
        return !dep || dep.status !== 'done'
      })
      throw new Error(`任务 ${task.name} 的前置任务未完成: ${blockedDeps.join(', ')}`)
    }
    task.status = 'running'
    task.startedAt = new Date().toISOString()
  }

  // 标记任务完成
  function finishTask(taskId, remark = '') {
    const task = tasks.value.find((t) => t.id === taskId)
    if (!task) return
    task.status = 'done'
    task.finishedAt = new Date().toISOString()
    task.remark = remark
    // 自动启动下一个可执行任务
    autoSchedule()
  }

  // 跳过任务
  function skipTask(taskId, reason = '') {
    const task = tasks.value.find((t) => t.id === taskId)
    if (!task) return
    task.status = 'skipped'
    task.remark = `跳过: ${reason}`
    autoSchedule()
  }

  // 自动调度：找到下一个可以开始的任务
  function autoSchedule() {
    const nextTask = tasks.value.find((t) => t.status === 'pending' && canStart(t.id))
    if (nextTask) {
      console.info(`[月结调度] 下一个可执行任务: ${nextTask.id} ${nextTask.name}`)
      return nextTask
    }
    if (isAllDone.value) {
      console.info('[月结调度] 所有任务已完成，月结结束')
    }
    return null
  }

  // 自动检查所有任务状态
  async function checkAll() {
    loading.value = true
    try {
      // 检查后端是否有任务完成信号
      const result = await http.get('/tenant/monthend/check', { params: { period: currentPeriod.value } }).catch(() => null)
      if (result && result.tasks) {
        // 合并后端状态
        result.tasks.forEach((rt) => {
          const local = tasks.value.find((t) => t.id === rt.id)
          if (local && rt.status === 'done' && local.status !== 'done') {
            local.status = 'done'
            local.finishedAt = rt.finishedAt
          }
        })
      }
    } finally {
      loading.value = false
    }
    return tasks.value
  }

  // 启动定时自动检查（每5分钟）
  function startAutoCheck() {
    if (autoTimer.value) return
    autoTimer.value = setInterval(() => {
      checkAll().catch(() => {})
    }, 5 * 60 * 1000)
  }

  // 停止自动检查
  function stopAutoCheck() {
    if (autoTimer.value) {
      clearInterval(autoTimer.value)
      autoTimer.value = null
    }
  }

  // 月末提醒：检查是否接近月末
  function checkMonthEndAlert() {
    const now = new Date()
    const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate()
    const daysLeft = lastDay - now.getDate()
    if (daysLeft <= 5 && daysLeft >= 0) {
      return {
        shouldAlert: true,
        message: `月末即将到来（剩余 ${daysLeft} 天），请尽快启动月结流程`,
        daysLeft,
      }
    }
    return { shouldAlert: false, daysLeft }
  }

  // 获取甘特图数据
  function getGanttData() {
    return tasks.value.map((t) => ({
      id: t.id,
      name: t.name,
      dept: t.dept,
      deps: t.deps,
      status: t.status,
      start: t.startedAt,
      end: t.finishedAt,
    }))
  }

  return {
    currentPeriod,
    tasks,
    loading,
    progress,
    completedCount,
    totalCount,
    isAllDone,
    initPeriod,
    canStart,
    startTask,
    finishTask,
    skipTask,
    autoSchedule,
    checkAll,
    startAutoCheck,
    stopAutoCheck,
    checkMonthEndAlert,
    getGanttData,
    getCurrentPeriod,
  }
}
