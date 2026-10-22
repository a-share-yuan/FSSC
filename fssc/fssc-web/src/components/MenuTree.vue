<template>
  <template v-for="n in nodes" :key="n.id">
    <!-- 目录 / 含可点击子级 → 折叠子菜单（支持多级嵌套） -->
    <el-sub-menu v-if="hasClickableChild(n)" :index="n.path ? 'dir-' + n.id : String(n.id)">
      <template #title>
        <el-icon v-if="n.icon" class="menu-icon"><component :is="n.icon" /></el-icon>
        <span>{{ n.menuName }}</span>
      </template>
      <!-- 该节点自身也是一个页面（带 path）时，把主页列为首项：
           否则「分组标题点不动 + 子项里又没有主页」会让这个页面在侧栏彻底没有入口
           （资产与存货 /asset、应收管理 /receivable、影像管理 /image 三域均如此，只能从工作台卡片进）。 -->
      <el-menu-item v-if="n.path && hubOpenable(n)" :index="itemIndex(n.path)">
        <el-icon v-if="n.icon" class="menu-icon"><component :is="n.icon" /></el-icon>
        <span>{{ n.menuName }}总览</span>
      </el-menu-item>
      <MenuTree :nodes="childNodes(n)" />
    </el-sub-menu>
    <!-- 叶子页面 → 直接菜单项 -->
    <el-menu-item v-else-if="n.path" :index="itemIndex(n.path)">
      <el-icon v-if="n.icon" class="menu-icon"><component :is="n.icon" /></el-icon>
      <span>{{ n.menuName }}</span>
    </el-menu-item>
  </template>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { pathDenyReason } from '../router/pathAccess'
/**
 * 递归菜单树组件：支持任意层级（顶级 → 目录 → 页面）。
 * - 目录（menu_type=M，无 path）：渲染为 el-sub-menu，仅作分组
 * - 页面（有 path）：渲染为 el-menu-item，点击路由跳转
 * - 顶级节点可通过 _icon 字段指定 Element Plus 图标名
 * 依赖外层 el-menu 的 router 模式与配色继承。
 */
defineOptions({ name: 'MenuTree' })
defineProps({
  nodes: { type: Array, default: () => [] },
})

const router = useRouter()

/** 取菜单路径的落地路径：菜单表存的是重定向前路径（如 /asset → /asset/asset）时，
 *  index 必须用落地路径，否则 default-active（$route.path）对不上、侧栏无高亮。
 *  注：router.resolve() 不跟随 redirect，这里自己读命中记录上的 redirect 字段。 */
function finalPath(p) {
  const abs = normPath(p)
  try {
    const r = router.resolve(abs)
    const rec = r.matched && r.matched[r.matched.length - 1]
    const rd = rec && rec.redirect
    if (typeof rd === 'string') return normPath(rd)
    return r.path || abs
  } catch { return abs }
}

function itemIndex(p) { return finalPath(p) }

/**
 * hub 自身页面要不要给「总览」项：
 *  ① 子项里已有指向同一落地页面的（资产工作台 /asset/asset 即 hub /asset 的落地页；
 *     影像工作台 3913 的 path 也写成 /image）→ 不重复挂；
 *  ② 本账号打不开 hub 页自身（只授了子页权限码）→ 不挂，免得造出点了被弹回的假入口。
 */
function hubOpenable(n) {
  const idx = finalPath(n.path)
  if ((n.children || []).some((c) => c.path && finalPath(c.path) === idx)) return false
  try { return !pathDenyReason(idx, router.resolve(idx).meta || {}) } catch { return true }
}

/** 菜单 path 统一为绝对路径：后端数据缺前导斜杠时, 相对路径会被 router 拼接到当前路由下, 导致整页白屏 */
function normPath(p) {
  return p && !p.startsWith('/') ? '/' + p : p
}

/** 子树中是否存在可点击节点（有 path 的页面） */
function hasClickableChild(n) {
  return (n.children || []).some((c) => c.path || hasClickableChild(c))
}

/** 只保留可渲染的子节点（页面 或 含可点击后代的目录），过滤空目录/按钮 */
function childNodes(n) {
  return (n.children || []).filter((c) => c.path || hasClickableChild(c))
}
</script>

<style scoped>
.menu-icon {
  margin-right: 6px;
  font-size: 16px;
  vertical-align: middle;
}
</style>
