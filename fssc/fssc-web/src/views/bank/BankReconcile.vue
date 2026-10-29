<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="bank-reconcile-page">
    <div class="page-header" style="margin-bottom: 16px;">
      <h2 style="margin: 0; font-size: 18px; font-weight: 600;">银行对账</h2>
    </div>
    <!-- 顶部汇总 -->
    <el-row class="cm-target-reconcile" :gutter="12" style="margin-bottom:12px">
      <el-col :span="6">
        <el-card class="cm-target-reconcile" shadow="never">
          <el-statistic title="对账记录总数" :value="summary.total" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic title="自动勾对" :value="summary.matched" :value-style="{ color: '#67c23a' }" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic title="人工处理" :value="summary.manual" :value-style="{ color: '#e6a23c' }" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic title="勾对总金额" :value="summary.totalAmount" :precision="2" :value-style="{ color: '#409eff' }" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 操作栏 -->
    <el-card shadow="never" style="margin-bottom:12px">
      <div class="toolbar">
        <el-radio-group v-model="filterStatus" size="small" @change="loadReconcile">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="MATCHED">已勾对</el-radio-button>
          <el-radio-button value="MANUAL">人工处理</el-radio-button>
        </el-radio-group>
        <el-input v-model="searchKey" placeholder="搜索流水号/付款号..." clearable size="small" style="width:240px" @input="filterRows" />
        <el-button type="primary" size="small" :loading="running" @click="runReconcile">一键勾对</el-button>
        <el-button size="small" @click="loadReconcile">刷新</el-button>
        <el-button size="small" @click="searchKey = ''; filterStatus = ''; loadReconcile()">重置</el-button>
        <el-button v-if="canOpenSheet()" size="small" type="warning" @click="goSheet">调节表</el-button>
        <el-button v-if="canOpenSlip()" size="small" @click="goSlip">回单认领</el-button>
      </div>
    </el-card>

    <!-- 双向匹配面板 -->
    <el-row :gutter="12">
      <!-- 左侧：银行流水 -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">银行流水（对账记录）</span>
              <el-tag size="small" type="info">{{ bankRows.length }} 条</el-tag>
            </div>
          </template>
          <SkeletonList v-if="loading.bank" :rows="8" :cols="5" />
          <el-table class="cm-target-reconcile" :data="bankRows" size="small" stripe border max-height="540" empty-text="暂无数据"
            v-else
            :row-class-name="bankRowClass" @row-dblclick="openTrace">
            <el-table-column prop="serialNo" label="流水号" width="130" show-overflow-tooltip sortable />
            <el-table-column label="金额" width="110" align="right" sortable :sort-by="(r) => Number(r.amount)">
              <template #default="{ row }">
                <span :class="{ 'amt-matched': isMatched(row.matchStatus) }">{{ fmt(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="accountNo" label="账户" width="110" show-overflow-tooltip />
            <el-table-column prop="paymentNo" label="付款单号" min-width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.matchStatus)" size="small">{{ statusText(row.matchStatus) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：系统付款记录 -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">付款记录（对账记录）</span>
              <el-tag size="small" type="info">{{ payRows.length }} 条</el-tag>
            </div>
          </template>
          <SkeletonList v-if="loading.pay" :rows="8" :cols="5" />
          <el-table :data="payRows" size="small" stripe border max-height="540" empty-text="暂无数据"
            v-else
            :row-class-name="payRowClass"
          >
            <el-table-column prop="paymentNo" label="付款单号" width="130" show-overflow-tooltip sortable />
            <el-table-column label="金额" width="110" align="right" sortable :sort-by="(r) => Number(r.amount)">
              <template #default="{ row }">
                <span :class="{ 'amt-matched': isMatched(row.matchStatus) }">{{ fmt(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="serialNo" label="银行流水号" width="130" show-overflow-tooltip />
            <el-table-column prop="accountNo" label="账户" width="110" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.matchStatus)" size="small">{{ statusText(row.matchStatus) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 本分页器驱动的是「对账记录」两张表（bank_reconcile）。没有归属标签时，
         记录 0 行只显示「共 0 条」，而下方待勾对银行流水其实有数据 ⇒ 被读成"流水表 0 条"。
         标签指明分页器归属；待勾对两张表各自带计数（银行流水侧另有自己的分页器），
         空表计数不再遮蔽有数表。 -->
    <div class="pager-scope">
      <span class="pager-scope-label">对账记录分页（上方「银行流水／付款记录」两张表共用同一页数据）</span>
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 待勾对候选：人工勾对只允许从真身数据源选行——银行流水取 op_bank_statement、
         付款侧取「企业已付银行未付」付款明细；勾选行 id 即 op_bank_statement.id / exp_bill_payment.id，
         与记录行（bank_reconcile.id）彻底分开，杜绝三表 id 数字相近串表 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">待勾对候选（人工勾对面）</span>
          <div style="display:flex; gap:8px; align-items:center">
            <el-date-picker v-model="pendingMonth" type="month" value-format="YYYYMM" size="small" placeholder="付款侧月份" style="width:130px" @change="loadPendingCandidates" />
            <el-button size="small" @click="loadPendingCandidates">刷新候选</el-button>
          </div>
        </div>
      </template>
      <el-row :gutter="12">
        <el-col :span="12">
          <div class="pending-title">待勾对银行流水（勾选一条）
            <el-tag size="small" type="info" style="margin-left:6px">{{ pendingBankTotal }} 条</el-tag>
          </div>
          <el-table :data="pendingBankRows" size="small" stripe border max-height="300" empty-text="暂无待勾对银行流水"
            v-loading="loading.pendingBank"
            @selection-change="s => selPendingBank = s"
            @row-dblclick="openTrace">
            <el-table-column type="selection" width="40" :selectable="r => !r.claimed && Number(r.amount || 0) > 0" />
            <el-table-column prop="bankSerial" label="流水号" width="130" show-overflow-tooltip sortable />
            <el-table-column label="金额" width="110" align="right" sortable :sort-by="(r) => Number(r.amount)">
              <template #default="{ row }">{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="oppositeName" label="对手" min-width="110" show-overflow-tooltip />
            <el-table-column prop="transDate" label="日期" width="120" show-overflow-tooltip :formatter="(r, c, v) => formatDate(v)" />
          </el-table>
          <el-pagination
            v-model:current-page="pendingBankPageNum"
            v-model:page-size="pendingBankPageSize"
            :total="pendingBankTotal"
            :page-sizes="[20, 50, 100]"
            layout="total, prev, pager, next"
            small
            style="margin-top:8px; justify-content:flex-end"
            @size-change="onPendingBankSize"
            @current-change="loadPendingBank"
          />
        </el-col>
        <el-col :span="12">
          <div class="pending-title">待勾对付款记录（企业已付银行未付，勾选一条）
            <el-tag size="small" type="info" style="margin-left:6px">{{ pendingPayRows.length }} 条</el-tag>
          </div>
          <el-table :data="pendingPayRows" size="small" stripe border max-height="300"
            empty-text="暂无待勾对付款记录（请在调节表页先生成当月调节表）"
            v-loading="loading.pendingPay"
            @selection-change="s => selPendingPay = s">
            <el-table-column type="selection" width="40" />
            <el-table-column prop="paymentNo" label="付款流水号" width="140" show-overflow-tooltip sortable />
            <el-table-column label="金额" width="110" align="right" sortable :sort-by="(r) => Number(r.amount)">
              <template #default="{ row }">{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="payeeName" label="收款方" min-width="120" show-overflow-tooltip />
            <el-table-column prop="bizDate" label="日期" width="110" show-overflow-tooltip :formatter="(r, c, v) => formatDate(v)" />
          </el-table>
        </el-col>
      </el-row>
    </el-card>

    <!-- 人工勾对操作 -->
    <el-card shadow="never" style="margin-top:12px">
      <div class="manual-bar">
        <span v-if="selPendingBank.length === 1 && selPendingPay.length === 1" class="match-hint">
          已选银行流水 {{ selPendingBank[0].bankSerial || ('#' + selPendingBank[0].id) }}（{{ fmt(selPendingBank[0].amount) }}）
          ↔ 付款记录 {{ selPendingPay[0].paymentNo || ('#' + selPendingPay[0].paymentId) }}（{{ fmt(selPendingPay[0].amount) }}）
          <el-button type="success" size="small" style="margin-left:12px" @click="manualMatch">人工勾对</el-button>
        </span>
        <span v-else class="match-hint-muted">
          在【待勾对候选】面板各勾选一条银行流水与一条付款记录（金额须一致），然后点击"人工勾对"完成匹配
        </span>
      </div>
    </el-card>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="TR_BANK_RECON" />

  <CoachMark ref="reconcileCm" :guide-key="_guideKey" :selector="'.cm-target-reconcile'" title="银企对账" text="银企对账页面支持银行流水与系统凭证的自动匹配和手工调整。" />
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { p2Api } from '../../api'
import { readBillDeepLink } from '../../utils/bizEntry'
import { formatDate } from '@/utils/format'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import CoachMark from '@/components/CoachMark.vue'
import { pathDenyReason } from '@/router/pathAccess'

// 兄弟页深链：本账号打不开的就不给按钮（点了被弹回工作台并提示无权限，等于假入口）
const canOpenSheet = () => !pathDenyReason('/bank/reconcile-sheet')
const canOpenSlip = () => !pathDenyReason('/bank/slip')
const reconcileCm = ref(null)

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const router = useRouter()

// 数据
const allRows = ref([])
const bankRows = ref([])
const payRows = ref([])
// 待勾对候选（人工勾对面，真身 id 数据源）——银行侧取 op_bank_statement、付款侧取付款明细
const pendingBankRows = ref([])
const pendingPayRows = ref([])
// 待勾对银行流水改服务端分页（源表 op_bank_statement 2376 行、其中 UNRECON 711，
// 旧写法一次拉 200 行且客户端排除表从不命中 ⇒ 硬截断＋混入已勾对行）。
const pendingBankPageNum = ref(1)
const pendingBankPageSize = ref(50)
const pendingBankTotal = ref(0)
const selPendingBank = ref([])
const selPendingPay = ref([])
const pendingMonth = ref(new Date().getFullYear() + String(new Date().getMonth() + 1).padStart(2, '0'))
const searchKey = ref('')
const filterStatus = ref('')
const running = ref(false)
const loading = reactive({ bank: false, pay: false, pendingBank: false, pendingPay: false })
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

/**
 * 本单上下文（「去处理跳到资金计划页且无本单据上下文」的对账半）。
 * 待办/我的单据带 ?billNo=SER…&billId=20 进来时：
 *  · billNo 写进搜索框（本列即 bank_reconcile.serial_no，与 BillRouteRegistry:229 的 bizNoCol 同列）；
 *  · billId 作精确兜底——md_bank_account 无单号列、对账域也无 keyword 入参，光靠单号可能筛空。
 * 该列表接口只有 matchStatus 一种服务端过滤（BankReconcileController:36-41 无 keyword 形参），
 *    所以首屏用一次性宽页取回后再客户端定位，否则默认 10 行里根本不含被审那一张。
 *    宽页只在落地那一次生效，之后翻页恢复正常页宽（deepLocate 用完即置空）。
 */
const DEEP_LOCATE_SIZE = 500
const deepLink = readBillDeepLink(useRoute())
const deepLocateOnce = ref(deepLink.hasAny)
const deepRowId = ref(deepLink.billId)
if (deepLink.billNo) searchKey.value = deepLink.billNo

const summary = computed(() => {
  const total = allRows.value.length
  // 已勾对规则四处一次办结——MATCHED（自动勾对）＋CONFIRMED（审批定稿）＋CLAIMED（收款认领）
  // 同属"已勾对"，统计/筛选（服务端 page() 同步）/状态文案/勾选禁用四处共用 isMatched()，不得各自漂移
  const matched = allRows.value.filter(r => isMatched(r.matchStatus)).length
  const manual = allRows.value.filter(r => r.matchStatus === 'MANUAL').length
  const totalAmount = allRows.value.reduce((s, r) => s + Number(r.amount || 0), 0)
  return { total, matched, manual, totalAmount }
})

async function loadReconcile() {
  loading.bank = true
  loading.pay = true
  try {
    // 参数名对齐后端（原先发的是 page/size，后端形参是 pageNum/pageSize ⇒ 一直被当成默认第 1 页 10 行）
    const once = deepLocateOnce.value
    const r = await p2Api.bankReconcilePage({
      pageNum: once ? 1 : pageNum.value,
      pageSize: once ? DEEP_LOCATE_SIZE : pageSize.value,
      matchStatus: filterStatus.value || undefined,
    })
    const records = r?.list || r?.records || []
    total.value = r?.total ?? r?.totalCount ?? records.length
    allRows.value = records
    // 拆分：有 serialNo 的视为银行流水侧，有 paymentNo 的视为系统付款侧
    // 但一条记录可能两边都有（已勾对），所以两边都展示
    bankRows.value = records.filter(r => r.serialNo)
    payRows.value = records.filter(r => r.paymentNo)
    if (searchKey.value || once) filterRows()
    // 主键定位只在落地那一次生效，翻页/换筛选后回到正常规则
    if (once) { deepLocateOnce.value = false; deepRowId.value = null }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.bank = false
    loading.pay = false
  }
}

function handleSizeChange(val) {
  pageSize.value = val
  pageNum.value = 1
  loadReconcile()
}

function handleCurrentChange(val) {
  pageNum.value = val
  loadReconcile()
}

function filterRows() {
  const k = searchKey.value.toLowerCase()
  const id = deepRowId.value
  if (!k && id == null) {
    bankRows.value = allRows.value.filter(r => r.serialNo)
    payRows.value = allRows.value.filter(r => r.paymentNo)
    return
  }
  // 命中规则：搜索框（流水号/付款单号）命中，或深链带进来的本单主键命中（billId 兜底）
  const hit = (r) => (k && ((r.serialNo || '').toLowerCase().includes(k) ||
      (r.paymentNo || '').toLowerCase().includes(k))) ||
      (id != null && String(r.id) === String(id))
  bankRows.value = allRows.value.filter(r => r.serialNo && hit(r))
  payRows.value = allRows.value.filter(r => r.paymentNo && hit(r))
}

// 一键勾对
async function runReconcile() {
  try {
    await ElMessageBox.confirm('执行自动勾对？将匹配金额一致的银行流水与系统付款记录。', '勾对', { type: 'info' })
    running.value = true
    const r = await p2Api.bankReconcileRun()
    const matched = Number(r?.matched || 0)
    const unmatched = Number(r?.unmatched || 0)
    const ambiguous = Number(r?.ambiguous || 0)
    const foreign = Number(r?.foreignSerialBlocked || 0)
    // 勾对结果必须当场反馈——未勾对的流水要当场看见，而不是等调节表环节才算出来
    // 丙：同人建单—同人勾对的收款核销单在此当场提示复核，不阻断本轮勾对
    ElMessage({
      type: (unmatched > 0 || r?.selfMatchWarning) ? 'warning' : 'success',
      duration: 6000,
      message: `自动勾对完成：勾对 ${matched} 条、未勾对 ${unmatched} 条`
        + (ambiguous > 0 ? `（其中同金额多候选拒配 ${ambiguous} 条，请人工勾对）` : '')
        + (foreign > 0 ? `（其中 ${foreign} 条的同金额付款已归属其他银行流水号，拒绝顶替）` : '')
        + (r?.selfMatchWarning ? `（${r.selfMatchWarning}）` : ''),
    })
    const serials = Array.isArray(r?.unmatchedSerials) ? r.unmatchedSerials : []
    if (serials.length) {
      ElMessage({ type: 'info', duration: 10000, message: '未勾对流水：' + serials.slice(0, 12).join('、') + (serials.length > 12 ? ' 等' + serials.length + '条' : '') })
    }
    await loadReconcile()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  } finally {
    running.value = false
  }
}

// 待勾对候选
/**
 * 人工勾对面的真身数据源：
 *  · 银行流水侧：GET /bank/reconcile/statement/page（op_bank_statement），剔除已勾对规则
 *    （MATCHED/RECONED/MANUAL，与后端 manualMatch 重复勾对校验同一规则），行 id 即 op_bank_statement.id；
 *  · 付款侧：GET /bank/reconcile/items?itemType=ENT_OUT（企业已付银行未付，来源 exp_bill_payment），
 *    行 id 从 sourceRef="PAYMENT:{id}" 解析，即 exp_bill_payment.id。
 * 两侧勾选后提交的 id 语义与后端一致，不再传 bank_reconcile 记录行的 id。
 */
async function loadPendingBank() {
  loading.pendingBank = true
  try {
    // 服务端按 reconStatus=UNRECON 过滤「未勾对」集合并分页回传总数。
    // 旧写法 pageSize:200 一次拉回后再用 !['MATCHED','RECONED','MANUAL'].includes(reconStatus) 客户端排除——
    // 而本列 reconStatus 实值只有 RECON/UNRECON，排除表从不命中（等于没排除），且 200 行硬截断漏掉其余未勾对流水。
    const r = await p2Api.bankStatementPage({
      pageNum: pendingBankPageNum.value,
      pageSize: pendingBankPageSize.value,
      reconStatus: 'UNRECON',
    })
    pendingBankRows.value = r?.list || r?.records || []
    pendingBankTotal.value = Number(r?.total ?? 0)
  } catch (e) {
    ElMessage.error('待勾对银行流水加载失败：' + (e.message || e))
  } finally {
    loading.pendingBank = false
  }
}

function onPendingBankSize() { pendingBankPageNum.value = 1; loadPendingBank() }

async function loadPendingPay() {
  loading.pendingPay = true
  try {
    const items = await p2Api.bankReconItems({ month: pendingMonth.value, itemType: 'ENT_OUT' })
    pendingPayRows.value = (items || []).map(it => {
      const m = /^PAYMENT:(\d+)$/.exec(String(it.sourceRef || ''))
      return {
        paymentId: m ? Number(m[1]) : null,
        paymentNo: it.serialNo,
        payeeName: it.oppositeName,
        amount: it.amount,
        bizDate: it.bizDate,
      }
    }).filter(x => x.paymentId != null)
  } catch (e) {
    ElMessage.error('待勾对付款明细加载失败：' + (e.message || e))
  } finally {
    loading.pendingPay = false
  }
}

async function loadPendingCandidates() {
  await Promise.all([loadPendingBank(), loadPendingPay()])
}

// 人工勾对
async function manualMatch() {
  if (selPendingBank.value.length !== 1 || selPendingPay.value.length !== 1) {
    ElMessage.warning('请各勾选一条银行流水与一条付款记录（人工勾对一次只配一对）')
    return
  }
  const b = selPendingBank.value[0]
  const p = selPendingPay.value[0]
  const bankAmount = Number(b.amount || 0)
  const payAmount = Number(p.amount || 0)
  const diff = Math.abs(bankAmount - payAmount)
  // 金额一致强制校验（与后端 AMOUNT_DIFF_TOLERANCE 双层同一规则，超差直接拒绝，不再"确认后仍勾对"）
  if (bankAmount <= 0) {
    ElMessage.warning('所选流水金额缺失或非正数，无法勾对')
    return
  }
  if (diff > 0.01) {
    ElMessage.error(`金额不一致：银行流水 ${fmt(bankAmount)}，付款记录 ${fmt(payAmount)}，差额 ${fmt(diff)}，禁止勾对`)
    return
  }
  // 提交真身 id：bankIds=[op_bank_statement.id]，payIds=[exp_bill_payment.id]
  try {
    await p2Api.bankManualMatch({ bankIds: [b.id], payIds: [p.paymentId], diff })
  } catch (e) {
    ElMessage.error('人工勾对保存失败：' + (e.message || e))
    return
  }
  ElMessage.success('人工勾对完成')
  await loadReconcile()
  await loadPendingCandidates()
}

// 导航
function goSheet() { router.push('/bank/reconcile-sheet') }
function goSlip() { router.push('/bank/slip') }

// 辅助方法
function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
// MATCHED（自动勾对）＋CONFIRMED（审批定稿）＋CLAIMED（收款认领核销）同属"已勾对"——
// 统计卡、服务端「已勾对」筛选、状态文案、待勾对勾选禁用四处共用本判定，不得按单值判
function isMatched(s) {
  return s === 'MATCHED' || s === 'CONFIRMED' || s === 'CLAIMED'
}
function statusTag(s) {
  return s === 'CONFIRMED' ? 'success' : s === 'MATCHED' ? 'success' : s === 'CLAIMED' ? 'success' : s === 'MANUAL' ? 'warning' : 'info'
}
function statusText(s) {
  return s === 'CONFIRMED' ? '已定稿' : s === 'MATCHED' ? '已勾对' : s === 'CLAIMED' ? '已认领' : s === 'MANUAL' ? '人工' : '未勾对'
}
function bankRowClass({ row }) {
  return isMatched(row.matchStatus) ? 'row-matched' : row.matchStatus === 'MANUAL' ? 'row-manual' : ''
}
function payRowClass({ row }) {
  return isMatched(row.matchStatus) ? 'row-matched' : row.matchStatus === 'MANUAL' ? 'row-manual' : ''
}

onMounted(() => {
  loadReconcile()
  loadPendingCandidates()
})

// Show guide on first visit
const _guideKey = 'fssc_guide_reconcileCm'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => reconcileCm.value?.show(), 800) } } catch {}
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.panel-title { font-size: 14px; font-weight: 600; color: #303133; }
.manual-bar { padding: 8px 0; text-align: center; }
.pending-title { font-size: 13px; font-weight: 600; color: #606266; margin-bottom: 6px; }
.pager-scope { display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-top: 12px; flex-wrap: wrap; }
.pager-scope-label { font-size: 12px; color: #909399; }
.match-hint { color: #409eff; font-size: 14px; }
.match-hint-muted { color: #909399; font-size: 13px; }
.amt-matched { color: #67c23a; font-weight: 600; }
:deep(.row-matched) { background-color: #f0f9eb !important; }
:deep(.row-manual) { background-color: #fdf6ec !important; }
</style>
