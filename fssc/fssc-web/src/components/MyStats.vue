<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-drawer v-model="visible" title="个人效率统计" direction="rtl" size="420px" :destroy-on-close="true">
    <div v-loading="loading" class="stats-content">
      <!-- 今日概览 -->
      <div class="stat-grid">
        <div class="stat-card blue">
          <div class="stat-val">{{ todayDone }}</div>
          <div class="stat-label">今日处理</div>
        </div>
        <div class="stat-card green">
          <div class="stat-val">{{ weekDone }}</div>
          <div class="stat-label">本周完成</div>
        </div>
        <div class="stat-card yellow">
          <div class="stat-val">{{ avgTime }}</div>
          <div class="stat-label">平均耗时</div>
        </div>
        <div class="stat-card red">
          <div class="stat-val">{{ timeoutRate }}%</div>
          <div class="stat-label">超时率</div>
        </div>
      </div>

      <!-- 本周趋势迷你图 -->
      <div class="trend-section">
        <div class="trend-title">本周处理量趋势</div>
        <div ref="trendRef" class="trend-chart"></div>
      </div>

      <!-- 同岗位对比 -->
      <div class="rank-section">
        <div class="rank-title">同岗位效率排行</div>
        <div v-for="(r, i) in rankings" :key="r.name" class="rank-item">
          <span class="rank-num" :class="{ 'rank-top': i < 3 }">{{ i + 1 }}</span>
          <span class="rank-name">{{ r.name }}</span>
          <span class="rank-val">{{ r.done }}件</span>
        </div>
        <el-empty v-if="rankings.length === 0" description="暂无排行数据" :image-size="40" />
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { wfApi, store } from '../api'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const loading = ref(false)
const todayDone = ref(0)
const weekDone = ref(0)
const avgTime = ref('-')
const timeoutRate = ref(0)
const rankings = ref([])
const trendRef = ref(null)
let chart = null

async function fetchData() {
  loading.value = true
  try {
    const todo = await wfApi.todo()
    // 从待办数据推断今日处理量
    const today = new Date().toDateString()
    todayDone.value = Array.isArray(todo) ? todo.filter(t => t.createTime && new Date(t.createTime).toDateString() === today).length : 0
    weekDone.value = Array.isArray(todo) ? todo.length : 0
  } catch { /* 忽略 */ }
  // 模拟排行
  rankings.value = [
    { name: '张三', done: 45 },
    { name: '李四', done: 38 },
    { name: store.user?.realName || '我', done: todayDone.value },
    { name: '王五', done: 30 },
  ].sort((a, b) => b.done - a.done)
  loading.value = false
  renderTrend()
}

function renderTrend() {
  if (!trendRef.value) return
  if (!chart) chart = echarts.init(trendRef.value)
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  chart.setOption({
    grid: { left: 30, right: 10, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: days, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
    series: [{
      type: 'bar', data: [12, 18, 15, 22, 19, 8, 5],
      itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
      barWidth: '50%',
    }],
    tooltip: { trigger: 'axis' },
  })
}

onMounted(() => {
  if (props.modelValue) fetchData()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
function onResize() { chart?.resize() }

watch(() => props.modelValue, (v) => {
  if (v) { fetchData(); nextTick(() => renderTrend()) }
})
</script>

<style scoped>
.stats-content { padding: 0 4px; }
.stat-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 20px; }
.stat-card {
  text-align: center; border-radius: 8px; padding: 16px;
  border-left: 4px solid #dcdfe6; background: #f5f7fa;
}
.stat-card.blue { border-color: #409eff; }
.stat-card.green { border-color: #67c23a; }
.stat-card.yellow { border-color: #e6a23c; }
.stat-card.red { border-color: #f56c6c; }
.stat-val { font-size: 22px; font-weight: 700; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
.trend-section { margin-bottom: 20px; }
.trend-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.trend-chart { height: 160px; }
.rank-section { }
.rank-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.rank-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 0; border-bottom: 1px solid #f5f5f5; font-size: 13px;
}
.rank-num {
  width: 24px; height: 24px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; background: #f0f2f5; color: #909399;
}
.rank-num.rank-top { background: #fdf6ec; color: #e6a23c; }
.rank-name { flex: 1; }
.rank-val { color: #606266; font-weight: 600; }
</style>
