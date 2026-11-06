<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->
<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>纳税申报台账</b>
        <el-select v-model="f.taxType" placeholder="税种" clearable style="width: 140px" @change="load(1)">
          <el-option v-for="t in TAX_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-date-picker v-model="f.period" type="month" placeholder="申报属期" value-format="YYYYMM"
          clearable style="width: 140px" @change="load(1)" />
        <el-button size="small" @click="load(1)">查询</el-button>
        <el-button v-if="can('gl:taxdecl:create')" type="primary" size="small" style="margin-left: auto"
          @click="openNew">新建申报</el-button>
      </div>
    </template>

    <SkeletonList v-if="loading" :rows="6" :cols="6" />
    <el-table v-else :data="rows" size="small" empty-text="暂无申报记录">
      <el-table-column prop="declarationNo" label="申报单号" width="170" />
      <el-table-column label="税种" width="110">
        <template #default="{ row }">{{ taxName(row.taxType) }}</template>
      </el-table-column>
      <el-table-column prop="period" label="属期" width="90" />
      <el-table-column label="计税基数" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.baseAmount) }}</template>
      </el-table-column>
      <el-table-column label="税率" width="80" align="right">
        <template #default="{ row }">{{ row.taxRate == null ? '—' : row.taxRate + '%' }}</template>
      </el-table-column>
      <el-table-column label="应纳税额" width="130" align="right">
        <template #default="{ row }"><b>{{ fmt(row.taxAmount) }}</b></template>
      </el-table-column>
      <el-table-column label="已缴/应补" width="150" align="right">
        <template #default="{ row }">{{ fmt(row.paidAmount) }} / {{ fmt(row.balanceAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusName(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="declareDate" label="申报日期" width="110" />
      <el-table-column label="应缴凭证" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.sourceVoucherId" type="success" size="small">已出证</el-tag>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT' && can('gl:taxdecl:submit')" link type="primary" size="small"
            @click="act(row, 'submit')">提交</el-button>
          <el-button v-if="row.status === 'PENDING' && can('gl:taxdecl:approve')" link type="primary" size="small"
            @click="act(row, 'approve')">审批通过</el-button>
          <el-button v-if="row.status === 'APPROVED' && can('gl:taxdecl:file')" link type="primary" size="small"
            @click="act(row, 'file')">确认申报</el-button>
          <el-button v-if="row.status === 'FILED' && can('gl:taxdecl:pay')" link type="primary" size="small"
            @click="openPay(row)">确认缴纳</el-button>
          <el-button v-if="(row.status === 'DRAFT' || row.status === 'PENDING') && can('gl:taxdecl:create')"
            link type="danger" size="small" @click="act(row, 'cancel')">作废</el-button>
          <el-button v-if="(row.status === 'FILED' || row.status === 'PAID') && can('gl:taxdecl:file')"
            link type="warning" size="small" @click="genVoucher(row)">补出凭证</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px; justify-content: flex-end" layout="total, prev, pager, next"
      :total="total" :page-size="f.pageSize" :current-page="f.pageNum" @current-change="load" />

    <el-dialog v-model="dlg.visible" title="新建纳税申报" width="520px">
      <el-form ref="formRef" :rules="formRules" :model="dlg.form" label-width="110px">
        <el-form-item label="税种" prop="taxType">
          <el-select v-model="dlg.form.taxType" filterable allow-create style="width: 100%">
            <el-option v-for="t in TAX_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="申报属期" prop="period">
          <el-date-picker v-model="dlg.form.period" type="month" value-format="YYYYMM" style="width: 100%" />
        </el-form-item>
        <el-form-item label="申报类型" prop="declareType">
          <el-select v-model="dlg.form.declareType" style="width: 100%">
            <el-option label="按月" value="MONTHLY" />
            <el-option label="按季" value="QUARTERLY" />
            <el-option label="按年" value="ANNUAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="计税基数">
          <el-input-number v-model="dlg.form.baseAmount" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税率(%)">
          <el-input-number v-model="dlg.form.taxRate" :precision="2" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="应纳税额">
          <span style="font-weight: 600">￥{{ fmt(calcTax) }}</span>
          <span style="margin-left: 8px; color: #909399; font-size: 12px">基数 × 税率，保存时由服务端复核</span>
        </el-form-item>
        <el-form-item label="缴款截止日">
          <el-date-picker v-model="dlg.form.payDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dlg.form.remark" type="textarea" :rows="2" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="create">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pay.visible" title="确认缴纳" width="420px">
      <el-form label-width="110px">
        <el-form-item label="应纳税额">￥{{ fmt(pay.row?.taxAmount) }}</el-form-item>
        <el-form-item label="实缴金额">
          <el-input-number v-model="pay.amount" :precision="2" :min="0" style="width: 100%" />
          <!-- 差异实时可见；登记前还会二次确认（差异缴纳要有据可查） -->
          <div v-if="payDiff !== 0" class="pay-diff">
            与应纳税额差异 {{ payDiff > 0 ? '+' : '' }}{{ fmt(payDiff) }}
          </div>
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon
        title="本操作只翻申报状态。真正的缴税付款请走对公付款单（付款单上填「缴纳税种／税期」），付款批次执行后才出「借 应交税费／贷 银行存款」。" />
      <template #footer>
        <el-button @click="pay.visible = false">取消</el-button>
        <el-button type="primary" :loading="pay.saving" @click="doPay">确认</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taxApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'
import { hasPerm } from '../../utils/perm'

const TAX_TYPES = [
  { label: '增值税', value: 'VAT' }, { label: '企业所得税', value: 'CIT' },
  { label: '个人所得税', value: 'IIT' }, { label: '印花税', value: 'STAMP' },
  { label: '房产税', value: 'PROPERTY' }, { label: '土地使用税', value: 'LAND' },
  { label: '车船税', value: 'VEHICLE' }, { label: '附加税', value: 'SURCHARGE' },
]

const f = reactive({ taxType: null, period: null, pageNum: 1, pageSize: 20 })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const formRef = ref(null)
const dlg = reactive({ visible: false, saving: false, form: {} })
const pay = reactive({ visible: false, saving: false, row: null, amount: 0 })
/** 实缴 − 应纳；非 0 即为差异缴纳，登记前要二次确认 */
const payDiff = computed(() => Number((Number(pay.amount || 0) - Number(pay.row?.taxAmount || 0)).toFixed(2)))

// 必填规则只写 required + message：带上 trigger 会退化成 string 校验器，
// 数字/布尔类型的正常取值会被判为非法（见 constants/requiredRules.spec.js）
const formRules = {
  taxType: [{ required: true, message: '请选择税种' }],
  period: [{ required: true, message: '请选择申报属期' }],
  declareType: [{ required: true, message: '请选择申报类型' }],
}

function can(code) { return hasPerm(code) }
function fmt(v) {
  if (v === null || v === undefined || v === '') return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function taxName(v) {
  const hit = TAX_TYPES.find((t) => t.value === v)
  return hit ? hit.label : v
}
function statusName(s) {
  return { DRAFT: '草稿', PENDING: '待审批', APPROVED: '已审批', FILED: '已申报', PAID: '已缴纳', CANCELLED: '已作废' }[s] || s
}
function statusTag(s) {
  return { DRAFT: 'info', PENDING: 'warning', APPROVED: '', FILED: 'success', PAID: 'success', CANCELLED: 'danger' }[s] || 'info'
}
const calcTax = computed(() => {
  const b = Number(dlg.form.baseAmount || 0)
  const r = Number(dlg.form.taxRate || 0)
  return Math.round(b * r) / 100
})

async function load(page) {
  if (page) f.pageNum = page
  loading.value = true
  try {
    const r = await taxApi.declarationPage({ ...f })
    rows.value = r?.list || r?.records || []
    total.value = r?.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openNew() {
  dlg.form = { taxType: 'VAT', period: null, declareType: 'MONTHLY', baseAmount: 0, taxRate: 13, payDeadline: null, remark: '' }
  dlg.visible = true
}

async function create() {
  if (!formRef.value) return
  await formRef.value.validate(async (ok) => {
    if (!ok) return
    dlg.saving = true
    try {
      await taxApi.declarationCreate(dlg.form)
      ElMessage.success('已保存')
      dlg.visible = false
      load(1)
    } catch (e) {
      ElMessage.error(e.message)
    } finally {
      dlg.saving = false
    }
  })
}

async function act(row, kind) {
  const label = { submit: '提交', approve: '审批通过', file: '确认申报并出应缴凭证', cancel: '作废' }[kind]
  await ElMessageBox.confirm(`对申报单 ${row.declarationNo} 执行「${label}」？`, '确认', { type: 'warning' })
  try {
    if (kind === 'submit') await taxApi.declarationSubmit(row.id)
    else if (kind === 'approve') await taxApi.declarationApprove(row.id)
    else if (kind === 'cancel') await taxApi.declarationCancel(row.id)
    else await taxApi.declarationFile(row.id)
    ElMessage.success('已执行')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openPay(row) {
  pay.row = row
  pay.amount = Number(row.taxAmount || 0)
  pay.visible = true
}
async function doPay() {
  const paid = Number(pay.amount || 0)
  // 0 元登记会让「已缴纳」状态与实际情况脱节，直接拦下
  if (!(paid > 0)) {
    ElMessage.warning('实缴金额必须大于 0；未实际缴款请勿登记缴纳')
    return
  }
  if (payDiff.value !== 0) {
    try {
      await ElMessageBox.confirm(
        `实缴 ${fmt(paid)} 与应纳 ${fmt(pay.row?.taxAmount)} 不一致（差异 ${payDiff.value > 0 ? '+' : ''}${fmt(payDiff.value)}），确认按实缴金额登记？`,
        '差异缴纳确认',
        { type: 'warning', confirmButtonText: '确认登记', cancelButtonText: '返回修改' },
      )
    } catch { return }
  }
  pay.saving = true
  try {
    await taxApi.declarationPay(pay.row.id, pay.amount)
    ElMessage.success('已登记缴纳')
    pay.visible = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    pay.saving = false
  }
}

async function genVoucher(row) {
  try {
    const id = await taxApi.declarationVoucher(row.id)
    if (id) ElMessage.success('已生成应缴凭证')
    else ElMessage.warning('该税种借贷科目相同（未细分），未生成空转凭证')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => load())
</script>

<style scoped>
/* 差异金额：只在实缴≠应纳时出现，属于会变化的信息，保留常驻显示 */
.pay-diff {
  margin-top: 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-warning);
}
</style>
