<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="skeleton-list">
    <div v-for="i in rows" :key="i" class="skeleton-row">
      <div class="skel-cell" v-for="c in cols" :key="c" :style="{ width: cellWidth(c) }">
        <div class="skel-bar"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  rows: { type: Number, default: 5 },
  cols: { type: Number, default: 4 },
})

function cellWidth(c) {
  const widths = ['120px', '80px', '150px', '100px', '90px', '130px', '70px', '110px']
  return widths[c % widths.length]
}
</script>

<style scoped>
.skeleton-list { width: 100%; }
.skeleton-row {
  display: flex; gap: 12px; align-items: center;
  padding: 12px 0; border-bottom: 1px solid #ebeef5;
}
.skel-cell { flex-shrink: 0; }
.skel-bar {
  height: 14px; border-radius: 4px;
  background: linear-gradient(90deg, #f0f2f5 25%, #e6e8eb 37%, #f0f2f5 63%);
  background-size: 400% 100%;
  animation: skel-loading 1.4s ease infinite;
}
@keyframes skel-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}
</style>
