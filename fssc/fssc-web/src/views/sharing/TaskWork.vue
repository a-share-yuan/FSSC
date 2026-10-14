<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center">
        <b>工单处理</b>
        <span style="color: #909399; font-size: 13px">共享作业人员处理已认领工单：凭证预览 / 修改分录 / 退单</span>
      </div>
    </template>

    <div style="margin-bottom: 8px">
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
    </div>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" size="small" stripe v-else max-height="520" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" label="单号" width="140"  sortable />
      <el-table-column label="类型" width="70">
        <template #default="{ row }">{{ billTypeLabel(row.billType) }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip  sortable />
      <el-table-column prop="amount" label="金额" width="100" align="right" sortable>
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="作业项" width="90">
        <template #default="{ row }">{{ taskTypeLabel(row.taskType) }}</template>
      </el-table-column>
      <!-- R5-D-238：认领时间此前直出后端原始串（ISO 带 T），与页面其余时间列格式不一致，统一走 formatDateTime -->
      <el-table-column prop="receiveTime" label="认领时间" width="160" sortable :formatter="(r, c, v) => formatDateTime(v)" />
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip  sortable />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
          <!-- D-DEC-029：凭证面按 sharing:voucher:list 前置收口。该接口无权限时服务端只回
               HTTP 200 + 业务码 4030，按钮照旧亮着、点了没抽屉＝"看得见点得动永远失败"，
          <el-button v-if="canVoucherList" link size="small" type="primary" @click="openVoucher(row)">凭证预览/修改分录</el-button>
          <el-button link size="small" type="primary" @click="viewBillImages(row)">查看影像</el-button>
          <el-button link size="small" type="warning" @click="openReturn(row)">退单</el-button>
          <el-button v-permission="'sharing:task:finish'" link size="small" type="success" @click="finish(row)">完成</el-button>
        </template>
      </el-table-column>
    </el-table>

    <TablePager :pager="pager" @change="load" />

    <el-empty v-if="!loading && !rows.length" description="暂无已认领工单（请在任务池抢单或由管理员分派）" />

    <!-- 凭证预览 + 分录修改 -->
    <el-dialog v-model="voucherVis" title="凭证预览 / 修改分录" width="720px" destroy-on-close>
      <el-alert v-if="voucherInfo" :title="`凭证 ${voucherInfo.voucherNo}（${voucherInfo.status === BillStatus.POSTED ? '已记账' : '未记账'}） 借 ${fmt(voucherInfo.debitTotal)} / 贷 ${fmt(voucherInfo.creditTotal)}`"
        :type="voucherInfo.status === BillStatus.POSTED ? 'info' : 'warning'" :closable="false" show-icon style="margin-bottom: 8px" />
      <el-table :data="editableLines" size="small" border max-height="380" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="lineNo" label="行号" width="60"  sortable />
        <el-table-column label="摘要" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.summary" size="small" :disabled="readonly" placeholder="摘要" />
          </template>
        </el-table-column>
        <el-table-column label="科目编码" width="160">
          <template #default="{ row }">
            <el-select
              v-model="row.subjectCode"
              size="small"
              :disabled="readonly"
              filterable
              remote
              :remote-method="(q) => searchSubjects(q)"
              placeholder="搜索科目编码/名称"
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
        <el-table-column label="科目名称" min-width="130">
          <template #default="{ row }">
            <span>{{ row.subjectName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="80">
          <template #default="{ row }">
            <el-select v-model="row.direction" size="small" :disabled="readonly" style="width: 100%">
              <el-option label="借" value="DEBIT" />
              <el-option label="贷" value="CREDIT" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.amount" size="small" :disabled="readonly" :min="0" :precision="2" style="width: 100%" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!editableLines.length" description="该单据未生成凭证" />
      <template #footer>
        <el-button @click="voucherVis = false">关闭</el-button>
        <el-button v-if="!voucherInfo && canGenerateVoucher" type="primary" :loading="generating" @click="generateVoucher">生成凭证</el-button>
        <el-button v-if="!readonly" type="primary" :disabled="!editableLines.length" @click="saveLines">保存分录</el-button>
        <el-button v-if="!readonly && voucherInfo" v-permission="'sharing:voucher:post'" type="success" @click="postVoucher">记账</el-button>
      </template>
    </el-dialog>

    <!-- 退单 -->
    <el-dialog v-model="returnVis" title="退单（退回业务组任务池）" width="460px">
      <!-- R5-D-235：退单原因与后端 SharingService.returnTask「请填写退单原因」断言对齐为前端必填 -->
      <el-form ref="returnFormRef" :model="returnForm" :rules="returnFormRules" label-width="80px">
        <el-form-item label="任务">{{ current?.billNo }}</el-form-item>
        <el-form-item label="退单原因" prop="reason">
          <el-select v-model="returnForm.reason" filterable allow-create default-first-option placeholder="请选择或输入退回原因" style="width: 100%"><el-option label="资料不齐" value="资料不齐" /><el-option label="金额有误" value="金额有误" /><el-option label="审批流程不符" value="审批流程不符" /><el-option label="超预算" value="超预算" /><el-option label="发票问题" value="发票问题" /><el-option label="信息填写错误" value="信息填写错误" /><el-option label="其他" value="其他" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVis = false">取消</el-button>
        <el-button type="warning" @click="doReturn">确认退单</el-button>
      </template>
    </el-dialog>

    <!-- 影像/附件查看器 -->
    <FileViewer ref="fileViewer" />
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
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

// R4-02: BillTraceDrawer
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
  return showRowDetail(row, { title: '详情' })
}

const selRows = ref([])
// 服务端分页（P0-1）：改造前固定 pageNum:1/pageSize:100，超 100 条静默截断
const pager = useTablePager({ mode: 'server', storageKey: 'sharing_taskwork' })
const { rows, loading, pageNum, pageSize, total } = pager
const voucherVis = ref(false)
const voucherInfo = ref(null)
const editableLines = ref([])
const readonly = ref(false)
const returnVis = ref(false)
const current = ref(null)
// R5-D-235：退单原因此前只有裸 ref + 空 rules（reactive({})），既无必填校验也无任何提示；
// 现改为表单绑定 + 必填规则，与后端 SharingService.returnTask 的「请填写退单原因」断言同一把尺。
const returnFormRef = ref(null)
const returnForm = reactive({ reason: '' })
const returnFormRules = reactive({
  reason: [{ required: true, message: '请填写退单原因' }],
})
const fileViewer = ref(null)
const subjectOptions = ref([])
const generating = ref(false)
const canGenerateVoucher = hasPerm('sharing:voucher:generate')
// D-DEC-029：预览/改分录的取数接口 GET /tenant/sharing/voucher/page 需 sharing:voucher:list，
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
    if (!fileIds.length) { ElMessage.info('该单据暂无影像/附件'); return }
    fileViewer.value?.open(fileIds, '单据影像：' + (d?.bill?.billNo || row.billId))
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
    const vp = await sharingApi.voucherPage({ pageNum: 1, pageSize: 200 })
    const v = (vp.list || []).find((x) => String(x.billId) === String(row.billId))
    voucherInfo.value = v || null
    readonly.value = v ? v.status === BillStatus.POSTED : false
    editableLines.value = v ? await sharingApi.voucherLines(v.id) : []
    // 预加载科目选项，确保已有分录的科目编码能在下拉中显示
    await searchSubjects('')
    voucherVis.value = true
  } catch (e) {
    // D-DEC-029：取数失败（含权限码 4030）必须带单据号如实报出，不能"点了没抽屉"就完事；
    // 弹窗保持关闭，避免呈现一张空凭证表让人误判"该单没有凭证"。
    voucherVis.value = false
    ElMessage.error(`凭证预览加载失败（${row.billNo || row.billId || '当前工单'}）：${e?.message || '未知错误'}`)
  }
}

/** 补生成凭证：单据已审批/已付款但无凭证时（如结账期间生成失败），按单据类型走后端幂等生成 */
async function generateVoucher() {
  if (!current.value?.billId) return ElMessage.warning('未选择单据')
  generating.value = true
  try {
    await sharingApi.voucherGenerate(current.value.billId)
    ElMessage.success('凭证已生成')
    await openVoucher(current.value)
  } catch (e) { ElMessage.error(e.message) } finally { generating.value = false }
}

async function saveLines() {
  if (!voucherInfo.value) return ElMessage.warning('无凭证可保存')
  // 后端校验借贷平衡；前端先粗校验
  const d = editableLines.value.reduce((a, l) => a + (l.direction === 'DEBIT' ? Number(l.amount || 0) : 0), 0)
  const c = editableLines.value.reduce((a, l) => a + (l.direction === 'CREDIT' ? Number(l.amount || 0) : 0), 0)
  if (Math.abs(d - c) > 0.01) return ElMessage.warning(`借贷不平衡：借 ${d} / 贷 ${c}`)
  try {
    await sharingApi.voucherLinesUpdate(voucherInfo.value.id, editableLines.value)
    ElMessage.success('分录已保存')
    voucherVis.value = false
  } catch (e) { ElMessage.error(e.message) }
}

async function postVoucher() {
  await ElMessageBox.confirm('确认记账？记账后分录不可修改', '记账', { type: 'warning' })
    .then(async () => {
      await sharingApi.voucherPost(voucherInfo.value.id)
      ElMessage.success('已记账')
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
  // R5-D-235：先过表单必填校验（allow-create 输入即触发 change），未填不发服务端请求
  try { await returnFormRef.value?.validate() } catch { return }
  const reason = (returnForm.reason || '').trim()
  if (!reason) return ElMessage.warning('请填写退单原因')
  await sharingApi.returnTask(current.value.id, reason)
  ElMessage.success('已退回任务池')
  returnVis.value = false
  load()
}

async function finish(row) {
  // D-DEC-024/D-DEC-030：完成弹窗由纯 confirm 改为带备注输入框——
  // 此前办结是 MessageBox.confirm，审计口语无处可写、更无处可提交，三张工单 remark 恒 NULL。
  // 备注选填（不阻断办结），提交后服务端落 op_audit_task.remark 并随「备注」列回显。
  const { value: remark } = await ElMessageBox.prompt(
    `完成工单 ${row.billNo}？请填写完成备注（留痕，供审计复核）`, '完成工单', {
      inputType: 'textarea',
      inputPlaceholder: '完成备注，如：票据三证齐全、制度校验通过、差异已口头确认（可空）',
      confirmButtonText: '确认完成',
      cancelButtonText: '取消',
      inputValidator: (v) => ((v || '').length > 490 ? '完成备注不得超过 490 字' : true),
    }).catch(() => ({}))
  if (remark === undefined) return // 用户取消
  try {
    await sharingApi.finish(row.id, remark)
    ElMessage.success('已完成')
    load()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'billNo', label: '单号' }, { prop: 'title', label: '标题' }, { prop: 'amount', label: '金额' }, { prop: 'receiveTime', label: '认领时间' }, { prop: 'remark', label: '备注' }]
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
  ElMessage.success('已导出 CSV')
}


function handleSelectionChange(val) { selRows.value = val }

function batchExport() {
  if (!selRows.value.length) { ElMessage.warning('请先勾选要导出的记录'); return }
  const keys = selRows.value[0] ? Object.keys(selRows.value[0]) : []
  if (!keys.length) { ElMessage.warning('无法获取列信息'); return }
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
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>
