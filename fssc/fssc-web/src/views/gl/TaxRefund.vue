<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>税务退税三场景</b>
        <el-select v-model="f.refundType" placeholder="退税类型" clearable style="width: 180px">
          <el-option label="出口免抵退" value="EXPORT_REFUND" />
          <el-option label="留抵退税" value="CREDIT_REFUND" />
          <el-option label="即征即退" value="IMMEDIATE_REFUND" />
        </el-select>
        <el-select v-model="f.status" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="s in STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-date-picker v-model="f.period" type="month" placeholder="所属期" value-format="YYYYMM" clearable style="width: 140px" />
        <el-input v-model="f.keyword" placeholder="退税单号/期" clearable style="width: 160px" />
        <el-button type="primary" size="small" @click="load(1)">查询</el-button>
        <el-button size="small" @click="createVisible = true">新建退税</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" v-else size="small" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="refundNo" label="退税单号" width="180"  sortable />
      <el-table-column label="退税类型" width="120">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.refundType)" size="small">{{ typeName(row.refundType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="period" label="所属期" width="90"  sortable />
      <el-table-column prop="refundType" label="计税方式" width="220" show-overflow-tooltip sortable>
        <template #default="{ row }">{{ calcDesc(row) }}</template>
      </el-table-column>
      <el-table-column prop="baseAmount" label="计税基数" width="120" align="right" sortable>
        <template #default="{ row }">￥{{ fmt(row.baseAmount) }}</template>
      </el-table-column>
      <el-table-column prop="rate" label="退税率/比例" width="100" align="right" sortable>
        <template #default="{ row }">{{ row.refundType === 'CREDIT_REFUND' ? '—' : row.rate + '%' }}</template>
      </el-table-column>
      <el-table-column prop="taxAmount" label="可退税额" width="120" align="right" sortable>
        <template #default="{ row }"><b>￥{{ fmt(row.taxAmount) }}</b></template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusName(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">          <InlineActions :visible="true" @view="openFlow(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="true" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <el-button v-if="row.status === BillStatus.DRAFT" size="small" type="primary" @click="submit(row)">提交</el-button>
          <el-button size="small" @click="openFlow(row)">状态流转</el-button>
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
    <el-dialog v-model="createVisible" title="新建退税申请" width="460px" @open="onCreateOpen">
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="110px">
        <el-form-item label="公司" prop="companyId">
          <el-select v-model="form.companyId" placeholder="请选择公司" filterable style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="退税类型" prop="refundType">
          <el-select v-model="form.refundType" style="width: 100%" @change="onTypeChange">
            <el-option label="出口免抵退" value="EXPORT_REFUND" />
            <el-option label="留抵退税" value="CREDIT_REFUND" />
            <el-option label="即征即退" value="IMMEDIATE_REFUND" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属期" prop="period">
          <el-date-picker v-model="form.period" type="month" placeholder="请选择所属期" value-format="YYYYMM" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.refundType !== 'CREDIT_REFUND'" label="计税基数" prop="baseAmount">
          <el-input-number v-model="form.baseAmount" :precision="2" :min="0" style="width: 100%" :placeholder="baseHint" />
        </el-form-item>
        <el-form-item v-if="form.refundType !== 'CREDIT_REFUND'" label="退税率 %" prop="rate">
          <el-input-number v-model="form.rate" :precision="2" :min="0.01" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.refundType === 'IMMEDIATE_REFUND'" label="算税预览">
          <span style="color: #e6a23c">可退税额 ≈ ￥{{ fmt(previewTax) }}</span>
          <span style="margin-left: 6px; color: #909399; font-size: 12px">= 已缴增值税 × 比例 / 100</span>
        </el-form-item>
        <el-form-item v-if="form.refundType === 'CREDIT_REFUND'" label="留抵上限">
          <span>￥{{ fmt(creditQuota) }}</span>
          <span style="margin-left: 6px; color: #909399; font-size: 12px">申请税额不得超过此上限</span>
        </el-form-item>
        <el-form-item v-if="form.refundType === 'CREDIT_REFUND'" label="申请税额" prop="taxAmount">
          <el-input-number v-model="form.taxAmount" :precision="2" :min="0.01" :max="creditQuota || undefined" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">保存</el-button>
      </template>
    </el-dialog>

    <!-- 状态流转 -->
    <el-dialog v-model="flowVisible" title="退税状态流转" width="420px">
      <el-form ref="flowFormRef" :rules="flowRules" :model="flow" label-width="110px">
        <el-form-item label="当前状态">
          <el-tag :type="statusTag(flow.src)" size="small">{{ statusName(flow.src) }}</el-tag>
        </el-form-item>
        <el-form-item label="目标状态" prop="to">
          <el-select v-model="flow.to" style="width: 100%">
            <el-option v-for="s in flowTargets()" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="flow.to === 'REFUNDED'" label="实际到账金额" prop="refundedAmount">
          <el-input-number v-model="flow.refundedAmount" :precision="2" :min="0" :max="flow.tax || undefined" style="width: 100%" />
          <span style="color: #909399; font-size: 12px">退税到账(→已退税)需登记实际到账金额，不得超过可退税额 ￥{{ fmt(flow.tax) }}</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="flow.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="flowVisible = false">取消</el-button>
        <el-button type="primary" @click="changeStatus">确认</el-button>
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
import { showRowDetail } from '@/utils/format'
import { glexApi, orgApi } from '../../api'
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

// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()
// R5-13: 快捷键
useKeyboard({
  'ctrl+s': () => { try { submit() } catch { /* noop */ } },
  'ctrl+r': () => { try { loadData() } catch { /* noop */ } },
})

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const STATUS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPLYING', label: '审批中' },
  { value: 'APPROVED', label: '已审批' },
  { value: 'REFUNDING', label: '退税中' },
  { value: 'REFUNDED', label: '已退税' },
  { value: 'REJECTED', label: '已驳回' },
]
const TYPE = {
  EXPORT_REFUND: '出口免抵退',
  CREDIT_REFUND: '留抵退税',
  IMMEDIATE_REFUND: '即征即退',
}

const selRows = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)
const flowVisible = ref(false)
const companies = ref([])
const creditQuota = ref(0)
const f = reactive({ pageNum: 1, pageSize: 10, refundType: '', status: '', period: '', keyword: '' })
const form = reactive({ companyId: null, refundType: 'EXPORT_REFUND', period: '', baseAmount: null, rate: 13, taxAmount: null, remark: '' })
const { autofill, saveFormHistory } = useAutoFill()
const flow = reactive({ id: null, src: '', to: '', remark: '', tax: null, refundedAmount: null })
const formRef = ref(null)
const flowFormRef = ref(null)

// 与后端 changeStatus 同口径：→REFUNDED 必须登记实际到账金额，允许 0、不可为负，上限由 el-input-number max 兜
const flowRules = computed(() => ({
  to: commonRules.select('请选择目标状态'),
  refundedAmount: flow.to === 'REFUNDED'
    ? [{ required: true, type: 'number', min: 0, message: '请登记实际到账金额（不能为负）' }]
    : [],
}))

const previewTax = computed(() => {
  const base = Number(form.baseAmount || 0)
  const rate = Number(form.rate || 0)
  return Math.round(base * rate) / 100
})

// 计税基数占位提示：须为 computed（曾误写为箭头函数导致 placeholder 显示函数源码）
const baseHint = computed(() => {
  if (form.refundType === 'EXPORT_REFUND') return '出口销售额'
  if (form.refundType === 'IMMEDIATE_REFUND') return '已缴增值税'
  return '计税基数'
})
const calcDesc = (row) => {
  if (row.refundType === 'CREDIT_REFUND') return '申请税额不超认证进项可留抵余额'
  if (row.refundType === 'EXPORT_REFUND') return '计税基数 × 免抵退率 / 100'
  return '已缴增值税 × 即征即退比例 / 100'
}
const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
const typeName = (t) => TYPE[t] || t
const statusName = (s) => (STATUS.find(x => x.value === s) || {}).label || s
const typeTag = (t) => (t === 'EXPORT_REFUND' ? 'success' : t === 'CREDIT_REFUND' ? 'warning' : 'danger')
const statusTag = (s) => s === 'REFUNDED' ? 'success' : s === BillStatus.REJECTED ? 'danger' : s === BillStatus.DRAFT ? 'info' : 'warning'

// 仅允许正向流转（DRAFT→APPLYING→APPROVED→REFUNDING→REFUNDED；任意未到账可驳回）
function flowTargets() {
  const src = flow.src
  const seq = ['DRAFT', 'APPLYING', 'APPROVED', 'REFUNDING', 'REFUNDED']
  const opts = seq.slice(seq.indexOf(src) + 1).map(v => ({ value: v, label: statusName(v) }))
  if (src !== 'REFUNDED') opts.push({ value: 'REJECTED', label: '驳回' })
  return opts
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
    ElMessage.success('退税申请已创建（草稿）')
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
    ElMessage.success('已提交审批')
    load(f.pageNum)
  } catch (e) { ElMessage.error(e.message) }
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
  try {
    await glexApi.taxRefundStatus(flow.id, flow.to, flow.remark, flow.to === 'REFUNDED' ? flow.refundedAmount : null)
    ElMessage.success('状态已流转为 ' + statusName(flow.to))
    flowVisible.value = false
    load(f.pageNum)
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(async () => {
  autofill(form, 'TAX_REFUND')
  load(1)
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
  return showRowDetail(row, { title: '详情' })
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
  a.download = `TaxRefund_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

// InlineActions handlers

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: load })
</script>