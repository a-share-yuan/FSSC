<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="nav-map">
    <!-- 顶部说明 -->
    <div class="map-header">
      <div>
        <h2 class="map-title">{{ $t('dashboard.processNavMap.Workflow') }}</h2>
        <p class="map-sub">7 大业务域端到端链路 · 点任意节点直达对应菜单页面 · 底层按业务单号串联成连续数据链</p>
      </div>
      <div class="map-legend">
        <span class="lg lg-start">{{ $t('common.start') }}</span>
        <span class="lg lg-review">{{ $t('dashboard.processNavMap.Review') }}</span>
        <span class="lg lg-account">{{ $t('dashboard.processNavMap.Post') }}</span>
        <span class="lg lg-close">{{ $t('common.closeout') }}</span>
        <el-button size="small" @click="goTraceDemo">{{ $t('dashboard.processNavMap.View') }}</el-button>
      </div>
    </div>

    <!-- 按角色聚焦（复用 RoleDashboard 角色枚举；当前前端未绑定真实登录角色，故本地切换） -->
    <div class="role-filter">
      <span class="rf-label">{{ $t('dashboard.processNavMap.Focus') }}</span>
      <el-radio-group v-model="roleFilter" size="small">
        <el-radio-button label="ALL">全部域</el-radio-button>
        <el-radio-button label="OPERATOR">操作员</el-radio-button>
        <el-radio-button label="SUPERVISOR">主管</el-radio-button>
        <el-radio-button label="CFO">财务总监</el-radio-button>
        <el-radio-button label="ADMIN">管理员</el-radio-button>
      </el-radio-group>
      <span v-if="roleFilter !== 'ALL'" class="rf-tip">
        已按【{{ roleLabel[roleFilter] }}】聚焦 {{ visibleDomains.length }} 个域，其余已隐藏；点「全部域」恢复完整地图
      </span>
    </div>

    <!-- 贯穿全程控制点 -->
    <el-alert type="info" :closable="false" class="ctrl-strip">
      <template #title>
        <span class="ctrl-title">{{ $t('dashboard.processNavMap.Control') }}</span>
        <span class="ctrl-items">
          <b :class="!canAccess('/budget/occupy') ? 'no-perm' : ''" @click="go('/budget/occupy','预算占用')">{{ $t('dashboard.processNavMap.Budget') }}</b>
          <b :class="!canAccess('/compliance/fraud') ? 'no-perm' : ''" @click="go('/compliance/fraud','反欺诈风控')">{{ $t('compliance.fraud.Anti') }}</b>
          <b :class="!canAccess('/image/review') ? 'no-perm' : ''" @click="go('/image/review','影像核对')">{{ $t('dashboard.processNavMap.Image') }}</b>
          <b :class="!canAccess('/sharing/quality') ? 'no-perm' : ''" @click="go('/sharing/quality','SLA 质检')">{{ $t('dashboard.processNavMap.SLA') }}</b>
          <b :class="!canAccess('/sharing/task-pool') ? 'no-perm' : ''" @click="go('/sharing/task-pool','共享任务池')">{{ $t('dashboard.processNavMap.Shared') }}</b>
        </span>
      </template>
    </el-alert>

    <!-- 7 大域卡片 -->
    <div class="domain-grid">
      <div v-for="d in visibleDomains" :key="d.key" class="domain-card" :style="{ '--c': d.color }">
        <div class="domain-head">
          <el-icon :size="20"><component :is="d.icon" /></el-icon>
          <span class="domain-name">{{ d.name }}</span>
        </div>
        <div class="domain-body">
          <div v-for="st in d.stages" :key="st.stage" class="stage">
            <div class="stage-label" :class="stageClass(st.stage)">{{ st.stage }}</div>
            <div class="steps">
              <div
                v-for="(s, i) in st.steps"
                :key="s.route"
                class="step"
                :class="[stageClass(st.stage), !canAccess(s.route) ? 'no-perm' : '']"
                @click="go(s.route, s.page)"
                :title="(!canAccess(s.route) ? '无访问权限 · ' : '') + s.menu + ' → ' + s.page"
              >
                <span class="step-idx">{{ i + 1 }}</span>
                <span class="step-text">
                  <span class="step-menu">{{ s.menu }}</span>
                  <span class="step-page">{{ s.page }} <el-icon class="step-go"><TopRight /></el-icon></span>
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ShoppingCart, Money, Wallet, CreditCard, Box, Document, Operation, TopRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { hasAnyPerm, isSuperPerm } from '../../utils/perm'
import { ensureMenuTree } from '../../router/pathAccess'
import BillTraceDrawer from '../../components/BillTraceDrawer.vue'

const router = useRouter()
const traceVisible = ref(false)
const traceBillId = ref(null)

// 路由 → 权限码（降级兜底用）。主判定已改为「用户菜单树同源」（见 canAccess），
// 仅当菜单树加载失败时才按本表权限码放行；数组表示「任一满足即可」。
// 数据来源：sys_menu.path/perms 的映射。空数组 = 菜单未挂权限码，全员可进。
// 未出现在本表的路由（treasury/*、budget/execution、expense/apply、approval 等）不在此处拦截，
// 交由目标页自身守卫处理，避免误拦。
const ROUTE_PERM = {
  '/contract': ['contract:list'],
  '/purchase/order': ['po:order:list', 'md:supplier:list'],
  '/purchase/receipt': ['po:receipt:list', 'md:supplier:list', 'po:order:list'],
  '/invoice/query': ['inv:list'],
  '/invoice/create': ['inv:create'],
  '/invoice/verify': ['inv:verify:list'],
  '/invoice/certify': ['inv:certify'],
  '/image': ['image:batch:list'],
  '/pay/match': [],
  '/corp-pay/settle': ['exp:pay:submit'],
  '/corp-pay/prepay': ['exp:pay:submit'],
  '/sharing/task-pool': ['sharing:task:list'],
  '/sharing/task-work': ['sharing:task:list'],
  '/pay': ['op:pay:list'],
  '/corp-pay/voucher': ['voucher:list:page'],
  '/sharing/archive': ['op:archive:list'],
  '/reports/ap-aging': ['report:view'],
  '/reports/supplier-pay': ['report:view'],
  '/reports/input-invoice': ['report:view'],
  '/expense/claim': ['exp:bill:submit'],
  '/expense/claim-travel': ['exp:bill:submit'],
  '/expense/loan': ['exp:bill:list'],
  '/expense/repay': [],
  '/reports/summary': ['report:view'],
  '/reports/loan': ['report:view'],
  '/reports/budget': ['report:view'],
  // 应收工作台页签级页面：一域一台后无独立菜单行，码值与 ArFlowWorkspace.TAB_PERMS 同源
  '/arflow/billing': ['arflow:billing:manage'],
  '/arflow/claims': ['arflow:claim:manage'],
  '/arflow/writeoff': ['arflow:writeoff:manage'],
  '/arflow/notes': ['arflow:note:list'],
  '/arflow/bad-debt': ['arflow:baddebt:manage'],
  '/arflow/reports': ['arflow:report:view'],
  '/arflow/collab': ['collab:scp:list'],
  '/arflow/base-config': ['arflow:baseconfig:manage'],
  '/arflow/ar-month-close': ['arflow:monthclose:manage'],
  '/reports/ar-aging': ['report:view'],
  '/bank/reconcile': ['bank:reconcile:list'],
  '/bank/slip': ['bank:reconcile:list'],
  '/bank/reconcile-sheet': ['bank:reconcile:list'],
  '/reports/cash-flow': ['report:view'],
  '/reports/bank-balance': ['report:view'],
  '/asset/depr-batch': ['asset:list', 'asset:update'],
  '/report/summary': ['report:summary:list'],
  '/budget/scheme': ['budget:scheme:list'],
  '/budget/item': ['budget:item:list', 'system:dept:list'],
  '/budget/adjust': ['budget:adjust:list', 'budget:scheme:list'],
  '/budget/occupy': ['budget:occupy:list'],
  '/compliance/fraud': ['compliance:fraud:list'],
  '/glex/tax-refund': ['gl:taxrefund:list', 'gl:taxrefund:create', 'gl:taxrefund:approve'],
  '/glex/fx-reval': ['glex:fxreval:list', 'glex:fxreval:run'],
  '/reports/tax-burden': ['report:view'],
  '/reports/budget-alert': ['report:view'],
  '/image/review': ['image:review'],
  '/sharing/quality': ['op:quality:list'],
  // —— 父菜单携带权限的二级路由（菜单 path 仅到父级）——
  '/op/gl/voucher': ['gl:entry:list', 'md:subject:list'],
  '/op/gl/entry': ['gl:entry:list', 'md:subject:list'],
  '/op/gl/balance': ['gl:entry:list', 'md:subject:list'],
  '/op/gl/trial': ['gl:entry:list', 'md:subject:list'],
  '/expense/my': ['exp:bill:list'], // 遗留路由，已不再使用；真实入口为 /requisition/mine
  '/expense/voucher': [], // 孤儿路由，已从 expense.js 删除
  '/receivable/receivable': ['receivable:list'],
  '/receivable/receipt': ['receivable:list'],
  '/receivable/collect': ['receivable:list'],
  // 资金管理各页：菜单只挂父项 /treasury，子页无独立菜单行 ⇒ 码值同 routePerms.js
  '/treasury/plan': ['treasury:fundplan:list', 'treasury:creditbill:list'],
  '/treasury/credit': ['treasury:fundplan:list', 'treasury:creditbill:list'],
  '/treasury/transfer': ['treasury:fundplan:list', 'treasury:creditbill:list'],
  '/treasury/settle': ['treasury:fundplan:list', 'treasury:creditbill:list'],
  '/treasury/position': ['treasury:fundplan:list', 'treasury:creditbill:list'],
  '/budget/execution': ['budget:scheme:list'],
  '/asset/asset': ['asset:list', 'inventory:list'],
  '/asset/category': ['asset:list', 'inventory:list'],
  '/asset/inventory': ['asset:list', 'inventory:list'],
  '/asset/taxDiff': ['asset:list', 'inventory:list'],
  '/glex/special/tax': ['gl:taxprov:list', 'gl:salary:list', 'gl:inter:list'],
  '/glex/special/salary': ['gl:taxprov:list', 'gl:salary:list', 'gl:inter:list'],
  '/glex/special/monthclose': ['gl:taxprov:list', 'gl:salary:list', 'gl:inter:list'],
  '/glex/special/consolidate': ['gl:taxprov:list', 'gl:salary:list', 'gl:inter:list'],
}

// 当前用户可见菜单 path 集合（与左侧菜单栏同源：/tenant/system/menu/tree）。
// null = 尚未加载完成/加载失败 → canAccess 降级为权限码判定。
const menuPaths = ref(null)
onMounted(async () => {
  try {
    const tree = await ensureMenuTree()
    const set = new Set()
    const walk = (nodes) => (nodes || []).forEach((n) => {
      if (n.path) set.add(n.path)
      walk(n.children)
    })
    walk(tree)
    menuPaths.value = set
  } catch (e) {
    menuPaths.value = null
  }
})

// 不在菜单体系的全局入口页（无菜单承载，按权限码判定；空数组 = 全员可进）
const GLOBAL_ENTRIES = {
  '/approval': [],
}

// 当前用户能否访问该路由：
//  ① 超管（*:*:* / is_admin）→ 全放行
//  ② 菜单树已加载 → 与左侧菜单严格同源：命中菜单 path，或是某菜单的父路由
//     （如 /expense/apply → /expense/apply/meeting）
//  ③ 菜单未命中不等于无权：一域一台改造后，工作台的页签级页面（/arflow/billing 等）
//     不再有独立菜单行，只在 ROUTE_PERM 里留码——此处必须回退按码判定，
//     否则这些真实可打开的页面会被整片标成「无权限」＝新造的假阴性。
//  ④ 既不在菜单、也没有登记码 → 视为不可达；全局入口页走 GLOBAL_ENTRIES 白名单。
function canAccess(route) {
  if (isSuperPerm()) return true
  if (menuPaths.value) {
    if (menuPaths.value.has(route)) return true
    for (const p of menuPaths.value) {
      if (p.startsWith(route + '/')) return true
    }
    const need = ROUTE_PERM[route]
    if (need && need.length) return hasAnyPerm(need)
    if (route in GLOBAL_ENTRIES) return hasAnyPerm(GLOBAL_ENTRIES[route])
    return false
  }
  const need = ROUTE_PERM[route]
  if (!need || !need.length) return true
  return hasAnyPerm(need)
}

// 点击节点：无权限则提醒并拦截跳转；有权限则导航
function go(route, label) {
  if (!canAccess(route)) {
    ElMessage.warning(`您没有「${label || route}」的访问权限，请联系管理员`)
    return
  }
  router.push(route)
}
function goTraceDemo() {
  // 演示用：取一张最近单据触发全链路联查抽屉（无 id 时仅提示入口位置）
  traceBillId.value = null
  ElMessage?.info?.('在任意业务单据页点「追溯」图标即可打开全链路联查（单据→付款→批次→凭证→档案）')
}

const stageClass = (stage) => {
  if (['业务发起', '发起', '合同与订单', '报销发起', '应收发起', '资产发起', '预算发起', '开票发起'].includes(stage)) return 's-start'
  if (['发票与影像', '审核', '共享审核', '收款与核销', '资金执行', '记账前审核'].includes(stage)) return 's-review'
  if (['记账与档案', '记账', '总账', '月结'].includes(stage)) return 's-account'
  return 's-close'
}

const domains = [
  {
    key: 'p2p', name: '采购到付款 (P2P)', icon: ShoppingCart, color: '#409eff',
    stages: [
      { stage: '业务发起', steps: [
        { menu: '采购与应付 / 合同台账', page: '合同台账', route: '/contract' },
        { menu: '采购与应付 / 采购订单', page: '采购订单', route: '/purchase/order' },
        { menu: '采购与应付 / 收货单', page: '收货单', route: '/purchase/receipt' },
      ]},
      { stage: '发票与影像', steps: [
        { menu: '发票管理 / 发票池', page: '发票池', route: '/invoice/query' },
        { menu: '发票管理 / 发票补录', page: '发票补录', route: '/invoice/create' },
        { menu: '发票管理 / 发票验真记录', page: '发票验真记录', route: '/invoice/verify' },
        { menu: '发票管理 / 进项发票认证', page: '进项发票认证', route: '/invoice/certify' },
        { menu: '影像管理 / 影像工作台', page: '影像采集批次', route: '/image' },
      ]},
      { stage: '三单匹配', steps: [
        { menu: '共享运营 / 对公三单匹配 / 匹配操作', page: '匹配操作', route: '/pay/match' },
      ]},
      { stage: '付款与共享审核', steps: [
        { menu: '费用报账 / 对公支付 / 对公结算', page: '对公结算', route: '/corp-pay/settle' },
        { menu: '费用报账 / 对公支付 / 费用预付款', page: '费用预付款', route: '/corp-pay/prepay' },
        { menu: '共享运营 / 任务池管理', page: '任务池管理', route: '/sharing/task-pool' },
        { menu: '共享运营 / 工单处理', page: '工单处理', route: '/sharing/task-work' },
        { menu: '资金管理 / 出纳付款', page: '出纳付款', route: '/pay' },
      ]},
      { stage: '记账与档案', steps: [
        { menu: '总账核算 / 凭证台账', page: '凭证台账', route: '/op/gl/voucher' },
        { menu: '总账核算 / 对公付款凭证·总账', page: '对公付款凭证·总账', route: '/corp-pay/voucher' },
        { menu: '档案管理 / 档案归档', page: '档案归档', route: '/sharing/archive' },
      ]},
      { stage: '出数', steps: [
        { menu: '管理报表 / 应付账龄分析', page: '应付账龄分析', route: '/reports/ap-aging' },
        { menu: '管理报表 / 供应商付款汇总', page: '供应商付款汇总', route: '/reports/supplier-pay' },
        { menu: '管理报表 / 进项发票统计', page: '进项发票统计', route: '/reports/input-invoice' },
      ]},
    ],
  },
  {
    key: 'exp', name: '费用报销链', icon: Money, color: '#67c23a',
    stages: [
      { stage: '报销发起', steps: [
        { menu: '费用报账 / 员工报销 / 员工费用报销', page: '员工费用报销', route: '/expense/claim' },
        { menu: '费用报账 / 员工报销 / 差旅培训报销', page: '差旅培训报销', route: '/expense/claim-travel' },
        { menu: '费用报账 / 事前申请', page: '事前申请', route: '/expense/apply' },
        { menu: '费用报账 / 借款还款 / 借款申请', page: '借款申请', route: '/expense/loan' },
        { menu: '费用报账 / 借款还款 / 还款管理', page: '还款管理', route: '/expense/repay' },
      ]},
      { stage: '审核', steps: [
        { menu: '费用报账 / 我的单据', page: '我的单据', route: '/requisition/mine' },
        { menu: '共享运营 / 审批工作台', page: '审批工作台', route: '/approval' },
        { menu: '共享运营 / 任务池管理', page: '任务池管理', route: '/sharing/task-pool' },
        { menu: '共享运营 / 工单处理', page: '工单处理', route: '/sharing/task-work' },
      ]},
      { stage: '记账与档案', steps: [
        { menu: '总账核算 / 凭证台账', page: '凭证台账', route: '/op/gl/voucher' },
        { menu: '档案管理 / 档案归档', page: '档案归档', route: '/sharing/archive' },
      ]},
      { stage: '出数', steps: [
        { menu: '管理报表 / 费用发生汇总', page: '费用发生汇总', route: '/reports/summary' },
        { menu: '管理报表 / 借款余额汇总', page: '借款余额汇总', route: '/reports/loan' },
        { menu: '管理报表 / 预算执行', page: '预算执行', route: '/reports/budget' },
      ]},
    ],
  },
  {
    key: 'o2c', name: '应收管理 (O2C)', icon: Wallet, color: '#e6a23c',
    stages: [
      { stage: '应收发起', steps: [
        { menu: '应收管理 / 应收工作台 › 应收单', page: '应收单', route: '/receivable/receivable' },
        { menu: '应收管理 / 应收工作台 › 开票链工作台', page: '开票链工作台', route: '/arflow/billing' },
        { menu: '应收管理 / 应收工作台 › 应收服务申请', page: '应收服务申请', route: '/arflow/claims' },
        { menu: '资金管理 / 资金收款', page: '资金收款', route: '/treasury/credit' },
      ]},
      { stage: '收款与核销', steps: [
        { menu: '应收管理 / 应收工作台 › 收款核销', page: '收款核销', route: '/receivable/receipt' },
        { menu: '应收管理 / 应收工作台 › 催收待办', page: '催收待办', route: '/receivable/collect' },
        { menu: '应收管理 / 应收工作台 › 票据管理', page: '票据管理', route: '/arflow/notes' },
      ]},
      { stage: '记账与档案', steps: [
        { menu: '应收管理 / 应收工作台 › 坏账管理', page: '坏账管理', route: '/arflow/bad-debt' },
        { menu: '总账核算 / 凭证台账', page: '凭证台账', route: '/op/gl/voucher' },
        { menu: '档案管理 / 档案归档', page: '档案归档', route: '/sharing/archive' },
      ]},
      { stage: '出数', steps: [
        { menu: '管理报表 / 应收账龄分析', page: '应收账龄分析', route: '/reports/ar-aging' },
        { menu: '应收管理 / 应收工作台 › 应收统计报表', page: '应收统计报表', route: '/arflow/reports' },
      ]},
    ],
  },
  {
    key: 'cash', name: '资金管理链', icon: CreditCard, color: '#9b59b6',
    stages: [
      { stage: '资金执行', steps: [
        { menu: '资金管理 / 资金计划', page: '资金计划', route: '/treasury/plan' },
        { menu: '资金管理 / 资金调拨', page: '资金调拨', route: '/treasury/transfer' },
        { menu: '资金管理 / 票据结算', page: '票据结算', route: '/treasury/settle' },
        { menu: '资金管理 / 资金头寸', page: '资金头寸', route: '/treasury/position' },
      ]},
      { stage: '银企核对', steps: [
        { menu: '共享运营 / 银企对账', page: '银企对账', route: '/bank/reconcile' },
        { menu: '共享运营 / 银企对账 / 银企回单·收款认领', page: '银企回单·收款认领', route: '/bank/slip' },
        { menu: '共享运营 / 未达账项调节表', page: '未达账项调节表', route: '/bank/reconcile-sheet' },
      ]},
      { stage: '记账', steps: [
        { menu: '总账核算 / 凭证台账', page: '凭证台账', route: '/op/gl/voucher' },
      ]},
      { stage: '出数', steps: [
        { menu: '管理报表 / 资金收支汇总', page: '资金收支汇总', route: '/reports/cash-flow' },
        { menu: '管理报表 / 资金余额表', page: '资金余额表', route: '/reports/bank-balance' },
      ]},
    ],
  },
  {
    key: 'asset', name: '资产与存货链', icon: Box, color: '#2db7f5',
    stages: [
      { stage: '资产发起', steps: [
        { menu: '资产与存货 / 资产工作台 › 资产卡片', page: '资产卡片', route: '/asset/asset' },
        { menu: '资产与存货 / 资产工作台 › 资产类别', page: '资产类别', route: '/asset/category' },
        { menu: '资产与存货 / 资产工作台 › 存货收发存', page: '存货收发存', route: '/asset/inventory' },
      ]},
      { stage: '记账前审核', steps: [
        { menu: '资产与存货 / 资产工作台 › 税会差异', page: '税会差异', route: '/asset/taxDiff' },
        { menu: '资产与存货 / 折旧计提批', page: '折旧计提批', route: '/asset/depr-batch' },
        { menu: '共享运营 / 任务池管理', page: '任务池管理', route: '/sharing/task-pool' },
      ]},
      { stage: '记账', steps: [
        { menu: '总账核算 / 凭证台账', page: '凭证台账', route: '/op/gl/voucher' },
      ]},
    ],
  },
  {
    key: 'gl', name: '总账核算链', icon: Document, color: '#ff9900',
    stages: [
      { stage: '记账', steps: [
        { menu: '总账核算 / 总账分录', page: '总账分录', route: '/op/gl/entry' },
        { menu: '总账核算 / 科目余额', page: '科目余额表', route: '/op/gl/balance' },
        { menu: '总账核算 / 试算平衡', page: '试算平衡', route: '/op/gl/trial' },
        { menu: '总账核算 / 凭证台账', page: '凭证台账', route: '/op/gl/voucher' },
      ]},
      { stage: '月结', steps: [
        { menu: '总账核算 / 税金计提', page: '税金计提', route: '/glex/special/tax' },
        { menu: '总账核算 / 薪酬发放', page: '薪酬发放', route: '/glex/special/salary' },
        { menu: '总账核算 / 月结', page: '月结', route: '/glex/special/monthclose' },
        { menu: '总账核算 / 合并报表', page: '合并报表', route: '/glex/special/consolidate' },
      ]},
      { stage: '出数', steps: [
        { menu: '管理报表 / 报表预聚合', page: '报表预聚合', route: '/report/summary' },
      ]},
    ],
  },
  {
    key: 'budget', name: '预算与税务链', icon: Operation, color: '#19be3b',
    stages: [
      { stage: '预算发起', steps: [
        { menu: '预算管理 / 预算方案', page: '预算方案', route: '/budget/scheme' },
        { menu: '预算管理 / 预算科目', page: '预算科目', route: '/budget/item' },
        { menu: '预算管理 / 预算调整', page: '预算调整', route: '/budget/adjust' },
      ]},
      { stage: '审核', steps: [
        { menu: '预算管理 / 预算占用', page: '预算占用', route: '/budget/occupy' },
        { menu: '预算税务 / 预算执行', page: '预算执行明细', route: '/budget/execution' },
        { menu: '系统管理 / 系统监控 / 反欺诈风控', page: '反欺诈风控', route: '/compliance/fraud' },
      ]},
      { stage: '税务', steps: [
        { menu: '总账核算 / 税务退税三场景', page: '税务退税三场景', route: '/glex/tax-refund' },
        { menu: '系统管理 / 系统监控 / 汇兑重估', page: '汇兑重估', route: '/glex/fx-reval' },
      ]},
      { stage: '出数', steps: [
        { menu: '管理报表 / 税负分析', page: '税负分析', route: '/reports/tax-burden' },
        { menu: '管理报表 / 预算超支预警', page: '预算超支预警', route: '/reports/budget-alert' },
      ]},
    ],
  },
]

// 角色聚焦（复用 RoleDashboard 角色枚举；当前前端未绑定真实登录角色，故本地切换）
const roleFilter = ref('ALL')
const roleLabel = { ALL: '全部', OPERATOR: '操作员', SUPERVISOR: '主管', CFO: '财务总监', ADMIN: '管理员' }
// 各角色聚焦的域（key）。职责聚焦映射，可按实际岗位调整；ALL = 7 域全显
const roleDomains = {
  ALL: ['p2p', 'exp', 'o2c', 'cash', 'asset', 'gl', 'budget'],
  OPERATOR: ['exp', 'p2p', 'o2c', 'asset'],
  SUPERVISOR: ['exp', 'p2p', 'o2c', 'cash', 'asset'],
  CFO: ['gl', 'budget', 'cash', 'o2c', 'p2p', 'exp'],
  ADMIN: ['p2p', 'exp', 'o2c', 'cash', 'asset', 'gl', 'budget'],
}
const visibleDomains = computed(() => {
  const keys = roleDomains[roleFilter.value] || roleDomains.ALL
  return domains.filter(d => keys.includes(d.key))
})
</script>

<style scoped>
.nav-map { padding: 16px; }
.map-header { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.map-title { margin: 0; font-size: 20px; font-weight: 700; }
.map-sub { margin: 4px 0 0; font-size: 13px; color: #909399; }
.map-sub code { background: var(--app-hover, #f0f2f5); padding: 1px 6px; border-radius: 4px; color: #409eff; }
.map-legend { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.lg { font-size: 12px; padding: 2px 8px; border-radius: 4px; color: #fff; }
.lg-start { background: #409eff; }
.lg-review { background: #e6a23c; }
.lg-account { background: #9b59b6; }
.lg-close { background: #67c23a; }
.ctrl-strip { margin-bottom: 16px; }
.ctrl-title { font-weight: 600; }
.ctrl-items { margin-left: 8px; }
.ctrl-items b { color: #409eff; cursor: pointer; margin: 0 6px; font-weight: 600; }
.ctrl-items b:hover { text-decoration: underline; }
.ctrl-items b.no-perm { opacity: .45; cursor: not-allowed; text-decoration: none; }

.domain-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 16px; }
.domain-card { background: var(--app-card-bg, #fff); border: 1px solid var(--app-border, #ebeef5); border-radius: 10px; overflow: hidden; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.domain-head { display: flex; align-items: center; gap: 8px; padding: 12px 14px; background: var(--c); color: #fff; font-weight: 600; font-size: 15px; }
.domain-body { padding: 12px 14px; }
.stage { margin-bottom: 12px; }
.stage:last-child { margin-bottom: 0; }
.stage-label { display: inline-block; font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 4px; margin-bottom: 8px; color: #fff; }
.stage-label.s-start { background: #409eff; }
.stage-label.s-review { background: #e6a23c; }
.stage-label.s-account { background: #9b59b6; }
.stage-label.s-close { background: #67c23a; }
.steps { display: flex; flex-direction: column; gap: 6px; }
.step { display: flex; align-items: flex-start; gap: 8px; padding: 7px 10px; border-radius: 6px; background: var(--app-hover, #f7f8fa); cursor: pointer; transition: all 0.15s; border: 1px solid transparent; }
.step:hover { background: var(--app-hover, #ecf5ff); border-color: #409eff; transform: translateX(2px); }
.step.no-perm { opacity: .45; cursor: not-allowed; }
.step.no-perm:hover { background: var(--app-hover, #f7f8fa); border-color: transparent; transform: none; }
.step.no-perm .step-go { display: none; }
.step.no-perm::after { content: ''; font-size: 11px; margin-left: auto; opacity: .8; }
.step-idx { flex-shrink: 0; width: 18px; height: 18px; border-radius: 50%; background: var(--c); color: #fff; font-size: 11px; font-weight: 700; display: flex; align-items: center; justify-content: center; margin-top: 1px; }
.step-text { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.step-menu { font-size: 11px; color: #909399; }
.step-page { font-size: 13px; color: var(--app-text, #303133); font-weight: 500; display: flex; align-items: center; gap: 2px; }
.step-go { font-size: 12px; color: #c0c4cc; }
.step:hover .step-go { color: #409eff; }
.role-filter { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.rf-label { font-size: 14px; color: var(--app-text-secondary, #606266); font-weight: 500; }
.rf-tip { font-size: 12px; color: #909399; background: var(--app-hover, #f4f4f5); padding: 3px 10px; border-radius: 4px; }
</style>
