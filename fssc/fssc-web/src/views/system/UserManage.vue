<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="user-manage">
    <el-row :gutter="12">
      <!-- ============ 左侧：机构目录树（公司 → 部门，左树右表） ============ -->
      <el-col :span="5">
        <el-card shadow="never" class="org-tree-card">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">{{ $t('system.user.ttlOrgCatalog') }}</span>
              <el-button v-if="selectedOrg" link type="primary" size="small" @click="clearOrgFilter">{{ $t('common.all') }}</el-button>
            </div>
          </template>
          <el-input v-model="orgKeyword" :placeholder="$t('system.user.phSearchOrg')" size="small" clearable style="margin-bottom:8px" />
          <el-tree
            ref="orgTreeRef"
            :data="orgTree"
            node-key="key"
            :props="{ label: 'label', children: 'children' }"
            :filter-node-method="filterOrgNode"
            highlight-current
            :expand-on-click-node="false"
            default-expand-all
            @node-click="onOrgNodeClick"
          >
            <template #default="{ data }">
              <span class="org-node" :class="{ active: selectedOrg && selectedOrg.key === data.key }">
                <el-icon v-if="data.type === 'company'"><OfficeBuilding /></el-icon>
                <el-icon v-else><Folder /></el-icon>
                <span class="org-label">{{ data.label }}</span>
                <span v-if="data.userCount" class="org-count">{{ data.userCount }}</span>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- ============ 右侧：用户列表 ============ -->
      <el-col :span="19">
        <el-card shadow="never">
          <div class="toolbar">
            <el-tag v-if="selectedOrg" closable type="info" effect="plain" @close="clearOrgFilter">
              {{ selectedOrg.label }}
            </el-tag>
            <el-input v-model="query.keyword" :placeholder="$t('system.user.phSearchUser')" clearable style="width:220px" @keyup.enter="loadList" />
            <el-button type="primary" @click="onQuery">{{ $t('common.search') }}</el-button>
            <el-button v-permission="'system:user:create'" type="success" @click="openCreate">{{ $t('system.user.btnAddEmployee') }}</el-button>
            <el-upload
              :show-file-list="false"
              accept=".xlsx"
              :http-request="handleBatchImport"
            >
              <el-button v-permission="'system:user:create'" type="warning">{{ $t('system.user.btnBatchImport') }}</el-button>
            </el-upload>
          </div>

          <SkeletonList v-if="loading" :rows="8" :cols="5" />
          <el-table :data="list" v-else border stripe @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
            <el-table-column prop="userName" :label="$t('system.common.colUsername')" min-width="120" />
            <el-table-column prop="nickName" :label="$t('system.user.colName')" min-width="100" />
            <el-table-column prop="deptName" :label="$t('field.dept')" min-width="120">
              <template #default="{ row }">{{ row.deptName || row.orgName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="phone" :label="$t('system.user.colPhone')" min-width="130" />
            <el-table-column prop="email" :label="$t('system.user.colEmail')" min-width="180" />
            <el-table-column :label="$t('common.status')" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'NORMAL' ? 'success' : 'info'">{{ row.status === 'NORMAL' ? t('system.common.btnEnable') : t('system.common.btnDisable') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" :label="$t('common.createTime')" width="170" :formatter="(r, c, v) => formatDateTime(v)" />
            <el-table-column :label="$t('common.operation')" width="360" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'system:user:update'" link type="primary" @click="openEdit(row)">{{ $t('system.common.btnEdit') }}</el-button>
                <el-button v-permission="'system:user:assignRole'" link type="primary" @click="openAssignRole(row)">{{ $t('system.user.btnAssignRoles') }}</el-button>
                <el-button v-permission="'system:user:update'" link :type="row.status === 'NORMAL' ? 'warning' : 'success'" @click="toggleEnable(row)">
                  {{ row.status === 'NORMAL' ? t('system.common.btnDisable') : t('system.common.btnEnable') }}
                </el-button>
                <el-button v-permission="'system:user:resetPwd'" link type="primary" @click="onResetPwd(row)">{{ $t('system.user.btnResetPassword') }}</el-button>
                <el-button v-permission="'system:user:delete'" link type="danger" @click="onDelete(row)">{{ $t('system.common.btnDelete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>

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
      </el-col>
    </el-row>

    <!-- Create / Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? t('system.user.dlgEditTitle') : t('system.user.btnAddEmployee')" width="520px">
      <el-form :model="form" label-width="80px" ref="formRef" :rules="formRules">
        <el-form-item :label="$t('system.common.colUsername')" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" :placeholder="$t('system.user.lblLoginName')" />
        </el-form-item>
        <el-form-item :label="$t('system.user.colName')" prop="realName">
          <el-input v-model="form.realName" :placeholder="$t('system.user.lblRealName')" maxlength="50" />
        </el-form-item>
        <el-form-item :label="$t('field.dept')">
          <el-tree-select
            v-model="form.deptId"
            :data="deptFormTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            check-strictly
            filterable
            clearable
            :placeholder="$t('system.user.phSelectDeptTree')"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item :label="$t('system.user.colPhone')">
          <el-input v-model="form.phone" :placeholder="$t('system.user.lblMobile')" maxlength="30" />
        </el-form-item>
        <el-form-item :label="$t('system.user.colEmail')">
          <el-input v-model="form.email" :placeholder="$t('system.user.lblEmail')" />
        </el-form-item>
        <el-form-item v-if="!isEdit" :label="$t('system.user.lblPassword')" prop="password">
          <el-input v-model="form.password" type="password" show-password :placeholder="$t('system.user.lblInitPassword')" />
        </el-form-item>
        <el-form-item :label="$t('system.common.colEnabled')">
          <el-switch v-model="form.status" :active-value="'NORMAL'" :inactive-value="'DISABLED'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色 Dialog（D-UR-1）：一用户一角色——单选，保存即覆盖为唯一角色 -->
    <el-dialog v-model="roleDialogVisible" :title="$t('system.user.btnAssignRoles')" width="460px">
      <div class="role-assign-tip">
        {{ $t('system.user.txtForEmployee') }}<b>[{{ currentUser?.nickName || currentUser?.userName }}]</b> {{ $t('system.user.txtAssignRoleTail') }}
        {{ $t('system.user.hintOneRole') }}
      </div>
      <el-input v-model="roleKeyword" :placeholder="$t('system.user.phSearchRole')" clearable size="small" style="margin-bottom:10px" />
      <SkeletonList v-if="roleLoading" :rows="4" :cols="1" />
      <el-scrollbar v-else max-height="320px">
        <el-radio-group v-model="selectedRoleId" class="role-radio">
          <el-radio v-for="r in filteredRoles" :key="r.id" :value="r.id" class="role-radio-item">
            {{ r.roleName }}
            <span class="role-code">{{ r.roleCode }}</span>
          </el-radio>
        </el-radio-group>
        <el-empty v-if="!filteredRoles.length" :description="$t('system.user.emptyNoRoleMatch')" :image-size="50" />
      </el-scrollbar>
      <template #footer>
        <el-button @click="roleDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="roleSaving" @click="onSaveRole">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>

import { onMounted, reactive, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OfficeBuilding, Folder } from '@element-plus/icons-vue'
import { showRowDetail, formatDateTime } from '@/utils/format'
import { userApi, orgApi, roleApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => loadList(),
})

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ keyword: '', pageNum: 1, pageSize: 10, deptIds: null, companyId: null })

/* ---- 机构目录树（左树右表） ---- */
const orgTreeRef = ref()
const orgTree = ref([])
const orgKeyword = ref('')
const selectedOrg = ref(null) // { key, type: 'company'|'dept', id, label }

watch(orgKeyword, (kw) => { orgTreeRef.value?.filter(kw) })

function filterOrgNode(value, data) {
  if (!value) return true
  return (data.label || '').toLowerCase().includes(value.toLowerCase())
}

/** 用扁平数据建树：parentId 分组（0 为根） */
function buildForest(nodes, idOf, parentOf) {
  const byParent = new Map()
  for (const n of nodes || []) {
    const p = parentOf(n) || 0
    if (!byParent.has(p)) byParent.set(p, [])
    byParent.get(p).push(n)
  }
  const build = (p) => (byParent.get(p) || []).map((n) => ({
    ...n,
    children: build(idOf(n)),
  }))
  return build(0)
}

async function loadOrgTree() {
  try {
    const [companies, depts] = await Promise.all([
      orgApi.companyTree().catch(() => []),
      orgApi.deptTree().catch(() => []),
    ])
    const compList = companies || []
    const deptList = depts || []
    // 表单 tree-select 用：纯部门树（跨公司按 parentId 建林）
    loadDeptFormTree(deptList)
    // 部门按公司归组 → 挂到对应公司节点下
    const deptByCompany = new Map()
    for (const d of deptList) {
      const cid = d.companyId || 0
      if (!deptByCompany.has(cid)) deptByCompany.set(cid, [])
      deptByCompany.get(cid).push(d)
    }
    const forestOf = (cid) => buildForest(deptByCompany.get(cid) || [], (d) => d.id, (d) => (d.parentId && d.parentId !== 0 ? d.parentId : 0))

    const companyNodes = buildForest(compList, (c) => c.id, (c) => (c.parentId && c.parentId !== 0 ? c.parentId : 0))
    const attachDepts = (nodes) => nodes.map((c) => ({
      key: 'c-' + c.id,
      type: 'company',
      id: c.id,
      label: c.companyName || c.companyCode || t('field.companyNo', { v1: c.id }),
      children: forestOf(c.id).map((d) => wrapDept(d)),
    }))
    const wrapDept = (d) => ({
      key: 'd-' + d.id,
      type: 'dept',
      id: d.id,
      label: d.deptName || d.deptCode || t('field.deptNo', { v1: d.id }),
      children: (d.children || []).map((c) => wrapDept(c)),
    })
    // 未挂公司的部门默认处理为根级"未分配部门"
    const orphanDepts = forestOf(0)
    orgTree.value = [
      ...attachDepts(companyNodes),
      ...(orphanDepts.length ? orphanDepts.map((d) => wrapDept(d)) : []),
    ]
  } catch (e) {
    // 机构树加载失败不影响列表
  }
}

/** 收集部门节点及其全部子部门的 deptId */
function collectDeptIds(node, acc = []) {
  acc.push(node.id)
  for (const c of node.children || []) collectDeptIds(c, acc)
  return acc
}

function onOrgNodeClick(data) {
  selectedOrg.value = { key: data.key, type: data.type, id: data.id, label: data.label }
  query.pageNum = 1
  if (data.type === 'company') {
    query.companyId = data.id
    query.deptIds = null
  } else {
    query.companyId = null
    query.deptIds = collectDeptIds(data).join(',')
  }
  loadList()
}

function clearOrgFilter() {
  selectedOrg.value = null
  query.companyId = null
  query.deptIds = null
  query.pageNum = 1
  try { orgTreeRef.value?.setCurrentKey(null) } catch (e) { /* ignore */ }
  loadList()
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  username: '',
  realName: '',
  deptId: null,
  phone: '',
  email: '',
  password: '',
  status: 'NORMAL',
})

const formRules = computed(() => ({
  username: [{ required: true, message: t('system.user.errUsernameRequired') }],
  realName: [{ required: true, message: t('system.user.errNameRequired') }],
  password: [{ required: true, message: t('system.user.errInitPwdRequired') }],
}))

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: t('field.detail') })
}

async function loadList() {
  loading.value = true
  try {
    const params = { keyword: query.keyword, pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.deptIds) params.deptIds = query.deptIds
    if (query.companyId) params.companyId = query.companyId
    const res = await userApi.page(params)
    list.value = res.records || res.list || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || t('system.common.msgLoadFail'))
  } finally {
    loading.value = false
  }
}

function onQuery() {
  query.pageNum = 1
  loadList()
}

/** 部门扁平表 → 纯部门树（表单 tree-select 用） */
const deptFormTree = ref([])
function loadDeptFormTree(deptList) {
  deptFormTree.value = buildForest(deptList || [], (d) => d.id, (d) => (d.parentId && d.parentId !== 0 ? d.parentId : 0))
}
function findDeptNode(nodes, id) {
  if (!id) return null
  for (const n of nodes || []) {
    if (n.id === id) return n
    const hit = findDeptNode(n.children, id)
    if (hit) return hit
  }
  return null
}

function resetForm() {
  Object.assign(form, { id: null, username: '', realName: '', deptId: null, phone: '', email: '', password: '', status: 'NORMAL' })
}

function openCreate() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row) {
  isEdit.value = true
  Object.assign(form, { id: row.id, username: row.userName, realName: row.nickName, deptId: row.deptId, phone: '', email: '', password: '', status: row.status })
  dialogVisible.value = true
  // P1-3：列表 phone/email 为脱敏值（138****8000），编辑时拉取明文回填，避免把脱敏串存回库
  try {
    const f = await userApi.full(row.id)
    form.phone = f?.phone || ''
    form.email = f?.email || ''
  } catch (e) {
    ElMessage.warning(t('system.user.msgContactMasked'))
  }
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      // 后端 SysUser 姓名字段为 nickName（非 realName），此前提交 realName 导致改名静默失效
      await userApi.update({ id: form.id, nickName: form.realName, deptId: form.deptId, phone: form.phone, email: form.email, status: form.status })
      ElMessage.success(t('system.common.msgUpdated'))
    } else {
      const deptNode = findDeptNode(deptFormTree.value, form.deptId)
      await userApi.create({
        user: {
          userName: form.username,
          nickName: form.realName,
          deptId: form.deptId,
          companyId: deptNode?.companyId || null,
          phone: form.phone,
          email: form.email,
          password: form.password,
          status: form.status,
        },
        roleIds: [],
      })
      ElMessage.success(t('system.common.msgCreated'))
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e.message || t('system.common.msgSaveFail'))
  } finally {
    saving.value = false
  }
}

async function toggleEnable(row) {
  try {
    const isEnabled = row.status === 'NORMAL'
    await userApi.enable(row.id, !isEnabled)
    ElMessage.success(isEnabled ? t('system.common.stDisabled') : t('system.common.stEnabled'))
    row.status = isEnabled ? 'DISABLED' : 'NORMAL'
  } catch (e) {
    ElMessage.error(e.message || t('common.failed'))
  }
}

async function onResetPwd(row) {
  await ElMessageBox.confirm(t('system.user.askResetPwd', { v1: row.userName }), t('system.user.btnResetPassword'), { type: 'warning' })
  try {
    await userApi.resetPwd(row.id)
    ElMessage.success(t('system.user.msgPwdReset'))
  } catch (e) {
    ElMessage.error(e.message || t('system.user.msgResetFail'))
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(t('system.user.askDeleteUser', { v1: row.userName }), t('system.common.ttlDelete'), { type: 'warning' })
  try {
    await userApi.delete(row.id)
    ElMessage.success(t('system.common.msgDeleteOk'))
    loadList()
  } catch (e) {
    ElMessage.error(e.message || t('system.common.msgDeleteFail'))
  }
}

async function handleBatchImport({ file }) {
  try {
    await userApi.batchImport(file)
    ElMessage.success(t('system.user.msgImportOk'))
    loadList()
  } catch (e) {
    ElMessage.error(e.message || t('system.user.msgImportFail'))
  }
}

/* ---- D-UR-1：分配角色（一用户一角色） ---- */
const roleDialogVisible = ref(false)
const roleLoading = ref(false)
const roleSaving = ref(false)
const roleOptions = ref([])
const roleKeyword = ref('')
const currentUser = ref(null)
const selectedRoleId = ref(null)

const filteredRoles = computed(() => {
  const kw = roleKeyword.value.trim().toLowerCase()
  if (!kw) return roleOptions.value
  return roleOptions.value.filter((r) =>
    (r.roleName || '').toLowerCase().includes(kw) || (r.roleCode || '').toLowerCase().includes(kw))
})

async function openAssignRole(row) {
  currentUser.value = row
  selectedRoleId.value = null
  roleKeyword.value = ''
  roleDialogVisible.value = true
  roleLoading.value = true
  try {
    if (!roleOptions.value.length) {
      const res = await roleApi.page({ pageNum: 1, pageSize: 200 })
      roleOptions.value = res.records || res.list || []
    }
    const ids = (await userApi.roleIds(row.id)) || []
    // 一用户一角色：回显其首个已分配角色；多角色遗留仅取其一，保存即收敛
    selectedRoleId.value = ids.length ? ids[0] : null
  } catch (e) {
    ElMessage.error(e.message || t('system.user.msgRoleLoadFail'))
  } finally {
    roleLoading.value = false
  }
}

async function onSaveRole() {
  if (!currentUser.value) return
  roleSaving.value = true
  try {
    await userApi.assignRoles(currentUser.value.id, selectedRoleId.value ? [selectedRoleId.value] : [])
    ElMessage.success(selectedRoleId.value ? t('system.user.msgRoleAssigned') : t('system.user.msgRolesCleared'))
    roleDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e.message || t('system.common.msgSaveFail'))
  } finally {
    roleSaving.value = false
  }
}

onMounted(async () => {
  loadOrgTree()
  loadList()
})
</script>

<style scoped>
/* 左树右表布局 */
.org-tree-card { height: 100%; }
.org-tree-card :deep(.el-tree) { max-height: 640px; overflow-y: auto; }
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.panel-title { font-size: 14px; font-weight: 600; color: #303133; }
.org-node { display: flex; align-items: center; gap: 6px; font-size: 13px; padding: 2px 0; }
.org-node .el-icon { color: #409eff; font-size: 14px; }
.org-node.active .org-label { color: #409eff; font-weight: 600; }
.org-count {
  margin-left: auto; margin-right: 8px; font-size: 11px; color: #909399;
  background: #f0f2f5; border-radius: 8px; padding: 0 6px; line-height: 16px;
}
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

/* ---- 分配角色弹窗（D-UR-1） ---- */
.role-assign-tip { font-size: 13px; color: #606266; line-height: 1.5; margin-bottom: 10px; }
.role-radio { display: flex; flex-direction: column; width: 100%; }
.role-radio-item { height: auto; margin-right: 0; padding: 6px 4px; width: 100%; }
.role-code { margin-left: 8px; font-size: 11px; color: #a8abb2; font-family: Consolas, monospace; }
</style>
