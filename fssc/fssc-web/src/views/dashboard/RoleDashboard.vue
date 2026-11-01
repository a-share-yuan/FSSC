<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="role-dashboard">
    <div class="page-header" style="margin-bottom: 16px;">
      <h2 style="margin: 0; font-size: 18px; font-weight: 600;">角色工作台</h2>
    </div>
    <!-- 角色切换 -->
    <div class="role-bar">
      <span class="role-label">当前角色:</span>
      <el-radio-group v-model="currentRole" size="small" @change="onRoleChange">
        <el-radio-button label="OPERATOR">操作员</el-radio-button>
        <el-radio-button label="SUPERVISOR">主管</el-radio-button>
        <el-radio-button label="CFO">财务总监</el-radio-button>
        <el-radio-button label="ADMIN">管理员</el-radio-button>
      </el-radio-group>
      <!-- 切换器与登录人真实角色无关，不写破这句就会被读成"切了就看到那个角色的数" -->
      <span class="role-scope-note">当前为全局视图：切换只决定显示哪几张卡，读数不随所选角色收窄</span>
    </div>

    <!-- KPI 卡片区 -->
    <div v-if="hasPerfKpi" class="kpi-scope">统计范围：{{ kpiScopeText }}（适用：今日完成／平均耗时／超时率／系统状态）</div>
    <div class="kpi-row">
      <div v-for="kpi in kpiCards" :key="kpi.label" class="kpi-card" :class="kpi.cls" @click="drillKpi(kpi)" style="cursor: pointer">
        <div class="kpi-icon">{{ kpi.icon }}</div>
        <div class="kpi-body">
          <div class="kpi-val">{{ kpi.value }}</div>
          <div class="kpi-label">{{ kpi.label }}</div>
        </div>
      </div>
    </div>
    <!-- 四项 KPI 的规则说明（原先是写死字面量，看不出数从哪来） -->
    <div v-if="hasPerfKpi" class="kpi-note">{{ kpiNote }}</div>

    <!-- Widget 区 -->
    <div class="widget-grid">
      <div v-for="w in widgets" :key="w.key" class="widget-card">
        <div class="widget-title">{{ w.title }}</div>
        <div class="widget-body">
          <!-- 操作员：我的任务 -->
          <div v-if="w.key === 'myTasks'" class="task-list">
            <div v-for="t in myTasks" :key="t.id" class="task-item" @click="$router.push('/approval')">
              <el-tag size="small" :type="t.timeout ? 'danger' : 'warning'">{{ docTypeLabel(t.billType) }}</el-tag>
              <span class="task-no">{{ t.billNo }}</span>
              <span class="task-time">{{ t.createTime?.slice(0, 10) }}</span>
            </div>
            <el-empty v-if="myTasks.length === 0" description="暂无待办" :image-size="40" />
          </div>
          <!-- 主管：超时预警 -->
          <div v-else-if="w.key === 'timeoutAlert'" class="alert-list">
            <div v-for="a in alerts" :key="a.id" class="alert-item">
              <el-tag size="small" type="danger">{{ docTypeLabel(a.billType) }}</el-tag>
              <span>{{ a.billNo }}</span>
              <span class="alert-stay">超时 {{ a.stayHours }}h</span>
            </div>
            <el-empty v-if="alerts.length === 0" description="无超时预警" :image-size="40" />
          </div>
          <!-- 总监：经营驾驶舱 -->
          <div v-else-if="w.key === 'kpiBoard'" class="mini-board">
            <div class="mini-stat">
              <span class="mini-label">本月报销</span>
              <span class="mini-val">{{ monthExp }}</span>
            </div>
            <div class="mini-stat">
              <span class="mini-label">本月付款</span>
              <span class="mini-val">{{ monthPay }}</span>
            </div>
            <div class="mini-stat">
              <span class="mini-label">预算执行率</span>
              <span class="mini-val">{{ budgetRate }}%</span>
            </div>
          </div>
          <!-- 月结进度 -->
          <div v-else-if="w.key === 'monthEnd'" class="month-end">
            <el-progress :percentage="monthProgress" :color="monthProgress >= 80 ? '#67c23a' : '#e6a23c'" />
            <span class="month-text">{{ monthProgress }}% 完成</span>
          </div>
          <!-- 流程监控 -->
          <div v-else-if="w.key === 'flowMonitor'" class="flow-mini">
            <div class="mini-stat">
              <span class="mini-label">在途</span>
              <span class="mini-val text-blue">{{ flowStats.inTransit }}</span>
            </div>
            <div class="mini-stat">
              <span class="mini-label">超时</span>
              <span class="mini-val text-red">{{ flowStats.timeout }}</span>
            </div>
            <el-button v-if="canFlowMonitor" type="primary" link size="small" @click="$router.push('/flow-monitor')">查看大屏</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * （处置＝下架入口、内容暂不修）：
 * 本页 KPI 中 monthExp/monthPay/monthProgress 无赋值点、inTransit/timeout/budgetRate 查询键与后端返回不符，
 * 另有 done/avgTime/timeoutRate/sysHealth 四项为写死的展示值（10-01 已办结＝改接
 * /wf/perf/overview 与 /api/health 两条真实读数，取不到显示「—」，见下方 kpiCards/kpiNote）。
 * 且「当前角色」由本页单选按钮自行切换，与登录人真实角色无关。
 * 可修，但等于重做：八项指标要各自接一条查询环节（全局规则在 /wf/perf/overview、预算在 budget/execution、
 * 月结在 month-close），角色模型还要改绑登录人真实角色；而修好后独有内容与 /dashboard、/flow-monitor 重叠。
 * ⇒ 先摘用户下拉入口，路由与组件保留（直接访问 /role-dashboard 仍可用），重做需求另立。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { store, wfApi, dashApi, flowApi, healthApi } from '../../api'
import { hasPerm } from '@/utils/perm'
import { docTypeLabel } from '@/constants/enums.js'

const router = useRouter()
const currentRole = ref('OPERATOR')
const myTasks = ref([])
const alerts = ref([])
const monthExp = ref(0)
const monthPay = ref(0)
const budgetRate = ref(0)
const monthProgress = ref(0)
const flowStats = ref({ inTransit: 0, timeout: 0 })
// 原先 done/avgTime/timeoutRate/sysHealth 四项是写死在 valMap 里的展示值
// 0／'12分'／'5%'／'正常'，无论谁登录都长一个样。现改接两条真实读数：
//  · 流程绩效 overview（GET /tenant/wf/perf/overview，WfPerfService.overview）——
//    finishedToday／avgNodeMinutes／slaRate+slaMeasured 三个键在同一个响应里，已在 FlowMonitor／FlowCenter 消费；
//  · 后端探活（GET /api/health，免鉴权，RunStatus.vue:91 同款用法）——status==='UP' 才算正常。
// 取不到就显示「—」：绩效端点带 wf:instance:list 权限校验，普通岗拿不到时不弹错、更不拿 0 冒充真实计数。
const perf = ref(null)
const sysHealth = ref('')
// 查询窗口与界面规则标签同源，改这里即改文案，不会出现"写着本周、算的是 30 天"
const PERF_WINDOW_DAYS = 30

/**
 * 外跳入口的权限门控
 * 「查看大屏」/ KPI 钻取的目标页（/flow-monitor、/month-end）是无菜单承载的全局入口页，
 * 有独立的接口权限码。无权限时给出提示而非把用户送进一个必然报错的页面。
 */
const canFlowMonitor = hasPerm('wf:instance:list')
const canMonthEnd = hasPerm('gl:monthclose:list')

const roleConfig = {
  OPERATOR: {
    kpis: [
      { label: '我的待办', key: 'todo', icon: '', cls: 'blue' },
      { label: '今日完成', key: 'done', icon: '', cls: 'green' },
      { label: '平均耗时', key: 'avgTime', icon: '', cls: 'yellow' },
      { label: '超时率', key: 'timeoutRate', icon: '', cls: 'red' },
    ],
    widgets: [{ key: 'myTasks', title: '我的待办任务' }],
  },
  SUPERVISOR: {
    kpis: [
      { label: '团队待办', key: 'todo', icon: '', cls: 'blue' },
      { label: '今日完成', key: 'done', icon: '', cls: 'green' },
      { label: '超时预警', key: 'timeout', icon: '', cls: 'red' },
      { label: '平均耗时', key: 'avgTime', icon: '', cls: 'yellow' },
    ],
    widgets: [
      { key: 'timeoutAlert', title: '超时预警' },
      { key: 'myTasks', title: '团队任务' },
    ],
  },
  CFO: {
    kpis: [
      { label: '本月报销', key: 'monthExp', icon: '', cls: 'blue' },
      { label: '本月付款', key: 'monthPay', icon: '', cls: 'green' },
      { label: '预算执行', key: 'budgetRate', icon: '', cls: 'yellow' },
      { label: '月结进度', key: 'monthProgress', icon: '', cls: 'blue' },
    ],
    widgets: [
      { key: 'kpiBoard', title: '经营驾驶舱' },
      { key: 'monthEnd', title: '月结进度' },
    ],
  },
  ADMIN: {
    kpis: [
      { label: '在途流程', key: 'inTransit', icon: '', cls: 'blue' },
      { label: '今日完成', key: 'done', icon: '', cls: 'green' },
      { label: '超时流程', key: 'timeout', icon: '', cls: 'red' },
      { label: '系统状态', key: 'sysHealth', icon: '', cls: 'green' },
    ],
    widgets: [{ key: 'flowMonitor', title: '流程监控' }],
  },
}

/**
 * 规则标签与规则说明只挂在"确实按这条规则查询"的卡上——
 * 财务总监那一组四枚卡不来自流程绩效，套同一句规则会变成假说明。
 */
const PERF_KPI_KEYS = ['done', 'avgTime', 'timeoutRate', 'sysHealth']
const hasPerfKpi = computed(() =>
  (roleConfig[currentRole.value]?.kpis || []).some((k) => PERF_KPI_KEYS.includes(k.key)))

/**
 * 三项绩效读数：全部来自 /wf/perf/overview 的同一份响应，缺任一键即回「—」（不补 0）。
 * 超时率＝未按节点 SLA 完成的占比，与 slaRate（达成率）互补，且只在"可测"时给数：
 * slaMeasured=0 表示没有任何事件配了 SLA，此时"0%"是假绿，故显示「—」。
 */
const perfDone = computed(() => {
  const p = perf.value
  return p && p.finishedToday != null ? Number(p.finishedToday) : '—'
})
const perfAvg = computed(() => {
  const p = perf.value
  if (!p || p.approvalEvents == null || Number(p.approvalEvents) <= 0) return '—'
  return `${Math.round(Number(p.avgNodeMinutes || 0))}分`
})
const perfTimeout = computed(() => {
  const p = perf.value
  if (!p || !Number(p.slaMeasured || 0) || p.slaRate == null) return '—'
  return `${(Math.round((100 - Number(p.slaRate)) * 10) / 10)}%`
})
const kpiWindowDays = computed(() => Number(perf.value?.days || PERF_WINDOW_DAYS))
/** 规则标签写业务规则，不写"全域/近30天"——权限范围内、不区分人员，时间窗按查询窗口如实标注 */
const kpiScopeText = computed(() => `权限范围内 · 全部人员 · 近 ${kpiWindowDays.value} 天`)
const kpiNote = computed(() => {
  const d = kpiWindowDays.value
  if (!perf.value) {
    return `统计范围：${kpiScopeText.value}。今日完成／平均耗时／超时率取自流程绩效统计，当前账号无权查看，显示「—」；`
      + '系统状态为实时检测结果。'
  }
  const m = Number(perf.value.slaMeasured || 0)
  return `统计范围：${kpiScopeText.value}。今日完成＝今天办结的流程数；`
    + `平均耗时＝近 ${d} 天审批环节的平均停留；超时率＝近 ${d} 天内超过办理时限的占比（可统计 ${m} 条）；`
    + '系统状态为实时检测结果。'
})

const kpiCards = computed(() => {
  const cfg = roleConfig[currentRole.value]
  return cfg.kpis.map(k => {
    const valMap = {
      todo: myTasks.value.length,
      // 以下四项由写死字面量改为真实读数（取不到显示「—」，不再伪造 0/'12分'/'5%'/'正常'）
      done: perfDone.value,
      avgTime: perfAvg.value,
      timeoutRate: perfTimeout.value,
      timeout: alerts.value.length,
      monthExp: '¥' + monthExp.value,
      monthPay: '¥' + monthPay.value,
      budgetRate: budgetRate.value + '%',
      monthProgress: monthProgress.value + '%',
      inTransit: flowStats.value.inTransit,
      sysHealth: sysHealth.value || '—',
    }
    return { ...k, value: valMap[k.key] ?? '-' }
  })
})

const widgets = computed(() => roleConfig[currentRole.value].widgets)

async function fetchData() {
  try {
    const todo = await wfApi.todo()
    myTasks.value = Array.isArray(todo) ? todo.slice(0, 5) : []
  } catch (e) { ElMessage.error(e.message) }
  try {
    const pay = await dashApi.pay().catch((e) => { ElMessage.error(e.message); return null })
    if (pay) {
      flowStats.value.inTransit = pay.inTransit || 0
      flowStats.value.timeout = pay.timeout || 0
    }
  } catch (e) { ElMessage.error(e.message) }
  try {
    const budget = await dashApi.budget().catch((e) => { ElMessage.error(e.message); return null })
    if (budget) budgetRate.value = budget.executionRate || 0
  } catch (e) { ElMessage.error(e.message) }
  // 绩效三卡静默查询——无 wf:instance:list 的岗位会得到 4030/403，这里不弹全局错误、
  // 也不把失败当成"0 件"，perf 保持 null 由 kpiCards 渲染「—」。
  try {
    perf.value = await flowApi.perfOverview(PERF_WINDOW_DAYS, { silent: true })
  } catch (e) { perf.value = null }
  // 系统状态用免鉴权探活端点，判据与 RunStatus.vue:91-96 同源：/api/health 在 DOWN 或非 0 码时抛错，
  // 抛错＝后端不可达或探活 DOWN ⇒ 同样记「异常」，不写死「正常」。
  try {
    const h = await healthApi.get()
    sysHealth.value = h && h.status === 'UP' ? '正常' : '异常'
  } catch (e) { sysHealth.value = '异常' }
}

function onRoleChange() {
  if (currentRole.value === 'SUPERVISOR') {
    alerts.value = myTasks.value.filter(t => t.timeout).slice(0, 5)
  }
}

// KPI 钻取
function drillKpi(kpi) {
  const drillRoutes = {
    todo: '/approval',
    done: '/approval',
    avgTime: '/approval',
    timeoutRate: '/approval',
    timeout: '/approval',
    monthExp: '/dashboard',
    monthPay: '/pay',
    budgetRate: '/budget/adjust',
    monthProgress: '/month-end',
    inTransit: '/flow-monitor',
    sysHealth: '/system/run-status',
  }
  const route = drillRoutes[kpi.key]
  // 无菜单承载的外跳页按各自权限码收敛：无权限给提示，不跳进必然报错的页面
  if (route === '/flow-monitor' && !canFlowMonitor) {
    ElMessage.warning('当前账号没有查看流程监控大屏的权限，请联系管理员开通')
    return
  }
  if (route === '/month-end' && !canMonthEnd) {
    ElMessage.warning('当前账号没有查看月结看板的权限，请联系管理员开通')
    return
  }
  if (route) router.push(route)
}

onMounted(() => { fetchData() })
</script>

<style scoped>
.role-dashboard { padding: 16px; }
.role-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.role-label { font-size: 14px; color: var(--app-text-secondary, #606266); }
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.kpi-card {
  display: flex; align-items: center; gap: 12px;
  background: var(--app-card-bg, #fff); border-radius: 10px; padding: 16px;
  border-left: 4px solid #dcdfe6;
}
.kpi-card.blue { border-color: #409eff; }
.kpi-card.green { border-color: #67c23a; }
.kpi-card.yellow { border-color: #e6a23c; }
.kpi-card.red { border-color: #f56c6c; }
.kpi-icon { font-size: 24px; }
.kpi-val { font-size: 22px; font-weight: 700; }
.kpi-label { font-size: 12px; color: #909399; }
.widget-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.kpi-scope { margin-bottom: 8px; font-size: 12px; color: var(--app-text-secondary, #606266); }
.role-scope-note { font-size: 12px; color: #e6a23c; }
.kpi-note { margin: -8px 0 16px; font-size: 12px; color: #909399; line-height: 1.5; }
.widget-card { background: var(--app-card-bg, #fff); border-radius: 10px; padding: 16px; border: 1px solid var(--app-border, #ebeef5); }
.widget-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
.task-list, .alert-list { display: flex; flex-direction: column; gap: 8px; }
.task-item, .alert-item {
  display: flex; align-items: center; gap: 8px; font-size: 13px;
  padding: 6px 8px; border-radius: 6px; cursor: pointer;
}
.task-item:hover { background: var(--app-hover, #f5f7fa); }
.task-no { flex: 1; }
.task-time { font-size: 12px; color: #909399; }
.alert-stay { color: #f56c6c; font-weight: 600; }
.mini-board, .flow-mini { display: flex; gap: 20px; align-items: center; }
.mini-stat { display: flex; flex-direction: column; }
.mini-label { font-size: 12px; color: #909399; }
.mini-val { font-size: 18px; font-weight: 600; }
.text-blue { color: #409eff; }
.text-red { color: #f56c6c; }
.month-end { display: flex; align-items: center; gap: 12px; }
.month-text { font-size: 13px; color: var(--app-text-secondary, #606266); }
</style>
