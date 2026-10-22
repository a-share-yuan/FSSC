<template>
  <div>
    <div style="display:flex; gap:8px; margin-bottom:10px">
      <el-button type="success" size="small" @click="openReceipt">{{ $t('flow.arRcpt.btnRegister') }}</el-button>
    </div>
    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="receipts" size="small" border stripe v-else @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" :label="$t('flow.arRcpt.colRcptNo')" width="130" sortable />
      <el-table-column :label="$t('flow.arRcpt.colPartner')" min-width="120">
        <template #default="{ row }">{{ customerName(row.customerId) }}</template>
      </el-table-column>
      <el-table-column :label="$t('flow.arRcpt.colRcptAmount')" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="90">
        <template #default="{ row }">{{ rcStatusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="140" fixed="right">
        <template #default="{ row }">
          <!-- D-DEC-423④：登记即冲减（D-V3-143），终态行不再给「先进先出核销」按钮，避免重复核销 -->
          <el-button v-if="!receiptAppliedStatuses.includes(row.status)" link size="small" type="primary" @click="applyReceipt(row)">{{ $t('flow.arRcpt.btnFifoApply') }}</el-button>
          <el-tag v-else size="small" type="success" effect="plain">{{ $t('flow.arRcpt.tagAppliedOnRegister') }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlgReceipt" :title="$t('flow.arRcpt.dlgRegisterTitle')" width="480px" destroy-on-close>
      <el-form ref="receiptFormRef" :model="rcForm" :rules="receiptRules" label-width="120px">
        <el-form-item :label="$t('flow.arRcpt.labPartner')" prop="customerId">
          <el-select v-model="rcForm.customerId" filterable :placeholder="$t('flow.arRcpt.phPartner')" style="width:100%">
            <el-option v-for="c in customers" :key="c.id" :label="(c.customerName || c.supplierName || c.name || '')" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('flow.arRcpt.labAmount')" prop="amount"><el-input-number v-model="rcForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('flow.arRcpt.labReceiptDate')" prop="receiptDate"><el-date-picker v-model="rcForm.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgReceipt = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveReceipt">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="RECEIPT" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { receivableApi, mdApi } from '@/api'
import SkeletonList from '@/components/SkeletonList.vue'
import BillTraceDrawer from '@/components/BillTraceDrawer.vue'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { showRowDetail } from '@/utils/format'
import { rcStatusLabel } from '@/constants/enums'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const traceVisible = ref(false)
const traceBillId = ref(null)
function openRowDetail(row) {
  traceBillId.value = row.id
  traceVisible.value = true
  return showRowDetail(row, { title: t('flow.arRcpt.ttlDetail') })
}

const loading = ref(false)
const receipts = ref([])
const customers = ref([])
function customerName(id) { const c = customers.value.find((x) => x.id === id); return c ? (c.customerName || c.supplierName || c.name || '—') : (id ? '#' + id : '—') }

const dlgReceipt = ref(false)
const rcForm = ref({ customerId: null, amount: 0, receiptDate: '' })
const receiptFormRef = ref(null)
const { rules: receiptRules } = useFormRules('RECEIPT')
const { submit: guardReceipt } = useSubmitGuard(receiptFormRef)

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// D-DEC-423④：收款单"已核销"终态词表（后端 RcReceiptService.APPLIED_RECEIPT_STATUSES 同口径）
const receiptAppliedStatuses = ['WRITTEN_OFF', 'WRITE_OFF', 'APPLIED']

async function loadReceipt() { try { receipts.value = await receivableApi.receiptList() } catch (e) { ElMessage.error(e.message) } }

// 收款日期字段名对齐实体 RcReceipt.receiptDate（原绑 payDate，实体无此属性⇒选的日期从未生效）
async function openReceipt() { rcForm.value = { customerId: null, amount: 0, receiptDate: '' }; dlgReceipt.value = true }
async function saveReceipt() {
  await guardReceipt(async () => {
    try { await receivableApi.receiptCreate(rcForm.value); ElMessage.success(t('flow.arRcpt.msgRegistered')); dlgReceipt.value = false; loadReceipt() }
    catch (e) { ElMessage.error(e.message) }
  })
}

async function applyReceipt(row) {
  try {
    await receivableApi.receiptApply(row.id)
    ElMessage.success(t('flow.arRcpt.msgNoDoubleApply'))
    loadReceipt()
  } catch (e) { ElMessage.error(e.message) }
}

function reload() { loadReceipt() }

onMounted(() => {
  mdApi.customers({ pageNum: 1, pageSize: 200 }).then((d) => { customers.value = d?.list || [] }).catch(() => {})
  reload()
})

function handleSelectionChange(val) { /* no-op */ }
</script>
