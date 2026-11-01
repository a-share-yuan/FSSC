<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-dialog v-model="innerVisible" :title="mode === 'voucher' ? '凭证套打预览' : '单据打印预览'" width="720px" top="3vh"
             destroy-on-close append-to-body class="print-dialog">
    <div class="print-area">
      <!-- 单据打印 -->
      <template v-if="mode === 'bill' && bill">
        <h2 class="print-title">{{ billTitle }}</h2>
        <table class="p-head">
          <tr><td>单号：{{ bill.billNo }}</td><td>制单日期：{{ bill.applyDate || '-' }}</td></tr>
          <tr><td>申请人：{{ bill.applyUserName || '-' }}</td><td>部门：{{ bill.deptName || '-' }}</td></tr>
          <tr><td>公司：{{ bill.companyName || '-' }}</td><td>状态：{{ statusLabel(bill.status) }}</td></tr>
        </table>
        <table class="p-detail" border="1" cellspacing="0">
          <colgroup><col style="width:22%"><col style="width:58%"><col style="width:20%"></colgroup>
          <thead><tr><th>费用项</th><th>摘要</th><th>金额</th></tr></thead>
          <tbody>
            <tr v-for="(l, i) in billLines" :key="i">
              <td>{{ l.expenseItemName || l.expenseItemId }}</td>
              <td>{{ l.summary }}</td>
              <td class="num">{{ fmt(l.amount) }}</td>
            </tr>
          </tbody>
          <tfoot><tr><td colspan="2" class="num">合计</td><td class="num">{{ fmt(bill.totalAmount) }}</td></tr></tfoot>
        </table>
        <p class="p-foot">审批：{{ statusLabel(bill.status) }}　　制单人：{{ makerName }}　　打印日期：{{ today }}</p>
      </template>

      <!-- 凭证套打 -->
      <template v-else-if="mode === 'voucher' && voucher">
        <h2 class="print-title">记 账 凭 证</h2>
        <table class="p-head">
          <tr><td>凭证号：{{ voucher.voucherNo }}</td><td>日期：{{ voucher.voucherDate || '' }}</td><td>附单据：{{ attachCount }} 张</td></tr>
        </table>
        <table class="p-detail" border="1" cellspacing="0">
          <thead><tr><th style="width:50px">行</th><th>摘要</th><th>科目</th><th style="width:90px">借方金额</th><th style="width:90px">贷方金额</th></tr></thead>
          <tbody>
            <tr v-for="(l, i) in voucherLines" :key="i">
              <td>{{ l.lineNo || i + 1 }}</td>
              <td>{{ l.summary }}</td>
              <td>{{ l.subjectName || l.subjectCode }}</td>
              <td class="num">{{ l.direction === 'DEBIT' ? fmt(l.amount) : '' }}</td>
              <td class="num">{{ l.direction === 'CREDIT' ? fmt(l.amount) : '' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr><td colspan="3" class="num">合计</td><td class="num">{{ fmt(voucher.debitTotal) }}</td><td class="num">{{ fmt(voucher.creditTotal) }}</td></tr>
          </tfoot>
        </table>
        <p class="p-foot">制单：　　　　审核：　　　　记账：　　　　出纳：　　　　打印日期：{{ today }}</p>
      </template>
    </div>
    <template #footer>
      <el-button @click="innerVisible = false">关闭</el-button>
      <el-button type="primary" @click="doPrint">打印</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { statusLabel } from '@/constants/billStatus.js'
import { billTypeLabel } from '@/constants/enums.js'

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'bill' }, // bill 单据 / voucher 凭证
  bill: { type: Object, default: null },
  billLines: { type: Array, default: () => [] },
  voucher: { type: Object, default: null },
  voucherLines: { type: Array, default: () => [] },
  attachCount: { type: Number, default: 0 },
})
const emit = defineEmits(['update:visible'])

const innerVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const today = new Date().toLocaleDateString('zh-CN')

const billTitle = computed(() => {
  const t = billTypeLabel(props.bill?.billType)
  return t ? `${t}单` : '单据'
})

const makerName = computed(() => {
  const b = props.bill || {}
  if (b.createByName) return b.createByName
  return b.createBy != null && b.createBy === b.applyUserId ? (b.applyUserName || '-') : '-'
})

function fmt(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

function doPrint() {
  const html = document.querySelector('.print-dialog .print-area')?.innerHTML || ''
  if (!html) return
  const w = window.open('', '_blank', 'width=820,height=600')
  if (!w) return
  w.document.write(`<html><head><title>${billTitle.value || '打印'}</title><style>
    @page { size: A4; margin: 12mm; }
    body { font-family: SimSun, serif; font-size: 13px; color: #000; margin: 24px; }
    .print-title { text-align: center; font-size: 18px; font-weight: bold; letter-spacing: 4px; margin-bottom: 12px; }
    table { width: 100%; border-collapse: collapse; }
    .p-head td { padding: 3px 6px; }
    .p-detail th, .p-detail td { border: 1px solid #000; padding: 4px 6px; text-align: left; }
    .p-detail .num { text-align: right; }
    .p-foot { margin-top: 12px; }
  </style></head><body>${html}</body></html>`)
  w.document.close()
  w.focus()
  w.print()
  setTimeout(() => w.close(), 500)
}
</script>

<style scoped>
.print-area .print-title { text-align: center; font-size: 18px; font-weight: bold; letter-spacing: 4px; margin: 6px 0 12px; }
.print-area .p-head,
.print-area .p-detail { width: 100%; border-collapse: collapse; }
.print-area .p-head td { padding: 3px 6px; }
.print-area .p-detail th,
.print-area .p-detail td { border: 1px solid #000; padding: 4px 6px; text-align: left; }
.print-area .p-detail .num { text-align: right; }
.print-area .p-foot { margin-top: 12px; }
</style>
