<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>坏账管理（计提 / 核销 / 查询）</span>
        <div class="ops">
          <el-input v-model="q.period" size="small" style="width: 130px" placeholder="期间 yyyy-MM" clearable @keyup.enter="load" />
          <el-select v-model="q.status" size="small" style="width: 110px" placeholder="状态" clearable @change="load">
            <el-option v-for="(l, s) in AR_BADDEBT_STATUS" :key="s" :label="l" :value="s" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button type="primary" size="small" @click="openProvision">按账龄计提</el-button>
          <el-upload :show-file-list="false" accept=".xlsx" :auto-upload="false" :on-change="doImportWriteoffExcel" style="display:inline-block">
            <el-button type="warning" size="small">坏账核销Excel(11.4.2)</el-button>
          </el-upload>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" size="small" border stripe empty-text="该账期暂无计提记录">
      <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
      <el-table-column prop="period" label="期间" width="90" />
      <el-table-column prop="receivableBillNo" label="应收单号" width="150" show-overflow-tooltip />
            <el-table-column prop="amount" label="计提金额" width="110" align="right" :formatter="(row, col, val) => formatMoney(val)" />
      <el-table-column prop="provisionRate" label="计提比例" width="90" align="right" :formatter="(row, col, val) => formatPercent(val)" />
      <el-table-column prop="provisionDate" label="计提日期" width="105" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="{ PROVISIONED: 'warning', WRITTEN_OFF: 'danger', REVERSED: 'info' }[row.status] || 'info'">
            {{ arBaddebtStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="核销信息" min-width="200">
        <template #default="{ row }">
          <span v-if="row.writeoffAmount != null">
            核销 {{ formatMoney(row.writeoffAmount) }}（{{ formatDate(row.writeoffDate) }}）{{ row.basisNo ? `依据 ${row.basisNo}` : '' }}
          </span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="writeoffVoucherNo" label="核销凭证" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PROVISIONED'" link type="danger" size="small" @click="openWriteoff(row)">坏账核销</el-button>
          <el-button v-if="row.status === 'PROVISIONED'" link type="warning" size="small" @click="doReverse(row)">坏账转回</el-button>
          <el-button v-if="row.status === 'PROVISIONED'" link type="primary" size="small" @click="doConfirm(row)">坏账确认</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="provisionVisible" title="按逾期账龄计提坏账（同一应收单同一期仅一次）" width="420px" destroy-on-close>
      <el-form ref="provisionFormRef" :model="provisionDlg" :rules="provisionRules" label-width="90px" size="small">
        <el-form-item label="期间" prop="period">
          <el-input v-model="provisionDlg.period" placeholder="yyyy-MM" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="provisionVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doProvision">开始计提</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="writeoffVisible" title="坏账核销（核销金额≤该客户坏账准备余额）" width="480px" destroy-on-close>
      <el-form ref="writeoffFormRef" :model="writeoffForm" :rules="writeoffRules" label-width="110px" size="small">
        <el-form-item label="核销金额" prop="amount"><el-input v-model="writeoffForm.amount" /></el-form-item>
        <el-form-item label="核销原因" prop="reason"><el-input v-model="writeoffForm.reason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="认定依据编号" prop="basisNo"><el-input v-model="writeoffForm.basisNo" placeholder="《坏账认定依据（破产说明等）》编号" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="writeoffVisible = false">取消</el-button>
        <el-button type="danger" size="small" @click="doWriteoff">核销</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { AR_BADDEBT_STATUS, arBaddebtStatusLabel } from '@/constants/enums'
import { formatDate, formatMoney, formatPercent } from '../../utils/format'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { receivableApi as badDebtApi } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'


const rows = ref([])
const loading = ref(false)
const q = reactive({ period: '', status: '' })
const provisionVisible = ref(false)
const provisionFormRef = ref(null)
const provisionDlg = reactive({ period: '' })
const provisionRules = {
  period: [...commonRules.required('请输入计提期间'), { pattern: /^\d{4}-\d{2}$/, message: '期间格式应为 yyyy-MM', trigger: 'blur' }],
}
const writeoffVisible = ref(false)
const current = ref(null)
const writeoffForm = reactive({})
const writeoffFormRef = ref(null)
const writeoffRules = {
  amount: commonRules.amountText('请输入核销金额'),
  reason: commonRules.required('请输入核销原因'),
  basisNo: commonRules.required('请输入认定依据编号'),
}

async function load() {
  loading.value = true
  try {
    const list = await badDebtApi.badDebtList(q.period || undefined)
    rows.value = q.status ? list.filter((r) => r.status === q.status) : list
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

/** 计提弹窗默认落在当前查询期间或本月，避免漏填期间（后端期间为空会造出无法去重的重复计提） */
function openProvision() {
  provisionDlg.period = q.period || new Date().toISOString().slice(0, 7)
  provisionVisible.value = true
}

async function doProvision() {
  if (!(await validateForms([provisionFormRef]))) return
  const res = await badDebtApi.badDebtProvision(provisionDlg.period)
  ElMessage.success(`计提完成，本次生成 ${res} 条`)
  provisionVisible.value = false
  await load()
}

async function doReverse(row) {
  await badDebtApi.badDebtReverse(row.id)
  ElMessage.success('已转回并生成红字凭证')
  await load()
}

async function doConfirm(row) {
  await badDebtApi.badDebtConfirm(row.id)
  ElMessage.success('已发起坏账确认审批')
  await load()
}

function openWriteoff(row) {
  current.value = row
  Object.keys(writeoffForm).forEach((k) => delete writeoffForm[k])
  writeoffForm.amount = row.amount
  writeoffVisible.value = true
}

async function doWriteoff() {
  if (!(await validateForms([writeoffFormRef]))) return
  await badDebtApi.badDebtWriteoff(current.value.id, { ...writeoffForm })
  ElMessage.success('坏账核销完成（借1231/贷1122）')
  writeoffVisible.value = false
  await load()
}

async function doImportWriteoffExcel(uploadFile) {
  const fd = new FormData()
  fd.append('file', uploadFile.raw)
  const res = await badDebtApi.badDebtWriteoffExcel(fd)
  ElMessage.success(`坏账核销Excel完成 ${res.writtenOff} 笔` + (res.errors?.length ? `，${res.errors.length} 条失败：${res.errors[0]}` : ''))
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
</style>
