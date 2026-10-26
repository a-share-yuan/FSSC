<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="timeout-board">
    <el-card shadow="never" class="head-card">
      <template #header>
        <div class="toolbar">
          <span>{{ $t('flow.wfTimeout.title') }}</span>
          <div class="ops">
            <el-button size="small" @click="loadAll">{{ $t('common.refresh') }}</el-button>
          </div>
        </div>
      </template>

      <div class="stat-row">
        <div class="stat">
          <div class="stat-num ok">{{ cell(stats.withinSlaCount) }}</div>
          <div class="stat-label">{{ $t('flow.wfTimeout.cardWithinSla') }}</div>
        </div>
        <div class="stat">
          <div class="stat-num">{{ cell(stats.avgProcessingHours) }}</div>
          <div class="stat-label">{{ $t('flow.wfTimeout.cardAvgHours') }}</div>
        </div>
        <div class="stat">
          <div class="stat-num">{{ cell(stats.totalPending) }}</div>
          <div class="stat-label">{{ $t('flow.wfTimeout.cardTotalPending') }}</div>
        </div>
        <div class="stat">
          <div class="stat-num danger">{{ cell(stats.overdueCount) }}</div>
          <div class="stat-label">{{ $t('flow.wfTimeout.cardTimedOut') }}</div>
        </div>
      </div>
      <div v-if="statsFailed" class="hint">{{ $t('flow.wfTimeout.msgStatsFailed') }}</div>
      <div v-else class="hint">
        {{ $t('flow.wfTimeout.hintStatsBasis') }}</div>
    </el-card>

    <el-card shadow="never" class="body-card">
      <template #header>
        <div class="toolbar">
          <span>{{ $t('flow.wfTimeout.listTitle') }}</span>
          <div class="ops">
            <el-button size="small" :disabled="!rows.length" @click="doExport">{{ $t('flow.wfTimeout.exportPageCsv') }}</el-button>
            <el-button size="small" @click="loadList">{{ $t('common.refresh') }}</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="listLoading" size="small" border stripe
                :empty-text="listFailed ? $t('flow.wfTimeout.emptyFailed') : $t('flow.wfTimeout.emptyNone')">
        <el-table-column prop="title" :label="$t('flow.wfTimeout.colTaskTitle')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="nodeName" :label="$t('flow.wfTimeout.colCurrentNode')" width="150" show-overflow-tooltip />
        <el-table-column :label="$t('flow.wfTimeout.colSlaVerdict')" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="Number(row.overdueHours) > 0 ? 'danger' : 'success'">
              {{ Number(row.overdueHours) > 0 ? t('flow.wfTimeout.stTimedOut') : t('flow.wfTimeout.stWithinSla') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="overdueHours" :label="$t('flow.wfTimeout.colTimeoutHours')" width="130" align="right">
          <template #default="{ row }">{{ Number(row.overdueHours) > 0 ? row.overdueHours : '—' }}</template>
        </el-table-column>
        <el-table-column :label="$t('flow.wfTimeout.colTimeoutAction')" width="150">
          <template #default="{ row }">{{ actionLabel(row.timeoutAction) }}</template>
        </el-table-column>
        <el-table-column :label="$t('flow.wfTimeout.colArrivalTime')" width="150">
          <template #default="{ row }">{{ row.createTime ? formatDateTime(row.createTime) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="assigneeId" :label="$t('flow.wfTimeout.colHandlerId')" width="110" align="right">
          <template #default="{ row }">{{ row.assigneeId ?? t('flow.wfTimeout.stUnclaimed') }}</template>
        </el-table-column>
        <el-table-column prop="taskId" :label="$t('flow.wfTimeout.colTaskId')" width="180" show-overflow-tooltip />
      </el-table>

      <el-pagination class="pager" layout="total, sizes, prev, pager, next" background
                     :total="total" :current-page="pageNum" :page-size="pageSize"
                     :page-sizes="[10, 20, 50, 100]"
                     @current-change="onPageChange" @size-change="onSizeChange" />
      <div class="hint">{{ $t('flow.wfTimeout.hintListBasis') }}</div>
    </el-card>

    <el-card shadow="never" class="body-card">
      <template #header>
        <div class="toolbar">
          <span>{{ $t('flow.wfTimeout.cfgTitle') }}</span>
          <div class="ops">
            <el-input v-model="modelKey" size="small" style="width: 220px" :placeholder="$t('flow.wfTimeout.colModelKey')" clearable
                      @keyup.enter="loadConfig" />
            <el-button size="small" type="primary" :loading="cfgLoading" @click="loadConfig">{{ $t('common.search') }}</el-button>
          </div>
        </div>
      </template>

      <el-table :data="cfgRows" v-loading="cfgLoading" size="small" border stripe
                :empty-text="cfgEmptyText">
        <el-table-column prop="nodeKey" :label="$t('flow.wfTimeout.colNodeId')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="nodeName" :label="$t('flow.wfTimeout.colNodeName')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="$t('flow.wfTimeout.colTimeLimit')" width="120" align="right">
          <template #default="{ row }">{{ row.timeoutHours > 0 ? row.timeoutHours : t('flow.wfTimeout.stNoLimit') }}</template>
        </el-table-column>
        <el-table-column :label="$t('flow.wfTimeout.colTimeoutAction')" width="160">
          <template #default="{ row }">{{ actionLabel(row.timeoutAction) }}</template>
        </el-table-column>
      </el-table>
      <div class="hint">{{ $t('flow.wfTimeout.hintNoLimit') }}</div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * 超时升级统计（D-V4R-62：后端 /api/tenant/wf/timeout 三端点早有、界面无）。
 * 全部只读，字段逐一对齐 WfTimeoutController 组装的 Map key，不做二次加工。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { wfTimeoutApi } from '../../api'
import { TIMEOUT_ACTION } from '@/constants/enums'
import { formatDateTime } from '../../utils/format'
import { exportCsv } from '../../utils/csv'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const stats = reactive({ totalPending: null, overdueCount: null, withinSlaCount: null, avgProcessingHours: null })
const statsFailed = ref(false)
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const listLoading = ref(false)
const listFailed = ref(false)
const modelKey = ref('')
const cfgRows = ref([])
const cfgLoading = ref(false)
const cfgFailed = ref(false)
const cfgQueried = ref(false)

function cell(v) {
  return v === null || v === undefined || v === '' ? '—' : v
}

function actionLabel(v) {
  if (!v) return '—'
  return TIMEOUT_ACTION[v] || v
}

async function loadStats() {
  statsFailed.value = false
  try {
    const d = await wfTimeoutApi.stats({ silent: true }) || {}
    stats.totalPending = d.totalPending ?? null
    stats.overdueCount = d.overdueCount ?? null
    stats.withinSlaCount = d.withinSlaCount ?? null
    stats.avgProcessingHours = d.avgProcessingHours ?? null
  } catch {
    statsFailed.value = true
    stats.totalPending = stats.overdueCount = stats.withinSlaCount = stats.avgProcessingHours = null
  }
}

async function loadList() {
  listLoading.value = true
  listFailed.value = false
  try {
    const d = await wfTimeoutApi.overdueList({ pageNum: pageNum.value, pageSize: pageSize.value })
    rows.value = d?.list || []
    total.value = Number(d?.total) || 0
  } catch (e) {
    listFailed.value = true
    rows.value = []
    total.value = 0
    ElMessage.error(e.message)
  } finally {
    listLoading.value = false
  }
}

function onPageChange(n) {
  pageNum.value = Number(n) || 1
  loadList()
}

function onSizeChange(s) {
  pageSize.value = Number(s) || 20
  pageNum.value = 1
  loadList()
}

// 后端返回空数组＝真没有；查询失败＝另一回事，文案分开，避免把故障说成"无数据"
const cfgEmptyText = computed(() => {
  if (cfgFailed.value) return t('flow.wfTimeout.msgCfgFailed')
  return cfgQueried.value ? t('flow.wfTimeout.msgCfgEmpty') : t('flow.wfTimeout.hintQueryByKey')
})

async function loadConfig() {
  const key = String(modelKey.value || '').trim()
  if (!key) {
    ElMessage.warning(t('flow.wfTimeout.errNeedModelKey'))
    return
  }
  cfgLoading.value = true
  cfgFailed.value = false
  cfgQueried.value = true
  try {
    cfgRows.value = await wfTimeoutApi.config(key, { silent: true }) || []
  } catch (e) {
    cfgFailed.value = true
    cfgRows.value = []
    ElMessage.error(e.message)
  } finally {
    cfgLoading.value = false
  }
}

function loadAll() {
  loadStats()
  loadList()
}

const EXPORT_COLS = computed(() => ([
  { prop: 'title', label: t('flow.wfTimeout.colTaskTitle') }, { prop: 'nodeName', label: t('flow.wfTimeout.colCurrentNode') },
  { prop: 'overdueHours', label: t('flow.wfTimeout.csvTimeoutHours') }, { prop: 'timeoutAction', label: t('flow.wfTimeout.colTimeoutAction') },
  { prop: 'createTime', label: t('flow.wfTimeout.colArrivalTime') }, { prop: 'assigneeId', label: t('flow.wfTimeout.csvHandlerId') },
  { prop: 'taskId', label: t('flow.wfTimeout.csvTaskId') },
]))
function doExport() {
  exportCsv(rows.value.map((r) => ({ ...r, timeoutAction: actionLabel(r.timeoutAction), createTime: r.createTime ? formatDateTime(r.createTime) : '' })), EXPORT_COLS.value, 'WfTimeoutStats')
}

onMounted(loadAll)
</script>

<style scoped>
.head-card { margin-bottom: 12px; }
.body-card { margin-bottom: 12px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
.stat-row { display: flex; gap: 36px; flex-wrap: wrap; }
.stat { text-align: center; min-width: 120px; }
.stat-num { font-size: 24px; font-weight: 700; }
.stat-num.ok { color: #67c23a; }
.stat-num.danger { color: #f56c6c; }
.stat-label { font-size: 12px; color: #909399; margin-top: 2px; }
.hint { font-size: 12px; color: #909399; margin-top: 10px; }
.pager { margin-top: 10px; justify-content: flex-end; }
</style>
