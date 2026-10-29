<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="app-shell">
    <!-- 后端未连接：全局置顶横幅（登录页也可见，因为后端没起时登录同样调不通） -->
    <div v-if="!backend.online" class="backend-down">
      <span class="bd-dot" />
      <span class="bd-text">
        {{ backend.msg || '系统服务暂时无法连接，请稍后重试或联系管理员' }}
      </span>
      <el-button size="small" type="warning" plain :loading="retrying" @click="retry">重试</el-button>
    </div>
    <!-- 移动报销端 「返回不丢数据」：
         只对 meta.keepAlive 的 /h5 路由族套 keep-alive，PC 路由走原来的裸 <component>，
         结构不变 ⇒ 不引入 PC 侧回归。key 用 fullPath：同一条地址返回即复用实例
         列表滚动位置、已填表单、分页进度都在，换单据/换任务则另起一份。
         缓存上限 5 页，超出按 LRU 淘汰；离开 /h5 路由族（如退出登录）缓存随容器卸载销毁。 -->
    <!-- /glex/consolidate 与 /glex/fx-reval 这类页面一旦组件 setup 抛错就整页白屏
         （控制台只有 `Unhandled error during execution of setup function` ＋ `Cannot read properties of null
         (reading 'subTree')`，界面不要表格不要空态不要提示），用户无从得知原因。ErrorBoundary若只在
         main.js 里全局注册、却没有任何一处使用，等于没接 ⇒ 这里把它真正套到路由出口上：页面组件 setup/render 抛错时
         显示可读兜底＋重试入口，而不是留一片空白。
         key 用 route.fullPath —— 边界自身不监听路由，换页必须重新挂载一份干净的边界，
         否则上一页捕获过的错误会把后续所有页面都挡在兜底页上。
         边界必须套在 keep-alive 内层：套在外层且带 key 会让 keep-alive 实例随每次换页重建，
         缓存全丢 ⇒ 移动报销端 「返回不丢数据」直接退化。 -->
    <router-view v-slot="{ Component, route }">
      <keep-alive v-if="route.meta?.keepAlive" :max="5">
        <ErrorBoundary :key="route.fullPath">
          <component :is="Component" :key="route.fullPath" />
        </ErrorBoundary>
      </keep-alive>
      <ErrorBoundary v-else :key="route.fullPath">
        <component :is="Component" />
      </ErrorBoundary>
    </router-view>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { backend, probeBackend, store } from './api'
import { preloadBuiltinDicts, PRELOAD_DICT_TYPES } from './composables/useDict.js'
// enums.js 的 66 个展示字典（enum_*）也要预热：
// enumLabel() 同为纯同步函数，被各页直接调用，没有生命周期可挂。
import { ENUM_DICT_TYPES } from './constants/enums.js'

const retrying = ref(false)
let timer = null

/**
 * 全局预热内置字典。
 *
 * 为什么挂在这里：billStatus.js 的 statusLabel()/statusType() 是纯同步函数，
 * 被 55 个业务文件直接调用，没有组件生命周期可挂 onMounted —— 逐文件改造既不现实也无必要，
 * 统一在根组件预热一次，后续所有同步调用共享内存缓存。
 *
 * 三点防护：
 *   ① 未登录不请求（避免每次开登录页都吃一次 401）；
 *   ② 后端离线不请求（后端都没起来，请求必然失败）；
 *   ③ 失败完全静默 —— useDict 内部 fail-soft，界面回落本地常量，与原先逐字一致。
 */
async function warmDicts() {
  if (!store.token || !backend.online) return
  try {
    // 走批量通道：67 个字典合成 1 次往返（后端未部署批量端点时 useDict 自动回退逐个拉取）
    await preloadBuiltinDicts([...PRELOAD_DICT_TYPES, ...ENUM_DICT_TYPES])
  } catch { /* 预热失败不影响任何业务：纯增量的文案增强 */ }
}

async function retry() {
  retrying.value = true
  try {
    await probeBackend()
    if (!backend.online) ElMessage.warning('系统服务暂时无法连接，请稍后重试或联系管理员')
  } finally {
    retrying.value = false
  }
}

onMounted(() => {
  warmDicts()
  // 断线期间每 10s 自动重探，后端起来后无需手动刷新
  timer = setInterval(() => {
    if (!backend.online && !retrying.value) probeBackend()
  }, 10000)
})
onBeforeUnmount(() => clearInterval(timer))

watch(() => backend.online, (v, old) => {
  if (v && old === false) {
    ElMessage.success('服务已恢复连接，正在刷新页面…')
    setTimeout(() => location.reload(), 600)
  }
})

/**
 * 登录成功后补一次预热。
 *
 * 根因：warmDicts() 原本只在 onMounted 调一次，而首次进入时用户尚未登录，
 * `!store.token` 直接 return；登录动作只是把 token 写进内存，不触发任何生命周期
 * ⇒ 第 3/4 批的 67 个字典在本次会话内永远不会被预热，statusLabel/enumLabel
 * 全程走本地常量，字典化等于没生效（刷新页面才碰巧生效，因为刷新时 token 已从本地恢复）。
 *
 * 修法：监听 token 由空变非空，触发一次预热（已加载的类型在 useDict 内短路，不会重复请求）。
 */
// 不能用 watch(() => store.token)：store 是普通对象（getter/setter 直读 localStorage），
//    不是 reactive，Vue 追踪不到依赖 ⇒ 监听永不触发。故改由 api.js 的 token setter 派发
//    'fssc:auth' 事件（见 api.js:43 附近）来通知。
function onAuth(e) {
  if (e && e.detail && e.detail.token) warmDicts()
}
window.addEventListener('fssc:auth', onAuth)
onBeforeUnmount(() => window.removeEventListener('fssc:auth', onAuth))
</script>

<style>
html, body, #app { height: 100%; margin: 0; }
body { font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; background: #f5f7fa; }
.app-shell { height: 100%; }

/* 原生 file 控件兜底隐藏：el-upload 依赖 .el-upload__input{display:none}，一旦全局样式
   缺失/延迟就会露出「选择文件 未选择任何文件」并压住侧边菜单（报销页实况截图）。
   这里对所有 el-upload 生效，组件内 UploadButton 另有一层定向加固。 */
.el-upload__input {
  display: none !important;
  position: absolute !important;
  left: -9999px !important;
  width: 1px !important;
  height: 1px !important;
  opacity: 0 !important;
  overflow: hidden !important;
}

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
  width: 140px;
  color: #909399;
  padding-right: 8px;
  text-align: right;
  /* break-all 会把 "Allowance Amount" 切成 "Allowance Amoun / t"；中文标签过长时按词换行即可 */
  word-break: normal;
  overflow-wrap: break-word;
}
.rd-val {
  flex: 1;
  color: #303133;
  font-weight: 500;
  word-break: break-all;
}

/* 描述列表是自动布局表格：值里出现不可断的长串（账号/单号/流水号）时，
   浏览器会把标签列压到最窄（单字竖排）。标签不换行、值允许断行，列宽才稳定。 */
.el-descriptions__label { white-space: nowrap; }
.el-descriptions__content { overflow-wrap: anywhere; word-break: break-word; }
</style>
