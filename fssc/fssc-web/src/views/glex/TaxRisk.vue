<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->
<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>税务风险指标</b>
        <el-select v-model="f.riskLevel" placeholder="风险等级" clearable style="width: 140px" @change="load(1)">
          <el-option label="高风险" value="HIGH" />
          <el-option label="预警" value="WARNING" />
          <el-option label="关注" value="ATTENTION" />
          <el-option label="正常" value="NORMAL" />
        </el-select>
        <el-checkbox v-model="f.unresolvedOnly" @change="load(1)">仅未处理</el-checkbox>
        <el-date-picker v-model="period" type="month" placeholder="属期" value-format="YYYYMM" style="width: 130px" />
        <el-button size="small" :loading="computing" @click="computeBurden">计算税负率</el-button>
        <span style="margin-left: auto; font-size: 13px; color: #606266">
          本期未处理：高 {{ summary.high || 0 }} · 预警 {{ summary.warning || 0 }} · 关注 {{ summary.attention || 0 }} · 合计 {{ summary.total || 0 }}
        </span>
      </div>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
      title="只有「税负率」是系统自动计算（数据源与税负分析报表同一条 SQL）；其余指标按业务判断人工登记。" />

    <SkeletonList v-if="loading" :rows="6" :cols="5" />
    <el-table v-else :data="rows" size="small" empty-text="暂无风险指标">
      <el-table-column prop="indicatorName" label="指标" min-width="160" show-overflow-tooltip />
      <el-table-column prop="riskType" label="类型" width="150" />
      <el-table-column prop="period" label="属期" width="90" />
      <el-table-column label="阈值/实际" width="160" align="right">
        <template #default="{ row }">{{ fmt(row.thresholdValue) }} / {{ fmt(row.actualValue) }}</template>
      </el-table-column>
      <el-table-column prop="riskScore" label="评分" width="80" align="center" />
      <el-table-column label="等级" width="100">
        <template #default="{ row }">
          <el-tag :type="levelTag(row.riskLevel)" size="small">{{ levelName(row.riskLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="处理" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.resolved === 1" type="success" size="small">已处理</el-tag>
          <el-button v-else-if="can('gl:taxrisk:resolve')" link type="primary" size="small"
            @click="resolve(row)">处理</el-button>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
      :total="total" :page-size="f.pageSize" :current-page="f.pageNum" @current-change="load" />
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taxApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { hasPerm } from '../../utils/perm'

const f = reactive({ riskLevel: null, unresolvedOnly: false, pageNum: 1, pageSize: 20 })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const computing = ref(false)
const summary = reactive({ high: 0, warning: 0, attention: 0, total: 0 })
// 属期不再默认「系统当月」：沙盘/补账场景下当月往往不是业务期间（2026-11 vs 202610），
// 默认当月会让指标落到错期间。改为必选，选什么就落什么。
const period = ref('')

function can(c) { return hasPerm(c) }
function fmt(v) {
  if (v === null || v === undefined || v === '') return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function levelName(v) {
  return { HIGH: '高风险', WARNING: '预警', ATTENTION: '关注', NORMAL: '正常' }[v] || v
}
function levelTag(v) {
  return { HIGH: 'danger', WARNING: 'warning', ATTENTION: '', NORMAL: 'success' }[v] || 'info'
}

async function load(page) {
  if (page) f.pageNum = page
  loading.value = true
  try {
    const r = await taxApi.riskPage({ ...f })
    rows.value = r?.list || r?.records || []
    total.value = r?.total || 0
    const s = await taxApi.riskSummary(period.value)
    Object.assign(summary, s || {})
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function computeBurden() {
  if (!period.value) { ElMessage.warning('请先选择要计算的属期（如 202610）'); return }
  computing.value = true
  try {
    const row = await taxApi.riskComputeBurden(period.value)
    if (row) ElMessage.success(`税负率 ${row.actualValue}%，判定 ${levelName(row.riskLevel)}`)
    else ElMessage.warning('本期无销项收入或报表无数据，未生成指标；该属期旧指标已作废')
    load(1)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    computing.value = false
  }
}

async function resolve(row) {
  const { value } = await ElMessageBox.prompt('请填写处理说明', '处理风险', {
    inputPattern: /\S+/, inputErrorMessage: '说明不能为空',
  })
  try {
    await taxApi.riskResolve(row.id, value)
    ElMessage.success('已处理')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => load())
</script>
