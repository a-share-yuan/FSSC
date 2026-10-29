<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    direction="rtl"
    size="820px"
    :destroy-on-close="true"
  >
    <BillDetailPanel :bill-id="row?.billId" :bill-type="row?.billType" :task-id="row?.taskId || ''" />

    <template #footer>
      <div class="apd-foot">
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="warning" :disabled="!hasBill" @click="emit('deduct')">核减</el-button>
        <el-button type="danger" :disabled="!hasBill" @click="emit('approve', false)">驳回</el-button>
        <el-button type="success" :disabled="!hasBill" @click="emit('approve', true)">通过</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import BillDetailPanel from '../bill/BillDetailPanel.vue'

/**
 * 审批工作台「去处理」直达的单据详情抽屉（合规整改）。
 *
 * 原实现把审批人送到业务模块的「发起单据」页（`/expense` → 重定向 `/expense/claim` 新建表单），
 * 既没有查询也没有详情，审批人看不到附件、OCR 结果与审批历史 —— 形成实质「盲审」。
 * 这里在审批上下文内一次性铺开审批所需材料（详情主体复用 BillDetailPanel），
 * 底部直接给出通过/驳回/核减，与工作台行内操作共用同一套审批逻辑（由父组件承接）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  row: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue', 'approve', 'deduct'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const hasBill = computed(() => props.row?.billId != null)
const drawerTitle = computed(() => `单据详情 · ${props.row?.billNo || ''}`)
</script>

<style scoped>
.apd-foot { display: flex; justify-content: flex-end; gap: 8px; }
</style>
