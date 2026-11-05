<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="precheck-stats">
    <el-card shadow="never">
      <div class="hd">
        <span class="ttl">{{ $t('sharing.precheck.ttl') }}</span>
        <div class="ops">
          <el-radio-group v-model="days" size="small" @change="load">
            <el-radio-button :value="7">{{ $t('sharing.precheck.d7') }}</el-radio-button>
            <el-radio-button :value="30">{{ $t('sharing.precheck.d30') }}</el-radio-button>
            <el-radio-button :value="90">{{ $t('sharing.precheck.d90') }}</el-radio-button>
          </el-radio-group>
          <el-button size="small" :loading="loading" @click="load">{{ $t('common.query') }}</el-button>
        </div>
      </div>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px">
        <template #title>{{ $t('sharing.precheck.tip') }}</template>
      </el-alert>

      <el-row :gutter="12" class="cards">
        <el-col :span="5"><div class="kpi"><div class="kpi-n">{{ stats.blockedBills ?? 0 }}</div><div class="kpi-l">{{ $t('sharing.precheck.kpiBlockedBills') }}</div></div></el-col>
        <el-col :span="5"><div class="kpi"><div class="kpi-n">{{ stats.blockedTimes ?? 0 }}</div><div class="kpi-l">{{ $t('sharing.precheck.kpiBlockedTimes') }}</div></div></el-col>
        <el-col :span="5"><div class="kpi"><div class="kpi-n">{{ stats.missRate?.backTotal ?? 0 }}</div><div class="kpi-l">{{ $t('sharing.precheck.kpiBack') }}</div></div></el-col>
        <el-col :span="4"><div class="kpi"><div class="kpi-n warn">{{ stats.missRate?.missed ?? 0 }}</div><div class="kpi-l">{{ $t('sharing.precheck.kpiMissed') }}</div></div></el-col>
        <el-col :span="5">
          <div class="kpi">
            <div class="kpi-n" :class="rateClass">{{ rateText }}</div>
            <div class="kpi-l">{{ $t('sharing.precheck.kpiRate') }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <div class="sec-title">{{ $t('sharing.precheck.secByRule') }}</div>
      <el-table :data="stats.byRule || []" size="small" border empty-text="—">
        <el-table-column prop="ruleCode" :label="$t('sharing.precheck.colRule')" min-width="180" />
        <el-table-column prop="blocks" :label="$t('sharing.precheck.colBlocks')" width="110" align="right" sortable />
        <el-table-column prop="bills" :label="$t('sharing.precheck.colBills')" width="110" align="right" sortable />
        <el-table-column :label="$t('sharing.precheck.colLast')" width="180">
          <template #default="{ row }">{{ row.lastAt ? formatDateTime(row.lastAt) : '—' }}</template>
        </el-table-column>
      </el-table>
      <div class="hint">{{ $t('sharing.precheck.hintByRule') }}</div>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <div class="sec-title">{{ $t('sharing.precheck.secMiss') }}</div>
      <el-table :data="stats.missRate?.samples || []" size="small" border empty-text="—">
        <el-table-column prop="billNo" :label="$t('sharing.common.colDocNoFull')" min-width="160" />
        <el-table-column :label="$t('sharing.quality.colCheckTime')" width="170">
          <template #default="{ row }">{{ row.checkTime ? formatDateTime(row.checkTime) : '—' }}</template>
        </el-table-column>
        <el-table-column :label="$t('sharing.quality.labViolation')" min-width="150">
          <template #default="{ row }">{{ row.violationReasonName || row.violationReasonCode || '—' }}</template>
        </el-table-column>
        <el-table-column prop="score" :label="$t('sharing.quality.colScore')" width="80" align="center" />
        <el-table-column prop="deductReason" :label="$t('sharing.quality.colDeduct')" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="hint">{{ $t('sharing.precheck.hintMiss') }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { complianceApi } from '../../api'
import { formatDateTime } from '@/utils/format'

const days = ref(30)
const loading = ref(false)
const stats = ref({})

const rateText = computed(() => {
  const r = stats.value.missRate?.rate
  return r == null ? '—' : (r * 100).toFixed(1) + '%'
})
const rateClass = computed(() => {
  const r = stats.value.missRate?.rate
  if (r == null) return ''
  return r >= 0.3 ? 'bad' : r >= 0.1 ? 'warn' : 'good'
})

async function load() {
  loading.value = true
  try {
    stats.value = (await complianceApi.precheckStats(days.value)) || {}
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.hd { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.ttl { font-size: 15px; font-weight: 600; }
.ops { display: flex; gap: 8px; align-items: center; }
.cards { margin-top: 4px; }
.kpi { background: var(--el-fill-color-light, #f5f7fa); border-radius: 6px; padding: 14px 12px; text-align: center; }
.kpi-n { font-size: 22px; font-weight: 700; line-height: 1.2; }
.kpi-n.warn { color: #e6a23c; }
.kpi-n.bad { color: #f56c6c; }
.kpi-n.good { color: #67c23a; }
.kpi-l { font-size: 12px; color: var(--el-text-color-secondary, #909399); margin-top: 4px; }
.sec-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.hint { font-size: 12px; color: var(--el-text-color-secondary, #909399); margin-top: 6px; line-height: 1.6; }
</style>
