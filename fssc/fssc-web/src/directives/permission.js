/**
 * v-permission 权限指令 (R5-04)
 *
 * 用法：
 *   v-permission="'expense:create'"          // 单权限
 *   v-permission="['expense:create','expense:approve']"  // 任一满足
 *   v-permission.role="'admin'"               // 角色判断
 *
 * 权限来源：store.user.perms（数组或逗号串，统一由 utils/perm 归一化）或 store.user.roles（数组）
 * 无匹配权限时元素从 DOM 移除。
 */

import { store } from '@/api'
import { hasPerm } from '@/utils/perm'

function check(el, binding) {
  const user = store.user || {}
  const hasRoles = Array.isArray(user.roles) || Array.isArray(user.role)
  const userRoles = user.roles || (user.role ? [user.role] : [])

  const { value, modifiers } = binding
  if (!value) return

  const codes = Array.isArray(value) ? value : [value]
  let ok = false

  if (modifiers.role) {
    // 角色模式
    if (!hasRoles) {
      // roles 数据尚未就绪（登录响应不含 roles，仅 /auth/profile 注入）：
      // 不能静默移除元素，放行等待数据到位，避免误删 UI。
      ok = true
    } else {
      ok = codes.some((c) => userRoles.includes(c))
    }
  } else {
    // 权限码模式：判定唯一走 utils/perm.hasPerm()——perms 可能是逗号串，
    // 直接 includes 会退化成子串匹配（`a:b` 会命中 `a:b:c`），且通配 *:*:* 只在 perm.js 单点解释。
    ok = codes.some((c) => hasPerm(c))
  }

  // 角色名为 admin 的历史口径仍放行（通配码与 is_admin 已由 hasPerm 覆盖）
  if (!ok && userRoles.includes('admin')) ok = true

  if (!ok) {
    el.parentNode && el.parentNode.removeChild(el)
  }
}

export const permissionDirective = {
  mounted: check,
  updated(el, binding) {
    // updated 时如果之前已移除，需重新挂载检查
    // 但 DOM 已移除无法恢复，所以 mounted 时做最终判定即可
  },
}

export default permissionDirective
