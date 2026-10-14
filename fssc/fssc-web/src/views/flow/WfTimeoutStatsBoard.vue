<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="timeout-board">
    <el-card shadow="never" class="head-card">
      <template #header>
        <div class="toolbar">
          <span>超时升级统计</span>
          <div class="ops">
            <el-button size="small" @click="loadAll">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="stat-row">
        <div class="stat">
          <div class="stat-num ok">{{ cell(stats.withinSlaCount) }}</div>
          <div class="stat-label">SLA 内数量</div>
        </div>
        <div class="stat">
          <div class="stat-num">{{ cell(stats.avgProcessingHours) }}</div>
          <div class="stat-label">平均处理时长（小时）</div>
        </div>
        <div class="stat">
          <div class="stat-num">{{ cell(stats.totalPending) }}</div>
          <div class="stat-label">待办总数</div>
        </div>
        <div class="stat">
          <div class="stat-num danger">{{ cell(stats.overdueCount) }}</div>
          <div class="stat-label">超时数量</div>
        </div>
      </div>
      <div v-if="statsFailed" class="hint">统计取数失败，上方数字留空；可点「刷新」重试。</div>
      <div v-else class="hint">
        口径：待办总数＝当前全部 PENDING 任务；超时＝账龄已达该节点配置的时限（未配时限的节点一律计为 SLA 内）。
      </div>
    </el-card>

    <el-card shadow="never" class="body-card">
      <template #header>
        <div class="toolbar">
          <span>超时待办清单</span>
          <div class="ops">
            <el-button size="small" :disabled="!rows.length" @click="doExport">导出当前页 CSV</el-button>
            <el-button size="small" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="listLoading" size="small" border stripe
                :empty-text="listFailed ? '清单取数失败，请点「刷新」重试' : '暂无待办任务'">
        <el-table-column prop="title" label="任务标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="nodeName" label="当前节点" width="150" show-overflow-tooltip />
        <el-table-column label="超时判定" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="Number(row.overdueHours) > 0 ? 'danger' : 'success'">
              {{ Number(row.overdueHours) > 0 ? '已超时' : 'SLA 内' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="overdueHours" label="超时时长（小时）" width="130" align="right">
          <template #default="{ row }">{{ Number(row.overdueHours) > 0 ? row.overdueHours : '—' }}</template>
        </el-table-column>
        <el-table-column label="超时动作" width="150">
          <template #default="{ row }">{{ actionLabel(row.timeoutAction) }}</template>
        </el-table-column>
        <el-table-column label="到达时间" width="150">
          <template #default="{ row }">{{ row.createTime ? formatDateTime(row.createTime) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="assigneeId" label="处理人 ID" width="110" align="right">
          <template #default="{ row }">{{ row.assigneeId ?? '未签收' }}</template>
        </el-table-column>
        <el-table-column prop="taskId" label="任务 ID" width="180" show-overflow-tooltip />
      </el-table>

      <el-pagination class="pager" layout="total, sizes, prev, pager, next" background
                     :total="total" :current-page="pageNum" :page-size="pageSize"
                     :page-sizes="[10, 20, 50, 100]"
                     @current-change="onPageChange" @size-change="onSizeChange" />
      <div class="hint">口径：该清单按 PENDING 待办分页原样返回，超时时长 0 ＝ 仍在节点时限内（不是"没有数据"）。</div>
    </el-card>

    <el-card shadow="never" class="body-card">
      <template #header>
        <div class="toolbar">
          <span>节点超时配置（只读）</span>
          <div class="ops">
            <el-input v-model="modelKey" size="small" style="width: 220px" placeholder="流程标识 modelKey" clearable
                      @keyup.enter="loadConfig" />
            <el-button size="small" type="primary" :loading="cfgLoading" @click="loadConfig">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="cfgRows" v-loading="cfgLoading" size="small" border stripe
                :empty-text="cfgEmptyText">
        <el-table-column prop="nodeKey" label="节点 ID" min-width="160" show-overflow-tooltip />
        <el-table-column prop="nodeName" label="节点名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="时限（小时）" width="120" align="right">
          <template #default="{ row }">{{ row.timeoutHours > 0 ? row.timeoutHours : '不限' }}</template>
        </el-table-column>
        <el-table-column label="超时动作" width="160">
          <template #default="{ row }">{{ actionLabel(row.timeoutAction) }}</template>
        </el-table-column>
      </el-table>
      <div class="hint">时限为「不限」（未配或 0）的节点不参与超时判定，上方清单会把它显示成 SLA 内。</div>
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

// 后端返回空数组＝真没有；取数失败＝另一回事，文案分开，避免把故障说成"无数据"
const cfgEmptyText = computed(() => {
  if (cfgFailed.value) return '配置取数失败，请重试'
  return cfgQueried.value ? '该流程标识下没有节点超时配置' : '输入流程标识后查询'
})

async function loadConfig() {
  const key = String(modelKey.value || '').trim()
  if (!key) {
    ElMessage.warning('请先输入流程标识')
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

const EXPORT_COLS = [
  { prop: 'title', label: '任务标题' }, { prop: 'nodeName', label: '当前节点' },
  { prop: 'overdueHours', label: '超时时长' }, { prop: 'timeoutAction', label: '超时动作' },
  { prop: 'createTime', label: '到达时间' }, { prop: 'assigneeId', label: '处理人ID' },
  { prop: 'taskId', label: '任务ID' },
]
function doExport() {
  exportCsv(rows.value.map((r) => ({ ...r, timeoutAction: actionLabel(r.timeoutAction), createTime: r.createTime ? formatDateTime(r.createTime) : '' })), EXPORT_COLS, 'WfTimeoutStats')
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
