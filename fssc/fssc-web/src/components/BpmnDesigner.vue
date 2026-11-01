<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="bpmn-designer">
    <!-- 只读提示：无编辑权限时给出明确说明，而不是让用户以为页面坏了 -->
    <el-alert v-if="readonly" type="info" :closable="false" show-icon
              title="只读预览：可拖动或缩放查看流程图。如需修改，请联系系统管理员开通流程建模权限。" />

    <!-- 专业工具栏（参照主流流程设计器） -->
    <div class="bpmn-toolbar" v-if="!readonly">
      <div class="toolbar-group">
        <el-tooltip content="保存流程" placement="bottom">
          <el-button size="small" type="success" :icon="Check" @click="exportXml">保存</el-button>
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-tooltip content="撤销 (Ctrl+Z)" placement="bottom">
          <el-button size="small" :icon="RefreshLeft" :disabled="!canUndo" @click="undo" />
        </el-tooltip>
        <el-tooltip content="重做 (Ctrl+Y)" placement="bottom">
          <el-button size="small" :icon="RefreshRight" :disabled="!canRedo" @click="redo" />
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-tooltip content="放大" placement="bottom">
          <el-button size="small" :icon="ZoomIn" @click="zoomIn" />
        </el-tooltip>
        <div class="zoom-display" @click="resetZoom">
          <span class="zoom-value">{{ zoomPct }}%</span>
        </div>
        <el-tooltip content="缩小" placement="bottom">
          <el-button size="small" :icon="ZoomOut" @click="zoomOut" />
        </el-tooltip>
        <el-tooltip content="重置缩放(100%)" placement="bottom">
          <el-button size="small" :icon="Aim" @click="resetZoom" />
        </el-tooltip>
        <el-tooltip content="适应视图" placement="bottom">
          <el-button size="small" :icon="FullScreen" @click="fitView" />
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-group">
        <el-tooltip content="属性面板" placement="bottom">
          <el-button size="small" :type="showPanel ? 'primary' : 'default'" :icon="Setting" @click="togglePanel">
            {{ showPanel ? '隐藏面板' : '属性面板' }}
          </el-button>
        </el-tooltip>
      </div>
      <el-divider direction="vertical" />
      <div class="toolbar-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>从左侧拖拽节点：开始 / 用户任务(审批) / 网关 / 结束，连线设置流转；点击节点配置审批人、会签、超时</span>
      </div>
    </div>

    <!-- 画布工作区 -->
    <div class="workspace">
      <div ref="canvasRef" class="canvas"></div>
      <div ref="panelRef" class="properties-panel" v-show="showPanel"></div>
    </div>

    <!-- 底部状态栏 -->
    <div class="status-bar">
      <span class="status-item">
        <el-icon><Grid /></el-icon>
        节点 {{ elementCount }}
      </span>
      <span class="status-item">
        <el-icon><ScaleToOriginal /></el-icon>
        缩放 {{ zoomPct }}%
      </span>
      <span class="status-item legend" v-if="!readonly">
        <span class="legend-dot" style="background:#e3f2fd"></span>业务审批
        <span class="legend-dot" style="background:#f3e8ff"></span>共享审批
        <span class="legend-dot" style="background:#eceff1"></span>系统自动
        <span class="legend-dot" style="background:#fff3e0"></span>网关
        <span class="legend-dot" style="background:#e1f5e8"></span>开始/结束
      </span>
    </div>

    <!-- UC03 财务节点快捷配置：选中节点后显示 -->
    <!-- 本面板只维护"节点取人"（→ flowable:candidateGroups/assignee）。DEPT_LEADER/NOT_SELF 后端无消费方，
         体检后摘除；超时与空策略在「流程配置→节点配置」维护
         （wf_node_config.timeout_hours/empty_strategy）。
         会签/顺序会签/票签：由「流程配置→节点配置」的「会签模式」维护
         （wf_node_config.multi_type = ALL/SEQ/VOTE），后端会据此真实消费——
         WfBpmnTemplateService 在此节点生成 BPMN multiInstanceLoopCharacteristics，
         故不在此面板重复提供控件，避免两处配置打架。 -->
    <div v-if="!readonly && selTask" class="finance-config">
      <b>节点配置：{{ selTask.name }}</b>
      <el-select v-model="selApproverType" size="small" style="width: 130px" @change="applyNodeFinance">
        <el-option label="按角色" value="ROLE" />
        <el-option label="指定用户" value="USER" />
        <el-option label="发起人(自审)" value="INITIATOR" />
      </el-select>
      <el-select v-if="selApproverType === 'ROLE'" v-model="selApproverValue" size="small" filterable clearable allow-create default-first-option
                 style="width: 140px" placeholder="选角色或直接输入角色编码" @change="applyNodeFinance">
        <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
      </el-select>
      <el-select v-else-if="selApproverType === 'USER'" v-model="selApproverValue" size="small" filterable remote clearable
                 :remote-method="searchAssignUsers" :loading="userLoading" placeholder="按姓名选择用户"
                 style="width: 180px" @focus="ensureAssignUsers" @change="applyNodeFinance">
        <el-option v-for="u in userOptions" :key="u.userId" :label="userLabel(u)" :value="String(u.userId)" />
      </el-select>
      <el-button size="small" type="primary" @click="applyNodeFinance">应用</el-button>
      <!-- 选完角色即体检：把"某公司 0 人 / 多公司 ≥2 人"这种运行期才炸的问题提到配置时 -->
      <div v-if="selApproverType === 'ROLE'" style="width:100%">
        <RoleHolderHint :role-code="selApproverValue" />
      </div>
    </div>

    <!-- UC06 连线条件快捷配置 -->
    <div v-if="!readonly && selFlow" class="finance-config" style="border-color:#e6a23c; background:#fdf6ec">
      <b>连线条件</b>
      <el-select v-model="condField" size="small" style="width: 105px" placeholder="字段">
        <el-option v-for="(l, v) in flowFields" :key="v" :label="l" :value="v" />
      </el-select>
      <el-select v-model="condOp" size="small" style="width: 72px" placeholder="运算">
        <el-option v-for="op in condOps" :key="op" :label="op" :value="op" />
      </el-select>
      <el-input v-model="condValue" size="small" style="width: 105px" placeholder="值" clearable />
      <el-button size="small" @click="applyCondExpr">生成</el-button>
      <el-divider direction="vertical" style="margin: 0 4px" />
      <el-input v-model="selCondExpr" size="small" style="width: 230px" placeholder="${amount > 5000}" clearable />
      <el-button size="small" type="primary" @click="applyFlowCondition">应用条件</el-button>
      <el-button size="small" @click="clearFlowCondition">清除</el-button>
      <el-switch v-if="selFlowCanDefault" v-model="selFlowDefault" size="small" active-text="默认流"
                 inactive-text="非默认" style="margin-left: 6px" @change="setDefaultFlow" />
    </div>

    <!-- UC12 子流程调用配置 -->
    <div v-if="!readonly && selSubprocess" class="finance-config" style="border-color:#7c4dff; background:#f5f0ff">
      <b>子流程调用：{{ selSubprocess.name }}</b>
      <el-select v-model="subprocessKey" size="small" style="width: 280px" filterable placeholder="选择公共子流程" clearable>
        <el-option v-for="s in subprocessOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-button size="small" type="primary" @click="applySubprocessFinance">应用</el-button>
      <el-button size="small" @click="loadSubprocesses">刷新列表</el-button>
    </div>

    <el-alert v-if="error" type="error" :closable="false" :title="error" style="margin-top: 6px" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ZoomIn, ZoomOut, Aim, FullScreen, Setting, Check,
  RefreshLeft, RefreshRight, InfoFilled, Grid, ScaleToOriginal,
} from '@element-plus/icons-vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
// 只读渲染用 NavigatedViewer（无调色板 / 无建模命令栈）：用于「流程图」页签的只读预览
import BpmnNavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import { BpmnPropertiesPanelModule, BpmnPropertiesProviderModule } from 'bpmn-js-properties-panel'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
// 属性面板官方样式（缺失会导致面板退化为无样式裸 HTML：标签/输入框错位堆叠）
import '@bpmn-io/properties-panel/dist/assets/properties-panel.css'
import zhModule, { DEFAULT_NODE_NAMES } from '../i18n/bpmn-zh'
import fsscPalette from './bpmn-palette'
import { flowApi, roleApi } from '../api'
import RoleHolderHint from './common/RoleHolderHint.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  // 只读模式：仅渲染流程图 + 缩放/适应视图；隐藏调色板、工具栏、属性面板与节点快捷配置条
  //（用于「流程配置」页的「流程图」页签——只有 wf:model:update 才允许编辑绘制）
  readonly: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const canvasRef = ref(null)
const panelRef = ref(null)
const error = ref('')
const showPanel = ref(true)
let modeler = null

// 隐藏容器挂载保护（修复 SVGMatrix non-finite）
// 场景：组件在 el-tab-pane（v-show 隐藏，尺寸 0×0）里挂载并 render。
// 此时 importXML 正常，但 canvas.zoom('fit-viewport') 会算出 NaN 缩放
// （diagram-js: newScale=min(1,0/w,0/h)=0 → outer.width/0=NaN），
// 抛 "Failed to execute 'scale' on 'SVGMatrix': The provided float value is non-finite"。
// 策略：容器无尺寸时挂起 fit（pendingFit），等 ResizeObserver 检测到容器 0→非0（如切换页签）再补做。
let resizeObserver = null
let pendingFit = false

function containerHasSize() {
  const el = canvasRef.value
  return !!(el && el.clientWidth > 0 && el.clientHeight > 0)
}

/** diagram-js 只在 importXML 那一刻读一次容器尺寸，之后 svg 就固死在那个值上。
 *  页签由 0×0 变有尺寸、或「全屏设计」把容器撑大之后不重算，节点会被裁在旧画布外，
 *  既点不中也双击不进标签编辑；fit-viewport 还会按旧的小容器算出 0.1 级的缩放。 */
function syncCanvasSize() {
  if (!modeler) return
  try { modeler.get('canvas').resized() } catch (e) { /* 旧版 diagram-js 无此方法时保持原尺寸 */ }
}

/** 适配视图：带 try/catch 兜底 + 挂起重试，缩放失败绝不拖垮整个渲染结果 */
function tryZoomFit() {
  if (!modeler) return
  if (!containerHasSize()) {
    pendingFit = true
    return
  }
  syncCanvasSize()
  try {
    modeler.get('canvas').zoom('fit-viewport')
    pendingFit = false
  } catch (e) { /* fit 失败保持默认缩放，不影响已渲染的图形 */ }
  updateZoomState()
  updateElementCount()
}

// 缩放与撤销状态
const currentZoom = ref(1)
const canUndo = ref(false)
const canRedo = ref(false)
const elementCount = ref(0)
const zoomPct = computed(() => Math.round(currentZoom.value * 100))

const ZOOM_STEP = 0.2
const ZOOM_MIN = 0.2
const ZOOM_MAX = 4

const EMPTY_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:flowable="http://flowable.org/bpmn"
  id="Definitions_1" targetNamespace="http://fssc">
  <bpmn:process id="Process_1" isExecutable="false">
    <bpmn:startEvent id="StartEvent_1" name="开始" />
    <bpmn:userTask id="Task_1" name="审批节点" />
    <bpmn:endEvent id="EndEvent_1" name="结束" />
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_1" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_1" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_1_di" bpmnElement="Task_1">
        <dc:Bounds x="248" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="408" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="188" y="120" />
        <di:waypoint x="248" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
        <di:waypoint x="348" y="120" />
        <di:waypoint x="408" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

// 缩放控制
function zoomIn() {
  if (!modeler) return
  const canvas = modeler.get('canvas')
  const newZoom = Math.min(ZOOM_MAX, currentZoom.value + ZOOM_STEP)
  canvas.zoom(newZoom)
  updateZoomState()
}

function zoomOut() {
  if (!modeler) return
  const canvas = modeler.get('canvas')
  const newZoom = Math.max(ZOOM_MIN, currentZoom.value - ZOOM_STEP)
  canvas.zoom(newZoom)
  updateZoomState()
}

function resetZoom() {
  if (!modeler) return
  const canvas = modeler.get('canvas')
  canvas.zoom(1)
  updateZoomState()
}

function fitView() {
  if (!modeler) return
  syncCanvasSize()
  // 原实现无兜底：容器尺寸为 0 时 zoom('fit-viewport') 会算出非有限缩放并抛未捕获异常
  try {
    modeler.get('canvas').zoom('fit-viewport')
  } catch (e) { /* 缩放失败保持当前视图，图形本身已渲染 */ }
  updateZoomState()
}

function updateZoomState() {
  if (!modeler) return
  try {
    const canvas = modeler.get('canvas')
    const viewbox = canvas.viewbox()
    currentZoom.value = viewbox.scale || 1
  } catch (e) { /* ignore */ }
}

// 撤销/重做
function undo() {
  if (!modeler) return
  try {
    modeler.get('commandStack').undo()
    updateUndoRedoState()
  } catch (e) { /* ignore */ }
}

function redo() {
  if (!modeler) return
  try {
    modeler.get('commandStack').redo()
    updateUndoRedoState()
  } catch (e) { /* ignore */ }
}

function updateUndoRedoState() {
  if (!modeler) return
  try {
    const cmd = modeler.get('commandStack')
    canUndo.value = cmd.canUndo()
    canRedo.value = cmd.canRedo()
  } catch (e) { /* ignore */ }
}

// 元素计数
function updateElementCount() {
  if (!modeler) return
  try {
    const registry = modeler.get('elementRegistry')
    elementCount.value = registry.getAll().filter(e => e.type !== 'bpmn:SequenceFlow' && e.type !== 'label').length
  } catch (e) { /* ignore */ }
}

async function render(xml) {
  if (!modeler || !canvasRef.value) return
  error.value = ''
  try {
    await modeler.importXML(xml || EMPTY_XML)
    tryZoomFit()
    colorizeNodes()
    upgradeExistingTasks()
    updateZoomState()
    updateElementCount()
  } catch (e) {
    if (xml && /no diagram to display|missing DI|DI/i.test(e.message || '')) {
      try {
        await modeler.importXML(EMPTY_XML)
        tryZoomFit()
        updateZoomState()
        updateElementCount()
        error.value = '原流程图缺少图形位置信息，已载入示例流程，请重新设计后保存'
        return
      } catch (e2) { /* fallthrough */ }
    }
    error.value = '流程图解析失败：' + (e.message || e)
  }
}

// UC03 节点财务属性配置
// 前 11 项是费用报销域的常用角色（保留中文简称，比 sys_role.role_name 更好读）；
// 真实可选项由 loadRoleOptions() 合并 sys_role 全量——只留硬编码会让新增角色在设计器里选不到，
// 只能靠手输编码，配置态与运行态就此分叉。
const roleOptions = ref([
  { label: '部门经理', value: 'DEPT_MANAGER' },
  { label: '分管领导', value: 'DIV_LEADER' },
  { label: '财务审核岗', value: 'FIN_AUDIT' },
  { label: '财务经理', value: 'FIN_MANAGER' },
  { label: '预算管理员', value: 'BUDGET_MANAGER' },
  { label: '资金岗', value: 'FUND_STAFF' },
  { label: '行政核对岗', value: 'ADMIN_CHECK' },
  { label: '培训部门负责人', value: 'TRAIN_MANAGER' },
  { label: '共享初审岗', value: 'SHARING_INITIAL' },
  { label: '共享财务经理', value: 'SHARING_MANAGER' },
  { label: '共享资金岗', value: 'SHARING_FUND' },
  // 取人规则而非授权角色：sys_role 里没有这一条，但 NodeOrgResolver 按部门汇报线解析它
  { label: '直线上级（部门负责人）', value: 'DIRECT_SUPERVISOR' },
])

/** 指定用户候选（归档到 BPMN assignee）：抄送候选人只读清单，按姓名远程检索，界面不手输用户ID */
const userOptions = ref([])
const userLoading = ref(false)
async function searchAssignUsers(keyword) {
  userLoading.value = true
  try { userOptions.value = (await flowApi.ccCandidates({ keyword: keyword || undefined, limit: 200 })) || [] }
  catch { userOptions.value = [] } finally { userLoading.value = false }
}
function userLabel(u) {
  const name = u.name || u.userName || ''
  return u.userName && u.userName !== u.name ? `${name}（${u.userName}）` : name
}
function ensureAssignUsers() { if (!userOptions.value.length) searchAssignUsers('') }

/** 合并 sys_role 全量到下拉（已存在的编码保留上面的中文简称，不覆盖） */
async function loadRoleOptions() {
  try {
    // 无 system:role:list 的流程配置者沿用内置名单，不在页面里弹「缺少权限」
    const res = await roleApi.page({ pageNum: 1, pageSize: 500 }, { silent: true })
    const rows = (res && (res.records || res.list)) || []
    const merged = roleOptions.value.slice()
    for (const r of rows) {
      const code = r && (r.roleCode || r.code)
      if (!code || merged.some((o) => o.value === code)) continue
      merged.push({ value: code, label: r.roleName || r.name || code })
    }
    roleOptions.value = merged
  } catch (e) {
    // 拉不到角色表时维持内置名单，下拉仍可 allow-create 手输编码，不阻断建模
  }
}
const selTask = ref(null)
const selApproverType = ref('ROLE')
const selApproverValue = ref('')

// UC06 连线条件配置
const flowFields = { amount: '单据金额', expenseType: '费用类型', overBudget: '是否超标', billType: '单据类型', deptId: '部门', companyId: '公司' }
const condOps = ['>', '>=', '<', '<=', '==', '!=']
const selFlow = ref(null)
const selCondExpr = ref('')
const condField = ref('amount')
const condOp = ref('>')
const condValue = ref('')
const selFlowDefault = ref(false)
const selFlowCanDefault = ref(false)

// UC12 子流程调用配置
const selSubprocess = ref(null)
const subprocessKey = ref('')
const subprocessOptions = ref([])

async function loadSubprocesses() {
  try {
    const d = await flowApi.modelPage({ pageNum: 1, pageSize: 200 })
    const subs = (d?.list || []).filter((m) => m.source === 'SUBPROCESS' || m.category === 'SUBPROCESS')
    subprocessOptions.value = subs.map((m) => ({ label: (m.modelName || '') + '（' + m.modelKey + '）', value: m.modelKey }))
  } catch (e) { /* 静默 */ }
}

function applySubprocessFinance() {
  if (!selSubprocess.value) return
  try {
    if (!subprocessKey.value) { ElMessage.warning('请选择公共子流程'); return }
    const element = modeler.get('elementRegistry').get(selSubprocess.value.id)
    modeler.get('modeling').updateProperties(element, { calledElement: subprocessKey.value })
    ElMessage.success('已引用子流程「' + subprocessKey.value + '」，保存后随流程生效')
    colorizeNodes()
  } catch (e) { ElMessage.error('配置失败：' + e.message) }
}

function onSelectionChange(evt) {
  const el = evt && evt.newSelection && evt.newSelection[0]
  if (!el || !(el.businessObject)) { selTask.value = null; selFlow.value = null; selSubprocess.value = null; return }
  const bo = el.businessObject
  if (bo.$type === 'bpmn:CallActivity') {
    selTask.value = null; selFlow.value = null
    selSubprocess.value = { id: el.id, name: bo.name || el.id }
    subprocessKey.value = bo.calledElement || ''
    loadSubprocesses()
    return
  }
  if (bo.$type === 'bpmn:SequenceFlow') {
    selTask.value = null
    selSubprocess.value = null
    selFlow.value = { id: el.id }
    const ce = bo.conditionExpression
    selCondExpr.value = (ce && ce.body) ? ce.body : ''
    parseCondExpr(selCondExpr.value)
    const srcBo = el.source && el.source.businessObject
    selFlowCanDefault.value = !!(srcBo && (srcBo.$type === 'bpmn:ExclusiveGateway' || srcBo.$type === 'bpmn:InclusiveGateway'))
    selFlowDefault.value = selFlowCanDefault.value && !!srcBo.default && srcBo.default.id === bo.id
    return
  }
  // 系统节点（ServiceTask 等）保留着色但不再提供配置面板：flowable:systemAction 无后端消费方，摘除
  const isSystem = bo.$type === 'bpmn:ServiceTask' || bo.$type === 'bpmn:ScriptTask' || bo.$type === 'bpmn:BusinessRuleTask'
  const isTask = bo.$type && bo.$type.indexOf('Task') >= 0 && !isSystem
  selFlow.value = null
  selSubprocess.value = null
  selTask.value = isTask ? { id: el.id, name: bo.name || el.id } : null
  if (isTask) {
    const attrs = bo.$attrs || {}
    selApproverType.value = attrs['flowable:approverType'] || (attrs['flowable:candidateGroups'] ? 'ROLE' : attrs['flowable:assignee'] ? 'USER' : 'ROLE')
    selApproverValue.value = attrs['flowable:candidateGroups'] || (attrs['flowable:assignee'] || '').replace('${', '').replace('}', '')
    if (selApproverType.value !== 'ROLE' && selApproverType.value !== 'USER' && selApproverType.value !== 'INITIATOR') selApproverType.value = 'ROLE'
    // 节点原值是数字用户ID：先把候选人拉回来，否则下拉框在选中瞬间只能显示裸主键
    if (selApproverType.value === 'USER' && selApproverValue.value) ensureAssignUsers()
  }
}

function parseCondExpr(expr) {
  condField.value = 'amount'; condOp.value = '>'; condValue.value = ''
  if (!expr) return
  const m = expr.match(/\$\{\s*(\w+)\s*(>=|<=|==|!=|>|<)\s*([^}]+)\s*\}/)
  if (m) { condField.value = m[1]; condOp.value = m[2]; condValue.value = m[3].trim() }
}

function applyCondExpr() {
  const v = condValue.value.trim()
  if (!v) { ElMessage.warning('请填写条件值'); return }
  const num = Number(v)
  selCondExpr.value = `\${${condField.value} ${condOp.value} ${Number.isNaN(num) ? "'" + v.replace(/'/g, "\\'") + "'" : num}}`
}

function applyFlowCondition() {
  if (!selFlow.value) return
  try {
    const expr = selCondExpr.value.trim()
    if (!expr) { ElMessage.warning('条件表达式为空，请先填写（如 ${amount > 5000}）'); return }
    const element = modeler.get('elementRegistry').get(selFlow.value.id)
    const moddle = modeler.get('moddle')
    const condition = moddle.create('bpmn:FormalExpression', { body: expr })
    modeler.get('modeling').updateProperties(element, { conditionExpression: condition })
    ElMessage.success('连线条件已应用')
  } catch (e) { ElMessage.error('配置失败：' + e.message) }
}

function clearFlowCondition() {
  if (!selFlow.value) return
  try {
    const element = modeler.get('elementRegistry').get(selFlow.value.id)
    modeler.get('modeling').updateProperties(element, { conditionExpression: null })
    selCondExpr.value = ''
    ElMessage.success('连线条件已清除')
  } catch (e) { ElMessage.error('清除失败：' + e.message) }
}

function setDefaultFlow(v) {
  if (!selFlow.value || !selFlowCanDefault.value) return
  try {
    const element = modeler.get('elementRegistry').get(selFlow.value.id)
    const source = element.source
    if (!source) return
    modeler.get('modeling').updateProperties(source, { default: v ? element.businessObject : null })
    ElMessage.success(v ? '已设为默认流（其余分支未命中时走此线）' : '已取消默认流')
  } catch (e) { ElMessage.error('设置失败：' + e.message) }
}

function applyNodeFinance() {
  if (!selTask.value) return
  try {
    const element = modeler.get('elementRegistry').get(selTask.value.id)
    const attrs = {}
    const val = String(selApproverValue.value || '').trim()
    if (selApproverType.value === 'ROLE') {
      if (!val) { ElMessage.warning('请选择/输入角色编码'); return }
      attrs['flowable:candidateGroups'] = val
      delete attrs['flowable:assignee']
    } else if (selApproverType.value === 'USER') {
      // assignee 必须是可登录的真人 ID：历史误存角色编码会让任务永远无人可见
      if (!/^\d+$/.test(val)) { ElMessage.warning('指定用户无效，请从人员列表重新选择'); return }
      attrs['flowable:assignee'] = val
      delete attrs['flowable:candidateGroups']
    } else if (selApproverType.value === 'INITIATOR') {
      attrs['flowable:assignee'] = '${initiator}'
      delete attrs['flowable:candidateGroups']
    }
    // 兜底清掉旧版遗留的伪属性（引擎与双账均不消费，留着只会误导下一个编辑者）
    attrs['flowable:multiInstance'] = null
    attrs['flowable:dueDate'] = null
    attrs['flowable:approverType'] = null
    const modeling = modeler.get('modeling')
    modeling.updateProperties(element, attrs)
    ElMessage.success('已应用到节点，点击「保存流程」后随流程生效')
    colorizeNodes()
  } catch (e) {
    ElMessage.error('配置失败：' + e.message)
  }
}

/** UC02 节点颜色分层 */
function colorizeNodes() {
  // 只读 Viewer 无 modeling 服务：上色属编辑态动作，只读时跳过
  if (props.readonly) return
  try {
    const registry = modeler.get('elementRegistry')
    const modeling = modeler.get('modeling')
    registry.getAll().forEach((el) => {
      const type = el.type || ''
      const bo = el.businessObject || {}
      const name = bo.name || ''
      if (type === 'bpmn:SequenceFlow') {
        if (bo.conditionExpression) {
          try { modeling.setColor(el, undefined, '#e6a23c') } catch (e3) { /* ignore */ }
        }
        return
      }
      let fill = null
      if (type === 'bpmn:StartEvent' || type === 'bpmn:EndEvent') {
        fill = '#e1f5e8'
      } else if (type === 'bpmn:ExclusiveGateway' || type === 'bpmn:ParallelGateway' || type === 'bpmn:InclusiveGateway') {
        fill = '#fff3e0'
      } else if (type === 'bpmn:CallActivity') {
        fill = '#ede7f6'
      } else if (type === 'bpmn:UserTask' || type === 'bpmn:Task' || type === 'bpmn:BusinessRuleTask' || type === 'bpmn:ServiceTask' || type === 'bpmn:ScriptTask') {
        if (name.includes('共享') || name.includes('财务') || name.includes('资金') || name.includes('记账') || name.includes('归档') || name.includes('出纳') || name.includes('审核岗')) {
          fill = '#f3e8ff'
        } else if (name.includes('预算') || name.includes('发票') || name.includes('验真') || name.includes('查重') || name.includes('校验') || name.includes('检查') || name.includes('归档') || name.includes('生成凭证') || type === 'bpmn:ServiceTask' || type === 'bpmn:ScriptTask') {
          fill = '#eceff1'
        } else {
          fill = '#e3f2fd'
        }
      }
      if (fill && el.businessObject && el.businessObject.$instanceOf && el.businessObject.$instanceOf('bpmn:FlowNode')) {
        try {
          modeling.setColor(el, fill, '#000000')
        } catch (e2) { /* ignore */ }
      }
    })
  } catch (e) { /* 颜色失败不影响编辑 */ }
}

async function exportXml() {
  try {
    const { xml } = await modeler.saveXML({ format: true })
    emit('update:modelValue', xml)
    emit('saved', xml)
    ElMessage.success('流程已保存，点击弹窗「保存」提交')
    return xml
  } catch (e) {
    ElMessage.error('导出失败：' + e.message)
    return null
  }
}

defineExpose({ save: exportXml, fitView })

function togglePanel() {
  showPanel.value = !showPanel.value
  if (!modeler) return
  try {
    modeler.get('propertiesPanel')._update()
  } catch (e) { /* ignore */ }
}

function upgradeTaskToUserTask(elementId) {
  try {
    const element = modeler.get('elementRegistry').get(elementId)
    if (!element) return
    const b = element.businessObject
    if (!b || b.$type !== 'bpmn:Task') return
    const replaced = modeler.get('modeling').replaceShape(element, { type: 'bpmn:UserTask' })
    if (replaced && !replaced.businessObject.name) {
      modeler.get('modeling').updateProperties(replaced, { name: '审批节点' })
    }
  } catch (err) { /* ignore */ }
}

function upgradeExistingTasks() {
  // 只读 Viewer 无 modeling 服务：Task→UserTask 自动升级属编辑态动作，只读时跳过
  if (props.readonly) return
  try {
    const registry = modeler.get('elementRegistry')
    const modeling = modeler.get('modeling')
    registry.getAll().forEach((el) => {
      const b = el.businessObject
      if (b && b.$type === 'bpmn:Task') {
        try {
          const replaced = modeling.replaceShape(el, { type: 'bpmn:UserTask' })
          if (replaced && !replaced.businessObject.name) {
            modeling.updateProperties(replaced, { name: '审批节点' })
          }
        } catch (e2) { /* ignore */ }
      }
    })
    colorizeNodes()
  } catch (e) { /* ignore */ }
}

// 键盘快捷键
function onKeydown(e) {
  if (e.ctrlKey || e.metaKey) {
    if (e.key === 'z' && !e.shiftKey) { e.preventDefault(); undo() }
    else if (e.key === 'y' || (e.key === 'z' && e.shiftKey)) { e.preventDefault(); redo() }
    else if (e.key === 's') { e.preventDefault(); exportXml() }
    else if (e.key === '=' || e.key === '+') { e.preventDefault(); zoomIn() }
    else if (e.key === '-') { e.preventDefault(); zoomOut() }
    else if (e.key === '0') { e.preventDefault(); resetZoom() }
  }
}

onMounted(async () => {
  loadRoleOptions()
  // 容器从 0×0（隐藏页签）变为有尺寸时，补做挂起的 fit-viewport
  if (canvasRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver((entries) => {
      if (!modeler) return
      const entry = entries && entries[0]
      if (!(entry && entry.contentRect && entry.contentRect.width > 0 && entry.contentRect.height > 0)) return
      syncCanvasSize()
      if (pendingFit) tryZoomFit()
    })
    resizeObserver.observe(canvasRef.value)
  }

  // 只读分支：用 NavigatedViewer 渲染，无调色板 / 无属性面板 / 无键盘快捷键
  if (props.readonly) {
    showPanel.value = false
    modeler = new BpmnNavigatedViewer({
      container: canvasRef.value,
      additionalModules: [zhModule],
    })
    modeler.on('canvas.viewbox.changed', () => { updateZoomState() })
    await render(props.modelValue)
    return
  }

  modeler = new BpmnModeler({
    container: canvasRef.value,
    propertiesPanel: { parent: panelRef.value },
    additionalModules: [
      BpmnPropertiesPanelModule,
      BpmnPropertiesProviderModule,
      zhModule,
      fsscPalette,
    ],
  })

  // 画布视图变化时更新缩放显示
  modeler.on('canvas.viewbox.changed', () => {
    updateZoomState()
  })

  // 命令栈变化时更新撤销/重做状态
  modeler.on('commandStack.changed', () => {
    updateUndoRedoState()
    updateElementCount()
  })

  // 新拖入节点后自动着色
  modeler.on('shape.added', () => { try { colorizeNodes() } catch (e) { /* ignore */ } })

  // 新拖入节点自动设置中文默认名称；Task 自动升级为 UserTask
  modeler.on('create.end', (e) => {
    const el = e && e.shape
    if (!el) return
    const bo = el.businessObject
    if (bo && bo.$type === 'bpmn:Task') {
      setTimeout(() => upgradeTaskToUserTask(el.id), 0)
      return
    }
    if (bo && !bo.name) {
      const name = DEFAULT_NODE_NAMES[bo.$type]
      if (name) {
        try { modeler.get('modeling').updateProperties(el, { name }) } catch (err) { /* ignore */ }
      }
    }
  })

  modeler.on('selection.changed', onSelectionChange)

  // 注册键盘快捷键
  window.addEventListener('keydown', onKeydown)

  await render(props.modelValue)
  updateUndoRedoState()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  if (resizeObserver) { try { resizeObserver.disconnect() } catch (e) { /* ignore */ } resizeObserver = null }
  if (modeler) { try { modeler.destroy() } catch (e) { /* ignore */ } modeler = null }
})
</script>

<style scoped>
.bpmn-designer {
  display: flex;
  flex-direction: column;
  gap: 6px;
  height: 100%;
}

/* 专业工具栏 */
.bpmn-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  flex-wrap: wrap;
  padding: 6px 10px;
  background: linear-gradient(180deg, #fff 0%, #f5f7fa 100%);
  border: 1px solid #dcdfe6;
  border-radius: 6px 6px 0 0;
  border-bottom: 2px solid #409eff;
}
.toolbar-group {
  display: flex;
  align-items: center;
  gap: 2px;
}
.bpmn-toolbar :deep(.el-button) {
  margin: 0;
  border-radius: 4px;
}
.bpmn-toolbar :deep(.el-button--small) {
  padding: 6px 10px;
}
.zoom-display {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  height: 28px;
  margin: 0 2px;
  padding: 0 6px;
  background: #f0f2f5;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}
.zoom-display:hover {
  background: #e6f1ff;
  border-color: #409eff;
}
.zoom-value {
  font-size: 12px;
  font-weight: 600;
  color: #409eff;
  user-select: none;
}
.toolbar-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}
.toolbar-hint .el-icon {
  color: #c0c4cc;
  font-size: 14px;
}

/* 工作区 */
.workspace {
  display: flex;
  gap: 6px;
  flex: 1;
  min-height: 0;
}
.canvas {
  flex: 1;
  width: 100%;
  min-height: 420px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #f8f9fb;
  background-image:
    linear-gradient(rgba(64, 158, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(64, 158, 255, 0.04) 1px, transparent 1px);
  background-size: 20px 20px;
}
.properties-panel {
  width: 320px;
  flex-shrink: 0;
  overflow-y: auto;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
}
/* 属性面板「表格化」皮肤（用户反馈排版乱） */
/* 顶部元素头：吸底分隔，类型名+节点名一眼可辨 */
.properties-panel :deep(.bio-properties-panel-header) {
  border-bottom: 2px solid #409eff;
  background: #f5f9ff;
  padding: 8px 10px;
}
.properties-panel :deep(.bio-properties-panel-header-icon) { font-size: 18px; }
.properties-panel :deep(.bio-properties-panel-header-label) {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
/* 分组（常规/自定义属性等）：表格式分组标题行 */
.properties-panel :deep(.bio-properties-panel-group) {
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}
.properties-panel :deep(.bio-properties-panel-group-header) {
  min-height: 34px;
  padding: 0 10px;
  background: #fafbfc;
  border-bottom: 1px solid #ebeef5;
}
.properties-panel :deep(.bio-properties-panel-group-header .bio-properties-panel-group-header-title) {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  letter-spacing: 1px;
}
/* 条目行：标签列 | 字段列 两栏对齐 = 表格排版 */
.properties-panel :deep(.bio-properties-panel-entry) {
  padding: 6px 10px;
  border-bottom: 1px dashed #f0f2f5;
}
.properties-panel :deep(.bio-properties-panel-entry:last-child) { border-bottom: none; }
.properties-panel :deep(.bio-properties-panel-label) {
  font-size: 12px;
  color: #606266;
}
.properties-panel :deep(.bio-properties-panel-input),
.properties-panel :deep(.bio-properties-panel-textfield),
.properties-panel :deep(select.bio-properties-panel-input),
.properties-panel :deep(input.bio-properties-panel-input),
.properties-panel :deep(textarea.bio-properties-panel-input) {
  font-size: 12px;
  background: #fff;
  border-color: #dcdfe6;
  border-radius: 3px;
}
.properties-panel :deep(.bio-properties-panel-input:focus) { border-color: #409eff; }
/* 屏蔽「弹窗编辑器」功能（用户要求：点击输入框右侧展开箭头弹出的英文编辑层，直接关闭） */
.properties-panel :deep(.bio-properties-panel-open-feel-popup) { display: none !important; }
.properties-panel :deep(.bio-properties-panel-feel-popup) { display: none !important; }

/* 底部状态栏 */
.status-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 10px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 0 0 6px 6px;
  font-size: 12px;
  color: #606266;
  flex-shrink: 0;
}
.status-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.status-item .el-icon {
  font-size: 13px;
  color: #909399;
}
.status-item.legend {
  margin-left: auto;
  gap: 6px;
}
.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-left: 4px;
}

/* 财务节点配置面板 */
.finance-config {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 6px 8px;
  border: 1px dashed #409eff;
  border-radius: 4px;
  background: #f5f9ff;
  font-size: 13px;
}
.timeout-field { display: inline-flex; align-items: center; gap: 4px; }
.timeout-label { font-size: 12px; color: #606266; white-space: nowrap; }
.timeout-hint {
  display: inline-flex; align-items: center; justify-content: center;
  width: 15px; height: 15px;
  border-radius: 50%; background: #e6f1ff; color: #409eff; font-size: 11px; cursor: help;
}

/* ===== 深色模式：bpmn-js 自带白底画布/调色板/属性面板 ===== */
html.dark .bpmn-toolbar {
  background: linear-gradient(180deg, #1b2440 0%, #16213e 100%);
  border-color: #2a2a4a;
}
html.dark .zoom-display { background: #1a1a3e; border-color: #2a2a4a; }
html.dark .zoom-display:hover { background: rgba(64, 158, 255, 0.16); }
html.dark .toolbar-hint { color: #a0a0b0; }
html.dark .canvas {
  background-color: #121a30;
  border-color: #2a2a4a;
  background-image:
    linear-gradient(rgba(160, 180, 220, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(160, 180, 220, 0.07) 1px, transparent 1px);
}
html.dark .properties-panel { background: #16213e; border-color: #2a2a4a; }
html.dark .status-bar { background: #14203a; border-color: #2a2a4a; color: #a0a0b0; }
html.dark .finance-config { background: rgba(64, 158, 255, 0.10) !important; }
html.dark .timeout-label { color: #a0a0b0; }
html.dark .timeout-hint { background: rgba(64, 158, 255, 0.16); }

/* 图形本体由 BpmnRenderer 写入内联 style，必须 !important 才盖得住 */
html.dark .canvas :deep(.djs-shape .djs-visual > :first-child:not(text)) {
  fill: #1d2a47 !important;
  stroke: #7b95c0 !important;
}
html.dark .canvas :deep(.djs-shape .djs-visual > :not(:first-child):not(text)) {
  stroke: #c8d4ea !important;
}
html.dark .canvas :deep(.djs-shape text),
html.dark .canvas :deep(.djs-connection text),
html.dark .canvas :deep(.djs-label) {
  fill: #e0e0e0 !important;
}
html.dark .canvas :deep(.djs-connection .djs-visual > path) {
  stroke: #8aa4cc !important;
}
html.dark .canvas :deep(marker path) {
  fill: #8aa4cc !important;
  stroke: #8aa4cc !important;
}
/* 调色板/右键菜单/上下文工具也走 diagram-js 的 CSS 变量 */
html.dark .canvas :deep(.djs-parent) {
  --color-white: #16213e;
  --color-black: #e0e0e0;
  --canvas-fill-color: #121a30;
  --bendpoint-stroke-color: #121a30;
  --palette-background-color: #16213e;
  --palette-border-color: #2a2a4a;
  --palette-entry-color: #c8c9cc;
  --palette-entry-hover-color: #66b1ff;
  --palette-separator-color: #2a2a4a;
  --palette-toggle-hover-background-color: #24304f;
  --context-pad-entry-background-color: #16213e;
  --context-pad-entry-hover-background-color: #24304f;
  --popup-background-color: #16213e;
  --popup-border-color: #2a2a4a;
  --popup-search-focus-background-color: #24304f;
  --search-container-background-color: #16213e;
}
html.dark .canvas :deep(.djs-tooltip) {
  background: #16213e;
  color: #e0e0e0;
  border-color: #2a2a4a;
}
/* 属性面板（@bpmn-io/properties-panel）的灰阶令牌整体反转 */
html.dark .properties-panel :deep(.bio-properties-panel) {
  --color-grey-225-10-15: #e0e0e0;
  --color-grey-225-10-35: #a0a0b0;
  --color-grey-225-10-55: #9098b8;
  --color-grey-225-10-75: #3a3a5a;
  --color-grey-225-10-80: #42426a;
  --color-grey-225-10-85: #4a4a72;
  --color-grey-225-10-90: #2a2a4a;
  --color-grey-225-10-95: #1b2743;
  --color-grey-225-10-97: #16213e;
  --color-white: #16213e;
  --color-black: #e0e0e0;
  --color-blue-205-100-95: #1b2a4a;
  --color-red-360-100-97: #3a1f28;
  --color-red-360-100-92: #46262f;
}
html.dark .properties-panel :deep(.bio-properties-panel-header) {
  background: #1b2a4a;
  border-color: #2a2a4a;
}
html.dark .properties-panel :deep(.bio-properties-panel-header-label) { color: #e0e0e0; }
html.dark .properties-panel :deep(.bio-properties-panel-group) {
  background: #16213e;
  border-color: #2a2a4a;
}
html.dark .properties-panel :deep(.bio-properties-panel-group-header) {
  background: #1a2240;
  border-color: #2a2a4a;
}
html.dark .properties-panel :deep(.bio-properties-panel-group-header-title) { color: #a0a0b0; }
html.dark .properties-panel :deep(.bio-properties-panel-label) { color: #a0a0b0; }
html.dark .properties-panel :deep(.bio-properties-panel-input) {
  background: #101830;
  border-color: #2a2a4a;
  color: #e0e0e0;
}
</style>
