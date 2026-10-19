<!-- SPDX-License-Identifier: Apache-2.0 -->
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
          <span>AI 助手</span>
        </div>
        <div class="ai-header-right">
          <el-button link size="small" @click="switchTab('chat')">对话</el-button>
          <el-button link size="small" @click="switchTab('confirm')">
            待我确认<template v-if="confirmList.length">（{{ confirmList.length }}）</template>
          </el-button>
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
          <div class="tk-row">动作：{{ t.actionCode }}</div>
          <div class="tk-row">{{ t.actionMethod }} {{ t.actionUrl }}</div>
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

      <!-- 下令者视角：对话 + 指令执行链 -->
      <div v-else ref="msgList" class="ai-messages">
        <div v-for="(msg, i) in messages" :key="i" class="msg-item" :class="msg.role">
          <el-avatar v-if="msg.role === 'ai'" :size="28" class="msg-avatar">AI</el-avatar>
          <div v-if="msg.ticket" class="ticket-card">
            <div class="tk-head">
              <b>票据 #{{ msg.ticket.confirmId }}</b>
              <el-tag size="small" :type="ticketTagType(msg.ticket.status)">{{ ticketText(msg.ticket.status) }}</el-tag>
            </div>
            <div class="tk-row">动作：<code>{{ msg.ticket.action }}</code>（风险档 {{ riskLevelLabel(msg.ticket.riskLevel) }}）</div>
            <div class="tk-row">参数：<code>{{ JSON.stringify(msg.ticket.args) }}</code></div>
            <div class="tk-row">摘要：{{ short(msg.ticket.paramsDigest) }}｜过期：{{ fmt(msg.ticket.expireAt) }}</div>
            <div v-if="msg.ticket.missing" class="tk-row warn">我还缺：{{ msg.ticket.missing }}（主数据 ID 我不会编造，请补全或驳回）</div>
            <div class="tk-row">
              现在请：换一位有 <code>ai:confirm:approve</code> 权限的真人登录，在本助手「待我确认」里点确认。
              确认后我以下令者本人的权限自动执行，无需您再操作。
            </div>
            <div v-if="msg.ticket.result" class="tk-row ok">
              执行成功，结果：<code>{{ JSON.stringify(msg.ticket.result).slice(0, 300) }}</code>
            </div>
            <div v-if="msg.ticket.error" class="tk-row bad">执行失败：{{ msg.ticket.error }}</div>
          </div>
          <div v-else class="msg-bubble" v-html="msg.text"></div>
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
          placeholder="说出你要查什么或做什么... (Enter 发送)"
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
import { ref, nextTick, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ChatRound, Close, Promotion } from '@element-plus/icons-vue'
import { billApi, wfApi, aiApi } from '../api'
import { riskLevelLabel } from '@/constants/enums.js'

const route = useRoute()
const visible = ref(false)
const tab = ref('chat')
const input = ref('')
const loading = ref(false)
const msgList = ref(null)

const confirmList = ref([])
const confirmError = ref('')
const busy = ref(null)

/** 等待他人背书的票据（下令侧会话跟踪，5s 轮询自己的票据状态） */
const tracked = ref([])
const executedIds = new Set()
let pollTimer = null

const messages = ref([
  {
    role: 'ai',
    text: '<b>你好！我是 FSSC AI 助手</b><br/>我能做两件事：<br/>① 查数（只读、按你本人的权限取，立即出结果）；<br/>② 办事（写操作）——我会先出<b>确认票据</b>，请另一位真人背书后才执行，绝不自行落地。<br/>试试：<i>我今天有多少待办？</i> 或 <i>建一张 800 元的差旅报销单</i>',
  },
])

const quickQuestions = [
  '我今天有多少待办？',
  '我司未派工的对账任务有多少条？',
  '帮我查单据',
  '报销流程是什么？',
  '快捷键有哪些？',
]

function scrollToBottom() {
  nextTick(() => {
    if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight
  })
}

function short(d) { return d ? String(d).slice(0, 12) + '…' : '-' }
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }
function ticketText(s) {
  return ({ PENDING: '等待他人确认', CONFIRMED: '已确认·正在执行', USED: '已执行（票据已消耗）', EXPIRED: '已过期作废', REJECTED: '已被驳回', FAILED: '执行失败', EXECUTED: '已执行' })[s] || s
}
function ticketTagType(s) {
  if (s === 'USED' || s === 'EXECUTED') return 'success'
  if (s === 'EXPIRED' || s === 'REJECTED' || s === 'FAILED') return 'danger'
  return 'warning'
}

function switchTab(t) {
  tab.value = t
  if (t === 'confirm') loadConfirms()
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

async function decide(id, approve) {
  busy.value = id
  try {
    await aiApi.approveTicket(id, approve, approve ? '人工背书' : '人工驳回')
    await loadConfirms()
    // 让下令侧轮询立刻看到状态翻转，不必等下一个 5s
    pollTracked()
  } catch (e) { /* 拦截器已弹错 */ }
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

  let answer = ''
  let ticket = null
  try {
    const out = await processQuery(q)
    answer = out.text
    if (out.ticket) ticket = { ...out.ticket, question: q, status: 'PENDING' }
  } catch (e) {
    answer = `抱歉，查询出错：${e.message}`
  }

  loading.value = false
  messages.value.push({ role: 'ai', text: answer, ticket })
  // 渲染与轮询必须指向同一个对象，票据状态翻转才会在对话里长出来
  if (ticket) trackTicket(ticket)
  scrollToBottom()
}

/** 只读动作直接出结果；写动作返回票据对象，交由轮询链在确认后执行。 */
async function processQuery(q) {
  let instructErr = null
  try {
    const r = await aiApi.instruct(q)
    if (r && (r.answer || r.data)) {
      let out = `<b>${r.answer}</b>`
      if (r.data) out += `<br/><pre class="ai-data">${JSON.stringify(r.data, null, 1).slice(0, 600)}</pre>`
      if (r.action) out += `<br/><small>动作=${r.action}｜权限码=${r.permCode || '-'}｜引擎=${r.engine || '-'}｜traceId=${r.traceId || '-'}</small>`
      if (r.engine === 'local-rule') {
        out += `<br/><small class="warn">本轮由本地规则匹配选型（大模型通道不可用），换说法可能识别不到。</small>`
      }
      if (r.needsConfirm) {
        return {
          text: '',
          ticket: {
            confirmId: r.confirmId, action: r.action, args: r.args || {}, riskLevel: r.riskLevel,
            paramsDigest: r.paramsDigest, expireAt: r.expireAt, engine: r.engine, missing: r.missing,
          },
        }
      }
      return { text: out }
    }
  } catch (e) { instructErr = e }

  const local = await localRule(q)
  if (!instructErr) return { text: local }
  // 兜底可以答，但不能把"通道报错"粉饰成"没听懂这句话"
  return { text: `<small class="warn">指令通道本轮报错：${instructErr.message}（以下为离线参考回答，未执行系统操作）</small><br/>${local}` }
}

/** 网关/目录未就绪时的本地兜底问答（与后端 local-rule 同源思路，纯前端可答的三问）。 */
async function localRule(q) {
  if (q.includes('待办') || q.includes('审批') || q.toLowerCase().includes('todo')) {
    try {
      const todo = await wfApi.todo()
      const cnt = Array.isArray(todo) ? todo.length : 0
      if (cnt === 0) return '你当前<b>没有待办审批</b>，一切处理完毕！'
      const types = {}
      todo.forEach(t => { types[t.billType || '其他'] = (types[t.billType || '其他'] || 0) + 1 })
      let detail = Object.entries(types).map(([k, v]) => `${k}: ${v}件`).join('，')
      return `你有 <b>${cnt}</b> 条待办审批。<br/>分类：${detail}<br/><a href="#/approval">点击前往审批</a>`
    } catch (e) { return '查询待办失败，请稍后重试。' }
  }

  if (q.includes('报销') && (q.includes('流程') || q.includes('怎么'))) {
    return `<b>报销流程：</b><br/>
      1. 填写报销单（费用报账 > 新建）<br/>
      2. 上传发票附件（支持OCR自动识别）<br/>
      3. 提交审批，进入工作流<br/>
      4. 审批人通过/驳回/核减<br/>
      5. 审批通过后自动生成凭证<br/>
      6. 进入付款流程<br/>
      7. 付款完成后归档`
  }

  if (q.includes('快捷键') || q.toLowerCase().includes('shortcut')) {
    return `<b>快捷键：</b><br/>
      Ctrl+K: 全局搜索<br/>
      Ctrl+/: 快捷键帮助<br/>
      Ctrl+D: 收藏当前页<br/>
      Ctrl+B: 打开收藏夹<br/>
      Ctrl+E: 效率统计<br/>
      Ctrl+H: 切换深/浅色<br/>
      Alt+1~4: 快速导航<br/>
      Esc: 关闭弹窗`
  }

  if (q.includes('查') && q.includes('单')) {
    try {
      const res = await billApi.myPage({ pageNum: 1, pageSize: 5 })
      const list = res.list || []
      if (list.length === 0) return '未找到你的单据。'
      let rows = list.map(b => `${b.billNo || b.id} (${b.billType || '-'}) - ${b.status || '-'}`).join('<br/>')
      return `你的最近 5 条单据：<br/>${rows}`
    } catch (e) { return '查询单据失败，请稍后重试。' }
  }

  if (q.includes('当前') || q.includes('这是哪')) {
    return `当前页面：<b>${route.meta?.title || route.path}</b><br/>路径：${route.path}`
  }

  return `我没能把这句话对到目录里的动作上。<br/>可以换个说法，或直接问：<br/>
    - 查询待办审批<br/>
    - 查询最近单据<br/>
    - 解释报销流程<br/>
    我不会替你发明一个系统里不存在的操作。`
}

// ------------------------------------------------ 票据状态轮询 → 确认后自动执行

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
    } else if (['USED', 'EXPIRED', 'REJECTED'].includes(row.status)) {
      w.status = row.status
    }
  }
}

/** 已确认票据 → 走网关执行（同一条闸门；带幂等键，重放不产生第二张单）。 */
async function runConfirmed(w) {
  if (executedIds.has(w.confirmId)) return
  executedIds.add(w.confirmId)
  try {
    const res = await aiApi.execute(
      { instruction: w.question, model: w.engine || 'web-assistant', action: w.action, args: w.args, confirmId: Number(w.confirmId) },
      `AIUI-${w.confirmId}-${Date.now()}`,
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
  width: 400px; height: 560px;
  background: #fff; border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  display: flex; flex-direction: column; overflow: hidden;
}
.ai-header { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border-bottom: 1px solid #ebeef5; background: #f5f7fa; }
.ai-title { display: flex; align-items: center; gap: 6px; font-weight: 600; font-size: 14px; color: #409eff; }
.ai-header-right { display: flex; align-items: center; gap: 2px; }
.ai-messages { flex: 1; overflow-y: auto; padding: 12px; }
.msg-item { display: flex; gap: 8px; margin-bottom: 12px; }
.msg-item.user { flex-direction: row-reverse; }
.msg-avatar { background: #409eff; color: #fff; font-size: 11px; flex-shrink: 0; }
.msg-bubble {
  max-width: 75%; padding: 8px 12px; border-radius: 10px;
  font-size: 13px; line-height: 1.6;
}
.msg-item.ai .msg-bubble { background: #f5f7fa; color: #303133; border-top-left-radius: 2px; }
.msg-item.user .msg-bubble { background: #409eff; color: #fff; border-top-right-radius: 2px; }
.msg-bubble a { color: #409eff; }
.ai-data { background: #f8f9fb; border-radius: 6px; padding: 6px 8px; font-size: 11px; max-height: 160px; overflow: auto; white-space: pre-wrap; word-break: break-all; margin: 4px 0; }
.typing { display: flex; gap: 4px; align-items: center; }
.typing .dot { width: 6px; height: 6px; border-radius: 50%; background: #909399; animation: bounce 1s infinite; }
@keyframes bounce { 0%, 60%, 100% { transform: translateY(0); } 30% { transform: translateY(-6px); } }
.quick-questions { display: flex; flex-wrap: wrap; gap: 6px; padding: 8px 12px; border-top: 1px solid #f5f5f5; }
.ai-input-area { display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid #ebeef5; align-items: flex-end; }
.ticket-card {
  flex: 1; border: 1px solid #e6a23c33; background: #fdf6ec; border-radius: 8px;
  padding: 8px 10px; font-size: 12px; line-height: 1.6; margin-bottom: 12px;
}
.tk-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.tk-row { color: #606266; word-break: break-all; }
.tk-row code { background: #fff; padding: 0 3px; border-radius: 3px; }
.tk-row.ok { color: #67c23a; }
.tk-row.bad { color: #f56c6c; }
.tk-actions { display: flex; gap: 6px; margin-top: 6px; }
.empty-tip { color: #909399; font-size: 12px; text-align: center; padding: 24px 0; }
.warn { color: #e6a23c; }
</style>
