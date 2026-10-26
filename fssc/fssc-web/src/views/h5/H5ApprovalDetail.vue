<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="h5-page h5-page--bar">
    <header class="h5-header">
      <!-- P0-5 同类修复：这里的返回原来是 `history.state`，而 setup 里有一个名为 history 的
           ref（审批历史数组），把 window.history 遮蔽了 ⇒ 判断恒假，返回永远只能回工作台。
           现用真 router 返回。 -->
      <button class="h5-back" aria-label="返回" @click="leave">&lt;</button>
      <span class="h5-header__title">审批详情</span>
      <span class="h5-header__sub">{{ myName }}</span>
    </header>

    <section v-if="loading" class="h5-body">
      <div v-for="i in 3" :key="i" class="h5-skeleton">
        <div class="h5-skeleton__line h5-skeleton__line--m" />
        <div class="h5-skeleton__line h5-skeleton__line--s" />
      </div>
    </section>

    <template v-else>
      <div v-if="loadError" class="h5-body">
        <div class="h5-error">
          <div class="h5-error__d">{{ loadError }}</div>
        </div>
        <button class="h5-btn h5-btn--sub h5-mt-10" @click="loadDetail">重新载入</button>
      </div>

      <template v-else-if="bill">
        <section class="h5-body">
          <!-- D-DEC-446 甲-1：status 驱动的生命周期步骤条已删除（记账/付款/组批/归档四格取的是
               其它域的列，不由 exp_bill.status 承载，永灰）。移动端改由状态标签 + 审批记录承担。 -->
          <div v-if="abnormalText" class="h5-abnormal">
            <div class="h5-error__d">这张单当前是「{{ abnormalText }}」，不能再在这里办理。</div>
          </div>

          <!-- 待办不可办的原因（改造前是"点得进、提交才发现办不了"） -->
          <div v-if="taskId && blockedReason" class="h5-denied">
            <div class="h5-denied__t">这条待办现在办不了</div>
            <div class="h5-denied__d">{{ blockedReason }}</div>
          </div>

          <!-- 基本信息 -->
          <div class="h5-card">
            <div class="h5-card__row">
              <span class="no">{{ bill.billNo }}</span>
              <el-tag size="small" :type="statusType(bill.status)">{{ statusLabel(bill.status) }}</el-tag>
            </div>
            <div class="h5-card__title">{{ bill.title }}</div>
            <div class="h5-amount-row">
              <span class="h5-big-num">￥{{ money(bill.totalAmount) }}</span>
              <span class="h5-flag h5-flag--info">{{ docTypeLabel(bill.billType) }}{{ bill.billSubType === 'TRAVEL' ? '（差旅）' : '' }}</span>
            </div>
            <div class="h5-card__meta">申请人：{{ bill.applyUserName || '—' }} · {{ dayText(bill.applyDate) }}</div>
            <div class="h5-card__meta">公司 {{ bill.companyId || '—' }} · 部门 {{ bill.deptId || '—' }}</div>
            <div v-if="bill.remark" class="h5-card__meta">说明：{{ bill.remark }}</div>
            <div v-if="bill.currentNode" class="h5-card__meta">当前节点：{{ bill.currentNode }}</div>
          </div>

          <!-- 费用明细 -->
          <div class="h5-card">
            <div class="h5-section-title h5-flush">费用明细（{{ lines.length }} 行）</div>
            <div v-for="(l, i) in lines" :key="i" class="h5-row-sub h5-row-sub--block">
              <div class="h5-card__row">
                <span>{{ l.itemName || '—' }}</span>
                <span>￥{{ money(l.amount) }}</span>
              </div>
              <div class="h5-card__meta">
                {{ dayText(l.expenseDate) }}
                <template v-if="l.taxAmount"> · 税额 ￥{{ money(l.taxAmount) }}</template>
                <template v-if="l.remark"> · {{ l.remark }}</template>
              </div>
            </div>
            <div v-if="!lines.length" class="h5-tip">这张单没有费用明细行</div>
          </div>

          <!-- 附件 / 票据影像（P0-7：改造前审批页整块看不到附件，审批人只能凭标题签字） -->
          <div class="h5-card">
            <div class="h5-section-title h5-flush">附件 / 票据影像（{{ attaches.length }}）</div>
            <div v-if="thumbs.length" class="h5-thumb-grid">
              <button v-for="(t, i) in thumbs" :key="t.url" class="h5-thumb" @click="openLightbox(i)">
                <img :src="t.url" :alt="t.name" loading="lazy" />
                <span class="h5-thumb__name">{{ t.name }}</span>
              </button>
            </div>
            <div v-for="o in otherFiles" :key="o.fileId" class="h5-row-sub">
              <span class="h5-flex-1">{{ o.fileName }}</span>
              <span class="h5-flag" :class="o.attachType === 'INVOICE' ? 'h5-flag--ok' : 'h5-flag--info'">
                {{ o.attachType === 'INVOICE' ? '发票' : '附件' }}
              </span>
              <button class="h5-btn-link h5-btn-link--plain" @click="openFile(o)">打开</button>
            </div>
            <div v-if="!attaches.length" class="h5-tip">提单人没上传任何附件</div>
            <div v-else-if="attachTokenFailed" class="h5-tip h5-tip--left">影像取址失败，可点「打开」逐份重试。</div>
            <div class="h5-tip h5-tip--left">图片可直接放大查看；PDF / OFD 走系统查看器打开。</div>
          </div>

          <!-- 关联发票（三态分立：缺权 ≠ 没票 ≠ 加载失败） -->
          <div class="h5-card">
            <div class="h5-section-title h5-flush">关联发票</div>
            <div v-if="invDenied" class="h5-denied">
              <div class="h5-denied__t">发票台账对当前账号不开放</div>
              <div class="h5-denied__d">发票台账属发票管理岗权限。上面附件区里的票据影像仍可查看、照常审核，不依赖这个权限；需要核对票面要素时请到电脑端由发票岗协查。</div>
            </div>
            <div v-else-if="invError" class="h5-error">
              <div class="h5-error__d">{{ invError }}</div>
              <button class="h5-btn h5-btn--sub h5-mt-10" @click="loadInvoices">重试</button>
            </div>
            <template v-else>
              <div v-for="inv in invoices" :key="inv.id" class="h5-row-sub h5-row-sub--block">
                <div class="h5-card__row">
                  <span class="no">{{ inv.invoiceNo }}</span>
                  <el-tag size="small" :type="verifyTagType(inv.verifyStatus)">{{ verifyLabel(inv.verifyStatus) }}</el-tag>
                </div>
                <div class="h5-card__meta">{{ inv.sellerName || '—' }} · ￥{{ money(inv.amount) }} · {{ dayText(inv.invoiceDate) }}</div>
              </div>
              <div v-if="!invoices.length" class="h5-tip">这张单没有关联发票</div>
            </template>
          </div>

          <!-- 审批记录（D-H5M-02/03：改走 /wf/query/chain）
               旧实现传 businessKey，后端无此入参 ⇒ 落进「只查本人参与过的全部留痕」分支，
               面板里混入别的单据、别的域的节点（D-H5M-02）；
               而该端点挂 wf:history:list，提单岗等业务账号直接被拒（D-H5M-03）。
               /chain 按本单实例取数、以「能读这张单据即可读这张单据的轨迹」守卫，
          <div class="h5-card">
            <div class="h5-section-title h5-flush">审批记录</div>
            <div v-if="histDenied" class="h5-denied">
              <div class="h5-denied__t">这张单的审批记录对你不开放</div>
              <div class="h5-denied__d">{{ histDenied.msg || '你不是本单的办理人，也没有本单的数据可见范围。' }}</div>
            </div>
            <div v-else-if="histError" class="h5-error">
              <div class="h5-error__d">{{ histError }}</div>
              <button class="h5-btn h5-btn--sub h5-mt-10" @click="loadHistory">重试</button>
            </div>
            <template v-else>
              <div v-for="(h, i) in hist" :key="(h.nodeKey || h.nodeName || '') + i" class="h5-row-sub h5-row-sub--block">
                <div class="h5-card__row">
                  <span>{{ h.handler || '—' }}</span>
                  <el-tag size="small" :type="h.action ? histType(h.action) : stateType(h.state)">
                    {{ h.action ? histLabel(h.action) : stateLabel(h.state) }}
                  </el-tag>
                </div>
                <div class="h5-card__meta">{{ h.nodeName || '' }}{{ h.time ? ' · ' + formatDateTime(h.time) : '' }}</div>
                <div v-if="h.comment" class="h5-card__meta">意见：{{ h.comment }}</div>
              </div>
              <div v-if="!hist.length" class="h5-tip">还没有审批记录</div>
            </template>
          </div>

          <!-- 下一环节办理人（D-H5M-01）
               服务端 enforcePickOnApprove：点名档节点必须由本环节指定下一环节办理人，
               未点名直接拒收提交。移动端原先没有这一区 ⇒ 点名环节在 H5 永远办不下去。
               规则与电脑端 ApprovalDialog 逐条对齐：候选唯一自动带出、候选多名必须手选、
               池节点与自动取人节点只说明不阻断。 -->
          <div v-if="canApprove && nextBlock" class="h5-card">
            <div class="h5-section-title h5-flush">下一环节</div>
            <div class="h5-card__row">
              <span class="no">{{ nextBlock.nodeName }}</span>
              <el-tag size="small" :type="nextBlock.pick ? 'warning' : (nextBlock.pool ? 'primary' : 'info')">
                {{ nextBlock.pick ? '需指定办理人' : (nextBlock.pool ? '任务池领取' : '自动指派') }}
              </el-tag>
            </div>
            <template v-if="nextBlock.pick">
              <div v-if="!nextBlock.candidates.length" class="h5-denied">
                <div class="h5-denied__t">该节点当前没有可选办理人</div>
                <div class="h5-denied__d">节点角色在本单所属公司下没有在职成员。请先在「系统管理 · 角色授权」补岗，否则单据会卡在这一节。</div>
              </div>
              <template v-else>
                <button v-for="c in nextBlock.candidates" :key="c.userId" class="h5-pick"
                        :class="{ on: String(nextUserId) === String(c.userId) }" @click="pickNext(c.userId)">
                  <span class="h5-flex-1">
                    <span>{{ c.name }}</span>
                    <span class="h5-card__meta h5-card__meta--block">
                      {{ c.deptName || '—' }}<template v-if="c.pendingCount"> · 手上 {{ c.pendingCount }} 条</template>
                    </span>
                  </span>
                  <span class="h5-flag" :class="String(nextUserId) === String(c.userId) ? 'h5-flag--ok' : 'h5-flag--info'">
                    {{ String(nextUserId) === String(c.userId) ? '已选' : '选他' }}
                  </span>
                </button>
                <div class="h5-tip h5-tip--left">这一节由你指定办理人，提交后即流转给所选的人。</div>
                <div v-if="nextErr" class="h5-tip h5-tip--left">请先指定下一环节办理人再提交（服务端会拒收未点名的提交）。</div>
              </template>
            </template>
            <div v-else-if="nextBlock.pool" class="h5-tip h5-tip--left">
              该节点由共享任务池承接，提交后不指派到具体人，谁先「领取」谁办理<template v-if="nextBlock.candidates.length">（本单公司下 {{ nextBlock.candidates.length }} 人可办）</template>。
            </div>
            <div v-else class="h5-tip h5-tip--left">
              该节点按配置自动取人<template v-if="nextBlock.candidates.length">：{{ nextBlock.candidates.map((c) => c.name).join('、') }}</template>。
            </div>
          </div>

          <!-- 审批意见 + 动作（吸底） -->
          <div v-if="canApprove" class="h5-card">
            <div class="h5-section-title h5-flush">审批意见</div>
            <textarea v-model="comment" class="h5-textarea" rows="3"
                      :placeholder="opinionPlaceholder"></textarea>
            <div v-if="!comment.trim()" class="h5-tip h5-tip--left">意见必填（不少于 2 个字）——留空提交会被服务端拒绝。</div>
          </div>
        </section>

        <!-- 吸底动作栏：通过/驳回常驻，转办/加签收进「更多」-->
        <footer v-if="canApprove" class="h5-actionbar">
          <button class="h5-btn--sub h5-btn h5-actionbar__more" @click="showOps = true">更多</button>
          <button class="h5-btn-danger" :disabled="busy" :aria-busy="busy ? 'true' : 'false'" @click="decide(false)"><i v-if="busy" class="h5-spin" />驳回</button>
          <button class="h5-btn-ok" :disabled="busy" :aria-busy="busy ? 'true' : 'false'" @click="decide(true)"><i v-if="busy" class="h5-spin" />通过</button>
        </footer>
      </template>

      <div v-else class="h5-body">
        <div class="h5-state">
          <div class="h5-state__title">单据不存在或无权查看</div>
          <div class="h5-state__hint">可能已被提单人撤回，或不是你的待办</div>
          <button class="h5-state__act" @click="leave">返回工作台</button>
        </div>
      </div>
    </template>

    <!-- 更多操作面板（P1-6：转办 / 加签，与 PC 复用同一套接口与语义）
         P2-5：进出场交给 <Transition name="h5-pop">，面板位移动画见 h5-tokens.css 动效层④ -->
    <Transition name="h5-pop">
      <div v-if="showOps" class="h5-mask" @click="showOps = false">
        <div class="h5-sheet" @click.stop>
          <div class="h5-sheet__grip" />
          <div class="h5-sheet__title">这条待办的其它处理</div>
          <button class="h5-btn h5-btn--sub h5-mt-8" @click="openPick('transfer')">转办给他人</button>
          <button v-if="addSignAllowed" class="h5-btn h5-btn--sub h5-mt-8" @click="openPick('addSign')">加签（请他人先/后看）</button>
          <div v-if="!addSignAllowed" class="h5-tip h5-tip--left">本流程已关闭加签。</div>
          <button class="h5-btn h5-btn--sub h5-mt-8" @click="showOps = false">返回</button>
        </div>
      </div>
    </Transition>

    <!-- 选人面板 -->
    <Transition name="h5-pop">
      <div v-if="pickMode" class="h5-mask" @click="closePick">
        <div class="h5-sheet" @click.stop>
          <div class="h5-sheet__grip" />
          <div class="h5-sheet__title">{{ pickMode === 'transfer' ? '转办给' : '加签给' }}</div>
          <div v-if="candLoading" class="h5-tip"><i class="h5-spin" />名单加载中…</div>
          <div v-else-if="candError" class="h5-error">
            <div class="h5-error__d">{{ candError }}</div>
            <button class="h5-btn h5-btn--sub h5-mt-10" @click="loadCandidates">重试</button>
          </div>
          <template v-else>
            <div v-if="pickMode === 'addSign'" class="h5-chips">
              <button class="h5-chip" :class="{ on: addSignPosition === 'BEFORE' }" @click="addSignPosition = 'BEFORE'">加在我之前</button>
              <button class="h5-chip" :class="{ on: addSignPosition === 'AFTER' }" @click="addSignPosition = 'AFTER'">加在我之后</button>
            </div>
            <div class="h5-sheet__list">
              <button v-for="c in candidates" :key="c.userId" class="h5-pick"
                      :class="{ on: isPicked(c.userId) }" @click="tapCandidate(c)">
                <span class="h5-flex-1">
                  <span>{{ c.name || c.userName || c.userId }}</span>
                  <span class="h5-card__meta h5-card__meta--block">
                    {{ c.deptName || '—' }}<template v-if="c.pendingCount"> · 手上 {{ c.pendingCount }} 条</template>
                  </span>
                </span>
                <span class="h5-flag" :class="isPicked(c.userId) ? 'h5-flag--ok' : 'h5-flag--info'">
                  {{ isPicked(c.userId) ? '已选' : (pickMode === 'transfer' ? '转办' : '加签') }}
                </span>
              </button>
              <div v-if="!candidates.length" class="h5-state">
                <div class="h5-state__title">{{ candEmptyText }}</div>
                <div class="h5-state__hint">名单为空通常是没有可选办理岗，请在电脑端「系统管理 · 角色授权」补岗后重试</div>
              </div>
            </div>
            <div class="h5-field h5-field--col h5-mt-8">
              <label for="h5-pick-reason">{{ pickMode === 'transfer' ? '转办说明' : '加签理由' }}</label>
              <textarea id="h5-pick-reason" v-model="pickReason" class="h5-textarea" rows="2"
                        :placeholder="pickMode === 'transfer' ? '为什么转给他（将记录，审批人会看到）' : '请他看什么（必填）'"></textarea>
            </div>
            <button class="h5-btn h5-mt-12" :disabled="busy || !picked.length"
                    :aria-busy="busy ? 'true' : 'false'" @click="confirmPick">
              <i v-if="busy" class="h5-spin" />{{ pickMode === 'transfer' ? '确认转办' : '确认加签' }}
            </button>
          </template>
        </div>
      </div>
    </Transition>

    <!-- 影像大图（UX-03：审批人要能看清票据本身；不做在线预览器，列表＋大图即本期交付面） -->
    <Transition name="h5-fade">
      <div v-if="viewer != null" class="h5-lightbox" @click="viewer = null">
        <button class="h5-lightbox__x" aria-label="关闭大图" @click.stop="viewer = null">×</button>
        <img :src="viewer.url" :alt="viewer.name" @click.stop />
        <div class="h5-lightbox__cap">{{ viewer.name }}（{{ viewer.index + 1 }} / {{ thumbs.length }}）</div>
        <div class="h5-ops">
          <button class="h5-btn h5-btn--sub" @click.stop="stepThumb(-1)">上一张</button>
          <button class="h5-btn h5-btn--sub" @click.stop="stepThumb(1)">下一张</button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
/**
 * 移动报销端 · 审批详情页（P0-1/P0-6/P0-7 + P1-3/P1-6 落点）
 *
 * 改造前的第一条就是致命环节：`wfApi.approve({ taskId: Number(taskId) })`。
 * /wf/todo 下发的 taskId 是 **UUID 字符串**（ApproveReq.taskId 是 @NotBlank String），
 * Number(uuid)＝NaN ⇒ JSON 序列化成 {taskId:null} ⇒ 服务端 PARAM_INVALID/500，
 * 也就是"移动端从来没有批成功过一张单"。现原样传字符串。
 *
 * 第二条：canApprove 只看 URL 上有没有 taskId。现加三条真实判据
 *  ①单据状态在可办理区间（SUBMITTED/APPROVING；DRAFT/PAID/VOID 这些一律不给出动作）
 *  ②工作台带过来的锁定标记 lk=1（同池他人正在办）不放开
 *  ③bizResolved===false（待办关联单据没取到）不放开
 */
import { computed, nextTick, onActivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billApi, wfApi, invoiceApi, flowApi, fileApi, store } from '../../api'
import { hasPerm } from '../../utils/perm'
import { statusType as _statusType, statusLabel as _statusLabel } from '../../constants/billStatus'
import { verifyStatusLabel, docTypeLabel, wfActionLabel, wfActionType, wfNodeStateLabel, wfNodeStateType } from '../../constants/enums'
import { money, dayText, permDeniedOf, isExpBillCarrier } from '@/utils/h5.js'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'H5ApprovalDetail' })

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const bill = ref(null)
const lines = ref([])
const attaches = ref([])
const invoices = ref([])
const hist = ref([])
const comment = ref('')
const busy = ref(false)
const myName = computed(() => store.user?.realName || store.user?.nickName || store.user?.username || '我')

const taskId = computed(() => route.query.taskId ? String(route.query.taskId) : '')
const billId = computed(() => route.params.id)
const billType = computed(() => route.query.billType || '')
// 工作台跳转时带过来的两条真实判据（刷新页面会丢，丢了也只是回到"只看 taskId"的原规则）
const lockedByOther = computed(() => route.query.lk === '1')
const bizUnresolved = computed(() => route.query.bz === '0')
const addSignAllowed = computed(() => route.query.as !== '0')

// 可办理状态：只有这两态存在在办任务（WITHDRAWN/PAID/VOID 等给了按钮也是必被服务端拒）
const APPROVABLE = ['SUBMITTED', 'APPROVING']
const blockedReason = computed(() => {
  if (!taskId.value) return ''
  if (lockedByOther.value) return '同池他人正在办理这条待办，等他办完或让流程管理员改派。'
  if (bizUnresolved.value) return '这条待办没取到关联单据内容，请在电脑端核对后再办。'
  if (!isExpBillCarrier(billType.value)) return `这条待办对应的单据类型是「${billType.value || '其它域'}」，移动端只能审批费用报销单流程。`
  if (bill.value && !APPROVABLE.includes(bill.value.status)) return `单据当前状态是「${_statusLabel(bill.value.status)}」，没有在办任务可办。`
  return ''
})
const canApprove = computed(() => !!taskId.value && !blockedReason.value)
const opinionPlaceholder = computed(() => '请填写审批意见（必填，不少于 2 个字）')

// 异常单据横幅：只认显式负面状态（REPAID 这类「已还清」的正常最终状态不能被当成异常）
const ABNORMAL_STATUS = ['REJECTED', 'WITHDRAWN', 'VOID', 'TERMINATED', 'CANCELLED']
const abnormalText = computed(() => {
  const s = bill.value?.status
  return ABNORMAL_STATUS.includes(s) ? _statusLabel(s) : ''
})

function leave() {
  if (window.history.state && window.history.state.back) router.back()
  else router.replace('/h5')
}

/* ---------------- 影像（P0-7） ---------------- */
const IMG_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'jfif']
const thumbs = ref([])
const otherFiles = ref([])
const attachTokenFailed = ref(false)
const viewer = ref(null)

function extOf(name) {
  const s = String(name || '').toLowerCase()
  const i = s.lastIndexOf('.')
  return i >= 0 ? s.slice(i + 1) : ''
}
/**
 * 图片走缩略图宫格：每张要一次一次性下载令牌（fileApi.createToken）。
 * 令牌是一次性的 ⇒ 只给前 9 张取（一屏能看完的量），其余归到「打开」列表，
 * 免得审批人一进页面就打十几个 POST。
 */
async function buildFileView() {
  thumbs.value = []
  otherFiles.value = []
  attachTokenFailed.value = false
  const imgs = attaches.value.filter((a) => IMG_EXTS.includes(extOf(a.fileName)))
  const others = attaches.value.filter((a) => !IMG_EXTS.includes(extOf(a.fileName)))
  otherFiles.value = others
  const take = imgs.slice(0, 9)
  const rest = imgs.slice(9)
  for (const a of take) {
    try {
      const tk = await fileApi.createToken(a.fileId)
      if (!tk) { attachTokenFailed.value = true; continue }
      thumbs.value.push({ url: `/api/tenant/system/file/download?token=${tk}`, name: a.fileName || String(a.fileId), fileId: a.fileId })
    } catch { attachTokenFailed.value = true }
  }
  if (rest.length) otherFiles.value = [...others, ...rest]
}
async function openFile(a) {
  try {
    const tk = await fileApi.createToken(a.fileId)
    window.open(`/api/tenant/system/file/download?token=${tk}`, '_blank')
  } catch (e) { ElMessage.error('取址失败：' + (e.message || '网络异常')) }
}
function openLightbox(i) { viewer.value = { ...thumbs.value[i], index: i } }
function stepThumb(d) {
  if (!thumbs.value.length) return
  const next = (viewer.value.index + d + thumbs.value.length) % thumbs.value.length
  viewer.value = { ...thumbs.value[next], index: next }
}

/* ---------------- 加载 ---------------- */
const invDenied = ref(null)
const invError = ref('')
const histDenied = ref(null)
const histError = ref('')

/**
 * 「关联发票」面板的数据源是 /invoice/bill-invoices，后端挂 inv:list——
 * 该码只授发票、税务、共享三岗（实测七个 H5 岗位只有共享会计赵勇持），
 * 于是提报岗与审批岗每次进详情页都会被全局拦截器弹一次「您没有「发票·list」权限，请联系管理员开通」。
 * 面板本就分「缺权／出错／真没票」三态呈现（把缺权渲染成「暂无」＝假空，见 D-TRC 系列），
 * 所以无权时不必先撞一次再降级：直接落缺权态。有权才取数，并带 silent——
 * 服务端还有权限依赖闭包等第二道口径可能拒，那一次也不该在审批中途弹出来。
 * 票据影像走 fileApi（无权限闸），与本面板无关，审批照常可看。
 */
async function loadInvoices() {
  invDenied.value = null
  invError.value = ''
  if (!hasPerm('inv:list')) {
    invDenied.value = { msg: '', permKey: 'inv:list' }
    invoices.value = []
    return
  }
  try {
    invoices.value = (await invoiceApi.billInvoices(billId.value, { silent: true })) || []
  } catch (e) {
    // 实测：提报岗在发票/验真/历史三个面板都是 code:4030，之前一律渲染成「暂无」＝假空
    const d = permDeniedOf(e)
    if (d) invDenied.value = d
    else invError.value = e.message || '发票加载失败'
  }
}
async function loadHistory() {
  histDenied.value = null
  histError.value = ''
  // D-H5M-02/03：改走 /wf/query/chain（按本单实例取数，按单据可读性守卫）。
  // 旧实现传 businessKey ⇒ 后端落进「只查本人参与过的全部留痕」分支（混入别的单据），
  // 且该端点挂 wf:history:list ⇒ 多数业务账号直接 4030 看不到面板。
  const pid = bill.value?.procInstId
  if (!pid) { hist.value = []; return }
  try {
    const d = await flowApi.chain(pid, { silent: true })
    hist.value = Array.isArray(d) ? d : ((d && d.list) || [])
  } catch (e) {
    const x = permDeniedOf(e)
    if (x) histDenied.value = x
    else histError.value = e.message || '审批记录加载失败'
  }
}

/* ---------------- 下一环节办理人（D-H5M-01） ---------------- */
const nextInfo = ref(null)
const nextUserId = ref(null)
const nextErr = ref(false)

/** 与电脑端 ApprovalDialog.nextBlock 同一判定：PICK 需点名、POOL 先到先得、其余自动取人 */
const nextBlock = computed(() => {
  const n = nextInfo.value
  if (!n) return null
  return {
    nodeName: n.nodeName || '—',
    pick: n.mode === 'PICK',
    pool: n.mode === 'POOL',
    candidates: Array.isArray(n.candidates) ? n.candidates : [],
  }
})
/**
 * 候选预览（只读）。静默失败即不显示这一区——审批本身不依赖它，
 * 服务端 complete() 仍会独立校验点名（绕开界面直接调接口只会收到可读的 BizException）。
 */
async function loadNext() {
  nextInfo.value = null
  nextUserId.value = null
  nextErr.value = false
  if (!taskId.value) return
  try {
    const res = await wfApi.nextCandidates(taskId.value, { silent: true })
    if (!res || typeof res !== 'object') return
    nextInfo.value = res
    // 候选唯一：自动带出，避免"看起来没得选却被判未指定"（与电脑端同一处理）
    if (res.mode === 'PICK' && Array.isArray(res.candidates) && res.candidates.length === 1) {
      nextUserId.value = res.candidates[0].userId
    }
  } catch { /* 端点未部署/无权：静默不显示，审批照旧 */ }
}
function pickNext(userId) {
  nextUserId.value = userId
  nextErr.value = false
}

async function loadDetail() {
  loading.value = true
  loadError.value = ''
  try {
    const d = await billApi.detail(billId.value)
    bill.value = d.bill || null
    lines.value = d.lines || []
    attaches.value = d.attaches || []
    if (!bill.value) { loadError.value = '没取到这张单据。'; return }
    buildFileView()
    loadInvoices()
    loadHistory()
    loadNext()
  } catch (e) {
    // R5-D-052：拦截器已 toast 一次，closeAll 去重——本页只留 loadError 正文一处
    ElMessage.closeAll()
    const d = permDeniedOf(e)
    loadError.value = (d ? `无权查看这张单：${d.msg}` : `加载失败：${e.message || '网络异常'}`)
  } finally {
    nextTick(() => { loading.value = false })
  }
}

/* ---------------- 审批动作 ---------------- */
function requireOpinion(ok) {
  const c = String(comment.value || '').trim()
  if (c.length < 2) {
    // 原先留空会被静默补成「同意/驳回」——等于系统替审批人签了个字，现在一律拦回
    ElMessage.warning(ok ? '请填写审批意见（不少于 2 个字）' : '请填写驳回原因（不少于 2 个字）')
    return null
  }
  return c
}
async function decide(ok) {
  const c = requireOpinion(ok)
  if (!c) return
  // D-H5M-01：点名环节必须带上下一环节办理人（服务端 enforcePickOnApprove 拒收未点名的提交）。
  // 与电脑端同一判定：候选为空＝该节无人可派（提示补岗，不再让提交去吃红字）；候选非空未选＝拦回。
  if (ok && nextBlock.value?.pick) {
    if (!nextBlock.value.candidates.length) {
      nextErr.value = true
      ElMessage.warning('下一环节没有可选办理人，请先在「系统管理 · 角色授权」补岗')
      return
    }
    if (!nextUserId.value) {
      nextErr.value = true
      ElMessage.warning('请先指定下一环节办理人')
      return
    }
  }
  try {
    await ElMessageBox.confirm(
      ok ? `通过后单据进入下一环节（本单 ￥${money(bill.value?.totalAmount)}）。` : `驳回后单据退回提单人修改，请确认理由已写清。`,
      ok ? '确认通过' : '确认驳回',
      { type: ok ? 'warning' : 'error', confirmButtonText: ok ? '通过' : '驳回', cancelButtonText: '再想想' },
    )
  } catch { return }
  busy.value = true
  try {
    // P0-1：taskId 原样传字符串（UUID），不再 Number()
    await wfApi.approve({
      taskId: taskId.value,
      approved: ok,
      comment: c,
      nextAssigneeUserId: ok && nextUserId.value ? nextUserId.value : null,
    })
    ElMessage.success(ok ? '已通过' : '已驳回')
    router.replace('/h5')
  } catch (e) {
    ElMessage.error(e.message || '办理失败')
  } finally { busy.value = false }
}

/* ---------------- 转办 / 加签（P1-6） ---------------- */
const showOps = ref(false)
const pickMode = ref('')       // '' | 'transfer' | 'addSign'
const candidates = ref([])
const candEmpty = ref(false)
const candLoading = ref(false)
const candError = ref('')
const picked = ref([])
const pickReason = ref('')
const addSignPosition = ref('BEFORE')

const candEmptyText = computed(() => candEmpty.value ? '本节点没有可转办的人' : '名单为空')

async function loadCandidates() {
  candLoading.value = true
  candError.value = ''
  try {
    const d = (await wfApi.taskCandidates({ taskId: taskId.value })) || {}
    candidates.value = Array.isArray(d.candidates) ? d.candidates : []
    candEmpty.value = !!d.empty || !candidates.value.length
  } catch (e) {
    candidates.value = []
    candError.value = e.message || '名单加载失败'
  } finally { candLoading.value = false }
}
function openPick(mode) {
  pickMode.value = mode
  picked.value = []
  pickReason.value = ''
  showOps.value = false
  loadCandidates()
}
function closePick() { pickMode.value = ''; picked.value = [] }
function isPicked(id) { return picked.value.some((x) => String(x) === String(id)) }
function tapCandidate(c) {
  if (pickMode.value === 'transfer') { picked.value = [c.userId]; return } // 转办只能一个人
  picked.value = isPicked(c.userId) ? picked.value.filter((x) => String(x) !== String(c.userId)) : [...picked.value, c.userId]
}
async function confirmPick() {
  if (!picked.value.length) return ElMessage.warning('请先选人')
  const reason = String(pickReason.value || '').trim()
  if (pickMode.value === 'addSign' && reason.length < 2) return ElMessage.warning('加签理由必填（不少于 2 个字）')
  if (pickMode.value === 'transfer' && reason.length < 2) return ElMessage.warning('转办说明必填（审批人要看得见为什么转）')
  busy.value = true
  try {
    if (pickMode.value === 'transfer') {
      await wfApi.transfer({ taskId: taskId.value, targetUserId: picked.value[0], comment: reason })
      ElMessage.success('已转办')
    } else {
      await wfApi.addSign({
        taskId: taskId.value,
        position: addSignPosition.value,
        signUsers: picked.value.join(','),
        reason,
      })
      ElMessage.success('已加签')
    }
    closePick()
    router.replace('/h5')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally { busy.value = false }
}

function statusType(s) { return _statusType(s) }
function statusLabel(s) { return _statusLabel(s) }
function histType(a) { return wfActionType(a) }
function histLabel(a) { return a ? wfActionLabel(a) : '—' }
/** 审批链节点状态（/chain 的 state）：无 action 的节点（当前/未到达/作废）用它显示 */
function stateType(s) { return wfNodeStateType(s) }
function stateLabel(s) { return wfNodeStateLabel(s) || '—' }
function verifyLabel(v) { return v == null || v === '' ? '—' : verifyStatusLabel(v) }
function verifyTagType(v) {
  if (v === 'PASS' || v === 'SUCCESS') return 'success'
  if (v === 'FAIL' || v === 'FAILED' || v === 'REJECT') return 'danger'
  return 'info'
}

onMounted(loadDetail)
onActivated(() => { if (bill.value) loadDetail() })
</script>

<style scoped>
/* 只留本页特有的两件；其余全部来自 src/styles/h5-tokens.css */
.h5-abnormal { margin-bottom: var(--h5-gap-2); background: #fef0f0; border: 1px solid #fbc4c4; border-radius: var(--h5-radius); padding: var(--h5-pad); }
.h5-actionbar__more { flex: 0 0 64px; }
.h5-pick {
  display: flex; align-items: center; justify-content: space-between; gap: var(--h5-gap);
  width: 100%; text-align: left; background: #fff; border: 1px solid var(--h5-border);
  border-radius: var(--h5-radius-sm); padding: 8px 10px; margin-bottom: var(--h5-gap);
  min-height: var(--h5-tap-h); color: var(--h5-text);
}
.h5-pick.on { border-color: var(--h5-primary); background: var(--h5-primary-soft); }
</style>
