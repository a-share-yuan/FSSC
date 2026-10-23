<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>
          {{ $t('flow.close.descApBoard') }}<el-tag size="small" type="warning" style="margin-left:8px">{{ $t('flow.close.stRunning', { v1: rows.filter(r => !['CLOSED', 'VOID'].includes(r.status)).length }) }}</el-tag>
          <el-tag size="small" type="danger" style="margin-left:4px">{{ $t('flow.close.collabFreezing', { v1: rows.filter(r => r.scpFrozen === 1 && !['CLOSED', 'VOID'].includes(r.status)).length }) }}</el-tag>
        </span>
        <div class="ops">
          <el-select v-model="q.companyId" size="small" style="width: 150px" :placeholder="$t('field.org')" clearable filterable @change="page.pageNum = 1">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-input v-model="q.period" size="small" style="width: 120px" :placeholder="$t('flow.close.colPeriodYm')" clearable @keyup.enter="page.pageNum = 1" />
          <el-select v-model="q.status" size="small" style="width: 120px" :placeholder="$t('common.status')" clearable @change="page.pageNum = 1">
            <el-option v-for="(v, k) in AP_MC_STATUS" :key="k" :label="v" :value="k" />
          </el-select>
          <el-button size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          <el-button v-permission="'apflow:close:create'" type="primary" size="small" @click="openInit">{{ $t('flow.close.btnStartClose') }}</el-button>
        </div>
      </div>
    </template>

    <el-table :data="pagedRows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="closeNo" :label="$t('flow.close.colDocNo')" min-width="180" show-overflow-tooltip />
      <el-table-column :label="$t('field.org')" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ companyName(row.companyId) }}</template>
      </el-table-column>
      <el-table-column prop="period" :label="$t('field.period')" width="90" />
      <el-table-column :label="$t('common.status')" width="105">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'CLOSED' ? 'success' : (row.status === 'VOID' ? 'info' : 'primary')">{{ apMcStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('flow.close.collabFreeze')" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.scpFrozen === 1 && row.status !== 'CLOSED' ? 'danger' : 'info'">{{ row.scpFrozen === 1 && row.status !== 'CLOSED' ? t('flow.close.stFrozenDone') : t('flow.close.stRestored') }}</el-tag>
        </template>
      </el-table-column>
      <!-- R5-D-212：人员存工号，展示层映射花名（历史昵称数据原值回显） -->
      <el-table-column :label="$t('field.leader')" width="95" show-overflow-tooltip>
        <template #default="{ row }">{{ personLabel(row.groupLeader) }}</template>
      </el-table-column>
      <el-table-column :label="$t('field.member')" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ personsLabel(row.members) }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.close.colCheckProgress')" width="85">
        <template #default="{ row }">{{ progress(row) }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.close.colCloseTime')" width="155">
        <template #default="{ row }">
          <div>{{ row.closedTime ? formatDateTime(row.closedTime) : '—' }}</div>
          <div v-if="row.reopenTime" class="reopen-time">{{ $t('flow.close.btnReopenN', { v1: formatDateTime(row.reopenTime) }) }}</div>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="270" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'INIT'" v-permission="'apflow:close:dispatch'" link type="primary" size="small" @click="openDispatch(row)">{{ $t('flow.close.dlgDispatch') }}</el-button>
          <el-button v-if="!['INIT', 'CLOSED', 'VOID'].includes(row.status)" v-permission="'apflow:close:item'" link type="primary" size="small" @click="openItems(row)">{{ $t('flow.close.maintainChecks') }}</el-button>
          <el-button v-if="row.status === 'CHECKING'" v-permission="'apflow:close:close'" link type="success" size="small" @click="doClose(row)">{{ $t('flow.close.btnGlClose') }}</el-button>
          <el-button v-if="row.status === 'CLOSED'" v-permission="'apflow:close:reopen'" link type="danger" size="small" @click="openReopen(row)">{{ $t('flow.close.btnReopen') }}</el-button>
          <!-- R5-D-218：未关账的关账单可作废（终态 VOID，行止不删；已关账须走反关账） -->
          <el-button v-if="!['CLOSED', 'VOID'].includes(row.status)" v-permission="'apflow:close:reopen'" link type="warning" size="small" @click="openVoid(row)">{{ $t('field.void') }}</el-button>
          <el-button link type="info" size="small" @click="openTrace(row)">{{ $t('field.trace') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page.pageNum" v-model:size="page.pageSize" :total="filteredRows.length" />

    <!-- 发起关账：组织必选 + 组长选人组件（P0-1：废弃 companyId=1 硬编码与纯文本组长） -->
    <el-dialog v-model="initVisible" :title="$t('flow.close.btnStartDlg')" width="460px" destroy-on-close>
      <el-form ref="initFormRef" :model="form" :rules="initRules" label-width="90px" size="small">
        <el-form-item :label="$t('flow.close.labelOrg')" prop="companyId">
          <el-select v-model="form.companyId" filterable :placeholder="$t('flow.close.phPickOrg')" style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('field.period')" prop="period"><el-date-picker v-model="form.period" type="month" value-format="YYYY-MM" style="width: 100%" /></el-form-item>
        <el-form-item :label="$t('field.leader')">
          <UserSelect v-model="form.leaderId" @change="(id, u) => onLeaderChange(id, u)" :placeholder="$t('flow.close.phPickLeader')" />
        </el-form-item>
        <el-form-item :label="$t('common.remark')"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div class="tip">{{ $t('flow.close.hintStart') }}</div>
      <template #footer>
        <el-button size="small" @click="initVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doInit">{{ $t('flow.close.btnStart') }}</el-button>
      </template>
    </el-dialog>

    <!-- 组长派工：人员多选（P0-1：废弃 window.prompt） -->
    <el-dialog v-model="dispatchVisible" :title="$t('flow.close.dlgDispatch')" width="440px" destroy-on-close>
      <el-form ref="dispatchFormRef" :model="dispatchModel" :rules="dispatchRules" label-width="90px" size="small">
        <el-form-item :label="$t('flow.close.colDoc')">{{ dispatchRow?.closeNo }}</el-form-item>
        <el-form-item :label="$t('field.member')" prop="memberIds">
          <el-select v-model="memberIds" multiple filterable :placeholder="$t('flow.close.phPickMembers')" style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.nickName || u.userName" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dispatchVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doDispatch">{{ $t('field.dispatch') }}</el-button>
      </template>
    </el-dialog>

    <!-- 检查项维护：支持 通过/退回/挂起 + 处理说明 + 前置自动检查（P0-2/P1-6） -->
    <el-dialog v-model="itemsVisible" :title="$t('flow.close.colChecks')" width="860px" destroy-on-close>
      <div class="items-ops">
        <el-button size="small" type="primary" plain :loading="autoChecking" @click="runAutoCheck">{{ $t('flow.close.btnRunAutoCheck') }}</el-button>
        <span class="auto-tip">{{ $t('flow.close.hintAutoCheck') }}</span>
      </div>
      <el-table :data="items" size="small" border>
        <el-table-column prop="name" :label="$t('field.checkItem')" min-width="200" />
        <el-table-column :label="$t('flow.close.colAutoCheck')" min-width="150">
          <template #default="{ row }">
            <template v-if="row.auto">
              <el-tag size="small" :type="row.auto.ok ? 'success' : 'danger'">{{ row.auto.metric }} {{ row.auto.count }}</el-tag>
            </template>
            <span v-else class="muted">{{ $t('flow.close.stNotRun') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="ITEM_ST[row.status]?.tag || 'warning'">{{ ITEM_ST[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('field.handleNote')" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.note" size="small" :placeholder="$t('flow.close.colHandleProof')" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('field.handler')" width="150">
          <template #default="{ row }">
            <!-- 处理人从「本单组长＋组员＋我自己」里选：检查项留痕要的是在岗的人，手输姓名会写出档案里不存在的人 -->
            <el-select v-model="row.handler" size="small" filterable clearable :placeholder="me" style="width: 100%">
              <el-option v-for="h in handlerOptions" :key="h" :label="personLabel(h)" :value="h" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'OK'" link type="success" size="small" @click="doItem(row, 'OK')">{{ $t('field.approve') }}</el-button>
            <el-button v-if="row.status !== 'REJECTED'" link type="danger" size="small" @click="doItem(row, 'REJECTED')">{{ $t('field.returnBack') }}</el-button>
            <el-button v-if="row.status !== 'SUSPENDED'" link type="warning" size="small" @click="doItem(row, 'SUSPENDED')">{{ $t('field.hold') }}</el-button>
            <el-button v-if="row.status !== 'PENDING'" link type="primary" size="small" @click="doItem(row, 'PENDING')">{{ $t('field.reopen') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="tip">{{ $t('flow.close.hintChecks') }}</div>
    </el-dialog>

    <!-- 反关账：原因必填（P0-2） -->
    <el-dialog v-model="reopenVisible" :title="$t('flow.close.btnReopenFull')" width="440px" destroy-on-close>
      <el-alert type="warning" :closable="false" style="margin-bottom: 10px"
        :title="$t('flow.close.dlgDocTitle', { v1: reopenRow?.closeNo, v2: reopenRow?.period })"
        :description="$t('flow.close.hintReopen')" />
      <el-input v-model="reopenReason" type="textarea" :rows="3" :placeholder="$t('flow.close.phReopenReason')" />
      <template #footer>
        <el-button size="small" @click="reopenVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" size="small" @click="doReopen">{{ $t('flow.close.btnConfirmReopen') }}</el-button>
      </template>
    </el-dialog>

    <!-- R5-D-218 作废：原因必填留痕（终态 VOID，行止不删；解冻协同） -->
    <el-dialog v-model="voidVisible" :title="$t('flow.close.btnVoid')" width="440px" destroy-on-close>
      <el-alert type="error" :closable="false" style="margin-bottom: 10px"
        :title="$t('flow.close.dlgDocTitle', { v1: voidRow?.closeNo, v2: voidRow?.period })"
        :description="$t('flow.close.voidIrreversible')" />
      <el-input v-model="voidReason" type="textarea" :rows="3" :placeholder="$t('flow.close.phVoidReason')" />
      <template #footer>
        <el-button size="small" @click="voidVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="warning" size="small" @click="doVoid">{{ $t('flow.close.btnConfirmVoid') }}</el-button>
      </template>
    </el-dialog>

    <!-- 全程操作留痕（P0-2）/ 关账报告导出（P2-10） -->
    <el-drawer v-model="traceVisible" size="460px" destroy-on-close>
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center">
          <b>{{ $t('flow.close.trailTitle') }}</b>
          <el-button size="small" type="primary" plain @click="exportReport">{{ $t('flow.close.btnExportReport') }}</el-button>
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
      <el-empty v-else :description="$t('flow.close.emptyTrail')" :image-size="60" />
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
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const ITEM_ST = computed(() => ({
  OK: { label: t('field.passTag'), tag: 'success' },
  PENDING: { label: t('flow.close.stPending'), tag: 'warning' },
  REJECTED: { label: t('flow.close.stReturned'), tag: 'danger' },
  SUSPENDED: { label: t('flow.close.stHeld'), tag: 'info' },
}))
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
const initRules = computed(() => ({
  companyId: commonRules.select(t('flow.close.msgPickCloseOrg')),
  period: commonRules.date(t('flow.close.msgPickClosePeriod')),
}))
const memberIds = ref([])
const dispatchFormRef = ref(null)
// 组员是独立的 ref，用只读视图包一层给 el-form 当 model，红星/闸门就能和其它页同源
const dispatchModel = computed(() => ({ memberIds: memberIds.value }))
const dispatchRules = computed(() => ({ memberIds: commonRules.multiSelect(t('flow.close.msgPickMember')) }))
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
  return c ? c.name : (id != null ? t('flow.close.companyLabel', { v1: id }) : '—')
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

// 检查项处理人候选：本单组长 + 组员 + 当前登录人（去重，全部按工号入库）
const handlerOptions = computed(() => {
  const set = new Set()
  const row = current.value || {}
  if (row.groupLeader) set.add(String(row.groupLeader).trim())
  String(row.members || '').split(',').forEach((s) => s.trim() && set.add(s.trim()))
  if (me) set.add(me)
  return [...set].filter(Boolean)
})

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
  // P0 修复（2026-10-21）：月结负责人通常为本财务人员，按同公司过滤
  try { users.value = (await userApi.page({ pageNum: 1, pageSize: 200, companyId: store.user?.companyId })).list || [] } catch { users.value = [] }
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
  ElMessage.success(t('flow.close.msgStarted'))
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
  ElMessage.success(t('flow.close.msgDispatched'))
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
    ElMessage.success(t('flow.close.msgAutoCheck') + ret.detail)
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { autoChecking.value = false }
}

async function doItem(item, status) {
  if (status === 'OK' && item.auto && item.auto.ok === false && !String(item.note || '').trim()) {
    return ElMessage.warning(t('flow.close.errOverrideNote'))
  }
  try {
    await apFlowApi.monthCloseItem(current.value.id, {
      code: item.code,
      itemStatus: status,
      note: item.note || '',
      handler: item.handler || me,
    })
    ElMessage.success(t('field.updatedTag'))
    await load()
    const fresh = rows.value.find((r) => r.id === current.value.id)
    if (fresh) {
      try { items.value = JSON.parse(fresh.checkItemsJson || '[]') } catch { items.value = [] }
    }
  } catch (e) { ElMessage.error(e.message) }
}

// ---- 关账 / 反关账 ----
async function doClose(row) {
  await ElMessageBox.confirm(t('flow.close.askClosePeriod', { v1: row.period, v2: companyName(row.companyId) }), t('flow.close.btnGlClose'), { type: 'warning' })
  await apFlowApi.monthCloseClose(row.id)
  ElMessage.success(t('flow.close.msgClosed'))
  await load()
}

function openReopen(row) {
  reopenRow.value = row
  reopenReason.value = ''
  reopenVisible.value = true
}

async function doReopen() {
  if (!reopenReason.value.trim()) return ElMessage.warning(t('flow.close.errReopenReason'))
  await apFlowApi.monthCloseReopen(reopenRow.value.id, reopenReason.value.trim())
  ElMessage.success(t('flow.close.msgReopened'))
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
  if (!voidReason.value.trim()) return ElMessage.warning(t('flow.close.errVoidReason'))
  await apFlowApi.monthCloseVoid(voidRow.value.id, voidReason.value.trim())
  ElMessage.success(t('flow.close.msgVoided'))
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
  if (!row) return ElMessage.warning(t('flow.close.errPickDoc'))
  let items = []
  try { items = JSON.parse(row.checkItemsJson || '[]') } catch { items = [] }
  const out = []
  const info = [
    [t('flow.close.colDocNo'), row.closeNo], [t('field.org'), companyName(row.companyId)], [t('field.period'), row.period],
    [t('common.status'), apMcStatusLabel(row.status)], [t('field.leader'), personLabel(row.groupLeader)], [t('field.member'), personsLabel(row.members)],
    [t('flow.close.collabFreeze'), row.scpFrozen === 1 && row.status !== 'CLOSED' ? t('flow.close.stFrozen') : t('flow.close.stRestored')],
    [t('flow.report.colCloseTime'), row.closedTime || '—'], [t('flow.close.colClosedBy'), personLabel(row.closedBy)],
    [t('flow.close.colReopenTime'), row.reopenTime || '—'], [t('flow.close.colReopenReason'), row.reopenReason || '—'], [t('common.remark'), row.remark || '—'],
  ]
  info.forEach(([k, v]) => out.push({ sec: t('flow.close.secInfo'), item: k, result: String(v), handler: '', time: '', note: '' }))
  out.push({ sec: t('flow.close.secCheck'), item: '', result: '', handler: '', time: '', note: '' })
  for (const it of items) {
    const auto = it.auto ? `${it.auto.metric}=${it.auto.count}${it.auto.ok ? t('flow.close.normalSuffix') : t('flow.close.abnormalSuffix')}` : ''
    out.push({
      sec: '', item: it.name, result: ITEM_ST.value[it.status]?.label || it.status,
      handler: it.handler || '', time: formatDateTime(it.time), note: [it.note, auto].filter(Boolean).join(' | '),
    })
  }
  out.push({ sec: t('flow.close.secTrail'), item: '', result: '', handler: '', time: '', note: '' })
  for (const t of traceList.value) {
    out.push({ sec: '', item: apMcTraceLabel(t.action), result: '', handler: t.oper || '', time: formatDateTime(t.time), note: t.detail || '' })
  }
  exportCsv(out, [
    { prop: 'sec', label: t('flow.close.secPara') }, { prop: 'item', label: t('flow.close.colProject') }, { prop: 'result', label: t('flow.close.colProof') },
    { prop: 'handler', label: t('field.handler') }, { prop: 'time', label: t('field.time') }, { prop: 'note', label: t('flow.close.colDescProof') },
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
