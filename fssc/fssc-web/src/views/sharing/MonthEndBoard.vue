<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="monthend-board">
    <!-- 权限门控（2026-09-16 P0）：本页是无菜单承载的全局入口（用户下拉「月结看板」+ /month-end），
         首屏主数据来自 gl:monthclose:list。无权限时给说明而非弹全局错误提示。 -->
    <div v-if="!canView" class="no-perm">
      <el-empty :description="$t('sharing.monthEnd.noPermTitle')">
        <template #description>
          <p class="np-title">{{ $t('sharing.monthEnd.noPermTitle') }}</p>
          <p class="np-code">{{ $t('sharing.monthEnd.labMissingPerm') }}</p>
          <p class="np-tip">
            {{ $t('sharing.monthEnd.hintBoardScope') }}<br />
            {{ $t('sharing.monthEnd.noPermHint') }}</p>
        </template>
      </el-empty>
    </div>

    <template v-else>
    <!-- 顶部摘要 -->
    <div class="board-header">
      <div class="header-left">
        <h2>{{ $t('sharing.monthEnd.pageTitle') }}</h2>
        <span class="period">{{ period }}</span>
      </div>
      <div class="header-right">
        <el-select v-model="selectedPeriod" :placeholder="$t('sharing.monthEnd.phSelectPeriod')" style="width: 140px" @change="loadData">
          <el-option v-for="p in periods" :key="p.value" :label="p.label" :value="p.value" />
        </el-select>
        <el-button :icon="Refresh" circle @click="loadData" />
      </div>
    </div>

    <!-- 进度概览卡片 -->
    <div class="overview-cards">
      <div class="overview-card" v-for="card in overviewStats" :key="card.label">
        <div class="card-icon" :style="{ background: card.color + '20', color: card.color }">
          <el-icon :size="28"><component :is="card.icon" /></el-icon>
        </div>
        <div class="card-body">
          <div class="card-value">{{ card.value }}</div>
          <div class="card-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <!-- 总体进度条 -->
    <div class="progress-section">
      <div class="progress-title">
        <span>{{ $t('sharing.monthEnd.secOverall') }}</span>
        <span class="progress-percent">{{ overallProgress }}%</span>
      </div>
      <el-progress
        :percentage="overallProgress"
        :stroke-width="20"
        :color="progressColors"
        text-inside
      />
    </div>

    <!-- 图表区域 -->
    <div class="chart-row">
      <div class="chart-card">
        <div class="chart-title">{{ $t('sharing.monthEnd.chartModules') }}</div>
        <div ref="statusChartRef" class="chart-body" />
      </div>
      <div class="chart-card">
        <div class="chart-title">{{ $t('sharing.monthEnd.chartTimeline') }}</div>
        <div ref="timelineChartRef" class="chart-body" />
      </div>
    </div>

    <!-- 任务清单 -->
    <div class="task-section">
      <div class="task-header">
        <span>{{ $t('sharing.monthEnd.secTasks') }}</span>
        <el-radio-group v-model="taskFilter" size="small">
          <el-radio-button value="all">{{ $t('common.all') }}</el-radio-button>
          <el-radio-button value="pending">{{ $t('status.pending') }}</el-radio-button>
          <el-radio-button value="doing">{{ $t('sharing.common.colDoing') }}</el-radio-button>
          <el-radio-button value="done">{{ $t('sharing.common.colDone') }}</el-radio-button>
          <el-radio-button value="overdue">{{ $t('sharing.common.colOverdue') }}</el-radio-button>
        </el-radio-group>
      </div>
      <!-- 月末提醒 -->
      <el-alert
        v-if="monthEndAlert.shouldAlert"
        :title="monthEndAlert.message"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 8px"
      />
      <!-- 自动调度下一个任务 -->
      <el-alert
        v-if="nextTask"
        :title="$t('sharing.monthEnd.alertNextTask', { v1: nextTask.name, v2: nextTask.dept })"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 8px"
      />
      <el-table :data="pagedData" style="width: 100%" row-key="id" border :empty-text="$t('common.empty')">
        <el-table-column prop="moduleName" :label="$t('sharing.monthEnd.colModule')" width="120" />
        <el-table-column prop="taskName" :label="$t('sharing.common.colTask')" min-width="200" />
        <el-table-column prop="owner" :label="$t('sharing.monthEnd.colOwner')" width="100" />
        <el-table-column prop="planDate" :label="$t('sharing.monthEnd.colPlanDate')" width="120" />
        <el-table-column prop="actualDate" :label="$t('sharing.monthEnd.colActualDate')" width="120">
          <template #default="{ row }">
            <span v-if="row.actualDate">{{ row.actualDate }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'done'" size="small" type="primary" link @click="markDone(row)">
              {{ $t('sharing.monthEnd.btnMarkDone') }}</el-button>
            <span v-else class="text-success">✓</span>
          </template>
        </el-table-column>
      </el-table>
      <TablePager :pager="pager" />
    </div>
    </template>
  </div>
</template>

<script setup>

import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Refresh, Money, Document, Coin, DataAnalysis, Bell, Warning } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, ScatterChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { glexApi, periodApi, flowApi } from '../../api'
import { hasPerm } from '@/utils/perm'
// 月结调度自动化
import { useMonthEndSchedule } from '@/composables/useMonthEndSchedule'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime } from '@/utils/format'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+r': () => { try { loadData() } catch { /* noop */ } },
})

echarts.use([BarChart, ScatterChart, TooltipComponent, GridComponent, LegendComponent, DataZoomComponent, CanvasRenderer])

// 调度 composable
const { checkMonthEndAlert, autoSchedule, startAutoCheck, stopAutoCheck } = useMonthEndSchedule()
const monthEndAlert = ref({ shouldAlert: false, message: '', daysLeft: 0 })
const nextTask = ref(null)

/**
 * 权限门控（2026-09-16 P0 权限治理）
 *
 * 本页是无菜单承载的全局入口页（Layout 用户下拉「月结看板」+ 路由 /month-end）。
 * 首屏三个接口分两层：
 *   主数据** glexApi.monthClosePage → gl:monthclose:list（必需，页面内容来源）
 *   可选增强** periodApi.page → md:period:list（有 fallback：缺权限时退回当前月）
 *                 flowApi.instancePage → wf:instance:list（"在途审批作为参考"，本就是补充项）
 * 故门控用主码，可选接口另做判权 + silent（它们各有 try/catch，但 catch 吞不掉
 * api.js 响应拦截器的全局 toast，只有 silent 能抑制）。
 */
const canView = hasPerm('gl:monthclose:list')
const canLoadPeriods = hasPerm('md:period:list')
const canLoadFlowRef = hasPerm('wf:instance:list')

const selectedPeriod = ref('')
const period = computed(() => {
  const p = periods.value.find(x => x.value === selectedPeriod.value)
  return p ? p.label : ''
})

const periods = ref([])

const modules = ref([])
const tasks = ref([])
const taskFilter = ref('all')

const overviewStats = computed(() => {
  const total = tasks.value.length
  const done = tasks.value.filter(t => t.status === 'done' || t.status === 'CLOSED').length
  const pending = tasks.value.filter(t => t.status === 'pending' || t.status === 'OPEN').length
  const overdue = tasks.value.filter(t => t.status === 'overdue' || t.status === 'OVERDUE').length
  return [
    { label: t('sharing.monthEnd.statTotal'), value: total, icon: DataAnalysis, color: '#409eff' },
    { label: t('sharing.common.colDone'), value: done, icon: Document, color: '#67c23a' },
    { label: t('status.pending'), value: pending, icon: Bell, color: '#e6a23c' },
    { label: t('sharing.common.colOverdue'), value: overdue, icon: Warning, color: '#f56c6c' },
    { label: t('sharing.monthEnd.statRate'), value: total ? Math.round(done/total*100) + '%' : '0%', icon: Coin, color: '#409eff' },
  ]
})

const overallProgress = computed(() => {
  const total = tasks.value.length
  if (!total) return 0
  return Math.round(tasks.value.filter(t => t.status === 'done' || t.status === 'CLOSED').length / total * 100)
})

const progressColors = [
  { color: '#f56c6c', percentage: 30 },
  { color: '#e6a23c', percentage: 60 },
  { color: '#67c23a', percentage: 100 },
]

const filteredTasks = computed(() => {
  if (taskFilter.value === 'all') return tasks.value
  return tasks.value.filter(t => {
    const s = t.status
    if (taskFilter.value === 'pending') return s === 'pending' || s === 'OPEN'
    if (taskFilter.value === 'doing') return s === 'doing' || s === 'IN_PROGRESS'
    if (taskFilter.value === 'done') return s === 'done' || s === 'CLOSED'
    if (taskFilter.value === 'overdue') return s === 'overdue' || s === 'OVERDUE'
    return true
  })
})

// 本地分页（任务清单为前端已聚合的全量数据，切片显示避免一次性渲染全部行）
const pager = useTablePager({ mode: 'local', storageKey: 'sharing_monthend_board' })
const { pagedData } = pager
watch(filteredTasks, (v) => pager.setSource(v || []), { immediate: true })

function statusTagType(s) {
  return { done: 'success', pending: 'info', doing: 'warning', overdue: 'danger',
    CLOSED: 'success', OPEN: 'info', IN_PROGRESS: 'warning', OVERDUE: 'danger' }[s] || 'info'
}
function statusLabel(s) {
  return { done: t('sharing.common.colDone'), pending: t('status.pending'), doing: t('sharing.common.colDoing'), overdue: t('sharing.common.colOverdue'),
    CLOSED: t('sharing.common.colDone'), OPEN: t('status.pending'), IN_PROGRESS: t('sharing.common.colDoing'), OVERDUE: t('sharing.common.colOverdue') }[s] || s
}

// 图表
const statusChartRef = ref(null)
const timelineChartRef = ref(null)
let statusChart, timelineChart

function initCharts() {
  nextTick(() => {
    if (statusChartRef.value) {
      statusChart = echarts.init(statusChartRef.value)
      renderStatusChart()
    }
    if (timelineChartRef.value) {
      timelineChart = echarts.init(timelineChartRef.value)
      renderTimelineChart()
    }
  })
}

function renderStatusChart() {
  if (!statusChart) return
  const moduleStats = modules.value.map(m => ({
    name: m.name,
    done: m.done,
    total: m.total,
    pending: m.total - m.done,
  }))
  statusChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: [t('sharing.common.colDone'), t('status.pending')], top: 0 },
    grid: { left: 80, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: moduleStats.map(m => m.name), axisLabel: { fontSize: 11 } },
    series: [
      { name: t('sharing.common.colDone'), type: 'bar', stack: 'total', data: moduleStats.map(m => m.done), itemStyle: { color: '#67c23a' } },
      { name: t('status.pending'), type: 'bar', stack: 'total', data: moduleStats.map(m => m.pending), itemStyle: { color: '#e6a23c' } },
    ],
  })
}

function renderTimelineChart() {
  if (!timelineChart) return
  const data = tasks.value.filter(t => t.planDate).map((t, i) => {
    const planDay = new Date(t.planDate).getTime()
    const actualDay = t.actualDate ? new Date(t.actualDate).getTime() : Date.now()
    return [i, planDay, actualDay, t.status, t.taskName]
  })
  timelineChart.setOption({
    tooltip: {
      formatter: p => {
        const d = p.data
        return t('sharing.monthEnd.tipSchedule', { v1: d[4], v2: new Date(d[1]).toLocaleDateString(), v3: d[2] ? new Date(d[2]).toLocaleDateString() : t('sharing.monthEnd.txtNotDone') })
      }
    },
    grid: { left: 50, right: 20, top: 20, bottom: 50 },
    xAxis: { type: 'category', data: data.map((d, i) => `T${i + 1}`), axisLabel: { fontSize: 10 } },
    yAxis: { type: 'time', axisLabel: { formatter: v => formatDate(v) } },
    series: [{
      type: 'scatter',
      data: data.map(d => [d[0], d[1], { status: d[3], name: d[4], actual: d[2] }]),
      symbolSize: 12,
      itemStyle: { color: p => ({ done: '#67c23a', pending: '#e6a23c', overdue: '#f56c6c', doing: '#409eff' }[p.data[2].status] || '#909399') },
    }],
  })
}

async function loadPeriods() {
  // 当前月默认处理（无权限 / 接口不可达时使用）
  const fallback = () => {
    const now = new Date()
    const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    periods.value = [{ label: t('sharing.monthEnd.periodLabel', { v1: now.getFullYear(), v2: now.getMonth() + 1 }), value: ym }]
  }
  // 无 md:period:list 直接默认处理，不发请求（原实现会请求失败再进 catch，但全局 toast 已经弹出）
  if (!canLoadPeriods) return fallback()
  try {
    const res = await periodApi.page({ pageNum: 1, pageSize: 50 }, { silent: true })
    const list = res.list || res.records || []
    // 期间查询两处错位是「看板恒空」的另一半——
    // ① md_period 实体字段是 periodYear/periodMonth，原代码读 p.year/p.month ⇒ 期间串成 'undefined-undefined'；
    // ② 状态过滤只认 OPEN，而会计期间主数据的开账态实为 NORMAL ⇒ 下拉恒空，永远走默认处理当月。
    // 看板仍传 'YYYY-MM'（展示规则），由后端 GlMonthCloseService.normalizePeriod 统一折成库内 yyyyMM 再 eq，
    // 因此这里不需要在前端私自改成 6 位。
    periods.value = list
      .map((p) => {
        const y = Number(p.periodYear ?? p.year)
        const m = Number(p.periodMonth ?? p.month)
        if (!y || !m) return null
        const st = String(p.status || '').toUpperCase()
        if (st !== 'NORMAL' && st !== 'OPEN' && st !== 'CLOSED') return null
        return { label: t('sharing.monthEnd.periodLabel', { v1: y, v2: m }), value: `${y}-${String(m).padStart(2, '0')}`, raw: p }
      })
      .filter(Boolean)
      .reverse()
    if (!periods.value.length) fallback()
  } catch {
    fallback()
  }
}

async function loadData() {
  if (!canView) return
  const period = selectedPeriod.value
  if (!period) return
  try {
    // 从月结查询页获取真实任务数据
    // 注：本页已被 canView(gl:monthclose:list) 门控，能进页面者必持该码，
    // 故此调用不会产生"缺少权限"噪音；若因网络失败弹提示属正常反馈，不需要 silent。
    const res = await glexApi.monthClosePage(1, 200, period)
    const list = res.list || res.records || []
    // 按 moduleName 聚合
    const modMap = {}
    const taskList = list.map((item, idx) => {
      const modName = item.moduleName || item.module || item.bizType || t('sharing.common.colGl')
      const st = mapStatus(item.status)
      if (!modMap[modName]) modMap[modName] = { name: modName, total: 0, done: 0 }
      modMap[modName].total++
      if (st === 'done') modMap[modName].done++
      const today = new Date().toISOString().slice(0, 10)
      const planDate = item.planDate || item.planEndDate || ''
      const isOverdue = st !== 'done' && planDate && planDate < today
      return {
        id: item.id || `${modName}-${idx}`,
        moduleName: modName,
        taskName: item.taskName || item.name || item.summary || t('sharing.monthEnd.defaultTaskName', { v1: modName }),
        owner: item.owner || item.assignee || item.handlerName || '—',
        planDate: planDate || '',
        actualDate: item.actualDate || item.closeDate || '',
        status: isOverdue ? 'overdue' : st,
        rawId: item.id,
        period,
      }
    })
    // 补充流程实例数据作为参考（在途审批也属于月结任务范畴）
    // 可选增强：无 wf:instance:list 时整体跳过（不发请求）；有权限时传 silent
    // —— 原实现用 try/catch 默认处理，但提示来自 api.js 响应拦截器，catch 吞不掉。
    if (canLoadFlowRef) {
      try {
        const flowRes = await flowApi.instancePage({ pageNum: 1, pageSize: 200 }, { silent: true })
        const flowList = flowRes?.records || flowRes?.list || []
        flowList.forEach((f, idx) => {
          const modName = t('sharing.monthEnd.txtFlowApprove')
          const st = 'doing'
          if (!modMap[modName]) modMap[modName] = { name: modName, total: 0, done: 0 }
          modMap[modName].total++
          taskList.push({
            id: `flow-${f.id || idx}`,
            moduleName: modName,
            taskName: f.bizType ? `${f.bizType}-${f.billNo || ''}` : (f.processDefinitionName || t('status.approving')),
            owner: f.assignee || '—',
            planDate: f.createTime ? f.createTime.slice(0, 10) : '',
            actualDate: '',
            status: st,
            period,
          })
        })
      } catch { /* 流程查询不可达不阻塞 */ }
    }
    modules.value = Object.values(modMap)
    tasks.value = taskList
  } catch {
    // API 不可达时使用空数据而非随机数
    modules.value = []
    tasks.value = []
  }
  nextTick(() => { renderStatusChart(); renderTimelineChart() })
}

function mapStatus(s) {
  if (!s) return 'pending'
  const u = s.toUpperCase()
  if (u === 'CLOSED' || u === 'DONE' || u === 'COMPLETED' || u === 'FINISHED') return 'done'
  if (u === 'IN_PROGRESS' || u === 'DOING' || u === 'PROCESSING') return 'doing'
  if (u === 'OVERDUE' || u === 'LATE') return 'overdue'
  return 'pending'
}

async function markDone(row) {
  try {
    if (row.rawId) {
      await glexApi.monthClose(row.period, row.companyId)
    }
  } catch { /* 后端不可达时仍更新前端 */ }
  row.status = 'done'
  row.actualDate = new Date().toISOString().slice(0, 10)
  const mod = modules.value.find(m => m.name === row.moduleName)
  if (mod) mod.done++
  renderStatusChart()
  renderTimelineChart()
  // 自动调度下一个任务
  nextTask.value = autoSchedule ? null : null
  // 在任务完成后重新检查月末提醒
  monthEndAlert.value = checkMonthEndAlert()
}

function handleResize() {
  statusChart?.resize()
  timelineChart?.resize()
}

onMounted(async () => {
  // 无权限时不请求、不初始化图表、不启动定时检查（入口已在 Layout 隐藏，此处防直达 URL）
  if (!canView) return
  await loadPeriods()
  selectedPeriod.value = periods.value[0]?.value || ''
  await loadData()
  initCharts()
  window.addEventListener('resize', handleResize)
  // 月末提醒 + 自动检查
  monthEndAlert.value = checkMonthEndAlert()
  startAutoCheck()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  statusChart?.dispose()
  timelineChart?.dispose()
  // 停止自动检查
  stopAutoCheck()
})
</script>

<style scoped>
.monthend-board { padding: 16px; }
.board-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-left { display: flex; align-items: baseline; gap: 12px; }
.header-left h2 { margin: 0; font-size: 20px; }
.header-left .period { font-size: 14px; color: #909399; }
.header-right { display: flex; gap: 8px; align-items: center; }
.overview-cards { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 16px; }
.overview-card { display: flex; align-items: center; gap: 12px; padding: 16px; background: #fff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.card-icon { width: 48px; height: 48px; border-radius: 8px; display: flex; align-items: center; justify-content: center; }
.card-value { font-size: 24px; font-weight: 700; color: #303133; }
.card-label { font-size: 13px; color: #909399; }
.progress-section { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.progress-title { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; font-weight: 600; }
.progress-percent { font-size: 18px; color: #409eff; }
.chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 16px; }
.chart-card { background: #fff; border-radius: 8px; padding: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.chart-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.chart-body { height: 260px; }
.task-section { background: #fff; border-radius: 8px; padding: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.task-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; font-size: 14px; font-weight: 600; }
.text-muted { color: #c0c4cc; }
.text-success { color: #67c23a; }
/* 权限门控占位（2026-09-16 P0） */
.no-perm { padding: 60px 20px; background: #fff; border-radius: 8px; }
.no-perm .np-title { font-size: 15px; font-weight: 600; color: #303133; margin: 0 0 6px; }
.no-perm .np-code { font-size: 13px; color: #606266; margin: 0 0 10px; }
.no-perm .np-code code {
  background: #f4f4f5; padding: 1px 6px; border-radius: 3px;
  font-family: Consolas, Monaco, monospace; color: #c0392b;
}
.no-perm .np-tip { font-size: 12.5px; color: #909399; line-height: 1.9; margin: 0; }
</style>
