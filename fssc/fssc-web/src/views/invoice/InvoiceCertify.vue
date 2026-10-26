<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>进项发票全生命周期</b>
        <span style="color:#909399; font-size:12px">认证勾选(仅专票可抵扣) / 取消勾选 / 进项转出 / 票账比对申报汇总</span>
        <span style="display:flex; gap:8px; margin-left:auto; align-items:center">
          <el-form ref="certifyFormRef" :model="certifyForm" :rules="certifyRules" inline class="toolbar-form">
            <el-form-item label="认证所属期" prop="certifyPeriod">
              <el-date-picker v-model="certifyPeriod" type="month" value-format="YYYYMM" placeholder="选择认证所属期" style="width:130px" clearable />
            </el-form-item>
          </el-form>
          <el-button v-permission="'inv:certify'" type="success" :disabled="!selRows.length" @click="doCertify">批量认证勾选</el-button>
          <el-button v-permission="'inv:certify'" :disabled="!selRows.length" @click="doCancelCertify">取消勾选</el-button>
          <el-button v-permission="'inv:certify'" type="warning" :disabled="!selRows.length" @click="doTransferOut">进项转出</el-button>
        </span>
      </div>
    </template>

    <div style="display:flex; gap:8px; margin-bottom:10px; flex-wrap:wrap">
      <el-input v-model="kw" placeholder="发票号码/销方" clearable style="width:220px" @keyup.enter="load" />
      <el-select v-model="certifyStatus" placeholder="认证状态" clearable style="width:160px" @change="load">
        <el-option label="未勾选" value="NOT_CERTIFIED" />
        <el-option label="已勾选" value="CERTIFIED" />
        <el-option label="进项转出" value="REVERSED" />
      </el-select>
      <el-button v-permission="'inv:list'" type="primary" @click="load">查询</el-button>
      <el-button v-permission="'inv:list'" @click="reload">刷新</el-button>
      <el-button v-permission="'inv:export'" size="small" @click="exportCsv">导出</el-button>
    </div>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" size="small" border stripe v-else v-loading="loading" @selection-change="s => selRows = s" @row-dblclick="openRowDetail" empty-text="暂无数据">
      <el-table-column type="selection" width="45" :selectable="row => row.certifyStatus !== 'REVERSED'" />
      <el-table-column prop="invoiceCode" label="发票代码" width="110" show-overflow-tooltip  sortable />
      <el-table-column prop="invoiceNo" label="发票号码" width="150" show-overflow-tooltip  sortable />
      <el-table-column label="种类" width="90" align="center">
        <template #default="{ row }">{{ kindText(row.invoiceKind) }}</template>
      </el-table-column>
      <el-table-column prop="sellerName" label="销方" min-width="140" show-overflow-tooltip  sortable />
      <el-table-column label="价税合计" width="120" align="right">
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="税额" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.taxAmount) }}</template>
      </el-table-column>
      <el-table-column label="可抵扣税额" width="120" align="right">
        <template #default="{ row }"><b>{{ fmt(row.deductAmount) }}</b></template>
      </el-table-column>
      <el-table-column label="认证状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="certifyTag(row.certifyStatus)" size="small">{{ certifyText(row.certifyStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="certifyPeriod" label="所属期" width="90" align="center"  sortable />
      <el-table-column prop="certifyDate" label="认证时间" width="150"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="pageSize" :current-page="pageNum" @current-change="p => { pageNum = p; load() }" />

    <!-- 票账比对 · 申报汇总 -->
    <el-divider content-position="left">票账比对 · 增值税申报汇总（销项 − 认证进项 = 应纳增值税）</el-divider>
    <div style="display:flex; gap:8px; margin-bottom:10px; align-items:center">
      <el-form ref="filingFormRef" :model="filingForm" :rules="filingRules" inline class="toolbar-form">
        <el-form-item label="申报期间" prop="summaryPeriod">
          <el-date-picker v-model="summaryPeriod" type="month" value-format="YYYYMM" placeholder="选择申报期间" style="width:140px" clearable />
        </el-form-item>
      </el-form>
      <el-button v-permission="'inv:list'" type="info" @click="loadSummary">生成申报汇总</el-button>
    </div>
    <el-descriptions v-if="summary" :column="3" border size="small">
      <el-descriptions-item label="申报期间">{{ summary.period }}</el-descriptions-item>
      <el-descriptions-item label="销项税额(计提)">{{ fmt(summary.outputVat) }}</el-descriptions-item>
      <el-descriptions-item label="认证进项税额(抵扣)">{{ fmt(summary.inputVat) }}</el-descriptions-item>
      <el-descriptions-item label="应纳增值税">{{ fmt(summary.netVatPayable) }}</el-descriptions-item>
      <el-descriptions-item label="本期认证张数">{{ summary.certifiedCount }}</el-descriptions-item>
      <el-descriptions-item label="认证发票税额合计(票账比对)">{{ fmt(summary.certifiedTax) }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { onMounted, computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { invoiceApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { useFormRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const rows = ref([])
const selRows = ref([])
const loading = ref(false)
const kw = ref('')
const certifyStatus = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const certifyPeriod = ref('')
const summaryPeriod = ref('')
const summary = ref(null)

const YM = () => {
  const d = new Date()
  return '' + d.getFullYear() + String(d.getMonth() + 1).padStart(2, '0')
}
if (!certifyPeriod.value) certifyPeriod.value = YM()
if (!summaryPeriod.value) summaryPeriod.value = YM()

// 校验规则唯一规则＝中央登记表（useFormRules）；工具条日期选择器同样纳入必填校验
const certifyFormRef = ref(null)
const filingFormRef = ref(null)
const { rules: certifyRules } = useFormRules('INVOICE_CERTIFY')
const { rules: filingRules } = useFormRules('INVOICE_FILING')
const { submit: guardCertify } = useSubmitGuard(certifyFormRef)
const { submit: guardFiling } = useSubmitGuard(filingFormRef)
const certifyForm = computed(() => ({ certifyPeriod: certifyPeriod.value }))
const filingForm = computed(() => ({ summaryPeriod: summaryPeriod.value }))

const fmt = (v) => (v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
const kindText = (k) => ({ SPECIAL: '专票', NORMAL: '普票', ELECTRONIC: '电子', VATN: '普票', VAT: '专票' }[k] || k || '—')
const certifyText = (s) => ({ NOT_CERTIFIED: '未勾选', CERTIFIED: '已勾选', REVERSED: '进项转出' }[s] || s || '—')
const certifyTag = (s) => ({ NOT_CERTIFIED: 'info', CERTIFIED: 'success', REVERSED: 'danger' }[s] || 'info')
// R5-D-120：专票（invoiceKind=SPECIAL 或 invoiceType=VAT）才可抵扣，与服务端 isVatDeductible 同一规则；
// 用于认证勾选时回显「本次可抵扣合计」（此前按 0 抵扣也无任何提示）
const isDeductible = (r) => String(r.invoiceKind || '').toUpperCase() === 'SPECIAL'
  || String(r.invoiceType || '').toUpperCase() === 'VAT'

async function load() {
  loading.value = true
  try {
    const d = await invoiceApi.page({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: kw.value, certifyStatus: certifyStatus.value })
    rows.value = d.list || []
    total.value = d.total || 0
  } finally { loading.value = false }
}
const reload = () => { pageNum.value = 1; load() }

function requireSel() {
  if (!selRows.value.length) throw new Error('请先勾选发票')
  return selRows.value.map(r => r.id)
}

async function doCertify() {
  await guardCertify(async () => {
    try {
      await ElMessageBox.confirm('确认对所选 ' + selRows.value.length + ' 张发票进行进项认证勾选？', '确认', { type: 'warning' })
      const n = await invoiceApi.certify(requireSel(), certifyPeriod.value)
      // R5-D-120：服务端按「专票→deduct_amount=taxAmount」回填；此处把本次可抵扣合计回显，
      // 让用户当场看清「勾了 n 张、按多少税额抵扣」（此前按 0 抵扣也无提示）
      const deductSum = selRows.value.reduce((sum, r) => sum + (isDeductible(r) ? Number(r.taxAmount || 0) : 0), 0)
      ElMessage.success('认证勾选 ' + n + ' 张，本次可抵扣合计 ¥' + fmt(deductSum))
    } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || String(e)) }
    load()
  })
}
async function doCancelCertify() {
  try {
    await ElMessageBox.confirm('确认取消所选发票的勾选认证？', '确认', { type: 'warning' })
    const n = await invoiceApi.cancelCertify(requireSel())
    ElMessage.success('取消勾选 ' + n + ' 张')
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || String(e)) }
  load()
}
async function doTransferOut() {
  try {
    await ElMessageBox.confirm('进项转出后该批发票不可再认证抵扣，确认转出？', '确认', { type: 'warning' })
    const n = await invoiceApi.transferOut(requireSel())
    ElMessage.success('进项转出 ' + n + ' 张')
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || String(e)) }
  load()
}

async function loadSummary() {
  await guardFiling(async () => {
    summary.value = await invoiceApi.filingSummary(summaryPeriod.value)
  })
}

function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

onMounted(load)

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'invoiceCode', label: '发票代码' }, { prop: 'invoiceNo', label: '发票号码' }, { prop: 'sellerName', label: '销方' }, { prop: 'certifyPeriod', label: '所属期' }, { prop: 'certifyDate', label: '认证时间' }]
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
  a.download = `InvoiceCertify_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}

</script>

<style scoped>
.pager { margin-top: 12px; justify-content: flex-end; }
/* 工具条内联表单：去掉 el-form-item 默认的 18px 下边距，与同行按钮基线对齐 */
.toolbar-form :deep(.el-form-item) { margin-bottom: 0; }
</style>