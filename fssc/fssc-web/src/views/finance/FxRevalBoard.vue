<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="fx-reval-board">
    <el-card>
      <!-- 步骤条（四步化：试算预览 → 确认执行 → 生成凭证 → 反冲） -->
      <el-steps :active="stepActive" align-center finish-status="success" style="margin-bottom: 16px">
        <el-step :title="$t('finance.fxReval.Simulation')" :description="$t('finance.fxReval.Simulation2')" />
        <el-step :title="$t('finance.fxReval.Confirm')" :description="$t('finance.fxReval.Revalue')" />
        <el-step :title="$t('finance.fxReval.Voucher')" :description="$t('finance.fxReval.Difference')" />
        <el-step :title="$t('finance.fxReval.Reverse')" :description="$t('finance.fxReval.Reverse2')" />
      </el-steps>

      <!-- Toolbar -->
      <div class="toolbar">
        <span class="label">重估期间：</span>
        <el-date-picker
          v-model="period"
          type="month"
          format="YYYY-MM"
          value-format="YYYY-MM"
          :placeholder="$t('bank.reconcileSheet.Select')"
          style="width:180px"
          @change="onPeriodChange"
        />
        <el-button type="primary" plain @click="onTrial">{{ $t('finance.fxReval.Simulation') }}</el-button>
        <el-button type="primary" :loading="running" @click="onRun">{{ $t('finance.fxReval.Confirm2') }}</el-button>
        <el-button @click="loadList">{{ $t('common.refresh') }}</el-button>
      </div>

      <!-- Table -->
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="list" v-else border stripe :empty-text="$t('common.empty')">
        <el-table-column :label="$t('finance.fxReval.Type')" width="80" align="center">
          <template #default="{ row }">{{ TYPE_CN[row.itemType] || row.itemType || '-' }}</template>
        </el-table-column>
        <el-table-column prop="fromCurrency" label="币种" width="80" align="center" />
        <el-table-column prop="bookCount" label="笔数" width="70" align="right" />
        <el-table-column label="原币金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.bookLocal && row.rate ? row.bookLocal / row.rate : null) }}</template>
        </el-table-column>
        <el-table-column label="账面金额(本位币)" width="150" align="right">
          <template #default="{ row }">{{ formatMoney(row.bookLocal) }}</template>
        </el-table-column>
        <el-table-column label="期末汇率" width="90" align="right">
          <template #default="{ row }">{{ row.rate ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="重估差额" width="130" align="right">
          <template #default="{ row }">
            <span :class="{ 'reval-positive': row.diff > 0, 'reval-negative': row.diff < 0 }">
              {{ formatMoney(row.diff) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="period" label="期间" width="100" align="center">
          <template #default="{ row }">{{ row.period && row.period.length === 6 ? row.period.slice(0,4) + '-' + row.period.slice(4) : row.period }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="ST_TAG[row.status] || 'info'" size="small">{{ ST_LABEL[row.status] || row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="凭证ID" width="90" align="center">
          <template #default="{ row }">{{ row.voucherId ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'GENERATED'" link type="danger" size="small" @click="onReverse(row)">反冲</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <el-pagination
        class="pager"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, prev, pager, next, jumper"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- 试算预览弹窗（第一步：只算不写入数据库） -->
    <el-dialog v-model="trialVisible" title="重估试算预览（仅试算不保存）" width="920px" destroy-on-close>
      <el-alert v-if="trial.existingCount > 0" type="warning" :closable="false" style="margin-bottom:8px"
        :title="`该期间已有 ${trial.existingCount} 条重估记录，正式执行时同组合将自动跳过`" />
      <el-table :data="trial.rows" size="small" border max-height="420">
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ row.itemTypeCn }}</template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="70" align="center" />
        <el-table-column prop="bookCount" label="笔数" width="70" align="right" />
        <el-table-column label="原币合计" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.origTotal) }}</template>
        </el-table-column>
        <el-table-column label="账面本位币" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.bookLocal) }}</template>
        </el-table-column>
        <el-table-column label="期末汇率" width="90" align="right">
          <template #default="{ row }">{{ row.rate ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="预计重估差异" width="130" align="right">
          <template #default="{ row }">
            <span v-if="row.diff != null" :class="{ 'reval-negative': row.diff < 0 }">{{ formatMoney(row.diff) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">{{ DIR_CN[row.direction] || '—' }}</template>
        </el-table-column>
        <el-table-column label="试算状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="TRIAL_TAG[row.trialStatus] || 'info'">{{ TRIAL_LABEL[row.trialStatus] || row.trialStatus }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="tip">试算不写库；确认无误后点「② 确认执行重估」正式生成凭证（草稿，不自动记账）。</div>
      <template #footer>
        <el-button size="small" @click="trialVisible = false">关闭</el-button>
        <el-button type="primary" size="small" @click="confirmFromTrial">确认并执行重估</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fxRevalApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

const ST_LABEL = { GENERATED: '已生成凭证', SKIPPED: '已跳过', REVERSED: '已反冲' }
const ST_TAG = { GENERATED: 'success', SKIPPED: 'info', REVERSED: 'danger' }
const TYPE_CN = { AP: '应付', AR: '应收', LOAN: '借款' }
const DIR_CN = { GAIN: '收益', LOSS: '损失', SKIP: '无差异' }
const TRIAL_LABEL = { READY: '可执行', EXIST: '已重估', NO_RATE: '缺汇率', SKIP: '无差异' }
const TRIAL_TAG = { READY: 'success', EXIST: 'info', NO_RATE: 'danger', SKIP: 'info' }

const loading = ref(false)
const running = ref(false)
const list = ref([])
const total = ref(0)
const trialVisible = ref(false)
const trial = ref({ rows: [], existingCount: 0 })

const now = new Date()
const period = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const query = reactive({ pageNum: 1, pageSize: 10 })

// 步骤条联动：有已生成凭证 → ③；存在已反冲 → ④
const stepActive = computed(() => {
  if (list.value.some((r) => r.status === 'REVERSED')) return 4
  if (list.value.some((r) => r.status === 'GENERATED')) return 3
  if (list.value.length) return 2
  return 1
})

function formatMoney(val) {
  if (val == null) return '-'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function normPeriod() {
  return (period.value || '').replace('-', '')
}

async function loadList() {
  if (!period.value) return
  loading.value = true
  try {
    const res = await fxRevalApi.page({ period: normPeriod(), pageNum: query.pageNum, pageSize: query.pageSize })
    list.value = res.records || res.list || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(t('audit.main.Failed'))
  } finally {
    loading.value = false
  }
}

function onPeriodChange() {
  query.pageNum = 1
  loadList()
}

// ① 试算预览：只算不写入数据库
async function onTrial() {
  if (!period.value) return ElMessage.warning(t('finance.fxReval.Please'))
  try {
    const ret = await fxRevalApi.trial(normPeriod())
    trial.value = { rows: ret.rows || [], existingCount: (ret.rows || []).filter((r) => r.trialStatus === 'EXIST').length }
    trialVisible.value = true
  } catch (e) {
    ElMessage.error(t('finance.fxReval.Simulation3'))
  }
}

async function confirmFromTrial() {
  // 试算预览页脚的写入口与工具栏 onRun 一样两步确认（不能一键直发 POST /fx-reval/run，
  // 同一写动作两条入口确认步数不一致）；取消时保留试算弹窗供继续预览。
  try {
    await ElMessageBox.confirm(`确认对期间 [${period.value}] 正式执行外币汇兑重估？将按月末汇率生成 草稿转账凭证（不自动记账）。`, '执行重估', { type: 'warning' })
  } catch (_) { return } // 用户取消：试算弹窗保留
  trialVisible.value = false
  doRun()
}

// ② 确认执行：正式重估并生成凭证
async function onRun() {
  if (!period.value) return ElMessage.warning(t('finance.fxReval.Please'))
  await ElMessageBox.confirm(`确认对期间 [${period.value}] 正式执行外币汇兑重估？将按月末汇率生成 草稿转账凭证（不自动记账）。`, '执行重估', { type: 'warning' })
  doRun()
}

async function doRun() {
  running.value = true
  try {
    const ret = await fxRevalApi.run(normPeriod())
    ElMessage.success(`重估执行完成：处理 ${ret.processed} 组，生成凭证 ${ret.generated} 张，跳过 ${ret.skipped} 组`)
    query.pageNum = 1
    loadList()
  } catch (e) {
    ElMessage.error(t('finance.fxReval.Revaluation'))
  } finally {
    running.value = false
  }
}

// ④ 反冲：对已生成凭证反向冲销
async function onReverse(row) {
  await ElMessageBox.confirm(
    `确认反冲该重估记录（${row.currencyCode || row.fromCurrency} 差异 ${formatMoney(row.revalAmount || row.diff)}）？将生成借贷对调的反向草稿凭证。`,
    '反冲确认', { type: 'warning' })
  try {
    const ret = await fxRevalApi.reverse(row.id)
    ElMessage.success(`反冲完成，反向凭证：${ret.reverseVoucherNo}`)
    loadList()
  } catch (e) {
    ElMessage.error(t('finance.fxReval.Reverse3'))
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.label {
  font-size: 14px;
  color: var(--app-text-secondary, #606266);
  white-space: nowrap;
}
.reval-positive {
  color: #e6a23c;
  font-weight: 600;
}
.reval-negative {
  color: #f56c6c;
  font-weight: 600;
}
.pager {
  margin-top: 10px;
  justify-content: flex-end;
}
.tip { font-size: 12px; color: #e6a23c; margin-top: 8px; }
.muted { color: #c0c4cc; }
</style>
