<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-tabs v-model="tab" :class="{ tabless: isSingle }">
    <!-- ============ 影像调阅评价 ============ -->
    <el-tab-pane label="影像调阅评价" name="review">
      <el-card>
        <div style="margin-bottom:8px;display:flex;gap:8px;align-items:center">
          <el-select v-model="rStatus" clearable placeholder="评价状态" style="width:140px" @change="loadReview">
            <el-option label="待评价" value="PENDING" />
            <el-option label="已通过" value="OK" />
            <el-option label="整单重采集" value="RE_COLLECT" />
            <el-option label="补采集" value="RESCAN" />
            <el-option label="整单作废" value="REJECT" />
          </el-select>
          <el-button size="small" @click="loadReview">查询</el-button>
          <el-button size="small" type="primary" @click="reviewForm.visible = true">+ 录入待评价影像</el-button>
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        </div>
        <el-table :data="reviews" size="small" stripe border v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="batchNo" label="影像批次" width="140"  sortable />
          <el-table-column prop="bizNo" label="业务单号" width="160"  sortable />
          <el-table-column label="业务类型" width="100">
            <template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template>
          </el-table-column>
          <el-table-column label="评价状态" width="120">
            <template #default="{ row }"><el-tag :type="rType(row.reviewStatus)" size="small">{{ rLabel(row.reviewStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="reviewTime" label="评价时间" width="165"  sortable :formatter="(row, col, val) => formatDateTime(val)" />
          <el-table-column prop="remark" label="备注" min-width="120"  sortable />
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <template v-if="row.reviewStatus === 'PENDING'">
                <el-button link size="small" type="success" @click="evaluate(row, 'OK')">通过</el-button>
                <el-button link size="small" type="warning" @click="evaluate(row, 'RE_COLLECT')">整单重采集</el-button>
                <el-button link size="small" @click="evaluate(row, 'RESCAN')">补采集</el-button>
                <el-button link size="small" type="danger" @click="evaluate(row, 'REJECT')">整单作废</el-button>
              </template>
              <el-button v-if="row.reviewStatus === 'PENDING'" link size="small" type="danger" @click="delReview(row)">删除</el-button>
              <span v-else style="color:#909399;font-size:12px">已评价记录留痕不可删</span>
            </template>
          </el-table-column>
        <template #empty>
          <el-empty description="暂无数据" />
        </template>
        </el-table>

        <!-- P0 易用性：影像评价列表分页，避免影像量增长后静默截断在 100 条 -->
        <el-pagination
          v-if="rPage.total > 0"
          class="img-pager"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="rPage.total"
          :page-size="rPage.pageSize"
          :current-page="rPage.pageNum"
          :page-sizes="[20, 50, 100]"
          @current-change="onRPage"
          @size-change="onRSize"
        />
      </el-card>
    </el-tab-pane>

    <!-- ============ 影像异常处理 ============ -->
    <el-tab-pane label="影像异常处理" name="exc">
      <el-card>
        <div style="margin-bottom:8px;display:flex;gap:8px;align-items:center">
          <el-select v-model="eStatus" clearable placeholder="异常状态" style="width:140px" @change="loadExc">
            <el-option label="待处理" value="OPEN" />
            <el-option label="已处理" value="FIXED" />
          </el-select>
          <el-button size="small" @click="loadExc">查询</el-button>
          <el-button size="small" type="primary" @click="excForm.visible = true">+ 上报异常</el-button>
        </div>
        <el-table :data="exceptions" size="small" stripe border v-loading="loading" @selection-change="handleSelectionChange" @row-dblclick="openRowDetail">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="batchNo" label="影像批次" width="140"  sortable />
          <el-table-column prop="bizNo" label="业务单号" width="150"  sortable />
          <el-table-column label="异常类型" width="170">
            <template #default="{ row }">{{ eTypeLabel(row.exceptionType) }}</template>
          </el-table-column>
          <el-table-column prop="exceptionDesc" label="异常说明" min-width="180"  sortable />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag :type="row.status === 'OPEN' ? 'danger' : 'success'" size="small">{{ row.status === 'OPEN' ? '待处理' : '已处理' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button v-if="row.status === 'OPEN'" link size="small" type="success" @click="fix(row)">处理</el-button>
              <el-button link size="small" type="danger" @click="delExc(row)">删除</el-button>
            </template>
          </el-table-column>
        <template #empty>
          <el-empty description="暂无数据" />
        </template>
        </el-table>

        <!-- P0 易用性：影像异常列表分页 -->
        <el-pagination
          v-if="ePage.total > 0"
          class="img-pager"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="ePage.total"
          :page-size="ePage.pageSize"
          :current-page="ePage.pageNum"
          :page-sizes="[20, 50, 100]"
          @current-change="onEPage"
          @size-change="onESize"
        />
      </el-card>
    </el-tab-pane>
  </el-tabs>

  <!-- 录入待评价 -->
  <el-dialog v-model="reviewForm.visible" title="录入待评价影像" width="520px" destroy-on-close>
    <!-- R5-D-226：表单项此前未绑 prop ⇒ reviewRules 永不触发（必填校验双向失效）；补 prop + 提交前 validate -->
    <el-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules" label-width="100px" size="default">
      <el-form-item label="影像批次号" prop="batchNo"><el-input v-model="reviewForm.batchNo" /></el-form-item>
      <el-form-item label="业务单号" prop="bizNo"><el-input v-model="reviewForm.bizNo" /></el-form-item>
      <el-form-item label="业务类型" prop="bizType">
        <el-select v-model="reviewForm.bizType" placeholder="选择业务类型" style="width:100%">
          <el-option label="事前申请" value="APPLY" />
          <el-option label="费用报销" value="CLAIM" />
          <el-option label="差旅报销" value="TRAVEL" />
          <el-option label="对公付款" value="PAY" />
          <el-option label="借款" value="LOAN" />
          <el-option label="还款" value="REPAY" />
          <el-option label="发票" value="INVOICE" />
          <el-option label="合同" value="CONTRACT" />
          <el-option label="收货单" value="GRN" />
          <el-option label="采购订单" value="PO" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="reviewForm.remark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer><el-button type="primary" @click="saveReview">保存</el-button></template>
  </el-dialog>

  <!-- 上报异常 -->
  <el-dialog v-model="excForm.visible" title="上报影像异常" width="560px" destroy-on-close>
    <!-- R5-D-226：同 review 弹窗——补 prop 使 excRules 真正生效 -->
    <el-form ref="excFormRef" :model="excForm" :rules="excRules" label-width="100px" size="default">
      <el-form-item label="影像批次号" prop="batchNo"><el-input v-model="excForm.batchNo" /></el-form-item>
      <el-form-item label="业务单号" prop="bizNo"><el-input v-model="excForm.bizNo" /></el-form-item>
      <el-form-item label="异常类型" prop="exceptionType">
        <el-select v-model="excForm.exceptionType" style="width:100%">
          <el-option v-for="(v, k) in EXC_TYPES" :key="k" :label="v" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="异常说明" prop="exceptionDesc"><el-input v-model="excForm.exceptionDesc" type="textarea" /></el-form-item>
    </el-form>
    <template #footer><el-button type="primary" @click="saveExc">保存</el-button></template>
  </el-dialog>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
</template>

<script setup>
import { bizTypeLabel } from '@/constants/enums'
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { imageApi } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.bizId || row.id
  traceBizType.value = row.bizType || null
  traceVisible.value = true
}

const selRows = ref([])
// P: tab 页拆分——由菜单深链进来时传入 tab，只显示该页并隐藏 tab 导航条（一 TAB = 一菜单）
const props = defineProps({ tab: { type: String, default: '' } })
const isSingle = computed(() => !!props.tab)
const tab = ref(props.tab || 'review')
const rStatus = ref('PENDING')
const eStatus = ref('OPEN')
const reviews = ref([])
const loading = ref(false)
const exceptions = ref([])
// 分页状态：原来两个列表都固定取第 1 页 100 条且无翻页入口，影像量增长后会静默截断
const rPage = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const ePage = reactive({ pageNum: 1, pageSize: 20, total: 0 })

// 业务类型翻译
const reviewForm = reactive({ visible: false, batchNo: '', bizNo: '', bizType: '', remark: '' })
const excForm = reactive({ visible: false, batchNo: '', bizNo: '', exceptionType: 'VERIFY_FAIL', exceptionDesc: '' })
// R5-D-226：表单实例引用——save 前先 validate，避免必填规则只摆设不拦截
const reviewFormRef = ref(null)
const excFormRef = ref(null)

const reviewRules = {
  batchNo: [{ required: true, message: '请输入影像批次号' }],
  bizNo: [{ required: true, message: '请输入业务单号' }],
  bizType: [{ required: true, message: '请选择业务类型' }],
}
const excRules = {
  batchNo: [{ required: true, message: '请输入影像批次号' }],
  bizNo: [{ required: true, message: '请输入业务单号' }],
  exceptionType: [{ required: true, message: '请选择异常类型' }],
  exceptionDesc: [{ required: true, message: '请输入异常说明' }],
}

const EXC_TYPES = {
  DUPLICATE_BILL: '单据重复',
  DUPLICATE_INVOICE: '不同单据发票重复',
  TAX_NO_MISMATCH: '单据法人税号与发票税号不一致',
  OCR_INCOMPLETE: '发票数据识别不完整或缺失',
  VERIFY_FAIL: '发票验真失败',
  INVOICE_DUP_IN_BILL: '单据内发票重复',
  // R5-D-227：OCR 自动留痕枚举入字典（此前直出英文枚举与中文混排）。
  // 约定：后端新增异常类型须同步本字典；字典未收录的未知值显示兜底文案而非裸码。
  OCR_NOT_RECOGNIZED: '票面未识别',
  OCR_POOL_FAILED: '发票未入池',
  OCR_AMOUNT_INCONSISTENT: '票面金额不自洽',
}
function eTypeLabel(t) { return EXC_TYPES[t] || (t ? '未识别值（联系管理员）' : '—') }
function rLabel(s) { return { PENDING: '待评价', OK: '已通过', RE_COLLECT: '整单重采集', RESCAN: '补采集', REJECT: '整单作废' }[s] || s }
function rType(s) { return s === 'OK' ? 'success' : s === 'PENDING' ? 'warning' : s === 'REJECT' ? 'danger' : 'info' }

async function loadReview() {
  loading.value = true
  try {
    const r = await imageApi.reviewPage({
      pageNum: rPage.pageNum, pageSize: rPage.pageSize, status: rStatus.value || undefined,
    })
    reviews.value = r.list || []
    rPage.total = Number(r.total || 0)
  } finally { loading.value = false }
}
async function loadExc() {
  loading.value = true
  try {
    const r = await imageApi.exceptionPage({
      pageNum: ePage.pageNum, pageSize: ePage.pageSize, status: eStatus.value || undefined,
    })
    exceptions.value = r.list || []
    ePage.total = Number(r.total || 0)
  } finally { loading.value = false }
}
/** 影像评价列表翻页 / 改每页条数 */
function onRPage(p) { rPage.pageNum = p; loadReview() }
function onRSize(s) { rPage.pageSize = s; rPage.pageNum = 1; loadReview() }
/** 异常列表翻页 / 改每页条数 */
function onEPage(p) { ePage.pageNum = p; loadExc() }
function onESize(s) { ePage.pageSize = s; ePage.pageNum = 1; loadExc() }
async function evaluate(row, action) {
  try {
    await ElMessageBox.confirm(`确认执行「${rLabel(action)}」？`, '影像评价', { type: 'warning' })
  } catch (e) { return }
  await imageApi.reviewEvaluate({ id: row.id, batchId: row.imgBatchId, action, remark: action === 'REJECT' ? '整单作废' : '' })
  ElMessage.success('评价完成'); loadReview()
}
async function delReview(row) {
  try {
    await ElMessageBox.confirm(`删除待评价影像记录 ${row.batchNo || row.id}？删除后不再进入评价列表。`, '删除', { type: 'warning' })
  } catch (e) { return }
  try {
    await imageApi.reviewDelete(row.id); ElMessage.success('已删除')
  } catch (e) { ElMessage.error(e.message) }
  loadReview()
}
async function saveReview() {
  // R5-D-226：先过表单校验（与服务端 AssertUtil.notBlank 双侧同口径）
  if (reviewFormRef.value) { try { await reviewFormRef.value.validate() } catch { return } }
  await imageApi.reviewCreate({ batchNo: reviewForm.batchNo, bizNo: reviewForm.bizNo, bizType: reviewForm.bizType, remark: reviewForm.remark })
  ElMessage.success('已录入'); reviewForm.visible = false; Object.assign(reviewForm, { batchNo: '', bizNo: '', bizType: '', remark: '' }); loadReview()
}
async function fix(row) {
  await imageApi.exceptionFix(row.id, '人工核实处理完成')
  ElMessage.success('已处理'); loadExc()
}
async function delExc(row) {
  try {
    await ElMessageBox.confirm(`删除影像异常记录（${eTypeLabel(row.exceptionType)}）？已处理的异常属留痕，删除后不可恢复。`, '删除', { type: 'warning' })
  } catch (e) { return }
  try {
    await imageApi.exceptionDelete(row.id); ElMessage.success('已删除')
  } catch (e) { ElMessage.error(e.message) }
  loadExc()
}
async function saveExc() {
  // R5-D-226：先过表单校验（与服务端 AssertUtil.notBlank 双侧同口径）
  if (excFormRef.value) { try { await excFormRef.value.validate() } catch { return } }
  await imageApi.exceptionCreate({ batchNo: excForm.batchNo, bizNo: excForm.bizNo, exceptionType: excForm.exceptionType, exceptionDesc: excForm.exceptionDesc })
  ElMessage.success('已上报'); excForm.visible = false; Object.assign(excForm, { batchNo: '', bizNo: '', exceptionType: 'VERIFY_FAIL', exceptionDesc: '' }); loadExc()
}

onMounted(() => { loadReview(); loadExc() })

function exportCsv() {
  if (!reviews.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'batchNo', label: '影像批次' }, { prop: 'bizNo', label: '业务单号' }, { prop: 'reviewTime', label: '评价时间' }, { prop: 'remark', label: '备注' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = reviews.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `ImageReview_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}


function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
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
  a.download = `ImageReview_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>

<style scoped>
.img-pager { margin-top: 12px; display: flex; justify-content: flex-end; flex-wrap: wrap; }
.tabless :deep(.el-tabs__header) { display: none; }
</style>
