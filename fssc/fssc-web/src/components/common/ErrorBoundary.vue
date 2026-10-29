<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

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
 * ErrorBoundary 错误边界组件 ()
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
  // -03：一次崩溃通常连着抛多条错误（首错 → 组件卸载期的二次错误）。
  // 原先每条都覆盖同一组 ref，留在「查看详情」里的往往是卸载期那条（实测 unregisterPane 读 undefined），
  // 真实首错被掩盖、用户与开发都无从定位。现只登记首条，后续错误带生命周期标签追加进详情。
  const cause = err?.cause ? '\n原因链：' + String(err?.cause?.message || err?.cause) : ''
  if (hasError.value) {
    errorStack.value += `\n—— 同期其它错误（环节：${info || '未知'}）：${String(err?.message || err)}${cause}`
    console.error('[ErrorBoundary·次生]', info, err)
    return false
  }
  hasError.value = true
  errorMessage.value = String(err?.message || err)
  errorStack.value = `环节：${info || '未知'}\n${String(err?.stack || '')}${cause}`
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
