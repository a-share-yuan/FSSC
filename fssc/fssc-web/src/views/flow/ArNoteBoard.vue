<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <el-tabs v-model="tab" style="flex: 1">
          <el-tab-pane label="票据列表" name="list" />
          <el-tab-pane label="到期未托收" name="due" />
        </el-tabs>
        <div class="ops">
          <el-select v-model="q.noteType" size="small" style="width: 110px" placeholder="票据类型" clearable @change="load">
            <el-option label="电子票据" value="ELECTRONIC" />
            <el-option label="纸质票据" value="PAPER" />
          </el-select>
          <el-select v-model="q.status" size="small" style="width: 120px" placeholder="状态" clearable @change="load">
            <el-option v-for="(l, s) in AR_NOTE_STATUS" :key="s" :label="l" :value="s" />
          </el-select>
          <el-input v-model="q.keyword" size="small" style="width: 150px" placeholder="票据编号/出票人/客户" clearable @keyup.enter="load" />
          <el-button size="small" @click="load">刷新</el-button>
          <el-button type="primary" size="small" @click="openRegister">票据登记</el-button>
        </div>
      </div>
    </template>

    <el-table v-if="tab === 'list'" :data="rows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="noteNo" label="票据编号" min-width="150" show-overflow-tooltip />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.noteType === 'PAPER' ? 'warning' : 'success'">{{ row.noteType === 'PAPER' ? '纸质' : '电子' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="drawer" label="出票人" min-width="110" show-overflow-tooltip />
      <el-table-column prop="customerName" label="客户" min-width="110" show-overflow-tooltip />
      <el-table-column prop="amount" label="票面金额" width="105" align="right" />
      <el-table-column prop="dueDate" label="到期日" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="{ REGISTERED: 'warning', IN_ACCOUNT: 'primary', COMPLETED: 'info', VOIDED: 'danger', DISCOUNTED: 'success', ENDORSED: 'success' }[row.status] || 'info'">
            {{ arNoteStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="贴现/托收" min-width="170">
        <template #default="{ row }">
          <span v-if="row.discountAmount != null">贴息 {{ row.discountInterest }}，净额 {{ row.discountAmount }}</span>
          <span v-else-if="row.collectVoucherNo">{{ row.collectVoucherNo }}</span>
          <span v-else>{{ row.voucherNo || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="330" fixed="right">
        <template #default="{ row }">
          <el-button v-for="a in actions(row)" :key="a.key" link type="primary" size="small" @click="act(row, a)">{{ a.label }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-table v-else :data="dueRows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="noteNo" label="票据编号" min-width="150" />
      <el-table-column prop="drawer" label="出票人" min-width="120" show-overflow-tooltip />
      <el-table-column prop="amount" label="票面金额" width="120" align="right" />
      <el-table-column prop="dueDate" label="到期日" width="110" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">{{ arNoteStatusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <!-- 与后端 markDue 同一状态闸：仅「已入账/已保管」可到期提示；数据陈旧时按钮不出现，点了才报错的情况消除 -->
          <el-button v-if="row.status === 'IN_ACCOUNT' || row.status === 'CUSTODY'" link type="primary" size="small" @click="act(row, { key: 'markDue' })">到期提示→待托收</el-button>
          <span v-else style="color:#909399;font-size:12px">当前状态「{{ arNoteStatusLabel(row.status) }}」不可到期提示</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="registerVisible" title="票据登记" width="640px" destroy-on-close>
      <el-form ref="registerFormRef" :model="form" :rules="registerRules" label-width="100px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="票据类型" prop="noteType">
            <el-radio-group v-model="form.noteType">
              <el-radio value="ELECTRONIC">电子票据</el-radio>
              <el-radio value="PAPER">纸质票据</el-radio>
            </el-radio-group>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="票据编号" prop="noteNo"><el-input v-model="form.noteNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="出票人"><el-input v-model="form.drawer" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="承兑人"><el-input v-model="form.acceptor" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="票据金额" prop="amount"><el-input v-model="form.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="出票日"><el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="到期日" prop="dueDate"><el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col v-if="form.noteType === 'PAPER'" :span="24">
            <el-form-item label="影像扫描" prop="imageRef">
              <el-input v-model="form.imageRef" placeholder="纸质票据必须影像扫描（影像批号/文件引用）" />
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="registerVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doRegister">登记（登记后请到「应收服务申请」手工建单）</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="discountVisible" title="汇票贴现（贴现款与利息单独记账）" width="460px" destroy-on-close>
      <el-form label-width="100px" size="small">
        <el-form-item label="贴现利率%"><el-input v-model="discountForm.rate" placeholder="年利率%，如 3.5" /></el-form-item>
        <el-form-item label="贴现日期"><el-date-picker v-model="discountForm.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="discountVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doDiscount">贴现</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="420px" destroy-on-close>
      <el-input v-model="inputText" :placeholder="inputKind === 'void' ? '作废原因（必填）' : '被背书人（银行名称，必填）'" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doInput">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { AR_NOTE_STATUS, arNoteStatusLabel } from '@/constants/enums'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { arGapApi } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'


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
const registerRules = {
  noteType: commonRules.select('请选择票据类型'),
  noteNo: commonRules.required('请输入票据编号'),
  amount: commonRules.amountText('请输入票据金额'),
  dueDate: commonRules.date('请选择到期日'),
  imageRef: commonRules.required('纸质票据必须登记影像扫描（影像批号/文件引用）'),
}
const discountForm = reactive({ rate: '', date: '' })
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')

function actions(row) {
  switch (row.status) {
    case 'REGISTERED':
      return [{ key: 'review', label: '审单入账' }, { key: 'void', label: '作废' }]
    case 'IN_ACCOUNT':
    case 'CUSTODY': {
      const ops = []
      if (row.noteType === 'PAPER' && row.status === 'IN_ACCOUNT') ops.push({ key: 'handover', label: '移交出纳' })
      ops.push({ key: 'endorse', label: '背书转让' }, { key: 'discount', label: '汇票贴现' })
      if (row.dueDate && row.dueDate <= today()) ops.push({ key: 'markDue', label: '到期提示' })
      return ops
    }
    case 'COLLECTING':
      return [{ key: 'collect', label: '托收到账' }]
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
  ElMessage.success(msg || '操作成功')
  await load()
}

function act(row, a) {
  current.value = row
  const id = row.id
  const m = {
    review: () => api(() => arGapApi.noteReview(id), '审单入账成功（借1121/贷1122）'),
    handover: () => api(() => arGapApi.noteHandover(id, ''), '已移交出纳保管'),
    markDue: () => api(() => arGapApi.noteMarkDue(id), '已进入待托收'),
    collect: () => api(() => arGapApi.noteCollect(id), '托收入账成功（借1002/贷1121）'),
  }
  if (m[a.key]) return m[a.key]()
  if (a.key === 'discount') {
    discountForm.rate = ''
    discountForm.date = today()
    discountVisible.value = true
  } else if (a.key === 'endorse') {
    inputKind.value = 'endorse'
    inputTitle.value = '背书转让给银行'
    inputText.value = ''
    inputVisible.value = true
  } else if (a.key === 'void') {
    inputKind.value = 'void'
    inputTitle.value = '票据作废'
    inputText.value = ''
    inputVisible.value = true
  }
}

async function doDiscount() {
  await api(() => arGapApi.noteDiscount(current.value.id, { annualRatePct: discountForm.rate, discountDate: discountForm.date }),
    '贴现成功（贴现款与利息单独记账）')
  discountVisible.value = false
}

async function doInput() {
  // 背书要记被背书人、作废要记原因，两者都是后续追索/审计的依据，留空即无意义
  if (!inputText.value.trim()) {
    return ElMessage.warning(inputKind.value === 'endorse' ? '请填写被背书人（银行名称）' : '请填写作废原因')
  }
  if (inputKind.value === 'endorse') await api(() => arGapApi.noteEndorse(current.value.id, inputText.value), '背书转让成功')
  else await api(() => arGapApi.noteVoid(current.value.id, inputText.value), '已作废')
  inputVisible.value = false
}

function openRegister() {
  Object.keys(form).forEach((k) => delete form[k])
  form.noteType = 'ELECTRONIC'
  registerVisible.value = true
}

async function doRegister() {
  if (!(await validateForms([registerFormRef]))) return
  await api(() => arGapApi.registerNote({ ...form }), '登记成功，待审单')
  registerVisible.value = false
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.ops { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
</style>
