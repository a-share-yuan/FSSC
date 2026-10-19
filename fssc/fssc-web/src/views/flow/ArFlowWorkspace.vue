<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="flow-workspace">
    <div class="ws-head">
      <h2 class="ws-title">{{ $t('flow.entryAr.arWorkspace') }}</h2>
      <span class="ws-sub">{{ $t('flow.entryAr.arWorkspaceDesc') }}</span>
    </div>
    <el-tabs v-model="tab" class="ws-tabs">
      <el-tab-pane :label="$t('flow.entryAr.tabServiceRequest')" name="claims" lazy><ArClaimBoard /></el-tab-pane>
      <el-tab-pane :label="$t('flow.entryAr.tabBillingChain')" name="billing" lazy><ArBillingBoard /></el-tab-pane>
      <el-tab-pane :label="$t('flow.entryAr.tabWriteoff')" name="writeoff" lazy><ArWriteoffBoard /></el-tab-pane>
      <el-tab-pane :label="$t('flow.entryAr.tabNote')" name="notes" lazy><ArNoteBoard /></el-tab-pane>
      <el-tab-pane :label="$t('flow.entryAr.tabBadDebt')" name="bad-debt" lazy><ArBadDebtBoard /></el-tab-pane>
      <el-tab-pane :label="$t('flow.entryAr.tabMonthClose')" name="month-close" lazy><CloseWorkbench scope="ar" /></el-tab-pane>
      <el-tab-pane :label="$t('flow.entryAr.tabReport')" name="reports" lazy><ArApReportBoard /></el-tab-pane>
      <el-tab-pane :label="$t('flow.entryAp.tabSupplyChain')" name="collab" lazy><ArApCollabBoard /></el-tab-pane>
      <el-tab-pane :label="$t('flow.entryAr.tabBaseConfig')" name="base-config" lazy><ArBaseConfigPage /></el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
// 方案 A（页面Tab化重构册 §甲）：蓝图 9 页收敛为一域一工作台，页内 tab 复用既有 Board 组件，
// 不新增流程/表/API。el-tab-pane lazy＝首次激活才挂载，避免 8 个 Board 并发取数（D-DEC-265 教训）。
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ArClaimBoard from './ArClaimBoard.vue'
import ArBillingBoard from './ArBillingBoard.vue'
import ArWriteoffBoard from './ArWriteoffBoard.vue'
import ArNoteBoard from './ArNoteBoard.vue'
import ArBadDebtBoard from './ArBadDebtBoard.vue'
import ArApReportBoard from './ArApReportBoard.vue'
import ArApCollabBoard from './ArApCollabBoard.vue'
import ArBaseConfigPage from './ArBaseConfigPage.vue'
import CloseWorkbench from './CloseWorkbench.vue'

const route = useRoute()
const router = useRouter()
const tab = ref(String(route.query.tab || 'claims'))
watch(tab, (v) => { router.replace({ query: { ...route.query, tab: v } }) })
</script>

<style scoped>
.ws-head { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
.ws-title { margin: 0; font-size: 18px; font-weight: 600; }
.ws-sub { color: #909399; font-size: 12px; }
</style>
