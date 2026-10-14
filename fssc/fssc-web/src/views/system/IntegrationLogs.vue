<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="integration-logs">
    <!-- R5-D-040：失败告警横幅——全失败红牌/部分类别失败黄牌，先于数字呈现在首屏 -->
    <el-alert v-if="allFail" type="error" show-icon :closable="false" style="margin-bottom: 12px"
              title="全部调用失败：请检查外部网关连通性与接口配置" />
    <el-alert v-else-if="failCats.length" type="warning" show-icon :closable="false" style="margin-bottom: 12px"
              :title="`存在调用失败的接口类别：${failCatsText}`" />
    <!-- 统计卡片 -->
    <el-row :gutter="12" style="margin-bottom: 16px">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">总调用次数</div>
          <div class="stat-value">{{ stats.total || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">成功率</div>
          <div class="stat-value" :style="{ color: successRateColor }">{{ stats.successRate || '—' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">失败次数</div>
          <div class="stat-value" style="color: #f56c6c">{{ stats.failCount || 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">近24小时</div>
          <div class="stat-value">{{ stats.recent24h || 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选 -->
    <div style="display: flex; gap: 8px; margin-bottom: 12px; align-items: center">
      <el-select v-model="filter.category" placeholder="全部分类" clearable style="width: 180px" @change="loadData">
        <el-option v-for="(label, code) in EXT_INTERFACE_CATEGORY" :key="code" :label="label" :value="code" />
      </el-select>
      <el-select v-model="filter.success" placeholder="全部状态" clearable style="width: 140px" @change="loadData">
        <el-option label="成功" :value="1" />
        <el-option label="失败" :value="0" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="loadStats">刷新统计</el-button>
    </div>

    <!-- 调用日志表格 -->
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="tableData" v-else border stripe style="width: 100%" @row-dblclick="openRowDetail" empty-text="暂无数据">
      <el-table-column prop="createTime" label="时间" width="170" :formatter="(r, c, v) => formatDateTime(v)" />
      <el-table-column prop="category" label="分类" width="130">
        <template #default="{ row }">
          <el-tag size="small">{{ extCategoryLabel(row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="interfaceCode" label="接口编码" width="160" show-overflow-tooltip />
      <el-table-column prop="method" label="方法" width="70" />
      <el-table-column prop="requestUrl" label="请求地址" min-width="250" show-overflow-tooltip />
      <el-table-column prop="statusCode" label="状态码" width="80">
        <template #default="{ row }">
          <span :style="{ color: row.success ? '#67c23a' : '#f56c6c', fontWeight: 'bold' }">{{ row.statusCode || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时(ms)" width="100" sortable>
        <template #default="{ row }">{{ row.durationMs ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorMsg" style="color: #f56c6c">{{ row.errorMsg }}</span>
          <span v-else style="color: #67c23a">无异常</span>
        </template>
      </el-table-column>
      <el-table-column label="详情" width="80" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="showDetail(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="filter.pageNum"
      v-model:page-size="filter.pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="loadData"
      @size-change="loadData"
    />

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="调用详情" width="800px" destroy-on-close>
      <template v-if="detailRow">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="时间">{{ detailRow.createTime }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ extCategoryLabel(detailRow.category) }}</el-descriptions-item>
          <el-descriptions-item label="接口编码">{{ detailRow.interfaceCode }}</el-descriptions-item>
          <el-descriptions-item label="HTTP 方法">{{ detailRow.method }}</el-descriptions-item>
          <el-descriptions-item label="请求地址" :span="2">{{ detailRow.requestUrl }}</el-descriptions-item>
          <el-descriptions-item label="状态码">{{ detailRow.statusCode }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detailRow.durationMs }}ms</el-descriptions-item>
          <el-descriptions-item label="结果">
            <el-tag :type="detailRow.success ? 'success' : 'danger'" size="small">{{ detailRow.success ? '成功' : '失败' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="错误信息" v-if="detailRow.errorMsg">{{ detailRow.errorMsg }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detailRow.requestBody" style="margin-top: 16px">
          <div style="font-weight: 600; margin-bottom: 8px">请求体</div>
          <el-input type="textarea" :model-value="formatJson(detailRow.requestBody)" :rows="6" readonly />
        </div>
        <div v-if="detailRow.responseBody" style="margin-top: 16px">
          <div style="font-weight: 600; margin-bottom: 8px">响应体</div>
          <el-input type="textarea" :model-value="formatJson(detailRow.responseBody)" :rows="8" readonly />
        </div>
      </template>
    </el-dialog>
  </div>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { integrationApi } from '../../api'
import { formatDateTime } from '@/utils/format'
import { extCategoryLabel, EXT_INTERFACE_CATEGORY } from '@/constants/enums.js'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

// 双击行：调用已有详情查看函数
function openRowDetail(row) {
  showDetail(row)
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const stats = ref({})
const detailVisible = ref(false)
const detailRow = ref(null)

const filter = reactive({ pageNum: 1, pageSize: 20, category: '', success: '' })

const successRateColor = computed(() => {
  const r = stats.value.successRate
  if (!r || r === '—') return '#909399'
  const pct = parseFloat(r)
  if (pct >= 95) return '#67c23a'
  if (pct >= 80) return '#e6a23c'
  return '#f56c6c'
})

// R5-D-040：后端 stats 新增 byCategory（分类别 total/fail），前端汇总出全失败/部分失败两类告警
const failCats = computed(() => (stats.value.byCategory || [])
  .filter((r) => Number(r.fail) > 0)
  .map((r) => `${extCategoryLabel(r.category)}(${r.fail})`))
const allFail = computed(() => Number(stats.value.total || 0) > 0 && Number(stats.value.failCount || 0) >= Number(stats.value.total || 0))
const failCatsText = computed(() => failCats.value.join('、'))

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: filter.pageNum, pageSize: filter.pageSize }
    if (filter.category) params.category = filter.category
    if (filter.success !== '' && filter.success !== null) params.success = filter.success
    const r = await integrationApi.logPage(params)
    tableData.value = r.list || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await integrationApi.stats()
  } catch (e) { ElMessage.error(e.message) }
}

function showDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

function formatJson(str) {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

onMounted(() => { loadData(); loadStats() })
</script>

<style scoped>
.integration-logs { padding: 0; }
.stat-card { text-align: center; }
.stat-label { color: #909399; font-size: 13px; margin-bottom: 8px; }
.stat-value { font-size: 24px; font-weight: 700; color: #303133; }
</style>
