<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-dialog v-model="visible" :title="$t('ExportPreview.main.Report')" width="900px" :destroy-on-close="true" class="export-dialog">
    <div class="export-toolbar">
      <el-select v-model="exportFormat" size="small" style="width: 120px">
        <el-option label="Excel (.xlsx)" value="xlsx" />
        <el-option label="PDF (.pdf)" value="pdf" />
        <el-option label="CSV (.csv)" value="csv" />
      </el-select>
      <el-select v-model="pageSize" size="small" style="width: 100px" @change="renderPreview">
        <el-option :label="$t('ExportPreview.main.A4')" value="a4-h" />
        <el-option :label="$t('ExportPreview.main.A42')" value="a4-v" />
        <el-option :label="$t('ExportPreview.main.A3')" value="a3-h" />
      </el-select>
      <el-checkbox v-model="includeHeader">包含表头</el-checkbox>
      <el-checkbox v-model="includeSummary">包含合计行</el-checkbox>
      <el-button type="primary" size="small" @click="doExport" :loading="exporting">
        <el-icon><Download /></el-icon>{{ $t('common.export') }}</el-button>
    </div>

    <div class="preview-container" v-loading="exporting">
      <!-- 纸张预览 -->
      <div class="paper" :class="pageSize" ref="paperRef">
        <div class="paper-header">
          <div class="paper-title">{{ reportTitle }}</div>
          <div class="paper-meta">
            <span>导出时间: {{ exportTime }}</span>
            <span>制表人: {{ operator }}</span>
          </div>
        </div>
        <table class="preview-table" v-if="columns.length > 0">
          <thead v-if="includeHeader">
            <tr>
              <th v-for="col in columns" :key="col.prop">{{ col.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in previewRows" :key="i">
              <td v-for="col in columns" :key="col.prop">
                {{ row[col.prop] ?? '-' }}
              </td>
            </tr>
          </tbody>
          <tfoot v-if="includeSummary && summaryRow">
            <tr>
              <td v-for="col in columns" :key="col.prop">
                {{ summaryRow[col.prop] ?? '' }}
              </td>
            </tr>
          </tfoot>
        </table>
        <div v-else class="no-data">
          <el-icon><DocumentRemove /></el-icon>
          <p>暂无可导出数据</p>
        </div>
        <div class="paper-footer">
          第 1 / {{ totalPages }} 页 · FSSC 财务共享中心
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Download, DocumentRemove } from '@element-plus/icons-vue'
import { store } from '../api'
import { i18n } from '../i18n'

const props = defineProps({
  modelValue: Boolean,
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  title: { type: String, default: i18n.global.t('ExportPreview.main.Reports') },
})

const emit = defineEmits(['update:modelValue', 'export'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const exportFormat = ref('xlsx')
const pageSize = ref('a4-h')
const includeHeader = ref(true)
const includeSummary = ref(true)
const exporting = ref(false)
const previewRows = ref([])
const totalPages = ref(1)

const reportTitle = computed(() => props.title)
const operator = computed(() => store.user?.realName || store.user?.username || '管理员')
const exportTime = computed(() => new Date().toLocaleString('zh-CN'))

const summaryRow = computed(() => {
  if (props.rows.length === 0) return null
  const numericCols = props.columns.filter(c => c.numeric)
  const row = {}
  numericCols.forEach(col => {
    row[col.prop] = '合计: ' + props.rows.reduce((s, r) => s + (Number(r[col.prop]) || 0), 0).toFixed(2)
  })
  return row
})

function renderPreview() {
  const maxRows = pageSize.value.startsWith('a3') ? 30 : 20
  previewRows.value = props.rows.slice(0, maxRows)
  totalPages.value = Math.max(1, Math.ceil(props.rows.length / maxRows))
}

function doExport() {
  exporting.value = true
  // 简单 CSV 导出实现
  setTimeout(() => {
    if (exportFormat.value === 'csv') {
      const headers = props.columns.map(c => c.label)
      const lines = [headers.join(',')]
      props.rows.forEach(r => {
        lines.push(props.columns.map(c => r[c.prop] ?? '').join(','))
      })
      const blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = `${props.title}_${new Date().toISOString().slice(0, 10)}.csv`
      link.click()
    }
    exporting.value = false
    emit('export', { format: exportFormat.value, count: props.rows.length })
  }, 500)
}

watch(() => props.modelValue, (v) => {
  if (v) renderPreview()
})

onMounted(() => {
  if (props.modelValue) renderPreview()
})
</script>

<style scoped>
.export-toolbar {
  display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
  flex-wrap: wrap;
}
.preview-container {
  max-height: 500px; overflow-y: auto; background: #f0f2f5; padding: 16px;
  border-radius: 4px;
}
.paper {
  background: white; margin: 0 auto; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}
.paper.a4-h { width: 100%; min-height: 600px; }
.paper.a4-v { width: 70%; min-height: 800px; }
.paper.a3-h { width: 100%; min-height: 800px; }
.paper-header { text-align: center; margin-bottom: 16px; border-bottom: 2px solid #409eff; padding-bottom: 8px; }
.paper-title { font-size: 18px; font-weight: 700; }
.paper-meta { font-size: 12px; color: #909399; margin-top: 4px; display: flex; justify-content: space-between; }
.preview-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.preview-table th { background: var(--app-bg, #f5f7fa); border: 1px solid #ebeef5; padding: 6px 8px; text-align: left; font-weight: 600; }
.preview-table td { border: 1px solid #ebeef5; padding: 4px 8px; }
.preview-table tfoot td { background: var(--app-hover, #fafafa); font-weight: 600; border-top: 2px solid #dcdfe6; }
.paper-footer { text-align: center; margin-top: 16px; font-size: 11px; color: #c0c4cc; }
.no-data { text-align: center; padding: 40px; color: #c0c4cc; }
.no-data p { margin-top: 8px; font-size: 13px; }
</style>
