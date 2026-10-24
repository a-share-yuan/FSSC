<template>
  <div class="org-page">
    <el-card>
      <!-- 左树右表联动布局 -->
      <el-row :gutter="16">
        <!-- 左侧：公司目录树 -->
        <el-col :span="7">
          <div class="left-panel">
            <div class="panel-header">
              <span class="panel-title">{{ $t('system.org.ttlCompanyTree') }}</span>
              <el-button v-permission="'system:company:create'" type="primary" size="small" @click="open('company', null)">{{ $t('system.common.btnAdd') }}</el-button>
            </div>
            <el-input v-model="companyFilter" :placeholder="$t('system.org.phSearchCompany')" clearable size="small" style="margin-bottom:8px" />
            <el-button size="small" @click="companyFilter = ''" style="margin-bottom:8px">{{ $t('common.reset') }}</el-button>
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
                    <el-button v-permission="'system:company:update'" link type="primary" size="small" @click.stop="open('company', data)">{{ $t('system.common.btnEdit') }}</el-button>
                    <el-button v-permission="'system:company:delete'" link type="danger" size="small" @click.stop="remove('company', data)">{{ $t('system.common.btnDelete') }}</el-button>
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
                  {{ $t('system.org.ttlDeptTree') }}
                  <el-tag v-if="selectedCompany" size="small" type="info" style="margin-left:8px">{{ selectedCompany.companyName }}</el-tag>
                  <el-text v-else size="small" type="info" style="margin-left:8px">{{ $t('system.org.hintPickCompanyLeft') }}</el-text>
                </span>
                <div>
                  <el-button v-permission="'system:dept:create'" type="primary" size="small" :disabled="!selectedCompany" @click="open('dept', null)">{{ $t('system.org.btnDeptAdd') }}</el-button>
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
                      <el-button v-permission="'system:dept:update'" link type="primary" size="small" @click.stop="open('dept', data)">{{ $t('system.common.btnEdit') }}</el-button>
                      <el-button v-permission="'system:dept:delete'" link type="danger" size="small" @click.stop="remove('dept', data)">{{ $t('system.common.btnDelete') }}</el-button>
                    </span>
                  </span>
                </template>
              </el-tree>
              <el-empty v-else :description="$t('system.org.hintPickCompany')" :image-size="60" />
            </div>

            <!-- 右侧下方：岗位列表 -->
            <div class="post-section">
              <div class="panel-header">
                <span class="panel-title">
                  {{ $t('system.org.ttlPostList') }}
                  <el-tag v-if="selectedDept" size="small" type="warning" style="margin-left:8px">{{ selectedDept.deptName }}</el-tag>
                  <el-text v-else-if="selectedCompany" size="small" type="info" style="margin-left:8px">{{ $t('system.org.textAllPosts', { v1: selectedCompany.companyName }) }}</el-text>
                  <el-text v-else size="small" type="info" style="margin-left:8px">{{ $t('system.org.hintCompanyFirst') }}</el-text>
                </span>
                <div>
                  <el-button v-permission="'system:post:create'" type="primary" size="small" :disabled="!selectedCompany" @click="open('post', null)">{{ $t('system.org.btnPostAdd') }}</el-button>
                  <el-button size="small" :disabled="!posts.length" @click="exportCsv">{{ $t('system.org.btnExportCsv') }}</el-button>
                </div>
              </div>
              <SkeletonList v-if="loading" :rows="8" :cols="5" />
              <el-table :data="pagedData" size="small" stripe v-else max-height="280">
                <el-table-column prop="postCode" :label="$t('system.common.colCode')" width="130" sortable />
                <el-table-column prop="postName" :label="$t('system.common.colName')" min-width="120" sortable />
                <el-table-column prop="sort" :label="$t('system.common.colSort')" width="80" sortable />
                <el-table-column prop="status" :label="$t('common.status')" width="90" sortable>
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'NORMAL' ? 'success' : 'info'" size="small">{{ row.status === 'NORMAL' ? t('system.common.stNormal') : t('system.common.btnDeactivate') }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="$t('common.operation')" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button v-permission="'system:post:update'" link type="primary" size="small" @click="open('post', row)">{{ $t('system.common.btnEdit') }}</el-button>
                    <el-button v-permission="'system:post:delete'" link type="danger" size="small" @click="remove('post', row)">{{ $t('system.common.btnDelete') }}</el-button>
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
    <el-dialog v-model="visible" :title="editingId ? t('system.common.btnEdit') : $t('system.org.optNewType', { v1: typeLabel })" width="520px" destroy-on-close>
      <el-form ref="orgFormRef" :model="form" :rules="orgFormRules" label-width="110px" size="default">
        <template v-if="type === 'company'">
          <el-form-item :label="$t('system.org.colCompanyName')" prop="companyName"><el-input v-model="form.companyName" /></el-form-item>
          <el-form-item :label="$t('system.org.colCompanyCode')" prop="companyCode"><el-input v-model="form.companyCode" /></el-form-item>
          <el-form-item :label="$t('system.org.lblParentCompany')">
            <el-select v-model="form.parentId" filterable clearable :placeholder="$t('system.org.optNoParentCompany')" style="width:100%">
              <el-option v-for="c in flatCompanies.filter((c) => c.id !== form.id)" :key="c.id" :label="c.companyName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('system.org.colCreditCode')"><el-input v-model="form.creditCode" /></el-form-item>
          <el-form-item :label="$t('system.org.colTaxNo')"><el-input v-model="form.taxNo" /></el-form-item>
          <!-- V1.4.92（AMC-01）：档案管理模式决定纸质原件是否寄送共享中心（非集中＝原件留原单位、不产生实物流） -->
          <el-form-item :label="$t('system.org.colArchiveMode')">
            <el-select v-model="form.archiveMode" style="width:100%">
              <el-option :label="$t('system.org.optArchiveCentral')" value="CENTRAL" />
              <el-option :label="$t('system.org.optArchiveNonCentral')" value="NON_CENTRAL" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('system.common.colSort')"><el-input-number v-model="form.sort" :min="0" style="width:100%" /></el-form-item>
        </template>
        <template v-else-if="type === 'dept'">
          <el-form-item :label="$t('system.org.colDeptName')" prop="deptName"><el-input v-model="form.deptName" /></el-form-item>
          <el-form-item :label="$t('system.org.colDeptCode')" prop="deptCode"><el-input v-model="form.deptCode" /></el-form-item>
          <el-form-item :label="$t('system.common.colCompany')" prop="companyId">
            <el-select v-model="form.companyId" filterable style="width:100%" :placeholder="$t('system.common.phSelectCompany')">
              <el-option v-for="c in flatCompanies" :key="c.id" :label="c.companyName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('system.org.lblParentDept')">
            <el-select v-model="form.parentId" filterable clearable :placeholder="$t('system.org.optNoParentDept')" style="width:100%">
              <el-option v-for="d in flatDepts.filter((d) => d.id !== form.id && d.companyId === form.companyId)" :key="d.id" :label="d.deptName" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('system.common.colSort')"><el-input-number v-model="form.sort" :min="0" style="width:100%" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item :label="$t('system.org.colPostName')" prop="postName"><el-input v-model="form.postName" /></el-form-item>
          <el-form-item :label="$t('system.org.colPostCode')" prop="postCode"><el-input v-model="form.postCode" /></el-form-item>
          <el-form-item :label="$t('system.common.colCompany')" prop="companyId">
            <el-select v-model="form.companyId" filterable style="width:100%" :placeholder="$t('system.common.phSelectCompany')" @change="onFormCompanyChange">
              <el-option v-for="c in flatCompanies" :key="c.id" :label="c.companyName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('system.common.colDept')">
            <el-select v-model="form.deptId" filterable clearable style="width:100%" :placeholder="$t('system.org.optNoDept')">
              <el-option v-for="d in flatDepts.filter((d) => d.companyId === form.companyId)" :key="d.id" :label="d.deptName" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('system.common.colSort')"><el-input-number v-model="form.sort" :min="0" style="width:100%" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ $t('common.save') }}</el-button>
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
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
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
const typeLabel = ref(t('field.company'))
const form = reactive({ companyName: '', companyCode: '', parentId: 0, creditCode: '', taxNo: '', archiveMode: 'CENTRAL', sort: 0, deptName: '', deptCode: '', companyId: 0, parentId_dept: 0, postName: '', postCode: '', deptId: 0 })

const orgFormRef = ref(null)
// 公司/部门下拉「未选」在本页是 0 而不是 null，required 校验器会放行 0 ⇒ 显式把 0 也判为未选
const requirePicked = (msg) => [{
  required: true,
  message: msg,
  validator: (rule, value, cb) => (!value || Number(value) === 0 ? cb(new Error(msg)) : cb()),
}]
const orgFormRules = computed(() => (reactive({
  // 与后端 SysCompany/SysDept/SysPost 服务同名断言（批次 2C）
  companyName: commonRules.required(t('system.org.errCompanyNameRequired')),
  companyCode: commonRules.required(t('system.org.errCompanyCodeRequired')),
  deptName: commonRules.required(t('system.org.errDeptNameRequired')),
  deptCode: commonRules.required(t('system.org.errDeptCodeRequired')),
  companyId: requirePicked(t('system.org.errCompanyRequired')),
  postName: commonRules.required(t('system.org.errPostNameRequired')),
  postCode: commonRules.required(t('system.org.errPostCodeRequired')),
})))

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

function open(kind, row) {
  type.value = kind
  typeLabel.value = kind === 'company' ? t('field.company') : kind === 'dept' ? t('field.dept') : t('system.org.optPost')
  editingId.value = row ? row.id : null
  Object.assign(form, {
    companyName: '', companyCode: '', parentId: 0, creditCode: '', taxNo: '', archiveMode: 'CENTRAL', sort: 0,
    deptName: '', deptCode: '', companyId: 0, parentId_dept: 0,
    postName: '', postCode: '', deptId: 0
  })
  // 新增部门时默认带入当前选中公司
  if (kind === 'dept' && !row && selectedCompany.value) {
    form.companyId = selectedCompany.value.id
  }
  // 新增岗位时默认带入当前选中公司/部门
  if (kind === 'post' && !row) {
    if (selectedCompany.value) form.companyId = selectedCompany.value.id
    if (selectedDept.value) form.deptId = selectedDept.value.id
  }
  if (row) {
    if (kind === 'company') {
      form.companyName = row.companyName || ''; form.companyCode = row.companyCode || ''
      form.parentId = row.parentId ?? 0; form.creditCode = row.creditCode || ''
      form.taxNo = row.taxNo || ''; form.sort = row.sort ?? 0
      form.archiveMode = row.archiveMode || 'CENTRAL'
    } else if (kind === 'dept') {
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
    const kind = type.value
    const isEdit = !!editingId.value
    if (kind === 'company') {
      const body = { companyName: form.companyName, companyCode: form.companyCode, parentId: form.parentId, creditCode: form.creditCode, taxNo: form.taxNo, archiveMode: form.archiveMode || 'CENTRAL', sort: form.sort }
      if (isEdit) await orgApi.companyUpdate({ id: editingId.value, ...body })
      else await orgApi.companyCreate(body)
    } else if (kind === 'dept') {
      const body = { deptName: form.deptName, deptCode: form.deptCode, companyId: form.companyId, parentId: form.parentId ?? 0, sort: form.sort }
      if (isEdit) await orgApi.deptUpdate({ id: editingId.value, ...body })
      else await orgApi.deptCreate(body)
    } else {
      const body = { postName: form.postName, postCode: form.postCode, companyId: form.companyId, deptId: form.deptId, sort: form.sort }
      if (isEdit) await orgApi.postUpdate({ id: editingId.value, ...body })
      else await orgApi.postCreate(body)
    }
    ElMessage.success(t('system.common.msgSaved'))
    visible.value = false
    // 刷新当前视图
    if (kind === 'company') { await loadCompanyTree() }
    else if (kind === 'dept' && selectedCompany.value) { await loadDeptAndPosts(selectedCompany.value.id) }
    else if (kind === 'post' && selectedDept.value) { await onDeptSelect(selectedDept.value) }
    else if (kind === 'post' && selectedCompany.value) { await loadPosts(selectedCompany.value.id, null) }
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

async function remove(kind, row) {
  try {
    await ElMessageBox.confirm(t('system.org.askDeleteEntity', { v1: typeLabel.value }), t('system.common.ttlDelete'), { type: 'warning' })
    if (kind === 'company') await orgApi.companyDelete(row.id)
    else if (kind === 'dept') await orgApi.deptDelete(row.id)
    else await orgApi.postDelete(row.id)
    ElMessage.success(t('system.common.stDeleted'))
    if (kind === 'company') { selectedCompany.value = null; deptTreeData.value = []; posts.value = []; await loadCompanyTree() }
    else if (kind === 'dept' && selectedCompany.value) { selectedDept.value = null; await loadDeptAndPosts(selectedCompany.value.id) }
    else if (kind === 'post' && selectedDept.value) { await onDeptSelect(selectedDept.value) }
    else if (kind === 'post' && selectedCompany.value) { await loadPosts(selectedCompany.value.id, null) }
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
  if (!posts.value.length) { ElMessage.warning(t('system.common.msgNoExportData')); return }
  const cols = [{ prop: 'postCode', label: t('system.common.colCode') }, { prop: 'postName', label: t('system.common.colName') }, { prop: 'sort', label: t('system.common.colSort') }, { prop: 'status', label: t('common.status') }]
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
  ElMessage.success(t('system.common.msgExportedCsv'))
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
