<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-drawer v-model="visible" :title="title" direction="rtl" size="560px" :destroy-on-close="true" @opened="onOpened">
    <div class="drill-content">
      <!-- 面包屑钻取路径 -->
      <div class="drill-crumbs">
        <span v-for="(c, i) in crumbs" :key="i" class="crumb-item" @click="goBack(i)">
          {{ c.label }}
          <el-icon v-if="i < crumbs.length - 1"><ArrowRight /></el-icon>
        </span>
      </div>

      <!-- 当前层级数据 -->
      <div class="drill-level" :class="{ 'can-drill': canDrillDeeper }">
        <el-table :data="tableData" stripe size="small" @row-click="onRowClick" highlight-current-row :empty-text="$t('common.empty')">
          <el-table-column v-for="col in visibleColumns" :key="col.prop" :prop="col.prop" :label="col.label"
            :width="col.width" :align="col.align || 'left'" sortable>
            <template #default="{ row }">
              <span v-if="col.formatter" v-html="col.formatter(row[col.prop], row)"></span>
              <span v-else-if="col.numeric" class="num-cell">{{ formatNum(row[col.prop]) }}</span>
              <span v-else>{{ row[col.prop] ?? '-' }}</span>
            </template>
          </el-table-column>
          <!-- 只有存在真实下一层（传入 childKey）时才给「钻取 →」，否则不摆无动作的按钮 -->
          <el-table-column label="操作" width="80" v-if="canDrillDeeper">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click.stop="drillDeeper(row)">钻取 →</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 迷你趋势图（按当前层数值列绘制，类目文案由 labelFormatter 提供） -->
        <div v-if="trendData.length > 0" class="drill-trend">
          <div class="trend-title">趋势分析</div>
          <div ref="trendRef" class="trend-chart"></div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { initThemedChart, onThemeChange } from '../utils/echartsTheme'
import { i18n } from '../i18n'
import { useI18n } from 'vue-i18n'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

// setup 体内的 t() 取词；defineProps 默认值仍用 i18n.global.t
// （defineProps 宏不能引用 setup 内的局部变量）。
const { t } = useI18n()

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: i18n.global.t('DrillDownDrawer.main.Data') },
  columns: { type: Array, default: () => [] },
  data: { type: Array, default: () => [] },
  drillKey: { type: String, default: '' }, // 第一层分组字段
  /** 首列/类目文案格式化 (value, row) => string，缺省直接显示原值 */
  labelFormatter: { type: Function, default: null },
  /** 下一层分组字段：有值才提供真实钻取，否则不摆「钻取 →」 */
  childKey: { type: String, default: '' },
  childLabel: { type: String, default: '明细' },
  childFormatter: { type: Function, default: null },
})
const emit = defineEmits(['update:modelValue', 'drill', 'back'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const crumbs = ref([{ label: t('DrillDownDrawer.main.Summary') }])
const currentLevel = ref(0)
const tableData = ref([])
const trendData = ref([])
const trendRef = ref(null)
let chart = null

const valueColumn = computed(() => props.columns.find((c) => c.numeric) || null)
const canDrillDeeper = computed(() => !!props.childKey && currentLevel.value === 0)

const visibleColumns = computed(() => {
  if (currentLevel.value === 0) return props.columns
  const num = valueColumn.value
  return [
    { prop: props.childKey, label: props.childLabel, formatter: props.childFormatter },
    ...(num ? [{ prop: num.prop, label: num.label, numeric: true, align: 'right' }] : []),
  ]
})

/** 以指定分组字段生成迷你柱状图；数值列取表格里的数值列（不再猜"第一个数字字段"） */
function buildTrend(rows, key, formatter) {
  const prop = valueColumn.value?.prop
  if (!prop || !key) { trendData.value = []; return }
  trendData.value = (rows || []).slice(0, 12).map((d, i) => ({
    name: formatter ? formatter(d[key], d) : (d[key] ?? `项${i + 1}`),
    value: Number(d[prop]) || 0,
  }))
}

function loadData() {
  tableData.value = props.data
  buildTrend(props.data, props.drillKey, props.labelFormatter)
  return nextTick().then(renderTrend)
}

function onRowClick(row) {
  if (canDrillDeeper.value) drillDeeper(row)
}

function drillDeeper(row) {
  if (!canDrillDeeper.value) return
  const rawVal = row[props.drillKey]
  crumbs.value.push({ label: props.labelFormatter ? props.labelFormatter(rawVal, row) : (rawVal ?? '明细'), raw: rawVal })
  currentLevel.value = 1
  const prop = valueColumn.value?.prop || 'value'
  const agg = new Map()
  props.data.forEach((r) => {
    if (String(r[props.drillKey]) !== String(rawVal)) return
    const k = r[props.childKey]
    const cur = agg.get(k) || { [props.childKey]: k, [prop]: 0 }
    cur[prop] += Number(r[prop]) || 0
    agg.set(k, cur)
  })
  tableData.value = [...agg.values()].sort((a, b) => (b[prop] || 0) - (a[prop] || 0))
  buildTrend(tableData.value, props.childKey, props.childFormatter)
  renderTrend()
  emit('drill', { level: 1, row })
}

function goBack(index) {
  if (index >= crumbs.value.length - 1) return
  crumbs.value = crumbs.value.slice(0, index + 1)
  currentLevel.value = index
  tableData.value = props.data
  buildTrend(props.data, props.drillKey, props.labelFormatter)
  renderTrend()
  emit('back', { level: index })
}

function formatNum(v) {
  if (v == null || isNaN(v)) return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function renderTrend() {
  if (!trendRef.value || trendData.value.length === 0) return
  // 抽屉 destroy-on-close：DOM 重建后旧实例指向已移除节点，必须重建
  if (chart && chart.getDom() !== trendRef.value) {
    chart.dispose()
    chart = null
  }
  if (!chart) chart = initThemedChart(trendRef.value)
  chart.setOption({
    // 下钻的金额可到百万级（「1,234,567」约 60px），写死 left:40 会裁掉首字符；
    // x 轴 rotate:30 的倾斜标签也需要底部自适应空间，一并交给 containLabel。
    grid: { left: 8, right: 12, top: 20, bottom: 8, containLabel: true },
    xAxis: { type: 'category', data: trendData.value.map((d) => d.name), axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
    series: [{
      type: 'bar', data: trendData.value.map((d) => d.value),
      itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
    }],
    tooltip: { trigger: 'axis' },
  }, true)
}

/** 首开时 watch 里的 nextTick 早于抽屉内容挂载，这里在打开动画结束后补渲染 */
function onOpened() {
  if (currentLevel.value === 0) loadData()
  else renderTrend()
}

watch(() => props.modelValue, (v) => {
  if (!v) return
  crumbs.value = [{ label: t('DrillDownDrawer.main.Summary') }]
  currentLevel.value = 0
  tableData.value = props.data
  buildTrend(props.data, props.drillKey, props.labelFormatter)
  nextTick(renderTrend)
})

watch(() => props.data, () => {
  if (!props.modelValue || currentLevel.value !== 0) return
  loadData()
})

onBeforeUnmount(() => {
  offTheme()
  chart?.dispose()
})

const offTheme = onThemeChange(() => {
  if (!chart || !trendRef.value) return
  chart.dispose()
  chart = null
  nextTick(renderTrend)
})
</script>

<style scoped>
.drill-content { display: flex; flex-direction: column; }
.drill-crumbs { display: flex; align-items: center; gap: 4px; margin-bottom: 12px; font-size: 13px; }
.crumb-item { display: flex; align-items: center; gap: 4px; cursor: pointer; color: #409eff; }
.crumb-item:last-child { color: var(--app-text, #303133); cursor: default; font-weight: 600; }
.drill-level { flex: 1; }
.drill-level.can-drill :deep(.el-table__row) { cursor: pointer; }
.drill-trend { margin-top: 16px; }
.trend-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.trend-chart { height: 200px; }
.num-cell { font-variant-numeric: tabular-nums; }
</style>
