// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * useSubmitGuard — 表单提交前的必填校验。
 *
 * 存在的原因：含表单页面里真正调用过 formRef.validate() 的只是少数，
 * 另有两个反模式——① `el-form-item` 上裸写 `required` 但不给 `prop`，于是红星照显示、
 * 校验一条都不跑（Element Plus 只遍历有 prop 的项）；② 定义了 rules 对象却从不在提交时调用
 * 死规则。本 composable 把"提交前先校验、不过就中止"收敛成一处，避免每个页面各写一遍、
 * 各自漏写。
 *
 * 用法：
 *   const formRef = ref(null)
 *   const submit = useSubmitGuard(formRef)
 *   async function save() {
 *     await submit(async () => { ...真正发请求的部分... })
 *   }
 */
import { ElMessage } from 'element-plus'

/** 从 validate 的拒绝值里取第一个出错字段名（EP 传回 { prop: [Error,...] }，也兼容数组） */
function firstErrorField(invalid) {
  if (!invalid) return null
  if (Array.isArray(invalid)) {
    return invalid[0]?.prop || invalid[0] || null
  }
  if (typeof invalid === 'object') {
    const key = Object.keys(invalid)[0]
    return key || null
  }
  return null
}

/** 依次校验多个 el-form（一页多表单时用），全部通过返回 true；第一个不通过的表单负责提示并定位 */
export async function validateForms(formRefs) {
  for (const ref of formRefs) {
    const form = ref?.value
    if (!form) continue
    let invalid
    try {
      await form.validate()
    } catch (e) {
      invalid = e
    }
    if (!invalid) continue
    const prop = firstErrorField(invalid)
    const msg = (Array.isArray(invalid?.[prop]) && invalid[prop][0]?.message)
      || '请填写必填项后再提交'
    ElMessage.warning(msg)
    try {
      form.scrollToField?.(prop)
    } catch { /* 字段不在可视区时忽略 */ }
    return false
  }
  return true
}

export function useSubmitGuard(formRef) {
  /**
   * @param action 校验通过后要执行的异步逻辑（真正的 create/update 请求）
   * @returns 校验未通过时返回 undefined 且不发请求
   */
  async function submit(action) {
    // 没挂 ref 的表单无法前端校验——此时宁可放行交给后端必填守卫，也不要静默吞掉提交
    if (!(await validateForms([formRef]))) return undefined
    return action()
  }

  return { submit }
}
