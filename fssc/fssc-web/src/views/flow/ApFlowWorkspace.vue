<template>
  <div class="flow-workspace">
    <div class="ws-head">
      <h2 class="ws-title">{{ $t('flow.entry.apWorkspace') }}</h2>
      <span class="ws-sub">{{ $t('flow.entry.apServiceDesc') }}</span>
      <div class="ws-links">
        <el-button v-if="hasPerm('apflow:close:list')" link type="primary" @click="$router.push('/apflow/ap-month-close')">{{ $t('flow.entry.monthCloseLink') }}</el-button>
        <el-button v-if="hasPerm('pay:recon:list')" link type="primary" @click="$router.push('/pay/recon')">{{ $t('flow.entry.apReconLink') }}</el-button>
      </div>
    </div>
    <el-tabs v-if="visibleNames.length" v-model="tab" class="ws-tabs">
      <el-tab-pane v-if="canList" :label="$t('flow.entry.apServiceTitle')" name="request" lazy><ApRequestBoard /></el-tab-pane>
      <el-tab-pane v-if="hasPerm('arflow:report:view')" :label="$t('flow.entry.reportTitle')" name="reports" lazy><ArApReportBoard /></el-tab-pane>
      <el-tab-pane v-if="hasPerm('collab:scp:list')" :label="$t('flow.entryAp.tabSupplyChain')" name="collab" lazy><ArApCollabBoard /></el-tab-pane>
    </el-tabs>
    <el-empty v-else :description="$t('flow.entry.noListPerm')" />
  </div>
</template>

<script setup>
// 方案 A（页面Tab化重构册 §甲）：应付侧蓝图收敛；ap-month-close 菜单 3400 保留故以链接承接，不重复嵌组件。
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ApRequestBoard from './ApRequestBoard.vue'
import ArApReportBoard from './ArApReportBoard.vue'
import ArApCollabBoard from './ArApCollabBoard.vue'
import { hasPerm } from '@/utils/perm'

const route = useRoute()
const router = useRouter()
// R5-D-106：无 apflow:request:list 权限时不挂载申请板（避免 4030 被吞成静默空表），显式无权限态引导授权
const canList = hasPerm('apflow:request:list')
// 页签按内层接口码门控：报表＝arflow:report:view，协同＝collab:scp:list，无码不渲染以免点了才 403
const visibleNames = ['request', 'reports', 'collab'].filter((n) =>
  n === 'request' ? canList : n === 'reports' ? hasPerm('arflow:report:view') : hasPerm('collab:scp:list'))
const q = String(route.query.tab || '')
const tab = ref(visibleNames.includes(q) ? q : (visibleNames[0] || ''))
watch(tab, (v) => { router.replace({ query: { ...route.query, tab: v } }) })
</script>

<style scoped>
.ws-head { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
.ws-title { margin: 0; font-size: 18px; font-weight: 600; }
.ws-sub { color: #909399; font-size: 12px; }
.ws-links { display: flex; gap: 8px; }
</style>
