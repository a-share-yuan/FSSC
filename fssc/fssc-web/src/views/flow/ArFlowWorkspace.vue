<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="flow-workspace">
    <div class="ws-head">
      <h2 class="ws-title">应收工作台</h2>
      <span class="ws-sub">应收域统一入口：服务申请 · 开票链 · 核销 · 票据 · 坏账 · 报表 · 协同 · 基础配置（审批走「我的待办」，作业走「任务池」）</span>
    </div>
    <el-tabs v-model="tab" class="ws-tabs">
      <el-tab-pane label="应收服务申请" name="claims" lazy><ArClaimBoard /></el-tab-pane>
      <el-tab-pane label="开票链工作台" name="billing" lazy><ArBillingBoard /></el-tab-pane>
      <el-tab-pane label="核销工作台" name="writeoff" lazy><ArWriteoffBoard /></el-tab-pane>
      <el-tab-pane label="票据管理" name="notes" lazy><ArNoteBoard /></el-tab-pane>
      <el-tab-pane label="坏账管理" name="bad-debt" lazy><ArBadDebtBoard /></el-tab-pane>
      <el-tab-pane label="应收月结" name="month-close" lazy><CloseWorkbench scope="ar" /></el-tab-pane>
      <el-tab-pane label="应收统计报表" name="reports" lazy><ArApReportBoard /></el-tab-pane>
      <el-tab-pane label="产业链协同" name="collab" lazy><ArApCollabBoard /></el-tab-pane>
      <el-tab-pane label="汇率与核对容差" name="base-config" lazy><ArBaseConfigPage /></el-tab-pane>
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
