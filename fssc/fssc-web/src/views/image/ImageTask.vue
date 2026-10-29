<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>影像任务</b>
        <el-select v-model="q.bizType" placeholder="业务类型" clearable style="width: 130px" @change="load">
          <el-option label="费用报销" value="EXP_CLAIM" />
          <el-option label="合同" value="CONTRACT" />
        </el-select>
        <el-input v-model="q.keyword" placeholder="批次号/业务单号" clearable style="width: 180px" @keyup.enter="load" />
        <el-select v-model="q.sealStatus" placeholder="封单状态" clearable style="width: 120px" @change="load">
          <el-option label="已封单" value="SEALED" />
          <el-option label="未封单" value="UNSEALED" />
        </el-select>
        <el-select v-model="q.status" placeholder="任务状态" clearable style="width: 130px" @change="load">
          <el-option label="已上传" value="UPLOADED" />
          <el-option label="已质检" value="CHECKED" />
        </el-select>
        <el-button type="primary" size="small" @click="load">查询</el-button>
      <el-button size="small" @click="exportCsv">导出</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 13px">
          采集来源：80 扫描客户端 / 81 手机 / 82 人工上传 / 83 第三方系统
        </span>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" size="small" stripe v-else max-height="500" v-loading="loading" @selection-change="handleSelectionChange" empty-text="暂无数据" @row-dblclick="openTrace">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="batchNo" label="批次号" width="170"  sortable />
      <el-table-column label="业务类型" width="100">
        <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
      </el-table-column>
      <el-table-column prop="bizNo" label="业务单号" width="140"  sortable />
      <el-table-column prop="source" label="采集来源" width="110" sortable>
        <template #default="{ row }">{{ srcLabel(row.source) }}</template>
      </el-table-column>
      <el-table-column prop="docCount" label="页数" width="60"  sortable />
      <el-table-column label="封单状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.sealStatus === 'SEALED' ? 'success' : 'info'" size="small">{{ row.sealStatus === 'SEALED' ? '已封单' : '未封单' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="调阅审核" width="100">
        <template #default="{ row }">
          <el-tag :type="reviewType(row.reviewStatus)" size="small">{{ reviewLabel(row.reviewStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="任务状态" width="90" sortable>
        <template #default="{ row }">{{ row.status === 'CHECKED' ? '已质检' : '已上传' }}</template>
      </el-table-column>
      <el-table-column label="提单人" width="90">
        <template #default="{ row }">{{ userName(row.scanUserId) }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="提单时间" width="160"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <el-button link size="small" type="primary" @click="viewPages(row)">查看影像</el-button>
          <el-button link size="small" type="primary" @click="toggleSeal(row)">{{ row.sealStatus === 'SEALED' ? '解锁' : '封单' }}</el-button>
          <el-button v-if="!row.reviewStatus || row.reviewStatus === 'PENDING'" link size="small" type="success" @click="setReview(row, 'OK')">审核通过</el-button>
          <el-button v-if="!row.reviewStatus || row.reviewStatus === 'PENDING'" link size="small" type="warning" @click="setReview(row, 'REJECTED')">审核拒绝</el-button>
          <el-button link size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 通用影像查看器 -->
    <FileViewer ref="fileViewer" />

    <el-pagination
      style="margin-top: 10px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total" :page-size="q.pageSize" :current-page="q.pageNum"
      @current-change="(n) => { q.pageNum = n; load() }"
    />
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
</template>

<script setup>
import { bizTypeLabel } from '@/constants/enums'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { imageApi, userApi, store } from '../../api'
import FileViewer from '../../components/FileViewer.vue'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { formatDate, formatDateTime } from '@/utils/format'

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.bizId || row.id
  traceBizType.value = row.bizType || null
  traceVisible.value = true
}

const selRows = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const fileViewer = ref(null)
const users = ref([])
const q = reactive({ pageNum: 1, pageSize: 20, bizType: '', keyword: '', sealStatus: '', status: '' })

// 用户 ID → 用户名
function userName(id) { const u = users.value.find((x) => x.id === id); return u ? (u.nickName || u.userName) : (id ? '#' + id : '—') }

// 业务类型翻译

/** 查看影像：取批次影像页 fileId 列表 → 通用查看器预览 */
async function viewPages(row) {
  try {
    const pages = await imageApi.pages(row.id)
    const fileIds = (pages || []).map((p) => p.fileId).filter((x) => x != null)
    if (!fileIds.length) { ElMessage.info('该批次暂无影像页'); return }
    fileViewer.value?.open(fileIds, '影像查看：' + (row.batchNo || row.id))
  } catch (e) { ElMessage.error(e.message) }
}

function srcLabel(s) {
  return { SCANNER: '80 扫描客户端', H5: '81 手机', PC: '82 人工上传', THIRD: '83 第三方系统' }[s] || s
}
function reviewLabel(s) {
  return { PENDING: '待审核', OK: '已审核', REJECTED: '已拒绝' }[s] || s
}
function reviewType(s) {
  return s === 'OK' ? 'success' : s === BillStatus.REJECTED ? 'danger' : 'info'
}

async function load() {
  loading.value = true
  try {
    const r = await imageApi.taskPage({ ...q })
    rows.value = r.list || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function toggleSeal(row) {
  const sealed = row.sealStatus !== 'SEALED'
  await imageApi.seal(row.id, sealed)
  ElMessage.success(sealed ? '已封单' : '已解锁')
  load()
}

async function setReview(row, status) {
  // 判定后不可重复判定（服务端状态机已拦），破坏性操作前置确认
  try {
    await ElMessageBox.confirm(
      `确认对影像任务 ${row.batchNo || row.id} 执行「${status === 'OK' ? '审核通过' : '审核拒绝'}」？判定后不可重复判定，如需更正请走影像替扫/重采集。`,
      '调阅审核', { type: 'warning' })
  } catch (e) { return }
  try {
    await imageApi.reviewStatus(row.id, status)
    ElMessage.success('已更新调阅审核')
  } catch (e) { ElMessage.error(e.message) }
  load()
}

async function del(row) {
  await ElMessageBox.confirm(`删除影像任务 ${row.batchNo}？`, '删除', { type: 'warning' })
    .then(async () => {
      await imageApi.batchDelete(row.id)
      ElMessage.success('已删除')
      load()
    }).catch(() => {})
}

onMounted(() => {
  // 默认只加载同公司人员
  userApi.page({ pageNum: 1, pageSize: 200, companyId: store.user?.companyId }).then((d) => { users.value = d?.list || [] }).catch(() => {})
  load()
})

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'batchNo', label: '批次号' }, { prop: 'bizNo', label: '业务单号' }, { prop: 'source', label: '采集来源' }, { prop: 'docCount', label: '页数' }, { prop: 'status', label: '任务状态' }, { prop: 'createTime', label: '提单时间' }]
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
  a.download = `ImageTask_${new Date().toISOString().slice(0, 10)}.csv`
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
  a.download = `ImageTask_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>
