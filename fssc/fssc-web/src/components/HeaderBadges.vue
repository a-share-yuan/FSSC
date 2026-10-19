<template>
  <div class="header-badges">
    <!-- 全局搜索触发器 -->
    <el-tooltip content="全局搜索 (Ctrl+K)" placement="bottom">
      <el-button link class="badge-btn" @click="$emit('search')">
        <el-icon :size="18"><Search /></el-icon>
      </el-button>
    </el-tooltip>

    <!-- 通知中心 -->
    <el-tooltip content="通知中心" placement="bottom">
      <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0" class="badge-wrap">
        <el-button link class="badge-btn" @click="$emit('notify')">
          <el-icon :size="18"><Bell /></el-icon>
        </el-button>
      </el-badge>
    </el-tooltip>

    <!-- 待办角标 -->
    <el-tooltip content="待办任务" placement="bottom">
      <el-badge :value="todoCount" :max="99" :hidden="todoCount === 0" class="badge-wrap">
        <el-button link class="badge-btn" @click="$emit('todo')">
          <el-icon :size="18"><List /></el-icon>
        </el-button>
      </el-badge>
    </el-tooltip>

    <!-- 收藏夹 -->
    <el-tooltip content="收藏夹" placement="bottom">
      <el-button link class="badge-btn" @click="$emit('favorites')">
        <el-icon :size="18"><Star /></el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { Search, Bell, List, Star } from '@element-plus/icons-vue'
import { wfApi, messageApi, store } from '../api'

defineEmits(['search', 'notify', 'todo', 'favorites'])

const todoCount = ref(0)
const unreadCount = ref(0)
let pollTimer = null

async function fetchCounts() {
  try {
    // silent：30s 轮询失败不走全局错误弹窗（避免 toast 风暴），由横幅与下次轮询兜底
    const todo = await wfApi.todo(null, { silent: true })
    todoCount.value = Array.isArray(todo) ? todo.length : (todo?.total || 0)
  } catch { /* 后端不可达不阻塞 */ }
  try {
    // 本人未读视角：TenantMessageController#unreadCount 对「查自己」免权限码（D-DEC-149 收口），
    // 仅「查他人/管理视图」才需 system:message:list。原按旧口径以 hasPerm('system:message:list') 门控，
    // 会把未持该码的业务角色（如 COMMON_EMP 撤权后）的铃铛未读数一并关掉 → 现只要已登录有 uid 即轮询本人未读数。
    const uid = store.user?.id // R5-D-302: uid 此前未声明，ReferenceError 被下面的 catch 空吞⇒ 该腿从未发出请求
    if (uid) unreadCount.value = await messageApi.unreadCount(uid, { silent: true }) || 0
  } catch { /* 忽略 */ }
}

onMounted(() => {
  fetchCounts()
  pollTimer = setInterval(fetchCounts, 30000)
})
onBeforeUnmount(() => clearInterval(pollTimer))

defineExpose({ refresh: fetchCounts })
</script>

<style scoped>
.header-badges {
  display: flex; align-items: center; gap: 4px;
}
.badge-btn {
  padding: 6px 8px; color: #606266;
}
.badge-btn:hover { color: #409eff; }
.badge-wrap { margin: 0 2px; }
</style>
