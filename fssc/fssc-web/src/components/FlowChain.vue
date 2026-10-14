<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<!--
  审批链全景条：把一单要走的每个节点平铺出来，标出已过/当前/待到达/已驳回。
  数据来自 GET /tenant/wf/query/chain（节点配置 + 审批历史 + 在办任务三源合一）。
  填单侧（BillWorkbench）与审批侧（ApprovalDialog）共用，回答「走到哪一节了」。
  D-DEC-091/110/147：格子编号按 counted（实际会经过的节）现地重排，不再拿数组下标当序号——
  分支未走（SKIPPED）与加签插道（addSignOnly）的格子仍看得见，但不占序号、不进分母。
-->
<template>
  <div class="fc">
    <el-tooltip v-for="(n, i) in nodes" :key="n.nodeKey || i" placement="top" :show-after="150">
      <template #content>
        <div class="fc-tip">
          <div class="fc-tip-title">{{ idx[i] ? idx[i] + '. ' : '' }}{{ n.nodeName }}</div>
          <div>状态：{{ STATE_LABEL[n.state] || '未到达' }}</div>
          <div v-if="n.handler">办理人：{{ n.handler }}</div>
          <div v-if="n.time">时间：{{ String(n.time).replace('T', ' ').slice(5, 16) }}</div>
          <div v-if="n.comment" class="fc-tip-cmt">意见：{{ n.comment }}</div>
          <div v-if="n.addSigns && n.addSigns.length" class="fc-tip-addsign">
            <div class="fc-tip-addsign-title">加签 {{ n.addSigns.length }} 道（临时插一道，不占本链序号）</div>
            <div v-for="(s, j) in n.addSigns" :key="'as' + j" class="fc-tip-cmt">
              {{ j + 1 }}. {{ s.handler || '（系统留痕）' }}{{ s.time ? ' · ' + String(s.time).replace('T', ' ').slice(5, 16) : '' }}<template v-if="s.phase === 'DONE'">（审结）</template>
              <template v-if="s.comment">：{{ s.comment }}</template>
            </div>
          </div>
          <div v-if="n.counted === false" class="fc-tip-note">{{ SKIP_REASON[n.state] || '不计入进度分母' }}</div>
        </div>
      </template>
      <span class="fc-node" :class="'fc-' + (n.state || 'TODO')">
        <i class="fc-idx">{{ idx[i] || '·' }}</i>{{ n.nodeName }}<b v-if="n.addSignCount" class="fc-addsign">+加签{{ n.addSignCount }}</b>
      </span>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({
  nodes: { type: Array, default: () => [] },
})
const STATE_LABEL = {
  DONE: '已通过', CURRENT: '当前节点', REJECTED: '曾驳回', VOID: '已作废', TODO: '尚未到达',
  SKIPPED: '未经过（他档分支）', ADDSIGN: '加签（临时插一道，不改变流程走向）',
}
const SKIP_REASON = {
  SKIPPED: '本单走的是另一条分支，这一节永远不会到 ⇒ 不计入分母',
  ADDSIGN: '加签只是临时插一道（引擎不新增节点）⇒ 不计入分母',
}
/** 格子编号＝实际会经过的节的现地序号（D-DEC-147：调用方可能把提单节提到首位，故不沿用后端 seq） */
const idx = computed(() => {
  let n = 0
  return (props.nodes || []).map((x) => (x.counted === false ? null : ++n))
})
</script>

<style scoped>
.fc { display: flex; flex-wrap: wrap; gap: 4px 6px; }
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
.fc-tip { max-width: 320px; line-height: 20px; }
.fc-tip-title { font-weight: 600; margin-bottom: 2px; }
.fc-tip-cmt { word-break: break-all; }
.fc-tip-addsign { margin-top: 4px; padding-top: 4px; border-top: 1px dashed #e4e7ed; }
.fc-tip-addsign-title { color: #b88230; }
.fc-tip-note { margin-top: 4px; color: #909399; font-size: 11px; }
</style>
