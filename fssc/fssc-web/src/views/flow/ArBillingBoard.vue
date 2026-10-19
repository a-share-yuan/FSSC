<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.bill.pageTitle') }}</span>
        <div class="ops">
          <el-radio-group v-model="tab" size="small" @change="load">
            <el-radio-button value="statement">{{ $t('flow.bill.statement') }}</el-radio-button>
            <el-radio-button value="pre">{{ $t('flow.bill.preInvoice') }}</el-radio-button>
            <el-radio-button value="mail">{{ $t('flow.bill.mailPackage') }}</el-radio-button>
            <el-radio-button value="rule">{{ $t('flow.bill.invoiceRule') }}</el-radio-button>
          </el-radio-group>
          <el-button size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          <el-button v-if="tab === 'statement'" type="primary" size="small" @click="importVisible = true">{{ $t('flow.bill.importStatement') }}</el-button>
          <el-button v-if="tab === 'statement'" size="small" @click="openEbsAdjust">{{ $t('flow.bill.erpAdjustPush') }}</el-button>
          <el-button v-if="tab === 'preinvoice'" size="small" @click="openRedPush">{{ $t('flow.bill.erpNegativePush') }}</el-button>
          <el-upload v-if="tab === 'statement'" :show-file-list="false" accept=".xlsx" :auto-upload="false" :on-change="doImportExcel" style="display:inline-block">
            <el-button type="success" size="small">{{ $t('flow.bill.excelTemplateImport') }}</el-button>
          </el-upload>
          <el-button v-if="tab === 'pre'" type="primary" size="small" @click="genVisible = true">{{ $t('flow.bill.splitMergeGenerate') }}</el-button>
        </div>
      </div>
    </template>

    <!-- 对账单 -->
    <el-table v-show="tab === 'statement'" :data="statements" v-loading="loading" size="small" border stripe>
      <el-table-column prop="billNo" :label="$t('field.reconNo')" min-width="180" />
      <el-table-column prop="customerCode" :label="$t('flow.collab.colCustomerCode')" width="100" />
      <el-table-column prop="customerName" :label="$t('field.customer')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="ccy" :label="$t('flow.bill.colCurrency')" width="60" />
      <el-table-column prop="amount" :label="$t('field.amount')" width="105" align="right" />
      <el-table-column prop="taxAmount" :label="$t('field.taxAmount')" width="100" align="right" />
      <el-table-column :label="$t('flow.bill.colCheck')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.checkStatus === 'CHECK_OK' ? 'success' : row.checkStatus === 'CHECK_FAILED' ? 'danger' : 'info'">
            {{ { UNCHECKED: t('flow.bill.statusUnchecked'), CHECK_OK: t('field.approve'), CHECK_FAILED: t('flow.collab.stFailed') }[row.checkStatus] || row.checkStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="diffReason" :label="$t('flow.bill.colDiffReason')" min-width="160" show-overflow-tooltip />
      <el-table-column :label="$t('common.operation')" width="190" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'RECONCILED'" link type="primary" size="small" @click="doCheck(row)">{{ $t('flow.bill.checkData') }}</el-button>
          <el-button v-if="row.status === 'ABNORMAL'" link type="warning" size="small" @click="doAdjust(row)">{{ $t('flow.bill.financeAdjust') }}</el-button>
          <el-button v-if="row.status === 'RECONCILED'" link type="success" size="small" @click="doGen(row.billNo)">{{ $t('flow.bill.goInvoice') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 预制发票 -->
    <el-table v-show="tab === 'pre'" :data="invoices" v-loading="loading" size="small" border stripe>
      <el-table-column prop="preInvoiceNo" :label="$t('flow.collab.colPreInvNo')" min-width="190" />
      <el-table-column prop="customerName" :label="$t('field.customer')" min-width="130" show-overflow-tooltip />
      <el-table-column prop="invoiceType" :label="$t('field.type')" width="60" :formatter="(row, col, val) => invoiceTypeLabel(val)" />
      <el-table-column prop="totalAmount" :label="$t('field.totalWithTax')" width="110" align="right" />
      <el-table-column :label="$t('common.status')" width="105">
        <template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ arBillingStatusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column :label="$t('flow.bill.colTaxWriteback')" min-width="140">
        <template #default="{ row }">{{ row.invoiceNo || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="320" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'BIZ_CONFIRMING'" link type="success" size="small" @click="biz(row, 'bizConfirm')">{{ $t('flow.bill.bizConfirm') }}</el-button>
          <el-button v-if="row.status === 'BIZ_CONFIRMING'" link type="danger" size="small" @click="bizReject(row)">{{ $t('flow.bill.bizReject') }}</el-button>
          <el-button v-if="row.status === 'TO_INVOICE'" link type="primary" size="small" @click="simple(row, 'pushTax')">{{ $t('flow.bill.pushTax') }}</el-button>
          <el-button v-if="row.status === 'PUSHED'" link type="primary" size="small" @click="openWriteback(row)">{{ $t('flow.bill.writebackInvoiceNo') }}</el-button>
          <el-button v-if="row.status === 'INVOICED' && !row.printedFlag" link type="primary" size="small" @click="simple(row, 'print')">{{ $t('flow.bill.print') }}</el-button>
          <el-button v-if="row.status === 'INVOICED'" link type="danger" size="small" @click="openRed(row)">{{ $t('flow.bill.creditNote') }}</el-button>
          <el-button v-if="['INVOICED', 'TO_INVOICE', 'PUSHED'].includes(row.status)" link type="warning" size="small" @click="openExchange(row)">{{ $t('flow.bill.replaceInvoice') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 邮包 -->
    <el-table v-show="tab === 'mail'" :data="mails" v-loading="loading" size="small" border stripe>
      <el-table-column prop="packageNo" :label="$t('flow.bill.colMailNo')" min-width="185" />
      <el-table-column prop="customerName" :label="$t('field.customer')" min-width="130" />
      <el-table-column prop="courierNo" :label="$t('flow.bill.colTrackingNo')" min-width="130" />
      <el-table-column :label="$t('flow.bill.colNotify')" width="80">
        <template #default="{ row }">{{ row.mailNotified === 1 ? t('flow.bill.notified') : t('flow.bill.notNotified') }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="90">
        <template #default="{ row }">{{ { CREATED: t('flow.bill.mailCreated'), SENT: t('flow.bill.mailSent'), SIGNED: t('flow.bill.mailSigned'), ARCHIVED: t('flow.bill.mailArchived') }[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="240" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'CREATED'" link type="primary" size="small" @click="mailAct(row, 'send')">{{ $t('flow.bill.send') }}</el-button>
          <el-button v-if="row.status === 'SENT' && !row.mailNotified" link type="warning" size="small" @click="mailAct(row, 'notify')">{{ $t('flow.bill.emailNotify') }}</el-button>
          <el-button v-if="row.status === 'SENT'" link type="primary" size="small" @click="mailAct(row, 'sign')">{{ $t('flow.bill.signReceipt') }}</el-button>
          <el-button v-if="row.status === 'SIGNED'" link type="success" size="small" @click="mailAct(row, 'archive')">{{ $t('flow.bill.archive') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 开票规则 -->
    <el-table v-show="tab === 'rule'" :data="rules" v-loading="loading" size="small" border stripe>
      <el-table-column prop="customerCode" :label="$t('flow.collab.colCustomerCode')" width="105" />
      <el-table-column prop="customerName" :label="$t('flow.collab.colCustomerName')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="invoiceType" :label="$t('field.type')" width="65" :formatter="(row, col, val) => invoiceTypeLabel(val)" />
      <el-table-column prop="limitAmount" :label="$t('flow.bill.colPerInvoiceLimit')" width="110" align="right" />
      <el-table-column prop="maxLines" :label="$t('flow.bill.colMaxLines')" width="85" align="right" />
      <el-table-column prop="splitDim" :label="$t('flow.bill.colSplitDim')" width="95" />
      <el-table-column :label="$t('flow.bill.bizConfirm')" width="90">
        <template #default="{ row }">{{ row.needBizConfirm === 1 ? t('flow.bill.needed') : t('flow.bill.notNeeded') }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link :type="row.status === 'ENABLED' ? 'warning' : 'success'" size="small"
            @click="saveRule({ ...row, status: row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED' })">{{ row.status === 'ENABLED' ? t('flow.bill.disable') : t('flow.bill.enable') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 对账单导入 -->
    <el-dialog v-model="importVisible" :title="$t('flow.bill.importDialogTitle')" width="560px" destroy-on-close>
      <el-form ref="stFormRef" :model="stForm" :rules="stRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item :label="$t('flow.collab.colCustomerCode')" prop="customerCode"><el-input v-model="stForm.customerCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.collab.colCustomerName')"><el-input v-model="stForm.customerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.amount')" prop="amount"><el-input v-model="stForm.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.taxAmount')" prop="taxAmount"><el-input v-model="stForm.taxAmount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.bill.colCurrency')"><el-input v-model="stForm.ccy" :placeholder="$t('flow.bill.ccyPlaceholder')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.bill.importUser')"><el-input v-model="stForm.importUser" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="importVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doImport">{{ $t('flow.bill.import') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="genVisible" :title="$t('flow.bill.genDialogTitle')" width="420px" destroy-on-close>
      <el-input v-model="genBillNo" :placeholder="$t('flow.bill.genBillNoPlaceholder')" />
      <div class="tip">{{ $t('flow.bill.genTip') }}</div>
      <template #footer>
        <el-button size="small" @click="genVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doGen(genBillNo)">{{ $t('flow.bill.generate') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="420px" destroy-on-close>
      <el-input v-model="inputText" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doInput">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { arBillingStatusLabel } from '@/constants/enums'
import { invoiceTypeLabel } from '@/constants/invoiceType'
import {onMounted, reactive, ref, computed} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { arFlowApi, arGapApi, store } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const tab = ref('statement')
const loading = ref(false)
const statements = ref([])
const invoices = ref([])
const mails = ref([])
const rules = ref([])
const importVisible = ref(false)
const genVisible = ref(false)
const inputVisible = ref(false)
const current = ref(null)
const stForm = reactive({ ccy: 'CNY' })
const stFormRef = ref(null)
// 客户编码是对账单归到哪家客户的唯一依据，缺失则导入的行无法核对
const stRules = computed(() => ({
  customerCode: commonRules.required(t('flow.bill.customerCodePh')),
  amount: commonRules.numberText(t('flow.request.phAmount')),
  taxAmount: commonRules.numberText(t('flow.bill.taxAmountPh')),
}))
const genBillNo = ref('')
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')

const statusTag = (s) => ({ INVOICED: 'success', VOIDED: 'danger', RED_INVOICED: 'danger', PUSHED: 'warning' }[s] || 'primary')

async function load() {
  loading.value = true
  try {
    if (tab.value === 'statement') statements.value = await arFlowApi.statements({})
    else if (tab.value === 'pre') invoices.value = await arFlowApi.preInvoices({})
    else if (tab.value === 'mail') mails.value = await arFlowApi.mailPackages({})
    else rules.value = await arFlowApi.invoiceRules()
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function doCheck(row) {
  await arFlowApi.checkStatement(row.id)
  ElMessage.success(t('flow.bill.msgChecked'))
  await load()
}

async function doAdjust(row) {
  // 41号审查 P0-1 同源整改：window.prompt -> ElMessageBox.prompt（统一样式/可取消）
  const { value: note } = await ElMessageBox.prompt(t('flow.bill.adjustPromptTitle'), t('flow.bill.adjustBoxTitle'), {
    inputPlaceholder: t('flow.bill.adjustNotePh'), inputType: 'textarea',
    confirmButtonText: t('flow.request.btnConfirm'), cancelButtonText: t('common.cancel'),
  }).catch(() => ({}))
  if (note === undefined) return
  await arFlowApi.adjustStatement(row.id, note)
  ElMessage.success(t('flow.bill.msgReconciled'))
  await load()
}

async function doGen(billNo) {
  if (!String(billNo || '').trim()) return ElMessage.warning(t('flow.bill.reconciledBillNoPh'))
  const r = await arFlowApi.generate(billNo)
  ElMessage.success(t('flow.bill.msgGeneratedPre', { v1: r.length }))
  genVisible.value = false
  tab.value = 'pre'
  await load()
}

async function biz(row, action) {
  if (action === 'bizConfirm') {
    await arFlowApi.bizConfirm(row.id, t('flow.bill.operatorBiz'))
  } else {
    // 41号审查 P0-1 同源整改：window.prompt -> ElMessageBox.prompt（必填校验）
    const { value: reason } = await ElMessageBox.prompt(t('flow.bill.rejectPromptTitle'), t('flow.bill.bizReject'), {
      inputPlaceholder: t('flow.bill.rejectReasonPh'), inputType: 'textarea',
      inputValidator: (v) => (v && v.trim() ? true : t('flow.collab.errRejectReason')),
      confirmButtonText: t('flow.bill.confirmReject'), cancelButtonText: t('common.cancel'),
    }).catch(() => ({}))
    if (!reason) return
    await arFlowApi.bizReject(row.id, reason)
  }
  ElMessage.success(t('common.success'))
  await load()
}

function openWriteback(row) {
  current.value = row
  inputKind.value = 'writeback'
  inputTitle.value = t('flow.bill.writebackPromptTitle')
  inputText.value = ''
  inputVisible.value = true
}

function openRed(row) {
  current.value = row
  inputKind.value = 'red'
  inputTitle.value = t('flow.bill.creditNoticeNoLabel')
  inputText.value = ''
  inputVisible.value = true
}

function openExchange(row) {
  current.value = row
  inputKind.value = 'exchange'
  inputTitle.value = t('flow.bill.replaceReasonLabel')
  inputText.value = ''
  inputVisible.value = true
}

async function doInput() {
  // 回写的金税发票号、红字通知单号、换票原因都是后续税务/审计凭据，留空即无从追溯
  if (!inputText.value.trim()) return ElMessage.warning(t('flow.bill.msgFillRequired', { v1: inputTitle.value }))
  const id = current.value.id
  if (inputKind.value === 'writeback') await arFlowApi.writeback(id, { invoiceNo: inputText.value })
  else if (inputKind.value === 'red') await arFlowApi.red(id, inputText.value)
  else await arFlowApi.exchange(id, inputText.value)
  ElMessage.success(t('common.success'))
  inputVisible.value = false
  await load()
}

async function simple(row, action) {
  await arFlowApi[action](row.id)
  ElMessage.success(t('common.success'))
  await load()
}

async function mailAct(row, action) {
  if (action === 'send') {
    // 41号审查 P0-1 同源整改：window.prompt -> ElMessageBox.prompt（必填校验）
    const { value: no } = await ElMessageBox.prompt(t('flow.bill.colTrackingNo'), t('flow.bill.mailDialogTitle'), {
      inputPlaceholder: t('flow.bill.trackingNoPh'),
      inputValidator: (v) => (v && v.trim() ? true : t('flow.bill.msgTrackingRequired')),
      confirmButtonText: t('flow.bill.confirmSend'), cancelButtonText: t('common.cancel'),
    }).catch(() => ({}))
    if (!no) return
    await arFlowApi.sendMail(row.id, { courierNo: no })
  } else {
    await arFlowApi[action](row.id)
  }
  ElMessage.success(t('common.success'))
  await load()
}

async function saveRule(rule) {
  await arFlowApi.saveRule(rule)
  ElMessage.success(t('flow.bill.msgSaved'))
  await load()
}

async function doImport() {
  if (!(await validateForms([stFormRef]))) return
  await arFlowApi.importStatement({ ...stForm, companyId: store.user?.companyId })
  ElMessage.success(t('flow.bill.msgImported'))
  importVisible.value = false
  await load()
}

async function doImportExcel(uploadFile) {
  const fd = new FormData()
  fd.append('file', uploadFile.raw)
  const res = await arFlowApi.importStatementExcel(fd)
  ElMessage.success(t('flow.bill.msgExcelImported', { v1: res.created }) + (res.errors?.length ? t('flow.badDebt.msgExcelFailSuffix', { v1: res.errors.length, v2: res.errors[0] }) : ''))
  await load()
}

function openEbsAdjust() {
  // 41号审查 P0-1 同源整改：window.prompt -> ElMessageBox.prompt（必填校验）
  ElMessageBox.prompt(t('flow.bill.erpAdjustPromptTitle'), t('flow.bill.erpAdjustBoxTitle'), {
    inputPlaceholder: t('flow.bill.erpAdjustFormat'),
    inputValidator: (v) => {
      if (!v || !v.trim()) return t('flow.bill.msgContentRequired')
      const parts = v.split(',').map((x) => x.trim())
      if (parts.length < 5) return t('flow.bill.msgAdjustFieldsRequired')
      return true
    },
    confirmButtonText: t('field.pushBtn'), cancelButtonText: t('common.cancel'),
  }).then(({ value: input }) => {
    if (!input) return
    const [adjustNo, customerCode, adjustType, amount, tax, stmtNo] = input.split(',').map((s) => s.trim())
    return arGapApi.pushEbsAdjust({ adjustNo, customerCode, adjustType, amount, taxAmount: tax || 0, statementNo: stmtNo || null })
      .then(async (r) => {
        ElMessage.success(t('flow.bill.msgAdjustReceived', { v1: r.status }) + (r.statementNo ? t('flow.bill.msgAutoRecheck') : ''))
        await load()
      }).catch((e) => ElMessage.error(e.message))
  }).catch(() => {})
}

function openRedPush() {
  // 41号审查 P0-1 同源整改：window.prompt -> ElMessageBox.prompt（必填校验）
  ElMessageBox.prompt(t('flow.bill.erpNegativePromptTitle'), t('flow.bill.erpNegativeBoxTitle'), {
    inputPlaceholder: t('flow.bill.erpNegativeFormat'),
    inputValidator: (v) => (v && v.includes(',') ? true : t('flow.bill.erpNegativeHint')),
    confirmButtonText: t('field.pushBtn'), cancelButtonText: t('common.cancel'),
  }).then(({ value: input }) => {
    if (!input) return
    const [memo, inv] = input.split(',').map((s) => s.trim())
    return arGapApi.pushRedInvoice({ ebsCreditMemoNo: memo, preInvoiceNo: inv }).then(async () => {
      ElMessage.success(t('flow.bill.msgNegativeReceived'))
      await load()
    }).catch((e) => ElMessage.error(e.message))
  }).catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
.tip { font-size: 12px; color: #e6a23c; margin-top: 6px; }
</style>
