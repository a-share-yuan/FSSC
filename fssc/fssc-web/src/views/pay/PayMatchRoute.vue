<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <!-- ============ 三单匹配 · 异常派单 ============ -->
  <el-card header="三单匹配 · 异常派单" style="margin-top: 12px">
    <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
      <el-button size="small" @click="loadApprovedPays">刷新已审批付款单</el-button>
      <span style="color: #909399; font-size: 12px">对已审批的三单匹配付款单发起异常派单，差异按类型路由到对应角色并自动冻结付款</span>
    </div>
    <SkeletonList v-if="routeLoading" :rows="8" :cols="5" />
    <el-table :data="pagedData" size="small" stripe v-else empty-text="暂无已审批付款单" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" label="付款单号" width="160" sortable />
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip sortable />
      <el-table-column label="金额" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="payStatusType(row.status)" size="small">{{ payStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <el-button link size="small" type="warning" @click="routeMatch(row)">异常派单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <TablePager :pager="pager" @change="loadApprovedPays" />
  </el-card>

  <el-dialog v-model="routeVis" title="三单匹配异常派单结果" width="640px" destroy-on-close>
    <el-alert :closable="false" show-icon style="margin-bottom: 10px"
      :type="routeResult.frozen ? 'warning' : 'success'"
      :title="routeResult.frozen ? '该付款单存在差异，已冻结付款，请通知对应角色处理' : '派单完成，未触发付款冻结'" />
    <el-table :data="routeRows" size="small" border empty-text="未发现差异" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="role" label="处理角色" width="180" sortable />
      <el-table-column label="差异原因" min-width="260">
        <template #default="{ row }">{{ (row.reasons || []).join('、') || '—' }}</template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="routeVis = false">关闭</el-button>
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

const approvedPays = ref([])
// P0-2: 服务端分页（后端 myPage 返回 PageResult，替代原固定 pageSize:100 的静默截断）
const pager = useTablePager({ mode: 'server', storageKey: 'pay_match_route' })
const { pagedData } = pager
const routeLoading = ref(false)
const routeVis = ref(false)
const routeResult = ref({ frozen: false })
const routeRows = ref([])
const selRows = ref([])

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
function payStatusLabel(s) { return STATUS_LABEL[s] || s }
function payStatusType(s) { return STATUS_TYPE[s] || 'info' }
function handleSelectionChange(val) { selRows.value = val }

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
    ElMessage.success('异常派单完成')
  } catch (e) { ElMessage.error(e.message) }
}

defineExpose({ loadApprovedPays })
</script>
