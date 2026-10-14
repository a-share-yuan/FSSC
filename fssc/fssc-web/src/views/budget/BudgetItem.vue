<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="budget-item-page">
    <!-- R5-D-263：页面层权限门控（/item/page 端点按设计保持开放供引用预览，管理页在此收口） -->
    <el-card v-if="canList" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">预算明细</span>
          <div style="display:flex;gap:8px">
            <el-button size="small" @click="handleExport" :loading="exporting">导出 Excel</el-button>
            <el-button size="small" type="success" @click="importDlg.visible = true">导入 Excel</el-button>
            <el-button type="primary" size="small" @click="openDialog(null)">录入预算</el-button>
          </div>
        </div>
      </template>
      <!-- 筛选 -->
      <el-form :inline="true" size="small" style="margin-bottom:12px">
        <el-form-item label="预算方案">
          <el-select v-model="filter.schemeId" placeholder="选择方案" clearable filterable style="width:200px" @change="loadList">
            <el-option v-for="s in schemes" :key="s.id" :label="s.schemeName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用项">
          <el-select v-model="filter.itemId" placeholder="选择费用项" clearable filterable style="width:180px" @change="loadList">
            <el-option v-for="i in expenseItems" :key="i.id" :label="i.itemName" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
      <!-- 预算执行月度趋势图 -->
      <el-card v-if="list.length" shadow="never" style="margin-bottom:12px;border:none">
        <template #header><span style="font-size:14px;font-weight:600">预算执行月度趋势</span></template>
        <div ref="trendChartRef" style="width:100%;height:280px" />
      </el-card>
      <!-- 列表 -->
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="list" v-else size="small" stripe border show-summary :summary-method="getSummary" empty-text="暂无数据">
        <el-table-column prop="budgetYear" label="年度" width="70" />
        <el-table-column prop="budgetPeriod" label="期间" width="70">
          <template #default="{ row }">{{ row.budgetPeriod === 0 ? '全年' : row.budgetPeriod + '月' }}</template>
        </el-table-column>
        <el-table-column prop="dimKey" label="维度键" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ncAccount" label="NC科目" width="100" />
        <el-table-column prop="ifsAccount" label="IFS科目" width="100" />
        <el-table-column prop="totalAmount" label="预算总额" width="110" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="adjustAmount" label="调整额" width="100" align="right" sortable>
          <template #default="{ row }">
            <span :class="row.adjustAmount > 0 ? 'text-green' : row.adjustAmount < 0 ? 'text-red' : ''">{{ fmt(row.adjustAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可用余额" width="120" align="right">
          <template #default="{ row }">
            <strong :class="availBalance(row) < 0 ? 'text-red' : 'text-green'">{{ fmt(availBalance(row)) }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="occupyAmount" label="已占用" width="100" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.occupyAmount) }}</template>
        </el-table-column>
        <el-table-column prop="usedAmount" label="已核销" width="100" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.usedAmount) }}</template>
        </el-table-column>
        <el-table-column label="执行率" width="90" align="center">
          <template #default="{ row }">
            <!-- R5-D-264：文本显真实值（超 100% 保留小数并转红）；进度条仅做可视化封顶 -->
            <el-progress :percentage="execRateBar(row)" :color="execColor(execRate(row))" :stroke-width="8" :show-text="false" />
            <small :class="execRate(row) > 100 ? 'text-red' : ''">{{ execRateText(row) }}</small>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">          <InlineActions :visible="true" @view="openDialog(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="false" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
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
    <!-- R5-D-263：无 budget:item:list 权限给显式空态（不再渲染真实预算行） -->
    <el-card v-else shadow="never">
      <el-result icon="warning" title="暂无访问权限"
        sub-title="当前账号缺少「预算明细」查看权限（budget:item:list）；如需查阅请向管理员申请授权。" />
    </el-card>

    <!-- 导入 Excel 弹窗 -->
    <el-dialog v-model="importDlg.visible" title="导入预算明细" width="560px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
        title="模板列：预算年度/期间(全年或1-12)/公司编码或名称/部门编码或名称/费用项编码或名称/预算总额/币种/NC科目/IFS科目/备注。可直接导出后修改再导入；按「方案+维度键」幂等更新。" />
      <el-form ref="importFormRef" :model="importDlg" :rules="importRules" label-width="90px">
        <el-form-item label="预算方案" prop="schemeId">
          <el-select v-model="importDlg.schemeId" placeholder="选择导入到的方案" filterable style="width:100%">
            <el-option v-for="s in schemes" :key="s.id" :label="s.schemeName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Excel 文件" prop="file">
          <input type="file" accept=".xlsx"
            @change="(e) => { importDlg.file = e.target.files[0] || null; importFormRef.value?.clearValidate('file') }" />
        </el-form-item>
      </el-form>
      <el-alert v-if="importDlg.result" :type="importDlg.result.errors?.length ? 'warning' : 'success'" :closable="false"
        :title="`导入完成：新增 ${importDlg.result.inserted} 条，更新 ${importDlg.result.updated} 条，失败 ${importDlg.result.errors?.length || 0} 行`" style="margin-bottom:8px" />
      <div v-if="importDlg.result?.errors?.length" style="max-height:180px;overflow:auto;font-size:12px;color:#e6a23c;line-height:1.8">
        <div v-for="(msg, i) in importDlg.result.errors" :key="i">{{ msg }}</div>
      </div>
      <template #footer>
        <el-button @click="importDlg.visible = false">关闭</el-button>
        <el-button type="primary" :loading="importDlg.saving" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 录入/编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.editing ? '编辑预算明细' : '录入预算明细'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="dialog.form" :rules="itemFormRules" label-width="120px" size="default">
        <el-form-item label="预算方案" prop="schemeId">
          <el-select v-model="dialog.form.schemeId" placeholder="选择方案" filterable style="width:100%">
            <el-option v-for="s in schemes" :key="s.id" :label="s.schemeName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预算年度" prop="budgetYear">
          <el-input-number v-model="dialog.form.budgetYear" :min="2020" :max="2099" :controls="false" style="width:100%" />
        </el-form-item>
        <!-- 预算期间为单选下拉且恒有默认值(全年)，不会出现未填态，故不加红星 -->
        <el-form-item label="预算期间">
          <el-select v-model="dialog.form.budgetPeriod" style="width:100%">
            <el-option label="全年" :value="0" />
            <el-option v-for="m in 12" :key="m" :label="m + '月'" :value="m" />
          </el-select>
        </el-form-item>
        <!-- 公司留空时后端按方案所属公司兜底，属可选项 -->
        <el-form-item label="公司">
          <el-select v-model="dialog.form.companyId" placeholder="留空取方案所属公司" filterable style="width:100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.companyName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="dialog.form.deptId" placeholder="选择部门" filterable clearable style="width:100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用项" prop="itemId">
          <el-select v-model="dialog.form.itemId" placeholder="选择费用项" filterable style="width:100%">
            <el-option v-for="i in expenseItems" :key="i.id" :label="i.itemName" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="dialog.form.projectId" placeholder="选择项目" filterable clearable style="width:100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预算总额" prop="totalAmount">
          <el-input-number v-model="dialog.form.totalAmount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="NC科目码"><el-input v-model="dialog.form.ncAccount" placeholder="如 660101" /></el-form-item>
        <el-form-item label="IFS科目码"><el-input v-model="dialog.form.ifsAccount" placeholder="如 4001" /></el-form-item>
        <el-form-item label="币种">
          <el-select v-model="dialog.form.currencyCode" style="width:100%">
            <el-option label="CNY 人民币" value="CNY" />
            <el-option label="USD 美元" value="USD" />
            <el-option label="EUR 欧元" value="EUR" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="dialog.form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>

import { onMounted, reactive, ref, nextTick, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { budgetApi, mdApi, orgApi, wfApi } from '../../api'
import { useRoute } from 'vue-router'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useAutoFill } from '@/composables/useAutoFill'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import InlineActions from '@/components/InlineActions.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { useApprovalScope } from '../../composables/useApprovalScope'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'
import { hasPerm } from '../../utils/perm'

// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => loadList(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})
const { autofill, saveFormHistory } = useAutoFill()

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

echarts.use([BarChart, LineChart, TooltipComponent, GridComponent, LegendComponent, CanvasRenderer])

const route = useRoute()
const list = ref([])
const loading = ref(false)
const schemes = ref([])
const expenseItems = ref([])
const companies = ref([])
const depts = ref([])
const projects = ref([])
const filter = reactive({ schemeId: null, itemId: null })
const page = reactive({ current: 1, size: 10, total: 0 })
// R5-D-263：读权限门控——无 budget:item:list 时不发起列表请求、不渲染数据区，给显式空态
const canList = hasPerm('budget:item:list')

// ---------------- 预算执行月度趋势图 ----------------
const trendChartRef = ref()
let trendChart = null

function renderTrend() {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)

  // 按月聚合
  const months = Array.from({ length: 12 }, (_, i) => i + 1)
  const budgetData = Array(12).fill(0)   // 预算（含调整）
  const occupyData = Array(12).fill(0)  // 已占用
  const usedData = Array(12).fill(0)     // 已核销
  list.value.forEach((r) => {
    const m = Number(r.budgetPeriod || 0)
    if (m >= 1 && m <= 12) {
      budgetData[m - 1] += Number(r.totalAmount || 0) + Number(r.adjustAmount || 0)
      occupyData[m - 1] += Number(r.occupyAmount || 0)
      usedData[m - 1] += Number(r.usedAmount || 0)
    }
  })
  // 全年(period=0)的数据均摊到 12 月
  list.value.filter((r) => Number(r.budgetPeriod || 0) === 0).forEach((r) => {
    const total = Number(r.totalAmount || 0) + Number(r.adjustAmount || 0)
    const avg = total / 12
    const occAvg = Number(r.occupyAmount || 0) / 12
    const useAvg = Number(r.usedAmount || 0) / 12
    for (let i = 0; i < 12; i++) {
      budgetData[i] += avg
      occupyData[i] += occAvg
      usedData[i] += useAvg
    }
  })

  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const m = params[0].axisValue
        let html = `${m}月<br/>`
        params.forEach((p) => {
          html += `${p.marker} ${p.seriesName}: ${fmt(p.value)}<br/>`
        })
        const rate = budgetData[months.indexOf(m)] > 0
          ? Math.round((usedData[months.indexOf(m)] / budgetData[months.indexOf(m)]) * 100)
          : 0
        html += `执行率: ${rate}%`
        return html
      },
    },
    legend: { data: ['预算', '已占用', '已核销'], bottom: 0 },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: months.map((m) => m + '月'), axisLabel: { interval: 0 } },
    yAxis: { type: 'value', name: '金额', axisLabel: { formatter: (v) => v >= 10000 ? (v / 10000).toFixed(0) + '万' : v } },
    series: [
      {
        name: '预算', type: 'bar', barWidth: '35%',
        data: budgetData.map((v) => Math.round(v * 100) / 100),
        itemStyle: { color: '#409eff', borderRadius: [3, 3, 0, 0] },
      },
      {
        name: '已占用', type: 'line', smooth: true,
        data: occupyData.map((v) => Math.round(v * 100) / 100),
        itemStyle: { color: '#e6a23c' }, lineStyle: { width: 2 }, symbol: 'circle', symbolSize: 6,
      },
      {
        name: '已核销', type: 'line', smooth: true,
        data: usedData.map((v) => Math.round(v * 100) / 100),
        itemStyle: { color: '#67c23a' }, lineStyle: { width: 2 }, symbol: 'circle', symbolSize: 6,
        areaStyle: { color: 'rgba(103,194,58,0.12)' },
      },
    ],
  })
}

const dialog = reactive({
  visible: false, editing: false, saving: false,
  form: {},
})

// ---------------- Excel 导入/导出（2026-09-17） ----------------
const exporting = ref(false)
const importDlg = reactive({ visible: false, saving: false, schemeId: null, file: null, result: null })

async function handleExport() {
  exporting.value = true
  try {
    const blob = await budgetApi.itemExport({ schemeId: filter.schemeId || undefined, itemId: filter.itemId || undefined })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `预算明细_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.click(); URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } catch (e) { ElMessage.error(e.message) } finally { exporting.value = false }
}

async function handleImport() {
  if (!(await validateForms([importFormRef]))) return
  importDlg.saving = true
  try {
    const r = await budgetApi.itemImport(importDlg.schemeId, importDlg.file)
    importDlg.result = r || { inserted: 0, updated: 0, errors: ['返回为空'] }
    await loadList()
  } catch (e) { ElMessage.error(e.message) } finally { importDlg.saving = false }
}

// 导入弹窗必填（与后端 BudgetItemExcelService.importItems 同口径）
const importFormRef = ref(null)
const importRules = reactive({
  schemeId: commonRules.select('请选择导入到的预算方案'),
  file: commonRules.required('请选择要导入的 Excel 文件'),
})

// 表单校验规则（与后端 BudgetService.assertItemRequired 同口径）
const itemFormRules = reactive({
  schemeId: commonRules.select('请选择预算方案'),
  budgetYear: commonRules.required('请输入预算年度'),
  itemId: commonRules.select('请选择费用项'),
  // 允许 0（先建维度占位、后续走调整单增拨），只拦"没填"和"负数"
  totalAmount: [{ required: true, type: 'number', min: 0, message: '请输入预算总额（不能为负）' }],
})
const formRef = ref(null)

async function loadList() {
  if (!canList) return // R5-D-263：无查看权限不发列表请求
  loading.value = true
  try {
    const r = await budgetApi.itemPage({
      pageNum: page.current, pageSize: page.size,
      schemeId: filter.schemeId, itemId: filter.itemId,
    })
    list.value = r?.list || r?.records || []
    page.total = r?.total || 0
    await nextTick()
    renderTrend()
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadSchemes() {
  try {
    const r = await budgetApi.schemePage({ pageNum: 1, pageSize: 200 })
    schemes.value = r?.list || r?.records || []
  } catch {}
}

function resetFilter() {
  filter.schemeId = null; filter.itemId = null
  page.current = 1
  loadList()
}

function openDialog(row) {
  dialog.editing = !!row
  dialog.form = row ? { ...row } : {
    schemeId: filter.schemeId, budgetYear: new Date().getFullYear(),
    budgetPeriod: 0, companyId: null, deptId: null, itemId: null,
    projectId: null, totalAmount: 0, currencyCode: 'CNY',
    ncAccount: '', ifsAccount: '', remark: '',
  }
  dialog.visible = true
}

async function save() {
  if (!(await validateForms([formRef]))) return
  saveFormHistory('BUDGET_ITEM', dialog.form)

  dialog.saving = true
  try {
    await budgetApi.itemUpsert({ ...dialog.form })
    ElMessage.success('保存成功')
    dialog.visible = false
    await loadList()
  } catch (e) { ElMessage.error(e.message) } finally { dialog.saving = false }
}

function availBalance(row) {
  return Number(row.totalAmount || 0) + Number(row.adjustAmount || 0) - Number(row.occupyAmount || 0) - Number(row.usedAmount || 0)
}
// R5-D-264：执行率读后端单点字段（execRateOf：(占用+核销)/(总额+调整)×100，scale 1，不封顶）
function execRate(row) {
  const v = row.execRate
  return v === null || v === undefined || v === '' ? null : Number(v)
}
// 文本显真实值、保留 1 位小数；无基数（后端 null）显示「—」
function execRateText(row) {
  const v = execRate(row)
  return v === null || Number.isNaN(v) ? '—' : v.toFixed(1) + '%'
}
// el-progress percentage 仅接受 0-100，仅进度条做可视化封顶，不进文本
function execRateBar(row) {
  const v = execRate(row)
  if (v === null || Number.isNaN(v)) return 0
  return Math.min(100, Math.max(0, v))
}
function execColor(v) {
  if (v === null || v === undefined || Number.isNaN(v)) return '#909399'
  if (v > 100) return '#f56c6c'
  if (v >= 90) return '#e6a23c'
  return '#67c23a'
}
function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function getSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, i) => {
    if (i === 0) { sums[i] = '合计'; return }
    const prop = col.property
    if (['totalAmount', 'adjustAmount', 'occupyAmount', 'usedAmount'].includes(prop)) {
      sums[i] = fmt(data.reduce((s, r) => s + Number(r[prop] || 0), 0))
    } else if (i === columns.length - 2) {
      // 可用余额列
      sums[i] = fmt(data.reduce((s, r) => s + availBalance(r), 0))
    } else sums[i] = ''
  })
  return sums
}

onMounted(async () => {
  if (!canList) return // R5-D-263：无查看权限不预载主数据与列表
  // 从URL参数获取schemeId
  if (route.query.schemeId) filter.schemeId = Number(route.query.schemeId)
  await Promise.all([
    loadSchemes(),
    mdApi.expenseItems().then(r => { expenseItems.value = r || [] }).catch(() => {}),
    orgApi.companyTree().then(r => { companies.value = flattenTree(r) }).catch(() => {}),
    orgApi.deptTree().then(r => { depts.value = flattenTree(r) }).catch(() => {}),
    mdApi.projects({ pageNum: 1, pageSize: 200 }).then(r => { projects.value = r?.list || r?.records || [] }).catch(() => {}),
  ])
  await loadList()
})

// 窗口缩放时自适应
onMounted(() => {
  autofill(dialog.form, 'BUDGET_ITEM')

  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  if (trendChart) { trendChart.dispose(); trendChart = null }
})
function onResize() { if (trendChart) trendChart.resize() }

function flattenTree(nodes) {
  const result = []
  const walk = (arr) => {
    if (!Array.isArray(arr)) return
    arr.forEach(n => {
      result.push(n)
      if (n.children) walk(n.children)
    })
  }
  walk(nodes)
  return result
}
// R5-01: InlineActions approve/reject handlers
// 通过走 budgetApi.adjustApprove（预算调剂确认，后端无意见字段，故弹窗只给上下文不假装能留痕）；
// 驳回走 wfApi.approve，必须写明原因。
const approvalRef = ref(null)

async function handleApprove(row) {
  if (!approvalRef.value) { ElMessage.error('审批弹窗未就绪，请刷新页面后重试'); return }
  const res = await approvalRef.value.open({
    row, allowReject: false, allowComment: false,
    notice: '预算调剂为业务确认动作，后端接口未提供审批意见字段，本次不记录意见。',
  })
  if (!res) return
  try {
    await budgetApi.adjustApprove(row.id)
    ElMessage.success('已通过')
    loadList()
  } catch (e) { ElMessage.error(e.message) }
}

async function handleReject(row) {
  if (!approvalRef.value) { ElMessage.error('审批弹窗未就绪，请刷新页面后重试'); return }
  const taskId = taskIdOf(row)
  if (!taskId) { ElMessage.warning('该单据不在您的待办中（可能已被他人办理），请刷新列表后重试'); return }
  const res = await approvalRef.value.open({ row, decision: 'reject', allowApprove: false })
  if (!res) return
  try {
    await wfApi.approve({ taskId, approved: false, comment: res.comment })
    ElMessage.success('已驳回')
    loadList()
  } catch (e) { ElMessage.error(e.message) }
}
</script>

<style scoped>
.budget-item-page { min-height: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; }
.text-green { color: #67c23a; }
.text-red { color: #f56c6c; }
</style>
