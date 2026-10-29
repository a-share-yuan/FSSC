<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap">
      <el-input v-model="kw" :placeholder="$t('flow.arRecv.phKw')" clearable style="width:220px" @keyup.enter="loadReceivable" />
      <el-button type="primary" @click="loadReceivable">{{ $t('common.search') }}</el-button>
      <el-button type="success" @click="openReceivable">{{ $t('flow.arRecv.btnGenFromSo') }}</el-button>
      <!-- 留空＝按系统当前日跑（旧行为）；业务日期晚于系统日时必须选基准日，
           否则到期日永不早于基准日、逾期集恒空。与催收待办页签共用同一把尺子。 -->
      <el-date-picker v-model="overdueBaseline" type="date" value-format="YYYY-MM-DD" :placeholder="$t('flow.arRecv.phBaseline')" clearable size="small" style="width:250px" />
      <el-button type="warning" @click="accrueOverdue">{{ $t('flow.arRecv.btnOverdueProvision') }}</el-button>
      <el-button size="small" @click="exportCsv">{{ $t('flow.arRecv.btnExportCsv') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('flow.arRecv.btnExportSelected', { v1: selRows.length }) }}</el-button>
    </div>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="receivables" size="small" border stripe v-else @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" :empty-text="denied ? $t('flow.arRecv.emptyDenied') : $t('common.empty')">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" :label="$t('flow.arRecv.colDocNo')" width="160" sortable />
      <el-table-column :label="$t('flow.arRecv.colPartner')" min-width="120">
        <template #default="{ row }">{{ customerName(row.customerId) }}</template>
      </el-table-column>
      <!-- 客户超额预警：高风险信用档行展示橙色「信用预警」tag（仅展示，不阻断操作） -->
      <el-table-column :label="$t('flow.arRecv.colCreditLevel')" width="170" align="center">
        <template #default="{ row }">
          <template v-if="customerCreditLevel(row.customerId)">
            <el-tag :type="isHighRiskCustomer(row.customerId) ? 'danger' : 'success'" size="small">{{ customerCreditLevel(row.customerId) }}</el-tag>
            <el-tooltip v-if="isHighRiskCustomer(row.customerId)" :content="$t('flow.arRecv.tipHighRisk')" placement="top">
              <el-tag type="warning" effect="dark" size="small" style="margin-left: 4px">{{ $t('flow.arRecv.tagCreditAlert') }}</el-tag>
            </el-tooltip>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="batchNo" :label="$t('flow.arRecv.colBatchNo')" width="140" sortable />
      <el-table-column prop="currencyCode" :label="$t('flow.arRecv.colCurrency')" width="70" sortable />
      <el-table-column :label="$t('flow.arRecv.colReceivableAmount')" width="120" align="right">
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.arRecv.colReceivedAmount')" width="120" align="right">
        <template #default="{ row }">{{ fmt(row.receivedAmount) }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.arRecv.colRemainAmount')" width="120" align="right">
        <template #default="{ row }">{{ fmt(row.remainAmount) }}</template>
      </el-table-column>
      <el-table-column prop="dueDate" :label="$t('flow.arRecv.colDueDate')" width="110" sortable />
      <el-table-column :label="$t('flow.arRecv.colRecvState')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'DONE' ? 'success' : row.status === 'OVERDUE' ? 'danger' : 'warning'" size="small">
            {{ rcStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- 单据态是「收款规则」（NONE 直译"未开始"），审批期间看不出流程在跑 ⇒
           后端 page() 按 wf_instance 投影一列流程态（未起流程/审批中/审批通过/已驳回/已撤销/已终止）。
           当前节点与办理人仍在「流程进度」面板里看，本列只回答"审批走到没走完"。 -->
      <el-table-column :label="$t('flow.arRecv.colFlowState')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="flowTagType(row.flowStatus)" size="small" effect="plain">
            {{ row.flowStatusText || $t('flow.arRecv.textNoFlow') }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="recvTotal"
                   :page-size="pageSize" :current-page="pageNum" @current-change="(p) => { pageNum = p; loadReceivable() }" />

    <!-- 应收单生成弹窗 -->
    <el-dialog v-model="dlgReceivable" :title="$t('flow.arRecv.dlgGenTitle')" width="500px" destroy-on-close>
      <el-form ref="recvFormRef" :model="rForm" :rules="formRules" label-width="120px">
        <el-form-item :label="$t('flow.arRecv.labPartner')" prop="customerId">
          <CustomerSelect v-model="rForm.customerId" value-key="id" :placeholder="$t('flow.arRecv.phPartner')" />
        </el-form-item>
        <el-form-item :label="$t('flow.arRecv.labAmount')" prop="amount"><el-input-number v-model="rForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('flow.arRecv.labCurrency')" prop="currencyCode">
          <el-select v-model="rForm.currencyCode" style="width:100%">
            <el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.arRecv.labDueDate')" prop="dueDate"><el-date-picker v-model="rForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('flow.arRecv.labBatchNo')">
          <el-input v-model="rForm.batchNo" :placeholder="$t('flow.arRecv.phBatchNo')" />
          <div style="color:#909399;font-size:12px">{{ $t('flow.arRecv.hintBatchNo') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgReceivable = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveReceivable">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="RECEIVABLE" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { receivableApi, mdApi } from '@/api'
import SkeletonList from '@/components/SkeletonList.vue'
import BillTraceDrawer from '@/components/BillTraceDrawer.vue'
import CustomerSelect from '@/components/common/CustomerSelect.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { showRowDetail } from '@/utils/format'
import { useCurrency } from '@/composables/useCurrency'
import { rcStatusLabel } from '@/constants/enums'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

useKeyboard({
  'ctrl+r': () => { try { loadReceivable() } catch { /* noop */ } },
})

const traceVisible = ref(false)
const traceBillId = ref(null)

// 流程态配色（wf_instance.status）——审批中给主色，最终状态按结果分色，未起流程灰
function flowTagType(s) {
  return { RUNNING: 'primary', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info', TERMINATED: 'info' }[s] || 'info'
}

function openRowDetail(row) {
  traceBillId.value = row.id
  traceVisible.value = true
  return showRowDetail(row, { title: t('flow.arRecv.ttlDetail') })
}

// 客户/供应商列表（用于往来单位 ID → 名称）
const selRows = ref([])
const customers = ref([])
function customerName(id) { const c = customers.value.find((x) => x.id === id); return c ? (c.customerName || c.supplierName || c.name || '—') : (id ? '#' + id : '—') }

// 客户信用等级：等级取值 A/B/C（见 master/Customer.vue），C 为最低档（高风险）
function customerCreditLevel(id) {
  const c = customers.value.find((x) => x.id === id)
  return c?.creditLevel || ''
}
// includes('高风险') 匹配的是业务取值本身而非界面文案，故不入 i18n
function isHighRiskCustomer(id) {
  const lv = String(customerCreditLevel(id) || '').toUpperCase()
  return !!lv && (lv.includes('C') || lv.includes('高风险'))
}

const loading = ref(false)
const kw = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const receivables = ref([])
const recvTotal = ref(0)
// 4030 权限拒绝独立空态（原先与「暂无数据」同形，无权限被读成没数据）
const denied = ref(false)
const overdueBaseline = ref('')

const dlgReceivable = ref(false)
const rForm = ref({ customerId: null, orderId: null, amount: 0, currencyCode: 'CNY', dueDate: '', batchNo: '' })
const recvFormRef = ref(null)
const { rules: formRules } = useFormRules('RECEIVABLE')
const { submit: guardReceivable } = useSubmitGuard(recvFormRef)

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const route = useRoute()
const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency()

async function loadReceivable() {
  loading.value = true
  try {
    const d = await receivableApi.page({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: kw.value })
    receivables.value = d.list || []
    recvTotal.value = Number(d.total || 0)
    denied.value = false
  } catch (e) {
    // 4030（无应收读权限）置权限空态，不再落到「暂无数据」
    if (e?.code === 4030) { denied.value = true; return }
    ElMessage.error(e.message)
  } finally { loading.value = false }
}

async function openReceivable() { rForm.value = { customerId: null, orderId: null, amount: 0, currencyCode: 'CNY', dueDate: '', batchNo: '' }; dlgReceivable.value = true }
async function saveReceivable() {
  await guardReceivable(async () => {
    try { await receivableApi.create(rForm.value); ElMessage.success(t('flow.arRecv.msgGenerated')); dlgReceivable.value = false; loadReceivable() }
    catch (e) { ElMessage.error(e.message) }
  })
}

/** 界面按截点日期选择，留空＝后端回退系统当前日 */
function overdueBaselineParams() {
  const v = String(overdueBaseline.value || '').trim()
  return v ? { asOfDate: v } : {}
}

function baselineSuffix(baseline) {
  return baseline.period || baseline.asOfDate
    ? t('flow.arRecv.msgBaselineSuffix', { v1: baseline.asOfDate || baseline.period })
    : ''
}

async function accrueOverdue() {
  let baseline
  try { baseline = overdueBaselineParams() } catch (e) { return ElMessage.error(e.message) }
  try {
    const n = await receivableApi.accrueOverdue(baseline)
    ElMessage.success(t('flow.arRecv.msgAccrued', { v1: n }) + baselineSuffix(baseline))
    loadReceivable()
  } catch (e) { ElMessage.error(e.message) }
}

function reload() { loadReceivable() }

// 消费半：工作台台账跳转同写 kw 与 billNo/billId 两套取参规则，本页两套都吃
onMounted(() => {
  loadCurrency()
  const q = route.query || {}
  const deep = q.billNo || q.kw || q.billId
  if (deep) kw.value = String(deep)
  mdApi.customers({ pageNum: 1, pageSize: 200 }).then((d) => { customers.value = d?.list || [] }).catch(() => {})
  reload()
})

function exportCsv() {
  if (!receivables.value.length) { ElMessage.warning(t('flow.arRecv.msgNoExportData')); return }
  const cols = [
    { prop: 'billNo', label: t('flow.arRecv.colDocNo') },
    { prop: 'batchNo', label: t('flow.arRecv.colBatchNo') },
    { prop: 'currencyCode', label: t('flow.arRecv.colCurrency') },
    { prop: 'dueDate', label: t('flow.arRecv.colDueDate') },
  ]
  const header = cols.map((c) => c.label).join(',')
  const lines = receivables.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `ReceivableBoard_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('flow.arRecv.msgCsvDone'))
}

function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('flow.arRecv.msgNeedSelection')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('flow.arRecv.msgNoColumns')); return }
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
  a.download = `ReceivableBoard_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('flow.arRecv.msgBatchDone', { v1: selRows.value.length }))
}
</script>

<style scoped>
.pager { margin-top: 10px; justify-content: flex-end; }
</style>
