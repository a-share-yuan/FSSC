<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="bct" v-if="stages.length">
    <div class="bct-title">业务链时间线</div>
    <div class="bct-track">
      <template v-for="(s, i) in stages" :key="s.key">
        <div class="bct-node" :class="[s.state, s.key]" @click="onClick(s)">
          <div class="bct-dot">{{ s.state === 'reached' ? '✓' : (s.state === 'blocked' ? '✕' : (i + 1)) }}</div>
          <div class="bct-label">{{ s.label }}</div>
          <div class="bct-sub">{{ s.state === 'reached' ? (s.detail || '已发生') : (s.hint || '待进行') }}</div>
        </div>
        <div class="bct-line" v-if="i < stages.length - 1" :class="{ on: s.state === 'reached' }"></div>
      </template>
    </div>
    <div class="bct-foot">点击节点可展开下方对应联查明细</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
// 提单节点 detail 原是后端 summary.billStatus 原码直出（非费用域显示 APPROVED）
import { statusLabel } from '@/constants/billStatus'

/**
 * 业务链时间线：把后端 BillTraceChainController 已发生的单据链路渲染成可点击时间线。
 *
 * 【单一数据源】各环节的 reached 一律取自后端 chain.summary.reached，前端不再自行拼装；
 * 格子数随单据类型而变（甲-2：summary.segmentSupport 标记本域无付款/组批环节的单据不再画该格）。
 * 历史缺陷：节点 1/2 靠另一个接口（/expense/bill/detail，被「仅限本人单据」守卫拦截，
 * 非申请人必 4030 → billInfo 为空 → status 缺失），节点 3-6 靠 traceChain；
 * 两条规则一旦前一个断供，就出现「提单灰 / 审批通过灰 / 生成凭证绿」的跳环节错觉，
 * 且 /expense/bill-trace-chain 把任意业务表的自增主键当 exp_bill 主键，
 * 还会把同 id 的其它域单据（如 GL 手工凭证）撞进本单链路（实证见 docs/62）。
 * 现在后端统一裁决并补齐 APPROVED 段，本组件只做展示。
 */
const props = defineProps({
  // BillTraceChainController 返回体；summary.reached 标记已达段，*Count 给出数量
  chain: { type: Object, default: null },
  // 单据状态（兼容字段，仅用于文案补充，不再决定环节是否已达）
  status: { type: String, default: '' },
  // 段 → 路由 映射；提供则点击跳转（独立使用时）；抽屉内不传，仅展开明细
  navMap: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['select'])
const router = useRouter()

const summary = computed(() => props.chain?.summary || {})
const reached = computed(() => summary.value.reached || [])
// 后端未接入（非费用单据）时 reached 为空数组，直接不渲染，避免出现半真半假的时间线
const supported = computed(() => summary.value.scope !== 'NOT_SUPPORTED')

/** 已驳回/已撤回/已作废：环节明确未达成，标 blocked 而非 pending */
const STATUS_BLOCKED = ['REJECTED', 'WITHDRAWN', 'VOID', 'TERMINATED', 'CANCELLED']
const BLOCK_LABEL = { REJECTED: '已驳回', WITHDRAWN: '已撤回', VOID: '已作废', TERMINATED: '已终止', CANCELLED: '已取消' }

/** 各段「存在计数」——用于副标题的分母，不代表已达 */
const EXIST_KEY = { VOUCHER: 'voucherCount', PAYMENT: 'paymentCount', BATCH: 'batchCount', ARCHIVE: 'archiveCount' }
/** 乙：各段「最终状态计数」（凭证 POSTED／付款 PAID／批次已出账／档案 ARCHIVED）——已达判据 */
const TERM_KEY = { VOUCHER: 'postedVoucherCount', PAYMENT: 'paidPaymentCount', BATCH: 'settledBatchCount', ARCHIVE: 'archivedCount' }
const UNIT = { VOUCHER: '张', PAYMENT: '笔', BATCH: '批', ARCHIVE: '卷' }

function num(k) {
  const v = summary.value[k]
  return typeof v === 'number' ? v : null
}
/** 旧 jar 不发最终状态计数：回落存在计数，宁可沿用旧规则也不能显示成「0 张」 */
function termCount(key) {
  const t = num(TERM_KEY[key])
  return t === null ? (num(EXIST_KEY[key]) || 0) : t
}
function existCount(key) {
  return num(EXIST_KEY[key]) || 0
}

/** 甲：付款/组批段对本单据类型不适用时整段不画（后端 segmentSupport，旧包回落 paymentSupported） */
const paySegment = computed(() => {
  const seg = summary.value.segmentSupport
  if (seg && seg.PAYMENT !== undefined) return seg.PAYMENT !== false
  if (summary.value.paymentSupported !== undefined) return summary.value.paymentSupported !== false
  return true
})

/**
 * 前端侧：审批段未达时，说清「主流程真实卡点」与「派单链已办结」是两条并存的事实。
 * 改前这里只有一句「待审批」，而后端摘要一度关联的是 id 最大＝最后完成的那枚实例（常是三单匹配派单链），
 * 于是 12-18:74 出现「匹配链批完 → 这单显示已批，主流程还停在扫描岗」。后端已改关联主流程，
 * 本组件把主流程卡点显出来（默认处理档不谎报具体节点），派单链完成数单列。
 * 只加文案：reached / segmentSupport / 最终状态计数（甲+乙）规则一律不动。
 */
// 派单/子流程实例的「已办结」＝与后端 BillTraceChainController.INST_APPROVED 同一规则的两枚末态
const SUB_DONE = ['APPROVED', 'COMPLETED']
const mainGate = computed(() => {
  const s = summary.value
  // 前三档落空、退回"最新一轮"时 currentNode 未必属于主流程 ⇒ 只报"待确认"，不报具体节点
  if (s.mainInstUncertain === true) return '主流程卡点待确认'
  if (!s.currentNode) return ''
  return `主流程在办·${s.currentNode}`
})
const subChainDoneCount = computed(() => {
  const list = summary.value.subInstances
  if (!Array.isArray(list)) return 0
  return list.filter((m) => m && m.subChain === true && SUB_DONE.includes(String(m.status))).length
})
const approvalPendingHint = computed(() => {
  const parts = []
  if (mainGate.value) parts.push(mainGate.value)
  if (subChainDoneCount.value > 0) parts.push(`派单链 ${subChainDoneCount.value} 条已办结`)
  return parts.length ? parts.join('｜') : '待审批'
})

const stages = computed(() => {
  const billStatus = summary.value.billStatus || props.status || ''
  if (!supported.value) return []
  const has = (k) => reached.value.includes(k)
  const blocked = STATUS_BLOCKED.includes(billStatus)

  const all = [
    { key: 'BILL', label: '提单',
      state: has('BILL') ? 'reached' : 'pending',
      detail: statusLabel(billStatus) || '已提交', hint: '录入中' },
    { key: 'APPROVED', label: '审批通过',
      state: has('APPROVED') ? 'reached' : (blocked ? 'blocked' : 'pending'),
      detail: has('APPROVED') ? '已完成' : '',
      hint: blocked ? (BLOCK_LABEL[billStatus] || '未通过') : approvalPendingHint.value },
    { key: 'VOUCHER', label: '生成凭证', hint: '审批后自动生成' },
    { key: 'PAYMENT', label: '付款', hint: '出纳执行' },
    { key: 'BATCH', label: '组批', hint: '付款组批' },
    { key: 'ARCHIVE', label: '归档', hint: '待归档／归档审批中' },
  ]

  return all
    .filter((s) => !['PAYMENT', 'BATCH'].includes(s.key) || paySegment.value)
    .map((s) => {
      if (!['VOUCHER', 'PAYMENT', 'BATCH', 'ARCHIVE'].includes(s.key)) return s
      const t = termCount(s.key)
      const e = existCount(s.key)
      const u = UNIT[s.key]
      return {
        ...s,
        state: has(s.key) ? 'reached' : 'pending',
        // 已达＝最终状态计数；未全部最终状态时把差额显式讲清楚，不含糊
        detail: t > 0 ? (t < e ? `${t}/${e} ${u}` : `${t} ${u}`) : '',
        // 有对象但一个都没到最终状态：不能说「待进行」，要说清卡在哪儿
        hint: t > 0 ? '' : (e > 0 ? `已生成 ${e} ${u}，未办结` : s.hint),
      }
    })
})

function onClick(s) {
  emit('select', s.key)
  const route = props.navMap[s.key]
  if (route) router.push(route)
}
</script>

<style scoped>
.bct { margin: 10px 0 14px; padding: 12px; background: #f5f7fa; border-radius: 8px; }
.bct-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 10px; }
.bct-track { display: flex; align-items: flex-start; }
.bct-node { flex: 1; text-align: center; cursor: pointer; user-select: none; }
.bct-dot {
  width: 26px; height: 26px; line-height: 26px; border-radius: 50%;
  margin: 0 auto 4px; font-size: 13px; font-weight: 700;
  background: #fff; border: 2px solid #dcdfe6; color: #909399;
}
.bct-node.reached .bct-dot { background: #67c23a; border-color: #67c23a; color: #fff; }
.bct-node.blocked .bct-dot { background: #fef0f0; border-color: #f56c6c; color: #f56c6c; }
.bct-node.pending .bct-dot { background: #fff; }
.bct-label { font-size: 12px; color: #606266; font-weight: 600; }
.bct-node.reached .bct-label { color: #303133; }
.bct-node.blocked .bct-label { color: #f56c6c; }
.bct-sub { font-size: 11px; color: #c0c4cc; margin-top: 2px; }
.bct-node.reached .bct-sub { color: #909399; }
.bct-node.blocked .bct-sub { color: #f56c6c; }
.bct-line { flex: 0 0 14px; height: 2px; background: #dcdfe6; margin-top: 13px; }
.bct-line.on { background: #67c23a; }
.bct-foot { font-size: 11px; color: #c0c4cc; margin-top: 8px; text-align: center; }
</style>
