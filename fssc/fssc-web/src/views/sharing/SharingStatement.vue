<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="statement-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">{{ $t('sharing.statement.pageTitle') }}</span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-select v-model="importBankAccountId" filterable clearable size="small"
                       :placeholder="$t('sharing.statement.phImportAccount')" style="width:220px" @visible-change="(v) => v && ensureBankOptions()">
              <el-option v-for="a in bankOptions" :key="a.id"
                         :label="a.accountName + ' / ' + a.bankAccount" :value="a.id" />
            </el-select>
            <el-upload :show-file-list="false" :before-upload="handleImport" accept=".csv">
              <el-button type="primary" size="small">{{ $t('sharing.statement.btnImport') }}</el-button>
            </el-upload>
            <el-button size="small" @click="downloadTemplate">{{ $t('sharing.statement.btnTemplate') }}</el-button>
            <el-button size="small" @click="doExport">{{ $t('sharing.common.btnExportCsv') }}</el-button>
            <el-button size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :rules="statementSearchRules" size="small" label-width="100px" style="margin-bottom:12px">
        <el-form-item :label="$t('sharing.statement.labSerial')">
          <el-input v-model="q.bankSerial" :placeholder="$t('sharing.statement.phBankSerial')" clearable @keyup.enter="load" />
        </el-form-item>
        <el-form-item :label="$t('sharing.statement.colReconStatus')">
          <el-select v-model="q.reconStatus" clearable style="width:120px" @change="load">
            <el-option :label="$t('sharing.statement.optUnrecon')" value="UNRECON" />
            <el-option :label="$t('sharing.statement.optReconed')" value="RECONED" />
            <el-option :label="$t('sharing.statement.optDiff')" value="DIFF" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('sharing.statement.labTransDir')">
          <el-select v-model="q.transType" clearable style="width:100px" @change="load">
            <el-option :label="$t('sharing.statement.optDebit')" value="DEBIT" />
            <el-option :label="$t('sharing.statement.optCredit')" value="CREDIT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">{{ $t('common.search') }}</el-button>
        </el-form-item>
      </el-form>

      <SkeletonList v-if="loading" :rows="8" :cols="5" />
<el-table :data="rows" v-else size="small" stripe border max-height="560" @row-dblclick="openRowDetail"
        :row-class-name="rowClass" :empty-text="denied ? t('sharing.statement.emptyNoPerm') : t('common.empty')">
        <el-table-column prop="bankSerial" :label="$t('sharing.statement.phBankSerial')" width="150" show-overflow-tooltip sortable />
        <!-- 此列原先直挂 prop，后端 LocalDateTime 序列化成 `2026-12-24T10:50:31` 原样上屏。
             现走全站唯一日期规则 formatDateTime（YYYY-MM-DD HH:mm），与同族页 BankSlip.vue 一致。 -->
        <el-table-column prop="transDate" :label="$t('sharing.statement.colTransTime')" width="160" sortable :formatter="(row, col, val) => formatDateTime(val)" />
        <el-table-column :label="$t('field.direction')" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.transType === 'CREDIT' ? 'success' : 'warning'" size="small">
              {{ row.transType === 'CREDIT' ? t('sharing.statement.optCredit') : t('sharing.statement.optDebit') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('field.amount')" width="120" align="right" sortable :sort-by="(r) => Number(r.amount)">
          <template #default="{ row }">
            <span :style="{ color: row.transType === 'CREDIT' ? '#67c23a' : '#e6a23c', fontWeight: 600 }">
              {{ fmt(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balance" :label="$t('sharing.statement.colBalance')" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.balance) }}</template>
        </el-table-column>
        <el-table-column prop="oppositeName" :label="$t('sharing.statement.colOppName')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="oppositeAccount" :label="$t('sharing.statement.colOppAccount')" width="140" show-overflow-tooltip />
        <el-table-column prop="summary" :label="$t('field.summary')" min-width="120" show-overflow-tooltip />
        <el-table-column :label="$t('sharing.statement.colReconStatus')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="reconTag(row.reconStatus)" size="small">{{ reconText(row.reconStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('sharing.statement.colSlip')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="slipTag(row.slipStatus)" size="small">{{ slipText(row.slipStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page.current" v-model:page-size="page.size"
        :total="page.total" :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top:12px;justify-content:flex-end"
        @size-change="load" @current-change="load"
      />
    </el-card>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { showRowDetail, formatDateTime, formatMoney } from '@/utils/format'
import { opApi, masterApi } from '../../api'
import { exportCsv } from '../../utils/csv'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: t('field.detail') })
}

const loading = ref(false)
const rows = ref([])
const page = reactive({ current: 1, size: 20, total: 0 })
const q = reactive({ bankSerial: '', reconStatus: '', transType: '' })

// CSV 批量导入需先选定目标银行账户（company_id/bank_account_id 由后端按账户回填）
const importBankAccountId = ref(null)
// 4030 权限拒绝独立空态（原先被统一渲染成「暂无数据」，用户把「无权限」读成「没数据」；
// 权限码文案由 api.js 拦截器 toast，表体这里只把空态的语义纠正过来）
const denied = ref(false)
const bankOptions = ref([])
async function ensureBankOptions() {
  if (bankOptions.value.length) return
  try {
    const res = await masterApi.bank.page({ pageNum: 1, pageSize: 200 })
    bankOptions.value = res?.list || res?.records || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const statementSearchRules = reactive({})

async function load() {
  loading.value = true
  try {
    // 同族于 「前端环节」的一处裸错（本域未登记新条目，随手随 159 一并如实修正并回报）：
    // 原发的是 page/size，而后端 PageQuery 只认 pageNum/pageSize ⇒ 两个名字从未生效，
    // 每页条数下拉（10/20/50）与翻页点击都被打回默认第 1 页 10 条，列表看着"翻不动、切了没反应"。
    const p = { pageNum: page.current, pageSize: page.size }
    if (q.bankSerial) p.bankSerial = q.bankSerial
    if (q.reconStatus) p.reconStatus = q.reconStatus
    if (q.transType) p.transType = q.transType
    const res = await opApi.statementPage(p)
    rows.value = res?.list || res?.records || []
    page.total = res?.total || rows.value.length
    denied.value = false
  } catch (e) {
    // 4030（无 op:bank:list）置权限空态，不再落到「暂无数据」
    if (e?.code === 4030) { denied.value = true; return }
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function handleImport(file) {
  if (!importBankAccountId.value) {
    ElMessage.warning(t('sharing.statement.warnPickBank'))
    return false
  }
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('bankAccountId', importBankAccountId.value)
    const res = await opApi.statementImport(fd)
    const inserted = res?.inserted ?? 0
    const skipped = res?.skipped ?? 0
    const errs = res?.errors || []
    if (errs.length) {
      ElMessage.warning(t('sharing.statement.msgImportSummary', { v1: inserted, v2: skipped, v3: errs.length, v4: errs.slice(0, 3).join('；'), v5: errs.length > 3 ? t('sharing.statement.txtEtc') : '' }))
    } else {
      ElMessage.success(t('sharing.statement.msgImportOk', { v1: inserted, v2: skipped ? t('sharing.statement.txtSkipped') + skipped + ' 条重复' : '' }))
    }
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
  return false
}

function downloadTemplate() {
  const tpl = [
    t('sharing.statement.tplColumns'),
    t('sharing.statement.tplFormat'),
    'bankSerial,transDate,transType,amount,balance,oppositeName,oppositeAccount,summary,reconStatus,slipStatus',
    t('sharing.statement.tplSample'),
  ].join('\n')
  const blob = new Blob(['' + tpl], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = t('sharing.statement.fileNameBankTpl')
  a.click()
  URL.revokeObjectURL(url)
}

// 金额/余额统一走全站金额定义 formatMoney（千分位 + 2 位小数），
// 并修掉「余额列把 NULL 显示成 0.00」——op_bank_statement.balance 是可空列（导入时不填即 NULL），
// 原先本地 fmt 的 `Number(v || 0)` 会把"没有余额"渲染成"余额 0.00"，看着像一笔真余额。
const fmt = (v) => formatMoney(v, '—')

// 41号审查 导出当前查询结果
function doExport() {
  // 同一规则导出：原先把 transDate 原样写进 CSV，导出件里仍是 `2026-12-24T10:50:31`，
  // 与本文件自带的导入模板（"交易时间格式 yyyy-MM-dd HH:mm:ss"）对不上，导出→再导入是回环脏值。
  const data = (rows.value || []).map((r) => ({ ...r, transDate: formatDateTime(r.transDate) }))
  exportCsv(data, [
    { prop: 'bankSerial', label: t('sharing.statement.phBankSerial') }, { prop: 'transDate', label: t('sharing.statement.colTransTime') },
    { prop: 'transType', label: t('sharing.statement.colTransDirCode') }, { prop: 'amount', label: t('field.amount') }, { prop: 'balance', label: t('sharing.statement.colBalance') },
    { prop: 'oppositeName', label: t('sharing.statement.colOppName') }, { prop: 'oppositeAccount', label: t('sharing.statement.colOppAccount') }, { prop: 'summary', label: t('field.summary') },
    { prop: 'reconStatus', label: t('sharing.statement.colReconStatus') }, { prop: 'slipStatus', label: t('sharing.statement.colSlipStatus') },
  ], 'SharingStatement')
}
// 勾对状态有三个同义码在写：本页 markRecon 写 RECONED、银行对账模块写 MATCHED、历史种子行是 RECON
// ⇒ 名称表必须同时认三者，否则 RECON 行直显英文。
function reconText(s) {
  return { UNRECON: t('sharing.statement.optUnrecon'), RECON: t('sharing.statement.optReconed'), RECONED: t('sharing.statement.optReconed'), MATCHED: t('sharing.statement.optReconed'), DIFF: t('sharing.statement.optDiff') }[s] || s
}
function reconTag(s) {
  return { RECON: 'success', RECONED: 'success', MATCHED: 'success', DIFF: 'warning', UNRECON: 'danger' }[s] || 'info'
}
function slipText(s) {
  return { NONE: t('sharing.statement.optSlipNone'), DOWNLOADED: t('sharing.statement.optSlipDownloaded'), ARCHIVED: t('sharing.common.colArchived') }[s] || s
}
function slipTag(s) {
  return { ARCHIVED: 'success', DOWNLOADED: '', NONE: 'info' }[s] || 'info'
}
function rowClass({ row }) {
  if (row.reconStatus === 'RECONED' || row.reconStatus === 'RECON' || row.reconStatus === 'MATCHED') return 'row-green'
  if (row.reconStatus === 'DIFF') return 'row-yellow'
  if (row.reconStatus === 'UNRECON') return 'row-red'
  return ''
}

onMounted(() => load())
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; color: var(--app-text, #303133); }
:deep(.row-green) { background-color: rgba(103, 194, 58, 0.14) !important; }
:deep(.row-yellow) { background-color: rgba(230, 162, 60, 0.14) !important; }
:deep(.row-red) { background-color: rgba(245, 108, 108, 0.13) !important; }
</style>
