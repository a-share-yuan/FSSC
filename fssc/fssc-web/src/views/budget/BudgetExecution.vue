<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="budget-execution-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">{{ $t('budget.budgetExecution.Budget') }}</span>
          <span style="color:#909399; font-size:12px">{{ $t('budget.budgetExecution.Query') }}</span>
        </div>
      </template>

      <el-form :inline="true" size="small" style="margin-bottom:12px">
        <el-form-item label="公司">
          <el-select v-model="filter.companyId" placeholder="全部公司" clearable filterable style="width:170px" @change="applyClientFilter">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间">
          <el-date-picker v-model="filter.period" type="month" value-format="YYYYMM" placeholder="全部期间" clearable style="width:140px" @change="loadList" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="filter.deptId" placeholder="全部部门" clearable filterable style="width:180px" @change="applyClientFilter">
            <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用项">
          <el-select v-model="filter.itemId" placeholder="全部费用项" clearable filterable style="width:200px" @change="applyClientFilter">
            <el-option v-for="s in expenseItems" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>

      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table v-else :data="pagedList" size="small" stripe border show-summary :summary-method="getSummary"
                empty-text="暂无数据" v-loading="loading" @row-click="openTrace" style="cursor:pointer">
        <el-table-column prop="companyName" label="公司" min-width="120" show-overflow-tooltip />
        <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
        <el-table-column prop="itemName" label="预算项" min-width="180" show-overflow-tooltip sortable />
        <el-table-column prop="period" label="期间" width="80" sortable />
        <el-table-column prop="total" label="预算(含调整)" width="130" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.total) }}</template>
        </el-table-column>
        <el-table-column prop="occupy" label="占用" width="120" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.occupy) }}</template>
        </el-table-column>
        <el-table-column prop="writeOff" label="核销" width="120" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.writeOff) }}</template>
        </el-table-column>
        <el-table-column prop="balance" label="余额" width="130" align="right" sortable>
          <template #default="{ row }">
            <span :style="{ color: Number(row.balance || 0) < 0 ? '#f56c6c' : '#67c23a', fontWeight: 600 }">{{ fmt(row.balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="流水" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openTrace(row)">查看</el-button>
          </template>
        </el-table-column>
        <!-- 数据为空时展示空态 -->
        <template #empty>
          <el-empty description="暂无预算执行明细，请调整查询条件后重试" />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="page.current" v-model:page-size="page.size"
        :total="filteredList.length" :page-sizes="[20, 50, 100]"
        layout="total, prev, pager, next, jumper"
        style="margin-top:12px;justify-content:flex-end"
      />
    </el-card>

    <!-- 占用/核销流水穿透抽屉 -->
    <el-drawer v-model="trace.visible" :title="trace.title" size="55%">
      <el-alert v-if="!canViewFlow" type="info" :closable="false"
        title="您没有查看占用流水明细的权限，请联系管理员开通。" style="margin-bottom:12px" />
      <template v-else>
        <div style="margin-bottom:10px;color:#909399;font-size:12px">
          该预算明细的全部占用/释放/核销流水，按时间倒序
        </div>
        <el-table :data="trace.rows" size="small" stripe border v-loading="trace.loading" empty-text="该明细暂无占用流水">
          <el-table-column prop="createTime" label="时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
          <el-table-column prop="operType" label="类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.operType === 'OCCUPY' ? 'warning' : row.operType === 'WRITE_OFF' ? 'success' : 'info'">
                {{ operLabel(row.operType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="130" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="bizType" label="业务类型" width="110" align="center">
            <template #default="{ row }">{{ bizLabel(row.bizType) }}</template>
          </el-table-column>
          <el-table-column prop="bizNo" label="单据号" min-width="160" show-overflow-tooltip />
          <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { budgetApi, mdApi, orgApi } from '../../api'
import { hasPerm } from '../../utils/perm'
import SkeletonList from '../../components/SkeletonList.vue'
import { formatDateTime } from '@/utils/format'

const list = ref([])
const loading = ref(false)
const filter = reactive({ period: '', deptId: null, companyId: null, itemId: null })
const page = reactive({ current: 1, size: 20 })

const depts = ref([])
const companies = ref([])
const expenseItems = ref([])

const canViewFlow = hasPerm('budget:occupy:list')

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// 名称兜底：后端未重启时 itemName 形如「预算项#96994 2026-COMPANY33-DEPT1302-ITEM335」，
// 前端从 dimKey 解析出公司/部门/费用项 id，用主数据映射补名（后端重启后直接用返回的名称字段）
function enrichRow(row) {
  const m = (row.dimKey || row.itemName || '').match(/COMPANY(\d+)-DEPT(\d+)-ITEM(\d+)/)
  if (m) {
    const [, cid, did, iid] = m
    if (!row.itemId) row.itemId = Number(iid)
    if (!row.companyId) row.companyId = Number(cid)
    if (!row.deptId) row.deptId = Number(did)
  }
  if (row.itemId && !row.itemName?.includes(' ') || /^(预算项#)/.test(row.itemName || '')) {
    const it = expenseItems.value.find((x) => x.id === row.itemId)
    if (it) row.itemName = (it.code ? it.code + ' ' : '') + it.name
  }
  if (!row.deptName && row.deptId) {
    row.deptName = depts.value.find((x) => x.id === row.deptId)?.name || ''
  }
  if (!row.companyName && row.companyId) {
    row.companyName = companies.value.find((x) => x.id === row.companyId)?.name || ''
  }
  return row
}

// 客户端过滤（执行接口为全量返回，量级百条级）
const filteredList = computed(() => {
  let arr = list.value
  if (filter.companyId) arr = arr.filter((r) => Number(r.companyId) === Number(filter.companyId))
  if (filter.deptId) arr = arr.filter((r) => Number(r.deptId) === Number(filter.deptId))
  if (filter.itemId) arr = arr.filter((r) => Number(r.itemId) === Number(filter.itemId))
  return arr
})
const pagedList = computed(() => {
  const start = (page.current - 1) * page.size
  return filteredList.value.slice(start, start + page.size)
})

function applyClientFilter() {
  page.current = 1
}

// 预算执行明细：GET /api/tenant/budget/execution?period=202609（后端并行实现）
async function loadList() {
  loading.value = true
  try {
    const params = {
      period: filter.period || undefined,
      deptId: filter.deptId || undefined,
    }
    const d = await budgetApi.execution(params)
    const arr = d?.list || d?.records || (Array.isArray(d) ? d : [])
    list.value = arr.map(enrichRow)
    page.current = 1
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function resetFilter() {
  filter.period = ''; filter.deptId = null; filter.companyId = null; filter.itemId = null
  page.current = 1
  loadList()
}

// 流水穿透
const trace = reactive({ visible: false, loading: false, title: '', rows: [] })

async function openTrace(row) {
  trace.title = `占用流水 — ${row.itemName || ''}（${row.period || ''}）`
  trace.visible = true
  trace.rows = []
  if (!canViewFlow) return
  trace.loading = true
  try {
    // budgetItemId 过滤参数随重启生效；生效前用返回行的 budgetItemId 客户端兜底过滤
    const d = await budgetApi.occupyPage({ pageNum: 1, pageSize: 200, budgetItemId: row.budgetItemId })
    const records = d?.list || d?.records || []
    trace.rows = records.filter((r) => !r.budgetItemId || Number(r.budgetItemId) === Number(row.budgetItemId))
  } catch (e) {
    ElMessage.error(e.message)
  } finally { trace.loading = false }
}

function operLabel(t) {
  return { OCCUPY: '占用', RELEASE: '释放', WRITE_OFF: '核销' }[t] || t
}
function bizLabel(t) {
  return {
    EXP_CLAIM: '费用报销', EXP_PAY: '对公付款', TR_PAY: '资金付款',
    PRE_APPLY: '事前申请', DEMO: '演示数据',
  }[t] || t
}

function getSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, i) => {
    if (i === 0) { sums[i] = '合计'; return }
    if (['total', 'occupy', 'writeOff', 'balance'].includes(col.property)) {
      sums[i] = fmt(data.reduce((s, r) => s + Number(r?.[col.property] || 0), 0))
    } else sums[i] = ''
  })
  return sums
}

onMounted(async () => {
  // 主数据下拉（公司/部门/费用项），失败不阻断列表加载
  orgApi.deptTree().then((res) => {
    const flat = []
    const walk = (arr) => { (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.deptName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
    walk(res)
    depts.value = flat
  }).catch(() => {})
  orgApi.companyTree().then((res) => {
    const flat = []
    const walk = (arr) => { (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
    walk(res)
    companies.value = flat
  }).catch(() => {})
  mdApi.expenseItems().then((d) => {
    const arr = Array.isArray(d) ? d : d?.list || []
    expenseItems.value = arr.map((x) => ({ id: x.id, name: x.itemName || x.name, code: x.itemCode || x.code }))
  }).catch(() => {})
  loadList()
})
</script>

<style scoped>
.budget-execution-page { min-height: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.header-title { font-size: 15px; font-weight: 600; }
</style>
