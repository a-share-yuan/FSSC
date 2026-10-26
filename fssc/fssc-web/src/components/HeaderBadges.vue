<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="header-badges">
    <!-- 全局搜索触发器 -->
    <el-tooltip content="全局搜索 (Ctrl+K)" placement="bottom">
      <el-button link class="badge-btn" @click="$emit('search')">
        <el-icon :size="18"><Search /></el-icon>
      </el-button>
    </el-tooltip>

    <!-- 通知铃铛不在这里：NotificationCenter 自带弹层触发器，两处各挂一个铃铛会重复渲染（且角标规则不一） -->

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
import { Search, List, Star } from '@element-plus/icons-vue'
import { wfApi } from '../api'

defineEmits(['search', 'todo', 'favorites'])

const todoCount = ref(0)
let pollTimer = null

async function fetchCounts() {
  try {
    // silent：30s 轮询失败不走全局错误弹窗（避免 toast 风暴），由横幅与下次轮询默认处理
    const todo = await wfApi.todo(null, { silent: true })
    todoCount.value = Array.isArray(todo) ? todo.length : (todo?.total || 0)
  } catch { /* 后端不可达不阻塞 */ }
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
