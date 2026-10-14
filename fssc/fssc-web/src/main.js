// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { createApp, watch } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 统一文件预览组件 @open-file-viewer 的全局样式（图片/PDF/Office 等在线预览）
import '@open-file-viewer/core/style.css'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { bootstrapSession } from './api'
// 国际化基座（域8A/T0.5）：i18n 实例 + 语言切换器 + Element Plus locale 跟随
import i18n, { getLocale } from './i18n'
import { elementLocaleOf } from './i18n/element-locale'
// 移动报销端（H5）视觉/交互 token（P1-1）：三页共享一套色板/字号阶/热区硬线，
// 取代此前「三页各自 scoped 复制一份」（AD-04）与内联 style 覆盖（AD-05）。
import './styles/h5-tokens.css'
import { permissionDirective } from './directives/permission'
import { preventDoubleClickDirective } from './directives/preventDoubleClick'
import ErrorBoundary from './components/common/ErrorBoundary.vue'
import UserSelect from './components/common/UserSelect.vue'
import LocaleSwitch from './components/common/LocaleSwitch.vue'

const app = createApp(App)
app.use(createPinia())
app.use(router)
// 域8A：注册 i18n（Composition API + 全局注入，模板可直接用 $t）
app.use(i18n)
// EP locale 跟随应用语言：切换时重新 install（Element Plus 支持后 install 覆盖 locale）
app.use(ElementPlus, { locale: elementLocaleOf(getLocale()) })
// 监听语言切换，同步 EP 组件文案（分页/日期/确认框等）
watch(
  () => i18n.global.locale.value,
  (loc) => app.use(ElementPlus, { locale: elementLocaleOf(loc) }),
)
for (const [name, comp] of Object.entries(Icons)) {
  app.component(name, comp)
}

// R5-04: 全局权限指令
app.directive('permission', permissionDirective)
// P2-5: 全局防重复提交指令
app.directive('prevent-double-click', preventDoubleClickDirective)
// R5-05: 全局错误边界组件
app.component('ErrorBoundary', ErrorBoundary)
// P2-3: 统一人员下拉组件
app.component('UserSelect', UserSelect)
// 域8A: 全局语言切换器
app.component('LocaleSwitch', LocaleSwitch)


// 专项五(WCAG)全局无障碍修补:
// 1) 图标-only 按钮自动回填 aria-label(从 el-icon 子类的图标名推导) —— 修复 button-name(critical)
// 2) 表格滚动容器补 tabindex=0 —— 修复 scrollable-region-focusable(serious)
;(function () {
  const labelFor = (btn) => {
    if (btn.getAttribute('aria-label')) return
    if ((btn.innerText || '').trim()) return
    const icon = btn.querySelector('[class*="el-icon"], svg')
    const cls = icon && (icon.getAttribute('class') || '')
    const m = cls && cls.match(/(?:el-icon--)?([A-Za-z]+)/)
    if (m) btn.setAttribute('aria-label', m[1])
  }
  const SEL = 'button, .el-table__body-wrapper, .el-scrollbar__wrap, input[placeholder]'
  const scan = (root) => {
    try {
      const targets = []
      if (root.matches && root.matches(SEL)) targets.push(root)
      if (root.querySelectorAll) targets.push(...root.querySelectorAll(SEL))
      targets.forEach((el) => {
        if (el.tagName === 'BUTTON') labelFor(el)
        else if (el.tagName === 'INPUT') {
          const unlabelled = !el.getAttribute('aria-label') && (!el.labels || el.labels.length === 0)
          if (unlabelled) el.setAttribute('aria-label', el.getAttribute('placeholder') || '')
        } else if (el.getAttribute('tabindex') !== '0') el.setAttribute('tabindex', '0')
      })
    } catch (e) { /* 静默 */ }
  }
  document.addEventListener('DOMContentLoaded', () => {
    scan(document.body)
    new MutationObserver((muts) => muts.forEach((m) => m.addedNodes.forEach((n) => n.nodeType === 1 && scan(n))))
      .observe(document.body, { childList: true, subtree: true })
  })
})()

// 挂载前先探活 + 校验本地 token：
// 后端返回 401（token 过期 / 后端重启后密钥变更）会清掉残留 token，
// 随后 router 守卫按"无 token"把用户送去 /login，而不是直接跳进 /dashboard。
bootstrapSession().finally(() => app.mount('#app'))
