<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div>
    <el-card style="margin-bottom: 12px">
      <template #header>
        <div class="card-head">
          <b>服务状态（系统监控 · /api/health 真实探活）</b>
          <el-button size="small" :loading="probing" @click="probe">重新探测</el-button>
        </div>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="整体状态">
          <el-tag :type="healthTag" size="small">{{ healthLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="数据库">
          <el-tag size="small" :type="compTag(components.db)">{{ compText[components.db] || components.db || '—' }}</el-tag>
          <span v-if="components.dbError" class="err">{{ components.dbError }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Redis">
          <el-tag size="small" :type="compTag(components.redis)">{{ compText[components.redis] || components.redis || '—' }}</el-tag>
          <span v-if="components.redisError" class="err">{{ components.redisError }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="磁盘">
          <el-tag size="small" :type="compTag(components.disk)">{{ compText[components.disk] || components.disk || '—' }}</el-tag>
          <span v-if="components.diskTotalGb != null" class="meta">
            剩余 {{ components.diskUsableGb }}GB / 共 {{ components.diskTotalGb }}GB（{{ components.diskPath }}）
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="JVM 堆">
          <span v-if="components.jvmHeapMaxMb != null" class="meta">
            {{ components.jvmHeapUsedMb }}MB / {{ components.jvmHeapMaxMb }}MB · 线程 {{ components.jvmThreads }}
          </span>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="运行时长">{{ uptimeText }}</el-descriptions-item>
        <el-descriptions-item label="服务版本">{{ health.app || '—' }} v{{ health.version || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Java">{{ health.javaVersion || '—' }}</el-descriptions-item>
        <el-descriptions-item label="服务器时间">{{ formatDateTime(health.serverTime) || now }}</el-descriptions-item>
        <el-descriptions-item label="租户">{{ store.user?.tenantId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="登录用户">{{ user }}</el-descriptions-item>
        <el-descriptions-item label="探测时间">{{ probedAt || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card>
      <template #header><b>运营数据一览</b></template>
      <el-row :gutter="12">
        <el-col v-for="k in kpis" :key="k.kpi" :span="6" style="margin-bottom: 12px">
          <el-card shadow="hover" style="text-align: center">
            <div style="font-size: 26px; font-weight: 700; color: #409eff">{{ k.val }}</div>
            <div style="color: #909399; font-size: 13px; margin-top: 4px">{{ k.kpi }}</div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!kpis.length" description="暂无运营数据" />
    </el-card>
  </div>
</template>

<script setup>
// P0-4（41号审查报告）：原页数据库为写死文案"MySQL 8.0 (fssc)"、无真实探活。
// 现接 /api/health（免鉴权端点，JwtAuthFilter 白名单）：DB / Redis / 磁盘 / JVM 堆 / 运行时长 / 版本。
import { computed, onMounted, ref } from 'vue'
import { healthApi, reportApi, store } from '../../api'
import { formatDateTime } from '@/utils/format'

const probing = ref(false)
const probedAt = ref('')
const health = ref({})
const now = ref('')
const user = ref(store.user?.realName || store.user?.username || '')
const kpis = ref([])

const components = computed(() => health.value.components || {})
const healthLabel = computed(() => health.value.status || (probedAt.value ? 'DOWN' : '未探测'))
const healthTag = computed(() => (health.value.status === 'UP' ? 'success' : 'danger'))
const uptimeText = computed(() => {
  const s = Number(health.value.uptimeSeconds || 0)
  if (!s) return '—'
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60)
  return (d ? `${d}天` : '') + (h ? `${h}时` : '') + `${m}分`
})

function compTag(st) {
  if (st === 'UP') return 'success'
  if (st === 'DISABLED') return 'info'
  return 'danger'
}

// R5-D-050：组件状态枚举中文化（UP/DOWN/DISABLED/UNKNOWN 原样出英文码对用户不可读）
const compText = { UP: '正常', DOWN: '异常', DISABLED: '未启用', UNKNOWN: '未知' }

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
