<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<!--
  FieldTip —— 表单字段的「说明」占位符。

  原先字段说明一律写成输入框下方一行灰色小字，一屏放三四个字段就有三四行灰字，
  纵向节奏被切碎、同一表单内的必填红字反倒不显眼。这里把**静态解释**收进
  标签旁的 ⓘ 图标（悬浮/聚焦即显示），输入区只留「校验中/校验失败」这类
  会变化的信息，表单行高因此统一。

  用法一（推荐，跟在标签后，必填红星仍由 el-form-item 自己渲染）：
    <el-form-item label="法人主体" prop="companyId">
      <template #label>法人主体<FieldTip text="决定入哪本账与后续三单匹配范围" /></template>
      ...
    </el-form-item>

  用法二（自带标签文本，省一层 span）：
    <template #label><FieldTip label="法人主体" text="..." /></template>

  用法三（独立放在表头/工具栏等非表单位置）：<FieldTip text="..." />

  设计约束：
  - 触发方式 hover + focus，键盘 Tab 可聚焦（tabindex=0）并显示，不以 hover 为唯一入口；
  - 提示内容自动换行（max-width 280px），长句不会拉成一条横穿的细线；
  - 颜色取主题令牌，暗色模式下自动跟随，不写死灰值。
-->
<template>
  <el-tooltip placement="top" :show-after="120" effect="dark">
    <template #content>
      <div class="field-tip__body">{{ text }}</div>
    </template>
    <span class="field-tip" tabindex="0" role="note" :aria-label="text">
      <el-icon :size="13"><InfoFilled /></el-icon>
      <span v-if="label" class="field-tip__label">{{ label }}</span>
    </span>
  </el-tooltip>
</template>

<script setup>
import { InfoFilled } from '@element-plus/icons-vue'

defineProps({
  /** 提示正文 */
  text: { type: String, required: true },
  /** 可选：与图标同排显示的标签文本（不传则只渲染图标） */
  label: { type: String, default: '' },
})
</script>

<style scoped>
.field-tip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 4px;
  color: var(--el-text-color-placeholder);
  cursor: help;
  vertical-align: -2px;
  border-radius: 3px;
  outline: none;
  transition: color 0.15s;
}

.field-tip:hover,
.field-tip:focus-visible {
  color: var(--el-color-primary);
}

.field-tip__label {
  font-weight: 400;
}

.field-tip__body {
  max-width: 280px;
  line-height: 1.55;
  white-space: normal;
  word-break: break-word;
}
</style>
