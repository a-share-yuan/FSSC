<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div :class="['requisition-workbench', { tabless: isSingle }]">
    <!-- 场景卡片入口 -->
    <el-card class="cm-target-req scene-section" shadow="never">
      <template #header><span style="font-weight:700">{{ $t('expense.workbench.tabClaimWb') }}</span></template>
      <el-row class="cm-target-req" :gutter="12">
        <el-col v-for="grp in scenes" :key="grp.category" :xs="24" :sm="12" :md="8" :lg="8" :xl="4">
          <el-card shadow="hover" class="scene-group" :body-style="{ padding: '12px' }">
            <div class="group-title">{{ grp.category }}</div>
            <div class="scene-items">
              <div v-for="item in grp.items" :key="item.docType"
                   class="scene-card" @click="goScene(item)">
                <el-icon class="scene-icon"><Document /></el-icon>
                <span class="scene-label">{{ item.label }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 概览看板 -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div class="stat" @click="activeTab = 'todo'">
            <div class="num" style="color:#e6a23c">{{ stats.todo }}</div>
            <div class="label">{{ $t('expense.workbench.tabMyTodo') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div class="stat" @click="activeTab = 'mine'">
            <div class="num" style="color:#409eff">{{ stats.mine }}</div>
            <div class="label">{{ $t('expense.workbench.tabMineDocuments') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div class="stat" @click="activeTab = 'done'">
            <div class="num" style="color:#67c23a">{{ stats.done }}</div>
            <div class="label">{{ $t('expense.workbench.tabMyDone') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div class="stat">
            <div class="num" style="color:#909399">{{ scenes.length }}</div>
            <div class="label">{{ $t('expense.workbench.hintScenarios') }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 三视图 Tab -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 我的单据（全类型：合同/采购/预算/总账/档案…由后端按路由注册表逐表归并） -->
      <el-tab-pane :label="$t('expense.common.tabMyBills')" name="mine">
        <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center">
          <el-select v-model="billFilter.type" style="width: 190px" @change="reloadMine" :placeholder="$t('field.billType')" filterable>
            <el-option :label="$t('expense.workbench.phAllTypes')" value="" />
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-select v-model="billFilter.status" style="width: 140px" @change="reloadMine" :placeholder="$t('common.status')">
            <el-option :label="$t('common.all')" value="" />
            <el-option :label="$t('status.draft')" value="DRAFT" />
            <el-option :label="$t('status.approving')" value="APPROVING" />
            <el-option :label="$t('expense.workbench.optApprovedFilter')" value="APPROVED" />
            <el-option :label="$t('status.rejected')" value="REJECTED" />
            <el-option :label="$t('status.paid')" value="PAID" />
            <el-option :label="$t('status.closed')" value="CLOSED" />
          </el-select>
          <span style="color:#909399;font-size:12px">{{ $t('expense.workbench.txtTotal', { v1: mine.total, v2: mine.approximate ? t('expense.workbench.txtItemsApprox') : t('expense.workbench.txtItems') }) }}</span>
        </div>
        <el-alert v-if="unsupportedText" type="info" :closable="false" show-icon style="margin-bottom: 8px"
                  :title="$t('expense.requisition.hintUnsupportedTypes', { v1: unsupportedText })" />
        <SkeletonList v-if="mine.loading" :rows="8" :cols="5" />
        <el-table class="cm-target-req" :data="mine.list" size="small" stripe v-else v-loading="mine.loading" max-height="420">
          <el-table-column prop="docNo" :label="$t('field.docNo')" width="150" sortable :formatter="(row, col, val) => val || '—'" />
          <el-table-column :label="$t('field.type')" width="130">
            <template #default="{ row }">{{ rowTypeLabel(row) }}</template>
          </el-table-column>
          <el-table-column prop="title" :label="$t('expense.common.colTitle')" min-width="160" show-overflow-tooltip />
          <el-table-column :label="$t('field.amount')" width="110" sortable>
            <template #default="{ row }">{{ row.amount == null ? '—' : formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column :label="$t('common.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="docTime" :label="$t('expense.workbench.colLastChange')" width="160" sortable :formatter="(row, col, val) => val ? formatDateTime(val) : '—'" />
          <el-table-column :label="$t('common.operation')" width="170" fixed="right">
            <template #default="{ row }">
              <!-- 我的单据：申请人在此只查看/追溯/编辑草稿；审批/退回属于「我的待办」，不放这里（避免无 taskId 误审批 + 点审批变退回） -->
              <!-- 费用域在工作台内联看全链路详情；其余域按注册表台账页定位（单号带过去），无台账页则不给死链 -->
              <InlineActions
                v-if="row.editable"
                :visible="true"
                @view="openDetail(row)"
                :show-approve="false"
                :show-reject="false"
                :show-trace="true"
                @trace="openTrace(row)"
              />
              <el-button v-if="row.editable && canEditResubmit(row)" link type="danger" size="small" @click="goEdit(row)">{{ $t('expense.common.btnEditResubmit') }}</el-button>
              <el-button v-if="!row.editable && row.ledgerRoute" link type="primary" size="small" @click="goLedger(row)">
                {{ row.status === BillStatus.REJECTED ? t('expense.common.btnEditResubmit') : t('expense.workbench.btnOpenLedger') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="mine.total > 0" :total="mine.total" :page-size="10" v-model:current-page="mine.page"
                       layout="total, prev, pager, next, jumper" @current-change="loadMine" style="margin-top: 8px" />
      </el-tab-pane>

      <!-- 我的待办 -->
      <el-tab-pane :label="$t('expense.workbench.tabMyTodo')" name="todo">
        <SkeletonList v-if="todo.loading" :rows="8" :cols="5" />
        <el-table :data="todo.list" size="small" stripe v-else v-loading="todo.loading" max-height="420">
          <el-table-column prop="bizNo" :label="$t('field.docNo')" width="150" sortable />
          <el-table-column prop="title" :label="$t('expense.common.colTitle')" min-width="160" />
          <el-table-column prop="taskName" :label="$t('expense.workbench.colCurrentNode')" width="120" />
          <el-table-column prop="bizType" :label="$t('field.type')" width="110" :formatter="(row, col, val) => docTypeLabel(val)" />
          <el-table-column prop="createTime" :label="$t('expense.workbench.colArrivedTime')" width="160" sortable :formatter="(row, col, val) => formatDateTime(val)" />
          <el-table-column :label="$t('common.operation')" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('expense.common.btnTrace') }}</el-button>
              <el-button link type="success" size="small" @click="doApprove(row, 'APPROVED')">{{ $t('field.approve') }}</el-button>
              <el-button link type="danger" size="small" @click="doApprove(row, 'REJECTED')">{{ $t('field.reject') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="todo.total > 0" :total="todo.total" :page-size="10" v-model:current-page="todo.page"
                       layout="total, prev, pager, next, jumper" @current-change="loadTodo" style="margin-top: 8px" />
      </el-tab-pane>

      <!-- 我的已办 -->
      <el-tab-pane :label="$t('expense.workbench.tabMyDone')" name="done">
        <SkeletonList v-if="done.loading" :rows="8" :cols="5" />
        <el-table :data="done.list" size="small" stripe v-else v-loading="done.loading" max-height="420">
          <el-table-column prop="bizType" :label="$t('field.type')" width="110" :formatter="(row, col, val) => docTypeLabel(val)" />
          <el-table-column prop="taskName" :label="$t('expense.workbench.colNode')" width="120" />
          <el-table-column prop="action" :label="$t('common.operation')" width="90">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('expense.common.btnTrace') }}</el-button>
              <el-tag :type="row.action === 'APPROVE' ? 'success' : row.action === 'REJECT' ? 'danger' : 'info'" size="small">{{ wfActionLabel(row.action) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operUserName" :label="$t('field.handler')" width="100" />
          <el-table-column prop="comment" :label="$t('expense.workbench.lblComment')" min-width="160" />
          <el-table-column prop="handleTime" :label="$t('expense.workbench.colHandledTime')" width="160" sortable :formatter="(row, col, val) => formatDateTime(val)" />
        </el-table>
        <el-pagination v-if="done.total > 0" :total="done.total" :page-size="10" v-model:current-page="done.page"
                       layout="total, prev, pager, next, jumper" @current-change="loadDone" style="margin-top: 8px" />
      </el-tab-pane>
    </el-tabs>

    <!-- 单据全链路详情抽屉 -->
    <el-drawer v-model="detail.visible" :title="$t('expense.requisition.titleTrace', { v1: detail.data.bill?.billNo || '' })" size="640px">
      <template v-if="detail.data.bill">
        <!-- 基础信息 -->
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="$t('field.docNo')">{{ detail.data.bill.billNo }}</el-descriptions-item>
          <el-descriptions-item :label="$t('common.status')"><el-tag :type="statusType(detail.data.bill.status)" size="small">{{ statusLabel(detail.data.bill.status) }}</el-tag></el-descriptions-item>
          <el-descriptions-item :label="$t('field.amount')">{{ formatMoney(detail.data.bill.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('expense.common.colApplyDate')">{{ detail.data.bill.applyDate }}</el-descriptions-item>
        </el-descriptions>

        <!-- 流程轨迹 -->
        <h4 style="margin: 14px 0 6px">{{ $t('expense.workbench.tabFlowTrail') }}</h4>
        <el-timeline v-if="detail.data.wfInstance">
          <el-timeline-item :timestamp="detail.data.wfInstance.startTime" type="primary">{{ $t('expense.workbench.labFlowStarted') }}</el-timeline-item>
          <el-timeline-item v-if="detail.data.wfInstance.currentNode" :timestamp="detail.data.wfInstance.updateTime" type="warning">
            {{ $t('expense.workbench.textCurrent', { v1: detail.data.wfInstance.currentNode }) }}
          </el-timeline-item>
          <el-timeline-item v-if="detail.data.wfInstance.endTime" :timestamp="detail.data.wfInstance.endTime" type="success">
            {{ detail.data.wfInstance.status === BillStatus.APPROVED ? t('expense.workbench.stApproved') : _statusLabel(detail.data.wfInstance.status) }}
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else :description="$t('expense.workbench.emptyFlow')" :image-size="40" />

        <!-- 凭证 -->
        <h4 style="margin: 14px 0 6px">{{ $t('expense.workbench.tabVouchers') }}</h4>
        <el-table v-if="detail.data.voucher" :data="detail.data.voucherLines" size="small" border v-loading="loading">
          <el-table-column prop="lineNo" :label="$t('expense.common.colLine')" width="50" />
          <el-table-column prop="summary" :label="$t('field.summary')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="subjectCode" :label="$t('expense.common.colAccountCode')" width="90" />
          <el-table-column prop="subjectName" :label="$t('field.subject')" width="120" />
          <el-table-column :label="$t('field.direction')" width="60">
            <template #default="{ row }">{{ row.direction === 'DEBIT' ? t('expense.voucher.optDebit') : t('expense.voucher.optCredit') }}</template>
          </el-table-column>
          <el-table-column :label="$t('field.amount')" width="100">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else :description="$t('expense.workbench.emptyVouchers')" :image-size="40" />

        <!-- 付款记录 -->
        <h4 style="margin: 14px 0 6px">{{ $t('expense.workbench.tabPayments') }}</h4>
        <el-table v-if="detail.data.payments?.length" :data="detail.data.payments" size="small" border v-loading="loading">
          <el-table-column prop="payeeName" :label="$t('expense.create.colReceivingParty')" min-width="120" show-overflow-tooltip />
          <el-table-column prop="bankAccount" :label="$t('expense.workbench.colBankAccount')" width="140" />
          <el-table-column :label="$t('field.amount')" width="100">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="payStatus" :label="$t('common.status')" width="80" :formatter="(row, col, val) => payStatusLabel(val)" />
          <el-table-column prop="payTime" :label="$t('expense.workbench.colPaidTime')" width="160" :formatter="(row, col, val) => formatDateTime(val)" />
        </el-table>
        <el-empty v-else :description="$t('expense.workbench.emptyPayments')" :image-size="40" />

        <!-- 影像附件 -->
        <h4 style="margin: 14px 0 6px">{{ $t('expense.workbench.tabImages', { v1: detail.data.imageCount || 0 }) }}</h4>
        <el-empty v-if="!detail.data.imageCount" :description="$t('expense.workbench.emptyImages')" :image-size="40" />

        <!-- 档案 -->
        <h4 style="margin: 14px 0 6px">{{ $t('expense.workbench.tabFiling') }}</h4>
        <el-descriptions v-if="detail.data.archive" :column="1" border size="small">
          <el-descriptions-item :label="$t('expense.workbench.colArchiveNo')">{{ detail.data.archive.archiveNo }}</el-descriptions-item>
          <el-descriptions-item :label="$t('expense.workbench.colFileStatus')">{{ detail.data.archive.archiveStatus }}</el-descriptions-item>
          <el-descriptions-item :label="$t('expense.ledger.colFiledAt')">{{ detail.data.archive.archivedAt }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else :description="$t('expense.workbench.stNotFiled')" :image-size="40" />
      </template>
    </el-drawer>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />

  <CoachMark ref="reqCm" :guide-key="_guideKey" :selector="'.cm-target-req'" :title="$t('expense.workbench.tabRequisitionWb')" :text="$t('expense.workbench.hintRequisitionWb')" />
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>
import { docTypeLabel, billSubTypeLabel, payStatusLabel, wfActionLabel } from '@/constants/enums'
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { http, wfApi } from '../../api'
import { statusLabel as _statusLabel, statusType as _statusType, BillStatus, canEditResubmit, editBillPath } from '../../constants/billStatus'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import CoachMark from '@/components/CoachMark.vue'
import InlineActions from '@/components/InlineActions.vue'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'
import { formatDate, formatDateTime } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const reqCm = ref(null)

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.bizId || row.id
  traceBizType.value = row.bizType || null
  traceVisible.value = true
}

const router = useRouter()

// 场景卡片
const scenes = ref([])
// 概览
const stats = reactive({ todo: 0, mine: 0, done: 0 })
// Tab
// P: tab 页拆分——由菜单深链进来时传入 tab，只显示该页并隐藏 tab 导航条（一 TAB = 一菜单）
const props = defineProps({ tab: { type: String, default: '' } })
const isSingle = computed(() => !!props.tab)
const activeTab = ref(props.tab || 'mine')

// 三个菜单（我的单据/待办/已办）共用本组件，仅 tab prop 不同。
// vue-router 复用同一组件实例时 props.tab 会更新但组件不会重新挂载，
// 若不 watch：从「我的单据」切到「我的待办/已办」界面不刷新 = 假死。
watch(() => props.tab, (v) => {
  if (v && v !== activeTab.value) {
    activeTab.value = v
    onTabChange(v)
  }
})

// 列表数据
const mine = reactive({ list: [], total: 0, page: 1, loading: false, types: [], unsupported: [], approximate: false })
const todo = reactive({ list: [], total: 0, page: 1, loading: false })
const done = reactive({ list: [], total: 0, page: 1, loading: false })
const billFilter = reactive({ type: '', status: '' })

// 详情
const detail = reactive({ visible: false, data: {} })
const loading = ref(false)

// 状态映射（统一引用 constants/billStatus）
function statusLabel(s) { return _statusLabel(s) || '—' }
function statusType(s) { return _statusType(s) }
/** 费用族一张表承载多种单据 ⇒ 行内 bill_type(+bill_sub_type) 最准；其余域后端按表给族名（一表多 bizType 时不猜具体类型） */
function rowTypeLabel(row) {
  if (row.sourceTable === 'exp_bill') {
    const docLabel = docTypeLabel(row.billType) || '—'
    return row.billSubType ? docLabel + '·' + billSubTypeLabel(row.billSubType) : docLabel
  }
  return row.typeLabel || docTypeLabel(row.bizType) || '—'
}
function formatMoney(val) {
  return Number(val || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

async function loadScenes() {
  try {
    const res = await http.get('/tenant/workbench/scenes')
    scenes.value = res || []
  } catch (e) { ElMessage.error(e.message) }
}

async function loadDashboard() {
  try {
    const res = await http.get('/tenant/workbench/dashboard')
    Object.assign(stats, res || {})
  } catch (e) { /* ignore */ }
}

// 加载我的单据：全类型（后端 MyDocumentService 按 BillRouteRegistry 逐表查询归并）。
// 旧 /my-bills 只查 exp_bill 一表，合同/采购/预算/总账的驳回单在列表里根本看不见。
async function loadMine() {
  mine.loading = true
  try {
    const res = await http.get('/tenant/workbench/my-documents', { params: { pageNum: mine.page, pageSize: 10, bizType: billFilter.type || undefined, status: billFilter.status || undefined } })
    mine.list = (res?.records || []).map((r) => ({ ...r, id: r.bizId, billNo: r.docNo, totalAmount: r.amount }))
    mine.total = res?.total || 0
    mine.types = res?.types || []
    mine.unsupported = res?.unsupported || []
    mine.approximate = !!res?.approximate
  } catch (e) { ElMessage.error(e.message) } finally { mine.loading = false }
}

function reloadMine() {
  mine.page = 1
  loadMine()
}

/** 无登记人列的类型：明说"工作台不按人汇总"，而不是让列表静默少数据 */
const unsupportedText = computed(() => (mine.unsupported || []).map((u) => {
  const codes = String(u.bizTypes || '').split(',').filter(Boolean).map((c) => docTypeLabel(c.trim()) || c.trim())
  return codes.length ? codes.join('、') : String(u.label || u.table || '')
}).filter(Boolean).join('、'))

// 下拉规则由后端给（＝注册表可按人检索的表），table: 前缀＝一表多类型只能按表筛；选项只显中文，英文码不上界面
const typeOptions = computed(() => (mine.types || []).map((t) => {
  const label = t.label || docTypeLabel(t.bizType) || t.bizType
  return { value: t.bizType, label }
}))

async function loadTodo() {
  todo.loading = true
  try {
    const res = await http.get('/tenant/workbench/my-todo', { params: { pageNum: todo.page, pageSize: 10 } })
    todo.list = res?.list || []
    todo.total = res?.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { todo.loading = false }
}

async function loadDone() {
  done.loading = true
  try {
    const res = await http.get('/tenant/workbench/my-done', { params: { pageNum: done.page, pageSize: 10 } })
    done.list = res?.list || []
    done.total = res?.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { done.loading = false }
}

// Tab 切换
function onTabChange(name) {
  if (name === 'todo' && !todo.list.length) loadTodo()
  if (name === 'done' && !done.list.length) loadDone()
  if (name === 'mine' && !mine.list.length) loadMine()
}

// 场景跳转
function goScene(item) {
  if (item.route) router.push(item.route)
}

// 编辑单据：统一走 editBillPath + editBillId 深链（旧写法传 ?id= 无人消费、APPLY 分支丢单据 id，编辑等于新建）
function goEdit(row) {
  router.push({ path: editBillPath(row), query: { editBillId: row.id } })
}

// 非费用域：工作台没有该域的表单，跳到注册表登记的台账页并把单号带过去定位
function goLedger(row) {
  if (!row.ledgerRoute) return
  router.push({ path: row.ledgerRoute, query: row.docNo ? { kw: row.docNo } : {} })
}

// 打开全链路详情
async function openDetail(row) {
  detail.visible = true
  detail.data = {}
  loading.value = true
  try {
    const res = await http.get('/tenant/workbench/bill-detail/' + row.id, { params: { bizType: row.bizType } })
    detail.data = res || {}
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

// 审批：共享弹窗（单据摘要 + 必填意见），替代原先无校验的单行 prompt
const approvalRef = ref(null)
async function doApprove(row, decision) {
  if (!row?.taskId) { ElMessage.warning(t('expense.workbench.errNoTaskId')); return }
  if (!approvalRef.value) { ElMessage.error(t('expense.workbench.errApproveDialogMissing')); return }
  const res = await approvalRef.value.open({
    row, taskId: row.taskId, decision: decision === BillStatus.APPROVED ? 'approve' : 'reject',
  })
  if (!res) return // 用户取消
  const payload = { taskId: row.taskId, approved: res.approved, comment: res.comment,
    nextAssigneeUserId: res.nextAssigneeUserId }
  try {
    await wfApi.approve(payload)
    ElMessage.success(res.approved ? t('field.passTag') : t('status.rejected'))
    await loadTodo()
    await loadDashboard()
  } catch (e) {
    // 作业池（POOL）任务未领取时服务端拒办：就地引导领取后重放本次审批，
    // 别让"待办页能点通过、点了只弹一句错"变成死路（本列表没有池标记与领取按钮可用）。
    if (String(e?.message || '').includes('须先「领取」')) {
      try {
        await ElMessageBox.confirm(
          t('expense.workbench.askClaimTask'),
          t('expense.workbench.errTaskNotClaimed'), { type: 'warning', confirmButtonText: t('expense.workbench.btnClaimAndContinue'), cancelButtonText: t('expense.workbench.btnNotNow') })
      } catch { return }
      try {
        await wfApi.claim(row.taskId)
        await wfApi.approve(payload)
        ElMessage.success(res.approved ? t('expense.workbench.stClaimedApproved') : t('expense.workbench.stClaimedRejected'))
        await loadTodo()
        await loadDashboard()
      } catch (e2) { ElMessage.error(e2?.message || t('expense.workbench.msgClaimOrHandleFailed')) }
      return
    }
    ElMessage.error(e?.message || t('expense.workbench.msgApproveFailed'))
  }
}

onMounted(() => {
  loadScenes()
  loadDashboard()
  loadMine()
  onTabChange(activeTab.value)
})

// Show guide on first visit
const _guideKey = 'fssc_guide_reqCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => reqCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
.scene-section { margin-bottom: 16px; }
.scene-group { margin-bottom: 12px; }
.group-title { font-weight: 700; font-size: 14px; color: #303133; margin-bottom: 8px; }
.scene-items { display: flex; flex-direction: column; gap: 6px; }
.scene-card { display: flex; align-items: center; gap: 6px; padding: 6px 8px; border-radius: 4px; cursor: pointer; transition: background 0.2s; }
.scene-card:hover { background: #ecf5ff; }
.scene-icon { font-size: 16px; color: #409eff; }
.scene-label { font-size: 13px; }
.stats-row { margin-bottom: 16px; }
.stat { text-align: center; cursor: pointer; }
.stat .num { font-size: 28px; font-weight: 700; }
.stat .label { color: #909399; margin-top: 4px; font-size: 13px; }
/* 一 TAB = 一菜单：从菜单深链进来（tab 命中）时隐藏 tab 导航条，只显示该页 */
.tabless :deep(.el-tabs__header) { display: none; }
</style>
