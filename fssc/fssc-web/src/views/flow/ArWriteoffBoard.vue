<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>核销工作台（收款核销/票据核销/对冲核销 · 手动匹配/自动核销）</span>
        <div class="ops">
          <el-button size="small" @click="loadAll">刷新</el-button>
          <el-button type="primary" size="small" @click="openFund">资金登记</el-button>
          <el-button type="warning" size="small" @click="openOffset">对冲核销</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab" @tab-change="loadAll">
      <el-tab-pane label="资金池（收款/票据）" name="funds">
        <el-table :data="funds" v-loading="loading" size="small" border stripe>
          <el-table-column prop="fundNo" label="资金单号" min-width="170" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="{ RECEIPT: 'primary', NOTE: 'warning', OFFSET: 'danger' }[row.fundType] || 'info'">
                {{ FUND_TYPE_LABEL[row.fundType] || writeoffTypeLabel(row.fundType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip />
          <el-table-column prop="fundAmount" label="资金总额" width="110" align="right" />
          <el-table-column prop="remainAmount" label="未核销余额" width="110" align="right" />
          <el-table-column prop="fundDate" label="日期" width="100" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'AVAILABLE' ? 'success' : 'info'">{{ row.status === 'AVAILABLE' ? '可用' : row.status === 'EXHAUSTED' ? '已用完' : '已作废' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="agreementNo" label="抵消协议" min-width="110" show-overflow-tooltip />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'AVAILABLE' && row.fundType !== 'OFFSET'" link type="primary" size="small" @click="openManual(row)">手动匹配</el-button>
              <el-button v-if="row.status === 'AVAILABLE' && row.fundType !== 'OFFSET'" link type="success" size="small" @click="doAuto(row)">自动核销(FIFO)</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="待核销发票" name="invoices">
        <div class="ops" style="margin-bottom: 8px">
          <el-input v-model="invQuery.customerId" size="small" style="width: 160px" placeholder="客户ID" clearable @keyup.enter="loadInvoices" />
          <el-input v-model="invQuery.keyword" size="small" style="width: 180px" placeholder="应收单号" clearable @keyup.enter="loadInvoices" />
          <el-button size="small" type="primary" @click="loadInvoices">查询</el-button>
        </div>
        <el-table :data="invoices" v-loading="invLoading" size="small" border stripe>
          <el-table-column prop="billNo" label="应收单号" min-width="170" show-overflow-tooltip />
          <el-table-column prop="customerId" label="客户ID" width="90" />
          <el-table-column prop="billDate" label="单据日期" width="105" />
          <el-table-column prop="dueDate" label="到期日" width="105" />
          <el-table-column prop="amount" label="应收金额" width="110" align="right" />
          <el-table-column prop="receivedAmount" label="已收金额" width="110" align="right" />
          <el-table-column prop="remainAmount" label="应收余额" width="110" align="right" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'OVERDUE' ? 'danger' : 'warning'">{{ { NONE: '未结清', DONE: '结清', OVERDUE: '逾期' }[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="核销查询" name="records">
        <div class="ops" style="margin-bottom: 8px">
          <el-select v-model="recQuery.writeoffType" size="small" style="width: 120px" placeholder="核销类型" clearable @change="loadRecords">
            <el-option v-for="(l, t) in WRITEOFF_TYPE" :key="t" :label="l" :value="t" />
          </el-select>
          <el-select v-model="recQuery.status" size="small" style="width: 110px" placeholder="状态" clearable @change="loadRecords">
            <el-option label="已完成" value="DONE" />
            <el-option label="已失败" value="FAILED" />
            <el-option label="待核销" value="PENDING" />
          </el-select>
          <el-input v-model="recQuery.keyword" size="small" style="width: 180px" placeholder="记录号/客户/资金单号" clearable @keyup.enter="loadRecords" />
          <el-button size="small" type="primary" @click="loadRecords">查询</el-button>
        </div>
        <el-table :data="records" v-loading="recLoading" size="small" border stripe>
          <el-table-column prop="recordNo" label="核销记录号" min-width="170" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ writeoffTypeLabel(row.writeoffType) }}</template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
          <el-table-column prop="invoiceNos" label="核销应收单" min-width="180" show-overflow-tooltip />
          <el-table-column prop="writeoffAmount" label="核销金额" width="110" align="right" />
          <el-table-column label="方式" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.autoFlag === 1 ? 'success' : 'primary'">{{ row.autoFlag === 1 ? '自动' : '手动' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="receiptNo" label="收款单号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="voucherNo" label="凭证号" min-width="130" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="{ DONE: 'success', FAILED: 'danger', PENDING: 'warning' }[row.status] || 'info'">{{ { DONE: '已完成', FAILED: '已失败', PENDING: '待核销' }[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="fundVisible" title="资金登记（收款/票据来源）" width="560px" destroy-on-close>
      <el-form ref="fundFormRef" :model="fundForm" :rules="fundRules" label-width="100px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="资金类型" prop="fundType">
            <el-select v-model="fundForm.fundType" style="width: 100%">
              <el-option label="收款" value="RECEIPT" />
              <el-option label="票据" value="NOTE" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="资金单号" prop="fundNo"><el-input v-model="fundForm.fundNo" placeholder="收款单号/票据编号" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户ID" prop="customerId"><el-input v-model="fundForm.customerId" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户名称"><el-input v-model="fundForm.customerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="资金金额" prop="fundAmount"><el-input v-model="fundForm.fundAmount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="资金日期"><el-date-picker v-model="fundForm.fundDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="银行流水"><el-input v-model="fundForm.bankSerial" /></el-form-item></el-col>
          <el-col v-if="fundForm.fundType === 'NOTE'" :span="12"><el-form-item label="票据ID" prop="noteId"><el-input v-model="fundForm.noteId" placeholder="请输入票据编号（应收票据台账中的编号）" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="fundVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doRegisterFund">登记</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="manualVisible" :title="`手动匹配核销 — ${current?.fundNo || ''}（可用余额 ${current?.remainAmount || 0}）`" width="720px" destroy-on-close>
      <el-table :data="manualInvoices" v-loading="manualLoading" size="small" border max-height="360">
        <el-table-column prop="billNo" label="应收单号" min-width="160" />
        <el-table-column prop="dueDate" label="到期日" width="105" />
        <el-table-column prop="remainAmount" label="应收余额" width="105" align="right" />
        <el-table-column label="本次核销金额" width="180">
          <template #default="{ row }">
            <el-input v-model="row._matchAmount" size="small" placeholder="≤应收余额" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button size="small" @click="manualVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doManual">核销</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="offsetVisible" title="应收应付对冲核销（同时核销应收与应付）" width="520px" destroy-on-close>
      <el-form ref="offsetFormRef" :model="offsetForm" :rules="offsetRules" label-width="110px" size="small">
        <el-form-item label="对冲客户ID" prop="customerId"><el-input v-model="offsetForm.customerId" /></el-form-item>
        <el-form-item label="客户名称"><el-input v-model="offsetForm.customerName" /></el-form-item>
        <el-form-item label="对冲金额" prop="amount"><el-input v-model="offsetForm.amount" placeholder="不能超过应收或应付余额" /></el-form-item>
        <el-form-item label="抵消协议编号"><el-input v-model="offsetForm.agreementNo" placeholder="《债务抵消协议》附件编号" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="offsetVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doOffset">对冲核销</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { WRITEOFF_TYPE, writeoffTypeLabel } from '@/constants/enums'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { arGapApi } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'


const tab = ref('funds')
const loading = ref(false)
const invLoading = ref(false)
const recLoading = ref(false)
const manualLoading = ref(false)

const funds = ref([])
const invoices = ref([])
const records = ref([])
const manualInvoices = ref([])

const invQuery = reactive({ customerId: '', keyword: '' })
const recQuery = reactive({ writeoffType: '', status: '', keyword: '' })

const fundVisible = ref(false)
const manualVisible = ref(false)
const offsetVisible = ref(false)
const current = ref(null)
// R5-D-104：资金池类型词表（含 RECEIVABLE 历史/外部来源值），未收录值回落 enums 词表
const FUND_TYPE_LABEL = { RECEIPT: '收款', NOTE: '票据', OFFSET: '对冲', RECEIVABLE: '收款', PAYABLE: '付款' }
const fundForm = reactive({ fundType: 'RECEIPT' })
const offsetForm = reactive({})
const fundFormRef = ref(null)
const offsetFormRef = ref(null)
// 票据来源的资金必须回指已登记票据，否则核销完成后无从追溯票面
const fundRules = computed(() => ({
  fundType: commonRules.select('请选择资金类型'),
  fundNo: commonRules.required('请输入资金单号（收款单号/票据编号）'),
  customerId: commonRules.required('请输入客户ID'),
  fundAmount: commonRules.amountText('请输入资金金额'),
  noteId: fundForm.fundType === 'NOTE' ? commonRules.required('票据核销须填票据ID（ar_note.id）') : [],
}))
const offsetRules = {
  customerId: commonRules.required('请输入对冲客户ID'),
  amount: commonRules.amountText('请输入对冲金额'),
}

async function loadFunds() {
  loading.value = true
  try { funds.value = await arGapApi.funds({}) } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadInvoices() {
  invLoading.value = true
  try {
    invoices.value = await arGapApi.pendingInvoices({
      customerId: invQuery.customerId || undefined,
      keyword: invQuery.keyword || undefined,
    })
  } catch (e) { ElMessage.error(e.message) } finally { invLoading.value = false }
}

async function loadRecords() {
  recLoading.value = true
  try {
    records.value = await arGapApi.writeoffRecords({
      writeoffType: recQuery.writeoffType || undefined,
      status: recQuery.status || undefined,
      keyword: recQuery.keyword || undefined,
    })
  } catch (e) { ElMessage.error(e.message) } finally { recLoading.value = false }
}

function loadAll() {
  if (tab.value === 'funds') loadFunds()
  else if (tab.value === 'invoices') loadInvoices()
  else loadRecords()
}

function openFund() {
  Object.keys(fundForm).forEach((k) => delete fundForm[k])
  fundForm.fundType = 'RECEIPT'
  fundVisible.value = true
}

async function doRegisterFund() {
  if (!(await validateForms([fundFormRef]))) return
  await arGapApi.registerFund({ ...fundForm, noteId: fundForm.noteId || undefined })
  ElMessage.success('资金登记成功')
  fundVisible.value = false
  await loadFunds()
}

async function doAuto(row) {
  await arGapApi.autoMatch(row.id)
  ElMessage.success('自动核销完成（FIFO按到期日）')
  await loadFunds()
}

async function openManual(row) {
  current.value = row
  manualLoading.value = true
  manualVisible.value = true
  try {
    manualInvoices.value = (await arGapApi.pendingInvoices({ customerId: row.customerId }))
      .map((r) => ({ ...r, _matchAmount: '' }))
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    manualLoading.value = false
  }
}

async function doManual() {
  const lines = manualInvoices.value
    .filter((r) => r._matchAmount && Number(r._matchAmount) > 0)
    .map((r) => ({ receivableId: r.id, offsetAmount: r._matchAmount }))
  if (!lines.length) return ElMessage.warning('请至少填写一行核销金额')
  await arGapApi.manualMatch(current.value.id, lines)
  ElMessage.success('手动匹配核销完成')
  manualVisible.value = false
  await loadFunds()
}

function openOffset() {
  Object.keys(offsetForm).forEach((k) => delete offsetForm[k])
  offsetVisible.value = true
}

async function doOffset() {
  if (!(await validateForms([offsetFormRef]))) return
  await arGapApi.offsetWriteoff({ ...offsetForm })
  ElMessage.success('对冲核销完成（借2202/贷1122）')
  offsetVisible.value = false
  await loadFunds()
}

onMounted(loadFunds)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
</style>
