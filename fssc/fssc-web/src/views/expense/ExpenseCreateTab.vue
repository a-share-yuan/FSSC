<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <el-card :header="$t('field.billType')">
      <el-radio-group v-model="form.bill.billType">
        <el-radio-button v-for="t in typeOptions" :key="t.label" :value="t.billType" :disabled="false" @click="pickType(t)">
          {{ t.label }}
        </el-radio-button>
      </el-radio-group>
    </el-card>

    <el-row :gutter="16" style="margin-top: 12px">
      <el-col :xs="24" :md="14">
        <el-card :header="$t('expense.create.secBasicInfo')">
          <el-form ref="formRef" :rules="formRules" :model="form.bill" label-width="90px" size="default">
            <el-form-item :label="$t('expense.common.colTitle')" prop="title">
              <el-input v-model="form.bill.title" :placeholder="$t('expense.createTab.titleApply', { v1: currentType.label })" />
            </el-form-item>
            <el-form-item :label="$t('expense.common.colApplyDate')" prop="applyDate">
              <el-date-picker v-model="form.bill.applyDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item :label="$t('expense.common.colCurrency')" prop="currencyCode">
              <el-select v-model="form.bill.currencyCode" style="width: 120px">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />



              </el-select>
            </el-form-item>
<el-form-item v-if="currentType.pay" :label="$t('expense.create.colReceivingParty')">
              <el-select v-model="form.bill.payeeName" filterable allow-create clearable style="width: 100%" :placeholder="$t('expense.create.phPickPayeeInput')">
                <el-option v-for="p in payees" :key="p.id" :label="p.payeeName" :value="p.payeeName" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="currentType.pay" :label="$t('expense.claim.colPayeeType')" prop="payTarget">
              <el-select v-model="form.bill.payTarget" style="width: 100%" :placeholder="$t('expense.common.phPickPayeeType')">
<el-option v-for="o in payeeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />




              </el-select>
            </el-form-item>
            <el-form-item v-if="currentType.pay" :label="$t('expense.common.colPayeeAccount')" prop="payeeAccountNo">
              <el-input v-model="form.bill.payeeAccountNo" :placeholder="$t('expense.travelClaim.phPayeeAccountRequired')" maxlength="64" />
            </el-form-item>
            <el-form-item :label="$t('common.remark')">
              <el-input v-model="form.bill.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card :header="$t('expense.create.secExpenseLines')" style="margin-top: 12px">
          <el-table :data="form.lines" size="small" border :empty-text="$t('common.empty')" v-loading="loading" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="45" />
            <el-table-column :label="$t('expense.create.colExpenseItem')" width="160">
              <template #default="{ row }">
                <el-select v-model="row.itemId" filterable style="width: 100%" @change="(id) => onItemChange(row, id)">
                  <el-option v-for="it in items" :key="it.id" :label="it.itemName" :value="it.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$t('expense.travelClaim.colExpenseDate')" width="140">
              <template #default="{ row }">
                <el-date-picker v-model="row.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column :label="$t('field.amount')" width="130">
              <template #default="{ row }">
                <el-input-number v-model="row.amount" :min="0" :precision="2" style="width: 100%" @change="recalcLine(row)" />
              </template>
            </el-table-column>
            <el-table-column :label="$t('expense.common.colTaxRate')" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" style="width: 100%" @change="recalcLine(row)" />
              </template>
            </el-table-column>
            <el-table-column :label="$t('field.taxAmount')" width="90">
              <template #default="{ row }">{{ fmt(row.taxAmount) }}</template>
            </el-table-column>
            <el-table-column :label="$t('expense.create.colInvoiceNo')" width="150">
              <template #default="{ row }">{{ invOf(row)?.invoiceNo || '—' }}</template>
            </el-table-column>
            <el-table-column :label="$t('field.invoiceDate')" width="110">
              <template #default="{ row }">{{ invOf(row)?.invoiceDate || '—' }}</template>
            </el-table-column>
            <el-table-column :label="$t('expense.create.colSeller')" min-width="130">
              <template #default="{ row }">{{ invOf(row)?.sellerName || '—' }}</template>
            </el-table-column>
            <el-table-column :label="$t('expense.common.colBudgetItem')" width="120">
              <template #default="{ row }">
                <el-select v-model="row.budgetItemId" clearable filterable style="width: 100%">
                  <el-option v-if="row.budgetItemId != null && !budgets.some((x) => String(x.id) === String(row.budgetItemId))" :label="budgetUnseenLabel(row.budgetItemId)" :value="row.budgetItemId" />
                  <el-option v-for="b in budgets" :key="b.id" :label="budgetLabel(items, [], [], b)" :value="b.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$t('expense.claim.colStdOver')" min-width="120">
              <template #default="{ row }">
                <span v-if="row.stdAmount != null">{{ $t('expense.create.txtStdLimit', { v1: fmt(row.stdAmount) }) }}</span>
                <el-tag v-if="row.overStd === 1" type="danger" size="small">{{ $t('expense.claim.stOverLimit') }}</el-tag>
                <span v-else-if="row.stdAmount != null" type="success">{{ $t('expense.claim.stCompliant') }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('common.operation')" width="60">
              <template #default="{ $index }">
                <el-button type="danger" link @click="removeLine($index)">{{ $t('expense.common.btnDeleteShort') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 8px" @click="form.lines.push(newLine())">{{ $t('expense.create.btnAddRow') }}</el-button>
          <div style="margin-top: 8px">{{ $t('expense.create.labTotal') }}<b>{{ fmt(sumAmount()) }}</b> {{ $t('expense.create.txtGrossWithTax', { v1: fmt(sumTax()) }) }}</div>
          <div style="margin-top: 4px; color: var(--app-text-secondary, #606266)">{{ $t('expense.create.txtAmountInWords', { v1: form.bill.amountCn || t('expense.createTab.txtAmountPending') }) }}</div>
        </el-card>

        <el-card v-if="currentType.travel" :header="$t('expense.create.colTravelLines')" style="margin-top: 12px">
          <el-table :data="form.travels" size="small" border :empty-text="$t('common.empty')" v-loading="loading" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="45" />
            <el-table-column :label="$t('expense.travelClaim.colPassenger')" width="120">
              <template #default="{ row }"><el-input v-model="row.travelerName" :placeholder="$t('expense.create.colPassengerName')" /></template>
            </el-table-column>
            <el-table-column :label="$t('expense.create.colFromCity')" width="140">
              <template #default="{ row }">
                <el-select v-model="row.fromCityId" filterable style="width: 100%">
                  <el-option v-for="c in cities" :key="c.id" :label="c.cityName" :value="c.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$t('expense.create.colToCity')" width="140">
              <template #default="{ row }">
                <el-select v-model="row.toCityId" filterable style="width: 100%">
                  <el-option v-for="c in cities" :key="c.id" :label="c.cityName" :value="c.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$t('expense.create.colDateRange')" width="220">
              <template #default="{ row }">
                <el-date-picker v-model="row.beginDate" type="date" value-format="YYYY-MM-DD" style="width: 105px" />
                <el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" style="width: 105px" />
              </template>
            </el-table-column>
            <el-table-column :label="$t('expense.travelClaim.colTrafficMode')" width="120">
              <template #default="{ row }">
                <el-select v-model="row.trafficType" style="width: 100%">
                  <el-option v-for="o in trafficOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$t('expense.travelClaim.colHotel')" width="110">
              <template #default="{ row }"><el-input-number v-model="row.hotelAmount" :min="0" :precision="2" style="width: 100%" /></template>
            </el-table-column>
            <el-table-column :label="$t('common.operation')" width="60">
              <template #default="{ $index }"><el-button type="danger" link @click="removeTravel($index)">{{ $t('expense.common.btnDeleteShort') }}</el-button></template>
            </el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 8px" @click="form.travels.push(newTravel())">{{ $t('expense.create.btnAddRow') }}</el-button>
        </el-card>

        <el-card :header="$t('expense.create.tabInvoiceAttach')" style="margin-top: 12px">
          <div class="attach-row">
            <UploadButton :label="$t('expense.create.btnUploadFiles')" :accept="ATTACH_ACCEPT" biz-type="INVOICE" :biz-id="form.bill.id || null" zone="TEMP" parse @done="onAttachUploaded" />
            <span v-if="form.attaches.length" class="attach-name">
              {{ $t('expense.createTab.txtUploaded', { v1: form.attaches.map((a) => a.fileName).join('、') }) }}<el-button type="primary" link @click="previewAttach">{{ $t('expense.create.btnPreview') }}</el-button>
              <el-button type="danger" link @click="clearAttaches()">{{ $t('expense.create.btnClear') }}</el-button>
            </span>
          </div>

          <div style="margin-top: 12px">
            <div style="font-weight: 600; margin-bottom: 6px">{{ $t('expense.create.hintParsedInvoices') }}</div>
            <el-table :data="form.invoices" size="small" border :empty-text="$t('expense.create.emptyInvoices')">
              <el-table-column :label="$t('expense.create.colEntry')" width="52">
                <template #default="{ row }">
                  <el-checkbox v-model="row.checked" @change="toggleInvoice(row)" />
                </template>
              </el-table-column>
              <el-table-column :label="$t('expense.create.colInvoiceNo')" min-width="160">
                <template #default="{ row }"><el-input v-model="row.invoiceNo" size="small" :placeholder="$t('expense.create.colInvoiceNo')" maxlength="255" /></template>
              </el-table-column>
              <el-table-column :label="$t('field.invoiceDate')" width="130">
                <template #default="{ row }"><el-date-picker v-model="row.invoiceDate" type="date" value-format="YYYY-MM-DD" size="small" style="width:100%" /></template>
              </el-table-column>
              <el-table-column :label="$t('expense.create.colAmountGross')" width="120">
                <template #default="{ row }"><el-input-number v-model="row.amount" :min="0" :precision="2" size="small" style="width:100%" @change="recalcInvoice(row); syncInvoiceLine(row)" /></template>
              </el-table-column>
              <el-table-column :label="$t('expense.common.colTaxRate')" width="110">
                <template #default="{ row }"><el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" size="small" style="width:100%" @change="recalcInvoice(row); syncInvoiceLine(row)" /></template>
              </el-table-column>
              <el-table-column :label="$t('field.taxAmount')" width="90">
                <template #default="{ row }">{{ fmt(row.taxAmount) }}</template>
              </el-table-column>
              <el-table-column :label="$t('expense.common.colNetAmount')" width="90">
                <template #default="{ row }">{{ fmt(row.netAmount) }}</template>
              </el-table-column>
              <el-table-column :label="$t('expense.create.colSeller')" min-width="130">
                <template #default="{ row }"><el-input v-model="row.sellerName" size="small" :placeholder="$t('expense.create.colSellerName')" @change="syncInvoiceLine(row)" /></template>
              </el-table-column>
              <el-table-column :label="$t('expense.create.colGoodsService')" min-width="140">
                <template #default="{ row }"><el-input v-model="row.primaryItemName" size="small" :placeholder="$t('expense.create.hintParsedEditable')" @change="applyItemMatch(row)" /></template>
              </el-table-column>
              <el-table-column :label="$t('field.type')" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.invoiceType" size="small" style="width: 100%" @change="syncInvoiceLine(row)">
                    <el-option v-for="t in INVOICE_TYPES" :key="t.code" :label="t.label" :value="t.code" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column :label="$t('expense.create.colSource')" width="120">
                <template #default="{ row }">
                  <span v-if="row.parsed" class="attach-name">{{ $t('expense.create.stParsed') }}</span>
                  <el-tag v-else type="warning" size="small">{{ $t('expense.create.optManual') }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="$t('common.operation')" width="52">
                <template #default="{ $index }"><el-button type="danger" link @click="removeInvoice($index)">{{ $t('expense.common.btnDeleteShort') }}</el-button></template>
              </el-table-column>
            </el-table>
            <el-button size="small" style="margin-top: 8px" @click="form.invoices.push(newInvoice())">{{ $t('expense.create.btnAddInvoiceLine') }}</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="10">
        <el-card :header="$t('expense.create.secBudgetPreview')">
          <el-alert v-if="budgetWarn.length" type="warning" :closable="false" show-icon :title="$t('expense.create.tipBudgetOverrunLevels')" style="margin-bottom: 8px" />
          <el-table :data="budgetPreview" size="small" border :empty-text="$t('common.empty')" v-loading="loading">
            <el-table-column prop="label" :label="$t('expense.common.colBudgetItem')" min-width="120" show-overflow-tooltip sortable />
            <el-table-column :label="$t('expense.create.colThisRequest')" width="110">
              <template #default="{ row }">{{ fmt(row.used) }}</template>
            </el-table-column>
            <el-table-column :label="$t('expense.create.colAvailableBalance')" width="110">
              <template #default="{ row }">{{ row.unknown ? '—' : fmt(row.available) }}</template>
            </el-table-column>
            <el-table-column :label="$t('common.status')" width="80">
              <template #default="{ row }">
                <el-tag :type="row.unknown ? 'warning' : row.over ? 'danger' : 'success'" size="small">
                  {{ row.unknown ? t('expense.create.stQuotaUnknown') : row.over ? t('expense.create.stOverBudget') : t('expense.create.stWithinBudget') }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card v-if="currentType.claim" :header="$t('expense.create.secLoanWriteOff')" style="margin-top: 12px">
          <el-button size="small" @click="openLoanDialog">{{ $t('expense.create.btnPickLoanOffset') }}</el-button>
          <el-table :data="form.loanOffsets" size="small" border style="margin-top: 8px" :empty-text="$t('common.empty')" v-loading="loading">
            <el-table-column prop="loanBillNo" :label="$t('expense.create.colLoanBillNo')" width="140" sortable />
            <el-table-column :label="$t('expense.create.colOffsetAmount')" width="120">
              <template #default="{ row }"><el-input-number v-model="row.offsetAmount" :min="0" :precision="2" style="width: 100%" /></template>
            </el-table-column>
            <el-table-column :label="$t('common.operation')" width="60">
              <template #default="{ $index }"><el-button type="danger" link @click="removeLoanOffset($index)">{{ $t('expense.common.btnDeleteShort') }}</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card v-if="currentType.claim" :header="$t('expense.create.secLinkRequisition')" style="margin-top: 12px">
          <el-button size="small" @click="openApplyDialog">{{ $t('expense.create.btnPickRequisition') }}</el-button>
          <el-table :data="form.relations" size="small" border style="margin-top: 8px" :empty-text="$t('common.empty')" v-loading="loading">
            <el-table-column prop="sourceBillNo" :label="$t('expense.create.colApplyNo')" width="140" sortable />
            <el-table-column prop="useAmount" :label="$t('expense.create.colOffsettable')" width="120" sortable />
            <el-table-column :label="$t('common.operation')" width="60">
              <template #default="{ $index }"><el-button type="danger" link @click="removeRelation($index)">{{ $t('expense.common.btnDeleteShort') }}</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card :header="$t('common.operation')" style="margin-top: 12px">
          <el-button @click="saveDraft">{{ $t('expense.common.btnSaveDraft') }}</el-button>
          <el-button type="primary" @click="submit">{{ $t('expense.create.btnSubmitApproval') }}</el-button>
          <el-button v-if="form.bill.id" link @click="resetForm">{{ $t('expense.create.btnNewBlank') }}</el-button>
        </el-card>
      </el-col>
    </el-row>

    <!-- 借款单选择（勾选+可改金额+【确定】） -->
    <CandidatePickerDialog v-model="loanDialog" :title="$t('expense.create.dlgPickLoanOffset')"
                          :rows="loanCandidates" :loading="loading" max-field="availableBalance"
                          extra-prop="title" :extra-label="$t('expense.common.colTitle')"
                          :no-label="$t('expense.create.colLoanBillNo')"
                          :amount-label="$t('expense.create.colLoanAmt')"
                          max-label="可冲抵余额"
                          input-label="冲抵金额" :picked-ids="loanOffsetIds"
                          @confirm="confirmLoanPicks" />

    <!-- 事前申请选择（关联金额可改，支持部分关联） -->
    <CandidatePickerDialog v-model="applyDialog" :title="$t('expense.create.dlgPickRequisition')"
                          :rows="applyCandidates" :loading="loading" max-field="totalAmount"
                          :show-max-column="false"
                          extra-prop="title" :extra-label="$t('expense.common.colTitle')"
                          :no-label="$t('expense.create.colApplyNo')"
                          :amount-label="$t('expense.create.colApplyAmount')"
                          input-label="本次关联金额" :picked-ids="relationIds"
                          @confirm="confirmApplyPicks" />
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
const { rules: formRules } = useFormRules('EXPENSE')
import { onMounted, reactive, ref, computed, inject } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billApi, mdApi, detailApi, masterCfgApi, store, userApi } from '../../api'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import { hasPerm } from '../../utils/perm'
import UploadButton from '../../components/UploadButton.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import CandidatePickerDialog from '../../components/bill/CandidatePickerDialog.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { INVOICE_TYPES } from '@/constants/invoiceType'
import { useAutoFill } from '@/composables/useAutoFill'
import { useFormRules } from '@/composables/useFormRules'
import { formatMoney, budgetLabel, budgetUnseenLabel } from '@/utils/format'
import { ATTACH_ACCEPT } from '@/utils/attachKind'
import { pickBudgetLine } from '@/utils/budget'
import { splitTaxInclusive } from '@/utils/tax'
import { invoiceOutcome } from '@/utils/invoiceOutcome'
import { fetchAllPages } from '@/utils/paging'
import { useI18n } from 'vue-i18n'
import { useBaseDict, TRAFFIC_LOCAL, TRAFFIC_I18N, PAYEE_TYPE_LOCAL, PAYEE_TYPE_I18N } from '@/composables/useBaseDict'
import { useCurrency } from '@/composables/useCurrency'

const { t } = useI18n()

// 交通方式（基础数据办结：字典定值域 + i18n 定文案）
const trafficDict = useBaseDict('traffic_mode', TRAFFIC_LOCAL, TRAFFIC_I18N)
const trafficOptions = trafficDict.options
const { autofill, saveFormHistory } = useAutoFill()
const formRef = ref(null)
const guard = useSubmitGuard(formRef)

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const emit = defineEmits(['saved', 'submitted'])
const attachViewer = inject('attachViewer')

// 预填日期与后端写入数据库时区对齐——toISOString() 是 UTC 串，东八区恒差 −8h、跨零点错成昨天，
// 故按本地时钟取 YYYY-MM-DD（与 composables/useBill.js today() 同一规则，不引日期库）。
function todayLocal() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const selRows = ref([])
const loading = ref(false)
const form = reactive({
  bill: { billType: 'CLAIM', billSubType: '', title: '', companyId: store.user?.companyId || null, deptId: store.user?.deptId || null, applyDate: todayLocal(), currencyCode: 'CNY', payeeName: '', remark: '' },
  lines: [],
  travels: [],
  shares: [],
  loanOffsets: [],
  relations: [],
  attaches: [],
  invoices: [],
})
const items = ref([])
const budgets = ref([])
const cities = ref([])
const payees = ref([])
const users = ref([])
const loanDialog = ref(false)
const applyDialog = ref(false)
const loanCandidates = ref([])
const applyCandidates = ref([])

const typeOptions = ref([
  { label: t('expense.create.optEmpClaim'), billType: 'CLAIM', sub: '', travel: false, claim: true, pay: false },
  { label: t('expense.create.optTravelTrainClaim'), billType: 'CLAIM', sub: 'TRAVEL', travel: true, claim: true, pay: false },
  { label: t('expense.create.optPrepay'), billType: 'PAY', sub: 'PREPAY', travel: false, claim: false, pay: true },
  { label: t('expense.create.optCorporateSettle'), billType: 'PAY', sub: 'SETTLE', travel: false, claim: false, pay: true },
  { label: t('expense.create.optAgencyPay'), billType: 'PAY', sub: 'AGENCY', travel: false, claim: false, pay: true },
  { label: t('expense.create.optAirSettle'), billType: 'PAY', sub: 'AIR', travel: false, claim: false, pay: true },
])
const currentType = computed(() => typeOptions.value.find((t) => t.billType === form.bill.billType && t.sub === form.bill.billSubType) || typeOptions.value[0])

function pickType(t) {
  form.bill.billType = t.billType
  form.bill.billSubType = t.sub
  if (!t.travel) form.travels = []
}

function newLine() {
  return { itemId: null, itemName: '', expenseDate: todayLocal(), amount: 0, taxRate: 0, taxAmount: 0, netAmount: 0, budgetItemId: null, stdAmount: null, overStd: 0, overReason: '', remark: '', invoice: null }
}
function newTravel() {
  // 同 useBill.js：出差人员默认本人，不要空值＋全员名单首位是平台管理员
  return { travelerName: store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '', fromCityId: null, toCityId: null, beginDate: '', endDate: '', days: 0, trafficType: 'RAIL', trafficClass: '', hotelAmount: 0, trafficAmount: 0, allowanceAmount: 0, otherAmount: 0, totalAmount: 0, overStd: 0, remark: '' }
}
function fmt(v) { return formatMoney(v, '0.00') }
function recalcLine(row) {
  // 价内税规则：含税金额 ÷ (1+税率) 反算不含税，税额 = 含税 − 不含税（与票面/税务规则一致）
  const r = splitTaxInclusive(row.amount, row.taxRate)
  row.taxAmount = r.taxAmount
  row.netAmount = r.netAmount
}
function onItemChange(row, id) {
  const it = items.value.find((x) => x.id === id)
  row.itemName = it ? it.itemName : ''
  // FINAL-01: 按费用类型自动带出映射的预算科目；手选过则不覆盖（主体规则见 utils/budget.js）
  if (id != null && row.budgetItemId == null) {
    const bid = pickBudgetLine({
      pool: budgets.value, itemId: id, map: budgetMap.value,
      companyId: form.bill.companyId, deptId: form.bill.deptId,
    })
    if (bid != null) row.budgetItemId = bid
  }
}
// FINAL-01: 费用类型→预算科目映射缓存(itemId→budgetItemId)
const budgetMap = ref({})
// 发票清单（上传解析 + 手动录入）→ 生成费用明细行
let invoiceSeq = 0
function newInvoice() {
  return { uid: ++invoiceSeq, checked: false, lineRef: null, fileId: null, invoiceNo: '', invoiceDate: '', amount: 0, netAmount: 0, taxAmount: 0, taxRate: 0, sellerName: '', sellerTaxNo: '', invoiceType: '', primaryItemName: '', parsed: false, sourceName: '' }
}
function recalcInvoice(inc) {
  // 与明细行同一价内税规则，避免"发票清单行 / 明细行"两套算法
  const r = splitTaxInclusive(inc.amount, inc.taxRate)
  inc.taxAmount = r.taxAmount
  inc.netAmount = r.netAmount
}
// 按发票首条「货物或应税劳务、服务名称」智能匹配费用类型：命中才填，未命中留空待填
async function applyItemMatch(inc) {
  if (!inc || !inc.lineRef) return
  const name = (inc.primaryItemName || '').trim()
  if (!name) return
  try {
    const m = await mdApi.invoiceRuleMatch(name)
    if (m && m.expenseItemId != null && inc.lineRef) {
      inc.lineRef.itemId = m.expenseItemId
      onItemChange(inc.lineRef, m.expenseItemId)
    }
  } catch (e) { /* 匹配失败静默，费用类型留空待填 */ }
}
function newLineFromInvoice(inc) {
  const line = newLine()
  line.expenseDate = inc.invoiceDate || todayLocal()
  line.amount = Number(inc.amount || 0)
  line.taxRate = Number(inc.taxRate || 0)
  recalcLine(line)
  line.remark = inc.invoiceNo ? t('expense.create.optInvoiceLine', { v1: inc.invoiceNo }) : ''
  line.invoice = { invoiceNo: inc.invoiceNo, invoiceDate: inc.invoiceDate, sellerName: inc.sellerName, sellerTaxNo: inc.sellerTaxNo, invoiceType: inc.invoiceType, primaryItemName: inc.primaryItemName || '', fileId: inc.fileId, amount: Number(inc.amount || 0), netAmount: line.netAmount, taxAmount: line.taxAmount }
  // 费用类型由发票货名智能匹配决定：命中才填，未命中留空待填（不再默认职工福利）
  applyItemMatch(inc)
  return line
}
function syncInvoiceLine(inc) {
  if (!inc.checked || !inc.lineRef) return
  const line = inc.lineRef
  line.amount = Number(inc.amount || 0)
  line.taxRate = Number(inc.taxRate || 0)
  recalcLine(line)
  line.expenseDate = inc.invoiceDate || line.expenseDate
  line.remark = inc.invoiceNo ? t('expense.create.optInvoiceLine', { v1: inc.invoiceNo }) : line.remark
  line.invoice = { invoiceNo: inc.invoiceNo, invoiceDate: inc.invoiceDate, sellerName: inc.sellerName, sellerTaxNo: inc.sellerTaxNo, invoiceType: inc.invoiceType, primaryItemName: inc.primaryItemName || '', fileId: inc.fileId, amount: Number(inc.amount || 0), netAmount: line.netAmount, taxAmount: line.taxAmount }
}
function toggleInvoice(inc) {
  if (inc.checked) {
    if (!inc.lineRef) inc.lineRef = newLineFromInvoice(inc)
    if (!form.lines.includes(inc.lineRef)) form.lines.push(inc.lineRef)
    applyItemMatch(inc)
  } else {
    const idx = form.lines.findIndex((l) => l === inc.lineRef)
    if (idx >= 0) form.lines.splice(idx, 1)
    inc.lineRef = null
  }
}
function removeInvoice(i) {
  const inc = form.invoices[i]
  if (inc.lineRef) { const idx = form.lines.findIndex((l) => l === inc.lineRef); if (idx >= 0) form.lines.splice(idx, 1) }
  form.invoices.splice(i, 1)
}
// 行的发票元信息：优先用内存对象，否则从持久化 extJson 解析回填
function invOf(line) {
  if (line.invoice) return line.invoice
  if (line.extJson) {
    try { const o = typeof line.extJson === 'string' ? JSON.parse(line.extJson) : line.extJson; if (o && o.invoice) { line.invoice = o.invoice; return o.invoice } } catch (e) { /* ignore */ }
  }
  return null
}
// 合计含发票生成的明细行（这些行也可能暂无费用项 itemId，但金额已确定）
function sumAmount() { return form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).reduce((a, l) => a + Number(l.amount || 0), 0) }
function sumTax() { return form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).reduce((a, l) => a + Number(l.taxAmount || 0), 0) }

const budgetPreview = computed(() => {
  const map = {}
  form.lines.filter((l) => l.budgetItemId).forEach((l) => { map[l.budgetItemId] = (map[l.budgetItemId] || 0) + Number(l.amount || 0) })
  return Object.keys(map).map((bid) => {
    const b = budgets.value.find((x) => String(x.id) === String(bid))
    const loaded = !!b
    const b0 = b || {}
    const total = Number(b0.totalAmount || 0)
    const adjust = Number(b0.adjustAmount || 0)
    const occupy = Number(b0.occupyAmount || 0)
    const used = Number(b0.usedAmount || 0)
    const avail = total + adjust - occupy - used
    // 科目取不到（跨主体/未下达）时按未知处理，不能拿 avail=0 误判成"超预算"，也不能显示空白
    return {
      label: loaded ? budgetLabel(items.value, [], [], b0) : budgetUnseenLabel(bid),
      used: map[bid], available: loaded ? avail : null, over: loaded && map[bid] > avail, unknown: !loaded,
      // 控制方式随 pick 行带出，预览提示按档位改口（缺失按 HARD fail-closed，与服务端同一规则）
      control: b0.controlType || 'HARD',
    }
  })
})
const budgetWarn = computed(() => budgetPreview.value.filter((x) => x.over))

async function loadMd() {
  try {
    items.value = await mdApi.expenseItems()
    budgets.value = (await mdApi.budgets({ pageNum: 1, pageSize: 200 })).list || []
    // FINAL-01: 加载费用类型→预算科目映射(选费用类型自动带出预算科目)
    try {
      const maps = (await masterCfgApi.itemBudgetMap.options()) || []
      const m = {}
      maps.forEach((x) => { if (x?.itemId != null && x?.budgetItemId != null) m[String(x.itemId)] = x.budgetItemId })
      budgetMap.value = m
    } catch (e) { budgetMap.value = {} }
    cities.value = await fetchAllPages((p) => mdApi.cities(p), { pageSize: 500 })
    payees.value = (await mdApi.payees({ pageNum: 1, pageSize: 200 })).list || []
    // 出差人受控选择：加载同公司用户列表供出行人下拉
    users.value = (await userApi.page({ pageNum: 1, pageSize: 200, companyId: store.user?.companyId })).list || []
  } catch (e) { ElMessage.error(e.message) }
}
async function loadBillTypes() {
  try {
    const list = await masterCfgApi.form.options()
    if (list && list.length) {
      typeOptions.value = list.filter((t) => t.category === 'CLAIM' || t.category === 'PAY').map((t) => ({
        label: t.formName, billType: t.billType, sub: t.billSubType || '',
        travel: t.billSubType === 'TRAVEL', claim: t.category === 'CLAIM', pay: t.category === 'PAY', modelKey: t.modelKey,
      }))
    }
  } catch (e) { ElMessage.error(e.message) }
}

function onAttachUploaded(rec) {
  if (!rec) return
  const attach = { fileId: rec.id, fileName: rec.fileName, attachType: rec.attachType === 'INVOICE' ? 'INVOICE' : 'OTHER' }
  // 同一文件不重复登记附件
  if (!form.attaches.find((a) => a.fileId === attach.fileId)) form.attaches = [...form.attaches, attach]
  if (rec.attachType === 'INVOICE' && rec.invoice) {
    const inc = newInvoice()
    inc.parsed = !!rec.invoice.ok
    inc.fileId = rec.id
    inc.invoiceNo = rec.invoice.invoiceNo || ''
    inc.invoiceDate = rec.invoice.invoiceDate || ''
    inc.invoiceType = rec.invoice.invoiceType || ''
    inc.sellerName = rec.invoice.sellerName || ''
    inc.sellerTaxNo = rec.invoice.sellerTaxNo || ''
    inc.primaryItemName = rec.invoice.primaryItemName || ''
    inc.amount = Number(rec.invoice.amount || 0)
    inc.taxAmount = Number(rec.invoice.taxAmount || 0)
    inc.netAmount = Number(rec.invoice.netAmount || 0)
    if (inc.netAmount > 0 && inc.amount > 0) inc.taxRate = Math.round(((inc.amount - inc.netAmount) / inc.netAmount) * 10000) / 100
    inc.sourceName = rec.fileName
    form.invoices = [...form.invoices, inc]
    // 解析失败也弹绿条"已识别发票"的话，提单人会以为票面已入单、直接提交一张没有票面的报销单
    // 只看 ok 还不够——"识别成功"和"票进了发票池"是两件事，
    // 分档一律走 invoiceOutcome（判据在后端 invoice.inPool，不要此处不重算金额自洽）
    const out = invoiceOutcome(rec.invoice, { fileName: rec.fileName || '', end: 'pc' })
    ElMessage({ type: out.tone, duration: 6000, message: out.message })
  } else {
    ElMessage.success(t('expense.create.msgAttachmentUploaded', { v1: rec.fileName }))
  }
}
function previewAttach() {
  const ids = form.attaches.map((a) => a.fileId)
  if (!ids.length) return
  attachViewer.value?.open(ids, t('expense.create.colInvoiceAttach'))
}

async function saveDraft() {
  try {
    const id = await billApi.saveDraft({
      bill: { ...form.bill, totalAmount: sumAmount() },
      lines: form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).map((l) => {
        const clone = { ...l }
        const base = (clone.extJson && typeof clone.extJson === 'object') ? clone.extJson : {}
        if (clone.invoice) base.invoice = clone.invoice
        clone.extJson = JSON.stringify(base)
        return clone
      }),
      attaches: form.attaches,
    })
    form.bill.id = id
    if (currentType.value.travel && form.travels.length) await detailApi.saveTravels(id, form.travels)
    // 无 exp:share:update 就不发这一跳（员工角色不在授权名册内 ⇒ 原先每次保存都红一发 4030、
    // 分摊行零写入数据库）。承担规则权威值在 exp_bill_line 各列上，分摊行至今无消费方（同 useBill.js 那条）。
    if (form.shares.length && hasPerm('exp:share:update')) await detailApi.replaceShares(id, form.shares)
    for (const o of form.loanOffsets) await detailApi.addLoanOffset(id, { ...o, billId: id })
    for (const r of form.relations) await detailApi.addRelation(r.sourceBillId, id)
    ElMessage.success(t('expense.common.msgDraftSaved'))
    emit('saved')
    return id
  } catch (e) { ElMessage.error(e.message) }
}
async function submit() {
  saveFormHistory('EXPENSE', form.value || form)

  try {
    // 表头必填由 formRules + guard 统一办结；明细/差旅的逐行规则在表格内，el-form 覆盖不到，仍在此校验
    await guard.submit(async () => {
      if (!form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).length) { ElMessage.warning(t('expense.create.errNeedExpenseLine')); return }
      // 提交前逐行校验：金额必须大于 0
      const badAmt = form.lines.find((l) => Number(l.amount || 0) <= 0)
      if (badAmt) { ElMessage.warning(t('expense.create.errAmountMissing')); return }
      // 差旅明细日期范围校验：止日不得早于起日
      const badTravel = form.travels.find((t) => t.beginDate && t.endDate && t.endDate < t.beginDate)
      if (badTravel) { ElMessage.warning(t('expense.create.errTravelDateOrder')); return }
      // 差旅明细交通/住宿金额不得为负
      if (form.travels.some((t) => Number(t.hotelAmount) < 0 || Number(t.trafficAmount) < 0 || Number(t.allowanceAmount) < 0 || Number(t.otherAmount) < 0)) { ElMessage.warning(t('expense.create.errTravelNegative')); return }
      const id = await saveDraft()
      await billApi.submit(id)
      ElMessage.success(t('expense.create.msgSubmitted'))
      emit('submitted')
    })
  } catch (e) { ElMessage.error(e.message) }
}
function resetForm() {
  form.bill = { billType: 'CLAIM', billSubType: '', title: '', companyId: 1, deptId: 1, applyDate: todayLocal(), currencyCode: 'CNY', payeeName: '', remark: '' }
  form.lines = [newLine()]
  form.travels = []
  form.shares = []
  form.loanOffsets = []
  form.relations = []
  form.attaches = []
  form.invoices = []
}

async function openLoanDialog() {
  try {
    // 改用候选面接口——availableBalance 已扣在途占用，与服务端放行校验同源
    // 同 LoanApply.vue / useBill.js：新建单时 companyId 为空会序列化成字符串 "null"
    //   导致后端 400、await 抛错把下面的 loanDialog=true 一起吞掉（按钮点了没反应）。
    const lp = { limit: 50 }
    const cid = form.bill.companyId
    if (cid !== null && cid !== undefined && cid !== '') lp.companyId = cid
    loanCandidates.value = (await detailApi.loanCandidates(lp)) || []
    loanDialog.value = true
  } catch (e) { ElMessage.error(e.message) }
}
/**
 * 【确定】后按对话框里改过的金额入账（默认=未还余额，可改，支持部分冲抵）。
 * 原先点任意行立刻按未还余额全额冲抵并关窗——误点即产生财务动作、无确认、键盘不可达。
 */
function confirmLoanPicks(items) {
  let added = 0
  for (const { row, amount } of items || []) {
    const exist = form.loanOffsets.find((o) => o.loanBillId === row.id)
    if (exist) exist.offsetAmount = amount
    else {
      form.loanOffsets.push({
        loanBillId: row.id, loanBillNo: row.billNo, offsetAmount: amount,
        maxAmount: Number(row.loanBalance || 0), offsetType: 'CLAIM',
      })
      added++
    }
  }
  if (added) ElMessage.success(t('expense.create.msgLoanOffsetAdded', { v: added }))
}

/** 申请关联金额可改（不硬取申请全额、入表后仍可编辑） */
function confirmApplyPicks(items) {
  let added = 0
  for (const { row, amount } of items || []) {
    const exist = form.relations.find((r) => r.sourceBillId === row.id)
    if (exist) exist.useAmount = amount
    else { form.relations.push({ sourceBillId: row.id, sourceBillNo: row.billNo, useAmount: amount }); added++ }
  }
  if (added) ElMessage.success(t('expense.create.msgApplyLinked', { v: added }))
}
/** 已入账的借款单/申请单 id —— 选择器里置灰，防重复入账 */
const loanOffsetIds = computed(() => form.loanOffsets.map((o) => o.loanBillId))
const relationIds = computed(() => form.relations.map((r) => r.sourceBillId))

async function openApplyDialog() {
  try {
    applyCandidates.value = (await billApi.myPage({ pageNum: 1, pageSize: 50, billType: 'APPLY', status: 'APPROVED' })).list || []
    applyDialog.value = true
  } catch (e) { ElMessage.error(e.message) }
}
// 旧的「点行即按申请全额关联并关窗」pickApply 已整体删除，由 confirmApplyPicks
// 勾选 + 可改金额 + 【确定】 取代；模板中无残留引用（grep pickApply 在本文件为 0）。

function handleSelectionChange(val) { selRows.value = val }

async function removeLine(i) {
  try {
    await ElMessageBox.confirm(t('expense.common.askDeleteRow'), t('expense.common.confirmDeleteTitle'), { type: 'warning' })
  } catch { return }
  form.lines.splice(i, 1)
}
async function removeTravel(i) {
  try {
    await ElMessageBox.confirm(t('expense.common.askDeleteRow'), t('expense.common.confirmDeleteTitle'), { type: 'warning' })
  } catch { return }
  form.travels.splice(i, 1)
}
async function removeLoanOffset(i) {
  try {
    await ElMessageBox.confirm(t('expense.common.askDeleteRow'), t('expense.common.confirmDeleteTitle'), { type: 'warning' })
  } catch { return }
  form.loanOffsets.splice(i, 1)
}
async function removeRelation(i) {
  try {
    await ElMessageBox.confirm(t('expense.common.askDeleteRow'), t('expense.common.confirmDeleteTitle'), { type: 'warning' })
  } catch { return }
  form.relations.splice(i, 1)
}
async function clearAttaches() {
  try {
    await ElMessageBox.confirm(t('expense.create.askClearAttachments'), t('expense.common.confirmDeleteTitle'), { type: 'warning' })
  } catch { return }
  form.attaches = []
}

const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency({ i18nKeys: { CNY: 'expense.common.curCny', USD: 'expense.common.curUsd' }, format: 'name-code' })

const { options: payeeTypeOptions, load: loadPayeeType } = useBaseDict('enum_payee_type', PAYEE_TYPE_LOCAL, PAYEE_TYPE_I18N)

onMounted(() => {
  loadPayeeType()
  loadCurrency()
  // 字典不可用时 useBaseDict 自动回落到本地常量，界面与原先逐字一致
  trafficDict.load()
  autofill(form, 'EXPENSE')

  form.lines = [newLine()]
  loadMd()
  loadBillTypes()
})

defineExpose({ saveDraft, submit, resetForm })
</script>

<style scoped>
.attach-row { display: flex; align-items: center; gap: 8px; }
.attach-name { font-size: 12px; color: #67c23a; }
</style>
