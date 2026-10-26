<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="sod-check">
    <!-- R5-D-034：SoD 检查页此前复用通用配置表格（无执行入口、列与返回体错位），改为独立页面 -->
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      <template #title>
        {{ $t('system.sod.hintPage') }}</template>
    </el-alert>

    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="color: #909399; font-size: 13px">{{ $t('system.sod.statConflicts') }}</div>
          <div :style="{ fontSize: '26px', fontWeight: 700, color: total > 0 ? '#f56c6c' : '#67c23a' }">{{ total }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="color: #909399; font-size: 13px">{{ $t('system.sod.statPeople') }}</div>
          <div style="font-size: 26px; font-weight: 700">{{ distinctUsers }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="color: #909399; font-size: 13px">{{ $t('system.sod.statCheckedAt') }}</div>
          <div style="font-size: 16px; font-weight: 600; margin-top: 6px">{{ detectTime || t('system.sod.stNotChecked') }}</div>
        </el-card>
      </el-col>
    </el-row>

    <div style="display: flex; gap: 8px; margin-bottom: 12px; align-items: center; flex-wrap: wrap">
      <el-button v-if="canScan" type="primary" :loading="running" @click="runCheck">{{ $t('system.sod.btnRunCheck') }}</el-button>
      <el-button :loading="loading" @click="load()">{{ $t('common.refresh') }}</el-button>
      <el-radio-group v-model="dimension" size="small" @change="() => load(1)">
        <el-radio-button value="ALL">{{ $t('system.sod.optAllDimensions') }}</el-radio-button>
        <el-radio-button value="ROLE">{{ $t('system.sod.dimPostHold') }}</el-radio-button>
        <el-radio-button value="PERM">{{ $t('system.sod.dimPermCombo') }}</el-radio-button>
      </el-radio-group>
      <el-input v-model="keyword" :placeholder="$t('system.sod.phSearchUser')" clearable style="width: 220px" @keyup.enter="load(1)" />
      <el-button @click="load(1)">{{ $t('common.search') }}</el-button>
      <span style="color: #909399; font-size: 12px; margin-left: auto">{{ $t('system.sod.hintCurrentCompany') }}</span>
    </div>

    <SkeletonList v-if="loading" :rows="8" :cols="6" />
    <el-table v-else :data="rows" size="small" border stripe :empty-text="$t('system.sod.emptyConflicts')">
      <el-table-column prop="userName" :label="$t('system.sod.colUser')" width="120" show-overflow-tooltip />
      <el-table-column prop="empNo" :label="$t('system.sod.colEmpNo')" width="110" show-overflow-tooltip />
      <el-table-column prop="roleName" :label="$t('system.sod.colConflictPosts')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="conflictDesc" :label="$t('system.sod.colConflictDesc')" min-width="240" show-overflow-tooltip />
      <el-table-column prop="severity" :label="$t('system.sod.colSeverity')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="severityTag(row.severity)" size="small">{{ row.severity || '—' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('system.sod.colDimension')" width="130" align="center">
        <template #default="{ row }">{{ dimensionLabel(row.dimension) }}</template>
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

const distinctUsers = computed(() => new Set(rows.value.map((r) => r.userId).filter(Boolean)).size)

function severityTag(v) {
  return v === '高' ? 'danger' : v === '中' ? 'warning' : 'info'
}
function dimensionLabel(v) {
  return v === 'ROLE' ? t('system.sod.dimPostHold') : v === 'PERM' ? t('system.sod.dimPermCombo') : '—'
}

function fillPage(d) {
  rows.value = d?.list || d?.records || d?.rows || []
  total.value = Number(d?.total || 0)
  detectTime.value = rows.value[0]?.detectTime || ''
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
    // 台账 R5-D-034：不把后端原文/错误码透出给业务用户
    ElMessage.error(t('system.sod.msgLoadFail'))
  } finally {
    loading.value = false
  }
}

async function runCheck() {
  running.value = true
  try {
    fillPage(await http.post('/tenant/compliance/sod/check/run', null, {
      params: { dimension: dimension.value, keyword: keyword.value, pageNum: pageNum.value, pageSize: pageSize.value },
      silent: true,
    }))
    ElMessage.success(total.value > 0 ? t('system.sod.msgFoundN', { v1: total.value }) : t('system.sod.msgNoneFound'))
  } catch {
    ElMessage.error(t('system.sod.msgCheckFail'))
  } finally {
    running.value = false
  }
}

onMounted(() => load(1))
</script>
