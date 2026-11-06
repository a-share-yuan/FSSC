<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <BillWorkbench ref="wb" bill-type="CLAIM" sub-type="" :claim="true" :before-submit="onBeforeSubmit">
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
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colApplicantDept')"><el-input :model-value="applicantDept" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colApplicantCompany')"><el-input :model-value="applicantCompany" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
<el-form-item :label="$t('expense.common.colCurrency')" prop="currencyCode">
                <el-select v-model="b.form.bill.currencyCode" style="width: 100%">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />



                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colEntryBasis')">
                <el-radio-group v-model="b.form.bill.priceType" @change="(v) => onPriceTypeChange(b, v)">
                  <el-radio-button value="TAX_IN">{{ $t('expense.claim.optGrossEntry') }}</el-radio-button>
                  <el-radio-button value="TAX_EX">{{ $t('expense.claim.optNetEntry') }}</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.common.colTotalAmount')"><span style="color:#f59a23;font-weight:700;font-size:15px">{{ $t('expense.common.amtYuan', { v1: b.fmt(totalAmount(b)) }) }}</span></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
<el-form-item :label="$t('expense.claim.colBudgetYear')" prop="periodYear">
                <el-select v-model="b.form.bill.periodYear" style="width: 100%">
                  <el-option v-for="y in years" :key="y" :label="$t('expense.claim.optYear', { y })" :value="y" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colPaperCount')" prop="extJson.paperAttachCount"><el-input-number v-model="b.form.bill.extJson.paperAttachCount" :min="0" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colPaperScanned')"><el-input :model-value="'影像扫描后回填'" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
<el-form-item :label="$t('expense.common.colTitle')" prop="title"><el-input v-model="b.form.bill.title" :placeholder="$t('expense.claim.phTitleExpense')" /></el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item :label="$t('expense.claim.colBillDesc')" prop="remark"><el-input v-model="b.form.bill.remark" type="textarea" :rows="2" /></el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- 费用详情 -->
      <el-card :header="$t('expense.claim.colExpenseDetail')" style="margin-top: 12px">
        <el-form :model="shareForm" :rules="shareTypeRules" label-width="110px" size="default">
          <!-- 单选组恒有默认值，不存在漏填，故不加红星 -->
          <el-form-item :label="$t('expense.claim.colBearingType')" prop="shareType">
            <el-radio-group v-model="shareType">
              <el-radio-button value="COST_CENTER">{{ $t('expense.claim.optCostCenterBearing') }}</el-radio-button>
              <el-radio-button value="PROJECT">{{ $t('expense.claim.optProjectBearing') }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <el-table :data="b.form.lines" size="small" border :empty-text="$t('common.empty')" v-loading="loading" @row-dblclick="openRowDetail">
          <el-table-column :label="$t('expense.common.colExpenseType')" width="150"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.common.colExpenseType') }}</template>
            <template #default="{ row }">
              <el-select v-model="row.itemId" filterable style="width: 100%" @change="(id) => onItemChange(b, row, id)">
                <el-option v-for="it in b.items" :key="it.id" :label="it.itemName" :value="it.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="shareType === 'COST_CENTER' ? t('expense.travelClaim.colCostCenter') : t('expense.travelClaim.colProject')" width="140">
            <template #default="{ row }">
              <el-select v-if="shareType === 'COST_CENTER'" v-model="row.costCenterId" filterable clearable style="width: 100%" :placeholder="$t('expense.claim.phPickCostCenter')">
                <el-option v-for="c in b.costCenters" :key="c.id" :label="ccLabel(c)" :value="c.id" />
              </el-select>
              <el-select v-else v-model="row.projectId" filterable clearable style="width: 100%" :placeholder="$t('expense.claim.phPickProject')">
                <el-option v-for="p in b.projects" :key="p.id" :label="projectLabel(p)" :value="p.id" />
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
          <el-table-column :label="$t('expense.claim.colOrigAmount')" width="130"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.claim.colOrigAmount') }}</template>
            <template #default="{ row }">
              <el-input-number v-if="!isForeignLine(row)" v-model="row.amount" :min="0" :precision="2" style="width: 100%" @change="recalcLine(b, row)" />
              <el-input-number v-else :model-value="Number(row.origAmount ?? row.amount)" :min="0" :precision="2" style="width: 100%" @change="(v) => onOrigAmt(b, row, v)" />
              <div v-if="b.form.bill.priceType === 'TAX_EX'" style="font-size:11px;color:#e6a23c;line-height:1.2">{{ $t('expense.claim.hintNetEntry') }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.common.colCurrency')" width="95">
            <template #default="{ row }">
              <el-select :model-value="lineCur(row)" style="width: 100%" @change="(v) => onCurChange(b, row, v)">
                <el-option v-for="c in currencies" :key="c" :label="c" :value="c" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.claim.colExchangeRate')" width="150">
            <template #default="{ row }">
              <template v-if="isForeignLine(row)">
                <el-input :model-value="row.exchangeRate ?? ''" :placeholder="$t('expense.claim.phRateAuto')" size="small" @change="(v) => onRateInput(b, row, v)" />
                <div v-if="row._sysRate" style="font-size:11px;color:#909399;line-height:1.2">{{ $t('expense.claim.txtSystemPrice', { v1: row._sysRate }) }}</div>
              </template>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('field.time')" width="120">
            <template #default="{ row }"><el-date-picker v-model="row.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled-date="blockFutureDate" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.claim.colExpenseReason')" min-width="110">
            <template #default="{ row }"><el-input v-model="row.remark" size="small" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.claim.colEntertainType')" width="130">
            <template #default="{ row }">
              <!-- 绑真实列 entertain_type，ext_json 仅作历史单据回显兜底（查询范围见 entertainOfLine） -->
              <el-select :model-value="entertainOfLine(row).type" style="width: 100%" :disabled="!isEntertain(b, row)" clearable
                :placeholder="isEntertain(b, row) ? t('expense.claim.dlgPickEntertainType') : t('expense.claim.phEntertainAfter')"
                @update:model-value="(v) => setEntertain(row, { entertainType: v })">
                <el-option :label="$t('expense.claim.optBusinessBanquet')" value="BUSINESS" /><el-option :label="$t('expense.claim.optWorkingBanquet')" value="WORK" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.claim.colGuests')" width="110">
            <template #default="{ row }">
              <!-- 接待对象 → exp_bill_line.attendees（列注释即「参与人员(招待费/会议费)」） -->
              <el-input :model-value="row.attendees || ''" :disabled="!isEntertain(b, row)" size="small" :placeholder="$t('expense.claim.colEntertainGuests')"
                @update:model-value="(v) => setEntertain(row, { attendees: v })" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.claim.colHeadcount')" width="120">
            <template #default="{ row }">
              <!-- 接待人数 → exp_bill_line.person_count（列注释「涉及人数(招待费必填)」） -->
              <el-input-number :model-value="row.personCount ?? undefined" :min="1" :disabled="!isEntertain(b, row)" style="width: 100%" :controls-position="'right'"
                @update:model-value="(v) => setEntertain(row, { personCount: v })" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('expense.claim.colStdPerPerson')" width="100">
            <template #default="{ row }">
              <!-- 人均标准随单入库（ext_json.perCapita），审批人不再只能口述 -->
              <span style="font-size:12px">{{ perCapita(row) != null ? b.fmt(perCapita(row)) : '—' }}</span>
            </template>
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
          <el-table-column :label="$t('common.operation')" width="70">
            <template #default="{ $index }">
              <el-button link size="small" @click="copyLine(b, $index)">{{ $t('expense.common.btnCopy') }}</el-button>
              <el-button link size="small" type="danger" @click="removeLine(b, $index)">{{ $t('expense.common.btnDeleteShort') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="b.form.lines.push(newLine(b))">{{ $t('expense.claim.btnAddExpenseLine') }}</el-button>
        <div style="margin-top: 6px">{{ $t('expense.claim.labFeeTotal') }}<span style="color:#f59a23;font-weight:700">{{ b.fmt(totalAmount(b)) }}</span> {{ $t('expense.claim.txtTaxSuffix', { v1: b.fmt(b.sumTax()) }) }}</div>
      </el-card>

      <!-- 结算信息 -->
      <el-card :header="$t('expense.claim.secSettlement')" style="margin-top: 12px">
<el-form ref="settleFormRef" :model="b.form.bill" :rules="formRules" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colReceiveAmount')">
                <span style="color:#f59a23;font-weight:700">{{ $t('expense.common.amtYuan', { v1: b.fmt(payable(b)) }) }}</span>
                <div style="width:100%;font-size:12px;color:#909399">{{ $t('expense.claim.txtNetPayable', { v1: b.fmt(totalAmount(b)), v2: b.fmt(offsetTotal(b)) }) }}</div>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colPayMethod')" prop="extJson.payMethod">
                <el-select v-model="b.form.bill.extJson.payMethod" style="width: 100%">
                  <el-option v-for="o in payMethodOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colPayeeType')" prop="payTarget">
                <el-select v-model="b.form.bill.payTarget" style="width: 100%" :placeholder="$t('expense.common.phPickPayeeType')">
<el-option v-for="o in payeeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />




                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colAccountName')" prop="extJson.accountName"><el-input v-model="b.form.bill.extJson.accountName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.common.colPayeeAccount')" prop="payeeAccountNo">
                <el-input v-model="b.form.bill.payeeAccountNo" :placeholder="$t('expense.claim.phPayeeAccount')" maxlength="64" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colBank')" prop="extJson.bankName"><el-input v-model="b.form.bill.extJson.bankName" /></el-form-item></el-col>
          </el-row>

          <!-- #18 跨组织四维度（承担/结算/支付/借款）+ 代理报销 分离 -->
          <el-divider content-position="left" style="margin: 4px 0 12px">{{ $t('expense.claim.secCrossOrgProxy') }}</el-divider>
          <el-row :gutter="12">
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colSettleUnit')">
                <el-select v-model="b.form.bill.settleCompanyId" filterable clearable style="width: 100%" :placeholder="$t('expense.claim.phBearingDefault')">
                  <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colPayUnit')">
                <el-select v-model="b.form.bill.payCompanyId" filterable clearable style="width: 100%" :placeholder="$t('expense.claim.colActualPayer')">
                  <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colLoanUnit')">
                <el-select v-model="b.form.bill.loanCompanyId" filterable clearable style="width: 100%" :placeholder="$t('expense.claim.phBearingDefault')">
                  <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
<el-form-item :label="$t('expense.claim.colPayee')">
                <UserSelect v-model="b.form.bill.receiverUserId" :users="users" :placeholder="$t('expense.claim.phPayeeDefault')" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-alert v-if="b.form.bill.receiverUserId && b.form.bill.receiverUserId !== b.form.bill.applyUserId"
            type="warning" :closable="false" show-icon
            :title="$t('expense.claim.tipProxyReimburse', { v1: applyUserName(b) })" style="margin-top: 6px" />
        </el-form>
        <el-alert type="info" :closable="false" :title="$t('expense.claim.hintLoanWriteOff')" style="margin-top: 6px" />
      </el-card>
    </template>
  </BillWorkbench>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ref, computed } from 'vue'
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import BillWorkbench from '../../components/bill/BillWorkbench.vue'
import { userApi, orgApi, masterApi, store, configApi } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useAutoFill } from '@/composables/useAutoFill'
import { billRules } from '@/constants/billRequired'
import { validateForms } from '@/composables/useSubmitGuard'
import { commonRules } from '@/composables/useFormRules'
import { budgetLabel, budgetUnseenLabel, entertainOfLine, formatDateTime } from '@/utils/format'
import { useI18n } from 'vue-i18n'
import { useBaseDict, PAY_METHOD_LOCAL, PAY_METHOD_I18N_CLAIM, PAYEE_TYPE_LOCAL, PAYEE_TYPE_I18N } from '@/composables/useBaseDict'
import { useCurrency } from '@/composables/useCurrency'

const { t } = useI18n()

// 付款方式（基础数据办结：字典定值域 + i18n 定文案）
const payMethodDict = useBaseDict('pay_method', PAY_METHOD_LOCAL, PAY_METHOD_I18N_CLAIM)
const payMethodOptions = payMethodDict.options
const { saveFormHistory } = useAutoFill()

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const wb = ref(null)
const loading = ref(false)
const users = ref([])
const companies = ref([])
const shareType = ref('COST_CENTER')
// 分摊方式恒有默认值（COST_CENTER）——挂 rules 纳入中央框架视野；
// computed 包装只为给 el-form 一个可校验 model，v-model 仍绑 shareType ref 不动
const shareForm = computed(() => ({ shareType: shareType.value }))
const shareTypeRules = { shareType: commonRules.select('请选择分摊方式') }
const currentUserName = ref(store.user?.username || store.user?.userName || t('expense.common.optCurrentUser'))
const years = [new Date().getFullYear() - 1, new Date().getFullYear(), new Date().getFullYear() + 1]

// 表头必填：红星/规则/提交校验同源（constants/billRequired.js），两个 el-form 共用一份 rules
const formRules = computed(() => billRules('CLAIM', ''))
const formRef = ref(null)
const settleFormRef = ref(null)

// 海外报账①：外币行汇率（留空自动带出当月汇率 + 系统价只读提示）
const currencies = ['CNY', 'USD', 'EUR', 'JPY', 'HKD', 'GBP', 'SGD']
function lineCur(row) { return row.currencyCode || 'CNY' }
function isForeignLine(row) { return lineCur(row) !== 'CNY' }
function effRate(row) {
  const r = Number(row.exchangeRate)
  return r > 0 ? r : (row._sysRate || 0)
}
function onCurChange(b, row, cur) {
  row.currencyCode = cur
  if (cur === 'CNY') {
    row.origAmount = null
    row.exchangeRate = null
    row._sysRate = null
    return
  }
  if (row.origAmount == null) row.origAmount = row.amount
  fetchSysRate(b, row)
}
async function fetchSysRate(b, row) {
  row._sysRate = null
  const date = row.expenseDate || b.form.bill.applyDate
  if (!date) return
  try {
    const r = await masterApi.rate.lookup(row.currencyCode, 'CNY', date)
    if (r != null) {
      row._sysRate = Number(r)
      // 汇率留空时仅前端预览本位币金额；保存草稿由后端按表价权威反算
      if (!(Number(row.exchangeRate) > 0) && row.origAmount != null) {
        row.amount = Math.round(row.origAmount * row._sysRate * 100) / 100
      }
    }
  } catch (e) { /* 无权限或表内无价：放行手填 */ }
}
function onOrigAmt(b, row, v) {
  if (isForeignLine(row)) {
    row.origAmount = v
    const r = effRate(row)
    row.amount = r > 0 ? Math.round(v * r * 100) / 100 : v
  } else {
    // 本币行同步回写原币金额与汇率（与后端 saveDraft 兜底形成双侧保证）
    row.amount = v
    row.origAmount = v
    row.exchangeRate = 1
    recalcLine(b, row)
  }
}
function onRateInput(b, row, v) {
  const r = Number(v)
  row.exchangeRate = r > 0 ? r : null
  if (row.origAmount != null) {
    const use = r > 0 ? r : (row._sysRate || 0)
    row.amount = use > 0 ? Math.round(row.origAmount * use * 100) / 100 : row.origAmount
  }
}

function newLine(b) {
  const l = b.newLine()
  l.priceType = b.form.bill.priceType || null
  // 境外凭证合规⑤：凭证形态默认境内发票；选境外收据/行程单时中文说明必填（19号公告）
  l.voucherForm = 'FAPIAO'
  l.cnDesc = ''
  return l
}
function needCnDesc(row) { return row.voucherForm === 'RECEIPT' || row.voucherForm === 'ITINERARY' }
function recalcLine(b, row) {
  if (b.form.bill.priceType) row.priceType = b.form.bill.priceType
  b.recalcLine(row)
  // 金额变了，人均标准（ext_json.perCapita）要跟着重算，不能只在人数输入时刷新
  syncPerCapitaExt(row)
}
// 录入规则变化：表头统一规则，逐行同步 priceType（行不单独选，保存草稿时随每行提交）
function onPriceTypeChange(b, val) {
  b.form.lines.forEach((l) => { l.priceType = val || null })
}
function onItemChange(b, row, id) { b.onItemChange(row, id) }
function copyLine(b, i) {
  const l = b.form.lines[i]
  b.form.lines.push({ ...l, id: null })
}
function isEntertain(b, row) {
  const it = b.items.find((x) => x.id === row.itemId)
  return it && (it.itemName || '').includes('招待')
}

// ───────── 招待三项（接待对象 / 接待人数 / 人均标准）随单入库 ─────────
// 原实现把三项绑在 row.entertainTarget / row.entertainCount / row.entertainType 三个「裸字段」上，
// 后端 ExpBillLine 实体没有这三个同名属性 ⇒ 请求体反序列化时被丢弃 ⇒ exp_bill_line.ext_json={}，
// 详情页三项全空、人均标准校验只能靠审批人口述。
// 现按真实承载写入数据库：接待对象→attendees、接待人数→person_count、人均标准→ext_json.perCapita，
// 三项另冗余写进 ext_json（后端可据此做标准校验）。
// 招待费类型改落真实列 exp_bill_line.entertain_type，ext_json.entertainType 同步写一份以兼容历史查询。
function lineExt(row) {
  if (typeof row.extJson === 'string') {
    try { row.extJson = JSON.parse(row.extJson || '{}') } catch { row.extJson = {} }
  }
  if (!row.extJson || typeof row.extJson !== 'object') row.extJson = {}
  return row.extJson
}
/** 人均标准 = 含税金额 ÷ 接待人数（与 12-09 台账「人均 833.33（6 人）」同一规则） */
function perCapita(row) {
  const n = Number(row.personCount || 0)
  const a = Number(row.amount || 0)
  return n >= 1 && a > 0 ? Math.round((a / n) * 100) / 100 : null
}
function syncPerCapitaExt(row) {
  const ext = lineExt(row)
  const pc = perCapita(row)
  if (pc == null) delete ext.perCapita
  else ext.perCapita = pc
}
function setEntertain(row, patch) {
  if ('attendees' in patch) row.attendees = patch.attendees
  if ('personCount' in patch) row.personCount = patch.personCount
  if ('entertainType' in patch) {
    row.entertainType = patch.entertainType || null
    lineExt(row).entertainType = patch.entertainType || null
  }
  const ext = lineExt(row)
  // ext_json 冗余三项：详情页与服务端标准校验按 ext_json 查询时不必再猜列名
  ext.entertainTarget = row.attendees || null
  ext.entertainCount = row.personCount ?? null
  syncPerCapitaExt(row)
}
// 主数据展示标签：兼容后端 ccCode/ccName（避免选中后因无匹配 option 而回退显示原始 ID 数字）
function ccLabel(c) {
  if (!c) return ''
  return ((c.ccCode || '') + ' ' + (c.ccName || c.costCenterName || '')).trim()
}
function projectLabel(p) {
  if (!p) return ''
  return ((p.projectCode || '') + ' ' + (p.projectName || '')).trim()
}
// 费用日期默认不允许未来；系统参数 expense.claim.futureDateAllowed=1 时放开（缺省/读取失败按不允许）
const futureExpenseDateAllowed = ref(false)
function dayStr(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
function todayStr() { return dayStr(new Date()) }
function blockFutureDate(d) { return !futureExpenseDateAllowed.value && dayStr(d) > todayStr() }
// 行删除确认：只拦「已填内容」的行，空白行直接删
async function removeLine(b, i) {
  const l = b.form.lines[i]
  if (!l) return
  const has = l.itemId != null || Number(l.amount || 0) > 0 || !!b.invOf(l) || !!String(l.remark || '').trim()
  if (has) {
    try {
      await ElMessageBox.confirm(t('expense.common.askDeleteRow'), t('expense.common.confirmDeleteTitle'), { type: 'warning' })
    } catch { return }
  }
  b.form.lines.splice(i, 1)
}
// 修复：发票勾选生成的明细行（可能暂无费用类型 itemId，但金额/税额已自动填入）
// 必须即时计入总金额/费用合计/收款金额，与 useBill.sumAmount 同一规则，否则勾选后合计不动像"无反应"
function totalAmount(b) {
  return b.form.lines.filter((l) => l.itemId || Number(l.amount || 0) > 0).reduce((a, l) => a + Number(l.amount || 0), 0)
}
function offsetTotal(b) {
  return b.form.loanOffsets.reduce((a, o) => a + Number(o.offsetAmount || 0), 0)
}
function payable(b) {
  return Math.max(0, Math.round((totalAmount(b) - offsetTotal(b)) * 100) / 100)
}

/** 收款人姓名（代理报销提示用） */
function applyUserName(b) {
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return u ? (u.nickName || u.userName) : (b.form.bill.applyUserName || '-')
}

async function onBeforeSubmit(b) {
  // 页面级拦截原因同步写 b.submitHint（3 秒 toast 会消失，常驻横幅靠它显示具体原因）
  const warnStop = (msg) => { b.submitHint = msg; ElMessage.warning(msg); return false }
  // 表头 + 结算必填校验（红星同源，见 constants/billRequired.js）
  if (!(await validateForms([formRef, settleFormRef]))) {
    b.submitHint = t('expense.claim.errFormIncomplete') || '表头/结算信息存在未填项，请按表单红色提示补正'
    return false
  }
  // 校验：至少一条费用明细
  if (!b.form.lines.filter((l) => l.itemId).length) {
    return warnStop(t('expense.claim.errNeedExpenseLine'))
  }
  if (!futureExpenseDateAllowed.value) {
    const future = b.form.lines.find((l) => l.expenseDate && l.expenseDate > todayStr())
    if (future) return warnStop(t('expense.claim.errExpenseDateFuture', { v1: future.expenseDate }))
  }
  // 防付错的后端兜底规则（assertPayeeAccount）：payTarget 驱动 payeeAccountNo，已由红星+校验覆盖
  // 收款账号同步到 extJson（兼容既有打印/导出规则）
  if (b.form.bill.payeeAccountNo) b.form.bill.extJson.accountNo = b.form.bill.payeeAccountNo
  // 费用承担：把每行的成本中心/项目/预算科目同步到分摊信息（exp_bill_share 持久化）
  b.form.shares = b.form.lines
    .filter((l) => l.itemId && (l.costCenterId || l.projectId))
    .map((l) => ({
      costCenterId: shareType.value === 'COST_CENTER' ? l.costCenterId : null,
      projectId: shareType.value === 'PROJECT' ? l.projectId : null,
      budgetItemId: l.budgetItemId,
      shareAmount: l.amount || 0,
      shareType: shareType.value,
      remark: l.remark || '',
    }))
  // 逐行必填校验（与表头 * 号对齐）：费用类型/金额>0/预算科目/承担对象；招待费行类型+人数
  for (const l of b.form.lines) {
    if (!l.itemId) continue // 完全空白行忽略，交给下方"至少一条"校验
    if (!(Number(l.amount) > 0)) {
      return warnStop(t('expense.claim.errOrigAmountMissing'))
    }
    if (!l.budgetItemId) {
      return warnStop(t('expense.claim.errBudgetItemRequired'))
    }
    // 承担对象按当前规则校验（单选成本中心或项目）
    if (shareType.value === 'COST_CENTER' && !l.costCenterId) {
      return warnStop(t('expense.claim.errCostCenterRequired'))
    }
    if (shareType.value === 'PROJECT' && !l.projectId) {
      return warnStop(t('expense.claim.errProjectRequired'))
    }
    if (isEntertain(b, l)) {
      // 三项承载＝entertain_type 列 / attendees / person_count
      // 历史草稿类型只在 ext_json 里，提交前回填进列，否则随单丢掉
      if (!l.entertainType && lineExt(l).entertainType) l.entertainType = lineExt(l).entertainType
      if (!l.entertainType) { return warnStop(t('expense.claim.errEntertainType')) }
      if (!(Number(l.personCount) >= 1)) { return warnStop(t('expense.claim.errEntertainHeadcount')) }
      if (!(l.attendees || '').trim()) { return warnStop(t('expense.claim.errEntertainGuests')) }
      syncPerCapitaExt(l)
    }
  }
  // 境外凭证合规⑤：境外收据/行程单必填中文说明（后端 submit 同一规则兜底）
  if (b.form.lines.some((l) => l.itemId && needCnDesc(l) && !(l.cnDesc || '').trim())) {
    return warnStop(t('expense.claim.errAbroadCnDesc'))
  }
  return true
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

async function openRowDetail(row) {
  // 空白/未选费用类型的行双击无意义：给出提示，避免弹出满屏空字段
  if (!row || !row.itemId) {
    ElMessage.info(t('expense.claim.errPickTypeFirst'))
    return
  }
  const b = wb.value && wb.value.b
  const it = (b?.items.find((x) => x.id === row.itemId)?.itemName) || '—'
  const cc = shareType.value === 'COST_CENTER'
    ? ccLabel(b?.costCenters.find((x) => x.id === row.costCenterId))
    : projectLabel(b?.projects.find((x) => x.id === row.projectId))
  const bg = b?.budgets.find((x) => String(x.id) === String(row.budgetItemId))
  const budget = bg ? budgetLabel(b.items, b.depts, b.costCenters, bg, { companies: b.companies, projects: b.projects }) : (budgetUnseenLabel(row.budgetItemId) || '—')
  const lines = [
    [t('expense.common.colExpenseType'), it],
    [t('expense.claim.expCostCenter'), cc || '—'],
    [t('expense.common.colBudgetItem'), budget],
    [t('expense.travelClaim.colExpenseDate'), row.expenseDate || '—'],
    [t('expense.claim.colOrigAmount'), t('expense.common.amtYuan', { v1: b?.fmt ? b.fmt(row.amount) : row.amount })],
    [t('expense.claim.expTaxRate'), row.taxRate != null ? row.taxRate + '%' : '—'],
    [t('field.taxAmount'), (b?.fmt ? b.fmt(row.taxAmount) : row.taxAmount) + ' 元'],
    [t('expense.claim.colExpenseReason'), row.remark || '—'],
    [t('expense.claim.colEntertainType'), { BUSINESS: t('expense.claim.optBusinessBanquet'), WORK: t('expense.claim.optWorkingBanquet') }[entertainOfLine(row).type] || '—'],
    [t('expense.claim.colGuests'), row.attendees || '—'],
    [t('expense.claim.colHeadcount'), row.personCount || '—'],
    [t('expense.claim.colStdPerPerson'), perCapita(row) != null ? t('expense.common.amtPerPerson', { v1: b?.fmt ? b.fmt(perCapita(row)) : perCapita(row) }) : '—'],
    [t('expense.claim.colStdOver'), row.overStd === 1 ? t('expense.claim.txtOverStdPrefix') + (b?.fmt ? b.fmt(row.stdAmount) : row.stdAmount) + '）' : (row.stdAmount != null ? t('expense.claim.stCompliant') : '—')],
  ]
  const items = lines.filter(([, v]) => v && v !== '—')
  if (!items.length) return
  const { h } = await import('vue')
  // Element Plus 契约：alert 的 message 直接传 VNode（{__isVNode, default} 对象会被 JSON.stringify
  // 成一屏节点 JSON——与 format.js showRowDetail 曾踩的同一坑）
  ElMessageBox.alert(
    h('div', { class: 'row-detail' },
      items.map(([k, v]) => h('div', { class: 'row-detail-item' }, [
        h('span', { class: 'rd-key' }, k + '：'),
        h('span', { class: 'rd-val' }, v),
      ]))),
    t('expense.claim.ttlExpenseLines'), { customClass: 'row-detail-dialog' })
}

const route = useRoute()
const router = useRouter()
const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency({ i18nKeys: { CNY: 'expense.common.curCny', USD: 'expense.common.curUsd' }, format: 'name-code' })

const { options: payeeTypeOptions, load: loadPayeeType } = useBaseDict('enum_payee_type', PAYEE_TYPE_LOCAL, PAYEE_TYPE_I18N)

onMounted(async () => {
  // 费用日期未来规则：参数缺失/读取失败一律按「不允许未来」
  configApi.value('expense.claim.futureDateAllowed').then((v) => {
    futureExpenseDateAllowed.value = ['1', 'true', 'TRUE'].includes(String(v == null ? '' : v).trim())
  }).catch(() => {})
  loadPayeeType()
  loadCurrency()
  // 字典不可用时 useBaseDict 自动回落到本地常量，界面与原先逐字一致
  payMethodDict.load()
  // 编辑草稿/驳回单重提：携带 editBillId 深链时载入该单据到表单
  // 不要此处不得回写路由 query：App.vue 按 route.fullPath 给页面挂 :key，改 query＝整页重挂载，
  // 刚载入的表单会被一份空白新建态顶掉（toast 仍说「已载入」，界面却是空单）。
  try {
    const eb = route.query.editBillId
    if (eb && wb.value?.b?.loadBillForEdit) {
      await wb.value.b.loadBillForEdit({ id: Number(eb) })
    }
  } catch (e) { /* 载入失败提示已在 loadBillForEdit 内 */ }
  try {
    // 默认只加载同公司人员，解决性能与安全问题
    const list = (await userApi.page({ pageNum: 1, pageSize: 200, companyId: store.user?.companyId })).list || []
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
    if (b && b.form.bill.title === '') b.form.bill.title = '员工费用报销'
    if (b && !b.form.bill.payTarget) b.form.bill.payTarget = 'EMPLOYEE'
  } catch (e) { /* ignore */ }
  // 跨组织四维度：公司树扁平化，供结算单位/支付单位选择
  try {
    const tree = (await orgApi.companyTree()) || []
    const flat = []
    const walk = (ns) => (ns || []).forEach((n) => {
      flat.push({ id: n.id, name: n.name || n.companyName || n.label })
      walk(n.children)
    })
    walk(tree)
    companies.value = flat
  } catch (e) { /* ignore */ }
})
</script>
