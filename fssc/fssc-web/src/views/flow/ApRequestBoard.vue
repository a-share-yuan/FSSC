<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>付款服务申请</span>
        <div class="ops">
          <el-select v-model="q.reqType" size="small" style="width: 140px" placeholder="全部类型" clearable @change="load">
            <el-option v-for="(l, c) in AP_REQ_TYPE" :key="c" :label="l" :value="c" />
          </el-select>
          <el-select v-model="q.status" size="small" style="width: 110px" placeholder="状态" clearable @change="load">
            <el-option v-for="(l, c) in AP_REQ_STATUS" :key="c" :label="l" :value="c" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button type="primary" size="small" @click="openCreate">新建申请</el-button>
          <el-upload :show-file-list="false" accept=".xlsx" :auto-upload="false" :on-change="doImportPlan">
            <el-button size="small" type="warning">付款计划表Excel导入</el-button>
          </el-upload>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="billNo" label="服务申请单号" min-width="175" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }"><el-tag size="small" type="info">{{ apReqTypeLabel(row.reqType) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="vendorName" label="供应商" min-width="130" show-overflow-tooltip />
      <el-table-column prop="paymentWay" label="付款方法" width="90" />
      <el-table-column prop="amount" label="金额" width="110" align="right" />
      <el-table-column prop="invoiceNo" label="发票编号" min-width="130" show-overflow-tooltip />
      <el-table-column label="状态" width="92">
        <template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ apReqStatusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="办理人" width="110">
        <template #default="{ row }">{{ row.handler || (row.handlerPool ? `待选单:${row.handlerPool}` : '—') }}</template>
      </el-table-column>
      <el-table-column prop="voucherNo" label="ERP凭证" width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-for="a in actions(row)" :key="a.key" link type="primary" size="small" @click="act(row, a)">{{ a.label }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建 -->
    <el-dialog v-model="createVisible" title="新建付款服务申请" width="680px" destroy-on-close>
      <el-form ref="createFormRef" :model="form" :rules="createRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="单据类型" prop="reqType">
            <el-select v-model="form.reqType" style="width: 100%">
              <el-option v-for="(l, c) in AP_REQ_TYPE" :key="c" :label="l" :value="c" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款方法" prop="paymentWay">
            <el-select v-model="form.paymentWay" style="width: 100%">
              <el-option label="银行转账" value="银行转账" /><el-option label="支票" value="支票" /><el-option label="应付票据" value="应付票据" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商名称"><el-input v-model="form.vendorName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="事业部"><el-input v-model="form.sbu" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="金额" prop="amount"><el-input v-model="form.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发票编号" prop="invoiceNo"><el-input v-model="form.invoiceNo" placeholder="XXXXXX01-10 或 XXXXXX01,XXXXXX05" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="PO编号" prop="poNo"><el-input v-model="form.poNo" placeholder="预付款必填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="银行账户"><el-input v-model="form.bankNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="汇票到期日" prop="expireDate"><el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="应付票据必填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="背书付款额"><el-input v-model="form.paymentAmount" placeholder="须=发票合计" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 确认服务请求 -->
    <el-dialog v-model="confirmVisible" title="确认服务请求（可修改付款方式等）" width="520px" destroy-on-close>
      <el-form :model="confirmForm" label-width="110px" size="small">
        <el-form-item label="银行账户"><el-input v-model="confirmForm.bankNo" /></el-form-item>
        <el-form-item label="付款方法">
          <el-select v-model="confirmForm.paymentWay" style="width: 100%">
            <el-option label="银行转账" value="银行转账" /><el-option label="支票" value="支票" /><el-option label="应付票据" value="应付票据" />
          </el-select>
          <div class="tip">修改付款方法将自动转AP组长审核</div>
        </el-form-item>
        <el-form-item label="折扣额"><el-input v-model="confirmForm.rebateAmount" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doConfirm">确认</el-button>
      </template>
    </el-dialog>

    <!-- 组长审核 -->
    <el-dialog v-model="reviewVisible" title="AP组长审核" width="420px" destroy-on-close>
      <el-input v-model="reviewNote" type="textarea" :rows="2" placeholder="驳回必填原因" />
      <template #footer>
        <el-button size="small" @click="doReview(false)">驳回</el-button>
        <el-button type="primary" size="small" @click="doReview(true)">通过</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="420px" destroy-on-close>
      <el-input v-model="inputText" :placeholder="inputKind === 'user' ? '办理人' : '作废原因'" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doInput">确定</el-button>
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
  reqType: commonRules.select('请选择单据类型'),
  paymentWay: commonRules.select('请选择付款方法'),
  amount: commonRules.amountText('请输入金额'),
  invoiceNo: commonRules.required('请输入发票编号（XXXXXX01-10 或逗号分隔）'),
  poNo: form.reqType === 'PREPAY' ? commonRules.required('预付款必须录入PO编号') : [],
  expireDate: form.paymentWay === '应付票据' ? commonRules.date('付款方法为应付票据时必须录入汇票到期日') : [],
}))
const confirmForm = reactive({})
const reviewNote = ref('')
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')

const statusTag = (s) => ({ PAID: 'success', POSTED: 'success', COMPLETED: 'info', VOIDED: 'danger', GROUP_REVIEW: 'warning' }[s] || 'primary')

function actions(row) {
  switch (row.status) {
    case 'PENDING_DISPATCH': return [{ key: 'dispatch', label: '自动派工' }, { key: 'assign', label: '手工派工' }, { key: 'void', label: '作废' }]
    case 'DISPATCHED': return row.handlerPool ? [{ key: 'claim', label: '选单' }, { key: 'assign', label: '运营岗派工' }] : [{ key: 'confirm', label: '确认服务请求' }, { key: 'post', label: '过账' }, { key: 'void', label: '作废' }]
    case 'GROUP_REVIEW': return [{ key: 'review', label: '组长审核' }]
    case 'POSTED': return [{ key: 'pay', label: '资金付款' }]
    case 'PAID': return [{ key: 'close', label: '完成' }]
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
  ElMessage.success(okMsg || '操作成功')
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
    inputTitle.value = { claim: '选单认领', assign: '运营岗派工/转单', void: '作废（终止付款）' }[a.key]
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
    return ElMessage.warning({ claim: '请填写认领的办理人', assign: '请填写办理人（多负责人逗号分隔）', void: '请填写作废原因' }[inputKind.value] || '此项为必填')
  }
  if (inputKind.value === 'claim') await api(() => apFlowApi.claim(id, inputText.value), '选单成功')
  else if (inputKind.value === 'assign') await api(() => apFlowApi.assign(id, inputText.value))
  else await api(() => apFlowApi.void(id, inputText.value))
  inputVisible.value = false
}

async function doConfirm() {
  await api(() => apFlowApi.confirm(current.value.id, { ...confirmForm }))
  confirmVisible.value = false
}

async function doReview(pass) {
  if (!pass && !reviewNote.value.trim()) return ElMessage.warning('驳回必须填写原因')
  await api(() => apFlowApi.groupReview(current.value.id, { pass, note: reviewNote.value, reviewer: '组长' }))
  reviewVisible.value = false
}

async function doCreate() {
  if (!(await validateForms([createFormRef]))) return
  await api(() => apFlowApi.create({ ...form, companyId: 1 }), '创建成功')
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
    ElMessage.success(`导入成功 ${r.created} 张，错误 ${r.errors.length} 项${r.errors.length ? '：' + r.errors.join('；') : ''}`)
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
