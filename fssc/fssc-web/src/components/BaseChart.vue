<!-- SPDX-License-Identifier: Apache-2.0 -->
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

function renderChart() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
    window.addEventListener('resize', resize)
  }
  if (props.option) {
    chart.setOption(props.option, { notMerge: true })
  }
}

function resize() {
  chart && chart.resize()
}

onMounted(() => {
  nextTick(renderChart)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  chart && chart.dispose()
  chart = null
})

watch(() => props.option, renderChart, { deep: true })
</script>
