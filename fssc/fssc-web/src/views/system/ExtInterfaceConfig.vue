<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="ext-interface-config">
    <el-tabs v-model="activeTab" @tab-change="loadData">
      <el-tab-pane v-for="t in visibleTabs" :key="t.name" :label="t.label" :name="t.name">
        <template #label><el-icon><component :is="t.icon" /></el-icon>&nbsp;{{ t.label }}{{ catCount(t.name) }}</template>
      </el-tab-pane>
    </el-tabs>

    <!-- OCR 通道设置：独立表单（原「OCR通道配置」页面合并于此） -->
    <OcrConfig v-if="activeTab === 'OCR_CHANNEL'" />

    <!-- 工具栏 -->
    <div v-else style="display: flex; gap: 8px; margin-bottom: 16px; align-items: center; flex-wrap: wrap">
      <el-input v-model="keyword" :placeholder="$t('system.extInterface.phSearchIf')" clearable style="width: 280px"
                @clear="loadData" @keyup.enter="loadData" />
      <el-button type="primary" @click="loadData">{{ $t('common.search') }}</el-button>
      <el-button v-if="canCreate" type="success" @click="openCreate">{{ $t('system.extInterface.btnAddIf') }}</el-button>
      <router-link to="/system/integration-logs" style="margin-left: 8px">
        <el-button type="info" link>{{ $t('system.extInterface.lnkCallLogs') }}</el-button>
      </router-link>
      <span style="margin-left: auto; color: #909399; font-size: 13px">
        {{ $t('system.extInterface.statSummary', { v1: pager.total.value, v2: enabledCount }) }}</span>
    </div>

    <!-- 表格 -->
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="pagedData" v-else border stripe style="width: 100%" @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
      <el-table-column prop="interfaceName" :label="$t('system.extInterface.colIfName')" min-width="160" show-overflow-tooltip />
      <el-table-column prop="interfaceCode" :label="$t('system.common.colIfCode')" width="180" show-overflow-tooltip />
      <el-table-column prop="provider" :label="$t('system.extInterface.colProvider')" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="providerTagType(row.provider)">{{ providerText(row.provider) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="apiUrl" :label="$t('system.common.colApiUrl')" min-width="250" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.apiUrl" style="font-size: 12px; color: var(--app-text-secondary, #606266)">{{ row.apiUrl }}</span>
          <span v-else style="color: #c0c4cc">{{ $t('system.extInterface.stNotConfigured') }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="modelName" :label="$t('system.extInterface.colModel')" width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.modelName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="$t('common.status')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
            {{ row.status === 'ENABLED' ? t('system.common.stEnabled') : t('system.extInterface.stDeactivated') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="isDefault" :label="$t('system.extInterface.colDefault')" width="70" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault === 1" type="warning" size="small">{{ $t('system.extInterface.colDefault') }}</el-tag>
          <span v-else style="color: #c0c4cc">-</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('system.extInterface.colTestResult')" width="110" align="center">
        <template #default="{ row }">
          <template v-if="row.lastTestResult">
            <el-tag :type="row.lastTestResult === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.lastTestResult === 'SUCCESS' ? t('field.approve') : t('system.common.stFailed') }}
            </el-tag>
            <el-tooltip :content="row.lastTestTime + ' ' + (row.lastTestMsg || '')" placement="top">
              <el-icon style="margin-left: 4px; color: #909399; cursor: help"><InfoFilled /></el-icon>
            </el-tooltip>
          </template>
          <span v-else style="color: #c0c4cc">{{ $t('system.extInterface.stNotTested') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canUpdate" link type="primary" size="small" @click="openEdit(row)">{{ $t('system.common.btnEdit') }}</el-button>
          <el-button v-if="canUpdate" link :type="row.status === 'ENABLED' ? 'warning' : 'success'" size="small"
                     @click="toggleStatus(row)">
            {{ row.status === 'ENABLED' ? t('system.common.btnDeactivate') : t('system.common.btnEnable') }}
          </el-button>
          <el-button v-if="canTest" link type="primary" size="small" :loading="testingId === row.id" @click="testConnection(row)">
            {{ $t('system.extInterface.btnTestConn') }}</el-button>
          <el-button v-if="canUpdate && row.isDefault !== 1" link type="warning" size="small" @click="setDefault(row)">{{ $t('system.extInterface.btnSetDefault') }}</el-button>
          <el-popconfirm v-if="canDelete" :title="$t('system.extInterface.askDeleteIf')" @confirm="deleteRow(row)">
            <template #reference>
              <el-button link type="danger" size="small">{{ $t('system.common.btnDelete') }}</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <TablePager :pager="pager" @change="loadData" />

    <!-- 编辑/新增对话框 -->
    <el-dialog v-model="dialogVisible" :title="editing.id ? t('system.extInterface.dlgEditTitle') : t('system.extInterface.dlgCreateTitle')" width="680px" @close="resetForm">
      <el-form ref="formRef" :model="editing" :rules="extInterfaceRules" label-width="120px" style="padding-right: 20px">
        <el-form-item :label="$t('system.extInterface.colIfCategory')">
          <el-tag>{{ categoryLabel(activeTab) }}</el-tag>
        </el-form-item>
        <el-form-item :label="$t('system.extInterface.colIfName')" prop="interfaceName">
          <el-input v-model="editing.interfaceName" :placeholder="$t('system.extInterface.phNameExample')" />
        </el-form-item>
        <el-form-item :label="$t('system.common.colIfCode')" prop="interfaceCode">
          <el-input v-model="editing.interfaceCode" :placeholder="$t('system.extInterface.phCodeExample')"
                    :disabled="!!editing.id" />
          <div class="tip">{{ $t('system.extInterface.hintCodeUnique') }}</div>
        </el-form-item>
        <el-form-item :label="$t('system.extInterface.colProvider')">
          <el-select v-model="editing.provider" filterable :placeholder="$t('system.extInterface.phProvider')" clearable style="width: 100%">
            <el-option v-for="p in providerOptions" :key="p" :label="erpProviderLabel(p)" :value="p" />
          </el-select>
          <div v-if="activeTab === 'ERP'" class="tip">
            {{ $t('system.extInterface.hintErpBrandPrefix') }}
            {{ $t('system.extInterface.hintDefaultRoute') }}
          </div>
        </el-form-item>
        <el-form-item :label="$t('system.common.colApiUrl')">
          <el-input v-model="editing.apiUrl" placeholder="https://api.example.com/endpoint" clearable />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="editing.apiKey" type="password" show-password
                    :placeholder="editing.id && editing.apiKey ? t('system.extInterface.hintKeySaved') : 'API Key / AppKey'" clearable />
        </el-form-item>
        <el-form-item label="API Secret">
          <el-input v-model="editing.apiSecret" type="password" show-password
                    :placeholder="editing.id && editing.apiSecret ? t('system.extInterface.hintKeySaved') : 'API Secret / SecretKey'" />
          <div class="tip">{{ $t('system.extInterface.hintSecretMasked') }}</div>
        </el-form-item>
        <el-form-item v-if="activeTab === 'AI_MODEL'" :label="$t('system.common.colModelName')">
          <el-input v-model="editing.modelName" :placeholder="$t('system.extInterface.phModelExample')" clearable />
        </el-form-item>
        <el-form-item :label="$t('system.extInterface.lblExtraJson')">
          <el-input v-model="editing.extraConfig" type="textarea" :rows="4"
                    :placeholder="$t('system.extInterface.phExtraConfigJson')" />
          <div class="tip">
            <el-button link type="primary" size="small" @click="validateJson">{{ $t('system.common.btnCheckJson') }}</el-button>
          </div>
        </el-form-item>
        <el-form-item :label="$t('common.remark')">
          <el-select v-model="editing.remark" filterable allow-create default-first-option :placeholder="$t('system.extInterface.phRemark')" style="width: 100%"><el-option :label="$t('system.extInterface.optProd')" value="生产环境" /><el-option :label="$t('system.extInterface.optTest')" value="测试环境" /><el-option :label="$t('system.extInterface.optBackup')" value="备用接口" /><el-option :label="$t('system.extInterface.optTemp')" value="临时配置" /><el-option :label="$t('system.common.optOther')" value="其他" /></el-select>
        </el-form-item>
        <el-form-item :label="$t('system.common.colEnabled')">
          <el-switch v-model="editing.status" active-value="ENABLED" inactive-value="DISABLED"
                     :active-text="$t('system.common.btnEnable')" :inactive-text="$t('system.common.btnDeactivate')" />
        </el-form-item>
        <el-form-item :label="$t('system.extInterface.btnMarkDefault')">
          <el-switch v-model="editing.isDefault" :active-value="1" :inactive-value="0"
                     :active-text="$t('system.extInterface.colDefault')" :inactive-text="$t('common.no')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { showRowDetail } from '@/utils/format'
import { CircleCheck, Stamp, Picture, SetUp, MagicStick, InfoFilled, Connection, Wallet, OfficeBuilding } from '@element-plus/icons-vue'
import { extInterfaceApi, http } from '../../api'
import { hasPerm } from '@/utils/perm'
import { extCategoryLabel, erpProviderLabel } from '@/constants/enums.js'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { commonRules } from '../../composables/useFormRules'
import { validateForms } from '../../composables/useSubmitGuard'
import OcrConfig from './OcrConfig.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: t('field.detail') })
}

const activeTab = ref('INVOICE_VERIFY')
const keyword = ref('')
const tableData = ref([])
// 本地分页（前端切片已拉取的全量接口配置，避免一次性渲染全部行）
const pager = useTablePager({ mode: 'server', storageKey: 'ext_interface_config' })
const { pagedData } = pager
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const testingId = ref(null)

// 入口显隐办结（hasPerm 已处理通配码，管理员不会被误隐藏）
const canList = hasPerm('system:extiface:list')
const canCreate = hasPerm('system:extiface:create')
const canUpdate = hasPerm('system:extiface:update')
const canDelete = hasPerm('system:extiface:delete')
const canTest = hasPerm('system:extiface:test')

// 出参凭据掩码规则：定长星号 + 后 4 位，与后端 ExtInterfaceConfig.maskCredential 同型
const CRED_MASK = '*'.repeat(8)
const CRED_TAIL = 4
const CRED_MIN_LEN = 8
const isMaskForm = (v) => {
  const s = String(v ?? '').trim()
  let stars = 0
  while (stars < s.length && s[stars] === '*') stars++
  return stars >= CRED_MASK.length && s.length - stars <= CRED_TAIL
}
const maskCredential = (v) => {
  const s = String(v ?? '').trim()
  if (!s) return ''
  if (isMaskForm(s)) return s
  return s.length < CRED_MIN_LEN ? CRED_MASK : CRED_MASK + s.slice(-CRED_TAIL)
}
// 附加配置里凭据类键的值一并脱敏（仅动字符串值，非凭据键保持可读）
const SECRET_KEY_TOKENS = ['key', 'secret', 'token', 'password', 'pwd', 'passwd', 'credential', 'credentials', 'signature']
const SECRET_KEY_NAMES = ['apikey', 'apisecret', 'secretkey', 'accesskey', 'accesssecret', 'appkey', 'appsecret',
  'clientsecret', 'accesstoken', 'refreshtoken', 'authtoken', 'secret', 'token', 'password', 'pwd',
  'passwd', 'credential', 'credentials', 'signature', 'signkey']
const isSecretName = (name) => {
  const n = String(name).toLowerCase()
  if (SECRET_KEY_NAMES.includes(n)) return true
  return n.split(/[^a-z0-9]+/).some((t) => SECRET_KEY_TOKENS.includes(t))
}
const maskExtraConfig = (json) => {
  const s = String(json ?? '')
  if (!s.trim()) return s
  return s.replace(/"((?:[^"\\]|\\.)+)"(\s*:\s*)"((?:[^"\\]|\\.)*)"/g,
    (m, name, sep, value) => (isSecretName(name) && !isMaskForm(value) ? `"${name}"${sep}"${maskCredential(value)}"` : m))
}
const desensitize = (row) => {
  if (!row) return row
  row.apiKey = maskCredential(row.apiKey)
  row.apiSecret = maskCredential(row.apiSecret)
  row.extraConfig = maskExtraConfig(row.extraConfig)
  return row
}

const editing = reactive({
  id: null,
  category: '',
  interfaceName: '',
  interfaceCode: '',
  provider: '',
  apiUrl: '',
  apiKey: '',
  apiSecret: '',
  modelName: '',
  extraConfig: '',
  status: 'DISABLED',
  isDefault: 0,
  remark: '',
})

const extInterfaceRules = computed(() => (reactive({
  // 接口名称/编码＝人工维护（编码租户内唯一、保存后不可改）；与后端 ExtInterfaceConfigService 同一规则
  interfaceName: commonRules.required(t('system.extInterface.phIfName')),
  interfaceCode: commonRules.required(t('system.extInterface.phIfCode')),
})))
const formRef = ref(null)

const providerOptions = computed(() => {
  // ERP 总账对接：provider 即 ERP 品牌，决定业务通道前缀（{PROVIDER}_AP 等，见后端 ErpChannelResolver）
  if (activeTab.value === 'ERP') return ['EBS', 'SAP', 'YONYOU', 'KINGDEE', 'CUSTOM']
  return ['BAIDU', 'TENCENT', 'ALIYUN', 'OPENAI', 'QIANFAN', 'LINKU', 'PIAOYITONG', 'CUSTOM']
})

const enabledCount = computed(() => tableData.value.filter(r => r.status === 'ENABLED').length)

// 分类中文名办结到全局字典 constants/enums.js（原来这里是 7 项局部映射，日志页/筛选器各写一份，
// SMS、TAX_* 与 {PROVIDER}_{ACTION} 通道系列都没覆盖，会直接显示英文码）
const categoryLabel = (cat) => extCategoryLabel(cat)

const providerTagType = (p) => ({
  BAIDU: 'primary', TENCENT: 'success', ALIYUN: 'warning',
  OPENAI: 'info', QIANFAN: 'primary', LINKU: 'success', PIAOYITONG: 'success',
  EBS: 'warning', SAP: 'primary', YONYOU: 'success', KINGDEE: 'warning', CUSTOM: '',
})[p] || ''

// 服务商枚举中文化（CUSTOM 直接显示 "CUSTOM" 对用户无意义）
const providerText = computed(() => (p) => (p === 'CUSTOM' ? t('system.extInterface.catCustom') : erpProviderLabel(p) || '-'))

// 页签由 category 字典驱动（tabDefs 与会话内既有点位/图标一致），计数来自后端汇总
const tabDefs = ref([
  { name: 'INVOICE_VERIFY', label: t('system.extInterface.catInvoiceVerify'), icon: CircleCheck },
  { name: 'INVOICE_CERTIFY', label: t('system.extInterface.catInvoiceAuth'), icon: Stamp },
  { name: 'OCR', label: t('system.extInterface.catOcrImage'), icon: Picture },
  { name: 'OCR_CHANNEL', label: t('system.extInterface.catOcrChannel'), icon: SetUp },
  { name: 'AI_MODEL', label: t('system.extInterface.catAiModel'), icon: MagicStick },
  { name: 'LINKU', label: t('system.extInterface.catLinkUSupp'), icon: Connection },
  { name: 'BANK', label: t('system.extInterface.catBankDirect'), icon: Wallet },
  { name: 'ERP', label: t('system.extInterface.catErpGl'), icon: OfficeBuilding },
])
const visibleTabs = computed(() => (canList ? tabDefs.value : []))
const catCounts = reactive({})
const catCount = (name) => {
  const n = catCounts[name]
  return n ? ` (${n})` : ''
}
async function loadCategories() {
  if (!canList) return
  try {
    const list = await http.get('/tenant/system/ext-interface/categories')
    ;(list || []).forEach((r) => { catCounts[r.category] = Number(r.cnt || 0) })
  } catch { /* 计数失败不影响页签可用性 */ }
}

async function loadData() {
  if (activeTab.value === 'OCR_CHANNEL') return // OCR 通道设置为独立表单，不走接口列表
  loading.value = true
  try {
    await pager.load(async ({ pageNum, pageSize }) => {
      const d = await extInterfaceApi.page({ pageNum, pageSize, category: activeTab.value, keyword: keyword.value })
      // 兜底脱敏：即使出参未掩码，行数据/详情弹层也不会拿到凭据原文
      tableData.value = (d.list || []).map(desensitize)
      return { list: tableData.value, total: d.total || tableData.value.length }
    })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  editing.category = activeTab.value
  editing.status = 'DISABLED'
  editing.isDefault = 0
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  // 弹窗只回填掩码，绝不回填凭据原文；保存时掩码形态按「未修改」处理
  Object.assign(editing, desensitize({ ...row }))
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(editing, {
    id: null, category: '', interfaceName: '', interfaceCode: '', provider: '',
    apiUrl: '', apiKey: '', apiSecret: '', modelName: '', extraConfig: '',
    status: 'DISABLED', isDefault: 0, remark: '',
  })
  formRef.value?.clearValidate()
}

function validateJson() {
  if (!editing.extraConfig || !editing.extraConfig.trim()) {
    ElMessage.success(t('system.extInterface.hintExtraEmpty'))
    return
  }
  try {
    JSON.parse(editing.extraConfig)
    ElMessage.success(t('system.common.msgJsonValid'))
  } catch (e) {
    ElMessage.error(t('system.extInterface.errJsonBad') + e.message)
  }
}

async function save() {
  if (!(await validateForms([formRef]))) return
  if (editing.extraConfig && editing.extraConfig.trim()) {
    try { JSON.parse(editing.extraConfig) } catch (e) { ElMessage.error(t('system.extInterface.errExtraJson')); return }
  }
  saving.value = true
  try {
    const body = { ...editing }
    // 留空或掩码形态＝未修改，星号串不得回写覆盖已保存的凭据
    if (!body.apiKey || !body.apiKey.trim() || isMaskForm(body.apiKey)) delete body.apiKey
    if (!body.apiSecret || !body.apiSecret.trim() || isMaskForm(body.apiSecret)) delete body.apiSecret
    if (body.id) {
      await extInterfaceApi.update(body)
    } else {
      delete body.id
      await extInterfaceApi.create(body)
    }
    ElMessage.success(t('system.common.msgSaved'))
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  try {
    await extInterfaceApi.toggleStatus(row.id, row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED')
    ElMessage.success(row.status === 'ENABLED' ? t('system.extInterface.stDeactivated') : t('system.common.stEnabled'))
    await loadData()
  } catch (e) { ElMessage.error(e.message) }
}

async function setDefault(row) {
  try {
    await extInterfaceApi.setDefault(row.id)
    ElMessage.success(t('system.extInterface.msgSetDefault'))
    await loadData()
  } catch (e) { ElMessage.error(e.message) }
}

async function testConnection(row) {
  testingId.value = row.id
  try {
    const r = await extInterfaceApi.test(row.id)
    if (r.success) {
      ElMessage.success(t('system.extInterface.msgTestPass') + r.message)
    } else {
      ElMessage.warning(t('system.extInterface.msgTestFail') + r.message)
    }
    await loadData()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    testingId.value = null
  }
}

async function deleteRow(row) {
  try {
    await extInterfaceApi.delete(row.id)
    ElMessage.success(t('system.common.stDeleted'))
    await loadData()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(() => { loadData(); loadCategories() })
</script>

<style scoped>
.ext-interface-config { padding: 0; }
.tip { color: #909399; font-size: 12px; line-height: 1.6; }
</style>
