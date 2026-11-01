<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center">
        <b>{{ $t('pay.contract.pageTitle') }}</b>
        <el-input v-model="kw" :placeholder="$t('pay.contract.phSearch')" clearable style="width: 220px" @keyup.enter="search" />
        <el-button type="primary" size="small" @click="search">{{ $t('common.search') }}</el-button>
      <el-button size="small" @click="exportCsv">{{ $t('pay.shared.btnExportCsv') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('pay.shared.btnExportSelected', { v1: selRows.length }) }}</el-button>
        <span style="color: #909399; font-size: 13px">{{ $t('pay.contract.hintAutoRecords') }}</span>
      </div>
    </template>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="pagedData" size="small" stripe v-else max-height="480" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="contractNo" :label="$t('pay.contract.colContractNo')" width="150"  sortable />
      <el-table-column prop="contractName" :label="$t('pay.shared.colContractName')" min-width="150" show-overflow-tooltip  sortable />
      <el-table-column :label="$t('field.type')" width="90">
        <template #default="{ row }">{{ contractTypeLabel(row.contractType) }}</template>
      </el-table-column>
      <el-table-column prop="partyBName" :label="$t('pay.contract.colPartyB')" width="120" show-overflow-tooltip  sortable />
      <el-table-column prop="contractAmount" :label="$t('pay.shared.colContractAmount')" width="110" align="right" sortable>
        <template #default="{ row }">{{ fmt(row.contractAmount) }}</template>
      </el-table-column>
      <el-table-column prop="paidAmount" :label="$t('pay.contract.colPaidAmount')" width="110" align="right" sortable>
        <template #default="{ row }">
          <span style="color: #f56c6c">{{ fmt(row.paidAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="$t('common.status')" width="100" sortable>
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('pay.shared.btnTrace') }}</el-button>
          <el-button link size="small" type="primary" @click="showRecords(row)">{{ $t('pay.contract.btnRecords') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TablePager :pager="pager" @change="load" />

    <el-dialog v-model="recVis" :title="$t('pay.contract.titleRecords', { v1: current?.contractName || '' })" width="700px" destroy-on-close>
      <el-table :data="records" size="small" border max-height="360" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="bizNo" :label="$t('pay.contract.colBizNo')" width="150"  sortable />
        <el-table-column :label="$t('field.billType')" width="100">
          <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
        </el-table-column>
        <el-table-column prop="amount" :label="$t('pay.contract.colPayAmount')" width="110" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="payDate" :label="$t('pay.contract.colPayDate')" width="110"  sortable :formatter="(row, col, val) => formatDate(val)" />
        <el-table-column prop="remark" :label="$t('common.remark')" min-width="120" show-overflow-tooltip  sortable />
      </el-table>
      <el-empty v-if="!records.length" :description="$t('pay.contract.hintNoRecords')" />
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="CONTRACT_APPROVE" />
</template>

<script setup>
import { contractTypeLabel, bizTypeLabel } from '@/constants/enums'
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { reportApi, contractApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.contractId || row.id
  traceVisible.value = true
}

const selRows = ref([])
const rows = ref([])
// 合同付款查询本地分页（后端未分页，前端切片）
const pager = useTablePager({ mode: 'server', storageKey: 'contract_pay' })
const { pagedData } = pager
const loading = ref(false)
const kw = ref('')
const recVis = ref(false)
const records = ref([])
const current = ref(null)

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }

async function load() {
  loading.value = true
  try {
    // 后端分页 + 关键字过滤（kw 在服务端 LIKE contractNo/contractName）
    await pager.load(async ({ pageNum, pageSize }) => {
      const r = await reportApi.contractSummaryPage({ pageNum, pageSize, kw: kw.value || undefined })
      rows.value = r.list || []
      return { list: rows.value, total: r.total || 0 }
    })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

/** 搜索条件变化 → 回到第 1 页再查 */
function search() {
  pager.resetPage()
  load()
}

async function showRecords(row) {
  current.value = row
  try {
    // 按合同号反查合同 id → 支付记录
    const p = await contractApi.page({ pageNum: 1, pageSize: 5, keyword: row.contractNo })
    const c = (p.list || []).find((x) => x.contractNo === row.contractNo)
    records.value = c ? (await contractApi.records(c.id)) || [] : []
    recVis.value = true
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning(t('pay.shared.msgNoExportData')); return }
  const cols = [{ prop: 'contractNo', label: t('pay.contract.colContractNo') }, { prop: 'contractName', label: t('pay.shared.colContractName') }, { prop: 'partyBName', label: t('pay.contract.colPartyB') }, { prop: 'contractAmount', label: t('pay.shared.colContractAmount') }, { prop: 'paidAmount', label: t('pay.contract.colPaidAmount') }, { prop: 'status', label: t('common.status') }]
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
  a.download = `ContractPay_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('pay.shared.msgExportedCsv'))
}


function openRowDetail(row) {
  return showRowDetail(row, { title: t('field.detail') })
}

function handleSelectionChange(val) { selRows.value = val }

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
  a.download = `ContractPay_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('pay.shared.msgExportedRows', { v1: selRows.value.length }))
}

</script>
