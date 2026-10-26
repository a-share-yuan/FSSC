<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="pc-wrap">
    <!-- 顶栏（平台端独立于租户 Layout：/api/tenant/** 对 PLATFORM Token 封闭，菜单树不可用） -->
    <div class="pc-header">
      <span class="pc-logo">FSSC 平台运营控制台</span>
      <el-tabs v-model="tab" class="pc-tabs" @tab-change="onTabChange">
        <el-tab-pane label="平台看板" name="dash" />
        <el-tab-pane label="租户管理" name="tenant" />
        <el-tab-pane label="套餐功能开关" name="feature" />
        <el-tab-pane label="数据导出" name="export" />
      </el-tabs>
      <el-dropdown @command="onCommand">
        <span class="pc-user">{{ user?.realName || user?.username || '平台管理员' }}</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <div class="pc-main">
      <!-- ================= 平台看板 ================= -->
      <div v-show="tab === 'dash'">
        <el-row :gutter="12">
          <el-col v-for="c in dashCards" :key="c.label" :xs="12" :sm="8" :md="6" :lg="3">
            <el-card shadow="hover" class="pc-stat">
              <div class="pc-stat-num" :style="{ color: c.color }">{{ c.value }}</div>
              <div class="pc-stat-label">{{ c.label }}</div>
            </el-card>
          </el-col>
        </el-row>
        <el-card header="近 7 天运营趋势（活跃租户数 / 单据增量）" style="margin-top: 12px">
          <SkeletonDetail v-if="dashLoading" :rows="6" />
          <div ref="dashChart" class="pc-chart" v-else />
        </el-card>
      </div>

      <!-- ================= 租户管理 ================= -->
      <el-card v-show="tab === 'tenant'">
        <template #header>
          <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
            <b>租户管理</b>
            <el-input v-model="tq.keyword" placeholder="名称/编码" clearable style="width: 200px" @keyup.enter="loadTenants(1)" />
            <el-select v-model="tq.status" placeholder="状态" clearable style="width: 140px">
              <el-option v-for="(v, k) in TENANT_STATUS" :key="k" :label="v.label" :value="k" />
            </el-select>
            <el-button type="primary" size="small" @click="loadTenants(1)">查询</el-button>
            <span style="color: #909399; font-size: 13px">平台只碰 pf_tenant，操作全部落 pf_oper_log 审计</span>
          </div>
        </template>
        <SkeletonList v-if="tenantLoading" :rows="8" :cols="5" />
        <el-table :data="tenants" size="small" stripe v-else max-height="520" @row-dblclick="openRowDetail">
          <el-table-column prop="tenantName" label="企业名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="tenantCode" label="企业编码" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="TENANT_STATUS[row.status]?.type || 'info'" size="small">
                {{ TENANT_STATUS[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="生效时间" width="160"><template #default="{ row }">{{ fmt(row.effectTime) }}</template></el-table-column>
          <el-table-column label="到期时间" width="160"><template #default="{ row }">{{ fmt(row.expireTime) }}</template></el-table-column>
          <el-table-column prop="contactName" label="联系人" width="100" show-overflow-tooltip />
          <el-table-column prop="contactPhone" label="联系电话" width="130" />
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'APPLYING'">
                <el-button link type="success" size="small" @click="doApprove(row)">通过申请</el-button>
                <el-button link type="danger" size="small" @click="doReject(row)">拒绝</el-button>
              </template>
              <template v-else-if="row.status === 'NORMAL'">
                <el-button link type="warning" size="small" @click="doFreeze(row)">冻结</el-button>
              </template>
              <template v-else-if="row.status === 'FROZEN'">
                <el-button link type="success" size="small" @click="doUnfreeze(row)">解冻</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="pc-pager" background layout="total, sizes, prev, pager, next"
          :total="tenantTotal" v-model:current-page="tq.pageNum" v-model:page-size="tq.pageSize"
          :page-sizes="[10, 20, 50]" @change="loadTenants()" />
      </el-card>

      <!-- ================= 套餐功能开关 ================= -->
      <el-card v-show="tab === 'feature'">
        <template #header>
          <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
            <b>套餐功能开关</b>
            <el-select v-model="fq.tenantId" placeholder="选择租户（审计记录）" clearable filterable style="width: 220px">
              <el-option v-for="t in tenantsAll" :key="t.id" :label="t.tenantName" :value="t.id" />
            </el-select>
            <el-select v-model="fq.packageId" placeholder="选择套餐" style="width: 200px" @change="loadMatrix">
              <el-option v-for="p in packages" :key="p.id" :label="p.packageName" :value="p.id" />
            </el-select>
            <el-button type="primary" size="small" :disabled="!matrix.length" :loading="fSaveLoading" @click="saveFeatures">
              保存到套餐
            </el-button>
            <span style="color: #909399; font-size: 13px">开关按套餐统一生效，用户登录后自动应用所属套餐的功能配置</span>
          </div>
        </template>
        <el-empty v-if="!matrix.length" description="请先选择套餐加载功能矩阵" />
        <SkeletonDetail v-if="fLoading" :rows="6" />
        <div v-else>
          <div v-for="grp in matrixGroups" :key="grp.module" class="pc-fgroup">
            <div class="pc-fmodule">{{ grp.module || '未分组' }}</div>
            <el-row :gutter="12">
              <el-col v-for="f in grp.items" :key="f.code" :xs="24" :sm="12" :md="8" :lg="6">
                <div class="pc-fitem">
                  <span class="pc-fname" :title="f.code">{{ f.name }}</span>
                  <el-switch v-model="f.on" />
                </div>
              </el-col>
            </el-row>
          </div>
        </div>
      </el-card>

      <!-- ================= 数据导出 ================= -->
      <el-card v-show="tab === 'export'">
        <template #header><b>租户数据导出（ZIP）</b></template>
        <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
          <el-select v-model="eq.tenantId" placeholder="选择租户" filterable style="width: 260px">
            <el-option v-for="t in tenantsAll" :key="t.id" :label="`${t.tenantName}（${t.tenantCode}）`" :value="t.id" />
          </el-select>
          <el-button type="primary" :loading="exportLoading" :disabled="!eq.tenantId" @click="doExport">
            导出数据包
          </el-button>
          <span style="color: #909399; font-size: 13px">GET /api/platform/tenant/{id}/export：核心业务表逐表 CSV 打包，数据主权迁出场景</span>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime, showRowDetail } from '@/utils/format'
import SkeletonList from '../../components/SkeletonList.vue'
import SkeletonDetail from '../../components/SkeletonDetail.vue'
// echarts 按需引入，用法与 views/Dashboard.vue 保持一致
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { store, logout, platformApi } from '../../api'

echarts.use([LineChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const router = useRouter()
const user = store.user
const tab = ref('dash')

const TENANT_STATUS = {
  APPLYING: { label: '待审核', type: 'warning' },
  NORMAL: { label: '正常', type: 'success' },
  FROZEN: { label: '已冻结', type: 'danger' },
  CANCELLING: { label: '注销中', type: 'info' },
  CANCELLED: { label: '已注销', type: 'info' },
  REJECTED: { label: '已拒绝', type: 'danger' },
}

/** LocalDateTime 序列化兼容：统一走全站 formatDateTime（YYYY-MM-DD HH:mm） */
function fmt(v) {
  if (!v) return '-'
  return formatDateTime(v) || '-'
}

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

// ---------------- 平台看板 ----------------
const dash = ref({})
const dashLoading = ref(false)
const dashChart = ref()
let dashInstance = null
const dashCards = computed(() => [
  { label: '租户总数', value: dash.value.totalTenants ?? '-', color: '#1f3b8f' },
  { label: '正常', value: dash.value.normalTenants ?? '-', color: '#67c23a' },
  { label: '冻结', value: dash.value.frozenTenants ?? '-', color: '#f56c6c' },
  { label: '30天到期', value: dash.value.expiringSoon ?? '-', color: '#e6a23c' },
  { label: '活跃租户(7天)', value: dash.value.activeTenants7d ?? '-', color: '#409eff' },
  { label: '今日操作量', value: dash.value.todayOperCount ?? '-', color: '#1f3b8f' },
  { label: '在途流程', value: dash.value.runningInstances ?? '-', color: '#9b59b6' },
])

async function loadDashboard() {
  dashLoading.value = true
  try {
    dash.value = await platformApi.dashboard()
    await nextTick()
    renderDashChart(dash.value.daily7d || [])
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    dashLoading.value = false
  }
}

function renderDashChart(rows) {
  if (!dashChart.value) return
  if (!dashInstance) dashInstance = echarts.init(dashChart.value)
  dashInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['活跃租户数', '单据增量'] },
    grid: { left: 40, right: 16, top: 36, bottom: 28 },
    xAxis: { type: 'category', data: rows.map((r) => String(r.date).slice(5)), axisLabel: { color: '#333' }, axisLine: { lineStyle: { color: '#e4e7ed' } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#333' }, splitLine: { lineStyle: { color: '#e4e7ed', opacity: 0.4 } } },
    series: [
      { name: '活跃租户数', type: 'line', smooth: true, data: rows.map((r) => Number(r.activeTenants || 0)), itemStyle: { color: '#409eff' } },
      { name: '单据增量', type: 'line', smooth: true, data: rows.map((r) => Number(r.billDelta || 0)), itemStyle: { color: '#67c23a' } },
    ],
  })
}

// ---------------- 租户管理 ----------------
const tenants = ref([])
const tenantsAll = ref([])
const tenantTotal = ref(0)
const tenantLoading = ref(false)
const tq = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: '' })

async function loadTenants(page) {
  if (page) tq.pageNum = page
  tenantLoading.value = true
  try {
    const r = await platformApi.tenantPage({ pageNum: tq.pageNum, pageSize: tq.pageSize, keyword: tq.keyword || undefined, status: tq.status || undefined })
    tenants.value = r.list || []
    tenantTotal.value = r.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    tenantLoading.value = false
  }
}

/** 下拉选项用：一次拉足够多的租户 */
async function loadTenantsAll() {
  if (tenantsAll.value.length) return
  try {
    const r = await platformApi.tenantPage({ pageNum: 1, pageSize: 500 })
    tenantsAll.value = r.list || []
  } catch (e) {
    console.warn('租户列表加载失败', e.message)
  }
}

async function confirmAction(title, message) {
  return ElMessageBox.confirm(message, title, { type: 'warning' }).then(() => true).catch(() => false)
}

async function doApprove(row) {
  if (!await confirmAction('审核通过', `确认通过租户「${row.tenantName}」的入驻申请？`)) return
  try {
    await platformApi.tenantApprove(row.id)
    ElMessage.success('已通过申请')
    loadTenants()
  } catch (e) { ElMessage.error(e.message) }
}

async function doReject(row) {
  try {
    const { value } = await ElMessageBox.prompt(`拒绝租户「${row.tenantName}」入驻申请，请填写原因`, '拒绝申请', { inputPlaceholder: '拒绝原因' })
    await platformApi.tenantReject(row.id, value)
    ElMessage.success('已拒绝')
    loadTenants()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

async function doFreeze(row) {
  try {
    const { value } = await ElMessageBox.prompt(`冻结租户「${row.tenantName}」，请填写原因`, '冻结租户', { inputPlaceholder: '冻结原因' })
    await platformApi.tenantFreeze(row.id, value)
    ElMessage.success('已冻结')
    loadTenants()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

async function doUnfreeze(row) {
  if (!await confirmAction('解冻租户', `确认解冻租户「${row.tenantName}」？`)) return
  try {
    await platformApi.tenantUnfreeze(row.id)
    ElMessage.success('已解冻')
    loadTenants()
  } catch (e) { ElMessage.error(e.message) }
}

// ---------------- 套餐功能开关 ----------------
const packages = ref([])
const matrix = ref([])
const fLoading = ref(false)
const fSaveLoading = ref(false)
const fq = reactive({ tenantId: null, packageId: null })
const matrixGroups = computed(() => {
  const map = new Map()
  for (const f of matrix.value) {
    const key = f.module || '未分组'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(f)
  }
  return [...map.entries()].map(([module, items]) => ({ module, items }))
})

async function loadPackages() {
  if (packages.value.length) return
  try {
    packages.value = await platformApi.packageList() || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadMatrix(packageId) {
  if (!packageId) { matrix.value = []; return }
  fLoading.value = true
  try {
    const rows = await platformApi.packageFeatures(packageId) || []
    matrix.value = rows.map((r) => ({ ...r, on: Number(r.enabled) === 1 }))
  } catch (e) {
    matrix.value = []
    ElMessage.error(e.message)
  } finally {
    fLoading.value = false
  }
}

async function saveFeatures() {
  fSaveLoading.value = true
  try {
    const affected = await platformApi.setFeatures({
      tenantId: fq.tenantId || undefined,
      packageId: fq.packageId,
      features: matrix.value.map((f) => ({ code: f.code, enabled: f.on ? 1 : 0 })),
    })
    ElMessage.success(`已保存 ${affected} 个功能开关`)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    fSaveLoading.value = false
  }
}

// ---------------- 数据导出（fetch + Blob，仿 Dashboard.vue exportXlsx） ----------------
const eq = reactive({ tenantId: null })
const exportLoading = ref(false)

async function doExport() {
  exportLoading.value = true
  try {
    const resp = await fetch(platformApi.exportUrl(eq.tenantId), { headers: { Authorization: 'Bearer ' + store.token } })
    if (!resp.ok) throw new Error('导出失败 HTTP ' + resp.status)
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `tenant-${eq.tenantId}-export-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.zip`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出已开始')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    exportLoading.value = false
  }
}

// ---------------- 页签/生命周期 ----------------
function onTabChange(name) {
  if (name === 'dash') nextTick(() => { if (dashInstance) dashInstance.resize(); else if (dash.value.totalTenants == null) loadDashboard() })
  if (name === 'tenant') loadTenants()
  if (name === 'feature') { loadTenantsAll(); loadPackages() }
  if (name === 'export') loadTenantsAll()
}

function onResize() { dashInstance?.resize() }

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  dashInstance?.dispose()
  dashInstance = null
})

async function onCommand(cmd) {
  if (cmd === 'logout') {
    try { await logout() } catch (e) { /* 忽略后端退出失败，前端仍清理 */ }
    store.logout()
    router.push('/platform/login')
  }
}
</script>

<style scoped>
.pc-wrap { height: 100%; display: flex; flex-direction: column; background: #f0f2f5; }
.pc-header {
  display: flex; align-items: center; gap: 16px; background: #1f2d3d; padding: 0 16px; height: 56px; flex-shrink: 0;
}
.pc-logo { color: #fff; font-weight: 700; font-size: 16px; white-space: nowrap; }
.pc-tabs { flex: 1; }
.pc-tabs :deep(.el-tabs__header) { margin: 0; border: none; }
.pc-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.pc-tabs :deep(.el-tabs__item) { color: #bfcbd9; height: 56px; line-height: 56px; }
.pc-tabs :deep(.el-tabs__item.is-active) { color: #409eff; }
.pc-tabs :deep(.el-tabs__active-bar) { background: #409eff; }
.pc-user { cursor: pointer; color: #409eff; white-space: nowrap; }
.pc-main { flex: 1; overflow: auto; padding: 16px; }
.pc-stat { text-align: center; margin-bottom: 12px; }
.pc-stat-num { font-size: 26px; font-weight: 700; }
.pc-stat-label { font-size: 12px; color: #8a94a6; margin-top: 4px; }
.pc-chart { height: 300px; }
.pc-pager { margin-top: 12px; justify-content: flex-end; }
.pc-fgroup { margin-bottom: 8px; }
.pc-fmodule { font-weight: 600; color: #1f3b8f; margin: 10px 0 6px; border-left: 3px solid #409eff; padding-left: 8px; }
.pc-fitem {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  border: 1px solid #e3e8ef; border-radius: 8px; padding: 8px 12px; margin-bottom: 8px; background: #fafbfc;
}
.pc-fname { font-size: 13px; color: #1f2733; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
