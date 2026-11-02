<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div :class="{ tabless: isSingle }">
    <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>{{ isSingle ? (tab === 'inst' ? '流程实例监控' : '流程定义') : '审批流看板' }}</b>
<el-button type="info" size="small" style="margin-left:auto" @click="refreshAll">{{ $t('common.refresh') }}</el-button>
      <el-button type="info" size="small" @click="exportCsv">{{ $t('common.export') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
      </div>
    </template>

    <!-- 顶部统计 -->
    <el-row :gutter="16" style="margin-bottom:12px">
      <el-col v-if="!isSingle" :span="4">
        <el-card shadow="hover"><el-statistic title="已上线流程定义" :value="defs.length" /></el-card>
      </el-col>
      <!-- 实例统计改走服务端聚合口；查询中/失败时显示「-」而非 0，避免空数被读成真实读数 -->
      <el-col :span="4">
        <el-card shadow="hover">
          <el-statistic v-if="!instStatsNoData" title="实例总数" :value="instStats.total" />
          <div v-else class="st-fallback"><span class="st-title">实例总数</span><span class="st-dash">-</span></div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <el-statistic v-if="!instStatsNoData" title="运行中" :value="instStats.running" />
          <div v-else class="st-fallback"><span class="st-title">运行中</span><span class="st-dash">-</span></div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <el-statistic v-if="!instStatsNoData" title="已完成" :value="instStats.completed" />
          <div v-else class="st-fallback"><span class="st-title">已完成</span><span class="st-dash">-</span></div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <el-statistic v-if="!instStatsNoData" title="已驳回/终止" :value="instStats.rejected" />
          <div v-else class="st-fallback"><span class="st-title">已驳回/终止</span><span class="st-dash">-</span></div>
        </el-card>
      </el-col>
      <el-col v-if="!isSingle" :span="4">
        <el-card shadow="hover"><el-statistic title="分支定义" :value="branchCount" /></el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="tab" @tab-change="() => { page = 1; load() }">
      <!-- 页签一：流程定义 -->
      <el-tab-pane v-if="hasPerm('wf:model:list')" label="流程定义" name="def">
        <el-table :data="defs" size="small" border stripe v-loading="loading" @row-click="onPick" @selection-change="handleSelectionChange" style="cursor:pointer">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="flowCode" label="流程编码" width="150"  sortable />
          <el-table-column prop="flowName" label="流程名称" min-width="200" show-overflow-tooltip  sortable />
          <el-table-column prop="bizType" label="业务类型" width="120" sortable>
            <template #default="{ row }"><el-tag size="small" effect="plain">{{ bizTypeLabel(row.bizType) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="modelKey" label="模型Key" width="150" show-overflow-tooltip  sortable />
          <el-table-column label="审批节点数" width="100" align="center">
            <template #default="{ row }"><b>{{ nodeCount(row) }}</b></template>
          </el-table-column>
          <el-table-column label="分支标记" width="130" align="center">
            <template #default="{ row }">
              <template v-if="branchesOf(row).length">
                <el-tag v-for="b in branchesOf(row)" :key="b" type="warning" size="small" style="margin-right:4px">{{ b }}</el-tag>
              </template>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'NORMAL' ? 'success' : 'info'" size="small">{{ defStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip  sortable />
        <template #empty>
          <el-empty description="暂无数据" />
        </template>
        </el-table>

        <!-- 选中流程的 BPMN 模板预览 -->
        <el-card v-if="pick" shadow="never" style="margin-top:12px">
          <template #header>
            <div style="display:flex; gap:10px; align-items:center">
              <b>{{ pick.flowName }}</b>
              <el-tag size="small">{{ bizTypeLabel(pick.bizType) }}</el-tag>
              <el-button v-permission="'wf:bizflow:list'" link type="primary" size="small" @click="loadTemplate(pick)">加载/刷新模板</el-button>
            </div>
          </template>
          <el-alert v-if="templateXml" type="info" :closable="false" style="margin-bottom:8px"
            title="由 WfBpmnTemplateService 生成：userTask 节点 + 排他网关分支（国外出差 abroad / 招待费 ENTERTAIN / 金额阈值）。下方展示提取的节点与网关条件链。">
          </el-alert>
          <div v-if="chain.length" style="display:flex; flex-wrap:wrap; gap:6px; align-items:center; margin-bottom:8px">
            <template v-for="(n, i) in chain" :key="i">
              <el-tag :type="n.type === 'branch' ? 'warning' : (n.type === 'gatewayIn' ? 'primary' : '')" effect="plain">
                {{ n.text }}
              </el-tag>
              <span v-if="i < chain.length - 1" style="color:#c0c4cc">→</span>
            </template>
          </div>
          <el-input v-if="templateXml" type="textarea" :model-value="templateXml"
            :rows="8" readonly placeholder="生成的流程定义（只读）"/>
        </el-card>
      </el-tab-pane>

      <!-- 页签二：流程实例监控 -->
      <el-tab-pane v-if="hasPerm('wf:instance:list')" label="流程实例监控" name="inst">
        <el-row :gutter="12" style="margin-bottom:10px">
          <el-col v-if="defs.length" :span="5">
            <el-select v-model="instBizType" placeholder="业务类型" clearable filterable style="width:100%">
              <el-option v-for="d in defs" :key="d.bizType" :label="bizTypeLabel(d.bizType) + ' ' + d.flowName" :value="d.bizType" />
            </el-select>
          </el-col>
          <el-col :span="5">
            <el-select v-model="instStatus" placeholder="状态" clearable style="width:100%">
              <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-col>
          <el-col :span="7">
            <el-input v-model="keyword" placeholder="单据号/标题" clearable />
          </el-col>
          <el-col :span="3">
            <el-button v-permission="['wf:manage:instance', 'wf:instance:list']" type="primary" size="small" style="width:100%" @click="reloadInst">查询</el-button>
          </el-col>
          <el-col :span="4" style="text-align:right; color:#909399; font-size:12px">
            当前节点 / 处理人实时可见
          </el-col>
        </el-row>

        <el-table :data="insts" size="small" border stripe v-loading="loading" :max-height="560" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="bizNo" label="单据号" width="160" fixed  sortable />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip  sortable />
          <el-table-column label="业务类型" width="120">
            <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="currentNode" label="当前节点" min-width="160" show-overflow-tooltip  sortable />
          <el-table-column label="处理人" width="140" sortable prop="currentAssigneeNames">
            <template #default="{ row }">{{ row.currentAssigneeNames || row.currentAssignee || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发起时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.startTime) || '—' }}</template>
          </el-table-column>
          <el-table-column label="耗时(分)" width="100" align="right">
            <template #default="{ row }">{{ durMin(row.duration) }}</template>
          </el-table-column>
          <el-table-column prop="procInstId" label="流程实例ID" width="120" show-overflow-tooltip  sortable />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goBiz(row)">查看业务</el-button>
            <el-button v-if="row.status === 'RUNNING'" v-permission="'system:message:send'" link type="warning" size="small" @click="urge(row)">催办</el-button>
            <el-button v-if="row.status === 'RUNNING' && canReassign" link type="primary" size="small" @click="openReassign(row)">改派</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无数据" />
        </template>
        </el-table>
        <el-pagination
          style="margin-top:10px; justify-content:flex-end"
          layout="total, prev, pager, next, jumper"
          :total="instTotal" :page-size="pageSize" :current-page="page"
          @current-change="(p) => { page = p; reloadInst() }"
          @size-change="(s) => { pageSize = s; reloadInst() }" />
      </el-tab-pane>
    </el-tabs>

    <!-- 改派（L2 管理员通道，需 wf:task:reassign）：派给任意真人，原因必填记录 -->
    <el-dialog v-model="reassignVisible" title="改派（流程管理员通道）" width="440px" destroy-on-close>
      <el-form ref="reassignFormRef" :model="reassignForm" :rules="reassignFormRules" label-width="90px" size="small">
        <el-form-item label="单据">{{ reassignRow?.bizNo }}</el-form-item>
        <el-form-item label="当前节点">{{ reassignRow?.currentNode }}（{{ reassignRow?.currentAssigneeNames || reassignRow?.currentAssignee || '候选组' }}）</el-form-item>
        <el-form-item label="新处理人" prop="target">
          <!-- 默认只加载同公司人员，流程管理员改派也须遵循同公司原则 -->
          <UserSelect v-model="reassignForm.target" scope="same-company" placeholder="选择接办人(任意在职真人)" />
        </el-form-item>
        <el-form-item label="改派原因" prop="reason"><el-input v-model="reassignForm.reason" type="textarea" :rows="2" placeholder="必填:如 原审批人离职/换岗/本节点无人可办(将记录)" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="reassignVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="reassignSaving" @click="doReassign">确认改派</el-button>
      </template>
    </el-dialog>
    </el-card>
  </div>
</template>

<script setup>

import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { bizFlowApi, flowApi } from '../../api'
import { statusLabel, statusType } from '@/constants/billStatus.js'
import { bizTypeLabel } from '@/constants/enums.js'
import { useKeyboard } from '@/composables/useKeyboard'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { goBizPage } from '@/utils/bizEntry'
import { formatDateTime } from '@/utils/format'
// MEDIUM 增强:催办 / 改派
import UserSelect from '../../components/common/UserSelect.vue'
import { messageApi, wfApi, store } from '../../api'
import { hasPerm } from '@/utils/perm'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

/**
 * 改派入口是否可见：要 wf:task:reassign 权限点，且排除 admin 账号。
 * 铁律"任何流程节点都不允许 admin 参与"——改派是流程动作的一种（替别人决定谁来办），
 * admin 虽然因 *:*:* 通配过 hasPerm，也不该看到这个入口（后端另有目标侧硬拦，此处是入口侧办结）。
 */
const canReassign = computed(() => hasPerm('wf:task:reassign') && store?.user?.isAdmin !== 1)

const reassignVisible = ref(false)
const reassignRow = ref(null)
const reassignSaving = ref(false)
const reassignFormRef = ref(null)
const reassignForm = ref({ target: null, reason: '' })
// 与后端 WfEngineFacade.reassign 同一规则：接办人 + 记录原因（≥2 字）都是硬性输入
const REASON_MSG = '请填写改派原因（≥2 字，记入审批记录）'
const reassignFormRules = {
  target: commonRules.select('请选择新处理人'),
  reason: [{
    required: true,
    message: REASON_MSG,
    validator: (rule, value, cb) => (!value || String(value).trim().length < 2 ? cb(new Error(REASON_MSG)) : cb()),
  }],
}

/** 催办:给当前所有可办理人发站内信(提醒尽快审批)。办理人由后端按 wf_task 镜像回填。 */
async function urge(row) {
  const ids = (row.currentAssigneeIds || '').split(',').filter(Boolean).map(Number)
  if (!ids.length) return ElMessage.warning(t('approval.approvalFlow.Current'))
  try {
    await Promise.all(ids.map((receiverId) => messageApi.send({
      receiverId,
      msgType: 'URGE',
      title: '【催办】' + (row.title || row.bizNo),
      content: `${t('approval.approvalFlow.Document')} ${row.bizNo} 当前节点「${row.currentNode || ''}」等待处理,请尽快审批。`,
      bizType: row.bizType || '',
      bizId: row.id,
      linkUrl: '',
    })))
    ElMessage.success(`已向 ${row.currentAssigneeNames || ids.length + ' 位'} 发送催办`)
  } catch (e) { ElMessage.error(e.message) }
}

function openReassign(row) {
  reassignRow.value = row
  reassignForm.value = { target: null, reason: '' }
  reassignVisible.value = true
}

/** 改派：L2 管理员通道（wf:task:reassign），原因必填；目标硬校验存在/在职/非admin 在后门面 */
async function doReassign() {
  if (!(await validateForms([reassignFormRef]))) return
  const taskId = reassignRow.value?.currentTaskId
  if (!taskId) return ElMessage.warning(t('approval.approvalFlow.Current2'))
  reassignSaving.value = true
  try {
    await wfApi.reassign({ taskId, targetUserId: reassignForm.value.target, reason: reassignForm.value.reason.trim() })
    ElMessage.success(t('approval.approvalFlow.Reassigned'))
    reassignVisible.value = false
    reloadInst()
  } catch (e) { ElMessage.error(e.message) } finally { reassignSaving.value = false }
}

// 快捷键
useKeyboard({
  'ctrl+r': () => { try { reload() } catch { /* noop */ } },
})

const selRows = ref([])
// P: tab 页拆分——由菜单深链进来时传入 tab，只显示该页并隐藏 tab 导航条（一 TAB = 一菜单）
const props = defineProps({ tab: { type: String, default: '' } })
const isSingle = computed(() => !!props.tab)
const tab = ref(props.tab || 'def')
const defs = ref([])
const loading = ref(false)
const pick = ref(null)
const templateXml = ref('')
const chain = ref([])

const insts = ref([])
const instTotal = ref(0)
const page = ref(1)
const pageSize = ref(20)
const instBizType = ref(null)
const instStatus = ref(null)
const keyword = ref('')

const statusOptions = [
  { label: '运行中', value: 'RUNNING' },
  { label: '已完成', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已撤销', value: 'CANCELLED' },
  { label: '已终止', value: 'TERMINATED' },
]

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function durMin(d) { return d == null ? '—' : Math.round(d / 60000) }
function defStatusLabel(s) {
  return s ? statusLabel(s) : '正常'
}

/** 解析 nodeJson（可能是 JSON 数组字符串或逗号分隔）得到节点数 */
function nodeCount(row) {
  if (!row.nodeJson) return 0
  try {
    const arr = JSON.parse(row.nodeJson)
    if (Array.isArray(arr)) return arr.length
  } catch { /* fallthrough */ }
  return String(row.nodeJson).split(',').filter((s) => s.trim()).length
}

/** 从节点文本/对象里挑出分支关键字（国外出差 / 招待费 / 金额阈值） */
function branchesOf(row) {
  const raw = row.nodeJson || ''
  const out = []
  ;['国外出差', '招待费', '金额>', 'ENTERTAIN', 'abroad'].forEach((k) => {
    if (raw.includes(k) && !out.includes(k)) out.push(k)
  })
  return out
}

const branchCount = computed(() => defs.value.reduce((n, d) => n + branchesOf(d).length, 0))

/** 从已生成的 BPMN XML 提取 userTask 与排他网关条件，拼成节点链 */
function buildChain(xml) {
  const out = []
  const tasks = [...xml.matchAll(/<userTask[^>]*id="([^"]+)[^>]*name="([^"]*)"/g)]
  const gateways = [...xml.matchAll(/id="([^"]*gateway[^"]*)"[^>]*><\/exclusiveGateway>/g)]
  const conds = [...xml.matchAll(/<conditionExpression[^>]*>([^<]*)<\/conditionExpression>/g)]
  tasks.forEach((m) => out.push({ type: 'task', text: m[2] }))
  conds.forEach((m) => out.push({ type: 'branch', text: '[' + m[1] + ']' }))
  if (!out.length) out.push({ type: 'task', text: t('approval.approvalFlow.Template') })
  return out
}

async function loadTemplate(row) {
  pick.value = row
  try {
    const xml = await bizFlowApi.template(row.bizType)
    templateXml.value = xml
    chain.value = buildChain(xml)
  } catch (e) {
    templateXml.value = ''
    chain.value = []
    ElMessage.error('模板加载失败：' + e.message)
  }
}

function onPick(row) { loadTemplate(row) }

async function loadDefs() {
  loading.value = true
  try {
    defs.value = (await bizFlowApi.types()) || []
    if (!pick.value && defs.value.length) await loadTemplate(defs.value[0])
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function reloadInst() {
  loading.value = true
  try {
    const p = { pageNum: page.value, pageSize: pageSize.value }
    if (instBizType.value) p.bizType = instBizType.value
    if (instStatus.value) p.status = instStatus.value
    if (keyword.value) p.keyword = keyword.value
    const d = await flowApi.instancePage(p)
    insts.value = d.list || []
    instTotal.value = Number(d.total || 0)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

/** 一键跳转到实例对应业务处理页（复用 bizEntry 分发器） */
const router = useRouter()
function goBiz(row) {
  if (!goBizPage(router, row)) {
    ElMessage.warning(t('SearchCommand.main.Current'))
  }
}

// 顶部四卡读数改取服务端<b>全量</b>聚合，不要从「当前页」的 insts 切片现算
// （原先翻页/筛选都会让四卡随页大小漂移，真有在途实例时恒显 0）
const instStats = ref({ total: 0, running: 0, completed: 0, rejected: 0 })
const instStatsPending = ref(true)
const instStatsFailed = ref(false)
const canViewInstance = hasPerm('wf:instance:list')
/** 「确实没有」与「这次没取到」必须可区分：后者渲染 -，绝不渲染 0 */
const instStatsNoData = computed(() => canViewInstance && (instStatsPending.value || instStatsFailed.value))

async function loadInstStats() {
  if (!canViewInstance) {
    instStatsPending.value = false
    return
  }
  try {
    const d = await flowApi.perfInstanceStatus({ silent: true })
    if (d) {
      instStats.value = {
        total: Number(d.total ?? 0),
        running: Number(d.running ?? 0),
        completed: Number(d.completed ?? 0),
        rejected: Number(d.rejected ?? 0),
      }
      instStatsFailed.value = false
    } else {
      instStatsFailed.value = true
    }
  } catch {
    instStatsFailed.value = true
  } finally {
    instStatsPending.value = false
  }
}

async function load() {
  if (tab.value === 'def') await loadDefs()
  else await reloadInst()
}
function refreshAll() { loadDefs(); reloadInst(); loadInstStats() }

onMounted(() => { load(); loadInstStats() })

function exportCsv() {
  if (!defs.value.length) { ElMessage.warning(t('useBill.main.There')); return }
  const cols = [{ prop: 'flowCode', label: t('approval.approvalFlow.Workflow') }, { prop: 'flowName', label: t('approval.approvalFlow.Workflow2') }, { prop: 'bizType', label: t('image.imageManager.Business') }, { prop: 'modelKey', label: t('approval.approvalFlow.Model') }, { prop: 'remark', label: t('common.remark') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = defs.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `ApprovalFlowBoard_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('useBill.main.CSV'))
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('utils.csv.Select')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('utils.csv.Cannot')); return }
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
  a.download = `ApprovalFlowBoard_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`${t('utils.csv.Exported')} ${selRows.value.length} 条记录`)
}

</script>

<style scoped>
/* 一 TAB = 一菜单：从菜单深链进来（tab 命中）时隐藏 tab 导航条，只显示该页 */
.tabless :deep(.el-tabs__header) { display: none; }
/* 统计口查询中/失败时的兜底形态——沿用 el-statistic 的字号与标题样式 */
.st-fallback { display: flex; flex-direction: column; }
.st-fallback .st-title { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.2; margin-bottom: 4px; }
.st-fallback .st-dash { font-size: 22px; color: var(--el-text-color-placeholder); line-height: 1; }
</style>