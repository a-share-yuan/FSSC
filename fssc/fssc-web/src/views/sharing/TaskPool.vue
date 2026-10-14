<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center">
        <b>任务池管理</b>
        <el-tag v-if="overdueCount" type="danger" size="small" effect="light">本页逾期 {{ overdueCount }}</el-tag>
        <el-select v-model="filter.poolStatus" placeholder="任务状态" clearable style="width: 150px" @change="load">
          <el-option label="待认领" value="WAITING" />
          <el-option label="已分派" value="RECEIVED" />
          <el-option label="已办结" value="FINISHED" />
          <!-- R5-D-236：未激活（实物未签收）任务此前在池中不可见也不可筛，现按 activeFlag='N' 落参过滤 -->
          <el-option label="未激活（待实物签收）" value="INACTIVE" />
        </el-select>
        <el-select v-model="filter.taskType" placeholder="作业项" clearable style="width: 130px" @change="load">
          <el-option label="审核" value="AUDIT" />
          <el-option label="记账" value="BOOKKEEP" />
          <el-option label="付款" value="PAY" />
        </el-select>
        <el-input v-model="filter.keyword" placeholder="单号/标题" clearable style="width: 200px" @keyup.enter="load" />
        <el-button type="primary" size="small" @click="load">查询</el-button>
        <el-button v-if="filter.poolStatus !== 'WAITING'" size="small" @click="filter.poolStatus = 'WAITING'; load()">查看待认领</el-button>
        <!-- P1-4 分组 + 批量抢单 -->
        <el-select v-model="groupBy" placeholder="分组" clearable size="small" style="width: 120px" @change="applyGroup">
          <el-option label="按作业项" value="taskType" />
          <el-option label="按单据类型" value="billType" />
          <el-option label="按状态" value="poolStatus" />
        </el-select>
        <el-button v-if="selRows.length" type="success" size="small" @click="batchGrabVis = true">
          批量抢单({{ selRows.length }})
        </el-button>
        <!-- R5-D-246：列表导出归位到工具栏（原先错挂在「重新分配」弹窗页脚，且导出内容是凭证行） -->
        <el-button size="small" @click="exportCsv">导出 CSV</el-button>
        <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table class="cm-target-taskpool" :data="rows" size="small" stripe v-else max-height="520" :row-class-name="slaRowClass" @selection-change="handleSelectionChange" @row-dblclick="showBillDetail">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" label="单号" width="140"  sortable>
        <template #default="{ row }">
          <el-link v-if="row.billId" type="primary" @click="showBillDetail(row)">{{ row.billNo }}</el-link>
          <span v-else>{{ row.billNo || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="70">
        <template #default="{ row }">{{ docTypeLabel(row.billType) }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip  sortable />
      <el-table-column label="金额" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="单据状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.billStatus" :type="billStatusType(row.billStatus)" size="small" effect="plain">{{ billStatusLabel(row.billStatus) }}</el-tag>
          <span v-else style="color: #c0c4cc; font-size: 12px">—</span>
        </template>
      </el-table-column>
      <el-table-column label="作业项" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.chainTotal > 1" size="small" :type="taskTypeTag(row.taskType)" effect="plain">
            {{ taskTypeLabel(row.taskType) }} {{ row.chainSeq }}/{{ row.chainTotal }}
          </el-tag>
          <el-tag v-else size="small" :type="taskTypeTag(row.taskType)" effect="plain">{{ taskTypeLabel(row.taskType) }}</el-tag>
          <el-tag v-if="row.activeFlag === 'N'" size="small" type="info" style="margin-left:4px">未激活</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="stType(row.poolStatus)" size="small">{{ stLabel(row.poolStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="认领人" width="100">
        <template #default="{ row }">{{ userName(row.assigneeId) }}</template>
      </el-table-column>
      <el-table-column prop="receiveTime" label="认领时间" width="160"  sortable :formatter="(r, c, v) => formatDateTime(v)" />
      <el-table-column prop="slaTime" label="SLA 截止" width="180" sortable>
        <template #default="{ row }">
          <span v-if="!row.slaTime" style="color: #c0c4cc; font-size: 12px">—</span>
          <template v-else>
            <span :class="{ 'sla-overdue-text': isSlaOverdue(row) }">{{ formatDateTime(row.slaTime) }}</span>
            <el-tag v-if="isSlaOverdue(row)" type="danger" size="small" style="margin-left:4px">逾期</el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160"  sortable :formatter="(r, c, v) => formatDateTime(v)" />
      <el-table-column label="操作" width="260" fixed="right">
        <!-- 共享任务池是「作业员抢单/办理」视角，不承载流程审批（那是「我的待办」的职责）。
             原先 InlineActions 上挂的 approve/reject 实际触发的是「批量操作」函数（忽略行参数），已移除。 -->
        <template #default="{ row }">
          <InlineActions :visible="true" @view="preview(row)" :show-trace="true" @trace="openTrace(row)" />
          <el-button v-if="row.poolStatus === 'WAITING' && row.activeFlag === 'Y'" v-prevent-double-click link type="success" size="small" @click="grab(row)">抢单</el-button>
          <el-button v-if="row.poolStatus === 'RECEIVED'" v-prevent-double-click link type="warning" size="small" @click="withdraw(row)">撤回</el-button>
          <el-button v-if="row.poolStatus !== 'FINISHED'" link type="primary" size="small" @click="openReassign(row)">重新分配</el-button>
          <el-button link size="small" @click="preview(row)">凭证</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 10px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total" :page-size="filter.pageSize" :current-page="filter.pageNum"
      @current-change="(n) => { filter.pageNum = n; load() }"
    />

    <!-- 重新分配：选择作业员 -->
    <el-dialog v-model="reassignVis" title="重新分配" width="420px">
      <el-form ref="reassignFormRef" :model="reassignForm" :rules="reassignFormRules" label-width="90px">
        <el-form-item label="当前任务">{{ current?.billNo }}（{{ current?.title }}）</el-form-item>
<el-form-item label="分配给人" prop="assigneeId">
          <UserSelect v-model="reassignForm.assigneeId" :users="users" placeholder="请选择作业员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reassignVis = false">取消</el-button>
        <el-button type="primary" v-prevent-double-click @click="doReassign">确认分配</el-button>
      </template>
    </el-dialog>

    <!-- 凭证预览 -->
    <el-dialog v-model="voucherVis" title="凭证预览" width="640px" destroy-on-close>
      <el-table :data="voucherLines" size="small" border max-height="360" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="lineNo" label="行号" width="60"  sortable />
        <el-table-column prop="summary" label="摘要" min-width="120"  sortable />
        <el-table-column prop="subjectCode" label="科目编码" width="100"  sortable />
        <el-table-column prop="subjectName" label="科目名称" min-width="120"  sortable />
        <el-table-column prop="direction" label="方向" width="60"  sortable :formatter="(row, col, val) => directionLabel(val)" />
        <el-table-column prop="amount" label="金额" width="100" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.amount) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!voucherLines.length" description="该任务未生成凭证" />
    </el-dialog>

    <!-- P1-4 批量抢单弹窗 -->
    <el-dialog v-model="batchGrabVis" title="批量抢单" width="480px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        将认领选中的 <b>{{ selRows.length }}</b> 条任务。只有「待认领」状态的任务可被抢单，其余会逐条列明失败原因。
      </el-alert>
      <el-table :data="selRows" size="small" border max-height="200" style="margin-bottom: 12px">
        <el-table-column prop="billNo" label="单号" width="140" />
        <el-table-column prop="title" label="标题" min-width="120" show-overflow-tooltip />
        <el-table-column label="作业项" width="80">
          <template #default="{ row }">{{ taskTypeLabel(row.taskType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="stType(row.poolStatus)" size="small">{{ stLabel(row.poolStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="batchGrabVis = false">取消</el-button>
        <el-button type="primary" @click="doBatchGrab" :loading="batchLoading">确认抢单</el-button>
      </template>
    </el-dialog>

    <!-- 单据详情：双击行 / 点击单号均在当前页展示，不跳转修改页 -->
    <el-dialog v-model="detailVis" :title="`单据详情 ${detailRow?.billNo || ''}`" width="560px" destroy-on-close>
      <el-descriptions v-if="detailRow" :column="2" border size="small">
        <el-descriptions-item label="单号">{{ detailRow.billNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="单据类型">{{ docTypeLabel(detailRow.billType) }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detailRow.title || '—' }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ fmt(detailRow.amount) }}</el-descriptions-item>
        <el-descriptions-item label="单据状态">
          <el-tag v-if="detailRow.billStatus" :type="billStatusType(detailRow.billStatus)" size="small" effect="plain">{{ billStatusLabel(detailRow.billStatus) }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="作业项">
          <el-tag size="small" :type="taskTypeTag(detailRow.taskType)" effect="plain">{{ taskTypeLabel(detailRow.taskType) }}{{ detailRow.chainTotal > 1 ? ` ${detailRow.chainSeq}/${detailRow.chainTotal}` : '' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag :type="stType(detailRow.poolStatus)" size="small">{{ stLabel(detailRow.poolStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="认领人">{{ userName(detailRow.assigneeId) }}</el-descriptions-item>
        <el-descriptions-item label="认领时间">{{ formatDateTime(detailRow.receiveTime) || '—' }}</el-descriptions-item>
        <el-descriptions-item label="SLA 截止">
          <span :class="{ 'sla-overdue-text': isSlaOverdue(detailRow) }">{{ formatDateTime(detailRow.slaTime) || '—' }}</span>
          <el-tag v-if="isSlaOverdue(detailRow)" type="danger" size="small" style="margin-left:4px">逾期</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detailRow.createTime) || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVis = false">关闭</el-button>
        <el-button type="primary" @click="goBill(detailRow)">前往业务页</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />

  <CoachMark ref="taskPoolCm" :guide-key="_guideKey" :selector="'.cm-target-taskpool'" title="任务池" text="共享任务池汇总所有共享中心的待处理任务，支持按类型筛选和领取。" />
</template>

<script setup>

import { onMounted, reactive, ref, computed, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime } from '@/utils/format'
import { sharingApi, userApi } from '../../api'
import { statusLabel as billStatusLabel, statusType as billStatusType } from '../../constants/billStatus'
import { docTypeLabel, taskTypeLabel, directionLabel } from '@/constants/enums'
import { goBizPage } from '@/utils/bizEntry'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import CoachMark from '@/components/CoachMark.vue'
import InlineActions from '@/components/InlineActions.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => location.reload(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})
const taskPoolCm = ref(null)

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.billId || row.id
  traceBizType.value = row.billType || null
  traceVisible.value = true
}

// 单据详情：双击行 / 点击单号均在当前页弹窗展示，不跳转修改页
const detailVis = ref(false)
const detailRow = ref(null)
function showBillDetail(row) {
  detailRow.value = row
  detailVis.value = true
}

const router = useRouter()
const route = useRoute()

const selRows = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
// 默认只显示待认领任务（SAP/用友标准做法：任务池默认展示待处理任务）
const filter = reactive({ pageNum: 1, pageSize: 20, poolStatus: 'WAITING', taskType: '', keyword: '' })
const users = ref([])
const reassignVis = ref(false)
const current = ref(null)
const reassignFormRef = ref(null)
const reassignForm = reactive({ assigneeId: null })
const voucherVis = ref(false)
const voucherLines = ref([])

// P1-4 分组 + 批量抢单
const groupBy = ref('')
const batchGrabVis = ref(false)
const batchLoading = ref(false)

const reassignFormRules = reactive({
  assigneeId: commonRules.select('请选择作业员'),
})

function applyGroup() {
  if (!groupBy.value) return
  rows.value.sort((a, b) => {
    const va = a[groupBy.value] || ''
    const vb = b[groupBy.value] || ''
    return String(va).localeCompare(String(vb))
  })
}

// 只做抢单。任务池行来自 op_audit_task，没有 task_id / 流程实例列，
// 在这里调 wfApi.approve 恒会失败——审批要回到「审批工作台」的待办列表做。
// 两张账打通后再考虑在本页加审批入口（见 流程节点指定处理人改造方案_20260922.md P2）。
async function doBatchGrab() {
  if (!selRows.value.length) return
  batchLoading.value = true
  const fails = []
  let ok = 0
  for (const [i, row] of selRows.value.entries()) {
    const name = row.billNo || row.title || `第 ${i + 1} 条`
    try {
      if (row.poolStatus !== 'WAITING') throw new Error('任务已被他人认领，请刷新后重选')
      await sharingApi.receive(row.id)
      ok++
    } catch (e) { fails.push(`${name}：${e?.message || '操作失败'}`) }
  }
  batchLoading.value = false
  if (!fails.length) {
    batchGrabVis.value = false
    ElMessage.success(`批量抢单完成：成功认领 ${ok} 条`)
  } else {
    // 有失败时保留弹窗与已选行，逐条列明原因，改完可直接重试
    ElMessage.warning(`批量抢单完成：成功 ${ok} 条，失败 ${fails.length} 条，明细见弹窗`)
    const lines = fails.map((l, i) => `${i + 1}. ${l}`)
    ElMessageBox.alert(
      h('div', null, lines.map((l) => h('div', { style: 'margin:2px 0;line-height:1.6' }, l))),
      `批量抢单失败明细（${fails.length} 条）`,
      { type: 'error', confirmButtonText: '知道了' },
    ).catch(() => {})
  }
  load()
}

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
function taskTypeTag(t) {
  return t === 'AUDIT' ? 'primary' : t === 'PAY' ? 'warning' : 'success'
}
function stLabel(s) {
  return { WAITING: '待认领', RECEIVED: '已分派', FINISHED: '已办结' }[s] || s
}
function stType(s) {
  return s === 'FINISHED' ? 'success' : s === 'RECEIVED' ? 'warning' : 'info'
}
function userName(id) {
  if (!id) return '—'
  const u = users.value.find((x) => x.id === id)
  return u ? (u.nickName || u.userName) : '#' + id
}

// SLA 逾期口径与流程侧「超时未办」一致：未办结 + 已过截止时间
function isSlaOverdue(row) {
  if (!row.slaTime || row.poolStatus === 'FINISHED') return false
  const t = new Date(String(row.slaTime).replace(' ', 'T')).getTime()
  return Number.isFinite(t) && t < Date.now()
}
function slaRowClass({ row }) {
  return isSlaOverdue(row) ? 'sla-overdue-row' : ''
}
const overdueCount = computed(() => rows.value.filter(isSlaOverdue).length)

// 跳转到单据详情（统一走共享 bizEntry：全量类型映射 + 携带单号 query 定位）
function goBill(row) {
  const ok = goBizPage(router, row)
  if (!ok) ElMessage.warning(`暂不支持 ${row.billType || '该类型'} 单据的直达跳转`)
}

async function load() {
  loading.value = true
  try {
    // R5-D-236：INACTIVE 是前端伪状态——落参时换成 activeFlag='N' 并清空 poolStatus；
    // 其余状态显式清空 activeFlag，避免上一次筛选残留导致列表莫名变空。
    const params = filter.poolStatus === 'INACTIVE'
      ? { ...filter, poolStatus: '', activeFlag: 'N' }
      : { ...filter, activeFlag: '' }
    const r = await sharingApi.manage(params)
    rows.value = r.list || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

// 抢单：将待认领任务认领到自己名下
function grab(row) {
  ElMessageBox.confirm(`确认抢单 ${row.billNo}（${row.title}）？`, '抢单', { type: 'info', confirmButtonText: '确认抢单' })
    .then(async () => {
      try {
        await sharingApi.receive(row.id)
        ElMessage.success('抢单成功')
        load()
      } catch (e) { ElMessage.error(e.message) }
    }).catch(() => {})
}

function withdraw(row) {
  ElMessageBox.confirm(`撤回任务 ${row.billNo}？将退回任务池待认领`, '撤回', { type: 'warning' })
    .then(async () => {
      await sharingApi.withdraw(row.id)
      ElMessage.success('已撤回')
      load()
    }).catch(() => {})
}

async function openReassign(row) {
  if (!users.value.length) users.value = (await userApi.page({ pageNum: 1, pageSize: 200 })).list || []
  current.value = row
  reassignForm.assigneeId = row.assigneeId || null
  reassignVis.value = true
}

async function doReassign() {
  if (!(await validateForms([reassignFormRef]))) return
  await sharingApi.reassign(current.value.id, reassignForm.assigneeId)
  ElMessage.success('已重新分配')
  reassignVis.value = false
  load()
}

async function preview(row) {
  try {
    const vp = await sharingApi.voucherPage({ pageNum: 1, pageSize: 200 })
    const v = (vp.list || []).find((x) => String(x.billId) === String(row.billId))
    if (!v) { voucherLines.value = []; voucherVis.value = true; return }
    voucherLines.value = await sharingApi.voucherLines(v.id)
    voucherVis.value = true
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(async () => {
  // 支持从工作台等待办入口携带 keyword（单号）直达定位
  if (route.query.keyword) filter.keyword = String(route.query.keyword)
  if (route.query.poolStatus) filter.poolStatus = String(route.query.poolStatus)
  load()
  try { users.value = (await userApi.page({ pageNum: 1, pageSize: 200 })).list || [] } catch (e) { /* ignore */ }
})

function exportCsv() {
  // R5-D-246：原实现导出的是 voucherLines（凭证行）、入口还错挂在「重新分配」弹窗页脚；
  // 现与「导出选中」并列在工具栏，列集对齐当前任务池表格。
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'billNo', label: '单号' }, { prop: 'title', label: '标题' }, { prop: 'amount', label: '金额' }, { prop: 'billStatus', label: '单据状态' }, { prop: 'taskType', label: '作业项' }, { prop: 'poolStatus', label: '状态' }, { prop: 'receiveTime', label: '认领时间' }, { prop: 'createTime', label: '创建时间' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `TaskPool_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning('请先勾选要导出的记录'); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning('无法获取列信息'); return }
  const header = keys.join(',')
  const lines = selRows.value.map((row) => keys.map((k) => {
    const v = row[k]
    if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `TaskPool_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}


// R4-06: Show guide on first visit
const _guideKey = 'fssc_guide_taskPoolCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => taskPoolCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
/* SLA 逾期行整行标红（同 借款台账逾期口径），使抢单/分派时先看账龄再看单号 */
:deep(.sla-overdue-row) td.el-table__cell { background-color: #fef0f0 !important; }
.sla-overdue-text { color: #f56c6c; font-weight: 600; }
</style>
