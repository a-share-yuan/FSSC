/**
 * 权限判定公共工具（P0 权限治理沉淀）
 *
 * 背景：FSSC 的权限完全来自「角色 → sys_role_menu → sys_menu.perms」，
 * 登录时由 /api/auth/login 下发并挂到 store.user.perms。
 * 该字段既可能是数组，也可能是逗号分隔字符串，故在此统一归一化，
 * 避免每个页面各写一份 hasPerm（此前 Workbench.vue / Pay.vue 已各有一份）。
 *
 * 三条务必遵守的实现事实：
 *  1. **登录时会注入通配权限 `*:*:*`**：`AuthService:150` 对 `is_admin=1` 的用户
 *     直接把 perms 置为 `["*:*:*"]`（**不经过菜单表**，故静态审计脚本看不到）。
 *     后台 `PermissionAspect` 对通配放行，**前端必须做同样处理**，
 *     否则管理员会因"没有这个具体码"被判定无权、入口被误隐藏。
 *     （历史注记：本文件早期注释写"本项目不存在 *:*:*"，那是只查了 sys_menu 得出的错误结论。）
 *  2. `sys_menu.perms` 允许逗号多码（如 `asset:list,inventory:list`），
 *     因此必须切分后比对，不能整串等值匹配。
 *  3. 除通配外还需兼容 `isAdmin === 1` / `roles` 含 admin 的场景（见 directives/permission.js）。
 */
import { store } from '../api'

/** 把 store.user.perms 归一化为 Set，兼容数组 / 逗号串 / 空值 */
export function permSet() {
  const perms = store?.user?.perms
  if (!perms) return new Set()
  if (Array.isArray(perms)) return new Set(perms)
  return new Set(String(perms).split(',').map((s) => s.trim()).filter(Boolean))
}

/** 内部：判断"是否超级权限"（通配码或 is_admin=1） */
function superPerm(s) {
  if (s.has('*:*:*')) return true
  return store?.user?.isAdmin === 1
}

/** 是否超级权限（通配码 *:*:* 或 is_admin=1）；超管默认全放行 */
export function isSuperPerm() {
  return superPerm(permSet())
}

/** 是否持有指定权限码；code 为空视为"无需权限"，返回 true */
export function hasPerm(code) {
  if (!code) return true
  const s = permSet()
  return superPerm(s) || s.has(code)
}

/** 是否持有其中任意一个权限码（任一满足） */
export function hasAnyPerm(codes) {
  if (!codes || !codes.length) return true
  const s = permSet()
  return superPerm(s) || codes.some((c) => s.has(c))
}

/** 是否同时持有全部权限码 */
export function hasAllPerms(codes) {
  if (!codes || !codes.length) return true
  const s = permSet()
  return superPerm(s) || codes.every((c) => s.has(c))
}
