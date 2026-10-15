<template>
  <slot v-if="!hasError" />
  <div v-else class="error-boundary">
    <el-result icon="error" :title="errorTitle" :sub-title="errorDesc">
      <template #extra>
        <el-button type="primary" @click="handleRetry">重试</el-button>
        <el-button @click="handleReset">重置页面</el-button>
        <el-button text @click="showDetail = !showDetail">
          {{ showDetail ? '隐藏详情' : '查看详情' }}
        </el-button>
      </template>
    </el-result>
    <el-collapse-transition>
      <div v-show="showDetail" class="error-detail">
        <pre>{{ errorMessage }}</pre>
        <pre v-if="errorStack" class="error-stack">{{ errorStack }}</pre>
      </div>
    </el-collapse-transition>
  </div>
</template>

<script setup>
/**
 * ErrorBoundary 错误边界组件 (R5-05)
 *
 * 用法：
 *   <ErrorBoundary>
 *     <ComplexComponent />
 *   </ErrorBoundary>
 *
 * 捕获子组件渲染错误，防止白屏，提供重试入口。
 */
import { onErrorCaptured, ref } from 'vue'

const hasError = ref(false)
const errorTitle = ref('页面出错了')
const errorDesc = ref('抱歉，页面渲染时发生错误，请重试或刷新页面')
const errorMessage = ref('')
const errorStack = ref('')
const showDetail = ref(false)

onErrorCaptured((err, instance, info) => {
  hasError.value = true
  errorMessage.value = String(err?.message || err)
  errorStack.value = err?.stack || ''
  // 上报错误到控制台（生产环境可替换为日志服务）
  console.error('[ErrorBoundary]', info, err)
  return false // 阻止错误继续向上传播
})

function handleRetry() {
  hasError.value = false
  errorMessage.value = ''
  errorStack.value = ''
  showDetail.value = false
}

function handleReset() {
  hasError.value = false
  errorMessage.value = ''
  errorStack.value = ''
  showDetail.value = false
  location.reload()
}
</script>

<style scoped>
.error-boundary {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.error-detail {
  margin: 0 20px 20px;
  max-height: 300px;
  overflow: auto;
}
.error-detail pre {
  margin: 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.error-stack {
  margin-top: 8px;
  color: #909399;
}
</style>
