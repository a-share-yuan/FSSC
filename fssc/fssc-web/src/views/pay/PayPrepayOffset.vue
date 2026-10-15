<template>
  <!-- ============ 冲销预付款（对公结算） ============ -->
  <el-card :header="$t('pay.offset.cardTitle')" style="margin-top: 12px">
    <el-button size="small" :disabled="!customerId" @click="loadPrepay">{{ $t('pay.offset.btnRefreshPrepay') }}</el-button>
    <el-table :data="offsets" size="small" border style="margin-top: 8px" :empty-text="$t('common.empty')">
      <el-table-column prop="billNo" :label="$t('pay.offset.colPrepayNo')" width="160" sortable />
      <el-table-column :label="$t('pay.offset.colPrepayAmount')" width="110">
        <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column :label="$t('pay.offset.colOffsettableAmount')" width="110">
        <template #default="{ row }"><span style="color:#f59a23">{{ fmt(row.available) }}</span></template>
      </el-table-column>
      <el-table-column :label="$t('pay.offset.colThisOffsetAmount')" width="150"><template #header><span style="color:#f56c6c">*</span>{{ $t('pay.offset.colThisOffsetAmount') }}</template>
        <template #default="{ row }">
          <el-input-number v-model="row.offsetAmount" :min="0" :max="Number(row.available) || 0" :precision="2" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="70">
        <template #default="{ $index }">
          <el-button link size="small" type="danger" @click="removePrepay($index)">{{ $t('pay.shared.btnDeleteShort') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-alert type="info" :closable="false" :title="$t('pay.offset.hintRule')" style="margin-top: 6px" />
  </el-card>

  <!-- 预付款选择弹窗 -->
  <el-dialog v-model="prepayDialog" :title="$t('pay.offset.ctxPickTitle')" width="640px">
    <el-table :data="prepayCandidates" size="small" @row-click="pickPrepay" highlight-current-row @selection-change="handleSelectionChange" :empty-text="$t('common.empty')">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" :label="$t('pay.offset.colPrepayNo')" width="160" sortable />
      <el-table-column prop="title" :label="$t('pay.shared.colTitle')" min-width="200" show-overflow-tooltip sortable />
      <el-table-column prop="totalAmount" :label="$t('pay.offset.colPrepayAmount')" width="110" sortable :formatter="(r,c,v) => formatMoney(v)" />
      <el-table-column :label="$t('pay.offset.colOffsettable')" width="110">
        <template #default="{ row }">{{ fmt2(row) }}</template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button size="small" @click="exportCsv">{{ $t('pay.shared.btnExportCsv') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('pay.shared.btnExportSelected', { v1: selRows.length }) }}</el-button>
      <el-button @click="prepayDialog = false">{{ $t('common.close') }}</el-button>
    </template>
  </el-dialog>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ref, computed, inject } from 'vue'
import { ElMessage } from 'element-plus'
import { billApi } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { formatMoney } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const props = defineProps({
  customerId: { type: [Number, String], default: null },
})
const emit = defineEmits(['offset-change'])

const selRows = ref([])
const prepayDialog = ref(false)
const prepayCandidates = ref([])
// inject the bill context from parent CorpPay
const wb = inject('wb', null)

const offsets = computed(() => {
  const b = wb?.value?.b
  if (!b) return []
  if (!b.form.bill.extJson.prepayOffsets) b.form.bill.extJson.prepayOffsets = []
  return b.form.bill.extJson.prepayOffsets
})

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
function fmt2(row) { return row.available == null ? '0.00' : Number(row.available).toFixed(2) }
function handleSelectionChange(val) { selRows.value = val }

async function loadPrepay() {
  if (!props.customerId) { ElMessage.warning(t('pay.offset.msgPickPartnerFirst')); return }
  const list = (await billApi.myPage({ pageNum: 1, pageSize: 100, billType: 'PAY', billSubType: 'PREPAY', status: 'APPROVED' })).list || []
  prepayCandidates.value = list.map((x) => ({
    id: x.id, billNo: x.billNo, title: x.title, totalAmount: Number(x.totalAmount || 0),
    available: Math.max(0, Number(x.totalAmount || 0) - Number(x.offsetAmount || 0)),
  }))
  prepayDialog.value = true
}

function pickPrepay(row) {
  const b = wb?.value?.b
  if (!b) return
  const arr = b.form.bill.extJson.prepayOffsets || []
  if (!arr.find((o) => o.billId === row.id)) {
    arr.push({ billId: row.id, billNo: row.billNo, totalAmount: row.totalAmount, available: row.available, offsetAmount: row.available })
    emit('offset-change')
  }
  prepayDialog.value = false
}

function removePrepay(i) {
  const b = wb?.value?.b
  if (!b) return
  ;(b.form.bill.extJson.prepayOffsets || []).splice(i, 1)
  emit('offset-change')
}

function exportCsv() {
  if (!prepayCandidates.value.length) { ElMessage.warning(t('pay.shared.msgNoExportData')); return }
  const cols = [{ prop: 'billNo', label: t('pay.offset.colPrepayNo') }, { prop: 'title', label: t('pay.shared.colTitle') }, { prop: 'totalAmount', label: t('pay.offset.colPrepayAmount') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = prepayCandidates.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `CorpPay_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('pay.shared.msgExportedCsv'))
}

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('pay.shared.msgPickExportRows')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('pay.shared.msgNoColumns')); return }
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
  a.download = `CorpPay_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('pay.shared.msgExportedRows', { v1: selRows.value.length }))
}

defineExpose({ loadPrepay })
</script>
