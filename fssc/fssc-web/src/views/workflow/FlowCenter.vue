<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="flow-center">
    <!-- 门户页头 -->
    <el-card shadow="never" class="fc-head" :body-style="{ padding: '16px 20px' }">
      <div class="fc-head-row">
        <div>
          <div class="fc-title">流程中心</div>
          <div class="fc-sub">跨业务域统一流程入口：待办 · 已办 · 我发起 · 抄送 · 委托 · 监控</div>
        </div>
        <div class="fc-head-actions">
          <el-button type="primary" @click="go('/requisition/todo')">去处理待办</el-button>
          <el-button @click="go('/requisition/mine')">我发起的</el-button>
        </div>
      </div>
    </el-card>

    <!-- 概览卡片 -->
    <el-row :gutter="12" class="fc-stats">
      <el-col v-for="c in cards" :key="c.key" :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="fc-stat" :body-style="{ padding: '16px' }" @click="go(c.path)">
          <div class="fc-stat-num" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="fc-stat-label">{{ c.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 流程效率（管理驾驶舱）：规则全部来自 /wf/perf/overview 真实聚合。
         仅持 wf:instance:list 的财务管理层/共享运营岗可见——全员页门控铁律：无权限时
         不请求、不渲染、不弹提示（隐藏而非报错），避免业务角色看到全公司时效数据。 -->
    <el-card v-if="canViewPerf" shadow="never" class="fc-block fc-perf" :body-style="{ padding: '14px 20px' }">
      <template #header>
        <div class="fc-block-head">
          <span>流程效率（近 {{ perfDays }} 天）</span>
          <div class="fc-perf-actions">
            <el-select v-model="perfDays" size="small" style="width:110px" @change="loadPerf">
              <el-option :value="7" label="近 7 天" />
              <el-option :value="30" label="近 30 天" />
              <el-option :value="90" label="近 90 天" />
            </el-select>
            <el-button link type="primary" size="small" @click="go('/flow-monitor')">监控大屏 / 效率分析</el-button>
          </div>
        </div>
      </template>
      <div class="fc-perf-row">
        <div v-for="m in perfMetrics" :key="m.label" class="fc-perf-item">
          <div class="fc-perf-num" :style="{ color: m.color }">{{ m.value }}</div>
          <div class="fc-perf-label">{{ m.label }}</div>
        </div>
      </div>
      <div v-if="perfNote" class="fc-perf-note">{{ perfNote }}</div>
    </el-card>

    <el-row :gutter="12">
      <!-- 待我处理（最近） -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="fc-block">
          <template #header>
            <div class="fc-block-head">
              <span>待我处理（最近）</span>
              <el-button link type="primary" size="small" @click="go('/requisition/todo')">全部待办</el-button>
            </div>
          </template>
          <SkeletonList v-if="loading.todo" :rows="6" :cols="4" />
          <el-table v-else :data="todoPreview" size="small" stripe max-height="360" empty-text="暂无待办">
            <el-table-column prop="bizNo" label="单号" width="150" sortable />
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="taskName" label="当前节点" width="120" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handle(row)">去处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 流程服务入口 -->
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="fc-block">
          <template #header><span>流程服务</span></template>
          <div class="fc-entries">
            <div v-for="e in entries" :key="e.path" class="fc-entry" @click="go(e.path)">
              <el-icon class="fc-entry-icon" :style="{ color: e.color }"><component :is="e.icon" /></el-icon>
              <div class="fc-entry-text">
                <div class="fc-entry-title">{{ e.title }}</div>
                <div class="fc-entry-desc">{{ e.desc }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  List, Checked, Document, Promotion, User, Monitor, DataLine, Setting, RefreshLeft,
} from '@element-plus/icons-vue'
import { http, flowApi } from '../../api'
import { hasPerm } from '../../utils/perm'
import { goBizPage, resolveBillRoute } from '@/utils/bizEntry'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'

const router = useRouter()

// 概览计数（/tenant/workbench/dashboard 无权限；抄送计数取 /wf/query/cc/mine 的 total）
const stats = reactive({ todo: 0, mine: 0, done: 0, cc: 0 })
const todoPreview = ref([])
const loading = reactive({ todo: false })

const cards = computed(() => [
  { key: 'todo', label: '我的待办', value: stats.todo, path: '/requisition/todo', color: '#e6a23c' },
  { key: 'mine', label: '我发起的单据', value: stats.mine, path: '/requisition/mine', color: '#409eff' },
  { key: 'done', label: '我的已办', value: stats.done, path: '/requisition/done', color: '#67c23a' },
  { key: 'cc', label: '我的抄送', value: stats.cc, path: '/flow-center/cc', color: '#909399' },
])

// 流程服务入口：与侧边栏菜单可见性同一规则（perm 缺省＝全员可见）
const ALL_ENTRIES = [
  { title: '我的待办', desc: '待我审批/处理的流程任务', path: '/requisition/todo', icon: List, color: '#e6a23c' },
  { title: '我的已办', desc: '我已处理过的流程任务', path: '/requisition/done', icon: Checked, color: '#67c23a' },
  { title: '我发起的', desc: '我提交的单据与流程', path: '/requisition/mine', icon: Document, color: '#409eff' },
  { title: '我的抄送', desc: '抄送给我的流程', path: '/flow-center/cc', icon: Promotion, color: '#909399' },
  // P3 G3：已办撤销回原节点的只读预览（与流程监控同档权限；执行面未开放）
  { title: '已办撤销预览', desc: '只读：撤回到某节点的影响面', path: '/flow-center/withdraw-preview', icon: RefreshLeft, color: '#e6a23c', perm: 'wf:instance:list' },
  { title: '我的委托代理', desc: '审批委托与代理规则', path: '/my/delegate', icon: User, color: '#9b59b6', perm: 'wf:delegate:list' },
  { title: '流程监控大屏', desc: '在办实例与积压监控', path: '/flow-monitor', icon: Monitor, color: '#f56c6c', perm: 'wf:instance:list' },
  { title: '审批流看板', desc: '流程定义与实例监控', path: '/wf/board', icon: DataLine, color: '#19be3b', perm: 'wf:bizflow:list' },
  { title: '流程管理', desc: '流程模型与节点配置', path: '/expense/flow', icon: Setting, color: '#606266', perm: 'wf:model:list' },
]
const entries = ALL_ENTRIES.filter((e) => !e.perm || hasPerm(e.perm))

// 流程效率：与监控大屏同档权限（wf:instance:list）。无权限＝不请求、不渲染、不弹提示。
const canViewPerf = hasPerm('wf:instance:list')
const perfDays = ref(30)
const perf = ref({})
const perfNote = ref('')
const perfMetrics = computed(() => [
  { label: '在途实例', value: perf.value.runningInstances ?? '-', color: '#409eff' },
  { label: '待办任务', value: perf.value.pendingTasks ?? '-', color: '#e6a23c' },
  { label: '今日办结', value: perf.value.finishedToday ?? '-', color: '#67c23a' },
  { label: '超时未办', value: perf.value.overdueInFlight ?? '-', color: '#f56c6c' },
  { label: 'SLA 达成率', value: perf.value.slaRate == null ? '不可测' : perf.value.slaRate + '%', color: '#9b59b6' },
  { label: '平均节点停留', value: fmtMinutes(perf.value.avgNodeMinutes), color: '#b88230' },
])

/** 分钟 → 人读时长（与监控大屏 formatDuration 同一规则，避免两页数字"看起来不一样"） */
function fmtMinutes(min) {
  if (min == null) return '-'
  if (min < 60) return min + ' 分钟'
  return (min / 60).toFixed(1) + ' 小时'
}

async function loadPerf() {
  if (!canViewPerf) return
  try {
    const d = await flowApi.perfOverview(perfDays.value, { silent: true })
    perf.value = d || {}
    perfNote.value = d?.note || ''
  } catch (e) {
    // 无权限/后端不可达：保持占位，不弹全局提示（本页对全员可见，弹窗会骚扰无权限用户）
    perf.value = {}
  }
}

function go(path) {
  router.push(path)
}

// 待办「去处理」：按单据类型分发到业务页；未命中则打开全链路追溯抽屉
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
// 与 Approval.vue / Workbench.vue 同一落点校验——发票池数据面按 inv:list 门控，
// 无权限岗不跳 #/invoice/query 吃「暂无数据＋缺少权限」，改落全链路追溯。
const canInvPool = hasPerm('inv:list')
function handle(row) {
  if (!canInvPool && resolveBillRoute(row?.billType || row?.bizType) === '/invoice') {
    traceBillId.value = row.bizId || row.billId || row.id
    traceBizType.value = row.bizType || row.billType || null
    traceVisible.value = true
    ElMessage.info('当前账号无发票池查看权限（inv:list），已打开全链路追溯')
    return
  }
  if (!goBizPage(router, row)) {
    traceBillId.value = row.bizId || row.billId || row.id
    traceBizType.value = row.bizType || row.billType || null
    traceVisible.value = true
    ElMessage.info('当前账号不能直接进入该单据的业务页面，已打开全链路追溯')
  }
}

async function load() {
  // 效率指标独立加载：无 wf:instance:list 时 loadPerf 直接返回，连请求都不发
  loadPerf()
  try {
    const d = await http.get('/tenant/workbench/dashboard')
    Object.assign(stats, d || {})
  } catch (e) { /* 概览失败不阻断门户 */ }
  loading.todo = true
  try {
    const res = await http.get('/tenant/workbench/my-todo', { params: { pageNum: 1, pageSize: 8 } })
    todoPreview.value = res?.list || []
  } catch (e) {
    todoPreview.value = []
  } finally {
    loading.todo = false
  }
  try {
    const cc = await flowApi.myCc({ pageNum: 1, pageSize: 1 })
    stats.cc = cc?.total || 0
  } catch (e) { /* 无抄送权限/空数据时保持 0 */ }
}

onMounted(load)
</script>

<style scoped>
.flow-center { padding: 4px; }
.fc-head { margin-bottom: 12px; }
.fc-head-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.fc-title { font-size: 20px; font-weight: 700; line-height: 1.4; }
.fc-sub { margin-top: 4px; font-size: 13px; color: var(--el-text-color-secondary); }
.fc-head-actions { display: flex; gap: 8px; }
.fc-stats { margin-bottom: 12px; }
.fc-stat { cursor: pointer; transition: transform .15s ease; }
.fc-stat:hover { transform: translateY(-2px); }
.fc-stat-num { font-size: 26px; font-weight: 700; line-height: 1.2; }
.fc-stat-label { margin-top: 4px; font-size: 13px; color: var(--el-text-color-secondary); }
.fc-block { min-height: 420px; }
.fc-block-head { display: flex; align-items: center; justify-content: space-between; }
.fc-entries { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.fc-entry { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; cursor: pointer; transition: all .15s ease; }
.fc-entry:hover { border-color: var(--el-color-primary); background: var(--el-fill-color-light); }
.fc-entry-icon { font-size: 22px; }
.fc-entry-text { min-width: 0; }
.fc-entry-title { font-size: 14px; font-weight: 600; }
.fc-entry-desc { font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
/* 流程效率卡：覆盖 .fc-block 的 420px 最小高度，它是给"待办列表"预留的 */
.fc-perf { min-height: 0; margin-bottom: 12px; }
.fc-perf-actions { display: flex; align-items: center; gap: 10px; }
.fc-perf-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
.fc-perf-item { text-align: center; padding: 6px 4px; }
.fc-perf-num { font-size: 22px; font-weight: 700; line-height: 1.25; }
.fc-perf-label { margin-top: 4px; font-size: 12px; color: var(--el-text-color-secondary); }
.fc-perf-note { margin-top: 10px; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.6; }
@media (max-width: 900px) { .fc-perf-row { grid-template-columns: repeat(3, 1fr); } }
</style>
