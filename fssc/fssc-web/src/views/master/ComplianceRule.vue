<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 10px; align-items: center">
        <b>{{ $t('master.complianceRule.Compliance') }}</b>
        <span style="color:#909399; font-size:12px">{{ $t('master.complianceRule.Four') }}</span>
      <el-button v-permission="'compliance:rule:edit'" type="primary" size="small" style="margin-left:auto" @click="openCreate">{{ $t('master.complianceRule.Add') }}</el-button>
      <el-button size="small" @click="exportCsv(allRows, CSV_COLS, 'ComplianceRule')">{{ $t('common.export') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="exportBatchCsv(selRows, 'ComplianceRule')">导出选中({{ selRows.length }})</el-button>
      </div>
    </template>

    <!-- 汇总文案随列表返回读数生成，不再静态宣称已启用 -->
    <el-tag :type="summaryType" style="margin-bottom: 10px" :closable="false">{{ summaryText }}</el-tag>

    <el-table :data="rows" size="small" border stripe v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="ruleCode" label="规则编码" width="160"  sortable />
      <el-table-column prop="ruleName" label="规则名称" min-width="170" show-overflow-tooltip  sortable />
      <el-table-column prop="ruleType" label="类别" width="150" sortable>
        <template #default="{ row }">{{ ruleTypeLabel(row.ruleType) }}</template>
      </el-table-column>
      <el-table-column prop="riskLevel" label="风险等级" width="100" sortable>
        <template #default="{ row }">
          <el-tag :type="riskType(row.riskLevel)" size="small">{{ riskLevelLabel(row.riskLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="action" label="处置动作" width="110" sortable>
        <template #default="{ row }">
          <el-tag :type="actionType(row.action)" effect="plain" size="small">{{ ruleActionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="matchExpression" label="判定表达式" min-width="150" show-overflow-tooltip  sortable />
      <el-table-column prop="relatedExpenseTypes" label="适用费用类型" width="130" show-overflow-tooltip  sortable />
      <el-table-column prop="sortNo" label="排序" width="60" align="center"  sortable />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 'ENABLED'" :disabled="!canEdit" @change="(v) => toggle(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'compliance:rule:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="emptyText" />
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 12px; justify-content: flex-end;"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑合规规则' : '新增合规规则'" width="640px">
      <el-form ref="formRef" :model="form" :rules="complianceRules" label-width="110px" size="small">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="form.ruleCode" placeholder="如 COMP-DUP-INV-001" maxlength="64" />
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" />
        </el-form-item>
        <el-form-item label="规则类别">
          <el-select v-model="form.ruleType" style="width:100%">
            <el-option label="重复发票" value="DUPLICATE_INVOICE" />
            <el-option label="费用不一致" value="EXPENSE_MISMATCH" />
            <el-option label="超标" value="OVER_STANDARD" />
            <el-option label="黑名单" value="BLACKLIST" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="form.riskLevel" style="width:100%">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置动作">
          <el-select v-model="form.action" style="width:100%">
            <el-option label="拦截" value="BLOCK" />
            <el-option label="警告" value="WARN" />
            <el-option label="转人工" value="REVIEW" />
            <el-option label="仅提示 PASS" value="PASS" />
          </el-select>
        </el-form-item>
        <el-form-item label="判定表达式">
          <el-input v-model="form.matchExpression" placeholder="如 dupCount >= 1" />
        </el-form-item>
        <el-form-item label="适用费用类型">
          <el-input v-model="form.relatedExpenseTypes" placeholder="逗号分隔，空表示全局" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { complianceRuleApi } from '../../api'
import { exportCsv, exportBatchCsv } from '../../utils/csv'
import { commonRules } from '../../composables/useFormRules'
import { validateForms } from '../../composables/useSubmitGuard'
import { ruleTypeLabel, riskLevelLabel, ruleActionLabel } from '@/constants/enums.js'
import { hasPerm } from '../../utils/perm'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

const canEdit = computed(() => hasPerm('compliance:rule:edit'))

const selRows = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const dialogVisible = ref(false)
// 后端 /compliance/rule 只有 list（无 page），全量取回后本地切片分页
const allRows = ref([])
const rows = computed(() => {
  const from = (pageNum.value - 1) * pageSize.value
  return allRows.value.slice(from, from + pageSize.value)
})
const total = computed(() => allRows.value.length)
const enabledCount = computed(() => allRows.value.filter((r) => r.status === 'ENABLED').length)
const loadError = ref(false)
const summaryType = computed(() => {
  if (loadError.value) return 'danger'
  return total.value === 0 ? 'info' : enabledCount.value === 0 ? 'warning' : 'success'
})
const summaryText = computed(() => {
  if (loadError.value) return '合规规则未能加载，请重新进入本页或稍后重试'
  if (total.value === 0) return '当前共 0 条合规规则（0 条启用）· 可点【新增规则】录入'
  return `共 ${total.value} 条合规规则，其中 ${enabledCount.value} 条启用中 · 命中判定支持 拦截 / 警告 / 转人工复核 / 提示`
})
const emptyText = computed(() => (loadError.value ? '规则未能加载，请稍后重试' : '暂无合规规则，可点【新增规则】录入'))
const editing = ref(false)
const form = reactive({ id: null, ruleCode: '', ruleName: '', ruleType: 'DUPLICATE_INVOICE', riskLevel: 'MEDIUM', action: 'WARN', matchExpression: '', relatedExpenseTypes: '', sortNo: 0, remark: '' })

const formRef = ref(null)

// 规则编码/名称＝人工维护的业务键（非系统取号，不适用规则 Q1）；与后端 ComplianceRuleService 同一规则
const complianceRules = {
  ruleCode: commonRules.required('请输入规则编码'),
  ruleName: commonRules.required('请输入规则名称'),
}

const CSV_COLS = [
  { prop: 'ruleCode', label: t('master.complianceRule.Rule') }, { prop: 'ruleName', label: t('master.complianceRule.Rule2') },
  { prop: 'ruleType', label: t('master.complianceRule.Category') }, { prop: 'riskLevel', label: t('compliance.fraud.Risk2') },
  { prop: 'action', label: t('compliance.fraud.Disposition') }, { prop: 'matchExpression', label: t('master.complianceRule.Evaluation') },
  { prop: 'relatedExpenseTypes', label: t('master.complianceRule.Applicable') }, { prop: 'sortNo', label: t('master.complianceRule.Sort') },
]

function riskType(r) {
  return ({ LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' })[r] || 'info'
}
function actionType(a) {
  return ({ BLOCK: 'danger', WARN: 'warning', REVIEW: 'primary', PASS: 'info' })[a] || 'info'
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const d = await complianceRuleApi.list()
    allRows.value = Array.isArray(d) ? d : (d?.list || d?.records || [])
    const maxPage = Math.max(1, Math.ceil(allRows.value.length / pageSize.value))
    if (pageNum.value > maxPage) pageNum.value = maxPage
  } catch (e) {
    // 查询失败置空并转空态，不透出后端原文
    allRows.value = []
    loadError.value = true
    ElMessage.error(t('master.complianceRule.Compliance2'))
  } finally { loading.value = false }
}

function handleSizeChange(val) {
  pageSize.value = val
  pageNum.value = 1
}

function handleCurrentChange(val) {
  pageNum.value = val
}
function reset() {
  Object.assign(form, { id: null, ruleCode: '', ruleName: '', ruleType: 'DUPLICATE_INVOICE', riskLevel: 'MEDIUM', action: 'WARN', matchExpression: '', relatedExpenseTypes: '', sortNo: 0, remark: '' })
}
function openCreate() { editing.value = false; reset(); dialogVisible.value = true; formRef.value?.clearValidate() }
function openEdit(row) { editing.value = true; Object.assign(form, row); dialogVisible.value = true; formRef.value?.clearValidate() }

async function toggle(row) {
  const next = row.status !== 'ENABLED'
  try {
    await complianceRuleApi.toggle(row.id, next)
    ElMessage.success(t('master.complianceRule.Status'))
    load()
  } catch (e) { ElMessage.error(t('master.complianceRule.Enable')) }
}

async function save() {
  if (!(await validateForms([formRef]))) return
  try {
    if (editing.value) await complianceRuleApi.update(form)
    else await complianceRuleApi.create(form)
    ElMessage.success(t('budget.budgetAdjust.Saved'))
    dialogVisible.value = false
    load()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)

function handleSelectionChange(val) { selRows.value = val }
</script>