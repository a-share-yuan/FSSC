<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="position-page">
    <el-card shadow="never" style="width:100%">
      <el-row :gutter="16">
        <!-- 左侧：职位树 -->
        <el-col :span="7">
          <div class="left-panel">
            <div class="panel-header">
              <span style="border-left:4px solid #ff8c00;padding-left:8px;font-weight:600">职位</span>
              <div>
                <el-button size="small" @click="doExport">导出 CSV</el-button>
                <el-button v-permission="'system:post:create'" type="primary" size="small" @click="openCreate">新增</el-button>
              </div>
            </div>
            <el-input v-model="kw" placeholder="输入关键字查询" clearable size="small" style="margin-bottom:8px" />
            <el-button size="small" style="margin-bottom:8px" @click="kw=''">重置</el-button>
            <SkeletonList v-if="loading" :rows="10" :cols="1" />
            <el-tree
              v-else
              ref="treeRef"
              :data="treeData"
              node-key="id"
              :props="{ label: 'name', children: 'children', isLeaf: 'isLeaf' }"
              :filter-node-method="filterNode"
              :render-after-expand="false"
              default-expand-all
              highlight-current
              @node-click="onNodeClick"
            />
            <el-empty v-if="!loading && !treeData.length" description="暂无职位" :image-size="60" />
          </div>
        </el-col>

        <!-- 右侧：职位详情 -->
        <el-col :span="17">
          <div class="right-panel">
            <div class="panel-header">
              <span style="border-left:4px solid #ff8c00;padding-left:8px;font-weight:600">职位详情</span>
              <div>
                <el-button v-if="current.id" v-permission="'system:post:delete'" size="small" @click="del(current)">删除</el-button>
                <el-button v-permission="['system:post:create','system:post:update']" type="primary" size="small" :loading="saving" @click="save">{{ current.id ? '保存' : '新增' }}</el-button>
              </div>
            </div>
            <el-form :model="form" :rules="formRules" label-width="100px" style="margin-top:12px">
              <div class="form-row">
                <el-form-item label="编号" class="half"><el-input :model-value="current.id || ''" disabled placeholder="自动生成" /></el-form-item>
                <el-form-item label="名称" class="half" prop="postName"><el-input v-model="form.postName" placeholder="请输入职位名称" /></el-form-item>
              </div>
              <div class="form-row">
                <el-form-item label="编码" class="half"><el-input v-model="form.postCode" placeholder="例如 J0001" /></el-form-item>
                <el-form-item label="职级" class="half"><el-input-number v-model="form.postLevel" :min="0" style="width:100%" /></el-form-item>
              </div>
              <div class="form-row">
                <el-form-item label="所属公司" class="half">
                  <el-select v-model="form.companyId" filterable clearable style="width:100%" placeholder="选择公司">
                    <el-option v-for="c in flatCompanies" :key="c.id" :label="c.companyName" :value="c.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="所属部门" class="half">
                  <el-select v-model="form.deptId" filterable clearable style="width:100%" placeholder="选择部门">
                    <el-option v-for="d in deptsOfCompany" :key="d.id" :label="d.deptName" :value="d.id" />
                  </el-select>
                </el-form-item>
              </div>
              <div class="form-row">
                <el-form-item label="排序" class="half"><el-input-number v-model="form.sort" :min="0" style="width:100%" /></el-form-item>
                <el-form-item label="启用" class="half">
                  <el-switch v-model="form.enabled" />
                </el-form-item>
              </div>
              <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="3" placeholder="备注说明" /></el-form-item>
            </el-form>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orgApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { exportCsv } from '../../utils/csv'

const treeRef = ref()
const kw = ref('')
const loading = ref(false)
const saving = ref(false)
const companyTree = ref([])
const allPosts = ref([])
const allDepts = ref([])
const current = reactive({ id: null, companyName: '' })
const form = reactive({ postName: '', postCode: '', postLevel: '', companyId: null, deptId: null, sort: 0, enabled: true, remark: '' })

const formRules = {
  postName: [{ required: true, message: '请输入职位名称' }],
}

// 扁平化公司
const flatCompanies = computed(() => {
  const out = []
  const walk = (nodes) => {
    for (const n of nodes || []) { out.push(n); if (n.children?.length) walk(n.children) }
  }
  walk(companyTree.value)
  return out
})

const deptsOfCompany = computed(() => {
  if (!form.companyId) return allDepts.value
  return allDepts.value.filter((d) => d.companyId === form.companyId)
})

// 职位树：根「所有职位」→ 按公司分组 → 公司下职位
const treeData = computed(() => {
  const coms = flatCompanies.value
  const roots = [{ id: 'ALL', name: '所有职位(' + allPosts.value.length + ')', children: [] }]
  // 未归属公司的职位直接放根下
  const orphanPosts = allPosts.value.filter((p) => !p.companyId)
  roots[0].children = coms.map((c) => ({
    id: 'COMP-' + c.id, name: c.companyName, children: allPosts.value.filter((p) => p.companyId === c.id).map((p) => ({ id: 'POST-' + p.id, name: p.postName + '(' + p.id + ')', pid: p.id, isLeaf: true })),
  })).filter((n) => n.children.length)
  for (const p of orphanPosts) roots[0].children.push({ id: 'POST-' + p.id, name: p.postName + '(' + p.id + ')', pid: p.id, isLeaf: true })
  return roots
})

function filterNode(value, data) {
  if (!value) return true
  return data.name.toLowerCase().includes(value.toLowerCase())
}
watch(kw, (v) => treeRef.value?.filter(v))

async function loadData() {
  loading.value = true
  try {
    const [ct, posts, depts] = await Promise.all([
      orgApi.companyTree(),
      orgApi.postList(),
      orgApi.deptTree(),
    ])
    companyTree.value = ct || []
    allPosts.value = posts || []
    // 全量部门：递归展平
    const flat = []
    const walk = (nodes) => { for (const n of nodes || []) { flat.push(n); if (n.children?.length) walk(n.children) } }
    walk(depts || [])
    allDepts.value = flat
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function onNodeClick(node) {
  if (!node?.pid && node?.id !== 'ALL') return // 仅职位节点可取详情
  if (node.id === 'ALL') { current.id = null; resetForm(); return }
  const p = allPosts.value.find((x) => x.id === node.pid)
  if (!p) return
  Object.assign(current, { id: p.id, companyName: (flatCompanies.value.find((c) => c.id === p.companyId) || {}).companyName || '' })
  Object.assign(form, {
    postName: p.postName || '', postCode: p.postCode || '', postLevel: p.postLevel ?? '', companyId: p.companyId ?? null, deptId: p.deptId ?? null, sort: p.sort ?? 0, enabled: (p.status ?? 'NORMAL') === 'NORMAL', remark: p.remark || '',
  })
}

function resetForm() {
  Object.assign(form, { postName: '', postCode: '', postLevel: '', companyId: null, deptId: null, sort: 0, enabled: true, remark: '' })
}

function openCreate() {
  current.id = null
  resetForm()
  ElMessage.info('请在右侧填写职位信息后点击「新增」')
}

async function save() {
  if (!form.postName) { ElMessage.warning('请输入职位名称'); return }
  saving.value = true
  try {
    const body = { ...form, status: form.enabled ? 'NORMAL' : 'DISABLED' }
    if (current.id) {
      body.id = current.id
      await orgApi.postUpdate(body)
    } else {
      await orgApi.postCreate(body)
    }
    ElMessage.success('已保存')
    await loadData()
    current.id = null
    resetForm()
  } catch (e) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function del(row) {
  try {
    await ElMessageBox.confirm('确认删除该职位？', '删除', { type: 'warning' })
    await orgApi.postDelete(row.id)
    ElMessage.success('已删除')
    await loadData()
    current.id = null
    resetForm()
  } catch (e) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// 41号审查 P1-9：全量职位导出（树形页无分页语义，导出即全量清单）
function doExport() {
  exportCsv(allPosts.value.map((p) => ({
    ...p,
    companyName: (flatCompanies.value.find((c) => c.id === p.companyId) || {}).companyName || '',
    deptName: (allDepts.value.find((d) => d.id === p.deptId) || {}).deptName || '',
    statusText: (p.status ?? 'NORMAL') === 'NORMAL' ? '启用' : '停用',
  })), [
    { prop: 'id', label: '编号' }, { prop: 'postName', label: '名称' }, { prop: 'postCode', label: '编码' },
    { prop: 'postLevel', label: '职级' }, { prop: 'companyName', label: '所属公司' }, { prop: 'deptName', label: '所属部门' },
    { prop: 'sort', label: '排序' }, { prop: 'statusText', label: '状态' }, { prop: 'remark', label: '备注' },
  ], 'Position')
}

onMounted(() => loadData())
</script>

<style scoped>
.position-page { min-height: 100%; }
.left-panel, .right-panel { min-height: 480px; }
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.form-row { display: flex; gap: 12px; }
.half { flex: 1; }
</style>