<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <el-tabs v-model="tab" style="flex: 1">
          <el-tab-pane :label="$t('flow.note.titleNoteList')" name="list" />
          <el-tab-pane :label="$t('flow.note.tabDueUncollected')" name="due" />
        </el-tabs>
        <div class="ops">
          <el-select v-model="q.noteType" size="small" style="width: 110px" :placeholder="$t('flow.report.colNoteType')" clearable @change="load">
            <el-option :label="$t('flow.report.noteElectronic')" value="ELECTRONIC" />
            <el-option :label="$t('flow.report.notePaper')" value="PAPER" />
          </el-select>
          <el-select v-model="q.status" size="small" style="width: 120px" :placeholder="$t('common.status')" clearable @change="load">
            <el-option v-for="(l, s) in AR_NOTE_STATUS" :key="s" :label="l" :value="s" />
          </el-select>
          <el-input v-model="q.keyword" size="small" style="width: 150px" :placeholder="$t('flow.note.phSearch')" clearable @keyup.enter="load" />
          <el-button size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          <el-button type="primary" size="small" @click="openRegister">{{ $t('flow.note.btnNoteRegister') }}</el-button>
        </div>
      </div>
    </template>

    <el-table v-if="tab === 'list'" :data="rows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="noteNo" :label="$t('flow.note.colNoteNo')" min-width="150" show-overflow-tooltip />
      <el-table-column :label="$t('field.type')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.noteType === 'PAPER' ? 'warning' : 'success'">{{ row.noteType === 'PAPER' ? t('flow.note.valPaper') : t('flow.note.valElectronic') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="drawer" :label="$t('flow.note.colDrawer')" min-width="110" show-overflow-tooltip />
      <el-table-column prop="customerName" :label="$t('field.customer')" min-width="110" show-overflow-tooltip />
      <el-table-column prop="amount" :label="$t('flow.note.colFaceAmount')" width="105" align="right" />
      <el-table-column prop="dueDate" :label="$t('flow.note.colDueDate')" width="100" />
      <el-table-column :label="$t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="{ REGISTERED: 'warning', IN_ACCOUNT: 'primary', COMPLETED: 'info', VOIDED: 'danger', DISCOUNTED: 'success', ENDORSED: 'success' }[row.status] || 'info'">
            {{ arNoteStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('flow.note.colDiscountCollect')" min-width="170">
        <template #default="{ row }">
          <span v-if="row.discountAmount != null">{{ $t('flow.note.textDiscountInfo', { v1: row.discountInterest, v2: row.discountAmount }) }}</span>
          <span v-else-if="row.collectVoucherNo">{{ row.collectVoucherNo }}</span>
          <span v-else>{{ row.voucherNo || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="330" fixed="right">
        <template #default="{ row }">
          <el-button v-for="a in actions(row)" :key="a.key" link type="primary" size="small" @click="act(row, a)">{{ a.label }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-table v-else :data="dueRows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="noteNo" :label="$t('flow.note.colNoteNo')" min-width="150" />
      <el-table-column prop="drawer" :label="$t('flow.note.colDrawer')" min-width="120" show-overflow-tooltip />
      <el-table-column prop="amount" :label="$t('flow.note.colFaceAmount')" width="120" align="right" />
      <el-table-column prop="dueDate" :label="$t('flow.note.colDueDate')" width="110" />
      <el-table-column :label="$t('common.status')" width="120">
        <template #default="{ row }">{{ arNoteStatusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="160" fixed="right">
        <template #default="{ row }">
          <!-- 与后端 markDue 同一状态校验：仅「已入账/已保管」可到期提示；数据陈旧时按钮不出现，点了才报错的情况消除 -->
          <el-button v-if="row.status === 'IN_ACCOUNT' || row.status === 'CUSTODY'" link type="primary" size="small" @click="act(row, { key: 'markDue' })">{{ $t('flow.note.btnMarkDue') }}</el-button>
          <span v-else style="color:#909399;font-size:12px">{{ $t('flow.note.hintStatusNoPresent', { v1: arNoteStatusLabel(row.status) }) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="registerVisible" :title="$t('flow.note.btnNoteRegister')" width="640px" destroy-on-close>
      <el-form ref="registerFormRef" :model="form" :rules="registerRules" label-width="100px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item :label="$t('flow.report.colNoteType')" prop="noteType">
            <el-radio-group v-model="form.noteType">
              <el-radio value="ELECTRONIC">{{ $t('flow.report.noteElectronic') }}</el-radio>
              <el-radio value="PAPER">{{ $t('flow.report.notePaper') }}</el-radio>
            </el-radio-group>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.note.colNoteNo')" prop="noteNo"><el-input v-model="form.noteNo" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.note.colDrawer')"><el-input v-model="form.drawer" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.note.colAcceptor')"><el-input v-model="form.acceptor" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('field.customer')">
            <CustomerSelect v-model="form.customerCode" :placeholder="$t('flow.note.phPickCustomer')" @change="onNoteCustomer" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.note.formNoteAmount')" prop="amount"><el-input v-model="form.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.note.colIssueDate')"><el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="$t('flow.note.colDueDate')" prop="dueDate"><el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col v-if="form.noteType === 'PAPER'" :span="24">
            <el-form-item :label="$t('flow.note.formImageScan')" prop="imageRef">
              <el-input v-model="form.imageRef" :placeholder="$t('flow.note.phImageScan')" />
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item :label="$t('common.remark')"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="registerVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doRegister">{{ $t('flow.note.btnRegisterConfirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="discountVisible" :title="$t('flow.note.dlgDiscount')" width="460px" destroy-on-close>
      <el-form ref="discountFormRef" :model="discountForm" :rules="discountRules" label-width="100px" size="small">
        <el-form-item :label="$t('flow.note.formDiscountRate')" prop="rate"><el-input v-model="discountForm.rate" :placeholder="$t('flow.note.phDiscountRate')" /></el-form-item>
        <el-form-item :label="$t('flow.note.formDiscountDate')" prop="date"><el-date-picker v-model="discountForm.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="discountVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doDiscount">{{ $t('flow.note.btnDiscount') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="handoverVisible" :title="$t('flow.note.opHandoverCashier')" width="430px" destroy-on-close>
      <el-form label-width="90px" size="small">
        <el-form-item :label="$t('flow.note.phHandoverKeeper')" required>
          <UserSelect v-model="handoverUserId" :company-id="current?.companyId" show-dept @change="onHandoverUser" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="handoverVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doHandover">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="420px" destroy-on-close>
      <el-input v-model="inputText" :placeholder="inputKind === 'void' ? $t('flow.note.phVoidReasonRequired') : $t('flow.note.phEndorsee')" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doInput">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { AR_NOTE_STATUS, arNoteStatusLabel } from '@/constants/enums'
import {onMounted, reactive, ref, computed} from 'vue'
import { ElMessage } from 'element-plus'
import { arGapApi } from '../../api'
import CustomerSelect from '@/components/common/CustomerSelect.vue'
import UserSelect from '@/components/common/UserSelect.vue'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()


const tab = ref('list')
const rows = ref([])
const dueRows = ref([])
const loading = ref(false)
const q = reactive({ noteType: '', status: '', keyword: '' })
const registerVisible = ref(false)
const discountVisible = ref(false)
const inputVisible = ref(false)
const current = ref(null)
const form = reactive({ noteType: 'ELECTRONIC' })
const registerFormRef = ref(null)
// 票据编号是银行票据自身的号码（外部单号，人工按票面录入），与"系统取号"的单据编号不是一类，故保留必输
const registerRules = computed(() => ({
  noteType: commonRules.select(t('flow.note.errTypeRequired')),
  noteNo: commonRules.required(t('flow.note.errNoteNoRequired')),
  amount: commonRules.amountText(t('flow.note.errAmountRequired')),
  dueDate: commonRules.date(t('flow.note.errDueDateRequired')),
  imageRef: commonRules.required(t('flow.note.errImageScanRequired')),
}))
const discountForm = reactive({ rate: '', date: '' })
// 票据贴现弹窗（原无任何校验）——年化贴现率必须是 0-100 的数字、贴现日必选
const discountFormRef = ref(null)
const discountRules = {
  rate: [...commonRules.required('请输入年化贴现率'),
    { validator: (rule, value, cb) => { const n = Number(value); (!isFinite(n) || n < 0 || n > 100) ? cb(new Error('贴现率应为 0-100 之间的数字（百分比）')) : cb() } }],
  date: commonRules.date('请选择贴现日'),
}
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')
const handoverVisible = ref(false)
const handoverUserId = ref(null)
const handoverUserName = ref('')

function actions(row) {
  switch (row.status) {
    case 'REGISTERED':
      return [{ key: 'review', label: t('flow.note.opReviewPost') }, { key: 'void', label: t('field.void') }]
    case 'IN_ACCOUNT':
    case 'CUSTODY': {
      const ops = []
      if (row.noteType === 'PAPER' && row.status === 'IN_ACCOUNT') ops.push({ key: 'handover', label: t('flow.note.opHandoverCashier') })
      ops.push({ key: 'endorse', label: t('flow.note.opEndorse') }, { key: 'discount', label: t('flow.note.opDiscountBill') })
      if (row.dueDate && row.dueDate <= today()) ops.push({ key: 'markDue', label: t('flow.note.opMarkDue') })
      return ops
    }
    case 'COLLECTING':
      return [{ key: 'collect', label: t('flow.note.opCollect') }]
    default:
      return []
  }
}

function today() {
  return new Date().toISOString().slice(0, 10)
}

async function load() {
  loading.value = true
  try {
    rows.value = await arGapApi.notes({ noteType: q.noteType || undefined, status: q.status || undefined, keyword: q.keyword || undefined })
    dueRows.value = await arGapApi.dueNotes()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function api(fn, msg) {
  await fn()
  ElMessage.success(msg || t('common.success'))
  await load()
}

function act(row, a) {
  current.value = row
  const id = row.id
  const m = {
    review: () => api(() => arGapApi.noteReview(id), t('flow.note.msgReviewedOk')),
    markDue: () => api(() => arGapApi.noteMarkDue(id), t('flow.note.msgInToCollect')),
    collect: () => api(() => arGapApi.noteCollect(id), t('flow.note.msgCollectedOk')),
  }
  if (m[a.key]) return m[a.key]()
  if (a.key === 'handover') {
    handoverUserId.value = null
    handoverUserName.value = ''
    handoverVisible.value = true
  } else if (a.key === 'discount') {
    discountForm.rate = ''
    discountForm.date = today()
    discountVisible.value = true
  } else if (a.key === 'endorse') {
    inputKind.value = 'endorse'
    inputTitle.value = t('flow.note.dlgEndorseBank')
    inputText.value = ''
    inputVisible.value = true
  } else if (a.key === 'void') {
    inputKind.value = 'void'
    inputTitle.value = t('flow.note.dlgVoidNote')
    inputText.value = ''
    inputVisible.value = true
  }
}

async function doDiscount() {
  if (!(await validateForms([discountFormRef]))) return
  await api(() => arGapApi.noteDiscount(current.value.id, { annualRatePct: discountForm.rate, discountDate: discountForm.date }),
    t('flow.note.msgDiscountOk'))
  discountVisible.value = false
}

function onNoteCustomer(c) {
  form.customerName = c?.customerName || ''
}

function onHandoverUser(id, u) {
  handoverUserName.value = u?.nickName || u?.userName || ''
}

async function doHandover() {
  if (!handoverUserId.value) return ElMessage.warning(t('flow.note.errKeeperRequired'))
  await api(() => arGapApi.noteHandover(current.value.id, handoverUserName.value), t('flow.note.msgHandedOver'))
  handoverVisible.value = false
}

async function doInput() {
  // 背书要记被背书人、作废要记原因，两者都是后续追索/审计的依据，留空即无意义
  if (!inputText.value.trim()) {
    return ElMessage.warning(inputKind.value === 'endorse' ? t('flow.note.errEndorseeRequired') : t('flow.request.errVoidReason'))
  }
  if (inputKind.value === 'endorse') await api(() => arGapApi.noteEndorse(current.value.id, inputText.value), t('flow.note.msgEndorsedOk'))
  else await api(() => arGapApi.noteVoid(current.value.id, inputText.value), t('flow.collab.stVoided'))
  inputVisible.value = false
}

function openRegister() {
  Object.keys(form).forEach((k) => delete form[k])
  form.noteType = 'ELECTRONIC'
  registerVisible.value = true
}

async function doRegister() {
  if (!(await validateForms([registerFormRef]))) return
  await api(() => arGapApi.registerNote({ ...form }), t('flow.note.msgRegisteredPending'))
  registerVisible.value = false
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.ops { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
</style>
