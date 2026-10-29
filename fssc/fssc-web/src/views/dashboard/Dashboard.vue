<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="dash" :class="{ 'dash-full': isFullscreen }">
    <div class="dash-toolbar">
      <el-button size="small" @click="exportXlsx('bills')">导出单据</el-button>
      <el-button size="small" @click="exportXlsx('dept-ranking')">导出部门排行</el-button>
      <el-button size="small" @click="exportXlsx('budget-execution')">导出预算执行率</el-button>
      <el-button :icon="isFullscreen ? 'Close' : 'FullScreen'" size="small" @click="toggleFullscreen">
        {{ isFullscreen ? '退出大屏' : '进入大屏' }}
      </el-button>
    </div>
    <el-row :gutter="16">
      <el-col :xs="24" :md="8"><el-card header="报销统计（按状态）"><div ref="statusChart" class="chart" @click="drillStatus" /></el-card></el-col>
      <el-col :xs="24" :md="8"><el-card header="付款进度"><div ref="payChart" class="chart" @click="drillPay" /></el-card></el-col>
      <el-col :xs="24" :md="8"><el-card header="审批时效（分钟）"><div ref="effChart" class="chart" @click="drillEff" /></el-card></el-col>
      <el-col :xs="24" :md="12" style="margin-top: 16px"><el-card header="月度趋势（单量/金额）"><div ref="trendChart" class="chart" @click="drillTrend" /></el-card></el-col>
      <el-col :xs="24" :md="12" style="margin-top: 16px"><el-card header="部门报销排行（金额）"><div ref="deptChart" class="chart" @click="drillDept" /></el-card></el-col>
      <el-col :xs="24" :md="12" style="margin-top: 16px"><el-card header="预算执行率"><div ref="budgetChart" class="chart" @click="drillBudget" /></el-card></el-col>
    </el-row>

    <!-- 数据钻取抽屉 -->
    <DrillDownDrawer
      v-model="drillVisible"
      :title="drillTitle"
      :columns="drillColumns"
      :data="drillData"
      :drill-key="drillKey"
      :label-formatter="drillLabelFormatter"
      :child-key="drillChildKey"
      :child-label="drillChildLabel"
      :child-formatter="drillChildFormatter"
    />
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
// echarts 按需引入（core + 图表 + 组件 + Canvas 渲染器）——显著减小包体积
import * as echarts from 'echarts/core'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage } from 'element-plus'
import { dashApi, store } from '../../api'
import { hasPerm } from '../../utils/perm'
import DrillDownDrawer from '../../components/DrillDownDrawer.vue'
import { docTypeLabel } from '@/constants/enums.js'
import { statusLabel } from '@/constants/billStatus.js'
import { pathDenyReason } from '@/router/pathAccess'

/** 图表下钻统一入口：先按路由守卫同一把钥匙预判，避免点了图被弹回并吃一句「无权限访问该页面」。 */
function drill(path) {
  if (pathDenyReason(path)) { ElMessage.info('当前账号没有该业务页面的查看权限'); return }
  router.push(path)
}

echarts.use([PieChart, BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const router = useRouter()

const statusChart = ref()
const payChart = ref()
const effChart = ref()
const trendChart = ref()
const deptChart = ref()
const budgetChart = ref()
const isFullscreen = ref(false)
const pay = ref({})

const charts = []
const mq = window.matchMedia('(max-width: 767px)')

// 数据钻取
const drillVisible = ref(false)
const drillTitle = ref('数据钻取')
const drillColumns = ref([])
const drillData = ref([])
const drillKey = ref('')
const drillLabelFormatter = ref(null)
const drillChildKey = ref('')
const drillChildLabel = ref('明细')
const drillChildFormatter = ref(null)
const rawData = { stats: [], trend: [], budget: [], pay: {}, dept: [], eff: [] }

/** 抽屉打开前的统一复位：没有真实下一层的入口不摆「钻取 →」 */
function resetDrill() {
  drillLabelFormatter.value = null
  drillChildKey.value = ''
  drillChildLabel.value = '明细'
  drillChildFormatter.value = null
}
function openDrill() {
  drillVisible.value = true
}

function drillStatus() {
  resetDrill()
  drillTitle.value = '报销统计 - 按状态明细'
  drillKey.value = 'bill_type'
  drillColumns.value = [
    { prop: 'bill_type', label: '单据类型', formatter: (v) => docTypeLabel(v) },
    { prop: 'status', label: '状态', formatter: (v) => statusLabel(v) },
    { prop: 'cnt', label: '数量', numeric: true, align: 'right' },
  ]
  drillData.value = rawData.stats
  drillLabelFormatter.value = (v) => docTypeLabel(v)
  drillChildKey.value = 'status'
  drillChildLabel.value = '状态'
  drillChildFormatter.value = (v) => statusLabel(v)
  openDrill()
}
function drillPay() {
  resetDrill()
  drillTitle.value = '付款进度 - 明细'
  drillKey.value = 'name'
  drillColumns.value = [
    { prop: 'name', label: '付款状态' },
    { prop: 'value', label: '笔数', numeric: true, align: 'right' },
  ]
  drillData.value = [
    { name: '已付', value: Number(rawData.pay.paidCnt || 0) },
    { name: '待付', value: Number(rawData.pay.waitingCnt || 0) },
  ]
  openDrill()
}
function drillEff() {
  resetDrill()
  drillTitle.value = '审批时效 - 按单据类型'
  drillKey.value = 'bill_type'
  drillColumns.value = [
    { prop: 'bill_type', label: '单据类型', formatter: (v) => docTypeLabel(v) },
    { prop: 'avg_minutes', label: '平均审批分钟', numeric: true, align: 'right' },
  ]
  drillData.value = rawData.eff
  drillLabelFormatter.value = (v) => docTypeLabel(v)
  openDrill()
}
function drillTrend() {
  resetDrill()
  drillTitle.value = '月度趋势 - 明细'
  drillKey.value = 'period_month'
  drillColumns.value = [
    { prop: 'period_year', label: '年' },
    { prop: 'period_month', label: '月' },
    { prop: 'cnt', label: '单量', numeric: true, align: 'right' },
    { prop: 'amount', label: '金额', numeric: true, align: 'right' },
  ]
  drillData.value = rawData.trend
  // 类目拼「年-月」，不再出现裸数字 1..6
  drillLabelFormatter.value = (v, row) => `${row.period_year}-${String(v).padStart(2, '0')}`
  openDrill()
}
function drillDept() {
  resetDrill()
  drillTitle.value = '部门报销排行 - 明细'
  drillKey.value = 'dept_name'
  drillColumns.value = [
    { prop: 'dept_name', label: '部门名称' },
    { prop: 'dept_id', label: '部门ID' },
    { prop: 'amount', label: '报销金额', numeric: true, align: 'right' },
  ]
  drillData.value = rawData.dept
  openDrill()
}
function drillBudget() {
  resetDrill()
  drillTitle.value = '预算执行率 - 明细'
  drillKey.value = 'itemName'
  drillColumns.value = [
    { prop: 'itemName', label: '预算项' },
    { prop: 'executionRate', label: '执行率(%)', numeric: true, align: 'right' },
  ]
  drillData.value = rawData.budget
  openDrill()
}

// 大屏（暗色）文字/分隔线配色，普通模式跟随页面浅色
const dark = { text: '#e5eaf3', line: '#3a4b63' }
const light = { text: '#333', line: '#e4e7ed' }
const palette = ['#409eff', '#67c23a', '#f56c6c', '#e6a23c', '#909399', '#9b59b6']

function theme() { return isFullscreen.value ? dark : light }

function initChart(el) {
  const c = echarts.init(el)
  charts.push(c)
  return c
}

function axisBase() {
  const t = theme()
  return {
    axisLabel: { color: t.text }, axisLine: { lineStyle: { color: t.line } },
    splitLine: { lineStyle: { color: t.line, opacity: 0.4 } },
  }
}

function renderStatus(rows) {
  const chart = initChart(statusChart.value)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}张 ({d}%)' },
    legend: { bottom: 0, type: 'scroll', textStyle: { color: theme().text } },
    series: [{
      type: 'pie', radius: ['40%', '65%'], center: ['50%', '45%'],
      label: { formatter: '{b}\n{c}张', color: theme().text },
      data: rows.map((r, i) => ({ name: `${docTypeLabel(r.bill_type)}/${statusLabel(r.status)}`, value: Number(r.cnt), itemStyle: { color: palette[i % 6] } })),
    }],
  })
  chart.on('click', () => drill('/expense'))
}

function renderPay() {
  const chart = initChart(payChart.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['50%', '72%'], center: ['50%', '45%'],
      label: { formatter: '{b}\n{c}笔', color: theme().text },
      data: [
        { name: '已付', value: Number(pay.value.paidCnt || 0), itemStyle: { color: '#67c23a' } },
        { name: '待付', value: Number(pay.value.waitingCnt || 0), itemStyle: { color: '#e6a23c' } },
      ],
    }],
  })
  chart.on('click', () => drill('/pay'))
}

function renderEff(rows) {
  const chart = initChart(effChart.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 28 },
    xAxis: { type: 'category', data: rows.map((r) => docTypeLabel(r.bill_type)), ...axisBase() },
    yAxis: { type: 'value', name: '分钟', ...axisBase() },
    series: [{ name: '平均审批分钟', type: 'bar', barWidth: 30, data: rows.map((r) => Number(r.avg_minutes || 0)), itemStyle: { color: '#9b59b6' } }],
  })
  chart.on('click', () => drill('/approval'))
}

function renderTrend(rows) {
  const chart = initChart(trendChart.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { textStyle: { color: theme().text } },
    grid: { left: 40, right: 16, top: 32, bottom: 28 },
    xAxis: { type: 'category', data: rows.map((r) => `${r.period_year}-${r.period_month}`), ...axisBase() },
    yAxis: { type: 'value', ...axisBase() },
    series: [
      { name: '单量', type: 'bar', data: rows.map((r) => Number(r.cnt)), itemStyle: { color: '#409eff' } },
      { name: '金额', type: 'line', smooth: true, data: rows.map((r) => Number(r.amount)), itemStyle: { color: '#f56c6c' } },
    ],
  })
  chart.on('click', () => drill('/reports/trend'))
}

function renderDept(rows) {
  const chart = initChart(deptChart.value)
  const list = rows.slice(0, 8).reverse()
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 90, right: 40, top: 16, bottom: 24 },
    xAxis: { type: 'value', ...axisBase() },
    yAxis: { type: 'category', data: list.map((r) => r.dept_name || ('部门' + r.dept_id)), ...axisBase() },
    series: [{
      type: 'bar', barWidth: 16,
      data: list.map((r) => ({ value: Number(r.amount || 0), itemStyle: { color: '#67c23a' } })),
      label: { show: true, position: 'right', formatter: '{c}', color: theme().text },
    }],
  })
  chart.on('click', () => drill('/reports/dept'))
}

function renderBudget(rows) {
  const chart = initChart(budgetChart.value)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 90, right: 40, top: 16, bottom: 24 },
    xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' }, ...axisBase() },
    yAxis: { type: 'category', data: rows.map((r) => r.itemName).reverse(), ...axisBase() },
    series: [{
      type: 'bar', barWidth: 16,
      data: rows.map((r) => ({
        value: Number(r.executionRate).toFixed(1),
        itemStyle: { color: Number(r.executionRate) > 80 ? '#f56c6c' : '#409eff' },
      })).reverse(),
      label: { show: true, position: 'right', formatter: '{c}%', color: theme().text },
    }],
  })
  chart.on('click', () => drill('/reports/budget'))
}

function resize() { charts.forEach((c) => c.resize()) }

function toggleFullscreen() {
  if (!isFullscreen.value) {
    document.documentElement.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

/** 报表 Excel 导出（Blob 下载） */
async function exportXlsx(kind) {
  try {
    const map = {
      bills: '/tenant/report/export/bills',
      'dept-ranking': '/tenant/report/export/dept-ranking',
      'budget-execution': '/tenant/report/export/budget-execution',
    }
    const resp = await fetch('/api' + map[kind], { headers: { Authorization: 'Bearer ' + store.token } })
    if (!resp.ok) throw new Error('导出失败 HTTP ' + resp.status)
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = { bills: '报销单据.xlsx', 'dept-ranking': '部门报销排行.xlsx', 'budget-execution': '预算执行率.xlsx' }[kind]
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function onFsChange() {
  isFullscreen.value = !!document.fullscreenElement
  // 切换大屏后重建图表（暗色/浅色主题 + 尺寸变化）
  charts.forEach((c) => c.dispose())
  charts.length = 0
  nextTick(renderAll)
}

let dashPermDenied = false // USG-01: 首次权限拒绝即永久跳过(profile 未就绪时 perms 前置判断不可靠)
async function renderAll() {
  if (dashPermDenied) return
  try {
    // USG-01: 无 dashboard:view 权限的角色跳过 6 个看板聚合接口（权限拒绝噪声源头）
    // 判定必须走 hasPerm()——admin 的 perms=["*:*:*"] 用 includes() 恒为 false，会把 6 图误判成无权
    if (!hasPerm('dashboard:view')) return
    const [stats, trend, budget, payData, dept, eff] = await Promise.all([
      dashApi.stats(), dashApi.trend(6), dashApi.budget(), dashApi.pay(),
      dashApi.deptRanking(), dashApi.approvalEfficiency(),
    ])
    pay.value = payData
    rawData.stats = stats
    rawData.trend = trend
    rawData.budget = budget
    rawData.pay = payData
    rawData.dept = dept
    rawData.eff = eff
    await nextTick()
    renderStatus(stats)
    renderPay()
    renderEff(eff)
    renderTrend(trend)
    renderDept(dept)
    renderBudget(budget)
  } catch (e) {
    // USG-01: 无权限(4030)则永久停调,消除每次进入/缩放都打 6 个无权限聚合调用的噪声
    if (/权限/.test(String((e && e.message) || ''))) dashPermDenied = true
    console.warn(e)
  }
}

onMounted(() => {
  renderAll()
  document.addEventListener('fullscreenchange', onFsChange)
  mq.addEventListener('change', resize)
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFsChange)
  mq.removeEventListener('change', resize)
  window.removeEventListener('resize', resize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.chart { height: 260px; cursor: pointer; }
.dash-toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.dash-full { background: #0d1421; padding: 16px; border-radius: 8px; }
.dash-full :deep(.el-card) { background: #16233a; border-color: #2a3b55; }
.dash-full :deep(.el-card__header) { color: #e5eaf3; border-color: #2a3b55; }
.dash-full .chart { height: 340px; }
</style>
