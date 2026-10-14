<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>开票链工作台（对账单 → 拆分合并 → 商务确认 → 金税 → 回写 → 红字/换票 → 邮包）</span>
        <div class="ops">
          <el-radio-group v-model="tab" size="small" @change="load">
            <el-radio-button value="statement">对账单</el-radio-button>
            <el-radio-button value="pre">预制发票</el-radio-button>
            <el-radio-button value="mail">邮包</el-radio-button>
            <el-radio-button value="rule">开票规则</el-radio-button>
          </el-radio-group>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button v-if="tab === 'statement'" type="primary" size="small" @click="importVisible = true">导入对账单</el-button>
          <el-button v-if="tab === 'statement'" size="small" @click="openEbsAdjust">ERP调整推送</el-button>
          <el-button v-if="tab === 'preinvoice'" size="small" @click="openRedPush">ERP负数推送</el-button>
          <el-upload v-if="tab === 'statement'" :show-file-list="false" accept=".xlsx" :auto-upload="false" :on-change="doImportExcel" style="display:inline-block">
            <el-button type="success" size="small">Excel模板导入(11.4.4)</el-button>
          </el-upload>
          <el-button v-if="tab === 'pre'" type="primary" size="small" @click="genVisible = true">拆分合并生成</el-button>
        </div>
      </div>
    </template>

    <!-- 对账单 -->
    <el-table v-show="tab === 'statement'" :data="statements" v-loading="loading" size="small" border stripe>
      <el-table-column prop="billNo" label="对账单号" min-width="180" />
      <el-table-column prop="customerCode" label="客户编码" width="100" />
      <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
      <el-table-column prop="ccy" label="币种" width="60" />
      <el-table-column prop="amount" label="金额" width="105" align="right" />
      <el-table-column prop="taxAmount" label="税额" width="100" align="right" />
      <el-table-column label="核对" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.checkStatus === 'CHECK_OK' ? 'success' : row.checkStatus === 'CHECK_FAILED' ? 'danger' : 'info'">
            {{ { UNCHECKED: '待核对', CHECK_OK: '通过', CHECK_FAILED: '失败' }[row.checkStatus] || row.checkStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="diffReason" label="差异原因" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'RECONCILED'" link type="primary" size="small" @click="doCheck(row)">数据核对</el-button>
          <el-button v-if="row.status === 'ABNORMAL'" link type="warning" size="small" @click="doAdjust(row)">财务调整确认</el-button>
          <el-button v-if="row.status === 'RECONCILED'" link type="success" size="small" @click="doGen(row.billNo)">去开票</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 预制发票 -->
    <el-table v-show="tab === 'pre'" :data="invoices" v-loading="loading" size="small" border stripe>
      <el-table-column prop="preInvoiceNo" label="预制发票号" min-width="190" />
      <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip />
      <el-table-column prop="invoiceType" label="类型" width="60" :formatter="(row, col, val) => invoiceTypeLabel(val)" />
      <el-table-column prop="totalAmount" label="价税合计" width="110" align="right" />
      <el-table-column label="状态" width="105">
        <template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ arBillingStatusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="金税回写" min-width="140">
        <template #default="{ row }">{{ row.invoiceNo || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'BIZ_CONFIRMING'" link type="success" size="small" @click="biz(row, 'bizConfirm')">商务确认</el-button>
          <el-button v-if="row.status === 'BIZ_CONFIRMING'" link type="danger" size="small" @click="bizReject(row)">商务驳回</el-button>
          <el-button v-if="row.status === 'TO_INVOICE'" link type="primary" size="small" @click="simple(row, 'pushTax')">推送金税</el-button>
          <el-button v-if="row.status === 'PUSHED'" link type="primary" size="small" @click="openWriteback(row)">回写发票号</el-button>
          <el-button v-if="row.status === 'INVOICED' && !row.printedFlag" link type="primary" size="small" @click="simple(row, 'print')">打印</el-button>
          <el-button v-if="row.status === 'INVOICED'" link type="danger" size="small" @click="openRed(row)">红字</el-button>
          <el-button v-if="['INVOICED', 'TO_INVOICE', 'PUSHED'].includes(row.status)" link type="warning" size="small" @click="openExchange(row)">换票</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 邮包 -->
    <el-table v-show="tab === 'mail'" :data="mails" v-loading="loading" size="small" border stripe>
      <el-table-column prop="packageNo" label="邮包号" min-width="185" />
      <el-table-column prop="customerName" label="客户" min-width="130" />
      <el-table-column prop="courierNo" label="快递单号" min-width="130" />
      <el-table-column label="通知" width="80">
        <template #default="{ row }">{{ row.mailNotified === 1 ? '已通知' : '未通知' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ { CREATED: '已创建', SENT: '已寄送', SIGNED: '已签收', ARCHIVED: '已归档' }[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'CREATED'" link type="primary" size="small" @click="mailAct(row, 'send')">寄送</el-button>
          <el-button v-if="row.status === 'SENT' && !row.mailNotified" link type="warning" size="small" @click="mailAct(row, 'notify')">邮件通知</el-button>
          <el-button v-if="row.status === 'SENT'" link type="primary" size="small" @click="mailAct(row, 'sign')">签收</el-button>
          <el-button v-if="row.status === 'SIGNED'" link type="success" size="small" @click="mailAct(row, 'archive')">归档</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 开票规则 -->
    <el-table v-show="tab === 'rule'" :data="rules" v-loading="loading" size="small" border stripe>
      <el-table-column prop="customerCode" label="客户编码" width="105" />
      <el-table-column prop="customerName" label="客户名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="invoiceType" label="类型" width="65" :formatter="(row, col, val) => invoiceTypeLabel(val)" />
      <el-table-column prop="limitAmount" label="单张限额" width="110" align="right" />
      <el-table-column prop="maxLines" label="最大行数" width="85" align="right" />
      <el-table-column prop="splitDim" label="拆分维度" width="95" />
      <el-table-column label="商务确认" width="90">
        <template #default="{ row }">{{ row.needBizConfirm === 1 ? '需要' : '不需要' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link :type="row.status === 'ENABLED' ? 'warning' : 'success'" size="small"
            @click="saveRule({ ...row, status: row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED' })">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 对账单导入 -->
    <el-dialog v-model="importVisible" title="导入客户对账单（必含客户代码）" width="560px" destroy-on-close>
      <el-form ref="stFormRef" :model="stForm" :rules="stRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="客户编码" prop="customerCode"><el-input v-model="stForm.customerCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户名称"><el-input v-model="stForm.customerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="金额" prop="amount"><el-input v-model="stForm.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="税额" prop="taxAmount"><el-input v-model="stForm.taxAmount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="币种"><el-input v-model="stForm.ccy" placeholder="默认CNY" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="导入人"><el-input v-model="stForm.importUser" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="importVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doImport">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="genVisible" title="拆分合并生成预制发票" width="420px" destroy-on-close>
      <el-input v-model="genBillNo" placeholder="对账完成的对账单号" />
      <div class="tip">按客户开票规则拆分（限额默认100万/行数上限/尾差自动并单）；商务确认规则+参数开启时先转商务确认。</div>
      <template #footer>
        <el-button size="small" @click="genVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doGen(genBillNo)">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="420px" destroy-on-close>
      <el-input v-model="inputText" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doInput">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { arBillingStatusLabel } from '@/constants/enums'
import { invoiceTypeLabel } from '@/constants/invoiceType'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { arFlowApi, arGapApi, store } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'

const tab = ref('statement')
const loading = ref(false)
const statements = ref([])
const invoices = ref([])
const mails = ref([])
const rules = ref([])
const importVisible = ref(false)
const genVisible = ref(false)
const inputVisible = ref(false)
const current = ref(null)
const stForm = reactive({ ccy: 'CNY' })
const stFormRef = ref(null)
// 客户编码是对账单归到哪家客户的唯一依据，缺失则导入的行无法核对
const stRules = {
  customerCode: commonRules.required('请输入客户编码'),
  amount: commonRules.numberText('请输入金额'),
  taxAmount: commonRules.numberText('请输入税额（免税/预付款可填 0）'),
}
const genBillNo = ref('')
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')

const statusTag = (s) => ({ INVOICED: 'success', VOIDED: 'danger', RED_INVOICED: 'danger', PUSHED: 'warning' }[s] || 'primary')

async function load() {
  loading.value = true
  try {
    if (tab.value === 'statement') statements.value = await arFlowApi.statements({})
    else if (tab.value === 'pre') invoices.value = await arFlowApi.preInvoices({})
    else if (tab.value === 'mail') mails.value = await arFlowApi.mailPackages({})
    else rules.value = await arFlowApi.invoiceRules()
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function doCheck(row) {
  await arFlowApi.checkStatement(row.id)
  ElMessage.success('核对完成')
  await load()
}

async function doAdjust(row) {
  // 41号审查 P0-1 同源整改：window.prompt -> ElMessageBox.prompt（统一样式/可取消）
  const { value: note } = await ElMessageBox.prompt('财务调整完成说明', '财务调整', {
    inputPlaceholder: '调整说明（可空）', inputType: 'textarea',
    confirmButtonText: '确认', cancelButtonText: '取消',
  }).catch(() => ({}))
  if (note === undefined) return
  await arFlowApi.adjustStatement(row.id, note)
  ElMessage.success('已确认对账完成')
  await load()
}

async function doGen(billNo) {
  if (!String(billNo || '').trim()) return ElMessage.warning('请输入已完成对账的对账单号')
  const r = await arFlowApi.generate(billNo)
  ElMessage.success(`生成 ${r.length} 张预制发票`)
  genVisible.value = false
  tab.value = 'pre'
  await load()
}

async function biz(row, action) {
  if (action === 'bizConfirm') {
    await arFlowApi.bizConfirm(row.id, '商务')
  } else {
    // 41号审查 P0-1 同源整改：window.prompt -> ElMessageBox.prompt（必填校验）
    const { value: reason } = await ElMessageBox.prompt('商务驳回原因', '商务驳回', {
      inputPlaceholder: '驳回原因（必填）', inputType: 'textarea',
      inputValidator: (v) => (v && v.trim() ? true : '驳回原因必填'),
      confirmButtonText: '确认驳回', cancelButtonText: '取消',
    }).catch(() => ({}))
    if (!reason) return
    await arFlowApi.bizReject(row.id, reason)
  }
  ElMessage.success('操作成功')
  await load()
}

function openWriteback(row) {
  current.value = row
  inputKind.value = 'writeback'
  inputTitle.value = '金税回写（发票号码）'
  inputText.value = ''
  inputVisible.value = true
}

function openRed(row) {
  current.value = row
  inputKind.value = 'red'
  inputTitle.value = '红字通知单号'
  inputText.value = ''
  inputVisible.value = true
}

function openExchange(row) {
  current.value = row
  inputKind.value = 'exchange'
  inputTitle.value = '换票原因'
  inputText.value = ''
  inputVisible.value = true
}

async function doInput() {
  // 回写的金税发票号、红字通知单号、换票原因都是后续税务/审计凭据，留空即无从追溯
  if (!inputText.value.trim()) return ElMessage.warning(`请填写${inputTitle.value}`)
  const id = current.value.id
  if (inputKind.value === 'writeback') await arFlowApi.writeback(id, { invoiceNo: inputText.value })
  else if (inputKind.value === 'red') await arFlowApi.red(id, inputText.value)
  else await arFlowApi.exchange(id, inputText.value)
  ElMessage.success('操作成功')
  inputVisible.value = false
  await load()
}

async function simple(row, action) {
  await arFlowApi[action](row.id)
  ElMessage.success('操作成功')
  await load()
}

async function mailAct(row, action) {
  if (action === 'send') {
    // 41号审查 P0-1 同源整改：window.prompt -> ElMessageBox.prompt（必填校验）
    const { value: no } = await ElMessageBox.prompt('快递单号', '寄送邮寄包', {
      inputPlaceholder: '快递单号（必填）',
      inputValidator: (v) => (v && v.trim() ? true : '快递单号必填'),
      confirmButtonText: '确认寄送', cancelButtonText: '取消',
    }).catch(() => ({}))
    if (!no) return
    await arFlowApi.sendMail(row.id, { courierNo: no })
  } else {
    await arFlowApi[action](row.id)
  }
  ElMessage.success('操作成功')
  await load()
}

async function saveRule(rule) {
  await arFlowApi.saveRule(rule)
  ElMessage.success('已保存')
  await load()
}

async function doImport() {
  if (!(await validateForms([stFormRef]))) return
  await arFlowApi.importStatement({ ...stForm, companyId: store.user?.companyId })
  ElMessage.success('导入成功')
  importVisible.value = false
  await load()
}

async function doImportExcel(uploadFile) {
  const fd = new FormData()
  fd.append('file', uploadFile.raw)
  const res = await arFlowApi.importStatementExcel(fd)
  ElMessage.success(`Excel导入成功 ${res.created} 张对账单` + (res.errors?.length ? `，${res.errors.length} 条失败：${res.errors[0]}` : ''))
  await load()
}

function openEbsAdjust() {
  // 41号审查 P0-1 同源整改：window.prompt -> ElMessageBox.prompt（必填校验）
  ElMessageBox.prompt('ERP财务调整推送，按顺序填写，逗号分隔', 'ERP财务调整推送', {
    inputPlaceholder: '调整单号,客户代码,类型(折扣/索赔/溢交),金额,税额,关联对账单号(可空)',
    inputValidator: (v) => {
      if (!v || !v.trim()) return '内容必填'
      const parts = v.split(',').map((x) => x.trim())
      if (parts.length < 5) return '至少填写：调整单号,客户代码,类型,金额,税额'
      return true
    },
    confirmButtonText: '推送', cancelButtonText: '取消',
  }).then(({ value: input }) => {
    if (!input) return
    const [adjustNo, customerCode, adjustType, amount, tax, stmtNo] = input.split(',').map((s) => s.trim())
    return arGapApi.pushEbsAdjust({ adjustNo, customerCode, adjustType, amount, taxAmount: tax || 0, statementNo: stmtNo || null })
      .then(async (r) => {
        ElMessage.success(`调整已接收（${r.status}）` + (r.statementNo ? '，已自动重核' : ''))
        await load()
      }).catch((e) => ElMessage.error(e.message))
  }).catch(() => {})
}

function openRedPush() {
  // 41号审查 P0-1 同源整改：window.prompt -> ElMessageBox.prompt（必填校验）
  ElMessageBox.prompt('ERP负数发票推送，逗号分隔', 'ERP负数发票推送', {
    inputPlaceholder: '贷项凭证号,预制发票号或发票号',
    inputValidator: (v) => (v && v.includes(',') ? true : '格式：贷项凭证号,预制发票号或发票号'),
    confirmButtonText: '推送', cancelButtonText: '取消',
  }).then(({ value: input }) => {
    if (!input) return
    const [memo, inv] = input.split(',').map((s) => s.trim())
    return arGapApi.pushRedInvoice({ ebsCreditMemoNo: memo, preInvoiceNo: inv }).then(async () => {
      ElMessage.success('负数发票已接收（红字前置就绪，请补录红字通知单号）')
      await load()
    }).catch((e) => ElMessage.error(e.message))
  }).catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; }
.tip { font-size: 12px; color: #e6a23c; margin-top: 6px; }
</style>
