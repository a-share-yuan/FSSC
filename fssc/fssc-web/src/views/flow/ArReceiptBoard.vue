<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap; align-items:center">
      <el-input v-model="q.keyword" :placeholder="$t('flow.arRcpt.phKw')" clearable size="small" style="width:200px" />
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" size="small" style="width:240px"
                      :start-placeholder="$t('flow.arRcpt.phFrom')" :end-placeholder="$t('flow.arRcpt.phTo')" />
      <el-select v-model="q.bankMatched" size="small" style="width:150px" :placeholder="$t('flow.arRcpt.colBankMatch')" clearable>
        <el-option :label="$t('flow.arRcpt.optMatched')" value="MATCHED" />
        <el-option :label="$t('flow.arRcpt.optUnmatched')" value="UNMATCHED" />
      </el-select>
      <el-button size="small" type="success" @click="openReceipt">{{ $t('flow.arRcpt.btnRegister') }}</el-button>
      <el-upload v-if="hasPerm('arflow:writeoff:manage')" :show-file-list="false" accept=".xlsx" :auto-upload="false"
                 :on-change="doImportExcel" style="display:inline-block">
        <el-button size="small" type="warning">{{ $t('flow.arRcpt.btnImportExcel') }}</el-button>
      </el-upload>
    </div>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="filtered" size="small" border stripe v-else @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
      <el-table-column prop="billNo" :label="$t('flow.arRcpt.colRcptNo')" width="150" sortable />
      <el-table-column :label="$t('flow.arRcpt.colPartner')" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ customerName(row.customerId) }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.arRcpt.colRcptAmount')" width="120" align="right" sortable>
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="receiptDate" :label="$t('flow.arRcpt.colReceiptDate')" width="110" sortable />
      <el-table-column :label="$t('flow.arRcpt.colPayMethod')" width="90">
        <template #default="{ row }">{{ row.payMethod || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.arRcpt.colOperator')" width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ operatorName(row.operatorId) }}</template>
      </el-table-column>
      <!-- 银行勾对规则取 bankMatchedAmount（部分认领时布尔位仍为 0，只有金额位能算出真实未达额） -->
      <el-table-column :label="$t('flow.arRcpt.colBankMatch')" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.bankSerial">{{ row.bankSerial }} · {{ $t('flow.arRcpt.textMatched', { v1: fmt(row.bankMatchedAmount || 0) }) }}</span>
          <el-tag v-else size="small" type="warning">{{ $t('flow.arRcpt.tagUnmatched') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="90">
        <template #default="{ row }">{{ rcStatusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="170" fixed="right">
        <template #default="{ row }">
          <!-- ④：登记即冲减，最终状态行不再给「先进先出核销」按钮，避免重复核销 -->
          <el-button v-if="!receiptAppliedStatuses.includes(row.status)" link size="small" type="primary" @click="applyReceipt(row)">{{ $t('flow.arRcpt.btnFifoApply') }}</el-button>
          <el-tag v-else size="small" type="success" effect="plain">{{ $t('flow.arRcpt.tagAppliedOnRegister') }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlgReceipt" :title="$t('flow.arRcpt.dlgRegisterTitle')" :width="woMode === 'MANUAL' ? '760px' : '480px'" destroy-on-close>
      <el-form ref="receiptFormRef" :model="rcForm" :rules="receiptRules" label-width="120px">
        <el-form-item :label="$t('flow.arRcpt.labPartner')" prop="customerId">
          <CustomerSelect v-model="rcForm.customerId" value-key="id" :placeholder="$t('flow.arRcpt.phPartner')" @change="onRcCustomerChange" />
        </el-form-item>
        <el-form-item :label="$t('flow.arRcpt.labAmount')" prop="amount"><el-input-number v-model="rcForm.amount" :min="0" :precision="2" style="width:100%" @change="onAmountChange" /></el-form-item>
        <el-form-item :label="$t('flow.arRcpt.labReceiptDate')" prop="receiptDate"><el-date-picker v-model="rcForm.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('flow.arRcpt.labPayMethod')">
          <el-select v-model="rcForm.payMethod" style="width:100%" clearable :placeholder="$t('flow.arRcpt.phPayMethod')">
            <el-option :label="$t('flow.arRcpt.methodBank')" value="银行" />
            <el-option :label="$t('flow.arRcpt.methodCash')" value="现金" />
            <el-option :label="$t('flow.arRcpt.methodNote')" value="票据" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.arRcpt.labBankSerial')"><el-input v-model="rcForm.bankSerial" :placeholder="$t('flow.arRcpt.phBankSerial')" /></el-form-item>
        <!-- 核销方式二选一。原对话框只有自动先进先出一条路，操作人无法把一笔收款
             指定冲某几张应收（后端 /receipt/create 的 lines 通道早已支持，界面无入口）；
             自动路径又会跨法人主体串账（一家公司的收款会冲掉另一家公司的应收）。
             后端已加公司校验，这里把手工指定通道也开出来，并明示自动路径的取单规则。 -->
        <el-form-item :label="$t('flow.arRcpt.labWriteoffMode')">
          <el-radio-group v-model="woMode" @change="onWoModeChange">
            <el-radio-button value="AUTO">{{ $t('flow.arRcpt.modeAuto') }}</el-radio-button>
            <el-radio-button value="MANUAL">{{ $t('flow.arRcpt.modeManual') }}</el-radio-button>
          </el-radio-group>
          <div class="rc-wo-tip">{{ woMode === 'AUTO' ? $t('flow.arRcpt.tipAuto') : $t('flow.arRcpt.tipManual') }}</div>
        </el-form-item>
      </el-form>
      <div v-if="woMode === 'MANUAL'" class="rc-wo">
        <el-table :data="recvCandidates" size="small" border max-height="260" :empty-text="recvLoading ? $t('common.loading') : $t('flow.arRcpt.msgNoOpenRecv')"
                  @selection-change="onRecvPick">
          <el-table-column type="selection" width="42" />
          <el-table-column prop="billNo" :label="$t('flow.arRcpt.colRecvNo')" width="160" show-overflow-tooltip />
          <el-table-column prop="dueDate" :label="$t('flow.arRcpt.colRecvDue')" width="104" />
          <el-table-column :label="$t('flow.arRcpt.colRecvRemain')" width="120" align="right">
            <template #default="{ row }">{{ fmt(row.remainAmount) }}</template>
          </el-table-column>
          <el-table-column :label="$t('flow.arRcpt.colRecvOffset')" min-width="150" align="right">
            <template #default="{ row }">
              <el-input-number v-model="offsetOf[row.id]" size="small" :min="0" :max="Number(row.remainAmount || 0)"
                               :precision="2" controls-position="right" style="width:140px" />
            </template>
          </el-table-column>
        </el-table>
        <div class="rc-wo-sum">
          {{ $t('flow.arRcpt.textWoSum', { v1: fmt(offsetSum), v2: fmt(rcForm.amount), v3: fmt(Math.max(Number(rcForm.amount || 0) - offsetSum, 0)) }) }}
        </div>
      </div>
      <template #footer>
        <el-button @click="dlgReceipt = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveReceipt">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="RECEIPT" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { arGapApi, receivableApi, mdApi, userApi } from '@/api'
import SkeletonList from '@/components/SkeletonList.vue'
import BillTraceDrawer from '@/components/BillTraceDrawer.vue'
import CustomerSelect from '@/components/common/CustomerSelect.vue'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { hasPerm } from '@/utils/perm'
import { showRowDetail, todayLocal } from '@/utils/format'
import { rcStatusLabel } from '@/constants/enums'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const traceVisible = ref(false)
const traceBillId = ref(null)
function openRowDetail(row) {
  traceBillId.value = row.id
  traceVisible.value = true
  return showRowDetail(row, { title: t('flow.arRcpt.ttlDetail') })
}

const loading = ref(false)
const receipts = ref([])
const customers = ref([])
const users = ref([])
const q = reactive({ keyword: '', bankMatched: '' })
const dateRange = ref([])
function customerName(id) { const c = customers.value.find((x) => x.id === id); return c ? (c.customerName || c.supplierName || c.name || '—') : (id ? '#' + id : '—') }
function operatorName(id) { const u = users.value.find((x) => x.id === id); return u ? (u.nickName || u.userName) : (id ? '#' + id : '—') }

// ④：收款单"已核销"最终状态名称表（后端 RcReceiptService.APPLIED_RECEIPT_STATUSES 同一规则）
const receiptAppliedStatuses = ['WRITTEN_OFF', 'WRITE_OFF', 'APPLIED']

const filtered = computed(() => receipts.value.filter((r) => {
  const kw = String(q.keyword || '').trim().toLowerCase()
  if (kw) {
    const name = String(customerName(r.customerId) || '').toLowerCase()
    if (!String(r.billNo || '').toLowerCase().includes(kw) && !name.includes(kw)) return false
  }
  const range = (dateRange.value || []).filter(Boolean)
  if (range.length === 2) {
    const d = String(r.receiptDate || '')
    if (!d || d < range[0] || d > range[1]) return false
  }
  if (q.bankMatched === 'MATCHED' && !r.bankSerial) return false
  if (q.bankMatched === 'UNMATCHED' && r.bankSerial) return false
  return true
}))

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

async function loadReceipt() {
  loading.value = true
  try { receipts.value = await receivableApi.receiptList() }
  catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

const dlgReceipt = ref(false)
const rcForm = ref({ customerId: null, amount: 0, receiptDate: '', payMethod: '', bankSerial: '' })
const receiptFormRef = ref(null)
const { rules: receiptRules } = useFormRules('RECEIPT')
const { submit: guardReceipt } = useSubmitGuard(receiptFormRef)

/**
 * 手工指定应收单核销（后端 /receipt/create 的 lines 通道）。
 * 候选＝该往来单位在本法人主体下的未清应收（后端 listByCustomer 已加公司校验，
 * 且排序＝到期日+id 升序，与自动 FIFO 同序）；status 排除 NONE/REJECTED 与后端候选规则一致。
 */
const woMode = ref('AUTO')
const recvLoading = ref(false)
const recvCandidates = ref([])
const recvPicked = ref([])
const offsetOf = reactive({})
const offsetSum = computed(() => recvPicked.value.reduce((a, r) => a + Number(offsetOf[r.id] || 0), 0))

async function loadRecvCandidates() {
  const cid = rcForm.value.customerId
  recvCandidates.value = []
  recvPicked.value = []
  if (!cid) return
  recvLoading.value = true
  try {
    const rows = await receivableApi.listByCustomer(cid)
    recvCandidates.value = (rows || []).filter((r) =>
      Number(r.remainAmount || 0) > 0 && !['NONE', 'REJECTED'].includes(r.status))
  } catch (e) { ElMessage.error(e.message) } finally { recvLoading.value = false }
}

/** 勾选变化：新勾上的行按 FIFO 顺序把剩余收款额摊进去（每行不超过该单余额） */
function onRecvPick(rows) {
  const added = rows.filter((r) => !recvPicked.value.some((x) => x.id === r.id))
  recvPicked.value = rows
  if (added.length) refill()
}

/** 按候选顺序（到期日先者先得）重摊本次核销额，合计＝收款金额；超出余额的行封顶在余额 */
function refill() {
  let rest = Number(rcForm.value.amount || 0)
  for (const r of recvCandidates.value) {
    if (!recvPicked.value.some((x) => x.id === r.id)) { offsetOf[r.id] = 0; continue }
    const cap = Number(r.remainAmount || 0)
    const v = Math.min(cap, Math.max(rest, 0))
    offsetOf[r.id] = Number(v.toFixed(2))
    rest -= v
  }
}

function onRcCustomerChange() { if (woMode.value === 'MANUAL') loadRecvCandidates() }
function onAmountChange() { if (woMode.value === 'MANUAL' && recvPicked.value.length) refill() }
function onWoModeChange(v) { if (v === 'MANUAL') loadRecvCandidates() }

// 收款日期字段名对齐实体 RcReceipt.receiptDate（原绑 payDate，实体无此属性⇒选的日期从未生效）
function openReceipt() {
  rcForm.value = { customerId: null, amount: 0, receiptDate: todayLocal(), payMethod: '', bankSerial: '' }
  woMode.value = 'AUTO'
  recvCandidates.value = []
  recvPicked.value = []
  dlgReceipt.value = true
}
async function saveReceipt() {
  await guardReceipt(async () => {
    const payload = { ...rcForm.value }
    if (woMode.value === 'MANUAL') {
      // 前端先按后端同一套勾稽校验，避免用户填错后只能靠 500 级报错回退
      const lines = recvPicked.value
        .map((r) => ({ receivableId: r.id, offsetAmount: Number(offsetOf[r.id] || 0) }))
        .filter((l) => l.offsetAmount > 0)
      if (!lines.length) { ElMessage.warning(t('flow.arRcpt.msgPickRecv')); return }
      const sum = lines.reduce((a, l) => a + l.offsetAmount, 0)
      // 金额比较留半分容差：el-input-number 逐行四舍五入后直接 !== 会假红
      if (Math.abs(sum - Number(payload.amount || 0)) > 0.005) {
        ElMessage.warning(t('flow.arRcpt.msgOffsetSumMismatch', { v1: fmt(sum), v2: fmt(payload.amount) })); return
      }
      payload.lines = lines
    }
    try {
      const newId = await receivableApi.receiptCreate(payload)
      ElMessage.success(t('flow.arRcpt.msgRegistered'))
      dlgReceipt.value = false
      // 保存即刷新，并复核新行是否真的进了视野。
      // 原实现 fire-and-forget 调 loadReceipt()，一旦新行被工具条检索条件（关键字/日期区间/银行勾对）
      // 过滤掉或不在本账号可见范围内，列表看起来"没变化"，操作人会误判成没写入数据库。
      await loadReceipt()
      if (newId && !filtered.value.some((r) => r.id === newId)) {
        ElMessage.warning(t('flow.arRcpt.msgRegisteredNotListed'))
      }
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function applyReceipt(row) {
  try {
    await receivableApi.receiptApply(row.id)
    ElMessage.success(t('flow.arRcpt.msgNoDoubleApply'))
    loadReceipt()
  } catch (e) { ElMessage.error(e.message) }
}

/** 收款核销 Excel 模板导入（后端 /ar-flow/receipts/import-excel，需 arflow:writeoff:manage） */
async function doImportExcel(uploadFile) {
  try {
    const fd = new FormData()
    fd.append('file', uploadFile.raw)
    const r = await arGapApi.importReceiptExcel(fd)
    ElMessage.success(t('flow.arRcpt.msgImported', { v1: r.created, v2: r.errors?.length || 0, v3: r.errors?.length ? '：' + r.errors[0] : '' }))
    await loadReceipt()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(() => {
  mdApi.customers({ pageNum: 1, pageSize: 200 }).then((d) => { customers.value = d?.list || [] }).catch(() => {})
  userApi.options({ pageNum: 1, pageSize: 500 }).then((d) => { users.value = d?.list || [] }).catch(() => {})
  loadReceipt()
})
</script>

<style scoped>
.rc-wo-tip { width: 100%; font-size: 12px; color: #909399; line-height: 18px; margin-top: 4px; }
.rc-wo { padding: 0 4px 4px; }
.rc-wo-sum { font-size: 12px; color: var(--app-text-secondary, #606266); margin-top: 6px; }
</style>
