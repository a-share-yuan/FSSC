<template>
  <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>{{ $t('pay.tolerance.pageTitle') }}</b>
        <span style="color:#909399; font-size:12px">{{ $t('pay.tolerance.hintByDim') }}</span>
        <span style="margin-left:auto; display:flex; gap:8px">
          <el-button type="primary" size="small" @click="reload">{{ $t('common.refresh') }}</el-button>
      <el-button size="small" @click="exportCsv(rows, CSV_COLS, 'MatchTolerance')">{{ $t('pay.shared.btnExportCsv') }}</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="exportBatchCsv(selRows, 'MatchTolerance')">{{ $t('pay.shared.btnExportSelected', { v1: selRows.length }) }}</el-button>
        </span>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" size="small" border stripe v-else @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" :empty-text="$t('common.empty')">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="dimensionCode" :label="$t('pay.tolerance.colDimension')" width="220" sortable>
        <template #default="{ row }">{{ dimText(row.dimensionCode) }}</template>
      </el-table-column>
      <el-table-column :label="$t('pay.tolerance.colToleranceType')" width="160">
        <template #default="{ row }">
          <el-select v-model="dim(row).toleranceType" :placeholder="$t('field.type')" size="small">
            <el-option :label="$t('pay.tolerance.optPercent')" value="PERCENT" />
            <el-option :label="$t('pay.tolerance.optFixedAmount')" value="FIXED" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column :label="$t('pay.tolerance.colToleranceValue')" width="180">
        <template #default="{ row }">
          <el-input-number v-model="dim(row).toleranceValue" :min="0" :precision="4" :step="0.5" size="small" style="width:150px" />
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.remark')" min-width="150">
        <template #default="{ row }">
          <el-input v-model="dim(row).remark" :placeholder="$t('pay.tolerance.phRemarkCurrent')" size="small" :rows="1" />
        </template>
      </el-table-column>
      <el-table-column :label="$t('pay.tolerance.colEffectiveFrom')" width="150">
        <template #default="{ row }">
          <el-date-picker v-model="dim(row).effectDate" type="date" value-format="YYYY-MM-DD" size="small" :placeholder="$t('pay.tolerance.phEffectiveNow')" style="width:130px" />
        </template>
      </el-table-column>
      <el-table-column :label="$t('pay.tolerance.colEffectiveTo')" width="150">
        <template #default="{ row }">
          <el-date-picker v-model="dim(row).expireDate" type="date" value-format="YYYY-MM-DD" size="small" :placeholder="$t('pay.tolerance.phPermanent')" style="width:130px" />
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.operation')" width="100" align="center">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="save(row)">{{ $t('common.save') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 12px; justify-content: flex-end;"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />

    <!-- 41号审查 P2-13:差异化容差(按组织/供应商) -->
    <div class="scoped-head">
      <b>{{ $t('pay.tolerance.secScoped') }}</b>
      <el-button size="small" type="primary" plain @click="scopedVisible = true">{{ $t('pay.tolerance.btnAddScoped') }}</el-button>
    </div>
    <el-table :data="scopedRows" size="small" border :empty-text="$t('pay.tolerance.hintNoScoped')">
      <el-table-column :label="$t('pay.tolerance.colDimension')" min-width="180">
        <template #default="{ row }">{{ dimText(row.dimensionCode) }}</template>
      </el-table-column>
      <el-table-column prop="companyId" :label="$t('pay.tolerance.colCompanyId')" width="150" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ row.companyId ? companyName(row.companyId) : t('common.all') }}</template>
      </el-table-column>
      <el-table-column prop="supplierId" :label="$t('pay.tolerance.colSupplierId')" width="150" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ row.supplierId ? supplierName(row.supplierId) : t('common.all') }}</template>
      </el-table-column>
      <el-table-column :label="$t('pay.tolerance.colTolerance')" width="110" align="right">
        <template #default="{ row }">{{ row.toleranceValue }}{{ row.toleranceType === 'PERCENT' ? '%' : t('pay.shared.unitYuan') }}</template>
      </el-table-column>
      <el-table-column :label="$t('pay.tolerance.colEffectivePeriod')" min-width="180">
        <template #default="{ row }">{{ (row.effectDate || t('pay.tolerance.phEffectiveNow')) }} ~ {{ (row.expireDate || t('pay.tolerance.phPermanent')) }}</template>
      </el-table-column>
      <el-table-column prop="remark" :label="$t('common.remark')" min-width="120" show-overflow-tooltip />
      <el-table-column :label="$t('common.operation')" width="70" align="center">
        <template #default="{ row }">
          <el-button link size="small" type="danger" @click="delScoped(row)">{{ $t('pay.tolerance.btnDeleteScoped') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="scopedVisible" :title="$t('pay.tolerance.ctxAddScopedTitle')" width="480px" destroy-on-close>
      <el-form ref="scopedFormRef" :model="scopedForm" :rules="scopedRules" label-width="100px" size="small">
        <el-form-item :label="$t('pay.tolerance.colDimension')" prop="dimensionCode">
          <el-select v-model="scopedForm.dimensionCode" style="width:100%">
            <el-option v-for="(v, k) in DIMS" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <!-- 公司与供应商从档案选取：容差行的 companyId/supplierId 是外键，手输数字对不上档案就永远不生效 -->
        <el-form-item :label="$t('pay.tolerance.colCompanyId')">
          <el-select v-model="scopedForm.companyId" filterable clearable style="width:100%" :placeholder="$t('pay.tolerance.phAllOrgs')">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('pay.tolerance.colSupplierId')">
          <SupplierSelect v-model="scopedForm.supplierId" value-key="id" clearable :placeholder="$t('pay.tolerance.phAllSuppliers')" />
        </el-form-item>
        <el-form-item :label="$t('pay.tolerance.colToleranceType')" prop="toleranceType">
          <el-select v-model="scopedForm.toleranceType" style="width:100%">
            <el-option :label="$t('pay.tolerance.optPercent')" value="PERCENT" />
            <el-option :label="$t('pay.tolerance.optFixedAmount')" value="FIXED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('pay.tolerance.colToleranceValue')" prop="toleranceValue"><el-input-number v-model="scopedForm.toleranceValue" :min="0" :precision="4" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('pay.tolerance.colEffectiveFrom')"><el-date-picker v-model="scopedForm.effectDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('pay.tolerance.colEffectiveTo')"><el-date-picker v-model="scopedForm.expireDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('common.remark')"><el-input v-model="scopedForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="scopedVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" size="small" @click="saveScoped">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-alert style="margin-top:12px" type="info" :closable="false"
      :title="$t('pay.tolerance.hintSemantics')" />
  </el-card>
</template>

<script setup>
import {onMounted, reactive, ref, computed} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showRowDetail } from '@/utils/format'
import { mdApi, orgApi, p1Api } from '../../api'
import { useFormRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import { exportCsv, exportBatchCsv } from '../../utils/csv'
import SkeletonList from '../../components/SkeletonList.vue'
import SupplierSelect from '@/components/common/SupplierSelect.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// 容差差异化行的公司/供应商列按档案回显名称（存的是外键 id，直显数字无人看得懂）
const companies = ref([])
const suppliers = ref([])
function companyName(id) { return companies.value.find((c) => c.id === Number(id))?.name || `#${id}` }
function supplierName(id) { return suppliers.value.find((s) => s.id === Number(id))?.supplierName || `#${id}` }
async function loadOptions() {
  try {
    const flat = []
    const walk = (arr) => { (arr || []).forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
    walk(await orgApi.companyTree())
    companies.value = flat
  } catch { companies.value = [] }
  try { suppliers.value = (await mdApi.suppliers({ pageNum: 1, pageSize: 500 }))?.list || [] } catch { suppliers.value = [] }
}

const selRows = ref([])
const rows = ref([])
const scopedRows = ref([])
const loading = ref(false)
const edit = ref({})
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const CSV_COLS = computed(() => ([{ prop: 'dimensionCode', label: t('pay.tolerance.colDimension') }]))

const DIMS = computed(() => ({
  PAY_VS_INVOICE: t('pay.tolerance.dimPayVsInvoice'),
  INVOICE_VS_RECEIPT: t('pay.tolerance.dimInvoiceVsReceipt'),
  RECEIPT_VS_PO: t('pay.tolerance.dimReceiptVsPo'),
}))
const dimText = (k) => DIMS.value[k] || k

/** 返回可赋值的编辑对象（不存在则初始化），供 v-model 直接绑定 */
function dim(row) {
  const k = row.dimensionCode
  if (!edit.value[k]) {
    edit.value[k] = { toleranceType: 'PERCENT', toleranceValue: 0, remark: '' }
  }
  return edit.value[k]
}

async function reload() {
  loading.value = true
  try {
    const r = await p1Api.toleranceList({ pageNum: pageNum.value, pageSize: pageSize.value })
    // 修复：后端返回原始数组(非 PageResult)，统一归一化
    const list = Array.isArray(r) ? r : (r?.list || r?.records || [])
    total.value = r?.total ?? r?.totalCount ?? list.length
    rows.value = list
    // 预填当前值到编辑副本（无配置行默认 PERCENT/0）
    const keep = edit.value
    edit.value = {}
    // 41号审查 P2-13:通用行(无公司/供应商维度)进主表,差异化行单列展示
    rows.value = list.filter((r) => !r.companyId && !r.supplierId)
    for (const r of rows.value) {
      edit.value[r.dimensionCode] = {
        toleranceType: r.toleranceType || 'PERCENT',
        toleranceValue: r.toleranceValue == null ? 0 : Number(r.toleranceValue),
        remark: r.remark || '',
        effectDate: r.effectDate || null,
        expireDate: r.expireDate || null,
      }
    }
    scopedRows.value = list.filter((r) => r.companyId || r.supplierId)
    // 保留尚未保存行的手改
    for (const k of Object.keys(keep)) if (!edit.value[k]) edit.value[k] = keep[k]
  } finally { loading.value = false }
}

async function save(row) {
  const e = edit.value[row.dimensionCode]
  if (!e) return
  try {
    await p1Api.toleranceUpsert({
      dimensionCode: row.dimensionCode,
      toleranceType: e.toleranceType,
      toleranceValue: Number(e.toleranceValue),
      remark: e.remark,
      effectDate: e.effectDate || null,
      expireDate: e.expireDate || null,
    })
    ElMessage.success(t('pay.tolerance.msgSaved') + row.dimensionCode)
    reload()
  } catch (err) { ElMessage.error(err.message || String(err)) }
}

function handleSizeChange(val) {
  pageSize.value = val
  pageNum.value = 1
  reload()
}

function handleCurrentChange(val) {
  pageNum.value = val
  reload()
}

onMounted(() => { reload(); loadOptions() })

function openRowDetail(row) {
  return showRowDetail(row, { title: t('field.detail') })
}

function handleSelectionChange(val) { selRows.value = val }

// ---- 41号审查 P2-13 差异化容差 CRUD ----
const scopedVisible = ref(false)
const scopedForm = reactive({ dimensionCode: '', companyId: null, supplierId: null, toleranceType: 'PERCENT', toleranceValue: 0, effectDate: null, expireDate: null, remark: '' })
// 校验规则唯一口径＝中央登记表（useFormRules）；页面不再本地手写规则对象
const scopedFormRef = ref(null)
const { rules: scopedRules } = useFormRules('PAY_TOL_SCOPE')
const { submit: guardScoped } = useSubmitGuard(scopedFormRef)

async function saveScoped() {
  await guardScoped(async () => {
    // 跨字段规则（不是单字段必填）：公司与供应商都为空＝通用配置，与页面上的通用行语义不同，故要求至少填一项
    if (!scopedForm.companyId && !scopedForm.supplierId) { ElMessage.warning(t('pay.tolerance.errNeedScope')); return }
    try {
      await p1Api.toleranceUpsert({
        dimensionCode: scopedForm.dimensionCode,
        companyId: scopedForm.companyId || null,
        supplierId: scopedForm.supplierId || null,
        toleranceType: scopedForm.toleranceType,
        toleranceValue: Number(scopedForm.toleranceValue),
        effectDate: scopedForm.effectDate || null,
        expireDate: scopedForm.expireDate || null,
        remark: scopedForm.remark || null,
      })
      ElMessage.success(t('pay.tolerance.msgScopedSaved'))
      scopedVisible.value = false
      reload()
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function delScoped(row) {
  await ElMessageBox.confirm(t('pay.tolerance.confirmDeleteScoped'), t('pay.tolerance.btnDeleteScoped'), { type: 'warning' })
  try {
    await p1Api.toleranceDelete(row.id)
    ElMessage.success(t('pay.tolerance.msgDeleted'))
    reload()
  } catch (e) { ElMessage.error(e.message) }
}

</script>

<style scoped>
.scoped-head { display: flex; justify-content: space-between; align-items: center; margin: 14px 0 8px; }
</style>