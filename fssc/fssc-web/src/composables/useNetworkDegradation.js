// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * useNetworkDegradation - 弱网降级策略 composable (R5-16)
 *
 * 功能：
 * 1. 监测网络质量（通过 navigator.connection + 自定义延迟探测）
 * 2. 根据 effectiveType 自动降级：4g→正常，3g/2g→降级模式
 * 3. 降级模式：关闭骨架屏动画、减少图片加载、禁用轮询、增大缓存
 * 4. 提供全局降级状态供组件读取
 *
 * 用法：
 *   import { useNetworkDegradation } from '@/composables/useNetworkDegradation'
 *   const { isSlowNetwork, degradationLevel, enableDegradation } = useNetworkDegradation()
 *   // 在组件中：v-if="isSlowNetwork" 显示简化版
 */

import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { backend } from '@/api'

const effectiveType = ref('4g')
const downlink = ref(10) // Mbps
const rtt = ref(50) // ms
const isSlowNetwork = ref(false)
const degradationLevel = ref('none') // none / mild / severe
let probeTimer = null

// 降级阈值
const THRESHOLDS = {
  mild: { rtt: 300, downlink: 2 },     // RTT > 300ms 或 下行 < 2Mbps → 轻度降级
  severe: { rtt: 800, downlink: 0.5 },   // RTT > 800ms 或 下行 < 0.5Mbps → 重度降级
}

function updateFromConnectionAPI() {
  if (navigator.connection) {
    const conn = navigator.connection
    effectiveType.value = conn.effectiveType || '4g'
    downlink.value = conn.downlink || 10
    rtt.value = conn.rtt || 50
    conn.addEventListener('change', evaluateNetwork)
  }
}

function evaluateNetwork() {
  // 基于 effectiveType
  if (effectiveType.value === '2g' || effectiveType.value === 'slow-2g') {
    isSlowNetwork.value = true
    degradationLevel.value = 'severe'
  } else if (effectiveType.value === '3g') {
    isSlowNetwork.value = true
    degradationLevel.value = 'mild'
  } else if (rtt.value > THRESHOLDS.severe.rtt || downlink.value < THRESHOLDS.severe.downlink) {
    isSlowNetwork.value = true
    degradationLevel.value = 'severe'
  } else if (rtt.value > THRESHOLDS.mild.rtt || downlink.value < THRESHOLDS.mild.downlink) {
    isSlowNetwork.value = true
    degradationLevel.value = 'mild'
  } else {
    isSlowNetwork.value = false
    degradationLevel.value = 'none'
  }

  // 后端离线也算 severe
  if (!backend.online) {
    isSlowNetwork.value = true
    degradationLevel.value = 'severe'
  }
}

// 自定义延迟探测：每 30 秒测一次到 /api 的延迟
function startProbing() {
  if (probeTimer) return
  probeTimer = setInterval(async () => {
    const start = Date.now()
    try {
      // 用 fetch 而非 axios，避免触发拦截器
      await fetch('/api/auth/profile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: 'null',
        signal: AbortSignal.timeout(5000),
      })
      rtt.value = Date.now() - start
      evaluateNetwork()
    } catch {
      rtt.value = 9999
      evaluateNetwork()
    }
  }, 30000)
}

function stopProbing() {
  if (probeTimer) {
    clearInterval(probeTimer)
    probeTimer = null
  }
}

// 降级策略配置
const degradationConfig = computed(() => {
  const level = degradationLevel.value
  return {
    skipAnimations: level !== 'none',
    skipImages: level === 'severe',
    skipPolling: level === 'severe',
    cacheStrategy: level === 'severe' ? 'force-cache' : level === 'mild' ? 'cache-first' : 'network-first',
    pageSize: level === 'severe' ? 10 : level === 'mild' ? 20 : 50,
    debounceMs: level === 'severe' ? 1000 : level === 'mild' ? 500 : 200,
    showSkeleton: level !== 'severe', // 弱网时关闭骨架屏动画减少渲染负担
  }
})

export function useNetworkDegradation() {
  onMounted(() => {
    // localhost / 127.0.0.1 本机开发环境跳过弱网检测，避免 navigator.connection 误报
    const isLocalhost = location.hostname === 'localhost' || location.hostname === '127.0.0.1'
    if (isLocalhost) {
      isSlowNetwork.value = false
      degradationLevel.value = 'none'
      return
    }
    updateFromConnectionAPI()
    evaluateNetwork()
    startProbing()
  })

  onBeforeUnmount(() => {
    if (navigator.connection) {
      navigator.connection.removeEventListener('change', evaluateNetwork)
    }
    stopProbing()
  })

  return {
    effectiveType,
    downlink,
    rtt,
    isSlowNetwork,
    degradationLevel,
    degradationConfig,
    evaluateNetwork,
  }
}
