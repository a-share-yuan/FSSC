<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <!--
    家族统一修复件：候选单据选择器。
    取代「@row-click 点哪行就把哪行全额推入表单」的模式——
    误点即产生财务动作、无确认、金额写死全额、键盘不可达、无可发现性提示。
    新形态：勾选生效 + 每行金额可改（上限=可冲抵余额，支持部分冲抵）+ 底部【确定】统一提交。
  -->
  <el-dialog :model-value="modelValue" @update:modelValue="onVisibleChange"
             :title="title" width="900px" @closed="resetState">
    <el-alert v-if="tip" type="info" :closable="false" :title="tip" style="margin-bottom: 8px" />
    <slot name="toolbar" />
    <el-table :data="rows" size="small" v-loading="loading" row-key="id"
              @selection-change="onSelectionChange" :empty-text="emptyText">
      <el-table-column type="selection" width="45" :selectable="isSelectable" />
      <el-table-column prop="billNo" :label="noLabel" width="170" sortable />
      <el-table-column v-if="extraProp" :prop="extraProp" :label="extraLabel" min-width="180" show-overflow-tooltip sortable />
      <el-table-column :prop="totalField" :label="amountLabel" width="110" sortable>
        <template #default="{ row }">{{ fmt(row[totalField]) }}</template>
      </el-table-column>
      <el-table-column v-if="showMaxColumn" :label="maxLabel" width="120">
        <template #default="{ row }"><span style="color:#f59a23;font-weight:600">{{ fmt(maxOf(row)) }}</span></template>
      </el-table-column>
      <el-table-column :label="inputLabel" width="150">
        <template #default="{ row }">
          <el-input-number v-if="isSelected(row)" v-model="amounts[row.id]"
                           :min="0.01" :max="maxOf(row)" :precision="2" size="small" style="width: 100%" />
          <span v-else style="color:#c0c4cc">勾选后可输入</span>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="onVisibleChange(false)">{{ cancelText }}</el-button>
      <el-button type="primary" :disabled="!canConfirm" @click="confirm">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { isAmountValid } from './candidateAmount'
import { i18n } from '../../i18n'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: i18n.global.t('bill.candidatePickerDialog.Select') },
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  /** 已入账（表单里已有的）候选 id —— 置灰不可再选，防同一张单重复入账 */
  pickedIds: { type: Array, default: () => [] },
  tip: { type: String, default: i18n.global.t('bill.candidatePickerDialog.Select2') },
  // 列配置
  noLabel: { type: String, default: '单号' },
  extraProp: { type: String, default: '' },
  extraLabel: { type: String, default: '标题' },
  totalField: { type: String, default: 'totalAmount' },
  amountLabel: { type: String, default: '金额' },
  showMaxColumn: { type: Boolean, default: true },
  maxLabel: { type: String, default: '可冲抵余额' },
  /** 上限字段名：借款=loanBalance、预付=available、申请=totalAmount */
  maxField: { type: String, default: 'loanBalance' },
  inputLabel: { type: String, default: '本次金额' },
  emptyText: { type: String, default: i18n.global.t('common.empty') },
  cancelText: { type: String, default: '取消' },
  confirmText: { type: String, default: '确定' },
})

const emit = defineEmits(['update:modelValue', 'confirm', 'selection-change'])

const selected = ref([])          // 当前勾选的行对象
const amounts = ref({})           // row.id -> 金额（勾选时初始化、改时同步、取消勾选时清除）

function fmt(v) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
function maxOf(row) { return Number(row?.[props.maxField] || 0) }
function isSelectable(row) {
  // 可冲抵余额为 0 的候选（余额已被在途全额占满）直接不可选：
  // ① 勾了也确认不了（金额校验要求 0 < 金额 ≤ 0 无解）；
  // ② 更早一步就别让 el-input-number 拿到 min=0.01 > max=0 的组合（EP 会告警并拒绝渲染）。
  if (maxOf(row) <= 0) return false
  return !props.pickedIds.some((id) => String(id) === String(row.id))
}
function isSelected(row) { return selected.value.some((r) => String(r.id) === String(row.id)) }

function onSelectionChange(val) {
  // 新勾选的行补默认金额（=可冲抵余额，可改）；取消勾选的行清掉金额，避免残留脏值
  for (const row of val) {
    if (amounts.value[row.id] == null) amounts.value[row.id] = maxOf(row)
  }
  const keep = new Set(val.map((r) => String(r.id)))
  for (const key of Object.keys(amounts.value)) {
    if (!keep.has(String(key))) delete amounts.value[key]
  }
  selected.value = val
  emit('selection-change', val)
}

function amountValid(row) {
  return isAmountValid(amounts.value[row.id], maxOf(row))
}

const canConfirm = computed(() =>
  selected.value.length > 0 && selected.value.every(amountValid))

function confirm() {
  if (!canConfirm.value) return
  emit('confirm', selected.value.map((row) => ({ row, amount: Number(amounts.value[row.id]) })))
  onVisibleChange(false)
}

function onVisibleChange(v) {
  emit('update:modelValue', v)
  if (!v) resetState()
}

function resetState() {
  selected.value = []
  amounts.value = {}
}

defineExpose({ selected, amounts })
</script>
