<template>
  <div>
    <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center">
      <el-input-number v-model="overdueDays" :min="0" size="small" />
      <el-input v-model="overdueBaseline" :placeholder="$t('flow.arColl.phBaseline')" clearable size="small" style="width:250px" />
      <el-button type="warning" size="small" @click="createOverdue">{{ $t('flow.arColl.btnScanOverdue') }}</el-button>
    </div>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="collects" size="small" border stripe v-else @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" :label="$t('flow.arColl.colArDocNo')" min-width="180" show-overflow-tooltip sortable />
      <el-table-column prop="dueDate" :label="$t('flow.arColl.colDueDate')" width="110" sortable />
      <el-table-column prop="overdueDays" :label="$t('flow.arColl.colOverdueDays')" width="100" sortable />
      <el-table-column :label="$t('common.status')" width="90">
        <template #default="{ row }">{{ rcStatusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="contactName" :label="$t('flow.arColl.colContact')" width="100" show-overflow-tooltip sortable />
      <!-- D-DEC-410 乙：同人多岗只做复核提示，不拦办结——提示必须在界面看得见才算控制点 -->
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
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { receivableApi } from '@/api'
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
const overdueDays = ref(30)
const overdueBaseline = ref('')

function overdueBaselineParams() {
  const v = String(overdueBaseline.value || '').trim()
  if (!v) return {}
  if (/^\d{6}$/.test(v) || /^\d{4}-\d{2}$/.test(v)) return { period: v }
  if (/^\d{4}-\d{2}-\d{2}$/.test(v)) return { asOfDate: v }
  if (/^\d{8}$/.test(v)) return { asOfDate: `${v.slice(0, 4)}-${v.slice(4, 6)}-${v.slice(6, 8)}` }
  throw new Error(t('flow.arColl.errBaselineFormat', { v1: v }))
}

// D-DEC-407：批处理基准日与原应收工作台同口径——留空＝系统当前日，填期间则按期末日跑
function baselineSuffix(baseline) {
  return baseline.period || baseline.asOfDate
    ? t('flow.arColl.msgBaselineSuffix', { v1: baseline.asOfDate || baseline.period })
    : ''
}

async function loadCollect() { try { collects.value = await receivableApi.collectList() } catch (e) { ElMessage.error(e.message) } }

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
    // D-DEC-410 乙：办结照做，只把"同一人在这条催收任务上占多岗"的复核提示显示出来（不拦、不报错）
    const r = await receivableApi.collectSolve(row.id)
    if (r && r.warning) ElMessage({ type: 'warning', message: t('flow.arColl.msgSolvedWithWarn', { v1: r.warning }), duration: 6000 })
    else ElMessage.success(t('flow.arColl.msgSolved'))
    loadCollect()
  } catch (e) { ElMessage.error(e.message) }
}

function reload() { loadCollect() }

onMounted(() => { reload() })

function handleSelectionChange(val) { /* no-op for selection */ }
</script>
