<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center">
      <el-select v-model="filterType" style="width: 160px" @change="loadBills">
        <el-option :label="$t('common.all')" value="" />
        <el-option :label="$t('expense.ledger.colClaim')" value="CLAIM" />
        <el-option :label="$t('expense.ledger.colPayment')" value="PAY" />
        <el-option :label="$t('expense.ledger.optLoan')" value="LOAN" />
        <el-option :label="$t('expense.preapply.tabRequisition')" value="APPLY" />
      </el-select>
      <!-- P2-1 渠道筛选：走服务端 source 参数（myPage 等值过滤），不做前端本地筛——
           本地只能在已加载的 50 条里挑，挑出来的"0 条手机端"是假结论。 -->
      <el-select v-model="filterSource" style="width: 120px" @change="loadBills">
        <el-option :label="$t('expense.ledger.phAllChannels')" value="" />
        <el-option :label="$t('expense.ledger.optDesktop')" value="PC" />
        <el-option :label="$t('expense.ledger.optMobile')" value="H5" />
      </el-select>
      <el-input v-model="keyword" :placeholder="$t('expense.ledger.phSearchBill')" clearable size="small" style="width: 180px"
                @keyup.enter="loadBills" @clear="loadBills" />
      <el-button size="small" @click="exportCsv">{{ $t('expense.common.btnExportCsv') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('expense.common.btnExportSelected', { v1: selRows.length }) }}</el-button>
      <el-button size="small" @click="openLoanLedger">{{ $t('expense.ledger.btnLoanLedger') }}</el-button>
      <el-button size="small" @click="openColdDialog">{{ $t('expense.ledger.tabArchived') }}</el-button>
    </div>
    <el-table :data="displayBills" size="small" stripe @row-click="onRowClick" @selection-change="handleSelectionChange" @row-dblclick="openTrace" style="cursor:pointer">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" :label="$t('field.docNo')" width="150" sortable />
      <el-table-column :label="$t('field.type')" width="80">
        <template #default="{ row }">{{ billTypeLabel(row.billType) }}</template>
      </el-table-column>
      <!-- P2-1 渠道列：枚举取名称表、禁中英直接显示（既有枚举治理约定）；未知值显「—」不猜 -->
      <el-table-column :label="$t('expense.ledger.colChannel')" width="72">
        <template #default="{ row }">{{ sourceLabel(row.source) }}</template>
      </el-table-column>
      <el-table-column :label="$t('expense.ledger.colSubType')" width="90">
        <template #default="{ row }">{{ billSubTypeLabel(row.billSubType) }}</template>
      </el-table-column>
      <el-table-column prop="title" :label="$t('expense.common.colTitle')" min-width="200" show-overflow-tooltip sortable />
      <el-table-column prop="totalAmount" :label="$t('field.amount')" width="100" sortable :formatter="(r,c,v) => formatMoney(v)" />
      <el-table-column :label="$t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="voucherNo" :label="$t('expense.ledger.colVoucherNo')" width="140" sortable />
      <el-table-column :label="$t('common.operation')" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click.stop="view(row)">{{ $t('field.detail') }}</el-button>
          <el-button v-if="canEdit(row)" link type="warning" size="small" @click.stop="editBill(row)">{{ row.status === 'REJECTED' ? t('expense.common.btnEditResubmit') : t('expense.common.btnEdit') }}</el-button>
          <el-button link size="small" @click.stop="openTrace(row)">{{ $t('expense.common.btnTrace') }}</el-button>
          <!-- 高频操作内联，其余次要操作收进"更多"下拉，避免一列挤 7 个按钮 -->
          <!-- 「我的单据」= 申请人视角：仅保留本人可做的动作（撤回/作废/冲销预付/查看凭证）。
               审批通过/驳回属于审批人动作，只在「我的待办」出现，此处绝不提供（避免"自己审自己"）。 -->
          <el-dropdown v-if="hasMoreActions(row)" trigger="click" @command="(cmd) => onMoreCommand(row, cmd)">
            <el-button link size="small" @click.stop>{{ $t('expense.bill.btnMore') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="row.billType === 'CLAIM' && (row.status === BillStatus.APPROVING || row.status === BillStatus.APPROVED)" command="offset">{{ $t('expense.bill.btnOffsetPrepay') }}</el-dropdown-item>
                <el-dropdown-item v-if="row.status === BillStatus.DRAFT" command="void">{{ $t('field.void') }}</el-dropdown-item>
                <el-dropdown-item v-if="canWithdraw(row)" command="withdraw">{{ $t('expense.bill.btnWithdraw') }}</el-dropdown-item>
                <el-dropdown-item v-if="row.voucherNo && hasPerm('sharing:voucher:list')" command="voucher">{{ $t('expense.bill.btnViewVoucher') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <!-- 员工借款余额台账 -->
  <el-dialog v-model="loanLedgerDialog" :title="$t('expense.ledger.titleLoanLedger')" width="720px">
    <div style="margin-bottom: 8px; display: flex; align-items: center; gap: 8px">
      <span style="font-size: 13px; color: #606266">{{ $t('expense.ledger.btnOverdueOnly') }}</span>
      <el-switch v-model="onlyOverdue" @change="loadLoanLedger" />
    </div>
    <SkeletonList v-if="loanLedgerLoading" :rows="8" :cols="5" />
    <el-table v-else :data="loanLedgerRows" size="small" border :row-class-name="loanRowClass" max-height="420">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="applyUserName" :label="$t('expense.ledger.colBorrower')" width="120" sortable />
      <el-table-column :label="$t('expense.ledger.colLoanTotal')" width="120">
        <template #default="{ row }">{{ fmt(row.loanTotal) }}</template>
      </el-table-column>
      <el-table-column :label="$t('expense.ledger.colUnpaidBalance')" width="120">
        <template #default="{ row }">{{ fmt(row.balance) }}</template>
      </el-table-column>
      <el-table-column :label="$t('expense.ledger.colDueDate')" width="130">
        <template #default="{ row }">{{ row.expectRepayDate || row.earliestRepayDate || '\u2014' }}</template>
      </el-table-column>
      <el-table-column :label="$t('expense.ledger.colOverdueDays')" width="100">
        <template #default="{ row }">{{ row.overdue ? row.overdueDays : '\u2014' }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.overdue ? 'danger' : 'success'" size="small">{{ row.overdue ? t('expense.ledger.stOverdue') : t('expense.ledger.stNormal') }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>

  <!-- 历史（冷）单据查询 -->
  <el-dialog v-model="coldDialog" :title="$t('expense.ledger.tabArchivedFull')" width="920px">
    <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
      <el-input v-model="coldFilter.billNo" :placeholder="$t('expense.ledger.phBillNoFuzzy')" size="small" style="width: 180px" clearable />
      <el-date-picker v-model="coldFilter.beginDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('expense.ledger.phApplyDateStart')" size="small" style="width: 160px" />
      <el-date-picker v-model="coldFilter.endDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('expense.ledger.phApplyDateEnd')" size="small" style="width: 160px" />
      <el-button size="small" type="primary" @click="loadColdPage(1)">{{ $t('common.search') }}</el-button>
      <el-button size="small" @click="resetColdFilter">{{ $t('common.reset') }}</el-button>
    </div>
    <SkeletonList v-if="coldLoading" :rows="8" :cols="5" />
    <el-table v-else :data="coldRows" size="small" border max-height="420"
              :empty-text="$t('expense.ledger.hintNoArchived')">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="bill_no" :label="$t('field.docNo')" width="160" sortable />
      <el-table-column :label="$t('field.type')" width="80">
        <template #default="{ row }">{{ billTypeLabel(row.bill_type) }}</template>
      </el-table-column>
      <el-table-column prop="title" :label="$t('expense.common.colTitle')" min-width="200" show-overflow-tooltip sortable />
      <el-table-column :label="$t('field.amount')" width="120">
        <template #default="{ row }">{{ fmt(row.total_amount) }}</template>
      </el-table-column>
      <el-table-column prop="status" :label="$t('common.status')" width="100" sortable>
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('expense.ledger.colFiledAt')" width="180">
        <template #default="{ row }">{{ row.archived_at || '\u2014' }}</template>
      </el-table-column>
    </el-table>
    <div style="margin-top: 10px; display: flex; justify-content: flex-end">
      <el-pagination background layout="total, prev, pager, next, sizes"
                     :total="coldTotal" :current-page="coldPage" :page-size="coldPageSize"
                     :page-sizes="[10, 20, 50]"
                     @current-change="loadColdPage" @size-change="onColdSizeChange" />
    </div>
  </el-dialog>

  <!-- 冲销预付款单 -->
  <el-dialog v-model="offsetDialog" :title="$t('expense.ledger.btnWriteOffPrepay')" width="520px">
    <el-form :rules="offsetFormRules" label-width="90px" size="default">
      <el-form-item :label="$t('expense.ledger.colClaimBill')">
        <span>{{ offsetTarget.billNo }} {{ offsetTarget.title }}</span>
      </el-form-item>
      <el-form-item :label="$t('expense.ledger.colPrepayBill')">
        <el-select v-model="offsetForm.prepayBillId" filterable :loading="prepayLoading" style="width: 100%" :placeholder="$t('expense.ledger.phPickPrepay')">
          <el-option v-for="p in prepayOptions" :key="p.id" :label="$t('expense.bill.optPrepayCandidate', { v1: p.billNo, v2: p.title || '', v3: fmt(p.totalAmount) })" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('expense.ledger.colWriteOffAmount')">
        <el-input-number v-model="offsetForm.amount" :min="0" :precision="2" style="width: 100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="offsetDialog = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="offsetSubmitting" @click="submitOffset">{{ $t('expense.ledger.btnConfirmWriteOff') }}</el-button>
    </template>
  </el-dialog>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />

  <!-- 单据详情抽屉：与审批工作台「去处理」共用同一详情面板，字段含义一致 -->
  <el-drawer v-model="detailVisible" :title="$t('expense.bill.ttlBillDetail', { v1: detailBillNo || '' })" direction="rtl" size="820px" :destroy-on-close="true">
    <BillDetailPanel :bill-id="detailBillId" :bill-type="detailBillType" />
  </el-drawer>
</template>

<script setup>

import { onMounted, ref, reactive, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billApi, detailApi, wfApi } from '../../api'
import { statusType as _statusType, statusLabel as _statusLabel, BillStatus, canEditResubmit, editBillPath } from '../../constants/billStatus'
import { billTypeLabel, billSubTypeLabel } from '@/constants/enums.js'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import BillDetailPanel from '../../components/bill/BillDetailPanel.vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatMoney } from '@/utils/format'
import { hasPerm } from '@/utils/perm'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => loadBills(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceBizType.value = row.billType || null
  traceVisible.value = true
}

// 单据详情抽屉（复用 BillDetailPanel）
const detailVisible = ref(false)
const detailBillId = ref(null)
const detailBillType = ref('')
const detailBillNo = ref('')

const emit = defineEmits(['view-voucher'])

const router = useRouter()
const currentRoute = useRoute()

const bills = ref([])
const filterType = ref('')
// P2-1 渠道（exp_bill.source）：筛选值与服务端白名单同域（只认 PC/H5，见 ExpenseBillService#normalizeBillSource）
const filterSource = ref('')
/** 渠道中文名名称表：库里存枚举码、界面显中文（枚举治理约定：禁中英直接显示、缺失不猜） */
function sourceLabel(v) {
  return { PC: t('expense.ledger.optDesktop'), H5: t('expense.ledger.optMobile') }[v] || '—'
}
const keyword = ref('')
const selRows = ref([])

// 单号/标题前端过滤（支持从待办等入口携带 billNo query 自动定位）
const displayBills = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return bills.value
  return bills.value.filter((b) =>
    String(b.billNo || '').toLowerCase().includes(kw) || String(b.title || '').toLowerCase().includes(kw))
})

// 借款台账
const loanLedgerDialog = ref(false)
const onlyOverdue = ref(false)
const loanLedgerRows = ref([])
const loanLedgerLoading = ref(false)

// 冷票据
const coldDialog = ref(false)
const coldLoading = ref(false)
const coldRows = ref([])
const coldTotal = ref(0)
const coldPage = ref(1)
const coldPageSize = ref(20)
const coldFilter = reactive({ billNo: '', beginDate: '', endDate: '' })

// 冲销预付
const offsetDialog = ref(false)
const offsetTarget = ref({})
const offsetSubmitting = ref(false)
const prepayLoading = ref(false)
const prepayOptions = ref([])
const offsetForm = reactive({ prepayBillId: null, amount: 0 })

// 表单校验规则
const offsetFormRules = computed(() => (reactive({
  prepayBillId: [{ required: true, message: t('expense.ledger.errPrepayRequired') }],
  amount: [{ required: true, message: t('expense.ledger.errWriteOffAmountRequired') }],
})))

function fmt(v) { return v == null ? '0.00' : Number(v).toFixed(2) }
function statusType(s) { return _statusType(s) }
function statusLabel(s) { return _statusLabel(s) }
// 单据类型/子类取中文：改用全局字典 constants/enums.js（原来此处手写了 5 项局部映射，
// 与字典同义但少 30 多个码，且各页重复定义违反 enums.js 顶部约定）

// 点击行 = 打开详情抽屉（与「详情」按钮一致），不再跳凭证页（旧逻辑会 emit('select-bill') 切到隐藏的凭证 tab，造成单页模式困死）
function onRowClick(row) { openDetailById(row.id, row.billType, row.billNo) }

async function loadBills() {
  // keyword 交服务端过滤（后端 myPage 已补该入参）：原先只在已加载的前 50 条里前端筛，
  // 单据一多，深链携带的单号/标题就筛不到（D-DEC-245 遗留第 3 项）。前端过滤保留，两者同一规则不冲突。
  const kw = keyword.value.trim()
  bills.value = (await billApi.myPage({
    pageNum: 1, pageSize: 50, billType: filterType.value, keyword: kw || undefined,
    source: filterSource.value || undefined,
  })).list || []
}

async function view(row) {
  openDetailById(row.id, row.billType, row.billNo)
}

/** 打开单据详情抽屉（供列表「详情」按钮与深链 ?billId= 共用） */
function openDetailById(id, billType, billNo) {
  if (id == null) { ElMessage.warning(t('expense.ledger.errNoBillId')); return }
  detailBillId.value = id
  detailBillType.value = billType || ''
  detailBillNo.value = billNo || ''
  detailVisible.value = true
}

async function voidBill(row) {
  try {
    await ElMessageBox.confirm('\u786e\u8ba4\u4f5c\u5e9f\u8be5\u8349\u7a3f\uff1f\u6b64\u64cd\u4f5c\u4e0d\u53ef\u6062\u590d\u3002', '\u4f5c\u5e9f\u786e\u8ba4', { type: 'warning' })
    await billApi.voidBill(row.id)
    ElMessage.success('\u5df2\u4f5c\u5e9f'); loadBills()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

async function withdraw(row) {
  await billApi.withdraw(row.id); ElMessage.success('\u5df2\u64a4\u56de'); loadBills()
}

// 借款台账
async function openLoanLedger() {
  loanLedgerDialog.value = true
  await loadLoanLedger()
}
async function loadLoanLedger() {
  loanLedgerLoading.value = true
  try {
    loanLedgerRows.value = (await billApi.loanLedger(onlyOverdue.value)) || []
  } catch (e) { ElMessage.error(e.message) }
  finally { loanLedgerLoading.value = false }
}
function loanRowClass({ row }) { return row.overdue ? 'loan-overdue-row' : '' }

// 冷票据
async function openColdDialog() {
  coldDialog.value = true
  coldPage.value = 1
  await loadColdPage(1)
}
async function loadColdPage(p) {
  coldPage.value = p || 1
  coldLoading.value = true
  try {
    const r = await billApi.coldPage({
      pageNum: coldPage.value, pageSize: coldPageSize.value,
      billNo: coldFilter.billNo || undefined,
      beginDate: coldFilter.beginDate || undefined,
      endDate: coldFilter.endDate || undefined,
    })
    coldRows.value = r.list || []
    coldTotal.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { coldLoading.value = false }
}
function resetColdFilter() {
  coldFilter.billNo = ''
  coldFilter.beginDate = ''
  coldFilter.endDate = ''
  loadColdPage(1)
}
function onColdSizeChange(sz) {
  coldPageSize.value = sz
  loadColdPage(1)
}

// 冲销预付
async function openOffsetDialog(row) {
  offsetTarget.value = row
  offsetForm.prepayBillId = null
  offsetForm.amount = 0
  offsetDialog.value = true
  prepayLoading.value = true
  try {
    const list = (await billApi.prepayPage()).list || []
    prepayOptions.value = list.filter((b) => b.billSubType === 'PREPAY' && b.id !== row.id)
  } catch (e) { ElMessage.error(e.message) }
  finally { prepayLoading.value = false }
}
async function submitOffset() {
  if (!offsetForm.prepayBillId) { ElMessage.warning('\u8bf7\u9009\u62e9\u9884\u4ed8\u6b3e\u5355'); return }
  if (!(Number(offsetForm.amount) > 0)) { ElMessage.warning('\u51b2\u9500\u91d1\u989d\u9700\u5927\u4e8e 0'); return }
  offsetSubmitting.value = true
  try {
    await detailApi.prepayOffset(offsetTarget.value.id, { prepayBillId: offsetForm.prepayBillId, amount: offsetForm.amount })
    ElMessage.success('\u51b2\u9500\u9884\u4ed8\u6b3e\u6210\u529f')
    offsetDialog.value = false
    loadBills()
  } catch (e) { ElMessage.error(e.message) }
  finally { offsetSubmitting.value = false }
}

function handleSelectionChange(val) { selRows.value = val }

function exportCsv() {
  if (!bills.value.length) { ElMessage.warning('\u5f53\u524d\u65e0\u6570\u636e\u53ef\u5bfc\u51fa'); return }
  const cols = [
    { prop: 'billNo', label: '\u5355\u53f7' }, { prop: 'billType', label: '\u7c7b\u578b' },
    { prop: 'billSubType', label: '\u5b50\u7c7b' }, { prop: 'title', label: '\u6807\u9898' },
    { prop: 'totalAmount', label: '\u91d1\u989d' }, { prop: 'status', label: '\u72b6\u6001' }, { prop: 'voucherNo', label: '\u51ed\u8bc1\u53f7' },
  ]
  const header = cols.map((c) => c.label).join(',')
  const lines = bills.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `\u6211\u7684\u5355\u636e_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('\u5df2\u5bfc\u51fa CSV')
}

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning('\u8bf7\u5148\u52fe\u9009\u8981\u5bfc\u51fa\u7684\u8bb0\u5f55'); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning('\u65e0\u6cd5\u83b7\u53d6\u5217\u4fe1\u606f'); return }
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
  a.download = `Expense_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`\u5df2\u5bfc\u51fa ${selRows.value.length} \u6761\u8bb0\u5f55`)
}

onMounted(() => {
  // 支持从工作台/待办等入口携带 billNo（或 keyword）直达定位；
  // 携带 billId 时直接打开该单据详情抽屉（消除「跳到列表却看不到单据」的断链）。
  const q = currentRoute.query
  if (q.billNo) keyword.value = String(q.billNo)
  else if (q.keyword) keyword.value = String(q.keyword)
  if (q.billId != null && q.billId !== '') {
    openDetailById(q.billId, q.billType ? String(q.billType) : '', q.billNo ? String(q.billNo) : '')
  }
  loadBills()
})

// 同页再次深链（已在 /expense/my 时又带新 billId 过来）→ 组件被复用、onMounted 不再触发，需 watch 默认处理
watch(() => currentRoute.query.billId, (v) => {
  if (v != null && v !== '') openDetailById(v, currentRoute.query.billType ? String(currentRoute.query.billType) : '', currentRoute.query.billNo ? String(currentRoute.query.billNo) : '')
})

defineExpose({ loadBills, openDetailById })

/** 「更多」下拉是否有可显示项（避免出现空下拉） */
function hasMoreActions(row) {
  return (row.billType === 'CLAIM' && (row.status === BillStatus.APPROVING || row.status === BillStatus.APPROVED))
    || row.status === BillStatus.DRAFT
    || canWithdraw(row)
    || (!!row.voucherNo && hasPerm('sharing:voucher:list'))
}

/** 撤回：仅本人发起且流程仍在审批中（首次提交后、最终状态前） */
function canWithdraw(row) {
  return row.status === BillStatus.APPROVING || row.status === BillStatus.SUBMITTED
}

// "更多"下拉命令分发：把次要操作从操作列收进下拉，避免按钮拥挤
// 编辑草稿 / 驳回单编辑重提：带 editBillId 深链跳到对应填单页载入表单。
// 不再按类型白名单拦（旧 EDITABLE_TYPES 会让 APPLY/PAY 等驳回单无编辑入口成死单）；
// 填单页路由统一走 constants/billStatus#editBillPath。
function canEdit(row) {
  return canEditResubmit(row)
}
function editBill(row) {
  router.push({ path: editBillPath(row), query: { editBillId: row.id } })
}
function onMoreCommand(row, cmd) {
  const map = {
    offset: () => openOffsetDialog(row),
    void: () => voidBill(row),
    withdraw: () => withdraw(row),
    voucher: () => emit('view-voucher', row),
  }
  map[cmd]?.()
}
</script>

<style scoped>
:deep(.loan-overdue-row) { color: #f56c6c; }
:deep(.loan-overdue-row) td.el-table__cell { background-color: #fef0f0 !important; }
</style>
