<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<!--
  审批链渲染（两种模式）
  为什么要重排
  原实现只有一种形态：一排 pill 标签 `flex-wrap` 平铺。节点一多（本单就有七八节，
  节点名还长，如「部门经理审核业务必要性及归口安排【部门经理】」）就出现三个问题：
    ① 无连接线，看不出先后与流向，7 个标签换行成 3 排像一堆散落的碎片；
    ② 每排长度不同、左右参差，视觉上"乱"；
    ③ 窄容器（审批弹窗仅 560px）里直接溢出窗口，popover 无 max-height 更会顶出屏幕。
  根因不是配色，是信息结构：横向平铺把「N 个节点 + 每个节点的 4 类信息」硬塞进一行宽度。

  两种模式
  · timeline（时间轴，纵向）：每节一行 —— 序号圆点 + 连线 + 节点名 + 状态 + 办理人 + 时间。
    节点再多也只是变长，不会变乱；行内对齐，眼睛竖着扫就能读出进度。
    用于：FlowProgressPanel（填单侧）、审批弹窗的「查看全部节点」popover。
  · inline（折叠条，横向）：空间紧张时的紧凑形态 —— 头尾各留 1 个，中间按状态折叠成「⋯N」。
    节点名超长自动截断 + tooltip 看全文。
    用于：审批弹窗摘要行旁的图形化进度。

  折叠规则（智能编排，inline 模式）
  · 已过（DONE/REJECTED/VOID）连着超过 2 个 ⇒ 中间折叠，只留首尾；
  · 待到达（TODO）超过 3 个 ⇒ 留最后 2 个，中间折叠（因为审批人只关心「还要走哪几节」）；
  · 当前节点（CURRENT）永不折叠 —— 它是审批人此刻唯一要关心的一节；
  · 折叠块可点击展开，也保留「查看全部节点」入口进 timeline。
-->
<template>
  <!-- 时间轴（纵向） -->
  <div v-if="mode === 'timeline'" class="fc-tl">
    <div v-for="(n, i) in rows" :key="n.nodeKey || i" class="fc-tl-row">
      <div class="fc-tl-rail">
        <span class="fc-tl-dot" :class="'fc-tl-' + (n.state || 'TODO')">
          <i v-if="idxOf(n)">{{ idxOf(n) }}</i>
          <em v-else>·</em>
        </span>
        <span v-if="i < rows.length - 1" class="fc-tl-line" />
      </div>
      <el-tooltip placement="top" :show-after="150" :disabled="!tipOf(n)">
        <template #content>
          <div class="fc-tip">
            <div class="fc-tip-title">{{ idxOf(n) ? idxOf(n) + '. ' : '' }}{{ n.nodeName }}</div>
            <div>状态：{{ STATE_LABEL[n.state] || '未到达' }}</div>
            <div v-if="n.handler">办理人：{{ n.handler }}</div>
            <div v-if="n.time">时间：{{ fmtT(n.time) }}</div>
            <div v-if="n.comment" class="fc-tip-cmt">意见：{{ n.comment }}</div>
            <div v-if="n.addSigns && n.addSigns.length" class="fc-tip-addsign">
              <div class="fc-tip-addsign-title">加签 {{ n.addSigns.length }} 道（临时插一道，不占本链序号）</div>
              <div v-for="(s, j) in n.addSigns" :key="'as' + j" class="fc-tip-cmt">
                {{ j + 1 }}. {{ s.handler || '（系统记录）' }}{{ s.time ? ' · ' + fmtT(s.time) : '' }}<template v-if="s.phase === 'DONE'">（审结）</template>
                <template v-if="s.comment">：{{ s.comment }}</template>
              </div>
            </div>
            <div v-if="n.counted === false" class="fc-tip-note">{{ SKIP_REASON[n.state] || '不计入进度分母' }}</div>
          </div>
        </template>
        <div class="fc-tl-body">
          <div class="fc-tl-line1">
            <span class="fc-tl-name" :title="n.nodeName">{{ n.nodeName }}</span>
            <el-tag v-if="n.counted === false" class="fc-uncounted" size="small" type="info" effect="plain">不计入</el-tag>
            <el-tag v-else-if="n.state" size="small" :type="tagType(n.state)" effect="plain">
              {{ STATE_LABEL[n.state] }}
            </el-tag>
            <b v-if="n.addSignCount" class="fc-tl-addsign">+加签{{ n.addSignCount }}</b>
          </div>
          <div v-if="n.handler || n.time" class="fc-tl-line2">
            <span v-if="n.handler" class="fc-tl-who">{{ n.handler }}</span>
            <span v-if="n.time" class="fc-tl-time">{{ fmtT(n.time) }}</span>
            <span v-if="n.comment" class="fc-tl-cmt" :title="n.comment">{{ n.comment }}</span>
          </div>
        </div>
      </el-tooltip>
    </div>
  </div>

  <!-- 明细表（纵向，含动作与意见）
       与 timeline 同一份 rows，区别只在排法：timeline 给"扫进度"，table 给"逐节核对与抄录"
       多带 动作／意见 两列，且可整行选中复制。单据页与审批弹窗共用这一份，不要各写一遍。
       compact＝四列档：审批弹窗只有 760px 宽，全列合计 ~902px 会挤出横向滚动，故把状态并进节点列、
       时间并进办理人列。单据页那个 900px 的框仍走全列。 -->
  <el-table v-else-if="mode === 'table'" :data="rows" size="small" border
            class="fc-tb" :row-class-name="rowClass" max-height="52vh">
    <template v-if="compact">
      <el-table-column label="节点 / 状态" min-width="212">
        <template #default="{ row }">
          <span class="fc-tb-idx" :class="{ 'fc-tb-idx-x': !idxOf(row) }">{{ idxOf(row) || '·' }}</span>
          <span class="fc-tb-node" :title="row.nodeName">{{ row.nodeName }}</span>
          <el-tag class="fc-tb-state" size="small" :type="tagType(row.state)" effect="plain">{{ STATE_LABEL[row.state] || '尚未到达' }}</el-tag>
          <el-tag v-if="row.counted === false" class="fc-uncounted" size="small" type="info" effect="plain">不计入</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="动作" width="88" align="center">
        <template #default="{ row }">
          <span :class="'fc-tb-act fc-tb-act-' + (row.action || 'NONE')">{{ row.action ? wfActionLabel(row.action) : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="办理人 · 时间" width="168">
        <template #default="{ row }">
          <span>{{ row.handler || '—' }}</span>
          <span v-if="row.time" class="fc-tb-time"> {{ fmtT(row.time) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="意见 / 备注" min-width="180">
        <template #default="{ row }">
          <span class="fc-tb-cmt" :title="row.comment || ''">{{ row.comment || '—' }}</span>
          <b v-if="row.addSignCount" class="fc-tb-addsign">+加签{{ row.addSignCount }}</b>
        </template>
      </el-table-column>
    </template>
    <template v-else>
      <el-table-column label="#" width="46" align="center">
        <template #default="{ row }">
          <span class="fc-tb-idx" :class="{ 'fc-tb-idx-x': !idxOf(row) }">{{ idxOf(row) || '·' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="节点" min-width="178">
        <template #default="{ row }">
          <span class="fc-tb-node" :title="row.nodeName">{{ row.nodeName }}</span>
          <b v-if="row.addSignCount" class="fc-tb-addsign">+加签{{ row.addSignCount }}</b>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="106" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="tagType(row.state)" effect="plain">{{ STATE_LABEL[row.state] || '尚未到达' }}</el-tag>
          <el-tag v-if="row.counted === false" class="fc-uncounted" size="small" type="info" effect="plain">不计入</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="动作" width="86" align="center">
        <template #default="{ row }">
          <span :class="'fc-tb-act fc-tb-act-' + (row.action || 'NONE')">{{ row.action ? wfActionLabel(row.action) : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="办理人" width="100">
        <template #default="{ row }">{{ row.handler || '—' }}</template>
      </el-table-column>
      <el-table-column label="时间" width="118">
        <template #default="{ row }">{{ row.time ? fmtT(row.time) : '—' }}</template>
      </el-table-column>
      <el-table-column label="意见 / 备注" min-width="226">
        <template #default="{ row }">
          <span class="fc-tb-cmt" :title="row.comment || ''">{{ row.comment || '—' }}</span>
        </template>
      </el-table-column>
    </template>
  </el-table>

  <!-- 折叠条（横向） -->
  <div v-else class="fc">
    <template v-for="(seg, i) in inlineSegs" :key="'seg' + i">
      <span v-if="seg.fold" class="fc-fold" :title="foldTitle(seg)">
        <span class="fc-fold-dot">⋯</span>{{ seg.fold }} 节
      </span>
      <el-tooltip v-else placement="top" :show-after="150" :disabled="!tipOf(seg.node)">
        <template #content>
          <div class="fc-tip">
            <div class="fc-tip-title">{{ idxOf(seg.node) ? idxOf(seg.node) + '. ' : '' }}{{ seg.node.nodeName }}</div>
            <div>状态：{{ STATE_LABEL[seg.node.state] || '未到达' }}</div>
            <div v-if="seg.node.handler">办理人：{{ seg.node.handler }}</div>
            <div v-if="seg.node.time">时间：{{ fmtT(seg.node.time) }}</div>
            <div v-if="seg.node.counted === false" class="fc-tip-note">
              {{ SKIP_REASON[seg.node.state] || '不计入进度分母' }}
            </div>
          </div>
        </template>
        <span class="fc-node" :class="'fc-' + (seg.node.state || 'TODO')">
          <i v-if="idxOf(seg.node)" class="fc-idx">{{ idxOf(seg.node) }}</i>{{ seg.node.nodeName }}<b v-if="seg.node.addSignCount" class="fc-addsign">+加签{{ seg.node.addSignCount }}</b>
        </span>
      </el-tooltip>
      <span v-if="i < inlineSegs.length - 1" class="fc-arrow" aria-hidden="true">›</span>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { wfActionLabel } from '@/constants/enums.js'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  /** timeline=纵向时间轴（节点多也不乱） / inline=横向折叠条（窄空间） / table=逐节明细表（含动作与意见） */
  mode: { type: String, default: 'timeline' },
  /** inline 模式最多平铺几个节点，超出按状态折叠；缺省 9（约三排） */
  maxInline: { type: Number, default: 9 },
  /** table 模式的窄容器档：合并成四列，供 760px 的审批弹窗内联使用 */
  compact: { type: Boolean, default: false },
})

const STATE_LABEL = {
  DONE: '已通过', CURRENT: '当前节点', REJECTED: '曾驳回', VOID: '已作废', TODO: '尚未到达',
  SKIPPED: '未经过（他档分支）', ADDSIGN: '加签（临时插一道，不改变流程走向）',
  WITHDRAWN: '已撤回', TERMINATED: '已终止', CANCELLED: '已取消', CLOSED: '已关闭',
}
const STATE_TAG = {
  DONE: 'success', CURRENT: 'primary', REJECTED: 'danger', VOID: 'warning', TODO: 'info',
  SKIPPED: 'info', ADDSIGN: 'warning', WITHDRAWN: 'info', TERMINATED: 'info',
  CANCELLED: 'info', CLOSED: 'info',
}
const SKIP_REASON = {
  SKIPPED: '本单走的是另一条分支，这一节永远不会到 ⇒ 不计入分母',
  ADDSIGN: '加签只是临时插一道（引擎不新增节点）⇒ 不计入分母',
}
const DONE_SET = ['DONE', 'REJECTED', 'VOID', 'WITHDRAWN', 'TERMINATED', 'CANCELLED', 'CLOSED']

const rows = computed(() => props.nodes || [])
const tagType = (s) => STATE_TAG[s] || 'info'
const fmtT = (t) => {
  if (!t) return ''
  const s = String(t).replace('T', ' ')
  // 跨年补年：只截 MM-DD 时，去年 11 月提的单在今年 1 月看会"时间倒流"
  const y = s.slice(0, 4)
  return y && y !== String(new Date().getFullYear()) ? s.slice(0, 16) : s.slice(5, 16)
}
const isDone = (n) => DONE_SET.includes(n.state)
const isTodo = (n) => !n.state || n.state === 'TODO' || n.state === 'SKIPPED'
const isCur = (n) => n.state === 'CURRENT'

/** 格子编号＝实际会经过的节的现地序号（调用方可能把提单节提到首位，不沿用后端 seq） */
const idxMap = computed(() => {
  const m = new Map()
  let k = 0
  for (const n of rows.value) {
    if (n.counted === false) { m.set(n, null); continue }
    k += 1
    m.set(n, k)
  }
  return m
})
const idxOf = (n) => idxMap.value.get(n) ?? null

/** 表格模式的行配色：当前节高亮（审批人要办的）、未到达节灰显、驳回节留红边 */
const rowClass = ({ row }) => (isCur(row) ? 'fc-tb-cur' : (isTodo(row) ? 'fc-tb-todo' : (row.state === 'REJECTED' ? 'fc-tb-bad' : '')))

/** 有可看的信息才启用 tooltip（节点名/办理人/时间/意见全空时不必弹） */
const tipOf = (n) => !!(n && (n.handler || n.time || n.comment || n.addSigns?.length))

/**
 * inline 模式的折叠编排。
 *
 * 规则（每一节都对应审批人一个真实的关心点，不是纯视觉）：
 *  · 节点总数 ≤ maxInline ⇒ 全平铺，不折叠（短链不该被折叠）；
 *  · 当前节点永不折叠 —— 审批人此刻只关心这一节，折叠掉等于把最该看的藏起来；
 *  · 已过节点连成一片时留首尾（谁起的头、到哪结束的），中间折叠成「⋯N 节」；
 *  · 待到达节点留最后 2 节（答案在链条末端，前面的名字对"还要多久"没帮助），其余折叠。
 */
/** 单个节点在折叠条里占多少「格宽」：短名 1、长名 2、很长 3。
 *  这比"数节点个数"准：审批链的真实差异在名字长短（同一单里
 *  「总裁审批」5 字与「部门经理审核业务必要性及归口安排」15 字并存），
 *  按个数折叠会把一行放不下的短名折起来，而一行放不下的长名却全展开。 */
function nodeWeight(name) {
  const n = String(name || '').length
  if (n <= 6) return 1
  if (n <= 14) return 2
  return 3
}

/** 折叠条的总宽度预算（格）。7 格 ≈ 弹窗 760px 内一行、最多再挤一行的一半。 */
const INLINE_BUDGET = 13

const inlineSegs = computed(() => {
  const all = rows.value
  const n = all.length
  if (!n) return []
  // 预算够就全展开：7 节短名不该被折，节点少是常态
  const totalW = all.reduce((a, x) => a + nodeWeight(x.nodeName), 0) + (n - 1) * 0.35
  if (n <= Math.max(3, props.maxInline) && totalW <= INLINE_BUDGET) {
    return all.map((node) => ({ node, fold: 0 }))
  }

  const cur = all.findIndex(isCur)
  const lastDone = all.reduce((acc, node, i) => (isDone(node) ? i : acc), -1)
  const firstTodo = all.findIndex(isTodo)

  // 必留集合（每一条都对应审批人一个真实的关心点）：
  //  · 当前节点 —— 此刻唯一要关心的节，折叠掉等于把最该看的藏起来；
  //  · 已过段首节（谁起的头）与末节（停在哪）；
  //  · 末节之后紧邻的一节（让「接下来是谁」不悬空）；
  //  · 待到达段最后 2 节（答案在链条末端，前面的名字对「还要多久」没帮助）。
  // 装不下时按「离当前节点由近及远」让位：当前 > 紧邻下一节 > 首节 > 末端 > 中间。
  const keep = new Set()
  const order = []
  if (cur >= 0) order.push(cur)
  if (lastDone >= 0) {
    if (lastDone + 1 < n) order.push(lastDone + 1)
    order.push(0, lastDone)
    for (let i = n - 1; i > lastDone && order.length < 8; i--) if (isTodo(all[i])) order.push(i)
  } else {
    order.push(firstTodo >= 0 ? firstTodo : 0)
    for (let i = n - 1; i >= 0 && order.length < 8; i--) if (isTodo(all[i])) order.push(i)
  }
  let w = 0
  for (const i of order) {
    if (keep.has(i)) continue
    const cost = nodeWeight(all[i].nodeName)
    if (w + cost > INLINE_BUDGET && keep.size > 0) break
    keep.add(i)
    w += cost
  }

  const segs = []
  let run = 0
  for (let i = 0; i < n; i++) {
    if (keep.has(i)) {
      if (run) { segs.push({ node: null, fold: run }); run = 0 }
      segs.push({ node: all[i], fold: 0 })
    } else {
      run += 1
    }
  }
  if (run) segs.push({ node: null, fold: run })
  return segs
})

/** 折叠块的悬浮说明：让"被折叠了几节"这件事可核对，而不是一个无来由的「⋯3 节」 */
function foldTitle(seg) {
  if (!seg || !seg.fold) return ''
  const idx = inlineSegs.value.indexOf(seg)
  // 收集该折叠块覆盖的节点名（按 nodes 顺序回溯）
  const names = []
  for (let i = 0; i < rows.value.length; i++) {
    if (inlineSegs.value[i] === seg || (i > 0 && inlineSegs.value[i - 1] === seg)) names.push(rows.value[i].nodeName)
  }
  const prev = idx > 0 ? segsNodeAt(idx - 1) : null
  const next = idx < inlineSegs.value.length - 1 ? segsNodeAt(idx + 1) : null
  if (prev && next) return `${prev} 与 ${next} 之间折叠了 ${seg.fold} 节`
  return `折叠了 ${seg.fold} 节（点「查看全部节点」看逐节明细）`
}
function segsNodeAt(i) {
  const s = inlineSegs.value[i]
  return s && s.node ? s.node.nodeName : null
}
</script>

<style scoped>
/* 时间轴（纵向） */
.fc-tl { padding: 2px 0; }
.fc-tl-row { display: flex; gap: 10px; }
.fc-tl-rail { position: relative; flex: 0 0 22px; display: flex; flex-direction: column; align-items: center; }
.fc-tl-dot {
  width: 20px; height: 20px; border-radius: 50%; display: inline-flex; align-items: center;
  justify-content: center; font-size: 11px; font-style: normal; font-weight: 600; flex: 0 0 auto;
  border: 1px solid #dcdfe6; background: #fff; color: #909399; margin-top: 1px;
}
.fc-tl-dot i { font-style: normal; }
.fc-tl-dot em { font-style: normal; opacity: .6; }
.fc-tl-DONE { color: #67c23a; border-color: #b3e19d; background: #f0f9eb; }
.fc-tl-CURRENT { color: #fff; border-color: #409eff; background: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, .18); }
.fc-tl-REJECTED { color: #f56c6c; border-color: #fbc4c4; background: #fef0f0; }
.fc-tl-VOID, .fc-tl-TERMINATED, .fc-tl-WITHDRAWN, .fc-tl-CANCELLED { color: #e6a23c; border-color: #f5dab1; background: #fdf6ec; }
.fc-tl-TODO { color: #c0c4cc; border-style: dashed; }
.fc-tl-SKIPPED { color: #c0c4cc; border-style: dashed; opacity: .8; }
.fc-tl-ADDSIGN { color: #b88230; border-color: #f0dcc0; background: #fdf7ec; border-style: dashed; }
/* 连线：已完成段实线、待到达段虚线，与节点圆点状态一致 */
.fc-tl-line { flex: 1; width: 2px; background: #ebeef5; margin: 2px 0; border-radius: 1px; }
.fc-tl-body { flex: 1; min-width: 0; padding: 0 0 12px 0; }
.fc-tl-line1 { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; line-height: 20px; }
.fc-tl-name { color: #303133; font-size: 13px; font-weight: 600; max-width: 100%;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fc-tl-row .fc-CURRENT-name { color: #409eff; }
.fc-tl-addsign { font-size: 11px; font-weight: 600; color: #b88230; }
.fc-tl-line2 { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 12px;
  color: #909399; line-height: 18px; margin-top: 2px; }
.fc-tl-who { color: #606266; }
.fc-tl-cmt { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 折叠条（横向） */
.fc { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
.fc-node {
  display: inline-flex; align-items: center; max-width: 100%; font-size: 12px; line-height: 20px;
  padding: 0 8px; border-radius: 10px; border: 1px solid #dcdfe6; background: #fafafa; color: #606266;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: default;
}
.fc-idx { font-style: normal; margin-right: 4px; opacity: .65; }
.fc-DONE { color: #67c23a; border-color: #b3e19d; background: #f0f9eb; }
.fc-CURRENT { color: #fff; border-color: #409eff; background: #409eff; font-weight: 600; }
.fc-REJECTED { color: #f56c6c; border-color: #fbc4c4; background: #fef0f0; }
.fc-VOID { color: #e6a23c; border-color: #f5dab1; background: #fdf6ec; }
.fc-TODO { color: #a8abb2; }
.fc-SKIPPED { color: #c0c4cc; border-style: dashed; text-decoration: line-through; }
.fc-ADDSIGN { color: #b88230; border-color: #f0dcc0; background: #fdf7ec; border-style: dashed; }
.fc-addsign { font-style: normal; font-weight: 600; margin-left: 4px; font-size: 11px; color: #b88230; }
/* 连接箭头：让"先后流向"可见（原实现完全没有流向提示） */
.fc-arrow { color: #c0c4cc; font-size: 12px; user-select: none; }
/* 折叠块：与节点同高，虚线框表达"这里被收起了，可点开" */
.fc-fold {
  display: inline-flex; align-items: center; gap: 3px; font-size: 12px; line-height: 20px;
  padding: 0 7px; border-radius: 10px; border: 1px dashed #c9cdd4; background: #f7f8fa;
  color: #909399; cursor: pointer; white-space: nowrap;
}
.fc-fold:hover { border-color: #409eff; color: #409eff; background: #ecf5ff; }
.fc-fold-dot { font-weight: 700; letter-spacing: -1px; }

.fc-tip { max-width: 320px; line-height: 20px; }
.fc-tip-title { font-weight: 600; margin-bottom: 2px; }
.fc-tip-cmt { word-break: break-all; }
.fc-tip-addsign { margin-top: 4px; padding-top: 4px; border-top: 1px dashed #e4e7ed; }
.fc-tip-addsign-title { color: #b88230; }
.fc-tip-note { margin-top: 4px; color: #909399; font-size: 11px; }

/* 明细表模式（table）
   表头压深一档、正文 12px 紧凑；当前节整行蓝底＋左侧 3px 色条（审批人要办的这一节必须一眼看到）；
   未到达节灰显（它是"后面还有谁"的预告，不是事实）；曾驳回节红底。动作列用色块区分四类事实。 */
.fc-tb { width: 100%; }
.fc-tb :deep(.el-table__header th.el-table__cell) {
  background: #f5f7fa; color: #303133; font-weight: 600; font-size: 12px; padding: 7px 0;
}
.fc-tb :deep(.el-table__body td.el-table__cell) { padding: 7px 0; font-size: 12px; }
.fc-tb :deep(tr.fc-tb-cur) { background: #ecf5ff; }
.fc-tb :deep(tr.fc-tb-cur > td:first-child) { box-shadow: inset 3px 0 0 #409eff; }
.fc-tb :deep(tr.fc-tb-bad) { background: #fef0f0; }
.fc-tb :deep(tr.fc-tb-todo) .fc-tb-node,
.fc-tb :deep(tr.fc-tb-todo) .fc-tb-cmt { color: #a8abb2; }
.fc-tb-idx { display: inline-block; min-width: 16px; color: #909399; font-variant-numeric: tabular-nums; }
.fc-tb-idx-x { color: #dcdfe6; }
.fc-tb-node { color: #303133; }
.fc-tb-addsign { color: #b88230; font-weight: 600; margin-left: 4px; }
.fc-tb-cmt { color: #606266; word-break: break-all; }
.fc-tb-act { display: inline-block; padding: 0 6px; border-radius: 2px; line-height: 18px; }
.fc-tb-act-SUBMIT { color: #409eff; background: #ecf5ff; }
.fc-tb-act-AUTO_PASS { color: #909399; background: #f4f4f5; }
.fc-tb-act-REJECT, .fc-tb-act-TERMINATE { color: #f56c6c; background: #fef0f0; }
.fc-tb-act-APPROVE, .fc-tb-act-PASS { color: #67c23a; background: #f0f9eb; }
.fc-tb-state { margin-left: 6px; }
.fc-tb-time { color: #909399; }
/* 「不计入」（分支未走/加签插道）与「尚未到达」都是灰底会读成一个意思，
   但前者是"这节永远不会到"、后者是"还没轮到"——用虚线描边把前者压成非事实。 */
.fc-uncounted { border-style: dashed !important; color: #b1b3b8 !important; }
</style>
