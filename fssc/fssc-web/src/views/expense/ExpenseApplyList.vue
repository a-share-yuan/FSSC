<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="expense-apply-list">
    <div class="page-header" style="margin-bottom: 16px;">
      <h2 style="margin: 0; font-size: 18px; font-weight: 600;">事前申请</h2>
    </div>
    <el-card>
      <!-- Toolbar -->
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索单号/标题" clearable style="width:240px" @keyup.enter="loadList" />
        <el-button type="primary" @click="onQuery">查询</el-button>
        <el-button @click="query.keyword = ''; onQuery()">重置</el-button>
        <el-button type="success" @click="openCreate">新增事前申请</el-button>
      </div>

      <!-- Table -->
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table class="cm-target-exp-list" :data="list" v-else border stripe empty-text="暂无数据" @row-dblclick="openRowDetail">
        <el-table-column prop="billNo" label="单据编号" min-width="150" sortable />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="applyUserName" label="申请人" min-width="100" />
        <el-table-column prop="totalAmount" label="金额" width="130" align="right" sortable>
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center" sortable>
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" sortable :formatter="(row, col, val) => formatDateTime(val)" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">          <InlineActions :visible="true" @view="onViewDetail(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="true" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
            <el-button v-if="canEditResubmit(row)" link size="small" type="warning" @click="onEdit(row)">{{ row.status === 'REJECTED' ? '编辑重提' : '编辑' }}</el-button>
            <el-button v-if="row.status === BillStatus.APPROVED" link type="primary" @click="onConvert(row)">转报销</el-button>
            <el-button link type="primary" @click="onViewDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <el-pagination
        class="pager"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, prev, pager, next, jumper"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- Create Dialog -->
    <el-dialog v-model="createDialogVisible" title="新增事前申请" width="480px">
      <el-form :model="createForm" label-width="80px" ref="createFormRef" :rules="createFormRules">
        <el-form-item label="标题" prop="title">
          <el-input v-model="createForm.title" placeholder="申请标题" />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="createForm.amount" :min="0" :precision="2" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="3" placeholder="申请说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailDialogVisible" title="事前申请详情" width="640px">
      <SkeletonDetail v-if="detailLoading" :rows="6" />
      <div v-else>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="单据编号">{{ detail.billNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)">{{ statusText(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.applyUserName }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatMoney(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updateTime }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="EXP_APPLY" />

  <CoachMark ref="expApplyCm" :guide-key="_guideKey" :selector="'.cm-target-exp-list'" title="报销申请" text="报销申请列表展示所有费用报销单据，支持新建、查看和审批。" />
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>

import { onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billApi } from '../../api'
import { statusType as _statusType, statusLabel as _statusLabel, BillStatus, canEditResubmit, editBillPath } from '../../constants/billStatus'
import SkeletonList from '../../components/SkeletonList.vue'
import SkeletonDetail from '../../components/SkeletonDetail.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import CoachMark from '@/components/CoachMark.vue'
import InlineActions from '@/components/InlineActions.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime } from '@/utils/format'
import { useApprovalScope } from '../../composables/useApprovalScope'
import { useApprovalDialog } from '@/composables/useApprovalDialog'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'

// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => loadList(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})
const expApplyCm = ref(null)

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ keyword: '', billType: 'APPLY', pageNum: 1, pageSize: 10 })
const router = useRouter()
const currentRoute = useRoute()

/* ---- Create ---- */
const createDialogVisible = ref(false)
const saving = ref(false)
const createFormRef = ref()
const createForm = reactive({ title: '', amount: 0, remark: '' })
const createFormRules = {
  title: [{ required: true, message: '请输入标题' }],
  amount: [{ required: true, message: '请输入金额' }],
}

/* ---- Detail ---- */
const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({})

function formatMoney(val) {
  if (val == null) return '-'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusType(status) { return _statusType(status) }

function statusText(status) { return _statusLabel(status) || status || '-' }

async function loadList() {
  loading.value = true
  try {
    const res = await billApi.myPage(query)
    list.value = res.records || res.list || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function onQuery() {
  query.pageNum = 1
  loadList()
}

function openCreate() {
  Object.assign(createForm, { title: '', amount: 0, remark: '' })
  createDialogVisible.value = true
}

async function onCreate() {
  await createFormRef.value?.validate()
  saving.value = true
  try {
    await billApi.saveDraft({ billType: 'APPLY', title: createForm.title, amount: createForm.amount, remark: createForm.remark })
    ElMessage.success('草稿已保存')
    createDialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 编辑草稿/驳回重提：深链到对应子类型填单页（editBillId 由 workbench 载入并自动回退草稿）
function onEdit(row) {
  router.push({ path: editBillPath(row), query: { editBillId: row.id } })
}

async function onConvert(row) {
  await ElMessageBox.confirm(`确认将事前申请 [${row.billNo}] 转为报销单？`, '转报销', { type: 'warning' })
  try {
    await billApi.convertApply(row.id)
    ElMessage.success('已转为报销单')
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '转换失败')
  }
}

async function onViewDetail(row) {
  detailDialogVisible.value = true
  detailLoading.value = true
  detail.value = {}
  try {
    detail.value = await billApi.detail(row.id) || {}
  } catch (e) {
    ElMessage.error(e.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function openRowDetail(row) {
  onViewDetail(row)
}

onMounted(() => {
  // 支持从待办/工作台等入口携带 billNo（或 keyword）直达定位
  const q = currentRoute.query
  if (q.billNo) query.keyword = String(q.billNo)
  else if (q.keyword) query.keyword = String(q.keyword)
  loadList()
})

// R4-06: Show guide on first visit
const _guideKey = 'fssc_guide_expApplyCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => expApplyCm.value?.show(), 800) } } catch {}
// InlineActions handlers

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: loadList })
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
</style>
