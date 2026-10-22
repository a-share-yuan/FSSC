<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>{{ $t('sharing.archive.pageTitle') }}</b>
        <el-radio-group v-model="tab" size="small" @change="load">
          <el-radio-button label="pending">{{ $t('sharing.archive.tabPending', { v1: pendingTotal }) }}</el-radio-button>
          <el-radio-button label="archived">{{ $t('sharing.archive.tabFiled') }}</el-radio-button>
        </el-radio-group>
        <el-input v-model="q.keyword" :placeholder="$t('sharing.common.phBillNoTitle')" clearable style="width: 160px" @keyup.enter="load" />
        <el-select v-if="tab === 'pending'" v-model="q.archiveStatus" size="small" style="width: 120px"
                   @change="() => { q.pageNum = 1; load() }">
          <el-option :label="$t('sharing.archive.labelUnfiled')" value="" />
          <el-option :label="$t('sharing.common.colArchived')" value="ARCHIVED" />
          <el-option :label="$t('sharing.common.colAny')" value="ALL" />
        </el-select>
        <el-button type="primary" size="small" @click="load">{{ $t('common.search') }}</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 13px">
          {{ $t('sharing.archive.hintFlow') }}</span>
      </div>
    </template>

    <!-- 待归档清单 -->
    <div v-if="tab === 'pending'">
      <div style="margin-bottom: 8px; display:flex; gap:8px; align-items:center; flex-wrap:wrap">
        <!-- D-DEC-046：归档执行面（POST /tenant/sharing/enhance/archive/do-archive）服务端要
             op:archive:create，而本页菜单按 op:archive:list 授权 ⇒ 档案岗"看得见、点得动、必失败"。
             可见性口径改为与接口一致：无执行码不渲染按钮，只留一句去向说明，避免误点后零反馈。 -->
        <el-button v-if="canArchive" type="primary" size="small" :disabled="!selected.length || q.archiveStatus !== ''"
                   :title="q.archiveStatus === '' ? '' : t('sharing.archive.hintOnlyUnarchived')"
                   @click="doArchive">{{ $t('sharing.archive.btnArchiveSel', { v1: selected.length }) }}</el-button>
        <span v-else style="color:#909399; font-size:12px">
          {{ $t('sharing.archive.hintNoArchivePerm') }}
          {{ $t('sharing.archive.txtOrVoucherFlow') }}
        </span>
        <el-button size="small" @click="viewSelected">{{ $t('sharing.archive.btnViewSelImages') }}</el-button>
      </div>
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
<el-table :data="pendingRows" size="small" stripe v-else max-height="460" @row-dblclick="openRowDetail"
                @selection-change="(s) => (selected = s.map((r) => r.id))" :empty-text="$t('common.empty')">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="billNo" :label="$t('field.docNo')" width="150"  sortable />
        <el-table-column prop="title" :label="$t('sharing.common.colTitle')" min-width="150" show-overflow-tooltip  sortable />
        <el-table-column :label="$t('field.type')" width="90">
          <template #default="{ row }">{{ billTypeLabel(row.billType) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" :label="$t('field.amount')" width="110" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column :label="$t('sharing.archive.colImage')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.imageStatus === 'COMPLETE' ? 'success' : 'warning'" size="small">{{ imageStatusLabel(row.imageStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approveTime" :label="$t('sharing.archive.colApproveTime')" width="160"  sortable :formatter="(r, c, v) => formatDateTime(v)" />
        <el-table-column :label="$t('sharing.archive.colArchStatus')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.archiveStatus === 'ARCHIVED' ? 'success' : 'info'" size="small">
              {{ row.archiveStatus === 'ARCHIVED' ? t('sharing.common.colArchived') : t('sharing.archive.labelUnfiled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('sharing.archive.colFilingTime')" width="180" sortable>
          <template #default="{ row }">
            {{ row.archiveTime || '—' }}
            <span v-if="row.archiveStatus === 'ARCHIVED' && !row.archiveBy" style="color: #909399; font-size: 12px">{{ $t('sharing.archive.hintAutoFiled') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('sharing.common.btnTrace') }}</el-button>
            <el-button link size="small" type="primary" @click="viewBillImages(row)">{{ $t('sharing.common.btnViewImages') }}</el-button>
      <el-button size="small" @click="exportCsv">{{ $t('sharing.common.btnExportCsv') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 10px; justify-content: flex-end" layout="total, prev, pager, next"
                     :total="pendingTotal" :page-size="q.pageSize" :current-page="q.pageNum" @current-change="(n) => { q.pageNum = n; load() }" />
    </div>

    <!-- 已归档档案 -->
    <div v-else>
      <SkeletonList v-if="loading" :rows="8" :cols="5" />
      <el-table :data="archiveRows" size="small" stripe v-else max-height="460" @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
        <!-- D-DEC-047／D-DEC-254 定案：档案号列绑 archiveNo（op_archive.archive_no，NOT NULL＋取号器唯一），
             不绑冗余列 archiveCode——该列在 184 的"编号镜像"上线前整列 NULL，绑上去就是"档案编码列全空"。 -->
        <el-table-column prop="archiveNo" :label="$t('sharing.archive.colArchiveNo')" width="180"  sortable />
        <el-table-column prop="title" :label="$t('sharing.common.colTitle')" min-width="150" show-overflow-tooltip  sortable />
        <el-table-column prop="refNo" :label="$t('sharing.archive.colBizNo')" width="150"  sortable />
        <el-table-column prop="archiveType" :label="$t('field.type')" width="80" :formatter="enumFormatter(ARCHIVE_TYPE)" sortable />
        <el-table-column prop="retentionYears" :label="$t('sharing.archive.colRetention')" width="90"  sortable />
        <el-table-column :label="$t('sharing.archive.colArchStatus')" width="110">
          <template #default="{ row }">
            <!-- D-DEC-182：判定列取真相源 archive_status（归档流程轴）。原取 status 镜像列，
                 而该列历史上被归档链路写过 'ARCHIVED' 这种另一轴的取值，档案板与本表互相打脸。 -->
            <el-tag :type="archTag(row.archiveStatus)" size="small">{{ archLabel(row.archiveStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('sharing.archive.colArchFlow')" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- D-DEC-185／D-DEC-028：这一列是本卷 FM_ARCHIVE 归档审批流自身的实例号（后端读侧富化），
                 与 row.procInstId（被归档那张单的审批流）不是一回事，两列并存在此才可核对"哪次审批定的稿"。 -->
            <span v-if="row.archiveProcInstId">{{ archFlowLabel(row.archiveFlowStatus) }} · {{ shortProc(row.archiveProcInstId) }}</span>
            <span v-else style="color:#909399">{{ $t('sharing.archive.labelNotStarted') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="filingTime" :label="$t('sharing.archive.colFilingTime')" width="160"  sortable :formatter="(r, c, v) => formatDateTime(v)" />
        <el-table-column :label="$t('common.operation')" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('sharing.common.btnTrace') }}</el-button>
            <el-button link size="small" type="primary" @click="viewArchive(row)">{{ $t('sharing.common.btnViewImages') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 10px; justify-content: flex-end" layout="total, prev, pager, next"
                     :total="archiveTotal" :page-size="a.pageSize" :current-page="a.pageNum" @current-change="(n) => { a.pageNum = n; load() }" />
    </div>

    <!-- 影像查看器 -->
    <FileViewer ref="fileViewer" />
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
</template>

<script setup>
import {onMounted, reactive, ref, computed} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showRowDetail, formatDateTime } from '@/utils/format'
import { opApi, billApi } from '../../api'
import { hasPerm } from '../../utils/perm'
import FileViewer from '../../components/FileViewer.vue'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { ARCHIVE_TYPE, billTypeLabel, imageStatusLabel, enumFormatter } from '@/constants/enums'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceBizType.value = tab.value === 'archived' ? 'FM_ARCHIVE' : (row.billType || null)
  traceVisible.value = true
}

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: t('field.detail') })
}

const tab = ref('pending')
const loading = ref(false)
const pendingRows = ref([])
const pendingTotal = ref(0)
const archiveRows = ref([])
const archiveTotal = ref(0)
const selected = ref([])
const fileViewer = ref(null)
const q = reactive({ pageNum: 1, pageSize: 20, keyword: '', archiveStatus: '' })
const a = reactive({ pageNum: 1, pageSize: 20, keyword: '' })
// D-DEC-046：与后端 /tenant/sharing/enhance/archive/do-archive 的 @RequiresPerm("op:archive:create") 同码
const canArchive = hasPerm('op:archive:create')

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }

// D-DEC-182：归档流程轴词表（与后端 op_archive.archive_status 列注释、
// ArchiveBoard.vue 的 archStatusLabel 同一套，三处必须一起改）
const ARCH_STATUS_LABEL = computed(() => ({ PENDING: t('sharing.archive.labelPending'), ARCHIVING: t('sharing.archive.labelArchiving'), ARCHIVED: t('sharing.common.colArchived'), DISPOSED: t('sharing.archive.labelDestroyed') }))
const archLabel = computed(() => (s) => ARCH_STATUS_LABEL.value[s] || s || t('sharing.archive.labelUnknown'))
const archTag = (s) => (s === 'ARCHIVED' ? 'success' : s === 'DISPOSED' ? 'danger' : s === 'ARCHIVING' ? 'warning' : 'info')

// D-DEC-185／D-DEC-028：归档审批流（FM_ARCHIVE）自身状态与实例号
const ARCH_FLOW_LABEL = computed(() => ({ RUNNING: t('status.approving'), APPROVED: t('field.passTag'), REJECTED: t('status.rejected'), CANCELLED: t('status.void'), TERMINATED: t('sharing.archive.labelTerminated') }))
const archFlowLabel = (s) => ARCH_FLOW_LABEL.value[s] || s || '—'
const shortProc = (p) => (p && p.length > 8 ? p.slice(0, 8) : (p || '—'))

async function load() {
  loading.value = true
  try {
    if (tab.value === 'pending') {
      const r = await opApi.archivePending({ ...q })
      pendingRows.value = r.list || []
      pendingTotal.value = r.total || 0
    } else {
      const r = await opApi.archivePage({ ...a })
      archiveRows.value = r.list || []
      archiveTotal.value = r.total || 0
    }
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function doArchive() {
  if (!selected.value.length) return
  // 双保险：按钮已按 op:archive:create 前置隐藏，若因数据/角色变化仍走到这里，
  // 就在发起请求前如实说明缺哪张码，不再让服务端 4030 空转一趟（D-DEC-046）
  if (!canArchive) { ElMessage.warning(t('sharing.archive.msgNoPerm')); return }
  try {
    await ElMessageBox.confirm(t('sharing.archive.confirmArchive', { v1: selected.value.length }), t('sharing.archive.titleConfirm'), { type: 'warning' })
  } catch { return } // 用户取消：不发请求、不报错
  try {
    const n = await opApi.archiveDo(selected.value)
    ElMessage.success(t('sharing.archive.msgDone', { v1: n ?? 0 }))
    selected.value = []
    load()
  } catch (e) {
    // 失败必须透出：原实现在 e.message 为空时整条 catch 静默吞掉（HTTP 200 + 零提示）。
    // 勾选保留，便于补齐权限/条件后原地重试。
    ElMessage.error(t('sharing.archive.msgFail', { v1: e?.message || t('sharing.archive.txtNoReason') }))
  }
}

/** 查看单据影像/附件（附件 fileId + 影像页 fileId） */
async function viewBillImages(row) {
  try {
    const fileIds = []
    const d = await billApi.detail(row.id)
    ;(d?.attaches || []).forEach((x) => x.fileId && fileIds.push(x.fileId))
    if (!fileIds.length) { ElMessage.info(t('sharing.common.msgNoDocImages')); return }
    fileViewer.value?.open(fileIds, t('sharing.common.titleDocImages') + (row.billNo || row.id))
  } catch (e) { ElMessage.error(e.message) }
}

/** 查看所选单据影像（合并全部 fileId） */
async function viewSelected() {
  const ids = selected.value || []
  if (!ids.length) return
  const fileIds = []
  for (const id of ids) {
    try {
      const d = await billApi.detail(id)
      ;(d?.attaches || []).forEach((x) => x.fileId && fileIds.push(x.fileId))
    } catch (e) { /* 跳过 */ }
  }
  if (!fileIds.length) { ElMessage.info(t('sharing.archive.msgNoSelImages')); return }
  fileViewer.value?.open(fileIds, t('sharing.archive.titleSelDocImages'))
}

/** 查看已归档档案影像（档案 fileId + 关联单据附件） */
async function viewArchive(row) {
  const fileIds = []
  if (row.fileId) fileIds.push(row.fileId)
  if (row.refId) {
    try {
      const d = await billApi.detail(row.refId)
      ;(d?.attaches || []).forEach((x) => x.fileId && !fileIds.includes(x.fileId) && fileIds.push(x.fileId))
    } catch (e) { /* 跳过 */ }
  }
  if (!fileIds.length) { ElMessage.info(t('sharing.archive.msgNoArchiveImage')); return }
  fileViewer.value?.open(fileIds, t('sharing.archive.titleArchiveImages') + (row.archiveNo || row.title || ''))
}

onMounted(load)

function exportCsv() {
  if (!archiveRows.value.length) { ElMessage.warning(t('sharing.common.msgNothingToExport')); return }
  const cols = [{ prop: 'archiveNo', label: t('sharing.archive.colArchiveNo') }, { prop: 'title', label: t('sharing.common.colTitle') }, { prop: 'refNo', label: t('sharing.archive.colBizNo') }, { prop: 'archiveType', label: t('field.type') }, { prop: 'retentionYears', label: t('sharing.archive.colRetention') }, { prop: 'filingTime', label: t('sharing.archive.colFilingTime') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = archiveRows.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `ArchivePage_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('sharing.common.msgCsvExported'))
}

</script>
