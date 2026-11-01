<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <Teleport to="body">
    <div v-if="active" class="coachmark-overlay">
      <div class="coachmark-mask" @click="dismiss" />
      <!-- 目标元素高亮 -->
      <div class="coachmark-target" :style="targetStyle">
        <div class="coachmark-pulse-ring" />
      </div>
      <!-- 气泡提示 -->
      <div class="coachmark-bubble" :style="bubbleStyle">
        <div class="bubble-arrow" :style="arrowStyle" />
        <div class="bubble-content">
          <div class="bubble-title">
            <el-icon v-if="icon"><component :is="icon" /></el-icon>
            <span>{{ title }}</span>
          </div>
          <div class="bubble-text">{{ text }}</div>
          <div class="bubble-actions" v-if="showActions">
            <el-button v-if="hasPrev" size="small" @click="$emit('prev')">上一步</el-button>
            <el-button size="small" @click="dismiss">知道了</el-button>
            <el-button v-if="hasNext" type="primary" size="small" @click="$emit('next')">下一步</el-button>
          </div>
          <div v-else class="bubble-ok">
            <el-button type="primary" size="small" @click="dismiss">知道了</el-button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'

const props = defineProps({
  selector: { type: String, default: '' },
  title: { type: String, default: '提示' },
  text: { type: String, default: '' },
  position: { type: String, default: 'bottom' }, // top/bottom/left/right
  showActions: { type: Boolean, default: false },
  hasPrev: { type: Boolean, default: false },
  hasNext: { type: Boolean, default: false },
  icon: { type: Object, default: null },
  guideKey: { type: String, default: '' }, // 传 localStorage key，点"知道了"后持久化，不再每次弹出
})

const emit = defineEmits(['next', 'prev', 'dismiss'])
const active = ref(false)
const rect = ref(null)

const targetStyle = computed(() => {
  if (!rect.value) return { display: 'none' }
  const r = rect.value
  return {
    left: r.left - 6 + 'px',
    top: r.top - 6 + 'px',
    width: r.width + 12 + 'px',
    height: r.height + 12 + 'px',
  }
})

const bubbleStyle = computed(() => {
  if (!rect.value) return { left: '50%', top: '50%', transform: 'translate(-50%, -50%)' }
  const r = rect.value
  const w = 280, h = 120
  let left, top
  if (props.position === 'bottom') {
    left = Math.max(8, r.left)
    top = r.bottom + 12
    if (top + h > window.innerHeight) top = r.top - h - 12
  } else if (props.position === 'top') {
    left = Math.max(8, r.left)
    top = r.top - h - 12
    if (top < 8) top = r.bottom + 12
  } else if (props.position === 'left') {
    left = r.left - w - 12
    top = Math.max(8, r.top)
    if (left < 8) left = r.right + 12
  } else { // right
    left = r.right + 12
    top = Math.max(8, r.top)
    if (left + w > window.innerWidth) left = r.left - w - 12
  }
  return { left: left + 'px', top: top + 'px' }
})

const arrowStyle = computed(() => {
  if (!rect.value) return {}
  const r = rect.value
  if (props.position === 'bottom') return { left: '20px', top: '-6px', borderBottom: '6px solid #fff' }
  if (props.position === 'top') return { left: '20px', bottom: '-6px', borderTop: '6px solid #fff' }
  return { left: '-6px', top: '20px', borderRight: '6px solid #fff' }
})

function show() {
  // 已点过"知道了"则不再弹出
  if (props.guideKey) {
    try { if (localStorage.getItem(props.guideKey)) return } catch {}
  }
  active.value = true
  nextTick(() => {
    const el = document.querySelector(props.selector)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      setTimeout(() => {
        const r = el.getBoundingClientRect()
        rect.value = { left: r.left, top: r.top, width: r.width, height: r.height, right: r.right, bottom: r.bottom }
      }, 300)
    } else {
      // selector 找不到元素时自动关闭，避免遮罩卡住页面
      rect.value = null
      active.value = false
    }
  })
}

function dismiss() {
  // 持久化"已看"，下次进入页面不再自动弹出
  if (props.guideKey) {
    try { localStorage.setItem(props.guideKey, '1') } catch {}
  }
  active.value = false
  rect.value = null
  emit('dismiss')
}

defineExpose({ show, dismiss })

onMounted(() => {
  // 监听滚动更新位置
  window.addEventListener('scroll', updateRect, true)
  window.addEventListener('resize', updateRect)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateRect, true)
  window.removeEventListener('resize', updateRect)
})

function updateRect() {
  if (!active.value || !props.selector) return
  const el = document.querySelector(props.selector)
  if (el) {
    const r = el.getBoundingClientRect()
    rect.value = { left: r.left, top: r.top, width: r.width, height: r.height, right: r.right, bottom: r.bottom }
  }
}
</script>

<style scoped>
.coachmark-overlay { position: fixed; inset: 0; z-index: 10001; }
.coachmark-mask { position: absolute; inset: 0; background: rgba(0,0,0,0.45); }
.coachmark-target {
  position: absolute; border-radius: 6px;
  box-shadow: 0 0 0 9999px rgba(0,0,0,0.45);
  border: 2px solid #409eff; z-index: 1;
}
.coachmark-pulse-ring {
  position: absolute; inset: -4px; border-radius: 8px;
  border: 2px solid #67c23a; animation: coach-pulse 2s infinite;
}
@keyframes coach-pulse {
  0% { opacity: 0.8; transform: scale(1); }
  100% { opacity: 0; transform: scale(1.08); }
}
.coachmark-bubble {
  position: absolute; width: 280px; z-index: 2;
  background: var(--app-card-bg, #fff); border-radius: 8px; padding: 14px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
}
.bubble-arrow { position: absolute; width: 0; height: 0; }
.bubble-title { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 600; color: var(--app-text, #303133); margin-bottom: 6px; }
.bubble-text { font-size: 13px; color: var(--app-text-secondary, #606266); line-height: 1.6; margin-bottom: 12px; }
.bubble-actions { display: flex; justify-content: flex-end; gap: 8px; }
.bubble-ok { text-align: right; }
</style>
