<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.badDebt.pageTitle') }}</span>
        <div class="ops">
          <el-input v-model="q.period" size="small" style="width: 130px" :placeholder="$t('flow.close.colPeriodYm')" clearable @keyup.enter="load" />
          <el-select v-model="q.status" size="small" style="width: 110px" :placeholder="$t('common.status')" clearable @change="load">
            <el-option v-for="(l, s) in AR_BADDEBT_STATUS" :key="s" :label="l" :value="s" />
          </el-select>
          <el-button size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          <el-button type="primary" size="small" @click="openProvision">{{ $t('flow.badDebt.btnProvisionByAgeing') }}</el-button>
          <el-upload :show-file-list="false" accept=".xlsx" :auto-upload="false" :on-change="doImportWriteoffExcel" style="display:inline-block">
            <el-button type="warning" size="small">{{ $t('flow.badDebt.btnWriteoffExcel') }}</el-button>
          </el-upload>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" size="small" border stripe :empty-text="$t('flow.badDebt.emptyNoProvision')">
      <el-table-column prop="customerName" :label="$t('field.customer')" min-width="120" show-overflow-tooltip />
      <el-table-column prop="period" :label="$t('field.period')" width="90" />
      <el-table-column prop="receivableBillNo" :label="$t('flow.badDebt.colArDocNo')" width="150" show-overflow-tooltip />
            <el-table-column prop="amount" :label="$t('flow.report.colProvision')" width="110" align="right" :formatter="(row, col, val) => formatMoney(val)" />
      <el-table-column prop="provisionRate" :label="$t('flow.badDebt.colProvisionRate')" width="90" align="right" :formatter="(row, col, val) => formatPercent(val)" />
      <el-table-column prop="provisionDate" :label="$t('flow.badDebt.colProvisionDate')" width="105" />
      <el-table-column :label="$t('common.status')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="{ PROVISIONED: 'warning', WRITTEN_OFF: 'danger', REVERSED: 'info' }[row.status] || 'info'">
            {{ arBaddebtStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('flow.badDebt.colWriteoffInfo')" min-width="200">
        <template #default="{ row }">
          <span v-if="row.writeoffAmount != null">
            {{ $t('flow.badDebt.writeoffInfo', { v1: formatMoney(row.writeoffAmount), v2: formatDate(row.writeoffDate) }) }}{{ row.basisNo ? $t('flow.badDebt.basisNo', { v1: row.basisNo }) : '' }}
          </span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="writeoffVoucherNo" :label="$t('flow.badDebt.colWriteoffVoucher')" min-width="120" show-overflow-tooltip />
      <el-table-column :label="$t('common.operation')" width="230" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PROVISIONED'" link type="danger" size="small" @click="openWriteoff(row)">{{ $t('flow.badDebt.btnWriteoff') }}</el-button>
          <el-button v-if="row.status === 'PROVISIONED'" link type="warning" size="small" @click="doReverse(row)">{{ $t('flow.badDebt.btnReverse') }}</el-button>
          <el-button v-if="row.status === 'PROVISIONED'" link type="primary" size="small" @click="doConfirm(row)">{{ $t('flow.badDebt.btnConfirmBadDebt') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="provisionVisible" :title="$t('flow.badDebt.dlgProvisionTitle')" width="420px" destroy-on-close>
      <el-form ref="provisionFormRef" :model="provisionDlg" :rules="provisionRules" label-width="90px" size="small">
        <el-form-item :label="$t('field.period')" prop="period">
          <el-input v-model="provisionDlg.period" placeholder="yyyy-MM" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="provisionVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doProvision">{{ $t('flow.badDebt.btnStartProvision') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="writeoffVisible" :title="$t('flow.badDebt.dlgWriteoffTitle')" width="480px" destroy-on-close>
      <el-form ref="writeoffFormRef" :model="writeoffForm" :rules="writeoffRules" label-width="110px" size="small">
        <el-form-item :label="$t('flow.report.colBadDebtWriteOff')" prop="amount"><el-input v-model="writeoffForm.amount" /></el-form-item>
        <el-form-item :label="$t('flow.badDebt.colWriteoffReason')" prop="reason"><el-input v-model="writeoffForm.reason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item :label="$t('flow.badDebt.colBasisNo')" prop="basisNo"><el-input v-model="writeoffForm.basisNo" :placeholder="$t('flow.badDebt.phBasisNo')" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="writeoffVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" size="small" @click="doWriteoff">{{ $t('flow.badDebt.btnWriteoffConfirm') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { AR_BADDEBT_STATUS, arBaddebtStatusLabel } from '@/constants/enums'
import { formatDate, formatMoney, formatPercent } from '../../utils/format'
import {onMounted, reactive, ref, computed} from 'vue'
import { ElMessage } from 'element-plus'
import { receivableApi as badDebtApi } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()


const rows = ref([])
const loading = ref(false)
const q = reactive({ period: '', status: '' })
const provisionVisible = ref(false)
const provisionFormRef = ref(null)
const provisionDlg = reactive({ period: '' })
const provisionRules = computed(() => ({
  period: [...commonRules.required(t('flow.badDebt.errPeriodRequired')), { pattern: /^\d{4}-\d{2}$/, message: t('flow.badDebt.errPeriodFormat'), trigger: 'blur' }],
}))
const writeoffVisible = ref(false)
const current = ref(null)
const writeoffForm = reactive({})
const writeoffFormRef = ref(null)
const writeoffRules = computed(() => ({
  amount: commonRules.amountText(t('flow.badDebt.errWtAmountRequired')),
  reason: commonRules.required(t('flow.badDebt.errWtReasonRequired')),
  basisNo: commonRules.required(t('flow.badDebt.errBasisNoRequired')),
}))

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
  ElMessage.success(t('flow.badDebt.msgProvisionDone', { v1: res }))
  provisionVisible.value = false
  await load()
}

async function doReverse(row) {
  await badDebtApi.badDebtReverse(row.id)
  ElMessage.success(t('flow.badDebt.msgReversed'))
  await load()
}

async function doConfirm(row) {
  await badDebtApi.badDebtConfirm(row.id)
  ElMessage.success(t('flow.badDebt.msgConfirmSubmitted'))
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
  ElMessage.success(t('flow.badDebt.msgWriteoffDone'))
  writeoffVisible.value = false
  await load()
}

async function doImportWriteoffExcel(uploadFile) {
  const fd = new FormData()
  fd.append('file', uploadFile.raw)
  const res = await badDebtApi.badDebtWriteoffExcel(fd)
  ElMessage.success(t('flow.badDebt.msgExcelDone', { v1: res.writtenOff }) + (res.errors?.length ? t('flow.badDebt.msgExcelFailSuffix', { v1: res.errors.length, v2: res.errors[0] }) : ''))
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
</style>
