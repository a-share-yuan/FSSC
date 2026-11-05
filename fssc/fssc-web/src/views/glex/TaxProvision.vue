<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->
<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>税金计提</b>
        <el-date-picker v-model="period" type="month" placeholder="会计期间" value-format="YYYYMM"
          style="width: 140px" @change="loadPreview" />
        <el-button size="small" @click="loadPreview">试算</el-button>
        <el-button type="primary" size="small" :loading="saving" :disabled="!preview.length" @click="submit">
          按试算结果计提
        </el-button>
        <el-button size="small" style="margin-left: auto" @click="loadLinkCheck">挂接核查</el-button>
      </div>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
      title="试算按「税金计提规则」逐税种算基数与税额；MANUAL 税种可直接改基数。提交后每个税种各起一条审批流，审批通过才出计提凭证。" />

    <SkeletonList v-if="loading" :rows="4" :cols="6" />
    <el-table v-else :data="preview" size="small" empty-text="请选择期间后点「试算」">
      <el-table-column prop="taxType" label="税种" width="110" />
      <el-table-column prop="ruleName" label="规则" min-width="150" show-overflow-tooltip />
      <el-table-column label="基数来源" width="110">
        <template #default="{ row }">{{ sourceName(row.baseSource) }}</template>
      </el-table-column>
      <el-table-column label="计税基数" width="180">
        <template #default="{ row }">
          <el-input-number v-if="row.baseSource === 'MANUAL'" v-model="row.baseAmount" :precision="2"
            :min="0" size="small" style="width: 100%" @change="dirty = true" />
          <span v-else>{{ fmt(row.baseAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="税率" width="90" align="right">
        <template #default="{ row }">{{ row.taxRate }}%</template>
      </el-table-column>
      <el-table-column label="应计提税额" width="140" align="right">
        <template #default="{ row }"><b>{{ fmt(taxOf(row)) }}</b></template>
      </el-table-column>
      <el-table-column label="分录" min-width="220">
        <template #default="{ row }">借 {{ row.debitSubjectCode }} / 贷 {{ row.creditSubjectCode }}</template>
      </el-table-column>
    </el-table>

    <div v-if="preview.length" style="margin-top: 8px; text-align: right; color: #606266">
      合计应计提：<b style="color: #f56c6c">￥{{ fmt(totalTax) }}</b>
    </div>

    <el-divider v-if="linkCheck" content-position="left">挂接核查 · {{ linkCheck.period }}</el-divider>
    <div v-if="linkCheck" style="font-size: 13px; color: #606266; margin-bottom: 8px">
      共 {{ linkCheck.total }} 条；无审批流程 <b>{{ linkCheck.missingFlowCount }}</b> 条（￥{{ fmt(linkCheck.missingFlowAmount) }}）；
      无凭证 <b>{{ linkCheck.missingVoucherCount }}</b> 条（￥{{ fmt(linkCheck.missingVoucherAmount) }}）；
      已入账但无分录 <b>{{ linkCheck.missingEntryCount }}</b> 条（￥{{ fmt(linkCheck.missingEntryAmount) }}）
    </div>
    <el-table v-if="linkCheck" :data="linkCheck.rows || []" size="small" max-height="320">
      <el-table-column prop="provisionNo" label="计提单号" width="170" />
      <el-table-column prop="taxType" label="税种" width="110" />
      <el-table-column label="税额" width="120" align="right">
        <template #default="{ row }">{{ fmt(row.taxAmount) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="voucherNo" label="凭证号" width="150" />
      <el-table-column label="审批流程" width="90" align="center">
        <template #default="{ row }"><el-tag :type="row.hasApproveFlow ? 'success' : 'danger'" size="small">{{ row.hasApproveFlow ? '有' : '无' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="凭证" width="90" align="center">
        <template #default="{ row }"><el-tag :type="row.hasVoucher ? 'success' : 'danger'" size="small">{{ row.hasVoucher ? '有' : '无' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="entryCount" label="总账分录" width="100" align="center" />
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { glexApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'

const period = ref(new Date().toISOString().slice(0, 7).replace('-', ''))
const preview = ref([])
const loading = ref(false)
const saving = ref(false)
const linkCheck = ref(null)
/** 手工改过基数＝true：此时点【试算】会把改动整表重拉覆盖，必须先确认 */
const dirty = ref(false)

function fmt(v) {
  if (v === null || v === undefined || v === '') return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function sourceName(v) {
  return { MANUAL: '手工录入', DECLARATION: '按申报数', INVOICE: '按进项税' }[v] || v || '—'
}
function taxOf(row) {
  const base = Number(row.baseAmount || 0)
  const rate = Number(row.taxRate || 0)
  return Math.round(base * rate) / 100
}
const totalTax = computed(() => preview.value.reduce((s, r) => s + taxOf(r), 0))

async function loadPreview() {
  if (!period.value) return
  if (dirty.value && preview.value.length) {
    try {
      await ElMessageBox.confirm('【试算】会按计提规则整表重算，你手工改过的基数会被覆盖。继续？', '覆盖确认',
        { type: 'warning', confirmButtonText: '继续试算', cancelButtonText: '取消' })
    } catch { return }
  }
  loading.value = true
  linkCheck.value = null
  try {
    preview.value = (await glexApi.taxPreview(period.value)) || []
    dirty.value = false
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function submit() {
  const bases = {}
  preview.value.forEach((r) => {
    if (r.baseSource === 'MANUAL') bases[r.taxType] = r.baseAmount
  })
  saving.value = true
  try {
    const rows = await glexApi.taxProvision(period.value, bases)
    ElMessage.success(`已计提 ${(rows || []).length} 条，已逐条发起审批`)
    await loadPreview()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function loadLinkCheck() {
  if (!period.value) return
  try {
    linkCheck.value = await glexApi.taxLinkCheck(period.value)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(loadPreview)
</script>
