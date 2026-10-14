<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:60vh;padding:40px 20px">
    <div style="font-size:72px;font-weight:700;color:#dcdfe6;line-height:1">404</div>
    <div style="font-size:18px;color:#606266;margin:16px 0 8px">页面不存在或已被移除</div>
    <div style="color:#909399;font-size:13px;margin-bottom:24px">请检查地址是否正确，或返回工作台继续操作</div>
    <el-button type="primary" @click="$router.push('/workbench')">返回工作台</el-button>
  </div>
</template>
<script setup>
</script>
