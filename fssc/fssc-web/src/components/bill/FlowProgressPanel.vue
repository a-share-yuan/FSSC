<!--
  流程进度卡片：单据页顶部常驻，回答三件事——
  ① 这张单现在停在流程的哪一节、是否还在办；② 该节谁在办、已停留多久；③ 每一节谁在什么时间签了什么意见。
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

    <!-- D-DEC-446 甲-1：status 驱动的生命周期步进条已删除。
         它与下方审批链（FlowChain，取 /wf/query/chain 的逐节证据）在同一卡片里并存、口径不同；
         后半截（记账/付款/组批/归档）取的列不由 exp_bill.status 承载 ⇒ 点了也不会绿。
         「走到哪一步」由 fp-cur 一格 + 审批链格子承担。 -->

    <div v-if="nodes2.length" class="fp-chain">
      <div class="fp-chain-head">
        <span class="fp-chain-title">{{ chainSummary }}</span>
        <el-button link size="small" @click="showDetail = !showDetail">
          {{ showDetail ? '收起流程明细' : '展开流程明细（每节办理人/时间/意见）' }}
        </el-button>
      </div>
      <!-- D-UI-03（2026-10-18）：横排平铺改纵向时间轴。
           原实现在填单侧同样是把 7 个节点标签平铺换行，无连接线、各排参差，
           「走到哪一节」只能靠上面的 fp-cur 那一格文字。本行改时间轴后，
           节点再多也只是变长，且每节的序号/状态/办理人/时间/意见逐行对齐。 -->
      <FlowChain :nodes="nodes2" mode="timeline" />
      <el-table v-if="showDetail" :data="nodes2" size="small" border class="fp-table">
        <el-table-column label="#" width="46">
          <template #default="{ $index }">{{ stepNoAt($index) || '—' }}</template>
        </el-table-column>
        <el-table-column prop="nodeName" label="节点" min-width="180" />
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="STATE_TYPE[row.state] || 'info'" size="small">{{ STATE_LABEL[row.state] || '尚未到达' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动作" width="80">
          <template #default="{ row }">{{ row.action ? wfActionLabel(row.action) : '—' }}</template>
        </el-table-column>
        <el-table-column label="办理人" width="110">
          <template #default="{ row }">{{ row.handler || '—' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="120">
          <template #default="{ row }">{{ fmtTime(row.time) }}</template>
        </el-table-column>
        <el-table-column prop="comment" label="意见 / 备注" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.comment || '—' }}</template>
        </el-table-column>
      </el-table>
    </div>
    <div v-else-if="bill && bill.id" class="fp-noflow">
      <!-- R5-D-054：DRAFT 判定走脚本里的 status 计算属性（bill.id 在而 status 字段缺失＝刚保存的草稿，
           缺省即 DRAFT），不再直读 bill.status——否则上方「尚未提交」与下方「未取到审批链数据」同屏自相矛盾。 -->
      {{ status === 'DRAFT' ? '草稿尚未提交，暂无审批链；提交后此处显示每一节的进度与办理人。' : '未取到审批链数据（流程实例缺失或接口异常），可点右上「刷新进度」重试。' }}
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import FlowChain from '../FlowChain.vue'
import { statusLabel, statusType } from '../../constants/billStatus'
import { wfActionLabel } from '@/constants/enums.js'

const props = defineProps({
  // 当前聚焦的单据：{ id, billNo, title, status, procInstId, voucherNo, currentNode, flowName }
  bill: { type: Object, default: null },
  // GET /wf/query/chain 原样返回：[{ nodeKey, nodeName, state, action, handler, time, comment }]
  nodes: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  updatedAt: { type: String, default: '' },
})
defineEmits(['refresh'])

const showDetail = ref(false)

const STATE_LABEL = {
  DONE: '已通过', CURRENT: '当前节点', REJECTED: '曾驳回', VOID: '已作废', TODO: '尚未到达',
  SKIPPED: '未经过（他档分支）', ADDSIGN: '加签（临时插一道）',
}
const STATE_TYPE = {
  DONE: 'success', CURRENT: 'primary', REJECTED: 'danger', VOID: 'warning', TODO: 'info',
  SKIPPED: 'info', ADDSIGN: 'warning',
}
// D-DEC-386：负面末态集与 SRV BillTraceChainController.STATUS_NEGATIVE 同口径（撤回/取消原缺，卡片头误落兜底档「审批链已办结」）
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
 * D-DEC-091/110/147：分母＝本单实际会经过的节（后端 counted/seqTotal），不再是 wf_node_config 的行数。
 * counted=false 的两类：分支未走（SKIPPED）与加签插道（addSignOnly）；老实例没有 counted 字段时
 * 回落成"全部计入"，与改造前逐字一致（后端新字段是纯增量，新旧包共存期不报错也不掉节）。
 */
const isCounted = (n) => n.counted !== false
const stepNodes = computed(() => nodes2.value.filter(isCounted))
/** 分母优先取后端 seqTotal（口径唯一真相源）；缺字段（旧包）才按现地 counted 计数 */
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
/** 明细表首列：不进分母的节（分支未走/加签插道）显示「—」而不是一个假序号（D-DEC-147） */
function stepNoAt(displayIndex) {
  const n = nodes2.value[displayIndex]
  return n && isCounted(n) ? stepNodes.value.indexOf(n) + 1 : null
}

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
 * D-DEC-091/110：分母从「配置 N 节」改成「本单实际 N 节」。
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
.fp { border: 1px solid #ebeef5; border-radius: 6px; background: #fff; padding: 10px 12px; margin-bottom: 14px; box-shadow: 0 1px 4px rgba(0, 21, 41, .04); }
.fp-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-bottom: 8px; }
.fp-id { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; flex: 1; min-width: 0; }
.fp-no { font-weight: 600; color: #303133; }
.fp-title { color: #606266; font-size: 13px; max-width: 46vw; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fp-none { color: #909399; font-size: 13px; }
.fp-ops { display: flex; align-items: center; gap: 10px; }
.fp-updated { color: #909399; font-size: 12px; }
.fp-cur { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 12px; padding: 8px 12px; border-radius: 4px; margin-bottom: 10px; background: #f5f7fa; }
.fp-cur-label { font-size: 12px; color: #606266; }
.fp-cur-node { font-size: 17px; font-weight: 600; color: #303133; }
.fp-cur-meta { font-size: 12px; color: #606266; }
.fp-cur-run { background: #ecf5ff; }
.fp-cur-run .fp-cur-node { color: #409eff; }
.fp-cur-bad { background: #fef0f0; }
.fp-cur-bad .fp-cur-node { color: #f56c6c; }
.fp-cur-done { background: #f0f9eb; }
.fp-cur-done .fp-cur-node { color: #67c23a; }
.fp-cur-draft .fp-cur-node { color: #909399; }
.fp-steps { margin-bottom: 10px; }
.fp-chain { border-top: 1px dashed #ebeef5; padding-top: 8px; }
.fp-chain-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 6px 12px; margin-bottom: 6px; }
.fp-chain-title { font-size: 12px; color: #303133; font-weight: 600; }
.fp-table { margin-top: 8px; }
.fp-noflow { font-size: 12px; color: #909399; border-top: 1px dashed #ebeef5; padding-top: 8px; }
</style>
