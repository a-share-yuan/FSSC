// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 暗色模式主题切换 composable
 * 使用 CSS 变量 + Element Plus dark class
 */
import { ref, watch, onMounted } from 'vue'

const STORAGE_KEY = 'fssc_theme_mode'

const isDark = ref(false)

function applyLightVars() {
  const root = document.documentElement
  root.style.setProperty('--app-bg', '#f5f7fa')
  root.style.setProperty('--app-card-bg', '#ffffff')
  root.style.setProperty('--app-text', '#303133')
  root.style.setProperty('--app-text-secondary', '#606266')
  root.style.setProperty('--app-border', '#ebeef5')
  root.style.setProperty('--app-hover', '#ecf5ff')
  root.style.setProperty('--app-header-bg', '#ffffff')
  root.style.setProperty('--app-sidebar-bg', '#304156')
  root.style.setProperty('--app-sidebar-text', '#bfcbd9')
  root.style.setProperty('--app-sidebar-active', '#409eff')
}

function applyDarkVars() {
  const root = document.documentElement
  root.style.setProperty('--app-bg', '#1a1a2e')
  root.style.setProperty('--app-card-bg', '#16213e')
  root.style.setProperty('--app-text', '#e0e0e0')
  root.style.setProperty('--app-text-secondary', '#a0a0b0')
  root.style.setProperty('--app-border', '#2a2a4a')
  root.style.setProperty('--app-hover', '#1a1a3e')
  root.style.setProperty('--app-header-bg', '#16213e')
  root.style.setProperty('--app-sidebar-bg', '#0f0f23')
  root.style.setProperty('--app-sidebar-text', '#8888aa')
  root.style.setProperty('--app-sidebar-active', '#409eff')
}

function applyTheme(dark) {
  const html = document.documentElement
  if (dark) {
    html.classList.add('dark')
    html.setAttribute('data-theme', 'dark')
    applyDarkVars()
  } else {
    html.classList.remove('dark')
    html.setAttribute('data-theme', 'light')
    applyLightVars()
  }
  // 画布类组件（ECharts）拿不到 CSS 变量，靠这个事件重建实例套用主题
  window.dispatchEvent(new CustomEvent('fssc:theme', { detail: { dark } }))
}

// 启动即落一次主题：/profile 等不在 Layout 内的独立路由不会触发 useTheme 的 onMounted，
// 只有等 Layout 挂载才套变量的话，这些页面在深色偏好下仍会露白底。
export function applyStoredTheme() {
  let dark = false
  try { dark = localStorage.getItem(STORAGE_KEY) === 'true' } catch { dark = false }
  isDark.value = dark
  applyTheme(dark)
  return dark
}

export function useTheme() {
  function toggle() {
    isDark.value = !isDark.value
    localStorage.setItem(STORAGE_KEY, String(isDark.value))
    applyTheme(isDark.value)
  }

  function loadTheme() {
    try {
      isDark.value = localStorage.getItem(STORAGE_KEY) === 'true'
    } catch { isDark.value = false }
    applyTheme(isDark.value)
  }

  onMounted(() => loadTheme())

  watch(isDark, (v) => applyTheme(v))

  return { isDark, toggle, loadTheme }
}

// 注入暗色模式全局样式
const darkStyleId = 'fssc-dark-theme-style'
function injectDarkStyles() {
  if (document.getElementById(darkStyleId)) return
  const style = document.createElement('style')
  style.id = darkStyleId
  style.textContent = `
    html.dark { color-scheme: dark; }
    /* EP 暗色令牌与本应用色板对齐：文字/页面/卡片取同一组变量。
       组件默认暗色页底是 #0a0a0a，与页面底色 #1a1a2e 拼在一起会显出一道深色边。 */
    html.dark {
      --el-bg-color-page: var(--app-bg);
      --el-bg-color: var(--app-card-bg);
      --el-bg-color-overlay: var(--app-card-bg);
      --el-fill-color-blank: var(--app-card-bg);
      --el-text-color-primary: var(--app-text);
      --el-text-color-regular: var(--app-text-secondary);
      --el-text-color-secondary: #8888aa;
      --el-border-color: var(--app-border);
      --el-border-color-light: var(--app-border);
      --el-border-color-lighter: var(--app-border);
    }
    html.dark body { background-color: var(--app-bg) !important; color: var(--app-text) !important; }
    html.dark ::-webkit-scrollbar { width: 10px; height: 10px; }
    html.dark ::-webkit-scrollbar-track { background: var(--app-bg); }
    html.dark ::-webkit-scrollbar-thumb { background: #3a3a5a; border-radius: 5px; }
    html.dark .el-card { background-color: var(--app-card-bg) !important; border-color: var(--app-border) !important; color: var(--app-text); }
    html.dark .el-card__header { border-bottom-color: var(--app-border) !important; }
    html.dark .el-table {
      background-color: var(--app-card-bg) !important;
      --el-table-bg-color: var(--app-card-bg);
      --el-table-tr-bg-color: var(--app-card-bg);
      --el-table-header-bg-color: #1a1a3e;
      --el-table-border-color: var(--app-border);
      --el-table-text-color: var(--app-text);
      --el-table-header-text-color: var(--app-text);
      --el-table-row-hover-bg-color: var(--app-hover);
    }
    html.dark .el-table tr { background-color: var(--app-card-bg) !important; }
    html.dark .el-table th.el-table__cell { background-color: #1a1a3e !important; }
    html.dark .el-table__empty-text, html.dark .el-empty__description { color: var(--app-text-secondary) !important; }
    html.dark .el-dialog { background-color: var(--app-card-bg) !important; color: var(--app-text); }
    html.dark .el-message-box { background-color: var(--app-card-bg) !important; color: var(--app-text); }
    html.dark .el-message-box__title, html.dark .el-dialog__title { color: var(--app-text) !important; }
    html.dark .el-drawer { background-color: var(--app-card-bg) !important; color: var(--app-text); }
    html.dark .el-drawer__header { color: var(--app-text) !important; }
    html.dark .el-input__wrapper { background-color: #1a1a3e !important; box-shadow: 0 0 0 1px var(--app-border) inset !important; }
    html.dark .el-textarea__inner { background-color: #1a1a3e !important; box-shadow: 0 0 0 1px var(--app-border) inset !important; color: var(--app-text) !important; }
    html.dark .el-select__wrapper { background-color: #1a1a3e !important; }
    html.dark .el-menu { background-color: var(--app-sidebar-bg) !important; }
    html.dark .el-dropdown-menu { background-color: var(--app-card-bg) !important; }
    html.dark .el-pagination button, html.dark .el-pagination .el-pager li { background-color: var(--app-card-bg) !important; color: var(--app-text) !important; }
    html.dark .el-pagination .el-pager li.is-active { color: #409eff !important; }
    html.dark .el-descriptions__label { color: var(--app-text-secondary) !important; }
    html.dark .el-descriptions__content { color: var(--app-text) !important; }
    html.dark .el-alert--info.is-light { background-color: var(--app-hover) !important; }
    html.dark .el-alert--info.is-light .el-alert__description { color: var(--app-text-secondary) !important; }
    html.dark .el-alert__title { color: var(--app-text) !important; }
    html.dark .el-loading-mask { background-color: rgba(22, 33, 62, 0.8) !important; }
    html.dark .el-overlay { background-color: rgba(0,0,0,0.6) !important; }
  `
  document.head.appendChild(style)
}

injectDarkStyles()
