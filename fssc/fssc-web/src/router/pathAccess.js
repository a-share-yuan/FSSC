// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 页面可达性判据（路由守卫与页内入口共用同一把钥匙）。
 *
 * 为什么必须共用：入口卡/追溯跳转原先各按业务权限码过滤，而守卫另按
 * route meta.perms → ROUTE_PERMS.perms/feature → 菜单树叶子 四级判定，
 * 两套判据不一致时用户看到的是「卡片点得动、落地被弹回并提示无权限」。
 *
 * fail-open 约定：菜单树叶子集未加载（或该角色一棵树都没有）时不做第四层拦截，
 * 后端 API 权限校验仍在；与原先的守卫行为逐字一致。
 */
import { ref } from 'vue'
import { store, getMenuTree } from '../api'
import { hasAnyPerm, isSuperPerm } from '../utils/perm'
import { ROUTE_PERMS } from './routePerms'

/** 全员可达：工作台是拦截回落点、个人中心/导航地图是工作台入口、本人视角自助页后端按登录人过滤 */
export const OPEN_PATHS = new Set([
  '/workbench', '/profile', '/process-nav',
  '/requisition/todo', '/requisition/done', '/requisition/mine', '/flow-center/cc',
])

/** 菜单树叶子路径集：null＝尚未加载，Set＝已加载（只有叶子可作直达凭据，父级只作分组） */
export const menuLeafPaths = ref(null)
let cachedToken = ''
/**
 * 菜单树原文的同一份缓存。原先侧栏（Layout）、导航地图（ProcessNavMap）、守卫（ensureMenuPaths）
 * 各调各的 getMenuTree() ⇒ 一次进页面把全库最贵的那条查询（sys_role_menu 联表）
 * 打两到三遍。三处要的是同一棵树，合并成一枚在途请求。
 */
let rawTree = null
let inflightRaw = null

function leafSetOf(tree) {
  const s = new Set()
  const hasClickableChild = (n) => (n.children || []).some((c) => c.path || hasClickableChild(c))
  const walk = (ns) => (ns || []).forEach((n) => {
    if (n.path && !hasClickableChild(n)) s.add(String(n.path).startsWith('/') ? n.path : '/' + n.path)
    walk(n.children)
  })
  walk(tree)
  return s
}

/**
 * 只挂一个条目的分组，渲染时直接收敛成那一项——侧栏少点一层、面包屑同步少一级。
 *
 * 收敛条件（两条都要满足，取保守规则）：
 *   ① 子树里可点的页面只剩 1 个；
 *   ② 该分组下总共也只有这 1 个子节点——还挂着别的条目（哪怕是不可点的按钮项）就不收。
 * 为什么要有 ②：分组下页面多、某角色恰好只被授其中 1 页时，按 ① 会把一个"设计上就是多页
 * 的分组"整体收掉（实案＝100010/崔丽 在「对公支付」下只持 243 机票结算申请，但该组现有有
 * 3 个页面＋8 枚按钮项），分组名凭空消失会让人以为入口变了。② 保证只有"纯壳分组"
 * 设计上就只挂一页 才会被收敛。
 * 只动"纯分组"（自身无 path）；自身带 path 的 hub（/asset、/image 这类页面兼分组）保持原样，
 * 否则会把 MenuTree 给 hub 挂的「X总览」入口挤掉。
 * 收敛只替换分组节点、不删页面节点 ⇒ leafSetOf 的叶子路径集逐字不变，可达性判据不受影响。
 */
export function collapseSingleLeafGroups(nodes) {
  const countLeaves = (n) => (n.path ? 1 : 0) + (n.children || []).reduce((s, c) => s + countLeaves(c), 0)
  const firstLeaf = (n) => {
    if (n.path) return n
    for (const c of n.children || []) { const f = firstLeaf(c); if (f) return f }
    return null
  }
  const out = []
  for (const n of nodes || []) {
    const node = { ...n, children: collapseSingleLeafGroups(n.children) }
    if (!node.path && node.children.length === 1 && countLeaves(node) === 1) {
      const leaf = { ...firstLeaf(node) }
      leaf.icon = leaf.icon || node.icon
      out.push(leaf)
      continue
    }
    out.push(node)
  }
  return out
}

/** 侧边栏渲染用的菜单树加载后回灌此处，避免为判据再发一次请求 */
export function setMenuTree(tree) {
  cachedToken = store.token
  menuLeafPaths.value = leafSetOf(tree || [])
}

export function resetMenuTree() {
  cachedToken = ''
  menuLeafPaths.value = null
  rawTree = null
  inflightRaw = null
}

/**
 * 取菜单树原文（侧栏／导航地图／守卫共用）。换账号自动作废；
 * 失败回 null，调用方按各自的降级路径走（不阻塞页面）。
 */
export function ensureMenuTree() {
  if (cachedToken && cachedToken !== store.token) { rawTree = null; inflightRaw = null }
  if (rawTree) return Promise.resolve(rawTree)
  if (!inflightRaw) {
    inflightRaw = getMenuTree()
      .then((tree) => { rawTree = tree || []; cachedToken = store.token; return rawTree })
      .catch(() => { inflightRaw = null; return null })
  }
  return inflightRaw
}

export function ensureMenuPaths() {
  if (cachedToken !== store.token) resetMenuTree()
  if (menuLeafPaths.value) return Promise.resolve(menuLeafPaths.value)
  return ensureMenuTree().then((tree) => {
    if (tree) setMenuTree(tree)
    return menuLeafPaths.value
  })
}

/**
 * @returns '' 可达；'perm' 缺页面权限；'feature' 套餐未开通该功能
 */
export function pathDenyReason(path, meta) {
  if (!path) return ''
  const m = meta || {}
  if (m.terminal === 'H5' || path.startsWith('/h5')) return ''
  if (OPEN_PATHS.has(path)) return ''
  if (isSuperPerm()) return ''
  if (Array.isArray(m.perms) && m.perms.length) return hasAnyPerm(m.perms) ? '' : 'perm'
  const entry = ROUTE_PERMS[path]
  if (entry && Array.isArray(entry.perms) && entry.perms.length) return hasAnyPerm(entry.perms) ? '' : 'perm'
  if (entry && entry.feature) return (store.user?.features || []).includes(entry.feature) ? '' : 'feature'
  const leaves = menuLeafPaths.value
  if (leaves && leaves.size) return leaves.has(path) ? '' : 'perm'
  return ''
}
