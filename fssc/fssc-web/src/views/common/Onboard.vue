<script setup>
// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOnboardStatus, applyOnboard, store } from '../../api'

const router = useRouter()
const loading = ref(true)
const applying = ref(false)
const err = ref('')
const tenantName = ref('')
const presets = ref([])
const selected = ref('CONTROL')
const currentMode = ref('')
const canSwitch = ref(true)

// 档位只是能力集合：卡片文案只描述功能范围，不出现人数、单据量、配额或价格
const HIGHLIGHTS = {
  EXPENSE: ['报销 / 借款 / 费用报表', '两级审批：部门经理 → 财务审核', '沿用系统已有的组织与基础数据，可直接开单'],
  CONTROL: ['在报销版之上增加申请与标准管控', '三级审批：部门经理 → 财务审核 → 财务总监', '适合需要事前申请与费用标准的团队'],
  SHARED: ['完整财务共享：影像 / 档案 / 总账 / 共享运营', '保留安装时的全量流程模型与路由', '适合已设共享中心的组织'],
}

const currentLabel = computed(() =>
  presets.value.find((p) => p.key === currentMode.value)?.label || currentMode.value)
const canApply = computed(() => !applying.value && canSwitch.value
  && presets.value.some((p) => p.key === selected.value))

onMounted(async () => {
  try {
    const d = await getOnboardStatus()
    tenantName.value = d.tenantName || d.tenantCode || ''
    presets.value = d.presets || []
    currentMode.value = d.opMode || ''
    canSwitch.value = d.canSwitch !== false
    // 已选过档的管理员仍可回到本页更换；非管理员不显示切换入口
    if (!d.needOnboard && !canSwitch.value) {
      router.replace('/workbench')
      return
    }
    if (currentMode.value && presets.value.some((p) => p.key === currentMode.value)) {
      selected.value = currentMode.value
    } else if (presets.value.length && !presets.value.some((p) => p.key === selected.value)) {
      selected.value = presets.value[0].key
    }
  } catch (e) {
    err.value = e.message || '读取版本信息失败'
  } finally {
    loading.value = false
  }
})

// 已选档后从用户下拉进入本页，若不打算更换可直接返回，不必强制选档
function goWorkbench() {
  if (!applying.value) router.replace('/workbench')
}

async function doApply(key) {
  if (applying.value) return
  applying.value = true
  err.value = ''
  try {
    const d = await applyOnboard(key)
    // 特性码是登录时写入 JWT 的快照，必须重新登录才能看到切换后的菜单
    store.token = ''
    store.user = null
    ElMessage.success(`${d.label || '版本'}已启用，请重新登录以加载新菜单`)
    router.replace('/login')
  } catch (e) {
    err.value = e.message || '切换失败，请重试'
  } finally {
    applying.value = false
  }
}
</script>

<template>
  <div class="onboard-page">
    <div class="onboard-shell">
      <div class="onboard-head">
        <h1>选择适合你的版本</h1>
        <p v-if="tenantName" class="onboard-sub">企业：{{ tenantName }}</p>
        <p v-if="currentLabel" class="onboard-sub">当前版本：{{ currentLabel }}，可随时更换</p>
        <p class="onboard-sub">
          版本只决定功能范围，不限人数、不限单据量，也没有配额或费用；切换不会删除已有数据。
        </p>
      </div>

      <div v-if="loading" class="onboard-hint">正在读取版本信息…</div>

      <template v-else>
        <div class="onboard-grid">
          <button
            v-for="p in presets"
            :key="p.key"
            type="button"
            class="onboard-card"
            :class="{ active: selected === p.key }"
            :disabled="applying"
            @click="selected = p.key"
          >
            <div class="onboard-card-title">{{ p.label }}</div>
            <div class="onboard-card-desc">{{ p.description }}</div>
            <div class="onboard-card-metric">{{ p.domains }} 个域 · {{ p.pages }} 个页面</div>
            <ul class="onboard-card-list">
              <li v-for="(h, i) in (HIGHLIGHTS[p.key] || [])" :key="i">{{ h }}</li>
            </ul>
            <div class="onboard-card-tag">{{ selected === p.key ? '已选择' : '点击选择' }}</div>
          </button>
        </div>

        <div v-if="err" class="onboard-err">{{ err }}</div>

        <div class="onboard-actions">
          <el-button type="primary" :loading="applying" :disabled="!canApply" @click="doApply(selected)">
            {{ currentMode ? '切换版本' : '启用并进入' }}
          </el-button>
          <el-button v-if="currentMode" :disabled="applying" @click="goWorkbench">
            返回工作台
          </el-button>
          <el-button v-else :disabled="applying" @click="doApply('CONTROL')">
            先按费控版进入，稍后再调整
          </el-button>
        </div>
        <p class="onboard-foot">切换后会重新登录一次，让新菜单与流程配置生效。</p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.onboard-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
  box-sizing: border-box;
}
.onboard-shell { width: min(1080px, 100%); }
.onboard-head { text-align: center; margin-bottom: 28px; }
.onboard-head h1 { font-size: 26px; font-weight: 600; margin: 0 0 12px; }
.onboard-sub { margin: 4px 0; font-size: 14px; color: var(--el-text-color-regular); }
.onboard-hint { text-align: center; color: var(--el-text-color-secondary); padding: 40px 0; }
.onboard-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.onboard-card {
  text-align: left;
  padding: 20px;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  background: var(--el-bg-color);
  color: inherit;
  cursor: pointer;
  transition: border-color .18s, box-shadow .18s;
  display: flex;
  flex-direction: column;
  min-height: 250px;
}
.onboard-card:hover:not(:disabled) { border-color: var(--el-color-primary-light-5); }
.onboard-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}
.onboard-card:disabled { cursor: default; opacity: .72; }
.onboard-card-title { font-size: 18px; font-weight: 600; margin-bottom: 8px; }
.onboard-card-desc { font-size: 13px; color: var(--el-text-color-regular); min-height: 38px; }
.onboard-card-metric {
  font-size: 12px;
  color: var(--el-color-primary);
  margin: 10px 0;
}
.onboard-card-list { margin: 0 0 14px; padding-left: 18px; flex: 1; }
.onboard-card-list li { font-size: 13px; line-height: 1.8; color: var(--el-text-color-regular); }
.onboard-card-tag { font-size: 12px; color: var(--el-text-color-secondary); text-align: right; }
.onboard-actions { display: flex; justify-content: center; gap: 12px; margin-top: 24px; flex-wrap: wrap; }
.onboard-err {
  margin-top: 16px; text-align: center; font-size: 13px;
  color: var(--el-color-danger);
}
.onboard-foot { text-align: center; font-size: 12px; color: var(--el-text-color-secondary); margin-top: 12px; }
@media (max-width: 820px) {
  .onboard-grid { grid-template-columns: 1fr; }
  .onboard-card { min-height: 0; }
}
</style>
