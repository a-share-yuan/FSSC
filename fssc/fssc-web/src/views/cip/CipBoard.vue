<template>
  <div>
    <el-card>
    <template #header>
      <div style="display: flex; gap: 16px; align-items: center; flex-wrap: wrap">
        <b>在建工程中心</b>
        <span style="color: #909399; font-size: 12px">在建工程卡片 / 成本归集 / 转固定资产 —— 归集成本、监督余额、转固进固定资产全流程</span>
        <el-button type="info" size="small" style="margin-left: auto" @click="load">刷新</el-button>
      </div>
    </template>

    <!-- 工具栏 -->
    <div style="display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; align-items: center">
      <el-input v-model="keyword" placeholder="搜索编码/名称/公司" clearable style="width: 240px" @keyup.enter="search" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="keyword = ''; search()">重置</el-button>
      <el-button type="success" @click="openCreate">新增在建工程</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="rows" size="small" border stripe v-loading="loading" empty-text="暂无数据">
      <el-table-column prop="cipCode" label="编码" width="130" />
      <el-table-column prop="cipName" label="名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="projectCategory" label="工程类别" width="100" align="center">
        <template #default="{ row }">{{ categoryLabel(row.projectCategory) }}</template>
      </el-table-column>
      <el-table-column prop="companyName" label="所属公司" width="130" show-overflow-tooltip />
      <el-table-column prop="accruedAmount" label="累计归集" width="120" align="right">
        <template #default="{ row }"><span style="font-family: monospace">{{ fmt(row.accruedAmount) }}</span></template>
      </el-table-column>
      <el-table-column prop="budgetAmount" label="预算" width="120" align="right">
        <template #default="{ row }"><span style="font-family: monospace">{{ fmt(row.budgetAmount) }}</span></template>
      </el-table-column>
      <el-table-column prop="transferDate" label="转固日期" width="105" align="center" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" :disabled="!editable(row)" @click="openEdit(row)">编辑</el-button>
          <el-button link size="small" type="warning" :disabled="!editable(row)" @click="openCost(row)">成本归集</el-button>
          <el-button link size="small" type="success" :disabled="!canTransfer(row)" @click="openTransfer(row)">转固</el-button>
          <el-button link size="small" type="danger" :disabled="!editable(row)" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total > pageSize" class="pager" background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="pageNum" @current-change="(p) => { pageNum = p; load() }" />
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dlgForm" :title="form.id ? '编辑在建工程' : '新增在建工程'" width="680px" destroy-on-close>
      <el-form :model="form" ref="formRef" :rules="formRules" label-width="100px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="编码" prop="cipCode"><el-input v-model="form.cipCode" placeholder="唯一编码" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="名称" prop="cipName"><el-input v-model="form.cipName" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="所属公司" prop="companyId">
              <el-select v-model="form.companyId" filterable clearable placeholder="请选择公司" style="width:100%">
                <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工程类别">
              <el-select v-model="form.projectCategory" clearable placeholder="建筑安装/设备/待摊/资本化利息" style="width:100%">
                <el-option v-for="o in CATEGORIES" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="负责人"><el-input v-model="form.manager" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="归属部门">
              <el-select v-model="form.deptId" filterable clearable placeholder="选择公司后联动" style="width:100%">
                <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="开工日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="预计完工"><el-date-picker v-model="form.expectedEndDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="预算总额"><el-input-number v-model="form.budgetAmount" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dlgForm = false">取消</el-button>
        <el-button type="primary" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 成本归集明细 -->
    <el-dialog v-model="dlgCost" :title="'成本归集 —— ' + (costCip ? costCip.cipName : '')" width="760px" destroy-on-close>
      <el-form ref="costFormRef" :model="costForm" :rules="costRules" :inline="true" size="small"
               style="display:flex; gap:8px; flex-wrap:wrap; align-items:center; margin-bottom:10px">
        <el-form-item label="归集类型" prop="costType" style="margin-bottom:0">
          <el-select v-model="costForm.costType" placeholder="归集类型" style="width: 130px">
            <el-option v-for="o in COST_TYPES" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" prop="amount" style="margin-bottom:0">
          <el-input-number v-model="costForm.amount" :min="0" :precision="2" placeholder="金额" />
        </el-form-item>
        <el-form-item label="发生日期" style="margin-bottom:0">
          <el-date-picker v-model="costForm.occurDate" type="date" value-format="YYYY-MM-DD" placeholder="发生日期" style="width: 140px" />
        </el-form-item>
        <el-form-item label="说明" style="margin-bottom:0">
          <el-input v-model="costForm.description" placeholder="说明" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item style="margin-bottom:0">
          <el-button type="primary" @click="addCostRow">新增归集</el-button>
        </el-form-item>
      </el-form>
      <div style="margin-bottom:10px; color:#606266">累计归集：<b style="font-family:monospace; color:#409EFF">{{ fmt(costCip ? costCip.accruedAmount : 0) }}</b></div>
      <el-table :data="costRows" size="small" border stripe empty-text="暂无归集明细">
        <el-table-column prop="costType" label="类型" width="110">
          <template #default="{ row }">{{ costTypeLabel(row.costType) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }"><span style="font-family:monospace">{{ fmt(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column prop="occurDate" label="发生日期" width="110" align="center" />
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="70">
          <template #default="{ row }"><el-button link size="small" type="danger" @click="delCostRow(row)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 转固定资产 -->
    <el-dialog v-model="dlgTransfer" :title="'在建工程转固定资产 —— ' + (transferRow ? transferRow.cipName : '')" width="560px" destroy-on-close>
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-width="120px" size="small">
        <el-form-item label="转固金额"><el-input :model-value="fmt(transferRow ? transferRow.accruedAmount : 0)" disabled style="width:100%" /></el-form-item>
        <el-form-item label="转固日期" prop="transferDate"><el-date-picker v-model="transferForm.transferDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="资产类别" prop="categoryId">
          <el-select v-model="transferForm.categoryId" filterable clearable placeholder="请选择固定资产类别" style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="使用部门" prop="deptId">
          <el-select v-model="transferForm.deptId" filterable clearable placeholder="请选择部门" style="width:100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="折旧方法">
          <el-select v-model="transferForm.depreciationMethod" style="width:100%">
            <el-option label="直线法" value="LINEAR" />
            <el-option label="加速折旧" value="ACCELERATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="折旧月数" prop="depreciationMonths"><el-input-number v-model="transferForm.depreciationMonths" :min="1" style="width:100%" /></el-form-item>
        <el-form-item label="税法年限(月)"><el-input-number v-model="transferForm.taxLifeMonths" :precision="0" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="transferForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgTransfer = false">取消</el-button>
        <el-button type="primary" @click="confirmTransfer">提交转固审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { statusLabel } from '@/constants/billStatus'
import { PROJECT_CATEGORY, projectCategoryLabel } from '@/constants/enums'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cipApi, orgApi, assetApi } from '../../api'
import { useFormRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'

// ========== 枚举 ==========
// 工程类别词表已并入 constants/enums.js 的 PROJECT_CATEGORY（本地原缺 TECH_REVAMP，导致该值显示为「—」）
const CATEGORIES = Object.entries(PROJECT_CATEGORY).map(([value, label]) => ({ value, label }))
const COST_TYPES = [
  { label: '领料', value: 'MATERIAL' },
  { label: '设备', value: 'EQUIPMENT' },
  { label: '人工', value: 'LABOR' },
  { label: '待摊', value: 'DEFERRED' },
  { label: '资本化利息', value: 'INTEREST' },
]

const categoryLabel = (v) => projectCategoryLabel(v) || '—'
const costTypeLabel = (v) => COST_TYPES.find((o) => o.value === v)?.label || '—'
const statusType = (v) => (v === 'NORMAL' ? 'success' : v === 'DISABLED' ? 'info' : v === 'TRANSFERRING' ? 'warning' : 'danger')
const editable = (row) => row.status === 'NORMAL' || row.status === 'DISABLED'
const canTransfer = (row) => editable(row) && Number(row.accruedAmount || 0) > 0

// ========== 状态 ==========
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const loading = ref(false)

const companies = ref([])
const depts = ref([])
const categories = ref([])

const dlgForm = ref(false)
const formRef = ref(null)
const form = reactive({})

const dlgCost = ref(false)
const costCip = ref(null)
const costRows = ref([])
const costForm = reactive({ costType: '', amount: null, occurDate: '', description: '' })

const dlgTransfer = ref(false)
const transferRow = ref(null)
const transferForm = reactive({ transferDate: '', categoryId: null, deptId: null, depreciationMethod: 'LINEAR', depreciationMonths: null, taxLifeMonths: null, remark: '' })

// 校验规则唯一口径＝中央登记表（useFormRules）；提交一律走 useSubmitGuard，不在页面手写 if 判空
const { rules: formRules } = useFormRules('CIP_CARD')
const { rules: costRules } = useFormRules('CIP_COST')
const { rules: transferRules } = useFormRules('CIP_TRANSFER')
const costFormRef = ref(null)
const transferFormRef = ref(null)
const { submit: guardSaveForm } = useSubmitGuard(formRef)
const { submit: guardAddCost } = useSubmitGuard(costFormRef)
const { submit: guardTransfer } = useSubmitGuard(transferFormRef)

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// ========== 列表 ==========
async function load() {
  loading.value = true
  try {
    const d = await cipApi.page({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value })
    rows.value = d?.list || d?.records || []
    total.value = Number(d.total || 0)
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}
function search() { pageNum.value = 1; load() }

// ========== 主数据 ==========
async function loadCompanies() {
  try {
    const flat = []
    const walk = (arr) => { (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
    walk(await orgApi.companyTree())
    companies.value = flat
  } catch (e) { ElMessage.error(e.message) }
}
async function loadDepts(companyId) {
  try {
    const flat = []
    const walk = (arr) => { (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.deptName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
    walk(await orgApi.deptTree(companyId))
    depts.value = flat
  } catch (e) { /* ignore */ }
}
async function loadCategories() {
  try { categories.value = (await assetApi.categoryPage({ pageNum: 1, pageSize: 200 })).list || [] } catch (e) { /* ignore */ }
}

// ========== 新增/编辑 ==========
function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { status: 'NORMAL', accruedAmount: 0, transferredAmount: 0 })
  dlgForm.value = true
}
function openEdit(row) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { ...row })
  loadDepts(row.companyId)
  dlgForm.value = true
}
async function saveForm() {
  await guardSaveForm(async () => {
    try {
      const body = { ...form }
      if (body.id) { await cipApi.update(body) } else { await cipApi.create(body) }
      ElMessage.success('已保存'); dlgForm.value = false; load()
    } catch (e) { ElMessage.error(e.message) }
  })
}
async function del(row) {
  try {
    await ElMessageBox.confirm('确认删除该在建工程？', '提示', { type: 'warning' })
    await cipApi.delete(row.id)
    ElMessage.success('已删除'); load()
  } catch (e) { /* cancelled */ }
}

// ========== 成本归集 ==========
async function openCost(row) {
  costCip.value = row
  costForm.costType = ''; costForm.amount = null; costForm.occurDate = ''; costForm.description = ''
  dlgCost.value = true
  await loadCosts(row.id)
}
async function loadCosts(cipId) {
  try { costRows.value = (await cipApi.costPage(cipId, { pageNum: 1, pageSize: 500 })).list || [] } catch (e) { ElMessage.error(e.message) }
}
async function addCostRow() {
  await guardAddCost(async () => {
    try {
      await cipApi.costAdd(costCip.value.id, { costType: costForm.costType, amount: costForm.amount, occurDate: costForm.occurDate || null, description: costForm.description })
      ElMessage.success('已归集'); costForm.costType = ''; costForm.amount = null; costForm.occurDate = ''; costForm.description = ''
      await loadCosts(costCip.value.id); load()
    } catch (e) { ElMessage.error(e.message) }
  })
}
async function delCostRow(row) {
  try {
    await cipApi.costDelete(row.id)
    ElMessage.success('已删除'); await loadCosts(costCip.value.id); load()
  } catch (e) { ElMessage.error(e.message) }
}

// ========== 转固定资产 ==========
function openTransfer(row) {
  transferRow.value = row
  Object.assign(transferForm, { transferDate: '', categoryId: null, deptId: row.deptId || null, depreciationMethod: 'LINEAR', depreciationMonths: null, taxLifeMonths: null, remark: '' })
  loadDepts(row.companyId)
  dlgTransfer.value = true
}
async function confirmTransfer() {
  await guardTransfer(async () => {
    try {
      await cipApi.transferToAsset(transferRow.value.id, { ...transferForm })
      ElMessage.success('已提交转固审批'); dlgTransfer.value = false; load()
    } catch (e) { ElMessage.error(e.message) }
  })
}

onMounted(async () => {
  await Promise.all([load(), loadCompanies(), loadDepts(), loadCategories()])
})
</script>

<style scoped>
.pager { margin-top: 10px; justify-content: flex-end; }
</style>