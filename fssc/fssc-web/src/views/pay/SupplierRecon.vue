<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>应付对账（函）</b>
        <el-select v-model="supplierId" placeholder="选择供应商" filterable clearable style="width: 220px">
          <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierCode + ' ' + s.supplierName" :value="s.id" />
        </el-select>
        <el-date-picker v-model="period" type="month" value-format="YYYYMM" placeholder="对账期间" style="width: 140px" />
        <el-button type="primary" size="small" @click="generate">生成对账单</el-button>
        <el-input v-model="q.keyword" placeholder="供应商名" clearable style="width: 140px" @keyup.enter="load" />
        <el-select v-model="q.status" placeholder="状态" clearable style="width: 130px" @change="load">
          <el-option label="已生成" value="DRAFT" />
          <el-option label="已发函" value="SENT" />
          <el-option label="已确认" value="CONFIRMED" />
          <el-option label="有差异" value="DIFF" />
          <el-option label="差异已处理" value="RESOLVED" />
        </el-select>
        <el-button size="small" @click="load">查询</el-button>
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 13px">
          生成对账单 → 自动勾对 → 发送对账函 → 供应商确认 → 差异处理 → 放行三单匹配
        </span>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" size="small" stripe v-else max-height="520" @selection-change="handleSelectionChange" empty-text="暂无数据" @row-dblclick="openDetail">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="reconNo" label="对账单号" width="160"  sortable />
      <el-table-column prop="supplierName" label="供应商" min-width="130" show-overflow-tooltip  sortable />
      <el-table-column prop="period" label="期间" width="75"  sortable />
      <el-table-column label="期初余额" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.openingBalance) }}</template>
      </el-table-column>
      <el-table-column label="本期应付" width="110" align="right">
        <template #default="{ row }">
          <span style="color: #e6a23c">{{ fmt(row.periodPayable) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="本期已付" width="110" align="right">
        <template #default="{ row }">
          <span style="color: #67c23a">{{ fmt(row.periodPaid) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="期末余额" width="110" align="right">
        <template #default="{ row }">
          <b>{{ fmt(row.closingBalance) }}</b>
        </template>
      </el-table-column>
      <el-table-column label="对账状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="对账函" width="90">
        <template #default="{ row }">
          <el-tag :type="letterTagType(row.letterStatus)" size="small" effect="plain">{{ letterLabel(row.letterStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="供应商确认" width="100">
        <template #default="{ row }">
          <el-tag :type="confirmTagType(row.supplierConfirmStatus)" size="small" effect="plain">{{ confirmLabel(row.supplierConfirmStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <el-button link type="primary" size="small" @click="openDetail(row)">明细勾对</el-button>
          <el-button v-if="row.letterStatus === 'NONE' || row.letterStatus === null" link type="warning" size="small" @click="sendLetter(row)">发送对账函</el-button>
          <el-button v-if="row.supplierConfirmStatus === 'PENDING' && row.status !== 'CONFIRMED'" link type="success" size="small" @click="supplierConfirm(row, 'CONFIRMED')">确认一致</el-button>
          <el-button v-if="row.supplierConfirmStatus === 'PENDING' && row.status !== 'DIFF'" link type="danger" size="small" @click="supplierConfirm(row, 'REJECTED')">有异议</el-button>
          <el-button link size="small" @click="openDiffs(row)">差异</el-button>
          <el-button v-if="linkuEnabled" link type="info" size="small" @click="syncLinkU(row)">查看LinkU状态</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 10px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="q.pageSize" :current-page="q.pageNum" @current-change="(n) => { q.pageNum = n; load() }" />

    <!-- 明细勾对弹窗 -->
    <el-dialog v-model="detailVisible" :title="`对账明细 - ${currentRow?.supplierName || ''} (${currentRow?.period || ''})`" width="95%" top="3vh" destroy-on-close>
      <div v-if="summary" style="margin-bottom: 12px; display: flex; gap: 16px; flex-wrap: wrap">
        <el-tag>发票 {{ summary.invoiceCount }} 笔</el-tag>
        <el-tag type="success">付款 {{ summary.paymentCount }} 笔</el-tag>
        <el-tag type="primary">已勾对 {{ summary.matchedCount }} 笔 / {{ fmt(summary.matchedAmount) }} 元</el-tag>
        <el-tag type="warning">未勾对 {{ summary.unmatchedCount }} 笔</el-tag>
        <el-tag v-if="summary.diffCount > 0" type="danger">差异 {{ summary.diffCount }} 条</el-tag>
        <el-tag effect="plain">期初: {{ fmt(currentRow?.openingBalance) }}</el-tag>
        <el-tag effect="plain">期末: {{ fmt(currentRow?.closingBalance) }}</el-tag>
      </div>
      <div style="margin-bottom: 10px; display: flex; gap: 8px; align-items: center">
        <el-button type="primary" size="small" @click="autoMatch" :loading="matchLoading">自动勾对</el-button>
        <span v-if="selectedInvoice && selectedPayment" style="color: #409eff; font-size: 13px">
          已选: {{ selectedInvoice.itemNo }} ↔ {{ selectedPayment.itemNo }}
          <el-button type="success" size="small" style="margin-left: 8px" @click="manualMatch">确认勾对</el-button>
          <el-button size="small" @click="clearSelection">取消选择</el-button>
        </span>
        <span v-else style="color: #909399; font-size: 13px">提示: 从左侧选一条发票, 右侧选一条付款, 然后点确认勾对</span>
      </div>

      <div style="display: flex; gap: 12px">
        <!-- 发票列表 -->
        <div style="flex: 1; min-width: 0">
          <div style="font-weight: bold; margin-bottom: 6px; color: #e6a23c">发票（应付）</div>
          <el-table :data="invoiceDetails" size="small" stripe max-height="450" highlight-current-row
                    @row-click="(row) => selectInvoice(row)" :row-class-name="detailRowClass" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="itemNo" label="发票号" width="130" show-overflow-tooltip  sortable />
            <el-table-column prop="itemDate" label="日期" width="100"  sortable :formatter="(row, col, val) => formatDate(val)" />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.matchStatus === 'MATCHED' ? 'success' : 'info'" size="small">
                  {{ row.matchStatus === 'MATCHED' ? '已勾对' : '未勾对' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="规则" width="80">
              <template #default="{ row }">{{ matchRuleLabel(row.matchRule) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ row }">
                <el-button v-if="row.matchStatus === 'MATCHED'" link type="danger" size="small" @click.stop="unmatch(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <!-- 付款列表 -->
        <div style="flex: 1; min-width: 0">
          <div style="font-weight: bold; margin-bottom: 6px; color: #67c23a">付款（已付）</div>
          <el-table :data="paymentDetails" size="small" stripe max-height="450" highlight-current-row
                    @row-click="(row) => selectPayment(row)" :row-class-name="detailRowClass" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
            <el-table-column prop="itemNo" label="流水号" width="130" show-overflow-tooltip  sortable />
            <el-table-column prop="itemDate" label="日期" width="100"  sortable :formatter="(row, col, val) => formatDate(val)" />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.matchStatus === 'MATCHED' ? 'success' : 'info'" size="small">
                  {{ row.matchStatus === 'MATCHED' ? '已勾对' : '未勾对' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="规则" width="80">
              <template #default="{ row }">{{ matchRuleLabel(row.matchRule) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ row }">
                <el-button v-if="row.matchStatus === 'MATCHED'" link type="danger" size="small" @click.stop="unmatch(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>

    <!-- 差异调节弹窗 -->
    <el-dialog v-model="diffVisible" :title="`差异调节 - ${currentRow?.supplierName || ''}`" width="80%" destroy-on-close>
      <div style="margin-bottom: 10px; display: flex; gap: 8px; align-items: center">
        <el-select v-model="newDiff.diffType" placeholder="差异类型" style="width: 160px">
          <el-option label="金额差异" value="AMOUNT_DIFF" />
          <el-option label="缺少发票" value="MISSING_INVOICE" />
          <el-option label="缺少付款" value="MISSING_PAYMENT" />
          <el-option label="时间性差异" value="TIMING_DIFF" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-input-number v-model="newDiff.ourAmount" :precision="2" :min="0" placeholder="我方金额" style="width: 140px" />
        <el-input-number v-model="newDiff.supplierAmount" :precision="2" :min="0" placeholder="供应商金额" style="width: 140px" />
        <el-select v-model="newDiff.reason" filterable allow-create default-first-option placeholder="选择或输入差异原因" style="width: 200px"><el-option label="时间差异" value="时间差异" /><el-option label="金额差异" value="金额差异" /><el-option label="账务处理差异" value="账务处理差异" /><el-option label="未达账项" value="未达账项" /><el-option label="银行手续费" value="银行手续费" /><el-option label="汇率差异" value="汇率差异" /><el-option label="其他" value="其他" /></el-select>
        <el-button type="primary" size="small" @click="addDiff">添加差异</el-button>
      </div>
      <el-table :data="diffs" size="small" stripe max-height="400" @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ diffTypeLabel(row.diffType) }}</template>
        </el-table-column>
        <el-table-column label="我方金额" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.ourAmount) }}</template>
        </el-table-column>
        <el-table-column label="供应商金额" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.supplierAmount) }}</template>
        </el-table-column>
        <el-table-column label="差异金额" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.diffAmount < 0 ? '#f56c6c' : '#e6a23c' }">{{ fmt(row.diffAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip  sortable />
        <el-table-column label="调整方式" width="120">
          <template #default="{ row }">{{ adjustTypeLabel(row.adjustmentType) }}</template>
        </el-table-column>
        <el-table-column prop="adjustmentVoucherNo" label="凭证号" width="120"  sortable />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RESOLVED' ? 'success' : 'warning'" size="small">{{ row.status === 'RESOLVED' ? '已处理' : '待处理' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="openResolveDiff(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 差异处理子弹窗 -->
      <el-dialog v-model="resolveVisible" title="处理差异" width="500px" append-to-body destroy-on-close>
        <el-form :model="resolveForm" :rules="resolveRules" label-width="90px">
          <el-form-item label="差异金额">{{ fmt(resolvingDiff?.diffAmount) }}</el-form-item>
          <el-form-item label="调整方式">
            <el-select v-model="resolveForm.adjustmentType" style="width: 100%">
              <el-option label="无需调整" value="NONE" />
              <el-option label="调整发票" value="ADJUST_INVOICE" />
              <el-option label="调整付款" value="ADJUST_PAYMENT" />
              <el-option label="核销" value="WRITE_OFF" />
            </el-select>
          </el-form-item>
          <el-form-item label="凭证号">
            <el-input v-model="resolveForm.voucherNo" placeholder="调整凭证号（如有）" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="resolveForm.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="resolveVisible = false">取消</el-button>
          <el-button type="primary" @click="resolveDiff">确认处理</el-button>
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
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => loadDetails(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})

// R4-02: BillTraceDrawer
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
  return { DRAFT: '已生成', SENT: '已发函', CONFIRMED: '已确认', DIFF: '有差异', RESOLVED: '差异已处理' }[s] || s || ''
}
function statusTagType(s) {
  return { DRAFT: 'info', SENT: 'warning', CONFIRMED: 'success', DIFF: 'danger', RESOLVED: 'success' }[s] || 'info'
}
function letterLabel(s) {
  return { NONE: '未发函', SENT: '已发送', RECEIVED: '已回函' }[s] || '未发函'
}
function letterTagType(s) {
  return { NONE: 'info', SENT: 'warning', RECEIVED: 'success' }[s] || 'info'
}
function confirmLabel(s) {
  // R5-D-074②：未发函（NONE/空）不显示「待确认」——那是发函后才存在的状态，
  // 一律显 '-'，杜绝「未发函却显示待确认/驳回」的状态机矛盾呈现。
  return { NONE: '-', PENDING: '待确认', CONFIRMED: '确认一致', REJECTED: '有异议' }[s] || '-'
}
function confirmTagType(s) {
  return { PENDING: 'info', CONFIRMED: 'success', REJECTED: 'danger' }[s] || 'info'
}
function matchRuleLabel(r) {
  return { INVOICE_NO: '发票号', AMOUNT: '金额', PO_NO: 'PO号', MANUAL: '手工' }[r] || ''
}
function diffTypeLabel(t) {
  return { AMOUNT_DIFF: '金额差异', MISSING_INVOICE: '缺少发票', MISSING_PAYMENT: '缺少付款', TIMING_DIFF: '时间性差异', OTHER: '其他' }[t] || t
}
function adjustTypeLabel(t) {
  return { NONE: '无需调整', ADJUST_INVOICE: '调整发票', ADJUST_PAYMENT: '调整付款', WRITE_OFF: '核销' }[t] || t
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
  if (!supplierId.value || !period.value) { ElMessage.warning('请选择供应商和对账期间'); return }
  try {
    const id = await opApi.reconGenerate({ supplierId: supplierId.value, period: period.value })
    ElMessage.success('对账单已生成，明细已按期间过滤')
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// ---- 明细勾对 ----
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
    ElMessage.success(`自动勾对完成，匹配 ${r.matchedCount} 笔`)
    await loadDetails()
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { matchLoading.value = false }
}

async function manualMatch() {
  if (!selectedInvoice.value || !selectedPayment.value) { ElMessage.warning('请选择发票和付款'); return }
  try {
    await opApi.reconManualMatch(currentRow.value.id, selectedInvoice.value.id, selectedPayment.value.id)
    ElMessage.success('手工勾对成功')
    clearSelection()
    await loadDetails()
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

async function unmatch(row) {
  try {
    await ElMessageBox.confirm('确认取消该笔勾对？', '取消勾对', { type: 'warning' })
    await opApi.reconUnmatch(row.id)
    ElMessage.success('已取消勾对')
    await loadDetails()
    await load()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// ---- 对账函 ----
async function sendLetter(row) {
  try {
    await ElMessageBox.confirm(`确认向「${row.supplierName}」发送对账函？`, '发送对账函', { type: 'warning' })
    const r = await opApi.reconSendLetter(row.id)
    ElMessage.success(`对账函已发送，编号: ${r.letterNo}`)
    load()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// ---- 供应商确认 ----
async function supplierConfirm(row, status) {
  const action = status === 'CONFIRMED' ? '确认一致' : '有异议'
  try {
    let remark = ''
    if (status === BillStatus.REJECTED) {
      const r = await ElMessageBox.prompt(`请输入差异说明：`, '供应商有异议', { inputPlaceholder: '必填' })
      remark = r.value
    } else {
      await ElMessageBox.confirm(`确认与「${row.supplierName}」对账一致？确认后将放行三单匹配。`, '供应商确认', { type: 'warning' })
    }
    await opApi.reconSupplierConfirm(row.id, status, remark)
    ElMessage.success(action + '已记录')
    load()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// ---- 差异调节 ----
const diffVisible = ref(false)
const diffs = ref([])
const newDiff = reactive({ diffType: 'AMOUNT_DIFF', ourAmount: '', supplierAmount: '', reason: '' })
const resolveVisible = ref(false)
const resolvingDiff = ref(null)
const resolveForm = reactive({ adjustmentType: 'NONE', voucherNo: '', remark: '' })

const resolveRules = {
  adjustmentType: [{ required: true, message: '请选择调整方式' }],
}

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
  if (!newDiff.diffType || !newDiff.reason) { ElMessage.warning('请填写差异类型和原因'); return }
  try {
    await opApi.reconAddDiff(currentRow.value.id, {
      diffType: newDiff.diffType,
      ourAmount: parseFloat(newDiff.ourAmount) || 0,
      supplierAmount: parseFloat(newDiff.supplierAmount) || 0,
      reason: newDiff.reason
    })
    ElMessage.success('差异已添加')
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
    ElMessage.success('差异已处理')
    resolveVisible.value = false
    await loadDiffs()
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

// ---- LinkU 状态查看（只读） ----
async function syncLinkU(row) {
  try {
    const r = await opApi.reconLinkUStatus(row.id)
    const statusMap = { NONE: '未同步', SYNCED: '已同步', CONFIRMED: '已确认', REJECTED: '有异议' }
    showRowDetail({
      linkuReconId: r.linkuReconId || '无',
      linkuSyncStatus: statusMap[r.linkuSyncStatus] || r.linkuSyncStatus || '未知',
      linkuSyncTime: r.linkuSyncTime,
      note: 'FSSC 不主动推送数据到 LinkU，而是被动接收 LinkU 的 Webhook 回调',
    }, { title: 'LinkU 同步状态', labels: { linkuReconId: '对账单ID', linkuSyncStatus: '同步状态', linkuSyncTime: '同步时间', note: '说明' } })
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
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'reconNo', label: '对账单号' }, { prop: 'supplierName', label: '供应商' }, { prop: 'period', label: '期间' }]
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
  ElMessage.success('已导出 CSV')
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
  a.download = `SupplierRecon_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
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
