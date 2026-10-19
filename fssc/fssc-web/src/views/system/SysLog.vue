<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
        <b>{{ $t('system.sysLog.ttlPage') }}</b>
        <!-- R5-D-046：操作日志 / 登录日志双页签；登录日志首次切到时懒加载 -->
        <el-tabs v-model="activeTab" style="flex: 1; min-width: 240px" class="log-tabs">
          <el-tab-pane :label="$t('system.sysLog.tabOpLog')" name="oper" />
          <el-tab-pane :label="$t('system.sysLog.tabLoginLog')" name="login" />
        </el-tabs>
        <span style="color: #909399; font-size: 13px">{{ $t('system.sysLog.hintAuditReadonly') }}</span>
      </div>
    </template>

    <template v-if="activeTab === 'oper'">
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 10px">
        <el-input v-model="q.keyword" :placeholder="$t('system.sysLog.phSearchOpLog')" clearable style="width: 200px" @keyup.enter="search" />
        <el-select v-model="q.businessType" :placeholder="$t('system.common.colBizType')" clearable style="width: 140px">
          <el-option v-for="t in bizTypes" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="q.status" :placeholder="$t('field.result')" clearable style="width: 110px">
          <el-option :label="$t('system.common.stSuccess')" :value="0" />
          <el-option :label="$t('system.common.stFailed')" :value="1" />
        </el-select>
        <el-button type="primary" size="small" @click="search">{{ $t('common.search') }}</el-button>
        <el-button size="small" @click="exportCsv">{{ $t('system.common.btnExportCsv') }}</el-button>
        <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('system.sysLog.btnExportSelectedN', { v1: selRows.length }) }}</el-button>
      </div>
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="rows" size="small" stripe v-else max-height="540" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="title" :label="$t('common.operation')" width="150" show-overflow-tooltip sortable />
        <el-table-column prop="businessType" :label="$t('system.common.colBizType')" width="100" sortable :formatter="(row) => bizTypeText(row.businessType)" />
        <el-table-column prop="requestMethod" :label="$t('system.common.colMethod')" width="80" sortable />
        <el-table-column prop="operUrl" label="URL" min-width="160" show-overflow-tooltip sortable />
        <el-table-column prop="operUserName" :label="$t('system.sysLog.colOperator')" width="100" sortable />
        <el-table-column prop="operIp" label="IP" width="120" sortable />
        <el-table-column :label="$t('field.result')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? t('system.common.stSuccess') : t('system.common.stFailed') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" :label="$t('system.sysLog.colFailReason')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="costTime" :label="$t('system.common.colCostMs')" width="90" sortable />
        <el-table-column prop="operTime" :label="$t('field.time')" width="170" sortable :formatter="(row, col, val) => formatDateTime(val)" />
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
        <el-input v-model="lq.keyword" :placeholder="$t('system.sysLog.phSearchLogin')" clearable style="width: 200px" @keyup.enter="searchLogin" />
        <el-select v-model="lq.status" :placeholder="$t('field.result')" clearable style="width: 110px">
          <el-option :label="$t('system.common.stSuccess')" :value="0" />
          <el-option :label="$t('system.common.stFailed')" :value="1" />
        </el-select>
        <el-button type="primary" size="small" @click="searchLogin">{{ $t('common.search') }}</el-button>
        <span style="color: #909399; font-size: 13px">{{ $t('system.sysLog.hintLoginReadonly') }}</span>
      </div>
      <el-table :data="loginRows" size="small" stripe v-loading="loginLoading" max-height="540" :empty-text="$t('common.empty')">
        <el-table-column prop="userName" :label="$t('system.common.colUsername')" width="120" show-overflow-tooltip />
        <el-table-column prop="ipaddr" label="IP" width="130" show-overflow-tooltip />
        <el-table-column prop="loginLocation" :label="$t('system.sysLog.colLocation')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="browser" :label="$t('system.sysLog.colBrowser')" min-width="130" show-overflow-tooltip />
        <el-table-column prop="os" :label="$t('system.sysLog.colOs')" width="120" show-overflow-tooltip />
        <el-table-column :label="$t('field.result')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? t('system.common.stSuccess') : t('system.common.stFailed') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="msg" :label="$t('field.desc')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="loginTime" :label="$t('field.time')" width="170" sortable :formatter="(row, col, val) => formatDateTime(val)" />
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
import {onMounted, reactive, ref, watch, computed} from 'vue'
import { ElMessage } from 'element-plus'
import { showRowDetail, formatDateTime } from '@/utils/format'
import { http, logApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { operBusinessTypeLabel } from '@/constants/enums.js'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const selRows = ref([])
const rows = ref([])
const loading = ref(false)
// 分页状态（原来固定取第 1 页 200 条且无翻页入口，日志超量后静默截断）
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const q = reactive({ keyword: '', businessType: '', status: null })
const bizTypes = computed(() => ([
  { label: t('system.sysLog.actInsert'), value: 'INSERT' },
  { label: t('system.sysLog.actUpdate'), value: 'UPDATE' },
  { label: t('system.sysLog.actDelete'), value: 'DELETE' },
  { label: t('system.sysLog.actQuery'), value: 'QUERY' },
  { label: t('system.sysLog.actApprove'), value: 'APPROVE' },
]))
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
    status: row.status === 0 ? t('system.common.stSuccess') : t('system.common.stFailed'),
    errorMsg: row.errorMsg,
    costTime: row.costTime == null ? '' : row.costTime + ' ms',
    operParam: row.operParam,
    jsonResult: row.jsonResult,
  }, {
    title: t('system.sysLog.ttlDetail'),
    labels: {
      operTime: t('system.sysLog.colOperatedAt'), title: t('common.operation'), businessType: t('system.common.colBizType'), operUserName: t('system.sysLog.colOperatorNo'),
      operDeptName: t('system.sysLog.colOperatorDept'), requestMethod: t('system.sysLog.colReqMethod'), method: t('system.sysLog.colImplMethod'), operUrl: t('system.common.colReqUrl'),
      operIp: t('system.sysLog.colSourceIp'), operLocation: t('system.sysLog.colLocation'), status: t('field.result'), errorMsg: t('system.sysLog.colFailReason'),
      costTime: t('system.common.colCost'), operParam: t('system.sysLog.colReqParams'), jsonResult: t('system.sysLog.colRespResult'),
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
  if (!rows.value.length) { ElMessage.warning(t('system.common.msgNoExportData')); return }
  const cols = [{ prop: 'title', label: t('common.operation') }, { prop: 'businessType', label: t('system.common.colBizType') }, { prop: 'requestMethod', label: t('system.common.colMethod') }, { prop: 'operUrl', label: 'URL' }, { prop: 'operUserName', label: t('system.sysLog.colOperator') }, { prop: 'operIp', label: 'IP' }, { prop: 'status', label: t('field.result') }, { prop: 'errorMsg', label: t('system.sysLog.colFailReason') }, { prop: 'costTime', label: t('system.common.colCostMs') }, { prop: 'operTime', label: t('field.time') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((row) => cols.map((c) => {
    let v = row?.[c.prop]
    if (c.prop === 'status') v = row?.status === 0 ? t('system.common.stSuccess') : t('system.common.stFailed')
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
  ElMessage.success(t('system.common.msgExportedCsv'))
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('system.sysLog.msgPickRowsFirst')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('system.sysLog.msgNoColumnInfo')); return }
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
  ElMessage.success(t('system.sysLog.msgExportedN', { v1: selRows.value.length }))
}

</script>

<style scoped>
.log-pager { margin-top: 12px; display: flex; justify-content: flex-end; flex-wrap: wrap; }
.log-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
</style>
