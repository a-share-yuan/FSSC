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
      <!-- 银行勾对口径取 bankMatchedAmount（部分认领时布尔位仍为 0，只有金额位能算出真实未达额） -->
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
          <!-- D-DEC-423④：登记即冲减（D-V3-143），终态行不再给「先进先出核销」按钮，避免重复核销 -->
          <el-button v-if="!receiptAppliedStatuses.includes(row.status)" link size="small" type="primary" @click="applyReceipt(row)">{{ $t('flow.arRcpt.btnFifoApply') }}</el-button>
          <el-tag v-else size="small" type="success" effect="plain">{{ $t('flow.arRcpt.tagAppliedOnRegister') }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlgReceipt" :title="$t('flow.arRcpt.dlgRegisterTitle')" width="480px" destroy-on-close>
      <el-form ref="receiptFormRef" :model="rcForm" :rules="receiptRules" label-width="120px">
        <el-form-item :label="$t('flow.arRcpt.labPartner')" prop="customerId">
          <CustomerSelect v-model="rcForm.customerId" value-key="id" :placeholder="$t('flow.arRcpt.phPartner')" />
        </el-form-item>
        <el-form-item :label="$t('flow.arRcpt.labAmount')" prop="amount"><el-input-number v-model="rcForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('flow.arRcpt.labReceiptDate')" prop="receiptDate"><el-date-picker v-model="rcForm.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('flow.arRcpt.labPayMethod')">
          <el-select v-model="rcForm.payMethod" style="width:100%" clearable :placeholder="$t('flow.arRcpt.phPayMethod')">
            <el-option :label="$t('flow.arRcpt.methodBank')" value="银行" />
            <el-option :label="$t('flow.arRcpt.methodCash')" value="现金" />
            <el-option :label="$t('flow.arRcpt.methodNote')" value="票据" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.arRcpt.labBankSerial')"><el-input v-model="rcForm.bankSerial" :placeholder="$t('flow.arRcpt.phBankSerial')" /></el-form-item>
      </el-form>
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
import { showRowDetail } from '@/utils/format'
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

// D-DEC-423④：收款单"已核销"终态词表（后端 RcReceiptService.APPLIED_RECEIPT_STATUSES 同口径）
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

// 收款日期字段名对齐实体 RcReceipt.receiptDate（原绑 payDate，实体无此属性⇒选的日期从未生效）
function openReceipt() {
  rcForm.value = { customerId: null, amount: 0, receiptDate: new Date().toISOString().slice(0, 10), payMethod: '', bankSerial: '' }
  dlgReceipt.value = true
}
async function saveReceipt() {
  await guardReceipt(async () => {
    try {
      await receivableApi.receiptCreate(rcForm.value)
      ElMessage.success(t('flow.arRcpt.msgRegistered'))
      dlgReceipt.value = false
      loadReceipt()
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
  userApi.page({ pageNum: 1, pageSize: 500 }).then((d) => { users.value = d?.list || [] }).catch(() => {})
  loadReceipt()
})
</script>
