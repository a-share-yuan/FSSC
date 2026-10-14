<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <div class="page-header" style="margin-bottom: 16px;">
      <h2 style="margin: 0; font-size: 18px; font-weight: 600;">机票查询</h2>
    </div>
    <div style="margin-bottom:8px;display:flex;gap:8px;align-items:center;flex-wrap:wrap">
      <el-input v-model="kw" placeholder="事前申请单号/申请人/标题" style="width:220px" clearable @keyup.enter="search" @input="debouncedSearch" />
      <el-select v-model="used" clearable placeholder="核销与否" style="width:130px" @change="search">
        <el-option label="已核销" :value="1" />
        <el-option label="未核销" :value="0" />
      </el-select>
      <el-button size="small" type="primary" @click="search">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
      <el-button size="small" @click="exportCsv">导出模板</el-button>
    </div>
    <el-alert type="info" :closable="false" style="margin-bottom:8px"
      title="机票结算申请：行政人员统一报销员工机票。查询涉及机票的事前申请单，选择后创建报销单进入差旅培训报销" />
    <el-table :data="pagedData" size="small" stripe border v-loading="loading" @selection-change="onSel" @row-dblclick="handleView">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" label="事前申请单号" width="160"  sortable />
      <el-table-column prop="applyUserName" label="申请人" width="100"  sortable />
      <el-table-column prop="title" label="标题" min-width="150"  sortable />
      <el-table-column prop="applyDate" label="申请时间" width="110"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
      <el-table-column label="金额" width="110">
        <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ formatMoney(row.totalAmount) }}</span></template>
      </el-table-column>
      <el-table-column label="核销" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.relationUsed" type="success" size="small">已核销</el-tag>
          <el-tag v-else type="info" size="small">未核销</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">          <InlineActions :visible="true" @view="handleView(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="true" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <el-button link size="small" type="primary" :disabled="row.relationUsed" @click="createClaim(row)">创建报销单</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
    </el-table>
    <TablePager :pager="pager" @change="load" />
    <div style="margin-top:8px;display:flex;gap:8px;align-items:center">
      <el-button size="small" type="primary" :disabled="!sel.length" @click="batchCreate">批量创建报销单</el-button>
      <span style="color:#909399">已选 {{ sel.length }} 条</span>
    </div>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="EXP_APPLY" />
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billApi } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import InlineActions from '@/components/InlineActions.vue'
import { formatDate, formatDateTime } from '@/utils/format'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import { useApprovalScope } from '../../composables/useApprovalScope'
import { useApprovalDialog } from '@/composables/useApprovalDialog'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'

// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const router = useRouter()
const rows = ref([])
const loading = ref(false)
// P0 遗留专项：后端 air-ticket/page 支持 keyword/used 服务端过滤 + relationUsed 回填，消除 N+1 与 100 条截断
const pager = useTablePager({ mode: 'server', storageKey: 'air_ticket_query' })
const { pagedData } = pager
const kw = ref('')
const used = ref(null)
const sel = ref([])

function onSel(selRows) { sel.value = selRows }
function formatMoney(val) {
  return Number(val || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

async function load() {
  loading.value = true
  try {
    await pager.load(async ({ pageNum, pageSize }) => {
      const r = await billApi.airTicketPage({ pageNum, pageSize, keyword: kw.value || undefined, used: used.value ?? undefined })
      rows.value = r.list || []
      return { list: rows.value, total: r.total || 0 }
    })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}
/** 搜索/筛选条件变化 → 回到第 1 页 */
function search() { pager.resetPage(); load() }
function reset() { kw.value = ''; used.value = null; search() }
/** P1-2 输入即过滤，防抖 300ms，避免高频请求 */
let searchTimer = null
function debouncedSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { search() }, 300)
}
async function createClaim(row) {
  try {
    const id = await billApi.convertApply(row.id)
    ElMessage.success(`已创建报销单草稿，可前往「差旅培训报销」完善后提交`)
    load()
    router.push('/expense/claim-travel')
  } catch (e) { ElMessage.error(e.message) }
}
async function batchCreate() {
  if (!sel.value.length) { ElMessage.warning('请先勾选要创建报销草稿的事前申请'); return }
  try { await ElMessageBox.confirm('将为勾选的 ' + sel.value.length + ' 条事前申请批量创建报销单草稿，确认继续？', '批量创建报销草稿', { type: 'warning' }) } catch { return }
  let ok = 0, fail = 0
  for (const row of sel.value) {
    try { await billApi.convertApply(row.id); ok++ } catch (e) { fail++; ElMessage.error(`创建失败：${row.billNo} ${e.message}`) }
  }
  ElMessage.success(`批量创建完成：成功 ${ok} 条${fail ? '，失败 ' + fail + ' 条' : ''}`); load()
}
function exportCsv() {
  if (!rows.value.length) return ElMessage.warning('无数据')
  const cols = [{ prop: 'billNo', label: '事前申请单号' }, { prop: 'applyUserName', label: '申请人' }, { prop: 'title', label: '标题' }, { prop: 'applyDate', label: '申请时间' }, { prop: 'totalAmount', label: '金额' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((r) => cols.map((c) => String(r?.[c.prop] ?? '')).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })); a.download = `机票事前申请_${new Date().toISOString().slice(0, 10)}.csv`; a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出')
}
onMounted(load)
// InlineActions handlers

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: load })

function handleView(row) {
  ElMessage.info('事前申请：' + (row.billNo || row.id))
}
</script>
