<template>
  <el-drawer v-model="visible" :title="title" direction="rtl" size="560px" :destroy-on-close="true">
    <div class="drill-content">
      <!-- 面包屑钻取路径 -->
      <div class="drill-crumbs">
        <span v-for="(c, i) in crumbs" :key="i" class="crumb-item" @click="goBack(i)">
          {{ c.label }}
          <el-icon v-if="i < crumbs.length - 1"><ArrowRight /></el-icon>
        </span>
      </div>

      <!-- 当前层级数据 -->
      <div class="drill-level" v-loading="loading">
        <el-table :data="tableData" stripe size="small" @row-click="drillDeeper" highlight-current-row empty-text="暂无数据">
          <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label"
            :width="col.width" :align="col.align || 'left'" sortable>
            <template #default="{ row }">
              <span v-if="col.formatter" v-html="col.formatter(row[col.prop], row)"></span>
              <span v-else-if="col.numeric" class="num-cell">{{ formatNum(row[col.prop]) }}</span>
              <span v-else>{{ row[col.prop] ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" v-if="canDrillDeeper">
            <template #default>
              <el-button link size="small" type="primary">钻取 →</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 迷你趋势图 -->
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

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '数据钻取' },
  columns: { type: Array, default: () => [] },
  data: { type: Array, default: () => [] },
  drillKey: { type: String, default: '' }, // 钻取字段
  drillLabels: { type: Array, default: () => [] }, // 各层标签
})
const emit = defineEmits(['update:modelValue', 'drill', 'back'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const loading = ref(false)
const crumbs = ref([{ label: '汇总' }])
const currentLevel = ref(0)
const tableData = ref([])
const trendData = ref([])
const trendRef = ref(null)
let chart = null

const canDrillDeeper = computed(() => currentLevel.value < (props.drillLabels.length - 1))

function loadData() {
  loading.value = true
  tableData.value = props.data
  trendData.value = props.data.slice(0, 12).map((d, i) => ({
    name: d[props.drillKey] || `项${i + 1}`,
    value: Object.values(d).find(v => typeof v === 'number') || 0,
  }))
  loading.value = false
  renderTrend()
}

function drillDeeper(row) {
  if (!canDrillDeeper.value) return
  const label = row[props.drillKey] || `层级${currentLevel.value + 1}`
  crumbs.value.push({ label, data: row })
  currentLevel.value++
  emit('drill', { level: currentLevel.value, row })
}

function goBack(index) {
  if (index === crumbs.value.length - 1) return
  crumbs.value = crumbs.value.slice(0, index + 1)
  currentLevel.value = index
  emit('back', { level: index })
}

function formatNum(v) {
  if (v == null || isNaN(v)) return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function renderTrend() {
  if (!trendRef.value || trendData.value.length === 0) return
  if (!chart) chart = echarts.init(trendRef.value)
  chart.setOption({
    grid: { left: 40, right: 10, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: trendData.value.map(d => d.name), axisLabel: { fontSize: 10, rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
    series: [{
      type: 'bar', data: trendData.value.map(d => d.value),
      itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
    }],
    tooltip: { trigger: 'axis' },
  })
}

watch(() => props.modelValue, (v) => {
  if (v) {
    crumbs.value = [{ label: '汇总' }]
    currentLevel.value = 0
    nextTick(() => loadData())
  }
})

onBeforeUnmount(() => chart?.dispose())
</script>

<style scoped>
.drill-content { display: flex; flex-direction: column; }
.drill-crumbs { display: flex; align-items: center; gap: 4px; margin-bottom: 12px; font-size: 13px; }
.crumb-item { display: flex; align-items: center; gap: 4px; cursor: pointer; color: #409eff; }
.crumb-item:last-child { color: #303133; cursor: default; font-weight: 600; }
.drill-level { flex: 1; }
.drill-trend { margin-top: 16px; }
.trend-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.trend-chart { height: 200px; }
.num-cell { font-variant-numeric: tabular-nums; }
</style>
