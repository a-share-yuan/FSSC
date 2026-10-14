// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 暗色模式主题切换 composable
 * 使用 CSS 变量 + Element Plus dark class
 */
import { ref, watch, onMounted } from 'vue'

const STORAGE_KEY = 'fssc_theme_mode'

const isDark = ref(false)

export function useTheme() {
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
  }

  function applyLightVars() {
    const root = document.documentElement
    root.style.setProperty('--app-bg', '#f0f2f5')
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
    html.dark .el-card { background-color: var(--app-card-bg) !important; border-color: var(--app-border) !important; }
    html.dark .el-table { background-color: var(--app-card-bg) !important; }
    html.dark .el-table tr { background-color: var(--app-card-bg) !important; }
    html.dark .el-table th.el-table__cell { background-color: #1a1a3e !important; }
    html.dark .el-dialog { background-color: var(--app-card-bg) !important; }
    html.dark .el-drawer { background-color: var(--app-card-bg) !important; }
    html.dark .el-input__wrapper { background-color: #1a1a3e !important; box-shadow: 0 0 0 1px var(--app-border) inset !important; }
    html.dark .el-select__wrapper { background-color: #1a1a3e !important; }
    html.dark .el-menu { background-color: var(--app-sidebar-bg) !important; }
    html.dark .el-pagination button, html.dark .el-pagination .el-pager li { background-color: var(--app-card-bg) !important; }
    html.dark .el-empty__description { color: var(--app-text-secondary); }
    html.dark .el-tag { background-color: #2a2a4a !important; }
    html.dark .el-overlay { background-color: rgba(0,0,0,0.6) !important; }
  `
  document.head.appendChild(style)
}

injectDarkStyles()
