<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-select
    :model-value="modelValue"
    filterable
    remote
    reserve-keyword
    :remote-method="load"
    :loading="loading"
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder || $t('common.phPickCustomer')"
    style="width: 100%"
    @update:model-value="onUpdate"
    @change="onChange"
  >
    <!-- 编码仍在（同名往来单位的唯一辨认手段），但退到次要位；不要从 label 里带编码＝选中框与输入回显都会显出「（S001）」这种开发腔 -->
    <el-option v-for="c in options" :key="c.id" :label="nameOf(c)" :value="valueOf(c)">
      <span class="opt-row"><span class="opt-name">{{ nameOf(c) }}</span><span class="opt-code">{{ c.customerCode }}</span></span>
    </el-option>
  </el-select>
</template>

<script setup>
/**
 * CustomerSelect 客户选择组件：远程搜索客户档案，避免手输客户编码/名称/ID。
 *
 * props:
 *   modelValue 选中值（默认客户编码；valueKey='id' 时为客户ID）
 *   valueKey 'code'（默认）/ 'id'
 *   pageSize 拉查询量（默认 300）
 *   clearable / disabled / placeholder
 *
 * emits:
 *   update:modelValue 选中值
 *   change(customer, value) 选中变化（customer 为完整客户对象，用于回填名称等）
 */
import { onMounted, ref } from 'vue'
import { mdApi } from '@/api'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  valueKey: { type: String, default: 'code', validator: (v) => ['code', 'id'].includes(v) },
  pageSize: { type: Number, default: 300 },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'change'])

const options = ref([])
const loading = ref(false)

function valueOf(c) {
  return props.valueKey === 'id' ? c.id : c.customerCode
}

function nameOf(c) {
  return c.customerName || c.supplierName || c.name || c.customerCode || ''
}

async function load(keyword) {
  loading.value = true
  try {
    const res = await mdApi.customers({ pageNum: 1, pageSize: props.pageSize, keyword: keyword || undefined })
    options.value = res?.list || []
  } catch (e) {
    options.value = []
  } finally {
    loading.value = false
  }
}

function onUpdate(v) {
  emit('update:modelValue', v)
}

function onChange(v) {
  emit('change', options.value.find((c) => valueOf(c) === v) || null, v)
}

onMounted(() => load())
</script>

<style scoped>
.opt-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}
.opt-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.opt-code {
  flex: none;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
