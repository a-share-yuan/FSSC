import * as echarts from 'echarts/core'

// 深色模式下的图表令牌：坐标轴/图例/提示框与 --app-* 对齐，
// ECharts 默认文字是浅色主题的深灰，深底上会看不清。
const axisStyle = () => ({
  axisLine: { lineStyle: { color: '#2a2a4a' } },
  axisTick: { lineStyle: { color: '#2a2a4a' } },
  axisLabel: { color: '#a0a0b0' },
  nameTextStyle: { color: '#a0a0b0' },
  splitLine: { lineStyle: { color: '#242442' } },
  splitArea: { areaStyle: { color: ['rgba(255,255,255,0.02)', 'rgba(255,255,255,0.04)'] } },
})

const darkTheme = {
  color: ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#9b59b6', '#36cfc9', '#f59a23', '#7c5cff'],
  backgroundColor: 'transparent',
  textStyle: { color: '#a0a0b0' },
  title: { textStyle: { color: '#e0e0e0' }, subtextStyle: { color: '#a0a0b0' } },
  legend: { textStyle: { color: '#a0a0b0' } },
  tooltip: {
    backgroundColor: '#16213e',
    borderColor: '#2a2a4a',
    textStyle: { color: '#e0e0e0' },
  },
  categoryAxis: axisStyle(),
  valueAxis: axisStyle(),
  timeAxis: axisStyle(),
  logAxis: axisStyle(),
  visualMap: { textStyle: { color: '#a0a0b0' } },
  // 滑杆的浅色来自 moveHandleStyle/handleStyle 的默认值（#d2dbee/#fff），不覆盖就会在深底上留白条
  dataZoom: {
    borderColor: '#2a2a4a',
    backgroundColor: 'rgba(255,255,255,0.04)',
    fillerColor: 'rgba(64,158,255,0.16)',
    handleStyle: { color: '#16213e', borderColor: '#409eff' },
    moveHandleStyle: { color: '#2a2a4a', opacity: 0.85 },
    emphasis: { handleStyle: { borderColor: '#66b1ff' }, moveHandleStyle: { color: '#3a3a5a' } },
    textStyle: { color: '#a0a0b0' },
    dataBackground: { lineStyle: { color: '#3a3a5a' }, areaStyle: { color: '#242442' } },
    selectedDataBackground: { lineStyle: { color: '#409eff' }, areaStyle: { color: '#1a1a3e' } },
  },
  // 系列标签不走 textStyle 默认值（饼图/散点等默认写死 #333），逐个补
  // emptyCircleStyle：饼图数据全为 0 时 ECharts 画一圈写死的 lightgray，深底下是一整个亮圆环
  pie: {
    label: { color: '#a0a0b0' },
    labelLine: { lineStyle: { color: '#4a4a6a' } },
    emptyCircleStyle: { color: '#2a2a4a' },
  },
  bar: { label: { color: '#a0a0b0' } },
  line: { label: { color: '#a0a0b0' } },
  scatter: { label: { color: '#a0a0b0' } },
  gauge: { detail: { color: '#e0e0e0' }, title: { color: '#a0a0b0' } },
  sankey: { label: { color: '#c8c9cc' } },
  heatmap: { label: { color: '#e0e0e0' } },
}

let registered = false

export function isDarkTheme() {
  return document.documentElement.classList.contains('dark')
}

export function initThemedChart(el) {
  if (!registered) {
    echarts.registerTheme('fssc-dark', darkTheme)
    registered = true
  }
  return echarts.init(el, isDarkTheme() ? 'fssc-dark' : undefined)
}

export function onThemeChange(cb) {
  window.addEventListener('fssc:theme', cb)
  return () => window.removeEventListener('fssc:theme', cb)
}
