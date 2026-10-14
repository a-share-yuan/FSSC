<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>
          AP 月末关账（发起冻结协同 → 组长派工 → 检查项签认 → 关账/反关账；会计期间由总账月结结账）
          <el-tag size="small" type="warning" style="margin-left:8px">进行中 {{ rows.filter(r => !['CLOSED', 'VOID'].includes(r.status)).length }}</el-tag>
          <el-tag size="small" type="danger" style="margin-left:4px">协同冻结中 {{ rows.filter(r => r.scpFrozen === 1 && !['CLOSED', 'VOID'].includes(r.status)).length }}</el-tag>
        </span>
        <div class="ops">
          <el-select v-model="q.companyId" size="small" style="width: 150px" placeholder="组织" clearable filterable @change="page.pageNum = 1">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-input v-model="q.period" size="small" style="width: 120px" placeholder="期间 yyyy-MM" clearable @keyup.enter="page.pageNum = 1" />
          <el-select v-model="q.status" size="small" style="width: 120px" placeholder="状态" clearable @change="page.pageNum = 1">
            <el-option v-for="(v, k) in AP_MC_STATUS" :key="k" :label="v" :value="k" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button type="primary" size="small" @click="openInit">发起关账</el-button>
        </div>
      </div>
    </template>

    <el-table :data="pagedRows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="closeNo" label="关账单号" min-width="180" show-overflow-tooltip />
      <el-table-column label="组织" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ companyName(row.companyId) }}</template>
      </el-table-column>
      <el-table-column prop="period" label="期间" width="90" />
      <el-table-column label="状态" width="105">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'CLOSED' ? 'success' : (row.status === 'VOID' ? 'info' : 'primary')">{{ apMcStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="协同冻结" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.scpFrozen === 1 && row.status !== 'CLOSED' ? 'danger' : 'info'">{{ row.scpFrozen === 1 && row.status !== 'CLOSED' ? '已冻结' : '已恢复' }}</el-tag>
        </template>
      </el-table-column>
      <!-- R5-D-212：人员存工号，展示层映射花名（历史昵称数据原值回显） -->
      <el-table-column label="组长" width="95" show-overflow-tooltip>
        <template #default="{ row }">{{ personLabel(row.groupLeader) }}</template>
      </el-table-column>
      <el-table-column label="组员" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ personsLabel(row.members) }}</template>
      </el-table-column>
      <el-table-column label="检查进度" width="85">
        <template #default="{ row }">{{ progress(row) }}</template>
      </el-table-column>
      <el-table-column label="关账/反关账时间" width="155">
        <template #default="{ row }">
          <div>{{ row.closedTime ? formatDateTime(row.closedTime) : '—' }}</div>
          <div v-if="row.reopenTime" class="reopen-time">反关账 {{ formatDateTime(row.reopenTime) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'INIT'" link type="primary" size="small" @click="openDispatch(row)">组长派工</el-button>
          <el-button v-if="!['INIT', 'CLOSED', 'VOID'].includes(row.status)" link type="primary" size="small" @click="openItems(row)">检查项维护</el-button>
          <el-button v-if="row.status === 'CHECKING'" link type="success" size="small" @click="doClose(row)">总账关账</el-button>
          <el-button v-if="row.status === 'CLOSED'" v-permission="'apflow:close:reopen'" link type="danger" size="small" @click="openReopen(row)">反关账</el-button>
          <!-- R5-D-218：未关账的关账单可作废（终态 VOID，行止不删；已关账须走反关账） -->
          <el-button v-if="!['CLOSED', 'VOID'].includes(row.status)" v-permission="'apflow:close:reopen'" link type="warning" size="small" @click="openVoid(row)">作废</el-button>
          <el-button link type="info" size="small" @click="openTrace(row)">轨迹</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page.pageNum" v-model:size="page.pageSize" :total="filteredRows.length" />

    <!-- 发起关账：组织必选 + 组长选人组件（P0-1：废弃 companyId=1 硬编码与纯文本组长） -->
    <el-dialog v-model="initVisible" title="发起月末关账" width="460px" destroy-on-close>
      <el-form ref="initFormRef" :model="form" :rules="initRules" label-width="90px" size="small">
        <el-form-item label="关账组织" prop="companyId">
          <el-select v-model="form.companyId" filterable placeholder="请选择组织（公司）" style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间" prop="period"><el-date-picker v-model="form.period" type="month" value-format="YYYY-MM" style="width: 100%" /></el-form-item>
        <el-form-item label="组长">
          <UserSelect v-model="form.leaderId" @change="(id, u) => onLeaderChange(id, u)" placeholder="选择组长" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div class="tip">发起后协同冻结（付款/对账通道拦截发送凭证），关账完成自动恢复协同交互；会计期间的结账由「总账核算›月结」审批末节点联动。</div>
      <template #footer>
        <el-button size="small" @click="initVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doInit">发起</el-button>
      </template>
    </el-dialog>

    <!-- 组长派工：人员多选（P0-1：废弃 window.prompt） -->
    <el-dialog v-model="dispatchVisible" title="组长派工" width="440px" destroy-on-close>
      <el-form ref="dispatchFormRef" :model="dispatchModel" :rules="dispatchRules" label-width="90px" size="small">
        <el-form-item label="关账单">{{ dispatchRow?.closeNo }}</el-form-item>
        <el-form-item label="组员" prop="memberIds">
          <el-select v-model="memberIds" multiple filterable placeholder="选择组员（可多选）" style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.nickName || u.userName" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doDispatch">派工</el-button>
      </template>
    </el-dialog>

    <!-- 检查项维护：支持 通过/退回/挂起 + 处理说明 + 前置自动检查（P0-2/P1-6） -->
    <el-dialog v-model="itemsVisible" title="关账检查项" width="860px" destroy-on-close>
      <div class="items-ops">
        <el-button size="small" type="primary" plain :loading="autoChecking" @click="runAutoCheck">运行前置自动检查</el-button>
        <span class="auto-tip">自动跑数（未审凭证/未匹配三单/未达账项），人工仅处理异常项</span>
      </div>
      <el-table :data="items" size="small" border>
        <el-table-column prop="name" label="检查项" min-width="200" />
        <el-table-column label="自动检查" min-width="150">
          <template #default="{ row }">
            <template v-if="row.auto">
              <el-tag size="small" :type="row.auto.ok ? 'success' : 'danger'">{{ row.auto.metric }} {{ row.auto.count }}</el-tag>
            </template>
            <span v-else class="muted">未运行</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="ITEM_ST[row.status]?.tag || 'warning'">{{ ITEM_ST[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理说明" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.note" size="small" placeholder="处理说明/凭据索引" />
          </template>
        </el-table-column>
        <el-table-column prop="handler" label="处理人" width="90">
          <template #default="{ row }"><el-input v-model="row.handler" size="small" :placeholder="me" /></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'OK'" link type="success" size="small" @click="doItem(row, 'OK')">通过</el-button>
            <el-button v-if="row.status !== 'REJECTED'" link type="danger" size="small" @click="doItem(row, 'REJECTED')">退回</el-button>
            <el-button v-if="row.status !== 'SUSPENDED'" link type="warning" size="small" @click="doItem(row, 'SUSPENDED')">挂起</el-button>
            <el-button v-if="row.status !== 'PENDING'" link type="primary" size="small" @click="doItem(row, 'PENDING')">重开</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="tip">全部检查项通过后出现「总账关账」入口；退回/挂起将把状态拉回「检查处理中」，关账入口随之关闭。</div>
    </el-dialog>

    <!-- 反关账：原因必填（P0-2） -->
    <el-dialog v-model="reopenVisible" title="反关账（重开期间）" width="440px" destroy-on-close>
      <el-alert type="warning" :closable="false" style="margin-bottom: 10px"
        :title="`关账单 ${reopenRow?.closeNo}（期间 ${reopenRow?.period}）`"
        description="反关账将：协同重新冻结、关账单回到检查处理中（会计期间状态不由本台改动，如需重开请到「总账核算›月结」反结账）。操作全程留痕。" />
      <el-input v-model="reopenReason" type="textarea" :rows="3" placeholder="反关账原因（必填，审计留痕）" />
      <template #footer>
        <el-button size="small" @click="reopenVisible = false">取消</el-button>
        <el-button type="danger" size="small" @click="doReopen">确认反关账</el-button>
      </template>
    </el-dialog>

    <!-- R5-D-218 作废：原因必填留痕（终态 VOID，行止不删；解冻协同） -->
    <el-dialog v-model="voidVisible" title="作废关账单" width="440px" destroy-on-close>
      <el-alert type="error" :closable="false" style="margin-bottom: 10px"
        :title="`关账单 ${voidRow?.closeNo}（期间 ${voidRow?.period}）`"
        description="作废为终态：单据保留不删行、原因入轨迹留痕、协同解冻；已关账的关账单须先反关账，不能直接作废。" />
      <el-input v-model="voidReason" type="textarea" :rows="3" placeholder="作废原因（必填，审计留痕）" />
      <template #footer>
        <el-button size="small" @click="voidVisible = false">取消</el-button>
        <el-button type="warning" size="small" @click="doVoid">确认作废</el-button>
      </template>
    </el-dialog>

    <!-- 全程操作留痕（P0-2）/ 关账报告导出（P2-10） -->
    <el-drawer v-model="traceVisible" size="460px" destroy-on-close>
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center">
          <b>关账操作留痕</b>
          <el-button size="small" type="primary" plain @click="exportReport">导出关账报告</el-button>
        </div>
      </template>
      <el-timeline v-if="traceList.length" style="padding-left: 4px">
        <el-timeline-item v-for="(t, i) in traceList" :key="i" :timestamp="t.time" :type="TRACE_TYPE[t.action] || 'primary'">
          <div class="trace-card">
            <div class="trace-action">{{ apMcTraceLabel(t.action) }}<span class="trace-oper" v-if="t.oper"> · {{ t.oper }}</span></div>
            <div class="trace-detail" v-if="t.detail">{{ t.detail }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无操作留痕" :image-size="60" />
    </el-drawer>
  </el-card>
</template>

<script setup>
import { AP_MC_STATUS, apMcStatusLabel, apMcTraceLabel } from '@/constants/enums'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apFlowApi, orgApi, store, userApi } from '../../api'
import { formatDateTime } from '../../utils/format'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { exportCsv } from '../../utils/csv'
import UserSelect from '../../components/common/UserSelect.vue'
import TablePager from '../../components/common/TablePager.vue'

const ITEM_ST = {
  OK: { label: '已通过', tag: 'success' },
  PENDING: { label: '待处理', tag: 'warning' },
  REJECTED: { label: '已退回', tag: 'danger' },
  SUSPENDED: { label: '已挂起', tag: 'info' },
}
const TRACE_TYPE = { CLOSE: 'success', REOPEN: 'danger', AUTO_CHECK: 'warning' }

const rows = ref([])
const loading = ref(false)
const companies = ref([])
const users = ref([])
// R5-D-212：当前用户以工号（user_name）入库，花名仅展示
const me = store.user?.username || store.user?.realName || ''
const q = reactive({ companyId: null, period: '', status: '' })
const page = reactive({ pageNum: 1, pageSize: 10 })

const initVisible = ref(false)
const dispatchVisible = ref(false)
const itemsVisible = ref(false)
const reopenVisible = ref(false)
const traceVisible = ref(false)
const current = ref(null)
const dispatchRow = ref(null)
const reopenRow = ref(null)
const form = reactive({ companyId: null, period: '', leaderId: null, leaderName: '', remark: '' })
const initFormRef = ref(null)
const initRules = {
  companyId: commonRules.select('请选择关账组织'),
  period: commonRules.date('请选择关账期间'),
}
const memberIds = ref([])
const dispatchFormRef = ref(null)
// 组员是独立的 ref，用只读视图包一层给 el-form 当 model，红星/闸门就能和其它页同源
const dispatchModel = computed(() => ({ memberIds: memberIds.value }))
const dispatchRules = { memberIds: commonRules.multiSelect('请选择组员') }
const items = ref([])
const autoChecking = ref(false)
const reopenReason = ref('')
const voidVisible = ref(false)
const voidRow = ref(null)
const voidReason = ref('')
const traceList = ref([])

const filteredRows = computed(() => rows.value.filter((r) =>
  (!q.companyId || r.companyId === q.companyId)
  && (!q.period || (r.period || '').includes(q.period))
  && (!q.status || r.status === q.status)
))
const pagedRows = computed(() => filteredRows.value.slice((page.pageNum - 1) * page.pageSize, page.pageNum * page.pageSize))

function companyName(id) {
  const c = companies.value.find((x) => x.id === id)
  return c ? c.name : (id != null ? `公司${id}` : '—')
}

// R5-D-212：库里存工号→显示花名；历史遗留的昵称/花名按原值回显（找不到不改写）
function personLabel(name) {
  if (!name) return '—'
  const u = users.value.find((x) => x.userName === name)
  return (u && (u.nickName || u.userName)) || name
}
function personsLabel(names) {
  if (!names) return '—'
  return String(names).split(',').map((s) => s.trim()).filter(Boolean).map(personLabel).join('、')
}

function progress(row) {
  try {
    const list = JSON.parse(row.checkItemsJson || '[]')
    return `${list.filter((i) => i.status === 'OK').length}/${list.length}`
  } catch { return '-' }
}

async function load() {
  loading.value = true
  try { rows.value = await apFlowApi.monthCloseList() } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadCompanies() {
  try {
    const flat = []
    const walk = (arr) => { (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
    walk(await orgApi.companyTree())
    companies.value = flat
  } catch { /* 组织树加载失败不阻断列表 */ }
}

async function loadUsers() {
  try { users.value = (await userApi.page({ pageNum: 1, pageSize: 200 })).list || [] } catch { users.value = [] }
}

// ---- 发起 ----
function openInit() {
  form.companyId = null
  form.period = ''
  form.leaderId = null
  form.leaderName = ''
  form.remark = ''
  initVisible.value = true
}

function onLeaderChange(id, u) {
  // R5-D-212：存工号（user_name），昵称仅供下拉展示
  form.leaderName = u ? (u.userName || u.nickName || '') : ''
}

async function doInit() {
  if (!(await validateForms([initFormRef]))) return
  await apFlowApi.monthCloseCreate({
    companyId: form.companyId,
    period: form.period,
    groupLeader: form.leaderName || me,
    remark: form.remark,
  })
  ElMessage.success('已发起并冻结协同')
  initVisible.value = false
  await load()
}

// ---- 派工 ----
function openDispatch(row) {
  dispatchRow.value = row
  // R5-D-212：优先按工号回填（历史昵称/花名数据兜底按昵称匹配）
  memberIds.value = (row.members || '').split(',').map((s) => s.trim()).filter(Boolean)
    .map((name) => (users.value.find((u) => u.userName === name) || users.value.find((u) => u.nickName === name))?.id)
    .filter((id) => id != null)
  dispatchVisible.value = true
}

async function doDispatch() {
  if (!(await validateForms([dispatchFormRef]))) return
  // R5-D-212：派工名单存工号（user_name），展示层再映射花名
  const names = memberIds.value.map((id) => {
    const u = users.value.find((x) => x.id === id)
    return u ? (u.userName || u.nickName) : String(id)
  })
  await apFlowApi.monthCloseDispatch(dispatchRow.value.id, names.join(','))
  ElMessage.success('派工成功')
  dispatchVisible.value = false
  await load()
}

// ---- 检查项 ----
function openItems(row) {
  current.value = row
  try { items.value = JSON.parse(row.checkItemsJson || '[]') } catch { items.value = [] }
  itemsVisible.value = true
}

async function runAutoCheck() {
  autoChecking.value = true
  try {
    const ret = await apFlowApi.monthCloseAutoCheck(current.value.id)
    items.value = ret.items || []
    ElMessage.success('自动检查完成：' + ret.detail)
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { autoChecking.value = false }
}

async function doItem(item, status) {
  try {
    await apFlowApi.monthCloseItem(current.value.id, {
      code: item.code,
      itemStatus: status,
      note: item.note || '',
      handler: item.handler || me,
    })
    ElMessage.success('已更新')
    await load()
    const fresh = rows.value.find((r) => r.id === current.value.id)
    if (fresh) {
      try { items.value = JSON.parse(fresh.checkItemsJson || '[]') } catch { items.value = [] }
    }
  } catch (e) { ElMessage.error(e.message) }
}

// ---- 关账 / 反关账 ----
async function doClose(row) {
  await ElMessageBox.confirm(`确认对期间 ${row.period}（${companyName(row.companyId)}）执行关账？关账后协同恢复，会计期间的结账由「总账核算›月结」审批末节点联动。`, '总账关账', { type: 'warning' })
  await apFlowApi.monthCloseClose(row.id)
  ElMessage.success('关账完成，协同已恢复（会计期间由总账月结结账）')
  await load()
}

function openReopen(row) {
  reopenRow.value = row
  reopenReason.value = ''
  reopenVisible.value = true
}

async function doReopen() {
  if (!reopenReason.value.trim()) return ElMessage.warning('反关账原因必填（审计留痕）')
  await apFlowApi.monthCloseReopen(reopenRow.value.id, reopenReason.value.trim())
  ElMessage.success('反关账完成：期间已重开、协同重新冻结')
  reopenVisible.value = false
  await load()
}

// ---- 作废（R5-D-218 终态 VOID） ----
function openVoid(row) {
  voidRow.value = row
  voidReason.value = ''
  voidVisible.value = true
}

async function doVoid() {
  if (!voidReason.value.trim()) return ElMessage.warning('作废原因必填（审计留痕）')
  await apFlowApi.monthCloseVoid(voidRow.value.id, voidReason.value.trim())
  ElMessage.success('关账单已作废（协同已解冻，留痕不删行）')
  voidVisible.value = false
  await load()
}

// ---- 轨迹 ----
function openTrace(row) {
  current.value = row
  try { traceList.value = JSON.parse(row.traceJson || '[]') } catch { traceList.value = [] }
  traceVisible.value = true
}

// P2-10 关账报告导出:关账信息 + 检查项结论(含自动检查) + 操作轨迹 三段式 CSV
function exportReport() {
  const row = current.value
  if (!row) return ElMessage.warning('请先选择关账单')
  let items = []
  try { items = JSON.parse(row.checkItemsJson || '[]') } catch { items = [] }
  const out = []
  const info = [
    ['关账单号', row.closeNo], ['组织', companyName(row.companyId)], ['期间', row.period],
    ['状态', apMcStatusLabel(row.status)], ['组长', personLabel(row.groupLeader)], ['组员', personsLabel(row.members)],
    ['协同冻结', row.scpFrozen === 1 && row.status !== 'CLOSED' ? '冻结中' : '已恢复'],
    ['关账时间', row.closedTime || '—'], ['关账人', personLabel(row.closedBy)],
    ['反关账时间', row.reopenTime || '—'], ['反关账原因', row.reopenReason || '—'], ['备注', row.remark || '—'],
  ]
  info.forEach(([k, v]) => out.push({ sec: '一、关账信息', item: k, result: String(v), handler: '', time: '', note: '' }))
  out.push({ sec: '二、检查项结论', item: '', result: '', handler: '', time: '', note: '' })
  for (const it of items) {
    const auto = it.auto ? `${it.auto.metric}=${it.auto.count}${it.auto.ok ? '(正常)' : '(异常)'}` : ''
    out.push({
      sec: '', item: it.name, result: ITEM_ST[it.status]?.label || it.status,
      handler: it.handler || '', time: (it.time || '').replace('T', ' ').slice(0, 16), note: [it.note, auto].filter(Boolean).join(' | '),
    })
  }
  out.push({ sec: '三、操作轨迹', item: '', result: '', handler: '', time: '', note: '' })
  for (const t of traceList.value) {
    out.push({ sec: '', item: apMcTraceLabel(t.action), result: '', handler: t.oper || '', time: (t.time || '').replace('T', ' ').slice(0, 16), note: t.detail || '' })
  }
  exportCsv(out, [
    { prop: 'sec', label: '段落' }, { prop: 'item', label: '项目/检查项' }, { prop: 'result', label: '结论/内容' },
    { prop: 'handler', label: '处理人' }, { prop: 'time', label: '时间' }, { prop: 'note', label: '说明/凭据' },
  ], `CloseReport_${row.closeNo}`)
}

onMounted(() => { load(); loadCompanies(); loadUsers() })

defineExpose({ rows })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
.ops { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.tip { font-size: 12px; color: #e6a23c; margin-top: 6px; }
.items-ops { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.auto-tip { font-size: 12px; color: #909399; }
.muted { color: #c0c4cc; font-size: 12px; }
.reopen-time { color: #f56c6c; font-size: 12px; }
.trace-card { font-size: 13px; }
.trace-action { font-weight: 600; }
.trace-oper { color: #409eff; font-weight: 400; }
.trace-detail { color: #606266; font-size: 12px; margin-top: 2px; }
</style>
