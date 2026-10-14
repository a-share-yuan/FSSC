<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="sod-check">
    <!-- R5-D-034：SoD 检查页此前复用通用配置表格（无执行入口、列与返回体错位），改为独立页面 -->
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      <template #title>
        职责分离检查：巡检本企业内「一人兼任互斥岗位」与「跨岗位累积出互斥操作权限」的人员组合，结果供内控复核取证。
      </template>
    </el-alert>

    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="color: #909399; font-size: 13px">冲突条数</div>
          <div :style="{ fontSize: '26px', fontWeight: 700, color: total > 0 ? '#f56c6c' : '#67c23a' }">{{ total }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="color: #909399; font-size: 13px">涉及人数</div>
          <div style="font-size: 26px; font-weight: 700">{{ distinctUsers }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="color: #909399; font-size: 13px">本次检查时间</div>
          <div style="font-size: 16px; font-weight: 600; margin-top: 6px">{{ detectTime || '尚未检查' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <div style="display: flex; gap: 8px; margin-bottom: 12px; align-items: center; flex-wrap: wrap">
      <el-button v-if="canScan" type="primary" :loading="running" @click="runCheck">执行检查</el-button>
      <el-button :loading="loading" @click="load()">刷新</el-button>
      <el-radio-group v-model="dimension" size="small" @change="() => load(1)">
        <el-radio-button value="ALL">全部维度</el-radio-button>
        <el-radio-button value="ROLE">岗位兼任</el-radio-button>
        <el-radio-button value="PERM">操作权限组合</el-radio-button>
      </el-radio-group>
      <el-input v-model="keyword" placeholder="按姓名 / 工号 / 岗位搜索" clearable style="width: 220px" @keyup.enter="load(1)" />
      <el-button @click="load(1)">查询</el-button>
      <span style="color: #909399; font-size: 12px; margin-left: auto">仅显示当前企业内的检查结果</span>
    </div>

    <SkeletonList v-if="loading" :rows="8" :cols="6" />
    <el-table v-else :data="rows" size="small" border stripe empty-text="暂无职责分离冲突，可点【执行检查】巡检">
      <el-table-column prop="userName" label="用户" width="120" show-overflow-tooltip />
      <el-table-column prop="empNo" label="工号" width="110" show-overflow-tooltip />
      <el-table-column prop="roleName" label="冲突岗位" min-width="200" show-overflow-tooltip />
      <el-table-column prop="conflictDesc" label="冲突描述" min-width="240" show-overflow-tooltip />
      <el-table-column prop="severity" label="严重度" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="severityTag(row.severity)" size="small">{{ row.severity || '—' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="判定维度" width="130" align="center">
        <template #default="{ row }">{{ dimensionLabel(row.dimension) }}</template>
      </el-table-column>
      <el-table-column prop="detectTime" label="检测时间" width="170" />
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
  return v === 'ROLE' ? '岗位兼任' : v === 'PERM' ? '操作权限组合' : '—'
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
    ElMessage.error('职责分离检查结果未能加载，请稍后重试')
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
    ElMessage.success(total.value > 0 ? `检查完成，发现 ${total.value} 条职责分离冲突` : '检查完成，未发现职责分离冲突')
  } catch {
    ElMessage.error('检查未能完成，请稍后重试')
  } finally {
    running.value = false
  }
}

onMounted(() => load(1))
</script>
