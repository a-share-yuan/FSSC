<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="watermark-layer" v-if="enabled">
    <svg class="watermark-svg" xmlns="http://www.w3.org/2000/svg" width="100%" height="100%">
      <defs>
        <pattern id="wmark" x="0" y="0" width="300" height="200" patternUnits="userSpaceOnUse" patternTransform="rotate(-25)">
          <text x="50" y="100" :fill="color" :font-size="fontSize" font-weight="600" opacity="0.06">
            {{ text }}
          </text>
          <text x="50" y="140" :fill="color" :font-size="subFontSize" opacity="0.04">
            {{ subText }}
          </text>
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#wmark)" />
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { store } from '../api'

const props = defineProps({
  enabled: { type: Boolean, default: true },
})

const text = computed(() => store.user?.realName || store.user?.username || 'FSSC')
const subText = computed(() => {
  const now = new Date()
  return `${now.toLocaleDateString('zh-CN')} ${now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
})
const color = '#409eff'
const fontSize = '16'
const subFontSize = '11'
</script>

<style scoped>
.watermark-layer {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
  z-index: 9998; pointer-events: none; overflow: hidden;
}
.watermark-svg { position: absolute; top: 0; left: 0; }
</style>
