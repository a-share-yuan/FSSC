<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="inline-actions" v-if="visible">
    <el-tooltip content="查看" placement="top">
      <el-button link size="small" @click.stop="$emit('view')">
        <el-icon><View /></el-icon>
      </el-button>
    </el-tooltip>
    <el-tooltip v-if="showApprove" content="审批" placement="top">
      <el-button link size="small" type="success" @click.stop="$emit('approve')">
        <el-icon><Check /></el-icon>
      </el-button>
    </el-tooltip>
    <el-tooltip v-if="showReject" content="退回" placement="top">
      <el-button link size="small" type="danger" @click.stop="$emit('reject')">
        <el-icon><Close /></el-icon>
      </el-button>
    </el-tooltip>
    <el-tooltip v-if="showPrint" content="打印" placement="top">
      <el-button link size="small" @click.stop="$emit('print')">
        <el-icon><Printer /></el-icon>
      </el-button>
    </el-tooltip>
    <el-tooltip v-if="showTrace" content="全链路追溯" placement="top">
      <el-button link size="small" @click.stop="$emit('trace')">
        <el-icon><Connection /></el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script setup>
import { View, Check, Close, Printer, Connection } from '@element-plus/icons-vue'

defineProps({
  visible: { type: Boolean, default: true },
  showApprove: { type: Boolean, default: true },
  showReject: { type: Boolean, default: true },
  showPrint: { type: Boolean, default: false },
  showTrace: { type: Boolean, default: true },
})

defineEmits(['view', 'approve', 'reject', 'print', 'trace'])
</script>

<style scoped>
/* 操作按钮始终可见：默认 opacity:0 仅悬停自显示的话，用户根本发现不了按钮 */
.inline-actions {
  display: flex; align-items: center; gap: 2px;
  opacity: 1; transition: opacity 0.15s;
}
.inline-actions:hover :deep(.el-button) { font-weight: 600; }
</style>
