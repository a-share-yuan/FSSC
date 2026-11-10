<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <BillWorkbench ref="wb" bill-type="CLAIM" sub-type="TRAVEL" :claim="true" :travel="true" :before-submit="onBeforeSubmit" :on-apply-pick="onApplyPick">
    <template #create="b">
      <!-- 单据概要 -->
      <el-card :header="$t('expense.claim.colBillSummary')">
        <el-form ref="formRef" :rules="formRules" :model="b.form.bill" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colBillNoFull')"><el-input :model-value="b.form.bill.billNo || $t('expense.common.txtAutoAfterSave')" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colMakeTime')"><el-input :model-value="b.form.bill.id ? formatDateTime(b.form.bill.createTime) : b.nowTs()" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colMaker')"><el-input :model-value="currentUserName" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
<el-form-item :label="$t('expense.common.colApplicant')" prop="applyUserId">
                <UserSelect v-model="b.form.bill.applyUserId" :users="users" @change="(id) => onApplicantChange(b, id)" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.claim.colApplicantDept')"><el-input :model-value="applicantDept" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.claim.colApplicantCompany')"><el-input :model-value="applicantCompany" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.common.colCurrency')" prop="currencyCode"><el-select v-model="b.form.bill.currencyCode" style="width: 100%">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />
</el-select></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.common.colTotalAmount')"><span style="color:#f59a23;font-weight:700;font-size:15px">{{ $t('expense.common.amtYuan', { v1: b.fmt(totalAmount(b)) }) }}</span></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colBudgetYear')" prop="periodYear"><el-select v-model="b.form.bill.periodYear" style="width: 100%"><el-option v-for="y in years" :key="y" :label="$t('expense.claim.optYear', { y })" :value="y" /></el-select></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colPaperCount')" prop="extJson.paperAttachCount"><el-input-number v-model="b.form.bill.extJson.paperAttachCount" :min="0" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.travelClaim.colClaimType')" prop="extJson.reimburseType">
                <el-select v-model="b.form.bill.extJson.reimburseType" style="width: 100%">
                  <el-option :label="$t('expense.travelClaim.optTravel')" value="TRAVEL" />
                  <el-option :label="$t('expense.travelClaim.optTrainWithTravel')" value="TRAIN_TRAVEL" />
                  <el-option :label="$t('expense.travelClaim.optTrainNoTravel')" value="TRAIN" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.travelClaim.colTripType')" prop="extJson.travelType">
                <el-select v-model="b.form.bill.extJson.travelType" style="width: 100%">
                  <el-option v-for="o in travelTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.travelClaim.lblHasEntertain')">
                <el-select v-model="b.form.bill.extJson.hasEntertain" style="width: 100%"><el-option :label="$t('common.no')" :value="false" /><el-option :label="$t('common.yes')" :value="true" /></el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item :label="$t('expense.common.colTitle')" prop="title"><el-input v-model="b.form.bill.title" :placeholder="$t('expense.travelClaim.phTitleTravel')" /></el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item :label="$t('expense.claim.colBillDesc')" prop="remark"><el-input v-model="b.form.bill.remark" type="textarea" :rows="2" /></el-form-item>
            </el-col>
            <!-- 超申请 -->
            <el-col :xs="24">
              <el-form-item :label="$t('expense.travelClaim.colOverApplyContent')"><el-input :model-value="overApplyContent(b)" disabled /></el-form-item>
            </el-col>
            <el-col v-if="isOverApply(b)" :xs="24">
              <el-form-item :label="$t('expense.travelClaim.colOverApplyNote')" prop="extJson.overApplyReason"><el-input v-model="b.form.bill.extJson.overApplyReason" :placeholder="$t('expense.travelClaim.phOverApplyRequired')" /></el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-alert type="info" :closable="false" :title="$t('expense.travelClaim.hintLinkReq')" />
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- 费用详情（按类别 tab） -->
      <el-card :header="$t('expense.claim.colExpenseDetail')" style="margin-top: 12px">
        <el-tabs v-model="activeCat" type="card">
          <el-tab-pane :label="$t('common.all')" name="ALL" />
          <el-tab-pane :label="$t('expense.travelClaim.tabTrainFee')" name="TRAIN_FEE" />
          <el-tab-pane :label="$t('expense.travelClaim.colHotel')" name="HOTEL" />
          <el-tab-pane :label="$t('expense.travelClaim.colLongTrafficFee')" name="TRAFFIC" />
          <el-tab-pane :label="$t('expense.travelClaim.colLocalTrafficFee')" name="LOCAL_TRAFFIC" />
          <el-tab-pane :label="$t('expense.travelClaim.tabMealAllowance')" name="MEAL" />
          <el-tab-pane :label="$t('expense.travelClaim.tabChangeFee')" name="CHANGE" />
          <el-tab-pane v-if="b.form.bill.extJson.travelType === 'ABROAD'" :label="$t('expense.travelClaim.tabVisaFee')" name="VISA" />
          <el-tab-pane :label="$t('expense.travelClaim.tabOtherFee')" name="OTHER" />
        </el-tabs>
        <el-table :data="filteredLines(b)" size="small" border style="margin-top: 8px" :empty-text="$t('common.empty')" v-loading="loading" @row-dblclick="openRowDetail">
          <el-table-column :label="$t('expense.travelClaim.colExpenseCategory')" width="120"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.travelClaim.colExpenseCategory') }}</template>
            <template #default="{ row }">
              <el-select v-model="row.category" style="width: 100%">
                <el-option v-for="c in cats" :key="c.v" :label="c.label" :value="c.v" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.common.colExpenseType')" width="140"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.common.colExpenseType') }}</template>
            <template #default="{ row }">
              <el-select v-model="row.itemId" filterable style="width: 100%" @change="(id) => onItemChange(b, row, id)">
                <el-option v-for="it in b.items" :key="it.id" :label="it.itemName" :value="it.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.common.colBudgetItem')" width="130"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.common.colBudgetItem') }}</template>
            <template #default="{ row }">
              <el-select v-model="row.budgetItemId" filterable clearable style="width: 100%">
                <el-option v-if="row.budgetItemId != null && !b.budgets.some((x) => String(x.id) === String(row.budgetItemId))" :label="budgetUnseenLabel(row.budgetItemId)" :value="row.budgetItemId" />
                <el-option v-for="bg in b.budgets" :key="bg.id" :label="budgetLabel(b.items, b.depts, b.costCenters, bg, { companies: b.companies, projects: b.projects })" :value="bg.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column v-if="isTrafficCat(row)" :label="$t('expense.travelClaim.colDeparture')" width="110">
            <template #default="{ row }"><el-select v-model="row.fromCityId" filterable clearable style="width: 100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column v-if="isTrafficCat(row)" :label="$t('expense.travelClaim.colDestination')" width="110">
            <template #default="{ row }"><el-select v-model="row.toCityId" filterable clearable style="width: 100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column v-if="isTrafficCat(row)" :label="$t('expense.travelClaim.colTripDates')" width="220">
            <template #default="{ row }">
              <el-date-picker v-model="row.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 104px" :disabled-date="(d) => dayAfter(d, row.endDate)" />
              <el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" style="width: 104px" :disabled-date="(d) => dayBefore(d, row.expenseDate)" />
            </template>
          </el-table-column>
          <el-table-column v-if="row && row.category === 'HOTEL'" :label="$t('expense.travelClaim.colStayRooms')" width="160">
            <template #default="{ row }">
              <el-select v-model="row.toCityId" filterable clearable style="width: 90px"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select>
              <el-input-number v-model="row.roomCount" :min="1" style="width: 60px" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('field.amount')" width="110"><template #header><span style="color:#f56c6c">*</span>{{ $t('field.amount') }}</template>
            <template #default="{ row }"><el-input-number v-model="row.amount" :min="0" :precision="2" style="width: 100%" @change="recalcLine(b, row)" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.claim.colStdOver')" width="90">
            <template #default="{ row }">
              <span v-if="row.stdAmount != null" style="font-size:12px">{{ $t('expense.claim.txtStdAmount', { v1: b.fmt(row.stdAmount) }) }}</span>
              <el-tag v-if="row.overStd === 1" type="danger" size="small">{{ $t('expense.claim.stOverLimit') }}</el-tag>
              <el-tag v-else-if="row.stdAmount != null" type="success" size="small">{{ $t('expense.claim.stCompliant') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.claim.colVoucherForm')" width="105">
            <template #default="{ row }">
              <el-select v-model="row.voucherForm" size="small" style="width: 100%">
                <el-option :label="$t('expense.claim.optDomesticInvoice')" value="FAPIAO" />
                <el-option :label="$t('expense.claim.optAbroadReceipt')" value="RECEIPT" />
                <el-option :label="$t('expense.claim.optItinerary')" value="ITINERARY" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.claim.colCnDesc')" min-width="130">
            <template #default="{ row }">
              <el-input v-model="row.cnDesc" size="small" :placeholder="needCnDesc(row) ? t('expense.claim.phCnDescRequired') : t('expense.claim.phCnDescOverseas')" />
              <div v-if="needCnDesc(row) && !(row.cnDesc || '').trim()" style="color:#f56c6c;font-size:11px;line-height:1.3">{{ $t('expense.claim.hintAbroadCnDesc') }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.remark')" min-width="100">
            <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
          </el-table-column>
          <el-table-column :label="$t('common.operation')" width="70">
            <template #default="{ $index }">
              <el-button link size="small" @click="copyLine(b, $index)">{{ $t('expense.common.btnCopy') }}</el-button>
              <el-button link size="small" type="danger" @click="removeLine(b, $index)">{{ $t('expense.common.btnDeleteShort') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="addLine(b)">{{ $t('expense.claim.btnAddExpenseLine') }}</el-button>
        <div style="margin-top: 6px">{{ $t('expense.claim.labFeeTotal') }}<span style="color:#f59a23;font-weight:700">{{ b.fmt(totalAmount(b)) }}</span> {{ $t('expense.common.unitYuan') }}</div>
      </el-card>

      <!-- 行程信息（差旅/培训涉及出差时） -->
      <el-card v-if="b.form.bill.extJson.reimburseType !== 'TRAIN'" :header="$t('expense.travelClaim.secTripInfo')" style="margin-top: 12px">
        <el-table :data="b.form.travels" size="small" border :empty-text="$t('common.empty')" v-loading="loading" @row-dblclick="openRowDetail">
          <el-table-column :label="$t('expense.travelClaim.colPassenger')" width="110">
            <template #default="{ row, $index }"><el-select v-model="row.travelerName" filterable style="width: 100%" :placeholder="$t('expense.common.phPickTraveler')" @change="onTravelerPick(row)"><el-option v-for="u in users" :key="u.id" :label="u.nickName || u.userName" :value="u.nickName || u.userName" :disabled="travelDup(b, $index, u.nickName || u.userName)" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colDeparture')" width="110">
            <template #default="{ row }"><el-select v-model="row.fromCityId" filterable style="width: 100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colDestination')" width="110">
            <template #default="{ row }"><el-select v-model="row.toCityId" filterable style="width: 100%"><el-option v-for="c in b.cities" :key="c.id" :label="c.cityName" :value="c.id" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colDestCountry')" width="120">
            <template #default="{ row }"><el-input v-model="row.destCountry" :placeholder="$t('expense.travelClaim.phBlankDomestic')" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colStartEndDate')" width="220">
            <template #default="{ row }">
              <el-date-picker v-model="row.beginDate" type="date" value-format="YYYY-MM-DD" style="width: 104px" :disabled-date="(d) => dayAfter(d, row.endDate)" @change="calcTravel(b, row)" />
              <el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" style="width: 104px" :disabled-date="(d) => dayBefore(d, row.beginDate)" @change="calcTravel(b, row)" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colTrafficMode')" width="100">
            <template #default="{ row }"><el-select v-model="row.trafficType" style="width: 100%" @change="onTrafficTypeChange(row)"><el-option v-for="o in trafficOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('expense.preapply.lblSeatClass')" width="100">
            <template #default="{ row }"><el-select v-model="row.trafficClass" clearable style="width: 100%"><el-option v-for="o in seatOptionsOf(row.trafficType)" :key="o.value" :label="o.label" :value="o.value" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colHotel')" width="100">
            <template #default="{ row }"><el-input-number v-model="row.hotelAmount" :min="0" :precision="2" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colTrafficFee')" width="100">
            <template #default="{ row }"><el-input-number v-model="row.trafficAmount" :min="0" :precision="2" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colAllowanceFormula')" width="100">
            <template #default="{ row }"><el-input-number v-model="row.allowanceAmount" :min="0" :precision="2" style="width: 100%" :placeholder="$t('expense.travelClaim.phBlankAuto')" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.travelClaim.colEstimatedDays')" width="80">
            <template #default="{ row }">{{ row.days || 0 }}</template>
          </el-table-column>
          <el-table-column :label="$t('common.operation')" width="60">
            <template #default="{ $index }"><el-button link size="small" type="danger" @click="removeTravel(b, $index)">{{ $t('expense.common.btnDeleteShort') }}</el-button></template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="b.form.travels.push(b.newTravel())">{{ $t('expense.preapply.btnAddTrip') }}</el-button>
        <el-alert style="margin-top: 6px" type="info" :closable="false"
          :title="$t('expense.travelClaim.hintAbroadAuto')" />
      </el-card>

      <!-- 结算信息 -->
      <el-card :header="$t('expense.claim.secSettlement')" style="margin-top: 12px">
        <el-form ref="settleFormRef" :rules="formRules" :model="b.form.bill" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colReceiveAmount')">
                <span style="color:#f59a23;font-weight:700">{{ $t('expense.common.amtYuan', { v1: b.fmt(payable(b)) }) }}</span>
                <div style="width:100%;font-size:12px;color:#909399">{{ $t('expense.claim.txtNetPayable', { v1: b.fmt(totalAmount(b)), v2: b.fmt(offsetTotal(b)) }) }}</div>
              </el-form-item>
            </el-col>
<el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colPayMethod')" prop="extJson.payMethod"><el-select v-model="b.form.bill.extJson.payMethod" style="width: 100%"><el-option v-for="o in payMethodOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colPayeeType')" prop="payTarget"><el-select v-model="b.form.bill.payTarget" style="width: 100%" :placeholder="$t('expense.common.phPickPayeeType')">
<el-option v-for="o in payeeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
</el-select></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.travelClaim.colAccountType')"><el-input :model-value="b.form.bill.payTarget === 'SUPPLIER' || b.form.bill.payTarget === 'CUSTOMER' ? t('expense.travelClaim.optCorporateAccount') : t('expense.claim.colEmployee')" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colPayeeAccount')" prop="payeeAccountNo"><el-input v-model="b.form.bill.payeeAccountNo" :placeholder="$t('expense.travelClaim.phPayeeAccountRequired')" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colAccountName')" prop="extJson.accountName"><el-input v-model="b.form.bill.extJson.accountName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colAccountNo')"><el-input v-model="b.form.bill.extJson.accountNo" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colBank')"><el-input v-model="b.form.bill.extJson.bankName" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </el-card>
    </template>
  </BillWorkbench>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import BillWorkbench from '../../components/bill/BillWorkbench.vue'
import { userApi, billApi, store } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useAutoFill } from '@/composables/useAutoFill'
import { billRules } from '@/constants/billRequired'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { showRowDetail, budgetLabel, budgetUnseenLabel, formatDateTime } from '@/utils/format'
import { useI18n } from 'vue-i18n'
import { useBaseDict, TRAFFIC_LOCAL, TRAFFIC_I18N, SEAT_LOCAL, SEAT_I18N, PAY_METHOD_LOCAL, PAY_METHOD_I18N_CLAIM, PAYEE_TYPE_LOCAL, PAYEE_TYPE_I18N } from '@/composables/useBaseDict'
import { useCurrency } from '@/composables/useCurrency'

const { t } = useI18n()
const { saveFormHistory } = useAutoFill()
// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const wb = ref(null)
// 表头红星/规则/校验同源（constants/billRequired.js），差旅页特有的三个下拉再叠加本地规则
const formRef = ref(null)
const settleFormRef = ref(null)
const formRules = computed(() => ({
  ...billRules('CLAIM', 'TRAVEL'),
  'extJson.reimburseType': [{ required: true, message: t('expense.travelClaim.errClaimTypeRequired') }],
  'extJson.travelType': [{ required: true, message: t('expense.travelClaim.errTripTypeRequired') }],
  'extJson.overApplyReason': commonRules.required(t('expense.travelClaim.errOverApplyNote')),
}))
const loading = ref(false)
const users = ref([])
const activeCat = ref('ALL')
const currentUserName = ref(store.user?.username || store.user?.userName || t('expense.common.optCurrentUser'))
const years = [new Date().getFullYear() - 1, new Date().getFullYear(), new Date().getFullYear() + 1]
const cats = computed(() => ([
  { v: 'TRAIN_FEE', label: t('expense.travelClaim.tabTrainFee') }, { v: 'HOTEL', label: t('expense.travelClaim.colHotel') }, { v: 'TRAFFIC', label: t('expense.travelClaim.colLongTrafficFee') },
  { v: 'LOCAL_TRAFFIC', label: t('expense.travelClaim.colLocalTrafficFee') }, { v: 'MEAL', label: t('expense.travelClaim.tabMealAllowance') }, { v: 'CHANGE', label: t('expense.travelClaim.tabChangeFee') },
  { v: 'VISA', label: t('expense.travelClaim.tabVisaFee') }, { v: 'OTHER', label: t('expense.travelClaim.tabOtherFee') },
]))

// 境外凭证合规⑤：凭证形态（境内发票/境外收据/行程单）+ 中文说明（19号公告税前扣除要件）
function newLine(b) { return Object.assign(b.newLine(), { category: 'TRAFFIC', roomCount: 1, endDate: '', fromCityId: null, toCityId: null, voucherForm: 'FAPIAO', cnDesc: '' }) }
function needCnDesc(row) { return row.voucherForm === 'RECEIPT' || row.voucherForm === 'ITINERARY' }
function addLine(b) { b.form.lines.push(newLine(b)) }
function copyLine(b, i) { b.form.lines.push({ ...b.form.lines[i], id: null }) }
function recalcLine(b, row) { b.recalcLine(row) }
function onItemChange(b, row, id) { b.onItemChange(row, id) }
function isTrafficCat(row) { return row && ['TRAFFIC', 'LOCAL_TRAFFIC'].includes(row.category) }
function filteredLines(b) {
  if (activeCat.value === 'ALL') return b.form.lines
  return b.form.lines.filter((l) => l.category === activeCat.value)
}
function calcTravel(b, row) {
  if (row.beginDate && row.endDate) {
    const diff = Math.round((new Date(row.endDate) - new Date(row.beginDate)) / 86400000) + 1
    // 结束早于开始时不再静默折成 1 天（会让倒置日期看起来"合法"）
    row.days = diff > 0 ? diff : null
  }
}
function dayStr(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
function dayBefore(d, min) { return !!min && dayStr(d) < min }
function dayAfter(d, max) { return !!max && dayStr(d) > max }
// 与 PreApply 同一规则：同一人同一天出现两条行程＝重复登记（多段行程日期不同，不受影响）
function travelDup(b, index, name) {
  const cur = b.form.travels[index]
  if (!cur || !name) return false
  return b.form.travels.some((t, i) => i !== index && t.travelerName === name && (t.beginDate || '') === (cur.beginDate || ''))
}
// 行删除确认：只拦「已填内容」的行，空白行直接删，不给高频操作加负担
async function confirmRemoveRow(hasContent) {
  if (!hasContent) return true
  try {
    await ElMessageBox.confirm(t('expense.common.askDeleteRow'), t('expense.common.confirmDeleteTitle'), { type: 'warning' })
    return true
  } catch { return false }
}
async function removeLine(b, i) {
  const l = b.form.lines[i]
  if (!l) return
  const has = l.itemId != null || Number(l.amount || 0) > 0 || !!b.invOf(l) || !!String(l.remark || '').trim()
  if (!(await confirmRemoveRow(has))) return
  b.form.lines.splice(i, 1)
}
async function removeTravel(b, i) {
  const t = b.form.travels[i]
  if (!t) return
  const has = !!t.beginDate || !!t.endDate || !!t.fromCityId || !!t.toCityId || !!t.destCountry
    || !!t.trafficClass || !!String(t.remark || '').trim()
    || [t.hotelAmount, t.trafficAmount, t.allowanceAmount, t.localTrafficAmount, t.otherAmount].some((n) => Number(n || 0) > 0)
  if (!(await confirmRemoveRow(has))) return
  b.form.travels.splice(i, 1)
}
// 同一规则：发票勾选生成的明细行（暂无 itemId 但金额已填）即时计入合计
function totalAmount(b) {
  return b.form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).reduce((a, l) => a + Number(l.amount || 0), 0)
}
function offsetTotal(b) {
  return b.form.loanOffsets.reduce((a, o) => a + Number(o.offsetAmount || 0), 0)
}
function payable(b) { return Math.max(0, Math.round((totalAmount(b) - offsetTotal(b)) * 100) / 100) }

// 超申请：报销总额 > 关联申请单可冲抵金额
function applyAmount(b) {
  return b.form.relations.reduce((a, r) => a + Number(r.useAmount || 0), 0)
}
function isOverApply(b) { return applyAmount(b) > 0 && totalAmount(b) > applyAmount(b) }
function overApplyContent(b) {
  if (!b.form.relations.length) return t('expense.travelClaim.txtNeedRequisition')
  if (isOverApply(b)) return t('expense.travelClaim.msgOverApply', { v1: b.fmt(totalAmount(b)), v2: b.fmt(applyAmount(b)) })
  return t('expense.travelClaim.msgWithinApply', { v1: b.fmt(totalAmount(b)), v2: b.fmt(applyAmount(b)) })
}

async function onBeforeSubmit(b) {
  // 表头/结算必填校验：与红星同源，漏填即中止、不发请求
  if (!(await validateForms([formRef, settleFormRef]))) return false
  if (!b.form.relations.length) {
    ElMessage.warning(t('expense.travelClaim.errRequisitionRequired')); return false
  }
  if (!b.form.lines.filter((l) => l.itemId).length) {
    ElMessage.warning(t('expense.claim.errNeedExpenseLine')); return false
  }
  // 境外凭证合规⑤：境外收据/行程单必填中文说明（后端 submit 同一规则兜底）
  if (b.form.lines.some((l) => l.itemId && needCnDesc(l) && !(l.cnDesc || '').trim())) {
    ElMessage.warning(t('expense.claim.errAbroadCnDesc')); return false
  }
  // 行程信息：已填的行必须完整，且起止/费用日期不得倒置（倒置会被后端按异常日期拒绝或算错标准）
  if (b.form.bill.extJson.reimburseType !== 'TRAIN') {
    const incomplete = b.form.travels.find((r) => !r.travelerName || !r.fromCityId || !r.toCityId || !r.beginDate || !r.endDate || !r.trafficType)
    if (incomplete) {
      ElMessage.warning(t('expense.travelClaim.errTripIncomplete')); return false
    }
    if (b.form.travels.some((r) => r.beginDate && r.endDate && r.beginDate > r.endDate)) {
      ElMessage.warning(t('expense.travelClaim.errTripDateOrder')); return false
    }
    const dupTrip = b.form.travels.find((r, i) => travelDup(b, i, r.travelerName))
    if (dupTrip) {
      ElMessage.warning(t('expense.preapply.errDuplicateTrip', { v1: dupTrip.travelerName })); return false
    }
  }
  if (b.form.lines.some((l) => isTrafficCat(l) && l.expenseDate && l.endDate && l.expenseDate > l.endDate)) {
    ElMessage.warning(t('expense.travelClaim.errLineDateOrder')); return false
  }
  b.form.bill.extJson.relations = b.form.relations.map((r) => ({ sourceBillId: r.sourceBillId, useAmount: r.useAmount }))
  return true
}

// 关联申请单后自动带入费用详情/分摊：根据分摊规则将申请单分摊信息带入各费用详情
function catOf(itemName) {
  const n = itemName || ''
  if (n.includes('培训')) return 'TRAIN_FEE'
  if (n.includes('住宿')) return 'HOTEL'
  if (n.includes('市内')) return 'LOCAL_TRAFFIC'
  if (n.includes('交通') || n.includes('机票') || n.includes('火车') || n.includes('高铁')) return 'TRAFFIC'
  if (n.includes('餐')) return 'MEAL'
  if (n.includes('退改') || n.includes('签')) return 'CHANGE'
  return 'OTHER'
}
// category 不是数据库列：草稿/驳回重提回填的行按费用项名补齐分类，否则「住宿费」等分类签整表为空，
// 看起来就像明细金额丢了。
watch(() => wb.value?.b?.form.lines, (lines) => {
  ;(lines || []).forEach((l) => { if (!l.category) l.category = catOf(l.itemName) })
})
async function onApplyPick(row, form) {
  try {
    const d = await billApi.detail(row.id)
    const srcLines = (d.lines || []).filter((l) => l.itemId)
    if (!srcLines.length) return
    try {
      await ElMessageBox.confirm(t('expense.travelClaim.askImportLines'), t('expense.travelClaim.btnLinkRequisition'), { type: 'warning' })
    } catch (e) { return }
    form.lines = srcLines.map((l) => Object.assign(newLine(wb.value.b), {
      itemId: l.itemId, itemName: l.itemName, amount: l.amount || 0, budgetItemId: l.budgetItemId || null,
      // 带入日期与后端写入数据库时区一致，走 useBill.today()（本地时钟），不再用 UTC 的 toISOString
      expenseDate: l.expenseDate || wb.value.b.today(), category: catOf(l.itemName), remark: l.remark || '',
    }))
    form.shares = (d.shares || []).map((s) => ({
      costCenterId: s.costCenterId, projectId: s.projectId, budgetItemId: s.budgetItemId,
      shareAmount: s.shareAmount || 0, shareType: s.shareType, remark: s.remark || '',
    }))
    if (d.travels && d.travels.length) form.travels = d.travels.map((t) => ({ ...t, id: null }))
    ElMessage.success(t('expense.travelClaim.msgLinesImported'))
  } catch (e) { ElMessage.error(e.message) }
}

async function onApplicantChange(b, id) {
  const u = users.value.find((x) => x.id === id)
  if (u) b.form.bill.applyUserName = u.nickName || u.userName
}

const applicantDept = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.deptName || u.departmentName)) || '—'
})
const applicantCompany = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.companyName || u.company)) || '—'
})

function openRowDetail(row) {
  if (!row || !Object.keys(row).length) {
    ElMessage.info(t('expense.travelClaim.errFillRowFirst'))
    return
  }
  // 双行详情：非原始字段 dump，友好展示既有值
  return showRowDetail(row, {
    title: t('expense.travelClaim.dlgLineDetail'),
    labels: {
      itemId: t('expense.travelClaim.ctxExpenseTypeId'), itemName: t('expense.common.colExpenseType'), costCenterName: t('expense.travelClaim.colCostCenter'), projectName: t('expense.travelClaim.colProject'),
      budgetItemId: t('expense.travelClaim.ctxBudgetItemId'), amount: t('field.amount'), taxRate: t('expense.common.colTaxRate'), taxAmount: t('field.taxAmount'), netAmount: t('expense.common.colNetAmount'),
      expenseDate: t('expense.travelClaim.colExpenseDate'), remark: t('expense.travelClaim.ctxReason'), overStd: t('expense.travelClaim.colOverStd'), stdAmount: t('expense.travelClaim.colStdAmount'),
    },
  })
}

// 出差类型（travel_type）：字典定值域 + i18n 定文案
const travelTypeDict = useBaseDict('travel_type',
  { DOMESTIC: '境内', ABROAD: '境外', TRIAREA: '三地' },
  { DOMESTIC: 'expense.travelClaim.optDomestic', ABROAD: 'expense.travelClaim.optAbroad', TRIAREA: 'expense.travelClaim.optThreeCities' })
const travelTypeOptions = travelTypeDict.options

// 交通方式 / 付款方式（基础数据办结）
const trafficDict = useBaseDict('traffic_mode', TRAFFIC_LOCAL, TRAFFIC_I18N)
const trafficOptions = trafficDict.options
const payMethodDict = useBaseDict('pay_method', PAY_METHOD_LOCAL, PAY_METHOD_I18N_CLAIM)
const payMethodOptions = payMethodDict.options

// 舱位席别（seat_class）：与事前申请同一规则——字典定值域，按交通方式窄化，换方式即清掉错配值
const seatDict = useBaseDict('seat_class', SEAT_LOCAL, SEAT_I18N)
const seatOptions = seatDict.options
const SEAT_CABIN = ['ECONOMY', 'BUSINESS', 'FIRST']
const SEAT_RAIL = ['FIRST_SEAT', 'SECOND']
function seatOptionsOf(trafficType) {
  const all = seatOptions.value
  if (trafficType === 'AIR' || trafficType === 'SHIP') return all.filter((o) => SEAT_CABIN.includes(o.value))
  if (trafficType === 'RAIL' || trafficType === 'TRAIN') return all.filter((o) => SEAT_RAIL.includes(o.value))
  return all
}
function onTrafficTypeChange(row) {
  const keep = seatOptionsOf(row.trafficType)
  if (row.trafficClass && !keep.some((o) => o.value === row.trafficClass)) row.trafficClass = ''
}
// 乘客姓名是显示值，写入数据库要 travelerId（后端正据此校验同人同日重复行程）
function onTravelerPick(row) {
  const u = users.value.find((x) => (x.nickName || x.userName) === row.travelerName)
  row.travelerId = u ? u.id : null
}

const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency({ i18nKeys: { CNY: 'expense.common.curCny', USD: 'expense.common.curUsd' }, format: 'name-code' })

const { options: payeeTypeOptions, load: loadPayeeType } = useBaseDict('enum_payee_type', PAYEE_TYPE_LOCAL, PAYEE_TYPE_I18N)

onMounted(async () => {
  loadPayeeType()
  loadCurrency()
  travelTypeDict.load()
  // 字典不可用时自动回落到本地常量，界面与原先逐字一致
  trafficDict.load()
  seatDict.load()
  payMethodDict.load()
  try {
    // 默认只加载同公司人员
    const list = (await userApi.options({ pageNum: 1, pageSize: 200, companyId: store.user?.companyId })).list || []
    users.value = list
currentUserName.value = store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || t('expense.common.optCurrentUser')
    const b = wb.value && wb.value.b
    const uid = store.user?.userId ?? store.user?.id
    if (b && !b.form.bill.applyUserId) {
      // 默认申请人 = 当前登录人，严格匹配 me.id，不回退 list[0]
      const me = uid ? list.find((x) => x.id === uid) : null
      if (me) {
        b.form.bill.applyUserId = me.id
        b.form.bill.applyUserName = me.nickName || me.userName
      } else {
        b.form.bill.applyUserId = null
        b.form.bill.applyUserName = ''
        if (uid != null) ElMessage.warning(t('expense.common.errApplicantNotMatched'))
      }
    }
    if (b && b.form.bill.title === '') b.form.bill.title = '差旅培训报销'
  } catch (e) { /* ignore */ }
})
</script>
