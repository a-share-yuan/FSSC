<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>{{ $t('pay.shared.secReconLetter') }}</b>
        <el-select v-model="supplierId" :placeholder="$t('pay.recon.phSupplier')" filterable clearable style="width: 220px">
          <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierCode + ' ' + s.supplierName" :value="s.id" />
        </el-select>
        <el-date-picker v-model="period" type="month" value-format="YYYYMM" :placeholder="$t('pay.recon.phPeriod')" style="width: 140px" />
        <el-button type="primary" size="small" @click="generate">{{ $t('pay.recon.btnGenerate') }}</el-button>
        <el-input v-model="q.keyword" :placeholder="$t('pay.recon.phSupplierName')" clearable style="width: 140px" @keyup.enter="load" />
        <el-select v-model="q.status" :placeholder="$t('common.status')" clearable style="width: 130px" @change="load">
          <el-option :label="$t('pay.recon.stDraft')" value="DRAFT" />
          <el-option :label="$t('pay.recon.stLetterSent')" value="SENT" />
          <el-option :label="$t('pay.shared.stConfirmed')" value="CONFIRMED" />
          <el-option :label="$t('pay.recon.stDiff')" value="DIFF" />
          <el-option :label="$t('pay.recon.stDiffResolved')" value="RESOLVED" />
        </el-select>
        <el-button size="small" @click="load">{{ $t('common.search') }}</el-button>
      <el-button size="small" @click="exportCsv">{{ $t('pay.shared.btnExportCsv') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('pay.shared.btnExportSelected', { v1: selRows.length }) }}</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 13px">
          {{ $t('pay.recon.hintFlow') }}</span>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" size="small" stripe v-else max-height="520" @selection-change="handleSelectionChange" :empty-text="$t('common.empty')" @row-dblclick="openDetail">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="reconNo" :label="$t('field.reconNo')" width="160"  sortable />
      <el-table-column prop="supplierName" :label="$t('field.supplier')" min-width="130" show-overflow-tooltip  sortable />
      <el-table-column prop="period" :label="$t('field.period')" width="75"  sortable />
      <el-table-column :label="$t('pay.recon.colOpeningBalance')" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.openingBalance) }}</template>
      </el-table-column>
      <el-table-column :label="$t('pay.recon.colPeriodPayable')" width="110" align="right">
        <template #default="{ row }">
          <span style="color: #e6a23c">{{ fmt(row.periodPayable) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('pay.recon.colPeriodPaid')" width="110" align="right">
        <template #default="{ row }">
          <span style="color: #67c23a">{{ fmt(row.periodPaid) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('pay.recon.colClosingBalance')" width="110" align="right">
        <template #default="{ row }">
          <b>{{ fmt(row.closingBalance) }}</b>
        </template>
      </el-table-column>
      <el-table-column :label="$t('pay.recon.colStatus')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('pay.recon.colLetter')" width="90">
        <template #default="{ row }">
          <el-tag :type="letterTagType(row.letterStatus)" size="small" effect="plain">{{ letterLabel(row.letterStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('pay.recon.colSupplierConfirm')" width="100">
        <template #default="{ row }">
          <el-tag :type="confirmTagType(row.supplierConfirmStatus)" size="small" effect="plain">{{ confirmLabel(row.supplierConfirmStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('pay.shared.btnTrace') }}</el-button>
          <el-button link type="primary" size="small" @click="openDetail(row)">{{ $t('pay.recon.btnDetailMatch') }}</el-button>
          <el-button v-if="row.letterStatus === 'NONE' || row.letterStatus === null" link type="warning" size="small" @click="sendLetter(row)">{{ $t('pay.recon.btnSendLetter') }}</el-button>
          <el-button v-if="row.supplierConfirmStatus === 'PENDING' && row.status !== 'CONFIRMED'" link type="success" size="small" @click="supplierConfirm(row, 'CONFIRMED')">{{ $t('pay.recon.btnConfirmAgreed') }}</el-button>
          <el-button v-if="row.supplierConfirmStatus === 'PENDING' && row.status !== 'DIFF'" link type="danger" size="small" @click="supplierConfirm(row, 'REJECTED')">{{ $t('pay.recon.btnDispute') }}</el-button>
          <el-button link size="small" @click="openDiffs(row)">{{ $t('pay.recon.btnDiffs') }}</el-button>
          <el-button v-if="linkuEnabled" link type="info" size="small" @click="syncLinkU(row)">{{ $t('pay.recon.btnLinkuStatus') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 10px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="q.pageSize" :current-page="q.pageNum" @current-change="(n) => { q.pageNum = n; load() }" />

    <!-- 明细勾对弹窗 -->
    <el-dialog v-model="detailVisible" :title="$t('pay.recon.ttlDetailDialog', { v1: currentRow?.supplierName || '', v2: currentRow?.period || '' })" width="95%" top="3vh" destroy-on-close>
      <div v-if="summary" style="margin-bottom: 12px; display: flex; gap: 16px; flex-wrap: wrap">
        <el-tag>{{ $t('pay.recon.tagInvoiceCount', { v1: summary.invoiceCount }) }}</el-tag>
        <el-tag type="success">{{ $t('pay.recon.tagPaymentCount', { v1: summary.paymentCount }) }}</el-tag>
        <el-tag type="primary">{{ $t('pay.recon.tagMatchedAmount', { v1: summary.matchedCount, v2: fmt(summary.matchedAmount) }) }}</el-tag>
        <el-tag type="warning">{{ $t('pay.recon.tagUnmatchedCount', { v1: summary.unmatchedCount }) }}</el-tag>
        <el-tag v-if="summary.diffCount > 0" type="danger">{{ $t('pay.recon.tagDiffCount', { v1: summary.diffCount }) }}</el-tag>
        <el-tag effect="plain">{{ $t('pay.recon.tagOpening', { v1: fmt(currentRow?.openingBalance) }) }}</el-tag>
        <el-tag effect="plain">{{ $t('pay.recon.tagClosing', { v1: fmt(currentRow?.closingBalance) }) }}</el-tag>
      </div>
      <div style="margin-bottom: 10px; display: flex; gap: 8px; align-items: center">
        <el-button type="primary" size="small" @click="autoMatch" :loading="matchLoading">{{ $t('pay.recon.btnAutoMatch') }}</el-button>
        <span v-if="selectedInvoice && selectedPayment" style="color: #409eff; font-size: 13px">
          {{ $t('pay.recon.textSelectedPair', { v1: selectedInvoice.itemNo, v2: selectedPayment.itemNo }) }}<el-button type="success" size="small" style="margin-left: 8px" @click="manualMatch">{{ $t('pay.recon.btnConfirmTie') }}</el-button>
          <el-button size="small" @click="clearSelection">{{ $t('pay.recon.btnClearSelection') }}</el-button>
        </span>
        <span v-else style="color: #909399; font-size: 13px">{{ $t('pay.recon.hintManualTie') }}</span>
      </div>

      <div style="display: flex; gap: 12px">
        <!-- 发票列表 -->
        <div style="flex: 1; min-width: 0">
          <div style="font-weight: bold; margin-bottom: 6px; color: #e6a23c">{{ $t('pay.recon.secInvoice') }}</div>
          <el-table :data="invoiceDetails" size="small" stripe max-height="450" highlight-current-row
                    @row-click="(row) => selectInvoice(row)" :row-class-name="detailRowClass" @selection-change="handleSelectionChange" :empty-text="$t('common.empty')" style="cursor:pointer">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="itemNo" :label="$t('pay.shared.colInvoiceNo')" width="130" show-overflow-tooltip  sortable />
            <el-table-column prop="itemDate" :label="$t('pay.recon.colDate')" width="100"  sortable :formatter="(row, col, val) => formatDate(val)" />
            <el-table-column :label="$t('field.amount')" width="110" align="right">
              <template #default="{ row }">{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$t('common.status')" width="80">
              <template #default="{ row }">
                <el-tag :type="row.matchStatus === 'MATCHED' ? 'success' : 'info'" size="small">
                  {{ row.matchStatus === 'MATCHED' ? t('pay.recon.stTieMatched') : t('pay.recon.stTieUnmatched') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('pay.recon.colRule')" width="80">
              <template #default="{ row }">{{ matchRuleLabel(row.matchRule) }}</template>
            </el-table-column>
            <el-table-column :label="$t('common.operation')" width="70">
              <template #default="{ row }">
                <el-button v-if="row.matchStatus === 'MATCHED'" link type="danger" size="small" @click.stop="unmatch(row)">{{ $t('common.cancel') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <!-- 付款列表 -->
        <div style="flex: 1; min-width: 0">
          <div style="font-weight: bold; margin-bottom: 6px; color: #67c23a">{{ $t('pay.recon.secPayment') }}</div>
          <el-table :data="paymentDetails" size="small" stripe max-height="450" highlight-current-row
                    @row-click="(row) => selectPayment(row)" :row-class-name="detailRowClass" @selection-change="handleSelectionChange" :empty-text="$t('common.empty')" style="cursor:pointer">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="itemNo" :label="$t('pay.recon.colItemNo')" width="130" show-overflow-tooltip  sortable />
            <el-table-column prop="itemDate" :label="$t('pay.recon.colDate')" width="100"  sortable :formatter="(row, col, val) => formatDate(val)" />
            <el-table-column :label="$t('field.amount')" width="110" align="right">
              <template #default="{ row }">{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column :label="$t('common.status')" width="80">
              <template #default="{ row }">
                <el-tag :type="row.matchStatus === 'MATCHED' ? 'success' : 'info'" size="small">
                  {{ row.matchStatus === 'MATCHED' ? t('pay.recon.stTieMatched') : t('pay.recon.stTieUnmatched') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('pay.recon.colRule')" width="80">
              <template #default="{ row }">{{ matchRuleLabel(row.matchRule) }}</template>
            </el-table-column>
            <el-table-column :label="$t('common.operation')" width="70">
              <template #default="{ row }">
                <el-button v-if="row.matchStatus === 'MATCHED'" link type="danger" size="small" @click.stop="unmatch(row)">{{ $t('common.cancel') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>

    <!-- 差异调节弹窗 -->
    <el-dialog v-model="diffVisible" :title="$t('pay.recon.ttlDiffDialog', { v1: currentRow?.supplierName || '' })" width="80%" destroy-on-close>
      <div style="margin-bottom: 10px; display: flex; gap: 8px; align-items: center">
        <el-select v-model="newDiff.diffType" :placeholder="$t('pay.shared.colDiffType')" style="width: 160px">
          <el-option :label="$t('pay.recon.typeAmountDiff')" value="AMOUNT_DIFF" />
          <el-option :label="$t('pay.recon.typeMissingInvoice')" value="MISSING_INVOICE" />
          <el-option :label="$t('pay.recon.typeMissingPayment')" value="MISSING_PAYMENT" />
          <el-option :label="$t('pay.recon.typeTimingDiff')" value="TIMING_DIFF" />
          <el-option :label="$t('pay.recon.typeOther')" value="OTHER" />
        </el-select>
        <el-input-number v-model="newDiff.ourAmount" :precision="2" :min="0" :placeholder="$t('pay.recon.phOurAmount')" style="width: 140px" />
        <el-input-number v-model="newDiff.supplierAmount" :precision="2" :min="0" :placeholder="$t('pay.recon.phSupplierAmount')" style="width: 140px" />
        <el-select v-model="newDiff.reason" filterable allow-create default-first-option :placeholder="$t('pay.recon.phDiffReason')" style="width: 200px"><el-option :label="$t('pay.recon.reasonTimeDiff')" value="时间差异" /><el-option :label="$t('pay.recon.typeAmountDiff')" value="金额差异" /><el-option :label="$t('pay.recon.reasonBookingDiff')" value="账务处理差异" /><el-option :label="$t('pay.recon.reasonOutstanding')" value="未达账项" /><el-option :label="$t('pay.recon.reasonBankFee')" value="银行手续费" /><el-option :label="$t('pay.recon.reasonFxDiff')" value="汇率差异" /><el-option :label="$t('pay.recon.typeOther')" value="其他" /></el-select>
        <el-button type="primary" size="small" @click="addDiff">{{ $t('pay.recon.btnAddDiff') }}</el-button>
      </div>
      <el-table :data="diffs" size="small" stripe max-height="400" @selection-change="handleSelectionChange" :empty-text="$t('common.empty')">
        <el-table-column type="selection" width="45" />
        <el-table-column :label="$t('field.type')" width="100">
          <template #default="{ row }">{{ diffTypeLabel(row.diffType) }}</template>
        </el-table-column>
        <el-table-column :label="$t('pay.recon.phOurAmount')" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.ourAmount) }}</template>
        </el-table-column>
        <el-table-column :label="$t('pay.recon.phSupplierAmount')" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.supplierAmount) }}</template>
        </el-table-column>
        <el-table-column :label="$t('pay.recon.colDiffAmount')" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.diffAmount < 0 ? '#f56c6c' : '#e6a23c' }">{{ fmt(row.diffAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" :label="$t('pay.recon.colReason')" min-width="150" show-overflow-tooltip  sortable />
        <el-table-column :label="$t('pay.recon.colAdjustType')" width="120">
          <template #default="{ row }">{{ adjustTypeLabel(row.adjustmentType) }}</template>
        </el-table-column>
        <el-table-column prop="adjustmentVoucherNo" :label="$t('pay.shared.colVoucherNo')" width="120"  sortable />
        <el-table-column :label="$t('common.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RESOLVED' ? 'success' : 'warning'" size="small">{{ row.status === 'RESOLVED' ? t('pay.recon.stResolved') : t('status.pending') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="openResolveDiff(row)">{{ $t('pay.recon.btnResolve') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 差异处理子弹窗 -->
      <el-dialog v-model="resolveVisible" :title="$t('pay.recon.ctxResolveTitle')" width="500px" append-to-body destroy-on-close>
        <el-form :model="resolveForm" :rules="resolveRules" label-width="90px">
          <el-form-item :label="$t('pay.recon.colDiffAmount')">{{ fmt(resolvingDiff?.diffAmount) }}</el-form-item>
          <el-form-item :label="$t('pay.recon.colAdjustType')">
            <el-select v-model="resolveForm.adjustmentType" style="width: 100%">
              <el-option :label="$t('pay.recon.adjNone')" value="NONE" />
              <el-option :label="$t('pay.recon.adjInvoice')" value="ADJUST_INVOICE" />
              <el-option :label="$t('pay.recon.adjPayment')" value="ADJUST_PAYMENT" />
              <el-option :label="$t('pay.recon.adjWriteOff')" value="WRITE_OFF" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('pay.shared.colVoucherNo')">
            <el-input v-model="resolveForm.voucherNo" :placeholder="$t('pay.recon.phAdjustVoucherNo')" maxlength="48" />
          </el-form-item>
          <el-form-item :label="$t('common.remark')">
            <el-input v-model="resolveForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="resolveVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" @click="resolveDiff">{{ $t('pay.recon.btnConfirmResolve') }}</el-button>
        </template>
      </el-dialog>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
</template>

<script setup>

import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { opApi, supplierApi, extInterfaceApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_TYPE } from '@/constants/billStatus.js'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+r': () => loadDetails(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceBizType.value = 'RECON_ACTIVE'
  traceVisible.value = true
}

const selRows = ref([])
const suppliers = ref([])
const supplierId = ref(null)
const period = ref('')
const linkuEnabled = ref(false)
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const q = reactive({ pageNum: 1, pageSize: 20, keyword: '', status: '' })

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }

function statusLabel(s) {
  // DRAFT 原直译「已生成」，与库面态是两套语言——采购退货审核通过的同一秒会自动生成
  // 一张 recon_type=AUTO 的负数对账单，
  // 界面却显示「已生成」，读起来像已走完对账。词面统一按"这一态还没发函、还没被供应商确认"＝待确认。
  return { DRAFT: t('pay.recon.stDraft'), SENT: t('pay.recon.stLetterSent'), CONFIRMED: t('pay.shared.stConfirmed'), DIFF: t('pay.recon.stDiff'), RESOLVED: t('pay.recon.stDiffResolved') }[s] || s || ''
}
function statusTagType(s) {
  return { DRAFT: 'info', SENT: 'warning', CONFIRMED: 'success', DIFF: 'danger', RESOLVED: 'success' }[s] || 'info'
}
function letterLabel(s) {
  return { NONE: t('pay.recon.lblLetterNone'), SENT: t('pay.recon.lblLetterSent'), RECEIVED: t('pay.recon.lblLetterReplied') }[s] || t('pay.recon.lblLetterNone')
}
function letterTagType(s) {
  return { NONE: 'info', SENT: 'warning', RECEIVED: 'success' }[s] || 'info'
}
function confirmLabel(s) {
  // ②：未发函（NONE/空）不显示「待确认」——那是发函后才存在的状态，
  // 一律显 '-'，杜绝「未发函却显示待确认/驳回」的状态机矛盾呈现。
  return { NONE: '-', PENDING: t('pay.recon.lblConfirmPending'), CONFIRMED: t('pay.recon.btnConfirmAgreed'), REJECTED: t('pay.recon.btnDispute') }[s] || '-'
}
function confirmTagType(s) {
  return { PENDING: 'info', CONFIRMED: 'success', REJECTED: 'danger' }[s] || 'info'
}
function matchRuleLabel(r) {
  return { INVOICE_NO: t('pay.shared.colInvoiceNo'), AMOUNT: t('field.amount'), PO_NO: t('pay.recon.rulePoNo'), MANUAL: t('pay.recon.ruleManual') }[r] || ''
}
function diffTypeLabel(code) {
  return { AMOUNT_DIFF: t('pay.recon.typeAmountDiff'), MISSING_INVOICE: t('pay.recon.typeMissingInvoice'), MISSING_PAYMENT: t('pay.recon.typeMissingPayment'), TIMING_DIFF: t('pay.recon.typeTimingDiff'), OTHER: t('pay.recon.typeOther') }[code] || code
}
function adjustTypeLabel(code) {
  return { NONE: t('pay.recon.adjNone'), ADJUST_INVOICE: t('pay.recon.adjInvoice'), ADJUST_PAYMENT: t('pay.recon.adjPayment'), WRITE_OFF: t('pay.recon.adjWriteOff') }[code] || code
}

async function load() {
  loading.value = true
  try {
    const r = await opApi.reconPage({ ...q })
    rows.value = r.list || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function generate() {
  if (!supplierId.value || !period.value) { ElMessage.warning(t('pay.recon.errPickSupplierPeriod')); return }
  try {
    const id = await opApi.reconGenerate({ supplierId: supplierId.value, period: period.value })
    ElMessage.success(t('pay.recon.msgGenerated'))
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 明细勾对
const detailVisible = ref(false)
const currentRow = ref(null)
const summary = ref(null)
const allDetails = ref([])
const invoiceDetails = computed(() => allDetails.value.filter(d => d.itemType === 'INVOICE'))
const paymentDetails = computed(() => allDetails.value.filter(d => d.itemType === 'PAYMENT'))
const selectedInvoice = ref(null)
const selectedPayment = ref(null)
const matchLoading = ref(false)

async function openDetail(row) {
  currentRow.value = row
  detailVisible.value = true
  clearSelection()
  await loadDetails()
}

async function loadDetails() {
  if (!currentRow.value) return
  try {
    const [s, d] = await Promise.all([
      opApi.reconSummary(currentRow.value.id),
      opApi.reconDetails(currentRow.value.id)
    ])
    summary.value = s
    allDetails.value = d || []
  } catch (e) { ElMessage.error(e.message) }
}

function selectInvoice(row) {
  if (row.matchStatus === 'MATCHED') return
  selectedInvoice.value = row
}
function selectPayment(row) {
  if (row.matchStatus === 'MATCHED') return
  selectedPayment.value = row
}
function clearSelection() {
  selectedInvoice.value = null
  selectedPayment.value = null
}
function detailRowClass({ row }) {
  if (row.matchStatus === 'MATCHED') return 'recon-matched'
  if (selectedInvoice.value?.id === row.id || selectedPayment.value?.id === row.id) return 'recon-selected'
  return ''
}

async function autoMatch() {
  matchLoading.value = true
  try {
    const r = await opApi.reconAutoMatch(currentRow.value.id)
    ElMessage.success(t('pay.recon.msgAutoTied', { v1: r.matchedCount }))
    await loadDetails()
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { matchLoading.value = false }
}

async function manualMatch() {
  if (!selectedInvoice.value || !selectedPayment.value) { ElMessage.warning(t('pay.recon.errPickInvoicePayment')); return }
  try {
    await opApi.reconManualMatch(currentRow.value.id, selectedInvoice.value.id, selectedPayment.value.id)
    ElMessage.success(t('pay.recon.msgManualTieOk'))
    clearSelection()
    await loadDetails()
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

async function unmatch(row) {
  try {
    await ElMessageBox.confirm(t('pay.recon.ctxUnmatchConfirm'), t('pay.recon.ctxUnmatchTitle'), { type: 'warning' })
    await opApi.reconUnmatch(row.id)
    ElMessage.success(t('pay.recon.msgUnmatched'))
    await loadDetails()
    await load()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// 对账函
async function sendLetter(row) {
  try {
    await ElMessageBox.confirm(t('pay.recon.ctxSendConfirm', { v1: row.supplierName }), t('pay.recon.btnSendLetter'), { type: 'warning' })
    const r = await opApi.reconSendLetter(row.id)
    ElMessage.success(t('pay.recon.msgLetterSent', { v1: r.letterNo }))
    load()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// 供应商确认
async function supplierConfirm(row, status) {
  const action = status === 'CONFIRMED' ? t('pay.recon.btnConfirmAgreed') : t('pay.recon.btnDispute')
  try {
    let remark = ''
    if (status === BillStatus.REJECTED) {
      const r = await ElMessageBox.prompt(t('pay.recon.ctxDisputePrompt'), t('pay.recon.ctxDisputeTitle'), { inputPlaceholder: t('pay.recon.phInputRequired') })
      remark = r.value
    } else {
      await ElMessageBox.confirm(t('pay.recon.ctxConfirmSupplier', { v1: row.supplierName }), t('pay.recon.colSupplierConfirm'), { type: 'warning' })
    }
    await opApi.reconSupplierConfirm(row.id, status, remark)
    ElMessage.success(t('pay.recon.msgActionLogged', { v1: action }))
    load()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// 差异调节
const diffVisible = ref(false)
const diffs = ref([])
const newDiff = reactive({ diffType: 'AMOUNT_DIFF', ourAmount: '', supplierAmount: '', reason: '' })
const resolveVisible = ref(false)
const resolvingDiff = ref(null)
const resolveForm = reactive({ adjustmentType: 'NONE', voucherNo: '', remark: '' })

const resolveRules = computed(() => ({
  adjustmentType: [{ required: true, message: t('pay.recon.errPickAdjustType') }],
}))

async function openDiffs(row) {
  currentRow.value = row
  diffVisible.value = true
  await loadDiffs()
}

async function loadDiffs() {
  try {
    diffs.value = await opApi.reconDiffs(currentRow.value.id) || []
  } catch (e) { ElMessage.error(e.message) }
}

async function addDiff() {
  if (!newDiff.diffType || !newDiff.reason) { ElMessage.warning(t('pay.recon.errFillDiffTypeReason')); return }
  try {
    await opApi.reconAddDiff(currentRow.value.id, {
      diffType: newDiff.diffType,
      ourAmount: parseFloat(newDiff.ourAmount) || 0,
      supplierAmount: parseFloat(newDiff.supplierAmount) || 0,
      reason: newDiff.reason
    })
    ElMessage.success(t('pay.recon.msgDiffAdded'))
    newDiff.reason = ''; newDiff.ourAmount = ''; newDiff.supplierAmount = ''
    await loadDiffs()
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

function openResolveDiff(row) {
  resolvingDiff.value = row
  resolveForm.adjustmentType = 'NONE'
  resolveForm.voucherNo = ''
  resolveForm.remark = ''
  resolveVisible.value = true
}

async function resolveDiff() {
  try {
    await opApi.reconResolveDiff(resolvingDiff.value.id, { ...resolveForm })
    ElMessage.success(t('pay.recon.stDiffResolved'))
    resolveVisible.value = false
    await loadDiffs()
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

// LinkU 状态查看（只读）
async function syncLinkU(row) {
  try {
    const r = await opApi.reconLinkUStatus(row.id)
    const statusMap = { NONE: t('pay.recon.stNotSynced'), SYNCED: t('pay.recon.stSynced'), CONFIRMED: t('pay.shared.stConfirmed'), REJECTED: t('pay.recon.btnDispute') }
    showRowDetail({
      linkuReconId: r.linkuReconId || t('pay.recon.lblNone'),
      linkuSyncStatus: statusMap[r.linkuSyncStatus] || r.linkuSyncStatus || t('pay.recon.lblUnknown'),
      linkuSyncTime: r.linkuSyncTime,
      note: t('pay.recon.noteLinku'),
    }, { title: t('pay.recon.ctxLinkuTitle'), labels: { linkuReconId: t('pay.recon.ctxStatementId'), linkuSyncStatus: t('pay.recon.ctxSyncStatus'), linkuSyncTime: t('pay.recon.ctxSyncTime'), note: t('field.desc') } })
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(async () => {
  const now = new Date()
  period.value = now.getFullYear() + String(now.getMonth() + 1).padStart(2, '0')
  try {
    const r = await supplierApi.page({ pageNum: 1, pageSize: 100 })
    suppliers.value = r.list || []
    await load()
  } catch (e) { /* 忽略 */ }
  // 检查 LinkU 是否已配置且启用，未启用则隐藏所有 LinkU 相关功能
  try {
    const cfg = await extInterfaceApi.getByCategory('LINKU')
    linkuEnabled.value = !!cfg
  } catch (e) { linkuEnabled.value = false }
})

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning(t('pay.shared.msgNoExportData')); return }
  const cols = [{ prop: 'reconNo', label: t('field.reconNo') }, { prop: 'supplierName', label: t('field.supplier') }, { prop: 'period', label: t('field.period') }]
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
  a.download = `SupplierRecon_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('pay.shared.msgExportedCsv'))
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
  a.download = `SupplierRecon_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('pay.shared.msgExportedRows', { v1: selRows.value.length }))
}

</script>

<style scoped>
:deep(.recon-matched) {
  background-color: var(--el-color-success-light-9) !important;
}
:deep(.recon-selected) {
  background-color: var(--el-color-primary-light-8) !important;
}
</style>
