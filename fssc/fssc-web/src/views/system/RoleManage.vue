<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="role-manage">
    <el-card>
      <!-- Toolbar -->
      <div class="toolbar">
        <el-input v-model="query.keyword" :placeholder="$t('system.role.phSearchRole')" clearable style="width:240px" @keyup.enter="loadList" />
        <el-button type="primary" @click="onQuery">{{ $t('common.search') }}</el-button>
        <el-button v-permission="'system:role:create'" type="success" @click="openCreate">{{ $t('system.role.btnAddRole') }}</el-button>
      </div>

      <!-- Table -->
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="list" v-else border stripe @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
        <el-table-column prop="roleCode" :label="$t('system.role.colRoleCode')" min-width="140" />
        <el-table-column prop="roleName" :label="$t('system.role.colRoleName')" min-width="140" />
        <el-table-column :label="$t('common.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'NORMAL' ? 'success' : 'info'">{{ row.status === 'NORMAL' ? $t('system.common.btnEnable') : $t('system.common.btnDisable') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="$t('common.createTime')" width="170" :formatter="(r, c, v) => formatDateTime(v)" />
        <el-table-column :label="$t('common.operation')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:role:update'" link type="primary" @click="openEdit(row)">{{ $t('system.common.btnEdit') }}</el-button>
            <el-button v-permission="'system:role:assignMenu'" link type="primary" @click="openAssignMenu(row)">{{ $t('system.role.btnAssignMenu') }}</el-button>
<el-button v-permission="'system:role:update'" link :type="row.status === 'NORMAL' ? 'warning' : 'success'" @click="toggleEnable(row)">
              {{ row.status === 'NORMAL' ? t('system.common.btnDisable') : t('system.common.btnEnable') }}
            </el-button>
            <el-button v-permission="'system:role:delete'" link type="danger" @click="onDelete(row)">{{ $t('system.common.btnDelete') }}</el-button>
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
    <el-dialog v-model="dialogVisible" :title="isEdit ? t('system.role.dlgEditTitle') : t('system.role.btnAddRole')" width="480px">
      <el-form :model="form" label-width="80px" ref="formRef" :rules="formRules">
        <el-form-item :label="$t('system.role.colRoleCode')" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="isEdit" placeholder="ROLE_ADMIN" />
        </el-form-item>
        <el-form-item :label="$t('system.role.colRoleName')" prop="roleName">
          <el-input v-model="form.roleName" :placeholder="$t('system.role.colRoleName')" />
        </el-form-item>
        <el-form-item :label="$t('system.role.colDescription')">
          <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="$t('system.role.lblRoleDesc')" maxlength="500" show-word-limit />
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

    <!-- Menu Assignment Dialog（重构：大弹窗，左目录树（搜索/展开收起）+ 右已选权限清单） -->
    <el-dialog v-model="menuDialogVisible" :title="$t('system.role.btnAssignMenu')" width="920px" top="6vh">
      <SkeletonDetail v-if="menuLoading" :rows="6" />
      <div v-else class="menu-assign">
        <div class="assign-tip">
          {{ $t('system.role.txtForRole') }}<b>[{{ currentRole?.roleName }}]</b> {{ $t('system.role.hintAssignMenu') }}</div>
        <div class="assign-body">
          <!-- 左：菜单目录树 -->
          <div class="assign-left">
            <div class="tree-toolbar">
              <el-input v-model="menuKeyword" :placeholder="$t('system.role.phSearchMenu')" size="small" clearable :prefix-icon="Search" style="flex:1" />
              <el-button size="small" @click="expandAllMenus(true)">{{ $t('system.role.btnExpand') }}</el-button>
              <el-button size="small" @click="expandAllMenus(false)">{{ $t('system.role.btnCollapse') }}</el-button>
            </div>
            <el-tree
              ref="menuTreeRef"
              :data="menuTree"
              node-key="id"
              :props="{ label: 'menuName', children: 'children' }"
              show-checkbox
              :default-expanded-keys="rootMenuIds"
              :filter-node-method="filterMenuNode"
              :expand-on-click-node="false"
              class="menu-tree"
              @check="syncCheckedList"
            >
              <template #default="{ data }">
                <span class="menu-node" :class="{ btn: data.menuType === 'F' }">
                  <span>{{ data.menuName }}</span>
                  <span v-if="data.perms" class="menu-perms">{{ data.perms }}</span>
                </span>
              </template>
            </el-tree>
          </div>
          <!-- 右：已选权限清单（按一级业务域分组，模块归类） -->
          <div class="assign-right">
            <div class="selected-head">
              <span>{{ $t('system.role.statSelectedPerms', { v1: checkedList.length }) }}</span>
              <el-button link type="danger" size="small" @click="clearMenuSelection">{{ $t('system.role.btnClear') }}</el-button>
            </div>
            <div class="selected-search">
              <el-input v-model="rightKeyword" :placeholder="$t('system.role.phSearchSelected')" size="small" clearable :prefix-icon="Search" />
            </div>
            <div class="selected-list">
              <el-empty v-if="!checkedList.length" :description="$t('system.role.hintPickPerms')" :image-size="60" />
              <template v-else>
                <div v-for="g in filteredGroups" :key="g.name" class="sel-group">
                  <div class="sel-group-head" @click="toggleGroup(g.name)">
                    <el-icon class="g-arrow" :class="{ fold: foldedGroups.has(g.name) }"><CaretRight /></el-icon>
                    <span class="g-name" :title="g.name">{{ g.name }}</span>
                    <span class="g-count">{{ g.items.length }}</span>
                  </div>
                  <div v-show="!foldedGroups.has(g.name)" class="sel-group-body">
                    <div v-for="m in g.items" :key="m.id" class="selected-item">
                      <span class="sel-name" :title="m.perms || m.menuName">{{ m.menuName }}</span>
                      <el-icon class="sel-del" :title="$t('system.role.btnRemove')" @click="removeMenuChecked(m.id)"><Close /></el-icon>
                    </div>
                  </div>
                </div>
                <el-empty v-if="!filteredGroups.length" :description="$t('system.role.emptyNoPermMatch')" :image-size="40" />
              </template>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="menuDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="menuSaving" @click="onSaveMenus">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>

import { computed, onMounted, reactive, ref, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Close, CaretRight } from '@element-plus/icons-vue'
import { showRowDetail, formatDateTime } from '@/utils/format'
import { roleApi, getMenuTree } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import SkeletonDetail from '../../components/SkeletonDetail.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// 快捷键
useKeyboard({
  'ctrl+r': () => loadList(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: t('field.detail') })
}

/* Create / Edit */
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  roleCode: '',
  roleName: '',
  description: '',
  status: 'NORMAL',
})

const formRules = computed(() => ({
  roleCode: [{ required: true, message: t('system.role.errRoleCodeRequired') }],
  roleName: [{ required: true, message: t('system.role.errRoleNameRequired') }],
}))

/* Menu Assignment */
const menuDialogVisible = ref(false)
const menuLoading = ref(false)
const menuSaving = ref(false)
const menuTreeRef = ref()
const menuTree = ref([])
const currentRole = ref(null)

// 目录树搜索 / 展开收起 / 已选清单
const menuKeyword = ref('')
const checkedList = ref([])
const rootMenuIds = computed(() => menuTree.value.map((m) => m.id))

watch(menuKeyword, (kw) => { try { menuTreeRef.value?.filter(kw) } catch (e) { /* ignore */ } })

function filterMenuNode(value, data) {
  if (!value) return true
  const kw = value.toLowerCase()
  return (data.menuName || '').toLowerCase().includes(kw) || (data.perms || '').toLowerCase().includes(kw)
}

function expandAllMenus(expand) {
  try {
    const nodes = menuTreeRef.value?.store?.root?.childNodes || []
    const walk = (list) => list.forEach((n) => { n.expanded = expand; if (n.childNodes?.length) walk(n.childNodes) })
    walk(nodes)
  } catch (e) { /* ignore */ }
}

/** 同步右侧已选清单（勾选事件与预置回显时调用） */
function syncCheckedList() {
  try {
    const nodes = menuTreeRef.value?.getCheckedNodes?.() || []
    checkedList.value = nodes.map((n) => ({ id: n.id, menuName: n.menuName, perms: n.perms || '' }))
  } catch (e) { /* ignore */ }
}

/**
 * 只回显叶子节点 id：保存数据里含父目录 id（checked+halfChecked 全量），
 * 若把父 id 传给 setCheckedKeys，el-tree 会把整棵子树连带勾选 → 回显虚增、
 * 再保存即静默扩权。只回显叶子，父节点勾选态由树自行推导，精确还原。
 */
function leafOnlyIds(ids) {
  const parentIds = new Set()
  const walk = (nodes) => (nodes || []).forEach((n) => {
    if (n.children && n.children.length) { parentIds.add(n.id); walk(n.children) }
  })
  walk(menuTree.value)
  return (ids || []).filter((id) => !parentIds.has(id))
}

/* 右侧已选清单：按一级业务域分组（按模块归类） */
const rightKeyword = ref('')
const foldedGroups = ref(new Set())

const checkedGroups = computed(() => {
  // nodeId → 一级域名称（根节点 menuName）
  const rootOf = new Map()
  const walk = (nodes, rootName, order) => (nodes || []).forEach((n) => {
    const rn = rootName || n.menuName
    rootOf.set(n.id, { root: rn, order })
    walk(n.children, rn, order)
  })
  menuTree.value.forEach((n, i) => walk([n], null, i))
  const groups = new Map()
  for (const m of checkedList.value) {
    const info = rootOf.get(m.id) || { root: t('system.common.optOther'), order: 999 }
    if (!groups.has(info.root)) groups.set(info.root, { name: info.root, order: info.order, items: [] })
    groups.get(info.root).items.push(m)
  }
  return [...groups.values()].sort((a, b) => a.order - b.order)
})

const filteredGroups = computed(() => {
  const kw = rightKeyword.value.trim().toLowerCase()
  if (!kw) return checkedGroups.value
  return checkedGroups.value
    .map((g) => ({ ...g, items: g.items.filter((m) => (m.menuName || '').toLowerCase().includes(kw) || (m.perms || '').toLowerCase().includes(kw)) }))
    .filter((g) => g.items.length)
})

function toggleGroup(name) {
  const s = new Set(foldedGroups.value)
  s.has(name) ? s.delete(name) : s.add(name)
  foldedGroups.value = s
}

function removeMenuChecked(id) {
  try {
    menuTreeRef.value?.setChecked(id, false)
    syncCheckedList()
  } catch (e) { /* ignore */ }
}

function clearMenuSelection() {
  try {
    menuTreeRef.value?.setCheckedKeys([])
    syncCheckedList()
  } catch (e) { /* ignore */ }
}

async function loadList() {
  loading.value = true
  try {
    const res = await roleApi.page(query)
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

function resetForm() {
  Object.assign(form, { id: null, roleCode: '', roleName: '', description: '', status: 'NORMAL' })
}

function openCreate() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, { id: row.id, roleCode: row.roleCode, roleName: row.roleName, description: row.description || '', status: row.status })
  dialogVisible.value = true
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await roleApi.update({ id: form.id, roleCode: form.roleCode, roleName: form.roleName, description: form.description, status: form.status })
      ElMessage.success(t('system.common.msgUpdated'))
    } else {
      await roleApi.create({ roleCode: form.roleCode, roleName: form.roleName, description: form.description, status: form.status })
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
    await roleApi.enable(row.id, !isEnabled)
    ElMessage.success(isEnabled ? t('system.common.stDisabled') : t('system.common.stEnabled'))
    row.status = isEnabled ? 'DISABLED' : 'NORMAL'
  } catch (e) {
    ElMessage.error(e.message || t('common.failed'))
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(t('system.role.askDeleteRole', { v1: row.roleName }), t('system.common.ttlDelete'), { type: 'warning' })
  try {
    await roleApi.delete(row.id)
    ElMessage.success(t('system.common.msgDeleteOk'))
    loadList()
  } catch (e) {
    ElMessage.error(e.message || t('system.common.msgDeleteFail'))
  }
}

/* Menu Assignment */
async function openAssignMenu(row) {
  currentRole.value = row
  menuDialogVisible.value = true
  menuLoading.value = true
  let checkedIds = []
  try {
    // Load menu tree (cache after first load)
    if (!menuTree.value.length) {
      menuTree.value = (await getMenuTree()) || []
    }
    // Load currently assigned menu IDs
    checkedIds = (await roleApi.menuIds(row.id)) || []
  } catch (e) {
    ElMessage.error(e.message || t('system.role.msgMenuLoadFail'))
  } finally {
    // 先结束骨架屏，让 el-tree 真正挂载后再回显勾选
    //（否则 menuTreeRef 为 undefined，setCheckedKeys 被可选链静默跳过 → 已选权限恒为空）
    menuLoading.value = false
  }
  if (!checkedIds.length) return
  await nextTick()
  // 关键：只勾叶子，防止父 id 连带整棵子树被勾上（回显虚增 → 保存即扩权）
  menuTreeRef.value?.setCheckedKeys(leafOnlyIds(checkedIds))
  await nextTick()
  syncCheckedList()
}

async function onSaveMenus() {
  const checked = menuTreeRef.value?.getCheckedKeys() || []
  const halfChecked = menuTreeRef.value?.getHalfCheckedKeys() || []
  const allIds = [...checked, ...halfChecked]
  // 零授权角色会让绑定它的账号登录即被 fail-closed 拒绝（权限码为空 + 有角色），先拦一次
  if (!allIds.length) {
    try {
      await ElMessageBox.confirm(
        `角色 [${currentRole.value?.roleName}] 未勾选任何菜单权限。保存后，仅拥有该角色的账号将无法登录（提示“权限加载异常”）。确认继续？`,
        t('system.role.btnAssignMenu'), { type: 'warning', confirmButtonText: t('common.save'), cancelButtonText: t('common.cancel') })
    } catch { return }
  }
  menuSaving.value = true
  try {
    await roleApi.assignMenus(currentRole.value.id, allIds)
    ElMessage.success(t('system.role.msgMenuAssigned'))
    menuDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e.message || t('system.common.msgSaveFail'))
  } finally {
    menuSaving.value = false
  }
}

onMounted(() => {
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

/* 分配菜单大弹窗（左树右清单） */
.menu-assign { display: flex; flex-direction: column; gap: 10px; }
.assign-tip { font-size: 13px; color: var(--app-text-secondary, #606266); line-height: 1.5; }
.assign-body { display: flex; gap: 12px; min-height: 420px; }
.assign-left { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.tree-toolbar { display: flex; gap: 6px; align-items: center; }
.menu-tree {
  flex: 1;
  max-height: 480px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 6px;
}
.menu-node { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; }
.menu-node.btn .menu-perms { color: #b0b3b8; }
.menu-perms { font-size: 11px; color: #a8abb2; font-family: Consolas, monospace; }
.assign-right {
  width: 250px;
  flex-shrink: 0;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
}
.selected-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 10px; border-bottom: 1px solid #e4e7ed;
  font-size: 13px; font-weight: 600; color: var(--app-text, #303133);
}
.selected-search { padding: 6px 8px 0; }
.selected-list { flex: 1; overflow-y: auto; max-height: 400px; padding: 6px 8px; }
.sel-group { margin-bottom: 6px; }
.sel-group-head {
  display: flex; align-items: center; gap: 4px;
  padding: 5px 6px; margin-bottom: 4px; cursor: pointer;
  background: #ecf2fe; border-radius: 4px;
  font-size: 12px; font-weight: 600; color: var(--app-text, #303133);
  user-select: none;
}
.sel-group-head:hover { background: #e0eafc; }
.g-arrow { transition: transform 0.15s; color: #909399; font-size: 12px; }
.g-arrow.fold { transform: rotate(90deg); }
.g-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.g-count {
  background: #fff; color: #409eff; border-radius: 8px;
  padding: 0 7px; font-size: 11px; font-weight: 600; line-height: 16px;
}
.selected-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 5px 8px; margin-bottom: 4px;
  background: var(--app-bg, #f5f7fa); border-radius: 4px; font-size: 12px; color: var(--app-text, #303133);
}
.sel-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sel-del { cursor: pointer; color: #c0c4cc; flex-shrink: 0; }
.sel-del:hover { color: #f56c6c; }
</style>
