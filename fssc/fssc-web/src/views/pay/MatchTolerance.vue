<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-card>
    <template #header>
      <div style="display:flex; gap:16px; align-items:center; flex-wrap:wrap">
        <b>参数化容差引擎</b>
        <span style="color:#909399; font-size:12px">按维度配置容差（百分比 / 固定金额），超容差冻结付款 / WARN 提示</span>
        <span style="margin-left:auto; display:flex; gap:8px">
          <el-button type="primary" size="small" @click="reload">刷新</el-button>
      <el-button size="small" @click="exportCsv(rows, CSV_COLS, 'MatchTolerance')">导出 CSV</el-button>
      <el-button v-if="selRows.length" type="success" plain size="small" @click="exportBatchCsv(selRows, 'MatchTolerance')">导出选中({{ selRows.length }})</el-button>
        </span>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="8" :cols="5" />
    <el-table :data="rows" size="small" border stripe v-else @selection-change="handleSelectionChange" @row-dblclick="openRowDetail" empty-text="暂无数据">
        <el-table-column type="selection" width="45" />
      <el-table-column prop="dimensionCode" label="维度" width="220" sortable>
        <template #default="{ row }">{{ dimText(row.dimensionCode) }}</template>
      </el-table-column>
      <el-table-column label="容差类型" width="160">
        <template #default="{ row }">
          <el-select v-model="dim(row).toleranceType" placeholder="类型" size="small">
            <el-option label="百分比(%)" value="PERCENT" />
            <el-option label="固定金额(元)" value="FIXED" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="容差值" width="180">
        <template #default="{ row }">
          <el-input-number v-model="dim(row).toleranceValue" :min="0" :precision="4" :step="0.5" size="small" style="width:150px" />
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="150">
        <template #default="{ row }">
          <el-input v-model="dim(row).remark" placeholder="说明（当前生效规则）" size="small" :rows="1" />
        </template>
      </el-table-column>
      <el-table-column label="生效日" width="150">
        <template #default="{ row }">
          <el-date-picker v-model="dim(row).effectDate" type="date" value-format="YYYY-MM-DD" size="small" placeholder="立即" style="width:130px" />
        </template>
      </el-table-column>
      <el-table-column label="失效日" width="150">
        <template #default="{ row }">
          <el-date-picker v-model="dim(row).expireDate" type="date" value-format="YYYY-MM-DD" size="small" placeholder="长期" style="width:130px" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="save(row)">保存</el-button>
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
      <b>差异化容差（组织 / 供应商级，未命中时回退上方通用配置）</b>
      <el-button size="small" type="primary" plain @click="scopedVisible = true">新增差异化规则</el-button>
    </div>
    <el-table :data="scopedRows" size="small" border empty-text="暂无差异化配置">
      <el-table-column label="维度" min-width="180">
        <template #default="{ row }">{{ dimText(row.dimensionCode) }}</template>
      </el-table-column>
      <el-table-column prop="companyId" label="公司ID" width="90" align="center">
        <template #default="{ row }">{{ row.companyId || '全部' }}</template>
      </el-table-column>
      <el-table-column prop="supplierId" label="供应商ID" width="90" align="center">
        <template #default="{ row }">{{ row.supplierId || '全部' }}</template>
      </el-table-column>
      <el-table-column label="容差" width="110" align="right">
        <template #default="{ row }">{{ row.toleranceValue }}{{ row.toleranceType === 'PERCENT' ? '%' : ' 元' }}</template>
      </el-table-column>
      <el-table-column label="生效期" min-width="180">
        <template #default="{ row }">{{ (row.effectDate || '立即') }} ~ {{ (row.expireDate || '长期') }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="70" align="center">
        <template #default="{ row }">
          <el-button link size="small" type="danger" @click="delScoped(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="scopedVisible" title="新增差异化容差规则" width="480px" destroy-on-close>
      <el-form ref="scopedFormRef" :model="scopedForm" :rules="scopedRules" label-width="100px" size="small">
        <el-form-item label="维度" prop="dimensionCode">
          <el-select v-model="scopedForm.dimensionCode" style="width:100%">
            <el-option v-for="(v, k) in DIMS" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="公司ID"><el-input-number v-model="scopedForm.companyId" :min="1" style="width:100%" placeholder="空=全部组织" /></el-form-item>
        <el-form-item label="供应商ID"><el-input-number v-model="scopedForm.supplierId" :min="1" style="width:100%" placeholder="空=全部供应商" /></el-form-item>
        <el-form-item label="容差类型" prop="toleranceType">
          <el-select v-model="scopedForm.toleranceType" style="width:100%">
            <el-option label="百分比(%)" value="PERCENT" />
            <el-option label="固定金额(元)" value="FIXED" />
          </el-select>
        </el-form-item>
        <el-form-item label="容差值" prop="toleranceValue"><el-input-number v-model="scopedForm.toleranceValue" :min="0" :precision="4" style="width:100%" /></el-form-item>
        <el-form-item label="生效日"><el-date-picker v-model="scopedForm.effectDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="失效日"><el-date-picker v-model="scopedForm.expireDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="scopedForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="scopedVisible = false">取消</el-button>
        <el-button type="primary" size="small" @click="saveScoped">保存</el-button>
      </template>
    </el-dialog>

    <el-alert style="margin-top:12px" type="info" :closable="false"
      title="容差语义：PAY_VS_INVOICE=付款≤发票(硬约束,默认0即不允许超付)；INVOICE_VS_RECEIPT=发票≤收货(超容差ERROR冻结,容差内WARN)；RECEIPT_VS_PO=收货≤订单(超容差WARN)。回到三单匹配页重新检查即可生效。" />
  </el-card>
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { showRowDetail } from '@/utils/format'
import { p1Api } from '../../api'
import { useFormRules } from '../../composables/useFormRules'
import { useSubmitGuard } from '../../composables/useSubmitGuard'
import { exportCsv, exportBatchCsv } from '../../utils/csv'
import SkeletonList from '../../components/SkeletonList.vue'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'

// R4-02: BillTraceDrawer
const traceVisible = ref(false)
const traceBillId = ref(null)
function openTrace(row) {
  traceBillId.value = row.id || row.billId || row.bill_id
  traceVisible.value = true
}

const selRows = ref([])
const rows = ref([])
const scopedRows = ref([])
const loading = ref(false)
const edit = ref({})
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const CSV_COLS = [{ prop: 'dimensionCode', label: '维度' }]

const DIMS = {
  PAY_VS_INVOICE: '付款≤发票',
  INVOICE_VS_RECEIPT: '发票≤收货',
  RECEIPT_VS_PO: '收货≤订单',
}
const dimText = (k) => DIMS[k] || k

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
    ElMessage.success('容差配置已保存：' + row.dimensionCode)
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

onMounted(reload)

function openRowDetail(row) {
  return showRowDetail(row, { title: '详情' })
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
    if (!scopedForm.companyId && !scopedForm.supplierId) { ElMessage.warning('至少填写公司ID或供应商ID之一(否则属通用配置)'); return }
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
      ElMessage.success('差异化规则已保存')
      scopedVisible.value = false
      reload()
    } catch (e) { ElMessage.error(e.message) }
  })
}

async function delScoped(row) {
  await ElMessageBox.confirm('确认删除该差异化容差规则?', '删除', { type: 'warning' })
  try {
    await p1Api.toleranceDelete(row.id)
    ElMessage.success('已删除')
    reload()
  } catch (e) { ElMessage.error(e.message) }
}

</script>

<style scoped>
.scoped-head { display: flex; justify-content: space-between; align-items: center; margin: 14px 0 8px; }
</style>