<template>
  <div>
    <el-card>
    <template #header>
      <div style="display:flex; gap:10px; align-items:center">
        <b>{{ $t('sharing.delivery.pageTitle') }}</b>
        <span style="color:#909399; font-size:12px">{{ $t('sharing.delivery.hintSub') }}</span>
        <el-button type="primary" size="small" style="margin-left:auto" @click="load">{{ $t('common.refresh') }}</el-button>
      </div>
    </template>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ==================== 签收中心 ==================== -->
      <el-tab-pane :label="$t('sharing.delivery.tabSign')" name="sign">
        <!-- 扫码签收栏 -->
        <div class="scan-bar">
          <el-input
            ref="scanInputRef"
            v-model="scanValue"
            :placeholder="$t('sharing.delivery.phScanInput')"
            size="large"
            style="width:400px"
            @keyup.enter="doScanSign"
          />
          <el-button type="primary" size="large" @click="doScanSign" :loading="scanLoading">
            {{ $t('sharing.delivery.btnScanSign') }}</el-button>
          <el-divider direction="vertical" />
          <el-button size="large" @click="batchSignVisible = true" :disabled="!selectedRows.length">
            {{ $t('sharing.delivery.btnBatchSignN', { v1: selectedRows.length }) }}</el-button>
          <el-button size="large" @click="activeTab = 'apply'">{{ $t('sharing.delivery.tabApply') }}</el-button>
        </div>

        <!-- 筛选栏 -->
        <div class="filter-bar">
          <el-select v-model="filters.status" :placeholder="$t('common.status')" clearable size="small" style="width:120px">
            <el-option :label="$t('sharing.delivery.optToSign')" value="PENDING" />
            <el-option :label="$t('sharing.delivery.optSigned')" value="SIGNED" />
            <el-option :label="$t('sharing.delivery.optRefused')" value="REJECTED" />
          </el-select>
          <el-input v-model="filters.billNo" :placeholder="$t('sharing.common.colDocNo')" clearable size="small" style="width:180px" />
          <el-input v-model="filters.expressNo" :placeholder="$t('sharing.delivery.phWaybill')" clearable size="small" style="width:180px" />
          <el-select v-model="filters.deliveryType" :placeholder="$t('sharing.delivery.phHandoverType')" clearable size="small" style="width:120px">
            <el-option :label="$t('sharing.delivery.optPost')" value="POST" />
            <el-option :label="$t('sharing.delivery.optHandover')" value="HANDOVER" />
            <el-option :label="$t('sharing.common.colOther')" value="OTHER" />
          </el-select>
          <el-input v-model="filters.keyword" :placeholder="$t('sharing.common.labKeyword')" clearable size="small" style="width:160px" @keyup.enter="doSearch" />
          <el-button type="primary" size="small" @click="doSearch">{{ $t('common.search') }}</el-button>
          <el-button size="small" @click="resetSearch">{{ $t('common.reset') }}</el-button>
      <el-button size="small" @click="exportCsv">{{ $t('sharing.common.btnExportCsv') }}</el-button>
        </div>

        <!-- 签收列表 -->
<el-table :data="rows" size="small" border stripe :max-height="480" @row-dblclick="openRowDetail"
          @selection-change="(v) => selectedRows = v" :empty-text="$t('common.empty')">
          <el-table-column type="selection" width="40" :selectable="(r) => r.status === 'PENDING'" />
          <el-table-column prop="billNo" :label="$t('sharing.common.colDocNo')" width="190"  sortable />
          <el-table-column :label="$t('field.type')" width="90">
            <template #default="{ row }">{{ billTypeLabel(row.billType) }}</template>
          </el-table-column>
          <el-table-column prop="deliverName" :label="$t('sharing.delivery.colDeliverer')" width="100"  sortable />
          <el-table-column :label="$t('sharing.common.colMethod')" width="80">
            <template #default="{ row }">{{ deliveryLabel(row.deliveryType) }}</template>
          </el-table-column>
          <el-table-column prop="expressCompany" :label="$t('sharing.delivery.colCourier')" width="90"  sortable />
          <el-table-column prop="expressNo" :label="$t('sharing.delivery.phWaybill')" width="140" show-overflow-tooltip  sortable />
          <el-table-column prop="barcode" :label="$t('sharing.delivery.colBarcode')" width="140" show-overflow-tooltip  sortable />
          <el-table-column prop="packageCount" :label="$t('sharing.delivery.colPackages')" width="55" align="center"  sortable />
          <el-table-column :label="$t('sharing.delivery.colDeliverTime')" width="145">
            <template #default="{ row }">{{ formatTime(row.deliverTime) }}</template>
          </el-table-column>
          <el-table-column :label="$t('common.status')" width="85" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'SIGNED'" type="success" size="small">{{ $t('sharing.delivery.optSigned') }}</el-tag>
              <el-tag v-else-if="row.status === BillStatus.REJECTED" type="danger" size="small">{{ $t('sharing.delivery.optRefused') }}</el-tag>
              <el-tag v-else type="warning" size="small">{{ $t('sharing.delivery.optToSign') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="signName" :label="$t('sharing.delivery.colSigner')" width="100"  sortable />
          <el-table-column :label="$t('common.operation')" width="190" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="openTrace(row)">{{ $t('sharing.common.btnTrace') }}</el-button>
              <!-- R5-D-243：单据 DRAFT/CANCELLED 时后端签收/拒签闸门必拒（先提交单据才能签），
                   前端同口径隐藏入口并给出原因，避免"点了必失败"；billStatus 由服务端批量回填。 -->
              <template v-if="row.status === 'PENDING'">
                <template v-if="!isDraftLike(row)">
                  <el-button link type="primary" v-prevent-double-click @click="sign(row)">{{ $t('sharing.delivery.btnSign') }}</el-button>
                  <el-button link type="danger" @click="openReject(row)">{{ $t('sharing.delivery.btnReject') }}</el-button>
                </template>
                <el-tooltip v-else placement="top" :content="$t('sharing.delivery.hintDraftBlocked', { v1: row.billStatus })">
                  <span class="sign-blocked-tip">{{ $t('sharing.delivery.hintSignBlocked') }}</span>
                </el-tooltip>
              </template>
              <el-button link type="info" @click="showByBill(row)">{{ $t('sharing.delivery.btnRecords') }}</el-button>
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
      <el-tab-pane :label="$t('sharing.delivery.tabApply')" name="apply">
        <el-form ref="applyFormRef" :model="applyForm" :rules="applyFormRules" label-width="100px" style="max-width:600px" size="default">
          <el-form-item :label="$t('sharing.delivery.btnPickBill')" prop="billId">
            <el-select
              v-model="applyForm.billId"
              filterable
              remote
              reserve-keyword
              :placeholder="$t('sharing.delivery.phBillSearch')"
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
          <!-- V1.4.92（AMC-01）：非集中档案管理公司的纸质原件留原单位，后端 applyDelivery 必拒，前端同口径前置说明 -->
          <el-form-item v-if="applyNonCentral">
            <el-alert type="info" :closable="false" show-icon :title="$t('sharing.delivery.hintNonCentralArchive')" />
          </el-form-item>
          <el-form-item :label="$t('sharing.delivery.colCourier')" v-if="applyForm.deliveryType === 'POST'">
            <el-select v-model="applyForm.expressCompany" :placeholder="$t('sharing.delivery.phPickCourier')" clearable style="width:220px">
              <el-option :label="$t('sharing.delivery.optSf')" value="SF" />
              <el-option :label="$t('sharing.delivery.optJd')" value="JD" />
              <el-option :label="$t('sharing.delivery.optZto')" value="ZTO" />
              <el-option :label="$t('sharing.delivery.optYto')" value="YTO" />
              <el-option :label="$t('sharing.delivery.optSto')" value="STO" />
              <el-option label="EMS" value="EMS" />
              <el-option :label="$t('sharing.delivery.optDbl')" value="DBL" />
              <el-option :label="$t('sharing.common.colOther')" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('sharing.delivery.phWaybill')" v-if="applyForm.deliveryType === 'POST'">
            <el-input v-model="applyForm.expressNo" :placeholder="$t('sharing.delivery.phExpressNo')" style="width:280px" />
          </el-form-item>
          <el-form-item :label="$t('sharing.delivery.colPackages')">
            <el-input-number v-model="applyForm.packageCount" :min="1" :max="99" style="width:120px" />
          </el-form-item>
          <el-form-item :label="$t('sharing.delivery.phHandoverType')">
            <el-select v-model="applyForm.deliveryType" style="width:160px">
              <el-option :label="$t('sharing.delivery.optPost')" value="POST" />
              <el-option :label="$t('sharing.delivery.optHandover')" value="HANDOVER" />
              <el-option :label="$t('sharing.common.colOther')" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('common.remark')">
            <el-select v-model="applyForm.remark" filterable allow-create default-first-option :placeholder="$t('sharing.delivery.phRemarkOptions')" style="width: 100%"><el-option :label="$t('sharing.delivery.optNormalHandover')" value="正常交接" /><el-option :label="$t('sharing.delivery.optRush')" value="加急处理" /><el-option :label="$t('sharing.delivery.optExtraDocs')" value="补充资料" /><el-option :label="$t('sharing.delivery.optSpecial')" value="特殊说明" /><el-option :label="$t('sharing.common.colOther')" value="其他" /></el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doApply" :loading="applyLoading" :disabled="applyNonCentral">{{ $t('sharing.delivery.btnSubmitApply') }}</el-button>
          </el-form-item>
        </el-form>

        <!-- 我的交单记录 -->
        <el-divider content-position="left">{{ $t('sharing.delivery.secMyApply') }}</el-divider>
        <el-table :data="myDeliveries" size="small" border stripe :max-height="300" @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
          <el-table-column prop="billNo" :label="$t('sharing.common.colDocNo')" width="190"  sortable />
          <el-table-column prop="expressCompany" :label="$t('sharing.delivery.colCourier')" width="90"  sortable />
          <el-table-column prop="expressNo" :label="$t('sharing.delivery.phWaybill')" width="140" show-overflow-tooltip  sortable />
          <el-table-column prop="packageCount" :label="$t('sharing.delivery.colPackages')" width="55" align="center"  sortable />
          <el-table-column :label="$t('sharing.common.colMethod')" width="80">
            <template #default="{ row }">{{ deliveryLabel(row.deliveryType) }}</template>
          </el-table-column>
          <el-table-column :label="$t('common.status')" width="85" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SIGNED' ? 'success' : row.status === BillStatus.REJECTED ? 'danger' : 'warning'" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('sharing.delivery.colDeliverTime')" width="145">
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
      <el-tab-pane :label="$t('sharing.delivery.tabBarcode')" name="barcode">
        <div class="filter-bar">
          <el-select v-model="barcodeFilters.bizType" :placeholder="$t('sharing.common.colBizType')" clearable size="small" style="width:120px">
            <el-option :label="$t('sharing.delivery.optBarBill')" value="BILL" />
            <el-option :label="$t('sharing.delivery.optBarArchive')" value="ARCHIVE" />
            <el-option :label="$t('sharing.delivery.optBarBox')" value="BOX" />
          </el-select>
          <el-select v-model="barcodeFilters.status" :placeholder="$t('common.status')" clearable size="small" style="width:120px">
            <el-option :label="$t('sharing.delivery.optGenerated')" value="GENERATED" />
            <el-option :label="$t('sharing.delivery.optPrinted')" value="PRINTED" />
            <el-option :label="$t('sharing.delivery.optScanned')" value="SCANNED" />
            <el-option :label="$t('sharing.delivery.optShelved')" value="SHELVED" />
          </el-select>
          <el-input v-model="barcodeFilters.keyword" :placeholder="$t('sharing.delivery.phBarcodeKeyword')" clearable size="small" style="width:200px" @keyup.enter="loadBarcodes" />
          <el-button type="primary" size="small" @click="loadBarcodes">{{ $t('common.search') }}</el-button>
          <!-- R5-D-245：条码管理此前只读列表，无生成/上架入口；后端 /barcode/generate、/barcode/shelve 早已在 -->
          <el-button type="success" size="small" @click="openGenerateBarcode">{{ $t('sharing.delivery.btnGenByBill') }}</el-button>
        </div>

        <el-table :data="barcodes" size="small" border stripe :max-height="420" @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
          <el-table-column prop="barcode" :label="$t('sharing.delivery.colBarcodeValue')" width="220"  sortable />
          <el-table-column :label="$t('field.type')" width="80">
            <template #default="{ row }">{{ barcodeTypeLabel(row.bizType) }}</template>
          </el-table-column>
          <el-table-column prop="refNo" :label="$t('sharing.delivery.colRefNo')" width="180" show-overflow-tooltip  sortable />
          <el-table-column :label="$t('common.status')" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="barcodeStatusType(row.status)" size="small">{{ barcodeStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="printName" :label="$t('sharing.delivery.colPrinter')" width="90"  sortable />
          <el-table-column :label="$t('sharing.delivery.colPrintTime')" width="145">
            <template #default="{ row }">{{ formatTime(row.printTime) }}</template>
          </el-table-column>
          <el-table-column prop="scanName" :label="$t('sharing.delivery.colScanner')" width="90"  sortable />
          <el-table-column prop="location" :label="$t('sharing.delivery.colLocation')" width="120"  sortable />
          <el-table-column :label="$t('common.operation')" width="80" fixed="right">
            <template #default="{ row }">
              <!-- R5-D-245：档案上架入口（扫码/输入位置）——未上架条码可操作，已上架只读展示 -->
              <el-button v-if="row.status !== 'SHELVED'" link type="primary" v-prevent-double-click @click="shelveBarcode(row)">{{ $t('sharing.delivery.btnShelve') }}</el-button>
              <span v-else style="color:#909399; font-size:12px">{{ $t('sharing.delivery.optShelved') }}</span>
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
    <el-dialog v-model="rejectVisible" :title="$t('sharing.delivery.titleReject')" width="450px" @open="onRejectOpen">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectFormRules" label-width="80px">
        <el-form-item :label="$t('sharing.common.colDocNo')">
          <span>{{ rejectRow?.billNo }}</span>
        </el-form-item>
        <el-form-item :label="$t('sharing.delivery.colRejectReason')" prop="reason">
          <el-select v-model="rejectForm.reason" filterable allow-create default-first-option :placeholder="$t('sharing.delivery.phRejectReason')" style="width: 100%"><el-option :label="$t('sharing.delivery.optDocWrong')" value="单据信息有误" /><el-option :label="$t('sharing.delivery.optAttachMissing')" value="附件缺失" /><el-option :label="$t('sharing.delivery.optSignerAbsent')" value="签收人不在" /><el-option :label="$t('sharing.delivery.optAddressWrong')" value="地址错误" /><el-option :label="$t('sharing.delivery.optDocExpired')" value="单据已过期" /><el-option :label="$t('sharing.common.colOther')" value="其他" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" @click="doReject" :loading="rejectLoading">{{ $t('sharing.delivery.btnConfirmReject') }}</el-button>
      </template>
    </el-dialog>

    <!-- 批量签收弹窗 -->
    <el-dialog v-model="batchSignVisible" :title="$t('sharing.delivery.btnBatchSign')" width="450px">
      <p>{{ $t('sharing.delivery.labBatchSignLead') }}<b>{{ selectedRows.length }}</b> {{ $t('sharing.delivery.labToSign') }}</p>
      <el-select v-model="batchSignRemark" filterable allow-create default-first-option :placeholder="$t('sharing.delivery.phBatchRemark')" style="margin-top:8px; width: 100%"><el-option :label="$t('sharing.delivery.optNormalSign')" value="正常签收" /><el-option :label="$t('sharing.delivery.optDelegated')" value="委托代签" /><el-option :label="$t('sharing.delivery.optPartial')" value="部分签收" /><el-option :label="$t('sharing.common.colOther')" value="其他" /></el-select>
      <template #footer>
        <el-button @click="batchSignVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doBatchSign" :loading="batchSignLoading">{{ $t('sharing.delivery.btnConfirmSign') }}</el-button>
      </template>
    </el-dialog>

    <!-- 单据交付记录弹窗 -->
    <el-dialog v-model="byBillVisible" :title="$t('sharing.delivery.titleRecords', { v1: byBillCurrentBillId || '' })" width="720px">
      <el-table :data="byBill" size="small" border @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
        <el-table-column prop="billNo" :label="$t('sharing.common.colDocNo')" width="190"  sortable />
        <el-table-column prop="deliverName" :label="$t('sharing.delivery.colDeliverer')" width="100"  sortable />
        <el-table-column prop="expressCompany" :label="$t('sharing.delivery.colCourier')" width="90"  sortable />
        <el-table-column prop="expressNo" :label="$t('sharing.delivery.phWaybill')" width="130" show-overflow-tooltip  sortable />
        <el-table-column :label="$t('sharing.common.colMethod')" width="80">
          <template #default="{ row }">{{ deliveryLabel(row.deliveryType) }}</template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SIGNED' ? 'success' : row.status === BillStatus.REJECTED ? 'danger' : 'warning'" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- R5-D-245：按单据生成条码（重打/补打场景，每次生成产生新条码值） -->
    <el-dialog v-model="genBarcodeVisible" :title="$t('sharing.delivery.btnGenByBill')" width="480px">
      <el-select
        v-model="genBarcodeBill"
        filterable
        remote
        reserve-keyword
        value-key="id"
        :placeholder="$t('sharing.delivery.phBillSearch')"
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
      <p style="color:#909399; font-size:12px; margin-top:8px">{{ $t('sharing.delivery.hintGenBarcode') }}</p>
      <template #footer>
        <el-button @click="genBarcodeVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="genBarcodeLoading" @click="doGenerateBarcode">{{ $t('sharing.delivery.btnGenerateBarcodes') }}</el-button>
      </template>
    </el-dialog>
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
  </div>
</template>

<script setup>
import { onMounted, ref, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deliveryApi, billApi, orgApi } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_TYPE } from '@/constants/billStatus.js'
import { formatDate, formatDateTime, showRowDetail } from '@/utils/format'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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
  return showRowDetail(row, { title: t('field.detail') })
}

// ==================== Common ====================
// P: tab 页拆分——菜单深链传入 tab 时只作为默认页签，页签条保持可点（D-BIZ-05）
const props = defineProps({ tab: { type: String, default: '' } })
const activeTab = ref(props.tab || 'sign')

function deliveryLabel(kind) {
  return ({ POST: t('sharing.delivery.optPost'), HANDOVER: t('sharing.delivery.optHandover'), OTHER: t('sharing.common.colOther') })[kind] || kind || '—'
}
function billTypeLabel(code) {
  return { LOAN: t('sharing.common.colLoan'), REPAY: t('sharing.common.colRepay'), APPLY: t('sharing.common.colPreApply'), CLAIM: t('sharing.common.colClaim'), PAY: t('sharing.common.colPay'),
    INVOICE: t('sharing.common.colInvoice'), AR: t('sharing.common.colAr'), AP: t('sharing.common.colAp'), GL: t('sharing.common.colGl') }[code] || code || ''
}
function statusLabel(s) {
  return { PENDING: t('sharing.delivery.optToSign'), SIGNED: t('sharing.delivery.optSigned'), REJECTED: t('sharing.delivery.optRefused') }[s] || s || ''
}
function formatTime(t) {
  if (!t) return '—'
  return formatDateTime(t)
}
function barcodeTypeLabel(code) {
  return { BILL: t('sharing.delivery.optBarBill'), ARCHIVE: t('sharing.delivery.optBarArchive'), BOX: t('sharing.delivery.optBarBox') }[code] || code
}
function barcodeStatusLabel(s) {
  return { GENERATED: t('sharing.delivery.optGenerated'), PRINTED: t('sharing.delivery.optPrinted'), SCANNED: t('sharing.delivery.optScanned'), SHELVED: t('sharing.delivery.optShelved') }[s] || s
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

// 签收与上架都用 ElMessageBox.prompt：连点两次会在 body 上叠出两层同款弹窗，
// 确认后只关掉最上面一层，下面那层仍在（用户观感「点了确认签收对话框一直没关闭」）。
// 用模块级锁拦住重入，不再另造弹窗机制。
let signPromptOpen = false
async function sign(row) {
  if (signPromptOpen) return
  signPromptOpen = true
  try {
    // R5-D-241：后端签收已支持签收备注（signRemark），前端由 confirm 改为 prompt 收集留痕；
    // 用户取消（undefined）时直接返回，不再把 cancel 当错误弹。
    const { value: signRemark } = await ElMessageBox.prompt(
      t('sharing.delivery.confirmSign', { v1: row.billNo }), t('sharing.delivery.titleSignConfirm'), {
        inputType: 'textarea',
        inputPlaceholder: t('sharing.delivery.phSignRemark'),
        confirmButtonText: t('sharing.delivery.btnConfirmSign'),
        cancelButtonText: t('common.cancel'),
        inputValidator: (v) => ((v || '').length > 200 ? t('sharing.delivery.errSignRemark') : true),
      }).catch(() => ({}))
    if (signRemark === undefined) return // 用户取消
    await deliveryApi.sign(row.id, { signRemark })
    ElMessage.success(t('sharing.delivery.msgSignedOk') + row.billNo)
    load()
  } catch (e) { ElMessage.error(e.message) }
  finally { signPromptOpen = false }
}

async function doScanSign() {
  if (scanLoading.value) return
  if (!scanValue.value?.trim()) {
    ElMessage.warning(t('sharing.delivery.errScanInput'))
    return
  }
  scanLoading.value = true
  try {
    const r = await deliveryApi.scanSign({ scanValue: scanValue.value.trim(), signRemark: '' })
    ElMessage.success(t('sharing.delivery.msgScanOk', { v1: r.billNo }))
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
    ElMessage.success(t('sharing.delivery.msgRefusedOk') + rejectRow.value.billNo)
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
    ElMessage.success(t('sharing.delivery.msgBatchSignOk', { v1: count }))
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
const applyFormRules = computed(() => ({
  // 与后端 DeliveryService.applyDelivery 同口径：单据是交单登记的主体
  billId: commonRules.select(t('sharing.delivery.errPickBill')),
}))
const rejectFormRules = computed(() => ({
  reason: commonRules.required(t('sharing.delivery.errRejectReason')),
}))
const applyLoading = ref(false)
const myDeliveries = ref([])
const myTotal = ref(0)
const myPageNum = ref(1)
const billOptions = ref([])
const billSearching = ref(false)
// V1.4.92（AMC-01）：公司档案管理模式（companyTree 返回 SysCompany.archiveMode）
const companyById = ref({})
const applyBill = ref(null)
const applyNonCentral = computed(() => {
  const c = applyBill.value && companyById.value[applyBill.value.companyId]
  return c?.archiveMode === 'NON_CENTRAL'
})

async function loadCompanies() {
  try {
    const list = await orgApi.companyTree()
    const m = {}
    ;(list || []).forEach((c) => { m[c.id] = c })
    companyById.value = m
  } catch { /* 模式信息不可得时按集中处理，交单链路由后端同一判据兜底 */ }
}

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
  applyBill.value = billOptions.value.find((b) => b.id === id) || null
}

async function doApply() {
  if (!(await validateForms([applyFormRef]))) return
  if (applyNonCentral.value) { ElMessage.warning(t('sharing.delivery.hintNonCentralArchive')); return }
  applyLoading.value = true
  try {
    await deliveryApi.apply(applyForm.value)
    ElMessage.success(t('sharing.delivery.msgApplyOk'))
    applyForm.value = { billId: null, expressCompany: '', expressNo: '', packageCount: 1, deliveryType: 'POST', remark: '' }
    applyBill.value = null
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
  if (!b?.id) { ElMessage.warning(t('sharing.delivery.warnPickBill')); return }
  genBarcodeLoading.value = true
  try {
    const ids = await deliveryApi.generateBarcodes({ bizType: 'BILL', refIds: [b.id], refNos: [b.billNo] })
    ElMessage.success(t('sharing.delivery.msgGenOk', { v1: b.billNo, v2: (ids || []).length }))
    genBarcodeVisible.value = false
    loadBarcodes()
  } catch (e) { ElMessage.error(e.message) }
  finally { genBarcodeLoading.value = false }
}

// R5-D-245：档案上架（扫码/输入位置），走后端 /barcode/shelve
let shelvePromptOpen = false
async function shelveBarcode(row) {
  if (shelvePromptOpen) return
  shelvePromptOpen = true
  try {
    const { value: location } = await ElMessageBox.prompt(
      t('sharing.delivery.promptShelve', { v1: row.barcode }), t('sharing.delivery.titleShelve'), {
        inputPlaceholder: t('sharing.delivery.phShelveLocation'),
        confirmButtonText: t('sharing.delivery.btnConfirmShelve'),
        cancelButtonText: t('common.cancel'),
        inputValidator: (v) => (!v || !v.trim() ? t('sharing.delivery.errShelveRequired') : (v.trim().length > 64 ? t('sharing.delivery.errShelveTooLong') : true)),
      }).catch(() => ({}))
    if (location === undefined) return // 用户取消
    await deliveryApi.shelve({ barcode: row.barcode, location: location.trim() })
    ElMessage.success(t('sharing.delivery.msgShelved') + row.barcode)
    loadBarcodes()
  } catch (e) { ElMessage.error(e.message) }
  finally { shelvePromptOpen = false }
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
  loadCompanies()
})

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning(t('sharing.common.msgNothingToExport')); return }
  const cols = [{ prop: 'billNo', label: t('sharing.common.colDocNo') }, { prop: 'deliverName', label: t('sharing.delivery.colDeliverer') }, { prop: 'expressCompany', label: t('sharing.delivery.colCourier') }, { prop: 'expressNo', label: t('sharing.delivery.phWaybill') }, { prop: 'barcode', label: t('sharing.delivery.colBarcode') }, { prop: 'packageCount', label: t('sharing.delivery.colPackages') }, { prop: 'signName', label: t('sharing.delivery.colSigner') }]
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
  ElMessage.success(t('sharing.common.msgCsvExported'))
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
/* R5-D-243：草稿/已取消单据的签收/拒签被闸时的灰态说明（hover 出原因） */
.sign-blocked-tip { color: #c0c4cc; font-size: 12px; cursor: not-allowed; }
</style>
