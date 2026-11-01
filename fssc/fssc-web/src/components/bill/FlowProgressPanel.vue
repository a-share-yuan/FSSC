<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<!--
  流程进度卡片：单据页顶部常驻，回答两件事——
  ① 这张单现在停在流程的哪一节、是否还在办；② 该节谁在办、已停留多久。
  逐节谁在什么时间签了什么意见收在「展开审批链」一个模态框里（上半时间轴、下半逐节明细表）：
  审批链环节多时（对公结算有十几节）不再把下方的单据操作顶出首屏——单据操作是页面主体，审批链是旁证。
  数据源：单据本身（status / procInstId / voucherNo）+ GET /tenant/wf/query/chain（节点配置 + 审批轨迹 + 在办任务三源合一）。
  chain 不挂配置权限，制单岗拿得到，因此不再依赖 wf:history:list 的审批轨迹接口。
-->
<template>
  <div class="fp">
    <div class="fp-head">
      <div class="fp-id">
        <template v-if="bill && bill.id">
          <span class="fp-no">{{ bill.billNo || ('单据 #' + bill.id) }}</span>
          <el-tag :type="statusType(bill.status)" size="small">{{ statusLabel(bill.status) }}</el-tag>
          <span class="fp-title" :title="bill.title">{{ bill.title }}</span>
          <el-tag v-if="bill.voucherNo" size="small" type="success">凭证 {{ bill.voucherNo }}</el-tag>
        </template>
        <span v-else class="fp-none">未选定单据：在「我的单据」点一行，或在下方填单——本卡片跟着当前这张单走。</span>
      </div>
      <div class="fp-ops">
        <span v-if="updatedAt" class="fp-updated">进度更新于 {{ updatedAt }}</span>
        <el-button size="small" :loading="loading" @click="$emit('refresh')">刷新进度</el-button>
      </div>
    </div>

    <!-- 最醒目的一格：当前停在哪一节、谁在办 -->
    <div class="fp-cur" :class="'fp-cur-' + tone">
      <div class="fp-cur-label">{{ curLabel }}</div>
      <div class="fp-cur-node">{{ curNodeText }}</div>
      <div class="fp-cur-meta">{{ curMeta }}</div>
    </div>

    <!-- 甲-1：status 驱动的生命周期步进条已删除。
         它与下方审批链（FlowChain，取 /wf/query/chain 的逐节证据）在同一卡片里并存、规则不同；
         后半截（记账/付款/组批/归档）取的列不由 exp_bill.status 承载 ⇒ 点了也不会绿。
         「走到哪一步」由 fp-cur 一格 + 审批链格子承担。 -->

    <div v-if="nodes2.length" class="fp-chain">
      <div class="fp-chain-head">
        <span class="fp-chain-title">{{ chainSummary }}</span>
        <div class="fp-chain-btns">
          <!-- 审批链默认收起、改由模态框查看：环节多时纵向时间轴会把下面的
               单据操作顶出首屏，而这一页的主体是单据操作，不是审批链。
               原「展开审批链」「展开流程明细」两枚按钮/两个框合并成一框——两者读的都是
               同一份 /wf/query/chain 行集，只是排法不同（时间轴 vs 表格），并排放等于把一条链摆两次。 -->
          <el-button size="small" @click="chainVis = true">展开审批链</el-button>
        </div>
      </div>
    </div>
    <div v-else-if="bill && bill.id" class="fp-noflow">
      <!-- DRAFT 判定走脚本里的 status 计算属性（bill.id 在而 status 字段缺失＝刚保存的草稿，
           缺省即 DRAFT），不再直读 bill.status——否则上方「尚未提交」与下方「未取到审批链数据」同屏自相矛盾。 -->
      {{ status === 'DRAFT' ? '草稿尚未提交，暂无审批链；提交后此处显示每一节的进度与办理人。' : '未取到审批链数据（流程实例缺失或接口异常），可点右上「刷新进度」重试。' }}
    </div>

    <el-dialog v-model="chainVis" :title="dialogTitle('审批链')" width="min(900px, 94vw)" append-to-body>
      <div class="fp-dialog-head">{{ chainSummary }}</div>
      <div class="fp-dialog-scroll">
        <FlowChain :nodes="nodes2" mode="timeline" />
      </div>
      <div class="fp-dialog-sub">逐节明细（含动作与意见，可整行选中抄录）</div>
      <FlowChain :nodes="nodes2" mode="table" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import FlowChain from '../FlowChain.vue'
import { statusLabel, statusType } from '../../constants/billStatus'

const props = defineProps({
  // 当前聚焦的单据：{ id, billNo, title, status, procInstId, voucherNo, currentNode, flowName }
  bill: { type: Object, default: null },
  // GET /wf/query/chain 原样返回：[{ nodeKey, nodeName, state, action, handler, time, comment }]
  nodes: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  updatedAt: { type: String, default: '' },
})
const emit = defineEmits(['refresh'])

const chainVis = ref(false)
// 换单时收起模态框，免得停留在上一张单的链上（卡片本身跨页签常驻，换单不重建）
watch(() => props.bill?.id, () => {
  chainVis.value = false
})
/**
 * 提交成功后卡片可能比流程实例慢一拍——toast 已说「已提交审批：BX…」，
 * 这里却报「未取到审批链数据」，屏幕上两条互相打脸。
 * 有 procInstId、非加载中、链 yet 空 ⇒ 自己重试两轮（1.2s 间隔）再让人工【刷新进度】兜底；
 * 换单或取到数据即清零，不会无限轮询。
 */
let retryTimer = null
let retryCount = 0
watch(() => [props.bill?.procInstId, (props.nodes || []).length, props.loading],
  ([pid, n, ld]) => {
    if (retryTimer && (n > 0 || !pid)) { clearTimeout(retryTimer); retryTimer = null }
    if (n > 0) { retryCount = 0; return }
    if (!pid || ld || retryCount >= 2) return
    retryCount += 1
    retryTimer = setTimeout(() => { retryTimer = null; emit('refresh') }, 1200)
  })
watch(() => props.bill?.id, () => { retryCount = 0 })
const dialogTitle = (base) => (props.bill?.billNo ? `${base} · ${props.bill.billNo}` : base)

// 负面末态集与 SRV BillTraceChainController.STATUS_NEGATIVE 同一规则（撤回/取消原缺，卡片头误落成笼统的档「审批链已办结」）
const ABNORMAL = ['REJECTED', 'WITHDRAWN', 'VOID', 'TERMINATED', 'CANCELLED', 'CLOSED']

// 提单节在 wf_node_config 的 sort 里排在末位，链上却是最先发生的一步：显示时提到首位
function isSubmitNode(n) {
  return n.nodeKey === 'SUBMIT' || n.nodeName === '提单'
}
const nodes2 = computed(() => {
  const arr = props.nodes || []
  return [...arr.filter(isSubmitNode), ...arr.filter((n) => !isSubmitNode(n))]
})
/**
 * /110/147：分母＝本单实际会经过的节（后端 counted/seqTotal），不再是 wf_node_config 的行数。
 * counted=false 的两类：分支未走（SKIPPED）与加签插道（addSignOnly）；老实例没有 counted 字段时
 * 回落成"全部计入"，与原先逐字一致（后端新字段是纯增量，新旧包共存期不报错也不掉节）。
 */
const isCounted = (n) => n.counted !== false
const stepNodes = computed(() => nodes2.value.filter(isCounted))
/** 分母优先取后端 seqTotal（规则唯一真相源）；缺字段（旧包）才按现地 counted 计数 */
const stepTotal = computed(() => {
  const hit = (props.nodes || []).find((n) => typeof n.seqTotal === 'number')
  return hit ? hit.seqTotal : stepNodes.value.length
})
const current = computed(() => nodes2.value.find((n) => n.state === 'CURRENT') || null)
// 已驳回的单没有在办任务，链上取最后一次驳回记录作为「停在哪一节」
const lastRejected = computed(() => {
  const r = nodes2.value.filter((n) => n.state === 'REJECTED')
  return r.length ? r[r.length - 1] : null
})
const reachedCount = computed(() => stepNodes.value.filter((n) => n.state !== 'TODO').length)
const currentIndex = computed(() => {
  const n = current.value || lastRejected.value
  return n ? stepNodes.value.indexOf(n) + 1 : 0
})
// 刚保存的草稿行未必带 status 字段，缺省按草稿处理，不能当成「无单据」
const status = computed(() => props.bill?.status || (props.bill?.id ? 'DRAFT' : ''))
const tone = computed(() => {
  if (!props.bill?.id) return 'idle'
  if (status.value === 'DRAFT') return 'draft'
  if (ABNORMAL.includes(status.value)) return 'bad'
  if (current.value || status.value === 'APPROVING' || status.value === 'SUBMITTED') return 'run'
  return 'done'
})

const curLabel = computed(() => {
  if (!props.bill?.id) return '尚未载入单据'
  if (status.value === 'DRAFT') return '尚未提交'
  if (current.value) return `当前审批节点（第 ${currentIndex.value} 节）`
  if (lastRejected.value) return `已驳回，停在第 ${currentIndex.value} 节`
  if (ABNORMAL.includes(status.value)) return statusLabel(status.value)
  // 审批中却拿不到在办节点（链未取到/镜像未同步）时不能说「已办结」
  if (status.value === 'APPROVING' || status.value === 'SUBMITTED') return '审批流转中'
  return '审批链已办结'
})
const curNodeText = computed(() => {
  if (!props.bill?.id) return '—'
  if (status.value === 'DRAFT') return '草稿（未进入流程）'
  const n = current.value || lastRejected.value
  if (n) return n.nodeName
  return props.bill.currentNode || (status.value ? statusLabel(status.value) : '—')
})
const curMeta = computed(() => {
  const n = current.value || lastRejected.value
  if (!n) return props.bill?.flowName ? `流程：${props.bill.flowName}` : ''
  const who = n.handler ? `办理人 ${n.handler}` : (n.state === 'CURRENT' ? '任务池中，等待认领' : '')
  const t = fmtTime(n.time)
  const dwell = n.state === 'CURRENT' ? humanizeDwell(n.time) : ''
  return [who, t ? `${n.state === 'CURRENT' ? '到达' : '办理'} ${t}` : '', dwell ? `已停留 ${dwell}` : '']
    .filter(Boolean).join(' · ')
})
/**
 * /110：分母从「配置 N 节」改成「本单实际 N 节」。
 * 配置节数与加签道数仍如实报出来，免得只看数字的人以为系统漏了几节——
 * 条件分支没命中的节与加签插的道都不属于本单的必经路线，不进分母。
 */
const chainSummary = computed(() => {
  const all = nodes2.value.length
  const addSigns = nodes2.value.filter((n) => n.addSignCount).length
  const cfgPart = all === stepTotal.value ? '' : `（配置 ${all} 节，含未走分支${addSigns ? '、加签 ' + addSigns + ' 节次' : ''}）`
  return `审批链：已发生 ${reachedCount.value} 节 / 本单实际 ${stepTotal.value} 节${cfgPart}`
})

function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}
function humanizeDwell(t) {
  if (!t) return ''
  const start = new Date(String(t).replace('T', ' ').replace(/-/g, '/'))
  if (Number.isNaN(start.getTime())) return ''
  const mins = Math.floor((Date.now() - start.getTime()) / 60000)
  if (mins < 1) return '刚刚到达'
  if (mins < 60) return `${mins} 分钟`
  const h = Math.floor(mins / 60)
  if (h < 24) return `${h} 小时 ${mins % 60} 分`
  return `${Math.floor(h / 24)} 天 ${h % 24} 小时`
}
</script>

<style scoped>
.fp { border: 1px solid var(--app-border, #ebeef5); border-radius: 6px; background: var(--app-card-bg, #fff); padding: 10px 12px; margin-bottom: 14px; box-shadow: 0 1px 4px rgba(0, 21, 41, .04); }
.fp-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-bottom: 8px; }
.fp-id { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; flex: 1; min-width: 0; }
.fp-no { font-weight: 600; color: var(--app-text, #303133); }
.fp-title { color: var(--app-text-secondary, #606266); font-size: 13px; max-width: 46vw; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fp-none { color: var(--app-text-secondary, #909399); font-size: 13px; }
.fp-ops { display: flex; align-items: center; gap: 10px; }
.fp-updated { color: var(--app-text-secondary, #909399); font-size: 12px; }
.fp-cur { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 12px; padding: 8px 12px; border-radius: 4px; margin-bottom: 10px; background: var(--app-bg, #f5f7fa); }
.fp-cur-label { font-size: 12px; color: var(--app-text-secondary, #606266); }
.fp-cur-node { font-size: 17px; font-weight: 600; color: var(--app-text, #303133); }
.fp-cur-meta { font-size: 12px; color: var(--app-text-secondary, #606266); }
.fp-cur-run { background: var(--app-hover, #ecf5ff); }
.fp-cur-run .fp-cur-node { color: #409eff; }
.fp-cur-bad { background: rgba(245, 108, 108, .14); }
.fp-cur-bad .fp-cur-node { color: #f56c6c; }
.fp-cur-done { background: rgba(103, 194, 58, .14); }
.fp-cur-done .fp-cur-node { color: #67c23a; }
.fp-cur-draft .fp-cur-node { color: var(--app-text-secondary, #909399); }
.fp-steps { margin-bottom: 10px; }
.fp-chain { border-top: 1px dashed var(--app-border, #ebeef5); padding-top: 8px; }
.fp-chain-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 6px 12px; }
.fp-chain-title { font-size: 12px; color: var(--app-text, #303133); font-weight: 600; }
.fp-chain-btns { display: flex; gap: 8px; }
.fp-dialog-head { font-size: 12px; color: var(--app-text-secondary, #606266); margin-bottom: 10px; }
/* 时间轴与明细表同框：时间轴限高 38vh 内滚，下面接表格（表格自身 max-height 由 FlowChain 控制），
   整框不会顶出视口——原来两框各占 64vh 是因为一框只装一种排法。 */
.fp-dialog-scroll { max-height: 38vh; overflow: auto; padding-right: 4px; }
.fp-dialog-sub { margin: 12px 0 6px; font-size: 12px; color: var(--app-text-secondary, #909399); }
.fp-noflow { font-size: 12px; color: var(--app-text-secondary, #909399); border-top: 1px dashed var(--app-border, #ebeef5); padding-top: 8px; }
</style>
