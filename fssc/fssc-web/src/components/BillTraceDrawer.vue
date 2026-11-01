<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-drawer
    v-model="visible"
    title="单据全链路联查"
    direction="rtl"
    size="640px"
    :destroy-on-close="true"
  >
    <div v-loading="loading" class="trace-content">
      <!-- 单据基本信息 -->
      <div class="trace-header" v-if="billInfo">
        <div class="trace-bill-no">{{ billInfo.billNo || '-' }}</div>
        <div class="trace-bill-meta">
          <el-tag size="small" :type="statusType(billInfo.status)">{{ headStatusLabel(billInfo.status) }}</el-tag>
          <!-- 原先非费用域直出 billType 原码（采购订单审批抽屉头显示 PURCHASE_ORDER_REVIEW）；
               docTypeLabel 先查 BILL_TYPE 再查 BIZ_TYPE，费用域规则不变。 -->
          <span class="trace-type">{{ docTypeLabel(billInfo.billType) }}</span>
          <span class="trace-amount" v-if="billInfo.totalAmount != null">金额 ¥{{ fmt(billInfo.totalAmount) }}</span>
          <span class="trace-amount" v-if="billInfo.paidAmount > 0">已付 ¥{{ fmt(billInfo.paidAmount) }}</span>
        </div>
      </div>

      <!-- 一张单据表可挂多条流程域（gl_salary_run 同一行既挂计提链也挂发放链）。
           入口声明的域在该行没有实例时，后端只在「同表业务域」内纠正查询并如实标注，
           避免把「本轮走的是另一条域」显示成「本单没有流程」。跨表撞号不走此路径。 -->
      <el-alert
        v-if="chain && supported && chain.summary?.bizTypeCorrected"
        type="warning" :closable="false" show-icon
        :title="`本单实际走「${scopeLabel(chain.summary.scope)}」流程链`"
        :description="`入口声明的单据类型「${docTypeLabel(chain.summary.declaredBizType)}」下此单没有流程记录，已按同一业务范围改为按「${scopeLabel(chain.summary.scope)}」加载数据；如需查看「${docTypeLabel(chain.summary.declaredBizType)}」业务记录，请从该业务的入口进入。`"
        style="margin:0 0 10px" />

      <!-- 方案 A：下一跳行动条（按状态+链缺口算下一步；规则是费用单据状态机，他域不渲染） -->
      <NextActionBar v-if="billInfo && isExpense" :billInfo="billInfo" :chain="chain" />
      <!-- 方案 A：业务链时间线（可点击展开下游明细） -->
      <BillChainTimeline v-if="chain" :chain="chain" :status="billInfo?.status" @select="openSeg" />
      <!-- 关联点未命中：明确告知未接入，不用别单数据画半真半假的时间线 -->
      <el-alert
        v-if="chain && !supported"
        type="info" :closable="false" show-icon
        title="该单据未能取得全链路数据"
        :description="notSupportReason"
        style="margin:10px 0 14px" />

      <!-- 甲-1：删除 status 驱动的生命周期步进条。
           它与上方 BillChainTimeline 在同一抽屉里并存且互相打架（前者读 exp_bill.status，
           后者读各段证据），且第 5/6 格取的列（op_voucher.status / op_archive.archive_status）
           根本不由 exp_bill.status 承载 ⇒ 结构性永灰。进度规则统一走证据链时间线。 -->

      <!-- 联查明细（五段链路） -->
      <el-collapse v-if="chain && supported" v-model="openSections" class="chain-collapse">
        <!-- 付款记录（exp_bill_payment 属费用域专属表，他域不按 bill_id 借用 ⇒ 不渲染该段） -->
        <el-collapse-item v-if="isExpense && showSeg('PAYMENT', chain.payments)" name="payments">
          <template #title><b>付款记录</b><el-tag size="small" style="margin-left:8px" :type="chain.payments?.length ? 'success' : 'info'">{{ chain.payments?.length || 0 }} 笔</el-tag></template>
          <el-table :data="chain.payments || []" size="small" border>
            <el-table-column prop="payeeName" label="收款方" min-width="120" show-overflow-tooltip />
            <el-table-column prop="bankAccount" label="账号" min-width="140" show-overflow-tooltip />
            <el-table-column label="金额" width="110" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }"><el-tag size="small" :type="payTag(row.payStatus)">{{ payStatusLabel(row.payStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="payBatchNo" label="批次号" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ row.payBatchNo || '—' }}</template></el-table-column>
            <el-table-column label="付款时间" width="150"><template #default="{ row }">{{ row.payTime ? shortTime(row.payTime) : '—' }}</template></el-table-column>
          </el-table>
        </el-collapse-item>

        <!-- 付款批次 -->
        <el-collapse-item v-if="showSeg('BATCH', chain.batches)" name="batches">
          <template #title><b>付款批次</b><el-tag size="small" style="margin-left:8px" :type="chain.batches?.length ? 'success' : 'info'">{{ chain.batches?.length || 0 }} 批</el-tag></template>
          <el-empty v-if="!chain.batches?.length" description="未组批/无批次" :image-size="48" />
          <el-table v-else :data="chain.batches" size="small" border>
            <el-table-column prop="batchNo" label="批次号" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }"><el-tag size="small" :type="payTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="总金额" width="120" align="right"><template #default="{ row }">{{ fmt(row.totalAmount) }}</template></el-table-column>
            <el-table-column prop="detailCount" label="笔数" width="70" align="center" />
            <el-table-column label="完成时间" width="150"><template #default="{ row }">{{ row.finishTime ? shortTime(row.finishTime) : '—' }}</template></el-table-column>
          </el-table>
        </el-collapse-item>

        <!-- 记账凭证（含分录） -->
        <el-collapse-item v-if="showSeg('VOUCHER', chain.vouchers)" name="vouchers">
          <template #title><b>记账凭证</b><el-tag size="small" style="margin-left:8px" :type="chain.vouchers?.length ? 'success' : 'info'">{{ chain.vouchers?.length || 0 }} 张</el-tag></template>
          <el-empty v-if="!chain.vouchers?.length" description="无凭证（未审核通过或非凭证域单据）" :image-size="48" />
          <div v-else v-for="v in chain.vouchers" :key="v.id" class="voucher-card">
            <div class="voucher-head">
              <!-- op_voucher.voucher_word 存的是英文枚举 JOURNAL，原先直接拼在凭证号前
                   ⇒ 抽屉里显示「JOURNAL-JZ2026102700016」。字号走 VOUCHER_WORD 名称表（记／付／转／收）。 -->
              <span class="voucher-no">{{ voucherWordLabel(v.voucherWord) }}-{{ v.voucherNo }}</span>
              <el-tag size="small" :type="v.status === 'POSTED' ? 'success' : 'warning'">{{ v.status === 'POSTED' ? '已过账' : '草稿' }}</el-tag>
              <span class="voucher-meta">{{ v.voucherDate || '' }}</span>
              <span class="voucher-meta">借 {{ fmt(v.debitTotal) }} = 贷 {{ fmt(v.creditTotal) }}</span>
              <span class="voucher-meta" v-if="v.sourceType">来源 {{ voucherSourceLabel(v.sourceType) }}</span>
            </div>
            <div class="voucher-summary">{{ v.summary || '' }}</div>
            <el-table :data="(chain.voucherLines || {})[v.id] || []" size="small" border>
              <el-table-column prop="lineNo" label="#" width="40" align="center" />
              <el-table-column prop="summary" label="摘要" min-width="120" show-overflow-tooltip />
              <el-table-column label="科目" min-width="130">
                <template #default="{ row }">{{ row.subjectCode || '' }} {{ row.subjectName || '' }}</template>
              </el-table-column>
              <!-- op_voucher_line.direction 只有 DEBIT／CREDIT 两种取值，
                   原先写 row.direction === 'D' ⇒ 借方行恒落 else，两行都显示「贷」，
                   与同卡片「借 80,000.00 = 贷 80,000.00」自相矛盾。改走共用名称表 DIRECTION。 -->
              <el-table-column label="方向" width="55" align="center">
                <template #default="{ row }"><el-tag size="small" :type="dirTag(row.direction)" effect="plain">{{ directionLabel(row.direction) }}</el-tag></template>
              </el-table-column>
              <el-table-column label="金额" width="110" align="right"><template #default="{ row }">{{ fmt(row.amount) }}</template></el-table-column>
            </el-table>
          </div>
        </el-collapse-item>

        <!-- 电子档案 -->
        <el-collapse-item v-if="showSeg('ARCHIVE', chain.archives)" name="archives">
          <template #title><b>电子档案</b><el-tag size="small" style="margin-left:8px" :type="chain.archives?.length ? 'success' : 'info'">{{ chain.archives?.length || 0 }} 卷</el-tag></template>
          <el-empty v-if="!chain.archives?.length" description="未归档" :image-size="48" />
          <el-table v-else :data="chain.archives" size="small" border>
            <el-table-column prop="archiveNo" label="档案号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
            <el-table-column prop="archiveType" label="类型" width="80" align="center" :formatter="enumFormatter(ARCHIVE_TYPE)" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }"><el-tag size="small" :type="row.archiveStatus === 'ARCHIVED' ? 'success' : 'info'">{{ ARC_ST[row.archiveStatus] || '其他状态' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="归档时间" width="150"><template #default="{ row }">{{ row.archivedAt ? shortTime(row.archivedAt) : '—' }}</template></el-table-column>
          </el-table>
        </el-collapse-item>

        <!-- 审批轨迹（直读 wf_task_history，含提交/审批/驳回/转办/加签/催办/撤销，多轮次全含） -->
        <el-collapse-item name="traces">
          <template #title><b>审批轨迹</b><el-tag size="small" style="margin-left:8px" :type="chain.traces?.length ? 'success' : 'info'">{{ chain.traces?.length || 0 }} 条</el-tag></template>
          <el-empty v-if="!chain.traces?.length" description="本单还没有流程动作（未提单或流程未发起）" :image-size="48" />
          <el-timeline v-else style="padding-left:4px">
            <el-timeline-item v-for="(t, i) in chain.traces" :key="i" :timestamp="shortTime(t.operTime)" placement="top" :type="wfActionType(t.action)">
              <span class="trace-action">{{ wfActionLabel(t.action) }}</span>
              <span class="trace-trans" v-if="t.nodeName">{{ t.nodeName }}</span>
              <span class="trace-oper" v-if="t.operUserName">{{ t.operUserName }}</span>
              <span class="trace-oper" v-if="t.duration">· 停留 {{ formatMs(t.duration) }}</span>
              <div class="trace-remark" v-if="t.comment">{{ t.comment }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-collapse-item>
      </el-collapse>

      <!-- 审批流转时间轴＝上方「审批轨迹」折叠段（同一 chain.traces 数据源，不再并接第二个接口） -->
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { billApi } from '../api'
import { statusLabel, statusType } from '../constants/billStatus'
import { docTypeLabel, wfActionLabel, wfActionType, ARCHIVE_TYPE, enumFormatter, payStatusLabel, voucherSourceLabel, directionLabel, voucherWordLabel } from '@/constants/enums.js'
// 方案 A：业务链可视化 + 下一跳行动条（纯前端，复用 traceChain 契约）
import BillChainTimeline from './BillChainTimeline.vue'
import NextActionBar from './NextActionBar.vue'

const props = defineProps({
  modelValue: Boolean,
  billId: { type: [String, Number], default: null },
  /**
   * 单据域。后端据此选择联查关联点（exp_bill 主键 vs 本域 bizType+bizId）。
   * 各业务页传的是本域自增主键，与 exp_bill.id 不是同一坐标系——不声明就只能按费用单解释，
   * 撞号时会读到别人的单据（/）。未声明时后端宁缺勿错、不猜域。
   */
  bizType: { type: String, default: null },
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const loading = ref(false)
const billInfo = ref(null)
// 全链路联查数据
const chain = ref(null)
const openSections = ref([])
// 后端是否取得了本单据的链路数据（关联点未命中 → NOT_SUPPORTED）
const supported = computed(() => chain.value?.summary?.scope !== 'NOT_SUPPORTED')
// 费用域专属交互（下一跳行动条 / 生命周期步骤条 / 付款记录段）的开关
const isExpense = computed(() => chain.value?.summary?.scope === 'EXPENSE')

/**
 * 各段适用性：后端 summary.segmentSupport 按「本单据类型 / 本业务域」逐段裁剪，与
 * BillChainTimeline 同源。无此环节的段不再渲染折叠面板（档案卷没有「生成凭证/付款批次」段）；
 * 有环节但暂无数据时照常渲染空态，让「还没做」可被看见。
 * 旧包只发 PAYMENT 一档 ⇒ 回落 paymentSupported；缺省视为有环节。
 */
function segOk(k) {
  const s = chain.value?.summary || {}
  const seg = s.segmentSupport
  if (seg && seg[k] !== undefined) return seg[k] !== false
  if (k === 'PAYMENT' || k === 'BATCH') {
    if (s.paymentSupported !== undefined) return s.paymentSupported !== false
  }
  return true
}
/** 判为不适用但确实取到了数据 ⇒ 照常显示（不让判据偏差把真实数据藏掉） */
function showSeg(k, data) {
  return segOk(k) || (Array.isArray(data) && data.length > 0)
}
const notSupportReason = computed(
  () => chain.value?.summary?.reason || '未声明单据域且该主键在费用单据表内不存在；请为该页面的追溯入口补传 bizType。'
)

// 方案 A：业务链时间线节点 → 下方联查明细折叠段 映射（点击时间线节点即展开真实下游数据）
const SEG2SECTION = { VOUCHER: 'vouchers', PAYMENT: 'payments', BATCH: 'batches', ARCHIVE: 'archives', APPROVED: 'traces', BILL: null }
function openSeg(key) {
  const sec = SEG2SECTION[key]
  if (sec && !openSections.value.includes(sec)) openSections.value.push(sec)
}
// 补 UNARCHIVED（exp_bill.archive_status 的实际取值），字典外不再回显英文
const ARC_ST = { PENDING: '待归档', ARCHIVING: '归档中', ARCHIVED: '已归档', DISPOSED: '已处置', UNARCHIVED: '未归档', DESTROYED: '已销毁' }
/** summary.scope 是「流程域」而非单据类型（只有 EXPENSE／NOT_SUPPORTED 两种），
 *  走 BIZ_TYPE 会显示成「费用组」，语义不对，故单独定名。 */
const SCOPE_LABEL = { EXPENSE: '费用报销', NOT_SUPPORTED: '未接入' }
function scopeLabel(s) { return SCOPE_LABEL[s] || docTypeLabel(s) || '' }
/** 他域单据头部取的是流程实例状态，不在费用单据状态名称表里 */
const WF_ST = { RUNNING: '在办', APPROVED: '已审批', COMPLETED: '已完成', REJECTED: '已驳回', CANCELLED: '已撤销', TERMINATED: '已终止' }
function headStatusLabel(s) {
  if (!s) return ''
  return isExpense.value ? statusLabel(s) : (WF_ST[s] || statusLabel(s))
}
function payTag(s) { return s === 'PAID' ? 'success' : (s === 'FAILED' ? 'danger' : 'warning') }
/** 分录方向标色：借＝danger、贷＝primary；名称表外的值走中性 info，不再默认成「贷」 */
function dirTag(d) { return d === 'DEBIT' ? 'danger' : (d === 'CREDIT' ? 'primary' : 'info') }

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
function shortTime(t) {
  if (!t) return ''
  try { return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) } catch { return t }
}
/** wf_task_history.duration 是毫秒（TRC-A 起回填），空＝历史行未记录时长 */
function formatMs(ms) {
  const min = Math.round(Number(ms || 0) / 60000)
  if (!min) return '不足 1 分钟'
  return min < 60 ? min + ' 分钟' : (min / 60).toFixed(1) + ' 小时'
}

watch(() => [props.modelValue, props.billId, props.bizType], async ([v, bid]) => {
  if (!v || !bid) return
  loading.value = true
  billInfo.value = null
  chain.value = null
  openSections.value = []
  // 【单一数据源】联查接口自身就返回单据头部 + 审批轨迹（wf_task_history），
  // 不再另调 /wf/query/history/page（需 wf:history:list、且只传 bizId 会撞他域同 id 的单），
  // 也不依赖 /expense/bill/detail（有 requireMine 守卫，非申请人查看必 4030 → 头部与
  // 时间线前两格数据源打空，视觉上就成了「跳过一个环节」）。
  try {
    const c = await billApi.traceChain(bid, props.bizType ? { bizType: props.bizType } : undefined, { silent: true })
    chain.value = c || null
    if (c?.bill) billInfo.value = c.bill
    // 自动展开有数据的段
    openSections.value = ['payments', 'batches', 'vouchers', 'archives', 'traces'].filter((k) => {
      const data = c?.[k]
      return Array.isArray(data) && data.length > 0
    })
  } catch (e) {
    // 声明了单据域之后，被归属校验拒（403）不再等于「什么都没发生」：
    // 把后端原话落到 NOT_SUPPORTED 空态上，否则抽屉只剩一片空白，看着像坏了。
    chain.value = { summary: { scope: 'NOT_SUPPORTED', reason: (e && e.message) || '联查失败' } }
  }
  // 兜底：只有「后端确认这是费用域、且链路响应里确实没带头部」才回退费用单详情接口。
  // /他域主键（抄送记录 id、方案 id…）与 exp_bill.id 不是同一坐标系，
  // 关联点未命中（NOT_SUPPORTED）或已按他域解析时再拿这个数字去查详情，就会把别人的报销单头贴进来。
  const scope = chain.value?.summary?.scope
  if (!billInfo.value && scope === 'EXPENSE') {
    try {
      const detail = await billApi.detail(bid, { silent: true })
      billInfo.value = detail?.bill || null
    } catch { /* 非本人单据/非报销类单据查不到详情很正常，忽略不提示 */ }
  }
  loading.value = false
})
</script>

<style scoped>
.trace-content { padding: 0 4px; }
.trace-header { margin-bottom: 12px; }
.trace-bill-no { font-size: 18px; font-weight: 700; margin-bottom: 6px; }
.trace-bill-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.trace-type { font-size: 13px; color: #909399; }
.trace-amount { font-size: 13px; font-weight: 600; color: var(--app-text, #303133); }

/* 链路完整度条 */
.chain-strip { display: flex; align-items: center; gap: 6px; margin: 10px 0 14px; padding: 8px 12px; background: var(--app-bg, #f5f7fa); border-radius: 6px; }
.chain-seg { text-align: center; padding: 4px 10px; border-radius: 4px; background: var(--app-card-bg, #fff); border: 1px solid var(--app-border, #e4e7ed); opacity: 0.55; }
.chain-seg.reached { opacity: 1; border-color: #67c23a; background: rgba(103, 194, 58, .16); }
.seg-name { font-size: 12px; color: var(--app-text-secondary, #606266); }
.seg-count { font-size: 14px; font-weight: 700; color: var(--app-text, #303133); }
.chain-arrow { color: #c0c4cc; font-weight: 700; }

.chain-collapse { margin-bottom: 16px; }
.chain-collapse :deep(.el-collapse-item__header) { font-size: 13px; }

.voucher-card { border: 1px solid var(--app-border, #e4e7ed); border-radius: 6px; padding: 8px 10px; margin-bottom: 10px; }
.voucher-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 4px; }
.voucher-no { font-weight: 700; color: var(--app-text, #303133); }
.voucher-meta { font-size: 12px; color: #909399; }
.voucher-summary { font-size: 12px; color: var(--app-text-secondary, #606266); margin-bottom: 6px; }

.trace-action { font-weight: 600; margin-right: 8px; }
.trace-trans { font-size: 12px; color: #909399; margin-right: 8px; }
.trace-oper { font-size: 12px; color: #409eff; }
.trace-remark { font-size: 12px; color: var(--app-text-secondary, #606266); margin-top: 2px; }
</style>
