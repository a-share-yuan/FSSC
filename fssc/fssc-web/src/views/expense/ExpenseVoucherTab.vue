<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card v-if="!voucher.id" header="凭证预览">
    <el-empty description="请在我的单据中选择带凭证号的单据查看" />
  </el-card>
  <el-card v-else :header="`凭证 ${voucher.voucherNo}（${voucher.status}）`">
    <el-alert type="info" :closable="false" :title="`单据：${selected.billNo} ${selected.title}`" style="margin-bottom: 8px" />
    <div style="margin-bottom: 10px">
      <el-button v-if="selected?.id" type="success" size="small" @click="viewVoucherBillImages">查看单据影像（穿透）</el-button>
      <el-button v-if="selected?.id" type="warning" size="small" @click="openPrint('bill')">打印单据</el-button>
      <el-button v-if="voucher.id" type="primary" size="small" @click="openPrint('voucher')">打印凭证</el-button>
    </div>
    <el-table :data="voucherLines" size="small" border v-loading="loading" @row-dblclick="openRowDetail">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="lineNo" label="行" width="50" sortable />
      <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip sortable />
      <el-table-column prop="subjectCode" label="科目码" width="90" sortable />
      <el-table-column prop="subjectName" label="科目" width="140" sortable />
      <el-table-column prop="direction" label="方向" width="70" sortable>
        <template #default="{ row }">
          <el-tag :type="row.direction === 'DEBIT' ? 'danger' : 'success'" size="small">{{ row.direction === 'DEBIT' ? '借' : '贷' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="amount" label="金额" width="110" sortable :formatter="(r,c,v) => fmt(v)" />
    </el-table>
    <div style="margin-top: 10px">
      <el-button type="primary" :disabled="voucher.status === BillStatus.POSTED" @click="postVoucher">记账（过账）</el-button>
      <span style="margin-left: 12px; color: #909399">借方合计 {{ fmt(voucher.debitTotal) }} / 贷方合计 {{ fmt(voucher.creditTotal) }} / 平衡 {{ voucher.isBalanced === 1 ? '是' : '否' }}</span>
      <!-- D-DEC-288（X-49 乙）：已记账而资金指令未下发的付款腿凭证——只明示注记，不动账、不新开弹窗 -->
      <div v-if="voucher.payLegUnsettled === true" style="margin-top: 6px; color: #e6a23c; font-size: 12px">资金指令未下发（账面已记账，款项尚未付出）</div>
    </div>
  </el-card>

  <PrintDialog v-model:visible="printDialog" :mode="printMode" :bill="printBill" :bill-lines="printBillLines"
               :voucher="voucher" :voucher-lines="voucherLines" :attach-count="printBillLines.length" />
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ref, reactive, watch, inject } from 'vue'
import { ElMessage } from 'element-plus'
import { billApi, sharingApi } from '../../api'
import PrintDialog from '../../components/PrintDialog.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { formatMoney, showRowDetail } from '@/utils/format'

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const props = defineProps({
  selected: { type: Object, default: () => ({}) },
})

const attachViewer = inject('attachViewer')
const voucher = reactive({ id: null, voucherNo: '', status: '', debitTotal: 0, creditTotal: 0, isBalanced: 0 })
const voucherLines = ref([])
const loading = ref(false)

const printDialog = ref(false)
const printMode = ref('bill')
const printBill = ref(null)
const printBillLines = ref([])

function fmt(v) { return formatMoney(v, '0.00') }

async function loadVoucher() {
  if (!props.selected?.id) return
  if (!props.selected.voucherNo) { ElMessage.info('该单据尚未生成凭证（需审批通过）'); return }
  loading.value = true
  try {
    const list = (await sharingApi.voucherPage({ pageNum: 1, pageSize: 50 })).list || []
    const v = list.find((x) => x.billId === props.selected.id) || list.find((x) => x.voucherNo === props.selected.voucherNo)
    if (v) {
      Object.assign(voucher, v)
      voucherLines.value = await sharingApi.voucherLines(v.id)
    }
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function postVoucher() {
  try {
    await sharingApi.voucherPost(voucher.id)
    ElMessage.success('已记账')
    const list = (await sharingApi.voucherPage({ pageNum: 1, pageSize: 50 })).list || []
    const v = list.find((x) => x.id === voucher.id)
    if (v) Object.assign(voucher, v)
  } catch (e) { ElMessage.error(e.message) }
}

async function viewVoucherBillImages() {
  if (!props.selected?.id) return
  try {
    const d = await billApi.detail(props.selected.id)
    const fileIds = (d?.attaches || []).map((a) => a.fileId).filter((x) => x != null)
    if (!fileIds.length) { ElMessage.info('该单据无影像/附件'); return }
    attachViewer.value?.open(fileIds, '单据影像：' + (props.selected.billNo || ''))
  } catch (e) { ElMessage.error(e.message) }
}

async function openPrint(mode) {
  printMode.value = mode
  if (mode === 'bill') {
    const d = await billApi.detail(props.selected.id)
    printBill.value = { ...d.bill, billType: props.selected.billType }
    printBillLines.value = d.lines || []
  } else {
    printBill.value = null
    printBillLines.value = []
  }
  printDialog.value = true
}

watch(() => props.selected, (val) => {
  if (val?.id) loadVoucher()
}, { deep: true })

function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
}

defineExpose({ loadVoucher })
</script>
