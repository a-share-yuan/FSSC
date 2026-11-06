<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!-- Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE) -->

<template>
  <div class="ai-assistant">
    <!-- 悬浮触发按钮 -->
    <el-button
      v-if="!visible"
      class="ai-fab"
      type="primary"
      circle
      size="large"
      @click="visible = true"
    >
      <el-icon :size="22"><ChatRound /></el-icon>
    </el-button>

    <!-- 对话面板 -->
    <div v-if="visible" class="ai-panel">
      <div class="ai-header">
        <div class="ai-title">
          <el-icon><ChatRound /></el-icon>
          <span>{{ $t('AIAssistant.main.AI') }}</span>
        </div>
        <div class="ai-header-right">
          <el-button link size="small" @click="switchTab('chat')">{{ $t('AIAssistant.main.Chat') }}</el-button>
          <el-button link size="small" @click="switchTab('confirm')">
            待我确认<template v-if="confirmList.length">（{{ confirmList.length }}）</template>
          </el-button>
          <el-button link size="small" @click="switchTab('skills')">能力</el-button>
          <el-button link size="small" @click="visible = false">
            <el-icon :size="18"><Close /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 确认人视角：待背书票据队列（决策属人，AI 只能出票不能自确） -->
      <div v-if="tab === 'confirm'" class="ai-messages">
        <el-alert
          v-if="confirmError"
          :title="confirmError"
          type="info"
          :closable="false"
          style="margin-bottom: 8px"
        />
        <div v-for="t in confirmList" :key="t.id" class="ticket-card">
          <div class="tk-head">
            <b>票据 #{{ t.id }}</b>
            <el-tag size="small" :type="ticketTagType(t.status)">{{ ticketText(t.status) }}</el-tag>
          </div>
          <div class="tk-row">动作：<code>{{ t.actionCode }}</code></div>
          <div class="tk-row">{{ t.actionMethod }} {{ t.actionUrl }}</div>
          <div v-if="t.argsText" class="tk-row">参数：<code>{{ t.argsText }}</code></div>
          <div class="tk-row">参数摘要：{{ short(t.paramsDigest) }}</div>
          <div class="tk-row">过期：{{ fmt(t.expireAt) }}</div>
          <div class="tk-actions">
            <el-button size="small" type="success" :loading="busy === t.id" @click="decide(t.id, true)">
              确认执行
            </el-button>
            <el-button size="small" @click="decide(t.id, false)">驳回</el-button>
          </div>
        </div>
        <div v-if="!confirmList.length && !confirmError" class="empty-tip">当前没有等您背书的票据。</div>
      </div>

      <!-- 能力地图：服务端按本人权限剪枝，越权技能不出现 -->
      <div v-else-if="tab === 'skills'" class="ai-messages">
        <el-alert
          v-if="skillsError"
          :title="skillsError"
          type="info"
          :closable="false"
          style="margin-bottom: 8px"
        />
        <div v-for="g in skillGroups" :key="g.type" class="skill-group">
          <div class="skill-group-title">{{ g.label }}（{{ g.items.length }}）</div>
          <div v-for="s in g.items" :key="s.name" class="skill-card">
            <div class="skill-name">{{ s.title }}</div>
            <div class="skill-desc">{{ s.description }}</div>
            <div v-if="s.examples && s.examples.length" class="skill-example">例：{{ s.examples[0] }}</div>
          </div>
        </div>
        <div v-if="!skillGroups.length && !skillsError" class="empty-tip">暂无可用能力。</div>
      </div>

      <!-- 下令者视角：对话 + 指令执行链 -->
      <div v-else ref="msgList" class="ai-messages">
        <div v-for="(msg, i) in messages" :key="i" class="msg-item" :class="msg.role">
          <el-avatar v-if="msg.role === 'ai'" :size="28" class="msg-avatar">AI</el-avatar>
          <div class="msg-body">
            <div v-if="msg.text" class="msg-bubble">{{ msg.text }}</div>

            <!-- 数据卡：只读查询的结构化结果 -->
            <div v-if="msg.data" class="data-card">
              <div class="dc-head">
                <b>数据结果</b>
                <span v-if="msg.data.total != null" class="dc-total">共 {{ msg.data.total }} 条</span>
              </div>
              <table v-if="rowsOf(msg.data).length" class="dc-table">
                <thead>
                  <tr><th v-for="c in colsOf(rowsOf(msg.data))" :key="c">{{ c }}</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(r, ri) in rowsOf(msg.data)" :key="ri">
                    <td v-for="c in colsOf(rowsOf(msg.data))" :key="c">{{ cell(r, c) }}</td>
                  </tr>
                </tbody>
              </table>
              <div v-else class="dc-empty">接口没有返回可展示的列表。</div>
            </div>

            <!-- 票据卡：写动作只出票，不执行 -->
            <div v-for="t in msg.tickets" :key="t.confirmId" class="ticket-card">
              <div class="tk-head">
                <b>票据 #{{ t.confirmId }}</b>
                <el-tag size="small" :type="ticketTagType(t.status)">{{ ticketText(t.status) }}</el-tag>
              </div>
              <div v-if="t.preview" class="tk-row tk-preview">{{ t.preview }}</div>
              <div class="tk-row">动作：<code>{{ t.action }}</code>（风险档 {{ riskLevelLabel(t.riskLevel) }}）</div>
              <div class="tk-row">参数：<code>{{ JSON.stringify(t.args) }}</code></div>
              <div class="tk-row">摘要：{{ short(t.paramsDigest) }}｜过期：{{ fmt(t.expireAt) }}</div>
              <div v-if="t.missing" class="tk-row warn">我还缺：{{ t.missing }}（主数据 ID 我不会编造，请补全或驳回）</div>
              <div v-if="t.status === 'PENDING'" class="tk-row">
                现在请：换一位有确认权限的同事登录，在本助手「待我确认」里点确认。
                确认后我以下令者本人的权限自动执行，无需您再操作。
              </div>
              <div v-if="t.result" class="tk-row ok">执行成功，结果：<code>{{ JSON.stringify(t.result).slice(0, 300) }}</code></div>
              <div v-if="t.error" class="tk-row bad">执行失败：{{ t.error }}</div>
              <div v-if="t.status === 'PENDING'" class="tk-actions">
                <el-button size="small" @click="cancelTicket(t)">撤回这张票</el-button>
              </div>
            </div>

            <!-- 知识出处 -->
            <div v-if="msg.citations && msg.citations.length" class="cite-card">
              <div class="cite-title">出处</div>
              <div v-for="(c, ci) in msg.citations" :key="ci" class="cite-row">
                <b>[{{ ci + 1 }}]</b> 《{{ c.title }}》
                <span v-if="c.heading"> · {{ c.heading }}</span>
                <div class="cite-path">{{ c.docPath }}</div>
              </div>
            </div>

            <!-- 导航落点 -->
            <div v-if="msg.route" class="nav-card">
              入口：<a :href="msg.route">{{ msg.route }}</a>
            </div>

            <!-- 反馈：只用于运营评测，不参与执行 -->
            <div v-if="msg.role === 'ai' && msg.traceId" class="feedback-row">
              <template v-if="!msg.feedback">
                <el-button link size="small" @click="sendFeedback(msg, 1, '有帮助')"> 有帮助</el-button>
                <el-button link size="small" @click="sendFeedback(msg, -1, '不准')"> 不准</el-button>
                <el-button link size="small" @click="sendFeedback(msg, -1, '没查到')">没查到</el-button>
                <el-button link size="small" @click="sendFeedback(msg, -1, '不是我要的')">不是我要的</el-button>
              </template>
              <span v-else class="feedback-done">{{ msg.feedback }}</span>
            </div>

            <div v-if="msg.meta" class="msg-meta">{{ msg.meta }}</div>
          </div>
        </div>
        <div v-if="loading" class="msg-item ai">
          <el-avatar :size="28" class="msg-avatar">AI</el-avatar>
          <div class="msg-bubble typing">
            <span class="dot" v-for="n in 3" :key="n" :style="{ animationDelay: n * 0.2 + 's' }" />
          </div>
        </div>
      </div>

      <!-- 快捷问题 -->
      <div class="quick-questions" v-if="tab === 'chat' && messages.length <= 1">
        <el-button v-for="q in quickQuestions" :key="q" size="small" round @click="askQuestion(q)">
          {{ q }}
        </el-button>
      </div>

      <!-- 输入区 -->
      <div class="ai-input-area" v-if="tab === 'chat'">
        <el-input
          v-model="input"
          placeholder="说出你要查什么或做什么...（回车发送）"
          :autosize="{ minRows: 1, maxRows: 3 }"
          type="textarea"
          resize="none"
          @keydown.enter.prevent="send"
        />
        <el-button type="primary" :icon="Promotion" circle size="small" @click="send" :loading="loading" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onUnmounted } from 'vue'
import { ChatRound, Close, Promotion } from '@element-plus/icons-vue'
import { aiApi } from '../api'
import { riskLevelLabel } from '@/constants/enums.js'

const visible = ref(false)
const tab = ref('chat')
const input = ref('')
const loading = ref(false)
const msgList = ref(null)

const confirmList = ref([])
const confirmError = ref('')
const busy = ref(null)

const skillsError = ref('')
const skillList = ref([])

/** 会话号：同标签页内保持，服务端按 租户+用户 再圈一次；"改成1200/再填一张"靠它继承上下文。 */
const sessionId = ref(newSessionId())

/** 等待他人背书的票据（下令侧会话跟踪，5s 轮询自己的票据状态） */
const tracked = ref([])
const executedIds = new Set()
let pollTimer = null

const messages = ref([
  {
    role: 'ai',
    text: '你好！我是 FSSC AI 助手。\n我能做两件事：\n① 查数：只读、按你本人的权限取，立即出结果；\n② 办事：先出确认票据，另一位真人背书后才执行，我不自行落地。\n试试：我今天有多少待办？ 或 帮我填写1000块的借款，再填写一个1200块的报销单据',
  },
])

const quickQuestions = [
  '我今天有多少待办？',
  '帮我填写1000块的借款，再填写一个1200块的报销单据',
  '上个月的报销单有哪些？',
  '报销流程是什么？',
]

function newSessionId() {
  const key = 'fssc-ai-session'
  try {
    let v = sessionStorage.getItem(key)
    if (!v) {
      v = (typeof crypto !== 'undefined' && crypto.randomUUID)
        ? crypto.randomUUID()
        : `s-${Date.now()}-${Math.random().toString(16).slice(2)}`
      sessionStorage.setItem(key, v)
    }
    return v
  } catch (e) {
    return `s-${Date.now()}`
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight
  })
}

function short(d) { return d ? String(d).slice(0, 12) + '…' : '-' }
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }

/** 服务端回答含少量 <b>/<br> 排版标记；这里统一转为纯文本渲染，面板不再有 v-html 注入面。 */
function stripHtml(s) {
  return String(s == null ? '' : s)
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(b|strong|i|em|p|div|small)>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function ticketText(s) {
  return ({
    PENDING: '等待他人确认', CONFIRMED: '已确认·正在执行', USED: '已执行（票据已消耗）',
    EXPIRED: '已过期作废', REJECTED: '已被驳回', FAILED: '执行失败', EXECUTED: '已执行',
    CANCELLED: '已撤回',
  })[s] || s
}
function ticketTagType(s) {
  if (s === 'USED' || s === 'EXECUTED') return 'success'
  if (s === 'EXPIRED' || s === 'REJECTED' || s === 'FAILED') return 'danger'
  if (s === 'CANCELLED') return 'info'
  return 'warning'
}

const SKILL_TYPE_LABEL = { read: '查数能力', write: '办事能力（先出票）', knowledge: '知识问答', nav: '页面导航' }
// 必须是 computed：写成普通函数时模板里的 v-for 拿不到数组（renderList 不接函数），
// 而 `skillGroups.length` 取到的是函数的形参个数（0），于是能力地图永远显示"暂无可用能力"。
const skillGroups = computed(() => {
  const map = {}
  for (const s of skillList.value) {
    const type = s.type || 'other'
    if (!map[type]) map[type] = { type, label: SKILL_TYPE_LABEL[type] || type, items: [] }
    map[type].items.push(s)
  }
  return Object.values(map)
})

function rowsOf(data) {
  if (!data) return []
  if (Array.isArray(data.top5)) return data.top5
  if (Array.isArray(data)) return data
  return []
}

const PREFERRED_COLS = [
  'billNo', 'invoiceNo', 'voucherNo', 'code', 'name', 'title', 'nodeName',
  'statusName', 'status', 'amount', 'totalAmount', 'createTime', 'applyDate', 'dueTime',
]
function colsOf(rows) {
  const present = new Set()
  for (const r of rows.slice(0, 3)) {
    if (r && typeof r === 'object') Object.keys(r).forEach((k) => present.add(k))
  }
  const cols = PREFERRED_COLS.filter((k) => present.has(k))
  if (cols.length) return cols.slice(0, 5)
  return Array.from(present).slice(0, 4)
}
function cell(row, key) {
  const v = row == null ? null : row[key]
  if (v == null) return '-'
  if (typeof v === 'object') return JSON.stringify(v).slice(0, 40)
  return String(v)
}

function switchTab(t) {
  tab.value = t
  if (t === 'confirm') loadConfirms()
  if (t === 'skills') loadSkills()
}

async function loadConfirms() {
  confirmError.value = ''
  try {
    confirmList.value = (await aiApi.pendingTickets()) || []
  } catch (e) {
    confirmList.value = []
    confirmError.value = '本账号看不到确认队列：' + (e.message || '需要「AI 确认」权限（当前开放给财务主管／共享服务主管）')
  }
}

async function loadSkills() {
  skillsError.value = ''
  try {
    skillList.value = (await aiApi.skills()) || []
  } catch (e) {
    skillList.value = []
    skillsError.value = '读取能力地图失败：' + (e.message || '稍后再试')
  }
}

async function decide(id, approve) {
  busy.value = id
  try {
    await aiApi.approveTicket(id, approve, approve ? '人工背书' : '人工驳回')
    await loadConfirms()
    // 让下令侧轮询立刻看到状态翻转，不必等下一个 5s
    pollTracked()
  } catch (e) { /* 拦截器已提示 */ }
  busy.value = null
}

async function askQuestion(q) {
  input.value = q
  await send()
}

async function send() {
  const q = input.value.trim()
  if (!q || loading.value) return

  messages.value.push({ role: 'user', text: q })
  input.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const out = await processQuery(q)
    messages.value.push({
      role: 'ai',
      text: stripHtml(out.text),
      data: out.data,
      citations: out.citations,
      route: out.route,
      tickets: out.tickets,
      traceId: out.traceId,
      sessionId: out.sessionId,
      skill: out.skill,
      question: q,
      meta: out.meta,
    })
    for (const t of out.tickets) trackTicket(t)
  } catch (e) {
    messages.value.push({ role: 'ai', text: `本次没有执行任何系统操作：${e.message || '通道异常'}` })
  }

  loading.value = false
  scrollToBottom()
}

/** 只读动作直接出结果；写动作返回票据（可能一次多张），交由轮询链在确认后执行。 */
async function processQuery(q) {
  const r = await aiApi.instruct(q, sessionId.value)
  if (!r) throw new Error('服务端没有返回结果')
  if (r.sessionId) sessionId.value = r.sessionId

  const tickets = []
  const fromStep = (s) => ({
    confirmId: s.confirmId,
    action: s.action,
    args: s.args || {},
    riskLevel: s.riskLevel,
    paramsDigest: s.paramsDigest,
    expireAt: s.expireAt,
    skill: s.skill,
    preview: s.preview,
    missing: s.missing,
    status: 'PENDING',
    question: s.question || q,
  })
  if (Array.isArray(r.steps) && r.steps.length) {
    for (const s of r.steps) if (s.needsConfirm) tickets.push(fromStep(s))
  } else if (r.needsConfirm) {
    tickets.push({
      confirmId: r.confirmId,
      action: r.action,
      args: r.args || {},
      riskLevel: r.riskLevel,
      paramsDigest: r.paramsDigest,
      expireAt: r.expireAt,
      skill: r.skill,
      preview: r.preview,
      missing: r.missing,
      status: 'PENDING',
      question: q,
    })
  }

  const metaParts = []
  if (r.action) metaParts.push(`动作=${r.action}`)
  if (r.permCode) metaParts.push(`权限码=${r.permCode}`)
  if (r.skill) metaParts.push(`技能=${r.skill}`)
  if (r.engine) metaParts.push(`引擎=${r.engine}`)
  if (r.traceId) metaParts.push(`traceId=${r.traceId}`)
  if (r.channelError) metaParts.push(`大模型通道不可用：${r.channelError}`)

  return {
    text: r.answer || '',
    data: r.data,
    citations: r.citations,
    route: r.route,
    tickets,
    traceId: r.traceId,
    sessionId: r.sessionId || sessionId.value,
    skill: r.skill,
    meta: metaParts.join('｜'),
  }
}

async function sendFeedback(msg, rating, reason) {
  try {
    await aiApi.feedback({
      traceId: msg.traceId,
      sessionId: msg.sessionId,
      skill: msg.skill,
      question: msg.question,
      answer: msg.text,
      rating,
      reason,
    })
    msg.feedback = rating === 1 ? '已记录：有帮助' : `已记录：${reason}`
  } catch (e) {
    msg.feedback = '反馈失败：' + (e.message || '稍后再试')
  }
}

async function cancelTicket(t) {
  try {
    await aiApi.cancelTicket(t.confirmId)
    t.status = 'CANCELLED'
    pollTracked()
  } catch (e) {
    t.error = e.message || '撤回失败'
  }
}

// 票据状态轮询 → 确认后自动执行

function trackTicket(t) {
  tracked.value.push(t)
  if (!pollTimer) pollTimer = setInterval(pollTracked, 5000)
}

async function pollTracked() {
  const waiting = tracked.value.filter((x) => x.status === 'PENDING')
  if (!waiting.length) {
    if (!tracked.value.some((x) => x.status === 'PENDING')) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    return
  }
  let rows = []
  try {
    rows = (await aiApi.myTickets()) || []
  } catch (e) {
    return // 通道抖动，下一轮再试
  }
  for (const w of waiting) {
    const row = rows.find((r) => String(r.id) === String(w.confirmId))
    if (!row) continue
    if (row.status === 'CONFIRMED') {
      w.status = 'CONFIRMED'
      await runConfirmed(w)
    } else if (['USED', 'EXPIRED', 'REJECTED', 'CANCELLED'].includes(row.status)) {
      w.status = row.status
    }
  }
}

/** 已确认票据 → 走网关执行（同一条校验；带唯一键，重放不产生第二张单）。 */
async function runConfirmed(w) {
  if (executedIds.has(w.confirmId)) return
  executedIds.add(w.confirmId)
  try {
    const res = await aiApi.execute(
      { instruction: w.question, model: 'web-assistant', action: w.action, args: w.args, confirmId: Number(w.confirmId) },
      `AIUI-${w.confirmId}`,
    )
    w.result = res
    w.status = 'USED'
  } catch (e) {
    w.error = e.message
    w.status = 'FAILED'
  }
}

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
})
</script>

<style scoped>
.ai-assistant { position: fixed; bottom: 24px; right: 24px; z-index: 9999; }
.ai-fab { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); }
.ai-panel {
  position: fixed; bottom: 24px; right: 24px;
  width: 420px; height: 600px;
  background: var(--app-card-bg, #fff); border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  display: flex; flex-direction: column; overflow: hidden;
}
.ai-header { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border-bottom: 1px solid var(--app-border, #ebeef5); background: var(--app-bg, #f5f7fa); }
.ai-title { display: flex; align-items: center; gap: 6px; font-weight: 600; font-size: 14px; color: #409eff; }
.ai-header-right { display: flex; align-items: center; gap: 2px; }
.ai-messages { flex: 1; overflow-y: auto; padding: 12px; }
.msg-item { display: flex; gap: 8px; margin-bottom: 12px; }
.msg-item.user { flex-direction: row-reverse; }
.msg-avatar { background: #409eff; color: #fff; font-size: 11px; flex-shrink: 0; }
.msg-body { max-width: 82%; display: flex; flex-direction: column; gap: 6px; }
.msg-item.user .msg-body { align-items: flex-end; }
.msg-bubble {
  padding: 8px 12px; border-radius: 10px;
  font-size: 13px; line-height: 1.6;
  white-space: pre-wrap; word-break: break-word;
}
.msg-item.ai .msg-bubble { background: var(--app-hover, #f5f7fa); color: var(--app-text, #303133); border-top-left-radius: 2px; }
.msg-item.user .msg-bubble { background: #409eff; color: #fff; border-top-right-radius: 2px; }
.msg-meta { font-size: 11px; color: #909399; word-break: break-all; }
.data-card { width: 100%; border: 1px solid var(--app-border, #dcdfe6); border-radius: 8px; padding: 8px 10px; background: var(--app-card-bg, #fff); }
.dc-head { display: flex; justify-content: space-between; font-size: 12px; color: var(--app-text, #303133); margin-bottom: 6px; }
.dc-total { color: #409eff; }
.dc-table { width: 100%; border-collapse: collapse; font-size: 11px; }
.dc-table th, .dc-table td { border-bottom: 1px solid var(--app-border, #f0f2f5); padding: 4px 3px; text-align: left; vertical-align: top; word-break: break-all; }
.dc-table th { color: #909399; font-weight: 500; }
.dc-empty { font-size: 12px; color: #909399; }
.cite-card { width: 100%; border-left: 3px solid #409eff; background: rgba(64, 158, 255, .12); border-radius: 6px; padding: 6px 10px; font-size: 12px; }
.cite-title { font-weight: 600; margin-bottom: 4px; }
.cite-row { color: var(--app-text-secondary, #606266); margin-bottom: 4px; }
.cite-path { color: #909399; font-size: 11px; word-break: break-all; }
.nav-card { font-size: 12px; }
.nav-card a { color: #409eff; }
.typing { display: flex; gap: 4px; align-items: center; }
.typing .dot { width: 6px; height: 6px; border-radius: 50%; background: #909399; animation: bounce 1s infinite; }
@keyframes bounce { 0%, 60%, 100% { transform: translateY(0); } 30% { transform: translateY(-6px); } }
.quick-questions { display: flex; flex-wrap: wrap; gap: 6px; padding: 8px 12px; border-top: 1px solid var(--app-border, #f5f5f5); }
.ai-input-area { display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid var(--app-border, #ebeef5); align-items: flex-end; }
.ticket-card {
  width: 100%; border: 1px solid #e6a23c33; background: rgba(230, 162, 60, .14); border-radius: 8px;
  padding: 8px 10px; font-size: 12px; line-height: 1.6; margin-bottom: 4px;
}
.tk-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.tk-row { color: var(--app-text-secondary, #606266); word-break: break-all; }
.tk-preview { color: var(--app-text, #303133); font-weight: 500; }
.tk-row code { background: var(--app-hover, #fff); padding: 0 3px; border-radius: 3px; }
.tk-row.ok { color: #67c23a; }
.tk-row.bad { color: #f56c6c; }
.tk-actions { display: flex; gap: 6px; margin-top: 6px; }
.feedback-row { display: flex; gap: 2px; align-items: center; flex-wrap: wrap; }
.feedback-done { font-size: 11px; color: #909399; }
.skill-group { margin-bottom: 12px; }
.skill-group-title { font-size: 12px; font-weight: 600; color: #409eff; margin-bottom: 4px; }
.skill-card { border: 1px solid var(--app-border, #ebeef5); border-radius: 6px; padding: 6px 8px; margin-bottom: 4px; }
.skill-name { font-size: 12px; font-weight: 600; color: var(--app-text, #303133); }
.skill-desc { font-size: 11px; color: var(--app-text-secondary, #606266); }
.skill-example { font-size: 11px; color: #909399; }
.empty-tip { color: #909399; font-size: 12px; text-align: center; padding: 24px 0; }
.warn { color: #e6a23c; }
</style>
