<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="org-page">
    <el-card>
      <!-- 左树右表联动布局 -->
      <el-row :gutter="16">
        <!-- 左侧：公司目录树 -->
        <el-col :span="7">
          <div class="left-panel">
            <div class="panel-header">
              <span class="panel-title">公司目录树</span>
              <el-button v-permission="'system:company:create'" type="primary" size="small" @click="open('company', null)">新增</el-button>
            </div>
            <el-input v-model="companyFilter" placeholder="搜索公司..." clearable size="small" style="margin-bottom:8px" />
            <el-button size="small" @click="companyFilter = ''" style="margin-bottom:8px">重置</el-button>
            <el-tree
              ref="companyTreeRef"
              :data="companyTree"
              node-key="id"
              :props="{ label: 'companyName', children: 'children' }"
              :filter-node-method="filterCompanyNode"
              default-expand-all
              highlight-current
              @node-click="onCompanySelect"
            >
              <template #default="{ data }">
                <span class="tree-node">
                  <span>{{ data.companyName }} <small style="color:#999">{{ data.companyCode }}</small></span>
                  <span class="node-ops">
                    <el-button v-permission="'system:company:update'" link type="primary" size="small" @click.stop="open('company', data)">编辑</el-button>
                    <el-button v-permission="'system:company:delete'" link type="danger" size="small" @click.stop="remove('company', data)">删除</el-button>
                  </span>
                </span>
              </template>
            </el-tree>
          </div>
        </el-col>

        <!-- 右侧：部门目录树 + 岗位列表 -->
        <el-col :span="17">
          <div class="right-panel">
            <!-- 右侧上方：部门目录树 -->
            <div class="dept-section">
              <div class="panel-header">
                <span class="panel-title">
                  部门目录树
                  <el-tag v-if="selectedCompany" size="small" type="info" style="margin-left:8px">{{ selectedCompany.companyName }}</el-tag>
                  <el-text v-else size="small" type="info" style="margin-left:8px">请从左侧选择公司</el-text>
                </span>
                <div>
                  <el-button v-permission="'system:dept:create'" type="primary" size="small" :disabled="!selectedCompany" @click="open('dept', null)">新增部门</el-button>
                </div>
              </div>
              <el-tree
                v-if="deptTreeData.length"
                :data="deptTreeData"
                node-key="id"
                :props="{ label: 'deptName', children: 'children' }"
                default-expand-all
                highlight-current
                @node-click="onDeptSelect"
              >
                <template #default="{ data }">
                  <span class="tree-node">
                    <span>{{ data.deptName }} <small style="color:#999">{{ data.deptCode }}</small></span>
                    <span class="node-ops">
                      <el-button v-permission="'system:dept:update'" link type="primary" size="small" @click.stop="open('dept', data)">编辑</el-button>
                      <el-button v-permission="'system:dept:delete'" link type="danger" size="small" @click.stop="remove('dept', data)">删除</el-button>
                    </span>
                  </span>
                </template>
              </el-tree>
              <el-empty v-else description="请从左侧选择公司后查看部门" :image-size="60" />
            </div>

            <!-- 右侧下方：岗位列表 -->
            <div class="post-section">
              <div class="panel-header">
                <span class="panel-title">
                  岗位列表
                  <el-tag v-if="selectedDept" size="small" type="warning" style="margin-left:8px">{{ selectedDept.deptName }}</el-tag>
                  <el-text v-else-if="selectedCompany" size="small" type="info" style="margin-left:8px">{{ selectedCompany.companyName }} 全部岗位</el-text>
                  <el-text v-else size="small" type="info" style="margin-left:8px">请先选择公司</el-text>
                </span>
                <div>
                  <el-button v-permission="'system:post:create'" type="primary" size="small" :disabled="!selectedCompany" @click="open('post', null)">新增岗位</el-button>
                  <el-button size="small" :disabled="!posts.length" @click="exportCsv">导出CSV</el-button>
                </div>
              </div>
              <SkeletonList v-if="loading" :rows="8" :cols="5" />
              <el-table :data="pagedData" size="small" stripe v-else max-height="280">
                <el-table-column prop="postCode" label="编码" width="130" sortable />
                <el-table-column prop="postName" label="名称" min-width="120" sortable />
                <el-table-column prop="sort" label="排序" width="80" sortable />
                <el-table-column prop="status" label="状态" width="90" sortable>
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'NORMAL' ? 'success' : 'info'" size="small">{{ row.status === 'NORMAL' ? '正常' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button v-permission="'system:post:update'" link type="primary" size="small" @click="open('post', row)">编辑</el-button>
                    <el-button v-permission="'system:post:delete'" link type="danger" size="small" @click="remove('post', row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <TablePager :pager="pager" @change="loadPostsCurrent" />
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="visible" :title="editingId ? '编辑' : '新增' + typeLabel" width="520px" destroy-on-close>
      <el-form ref="orgFormRef" :model="form" :rules="orgFormRules" label-width="110px" size="default">
        <template v-if="type === 'company'">
          <el-form-item label="公司名称" prop="companyName"><el-input v-model="form.companyName" /></el-form-item>
          <el-form-item label="公司编码" prop="companyCode"><el-input v-model="form.companyCode" /></el-form-item>
          <el-form-item label="上级公司">
            <el-select v-model="form.parentId" filterable clearable placeholder="无上级公司（顶级）" style="width:100%">
              <el-option v-for="c in flatCompanies.filter((c) => c.id !== form.id)" :key="c.id" :label="c.companyName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="统一信用代码"><el-input v-model="form.creditCode" /></el-form-item>
          <el-form-item label="纳税人识别号"><el-input v-model="form.taxNo" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" style="width:100%" /></el-form-item>
        </template>
        <template v-else-if="type === 'dept'">
          <el-form-item label="部门名称" prop="deptName"><el-input v-model="form.deptName" /></el-form-item>
          <el-form-item label="部门编码" prop="deptCode"><el-input v-model="form.deptCode" /></el-form-item>
          <el-form-item label="所属公司" prop="companyId">
            <el-select v-model="form.companyId" filterable style="width:100%" placeholder="选择公司">
              <el-option v-for="c in flatCompanies" :key="c.id" :label="c.companyName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="上级部门">
            <el-select v-model="form.parentId" filterable clearable placeholder="无上级部门（顶级）" style="width:100%">
              <el-option v-for="d in flatDepts.filter((d) => d.id !== form.id && d.companyId === form.companyId)" :key="d.id" :label="d.deptName" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" style="width:100%" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="岗位名称" prop="postName"><el-input v-model="form.postName" /></el-form-item>
          <el-form-item label="岗位编码" prop="postCode"><el-input v-model="form.postCode" /></el-form-item>
          <el-form-item label="所属公司" prop="companyId">
            <el-select v-model="form.companyId" filterable style="width:100%" placeholder="选择公司" @change="onFormCompanyChange">
              <el-option v-for="c in flatCompanies" :key="c.id" :label="c.companyName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属部门">
            <el-select v-model="form.deptId" filterable clearable style="width:100%" placeholder="不限部门（公司级岗位）">
              <el-option v-for="d in flatDepts.filter((d) => d.companyId === form.companyId)" :key="d.id" :label="d.deptName" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" style="width:100%" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>

import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orgApi, store } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { useKeyboard } from '@/composables/useKeyboard'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => loadCompanyTree(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})

// ========== 左树右表联动状态 ==========
const companyTreeRef = ref()
const companyFilter = ref('')
const companyTree = ref([])
const selectedCompany = ref(null)
const selectedDept = ref(null)
const deptTreeData = ref([])
const posts = ref([])
// P0-2: 服务端分页（后端 postPage 返回 PageResult，前端传 pageNum/pageSize 用后端 total）
const pager = useTablePager({ mode: 'server', storageKey: 'system_org' })
const { pagedData } = pager
// 记录最近一次筛选条件，供 TablePager @change 翻页/改每页条数时复用
const lastCompanyId = ref(null)
const lastDeptId = ref(null)
const loading = ref(false)

/**
 * 服务端分页加载岗位列表。companyId/deptId 二选一（后端 postPage 按条件过滤）。
 */
async function loadPosts(companyId, deptId) {
  lastCompanyId.value = companyId ?? null
  lastDeptId.value = deptId ?? null
  loading.value = true
  try {
    await pager.load(async ({ pageNum, pageSize }) => {
      const r = await orgApi.postPage({ pageNum, pageSize, companyId, deptId })
      posts.value = r.list || []
      return { list: r.list || [], total: r.total || 0 }
    })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}
function loadPostsCurrent() { return loadPosts(lastCompanyId.value, lastDeptId.value) }

const flatCompanies = computed(() => {
  const out = []
  const walk = (nodes) => {
    for (const n of nodes || []) { out.push(n); if (n.children?.length) walk(n.children) }
  }
  walk(companyTree.value)
  return out
})

// 扁平化部门（表单下拉用，需先加载全量部门或按公司缓存）
const flatDepts = ref([])

const visible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const type = ref('company')
const typeLabel = ref('公司')
const form = reactive({ companyName: '', companyCode: '', parentId: 0, creditCode: '', taxNo: '', sort: 0, deptName: '', deptCode: '', companyId: 0, parentId_dept: 0, postName: '', postCode: '', deptId: 0 })

const orgFormRef = ref(null)
// 公司/部门下拉「未选」在本页是 0 而不是 null，required 校验器会放行 0 ⇒ 显式把 0 也判为未选
const requirePicked = (msg) => [{
  required: true,
  message: msg,
  validator: (rule, value, cb) => (!value || Number(value) === 0 ? cb(new Error(msg)) : cb()),
}]
const orgFormRules = reactive({
  // 与后端 SysCompany/SysDept/SysPost 服务同名断言（批次 2C）
  companyName: commonRules.required('请输入公司名称'),
  companyCode: commonRules.required('请输入公司编码'),
  deptName: commonRules.required('请输入部门名称'),
  deptCode: commonRules.required('请输入部门编码'),
  companyId: requirePicked('请选择公司'),
  postName: commonRules.required('请输入岗位名称'),
  postCode: commonRules.required('请输入岗位编码'),
})

// ========== 数据加载 ==========

async function loadCompanyTree() {
  try {
    const ct = await orgApi.companyTree()
    companyTree.value = buildDeptTree(ct || [])
  } catch (e) { ElMessage.error(e.message) }
}

async function onCompanySelect(node) {
  selectedCompany.value = node
  selectedDept.value = null
  await loadDeptAndPosts(node.id)
}

async function loadDeptAndPosts(companyId) {
  if (!companyId) { deptTreeData.value = []; posts.value = []; lastCompanyId.value = null; lastDeptId.value = null; return }
  loading.value = true
  try {
    const dt = await orgApi.deptTree(companyId)
    deptTreeData.value = buildDeptTree(dt || [])
    flatDepts.value = dt || []
    await loadPosts(companyId, null)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function onDeptSelect(node) {
  selectedDept.value = node
  if (!node) { await loadPosts(selectedCompany.value?.id || null, null); return }
  await loadPosts(null, node.id)
}

/** 将平铺部门列表构建为树 */
function buildDeptTree(list) {
  const map = {}
  const roots = []
  for (const d of list) { map[d.id] = { ...d, children: [] } }
  for (const d of list) {
    const node = map[d.id]
    if (d.parentId && map[d.parentId]) {
      map[d.parentId].children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
}

// ========== 公司树搜索过滤 ==========
watch(companyFilter, (val) => {
  companyTreeRef.value?.filter(val)
})
function filterCompanyNode(value, data) {
  if (!value) return true
  return data.companyName?.includes(value) || data.companyCode?.includes(value)
}

// ========== 弹窗 ==========

function open(t, row) {
  type.value = t
  typeLabel.value = t === 'company' ? '公司' : t === 'dept' ? '部门' : '岗位'
  editingId.value = row ? row.id : null
  Object.assign(form, {
    companyName: '', companyCode: '', parentId: 0, creditCode: '', taxNo: '', sort: 0,
    deptName: '', deptCode: '', companyId: 0, parentId_dept: 0,
    postName: '', postCode: '', deptId: 0
  })
  // 新增部门时默认带入当前选中公司
  if (t === 'dept' && !row && selectedCompany.value) {
    form.companyId = selectedCompany.value.id
  }
  // 新增岗位时默认带入当前选中公司/部门
  if (t === 'post' && !row) {
    if (selectedCompany.value) form.companyId = selectedCompany.value.id
    if (selectedDept.value) form.deptId = selectedDept.value.id
  }
  if (row) {
    if (t === 'company') {
      form.companyName = row.companyName || ''; form.companyCode = row.companyCode || ''
      form.parentId = row.parentId ?? 0; form.creditCode = row.creditCode || ''
      form.taxNo = row.taxNo || ''; form.sort = row.sort ?? 0
    } else if (t === 'dept') {
      form.deptName = row.deptName || ''; form.deptCode = row.deptCode || ''
      form.companyId = row.companyId ?? 0; form.parentId = row.parentId ?? 0; form.sort = row.sort ?? 0
    } else {
      form.postName = row.postName || ''; form.postCode = row.postCode || ''
      form.companyId = row.companyId ?? 0; form.deptId = row.deptId ?? 0; form.sort = row.sort ?? 0
    }
  }
  visible.value = true
}

function onFormCompanyChange() {
  form.deptId = 0
}

async function save() {
  if (!(await validateForms([orgFormRef]))) return
  saving.value = true
  try {
    const t = type.value
    const isEdit = !!editingId.value
    if (t === 'company') {
      const body = { companyName: form.companyName, companyCode: form.companyCode, parentId: form.parentId, creditCode: form.creditCode, taxNo: form.taxNo, sort: form.sort }
      if (isEdit) await orgApi.companyUpdate({ id: editingId.value, ...body })
      else await orgApi.companyCreate(body)
    } else if (t === 'dept') {
      const body = { deptName: form.deptName, deptCode: form.deptCode, companyId: form.companyId, parentId: form.parentId ?? 0, sort: form.sort }
      if (isEdit) await orgApi.deptUpdate({ id: editingId.value, ...body })
      else await orgApi.deptCreate(body)
    } else {
      const body = { postName: form.postName, postCode: form.postCode, companyId: form.companyId, deptId: form.deptId, sort: form.sort }
      if (isEdit) await orgApi.postUpdate({ id: editingId.value, ...body })
      else await orgApi.postCreate(body)
    }
    ElMessage.success('保存成功')
    visible.value = false
    // 刷新当前视图
    if (t === 'company') { await loadCompanyTree() }
    else if (t === 'dept' && selectedCompany.value) { await loadDeptAndPosts(selectedCompany.value.id) }
    else if (t === 'post' && selectedDept.value) { await onDeptSelect(selectedDept.value) }
    else if (t === 'post' && selectedCompany.value) { await loadPosts(selectedCompany.value.id, null) }
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

async function remove(t, row) {
  try {
    await ElMessageBox.confirm(`确认删除该${typeLabel.value}？`, '删除确认', { type: 'warning' })
    if (t === 'company') await orgApi.companyDelete(row.id)
    else if (t === 'dept') await orgApi.deptDelete(row.id)
    else await orgApi.postDelete(row.id)
    ElMessage.success('已删除')
    if (t === 'company') { selectedCompany.value = null; deptTreeData.value = []; posts.value = []; await loadCompanyTree() }
    else if (t === 'dept' && selectedCompany.value) { selectedDept.value = null; await loadDeptAndPosts(selectedCompany.value.id) }
    else if (t === 'post' && selectedDept.value) { await onDeptSelect(selectedDept.value) }
    else if (t === 'post' && selectedCompany.value) { await loadPosts(selectedCompany.value.id, null) }
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// R5-D-045：进页默认选中当前用户所属公司（loadCompanyTree 完成后联动右侧）
onMounted(async () => {
  await loadCompanyTree()
  const cid = store.user?.companyId
  if (cid == null) return
  const hit = flatCompanies.value.find((c) => String(c.id) === String(cid))
  if (hit) await onCompanySelect(hit)
})

function exportCsv() {
  if (!posts.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'postCode', label: '编码' }, { prop: 'postName', label: '名称' }, { prop: 'sort', label: '排序' }, { prop: 'status', label: '状态' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = posts.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `Org_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}
</script>

<style scoped>
.org-page { min-height: 100%; }
.left-panel { border-right: 1px solid #e4e7ed; padding-right: 12px; min-height: 500px; }
.right-panel { padding-left: 4px; }
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.panel-title { font-size: 15px; font-weight: 600; color: #303133; }
.dept-section { margin-bottom: 24px; }
.post-section { border-top: 1px solid #e4e7ed; padding-top: 16px; }
.tree-node { display: flex; justify-content: space-between; align-items: center; width: 100%; padding-right: 12px; }
.node-ops { visibility: hidden; }
.tree-node:hover .node-ops { visibility: visible; }
</style>
