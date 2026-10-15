<template>
  <div class="close-workbench">
    <!-- 统一关账工作台头：AP 月末关账 + AR 应收月结合并入口（41号审查报告 P1-5） -->
    <el-card shadow="never" class="head-card">
      <div class="head">
        <div>
          <div class="head-title">{{ $t('flow.closebench.title') }}</div>
          <div class="head-sub">{{ scope === 'ar' ? $t('flow.closebench.scopeAr') : scope === 'ap' ? $t('flow.closebench.scopeAp') : $t('flow.closebench.scopeBoth') }}</div>
        </div>
        <div class="head-stats">
          <div class="stat">
            <div class="stat-num warn">{{ (apBoard?.rows || []).filter((r) => r.status !== 'CLOSED').length + (arBoard?.rows || []).filter((r) => r.status !== 'CLOSED').length }}</div>
            <div class="stat-label">{{ $t('flow.closebench.statInProgress') }}</div>
          </div>
          <div class="stat">
            <div class="stat-num danger">{{ frozenCount }}</div>
            <div class="stat-label">{{ $t('flow.closebench.statFrozen') }}</div>
          </div>
          <div class="stat">
            <div class="stat-num ok">{{ closedThisYear }}</div>
            <div class="stat-label">{{ $t('flow.closebench.statDoneThisYear') }}</div>
          </div>
        </div>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" class="close-tabs">
      <el-tab-pane v-if="scope !== 'ar'" :label="$t('flow.closebench.scopeAp')" name="ap">
        <ApMonthCloseBoard ref="apBoard" />
      </el-tab-pane>
      <el-tab-pane v-if="scope !== 'ap'" :label="$t('flow.entryAr.tabMonthClose')" name="ar">
        <ArMonthCloseBoard ref="arBoard" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
/**
 * 统一关账工作台（41-非费用域完成度审查 P1-5）
 *
 * 一个工作台分模块 Checklist（AP / AR），后续 GL/资产模块可继续扩展 el-tab-pane。
 * 按路由自动定位页签：/apflow/ap-month-close → AP，/arflow/ar-month-close → AR。
 * scope prop＝宿主工作台限定单侧（'ar' 时只渲染 AR 月结，避免向应收角色暴露 AP 关账数据）。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ApMonthCloseBoard from './ApMonthCloseBoard.vue'
import ArMonthCloseBoard from './ArMonthCloseBoard.vue'

const props = defineProps({ scope: { type: String, default: '' } })
const route = useRoute()
const apBoard = ref(null)
const arBoard = ref(null)
const activeTab = ref(props.scope === 'ar' ? 'ar' : String(route.path || '').includes('arflow') ? 'ar' : 'ap')

const frozenCount = computed(() =>
  apBoard.value?.rows?.filter((r) => r.scpFrozen === 1 && r.status !== 'CLOSED').length
  || 0)

const closedThisYear = computed(() => {
  const y = String(new Date().getFullYear())
  const hit = (r) => r.status === 'CLOSED' && String(r.closedTime || '').startsWith(y)
  return (apBoard.value?.rows?.filter(hit).length || 0)
    + (arBoard.value?.rows?.filter(hit).length || 0)
})

onMounted(() => {
  // 子面板各自 onMounted 拉数，此处无需重复请求
})
</script>

<style scoped>
.head-card { margin-bottom: 12px; }
.head { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.head-title { font-size: 17px; font-weight: 700; }
.head-sub { font-size: 12px; color: #909399; margin-top: 4px; }
.head-stats { display: flex; gap: 28px; }
.stat { text-align: center; }
.stat-num { font-size: 24px; font-weight: 700; }
.stat-num.warn { color: #e6a23c; }
.stat-num.danger { color: #f56c6c; }
.stat-num.ok { color: #67c23a; }
.stat-label { font-size: 12px; color: #909399; margin-top: 2px; }
</style>
