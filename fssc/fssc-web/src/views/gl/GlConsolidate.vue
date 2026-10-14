<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>合并报表底稿</b>
        <el-date-picker v-model="f.period" type="month" placeholder="所属期" value-format="YYYYMM" clearable style="width: 140px" />
        <el-select v-model="f.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已合并" value="CONSOLIDATED" />
        </el-select>
        <el-input v-model="f.keyword" placeholder="底稿编号/公司" clearable style="width: 170px" />
        <el-button type="primary" size="small" @click="load(1)">查询</el-button>
        <el-button size="small" @click="createVisible = true">新建底稿</el-button>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" v-else size="small" empty-text="暂无数据">
      <el-table-column prop="reportNo" label="底稿编号" width="170" sortable />
      <el-table-column prop="period" label="所属期" width="90" sortable />
      <el-table-column label="合并范围（公司）" min-width="200" show-overflow-tooltip>
        <!-- R5-D-150：公司 ID 串按 orgApi.companyTree 词表映射为公司名（取不到回落裸 ID，不造词） -->
        <template #default="{ row }">{{ companyRangeText(row) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CONSOLIDATED' ? 'success' : 'info'" size="small">{{ row.status === 'CONSOLIDATED' ? '已合并' : '草稿' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160">
        <template #default="{ row }">{{ String(row.createTime || '').replace('T', ' ').slice(0, 16) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" size="small" type="primary" @click="runMerge(row.period)">执行合并</el-button>
          <el-button v-if="row.status === 'DRAFT'" size="small" @click="openAdjust(row)">调整分录</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="f.pageSize"
      :current-page="f.pageNum"
      @current-change="load"
    />

    <!-- 新建底稿 -->
    <el-dialog v-model="createVisible" title="新建合并底稿" width="460px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="会计期间" required>
          <el-date-picker v-model="form.period" type="month" value-format="YYYYMM" placeholder="所属期间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="合并范围公司">
          <el-input v-model="form.companyIds" placeholder="公司ID，逗号分隔，如 1,33" />
        </el-form-item>
        <el-form-item label="抵消分录初稿">
          <el-input v-model="form.rawRows" type="textarea" :rows="3" placeholder="可选：初始抵消分录（留空由汇总合并自动生成）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 调整分录 -->
    <el-dialog v-model="adjustVisible" title="合并调整分录" width="520px">
      <el-form label-width="90px">
        <el-form-item label="底稿编号"><el-input :model-value="adjustRow?.reportNo" disabled /></el-form-item>
        <el-form-item label="调整分录">
          <el-input v-model="adjustRaw" type="textarea" :rows="5" placeholder="调整分录（rawRows，按后端约定格式）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" @click="doAdjust">保存调整</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import SkeletonList from '../../components/SkeletonList.vue'
import { glexApi, orgApi } from '../../api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const f = ref({ pageNum: 1, pageSize: 20, period: '', status: '', keyword: '' })
const createVisible = ref(false)
const form = ref({ period: '', companyIds: '', rawRows: '' })
const adjustVisible = ref(false)
const adjustRow = ref(null)
const adjustRaw = ref('')

async function load(pageNum) {
  if (pageNum) f.value.pageNum = pageNum
  loading.value = true
  try {
    const d = await glexApi.consolidatePage(f.value.pageNum, f.value.pageSize, f.value.period, f.value.status, f.value.keyword)
    rows.value = (d && (d.records || d.list)) || []
    total.value = (d && d.total) || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function doCreate() {
  if (!form.value.period) { ElMessage.warning('请选择会计期间'); return }
  try {
    await glexApi.consolidateCreate({ period: form.value.period, companyIds: form.value.companyIds, rawRows: form.value.rawRows })
    ElMessage.success('底稿已创建（草稿）；执行合并将发起合并报表作业')
    createVisible.value = false
    load(1)
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  }
}

async function runMerge(period) {
  try {
    await glexApi.consolidateRun(period)
    ElMessage.success('汇总合并完成（期间 ' + period + '）')
    load()
  } catch (e) {
    ElMessage.error(e.message || '执行合并失败')
  }
}

function openAdjust(row) {
  adjustRow.value = row
  adjustRaw.value = row.rawRows || ''
  adjustVisible.value = true
}

async function doAdjust() {
  try {
    await glexApi.consolidateAdjust(adjustRow.value.id, adjustRaw.value)
    ElMessage.success('调整分录已保存')
    adjustVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

// R5-D-150：合并范围「公司 ID 串 → 公司名」映射（词表＝orgApi.companyTree 摊平，与 BankAccount/MasterDataTree 同机制；
// 取不到时回落裸 ID，不造词）
const companyMap = ref({})
async function loadCompanyMap() {
  try {
    const tree = (await orgApi.companyTree({ silent: true })) || []
    const walk = (arr) => (arr || []).forEach((n) => {
      if (!n) return
      companyMap.value[n.id] = n.companyName || n.name || ''
      if (n.children?.length) walk(n.children)
    })
    walk(tree)
  } catch (e) { /* 词表取不到时列回落裸 ID，不阻断底稿列表 */ }
}
function companyRangeText(row) {
  const raw = String(row?.companyIds ?? '').trim()
  if (!raw) return '—'
  return raw.split(',').map((s) => s.trim()).filter(Boolean)
    .map((id) => companyMap.value[id] || id).join('、')
}

onMounted(() => { load(1); loadCompanyMap() })
</script>
