<template>
  <div class="skeleton-detail">
    <div class="skel-header">
      <div class="skel-bar" style="width: 200px; height: 20px;"></div>
      <div class="skel-bar" style="width: 80px; height: 24px; margin-left: auto;"></div>
    </div>
    <div class="skel-form">
      <div v-for="i in rows" :key="i" class="skel-form-row">
        <div class="skel-bar" style="width: 80px; height: 14px;"></div>
        <div class="skel-bar" style="width: 220px; height: 14px; margin-left: 16px;"></div>
      </div>
    </div>
    <div class="skel-cards">
      <div v-for="i in 3" :key="i" class="skel-card">
        <div class="skel-bar" style="width: 120px; height: 16px; margin-bottom: 12px;"></div>
        <div v-for="j in 3" :key="j" class="skel-bar" style="width: 100%; height: 14px; margin-bottom: 8px;"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ rows: { type: Number, default: 4 } })
</script>

<style scoped>
.skeleton-detail { width: 100%; }
.skel-header {
  display: flex; align-items: center; margin-bottom: 20px;
}
.skel-form {
  display: flex; flex-direction: column; gap: 16px;
  margin-bottom: 24px;
}
.skel-form-row { display: flex; align-items: center; }
.skel-cards {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px;
}
.skel-card {
  background: #f5f7fa; border-radius: 8px; padding: 16px;
}
.skel-bar {
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f2f5 25%, #e6e8eb 37%, #f0f2f5 63%);
  background-size: 400% 100%;
  animation: skel-loading 1.4s ease infinite;
}
@keyframes skel-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}
</style>
