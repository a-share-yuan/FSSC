<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
        <b>系统日志（系统监控）</b>
        <!-- R5-D-046：操作日志 / 登录日志双页签；登录日志首次切到时懒加载 -->
        <el-tabs v-model="activeTab" style="flex: 1; min-width: 240px" class="log-tabs">
          <el-tab-pane label="操作日志" name="oper" />
          <el-tab-pane label="登录日志" name="login" />
        </el-tabs>
        <span style="color: #909399; font-size: 13px">审计日志只读（租户操作留痕，支持翻页查询全量）</span>
      </div>
    </template>

    <template v-if="activeTab === 'oper'">
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 10px">
        <el-input v-model="q.keyword" placeholder="标题/操作人/URL" clearable style="width: 200px" @keyup.enter="search" />
        <el-select v-model="q.businessType" placeholder="业务类型" clearable style="width: 140px">
          <el-option v-for="t in bizTypes" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="q.status" placeholder="结果" clearable style="width: 110px">
          <el-option label="成功" :value="0" />
          <el-option label="失败" :value="1" />
        </el-select>
        <el-button type="primary" size="small" @click="search">查询</el-button>
        <el-button size="small" @click="exportCsv">导出 CSV</el-button>
        <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
      </div>
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="rows" size="small" stripe v-else max-height="540" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="title" label="操作" width="150" show-overflow-tooltip sortable />
        <el-table-column prop="businessType" label="业务类型" width="100" sortable :formatter="(row) => bizTypeText(row.businessType)" />
        <el-table-column prop="requestMethod" label="方法" width="80" sortable />
        <el-table-column prop="operUrl" label="URL" min-width="160" show-overflow-tooltip sortable />
        <el-table-column prop="operUserName" label="操作人" width="100" sortable />
        <el-table-column prop="operIp" label="IP" width="120" sortable />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="失败原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="costTime" label="耗时(ms)" width="90" sortable />
        <el-table-column prop="operTime" label="时间" width="170" sortable :formatter="(row, col, val) => formatDateTime(val)" />
      </el-table>

      <!-- P0 易用性：日志增长最快，原来固定取第 1 页 200 条且无翻页入口，超量后静默截断 -->
      <el-pagination
        v-if="total > 0"
        class="log-pager"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        :page-sizes="[20, 50, 100, 200]"
        @current-change="onPage"
        @size-change="onSizeChange"
      />
    </template>

    <template v-else>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 10px">
        <el-input v-model="lq.keyword" placeholder="用户名/IP" clearable style="width: 200px" @keyup.enter="searchLogin" />
        <el-select v-model="lq.status" placeholder="结果" clearable style="width: 110px">
          <el-option label="成功" :value="0" />
          <el-option label="失败" :value="1" />
        </el-select>
        <el-button type="primary" size="small" @click="searchLogin">查询</el-button>
        <span style="color: #909399; font-size: 13px">登录留痕只读（sys_login_log，按时间倒序）</span>
      </div>
      <el-table :data="loginRows" size="small" stripe v-loading="loginLoading" max-height="540" empty-text="暂无数据">
        <el-table-column prop="userName" label="用户名" width="120" show-overflow-tooltip />
        <el-table-column prop="ipaddr" label="IP" width="130" show-overflow-tooltip />
        <el-table-column prop="loginLocation" label="归属地" min-width="140" show-overflow-tooltip />
        <el-table-column prop="browser" label="浏览器" min-width="130" show-overflow-tooltip />
        <el-table-column prop="os" label="操作系统" width="120" show-overflow-tooltip />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="msg" label="说明" min-width="150" show-overflow-tooltip />
        <el-table-column prop="loginTime" label="时间" width="170" sortable :formatter="(row, col, val) => formatDateTime(val)" />
      </el-table>
      <el-pagination
        v-if="loginTotal > 0"
        class="log-pager"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="loginTotal"
        :page-size="loginPageSize"
        :current-page="loginPageNum"
        :page-sizes="[20, 50, 100, 200]"
        @current-change="onLoginPage"
        @size-change="onLoginSizeChange"
      />
    </template>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { showRowDetail, formatDateTime } from '@/utils/format'
import { http, logApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { operBusinessTypeLabel } from '@/constants/enums.js'

const selRows = ref([])
const rows = ref([])
const loading = ref(false)
// 分页状态（原来固定取第 1 页 200 条且无翻页入口，日志超量后静默截断）
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const q = reactive({ keyword: '', businessType: '', status: null })
const bizTypes = [
  { label: '新增 INSERT', value: 'INSERT' },
  { label: '修改 UPDATE', value: 'UPDATE' },
  { label: '删除 DELETE', value: 'DELETE' },
  { label: '查询 QUERY', value: 'QUERY' },
  { label: '审批 APPROVE', value: 'APPROVE' },
]
function bizTypeText(v) { return operBusinessTypeLabel(v) || '-' }

// R5-D-046：登录日志页签状态（懒加载——首次切到才请求，避免无权限账号空打请求）
const activeTab = ref('oper')
const loginRows = ref([])
const loginLoading = ref(false)
const loginPageNum = ref(1)
const loginPageSize = ref(20)
const loginTotal = ref(0)
const loginLoadedOnce = ref(false)
const lq = reactive({ keyword: '', status: null })

// 双击行：按中文字典展示，避免直吐实体裸字段名与 status=1.00 / costTime=2.00 这类格式化错误
function openRowDetail(row) {
  return showRowDetail({
    operTime: row.operTime ? formatDateTime(row.operTime) : '',
    title: row.title,
    businessType: bizTypeText(row.businessType),
    operUserName: row.operUserName,
    operDeptName: row.operDeptName,
    requestMethod: row.requestMethod,
    method: row.method,
    operUrl: row.operUrl,
    operIp: row.operIp,
    operLocation: row.operLocation,
    status: row.status === 0 ? '成功' : '失败',
    errorMsg: row.errorMsg,
    costTime: row.costTime == null ? '' : row.costTime + ' ms',
    operParam: row.operParam,
    jsonResult: row.jsonResult,
  }, {
    title: '系统日志详情',
    labels: {
      operTime: '操作时间', title: '操作', businessType: '业务类型', operUserName: '操作人工号',
      operDeptName: '操作人部门', requestMethod: '请求方式', method: '实现方法', operUrl: '请求地址',
      operIp: '来源IP', operLocation: '归属地', status: '结果', errorMsg: '失败原因',
      costTime: '耗时', operParam: '请求参数', jsonResult: '返回结果',
    },
  })
}

async function load() {
  loading.value = true
  try {
    const r = await logApi.page({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...q,
      status: q.status === null ? undefined : q.status,
    })
    rows.value = r.list || []
    total.value = Number(r.total || 0)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

/** 翻页 */
function onPage(p) { pageNum.value = p; load() }
/** 每页条数变化：回到第一页，避免停在越界空页 */
function onSizeChange(s) { pageSize.value = s; pageNum.value = 1; load() }
/** 查询：条件变化后必须回到第一页，否则会停在不存在的页码上显示空列表 */
function search() { pageNum.value = 1; load() }

// R5-D-046：登录日志加载/查询/翻页（审计表只读）
async function loadLogin() {
  loginLoading.value = true
  try {
    const r = await http.get('/tenant/system/log/login/page', {
      params: {
        pageNum: loginPageNum.value,
        pageSize: loginPageSize.value,
        keyword: lq.keyword || undefined,
        status: lq.status === null ? undefined : lq.status,
      },
    })
    loginRows.value = r.list || []
    loginTotal.value = Number(r.total || 0)
  } catch (e) { ElMessage.error(e.message) } finally { loginLoading.value = false }
}
function onLoginPage(p) { loginPageNum.value = p; loadLogin() }
function onLoginSizeChange(s) { loginPageSize.value = s; loginPageNum.value = 1; loadLogin() }
function searchLogin() { loginPageNum.value = 1; loadLogin() }
watch(activeTab, (v) => {
  if (v === 'login' && !loginLoadedOnce.value) { loginLoadedOnce.value = true; loadLogin() }
})

onMounted(load)

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'title', label: '操作' }, { prop: 'businessType', label: '业务类型' }, { prop: 'requestMethod', label: '方法' }, { prop: 'operUrl', label: 'URL' }, { prop: 'operUserName', label: '操作人' }, { prop: 'operIp', label: 'IP' }, { prop: 'status', label: '结果' }, { prop: 'errorMsg', label: '失败原因' }, { prop: 'costTime', label: '耗时(ms)' }, { prop: 'operTime', label: '时间' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((row) => cols.map((c) => {
    let v = row?.[c.prop]
    if (c.prop === 'status') v = row?.status === 0 ? '成功' : '失败'
    if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `SysLog_${new Date().toISOString().slice(0, 10)}.csv`
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
  a.download = `SysLog_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>

<style scoped>
.log-pager { margin-top: 12px; display: flex; justify-content: flex-end; flex-wrap: wrap; }
.log-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
</style>
