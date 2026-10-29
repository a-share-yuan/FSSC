<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="sharing-config-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">{{ pageTitle }}</span>
          <el-button type="primary" size="small" @click="openDialog(null)">{{ $t('sharing.common.btnAdd') }}</el-button>
        </div>
      </template>
      <el-form :inline="true" :rules="searchRules" size="small" style="margin-bottom:12px">
        <el-form-item :label="$t('sharing.common.labKeyword')">
          <el-input v-model="keyword" :placeholder="$t('sharing.cfg.phSearchCodeName')" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">{{ $t('common.search') }}</el-button>
          <el-button @click="keyword = ''; loadList()">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="filteredList" v-else size="small" stripe border max-height="560" @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
        <el-table-column
          v-for="col in columns" :key="col.prop"
          :prop="col.prop" :label="col.label" :width="col.width" :min-width="col.minWidth || 120"
          show-overflow-tooltip>
          <template v-if="col.options" #default="{ row }">
            <el-tag :type="tagType(col, row[col.prop])" size="small">
              {{ optionLabel(col, row[col.prop]) }}
            </el-tag>
          </template>
          <!-- 目标分组按 id 查名展示（冗余名称列可能为 NULL，不做名称直出） -->
          <template v-else-if="col.dynamic === 'group'" #default="{ row }">
            {{ groupNameById(row[col.prop]) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">{{ $t('sharing.common.btnEdit') }}</el-button>
            <el-button link type="danger" size="small" @click="del(row)">{{ $t('sharing.common.btnDelete') }}</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.editing ? t('sharing.common.btnEdit') : t('sharing.common.btnAdd')" width="560px" destroy-on-close>
      <el-form ref="dialogFormRef" :model="dialog.form" :rules="dialogRules" label-width="110px" size="default">
        <el-form-item v-for="col in columns" :key="col.prop" :label="col.label" :prop="col.prop">
          <el-select v-if="col.options" v-model="dialog.form[col.prop]" style="width:100%" :placeholder="$t('sharing.cfg.phSelectOption')">
            <el-option v-for="o in col.options" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <!-- 目标分组下拉（选项来自分组管理 op_sh_group），只提交 id -->
          <el-select v-else-if="col.dynamic === 'group'" v-model="dialog.form[col.prop]" filterable clearable style="width:100%" :placeholder="$t('sharing.cfg.phSelectGroup')">
            <el-option v-for="g in groupOptions" :key="g.id" :label="g.groupName || g.groupCode || ('#' + g.id)" :value="g.id" />
          </el-select>
          <el-input-number v-else-if="col.type === 'number'" v-model="dialog.form[col.prop]" :controls="false" style="width:100%" />
          <el-date-picker v-else-if="col.type === 'date'" v-model="dialog.form[col.prop]" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          <el-input v-else v-model="dialog.form[col.prop]" :placeholder="$t('sharing.configTable.phRequiredField', { v1: col.label })" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">{{ $t('common.save') }}</el-button>
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
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+r': () => loadList(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})

// BillTraceDrawer
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

const STATUS_OPTS = computed(() => ([
  { label: t('sharing.cfg.optNormal'), value: 'NORMAL' },
  { label: t('sharing.common.colDisabled'), value: 'DISABLED' },
]))

const CONFIG_MAP = computed(() => ({
  job: {
    title: t('sharing.cfg.titleJob'),
    api: { page: opsCfgApi.jobPage, create: opsCfgApi.jobCreate, update: opsCfgApi.jobUpdate, delete: opsCfgApi.jobDelete },
    columns: [
      { prop: 'jobCode', label: t('sharing.cfg.colJobCode'), width: 120, required: true },
      { prop: 'jobName', label: t('sharing.cfg.colJobName'), minWidth: 140, required: true },
      { prop: 'workItems', label: t('sharing.common.colWorkItem'), minWidth: 160 },
      { prop: 'status', label: t('common.status'), width: 90, options: STATUS_OPTS.value, defaultValue: 'NORMAL' },
      { prop: 'remark', label: t('common.remark'), minWidth: 120 },
    ],
  },
  group: {
    title: t('sharing.cfg.titleGroup'),
    api: { page: opsCfgApi.groupPage, create: opsCfgApi.groupCreate, update: opsCfgApi.groupUpdate, delete: opsCfgApi.groupDelete },
    columns: [
      { prop: 'groupCode', label: t('sharing.cfg.colGroupCode'), width: 120, required: true },
      { prop: 'groupName', label: t('sharing.cfg.colGroupName'), minWidth: 140, required: true },
      { prop: 'bizType', label: t('sharing.common.colBizType'), width: 110, options: [
        { label: t('sharing.cfg.optExpenseGroup'), value: 'EXPENSE' }, { label: t('sharing.cfg.optFundGroup'), value: 'FUND' },
        { label: t('sharing.cfg.optArGroup'), value: 'AR' }, { label: t('sharing.cfg.optApGroup'), value: 'AP' },
      ]},
      { prop: 'groupType', label: t('sharing.cfg.colGroupType'), width: 100, options: [
        { label: t('sharing.cfg.optByType'), value: 'BY_TYPE' }, { label: t('sharing.cfg.optByAmount'), value: 'BY_AMOUNT' },
        { label: t('sharing.cfg.optByLegal'), value: 'BY_LEGAL' }, { label: t('sharing.cfg.optByRegion'), value: 'BY_REGION' },
      ]},
      { prop: 'taskMode', label: t('sharing.cfg.colPullMode'), width: 90, options: [
        { label: t('sharing.common.colManual'), value: 'MANUAL' }, { label: t('sharing.common.colSystem'), value: 'SYSTEM' },
      ]},
      { prop: 'memberMin', label: t('sharing.cfg.colMin'), width: 70, type: 'number' },
      { prop: 'memberMax', label: t('sharing.cfg.colMax'), width: 70, type: 'number' },
      { prop: 'status', label: t('common.status'), width: 90, options: STATUS_OPTS.value, defaultValue: 'NORMAL' },
      { prop: 'remark', label: t('common.remark'), minWidth: 120 },
    ],
  },
  calendar: {
    title: t('sharing.cfg.titleCalendar'),
    api: { page: opsCfgApi.calendarPage, create: opsCfgApi.calendarCreate, update: opsCfgApi.calendarUpdate, delete: opsCfgApi.calendarDelete },
    columns: [
      { prop: 'calDate', label: t('sharing.cfg.colDate'), width: 120, type: 'date', required: true },
      { prop: 'dayType', label: t('sharing.cfg.colDayType'), width: 100, options: [
        { label: t('sharing.cfg.optWorkday'), value: 'WORKDAY' }, { label: t('sharing.cfg.optRestday'), value: 'REST' }, { label: t('sharing.cfg.optHoliday'), value: 'HOLIDAY' },
      ], defaultValue: 'WORKDAY' },
      { prop: 'year', label: t('sharing.cfg.colYear'), width: 70, type: 'number' },
      { prop: 'month', label: t('sharing.cfg.colMonth'), width: 70, type: 'number' },
      { prop: 'workStart', label: t('sharing.cfg.colShiftStart'), width: 90 },
      { prop: 'workEnd', label: t('sharing.cfg.colShiftEnd'), width: 90 },
      { prop: 'remark', label: t('common.remark'), minWidth: 120 },
    ],
  },
  'dispatch-rule': {
    title: t('sharing.cfg.titleDispatch'),
    api: { page: opsCfgApi.dispatchRulePage, create: opsCfgApi.dispatchRuleCreate, update: opsCfgApi.dispatchRuleUpdate, delete: opsCfgApi.dispatchRuleDelete },
    columns: [
      { prop: 'ruleCode', label: t('sharing.common.colRuleCode'), width: 120, required: true },
      { prop: 'ruleName', label: t('sharing.common.colRuleName'), minWidth: 140, required: true },
      { prop: 'taskType', label: t('sharing.common.colWorkItem'), width: 100, options: [
        { label: t('sharing.common.colAudit'), value: 'AUDIT' }, { label: t('sharing.common.colBookkeep'), value: 'BOOKKEEP' }, { label: t('sharing.cfg.colSettle'), value: 'PAY' },
      ]},
      { prop: 'mode', label: t('sharing.cfg.colDispatchMode'), width: 100, options: [
        { label: t('sharing.cfg.optSystemDispatch'), value: 'SYSTEM' }, { label: t('sharing.cfg.optManualHandle'), value: 'MANUAL' },
        // 库中已有 AUTO 值，缺此标签会显示成裸码且编辑时选不回
        { label: t('sharing.cfg.optAutoDispatch'), value: 'AUTO' },
      ], defaultValue: 'SYSTEM' },
      // 策略值域对齐引擎实认集（compliance DispatchService.selectReviewer 的 switch 分支：
      // ROUND_ROBIN/LOAD_BALANCE/SKILL_MATCH/CREDIT_BASED；库中亦有 CREDIT_BASED 实例）。
      // 原 LEAST_LOADED/RANDOM 引擎不识别（掉 default 分支），已从编辑选项移除，旧值经 optionLabel 兜底提示。
      { prop: 'dispatchStrategy', label: t('sharing.common.colStrategy'), width: 110, options: [
        { label: t('sharing.cfg.optRoundRobin'), value: 'ROUND_ROBIN' }, { label: t('sharing.cfg.optLoadBalance'), value: 'LOAD_BALANCE' },
        { label: t('sharing.cfg.optSkillMatch'), value: 'SKILL_MATCH' }, { label: t('sharing.cfg.optCreditBased'), value: 'CREDIT_BASED' },
      ]},
      { prop: 'priority', label: t('sharing.common.colPriority'), width: 80, type: 'number', defaultValue: 10 },
      { prop: 'slaHours', label: t('sharing.cfg.colSlaHours'), width: 90, type: 'number' },
      // 目标分组曾是手填展示列（行里只存 id、名称没填，页面空白）；
      // 改为 id 驱动的下拉列：编辑选分组、展示按 id 查名，保存只提交 target_group_id。
      { prop: 'targetGroupId', label: t('sharing.cfg.colTargetGroup'), minWidth: 140, dynamic: 'group' },
      { prop: 'status', label: t('common.status'), width: 90, options: STATUS_OPTS.value, defaultValue: 'NORMAL' },
      { prop: 'remark', label: t('common.remark'), minWidth: 120 },
    ],
  },
}))

const cfg = computed(() => CONFIG_MAP.value[props.configType] || CONFIG_MAP.value.job)
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
 * 列驱动的必填规则。el-form-item 没有 prop 的话，dialogRules 从未生效，红星只是画出来；
 * 补 prop 后必须用自定义 validator：本表必填列含数字/日期，三键 {required,message,trigger} 会把 0 判成未填。
 * 数字列不再另挂 type:'number' 规则——el-input-number 本身只产数字，挂了反而让「清空的可选数字列」存不进去。
 */
const dialogRules = computed(() => {
  const r = {}
  columns.value.forEach((col) => {
    if (!col.required || !col.prop) return
    const isSelect = col.options || col.type === 'date'
    r[col.prop] = commonRules.required((isSelect ? t('common.pleaseSelect') : t('common.pleaseInput')) + col.label)
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
  // 未知值不得直出裸码（运维看到 LEAST_LOADED 这种值也判断不了含义与处置）；
  // 空值给占位符，未知非空值给可行动文案。
  if (val == null || val === '') return '—'
  const o = (col.options || []).find(o => o.value === val)
  return o ? o.label : t('sharing.cfg.labelUnknownValue')
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

// 系统列（审计/租户/逻辑删）不回传，避免旧行回显触发后端填充跳过或入参伪造
const SYSTEM_COLS = ['createBy', 'createTime', 'updateBy', 'updateTime', 'delFlag', 'tenantId']
function stripSystemCols(o) {
  const c = { ...o }
  SYSTEM_COLS.forEach((k) => delete c[k])
  return c
}

// 目标分组只提交 id——名称是服务端冗余列（存在名称为 NULL 的行），
// 把旧行回显的名称/派生标志回传会把冗余列写脏或引入伪造面。
function stripDerivedCols(o) {
  const c = stripSystemCols(o)
  if (props.configType === 'dispatch-rule') {
    delete c.targetGroupName
    delete c.targetGroupAutoDispatch
  }
  return c
}

// 分组下拉选项（分组管理 op_sh_group）与按 id 查名
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
    ElMessage.success(t('sharing.common.msgSaved'))
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
    await ElMessageBox.confirm(t('sharing.cfg.confirmDelete'), t('sharing.common.btnDelete'), { type: 'warning' })
    await api.value.delete(row.id)
    ElMessage.success(t('sharing.common.msgDelOk'))
    await loadList()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

onMounted(() => {
  loadList()
  // 派单规则页需分组下拉（展示按 id 查名、编辑按下拉选择）
  if (props.configType === 'dispatch-rule') loadGroupOptions()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; color: #303133; }
</style>
