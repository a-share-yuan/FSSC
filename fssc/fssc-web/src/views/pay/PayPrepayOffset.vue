<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <!-- ============ 冲销预付款（对公结算） ============ -->
  <el-card header="冲销预付款（该供应商未冲销的预付款自动带出）" style="margin-top: 12px">
    <el-button size="small" :disabled="!customerId" @click="loadPrepay">刷新该供应商预付款</el-button>
    <el-table :data="offsets" size="small" border style="margin-top: 8px" empty-text="暂无数据">
      <el-table-column prop="billNo" label="预付款单号" width="160" sortable />
      <el-table-column label="预付款金额" width="110">
        <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="可冲销金额" width="110">
        <template #default="{ row }"><span style="color:#f59a23">{{ fmt(row.available) }}</span></template>
      </el-table-column>
      <el-table-column label="本次冲销金额" width="150"><template #header><span style="color:#f56c6c">*</span>本次冲销金额</template>
        <template #default="{ row }">
          <el-input-number v-model="row.offsetAmount" :min="0" :max="Number(row.available) || 0" :precision="2" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70">
        <template #default="{ $index }">
          <el-button link size="small" type="danger" @click="removePrepay($index)">删</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-alert type="info" :closable="false" title="冲销金额不能超过可冲销金额；提交后按冲销金额释放对应预付款占用的预算" style="margin-top: 6px" />
  </el-card>

  <!-- 预付款选择弹窗 -->
  <el-dialog v-model="prepayDialog" title="选择该供应商未冲销的预付款" width="640px">
    <el-table :data="prepayCandidates" size="small" @row-click="pickPrepay" highlight-current-row @selection-change="handleSelectionChange" empty-text="暂无数据">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" label="预付款单号" width="160" sortable />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip sortable />
      <el-table-column prop="totalAmount" label="预付款金额" width="110" sortable :formatter="(r,c,v) => formatMoney(v)" />
      <el-table-column label="可冲销" width="110">
        <template #default="{ row }">{{ fmt2(row) }}</template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
      <el-button @click="prepayDialog = false">关闭</el-button>
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
  if (!props.customerId) { ElMessage.warning('请先在右侧选择往来单位'); return }
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
  if (!prepayCandidates.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'billNo', label: '预付款单号' }, { prop: 'title', label: '标题' }, { prop: 'totalAmount', label: '预付款金额' }]
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
  ElMessage.success('已导出 CSV')
}

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
  a.download = `CorpPay_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

defineExpose({ loadPrepay })
</script>
