<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="sharing-config-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">{{ pageTitle }}</span>
          <el-button type="primary" size="small" @click="openDialog(null)">新增</el-button>
        </div>
      </template>
      <el-form :inline="true" :rules="searchRules" size="small" style="margin-bottom:12px">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="搜索编码/名称..." clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="keyword = ''; loadList()">重置</el-button>
        </el-form-item>
      </el-form>
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="filteredList" v-else size="small" stripe border max-height="560" @row-dblclick="openRowDetail" empty-text="暂无数据">
        <el-table-column
          v-for="col in columns" :key="col.prop"
          :prop="col.prop" :label="col.label" :width="col.width" :min-width="col.minWidth || 120"
          show-overflow-tooltip>
          <template v-if="col.options" #default="{ row }">
            <el-tag :type="tagType(col, row[col.prop])" size="small">
              {{ optionLabel(col, row[col.prop]) }}
            </el-tag>
          </template>
          <!-- R5-D-253：目标分组按 id 查名展示（冗余名称列可能为 NULL，不做名称直出） -->
          <template v-else-if="col.dynamic === 'group'" #default="{ row }">
            {{ groupNameById(row[col.prop]) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="del(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page.current" v-model:page-size="page.size"
        :total="page.total" :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top:12px;justify-content:flex-end"
        @size-change="loadList" @current-change="loadList"
      />
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.editing ? '编辑' : '新增'" width="560px" destroy-on-close>
      <el-form ref="dialogFormRef" :model="dialog.form" :rules="dialogRules" label-width="110px" size="default">
        <el-form-item v-for="col in columns" :key="col.prop" :label="col.label" :prop="col.prop">
          <el-select v-if="col.options" v-model="dialog.form[col.prop]" style="width:100%" :placeholder="'请选择'">
            <el-option v-for="o in col.options" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <!-- R5-D-253：目标分组下拉（选项来自分组管理 op_sh_group），只提交 id -->
          <el-select v-else-if="col.dynamic === 'group'" v-model="dialog.form[col.prop]" filterable clearable style="width:100%" placeholder="请选择分组">
            <el-option v-for="g in groupOptions" :key="g.id" :label="g.groupName || g.groupCode || ('#' + g.id)" :value="g.id" />
          </el-select>
          <el-input-number v-else-if="col.type === 'number'" v-model="dialog.form[col.prop]" :controls="false" style="width:100%" />
          <el-date-picker v-else-if="col.type === 'date'" v-model="dialog.form[col.prop]" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          <el-input v-else v-model="dialog.form[col.prop]" :placeholder="'请输入' + col.label" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>

import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { opsCfgApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { commonRules } from '../../composables/useFormRules'
import { validateForms } from '../../composables/useSubmitGuard'
import { useKeyboard } from '@/composables/useKeyboard'
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => loadList(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

// 双击行：调用已有详情/编辑函数
function openRowDetail(row) {
  openDialog(row)
}

const props = defineProps({
  configType: { type: String, required: true },
  title: { type: String, default: '' },
})

const STATUS_OPTS = [
  { label: '正常', value: 'NORMAL' },
  { label: '停用', value: 'DISABLED' },
]

const CONFIG_MAP = {
  job: {
    title: '作业岗位设置',
    api: { page: opsCfgApi.jobPage, create: opsCfgApi.jobCreate, update: opsCfgApi.jobUpdate, delete: opsCfgApi.jobDelete },
    columns: [
      { prop: 'jobCode', label: '岗位编码', width: 120, required: true },
      { prop: 'jobName', label: '岗位名称', minWidth: 140, required: true },
      { prop: 'workItems', label: '作业项', minWidth: 160 },
      { prop: 'status', label: '状态', width: 90, options: STATUS_OPTS, defaultValue: 'NORMAL' },
      { prop: 'remark', label: '备注', minWidth: 120 },
    ],
  },
  group: {
    title: '分组管理',
    api: { page: opsCfgApi.groupPage, create: opsCfgApi.groupCreate, update: opsCfgApi.groupUpdate, delete: opsCfgApi.groupDelete },
    columns: [
      { prop: 'groupCode', label: '分组编码', width: 120, required: true },
      { prop: 'groupName', label: '分组名称', minWidth: 140, required: true },
      { prop: 'bizType', label: '业务类型', width: 110, options: [
        { label: '费用组', value: 'EXPENSE' }, { label: '资金组', value: 'FUND' },
        { label: '应收组', value: 'AR' }, { label: '应付组', value: 'AP' },
      ]},
      { prop: 'groupType', label: '组类型', width: 100, options: [
        { label: '按类型', value: 'BY_TYPE' }, { label: '按金额', value: 'BY_AMOUNT' },
        { label: '按法人', value: 'BY_LEGAL' }, { label: '按区域', value: 'BY_REGION' },
      ]},
      { prop: 'taskMode', label: '提取方式', width: 90, options: [
        { label: '手动', value: 'MANUAL' }, { label: '系统', value: 'SYSTEM' },
      ]},
      { prop: 'memberMin', label: '下限', width: 70, type: 'number' },
      { prop: 'memberMax', label: '上限', width: 70, type: 'number' },
      { prop: 'status', label: '状态', width: 90, options: STATUS_OPTS, defaultValue: 'NORMAL' },
      { prop: 'remark', label: '备注', minWidth: 120 },
    ],
  },
  calendar: {
    title: '工作日历',
    api: { page: opsCfgApi.calendarPage, create: opsCfgApi.calendarCreate, update: opsCfgApi.calendarUpdate, delete: opsCfgApi.calendarDelete },
    columns: [
      { prop: 'calDate', label: '日期', width: 120, type: 'date', required: true },
      { prop: 'dayType', label: '日期类型', width: 100, options: [
        { label: '工作日', value: 'WORKDAY' }, { label: '休息日', value: 'REST' }, { label: '节假日', value: 'HOLIDAY' },
      ], defaultValue: 'WORKDAY' },
      { prop: 'year', label: '年', width: 70, type: 'number' },
      { prop: 'month', label: '月', width: 70, type: 'number' },
      { prop: 'workStart', label: '在岗开始', width: 90 },
      { prop: 'workEnd', label: '在岗结束', width: 90 },
      { prop: 'remark', label: '备注', minWidth: 120 },
    ],
  },
  'dispatch-rule': {
    title: '抢派单规则',
    api: { page: opsCfgApi.dispatchRulePage, create: opsCfgApi.dispatchRuleCreate, update: opsCfgApi.dispatchRuleUpdate, delete: opsCfgApi.dispatchRuleDelete },
    columns: [
      { prop: 'ruleCode', label: '规则编码', width: 120, required: true },
      { prop: 'ruleName', label: '规则名称', minWidth: 140, required: true },
      { prop: 'taskType', label: '作业项', width: 100, options: [
        { label: '审核', value: 'AUDIT' }, { label: '记账', value: 'BOOKKEEP' }, { label: '支付', value: 'PAY' },
      ]},
      { prop: 'mode', label: '分派模式', width: 100, options: [
        { label: '系统分派', value: 'SYSTEM' }, { label: '人工处理', value: 'MANUAL' },
        // R5-D-252：库中已有 AUTO 值（只读实测 op_dispatch_rule id=19），缺此标签会显示成裸码且编辑时选不回
        { label: '自动分派', value: 'AUTO' },
      ], defaultValue: 'SYSTEM' },
      // R5-D-252：策略值域对齐引擎实认集（compliance DispatchService.selectReviewer 的 switch 分支：
      // ROUND_ROBIN/LOAD_BALANCE/SKILL_MATCH/CREDIT_BASED；库中亦有 CREDIT_BASED 实例）。
      // 原 LEAST_LOADED/RANDOM 引擎不识别（掉 default 分支），已从编辑选项移除，旧值经 optionLabel 兜底提示。
      { prop: 'dispatchStrategy', label: '策略', width: 110, options: [
        { label: '轮询', value: 'ROUND_ROBIN' }, { label: '负载均衡', value: 'LOAD_BALANCE' },
        { label: '技能匹配', value: 'SKILL_MATCH' }, { label: '信用分驱动', value: 'CREDIT_BASED' },
      ]},
      { prop: 'priority', label: '优先级', width: 80, type: 'number', defaultValue: 10 },
      { prop: 'slaHours', label: 'SLA小时', width: 90, type: 'number' },
      // R5-D-253：目标分组原为手填展示列（只读实测 id=1 的行名称为 NULL，页面空白）；
      // 改为 id 驱动的下拉列：编辑选分组、展示按 id 查名，保存只提交 target_group_id。
      { prop: 'targetGroupId', label: '目标分组', minWidth: 140, dynamic: 'group' },
      { prop: 'status', label: '状态', width: 90, options: STATUS_OPTS, defaultValue: 'NORMAL' },
      { prop: 'remark', label: '备注', minWidth: 120 },
    ],
  },
}

const cfg = computed(() => CONFIG_MAP[props.configType] || CONFIG_MAP.job)
const api = computed(() => cfg.value.api)
const columns = computed(() => cfg.value.columns)
const pageTitle = computed(() => props.title || cfg.value.title)

const keyword = ref('')
const loading = ref(false)
const list = ref([])
const page = reactive({ current: 1, size: 20, total: 0 })
const dialog = reactive({ visible: false, editing: null, form: {}, saving: false })

const searchRules = reactive({})
const dialogFormRef = ref(null)
/**
 * 列驱动的必填规则。此前 el-form-item 没有 prop，dialogRules 从未生效，红星只是画出来；
 * 补 prop 后必须用自定义 validator：本表必填列含数字/日期，三键 {required,message,trigger} 会把 0 判成未填。
 * 数字列不再另挂 type:'number' 规则——el-input-number 本身只产数字，挂了反而让「清空的可选数字列」存不进去。
 */
const dialogRules = computed(() => {
  const r = {}
  columns.value.forEach((col) => {
    if (!col.required || !col.prop) return
    const isSelect = col.options || col.type === 'date'
    r[col.prop] = commonRules.required((isSelect ? '请选择' : '请输入') + col.label)
  })
  return r
})

const filteredList = computed(() => {
  if (!keyword.value) return list.value
  const k = keyword.value.toLowerCase()
  return list.value.filter(r =>
    columns.value.some(c => String(r[c.prop] || '').toLowerCase().includes(k))
  )
})

async function loadList() {
  loading.value = true
  try {
    const res = await api.value.page({ page: page.current, size: page.size })
    list.value = res?.list || res?.records || []
    page.total = res?.total || list.value.length
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function optionLabel(col, val) {
  // R5-D-252：未知值不得直出裸码（运维看到 LEAST_LOADED 这种值也判断不了含义与处置）；
  // 空值给占位符，未知非空值给可行动文案。
  if (val == null || val === '') return '—'
  const o = (col.options || []).find(o => o.value === val)
  return o ? o.label : '未识别值（联系管理员）'
}
function tagType(col, val) {
  if (val === 'NORMAL' || val === 'WORKDAY' || val === 'SYSTEM' || val === 'PASS') return 'success'
  if (val === 'DISABLED' || val === 'HOLIDAY' || val === 'BACK') return 'danger'
  if (val === 'REST' || val === 'MANUAL') return 'warning'
  return 'info'
}

function openDialog(row) {
  dialog.editing = row
  if (row) {
    dialog.form = { ...row }
  } else {
    dialog.form = {}
    columns.value.forEach(c => {
      if (c.defaultValue !== undefined) dialog.form[c.prop] = c.defaultValue
    })
  }
  dialog.visible = true
}

// R5-D-249：系统列（审计/租户/逻辑删）不回传，避免旧行回显触发后端填充跳过或入参伪造
const SYSTEM_COLS = ['createBy', 'createTime', 'updateBy', 'updateTime', 'delFlag', 'tenantId']
function stripSystemCols(o) {
  const c = { ...o }
  SYSTEM_COLS.forEach((k) => delete c[k])
  return c
}

// R5-D-253：目标分组只提交 id——名称是服务端冗余列（只读实测存在为 NULL 的行），
// 把旧行回显的名称/派生标志回传会把冗余列写脏或引入伪造面。
function stripDerivedCols(o) {
  const c = stripSystemCols(o)
  if (props.configType === 'dispatch-rule') {
    delete c.targetGroupName
    delete c.targetGroupAutoDispatch
  }
  return c
}

// R5-D-253：分组下拉选项（分组管理 op_sh_group）与按 id 查名
const groupOptions = ref([])
async function loadGroupOptions() {
  try {
    const res = await opsCfgApi.groupPage({ page: 1, size: 200 })
    groupOptions.value = res?.list || res?.records || []
  } catch (e) { groupOptions.value = [] }
}
function groupNameById(id) {
  if (id == null || id === '') return '—'
  const g = groupOptions.value.find((x) => String(x.id) === String(id))
  return g ? (g.groupName || g.groupCode || String(id)) : `#${id}`
}

async function save() {
  if (!(await validateForms([dialogFormRef]))) return
  dialog.saving = true
  try {
    if (dialog.editing) {
      await api.value.update(stripDerivedCols(dialog.form))
    } else {
      await api.value.create(stripDerivedCols(dialog.form))
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    await loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    dialog.saving = false
  }
}

async function del(row) {
  try {
    await ElMessageBox.confirm('确认删除该记录？', '删除', { type: 'warning' })
    await api.value.delete(row.id)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

onMounted(() => {
  loadList()
  // R5-D-253：派单规则页需分组下拉（展示按 id 查名、编辑按下拉选择）
  if (props.configType === 'dispatch-rule') loadGroupOptions()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; color: #303133; }
</style>
