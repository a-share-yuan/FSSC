<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>出纳付款批次</b>
        <!-- D-DEC-037：文案对齐「先审后付」时序——原文「出纳确认后执行付款」正是把先付后审写成操作指引，
             与下方 CONFIRMED 只读提示（同一缺陷的按钮）矛盾，改为「确认→审批办结→执行」。 -->
        <span style="color: #909399; font-size: 13px">审批通过自动组批推送（按收款方归组），出纳确认后须待付款审批办结再执行付款</span>
        <div style="margin-left: auto">
          <el-button v-if="canCreate" type="primary" size="small" @click="openPick">选待付记录成批</el-button>
          <el-button v-if="canCreate" type="warning" plain size="small" @click="groupNow">立即组批</el-button>
          <el-button type="info" size="small" @click="load">刷新</el-button>
          <el-button type="success" plain size="small" @click="exportCsv">导出</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        </div>
      </div>
    </template>
    <el-alert v-if="groupNotice" type="warning" show-icon :closable="true" :title="groupNotice" style="margin-bottom: 8px" />
    <el-table class="cm-target-pay" :data="rows" stripe v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="showDetails">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="batchNo" label="批次号" width="170"  sortable />
      <el-table-column prop="totalAmount" label="合计金额" width="110"  sortable :formatter="(r,c,v) => formatMoney(v)" />
      <el-table-column prop="detailCount" label="笔数" width="70"  sortable />
      <el-table-column prop="status" label="状态" width="100" sortable>
        <template #default="{ row }">
          <el-tag :type="row.status === BillStatus.PAID ? 'success' : row.status === 'FAILED' ? 'danger' : row.status === 'CONFIRMED' ? 'warning' : 'info'" size="small">{{ billStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="单据状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.billStatus" :type="billStatusType(row.billStatus)" size="small" effect="plain">{{ billStatusLabel(row.billStatus) }}</el-tag>
          <span v-else style="color: #c0c4cc; font-size: 12px">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="failReason" label="失败原因" min-width="160" show-overflow-tooltip sortable>
        <template #default="{ row }"><span v-if="row.status === 'FAILED'" style="color:#a32d2d">{{ row.failReason }}</span></template>
      </el-table-column>
      <el-table-column prop="finishTime" label="完成/失败时间" width="170"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
      <el-table-column label="凭证/记账" width="170">
        <template #default="{ row }">
          <div v-if="row.voucherNo" style="display: flex; flex-direction: column; gap: 2px">
            <el-link type="primary" :underline="false" @click="openVoucher(row)">
              <span style="font-size: 12px">{{ row.voucherNo }}</span>
            </el-link>
            <div v-if="row.voucherStatus === BillStatus.POSTED" style="display: inline-flex; align-items: center; gap: 2px">
              <el-icon color="#67c23a" size="13"><CircleCheck /></el-icon>
              <span style="color: #67c23a; font-size: 12px; font-weight: 600">已记账</span>
            </div>
            <span v-else style="color: #e6a23c; font-size: 12px">待记账</span>
          </div>
          <span v-else style="color: #c0c4cc; font-size: 12px">—</span>
          <!-- R5-D-079③：账证勾稽红标——「已作废却仍挂已记账凭证」「已付款却查无凭证」两组合给红色标记，
               并禁止再次付款（按钮侧由下方 !row.voucherAnomaly 门控＋executeBatch 守卫，服务端 execute 另有同源硬闸） -->
          <el-tooltip v-if="row.voucherAnomaly" :content="voucherAnomalyHint(row.voucherAnomaly)" placement="top">
            <div style="display: inline-flex; align-items: center; gap: 2px; margin-top: 2px">
              <el-icon color="#f56c6c" size="13"><WarningFilled /></el-icon>
              <span style="color: #f56c6c; font-size: 12px; font-weight: 600">{{ voucherAnomalyLabel(row.voucherAnomaly) }}</span>
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <template #default="{ row }">          <InlineActions :visible="true" @view="showDetails(row)" :show-approve="canApprove(row)" :show-reject="canApprove(row)" :show-trace="true" @approve="handleApprove(row)" @reject="handleReject(row)" @trace="openTrace(row)" />
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <el-button v-if="canConfirm && row.status === BillStatus.DRAFT" link type="primary" @click="confirmBatch(row)">确认</el-button>
          <el-button v-if="canVoid && row.status === BillStatus.DRAFT" link type="danger" @click="voidBatch(row)">作废</el-button>
          <el-button v-if="canExecute && row.status === BillStatus.APPROVED && !row.voucherAnomaly" link type="success" @click="executeBatch(row)">执行付款</el-button>
          <!-- D-DEC-037：CONFIRMED＝出纳已确认、TR_PAY 审批仍在途。服务端已有两道闸（D-DEC-287 前置收窄为
               APPROVED/FAILED ＋ D-DEC-199 打款前 hasActiveTask("TR_PAY") 复确认），此处同步撤下「执行付款」
               按钮改只读提示＝按钮，不再给出纳一个点了必被拒的付款入口（不新建闸，只对齐既有闸）。 -->
          <el-tag v-if="row.status === 'CONFIRMED'" type="warning" size="small" effect="plain">付款审批中·办结后方可执行</el-tag>
          <el-button v-if="canExecute && row.status === 'FAILED' && !row.voucherAnomaly" link type="warning" @click="executeBatch(row)">重试付款</el-button>
          <el-button v-if="canCreate && row.status === 'REJECTED'" link type="warning" @click="returnBatchToEdit(row)">编辑重提</el-button>
          <el-button link @click="showDetails(row)">明细</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!canList" description="当前账号无「付款批次查询」权限（pay:batch:list），请联系管理员开通出纳 / 资金支付岗角色" />
        <el-empty v-else description="暂无数据" />
      </template>
    </el-table>

    <TablePager :pager="pager" @change="load" />

  </el-card>

  <el-dialog v-model="dlgDetails" :title="'批次明细：' + detailBatchNo" width="900px">
    <el-table :data="detailRows" size="small" border stripe v-loading="detailLoading" empty-text="暂无明细" max-height="440">
      <el-table-column type="index" label="#" width="50" align="center" />
      <el-table-column prop="payeeName" label="收款方" min-width="150" show-overflow-tooltip />
      <el-table-column label="收款账号" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.bankAccount">
            <span v-if="row.bankName">{{ row.bankName }} · </span>{{ row.bankAccount }}
          </template>
          <span v-else style="color:#c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }">{{ formatMoney(row.amount) }} <span v-if="row.currencyCode && row.currencyCode !== 'CNY'" style="color:#909399;font-size:12px">{{ row.currencyCode }}</span></template>
      </el-table-column>
      <el-table-column prop="useFlag" label="用途" width="90" align="center" />
      <el-table-column prop="postscript" label="附言" min-width="110" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="detailStatusType(row.status)">{{ payStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bankSerial" label="银行流水号" width="160" show-overflow-tooltip />
      <el-table-column prop="failReason" label="失败原因" min-width="120" show-overflow-tooltip>
        <template #default="{ row }"><span v-if="row.failReason" style="color:#a32d2d">{{ row.failReason }}</span></template>
      </el-table-column>
    </el-table>
    <template #footer>
      <div style="display:flex; align-items:center; justify-content:space-between">
        <span style="color:#909399; font-size:12px">共 {{ detailRows.length }} 笔</span>
        <el-button @click="dlgDetails = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>

  <!-- D-DEC-438：执行付款被拦时的拦口详情。真缺陷是「闸无出口」——原来只有一条拼好的 toast，
       出纳看不出是哪条明细、缺哪一腿、该去哪个界面补，批次原地锁死在已审批态。
       本弹窗把后端同一条判据的结构化结果逐条列出：拦口原因 + 缺腿清单 + 处理出口。 -->
  <el-dialog v-model="dlgBlock" :title="'执行付款被拦截：' + blockBatchNo" width="1000px">
    <el-alert type="error" show-icon :closable="false" style="margin-bottom:10px"
      title="批次未扣款、仍停在当前状态"
      description="本闸不设放行／解拦出口：要么按下方逐条补齐单据后回到本批次直接重试执行（无需重建批次），要么改走预付或付款计划两条正式通道。" />
    <el-alert v-if="blockSummaryMsg" type="info" show-icon :closable="false" style="margin-bottom:12px"
      :title="'后端拦截原文'" :description="blockSummaryMsg" />
    <div v-if="!blockRows.length" style="color:#909399;font-size:13px;padding:8px 0">
      未能取到逐条拦口明细（拦截可能来自三单匹配以外的闸，如付款审批未办结、附件/合同齐套）。
      请按上方后端原文处理，或联系共享作业台核查。
    </div>
    <!-- D-DEC-438：一条拦口三段读法——缺哪一张单 / 涉及哪些单据号 / 下一步走哪条正式通道 -->
    <div v-for="(b, bi) in blockRows" :key="b.detailId" style="border:1px solid #e4e7ed;border-radius:6px;padding:10px 12px;margin-bottom:10px">
      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:8px">
        <b>{{ b.billNo || ('明细 ' + b.detailId) }}</b>
        <el-tag size="small" type="danger" effect="plain">{{ b.payeeName || '收款方未填' }}</el-tag>
        <span style="color:#606266;font-size:13px">{{ formatMoney(b.amount) }}</span>
        <el-tag v-if="b.matchStatus" size="small" :type="b.matchStatus === 'FAILED' ? 'danger' : 'warning'" effect="plain">
          三单匹配 {{ b.matchStatus }}
        </el-tag>
      </div>
      <div class="blk-sec">
        <span class="blk-sec-label">缺哪一张单</span>
        <template v-if="missingLegNames(b).length">
          <el-tag v-for="(m, mi) in missingLegNames(b)" :key="'m' + mi" size="small" type="warning" effect="plain"
                  style="margin:0 4px 2px 0">{{ m }}</el-tag>
        </template>
        <span v-else style="font-size:12px;color:#909399">发票／合同／到货三腿均已挂上，拦口来自其他条件（见下方原因）</span>
      </div>
      <div class="blk-sec">
        <span class="blk-sec-label">涉及单据号</span>
        <div style="display:flex;gap:12px;flex-wrap:wrap;font-size:12px;color:#606266">
          <span>付款单 {{ b.billNo || '—' }}</span>
          <span>到货（收货单）{{ b.receiptNo || '未关联' }} / {{ formatMoney(b.receiptAmount) }}</span>
          <span>采购订单 {{ b.poNo || '未关联' }} / {{ formatMoney(b.poAmount) }}</span>
          <span>合同 {{ b.contractNo || '未关联' }} / {{ formatMoney(b.contractAmount) }}</span>
          <span>发票金额 {{ formatMoney(b.invoiceAmount) }}（本次付款 {{ formatMoney(b.payAmount) }}）</span>
        </div>
      </div>
      <div class="blk-sec">
        <span class="blk-sec-label">下一步通道</span>
        <ul class="blk-channels">
          <li v-for="(c, ci) in blockChannels(b)" :key="'c' + ci">{{ c }}</li>
        </ul>
      </div>
      <div v-if="b.payeeNameOnBill || b.poSupplierName" style="margin-bottom:8px;font-size:12px;color:#606266">
        收款方：{{ b.payeeNameOnBill || '—' }} ｜ 采购订单供应商：{{ b.poSupplierName || '未关联采购订单' }}
      </div>
      <div v-for="(r, ri) in (b.reasons || [])" :key="'r' + ri" style="border-top:1px dashed #ebeef5;padding-top:6px;margin-top:6px">
        <div style="font-size:13px;color:#a32d2d">{{ r.msg }}</div>
        <div v-if="r.exit" style="font-size:12px;color:#0a7d43;margin-top:2px">处理出口：{{ r.exit }}</div>
      </div>
    </div>
    <template #footer>
      <div style="display:flex;align-items:center;justify-content:space-between">
        <span style="color:#909399;font-size:12px">共 {{ blockRows.length }} 条明细存在拦口</span>
        <el-button @click="dlgBlock = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>

  <!-- D-DEC-277（C1）：选待付记录成批 -->
  <el-dialog v-model="dlgPick" title="选待付记录成批" width="1000px" @open="loadAccounts">
    <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap">
      <el-input v-model="pickKeyword" placeholder="单号/标题/收款方" clearable style="width: 200px"
                @keyup.enter="() => { pickQuery.pageNum = 1; loadPick() }" />
      <el-button size="small" type="primary" @click="() => { pickQuery.pageNum = 1; loadPick() }">查询</el-button>
      <span style="color: #909399; font-size: 13px">出账账户</span>
      <el-select v-model="pickAccount" size="small" style="width: 260px" clearable
                 placeholder="不选＝用本公司默认出账账户">
        <el-option v-for="a in filteredAccounts" :key="a.id"
                   :label="(a.accountName || a.bankAccount) + (Number(a.isDefault) === 1 ? '（默认）' : '')"
                   :value="a.id" />
      </el-select>
      <span style="color: #909399; font-size: 12px">同一批次要求同公司、同币别；账户不一致时请在上方指定</span>
    </div>
    <el-table :data="pickRows" size="small" stripe max-height="380" v-loading="pickLoading" empty-text="暂无待付记录"
              @selection-change="(s) => (pickSelected = s)">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" label="来源单号" width="150" show-overflow-tooltip />
      <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
      <el-table-column prop="payeeName" label="收款方" min-width="130" show-overflow-tooltip />
      <el-table-column label="收款账号" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.bankAccount"><span v-if="row.bankName">{{ row.bankName }} · </span>{{ row.bankAccount }}</template>
          <span v-else style="color:#c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="110" align="right">
        <template #default="{ row }">{{ formatMoney(row.amount) }}<span v-if="row.currencyCode && row.currencyCode !== 'CNY'" style="color:#909399;font-size:12px"> {{ row.currencyCode }}</span></template>
      </el-table-column>
      <el-table-column prop="createTime" label="生成时间" width="160" />
    </el-table>
    <el-pagination style="margin-top: 8px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="pickTotal" :page-size="pickQuery.pageSize" :current-page="pickQuery.pageNum"
                   @current-change="(n) => { pickQuery.pageNum = n; loadPick() }" />
    <template #footer>
      <div style="display:flex; align-items:center; justify-content:space-between">
        <span style="color:#909399; font-size:12px">已勾选 {{ pickSelected.length }} 笔</span>
        <div>
          <el-button @click="dlgPick = false">取消</el-button>
          <el-button type="primary" :disabled="!pickSelected.length" @click="submitPick">成批（{{ pickSelected.length }}）</el-button>
        </div>
      </div>
    </template>
  </el-dialog>

<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="TR_PAY" />

  <CoachMark ref="payCm" :guide-key="_guideKey" :selector="'.cm-target-pay'" title="付款管理" text="付款管理页面处理各类付款单据的创建、审批和执行。" />
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
// 服务端 execute 入口另有同源硬闸兜底。
const VOUCHER_ANOMALY_LABEL = {
  VOID_WITH_POSTED_VOUCHER: '账证不符：已作废仍挂已记账凭证',
  PAID_WITHOUT_VOUCHER: '账证不符：已付款但无凭证',
}
function voucherAnomalyLabel(code) {
  return VOUCHER_ANOMALY_LABEL[code] || '账证不符'
}
function voucherAnomalyHint(code) {
  return code === 'VOID_WITH_POSTED_VOUCHER'
    ? '批次已作废但名下仍有已记账（POSTED）凭证：作废收尾没有把凭证一并带下去，账上还站着这笔款。已禁止再次付款，请到 凭证工作台 核对该批次名下凭证。'
    : '批次已付款（PAID）但名下查无任何有效凭证：钱已出去而账上无凭无据。已禁止再次付款，请到 凭证工作台 补制付款凭证后人工核销。'
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

// D-DEC-099：手工兜底的组批入口（显式点击才写数据）。服务端收尾已覆盖常态，
// 这里只处理历史遗留/收尾异常留下的 WAITING 记录。
async function groupNow() {
  if (!canCreate) return
  try {
    const g = (await payApi.autoGroup()) || { batchCount: 0, skippedReasons: [] }
    if (g.batchCount > 0) ElMessage.success(`已组批 ${g.batchCount} 组待付批次（确认后即可执行付款）`)
    // D-DEC-277（C2②′）：缺默认出账账户造成的"没建批"原先只写后端日志，界面上表现为批次凭空不变多。
    // 现在把原因摊在页面顶部（不用 toast，避免每次刷新都打断操作）。
    groupNotice.value = (g.skippedReasons || [])[0] || ''
    if (!(g.batchCount > 0) && !groupNotice.value) ElMessage.info('当前没有可组批的待付记录')
  } catch (e) { ElMessage.error(e.message) }
  await load()
}

// ---------- D-DEC-277（C1）：选待付记录一步成批 ----------
// 原先这里是"先建空批次（账户取本公司任一账户兜底）→ 再逐笔挂明细"，而挂明细接口 payApi.addDetail
// 在本页从未被调用过，所以手工建出的批次永远是 0 笔、确认时被空批闸口拒掉，等于没有出口。
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
    ElMessage.error(e.message || '待付清单加载失败')
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
  if (!pickSelected.value.length) { ElMessage.warning('请先勾选待付记录'); return }
  try {
    await payApi.createBatchFromPayments({
      billPaymentIds: pickSelected.value.map((r) => r.id),
      bankAccountId: pickAccount.value || undefined,
    })
    ElMessage.success('已成批 ' + pickSelected.value.length + ' 笔待付记录，可在列表中确认并执行付款')
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
    ElMessage.success('已确认')
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
    await ElMessageBox.confirm(`批次 ${row.batchNo} 已被驳回，确认转回草稿以便调整后重新确认？`, '编辑重提', { type: 'warning' })
    await payApi.batchReturnToEdit(row.id)
    ElMessage.success('已转回草稿，请调整明细后重新确认')
    load()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function voidBatch(row) {
  try {
    await ElMessageBox.confirm('确认作废批次 ' + row.batchNo + '？此操作不可恢复。', '作废确认', { type: 'warning' })
    await payApi.voidBatch(row.id)
    ElMessage.success('已作废')
    load()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function executeBatch(row) {
  // R5-D-079③：账证不符（红标）批次禁止再次付款——前端硬停（服务端 execute 入口有同源硬闸，
  // 此处只是把白跑一趟的网络往返与后端异常提示前置）。核对补平后红标消失，自然恢复可付。
  if (row.voucherAnomaly) {
    ElMessageBox.alert(voucherAnomalyHint(row.voucherAnomaly), '账证不符，禁止再次付款（R5-D-079）', { type: 'error' })
    return
  }
  // D-DEC-438：执行付款被拦时（后端抛 BizException → code!=0 → api.js 拦截器 reject），
  // 不再只留一条 toast：额外调只读拦口报告，把「哪条明细 / 缺哪一腿 / 去哪儿补」逐条摆出来。
  // 本改动不放宽那道闸，只是给它一个看得见的出口。
  try {
    const result = await payApi.execute(row.id)
    // D-DEC-088（X-39 丙）：DEFERRED＝收款账号未经供应商档案核对，整批<b>未提交银企</b>、
    // 批次状态未动 ⇒ 绝不能报"付款执行完成"。同样走拦口弹窗看逐条缺档明细与补录出口。
    if (result === 'DEFERRED') {
      await openBlockReport(row, { message: '本批未提交银企：存在收款账号没有供应商档案开户信息支撑的明细，批次状态未变、款项未付出' })
    } else if (result === 'FAILED') {
      ElMessage.warning('付款执行失败（银行拒绝），请在列表查看失败原因后重试')
    } else {
      ElMessage.success('付款执行完成')
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

/** 缺腿名优先用后端给的人话（含金额类差异），老包只回差异码时按码翻档 */
const LEG_NAME_BY_CODE = {
  NO_PO: '缺采购订单',
  NO_RECEIPT: '缺到货（收货单）',
  NO_CONTRACT: '缺合同',
  PAY_EXCEED_INVOICE: '发票不足（付款额 > 已关联发票）',
  INVOICE_OVER_RECEIPT: '发票超到货金额且超容差',
  RECEIPT_OVER_PO: '到货超采购订单金额',
  RECEIPT_QTY_OVER_PO: '到货数量超订购数量',
  RECON_UNCONFIRMED: '本期对账单未确认',
  PAYEE_NOT_SUPPLIER: '收款方与订单供应商不一致',
}
function missingLegNames(b) {
  const fromBackend = (b.missingLegs || []).filter((x) => x)
  if (fromBackend.length) return fromBackend
  return (b.missingLegCodes || []).map((c) => LEG_NAME_BY_CODE[c] || c)
}

/**
 * D-DEC-438：拦口 → 可走的正式通道。闸本身不提供放行出口，所以每条拦口都要落到
 * 「补齐后重试」之外的真路子：发票未到的可先付（费用预付款，票到冲销），
 * 按合同/发票到期日排期的走采购付款计划。
 */
function blockChannels(b) {
  const codes = [...(b.missingLegCodes || []), ...(b.blockers || [])]
  const has = (...cs) => cs.some((c) => codes.includes(c))
  const out = ['补齐上述单据后回到本批次直接重试「执行付款」（批次停在已审批态，无需重建批次）']
  if (has('PAY_EXCEED_INVOICE', 'INVOICE_OVER_RECEIPT', 'INVOICE_IN_TOLERANCE')) {
    out.push('发票确实未到而款项必须先付：改走「对公支付 › 费用预付款」发起预付，票到后由预付冲销本笔')
    out.push('不必先付的：按发票/合同到期日改走「采购 › 采购付款计划」排期，本期不组批')
  }
  if (has('NO_PO', 'NO_RECEIPT', 'NO_CONTRACT', 'RECEIPT_OVER_PO', 'RECEIPT_QTY_OVER_PO')) {
    out.push('到货/订单/合同任一条缺失或超额：先由采购与仓库在「对公三单匹配」补齐，或改走「采购 › 采购付款计划」按合同节点排期')
  }
  if (has('RECON_UNCONFIRMED')) out.push('本期对账单未确认：到「付款 › 应付对账（函）」确认后回本批次重试')
  // D-DEC-088（X-39 丙）：账号"无出处"＝整批暂缓提交银企（DEFERRED），补档案后原地重试，不写 FAILED
  if (has('PAYEE_ACCOUNT_NOT_ARCHIVED')) out.push('收款账号没有供应商档案开户信息支撑：到「主数据 › 供应商 › 银行账户」补录或更正该供应商开户账号，使档案与付款账号一致，再回本批次重试「执行付款」（本批未提交银企、批次状态未变）')
  if (has('BANK_ACCOUNT_MISSING')) out.push('收款账号为空：到收款方主数据补录账号后重新组批，或从本批次删除该条明细')
  if (has('SUPPLIER_BLACKLISTED', 'SUPPLIER_DISABLED')) out.push('供应商处于黑名单/停用：该笔不进入任何付款通道，需先更正主数据档案状态')
  if (has('FRAUD_OPEN')) out.push('风控记录未复审结案：复审通过后回本批次重试')
  if (has('THREE_WAY_MATCH_FAILED') && out.length === 1) out.push('三单匹配不通过：到「对公三单匹配」逐腿核对发票／到货／合同后再重试')
  return out
}

async function openBlockReport(row, err) {
  blockBatchNo.value = row.batchNo || String(row.id || '')
  blockSummaryMsg.value = (err && err.message) || '执行付款被拦截'
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
    ElMessage.info('凭证号：' + row.voucherNo)
  }
}

// D-DEC-026＋102（枚举直显·第三类根因＝列配置写在 JS）：
// 表格里的状态列已经过 billStatusLabel 翻档，但本页两处导出走的是 JS 列配置——
// 单元格直接把库中枚举码（CONFIRMED/APPROVED/PAID/FAILED…）写进 CSV，出纳拿到的文件仍是英文，
// 同一份数据在屏上中文、在导出里英文＝两套口径。这里只在显示层映射，库里存的值不动。
// 凭证列单列口径：op_voucher.status 只有 DRAFT/POSTED 两态，语义是「待记账/已记账」，
// 不能套单据状态的「草稿」，故与表格里的文案（已记账/待记账）保持一致。
const CSV_ENUM_LABEL = {
  status: (v) => billStatusLabel(v),
  billStatus: (v) => billStatusLabel(v),
  payFlowStatus: (v) => billStatusLabel(v),
  voucherStatus: (v) => (v === BillStatus.POSTED ? '已记账' : v === BillStatus.DRAFT ? '待记账' : v),
}
function csvCell(prop, v) {
  if (v == null || v === '') return ''
  let s = String(CSV_ENUM_LABEL[prop] ? CSV_ENUM_LABEL[prop](v) : v)
  if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
  return s
}

function exportCsv() {
  if (!rows.value.length) {
    ElMessage.warning('当前无数据可导出')
    return
  }
  const cols = [
    { prop: 'batchNo', label: '批次号' },
    { prop: 'totalAmount', label: '合计金额' },
    { prop: 'detailCount', label: '笔数' },
    { prop: 'status', label: '状态' },
    { prop: 'finishTime', label: '完成时间' },
  ]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((row) =>
    cols.map((c) => csvCell(c.prop, row?.[c.prop])).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `付款批次_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}

onMounted(load)

function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning('请先勾选要导出的记录'); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning('无法获取列信息'); return }
  const header = keys.join(',')
  // D-DEC-026＋102：全字段导出同样过一遍显示层枚举映射（status／billStatus／voucherStatus／payFlowStatus）
  const lines = selRows.value.map((row) => keys.map((k) => csvCell(k, row[k])).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `Pay_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
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
