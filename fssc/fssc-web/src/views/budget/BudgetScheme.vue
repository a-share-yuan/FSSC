<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="budget-scheme-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">预算方案</span>
          <el-button type="primary" size="small" @click="openDialog(null)">新增方案</el-button>
        </div>
      </template>
      <!-- 搜索 -->
      <el-form :inline="true" size="small" style="margin-bottom:12px">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="方案编码/名称" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
        </el-form-item>
      </el-form>
      <!-- 列表 -->
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="list" v-else size="small" stripe border empty-text="暂无数据" @row-dblclick="openRowDetail">
        <el-table-column prop="schemeCode" label="方案编码" width="140" sortable />
        <el-table-column prop="schemeName" label="方案名称" min-width="180" />
        <el-table-column prop="budgetYear" label="预算年度" width="100" />
        <el-table-column prop="periodType" label="周期类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ periodTypeLabel(row.periodType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dimension" label="预算维度" width="160">
          <template #default="{ row }">{{ dimensionText(row.dimension) }}</template>
        </el-table-column>
        <el-table-column prop="controlType" label="控制方式" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="controlTypeTag(row.controlType)">{{ controlTypeLabel(row.controlType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="carryOver" label="结余结转" width="90">
          <template #default="{ row }">{{ row.carryOver === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" sortable>
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status === BillStatus.DRAFT || row.status === 'ACTIVE'" link :type="row.status === 'ACTIVE' ? 'warning' : 'success'" size="small" @click="toggleEnable(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="row.status === BillStatus.DRAFT" link type="success" size="small" @click="submitApprove(row)">提交审批</el-button>
            <el-button v-if="row.status === 'APPROVED'" link type="warning" size="small" @click="issue(row)">下达</el-button>
            <el-button link type="primary" size="small" @click="viewItems(row)">查看明细</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page.current" v-model:page-size="page.size"
        :total="page.total" :page-sizes="[10, 20, 50]"
        layout="total, prev, pager, next, jumper"
        style="margin-top:12px;justify-content:flex-end"
        @size-change="loadList" @current-change="loadList"
      />
    </el-card>

    <!-- 方案编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.editing ? '编辑方案' : '新增方案'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="dialog.form" :rules="schemeFormRules" label-width="120px" size="default">
        <!-- 方案编码由后端自动生成（留空时 nextSchemeCode），编辑态只读，属系统编号 → 不加红星 -->
        <el-form-item label="方案编码"><el-input v-model="dialog.form.schemeCode" :disabled="dialog.editing" :placeholder="dialog.editing ? '' : '留空由系统自动生成'" /></el-form-item>
        <el-form-item label="方案名称" prop="schemeName"><el-input v-model="dialog.form.schemeName" /></el-form-item>
        <el-form-item label="预算年度" prop="budgetYear">
          <el-input-number v-model="dialog.form.budgetYear" :min="2020" :max="2099" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="周期类型">
          <el-select v-model="dialog.form.periodType" style="width:100%">
            <el-option label="按月" value="MONTH" />
            <el-option label="按季" value="QUARTER" />
            <el-option label="按年" value="YEAR" />
          </el-select>
        </el-form-item>
        <el-form-item label="预算维度" prop="dimension">
          <el-checkbox-group v-model="dimArr">
            <el-checkbox value="DEPT">部门</el-checkbox>
            <el-checkbox value="ITEM">费用项</el-checkbox>
            <el-checkbox value="PROJECT">项目</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="控制方式">
          <el-select v-model="dialog.form.controlType" style="width:100%">
            <el-option label="预警（超预算提醒，允许提交）" value="WARN" />
            <el-option label="软控（超预算需填写超支理由）" value="SOFT" />
            <el-option label="硬控（超预算禁止提交）" value="HARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="结余结转">
          <el-switch v-model="dialog.form.carryOver" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="dialog.form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- R4-02 单据全链路联查 -->
    <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="BG_SCHEME" />
  </div>
</template>

<script setup>

import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showRowDetail } from '@/utils/format'
import { budgetApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useAutoFill } from '@/composables/useAutoFill'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useKeyboard } from '@/composables/useKeyboard'
// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const list = ref([])
const loading = ref(false)
const keyword = ref('')
const page = reactive({ current: 1, size: 10, total: 0 })
const dimArr = ref([])

const dialog = reactive({
  visible: false, editing: false, saving: false,
  form: {},
})

const { autofill, saveFormHistory } = useAutoFill()
autofill(dialog.form, 'BUDGET_SCHEME')

// R5-13: 快捷键（仅保留本页可用的刷新）
useKeyboard({
  'ctrl+r': () => loadList(),
})

// 表单校验规则（与后端 schemeCreate/schemeUpdate 必填校验同一规则）
const schemeFormRules = reactive({
  schemeName: commonRules.required('请输入方案名称'),
  budgetYear: commonRules.required('请输入预算年度'),
  dimension: commonRules.required('请至少选择一个预算维度'),
})
const formRef = ref(null)

// 维度勾选同步进表单模型，保证红星校验读到的就是勾选状态（取消勾选即时清除报错）
watch(dimArr, (v) => { dialog.form.dimension = v.join(',') })

async function loadList() {
  loading.value = true
  try {
    const r = await budgetApi.schemePage({ pageNum: page.current, pageSize: page.size, keyword: keyword.value })
    list.value = r?.list || r?.records || []
    page.total = r?.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function openDialog(row) {
  dialog.editing = !!row
  dialog.form = row ? { ...row } : {
    schemeCode: '', schemeName: '', budgetYear: new Date().getFullYear(),
    periodType: 'MONTH', dimension: 'DEPT,ITEM', controlType: 'SOFT',
    carryOver: 1, status: 'DRAFT', remark: '',
  }
  dimArr.value = row?.dimension ? row.dimension.split(',') : ['DEPT', 'ITEM']
  dialog.visible = true
}

async function save() {
  dialog.form.dimension = dimArr.value.join(',')
  if (!(await validateForms([formRef]))) return
  saveFormHistory('BUDGET_SCHEME', dialog.form)

  // 方案编码留空时由后端 schemeCreate 自动生成（nextSchemeCode），故此处不校验编码
  dialog.saving = true
  try {
    if (dialog.editing) await budgetApi.schemeUpdate({ ...dialog.form })
    else await budgetApi.schemeCreate({ ...dialog.form })
    ElMessage.success('保存成功')
    dialog.visible = false
    await loadList()
  } catch (e) { ElMessage.error(e.message) } finally { dialog.saving = false }
}

async function toggleEnable(row) {
  const enable = row.status !== 'ACTIVE'
  try {
    await ElMessageBox.confirm(`${enable ? '启用' : '停用'}方案「${row.schemeName}」？`, '确认', { type: 'warning' })
    await budgetApi.schemeEnable(row.id, enable)
    ElMessage.success(enable ? '已启用' : '已停用')
    await loadList()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// 预算审批流：提交审批与下达
async function submitApprove(row) {
  try {
    await ElMessageBox.confirm(`确认将方案「${row.schemeName || row.schemeCode}」提交审批？`, '提交审批', { type: 'warning' })
  } catch (e) { return }
  try {
    await budgetApi.schemeSubmit(row.id)
    ElMessage.success('已提交审批')
    await loadList()
  } catch (e) { if (e?.message) ElMessage.error(e.message) }
}
async function issue(row) {
  try {
    await budgetApi.schemeIssue(row.id)
    ElMessage.success('已下达')
    await loadList()
  } catch (e) { if (e?.message) ElMessage.error(e.message) }
}

function viewItems(row) {
  window.location.hash = `#/budget/item?schemeId=${row.id}&year=${row.budgetYear}`
}

function periodTypeLabel(v) {
  return { MONTH: '按月', QUARTER: '按季', YEAR: '按年' }[v] || v
}
function controlTypeLabel(v) {
  return { WARN: '预警', SOFT: '软控', HARD: '硬控' }[v] || v
}
/** dimension 是多选拼接串（库内现值 DEPT ／ DEPT,ITEM），逐段换中文再拼 */
const DIMENSION_TEXT = { DEPT: '部门', ITEM: '费用项' }
function dimensionText(v) {
  if (!v) return '—'
  return String(v).split(',').map(x => DIMENSION_TEXT[x.trim()] || x.trim()).join('、')
}
function controlTypeTag(v) {
  return { WARN: 'warning', SOFT: 'primary', HARD: 'danger' }[v] || 'info'
}
function statusTag(v) { return STATUS_TYPE[v] || 'info' }

function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

onMounted(() => loadList())
</script>

<style scoped>
.budget-scheme-page { min-height: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; }
</style>
