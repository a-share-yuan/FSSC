<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
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
        <el-button @click="visible = false">{{ $t('common.close') }}</el-button>
        <!-- 三个动作按钮原先只写 :disabled="!hasBill"，灰了不说原因：
             审批人看到「通过」点不动，只会以为系统坏了或自己没权限。
             hasBill=false 的真实含义是「单据详情还没加载完」，补一句原因即可。
             el-tooltip 必须包一层 span：disabled 的 el-button 不派发鼠标事件，直接包不显示提示。 -->
        <el-tooltip :disabled="hasBill" content="单据详情尚未加载完成，暂不能操作；请稍候或重新打开" placement="top">
          <span class="apd-btn-wrap">
            <el-button type="warning" :disabled="!hasBill" @click="emit('deduct')">{{ $t('approval.approvalDetailDrawer.Reduce') }}</el-button>
          </span>
        </el-tooltip>
        <el-tooltip :disabled="hasBill" content="单据详情尚未加载完成，暂不能操作；请稍候或重新打开" placement="top">
          <span class="apd-btn-wrap">
            <el-button type="danger" :disabled="!hasBill" @click="emit('approve', false)">{{ $t('approval.approvalDetailDrawer.Reject') }}</el-button>
          </span>
        </el-tooltip>
        <el-tooltip :disabled="hasBill" content="单据详情尚未加载完成，暂不能操作；请稍候或重新打开" placement="top">
          <span class="apd-btn-wrap">
            <el-button type="success" :disabled="!hasBill" @click="emit('approve', true)">{{ $t('common.pass') }}</el-button>
          </span>
        </el-tooltip>
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
/* 禁用按钮的 tooltip 载体：flex 容器里必须是 inline-flex，否则 span 撑满整行 */
.apd-btn-wrap { display: inline-flex; }
</style>
