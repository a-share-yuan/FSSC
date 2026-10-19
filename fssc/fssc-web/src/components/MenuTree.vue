<template>
  <template v-for="n in nodes" :key="n.id">
    <!-- 目录 / 含可点击子级 → 折叠子菜单（支持多级嵌套） -->
    <el-sub-menu v-if="hasClickableChild(n)" :index="normPath(n.path) || String(n.id)">
      <template #title>
        <el-icon v-if="n.icon" class="menu-icon"><component :is="n.icon" /></el-icon>
        <span>{{ n.menuName }}</span>
      </template>
      <MenuTree :nodes="childNodes(n)" />
    </el-sub-menu>
    <!-- 叶子页面 → 直接菜单项 -->
    <el-menu-item v-else-if="n.path" :index="normPath(n.path)">
      <el-icon v-if="n.icon" class="menu-icon"><component :is="n.icon" /></el-icon>
      <span>{{ n.menuName }}</span>
    </el-menu-item>
  </template>
</template>

<script setup>
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
