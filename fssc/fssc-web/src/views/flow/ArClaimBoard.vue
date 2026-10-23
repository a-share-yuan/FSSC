<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.claim.pageTitle') }}</span>
        <div class="ops">
          <el-select v-model="q.claimType" size="small" style="width: 150px" :placeholder="$t('flow.request.phAllTypes')" clearable @change="load">
            <el-option v-for="(l, c) in AR_CLAIM_TYPE" :key="c" :label="l" :value="c" />
          </el-select>
          <el-button size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          <el-button type="primary" size="small" @click="openCreate">{{ $t('flow.request.btnNew') }}</el-button>
          <el-upload :show-file-list="false" accept=".xlsx" :auto-upload="false" :on-change="doImportOffsetExcel" style="display:inline-block">
            <el-button type="warning" size="small">{{ $t('flow.claim.offsetExcelImport') }}</el-button>
          </el-upload>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="billNo" :label="$t('flow.request.colApplyNo')" min-width="180" />
      <el-table-column :label="$t('field.type')" width="120">
        <template #default="{ row }"><el-tag size="small" type="info">{{ arClaimTypeLabel(row.claimType) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="customerName" :label="$t('field.customer')" min-width="130" show-overflow-tooltip />
      <el-table-column prop="amount" :label="$t('field.amount')" width="105" align="right" />
      <el-table-column prop="debitAccount" :label="$t('flow.claim.debitAccount')" min-width="120" show-overflow-tooltip />
      <el-table-column prop="buAmount" :label="$t('flow.claim.buAmount')" width="105" align="right" />
      <el-table-column :label="$t('common.status')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="{ POSTED: 'success', COMPLETED: 'info', REJECTED: 'danger', EKP_REVIEW: 'warning' }[row.status] || 'primary'">
            {{ arClaimStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="voucherNo" :label="$t('flow.request.colErpVoucher')" width="150" show-overflow-tooltip />
      <el-table-column :label="$t('common.operation')" width="270" fixed="right">
        <template #default="{ row }">
          <el-button v-for="a in actions(row)" :key="a.key" link type="primary" size="small" @click="act(row, a)">{{ a.label }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" :title="$t('flow.claim.createDialogTitle')" width="660px" destroy-on-close>
      <el-form ref="createFormRef" :model="form" :rules="createRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item :label="$t('field.billType')" prop="claimType">
            <el-select v-model="form.claimType" style="width: 100%">
              <el-option v-for="(l, c) in AR_CLAIM_TYPE" :key="c" :label="l" :value="c" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.claim.company')" prop="companyId">
            <el-tree-select v-model="form.companyId" :data="companyTree" :props="{ label: 'companyName', value: 'id', children: 'children' }"
              check-strictly style="width: 100%" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.customer')">
            <CustomerSelect v-model="form.customerCode" @change="onClaimCustomer" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.claim.applyAmount')" prop="amount"><el-input v-model="form.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.claim.costAmount')" prop="costAmount"><el-input v-model="form.costAmount" :placeholder="$t('flow.claim.customerClaimRequired')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.claim.taxRate')"><el-input v-model="form.taxRate" :placeholder="$t('flow.claim.taxRateHint')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.claim.plant')">
            <el-select v-model="form.factoryCode" filterable clearable style="width: 100%" :placeholder="$t('flow.claim.plantHint')">
              <el-option v-for="p in PLANT_OPTIONS" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.claim.orderNo')" prop="orderNo"><el-input v-model="form.orderNo" :placeholder="$t('flow.claim.unholdRequired')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.claim.debitAmount')" prop="debitAmount"><el-input v-model="form.debitAmount" :placeholder="$t('flow.claim.otherOffsetRequired')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.claim.creditAmount')" prop="creditAmount"><el-input v-model="form.creditAmount" :placeholder="$t('flow.claim.dcBalanceHint')" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="$t('flow.claim.reason')"><el-input v-model="form.reason" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doCreate">{{ $t('field.create') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="acctVisible" :title="$t('flow.claim.supplementDialogTitle')" width="520px" destroy-on-close>
      <el-form :model="acctForm" label-width="100px" size="small">
        <el-form-item :label="$t('flow.claim.debitAccount')" required>
          <el-select v-model="acctForm.debitAccount" filterable style="width: 100%" :placeholder="$t('flow.claim.subjectPickPh')">
            <el-option v-for="s in subjectOptions" :key="s.id" :label="`${s.subjectCode} ${s.subjectName}`" :value="s.subjectCode" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.claim.creditAccount')" required>
          <el-select v-model="acctForm.creditAccount" filterable style="width: 100%" :placeholder="$t('flow.claim.subjectPickPh')">
            <el-option v-for="s in subjectOptions" :key="s.id" :label="`${s.subjectCode} ${s.subjectName}`" :value="s.subjectCode" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.claim.taxAccount')">
          <el-select v-model="acctForm.taxAccount" filterable clearable style="width: 100%" :placeholder="$t('flow.claim.taxAccountPh')">
            <el-option v-for="s in subjectOptions" :key="s.id" :label="`${s.subjectCode} ${s.subjectName}`" :value="s.subjectCode" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="acctVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doSaveAccounts">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" :title="$t('flow.claim.assignDialogTitle')" width="430px" destroy-on-close>
      <el-form label-width="80px" size="small">
        <el-form-item :label="$t('flow.claim.assignUser')" required>
          <UserSelect v-model="assignUserId" :company-id="current?.companyId" show-dept @change="onAssignUser" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="assignVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doAssign">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="420px" destroy-on-close>
      <el-input v-model="inputText" :placeholder="$t('flow.bill.rejectReasonPh')" type="textarea" :rows="2" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doInput">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { AR_CLAIM_TYPE, arClaimTypeLabel, arClaimStatusLabel } from '@/constants/enums'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { arFlowApi, orgApi, store } from '../../api'
import CustomerSelect from '@/components/common/CustomerSelect.vue'
import UserSelect from '@/components/common/UserSelect.vue'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// 厂别取值来自 ArClaimAccounts.buAmount 的口径（00 不生成事业部凭证；2400/2500 折本位币）
const PLANT_OPTIONS = ['00', '2400', '2500']

const rows = ref([])
const loading = ref(false)
const q = reactive({ claimType: '' })
const createVisible = ref(false)
const inputVisible = ref(false)
const current = ref(null)
const form = reactive({})
const createFormRef = ref(null)
const companyTree = ref([])
const subjectOptions = ref([])
const assignVisible = ref(false)
const assignUserId = ref(null)
const assignUserName = ref('')
// 条件必输与后端 ArClaimService.create 同口径：索赔要成本金额、对冲要借贷金额、订单解挂要订单号
const createRules = computed(() => {
  // 局部变量不叫 t：会遮蔽 useI18n() 的取词函数，落地器无法接线
  const ct = form.claimType
  return {
    claimType: commonRules.select(t('flow.request.phDocType')),
    companyId: commonRules.select(t('flow.claim.company')),
    amount: commonRules.amountText(t('flow.claim.applyAmountPh')),
    costAmount: ct === 'CLAIM_OUT' ? commonRules.numberText(t('flow.claim.msgCostRequired')) : [],
    orderNo: ct === 'ORDER_UNFRZ' ? commonRules.required(t('flow.claim.msgOrderNoRequired')) : [],
    debitAmount: ct === 'OTHER_OFFSET' ? commonRules.numberText(t('flow.claim.msgDebitRequired')) : [],
    creditAmount: ct === 'OTHER_OFFSET' ? commonRules.numberText(t('flow.claim.msgCreditRequired')) : [],
  }
})
const inputTitle = ref('')
const inputText = ref('')
const acctVisible = ref(false)
const acctForm = reactive({})

function actions(row) {
  switch (row.status) {
    case 'EKP_REVIEW': return [{ key: 'approve', label: t('flow.claim.ekpApproved') }]
    case 'PENDING_DISPATCH': return [{ key: 'assign', label: t('flow.request.actManualDispatch') }]
    case 'DISPATCHED': return [{ key: 'accounts', label: t('flow.claim.supplementAccounts') },
      { key: 'review', label: t('flow.claim.reviewPost') }, { key: 'reject', label: t('field.reject') }]
    case 'POSTED':
      return row.claimType === 'REVENUE_EST'
        ? [{ key: 'reverse', label: t('flow.claim.reverseAccrual') }, { key: 'complete', label: t('flow.bill.archive') }]
        : [{ key: 'complete', label: t('flow.bill.archive') }]
    default: return []
  }
}

async function load() {
  loading.value = true
  try { rows.value = await arFlowApi.claims({ claimType: q.claimType || undefined }) } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function api(fn, msg) {
  await fn()
  ElMessage.success(msg || t('common.success'))
  await load()
}

async function act(row, a) {
  current.value = row
  const id = row.id
  const m = {
    approve: () => api(() => arFlowApi.claimApprove(id)),
    review: () => api(() => arFlowApi.claimReview(id)),
    reverse: () => api(() => arFlowApi.claimReverse(id)),
    complete: () => api(() => arFlowApi.claimComplete(id)),
  }
  if (m[a.key]) return m[a.key]()
  if (a.key === 'accounts') {
    Object.keys(acctForm).forEach((k) => delete acctForm[k])
    acctForm.debitAccount = row.debitAccount || ''
    acctForm.creditAccount = row.creditAccount || ''
    acctForm.taxAccount = row.taxAccount || ''
    await loadSubjects(row.companyId)
    acctVisible.value = true
    return
  }
  if (a.key === 'assign') {
    assignUserId.value = null
    assignUserName.value = ''
    assignVisible.value = true
  } else if (a.key === 'reject') {
    inputTitle.value = t('field.reject'); inputText.value = ''; inputVisible.value = true
  }
}

async function loadSubjects(companyId) {
  try {
    subjectOptions.value = (await arFlowApi.subjects({ companyId })) || []
  } catch (e) {
    subjectOptions.value = []
    ElMessage.error(e.message)
  }
}

function onClaimCustomer(c) {
  form.customerName = c?.customerName || ''
}

function onAssignUser(id, u) {
  assignUserName.value = u?.nickName || u?.userName || ''
}

async function doAssign() {
  if (!assignUserId.value) return ElMessage.warning(t('flow.claim.msgHandlerRequired'))
  await api(() => arFlowApi.claimAssign(current.value.id, assignUserName.value))
  assignVisible.value = false
}

async function doSaveAccounts() {
  if (!acctForm.debitAccount?.trim() || !acctForm.creditAccount?.trim()) {
    return ElMessage.warning(t('flow.claim.msgAccountRequired'))
  }
  await api(() => arFlowApi.claimAccounts(current.value.id, {
    debitAccount: acctForm.debitAccount, creditAccount: acctForm.creditAccount, taxAccount: acctForm.taxAccount,
  }), t('flow.claim.msgAccountsSaved'))
  acctVisible.value = false
}

async function doInput() {
  // 驳回要留原因（后端 ArClaimService「驳回必须填写原因」同口径）
  if (!inputText.value.trim()) {
    return ElMessage.warning(t('flow.claim.msgRejectReasonRequired'))
  }
  await api(() => arFlowApi.claimReject(current.value.id, inputText.value))
  inputVisible.value = false
}

/** 新建前清空上一次录入，避免带旧内容开单 */
function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  form.companyId = store.user?.companyId
  createVisible.value = true
}

async function doCreate() {
  if (!(await validateForms([createFormRef]))) return
  await api(() => arFlowApi.createClaim({ ...form, companyId: form.companyId || store.user?.companyId }), t('flow.request.msgCreated'))
  createVisible.value = false
}

async function doImportOffsetExcel(uploadFile) {
  const fd = new FormData()
  fd.append('file', uploadFile.raw)
  const res = await arFlowApi.importOffsetExcel(fd)
  ElMessage.success(t('flow.claim.msgOffsetCreated', { v1: res.billNo, v2: res.lines, v3: res.totalDebit }))
  await load()
}

async function loadCompanyTree() {
  try { companyTree.value = (await orgApi.companyTree()) || [] } catch (e) { companyTree.value = [] }
}

onMounted(async () => {
  await Promise.all([load(), loadCompanyTree()])
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
</style>
