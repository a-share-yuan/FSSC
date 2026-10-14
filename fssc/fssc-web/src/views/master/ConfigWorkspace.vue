<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="cfg-workspace">
    <div class="cw-head">
      <h2 class="cw-title">{{ group.title }}</h2>
      <span class="cw-sub">{{ group.sub }}</span>
    </div>
    <el-tabs v-model="tab" class="cw-tabs">
      <el-tab-pane v-for="t in visibleTabs" :key="t.key" :label="t.label" :name="t.key" lazy>
        <component :is="t.comp" v-bind="t.props" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
/**
 * 方案 B 泛型宿主（页面Tab化重构册 §乙）：一个配置工作区＝一个菜单，页内 tab 复用通用表格组件。
 * el-tab-pane lazy＝首次激活才挂载，避免同组 N 张表并发取数（D-DEC-265 教训）。
 * 分组清单见 configWorkspaceRegistry.js；旧「一表一页」路由全部保留，本页只做聚合呈现。
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { hasPerm } from '@/utils/perm'
import { CONFIG_GROUPS } from './configWorkspaceRegistry'

const props = defineProps({
  group: { type: String, required: true },
})

const route = useRoute()
const router = useRouter()
const group = computed(() => CONFIG_GROUPS[props.group] || { title: '配置工作区', sub: '', tabs: [] })
// 声明了 perm 的 tab 才门控（无码＝不渲染，避免点了才吃「缺少权限」）；未声明的沿用全量渲染。
const visibleTabs = computed(() => group.value.tabs.filter((t) => !t.perm || hasPerm(t.perm)))
// 越权/拼错的 ?tab= 不能留空白正文：回落到首个可见 tab
const initialTab = () => {
  const q = String(route.query.tab || '');
  return visibleTabs.value.some((t) => t.key === q) ? q : (visibleTabs.value[0]?.key || '');
};
const tab = ref(initialTab());
// 回落时同步改写 URL，避免复制出去的链接仍指向不可见 tab
if (String(route.query.tab || '') !== tab.value) router.replace({ query: { ...route.query, tab: tab.value } });
watch(tab, (v) => { router.replace({ query: { ...route.query, tab: v } }) });
</script>

<style scoped>
.cw-head { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
.cw-title { margin: 0; font-size: 18px; font-weight: 600; }
.cw-sub { color: #909399; font-size: 12px; }
</style>
