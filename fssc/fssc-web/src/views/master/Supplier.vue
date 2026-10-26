<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="supplier-page">
    <el-card shadow="never">
      <el-row :gutter="16">
        <!-- 左侧:供应商列表 -->
        <el-col :span="9">
          <div class="panel-header">
            <span class="panel-title">供应商(共 {{ total }})</span>
            <div>
              <el-button size="small" @click="doExport">导出</el-button>
              <el-button v-permission="'md:supplier:create'" type="primary" size="small" @click="openCreate">新增</el-button>
            </div>
          </div>
          <div style="display:flex; gap:6px; margin-bottom:8px">
            <el-input v-model="q.keyword" size="small" placeholder="编码/名称" clearable @keyup.enter="search" />
            <el-button size="small" type="primary" @click="search">查询</el-button>
          </div>
          <el-table :data="rows" v-loading="loading" size="small" border stripe highlight-current
            max-height="560" @row-click="onSelect" :row-class-name="rowClass" style="cursor:pointer">
            <el-table-column prop="supplierCode" label="编码" width="100" show-overflow-tooltip />
            <el-table-column prop="supplierName" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'PENDING'" size="small" type="warning">未核准</el-tag>
                <el-tag v-else size="small" :type="isSupplierActive(row.status) ? 'success' : 'info'">{{ isSupplierActive(row.status) ? '启用' : '停用' }}</el-tag>
                <el-tag v-if="row.blacklist === 1" size="small" type="danger" style="margin-left:2px">黑</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <!-- D-UI-06（2026-10-19）：此前非 PENDING 行没有任何显式控件，选中/进编辑只能靠整行点击，
                     键盘用户彻底不可达。补「编辑」按钮与 onSelect 同源（.stop 防冒泡再触发一次行点击）。 -->
                <el-button v-if="row.status !== 'PENDING'" link size="small" type="primary" @click.stop="onSelect(row)">编辑</el-button>
                <el-button v-if="row.status === 'PENDING'" v-permission="'md:supplier:approve'" type="success" size="small" @click="approveSupplier(row)">核准</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination small style="margin-top:8px; justify-content:flex-end"
            v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
            :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next"
            @size-change="() => { pageNum = 1; load() }" @current-change="load" />
        </el-col>

        <!-- 右侧:档案多 Tab -->
        <el-col :span="15">
          <div class="panel-header">
            <span class="panel-title">供应商档案{{ current.supplierName ? ' · ' + current.supplierName : '' }}</span>
            <div>
              <el-button v-if="current.id" v-permission="'md:supplier:update'" size="small" type="warning" @click="toggleEnable">{{ isSupplierActive(current.status) ? '停用' : '启用' }}</el-button>
              <el-button v-permission="['md:supplier:create', 'md:supplier:update']" type="primary" size="small" :loading="saving" @click="saveBasic">{{ current.id ? '保存资料' : '创建供应商' }}</el-button>
            </div>
          </div>

          <el-tabs v-model="tab">
            <!-- Tab1 基本信息 -->
            <el-tab-pane v-if="hasPerm('md:supplier:list')" label="基本信息" name="basic">
              <el-form ref="basicFormRef" :model="form" :rules="basicRules" label-width="110px" size="small" style="margin-top:8px">
                <div class="form-row">
                  <el-form-item label="供应商编码" prop="supplierCode" class="half"><el-input v-model="form.supplierCode" placeholder="唯一编码" :disabled="!!current.id" /></el-form-item>
                  <el-form-item label="供应商名称" prop="supplierName" class="half"><el-input v-model="form.supplierName" /></el-form-item>
                </div>
                <div class="form-row">
                  <el-form-item label="简称" class="half"><el-input v-model="form.shortName" /></el-form-item>
                  <el-form-item label="供应商类型" class="half">
                    <el-select v-model="form.supplierType" clearable style="width:100%" placeholder="如 物料/服务/物流">
                      <el-option v-for="t in SUPPLIER_TYPES" :key="t" :label="t" :value="t" />
                    </el-select>
                  </el-form-item>
                </div>
                <div class="form-row">
                  <el-form-item label="统一社会信用代码" class="half"><el-input v-model="form.creditCode" maxlength="18" /></el-form-item>
                  <el-form-item label="税号" class="half"><el-input v-model="form.taxNo" maxlength="64" /></el-form-item>
                </div>
                <div class="form-row">
                  <el-form-item label="纳税人资质" class="half">
                    <el-select v-model="form.taxType" style="width:100%">
                      <el-option label="一般纳税人" value="GENERAL" />
                      <el-option label="小规模纳税人" value="SMALL" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="发票类型" class="half">
                    <el-select v-model="form.invoiceType" clearable style="width:100%">
                      <el-option label="增值税专用发票" value="SPECIAL" />
                      <el-option label="增值税普通发票" value="NORMAL" />
                    </el-select>
                  </el-form-item>
                </div>
                <div class="form-row">
                  <el-form-item label="付款条件" class="half">
                    <el-select v-model="form.payTerms" clearable filterable allow-create style="width:100%" placeholder="如 月结30 / 货到付款">
                      <el-option v-for="t in PAY_TERMS" :key="t" :label="t" :value="t" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="结算币种" class="half">
                    <el-select v-model="form.currencyCode" clearable filterable style="width:100%">
                      <el-option v-for="c in currencies" :key="c" :label="c" :value="c" />
                    </el-select>
                  </el-form-item>
                </div>
                <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
                <div class="form-row">
                  <el-form-item label="电话" class="half"><el-input v-model="form.tel" maxlength="30" /></el-form-item>
                  <el-form-item label="黑名单" class="half"><el-switch v-model="form.blacklist" active-text="是" inactive-text="否" /></el-form-item>
                </div>
                <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- Tab2 银行账户(N) -->
            <el-tab-pane v-if="hasPerm('md:supplier:list')" label="银行账户" :name="`bank`">
              <div class="sub-ops">
                <el-button v-permission="'md:supplier:update'" size="small" type="primary" :disabled="!current.id" @click="subDialog = 'bank'">新增账户</el-button>
              </div>
              <el-table :data="banks" size="small" border max-height="430">
                <el-table-column prop="bankName" label="开户行" min-width="140" show-overflow-tooltip />
                <el-table-column prop="bankBranch" label="支行" min-width="120" show-overflow-tooltip />
                <el-table-column prop="bankAccount" label="账号" min-width="150" show-overflow-tooltip />
                <el-table-column prop="currencyCode" label="币种" width="70" align="center" />
                <el-table-column label="默认" width="60" align="center">
                  <template #default="{ row }"><el-tag v-if="row.isDefault === 1" size="small" type="success">默认</el-tag></template>
                </el-table-column>
                <el-table-column label="操作" width="70" align="center">
                  <template #default="{ row }">
                    <el-button v-permission="'md:supplier:update'" link size="small" type="danger" @click="delSub('bank', row.id)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- Tab3 联系人(N) -->
            <el-tab-pane v-if="hasPerm('md:supplier:list')" label="联系人" :name="`contact`">
              <div class="sub-ops">
                <el-button v-permission="'md:supplier:update'" size="small" type="primary" :disabled="!current.id" @click="subDialog = 'contact'">新增联系人</el-button>
              </div>
              <el-table :data="contacts" size="small" border max-height="430">
                <el-table-column prop="contactName" label="姓名" min-width="100" />
                <el-table-column prop="position" label="职务" min-width="100" />
                <el-table-column prop="phone" label="电话" min-width="120" />
                <el-table-column prop="email" label="邮箱" min-width="150" show-overflow-tooltip />
                <el-table-column label="主要" width="60" align="center">
                  <template #default="{ row }"><el-tag v-if="row.isPrimary === 1" size="small" type="success">主</el-tag></template>
                </el-table-column>
                <el-table-column label="操作" width="70" align="center">
                  <template #default="{ row }">
                    <el-button v-permission="'md:supplier:update'" link size="small" type="danger" @click="delSub('contact', row.id)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- Tab4 资质证照(N) -->
            <el-tab-pane v-if="hasPerm('md:supplier:list')" label="资质证照" :name="`qual`">
              <div class="sub-ops">
                <el-button v-permission="'md:supplier:update'" size="small" type="primary" :disabled="!current.id" @click="subDialog = 'qual'">新增证照</el-button>
              </div>
              <el-table :data="quals" size="small" border max-height="430">
                <el-table-column prop="qualName" label="证照名称" min-width="130" show-overflow-tooltip />
                <el-table-column prop="qualNo" label="证照编号" min-width="140" show-overflow-tooltip />
                <el-table-column prop="validFrom" label="生效日" width="100">
                  <template #default="{ row }">{{ (row.validFrom || '').slice(0, 10) || '—' }}</template>
                </el-table-column>
                <el-table-column label="有效期至" width="110">
                  <template #default="{ row }">
                    <span v-if="row.validTo && daysLeft(row.validTo) < 0" style="color:#f56c6c">{{ row.validTo.slice(0, 10) }}(过期)</span>
                    <span v-else-if="row.validTo && daysLeft(row.validTo) < 90" style="color:#e6a23c">{{ row.validTo.slice(0, 10) }}(临期)</span>
                    <span v-else>{{ (row.validTo || '').slice(0, 10) || '长期' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="70" align="center">
                  <template #default="{ row }">
                    <el-button v-permission="'md:supplier:update'" link size="small" type="danger" @click="delSub('qual', row.id)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-col>
      </el-row>
    </el-card>

    <!-- 子表新增弹窗 -->
    <el-dialog v-model="subDialogVisible" :title="SUB_TITLES[subDialog]" width="480px" destroy-on-close>
      <el-form ref="subFormRef" :model="subForm" :rules="SUB_RULES[subDialog]" label-width="90px" size="small">
        <template v-if="subDialog === 'bank'">
          <el-form-item label="开户行" prop="bankName"><el-input v-model="bankForm.bankName" /></el-form-item>
          <el-form-item label="支行"><el-input v-model="bankForm.bankBranch" /></el-form-item>
          <el-form-item label="银行账号" prop="bankAccount"><el-input v-model="bankForm.bankAccount" maxlength="64" /></el-form-item>
          <el-form-item label="币种"><el-input v-model="bankForm.currencyCode" placeholder="CNY" maxlength="10" /></el-form-item>
          <el-form-item label="默认账户"><el-switch v-model="bankForm.isDefault" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="bankForm.remark" /></el-form-item>
        </template>
        <template v-else-if="subDialog === 'contact'">
          <el-form-item label="姓名" prop="contactName"><el-input v-model="contactForm.contactName" maxlength="50" /></el-form-item>
          <el-form-item label="职务"><el-input v-model="contactForm.position" maxlength="50" /></el-form-item>
          <el-form-item label="电话" prop="phone"><el-input v-model="contactForm.phone" maxlength="30" /></el-form-item>
          <el-form-item label="邮箱" prop="email"><el-input v-model="contactForm.email" /></el-form-item>
          <el-form-item label="主要联系人"><el-switch v-model="contactForm.isPrimary" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="证照名称" prop="qualName"><el-input v-model="qualForm.qualName" placeholder="营业执照/开户许可/行业资质..." /></el-form-item>
          <el-form-item label="证照编号"><el-input v-model="qualForm.qualNo" /></el-form-item>
          <el-form-item label="生效日"><el-date-picker v-model="qualForm.validFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="有效期至"><el-date-picker v-model="qualForm.validTo" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="qualForm.remark" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button size="small" @click="subDialog = ''">取消</el-button>
        <el-button v-permission="'md:supplier:update'" type="primary" size="small" @click="saveSub">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 供应商专属档案页(41号审查 P1-8 主数据深化第一批)
 * 基本信息 + 银行账户(N) + 联系人(N) + 资质证照(N) 多 Tab 档案,
 * 替代原 MasterConfigTable 3 列通用表;证照临期/过期自动预警着色。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { supplierApi, configApi } from '../../api'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import { useFormRules } from '../../composables/useFormRules'
import { exportCsv } from '../../utils/csv'
import { hasPerm } from '@/utils/perm'

const SUPPLIER_TYPES = ['物料供应商', '服务供应商', '物流供应商', '代理商', '其他']
const PAY_TERMS = ['月结30', '月结60', '货到付款', '预付款', '预付30%尾款70%']
const SUB_TITLES = { bank: '新增银行账户', contact: '新增联系人', qual: '新增资质证照' }

const rows = ref([])
const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const q = reactive({ keyword: '' })
const current = ref({})
const tab = ref('basic')
const saving = ref(false)
const currencies = ref(['CNY', 'USD', 'EUR', 'HKD', 'JPY'])

const form = reactive({})
const banks = ref([])
const contacts = ref([])
const quals = ref([])

const subDialog = ref('')
const subDialogVisible = computed({ get: () => !!subDialog.value, set: (v) => { if (!v) subDialog.value = '' } })
const bankForm = reactive({})
const contactForm = reactive({})
const qualForm = reactive({})

const basicFormRef = ref(null)
const subFormRef = ref(null)
const basicGuard = useSubmitGuard(basicFormRef)
const subGuard = useSubmitGuard(subFormRef)
// 第1批 P0：录入规则统一走中央框架 useFormRules（原页面本地 rules 只有编码/名称必填，
// 税号/信用代码/电话/子表账号全部裸奔）。资质证照「有效期至不得早于生效日」是跨字段勾稽，
// 用页面级 computed 追加一条闭包 validator（async-validator 的 validator 拿不到 form 其他字段）。
const { rules: supplierRules } = useFormRules('SUPPLIER')
const { rules: subBankRules } = useFormRules('SUPPLIER_BANK')
const { rules: subContactRules } = useFormRules('SUPPLIER_CONTACT')
const qualBase = useFormRules('SUPPLIER_QUAL').rules
const qualRules = computed(() => ({
  ...qualBase.value,
  validTo: [{
    validator: (rule, value, cb) => {
      const from = qualForm.validFrom
      if (from && value && new Date(value) < new Date(from)) {
        return cb(new Error('有效期至不得早于生效日'))
      }
      cb()
    },
  }],
}))
const SUB_RULES = computed(() => ({
  bank: subBankRules.value,
  contact: subContactRules.value,
  qual: qualRules.value,
}))
// 三个子表弹窗共用一个 el-form，:model 随当前弹窗类型切换
const subForm = computed(() => (subDialog.value === 'bank' ? bankForm : subDialog.value === 'contact' ? contactForm : qualForm))

function rowClass({ row }) {
  return current.value.id === row.id ? 'row-active' : ''
}

async function approveSupplier(row) {
  try {
    await ElMessageBox.confirm(`确认核准供应商「${row.supplierName}」？核准后状态位翻为 ACTIVE，方可被业务引用。`, '状态位核准', { type: 'warning', closeOnClickModal: false })
  } catch (_) { return }
  try {
    await supplierApi.approve(row.id)
    ElMessage.success('已核准（ACTIVE）')
    load()
  } catch (e) { ElMessage.error(e?.message || '核准失败') }
}

async function load() {
  loading.value = true
  try {
    const r = await supplierApi.page({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: q.keyword || undefined })
    rows.value = r.list || r.records || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function search() { pageNum.value = 1; load() }

async function loadCurrencies() {
  try {
    // silent：币种候选是辅助下拉，取不到就退默认币种；无 system:config:list 的角色不该因此吃一条 403 提示
    const r = await configApi.page({ pageNum: 1, pageSize: 50, keyword: 'currency' }, { silent: true })
    const list = r.list || r.records || []
    if (list.length) currencies.value = list.map((x) => x.configKey || x.configValue).filter(Boolean)
  } catch { /* 保持默认币种 */ }
}

async function onSelect(row) {
  current.value = row
  Object.assign(form, row, { blacklist: row.blacklist === 1 })
  tab.value = 'basic'
  await loadSubs()
}

async function loadSubs() {
  if (!current.value.id) { banks.value = []; contacts.value = []; quals.value = []; return }
  try {
    const [b, c, ql] = await Promise.all([supplierApi.banks(current.value.id), supplierApi.contacts(current.value.id), supplierApi.quals(current.value.id)])
    banks.value = b || []; contacts.value = c || []; quals.value = ql || []
  } catch (e) { ElMessage.error(e.message) }
}

function openCreate() {
  current.value = {}
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { supplierType: '', taxType: 'GENERAL', blacklist: false })
  banks.value = []; contacts.value = []; quals.value = []
  tab.value = 'basic'
  basicFormRef.value?.clearValidate()
}

async function saveBasic() {
  saving.value = true
  try {
    await basicGuard.submit(async () => {
      const body = { ...form, blacklist: form.blacklist ? 1 : 0 }
      if (current.value.id) {
        body.id = current.value.id
        await supplierApi.update(body)
        ElMessage.success('资料已保存')
      } else {
        const id = await supplierApi.create(body)
        ElMessage.success('供应商已创建(准入审批已发起)')
        current.value = { id, supplierName: form.supplierName, status: 'NORMAL' }
      }
      await load()
    })
  } catch (e) { ElMessage.error(e.message) } finally { saving.value = false }
}

// 历史种子/审批写入过 ACTIVE，新建与停用写 NORMAL/DISABLED；ACTIVE 与 NORMAL 同为启用态
const isSupplierActive = (s) => s === 'NORMAL' || s === 'ACTIVE'

async function toggleEnable() {
  const enable = !isSupplierActive(current.value.status)
  await supplierApi.enable(current.value.id, enable)
  ElMessage.success(enable ? '已启用' : '已停用')
  await load()
  const fresh = rows.value.find((r) => r.id === current.value.id)
  if (fresh) onSelect(fresh)
}

async function saveSub() {
  const id = current.value.id
  if (!id) return ElMessage.warning('请先选择或创建供应商')
  try {
    await subGuard.submit(async () => {
      if (subDialog.value === 'bank') {
        await supplierApi.addBank(id, { ...bankForm, isDefault: bankForm.isDefault ? 1 : 0, currencyCode: bankForm.currencyCode || 'CNY' })
      } else if (subDialog.value === 'contact') {
        await supplierApi.addContact(id, { ...contactForm, isPrimary: contactForm.isPrimary ? 1 : 0 })
      } else {
        await supplierApi.addQual(id, { ...qualForm })
      }
      ElMessage.success('已保存')
      subDialog.value = ''
      Object.keys(bankForm).forEach((k) => delete bankForm[k])
      Object.keys(contactForm).forEach((k) => delete contactForm[k])
      Object.keys(qualForm).forEach((k) => delete qualForm[k])
      await loadSubs()
    })
  } catch (e) { ElMessage.error(e.message) }
}

async function delSub(kind, subId) {
  await ElMessageBox.confirm('确认删除该条记录？', '删除', { type: 'warning' })
  try {
    if (kind === 'bank') await supplierApi.deleteBank(subId)
    else if (kind === 'contact') await supplierApi.deleteContact(subId)
    else await supplierApi.deleteQual(subId)
    ElMessage.success('已删除')
    await loadSubs()
  } catch (e) { ElMessage.error(e.message) }
}

function daysLeft(d) {
  return Math.ceil((new Date(d) - new Date()) / 86400000)
}

function doExport() {
  exportCsv(rows.value, [
    { prop: 'supplierCode', label: '编码' }, { prop: 'supplierName', label: '名称' }, { prop: 'shortName', label: '简称' },
    { prop: 'supplierType', label: '类型' }, { prop: 'creditCode', label: '统一社会信用代码' }, { prop: 'taxNo', label: '税号' },
    { prop: 'taxType', label: '纳税人资质' }, { prop: 'invoiceType', label: '发票类型' }, { prop: 'payTerms', label: '付款条件' },
    { prop: 'currencyCode', label: '结算币种' }, { prop: 'address', label: '地址' }, { prop: 'tel', label: '电话' },
    { prop: 'contactName', label: '联系人' }, { prop: 'contactPhone', label: '联系电话' },
    { prop: 'blacklist', label: '黑名单(1是)' }, { prop: 'status', label: '状态' }, { prop: 'remark', label: '备注' },
  ], 'Supplier')
}

onMounted(() => { load(); loadCurrencies() })
</script>

<style scoped>
.supplier-page { min-height: 100%; }
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.panel-title { font-size: 15px; font-weight: 600; border-left: 4px solid #409eff; padding-left: 8px; }
.form-row { display: flex; gap: 12px; }
.half { flex: 1; }
.sub-ops { display: flex; justify-content: flex-end; margin-bottom: 8px; }
:deep(.row-active) { background-color: #ecf5ff !important; }
</style>
