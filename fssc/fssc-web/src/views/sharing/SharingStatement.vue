<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="statement-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">银行流水</span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-select v-model="importBankAccountId" filterable clearable size="small"
                       placeholder="导入前选择银行账户" style="width:220px" @visible-change="(v) => v && ensureBankOptions()">
              <el-option v-for="a in bankOptions" :key="a.id"
                         :label="a.accountName + ' / ' + a.bankAccount" :value="a.id" />
            </el-select>
            <el-upload :show-file-list="false" :before-upload="handleImport" accept=".csv">
              <el-button type="primary" size="small">导入流水</el-button>
            </el-upload>
            <el-button size="small" @click="downloadTemplate">模板</el-button>
            <el-button size="small" @click="doExport">导出 CSV</el-button>
            <el-button size="small" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :rules="statementSearchRules" size="small" label-width="100px" style="margin-bottom:12px">
        <el-form-item label="流水号">
          <el-input v-model="q.bankSerial" placeholder="银行流水号" clearable @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="对账状态">
          <el-select v-model="q.reconStatus" clearable style="width:120px" @change="load">
            <el-option label="未对账" value="UNRECON" />
            <el-option label="已对账" value="RECONED" />
            <el-option label="差额" value="DIFF" />
          </el-select>
        </el-form-item>
        <el-form-item label="交易方向">
          <el-select v-model="q.transType" clearable style="width:100px" @change="load">
            <el-option label="借方" value="DEBIT" />
            <el-option label="贷方" value="CREDIT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>

      <SkeletonList v-if="loading" :rows="8" :cols="5" />
<el-table :data="rows" v-else size="small" stripe border max-height="560" @row-dblclick="openRowDetail"
        :row-class-name="rowClass" :empty-text="denied ? '无权限查看：当前账号缺少银行流水读权限（op:bank:list），请联系管理员开通' : '暂无数据'">
        <el-table-column prop="bankSerial" label="银行流水号" width="150" show-overflow-tooltip sortable />
        <!-- D-DEC-159：此列原先直挂 prop，后端 LocalDateTime 序列化成 `2026-12-24T10:50:31` 原样上屏。
             现走全站唯一日期口径 formatDateTime（YYYY-MM-DD HH:mm），与同族页 BankSlip.vue 一致。 -->
        <el-table-column prop="transDate" label="交易时间" width="160" sortable :formatter="(row, col, val) => formatDateTime(val)" />
        <el-table-column label="方向" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.transType === 'CREDIT' ? 'success' : 'warning'" size="small">
              {{ row.transType === 'CREDIT' ? '贷方' : '借方' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right" sortable :sort-by="(r) => Number(r.amount)">
          <template #default="{ row }">
            <span :style="{ color: row.transType === 'CREDIT' ? '#67c23a' : '#e6a23c', fontWeight: 600 }">
              {{ fmt(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balance" label="余额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.balance) }}</template>
        </el-table-column>
        <el-table-column prop="oppositeName" label="对方户名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="oppositeAccount" label="对方账号" width="140" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="120" show-overflow-tooltip />
        <el-table-column label="对账状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="reconTag(row.reconStatus)" size="small">{{ reconText(row.reconStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="回单" width="80" align="center">
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

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

const loading = ref(false)
const rows = ref([])
const page = reactive({ current: 1, size: 20, total: 0 })
const q = reactive({ bankSerial: '', reconStatus: '', transType: '' })

// D-BS-1：CSV 批量导入需先选定目标银行账户（company_id/bank_account_id 由后端按账户回填）
const importBankAccountId = ref(null)
// R5-D-174：4030 权限拒绝独立空态（改前被统一渲染成「暂无数据」，用户把「无权限」读成「没数据」；
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
    // 同族于 D-DEC-168「前端腿」的一处裸错（本域未登记新条目，随手随 159 一并如实修正并回报）：
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
    // R5-D-174：4030（无 op:bank:list）置权限空态，不再落到「暂无数据」
    if (e?.code === 4030) { denied.value = true; return }
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function handleImport(file) {
  if (!importBankAccountId.value) {
    ElMessage.warning('请先在右上角选择目标银行账户')
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
      ElMessage.warning(`导入完成：成功 ${inserted} 条，跳过 ${skipped} 条重复，${errs.length} 行有错（${errs.slice(0, 3).join('；')}${errs.length > 3 ? ' 等' : ''}）`)
    } else {
      ElMessage.success(`导入成功 ${inserted} 条${skipped ? '，跳过 ' + skipped + ' 条重复' : ''}`)
    }
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
  return false
}

function downloadTemplate() {
  const tpl = [
    '# 银行流水导入模板（列）：银行流水号,交易时间,方向(DEBIT借/CREDIT贷),金额,余额,对方户名,对方账号,摘要,对账状态,回单状态',
    '# 交易时间格式 yyyy-MM-dd HH:mm:ss；金额必填；重复流水号按所选银行账户幂等忽略；# 开头与表头行自动跳过',
    'bankSerial,transDate,transType,amount,balance,oppositeName,oppositeAccount,summary,reconStatus,slipStatus',
    'BS202610001,2026-10-05 09:30:00,CREDIT,12000.00,,客户回款,6222001,货款,UNRECON,NONE',
  ].join('\n')
  const blob = new Blob(['' + tpl], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '银行流水导入模板.csv'
  a.click()
  URL.revokeObjectURL(url)
}

// D-DEC-159：金额/余额统一走全站金额口径 formatMoney（千分位 + 2 位小数），
// 并修掉「余额列把 NULL 显示成 0.00」——op_bank_statement.balance 是可空列（导入时不填即 NULL），
// 原先本地 fmt 的 `Number(v || 0)` 会把"没有余额"渲染成"余额 0.00"，看着像一笔真余额。
const fmt = (v) => formatMoney(v, '—')

// 41号审查 P1-9：导出当前查询结果
function doExport() {
  // D-DEC-159 同口径导出：原先把 transDate 原样写进 CSV，导出件里仍是 `2026-12-24T10:50:31`，
  // 与本文件自带的导入模板（"交易时间格式 yyyy-MM-dd HH:mm:ss"）对不上，导出→再导入是回环脏值。
  const data = (rows.value || []).map((r) => ({ ...r, transDate: formatDateTime(r.transDate) }))
  exportCsv(data, [
    { prop: 'bankSerial', label: '银行流水号' }, { prop: 'transDate', label: '交易时间' },
    { prop: 'transType', label: '方向(DEBIT借/CREDIT贷)' }, { prop: 'amount', label: '金额' }, { prop: 'balance', label: '余额' },
    { prop: 'oppositeName', label: '对方户名' }, { prop: 'oppositeAccount', label: '对方账号' }, { prop: 'summary', label: '摘要' },
    { prop: 'reconStatus', label: '对账状态' }, { prop: 'slipStatus', label: '回单状态' },
  ], 'SharingStatement')
}
function reconText(s) {
  return { UNRECON: '未对账', RECONED: '已对账', DIFF: '差额' }[s] || s
}
function reconTag(s) {
  return { RECONED: 'success', DIFF: 'warning', UNRECON: 'danger' }[s] || 'info'
}
function slipText(s) {
  return { NONE: '无', DOWNLOADED: '已下载', ARCHIVED: '已归档' }[s] || s
}
function slipTag(s) {
  return { ARCHIVED: 'success', DOWNLOADED: '', NONE: 'info' }[s] || 'info'
}
function rowClass({ row }) {
  if (row.reconStatus === 'RECONED') return 'row-green'
  if (row.reconStatus === 'DIFF') return 'row-yellow'
  if (row.reconStatus === 'UNRECON') return 'row-red'
  return ''
}

onMounted(() => load())
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; color: #303133; }
:deep(.row-green) { background-color: #f0f9eb !important; }
:deep(.row-yellow) { background-color: #fdf6ec !important; }
:deep(.row-red) { background-color: #fef0f0 !important; }
</style>
