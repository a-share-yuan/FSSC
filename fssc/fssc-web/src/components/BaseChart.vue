<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div ref="chartRef" :style="{ width: '100%', height: height }" />
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart, GaugeChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, GridComponent, LegendComponent,
  DataZoomComponent, ToolboxComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { initThemedChart, onThemeChange } from '../utils/echartsTheme'

echarts.use([
  BarChart, LineChart, PieChart, GaugeChart,
  TitleComponent, TooltipComponent, GridComponent, LegendComponent,
  DataZoomComponent, ToolboxComponent,
  CanvasRenderer,
])

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '300px' },
})

const chartRef = ref(null)
let chart = null
let offTheme = null
let ro = null

function renderChart() {
  if (!chartRef.value) return
  if (!chart) {
    chart = initThemedChart(chartRef.value)
    window.addEventListener('resize', resize)
  }
  if (props.option) {
    chart.setOption(props.option, { notMerge: true })
  }
}

// 深浅色切换：画布颜色不会随 CSS 变量变，重建实例后重新铺一遍数据
function onThemeSwitch() {
  if (!chartRef.value) return
  chart && chart.dispose()
  chart = null
  renderChart()
}

function resize() {
  chart && chart.resize()
}

onMounted(() => {
  offTheme = onThemeChange(onThemeSwitch)
  nextTick(renderChart)
  // 报表工作台把 25 张图挂在同一 el-tabs 的隐藏页签里，挂载时容器宽度为 0；
  // 切到该页不触发 window resize，尺寸就永远停在 0。ResizeObserver 在转可见时才补一次重算。
  ro = new ResizeObserver(resize)
  chartRef.value && ro.observe(chartRef.value)
})

onUnmounted(() => {
  offTheme && offTheme()
  window.removeEventListener('resize', resize)
  ro && ro.disconnect()
  ro = null
  chart && chart.dispose()
  chart = null
})

watch(() => props.option, renderChart, { deep: true })
</script>
