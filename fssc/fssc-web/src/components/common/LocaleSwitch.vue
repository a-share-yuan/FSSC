<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-dropdown trigger="click" @command="onPick" :title="t('locale.switch')">
    <el-button text size="small" :aria-label="t('locale.switch')">
      <el-icon><component :is="current?.icon" /></el-icon>
      <span class="ls-text">{{ current?.short }}</span>
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="l in SUPPORT_LOCALES"
          :key="l.value"
          :command="l.value"
          :disabled="l.value === getLocale()"
        >
          {{ l.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
/**
 * LocaleSwitch 语言切换器（域8A / 任务 T0.5）
 *
 * 全局组件，挂在布局右上角与登录页。切换后：
 *   - i18n.locale 更新（语言包回落 zh-CN）
 *   - localStorage 持久化（刷新不丢）
 *   - <html lang> 同步（无障碍朗读）
 *   - Element Plus 组件文案随之切换（由 LocaleProvider 统一处理）
 *
 * 用法：<LocaleSwitch />
 */
import { computed } from 'vue'
import { ElIcon } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { SUPPORT_LOCALES, getLocale, setLocale } from '../../i18n'

const { t } = useI18n()

// 用本地图标避免为两个图标引入新的 @element-plus/icons 依赖面
const ICONS = { 'zh-CN': 'ChatDotSquare', 'en-US': 'Switch' }
const current = computed(() => {
  const code = getLocale()
  return SUPPORT_LOCALES.find((l) => l.value === code) || SUPPORT_LOCALES[0]
})

function onPick(code) {
  setLocale(code)
}
</script>

<style scoped>
.ls-text {
  margin-left: 4px;
  font-size: 12px;
}
</style>
