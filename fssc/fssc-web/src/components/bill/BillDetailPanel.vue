<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div v-loading="loading" class="bdp">
    <el-alert
      v-if="loadError"
      type="warning"
      :closable="false"
      show-icon
      :title="loadError"
      style="margin-bottom: 10px"
    />

    <template v-if="bill">
      <!-- 单据头 -->
      <div class="bdp-head">
        <div class="bdp-no">
          <span>{{ bill.billNo || '-' }}</span>
          <el-button size="small" type="primary" plain @click="printVisible = true">{{ $t('InlineActions.main.Print') }}</el-button>
        </div>
        <div class="bdp-meta">
          <el-tag size="small" :type="statusType(bill.status)">{{ statusLabel(bill.status) }}</el-tag>
          <span class="bdp-type">{{ billTypeLabel(bill.billType) }}{{ bill.billSubType ? ' / ' + billSubTypeLabel(bill.billSubType) : '' }}</span>
          <span v-if="!isHidden('totalAmount')" class="bdp-amt">申请 ¥{{ fmt(bill.totalAmount) }}</span>
          <span v-if="bill.approvedAmount != null && !isHidden('approvedAmount')" class="bdp-amt">核定 ¥{{ fmt(bill.approvedAmount) }}</span>
          <el-tag v-if="bill.urgent === 1" size="small" type="danger" effect="plain">加急</el-tag>
        </div>
        <div class="bdp-title">{{ bill.title || '（无标题）' }}</div>
      </div>

      <el-tabs v-model="tab">
        <!-- 单据信息 -->
        <el-tab-pane :label="$t('bill.billDetailPanel.Document')" name="info">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item :label="$t('bill.billDetailPanel.Document2')">{{ bill.billNo || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('bill.billDetailPanel.Document3')">{{ statusLabel(bill.status) }}</el-descriptions-item>
            <el-descriptions-item v-if="bill.rejectType" :label="$t('bill.billDetailPanel.Rejection')">{{ rejectTypeLabel(bill.rejectType) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('bill.billDetailPanel.Applicant')">{{ bill.applyUserName || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('bill.billDetailPanel.Application')">{{ bill.applyDate || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="!isHidden('totalAmount')" :label="permLabel('totalAmount', '价税合计')">{{ fmt(bill.totalAmount) }}</el-descriptions-item>
            <el-descriptions-item v-if="!isHidden('taxAmount')" :label="permLabel('taxAmount', '其中税额')">{{ fmt(bill.taxAmount) }}</el-descriptions-item>
            <el-descriptions-item v-if="!isHidden('netAmount')" :label="permLabel('netAmount', '不含税金额')">{{ fmt(bill.netAmount) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('bill.billDetailPanel.Currency')">{{ bill.currencyCode || 'CNY' }}</el-descriptions-item>
            <el-descriptions-item v-if="!isHidden('payeeName')" :label="permLabel('payeeName', '收款方')">{{ bill.payeeName || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="!isHidden('payeeAccountNo')" :label="permLabel('payeeAccountNo', '收款账号')">{{ maskIfRo('payeeAccountNo', bill.payeeAccountNo) || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('bill.billDetailPanel.Current')" :span="2">{{ bill.flowName || '-' }} / {{ bill.currentNode || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="!isHidden('remark')" :label="permLabel('remark', '备注')" :span="2">{{ bill.remark || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="bdp-sec">费用明细（{{ lines.length }} 行）</div>
          <el-table v-if="lines.length" :data="lines" size="small" border show-summary :summary-method="sumLine">
            <el-table-column prop="lineNo" label="#" width="45" align="center" />
            <el-table-column prop="itemName" :label="$t('bill.billDetailPanel.Expense')" min-width="120" show-overflow-tooltip :formatter="(row, col, v) => v || (row.itemId != null ? '费用项#' + row.itemId : '—')" />
            <el-table-column prop="expenseDate" :label="$t('bill.billDetailPanel.Transaction')" width="105" />
            <el-table-column v-if="!isHidden('lineAmount')" :label="$t('common.amount')" width="105" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
            <el-table-column v-if="!isHidden('taxRate')" label="税率" width="70" align="right"><template #default="{ row }">{{ row.taxRate != null ? row.taxRate + '%' : '-' }}</template></el-table-column>
            <el-table-column v-if="!isHidden('lineTaxAmount')" label="税额" width="100" align="right"><template #default="{ row }">{{ fmt(row.taxAmount) }}</template></el-table-column>
            <el-table-column v-if="hasEntertainLine" label="接待对象" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ entertainOfLine(row).target || '—' }}</template>
            </el-table-column>
            <el-table-column v-if="hasEntertainLine" label="接待人数" width="86" align="right">
              <template #default="{ row }">{{ entertainOfLine(row).count != null ? entertainOfLine(row).count : '—' }}</template>
            </el-table-column>
            <el-table-column v-if="hasEntertainLine" label="人均标准" width="100" align="right">
              <template #default="{ row }">{{ entertainOfLine(row).perCapita != null ? fmt(entertainOfLine(row).perCapita) : '—' }}</template>
            </el-table-column>
            <el-table-column label="超标" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.overStd === 1" size="small" type="danger" effect="plain">超标</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!isHidden('lineRemark')" prop="remark" label="备注" min-width="110" show-overflow-tooltip />
          </el-table>
          <el-empty v-else description="无费用明细" :image-size="56" />
        </el-tab-pane>

        <!-- 附件与识别 -->
        <el-tab-pane :label="`附件与识别 (${attaches.length})`" name="attach">
          <el-table v-if="attaches.length" :data="attaches" size="small" border>
            <el-table-column prop="id" label="附件ID" width="80" align="center" />
            <el-table-column prop="fileId" label="文件ID" width="80" align="center" />
            <el-table-column label="类型" width="80" align="center">
              <template #default="{ row }">
                <!-- 定性规则统一在 utils/attachKind：attachType 缺失时按扩展名兜底，
                     不再把老数据直接吐成 "OTHER" / "其他" 这种半英文 -->
                <el-tag size="small" :type="attachKindOf(row) === 'INVOICE' ? 'success' : 'info'">
                  {{ ATTACH_KIND_TEXT[attachKindOf(row)] }}
                </el-tag>
              </template>
            </el-table-column>
            <!-- 上传人显姓名（uploadByName 服务端批量反查 sys_user），空显「—」不显裸 id -->
            <el-table-column label="上传人" width="90">
              <template #default="{ row }">{{ row.uploadByName || '—' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="上传时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button link type="primary" size="small" :disabled="!row.fileId" @click="preview(row)">预览</el-button>
                <el-button link type="success" size="small" :disabled="!row.fileId || ocrBusy === row.fileId" :loading="ocrBusy === row.fileId" @click="runOcr(row)">OCR识别</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="无附件" :image-size="56" />

          <div class="bdp-sec">识别结果 · 关联发票（{{ invoices.length }} 张）</div>
          <el-alert
            v-if="!canViewInvoice"
            type="info"
            :closable="false"
            show-icon
            title="当前账号无查看关联发票的权限（需 inv:list）"
          />
          <el-alert
            v-else-if="!invoices.length"
            type="info"
            :closable="false"
            show-icon
            title="该单据未登记发票。若附件为发票影像，可先点「OCR识别」完成识别入池。"
          />
          <el-table v-else :data="invoices" size="small" border>
            <el-table-column prop="invoiceNo" label="发票号码" width="130" show-overflow-tooltip />
            <el-table-column prop="invoiceDate" label="开票日期" width="105" />
            <el-table-column prop="sellerName" label="销售方" min-width="150" show-overflow-tooltip />
            <el-table-column label="价税合计" width="110" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
            <el-table-column label="税额" width="95" align="right"><template #default="{ row }">{{ fmt(row.taxAmount) }}</template></el-table-column>
            <el-table-column label="验真" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="['REJECT', 'FAIL', 'FAILED'].includes(row.verifyStatus) ? 'danger' : row.verifyStatus === 'PASS' || row.verifyStatus === 'SUCCESS' ? 'success' : 'info'">
                  {{ verifyStatusLabel(row.verifyStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="占用" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.lockStatus === 'LOCKED_IN_USE' ? 'warning' : 'info'" effect="plain">
                  {{ row.lockStatus === 'LOCKED_IN_USE' ? '已占用' : '未占用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 审批历史 -->
        <el-tab-pane :label="`审批历史 (${history.length})`" name="history">
          <el-alert v-if="!canViewHistory" type="info" :closable="false" show-icon
                    title="当前账号无查看审批历史的权限，请联系管理员开通。" />
          <!-- 查询失败要说"取不到"，不能空着让人以为"没有" -->
          <el-alert v-else-if="historyError" type="error" :closable="false" show-icon
                    :title="`审批历史加载失败：${historyError}`">
            <div style="font-size:12px; color: var(--app-text-secondary, #606266); line-height:20px">
              此处为空表示加载失败，不代表本单没有审批记录；请重试，仍失败时联系流程管理员核对。
              <div><el-button size="small" type="primary" plain style="margin-top:6px" @click="loadHistory(billId)">重新加载</el-button></div>
            </div>
          </el-alert>
          <el-timeline v-else-if="history.length">
            <el-timeline-item
              v-for="(h, i) in history"
              :key="h.id || i"
              :timestamp="shortTime(h.operTime)"
              placement="top"
              :type="actionType(h.action)"
            >
              <span class="bdp-node">{{ h.nodeName || '—' }}</span>
              <el-tag :type="actionType(h.action)" size="small" style="margin: 0 6px">{{ actionLabel(h.action) }}</el-tag>
              <span class="bdp-oper">{{ h.operUserName || '—' }}</span>
              <span v-if="h.duration" class="bdp-dur">停留 {{ fmtDuration(h.duration) }}</span>
              <div v-if="h.comment" class="bdp-comment">意见：{{ h.comment }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审批记录" :image-size="56" />
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-empty v-else-if="!loading" description="无法加载单据详情" />
  </div>

  <PrintDialog v-model:visible="printVisible" mode="bill" :bill="printBill" :bill-lines="printLines" />
  <FileViewer ref="viewer" />
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { billApi, flowApi, imageApi, invoiceApi } from '../../api'
import FileViewer from '../FileViewer.vue'
import PrintDialog from '../PrintDialog.vue'
import { statusLabel, statusType, rejectTypeLabel } from '@/constants/billStatus.js'
import { wfActionLabel, wfActionType, billTypeLabel, billSubTypeLabel, verifyStatusLabel } from '@/constants/enums.js'
import { hasPerm } from '@/utils/perm'
import { entertainOfLine, hasEntertainLines, formatDateTime } from '@/utils/format'
import { attachKindOf, ATTACH_KIND_TEXT } from '@/utils/attachKind'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

// 审批历史(wf:history:list) / 关联发票(inv:list) 为受控权限：无权限时不发请求，
// 否则全局拦截器会对 4030 弹「缺少权限」错误（见 Y009154 工单）。
const canViewHistory = hasPerm('wf:history:list')
const canViewInvoice = hasPerm('inv:list')

/**
 * 单据详情面板（可复用）：单据信息 + 费用明细 + 附件（预览/触发 OCR）+ 识别结果（关联发票）+ 审批历史。
 *
 * 用途：
 *   1) 审批工作台「去处理」→ ApprovalDetailDrawer（抽屉 + 通过/驳回/核减）；
 *   2) 我的单据列表「详情」→ ExpenseBillListTab（抽屉只读）；
 *   3) 任何需要「让人看得见单据」的场合，避免各自实现一份导致字段含义不一。
 *
 * 契约：GET /tenant/expense/bill/detail/{id} → { bill, lines, attaches }
 *       GET /tenant/wf/query/history/page?bizId=&bizType= → 审批历史（注意参数是 bizId，不是 billId）
 */
const props = defineProps({
  billId: { type: [String, Number], default: null },
  billType: { type: String, default: '' },
  // G13 字段级权限：审批上下文传入待办 taskId；无 taskId（如「我的单据」入口）则不裁剪
  taskId: { type: String, default: '' },
})

// ───────────── G13 字段级表单权限（form_perm）─────────────
// 消费 GET /api/tenant/wf/task-form-perm?taskId= → fieldPerm 形如 { totalAmount: 'H', payeeAccountNo: 'R' }
// H=hidden 隐藏 / R=readonly 只读展示（详情面板本就只读，R 以「已脱敏只读」标签提示）。
// fail-open：接口失败 / 未配置 / 任务已办结 → 空 Map，按原样渲染。
const fieldPerm = ref({})
const fieldPermNode = ref('')
async function loadFieldPerm(taskId) {
  fieldPerm.value = {}
  fieldPermNode.value = ''
  if (!taskId) return
  try {
    const d = await flowApi.taskFormPerm(taskId)
    fieldPerm.value = d?.fieldPerm || {}
    fieldPermNode.value = d?.nodeKey || ''
  } catch { /* fail-open */ }
}
function isHidden(field) { return String(fieldPerm.value[field] || '').toUpperCase() === 'H' }
function isRo(field) { return String(fieldPerm.value[field] || '').toUpperCase() === 'R' }
function permLabel(field, label) { return isRo(field) ? label + '（只读）' : label }
/** 只读档对敏感串做脱敏展示（保留前 4 后 4），hidden 档由 v-if 整项隐藏 */
function maskIfRo(field, v) {
  if (!isRo(field) || !v) return v
  const s = String(v)
  return s.length > 8 ? s.slice(0, 4) + '****' + s.slice(-4) : '****'
}

const viewer = ref(null)
const loading = ref(false)
const loadError = ref('')
const tab = ref('info')
const bill = ref(null)
const lines = ref([])
const attaches = ref([])
const history = ref([])
// 审批历史查询失败的原因（空＝没失败过）。查询失败与"真的没有记录"必须分开呈现，
// 否则前序环节明明有记录，界面却对谁说"暂无审批记录"。
const historyError = ref('')
const invoices = ref([])
const ocrBusy = ref(null)

// 审批动作 → 中文/类型：名称表办结到 constants/enums，与审批列表、H5 审批历史共用一份
function actionLabel(a) { return a ? wfActionLabel(a) : '—' }
function actionType(a) { return wfActionType(a) }

function fmt(v) {
  if (v == null || v === '') return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function shortTime(t) {
  if (!t) return ''
  const d = new Date(String(t).replace(/-/g, '/'))
  if (isNaN(d.getTime())) return String(t)
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
function fmtDuration(ms) {
  if (!ms || ms < 0) return ''
  const m = Math.floor(ms / 60000)
  if (m < 60) return m + ' 分钟'
  const h = Math.floor(m / 60)
  if (h < 24) return h + ' 小时' + (m % 60) + ' 分'
  return Math.floor(h / 24) + ' 天' + (h % 24) + ' 小时'
}
function sumLine({ columns, data }) {
  return columns.map((c, i) => {
    if (i === 0) return '合计'
    if (c.label === '金额') return fmt(data.reduce((s, r) => s + Number(r.amount || 0), 0))
    if (c.label === '税额') return fmt(data.reduce((s, r) => s + Number(r.taxAmount || 0), 0))
    return ''
  })
}

/**
 * 招待三项（接待对象 / 接待人数 / 人均标准）详情渲染，查询范围见 utils/format.js。
 */
const hasEntertainLine = computed(() => hasEntertainLines(lines.value))

function preview(row) {
  if (!row.fileId) return
  viewer.value?.open([row.fileId], '单据附件')
}

/** 单附件触发 OCR：入参是 sys_file 的 fileId，后端解析其影像批次后整批识别（返回识别页数） */
async function runOcr(row) {
  if (!row.fileId) return
  ocrBusy.value = row.fileId
  try {
    const n = await imageApi.ocrByFile(row.fileId)
    ElMessage.success(`识别完成${typeof n === 'number' ? '，共处理 ' + n + ' 页' : ''}；结构化结果见下方「关联发票」`)
    await loadInvoices(props.billId)
  } catch (e) {
    ElMessage.error(t('bill.billDetailPanel.OCR'))
  } finally {
    ocrBusy.value = null
  }
}

/** 关联发票（识别结果的结构化写入数据库形态）：先取单据-发票绑定，再补齐票面信息 */
async function loadInvoices(billId) {
  invoices.value = []
  if (billId == null) return
  try {
    const bind = await invoiceApi.billInvoices(billId)
    const ids = (bind || []).map((b) => b.invoiceId).filter((x) => x != null)
    if (!ids.length) return
    let pool = []
    try {
      const page = await invoiceApi.page({ pageNum: 1, pageSize: 200, billNo: bill.value?.billNo })
      pool = page?.list || page?.records || []
    } catch { /* 票池查询失败时退化为仅显示绑定信息 */ }
    const byId = new Map(pool.map((p) => [p.id, p]))
    invoices.value = ids.map((id) => byId.get(id) || { id, invoiceNo: `发票ID ${id}` })
  } catch { /* 无权限或无绑定时静默为空 */ }
}

async function load() {
  const billId = props.billId
  if (billId == null) return
  loading.value = true
  loadError.value = ''
  bill.value = null; lines.value = []; attaches.value = []; history.value = []; invoices.value = []
  tab.value = 'info'
  try {
    const d = await billApi.detail(billId)
    // 契约：{ bill, lines, attaches }
    bill.value = d?.bill || null
    lines.value = d?.lines || []
    attaches.value = d?.attaches || []
    if (!bill.value) loadError.value = '后端未返回单据主体，可能已被作废或跨租户不可见。'
  } catch (e) {
    loadError.value = e?.message || '加载单据详情失败'
  }
  // 审批历史：按 bizId + bizType 过滤（bizType 缺失时只按 bizId）；无权限则跳过，避免 4030 弹错
  await loadHistory(billId)
  if (canViewInvoice) await loadInvoices(billId)
  loading.value = false
}

/**
 * 审批历史查询。
 * 与旧实现的区别：失败不再等价于「暂无审批记录」——把原因留在时间线位置上，给一句实话和重试入口，
 * 让办理人能自证"是取不到，不是没有"。
 */
async function loadHistory(billId) {
  history.value = []
  historyError.value = ''
  if (!canViewHistory) return
  try {
    const p = { pageNum: 1, pageSize: 100, bizId: billId }
    if (props.billType) p.bizType = props.billType
    const h = await flowApi.historyPage(p)
    const rows = h?.list || h?.records || []
    history.value = rows.slice().sort((a, b) => String(a.operTime || '').localeCompare(String(b.operTime || '')))
  } catch (e) {
    historyError.value = e?.message || '审批历史接口未返回具体原因'
  }
}

defineExpose({ reload: load })

const printVisible = ref(false)
const printBill = computed(() => bill.value || null)
const printLines = computed(() => (lines.value || []).map((l) => ({
  ...l,
  expenseItemName: l.expenseItemName || l.itemName || '',
  summary: l.summary || l.remark || '',
})))

watch(() => props.billId, (v) => { if (v != null) load() }, { immediate: true })
watch(() => props.taskId, (v) => { void loadFieldPerm(v || '') })
</script>

<style scoped>
.bdp { min-height: 120px; }
.bdp-head { margin-bottom: 8px; }
.bdp-no { font-size: 18px; font-weight: 700; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.bdp-title { font-size: 14px; font-weight: 600; margin-top: 6px; color: var(--app-text, #303133); }
.bdp-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 4px; }
.bdp-type { font-size: 13px; color: #909399; }
.bdp-amt { font-size: 13px; font-weight: 600; color: var(--app-text, #303133); }
.bdp-sec { font-size: 14px; font-weight: 600; margin: 16px 0 8px; }
.bdp-node { font-weight: 600; }
.bdp-oper { font-size: 13px; color: #409eff; }
.bdp-dur { font-size: 12px; color: #909399; margin-left: 8px; }
.bdp-comment { font-size: 12px; color: var(--app-text-secondary, #606266); margin-top: 2px; }
</style>
