<template>
  <div class="workbench">
    <!-- 业务入口卡片 -->
    <div class="biz-entries">
      <div v-for="e in entries" :key="e.path" class="biz-entry" @click="go(e.path)">
        <div class="biz-icon" :style="{ background: e.color }"><el-icon :size="24"><component :is="e.icon" /></el-icon></div>
        <div class="biz-text">
          <div class="biz-title">{{ e.title }}</div>
          <div class="biz-desc">{{ e.desc }}</div>
        </div>
      </div>
    </div>

    <!-- 概览卡片（无权限的卡片不渲染，避免点了才报"缺少权限"） -->
    <el-row class="cm-target-workbench cards" :gutter="16">
      <el-col :span="statSpan"><el-card shadow="hover" class="stat-card" @click="go('/approval')"><div class="stat"><div class="num">{{ stats.todo }}</div><div class="label">我的待办</div></div></el-card></el-col>
      <el-col :span="statSpan"><el-card shadow="hover" class="stat-card" @click="go('/requisition/mine')"><div class="stat"><div class="num">{{ stats.mine }}</div><div class="label">我发起(待审)</div></div></el-card></el-col>
      <el-col v-if="canPool" :span="statSpan"><el-card shadow="hover" class="stat-card" @click="go('/sharing/task-pool')"><div class="stat"><div class="num">{{ stats.pool }}</div><div class="label">待派工</div></div></el-card></el-col>
      <el-col v-if="canPay" :span="statSpan"><el-card shadow="hover" class="stat-card" @click="go('/pay')"><div class="stat"><div class="num">{{ stats.pay }}</div><div class="label">付款待执行</div></div></el-card></el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 我的待办 -->
      <el-col :span="canPool ? 12 : 24">
        <el-card header="我的待办">
          <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center">
            <el-button size="small" @click="exportTableCsv('todo')">导出 CSV</el-button>
            <el-button v-if="todoSel.length" type="success" plain size="small" @click="exportSelected('todo')">导出选中({{ todoSel.length }})</el-button>
          </div>
          <SkeletonList v-if="loading.todo" :rows="8" :cols="5" />
          <el-table class="cm-target-workbench" :data="pagedTodo" size="small" stripe v-else max-height="320" empty-text="暂无数据" v-loading="loading.todo" @selection-change="(val) => todoSel = val" @row-dblclick="openTrace">
            <el-table-column type="selection" width="45" />
            <el-table-column prop="billNo" label="单号" width="150" sortable />
            <!-- W4（2026-10-08 非人性化整改）：待办混排报销/借款/付款/合同等，原列表无类型列，逐行点开才知是什么单 -->
            <el-table-column label="单据类型" width="100" sortable>
              <template #default="{ row }">{{ docTypeLabel(row.billType || row.bizType) || '—' }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip sortable />
            <el-table-column prop="taskName" label="节点" width="110" sortable />
            <el-table-column label="操作" width="178" fixed="right">
              <template #default="{ row }">
                <!-- P0: 主入口——去业务处理页；始终可见，不依赖状态字段 -->
                <el-button link type="primary" size="small" @click="goBiz(row)">去处理</el-button>
                <!-- 待办均为待审批项，审批/退回/追溯始终展示（待办行无 status 字段，不能用状态门控） -->
                <el-tooltip content="审批通过" placement="top">
                  <el-button link type="success" size="small" @click="approve(row, BillStatus.APPROVED)"><el-icon><Check /></el-icon></el-button>
                </el-tooltip>
                <el-tooltip content="退回/驳回" placement="top">
                  <el-button link type="danger" size="small" @click="approve(row, BillStatus.REJECTED)"><el-icon><Close /></el-icon></el-button>
                </el-tooltip>
                <el-tooltip content="全链路追溯" placement="top">
                  <el-button link size="small" @click="openTrace(row)"><el-icon><Connection /></el-icon></el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="pagerTodo" @change="loadTodo" />
        </el-card>
      </el-col>

      <!-- 待派工任务池（共享运营，需 sharing:task:list） -->
      <el-col v-if="canPool" :span="12">
        <el-card header="待派工任务池（共享运营）">
          <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center">
            <el-button size="small" @click="exportTableCsv('pool')">导出 CSV</el-button>
            <el-button v-if="poolSel.length" type="success" plain size="small" @click="exportSelected('pool')">导出选中({{ poolSel.length }})</el-button>
          </div>
          <SkeletonList v-if="loading.pool" :rows="8" :cols="5" />
          <el-table :data="pagedPool" size="small" stripe v-else max-height="320" empty-text="暂无数据" v-loading="loading.pool" @selection-change="(val) => poolSel = val">
            <el-table-column type="selection" width="45" />
            <el-table-column prop="billNo" label="业务单号" width="150" sortable />
            <el-table-column label="类型" width="110">
              <template #default="{ row }">{{ taskTypeLabel(row.taskType) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag :type="row.poolStatus === 'WAITING' ? 'warning' : 'info'">{{ poolStatusLabel(row.poolStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="goPoolWork(row)">去处理</el-button>
                <el-button v-if="row.poolStatus === 'WAITING'" link type="success" size="small" @click="receive(row)">抢单</el-button>
                <el-button v-if="row.poolStatus === 'RECEIVED'" link type="success" size="small" @click="finish(row)">完成</el-button>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="pagerPool" @change="loadPool" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <!-- 我发起的 -->
      <el-col :span="12">
        <el-card header="我发起的单据">
          <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center">
            <el-button size="small" @click="exportTableCsv('mine')">导出 CSV</el-button>
            <el-button v-if="mineSel.length" type="success" plain size="small" @click="exportSelected('mine')">导出选中({{ mineSel.length }})</el-button>
            <span v-if="mineApprox" style="color:#909399;font-size:12px">（跨业务域汇总，总数为近似）</span>
          </div>
          <!-- D-DEC-406：本卡改跨域聚合（合同／采购／预算／总账／应收／资产…一并按登记人取数）。
               后端取不到归属人列的类型如实报出，此处明示"工作台不按人汇总"，不让列表静默少数据。 -->
          <el-alert v-if="mineUnsupportedText" type="info" :closable="false" show-icon style="margin-bottom: 8px"
                    :title="'以下单据类型暂不支持按发起人汇总，不会出现在本列表，请到对应业务的台账页查看：' + mineUnsupportedText" />
          <SkeletonList v-if="loading.mine" :rows="8" :cols="5" />
          <el-table :data="pagedMine" size="small" stripe v-else max-height="280" empty-text="暂无数据" v-loading="loading.mine" @selection-change="(val) => mineSel = val">
            <el-table-column type="selection" width="45" />
            <el-table-column prop="billNo" label="单号" width="150" sortable />
            <el-table-column label="类型" width="110">
              <template #default="{ row }">{{ row.typeLabel || '—' }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip sortable />
            <el-table-column label="金额" width="100" sortable>
              <!-- 跨域行取的是各域自己的金额列，部分域（档案／借阅…）本表没有金额列 ⇒ 后端回 NULL。
                   此处显「—」而不是 0.00：把"没有这个字段"显示成"金额为零"是错值，比空值更危险。 -->
              <template #default="{ row }">{{ row.totalAmount == null ? '—' : formatMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="billStatusType(row.status)" size="small">{{ billStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="goBiz(row)">查看</el-button>
                <el-button link size="small" @click="openTrace(row)">追溯</el-button>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="pagerMine" @change="loadMine" />
        </el-card>
      </el-col>
      <!-- 我的抄送 -->
      <el-col :span="12">
        <el-card header="我的抄送">
          <div style="margin-bottom: 8px; display: flex; gap: 8px; align-items: center">
            <el-button size="small" @click="exportTableCsv('cc')">导出 CSV</el-button>
            <el-button v-if="ccSel.length" type="success" plain size="small" @click="exportSelected('cc')">导出选中({{ ccSel.length }})</el-button>
          </div>
          <SkeletonList v-if="loading.cc" :rows="8" :cols="5" />
          <el-table :data="pagedCc" size="small" stripe v-else max-height="280" empty-text="暂无数据" v-loading="loading.cc" @selection-change="(val) => ccSel = val">
            <el-table-column type="selection" width="45" />
            <el-table-column prop="bizNo" label="业务单号" width="150" sortable />
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip sortable />
            <el-table-column prop="ccUserName" label="抄送人" width="100" sortable />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="goBiz(row)">查看</el-button>
                <el-button link size="small" @click="openTrace(row)">追溯</el-button>
              </template>
            </el-table-column>
          </el-table>
          <TablePager :pager="pagerCc" @change="loadCc" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 审批/驳回弹窗 -->
    <el-dialog v-model="approveVisible" :title="approveDecision === BillStatus.APPROVED ? '审批通过' : '驳回'" width="420px">
      <el-form :model="{ comment: approveComment }" :rules="approveRules" label-width="80px">
        <el-form-item :label="approveDecision === BillStatus.APPROVED ? '审批意见' : '驳回原因'" :required="approveDecision === BillStatus.REJECTED" prop="comment">
          <el-select v-model="approveComment" filterable allow-create default-first-option :placeholder="approveDecision === BillStatus.APPROVED ? '请选择或输入审批意见' : '请选择或输入驳回原因'" style="width: 100%">
            <template v-if="approveDecision === BillStatus.APPROVED">
              <el-option label="同意" value="同意" />
              <el-option label="已核实无误" value="已核实无误" />
              <el-option label="符合规定" value="符合规定" />
            </template>
            <template v-else>
              <el-option label="金额超出预算" value="金额超出预算" />
              <el-option label="发票不合规" value="发票不合规" />
              <el-option label="审批流程不符" value="审批流程不符" />
              <el-option label="资料不完整" value="资料不完整" />
              <el-option label="费用不合理" value="费用不合理" />
              <el-option label="其他" value="其他" />
            </template>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button :type="approveDecision === BillStatus.APPROVED ? 'primary' : 'danger'" :loading="approving" @click="doApprove">确认</el-button>
      </template>
    </el-dialog>
  </div>

  <CoachMark ref="wb" :guide-key="_guideKey" :selector="'.cm-target-workbench'" title="工作台" text="这里是您的工作台首页，可查看待办任务、单据统计和快捷入口。" />
<BillTraceDrawer v-model="traceVisible" :bill-id="traceBillId" :biz-type="traceBizType" />
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import SkeletonList from '../../components/SkeletonList.vue'
import {
  Money, Document, Wallet, CreditCard, Coin, Box, Files, DataAnalysis,
  Check, Close, Connection,
} from '@element-plus/icons-vue'
// D-DEC-406：billApi.myPage（费用域单表）已由 /tenant/workbench/my-documents 跨域取数替代，不再引入
import { wfApi, flowApi, payApi, sharingApi, store, http } from '../../api'
import { hasPerm } from '../../utils/perm'
import { statusLabel as _billStatusLabel, statusType as _billStatusType, BillStatus } from '../../constants/billStatus'
import { taskTypeLabel, poolStatusLabel, docTypeLabel } from '../../constants/enums'
import CoachMark from '@/components/CoachMark.vue'
import BillTraceDrawer from '@/components/BillTraceDrawer.vue'
import { useTablePager } from '../../composables/useTablePager'
import TablePager from '../../components/common/TablePager.vue'
import { goBizPage, resolveBillRoute } from '@/utils/bizEntry'
const wb = ref(null)

const router = useRouter()

// ---------- 权限守卫 ----------
// 工作台是全员默认落地页(USG-01)，但其中「共享运营任务池」「付款待执行」属模块专属数据。
// 无对应权限的角色若照旧无条件请求，会弹出"无该功能/按钮权限：缺少权限：xxx"的误导性报错
// （实测租户 136 名非管理员用户中，119 人无 sharing:task:list、125 人无 pay:batch:list）。
// 故按权限跳过请求，并同步隐藏对应卡片。
// 注：store 是 localStorage 支持的普通对象（非 reactive），且登录/会话校验都发生在工作台挂载之前，
// 故这里在 setup 期一次性求值即可，不用 computed 制造"可响应"的假象。
// hasPerm 已抽取至 utils/perm（与 Pay.vue / FlowConfig.vue 共用，避免三份重复实现）
const canPool = hasPerm('sharing:task:list')
const canPay = hasPerm('pay:batch:list')
const canInvPool = hasPerm('inv:list')
// 概览卡片等分铺满（隐藏权限不足的卡片后不留半行空白）
const statSpan = 24 / (2 + (canPool ? 1 : 0) + (canPay ? 1 : 0))

// 业务入口定义（perm 取自该入口对应菜单的权限码，缺省表示全员可见）
const ENTRIES = [
  { title: '我要报销', desc: '员工费用报销', path: '/expense/claim', icon: Money, color: '#409eff', perm: 'exp:bill:submit' },
  { title: '事前申请', desc: '会议/出差/培训', path: '/expense/apply', icon: Document, color: '#67c23a' },
  { title: '借款申请', desc: '借款与还款', path: '/expense/loan', icon: Wallet, color: '#e6a23c', perm: 'exp:bill:list' },
  { title: '对公付款', desc: '对公支付申请', path: '/corp-pay', icon: CreditCard, color: '#f56c6c', perm: ['exp:pay:list', 'exp:pay:submit'] },
  { title: '出纳付款', desc: '付款批次执行', path: '/pay', icon: Coin, color: '#9b59b6', perm: 'op:pay:list' },
  { title: '资产管理', desc: '资产与存货', path: '/asset', icon: Box, color: '#2db7f5', perm: ['asset:list', 'inventory:list'] },
  { title: '合同台账', desc: '合同与付款', path: '/contract', icon: Files, color: '#ff9900', perm: 'contract:list' },
  { title: '管理报表', desc: '报表与分析', path: '/reports', icon: DataAnalysis, color: '#19be3b', perm: 'report:view' },
  { title: '流程导航地图', desc: '7 大域端到端链路', path: '/process-nav', icon: Connection, color: '#606266' },
]
// 入口卡此前硬编码全量渲染，而路由守卫只校验 token 不校验权限，
// 无权限用户点进去才吃报错；现按菜单权限码过滤，与侧边栏菜单可见性保持一致。
const entries = ENTRIES.filter((e) => {
  const need = e.perm
  if (!need) return true
  return Array.isArray(need) ? need.some(hasPerm) : hasPerm(need)
})
function go(path) { router.push(path) }

// P1-1：查看/全链路追溯统一走单据轨迹抽屉（此前 @view 绑到空函数、openTrace 未定义）
const traceVisible = ref(false)
const traceBillId = ref(null)
const traceBizType = ref(null)
function openTrace(row) {
  traceBillId.value = row.bizId || row.billId || row.bill_id || row.id
  traceBizType.value = row.bizType || row.billType || null
  traceVisible.value = true
}

// 按单据类型进入对应业务处理页（携带 billNo/billId 供落地页定位）；未命中类型兜底打开追溯抽屉
function goBiz(row) {
  // D-DEC-078：与 Approval.vue 同一落点闸——发票池数据面按 inv:list 门控，
  // 无权限岗不跳闸页（落地只会吃「暂无数据」＋一串「缺少权限」报错），改落全链路追溯。
  if (!canInvPool && resolveBillRoute(row?.billType || row?.bizType) === '/invoice') {
    ElMessage.info('当前账号无发票池查看权限（inv:list），已打开全链路追溯')
    openTrace(row)
    return
  }
  if (!goBizPage(router, row)) {
    // D-DEC-406：一表多 bizType 的跨域行（po_order＝PO/PURCHASE/PURCHASE_ORDER_REVIEW 三写、
    // gl_journal_entry＝计提/待摊/其他收入/手工凭证四合一）后端宁缺勿错、不猜类型 ⇒ 行内 bizType 为空，
    // 按 billType 的映射表必然落空。这类行带的是**表级**台账坐标（ledgerRoute），跳台账并按单号定位。
    if (row.ledgerRoute) {
      // 落地页取参有两套既有口径，且各页只认自己那套：
      //   ①kw——台账搜索框（Contract.vue:276／PoOrder.vue:367／PayPlan.vue:113）
      //   ②billNo/billId——readBillDeepLink（GlSpecial.vue:390／BankAccount／BankReconcile）
      // 只写一套 ⇒ 另一半页面跳过去照旧是全量首页。两套一并带上，各页取自己要的，互不干扰。
      const q = {}
      if (row.billNo) { q.kw = row.billNo; q.billNo = row.billNo }
      if (row.billId != null && row.billId !== '') q.billId = row.billId
      router.push({ path: row.ledgerRoute, query: q })
      return
    }
    ElMessage.info('该单据类型暂未配置业务处理入口，已打开全链路追溯')
    openTrace(row)
  }
}

// 派工池：跳到任务池管理页并按单号定位该任务
function goPoolWork(row) {
  router.push({ path: '/sharing/task-pool', query: row.billNo ? { keyword: row.billNo } : {} })
}

function billStatusLabel(s) { return _billStatusLabel(s) }
function billStatusType(s) { return _billStatusType(s) }
function formatMoney(val) {
  return Number(val || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

const stats = reactive({ todo: 0, mine: 0, pool: 0, pay: 0 })

// D-DEC-406：跨域汇总的两条如实标注——无归属人列的类型（后端 unsupported）、单表取数封顶（approximate）
const mineUnsupported = ref([])
const mineApprox = ref(false)
const mineUnsupportedText = computed(() => {
  const names = []
  for (const u of mineUnsupported.value || []) {
    const codes = String(u.bizTypes || '').split(',').filter(Boolean)
      .map((c) => docTypeLabel(c.trim()) || c.trim())
    const fallback = [u.label || u.table].filter(Boolean)
    for (const n of (codes.length ? codes : fallback)) if (n && !names.includes(n)) names.push(n)
  }
  return names.join('、')
})

// 各表格独立 selection，互不联动
const todoSel = ref([])
const poolSel = ref([])
const mineSel = ref([])
const ccSel = ref([])

const todos = ref([])
const pools = ref([])
const mines = ref([])
const ccs = ref([])
const pagerTodo = useTablePager({ mode: 'server', storageKey: 'wb_todo' })
const pagerPool = useTablePager({ mode: 'server', storageKey: 'wb_pool' })
const pagerMine = useTablePager({ mode: 'server', storageKey: 'wb_mine' })
const pagerCc = useTablePager({ mode: 'server', storageKey: 'wb_cc' })
const { pagedData: pagedTodo } = pagerTodo
const { pagedData: pagedPool } = pagerPool
const { pagedData: pagedMine } = pagerMine
const { pagedData: pagedCc } = pagerCc
const loading = reactive({ todo: false, pool: false, mine: false, cc: false })

// 表格元数据：列定义 + 数据源 + selection + 文件名
const TABLE_META = {
  todo: { label: '我的待办', data: () => todos.value, sel: () => todoSel.value, cols: [
    { prop: 'billNo', label: '单号' }, { prop: 'title', label: '标题' }, { prop: 'taskName', label: '节点' } ] },
  pool: { label: '待派工任务池', data: () => pools.value, sel: () => poolSel.value, cols: [
    { prop: 'billNo', label: '业务单号' }, { prop: 'taskType', label: '类型' }, { prop: 'poolStatus', label: '状态' } ] },
  mine: { label: '我发起的单据', data: () => mines.value, sel: () => mineSel.value, cols: [
    { prop: 'billNo', label: '单号' }, { prop: 'typeLabel', label: '类型' }, { prop: 'title', label: '标题' },
    { prop: 'totalAmount', label: '金额' }, { prop: 'status', label: '状态' } ] },
  cc: { label: '我的抄送', data: () => ccs.value, sel: () => ccSel.value, cols: [
    { prop: 'bizNo', label: '业务单号' }, { prop: 'title', label: '标题' }, { prop: 'ccUserName', label: '抄送人' } ] },
}

async function loadTodo() {
  loading.todo = true
  try {
    await pagerTodo.load(async ({ pageNum, pageSize }) => {
      const r = await wfApi.todoPage({ pageNum, pageSize })
      todos.value = r?.list || []
      stats.todo = Number(r?.total) || todos.value.length
      return { list: todos.value, total: r?.total || todos.value.length }
    })
  } catch (e) { ElMessage.error(e.message) } finally { loading.todo = false }
}

async function loadPool() {
  // P0-2: 服务端分页（后端 pool 已返 PageResult，替代原固定 pageSize:20 截断）
  loading.pool = true
  try {
    await pagerPool.load(async ({ pageNum, pageSize }) => {
      const p = await sharingApi.pool({ pageNum, pageSize })
      pools.value = p?.list || []
      // 数据字段为 poolStatus（非 status），此前误用 x.status 导致待派工统计恒为 0
      stats.pool = pools.value.filter((x) => x.poolStatus === 'WAITING').length
      return { list: pools.value, total: p?.total || pools.value.length }
    })
  } catch (e) { ElMessage.error(e.message) } finally { loading.pool = false }
}

async function loadMine() {
  loading.mine = true
  try {
    await pagerMine.load(async ({ pageNum, pageSize }) => {
      // D-DEC-406：跨域「我发起的单据」。旧口径 billApi.myPage → /expense/bill/myPage 只查 exp_bill 一表，
      // 合同／采购／预算／总账／应收／资产等自提单在发起人自己的工作台恒不显示（实测同一张单
      // 「我的待办」有、「我发起的」零行）。改走 /tenant/workbench/my-documents——后端
      // MyDocumentService 按 BillRouteRegistry 的「bizType→业务主表→归属人列」逐表取数后内存归并，
      // 取不到归属人列的类型由 unsupported 如实报出（见卡片上方提示条），不静默少数据。
      const r = await http.get('/tenant/workbench/my-documents', { params: { pageNum, pageSize } })
      mines.value = (r?.records || []).map((x) => ({
        ...x,
        // 后端行字段＝docNo／bizId／bizType／amount；本卡列名与 goBiz／openTrace 的入参口径＝billNo／billId／billType／totalAmount
        billNo: x.docNo || '',
        billId: x.bizId ?? null,
        billType: x.bizType || '',
        totalAmount: x.amount ?? null,
        // 跨域行的类型标签：非费用域由后端给域标签，费用族行内自带 bill_type ⇒ 前端补词表
        typeLabel: x.typeLabel || docTypeLabel(x.bizType) || '',
      }))
      mineUnsupported.value = r?.unsupported || []
      mineApprox.value = !!r?.approximate
      return { list: mines.value, total: r?.total || mines.value.length }
    })
  } catch (e) { ElMessage.error(e.message) } finally { loading.mine = false }
}

async function loadCc() {
  loading.cc = true
  try {
    await pagerCc.load(async ({ pageNum, pageSize }) => {
      const c = await flowApi.myCc({ pageNum, pageSize })
      ccs.value = c?.list || []
      return { list: ccs.value, total: c?.total || ccs.value.length }
    })
  } catch (e) { ElMessage.error(e.message) } finally { loading.cc = false }
}

async function load() {
  // 只加载当前身份有权访问的数据源：无权限的卡片不渲染、也不发请求
  const jobs = [loadTodo(), loadMine(), loadCc()]
  if (canPool) jobs.push(loadPool())
  await Promise.all(jobs)

  // D-DEC-406：「我发起(待审)」改取流程侧跨域口径（/workbench/dashboard 的 mine＝
  // wf_instance.start_user_id＝我 且 status=RUNNING 的实例数，天然覆盖全部单据域）。
  // 旧口径是「列表首页若干行里状态不在 APPROVED/PAID/REJECTED 的行数」——列表一旦跨域，
  // 费用状态词表就套不到合同／采购／总账的列值上，且分页外不计＝两个错叠加。
  try {
    // silent：概览计数属后台统计，失败静默（卡片照旧显示 0，不打断门户加载）
    const d = await http.get('/tenant/workbench/dashboard', { silent: true })
    stats.mine = Number(d?.mine) || 0
  } catch { /* 概览失败不阻断工作台 */ }

  // 付款待执行（需 pay:batch:list；无权限角色直接跳过，避免误导性权限报错）
  if (canPay) {
    try {
      // silent：后台统计徽标，失败静默不进全局错误弹窗（卡片已按权限隐藏）
      const pb = await payApi.batchPage({ pageNum: 1, pageSize: 20 }, { silent: true })
      const list = pb?.list || []
      // D-DEC-287：可打款态改为"已审批/付款失败重试"，CONFIRMED（审批在途）不再计入待执行
      stats.pay = list.filter((x) => x.status === BillStatus.APPROVED || x.status === 'FAILED').length
    } catch (e) { /* ignore */ }
  }
}

const approveVisible = ref(false)
const approving = ref(false)
const approveDecision = ref('')
const approveComment = ref('')
const approveRow = ref(null)

const approveRules = reactive({
  comment: [{ required: true, message: '请选择或输入审批意见' }],
})

async function approve(row, decision) {
  approveRow.value = row
  approveDecision.value = decision
  approveComment.value = ''
  approveVisible.value = true
}

async function doApprove() {
  if (approving.value) return
  const decision = approveDecision.value
  const comment = approveComment.value
  if (decision === BillStatus.REJECTED && !comment.trim()) {
    ElMessage.warning('驳回时请选择或输入驳回原因')
    return
  }
  approving.value = true
  try {
    // P0-3 决策字符串（APPROVED/REJECTED）映射为后端布尔 approved，避免 REJECTED 被忽略走默认通过
    await wfApi.approve({ taskId: approveRow.value.taskId, approved: decision === BillStatus.APPROVED, comment })
    ElMessage.success('已处理')
    approveVisible.value = false
    await load()
  } catch (e) { ElMessage.error(e.message) } finally { approving.value = false }
}
async function receive(row) {
  try { await sharingApi.receive(row.id); ElMessage.success('已抢单'); await load() } catch (e) { ElMessage.error(e.message) }
}
async function finish(row) {
  // D-DEC-024/D-DEC-030（与工单处理页同一处修复）：工作台办结也走带备注的弹窗，
  // 此前一键完成不带任何输入，备注列恒 NULL。
  const { value: remark } = await ElMessageBox.prompt(
    `完成工单 ${row.billNo || row.id}？请填写完成备注（留痕，供审计复核）`, '完成工单', {
      inputType: 'textarea',
      inputPlaceholder: '完成备注（可空）',
      confirmButtonText: '确认完成',
      cancelButtonText: '取消',
      inputValidator: (v) => ((v || '').length > 490 ? '完成备注不得超过 490 字' : true),
    }).catch(() => ({}))
  if (remark === undefined) return // 用户取消
  try { await sharingApi.finish(row.id, remark); ElMessage.success('已完成'); await load() } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)

// ---- 通用 CSV 导出（每个表格独立调用） ----

function toCsv(rows, cols) {
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.map((row) => cols.map((c) => {
    const v = row?.[c.prop]
    if (v == null) return ''
    let s = String(v)
    if (s.includes(',') || s.includes('"') || s.includes('\n')) s = '"' + s.replace(/"/g, '""') + '"'
    return s
  }).join(','))
  return '\ufeff' + [header, ...lines].join('\r\n')
}

function downloadCsv(csv, filename) {
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}

function exportTableCsv(key) {
  const meta = TABLE_META[key]
  const rows = meta.data()
  if (!rows.length) { ElMessage.warning('当前无数据可导出'); return }
  const csv = toCsv(rows, meta.cols)
  const date = new Date().toISOString().slice(0, 10)
  downloadCsv(csv, `${meta.label}_${date}.csv`)
  ElMessage.success('已导出 CSV')
}

function exportSelected(key) {
  const meta = TABLE_META[key]
  const rows = meta.sel()
  if (!rows.length) { ElMessage.warning('请先勾选要导出的记录'); return }
  const csv = toCsv(rows, meta.cols)
  const date = new Date().toISOString().slice(0, 10)
  downloadCsv(csv, `${meta.label}_${date}_selected.csv`)
  ElMessage.success(`已导出 ${rows.length} 条记录`)
}

// R4-06: Show guide on first visit
const _guideKey = 'fssc_guide_wb'
try { if (!localStorage.getItem(_guideKey)) { setTimeout(() => wb.value?.show(), 800) } } catch {}
</script>

<style scoped>
.biz-entries {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.biz-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.biz-entry:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}
.biz-icon {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.biz-title { font-size: 14px; font-weight: 600; color: #303133; }
.biz-desc { font-size: 12px; color: #909399; margin-top: 2px; }
@media (max-width: 1400px) {
  .biz-entries { grid-template-columns: repeat(4, 1fr); }
}
@media (max-width: 768px) {
  .biz-entries { grid-template-columns: repeat(2, 1fr); }
}
.cards { margin-bottom: 16px; }
.stat-card { cursor: pointer; transition: transform 0.2s, box-shadow 0.2s; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.15) !important; }
.stat { text-align: center; }
.stat .num { font-size: 28px; font-weight: 700; color: #409eff; }
.stat .label { color: #909399; margin-top: 4px; }
</style>
