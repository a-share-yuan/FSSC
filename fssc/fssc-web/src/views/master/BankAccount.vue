<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="bank-account-page">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>银行账户管理（账户全要素维护 · 印鉴/网银变更审批）</span>
          <div class="ops">
            <el-select v-model="q.companyId" size="small" style="width: 160px" placeholder="组织" clearable filterable @change="search">
              <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-input v-model="q.keyword" size="small" style="width: 180px" placeholder="账号/户名/开户行" clearable @keyup.enter="search" />
            <el-button size="small" type="primary" @click="search">查询</el-button>
            <el-button size="small" @click="doExport">导出</el-button>
            <el-button v-permission="'md:bankaccount:create'" size="small" type="success" @click="openForm()">新增账户</el-button>
          </div>
        </div>
      </template>

      <SkeletonList v-if="loading" :rows="10" :cols="8" />
      <el-table v-else :data="rows" size="small" border stripe
        @row-dblclick="onRowDblclick" :row-class-name="rowClass"
        :empty-text="denied ? '无权限查看：当前账号缺少银行账户读权限，请联系管理员开通' : '暂无数据'">
        <el-table-column prop="accountCode" label="账户编码" width="110" show-overflow-tooltip />
        <el-table-column prop="accountName" label="账户名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="bankName" label="开户行" min-width="140" show-overflow-tooltip />
        <el-table-column prop="bankBranch" label="支行" min-width="120" show-overflow-tooltip />
        <!-- R5-D-051：账号列展示层脱敏（首尾各留4位）；编辑框仍回填全号（openForm 拷贝原行），保存不受影响 -->
        <el-table-column prop="bankAccount" label="银行账号" min-width="160" show-overflow-tooltip :formatter="(row) => maskAccount(row.bankAccount)" />
        <el-table-column prop="bankCode" label="联行号" width="120" show-overflow-tooltip />
        <el-table-column prop="currencyCode" label="币种" width="70" align="center" />
        <el-table-column label="账户性质" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.accountType === 'BASIC'" size="small" type="danger">基本户</el-tag>
            <span v-else>{{ accountTypeLabel(row.accountType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="余额" width="130" align="right">
          <template #default="{ row }">{{ fmtMoney(row.balance) }}</template>
        </el-table-column>
        <el-table-column label="组织" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ companyName(row.companyId) }}</template>
        </el-table-column>
        <el-table-column label="默认" width="60" align="center">
          <template #default="{ row }"><el-tag v-if="row.isDefault === 1" size="small" type="success">默认</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'info'">{{ row.status === 'NORMAL' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'md:bankaccount:update'" link size="small" type="primary" @click="openForm(row)">编辑</el-button>
            <el-button v-permission="'md:bankaccount:update'" link size="small" :type="row.status === 'NORMAL' ? 'warning' : 'success'" @click="toggleEnable(row)">{{ row.status === 'NORMAL' ? '停用' : '启用' }}</el-button>
            <el-button v-permission="'md:bankaccount:update'" link size="small" type="warning" @click="startSealChange(row)">印鉴变更</el-button>
            <el-button v-permission="'md:bankaccount:delete'" link size="small" type="danger" @click="del(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:10px; justify-content:flex-end"
        v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
        :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="() => { pageNum = 1; load() }" @current-change="load" />
    </el-card>

    <!-- 新增/编辑账户 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑银行账户' : '新增银行账户'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px" size="small">
        <div class="form-row">
          <el-form-item label="账户编码" class="half"><el-input v-model="form.accountCode" readonly placeholder="保存后由系统自动生成" /></el-form-item>
          <el-form-item label="账户名称" prop="accountName" class="half"><el-input v-model="form.accountName" /></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="所属组织" prop="companyId" class="half">
            <el-select v-model="form.companyId" filterable style="width:100%">
              <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="币种" class="half">
            <el-select v-model="form.currencyCode" filterable style="width:100%">
              <el-option v-for="c in ['CNY', 'USD', 'EUR', 'HKD', 'JPY', 'GBP', 'SGD']" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="开户行" prop="bankName" class="half"><el-input v-model="form.bankName" /></el-form-item>
          <el-form-item label="支行" class="half"><el-input v-model="form.bankBranch" /></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="银行账号" prop="bankAccount" class="half"><el-input v-model="form.bankAccount" maxlength="64" /></el-form-item>
          <el-form-item label="联行号" prop="bankCode" class="half"><el-input v-model="form.bankCode" maxlength="64" /></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="账户性质" prop="accountType" class="half">
            <el-select v-model="form.accountType" clearable filterable style="width:100%" placeholder="基本/一般/专用/临时存款账户">
              <el-option v-for="t in ACCOUNT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="期初余额" prop="balance" class="half"><el-input-number v-model="form.balance" :precision="2" :min="0" style="width:100%" /></el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="默认账户" class="half"><el-switch v-model="form.isDefault" /></el-form-item>
        </div>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="formVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 银行账户专属页(41号审查 P1-8 主数据深化第一批)
 * 全要素列(编码/户名/开户行/支行/账号/联行号/币种/余额/组织/默认/状态)
 * + 组织筛选 + 印鉴/网银变更审批入口(TR01-02) + 导出,替代原通用 3 列表。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { masterApi, orgApi } from '../../api'
import { readBillDeepLink } from '../../utils/bizEntry'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import { commonRules } from '../../composables/useFormRules'
import { exportCsv } from '../../utils/csv'
import { maskAccount } from '../../utils/mask'
import { hasPerm } from '../../utils/perm'
import SkeletonList from '../../components/SkeletonList.vue'

// D-DEC-158：账户性质（人民银行银行结算账户分类）——基本户唯一性判据，与后端 ACCOUNT_TYPES 同名称表
const ACCOUNT_TYPES = [
  { value: 'BASIC', label: '基本存款账户' },
  { value: 'GENERAL', label: '一般存款账户' },
  { value: 'SPECIAL', label: '专用存款账户' },
  { value: 'TEMPORARY', label: '临时存款账户' },
]
const accountTypeLabel = (t) => (ACCOUNT_TYPES.find((x) => x.value === t)?.label) || (t || '—')

const rows = ref([])
const companies = ref([])
const loading = ref(false)
const saving = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const q = reactive({ companyId: null, keyword: '' })
/**
 * 本单上下文（D-DEC-153 的主数据半）：TR_BANK_MASTER / TR_SEAL_CHANGE 的「去处理」现在
 * 解析到本页（与后端 BillRouteRegistry.TABLE_LEDGER 的 md_bank_account→/master/bank 同判据），
 * 深链带来的是 ?billId=<md_bank_account.id>——这张表没有业务单号列（注册表 bizNoCol 为空＝预期），
 * 所以只能按主键定位：首屏一次性取回宽页后筛出本单，其后翻页恢复正常页宽。
 * 定位失败（账户已被删/跨法人不可见）时回落全量列表，不显示"查无此单"的假象。
 */
const DEEP_LOCATE_SIZE = 500
const deepLink = readBillDeepLink(useRoute())
const deepLocateOnce = ref(deepLink.hasAny)
if (deepLink.billNo) q.keyword = deepLink.billNo
const formVisible = ref(false)
const form = reactive({})
const formRef = ref(null)
const guard = useSubmitGuard(formRef)
const formRules = computed(() => ({
  accountName: [{ required: true, message: '请输入账户名称' }],
  companyId: [{ required: true, message: '请选择所属组织' }],
  bankName: [{ required: true, message: '请输入开户行' }],
  // P0 资金路由字段：银行账号必填 + 格式（6-30 位数字）；唯一性由后端默认处理（同租户同号拒登记）
  bankAccount: [...commonRules.required('请输入银行账号'), ...commonRules.bankAccount()],
  // 联行号选填，填了就必须是 12 位数字
  bankCode: commonRules.bankCode(),
  // 期初余额不能为负（后端 @DecimalMin 默认处理）
  balance: commonRules.amountRange(0, null, '期初余额不能为负'),
  // D-DEC-158：账户性质是「一司一基本户」判据的来源，新增即必选；历史现有账户允许先改其它字段
  accountType: form.id ? [] : [{ required: true, message: '请选择账户性质' }],
}))

function companyName(id) {
  const c = companies.value.find((x) => x.id === id)
  return c ? c.name : (id != null ? `公司${id}` : '—')
}

function rowClass({ row }) {
  return row.status !== 'NORMAL' ? 'row-disabled' : ''
}

function fmtMoney(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// R5-D-174：4030 权限拒绝独立空态（改前与「暂无数据」同形，无权限被读成没数据）
const denied = ref(false)

async function load() {
  loading.value = true
  try {
    const once = deepLocateOnce.value
    const r = await masterApi.bank.page({
      pageNum: once ? 1 : pageNum.value, pageSize: once ? DEEP_LOCATE_SIZE : pageSize.value,
      companyId: q.companyId || undefined, keyword: q.keyword || undefined,
    })
    let list = r.list || r.records || []
    total.value = r.total || 0
    if (once) {
      deepLocateOnce.value = false
      if (deepLink.billId != null) {
        const hit = list.filter((x) => String(x.id) === String(deepLink.billId))
        // 命中 ⇒ 只留本单（三节审批屏上第一次有"这就是被审的那个账户"）；
        // 取不到 ⇒ 保持全量列表，不制造「账户不存在」的假象（可能是跨法人或已删）。
        if (hit.length) { list = hit; total.value = hit.length }
      }
    }
    rows.value = list
    denied.value = false
  } catch (e) {
    // R5-D-174：4030（无账户读权限）置权限空态，不再落到「暂无数据」
    if (e?.code === 4030) { denied.value = true; return }
    ElMessage.error(e.message)
  } finally { loading.value = false }
}

async function loadCompanies() {
  try {
    const flat = []
    const walk = (arr) => { (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
    walk(await orgApi.companyTree())
    companies.value = flat
  } catch { /* 不阻断 */ }
}

function search() { pageNum.value = 1; load() }

const canUpdate = computed(() => hasPerm('md:bankaccount:update'))

// 双击进编辑与「编辑」按钮同权限：只读账号双击不应打开可保存的表单
function onRowDblclick(row) {
  if (canUpdate.value) openForm(row)
}

function openForm(row) {
  Object.keys(form).forEach((k) => delete form[k])
  if (row) Object.assign(form, row, { isDefault: row.isDefault === 1 })
  else Object.assign(form, { currencyCode: 'CNY', balance: 0, isDefault: false })
  formVisible.value = true
}

async function save() {
  saving.value = true
  try {
    await guard.submit(async () => {
      const body = { ...form, isDefault: form.isDefault ? 1 : 0 }
      if (form.id) await masterApi.bank.update(body)
      else await masterApi.bank.create(body)
      ElMessage.success('已保存')
      formVisible.value = false
      await load()
    })
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

async function toggleEnable(row) {
  const enable = row.status !== 'NORMAL'
  await masterApi.bank.enable(row.id, enable)
  ElMessage.success(enable ? '已启用' : '已停用')
  await load()
}

async function startSealChange(row) {
  await ElMessageBox.confirm(`确认为账户 [${row.accountName}] 发起印鉴/网银变更审批(TR01-02)?`, '印鉴变更', { type: 'warning' })
  try {
    const proc = await masterApi.bank.sealChange(row.id)
    ElMessage.success('印鉴变更审批已发起' + (proc ? ':' + proc : ''))
  } catch (e) { ElMessage.error(e.message) }
}

async function del(row) {
  await ElMessageBox.confirm(`确认删除账户 [${row.accountName}]?`, '删除', { type: 'warning' })
  try {
    await masterApi.bank.delete(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

function doExport() {
  // R5-D-051：CSV 与屏上同一规则——投影时打码 bankAccount，堵住「列表打码、导出放全号」的旁路
  exportCsv(rows.value.map((r) => ({ ...r, bankAccount: maskAccount(r.bankAccount) })), [
    { prop: 'accountCode', label: '账户编码' }, { prop: 'accountName', label: '账户名称' },
    { prop: 'bankName', label: '开户行' }, { prop: 'bankBranch', label: '支行' },
    { prop: 'bankAccount', label: '银行账号' }, { prop: 'bankCode', label: '联行号' },
    { prop: 'currencyCode', label: '币种' }, { prop: 'balance', label: '余额' },
    { prop: 'isDefault', label: '默认(1是)' }, { prop: 'status', label: '状态' }, { prop: 'remark', label: '备注' },
  ], 'BankAccount')
}

onMounted(() => { load(); loadCompanies() })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
.ops { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.form-row { display: flex; gap: 12px; }
.half { flex: 1; }
:deep(.row-disabled) { color: #909399; }
</style>
