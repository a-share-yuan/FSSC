<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <div style="display: flex; gap: 8px; margin-bottom: 10px; align-items: center; flex-wrap: wrap">
      <el-button type="primary" @click="loadTaxDiff">刷新税会差异</el-button>
      <el-button @click="doExport">导出 CSV</el-button>
      <span style="color: #909399; font-size: 12px">会计折旧与税法折旧差异汇总，支撑企业所得税 A105080「固定资产折旧纳税调整」</span>
    </div>
    <SkeletonList v-if="taxDiffLoading" :rows="8" :cols="5" />
    <el-table :data="taxDiffs" size="small" border stripe v-else empty-text="暂无税会差异数据" v-loading="taxDiffLoading" @row-dblclick="openRowDetail">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="assetNo" label="资产编号" width="140" sortable />
      <el-table-column prop="assetName" label="资产名称" min-width="150" show-overflow-tooltip sortable />
      <el-table-column label="原值" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.originalAmount) }}</template>
      </el-table-column>
      <el-table-column prop="accountMonths" label="会计年限(月)" width="110" align="center" sortable />
      <el-table-column prop="taxMonths" label="税法年限(月)" width="110" align="center" sortable />
      <el-table-column label="会计月折旧" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.accountMonthlyDepr) }}</template>
      </el-table-column>
      <el-table-column label="税法月折旧" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.taxMonthlyDepr) }}</template>
      </el-table-column>
      <el-table-column label="差异金额" width="120" align="right">
        <template #default="{ row }">{{ fmt(row.diffAmount) }}</template>
      </el-table-column>
      <el-table-column label="调整方向" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="Number(row.diffAmount) > 0" type="danger" size="small">调增</el-tag>
          <el-tag v-else-if="Number(row.diffAmount) < 0" type="success" size="small">调减</el-tag>
          <el-tag v-else type="info" size="small">无差异</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, prev, pager, next, jumper"
      style="margin-top: 12px; justify-content: flex-end;"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script setup>
import { ref, inject, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { showRowDetail } from '@/utils/format'
import { http } from '../../request'
import { exportCsv } from '../../utils/csv'
import SkeletonList from '../../components/SkeletonList.vue'

const { fmt } = inject('assetShared')

const taxDiffs = ref([])
const taxDiffLoading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function loadTaxDiff() {
  taxDiffLoading.value = true
  try {
    const res = await http.get('/tenant/asset/tax-diff', { params: { pageNum: pageNum.value, pageSize: pageSize.value } })
    taxDiffs.value = res?.list || res?.records || []
    total.value = res?.total ?? res?.totalCount ?? taxDiffs.value.length
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    taxDiffLoading.value = false
  }
}

function handleSizeChange(val) {
  pageSize.value = val
  pageNum.value = 1
  loadTaxDiff()
}

function handleCurrentChange(val) {
  pageNum.value = val
  loadTaxDiff()
}

function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

// 41号审查 P1-9：导出当前页税会差异
function doExport() {
  exportCsv(taxDiffs.value, [
    { prop: 'assetNo', label: '资产编号' }, { prop: 'assetName', label: '资产名称' },
    { prop: 'originalAmount', label: '原值' }, { prop: 'accountMonths', label: '会计年限(月)' },
    { prop: 'taxMonths', label: '税法年限(月)' }, { prop: 'accountMonthlyDepr', label: '会计月折旧' },
    { prop: 'taxMonthlyDepr', label: '税法月折旧' }, { prop: 'diffAmount', label: '差异金额' },
  ], 'TaxDiff')
}

onMounted(() => { loadTaxDiff() })

defineExpose({ loadTaxDiff })
</script>
