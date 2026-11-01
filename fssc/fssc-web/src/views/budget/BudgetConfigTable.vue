<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="budget-config-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">{{ title }}</span>
          <el-button type="primary" size="small" @click="openDialog(null)">新增</el-button>
        </div>
      </template>
      <el-form :inline="true" size="small" style="margin-bottom:12px">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="搜索" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="keyword = ''; loadList()">重置</el-button>
        </el-form-item>
      </el-form>
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="list" v-else size="small" stripe border empty-text="暂无数据">
        <el-table-column
          v-for="col in columns" :key="col.prop"
          :prop="col.prop" :label="col.label" :width="col.width" :min-width="col.minWidth || 120"           :sortable="['categoryCode', 'orgCode', 'periodNo', 'budgetItemId'].includes(col.prop)"
          :formatter="col.format ? (row) => col.format(row[col.prop]) : undefined"
          show-overflow-tooltip
        />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">          <InlineActions :visible="true" @view="openDialog(row, true)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="false" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="del(row)">删除</el-button>
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

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.readonly ? '详情' : (dialog.editing ? '编辑' : '新增')" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="dialog.form" :rules="configFormRules" label-width="120px" size="default" :disabled="dialog.readonly">
        <el-form-item v-for="col in columns" :key="col.prop" :label="col.label" :prop="col.prop">
          <el-input v-if="col.type !== 'select'" v-model="dialog.form[col.prop]" />
          <el-select v-else v-model="dialog.form[col.prop]" style="width:100%">
            <el-option v-for="o in col.options" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">{{ dialog.readonly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!dialog.readonly" type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { masterCfgApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import InlineActions from '@/components/InlineActions.vue'
import { useApprovalScope } from '../../composables/useApprovalScope'
import { useApprovalDialog } from '@/composables/useApprovalDialog'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'

// 审批按钮归属门控：仅当该单据存在派给当前用户的待办任务时才显示，
// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const props = defineProps({
  configType: { type: String, required: true }, // 'budgetCategory' | 'budgetOrg' | 'budgetPeriod' | 'itemBudgetMap'
  title: { type: String, default: '' },
})

const api = computed(() => masterCfgApi[props.configType])
const list = ref([])
const loading = ref(false)
const keyword = ref('')
const page = reactive({ current: 1, size: 10, total: 0 })

// 映射行维度串登记为「重整中间态」，多数不含 '-DEPT' 段（＝按公司下达、无部门维度）；
// 与 bg_item.dim_key（恒含 DEPT 段）不同构，此处显式标注缺段，避免用户按该键回查预算表落空而不明因。
function annotateDimKey(v) {
  const s = v == null ? '' : String(v)
  if (!s) return '—'
  return s.includes('-DEPT') ? s : s + '（公司级·无部门段）'
}

// 各类型列定义
const columnsConfig = {
  budgetCategory: [
    { prop: 'categoryCode', label: '类别编码', width: 140, required: true },
    { prop: 'categoryName', label: '类别名称', minWidth: 200, required: true },
    { prop: 'remark', label: '备注', minWidth: 200 },
  ],
  budgetOrg: [
    { prop: 'orgCode', label: '组织编码', width: 140, required: true },
    { prop: 'orgName', label: '组织名称', minWidth: 200, required: true },
    { prop: 'remark', label: '备注', minWidth: 200 },
  ],
  budgetPeriod: [
    { prop: 'periodNo', label: '期间编码', width: 140, required: true },
    { prop: 'periodName', label: '期间名称', minWidth: 200, required: true },
    { prop: 'remark', label: '备注', minWidth: 200 },
  ],
  itemBudgetMap: [
    { prop: 'itemName', label: '费用项名称', minWidth: 200, required: true },
    // 该列是外部来源系统的预算科目编码（与 FSSC 侧 item_id 不同键空间），显式标注来源，
    // 不再与「预算维度」列并呈为同一主键空间（两列回查关系见预算明细页 dim_key 规则）。
    { prop: 'budgetItemId', label: '预算科目ID（外部系统编码）', width: 180, required: true },
    // 映射行维度串为重整中间态，通常缺 '-DEPT' 段（公司级下达）；缺段即显式标注，
    // 避免用户拿该键去 bg_item（dim_key 恒含 DEPT 段，形如 2026-COMPANY1-DEPTnull-ITEM333）定位落空而不知因。
    { prop: 'budgetDimKey', label: '预算维度', minWidth: 220, format: (v) => annotateDimKey(v) },
    { prop: 'remark', label: '备注', minWidth: 200 },
  ],
}
const columns = computed(() => columnsConfig[props.configType] || columnsConfig.budgetCategory)

const dialog = reactive({
  visible: false, editing: false, saving: false, readonly: false,
  form: {},
})
const formRef = ref(null)

// 表单校验规则（基于动态列定义生成）
// 必填判定用 commonRules.required：它靠自定义 validator 判空，数字/布尔列（如预算科目 ID）
// 的正常取值不会被误判成未填——纯 {required,message,trigger} 三键规则会改走 string 校验器。
const configFormRules = computed(() => {
  const rules = {}
  columns.value.forEach(col => {
    if (col.required) {
      rules[col.prop] = commonRules.required(`请输入${col.label}`)
    }
  })
  return rules
})

async function loadList() {
  loading.value = true
  try {
    const r = await api.value.page({ pageNum: page.current, pageSize: page.size, keyword: keyword.value })
    list.value = r?.list || r?.records || []
    page.total = r?.total || list.value.length
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function openDialog(row, readonly = false) {
  dialog.editing = !!row
  dialog.readonly = !!readonly && !!row
  dialog.form = row ? { ...row } : {}
  columns.value.forEach(col => {
    if (dialog.form[col.prop] === undefined) dialog.form[col.prop] = ''
  })
  dialog.visible = true
}

async function save() {
  if (!(await validateForms([formRef]))) return
  dialog.saving = true
  try {
    if (dialog.editing) await api.value.update({ ...dialog.form })
    else await api.value.create({ ...dialog.form })
    ElMessage.success('保存成功')
    dialog.visible = false
    await loadList()
  } catch (e) { ElMessage.error(e.message) } finally { dialog.saving = false }
}

async function del(row) {
  try {
    await ElMessageBox.confirm('确认删除？', '删除', { type: 'warning' })
    await api.value.delete(row.id)
    ElMessage.success('已删除')
    await loadList()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

watch(() => props.configType, () => {
  page.current = 1; keyword.value = ''
  loadList()
})

onMounted(() => loadList())
// InlineActions approve/reject handlers

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: loadList })
</script>

<style scoped>
.budget-config-page { min-height: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; }
</style>
