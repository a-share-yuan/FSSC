<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.report.title') }}</span>
        <div class="ops">
          <el-button size="small" @click="doExport">{{ $t('flow.report.exportTabCsv') }}</el-button>
          <el-button size="small" @click="loadTab">{{ $t('common.refresh') }}</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab" @tab-change="loadTab">
      <el-tab-pane :label="$t('flow.report.secInvoicing')" name="invoicing">
        <div class="ops" style="margin-bottom: 8px">
          <CustomerSelect v-model="invCustomerCode" style="width: 220px" :placeholder="$t('flow.collab.phPickCustomer')" @change="onInvCustomer" />
          <el-button size="small" type="primary" @click="loadInvoicing">{{ $t('common.search') }}</el-button>
        </div>
        <el-table :data="invoicingRows" v-loading="loading" size="small" border stripe show-summary :summary-method="summaryAmounts">
          <el-table-column prop="period" :label="$t('field.period')" width="100" />
          <el-table-column :label="$t('common.status')" width="120">
            <template #default="{ row }">{{ arBillingStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="count" :label="$t('flow.report.colInvoiceCount')" width="100" align="right" />
          <el-table-column prop="amount" :label="$t('flow.report.colAmountTotal')" width="130" align="right" />
          <el-table-column prop="taxAmount" :label="$t('flow.report.colTaxTotal')" width="130" align="right" />
          <el-table-column prop="totalAmount" :label="$t('field.totalWithTax')" width="130" align="right" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="$t('flow.report.secWriteOff')" name="writeoff">
        <div class="ops" style="margin-bottom: 8px">
          <CustomerSelect v-model="woCustomerCode" style="width: 220px" :placeholder="$t('flow.collab.phPickCustomer')" @change="onWoCustomer" />
          <el-button size="small" type="primary" @click="loadWriteoff">{{ $t('common.search') }}</el-button>
        </div>
        <el-table :data="writeoffRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="period" :label="$t('field.period')" width="100" />
          <el-table-column :label="$t('flow.report.colWriteOffType')" width="110">
            <template #default="{ row }">{{ writeoffTypeLabel(row.writeoffType) }}</template>
          </el-table-column>
          <el-table-column prop="count" :label="$t('flow.report.colWriteOffCount')" width="100" align="right" />
          <el-table-column prop="autoCount" :label="$t('flow.report.colAutoOfTotal')" width="100" align="right" />
          <el-table-column prop="writeoffAmount" :label="$t('flow.report.colWriteOffTotal')" width="140" align="right" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="$t('flow.report.secNotes')" name="note">
        <el-table :data="noteRows" v-loading="loading" size="small" border stripe>
          <el-table-column :label="$t('flow.report.colNoteType')" width="110">
            <template #default="{ row }">{{ row.noteType === 'PAPER' ? t('flow.report.notePaper') : t('flow.report.noteElectronic') }}</template>
          </el-table-column>
          <el-table-column :label="$t('common.status')" width="120">
            <template #default="{ row }">{{ arNoteStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="count" :label="$t('flow.report.colNoteCount')" width="110" align="right" />
          <el-table-column prop="amount" :label="$t('flow.report.colFaceAmountTotal')" width="140" align="right" />
          <el-table-column prop="discountInterest" :label="$t('flow.report.colDiscountInterest')" width="130" align="right" />
          <el-table-column prop="discountAmount" :label="$t('flow.report.colDiscountNet')" width="130" align="right" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="$t('flow.report.secBadDebt')" name="badDebt">
        <div class="ops" style="margin-bottom: 8px">
          <el-date-picker v-model="bdPeriod" type="month" value-format="YYYY-MM" :placeholder="$t('flow.close.colPeriodYm')" style="width: 160px" @change="loadBadDebt" />
          <el-button size="small" type="primary" @click="loadBadDebt">{{ $t('common.search') }}</el-button>
        </div>
        <el-table :data="badDebtRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="period" :label="$t('field.period')" width="100" />
          <el-table-column :label="$t('common.status')" width="110">
            <template #default="{ row }">{{ arBaddebtStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="count" :label="$t('flow.report.colCount')" width="90" align="right" />
          <el-table-column prop="provisionAmount" :label="$t('flow.report.colProvision')" width="130" align="right" />
          <el-table-column prop="writeoffAmount" :label="$t('flow.report.colBadDebtWriteOff')" width="130" align="right" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="$t('flow.report.secMonthClose')" name="monthClose">
        <div class="ops" style="margin-bottom: 8px">
          <el-date-picker v-model="mcPeriod" type="month" value-format="YYYY-MM" :placeholder="$t('flow.close.colPeriodYm')" style="width: 160px" @change="loadMonthClose" />
          <el-button size="small" type="primary" @click="loadMonthClose">{{ $t('common.search') }}</el-button>
        </div>
        <el-table :data="monthCloseRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="closeNo" :label="$t('flow.report.colCloseDocNo')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="period" :label="$t('flow.report.colPeriod')" width="90" />
          <el-table-column :label="$t('flow.report.colDomain')" width="70">
            <template #default="{ row }"><el-tag size="small" :type="row.domain === 'AP' ? 'warning' : 'primary'">{{ row.domain }}</el-tag></template>
          </el-table-column>
          <el-table-column :label="$t('field.type')" width="110">
            <template #default="{ row }">{{ mcTypeLabel(row.closeType) }}</template>
          </el-table-column>
          <el-table-column :label="$t('common.status')" width="130">
            <template #default="{ row }">{{ arMcStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="amount" :label="$t('flow.report.colCarriedAmount')" width="115" align="right" :formatter="(row, col, val) => formatMoney(val)" />
          <el-table-column prop="diffAmount" :label="$t('flow.report.colDiff')" width="100" align="right" :formatter="(row, col, val) => formatMoney(val)" />
          <el-table-column :label="$t('flow.report.colCloseTime')" width="150">
            <template #default="{ row }">{{ row.closedTime ? formatDateTime(row.closedTime) : '—' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { arBillingStatusLabel, writeoffTypeLabel, arNoteStatusLabel, arBaddebtStatusLabel, mcTypeLabel, arMcStatusLabel } from '@/constants/enums'
import {onMounted, ref, computed} from 'vue'
import { ElMessage } from 'element-plus'
import { arGapApi } from '../../api'
import CustomerSelect from '@/components/common/CustomerSelect.vue'
import { formatDateTime, formatMoney } from '../../utils/format'
import { exportCsv } from '../../utils/csv'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()


const tab = ref('invoicing')
const loading = ref(false)
const invoicingRows = ref([])
const writeoffRows = ref([])
const noteRows = ref([])
const badDebtRows = ref([])
const monthCloseRows = ref([])
const invCustomer = ref('')
const woCustomer = ref('')
const invCustomerCode = ref('')
const woCustomerCode = ref('')
const bdPeriod = ref('')
const mcPeriod = ref('')

function onInvCustomer(c) {
  invCustomer.value = c?.customerName || ''
}

function onWoCustomer(c) {
  woCustomer.value = c?.customerName || ''
}

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
    if (i === 0) return t('flow.report.totalRow')
    if (COUNT_COLS.includes(col.property)) {
      return data.reduce((s, r) => s + (Number(r[col.property]) || 0), 0)
    }
    if (AMOUNT_COLS.includes(col.property)) {
      return data.reduce((s, r) => s + (Number(r[col.property]) || 0), 0).toFixed(2)
    }
    return ''
  })
}

// 41号审查 按页签导出统计结果
const TAB_EXPORT = computed(() => ({
  invoicing: () => [invoicingRows.value, [
    { prop: 'period', label: t('field.period') }, { prop: 'status', label: t('common.status') }, { prop: 'count', label: t('flow.report.colInvoiceCount') },
    { prop: 'amount', label: t('flow.report.colAmountTotal') }, { prop: 'taxAmount', label: t('flow.report.colTaxTotal') }, { prop: 'totalAmount', label: t('field.totalWithTax') },
  ], 'ArReport_Invoicing'],
  writeoff: () => [writeoffRows.value, [
    { prop: 'period', label: t('field.period') }, { prop: 'writeoffType', label: t('flow.report.colWriteOffType') }, { prop: 'count', label: t('flow.report.colWriteOffCount') },
    { prop: 'autoCount', label: t('flow.report.colAutoOfTotal') }, { prop: 'writeoffAmount', label: t('flow.report.colWriteOffTotal') },
  ], 'ArReport_Writeoff'],
  note: () => [noteRows.value, [
    { prop: 'noteType', label: t('flow.report.colNoteType') }, { prop: 'status', label: t('common.status') }, { prop: 'count', label: t('flow.report.colNoteCount') },
    { prop: 'amount', label: t('flow.report.colFaceAmountTotal') }, { prop: 'discountInterest', label: t('flow.report.colDiscountInterest') }, { prop: 'discountAmount', label: t('flow.report.colDiscountNet') },
  ], 'ArReport_Note'],
  badDebt: () => [badDebtRows.value, [
    { prop: 'period', label: t('field.period') }, { prop: 'status', label: t('common.status') }, { prop: 'count', label: t('flow.report.colCount') },
    { prop: 'provisionAmount', label: t('flow.report.colProvision') }, { prop: 'writeoffAmount', label: t('flow.report.colBadDebtWriteOff') },
  ], 'ArReport_BadDebt'],
  monthClose: () => [monthCloseRows.value, [
    { prop: 'closeNo', label: t('flow.report.colCloseDocNo') }, { prop: 'period', label: t('flow.report.colPeriod') }, { prop: 'domain', label: t('flow.report.colDomain') },
    { prop: 'closeType', label: t('field.type') }, { prop: 'status', label: t('common.status') }, { prop: 'amount', label: t('flow.report.colCarriedAmount') },
    { prop: 'diffAmount', label: t('flow.report.colDiff') }, { prop: 'closedTime', label: t('flow.report.colCloseTime') },
  ], 'ArReport_MonthClose'],
}))
function doExport() {
  const [rows, cols, name] = TAB_EXPORT.value[tab.value]()
  exportCsv(rows, cols, name)
}

onMounted(loadInvoicing)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
</style>
