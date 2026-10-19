<template>
  <div>
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div class="card-head">
          <b>{{ $t('system.runStatus.ttlHealth') }}</b>
          <el-button size="small" :loading="probing" @click="probe">{{ $t('system.runStatus.btnRedetect') }}</el-button>
        </div>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item :label="$t('system.runStatus.colOverall')">
          <el-tag :type="healthTag" size="small">{{ healthLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.runStatus.colDatabase')">
          <el-tag size="small" :type="compTag(components.db)">{{ compText[components.db] || components.db || '—' }}</el-tag>
          <span v-if="components.dbError" class="err">{{ components.dbError }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Redis">
          <el-tag size="small" :type="compTag(components.redis)">{{ compText[components.redis] || components.redis || '—' }}</el-tag>
          <span v-if="components.redisError" class="err">{{ components.redisError }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.runStatus.colDisk')">
          <el-tag size="small" :type="compTag(components.disk)">{{ compText[components.disk] || components.disk || '—' }}</el-tag>
          <span v-if="components.diskTotalGb != null" class="meta">
            {{ $t('system.runStatus.statDisk', { v1: components.diskUsableGb, v2: components.diskTotalGb, v3: components.diskPath }) }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.runStatus.colJvmHeap')">
          <span v-if="components.jvmHeapMaxMb != null" class="meta">
            {{ $t('system.runStatus.statJvm', { v1: components.jvmHeapUsedMb, v2: components.jvmHeapMaxMb, v3: components.jvmThreads }) }}</span>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.runStatus.colUptime')">{{ uptimeText }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.runStatus.colServiceVersion')">{{ health.app || '—' }} v{{ health.version || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Java">{{ health.javaVersion || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.runStatus.colServerTime')">{{ formatDateTime(health.serverTime) || now }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.runStatus.colTenant')">{{ store.user?.tenantId || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.runStatus.colLoggedInUser')">{{ user }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.runStatus.colDetectedAt')">{{ probedAt || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card>
      <template #header><b>{{ $t('system.runStatus.ttlOpsData') }}</b></template>
      <el-row :gutter="12">
        <el-col v-for="k in kpis" :key="k.kpi" :span="6" style="margin-bottom: 12px">
          <el-card shadow="hover" style="text-align: center">
            <div style="font-size: 26px; font-weight: 700; color: #409eff">{{ k.val }}</div>
            <div style="color: #909399; font-size: 13px; margin-top: 4px">{{ k.kpi }}</div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!kpis.length" :description="$t('system.runStatus.emptyOpsData')" />
    </el-card>
  </div>
</template>

<script setup>
// P0-4（41号审查报告）：原页数据库为写死文案"MySQL 8.0 (fssc)"、无真实探活。
// 现接 /api/health（免鉴权端点，JwtAuthFilter 白名单）：DB / Redis / 磁盘 / JVM 堆 / 运行时长 / 版本。
import { computed, onMounted, ref } from 'vue'
import { healthApi, reportApi, store } from '../../api'
import { formatDateTime } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const probing = ref(false)
const probedAt = ref('')
const health = ref({})
const now = ref('')
const user = ref(store.user?.realName || store.user?.username || '')
const kpis = ref([])

const components = computed(() => health.value.components || {})
const healthLabel = computed(() => health.value.status || (probedAt.value ? 'DOWN' : t('system.runStatus.stNotDetected')))
const healthTag = computed(() => (health.value.status === 'UP' ? 'success' : 'danger'))
const uptimeText = computed(() => {
  const s = Number(health.value.uptimeSeconds || 0)
  if (!s) return '—'
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60)
  return (d ? t('system.runStatus.unitDays', { v1: d }) : '') + (h ? t('system.runStatus.unitHours', { v1: h }) : '') + t('system.runStatus.unitMinutes', { v1: m })
})

function compTag(st) {
  if (st === 'UP') return 'success'
  if (st === 'DISABLED') return 'info'
  return 'danger'
}

// R5-D-050：组件状态枚举中文化（UP/DOWN/DISABLED/UNKNOWN 原样出英文码对用户不可读）
const compText = computed(() => ({ UP: t('system.common.stNormal'), DOWN: t('system.runStatus.stAbnormal'), DISABLED: t('system.runStatus.stNotEnabled'), UNKNOWN: t('system.runStatus.stUnknown') }))

async function probe() {
  probing.value = true
  try {
    const r = await healthApi.get()
    health.value = r || {}
  } catch {
    // 探活失败 = 后端不可达或 DOWN（/api/health DOWN 时返回 code!=0）
    health.value = { status: 'DOWN', components: {} }
  } finally {
    probedAt.value = new Date().toLocaleString('zh-CN')
    probing.value = false
  }
}

onMounted(async () => {
  now.value = new Date().toLocaleString('zh-CN')
  probe()
  try {
    kpis.value = (await reportApi.opsStatistics()) || []
  } catch (e) {
    // 运营数据加载失败不阻断健康展示
  }
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.meta { font-size: 12px; color: #909399; margin-left: 6px; }
.err { font-size: 12px; color: #f56c6c; margin-left: 6px; }
</style>
