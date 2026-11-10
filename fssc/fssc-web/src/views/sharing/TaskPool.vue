<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center">
        <b>{{ $t('sharing.taskPool.pageTitle') }}</b>
        <el-tag v-if="overdueCount" type="danger" size="small" effect="light">{{ $t('sharing.taskPool.tagPageOverdue', { v1: overdueCount }) }}</el-tag>
        <el-select v-model="filter.poolStatus" :placeholder="$t('sharing.taskPool.colTaskStatus')" clearable style="width: 192px" @change="load">
          <el-option :label="$t('sharing.taskPool.optWaiting')" value="WAITING" />
          <el-option :label="$t('sharing.taskPool.optAssigned')" value="RECEIVED" />
          <el-option :label="$t('sharing.taskPool.optFinished')" value="FINISHED" />
          <!-- 未激活（实物未签收）任务按 activeFlag='N' 落参过滤，否则池中不可见也不可筛 -->
          <el-option :label="$t('sharing.taskPool.optInactiveFilter')" value="INACTIVE" />
        </el-select>
        <el-select v-model="filter.taskType" :placeholder="$t('sharing.common.colWorkItem')" clearable style="width: 130px" @change="load">
          <el-option :label="$t('sharing.common.colAudit')" value="AUDIT" />
          <el-option :label="$t('sharing.common.colBookkeep')" value="BOOKKEEP" />
          <el-option :label="$t('sharing.common.colPay')" value="PAY" />
        </el-select>
        <el-input v-model="filter.keyword" :placeholder="$t('sharing.common.phBillNoTitle')" clearable style="width: 200px" @keyup.enter="load" />
        <el-button type="primary" size="small" @click="load">{{ $t('common.search') }}</el-button>
        <el-button v-if="filter.poolStatus !== 'WAITING'" size="small" @click="filter.poolStatus = 'WAITING'; load()">{{ $t('sharing.taskPool.btnViewWaiting') }}</el-button>
        <!-- 分组 + 批量抢单 -->
        <el-select v-model="groupBy" :placeholder="$t('sharing.taskPool.phGroupBy')" clearable size="small" style="width: 120px" @change="applyGroup">
          <el-option :label="$t('sharing.taskPool.optGroupWorkItem')" value="taskType" />
          <el-option :label="$t('sharing.taskPool.optGroupDocType')" value="billType" />
          <el-option :label="$t('sharing.taskPool.optGroupStatus')" value="poolStatus" />
        </el-select>
        <el-button v-if="selRows.length" type="success" size="small" @click="batchGrabVis = true">
          {{ $t('sharing.taskPool.btnBatchGrabN', { v1: selRows.length }) }}</el-button>
        <!-- 列表导出归位到工具栏（原先错挂在「重新分配」弹窗页脚，且导出内容是凭证行） -->
        <el-button size="small" @click="exportCsv">{{ $t('sharing.common.btnExportCsv') }}</el-button>
        <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('sharing.common.btnExportSelected', { v1: selRows.length }) }}</el-button>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table class="cm-target-taskpool" :data="rows" size="small" stripe v-else max-height="520" :row-class-name="slaRowClass" @selection-change="handleSelectionChange" @row-dblclick="showBillDetail">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" :label="$t('field.docNo')" width="140"  sortable>
        <template #default="{ row }">
          <el-link v-if="row.billId" type="primary" @click="showBillDetail(row)">{{ row.billNo }}</el-link>
          <span v-else>{{ row.billNo || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('field.type')" width="70">
        <template #default="{ row }">{{ docTypeLabel(row.billType) }}</template>
      </el-table-column>
      <el-table-column prop="title" :label="$t('sharing.common.colTitle')" min-width="140" show-overflow-tooltip  sortable />
      <el-table-column :label="$t('field.amount')" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column :label="$t('sharing.common.colDocStatus')" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.billStatus" :type="billStatusType(row.billStatus)" size="small" effect="plain">{{ billStatusLabel(row.billStatus) }}</el-tag>
          <span v-else style="color: #c0c4cc; font-size: 12px">—</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('sharing.common.colWorkItem')" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.chainTotal > 1" size="small" :type="taskTypeTag(row.taskType)" effect="plain">
            {{ taskTypeLabel(row.taskType) }} {{ row.chainSeq }}/{{ row.chainTotal }}
          </el-tag>
          <el-tag v-else size="small" :type="taskTypeTag(row.taskType)" effect="plain">{{ taskTypeLabel(row.taskType) }}</el-tag>
          <el-tag v-if="row.activeFlag === 'N'" size="small" type="info" style="margin-left:4px">{{ $t('sharing.taskPool.labelInactive') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="stType(row.poolStatus)" size="small">{{ stLabel(row.poolStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('sharing.taskPool.colClaimer')" width="100">
        <template #default="{ row }">{{ userName(row.assigneeId) }}</template>
      </el-table-column>
      <el-table-column prop="receiveTime" :label="$t('sharing.common.colClaimTime')" width="160"  sortable :formatter="(r, c, v) => formatDateTime(v)" />
      <el-table-column prop="slaTime" :label="$t('sharing.taskPool.colSlaDue')" width="180" sortable>
        <template #default="{ row }">
          <span v-if="!row.slaTime" style="color: #c0c4cc; font-size: 12px">—</span>
          <template v-else>
            <span :class="{ 'sla-overdue-text': isSlaOverdue(row) }">{{ formatDateTime(row.slaTime) }}</span>
            <el-tag v-if="isSlaOverdue(row)" type="danger" size="small" style="margin-left:4px">{{ $t('sharing.common.colOverdue') }}</el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" :label="$t('common.createTime')" width="160"  sortable :formatter="(r, c, v) => formatDateTime(v)" />
      <el-table-column :label="$t('common.operation')" width="260" fixed="right">
        <!-- 共享任务池是「作业员抢单/办理」视角，不承载流程审批（那是「我的待办」的职责）。
             原先 InlineActions 上挂的 approve/reject 实际触发的是「批量操作」函数（忽略行参数），已移除。 -->
        <template #default="{ row }">
          <InlineActions :visible="true" @view="preview(row)" :show-trace="true" @trace="openTrace(row)" />
          <el-button v-if="row.poolStatus === 'WAITING' && row.activeFlag === 'Y'" v-prevent-double-click link type="success" size="small" @click="grab(row)">{{ $t('sharing.common.btnGrab') }}</el-button>
          <el-button v-if="row.poolStatus === 'RECEIVED'" v-prevent-double-click link type="warning" size="small" @click="withdraw(row)">{{ $t('sharing.common.btnWithdraw') }}</el-button>
          <el-button v-if="row.poolStatus !== 'FINISHED'" link type="primary" size="small" @click="openReassign(row)">{{ $t('sharing.common.btnReassign') }}</el-button>
          <el-button link size="small" @click="preview(row)">{{ $t('sharing.common.colVoucher') }}</el-button>
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
    <el-dialog v-model="reassignVis" :title="$t('sharing.common.btnReassign')" width="420px">
      <el-form ref="reassignFormRef" :model="reassignForm" :rules="reassignFormRules" label-width="90px">
        <el-form-item :label="$t('sharing.taskPool.labCurrentTask')">{{ current?.billNo }}（{{ current?.title }}）</el-form-item>
<el-form-item :label="$t('sharing.taskPool.labAssignTo')" prop="assigneeId">
          <UserSelect v-model="reassignForm.assigneeId" :users="users" :placeholder="$t('sharing.taskPool.phSelectOperator')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reassignVis = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" v-prevent-double-click @click="doReassign">{{ $t('sharing.taskPool.btnConfirmAssign') }}</el-button>
      </template>
    </el-dialog>

    <!-- 凭证预览 -->
    <el-dialog v-model="voucherVis" :title="$t('sharing.taskPool.titleVoucherPreview')" width="640px" destroy-on-close>
      <el-table :data="voucherLines" size="small" border max-height="360" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="lineNo" :label="$t('sharing.common.colLineNo')" width="60"  sortable />
        <el-table-column prop="summary" :label="$t('field.summary')" min-width="120"  sortable />
        <el-table-column prop="subjectCode" :label="$t('sharing.common.colAcctCode')" width="100"  sortable />
        <el-table-column prop="subjectName" :label="$t('sharing.common.colAcctName')" min-width="120"  sortable />
        <el-table-column prop="direction" :label="$t('field.direction')" width="60"  sortable :formatter="(row, col, val) => directionLabel(val)" />
        <el-table-column prop="amount" :label="$t('field.amount')" width="100" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.amount) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!voucherLines.length" :description="$t('sharing.taskPool.emptyNoVoucher')" />
    </el-dialog>

    <!-- 批量抢单弹窗 -->
    <el-dialog v-model="batchGrabVis" :title="$t('sharing.taskPool.btnBatchGrab')" width="480px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        {{ $t('sharing.taskPool.msgBatchGrabLead') }}<b>{{ selRows.length }}</b> {{ $t('sharing.taskPool.msgBatchGrabRule') }}
      </el-alert>
      <el-table :data="selRows" size="small" border max-height="200" style="margin-bottom: 12px">
        <el-table-column prop="billNo" :label="$t('field.docNo')" width="140" />
        <el-table-column prop="title" :label="$t('sharing.common.colTitle')" min-width="120" show-overflow-tooltip />
        <el-table-column :label="$t('sharing.common.colWorkItem')" width="80">
          <template #default="{ row }">{{ taskTypeLabel(row.taskType) }}</template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="stType(row.poolStatus)" size="small">{{ stLabel(row.poolStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="batchGrabVis = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doBatchGrab" :loading="batchLoading">{{ $t('sharing.taskPool.msgConfirmGrab') }}</el-button>
      </template>
    </el-dialog>

    <!-- 单据详情：双击行 / 点击单号均在当前页展示，不跳转修改页 -->
    <el-dialog v-model="detailVis" :title="$t('sharing.taskPool.titleBillDetail', { v1: detailRow?.billNo || '' })" width="560px" destroy-on-close>
      <el-descriptions v-if="detailRow" :column="2" border size="small">
        <el-descriptions-item :label="$t('field.docNo')">{{ detailRow.billNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.billType')">{{ docTypeLabel(detailRow.billType) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.common.colTitle')" :span="2">{{ detailRow.title || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.amount')">{{ fmt(detailRow.amount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.common.colDocStatus')">
          <el-tag v-if="detailRow.billStatus" :type="billStatusType(detailRow.billStatus)" size="small" effect="plain">{{ billStatusLabel(detailRow.billStatus) }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.common.colWorkItem')">
          <el-tag size="small" :type="taskTypeTag(detailRow.taskType)" effect="plain">{{ taskTypeLabel(detailRow.taskType) }}{{ detailRow.chainTotal > 1 ? ` ${detailRow.chainSeq}/${detailRow.chainTotal}` : '' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.taskPool.colTaskStatus')">
          <el-tag :type="stType(detailRow.poolStatus)" size="small">{{ stLabel(detailRow.poolStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.taskPool.colClaimer')">{{ userName(detailRow.assigneeId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.common.colClaimTime')">{{ formatDateTime(detailRow.receiveTime) || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('sharing.taskPool.colSlaDue')">
          <span :class="{ 'sla-overdue-text': isSlaOverdue(detailRow) }">{{ formatDateTime(detailRow.slaTime) || '—' }}</span>
          <el-tag v-if="isSlaOverdue(detailRow)" type="danger" size="small" style="margin-left:4px">{{ $t('sharing.common.colOverdue') }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('common.createTime')">{{ formatDateTime(detailRow.createTime) || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVis = false">{{ $t('common.close') }}</el-button>
        <el-button type="primary" @click="goBill(detailRow)">{{ $t('sharing.taskPool.btnGoBiz') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />

  <CoachMark ref="taskPoolCm" :guide-key="_guideKey" :selector="'.cm-target-taskpool'" :title="$t('sharing.taskPool.cmTitle')" :text="$t('sharing.taskPool.cmText')" />
</template>

<script setup>

import { onMounted, reactive, ref, computed, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime } from '@/utils/format'
import { sharingApi, userApi, store } from '../../api'
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
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+r': () => location.reload(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})
const taskPoolCm = ref(null)

// BillTraceDrawer
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
// 默认只显示待认领任务（任务池默认展示待处理任务）
const filter = reactive({ pageNum: 1, pageSize: 20, poolStatus: 'WAITING', taskType: '', keyword: '' })
const users = ref([])
const reassignVis = ref(false)
const current = ref(null)
const reassignFormRef = ref(null)
const reassignForm = reactive({ assigneeId: null })
const voucherVis = ref(false)
const voucherLines = ref([])

// 分组 + 批量抢单
const groupBy = ref('')
const batchGrabVis = ref(false)
const batchLoading = ref(false)

const reassignFormRules = computed(() => (reactive({
  assigneeId: commonRules.select(t('sharing.taskPool.phSelectOperator')),
})))

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
// 两张账打通后再考虑在本页加审批入口（见 流程节点指定处理人改造方案_20260922.md）。
async function doBatchGrab() {
  if (!selRows.value.length) return
  batchLoading.value = true
  const fails = []
  let ok = 0
  for (const [i, row] of selRows.value.entries()) {
    const name = row.billNo || row.title || t('sharing.taskPool.itemLabel', { v1: i + 1 })
    try {
      if (row.poolStatus !== 'WAITING') throw new Error(t('sharing.taskPool.errGrabbed'))
      await sharingApi.receive(row.id)
      ok++
    } catch (e) { fails.push(`${name}：${e?.message || t('common.failed')}`) }
  }
  batchLoading.value = false
  if (!fails.length) {
    batchGrabVis.value = false
    ElMessage.success(t('sharing.taskPool.msgBatchAll', { v1: ok }))
  } else {
    // 有失败时保留弹窗与已选行，逐条列明原因，改完可直接重试
    ElMessage.warning(t('sharing.taskPool.msgBatchPart', { v1: ok, v2: fails.length }))
    const lines = fails.map((l, i) => `${i + 1}. ${l}`)
    ElMessageBox.alert(
      h('div', null, lines.map((l) => h('div', { style: 'margin:2px 0;line-height:1.6' }, l))),
      t('sharing.taskPool.titleBatchFails', { v1: fails.length }),
      { type: 'error', confirmButtonText: t('common.gotIt') },
    ).catch(() => {})
  }
  load()
}

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
function taskTypeTag(t) {
  return t === 'AUDIT' ? 'primary' : t === 'PAY' ? 'warning' : 'success'
}
function stLabel(s) {
  return { WAITING: t('sharing.taskPool.optWaiting'), RECEIVED: t('sharing.taskPool.optAssigned'), FINISHED: t('sharing.taskPool.optFinished') }[s] || s
}
function stType(s) {
  return s === 'FINISHED' ? 'success' : s === 'RECEIVED' ? 'warning' : 'info'
}
function userName(id) {
  if (!id) return '—'
  const u = users.value.find((x) => x.id === id)
  return u ? (u.nickName || u.userName) : '#' + id
}

// SLA 逾期规则与流程侧「超时未办」一致：未办结 + 已过截止时间
function isSlaOverdue(row) {
  if (!row.slaTime || row.poolStatus === 'FINISHED') return false
  const stamp = new Date(String(row.slaTime).replace(' ', 'T')).getTime()
  return Number.isFinite(stamp) && stamp < Date.now()
}
function slaRowClass({ row }) {
  return isSlaOverdue(row) ? 'sla-overdue-row' : ''
}
const overdueCount = computed(() => rows.value.filter(isSlaOverdue).length)

// 跳转到单据详情（统一走共享 bizEntry：全量类型映射 + 携带单号 query 定位）
function goBill(row) {
  const ok = goBizPage(router, row)
  if (!ok) ElMessage.warning(t('sharing.taskPool.msgNoJump', { v1: row.billType || t('sharing.taskPool.txtThisType') }))
}

async function load() {
  loading.value = true
  try {
    // INACTIVE 是前端伪状态——落参时换成 activeFlag='N' 并清空 poolStatus；
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
  ElMessageBox.confirm(t('sharing.taskPool.confirmGrab', { v1: row.billNo, v2: row.title }), t('sharing.common.btnGrab'), { type: 'info', confirmButtonText: t('sharing.taskPool.msgConfirmGrab') })
    .then(async () => {
      try {
        await sharingApi.receive(row.id)
        ElMessage.success(t('sharing.taskPool.msgGrabOk'))
        load()
      } catch (e) { ElMessage.error(e.message) }
    }).catch(() => {})
}

function withdraw(row) {
  ElMessageBox.confirm(t('sharing.taskPool.confirmWithdraw', { v1: row.billNo }), t('sharing.common.btnWithdraw'), { type: 'warning' })
    .then(async () => {
      await sharingApi.withdraw(row.id)
      ElMessage.success(t('sharing.common.colWithdrawn'))
      load()
    }).catch(() => {})
}

async function openReassign(row) {
  // 默认只加载同公司人员
  if (!users.value.length) users.value = (await userApi.options({ pageNum: 1, pageSize: 200, companyId: store.user?.companyId })).list || []
  current.value = row
  reassignForm.assigneeId = row.assigneeId || null
  reassignVis.value = true
}

async function doReassign() {
  if (!(await validateForms([reassignFormRef]))) return
  await sharingApi.reassign(current.value.id, reassignForm.assigneeId)
  ElMessage.success(t('sharing.taskPool.msgReassigned'))
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
  // 默认只加载同公司人员
  try { users.value = (await userApi.options({ pageNum: 1, pageSize: 200, companyId: store.user?.companyId })).list || [] } catch (e) { /* ignore */ }
})

function exportCsv() {
  // 原实现导出的是 voucherLines（凭证行）、入口还错挂在「重新分配」弹窗页脚；
  // 现与「导出选中」并列在工具栏，列集对齐当前任务池表格。
  if (!rows.value.length) { ElMessage.warning(t('sharing.common.msgNothingToExport')); return }
  const cols = [{ prop: 'billNo', label: t('field.docNo') }, { prop: 'title', label: t('sharing.common.colTitle') }, { prop: 'amount', label: t('field.amount') }, { prop: 'billStatus', label: t('sharing.common.colDocStatus') }, { prop: 'taskType', label: t('sharing.common.colWorkItem') }, { prop: 'poolStatus', label: t('common.status') }, { prop: 'receiveTime', label: t('sharing.common.colClaimTime') }, { prop: 'createTime', label: t('common.createTime') }]
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
  ElMessage.success(t('sharing.common.msgCsvExported'))
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('sharing.common.msgPickRowsFirst')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('sharing.common.msgNoColumnInfo')); return }
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
  ElMessage.success(t('sharing.common.msgExportedCount', { v1: selRows.value.length }))
}


// Show guide on first visit
const _guideKey = 'fssc_guide_taskPoolCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => taskPoolCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
/* SLA 逾期行整行标红（同 借款台账逾期规则），使抢单/分派时先看账龄再看单号 */
:deep(.sla-overdue-row) td.el-table__cell { background-color: rgba(245, 108, 108, 0.13) !important; }
.sla-overdue-text { color: #f56c6c; font-weight: 600; }
</style>
