<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="nab" v-if="action" :class="'tone-' + action.tone">
    <span class="nab-dot"></span>
    <div class="nab-body">
      <div class="nab-step">下一跳 · {{ action.title }}</div>
      <div class="nab-desc">{{ action.desc }}</div>
    </div>
    <el-button v-if="action.route" type="primary" size="small" @click="go(action.route)">{{ action.btn }}</el-button>
    <el-tag v-else type="success" effect="plain" size="small">已闭环</el-tag>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

/**
 * 下一跳行动条：根据单据状态 + 业务链缺口，计算流程当前「该去哪一步」，
 * 一键跳到下游真实页面。纯前端、零后端依赖，复用 billInfo.status 与 traceChain.summary。
 * 对齐 SAP/用友/金蝶 的「流程下一站」引导思想——让用户始终知道流程如何往下走。
 */
const props = defineProps({
  billInfo: { type: Object, default: null },
  chain: { type: Object, default: null },
})
const router = useRouter()
const status = computed(() => props.billInfo?.status || '')
const reached = computed(() => props.chain?.summary?.reached || [])

/** D-DEC-362：本域无出证/付款环节的单据不再引导「去凭证台账」「去出纳付款」——
 *  判据与 BillChainTimeline／D-DEC-446 甲-2 同源：优先 summary.segmentSupport，旧包回落 paymentSupported，缺省视为有腿。 */
const segOk = (k) => {
  const s = props.chain?.summary
  const seg = s && s.segmentSupport
  if (seg && seg[k] !== undefined) return seg[k] !== false
  if (k === 'PAYMENT' && s && s.paymentSupported !== undefined) return s.paymentSupported !== false
  return true
}

/**
 * D-DEC-369：「付款已完成」这句话此前在归档引导里是无条件写的，而同抽屉的付款记录段可能正显示
 * 「待付款」（exp_bill_payment.pay_status=WAITING、pay_time NULL、paid_amount=0）。
 * 判据一律复用后端同一份读数，前端只负责把话说实，不另算一套：
 *  · 是否有付款环节 ＝ summary.segmentSupport.PAYMENT（沿用上方 segOk，与 D-DEC-446 甲-2 同源）；
 *  · 是否付讫 ＝ summary.paidPaymentCount（D-DEC-446 乙-2 的终态计数，只算 pay_status='PAID'）
 *    对比 summary.paymentCount（记录存在数）。
 * 旧包没有 paidPaymentCount 时判据不可知 ⇒ 维持原措辞（按 reached 段走），不臆断"未付讫"。
 */
const payFact = computed(() => {
  const s = props.chain?.summary || {}
  if (!segOk('PAYMENT')) return { kind: 'none' }
  const total = Number(s.paymentCount ?? 0)
  const paidRaw = s.paidPaymentCount
  if (paidRaw === undefined || paidRaw === null) return { kind: 'unknown', total }
  const paid = Number(paidRaw)
  if (total > 0 && paid === 0) return { kind: 'unpaid', paid, total }
  if (total > 0 && paid < total) return { kind: 'partial', paid, total }
  return { kind: 'paid', paid, total }
})

const action = computed(() => {
  const s = status.value
  const has = (k) => reached.value.includes(k)
  if (!s) return null
  if (s === 'DRAFT' || s === 'SUBMITTED')
    return { title: '提交审批', desc: '单据尚在草稿，提交后进入共享审批流', route: '/approval', btn: '去审批工作台', tone: 'warn', done: false }
  if (s === 'APPROVING')
    return { title: '等待审批', desc: '审批流进行中，审批通过后系统自动生成凭证', route: '/approval', btn: '查看审批', tone: 'warn', done: false }
  if (s === 'REJECTED')
    return { title: '已驳回', desc: '审批被驳回，请修改后重新提交', route: '/requisition/mine', btn: '去我的单据', tone: 'danger', done: false }
  if (s === 'VOID' || s === 'TERMINATED' || s === 'CLOSED')
    return { title: '单据已终结', desc: `当前状态：${s}，流程不再往下走`, tone: 'info', done: true }
  // 正常推进链：APPROVED / PAID / POSTED
  if (segOk('VOUCHER') && !has('VOUCHER'))
    return { title: '生成凭证', desc: '审批已通过，去总账查看/补录凭证', route: '/op/gl/voucher', btn: '去凭证台账', tone: 'primary', done: false }
  if (segOk('PAYMENT') && !has('PAYMENT'))
    return { title: '执行付款', desc: '凭证已生成，去出纳付款', route: '/pay', btn: '去出纳付款', tone: 'primary', done: false }
  // D-DEC-369：到了归档这一句，付款段的实况必须跟着 summary 的终态计数说，不能再一律说"付款已完成"
  if (!has('ARCHIVE')) {
    const pf = payFact.value
    if (pf.kind === 'unpaid')
      return { title: '执行付款', desc: `付款记录 ${pf.total} 笔尚未付讫（待付款），先去出纳付款/组批再归档`, route: '/pay', btn: '去出纳付款', tone: 'warn', done: false }
    if (pf.kind === 'partial')
      return { title: '执行付款', desc: `付款已付讫 ${pf.paid}/${pf.total} 笔，其余仍未到账`, route: '/pay', btn: '去出纳付款', tone: 'warn', done: false }
    if (pf.kind === 'none')
      return { title: '归档', desc: '本单据类型没有付款环节，去归档收口', route: '/archive/archive', btn: '去归档', tone: 'primary', done: false }
    return { title: '归档', desc: '付款已完成，去归档收口', route: '/archive/archive', btn: '去归档', tone: 'primary', done: false }
  }
  return { title: '全链路已完成', desc: payFact.value.kind === 'none' ? '凭证 · 归档均已收口（本域无付款环节）' : '凭证 · 付款 · 归档均已收口', tone: 'success', done: true }
})

function go(r) { router.push(r) }
</script>

<style scoped>
.nab {
  display: flex; align-items: center; gap: 10px;
  margin: 10px 0; padding: 10px 14px; border-radius: 8px;
  background: #ecf5ff; border: 1px solid #d9ecff;
}
.nab-dot { width: 10px; height: 10px; border-radius: 50%; background: #409eff; flex: 0 0 auto; }
.nab-body { flex: 1; min-width: 0; }
.nab-step { font-size: 13px; font-weight: 700; color: #303133; }
.nab-desc { font-size: 12px; color: #606266; margin-top: 2px; }
.nab.tone-warn { background: #fdf6ec; border-color: #faecd8; } .nab.tone-warn .nab-dot { background: #e6a23c; }
.nab.tone-danger { background: #fef0f0; border-color: #fde2e2; } .nab.tone-danger .nab-dot { background: #f56c6c; }
.nab.tone-info { background: #f4f4f5; border-color: #e9e9eb; } .nab.tone-info .nab-dot { background: #909399; }
.nab.tone-success { background: #f0f9eb; border-color: #e1f3d8; } .nab.tone-success .nab-dot { background: #67c23a; }
</style>
