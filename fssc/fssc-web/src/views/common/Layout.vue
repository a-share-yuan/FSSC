<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <el-container class="layout">
    <!-- PC 侧边栏（≥768px 显示） -->
    <el-aside width="210px" class="aside desktop-menu">
      <div class="logo"><img class="logo-img" src="../../assets/FSSC_LOGO_Blue.jpeg" alt="FSSC 财务共享" /></div>
      <el-menu
        :default-active="$route.path"
        router
        :background-color="isDark ? '#0f0f23' : '#1f2d3d'"
        :text-color="isDark ? '#c8c9cc' : '#bfcbd9'"
        active-text-color="#409eff"
      >
        <MenuTree :nodes="menuTree" />
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <!-- 移动端菜单按钮 -->
        <el-button v-if="isMobile" link class="hamburger" @click="drawer = true">
          <el-icon :size="20"><Menu /></el-icon>
        </el-button>
        <span class="page-title">{{ $route.meta.title }}</span>

        <!-- Header 右侧功能区 -->
        <div class="header-right">
          <!-- 暗色模式切换 -->
          <el-button link class="header-btn" @click="toggleTheme" :title="$t('layout.main.Toggle')">
            <el-icon :size="18"><Sunny v-if="!isDark" /><Moon v-else /></el-icon>
          </el-button>
          <!-- 快捷键帮助 -->
          <el-button link class="header-btn" @click="kbHelpVisible = true" :title="$t('layout.main.Shortcuts')">
            <el-icon :size="18"><Key /></el-icon>
          </el-button>
          <!-- 待办角标 + 搜索 + 通知 + 收藏 -->
          <HeaderBadges
            @search="searchVisible = true"
            @todo="router.push('/approval')"
            @favorites="favVisible = true"
          />
          <!-- 通知铃铛唯一入口：组件自带弹层触发器，头部不再另挂一个 -->
          <NotificationCenter />

          <el-dropdown @command="onCommand">
            <span class="user">{{ user?.realName || user?.username || '用户' }}</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <!-- 「角色化首页」入口已下架（路由与组件保留，直接访问 /role-dashboard 仍可用）。
                     原因＝四项 KPI 查询键与后端返回不符（恒 0）＋四项为写死的展示值，
                     真要修等于重做整页查询与角色模型，而重做后独有内容与报表看板/流程监控大屏重叠。 -->
                <el-dropdown-item v-if="canFlowMonitor && openable('/flow-monitor')" command="flow-monitor">流程监控大屏</el-dropdown-item>
                <!-- G3 共享作业层新视图：同属「无 sys_menu 承载的入口页」，按各页首屏接口权限码收敛 -->
                <el-dropdown-item v-if="canTaskPool && openable('/sharing/shared-pool')" command="shared-pool">共享任务池（五视图）</el-dropdown-item>
                <el-dropdown-item v-if="canFlowMonitor && openable('/sharing/shared-ops')" command="shared-ops">共享作业控制台</el-dropdown-item>
                <el-dropdown-item v-if="canFlowMonitor && openable('/sharing/task-quality')" command="task-quality">任务质量报表</el-dropdown-item>
                <el-dropdown-item v-if="canFlowMonitor && openable('/flow-center/log')" command="flow-log">流程日志中心</el-dropdown-item>
                <el-dropdown-item v-if="canFlowMonitor && openable('/flow-center/handover')" command="handover">流程移交</el-dropdown-item>
                <el-dropdown-item v-if="canBizProgress && openable('/biz-progress')" command="biz-progress">业务进度看板</el-dropdown-item>
                <el-dropdown-item divided command="my-stats">效率统计</el-dropdown-item>
                <el-dropdown-item command="export-preview">导出预览</el-dropdown-item>
                <el-dropdown-item command="toggle-watermark">{{ watermarkEnabled ? '关闭水印' : '开启水印' }}</el-dropdown-item>
                <el-dropdown-item divided command="onboarding">新手引导</el-dropdown-item>
                <el-dropdown-item command="help">帮助中心</el-dropdown-item>
                <el-dropdown-item v-if="canMonthEnd && openable('/month-end')" command="month-end">月结看板</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 面包屑导航 -->
      <div class="breadcrumb-bar">
        <el-breadcrumb :separator-icon="ArrowRight">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item
            v-for="(item, i) in breadcrumbChain"
            :key="i"
            :to="i < breadcrumbChain.length - 1 && item.path ? { path: item.path } : undefined"
          >{{ item.title }}</el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <!-- 移动端抽屉菜单 -->
      <el-drawer v-model="drawer" direction="ltr" size="70%" :with-header="false" class="mobile-drawer">
        <div class="drawer-logo"><img class="drawer-logo-img" src="../../assets/FSSC_LOGO.png" alt="FSSC 财务共享" /></div>
        <el-menu
          :default-active="$route.path"
          router
          :background-color="isDark ? '#16213e' : '#fff'"
          :text-color="isDark ? '#e0e0e0' : undefined"
          @select="drawer = false"
        >
          <MenuTree :nodes="menuTree" />
        </el-menu>
      </el-drawer>

      <el-main class="main">
        <!-- key=$route.path：防止"同组件不同菜单"（如 银行账户/金融机构 等共用 MasterConfigTable）
             复用同一实例导致旧数据残留、新页签字段全空 -->
        <router-view :key="$route.path" />
      </el-main>
    </el-container>

    <!-- 公告弹窗提醒 -->
    <!-- append-to-body：遮罩独立挂到 body，关闭后彻底移除，避免嵌套在 flex .layout 内时遮罩层残留把整页盖成"内容全没了" -->
    <el-dialog v-model="noticeVisible" title="系统公告" width="520px" :close-on-click-modal="false" append-to-body :close-on-press-escape="true" @closed="onNoticeClosed">
      <template v-if="currentNotice">
        <h3 style="margin: 0 0 8px">{{ currentNotice.noticeTitle }}</h3>
        <div style="color: var(--app-text-secondary, #666); font-size: 13px; margin-bottom: 12px">
          发布时间：{{ currentNotice.publishTime }}
        </div>
        <div style="white-space: pre-wrap; line-height: 1.6">{{ currentNotice.noticeContent }}</div>
      </template>
      <template #footer>
        <el-button @click="closeNotice">关闭</el-button>
        <el-button v-if="notices.length > 1" type="primary" @click="nextNotice">下一条 ({{ currentIdx + 1 }}/{{ notices.length }})</el-button>
      </template>
    </el-dialog>

    <!-- 全局搜索 Ctrl+K -->
    <SearchCommand v-model:visible="searchVisible" />

    <!-- 收藏夹 -->
    <Favorites v-model="favVisible" ref="favoritesRef" />

    <!-- 个人效率统计 -->
    <MyStats v-model="statsVisible" />

    <!-- 全局水印 -->
    <Watermark ref="watermarkRef" :enabled="watermarkEnabled" />

    <!-- 导出预览 -->
    <ExportPreview v-model="exportVisible" :columns="exportColumns" :rows="exportRows" :title="exportTitle" />

    <!-- 快捷键帮助弹窗 -->
    <el-dialog v-model="kbHelpVisible" title="键盘快捷键" width="480px" :destroy-on-close="true">
      <el-table :data="kbShortcuts" size="small" border empty-text="暂无数据" v-loading="loading">
        <el-table-column prop="key" label="快捷键" width="140" />
        <el-table-column prop="desc" label="功能说明" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <!-- 新手引导 -->
    <OnboardingGuide ref="onboardingRef" />

    <!-- 上下文帮助面板 -->
    <HelpPanel v-model:visible="helpVisible" />

    <!-- AI 智能助手 -->
    <AIAssistant />

    <!-- 操作引导气泡 -->
    <CoachMark ref="coachMarkRef" />

    <!-- 弱网降级小通知（右下角，不影响浏览） -->
    <Transition name="net-slide">
      <div v-if="isSlowNetwork && !netNoticeDismissed" class="net-notice" :class="degradationLevel">
        <span class="net-notice-icon">{{ degradationLevel === 'severe' ? '' : '' }}</span>
        <span class="net-notice-text">
          {{ degradationLevel === 'severe' ? '网络较差，已开启重度降级' : '网络较慢，已优化加载速度' }}
        </span>
        <span class="net-notice-close" @click="netNoticeDismissed = true">✕</span>
      </div>
    </Transition>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Key, Sunny, Moon } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { store, logout, noticeApi } from '../../api'
import { setMenuTree, ensureMenuTree, collapseSingleLeafGroups, pathDenyReason } from '../../router/pathAccess'
import MenuTree from '../../components/MenuTree.vue'
import HeaderBadges from '../../components/HeaderBadges.vue'
import SearchCommand from '../../components/SearchCommand.vue'
import NotificationCenter from '../../components/NotificationCenter.vue'
import Favorites from '../../components/Favorites.vue'
import MyStats from '../../components/MyStats.vue'
import Watermark from '../../components/Watermark.vue'
import ExportPreview from '../../components/ExportPreview.vue'
import OnboardingGuide from '../../components/OnboardingGuide.vue'
import HelpPanel from '../../components/HelpPanel.vue'
import AIAssistant from '../../components/AIAssistant.vue'
import CoachMark from '../../components/CoachMark.vue'
import { useKeyboard } from '../../composables/useKeyboard'
import { useTheme } from '../../composables/useTheme'
import { useNetworkDegradation } from '../../composables/useNetworkDegradation'
import { hasPerm } from '../../utils/perm'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const user = store.user
const drawer = ref(false)
const isMobile = ref(false)
const menuTree = ref([])
const searchVisible = ref(false)

// 弱网降级
const { isSlowNetwork, degradationLevel, degradationConfig } = useNetworkDegradation()
const netNoticeDismissed = ref(false)
const favVisible = ref(false)
const favoritesRef = ref(null)
const statsVisible = ref(false)
const exportVisible = ref(false)
const exportColumns = ref([])
const exportRows = ref([])
const exportTitle = ref('报表')
const watermarkRef = ref(null)
const watermarkEnabled = ref(loadWatermarkPref())

function loadWatermarkPref() {
  try { return localStorage.getItem('fssc_watermark_enabled') !== 'false' } catch { return true }
}
const kbHelpVisible = ref(false)
const loading = ref(false)
const helpVisible = ref(false)
const onboardingRef = ref(null)
const coachMarkRef = ref(null)

// 暗色模式
const { isDark, toggle: toggleTheme } = useTheme()

/**
 * 全局入口页权限门控
 *
 * 用户下拉菜单里的「流程监控大屏」「月结看板」是没有菜单承载的入口页
 * （sys_menu 中无任何 path/component 指向它们），因此不受菜单权限体系约束。
 * 对全员无条件渲染的话 → 无权限者点进去即弹「缺少权限：xxx」。
 * 这里按各自页面首屏接口的真实权限码收敛。
 */
const canFlowMonitor = hasPerm('wf:instance:list')
const canMonthEnd = hasPerm('gl:monthclose:list')
/** 入口是否真能打开：与路由守卫同一把钥匙（一个权限码管六个不同判据的页面会给出点得动却被弹回的假入口） */
const openable = (p) => !pathDenyReason(p)
// G3 任务池五视图是业务角色日常工作台，权限码为 wf:task:list（与 /wf/pool/mine 同族）
const canTaskPool = hasPerm('wf:task:list')
// 业务进度看板的两条查询环节分别是 dashboard:view（付款进度）与 exp:bill:list（在途单量），
// 两码皆无者进来只会看到空卡，故入口同样按首屏接口权限码收敛。
const canBizProgress = hasPerm('dashboard:view') || hasPerm('exp:bill:list')

// 快捷键（Alt+4 仅在持 wf:instance:list 时展示，避免"按了没反应"的困惑）
const kbShortcuts = computed(() => [
  { key: 'Ctrl + K', desc: '全局搜索' },
  { key: 'Ctrl + /', desc: '快捷键帮助' },
  { key: 'Ctrl + D', desc: '收藏当前页' },
  { key: 'Ctrl + B', desc: '收藏夹' },
  { key: 'Ctrl + E', desc: '效率统计' },
  { key: 'Ctrl + H', desc: '切换深/浅色模式' },
  { key: 'Alt + 1', desc: '首页 Dashboard' },
  { key: 'Alt + 2', desc: '我的审批' },
  { key: 'Alt + 3', desc: '任务池' },
  ...(canFlowMonitor ? [{ key: 'Alt + 4', desc: '流程监控大屏' }] : []),
  { key: 'F1', desc: '帮助中心' },
  { key: 'Esc', desc: '关闭弹窗/抽屉' },
])

useKeyboard({
  'ctrl+/': () => { kbHelpVisible.value = true },
  'ctrl+d': () => { addCurrentPageToFav() },
  'ctrl+b': () => { favVisible.value = true },
  'ctrl+e': () => { statsVisible.value = true },
  'ctrl+h': () => { toggleTheme() },
  'f1': () => { helpVisible.value = true },
  'alt+1': () => { if (openable('/dashboard')) router.push('/dashboard') },
  'alt+2': () => { if (openable('/approval')) router.push('/approval') },
  'alt+3': () => { if (openable('/sharing/task-pool')) router.push('/sharing/task-pool') },
  'alt+4': () => { if (canFlowMonitor && openable('/flow-monitor')) router.push('/flow-monitor') },
})

function addCurrentPageToFav() {
  if (!favoritesRef.value) return
  favoritesRef.value.addFavorite({
    id: 'menu-' + route.path,
    type: 'menu',
    name: route.meta.title || route.path,
    path: route.path,
  })
  ElMessage.success(t('layout.main.Current'))
}

// 面包屑以登录菜单树为准（菜单挂在哪，面包屑就走哪），仅菜单树无匹配时按路径首段兜底
const PATH_DOMAIN_MAP = {
  expense: '费用报账', approval: '流程中心', wf: '流程中心',
  pay: '付款与对账', 'corp-pay': '付款与对账', contract: '付款与对账',
  purchase: '采购管理', invoice: '发票管理',
  sharing: '共享运营',
  system: '系统管理',
  budget: '预算管理', asset: '财务核算', treasury: '财务核算',
  receivable: '财务核算', archive: '财务核算',
  report: '管理报表', reports: '管理报表',
  op: '财务核算', glex: '财务核算', bank: '财务核算', customer: '主数据管理',
  // 审计轨迹/会计科目等页面已从左侧菜单软删（sys_menu 3056/205），只能深链进入，
  // 菜单树匹配不到父链时靠这里补模块名，否则面包屑只剩「首页›当前页」，看不出归属。
  audit: '系统监控', compliance: '系统监控', master: '系统主数据',
}

function normMenuPath(p) {
  if (!p) return ''
  const s = String(p).split('?')[0].trim()
  if (!s) return ''
  return ('/' + s.replace(/^\/+/, '')).replace(/\/+$/, '') || '/'
}

function findMenuChain(nodes, targetPath) {
  let best = null
  const walk = (list, ancestors) => {
    for (const n of list || []) {
      const chain = [...ancestors, n]
      const p = normMenuPath(n.path)
      if (p && p === targetPath) return chain
      if (p && (targetPath === p || targetPath.startsWith(p + '/'))) {
        if (!best || p.length > normMenuPath(best[best.length - 1].path).length) best = chain
      }
      const hit = walk(n.children, chain)
      if (hit) return hit
    }
    return null
  }
  return walk(nodes, []) || best
}

const breadcrumbChain = computed(() => {
  const target = normMenuPath(route.path)
  const chain = findMenuChain(menuTree.value, target)
  const seg = route.path.split('/')[1]
  const domain = PATH_DOMAIN_MAP[seg]
  const title = route.meta.title || '当前页'
  if (chain && chain.length) {
    const items = chain.map((n) => ({ title: n.menuName, path: normMenuPath(n.path) }))
    // 菜单树只命中单级（该页在菜单里是根节点、或父级被裁剪）时补模块名，避免只剩「首页›当前页」
    if (items.length === 1 && domain && domain !== items[0].title) items.unshift({ title: domain })
    return items
  }
  return domain && domain !== title ? [{ title: domain }, { title }] : [{ title }]
})

// 公告弹窗
const noticeVisible = ref(false)
const notices = ref([])
const currentIdx = ref(0)
const currentNotice = computed(() => notices.value[currentIdx.value] || null)

function closeNotice() {
  noticeVisible.value = false
  notices.value = []
  currentIdx.value = 0
}
// 任意关闭路径（X / ESC / 关闭按钮）统一兜底清理，避免残留状态
function onNoticeClosed() {
  notices.value = []
  currentIdx.value = 0
}
function nextNotice() {
  if (currentIdx.value < notices.value.length - 1) {
    currentIdx.value++
  } else {
    closeNotice()
  }
}

const mq = window.matchMedia('(max-width: 767px)')
function onMq(e) { isMobile.value = e.matches }

onMounted(async () => {
  isMobile.value = mq.matches
  mq.addEventListener('change', onMq)
  try {
    // 菜单树走共享缓存：守卫/导航地图要的是同一棵树，不要各拉各的（那条联表是全站最贵的一枚）
    const raw = await ensureMenuTree()
    if (raw) {
      // 只挂一个页面的分组就地收敛：侧栏与面包屑共用这一份，两处层级一致
      const collapsed = collapseSingleLeafGroups(raw)
      menuTree.value = collapsed
      // 回灌给页面可达性判据（router/pathAccess），避免入口卡与守卫各判一套
      setMenuTree(collapsed)
    }
  } catch (e) {
    // 菜单树拉取失败不阻塞页面
    console.warn('菜单树加载失败', e.message)
  }
  // 加载公告弹窗
  try {
    const list = await noticeApi.active()
    if (list && list.length > 0) {
      notices.value = list
      currentIdx.value = 0
      noticeVisible.value = true
    }
  } catch (e) {
    // 公告加载失败不阻塞
    console.warn('公告加载失败', e.message)
  }
  // 新手引导仅从下拉菜单手动触发，不自动弹出（全屏遮罩会阻断用户操作）
})
onBeforeUnmount(() => mq.removeEventListener('change', onMq))

async function onCommand(cmd) {
  if (cmd === 'logout') {
    try { await logout() } catch (e) { /* 忽略后端退出失败，前端仍清理 */ }
    store.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'flow-monitor') {
    router.push('/flow-monitor')
  } else if (cmd === 'shared-pool') {
    router.push('/sharing/shared-pool')
  } else if (cmd === 'shared-ops') {
    router.push('/sharing/shared-ops')
  } else if (cmd === 'task-quality') {
    router.push('/sharing/task-quality')
  } else if (cmd === 'flow-log') {
    router.push('/flow-center/log')
  } else if (cmd === 'handover') {
    router.push('/flow-center/handover')
  } else if (cmd === 'biz-progress') {
    router.push('/biz-progress')
  } else if (cmd === 'my-stats') {
    statsVisible.value = true
  } else if (cmd === 'export-preview') {
    exportVisible.value = true
  } else if (cmd === 'toggle-watermark') {
    watermarkEnabled.value = !watermarkEnabled.value
    localStorage.setItem('fssc_watermark_enabled', String(watermarkEnabled.value))
    ElMessage.success(t('layout.main.Watermark'))
  } else if (cmd === 'onboarding') {
    onboardingRef.value?.start()
  } else if (cmd === 'help') {
    helpVisible.value = true
  } else if (cmd === 'month-end') {
    router.push('/month-end')
  }
}
</script>

<style scoped>
.layout { height: 100%; }
.aside { background: var(--app-sidebar-bg, #1f2d3d); }
.desktop-menu { display: none; }
.logo { height: 56px; display: flex; align-items: center; justify-content: center; }
.logo-img { height: 40px; border-radius: 8px; object-fit: contain; }
.aside :deep(.el-menu) { border-right: none; }
.header {
  display: flex; align-items: center;
  background: var(--app-header-bg, #fff);
  color: var(--app-text, #303133);
  border-bottom: 1px solid var(--app-border, #e4e7ed);
  height: 56px; padding: 0 16px; gap: 8px;
}
.page-title { font-size: 16px; font-weight: 600; flex: 1; }
.header-right { display: flex; align-items: center; gap: 8px; }
.header-btn { color: var(--app-text-secondary, #606266); }
.header-btn:hover { color: #409eff; }
.user { cursor: pointer; color: #409eff; }
.breadcrumb-bar {
  padding: 8px 16px;
  background: var(--app-bg, #f5f7fa);
  border-bottom: 1px solid var(--app-border, #e4e7ed);
}
.main {
  padding: 16px;
  background: var(--app-bg, #f5f7fa);
  color: var(--app-text, #303133);
}
.drawer-logo { padding: 12px 16px; }
.drawer-logo-img { height: 40px; object-fit: contain; }

/* ≥768px 显示侧边栏 */
@media (min-width: 768px) {
  .desktop-menu { display: block; }
}
@media (max-width: 767px) {
  .main { padding: 10px; }
  .page-title { font-size: 15px; }
}

/* 弱网降级小通知 */
.net-notice {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  color: #856404;
  background: #fff3cd;
  border: 1px solid #ffe082;
  box-shadow: 0 2px 8px rgba(0,0,0,0.12);
  max-width: 320px;
}
.net-notice.severe {
  color: #721c24;
  background: #f8d7da;
  border-color: #f5c6cb;
}
.net-notice-icon { font-size: 15px; }
.net-notice-text { flex: 1; line-height: 1.4; }
.net-notice-close {
  cursor: pointer;
  opacity: 0.6;
  font-size: 14px;
}
.net-notice-close:hover { opacity: 1; }
.net-slide-enter-active, .net-slide-leave-active {
  transition: all 0.3s ease;
}
.net-slide-enter-from, .net-slide-leave-to {
  opacity: 0;
  transform: translateX(40px);
}
</style>
