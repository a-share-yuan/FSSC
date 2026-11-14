<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center">
        <b>{{ $t('sharing.taskWork.pageTitle') }}</b>
        <span style="color: #909399; font-size: 13px">{{ $t('sharing.taskWork.hintSub') }}</span>
      </div>
    </template>

    <div style="margin-bottom: 8px">
      <el-button size="small" @click="exportCsv">{{ $t('sharing.common.btnExportCsv') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">{{ $t('sharing.common.btnExportSelected', { v1: selRows.length }) }}</el-button>
    </div>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" size="small" stripe v-else max-height="520" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" :label="$t('field.docNo')" width="140"  sortable />
      <el-table-column :label="$t('field.type')" width="70">
        <template #default="{ row }">{{ billTypeLabel(row.billType) }}</template>
      </el-table-column>
      <el-table-column prop="title" :label="$t('sharing.common.colTitle')" min-width="140" show-overflow-tooltip  sortable />
      <el-table-column prop="amount" :label="$t('field.amount')" width="100" align="right" sortable>
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column :label="$t('sharing.common.colWorkItem')" width="90">
        <template #default="{ row }">{{ taskTypeLabel(row.taskType) }}</template>
      </el-table-column>
      <!-- 认领时间统一走 formatDateTime：直出后端原始串（ISO 带 T）会与页面其余时间列格式不一致 -->
      <el-table-column prop="receiveTime" :label="$t('sharing.common.colClaimTime')" width="160" sortable :formatter="(r, c, v) => formatDateTime(v)" />
      <el-table-column prop="remark" :label="$t('common.remark')" min-width="120" show-overflow-tooltip  sortable />
      <el-table-column :label="$t('common.operation')" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('sharing.common.btnTrace') }}</el-button>
          <!-- 凭证面按 sharing:voucher:list 前置办结。该接口无权限时服务端只回
               HTTP 200 + 业务码 4030，按钮照旧亮着、点了没抽屉＝"看得见点得动永远失败"，
               故无权限账号直接不渲染本入口（与同页 canGenerateVoucher 同一规则）。 -->
          <el-button v-if="canVoucherList" link size="small" type="primary" @click="openVoucher(row)">{{ $t('sharing.taskWork.btnVoucherEdit') }}</el-button>
          <el-button link size="small" type="primary" @click="viewBillImages(row)">{{ $t('sharing.common.btnViewImages') }}</el-button>
          <el-button link size="small" type="warning" @click="openReturn(row)">{{ $t('sharing.taskWork.btnReturn') }}</el-button>
          <el-button v-permission="'sharing:task:finish'" link size="small" type="success" @click="finish(row)">{{ $t('field.complete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <TablePager :pager="pager" @change="load" />

    <el-empty v-if="!loading && !rows.length" :description="$t('sharing.taskWork.emptyNoClaimed')" />

    <!-- 凭证预览 + 分录修改 -->
    <el-dialog v-model="voucherVis" :title="$t('sharing.taskWork.titleVoucherEdit')" width="780px" destroy-on-close>
      <el-alert v-if="voucherInfo" :title="$t('sharing.taskWork.titleVoucherBar', { v1: voucherInfo.voucherNo, v2: voucherInfo.status === BillStatus.POSTED ? t('sharing.taskWork.msgPosted') : t('sharing.taskWork.txtNotPosted'), v3: fmt(voucherInfo.debitTotal), v4: fmt(voucherInfo.creditTotal) })"
        :type="voucherInfo.status === BillStatus.POSTED ? 'info' : 'warning'" :closable="false" show-icon style="margin-bottom: 8px" />
      <el-table :data="editableLines" size="small" border max-height="380" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="lineNo" :label="$t('sharing.common.colLineNo')" width="60"  sortable />
        <el-table-column :label="$t('field.summary')" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.summary" size="small" :disabled="readonly" :placeholder="$t('field.summary')" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('sharing.common.colAcctCode')" width="160">
          <template #default="{ row }">
            <el-select
              v-model="row.subjectCode"
              size="small"
              :disabled="readonly"
              filterable
              remote
              :remote-method="(q) => searchSubjects(q)"
              :placeholder="$t('sharing.taskWork.phSearchSubject')"
              style="width: 100%"
              @change="(val) => onSubjectSelect(row, val)"
            >
              <el-option
                v-for="s in subjectOptions"
                :key="s.code"
                :label="`${s.code} - ${s.name}`"
                :value="s.code"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="$t('sharing.common.colAcctName')" min-width="130">
          <template #default="{ row }">
            <span>{{ row.subjectName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('field.direction')" width="80">
          <template #default="{ row }">
            <el-select v-model="row.direction" size="small" :disabled="readonly" style="width: 100%">
              <el-option :label="$t('sharing.common.colDebit')" value="DEBIT" />
              <el-option :label="$t('sharing.common.colCredit')" value="CREDIT" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="$t('field.amount')" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.amount" size="small" :disabled="readonly" :min="0" :precision="2" style="width: 100%" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!editableLines.length" :description="$t('sharing.taskWork.emptyNoVoucher')" />
      <template #footer>
        <el-button @click="voucherVis = false">{{ $t('common.close') }}</el-button>
        <el-button v-if="!voucherInfo && canGenerateVoucher" type="primary" :loading="generating" @click="generateVoucher">{{ $t('sharing.taskWork.btnGenerateVoucher') }}</el-button>
        <el-button v-if="!readonly" type="primary" :disabled="!editableLines.length" @click="saveLines">{{ $t('sharing.taskWork.btnSaveLines') }}</el-button>
        <el-button v-if="!readonly && voucherInfo" v-permission="'sharing:voucher:post'" type="success" @click="postVoucher">{{ $t('sharing.common.colBookkeep') }}</el-button>
      </template>
    </el-dialog>

    <!-- 退单 -->
    <el-dialog v-model="returnVis" :title="$t('sharing.taskWork.titleReturn')" width="460px">
      <!-- 退单原因与后端 SharingService.returnTask「请填写退单原因」断言对齐为前端必填 -->
      <el-form ref="returnFormRef" :model="returnForm" :rules="returnFormRules" label-width="80px">
        <el-form-item :label="$t('sharing.common.colTask')">{{ current?.billNo }}</el-form-item>
        <el-form-item :label="$t('sharing.taskWork.colReturnReason')" prop="reason">
          <el-select v-model="returnForm.reason" filterable allow-create default-first-option :placeholder="$t('sharing.taskWork.phReturnReason')" style="width: 100%"><el-option :label="$t('sharing.taskWork.optMaterials')" value="资料不齐" /><el-option :label="$t('sharing.taskWork.optAmountWrong')" value="金额有误" /><el-option :label="$t('sharing.taskWork.optFlowWrong')" value="审批流程不符" /><el-option :label="$t('sharing.taskWork.optOverBudget')" value="超预算" /><el-option :label="$t('sharing.taskWork.optInvoiceIssue')" value="发票问题" /><el-option :label="$t('sharing.taskWork.optInfoWrong')" value="信息填写错误" /><el-option :label="$t('sharing.common.colOther')" value="其他" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVis = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="warning" @click="doReturn">{{ $t('sharing.taskWork.btnConfirmReturn') }}</el-button>
      </template>
    </el-dialog>

    <!-- 影像/附件查看器 -->
    <FileViewer ref="fileViewer" />
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
</template>

<script setup>
import {onMounted, reactive, ref, computed} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime, showRowDetail } from '@/utils/format'
import { sharingApi, billApi, masterApi } from '../../api'
import { hasPerm } from '../../utils/perm'
import FileViewer from '../../components/FileViewer.vue'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import TablePager from '../../components/common/TablePager.vue'
import { useTablePager } from '../../composables/useTablePager'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { billTypeLabel, taskTypeLabel } from '@/constants/enums'
import { useI18n } from 'vue-i18n'
import { validateForm } from '@/composables/useFormRules';


const { t } = useI18n()

// BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.billId || row.id
  traceBizType.value = row.billType || null
  traceVisible.value = true
}

// 双击行：展示行数据详情
function openRowDetail(row) {
  return showRowDetail(row, { title: t('field.detail') })
}

const selRows = ref([])
// 服务端分页：原先固定 pageNum:1/pageSize:100，超 100 条静默截断
const pager = useTablePager({ mode: 'server', storageKey: 'sharing_taskwork' })
const { rows, loading, pageNum, pageSize, total } = pager
const voucherVis = ref(false)
const voucherInfo = ref(null)
const editableLines = ref([])
const readonly = ref(false)
const returnVis = ref(false)
const current = ref(null)
// 退单原因只有裸 ref + 空 rules（reactive({})）的话，既无必填校验也无任何提示；
// 表单绑定 + 必填规则，与后端 SharingService.returnTask 的「请填写退单原因」断言同一把尺。
const returnFormRef = ref(null)
const returnForm = reactive({ reason: '' })
const returnFormRules = computed(() => (reactive({
  reason: [{ required: true, message: t('sharing.taskWork.errReturnReason') }],
})))
const fileViewer = ref(null)
const subjectOptions = ref([])
const generating = ref(false)
const canGenerateVoucher = hasPerm('sharing:voucher:generate')
// 预览/改分录的查询接口 GET /tenant/sharing/voucher/page 需 sharing:voucher:list，
// 无该码时服务端以业务码 4030 拒绝（HTTP 仍是 200），故入口按同一码前置隐藏。
const canVoucherList = hasPerm('sharing:voucher:list')

/** 远程搜索科目 */
async function searchSubjects(query) {
  try {
    const r = await masterApi.subject.page({ pageNum: 1, pageSize: 50, keyword: query || '' })
    subjectOptions.value = r.list || r.records || []
  } catch { subjectOptions.value = [] }
}

/** 选中科目后联动带出科目名称 */
function onSubjectSelect(row, code) {
  const s = subjectOptions.value.find((x) => x.code === code)
  row.subjectName = s ? s.name : ''
}

/** 查看工单关联单据的影像/附件（共享审核必看） */
async function viewBillImages(row) {
  try {
    const d = await billApi.detail(row.billId)
    const atts = (d && d.attaches) || []
    const fileIds = atts.map((a) => a.fileId).filter((x) => x != null)
    if (!fileIds.length) { ElMessage.info(t('sharing.common.msgNoDocImages')); return }
    fileViewer.value?.open(fileIds, t('sharing.common.titleDocImages') + (d?.bill?.billNo || row.billId))
  } catch (e) { ElMessage.error(e.message) }
}

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }

async function load() {
  try {
    await pager.load(async ({ pageNum, pageSize }) => {
      const r = await sharingApi.mine({ pageNum, pageSize })
      return { list: r.list || [], total: Number(r.total) || (r.list || []).length }
    })
  } catch (e) { ElMessage.error(e.message) }
}

async function openVoucher(row) {
  current.value = row
  try {
    // 按 billId 直查（走 op_voucher(tenant_id,bill_id) 索引）；原写法取最新 200 张再前端 find，
    // 客户数据上量后目标凭证不在其中 ⇒ 误判为"未生成凭证"并锁死编辑态
    const vp = await sharingApi.voucherPage({ pageNum: 1, pageSize: 200, billId: row.billId })
    const v = (vp.list || []).find((x) => String(x.billId) === String(row.billId)) || (vp.list || [])[0]
    voucherInfo.value = v || null
    readonly.value = v ? v.status === BillStatus.POSTED : false
    editableLines.value = v ? await sharingApi.voucherLines(v.id) : []
    // 预加载科目选项，确保已有分录的科目编码能在下拉中显示
    await searchSubjects('')
    voucherVis.value = true
  } catch (e) {
    // 查询失败（含权限码 4030）必须带单据号如实报出，不能"点了没抽屉"就完事；
    // 弹窗保持关闭，避免呈现一张空凭证表让人误判"该单没有凭证"。
    voucherVis.value = false
    ElMessage.error(t('sharing.taskWork.msgLoadFail', { v1: row.billNo || row.billId || t('sharing.taskWork.txtCurrentTicket'), v2: e?.message || t('sharing.ops.fallbackUnknown') }))
  }
}

/** 补生成凭证：单据已审批/已付款但无凭证时（如结账期间生成失败），按单据类型走后端去重生成 */
async function generateVoucher() {
  if (!current.value?.billId) return ElMessage.warning(t('sharing.taskWork.warnNoBill'))
  generating.value = true
  try {
    await sharingApi.voucherGenerate(current.value.billId)
    ElMessage.success(t('sharing.taskWork.msgVoucherDone'))
    await openVoucher(current.value)
  } catch (e) { ElMessage.error(e.message) } finally { generating.value = false }
}

async function saveLines() {
  if (!voucherInfo.value) return ElMessage.warning(t('sharing.taskWork.warnNoVoucher'))
  // 后端校验借贷平衡；前端先粗校验
  const d = editableLines.value.reduce((a, l) => a + (l.direction === 'DEBIT' ? Number(l.amount || 0) : 0), 0)
  const c = editableLines.value.reduce((a, l) => a + (l.direction === 'CREDIT' ? Number(l.amount || 0) : 0), 0)
  if (Math.abs(d - c) > 0.01) return ElMessage.warning(t('sharing.taskWork.warnUnbalanced', { v1: d, v2: c }))
  try {
    await sharingApi.voucherLinesUpdate(voucherInfo.value.id, editableLines.value)
    ElMessage.success(t('sharing.taskWork.msgLinesSaved'))
    voucherVis.value = false
  } catch (e) { ElMessage.error(e.message) }
}

async function postVoucher() {
  await ElMessageBox.confirm(t('sharing.taskWork.confirmPost'), t('sharing.common.colBookkeep'), { type: 'warning' })
    .then(async () => {
      await sharingApi.voucherPost(voucherInfo.value.id)
      ElMessage.success(t('sharing.taskWork.msgPosted'))
      voucherVis.value = false
      load()
    }).catch(() => {})
}

function openReturn(row) {
  current.value = row
  returnForm.reason = ''
  returnVis.value = true
}

async function doReturn() {
  // 先过表单必填校验（allow-create 输入即触发 change），未填不发服务端请求
  if (!(await validateForm(returnFormRef))) return;
  const reason = (returnForm.reason || '').trim()
  if (!reason) return ElMessage.warning(t('sharing.taskWork.errReturnReason'))
  await sharingApi.returnTask(current.value.id, reason)
  ElMessage.success(t('sharing.taskWork.msgReturned'))
  returnVis.value = false
  load()
}

async function finish(row) {
  // 完成弹窗带备注输入框，不用纯 confirm——
  // 纯 confirm 下审计口语无处可写、更无处可提交，三张工单 remark 恒 NULL。
  // 备注选填（不阻断办结），提交后服务端落 op_audit_task.remark 并随「备注」列回显。
  const { value: remark } = await ElMessageBox.prompt(
    t('sharing.taskWork.promptFinish', { v1: row.billNo }), t('sharing.taskWork.titleFinish'), {
      inputType: 'textarea',
      inputPlaceholder: t('sharing.taskWork.phFinishRemark'),
      confirmButtonText: t('sharing.taskWork.btnConfirmFinish'),
      cancelButtonText: t('common.cancel'),
      inputValidator: (v) => ((v || '').length > 490 ? t('sharing.taskWork.errFinishRemark') : true),
    }).catch(() => ({}))
  if (remark === undefined) return // 用户取消
  try {
    await sharingApi.finish(row.id, remark)
    ElMessage.success(t('sharing.common.colDone'))
    load()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning(t('sharing.common.msgNothingToExport')); return }
  const cols = [{ prop: 'billNo', label: t('field.docNo') }, { prop: 'title', label: t('sharing.common.colTitle') }, { prop: 'amount', label: t('field.amount') }, { prop: 'receiveTime', label: t('sharing.common.colClaimTime') }, { prop: 'remark', label: t('common.remark') }]
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `TaskWork_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('sharing.common.msgCsvExported'))
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning(t('sharing.common.msgPickRowsFirst')); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning(t('sharing.common.msgNoColumnInfo')); return }
  const header = keys.join(',')
  const lines = selRows.value.map((row) => keys.map((k) => {
    const v = row[k]
    if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `TaskWork_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(t('sharing.common.msgExportedCount', { v1: selRows.value.length }))
}

</script>
