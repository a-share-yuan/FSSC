<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="sod-check">
    <!-- SoD 检查页不复用通用配置表格（无执行入口、列与返回体错位），改独立页面 -->
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      <template #title>
        {{ $t('system.sod.hintPage') }}</template>
    </el-alert>

    <!-- 分层计数：静态冲突 / 运行时连办 / 首节点豁免 分开显示，
         不再用一个红色 total 把「提交即办结」的正常轨迹报成冲突 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">{{ $t('system.sod.statStatic') }}</div>
          <div :style="{ fontSize: '26px', fontWeight: 700, color: summary.staticConflicts > 0 ? '#f56c6c' : '#67c23a' }">
            {{ summary.staticConflicts }}
          </div>
          <div class="stat-sub">{{ $t('system.sod.statStaticHint', { v1: summary.roleConflicts, v2: summary.permConflicts }) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">{{ $t('system.sod.statSerial') }}</div>
          <div :style="{ fontSize: '26px', fontWeight: 700, color: summary.runtimeSerial > 0 ? '#e6a23c' : '#67c23a' }">
            {{ summary.runtimeSerial }}
          </div>
          <div class="stat-sub">{{ $t('system.sod.statSerialHint', { v1: summary.runtimeSelfAudit }) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">{{ $t('system.sod.statExempt') }}</div>
          <div style="font-size: 26px; font-weight: 700; color: #909399">{{ summary.exemptedFirstNode }}</div>
          <div class="stat-sub">{{ $t('system.sod.statExemptHint') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">{{ $t('system.sod.statCheckedAt') }}</div>
          <div style="font-size: 15px; font-weight: 600; margin-top: 8px">
            {{ summary.detectTime || detectTime || t('system.sod.stNotChecked') }}
          </div>
          <div class="stat-sub">{{ $t('system.sod.statPeople') }} {{ distinctUsers }}</div>
        </el-card>
      </el-col>
    </el-row>

    <div style="display: flex; gap: 8px; margin-bottom: 12px; align-items: center; flex-wrap: wrap">
      <el-button v-if="canScan" type="primary" :loading="running" @click="runCheck">{{ $t('system.sod.btnRunCheck') }}</el-button>
      <el-button :loading="loading" @click="refreshAll()">{{ $t('common.refresh') }}</el-button>
      <el-radio-group v-model="dimension" size="small" @change="() => load(1)">
        <el-radio-button value="ALL">{{ $t('system.sod.optAllDimensions') }}</el-radio-button>
        <el-radio-button value="ROLE">{{ $t('system.sod.dimPostHold') }}</el-radio-button>
        <el-radio-button value="PERM">{{ $t('system.sod.dimPermCombo') }}</el-radio-button>
        <el-radio-button value="DUTY">{{ $t('system.sod.dimDuty') }}</el-radio-button>
      </el-radio-group>
      <el-input v-model="keyword" :placeholder="$t('system.sod.phSearchUser')" clearable style="width: 220px" @keyup.enter="load(1)" />
      <el-button @click="load(1)">{{ $t('common.search') }}</el-button>
      <span style="color: #909399; font-size: 12px; margin-left: auto">{{ $t('system.sod.hintCurrentCompany') }}</span>
    </div>

    <SkeletonList v-if="loading" :rows="8" :cols="6" />
    <el-table v-else :data="rows" size="small" border stripe :empty-text="$t('system.sod.emptyConflicts')">
      <el-table-column prop="userName" :label="$t('system.sod.colUser')" width="110" show-overflow-tooltip />
      <el-table-column prop="empNo" :label="$t('system.sod.colEmpNo')" width="100" show-overflow-tooltip />
      <el-table-column prop="roleName" :label="$t('system.sod.colConflictPosts')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="conflictDesc" :label="$t('system.sod.colConflictDesc')" min-width="240" show-overflow-tooltip />
      <el-table-column prop="severity" :label="$t('system.sod.colSeverity')" width="86" align="center">
        <template #default="{ row }">
          <el-tag :type="severityTag(row.severity)" size="small">{{ row.severity || '—' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('system.sod.colDimension')" width="140" align="center">
        <template #default="{ row }">
          <el-tag :type="dimensionTag(row.dimension)" size="small" effect="plain">{{ dimensionLabel(row.dimension) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detectTime" :label="$t('system.sod.colCheckedAt')" width="170" :formatter="(r, c, v) => formatDateTime(v)" />
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="load()"
      @size-change="load(1)"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../../api'
import { hasAnyPerm } from '../../utils/perm'
import SkeletonList from '../../components/SkeletonList.vue'
import { formatDateTime } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const canScan = hasAnyPerm(['system:sod:scan', 'system:sod:list'])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const running = ref(false)
const dimension = ref('ALL')
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(20)
const detectTime = ref('')

/** 分层计数（/check/summary）：不再用单一 total 混算三类 */
const summary = ref({
  staticConflicts: 0, roleConflicts: 0, permConflicts: 0,
  runtimeSelfAudit: 0, runtimeSerial: 0, exemptedFirstNode: 0, detectTime: '',
})

const distinctUsers = computed(() => new Set(rows.value.map((r) => r.userId).filter(Boolean)).size)

function severityTag(v) {
  return v === '高' ? 'danger' : v === '中' ? 'warning' : 'info'
}
function dimensionTag(v) {
  if (v === 'ROLE' || v === 'PERM') return 'danger'
  if (v === 'DUTY_SELF') return 'danger'
  if (v === 'DUTY_SERIAL') return 'warning'
  return 'info'
}
function dimensionLabel(v) {
  if (v === 'ROLE') return t('system.sod.dimPostHold')
  if (v === 'PERM') return t('system.sod.dimPermCombo')
  if (v === 'DUTY_SELF') return t('system.sod.dimDutySelf')
  if (v === 'DUTY_SERIAL') return t('system.sod.dimDutySerial')
  if (v === 'DUTY') return t('system.sod.dimDuty')
  return '—'
}

function fillPage(d) {
  rows.value = d?.list || d?.records || d?.rows || []
  total.value = Number(d?.total || 0)
  detectTime.value = rows.value[0]?.detectTime || ''
}

async function loadSummary() {
  try {
    const d = await http.get('/tenant/compliance/sod/check/summary', { silent: true })
    if (d && typeof d === 'object') summary.value = { ...summary.value, ...d }
  } catch {
    // 摘要失败不影响表格；卡片保留上一次读数
  }
}

async function load(page) {
  if (page) pageNum.value = page
  loading.value = true
  try {
    fillPage(await http.get('/tenant/compliance/sod/check/list', {
      params: { dimension: dimension.value, keyword: keyword.value, pageNum: pageNum.value, pageSize: pageSize.value },
      silent: true,
    }))
  } catch {
    // 台账 不把后端原文/错误码透出给业务用户
    ElMessage.error(t('system.sod.msgLoadFail'))
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([load(), loadSummary()])
}

async function runCheck() {
  running.value = true
  try {
    fillPage(await http.post('/tenant/compliance/sod/check/run', null, {
      params: { dimension: dimension.value, keyword: keyword.value, pageNum: pageNum.value, pageSize: pageSize.value },
      silent: true,
    }))
    await loadSummary()
    // 提示语改分层：静态冲突 / 连办 / 豁免 分别报，避免「22 条冲突」这种把正常轨迹算进去的说法
    ElMessage.success(t('system.sod.msgFoundLayered', {
      v1: summary.value.staticConflicts,
      v2: summary.value.runtimeSerial,
      v3: summary.value.exemptedFirstNode,
    }))
  } catch {
    ElMessage.error(t('system.sod.msgCheckFail'))
  } finally {
    running.value = false
  }
}

onMounted(() => refreshAll())
</script>

<style scoped>
.stat-label {
  color: #909399;
  font-size: 13px;
}
.stat-sub {
  color: #a8abb2;
  font-size: 12px;
  margin-top: 4px;
}
</style>
