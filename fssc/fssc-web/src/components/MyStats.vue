<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-drawer v-model="visible" title="个人效率统计" direction="rtl" size="420px" :destroy-on-close="true" @opened="renderTrend">
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
          <div class="stat-val">{{ timeoutRate === null ? '—' : timeoutRate + '%' }}</div>
          <div class="stat-label">超时率</div>
        </div>
      </div>

      <!-- 近 7 天办理量 -->
      <div class="trend-section">
        <div class="trend-title">近 7 天处理量</div>
        <div v-if="trendData.length" ref="trendRef" class="trend-chart"></div>
        <el-empty v-else :description="historyHint || '近 7 天暂无处理记录'" :image-size="40" />
      </div>

      <!-- 同岗位对比 -->
      <div class="rank-section">
        <div class="rank-title">近 7 天办结排行</div>
        <div v-for="(r, i) in rankings" :key="r.name" class="rank-item">
          <span class="rank-num" :class="{ 'rank-top': i < 3 }">{{ i + 1 }}</span>
          <span class="rank-name">{{ r.name }}</span>
          <span class="rank-val">{{ r.done }}件</span>
        </div>
        <el-empty v-if="rankings.length === 0" :description="rankHint" :image-size="40" />
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
import { initThemedChart, onThemeChange } from '../utils/echartsTheme'
import { flowApi, store } from '../api'
import { hasPerm } from '../utils/perm'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const loading = ref(false)
const todayDone = ref('—')
const weekDone = ref('—')
const avgTime = ref('—')
const timeoutRate = ref(null)
const rankings = ref([])
const rankHint = ref('暂无排行数据')
const historyHint = ref('')
const trendData = ref([])
const trendRef = ref(null)
let chart = null

/** 计入「处理」的动作（撤销/催办/终止不算办理动作） */
const HANDLED_ACTIONS = new Set(['SUBMIT', 'APPROVE', 'REJECT', 'TRANSFER', 'ADDSIGN', 'AUTO_PASS'])

function ts(v) {
  if (!v) return NaN
  if (typeof v === 'number') return v
  if (Array.isArray(v)) return new Date(v[0], (v[1] || 1) - 1, v[2] || 1, v[3] || 0, v[4] || 0, v[5] || 0).getTime()
  return new Date(String(v).replace(' ', 'T')).getTime()
}

/** wf_task_history.duration 单位是分钟（WfQueryService 收尾按 toMinutes 补齐） */
function fmtDuration(minutes) {
  if (!Number.isFinite(minutes) || minutes <= 0) return '—'
  if (minutes < 60) return `${minutes < 1 ? '<1' : Math.round(minutes)}分钟`
  return `${(minutes / 60).toFixed(1)}小时`
}

/** 本人办理记录（/history/page 不传 bizId 时后端按 oper_user_id 收敛到本人参与过的行） */
async function fetchHistory() {
  if (!hasPerm('wf:history:list')) {
    historyHint.value = '暂无数据（需审批历史查看权限）'
    return
  }
  const r = await flowApi.historyPage({ pageNum: 1, pageSize: 500 })
  const rows = r?.list || r?.records || []
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const weekStart = todayStart - ((now.getDay() + 6) % 7) * 86400000
  const mine = rows.filter((x) => HANDLED_ACTIONS.has(x.action))
  todayDone.value = mine.filter((x) => ts(x.operTime) >= todayStart).length
  const weekRows = mine.filter((x) => ts(x.operTime) >= weekStart)
  weekDone.value = weekRows.length
  const durs = weekRows.map((x) => Number(x.duration)).filter((v) => Number.isFinite(v) && v > 0)
  avgTime.value = durs.length ? fmtDuration(durs.reduce((a, b) => a + b, 0) / durs.length) : '—'
  const buckets = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date(todayStart - i * 86400000)
    buckets.push({ start: d.getTime(), end: d.getTime() + 86400000, label: `${d.getMonth() + 1}/${d.getDate()}`, count: 0 })
  }
  mine.forEach((x) => {
    const t = ts(x.operTime)
    const b = buckets.find((b) => t >= b.start && t < b.end)
    if (b) b.count++
  })
  trendData.value = buckets
  return rows.length
}

/** 排行与超时率：/perf/handler-top 是全公司范围，需流程监控权限；无权限时不臆造数字 */
async function fetchRanking() {
  rankings.value = []
  timeoutRate.value = null
  if (!hasPerm('wf:instance:list')) {
    rankHint.value = '暂无排行数据（需流程监控权限）'
    return
  }
  try {
    const r = await flowApi.perfHandlerTop(7)
    const rows = r?.rows || []
    rankings.value = rows.slice(0, 10).map((x) => ({ name: x.name, done: Number(x.handled) || 0 }))
    const meId = store.user?.userId ?? store.user?.id
    const me = rows.find((x) => meId != null && String(x.userId) === String(meId))
    if (me && Number(me.handled) > 0) {
      timeoutRate.value = Math.round((Number(me.overdueHandled) || 0) * 100 / Number(me.handled))
    }
    rankHint.value = '近 7 天暂无办结记录'
  } catch {
    rankHint.value = '排行数据加载失败'
  }
}

async function fetchData() {
  loading.value = true
  try {
    await fetchHistory()
  } catch {
    todayDone.value = '—'
    weekDone.value = '—'
    avgTime.value = '—'
    historyHint.value = '办理数据加载失败'
    trendData.value = []
  }
  await fetchRanking()
  loading.value = false
  await nextTick()
  renderTrend()
}

function renderTrend() {
  if (!trendRef.value || trendData.value.length === 0) return
  if (chart && chart.getDom() !== trendRef.value) {
    chart.dispose()
    chart = null
  }
  if (!chart) chart = initThemedChart(trendRef.value)
  chart.setOption({
    grid: { left: 30, right: 10, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: trendData.value.map((d) => d.label), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
    series: [{
      type: 'bar', data: trendData.value.map((d) => d.count),
      itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
      barWidth: '50%',
    }],
    tooltip: { trigger: 'axis' },
  }, true)
}

let offTheme = null
onMounted(() => {
  if (props.modelValue) fetchData()
  window.addEventListener('resize', onResize)
  offTheme = onThemeChange(() => {
    if (!chart || !trendRef.value) return
    chart.dispose()
    chart = null
    nextTick(renderTrend)
  })
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  offTheme && offTheme()
  chart?.dispose()
})
function onResize() { chart?.resize() }

watch(() => props.modelValue, (v) => {
  if (v) fetchData()
})
</script>

<style scoped>
.stats-content { padding: 0 4px; }
.stat-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 20px; }
.stat-card {
  text-align: center; border-radius: 8px; padding: 16px;
  border-left: 4px solid #dcdfe6; background: var(--app-bg, #f5f7fa);
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
  font-size: 12px; font-weight: 700; background: var(--app-hover, #f0f2f5); color: #909399;
}
.rank-num.rank-top { background: rgba(230, 162, 60, 0.14); color: #e6a23c; }
.rank-name { flex: 1; }
.rank-val { color: var(--app-text-secondary, #606266); font-weight: 600; }
</style>
