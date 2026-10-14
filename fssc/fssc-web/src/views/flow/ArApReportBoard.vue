<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>应收统计报表（开票 / 核销 / 票据 / 坏账 / 月结）</span>
        <div class="ops">
          <el-button size="small" @click="doExport">导出当前页签 CSV</el-button>
          <el-button size="small" @click="loadTab">刷新</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab" @tab-change="loadTab">
      <el-tab-pane label="开票统计" name="invoicing">
        <div class="ops" style="margin-bottom: 8px">
          <el-input v-model="invCustomer" size="small" style="width: 180px" placeholder="客户名称" clearable @keyup.enter="loadInvoicing" />
          <el-button size="small" type="primary" @click="loadInvoicing">查询</el-button>
        </div>
        <el-table :data="invoicingRows" v-loading="loading" size="small" border stripe show-summary :summary-method="summaryAmounts">
          <el-table-column prop="period" label="期间" width="100" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">{{ arBillingStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="count" label="发票张数" width="100" align="right" />
          <el-table-column prop="amount" label="金额合计" width="130" align="right" />
          <el-table-column prop="taxAmount" label="税额合计" width="130" align="right" />
          <el-table-column prop="totalAmount" label="价税合计" width="130" align="right" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="核销统计" name="writeoff">
        <div class="ops" style="margin-bottom: 8px">
          <el-input v-model="woCustomer" size="small" style="width: 180px" placeholder="客户名称" clearable @keyup.enter="loadWriteoff" />
          <el-button size="small" type="primary" @click="loadWriteoff">查询</el-button>
        </div>
        <el-table :data="writeoffRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="period" label="期间" width="100" />
          <el-table-column label="核销类型" width="110">
            <template #default="{ row }">{{ writeoffTypeLabel(row.writeoffType) }}</template>
          </el-table-column>
          <el-table-column prop="count" label="核销笔数" width="100" align="right" />
          <el-table-column prop="autoCount" label="其中自动" width="100" align="right" />
          <el-table-column prop="writeoffAmount" label="核销金额合计" width="140" align="right" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="票据统计" name="note">
        <el-table :data="noteRows" v-loading="loading" size="small" border stripe>
          <el-table-column label="票据类型" width="110">
            <template #default="{ row }">{{ row.noteType === 'PAPER' ? '纸质票据' : '电子票据' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">{{ arNoteStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="count" label="票据张数" width="110" align="right" />
          <el-table-column prop="amount" label="票面金额合计" width="140" align="right" />
          <el-table-column prop="discountInterest" label="贴现利息" width="130" align="right" />
          <el-table-column prop="discountAmount" label="贴现净额" width="130" align="right" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="坏账统计" name="badDebt">
        <div class="ops" style="margin-bottom: 8px">
          <el-input v-model="bdPeriod" size="small" style="width: 150px" placeholder="期间 yyyy-MM" clearable @keyup.enter="loadBadDebt" />
          <el-button size="small" type="primary" @click="loadBadDebt">查询</el-button>
        </div>
        <el-table :data="badDebtRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="period" label="期间" width="100" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ arBaddebtStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="count" label="笔数" width="90" align="right" />
          <el-table-column prop="provisionAmount" label="计提金额" width="130" align="right" />
          <el-table-column prop="writeoffAmount" label="核销金额" width="130" align="right" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="月结统计" name="monthClose">
        <div class="ops" style="margin-bottom: 8px">
          <el-input v-model="mcPeriod" size="small" style="width: 150px" placeholder="期间 yyyy-MM" clearable @keyup.enter="loadMonthClose" />
          <el-button size="small" type="primary" @click="loadMonthClose">查询</el-button>
        </div>
        <el-table :data="monthCloseRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="closeNo" label="月结单号" min-width="180" show-overflow-tooltip />
          <el-table-column prop="period" label="账期" width="90" />
          <el-table-column label="域" width="70">
            <template #default="{ row }"><el-tag size="small" :type="row.domain === 'AP' ? 'warning' : 'primary'">{{ row.domain }}</el-tag></template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ mcTypeLabel(row.closeType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">{{ arMcStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="扛数金额" width="115" align="right" :formatter="(row, col, val) => formatMoney(val)" />
          <el-table-column prop="diffAmount" label="差异" width="100" align="right" :formatter="(row, col, val) => formatMoney(val)" />
          <el-table-column label="关账时间" width="150">
            <template #default="{ row }">{{ row.closedTime ? formatDateTime(row.closedTime) : '—' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { arBillingStatusLabel, writeoffTypeLabel, arNoteStatusLabel, arBaddebtStatusLabel, mcTypeLabel, arMcStatusLabel } from '@/constants/enums'
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { arGapApi } from '../../api'
import { formatDateTime, formatMoney } from '../../utils/format'
import { exportCsv } from '../../utils/csv'


const tab = ref('invoicing')
const loading = ref(false)
const invoicingRows = ref([])
const writeoffRows = ref([])
const noteRows = ref([])
const badDebtRows = ref([])
const monthCloseRows = ref([])
const invCustomer = ref('')
const woCustomer = ref('')
const bdPeriod = ref('')
const mcPeriod = ref('')

const AMOUNT_COLS = ['amount', 'taxAmount', 'totalAmount', 'writeoffAmount', 'discountInterest', 'discountAmount', 'provisionAmount']
const COUNT_COLS = ['count', 'autoCount']

async function fetchRows(fn, target) {
  loading.value = true
  try {
    target.value = await fn()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const loadInvoicing = () => fetchRows(() => arGapApi.reportInvoicing({ customerName: invCustomer.value || undefined }), invoicingRows)
const loadWriteoff = () => fetchRows(() => arGapApi.reportWriteoff({ customerName: woCustomer.value || undefined }), writeoffRows)
const loadNote = () => fetchRows(() => arGapApi.reportNote(), noteRows)
const loadBadDebt = () => fetchRows(() => arGapApi.reportBadDebt({ period: bdPeriod.value || undefined }), badDebtRows)
const loadMonthClose = () => fetchRows(() => arGapApi.reportMonthClose({ period: mcPeriod.value || undefined }), monthCloseRows)

function loadTab() {
  const m = {
    invoicing: loadInvoicing,
    writeoff: loadWriteoff,
    note: loadNote,
    badDebt: loadBadDebt,
    monthClose: loadMonthClose,
  }
  m[tab.value]()
}

function summaryAmounts({ columns, data }) {
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    if (COUNT_COLS.includes(col.property)) {
      return data.reduce((s, r) => s + (Number(r[col.property]) || 0), 0)
    }
    if (AMOUNT_COLS.includes(col.property)) {
      return data.reduce((s, r) => s + (Number(r[col.property]) || 0), 0).toFixed(2)
    }
    return ''
  })
}

// 41号审查 P1-9：按页签导出统计结果
const TAB_EXPORT = {
  invoicing: () => [invoicingRows.value, [
    { prop: 'period', label: '期间' }, { prop: 'status', label: '状态' }, { prop: 'count', label: '发票张数' },
    { prop: 'amount', label: '金额合计' }, { prop: 'taxAmount', label: '税额合计' }, { prop: 'totalAmount', label: '价税合计' },
  ], 'ArReport_Invoicing'],
  writeoff: () => [writeoffRows.value, [
    { prop: 'period', label: '期间' }, { prop: 'writeoffType', label: '核销类型' }, { prop: 'count', label: '核销笔数' },
    { prop: 'autoCount', label: '其中自动' }, { prop: 'writeoffAmount', label: '核销金额合计' },
  ], 'ArReport_Writeoff'],
  note: () => [noteRows.value, [
    { prop: 'noteType', label: '票据类型' }, { prop: 'status', label: '状态' }, { prop: 'count', label: '票据张数' },
    { prop: 'amount', label: '票面金额合计' }, { prop: 'discountInterest', label: '贴现利息' }, { prop: 'discountAmount', label: '贴现净额' },
  ], 'ArReport_Note'],
  badDebt: () => [badDebtRows.value, [
    { prop: 'period', label: '期间' }, { prop: 'status', label: '状态' }, { prop: 'count', label: '笔数' },
    { prop: 'provisionAmount', label: '计提金额' }, { prop: 'writeoffAmount', label: '核销金额' },
  ], 'ArReport_BadDebt'],
  monthClose: () => [monthCloseRows.value, [
    { prop: 'closeNo', label: '月结单号' }, { prop: 'period', label: '账期' }, { prop: 'domain', label: '域' },
    { prop: 'closeType', label: '类型' }, { prop: 'status', label: '状态' }, { prop: 'amount', label: '扛数金额' },
    { prop: 'diffAmount', label: '差异' }, { prop: 'closedTime', label: '关账时间' },
  ], 'ArReport_MonthClose'],
}
function doExport() {
  const [rows, cols, name] = TAB_EXPORT[tab.value]()
  exportCsv(rows, cols, name)
}

onMounted(loadInvoicing)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
</style>
