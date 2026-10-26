<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>{{ $t('gl.consolidate.pageTitle') }}</b>
        <el-date-picker v-model="f.period" type="month" :placeholder="$t('gl.consolidate.colBelong')" value-format="YYYYMM" clearable style="width: 140px" />
        <el-select v-model="f.status" :placeholder="$t('common.status')" clearable style="width: 130px">
          <el-option :label="$t('gl.consolidate.stDraft')" value="DRAFT" />
          <el-option :label="$t('gl.consolidate.stDone')" value="CONSOLIDATED" />
        </el-select>
        <el-input v-model="f.keyword" :placeholder="$t('gl.consolidate.phNo')" clearable style="width: 170px" />
        <el-button type="primary" size="small" @click="load(1)">{{ $t('common.search') }}</el-button>
        <el-button size="small" @click="createVisible = true">{{ $t('gl.consolidate.btnNewShort') }}</el-button>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" v-else size="small" :empty-text="$t('common.empty')">
      <el-table-column prop="reportNo" :label="$t('gl.consolidate.colNo')" width="170" sortable />
      <el-table-column prop="period" :label="$t('gl.consolidate.colBelong')" width="90" sortable />
      <el-table-column :label="$t('gl.consolidate.colScope')" min-width="200" show-overflow-tooltip>
        <!-- R5-D-150：公司 ID 串按 orgApi.companyTree 名称表映射为公司名（取不到回落裸 ID，不造词） -->
        <template #default="{ row }">{{ companyRangeText(row) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CONSOLIDATED' ? 'success' : 'info'" size="small">{{ row.status === 'CONSOLIDATED' ? t('gl.consolidate.stDone') : t('gl.consolidate.stDraft') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" :label="$t('common.createTime')" width="160"
                       :formatter="(r, c, v) => formatDateTime(v)" />
      <el-table-column :label="$t('common.operation')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" size="small" type="primary" @click="runMerge(row.period)">{{ $t('gl.consolidate.btnRun') }}</el-button>
          <el-button v-if="row.status === 'DRAFT'" size="small" @click="openAdjust(row)">{{ $t('gl.consolidate.colAdj') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="f.pageSize"
      :current-page="f.pageNum"
      @current-change="load"
    />

    <!-- 新建底稿 -->
    <el-dialog v-model="createVisible" :title="$t('gl.consolidate.btnNew')" width="460px">
      <el-form ref="createFormRef" :model="form" :rules="createRules" label-width="110px">
        <el-form-item :label="$t('gl.consolidate.labelPeriod')" prop="period">
          <el-date-picker v-model="form.period" type="month" value-format="YYYYMM" :placeholder="$t('gl.consolidate.colPeriod')" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('gl.consolidate.limCompany')">
          <el-input v-model="form.companyIds" :placeholder="$t('gl.consolidate.phCompany')" />
        </el-form-item>
        <el-form-item :label="$t('gl.consolidate.limElim')">
          <el-input v-model="form.rawRows" type="textarea" :rows="3" :placeholder="$t('gl.consolidate.phElim')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doCreate">{{ $t('gl.consolidate.btnCreate') }}</el-button>
      </template>
    </el-dialog>

    <!-- 调整分录 -->
    <el-dialog v-model="adjustVisible" :title="$t('gl.consolidate.secAdj')" width="520px">
      <el-form ref="adjustFormRef" :model="adjustFormModel" :rules="adjustRules" label-width="90px">
        <el-form-item :label="$t('gl.consolidate.colNo')"><el-input :model-value="adjustRow?.reportNo" disabled /></el-form-item>
        <el-form-item :label="$t('gl.consolidate.colAdj')" prop="rawRows">
          <el-input v-model="adjustRaw" type="textarea" :rows="5" :placeholder="$t('gl.consolidate.phRawRows')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doAdjust">{{ $t('gl.consolidate.btnSaveAdj') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import SkeletonList from '../../components/SkeletonList.vue'
import { glexApi, orgApi } from '../../api'
import { formatDateTime } from '@/utils/format'
import { useFormRules, commonRules, validateForm } from '../../composables/useFormRules'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const f = ref({ pageNum: 1, pageSize: 20, period: '', status: '', keyword: '' })
const createVisible = ref(false)
const form = ref({ period: '', companyIds: '', rawRows: '' })
const adjustVisible = ref(false)
const adjustRow = ref(null)
const adjustRaw = ref('')
// 第2批：新建底稿期间原为装饰红星（el-form-item required 无 prop，缺期间时只有一句 toast）
const createFormRef = ref(null)
const { rules: createRules } = useFormRules('GLEX_CONSOLIDATE')
// 第2批：调整分录弹窗接入（空分录提交无意义）；computed 包装给 el-form 可校验 model
const adjustFormRef = ref(null)
const adjustFormModel = computed(() => ({ rawRows: adjustRaw.value }))
const adjustRules = { rawRows: commonRules.required('请输入调整分录内容') }

async function load(pageNum) {
  if (pageNum) f.value.pageNum = pageNum
  loading.value = true
  try {
    const d = await glexApi.consolidatePage(f.value.pageNum, f.value.pageSize, f.value.period, f.value.status, f.value.keyword)
    rows.value = (d && (d.records || d.list)) || []
    total.value = (d && d.total) || 0
  } catch (e) {
    ElMessage.error(e.message || t('gl.consolidate.msgLoadFail'))
  } finally {
    loading.value = false
  }
}

async function doCreate() {
  if (!(await validateForm(createFormRef.value))) return
  try {
    await glexApi.consolidateCreate({ period: form.value.period, companyIds: form.value.companyIds, rawRows: form.value.rawRows })
    ElMessage.success(t('gl.consolidate.msgCreated'))
    createVisible.value = false
    load(1)
  } catch (e) {
    ElMessage.error(e.message || t('gl.consolidate.msgCreateFail'))
  }
}

async function runMerge(period) {
  try {
    await glexApi.consolidateRun(period)
    ElMessage.success(t('gl.consolidate.msgDone', { period }))
    load()
  } catch (e) {
    ElMessage.error(e.message || t('gl.consolidate.msgRunFail'))
  }
}

function openAdjust(row) {
  adjustRow.value = row
  adjustRaw.value = row.rawRows || ''
  adjustVisible.value = true
}

async function doAdjust() {
  if (!(await validateForm(adjustFormRef.value))) return
  try {
    await glexApi.consolidateAdjust(adjustRow.value.id, adjustRaw.value)
    ElMessage.success(t('gl.consolidate.msgAdjSaved'))
    adjustVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || t('gl.consolidate.msgSaveFail'))
  }
}

// R5-D-150：合并范围「公司 ID 串 → 公司名」映射（名称表＝orgApi.companyTree 摊平，与 BankAccount/MasterDataTree 同机制；
// 取不到时回落裸 ID，不造词）
const companyMap = ref({})
async function loadCompanyMap() {
  try {
    const tree = (await orgApi.companyTree({ silent: true })) || []
    const walk = (arr) => (arr || []).forEach((n) => {
      if (!n) return
      companyMap.value[n.id] = n.companyName || n.name || ''
      if (n.children?.length) walk(n.children)
    })
    walk(tree)
  } catch (e) { /* 名称表取不到时列回落裸 ID，不阻断底稿列表 */ }
}
function companyRangeText(row) {
  const raw = String(row?.companyIds ?? '').trim()
  if (!raw) return '—'
  return raw.split(',').map((s) => s.trim()).filter(Boolean)
    .map((id) => companyMap.value[id] || id).join('、')
}

onMounted(() => { load(1); loadCompanyMap() })
</script>
