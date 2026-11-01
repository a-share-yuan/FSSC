<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="flow-log-center">
    <el-card shadow="never">
      <template #header>
        <span class="header-title">流程日志中心</span>
        <span class="header-note">流程处理详细日志（诊断说明）· 实例时间线 · 开关与清理 · 归档预览</span>
      </template>

      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- 1. 日志分页查询 -->
        <el-tab-pane label="日志查询" name="query">
          <el-form :inline="true" size="small" style="margin-bottom: 10px">
            <el-form-item label="流程实例">
              <el-select v-model="q.procInstId" filterable remote clearable :remote-method="searchInstances"
                         :loading="instLoading" placeholder="按单号/标题搜索实例" style="width: 240px" @change="resetQuery">
                <el-option v-for="i in instOptions" :key="i.procInstId" :label="instLabel(i)" :value="i.procInstId" />
              </el-select>
            </el-form-item>
            <el-form-item label="单号"><el-input v-model="q.bizNo" clearable style="width: 150px" @keyup.enter="resetQuery" /></el-form-item>
            <el-form-item label="单据类型"><el-input v-model="q.bizType" clearable style="width: 130px" @keyup.enter="resetQuery" /></el-form-item>
            <el-form-item label="日志类型">
              <el-select v-model="q.logType" clearable placeholder="全部" style="width: 170px" @change="resetQuery">
                <el-option v-for="t in LOG_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="开始">
              <el-date-picker v-model="q.from" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 150px" />
            </el-form-item>
            <el-form-item label="结束">
              <el-date-picker v-model="q.to" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 150px" />
            </el-form-item>
            <el-form-item><el-button type="primary" @click="resetQuery">查询</el-button></el-form-item>
          </el-form>

          <el-table :data="rows" v-loading="loading" size="small" stripe border max-height="520" empty-text="暂无日志（开关关闭时不记录日志）">
            <el-table-column prop="createTime" label="时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">{{ logTypeLabel(row.logType) }}</template>
            </el-table-column>
            <el-table-column prop="bizNo" label="单号" width="140" show-overflow-tooltip />
            <el-table-column prop="nodeName" label="节点" width="140" show-overflow-tooltip />
            <el-table-column prop="content" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="级别" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.level === 'WARN' ? 'warning' : 'info'">{{ row.level || 'INFO' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="procInstId" label="实例ID" width="150" show-overflow-tooltip />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :disabled="!row.procInstId" @click="openTimeline(row)">时间线</el-button>
                <el-button link type="info" size="small" :disabled="!row.detail" @click="openDetail(row)">明细</el-button>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="pager" @change="load" />
        </el-tab-pane>

        <!-- 2. 实例时间线 -->
        <el-tab-pane label="实例时间线" name="timeline">
          <el-form :inline="true" size="small" style="margin-bottom: 10px">
            <el-form-item label="流程实例">
              <el-select v-model="timelineInstId" filterable remote clearable :remote-method="searchInstances"
                         :loading="instLoading" placeholder="按单号/标题搜索实例" style="width: 280px">
                <el-option v-for="i in instOptions" :key="i.procInstId" :label="instLabel(i)" :value="i.procInstId" />
              </el-select>
            </el-form-item>
            <el-form-item><el-button type="primary" :loading="timelineLoading" @click="loadTimeline">查询</el-button></el-form-item>
          </el-form>
          <el-timeline v-if="timelineRows.length">
            <el-timeline-item
              v-for="(item, idx) in timelineRows"
              :key="idx"
              :timestamp="formatDateTime(item.createTime)"
              :type="item.level === 'WARN' ? 'warning' : 'primary'"
              placement="top"
            >
              <div class="tl-title">
                <b>{{ logTypeLabel(item.logType) }}</b>
                <span v-if="item.nodeName" class="tl-node">{{ item.nodeName }}</span>
                <el-tag v-if="item.bizNo" size="small" type="info" effect="plain">{{ item.bizNo }}</el-tag>
              </div>
              <div class="tl-content">{{ item.content || '—' }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="请选择流程实例后查询（升序展示该实例的完整处理日志）" :image-size="80" />
        </el-tab-pane>

        <!-- 3. 开关与清理 -->
        <el-tab-pane label="开关与清理" name="switch">
          <el-descriptions :column="1" border size="small" style="max-width: 720px">
            <el-descriptions-item label="详细日志开关">
              <el-tag :type="switchState?.enabled ? 'success' : 'info'">{{ switchState?.enabled ? '已开启' : '已关闭（缺省）' }}</el-tag>
              <span class="hint">参数键：{{ switchState?.cfgKey || '—' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="说明">{{ switchState?.hint || '加载中…' }}</el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">按保留天数清理历史日志（物理删除，日志非审计凭证）</el-divider>
          <el-form :inline="true" size="small">
            <el-form-item label="保留天数"><el-input-number v-model="keepDays" :min="1" :max="3650" style="width: 140px" /></el-form-item>
            <el-form-item>
              <el-button v-permission="'wf:config:update'" type="danger" :loading="cleanLoading" @click="doClean">执行清理</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 4. 归档预览 -->
        <el-tab-pane label="归档预览" name="archive">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
            title="纯只读：只做 COUNT，不搬移、不删除任何数据（按当前配置回答「当前会归档多少条、N 是多少」）。" />
          <el-descriptions v-if="archive" :column="4" border size="small" style="margin-bottom: 10px">
            <el-descriptions-item label="保留月数上限">{{ archive.maxMonths ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="默认月数">{{ archive.defaultMonths ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="批大小参数键">{{ archive.batchLimitKey || '—' }}</el-descriptions-item>
            <el-descriptions-item label="预计归档总数">{{ archive.total ?? 0 }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="archive?.tenants || []" size="small" stripe border max-height="380" empty-text="暂无可归档实例">
            <el-table-column prop="tenantId" label="账套编号" width="120" />
            <el-table-column label="归档开关" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '已启用' : '未启用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="months" label="保留月数" width="100" align="right" />
            <el-table-column prop="limit" label="批大小" width="100" align="right" />
            <el-table-column prop="count" label="可归档实例数" width="130" align="right" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="detailVisible" title="日志明细快照" width="640px" destroy-on-close>
      <pre class="detail-pre">{{ prettyDetail }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { wfOpsApi, flowApi } from '../../api'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { formatDateTime } from '@/utils/format'

/**
 * 流程日志中心（G3）：数据源 WfProcessLogController（/api/tenant/wf/process-log/**，读 wf:instance:list、清理 wf:config:update）
 * 与 WfPerfController 的归档预览（/api/tenant/wf/perf/archive-preview，wf:instance:list）。
 */
const LOG_TYPES = [
  { value: 'PROCESS_STARTED', label: '流程发起' },
  { value: 'TASK_CREATED', label: '任务生成' },
  { value: 'TASK_ASSIGNED', label: '任务指派' },
  { value: 'TASK_COMPLETED', label: '任务办结' },
  { value: 'TASK_CANCELLED', label: '任务取消' },
  { value: 'PROCESS_COMPLETED', label: '流程结束' },
  { value: 'TIMER_FIRED', label: '定时器触发' },
]
const LOG_TYPE_MAP = Object.fromEntries(LOG_TYPES.map((t) => [t.value, t.label]))
function logTypeLabel(t) { return LOG_TYPE_MAP[t] || t || '—' }

const tab = ref('query')
const loadedTabs = new Set()

const q = reactive({ procInstId: '', bizNo: '', bizType: '', logType: '', from: null, to: null })
const pager = useTablePager({ mode: 'server', storageKey: 'wf_process_log' })
const { rows, loading } = pager

function resetQuery() { pager.resetPage(); load() }
async function load() {
  await pager.load(async (p) => {
    const r = await wfOpsApi.processLogPage({
      pageNum: p.pageNum,
      pageSize: p.pageSize,
      procInstId: q.procInstId || undefined,
      bizNo: q.bizNo || undefined,
      bizType: q.bizType || undefined,
      logType: q.logType || undefined,
      from: q.from || undefined,
      to: q.to || undefined,
    })
    return { list: r?.list || [], total: r?.total || 0 }
  })
}

// 时间线
const timelineInstId = ref('')
const timelineRows = ref([])
const timelineLoading = ref(false)

/**
 * 流程实例选项：候选取自「流程管理›实例列表」（单号/标题远程检索），
 * 界面只让选实例、不再让人手输 procInstId。
 */
const instOptions = ref([])
const instLoading = ref(false)
async function searchInstances(keyword) {
  instLoading.value = true
  try {
    const r = await flowApi.instancePage({ pageNum: 1, pageSize: 20, keyword: keyword || undefined })
    instOptions.value = r?.list || []
  } catch { instOptions.value = [] } finally { instLoading.value = false }
}
function instLabel(i) {
  return [i.bizNo || i.title || '', i.currentNode, i.status].filter(Boolean).join(' · ') || i.procInstId
}
/** 从日志行点「时间线」：把该行实例补进选项，避免下拉显示成裸实例号 */
function openTimeline(row) {
  const procInstId = row.procInstId
  timelineInstId.value = procInstId
  if (procInstId && !instOptions.value.some((i) => i.procInstId === procInstId)) {
    instOptions.value = [{ procInstId, bizNo: row.bizNo, status: '' }, ...instOptions.value]
  }
  tab.value = 'timeline'
  loadTimeline()
}
async function loadTimeline() {
  if (!timelineInstId.value) { ElMessage.warning('请选择流程实例'); return }
  timelineLoading.value = true
  try { timelineRows.value = await wfOpsApi.processLogByInstance(timelineInstId.value) }
  catch (e) { ElMessage.error(e.message); timelineRows.value = [] } finally { timelineLoading.value = false }
}

// 明细快照
const detailVisible = ref(false)
const detailText = ref('')
const prettyDetail = computed(() => {
  try { return JSON.stringify(JSON.parse(detailText.value), null, 2) } catch { return detailText.value || '—' }
})
function openDetail(row) { detailText.value = row.detail; detailVisible.value = true }

// 开关与清理
const switchState = ref(null)
const keepDays = ref(30)
const cleanLoading = ref(false)
async function loadSwitch() {
  try { switchState.value = await wfOpsApi.processLogSwitch() } catch (e) { ElMessage.error(e.message) }
}
async function doClean() {
  cleanLoading.value = true
  try {
    const n = await wfOpsApi.processLogClean(keepDays.value)
    ElMessage.success(`已清理 ${n ?? 0} 条历史日志`)
  } catch (e) { ElMessage.error(e.message) } finally { cleanLoading.value = false }
}

// 归档预览
const archive = ref(null)
async function loadArchive() {
  try { archive.value = await wfOpsApi.archivePreview() } catch (e) { ElMessage.error(e.message) }
}

function onTabChange(name) {
  if (loadedTabs.has(name)) return
  loadedTabs.add(name)
  if (name === 'switch') loadSwitch()
  if (name === 'archive') loadArchive()
}

onMounted(() => { loadedTabs.add('query'); load(); searchInstances('') })
</script>

<style scoped>
.header-title { font-size: 15px; font-weight: 600; color: var(--app-text, #303133); margin-right: 10px; }
.header-note { font-size: 12px; color: #909399; }
.hint { margin-left: 8px; font-size: 12px; color: #909399; }
.tl-title { display: flex; gap: 8px; align-items: center; margin-bottom: 4px; }
.tl-node { font-size: 12px; color: #909399; }
.tl-content { color: var(--app-text-secondary, #606266); font-size: 13px; }
.detail-pre { max-height: 460px; overflow: auto; background: var(--app-bg, #f5f7fa); padding: 12px; border-radius: 4px; font-size: 12px; }
</style>