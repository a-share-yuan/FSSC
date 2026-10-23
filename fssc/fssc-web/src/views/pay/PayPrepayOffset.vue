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

  <!-- 预付款选择弹窗（D-UI-05：勾选+可改冲抵金额+【确定】；导出按钮移入 toolbar，selection 语义不变） -->
  <CandidatePickerDialog v-model="prepayDialog" :title="$t('pay.offset.ctxPickTitle')"
                        :rows="prepayCandidates" max-field="available" :show-max-column="true"
                        extra-prop="title" :extra-label="$t('pay.shared.colTitle')"
                        :amount-label="$t('pay.offset.colPrepayAmount')"
                        :max-label="$t('pay.offset.colOffsettable')"
                        :input-label="$t('pay.offset.colThisOffsetAmount')"
                        :empty-text="$t('common.empty')"
                        :picked-ids="pickedPrepayIds"
                        @confirm="confirmPrepayPicks"
                        @selection-change="handleSelectionChange">
    <template #toolbar>
      <div style="display: flex; gap: 8px; margin-bottom: 6px">
        <el-button size="small" @click="exportCsv">{{ $t('pay.shared.btnExportCsv') }}</el-button>
        <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('pay.shared.btnExportSelected', { v1: selRows.length }) }}</el-button>
      </div>
    </template>
  </CandidatePickerDialog>
</template>

<script setup>
import { ref, computed, inject } from 'vue'
import { ElMessage } from 'element-plus'
import { billApi } from '../../api'
import CandidatePickerDialog from '../../components/bill/CandidatePickerDialog.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { formatMoney } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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
/** 已入账的预付单 id —— 选择器里置灰，防同一张预付单重复入账（D-UI-05） */
const pickedPrepayIds = computed(() => offsets.value.map((o) => o.billId))

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
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

/**
 * D-UI-05（2026-10-19）：【确定】后按对话框里改过的冲抵金额入账（默认=可用余额，可改，支持部分冲抵）。
 * 改前是点任意行立刻按 available 全额入账并关窗；且该弹窗同时挂着「复选框（供导出）」与「行点入账」
 * 两套语义、彼此不一致——现在统一为「勾选=要入账的行」。
 */
function confirmPrepayPicks(items) {
  const b = wb?.value?.b
  if (!b) return
  const arr = b.form.bill.extJson.prepayOffsets || []
  let added = 0
  for (const { row, amount } of items || []) {
    const exist = arr.find((o) => o.billId === row.id)
    if (exist) {
      exist.offsetAmount = amount
    } else {
      arr.push({ billId: row.id, billNo: row.billNo, totalAmount: row.totalAmount, available: row.available, offsetAmount: amount })
      added++
    }
  }
  emit('offset-change')
  if (added) ElMessage.success(t('pay.offset.msgPrepayAdded', { v: added }))
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
