<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div :class="{ tabless: isSingle }">
    <el-card>
    <template #header>
      <div style="display:flex; gap:10px; align-items:center">
        <b>实物流签收</b>
        <span style="color:#909399; font-size:12px">条码扫描 / 快递登记 / 批量签收 / 拒签退件</span>
        <el-button type="primary" size="small" style="margin-left:auto" @click="load">刷新</el-button>
      </div>
    </template>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ==================== 签收中心 ==================== -->
      <el-tab-pane label="签收中心" name="sign">
        <!-- 扫码签收栏 -->
        <div class="scan-bar">
          <el-input
            ref="scanInputRef"
            v-model="scanValue"
            placeholder="扫码 / 输入单据号 / 运单号"
            size="large"
            style="width:400px"
            @keyup.enter="doScanSign"
          />
          <el-button type="primary" size="large" @click="doScanSign" :loading="scanLoading">
            扫码签收
          </el-button>
          <el-divider direction="vertical" />
          <el-button size="large" @click="batchSignVisible = true" :disabled="!selectedRows.length">
            批量签收({{ selectedRows.length }})
          </el-button>
          <el-button size="large" @click="activeTab = 'apply'">交单登记</el-button>
        </div>

        <!-- 筛选栏 -->
        <div class="filter-bar">
          <el-select v-model="filters.status" placeholder="状态" clearable size="small" style="width:120px">
            <el-option label="待签收" value="PENDING" />
            <el-option label="已签收" value="SIGNED" />
            <el-option label="已拒签" value="REJECTED" />
          </el-select>
          <el-input v-model="filters.billNo" placeholder="单据号" clearable size="small" style="width:180px" />
          <el-input v-model="filters.expressNo" placeholder="运单号" clearable size="small" style="width:180px" />
          <el-select v-model="filters.deliveryType" placeholder="交接方式" clearable size="small" style="width:120px">
            <el-option label="邮寄" value="POST" />
            <el-option label="当面交接" value="HANDOVER" />
            <el-option label="其他" value="OTHER" />
          </el-select>
          <el-input v-model="filters.keyword" placeholder="关键词" clearable size="small" style="width:160px" @keyup.enter="doSearch" />
          <el-button type="primary" size="small" @click="doSearch">查询</el-button>
          <el-button size="small" @click="resetSearch">重置</el-button>
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
        </div>

        <!-- 签收列表 -->
<el-table :data="rows" size="small" border stripe :max-height="480" @row-dblclick="openRowDetail"
          @selection-change="(v) => selectedRows = v" empty-text="暂无数据">
          <el-table-column type="selection" width="40" :selectable="(r) => r.status === 'PENDING'" />
          <el-table-column prop="billNo" label="单据号" width="190"  sortable />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ billTypeLabel(row.billType) }}</template>
          </el-table-column>
          <el-table-column prop="deliverName" label="交付人" width="100"  sortable />
          <el-table-column label="方式" width="80">
            <template #default="{ row }">{{ deliveryLabel(row.deliveryType) }}</template>
          </el-table-column>
          <el-table-column prop="expressCompany" label="快递公司" width="90"  sortable />
          <el-table-column prop="expressNo" label="运单号" width="140" show-overflow-tooltip  sortable />
          <el-table-column prop="barcode" label="条码" width="140" show-overflow-tooltip  sortable />
          <el-table-column prop="packageCount" label="件数" width="55" align="center"  sortable />
          <el-table-column label="交付时间" width="145">
            <template #default="{ row }">{{ formatTime(row.deliverTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="85" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'SIGNED'" type="success" size="small">已签收</el-tag>
              <el-tag v-else-if="row.status === BillStatus.REJECTED" type="danger" size="small">已拒签</el-tag>
              <el-tag v-else type="warning" size="small">待签收</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="signName" label="签收人" width="100"  sortable />
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">追溯</el-button>
              <!-- R5-D-243：单据 DRAFT/CANCELLED 时后端签收/拒签闸门必拒（先提交单据才能签），
                   前端同口径隐藏入口并给出原因，避免"点了必失败"；billStatus 由服务端批量回填。 -->
              <template v-if="row.status === 'PENDING'">
                <template v-if="!isDraftLike(row)">
                  <el-button link type="primary" @click="sign(row)">签收</el-button>
                  <el-button link type="danger" @click="openReject(row)">拒签</el-button>
                </template>
                <el-tooltip v-else placement="top" :content="`单据处于草稿/已取消状态（${row.billStatus}），请先提交单据`">
                  <span class="sign-blocked-tip">签收/拒签不可用</span>
                </el-tooltip>
              </template>
              <el-button link type="info" @click="showByBill(row)">交付记录</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top:10px; justify-content:flex-end"
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNum"
          @current-change="(p) => { pageNum = p; load() }" />
      </el-tab-pane>

      <!-- ==================== 交单登记 ==================== -->
      <el-tab-pane label="交单登记" name="apply">
        <el-form ref="applyFormRef" :model="applyForm" :rules="applyFormRules" label-width="100px" style="max-width:600px" size="default">
          <el-form-item label="选择单据" prop="billId">
            <el-select
              v-model="applyForm.billId"
              filterable
              remote
              reserve-keyword
              placeholder="输入单据号/标题搜索"
              :remote-method="searchBills"
              :loading="billSearching"
              style="width:380px"
              @change="onBillSelect"
            >
              <el-option
                v-for="b in billOptions"
                :key="b.id"
                :label="`${b.billNo} - ${b.title || ''} (${billTypeLabel(b.billType)})`"
                :value="b.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="快递公司" v-if="applyForm.deliveryType === 'POST'">
            <el-select v-model="applyForm.expressCompany" placeholder="选择快递公司" clearable style="width:220px">
              <el-option label="顺丰速运" value="SF" />
              <el-option label="京东物流" value="JD" />
              <el-option label="中通快递" value="ZTO" />
              <el-option label="圆通速递" value="YTO" />
              <el-option label="申通快递" value="STO" />
              <el-option label="EMS" value="EMS" />
              <el-option label="德邦快递" value="DBL" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="运单号" v-if="applyForm.deliveryType === 'POST'">
            <el-input v-model="applyForm.expressNo" placeholder="快递运单号" style="width:280px" />
          </el-form-item>
          <el-form-item label="件数">
            <el-input-number v-model="applyForm.packageCount" :min="1" :max="99" style="width:120px" />
          </el-form-item>
          <el-form-item label="交接方式">
            <el-select v-model="applyForm.deliveryType" style="width:160px">
              <el-option label="邮寄" value="POST" />
              <el-option label="当面交接" value="HANDOVER" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-select v-model="applyForm.remark" filterable allow-create default-first-option placeholder="请选择或输入备注" style="width: 100%"><el-option label="正常交接" value="正常交接" /><el-option label="加急处理" value="加急处理" /><el-option label="补充资料" value="补充资料" /><el-option label="特殊说明" value="特殊说明" /><el-option label="其他" value="其他" /></el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doApply" :loading="applyLoading">提交交单</el-button>
          </el-form-item>
        </el-form>

        <!-- 我的交单记录 -->
        <el-divider content-position="left">我的交单记录</el-divider>
        <el-table :data="myDeliveries" size="small" border stripe :max-height="300" @row-dblclick="openRowDetail" empty-text="暂无数据">
          <el-table-column prop="billNo" label="单据号" width="190"  sortable />
          <el-table-column prop="expressCompany" label="快递公司" width="90"  sortable />
          <el-table-column prop="expressNo" label="运单号" width="140" show-overflow-tooltip  sortable />
          <el-table-column prop="packageCount" label="件数" width="55" align="center"  sortable />
          <el-table-column label="方式" width="80">
            <template #default="{ row }">{{ deliveryLabel(row.deliveryType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="85" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SIGNED' ? 'success' : row.status === BillStatus.REJECTED ? 'danger' : 'warning'" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="交付时间" width="145">
            <template #default="{ row }">{{ formatTime(row.deliverTime) }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top:10px; justify-content:flex-end"
          layout="total, prev, pager, next"
          :total="myTotal"
          :page-size="10"
          :current-page="myPageNum"
          @current-change="(p) => { myPageNum = p; loadMyDeliveries() }" />
      </el-tab-pane>

      <!-- ==================== 条码管理 ==================== -->
      <el-tab-pane label="条码管理" name="barcode">
        <div class="filter-bar">
          <el-select v-model="barcodeFilters.bizType" placeholder="业务类型" clearable size="small" style="width:120px">
            <el-option label="单据" value="BILL" />
            <el-option label="档案" value="ARCHIVE" />
            <el-option label="档案盒" value="BOX" />
          </el-select>
          <el-select v-model="barcodeFilters.status" placeholder="状态" clearable size="small" style="width:120px">
            <el-option label="已生成" value="GENERATED" />
            <el-option label="已打印" value="PRINTED" />
            <el-option label="已扫描" value="SCANNED" />
            <el-option label="已上架" value="SHELVED" />
          </el-select>
          <el-input v-model="barcodeFilters.keyword" placeholder="条码值/编号" clearable size="small" style="width:200px" @keyup.enter="loadBarcodes" />
          <el-button type="primary" size="small" @click="loadBarcodes">查询</el-button>
          <!-- R5-D-245：条码管理此前只读列表，无生成/上架入口；后端 /barcode/generate、/barcode/shelve 早已在 -->
          <el-button type="success" size="small" @click="openGenerateBarcode">按单据生成条码</el-button>
        </div>

        <el-table :data="barcodes" size="small" border stripe :max-height="420" @row-dblclick="openRowDetail" empty-text="暂无数据">
          <el-table-column prop="barcode" label="条码值" width="220"  sortable />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ barcodeTypeLabel(row.bizType) }}</template>
          </el-table-column>
          <el-table-column prop="refNo" label="关联编号" width="180" show-overflow-tooltip  sortable />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="barcodeStatusType(row.status)" size="small">{{ barcodeStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="printName" label="打印人" width="90"  sortable />
          <el-table-column label="打印时间" width="145">
            <template #default="{ row }">{{ formatTime(row.printTime) }}</template>
          </el-table-column>
          <el-table-column prop="scanName" label="扫描人" width="90"  sortable />
          <el-table-column prop="location" label="上架位置" width="120"  sortable />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <!-- R5-D-245：档案上架入口（扫码/输入位置）——未上架条码可操作，已上架只读展示 -->
              <el-button v-if="row.status !== 'SHELVED'" link type="primary" @click="shelveBarcode(row)">上架</el-button>
              <span v-else style="color:#909399; font-size:12px">已上架</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top:10px; justify-content:flex-end"
          layout="total, prev, pager, next"
          :total="barcodeTotal"
          :page-size="10"
          :current-page="barcodePageNum"
          @current-change="(p) => { barcodePageNum = p; loadBarcodes() }" />
      </el-tab-pane>
    </el-tabs>

    <!-- 拒签弹窗 -->
    <el-dialog v-model="rejectVisible" title="拒签退件" width="450px" @open="onRejectOpen">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectFormRules" label-width="80px">
        <el-form-item label="单据号">
          <span>{{ rejectRow?.billNo }}</span>
        </el-form-item>
        <el-form-item label="拒签原因" prop="reason">
          <el-select v-model="rejectForm.reason" filterable allow-create default-first-option placeholder="请选择或输入拒签原因" style="width: 100%"><el-option label="单据信息有误" value="单据信息有误" /><el-option label="附件缺失" value="附件缺失" /><el-option label="签收人不在" value="签收人不在" /><el-option label="地址错误" value="地址错误" /><el-option label="单据已过期" value="单据已过期" /><el-option label="其他" value="其他" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="doReject" :loading="rejectLoading">确认拒签</el-button>
      </template>
    </el-dialog>

    <!-- 批量签收弹窗 -->
    <el-dialog v-model="batchSignVisible" title="批量签收" width="450px">
      <p>将签收 <b>{{ selectedRows.length }}</b> 条待签收单据：</p>
      <el-select v-model="batchSignRemark" filterable allow-create default-first-option placeholder="签收备注（可选）" style="margin-top:8px; width: 100%"><el-option label="正常签收" value="正常签收" /><el-option label="委托代签" value="委托代签" /><el-option label="部分签收" value="部分签收" /><el-option label="其他" value="其他" /></el-select>
      <template #footer>
        <el-button @click="batchSignVisible = false">取消</el-button>
        <el-button type="primary" @click="doBatchSign" :loading="batchSignLoading">确认签收</el-button>
      </template>
    </el-dialog>

    <!-- 单据交付记录弹窗 -->
    <el-dialog v-model="byBillVisible" :title="'单据 #' + (byBillCurrentBillId || '') + ' 交付记录'" width="720px">
      <el-table :data="byBill" size="small" border @row-dblclick="openRowDetail" empty-text="暂无数据">
        <el-table-column prop="billNo" label="单据号" width="190"  sortable />
        <el-table-column prop="deliverName" label="交付人" width="100"  sortable />
        <el-table-column prop="expressCompany" label="快递公司" width="90"  sortable />
        <el-table-column prop="expressNo" label="运单号" width="130" show-overflow-tooltip  sortable />
        <el-table-column label="方式" width="80">
          <template #default="{ row }">{{ deliveryLabel(row.deliveryType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SIGNED' ? 'success' : row.status === BillStatus.REJECTED ? 'danger' : 'warning'" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- R5-D-245：按单据生成条码（重打/补打场景，每次生成产生新条码值） -->
    <el-dialog v-model="genBarcodeVisible" title="按单据生成条码" width="480px">
      <el-select
        v-model="genBarcodeBill"
        filterable
        remote
        reserve-keyword
        value-key="id"
        placeholder="输入单据号/标题搜索"
        :remote-method="searchGenBill"
        :loading="genBarcodeSearching"
        style="width: 100%"
      >
        <el-option
          v-for="b in genBarcodeOptions"
          :key="b.id"
          :label="`${b.billNo} - ${b.title || ''}`"
          :value="b"
        />
      </el-select>
      <p style="color:#909399; font-size:12px; margin-top:8px">为所选单据生成实物条码；每次生成产生新的条码值（重打/补打场景），已打印的旧条码仍可追溯。</p>
      <template #footer>
        <el-button @click="genBarcodeVisible = false">取消</el-button>
        <el-button type="primary" :loading="genBarcodeLoading" @click="doGenerateBarcode">生成条码</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
  </div>
</template>

<script setup>
import { onMounted, ref, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deliveryApi, billApi } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_TYPE } from '@/constants/billStatus.js'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'

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

// ==================== Common ====================
// P: tab 页拆分——由菜单深链进来时传入 tab，只显示该页并隐藏 tab 导航条（一 TAB = 一菜单）
const props = defineProps({ tab: { type: String, default: '' } })
const isSingle = computed(() => !!props.tab)
const activeTab = ref(props.tab || 'sign')

function deliveryLabel(t) {
  return ({ POST: '邮寄', HANDOVER: '当面交接', OTHER: '其他' })[t] || t || '—'
}
function billTypeLabel(t) {
  return { LOAN: '借款', REPAY: '还款', APPLY: '事前申请', CLAIM: '报销', PAY: '付款',
    INVOICE: '发票', AR: '应收', AP: '应付', GL: '总账' }[t] || t || ''
}
function statusLabel(s) {
  return { PENDING: '待签收', SIGNED: '已签收', REJECTED: '已拒签' }[s] || s || ''
}
function formatTime(t) {
  if (!t) return '—'
  return formatDateTime(t)
}
function barcodeTypeLabel(t) {
  return { BILL: '单据', ARCHIVE: '档案', BOX: '档案盒' }[t] || t
}
function barcodeStatusLabel(s) {
  return { GENERATED: '已生成', PRINTED: '已打印', SCANNED: '已扫描', SHELVED: '已上架' }[s] || s
}
function barcodeStatusType(s) {
  return { GENERATED: 'info', PRINTED: '', SCANNED: 'warning', SHELVED: 'success' }[s] || 'info'
}

// ==================== Sign Center ====================
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const selectedRows = ref([])

const filters = ref({ status: 'PENDING', billNo: '', expressNo: '', deliveryType: '', keyword: '' })

const scanValue = ref('')
const scanLoading = ref(false)
const scanInputRef = ref(null)

const rejectVisible = ref(false)
const rejectRow = ref(null)
const rejectFormRef = ref(null)
const rejectForm = ref({ reason: '' })
const rejectLoading = ref(false)

const batchSignVisible = ref(false)
const batchSignRemark = ref('')
const batchSignLoading = ref(false)

const byBillVisible = ref(false)
const byBill = ref([])
const byBillCurrentBillId = ref(null)

async function load() {
  try {
    const d = await deliveryApi.page({
      pageNum: pageNum.value, pageSize: pageSize.value,
      ...filters.value,
    })
    rows.value = d.list || []
    total.value = Number(d.total || 0)
  } catch (e) { ElMessage.error(e.message) }
}

function doSearch() {
  pageNum.value = 1
  load()
}

function resetSearch() {
  filters.value = { status: 'PENDING', billNo: '', expressNo: '', deliveryType: '', keyword: '' }
  doSearch()
}

// R5-D-243：与后端 DeliveryService.sign/reject 的 DRAFT/CANCELLED 守卫同口径（billStatus 由服务端回填）
function isDraftLike(row) {
  return ['DRAFT', 'CANCELLED'].includes(row?.billStatus)
}

async function sign(row) {
  // R5-D-241：后端签收已支持签收备注（signRemark），前端由 confirm 改为 prompt 收集留痕；
  // 用户取消（undefined）时直接返回，不再把 cancel 当错误弹。
  const { value: signRemark } = await ElMessageBox.prompt(
    `确认签收单据 ${row.billNo}？可填写签收备注（留痕）`, '签收确认', {
      inputType: 'textarea',
      inputPlaceholder: '签收备注（可空），如：代签人张三 / 章已当面核验',
      confirmButtonText: '确认签收',
      cancelButtonText: '取消',
      inputValidator: (v) => ((v || '').length > 200 ? '签收备注不得超过 200 字' : true),
    }).catch(() => ({}))
  if (signRemark === undefined) return // 用户取消
  try {
    await deliveryApi.sign(row.id, { signRemark })
    ElMessage.success('已签收：' + row.billNo)
    load()
  } catch (e) { ElMessage.error(e.message) }
}

async function doScanSign() {
  if (!scanValue.value?.trim()) {
    ElMessage.warning('请扫码或输入单据号/运单号')
    return
  }
  scanLoading.value = true
  try {
    const r = await deliveryApi.scanSign({ scanValue: scanValue.value.trim(), signRemark: '' })
    ElMessage.success(`扫码签收成功：${r.billNo}`)
    scanValue.value = ''
    load()
  } catch (e) { ElMessage.error(e.message) }
  finally {
    scanLoading.value = false
    nextTick(() => scanInputRef.value?.focus?.())
  }
}

function openReject(row) {
  rejectRow.value = row
  rejectForm.value = { reason: '' }
  rejectVisible.value = true
}

// R5-D-247：上次校验失败的错误文案会残留在重新打开的拒签弹窗上（数据已重置、提示未清），
// 打开即清校验状态。
function onRejectOpen() {
  rejectFormRef.value?.clearValidate()
}

async function doReject() {
  if (!(await validateForms([rejectFormRef]))) return
  rejectLoading.value = true
  try {
    await deliveryApi.reject(rejectRow.value.id, { rejectReason: rejectForm.value.reason })
    ElMessage.success('已拒签：' + rejectRow.value.billNo)
    rejectVisible.value = false
    load()
  } catch (e) { ElMessage.error(e.message) }
  finally { rejectLoading.value = false }
}

async function doBatchSign() {
  if (!selectedRows.value.length) return
  batchSignLoading.value = true
  try {
    const ids = selectedRows.value.map(r => r.id)
    const count = await deliveryApi.batchSign({ ids, signRemark: batchSignRemark.value })
    ElMessage.success(`批量签收成功：${count} 条`)
    batchSignVisible.value = false
    batchSignRemark.value = ''
    load()
  } catch (e) { ElMessage.error(e.message) }
  finally { batchSignLoading.value = false }
}

async function showByBill(row) {
  byBillCurrentBillId.value = row.billId
  try {
    byBill.value = await deliveryApi.byBill(row.billId) || []
    byBillVisible.value = true
  } catch (e) { ElMessage.error(e.message) }
}

// ==================== Apply (交单登记) ====================
const applyFormRef = ref(null)
const applyForm = ref({ billId: null, expressCompany: '', expressNo: '', packageCount: 1, deliveryType: 'POST', remark: '' })
const applyFormRules = {
  // 与后端 DeliveryService.applyDelivery 同口径：单据是交单登记的主体
  billId: commonRules.select('请选择单据'),
}
const rejectFormRules = {
  reason: commonRules.required('请填写拒签原因'),
}
const applyLoading = ref(false)
const myDeliveries = ref([])
const myTotal = ref(0)
const myPageNum = ref(1)
const billOptions = ref([])
const billSearching = ref(false)

async function searchBills(query) {
  if (!query) { billOptions.value = []; return }
  billSearching.value = true
  try {
    const d = await billApi.myPage({ pageNum: 1, pageSize: 20, keyword: query })
    billOptions.value = d.list || []
  } catch (e) { billOptions.value = [] }
  finally { billSearching.value = false }
}

function onBillSelect(id) {
  applyForm.value.billId = id
}

async function doApply() {
  if (!(await validateForms([applyFormRef]))) return
  applyLoading.value = true
  try {
    await deliveryApi.apply(applyForm.value)
    ElMessage.success('交单登记成功')
    applyForm.value = { billId: null, expressCompany: '', expressNo: '', packageCount: 1, deliveryType: 'POST', remark: '' }
    applyFormRef.value?.clearValidate()
    loadMyDeliveries()
  } catch (e) { ElMessage.error(e.message) }
  finally { applyLoading.value = false }
}

async function loadMyDeliveries() {
  try {
    const d = await deliveryApi.myDeliveries({ pageNum: myPageNum.value, pageSize: 10 })
    myDeliveries.value = d.list || []
    myTotal.value = Number(d.total || 0)
  } catch (e) { ElMessage.error(e.message) }
}

// ==================== Barcode ====================
const barcodes = ref([])
const barcodeTotal = ref(0)
const barcodePageNum = ref(1)
const barcodeFilters = ref({ bizType: '', status: '', keyword: '' })

async function loadBarcodes() {
  try {
    const d = await deliveryApi.barcodePage({ pageNum: barcodePageNum.value, pageSize: 10, ...barcodeFilters.value })
    barcodes.value = d.list || []
    barcodeTotal.value = Number(d.total || 0)
  } catch (e) { ElMessage.error(e.message) }
}

// R5-D-245：按单据生成条码（条码管理页此前无写入出口；后端 /barcode/generate 早已在）
const genBarcodeVisible = ref(false)
const genBarcodeBill = ref(null)
const genBarcodeOptions = ref([])
const genBarcodeSearching = ref(false)
const genBarcodeLoading = ref(false)

async function searchGenBill(query) {
  if (!query) { genBarcodeOptions.value = []; return }
  genBarcodeSearching.value = true
  try {
    const d = await billApi.myPage({ pageNum: 1, pageSize: 20, keyword: query })
    genBarcodeOptions.value = d.list || []
  } catch (e) { genBarcodeOptions.value = [] }
  finally { genBarcodeSearching.value = false }
}

function openGenerateBarcode() {
  genBarcodeBill.value = null
  genBarcodeOptions.value = []
  genBarcodeVisible.value = true
}

async function doGenerateBarcode() {
  const b = genBarcodeBill.value
  if (!b?.id) { ElMessage.warning('请先选择单据'); return }
  genBarcodeLoading.value = true
  try {
    const ids = await deliveryApi.generateBarcodes({ bizType: 'BILL', refIds: [b.id], refNos: [b.billNo] })
    ElMessage.success(`已为 ${b.billNo} 生成条码 ${(ids || []).length} 张`)
    genBarcodeVisible.value = false
    loadBarcodes()
  } catch (e) { ElMessage.error(e.message) }
  finally { genBarcodeLoading.value = false }
}

// R5-D-245：档案上架（扫码/输入位置），走后端 /barcode/shelve
async function shelveBarcode(row) {
  const { value: location } = await ElMessageBox.prompt(
    `为条码 ${row.barcode} 指定上架位置`, '档案上架', {
      inputPlaceholder: '如：A区-03架-2层',
      confirmButtonText: '确认上架',
      cancelButtonText: '取消',
      inputValidator: (v) => (!v || !v.trim() ? '请填写上架位置' : (v.trim().length > 64 ? '上架位置不得超过 64 字' : true)),
    }).catch(() => ({}))
  if (location === undefined) return // 用户取消
  try {
    await deliveryApi.shelve({ barcode: row.barcode, location: location.trim() })
    ElMessage.success('已上架：' + row.barcode)
    loadBarcodes()
  } catch (e) { ElMessage.error(e.message) }
}

// ==================== Tab Change ====================
function onTabChange(tab) {
  if (tab === 'apply') loadMyDeliveries()
  else if (tab === 'barcode') loadBarcodes()
  else if (tab === 'sign') {
    load()
    nextTick(() => scanInputRef.value?.focus?.())
  }
}

onMounted(() => {
  onTabChange(activeTab.value)
  nextTick(() => scanInputRef.value?.focus?.())
})

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'billNo', label: '单据号' }, { prop: 'deliverName', label: '交付人' }, { prop: 'expressCompany', label: '快递公司' }, { prop: 'expressNo', label: '运单号' }, { prop: 'barcode', label: '条码' }, { prop: 'packageCount', label: '件数' }, { prop: 'signName', label: '签收人' }]
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
  a.download = `DeliveryPage_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}

</script>

<style scoped>
.scan-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
/* 一 TAB = 一菜单：从菜单深链进来（tab 命中）时隐藏 tab 导航条，只显示该页 */
.tabless :deep(.el-tabs__header) { display: none; }
/* R5-D-243：草稿/已取消单据的签收/拒签被闸时的灰态说明（hover 出原因） */
.sign-blocked-tip { color: #c0c4cc; font-size: 12px; cursor: not-allowed; }
</style>
