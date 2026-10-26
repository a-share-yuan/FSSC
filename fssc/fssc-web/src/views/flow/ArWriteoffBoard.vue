<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.wfo.pageTitle') }}</span>
        <div class="ops">
          <el-button size="small" @click="loadAll">{{ $t('common.refresh') }}</el-button>
          <el-button type="primary" size="small" @click="openFund">{{ $t('flow.wfo.btnFundRegister') }}</el-button>
          <el-button type="warning" size="small" @click="openOffset">{{ $t('flow.baseCfg.typeOffsetWriteoff') }}</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab" @tab-change="loadAll">
      <el-tab-pane :label="$t('flow.wfo.tabFundPool')" name="funds">
        <el-table :data="funds" v-loading="loading" size="small" border stripe>
          <el-table-column prop="fundNo" :label="$t('flow.wfo.colFundNo')" min-width="170" show-overflow-tooltip />
          <el-table-column :label="$t('field.type')" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="{ RECEIPT: 'primary', NOTE: 'warning', OFFSET: 'danger' }[row.fundType] || 'info'">
                {{ FUND_TYPE_LABEL[row.fundType] || writeoffTypeLabel(row.fundType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="customerName" :label="$t('field.customer')" min-width="130" show-overflow-tooltip />
          <el-table-column prop="fundAmount" :label="$t('flow.wfo.colFundTotal')" width="110" align="right" />
          <el-table-column prop="remainAmount" :label="$t('flow.wfo.colUnwrittenBalance')" width="110" align="right" />
          <el-table-column prop="fundDate" :label="$t('flow.wfo.colDate')" width="100" />
          <el-table-column :label="$t('common.status')" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'AVAILABLE' ? 'success' : 'info'">{{ row.status === 'AVAILABLE' ? t('flow.wfo.stAvailable') : row.status === 'EXHAUSTED' ? t('flow.wfo.stExhausted') : t('flow.collab.stVoided') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="agreementNo" :label="$t('flow.wfo.colAgreement')" min-width="110" show-overflow-tooltip />
          <el-table-column :label="$t('common.operation')" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'AVAILABLE' && row.fundType !== 'OFFSET'" link type="primary" size="small" @click="openManual(row)">{{ $t('flow.wfo.btnManualMatch') }}</el-button>
              <el-button v-if="row.status === 'AVAILABLE' && row.fundType !== 'OFFSET'" link type="success" size="small" @click="doAuto(row)">{{ $t('flow.wfo.btnAutoFifo') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="$t('flow.wfo.tabPendingInvoices')" name="invoices">
        <div class="ops" style="margin-bottom: 8px">
          <CustomerSelect v-model="invQuery.customerId" value-key="id" style="width: 200px" clearable :placeholder="$t('flow.wfo.phPickCustomer')" @change="loadInvoices" />
          <el-input v-model="invQuery.keyword" size="small" style="width: 180px" :placeholder="$t('flow.badDebt.colArDocNo')" clearable @keyup.enter="loadInvoices" />
          <el-button size="small" type="primary" @click="loadInvoices">{{ $t('common.search') }}</el-button>
        </div>
        <el-table :data="invoices" v-loading="invLoading" size="small" border stripe>
          <el-table-column prop="billNo" :label="$t('flow.badDebt.colArDocNo')" min-width="170" show-overflow-tooltip />
          <el-table-column prop="billDate" :label="$t('flow.wfo.colDocDate')" width="105" />
          <el-table-column prop="dueDate" :label="$t('flow.note.colDueDate')" width="105" />
          <el-table-column prop="amount" :label="$t('flow.wfo.colReceivableAmount')" width="110" align="right" />
          <el-table-column prop="receivedAmount" :label="$t('flow.wfo.colReceivedAmount')" width="110" align="right" />
          <el-table-column prop="remainAmount" :label="$t('flow.wfo.colReceivableBalance')" width="110" align="right" />
          <el-table-column :label="$t('common.status')" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'OVERDUE' ? 'danger' : 'warning'">{{ { NONE: t('flow.wfo.stUnsettled'), DONE: t('flow.wfo.stSettled'), OVERDUE: t('flow.wfo.stOverdue') }[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="$t('flow.wfo.tabRecords')" name="records">
        <div class="ops" style="margin-bottom: 8px">
          <el-select v-model="recQuery.writeoffType" size="small" style="width: 120px" :placeholder="$t('flow.report.colWriteOffType')" clearable @change="loadRecords">
            <el-option v-for="(l, t) in WRITEOFF_TYPE" :key="t" :label="l" :value="t" />
          </el-select>
          <el-select v-model="recQuery.status" size="small" style="width: 110px" :placeholder="$t('common.status')" clearable @change="loadRecords">
            <el-option :label="$t('flow.wfo.stDone')" value="DONE" />
            <el-option :label="$t('flow.wfo.stFailed')" value="FAILED" />
            <el-option :label="$t('flow.wfo.stPending')" value="PENDING" />
          </el-select>
          <el-input v-model="recQuery.keyword" size="small" style="width: 180px" :placeholder="$t('flow.wfo.phRecordSearch')" clearable @keyup.enter="loadRecords" />
          <el-button size="small" type="primary" @click="loadRecords">{{ $t('common.search') }}</el-button>
        </div>
        <el-table :data="records" v-loading="recLoading" size="small" border stripe>
          <el-table-column prop="recordNo" :label="$t('flow.wfo.colRecordNo')" min-width="170" show-overflow-tooltip />
          <el-table-column :label="$t('field.type')" width="90">
            <template #default="{ row }">{{ writeoffTypeLabel(row.writeoffType) }}</template>
          </el-table-column>
          <el-table-column prop="customerName" :label="$t('field.customer')" min-width="120" show-overflow-tooltip />
          <el-table-column prop="invoiceNos" :label="$t('flow.wfo.colArDocWrittenOff')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="writeoffAmount" :label="$t('flow.report.colBadDebtWriteOff')" width="110" align="right" />
          <el-table-column :label="$t('flow.wfo.colMethod')" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.autoFlag === 1 ? 'success' : 'primary'">{{ row.autoFlag === 1 ? t('flow.wfo.tagAuto') : t('flow.wfo.tagManual') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="receiptNo" :label="$t('flow.wfo.colReceiptNo')" min-width="150" show-overflow-tooltip />
          <el-table-column prop="voucherNo" :label="$t('flow.wfo.colVoucherNo')" min-width="130" show-overflow-tooltip />
          <el-table-column :label="$t('common.status')" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="{ DONE: 'success', FAILED: 'danger', PENDING: 'warning' }[row.status] || 'info'">{{ { DONE: t('flow.wfo.stDone'), FAILED: t('flow.wfo.stFailed'), PENDING: t('flow.wfo.stPending') }[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="fundVisible" :title="$t('flow.wfo.dlgFundRegisterTitle')" width="560px" destroy-on-close>
      <el-form ref="fundFormRef" :model="fundForm" :rules="fundRules" label-width="100px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item :label="$t('flow.wfo.lblFundType')" prop="fundType">
            <el-select v-model="fundForm.fundType" style="width: 100%">
              <el-option :label="$t('flow.wfo.fundTypeReceipt')" value="RECEIPT" />
              <el-option :label="$t('flow.wfo.fundTypeNote')" value="NOTE" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.wfo.colFundNo')" prop="fundNo"><el-input v-model="fundForm.fundNo" :placeholder="$t('flow.wfo.phFundNo')" maxlength="48" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.customer')" prop="customerId">
            <CustomerSelect v-model="fundForm.customerId" value-key="id" :placeholder="$t('flow.wfo.phPickCustomer')" @change="onFundCustomer" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.wfo.lblFundAmount')" prop="fundAmount"><el-input v-model="fundForm.fundAmount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.wfo.lblFundDate')"><el-date-picker v-model="fundForm.fundDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.wfo.lblBankSerial')"><el-input v-model="fundForm.bankSerial" maxlength="64" /></el-form-item></el-col>
          <el-col v-if="fundForm.fundType === 'NOTE'" :span="12"><el-form-item :label="$t('flow.wfo.lblNoteId')" prop="noteId">
            <el-select v-model="fundForm.noteId" filterable style="width: 100%" :placeholder="$t('flow.wfo.phPickNote')">
              <el-option v-for="n in noteOptions" :key="n.id" :label="`${n.noteNo}（${n.customerName || n.drawer || ''}）`" :value="n.id" />
            </el-select>
          </el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="fundVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doRegisterFund">{{ $t('flow.wfo.btnRegister') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="manualVisible" :title="$t('flow.wfo.dlgManualMatchTitle', { v1: current?.fundNo || '', v2: current?.remainAmount || 0 })" width="720px" destroy-on-close>
      <el-table :data="manualInvoices" v-loading="manualLoading" size="small" border max-height="360">
        <el-table-column prop="billNo" :label="$t('flow.badDebt.colArDocNo')" min-width="160" />
        <el-table-column prop="dueDate" :label="$t('flow.note.colDueDate')" width="105" />
        <el-table-column prop="remainAmount" :label="$t('flow.wfo.colReceivableBalance')" width="105" align="right" />
        <el-table-column :label="$t('flow.wfo.colMatchAmount')" width="180">
          <template #default="{ row }">
            <el-input v-model="row._matchAmount" size="small" :placeholder="$t('flow.wfo.phMatchAmountHint')" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button size="small" @click="manualVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doManual">{{ $t('flow.badDebt.btnWriteoffConfirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="offsetVisible" :title="$t('flow.wfo.dlgOffsetTitle')" width="520px" destroy-on-close>
      <el-form ref="offsetFormRef" :model="offsetForm" :rules="offsetRules" label-width="110px" size="small">
        <el-form-item :label="$t('field.customer')" prop="customerId">
          <CustomerSelect v-model="offsetForm.customerId" value-key="id" :placeholder="$t('flow.wfo.phPickCustomer')" @change="onOffsetCustomer" />
        </el-form-item>
        <el-form-item :label="$t('flow.wfo.lblOffsetAmount')" prop="amount"><el-input v-model="offsetForm.amount" :placeholder="$t('flow.wfo.phOffsetAmountHint')" /></el-form-item>
        <el-form-item :label="$t('flow.wfo.lblAgreementNo')"><el-input v-model="offsetForm.agreementNo" :placeholder="$t('flow.wfo.phAgreementAttachNo')" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="offsetVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doOffset">{{ $t('flow.baseCfg.typeOffsetWriteoff') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { WRITEOFF_TYPE, writeoffTypeLabel } from '@/constants/enums'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { arGapApi } from '../../api'
import CustomerSelect from '@/components/common/CustomerSelect.vue'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()


const tab = ref('funds')
const loading = ref(false)
const invLoading = ref(false)
const recLoading = ref(false)
const manualLoading = ref(false)

const funds = ref([])
const invoices = ref([])
const records = ref([])
const manualInvoices = ref([])

const invQuery = reactive({ customerId: '', keyword: '' })
const recQuery = reactive({ writeoffType: '', status: '', keyword: '' })

const fundVisible = ref(false)
const manualVisible = ref(false)
const offsetVisible = ref(false)
const current = ref(null)
const noteOptions = ref([])
// R5-D-104：资金池类型名称表（含 RECEIVABLE 历史/外部来源值），未收录值回落 enums 名称表
const FUND_TYPE_LABEL = computed(() => ({ RECEIPT: t('flow.wfo.fundTypeReceipt'), NOTE: t('flow.wfo.fundTypeNote'), OFFSET: t('flow.wfo.fundTypeOffset'), RECEIVABLE: t('flow.wfo.fundTypeReceipt'), PAYABLE: t('flow.wfo.fundTypePayment') }))
const fundForm = reactive({ fundType: 'RECEIPT' })
const offsetForm = reactive({})
const fundFormRef = ref(null)
const offsetFormRef = ref(null)
// 票据来源的资金必须回指已登记票据，否则核销完成后无从追溯票面
const fundRules = computed(() => ({
  fundType: commonRules.select(t('flow.wfo.msgPickFundType')),
  fundNo: commonRules.required(t('flow.wfo.msgInputFundNo')),
  customerId: commonRules.required(t('flow.wfo.msgInputCustomerId')),
  fundAmount: commonRules.amountText(t('flow.wfo.msgInputFundAmount')),
  noteId: fundForm.fundType === 'NOTE' ? commonRules.required(t('flow.wfo.msgNeedNoteId')) : [],
}))
const offsetRules = computed(() => ({
  customerId: commonRules.required(t('flow.wfo.msgInputOffsetCustomerId')),
  amount: commonRules.amountText(t('flow.wfo.msgInputOffsetAmount')),
}))

async function loadFunds() {
  loading.value = true
  try { funds.value = await arGapApi.funds({}) } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadInvoices() {
  invLoading.value = true
  try {
    invoices.value = await arGapApi.pendingInvoices({
      customerId: invQuery.customerId || undefined,
      keyword: invQuery.keyword || undefined,
    })
  } catch (e) { ElMessage.error(e.message) } finally { invLoading.value = false }
}

async function loadRecords() {
  recLoading.value = true
  try {
    records.value = await arGapApi.writeoffRecords({
      writeoffType: recQuery.writeoffType || undefined,
      status: recQuery.status || undefined,
      keyword: recQuery.keyword || undefined,
    })
  } catch (e) { ElMessage.error(e.message) } finally { recLoading.value = false }
}

function loadAll() {
  if (tab.value === 'funds') loadFunds()
  else if (tab.value === 'invoices') loadInvoices()
  else loadRecords()
}

function openFund() {
  Object.keys(fundForm).forEach((k) => delete fundForm[k])
  fundForm.fundType = 'RECEIPT'
  noteOptions.value = []
  fundVisible.value = true
  arGapApi.notes({}).then((list) => { noteOptions.value = list || [] }).catch(() => { noteOptions.value = [] })
}

function onFundCustomer(c) {
  fundForm.customerName = c?.customerName || ''
}

function onOffsetCustomer(c) {
  offsetForm.customerName = c?.customerName || ''
}

async function doRegisterFund() {
  if (!(await validateForms([fundFormRef]))) return
  await arGapApi.registerFund({ ...fundForm, noteId: fundForm.fundType === 'NOTE' ? fundForm.noteId : undefined })
  ElMessage.success(t('flow.wfo.msgFundRegistered'))
  fundVisible.value = false
  await loadFunds()
}

async function doAuto(row) {
  await arGapApi.autoMatch(row.id)
  ElMessage.success(t('flow.wfo.msgAutoDone'))
  await loadFunds()
}

async function openManual(row) {
  current.value = row
  manualLoading.value = true
  manualVisible.value = true
  try {
    manualInvoices.value = (await arGapApi.pendingInvoices({ customerId: row.customerId }))
      .map((r) => ({ ...r, _matchAmount: '' }))
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    manualLoading.value = false
  }
}

async function doManual() {
  const lines = manualInvoices.value
    .filter((r) => r._matchAmount && Number(r._matchAmount) > 0)
    .map((r) => ({ receivableId: r.id, offsetAmount: r._matchAmount }))
  if (!lines.length) return ElMessage.warning(t('flow.wfo.msgNeedOneAmount'))
  await arGapApi.manualMatch(current.value.id, lines)
  ElMessage.success(t('flow.wfo.msgManualDone'))
  manualVisible.value = false
  await loadFunds()
}

function openOffset() {
  Object.keys(offsetForm).forEach((k) => delete offsetForm[k])
  offsetVisible.value = true
}

async function doOffset() {
  if (!(await validateForms([offsetFormRef]))) return
  await arGapApi.offsetWriteoff({ ...offsetForm })
  ElMessage.success(t('flow.wfo.msgOffsetDone'))
  offsetVisible.value = false
  await loadFunds()
}

onMounted(loadFunds)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
</style>
