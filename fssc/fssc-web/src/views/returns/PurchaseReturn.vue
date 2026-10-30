<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>采购退货单（AP 退货退款冲减应付）</span>
        <div class="ops">
          <el-input v-model="q.vendorCode" size="small" style="width: 130px" placeholder="供应商代码" clearable @keyup.enter="search" />
          <el-input v-model="q.keyword" size="small" style="width: 130px" placeholder="单号/供应商名" clearable @keyup.enter="search" />
          <el-select v-model="q.status" size="small" style="width: 110px" placeholder="状态" clearable @change="search">
            <el-option v-for="(l, c) in RETURN_STATUS" :key="c" :label="l" :value="c" />
          </el-select>
          <el-date-picker v-model="q.range" type="daterange" size="small" value-format="YYYY-MM-DD" range-separator="~" start-placeholder="退货起" end-placeholder="退货止" @change="onRange" style="width: 220px" />
          <el-button size="small" @click="search">查询</el-button>
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
      <!-- 审核人列外显（approver 列已在库，原列表 6 列读不到审核人） -->
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

    <TablePager v-model:page="q.pageNum" v-model:size="q.pageSize" :total="total" @change="load" />

    <el-dialog v-model="formVisible" :title="editing ? '编辑采购退货单' : '新建采购退货单'" width="900px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px" size="small">
        <el-row :gutter="12">
          <!-- 供应商代码由纯输入框改远程检索下拉（与合同/付款计划同组件）。
               原实现任意字符串都能提交（如 XXX-999），名称留空则 vendor_name 落空串⇒列表「供应商」列空白。
               名称改只读：一律以供应商主数据为准（后端 PurReturnService.assertVendorInMaster 同一规则），
               手填名称与现有名称分叉会让同一供应商在单据上出现两种写法。 -->
          <el-col :span="12"><el-form-item label="供应商代码" prop="vendorCode">
            <SupplierSelect v-model="form.vendorCode" value-key="code" placeholder="检索已有供应商（代码/名称）" @change="onVendorChange" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商名称">
            <el-input v-model="form.vendorName" readonly placeholder="选供应商代码后按主数据自动带出" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="所属公司">
            <el-select v-model="form.companyId" filterable clearable placeholder="请选择公司" style="width:100%">
              <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item></el-col>
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
          <el-col :span="12"><el-form-item label="源单号"><el-input v-model="form.sourceBillNo" placeholder="对账明细追溯用" maxlength="48" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="退货原因">
            <el-select v-model="form.returnReason" clearable style="width: 100%" placeholder="选择退货原因">
              <el-option v-for="r in reasonOptions" :key="r.value" :label="r.label" :value="r.value" />
            </el-select>
          </el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">明细行</el-divider>
        <el-table :data="form.lines" size="small" border>
          <el-table-column label="物料编码" width="130"><template #default="{ row }"><el-input v-model="row.itemCode" maxlength="64" /></template></el-table-column>
          <el-table-column label="物料名称" min-width="140"><template #default="{ row }"><el-input v-model="row.itemName" /></template></el-table-column>
          <!-- 数量列原先不挂 recalc ⇒ 先填单价后改数量时行金额停在旧值，
               表头也随明细合计同步错下去，"表头＝明细合计"的等式校验查不出。数量与单价同权：任一变更即重算行金额。 -->
          <el-table-column label="数量" width="100"><template #default="{ row }"><el-input v-model="row.qty" @change="recalc(row)" /></template></el-table-column>
          <!-- 明细补「单位」列（原 uom 恒 NULL，行级追溯断在单位）。
               留空时后端按物料编码从采购订单行回填单位，填了以人工为准。 -->
          <el-table-column label="单位" width="80"><template #default="{ row }"><el-input v-model="row.uom" placeholder="台/个" maxlength="16" /></template></el-table-column>
          <el-table-column label="单价" width="110"><template #default="{ row }"><el-input v-model="row.price" @change="recalc(row)" /></template></el-table-column>
          <el-table-column label="税率%" width="90"><template #default="{ row }"><el-input v-model="row.taxRate" /></template></el-table-column>
          <el-table-column label="金额" width="120"><template #default="{ row }"><el-input v-model="row.amount" /></template></el-table-column>
          <el-table-column label="操作" width="70"><template #default="{ row, $index }"><el-button link type="danger" size="small" @click="form.lines.splice($index, 1)">删</el-button></template></el-table-column>
        </el-table>
        <div class="line-sum">
          明细合计：<b>{{ lineSum() }}</b>
          <!-- 税额/价税合计为只读派生（含税规则：税额=Σ amount×taxRate/(100+taxRate)，
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
        <!-- 审核记录外显（approver/approve_time/void_reason/create_time 列已在库，纯显示办结） -->
        <el-descriptions-item label="审核人">{{ detail.approver || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ formatDateTime(detail.approveTime) || '—' }}</el-descriptions-item>
        <el-descriptions-item label="作废/驳回原因">{{ detail.voidReason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detail.createTime) || '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.lines || []" size="small" border style="margin-top: 10px">
        <el-table-column prop="itemCode" label="物料编码" />
        <el-table-column prop="itemName" label="物料名称" />
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="price" label="单价" width="110" />
        <el-table-column prop="amount" label="金额" width="110" />
        <!-- 明细补 税率/单位/税额 三列（税额按含税规则派生，与 lineTax 同式） -->
        <el-table-column prop="taxRate" label="税率%" width="80" />
        <el-table-column prop="uom" label="单位" width="80" />
        <el-table-column label="税额" width="100"><template #default="{ row }">{{ lineTax(row).toFixed(2) }}</template></el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="reasonVisible" :title="reasonTitle" width="420px" destroy-on-close>
      <el-input v-model="reasonText" type="textarea" :rows="2" :placeholder="reasonKind === 'void' ? '作废原因（必填，退出对账需记录）' : '驳回原因（必填）'" />
      <template #footer>
        <el-button size="small" @click="reasonVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="doReason">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { RETURN_STATUS, RETURN_MODE, returnStatusLabel, returnModeLabel } from '@/constants/enums'
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { returnApi, store, orgApi } from '../../api'
import { hasPerm } from '../../utils/perm'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { formatDateTime } from '@/utils/format'
import TablePager from '@/components/common/TablePager.vue'
import SupplierSelect from '@/components/common/SupplierSelect.vue'
import { useDict } from '@/composables/useDict'

/**
 * 退货原因改字典驱动（dict_type=enum_return_reason_pur，采购域单列，
 * 与销售域 enum_return_reason_sale 不共用一套——两域原因集本就不同：采购有「多发/合同取消」，
 * 销售有「客户取消/合同终止」）。
 * 下面这份本地常量是 useDict 的兜底：字典未配置/接口不通时逐字回落成原先的六项，界面零变化。
 */
const REASON_FALLBACK = {
  质量不合格: '质量不合格', 破损: '破损', 错发: '错发', 多发: '多发', 合同取消: '合同取消', 其他: '其他',
}
const reasonDict = useDict('enum_return_reason_pur', REASON_FALLBACK)
const reasonOptions = reasonDict.options

/** 选中供应商后按主数据带出名称（名称框只读；后端保存时还会以主数据为准再覆盖一次） */
function onVendorChange(sup) {
  form.vendorName = sup ? (sup.supplierName || sup.shortName || '') : ''
}

// 公司列表（用于下拉选择）
const companies = ref([])
// orgApi 没有 companyList，`orgApi.companyList?.()` 的可选链把「方法不存在」吞成
// undefined ⇒ 下拉恒空且无任何提示。公司树接口本就返回全量，改走 companyTree 并摊平。
async function loadCompanies() {
  try {
    const tree = await orgApi.companyTree()
    const flat = []
    const walk = (arr) => (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) })
    walk(tree)
    companies.value = flat
  } catch (e) {
    console.warn('加载公司列表失败', e)
  }
}

const rows = ref([])
const total = ref(0)
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
  search()
}

function search() { q.pageNum = 1; load() }

function actions(row) {
  const denied = isMine(row) ? (SELF_DENIED[row.status] || []) : []
  // 带 need 的动作必须过 can(a.need)——原只滤「本人制单」，无 void 权限的角色仍见【作废】，
  // 点击被后端 4030 拦＝看得见做不了；此处与授权同一规则（can 与 :14 openCreate 同函数）
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

// 明细变更即把合计回填表头金额（原表头为纯手输、仅校验 >0，易与明细合计不符）。
// 表头仍可手改，doSave 再做等式强校验（与后端 validateLineSum 同一规则，尾差 ≤0.01）。
watch(() => form.lines, () => { form.amount = lineSum() }, { deep: true })

async function load() {
  loading.value = true
  try {
    const r = await returnApi.purPage({ ...q, vendorCode: q.vendorCode || undefined, status: q.status || undefined, keyword: q.keyword || undefined })
    rows.value = r.list || []
    total.value = Number(r.total) || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, vendorCode: '', vendorName: '', companyId: 1, billDate: '', amount: '', taxAmount: '', returnMode: 'REFUND', sourceDocType: '', sourceBillNo: '', returnReason: '', remark: '', lines: [emptyLine()] })
  formVisible.value = true
}

function addLine() { form.lines.push(emptyLine()) }
// 单位随行提交（原 emptyLine 无 uom 字段⇒后端恒收不到，pur_return_line.uom 全表 NULL）
function emptyLine() { return { itemCode: '', itemName: '', qty: 1, uom: '', price: '', taxRate: 13, amount: '' } }
function recalc(row) { row.amount = (Number(row.qty) * Number(row.price) || 0).toFixed(2) }
/** 行税额（含税规则，与后端 PurReturnService.deriveTaxAmount 同式）：amount×rate/(100+rate) */
const lineTax = (l) => {
  const a = Number(l.amount) || 0
  const r = Number(l.taxRate) || 0
  return r > 0 ? (a * r) / (100 + r) : 0
}
const derivedTax = () => (form.lines || []).reduce((s, l) => s + lineTax(l), 0).toFixed(2)
const derivedInclusive = () => (form.lines || []).reduce((s, l) => s + (Number(l.amount) || 0), 0).toFixed(2)

async function doSave() {
  await guardSubmit(async () => {
    // 明细行校验与后端 validateLineSum 同一规则（后端还会比对"合计=表头金额，尾差≤0.01"）
    const filled = (form.lines || []).filter(l => (l.itemName || l.itemCode) && Number(l.amount) > 0)
    if (!filled.length) { ElMessage.warning('请至少填写一行退货明细（物料＋金额）'); return }
    // 表头金额＝明细合计的等式强校验（前端先拦，后端 validateLineSum 再拦一道）
    const headAmt = Number(form.amount) || 0
    const sumAmt = Number(lineSum())
    if (Math.abs(headAmt - sumAmt) > 0.01) {
      ElMessage.warning(`表头金额（${headAmt.toFixed(2)}）须等于明细合计（${sumAmt.toFixed(2)}），尾差 ≤0.01`)
      return
    }
    const lines = (form.lines || []).map(l => ({ ...l, amount: Number(l.amount) || 0, qty: Number(l.qty) || 0, price: Number(l.price) || 0, taxRate: Number(l.taxRate) || 0 }))
    // 表头税额不再吃空输入框（原 form.taxAmount 无输入框恒兜 0），按明细派生回写
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
  // 与后端 reject/void 的原因断言同一规则（原因会写进 void_reason 作为记录，不能空）
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

onMounted(() => { load(); loadCompanies(); reasonDict.load() })
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.ops { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.line-sum { margin: 6px 0; font-size: 13px; }
</style>
