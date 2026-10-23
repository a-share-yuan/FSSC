<template>
  <!-- ============ 三单匹配 · 异常派单 ============ -->
  <el-card :header="$t('pay.route.cardTitle')" style="margin-top: 12px">
    <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
      <el-button size="small" @click="loadApprovedPays">{{ $t('pay.route.btnRefreshApproved') }}</el-button>
      <span style="color: #909399; font-size: 12px">{{ $t('pay.route.hintFlow') }}</span>
    </div>
    <SkeletonList v-if="routeLoading" :rows="8" :cols="5" />
    <el-table :data="pagedData" size="small" stripe v-else :empty-text="$t('pay.route.hintNoApproved')" @row-dblclick="openRowDetail">
      <el-table-column prop="billNo" :label="$t('pay.shared.colPayBillNo')" width="160" sortable />
      <el-table-column prop="title" :label="$t('pay.shared.colTitle')" min-width="160" show-overflow-tooltip sortable />
      <el-table-column :label="$t('field.amount')" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="payStatusType(row.status)" size="small">{{ payStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('pay.shared.btnTrace') }}</el-button>
          <el-button link size="small" type="warning" @click="routeMatch(row)">{{ $t('pay.shared.btnExceptionRoute') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <TablePager :pager="pager" @change="loadApprovedPays" />
  </el-card>

  <el-dialog v-model="routeVis" :title="$t('pay.route.ctxResultTitle')" width="640px" destroy-on-close>
    <el-alert :closable="false" show-icon style="margin-bottom: 10px"
      :type="routeResult.frozen ? 'warning' : 'success'"
      :title="routeResult.frozen ? t('pay.route.msgFrozen') : t('pay.route.msgNotFrozen')" />
    <el-table :data="routeRows" size="small" border :empty-text="$t('pay.route.hintNoDiff')" @row-dblclick="openRowDetail">
      <el-table-column prop="role" :label="$t('pay.route.colRole')" width="180" sortable />
      <el-table-column :label="$t('pay.route.colDiffReason')" min-width="260">
        <template #default="{ row }">{{ (row.reasons || []).join('、') || '—' }}</template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="routeVis = false">{{ $t('common.close') }}</el-button>
    </template>
  </el-dialog>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="EXP_PAY" />
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { showRowDetail } from '@/utils/format'
import { billApi, store } from '../../api'
import { http } from '../../request'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// R4-02: BillTraceDrawer
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

const approvedPays = ref([])
// P0-2: 服务端分页（后端 myPage 返回 PageResult，替代原固定 pageSize:100 的静默截断）
const pager = useTablePager({ mode: 'server', storageKey: 'pay_match_route' })
const { pagedData } = pager
const routeLoading = ref(false)
const routeVis = ref(false)
const routeResult = ref({ frozen: false })
const routeRows = ref([])

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
function payStatusLabel(s) { return STATUS_LABEL[s] || s }
function payStatusType(s) { return STATUS_TYPE[s] || 'info' }

async function loadApprovedPays() {
  routeLoading.value = true
  try {
    await pager.load(async ({ pageNum, pageSize }) => {
      const r = await billApi.myPage({ pageNum, pageSize, billType: 'PAY', status: 'APPROVED' })
      approvedPays.value = r.list || []
      return { list: approvedPays.value, total: r.total || approvedPays.value.length }
    })
  } catch (e) { ElMessage.error(e.message) } finally { routeLoading.value = false }
}

async function routeMatch(row) {
  try {
    const d = await http.post(`/tenant/pay/match/${row.id}/route`)
    const routing = (d && d.routing) || {}
    routeRows.value = Object.keys(routing).map((role) => ({ role, reasons: routing[role] || [] }))
    routeResult.value = { frozen: !!(d && d.frozen) }
    routeVis.value = true
    ElMessage.success(t('pay.route.msgDone'))
  } catch (e) { ElMessage.error(e.message) }
}

defineExpose({ loadApprovedPays })
</script>
