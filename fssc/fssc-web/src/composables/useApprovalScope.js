// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 *
 * 背景（2026-09-17 缺陷整改）：
 *   全项目列表页原先用「单据状态 = APPROVING」来决定是否显示「审批通过 / 驳回」按钮，
 *   完全不判断「这单是否轮到我审批」，导致：
 *     - 申请人打开「我的单据」看到自己单据的「审批通过 / 驳回」；
 *     - 任意有列表页权限的人看到全部在途单据的审批按钮。
 *   即便后端有 V1.3.19 越权守卫（非办理人 403）+ 自审禁止（approve.deny_self=1 fail-closed），
 *   用户点了也必然报错 —— 纯属把不该出现的按钮放出来了。
 *
 * 正确口径（与「我的待办」完全一致）：
 *   只有「当前登录用户在 /tenant/wf/todo 中存在该单据的待办任务」时，才允许在列表上直接审批。
 *   待办接口本身已按 assignee / 候选人过滤，因此它就是「是否轮到我」的权威数据源。
 *
 * 用法：
 *   const { canApprove, taskIdOf, refresh } = useApprovalScope()
 *   <InlineActions :show-approve="canApprove(row)" :show-reject="canApprove(row)" ... />
 *   // 审批成功后调用 refresh() 让集合重新对齐
 *
 * 说明：模块级缓存 + 单飞（inflight）复用，多个页面/组件共用同一份数据，不会重复请求。
 *      调用失败（网络/异常）时降级为空集合 —— 宁可少显示按钮，也绝不显示错误按钮。
 */
import { ref } from 'vue'
import { wfApi } from '../api'

/** 我的待办 bizId 集合（统一转字符串比较，规避 bigint/string 类型差） */
const pendingIds = ref(new Set())
/** bizId -> taskId（需要按 taskId 调接口时使用） */
const taskIdByBiz = ref(new Map())

let inflight = null
let loaded = false
let lastAt = 0
/** 缓存有效期：超过此时间后，下一次 canApprove 调用会异步刷新（审批完成后按钮自动消失） */
const TTL = 20000

/** 从待办行里取业务单据 ID（后端字段名为 billId） */
function bizIdOf(row) {
  if (!row) return null
  const v = row.id ?? row.bizId ?? row.billId ?? row.bill_id
  return v == null ? null : String(v)
}

async function load(force = false) {
  if (inflight) return inflight
  if (!force && loaded && Date.now() - lastAt < TTL) return pendingIds.value
  inflight = (async () => {
    try {
      // /tenant/wf/todo 无权限码，只返回当前登录用户自己的待办；silent 防止异常弹窗
      const list = (await wfApi.todo({ limit: 200 }, { silent: true })) || []
      const ids = new Set()
      const map = new Map()
      list.forEach((t) => {
        const bid = t.billId ?? t.bizId ?? t.bill_id
        if (bid == null) return
        ids.add(String(bid))
        if (t.taskId) map.set(String(bid), t.taskId)
      })
      pendingIds.value = ids
      taskIdByBiz.value = map
      loaded = true
    } catch (e) {
      // 降级：视为无待办，避免把不该出现的审批按钮显示出来
      pendingIds.value = new Set()
      taskIdByBiz.value = new Map()
    } finally {
      lastAt = Date.now()
      inflight = null
    }
    return pendingIds.value
  })()
  return inflight
}

/** 惰性触发（不阻塞渲染）：首次调用发起加载；超过 TTL 后异步刷新 */
function maybeLoad() {
  if (inflight) return
  if (loaded && Date.now() - lastAt < TTL) return
  load(true)
}

export function useApprovalScope() {
  /** 该行是否轮到我审批（我的待办里存在该单据） */
  function canApprove(row) {
    maybeLoad() // 惰性加载/定时刷新：数据到达后组件自动重渲染
    const id = bizIdOf(row)
    return id != null && pendingIds.value.has(id)
  }

  /** 该行的待办任务 ID（无则 null） */
  function taskIdOf(row) {
    const id = bizIdOf(row)
    return id == null ? null : (taskIdByBiz.value.get(id) || null)
  }

  return {
    canApprove,
    taskIdOf,
    /** 强制刷新（审批/撤回/提交后调用，保持按钮可见性同步） */
    refresh: () => load(true),
    load,
  }
}
