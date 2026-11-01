<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div :class="['expense-hub', { tabless: isSingle }]">
    <el-tabs v-model="activeTab">
      <!-- 发起单据 -->
      <el-tab-pane :label="$t('expense.shell.tabInitiated')" name="create" lazy>
        <ExpenseCreateTab ref="createTab" @submitted="onSubmitted" @saved="onSaved" />
      </el-tab-pane>

      <!-- 我的单据 -->
      <el-tab-pane :label="$t('expense.common.tabMyBills')" name="my" lazy>
        <ExpenseBillListTab ref="billListTab" @view-voucher="onViewVoucher" />
      </el-tab-pane>

      <!-- 附件查看器 -->
      <FileViewer ref="attachViewer" />
    </el-tabs>

    <!-- 凭证预览：以抽屉呈现，避免单页模式（tab 导航隐藏）下切到隐藏的凭证 tab 后无法返回 -->
    <el-drawer
      v-model="voucherDrawer"
      :title="$t('expense.shell.ttlVoucherPreview', { v1: selected?.voucherNo ? ' ' + selected.voucherNo : '' })"
      direction="rtl"
      size="760px"
      :destroy-on-close="true"
      @opened="onVoucherDrawerOpened"
    >
      <ExpenseVoucherTab ref="voucherTabRef" :selected="selected" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, provide, watch, nextTick, computed } from 'vue'
import FileViewer from '../../components/FileViewer.vue'
import ExpenseCreateTab from './ExpenseCreateTab.vue'
import ExpenseBillListTab from './ExpenseBillListTab.vue'
import ExpenseVoucherTab from './ExpenseVoucherTab.vue'

// P: tab 页拆分——由菜单深链进来时传入 tab，只显示该页并隐藏 tab 导航条（一 TAB = 一菜单）
const props = defineProps({ tab: { type: String, default: '' } })
const isSingle = computed(() => !!props.tab)

const activeTab = ref(props.tab || 'create')
const selected = ref({})
const attachViewer = ref(null)
const createTab = ref(null)
const billListTab = ref(null)
const voucherDrawer = ref(false)
const voucherTabRef = ref(null)

provide('attachViewer', attachViewer)

// 单页模式：提交后切到"我的单据"无意义（导航条已隐藏），保持原列表刷新逻辑即可
function onSubmitted() {
  activeTab.value = 'my'
}

function onSaved() {
  // 草稿保存后刷新单据列表
  if (billListTab.value) billListTab.value.loadBills()
}

/**
 * 「查看凭证」：原实现 emit('select-bill') → 父组件把 activeTab 切成隐藏的凭证 tab，
 * 在单页模式（/expense/my 即此场景，tab 导航条被隐藏）下用户会被困在凭证页且无法返回，
 * 且 Popper 参照元素随 tab 隐藏而失效、弹层回退到左上角盖住左侧菜单。
 * 弹出凭证抽屉，停留在当前列表，可随时关闭返回。
 */
function onViewVoucher(row) {
  selected.value = { ...row }
  voucherDrawer.value = true
}

function onVoucherDrawerOpened() {
  voucherTabRef.value?.loadVoucher()
}

// 切换到"我的单据"时自动刷新
watch(activeTab, async (val) => {
  if (val === 'my') {
    await nextTick()
    billListTab.value?.loadBills()
  }
})
</script>

<style scoped>
/* 一 TAB = 一菜单：从菜单深链进来（tab 命中）时隐藏 tab 导航条，只显示该页 */
.tabless :deep(.el-tabs__header) { display: none; }
</style>
