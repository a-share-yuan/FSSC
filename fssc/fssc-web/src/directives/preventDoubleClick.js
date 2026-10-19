// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * P2-5 全局防重复提交指令 v-prevent-double-click
 *
 * 用法：<el-button v-prevent-double-click @click="submit">提交</el-button>
 *       <el-button v-prevent-double-click="3000" @click="submit">提交</el-button>
 *
 * 行为：点击后在指定窗口期（默认 1500ms）内禁用按钮，
 *       避免用户快速连点造成重复提交/重复审批。
 * 说明：这是一层兜底防抖，业务侧（如 doApprove 的 approving 锁）仍是第一道防线。
 */
export const preventDoubleClickDirective = {
  mounted(el, binding) {
    const disabledMs = Number(binding.value) > 0 ? Number(binding.value) : 1500
    let timer = null
    let lastDisabled = false

    el.addEventListener('click', () => {
      if (lastDisabled) return
      lastDisabled = true
      el.setAttribute('disabled', '')
      el.classList.add('is-disabled')
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        el.removeAttribute('disabled')
        el.classList.remove('is-disabled')
        lastDisabled = false
      }, disabledMs)
    })
  },
  unmounted(el) {
    // 组件销毁时移除禁用状态，避免内存/状态泄漏
    el.removeAttribute('disabled')
    el.classList.remove('is-disabled')
  },
}