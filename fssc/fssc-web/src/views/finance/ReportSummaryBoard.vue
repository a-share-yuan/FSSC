<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="report-summary-board">
    <el-card>
      <!-- Toolbar -->
      <div class="toolbar">
        <span class="label">年度：</span>
        <el-select v-model="year" placeholder="选择年度" style="width:120px" @change="onParamChange">
          <el-option v-for="y in yearOptions" :key="y" :label="y + '年'" :value="y" />
        </el-select>
        <span class="label">月份：</span>
        <el-select v-model="month" placeholder="选择月份" style="width:100px" @change="onParamChange">
          <el-option v-for="m in 12" :key="m" :label="m + '月'" :value="m" />
        </el-select>
        <el-button type="primary" :loading="refreshing" @click="onRefresh">刷新预聚合</el-button>
        <el-button @click="doExport">导出</el-button>
      </div>

      <!-- Stats Cards -->
      <el-row :gutter="16" class="stats-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ summary.totalBills ?? 0 }}</div>
            <div class="stat-label">单据总数</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ formatMoney(summary.totalAmount) }}</div>
            <div class="stat-label">总金额</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ summary.typeCount ?? 0 }}</div>
            <div class="stat-label">单据类型数</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value stat-value-sm">{{ summary.refreshTime ? formatDateTime(summary.refreshTime) : '-' }}</div>
            <div class="stat-label">最近刷新时间</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- Period Dropdown -->
      <div class="period-section">
        <span class="label">历史期间：</span>
        <el-select v-model="selectedPeriod" placeholder="选择历史期间查看" clearable style="width:200px" @change="onPeriodSelect">
          <el-option v-for="p in periods" :key="p" :label="periodLabel(p)" :value="p" />
        </el-select>
      </div>

      <!-- Summary Breakdown Table -->
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="breakdown" v-else border stripe style="margin-top:16px" @row-dblclick="openRowDetail" empty-text="该期间暂无预聚合数据，可点击「刷新预聚合」或切换期间">
        <el-table-column prop="category" label="单据类型" min-width="140" />
        <el-table-column prop="billCount" label="单据数" width="100" align="center" />
        <el-table-column label="金额" width="150" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="占比" width="120" align="center">
          <template #default="{ row }">{{ formatPercent(row.amount, summary.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="refreshText" label="最近刷新" width="150" align="center" />
      </el-table>
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end;"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showRowDetail, formatDateTime } from '@/utils/format'
import { billTypeLabel } from '@/constants/enums.js'
import { p2Api } from '../../api'
import { exportCsv } from '../../utils/csv'
import SkeletonList from '../../components/SkeletonList.vue'

const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)
const refreshing = ref(false)
const loading = ref(false)

const summary = reactive({
  totalBills: 0,
  totalAmount: 0,
  typeCount: 0,
  refreshTime: null,
  breakdown: [],
})

const periods = ref([])
const selectedPeriod = ref('')

const yearOptions = computed(() => {
  const base = now.getFullYear()
  return [base - 2, base - 1, base, base + 1]
})

const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const breakdown = computed(() => {
  const all = summary.breakdown || []
  const start = (pageNum.value - 1) * pageSize.value
  return all.slice(start, start + pageSize.value)
})

function periodKey(y, m) {
  return `${y}${String(m).padStart(2, '0')}`
}

function periodLabel(p) {
  const s = String(p || '')
  return s.length === 6 ? `${s.slice(0, 4)}-${s.slice(4)}` : s
}

function formatMoney(val) {
  if (val == null) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatPercent(amount, total) {
  if (!total || !amount) return '0%'
  return ((Number(amount) / Number(total)) * 100).toFixed(1) + '%'
}

function handleSizeChange(val) {
  pageSize.value = val
  pageNum.value = 1
}

function handleCurrentChange(val) {
  pageNum.value = val
}

async function loadSummary() {
  loading.value = true
  try {
    const res = await p2Api.summaryQuery({ period: periodKey(year.value, month.value), dimType: 'EXPENSE_TYPE' })
    const rows = Array.isArray(res) ? res : []
    let bills = 0
    let amount = 0
    let refresh = null
    for (const r of rows) {
      bills += Number(r.billCount) || 0
      amount += Number(r.totalAmount) || 0
      if (r.refreshTime && (!refresh || new Date(r.refreshTime) > new Date(refresh))) refresh = r.refreshTime
    }
    Object.assign(summary, {
      totalBills: bills,
      totalAmount: amount,
      typeCount: rows.length,
      refreshTime: refresh,
      breakdown: rows.map((r) => ({
        category: r.dimName || billTypeLabel(r.dimKey) || r.dimKey || '-',
        billCount: Number(r.billCount) || 0,
        amount: Number(r.totalAmount) || 0,
        refreshText: r.refreshTime ? formatDateTime(r.refreshTime) : '-',
      })),
    })
    total.value = rows.length
    pageNum.value = 1
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadPeriods() {
  try {
    const res = await p2Api.summaryPeriods()
    periods.value = Array.isArray(res) ? res : []
  } catch {
    // periods may be empty
  }
}

function onParamChange() {
  selectedPeriod.value = ''
  loadSummary()
}

function onPeriodSelect(val) {
  if (!val) return
  const s = String(val)
  year.value = Number(s.slice(0, 4))
  month.value = Number(s.slice(4, 6))
  loadSummary()
}

async function onRefresh() {
  await ElMessageBox.confirm(`确认刷新 ${year.value}年${month.value}月 的报表预聚合数据？`, '刷新预聚合', { type: 'warning' })
  refreshing.value = true
  try {
    await p2Api.summaryRefresh(year.value, month.value)
    ElMessage.success('预聚合刷新完成')
    loadSummary()
    loadPeriods()
  } catch (e) {
    ElMessage.error(e.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

// 41号审查 导出当前期间费用类别分解
function doExport() {
  // 导出列与页面列同源同序（原缺「占比」列，页面 5 列导出只有 4 列）。
  // 导出取 summary.breakdown 全集而非分页切片，与页面「导出全部」语义一致。
  exportCsv(summary.breakdown || [], [
    { prop: 'category', label: '单据类型' }, { prop: 'billCount', label: '单据数' },
    { prop: 'amount', label: '金额' },
    { value: (row) => formatPercent(row.amount, summary.totalAmount), label: '占比' },
    { prop: 'refreshText', label: '最近刷新' },
  ], `ReportSummary_${periodKey(year.value, month.value)}`)
}

onMounted(() => {
  loadSummary()
  loadPeriods()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  flex-wrap: wrap;
  align-items: center;
}
.label {
  font-size: 14px;
  color: var(--app-text-secondary, #606266);
  white-space: nowrap;
}
.stats-row {
  margin-bottom: 20px;
}
.stat-card {
  text-align: center;
  padding: 8px 0;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 6px;
}
.stat-value-sm {
  font-size: 15px;
  line-height: 32px;
}
.stat-label {
  font-size: 13px;
  color: #909399;
}
.period-section {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
