<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div ref="scrollRef" class="virtual-scroll-container" :style="{ height: typeof height === 'number' ? height + 'px' : height }" @scroll="onScroll">
    <div class="virtual-scroll-spacer" :style="{ height: totalHeight + 'px', position: 'relative' }">
      <div
        v-for="item in visibleItems"
        :key="itemKey ? item[itemKey] : startIndex + visibleItems.indexOf(item)"
        class="virtual-scroll-item"
        :style="{ position: 'absolute', top: (startIndex + visibleItems.indexOf(item)) * itemHeight + 'px', width: '100%', height: itemHeight + 'px' }"
      >
        <slot :item="item" :index="startIndex + visibleItems.indexOf(item)" />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * VirtualScroll 轻量虚拟滚动组件 (R5-12)
 *
 * 不依赖第三方库，基于固定行高实现虚拟滚动。
 * 适用于大列表（1000+行）场景，只渲染可视区域内的行。
 *
 * 用法：
 *   <VirtualScroll :items="data" :item-height="48" :height="500" item-key="id">
 *     <template #default="{ item, index }">
 *       <div>{{ item.name }}</div>
 *     </template>
 *   </VirtualScroll>
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  itemHeight: { type: Number, default: 48 },
  height: { type: [Number, String], default: 400 },
  itemKey: { type: String, default: '' },
  overscan: { type: Number, default: 5 }, // 可视区域上下额外渲染的行数
})

const scrollRef = ref(null)
const scrollTop = ref(0)

const totalHeight = computed(() => props.items.length * props.itemHeight)

const containerHeight = computed(() => {
  return typeof props.height === 'number' ? props.height : 400
})

const visibleCount = computed(() => {
  return Math.ceil(containerHeight.value / props.itemHeight) + props.overscan * 2
})

const startIndex = computed(() => {
  const start = Math.floor(scrollTop.value / props.itemHeight) - props.overscan
  return Math.max(0, start)
})

const endIndex = computed(() => {
  return Math.min(props.items.length, startIndex.value + visibleCount.value)
})

const visibleItems = computed(() => {
  return props.items.slice(startIndex.value, endIndex.value)
})

function onScroll() {
  if (scrollRef.value) {
    scrollTop.value = scrollRef.value.scrollTop
  }
}

// 滚动到指定索引
function scrollToIndex(index) {
  if (scrollRef.value) {
    scrollRef.value.scrollTop = index * props.itemHeight
  }
}

onMounted(() => {
  if (scrollRef.value) {
    scrollRef.value.addEventListener('scroll', onScroll, { passive: true })
  }
})

onBeforeUnmount(() => {
  if (scrollRef.value) {
    scrollRef.value.removeEventListener('scroll', onScroll)
  }
})

defineExpose({ scrollToIndex })
</script>

<style scoped>
.virtual-scroll-container {
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;
  -webkit-overflow-scrolling: touch;
}
.virtual-scroll-spacer {
  width: 100%;
}
.virtual-scroll-item {
  box-sizing: border-box;
}
</style>
