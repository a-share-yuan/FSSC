<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.entry.apServiceTitle') }}</span>
        <div class="ops">
          <el-select v-model="q.reqType" size="small" style="width: 140px" :placeholder="$t('flow.request.phAllTypes')" clearable @change="load">
            <el-option v-for="(l, c) in AP_REQ_TYPE" :key="c" :label="l" :value="c" />
          </el-select>
          <el-select v-model="q.status" size="small" style="width: 110px" :placeholder="$t('common.status')" clearable @change="load">
            <el-option v-for="(l, c) in AP_REQ_STATUS" :key="c" :label="l" :value="c" />
          </el-select>
          <el-button size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          <el-button type="primary" size="small" @click="openCreate">{{ $t('flow.request.btnNew') }}</el-button>
          <el-upload :show-file-list="false" accept=".xlsx" :auto-upload="false" :on-change="doImportPlan">
            <el-button size="small" type="warning">{{ $t('flow.request.importExcelTitle') }}</el-button>
          </el-upload>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="billNo" :label="$t('flow.request.colApplyNo')" min-width="175" />
      <el-table-column :label="$t('field.type')" width="100">
        <template #default="{ row }"><el-tag size="small" type="info">{{ apReqTypeLabel(row.reqType) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="vendorName" :label="$t('field.supplier')" min-width="130" show-overflow-tooltip />
      <el-table-column prop="paymentWay" :label="$t('field.method')" width="90" />
      <el-table-column prop="amount" :label="$t('field.amount')" width="110" align="right" />
      <el-table-column prop="invoiceNo" :label="$t('flow.request.colInvoiceNo')" min-width="130" show-overflow-tooltip />
      <el-table-column :label="$t('common.status')" width="92">
        <template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ apReqStatusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column :label="$t('flow.request.colHandler')" width="110">
        <template #default="{ row }">{{ row.handler || (row.handlerPool ? t('flow.request.pickCount', { v1: row.handlerPool }) : '—') }}</template>
      </el-table-column>
      <el-table-column prop="voucherNo" :label="$t('flow.request.colErpVoucher')" width="150" show-overflow-tooltip />
      <el-table-column :label="$t('common.operation')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-for="a in actions(row)" :key="a.key" link type="primary" size="small" @click="act(row, a)">{{ a.label }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建 -->
    <el-dialog v-model="createVisible" :title="$t('flow.request.dlgNew')" width="680px" destroy-on-close>
      <el-form ref="createFormRef" :model="form" :rules="createRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item :label="$t('field.billType')" prop="reqType">
            <el-select v-model="form.reqType" style="width: 100%">
              <el-option v-for="(l, c) in AP_REQ_TYPE" :key="c" :label="l" :value="c" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.method')" prop="paymentWay">
            <el-select v-model="form.paymentWay" style="width: 100%">
              <el-option :label="$t('flow.request.payMethodBankTransfer')" value="银行转账" /><el-option :label="$t('flow.request.colCheque')" value="支票" /><el-option :label="$t('flow.request.colApNote')" value="应付票据" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colSupplierName')"><el-input v-model="form.vendorName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colBu')"><el-input v-model="form.sbu" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.amount')" prop="amount"><el-input v-model="form.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colInvoiceNo')" prop="invoiceNo"><el-input v-model="form.invoiceNo" :placeholder="$t('flow.request.hintInvoiceNoRange')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colPoNo')" prop="poNo"><el-input v-model="form.poNo" :placeholder="$t('flow.request.errPrepayRequired')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.account')"><el-input v-model="form.bankNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colDraftDue')" prop="expireDate"><el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :placeholder="$t('flow.request.errApNoteRequired')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.request.colEndorseAmount')"><el-input v-model="form.paymentAmount" :placeholder="$t('flow.request.needInvoiceTotal')" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doCreate">{{ $t('field.create') }}</el-button>
      </template>
    </el-dialog>

    <!-- 确认服务请求 -->
    <el-dialog v-model="confirmVisible" :title="$t('flow.request.confirmTip')" width="520px" destroy-on-close>
      <el-form :model="confirmForm" label-width="110px" size="small">
        <el-form-item :label="$t('field.account')"><el-input v-model="confirmForm.bankNo" /></el-form-item>
        <el-form-item :label="$t('field.method')">
          <el-select v-model="confirmForm.paymentWay" style="width: 100%">
            <el-option :label="$t('flow.request.payMethodBankTransfer')" value="银行转账" /><el-option :label="$t('flow.request.colCheque')" value="支票" /><el-option :label="$t('flow.request.colApNote')" value="应付票据" />
          </el-select>
          <div class="tip">{{ $t('flow.request.hintMethodChange') }}</div>
        </el-form-item>
        <el-form-item :label="$t('flow.request.colDiscount')"><el-input v-model="confirmForm.rebateAmount" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="confirmVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doConfirm">{{ $t('flow.request.btnConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 组长审核 -->
    <el-dialog v-model="reviewVisible" :title="$t('flow.request.stApLeaderReview')" width="420px" destroy-on-close>
      <el-input v-model="reviewNote" type="textarea" :rows="2" :placeholder="$t('flow.request.errRejectReasonRequired')" />
      <template #footer>
        <el-button size="small" @click="doReview(false)">{{ $t('field.reject') }}</el-button>
        <el-button type="primary" size="small" @click="doReview(true)">{{ $t('field.approve') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="420px" destroy-on-close>
      <el-input v-model="inputText" :placeholder="inputKind === 'user' ? $t('flow.request.colHandler') : $t('flow.request.phVoidReason')" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doInput">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { AP_REQ_TYPE, AP_REQ_STATUS, apReqTypeLabel, apReqStatusLabel } from '@/constants/enums'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apFlowApi } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()


const rows = ref([])
const loading = ref(false)
const q = reactive({ pageNum: 1, pageSize: 20, reqType: '', status: '' })
const createVisible = ref(false)
const confirmVisible = ref(false)
const reviewVisible = ref(false)
const inputVisible = ref(false)
const current = ref(null)
const form = reactive({})
const createFormRef = ref(null)
// 条件必输与后端 ApRequestService.validateForms 同口径：预付款要有 PO 编号，付款方法=应付票据要有汇票到期日
const createRules = computed(() => ({
  reqType: commonRules.select(t('flow.request.phDocType')),
  paymentWay: commonRules.select(t('flow.request.phPayMethod')),
  amount: commonRules.amountText(t('flow.request.phAmount')),
  invoiceNo: commonRules.required(t('flow.request.phInvoiceNo')),
  poNo: form.reqType === 'PREPAY' ? commonRules.required(t('flow.request.errPoRequired')) : [],
  expireDate: form.paymentWay === '应付票据' ? commonRules.date(t('flow.request.errNoteDueDate')) : [],
}))
const confirmForm = reactive({})
const reviewNote = ref('')
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')

const statusTag = (s) => ({ PAID: 'success', POSTED: 'success', COMPLETED: 'info', VOIDED: 'danger', GROUP_REVIEW: 'warning' }[s] || 'primary')

function actions(row) {
  switch (row.status) {
    case 'PENDING_DISPATCH': return [{ key: 'dispatch', label: t('flow.request.actAutoDispatch') }, { key: 'assign', label: t('flow.request.actManualDispatch') }, { key: 'void', label: t('field.void') }]
    case 'DISPATCHED': return row.handlerPool ? [{ key: 'claim', label: t('flow.request.actPick') }, { key: 'assign', label: t('flow.request.actOpsDispatch') }] : [{ key: 'confirm', label: t('flow.request.confirmTitle') }, { key: 'post', label: t('flow.request.actPost') }, { key: 'void', label: t('field.void') }]
    case 'GROUP_REVIEW': return [{ key: 'review', label: t('flow.request.stLeaderReview') }]
    case 'POSTED': return [{ key: 'pay', label: t('flow.request.actFundPay') }]
    case 'PAID': return [{ key: 'close', label: t('field.complete') }]
    default: return []
  }
}

async function load() {
  loading.value = true
  try {
    const r = await apFlowApi.page({ ...q, reqType: q.reqType || undefined, status: q.status || undefined })
    rows.value = r.list || []
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function api(fn, okMsg) {
  await fn()
  ElMessage.success(okMsg || t('common.success'))
  await load()
}

function act(row, a) {
  current.value = row
  const id = row.id
  const m = {
    dispatch: () => api(() => apFlowApi.dispatch(id)),
    post: () => api(() => apFlowApi.post(id)),
    pay: () => api(() => apFlowApi.pay(id)),
    close: () => api(() => apFlowApi.close(id)),
  }
  if (m[a.key]) return m[a.key]()
  if (a.key === 'claim' || a.key === 'assign' || a.key === 'void') {
    inputKind.value = a.key
    inputTitle.value = { claim: t('flow.request.actPickClaim'), assign: t('flow.request.actOpsDispatchTransfer'), void: t('flow.request.actVoidStop') }[a.key]
    inputText.value = ''
    inputVisible.value = true
    return
  }
  if (a.key === 'confirm') {
    Object.assign(confirmForm, { bankNo: row.bankNo || '', paymentWay: row.paymentWay || '', rebateAmount: row.rebateAmount ?? 0 })
    confirmVisible.value = true
    return
  }
  if (a.key === 'review') { reviewNote.value = ''; reviewVisible.value = true }
}

async function doInput() {
  const id = current.value.id
  // 选单要认领人、派工要负责人清单、作废要原因，留空后端一律拒（ApRequestService 负责人清单必填）
  if (!inputText.value.trim()) {
    return ElMessage.warning({ claim: t('flow.request.errFillClaimant'), assign: t('flow.request.errFillHandler'), void: t('flow.request.errVoidReason') }[inputKind.value] || t('flow.request.errRequired'))
  }
  if (inputKind.value === 'claim') await api(() => apFlowApi.claim(id, inputText.value), t('flow.request.msgPicked'))
  else if (inputKind.value === 'assign') await api(() => apFlowApi.assign(id, inputText.value))
  else await api(() => apFlowApi.void(id, inputText.value))
  inputVisible.value = false
}

async function doConfirm() {
  await api(() => apFlowApi.confirm(current.value.id, { ...confirmForm }))
  confirmVisible.value = false
}

async function doReview(pass) {
  if (!pass && !reviewNote.value.trim()) return ElMessage.warning(t('flow.request.errRejectReason'))
  await api(() => apFlowApi.groupReview(current.value.id, { pass, note: reviewNote.value, reviewer: t('field.leader') }))
  reviewVisible.value = false
}

async function doCreate() {
  if (!(await validateForms([createFormRef]))) return
  await api(() => apFlowApi.create({ ...form, companyId: 1 }), t('flow.request.msgCreated'))
  createVisible.value = false
}

/** 新建前清空上一次的录入，避免带着旧单内容开单（金额/发票号残留最易造成错单） */
function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  createVisible.value = true
}

async function doImportPlan(file) {
  try {
    const fd = new FormData()
    fd.append('file', file.raw)
    const r = await apFlowApi.importPlan(fd)
    ElMessage.success(t('flow.request.msgImported', { v1: r.created, v2: r.errors.length, v3: r.errors.length ? '：' + r.errors.join('；') : '' }))
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
.tip { font-size: 12px; color: #e6a23c; }
</style>
