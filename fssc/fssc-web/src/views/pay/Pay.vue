<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>{{ $t('pay.batch.pageTitle') }}</b>
        <!-- D-DEC-037：文案对齐「先审后付」时序——原文「出纳确认后执行付款」正是把先付后审写成操作指引，
             与下方 CONFIRMED 只读提示（同一缺陷的按钮）矛盾，改为「确认→审批办结→执行」。 -->
        <span style="color: #909399; font-size: 13px">{{ $t('pay.batch.hintAutoGroup') }}</span>
        <div style="margin-left: auto">
          <el-button v-if="canCreate" type="primary" size="small" @click="openPick">{{ $t('pay.batch.btnPickPending') }}</el-button>
          <el-button v-if="canCreate" type="warning" plain size="small" @click="groupNow">{{ $t('pay.batch.btnGroupNow') }}</el-button>
          <el-button type="info" size="small" @click="load">{{ $t('common.refresh') }}</el-button>
          <el-button type="success" plain size="small" @click="exportCsv">{{ $t('pay.batch.btnExport') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('pay.shared.btnExportSelected', { v1: selRows.length }) }}</el-button>
        </div>
      </div>
    </template>
    <el-alert v-if="groupNotice" type="warning" show-icon :closable="true" :title="groupNotice" style="margin-bottom: 8px" />
    <el-table class="cm-target-pay" :data="rows" stripe v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="showDetails">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="batchNo" :label="$t('field.batchNo')" width="170"  sortable />
      <el-table-column prop="totalAmount" :label="$t('pay.batch.colTotalAmount')" width="110"  sortable :formatter="(r,c,v) => formatMoney(v)" />
      <el-table-column prop="detailCount" :label="$t('pay.batch.colItemCount')" width="70"  sortable />
      <el-table-column prop="status" :label="$t('common.status')" width="100" sortable>
        <template #default="{ row }">
          <el-tag :type="row.status === BillStatus.PAID ? 'success' : row.status === 'FAILED' ? 'danger' : row.status === 'CONFIRMED' ? 'warning' : 'info'" size="small">{{ billStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('pay.shared.colBillStatus')" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.billStatus" :type="billStatusType(row.billStatus)" size="small" effect="plain">{{ billStatusLabel(row.billStatus) }}</el-tag>
          <span v-else style="color: #c0c4cc; font-size: 12px">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="failReason" :label="$t('pay.batch.colFailReason')" min-width="160" show-overflow-tooltip sortable>
        <template #default="{ row }"><span v-if="row.status === 'FAILED'" style="color:#a32d2d">{{ row.failReason }}</span></template>
      </el-table-column>
      <el-table-column prop="finishTime" :label="$t('pay.batch.colFinishFailTime')" width="170"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
      <el-table-column :label="$t('pay.batch.colVoucherPost')" width="170">
        <template #default="{ row }">
          <div v-if="row.voucherNo" style="display: flex; flex-direction: column; gap: 2px">
            <el-link type="primary" :underline="false" @click="openVoucher(row)">
              <span style="font-size: 12px">{{ row.voucherNo }}</span>
            </el-link>
            <div v-if="row.voucherStatus === BillStatus.POSTED" style="display: inline-flex; align-items: center; gap: 2px">
              <el-icon color="#67c23a" size="13"><CircleCheck /></el-icon>
              <span style="color: #67c23a; font-size: 12px; font-weight: 600">{{ $t('pay.shared.stPosted') }}</span>
            </div>
            <span v-else style="color: #e6a23c; font-size: 12px">{{ $t('pay.batch.stToPost') }}</span>
          </div>
          <span v-else style="color: #c0c4cc; font-size: 12px">—</span>
          <!-- R5-D-079③：账证勾稽红标——「已作废却仍挂已记账凭证」「已付款却查无凭证」两组合给红色标记，
               并禁止再次付款（按钮侧由下方 !row.voucherAnomaly 门控＋executeBatch 守卫，服务端 execute 另有同源强制校验） -->
          <el-tooltip v-if="row.voucherAnomaly" :content="voucherAnomalyHint(row.voucherAnomaly)" placement="top">
            <div style="display: inline-flex; align-items: center; gap: 2px; margin-top: 2px">
              <el-icon color="#f56c6c" size="13"><WarningFilled /></el-icon>
              <span style="color: #f56c6c; font-size: 12px; font-weight: 600">{{ voucherAnomalyLabel(row.voucherAnomaly) }}</span>
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="300">
        <template #default="{ row }">          <InlineActions :visible="true" @view="showDetails(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="true" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('pay.shared.btnTrace') }}</el-button>
          <el-button v-if="canConfirm && row.status === BillStatus.DRAFT" link type="primary" @click="confirmBatch(row)">{{ $t('pay.batch.btnConfirm') }}</el-button>
          <el-button v-if="canVoid && row.status === BillStatus.DRAFT" link type="danger" @click="voidBatch(row)">{{ $t('field.void') }}</el-button>
          <el-button v-if="canExecute && row.status === BillStatus.APPROVED && !row.voucherAnomaly" link type="success" @click="executeBatch(row)">{{ $t('pay.batch.btnExecute') }}</el-button>
          <!-- D-DEC-037：CONFIRMED＝出纳已确认、TR_PAY 审批仍在途。服务端已有两道校验（D-DEC-287 前置收窄为
               APPROVED/FAILED ＋ D-DEC-199 打款前 hasActiveTask("TR_PAY") 复确认），此处同步撤下「执行付款」
               按钮改只读提示＝按钮，不再给出纳一个点了必被拒的付款入口（不新建校验，只对齐既有校验）。 -->
          <el-tag v-if="row.status === 'CONFIRMED'" type="warning" size="small" effect="plain">{{ $t('pay.batch.tagApproving') }}</el-tag>
          <el-button v-if="canExecute && row.status === 'FAILED' && !row.voucherAnomaly" link type="warning" @click="executeBatch(row)">{{ $t('pay.batch.btnRetryPayment') }}</el-button>
          <el-button v-if="canCreate && row.status === 'REJECTED'" link type="warning" @click="returnBatchToEdit(row)">{{ $t('pay.batch.btnEditResubmit') }}</el-button>
          <el-button link @click="showDetails(row)">{{ $t('pay.shared.btnDetails') }}</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!canList" :description="$t('pay.batch.hintNoPermission')" />
        <el-empty v-else :description="$t('common.empty')" />
      </template>
    </el-table>

    <TablePager :pager="pager" @change="load" />

  </el-card>

  <!-- 批次明细弹窗（表格展示） -->
  <el-dialog v-model="dlgDetails" :title="$t('pay.pay.titleBatchDetail', { v1: detailBatchNo })" width="900px">
    <el-table :data="detailRows" size="small" border stripe v-loading="detailLoading" :empty-text="$t('pay.batch.hintNoDetails')" max-height="440">
      <el-table-column type="index" label="#" width="50" align="center" />
      <el-table-column prop="payeeName" :label="$t('pay.batch.colPayee')" min-width="150" show-overflow-tooltip />
      <el-table-column :label="$t('pay.batch.colPayeeAccount')" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.bankAccount">
            <span v-if="row.bankName">{{ row.bankName }} · </span>{{ row.bankAccount }}
          </template>
          <span v-else style="color:#c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('field.amount')" width="120" align="right">
        <template #default="{ row }">{{ formatMoney(row.amount) }} <span v-if="row.currencyCode && row.currencyCode !== 'CNY'" style="color:#909399;font-size:12px">{{ row.currencyCode }}</span></template>
      </el-table-column>
      <el-table-column prop="useFlag" :label="$t('pay.batch.colPurpose')" width="90" align="center" />
      <el-table-column prop="postscript" :label="$t('pay.batch.colPostscript')" min-width="110" show-overflow-tooltip />
      <el-table-column :label="$t('common.status')" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="detailStatusType(row.status)">{{ payStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bankSerial" :label="$t('pay.batch.colBankSerial')" width="160" show-overflow-tooltip />
      <el-table-column prop="failReason" :label="$t('pay.batch.colFailReason')" min-width="120" show-overflow-tooltip>
        <template #default="{ row }"><span v-if="row.failReason" style="color:#a32d2d">{{ row.failReason }}</span></template>
      </el-table-column>
    </el-table>
    <template #footer>
      <div style="display:flex; align-items:center; justify-content:space-between">
        <span style="color:#909399; font-size:12px">{{ $t('pay.batch.totalItems', { v1: detailRows.length }) }}</span>
        <el-button @click="dlgDetails = false">{{ $t('common.close') }}</el-button>
      </div>
    </template>
  </el-dialog>

  <!-- D-DEC-438：执行付款被拦时的拦口详情。真缺陷是「校验无出口」——原来只有一条拼好的 toast，
       出纳看不出是哪条明细、缺哪一环节、该去哪个界面补，批次原地锁死在已审批态。
       本弹窗把后端同一条判据的结构化结果逐条列出：拦口原因 + 缺环节清单 + 处理出口。 -->
  <el-dialog v-model="dlgBlock" :title="$t('pay.pay.titleBlocked', { v1: blockBatchNo })" width="1000px">
    <el-alert type="error" show-icon :closable="false" style="margin-bottom:10px"
      :title="$t('pay.batch.hintNoDebit')"
      :description="blockExitHint" />
    <el-alert v-if="blockSummaryMsg" type="info" show-icon :closable="false" style="margin-bottom:12px"
      :title="$t('pay.batch.labBackendVerbatim')" :description="blockSummaryMsg" />
    <div v-if="!blockRows.length" style="color:#909399;font-size:13px;padding:8px 0">
      {{ $t('pay.batch.hintNoBlockDetails') }}
      {{ $t('pay.pay.txtHandlePerBackend') }}
    </div>
    <!-- D-DEC-438：一条拦口三段读法——缺哪一张单 / 涉及哪些单据号 / 下一步走哪条正式通道 -->
    <div v-for="(b, bi) in blockRows" :key="b.detailId" style="border:1px solid #e4e7ed;border-radius:6px;padding:10px 12px;margin-bottom:10px">
      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:8px">
        <b>{{ b.billNo || $t('pay.batch.textDetailNo', { v1: b.detailId }) }}</b>
        <el-tag size="small" type="danger" effect="plain">{{ b.payeeName || t('pay.batch.stNoPayee') }}</el-tag>
        <span style="color:#606266;font-size:13px">{{ formatMoney(b.amount) }}</span>
        <el-tag v-if="b.matchStatus" size="small" :type="b.matchStatus === 'FAILED' ? 'danger' : 'warning'" effect="plain">
          {{ $t('pay.batch.matchStatus', { v1: b.matchStatus }) }}</el-tag>
      </div>
      <div class="blk-sec">
        <span class="blk-sec-label">{{ $t('pay.batch.secMissingLeg') }}</span>
        <template v-if="missingLegNames(b).length">
          <el-tag v-for="(m, mi) in missingLegNames(b)" :key="'m' + mi" size="small" type="warning" effect="plain"
                  style="margin:0 4px 2px 0">{{ m }}</el-tag>
        </template>
        <span v-else style="font-size:12px;color:#909399">{{ $t('pay.batch.hintAllLegsOk') }}</span>
      </div>
      <div class="blk-sec">
        <span class="blk-sec-label">{{ $t('pay.batch.secBillInvolved') }}</span>
        <div style="display:flex;gap:12px;flex-wrap:wrap;font-size:12px;color:#606266">
          <span>{{ $t('pay.batch.legPayBill', { v1: b.billNo || '—' }) }}</span>
          <span>{{ $t('pay.batch.legGrn', { v1: b.receiptNo || t('pay.match.stUnlinked'), v2: formatMoney(b.receiptAmount) }) }}</span>
          <span>{{ $t('pay.batch.legPo', { v1: b.poNo || t('pay.match.stUnlinked'), v2: formatMoney(b.poAmount) }) }}</span>
          <span>{{ $t('pay.batch.legContract', { v1: b.contractNo || t('pay.match.stUnlinked'), v2: formatMoney(b.contractAmount) }) }}</span>
          <span>{{ $t('pay.batch.legInvoice', { v1: formatMoney(b.invoiceAmount), v2: formatMoney(b.payAmount) }) }}</span>
        </div>
      </div>
      <div class="blk-sec">
        <span class="blk-sec-label">{{ $t('pay.batch.secNextChannel') }}</span>
        <ul class="blk-channels">
          <li v-for="(c, ci) in blockChannels(b)" :key="'c' + ci">{{ c }}</li>
        </ul>
      </div>
      <div v-if="b.payeeNameOnBill || b.poSupplierName" style="margin-bottom:8px;font-size:12px;color:#606266">
        {{ $t('pay.batch.payeeVsPoSupplier', { v1: b.payeeNameOnBill || '—', v2: b.poSupplierName || t('pay.match.noPoLinked') }) }}</div>
      <div v-for="(r, ri) in (b.reasons || [])" :key="'r' + ri" style="border-top:1px dashed #ebeef5;padding-top:6px;margin-top:6px">
        <div style="font-size:13px;color:#a32d2d">{{ r.msg }}</div>
        <div v-if="r.exit" style="font-size:12px;color:#0a7d43;margin-top:2px">{{ $t('pay.batch.exitPath', { v1: r.exit }) }}</div>
      </div>
    </div>
    <template #footer>
      <div style="display:flex;align-items:center;justify-content:space-between">
        <span style="color:#909399;font-size:12px">{{ $t('pay.batch.blockedCount', { v1: blockRows.length }) }}</span>
        <el-button @click="dlgBlock = false">{{ $t('common.close') }}</el-button>
      </div>
    </template>
  </el-dialog>

  <!-- D-DEC-277（C1）：选待付记录成批 -->
  <el-dialog v-model="dlgPick" :title="$t('pay.batch.btnPickPending')" width="1000px" @open="loadAccounts">
    <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap">
      <el-input v-model="pickKeyword" :placeholder="$t('pay.batch.phPickKeyword')" clearable style="width: 200px"
                @keyup.enter="() => { pickQuery.pageNum = 1; loadPick() }" />
      <el-button size="small" type="primary" @click="() => { pickQuery.pageNum = 1; loadPick() }">{{ $t('common.search') }}</el-button>
      <span style="color: #909399; font-size: 13px">{{ $t('pay.batch.secPayerAccount') }}</span>
      <el-select v-model="pickAccount" size="small" style="width: 260px" clearable
                 :placeholder="$t('pay.batch.phDefaultAccount')">
        <el-option v-for="a in filteredAccounts" :key="a.id"
                   :label="(a.accountName || a.bankAccount) + (Number(a.isDefault) === 1 ? t('pay.pay.tagDefault') : '')"
                   :value="a.id" />
      </el-select>
      <span style="color: #909399; font-size: 12px">{{ $t('pay.batch.hintSameCompany') }}</span>
    </div>
    <el-table :data="pickRows" size="small" stripe max-height="380" v-loading="pickLoading" :empty-text="$t('pay.batch.hintNoPending')"
              @selection-change="(s) => (pickSelected = s)">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" :label="$t('pay.batch.colSourceNo')" width="150" show-overflow-tooltip />
      <el-table-column prop="title" :label="$t('pay.shared.colTitle')" min-width="150" show-overflow-tooltip />
      <el-table-column prop="payeeName" :label="$t('pay.batch.colPayee')" min-width="130" show-overflow-tooltip />
      <el-table-column :label="$t('pay.batch.colPayeeAccount')" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.bankAccount"><span v-if="row.bankName">{{ row.bankName }} · </span>{{ row.bankAccount }}</template>
          <span v-else style="color:#c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('field.amount')" width="110" align="right">
        <template #default="{ row }">{{ formatMoney(row.amount) }}<span v-if="row.currencyCode && row.currencyCode !== 'CNY'" style="color:#909399;font-size:12px"> {{ row.currencyCode }}</span></template>
      </el-table-column>
      <el-table-column prop="createTime" :label="$t('pay.batch.colGeneratedAt')" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
    </el-table>
    <el-pagination style="margin-top: 8px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="pickTotal" :page-size="pickQuery.pageSize" :current-page="pickQuery.pageNum"
                   @current-change="(n) => { pickQuery.pageNum = n; loadPick() }" />
    <template #footer>
      <div style="display:flex; align-items:center; justify-content:space-between">
        <span style="color:#909399; font-size:12px">{{ $t('pay.batch.pickedCount', { v1: pickSelected.length }) }}</span>
        <div>
          <el-button @click="dlgPick = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" :disabled="!pickSelected.length" @click="submitPick">{{ $t('pay.batch.btnSubmitPick', { v1: pickSelected.length }) }}</el-button>
        </div>
      </div>
    </template>
  </el-dialog>

<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="TR_PAY" />

  <CoachMark ref="payCm" :guide-key="_guideKey" :selector="'.cm-target-pay'" :title="$t('pay.batch.coachTitle')" :text="$t('pay.batch.coachText')" />
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>
import { payStatusLabel } from '@/constants/enums'

import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, WarningFilled } from '@element-plus/icons-vue'
import { payApi, orgApi, masterApi, store } from '../../api'
import { hasPerm } from '../../utils/perm'
import { BillStatus, statusLabel as billStatusLabel, statusType as billStatusType } from '@/constants/billStatus'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import CoachMark from '@/components/CoachMark.vue'
import InlineActions from '@/components/InlineActions.vue'
import { useKeyboard } from '@/composables/useKeyboard'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import {formatDate, formatDateTime, formatMoney } from '@/utils/format'
import { useApprovalScope } from '../../composables/useApprovalScope'
import { useApprovalDialog } from '@/composables/useApprovalDialog'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// 审批按钮归属门控：仅当该单据存在派给当前用户的待办任务时才显示，
// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => location.reload(),
  'ctrl+n': () => { try { router.push(currentRoute.path.replace(/\/list$/, '/create')) } catch { /* noop */ } },
})
const payCm = ref(null)

// ---------- 权限收敛 ----------
// 本页菜单是 op:pay:list（出纳付款），但页面首屏实际调用的是 pay:batch:* 系列接口，
// 两套码不一致 → 实测 FIN_MANAGER/BIZ_FIN_MANAGER/FIN_DIRECTOR/BASE_FIN_MANAGER/PLATE_FIN_MANAGER
// 这 5 个角色（有 op:pay:list、无 pay:batch:list）进页即弹「缺少权限：pay:batch:list」；
// CFO 少 pay:batch:create 也会因自动组批报错。
// 故这里统一按「接口真实所需权限」收敛：无权的请求不发、无权按钮不渲染。
// hasPerm 已抽取至 utils/perm（与 Workbench.vue / FlowConfig.vue 共用）
const canList = hasPerm('pay:batch:list')       // 首屏列表/明细
const canCreate = hasPerm('pay:batch:create')   // 手动新建批次、自动组批
const canConfirm = hasPerm('pay:batch:confirm')
const canVoid = hasPerm('pay:batch:void')
const canExecute = hasPerm('pay:batch:execute')

// D-DEC-277（C2②′）：自动组批被跳过时的面向出纳的说明
const groupNotice = ref('')

// R5-D-079③：账证不符红标映射与解说出处（与后端 voucherAnomaly 判据同源，两组合：
// 已作废却仍挂已记账凭证 / 已付款却查无任何凭证）。红标行禁止再次付款——按钮门控＋executeBatch 守卫，
// 服务端 execute 入口另有同源强制校验默认处理。
const VOUCHER_ANOMALY_LABEL = computed(() => ({
  VOID_WITH_POSTED_VOUCHER: t('pay.batch.anomalyVoidWithPosted'),
  PAID_WITHOUT_VOUCHER: t('pay.batch.anomalyPaidNoVoucher'),
}))
function voucherAnomalyLabel(code) {
  return VOUCHER_ANOMALY_LABEL.value[code] || t('pay.batch.anomalyDefault')
}
function voucherAnomalyHint(code) {
  return code === 'VOID_WITH_POSTED_VOUCHER'
    ? t('pay.batch.anomalyVoidWithPostedMsg')
    : t('pay.batch.anomalyPaidNoVoucherMsg')
}

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const router = useRouter()

const selRows = ref([])
const pager = useTablePager({ mode: 'server', storageKey: 'pay_batch' })
const { rows, loading, total } = pager
const companies = ref([])
const bankAccounts = ref([])


async function load() {
  // 无 pay:batch:list（如只有 op:pay:list 的财务经理类角色）：不请求接口，页面显示无权限空态
  if (!canList) return
  // D-DEC-099：本方法改为纯读。原先在列表加载里调 autoGroup()——读动作带写副作用，
  // 批次生成时点与审批时点解耦（实测差 1 小时 46 分），且不持 pay:batch:create 的出纳（CASHIER）
  // 永远组不出批次。组批现由服务端在单据审批通过收尾时触发（PayBatchAutoGroupListener），
  // 页面只负责看；需要手工补组时用「立即组批」显式触发（仍限建批权限）。
  try {
    const r = await pager.load(async ({ pageNum, pageSize }) => {
      const p = await payApi.batchPage({ pageNum, pageSize })
      return { list: p.list || [], total: Number(p.total) || (p.list || []).length }
    })
    return r
  } catch (e) { ElMessage.error(e.message) }
}

// D-DEC-099：手工默认处理的组批入口（显式点击才写数据）。服务端收尾已覆盖常态，
// 这里只处理历史遗留/收尾异常留下的 WAITING 记录。
async function groupNow() {
  if (!canCreate) return
  try {
    const g = (await payApi.autoGroup()) || { batchCount: 0, skippedReasons: [] }
    if (g.batchCount > 0) ElMessage.success(t('pay.batch.msgGrouped', { v1: g.batchCount }))
    // D-DEC-277（C2②′）：缺默认出账账户造成的"没建批"原先只写后端日志，界面上表现为批次凭空不变多。
    // 现在把原因摊在页面顶部（不用 toast，避免每次刷新都打断操作）。
    groupNotice.value = (g.skippedReasons || [])[0] || ''
    if (!(g.batchCount > 0) && !groupNotice.value) ElMessage.info(t('pay.batch.msgNothingToGroup'))
  } catch (e) { ElMessage.error(e.message) }
  await load()
}

// ---------- D-DEC-277（C1）：选待付记录一步成批 ----------
// 原先这里是"先建空批次（账户取本公司任一账户默认处理）→ 再逐笔挂明细"，而挂明细接口 payApi.addDetail
// 在本页从未被调用过，所以手工建出的批次永远是 0 笔、确认时被空批校验拒掉，等于没有出口。
// 现在改为在待付清单里勾选记录、一次请求成批；出账账户可显式选择，不选就用本公司默认账户，
// 两者都没有时后端直接拒建并把原因返到界面（不再悄悄建出一张付不出去的批次）。
const dlgPick = ref(false)
const pickRows = ref([])
const pickSelected = ref([])
const pickLoading = ref(false)
const pickKeyword = ref('')
const pickAccount = ref(null)
const pickTotal = ref(0)
const pickQuery = reactive({ pageNum: 1, pageSize: 20 })

async function openPick() {
  dlgPick.value = true
  pickSelected.value = []
  await loadPick()
}

async function loadPick() {
  pickLoading.value = true
  try {
    const r = await payApi.waitingPayments({ ...pickQuery, keyword: pickKeyword.value || undefined })
    pickRows.value = r.list || []
    pickTotal.value = Number(r.total) || 0
  } catch (e) {
    pickRows.value = []
    ElMessage.error(e.message || t('pay.batch.msgLoadPendingFail'))
  } finally {
    pickLoading.value = false
  }
}

// 出账账户候选：勾选记录同属一家公司时按该公司过滤，未勾选则先列全量供参考
const accountOptions = ref([])
async function loadAccounts() {
  if (accountOptions.value.length) return
  try {
    const page = await masterApi['bank-account'].page({ pageNum: 1, pageSize: 200 })
    accountOptions.value = (page && (page.list || page.records)) || []
  } catch { accountOptions.value = [] }
}
const filteredAccounts = computed(() => {
  const companies = new Set(pickSelected.value.map((r) => r.companyId).filter((c) => c != null))
  if (companies.size !== 1) return accountOptions.value
  const c = [...companies][0]
  return accountOptions.value.filter((a) => a.companyId === c && String(a.status || 'NORMAL') === 'NORMAL')
})

async function submitPick() {
  if (!pickSelected.value.length) { ElMessage.warning(t('pay.batch.msgPickPendingFirst')); return }
  try {
    await payApi.createBatchFromPayments({
      billPaymentIds: pickSelected.value.map((r) => r.id),
      bankAccountId: pickAccount.value || undefined,
    })
    ElMessage.success(t('pay.batch.msgGroupedPrefix') + pickSelected.value.length + ' 笔待付记录，可在列表中确认并执行付款')
    dlgPick.value = false
    load()
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  }
}

const confirming = new Set()
async function confirmBatch(row) {
  if (confirming.has(row.id)) return // D-TR-1：防双击重复确认
  confirming.add(row.id)
  try {
    await payApi.confirm(row.id)
    ElMessage.success(t('pay.shared.stConfirmed'))
    load()
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  } finally {
    confirming.delete(row.id)
  }
}

// 驳回批次回退草稿（REJECTED→DRAFT）：回退后走既有 明细/确认 流程重报
async function returnBatchToEdit(row) {
  try {
    await ElMessageBox.confirm(t('pay.batch.ctxReturnDraft', { v1: row.batchNo }), t('pay.batch.btnEditResubmit'), { type: 'warning' })
    await payApi.batchReturnToEdit(row.id)
    ElMessage.success(t('pay.batch.msgBackToDraft'))
    load()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function voidBatch(row) {
  try {
    await ElMessageBox.confirm('确认作废批次 ' + row.batchNo + '？此操作不可恢复。', t('pay.batch.ctxVoidTitle'), { type: 'warning' })
    await payApi.voidBatch(row.id)
    ElMessage.success(t('status.void'))
    load()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function executeBatch(row) {
  // R5-D-079③：账证不符（红标）批次禁止再次付款——前端硬停（服务端 execute 入口有同源强制校验，
  // 此处只是把白跑一趟的网络往返与后端异常提示前置）。核对补平后红标消失，自然恢复可付。
  if (row.voucherAnomaly) {
    ElMessageBox.alert(voucherAnomalyHint(row.voucherAnomaly), t('pay.batch.ctxAnomalyTitle'), { type: 'error' })
    return
  }
  // D-DEC-438：执行付款被拦时（后端抛 BizException → code!=0 → api.js 拦截器 reject），
  // 不再只留一条 toast：额外调只读拦口报告，把「哪条明细 / 缺哪一环节 / 去哪儿补」逐条摆出来。
  // 本改动不放宽那道校验，只是给它一个看得见的出口。
  try {
    const result = await payApi.execute(row.id)
    // D-DEC-088（X-39 丙）：DEFERRED＝收款账号未经供应商档案核对，整批<b>未提交银企</b>、
    // 批次状态未动 ⇒ 绝不能报"付款执行完成"。同样走拦口弹窗看逐条缺档明细与补录出口。
    if (result === 'DEFERRED') {
      await openBlockReport(row, { message: t('pay.batch.msgBankNotSubmitted') })
    } else if (result === 'FAILED') {
      ElMessage.warning(t('pay.batch.msgPayFailed'))
    } else {
      ElMessage.success(t('pay.batch.msgPayDone'))
    }
  } catch (e) {
    await openBlockReport(row, e)
  }
  load()
}

/** D-DEC-438 拦口详情弹窗状态 */
const dlgBlock = ref(false)
const blockRows = ref([])
const blockBatchNo = ref('')
const blockSummaryMsg = ref('')

/** 缺环节名优先用后端给的人话（含金额类差异），老包只回差异码时按码翻档 */
const LEG_NAME_BY_CODE = computed(() => ({
  NO_PO: t('pay.batch.blkNoPo'),
  NO_RECEIPT: t('pay.batch.blkNoGrn'),
  NO_CONTRACT: t('pay.batch.blkNoContract'),
  PAY_EXCEED_INVOICE: t('pay.batch.blkInvoiceShort'),
  INVOICE_OVER_RECEIPT: t('pay.batch.blkInvOverGrn'),
  RECEIPT_OVER_PO: t('pay.batch.blkGrnOverPoAmount'),
  RECEIPT_QTY_OVER_PO: t('pay.batch.blkQtyOverPo'),
  RECON_UNCONFIRMED: t('pay.batch.blkReconUnconfirmed'),
  PAYEE_NOT_SUPPLIER: t('pay.batch.blkPayeeMismatch'),
}))
function missingLegNames(b) {
  const fromBackend = (b.missingLegs || []).filter((x) => x)
  if (fromBackend.length) return fromBackend
  return (b.missingLegCodes || []).map((c) => LEG_NAME_BY_CODE.value[c] || c)
}

/**
 * D-DEC-438：拦口 → 可走的正式通道。校验本身不提供放行出口，所以每条拦口都要落到
 * 「补齐后重试」之外的真路子：发票未到的可先付（费用预付款，票到冲销），
 * 按合同/发票到期日排期的走采购付款计划。
 */
function blockChannels(b) {
  const codes = [...(b.missingLegCodes || []), ...(b.blockers || [])]
  const has = (...cs) => cs.some((c) => codes.includes(c))
  const out = [t('pay.batch.hintRetryAfterFix')]
  if (has('PAY_EXCEED_INVOICE', 'INVOICE_OVER_RECEIPT', 'INVOICE_IN_TOLERANCE')) {
    out.push(t('pay.batch.exitInvoiceMissing'))
    out.push(t('pay.batch.exitNoUrgency'))
  }
  if (has('NO_PO', 'NO_RECEIPT', 'NO_CONTRACT', 'RECEIPT_OVER_PO', 'RECEIPT_QTY_OVER_PO')) {
    out.push(t('pay.batch.exitLegMissing'))
  }
  if (has('RECON_UNCONFIRMED')) out.push(t('pay.batch.exitReconUnconfirmed'))
  // D-DEC-088（X-39 丙）：账号"无出处"＝整批暂缓提交银企（DEFERRED），补档案后原地重试，不写 FAILED
  if (has('PAYEE_ACCOUNT_NOT_ARCHIVED')) out.push(t('pay.batch.exitAccountNotArchived'))
  if (has('BANK_ACCOUNT_MISSING')) out.push(t('pay.batch.exitAccountEmpty'))
  if (has('SUPPLIER_BLACKLISTED', 'SUPPLIER_DISABLED')) out.push(t('pay.batch.exitBlacklisted'))
  if (has('FRAUD_OPEN')) out.push(t('pay.batch.exitFraudOpen'))
  if (has('THREE_WAY_MATCH_FAILED') && out.length === 1) out.push(t('pay.batch.exitThreeWayFailed'))
  return out
}

/**
 * G2R-03（2026-10-19）：顶部「处理出口」此前恒取 pay.batch.hintNoBypass（三单匹配规则），
 * 反欺诈／供应商停用／账号未核档等非三单匹配校验被拦时，出纳按提示去补 PO／合同／发票，
 * 永远解不掉校验。此处按后端拦截原文识别校验类型，给出对应的真出口。
 */
const blockExitHint = computed(() => {
  const m = String(blockSummaryMsg.value || '')
  if (/反欺诈|欺诈检测|风控/.test(m)) return t('pay.batch.hintBypassFraud')
  if (/黑名单|已停用|停用/.test(m)) return t('pay.batch.hintBypassSupplier')
  if (/账号|账户|档案/.test(m)) return t('pay.batch.hintBypassAccount')
  return t('pay.batch.hintNoBypass')
})

async function openBlockReport(row, err) {
  blockBatchNo.value = row.batchNo || String(row.id || '')
  blockSummaryMsg.value = (err && err.message) || t('pay.batch.msgBlocked')
  try {
    const list = await payApi.blockReport(row.id, { silent: true })
    blockRows.value = Array.isArray(list) ? list : []
  } catch {
    // 取不到结构化报告时如实降级——弹窗仍显示后端原文，不假装已经列全
    blockRows.value = []
  }
  dlgBlock.value = true
}

async function showDetails(row) {
  detailBatchNo.value = row.batchNo || String(row.id || '')
  dlgDetails.value = true
  detailLoading.value = true
  detailRows.value = []
  try {
    detailRows.value = (await payApi.details(row.id)) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    detailLoading.value = false
  }
}

// 批次明细状态（WAITING/SUCCESS/FAILED）
function detailStatusType(s) { return s === 'SUCCESS' ? 'success' : s === 'FAILED' ? 'danger' : s === 'WAITING' ? 'warning' : 'info' }

// 批次明细弹窗状态
const dlgDetails = ref(false)
const detailRows = ref([])
const detailLoading = ref(false)
const detailBatchNo = ref('')

// 跳转到凭证查看页面
function openVoucher(row) {
  if (row.billId) {
    router.push('/expense')
  } else {
    ElMessage.info(t('pay.batch.msgVoucherNoPrefix') + row.voucherNo)
  }
}

// D-DEC-026＋102（枚举直接显示·第三类根因＝列配置写在 JS）：
// 表格里的状态列已经过 billStatusLabel 翻档，但本页两处导出走的是 JS 列配置——
// 单元格直接把库中枚举码（CONFIRMED/APPROVED/PAID/FAILED…）写进 CSV，出纳拿到的文件仍是英文，
// 同一份数据在屏上中文、在导出里英文＝两套规则。这里只在显示层映射，库里存的值不动。
// 凭证列单列规则：op_voucher.status 只有 DRAFT/POSTED 两态，语义是「待记账/已记账」，
// 不能套单据状态的「草稿」，故与表格里的文案（已记账/待记账）保持一致。
const CSV_ENUM_LABEL = computed(() => ({
  status: (v) => billStatusLabel(v),
  billStatus: (v) => billStatusLabel(v),
  payFlowStatus: (v) => billStatusLabel(v),
  voucherStatus: (v) => (v === BillStatus.POSTED ? t('pay.shared.stPosted') : v === BillStatus.DRAFT ? t('pay.batch.stToPost') : v),
}))
function csvCell(prop, v) {
  if (v == null || v === '') return ''
  let s = String(CSV_ENUM_LABEL.value[prop] ? CSV_ENUM_LABEL.value[prop](v) : v)
  if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
  return s
}

function exportCsv() {
  if (!rows.value.length) {
    ElMessage.warning(t('pay.shared.msgNoExportData'))
    return
  }
  const cols = [
    { prop: 'batchNo', label: t('field.batchNo') },
    { prop: 'totalAmount', label: t('pay.batch.colTotalAmount') },
    { prop: 'detailCount', label: t('pay.batch.colItemCount') },
    { prop: 'status', label: t('common.status') },
    { prop: 'finishTime', label: t('pay.batch.colFinishTime') },
  ]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((row) =>
    cols.map((c) => csvCell(c.prop, row?.[c.prop])).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = t('pay.batch.exportFileName', { v1: new Date().toISOString().slice(0, 10) })
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success(t('pay.shared.msgExportedCsv'))
}

onMounted(load)

function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('pay.shared.msgPickExportRows')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('pay.shared.msgNoColumns')); return }
  const header = keys.join(',')
  // D-DEC-026＋102：全字段导出同样过一遍显示层枚举映射（status／billStatus／voucherStatus／payFlowStatus）
  const lines = selRows.value.map((row) => keys.map((k) => csvCell(k, row[k])).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `Pay_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('pay.shared.msgExportedRows', { v1: selRows.value.length }))
}


// R4-06: Show guide on first visit
const _guideKey = 'fssc_guide_payCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => payCm.value?.show(), 800) } } catch {}
// InlineActions handlers

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: load })
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
.blk-sec { display: flex; flex-direction: column; gap: 4px; margin-bottom: 8px; }
.blk-sec-label { font-size: 12px; color: #909399; }
.blk-channels { margin: 0; padding-left: 18px; font-size: 12px; color: #0a7d43; line-height: 1.7; }
</style>
