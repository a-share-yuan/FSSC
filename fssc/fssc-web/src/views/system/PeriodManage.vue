<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="period-manage">
    <el-card>
      <!-- Toolbar -->
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索期间编码" clearable style="width:220px" @keyup.enter="loadList" />
        <!-- R5-D-042：公司维度筛选——与弹窗「所属公司」同源（orgApi.companyTree 词表）；筛选值交服务端，服务端另叠加登录人 data_scope -->
        <el-select v-model="query.companyId" placeholder="公司" clearable filterable style="width:200px" @change="onQuery">
          <el-option v-for="c in companies" :key="c.id" :label="c.companyName || c.companyCode" :value="c.id" />
        </el-select>
        <el-button type="primary" @click="onQuery">查询</el-button>
        <el-button v-if="hasPerm('md:period:create')" type="success" @click="openCreate">新增期间</el-button>
        <el-button v-if="hasPerm('md:period:close')" type="warning" :disabled="!selRows.length" @click="onBatchClose">批量结账({{ selRows.length }})</el-button>
        <el-button v-if="hasPerm('md:period:close')" :disabled="!selRows.length" @click="onBatchReopen">批量反结账({{ selRows.length }})</el-button>
        <el-button @click="doExport">导出 CSV</el-button>
      </div>

      <!-- Table -->
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="list" v-else border stripe @row-dblclick="openRowDetail" empty-text="暂无数据" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" :selectable="(row) => row.status !== 'DISABLED'" />
        <el-table-column label="期间编码" min-width="120">
          <template #default="{ row }">{{ periodCodeOf(row) }}</template>
        </el-table-column>
        <!-- R5-D-042：公司维度列——值来自服务端 row.companyId，名称按 orgApi.companyTree 词表翻译（md_period 无 company_name 列，不做 DDL） -->
        <el-table-column label="公司" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ companyName(row.companyId) }}</template>
        </el-table-column>
        <el-table-column prop="periodYear" label="年度" width="80" align="center" />
        <el-table-column prop="periodMonth" label="月份" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeOf(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="beginDate" label="开始日期" width="130">
          <template #default="{ row }">{{ (row.beginDate || '').slice(0, 10) }}</template>
        </el-table-column>
        <el-table-column prop="endDate" label="结束日期" width="130">
          <template #default="{ row }">{{ (row.endDate || '').slice(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPerm('md:period:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 'CLOSED' && hasPerm('md:period:close')" link type="warning" @click="onClose(row)">结账</el-button>
            <el-button v-if="row.status === 'CLOSED' && hasPerm('md:period:close')" link type="success" @click="onReopen(row)">反结账</el-button>
            <el-button v-if="hasPerm('md:period:delete')" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <el-pagination
        class="pager"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- Create / Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑期间' : '新增期间'" width="480px">
      <el-form :model="form" label-width="80px" ref="formRef" :rules="formRules">
        <el-form-item label="所属公司" prop="companyId">
          <el-select v-model="form.companyId" filterable placeholder="选择公司主体" style="width:100%" :disabled="isEdit">
            <el-option v-for="c in companies" :key="c.id" :label="c.companyName || c.companyCode || ('公司' + c.id)" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="年度" prop="periodYear">
          <el-input-number v-model="form.periodYear" :min="2000" :max="2099" controls-position="right" />
        </el-form-item>
        <el-form-item label="月份" prop="periodMonth">
          <el-input-number v-model="form.periodMonth" :min="1" :max="12" controls-position="right" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.beginDate" type="date" value-format="YYYY-MM-DD" placeholder="留空按年月自动推算" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="留空按年月自动推算" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showRowDetail } from '@/utils/format'
import { periodApi, orgApi, store } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { exportCsv } from '../../utils/csv'
import { hasPerm } from '@/utils/perm'

// 41号审查 P1：批量开账/结账（多选行，仅对未结账/已结账行生效）
const selRows = ref([])
function handleSelectionChange(val) { selRows.value = val }

async function onBatchClose() {
  const targets = selRows.value.filter((r) => r.status !== 'CLOSED')
  if (!targets.length) return ElMessage.warning('所选期间均已结账')
  await ElMessageBox.confirm(`确认批量结账 ${targets.length} 个期间？结账后不可再录入数据。`, '批量结账', { type: 'warning' })
  let ok = 0, fail = 0
  for (const r of targets) {
    try { await periodApi.close(r.id); ok++ } catch { fail++ }
  }
  ElMessage[fail ? 'warning' : 'success'](`批量结账完成：成功 ${ok}，失败 ${fail}`)
  loadList()
}

async function onBatchReopen() {
  const targets = selRows.value.filter((r) => r.status === 'CLOSED')
  if (!targets.length) return ElMessage.warning('所选期间无已结账项')
  await ElMessageBox.confirm(`确认批量反结账 ${targets.length} 个期间？已关账期间将恢复开账。`, '批量反结账', { type: 'warning' })
  let ok = 0, fail = 0
  for (const r of targets) {
    try { await periodApi.reopen(r.id); ok++ } catch { fail++ }
  }
  ElMessage[fail ? 'warning' : 'success'](`批量反结账完成：成功 ${ok}，失败 ${fail}`)
  loadList()
}

// 41号审查 P1-9：期间导出
function doExport() {
  // R5-D-042：导出补公司列——导出时按词表实时翻译（list 只存 companyId，词表异步载入）
  const rows = list.value.map((r) => ({ ...r, companyNameText: companyName(r.companyId) }))
  exportCsv(rows, [
    { prop: 'periodCode', label: '期间编码' }, { prop: 'companyNameText', label: '公司' }, { prop: 'periodYear', label: '年度' }, { prop: 'periodMonth', label: '月份' },
    { prop: 'statusText', label: '状态' }, { prop: 'beginDate', label: '开始日期' }, { prop: 'endDate', label: '结束日期' },
    { prop: 'closeTime', label: '结账时间' },
  ], 'Period')
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
// R5-D-042：查询对象补 companyId（与 keyword 一并入服务端分页查询；服务端另有 data_scope 收口）
const query = reactive({ keyword: '', companyId: null, pageNum: 1, pageSize: 10 })

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  companyId: null,
  periodYear: new Date().getFullYear(),
  periodMonth: 1,
  beginDate: '',
  endDate: '',
})

const formRules = {
  companyId: [{ required: true, message: '请选择所属公司' }],
  periodYear: [{ required: true, message: '请输入年度' }],
  periodMonth: [{ required: true, message: '请输入月份' }],
}

// D-MD-PERIOD1：弹窗补公司主体字段，默认登录公司；跨公司主体亦可建档
// R5-D-042：同一份公司词表兼作 列表「公司」列的翻译源 + 工具栏公司筛选源（不另造一份清单）
const companies = ref([])
function flattenCompanies(nodes, out = []) {
  for (const n of nodes || []) {
    out.push(n)
    if (n.children && n.children.length) flattenCompanies(n.children, out)
  }
  return out
}
async function loadCompanies() {
  if (companies.value.length) return
  try {
    companies.value = flattenCompanies(await orgApi.companyTree() || [])
  } catch { /* 下拉为空时仍可保存，后端按登录主体回填 */ }
}

/** 公司名翻译（服务端只给 companyId；md_period 无 company_name 列，加列＝DDL，禁） */
function companyName(id) {
  if (id == null) return '—'
  const c = companies.value.find((x) => x.id === id)
  return c ? (c.companyName || c.companyCode || String(id)) : String(id)
}

/** 后端无 periodCode 字段，编码 = 年-月（两位补零） */
function periodCodeOf(row) {
  if (row.periodYear == null || row.periodMonth == null) return '-'
  return `${row.periodYear}-${String(row.periodMonth).padStart(2, '0')}`
}

function statusText(status) {
  return status === 'NORMAL' ? '开账' : status === 'CLOSED' ? '已结账' : status === 'DISABLED' ? '停用' : status || '-'
}

function statusTypeOf(status) {
  return status === 'NORMAL' ? 'success' : status === 'CLOSED' ? 'info' : 'warning'
}

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

async function loadList() {
  loading.value = true
  try {
    // R5-D-042：公司维度显式入参——筛选走服务端（md_period.company_id），不在前端按公司名截取；
    // 未选公司＝传空，由服务端 @DataScope + DataScopeKit 按登录人 data_scope 收口
    const res = await periodApi.page({
      keyword: query.keyword || undefined,
      companyId: query.companyId ?? undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    })
    list.value = (res.records || res.list || []).map((r) => ({
      ...r,
      periodCode: `${r.periodYear}-${String(r.periodMonth).padStart(2, '0')}`,
      statusText: r.status === 'NORMAL' ? '开账' : r.status === 'CLOSED' ? '已结账' : r.status === 'DISABLED' ? '停用' : (r.status || ''),
    }))
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function onQuery() {
  query.pageNum = 1
  loadList()
}

function resetForm() {
  Object.assign(form, { id: null, companyId: store.user?.companyId || null, periodYear: new Date().getFullYear(), periodMonth: 1, beginDate: '', endDate: '' })
}

function openCreate() {
  isEdit.value = false
  resetForm()
  loadCompanies()
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, { id: row.id, companyId: row.companyId ?? null, periodYear: row.periodYear, periodMonth: row.periodMonth, beginDate: (row.beginDate || '').slice(0, 10), endDate: (row.endDate || '').slice(0, 10) })
  loadCompanies()
  dialogVisible.value = true
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await periodApi.update({ ...form })
      ElMessage.success('更新成功')
    } else {
      await periodApi.create({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onClose(row) {
  await ElMessageBox.confirm(`确认结账期间 [${periodCodeOf(row)}]？结账后将不可再录入数据。`, '结账期间', { type: 'warning' })
  try {
    await periodApi.close(row.id)
    ElMessage.success('期间已结账')
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function onReopen(row) {
  await ElMessageBox.confirm(`确认反结账期间 [${periodCodeOf(row)}]？`, '反结账期间', { type: 'warning' })
  try {
    await periodApi.reopen(row.id)
    ElMessage.success('期间已反结账')
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除期间 [${periodCodeOf(row)}]？`, '删除确认', { type: 'warning' })
  try {
    await periodApi.delete(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(() => {
  // R5-D-042：公司词表与列表并行加载——词表只用于「公司」列名称翻译（服务端仅回 companyId）
  loadCompanies()
  loadList()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.pager {
  margin-top: 10px;
  justify-content: flex-end;
}
</style>
