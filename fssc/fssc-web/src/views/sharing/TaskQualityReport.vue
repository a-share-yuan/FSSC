<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="quality-report">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">{{ $t('sharing.qreport.pageTitle') }}</span>
          <div class="toolbar">
            <span class="tb-label">{{ $t('sharing.qreport.labWindow') }}</span>
            <el-select v-model="days" size="small" style="width: 120px" @change="reloadAll">
              <el-option :value="7" :label="$t('sharing.qreport.optLast7')" />
              <el-option :value="30" :label="$t('sharing.qreport.optLast30')" />
              <el-option :value="90" :label="$t('sharing.qreport.optLast90')" />
            </el-select>
            <el-button size="small" @click="reloadAll">{{ $t('common.refresh') }}</el-button>
            <el-tag v-if="summary?.note" type="info" size="small" effect="light">{{ $t('sharing.qreport.tagCaliber') }}</el-tag>
          </div>
        </div>
      </template>

      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane :label="$t('sharing.qreport.tabSummary')" name="summary">
          <div class="stat-row">
            <div class="stat-card">
              <div class="stat-val">{{ summary?.handledCount ?? ((summary?.approveCount || 0) + (summary?.rejectCount || 0)) }}</div>
              <div class="stat-label">{{ $t('sharing.qreport.statActions') }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-val">{{ formatPercent(summary?.rejectRate, false) }}</div>
              <div class="stat-label">{{ $t('sharing.qreport.statRejectRate') }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-val">{{ formatPercent(summary?.firstPassRate, false) }}</div>
              <div class="stat-label">{{ $t('sharing.qreport.statFirstPass') }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-val">{{ summary?.avgDwellMinutes ?? '-' }}</div>
              <div class="stat-label">{{ $t('sharing.qreport.statAvgDwell') }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-val">{{ summary?.instanceCount ?? 0 }}</div>
              <div class="stat-label">{{ $t('sharing.qreport.statInstances') }}</div>
            </div>
          </div>
          <el-alert v-if="summary?.note" :title="summary.note" type="info" :closable="false" show-icon style="margin-top: 10px" />
          <el-divider content-position="left">{{ $t('sharing.qreport.secTopReasons') }}</el-divider>
          <el-table :data="summary?.topRejectReasons || []" size="small" stripe border max-height="320" :empty-text="$t('sharing.qreport.emptyNoReason')">
            <el-table-column prop="reason" :label="$t('sharing.qreport.tabReason')" min-width="220" show-overflow-tooltip />
            <el-table-column prop="count" :label="$t('sharing.qreport.colCount')" width="100" align="right" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="$t('sharing.qreport.tabByNode')" name="nodes">
          <el-table :data="nodeRows" v-loading="nodeLoading" size="small" stripe border max-height="520" :empty-text="$t('sharing.qreport.emptyNoData')">
            <el-table-column prop="nodeName" :label="$t('sharing.common.colNode')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="approveCount" :label="$t('field.approve')" width="90" align="right" />
            <el-table-column prop="rejectCount" :label="$t('sharing.qreport.colRejected')" width="90" align="right" />
            <el-table-column prop="handledCount" :label="$t('sharing.qreport.colHandled')" width="90" align="right" />
            <el-table-column :label="$t('sharing.qreport.statRejectRate')" width="100" align="right">
              <template #default="{ row }">{{ formatPercent(row.rejectRate, false) }}</template>
            </el-table-column>
            <el-table-column prop="avgDwellMinutes" :label="$t('sharing.qreport.colAvgDwell')" width="130" align="right" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="$t('sharing.qreport.tabByHandler')" name="handlers">
          <el-table :data="handlerRows" v-loading="handlerLoading" size="small" stripe border max-height="520" :empty-text="$t('sharing.qreport.emptyNoData')">
            <el-table-column prop="operUserName" :label="$t('sharing.qreport.colHandler')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="approveCount" :label="$t('field.approve')" width="90" align="right" />
            <el-table-column prop="rejectCount" :label="$t('sharing.qreport.colRejected')" width="90" align="right" />
            <el-table-column prop="handledCount" :label="$t('sharing.qreport.colHandled')" width="90" align="right" />
            <el-table-column :label="$t('sharing.qreport.statRejectRate')" width="100" align="right">
              <template #default="{ row }">{{ formatPercent(row.rejectRate, false) }}</template>
            </el-table-column>
            <el-table-column prop="avgDwellMinutes" :label="$t('sharing.qreport.colAvgDwell')" width="130" align="right" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="$t('sharing.qreport.tabReason')" name="reasons">
          <el-form :inline="true" size="small" style="margin-bottom: 10px">
            <el-form-item :label="$t('sharing.common.colNodeKey')"><el-input v-model="reasonQ.nodeKey" clearable :placeholder="$t('sharing.common.colAny')" style="width: 180px" @keyup.enter="loadReasons" /></el-form-item>
            <el-form-item><el-button type="primary" @click="loadReasons">{{ $t('common.search') }}</el-button></el-form-item>
          </el-form>
          <el-table :data="reasonRows" v-loading="reasonLoading" size="small" stripe border max-height="480" :empty-text="$t('sharing.qreport.emptyNoReason')">
            <el-table-column prop="reason" :label="$t('sharing.qreport.colReasonBucketRaw')" min-width="240" show-overflow-tooltip />
            <el-table-column prop="count" :label="$t('sharing.qreport.colCount')" width="120" align="right" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="$t('sharing.qreport.tabDetail')" name="detail">
          <el-form :inline="true" size="small" style="margin-bottom: 10px">
            <el-form-item :label="$t('sharing.common.colNodeKey')"><el-input v-model="detailQ.nodeKey" clearable :placeholder="$t('sharing.common.colAny')" style="width: 160px" @keyup.enter="detailReset" /></el-form-item>
            <el-form-item :label="$t('sharing.common.colKeyword')"><el-input v-model="detailQ.keyword" clearable :placeholder="$t('sharing.qreport.phBillNoComment')" style="width: 180px" @keyup.enter="detailReset" /></el-form-item>
            <el-form-item><el-button type="primary" @click="detailReset">{{ $t('common.search') }}</el-button></el-form-item>
          </el-form>
          <el-table :data="detailRows" v-loading="detailLoading" size="small" stripe border max-height="480" :empty-text="$t('sharing.qreport.emptyNoDetail')">
            <el-table-column prop="bizType" :label="$t('field.billType')" width="120" :formatter="(row, col, val) => bizTypeLabel(val)" />
            <el-table-column prop="nodeName" :label="$t('sharing.common.colNode')" width="150" show-overflow-tooltip />
            <el-table-column prop="operUserName" :label="$t('sharing.qreport.colHandler')" width="110" />
            <el-table-column prop="reasonBucket" :label="$t('sharing.qreport.colReasonBucket')" width="140" show-overflow-tooltip />
            <el-table-column prop="comment" :label="$t('sharing.qreport.colComment')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="durationMinutes" :label="$t('sharing.qreport.colDwell')" width="100" align="right" />
            <el-table-column prop="operTime" :label="$t('sharing.qreport.colOperTime')" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
          </el-table>
          <TablePager :pager="detailPager" @change="loadDetail" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { bizTypeLabel } from '@/constants/enums'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { wfOpsApi } from '../../api'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { formatDateTime, formatPercent } from '@/utils/format'

/**
 * 任务质量报表（G3）：数据源 WfSharedOpsController /api/tenant/wf/ops/quality/**（权限 wf:instance:list）。
 * 口径见后端 TaskQualityReportService：批退 = wf_task_history.action='REJECT'。
 */
const days = ref(30)
const tab = ref('summary')
const loadedTabs = new Set()

const summary = ref(null)
const nodeRows = ref([])
const nodeLoading = ref(false)
const handlerRows = ref([])
const handlerLoading = ref(false)
const reasonQ = reactive({ nodeKey: '' })
const reasonRows = ref([])
const reasonLoading = ref(false)
const detailQ = reactive({ nodeKey: '', keyword: '' })
const detailPager = useTablePager({ mode: 'server', storageKey: 'sharing_ops_quality_reject' })
const { rows: detailRows, loading: detailLoading } = detailPager

async function loadSummary() {
  try { summary.value = await wfOpsApi.qualitySummary(days.value) } catch (e) { ElMessage.error(e.message) }
}
async function loadNodes() {
  nodeLoading.value = true
  try { nodeRows.value = await wfOpsApi.qualityByNode(days.value) } catch (e) { ElMessage.error(e.message) } finally { nodeLoading.value = false }
}
async function loadHandlers() {
  handlerLoading.value = true
  try { handlerRows.value = await wfOpsApi.qualityByHandler(days.value) } catch (e) { ElMessage.error(e.message) } finally { handlerLoading.value = false }
}
async function loadReasons() {
  reasonLoading.value = true
  try { reasonRows.value = await wfOpsApi.qualityRejectReasons({ days: days.value, nodeKey: reasonQ.nodeKey || undefined }) } catch (e) { ElMessage.error(e.message) } finally { reasonLoading.value = false }
}
function detailReset() { detailPager.resetPage(); loadDetail() }
async function loadDetail() {
  await detailPager.load(async (p) => {
    const r = await wfOpsApi.qualityRejectPage({
      pageNum: p.pageNum,
      pageSize: p.pageSize,
      days: days.value,
      nodeKey: detailQ.nodeKey || undefined,
      keyword: detailQ.keyword || undefined,
    })
    return { list: r?.list || [], total: r?.total || 0 }
  })
}

function reloadAll() {
  loadedTabs.clear()
  loadedTabs.add(tab.value)
  if (tab.value === 'summary') loadSummary()
  if (tab.value === 'nodes') loadNodes()
  if (tab.value === 'handlers') loadHandlers()
  if (tab.value === 'reasons') loadReasons()
  if (tab.value === 'detail') detailReset()
}

function onTabChange(name) {
  if (loadedTabs.has(name)) return
  loadedTabs.add(name)
  if (name === 'nodes') loadNodes()
  if (name === 'handlers') loadHandlers()
  if (name === 'reasons') loadReasons()
  if (name === 'detail') loadDetail()
}

onMounted(() => { loadedTabs.add('summary'); loadSummary() })
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.header-title { font-size: 15px; font-weight: 600; color: #303133; }
.toolbar { display: flex; gap: 8px; align-items: center; }
.tb-label { font-size: 12px; color: #909399; }
.stat-row { display: flex; gap: 12px; flex-wrap: wrap; }
.stat-card { flex: 1 1 150px; background: #f5f7fa; border-radius: 6px; padding: 14px; text-align: center; }
.stat-val { font-size: 20px; font-weight: 700; color: #303133; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
</style>