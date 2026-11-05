<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>{{ $t('gl.refund.pageTitle') }}</b>
        <el-select v-model="f.refundType" :placeholder="$t('gl.refund.colKind')" clearable style="width: 180px">
          <el-option :label="$t('gl.refund.kindExport')" value="EXPORT_REFUND" />
          <el-option :label="$t('gl.refund.kindRetained')" value="CREDIT_REFUND" />
          <el-option :label="$t('gl.refund.kindInstant')" value="IMMEDIATE_REFUND" />
        </el-select>
        <el-select v-model="f.status" :placeholder="$t('common.status')" clearable style="width: 140px">
          <el-option v-for="s in STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-date-picker v-model="f.period" type="month" :placeholder="$t('gl.consolidate.colBelong')" value-format="YYYYMM" clearable style="width: 140px" />
        <el-input v-model="f.keyword" :placeholder="$t('gl.refund.phNoPeriod')" clearable style="width: 160px" />
        <el-button type="primary" size="small" @click="load(1)">{{ $t('common.search') }}</el-button>
        <el-button size="small" @click="createVisible = true">{{ $t('gl.refund.btnNew') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('gl.ledger.btnExportSel', { v1: selRows.length }) }}</el-button>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" v-else size="small" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="refundNo" :label="$t('gl.refund.colNo')" width="180"  sortable />
      <el-table-column :label="$t('gl.refund.colKind')" width="120">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.refundType)" size="small">{{ typeName(row.refundType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="period" :label="$t('gl.consolidate.colBelong')" width="90"  sortable />
      <el-table-column label="申请人" width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ userName(row.applyBy) }}</template>
      </el-table-column>
      <el-table-column label="申请时间" width="150" sortable>
        <template #default="{ row }">{{ formatDateTime(row.applyTime) }}</template>
      </el-table-column>
      <el-table-column prop="refundType" :label="$t('gl.refund.colMethod')" width="220" show-overflow-tooltip sortable>
        <template #default="{ row }">{{ calcDesc(row) }}</template>
      </el-table-column>
      <el-table-column prop="baseAmount" :label="$t('gl.special.colTaxBase')" width="120" align="right" sortable>
        <template #default="{ row }">￥{{ fmt(row.baseAmount) }}</template>
      </el-table-column>
      <el-table-column prop="rate" :label="$t('gl.refund.colRateRatio')" width="100" align="right" sortable>
        <template #default="{ row }">{{ row.refundType === 'CREDIT_REFUND' ? '—' : row.rate + '%' }}</template>
      </el-table-column>
      <el-table-column prop="taxAmount" :label="$t('gl.refund.colRefundable')" width="120" align="right" sortable>
        <template #default="{ row }"><b>￥{{ fmt(row.taxAmount) }}</b></template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusName(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="180" fixed="right">
        <template #default="{ row }">          <InlineActions :visible="true" @view="viewRefund(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="true" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('gl.special.colTrace') }}</el-button>
          <el-button v-if="row.status === BillStatus.DRAFT" size="small" type="primary" @click="submit(row)">{{ $t('common.submit') }}</el-button>
          <el-button size="small" @click="openFlow(row)">{{ $t('gl.refund.btnTransit') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="f.pageSize"
      :current-page="f.pageNum"
      @current-change="load"
    />

    <!-- 新建退税 -->
    <el-dialog v-model="createVisible" :title="$t('gl.refund.dlgNew')" width="460px" @open="onCreateOpen">
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="110px">
        <el-form-item :label="$t('gl.ledger.colCompany')" prop="companyId">
          <el-select v-model="form.companyId" :placeholder="$t('gl.refund.errCompany')" filterable style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('gl.refund.colKind')" prop="refundType">
          <el-select v-model="form.refundType" style="width: 100%" @change="onTypeChange">
            <el-option :label="$t('gl.refund.kindExport')" value="EXPORT_REFUND" />
            <el-option :label="$t('gl.refund.kindRetained')" value="CREDIT_REFUND" />
            <el-option :label="$t('gl.refund.kindInstant')" value="IMMEDIATE_REFUND" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('gl.consolidate.colBelong')" prop="period">
          <el-date-picker v-model="form.period" type="month" :placeholder="$t('gl.refund.errPeriod')" value-format="YYYYMM" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.refundType !== 'CREDIT_REFUND'" :label="$t('gl.special.colTaxBase')" prop="baseAmount">
          <el-input-number v-model="form.baseAmount" :precision="2" :min="0" style="width: 100%" :placeholder="baseHint" />
        </el-form-item>
        <el-form-item v-if="form.refundType !== 'CREDIT_REFUND'" :label="$t('gl.refund.colRatePct')" prop="rate">
          <el-input-number v-model="form.rate" :precision="2" :min="0.01" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.refundType === 'IMMEDIATE_REFUND'" :label="$t('gl.refund.btnPreview')">
          <span style="color: #e6a23c">{{ $t('gl.refund.hintRefundable', { v1: fmt(previewTax) }) }}</span>
          <span style="margin-left: 6px; color: #909399; font-size: 12px">{{ $t('gl.refund.formulaCap') }}</span>
        </el-form-item>
        <el-form-item v-if="form.refundType === 'CREDIT_REFUND'" :label="$t('gl.refund.colCap')">
          <span>￥{{ fmt(creditQuota) }}</span>
          <span style="margin-left: 6px; color: #909399; font-size: 12px">{{ $t('gl.refund.hintCap') }}</span>
        </el-form-item>
        <el-form-item v-if="form.refundType === 'CREDIT_REFUND'" :label="$t('gl.refund.colApplyAmount')" prop="taxAmount">
          <!-- 不设 :max：el-input-number 的 max 会在输入时就地钳值，change 拿到的是已被改小的数，
               用户只看到数字变了却没有任何提示。上限改由 change 处理器判并显式告知。 -->
          <el-input-number v-model="form.taxAmount" :precision="2" :min="0.01" style="width: 100%" @change="onCreditAmountChange" />
        </el-form-item>
        <el-form-item :label="$t('gl.special.colDesc')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="create">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 状态流转 -->
    <el-dialog v-model="flowVisible" :title="$t('gl.refund.dlgTransit')" width="420px">
      <el-form ref="flowFormRef" :rules="flowRules" :model="flow" label-width="110px">
        <el-form-item :label="$t('gl.refund.colCurrentStatus')">
          <el-tag :type="statusTag(flow.src)" size="small">{{ statusName(flow.src) }}</el-tag>
        </el-form-item>
        <el-form-item :label="$t('gl.refund.colTargetStatus')" prop="to">
          <el-select v-model="flow.to" style="width: 100%">
            <el-option v-for="s in flowTargets()" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="flow.to === 'REFUNDED'" :label="$t('gl.refund.colActualAmount')" prop="refundedAmount">
          <!-- 同上：到账金额的上限也用显式提示，不交给 :max 静默回填 -->
          <el-input-number v-model="flow.refundedAmount" :precision="2" :min="0" style="width: 100%" @change="onRefundedAmountChange" />
          <span style="color: #909399; font-size: 12px">{{ $t('gl.refund.hintArrival', { v1: fmt(flow.tax) }) }}</span>
        </el-form-item>
        <el-form-item :label="$t('common.remark')">
          <el-input v-model="flow.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="flowVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="changeStatus">{{ $t('gl.refund.btnConfirm') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="GL_TAX_REFUND" />
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>

const { rules: formRules } = useFormRules('TAX_REFUND')
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { showRowDetail, formatDateTime } from '@/utils/format'
import { glexApi, orgApi, userApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useAutoFill } from '@/composables/useAutoFill'
import InlineActions from '@/components/InlineActions.vue'
import { useFormRules, commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useKeyboard } from '@/composables/useKeyboard'
import { useApprovalScope } from '../../composables/useApprovalScope'
import { useApprovalDialog } from '@/composables/useApprovalDialog'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// 审批按钮归属门控：仅当该单据存在派给当前用户的待办任务时才显示，
// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()
// 快捷键
useKeyboard({
  'ctrl+s': () => { try { submit() } catch { /* noop */ } },
  'ctrl+r': () => { try { loadData() } catch { /* noop */ } },
})

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

// 两个表都包成 computed：静态数组/对象在 setup 求值时固化语言，切语言不换
const STATUS = computed(() => [
  { value: 'DRAFT', label: t('gl.consolidate.stDraft') },
  { value: 'APPLYING', label: t('gl.refund.stApproving') },
  { value: 'APPROVED', label: t('gl.refund.stApproved') },
  { value: 'REFUNDING', label: t('gl.refund.stRefunding') },
  { value: 'REFUNDED', label: t('gl.refund.stRefunded') },
  { value: 'REJECTED', label: t('gl.refund.stRejected') },
])
const TYPE = computed(() => ({
  EXPORT_REFUND: t('gl.refund.kindExport'),
  CREDIT_REFUND: t('gl.refund.kindRetained'),
  IMMEDIATE_REFUND: t('gl.refund.kindInstant'),
}))

const selRows = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)
const flowVisible = ref(false)
const companies = ref([])
/** 申请人 id → 姓名（列表只落 applyBy，逐行查接口不值当） */
const userNames = ref({})
function userName(id) {
  if (id == null) return '—'
  return userNames.value[id] || ('#' + id)
}
const creditQuota = ref(0)
const f = reactive({ pageNum: 1, pageSize: 10, refundType: '', status: '', period: '', keyword: '' })
const form = reactive({ companyId: null, refundType: 'EXPORT_REFUND', period: '', baseAmount: null, rate: 13, taxAmount: null, remark: '' })
const { autofill, saveFormHistory } = useAutoFill()
const flow = reactive({ id: null, src: '', to: '', remark: '', tax: null, refundedAmount: null })
const formRef = ref(null)
const flowFormRef = ref(null)

// 与后端 changeStatus 同一规则：→REFUNDED 必须登记实际到账金额，允许 0、不可为负；
// 上限（可退税额）由 onRefundedAmountChange 显式提示并回填，规则之外再兜一次提交前校验
const flowRules = computed(() => ({
  to: commonRules.select(t('gl.refund.errTargetStatus')),
  refundedAmount: flow.to === 'REFUNDED'
    ? [{ required: true, type: 'number', min: 0, message: t('gl.refund.errActualAmount') }]
    : [],
}))

const previewTax = computed(() => {
  const base = Number(form.baseAmount || 0)
  const rate = Number(form.rate || 0)
  return Math.round(base * rate) / 100
})

// 计税基数占位提示：须为 computed（曾误写为箭头函数导致 placeholder 显示函数源码）
const baseHint = computed(() => {
  if (form.refundType === 'EXPORT_REFUND') return t('gl.refund.baseExportSales')
  if (form.refundType === 'IMMEDIATE_REFUND') return t('gl.refund.basePaidVat')
  return t('gl.special.colTaxBase')
})
const calcDesc = (row) => {
  if (row.refundType === 'CREDIT_REFUND') return t('gl.refund.hintCreditCap')
  if (row.refundType === 'EXPORT_REFUND') return t('gl.refund.formulaExport')
  return t('gl.refund.formulaImmediate')
}
const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
const typeName = (code) => TYPE.value[code] || code
const statusName = (s) => (STATUS.value.find(x => x.value === s) || {}).label || s
const typeTag = (t) => (t === 'EXPORT_REFUND' ? 'success' : t === 'CREDIT_REFUND' ? 'warning' : 'danger')
const statusTag = (s) => s === 'REFUNDED' ? 'success' : s === BillStatus.REJECTED ? 'danger' : s === BillStatus.DRAFT ? 'info' : 'warning'

// 仅允许正向流转（DRAFT→APPLYING→APPROVED→REFUNDING→REFUNDED；任意未到账可驳回）
function flowTargets() {
  const src = flow.src
  const seq = ['DRAFT', 'APPLYING', 'APPROVED', 'REFUNDING', 'REFUNDED']
  // 与后端 changeStatus 的过审闸同口径：审批中只能先去「已审批」；已审批才能去「退税中/已退税」。
  // 之前一把给出全部后续态，用户点「已退税」必吃一次参数校验失败。
  let opts
  if (src === 'APPLYING') {
    opts = [{ value: 'APPROVED', label: statusName('APPROVED') }]
  } else if (src === 'APPROVED') {
    opts = [{ value: 'REFUNDING', label: statusName('REFUNDING') }, { value: 'REFUNDED', label: statusName('REFUNDED') }]
  } else {
    opts = seq.slice(seq.indexOf(src) + 1).map(v => ({ value: v, label: statusName(v) }))
  }
  if (src !== 'REFUNDED') opts.push({ value: 'REJECTED', label: t('gl.refund.actReject') })
  return opts
}

/** 到账金额超可退税额时给出显式提示（输入框不再挂 :max，钳制由这里做并说明原因） */
function onRefundedAmountChange(v) {
  const cap = Number(flow.tax || 0)
  if (v != null && cap > 0 && Number(v) > cap) {
    ElMessage.warning(`实际到账不得超过可退税额 ${fmt(cap)}，已按上限调整`)
    flow.refundedAmount = cap
  }
}

/** 留抵退税申请税额超可留抵余额：同一条口径，超填回填上限并明确提示 */
function onCreditAmountChange(v) {
  const cap = Number(creditQuota.value || 0)
  if (v != null && cap > 0 && Number(v) > cap) {
    ElMessage.warning(`申请税额不得超过留抵上限 ${fmt(cap)}，已按上限调整`)
    form.taxAmount = cap
  }
}

async function load(page) {
  if (page) f.pageNum = page
  loading.value = true
  try {
    const d = await glexApi.taxRefundPage(f.pageNum, f.pageSize, f.refundType || null, f.status || null, f.period || null, f.keyword || null)
    rows.value = d.list || []
    total.value = d.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadQuota() {
  try { creditQuota.value = Number(await glexApi.taxRefundCreditQuota()) || 0 } catch (e) { creditQuota.value = 0 }
}

function onCreateOpen() {
  if (form.refundType === 'CREDIT_REFUND') loadQuota()
}

function onTypeChange(v) {
  if (v === 'CREDIT_REFUND') loadQuota()
}

async function create() {
  if (!(await validateForms([formRef]))) return
  // 兜底：change 只在失焦/步进时触发，键盘直接回车提交也可能带着超限值过来
  if (form.refundType === 'CREDIT_REFUND') {
    const cap = Number(creditQuota.value || 0)
    if (cap > 0 && Number(form.taxAmount || 0) > cap) {
      ElMessage.warning(`申请税额不得超过留抵上限 ${fmt(cap)}，请调整后再保存`)
      return
    }
  }
  saving.value = true
  try {
    await glexApi.taxRefundCreate({
      companyId: form.companyId,
      refundType: form.refundType,
      period: form.period,
      baseAmount: form.baseAmount,
      rate: form.refundType === 'CREDIT_REFUND' ? 0 : form.rate,
      taxAmount: form.refundType === 'CREDIT_REFUND' ? form.taxAmount : 0,
      remark: form.remark,
    })
    ElMessage.success(t('gl.refund.msgCreated'))
    createVisible.value = false
    Object.assign(form, { companyId: null, refundType: 'EXPORT_REFUND', period: '', baseAmount: null, rate: 13, taxAmount: null, remark: '' })
    formRef.value?.clearValidate()
    load(f.pageNum)
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

async function submit(row) {
  saveFormHistory('TAX_REFUND', form.value || form)

  try {
    await glexApi.taxRefundSubmit(row.id)
    ElMessage.success(t('gl.refund.msgSubmitted'))
    load(f.pageNum)
  } catch (e) { ElMessage.error(e.message) }
}

/** 行内「查看」只出只读详情；状态流转是能改账的写动作，入口留给行末「流转」按钮。 */
function viewRefund(row) {
  return showRowDetail({
    refundNo: row.refundNo,
    refundType: typeName(row.refundType),
    period: row.period,
    calc: calcDesc(row),
    baseAmount: '￥' + fmt(row.baseAmount),
    rate: fmt(row.rate),
    taxAmount: '￥' + fmt(row.taxAmount),
    refundedAmount: '￥' + fmt(row.refundedAmount),
    status: statusName(row.status),
  }, {
    title: '退税详情',
    labels: { refundNo: '退（抵）税单号', refundType: '退税类型', period: '所属期', calc: '计算方式',
      baseAmount: '计税依据', rate: '退税率', taxAmount: '应退税额', refundedAmount: '已退税额', status: '状态' },
  })
}

function openFlow(row) {
  flow.id = row.id
  flow.src = row.status
  flow.to = ''
  flow.remark = ''
  flow.tax = row.taxAmount
  flow.refundedAmount = null
  flowFormRef.value?.clearValidate()
  flowVisible.value = true
}

async function changeStatus() {
  if (!(await validateForms([flowFormRef]))) return
  if (flow.to === 'REFUNDED') {
    const cap = Number(flow.tax || 0)
    if (cap > 0 && Number(flow.refundedAmount || 0) > cap) {
      ElMessage.warning(`实际到账不得超过可退税额 ${fmt(cap)}，请调整后再提交`)
      return
    }
  }
  try {
    await glexApi.taxRefundStatus(flow.id, flow.to, flow.remark, flow.to === 'REFUNDED' ? flow.refundedAmount : null)
    ElMessage.success(t('gl.refund.msgStatusTo') + statusName(flow.to))
    flowVisible.value = false
    load(f.pageNum)
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(async () => {
  autofill(form, 'TAX_REFUND')
  load(1)
  // 申请人姓名映射：列表只存 applyBy（用户 id），一次性拉人员表后本地映射，避免逐行查询
  try {
    const d = await userApi.page({ pageNum: 1, pageSize: 500 })
    const m = {}
    for (const u of (d?.list || [])) m[u.id] = u.nickName || u.userName || ('#' + u.id)
    userNames.value = m
  } catch (e) { /* 人员表读不到就退回 #id */ }
  try {
    const tree = (await orgApi.companyTree()) || []
    const flat = []
    const walk = (ns) => (ns || []).forEach((n) => {
      flat.push({ id: n.id, name: n.name || n.companyName || n.label })
      walk(n.children)
    })
    walk(tree)
    companies.value = flat
  } catch (e) { /* 忽略 */ }
})

function openRowDetail(row) {
  return showRowDetail(row, { title: t('gl.refund.colDetail') })
}

function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('gl.ledger.msgPickExport')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('gl.ledger.msgNoColumns')); return }
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
  a.download = `TaxRefund_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('gl.ledger.msgExportedN', { v1: selRows.value.length }))
}

// InlineActions handlers

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: load })
</script>