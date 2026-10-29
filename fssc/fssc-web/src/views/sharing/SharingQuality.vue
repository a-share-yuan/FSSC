<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="quality-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">{{ $t('sharing.quality.pageTitle') }}</span>
          <el-button v-permission="'sharing:quality:create'" type="primary" size="small" @click="openDialog(null)">{{ $t('sharing.quality.btnAdd') }}</el-button>
        </div>
      </template>

      <el-form :inline="true" :rules="qualitySearchRules" size="small" style="margin-bottom:12px">
        <el-form-item :label="$t('field.docNo')">
          <el-input v-model="q.billNo" :placeholder="$t('sharing.quality.phPayBillNo')" clearable @keyup.enter="load" />
        </el-form-item>
        <el-form-item :label="$t('sharing.quality.labResult')">
          <el-select v-model="q.result" clearable style="width:120px" @change="load">
            <el-option :label="$t('sharing.quality.optPass')" value="PASS" />
            <el-option :label="$t('sharing.quality.optBack')" value="BACK" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('sharing.quality.labSampling')">
          <el-select v-model="q.checkType" clearable style="width:120px" @change="load">
            <el-option :label="$t('sharing.quality.optRandom')" value="RANDOM" />
            <el-option :label="$t('sharing.quality.optFull')" value="FULL" />
            <el-option :label="$t('sharing.quality.optKey')" value="KEY" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">{{ $t('common.search') }}</el-button>
        </el-form-item>
      </el-form>

      <SkeletonList v-if="loading" :rows="8" :cols="5" />
<el-table :data="rows" v-else size="small" stripe border max-height="560" @row-dblclick="openRowDetail"
        :row-class-name="rowClass" :empty-text="$t('common.empty')">
        <el-table-column prop="billNo" :label="$t('sharing.common.colDocNoFull')" width="140" show-overflow-tooltip sortable />
        <el-table-column :label="$t('sharing.quality.labSampling')" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.checkType === 'FULL' ? 'danger' : row.checkType === 'KEY' ? 'warning' : 'info'">
              {{ checkTypeText(row.checkType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('sharing.quality.colScore')" width="80" align="center" sortable :sort-by="(r) => r.score">
          <template #default="{ row }">
            <span :style="{ color: row.score >= 80 ? '#67c23a' : row.score >= 60 ? '#e6a23c' : '#f56c6c', fontWeight: 700 }">
              {{ row.score }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="deductReason" :label="$t('sharing.quality.colDeduct')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="$t('sharing.quality.labResult')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.result === 'PASS' ? 'success' : 'danger'" size="small">
              {{ row.result === 'PASS' ? t('sharing.quality.optPass') : t('sharing.quality.optBack') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkerId" :label="$t('sharing.quality.colChecker')" width="80" />
        <el-table-column prop="checkTime" :label="$t('sharing.quality.colCheckTime')" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
        <el-table-column prop="remark" :label="$t('common.remark')" min-width="120" show-overflow-tooltip />
        <el-table-column :label="$t('common.operation')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'sharing:quality:update'" link type="primary" size="small" @click="openDialog(row)">{{ $t('sharing.common.btnEdit') }}</el-button>
            <el-button v-permission="'sharing:quality:delete'" link type="danger" size="small" @click="del(row)">{{ $t('sharing.common.btnDelete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page.current" v-model:page-size="page.size"
        :total="page.total" :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top:12px;justify-content:flex-end"
        @size-change="load" @current-change="load"
      />
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.editing ? t('sharing.quality.dlgEditTitle') : t('sharing.quality.btnAdd')" width="560px" destroy-on-close>
      <el-form ref="qualityFormRef" :model="dialog.form" :rules="qualityFormRules" label-width="100px" size="default">
        <el-form-item :label="$t('sharing.common.colDocNoFull')" prop="billNo">
          <el-input v-model="dialog.form.billNo" :placeholder="$t('sharing.quality.phPayBillNo')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('sharing.quality.labSampling')" prop="checkType">
          <el-select v-model="dialog.form.checkType" style="width:100%">
            <el-option :label="$t('sharing.quality.optRandom')" value="RANDOM" />
            <el-option :label="$t('sharing.quality.optFull')" value="FULL" />
            <el-option :label="$t('sharing.quality.optKey')" value="KEY" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('sharing.quality.colScore')" prop="score">
          <el-input-number v-model="dialog.form.score" :min="0" :max="100" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('sharing.quality.labResult')" prop="result">
          <el-select v-model="dialog.form.result" style="width:100%">
            <el-option :label="$t('sharing.quality.optPass')" value="PASS" />
            <el-option :label="$t('sharing.quality.optBack')" value="BACK" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('sharing.quality.colDeduct')">
          <el-input v-model="dialog.form.deductReason" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="$t('common.remark')">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button v-permission="['sharing:quality:create', 'sharing:quality:update']" type="primary" :loading="dialog.saving" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import {onMounted, reactive, ref, computed} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { opApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { formatDateTime } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

// 双击行：调用已有详情/编辑函数
function openRowDetail(row) {
  openDialog(row)
}

const loading = ref(false)
const rows = ref([])
const page = reactive({ current: 1, size: 20, total: 0 })
const q = reactive({ billNo: '', result: '', checkType: '' })
const dialog = reactive({ visible: false, editing: null, form: {}, saving: false })

const qualitySearchRules = reactive({})
const qualityFormRef = ref(null)
const qualityFormRules = computed(() => (reactive({
  // 质检记录的人工输入：单据编号＝被检单据的定位符（此处由质检员手工填写，非系统只读编号）
  billNo: commonRules.required(t('sharing.quality.errDocNo')),
  checkType: commonRules.select(t('sharing.quality.errSampling')),
  score: [{ required: true, type: 'number', min: 0, max: 100, message: t('sharing.quality.errScore') }],
  result: commonRules.select(t('sharing.quality.errResult')),
})))

async function load() {
  loading.value = true
  try {
    const res = await opApi.qualityPage({ page: page.current, size: page.size, ...q })
    rows.value = res?.list || res?.records || []
    page.total = res?.total || rows.value.length
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function checkTypeText(code) {
  return { RANDOM: t('sharing.quality.optRandom'), FULL: t('sharing.quality.optFull'), KEY: t('sharing.quality.optKey') }[code] || code
}

function rowClass({ row }) {
  if (row.result === 'BACK') return 'row-red'
  if (row.score < 60) return 'row-yellow'
  return ''
}

function openDialog(row) {
  dialog.editing = row
  dialog.form = row ? { ...row } : { checkType: 'RANDOM', score: 100, result: 'PASS' }
  dialog.visible = true
}

async function save() {
  if (!(await validateForms([qualityFormRef]))) return
  dialog.saving = true
  try {
    if (dialog.editing) {
      await opApi.qualityUpdate(dialog.form)
    } else {
      await opApi.qualityCreate(dialog.form)
    }
    ElMessage.success(t('sharing.common.msgSaved'))
    dialog.visible = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    dialog.saving = false
  }
}

async function del(row) {
  try {
    await ElMessageBox.confirm(t('sharing.quality.confirmDelete'), t('sharing.common.btnDelete'), { type: 'warning' })
    await opApi.qualityDelete(row.id)
    ElMessage.success(t('sharing.common.msgDelOk'))
    await load()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

onMounted(() => load())
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; color: #303133; }
:deep(.row-red) { background-color: #fef0f0 !important; }
:deep(.row-yellow) { background-color: #fdf6ec !important; }
</style>
