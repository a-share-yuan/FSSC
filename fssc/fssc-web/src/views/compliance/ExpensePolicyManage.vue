<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="expense-policy-manage">
    <el-alert type="info" :closable="false" show-icon class="mb12"
              title="费用标准三层结构"
              description="① 政策（expense_policy）定义适用费用类型/组织/金额上限与有效期；② 标准明细（expense_policy_standard）按员工级别/职位/部门定额度与超标动作；③ 版本（expense_policy_version）留存每次变更快照并走审批生效。先在左侧维护政策，选中后再维护其标准明细与版本。" />

    <el-row :gutter="16">
      <!-- 左：政策主表 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">政策（{{ rows.length }}）</span>
              <el-button v-if="canCreate" type="success" size="small" @click="openCreate">新增政策</el-button>
            </div>
          </template>

          <el-input v-model="keyword" placeholder="政策编码/名称" clearable size="small" class="mb12" @keyup.enter="load" />

          <SkeletonList v-if="loading" :rows="6" :cols="2" />
          <el-table v-else :data="filtered" size="small" border stripe highlight-current-row
                    empty-text="暂无政策，先新增一条" @current-change="onSelect">
            <el-table-column prop="policyCode" label="编码" width="110" show-overflow-tooltip />
            <el-table-column prop="policyName" label="政策名称" min-width="130" show-overflow-tooltip />
            <el-table-column label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click.stop="openEdit(row)">编辑</el-button>
                <el-button v-if="canUpdate" link size="small"
                           :type="row.status === 1 ? 'warning' : 'success'"
                           @click.stop="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
                <el-button v-if="canDelete" link size="small" type="danger" @click.stop="removePolicy(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右：选中政策的 标准明细 + 版本 -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">
                {{ current ? `${current.policyName}（${current.policyCode}）` : '标准明细与版本' }}
              </span>
              <el-button v-if="current && canStdCreate" type="success" size="small" @click="openStdCreate">新增标准</el-button>
            </div>
          </template>

          <el-empty v-if="!current" description="请先在左侧选择一条政策" :image-size="70" />

          <el-tabs v-else v-model="tab" type="border-card">
            <!-- 标准明细 -->
            <el-tab-pane label="标准明细" name="std">
              <el-table :data="stds" size="small" border stripe empty-text="该政策尚未维护标准明细" v-loading="loadingStd">
                <el-table-column prop="standardType" label="类型" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="STD_TYPE[row.standardType]?.type || 'info'">{{ STD_TYPE[row.standardType]?.label || row.standardType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="standardKey" label="标准键" width="130" show-overflow-tooltip />
                <el-table-column label="下限" width="100" align="right">
                  <template #default="{ row }">{{ row.standardValueMin ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="上限" width="100" align="right">
                  <template #default="{ row }">{{ row.standardValueMax ?? '-' }}</template>
                </el-table-column>
                <el-table-column prop="standardUnit" label="单位" width="70" align="center" />
                <el-table-column label="适用级别" min-width="150">
                  <template #default="{ row }">
                    <span v-if="row.levelType">{{ LEVEL_TYPE[row.levelType] || row.levelType }}：{{ row.levelValue }}</span>
                    <span v-else style="color: #c0c4cc">全级别</span>
                  </template>
                </el-table-column>
                <el-table-column label="超标动作" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.overtimeAction" size="small" :type="OVERTIME[row.overtimeAction]?.type || 'info'">
                      {{ OVERTIME[row.overtimeAction]?.label || row.overtimeAction }}
                    </el-tag>
                    <span v-else style="color: #c0c4cc">-</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="110" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canStdUpdate" link size="small" type="primary" @click="openStdEdit(row)">编辑</el-button>
                    <el-button v-if="canStdDelete" link size="small" type="danger" @click="removeStd(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 版本 -->
            <el-tab-pane label="版本记录" name="ver">
              <el-table :data="vers" size="small" border stripe empty-text="该政策尚无版本记录" v-loading="loadingVer">
                <el-table-column prop="version" label="版本" width="90" />
                <el-table-column prop="status" label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="VER_STATUS[row.status]?.type || 'info'">{{ VER_STATUS[row.status]?.label || row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="changeLog" label="变更说明" min-width="180" show-overflow-tooltip />
                <el-table-column label="审批人" width="90" align="center">
                  <template #default="{ row }">{{ row.approvedBy || '-' }}</template>
                </el-table-column>
                <el-table-column label="审批时间" width="150">
                  <template #default="{ row }">{{ row.approvedAt || '-' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="150" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canVerApprove && row.status === 'DRAFT'" link size="small" type="success" @click="approveVer(row)">审批</el-button>
                    <el-button v-if="canVerUpdate && row.status === 'APPROVED'" link size="small" type="primary" @click="activateVer(row)">生效</el-button>
                    <el-button v-if="canVerUpdate && row.status === 'ACTIVE'" link size="small" type="warning" @click="archiveVer(row)">归档</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div style="margin-top: 8px">
                <el-button v-if="canVerCreate" size="small" type="success" @click="snapshotVer">按当前配置存为新版本</el-button>
                <span style="margin-left: 8px; color: #909399; font-size: 12px">
                  快照会冻结当前政策与标准明细，后续改动不影响已存版本
                </span>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>

    <!-- 政策编辑弹窗 -->
    <el-dialog v-model="dlg" :title="form.id ? '编辑政策' : '新增政策'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="政策编码" prop="policyCode"><el-input v-model="form.policyCode" :disabled="!!form.id" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="政策名称" prop="policyName"><el-input v-model="form.policyName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="费用类型"><el-input v-model="form.expenseType" placeholder="如 TRAVEL / MEETING" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="费用项名称"><el-input v-model="form.expenseItemName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="金额上限" prop="maxAmount"><el-input-number v-model="form.maxAmount" :min="0" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="超额审批阈值"><el-input-number v-model="form.approveAboveAmount" :min="0" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="提交时限(天)"><el-input-number v-model="form.submitDays" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="优先级"><el-input-number v-model="form.priority" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="需要发票">
            <el-select v-model="form.needInvoice" style="width:100%">
              <el-option :value="1" label="是" /><el-option :value="0" label="否" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="适用组织">
            <!-- orgId 存公司主键：手输一个不存在的组织号，政策会静默不生效 -->
            <el-tree-select v-model="form.orgId" :data="companyTree"
                            :props="{ label: 'companyName', value: 'id', children: 'children' }"
                            check-strictly filterable clearable placeholder="留空表示全组织" style="width:100%" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="生效起"><el-date-picker v-model="form.effectiveStart" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="生效止"><el-date-picker v-model="form.effectiveEnd" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 标准明细编辑弹窗 -->
    <el-dialog v-model="stdDlg" :title="stdForm.id ? '编辑标准' : '新增标准'" width="640px" destroy-on-close>
      <el-form ref="stdRef" :model="stdForm" :rules="stdRules" label-width="130px" size="small">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标准类型" prop="standardType">
              <el-select v-model="stdForm.standardType" style="width:100%">
                <el-option v-for="(v, k) in STD_TYPE" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="标准键" prop="standardKey"><el-input v-model="stdForm.standardKey" placeholder="如 HOTEL_RATE / MEAL" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="下限"><el-input-number v-model="stdForm.standardValueMin" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="上限"><el-input-number v-model="stdForm.standardValueMax" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单位"><el-input v-model="stdForm.standardUnit" placeholder="元 / 人天 / %" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="级别类型">
              <el-select v-model="stdForm.levelType" clearable style="width:100%">
                <el-option v-for="(v, k) in LEVEL_TYPE" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="级别值"><el-input v-model="stdForm.levelValue" placeholder="与级别类型对应，如 P5 / 经理" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="超标动作">
              <el-select v-model="stdForm.overtimeAction" clearable style="width:100%">
                <el-option v-for="(v, k) in OVERTIME" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="超标审批级别"><el-input v-model="stdForm.overtimeAuthLevel" placeholder="如 MANAGER / CFO" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="stdForm.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
        <el-alert v-if="stdForm.standardValueMin != null && stdForm.standardValueMax != null
                       && stdForm.standardValueMin > stdForm.standardValueMax" type="error" :closable="false" show-icon
                  title="下限大于上限，该标准将永远无法命中" />
      </el-form>
      <template #footer>
        <el-button @click="stdDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveStd">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { expensePolicyApi, policyStandardApi, policyVersionApi, orgApi } from '@/api'
import { hasPerm } from '@/utils/perm'
import SkeletonList from '@/components/SkeletonList.vue'

// 枚举名称表：取值严格对齐后端实体注释（ExpensePolicyStandard.standardType / levelType / overtimeAction）
const STD_TYPE = {
  AMOUNT: { label: '金额标准', type: 'success' },
  QUOTA:  { label: '限额',     type: 'warning' },
  RATE:   { label: '比例',     type: 'info' },
}
const LEVEL_TYPE = {
  EMPLOYEE_LEVEL: '员工级别',
  POSITION:       '职位',
  DEPARTMENT:     '部门',
}
const OVERTIME = {
  WARN:       { label: '警告',     type: 'info' },
  BLOCK:      { label: '阻止',     type: 'danger' },
  EXTRAAUTH:  { label: '附加审批', type: 'warning' },
}
const VER_STATUS = {
  DRAFT:     { label: '草稿',   type: 'info' },
  APPROVED:  { label: '已审批', type: 'warning' },
  ACTIVE:    { label: '生效中', type: 'success' },
  ARCHIVED:  { label: '已归档', type: '' },
}

const canList    = hasPerm('compliance:policy:list')
const canCreate  = hasPerm('compliance:policy:create')
const canUpdate  = hasPerm('compliance:policy:update')
const canDelete  = hasPerm('compliance:policy:delete')
const canStdCreate = hasPerm('compliance:policystandard:create')
const canStdUpdate = hasPerm('compliance:policystandard:update')
const canStdDelete = hasPerm('compliance:policystandard:delete')
const canVerCreate  = hasPerm('compliance:policyversion:create')
const canVerUpdate  = hasPerm('compliance:policyversion:update')
const canVerApprove = hasPerm('compliance:policyversion:approve')

const keyword = ref('')
const rows = ref([])
const loading = ref(false)
const current = ref(null)
const tab = ref('std')
const stds = ref([])
const vers = ref([])
const loadingStd = ref(false)
const loadingVer = ref(false)
const saving = ref(false)

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return rows.value
  return rows.value.filter(r => `${r.policyCode} ${r.policyName}`.toLowerCase().includes(k))
})

const dlg = ref(false)
const formRef = ref(null)
const form = reactive({})
const rules = {
  policyCode: [{ required: true, message: '请填写政策编码', trigger: 'blur' }],
  policyName: [{ required: true, message: '请填写政策名称', trigger: 'blur' }],
  maxAmount:  [{ required: true, message: '请填写金额上限', trigger: 'blur' }],
}

const stdDlg = ref(false)
const stdRef = ref(null)
const stdForm = reactive({})
const stdRules = {
  standardType: [{ required: true, message: '请选择标准类型', trigger: 'change' }],
  standardKey:  [{ required: true, message: '请填写标准键', trigger: 'blur' }],
}

async function load() {
  if (!canList) return
  loading.value = true
  try {
    const d = await expensePolicyApi.page({ pageNum: 1, pageSize: 200 })
    rows.value = d?.list || d?.records || []
    if (current.value) {
      const again = rows.value.find(r => r.id === current.value.id)
      if (again) current.value = again; else current.value = null
    }
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function onSelect(row) {
  if (!row) return
  current.value = row
  loadStd()
  loadVer()
}

async function loadStd() {
  if (!current.value) return
  loadingStd.value = true
  try { stds.value = (await policyStandardApi.list(current.value.id)) || [] }
  catch (e) { ElMessage.error(e.message) } finally { loadingStd.value = false }
}

async function loadVer() {
  if (!current.value) return
  loadingVer.value = true
  try { vers.value = (await policyVersionApi.list(current.value.id)) || [] }
  catch (e) { ElMessage.error(e.message) } finally { loadingVer.value = false }
}

function openCreate() {
  Object.keys(form).forEach(k => delete form[k])
  Object.assign(form, { needInvoice: 1, priority: 0, submitDays: 30 })
  dlg.value = true
}

function openEdit(row) {
  Object.keys(form).forEach(k => delete form[k])
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  dlg.value = true
}

async function save() {
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    if (form.id) await expensePolicyApi.update(form)
    else await expensePolicyApi.create(form)
    ElMessage.success('已保存')
    dlg.value = false
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

async function toggleStatus(row) {
  try {
    if (row.status === 1) await expensePolicyApi.disable(row.id)
    else await expensePolicyApi.enable(row.id)
    ElMessage.success('已' + (row.status === 1 ? '停用' : '启用'))
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

async function removePolicy(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除政策「${row.policyName}」？其下的标准明细与版本记录也可能一并失效。`,
      '删除确认', { type: 'warning' })
    await expensePolicyApi.remove(row.id)
    ElMessage.success('已删除')
    if (current.value?.id === row.id) current.value = null
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '')
  }
}

function openStdCreate() {
  Object.keys(stdForm).forEach(k => delete stdForm[k])
  Object.assign(stdForm, { policyId: current.value.id, standardType: 'AMOUNT' })
  stdDlg.value = true
}

function openStdEdit(row) {
  Object.keys(stdForm).forEach(k => delete stdForm[k])
  Object.assign(stdForm, JSON.parse(JSON.stringify(row)))
  stdDlg.value = true
}

async function saveStd() {
  try { await stdRef.value.validate() } catch { return }
  if (stdForm.standardValueMin != null && stdForm.standardValueMax != null
      && stdForm.standardValueMin > stdForm.standardValueMax) {
    ElMessage.error('下限不能大于上限')
    return
  }
  saving.value = true
  try {
    if (stdForm.id) await policyStandardApi.update(stdForm)
    else await policyStandardApi.create(stdForm)
    ElMessage.success('已保存')
    stdDlg.value = false
    await loadStd()
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

async function removeStd(row) {
  try {
    await ElMessageBox.confirm(`确认删除标准「${row.standardKey}」？`, '删除确认', { type: 'warning' })
    await policyStandardApi.remove(row.id)
    ElMessage.success('已删除')
    await loadStd()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '')
  }
}

async function snapshotVer() {
  try {
    await policyVersionApi.create({
      policyId: current.value.id,
      changeLog: '手工快照',
      snapshotData: JSON.stringify({ policy: current.value, standards: stds.value }),
    })
    ElMessage.success('已存为新版本（草稿）')
    await loadVer()
  } catch (e) { ElMessage.error(e.message) }
}

async function approveVer(row) {
  try {
    await policyVersionApi.approve(row.id, 1)
    ElMessage.success('已审批')
    await loadVer()
  } catch (e) { ElMessage.error(e.message) }
}

async function activateVer(row) {
  try { await policyVersionApi.activate(row.id); ElMessage.success('已生效'); await loadVer() }
  catch (e) { ElMessage.error(e.message) }
}

async function archiveVer(row) {
  try { await policyVersionApi.archive(row.id); ElMessage.success('已归档'); await loadVer() }
  catch (e) { ElMessage.error(e.message) }
}

const companyTree = ref([])
async function loadCompanyTree() {
  try { companyTree.value = await orgApi.companyTree() } catch { companyTree.value = [] }
}

onMounted(() => { load(); loadCompanyTree() })
</script>

<style scoped>
.mb12 { margin-bottom: 12px; }
.panel-header { display: flex; align-items: center; justify-content: space-between; }
.panel-title { font-weight: 600; }
</style>
