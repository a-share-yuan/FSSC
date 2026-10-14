<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>产业链协同（供应商发票全流程 · 客户发运/对账同步）</span>
        <div class="ops">
          <el-tag v-if="freeze.frozen" type="danger" size="small">R-14 关账冻结中{{ freeze.period ? `（${freeze.period}）` : '' }}：禁止发送数据至ERP</el-tag>
          <el-tag v-else type="success" size="small">数据交互正常</el-tag>
          <el-button size="small" @click="loadAll">刷新</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="tab" @tab-change="loadAll">
      <el-tab-pane label="SCP 供应商发票链" name="scp">
        <div class="ops" style="margin-bottom: 8px">
          <el-select v-model="scpQuery.status" size="small" style="width: 120px" placeholder="状态" clearable @change="loadScp">
            <el-option v-for="(l, s) in SCP_STATUS" :key="s" :label="l" :value="s" />
          </el-select>
          <el-input v-model="scpQuery.keyword" size="small" style="width: 190px" placeholder="单号/对账单号/供应商/发票号" clearable @keyup.enter="loadScp" />
          <el-button size="small" type="primary" @click="loadScp">查询</el-button>
          <el-button size="small" type="success" @click="openCreate">对账开票数据落地</el-button>
        </div>
        <el-table :data="scpRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="billNo" label="SCP单号" min-width="165" show-overflow-tooltip />
          <el-table-column prop="reconBillNo" label="对账单号" min-width="120" show-overflow-tooltip />
          <el-table-column prop="preInvoiceNo" label="预制发票号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="supplierName" label="供应商" min-width="110" show-overflow-tooltip />
          <el-table-column prop="amount" label="发票金额" width="100" align="right" />
          <el-table-column prop="invoiceNo" label="发票号码" min-width="110" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="{ CERTIFIED: 'success', POSTED: 'success', MATCH_FAILED: 'danger', CHECK_FAILED: 'danger', FORCE_PENDING: 'danger', WAIT_MATCH: 'warning', CHECKING: 'warning', VOIDED: 'info' }[row.status] || 'primary'">
                {{ SCP_STATUS[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="付款/认证" min-width="160">
            <template #default="{ row }">
              <span v-if="row.payVoucherNo">付款 {{ row.payVoucherNo }}</span>
              <span v-else-if="row.certifyDate">认证 {{ row.certifyDate }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="330" fixed="right">
            <template #default="{ row }">
              <el-button v-for="a in scpActions(row)" :key="a.key" link type="primary" size="small" @click="scpAct(row, a)">{{ a.label }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 演示沙盘：与真实供应商对账(/pay/recon)重复，暂下线，恢复时改回 hasPerm('collab:scp:list') -->
      <el-tab-pane v-if="false" label="供应商对账" name="recon">
        <div class="ops" style="margin-bottom: 8px">
          <el-select v-model="reconQuery.status" size="small" style="width: 140px" placeholder="状态" clearable @change="loadRecons">
            <el-option label="预制待确认" value="PRELIM" />
            <el-option label="最终对账单" value="CONFIRMED" />
            <el-option label="已作废" value="VOIDED" />
          </el-select>
          <el-input v-model="reconQuery.keyword" size="small" style="width: 170px" placeholder="对账单号/供应商" clearable @keyup.enter="loadRecons" />
          <el-button size="small" type="primary" @click="loadRecons">查询</el-button>
          <el-button v-if="hasPerm('collab:scp:supplier')" type="success" size="small" @click="openGoods">收货导入→预制对账单</el-button>
        </div>
        <el-table :data="reconRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="reconNo" label="对账单号" min-width="165" show-overflow-tooltip />
          <el-table-column prop="supplierName" label="供应商" min-width="120" show-overflow-tooltip />
          <el-table-column prop="amount" label="金额" width="100" align="right" />
          <el-table-column prop="taxAmount" label="税额" width="95" align="right" />
          <el-table-column prop="totalAmount" label="价税合计" width="105" align="right" />
          <el-table-column prop="invoiceType" label="发票类型" width="85" :formatter="(row, col, val) => invoiceTypeLabel(val)" />
          <el-table-column label="状态" width="105">
            <template #default="{ row }">
              <el-tag size="small" :type="{ CONFIRMED: 'success', PRELIM: 'warning', VOIDED: 'info' }[row.status] || 'info'">
                {{ { PRELIM: '预制待确认', CONFIRMED: '已确认', VOIDED: '已作废' }[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="异议" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.objectionStatus === 'SUBMITTED'" size="small" type="danger">待处理</el-tag>
              <span v-else-if="row.objectionStatus === 'RESOLVED'">已处理</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button v-for="a in reconActions(row)" :key="a.key" link type="primary" size="small" @click="reconAct(row, a)">{{ a.label }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="hasPerm('collab:ccp:manage')" label="CCP 发运与对账" name="ccp">
        <div class="ops" style="margin-bottom: 8px">
          <el-input v-model="ccpKeyword" size="small" style="width: 180px" placeholder="发运单/系统发票/客户" clearable @keyup.enter="loadCcp" />
          <el-button size="small" type="primary" @click="loadCcp">查询</el-button>
          <el-button size="small" type="success" @click="openShipping">发运数据推送</el-button>
          <el-button size="small" @click="openReconSync">对账状态同步</el-button>
          <el-button size="small" @click="openInvoiceWb">发票回写ERP</el-button>
        </div>
        <el-table :data="ccpRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="shippingNo" label="发运单号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="soInvoiceNo" label="ERP系统发票号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip />
          <el-table-column prop="amount" label="发运金额" width="110" align="right" />
          <el-table-column prop="taxAmount" label="税额" width="100" align="right" />
          <el-table-column prop="shippingDate" label="发运日期" width="105" />
          <el-table-column prop="statementNo" label="关联对账单" min-width="130" show-overflow-tooltip />
          <el-table-column label="状态" width="95">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'RECEIVED' ? 'primary' : 'info'">{{ row.status === 'RECEIVED' ? '已接收' : '已消耗' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="hasPerm('collab:log:view')" label="接口同步日志" name="logs">
        <div class="ops" style="margin-bottom: 8px">
          <el-select v-model="logQuery.ifaceCode" size="small" style="width: 190px" placeholder="接口" clearable @change="loadLogs">
            <el-option v-for="(l, c) in IFACES" :key="c" :label="l" :value="c" />
          </el-select>
          <el-select v-model="logQuery.status" size="small" style="width: 110px" placeholder="状态" clearable @change="loadLogs">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="模拟" value="MOCK" />
          </el-select>
          <el-button size="small" type="primary" @click="loadLogs">查询</el-button>
        </div>
        <el-table :data="logRows" v-loading="loading" size="small" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="接口" min-width="170">
            <template #default="{ row }">{{ IFACES[row.ifaceCode] || row.ifaceCode }}</template>
          </el-table-column>
          <el-table-column label="方向" width="140" show-overflow-tooltip><template #default="{ row }">{{ DIRECTION_LABEL[row.direction] || row.direction }}</template></el-table-column>
          <el-table-column prop="refNo" label="关联单号" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="{ SUCCESS: 'success', FAILED: 'danger', MOCK: 'info' }[row.status] || 'info'">{{ { SUCCESS: '成功', FAILED: '失败', MOCK: '模拟' }[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="消息" min-width="220" show-overflow-tooltip />
          <el-table-column label="时间" width="160"><template #default="{ row }">{{ row.createTime ? formatDateTime(row.createTime) : '—' }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="createVisible" title="对账开票数据落地" width="660px" destroy-on-close>
      <el-form ref="createFormRef" :model="form" :rules="createRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="对账单号" prop="reconBillNo"><el-input v-model="form.reconBillNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商代码"><el-input v-model="form.supplierCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商名称"><el-input v-model="form.supplierName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="PO编号"><el-input v-model="form.poNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发票类型">
            <el-select v-model="form.invoiceType" style="width: 100%">
              <el-option label="增专" value="VAT_SPECIAL" /><el-option label="增普" value="VAT_NORMAL" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发票金额" prop="amount"><el-input v-model="form.amount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发票税额"><el-input v-model="form.taxAmount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="价税合计"><el-input v-model="form.totalAmount" placeholder="空=金额+税额" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="对账基准金额"><el-input v-model="form.reconAmount" placeholder="对账单侧金额（匹配比对）" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="对账基准税额"><el-input v-model="form.reconTaxAmount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="购方税号"><el-input v-model="form.buyerTaxNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="销方税号"><el-input v-model="form.sellerTaxNo" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doCreate">落地（生成预制发票号）</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shipVisible" title="发运数据推送" width="520px" destroy-on-close>
      <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="120px" size="small">
        <el-form-item label="发运单号" prop="shippingNo"><el-input v-model="shipForm.shippingNo" /></el-form-item>
        <el-form-item label="ERP系统发票号"><el-input v-model="shipForm.soInvoiceNo" /></el-form-item>
        <el-form-item label="客户编码"><el-input v-model="shipForm.customerCode" /></el-form-item>
        <el-form-item label="客户名称"><el-input v-model="shipForm.customerName" /></el-form-item>
        <el-form-item label="发运金额" prop="amount"><el-input v-model="shipForm.amount" /></el-form-item>
        <el-form-item label="税额"><el-input v-model="shipForm.taxAmount" /></el-form-item>
        <el-form-item label="发运日期"><el-date-picker v-model="shipForm.shippingDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="关联对账单号"><el-input v-model="shipForm.statementNo" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="shipVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doPushShipping">推送</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="inputVisible" :title="inputTitle" width="440px" destroy-on-close>
      <el-input v-model="inputText" :placeholder="inputPlaceholder" />
      <template #footer>
        <el-button size="small" @click="inputVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doInput">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { invoiceTypeLabel } from '@/constants/invoiceType'
import { formatDateTime } from '../../utils/format'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { collabApi, store } from '../../api'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
import { hasPerm } from '@/utils/perm'

const SCP_STATUS = {
  WAIT_MATCH: '待匹配', WAIT_SEND: '待寄送', MATCH_FAILED: '匹配失败', WAIT_SIGN: '待签收',
  WAIT_SCAN: '待扫描', CHECKING: '票核中', CHECK_OK: '票核成功', CHECK_FAILED: '票核失败',
  POSTED: '记账成功', CERTIFIED: '认证完成', FORCE_PENDING: '强匹审批中', VOIDED: '已作废',
}
const IFACES = {
  SCP_IF03_MATCH_POST: '三单匹配记账',
  SCP_IF05_PAY: '付款信息回写',
  SCP_IF06_CERTIFY: '认证状态',
  SCP_IF07_FREEZE: '关账冻结通知',
  SCP_IF07_UNFREEZE: '关账解冻通知',
  AR_IF01_SHIPPING: 'ERP发运数据推送',
  AR_IF02_INVOICE_WB: '发票信息回写ERP',
  AR_IF10_RECON_SYNC: '对账状态同步',
}

// R5-D-104：协同日志方向英文枚举→中文映射（未收录值原样兜底）
const DIRECTION_LABEL = { SCP_TO_AP: 'SCP→AP', AP_TO_SCP: 'AP→SCP', SCP_TO_EBS: 'SCP→EBS', EBS_TO_SHARED: 'EBS→共享', SHARED_TO_EBS: '共享→EBS', CCP_TO_SHARED: 'CCP→共享' }

const tab = ref('scp')
const loading = ref(false)
const scpRows = ref([])
const ccpRows = ref([])
const logRows = ref([])
const scpQuery = reactive({ status: '', keyword: '' })
const logQuery = reactive({ ifaceCode: '', status: '' })
const ccpKeyword = ref('')
const freeze = reactive({ frozen: false, period: '' })
const reconRows = ref([])
const reconQuery = reactive({ status: '', keyword: '' })

const createVisible = ref(false)
const shipVisible = ref(false)
const inputVisible = ref(false)
const current = ref(null)
const form = reactive({})
const createFormRef = ref(null)
// 对账单号是预制发票号的取号依据（R-31：预制发票号=对账单号+流水），留空整张票无从生成
const createRules = {
  reconBillNo: commonRules.required('请输入对账单号'),
  amount: commonRules.amountText('请输入发票金额'),
}
const shipForm = reactive({})
const shipFormRef = ref(null)
const shipRules = {
  shippingNo: commonRules.required('请输入发运单号'),
  amount: commonRules.amountText('请输入发运金额'),
}
const inputKind = ref('')
const inputTitle = ref('')
const inputText = ref('')
const inputPlaceholder = ref('')

const today = () => new Date().toISOString().slice(0, 10)

function scpActions(row) {
  switch (row.status) {
    case 'WAIT_MATCH':
    case 'MATCH_FAILED':
      return [{ key: 'match', label: '自动匹配' }, { key: 'manual', label: '手工匹配' },
        { key: 'force', label: '申请强制匹配' }]
    case 'FORCE_PENDING':
      return hasPerm('collab:scp:force-appr')
        ? [{ key: 'forceApprove', label: '审批通过' }, { key: 'forceReject', label: '驳回' }]
        : []
    case 'WAIT_SEND':
      return [{ key: 'send', label: '创建邮包寄送' }, { key: 'cancelMatch', label: '取消匹配' }]
    case 'WAIT_SIGN':
      return [{ key: 'sign', label: '快递签收' }]
    case 'WAIT_SCAN':
      return [{ key: 'scan', label: '扫描+票核' }]
    case 'CHECK_FAILED':
      return [{ key: 'rematch', label: '复核重匹配' }]
    case 'CHECK_OK':
      return [{ key: 'post', label: '记账推送ERP' }]
    case 'POSTED':
      return [{ key: 'preCertify', label: '预认证' }, { key: 'certify', label: '集中认证' }, { key: 'pay', label: '付款回写' }]
    case 'CERTIFIED':
      return [{ key: 'pay', label: '付款回写' }]
    default:
      return []
  }
}

async function loadScp() {
  loading.value = true
  try {
    scpRows.value = await collabApi.scpBills({ status: scpQuery.status || undefined, keyword: scpQuery.keyword || undefined })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadCcp() {
  loading.value = true
  try {
    ccpRows.value = await collabApi.shippings({ keyword: ccpKeyword.value || undefined })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadLogs() {
  loading.value = true
  try {
    logRows.value = await collabApi.syncLogs({ ifaceCode: logQuery.ifaceCode || undefined, status: logQuery.status || undefined })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

async function loadFreeze() {
  try {
    const m = await collabApi.freezeStatus({ companyId: store.user?.companyId })
    freeze.frozen = !!m.frozen
    freeze.period = m.period || ''
  } catch { /* 冻结横幅失败不打断页面 */ }
}

async function loadRecons() {
  loading.value = true
  try {
    reconRows.value = await collabApi.scpRecons({ status: reconQuery.status || undefined, keyword: reconQuery.keyword || undefined })
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function loadAll() {
  loadFreeze()
  if (tab.value === 'scp') loadScp()
  else if (tab.value === 'recon') loadRecons()
  else if (tab.value === 'ccp') loadCcp()
  else loadLogs()
}

async function api(fn, msg) {
  await fn()
  ElMessage.success(msg)
  await loadAll()
}

function scpAct(row, a) {
  current.value = row
  const id = row.id
  const m = {
    match: () => api(() => collabApi.scpMatch(id), '自动匹配完成'),
    cancelMatch: () => api(() => collabApi.scpCancelMatch(id), '已取消匹配'),
    sign: () => api(() => collabApi.scpSign(id), '签收完成，待扫描'),
    rematch: () => api(() => collabApi.scpRematch(id), '已复核回待匹配'),
    preCertify: () => api(() => collabApi.scpPreCertify(id), '预认证完成'),
    forceApprove: () => api(() => collabApi.scpForceApprove(id), '强匹审批通过（四眼校验+差额分级）'),
    post: () => api(() => collabApi.scpPost(id), '记账推送完成'),
    certify: () => api(() => collabApi.scpCertify(id, '认证通过'), '认证完成'),
  }
  if (m[a.key]) return m[a.key]()
  if (a.key === 'send') {
    inputKind.value = 'send'; inputTitle.value = '创建邮包寄送'
    inputPlaceholder.value = '快递公司/快递单号（逗号分隔）'
    inputText.value = ''; inputVisible.value = true
  } else if (a.key === 'manual') {
    inputKind.value = 'manual'; inputTitle.value = '手工匹配：人工核定对账单基准 金额,税额,价税合计'
    inputPlaceholder.value = '如 10000,1300,11300'
    inputText.value = ''; inputVisible.value = true
  } else if (a.key === 'force') {
    inputKind.value = 'force'; inputTitle.value = '申请强制匹配（进审批，双人复核+差额分级）'
    inputPlaceholder.value = '强制匹配原因'
    inputText.value = ''; inputVisible.value = true
  } else if (a.key === 'forceReject') {
    inputKind.value = 'forceReject'; inputTitle.value = '驳回强匹申请'
    inputPlaceholder.value = '驳回原因'
    inputText.value = ''; inputVisible.value = true
  } else if (a.key === 'scan') {
    inputKind.value = 'scan'; inputTitle.value = '扫描识别回填（票面数据，逗号分隔：发票号,发票代码,金额,税额,价税合计,日期）'
    inputPlaceholder.value = '如 FP123,code,10000,1300,11300,2026-09-01'
    inputText.value = ''; inputVisible.value = true
  } else if (a.key === 'pay') {
    inputKind.value = 'pay'; inputTitle.value = '付款信息回写（凭证号,日期,金额）'
    inputPlaceholder.value = '如 PAY-001,2026-09-20,11300'
    inputText.value = ''; inputVisible.value = true
  }
}

function parts() {
  return inputText.value.split(',').map((s) => s.trim())
}

async function doInput() {
  const id = current.value.id
  if (inputKind.value === 'send') {
    const [c, n] = parts()
    await api(() => collabApi.scpSend(id, { courierCompany: c, courierNo: n }), '邮包已创建，待签收')
  } else if (inputKind.value === 'scan') {
    const [invoiceNo, invoiceCode, amount, taxAmount, totalAmount, invoiceDate] = parts()
    const body = {}
    if (invoiceNo) body.invoiceNo = invoiceNo
    if (invoiceCode) body.invoiceCode = invoiceCode
    if (amount) body.amount = amount
    if (taxAmount) body.taxAmount = taxAmount
    if (totalAmount) body.totalAmount = totalAmount
    if (invoiceDate) body.invoiceDate = invoiceDate
    await api(() => collabApi.scpScan(id, body), '扫描票核完成')
  } else if (inputKind.value === 'manual') {
    const [a2, b2, c2] = parts()
    await api(() => collabApi.scpManualMatch(current.value.id, { reconAmount: a2, reconTaxAmount: b2, reconTotal: c2 }), '手工匹配完成')
  } else if (inputKind.value === 'force') {
    if (!inputText.value) return ElMessage.warning('强制匹配原因必填')
    await api(() => collabApi.scpForceApply(current.value.id, inputText.value), '强匹申请已提交（单据锁定待审批）')
  } else if (inputKind.value === 'forceReject') {
    if (!inputText.value) return ElMessage.warning('驳回原因必填')
    await api(() => collabApi.scpForceReject(current.value.id, inputText.value), '已驳回，单据回原状态')
  } else if (inputKind.value === 'pay') {
    const [voucher, date, amt] = parts()
    if (!voucher) return ElMessage.warning('付款凭证号必填')
    await api(() => collabApi.scpPayWriteback(id, { payVoucherNo: voucher, payDate: date || today(), payAmount: amt }), '付款回写完成')
  } else if (['confirm', 'objection', 'resolve', 'voidRecon', 'applyInvoice', 'importGoods'].includes(inputKind.value)) {
    await doReconInput()
  } else {
    await doInputCollab()
  }
  inputVisible.value = false
}

function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  form.invoiceType = 'VAT_SPECIAL'
  createVisible.value = true
}

async function doCreate() {
  if (!(await validateForms([createFormRef]))) return
  await collabApi.scpCreate({ ...form })
  ElMessage.success('落地成功，预制发票号已按对账单号+流水生成')
  createVisible.value = false
  await loadScp()
}

function openShipping() {
  Object.keys(shipForm).forEach((k) => delete shipForm[k])
  shipForm.shippingDate = today()
  shipVisible.value = true
}

async function doPushShipping() {
  if (!(await validateForms([shipFormRef]))) return
  await collabApi.pushShipping({ ...shipForm })
  ElMessage.success('发运数据接收成功')
  shipVisible.value = false
  await loadCcp()
}

function reconActions(row) {
  const ops = []
  if (row.status === 'PRELIM') {
    ops.push({ key: 'confirm', label: '确认对账单' })
    if (hasPerm('collab:scp:supplier')) ops.push({ key: 'objection', label: '异议反馈' })
    ops.push({ key: 'voidRecon', label: '作废' })
  }
  if (row.objectionStatus === 'SUBMITTED' && hasPerm('collab:scp:sign')) ops.push({ key: 'resolve', label: '处理异议' })
  if (row.status === 'CONFIRMED' && hasPerm('collab:scp:supplier')) ops.push({ key: 'applyInvoice', label: '申请开票' })
  return ops
}

function reconAct(row, a) {
  current.value = row
  inputKind.value = a.key
  inputText.value = ''
  if (a.key === 'confirm') inputTitle.value = '确认对账单（发票类型,税率%，如 增普,13；增专不调整）'
  else if (a.key === 'objection') inputTitle.value = '异议反馈（修改后销方物料描述+申请原因）'
  else if (a.key === 'resolve') inputTitle.value = '处理异议（判责/处理说明）'
  else if (a.key === 'voidRecon') inputTitle.value = '作废对账单'
  else if (a.key === 'applyInvoice') inputTitle.value = '申请开票'
  inputVisible.value = true
}

function openGoods() {
  Object.keys(shipForm).forEach((k) => delete shipForm[k])
  inputKind.value = 'importGoods'
  inputTitle.value = '收货导入→预制对账单（供应商代码,供应商名称,不含税金额,税额,明细行用|分隔）'
  inputPlaceholder.value = '如 SUP001,冒烟供应商,10000,1300,物料A x10;物料B x5'
  inputText.value = ''
  inputVisible.value = true
}

async function doReconInput() {
  const id = current.value.id
  if (inputKind.value === 'confirm') {
    const [t, r] = inputText.value.split(',').map((s) => s.trim())
    await api(() => collabApi.scpConfirmRecon(id, { invoiceType: { 增专: 'VAT_SPECIAL', 增普: 'VAT_NORMAL' }[t] || t || 'VAT_SPECIAL', taxRatePct: r || undefined }), '对账单已确认')
  } else if (inputKind.value === 'objection') {
    await api(() => collabApi.scpObjection(id, inputText.value), '异议已提交')
  } else if (inputKind.value === 'resolve') {
    await api(() => collabApi.scpResolveObjection(id, inputText.value), '异议已处理')
  } else if (inputKind.value === 'voidRecon') {
    await api(() => collabApi.scpVoidRecon(id, inputText.value), '对账单已作废')
  } else if (inputKind.value === 'applyInvoice') {
    await api(() => collabApi.scpApplyInvoice(id, { poNo: inputText.value || undefined }), '预制发票申请已落地')
  } else if (inputKind.value === 'importGoods') {
    const [sc, sn, amt, tax, linesStr] = inputText.value.split(',').map((s) => s.trim())
    if (!sc || !amt) return ElMessage.warning('格式：供应商代码,供应商名称,不含税金额,税额,明细(用|分隔)')
    const lines = (linesStr || '').split(';').filter(Boolean).map((s, i) => {
      const seg = s.split('x')
      return { desc: (seg[0] || '物料' + (i + 1)).trim(), qty: (seg[1] || '1').trim(), amount: amt }
    })
    await api(() => collabApi.scpImportGoods({ supplierCode: sc, supplierName: sn, amount: Number(amt), taxAmount: Number(tax || 0), linesJson: JSON.stringify(lines) }), '预制对账单已创建')
  }
  inputVisible.value = false
}

async function openReconSync() {
  inputKind.value = 'reconSync'; inputTitle.value = '对账状态同步'
  inputPlaceholder.value = '客户对账单号（如 ARB...）'
  inputText.value = ''; inputVisible.value = true
}

async function openInvoiceWb() {
  inputKind.value = 'invoiceWb'; inputTitle.value = '发票信息回写ERP'
  inputPlaceholder.value = '预制发票ID（已开票 INVOICED 状态）'
  inputText.value = ''; inputVisible.value = true
}

async function doInputCollab() {
  if (inputKind.value === 'reconSync') {
    if (!inputText.value) return ElMessage.warning('对账单号必填')
    const st = await collabApi.reconSync(inputText.value)
    ElMessage.success(`对账状态同步成功：${st.status} / 核对 ${st.checkStatus}`)
  } else if (inputKind.value === 'invoiceWb') {
    if (!inputText.value) return ElMessage.warning('预制发票ID必填')
    await collabApi.invoiceWriteback(inputText.value)
    ElMessage.success('发票信息已回写ERP')
  }
  inputVisible.value = false
}

onMounted(loadAll)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.ops { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
</style>
