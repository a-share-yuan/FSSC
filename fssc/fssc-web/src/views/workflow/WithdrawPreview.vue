<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<!--
  「已办撤销回原节点」只读预览（P3 G3，2026-09-23）。

  为什么是"只读"而不是"撤销按钮"：Flowable 8 没有 ProcessInstanceModification，
  等价原语 ChangeActivityStateBuilder.moveActivityIdTo 属「搬执行」——直接把运行中的执行实例
  挪到目标活动，会真实改动在办单据本体（作废既有任务、重放历史流转），改错破坏的是单据而非日志。
  在无运行期验证手段时盲改不可接受，故本页只回答"撤回到某节点会影响什么"，供人工核对；
  真正的执行面留待开关 sys_config.wf.withdraw.done.enabled 放行后再实现。

  入参：实例ID 或 任务ID 均可（后端自动识别），故界面只给一个输入框。
-->
<template>
  <div class="wp">
    <el-card shadow="never">
      <template #header>
        <div class="wp-head">
          <span>已办撤销预览</span>
          <el-tag size="small" type="info">只读</el-tag>
          <el-button link type="primary" size="small" @click="load" :disabled="!keyword">刷新</el-button>
        </div>
      </template>

      <el-alert type="warning" :closable="false" show-icon class="wp-alert"
                title="本页只读，不会修改任何流程数据"
                description="「已办撤销回原节点」的自动执行功能尚未开放。本页仅列出可回退的节点及其影响范围，供人工核对，不会改动任何在办单据。" />

      <div class="wp-query">
        <el-input v-model="keyword" clearable class="wp-input"
                  placeholder="流程编号或任务编号"
                  @keyup.enter="load" />
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      </div>

      <template v-if="data">
        <el-descriptions :column="2" border size="small" class="wp-desc">
          <el-descriptions-item label="单据编号">{{ data.bizNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="流程模型">{{ data.modelKey || '—' }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ data.title || '—' }}</el-descriptions-item>
          <el-descriptions-item label="实例状态">{{ data.instanceStatus || '—' }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ data.currentNode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="撤销开关" :span="2">
            <el-tag size="small" :type="data.enabled ? 'success' : 'info'">
              {{ data.enabled ? '已开启' : '关闭（默认）' }}
            </el-tag>
            <span class="wp-hint">{{ data.enabledHint }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="wp-sec">在办任务（{{ (data.activeTasks || []).length }}）</div>
        <el-table :data="data.activeTasks || []" size="small" stripe empty-text="无在办任务">
          <el-table-column prop="nodeName" label="节点" min-width="160" />
          <el-table-column prop="nodeKey" label="节点编码" width="150" />
          <el-table-column prop="assignee" label="办理人ID" width="120" />
        </el-table>

        <div class="wp-sec">
          可回退节点（{{ (data.candidates || []).length }}）
          <span class="wp-hint">退回后该节点需重新办理，其后续节点将被作废并重新流转</span>
        </div>
        <el-table :data="data.candidates || []" size="small" stripe empty-text="暂无可回退的已办节点">
          <el-table-column prop="nodeName" label="回退到" min-width="150" />
          <el-table-column prop="handler" label="原办理人" width="110" />
          <el-table-column prop="action" label="原动作" width="100" :formatter="(row, col, val) => wfActionLabel(val)" />
          <el-table-column label="将被作废的后续节点" min-width="240">
            <template #default="{ row }">
              <span v-if="!row.willVoidCount" class="wp-hint">无（已是最后一节）</span>
              <el-tag v-for="(n, i) in row.willVoidNodes" :key="i" size="small" type="warning" class="wp-vt">
                {{ n }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="wp-notice">{{ data.notice }}</div>
      </template>

      <el-empty v-else-if="searched" description="未找到该流程实例（请确认输入的是实例ID或任务ID）" />
    </el-card>
  </div>
</template>

<script setup>
import { wfActionLabel } from '@/constants/enums'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { wfApi } from '@/api'

const keyword = ref('')
const loading = ref(false)
const searched = ref(false)
const data = ref(null)

async function load() {
  const kw = String(keyword.value || '').trim()
  if (!kw) { ElMessage.warning('请输入流程实例ID 或 任务ID'); return }
  loading.value = true
  try {
    // keyword 走后端自动识别（实例ID 与 任务ID 都是 Flowable 数字串，前端无法区分）
    data.value = await wfApi.withdrawPreview({ keyword: kw })
    searched.value = true
    if (!data.value?.procInstId || !(data.value.candidates || []).length) {
      // 仍可能是合法但无已办节点的实例，故只提示不报错
      if (!data.value?.instanceStatus && !data.value?.bizNo) ElMessage.info('未查询到该流程实例')
    }
  } catch (e) {
    data.value = null
    searched.value = true
    ElMessage.error(e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.wp { padding: 4px; }
.wp-head { display: flex; align-items: center; gap: 8px; }
.wp-head .el-button { margin-left: auto; }
.wp-alert { margin-bottom: 12px; }
.wp-query { display: flex; gap: 8px; }
.wp-input { max-width: 420px; }
.wp-desc { margin-top: 12px; }
.wp-sec { margin: 14px 0 6px; font-size: 13px; font-weight: 600; color: #303133; }
.wp-hint { font-size: 12px; color: #909399; font-weight: 400; margin-left: 6px; }
.wp-vt { margin: 0 6px 4px 0; }
.wp-notice { margin-top: 14px; font-size: 12px; color: #909399; line-height: 20px;
  background: #f7f8fa; border-radius: 4px; padding: 8px 10px; }
</style>
