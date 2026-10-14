<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center">
        <b>合同付款查询</b>
        <el-input v-model="kw" placeholder="合同号/名称" clearable style="width: 220px" @keyup.enter="search" />
        <el-button type="primary" size="small" @click="search">查询</el-button>
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        <span style="color: #909399; font-size: 13px">报销单/付款单引用合同后支付记录自动更新；线下支付可手工添加</span>
      </div>
    </template>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="pagedData" size="small" stripe v-else max-height="480" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="contractNo" label="合同编号" width="150"  sortable />
      <el-table-column prop="contractName" label="合同名称" min-width="150" show-overflow-tooltip  sortable />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ contractTypeLabel(row.contractType) }}</template>
      </el-table-column>
      <el-table-column prop="partyBName" label="乙方" width="120" show-overflow-tooltip  sortable />
      <el-table-column prop="contractAmount" label="合同金额" width="110" align="right" sortable>
        <template #default="{ row }">{{ fmt(row.contractAmount) }}</template>
      </el-table-column>
      <el-table-column prop="paidAmount" label="已付款金额" width="110" align="right" sortable>
        <template #default="{ row }">
          <span style="color: #f56c6c">{{ fmt(row.paidAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" sortable>
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <el-button link size="small" type="primary" @click="showRecords(row)">支付记录</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TablePager :pager="pager" @change="load" />

    <el-dialog v-model="recVis" :title="'支付记录 - ' + (current?.contractName || '')" width="640px" destroy-on-close>
      <el-table :data="records" size="small" border max-height="360" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="bizNo" label="单据号" width="150"  sortable />
        <el-table-column label="单据类型" width="100">
          <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="付款金额" width="110" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="payDate" label="支付日期" width="110"  sortable :formatter="(row, col, val) => formatDate(val)" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip  sortable />
      </el-table>
      <el-empty v-if="!records.length" description="暂无支付记录" />
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

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.contractId || row.id
  traceVisible.value = true
}

const selRows = ref([])
const rows = ref([])
// P0-1: 合同付款查询本地分页（后端未分页，前端切片）
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
    // P0-2: 后端分页 + 关键字过滤（kw 在服务端 LIKE contractNo/contractName）
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
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'contractNo', label: '合同编号' }, { prop: 'contractName', label: '合同名称' }, { prop: 'partyBName', label: '乙方' }, { prop: 'contractAmount', label: '合同金额' }, { prop: 'paidAmount', label: '已付款金额' }, { prop: 'status', label: '状态' }]
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
  ElMessage.success('已导出 CSV')
}


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
  a.download = `ContractPay_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>
