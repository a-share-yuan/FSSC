<template>
  <div class="flow-workspace">
    <div class="ws-head">
      <div class="ws-head-txt">
        <h2 class="ws-title">{{ $t('flow.entryAr.arWorkspace') }}</h2>
        <span class="ws-sub">{{ $t('flow.entryAr.arWorkspaceDesc') }}</span>
      </div>
      <!-- 原应收工作台头部有统一刷新；页签化后改由这里重建当前页签取数（lazy 下只挂激活项） -->
      <el-button v-if="visibleNames.length" size="small" @click="refresh">{{ $t('common.refresh') }}</el-button>
    </div>
    <el-tabs v-if="visibleNames.length" :key="nonce" v-model="tab" class="ws-tabs">
      <el-tab-pane v-if="tabVisible('receivable-list')" :label="$t('flow.entryAr.tabReceivable')" name="receivable-list" lazy><ArReceivableList /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('claims')" :label="$t('flow.entryAr.tabServiceRequest')" name="claims" lazy><ArClaimBoard /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('billing')" :label="$t('flow.entryAr.tabBillingChain')" name="billing" lazy><ArBillingBoard /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('writeoff')" :label="$t('flow.entryAr.tabWriteoff')" name="writeoff" lazy><ArWriteoffBoard /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('notes')" :label="$t('flow.entryAr.tabNote')" name="notes" lazy><ArNoteBoard /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('collection')" :label="$t('flow.entryAr.tabCollection')" name="collection" lazy><ArCollectionBoard /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('receipt')" :label="$t('flow.entryAr.tabReceipt')" name="receipt" lazy><ArReceiptBoard /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('bad-debt')" :label="$t('flow.entryAr.tabBadDebt')" name="bad-debt" lazy><ArBadDebtBoard /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('month-close')" :label="$t('flow.entryAr.tabMonthClose')" name="month-close" lazy><CloseWorkbench scope="ar" /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('reports')" :label="$t('flow.entryAr.tabReport')" name="reports" lazy><ArApReportBoard /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('collab')" :label="$t('flow.entryAp.tabSupplyChain')" name="collab" lazy><ArApCollabBoard /></el-tab-pane>
      <el-tab-pane v-if="tabVisible('base-config')" :label="$t('flow.entryAr.tabBaseConfig')" name="base-config" lazy><ArBaseConfigPage /></el-tab-pane>
    </el-tabs>
    <el-empty v-else :description="$t('flow.entry.noListPerm')" />
  </div>
</template>

<script setup>
// 方案 A（页面Tab化重构册 §甲）：蓝图 9 页收敛为一域一工作台，页内 tab 复用既有 Board 组件，
// 不新增流程/表/API。el-tab-pane lazy＝首次激活才挂载，避免 8 个 Board 并发取数（D-DEC-265 教训）。
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ArClaimBoard from './ArClaimBoard.vue'
import ArBillingBoard from './ArBillingBoard.vue'
import ArWriteoffBoard from './ArWriteoffBoard.vue'
import ArNoteBoard from './ArNoteBoard.vue'
import ArBadDebtBoard from './ArBadDebtBoard.vue'
import ArApReportBoard from './ArApReportBoard.vue'
import ArApCollabBoard from './ArApCollabBoard.vue'
import ArBaseConfigPage from './ArBaseConfigPage.vue'
import CloseWorkbench from './CloseWorkbench.vue'
import ArReceivableList from './ArReceivableList.vue'
import ArCollectionBoard from './ArCollectionBoard.vue'
import ArReceiptBoard from './ArReceiptBoard.vue'
import { hasPerm } from '@/utils/perm'

const route = useRoute()
const router = useRouter()
// 页签逐个按内层接口码门控：页面授了但缺某个域的码时，不渲染该页签，避免点开才 403。
// 这里只用**本域**权限码门控。跨域借来的码（如基础配置的端点在系统参数下、要 system:config:list）
// 一律不参与页签显隐：业务角色多数不持有系统域码，拿它做 AND 门控会连整个功能入口一起抹掉，
// 用户看到的是"这功能没了"而不是"没权限"——比报错更糟。缺该码时由组件内空态明示。
const TAB_PERMS = {
  'receivable-list': 'receivable:list',
  claims: 'arflow:claim:manage',
  billing: 'arflow:billing:manage',
  writeoff: 'arflow:writeoff:manage',
  notes: 'arflow:note:list',
  collection: 'receivable:list',
  receipt: 'receivable:list',
  'bad-debt': 'arflow:baddebt:manage',
  'month-close': 'arflow:monthclose:manage',
  reports: 'arflow:report:view',
  collab: 'collab:scp:list',
  'base-config': 'arflow:baseconfig:manage',
}
const tabVisible = (name) => hasPerm(TAB_PERMS[name])
const visibleNames = Object.keys(TAB_PERMS).filter(tabVisible)
const q = String(route.query.tab || '')
const tab = ref(visibleNames.includes(q) ? q : (visibleNames[0] || ''))
watch(tab, (v) => { router.replace({ query: { ...route.query, tab: v } }) })
const nonce = ref(0)
const refresh = () => { nonce.value++ }
</script>

<style scoped>
.ws-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.ws-head-txt { display: flex; flex-direction: column; gap: 4px; }
.ws-title { margin: 0; font-size: 18px; font-weight: 600; }
.ws-sub { color: #909399; font-size: 12px; }
</style>
