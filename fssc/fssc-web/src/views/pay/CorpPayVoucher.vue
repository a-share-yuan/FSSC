<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap">
        <b>对公付款凭证（付款 → 总账）</b>
        <el-select v-model="sourceType" placeholder="业务类型" clearable style="width: 220px">
          <!-- R5-D-078：默认口径＝对公付款族（页名与实际取数同口径，后端 page 已支持逗号族 IN 过滤）；
               选项按共享词表（= 库内实际枚举，bizEntry.VOUCHER_SOURCE_LABEL）动态生成——
               原先硬编码 4 值，20+ 类里 16+ 类既出现在列表又无法被筛出。 -->
          <el-option label="对公付款族（默认）" value="EXPENSE_PAY,EXPENSE_SETTLE,TREASURY_PAY" />
          <el-option v-for="(label, code) in VOUCHER_SOURCE_LABEL" :key="code" :label="label" :value="code" />
        </el-select>
        <el-select v-model="status" placeholder="状态" clearable style="width: 140px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已记账" value="POSTED" />
        </el-select>
<el-button type="primary" size="small" @click="load">查询</el-button>
        <el-button v-if="selDraftIds.length" v-permission="'sharing:voucher:post'" type="success" size="small" :loading="batchPosting" @click="batchPost">
          批量记账（{{ selDraftIds.length }}）
        </el-button>
        <span style="margin-left:auto; color:#909399; font-size:12px">
          凭证过账后自动落总账；勾选凭证可查看或重新落账
        </span>
      </div>
    </template>

    <el-table :data="rows" size="small" border stripe :max-height="420" highlight-current-row @current-change="onSelect" @selection-change="handleSelectionChange" empty-text="暂无数据" @row-dblclick="openTrace">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="voucherNo" label="凭证号" width="170" fixed  sortable />
      <el-table-column label="业务类型" width="120">
        <template #default="{ row }"><el-tag size="small" :type="sourceTypeTag(row.sourceType)">{{ voucherSourceLabel(row.sourceType) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip  sortable />
      <el-table-column label="期间" width="100">
        <template #default="{ row }">{{ row.periodYear }}-{{ String(row.periodMonth).padStart(2, '0') }}</template>
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
<el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === BillStatus.POSTED" type="primary" size="small">已记账</el-tag>
          <el-tag v-else type="info" size="small">草稿</el-tag>
        </template>
      </el-table-column>
      <!-- P2-4 来源单据列：单据号 + 状态，随手核对来源（孤儿凭证显示—） -->
      <el-table-column label="来源单据" width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.sourceBillNo && isExpenseFamilyVoucherSource(row.sourceType)">
            <span>{{ row.sourceBillNo }}</span>
            <el-tag v-if="row.sourceBillStatus" size="small" :type="statusType(row.sourceBillStatus)" style="margin-left: 4px">{{ statusLabel(row.sourceBillStatus) }}</el-tag>
          </template>
          <!-- R5-D-076：来源列不直出裸主键（「#主键」外显＝与 R5-D-039 裸主键族同判据，跨域还易串号）。 -->
          <span v-else-if="row.billId && !isExpenseFamilyVoucherSource(row.sourceType)" style="color: #909399">{{ voucherSourceLabel(row.sourceType) }}（来源单号按域追溯）</span>
          <!-- 费用族查不到来源单据（或本就不带来源）＝真孤儿凭证，显「孤儿凭证」而非 #id -->
          <span v-else style="color: #c45656">｜孤儿凭证</span>
        </template>
      </el-table-column>
      <el-table-column label="总账落账" width="100" align="center">
        <template #default="{ row }">
          <!-- R5-D-075：落账态来自列表接口一次性聚合（row.glEntryCount，后端 SQL 侧按 voucher_id 分组计数），
               不再由页内逐张探测；聚合缺失＝「校验失败」显式态（可点击重试），禁止降级显示成「未落账」 -->
          <el-tag v-if="row.glEntryCount > 0" type="success" size="small">已落账</el-tag>
          <el-tag v-else-if="row.glEntryCount === 0" type="warning" size="small">未落账</el-tag>
          <el-tag v-else type="danger" size="small" style="cursor: pointer" title="列表未带回落账聚合结果，点击重试校验" @click="retryGlCheck(row)">校验失败</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <el-button v-if="row.status !== BillStatus.POSTED" v-permission="'sharing:voucher:post'" link type="primary" @click="post(row)">记账落账</el-button>
          <el-button v-permission="'sharing:voucher:post'" link type="warning" @click="repost(row)">重新落账</el-button>
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 10px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="pageNum"
      @current-change="(p) => { pageNum = p; load() }" />

    <!-- 选中的凭证分录 -->
    <el-card v-if="current" header="凭证分录明细" shadow="never" style="margin-top: 14px">
      <el-descriptions :column="4" border size="small" style="margin-bottom: 10px">
        <el-descriptions-item label="凭证号">{{ current.voucherNo }}</el-descriptions-item>
        <!-- R5-D-076：明细卡同样不直出裸主键；无可信单号时引导走「追溯」 -->
        <el-descriptions-item label="来源单据">{{ current.sourceBillNo || '按域追溯' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(current.status) }}</el-descriptions-item>
        <el-descriptions-item label="摘要">{{ current.summary }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="lines" size="small" border @selection-change="handleSelectionChange" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="lineNo" label="行" width="60"  sortable />
        <el-table-column prop="subjectCode" label="科目编码" width="110"  sortable />
        <el-table-column prop="subjectName" label="科目名称" min-width="150" show-overflow-tooltip  sortable />
        <el-table-column prop="summary" label="摘要" min-width="170" show-overflow-tooltip  sortable />
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'DEBIT' ? 'danger' : 'success'" size="small">{{ row.direction === 'DEBIT' ? '借' : '贷' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="130" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.amount) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sharingApi, glApi } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { bizTypeOfVoucherSource, voucherSourceLabel, isExpenseFamilyVoucherSource } from '../../utils/bizEntry'

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.billId || row.id
  traceBizType.value = bizTypeOfVoucherSource(row.sourceType)
  traceVisible.value = true
}

const selRows = ref([])
// R5-D-078：默认＝对公付款族（页名「对公付款凭证」与取数同口径；清空筛选可看全库）
const sourceType = ref('EXPENSE_PAY,EXPENSE_SETTLE,TREASURY_PAY')
const status = ref(null)
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const current = ref(null)
const lines = ref([])

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }

// D-DEC-264 + R5-D-077：原先页内私有表只有对公支付四键，而本页列的是全部凭证（库内 source_type 已达 20+ 种，
// r325 普查：AR_MONTH_CLOSE/ASSET_DEPRECIATION/BAD_DEBT_PROV/CONTRACT/FA_*/GL_*/TREASURY_PAY/RECEIVABLE…），
// 其余一律直出英文码 ⇒ 词表以 bizEntry.VOUCHER_SOURCE_LABEL 为单一出处（与凭证台账同词）。
// 治理判据（走查）：op_voucher.source_type 键集与该词表差集必须为空——每接一类自动凭证须同步补词表。
function sourceTypeTag(s) { return s === 'EXPENSE_PAY' ? 'primary' : s === 'EXP_CLAIM' ? 'success' : s === 'EXP_TRAVEL' ? 'warning' : 'info' }

async function load() {
  try {
    const p = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (sourceType.value) p.sourceType = sourceType.value
    if (status.value) p.status = status.value
    const d = await sharingApi.voucherPage(p)
    rows.value = d.list || []
    total.value = Number(d.total || 0)
    // R5-D-075：落账态由列表接口一次性返回（row.glEntryCount，后端 SQL 侧按 voucher_id 聚合）。
    // 原页内 for 循环逐张串行探测（逐行调用 posted 接口）已删除——探测失败不得再降级显示「未落账」；
    // 聚合缺失的行显示「校验失败」并可由 retryGlCheck 人工重试。
    if (current.value) {
      const found = rows.value.find(x => x.id === current.value.id)
      if (!found) current.value = null
    }
  } catch (e) { ElMessage.error(e.message) }
}

/** R5-D-075：仅在列表聚合缺失（行显示「校验失败」）时由人工点击触发的单张重试校验；
 *  失败仍显式提示「校验失败」并可再点，绝不把探测失败降级成「未落账」。 */
async function retryGlCheck(row) {
  try {
    const ok = await glApi.posted(row.id)
    row.glEntryCount = ok ? 1 : 0
  } catch (e) { ElMessage.error('落账校验失败，请重试：' + (e.message || '请求异常')) }
}

async function onSelect(row) {
  if (!row) return
  current.value = row
  try {
    lines.value = await sharingApi.voucherLines(row.id) || []
  } catch (e) { ElMessage.error(e.message) }
}

async function post(row) {
  try {
    await sharingApi.voucherPost(row.id)
    ElMessage.success('凭证已记账并入账总账')
    onSelect(row); load()
  } catch (e) { ElMessage.error(e.message) }
}

async function repost(row) {
  try {
    const n = await glApi.repost(row.id)
    ElMessage.success('重新落账完成，分录 ' + (n || 0) + ' 条')
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// P1-2: 批量记账（仅对未 POSTED 的勾选行）
const selDraftIds = ref([])
const batchPosting = ref(false)

async function batchPost() {
  if (!selDraftIds.value.length) { ElMessage.warning('请先勾选未记账的凭证'); return }
  batchPosting.value = true
  try {
    const map = await sharingApi.voucherBatchPost(selDraftIds.value)
    const ok = Object.keys(map).filter((k) => map[k] === 'OK')
    const fail = Object.keys(map).filter((k) => map[k] !== 'OK')
    if (ok.length) ElMessage.success(`批量记账成功 ${ok.length} 张${fail.length ? '，失败 ' + fail.length + ' 张' : ''}`)
    if (fail.length) {
      const detail = fail.map((k) => `#${k}: ${map[k]}`).join('；')
      ElMessage.warning('失败明细：' + detail)
    }
    selRows.value = []
    load()
  } catch (e) { ElMessage.error(e.message || '批量记账失败') } finally { batchPosting.value = false }
}

onMounted(async () => {
  await load()
  if (rows.value.length) await onSelect(rows.value[0])
})

function exportCsv() {
  if (!lines.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'lineNo', label: '行' }, { prop: 'subjectCode', label: '科目编码' }, { prop: 'subjectName', label: '科目名称' }, { prop: 'summary', label: '摘要' }, { prop: 'amount', label: '金额' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = lines.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `CorpPayVoucher_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}


function handleSelectionChange(val) { selRows.value = val; selDraftIds.value = val.filter((r) => r.status !== 'POSTED').map((r) => r.id) }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning('请先勾选要导出的记录'); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning('无法获取列信息'); return }
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
  a.download = `CorpPayVoucher_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>