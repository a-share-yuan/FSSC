<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="app-shell">
    <!-- 后端未连接：全局置顶横幅（登录页也可见，因为后端没起时登录同样调不通） -->
    <div v-if="!backend.online" class="backend-down">
      <span class="bd-dot" />
      <span class="bd-text">
        {{ backend.msg || `后端服务未连接：无法访问 ${BACKEND_TARGET}，请先启动 fssc-server` }}
      </span>
      <el-button size="small" type="warning" plain :loading="retrying" @click="retry">重试</el-button>
    </div>
    <!-- 移动报销端 P0-8「返回不丢数据」：
         只对 meta.keepAlive 的 /h5 路由族套 keep-alive，PC 路由走原来的裸 <component>，
         结构不变 ⇒ 不引入 PC 侧回归。key 用 fullPath：同一条地址返回即复用实例
         （列表滚动位置、已填表单、分页进度都在），换单据/换任务则另起一份。
         缓存上限 5 页，超出按 LRU 淘汰；离开 /h5 路由族（如退出登录）缓存随容器卸载销毁。 -->
    <router-view v-slot="{ Component, route }">
      <keep-alive v-if="route.meta?.keepAlive" :max="5">
        <component :is="Component" :key="route.fullPath" />
      </keep-alive>
      <component v-else :is="Component" />
    </router-view>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { BACKEND_TARGET, backend, probeBackend } from './api'

const retrying = ref(false)
let timer = null

async function retry() {
  retrying.value = true
  try {
    await probeBackend()
    if (!backend.online) ElMessage.warning('仍未连接上后端，请检查 fssc-server 是否已启动')
  } finally {
    retrying.value = false
  }
}

onMounted(() => {
  // 断线期间每 10s 自动重探，后端起来后无需手动刷新
  timer = setInterval(() => {
    if (!backend.online && !retrying.value) probeBackend()
  }, 10000)
})
onBeforeUnmount(() => clearInterval(timer))

watch(() => backend.online, (v, old) => {
  if (v && old === false) {
    ElMessage.success('后端已恢复连接，正在刷新页面…')
    setTimeout(() => location.reload(), 600)
  }
})
</script>

<style>
html, body, #app { height: 100%; margin: 0; }
body { font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; background: #f5f7fa; }
.app-shell { height: 100%; }

/* 后端未连接横幅：置顶、不随页面滚动消失 */
.backend-down {
  position: sticky;
  top: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  color: #fff;
  background: #d03050;
  font-size: 13px;
  line-height: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, .18);
}
.bd-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #fff; flex: none;
  animation: bd-blink 1.2s infinite;
}
.bd-text { flex: 1; word-break: break-all; }
@keyframes bd-blink { 0%, 100% { opacity: 1 } 50% { opacity: .25 } }

/* 窄屏：卡片内边距收紧、表格横向滚动（el-table 原生支持）、按钮/输入可点区域加高 */
@media (max-width: 767px) {
  .el-card__body { padding: 12px; }
  .el-table { min-width: 100%; }
  .el-form-item { margin-bottom: 14px; }
  .el-input, .el-select { max-width: 100%; }
  .el-drawer__body { padding: 0; }
}

/* 通用「行详情」弹窗：两列定义列表（showRowDetail / ClaimExpense 等共用） */
.row-detail-dialog .el-message-box__message {
  max-height: 60vh;
  overflow: auto;
  padding: 4px 2px;
}
.row-detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.row-detail-item {
  display: flex;
  line-height: 22px;
  font-size: 13px;
}
.row-detail-item:nth-child(odd) { background: #fafafa; }
.rd-key {
  flex: none;
  width: 130px;
  color: #909399;
  padding-right: 8px;
  text-align: right;
  word-break: break-all;
}
.rd-val {
  flex: 1;
  color: #303133;
  font-weight: 500;
  word-break: break-all;
}
</style>
