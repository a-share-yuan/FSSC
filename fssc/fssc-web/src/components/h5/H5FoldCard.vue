<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<!--
  移动端可折叠卡片：H5 审批详情/提单页整屏是七八段卡片顺排，整页要滚五六屏才到底，
  办理按钮虽吸底但"看单据"本身很累。本组件把每段收成一行标题，默认只展开第一段，
  收起态在标题右侧给一句摘要（明细行数与合计、发票张数、审批记录条数与最后一条、
  表单待补项计数），审批人不展开也能核对关键数，不用把决策依据全摊在长页上。

  两条实现约束：
  1. 正文用 v-show 而非 v-if——提单页的附件 input（ref="fileRef"）与已聚焦的输入框
     被销毁重建会丢引用/丢焦点，折叠只该是视觉收起。
  2. 不设 max-height / overflow（h5-tokens.css 硬线 3：单滚动容器＝文档流本身），
     所以不做高度过渡动画，只有箭头旋转。
-->
<template>
  <div class="h5-card h5-card--fold" :class="{ 'is-open': open }">
    <button
      type="button"
      class="h5-fold__head"
      :aria-expanded="open ? 'true' : 'false'"
      @click="$emit('update:open', !open)"
    >
      <slot name="head">
        <span class="h5-fold__title">{{ title }}</span>
      </slot>
      <span v-if="summary && !open" class="h5-fold__sum" :class="tone ? `h5-fold__sum--${tone}` : ''">{{ summary }}</span>
      <i class="h5-fold__caret" aria-hidden="true" />
    </button>
    <div v-show="open" class="h5-fold__body">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'H5FoldCard' })

defineProps({
  /** 标题行文字；需要放单号/标签等结构化内容时改用 #head 插槽 */
  title: { type: String, default: '' },
  /** 收起态摘要，展开后自动让位给正文（避免与正文里的同一数字重复显示） */
  summary: { type: String, default: '' },
  /** 摘要语气：''（次要灰）| 'warn'（橙）| 'err'（红，用于表单待补项） */
  tone: { type: String, default: '' },
  open: { type: Boolean, default: false },
})

defineEmits(['update:open'])
</script>
