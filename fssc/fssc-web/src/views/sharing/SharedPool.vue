<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="shared-pool">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">共享任务池（五视图）</span>
          <div class="toolbar">
            <el-select v-model="q.view" size="small" style="width: 190px" @change="reset">
              <el-option v-for="v in viewOptions" :key="v.value" :label="v.label" :value="v.value" />
            </el-select>
            <el-input
              v-model="q.keyword"
              size="small"
              placeholder="单号/标题"
              clearable
              style="width: 200px"
              @keyup.enter="reset"
            />
            <el-button type="primary" size="small" @click="reset">查询</el-button>
            <el-tag v-if="overdueCount" type="danger" size="small" effect="light">本页已超时 {{ overdueCount }}</el-tag>
          </div>
        </div>
      </template>

      <el-alert v-if="viewHint" :title="viewHint" type="info" :closable="false" show-icon style="margin-bottom: 10px" />

      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table
        v-else
        :data="rows"
        size="small"
        stripe
        border
        max-height="560"
        empty-text="当前视图暂无任务"
        :row-class-name="rowClass"
      >
        <el-table-column prop="bizNo" label="单号" width="140" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="nodeName" label="当前节点" width="150" show-overflow-tooltip />
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="88" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.priority === 'URGENT' ? 'danger' : 'info'" effect="plain">
              {{ row.priority === 'URGENT' ? '加急' : '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="受理人" width="110">
          <template #default="{ row }">
            <span v-if="row.assigneeName">{{ row.assigneeName }}</span>
            <span v-else-if="row.locked">#{{ row.assigneeId }}</span>
            <span v-else class="muted">未指派</span>
          </template>
        </el-table-column>
        <el-table-column label="候选人" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.candidateNames || []).join('、') || '—' }}</template>
        </el-table-column>
        <el-table-column label="锁定" width="72" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.locked ? 'warning' : 'success'" effect="plain">
              {{ row.locked ? '已锁' : '可抢' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到期时间" width="180">
          <template #default="{ row }">
            <el-tag v-if="row.dueTimeMissing" size="small" type="info" effect="plain">待补时限</el-tag>
            <template v-else>
              <span :class="{ 'text-danger': row.overdue }">{{ formatDateTime(row.dueTime) }}</span>
              <el-tag v-if="row.overdue" size="small" type="danger" style="margin-left: 4px">超时</el-tag>
            </template>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
      </el-table>

      <TablePager :pager="pager" @change="load" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { wfOpsApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { formatDateTime, formatMoney } from '@/utils/format'

/**
 * 任务池五视图（G3）：数据源 GET /api/tenant/wf/pool/views | /mine | /all。
 * 超时判定唯一真值是后端 wf_task.due_time：dueTimeMissing=true 表示历史行未落时限，
 * 明确显示「待补时限」而不是当成「未超时」（与后端注释口径一致）。
 */
const VIEW_LABELS = {
  MY_TODO: '我的待办',
  CLAIMABLE: '可认领',
  LOCKED_BY_OTHERS: '被他人锁定',
  MY_CLAIMED: '我已认领',
  POOL_ALL: '池内全部',
}
const VIEW_HINTS = {
  MY_TODO: 'PENDING 且受理人是我或我是候选人（含池内未锁定）。',
  CLAIMABLE: 'PENDING、未指派、我是候选人且节点是任务池——真正能抢的任务。',
  LOCKED_BY_OTHERS: '节点是任务池、已被指派给他人但我是候选人：行看得见、办不了，可看到锁定者。',
  MY_CLAIMED: '节点是任务池且受理人是我。',
  POOL_ALL: '节点是任务池的全部在办任务（不限候选人，共享中心主管总览视角，需 wf:instance:list）。',
}

const viewOptions = ref(Object.entries(VIEW_LABELS).map(([value, label]) => ({ value, label })))
const q = reactive({ view: 'MY_TODO', keyword: '' })

const pager = useTablePager({ mode: 'server', storageKey: 'sharing_shared_pool' })
const { rows, loading } = pager

const viewHint = computed(() => VIEW_HINTS[q.view] || '')
const overdueCount = computed(() => rows.value.filter((r) => r.overdue).length)

function rowClass({ row }) {
  if (row.overdue) return 'row-overdue'
  if (row.dueTimeMissing) return 'row-no-due'
  return ''
}

function reset() {
  pager.resetPage()
  load()
}

async function load() {
  await pager.load(async (p) => {
    const params = { pageNum: p.pageNum, pageSize: p.pageSize, keyword: q.keyword || undefined }
    const r = q.view === 'POOL_ALL'
      ? await wfOpsApi.poolAll(params)
      : await wfOpsApi.poolMine({ ...params, view: q.view })
    return { list: r?.list || [], total: r?.total || 0 }
  })
}

onMounted(async () => {
  try {
    // 视图清单以后端为准（silent：旧后端无此端点时静默回退本地标签，不影响主查询）
    const views = await wfOpsApi.poolViews({ silent: true })
    if (Array.isArray(views) && views.length) {
      viewOptions.value = views.map((v) => ({ value: v, label: VIEW_LABELS[v] || v }))
    }
  } catch (e) { /* 回退本地标签 */ }
  load()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.header-title { font-size: 15px; font-weight: 600; color: #303133; }
.toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.muted { color: #c0c4cc; font-size: 12px; }
.text-danger { color: #f56c6c; font-weight: 600; }
:deep(.row-overdue) td.el-table__cell { background-color: #fef0f0 !important; }
:deep(.row-no-due) td.el-table__cell { background-color: #fdf6ec !important; }
</style>