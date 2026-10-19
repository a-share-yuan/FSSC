<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="budget-occupy-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">预算占用流水</span>
        </div>
      </template>
      <el-form :inline="true" :rules="filterRules" size="small" label-width="100px" style="margin-bottom:12px">
        <el-form-item label="操作类型">
          <el-select v-model="filter.operType" placeholder="全部" clearable style="width:140px" @change="loadList">
            <el-option label="占用" value="OCCUPY" />
            <el-option label="释放" value="RELEASE" />
            <el-option label="核销" value="WRITE_OFF" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="filter.bizType" placeholder="全部" clearable filterable style="width:180px" @change="loadList">
            <el-option v-for="t in bizTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filter.keyword" placeholder="单据号/备注" clearable @keyup.enter="loadList" style="width:180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
          <el-button @click="doExport">导出 CSV</el-button>
        </el-form-item>
      </el-form>
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="list" v-else size="small" stripe border show-summary :summary-method="getSummary" empty-text="暂无数据" v-loading="loading" @row-dblclick="openRowDetail">
        <el-table-column prop="budgetItemId" label="预算明细ID" width="120" sortable>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openBalance(row)">{{ row.budgetItemId }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="operType" label="操作类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="operTypeTag(row.operType)">{{ operTypeLabel(row.operType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120" align="right" sortable>
          <template #default="{ row }">
            <span :class="row.amount >= 0 ? 'text-green' : 'text-red'">{{ fmt(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务类型" width="130" :formatter="(row, col, val) => bizTypeLabel(val)" />
        <el-table-column prop="bizNo" label="单据号" width="160" sortable />
        <!-- R5-D-269：操作人改「姓名（工号）」可读口径（后端批查 sys_user 回填）；查不到回退裸 id -->
        <el-table-column prop="operUserId" label="操作人" width="130" sortable :formatter="(row) => operatorText(row)" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="160" sortable :formatter="(row, col, val) => formatDateTime(val)" />
        <!-- D-V4R-39：审批收尾的「占用转消耗」撞行锁失败时，日志让用户去 预算›占用 点【核销】——
             接口 /budget/occupy/write-off 与权限点 budget:occupy:do 一直都在，缺的只是这扇门。 -->
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.operType === 'OCCUPY'" link type="warning" size="small" @click="openWriteOff(row)">核销</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page.current" v-model:page-size="page.size"
        :total="page.total" :page-sizes="[10, 20, 50]"
        layout="total, prev, pager, next, jumper"
        style="margin-top:12px;justify-content:flex-end"
        @size-change="loadList" @current-change="loadList"
      />
    </el-card>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />

<!-- P2-14 预算占用→余额双向穿透 -->
<el-dialog v-model="balanceVisible" title="预算项余额联查" width="480px" destroy-on-close>
  <el-descriptions v-if="balanceInfo" :column="2" border size="small">
    <el-descriptions-item label="预算明细ID">{{ balanceInfo.id }}</el-descriptions-item>
    <el-descriptions-item label="方案ID">{{ balanceInfo.schemeId ?? '—' }}</el-descriptions-item>
    <el-descriptions-item label="预算总额">{{ fmt(balanceInfo.totalAmount) }}</el-descriptions-item>
    <el-descriptions-item label="调整额">{{ fmt(balanceInfo.adjustAmount) }}</el-descriptions-item>
    <el-descriptions-item label="已占用">{{ fmt(balanceInfo.occupyAmount) }}</el-descriptions-item>
    <el-descriptions-item label="已使用">{{ fmt(balanceInfo.usedAmount) }}</el-descriptions-item>
    <el-descriptions-item label="可用余额">
      <b :style="{ color: availAmount >= 0 ? '#67c23a' : '#f56c6c' }">{{ fmt(availAmount) }}</b>
    </el-descriptions-item>
    <el-descriptions-item label="币种">{{ balanceInfo.currencyCode || 'CNY' }}</el-descriptions-item>
  </el-descriptions>
  <el-empty v-else description="未找到该预算明细" :image-size="60" />
</el-dialog>

<!-- D-V4R-39：占用核销入口（后端 budget:occupy:do 闸在，缺的是门） -->
<el-dialog v-model="woVisible" title="核销预算占用" width="470px" destroy-on-close>
  <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px"
    title="核销＝把这笔占用转成已用（消耗）。只在系统自动结转失败、账本上出现「只有占用没有核销」的单子上用；操作会留流水，不可撤销。" />
  <el-form ref="woFormRef" :model="woForm" :rules="woRules" label-width="100px" size="small">
    <el-form-item label="预算明细ID" prop="budgetItemId"><el-input-number v-model="woForm.budgetItemId" :min="1" style="width:100%" /></el-form-item>
    <el-form-item label="核销金额" prop="amount"><el-input-number v-model="woForm.amount" :min="0.01" :precision="2" style="width:100%" /></el-form-item>
    <el-form-item label="业务类型" prop="bizType">
      <el-select v-model="woForm.bizType" style="width:100%">
        <el-option v-for="t in bizTypes" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
    </el-form-item>
    <el-form-item label="单据号"><el-input v-model="woForm.bizNo" /></el-form-item>
    <el-form-item label="单据ID" prop="bizId"><el-input-number v-model="woForm.bizId" :min="1" style="width:100%" /></el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="woVisible = false">取消</el-button>
    <el-button type="warning" :loading="woBusy" @click="doWriteOff">确认核销</el-button>
  </template>
</el-dialog>
</template>

<script setup>
import { bizTypeLabel } from '@/constants/enums'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { budgetApi } from '../../api'
import { exportCsv } from '../../utils/csv'
import SkeletonList from '../../components/SkeletonList.vue'
import { commonRules, validateForm } from '../../composables/useFormRules'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const list = ref([])
const loading = ref(false)
const filter = reactive({ operType: null, bizType: null, keyword: '' })

// 表单校验规则（筛选条件均为可选）
const filterRules = reactive({})
const page = reactive({ current: 1, size: 10, total: 0 })

const bizTypes = [
  { label: '员工费用报销', value: 'EXP_CLAIM' },
  { label: '事前申请', value: 'EXP_APPLY' },
  { label: '对公付款', value: 'EXP_PAY' },
  { label: '员工借款', value: 'EXP_LOAN' },
  { label: '员工还款', value: 'EXP_REPAY' },
  { label: '资金付款', value: 'TR_PAY' },
]

// D-V4R-39：核销入口——从一条「占用」流水带出同一枚预算明细与同一张单据，金额取该笔占用的绝对值
const woVisible = ref(false)
const woBusy = ref(false)
const woForm = reactive({ budgetItemId: null, amount: 0, bizType: null, bizId: null, bizNo: '' })
// 第2批：预算核销弹窗接入中央框架（原只有一句组合 toast，字段级错误不落位）
const woFormRef = ref(null)
const woRules = {
  budgetItemId: [{ required: true, type: 'number', min: 1, message: '请输入预算明细ID（须大于 0）' }],
  amount: [{ required: true, type: 'number', min: 0.01, message: '请输入核销金额（须大于 0）' }],
  bizType: commonRules.select('请选择业务类型'),
  bizId: [{ required: true, type: 'number', min: 1, message: '请输入单据ID（须大于 0）' }],
}
function openWriteOff(row) {
  woForm.budgetItemId = row.budgetItemId
  woForm.amount = Math.abs(Number(row.amount || 0))
  woForm.bizType = row.bizType || filter.bizType || null
  woForm.bizId = row.bizId || null
  woForm.bizNo = row.bizNo || ''
  woVisible.value = true
}
async function doWriteOff() {
  if (!(await validateForm(woFormRef.value))) return
  if (!woForm.budgetItemId || !(Number(woForm.amount) > 0) || !woForm.bizType || !woForm.bizId) {
    ElMessage.warning('预算明细ID／核销金额／业务类型／单据ID 都要填齐才能核销')
    return
  }
  woBusy.value = true
  try {
    await budgetApi.occupyWriteOff({
      budgetItemId: Number(woForm.budgetItemId), amount: Number(woForm.amount),
      bizType: woForm.bizType, bizId: Number(woForm.bizId), bizNo: woForm.bizNo || null,
    })
    ElMessage.success('已核销：这笔占用已转消耗，账本会多一条 WRITE_OFF 流水')
    woVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error('核销失败：' + (e?.message || e))
  } finally { woBusy.value = false }
}

async function loadList() {
  loading.value = true
  try {
    const r = await budgetApi.occupyPage({
      pageNum: page.current, pageSize: page.size,
      operType: filter.operType || undefined,
      bizType: filter.bizType || undefined,
      keyword: filter.keyword || undefined,
    })
    list.value = r?.list || r?.records || []
    page.total = r?.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function resetFilter() {
  filter.operType = null; filter.bizType = null; filter.keyword = ''
  page.current = 1
  loadList()
}

function operTypeLabel(v) {
  return { OCCUPY: '占用', RELEASE: '释放', WRITE_OFF: '核销' }[v] || v
}
// R5-D-269：操作人显示「姓名（工号）」；姓名缺失回退裸 id，工号缺失只显姓名——绝不臆造
function operatorText(row) {
  const name = row.operUserName
  if (!name) return row.operUserId == null ? '—' : String(row.operUserId)
  return row.operUserNo ? `${name}（${row.operUserNo}）` : name
}
function operTypeTag(v) {
  return { OCCUPY: 'warning', RELEASE: 'info', WRITE_OFF: 'success' }[v] || 'info'
}
function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function getSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, i) => {
    if (i === 0) { sums[i] = '合计'; return }
    if (col.property === 'amount') {
      sums[i] = fmt(data.reduce((s, r) => s + Number(r.amount || 0), 0))
    } else sums[i] = ''
  })
  return sums
}

function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

// P2-14:点击预算明细ID 联查该项预算余额(总额/调整/占用/使用/可用)
const balanceVisible = ref(false)
const balanceInfo = ref(null)
const availAmount = computed(() => {
  const b = balanceInfo.value
  if (!b) return 0
  return Number(b.totalAmount || 0) + Number(b.adjustAmount || 0) - Number(b.occupyAmount || 0) - Number(b.usedAmount || 0)
})
async function openBalance(row) {
  if (!row.budgetItemId) return
  balanceInfo.value = null
  balanceVisible.value = true
  try {
    const r = await budgetApi.itemPage({ itemId: row.budgetItemId, pageNum: 1, pageSize: 1 })
    const list = r?.list || r?.records || []
    const hit = list.find((x) => x.id === row.budgetItemId) || list[0] || null
    balanceInfo.value = hit
  } catch (e) { ElMessage.error(e.message) }
}

// 41号审查 P1-9：导出当前查询结果；R5-D-269：操作人同步「姓名（工号）」可读口径
function doExport() {
  const rows = list.value.map((r) => ({ ...r, operatorText: operatorText(r) }))
  exportCsv(rows, [
    { prop: 'budgetItemId', label: '预算明细ID' }, { prop: 'operType', label: '操作类型' },
    { prop: 'amount', label: '金额' }, { prop: 'bizType', label: '业务类型' }, { prop: 'bizNo', label: '单据号' },
    { prop: 'operatorText', label: '操作人' }, { prop: 'remark', label: '备注' }, { prop: 'createTime', label: '时间' },
  ], 'BudgetOccupy')
}

onMounted(() => loadList())
</script>

<style scoped>
.budget-occupy-page { min-height: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; }
.text-green { color: #67c23a; }
.text-red { color: #f56c6c; }
</style>
