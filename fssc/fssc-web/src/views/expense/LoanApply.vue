<template>
  <!-- RA-PAGE-21 2.2：借款/还款双形态并入一个菜单（原 225 还款管理退册）——切换即跳对侧路由，整页重挂重建单据状态 -->
  <el-radio-group :model-value="mode" size="small" style="margin-bottom:8px" @change="onModeSwitch">
    <el-radio-button value="LOAN">{{ $t('expense.loan.tabLoanApply') }}</el-radio-button>
    <el-radio-button value="REPAY">{{ $t('expense.loan.tabRepayRegister') }}</el-radio-button>
  </el-radio-group>
  <BillWorkbench ref="wb" :bill-type="mode === 'LOAN' ? 'LOAN' : 'REPAY'" :sub-type="mode" :before-submit="onBeforeSubmit">
    <template #create="b">
      <!-- ============ 单据概要 ============ -->
      <el-card :header="$t('expense.claim.colBillSummary')">
        <el-form ref="formRef" :rules="formRules" :model="b.form.bill" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colBillNoFull')"><el-input :model-value="b.form.bill.billNo || $t('expense.common.txtAutoAfterSave')" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.loan.colMakeDate')"><el-input :model-value="b.today()" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.claim.colMaker')"><el-input :model-value="currentUserName" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.loan.ctxMakerDept')"><el-input :model-value="applicantDept" disabled /></el-form-item>
            </el-col>
            <el-col v-if="mode === 'REPAY'" :xs="24" :md="12">
              <el-form-item :label="$t('expense.loan.ctxMakerCompany')"><el-input :model-value="applicantCompany" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.common.colCurrency')" prop="currencyCode">
                <el-select v-model="b.form.bill.currencyCode" style="width: 100%">
<el-option v-for="o in currencyOptions" :key="o.value" :label="currencyLabel(o.value, 'name-code')" :value="o.value" />

                  
                  
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="mode === 'REPAY'" :xs="24" :md="12">
              <el-form-item :label="$t('expense.loan.colRepayPerson')" prop="applyUserId">
                <UserSelect v-model="b.form.bill.applyUserId" :users="users" @change="(id) => onRepayUserChange(b, id)" />
              </el-form-item>
            </el-col>
            <el-col v-if="mode === 'REPAY'" :xs="24" :md="12">
              <el-form-item :label="$t('expense.loan.colRepayPersonDept')"><el-input :model-value="repayUserDept" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item :label="$t('expense.common.colTitle')" prop="title">
                <el-input v-model="b.form.bill.title" :placeholder="mode === 'LOAN' ? $t('expense.loan.titleLoanApply') : $t('expense.loan.titleRepayRegister')" />
              </el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item :label="$t('expense.claim.colBillDesc')" prop="remark">
                <el-input v-model="b.form.bill.remark" type="textarea" :rows="2" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- ============ 借款申请：借款详情 ============ -->
      <el-card v-if="mode === 'LOAN'" :header="$t('expense.loan.secLoanDetail')" style="margin-top: 12px">
        <el-form ref="loanFormRef" :model="b.form.bill.extJson" :rules="loanRules" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.loan.colLoanType')" prop="loanType">
                <el-select v-model="b.form.bill.extJson.loanType" style="width: 100%">
                  <el-option :label="$t('expense.ledger.optLoan')" value="LOAN" />
                  <el-option :label="$t('expense.loan.optPettyCash')" value="PETTY" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.loan.colLoanAmount')" prop="loanAmount">
                <el-input-number v-model="b.form.bill.extJson.loanAmount" :min="0" :precision="2" style="width: 100%" @change="syncLoanLine(b)" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item :label="$t('expense.claim.colPayMethod')" prop="payMethod">
                <el-select v-model="b.form.bill.extJson.payMethod" style="width: 100%">
                  <el-option v-for="o in payMethodOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.travelClaim.colAccountType')"><el-input :model-value="'员工'" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colAccountName')" prop="accountName"><el-input v-model="b.form.bill.extJson.accountName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colAccountNo')" prop="accountNo"><el-input v-model="b.form.bill.extJson.accountNo" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colBank')" prop="bankName"><el-input v-model="b.form.bill.extJson.bankName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.loan.colExpectedRepayDate')"><el-date-picker v-model="b.form.bill.extJson.expectRepayDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
            <el-col :xs="24"><el-form-item :label="$t('expense.loan.colLoanRemark')"><el-input v-model="b.form.bill.extJson.loanRemark" type="textarea" :rows="2" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- ============ 借款申请：未冲销详情 ============ -->
      <el-card v-if="mode === 'LOAN'" :header="$t('expense.loan.secOpenLoans')" style="margin-top: 12px">
        <div style="margin-bottom: 8px">
      <el-button type="info" size="small" @click="exportCsv">{{ $t('expense.common.btnExportCsv') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('expense.common.btnExportSelected', { v1: selRows.length }) }}</el-button>
    </div>
    <el-table :data="pagedData" size="small" border :empty-text="$t('expense.loan.emptyNoOpenLoans')" v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="billNo" :label="$t('field.docNo')" width="160"  sortable />
          <el-table-column :label="$t('common.status')" width="100">
            <template #default="{ row }"><el-tag size="small" type="warning">{{ row.loanBalance > 0 ? $t('expense.loan.stInFlight') : $t('expense.loan.stNotWriteOff') }}</el-tag></template>
          </el-table-column>
          <el-table-column :label="$t('expense.loan.colLoanAmount')" width="110"  sortable>
            <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column prop="loanBalance" :label="$t('expense.loan.colNotWriteOffAmount')" width="110"  sortable :formatter="(r,c,v) => formatMoney(v)" />
        </el-table>
        <TablePager :pager="pager" @change="loadOpenLoans" />
      </el-card>

      <!-- ============ 还款申请：还款详情 ============ -->
      <el-card v-if="mode === 'REPAY'" :header="$t('expense.loan.secRepayDetail')" style="margin-top: 12px">
        <el-button type="primary" size="small" @click="openRepayLoan">{{ $t('expense.loan.btnPickLoanWriteOff') }}</el-button>
        <el-table :data="b.form.loanOffsets" size="small" border style="margin-top: 8px" v-loading="loading">
          <el-table-column prop="loanBillNo" :label="$t('expense.claim.colBillNoFull')" width="160"  sortable />
          <el-table-column :label="$t('field.billType')" width="110">
            <template #default="{ row }"><el-tag size="small">{{ row.sourceType === 'APPLY' ? t('expense.loan.optRequisitionBill') : t('expense.loan.optLoanBill') }}</el-tag></template>
          </el-table-column>
          <el-table-column :label="$t('expense.loan.colLoanRemark')" min-width="140">
            <template #default="{ row }"><el-input v-model="row.loanRemark" size="small" /></template>
          </el-table-column>
          <el-table-column :label="$t('expense.loan.colRepayAmount')" width="150"><template #header><span style="color:#f56c6c">*</span>{{ $t('expense.loan.colRepayAmount') }}</template>
            <template #default="{ row }"><el-input-number v-model="row.offsetAmount" :min="0" :max="row.maxAmount || undefined" :precision="2" style="width: 100%" @change="syncRepayLine(b)" /></template>
          </el-table-column>
          <el-table-column :label="$t('common.operation')" width="70">
            <template #default="{ $index }"><el-button link size="small" type="danger" @click="removeOffset(b, $index)">{{ $t('expense.common.btnDeleteShort') }}</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- ============ 还款申请：公司收款账号 ============ -->
      <el-card v-if="mode === 'REPAY'" :header="$t('expense.loan.secCompanyBankInfo')" style="margin-top: 12px">
        <el-form ref="companyFormRef" :model="b.form.bill.extJson" :rules="companyRules" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.loan.colAccountHolder')" prop="companyAccountName"><el-input v-model="b.form.bill.extJson.companyAccountName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colPayeeAccount')" prop="companyAccountNo"><el-input v-model="b.form.bill.extJson.companyAccountNo" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item :label="$t('expense.common.colBank')" prop="companyBankName"><el-input v-model="b.form.bill.extJson.companyBankName" /></el-form-item></el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="$t('expense.loan.colRepayMethod')" prop="repayMethod">
                <el-select v-model="b.form.bill.extJson.repayMethod" style="width: 100%">
                  <el-option v-for="o in payMethodOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>
    </template>
  </BillWorkbench>

  <!-- 还款：选择借款单弹窗（D-UI-04：勾选+可改金额+【确定】，取代行点即全额冲抵并关窗） -->
  <CandidatePickerDialog v-model="repayDialog" :title="$t('expense.loan.dlgPickLoanWriteOff')"
                        :rows="b_loanCandidates" :loading="loading" max-field="availableBalance"
                        extra-prop="title" :extra-label="$t('expense.common.colTitle')"
                        :amount-label="$t('expense.loan.colLoanAmount')"
                        max-label="可冲抵余额"
                        :input-label="$t('expense.loan.colRepayAmount')"
                        :picked-ids="loanOffsetIds"
                        @confirm="confirmRepayLoans" />
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { ref, computed, watch } from 'vue'
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BillWorkbench from '../../components/bill/BillWorkbench.vue'
import CandidatePickerDialog from '../../components/bill/CandidatePickerDialog.vue'
import { userApi, billApi, detailApi, store } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useAutoFill } from '@/composables/useAutoFill'
import { billRules } from '@/constants/billRequired'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'
import { useBaseDict, PAY_METHOD_LOCAL, PAY_METHOD_I18N_CLAIM } from '@/composables/useBaseDict'
import { useCurrency } from '@/composables/useCurrency'

const { t } = useI18n()

// 付款方式 / 偿还方式（2026-10-18 基础数据收口：二者同属 pay_method 字典）
const payMethodDict = useBaseDict('pay_method', PAY_METHOD_LOCAL, PAY_METHOD_I18N_CLAIM)
const payMethodOptions = payMethodDict.options
const { saveFormHistory } = useAutoFill()
// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const props = defineProps({
  mode: { type: String, default: 'LOAN' },
})
const selRows = ref([])
const loading = ref(false)
const wb = ref(null)
const users = ref([])
const openLoans = ref([])

// ---------- 表头必填：通用字段走登记表（红星/闸门同源），单据说明本页加严 ----------
const formRules = computed(() => ({
  ...billRules(props.mode === 'LOAN' ? 'LOAN' : 'REPAY', ''),
  // 还款单上这个字段是「还款人」，提示语随场景改口
  applyUserId: [{ required: true, message: t('expense.loan.errRepayPersonRequired') }],
  remark: commonRules.required(t('expense.loan.errBillDescRequired')),
}))
const loanRules = computed(() => ({
  loanType: [{ required: true, message: t('expense.loan.errLoanTypeRequired') }],
  loanAmount: [{ required: true, type: 'number', min: 0.01, message: t('expense.loan.errLoanAmountRequired'), trigger: 'change' }],
  payMethod: [{ required: true, message: t('expense.loan.errPayMethodRequired') }],
  accountName: [{ required: true, message: t('expense.loan.errAccountNameRequired') }],
  accountNo: [{ required: true, message: t('expense.loan.errAccountRequired') }],
  bankName: [{ required: true, message: t('expense.loan.errBankRequired') }],
}))
const companyRules = computed(() => ({
  companyAccountName: [{ required: true, message: t('expense.loan.errAccountHolderRequired') }],
  companyAccountNo: [{ required: true, message: t('expense.loan.errPayeeAccountRequired') }],
  companyBankName: [{ required: true, message: t('expense.loan.errBankRequired') }],
  repayMethod: [{ required: true, message: t('expense.loan.errRepayMethodRequired') }],
}))
const formRef = ref(null)
const loanFormRef = ref(null)
const companyFormRef = ref(null)
// P0 遗留专项：myPage 支持 minBalance 服务端过滤，未冲销借款列表切 server 模式
const pager = useTablePager({ mode: 'server', storageKey: 'loan_apply' })
const { pagedData } = pager
const currentUserName = ref(store.user?.username || store.user?.userName || t('expense.common.optCurrentUser'))
const repayDialog = ref(false)
const b_loanCandidates = ref([])

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
const repayUserDept = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.deptName || u.departmentName)) || '—'
})

function loanAmount(b) { return b.form.bill.extJson.loanAmount || 0 }
function syncLoanLine(b) {
  ensureLine(b)
  b.form.lines[0].amount = Math.round(Number(b.form.bill.extJson.loanAmount || 0) * 100) / 100
  b.form.lines[0].itemId = loanItemId(b)
  b.form.bill.totalAmount = b.form.lines[0].amount
}
function syncRepayLine(b) {
  ensureLine(b)
  const total = b.form.loanOffsets.reduce((a, o) => a + Number(o.offsetAmount || 0), 0)
  b.form.lines[0].amount = Math.round(total * 100) / 100
  b.form.lines[0].itemId = loanItemId(b)
}
/** 已入账的借款单 id —— 选择器里置灰，防同一张借款单重复入账（D-UI-04） */
const loanOffsetIds = computed(() => (wb.value?.b?.form?.loanOffsets || []).map((o) => o.loanBillId))
function loanItemId(b) {
  const it = b.items.find((x) => (x.itemName || '').includes('借款') || (x.itemName || '').includes('备用'))
  return it ? it.id : (b.items[0] ? b.items[0].id : null)
}
function ensureLine(b) {
  if (!b.form.lines.length) b.form.lines.push(b.newLine())
}
// D-TR-02 同口径：冲销清单双向同步 extJson.loanOffsets——草稿即存，编辑重提回读回填
// （此前只在提交时写入，重开还款草稿冲销行整块丢失）
watch(() => {
  const b = wb.value && wb.value.b
  return b && b.form.loanOffsets
}, () => {
  const b = wb.value && wb.value.b
  if (!b || props.mode !== 'REPAY') return
  const ext = b.form.bill.extJson
  if (JSON.stringify(ext.loanOffsets || []) !== JSON.stringify(b.form.loanOffsets)) {
    ext.loanOffsets = b.form.loanOffsets.map((o) => ({ ...o }))
  }
}, { deep: true })
watch(() => {
  const b = wb.value && wb.value.b
  return b && b.form.bill.extJson.loanOffsets
}, (v) => {
  const b = wb.value && wb.value.b
  if (!b || props.mode !== 'REPAY' || !Array.isArray(v)) return
  if (JSON.stringify(b.form.loanOffsets) !== JSON.stringify(v)) b.form.loanOffsets = v.map((o) => ({ ...o }))
}, { deep: true })
async function onBeforeSubmit(b) {
  // 表头必填闸门：提示具体漏填项并滚动定位
  if (!(await validateForms([formRef]))) return false
  if (props.mode === 'LOAN') {
    syncLoanLine(b)
    b.form.bill.expectRepayDate = b.form.bill.extJson.expectRepayDate || null
    // P1-1 借款详情必填（类型/金额/收款方式/账户名称/账号/开户行）
    if (!(await validateForms([loanFormRef]))) return false
  } else {
    syncRepayLine(b)
    b.form.bill.extJson.loanOffsets = b.form.loanOffsets.map((o) => ({ ...o }))
    // P1-1 还款：至少一条冲销记录
    if (!b.form.loanOffsets.length) {
      ElMessage.warning(t('expense.loan.errNeedLoanPick')); return false
    }
    // P1-1 还款：每笔冲销金额 > 0
    if (b.form.loanOffsets.some((o) => !(Number(o.offsetAmount) > 0))) {
      ElMessage.warning(t('expense.loan.errRepayPositive')); return false
    }
    // P1-1 公司收款账号必填（户名/账号/开户行/还款方式）
    if (!(await validateForms([companyFormRef]))) return false
  }
  return true
}
async function removeOffset(b, i) {
  try {
    await ElMessageBox.confirm(t('expense.common.askDeleteRow'), t('expense.common.confirmDeleteTitle'), { type: 'warning' })
  } catch { return }
  b.form.loanOffsets.splice(i, 1)
  syncRepayLine(b)
}

/**
 * D-UI-03（2026-10-19）：候选面改走 /loan-candidates，一次查询取回。
 *
 * <p>此前这里按 {@code APPROVED／PARTIAL_REPAID／REPAID} 三次 myPage 再合并去重
 * （D-DEC-396 的历史补丁：借款被在途冲抵会迁 PARTIAL_REPAID，单查 APPROVED 会失明）。
 * 新接口在服务端一次取齐三态、并已把 {@code loan_balance ≤ 0} 的滤掉，三分支拼装随之删除。</p>
 *
 * <p>更要紧的是它给的是 {@code availableBalance}（账面余额 − <b>在途占用</b>）：
 * 旧口径只给账面余额，前端默认勾全额必然撞服务端 {@code registerLoanOccupancy} 的超额闸
 * （即 {@code ExpenseDetailService} 类注释里登记的「已知在册残口①」）。</p>
 */
async function openRepayLoan() {
  //  本函数原先直接写 `b.form.bill.companyId`，但脚本作用域里并没有裸的 `b`
  //   （b 是 BillWorkbench 经 ref="wb" 暴露出来的，只能用 `wb.value && wb.value.b`
  //     取得 —— 本文件其余 12 处函数都是这么写的）。
  //   结果点按钮即抛 ReferenceError，loan-candidates 请求根本没发出、repayDialog
  //   也永不置 true，表现为「点了『选择借款单冲销』毫无反应」。
  const b = wb.value && wb.value.b
  if (!b || !b.form || !b.form.bill) { ElMessage.warning('单据尚未初始化，请稍后重试'); return }
  // 另外：新建还款单时 companyId 还是空值，原样传会被序列化成字符串 "null"，
  // 后端 @RequestParam Long companyId 转换失败返回 400，await 抛错同样会把后半段吞掉。
  // 故只在拿到有效 companyId 时才带该参数（后端 required=false，可省略）。
  const params = { limit: 50 }
  const cid = b.form.bill.companyId
  if (cid !== null && cid !== undefined && cid !== '') params.companyId = cid
  b_loanCandidates.value = (await detailApi.loanCandidates(params)) || []
  repayDialog.value = true
}
/**
 * D-UI-04（2026-10-19）：【确定】后才入账，且金额以对话框里改过的值为准（支持部分冲抵）。
 * 改前是点任意行立刻按未还余额全额冲抵并关窗——误点即产生财务动作、无确认、金额不可调。
 */
function confirmRepayLoans(items) {
  const b = wb.value && wb.value.b
  if (!b) return
  let added = 0
  for (const { row, amount } of items || []) {
    const exist = b.form.loanOffsets.find((o) => o.loanBillId === row.id)
    if (exist) {
      exist.offsetAmount = amount
    } else {
      b.form.loanOffsets.push({
        loanBillId: row.id, loanBillNo: row.billNo, offsetAmount: amount,
        maxAmount: Number(row.loanBalance || 0),
        offsetType: 'REPAY', sourceType: 'LOAN', loanRemark: row.title || '',
      })
      added++
    }
  }
  if (added) ElMessage.success(t('expense.loan.msgLoanWriteOffAdded', { v: added }))
  syncRepayLine(b)
}
function onRepayUserChange(b, id) {
  const u = users.value.find((x) => x.id === id)
  if (u) b.form.bill.applyUserName = u.nickName || u.userName
}

/** P0 遗留专项：未冲销借款列表（服务端分页 + loan_balance>0 过滤） */
async function loadOpenLoans() {
  await pager.load(async ({ pageNum, pageSize }) => {
    const r = await billApi.myPage({ pageNum, pageSize, billType: 'LOAN', status: 'APPROVED', minBalance: 0 })
    openLoans.value = r.list || []
    return { list: openLoans.value, total: r.total || 0 }
  })
}

const route = useRoute()
const router = useRouter()
// 借款⇄还款切换（RA-PAGE-21 2.2）
function onModeSwitch(v) {
  if (v === props.mode) return
  router.push(v === 'REPAY' ? '/expense/repay' : '/expense/loan')
}
const { options: currencyOptions, load: loadCurrency, formatLabel: currencyLabel } = useCurrency({ i18nKeys: { CNY: 'expense.common.curCny', USD: 'expense.common.curUsd' }, format: 'name-code' })

onMounted(async () => {
  loadCurrency()
  // 字典不可用时 useBaseDict 自动回落到本地常量，界面与改造前逐字一致
  payMethodDict.load()
  // 编辑草稿/驳回单重提：携带 editBillId 深链时载入该单据到表单（LOAN/REPAY 同组件，按单据实际类型载入）
  try {
    const eb = route.query.editBillId
    if (eb && wb.value?.b?.loadBillForEdit) {
      await wb.value.b.loadBillForEdit({ id: Number(eb) })
      router.replace({ query: {} })
    }
  } catch (e) { /* 载入失败提示已在 loadBillForEdit 内 */ }
  loading.value = true
  try {
    const list = (await userApi.page({ pageNum: 1, pageSize: 200 })).list || []
    users.value = list
    currentUserName.value = store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || t('expense.common.optCurrentUser')
    const b = wb.value && wb.value.b
    const uid = store.user?.userId ?? store.user?.id
    if (b && !b.form.bill.applyUserId) {
      // P1-5 默认申请人 = 当前登录人，严格匹配 me.id，不回退 list[0]
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
    if (props.mode === 'LOAN') {
      await loadOpenLoans()
    }
  } catch (e) { /* ignore */ } finally { loading.value = false }
})

function exportCsv() {
  if (!openLoans.value.length) { ElMessage.warning(t('expense.loan.msgNoDataToExport')); return }
  const cols = [{ prop: 'billNo', label: t('field.docNo') }, { prop: 'totalAmount', label: t('expense.loan.colLoanAmount') }, { prop: 'loanBalance', label: t('expense.loan.colNotWriteOffAmount') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = openLoans.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `LoanApply_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('expense.loan.msgExportedCsv'))
}


function handleSelectionChange(val) { selRows.value = val }

function formatMoney(val) {
  return Number(val || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('expense.common.errPickExportRows')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('expense.loan.msgNoColumns')); return }
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
  a.download = `LoanApply_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('expense.common.msgExportedRows', { v1: selRows.value.length }))
}

</script>
