<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>收货单</b>
        <el-button type="primary" size="small" @click="openCreate">新增收货单</el-button>
        <el-input v-model="q.keyword" placeholder="收货单号/PO/供应商" clearable style="width: 200px" @keyup.enter="load" />
        <el-select v-model="q.status" placeholder="状态" clearable style="width: 120px" @change="load">
          <el-option label="已收货" value="RECEIVED" />
          <el-option label="已开票" value="INVOICED" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-button size="small" @click="load">查询</el-button>
      <el-button size="small" @click="exportCsv">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="batchExport">导出选中({{ selRows.length }})</el-button>
        <span style="margin-left: auto; color: #909399; font-size: 12px">收货金额 <= 采购订单剩余可收（自动校验并回写累计收货）</span>
      </div>
    </template>

    <!-- R5-D-072：无查看权限时显式无权限态（4030 不得被吞成「没数据」空表） -->
    <el-empty v-if="!canList" description="当前账号无收货单查看权限，请联系管理员开通" />
    <SkeletonList v-if="loading && canList" :rows="8" :cols="5" />
    <el-table v-else-if="canList" :data="rows" size="small" stripe max-height="520" border @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="receiptNo" label="收货单号" width="150" fixed="left"  sortable />
      <el-table-column prop="poNo" label="采购订单" width="140"  sortable />
      <el-table-column prop="supplierName" label="供应商" min-width="130" show-overflow-tooltip  sortable />
      <el-table-column prop="receiptDate" label="收货日期" width="100"  sortable :formatter="(row, col, val) => formatDate(val)" />
      <el-table-column label="收货金额" width="110" align="right">
        <template #default="{ row }"><span style="font-weight:600">{{ fmt(row.amount) }}</span></template>
      </el-table-column>
      <el-table-column label="累计开票" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.invoiceAmount) }}</template>
      </el-table-column>
      <el-table-column label="未开票" width="110" align="right">
        <template #default="{ row }">
          <!-- R5-D-070：负值＝累计开票超收货额（异常/超额），红字加粗显性提示并示"超额 xx"语义 -->
          <span :style="{ color: unInvoicedOf(row) < 0 ? '#f56c6c' : unInvoicedOf(row) > 0 ? '#e6a23c' : '#909399', fontWeight: unInvoicedOf(row) < 0 ? 600 : 400 }">
            {{ unInvoicedOf(row) < 0 ? '超额 ' + fmt(-unInvoicedOf(row)) : fmt(unInvoicedOf(row)) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="receiverName" label="收货人" width="90"  sortable />
      <el-table-column prop="warehouse" label="仓库" width="100"  sortable />
      <el-table-column label="质检" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="qualityTag(row.qualityStatus)" size="small">{{ qualityLabel(row.qualityStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deliveryNote" label="送货单号" width="130" show-overflow-tooltip  sortable />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'INVOICED' ? 'success' : row.status === BillStatus.CLOSED ? 'info' : 'warning'" size="small">
            {{ { RECEIVED: '已收货', INVOICED: '已开票', CLOSED: '已关闭' }[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="info" size="small" @click="viewDetail(row)">详情</el-button>
          <!-- D-DEC-334：行内追溯入口——此前抽屉死挂载（openTrace 零调用点），数据在、页面不给看 -->
          <el-button link size="small" @click.stop="openTrace(row)">追溯</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 10px; justify-content: flex-end" layout="total, prev, pager, next"
                   :total="total" :page-size="q.pageSize" :current-page="q.pageNum" @current-change="(n) => { q.pageNum = n; load() }" />

    <!-- 新增弹窗 -->
    <el-dialog v-model="dlg" title="新增收货单" width="720px">
      <el-form ref="formRef" :rules="formRules" :model="form" label-width="100px" size="small">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="收货单号">
              <el-input v-model="form.receiptNo" placeholder="留空自动取号（也可手填，如 GRN202608001）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收货日期" prop="receiptDate">
              <el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="采购订单">
              <el-select v-model="form.poId" filterable clearable style="width: 100%" placeholder="选择采购订单（可选）" @change="onPoChange">
                <el-option v-for="po in pos" :key="po.id" :label="po.poNo + ' | ' + po.supplierName + ' (剩 ' + fmt(Number(po.amount || 0) - Number(po.receivedAmount || 0)) + ')'" :value="po.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.supplierId" filterable style="width: 100%" :disabled="!!form.poId" placeholder="搜索选择供应商" @change="(id) => { const s = suppliers.find(x => x.id === id); if (s) form.supplierName = s.supplierName }">
                <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierCode + ' ' + s.supplierName" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">收货信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="收货金额" prop="amount">
              <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收货人">
              <el-input v-model="form.receiverName" placeholder="实际收货人" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收货仓库">
              <el-select v-model="form.warehouse" filterable allow-create style="width: 100%" placeholder="选择或输入仓库">
                <el-option label="主仓库" value="主仓库" />
                <el-option label="原料仓" value="原料仓" />
                <el-option label="成品仓" value="成品仓" />
                <el-option label="备品备件仓" value="备品备件仓" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="质检状态">
              <el-select v-model="form.qualityStatus" style="width: 100%">
                <el-option label="待检" value="PENDING" />
                <el-option label="合格" value="PASSED" />
                <el-option label="不合格" value="FAILED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="送货单号">
              <el-input v-model="form.deliveryNote" placeholder="供应商送货单号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="收货备注说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="dlgDetail" title="收货单详情" width="650px">
      <el-descriptions :column="2" border size="small" v-if="detailRow">
        <el-descriptions-item label="收货单号">{{ detailRow.receiptNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailRow.status === 'INVOICED' ? 'success' : detailRow.status === BillStatus.CLOSED ? 'info' : 'warning'" size="small">
            {{ { RECEIVED: '已收货', INVOICED: '已开票', CLOSED: '已关闭' }[detailRow.status] || detailRow.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="采购订单">{{ detailRow.poNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailRow.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="收货日期">{{ detailRow.receiptDate }}</el-descriptions-item>
        <el-descriptions-item label="收货金额">{{ fmt(detailRow.amount) }}</el-descriptions-item>
        <el-descriptions-item label="累计开票">{{ fmt(detailRow.invoiceAmount) }}</el-descriptions-item>
        <!-- R5-D-070：详情与列表同口径——负值红字并示"超额 xx" -->
        <el-descriptions-item label="未开票">
          <span v-if="unInvoicedOf(detailRow) < 0" style="color:#f56c6c;font-weight:600">超额 {{ fmt(-unInvoicedOf(detailRow)) }}</span>
          <span v-else>{{ fmt(unInvoicedOf(detailRow)) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="收货人">{{ detailRow.receiverName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="收货仓库">{{ detailRow.warehouse || '—' }}</el-descriptions-item>
        <el-descriptions-item label="质检状态">
          <el-tag :type="qualityTag(detailRow.qualityStatus)" size="small">{{ qualityLabel(detailRow.qualityStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="送货单号">{{ detailRow.deliveryNote || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailRow.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="dlgDetail = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
<!-- D-DEC-334：声明单据域 PO_RECEIPT——不声明则收货主键会被后端按 exp_bill.id 解释（D-TRC-04 跨域串号）。
     后端已配无审批流配对锚点（BillRouteRegistry.isNoFlowVoucherDomain⇒pairOnlyChain 直读凭证段），行内「追溯」入口已开。 -->
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" biz-type="PO_RECEIPT" />
</template>

<script setup>

const { rules: formRules } = useFormRules('PO_RECEIPT')
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { purchaseApi, supplierApi } from '../../api'
import { hasPerm } from '../../utils/perm'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'
import { BillStatus, STATUS_LABEL, STATUS_TYPE, statusLabel, statusType } from '@/constants/billStatus.js'
import { useAutoFill } from '@/composables/useAutoFill'
import { useFormRules } from '@/composables/useFormRules'
import { useSubmitGuard } from '@/composables/useSubmitGuard'
import { useKeyboard } from '@/composables/useKeyboard'
import { formatDate, formatDateTime } from '@/utils/format'
// R5-13: 快捷键
useKeyboard({
  'ctrl+s': () => { try { save() } catch { /* noop */ } },
  'ctrl+r': () => { try { loadData() } catch { /* noop */ } },
})

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

// 双击行：调用已有详情查看函数
function openRowDetail(row) {
  viewDetail(row)
}

const selRows = ref([])
const rows = ref([])
/** R5-D-072：列表权限预判（与后端 @RequiresPerm("po:receipt:list") 同码）——无权不打接口、渲染无权限态 */
const canList = hasPerm('po:receipt:list')
const total = ref(0)
const loading = ref(false)
const suppliers = ref([])
const pos = ref([])
const dlg = ref(false)
const dlgDetail = ref(false)
const detailRow = ref(null)
const q = reactive({ pageNum: 1, pageSize: 20, keyword: '', status: '' })
const form = reactive({})
const formRef = ref(null)
const { submit: guardSubmit } = useSubmitGuard(formRef)
const { autofill, saveFormHistory } = useAutoFill()

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
// R5-D-070：未开票口径＝收货额 − 累计开票；为负即累计开票超收货额（后端已设硬闸拒绝回写，历史/异常数据仍须显性提示）
function unInvoicedOf(row) { return Number(row.amount || 0) - Number(row.invoiceAmount || 0) }
function qualityTag(s) { return s === 'PASSED' ? 'success' : s === 'FAILED' ? 'danger' : 'warning' }
function qualityLabel(s) { return ({ PENDING: '待检', PASSED: '合格', FAILED: '不合格' })[s] || '待检' }

// 本地时区当天（原 toISOString 按 UTC 取日期，东八区 0~8 点会落到"昨天"）
function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
// 收货日期统一归一为后端 LocalDate 可解析的 'YYYY-MM-DD' 字符串
//（日期选择器 value-format 已是字符串，但清空得 null、历史值可能是 Date/ISO，统一收口避免反序列化失败）
function normalizeDate(v) {
  if (v instanceof Date && !isNaN(v.getTime())) {
    return `${v.getFullYear()}-${String(v.getMonth() + 1).padStart(2, '0')}-${String(v.getDate()).padStart(2, '0')}`
  }
  const s = String(v ?? '').trim().slice(0, 10)
  return /^\d{4}-\d{2}-\d{2}$/.test(s) ? s : todayStr()
}

async function load() {
  if (!canList) return // R5-D-072：无权不请求，避免 4030 被吞成空表
  loading.value = true
  try {
    const r = await purchaseApi.receiptPage({ ...q })
    rows.value = r.list || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error(e.message) } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, {
    receiptNo: '', poId: null, supplierId: null, supplierName: '',
    receiptDate: todayStr(), amount: 0,
    receiverName: '', warehouse: '', qualityStatus: 'PENDING', deliveryNote: '',
    remark: '',
  })
  dlg.value = true
}

function viewDetail(row) {
  detailRow.value = row
  dlgDetail.value = true
}

async function onPoChange(poId) {
  const po = pos.value.find((x) => x.id === poId)
  if (po) {
    // 历史订单可能只有 supplierName 没有 supplierId：按名称回填，避免供应商下拉被锁成空值
    const matched = po.supplierId == null
      ? suppliers.value.find((s) => s.supplierName === po.supplierName)
      : suppliers.value.find((s) => s.id === po.supplierId)
    form.supplierId = po.supplierId ?? matched?.id ?? null
    form.supplierName = po.supplierName ?? matched?.supplierName ?? ''
    if (!form.receiptNo) form.receiptNo = 'GRN' + po.poNo.replace(/[^0-9]/g, '').slice(-6)
  }
}

async function save() {
  saveFormHistory('PO_RECEIPT', form.value || form)

  await guardSubmit(async () => {
    // 必填闸门已保证 supplierId/收货日期/金额>0；收货单号后端支持留空自动取号（PURCHASE_RECEIPT 规则），
    // 故不列入必填。
    const supplierName = String(form.supplierName || '').trim()
    const amount = Number(form.amount)

    // 显式按 PoReceipt 实体组装提交数据（不再整包 spread 表单——autofill 会往 form 里塞
    // applicantId/currency/billDate 等非实体字段，历史上还可能残留 id/status 等脏键）；
    // 数量/金额统一 Number 化避免字符串化，日期归一 'YYYY-MM-DD'。
    const po = form.poId != null ? pos.value.find((x) => x.id === form.poId) : null
    const payload = {
      // 收货单号：留空交由后端取号（nextReceiptNo），保证 uk(tenant_id, receipt_no) 唯一
      receiptNo: String(form.receiptNo || '').trim() || null,
      poId: po ? po.id : null,
      poNo: po ? po.poNo : null,
      supplierId: form.supplierId != null ? Number(form.supplierId) : null,
      supplierName,
      receiptDate: normalizeDate(form.receiptDate),
      amount: Math.round(amount * 100) / 100,
      receiverName: String(form.receiverName || '').trim(),
      warehouse: form.warehouse || '',
      qualityStatus: form.qualityStatus || 'PENDING',
      deliveryNote: String(form.deliveryNote || '').trim(),
      remark: form.remark || '',
    }
    try {
      await purchaseApi.receiptCreate(payload)
      ElMessage.success('已保存（收货超单会自动拦截）')
      dlg.value = false
      load()
    } catch (e) { ElMessage.error(e?.message || '收货单保存失败') }
  })
}

onMounted(async () => {
  autofill(form, 'PO_RECEIPT')
  try {
    const [s, p] = await Promise.all([
      supplierApi.page({ pageNum: 1, pageSize: 100 }),
      purchaseApi.orderPage({ pageNum: 1, pageSize: 100, receivable: true }),
    ])
    suppliers.value = s.list || []
    pos.value = p.list || []
  } catch (e) { /* 失败已由 api.js 拦截器全局弹错（），此处仅兜底防止未捕获拒绝；下拉为空时用户可感知 */ }
  load()
})

function exportCsv() {
  if (!rows.value.length) { ElMessage.warning('当前无数据可导出'); return }
  const cols = [{ prop: 'receiptNo', label: '收货单号' }, { prop: 'poNo', label: '采购订单' }, { prop: 'supplierName', label: '供应商' }, { prop: 'receiptDate', label: '收货日期' }, { prop: 'receiverName', label: '收货人' }, { prop: 'warehouse', label: '仓库' }, { prop: 'deliveryNote', label: '送货单号' }]
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
  a.download = `PoReceipt_${new Date().toISOString().slice(0, 10)}.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}


function handleSelectionChange(val) { selRows.value = val }

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
  a.download = `PoReceipt_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click(); URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.value.length} 条记录`)
}

</script>
