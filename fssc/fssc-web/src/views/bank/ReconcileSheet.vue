<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>未达账项调节表</b>
        <el-select v-model="bankAccountId" placeholder="银行账户" clearable filterable style="width: 220px">
          <el-option v-for="a in accounts" :key="a.id" :label="a.accountName + '（' + a.bankAccount + '）'" :value="a.id" />
        </el-select>
        <el-date-picker v-model="month" type="month" value-format="YYYYMM" placeholder="选择月份" style="width: 140px" />
        <el-button type="primary" size="small" @click="load">生成调节表</el-button>
        <el-button size="small" @click="runReconcile">立即勾对</el-button>
        <el-button v-if="bankAccountId" size="small" type="warning" @click="loadItems">追溯记录明细</el-button>
      <el-button size="small" @click="exportCsv">导出</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 13px">
          调节后余额 = 银行余额 − 企业已付银行未付 + 企业已收银行未收 + 银行已付企业未付 − 银行已收企业未收
        </span>
      </div>
    </template>

    <template v-if="sheet">
      <!-- 余额对比 -->
      <el-row :gutter="16" style="margin-bottom: 12px">
        <el-col :span="6"><el-statistic title="银行对账单余额" :value="Number(sheet.bankBalance || 0)" :precision="2" /></el-col>
        <el-col :span="6"><el-statistic title="调节后余额" :value="Number(sheet.adjustBalance || 0)" :precision="2" /></el-col>
        <el-col :span="6"><el-statistic title="企业账面余额" :value="Number(sheet.enterpriseBalance || 0)" :precision="2" /></el-col>
        <el-col :span="6">
          <el-statistic title="调节差（应=0）" :value="Number(sheet.diff || 0)" :precision="2">
            <template #suffix>
              <el-tag v-if="Math.abs(Number(sheet.diff || 0)) < 0.01" type="success" size="small">已平</el-tag>
              <el-tag v-else type="danger" size="small">待查</el-tag>
            </template>
          </el-statistic>
        </el-col>
      </el-row>

      <!-- 未达项明细 -->
      <el-row :gutter="12">
        <el-col :span="6">
          <el-card header="银行已收 企业未收" shadow="never" style="margin-bottom: 8px">
            <el-table :data="sheet.bankIn" size="small" max-height="260" v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
              <el-table-column prop="serialNo" label="流水号" width="90" show-overflow-tooltip  sortable />
              <el-table-column prop="oppositeName" label="对方" width="70" show-overflow-tooltip  sortable />
              <el-table-column label="金额" width="80" align="right"  sortable>
                <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
              </el-table-column>
              <el-table-column prop="transDate" label="日期" width="80"  sortable :formatter="(row, col, val) => formatDate(val)" />
            </el-table>
            <el-empty v-if="!sheet.bankIn.length" description="无" :image-size="40" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card header="银行已付 企业未付" shadow="never" style="margin-bottom: 8px">
            <el-table :data="sheet.bankOut" size="small" max-height="260" v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
              <el-table-column prop="serialNo" label="流水号" width="90" show-overflow-tooltip  sortable />
              <el-table-column prop="oppositeName" label="对方" width="70" show-overflow-tooltip  sortable />
              <el-table-column label="金额" width="80" align="right"  sortable>
                <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
              </el-table-column>
              <el-table-column prop="transDate" label="日期" width="80"  sortable :formatter="(row, col, val) => formatDate(val)" />
            </el-table>
            <el-empty v-if="!sheet.bankOut.length" description="无" :image-size="40" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card header="企业已收 银行未收" shadow="never" style="margin-bottom: 8px">
            <el-table :data="sheet.entIn" size="small" max-height="260" v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
              <el-table-column prop="serialNo" label="收款单号" width="90" show-overflow-tooltip  sortable />
              <el-table-column prop="oppositeName" label="往来单位" width="70" show-overflow-tooltip  sortable />
              <el-table-column label="金额" width="80" align="right"  sortable>
                <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
              </el-table-column>
              <el-table-column prop="transDate" label="日期" width="80"  sortable :formatter="(row, col, val) => formatDate(val)" />
            </el-table>
            <el-empty v-if="!sheet.entIn.length" description="无" :image-size="40" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card header="企业已付 银行未付" shadow="never" style="margin-bottom: 8px">
            <el-table :data="sheet.entOut" size="small" max-height="260" v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
              <el-table-column prop="serialNo" label="流水号" width="90" show-overflow-tooltip  sortable />
              <el-table-column prop="oppositeName" label="对方" width="70" show-overflow-tooltip  sortable />
              <el-table-column label="金额" width="80" align="right"  sortable>
                <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
              </el-table-column>
              <el-table-column prop="transDate" label="日期" width="80"  sortable :formatter="(row, col, val) => formatDate(val)" />
            </el-table>
            <el-empty v-if="!sheet.entOut.length" description="无" :image-size="40" />
          </el-card>
        </el-col>
      </el-row>
    </template>
    <el-empty v-else description="选择账户和月份后生成调节表" />

    <!-- 未达账差异明细追溯（bank_recon_item 写入数据库记录） -->
    <el-dialog v-model="itemsVisible" title="未达账差异明细追溯（已保存）" width="920px">
      <template #header>
        <div style="display: flex; gap: 8px; align-items: center; width: 100%">
          <span>未达账差异明细追溯（已保存）</span>
          <el-select v-model="itemsType" placeholder="全部类型" clearable style="width: 170px" @change="loadItems">
            <el-option label="银行已收企业未收" value="BANK_IN" />
            <el-option label="银行已付企业未付" value="BANK_OUT" />
            <el-option label="企业已收银行未收" value="ENT_IN" />
            <el-option label="企业已付银行未付" value="ENT_OUT" />
          </el-select>
        </div>
      </template>
      <SkeletonList v-if="itemsLoading" :rows="8" :cols="5" />
      <el-table :data="itemsPager.pagedData" v-else size="small" max-height="420" v-loading="itemsLoading" empty-text="暂无数据" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="itemType" label="未达类型" width="130" sortable>
          <template #default="{ row }">
            <el-tag :type="itemTypeTag(row.itemType)" size="small">{{ itemTypeName(row.itemType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="serialNo" label="流水号" width="140" show-overflow-tooltip  sortable />
        <el-table-column prop="oppositeName" label="对方" width="110" show-overflow-tooltip  sortable />
        <el-table-column prop="amount" label="金额" width="110" align="right" sortable>
          <template #default="{ row }">￥{{ fmtMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="bizDate" label="日期" width="100"  sortable :formatter="(row, col, val) => formatDate(val)" />
        <el-table-column prop="sourceRef" label="来源引用" width="150" show-overflow-tooltip  sortable />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CLEARED' ? 'success' : 'warning'" size="small">{{ itemStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <TablePager :pager="itemsPager" :hide-on-less-than="10" />
      <el-empty v-if="!itemsLoading && !items.length" description="该账户与月份暂无未达账项明细" />
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { p2Api, masterApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { useTablePager } from '@/composables/useTablePager'
import TablePager from '@/components/common/TablePager.vue'

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const selRows = ref([])
const accounts = ref([])
const bankAccountId = ref(null)
const month = ref('')
const sheet = ref(null)
const loading = ref(false)
const itemsVisible = ref(false)
const items = ref([])
const itemsLoading = ref(false)
const itemsType = ref('')
// 未达账明细追溯弹窗列表本地分页（数据量大时避免一次性渲染上百行 DOM）
const itemsPager = useTablePager({ mode: 'local' })

const ITEM_TYPE = {
  BANK_IN: '银行已收企业未收',
  BANK_OUT: '银行已付企业未付',
  ENT_IN: '企业已收银行未收',
  ENT_OUT: '企业已付银行未付',
}
const itemTypeName = (t) => ITEM_TYPE[t] || t
// 未达账项状态（bank_recon_item.status）——对账审批定稿后由 OPEN 转 CLEARED
const ITEM_STATUS = { OPEN: '未清', CLEARED: '已清结' }
const itemStatusLabel = (s) => ITEM_STATUS[s] || s || '—'
const itemTypeTag = (t) => t === 'BANK_IN' ? 'success' : t === 'BANK_OUT' ? 'warning' : t === 'ENT_IN' ? 'primary' : 'danger'

function fmtMoney(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }

async function load() {
  loading.value = true
  try {
    const d = await p2Api.bankReconcileSheet({ bankAccountId: bankAccountId.value, month: month.value })
    sheet.value = d
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

/** 追溯未达账差异写入数据库明细（bank_recon_item，sheet 生成时记录） */
async function loadItems() {
  itemsType.value = itemsType.value || ''
  itemsLoading.value = true
  itemsVisible.value = true
  try {
    items.value = await p2Api.bankReconItems({
      bankAccountId: bankAccountId.value,
      month: month.value || '',
      itemType: itemsType.value || null,
    })
    if (!items.value.length) ElMessage.info('该账户与月份暂无未达账项明细，请先生成调节表')
    itemsPager.setSource(items.value)
  } catch (e) { ElMessage.error(e.message) } finally { itemsLoading.value = false }
}

async function runReconcile() {
  try { await ElMessageBox.confirm('将对当前账户与月份执行银行勾对，命中未达账将被核销，确认继续？', '银行勾对', { type: 'warning' }) } catch { return }
  try {
    const r = await p2Api.bankReconcileRun()
    const matched = Number(r?.matched || 0)
    const unmatched = Number(r?.unmatched || 0)
    const ambiguous = Number(r?.ambiguous || 0)
    const foreign = Number(r?.foreignSerialBlocked || 0)
    // 当场给出"勾对几条／未勾几条"，未达账项不等调节表环节才发现
    // 丙：同人建单—同人勾对的收款核销单当场提示复核，不阻断本轮勾对
    ElMessage({
      type: (unmatched > 0 || r?.selfMatchWarning) ? 'warning' : 'success',
      duration: 6000,
      message: `勾对完成：命中 ${matched} 条、未勾对 ${unmatched} 条`
        + (ambiguous > 0 ? `（同金额多候选拒配 ${ambiguous} 条，请人工勾对）` : '')
        + (foreign > 0 ? `（${foreign} 条的同金额单据已归属其他银行流水号，拒绝顶替）` : '')
        + (r?.selfMatchWarning ? `（${r.selfMatchWarning}）` : ''),
    })
    const serials = Array.isArray(r?.unmatchedSerials) ? r.unmatchedSerials : []
    if (serials.length) {
      ElMessage({ type: 'info', duration: 10000, message: '未勾对流水：' + serials.slice(0, 12).join('、') + (serials.length > 12 ? ' 等' + serials.length + '条' : '') })
    }
    load()
  } catch (e) {
    ElMessage.error('勾对失败：' + (e.message || '未知错误'))
  }
}

onMounted(async () => {
  const now = new Date()
  month.value = now.getFullYear() + String(now.getMonth() + 1).padStart(2, '0')
  try {
    const r = await masterApi.bank.page({ pageNum: 1, pageSize: 100 })
    accounts.value = r.list || []
    if (accounts.value.length) bankAccountId.value = accounts.value[0].id
    await load()
  } catch (e) { /* 忽略 */ }
})

function exportCsv() {
  if (!items.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'itemType', label: '未达类型' }, { prop: 'serialNo', label: '流水号' }, { prop: 'oppositeName', label: '对方' }, { prop: 'amount', label: '金额' }, { prop: 'bizDate', label: '日期' }, { prop: 'sourceRef', label: '来源引用' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = items.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `ReconcileSheet_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}


function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
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
  a.download = `ReconcileSheet_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>
