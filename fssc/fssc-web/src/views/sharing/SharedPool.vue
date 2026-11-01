<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="shared-pool">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">{{ $t('sharing.pool.pageTitle') }}</span>
          <div class="toolbar">
            <el-select v-model="q.view" size="small" style="width: 190px" @change="reset">
              <el-option v-for="v in viewOptions" :key="v.value" :label="v.label" :value="v.value" />
            </el-select>
            <el-input
              v-model="q.keyword"
              size="small"
              :placeholder="$t('sharing.common.phBillNoTitle')"
              clearable
              style="width: 200px"
              @keyup.enter="reset"
            />
            <el-button type="primary" size="small" @click="reset">{{ $t('common.search') }}</el-button>
            <el-tag v-if="overdueCount" type="danger" size="small" effect="light">{{ $t('sharing.pool.tagPageOverdue', { v1: overdueCount }) }}</el-tag>
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
        :empty-text="$t('sharing.pool.emptyView')"
        :row-class-name="rowClass"
      >
        <el-table-column prop="bizNo" :label="$t('field.docNo')" width="140" show-overflow-tooltip />
        <el-table-column prop="title" :label="$t('sharing.common.colTitle')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="nodeName" :label="$t('sharing.pool.colCurrentNode')" width="150" show-overflow-tooltip />
        <el-table-column :label="$t('field.amount')" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column :label="$t('sharing.common.colPriority')" width="88" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.priority === 'URGENT' ? 'danger' : 'info'" effect="plain">
              {{ row.priority === 'URGENT' ? t('sharing.common.colUrgent') : t('sharing.common.colNormal') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('sharing.pool.colAssignee')" width="110">
          <template #default="{ row }">
            <span v-if="row.assigneeName">{{ row.assigneeName }}</span>
            <span v-else-if="row.locked">#{{ row.assigneeId }}</span>
            <span v-else class="muted">{{ $t('sharing.pool.labelUnassigned') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('sharing.pool.colCandidate')" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.candidateNames || []).join('、') || '—' }}</template>
        </el-table-column>
        <el-table-column :label="$t('sharing.pool.colLock')" width="72" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.locked ? 'warning' : 'success'" effect="plain">
              {{ row.locked ? t('sharing.pool.labelLocked') : t('sharing.pool.labelGrabbable') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('sharing.common.colDueTime')" width="180">
          <template #default="{ row }">
            <el-tag v-if="row.dueTimeMissing" size="small" type="info" effect="plain">{{ $t('sharing.pool.labelDueMissing') }}</el-tag>
            <template v-else>
              <span :class="{ 'text-danger': row.overdue }">{{ formatDateTime(row.dueTime) }}</span>
              <el-tag v-if="row.overdue" size="small" type="danger" style="margin-left: 4px">{{ $t('sharing.pool.labelTimeout') }}</el-tag>
            </template>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="$t('common.createTime')" width="160" :formatter="(r, c, v) => formatDateTime(v)" />
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
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

/**
 * 任务池五视图（G3）：数据源 GET /api/tenant/wf/pool/views | /mine | /all。
 * 超时判定唯一真值是后端 wf_task.due_time：dueTimeMissing=true 表示历史行未落时限，
 * 明确显示「待补时限」而不是当成「未超时」（与后端注释规则一致）。
 */
const VIEW_LABELS = computed(() => ({
  MY_TODO: t('sharing.pool.viewMyTodo'),
  CLAIMABLE: t('sharing.pool.viewClaimable'),
  LOCKED_BY_OTHERS: t('sharing.pool.viewLockedByOthers'),
  MY_CLAIMED: t('sharing.pool.viewMyClaimed'),
  POOL_ALL: t('sharing.pool.viewPoolAll'),
}))
const VIEW_HINTS = computed(() => ({
  MY_TODO: t('sharing.pool.hintMyTodo'),
  CLAIMABLE: t('sharing.pool.hintClaimable'),
  LOCKED_BY_OTHERS: t('sharing.pool.hintLockedByOthers'),
  MY_CLAIMED: t('sharing.pool.hintMyClaimed'),
  POOL_ALL: t('sharing.pool.hintPoolAll'),
}))

const viewOptions = ref(Object.entries(VIEW_LABELS.value).map(([value, label]) => ({ value, label })))
const q = reactive({ view: 'MY_TODO', keyword: '' })

const pager = useTablePager({ mode: 'server', storageKey: 'sharing_shared_pool' })
const { rows, loading } = pager

const viewHint = computed(() => VIEW_HINTS.value[q.view] || '')
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
      viewOptions.value = views.map((v) => ({ value: v, label: VIEW_LABELS.value[v] || v }))
    }
  } catch (e) { /* 回退本地标签 */ }
  load()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.header-title { font-size: 15px; font-weight: 600; color: var(--app-text, #303133); }
.toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.muted { color: #c0c4cc; font-size: 12px; }
.text-danger { color: #f56c6c; font-weight: 600; }
:deep(.row-overdue) td.el-table__cell { background-color: rgba(245, 108, 108, 0.13) !important; }
:deep(.row-no-due) td.el-table__cell { background-color: rgba(230, 162, 60, 0.14) !important; }
</style>