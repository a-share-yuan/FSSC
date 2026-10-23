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
    :placeholder="placeholder || '请选择供应商'"
    style="width: 100%"
    @update:model-value="onUpdate"
    @change="onChange"
  >
    <el-option v-for="s in options" :key="s.id" :label="labelOf(s)" :value="valueOf(s)" />
  </el-select>
</template>

<script setup>
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

function valueOf(s) {
  return props.valueKey === 'id' ? s.id : s.supplierCode
}

function labelOf(s) {
  const name = s.supplierName || s.shortName || ''
  return s.supplierCode ? `${name}（${s.supplierCode}）` : name
}

async function load(keyword) {
  loading.value = true
  try {
    const res = await mdApi.suppliers({ pageNum: 1, pageSize: props.pageSize, keyword: keyword || undefined })
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
  emit('change', options.value.find((s) => valueOf(s) === v) || null, v)
}

onMounted(() => load())
</script>
