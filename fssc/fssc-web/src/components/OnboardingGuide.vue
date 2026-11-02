<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <Teleport to="body">
    <div v-if="active" class="onboarding-overlay">
      <!-- 遮罩 + 高亮区域 -->
      <div class="onboarding-mask" @click="next" />
      <div
        v-if="currentStep"
        class="onboarding-highlight"
        :style="highlightStyle"
      >
        <div class="onboarding-pulse" />
      </div>
      <!-- 引导卡片 -->
      <div
        v-if="currentStep"
        class="onboarding-card"
        :style="cardStyle"
      >
        <div class="card-header">
          <span class="step-badge">{{ currentIdx + 1 }} / {{ steps.length }}</span>
          <el-button link size="small" @click="skip">{{ $t('common.skip') }}</el-button>
        </div>
        <div class="card-title">{{ currentStep.title }}</div>
        <div class="card-desc">{{ currentStep.desc }}</div>
        <div class="card-footer">
          <el-button v-if="currentIdx > 0" size="small" @click="prev">{{ $t('common.prev') }}</el-button>
          <el-button size="small" @click="skip">{{ $t('OnboardingGuide.main.Skip') }}</el-button>
          <el-button type="primary" size="small" @click="next">
            {{ currentIdx < steps.length - 1 ? '下一步' : '完成' }}
          </el-button>
        </div>
        <!-- 进度条 -->
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: ((currentIdx + 1) / steps.length * 100) + '%' }" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

const STORAGE_KEY = 'fssc_onboarding_done'

const steps = [
  { selector: '.logo', title: '欢迎使用 FSSC', desc: t('OnboardingGuide.main.Financial') },
  { selector: '.header-right', title: '全局搜索与通知', desc: t('OnboardingGuide.main.Click') },
  { selector: '.breadcrumb-bar', title: '面包屑导航', desc: t('OnboardingGuide.main.Always') },
  { selector: '.el-aside', title: '功能菜单', desc: t('OnboardingGuide.main.Left') },
  { selector: '.main', title: '工作区域', desc: t('OnboardingGuide.main.All') },
  { selector: '.header-right', title: '更多功能', desc: t('OnboardingGuide.main.User') },
]

const active = ref(false)
const currentIdx = ref(0)
const targetRect = ref(null)

const currentStep = computed(() => active.value ? steps[currentIdx.value] : null)

const highlightStyle = computed(() => {
  if (!targetRect.value) return { display: 'none' }
  const r = targetRect.value
  return {
    left: r.left - 4 + 'px',
    top: r.top - 4 + 'px',
    width: r.width + 8 + 'px',
    height: r.height + 8 + 'px',
  }
})

const cardStyle = computed(() => {
  if (!targetRect.value) return { left: '50%', top: '50%', transform: 'translate(-50%, -50%)' }
  const r = targetRect.value
  let top = r.bottom + 12
  if (top + 200 > window.innerHeight) top = r.top - 212
  return {
    left: Math.max(16, r.left) + 'px',
    top: top + 'px',
  }
})

function start() {
  if (localStorage.getItem(STORAGE_KEY) === 'true') return
  active.value = true
  currentIdx.value = 0
  highlightTarget()
}

function highlightTarget() {
  const step = steps[currentIdx.value]
  if (!step) return
  nextTick(() => {
    const el = document.querySelector(step.selector)
    if (el) {
      const rect = el.getBoundingClientRect()
      targetRect.value = { left: rect.left, top: rect.top, width: rect.width, height: rect.height }
    } else {
      targetRect.value = null
    }
  })
}

function next() {
  if (currentIdx.value < steps.length - 1) {
    currentIdx.value++
    highlightTarget()
  } else {
    finish()
  }
}

function prev() {
  if (currentIdx.value > 0) {
    currentIdx.value--
    highlightTarget()
  }
}

function skip() {
  finish()
}

function finish() {
  active.value = false
  localStorage.setItem(STORAGE_KEY, 'true')
}

function restart() {
  localStorage.removeItem(STORAGE_KEY)
  start()
}

defineExpose({ start, restart })

// 不自动启动：全屏遮罩会阻断用户操作，仅从 Layout 下拉菜单手动触发
</script>

<style scoped>
.onboarding-overlay { position: fixed; inset: 0; z-index: 10000; }
.onboarding-mask {
  position: absolute; inset: 0;
  background: rgba(0, 0, 0, 0.55);
}
.onboarding-highlight {
  position: absolute; border-radius: 6px;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.55);
  border: 2px solid #409eff; z-index: 1;
}
.onboarding-pulse {
  position: absolute; inset: -2px; border-radius: 6px;
  border: 2px solid #409eff; animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0% { opacity: 1; transform: scale(1); }
  100% { opacity: 0; transform: scale(1.05); }
}
.onboarding-card {
  position: absolute; width: 320px; z-index: 2;
  background: var(--app-card-bg, #fff); border-radius: 10px; padding: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.step-badge { font-size: 12px; color: #409eff; font-weight: 600; background: var(--app-hover, #ecf5ff); padding: 2px 8px; border-radius: 10px; }
.card-title { font-size: 16px; font-weight: 700; margin-bottom: 6px; }
.card-desc { font-size: 13px; color: var(--app-text-secondary, #606266); line-height: 1.6; margin-bottom: 14px; }
.card-footer { display: flex; justify-content: flex-end; gap: 8px; }
.progress-bar { height: 3px; background: var(--app-border, #ebeef5); border-radius: 2px; margin-top: 12px; overflow: hidden; }
.progress-fill { height: 100%; background: #409eff; transition: width 0.3s; border-radius: 2px; }
</style>
