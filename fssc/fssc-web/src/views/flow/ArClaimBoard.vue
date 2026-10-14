<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>应收服务申请（索赔/暂估/调整/对冲/其它业务/解挂/贴息）</span>
        <div class="ops">
          <el-select v-model="q.claimType" size="small" style="width: 150px" placeholder="全部类型" clearable @change="load">
            <el-option v-for="(l, c) in AR_CLAIM_TYPE" :key="c" :label="l" :value="c" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button type="primary" size="small" @click="openCreate">新建申请</el-button>
          <el-upload :show-file-list="false" accept=".xlsx" :auto-upload="false" :on-change="doImportOffsetExcel" style="display:inline-block">
            <el-button type="warning" size="small">对冲借贷Excel导入</el-button>
          </el-upload>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="billNo" label="服务申请单号" min-width="180" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }"><el-tag size="small" type="info">{{ arClaimTypeLabel(row.claimType) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip />
      <el-table-column prop="amount" label="金额" width="105" align="right" />
      <el-table-column prop="debitAccount" label="借方科目" min-width="120" show-overflow-tooltip />
      <el-table-column prop="buAmount" label="事业部金额" width="105" align="right" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="{ POSTED: 'success', COMPLETED: 'info', REJECTED: 'danger', EKP_REVIEW: 'warning' }[row.status] || 'primary'">
            {{ arClaimStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="voucherNo" label="ERP凭证" width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button v-for="a in actions(row)" :key="a.key" link type="primary" size="small" @click="act(row, a)">{{ a.label }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新建应收服务申请（科目/组织映射自动带默认）" width="660px" destroy-on-close>
      <el-form ref="createFormRef" :model="form" :rules="createRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="单据类型" prop="claimType">
            <el-select v-model="form.claimType" style="width: 100%">
              <el-option v-for="(l, c) in AR_CLAIM_TYPE" :key="c" :label="l" :value="c" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户编码"><el-input v-model="form.customerCode" placeholder="应收余额校验按此匹配" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="申请金额" prop="amount"><el-input v-model="form.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="成本金额" prop="costAmount"><el-input v-model="form.costAmount" placeholder="向客户索赔必填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="税率%"><el-input v-model="form.taxRate" placeholder="索赔16%，空=销售费用660121" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="厂别"><el-input v-model="form.factoryCode" placeholder="00不做事业部凭证；2400/2500折本位币" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="订单号" prop="orderNo"><el-input v-model="form.orderNo" placeholder="订单解挂必填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="借方金额" prop="debitAmount"><el-input v-model="form.debitAmount" placeholder="其它业务对冲必填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="贷方金额" prop="creditAmount"><el-input v-model="form.creditAmount" placeholder="借贷必须平衡" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="事由"><el-input v-model="form.reason" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="420px" destroy-on-close>
      <el-input v-model="inputText" :placeholder="inputKind === 'assign' ? '办理人' : '驳回原因（必填）'"
        :type="inputKind === 'assign' ? 'text' : 'textarea'" :rows="2" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doInput">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { AR_CLAIM_TYPE, arClaimTypeLabel, arClaimStatusLabel } from '@/constants/enums'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { arFlowApi } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'


const rows = ref([])
const loading = ref(false)
const q = reactive({ claimType: '' })
const createVisible = ref(false)
const inputVisible = ref(false)
const current = ref(null)
const form = reactive({})
const createFormRef = ref(null)
// 条件必输与后端 ArClaimService.create 同口径：索赔要成本金额、对冲要借贷金额、订单解挂要订单号
const createRules = computed(() => {
  const t = form.claimType
  return {
    claimType: commonRules.select('请选择单据类型'),
    amount: commonRules.amountText('请输入申请金额'),
    costAmount: t === 'CLAIM_OUT' ? commonRules.numberText('向客户索赔必须录入成本金额') : [],
    orderNo: t === 'ORDER_UNFRZ' ? commonRules.required('订单解挂必须录入订单号') : [],
    debitAmount: t === 'OTHER_OFFSET' ? commonRules.numberText('其它业务对冲必须录入借方金额') : [],
    creditAmount: t === 'OTHER_OFFSET' ? commonRules.numberText('其它业务对冲必须录入贷方金额') : [],
  }
})
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')

function actions(row) {
  switch (row.status) {
    case 'EKP_REVIEW': return [{ key: 'approve', label: 'EKP审批通过' }]
    case 'PENDING_DISPATCH': return [{ key: 'assign', label: '手工派工' }]
    case 'DISPATCHED': return [{ key: 'review', label: '审单过账' }, { key: 'reject', label: '驳回' }]
    case 'POSTED':
      return row.claimType === 'REVENUE_EST'
        ? [{ key: 'reverse', label: '暂估冲回' }, { key: 'complete', label: '归档' }]
        : [{ key: 'complete', label: '归档' }]
    default: return []
  }
}

async function load() {
  loading.value = true
  try { rows.value = await arFlowApi.claims({ claimType: q.claimType || undefined }) } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
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
    approve: () => api(() => arFlowApi.claimApprove(id)),
    review: () => api(() => arFlowApi.claimReview(id)),
    reverse: () => api(() => arFlowApi.claimReverse(id)),
    complete: () => api(() => arFlowApi.claimComplete(id)),
  }
  if (m[a.key]) return m[a.key]()
  if (a.key === 'assign') { inputKind.value = 'assign'; inputTitle.value = '手工派工'; inputText.value = ''; inputVisible.value = true }
  else if (a.key === 'reject') { inputKind.value = 'reject'; inputTitle.value = '驳回'; inputText.value = ''; inputVisible.value = true }
}

async function doInput() {
  const id = current.value.id
  // 派工要有办理人、驳回要留原因（后端 ArClaimService「驳回必须填写原因」同口径）
  if (!inputText.value.trim()) {
    return ElMessage.warning(inputKind.value === 'assign' ? '请填写办理人' : '请填写驳回原因')
  }
  if (inputKind.value === 'assign') await api(() => arFlowApi.claimAssign(id, inputText.value))
  else await api(() => arFlowApi.claimReject(id, inputText.value))
  inputVisible.value = false
}

/** 新建前清空上一次录入，避免带旧内容开单 */
function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  createVisible.value = true
}

async function doCreate() {
  if (!(await validateForms([createFormRef]))) return
  await api(() => arFlowApi.createClaim({ ...form, companyId: 1 }), '创建成功')
  createVisible.value = false
}

async function doImportOffsetExcel(uploadFile) {
  const fd = new FormData()
  fd.append('file', uploadFile.raw)
  const res = await arFlowApi.importOffsetExcel(fd)
  ElMessage.success(`对冲单已生成 ${res.billNo}（${res.lines} 行分录，借/贷合计 ${res.totalDebit}），请审单过账`)
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
</style>
