<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div :class="['audit-board', { tabless: isSingle }]">
    <div class="page-header" style="margin-bottom: 16px;">
      <h2 style="margin: 0; font-size: 18px; font-weight: 600;">审计看板</h2>
    </div>
    <el-tabs v-model="activeTab">
      <!-- Tab 1: Reconcile -->
      <el-tab-pane label="数据一致性" name="reconcile">
        <div class="toolbar">
          <el-button type="primary" :loading="running" @click="onRunReconcile">执行对账</el-button>
          <el-select v-model="reconcileQuery.status" placeholder="处理状态" clearable style="width:140px" @change="onReconcileQuery">
            <el-option label="未处理" value="OPEN" />
            <el-option label="已处理" value="RESOLVED" />
          </el-select>
          <el-input v-model="reconcileQuery.billType" placeholder="单据类型（如 LOAN/CLAIM）" clearable style="width:200px" @keyup.enter="onReconcileQuery" />
          <el-button @click="loadReconcile">刷新</el-button>
        </div>
        <SkeletonList v-if="reconcileLoading" :rows="8" :cols="5" />
        <el-table :data="reconcileList" v-else border stripe empty-text="暂无数据" @row-dblclick="handleView">
          <el-table-column prop="checkItem" label="检查项" min-width="140" :formatter="(row) => checkItemText(row.checkItem)" />
          <el-table-column prop="billNo" label="业务单号" min-width="160" />
          <el-table-column prop="billType" label="单据类型" width="110" :formatter="(row) => billTypeText(row.billType)" />
          <el-table-column prop="expectValue" label="期望值" min-width="160" show-overflow-tooltip />
          <el-table-column prop="actualValue" label="实际值" min-width="180" show-overflow-tooltip />
          <el-table-column prop="diffDesc" label="差异说明" min-width="160" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="110" align="center" sortable>
            <template #default="{ row }">
              <el-tag :type="reconcileStatusType(row.status)">{{ reconcileStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="resolveRemark" label="处理说明" min-width="160" show-overflow-tooltip />
          <el-table-column prop="resolveTime" label="处理时间" width="170" sortable :formatter="(row, col, val) => formatDateTime(val)" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleView(row)">详情</el-button>
              <el-button v-if="row.status === 'OPEN'" link type="warning" @click="onResolve(row)">处理</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          v-model:current-page="reconcileQuery.pageNum"
          v-model:page-size="reconcileQuery.pageSize"
          :total="reconcileTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next, jumper"
          @size-change="loadReconcile"
          @current-change="loadReconcile"
        />
      </el-tab-pane>

      <!-- Tab 2: Audit Trail -->
      <el-tab-pane label="审计轨迹" name="trail">
        <div class="toolbar">
          <el-input v-model="trailQuery.bizNo" placeholder="业务单号（可模糊）" clearable style="width:220px" @keyup.enter="onTrailQuery" />
          <el-select v-model="trailQuery.bizType" placeholder="业务类型" clearable style="width:160px" @change="onTrailQuery">
            <el-option v-for="o in BIZ_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input v-model="trailQuery.keyword" placeholder="对象/说明关键字" clearable style="width:200px" @keyup.enter="onTrailQuery" />
          <el-button type="primary" @click="onTrailQuery">查询</el-button>
          <el-button @click="onTrailReset">重置</el-button>
          <el-button :loading="exporting" @click="onTrailExport">导出</el-button>
        </div>
        <SkeletonList v-if="trailLoading" :rows="8" :cols="5" />
        <el-table :data="trailList" v-else border stripe empty-text="暂无数据" @row-dblclick="handleTrailView">
          <el-table-column prop="createTime" label="操作时间" width="170" sortable :formatter="(row, col, val) => formatDateTime(val)" />
          <el-table-column prop="bizType" label="业务类型" width="120" :formatter="(row) => billTypeText(row.bizType)" />
          <el-table-column prop="bizNo" label="业务单号" min-width="180" />
          <el-table-column prop="action" label="动作" width="120" :formatter="(row) => actionText(row.action)" />
          <el-table-column prop="objType" label="变更对象" width="140" :formatter="(row) => objTypeText(row.objType)" />
          <el-table-column prop="operatorName" label="操作人" min-width="150" />
          <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="变更前后" width="110" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.beforeJson || row.afterJson" type="success" size="small">有快照</el-tag>
              <el-tag v-else type="info" size="small">无</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleTrailView(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pager"
          v-model:current-page="trailQuery.pageNum"
          v-model:page-size="trailQuery.pageSize"
          :total="trailTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next, jumper"
          @size-change="loadTrail"
          @current-change="loadTrail"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>

import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditApi, store } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { formatDateTime, showRowDetail } from '@/utils/format'
// 本页手写名称表只覆盖旧值域，audit_trail 里还有 RC_RECEIVABLE / ARCHIVE /
// SALE_RETURN / PUR_RETURN 等无键值 ⇒ 业务类型列直显英文。保留本地措辞优先，未命中回落全站名称表。
import { bizTypeLabel, actionLabel } from '@/constants/enums.js'

// D-42 整改：本页原先绑的是 operation/module/operateTime/detail/ipAddress ——
// audit_trail 实体与表里根本没有这些列，故 6 列有 5 列恒空、且无法按业务单号检索。
// 现一律改绑真实字段（bizType/bizNo/action/objType/operator/remark/createTime/beforeJson/afterJson）。

const props = defineProps({ activeTab: { type: String, default: '' } })
const isSingle = computed(() => !!props.activeTab)
const activeTab = ref(props.activeTab || 'reconcile')

const CHECK_ITEM_TEXT = {
  VOUCHER: '凭证一致性', PAYMENT: '付款一致性', STATUS: '最终状态记录',
  AR_RECEIVABLE: '应收余额对平', AP_RECON: '供应商对账余额', BCP_WRITEOFF: '跨系统核销对平',
}
const BILL_TYPE_TEXT = {
  APPLY: '付款申请', LOAN: '借款', REPAY: '还款', CLAIM: '报销', PAY: '付款',
  PREPAY: '预付', SETTLE: '对公结算', VOUCHER: '凭证', PERIOD: '期间',
  RECON: '对账单', RECEIPT: '收款单', CONTRACT: '合同',
}
const ACTION_TEXT = {
  APPROVE: '审批通过', REJECT: '审批驳回', UPDATE: '变更', INSERT: '新增',
  PAY: '付款', CHECKSUM: '校验和失败', TAMPER_REJECT: '防篡改拒绝',
  WITHDRAW: '撤回', POST: '过账', CLOSE: '关账', REOPEN: '反结账', RESOLVE: '核销',
}
const OBJ_TYPE_TEXT = {
  exp_bill: '借款/报账单', op_voucher: '凭证', gl_month_close: '期间月结',
  rc_receivable: '应收单', rc_receipt: '收款单', supplier_recon: '供应商对账单',
  ar_recon_record: '应收对账记录', md_period: '会计期间',
}
const BIZ_TYPE_OPTIONS = Object.entries(BILL_TYPE_TEXT).map(([value, label]) => ({ value, label }))

function checkItemText(v) { return CHECK_ITEM_TEXT[v] || v || '-' }
function billTypeText(v) { return BILL_TYPE_TEXT[v] || bizTypeLabel(v) || v || '-' }
function actionText(v) { return ACTION_TEXT[v] || actionLabel(v) || v || '-' }
function objTypeText(v) { return OBJ_TYPE_TEXT[v] || v || '-' }

/* Reconcile */
const reconcileLoading = ref(false)
const running = ref(false)
const reconcileList = ref([])
const reconcileTotal = ref(0)
const reconcileQuery = reactive({ pageNum: 1, pageSize: 10, status: '', billType: '' })

async function loadReconcile() {
  reconcileLoading.value = true
  try {
    const res = await auditApi.reconcilePage({
      pageNum: reconcileQuery.pageNum, pageSize: reconcileQuery.pageSize,
      status: reconcileQuery.status || undefined, billType: reconcileQuery.billType || undefined,
    })
    reconcileList.value = res.records || res.list || []
    reconcileTotal.value = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    reconcileLoading.value = false
  }
}

function onReconcileQuery() {
  reconcileQuery.pageNum = 1
  loadReconcile()
}

async function onRunReconcile() {
  try {
    await ElMessageBox.confirm('确认执行数据一致性对账？这可能需要一些时间。', '执行对账', { type: 'warning' })
  } catch { return }
  running.value = true
  try {
    const n = await auditApi.reconcileRun()
    ElMessage.success(`对账执行完成，异常 ${n ?? 0} 条`)
    loadReconcile()
  } catch (e) {
    ElMessage.error(e.message || '对账失败')
  } finally {
    running.value = false
  }
}

// audit_reconcile.status 值域只有 OPEN/RESOLVED（原先按 MATCH/MISMATCH 判断，故 21 条差异全显灰字且「处理」按钮永不出现）
function reconcileStatusType(status) {
  return status === 'RESOLVED' ? 'success' : status === 'OPEN' ? 'danger' : 'info'
}

function reconcileStatusText(status) {
  return status === 'RESOLVED' ? '已处理' : status === 'OPEN' ? '未处理' : status || '-'
}

async function onResolve(row) {
  let value
  try {
    const r = await ElMessageBox.prompt('请输入处理说明', `处理差异 - ${checkItemText(row.checkItem)}（${row.billNo || row.id}）`, {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim() ? true : '处理说明不能为空'),
      inputPlaceholder: '请说明差异原因及处理方式',
    })
    value = r.value
  } catch { return }
  try {
    await auditApi.reconcileResolve(row.id, { remark: value })
    ElMessage.success('已标记处理')
    loadReconcile()
  } catch (e) {
    ElMessage.error(e.message || '处理失败')
  }
}

function handleView(row) {
  showRowDetail({
    billNo: row.billNo,
    billType: billTypeText(row.billType),
    checkItem: checkItemText(row.checkItem),
    expectValue: row.expectValue,
    actualValue: row.actualValue,
    diffDesc: row.diffDesc,
    status: reconcileStatusText(row.status),
    resolveBy: row.resolveBy,
    resolveRemark: row.resolveRemark,
    resolveTime: row.resolveTime ? formatDateTime(row.resolveTime) : '',
    createTime: row.createTime ? formatDateTime(row.createTime) : '',
  }, {
    title: '一致性差异详情',
    labels: {
      billNo: '业务单号', billType: '单据类型', checkItem: '检查项', expectValue: '期望值',
      actualValue: '实际值', diffDesc: '差异说明', status: '状态', resolveBy: '处理人',
      resolveRemark: '处理说明', resolveTime: '处理时间', createTime: '生成时间',
    },
  })
}

/* Trail */
const trailLoading = ref(false)
const exporting = ref(false)
const trailList = ref([])
const trailTotal = ref(0)
const trailQuery = reactive({ bizNo: '', bizType: '', keyword: '', pageNum: 1, pageSize: 10 })

async function loadTrail() {
  trailLoading.value = true
  try {
    const res = await auditApi.trailPage({
      pageNum: trailQuery.pageNum, pageSize: trailQuery.pageSize,
      bizNo: trailQuery.bizNo || undefined,
      bizType: trailQuery.bizType || undefined,
      keyword: trailQuery.keyword || undefined,
    })
    trailList.value = res.records || res.list || []
    trailTotal.value = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    trailLoading.value = false
  }
}

function onTrailQuery() {
  trailQuery.pageNum = 1
  loadTrail()
}

function onTrailReset() {
  trailQuery.bizNo = ''
  trailQuery.bizType = ''
  trailQuery.keyword = ''
  onTrailQuery()
}

// 后端 /export 现按同一组过滤条件出全量（含变更前/变更后），故走 fetch 直下而非导出当前页
async function onTrailExport() {
  exporting.value = true
  try {
    const p = new URLSearchParams()
    if (trailQuery.bizNo) p.set('bizNo', trailQuery.bizNo)
    if (trailQuery.bizType) p.set('bizType', trailQuery.bizType)
    if (trailQuery.keyword) p.set('keyword', trailQuery.keyword)
    const resp = await fetch('/api/tenant/audit/trail/export?' + p.toString(), {
      headers: { Authorization: 'Bearer ' + store.token },
    })
    if (!resp.ok) throw new Error('导出失败：HTTP ' + resp.status)
    const blob = await resp.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `审计轨迹_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success('已导出 CSV')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function handleTrailView(row) {
  showRowDetail({
    createTime: row.createTime ? formatDateTime(row.createTime) : '',
    bizType: billTypeText(row.bizType),
    bizNo: row.bizNo,
    action: actionText(row.action),
    objType: objTypeText(row.objType),
    objId: row.objId,
    operatorName: row.operatorName,
    remark: row.remark,
    beforeJson: row.beforeJson,
    afterJson: row.afterJson,
  }, {
    title: '审计轨迹详情',
    labels: {
      createTime: '操作时间', bizType: '业务类型', bizNo: '业务单号', action: '动作',
      objType: '变更对象', objId: '对象ID', operatorName: '操作人', remark: '说明',
      beforeJson: '变更前', afterJson: '变更后',
    },
  })
}

onMounted(() => {
  loadReconcile()
  loadTrail()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.pager {
  margin-top: 10px;
  justify-content: flex-end;
}
/* 一 TAB = 一菜单：从菜单深链进来（activeTab 命中）时隐藏 tab 导航条，只显示该页 */
.tabless :deep(.el-tabs__header) { display: none; }
</style>
