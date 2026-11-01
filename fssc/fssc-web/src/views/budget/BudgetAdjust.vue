<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="budget-adjust-page">
    <el-card class="cm-target-dialog" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">预算调整</span>
          <el-button type="primary" size="small" @click="openDialog">新建调整单</el-button>
        </div>
      </template>
      <el-form :inline="true" size="small" style="margin-bottom:12px">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="调整单号" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
        </el-form-item>
      </el-form>
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table class="cm-target-dialog" :data="list" v-else size="small" stripe border empty-text="暂无数据" @row-dblclick="openRowDetail">
        <el-table-column prop="adjustNo" label="调整单号" width="160" sortable />
        <el-table-column prop="adjustType" label="调整类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="adjustTypeTag(row.adjustType)">{{ adjustTypeLabel(row.adjustType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyDate" label="申请日期" width="120" sortable :formatter="(row, col, val) => formatDate(val)" />
        <el-table-column prop="reason" label="调整原因" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" sortable>
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">明细</el-button>
            <el-button v-if="row.status === BillStatus.DRAFT" link type="success" size="small" @click="approve(row)">审批</el-button>
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

    <!-- 新建调整单弹窗 -->
    <!-- Escape 关闭竞态下全屏遮罩层残留拦截整页点击（destroy-on-close 已在位仍复现）。
         modal-penetrable＝遮罩 pointer-events:none、由内层弹窗承接点击（本 EP 版本以去模态实现，故无半透明遮罩、点空白不再关窗）。 -->
    <el-dialog v-model="dialog.visible" title="新建预算调整单" width="800px" destroy-on-close modal-penetrable>
      <el-form ref="formRef" :model="dialog.form" :rules="adjustFormRules" label-width="120px" size="default">
        <!-- 调整类型为单选组且恒有默认值(ADD)，不会出现未填态，故不加红星 -->
        <el-form-item label="调整类型">
          <el-radio-group v-model="dialog.form.adjustType" @change="onTypeChange">
            <el-radio value="ADD">增拨（追加预算）</el-radio>
            <el-radio value="CUT">调减（削减预算）</el-radio>
            <el-radio value="TRANSFER">调剂（部门间调拨）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="预算方案" prop="schemeId">
          <el-select v-model="dialog.form.schemeId" placeholder="选择方案" filterable style="width:100%" @change="onSchemeChange">
            <el-option v-for="s in schemes" :key="s.id" :label="s.schemeName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input v-model="dialog.form.reason" type="textarea" :rows="3" placeholder="说明调整预算的原因" />
        </el-form-item>
      </el-form>
      <!-- 调整明细行 -->
      <el-divider content-position="left">调整明细</el-divider>
      <div style="margin-bottom:8px">
        <el-button type="primary" size="small" @click="addLine">添加明细行</el-button>
      </div>
      <el-table :data="dialog.lines" size="small" border empty-text="暂无数据，请点击「添加明细行」">
        <el-table-column label="调入预算明细" min-width="200">
          <template #header><span style="color:#f56c6c">*</span>调入预算明细</template>
          <template #default="{ row }">
            <el-select v-model="row.budgetItemId" placeholder="选择预算明细" filterable size="small" style="width:100%">
              <el-option v-for="b in budgetItems" :key="b.id" :label="b.dimKey + ' (' + fmt(b.totalAmount) + ')'" :value="b.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column v-if="dialog.form.adjustType === 'TRANSFER'" label="调出预算明细" min-width="200">
          <template #header><span style="color:#f56c6c">*</span>调出预算明细</template>
          <template #default="{ row }">
            <el-select v-model="row.outItemId" placeholder="选择调出明细" filterable size="small" style="width:100%">
              <el-option v-for="b in budgetItems" :key="b.id" :label="b.dimKey + ' (' + fmt(b.totalAmount) + ')'" :value="b.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="调整金额" width="160">
          <template #header><span style="color:#f56c6c">*</span>调整金额</template>
          <template #default="{ row }">
            <el-input-number v-model="row.amount" :min="0" :precision="2" size="small" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" placeholder="备注" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="dialog.lines.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">提交</el-button>
      </template>
    </el-dialog>

    <!-- 调整单详情：草稿可编辑（改类型/方案/原因/明细行），已审批只读——调整单范式 -->
    <!-- 同新建弹窗，遮罩穿透防残留拦截 -->
    <el-dialog v-model="detailDialog.visible" :title="detailDialog.editable ? '调整单编辑（草稿）' : '调整明细'" width="860px" destroy-on-close modal-penetrable>
      <el-descriptions :column="3" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="单号">{{ detailDialog.row?.adjustNo }}</el-descriptions-item>
        <el-descriptions-item label="申请日期">{{ formatDate(detailDialog.row?.applyDate) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="statusTag(detailDialog.row?.status)">{{ statusLabel(detailDialog.row?.status) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 草稿：主表信息可编辑 -->
      <el-form v-if="detailDialog.editable" ref="detailFormRef" :model="detailDialog.form" :rules="detailFormRules" label-width="100px" size="default" style="margin-bottom:8px">
        <!-- 调整类型为单选组，编辑时回填原值，不会出现未填态，故不加红星 -->
        <el-form-item label="调整类型">
          <el-radio-group v-model="detailDialog.form.adjustType" @change="onDetailTypeChange">
            <el-radio value="ADD">增拨（追加预算）</el-radio>
            <el-radio value="CUT">调减（削减预算）</el-radio>
            <el-radio value="TRANSFER">调剂（部门间调拨）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="预算方案" prop="schemeId">
          <el-select v-model="detailDialog.form.schemeId" placeholder="选择方案" filterable style="width:100%" @change="loadItems">
            <el-option v-for="s in schemes" :key="s.id" :label="s.schemeName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input v-model="detailDialog.form.reason" type="textarea" :rows="2" placeholder="说明调整预算的原因" />
        </el-form-item>
      </el-form>
      <el-descriptions v-else :column="1" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="类型">{{ adjustTypeLabel(detailDialog.row?.adjustType) }}</el-descriptions-item>
        <el-descriptions-item label="调整原因">{{ detailDialog.row?.reason }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">调整明细</el-divider>
      <div v-if="detailDialog.editable" style="margin-bottom:8px">
        <el-button type="primary" size="small" @click="addDetailLine">添加明细行</el-button>
      </div>
      <el-table v-loading="detailDialog.loading" :data="detailDialog.lines" size="small" border empty-text="暂无明细数据">
        <!-- 可编辑模式 -->
        <template v-if="detailDialog.editable">
          <el-table-column label="调入预算明细" min-width="230">
            <template #header><span style="color:#f56c6c">*</span>调入预算明细</template>
            <template #default="{ row }">
              <el-select v-model="row.budgetItemId" placeholder="选择预算明细" filterable size="small" style="width:100%">
                <el-option v-for="b in budgetItems" :key="b.id" :label="itemOptionLabel(b)" :value="b.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column v-if="detailDialog.form.adjustType === 'TRANSFER'" label="调出预算明细" min-width="230">
            <template #header><span style="color:#f56c6c">*</span>调出预算明细</template>
            <template #default="{ row }">
              <el-select v-model="row.outItemId" placeholder="选择调出明细" filterable size="small" style="width:100%">
                <el-option v-for="b in budgetItems" :key="b.id" :label="itemOptionLabel(b)" :value="b.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="调整金额" width="160">
            <template #header><span style="color:#f56c6c">*</span>调整金额</template>
            <template #default="{ row }">
              <el-input-number v-model="row.amount" :min="0.01" :precision="2" :controls="false" size="small" style="width:100%" placeholder="金额" />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="130">
            <template #default="{ row }">
              <el-input v-model="row.remark" size="small" placeholder="备注" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="detailDialog.lines.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </template>
        <!-- 只读模式：显示可读名称而非裸 ID -->
        <template v-else>
          <el-table-column label="调入预算明细" min-width="220">
            <template #default="{ row }">{{ itemLabel(row.budgetItemId) }}</template>
          </el-table-column>
          <el-table-column v-if="detailDialog.row?.adjustType === 'TRANSFER'" label="调出预算明细" min-width="220">
            <template #default="{ row }">{{ itemLabel(row.outItemId) }}</template>
          </el-table-column>
          <el-table-column label="调整金额" width="130" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="备注" min-width="140">
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
        </template>
      </el-table>

      <template #footer>
        <template v-if="detailDialog.editable">
          <el-button @click="detailDialog.visible = false">取消</el-button>
          <el-button type="primary" :loading="detailDialog.saving" @click="saveDetail">保存修改</el-button>
          <el-button v-if="hasPerm('budget:adjust:approve')" type="success" :loading="detailDialog.saving" @click="approveFromDetail">保存并提请审批</el-button>
        </template>
        <el-button v-else @click="detailDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 单据全链路联查 -->
    <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />

    <CoachMark ref="budgetAdjCm" :guide-key="_guideKey" :selector="'.cm-target-dialog'" title="预算调整" text="预算调整页面可对已有预算方案进行追加、削减或转移操作。" />
  </div>
</template>

<script setup>

import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { budgetApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useAutoFill } from '@/composables/useAutoFill'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import CoachMark from '@/components/CoachMark.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { hasPerm } from '../../utils/perm'
import { formatDate, formatDateTime } from '@/utils/format'
const budgetAdjCm = ref(null)

// BillTraceDrawer
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
const schemes = ref([])
const budgetItems = ref([])

const dialog = reactive({
  visible: false, saving: false,
  form: { adjustType: 'ADD', schemeId: null, reason: '' },
  lines: [],
})

const detailDialog = reactive({
  visible: false, row: null, lines: [], loading: false, saving: false,
  editable: false,
  form: { adjustType: null, schemeId: null, reason: '' },
})

const { autofill, saveFormHistory } = useAutoFill()
autofill(dialog.form, 'BUDGET_ADJUST')

// 快捷键（仅保留本页可用的刷新）
useKeyboard({
  'ctrl+r': () => loadList(),
})

// 表单校验规则（红星 = 此表有规则 = 提交前必过；明细行的必填校验见 linesInvalid）
const adjustFormRules = reactive({
  schemeId: commonRules.select('请选择预算方案'),
  reason: commonRules.required('请输入调整原因'),
})
const detailFormRules = adjustFormRules

const formRef = ref(null)
const detailFormRef = ref(null)

/** 明细行必填校验：调入明细+金额>0；调剂还需调出明细，且不得与调入相同 */
function linesInvalid(lines, adjustType) {
  if (!lines.length) return '请至少添加一条调整明细'
  if (lines.some((l) => !l.budgetItemId)) return '请为每一行选择调入预算明细'
  if (lines.some((l) => !l.amount || Number(l.amount) <= 0)) return '调整金额必须大于 0'
  if (adjustType === 'TRANSFER') {
    if (lines.some((l) => !l.outItemId)) return '调剂需为每一行选择调出预算明细'
    if (lines.some((l) => l.outItemId === l.budgetItemId)) return '调入与调出不能是同一预算明细'
  }
  return ''
}

function clearOutItem(lines) {
  lines.forEach((l) => { l.outItemId = null })
}

function onTypeChange(v) {
  if (v !== 'TRANSFER') clearOutItem(dialog.lines)
}

function onDetailTypeChange(v) {
  if (v !== 'TRANSFER') clearOutItem(detailDialog.lines)
}

async function loadList() {
  loading.value = true
  try {
    const r = await budgetApi.adjustPage({ pageNum: page.current, pageSize: page.size, keyword: keyword.value })
    list.value = r?.list || r?.records || []
    page.total = r?.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function openDialog() {
  Object.assign(dialog.form, { adjustType: 'ADD', schemeId: null, reason: '' })
  dialog.lines = []
  dialog.visible = true
}

async function onSchemeChange(schemeId) {
  await loadItems(schemeId)
}

/** 拉取方案下的预算明细（新建/编辑明细下拉 + 只读名称映射共用） */
async function loadItems(schemeId) {
  if (!schemeId) { budgetItems.value = []; return }
  try {
    const r = await budgetApi.itemPage({ pageNum: 1, pageSize: 500, schemeId })
    budgetItems.value = r?.list || r?.records || []
  } catch (e) { ElMessage.error(e.message) }
}

function addLine() {
  dialog.lines.push({ budgetItemId: null, outItemId: null, amount: 0, remark: '' })
}

async function save() {
  if (!(await validateForms([formRef]))) return
  const invalid = linesInvalid(dialog.lines, dialog.form.adjustType)
  if (invalid) { ElMessage.warning(invalid); return }
  saveFormHistory('BUDGET_ADJUST', dialog.form)

  dialog.saving = true
  try {
    await budgetApi.adjustCreate({
      adjust: {
        adjustType: dialog.form.adjustType,
        schemeId: dialog.form.schemeId,
        reason: dialog.form.reason,
        status: 'DRAFT',
      },
      lines: dialog.lines,
    })
    ElMessage.success('提交成功')
    dialog.visible = false
    await loadList()
  } catch (e) { ElMessage.error(e.message) } finally { dialog.saving = false }
}

async function approve(row) {
  try {
    await ElMessageBox.confirm(`审批通过调整单「${row.adjustNo}」？审批后将更新预算额度`, '审批', { type: 'warning' })
    await budgetApi.adjustApprove(row.id)
    ElMessage.success('审批通过')
    await loadList()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

async function viewDetail(row) {
  detailDialog.row = row
  // 草稿且有建单权限 → 编辑模式；其余只读（草稿可改，审批后锁定）
  detailDialog.editable = row.status === BillStatus.DRAFT && hasPerm('budget:adjust:create')
  detailDialog.form = { adjustType: row.adjustType, schemeId: row.schemeId, reason: row.reason }
  detailDialog.lines = []
  detailDialog.visible = true
  loadItems(row.schemeId)
  detailDialog.loading = true
  try {
    const d = await budgetApi.adjustLines(row.id)
    let lines = Array.isArray(d) ? d : (d?.list || [])
    if (!lines.length && Array.isArray(row.lines) && row.lines.length) lines = row.lines
    // 深拷贝，避免编辑取消后污染列表缓存
    detailDialog.lines = lines.map((l) => ({ ...l, amount: l.amount != null ? Number(l.amount) : null }))
  } catch (e) {
    detailDialog.lines = (row.lines || []).map((l) => ({ ...l }))
    if (detailDialog.editable) ElMessage.info('明细行编辑功能暂未开放，请在调整单据中逐行提交')
  } finally { detailDialog.loading = false }
}

function addDetailLine() {
  detailDialog.lines.push({ budgetItemId: null, outItemId: null, amount: null, remark: '' })
}

/** 下拉选项文案：维度键 + 总额 */
function itemOptionLabel(b) {
  const avail = Number(b.totalAmount || 0) + Number(b.adjustAmount || 0) - Number(b.occupyAmount || 0) - Number(b.usedAmount || 0)
  return `${b.dimKey}（总额 ${fmt(b.totalAmount)} / 可用 ${fmt(avail)}）`
}

/** 只读展示：id → 可读维度键 */
function itemLabel(id) {
  if (!id) return '—'
  const b = budgetItems.value.find((x) => x.id === id)
  return b ? b.dimKey : `明细#${id}`
}

async function saveDetail() {
  if (!(await validateForms([detailFormRef]))) return
  const f = detailDialog.form
  const lines = detailDialog.lines
  const invalid = linesInvalid(lines, f.adjustType)
  if (invalid) { ElMessage.warning(invalid); return }
  detailDialog.saving = true
  try {
    await budgetApi.adjustUpdate({
      adjust: { id: detailDialog.row.id, adjustType: f.adjustType, schemeId: f.schemeId, reason: f.reason },
      lines,
    })
    ElMessage.success('保存成功')
    detailDialog.visible = false
    await loadList()
  } catch (e) { ElMessage.error(e.message) } finally { detailDialog.saving = false }
}

/** 保存并提请审批：先保存草稿，再走既有审批 */
async function approveFromDetail() {
  if (!(await validateForms([detailFormRef]))) return
  const f = detailDialog.form
  const lines = detailDialog.lines
  const invalid = linesInvalid(lines, f.adjustType)
  if (invalid) { ElMessage.warning(invalid); return }
  try {
    await ElMessageBox.confirm(`保存并审批通过调整单「${detailDialog.row.adjustNo}」？审批后将更新预算额度`, '保存并审批', { type: 'warning' })
  } catch { return }
  detailDialog.saving = true
  try {
    await budgetApi.adjustUpdate({
      adjust: { id: detailDialog.row.id, adjustType: f.adjustType, schemeId: f.schemeId, reason: f.reason },
      lines,
    })
    await budgetApi.adjustApprove(detailDialog.row.id)
    ElMessage.success('已保存并审批通过')
    detailDialog.visible = false
    await loadList()
  } catch (e) { ElMessage.error(e.message) } finally { detailDialog.saving = false }
}

function openRowDetail(row) {
  viewDetail(row)
}

function adjustTypeLabel(v) {
  return { ADD: '增拨', CUT: '调减', TRANSFER: '调剂' }[v] || v
}
function adjustTypeTag(v) {
  return { ADD: 'success', CUT: 'danger', TRANSFER: 'primary' }[v] || 'info'
}
function statusTag(v) { return STATUS_TYPE[v] || 'info' }
function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(async () => {
  try {
    const r = await budgetApi.schemePage({ pageNum: 1, pageSize: 200 })
    schemes.value = r?.list || r?.records || []
  } catch {}
  await loadList()
})

// Show guide on first visit
const _guideKey = 'fssc_guide_budgetAdjCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => budgetAdjCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
.budget-adjust-page { min-height: 100%; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-title { font-size: 15px; font-weight: 600; }
</style>
