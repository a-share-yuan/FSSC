<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <el-card>
    <template #header>
      <div style="display: flex; gap: 16px; align-items: center; flex-wrap: wrap">
        <b>资产与存货管理</b>
        <span style="color: #909399; font-size: 12px">资产卡片 / 资产类别 / 存货收发存 / 税会差异 —— 折旧、报废、出入库全流程可视化</span>
        <el-button type="info" size="small" style="margin-left: auto" @click="reload">刷新</el-button>
      </div>
    </template>

    <el-tabs v-model="tab">
      <el-tab-pane label="资产卡片" name="asset" lazy>
        <AssetCardTab ref="assetTabRef" />
      </el-tab-pane>
      <el-tab-pane label="资产类别" name="category" lazy>
        <AssetCategoryTab ref="categoryTabRef" />
      </el-tab-pane>
      <el-tab-pane label="存货收发存" name="inventory" lazy>
        <InventoryTab ref="inventoryTabRef" />
      </el-tab-pane>
      <el-tab-pane label="税会差异" name="taxDiff" lazy>
        <TaxDiffTab ref="taxDiffTabRef" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
  </div>
</template>

<script setup>

import { ref, provide, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { assetApi, orgApi, mdApi } from '../../api'
import AssetCardTab from './AssetCardTab.vue'
import AssetCategoryTab from './AssetCategoryTab.vue'
import InventoryTab from './InventoryTab.vue'
import TaxDiffTab from './TaxDiffTab.vue'
import { useKeyboard } from '@/composables/useKeyboard'
// R5-13: 快捷键
useKeyboard({
  'ctrl+r': () => { try { reload() } catch { /* noop */ } },
})

// D-BIZ-05：资产与存货在 sys_menu 里只有 3071「资产与存货」一条菜单行，四个子视图没有拆出的子菜单。
// 原先按「一 TAB = 一菜单」的约定隐藏页签条 ⇒ 资产类别/存货收发存/税会差异只能手敲 URL。
// 菜单未拆的页不藏页签：深链进来仍定位到指定页签，同时把其余页签露出来可点。
const props = defineProps({ tab: { type: String, default: '' } })
const tab = ref(props.tab || 'asset')
const loading = ref(false)

// ========== 共享主数据 ==========
const categories = ref([])
const depts = ref([])
const companies = ref([])
const suppliers = ref([])
// D-BIZ-03：供应商查询被 4030 拒时前端把错误吞成空列表，用户只看到「下拉是空的」。
// 记下该标志，供建档弹窗把提示改成「无查看权限」而不是静默空表。
const suppliersDenied = ref(false)
const warehouses = ref([])

// ========== 共享工具函数 ==========
const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const fmt0 = (v) => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })

function categoryName(id) { const c = categories.value.find((x) => x.id === id); return c ? c.categoryName : (id || '\u2014') }
function deptName(id) { const d = depts.value.find((x) => x.id === id); return d ? d.name : (id || '\u2014') }
function companyName(id) { const c = companies.value.find((x) => x.id === id); return c ? c.name : (id || '\u2014') }
function supplierName(id) { const s = suppliers.value.find((x) => x.id === id); return s ? (s.supplierName || s.name) : (id || '\u2014') }
function warehouseName(id) { const w = warehouses.value.find((x) => x.id === id); return w ? w.name : (id ? '\u4ed3\u5e93-' + id : '\u2014') }

// provide 共享数据给所有子组件
provide('assetShared', {
  categories, depts, companies, suppliers, suppliersDenied, warehouses, loading,
  fmt, fmt0, categoryName, deptName, companyName, supplierName, warehouseName,
})

// ========== 子组件 refs ==========
const assetTabRef = ref(null)
const categoryTabRef = ref(null)
const inventoryTabRef = ref(null)
const taxDiffTabRef = ref(null)

// ========== 加载主数据 ==========
async function loadMasters() {
  try {
    const [deptRes, compRes, supplierRes] = await Promise.allSettled([
      orgApi.deptTree(),
      orgApi.companyTree(),
      mdApi.suppliers({ pageNum: 1, pageSize: 500 }),
    ])
    if (deptRes.status === 'fulfilled') {
      const flat = []
      const walk = (arr) => { arr.forEach((n) => { flat.push({ id: n.id, name: n.deptName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
      walk(deptRes.value || [])
      depts.value = flat
    }
    if (compRes.status === 'fulfilled') {
      const flat = []
      const walk = (arr) => { arr.forEach((n) => { flat.push({ id: n.id, name: n.companyName || n.name || n.label }); if (n.children?.length) walk(n.children) }) }
      walk(compRes.value || [])
      companies.value = flat
    }
    if (supplierRes.status === 'fulfilled') {
      suppliers.value = (supplierRes.value.list || []).map((s) => ({ id: s.id, supplierName: s.supplierName || s.name, name: s.name }))
      suppliersDenied.value = false
    } else {
      suppliersDenied.value = supplierRes.reason?.code === 4030
    }
  } catch (e) { /* ignore */ }
}

// 加载资产类别（共享数据，子组件依赖）
async function loadCategories() {
  try { categories.value = (await assetApi.categoryPage({ pageNum: 1, pageSize: 200 })).list || [] } catch (e) { ElMessage.error(e.message) }
}

function reload() {
  assetTabRef.value?.loadAssets()
  categoryTabRef.value?.loadCategories()
  inventoryTabRef.value?.loadInventory()
  taxDiffTabRef.value?.loadTaxDiff()
}

onMounted(async () => {
  await Promise.all([loadMasters(), loadCategories()])
})
</script>

