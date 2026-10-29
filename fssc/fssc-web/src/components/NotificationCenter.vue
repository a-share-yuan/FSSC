<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="380"
    trigger="click"
    popper-class="notify-popper"
  >
    <template #reference>
      <slot>
        <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0">
          <el-button link><el-icon :size="18"><Bell /></el-icon></el-button>
        </el-badge>
      </slot>
    </template>

    <div class="notify-panel">
      <div class="notify-header">
        <span class="notify-title">通知中心</span>
        <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="readAll">全部已读</el-button>
      </div>

      <el-tabs v-model="activeTab" class="notify-tabs">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane :label="`未读(${unreadCount})`" name="unread" />
      </el-tabs>

      <div class="notify-list" v-loading="loading">
        <div v-for="msg in filteredList" :key="msg.id" class="notify-item" :class="{ unread: !msg.isRead }" @click="onItemClick(msg)">
          <div class="notify-item-header">
            <el-tag size="small" :type="msgTypeTag(msg.msgType)">{{ msgTypeLabel(msg.msgType) }}</el-tag>
            <span class="notify-time">{{ formatTime(msg.createTime) }}</span>
          </div>
          <div class="notify-content">{{ msg.content || msg.title }}</div>
          <div v-if="msg.linkUrl" class="notify-link">前往查看 ›</div>
        </div>
        <el-empty v-if="!loading && filteredList.length === 0" description="暂无通知" :image-size="48" />
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { messageApi, store } from '../api'
import { msgTypeLabel } from '../constants/enums'

const router = useRouter()

const popoverVisible = ref(false)
// 每次展开都重新拉一遍：读过的、别人代读的都不会残留旧角标
watch(popoverVisible, (v) => { if (v) { fetchMessages(); fetchUnread() } })

const activeTab = ref('all')
const loading = ref(false)
const messages = ref([])
const unreadCount = ref(0)
const uid = store.user?.userId ?? store.user?.id

/**
 * 角标规则＝服务端本人未读总数（messageApi.unreadCount），不再用「当前页 20 条里数出来的未读」。
 * 原实现两处各挂一个铃铛：HeaderBadges 那个用服务端总数、这里用页内计数，
 * 未读超过一页时两个数字会打架；现只留这一个铃铛，取权威规则。
 */
async function fetchUnread() {
  if (!uid) return
  try {
    // silent：辅助角标拉取失败不弹全局错误（30s 轮询与下次展开兜底）
    unreadCount.value = await messageApi.unreadCount(uid, { silent: true }) || 0
  } catch { /* 忽略 */ }
}

// 字段名按后端 SysMessage 序列化键取 —— 已读位是 `isRead`（0/1）、类型位是 `msgType`。
// 读 `msg.readFlag`／`msg.category` 两个不存在的键的话（对照系统设置「消息中心」
// 只读视图 MasterConfigTable.vue 用的正是 `isRead`＋`msgType`）⇒ `!undefined === true` 恒真，
// 已读的消息也一律渲染成未读、未读数按整页虚高、未读 tab 与「全部」tab 内容相同。
const filteredList = computed(() => {
  if (activeTab.value === 'unread') return messages.value.filter(m => !m.isRead)
  return messages.value
})

async function fetchMessages() {
  loading.value = true
  try {
    // silent：消息中心为辅助功能，拉取失败不弹全局错误（可重开重试）
    const data = await messageApi.page({ pageNum: 1, pageSize: 20, receiverId: uid }, { silent: true })
    messages.value = data?.records || data?.list || []
  } catch { /* 忽略 */ }
  loading.value = false
}

/**
 * 点击一条通知：先回写已读，再按 `link_url` 跳转。
 *
 * 的后半：`sys_message.link_url` 注释写着「跳转地址（PC/H5共用相对路径）」，
 * 抄送提醒也明确引导「可在『流程中心 > 我的抄送』查看该单」，但本组件只标已读、不消费
 * link_url ⇒ 通知出口只到"知道有这条消息"，被抄送人仍需自己找入口。所以已读＋直达一并补上。
 * 跳转失败（路由表外的历史链接）不打断已读回写，也不弹全局错误。
 */
async function onItemClick(msg) {
  if (!msg.isRead) {
    try {
      await messageApi.read(msg.id)
      msg.isRead = 1
      fetchUnread()
    } catch { /* 忽略：与拉取同一规则，辅助功能不弹错 */ }
  }
  if (msg.linkUrl) {
    popoverVisible.value = false
    try {
      await router.push(msg.linkUrl)
    } catch { /* 忽略：链接指向的路由不存在时保持当前页 */ }
  }
}

async function readAll() {
  try {
    await messageApi.readAll(uid)
    messages.value.forEach(m => { m.isRead = 1 })
    fetchUnread()
  } catch { /* 忽略 */ }
}

/** 类型标签配色：与枚举 MSG_TYPE 的中文标签同源，未登记的新类型落 'info' 直接显示原码 */
function msgTypeTag(t) {
  const map = {
    APPROVAL: 'warning', APPROVAL_URGE: 'warning', CC_NOTICE: 'primary',
    TIMEOUT_REMINDER: 'danger', TIMEOUT_ESCALATED: 'danger', WF_STUCK_ALERT: 'danger',
    ALERT: 'danger', WF_ADDSIGN: 'warning',
    DELEGATE_NEW: 'success', DELEGATE_DONE: 'success', DELEGATE_TAKEOVER: 'success',
    ABNORMAL_INVOICE: 'danger', ARCHIVE_EXPIRE: 'info', DUP_GLOBAL: 'danger', RC_OVERDUE: 'danger',
  }
  return map[t] || 'info'
}

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    const now = new Date()
    const diff = (now - d) / 60000
    if (diff < 1) return '刚刚'
    if (diff < 60) return Math.floor(diff) + '分钟前'
    if (diff < 1440) return Math.floor(diff / 60) + '小时前'
    return d.toLocaleDateString('zh-CN')
  } catch { return t }
}

let unreadTimer = null
onMounted(() => {
  fetchMessages()
  fetchUnread()
  // 角标轮询从 HeaderBadges 搬来（那里已删掉重复铃铛），规则与间隔保持不变
  unreadTimer = setInterval(fetchUnread, 30000)
})
onBeforeUnmount(() => clearInterval(unreadTimer))
</script>

<style scoped>
.notify-panel { min-height: 200px; max-height: 400px; display: flex; flex-direction: column; }
.notify-header {
  display: flex; align-items: center; justify-content: space-between;
  padding-bottom: 8px; border-bottom: 1px solid #ebeef5;
}
.notify-title { font-size: 15px; font-weight: 600; }
.notify-tabs { flex-shrink: 0; }
.notify-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
.notify-list { flex: 1; overflow-y: auto; }
.notify-item {
  padding: 10px; border-bottom: 1px solid #f5f5f5; cursor: pointer;
  transition: background 0.15s;
}
.notify-item:hover { background: #f5f7fa; }
.notify-item.unread { background: #ecf5ff; }
.notify-item-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 4px;
}
.notify-time { font-size: 12px; color: #909399; }
.notify-content { font-size: 13px; color: #303133; line-height: 1.5; }
.notify-link { font-size: 12px; color: #409eff; margin-top: 4px; }
</style>
