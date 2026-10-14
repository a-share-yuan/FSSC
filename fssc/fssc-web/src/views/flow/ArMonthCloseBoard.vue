<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>应收月结管理（应收月结 / 成本月结 · 清接口行→扛数→差异调整→过总账→核对→关账/反月结）</span>
        <div class="ops">
          <el-select v-model="q.closeType" size="small" style="width: 120px" placeholder="月结类型" clearable @change="page.pageNum = 1">
            <el-option label="应收月结" value="RECEIVABLE" />
            <el-option label="成本月结" value="COST" />
          </el-select>
          <el-input v-model="q.period" size="small" style="width: 130px" placeholder="期间 yyyy-MM" clearable @keyup.enter="page.pageNum = 1" />
          <el-button size="small" @click="load">刷新</el-button>
          <el-button type="primary" size="small" @click="openCreate">发起月结</el-button>
        </div>
      </div>
    </template>

    <el-table :data="pagedRows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="closeNo" label="月结单号" min-width="180" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.closeType === 'COST' ? 'warning' : 'primary'">{{ row.closeType === 'COST' ? '成本月结' : '应收月结' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="period" label="账期" width="90" />
      <el-table-column prop="pulledAmount" label="扛数金额" width="115" align="right" :formatter="(row, col, val) => formatMoney(val)" />
      <el-table-column prop="pulledCount" label="单据数" width="80" align="right" />
      <el-table-column prop="glAmount" label="过总账金额" width="115" align="right" :formatter="(row, col, val) => formatMoney(val)" />
      <el-table-column prop="diffAmount" label="核对差异" width="100" align="right" :formatter="(row, col, val) => formatMoney(val)" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="{ CLOSED: 'success', RECONCILED: 'success', GL_POSTED: row.diffFlag === 1 ? 'danger' : 'warning', INIT: 'info', VOID: 'info' }[row.status] || 'primary'">
            {{ arMcStatusLabel(row.status) }}<template v-if="row.diffFlag === 1">（差异未消除）</template>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="voucherNo" label="总账凭证" min-width="130" show-overflow-tooltip />
      <el-table-column prop="adjustNote" label="调整说明" min-width="130" show-overflow-tooltip />
      <el-table-column label="操作" width="400" fixed="right">
        <template #default="{ row }">
          <el-button v-for="a in actions(row)" :key="a.key" link type="primary" size="small" @click="act(row, a)">{{ a.label }}</el-button>
          <el-button v-if="row.status === 'CLOSED'" link type="danger" size="small" @click="openReopen(row)">反月结</el-button>
          <!-- R5-D-218：未关闭账期的月结单可作废（终态 VOID，留痕不删行） -->
          <el-button v-if="!['CLOSED', 'VOID'].includes(row.status)" link type="warning" size="small" @click="openVoid(row)">作废</el-button>
          <el-button link type="info" size="small" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TablePager v-model:page="page.pageNum" v-model:size="page.pageSize" :total="rows.length" />

    <el-dialog v-model="createVisible" title="发起月结" width="460px" destroy-on-close>
      <el-form ref="createFormRef" :model="form" :rules="createRules" label-width="100px" size="small">
        <el-form-item label="月结组织" prop="companyId">
          <el-select v-model="form.companyId" filterable placeholder="请选择组织（公司）" style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <!-- 月结类型单选组恒有默认值（开单时写入 RECEIVABLE），不存在漏填，故不加红星 -->
        <el-form-item label="月结类型">
          <el-radio-group v-model="form.closeType">
            <el-radio value="RECEIVABLE">应收月结</el-radio>
            <el-radio value="COST">成本月结</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="账期" prop="period">
          <el-date-picker v-model="form.period" type="month" value-format="YYYY-MM" style="width: 100%" placeholder="yyyy-MM" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doCreate">发起</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjustVisible" title="差异调整" width="440px" destroy-on-close>
      <el-input v-model="adjustNote" type="textarea" :rows="3" placeholder="调整说明（必填）" />
      <template #footer>
        <el-button size="small" @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doAdjust">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 反月结：原因必填留痕（41号审查报告 P0-2 同源整改） -->
    <el-dialog v-model="reopenVisible" title="反月结（重开账期）" width="440px" destroy-on-close>
      <el-alert type="warning" :closable="false" style="margin-bottom: 10px"
        :title="`月结单 ${reopenRow?.closeNo}（期间 ${reopenRow?.period}）`"
        description="反月结将：账期解锁（当月恢复可录入）、月结单回到「核对通过」（会计期间状态不由本台改动）。原因将写入留痕。" />
      <el-input v-model="reopenReason" type="textarea" :rows="3" placeholder="反月结原因（必填，审计留痕）" />
      <template #footer>
        <el-button size="small" @click="reopenVisible = false">取消</el-button>
        <el-button type="danger" size="small" @click="doReopen">确认反月结</el-button>
      </template>
    </el-dialog>

    <!-- R5-D-218 作废：原因必填留痕（终态 VOID，行止不删） -->
    <el-dialog v-model="voidVisible" title="作废月结单" width="440px" destroy-on-close>
      <el-alert type="error" :closable="false" style="margin-bottom: 10px"
        :title="`月结单 ${voidRow?.closeNo}（期间 ${voidRow?.period}）`"
        description="作废为终态：单据保留不删行、原因并入 remark 留痕；已关闭账期的月结单须先反月结，不能直接作废。" />
      <el-input v-model="voidReason" type="textarea" :rows="3" placeholder="作废原因（必填，审计留痕）" />
      <template #footer>
        <el-button size="small" @click="voidVisible = false">取消</el-button>
        <el-button type="warning" size="small" @click="doVoid">确认作废</el-button>
      </template>
    </el-dialog>

    <!-- 月结单详情 -->
    <el-drawer v-model="detailVisible" title="月结单详情" size="480px" destroy-on-close>
      <el-descriptions v-if="detailRow" :column="1" border size="small">
        <el-descriptions-item label="月结单号">{{ detailRow.closeNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detailRow.closeType === 'COST' ? '成本月结' : '应收月结' }}</el-descriptions-item>
        <el-descriptions-item label="账期">{{ detailRow.period }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ arMcStatusLabel(detailRow.status) }}<template v-if="detailRow.diffFlag === 1">（差异未消除）</template></el-descriptions-item>
        <el-descriptions-item label="扛数金额/单据数">{{ formatMoney(detailRow.pulledAmount) }} / {{ detailRow.pulledCount ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="过总账金额">{{ formatMoney(detailRow.glAmount) }}</el-descriptions-item>
        <el-descriptions-item label="核对差异">{{ formatMoney(detailRow.diffAmount) }}</el-descriptions-item>
        <el-descriptions-item label="总账凭证">{{ detailRow.voucherNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="调整说明">{{ detailRow.adjustNote || '—' }}</el-descriptions-item>
        <el-descriptions-item label="关账时间">{{ detailRow.closedTime ? formatDateTime(detailRow.closedTime) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ personLabel(detailRow.operator) }}</el-descriptions-item>
        <el-descriptions-item label="备注/留痕"><span class="pre-wrap">{{ detailRow.remark || '—' }}</span></el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { arMcStatusLabel } from '@/constants/enums'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { arGapApi, orgApi, userApi } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { formatDateTime, formatMoney } from '../../utils/format'
import TablePager from '../../components/common/TablePager.vue'


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
const createRules = {
  companyId: commonRules.select('请选择月结组织'),
  period: commonRules.date('请选择账期'),
}
const adjustNote = ref('')
const reopenReason = ref('')
const voidVisible = ref(false)
const voidRow = ref(null)
const voidReason = ref('')
const users = ref([])

// R5-D-212：库内存工号→展示花名；历史遗留的昵称按原值回显（找不到不改写）
function personLabel(name) {
  if (!name) return '—'
  const u = users.value.find((x) => x.userName === name)
  return (u && (u.nickName || u.userName)) || name
}
async function loadUsers() {
  try { users.value = (await userApi.page({ pageNum: 1, pageSize: 200 })).list || [] } catch { users.value = [] }
}

const pagedRows = computed(() => rows.value.slice((page.pageNum - 1) * page.pageSize, page.pageNum * page.pageSize))

function actions(row) {
  switch (row.status) {
    case 'INIT':
      return row.closeType === 'COST'
        ? [{ key: 'pullData', label: '扛数据' }]
        : [{ key: 'clearInterface', label: '清接口行' }, { key: 'pullData', label: '扛数据' }]
    case 'INTERFACE_CLEARED':
      return [{ key: 'pullData', label: '扛数据' }]
    case 'DATA_PULLED':
      return [{ key: 'adjust', label: '差异调整' }]
    case 'ADJUSTED':
      return [{ key: 'postGl', label: '过总账' }]
    case 'GL_POSTED':
      return row.diffFlag === 1
        ? [{ key: 'adjust', label: '差异调整' }, { key: 'reconcile', label: '重新核对' }]
        : [{ key: 'reconcile', label: '数据核对' }]
    case 'RECONCILED':
      return [{ key: 'closePeriod', label: '关闭账期' }]
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
  await fn()
  ElMessage.success(msg)
  await load()
}

function act(row, a) {
  current.value = row
  const id = row.id
  const m = {
    clearInterface: () => api(() => arGapApi.monthCloseClearInterface(id), '接口行已清理'),
    pullData: () => api(() => arGapApi.monthClosePullData(id), '扛数完成'),
    postGl: () => api(() => arGapApi.monthClosePostGl(id), '过总账完成'),
    reconcile: () => api(() => arGapApi.monthCloseReconcile(id), '数据核对通过'),
    closePeriod: () => api(() => arGapApi.monthCloseClose(id), '账期已关闭，当月不允许再录入业务'),
  }
  if (m[a.key]) return m[a.key]()
  if (a.key === 'adjust') {
    adjustNote.value = row.adjustNote || ''
    adjustVisible.value = true
  }
}

async function doAdjust() {
  if (!adjustNote.value) return ElMessage.warning('调整说明必填')
  await api(() => arGapApi.monthCloseAdjust(current.value.id, adjustNote.value), '差异调整已记录')
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
  await api(() => arGapApi.monthCloseCreate({ ...form }), '月结已发起')
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

// ---- 反月结 ----
function openReopen(row) {
  reopenRow.value = row
  reopenReason.value = ''
  reopenVisible.value = true
}

async function doReopen() {
  if (!reopenReason.value.trim()) return ElMessage.warning('反月结原因必填（审计留痕）')
  await api(() => arGapApi.monthCloseReopen(reopenRow.value.id, reopenReason.value.trim()), '反月结完成：账期已解锁（会计期间状态请到「总账核算›月结」反结账）')
  reopenVisible.value = false
}

// ---- 作废（R5-D-218 终态） ----
function openVoid(row) {
  voidRow.value = row
  voidReason.value = ''
  voidVisible.value = true
}

async function doVoid() {
  if (!voidReason.value.trim()) return ElMessage.warning('作废原因必填（审计留痕）')
  await api(() => arGapApi.monthCloseVoid(voidRow.value.id, voidReason.value.trim()), '月结单已作废（留痕不删行）')
  voidVisible.value = false
}

// ---- 详情 ----
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
