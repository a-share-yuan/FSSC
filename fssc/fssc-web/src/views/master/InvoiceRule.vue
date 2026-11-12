<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <!-- 说明提示 -->
    <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap">
      <span style="color: var(--app-text-secondary, #606266); font-size: 13px">{{ $t('master.invoiceRule.When') }}</span>
      <span style="color: #909399; font-size: 12px; margin-left: auto">{{ $t('master.invoiceRule.Higher') }}</span>
    </div>

    <!-- Toolbar -->
    <div style="display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; align-items: center">
      <el-input v-model="keyword" :placeholder="$t('master.invoiceRule.Search')" clearable style="width: 240px" @keyup.enter="load" />
      <el-button type="primary" @click="load">{{ $t('common.search') }}</el-button>
      <el-button @click="keyword = ''; load()">{{ $t('common.reset') }}</el-button>
      <el-button type="success" @click="openCreate">{{ $t('common.add') }}</el-button>
      <el-button type="primary" plain @click="load">{{ $t('common.refresh') }}</el-button>
    </div>

    <!-- Table -->
    <SkeletonList v-if="loading" :rows="8" :cols="6" />
    <el-table :data="rows" size="small" border stripe v-else :empty-text="$t('master.invoiceRule.No')">
      <el-table-column :label="$t('master.invoiceRule.Keyword')" min-width="160">
        <template #default="{ row }"><span style="font-family: monospace">{{ row.keyword }}</span></template>
      </el-table-column>
      <el-table-column label="匹配方式" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.matchType === 'EXACT' ? 'warning' : ''">{{ row.matchType === 'EXACT' ? '精确' : '包含' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="费用类型" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.expenseItemId">{{ row.itemName }} <span style="color: #909399; font-size: 12px">(#{{ row.expenseItemId }})</span></span>
          <el-tag v-else size="small" type="info">未绑定</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="(row.status || 'NORMAL') === 'NORMAL' ? 'success' : 'danger'">{{ (row.status || 'NORMAL') === 'NORMAL' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link size="small" :type="(row.status || 'NORMAL') === 'NORMAL' ? 'warning' : 'success'" @click="toggleEnable(row)">{{ (row.status || 'NORMAL') === 'NORMAL' ? '停用' : '启用' }}</el-button>
          <el-button link size="small" type="danger" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-if="total > pageSize" class="pager" background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="pageNum" @current-change="(p) => { pageNum = p; load() }" />

    <!-- Add / Edit Dialog -->
    <el-dialog v-model="dlg" :title="editing.id ? '编辑规则' : '新增规则'" width="560px">
      <el-form ref="formRef" :model="editing" :rules="formRules" label-width="110px" size="small">
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="editing.keyword" placeholder="多个用 / 分隔，如：打车 / 出租 / 滴滴" />
        </el-form-item>
        <el-form-item label="匹配方式" prop="matchType">
          <el-select v-model="editing.matchType" style="width: 100%">
            <el-option label="包含（货名里含有即命中）" value="CONTAINS" />
            <el-option label="精确（货名完全一致才命中）" value="EXACT" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用类型" prop="expenseItemId">
          <el-select v-model="editing.expenseItemId" filterable clearable placeholder="请选择费用类型（不选则本条规则只展示不匹配）" style="width: 100%">
            <el-option v-for="it in itemOptions" :key="it.value" :label="it.label" :value="it.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="editing.sort" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用" prop="status">
          <el-switch v-model="editing.status" active-value="NORMAL" inactive-value="DISABLED" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editing.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { masterApi, mdApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { useI18n } from 'vue-i18n'
import { validateForm } from '@/composables/useFormRules';

const { t } = useI18n()

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const loading = ref(false)
const dlg = ref(false)
const editing = ref({})
const formRef = ref(null)
const itemOptions = ref([])

const formRules = {
  keyword: [{ required: true, message: t('master.invoiceRule.Please') }],
  // 费用类型可空：后端 InvoiceRuleService 明确允许置空（用于解除绑定），
  // 界面占位也写明「不选则本条规则只展示不匹配」⇒ 此处不得标必填，否则红星与业务规则相反
}

async function load() {
  loading.value = true
  try {
    const d = await masterApi.invoiceRule.page({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value })
    rows.value = d.list || []
    total.value = Number(d.total || 0)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadItems() {
  // 费用类型树 → 扁平可搜下拉（缩进展示层级）
  try {
    const list = await mdApi.expenseItems() || []
    // 后端返回扁平列表（祖先路径+排序），前端按 parentId 组树后缩进展示
    const map = {}
    list.forEach((n) => { map[n.id] = { ...n, children: [] } })
    const roots = []
    list.forEach((n) => {
      const node = map[n.id]
      if (n.parentId && map[n.parentId]) map[n.parentId].children.push(node)
      else roots.push(node)
    })
    const flat = []
    const walk = (arr, depth) => { arr.forEach((n) => {
      flat.push({ value: n.id, label: ('　'.repeat(depth)) + (n.itemName || n.name || '') })
      if (n.children && n.children.length) walk(n.children, depth + 1)
    }) }
    walk(roots, 0)
    itemOptions.value = flat
  } catch (e) { itemOptions.value = [] }
}

function openCreate() {
  editing.value = { keyword: '', matchType: 'CONTAINS', expenseItemId: null, itemName: '', sort: 0, status: 'NORMAL', remark: '' }
  dlg.value = true
}
function openEdit(row) {
  editing.value = { ...row, itemName: row.itemName || '' }
  dlg.value = true
}

async function save() {
  // 关键词是匹配的唯一依据，空关键词的规则永远匹配不到任何单据 ⇒ 保存前先校验
  if (!(await validateForm(formRef))) return;
  const it = itemOptions.value.find((o) => o.value === editing.value.expenseItemId)
  editing.value.itemName = it ? it.label.replace(/^\s*　*/, '') : ''
  try {
    if (editing.value.id) { await masterApi.invoiceRule.update(editing.value) } else { await masterApi.invoiceRule.create(editing.value) }
    ElMessage.success(t('useCrud.main.Saved')); dlg.value = false; load()
  } catch (e) { ElMessage.error(e.message) }
}

async function del(row) {
  try {
    await ElMessageBox.confirm('确认删除该规则？', '提示', { type: 'warning' })
    await masterApi.invoiceRule.delete(row.id)
    ElMessage.success(t('useCrud.main.Deleted')); load()
  } catch (e) { /* cancelled */ }
}

async function toggleEnable(row) {
  try {
    const on = (row.status || 'NORMAL') === 'NORMAL'
    await masterApi.invoiceRule.enable(row.id, !on)
    row.status = !on ? 'NORMAL' : 'DISABLED'
    ElMessage.success(t('master.invoiceRule.Enabled'))
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(() => { loadItems(); load() })
</script>

<style scoped>
.pager { margin-top: 10px; justify-content: flex-end; }
</style>
