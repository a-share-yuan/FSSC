<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>{{ $t('flow.arClose.pageTitle') }}</span>
        <div class="ops">
          <el-select v-model="q.closeType" size="small" style="width: 120px" :placeholder="$t('flow.arClose.colCloseType')" clearable @change="page.pageNum = 1">
            <el-option :label="$t('flow.entryAr.tabMonthClose')" value="RECEIVABLE" />
            <el-option :label="$t('flow.arClose.typeCostClose')" value="COST" />
          </el-select>
          <el-input v-model="q.period" size="small" style="width: 130px" :placeholder="$t('flow.close.colPeriodYm')" clearable @keyup.enter="page.pageNum = 1" />
          <el-button size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          <el-button type="primary" size="small" @click="openCreate">{{ $t('flow.arClose.btnStartClose') }}</el-button>
        </div>
      </div>
    </template>

    <el-table :data="pagedRows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="closeNo" :label="$t('flow.report.colCloseDocNo')" min-width="180" show-overflow-tooltip />
      <el-table-column :label="$t('field.type')" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.closeType === 'COST' ? 'warning' : 'primary'">{{ row.closeType === 'COST' ? t('flow.arClose.typeCostClose') : t('flow.entryAr.tabMonthClose') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="period" :label="$t('flow.report.colPeriod')" width="90" />
      <el-table-column prop="pulledAmount" :label="$t('flow.report.colCarriedAmount')" width="115" align="right" :formatter="(row, col, val) => formatMoney(val)" />
      <el-table-column prop="pulledCount" :label="$t('flow.arClose.colDocCount')" width="80" align="right" />
      <el-table-column prop="glAmount" :label="$t('flow.arClose.colGlAmount')" width="115" align="right" :formatter="(row, col, val) => formatMoney(val)" />
      <el-table-column prop="diffAmount" :label="$t('flow.arClose.colVariance')" width="100" align="right" :formatter="(row, col, val) => formatMoney(val)" />
      <el-table-column :label="$t('common.status')" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="{ CLOSED: 'success', RECONCILED: 'success', GL_POSTED: row.diffFlag === 1 ? 'danger' : 'warning', INIT: 'info', VOID: 'info' }[row.status] || 'primary'">
            {{ arMcStatusLabel(row.status) }}<template v-if="row.diffFlag === 1">{{ $t('flow.arClose.diffUnresolved') }}</template>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="voucherNo" :label="$t('flow.arClose.colGlVoucher')" min-width="130" show-overflow-tooltip />
      <el-table-column prop="adjustNote" :label="$t('flow.arClose.colAdjustNote')" min-width="130" show-overflow-tooltip />
      <el-table-column :label="$t('common.operation')" width="400" fixed="right">
        <template #default="{ row }">
          <el-button v-for="a in actions(row)" :key="a.key" link type="primary" size="small" @click="act(row, a)">{{ a.label }}</el-button>
          <el-button v-if="row.status === 'CLOSED'" link type="danger" size="small" @click="openReopen(row)">{{ $t('flow.arClose.btnReopen') }}</el-button>
          <!-- 未关闭账期的月结单可作废（最终状态 VOID，记录不删行） -->
          <el-button v-if="!['CLOSED', 'VOID'].includes(row.status)" link type="warning" size="small" @click="openVoid(row)">{{ $t('field.void') }}</el-button>
          <el-button link type="info" size="small" @click="openDetail(row)">{{ $t('field.detail') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page.pageNum" v-model:size="page.pageSize" :total="rows.length" />

    <el-dialog v-model="createVisible" :title="$t('flow.arClose.btnStartClose')" width="460px" destroy-on-close>
      <el-form ref="createFormRef" :model="form" :rules="createRules" label-width="100px" size="small">
        <el-form-item :label="$t('flow.arClose.lblCloseOrg')" prop="companyId">
          <el-select v-model="form.companyId" filterable :placeholder="$t('flow.close.phPickOrg')" style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <!-- 月结类型单选组恒有默认值（开单时写入 RECEIVABLE），不存在漏填，故不加红星 -->
        <el-form-item :label="$t('flow.arClose.colCloseType')">
          <el-radio-group v-model="form.closeType">
            <el-radio value="RECEIVABLE">{{ $t('flow.entryAr.tabMonthClose') }}</el-radio>
            <el-radio value="COST">{{ $t('flow.arClose.typeCostClose') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('flow.report.colPeriod')" prop="period">
          <el-date-picker v-model="form.period" type="month" value-format="YYYY-MM" style="width: 100%" placeholder="yyyy-MM" />
        </el-form-item>
        <el-form-item :label="$t('common.remark')"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doCreate">{{ $t('flow.close.btnStart') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjustVisible" :title="$t('flow.arClose.tabAdjust')" width="440px" destroy-on-close>
      <el-input v-model="adjustNote" type="textarea" :rows="3" :placeholder="$t('flow.arClose.phAdjustNote')" />
      <template #footer>
        <el-button size="small" @click="adjustVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="doAdjust">{{ $t('flow.arClose.btnConfirmAdjust') }}</el-button>
      </template>
    </el-dialog>

    <!-- 反月结：原因必填记录（41号审查报告 同源整改） -->
    <el-dialog v-model="reopenVisible" :title="$t('flow.arClose.dlgReopenTitle')" width="440px" destroy-on-close>
      <el-alert type="warning" :closable="false" style="margin-bottom: 10px"
        :title="$t('flow.arClose.dlgReopenDoc', { v1: reopenRow?.closeNo, v2: reopenRow?.period })"
        :description="$t('flow.arClose.dlgReopenDesc')" />
      <el-input v-model="reopenReason" type="textarea" :rows="3" :placeholder="$t('flow.arClose.phReopenReason')" />
      <template #footer>
        <el-button size="small" @click="reopenVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" size="small" @click="doReopen">{{ $t('flow.arClose.btnConfirmReopen') }}</el-button>
      </template>
    </el-dialog>

    <!-- 作废：原因必填记录（最终状态 VOID，行止不删） -->
    <el-dialog v-model="voidVisible" :title="$t('flow.arClose.dlgVoidTitle')" width="440px" destroy-on-close>
      <el-alert type="error" :closable="false" style="margin-bottom: 10px"
        :title="$t('flow.arClose.dlgReopenDoc', { v1: voidRow?.closeNo, v2: voidRow?.period })"
        :description="$t('flow.arClose.dlgVoidDesc')" />
      <el-input v-model="voidReason" type="textarea" :rows="3" :placeholder="$t('flow.close.phVoidReason')" />
      <template #footer>
        <el-button size="small" @click="voidVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="warning" size="small" @click="doVoid">{{ $t('flow.close.btnConfirmVoid') }}</el-button>
      </template>
    </el-dialog>

    <!-- 月结单详情 -->
    <el-drawer v-model="detailVisible" :title="$t('flow.arClose.drawerDetailTitle')" size="480px" destroy-on-close>
      <el-descriptions v-if="detailRow" :column="1" border size="small">
        <el-descriptions-item :label="$t('flow.report.colCloseDocNo')">{{ detailRow.closeNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('field.type')">{{ detailRow.closeType === 'COST' ? t('flow.arClose.typeCostClose') : t('flow.entryAr.tabMonthClose') }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.report.colPeriod')">{{ detailRow.period }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.status')">{{ arMcStatusLabel(detailRow.status) }}<template v-if="detailRow.diffFlag === 1">{{ $t('flow.arClose.diffUnresolved') }}</template></el-descriptions-item>
        <el-descriptions-item :label="$t('flow.arClose.colPulledAmountDocs')">{{ formatMoney(detailRow.pulledAmount) }} / {{ detailRow.pulledCount ?? '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.arClose.colGlAmount')">{{ formatMoney(detailRow.glAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.arClose.colVariance')">{{ formatMoney(detailRow.diffAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.arClose.colGlVoucher')">{{ detailRow.voucherNo || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.arClose.colAdjustNote')">{{ detailRow.adjustNote || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.report.colCloseTime')">{{ detailRow.closedTime ? formatDateTime(detailRow.closedTime) : '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.arClose.colOperator')">{{ personLabel(detailRow.operator) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('flow.arClose.colRemarkTrail')"><span class="pre-wrap">{{ detailRow.remark || '—' }}</span></el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { arMcStatusLabel } from '@/constants/enums'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { arGapApi, orgApi, userApi, store } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { formatDateTime, formatMoney } from '../../utils/format'
import TablePager from '../../components/common/TablePager.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()


const rows = ref([])
const loading = ref(false)
const companies = ref([])
const q = reactive({ closeType: '', period: '' })
const page = reactive({ pageNum: 1, pageSize: 10 })
const createVisible = ref(false)
const adjustVisible = ref(false)
const reopenVisible = ref(false)
const detailVisible = ref(false)
const current = ref(null)
const reopenRow = ref(null)
const detailRow = ref(null)
const form = reactive({ closeType: 'RECEIVABLE' })
const createFormRef = ref(null)
const createRules = computed(() => ({
  companyId: commonRules.select(t('flow.arClose.msgPickCloseOrg')),
  period: commonRules.date(t('flow.arClose.msgPickPeriod')),
}))
const adjustNote = ref('')
const reopenReason = ref('')
const voidVisible = ref(false)
const voidRow = ref(null)
const voidReason = ref('')
const users = ref([])

// 库内存工号→展示花名；历史遗留的昵称按原值回显（找不到不改写）
function personLabel(name) {
  if (!name) return '—'
  const u = users.value.find((x) => x.userName === name)
  return (u && (u.nickName || u.userName)) || name
}
async function loadUsers() {
  // 月结负责人通常为本财务人员，按同公司过滤
  try { users.value = (await userApi.page({ pageNum: 1, pageSize: 200, companyId: store.user?.companyId })).list || [] } catch { users.value = [] }
}

const pagedRows = computed(() => rows.value.slice((page.pageNum - 1) * page.pageSize, page.pageNum * page.pageSize))

function actions(row) {
  switch (row.status) {
    case 'INIT':
      return row.closeType === 'COST'
        ? [{ key: 'pullData', label: t('flow.arClose.actPullData') }]
        : [{ key: 'clearInterface', label: t('flow.arClose.actClearInterface') }, { key: 'pullData', label: t('flow.arClose.actPullData') }]
    case 'INTERFACE_CLEARED':
      return [{ key: 'pullData', label: t('flow.arClose.actPullData') }]
    case 'DATA_PULLED':
      return [{ key: 'adjust', label: t('flow.arClose.tabAdjust') }]
    case 'ADJUSTED':
      return [{ key: 'postGl', label: t('flow.arClose.actPostGl') }]
    case 'GL_POSTED':
      return row.diffFlag === 1
        ? [{ key: 'adjust', label: t('flow.arClose.tabAdjust') }, { key: 'reconcile', label: t('flow.arClose.actReconcile') }]
        : [{ key: 'reconcile', label: t('flow.bill.checkData') }]
    case 'RECONCILED':
      return [{ key: 'closePeriod', label: t('flow.arClose.actClosePeriod') }]
    default:
      return []
  }
}

async function load() {
  loading.value = true
  try {
    rows.value = await arGapApi.monthCloseList({
      closeType: q.closeType || undefined,
      period: q.period || undefined,
    })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function api(fn, msg) {
  // 五个步骤动作（清接口行／扛数据／过总账／核对／关账期）与差异调整都走这里，原先不接异常：
  // 后端按业务规则拦停时（例如「参数校验失败：扛数金额为0，无可过总账数据」）拦截器 reject 出的
  // Error 一路冒到 Vue 全局错误边界 ⇒ 整页换成「页面出错了」，红 toast 只闪一下、月结单也看不见了。
  // 与本文件 load()（:213 catch (e) { ElMessage.error(e.message) }）同一写法补齐。
  try {
    await fn()
  } catch (e) {
    ElMessage.error(e.message)
    return
  }
  ElMessage.success(msg)
  await load()
}

function act(row, a) {
  current.value = row
  const id = row.id
  const m = {
    clearInterface: () => api(() => arGapApi.monthCloseClearInterface(id), t('flow.arClose.msgInterfaceCleared')),
    pullData: () => api(() => arGapApi.monthClosePullData(id), t('flow.arClose.msgPullDone')),
    postGl: () => api(() => arGapApi.monthClosePostGl(id), t('flow.arClose.msgPostGlDone')),
    reconcile: () => api(() => arGapApi.monthCloseReconcile(id), t('flow.arClose.msgReconcilePassed')),
    closePeriod: () => api(() => arGapApi.monthCloseClose(id), t('flow.arClose.msgPeriodClosed')),
  }
  if (m[a.key]) return m[a.key]()
  if (a.key === 'adjust') {
    adjustNote.value = row.adjustNote || ''
    adjustVisible.value = true
  }
}

async function doAdjust() {
  if (!adjustNote.value) return ElMessage.warning(t('flow.arClose.msgAdjustNoteRequired'))
  await api(() => arGapApi.monthCloseAdjust(current.value.id, adjustNote.value), t('flow.arClose.msgAdjustRecorded'))
  adjustVisible.value = false
}

function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  form.closeType = 'RECEIVABLE'
  form.companyId = null
  createVisible.value = true
}

async function doCreate() {
  if (!(await validateForms([createFormRef]))) return
  await api(() => arGapApi.monthCloseCreate({ ...form }), t('flow.arClose.msgCloseStarted'))
  createVisible.value = false
}

async function loadCompanies() {
  try {
    const flat = []
    const walk = (arr) => { (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
    walk(await orgApi.companyTree())
    companies.value = flat
  } catch { /* 组织树加载失败不阻断列表 */ }
}

// 反月结
function openReopen(row) {
  reopenRow.value = row
  reopenReason.value = ''
  reopenVisible.value = true
}

async function doReopen() {
  if (!reopenReason.value.trim()) return ElMessage.warning(t('flow.arClose.msgReopenReasonRequired'))
  await api(() => arGapApi.monthCloseReopen(reopenRow.value.id, reopenReason.value.trim()), t('flow.arClose.msgReopenDone'))
  reopenVisible.value = false
}

// 作废（最终状态）
function openVoid(row) {
  voidRow.value = row
  voidReason.value = ''
  voidVisible.value = true
}

async function doVoid() {
  if (!voidReason.value.trim()) return ElMessage.warning(t('flow.close.errVoidReason'))
  await api(() => arGapApi.monthCloseVoid(voidRow.value.id, voidReason.value.trim()), t('flow.arClose.msgVoided'))
  voidVisible.value = false
}

// 详情
function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

onMounted(() => { load(); loadCompanies(); loadUsers() })

defineExpose({ rows })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
.pre-wrap { white-space: pre-wrap; }
</style>
