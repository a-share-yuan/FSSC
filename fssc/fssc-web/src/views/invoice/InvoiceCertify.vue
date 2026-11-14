<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>{{ $t('invoice.invoiceCertify.Full') }}</b>
        <span style="color:#909399; font-size:12px">{{ $t('invoice.invoiceCertify.Certify') }}</span>
        <span style="display:flex; gap:8px; margin-left:auto; align-items:center">
          <el-form ref="certifyFormRef" :model="certifyForm" :rules="certifyRules" inline class="toolbar-form">
            <el-form-item :label="$t('invoice.invoiceCertify.Certification')" prop="certifyPeriod">
              <el-date-picker v-model="certifyPeriod" type="month" value-format="YYYYMM" :placeholder="$t('invoice.invoiceCertify.Select')" style="width:130px" clearable />
            </el-form-item>
          </el-form>
          <el-button v-permission="'inv:certify'" type="success" :disabled="!selRows.length" @click="doCertify">{{ $t('invoice.invoiceCertify.Batch') }}</el-button>
          <el-button v-permission="'inv:certify'" :disabled="!selRows.length" @click="doCancelCertify">{{ $t('invoice.invoiceCertify.Deselect') }}</el-button>
          <el-button v-permission="'inv:certify'" type="warning" :disabled="!selRows.length" @click="doTransferOut">{{ $t('invoice.invoiceCertify.Input') }}</el-button>
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
      <el-table-column prop="transferReason" label="转出原因" width="110" show-overflow-tooltip />
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

    <!-- 进项转出：原因必填（税务口径六项，可自定义），否则转出无依据可查 -->
    <el-dialog v-model="transferVisible" title="进项转出（不可抵扣）" width="460px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px"
        title="转出后该批发票不可再认证抵扣，可抵扣税额清零；已转出不可撤销。" />
      <el-form label-width="96px" :model="{ transferReason }" :rules="transferRules">
        <el-form-item label="本次转出">
          <span>{{ selRows.length }} 张<template v-if="transferTax > 0">，税额合计 ￥{{ fmt(transferTax) }}</template></span>
        </el-form-item>
        <el-form-item label="转出原因" required prop="transferReason">
          <el-select v-model="transferReason" filterable allow-create default-first-option
            placeholder="选择或输入原因（如：集体福利）" style="width: 100%">
            <el-option v-for="r in TRANSFER_REASONS" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="warning" @click="submitTransferOut">确认转出</el-button>
      </template>
    </el-dialog>
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
import { useFormRules, commonRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const rows = ref([])
const selRows = ref([])
/** 进项转出弹窗：原因必填、显示本次转出税额合计 */
const transferVisible = ref(false)
const transferReason = ref('')
const transferTax = ref(0)
const TRANSFER_REASONS = ['集体福利', '个人消费', '非正常损失', '简易计税', '免税项目', '其他']
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
// 专票（invoiceKind=SPECIAL 或 invoiceType=VAT）才可抵扣，与服务端 isVatDeductible 同一规则；
// 用于认证勾选时回显「本次可抵扣合计」（否则按 0 抵扣也无任何提示）
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
      // 服务端按「专票→deduct_amount=taxAmount」回填；此处把本次可抵扣合计回显，
      // 让用户当场看清「勾了 n 张、按多少税额抵扣」（否则按 0 抵扣也无提示）
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
    ElMessage.success(`已取消勾选 ${n} 张`)
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || String(e)) }
  load()
}
async function doTransferOut() {
  const sel = requireSel()
  // 税额合计：优先票面税额，退而取可抵扣额/价税分离差额；都取不到就只显示张数，
  // 不显示 0.00——那会让人误以为这批票没有可转出税额。
  const taxSum = sel.reduce((s, r) => {
    const raw = r.taxAmount ?? r.deductAmount ?? (Number(r.amount || 0) - Number(r.netAmount || 0))
    const v = Number(raw)
    return s + (Number.isFinite(v) ? v : 0)
  }, 0)
  transferTax.value = Number.isFinite(taxSum) ? taxSum : 0
  transferReason.value = ''
  transferVisible.value = true
}

/** 转出原因：税务口径固定六项，允许自定义；不填不许提交（转出必须说明依据） */
async function submitTransferOut() {
  if (!transferReason.value) { ElMessage.warning('请选择或填写转出原因'); return }
  try {
    const n = await invoiceApi.transferOut(requireSel(), transferReason.value)
    ElMessage.success(`已转出 ${n} 张（原因：${transferReason.value}），可抵扣税额已清零`)
    transferVisible.value = false
    load()
  } catch (e) { ElMessage.error(e.message || String(e)) }
}

async function loadSummary() {
  await guardFiling(async () => {
    summary.value = await invoiceApi.filingSummary(summaryPeriod.value)
  })
}

function openRowDetail(row) {
  return showRowDetail(row, { title: t('common.details') })
}

onMounted(load)

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning(t('useBill.main.There')); return }
  const cols = [{ prop: 'invoiceCode', label: t('invoice.main.Invoice3') }, { prop: 'invoiceNo', label: t('invoice.main.Invoice2') }, { prop: 'sellerName', label: t('image.ocrRecords.Seller') }, { prop: 'certifyPeriod', label: t('invoice.invoiceCertify.Period') }, { prop: 'certifyDate', label: t('invoice.invoiceCertify.Certification2') }]
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
  ElMessage.success(t('useBill.main.CSV'))
}


// required 原先只画红星、提交时不拦；补上真校验
// 必须走 commonRules：转出原因是 filterable+allow-create 下拉，写 { required, message, trigger }
// 会让 async-validator 改走 string 校验器，选中后仍判非法 ⇒ 永远提交不过
const transferRules = {
  transferReason: commonRules.required('请选择或填写转出原因'),
}
</script>

<style scoped>
.pager { margin-top: 12px; justify-content: flex-end; }
/* 工具条内联表单：去掉 el-form-item 默认的 18px 下边距，与同行按钮基线对齐 */
.toolbar-form :deep(.el-form-item) { margin-bottom: 0; }
</style>