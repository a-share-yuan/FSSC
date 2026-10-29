<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap; align-items:center">
      <el-input v-model="q.keyword" :placeholder="$t('flow.arColl.phKw')" clearable size="small" style="width:200px" />
      <el-select v-model="q.status" size="small" style="width:120px" :placeholder="$t('common.status')" clearable>
        <el-option :label="$t('flow.arColl.stPending')" value="PENDING" />
        <el-option :label="$t('flow.arColl.stResolved')" value="RESOLVED" />
      </el-select>
      <el-input-number v-model="minOverdue" :min="0" size="small" style="width:130px" :placeholder="$t('flow.arColl.phMinOverdue')" :controls="false" />
      <el-input-number v-model="overdueDays" :min="0" size="small" style="width:130px" :placeholder="$t('flow.arColl.phScanDays')" />
      <el-date-picker v-model="overdueBaseline" type="date" value-format="YYYY-MM-DD" :placeholder="$t('flow.arColl.phBaseline')" clearable size="small" style="width:250px" />
      <el-button type="warning" size="small" @click="createOverdue">{{ $t('flow.arColl.btnScanOverdue') }}</el-button>
    </div>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="filtered" size="small" border stripe v-else @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
      <el-table-column prop="billNo" :label="$t('flow.arColl.colArDocNo')" min-width="180" show-overflow-tooltip sortable />
      <el-table-column :label="$t('flow.arColl.colCustomer')" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ customerName(row.customerId) }}</template>
      </el-table-column>
      <el-table-column prop="dueDate" :label="$t('flow.arColl.colDueDate')" width="110" sortable />
      <el-table-column prop="overdueDays" :label="$t('flow.arColl.colOverdueDays')" width="100" sortable />
      <el-table-column :label="$t('common.status')" width="90">
        <template #default="{ row }">{{ rcStatusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="contactName" :label="$t('flow.arColl.colContact')" width="100" show-overflow-tooltip sortable />
      <el-table-column :label="$t('flow.arColl.colContactPhone')" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.contactPhone || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.arColl.colNote')" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.note || '—' }}</template>
      </el-table-column>
      <!-- 乙：同人多岗只做复核提示，不拦办结——提示必须在界面看得见才算控制点 -->
      <el-table-column :label="$t('flow.arColl.colReview')" width="80" align="center">
        <template #default="{ row }">
          <el-tooltip v-if="row.multiRoleWarning" :content="row.multiRoleWarning" placement="top">
            <el-tag size="small" type="warning">{{ $t('flow.arColl.tagMultiRole') }}</el-tag>
          </el-tooltip>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('flow.arColl.btnTrace') }}</el-button>
          <el-button v-if="row.status !== 'RESOLVED'" link size="small" type="primary" @click="solveCollect(row)">{{ $t('flow.arColl.btnSolve') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="RECEIVABLE" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { receivableApi, mdApi } from '@/api'
import SkeletonList from '@/components/SkeletonList.vue'
import BillTraceDrawer from '@/components/BillTraceDrawer.vue'
import { showRowDetail } from '@/utils/format'
import { rcStatusLabel } from '@/constants/enums'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const traceVisible = ref(false)
const traceBillId = ref(null)
function openRowDetail(row) {
  traceBillId.value = row.receivableId
  traceVisible.value = true
  return showRowDetail(row, { title: t('flow.arColl.ttlDetail') })
}

function openTrace(row) {
  traceBillId.value = row.receivableId
  traceVisible.value = true
}

const loading = ref(false)
const collects = ref([])
const customers = ref([])
const q = reactive({ keyword: '', status: '' })
const minOverdue = ref(null)
const overdueDays = ref(30)
const overdueBaseline = ref('')

function customerName(id) { const c = customers.value.find((x) => x.id === id); return c ? (c.customerName || c.supplierName || c.name || '—') : (id ? '#' + id : '—') }

const filtered = computed(() => collects.value.filter((r) => {
  const kw = String(q.keyword || '').trim().toLowerCase()
  if (kw) {
    const hay = [r.billNo, customerName(r.customerId), r.contactName].map((v) => String(v || '').toLowerCase())
    if (!hay.some((v) => v.includes(kw))) return false
  }
  if (q.status && r.status !== q.status) return false
  if (minOverdue.value != null && Number(r.overdueDays || 0) < Number(minOverdue.value)) return false
  return true
}))

function overdueBaselineParams() {
  const v = String(overdueBaseline.value || '').trim()
  return v ? { asOfDate: v } : {}
}

// 批处理基准日与原应收工作台同一规则——留空＝系统当前日，选日期则按该日跑
function baselineSuffix(baseline) {
  return baseline.period || baseline.asOfDate
    ? t('flow.arColl.msgBaselineSuffix', { v1: baseline.asOfDate || baseline.period })
    : ''
}

async function loadCollect() {
  loading.value = true
  try { collects.value = await receivableApi.collectList() }
  catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

async function createOverdue() {
  let baseline
  try { baseline = overdueBaselineParams() } catch (e) { return ElMessage.error(e.message) }
  try {
    const n = await receivableApi.collectCreateOverdue(overdueDays.value, baseline)
    ElMessage.success(t('flow.arColl.msgTodosCreated', { v1: n }) + baselineSuffix(baseline))
    loadCollect()
  } catch (e) { ElMessage.error(e.message) }
}

async function solveCollect(row) {
  try {
    // 乙：办结照做，只把"同一人在这条催收任务上占多岗"的复核提示显示出来（不拦、不报错）
    const r = await receivableApi.collectSolve(row.id)
    if (r && r.warning) ElMessage({ type: 'warning', message: t('flow.arColl.msgSolvedWithWarn', { v1: r.warning }), duration: 6000 })
    else ElMessage.success(t('flow.arColl.msgSolved'))
    loadCollect()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(() => {
  mdApi.customers({ pageNum: 1, pageSize: 200 }).then((d) => { customers.value = d?.list || [] }).catch(() => {})
  loadCollect()
})
</script>
