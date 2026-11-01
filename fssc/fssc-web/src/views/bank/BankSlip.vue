<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>收款认领核销与回单归档</b>
        <span style="color:#909399; font-size:12px">银行流水 → 关联应收单冲余额（强勾稽防超领）→ 回单归档</span>
        <span style="margin-left:auto; display:flex; gap:8px">
          <el-select v-model="reconStatus" placeholder="对账状态" clearable size="small" style="width:140px" @change="reload">
            <el-option label="未对账" value="UNRECON" />
            <el-option label="已对账" value="RECON" />
            <el-option label="全部" value="ALL" />
          </el-select>
          <el-button type="primary" size="small" @click="reload">刷新</el-button>
        </span>
      </div>
    </template>

    <!-- 异步动作失败必须在页面上留下可见的错误态，不能只剩一张"看起来正常"的旧列表 -->
    <el-alert v-if="errorText" :title="errorText" type="error" show-icon closable
              class="err-bar" @close="errorText = ''" />

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" size="small" border stripe v-if="!loading" empty-text="暂无数据" @selection-change="s => selRows = s" @row-dblclick="openRowDetail">
      <el-table-column type="selection" width="45" :selectable="row => !isReconciled(row.reconStatus)" />
      <el-table-column prop="bankSerial" label="银行流水号" width="170" show-overflow-tooltip  sortable />
      <el-table-column prop="transDate" label="交易时间" width="150"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
      <el-table-column label="收/付" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="isReceiptDir(row.transType) ? 'success' : 'warning'" size="small">{{ transText(row.transType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="oppositeName" label="对方单位" min-width="130" show-overflow-tooltip  sortable />
      <el-table-column prop="oppositeAccount" label="对方账号" width="140"  sortable />
      <el-table-column label="勾对状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="isReconciled(row.reconStatus) ? 'success' : 'info'" size="small">{{ isReconciled(row.reconStatus) ? '已勾对' : '未对账' }}</el-tag>
        </template>
      </el-table-column>
      <!-- 勾对（recon_status=MATCHED）与认领（bank_reconcile 记录 match_status=CLAIMED 派生的 claimed 补充）是两件事，分列各自呈现 -->
      <el-table-column label="认领状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.claimed ? 'success' : 'info'" size="small">{{ row.claimed ? '已认领' : '未认领' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="回单状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.slipStatus === BillStatus.ARCHIVED ? 'success' : row.slipStatus === 'DOWNLOADED' ? 'warning' : 'info'" size="small">{{ slipText(row.slipStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">          <InlineActions :visible="true" @view="viewSlip(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="false" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <!-- 付款（借方）方向流水不做收款认领 ⇒ 界面不给这个入口（「查看」只出只读详情，认领必须由本按钮进）。
               方向校验在服务端 claimReceipt 独立成立，这里只是不让非法动作变得"可点"。 -->
          <el-button v-if="canClaim(row)" link type="primary" size="small" @click="openClaim(row)">收款认领</el-button>
          <el-button v-else-if="!isReconciled(row.reconStatus)" link type="primary" size="small" :disabled="true"
                     :title="CLAIM_DIR_HINT">收款认领</el-button>
          <!-- 禁点按钮文案与行事实同源——claimed（认领记录）为真显示「已认领」，否则只说明已勾对 -->
          <el-button v-else link type="success" size="small" :disabled="true">{{ row.claimed ? '已认领' : '已勾对' }}</el-button>
          <el-button link type="warning" size="small" @click="openArchive(row)">回单归档</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" background layout="total, prev, pager, next" :total="total"
                   :page-size="pageSize" :current-page="pageNum" @current-change="p => { pageNum = p; load() }" />

    <!-- 收款认领核销弹窗 -->
    <el-dialog v-model="dlgClaim" title="收款认领核销（贷方流水 → 应收单）" width="560px" destroy-on-close>
      <!-- 清单查询失败 / 认领被拒都要在弹窗内留下可见错误态，弹窗不静默关闭、行状态不动 -->
      <el-alert v-if="claimError" :title="claimError" type="error" show-icon class="err-bar" />
      <el-form ref="claimFormRef" :model="claimModel" :rules="claimRules" label-width="110px">
        <el-form-item label="银行流水">
          <span>{{ cur.bankSerial }} · {{ fmt(cur.amount) }} · {{ transText(cur.transType) }}方</span>
        </el-form-item>
        <el-form-item label="选择应收单" prop="claimReceivableId">
          <!-- 应收单清单是共享列表（不按流水逐条取），故不在此按方向做客户端过滤——
               方向与金额的判定权在服务端 claimReceipt，客户端筛掉的单子只会让人误判规则在哪一层。 -->
          <el-select v-model="claimReceivableId" filterable :loading="receivablesLoading"
                     :placeholder="receivablesLoading ? '应收单清单加载中…' : '按余额搜索应收单'" style="width:100%">
            <el-option v-for="rec in receivables" :key="rec.id" :value="rec.id"
                       :label="`${rec.billNo}（余额 ${fmt(rec.remainAmount)}）`" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgClaim = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doClaim">确认认领</el-button>
      </template>
    </el-dialog>

    <!-- 回单归档弹窗 -->
    <el-dialog v-model="dlgSlip" title="回单归档" width="460px" destroy-on-close>
      <!-- 归档失败同样要有显式落点，不能静默回到"看起来已归档"的行状态 -->
      <el-alert v-if="slipError" :title="slipError" type="error" show-icon class="err-bar" />
      <el-form ref="slipFormRef" :model="slipModel" :rules="slipRules" label-width="110px">
        <el-form-item label="银行流水">
          <span>{{ cur.bankSerial }}</span>
        </el-form-item>
        <el-form-item label="回单影像" prop="slipFileId">
          <UploadButton label="选择文件" accept=".jpg,.png,.pdf" biz-type="BANK_SLIP" zone="BUSINESS" :on-done="onSlipUploaded" />
          <span v-if="slipFileId" style="margin-left: 8px; color: #67c23a; font-size: 12px">已选择影像文件</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgSlip = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doArchive">确认归档</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
  <ApprovalDialog ref="approvalRef" />
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { p2Api } from '../../api'
import { useFormRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import UploadButton from '../../components/UploadButton.vue'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import InlineActions from '@/components/InlineActions.vue'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { useApprovalScope } from '../../composables/useApprovalScope'
import { useApprovalDialog } from '@/composables/useApprovalDialog'
import ApprovalDialog from '@/components/approval/ApprovalDialog.vue'

// 审批按钮归属门控：仅当该单据存在派给当前用户的待办任务时才显示，
// 杜绝「申请人看到自己单据的审批通过/驳回」「非办理人看到审批按钮」这类无效按钮。
const { canApprove, taskIdOf } = useApprovalScope()

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

const rows = ref([])
const selRows = ref([])
const loading = ref(false)
const saving = ref(false)
const reconStatus = ref('ALL')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dlgClaim = ref(false)
const dlgSlip = ref(false)
const cur = ref({})
const receivables = ref([])
const claimReceivableId = ref(null)
const slipFileId = ref('')
// 异步动作的显式错误态——列表级一个（页顶横幅），认领/归档弹窗内各一个。
// 规则：请求失败只写这些状态 + 一条可见提示，行状态与列表一律不在失败时改写。
const errorText = ref('')
const claimError = ref('')
const slipError = ref('')
const receivablesLoading = ref(false)

// 校验规则唯一规则＝中央登记表；散落的单选值组一个只读模型供 el-form 解析 prop
const { rules: claimRules } = useFormRules('BANK_CLAIM')
const { rules: slipRules } = useFormRules('BANK_SLIP')
const claimFormRef = ref(null)
const slipFormRef = ref(null)
const claimModel = computed(() => ({ claimReceivableId: claimReceivableId.value }))
const slipModel = computed(() => ({ slipFileId: slipFileId.value }))
const { submit: guardClaim } = useSubmitGuard(claimFormRef)
const { submit: guardArchive } = useSubmitGuard(slipFormRef)

const fmt = (v) => (v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
const transText = (t) => ({ CREDIT: '收', DEBIT: '付', RECEIPT: '收', IN: '收', OUT: '付' }[t] || t || '—')
const slipText = (s) => ({ NONE: '未生成', DOWNLOADED: '已下载', ARCHIVED: '已归档' }[s] || s || '—')
/**
 * 勾对口径：库里并存两族写法——历史导入落 RECON，
 * 勾对作业落 MATCHED（BankReconcileService），旧注释还提过 RECONED。
 * 只认 MATCHED 曾让 RECON 行整片显示「未对账」且「已对账」筛选恒空。
 */
const RECON_CODES = ['MATCHED', 'RECON', 'RECONED']
const isReconciled = (s) => RECON_CODES.includes(String(s || '').trim().toUpperCase())

// 收款（贷方）方向的取值集——与服务端 BankReconcileService 里的同名值集逐字一致
//（trans_type 取 CREDIT／RECEIPT／IN 者为收款，DEBIT 为付款方向，大小写不敏感）。
const CREDIT_TRANS_TYPES = ['CREDIT', 'RECEIPT', 'IN']
const isReceiptDir = (t) => !!t && CREDIT_TRANS_TYPES.some(c => c.toLowerCase() === String(t).toLowerCase())
/**
 * 收款认领入口的唯一判据：该流水尚未认领 且 属收款方向。
 * 服务端 claimReceipt 另有与本页无关的独立方向校验（真正的防线），本判据只负责不把非法动作摆到界面上。
 */
const canClaim = (row) => !!row && !isReconciled(row.reconStatus) && isReceiptDir(row.transType)
const CLAIM_DIR_HINT = '该流水不是收款方向，不可收款认领'

// 错误文案只取后端回传的业务消息本身（拦截器已把权限类消息换成中文规则），
// 前端不臆造成因、不写"后端不可达"这类与既有默认规则冲突的话。
const errText = (e) => (e && (e.message || e.msg)) || '操作未完成'

async function load() {
  loading.value = true
  // 本函数自身已消化失败（只写 errorText，不再向上抛），调用点无需再包一层，
  // 也不会把"列表刷新失败"错报成"刚才那笔认领没做成"。
  try {
    const d = await p2Api.bankStatementPage({ pageNum: pageNum.value, pageSize: pageSize.value, reconStatus: reconStatus.value || 'ALL' })
    rows.value = d.list || []
    total.value = d.total || 0
    errorText.value = ''
  } catch (e) {
    errorText.value = `银行流水列表加载失败：${errText(e)}。下方仍是上次成功加载的数据，请重试或点「刷新」。`
    ElMessage.error(errorText.value)
  } finally {
    loading.value = false
  }
}
const reload = () => { pageNum.value = 1; load() }

function requireSel() {
  if (!selRows.value.length) throw new Error('请先勾选未对账流水')
  return selRows.value
}

/** 取应收单清单：成功返回 true；失败清空清单并写明错误态后返回 false（不沿用上一条流水带来的旧清单）
 *  改调银行域专用端点 bankClaimableReceivables。
 *  原先借应收域的 receivableApi.page（要求 receivable:list 应收域权限），
 *  纯银企对账岗（只有 bank:reconcile:list）打开认领弹层时清单恒空、认领做不了；
 *  查询权限随域走，改调本域端点（同样由服务端做余额/方向判定，前端不做业务过滤）。
 */
async function loadReceivables() {
  receivablesLoading.value = true
  try {
    const d = await p2Api.bankClaimableReceivables({ pageNum: 1, pageSize: 1000 })
    receivables.value = d.list || []
    claimError.value = ''
    return true
  } catch (e) {
    receivables.value = []
    claimError.value = `应收单清单加载失败：${errText(e)}。可关闭弹窗后重试，或先点「刷新」重载列表。`
    return false
  } finally {
    receivablesLoading.value = false
  }
}

/** 行内「查看」只出只读详情：收款认领弹窗是写动作，挂在查看图标上等于查看态仍给提交入口。 */
function viewSlip(row) {
  return showRowDetail({
    bankSerial: row.bankSerial,
    transDate: formatDateTime(row.transDate),
    transType: transText(row.transType),
    amount: fmt(row.amount),
    oppositeName: row.oppositeName,
    oppositeAccount: row.oppositeAccount,
    reconStatus: isReconciled(row.reconStatus) ? '已勾对' : '未对账',
    claimed: row.claimed ? '已认领' : '未认领',
    slipStatus: slipText(row.slipStatus),
  }, {
    title: '银行流水详情',
    labels: { bankSerial: '银行流水号', transDate: '交易时间', transType: '收/付', amount: '金额',
      oppositeName: '对方单位', oppositeAccount: '对方账号', reconStatus: '勾对状态', claimed: '认领状态', slipStatus: '回单状态' },
  })
}

async function openClaim(row) {
  // 付款（借方）方向流水不做收款认领 ⇒ 界面不给这个入口；
  // 弹窗一开就能提交，判据必须在这里再立一道（行内「查看」已改走只读详情 viewSlip）。
  if (!canClaim(row)) {
    ElMessage.warning(CLAIM_DIR_HINT)
    return
  }
  cur.value = row
  claimReceivableId.value = null
  claimError.value = ''
  receivables.value = []
  dlgClaim.value = true
  await loadReceivables()
}

async function doClaim() {
  // 整段动作（校验校验 → 请求 → 成功后的收尾）都在 try 里；
  // 失败时弹窗保持打开、claimError 显式可见、行状态与列表不改写、不刷新——
  // 只有认领确实成功才关弹窗并刷新列表（避免把未生效的行照成"已认领"）。
  try {
    await guardClaim(async () => {
      saving.value = true
      claimError.value = ''
      try {
        const r = await p2Api.bankClaim(cur.value.id, claimReceivableId.value)
        ElMessage.success(`认领成功：${r.bankSerial} → ${r.receivableNo}，剩余余额 ${fmt(r.receivableBalance)}`)
        // 丙：认领照做，同人建单—同人勾对时补一条不阻断的复核提示
        if (r && r.warning) ElMessage({ type: 'warning', message: r.warning, duration: 6000 })
        claimError.value = ''
        dlgClaim.value = false
        load()
      } catch (err) {
        claimError.value = `收款认领未完成：${errText(err)}。该流水仍是未认领状态，应收单余额未被冲减；修正后可重试。`
        ElMessage.error(claimError.value)
      } finally {
        saving.value = false
      }
    })
  } catch (err) {
    claimError.value = claimError.value || `收款认领未完成：${errText(err)}`
    ElMessage.error(claimError.value)
  }
}

function openArchive(row) {
  cur.value = row
  slipFileId.value = row.slipFileId != null ? String(row.slipFileId) : ''
  slipError.value = ''
  dlgSlip.value = true
}

/** UploadButton 上传完成回调 */
function onSlipUploaded(rec) {
  if (rec && rec.id) slipFileId.value = String(rec.id)
}

async function doArchive() {
  // 与认领同一规则——失败留显式错误态、不关弹窗、不刷新列表
  try {
    await guardArchive(async () => {
      saving.value = true
      slipError.value = ''
      try {
        await p2Api.bankSlipArchive(cur.value.id, Number(slipFileId.value))
        ElMessage.success('回单已归档')
        slipError.value = ''
        dlgSlip.value = false
        load()
      } catch (err) {
        slipError.value = `回单归档未完成：${errText(err)}。该流水的回单状态未变更；修正后可重试。`
        ElMessage.error(slipError.value)
      } finally {
        saving.value = false
      }
    })
  } catch (err) {
    slipError.value = slipError.value || `回单归档未完成：${errText(err)}`
    ElMessage.error(slipError.value)
  }
}

onMounted(load)
// InlineActions approve/reject handlers

// 行内审批统一走共享审批弹窗：单据摘要 + 必填意见（通过预填「同意」可改写、驳回必须写原因），
// 取代原先的 confirm + 硬编码意见「审批通过」与无校验的驳回 prompt。
const { approvalRef, handleApprove, handleReject } = useApprovalDialog({ taskIdOf, reload: load })
</script>

<style scoped>
.pager { margin-top: 12px; justify-content: flex-end; }
/* 错误横幅与表格/表单之间留一行间距，保证它在页面上是"看得见的一条"而非贴字 */
.err-bar { margin-bottom: 10px; }
</style>