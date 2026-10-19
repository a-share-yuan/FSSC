<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>采购退货单（AP 退货退款冲减应付）</span>
        <div class="ops">
          <el-input v-model="q.vendorCode" size="small" style="width: 130px" placeholder="供应商代码" clearable @keyup.enter="load" />
          <el-input v-model="q.keyword" size="small" style="width: 130px" placeholder="单号/供应商名" clearable @keyup.enter="load" />
          <el-select v-model="q.status" size="small" style="width: 110px" placeholder="状态" clearable @change="load">
            <el-option v-for="(l, c) in RETURN_STATUS" :key="c" :label="l" :value="c" />
          </el-select>
          <el-date-picker v-model="q.range" type="daterange" size="small" value-format="YYYY-MM-DD" range-separator="~" start-placeholder="退货起" end-placeholder="退货止" @change="onRange" style="width: 220px" />
          <el-button size="small" @click="load">查询</el-button>
          <el-button type="primary" size="small" @click="openCreate" v-if="can('returns:pur:create')">新建退货单</el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" size="small" border stripe>
      <el-table-column prop="billNo" label="退货单号" min-width="170" />
      <el-table-column prop="vendorName" label="供应商" min-width="130" show-overflow-tooltip />
      <el-table-column prop="billDate" label="退货日期" width="115" />
      <el-table-column prop="amount" label="金额" width="120" align="right" />
      <el-table-column label="方式" width="100">
        <template #default="{ row }"><el-tag size="small" :type="row.returnMode === 'REFUND' ? 'danger' : 'info'">{{ returnModeLabel(row.returnMode) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="状态" width="92">
        <template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)">{{ returnStatusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <!-- R5-D-086：审核人列外显（approver 列已在库，原列表 6 列读不到审核人） -->
      <el-table-column prop="approver" label="审核人" width="110" />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          <el-button v-for="a in actions(row)" :key="a.key" link type="primary" size="small" @click="act(row, a)">{{ a.label }}</el-button>
          <el-tooltip v-if="hiddenBySelf(row)" content="本人制单：不可自审/自驳，已审核单不可自作废，请由他人办理" placement="top">
            <el-tag size="small" type="info" effect="plain">本人单据</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="editing ? '编辑采购退货单' : '新建采购退货单'" width="820px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="供应商代码" prop="vendorCode"><el-input v-model="form.vendorCode" placeholder="与 BCP 往来单位编码对齐" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商名称"><el-input v-model="form.vendorName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="公司ID"><el-input v-model="form.companyId" placeholder="1" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="退货日期" prop="billDate">
            <el-date-picker v-model="form.billDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="退货方式" prop="returnMode">
            <el-select v-model="form.returnMode" style="width: 100%">
              <el-option label="退货退款(进对账)" value="REFUND" /><el-option label="退货补货(不进)" value="EXCHANGE" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="源单类型">
            <el-select v-model="form.sourceDocType" clearable style="width: 100%" placeholder="选填">
              <el-option label="采购入库单" value="PO_RECEIPT" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="源单号"><el-input v-model="form.sourceBillNo" placeholder="对账明细追溯用" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="退货原因">
            <el-select v-model="form.returnReason" clearable style="width: 100%" placeholder="质量不合格/破损/错发/多发/合同取消/其他">
              <el-option v-for="r in REASONS" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">明细行</el-divider>
        <el-table :data="form.lines" size="small" border>
          <el-table-column label="物料编码" width="130"><template #default="{ row }"><el-input v-model="row.itemCode" /></template></el-table-column>
          <el-table-column label="物料名称" min-width="140"><template #default="{ row }"><el-input v-model="row.itemName" /></template></el-table-column>
          <el-table-column label="数量" width="100"><template #default="{ row }"><el-input v-model="row.qty" /></template></el-table-column>
          <el-table-column label="单价" width="110"><template #default="{ row }"><el-input v-model="row.price" @change="recalc(row)" /></template></el-table-column>
          <el-table-column label="税率%" width="90"><template #default="{ row }"><el-input v-model="row.taxRate" /></template></el-table-column>
          <el-table-column label="金额" width="120"><template #default="{ row }"><el-input v-model="row.amount" /></template></el-table-column>
          <el-table-column label="操作" width="70"><template #default="{ row, $index }"><el-button link type="danger" size="small" @click="form.lines.splice($index, 1)">删</el-button></template></el-table-column>
        </el-table>
        <div class="line-sum">
          明细合计：<b>{{ lineSum() }}</b>
          <!-- R5-D-085：税额/价税合计为只读派生（含税口径：税额=Σ amount×taxRate/(100+taxRate)，
               与后端 PurReturnService.deriveTaxAmount 同式；保存时按明细汇总回写表头 tax_amount） -->
          <span style="margin-left:12px">税额（派生）：<b>{{ derivedTax() }}</b></span>
          <span style="margin-left:12px">价税合计（派生）：<b>{{ derivedInclusive() }}</b></span>
          <el-form-item label="表头金额" prop="amount" style="display:inline-flex;margin:0 0 0 16px">
            <el-input v-model="form.amount" style="width: 140px" />
          </el-form-item>
          <span style="margin-left:8px;color:#909399;font-size:12px">须等于明细合计（尾差 ≤0.01）</span>
        </div>
        <el-button size="small" type="primary" plain @click="addLine">+ 加一行</el-button>
      </el-form>
      <template #footer>
        <el-button size="small" @click="formVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="采购退货单详情" width="820px" destroy-on-close>
      <el-descriptions :column="2" size="small" border v-if="detail">
        <el-descriptions-item label="单号">{{ detail.billNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag size="small" :type="statusTag(detail.status)">{{ returnStatusLabel(detail.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.vendorName }}（{{ detail.vendorCode }}）</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ detail.billDate }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ detail.amount }}</el-descriptions-item>
        <el-descriptions-item label="方式">{{ returnModeLabel(detail.returnMode) }}</el-descriptions-item>
        <el-descriptions-item label="源单">{{ detail.sourceBillNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="退货原因">{{ detail.returnReason || '—' }}</el-descriptions-item>
        <!-- R5-D-086：审核留痕外显（approver/approve_time/void_reason/create_time 列已在库，纯显示收口） -->
        <el-descriptions-item label="审核人">{{ detail.approver || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detail.approveTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="作废/驳回原因">{{ detail.voidReason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.lines || []" size="small" border style="margin-top: 10px">
        <el-table-column prop="itemCode" label="物料编码" />
        <el-table-column prop="itemName" label="物料名称" />
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="price" label="单价" width="110" />
        <el-table-column prop="amount" label="金额" width="110" />
        <!-- R5-D-086：明细补 税率/单位/税额 三列（税额按含税口径派生，与 lineTax 同式） -->
        <el-table-column prop="taxRate" label="税率%" width="80" />
        <el-table-column prop="uom" label="单位" width="80" />
        <el-table-column label="税额" width="100"><template #default="{ row }">{{ lineTax(row).toFixed(2) }}</template></el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="reasonVisible" :title="reasonTitle" width="420px" destroy-on-close>
      <el-input v-model="reasonText" type="textarea" :rows="2" :placeholder="reasonKind === 'void' ? '作废原因（必填，退出对账口径留痕）' : '驳回原因（必填）'" />
      <template #footer>
        <el-button size="small" @click="reasonVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doReason">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { RETURN_STATUS, RETURN_MODE, returnStatusLabel, returnModeLabel } from '@/constants/enums'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { returnApi, store } from '../../api'
import { hasPerm } from '../../utils/perm'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'

const REASONS = ['质量不合格', '破损', '错发', '多发', '合同取消', '其他']

const rows = ref([])
const loading = ref(false)
const q = reactive({ pageNum: 1, pageSize: 20, vendorCode: '', keyword: '', status: '', billDateStart: '', billDateEnd: '', range: [] })
const formVisible = ref(false)
const detailVisible = ref(false)
const reasonVisible = ref(false)
const editing = ref(false)
const detail = ref(null)
const reasonKind = ref('')
const reasonTitle = ref('')
const reasonText = ref('')
const current = ref(null)
const form = reactive({ lines: [] })
const formRef = ref(null)
const { rules: formRules } = useFormRules('PUR_RETURN')
const { submit: guardSubmit } = useSubmitGuard(formRef)

const can = (code) => hasPerm(code)
const statusTag = (s) => ({ APPROVED: 'success', VOIDED: 'danger', REJECTED: 'warning', PENDING: 'primary' }[s] || 'info')

/** 本人制单时被禁的自审类动作（与后端 PurReturnService.assertNotSelf 同源，改一处须同步另一处） */
const SELF_DENIED = { PENDING: ['approve', 'reject'], APPROVED: ['void'] }
const myId = () => store?.user?.userId ?? store?.user?.id ?? null
/** 是否由当前登录用户制单（create_by 为空 = 历史/外部导入数据，不作归属判定） */
const isMine = (row) => row.createBy != null && myId() != null && String(row.createBy) === String(myId())
/** 确因「本人制单」而隐藏了按钮 → 给出原因，避免用户误以为功能缺失 */
const hiddenBySelf = (row) =>
  isMine(row) && ((row.status === 'PENDING' && can('returns:pur:approve')) || (row.status === 'APPROVED' && can('returns:pur:void')))

function onRange(v) {
  q.billDateStart = v && v[0] ? v[0] : ''
  q.billDateEnd = v && v[1] ? v[1] : ''
  load()
}

function actions(row) {
  const denied = isMine(row) ? (SELF_DENIED[row.status] || []) : []
  // R5-D-087：带 need 的动作必须过 can(a.need)——原只滤「本人制单」，无 void 权限的角色仍见【作废】，
  // 点击被后端 4030 拦＝看得见做不了；此处与授权同口径（can 与 :14 openCreate 同函数）
  return rawActions(row).filter((a) => !denied.includes(a.key) && can(a.need))
}

function rawActions(row) {
  switch (row.status) {
    case 'DRAFT': return [
      { key: 'edit', label: '编辑', need: 'returns:pur:create' },
      { key: 'submit', label: '提交', need: 'returns:pur:submit' },
      { key: 'delete', label: '删除', need: 'returns:pur:create' },
    ]
    case 'PENDING': return [
      { key: 'approve', label: '审核', need: 'returns:pur:approve' },
      { key: 'reject', label: '驳回', need: 'returns:pur:approve' },
      { key: 'void', label: '作废', need: 'returns:pur:void' },
    ]
    case 'REJECTED': return [
      { key: 'edit', label: '编辑', need: 'returns:pur:create' },
      { key: 'submit', label: '重新提交', need: 'returns:pur:submit' },
    ]
    case 'APPROVED': return [{ key: 'void', label: '作废', need: 'returns:pur:void' }]
    default: return []
  }
}

const lineSum = () => (form.lines || []).reduce((s, l) => s + (Number(l.amount) || 0), 0).toFixed(2)

async function load() {
  loading.value = true
  try {
    const r = await returnApi.purPage({ ...q, vendorCode: q.vendorCode || undefined, status: q.status || undefined, keyword: q.keyword || undefined })
    rows.value = r.list || []
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, vendorCode: '', vendorName: '', companyId: 1, billDate: '', amount: '', taxAmount: '', returnMode: 'REFUND', sourceDocType: '', sourceBillNo: '', returnReason: '', remark: '', lines: [emptyLine()] })
  formVisible.value = true
}

function addLine() { form.lines.push(emptyLine()) }
function emptyLine() { return { itemCode: '', itemName: '', qty: 1, price: '', taxRate: 13, amount: '' } }
function recalc(row) { row.amount = (Number(row.qty) * Number(row.price) || 0).toFixed(2) }
/** R5-D-085：行税额（含税口径，与后端 PurReturnService.deriveTaxAmount 同式）：amount×rate/(100+rate) */
const lineTax = (l) => {
  const a = Number(l.amount) || 0
  const r = Number(l.taxRate) || 0
  return r > 0 ? (a * r) / (100 + r) : 0
}
const derivedTax = () => (form.lines || []).reduce((s, l) => s + lineTax(l), 0).toFixed(2)
const derivedInclusive = () => (form.lines || []).reduce((s, l) => s + (Number(l.amount) || 0), 0).toFixed(2)

async function doSave() {
  await guardSubmit(async () => {
    // 明细行闸门与后端 validateLineSum 同口径（后端还会比对"合计=表头金额，尾差≤0.01"）
    const filled = (form.lines || []).filter(l => (l.itemName || l.itemCode) && Number(l.amount) > 0)
    if (!filled.length) { ElMessage.warning('请至少填写一行退货明细（物料＋金额）'); return }
    const lines = (form.lines || []).map(l => ({ ...l, amount: Number(l.amount) || 0, qty: Number(l.qty) || 0, price: Number(l.price) || 0, taxRate: Number(l.taxRate) || 0 }))
    // R5-D-085：表头税额不再吃空输入框（原 form.taxAmount 无输入框恒兜 0），按明细派生回写
    const body = { ...form, amount: Number(form.amount) || 0, taxAmount: Number(derivedTax()) || 0, companyId: Number(form.companyId) || 1, lines }
    try {
      if (editing.value) await returnApi.purUpdate(form.id, body)
      else await returnApi.purCreate(body)
      ElMessage.success('保存成功')
      formVisible.value = false
      await load()
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function openDetail(row) {
  detail.value = await returnApi.purGet(row.id)
  detailVisible.value = true
}

async function act(row, a) {
  current.value = row
  const id = row.id
  const m = {
    submit: () => returnApi.purSubmit(id),
    approve: () => returnApi.purApprove(id),
    delete: () => returnApi.purDelete(id),
  }
  if (m[a.key]) { try { await m[a.key](); ElMessage.success('操作成功'); await load() } catch (e) { ElMessage.error(e.message) }; return }
  if (a.key === 'edit') {
    detail.value = await returnApi.purGet(id)
    editing.value = true
    Object.assign(form, detail.value, { lines: (detail.value.lines || []).map(l => ({ ...l })) })
    formVisible.value = true
    return
  }
  if (a.key === 'reject' || a.key === 'void') {
    reasonKind.value = a.key; reasonTitle.value = a.key === 'void' ? '作废采购退货单' : '驳回采购退货单'
    reasonText.value = ''; reasonVisible.value = true
  }
}

async function doReason() {
  // 与后端 reject/void 的原因断言同口径（原因会写进 void_reason 作为留痕，不能空）
  if (!reasonText.value || !reasonText.value.trim()) {
    ElMessage.warning(reasonKind.value === 'void' ? '请填写作废原因' : '请填写驳回原因'); return
  }
  const id = current.value.id
  try {
    if (reasonKind.value === 'reject') await returnApi.purReject(id, reasonText.value)
    else await returnApi.purVoid(id, reasonText.value)
    ElMessage.success('操作成功')
    reasonVisible.value = false
    await load()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.ops { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.line-sum { margin: 6px 0; font-size: 13px; }
</style>
