<template>
  <div v-if="show" class="table-pager">
    <el-pagination
      background
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      :page-sizes="pageSizes"
      :page-size="innerSize"
      :current-page="innerPage"
      :disabled="disabled"
      @current-change="onCurrent"
      @size-change="onSize"
    />
  </div>
</template>

<script setup>
/**
 * TablePager - 统一分页器组件（第 6 轮体检 P0-1）
 *
 * 全项目此前仅 7/48 处分页器配置完整（缺 sizes / jumper）。
 * 本组件统一为「总数 + 每页条数 + 上下页 + 页码 + 跳页」完整布局，
 * 对齐 SAP / 用友 / 金蝶 的企业级列表标准。
 *
 * 用法（配合 useTablePager）：
 *   <TablePager :pager="pager" @change="load" />
 *
 * 也可独立使用：
 *   <TablePager v-model:page="pageNum" v-model:size="pageSize" :total="total" @change="load" />
 */
import { computed, ref, watch } from 'vue'
import { PAGE_SIZE_OPTIONS } from '../../composables/useTablePager'

const props = defineProps({
  /** useTablePager 返回对象（传入后自动接管 pageNum/pageSize/total） */
  pager: { type: Object, default: null },
  /** v-model:page 页码（未传 pager 时使用） */
  page: { type: Number, default: 1 },
  /** v-model:size 每页条数（未传 pager 时使用） */
  size: { type: Number, default: 20 },
  /** 总条数（未传 pager 时使用） */
  total: { type: Number, default: 0 },
  /** 少于该条数时隐藏分页器（避免只有几行还挂分页器） */
  hideOnLessThan: { type: Number, default: 0 },
  disabled: { type: Boolean, default: false },
  pageSizes: { type: Array, default: () => PAGE_SIZE_OPTIONS },
})

const emit = defineEmits(['update:page', 'update:size', 'change'])

// 代理到 pager 或独立 props
const innerPage = computed(() => (props.pager ? props.pager.pageNum.value : props.page))
const innerSize = computed(() => (props.pager ? props.pager.pageSize.value : props.size))
const total = computed(() => (props.pager ? props.pager.total.value : props.total))
const show = computed(() => !props.hideOnLessThan || total.value > props.hideOnLessThan)

function onCurrent(p) {
  if (props.pager) props.pager.onPageChange(p)
  emit('update:page', p)
  emit('change', { pageNum: p, pageSize: innerSize.value })
}

function onSize(s) {
  if (props.pager) props.pager.onSizeChange(s)
  emit('update:size', s)
  emit('change', { pageNum: 1, pageSize: s })
}
</script>

<style scoped>
.table-pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-top: 12px;
  padding-top: 4px;
}
.table-pager :deep(.el-pagination) {
  --el-pagination-button-color: var(--el-text-color-regular);
  flex-wrap: wrap;
  row-gap: 6px;
}
</style>
