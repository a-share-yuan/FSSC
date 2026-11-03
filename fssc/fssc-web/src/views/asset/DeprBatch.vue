<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>{{ $t('asset.deprBatch.Depreciation') }}</b>
        <span style="color:#909399; font-size:12px">{{ $t('asset.deprBatch.Monthly') }}</span>
        <el-button type="info" size="small" style="margin-left:auto" @click="reload">{{ $t('common.refresh') }}</el-button>
      </div>
    </template>

    <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap">
      <!-- 计提期间是批单唯一的必输项（留空会被后端按当前月补值），故包成 el-form-item 拿红星 + 走提交校验 -->
      <el-form ref="periodFormRef" :model="periodModel" :rules="formRules" :inline="true" size="small">
        <el-form-item label="计提期间" prop="period" style="margin-bottom:0">
          <el-date-picker v-model="period" type="month" placeholder="计提期间" value-format="YYYY-MM" clearable style="width:150px" />
        </el-form-item>
      </el-form>
      <el-button v-permission="'sharing:voucher:generate'" type="primary" @click="doSubmit">发起月度计提审批</el-button>
      <el-button v-permission="'sharing:voucher:generate'" type="success" plain @click="doCreate">仅生成草稿批单</el-button>
      <el-button type="info" size="small" @click="exportCsv">导出</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
    </div>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="batches" size="small" border stripe v-else empty-text="暂无数据" v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="handleView">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="batchNo" label="批号" width="180"  sortable />
      <el-table-column prop="period" label="计提期间" width="110"  sortable />
      <el-table-column prop="assetCount" label="待计提数" width="90" align="right"  sortable />
      <el-table-column label="应计提总额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="sttType(row.status)" size="small">{{ statusTxt(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">          <InlineActions :visible="true" @view="handleView(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="true" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <el-button v-if="row.status === BillStatus.DRAFT" v-permission="'sharing:voucher:generate'" link size="small" type="primary" @click="submitById(row)">提交审批</el-button>
          <!-- 批单落账后生成的折旧凭证为 DRAFT，提供查看与过账入口（DRAFT→POSTED 并落总账） -->
          <el-button v-if="row.status === 'DONE'" v-permission="'sharing:voucher:list'" link size="small" type="success" @click="openVouchers(row)">凭证/过账</el-button>
          <el-button v-show="false" link size="small" type="warning" @click="execute(row)">执行落账</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="pageSize" :current-page="pageNum" @current-change="(p) => { pageNum = p; load() }" />

    <!-- 折旧凭证查看与过账弹窗（批单 DONE 后逐资产生成的 ASSET_DEPRECIATION 凭证） -->
    <el-dialog v-model="dlgVoucher" title="折旧凭证查看与过账" width="880px">
      <div style="margin-bottom:8px; color:#909399; font-size:12px; line-height:1.6">
        折旧批「{{ curBatch?.batchNo }}」（期间 {{ curBatch?.period || '—' }}）落账时逐资产生成的折旧凭证
        （source_type = ASSET_DEPRECIATION）。过账：DRAFT → POSTED 并落总账；
        月结前置校验要求期间内无 DRAFT 凭证，请在月结前完成过账。
      </div>
      <el-table :data="vouchers" v-loading="vLoading" size="small" border stripe max-height="420"
                empty-text="暂无折旧凭证" @selection-change="handleVSelection">
        <el-table-column type="selection" width="45" :selectable="(row) => row.status !== 'POSTED'" />
        <el-table-column prop="voucherNo" label="凭证号" width="170" fixed sortable />
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="期间" width="100">
          <template #default="{ row }">{{ row.periodYear }}-{{ String(row.periodMonth || 0).padStart(2, '0') }}</template>
        </el-table-column>
        <el-table-column label="借方" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.debitTotal) }}</template>
        </el-table-column>
        <el-table-column label="贷方" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.creditTotal) }}</template>
        </el-table-column>
        <el-table-column label="借贷平衡" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isBalanced === 1" type="success" size="small">平衡</el-tag>
            <el-tag v-else type="danger" size="small">不平</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'POSTED'" type="primary" size="small">已记账</el-tag>
            <el-tag v-else type="info" size="small">草稿</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'POSTED'" v-permission="'sharing:voucher:post'" link type="primary" size="small" @click="postOne(row)">过账</el-button>
            <span v-else style="color:#909399; font-size:12px">—</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="dlgVoucher = false">关闭</el-button>
        <el-button v-permission="'sharing:voucher:post'" type="primary" :disabled="!vSelRows.length" @click="postSelected">过账选中({{ vSelRows.length }})</el-button>
      </template>
    </el-dialog>
    <!-- 全链路追溯抽屉（单据链路/付款/凭证/审批轨迹） -->
    <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="FA_DEPRECIATION" />
  </el-card>
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deprBatchApi, sharingApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { useFormRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import InlineActions from '@/components/InlineActions.vue'
import BillTraceDrawer from '@/components/BillTraceDrawer.vue'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { useApprovalScope } from '../../composables/useApprovalScope'
import { useApprovalDialog } from '@/composables/useApprovalDialog'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

// 审批按钮归属门控：仅当该单据存在派给当前用户的待办任务时才显示，
// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()

const selRows = ref([])
const batches = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const period = ref('')

const periodFormRef = ref(null)
const { rules: formRules } = useFormRules('DEPR_BATCH')
const { submit: guardPeriod } = useSubmitGuard(periodFormRef)
const periodModel = computed(() => ({ period: period.value }))

function fmt(v) { return (v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function statusTxt(s) { return STATUS_LABEL[s] || s }
function sttType(s) { return STATUS_TYPE[s] || 'info' }

async function load() {
  loading.value = true
  try {
    const r = await deprBatchApi.page({ pageNum: pageNum.value, pageSize: pageSize.value })
    batches.value = r.list || []
    total.value = r.total || 0
  } finally { loading.value = false }
}
async function reload() { pageNum.value = 1; load() }

async function doSubmit() {
  await guardPeriod(async () => {
    await ElMessageBox.confirm('确认对本期间资产生成折旧批单并发起折旧计提审批？', '发起月度计提', { type: 'warning' })
    const b = await deprBatchApi.submit(period.value)
    ElMessage.success(t('asset.assetCard.Items')); load()
  })
}
async function doCreate() {
  await guardPeriod(async () => {
    await ElMessageBox.confirm('生成草稿批单（不发起审批）？', '生成批单', { type: 'warning' })
    const b = await deprBatchApi.create(period.value)
    ElMessage.success('草稿批单已生成：' + b.batchNo); load()
  })
}
async function submitById(row) {
  await ElMessageBox.confirm('确认发起批单「' + row.batchNo + '」审批？', '提交审批', { type: 'warning' })
  await deprBatchApi.submitById(row.id); ElMessage.success(t('asset.deprBatch.Submitted')); load()
}
async function execute(row) {
  await ElMessageBox.confirm('确认对批单「' + row.batchNo + '」执行落账？', '执行落账', { type: 'warning' })
  await deprBatchApi.execute(row.id); ElMessage.success(t('asset.deprBatch.Posting')); load()
}

// 折旧凭证查看与过账
// 后端已提供凭证过账端点：POST /tenant/sharing/voucher/{id}/post（权限 sharing:voucher:post）
// 及批量 POST /tenant/sharing/voucher/batch-post。批单执行落账时逐资产生成 DRAFT 凭证；
// 月结（GL_MONTH_CLOSE）前置校验会因期间存在 DRAFT 凭证而拦截（并非自动过账），
// 故在批单页面提供过账入口，由资产核算岗在月结前完成 DRAFT→POSTED。
const dlgVoucher = ref(false)
const curBatch = ref(null)
const vouchers = ref([])
const vLoading = ref(false)
const vSelRows = ref([])
function handleVSelection(rows) { vSelRows.value = (rows || []).filter((r) => r && r.status !== 'POSTED') }

async function openVouchers(row) {
  curBatch.value = row
  dlgVoucher.value = true
  vLoading.value = true
  vouchers.value = []
  try {
    const [y, m] = String(row.period || '').split('-').map(Number)
    const found = []
    // voucher/page 仅分页（按 id 倒序），折旧凭证为最新生成——扫描前 3 页按来源类型+期间过滤即可覆盖当月批次
    for (let p = 1; p <= 3; p++) {
      const r = await sharingApi.voucherPage({ pageNum: p, pageSize: 100 })
      const list = r?.list || []
      found.push(...list.filter((v) => v.sourceType === 'ASSET_DEPRECIATION'
        && (!y || Number(v.periodYear) === y) && (!m || Number(v.periodMonth) === m)))
      const t = Number(r?.total || 0)
      if (!list.length || (t && p * 100 >= t)) break
    }
    vouchers.value = found
    if (!found.length) ElMessage.info(t('asset.deprBatch.Depreciation2'))
  } catch (e) { ElMessage.error(e.message) } finally { vLoading.value = false }
}

async function postOne(v) {
  try {
    await ElMessageBox.confirm(`确认过账凭证「${v.voucherNo}」？过账后凭证由草稿变为已记账并登记总账`, '凭证过账', { type: 'warning' })
  } catch { return }
  try {
    await sharingApi.voucherPost(v.id)
    ElMessage.success(`${t('useBill.main.Voucher')} ${v.voucherNo} ${t('asset.deprBatch.Posted')}`)
    openVouchers(curBatch.value)
  } catch (e) { ElMessage.error(e.message) }
}

async function postSelected() {
  const ids = (vSelRows.value || []).map((v) => v.id).filter(Boolean)
  if (!ids.length) { ElMessage.warning(t('asset.deprBatch.Select')); return }
  try {
    await ElMessageBox.confirm(`确认批量过账选中的 ${ids.length} 张凭证？`, '批量过账', { type: 'warning' })
  } catch { return }
  try {
    const r = await sharingApi.voucherBatchPost(ids)
    const vals = Object.values(r || {})
    const ok = vals.filter((s) => s === 'OK').length
    const bad = vals.filter((s) => s !== 'OK')
    if (!bad.length) ElMessage.success(`批量过账完成：成功 ${ok}/${ids.length} ${t('asset.deprBatch.Sheets')}`)
    else ElMessage.warning(`批量过账完成：成功 ${ok} 张，失败 ${bad.length} 张；${bad.slice(0, 2).join('；')}`)
    openVouchers(curBatch.value)
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)

function exportCsv() {
  if (!batches.value.length) { ElMessage.warning(t('useBill.main.There')); return }
  const cols = [{ prop: 'batchNo', label: t('asset.deprBatch.Batch') }, { prop: 'period', label: t('asset.deprBatch.Accrual') }, { prop: 'assetCount', label: t('asset.deprBatch.Pending') }, { prop: 'createTime', label: t('common.createTime') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = batches.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `DeprBatch_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('useBill.main.CSV'))
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('utils.csv.Select')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('utils.csv.Cannot')); return }
  const header = keys.join(',')
  const lines = selRows.value.map((row) => keys.map((k) => {
    const v = row[k]
    if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `DeprBatch_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`${t('utils.csv.Exported')} ${selRows.value.length} 条记录`)
}

// InlineActions approve/reject/view/trace handlers

async function handleView(row) {
  try {
    const d = await deprBatchApi.detail(row.id)
    await showRowDetail({
      batchNo: d.batchNo,
      period: d.period,
      assetCount: d.assetCount,
      totalAmount: d.totalAmount,
      status: statusTxt(d.status),
    }, { title: '批单详情', labels: { batchNo: '批号', period: '期间', assetCount: '待计提数', totalAmount: '应计提总额', status: t('common.status') } })
  } catch (e) { ElMessage.error(e.message) }
}

const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id
  traceVisible.value = true
}

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: load })
</script>

<style scoped>
.pager { margin-top: 12px; justify-content: flex-end; }
</style>