<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="image-manager">
    <!-- Toolbar -->
    <el-card shadow="never" body-style="padding:12px 16px">
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
        <b style="font-size:15px">影像管理</b>
        <el-select v-model="q.bizType" placeholder="业务类型" clearable style="width:130px" @change="load">
          <el-option label="费用报销" value="EXP_CLAIM" />
          <el-option label="事前申请" value="APPLY" />
          <el-option label="对公付款" value="EXP_PAY" />
          <el-option label="借款" value="LOAN" />
          <el-option label="合同" value="CONTRACT" />
          <el-option label="发票" value="INVOICE" />
        </el-select>
        <el-input v-model="q.keyword" placeholder="批次号/业务单号" clearable style="width:180px" @keyup.enter="load" />
        <el-select v-model="q.status" placeholder="状态" clearable style="width:110px" @change="load">
          <el-option label="已上传" value="UPLOADED" />
          <el-option label="已质检" value="CHECKED" />
        </el-select>
        <el-button type="primary" size="small" @click="load">查询</el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="handleUpload"
          multiple
          accept=".jpg,.jpeg,.png,.bmp,.webp,.pdf,.ofd,.xml"
        >
          <el-button type="success" size="small" :loading="uploading">+ 上传影像</el-button>
        </el-upload>
        <span style="margin-left:auto;color:#909399;font-size:12px">
          缩略图模式 | 点击批次查看页面 | 点击页面预览大图 + OCR 标注
        </span>
      </div>
    </el-card>

    <!-- Batch Thumbnail Grid -->
    <SkeletonDetail v-if="loading" :rows="6" />
    <div v-else style="margin-top:12px">
      <el-row v-if="batches.length" :gutter="12">
        <el-col v-for="b in batches" :key="b.id" :xs="12" :sm="8" :md="6" :lg="4" :xl="3" style="margin-bottom:12px">
          <el-card
            shadow="hover"
            body-style="padding:0"
            class="batch-card"
            @click="openBatch(b)"
          >
            <!-- Thumbnail preview (first page image or placeholder) -->
            <div class="thumb-wrap">
              <el-image
                v-if="b._thumbUrl"
                :src="b._thumbUrl"
                fit="cover"
                style="width:100%;height:140px;border-radius:4px 4px 0 0"
                :preview-src-list="b._thumbUrl ? [b._thumbUrl] : []"
                @click.stop
              >
                <template #error>
                  <div class="thumb-placeholder">
                    <el-icon size="32"><Picture /></el-icon>
                    <span>无法预览</span>
                  </div>
                </template>
                <template #placeholder>
                  <div class="thumb-placeholder">
                    <el-icon size="24" class="is-loading"><Loading /></el-icon>
                  </div>
                </template>
              </el-image>
              <div v-else class="thumb-placeholder">
                <el-icon size="32"><Picture /></el-icon>
                <span>{{ b.docCount || 0 }} 页</span>
              </div>
              <!-- Status badge -->
              <div class="status-badge" :class="statusClass(b.status)">
                {{ statusLabel(b.status) }}
              </div>
              <!-- OCR badge -->
              <div v-if="b._ocrDone" class="ocr-badge" :class="ocrBadgeClass(b)">
                OCR {{ ocrStatusLabel(b._ocrStatus) }}
              </div>
            </div>
            <!-- Batch info -->
            <div style="padding:8px 10px">
              <div style="font-size:13px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ b.batchNo || '#' + b.id }}</div>
              <div style="font-size:12px;color:#909399;margin-top:2px">
                {{ bizTypeLabel(b.bizType) }} | {{ b.docCount || 0 }} 页
              </div>
              <div style="font-size:11px;color:#c0c4cc;margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                {{ b.bizNo || '—' }}
              </div>
            </div>
            <!-- Actions -->
            <div class="card-actions" @click.stop>
              <el-button link size="small" type="primary" @click="openBatch(b)">查看</el-button>
              <el-button link size="small" type="warning" @click="runOcr(b)" :loading="b._ocrLoading">OCR</el-button>
              <el-button link size="small" type="danger" @click="delBatch(b)">删除</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无影像批次，点击「上传影像」创建" />
    </div>

    <!-- Pagination -->
    <el-pagination
      v-if="total > 0"
      style="margin-top:10px;justify-content:flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="q.pageSize"
      :current-page="q.pageNum"
      @current-change="(n) => { q.pageNum = n; load() }"
    />

    <!-- Page Thumbnails Drawer (batch detail) -->
    <el-drawer
      v-model="drawerVisible"
      :title="currentBatch ? ('批次详情：' + (currentBatch.batchNo || '#' + currentBatch.id)) : ''"
      direction="rtl"
      size="620px"
      destroy-on-close
    >
      <SkeletonDetail v-if="pageLoading" :rows="6" />
      <div v-else>
        <div v-if="pages.length" style="display:flex;flex-wrap:wrap;gap:10px;padding:4px">
          <div
            v-for="p in pages"
            :key="p.id"
            class="page-thumb"
            @click="previewPage(p)"
          >
            <el-image
              v-if="p._url"
              :src="p._url"
              fit="cover"
              style="width:120px;height:160px;border-radius:4px;cursor:pointer;border:1px solid var(--el-border-color-lighter)"
            >
              <template #error>
                <div class="page-placeholder">
                  <el-icon size="24"><Document /></el-icon>
                  <span>{{ p.pageNo }}</span>
                </div>
              </template>
              <template #placeholder>
                <div class="page-placeholder">
                  <el-icon size="20" class="is-loading"><Loading /></el-icon>
                </div>
              </template>
            </el-image>
            <div v-else class="page-placeholder" style="width:120px;height:160px">
              <el-icon size="24"><Document /></el-icon>
              <span>P{{ p.pageNo }}</span>
            </div>
            <!-- OCR status dot -->
            <div class="page-ocr-dot" :class="ocrDotClass(p.ocrStatus)" :title="'OCR: ' + ocrStatusLabel(p.ocrStatus)" />
            <div style="text-align:center;font-size:11px;color:#909399;margin-top:2px">P{{ p.pageNo }}</div>
          </div>
        </div>
        <el-empty v-else description="批次无页面" />
      </div>
    </el-drawer>

    <!-- Full Image Preview with OCR Annotation Overlay -->
    <el-dialog
      v-model="previewVisible"
      :title="previewTitle"
      width="900px"
      top="3vh"
      append-to-body
      destroy-on-close
    >
      <div v-if="currentPage" class="preview-container">
        <el-row :gutter="16">
          <!-- Image area -->
          <el-col :span="14">
            <div class="image-area">
              <el-image
                v-if="currentPage._url"
                :src="currentPage._url"
                fit="contain"
                style="width:100%;max-height:560px;border-radius:6px"
                :preview-src-list="currentPage._url ? [currentPage._url] : []"
              >
                <template #error>
                  <div class="image-placeholder">
                    <el-icon size="48"><Picture /></el-icon>
                    <span>影像加载失败</span>
                  </div>
                </template>
              </el-image>
              <div v-else class="image-placeholder">
                <el-icon size="48"><Picture /></el-icon>
                <span>无影像文件</span>
              </div>
            </div>
          </el-col>
          <!-- OCR Annotation Panel -->
          <el-col :span="10">
            <div class="ocr-panel">
              <div class="ocr-panel-header">
                <b>OCR 标注信息</b>
                <el-tag :type="ocrTagType(currentPage.ocrStatus)" size="small">
                  {{ ocrStatusLabel(currentPage.ocrStatus) }}
                </el-tag>
              </div>

              <!-- Parsed OCR fields -->
              <template v-if="ocrData">
                <div class="ocr-field-grid">
                  <div v-for="f in ocrFields" :key="f.key" class="ocr-field" :class="fieldClass(f)">
                    <div class="field-label">{{ f.label }}</div>
                    <div class="field-value">{{ f.value || '—' }}</div>
                  </div>
                </div>

                <!-- Amount consistency check -->
                <div v-if="ocrData.amount || ocrData.netAmount || ocrData.taxAmount" class="amount-check" :class="amountCheckClass">
                  <el-icon><CircleCheck v-if="amountConsistent" /><Warning v-else /></el-icon>
                  <span>{{ amountConsistent ? '票面金额自洽' : '票面金额不自洽' }}</span>
                  <span style="margin-left:auto;font-size:11px;color:#909399">
                    {{ ocrData.netAmount || '?' }} + {{ ocrData.taxAmount || '?' }} = {{ ocrData.amount || '?' }}
                  </span>
                </div>

                <!-- Raw text -->
                <div v-if="ocrData.rawText" class="raw-text-section">
                  <div style="font-size:12px;font-weight:600;margin-bottom:4px">识别原文</div>
                  <div class="raw-text">{{ ocrData.rawText }}</div>
                </div>
              </template>

              <!-- No OCR data -->
              <div v-else class="ocr-empty">
                <el-icon size="36" color="#dcdfe6"><DocumentRemove /></el-icon>
                <p v-if="currentPage.ocrStatus === 'NONE'">尚未执行 OCR 识别</p>
                <p v-else-if="currentPage.ocrStatus === 'PENDING'">OCR 识别中...</p>
                <p v-else-if="currentPage.ocrStatus === 'FAILED'">
                  OCR 识别失败
                  <!-- D-DEC-009：这一页只剩"识别失败"四个字时，人不知道下一步能干什么 -->
                  <span class="ocr-fail-exit">可改传 XML/OFD 版式文件重新上传，或在「发票管理 › 发票补录」手工录入发票要素</span>
                </p>
                <p v-else>无 OCR 数据</p>
                <el-button
                  v-if="currentPage.ocrStatus === 'NONE' || currentPage.ocrStatus === 'FAILED'"
                  type="warning"
                  size="small"
                  @click="runOcr(currentBatch, currentPage)"
                  :loading="ocrRunning"
                >重新识别</el-button>
              </div>

              <!-- Page meta -->
              <div class="page-meta">
                <div>页面编号: P{{ currentPage.pageNo }}</div>
                <div>验证状态: {{ verifyLabel(currentPage.verifyStatus) }}</div>
                <div v-if="currentPage.remark">备注: {{ currentPage.remark }}</div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { bizTypeLabel } from '@/constants/enums'
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, Document, Loading, CircleCheck, Warning, DocumentRemove } from '@element-plus/icons-vue'
import { imageApi, fileApi } from '../../api'
import { invoiceTypeLabel } from '../../constants/invoiceType'
import SkeletonDetail from '../../components/SkeletonDetail.vue'

const loading = ref(false)
const pageLoading = ref(false)
const uploading = ref(false)
const ocrRunning = ref(false)
const drawerVisible = ref(false)
const previewVisible = ref(false)

const batches = ref([])
const total = ref(0)
const pages = ref([])
const currentBatch = ref(null)
const currentPage = ref(null)

const q = reactive({ pageNum: 1, pageSize: 24, bizType: '', keyword: '', status: '' })


function statusLabel(s) { return { UPLOADED: '已上传', CHECKED: '已质检' }[s] || s || '—' }
function statusClass(s) { return s === 'CHECKED' ? 'st-ok' : 'st-upload' }
function ocrStatusLabel(s) { return { NONE: '未识别', PENDING: '识别中', SUCCESS: '已识别', NEED_REVIEW: '待复核', FAILED: '识别失败' }[s] || s || '—' }
function ocrTagType(s) { return s === 'SUCCESS' ? 'success' : s === 'NEED_REVIEW' ? 'warning' : s === 'FAILED' ? 'danger' : s === 'PENDING' ? 'info' : 'info' }
function ocrDotClass(s) { return s === 'SUCCESS' ? 'dot-ok' : s === 'NEED_REVIEW' ? 'dot-warn' : s === 'FAILED' ? 'dot-fail' : s === 'PENDING' ? 'dot-pending' : 'dot-none' }
function ocrBadgeClass(b) { return b._ocrStatus === 'SUCCESS' ? 'ocr-ok' : b._ocrStatus === 'NEED_REVIEW' ? 'ocr-warn' : b._ocrStatus === 'FAILED' ? 'ocr-fail' : 'ocr-none' }
function verifyLabel(s) { return { NONE: '未验证', PASS: '通过', REJECT: '驳回' }[s] || s || '—' }

const IMG_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'jfif']

async function getFileUrl(fileId) {
  if (!fileId) return null
  try {
    const detail = await fileApi.detail(fileId) || {}
    const name = detail.fileName || String(fileId)
    const suffix = (detail.fileSuffix || (name.split('.').pop() || '')).toLowerCase()
    if (!IMG_EXTS.includes(suffix)) return null
    const token = await fileApi.createToken(fileId)
    // R5-D-223：令牌签发异常时服务端曾回 data:null，此处绝不能拼 download?token=null
    // ——浏览器只会得到 400「令牌无效」，预览静默"加载失败"且用户无从重试；显式失败并给可重试提示。
    if (!token || String(token).trim() === '' || String(token) === 'null') {
      ElMessage.error('预览令牌签发失败，请稍后重试')
      return null
    }
    return '/api/tenant/system/file/download?token=' + token
  } catch { return null }
}

async function load() {
  loading.value = true
  try {
    const r = await imageApi.taskPage({ ...q })
    const list = r.list || []
    // Load thumbnail for each batch (first page image)
    list.forEach(b => { b._thumbUrl = null; b._ocrStatus = 'NONE'; b._ocrDone = false; b._ocrLoading = false })
    batches.value = list
    total.value = r.total || 0
    // Async load thumbnails
    for (const b of list) {
      try {
        const pgs = await imageApi.pages(b.id)
        if (pgs && pgs.length) {
          // Check overall OCR status
          const ocrStatuses = pgs.map(p => p.ocrStatus)
          if (ocrStatuses.every(s => s === 'SUCCESS' || s === 'NEED_REVIEW')) {
            b._ocrStatus = ocrStatuses.includes('NEED_REVIEW') ? 'NEED_REVIEW' : 'SUCCESS'
            b._ocrDone = true
          } else if (ocrStatuses.some(s => s === 'FAILED')) {
            b._ocrStatus = 'FAILED'; b._ocrDone = true
          } else if (ocrStatuses.some(s => s === 'PENDING')) {
            b._ocrStatus = 'PENDING'; b._ocrDone = true
          }
          // Get first image page for thumbnail
          for (const p of pgs) {
            if (p.fileId) {
              const url = await getFileUrl(p.fileId)
              if (url) { b._thumbUrl = url; break }
            }
          }
        }
      } catch (e) { ElMessage.error(e.message) }
    }
    // Trigger reactivity
    batches.value = [...list]
  } catch (e) {
    ElMessage.error(e.message)
  } finally { loading.value = false }
}

async function openBatch(b) {
  currentBatch.value = b
  drawerVisible.value = true
  pageLoading.value = true
  try {
    const pgs = await imageApi.pages(b.id) || []
    // Load image URLs for pages
    for (const p of pgs) {
      p._url = null
      if (p.fileId) {
        p._url = await getFileUrl(p.fileId)
      }
    }
    pages.value = pgs
  } catch (e) {
    ElMessage.error(e.message)
  } finally { pageLoading.value = false }
}

const previewTitle = computed(() => {
  if (!currentBatch.value || !currentPage.value) return '影像预览'
  return `影像预览：${currentBatch.value.batchNo || ''} - P${currentPage.value.pageNo}`
})

// OCR parsed data from ocrText JSON
const ocrData = computed(() => {
  if (!currentPage.value || !currentPage.value.ocrText) return null
  try {
    return JSON.parse(currentPage.value.ocrText)
  } catch { return null }
})

const ocrFields = computed(() => {
  if (!ocrData.value) return []
  const d = ocrData.value
  return [
    { key: 'invoiceNo', label: '发票号码', value: d.invoiceNo },
    { key: 'invoiceCode', label: '发票代码', value: d.invoiceCode },
    { key: 'invoiceType', label: '发票类型', value: invoiceTypeLabel(d.invoiceType) },
    { key: 'invoiceDate', label: '开票日期', value: d.invoiceDate },
    { key: 'amount', label: '价税合计', value: d.amount != null ? '¥' + d.amount : null },
    { key: 'netAmount', label: '不含税额', value: d.netAmount != null ? '¥' + d.netAmount : null },
    { key: 'taxAmount', label: '税额', value: d.taxAmount != null ? '¥' + d.taxAmount : null },
    { key: 'sellerName', label: '销方名称', value: d.sellerName },
    { key: 'sellerTaxNo', label: '销方税号', value: d.sellerTaxNo },
    { key: 'buyerName', label: '购方名称', value: d.buyerName },
    { key: 'buyerTaxNo', label: '购方税号', value: d.buyerTaxNo },
  ]
})

function fieldClass(f) {
  if (f.value && f.value !== '—') return 'field-hit'
  return 'field-miss'
}

const amountConsistent = computed(() => {
  if (!ocrData.value) return true
  const d = ocrData.value
  if (!d.amount || !d.netAmount || !d.taxAmount) return true
  const sum = Number(d.netAmount) + Number(d.taxAmount)
  return Math.abs(sum - Number(d.amount)) < 0.02
})

const amountCheckClass = computed(() => amountConsistent.value ? 'amt-ok' : 'amt-fail')

function previewPage(p) {
  currentPage.value = p
  previewVisible.value = true
}

async function runOcr(b, page) {
  ocrRunning.value = true
  try {
    const count = await imageApi.ocr(b.id)
    // D-DEC-009：整批报绿会把"有几页没解析出来"盖掉——批次只要存在 FAILED 页就按警示报，
    // 并把两条真路说清楚（换版式文件重传 / 手工录入发票要素）。
    let failed = 0
    try { failed = ((await imageApi.pages(b.id)) || []).filter((p) => p.ocrStatus === 'FAILED').length } catch { /* 页态取不到时沿用原提示 */ }
    if (failed) {
      ElMessage.warning(`${failed} 页未解析出票面（本批入池 ${count} 张）：可改传 XML/OFD 版式文件重新上传，`
        + '或在「发票管理 › 发票补录」手工录入发票要素')
    } else {
      ElMessage.success(`OCR 识别完成，创建发票 ${count} 张`)
    }
    // Reload pages if drawer is open
    if (drawerVisible.value && currentBatch.value && currentBatch.value.id === b.id) {
      await openBatch(b)
    }
    // Update batch status
    b.status = 'CHECKED'
    b._ocrDone = true
    b._ocrStatus = 'SUCCESS'
    // Reload batch list thumbnails
    load()
  } catch (e) {
    ElMessage.error('OCR 识别失败：' + e.message)
  } finally { ocrRunning.value = false }
}

async function delBatch(b) {
  try {
    await ElMessageBox.confirm(`删除影像批次 ${b.batchNo || '#' + b.id}？`, '删除确认', { type: 'warning' })
    await imageApi.batchDelete(b.id)
    ElMessage.success('已删除')
    load()
  } catch { /* cancel */ }
}

async function handleUpload(file) {
  uploading.value = true
  try {
    // 1. Upload file
    const fileRes = await fileApi.upload(file, 'INVOICE', null, 'BUSINESS')
    const fileId = fileRes?.id
    if (!fileId) throw new Error('文件上传失败')
    // 2. Create batch with uploaded file
    const fd = new FormData()
    fd.append('files', file)
    fd.append('bizType', q.bizType || 'INVOICE')
    fd.append('bizNo', q.keyword || '')
    await imageApi.batchUpload(fd)
    ElMessage.success('影像上传成功')
    load()
  } catch (e) {
    ElMessage.error('上传失败：' + e.message)
  } finally { uploading.value = false }
  return false // prevent el-upload default behavior
}

onMounted(() => { load() })
</script>

<style scoped>
.batch-card {
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.batch-card:hover {
  transform: translateY(-2px);
}
.thumb-wrap {
  position: relative;
  width: 100%;
  height: 140px;
  background: #f5f7fa;
  overflow: hidden;
}
.thumb-placeholder {
  width: 100%;
  height: 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #c0c4cc;
  font-size: 12px;
}
.status-badge {
  position: absolute;
  top: 6px;
  right: 6px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
}
.st-ok { background: #67c23a; }
.st-upload { background: #409eff; }

.ocr-badge {
  position: absolute;
  bottom: 6px;
  left: 6px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
}
.ocr-ok { background: #67c23a; }
.ocr-warn { background: #e6a23c; }
.ocr-fail { background: #f56c6c; }
.ocr-none { display: none; }

.card-actions {
  padding: 4px 10px 8px;
  display: flex;
  gap: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.page-thumb {
  position: relative;
  cursor: pointer;
}
.page-placeholder {
  width: 120px;
  height: 160px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #c0c4cc;
  font-size: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  border: 1px solid var(--el-border-color-lighter);
}
.page-ocr-dot {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 0 2px rgba(0,0,0,0.2);
}
.dot-ok { background: #67c23a; }
.dot-warn { background: #e6a23c; }
.dot-fail { background: #f56c6c; }
.dot-pending { background: #909399; }
.dot-none { background: #dcdfe6; }

.preview-container {
  min-height: 400px;
}
.image-area {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}
.image-placeholder {
  width: 100%;
  min-height: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #c0c4cc;
}

.ocr-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px;
  background: #fff;
}
.ocr-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.ocr-field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.ocr-field {
  padding: 6px 8px;
  border-radius: 4px;
  border: 1px solid var(--el-border-color-lighter);
  background: #fafafa;
}
.field-hit {
  border-color: #b3e19d;
  background: #f0f9eb;
}
.field-miss {
  border-color: var(--el-border-color-lighter);
  background: #f5f5f5;
}
.field-label {
  font-size: 11px;
  color: #909399;
  margin-bottom: 2px;
}
.field-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.amount-check {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  margin-top: 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.amt-ok {
  background: #f0f9eb;
  color: #67c23a;
}
.amt-fail {
  background: #fef0f0;
  color: #f56c6c;
}

.raw-text-section {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.raw-text {
  font-size: 11px;
  color: #909399;
  background: #f5f7fa;
  padding: 6px 8px;
  border-radius: 4px;
  max-height: 100px;
  overflow-y: auto;
  word-break: break-all;
  line-height: 1.5;
}

.ocr-empty {
  text-align: center;
  padding: 24px 0;
  color: #c0c4cc;
}
.ocr-empty p {
  margin: 8px 0;
  font-size: 13px;
}
.ocr-fail-exit {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #e6a23c;
}

.page-meta {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  color: #909399;
  line-height: 1.8;
}
</style>
