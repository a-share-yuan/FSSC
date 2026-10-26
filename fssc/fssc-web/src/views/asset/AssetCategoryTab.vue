<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <div style="display: flex; gap: 8px; margin-bottom: 10px">
      <el-button type="success" @click="openCategory()">新增类别</el-button>
      <el-button @click="doExport">导出</el-button>
      <el-button v-if="selRows.length" type="success" plain @click="exportSel">导出选中({{ selRows.length }})</el-button>
    </div>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="pagedData" size="small" border stripe v-else empty-text="暂无数据" v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="categoryCode" label="类别编码" width="140" sortable />
      <el-table-column prop="categoryName" label="类别名称" min-width="160" sortable />
      <el-table-column label="上级类别" width="130">
        <template #default="{ row }">{{ row.parentId ? categoryName(row.parentId) : '\u2014\u3010\u9876\u7ea7\u3011' }}</template>
      </el-table-column>
      <el-table-column prop="depreciationMonths" label="默认折旧(月)" width="120" sortable />
      <el-table-column label="默认残值率" width="110" align="right">
        <template #default="{ row }">{{ row.residualRate ? (row.residualRate * 100).toFixed(0) + '%' : '\u2014' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openCategory(row)">编辑</el-button>
          <el-button link size="small" type="danger" @click="delCategory(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TablePager :pager="pager" @change="loadCategories" />
  </div>

  <!-- 资产类别弹窗 -->
  <el-dialog v-model="dlgCategory" :title="cForm.id ? '编辑类别' : '新增类别'" width="520px" destroy-on-close>
    <el-form ref="categoryFormRef" :model="cForm" :rules="formRules" label-width="130px" size="small">
      <el-form-item label="类别编码" prop="categoryCode"><el-input v-model="cForm.categoryCode" /></el-form-item>
      <el-form-item label="类别名称" prop="categoryName"><el-input v-model="cForm.categoryName" /></el-form-item>
      <el-form-item label="上级类别">
        <el-select v-model="cForm.parentId" filterable clearable placeholder="不选则为顶级类别" style="width:100%">
          <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="默认折旧月数"><el-input-number v-model="cForm.depreciationMonths" :min="1" style="width:100%" /></el-form-item>
      <el-form-item label="默认残值率">
        <el-input-number v-model="cForm.residualRate" :min="0" :max="1" :step="0.05" :precision="2" style="width:100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dlgCategory = false">取消</el-button>
      <el-button type="primary" @click="saveCategory">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, inject, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showRowDetail } from '@/utils/format'
import { assetApi } from '../../api'
import { exportCsv } from '../../utils/csv'
import SkeletonList from '../../components/SkeletonList.vue'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'

const { categories, categoryName, loading } = inject('assetShared')

const pager = useTablePager({ mode: 'server', storageKey: 'asset_category' })
const { pagedData } = pager

const selRows = ref([])
const dlgCategory = ref(false)
const cForm = ref({ id: null, categoryCode: '', categoryName: '', parentId: null, depreciationMonths: 60, residualRate: 0.05 })

// 表单校验规则：取自中央登记表（useFormRules），页面不再本地手写规则对象
const categoryFormRef = ref(null)
const { rules: formRules } = useFormRules('ASSET_CATEGORY')
const { submit: guardSave } = useSubmitGuard(categoryFormRef)

async function loadCategories() {
  try {
    await pager.load(async ({ pageNum, pageSize }) => {
      const r = await assetApi.categoryPage({ pageNum, pageSize })
      categories.value = r.list || []
      return { list: r.list || [], total: r.total || 0 }
    })
  } catch (e) { ElMessage.error(e.message) }
}

async function openCategory(row) { cForm.value = row ? { ...row } : { id: null, categoryCode: '', categoryName: '', parentId: null, depreciationMonths: 60, residualRate: 0.05 }; dlgCategory.value = true }
async function saveCategory() {
  await guardSave(async () => {
    try {
      if (cForm.value.id) { await assetApi.categoryUpdate(cForm.value) } else { await assetApi.categoryCreate(cForm.value) }
      ElMessage.success('已保存'); dlgCategory.value = false; loadCategories()
    } catch (e) { ElMessage.error(e.message) }
  })
}
async function delCategory(row) {
  try { await ElMessageBox.confirm('确认删除类别「' + row.categoryName + '」？', '提示', { type: 'warning' }); await assetApi.categoryDelete(row.id); ElMessage.success('已删除'); loadCategories() } catch (e) { /* cancelled */ }
}

function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

function handleSelectionChange(val) { selRows.value = val }

const CATEGORY_COLS = [
  { prop: 'categoryCode', label: '类别编码' }, { prop: 'categoryName', label: '类别名称' },
  { prop: 'parentId', label: '上级类别ID' }, { prop: 'depreciationMonths', label: '默认折旧(月)' },
  { prop: 'residualRate', label: '默认残值率' },
]

// 41号审查 P1-9：资产类别导出（当前页/选中）
function doExport() { exportCsv(categories.value, CATEGORY_COLS, 'AssetCategory') }
function exportSel() { exportCsv(selRows.value, CATEGORY_COLS, 'AssetCategory_Sel') }

onMounted(() => { loadCategories() })

defineExpose({ loadCategories })
</script>
