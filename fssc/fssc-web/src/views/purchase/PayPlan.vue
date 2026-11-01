<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>采购付款计划</b>
        <el-button type="primary" size="small" @click="openDialog()">+ 新建付款计划</el-button>
        <el-input v-model="q.keyword" placeholder="单号/供应商/用途" clearable style="width: 180px" @keyup.enter="load" />
        <el-button size="small" @click="load">查询</el-button>
      <el-button size="small" @click="exportCsv">导出</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 13px">
          合同/采购订单签订后提交付款计划 → 审批通过后流转付款申请
        </span>
      </div>
    </template>

    <el-empty v-if="!canList" description="当前账号无付款计划查看权限，请联系管理员授权 payplan:list" />
    <SkeletonList v-if="loading && canList" :rows="8" :cols="5" />
    <el-table v-else-if="canList" :data="rows" size="small" stripe max-height="520" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="planNo" label="单号" width="190"  sortable />
      <el-table-column prop="supplier" label="供应商" min-width="130" show-overflow-tooltip  sortable />
      <el-table-column prop="period" label="期间" width="80"  sortable />
      <el-table-column prop="amount" label="计划金额" width="120" align="right" sortable>
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="purpose" label="用途" min-width="150" show-overflow-tooltip  sortable />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === BillStatus.APPROVED ? 'success' : (row.status === BillStatus.REJECTED || row.status === BillStatus.VOID) ? 'danger' : 'primary'" size="small">
            {{ STATUS_LABEL[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === BillStatus.REJECTED" link size="small" type="warning" @click="resubmitPlan(row)">重报审批</el-button>
          <el-button v-else-if="canVoid(row)" link size="small" type="danger" @click="voidPlan(row)">作废</el-button>
          <span v-else style="color:#c0c4cc;font-size:12px">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 10px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="q.pageSize" :current-page="q.pageNum" @current-change="(n) => { q.pageNum = n; load() }" />

    <el-dialog v-model="dialogVisible" title="新建采购付款计划" width="460px">
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="100px">
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="form.supplierId" filterable style="width: 100%" placeholder="搜索选择供应商" @change="(id) => { const s = suppliers.find(x => x.id === id); if (s) form.supplier = s.supplierName }">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierCode + ' ' + s.supplierName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划期间" prop="period">
          <!-- 手输「2026-11」会被控件按自身格式解析后写进 value-format=YYYYMM 的模型，
               写入数据库期间就成了非预期值（会写出 2026-11 这类带横线值）。期间只允许从面板选。 -->
          <el-date-picker v-model="form.period" type="month" value-format="YYYYMM" format="YYYY-MM" :editable="false" unlink-panels style="width: 100%" placeholder="请选择年月" />
        </el-form-item>
        <el-form-item label="计划金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="10000" style="width: 100%" :controls="false" />
        </el-form-item>
        <el-form-item label="用途说明" prop="purpose">
          <el-select v-model="form.purpose" filterable allow-create default-first-option placeholder="请选择或输入用途说明" style="width: 100%"><el-option label="设备采购" value="设备采购" /><el-option label="材料采购" value="材料采购" /><el-option label="服务费" value="服务费" /><el-option label="工程款" value="工程款" /><el-option label="日常运营" value="日常运营" /><el-option label="研发投入" value="研发投入" /><el-option label="其他" value="其他" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交并发起审批</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>

const { rules: formRules } = useFormRules('PAY_PLAN')
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { payPlanApi, supplierApi, store } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useAutoFill } from '@/composables/useAutoFill'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { hasPerm } from '../../utils/perm'
// 快捷键
useKeyboard({
  'ctrl+s': () => { try { submit() } catch { /* noop */ } },
  'ctrl+r': () => { try { loadData() } catch { /* noop */ } },
})

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
// 工作台「我的单据」深链：?kw=计划单号 直接定位该行（台账页自带行内「重报审批」）
const route = useRoute()
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

const selRows = ref([])
const rows = ref([])
/** 列表权限预判（与后端 @RequiresPerm("payplan:list") 同码）——无权不打接口、渲染无权限态 */
const canList = hasPerm('payplan:list')
const total = ref(0)
const loading = ref(false)
const suppliers = ref([])
const q = reactive({ pageNum: 1, pageSize: 20, keyword: route.query.kw ? String(route.query.kw) : '' })

const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ planType: 'BASE', supplierId: null, supplier: '', period: '', amount: 0, purpose: '' })
const formRef = ref(null)
const { submit: guardSubmit } = useSubmitGuard(formRef)
const { autofill, saveFormHistory } = useAutoFill()

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }

async function load() {
  if (!canList) return // 无列表权限就不发请求，避免 4030 被吞成空表
  loading.value = true
  try {
    const r = await payPlanApi.page({ ...q })
    rows.value = r.list || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function openDialog() {
  Object.assign(form, { planType: 'BASE', supplierId: null, supplier: '', period: '', amount: 0, purpose: '' })
  dialogVisible.value = true
}

async function submit() {
  saveFormHistory('PAY_PLAN', form.value || form)

  await guardSubmit(async () => {
    submitting.value = true
    try {
      await payPlanApi.create({ ...form })
      ElMessage.success('已发起【采购付款计划】审批')
      dialogVisible.value = false
      load()
    } catch (e) { ElMessage.error(e.message) } finally { submitting.value = false }
  })
}

// 批次1：驳回计划重报——原单重走审批流，不再逼用户新建绕过
async function resubmitPlan(row) {
  try {
    await ElMessageBox.confirm(`确认将驳回计划 ${row.planNo} 重新提交审批？`, '重报审批', { type: 'warning' })
  } catch { return }
  try {
    await payPlanApi.resubmit(row.id)
    ElMessage.success('已重新提交审批')
    load()
  } catch (e) { ElMessage.error(e.message) }
}

// 在途计划的正当出口。按钮只对提单人本人出现（服务端同校验＝第二道），
// 已核销行由服务端拦下；作废会同步终止审批链，单据不再挂在审批池里。
function canVoid(row) {
  const uid = store.user?.userId ?? store.user?.id
  return row.status === BillStatus.APPROVING && !!uid && row.applicantId === uid
}

async function voidPlan(row) {
  let reason
  try {
    const r = await ElMessageBox.prompt('填写作废原因（留审计痕迹，同时写入审批链撤销事由）', '作废付款计划', {
      confirmButtonText: '确认作废', cancelButtonText: '取消',
      inputValidator: (v) => (v && v.trim() ? true : '请填写作废原因'),
    })
    reason = r.value.trim()
  } catch { return }
  try {
    await payPlanApi.voidPlan(row.id, reason)
    ElMessage.success('已作废，审批链同步终止')
    load()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(async () => {
  autofill(form, 'PAY_PLAN')
  form.planType = 'BASE' // 合并后统一走 PAY_PLAN_BASE 流程，防止 autofill 历史恢复出 PLATE
  try {
    const r = await supplierApi.page({ pageNum: 1, pageSize: 100 })
    suppliers.value = r.list || []
  } catch (e) { /* 忽略 */ }
  load()
})

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'planNo', label: '单号' }, { prop: 'supplier', label: '供应商' }, { prop: 'period', label: '期间' }, { prop: 'amount', label: '计划金额' }, { prop: 'purpose', label: '用途' }, { prop: 'createTime', label: '创建时间' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `PayPlan_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}


function handleSelectionChange(val) { selRows.value = val }

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
  a.download = `PayPlan_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>