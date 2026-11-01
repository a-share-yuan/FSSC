<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <Teleport to="body">
    <div v-if="visible" class="search-overlay" @click.self="close">
      <div class="search-panel">
        <div class="search-input-bar">
          <el-icon class="search-icon"><Search /></el-icon>
          <input
            ref="inputRef"
            v-model="keyword"
            class="search-input"
            placeholder="搜索功能菜单 / 我的单据号 (Ctrl+K)"
            @input="onInput"
            @keydown.enter="onEnter"
            @keydown.esc="close"
            @keydown.down.prevent="moveCursor(1)"
            @keydown.up.prevent="moveCursor(-1)"
          />
          <kbd class="kbd-hint">ESC</kbd>
        </div>

        <div v-if="keyword && filteredResults.length > 0" class="search-results">
          <template v-for="(group, gi) in groupedResults" :key="group.label">
            <div v-if="group.items.length" class="result-group">
              <div class="group-label">{{ group.label }}</div>
              <div
                v-for="(item, ii) in group.items"
                :key="item.key"
                class="result-item"
                :class="{ active: flatIndex === getFlatIndex(gi, ii) }"
                @click="selectItem(item)"
                @mouseenter="flatIndex = getFlatIndex(gi, ii)"
              >
                <el-icon class="item-icon"><component :is="item.icon || Document" /></el-icon>
                <span class="item-label" v-html="highlight(item.label)"></span>
                <span class="item-desc">{{ item.desc || '' }}</span>
              </div>
            </div>
          </template>
        </div>

        <div v-else-if="keyword && filteredResults.length === 0" class="search-empty">
          <el-empty description="未找到匹配结果" :image-size="60" />
        </div>

        <div v-else class="search-hint">
          <div class="hint-section">
            <span class="hint-title">快捷操作</span>
            <div class="hint-items">
              <span class="hint-item" @click="quickAction('expense')">新建报销单</span>
              <span class="hint-item" @click="quickAction('loan')">新建借款单</span>
              <span class="hint-item" @click="quickAction('approval')">查看待审批</span>
              <span class="hint-item" @click="quickAction('dashboard')">经营看板</span>
            </div>
          </div>
          <div class="hint-footer">
            <span><kbd>↑</kbd><kbd>↓</kbd> 导航</span>
            <span><kbd>Enter</kbd> 选择</span>
            <span><kbd>Esc</kbd> 关闭</span>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Document, Tickets, Money, DataAnalysis, Setting, Files } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { billApi } from '../api'
import { goBizPage } from '../utils/bizEntry'
import { docTypeLabel } from '../constants/enums'
import { pathDenyReason } from '../router/pathAccess'

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['update:visible'])

const router = useRouter()
const keyword = ref('')
const inputRef = ref(null)
const flatIndex = ref(0)
const billResults = ref([])
let debounceTimer = null

// 菜单索引（静态路由列表）
const menuIndex = [
  { key: 'm-dashboard', label: '报表看板', desc: 'Dashboard', icon: DataAnalysis, path: '/dashboard', group: 'menu' },
  { key: 'm-requisition', label: '报账工作台', desc: '统一收单', icon: Tickets, path: '/requisition', group: 'menu' },
  { key: 'm-workbench', label: '我的工作台', desc: '个人工作台', icon: Document, path: '/workbench', group: 'menu' },
  { key: 'm-expense', label: '报销管理', desc: '费用报账', icon: Money, path: '/expense', group: 'menu' },
  { key: 'm-loan', label: '借款管理', desc: '借款/还款', icon: Money, path: '/expense/loan', group: 'menu' },
  { key: 'm-approval', label: '审批工作台', desc: '待办审批', icon: Document, path: '/approval', group: 'menu' },
  { key: 'm-contract', label: '合同管理', desc: '合同台账', icon: Files, path: '/contract', group: 'menu' },
  { key: 'm-pay', label: '对公支付', desc: '付款管理', icon: Money, path: '/pay', group: 'menu' },
  { key: 'm-budget', label: '预算明细', desc: '预算编制执行', icon: DataAnalysis, path: '/budget/item', group: 'menu' },
  { key: 'm-invoice', label: '发票管理', desc: '进项/销项发票', icon: Document, path: '/invoice', group: 'menu' },
  { key: 'm-archive', label: '档案管理', desc: '凭证归档', icon: Files, path: '/archive', group: 'menu' },
  { key: 'm-system-user', label: '用户管理', desc: '系统管理', icon: Setting, path: '/system/user', group: 'menu' },
  { key: 'm-system-role', label: '角色管理', desc: '权限配置', icon: Setting, path: '/system/role', group: 'menu' },
  { key: 'm-system-org', label: '组织管理', desc: '公司/部门/岗位', icon: Setting, path: '/system/org', group: 'menu' },
]

const filteredResults = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return []
  const results = []

  // 菜单匹配：只列本账号真能打开的页面（与路由守卫同一把钥匙），
  // 否则搜得到、点进去被弹回工作台并提示无权限。
  const menuHits = menuIndex.filter(m =>
    !pathDenyReason(m.path) && (m.label.toLowerCase().includes(kw) || m.desc.toLowerCase().includes(kw))
  ).map(m => ({ ...m, key: m.key, label: m.label, group: 'menu' }))
  if (menuHits.length) results.push(...menuHits)

  // 单据匹配：/myPage 接口不认 keyword（后端无该参数，任意输入都返回同一页），
  // 因此必须在本地按单号/标题二次过滤，否则会把无关单据当成命中结果。
  const billHits = billResults.value.filter(b =>
    String(b.billNo || '').toLowerCase().includes(kw) || String(b.title || '').toLowerCase().includes(kw)
  ).map(b => ({
    key: 'bill-' + b.id,
    label: b.billNo || b.title || ('单据#' + b.id),
    desc: docTypeLabel(b.billType),
    icon: Document,
    bill: { ...b, billId: b.id },
    group: 'bill',
  }))
  if (billHits.length) results.push(...billHits)

  return results
})

const groupedResults = computed(() => {
  const groups = [
    { label: '功能菜单', items: [] },
    { label: '单据', items: [] },
  ]
  const gMap = { menu: 0, bill: 1 }
  for (const r of filteredResults.value) {
    const gi = gMap[r.group] ?? 0
    if (groups[gi]) groups[gi].items.push(r)
  }
  return groups.filter(g => g.items.length)
})

function getFlatIndex(gi, ii) {
  let idx = 0
  for (let i = 0; i < gi; i++) idx += groupedResults.value[i].items.length
  return idx + ii
}

function moveCursor(dir) {
  const total = filteredResults.value.length
  if (total === 0) return
  flatIndex.value = (flatIndex.value + dir + total) % total
}

function selectItem(item) {
  // 单据结果走 goBizPage 分发器：按 billType 解析落地业务页并带 billNo/billId 深链。
  // 不能硬写 /expense?billId= —— /expense 会 redirect 到新建表单页，且不认 billId 参数，
  // 跳过去只会得到一张空白新单（「搜到单据却看不到单据」的断链）。
  if (item.bill) {
    if (!goBizPage(router, item.bill)) {
      ElMessage.warning('当前账号不能直接进入该单据的业务页面，请用行内「追溯」查看全链路')
      return
    }
    close()
    return
  }
  if (item.path) router.push(item.path)
  close()
}

function onEnter() {
  const item = filteredResults.value[flatIndex.value]
  if (item) selectItem(item)
}

function onInput() {
  flatIndex.value = 0
  clearTimeout(debounceTimer)
  const kw = keyword.value.trim()
  if (!kw || kw.length < 2) { billResults.value = []; return }
  debounceTimer = setTimeout(async () => {
    try {
      const data = await billApi.myPage({ pageNum: 1, pageSize: 10, keyword: kw })
      billResults.value = data?.records || data?.list || []
    } catch { billResults.value = [] }
  }, 300)
}

function highlight(text) {
  if (!keyword.value) return text
  const kw = keyword.value.trim()
  if (!kw) return text
  const reg = new RegExp('(' + kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'gi')
  return text.replace(reg, '<em>$1</em>')
}

function quickAction(action) {
  const map = {
    expense: '/expense',
    loan: '/expense/loan',
    approval: '/approval',
    dashboard: '/dashboard',
  }
  if (map[action]) router.push(map[action])
  close()
}

function close() {
  keyword.value = ''
  billResults.value = []
  flatIndex.value = 0
  emit('update:visible', false)
}

function onKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    emit('update:visible', true)
    nextTick(() => inputRef.value?.focus())
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

watch(() => props.visible, (v) => {
  if (v) nextTick(() => inputRef.value?.focus())
})
</script>

<style scoped>
.search-overlay {
  position: fixed; inset: 0; z-index: 9999;
  background: rgba(0,0,0,0.5);
  display: flex; justify-content: center; align-items: flex-start;
  padding-top: 12vh;
}
.search-panel {
  width: 600px; max-width: 90vw;
  background: var(--app-card-bg, #fff); border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
  overflow: hidden;
}
.search-input-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 20px; border-bottom: 1px solid var(--app-border, #e4e7ed);
}
.search-icon { color: #909399; font-size: 18px; }
.search-input {
  flex: 1; border: none; outline: none;
  font-size: 15px; background: transparent;
}
.kbd-hint {
  font-size: 11px; color: #909399;
  background: var(--app-bg, #f5f7fa); border: 1px solid var(--app-border, #dcdfe6);
  border-radius: 4px; padding: 2px 6px;
}
.search-results { max-height: 400px; overflow-y: auto; }
.result-group { padding: 6px 0; }
.group-label {
  font-size: 12px; color: #909399; font-weight: 600;
  padding: 4px 20px;
}
.result-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 20px; cursor: pointer; transition: background 0.15s;
}
.result-item.active { background: var(--app-hover, #ecf5ff); }
.item-icon { color: #409eff; font-size: 16px; flex-shrink: 0; }
.item-label { font-size: 14px; }
.item-label :deep(em) { color: #409eff; font-style: normal; font-weight: 600; }
.item-desc { font-size: 12px; color: #909399; margin-left: auto; }
.search-empty { padding: 20px; }
.search-hint { padding: 16px 20px; }
.hint-section { margin-bottom: 12px; }
.hint-title { font-size: 12px; color: #909399; font-weight: 600; }
.hint-items { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.hint-item {
  font-size: 13px; color: #409eff; cursor: pointer;
  background: var(--app-hover, #ecf5ff); border-radius: 6px; padding: 6px 12px;
  transition: background 0.15s;
}
.hint-item:hover { background: var(--app-hover, #d9ecff); }
.hint-footer {
  display: flex; gap: 16px; justify-content: flex-end;
  border-top: 1px solid var(--app-border, #e4e7ed); padding-top: 10px;
  font-size: 12px; color: #909399;
}
.hint-footer kbd {
  font-size: 11px; background: var(--app-bg, #f5f7fa); border: 1px solid #dcdfe6;
  border-radius: 3px; padding: 1px 5px; margin-right: 2px;
}
</style>
