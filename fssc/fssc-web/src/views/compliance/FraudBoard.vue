<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>反欺诈风控</b>
        <span style="color:#909399; font-size:12px">三档处置：提示 → 人工待审（进队列不挡流程） → 阻断（硬阻断位在付款指令执行前） —— 风险概览 + 检测记录 + 人工复审</span>
        <div style="margin-left:auto; display:flex; gap:8px">
          <el-button size="small" @click="doExport">导出 CSV</el-button>
          <el-button size="small" @click="loadAll">刷新</el-button>
        </div>
      </div>
    </template>

    <!-- R5-D-049：阻断档位当前 0 命中——说明卡解释开/关两种行为，避免用户误判"风控失效" -->
    <el-alert type="info" show-icon :closable="false" style="margin-bottom:12px"
              title="阻断档位说明：配置键 fraud.block_on_submit.enabled 默认关闭——关闭时仅记录并进人工待审，开启后命中阻断规则的单据在提交时硬拦。" />

    <!-- 风险概览 -->
    <div style="display:flex; gap:12px; flex-wrap:wrap; margin-bottom:16px">
      <div class="fraud-card">
        <div class="fraud-label">检测总量</div>
        <div class="fraud-value">{{ summary.total ?? '—' }}</div>
      </div>
      <div class="fraud-card" style="border-left-color:#f56c6c">
        <div class="fraud-label">高风险（待审+阻断）</div>
        <div class="fraud-value" style="color:#f56c6c">{{ summary.highRisk ?? '—' }}</div>
      </div>
      <div class="fraud-card" style="border-left-color:#b42b2b; cursor:pointer" title="点击只看待复审队列" @click="openReviewQueue()">
        <div class="fraud-label">人工待审积压</div>
        <div class="fraud-value" style="color:#b42b2b">{{ summary.pendingReview ?? '—' }}</div>
      </div>
      <div class="fraud-card" style="border-left-color:#e6a23c">
        <div class="fraud-label">可疑（预警放行）</div>
        <div class="fraud-value" style="color:#e6a23c">{{ summary.suspicious ?? '—' }}</div>
      </div>
      <div class="fraud-card" style="border-left-color:#67c23a">
        <div class="fraud-label">正常放行</div>
        <div class="fraud-value" style="color:#67c23a">{{ summary.normal ?? '—' }}</div>
      </div>
      <div class="fraud-card" style="border-left-color:#409eff">
        <div class="fraud-label">风险命中率</div>
        <div class="fraud-value" style="color:#409eff">{{ summary.riskRate != null ? summary.riskRate + '%' : '—' }}</div>
      </div>
      <div class="fraud-card" style="min-width:260px; flex:1">
        <div class="fraud-label">检测类型分布</div>
        <div class="fraud-types">
          <el-tag v-for="t in byType" :key="t.detection_type" size="small" style="margin:2px 4px 2px 0">
            {{ TYPE_ST[t.detection_type] || t.detection_type }}：{{ t.cnt }}
          </el-tag>
          <span v-if="!byType.length" style="color:#909399; font-size:12px">暂无数据</span>
        </div>
      </div>
    </div>

    <!-- 记录列表 -->
    <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center">
      <el-select v-model="q.detectionResult" placeholder="风险等级" clearable style="width:140px" size="small" @change="search">
        <el-option label="高风险" value="HIGH_RISK" />
        <el-option label="可疑" value="SUSPICIOUS" />
        <el-option label="正常" value="NORMAL" />
      </el-select>
      <el-select v-model="q.detectionType" placeholder="检测类型" clearable style="width:180px" size="small" @change="search">
        <el-option v-for="(v, k) in TYPE_ST" :key="k" :label="v" :value="k" />
      </el-select>
      <el-input v-model="q.employeeId" placeholder="员工ID" clearable style="width:130px" size="small" @keyup.enter="search" />
      <el-select v-model="q.actionTaken" placeholder="处置动作" clearable style="width:150px" size="small" @change="search">
        <el-option v-for="(v, k) in ACTION_ST" :key="k" :label="v" :value="k" />
      </el-select>
      <el-checkbox v-model="q.unreviewed" label="仅看待复审" size="small" @change="search" />
      <el-button size="small" type="primary" @click="search">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
    </div>

    <SkeletonList v-if="loading" :rows="10" :cols="7" />
    <el-table v-else :data="pagedLogs" size="small" border stripe empty-text="暂无数据" @row-dblclick="openRowDetail">
      <el-table-column prop="id" label="编号" width="70" sortable />
      <el-table-column label="检测类型" width="130">
        <template #default="{ row }">{{ TYPE_ST[row.detectionType] || row.detectionType }}</template>
      </el-table-column>
      <el-table-column label="风险等级" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="riskTag(row.detectionResult)">{{ RESULT_ST[row.detectionResult] || row.detectionResult }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="风险分" width="80" align="right">
        <template #default="{ row }">{{ row.riskScore != null ? Number(row.riskScore) : '—' }}</template>
      </el-table-column>
      <el-table-column prop="details" label="检测详情" min-width="240" show-overflow-tooltip />
      <el-table-column label="处置动作" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="actionTag(row.actionTaken)">{{ ACTION_ST[row.actionTaken] || row.actionTaken || '—' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="employeeId" label="员工" width="70" align="center" />
      <el-table-column prop="billId" label="单据" width="80" align="center">
        <template #default="{ row }">{{ row.billId || '—' }}</template>
      </el-table-column>
      <el-table-column label="检测时间" width="150" sortable prop="createTime">
        <template #default="{ row }">{{ shortTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="hasPendingAppeal(row)">
            <el-button link size="small" type="success" @click="doAppealReview(row, true)">申诉通过</el-button>
            <el-button link size="small" type="danger" @click="doAppealReview(row, false)">申诉驳回</el-button>
          </template>
          <!-- D-V5R-10（F-run-1017b 实测）：人工放行只对「会拦付款的行」渲染——与后端
               paymentBlockReason / fraud/page pendingRelease 同口径（HIGH_RISK 且未复审且未人工放行）。
               原实现按 actionTaken==='REVIEW' 渲染，预警行（WARN）与待审行的按钮混排，
               按行文本定位极易点到预警行的【申诉】（实测首轮脚本就点错行）。 -->
          <template v-else-if="isPendingRelease(row)">
            <el-button link size="small" type="success" @click="doReview(row, 'RELEASE')">人工放行</el-button>
            <el-button link size="small" type="danger" @click="doReview(row, 'BLOCK')">阻断</el-button>
            <el-button link size="small" type="warning" @click="openAppeal(row)">申诉</el-button>
          </template>
          <template v-else-if="!row.reviewedAt">
            <el-button link size="small" type="warning" @click="openAppeal(row)">申诉</el-button>
          </template>
          <span v-else style="color:#909399; font-size:12px">已复审</span>
        </template>
      </el-table-column>
    </el-table>
    <TablePager :pager="pager" @change="load" />
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { complianceApi } from '../../api'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import SkeletonList from '../../components/SkeletonList.vue'
import { showRowDetail } from '@/utils/format'
import { exportCsv } from '../../utils/csv'

const summary = ref({})
const byType = computed(() => summary.value?.byType || [])
const loading = ref(false)
const logs = ref([])
const q = reactive({ detectionResult: '', detectionType: '', employeeId: '', actionTaken: '', unreviewed: false })

const TYPE_ST = {
  FREQUENCY: '提交频率', AMOUNT: '金额', GEOGRAPHY: '地理位置', SUPPLIER: '供应商',
  TIME: '时间', ROUND_AMOUNT: '整数金额', HIGH_AMOUNT: '大额', SUPPLIER_RISK: '供应商风险',
  ENTERTAINMENT: '招待费', SAME_SUPPLIER: '同供应商频次', HOLIDAY: '节假日', TYPE_AMOUNT_MISMATCH: '类型金额匹配', MULTI_SUPPLIER: '多笔拆分',
}
const RESULT_ST = { HIGH_RISK: '高风险', SUSPICIOUS: '可疑', NORMAL: '正常' }
// R5-D-049：ALLOW 是自动放行、RELEASE 是人工放行，两者同义不同源，文案必须区分
const ACTION_ST = { ALLOW: '自动放行', WARN: '预警', REVIEW: '人工待审', BLOCK: '阻断', MONITOR: '监控', RELEASE: '人工放行' }
function riskTag(r) { return r === 'HIGH_RISK' ? 'danger' : (r === 'SUSPICIOUS' ? 'warning' : 'success') }
function actionTag(a) { return a === 'BLOCK' ? 'danger' : ((a === 'WARN' || a === 'MONITOR' || a === 'REVIEW') ? 'warning' : 'success') }

// P0 铁律：列表页必须 server 分页（sizes + jumper）
const pager = useTablePager({ mode: 'server', storageKey: 'fraud_logs' })
const { pagedData: pagedLogs } = pager

function load() {
  return pager.load(async ({ pageNum, pageSize }) => {
    const params = { pageNum, pageSize }
    if (q.detectionResult) params.detectionResult = q.detectionResult
    if (q.detectionType) params.detectionType = q.detectionType
    if (q.employeeId) params.employeeId = q.employeeId
    if (q.actionTaken) params.actionTaken = q.actionTaken
    if (q.unreviewed) params.unreviewed = true
    const d = await complianceApi.fraudPage(params)
    logs.value = d.list || []
    return { list: logs.value, total: d.total || 0 }
  }).catch((e) => ElMessage.error(e.message))
}
function loadSummary() {
  return complianceApi.fraudSummary().then((d) => { summary.value = d || {} }).catch(() => {})
}
function loadAll() { load(); loadSummary() }
function search() { pager.resetPage(); load() }
function reset() { q.detectionResult = ''; q.detectionType = ''; q.employeeId = ''; q.actionTaken = ''; q.unreviewed = false; pager.resetPage(); load() }
function openReviewQueue() { q.detectionResult = ''; q.detectionType = ''; q.employeeId = ''; q.actionTaken = 'REVIEW'; q.unreviewed = true; pager.resetPage(); load() }

async function doReview(row, action) {
  const tip = action === 'RELEASE' ? '确认人工放行该条风险记录？' : '确认人工阻断该条风险记录？'
  await ElMessageBox.confirm(tip, '反欺诈复审', { type: 'warning' })
  try {
    await complianceApi.fraudReview(row.id, action)
    ElMessage.success('复审完成')
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// P2-12 申诉闭环
function hasPendingAppeal(row) {
  return !row.reviewedAt && (row.details || '').includes('[申诉待审')
}

/** D-V5R-10：是否「待人工放行」行——与后端 paymentBlockReason / /fraud/page pendingRelease 同口径：
 *  HIGH_RISK 且未复审且未人工放行。只有这些行会拦付款，才给【人工放行/阻断】；
 *  预警（WARN）/可疑（SUSPICIOUS）行不拦付款，只留【申诉】，避免两类按钮混排点错。 */
function isPendingRelease(row) {
  return row.detectionResult === 'HIGH_RISK' && !row.reviewedAt && row.actionTaken !== 'RELEASE'
}

function openAppeal(row) {
  ElMessageBox.prompt('申诉原因(必填,留痕)', '风险申诉登记', {
    inputType: 'textarea',
    inputValidator: (v) => (v && v.trim() ? true : '申诉原因必填'),
    confirmButtonText: '提交申诉', cancelButtonText: '取消',
  }).then(async ({ value }) => {
    try {
      await complianceApi.fraudAppeal(row.id, value.trim())
      ElMessage.success('申诉已登记,待财务复审')
      load()
    } catch (e) { ElMessage.error(e.message) }
  }).catch(() => {})
}

function doAppealReview(row, approve) {
  ElMessageBox.prompt(approve ? '申诉通过确认(可填复核意见)' : '申诉驳回原因(必填)', approve ? '申诉复审-通过' : '申诉复审-驳回', {
    inputType: 'textarea',
    inputValidator: (v) => (approve || (v && v.trim()) ? true : '驳回原因必填'),
    confirmButtonText: approve ? '通过并放行' : '驳回',
    cancelButtonText: '取消',
  }).then(async ({ value }) => {
    try {
      await complianceApi.fraudAppealReview(row.id, approve, value || '')
      ElMessage.success(approve ? '申诉通过,已放行结案' : '申诉已驳回,维持原处置')
      load()
    } catch (e) { ElMessage.error(e.message) }
  }).catch(() => {})
}

function shortTime(t) {
  if (!t) return ''
  try { return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) } catch { return t }
}

// 双击行：展示行数据详情（41号审查 P1-9）
function openRowDetail(row) {
  return showRowDetail(row, { title: '风险检测详情' })
}

// 41号审查 P1-9：导出当前查询结果
function doExport() {
  exportCsv(logs.value.map((r) => ({
    ...r,
    detectionTypeCn: TYPE_ST[r.detectionType] || r.detectionType,
    resultCn: RESULT_ST[r.detectionResult] || r.detectionResult,
    actionCn: ACTION_ST[r.actionTaken] || r.actionTaken || '',
  })), [
    { prop: 'id', label: 'ID' }, { prop: 'detectionTypeCn', label: '检测类型' }, { prop: 'resultCn', label: '风险等级' },
    { prop: 'riskScore', label: '风险分' }, { prop: 'details', label: '检测详情' }, { prop: 'actionCn', label: '处置动作' },
    { prop: 'employeeId', label: '员工' }, { prop: 'billId', label: '单据' },
    { prop: 'reviewedAt', label: '复审时间' }, { prop: 'createTime', label: '检测时间' },
  ], 'FraudRecords')
}
onMounted(loadAll)
</script>

<style scoped>
.fraud-card {
  border: 1px solid #e4e7ed;
  border-left: 4px solid #909399;
  border-radius: 6px;
  padding: 10px 16px;
  min-width: 150px;
  background: #fafafa;
}
.fraud-label { color: #909399; font-size: 12px; margin-bottom: 4px; }
.fraud-value { font-size: 22px; font-weight: 600; color: #303133; }
.fraud-types { margin-top: 4px; }
</style>
