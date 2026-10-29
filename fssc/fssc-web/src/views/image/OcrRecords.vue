<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>OCR识别记录</b>
        <span style="color:#909399; font-size:12px">OCR 结构化票面识别结果 —— 发票号/购销方/价税明细/票面自洽校验</span>
        <div style="margin-left:auto; display:flex; gap:8px; align-items:center">
          <el-select v-if="canPickBatch" v-model="q.batchId" placeholder="按批次筛选" clearable filterable style="width:220px" size="small" @change="search">
            <el-option v-for="b in batchOptions" :key="b.id" :label="b.batchNo || ('#' + b.id)" :value="b.id" />
          </el-select>
          <el-input v-model="q.invoiceNo" placeholder="发票号" clearable style="width:160px" size="small" @keyup.enter="search" />
          <el-button size="small" type="primary" @click="search">查询</el-button>
          <el-button size="small" @click="reset">重置</el-button>
          <el-button size="small" @click="doExport">导出</el-button>
        </div>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="10" :cols="8" />
    <el-table v-else :data="pagedRecords" size="small" border stripe>
      <el-table-column label="序号" width="60" align="center">
        <template #default="{ $index }">{{ $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="batchId" label="批次" width="90" sortable>
        <template #default="{ row }">{{ row.batchNo || (row.batchId ? `批次#${row.batchId}` : '—') }}</template>
      </el-table-column>
      <el-table-column prop="imgFileId" label="影像页" width="100">
        <template #default="{ row }">{{ row.fileName || (row.imgFileId ? `页#${row.imgFileId}` : '—') }}</template>
      </el-table-column>
      <el-table-column prop="invoiceNo" label="发票号" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.invoiceNo || '—' }}</template>
      </el-table-column>
      <el-table-column prop="invoiceType" label="类型" width="120">
        <template #default="{ row }">{{ invoiceTypeLabel(row.invoiceType) }}</template>
      </el-table-column>
      <el-table-column prop="invoiceDate" label="开票日期" width="110" />
      <el-table-column label="金额（价税合计）" width="140" align="right">
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="不含税" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.netAmount) }}</template>
      </el-table-column>
      <el-table-column label="税额" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.taxAmount) }}</template>
      </el-table-column>
      <el-table-column prop="sellerName" label="销方" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.sellerName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="buyerName" label="购方" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.buyerName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="channelName" label="识别通道" width="110" align="center">
        <template #default="{ row }">{{ ocrChannelLabel(row.channelName) || '—' }}</template>
      </el-table-column>
      <!-- 识别失败若不落本表 ⇒ 记录页对一次失败恒 0 行、无从复核。
           现失败也有一行（recognizeStatus=FAILED），本列区分成功/失败，失败原因见下一列。 -->
      <el-table-column label="识别结果" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.recognizeStatus === 'FAILED'" type="danger" size="small">失败</el-tag>
          <el-tag v-else type="success" size="small">成功</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.recognizeStatus === 'FAILED' ? (row.failReason || '—') : '—' }}</template>
      </el-table-column>
      <el-table-column label="票面自洽" width="90" align="center">
        <template #default="{ row }">
          <!-- 失败行没有票面，自洽位恒 0，直译成「超差」会被读成"金额对不上" ⇒ 显示 — -->
          <el-tag v-if="row.recognizeStatus === 'FAILED'" type="info" size="small">—</el-tag>
          <el-tag v-else-if="row.consistent === 1" type="success" size="small">通过</el-tag>
          <el-tag v-else-if="row.consistent === 0" type="danger" size="small">超差</el-tag>
          <el-tag v-else type="info" size="small">—</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="识别时间" width="150" sortable prop="createdAt">
        <template #default="{ row }">{{ shortTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openCompare(row)">对照</el-button>
          <el-button v-if="needReRecognize(row)" link size="small" type="warning" :loading="reRecognizing === row.id" @click="reRecognize(row)">重识别</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TablePager :pager="pager" @change="load" />

    <!-- 明细抽屉：原始识别文本 -->
    <el-drawer v-model="rawVisible" title="OCR 原始识别结果" size="45%">
      <pre v-if="rawText" style="white-space:pre-wrap; font-size:12px; background:#f5f7fa; padding:12px; border-radius:6px">{{ rawText }}</pre>
      <el-empty v-else description="无原始文本" />
    </el-drawer>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
// ElMessageBox 用于重识别二次确认
import { ElMessage, ElMessageBox } from 'element-plus'
import { imageApi, fileApi } from '../../api'
import { invoiceTypeLabel } from '../../constants/invoiceType'
import { ocrChannelLabel } from '@/constants/enums.js'
import { exportCsv } from '../../utils/csv'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import SkeletonList from '../../components/SkeletonList.vue'
import { hasPerm } from '@/utils/perm'

const loading = ref(false)
const records = ref([])
const q = reactive({ batchId: '', invoiceNo: '' })

// 批次下拉需要 image:batch:list；无该码时退回手输，不拿 403 换一个「看起来没数据」的下拉
const canPickBatch = hasPerm('image:batch:list')
const batchOptions = ref([])
async function loadBatches() {
  if (!canPickBatch) return
  try { batchOptions.value = (await imageApi.batchList({})) || [] } catch { batchOptions.value = [] }
}

// 列表页必须 server 分页（sizes + jumper）
const pager = useTablePager({ mode: 'server', storageKey: 'ocr_records' })
const { pagedData: pagedRecords } = pager

function load() {
  return pager.load(async ({ pageNum, pageSize }) => {
    const params = { pageNum, pageSize }
    if (q.batchId) params.batchId = q.batchId
    if (q.invoiceNo) params.invoiceNo = q.invoiceNo
    const d = await imageApi.ocrRecordPage(params)
    records.value = d.list || []
    return { list: records.value, total: d.total || 0 }
  }).catch((e) => ElMessage.error(e.message))
}
function search() { pager.resetPage(); load() }
function reset() { q.batchId = ''; q.invoiceNo = ''; pager.resetPage(); load() }

const rawVisible = ref(false)
const rawText = ref('')
const compareImgUrl = ref('')
const compareRow = ref(null)
const compareLoading = ref(false)
const reRecognizing = ref(null)

// 识别失败/超差/无文本 → 可重识别(对接影像服务回源重识别能力)
function needReRecognize(row) {
  return row.consistent === 0 || !row.rawText
}

async function openCompare(row) {
  compareRow.value = row
  rawText.value = row.rawText || ''
  compareImgUrl.value = ''
  rawVisible.value = true
  compareLoading.value = true
  try {
    compareImgUrl.value = await getFileUrl(row.fileId || row.imgFileId)
  } finally { compareLoading.value = false }
}

async function reRecognize(row) {
  const fileId = row.fileId || row.imgFileId
  if (!fileId) return ElMessage.warning('该记录无关联附件,无法回源重识别')
  await ElMessageBox.confirm('确认对该附件回源重新识别?将覆盖更新本条识别记录。', '回源重识别', { type: 'warning' })
  reRecognizing.value = row.id
  try {
    await imageApi.ocrByFile(fileId)
    ElMessage.success('重识别完成')
    search()
  } catch (e) { ElMessage.error(e.message) } finally { reRecognizing.value = null }
}

// 附件原图地址(与影像管理页同一规则:file detail → 图片后缀校验 → 下载 token)
const IMG_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg', 'jfif']
async function getFileUrl(fileId) {
  if (!fileId) return null
  try {
    const detail = await fileApi.detail(fileId) || {}
    const name = detail.fileName || String(fileId)
    const suffix = (detail.fileSuffix || (name.split('.').pop() || '')).toLowerCase()
    if (!IMG_EXTS.includes(suffix)) return null
    const token = await fileApi.createToken(fileId)
    // 令牌签发异常时服务端曾回 data:null，此处绝不能拼 download?token=null
    // ——浏览器只会得到 400「令牌无效」，预览静默"加载失败"且用户无从重试；显式失败并给可重试提示。
    if (!token || String(token).trim() === '' || String(token) === 'null') {
      ElMessage.error('预览令牌签发失败，请稍后重试')
      return null
    }
    return '/api/tenant/system/file/download?token=' + token
  } catch { return null }
}

// 41号审查 导出当前查询结果（全列）
function doExport() {
  exportCsv(records.value, [
    { prop: 'id', label: 'ID' }, { prop: 'batchId', label: '批次' }, { prop: 'imgFileId', label: '页ID' },
    { prop: 'invoiceNo', label: '发票号' }, { prop: 'invoiceType', label: '类型' }, { prop: 'invoiceDate', label: '开票日期' },
    { prop: 'amount', label: '价税合计' }, { prop: 'netAmount', label: '不含税' }, { prop: 'taxAmount', label: '税额' },
    { prop: 'sellerName', label: '销方' }, { prop: 'buyerName', label: '购方' }, { prop: 'channelName', label: '识别通道' },
    { prop: 'consistent', label: '票面自洽(1通过/0超差)' }, { prop: 'createdAt', label: '识别时间' },
  ], 'OcrRecords')
}

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
function shortTime(t) {
  if (!t) return ''
  try { return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) } catch { return t }
}
onMounted(() => { load(); loadBatches() })
</script>

<style scoped>
.compare-wrap { display: flex; gap: 16px; }
.compare-pane { flex: 1; min-width: 0; }
.pane-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; color: #303133; }
</style>
