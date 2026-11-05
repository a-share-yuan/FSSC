<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->
<template>
  <el-card>
    <template #header>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <b>税金计提规则</b>
        <span style="color: #909399; font-size: 12px">
          税种／计税基数来源／税率／借贷科目全在这里配，换客户不改码（表空时按内置三条兜底）
        </span>
        <el-button type="primary" size="small" style="margin-left: auto" @click="openNew">新增规则</el-button>
      </div>
    </template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
      title="基数来源：MANUAL 手工录入（计提时可在计提页改数）；DECLARATION 按同期已申报数汇总；INVOICE 按同期已认证进项税汇总。汇总为 0 的税种在计提时自动跳过。" />

    <SkeletonList v-if="loading" :rows="6" :cols="6" />
    <el-table v-else :data="rows" size="small" :empty-text="'暂无规则（计提将使用内置默认三条）'">
      <el-table-column prop="taxType" label="税种" width="110" />
      <el-table-column prop="ruleName" label="规则名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="基数来源" width="120">
        <template #default="{ row }">{{ sourceName(row.baseSource) }}</template>
      </el-table-column>
      <el-table-column label="缺省基数" width="120" align="right">
        <template #default="{ row }">{{ row.baseSource === 'MANUAL' ? fmt(row.baseAmount) : '—' }}</template>
      </el-table-column>
      <el-table-column label="税率" width="90" align="right">
        <template #default="{ row }">{{ row.taxRate }}%</template>
      </el-table-column>
      <el-table-column label="计提分录" min-width="220">
        <template #default="{ row }">
          借 {{ row.debitSubjectCode || '—' }} / 贷 {{ row.creditSubjectCode || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="申报/缴纳" min-width="200">
        <template #default="{ row }">
          申报贷 {{ row.declareCreditSubjectCode || '2221' }} · 缴纳借 {{ row.payDebitSubjectCode || '2221' }}
        </template>
      </el-table-column>
      <el-table-column label="纳税周期" width="110">
        <template #default="{ row }">{{ periodName(row.periodType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'NORMAL' ? 'success' : 'info'" size="small">
            {{ row.status === 'NORMAL' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑计提规则' : '新增计提规则'" width="560px">
      <el-form ref="formRef" :rules="rules" :model="dlg.form" label-width="130px">
        <el-form-item label="税种" prop="taxType">
          <el-select v-model="dlg.form.taxType" filterable allow-create default-first-option style="width: 100%">
            <el-option v-for="t in TAX_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="dlg.form.ruleName" maxlength="100" />
        </el-form-item>
        <el-form-item label="基数来源" prop="baseSource">
          <el-select v-model="dlg.form.baseSource" style="width: 100%">
            <el-option label="手工录入" value="MANUAL" />
            <el-option label="按已申报数汇总" value="DECLARATION" />
            <el-option label="按已认证进项税汇总" value="INVOICE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dlg.form.baseSource === 'MANUAL'" label="缺省计税基数" prop="baseAmount">
          <el-input-number v-model="dlg.form.baseAmount" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税率(%)" prop="taxRate">
          <el-input-number v-model="dlg.form.taxRate" :precision="2" :min="0.01" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计提借方科目">
          <el-input v-model="dlg.form.debitSubjectCode" placeholder="如 6403 税金及附加 / 6801 所得税费用" />
        </el-form-item>
        <el-form-item label="计提贷方科目">
          <el-input v-model="dlg.form.creditSubjectCode" placeholder="应交税费明细，如 22210101" />
        </el-form-item>
        <el-form-item label="申报贷方科目">
          <el-input v-model="dlg.form.declareCreditSubjectCode" placeholder="未交税金，缺省 2221" />
        </el-form-item>
        <el-form-item label="缴纳借方科目">
          <el-input v-model="dlg.form.payDebitSubjectCode" placeholder="缴税付款凭证借方，缺省 2221" />
        </el-form-item>
        <el-form-item label="纳税周期">
          <el-select v-model="dlg.form.periodType" style="width: 100%">
            <el-option v-for="p in PERIODS" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="申报/缴款截止日">
          <div style="display: flex; gap: 8px">
            <el-input-number v-model="dlg.form.declareDeadlineDay" :min="1" :max="31" controls-position="right" style="width: 120px" />
            <el-input-number v-model="dlg.form.payDeadlineDay" :min="1" :max="31" controls-position="right" style="width: 120px" />
            <span style="color: #909399; font-size: 12px; line-height: 32px">月内第几日</span>
          </div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="dlg.form.status" active-value="NORMAL" inactive-value="DISABLED" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dlg.form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { glexApi } from '../../api'
import SkeletonList from '../../components/SkeletonList.vue'

const TAX_TYPES = [
  { label: '增值税 VAT', value: 'VAT' },
  { label: '附加税 SURCHARGE', value: 'SURCHARGE' },
  { label: '企业所得税 INCOME/CIT', value: 'INCOME' },
  { label: '个人所得税 IIT', value: 'IIT' },
  { label: '印花税 STAMP', value: 'STAMP' },
  { label: '房产税 PROPERTY', value: 'PROPERTY' },
  { label: '土地使用税 LAND', value: 'LAND' },
  { label: '车船税 VEHICLE', value: 'VEHICLE' },
]
const PERIODS = [
  { label: '按月 MONTHLY', value: 'MONTHLY' },
  { label: '按季 QUARTERLY', value: 'QUARTERLY' },
  { label: '按半年 HALF_YEARLY', value: 'HALF_YEARLY' },
  { label: '按年 ANNUAL', value: 'ANNUAL' },
  { label: '按次 PER_TIME', value: 'PER_TIME' },
]

const rows = ref([])
const loading = ref(false)
const formRef = ref(null)
const dlg = reactive({ visible: false, saving: false, form: {} })

// 必填规则只写 required + message：带上 trigger 会退化成 string 校验器，
// 数字/布尔类型的正常取值会被判为非法（见 constants/requiredRules.spec.js）
const rules = {
  taxType: [{ required: true, message: '请选择税种' }],
  ruleName: [{ required: true, message: '请填写规则名称' }],
  baseSource: [{ required: true, message: '请选择基数来源' }],
  taxRate: [{ required: true, message: '请填写税率' }],
  baseAmount: [{ required: true, message: '手工基数来源必须填缺省基数' }],
}

function fmt(v) {
  if (v === null || v === undefined || v === '') return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function sourceName(v) {
  return { MANUAL: '手工录入', DECLARATION: '按申报数', INVOICE: '按进项税' }[v] || v || '—'
}
function periodName(v) {
  return { MONTHLY: '按月', QUARTERLY: '按季', HALF_YEARLY: '按半年', ANNUAL: '按年', PER_TIME: '按次' }[v] || v || '—'
}

async function load() {
  loading.value = true
  try {
    rows.value = (await glexApi.taxRuleList()) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openNew() {
  dlg.form = {
    taxType: null, ruleName: '', baseSource: 'MANUAL', baseAmount: 0, taxRate: 13,
    debitSubjectCode: '6403', creditSubjectCode: '2221', declareCreditSubjectCode: '2221',
    payDebitSubjectCode: '2221', periodType: 'MONTHLY', declareDeadlineDay: 15, payDeadlineDay: 15,
    status: 'NORMAL', remark: '',
  }
  dlg.visible = true
}
function openEdit(row) {
  dlg.form = { ...row }
  dlg.visible = true
}

async function save() {
  if (!formRef.value) return
  await formRef.value.validate(async (ok) => {
    if (!ok) return
    dlg.saving = true
    try {
      if (dlg.form.id) await glexApi.taxRuleUpdate(dlg.form.id, dlg.form)
      else await glexApi.taxRuleCreate(dlg.form)
      ElMessage.success('已保存')
      dlg.visible = false
      load()
    } catch (e) {
      ElMessage.error(e.message)
    } finally {
      dlg.saving = false
    }
  })
}

async function remove(row) {
  await ElMessageBox.confirm(`删除税种 ${row.taxType} 的计提规则？删除后该税种不再参与计提。`, '确认', { type: 'warning' })
  try {
    await glexApi.taxRuleDelete(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>
