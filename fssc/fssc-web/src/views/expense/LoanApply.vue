<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <!-- RA-PAGE-21 2.2：借款/还款双形态并入一个菜单（原 225 还款管理退册）——切换即跳对侧路由，整页重挂重建单据状态 -->
  <el-radio-group :model-value="mode" size="small" style="margin-bottom:8px" @change="onModeSwitch">
    <el-radio-button value="LOAN">借款申请</el-radio-button>
    <el-radio-button value="REPAY">还款登记</el-radio-button>
  </el-radio-group>
  <BillWorkbench ref="wb" :bill-type="mode === 'LOAN' ? 'LOAN' : 'REPAY'" :sub-type="mode" :before-submit="onBeforeSubmit">
    <template #create="b">
      <!-- ============ 单据概要 ============ -->
      <el-card header="单据概要">
        <el-form ref="formRef" :rules="formRules" :model="b.form.bill" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="12">
              <el-form-item label="单据编号"><el-input :model-value="b.form.bill.billNo || '（保存后自动生成）'" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="制单日期"><el-input :model-value="b.today()" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="制单人"><el-input :model-value="currentUserName" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="制单人部门"><el-input :model-value="applicantDept" disabled /></el-form-item>
            </el-col>
            <el-col v-if="mode === 'REPAY'" :xs="24" :md="12">
              <el-form-item label="制单人公司"><el-input :model-value="applicantCompany" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="币种" prop="currencyCode">
                <el-select v-model="b.form.bill.currencyCode" style="width: 100%">
                  <el-option label="人民币 CNY" value="CNY" />
                  <el-option label="美元 USD" value="USD" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="mode === 'REPAY'" :xs="24" :md="12">
              <el-form-item label="还款人" prop="applyUserId">
                <UserSelect v-model="b.form.bill.applyUserId" :users="users" @change="(id) => onRepayUserChange(b, id)" />
              </el-form-item>
            </el-col>
            <el-col v-if="mode === 'REPAY'" :xs="24" :md="12">
              <el-form-item label="还款人部门"><el-input :model-value="repayUserDept" disabled /></el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item label="标题" prop="title">
                <el-input v-model="b.form.bill.title" :placeholder="mode === 'LOAN' ? '借款申请标题' : '还款登记标题'" />
              </el-form-item>
            </el-col>
            <el-col :xs="24">
              <el-form-item label="单据说明" prop="remark">
                <el-input v-model="b.form.bill.remark" type="textarea" :rows="2" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- ============ 借款申请：借款详情 ============ -->
      <el-card v-if="mode === 'LOAN'" header="借款详情" style="margin-top: 12px">
        <el-form ref="loanFormRef" :model="b.form.bill.extJson" :rules="loanRules" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8">
              <el-form-item label="借款类型" prop="loanType">
                <el-select v-model="b.form.bill.extJson.loanType" style="width: 100%">
                  <el-option label="借款" value="LOAN" />
                  <el-option label="备用金" value="PETTY" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="借款金额" prop="loanAmount">
                <el-input-number v-model="b.form.bill.extJson.loanAmount" :min="0" :precision="2" style="width: 100%" @change="syncLoanLine(b)" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8">
              <el-form-item label="收款方式" prop="payMethod">
                <el-select v-model="b.form.bill.extJson.payMethod" style="width: 100%">
                  <el-option label="银行转账" value="BANK" />
                  <el-option label="现金" value="CASH" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="8"><el-form-item label="账户类型"><el-input :model-value="'员工'" disabled /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="账户名称" prop="accountName"><el-input v-model="b.form.bill.extJson.accountName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="账号" prop="accountNo"><el-input v-model="b.form.bill.extJson.accountNo" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="开户行" prop="bankName"><el-input v-model="b.form.bill.extJson.bankName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="预计还款日期"><el-date-picker v-model="b.form.bill.extJson.expectRepayDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
            <el-col :xs="24"><el-form-item label="借款说明"><el-input v-model="b.form.bill.extJson.loanRemark" type="textarea" :rows="2" /></el-form-item></el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- ============ 借款申请：未冲销详情 ============ -->
      <el-card v-if="mode === 'LOAN'" header="未冲销详情（历史借款）" style="margin-top: 12px">
        <div style="margin-bottom: 8px">
      <el-button type="info" size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
    </div>
    <el-table :data="pagedData" size="small" border empty-text="暂无未冲销借款" v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
          <el-table-column prop="billNo" label="单号" width="160"  sortable />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag size="small" type="warning">{{ row.loanBalance > 0 ? '在途' : '未冲销' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="借款金额" width="110"  sortable>
            <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column prop="loanBalance" label="未冲销金额" width="110"  sortable :formatter="(r,c,v) => formatMoney(v)" />
        </el-table>
        <TablePager :pager="pager" @change="loadOpenLoans" />
      </el-card>

      <!-- ============ 还款申请：还款详情 ============ -->
      <el-card v-if="mode === 'REPAY'" header="还款详情（可同时冲销多笔借款）" style="margin-top: 12px">
        <el-button type="primary" size="small" @click="openRepayLoan">+ 选择借款单冲销</el-button>
        <el-table :data="b.form.loanOffsets" size="small" border style="margin-top: 8px" v-loading="loading">
          <el-table-column prop="loanBillNo" label="单据编号" width="160"  sortable />
          <el-table-column label="单据类型" width="110">
            <template #default="{ row }"><el-tag size="small">{{ row.sourceType === 'APPLY' ? '申请单' : '借款单' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="借款说明" min-width="140">
            <template #default="{ row }"><el-input v-model="row.loanRemark" size="small" /></template>
          </el-table-column>
          <el-table-column label="本次还款金额" width="150"><template #header><span style="color:#f56c6c">*</span>本次还款金额</template>
            <template #default="{ row }"><el-input-number v-model="row.offsetAmount" :min="0" :precision="2" style="width: 100%" @change="syncRepayLine(b)" /></template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }"><el-button link size="small" type="danger" @click="removeOffset(b, $index)">删</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- ============ 还款申请：公司收款账号 ============ -->
      <el-card v-if="mode === 'REPAY'" header="公司收款账号信息" style="margin-top: 12px">
        <el-form ref="companyFormRef" :model="b.form.bill.extJson" :rules="companyRules" label-width="110px" size="default">
          <el-row :gutter="12">
            <el-col :xs="24" :md="8"><el-form-item label="户名" prop="companyAccountName"><el-input v-model="b.form.bill.extJson.companyAccountName" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="收款账号" prop="companyAccountNo"><el-input v-model="b.form.bill.extJson.companyAccountNo" /></el-form-item></el-col>
            <el-col :xs="24" :md="8"><el-form-item label="开户行" prop="companyBankName"><el-input v-model="b.form.bill.extJson.companyBankName" /></el-form-item></el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="还款方式" prop="repayMethod">
                <el-select v-model="b.form.bill.extJson.repayMethod" style="width: 100%">
                  <el-option label="银行转账" value="BANK" />
                  <el-option label="现金" value="CASH" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>
    </template>
  </BillWorkbench>

  <!-- 还款：选择借款单弹窗 -->
  <el-dialog v-model="repayDialog" title="选择借款单冲销" width="600px">
    <el-table :data="b_loanCandidates" size="small" v-loading="loading" @row-click="pickRepayLoan" highlight-current-row @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="billNo" label="借款单号" width="160"  sortable />
      <el-table-column prop="title" label="标题"  min-width="200" show-overflow-tooltip sortable />
      <el-table-column prop="loanBalance" label="可冲销金额" width="120"  sortable :formatter="(r,c,v) => formatMoney(v)" />
    </el-table>
  </el-dialog>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { ref, computed, watch } from 'vue'
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BillWorkbench from '../../components/bill/BillWorkbench.vue'
import { userApi, billApi, store } from '../../api'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useAutoFill } from '@/composables/useAutoFill'
import { billRules } from '@/constants/billRequired'
import { commonRules } from '@/composables/useFormRules'
import { validateForms } from '@/composables/useSubmitGuard'
const { saveFormHistory } = useAutoFill()
// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const props = defineProps({
  mode: { type: String, default: 'LOAN' },
})
const selRows = ref([])
const loading = ref(false)
const wb = ref(null)
const users = ref([])
const openLoans = ref([])

// ---------- 表头必填：通用字段走登记表（红星/闸门同源），单据说明本页加严 ----------
const formRules = computed(() => ({
  ...billRules(props.mode === 'LOAN' ? 'LOAN' : 'REPAY', ''),
  // 还款单上这个字段是「还款人」，提示语随场景改口
  applyUserId: [{ required: true, message: '请选择还款人' }],
  remark: commonRules.required('单据说明为必填'),
}))
const loanRules = {
  loanType: [{ required: true, message: '请选择借款类型' }],
  loanAmount: [{ required: true, type: 'number', min: 0.01, message: '请输入借款金额（大于 0）', trigger: 'change' }],
  payMethod: [{ required: true, message: '请选择收款方式' }],
  accountName: [{ required: true, message: '请输入账户名称' }],
  accountNo: [{ required: true, message: '请输入账号' }],
  bankName: [{ required: true, message: '请输入开户行' }],
}
const companyRules = {
  companyAccountName: [{ required: true, message: '请输入户名' }],
  companyAccountNo: [{ required: true, message: '请输入收款账号' }],
  companyBankName: [{ required: true, message: '请输入开户行' }],
  repayMethod: [{ required: true, message: '请选择还款方式' }],
}
const formRef = ref(null)
const loanFormRef = ref(null)
const companyFormRef = ref(null)
// P0 遗留专项：myPage 支持 minBalance 服务端过滤，未冲销借款列表切 server 模式
const pager = useTablePager({ mode: 'server', storageKey: 'loan_apply' })
const { pagedData } = pager
const currentUserName = ref(store.user?.username || store.user?.userName || '当前用户')
const repayDialog = ref(false)
const b_loanCandidates = ref([])

const applicantDept = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.deptName || u.departmentName)) || '—'
})
const applicantCompany = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.companyName || u.company)) || '—'
})
const repayUserDept = computed(() => {
  const b = wb.value && wb.value.b
  if (!b) return '—'
  const u = users.value.find((x) => x.id === b.form.bill.applyUserId)
  return (u && (u.deptName || u.departmentName)) || '—'
})

function loanAmount(b) { return b.form.bill.extJson.loanAmount || 0 }
function syncLoanLine(b) {
  ensureLine(b)
  b.form.lines[0].amount = Math.round(Number(b.form.bill.extJson.loanAmount || 0) * 100) / 100
  b.form.lines[0].itemId = loanItemId(b)
  b.form.bill.totalAmount = b.form.lines[0].amount
}
function syncRepayLine(b) {
  ensureLine(b)
  const total = b.form.loanOffsets.reduce((a, o) => a + Number(o.offsetAmount || 0), 0)
  b.form.lines[0].amount = Math.round(total * 100) / 100
  b.form.lines[0].itemId = loanItemId(b)
}
function loanItemId(b) {
  const it = b.items.find((x) => (x.itemName || '').includes('借款') || (x.itemName || '').includes('备用'))
  return it ? it.id : (b.items[0] ? b.items[0].id : null)
}
function ensureLine(b) {
  if (!b.form.lines.length) b.form.lines.push(b.newLine())
}
// D-TR-02 同口径：冲销清单双向同步 extJson.loanOffsets——草稿即存，编辑重提回读回填
// （此前只在提交时写入，重开还款草稿冲销行整块丢失）
watch(() => {
  const b = wb.value && wb.value.b
  return b && b.form.loanOffsets
}, () => {
  const b = wb.value && wb.value.b
  if (!b || props.mode !== 'REPAY') return
  const ext = b.form.bill.extJson
  if (JSON.stringify(ext.loanOffsets || []) !== JSON.stringify(b.form.loanOffsets)) {
    ext.loanOffsets = b.form.loanOffsets.map((o) => ({ ...o }))
  }
}, { deep: true })
watch(() => {
  const b = wb.value && wb.value.b
  return b && b.form.bill.extJson.loanOffsets
}, (v) => {
  const b = wb.value && wb.value.b
  if (!b || props.mode !== 'REPAY' || !Array.isArray(v)) return
  if (JSON.stringify(b.form.loanOffsets) !== JSON.stringify(v)) b.form.loanOffsets = v.map((o) => ({ ...o }))
}, { deep: true })
async function onBeforeSubmit(b) {
  // 表头必填闸门：提示具体漏填项并滚动定位
  if (!(await validateForms([formRef]))) return false
  if (props.mode === 'LOAN') {
    syncLoanLine(b)
    b.form.bill.expectRepayDate = b.form.bill.extJson.expectRepayDate || null
    // P1-1 借款详情必填（类型/金额/收款方式/账户名称/账号/开户行）
    if (!(await validateForms([loanFormRef]))) return false
  } else {
    syncRepayLine(b)
    b.form.bill.extJson.loanOffsets = b.form.loanOffsets.map((o) => ({ ...o }))
    // P1-1 还款：至少一条冲销记录
    if (!b.form.loanOffsets.length) {
      ElMessage.warning('请至少选择一笔借款进行冲销'); return false
    }
    // P1-1 还款：每笔冲销金额 > 0
    if (b.form.loanOffsets.some((o) => !(Number(o.offsetAmount) > 0))) {
      ElMessage.warning('本次还款金额必须大于 0'); return false
    }
    // P1-1 公司收款账号必填（户名/账号/开户行/还款方式）
    if (!(await validateForms([companyFormRef]))) return false
  }
  return true
}
async function removeOffset(b, i) {
  try {
    await ElMessageBox.confirm('确认删除？此操作不可恢复。', '删除确认', { type: 'warning' })
  } catch { return }
  b.form.loanOffsets.splice(i, 1)
  syncRepayLine(b)
}

/**
 * D-DEC-396：冲销候选面原按 status='APPROVED' 单值过滤、与余额口径不同源——借款一旦被在途冲抵
 * 迁到 PARTIAL_REPAID（ExpenseDetailService:224 提交期即迁档），本弹窗对它立刻失明而余额真实存在；
 * 后端读侧同族断链已按 {@code APPROVED／PARTIAL_REPAID／REPAID} 收口（ExpenseWfEventListener:639），
 * 此处取同一组状态口径，并以服务端 loan_balance>0（minBalance=0）为主判据。
 */
const LOAN_OPEN_STATUSES = ['APPROVED', 'PARTIAL_REPAID', 'REPAID']
async function openRepayLoan() {
  const pages = await Promise.all(LOAN_OPEN_STATUSES.map((s) =>
    billApi.myPage({ pageNum: 1, pageSize: 50, billType: 'LOAN', status: s, minBalance: 0 })))
  const seen = new Map()
  for (const r of pages) for (const x of (r.list || [])) if (!seen.has(x.id)) seen.set(x.id, x)
  b_loanCandidates.value = [...seen.values()]
  repayDialog.value = true
}
function pickRepayLoan(row) {
  const b = wb.value && wb.value.b
  if (!b) return
  if (!b.form.loanOffsets.find((o) => o.loanBillId === row.id)) {
    b.form.loanOffsets.push({ loanBillId: row.id, loanBillNo: row.billNo, offsetAmount: Number(row.loanBalance || 0), offsetType: 'REPAY', sourceType: 'LOAN', loanRemark: row.title || '' })
  }
  repayDialog.value = false
  syncRepayLine(b)
}
function onRepayUserChange(b, id) {
  const u = users.value.find((x) => x.id === id)
  if (u) b.form.bill.applyUserName = u.nickName || u.userName
}

/** P0 遗留专项：未冲销借款列表（服务端分页 + loan_balance>0 过滤） */
async function loadOpenLoans() {
  await pager.load(async ({ pageNum, pageSize }) => {
    const r = await billApi.myPage({ pageNum, pageSize, billType: 'LOAN', status: 'APPROVED', minBalance: 0 })
    openLoans.value = r.list || []
    return { list: openLoans.value, total: r.total || 0 }
  })
}

const route = useRoute()
const router = useRouter()
// 借款⇄还款切换（RA-PAGE-21 2.2）
function onModeSwitch(v) {
  if (v === props.mode) return
  router.push(v === 'REPAY' ? '/expense/repay' : '/expense/loan')
}
onMounted(async () => {
  // 编辑草稿/驳回单重提：携带 editBillId 深链时载入该单据到表单（LOAN/REPAY 同组件，按单据实际类型载入）
  try {
    const eb = route.query.editBillId
    if (eb && wb.value?.b?.loadBillForEdit) {
      await wb.value.b.loadBillForEdit({ id: Number(eb) })
      router.replace({ query: {} })
    }
  } catch (e) { /* 载入失败提示已在 loadBillForEdit 内 */ }
  loading.value = true
  try {
    const list = (await userApi.page({ pageNum: 1, pageSize: 200 })).list || []
    users.value = list
    currentUserName.value = store.user?.realName || store.user?.nickName || store.user?.username || store.user?.userName || '当前用户'
    const b = wb.value && wb.value.b
    const uid = store.user?.userId ?? store.user?.id
    if (b && !b.form.bill.applyUserId) {
      // P1-5 默认申请人 = 当前登录人，严格匹配 me.id，不回退 list[0]
      const me = uid ? list.find((x) => x.id === uid) : null
      if (me) {
        b.form.bill.applyUserId = me.id
        b.form.bill.applyUserName = me.nickName || me.userName
      } else {
        b.form.bill.applyUserId = null
        b.form.bill.applyUserName = ''
        if (uid != null) ElMessage.warning('未在用户列表中匹配到当前登录人，请手动选择申请人')
      }
    }
    if (props.mode === 'LOAN') {
      await loadOpenLoans()
    }
  } catch (e) { /* ignore */ } finally { loading.value = false }
})

function exportCsv() {
  if (!openLoans.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'billNo', label: '单号' }, { prop: 'totalAmount', label: '借款金额' }, { prop: 'loanBalance', label: '未冲销金额' }]
  const header = cols.map((c) => c.label).join(',')
  const lines = openLoans.value.map((row) => cols.map((c) => {
    const v = row?.[c.prop]; if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `LoanApply_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}


function handleSelectionChange(val) { selRows.value = val }

function formatMoney(val) {
  return Number(val || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

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
  a.download = `LoanApply_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>
