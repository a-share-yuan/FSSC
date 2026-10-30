// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import axios from 'axios'
import { reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { friendlyPermError } from './utils/perm403'

const TOKEN_KEY = 'fssc_token'
const USER_KEY = 'fssc_user'

/** 后端地址（与 vite.config.js 的 proxy target 保持一致，仅用于提示文案） */
export const BACKEND_TARGET = '127.0.0.1:8089'

/**
 * D-DEC-463（10-05）：判定"这次失败是前端自己中断的"，与"上传失败"混成一句。
 *
 * 发票上传是「上传＋识别」同步串行的长请求，而后端识别链跑在 `REQUIRES_NEW` 独立事务里 ⇒
 * 前端 abort() **不会**让服务端停手（实测：浏览器 18:33:27 已关，18:33:33 仍落 ocr_record、18:33:37 仍入池）。
 * 所以中断只能报"结果未知＋去票夹核对"，不能报"已取消"，也不能报"上传失败"后让用户重传同一张票。
 * axios 1.x 对 `signal.abort()` 抛的是 CanceledError，`axios.isCancel` 为 true。
 */
export const isRequestCancelled = (e) => !!e && axios.isCancel(e)

/**
 * 后端连通性状态（全局共享）
 * online=false 表示：后端未启动 / 代理转发失败 / 网络不可达。
 * 注意：后端不可达时不清 token（网络抖动不该把用户踢下线），只提示。
 */
export const backend = reactive({ online: true, msg: '' })

const backendListeners = new Set()
/** 订阅后端连通性变化，返回取消订阅函数 */
export function onBackendChange(cb) {
  backendListeners.add(cb)
  return () => backendListeners.delete(cb)
}
function setBackend(online, msg = '') {
  if (backend.online === online && backend.msg === msg) return
  backend.online = online
  backend.msg = msg
  backendListeners.forEach((f) => { try { f(backend) } catch { /* 监听方异常不影响主流程 */ } })
}

export const store = {
  get token() { return localStorage.getItem(TOKEN_KEY) || '' },
  //  store 是**普通对象**（getter/setter 直接读写 localStorage），不是 reactive ⇒
  //    Vue 的 watch(() => store.token) 追踪不到，登录不会触发任何回调。
  //    故在 setter 里额外派发一个事件，供 App.vue 补做「登录成功后预热字典」。
  //    （2026-10-16：修复"字典预热只在刷新页面时才发生"的缺陷）
  set token(v) {
    v ? localStorage.setItem(TOKEN_KEY, v) : localStorage.removeItem(TOKEN_KEY)
    try {
      window.dispatchEvent(new CustomEvent('fssc:auth', { detail: { token: v || '' } }))
    } catch { /* 非浏览器环境（如单测）忽略 */ }
  },
  get user() {
    try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null') } catch { return null }
  },
  set user(v) { v ? localStorage.setItem(USER_KEY, JSON.stringify(v)) : localStorage.removeItem(USER_KEY) },
  logout() {
    this.token = ''
    this.user = null
    clearGlobalOverlays()
  },
}

/**
 * 关闭所有挂在 body 上的全局浮层。
 * 它们不属于 Layout，切换账号时不会随路由卸载 ⇒ 快速切换后新用户会继续看到
 * 上一个会话的提示条／确认框，直到默认时长（3~5s）走完或手动关闭。
 * 登出与登录成功两个边界都调用，保证"谁的会话只显示谁的提醒"。
 */
export function clearGlobalOverlays() {
  try { ElMessage.closeAll() } catch { /* 非浏览器环境忽略 */ }
  try { ElMessageBox.close() } catch { /* 无实例时忽略 */ }
}

// 导出供 request.js 再导出复用，统一全项目 axios 实例（原 request.js 为并行开发复制的第二套同款实例）
export const http = axios.create({ baseURL: '/api', timeout: 20000 })

/**
 * R5-D-200（档案域起流程／长耗时写端点）：全局 20s 一刀切对「起流程」类写端点是假失败高发区——
 * 实测 archive-destroy/create 与 archive/{id}/archive 前端均在 20s 断连（st 0/20006ms），
 * 而服务端其实已建 wf_instance（数据 128），用户看到「没反应」、重试即重复起流程。
 * 处置：起流程类写端点做端点级超时覆盖（全局默认 20s 保持不变，不动普通端点面）；
 * longWrite 标记另供响应拦截器把超时规则从「接口失败」改成「已提交，处理中，请稍后刷新」显式提示。
 */
const LONG_WRITE_TIMEOUT = 60000
const longWrite = { timeout: LONG_WRITE_TIMEOUT, longWrite: true }

// R5-D-294：写端点唯一键 —— 服务端已成功而客户端超时（≥15s 慢尾族）后的用户重试，
// 服务端按 X-Idempotency-Key 去重，避免二次写入数据库；键缺省即时生成，旧调用点行为逐字不变。
const idemKey = () => `idem-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`

// ---------------- : 业务错误全局可见提示 
// 背景：后端业务错误（HTTP 200 + code!=0）只走 reject，页面若 catch 后吞掉/或未 catch，
// 用户看不到任何提示，误以为提交成功（实际只落了草稿）。这里在拦截器统一弹一次错误提示；
// 页面自己的 ElMessage.error(e.message) 与本提示文案相同，靠下方去重避免双弹。
// 调用方可传 { silent: true }（axios config）显式关闭本提示，用于探活/轮询等后台调用。
let _lastErrToast = { msg: '', ts: 0 }
function toastBizError(msg) {
  const now = Date.now()
  if (msg && msg === _lastErrToast.msg && now - _lastErrToast.ts < 1500) return // 去重：1.5s 内同文案只弹一次
  _lastErrToast = { msg: msg || '', ts: now }
  try { ElMessage.error(msg || '请求失败') } catch { /* 提示组件异常不影响主流程 */ }
}

/**
 * 401 文案归一：后端 BizException 的 message 形如「未登录或登录已过期：缺少认证 Token」。
 * 直接展示会把实现细节漏给用户；登录页拿到的凭据错误也会被冠上「登录已过期」的假前提。
 * 凭据类细节（用户名或密码错误）保留，Token 缺失/过期统一说人话。
 */
function friendlyAuthMsg(msg) {
  const raw = String(msg || '')
  if (!raw || raw.includes('缺少认证 Token')) return '登录已过期，请重新登录'
  return raw.replace(/^未登录或登录已过期[：:]\s*/, '') || '登录已过期，请重新登录'
}

// ---------------- R5-08: API 性能监控打点 ----------------
const PERF_THRESHOLD = 800 // 超过 800ms 记录慢请求
const perfLog = reactive({ slowReqs: [], totalReqs: 0, totalErrors: 0, avgMs: 0 })
export function getPerfLog() { return perfLog }

// ---------------- R5-07: 断网自动重试 ----------------
const MAX_RETRY = 3
const RETRY_DELAY = 1000 // 1s

function isRetryableError(err) {
  // F1 重复提交根因：写请求超时（axios timeout 20s）时服务端往往已经处理完，
  // 只前端没等到响应 —— 此时自动重试＝把同一张单再提交一次（实测 57s/40s 两笔各落 2 行）。
  // 因此 POST/PATCH 一律不自动重试，由用户按结果横幅自行判断；GET/PUT/DELETE 仍按原规则重试。
  const method = String(err.config?.method || '').toLowerCase()
  if (method === 'post' || method === 'patch') return false
  // 无响应（网络断开/超时）或 5xx 网关错误才重试；401/4xx 不重试
  const status = err.response?.status
  if (!status) return true // 无网络
  return status === 502 || status === 503 || status === 504
}

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)) }

http.interceptors.request.use((cfg) => {
  // 记录本次请求发出时是否已带身份：登出/登录切换瞬间在途的匿名请求不能在回包时
  // 把"刚登录的新会话"当成过期会话踢掉，也不能把 401 弹成用户可见的报错。
  cfg._hadToken = !!store.token
  if (store.token) cfg.headers.Authorization = 'Bearer ' + store.token
  // R5-08: 记录请求开始时间
  cfg._startTime = Date.now()
  return cfg
})

// 探测后端连通性时抑制"踢到登录页"（未登录探测拿 401 属正常，不应改写当前路由）
let suppressKick = false
function kickToLogin() {
  if (suppressKick) return
  const wasPlatform = store.user?.subjectType === 'PLATFORM'
  store.logout()
  const target = wasPlatform ? '#/platform/login' : '#/login'
  if (location.hash !== target) location.hash = target
}

http.interceptors.response.use(
  async (res) => {
    // R5-08: 记录性能数据
    const duration = res.config?._startTime ? Date.now() - res.config._startTime : 0
    perfLog.totalReqs++
    perfLog.avgMs = Math.round((perfLog.avgMs * (perfLog.totalReqs - 1) + duration) / perfLog.totalReqs)
    if (duration > PERF_THRESHOLD) {
      const entry = { url: res.config?.url, method: res.config?.method, duration, time: new Date().toLocaleTimeString() }
      perfLog.slowReqs.unshift(entry)
      if (perfLog.slowReqs.length > 50) perfLog.slowReqs.pop()
      console.warn(`[Slow API] ${duration}ms ${res.config?.method?.toUpperCase()} ${res.config?.url}`)
    }

    const body = res.data
    setBackend(true)
    if (body && body.code === 0) {
      const d = body.data
      // 分页契约归一化：后端 PageResult 字段为 list/total/pages/pageNum/pageSize。
      // 历史页面存在按 records / rows 读取的写法（如 `res?.records || res || []`），
      // 一旦 records 缺失就会把整个分页对象当数组迭代 → "rows is not iterable"。
      // 统一补别名，页面两种写法都能拿到数组。
      if (d && typeof d === 'object' && !Array.isArray(d) && Array.isArray(d.list)) {
        if (!('records' in d)) d.records = d.list
        if (!('rows' in d)) d.rows = d.list
      }
      return d
    }
    // 业务错误码统一提示（401 跳登录；按退出前的身份回对应登录页）
    if (body && body.code === 401) {
      // 匿名请求的 401（登出瞬间在途的调用、登录页探活）不是"会话过期"：
      // 不踢人也不弹错，否则会把刚登录成功的新会话顶掉，并在新用户页面上留下
      // 「未登录或登录已过期：缺少认证 Token」的假报错。
      if (res.config?._hadToken) {
        kickToLogin()
        if (!res.config?.silent) toastBizError(friendlyAuthMsg(body && body.msg))
      }
      return Promise.reject(new Error(friendlyAuthMsg(body && body.msg)))
    }
    // 业务错误（HTTP 200 + code!=0，如参数校验失败/反欺诈拦截/权限不足）统一弹错，
    // 不再依赖各页面自行 catch（吞错时用户会误以为操作成功）。blob 等非 JSON 响应无 code 字段，不在此弹。
    if (body && typeof body === 'object' && body.code !== undefined && !res.config?.silent) {
      // G12: 4030 权限不足用人话包裹，权限码写入 console 供「查看原因」
      if (body.code === 4030) {
        const { friendly, permCode, rawMsg } = friendlyPermError(body.msg || '', body.code)
        if (permCode) console.info(`[4030] 权限码: ${permCode} | 原始消息: ${rawMsg}`)
        toastBizError(friendly)
      } else {
        toastBizError(body.msg || '请求失败')
      }
    }
    // 移动报销端 P1-3：把业务码一并挂到 Error 上。此前只有 msg 透出，页面无法把
    // 「code:4030 权限不足」与「真的没数据」分开渲染，于是缺权面板被显示成「暂无 X」
    // （AD-13/UX-05；本窗用提报岗 Y000147 实测 /invoice/page、/invoice/enhance/verify/page、
    // /wf/query/history/page 三条均回 code:4030）。纯附加字段，既有调用点行为不变。
    // R5-D-112：4030 的页面级 catch 同样拿人话——此前 bizErr 直出「缺少权限：inv:xxx」原文
    const bizErr = new Error(body && body.code === 4030
      ? friendlyPermError(body.msg || '', body.code).friendly
      : ((body && body.msg) || '请求失败'))
    bizErr.code = body?.code
    return Promise.reject(bizErr)
  },
  async (err) => {
    // R5-08: 记录错误
    perfLog.totalReqs++
    perfLog.totalErrors++

    const status = err.response?.status
    if (status === 401) {
      // 与业务码 401 同一规则：匿名在途请求的回包不踢人、不弹错
      if (err.config?._hadToken) {
        kickToLogin()
        if (!err.config?.silent) toastBizError(friendlyAuthMsg(err.response?.data?.msg))
      }
      return Promise.reject(new Error(friendlyAuthMsg(err.response?.data?.msg)))
    }

    // R5-07: 断网自动重试（仅对无响应/5xx，非探测请求）
    const cfg = err.config
    if (cfg && isRetryableError(err) && !cfg._isRetry && !cfg.url?.includes('/auth/profile')) {
      cfg._retryCount = (cfg._retryCount || 0) + 1
      if (cfg._retryCount <= MAX_RETRY) {
        await sleep(RETRY_DELAY * cfg._retryCount) // 递增延迟
        cfg._isRetry = true
        return http.request(cfg)
      }
    }

    // D-DEC-459：超时 ≠ 掉线。axios 20s 到点时服务端往往已经处理完（实测一笔 44,120ms 的审批
    // 已落 wf_task=DONE、实例 APPROVED），旧规则把 ECONNABORTED 与真·连接拒绝一并渲染成
    // 「后端服务未连接」并挂全局横幅 ⇒ 成功响应丢失、界面反像失败，诱导重复提交（§9.29 T9 重复单的客户端面）。
    // 慢打点旧时只在成功分支（:103），超时请求走 error 分支＝控制台零痕迹，这里一并补上。
    const isTimeout = err.code === 'ECONNABORTED' || err.code === 'ETIMEDOUT'
    const costMs = err.config?._startTime ? Date.now() - err.config._startTime : 0
    if (isTimeout && costMs > PERF_THRESHOLD) {
      perfLog.slowReqs.unshift({ url: err.config?.url, method: err.config?.method, duration: costMs, time: new Date().toLocaleTimeString() })
      if (perfLog.slowReqs.length > 50) perfLog.slowReqs.pop()
      console.warn(`[Slow API] ${costMs}ms ${String(err.config?.method).toUpperCase()} ${err.config?.url}（等待服务响应超时）`)
    }
    if (isTimeout) {
      // R5-D-200：长耗时写端点（longWrite）超时＝服务端多半已受理、只是响应慢——
      // 继续报「失败」会诱导用户重试同一动作（实测 20006ms 断连时 wf_instance 已建）；
      // 一律按「已提交，处理中，请稍后刷新」规则显式提示，通用端点维持原规则。
      const accepted = !!err.config?.longWrite
      if (!err.config?.silent) {
        toastBizError(accepted
          ? '已提交：服务器处理较慢仍在处理中——请勿重复提交，稍后刷新列表确认结果'
          : `系统响应较慢（已等待 ${Math.round(costMs / 1000)} 秒）：本次操作可能已经生效——请勿重复提交，稍后刷新列表确认结果`)
      }
      const timeoutErr = new Error(accepted
        ? '已提交，处理中：请稍后刷新列表确认结果（请勿重复提交）'
        : '请求超时：系统仍在处理，请勿重复提交，稍后刷新查看结果')
      timeoutErr.timeout = true
      timeoutErr.accepted = accepted
      return Promise.reject(timeoutErr)
    }

    // 无响应（连接被拒/超时）或网关类状态码（vite 代理转发失败）→ 判定后端不可达
    if (!status || status === 502 || status === 503 || status === 504) {
      setBackend(false, err.response?.data?.msg || '系统服务暂时无法连接，请稍后重试或联系管理员')
      // 后端不可达已有全局顶部横幅提示，不再重复弹 toast
    } else if (!err.config?.silent) {
      // G12: HTTP 403 权限不足用人话包裹
      if (status === 403) {
        const raw403Msg = err.response?.data?.msg || err.message || ''
        const { friendly, permCode } = friendlyPermError(raw403Msg, undefined, 403)
        if (permCode) console.info(`[403] 权限码: ${permCode} | 原始消息: ${raw403Msg}`)
        toastBizError(friendly)
      } else {
        // 其余 HTTP 错误（400/404/500...）也必须有可见提示
        toastBizError(err.response?.data?.msg || err.message || '网络错误')
      }
    }
    const e = new Error(err.response?.data?.msg || err.message || '网络错误')
    e.status = status // 供调用方按状态码分流（如批量审批接口 404 回退）
    return Promise.reject(e)
  }
)

/**
 * 登录：平台或租户。
 * captchaId / captchaCode（C-3 图形验证码）：平时不传即可——服务端只在「同一身份连续凭据失败
 * 达到 login.captcha-fail-threshold（默认 3）」之后才要求携带（总开关 login.captcha-enabled，默认 false）。
 * 需要时响应错误码为 4011（需携带）/ 4012（验证码错误或已失效），登录页据此显示输入框并自动拉图。
 */
export function login({ username, password, subjectType, tenantCode, rememberMe, captchaId, captchaCode }) {
  // R5-D-256：登录首访含 BCrypt 校验＋（可能）验证码校验，慢环境下 20s 全局超时会误报超时；
  // 登录单独放宽到 30s（不动全局 http 实例，其余接口维持 20s 的快速失败反馈）。
  return http.post('/auth/login', { username, password, subjectType, tenantCode, rememberMe, captchaId, captchaCode }, { timeout: 30000 })
}

/** 需要图形验证码的业务错误码（4011=未携带被要求，4012=携带但不匹配/已失效） */
export const CAPTCHA_ERROR_CODES = [4011, 4012]

/**
 * 取一张一次性图形验证码：返回 { captchaId, image }（image 为 base64 PNG dataURL，直接绑 img src）。
 * 服务端一次一用（校验过或过期即失效），所以每次登录失败后都要重新取图，不能复用上一张的 captchaId。
 * silent：取图失败由页面自己提示，不叠加全局错误弹窗。
 */
export function getCaptcha() {
  return http.get('/auth/captcha', { silent: true })
}

/** 退出登录（后端 Token 黑名单） */
export function logout() {
  return http.post('/auth/logout')
}

/** 企业自助注册（公开，无需登录）：提交后待平台审核 */
export function registerTenant(body) {
  return http.post('/auth/tenant/register', body)
}

// ---------------- 个人中心 ----------------
export const profileApi = {
  /** 获取个人资料（含权限） */
  get: () => http.post('/auth/profile'),
  /** 更新个人资料 */
  update: (body) => http.post('/auth/profile/update', body),
  /** 修改密码 */
  changePassword: (body) => http.post('/auth/profile/changePassword', body),
  /** 更换手机号 */
  changePhone: (body) => http.post('/auth/profile/changePhone', body),
  /** 更换邮箱 */
  changeEmail: (body) => http.post('/auth/profile/changeEmail', body),
}

export function getMenuTree() { return http.get('/tenant/system/menu/tree') }

// ---------------- 报销 ----------------
export const billApi = {
  // R5-D-294：draft 保存为「服务端成功/客户端超时」高发族（P95 21s）——带唯一键去重
  saveDraft: (body, key) => http.post('/tenant/expense/bill/saveDraft', body, { headers: { 'X-Idempotency-Key': key || idemKey() } }),
  submit: (id, approverUserId, firstAssigneeUserId) => http.post(`/tenant/expense/bill/submit/${id}`, null, { params: { approverUserId, firstAssigneeUserId } }),
  withdraw: (id) => http.post(`/tenant/expense/bill/withdraw/${id}`),
  // 反审核（D-V3-135 配套 · 单据反操作链第二环）：APPROVED → DRAFT，前置校验在服务层
  unaudit: (id) => http.post(`/tenant/expense/bill/unaudit/${id}`),
  voidBill: (id) => http.post(`/tenant/expense/bill/void/${id}`),
  editForResubmit: (id) => http.post(`/tenant/expense/bill/edit/${id}`),
  closeApply: (id) => http.post(`/tenant/expense/bill/close/${id}`),
  terminateApply: (id) => http.post(`/tenant/expense/bill/terminate/${id}`),
  detail: (id, cfg) => http.get(`/tenant/expense/bill/detail/${id}`, cfg || {}),
  // P2-1 单据全链路联查：单据→审批→付款→批次→凭证（含分录）→档案→审批轨迹
  // D-TRC-06：bizType 决定后端取哪个域的关联点；不传＝按费用单据主键解释 id（历史规则）。
  traceChain: (id, params, cfg) => http.get(`/tenant/expense/bill-trace-chain/${id}`, { params, ...cfg }),
  myPage: (params) => http.get('/tenant/expense/bill/myPage', { params }),
  // P1-9「我的」页四数（借款余额/笔数、在途、打回）：库里 SUM 聚合，不受分页条数截断。
  // silent=true——本接口在后端未发版时回 404，调用方（H5Workbench#loadMine）有本地分页回落，
  // 不该因为"聚合口还没上线"就弹一次全局错误条。
  mineStat: () => http.get('/tenant/expense/mine/stat', { silent: true }),
  // P2-3 甲案「移动端单据启用清单」（库里表 exp_mobile_template，只读、无入参、全量下发含未启用行）。
  // silent=true——同 mineStat：后端未发版时该路径回 404，调用方（H5Workbench#loadDocTypes）回落
  // utils/h5.js 的 H5_DOC_TYPES 常量默认处理，不该因为"配置接口还没上线"在提报页弹一次全局错误条。
  mobileTemplates: () => http.get('/tenant/expense/mobile/templates', { silent: true }),
  airTicketPage: (params) => http.get('/tenant/expense/bill/air-ticket/page', { params }),
  convertApply: (applyId) => http.post(`/tenant/expense/bill/apply/convert/${applyId}`),
  // 发票池→报销草稿自动填单：body={itemId, title?}，返回 {billId, billNo}（同时自动占用发票）
  fromInvoice: (invoiceId, body) => http.post(`/tenant/expense/bill/from-invoice/${invoiceId}`, body),
  // P2 核定金额三层：审批核减（核定报销/核定付款/核定退款）
  approveDeduct: (id, body) => http.post(`/tenant/expense/bill/approve-deduct/${id}`, body),
  // 员工借款余额台账：按人聚合，overdue=逾期标记；onlyOverdue=true 仅看逾期
  loanLedger: (onlyOverdue) => http.get('/tenant/expense/bill/loan-ledger', { params: { onlyOverdue } }),
  // 预付款单列表（报销冲销预付选择用）：复用现有单据分页接口取已批 PAY 单，前端再按 billSubType=PREPAY 过滤
  prepayPage: (params) => http.get('/tenant/expense/bill/myPage', { params: { pageNum: 1, pageSize: 200, billType: 'PAY', status: 'APPROVED', ...params } }),
  // 冷热分离：历史（冷）单据查询 + 归档预演。冷表在归档日任务里从热表整体搬来，查询走 JdbcTemplate 手工带 tenant_id。
  coldPage: (params) => http.get('/tenant/expense/bill/cold/page', { params }),
  coldDetail: (id) => http.get(`/tenant/expense/bill/cold/${id}`),
  coldDryRun: () => http.post('/tenant/expense/bill/cold/dry-run'),
}

// ---------------- 审批 ----------------
export const wfApi = {
  // 兼容版：返回数组（工作台徽标/AI助手等取前 N 条或仅计数）。cfg.silent 供轮询调用关闭全局错误弹窗
  todo: (params, cfg) => http.get('/tenant/wf/todo', { params, ...cfg }),
  // 服务端分页：返回 { list, total, pages, pageNum, pageSize }，避免数据量增长后全量拉取卡死
  todoPage: (params) => http.get('/tenant/wf/todo/page', { params }),
  // cfg 可选（D-DEC-198 增量）：批量审批改走逐单通道时，逐条失败由调用方聚合成明细回显，
  // 不能再让全局拦截器每条弹一次 toast；不传 cfg 的 30+ 既有调用点行为逐字不变。
  // R5-D-294：approve 为慢尾族（≥15s 6 条）——带唯一键去重；cfg 语义逐字不变（silent 等原样透传）
  approve: (body, cfg, key) => http.post('/tenant/wf/approve', body, { ...cfg, headers: { ...((cfg || {}).headers || {}), 'X-Idempotency-Key': key || idemKey() } }),
  /**
   * 下一环节候选办理人（审批弹窗「下一步处理人」区块）。
   * silent：老版本后端无此端点（404/403），弹窗要静默降级为"不显示该区块"，不能弹全局错误。
   * cfg 传 { silent: true } 使用。
   */
  nextCandidates: (taskId, cfg) => http.get('/tenant/wf/next-candidates', { params: { taskId }, ...cfg }),
  /**
   * 会签/票签节点进度（审批弹窗「会签·票签进度」区块）。
   * 返回 { multi, multiType, total, approved, rejected, pending, voteRatio, passThreshold, votes[] }；
   * multi=false 表示普通单办理人节点，弹窗不渲染该区块。
   * silent：老后端无此端点（404/403）时静默降级为"不显示进度"，不能因此挡住审批提交。
   */
  miProgress: (taskId, cfg) => http.get('/tenant/wf/mi-progress', { params: { taskId }, ...cfg }),
  /**
   * 提单态首审环节候选人（G2-3 提单选人）。单据必须已保存草稿（bizId 必填，候选人按单据公司过滤）。
   * bizType 用后端提交时的同一规则（EXP_CLAIM/EXP_APPLY/EXP_LOAN/EXP_REPAY/EXP_PAY），
   * 否则流程 key 解析会与真实提交不一致，预览的落点就不是实际落点。
   * silent：老后端无此端点时静默降级为"不显示选人区"。
   */
  startCandidates: (params, cfg) => http.get('/tenant/wf/start-candidates', { params, ...cfg }),
  /**
   * 批量审批：统一通过一批待办任务。
   * 主路径：POST /api/tenant/workflow/task/batch-approve（WfTaskBatchController 实际部署路径，已探活 200）
   * 请求体 { taskIds: [...], comment: '...' }
   * 响应 data 宽容解析：{ successCount?, failCount?, total?, fail?/failed?: [{ taskId, msg, billNo? }] }
   * 兼容回退：若后端按工单原契约部署在 /api/tenant/wf/batch-approve，404 时自动改走该路径。
   * 【2026-09-23 复核】当前后端 WfTaskController（前缀 /api/tenant/wf）下并不存在该路径，
   * 回退分支属纯防御、实际不会命中；唯一实现是 WfTaskBatchController 的 workflow/task 前缀。
   * 两次尝试均 silent（404 属部署形态问题，由调用方按解析结果统一提示）。
   */
  batchApprove: async (body) => {
    try {
      return await http.post('/tenant/workflow/task/batch-approve', body, { silent: true })
    } catch (e) {
      if (e?.status === 404 || /404/.test(String(e?.message))) {
        return await http.post('/tenant/wf/batch-approve', body, { silent: true })
      }
      throw e
    }
  },
  /** 按业务单据审批（列表页通用入口，无需 taskId） */
  approveByBill: (bizType, bizId, approved, comment) =>
    http.post('/tenant/wf/approve-bill', null, { params: { bizType, bizId, approved, comment } }),
  /** 转办：把待办任务改派给其他用户 */
  transfer: (body) => http.post('/tenant/wf/transfer', body),
  // 本节点可转办名单（与 /transfer 目标校验同一规则）；名单空＝找流程管理员改派
  taskCandidates: (params) => http.get('/tenant/wf/task-candidates', { params }),
  /**
   * 加签（G1）：position=BEFORE 前加签 / AFTER 后加签；signUsers 为逗号分隔用户ID。
   * 与转办的区别＝临时插一道，加签人审完自动回跳，流程走向不变（同一任务同时只允许一组加签）。
   */
  addSign: (body) => http.post('/tenant/wf/add-sign', body),
  // 取消加签（加签发起人或当期加签人本人）；进行中的加签取消时任务先交还回跳目标
  cancelAddSign: (id) => http.post(`/tenant/wf/add-sign/${id}/cancel`),
  // 该实例仍在进行/待生效的加签（只读）：前端显示"当前在加签：X"并给取消入口
  openAddSign: (params) => http.get('/tenant/wf/add-sign/open', { params }),
  /**
   * 「已办撤销回原节点」只读预览（P3 G3）：列出可回退的已办节点与各自影响面，
   * **不修改任何流程数据**。执行面未开放（Flowable 8 无 ProcessInstanceModification）。
   * 入参传 { procInstId } 或 { taskId } 之一即可。
   */
  withdrawPreview: (params, cfg) => http.get('/tenant/wf/withdraw-preview', { params, ...cfg }),
  /**
   * 「自由驳回」只读预览（P4 G4）：给定当前待办任务，列出可退回的上游节点与各自影响面，
   * **不修改任何流程数据**。silent：开关未开/老版本后端无此端点时静默降级为不显示级次选择器。
   */
  rejectTargets: (params, cfg) => http.get('/tenant/wf/reject-targets', { params, ...cfg }),
  /**
   * ①层「审批项」只读候选（2026-09-24）：给定当前待办任务，
   * 返回该节点上启用中的自定义审批结果 `[{ itemCode, itemName, category, linkedOper, targetNode }]`。
   * category：POSITIVE 正向 / REJECT 驳回 / TERMINATE 终止（服务端按类别覆盖 approved）。
   * 返回空数组 ⇒ 不渲染审批项区（与改造前完全一致）。
   * silent：老版本后端无此端点（404/403）时静默降级，绝不阻断审批提交。
   */
  approvalItems: (taskId, cfg) => http.get('/tenant/wf/approval-items', { params: { taskId }, ...cfg }),
  // 改派（L2，需 wf:task:reassign 权限点）：流程管理员把在办任务派给任意真人，原因必填
  reassign: (body) => http.post('/tenant/wf/reassign', body),
  // 领取/释放（POOL 任务池）：候选人把仅候选组态的任务锁定到自己名下，或退回任务池
  claim: (taskId) => http.post('/tenant/wf/claim', null, { params: { taskId } }),
  unclaim: (taskId) => http.post('/tenant/wf/unclaim', null, { params: { taskId } }),
  /**
   * 发起人催办（体检 E-10 / F1-5）：提单人对自己的在办单据催一次。
   * 服务端硬校验 wf_instance.start_user_id == 当前用户，同一环节 24 小时内只发一次；
   * 命中限频以业务异常返回（调用方必须把原因显示出来，不能报"催办成功"）。
   */
  urgeInitiator: (procInstId) => http.post('/tenant/wf/instance/urge-initiator', null, { params: { procInstId } }),
}

// ---------------- 文件存储 ----------------
export const fileApi = {
  // 上传返回原始响应（含文件记录），需手动取 data.id；zone=TEMP/BUSINESS/ARCHIVE 存储区域
  upload: (file, bizType, bizId, zone) => {
    const fd = new FormData()
    fd.append('file', file)
    if (bizType) fd.append('bizType', bizType)
    if (bizId != null) fd.append('bizId', bizId)
    if (zone) fd.append('zone', zone)
    return http.post('/tenant/system/file/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  detail: (id) => http.get(`/tenant/system/file/${id}`),
  page: (params) => http.get('/tenant/system/file/page', { params }),
  delete: (id) => http.post(`/tenant/system/file/${id}/delete`),
  // 上传并解析报销发票/附件：返回 {id,fileName,attachType,invoice}
  // D-DEC-463：第三个参数收 AbortController.signal，让"用户中断等待"成为受支持的出口；
  // 中断不等于服务端停手（识别链是 REQUIRES_NEW），调用方必须按"结果未知"处置。
  attachUpload: (file, bizId, signal, key) => {
    const fd = new FormData()
    fd.append('file', file)
    if (bizId != null) fd.append('billId', bizId)
    // 图片发票走图像 OCR 通道（后端允许 120s），全局 20s 超时会误杀合法慢识别
    // R5-D-294：attach/upload 为「服务端成功/客户端超时」最大族（≥15s 9 条）——带唯一键去重
    return http.post('/tenant/expense/attach/upload', fd,
      { headers: { 'Content-Type': 'multipart/form-data', 'X-Idempotency-Key': key || idemKey() }, timeout: 120000, signal })
  },
  // 申领一次性下载令牌
  createToken: (id) => http.post(`/tenant/system/file/${id}/token`),
  // 存储占用统计（MB）
  storageStats: () => http.get('/tenant/system/file/storageStats'),
}

// ---------------- 编号规则 ----------------
export const numberApi = {
  rulePage: (params) => http.get('/tenant/system/number/rule/page', { params }),
  ruleCreate: (body) => http.post('/tenant/system/number/rule/create', body),
  ruleUpdate: (body) => http.post('/tenant/system/number/rule/update', body),
  ruleDelete: (id) => http.post(`/tenant/system/number/rule/${id}/delete`),
  ruleEnable: (id, enable) => http.post(`/tenant/system/number/rule/${id}/enable`, null, { params: { enable } }),
  next: (ruleCode) => http.post('/tenant/system/number/next', { ruleCode }),
}

// ---------------- 公告 / 消息 ----------------
export const noticeApi = {
  page: (params) => http.get('/tenant/system/notice/page', { params }),
  create: (body) => http.post('/tenant/system/notice/create', body),
  update: (body) => http.post('/tenant/system/notice/update', body),
  publish: (id) => http.post(`/tenant/system/notice/${id}/publish`),
  close: (id) => http.post(`/tenant/system/notice/${id}/close`),
  delete: (id) => http.post(`/tenant/system/notice/${id}/delete`),
  active: () => http.get('/tenant/system/notice/active'),
}
export const messageApi = {
  // cfg.silent：NotificationCenter 轮询/开关弹层时关闭全局错误弹窗（失败静默重试即可）
  page: (params, cfg) => http.get('/tenant/system/message/page', { params, ...cfg }),
  send: (body) => http.post('/tenant/system/message/send', body),
  unreadCount: (receiverId, cfg) => http.get('/tenant/system/message/unreadCount', { params: { receiverId }, ...cfg }),
  read: (id) => http.post(`/tenant/system/message/${id}/read`),
  readAll: (receiverId) => http.post('/tenant/system/message/readAll', null, { params: { receiverId } }),
  delete: (id) => http.post(`/tenant/system/message/${id}/delete`),
}

// ---------------- 流程配置 ----------------
export const bizFlowApi = {
  page: (p) => http.get('/tenant/wf/biz-flow/page', { params: p }),
  create: (b) => http.post('/tenant/wf/biz-flow/create', b),
  update: (b) => http.post('/tenant/wf/biz-flow/update', b),
  delete: (id) => http.post(`/tenant/wf/biz-flow/${id}/delete`),
  byType: (bizType) => http.get('/tenant/wf/biz-flow/by-type', { params: { bizType } }),
  types: () => http.get('/tenant/wf/biz-flow/types'),
  template: (bizType) => http.get('/tenant/wf/biz-flow/template', { params: { bizType } }),
}

export const flowConfigApi = {
  page: (p) => http.get('/tenant/wf/flow-config/page', { params: p }),
  byType: (cfgType) => http.get('/tenant/wf/flow-config/by-type', { params: { cfgType } }),
  create: (b) => http.post('/tenant/wf/flow-config/create', b),
  update: (b) => http.post('/tenant/wf/flow-config/update', b),
  delete: (id) => http.post(`/tenant/wf/flow-config/${id}/delete`),
}

export const designerApi = {
  validate: (bpmnXml) => http.post('/tenant/wf/designer/validate', { bpmnXml }),
  simulate: (bpmnXml, amount, itemName) => http.post('/tenant/wf/designer/simulate', { bpmnXml, amount, itemName }),
  publish: (model) => http.post('/tenant/wf/designer/publish', model),
  versions: (modelKey) => http.get('/tenant/wf/designer/versions', { params: { modelKey } }),
  exportModel: (id) => http.get('/tenant/wf/designer/export', { params: { id } }),
  importModel: (body) => http.post('/tenant/wf/designer/import', body),
  // G3-3 在途影响面 / G3-4 BPMN 仓库化
  inFlight: (modelKey) => http.get('/tenant/wf/designer/in-flight', { params: { modelKey } }),
  exportRepo: () => http.post('/tenant/wf/designer/export-repo'),
  publishHistory: (modelKey) => http.get('/tenant/wf/designer/publish-history', { params: { modelKey } }),
  publishDiff: (modelKey, name) => http.get('/tenant/wf/designer/publish-diff', { params: { modelKey, name } }),
}

/** P0 系统级可靠性：一致性对账 + 审计记录 */
export const auditApi = {
  reconcilePage: (params) => http.get('/tenant/audit/reconcile/page', { params }),
  // 全量逐单对账为同步长任务（1,587 单实测 50.8s），全局 20s 超时会假报失败
  reconcileRun: () => http.post('/tenant/audit/reconcile/run', null, { timeout: 180000, longWrite: true }),
  reconcileResolve: (id, body) => http.post(`/tenant/audit/reconcile/${id}/resolve`, body),
  trailPage: (params) => http.get('/tenant/audit/trail/page', { params }),
}

/** P0 内控：定时任务心跳（只读巡检）；SoD 检查页见 views/system/SoDCheck.vue（直接走 /tenant/compliance/sod/*） */
export const jobApi = {
  heartbeatPage: (params) => http.get('/tenant/system/job-heartbeat/page', { params }),
}

/** P2 银企对账 + 报表预聚合 */
export const p2Api = {
  bankReconcilePage: (params) => http.get('/tenant/bank/reconcile/page', { params }),
  bankReconcileRun: () => http.post('/tenant/bank/reconcile/run'),
  bankManualMatch: (data) => http.post('/tenant/bank/reconcile/manual-match', data),
  bankReconcileSheet: (params) => http.get('/tenant/bank/reconcile/sheet', { params }),
  bankReconItems: (params) => http.get('/tenant/bank/reconcile/items', { params }),
  summaryQuery: (params) => http.get('/tenant/report/summary/query', { params }),
  summaryPeriods: () => http.get('/tenant/report/summary/periods'),
  summaryRefresh: (year, month) => http.post(`/tenant/report/summary/refresh?year=${year}&month=${month}`),
  // P1-7 银企直连闭环：流水分页 / 收款认领核销 / 回单归档
  bankStatementPage: (params) => http.get('/tenant/bank/reconcile/statement/page', { params }),
  bankClaim: (statementId, receivableId) => http.post('/tenant/bank/reconcile/claim', { statementId, receivableId }),
  // D-G5-02：可认领应收单清单（银行域专用端点）。
  // 原先借应收域的 receivableApi.page 取清单，而该端点要求 receivable:list（应收域）
  // ⇒ 纯银企对账岗（只有 bank:reconcile:list）打开认领弹层时清单恒空、动作做不了。
  // 查询权限随域走：改调本域端点。
  bankClaimableReceivables: (params) => http.get('/tenant/bank/reconcile/claimable-receivables', { params }),
  bankSlipArchive: (statementId, fileId) => http.post('/tenant/bank/reconcile/slip/archive', { statementId, fileId }),
  // R5-D-185：资金头寸页「登记余额」——余额快照保存（同账户同日期去重覆盖，服务端 BankReconcileController#saveBalance）
  bankBalanceSave: (b) => http.post('/tenant/bank/reconcile/balance/save', b),
}

/** P1 优化相关配置（三单匹配参数化容差） */
export const p1Api = {
  // 三单匹配容差配置（键值型）
  toleranceList: () => http.get('/tenant/pay/match/tolerance/list'),
  toleranceUpsert: (body) => http.post('/tenant/pay/match/tolerance/upsert', body),
  // 41号审查 P2-13:删除容差配置(含差异化行)
  toleranceDelete: (id) => http.post(`/tenant/pay/match/tolerance/${id}/delete`),
}

export const flowApi = {
  modelPage: (params) => http.get('/tenant/wf/config/model/page', { params }),
  modelCreate: (body) => http.post('/tenant/wf/config/model/create', body),
  modelUpdate: (body) => http.post('/tenant/wf/config/model/update', body),
  modelDelete: (id) => http.post(`/tenant/wf/config/model/${id}/delete`),
  processPage: (params) => http.get('/tenant/wf/config/process/page', { params }),
  processCreate: (body) => http.post('/tenant/wf/config/process/create', body),
  processUpdate: (body) => http.post('/tenant/wf/config/process/update', body),
  processDelete: (id) => http.post(`/tenant/wf/config/process/${id}/delete`),
  nodeList: (modelKey) => http.get('/tenant/wf/config/node/list', { params: { modelKey } }),
  nodeCreate: (body) => http.post('/tenant/wf/config/node/create', body),
  nodeUpdate: (body) => http.post('/tenant/wf/config/node/update', body),
  nodeDelete: (id) => http.post(`/tenant/wf/config/node/${id}/delete`),
  // 节点审批角色持有人体检（只读）：配置时即暴露"某公司 0 人 / 多公司 ≥2 人"，不留到运行期
  roleHolders: (roleCode) => http.get('/tenant/wf/config/node/role-holders', { params: { roleCode } }),
  delegatePage: (params) => http.get('/tenant/wf/config/delegate/page', { params }),
  delegateCreate: (body) => http.post('/tenant/wf/config/delegate/create', body),
  delegateCancel: (id) => http.post(`/tenant/wf/config/delegate/${id}/cancel`),
  // 查询视图
  instancePage: (params, cfg) => http.get('/tenant/wf/query/instance/page', { params, ...cfg }),
  historyPage: (params, cfg) => http.get('/tenant/wf/query/history/page', { params, ...cfg }),
  // 审批链全景（全节点 + 已过/当前/待到达 + 办理人意见）：业务角色无 wf:node:list 也能看进度
  chain: (procInstId, cfg) => http.get('/tenant/wf/query/chain', { params: { procInstId }, ...cfg }),
  myTodo: (params) => http.get('/tenant/wf/query/todo', { params }),
  myCc: (params) => http.get('/tenant/wf/query/cc/mine', { params }),
  /**
   * 抄送候选人（只读，G2 传阅）：审批弹窗「抄送给」多选的数据源。
   * 返回 [{ userId, name, userName }]，限本租户在职用户并排除管理员，上限 500 条。
   */
  ccCandidates: (params, cfg) => http.get('/tenant/wf/query/cc/candidates', { params, ...cfg }),
  // 流程绩效与积压（G3-2 真规则）：桑基走实例真实路径、热力走 task_history 节点命中，
  // 另含 SLA 达成率、积压账龄、按人时效与超时未办榜。cfg 传 { silent: true } 供轮询页使用。
  perfOverview: (days = 30, cfg) => http.get('/tenant/wf/perf/overview', { params: { days }, ...cfg }),
  // R5-D-036：实例状态全量计数（/wf/board 顶部四卡规则源，前端从当前页切片现算）
  perfInstanceStatus: (cfg) => http.get('/tenant/wf/perf/instance-status', { ...(cfg || {}) }),
  perfPaths: (days = 30, cfg) => http.get('/tenant/wf/perf/paths', { params: { days }, ...cfg }),
  perfNodeDwell: (days = 30, cfg) => http.get('/tenant/wf/perf/node-dwell', { params: { days }, ...cfg }),
  perfBacklog: (cfg) => http.get('/tenant/wf/perf/backlog', { ...(cfg || {}) }),
  perfHandlerTop: (days = 30, cfg) => http.get('/tenant/wf/perf/handler-top', { params: { days }, ...cfg }),
  // G13 字段级表单权限：按待办任务解析当前节点 form_perm（{field: 'H'|'R'|'W'}）；fail-open 空 Map
  taskFormPerm: (taskId, cfg) => http.get('/tenant/wf/task-form-perm', { params: { taskId }, ...cfg }),
}

/**
 * G3 共享作业层（2026-09-24 新接口集）——任务池 / 分配策略 / 自动分配日志 / 质量报表。
 *
 * URL / 方法与权限码均以后端控制器源码为准：
 *  - 任务池五视图        WfTaskPoolController      /api/tenant/wf/pool/**           wf:task:list（POOL_ALL 为 wf:instance:list）
 *  - 分配策略/规则/日志/时限/质量  WfSharedOpsController    /api/tenant/wf/ops/**     读 wf:config:list|wf:instance:list，写 wf:config:update|wf:task:reassign
 *  - 流程处理详细日志    WfProcessLogController    /api/tenant/wf/process-log/**    读 wf:instance:list，清理 wf:config:update
 *  - 流程批量移交        WfHandoverController      /api/tenant/wf/handover/**       wf:manage:intervene|wf:task:reassign，日志 wf:instance:list
 *  - 归档预览            WfPerfController          /api/tenant/wf/perf/archive-preview  wf:instance:list
 * 分页统一走 PageQuery（pageNum/pageSize），返回值由拦截器解出 Result.data（PageResult 含 list/total）。
 */
export const wfOpsApi = {
  // ---- 1. 任务池五视图（/tenant/wf/pool） ----
  /** 视图清单（前端下拉数据源，避免硬编码视图名） */
  poolViews: (cfg) => http.get('/tenant/wf/pool/views', { ...(cfg || {}) }),
  /** 我的待办 / 可认领 / 被他人锁定 / 我已认领（params: { view, keyword }, PageQuery） */
  poolMine: (params, cfg) => http.get('/tenant/wf/pool/mine', { params, ...cfg }),
  /** 池内全部（共享中心主管总览，不限候选人） */
  poolAll: (params, cfg) => http.get('/tenant/wf/pool/all', { params, ...cfg }),

  // ---- 2. 分配策略四模式（PICK / BALANCE_TIME / BALANCE_VOLUME / SMART） ----
  policyPage: (params) => http.get('/tenant/wf/ops/policy/page', { params }),          // params: { keyword, strategy }, PageQuery
  policyGet: (id) => http.get(`/tenant/wf/ops/policy/${id}`),
  /** 某节点当前生效策略（只读试算：节点级 → 全局默认 → 内置默认处理） */
  policyEffective: (params, cfg) => http.get('/tenant/wf/ops/policy/effective', { params, ...cfg }), // params: { modelKey, nodeKey }
  policyCreate: (body) => http.post('/tenant/wf/ops/policy/create', body),
  policyUpdate: (body) => http.post('/tenant/wf/ops/policy/update', body),
  policyDelete: (id) => http.post(`/tenant/wf/ops/policy/${id}/delete`),

  // ---- 3. 分配规则顺序解析试算（只读） ----
  /** params: { taskType, bizType, amount, companyId, expenseItemId, currencyCode, legalEntityId } */
  ruleResolve: (params, cfg) => http.get('/tenant/wf/ops/rule/resolve', { params, ...cfg }),

  // ---- 4. 自动分配（试算/执行）＋ 调度日志 ----
  /** body: { taskRowId, taskType, amount, companyId, expenseItemId, currencyCode, legalEntityId, priority, dryRun } */
  assign: (body) => http.post('/tenant/wf/ops/assign', body),
  /** params: { taskId, bizNo, strategy }, PageQuery */
  dispatchLogPage: (params) => http.get('/tenant/wf/ops/dispatch-log/page', { params }),
  /** 某一任务的全部分配决策链（时间正序） */
  dispatchLogChain: (taskId, cfg) => http.get('/tenant/wf/ops/dispatch-log/chain', { params: { taskId }, ...cfg }),

  // ---- 5. 双时限（普通 24h / 加急 4h） ----
  timeLimitMark: (taskRowId, priority = 'NORMAL') => http.post('/tenant/wf/ops/time-limit/mark', null, { params: { taskRowId, priority } }),
  timeLimitBackfill: (cap = 200) => http.post('/tenant/wf/ops/time-limit/backfill', null, { params: { cap } }),
  timeLimitDueScan: (limit = 200, cfg) => http.get('/tenant/wf/ops/time-limit/due-scan', { params: { limit }, ...cfg }),
  timeLimitScanJobReport: (cfg) => http.get('/tenant/wf/ops/time-limit/scan-job-report', { ...(cfg || {}) }),

  // ---- 6. 任务质量报表（批退率 / 一次通过率 / 节点停留 / 批退原因） ----
  qualitySummary: (days = 30, cfg) => http.get('/tenant/wf/ops/quality/summary', { params: { days }, ...cfg }),
  qualityByNode: (days = 30, limit = 30, cfg) => http.get('/tenant/wf/ops/quality/nodes', { params: { days, limit }, ...cfg }),
  qualityByHandler: (days = 30, limit = 30, cfg) => http.get('/tenant/wf/ops/quality/handlers', { params: { days, limit }, ...cfg }),
  qualityRejectReasons: (params, cfg) => http.get('/tenant/wf/ops/quality/reject-reasons', { params, ...cfg }), // params: { days, nodeKey, limit }
  qualityRejectPage: (params) => http.get('/tenant/wf/ops/quality/reject/page', { params }), // params: { days, nodeKey, keyword }, PageQuery

  // ---- 7. 流程处理详细日志（/tenant/wf/process-log） ----
  /** params: { procInstId, bizNo, bizType, logType, from, to }, PageQuery（from/to 为 yyyy-MM-dd） */
  processLogPage: (params) => http.get('/tenant/wf/process-log/page', { params }),
  /** 某实例的完整日志时间线（升序） */
  processLogByInstance: (procInstId, cfg) => http.get('/tenant/wf/process-log/by-instance', { params: { procInstId }, ...cfg }),
  /** 读取详细日志开关（返回 { cfgKey, enabled, hint }） */
  processLogSwitch: (cfg) => http.get('/tenant/wf/process-log/switch', { ...(cfg || {}) }),
  /** 按保留天数物理清理历史日志；返回清理条数 */
  processLogClean: (keepDays = 30) => http.post('/tenant/wf/process-log/clean', null, { params: { keepDays } }),

  // ---- 8. 流程批量移交（/tenant/wf/handover） ----
  /** body: { fromUserId, toUserId, taskIds, reason, remark }（taskIds 与 fromUserId 二选一） */
  handoverExecute: (body) => http.post('/tenant/wf/handover/execute', body),
  /** params: { fromUserId, toUserId }, PageQuery */
  handoverLogPage: (params) => http.get('/tenant/wf/handover/log/page', { params }),

  // ---- 9. 流程实例归档只读预览（/tenant/wf/perf/archive-preview，纯 COUNT 不搬移） ----
  archivePreview: (cfg) => http.get('/tenant/wf/perf/archive-preview', { ...(cfg || {}) }),
}

/** 超时升级三端点（WfTimeoutController）：行字段与分页字段以后端源码为准，见下方注释 */
export const wfTimeoutApi = {
  // stats → { totalPending, overdueCount, withinSlaCount, avgProcessingHours }；silent 供页面静默重试
  stats: (cfg) => http.get('/tenant/wf/timeout/stats', { ...(cfg || {}) }),
  // params: { pageNum, pageSize }（PageQuery 规则，后端返回全部 PENDING 待办，靠 overdueHours 区分是否超时）
  overdueList: (params) => http.get('/tenant/wf/timeout/overdue-list', { params }),
  // config/{modelKey} → [{ nodeKey, nodeName, timeoutHours, timeoutAction }]
  config: (modelKey, cfg) => http.get(`/tenant/wf/timeout/config/${encodeURIComponent(modelKey)}`, { ...(cfg || {}) }),
}

// ---------------- 运营增强 ----------------
export const opApi = {
  qualityPage: (params) => http.get('/tenant/sharing/enhance/quality/page', { params }),
  qualityCreate: (body) => http.post('/tenant/sharing/enhance/quality/create', body),
  qualityUpdate: (body) => http.post('/tenant/sharing/enhance/quality/update', body),
  qualityDelete: (id) => http.post(`/tenant/sharing/enhance/quality/${id}/delete`),
  // 对公付款三单匹配（PO ↔ 收货单 ↔ 发票 ↔ 付款单）
  matchPage: (params) => http.get('/tenant/pay/match/page', { params }),
  matchCheck: (billId) => http.get(`/tenant/pay/match/check?billId=${billId}`),
  matchLink: (billId, body) => http.post(`/tenant/pay/match/${billId}/link`, body),
  matchInvoices: (billId) => http.get(`/tenant/pay/match/${billId}/invoices`),
  matchRoute: (billId) => http.post(`/tenant/pay/match/${billId}/route`),
  matchReview: (billId) => http.post(`/tenant/pay/match/${billId}/review`),
  matchManualReview: (billId, reason) => http.post(`/tenant/pay/match/${billId}/manual-review`, null, { params: { reason } }),
  // 供应商对账（预留第三方对账平台接口）
  reconPage: (params) => http.get('/tenant/pay/recon/page', { params }),
  reconGenerate: (body) => http.post('/tenant/pay/recon/generate', body),
  reconSummary: (id) => http.get(`/tenant/pay/recon/${id}/summary`),
  reconDetails: (id) => http.get(`/tenant/pay/recon/${id}/details`),
  reconAutoMatch: (id) => http.post(`/tenant/pay/recon/${id}/auto-match`),
  reconManualMatch: (id, invoiceDetailId, paymentDetailId) => http.post(`/tenant/pay/recon/${id}/manual-match`, { invoiceDetailId, paymentDetailId }),
  reconUnmatch: (detailId) => http.post(`/tenant/pay/recon/detail/${detailId}/unmatch`),
  reconSendLetter: (id) => http.post(`/tenant/pay/recon/${id}/send-letter`),
  reconSupplierConfirm: (id, confirmStatus, remark) => http.post(`/tenant/pay/recon/${id}/supplier-confirm`, { confirmStatus, remark }),
  reconDiffs: (id) => http.get(`/tenant/pay/recon/${id}/diffs`),
  reconAddDiff: (id, body) => http.post(`/tenant/pay/recon/${id}/diffs`, body),
  reconResolveDiff: (diffId, body) => http.post(`/tenant/pay/recon/diffs/${diffId}/resolve`, body),
  reconLinkUStatus: (id) => http.get(`/tenant/pay/recon/${id}/linku-status`),
  archivePage: (params) => http.get('/tenant/sharing/enhance/archive/page', { params }),
  archivePending: (params) => http.get('/tenant/sharing/enhance/archive/pending', { params }),
  archiveDo: (billIds) => http.post('/tenant/sharing/enhance/archive/do-archive', billIds),
  archiveCreate: (body) => http.post('/tenant/sharing/enhance/archive/create', body),
  archiveUpdate: (body) => http.post('/tenant/sharing/enhance/archive/update', body),
  archiveDestroy: (id) => http.post(`/tenant/sharing/enhance/archive/${id}/destroy`),
  statementPage: (params) => http.get('/tenant/sharing/enhance/statement/page', { params }),
  statementImport: (body) => http.post('/tenant/sharing/enhance/statement/import', body),
  statementRecon: (id, reconBillId) => http.post(`/tenant/sharing/enhance/statement/${id}/recon`, null, { params: { reconBillId } }),
}

// ---------------- 采购付款计划 PAY_PLAN_BASE/PAY_PLAN_PLATE ----------------
export const payPlanApi = {
  page: (params) => http.get('/tenant/pay-plan/page', { params }),
  create: (body) => http.post('/tenant/pay-plan/create', body),
  // 批次1：驳回计划重报（REJECTED→重新审批），替代"驳回只能新建绕过"
  resubmit: (id) => http.post(`/tenant/pay-plan/${id}/resubmit`),
  // D-DEC-454：提单人作废在途计划（APPROVING→VOID，服务端同步终止审批链）
  voidPlan: (id, reason) => http.post(`/tenant/pay-plan/${id}/void`, { reason }),
}

// ---------------- 07 档案管理 FM：单据流转(FM_DOC_FLOW) / 凭证归档(FM_ARCHIVE) ----------------
export const fmApi = {
  // 单据流转 FM_DOC_FLOW：派单→审核组→完成（出单即发起单据流转审批）
  dispatchRun: (body) => http.post('/tenant/compliance/dispatch/run', body),
  dispatchList: (status) => http.get('/tenant/compliance/dispatch/by-status', { params: { status } }),
  dispatchListPage: (params) => http.get('/tenant/compliance/dispatch/by-status/page', { params }),
  dispatchById: (id) => http.get(`/tenant/compliance/dispatch/${id}`),
  dispatchMyTasks: (reviewerId, status) => http.get('/tenant/compliance/dispatch/my-tasks', { params: { reviewerId, status } }),
  dispatchAssign: (id, reviewerId) => http.post(`/tenant/compliance/dispatch/${id}/assign`, null, { params: { reviewerId } }),
  dispatchComplete: (id) => http.post(`/tenant/compliance/dispatch/${id}/complete`),
  dispatchEscalate: (id, reason) => http.post(`/tenant/compliance/dispatch/${id}/escalate`, { reason }),
  dispatchAutoCheck: () => http.post('/tenant/compliance/dispatch/auto-check'),
  // 凭证归档 FM_ARCHIVE：档案目录 → 执行归档即发起归档审批
  archivePage: (params) => http.get('/tenant/compliance/archive/page', { params }),
  archiveCreate: (body) => http.post('/tenant/compliance/archive/create', body),
  // R5-D-200：执行归档＝起流程类长耗时写端点（实测 20s 断连而服务端已建 wf_instance），端点级放宽超时
  archiveDo: (id) => http.post(`/tenant/compliance/archive/${id}/archive`, null, { ...longWrite }),
  archiveAuto: (body) => http.post('/tenant/compliance/archive/auto-archive', body, { ...longWrite }),
  archiveDestroy: (id) => http.post(`/tenant/compliance/archive/${id}/destroy`),
  archiveVerify: (id) => http.post(`/tenant/compliance/archive/${id}/verify`),
  archiveList: (params) => http.get('/tenant/compliance/archive/list', { params }),
  archiveStats: (year) => http.get('/tenant/compliance/archive/stats', { params: { year } }),
}

// ---------------- 付款 ----------------
export const payApi = {
  createPayment: (billId, body) => http.post(`/tenant/pay/payment/create/${billId}`, body),
  createBatch: (body) => http.post('/tenant/pay/batch/create', body),
  autoGroup: () => http.post('/tenant/pay/batch/auto-group'),
  /** D-DEC-277(C1)：待付记录清单（WAITING 且未入批），供勾选成批 */
  waitingPayments: (params) => http.get('/tenant/pay/payment/waiting-page', { params }),
  /** D-DEC-277(C1)：勾选待付记录一步成批（后端校验同公司/同币别/同出账账户） */
  createBatchFromPayments: (body) => http.post('/tenant/pay/batch/create-from-payments', body),
  addDetail: (batchId, billPaymentId) => http.post(`/tenant/pay/batch/${batchId}/detail`, { billPaymentId }),
  confirm: (id) => http.post(`/tenant/pay/batch/${id}/confirm`),
  voidBatch: (id) => http.post(`/tenant/pay/batch/${id}/void`),
  /** 批次1：驳回批次编辑重提 REJECTED → DRAFT */
  batchReturnToEdit: (id) => http.post(`/tenant/pay/batch/${id}/edit`),
  execute: (id) => http.post(`/tenant/pay/batch/${id}/execute`),
  /**
   * D-DEC-438：执行付款被强制校验拦下后，逐条取回「哪条明细 / 缺哪一环节 / 去哪儿补」的结构化拦口报告。
   * 后端复用 execute() 同一条判据（不新增校验），只读。cfg 传 { silent: true } —— 拦口详情由
   * 页面弹窗呈现，不再叠一条全局 toast。
   */
  blockReport: (id, cfg) => http.get(`/tenant/pay/batch/${id}/block-report`, cfg),
  batchPage: (params, cfg) => http.get('/tenant/pay/batch/page', { params, ...cfg }),
  details: (id) => http.get(`/tenant/pay/batch/${id}/details`),
  payments: (billId) => http.get(`/tenant/pay/payment/list/${billId}`),
}

// ---------------- 主数据 ----------------
export const mdApi = {
  expenseItems: () => http.get('/tenant/masterdata/expense-item/tree'),
  // 按发票「货物或应税劳务、服务名称」智能匹配费用类型（命中返回规则，未命中返回 null）
  invoiceRuleMatch: (name) => http.get('/tenant/masterdata/invoice-rule/match', { params: { name } }),
  // D-DEC-042：单据发起页的「预算科目」红星查询改走引用选择规则 /item/pick（按公司/法人放行）。
  // 旧 /item/page 套个人 data_scope 五级——普通员工 COMMON_EMP=4(本部门) 而 bg_item.dept_id 实测恒 NULL，
  // 命中 0 行 ⇒ 分摊/费用行永远选不出合规科目。管理侧预算明细列表仍用 budgetApi.itemPage(/item/page)，不受影响。
  budgets: (params) => http.get('/tenant/budget/item/pick', { params }),
  budgetUpsert: (body) => http.post('/tenant/budget/item/upsert', body),
  subjects: (params) => http.get('/tenant/masterdata/subject/page', { params }),
  costCenters: (params) => http.get('/tenant/masterdata/cost-center/page', { params }),
  projects: (params) => http.get('/tenant/masterdata/project/page', { params }),
  cities: (params) => http.get('/tenant/masterdata/city/page', { params }),
  // 下拉专用：全部 NORMAL 城市（不分页）。单据发起页城市选择改用此接口，避免 /page 分页截断低 id 城市
  cityList: () => http.get('/tenant/masterdata/city/list'),
  payees: (params) => http.get('/tenant/masterdata/payee/page', { params }),
  // D-BIZ-08：走 /select（不挂权限点）——资产建档/付款计划/容差配置选供应商是基本功能，
  // 不该要求用户持有 md:supplier:list；后端该端点与 /page 同一服务逻辑。
  suppliers: (params) => http.get('/tenant/masterdata/supplier/select', { params }),
  customers: (params) => http.get('/tenant/customer/page', { params }),
}

// ---------------- 单据细分（差旅/冲借款/关联/分摊） ----------------
export const detailApi = {
  travels: (billId) => http.get(`/tenant/expense/detail/travels/${billId}`),
  saveTravels: (billId, list) => http.post(`/tenant/expense/detail/travels/${billId}`, list),
  loanOffsets: (billId) => http.get(`/tenant/expense/detail/loan-offset/${billId}`),
  addLoanOffset: (billId, body) => http.post(`/tenant/expense/detail/loan-offset/${billId}`, body),
  // D-UI-03（2026-10-19）：借款冲抵候选面。返回 availableBalance＝账面余额−在途占用，
  // 与服务端 registerLoanOccupancy 的放行校验同源；旧规则只给 loanBalance（账面），
  // 前端默认勾全额必然被拒（ExpenseDetailService 类注释里的「已知现有残口①」）。
  loanCandidates: (params) => http.get(`/tenant/expense/detail/loan-candidates`, { params }),
  // 报销单冲销预付款单（PAY/PREPAY）：body={prepayBillId, amount}，后端校验累计冲销≤预付余额
  prepayOffset: (billId, body) => http.post(`/tenant/expense/detail/prepay-offset/${billId}`, body),
  relations: (billId) => http.get(`/tenant/expense/detail/relation/${billId}`),
  addRelation: (sourceBillId, targetBillId, relationType, useAmount) =>
    http.post(`/tenant/expense/detail/relation`, null, { params: { sourceBillId, targetBillId, relationType, useAmount } }),
  shares: (billId) => http.get(`/tenant/expense/share/byBill`, { params: { billId } }),
  replaceShares: (billId, list) => http.post(`/tenant/expense/share/replace`, list, { params: { billId } }),
}

// ---------------- 共享运营-基础配置（需求 8.1） ----------------
export const opsCfgApi = {
  jobPage: (p) => http.get('/tenant/sharing/config/job/page', { params: p }),
  jobCreate: (b) => http.post('/tenant/sharing/config/job/create', b),
  jobUpdate: (b) => http.post('/tenant/sharing/config/job/update', b),
  jobDelete: (id) => http.post(`/tenant/sharing/config/job/${id}/delete`),
  groupPage: (p) => http.get('/tenant/sharing/config/group/page', { params: p }),
  groupCreate: (b) => http.post('/tenant/sharing/config/group/create', b),
  groupUpdate: (b) => http.post('/tenant/sharing/config/group/update', b),
  groupDelete: (id) => http.post(`/tenant/sharing/config/group/${id}/delete`),
  calendarPage: (p) => http.get('/tenant/sharing/config/calendar/page', { params: p }),
  calendarCreate: (b) => http.post('/tenant/sharing/config/calendar/create', b),
  calendarUpdate: (b) => http.post('/tenant/sharing/config/calendar/update', b),
  calendarDelete: (id) => http.post(`/tenant/sharing/config/calendar/${id}/delete`),
  dispatchRulePage: (p) => http.get('/tenant/sharing/config/dispatch-rule/page', { params: p }),
  dispatchRuleCreate: (b) => http.post('/tenant/sharing/config/dispatch-rule/create', b),
  dispatchRuleUpdate: (b) => http.post('/tenant/sharing/config/dispatch-rule/update', b),
  dispatchRuleDelete: (id) => http.post(`/tenant/sharing/config/dispatch-rule/${id}/delete`),
}

// ---------------- 合同管理 ----------------
export const contractApi = {
  page: (p) => http.get('/tenant/contract/page', { params: p }),
  create: (b) => http.post('/tenant/contract/create', b),
  update: (b) => http.post('/tenant/contract/update', b),
  delete: (id) => http.post(`/tenant/contract/${id}/delete`),
  submit: (id) => http.post(`/tenant/contract/${id}/submit`),
  /** 批次1：驳回合同编辑重提 REJECTED → DRAFT */
  returnToEdit: (id) => http.post(`/tenant/contract/${id}/edit`),
  approve: (id) => http.post(`/tenant/contract/${id}/approve`),
  reject: (id) => http.post(`/tenant/contract/${id}/reject`),
  change: (id) => http.post(`/tenant/contract/${id}/change`),
  close: (id) => http.post(`/tenant/contract/${id}/close`),
  supplement: (id) => http.post(`/tenant/contract/${id}/supplement`),
  conditions: (id) => http.get(`/tenant/contract/${id}/conditions`),
  template: () => http.post('/tenant/contract/template'),
  importContract: (csv) => http.post('/tenant/contract/import', csv,
    { headers: { 'Content-Type': 'text/plain;charset=utf-8' } }),
  conditionCreate: (b) => http.post('/tenant/contract/condition/create', b),
  conditionDelete: (id) => http.post(`/tenant/contract/condition/${id}/delete`),
  records: (id) => http.get(`/tenant/contract/${id}/records`),
  recordCreate: (b) => http.post('/tenant/contract/record/create', b),
  recordDelete: (id) => http.post(`/tenant/contract/record/${id}/delete`),
}

// ---------------- 客户 / 往来单位 ----------------
export const customerApi = {
  page: (p) => http.get('/tenant/customer/page', { params: p }),
  /**
   * D-V4R-96：往来单位候选（客户档案 ∪ 供应商主数据）。
   * 供应商会按 supplier_code 去重建档为 crm_customer，返回的 id 是语义正确的 customer_id。
   * 对公结算的往来单位下拉必须用这个，page() 只读 crm_customer 选不到供应商。
   */
  candidates: (p) => http.get('/tenant/customer/candidates', { params: p }),
  create: (b) => http.post('/tenant/customer/create', b),
  approve: (id) => http.post(`/tenant/customer/${id}/approve`),
  update: (b) => http.post('/tenant/customer/update', b),
  delete: (id) => http.post(`/tenant/customer/${id}/delete`),
  enable: (id, enable) => http.post(`/tenant/customer/${id}/enable`, null, { params: { enable } }),
}

// ---------------- 系统管理 ----------------
export const userApi = {
  page: (params) => http.get('/tenant/system/user/page', { params }),
  // P1-3：列表 phone/email 已脱敏，编辑前拉取明文联系方式
  full: (id) => http.get(`/tenant/system/user/${id}/full`),
  create: (body) => http.post('/tenant/system/user/create', body),
  update: (body) => http.post('/tenant/system/user/update', body),
  enable: (id, enable) => http.post(`/tenant/system/user/${id}/enable`, null, { params: { enable } }),
  resetPwd: (id) => http.post(`/tenant/system/user/${id}/resetPwd`),
  delete: (id) => http.post(`/tenant/system/user/${id}/delete`),
  // D-UR-1：员工管理分配角色（读回显 roleIds + 保存覆盖）
  roleIds: (id) => http.get(`/tenant/system/user/${id}/roleIds`),
  assignRoles: (id, roleIds) => http.post(`/tenant/system/user/${id}/assignRoles`, roleIds),
  batchImport: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/tenant/system/user/batchImport', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
}
export const roleApi = {
  page: (params, cfg) => http.get('/tenant/system/role/page', Object.assign({ params }, cfg || {})),
  create: (body) => http.post('/tenant/system/role/create', body),
  update: (body) => http.post('/tenant/system/role/update', body),
  enable: (id, enable) => http.post(`/tenant/system/role/${id}/enable`, null, { params: { enable } }),
  delete: (id) => http.post(`/tenant/system/role/${id}/delete`),
  assignMenus: (id, menuIds) => http.post(`/tenant/system/role/${id}/assignMenus`, menuIds),
  menuIds: (id) => http.get(`/tenant/system/role/${id}/menuIds`),
}
export const orgApi = {
  // cfg 是 axios 配置（silent 等）原样透传；早期实现不透传，导致 { silent: true } 被静默丢弃
  companyTree: (cfg) => http.get('/tenant/system/org/company/tree', cfg || {}),
  companyCreate: (body) => http.post('/tenant/system/org/company/create', body),
  companyUpdate: (body) => http.post('/tenant/system/org/company/update', body),
  companyDelete: (id) => http.post(`/tenant/system/org/company/${id}/delete`),
  companyEnable: (id, enable) => http.post(`/tenant/system/org/company/${id}/enable`, null, { params: { enable } }),
  // 兼容 deptTree({ silent: true }) 只传配置不传公司的调用：旧实现把对象当 companyId 拼进 query，
  // 静默产出非法参数（companyId=[object Object]）⇒ 部门下拉可能空表
  deptTree: (companyId, cfg) => {
    if (companyId && typeof companyId === 'object') { cfg = companyId; companyId = null }
    return http.get('/tenant/system/org/dept/tree', { params: companyId ? { companyId } : {}, ...(cfg || {}) })
  },
  deptCreate: (body) => http.post('/tenant/system/org/dept/create', body),
  deptUpdate: (body) => http.post('/tenant/system/org/dept/update', body),
  deptDelete: (id) => http.post(`/tenant/system/org/dept/${id}/delete`),
  deptEnable: (id, enable) => http.post(`/tenant/system/org/dept/${id}/enable`, null, { params: { enable } }),
  postList: (companyId, deptId) => http.get('/tenant/system/org/post/list', { params: { ...(companyId ? { companyId } : {}), ...(deptId ? { deptId } : {}) } }),
  postPage: (params) => http.get('/tenant/system/org/post/page', { params }),
  postCreate: (body) => http.post('/tenant/system/org/post/create', body),
  postUpdate: (body) => http.post('/tenant/system/org/post/update', body),
  postDelete: (id) => http.post(`/tenant/system/org/post/${id}/delete`),
}
export const dictApi = {
  typePage: (params) => http.get('/tenant/system/dict/type/page', { params }),
  typeCreate: (body) => http.post('/tenant/system/dict/type/create', body),
  typeUpdate: (body) => http.post('/tenant/system/dict/type/update', body),
  typeDelete: (id) => http.post(`/tenant/system/dict/type/${id}/delete`),
  dataList: (typeCode) => http.get('/tenant/system/dict/data/list', { params: { dictType: typeCode } }),
  /**
   * 【2026-10-15 前置A】内置字典免权限只读通道。
   * 服务端校验：仅 is_builtin=1 的类型可读，非内置返回空列表（不抛错）。
   * 仍需登录（不在 JwtAuthFilter 白名单内），但**不需要 system:dict:list 权限**
   * —— 该权限实测只授予 CONFIG_ADMIN/IT_ADMIN，业务角色 159 人读不到。
   */
  builtinDataList: (typeCode) => http.get('/tenant/system/dict/builtin/data/list', { params: { dictType: typeCode } }),
  /**
   * 【2026-10-16 第 4 批】内置字典**批量**免权限只读通道。
   * 一次取回多个类型（后端按 is_builtin 校验过滤，非内置类型不出现在返回 Map 里）。
   * 用途：枚举办结后有 66 个 enum_* 展示字典要预热，逐个请求会打 66 次往返。
   * @param {string[]} typeCodes dict_type 数组
   */
  builtinDataBatch: (typeCodes) => http.get('/tenant/system/dict/builtin/data/batch', { params: { types: (typeCodes || []).join(',') } }),
  dataCreate: (body) => http.post('/tenant/system/dict/data/create', body),
  dataUpdate: (body) => http.post('/tenant/system/dict/data/update', body),
  dataDelete: (id) => http.post(`/tenant/system/dict/data/delete/${id}`),
}

// ---------------- 影像管理 ----------------
export const imageApi = {
  batchList: (params) => http.get('/tenant/image/batch/list', { params }),
  // 标准 MultipartFile 上传（files + bizType/bizId/bizNo/zone）
  batchUpload: (formData) => http.post('/tenant/image/batch/upload-multi', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  batchDelete: (id) => http.post(`/tenant/image/batch/${id}/delete`),
  pages: (id) => http.get(`/tenant/image/batch/${id}/pages`),
  ocr: (id) => http.post(`/tenant/image/batch/${id}/ocr`),
  // 附件维度触发识别：入参是 sys_file 的 fileId（附件列表持有的就是它），后端自行解析所属影像批次
  ocrByFile: (fileId) => http.post(`/tenant/image/file/${fileId}/ocr`),
  // 影像任务
  taskPage: (params) => http.get('/tenant/image/task/page', { params }),
  seal: (id, sealed) => http.post(`/tenant/image/task/${id}/seal`, null, { params: { sealed } }),
  reviewStatus: (id, reviewStatus) => http.post(`/tenant/image/task/${id}/review-status`, null, { params: { reviewStatus } }),
  // 系统设置（6.3）
  settingPage: (p) => http.get('/tenant/image/setting/page', { params: p }),
  settingCreate: (b) => http.post('/tenant/image/setting/create', b),
  settingUpdate: (b) => http.post('/tenant/image/setting/update', b),
  settingDelete: (id) => http.post(`/tenant/image/setting/${id}/delete`),
  reviewPage: (p) => http.get('/tenant/image/review/page', { params: p }),
  reviewEvaluate: (b) => http.post('/tenant/image/review/evaluate', b),
  reviewCreate: (b) => http.post('/tenant/image/review/create', b),
  reviewDelete: (id) => http.post(`/tenant/image/review/${id}/delete`),
  exceptionPage: (p) => http.get('/tenant/image/exception/page', { params: p }),
  exceptionCreate: (b) => http.post('/tenant/image/exception/create', b),
  exceptionFix: (id, remark) => http.post(`/tenant/image/exception/${id}/fix`, null, { params: { remark } }),
  exceptionDelete: (id) => http.post(`/tenant/image/exception/${id}/delete`),
  // P2-5 OCR 识别记录（结构化票面写入数据库查询）
  ocrRecordPage: (p) => http.get('/tenant/ocr-records', { params: p }),
  ocrRecordByBatch: (batchId) => http.get(`/tenant/ocr-records/batch/${batchId}`),
}

// ---------------- 主数据维护 ----------------
export const supplierApi = {
  page: (params) => http.get('/tenant/masterdata/supplier/page', { params }),
  create: (body) => http.post('/tenant/masterdata/supplier/create', body),
  approve: (id) => http.post(`/tenant/masterdata/supplier/${id}/approve`),
  update: (body) => http.post('/tenant/masterdata/supplier/update', body),
  delete: (id) => http.post(`/tenant/masterdata/supplier/${id}/delete`),
  enable: (id, enable) => http.post(`/tenant/masterdata/supplier/${id}/enable`, null, { params: { enable } }),
  // 41号审查 P1-8 专属页子表：银行账户(N)/联系人(N)/资质证照(N)
  banks: (id) => http.get(`/tenant/masterdata/supplier/${id}/banks`),
  addBank: (id, b) => http.post(`/tenant/masterdata/supplier/${id}/banks`, b),
  deleteBank: (bankId) => http.post(`/tenant/masterdata/supplier/banks/${bankId}/delete`),
  contacts: (id) => http.get(`/tenant/masterdata/supplier/${id}/contacts`),
  addContact: (id, b) => http.post(`/tenant/masterdata/supplier/${id}/contacts`, b),
  deleteContact: (contactId) => http.post(`/tenant/masterdata/supplier/contacts/${contactId}/delete`),
  quals: (id) => http.get(`/tenant/masterdata/supplier/${id}/quals`),
  addQual: (id, b) => http.post(`/tenant/masterdata/supplier/${id}/quals`, b),
  deleteQual: (qualId) => http.post(`/tenant/masterdata/supplier/quals/${qualId}/delete`),
}
// ---------------- 采购三单域（采购订单 + 收货单） ----------------
export const purchaseApi = {
  orderPage: (params, cfg) => http.get('/tenant/purchase/order/page', Object.assign({ params }, cfg || {})),
  orderCreate: (body) => http.post('/tenant/purchase/order/create', body),
  orderUpdate: (body) => http.post('/tenant/purchase/order/update', body),
  /** 删除采购订单（仅 DRAFT 草稿且无下游关联，逻辑删除） */
  orderDelete: (id) => http.post(`/tenant/purchase/order/${id}/delete`),
  /** 作废采购订单：body={orderId, reason}；后端拒绝已收货/已付款订单并返回错误原因 */
  orderVoid: (body) => http.post('/tenant/purchase/order/void', body),
  /** 终止采购订单（人工最终状态，权限码 po:order:update）：body={reason}；已收货/已挂付款但不履约的单走这里 */
  orderTerminate: (id, body) => http.post(`/tenant/purchase/order/${id}/terminate`, body),
  /** 归档采购订单（人工最终状态，权限码 po:order:update）：CLOSED → ARCHIVED */
  orderArchive: (id) => http.post(`/tenant/purchase/order/${id}/archive`),
  /** 批次1：草稿采购订单提交审批 DRAFT → OPEN + 起 PURCHASE_ORDER_REVIEW */
  orderSubmit: (id) => http.post(`/tenant/purchase/order/${id}/submit`),
  /** 批次1：驳回采购订单编辑重提 REJECTED → DRAFT */
  orderReturnToEdit: (id) => http.post(`/tenant/purchase/order/${id}/edit`),
  receiptPage: (params) => http.get('/tenant/purchase/receipt/page', { params }),
  receiptCreate: (body) => http.post('/tenant/purchase/receipt/create', body),
}

// ---------------- 三单匹配容差参数（采购订单-收货单-发票 金额容差） 
// 契约：GET  /api/tenant/match-tolerance?companyId=1 → {companyId, amountPct, amountAbs, status}
//      POST /api/tenant/match-tolerance body={companyId, amountPct, amountAbs, status}
// 后端并行实现中：get 用 silent，404/失败由页面降级处理，不弹全局错误
export const matchToleranceApi = {
  get: (companyId) => http.get('/tenant/match-tolerance', { params: { companyId }, silent: true }),
  save: (body) => http.post('/tenant/match-tolerance', body),
}
// ---------------- P0-5 物理签收 ----------------
export const deliveryApi = {
  pending: (params) => http.get('/tenant/sharing/delivery/pending', { params }),
  page: (params) => http.get('/tenant/sharing/delivery/page', { params }),
  byBill: (billId) => http.get(`/tenant/sharing/delivery/byBill/${billId}`),
  sign: (id, body) => http.post(`/tenant/sharing/delivery/sign/${id}`, body),
  batchSign: (body) => http.post('/tenant/sharing/delivery/batch-sign', body),
  scanSign: (body) => http.post('/tenant/sharing/delivery/scan-sign', body),
  reject: (id, body) => http.post(`/tenant/sharing/delivery/${id}/reject`, body),
  apply: (body) => http.post('/tenant/sharing/delivery/apply', body),
  myDeliveries: (params) => http.get('/tenant/sharing/delivery/my-deliveries', { params }),
  register: (billId, body) => http.post(`/tenant/sharing/delivery/register/${billId}`, body),
  barcodePage: (params) => http.get('/tenant/sharing/delivery/barcode/page', { params }),
  findByBarcode: (barcodeValue) => http.get(`/tenant/sharing/delivery/barcode/${barcodeValue}`),
  generateBarcodes: (body) => http.post('/tenant/sharing/delivery/barcode/generate', body),
  shelve: (body) => http.post('/tenant/sharing/delivery/barcode/shelve', body),
}

// ---------------- P0-10 合规规则（内置 + CRUD） ----------------
export const complianceRuleApi = {
  list: () => http.get('/tenant/compliance/rule/list'),
  detail: (id) => http.get(`/tenant/compliance/rule/${id}`),
  create: (body) => http.post('/tenant/compliance/rule/create', body),
  update: (body) => http.post('/tenant/compliance/rule/update', body),
  toggle: (id, enable) => http.post(`/tenant/compliance/rule/${id}/toggle`, null, { params: { enable } }),
}

// ---------------- 合规（费用政策 + 预检引擎） ----------------
export const complianceApi = {
  policyPage: (params) => http.get('/tenant/compliance/policy/page', { params }),
  policyCreate: (body) => http.post('/tenant/compliance/policy/create', body),
  policyUpdate: (body) => http.post('/tenant/compliance/policy/update', body),
  precheck: (billId) => http.get(`/tenant/compliance/precheck?billId=${billId}`),
  // 智能审单
  reviewRules: (params) => http.get('/tenant/compliance/review/rules', { params }),
  reviewRuleCreate: (body) => http.post('/tenant/compliance/review/rules', body),
  reviewRuleUpdate: (body) => http.put('/tenant/compliance/review/rules', body),
  reviewRuleDelete: (id) => http.delete(`/tenant/compliance/review/rules/${id}`),
  reviewRuleEnable: (id) => http.post(`/tenant/compliance/review/rules/${id}/enable`),
  reviewRuleDisable: (id) => http.post(`/tenant/compliance/review/rules/${id}/disable`),
  reviewExecute: (body) => http.post('/tenant/compliance/review/execute', body),
  reviewLogs: (params) => http.get('/tenant/compliance/review/logs', { params }),
  reviewStats: () => http.get('/tenant/compliance/review/stats'),
  // 反欺诈
  fraudList: (params) => http.get('/tenant/compliance/fraud/list', { params }),
  fraudByBill: (billId) => http.get(`/tenant/compliance/fraud/by-bill?billId=${billId}`),
  fraudSummary: () => http.get('/tenant/compliance/fraud/summary'),
  // P2-5 风控看板：分页 + 复审
  fraudPage: (params) => http.get('/tenant/compliance/fraud/page', { params }),
  fraudReview: (id, action) => http.post(`/tenant/compliance/fraud/review/${id}`, { action }),
  // 41号审查 P2-12 申诉闭环:登记申诉/申诉复审
  fraudAppeal: (id, reason) => http.post(`/tenant/compliance/fraud/${id}/appeal`, { reason }),
  fraudAppealReview: (id, approve, note) => http.post(`/tenant/compliance/fraud/${id}/appeal-review`, { approve, note }),
  // 服务目录与 SLA
  catalogPage: (params) => http.get('/tenant/compliance/service-catalog', { params }),
  catalogCreate: (body) => http.post('/tenant/compliance/service-catalog', body),
  catalogUpdate: (body) => http.put('/tenant/compliance/service-catalog', body),
  catalogDelete: (id) => http.delete(`/tenant/compliance/service-catalog/${id}`),
  catalogStatus: (id, status) => http.post(`/tenant/compliance/service-catalog/${id}/status?status=${status}`),
  satisfactionPage: (params) => http.get('/tenant/compliance/satisfaction', { params }),
  satisfactionSubmit: (body) => http.post('/tenant/compliance/satisfaction', body),
}
// ---------------- 费用标准（expense_policy 主表 + 标准明细 + 版本，2026-10-17 新增）----------------
// 注：路径用后端 @RequestMapping 明确声明的 /expense-policy（实测 /policy 亦通，但不在 Controller 声明面内，
//     按「未登记路径不可调用」的端点治理规则，统一走声明面路径）。
export const expensePolicyApi = {
  page: (params) => http.get('/tenant/compliance/expense-policy/page', { params }),
  getById: (id) => http.get(`/tenant/compliance/expense-policy/${id}`),
  create: (body) => http.post('/tenant/compliance/expense-policy/create', body),
  update: (body) => http.post('/tenant/compliance/expense-policy/update', body),
  enable: (id) => http.post(`/tenant/compliance/expense-policy/${id}/enable`),
  disable: (id) => http.post(`/tenant/compliance/expense-policy/${id}/disable`),
  remove: (id) => http.delete(`/tenant/compliance/expense-policy/${id}`),
  effective: (params) => http.get('/tenant/compliance/expense-policy/effective', { params }),
}

export const policyStandardApi = {
  list: (policyId) => http.get('/tenant/compliance/policy-standard/list', { params: { policyId } }),
  create: (body) => http.post('/tenant/compliance/policy-standard/create', body),
  update: (body) => http.post('/tenant/compliance/policy-standard/update', body),
  remove: (id) => http.delete(`/tenant/compliance/policy-standard/${id}`),
}

export const policyVersionApi = {
  list: (policyId) => http.get('/tenant/compliance/policy-version/list', { params: { policyId } }),
  latest: (policyId) => http.get('/tenant/compliance/policy-version/latest', { params: { policyId } }),
  create: (body) => http.post('/tenant/compliance/policy-version/create', body),
  approve: (id, approverId) => http.post(`/tenant/compliance/policy-version/${id}/approve`, null, { params: { approverId } }),
  activate: (id) => http.post(`/tenant/compliance/policy-version/${id}/activate`),
  archive: (id) => http.post(`/tenant/compliance/policy-version/${id}/archive`),
  remove: (id) => http.delete(`/tenant/compliance/policy-version/${id}`),
}

export const periodApi = {
  page: (params, cfg) => http.get('/tenant/masterdata/period/page', { params, ...cfg }),
  create: (body) => http.post('/tenant/masterdata/period/create', body),
  update: (body) => http.post('/tenant/masterdata/period/update', body),
  delete: (id) => http.post(`/tenant/masterdata/period/${id}/delete`),
  close: (id) => http.post(`/tenant/masterdata/period/${id}/close`),
  reopen: (id) => http.post(`/tenant/masterdata/period/${id}/reopen`),
}

// ---------------- 主数据（币种/项目/银行账户/费用类型/科目/成本中心/城市/税码/报销标准/收款方/汇率） ----------------
function crud(base) {
  return {
    page: (p) => http.get(`/tenant/masterdata/${base}/page`, { params: p }),
    create: (b) => http.post(`/tenant/masterdata/${base}/create`, b),
    update: (b) => http.post(`/tenant/masterdata/${base}/update`, b),
    delete: (id) => http.post(`/tenant/masterdata/${base}/${id}/delete`),
    enable: (id, enable) => http.post(`/tenant/masterdata/${base}/${id}/enable`, null, { params: { enable } }),
  }
}
export const masterApi = {
  currency: {
    ...crud('currency'),
    /** 免权限只读下拉通道（后端 2026-10-18 新增）。
     *  币种散落 12 个业务页面 40 处、且是录入必填项，若走带 md:currency:list 的 /page，
     *  普通员工一律 4030，等于"要先有币种管理权才能填单"。本端点仅需登录，
     *  只返回 status=NORMAL 且未删的展示字段。 */
    options: () => http.get('/tenant/masterdata/currency/options'),
  },
  project: crud('project'),
  bank: {
    ...crud('bank-account'),
    // 印鉴/网银变更审批(TR01-02,银行账户专属页入口)
    sealChange: (id) => http.post(`/tenant/masterdata/bank-account/${id}/start-seal-change`),
  },
  item: { ...crud('expense-item'), tree: () => http.get('/tenant/masterdata/expense-item/tree') },
  subject: { ...crud('subject'), tree: () => http.get('/tenant/masterdata/subject/tree') },
  costCenter: { ...crud('cost-center'), tree: () => http.get('/tenant/masterdata/cost-center/tree') },
  city: crud('city'),
  tax: crud('tax-rate'),
  // 报销标准无 enable 端点
  std: {
    page: (p) => http.get('/tenant/masterdata/expense-standard/page', { params: p }),
    create: (b) => http.post('/tenant/masterdata/expense-standard/create', b),
    update: (b) => http.post('/tenant/masterdata/expense-standard/update', b),
    delete: (id) => http.post(`/tenant/masterdata/expense-standard/${id}/delete`),
  },
  payee: crud('payee'),
  invoiceRule: crud('invoice-rule'),
  rate: {
    ...crud('exchange-rate'),
    // 海外报账①：外币行系统价查询（直查→反向→经CNY交叉；无价 data=null 放行手填）
    lookup: (from, to, date) => http.get('/tenant/masterdata/exchange-rate/lookup', { params: { from, to, date } }),
  },
}

// ---------------- 基础数据-配置类（需求 2.17/2.23/2.27~2.34，统一 /tenant/masterdata/config/{type}/...） ----------------
function cfgCrud(type) {
  return {
    page: (p) => http.get(`/tenant/masterdata/config/${type}/page`, { params: p }),
    create: (b) => http.post(`/tenant/masterdata/config/${type}/create`, b),
    update: (b) => http.post(`/tenant/masterdata/config/${type}/update`, b),
    delete: (id) => http.post(`/tenant/masterdata/config/${type}/${id}/delete`),
  }
}
export const masterCfgApi = {
  form: { ...cfgCrud('form'), options: () => http.get('/tenant/masterdata/config/form/options') },
  loanLimit: cfgCrud('loanLimit'),
  loanInitial: cfgCrud('loanInitial'),
  orgType: cfgCrud('orgType'),
  cashFlow: cfgCrud('cashFlow'),
  itemCashMap: cfgCrud('itemCashMap'),
  itemBudgetMap: {
    ...cfgCrud('itemBudgetMap'),
    /** FINAL-01：登录即可读的映射选项（单据发起页自动带出预算科目） */
    options: () => http.get('/tenant/masterdata/config/itemBudgetMap/options'),
  },
  costCenterMap: cfgCrud('costCenterMap'),
  vehicle: cfgCrud('vehicle'),
  bank: cfgCrud('bank'),
  employeeLevel: cfgCrud('employeeLevel'),
  glConfig: cfgCrud('glConfig'),
  incomeExpense: cfgCrud('incomeExpense'),
  loanPayType: cfgCrud('loanPayType'),
  budgetCategory: cfgCrud('budgetCategory'),
  budgetOrg: cfgCrud('budgetOrg'),
  budgetPeriod: cfgCrud('budgetPeriod'),
  contractType: cfgCrud('contractType'),
  cip: cfgCrud('cip'),
  budgetType: cfgCrud('budgetType'),
}

// ---------------- 在建工程（成本归集 + 转固） ----------------
export const cipApi = {
  page: (p) => http.get('/tenant/masterdata/config/cip/page', { params: p }),
  create: (b) => http.post('/tenant/masterdata/config/cip/create', b),
  update: (b) => http.post('/tenant/masterdata/config/cip/update', b),
  delete: (id) => http.post(`/tenant/masterdata/config/cip/${id}/delete`),
  // 成本归集明细
  costPage: (cipId, p) => http.get(`/tenant/masterdata/config/cip/${cipId}/cost/page`, { params: p }),
  costAdd: (cipId, body) => http.post(`/tenant/masterdata/config/cip/${cipId}/cost/add`, body),
  costDelete: (costId) => http.post(`/tenant/masterdata/config/cip/cost/${costId}/delete`),
  // 转固定资产
  transferToAsset: (cipId, body) => http.post(`/tenant/masterdata/config/cip/${cipId}/transferToAsset`, body),
}

// ---------------- 在建工程转固流程（D-DEC-296：原 /tenant/cip/* 死契约 404，改指已上线 /tenant/masterdata/config/cip/*）
// inProgress → GET /cip/page（PageResult{records}）；toAsset → POST /cip/{id}/transferToAsset（MdCipTransferRequest）
export const cipFlowApi = {
  /** 在途在建工程列表：data 为 {records:[...]}（宽容解析；在途=status NORMAL） */
  inProgress: () => http.get('/tenant/masterdata/config/cip/page', { params: { pageNum: 1, pageSize: 100 }, silent: true }),
  /** 转固：POST /tenant/masterdata/config/cip/{id}/transferToAsset */
  toAsset: (cipId, body) => http.post(`/tenant/masterdata/config/cip/${cipId}/transferToAsset`, body, { silent: true }),
}

// ---------------- 全局参数 / 系统日志 ----------------
export const configApi = {
  page: (params) => http.get('/tenant/system/config/page', { params }),
  create: (body) => http.post('/tenant/system/config/create', body),
  update: (body) => http.post('/tenant/system/config/update', body),
  // 业务参数单键读取（后端白名单，登录即可；系统参数整表仍要 system:config:list）
  value: (key) => http.get('/tenant/system/config/value', { params: { key }, silent: true }),
  ocrGet: () => http.get('/tenant/system/config/ocr'),
  ocrSave: (body) => http.post('/tenant/system/config/ocr', body),
}

// ---------------- 查询方案（Variant）——保存个人常用筛选组合 ----------------
export const queryVariantApi = {
  list: (pageKey) => http.get('/tenant/system/query-variant/list', { params: { pageKey } }),
  getDefault: (pageKey) => http.get('/tenant/system/query-variant/default', { params: { pageKey } }),
  create: (body) => http.post('/tenant/system/query-variant/create', body),
  update: (body) => http.post('/tenant/system/query-variant/update', body),
  delete: (id) => http.post('/tenant/system/query-variant/delete', null, { params: { id } }),
  setDefault: (id) => http.post('/tenant/system/query-variant/setDefault', null, { params: { id } }),
}

// ---------------- 外部接口统一配置 ----------------
export const extInterfaceApi = {
  page: (params) => http.get('/tenant/system/ext-interface/page', { params }),
  getById: (id) => http.get(`/tenant/system/ext-interface/${id}`),
  create: (body) => http.post('/tenant/system/ext-interface/create', body),
  update: (body) => http.post('/tenant/system/ext-interface/update', body),
  delete: (id) => http.post(`/tenant/system/ext-interface/${id}/delete`),
  toggleStatus: (id, status) => http.post(`/tenant/system/ext-interface/${id}/toggle-status`, { status }),
  setDefault: (id) => http.post(`/tenant/system/ext-interface/${id}/set-default`),
  test: (id) => http.post(`/tenant/system/ext-interface/${id}/test`),
  getByCategory: (category) => http.get('/tenant/system/ext-interface/by-category', { params: { category } }),
}

// ---------------- 集成网关（调用日志 / 统计 / 测试） ----------------
export const integrationApi = {
  logPage: (params) => http.get('/tenant/system/integration/logs/page', { params }),
  stats: () => http.get('/tenant/system/integration/stats'),
  test: (id) => http.post(`/tenant/system/integration/${id}/test`),
}

export const logApi = {
  page: (params) => http.get('/tenant/system/log/page', { params }),
}

// ---------------- 共享运营（任务池 / 抢派单 / 凭证） ----------------
export const sharingApi = {
  pool: (params, cfg) => http.get('/tenant/sharing/task/pool', { params, ...cfg }),
  mine: (params) => http.get('/tenant/sharing/task/mine', { params }),
  receive: (id) => http.post(`/tenant/sharing/task/${id}/receive`),
  // D-DEC-024/D-DEC-030：办结备注随请求提交（remark → op_audit_task.remark 记录）
  finish: (id, remark) => http.post(`/tenant/sharing/task/${id}/finish`, null, { params: { remark } }),
  manage: (params) => http.get('/tenant/sharing/task/manage', { params }),
  withdraw: (id) => http.post(`/tenant/sharing/task/${id}/withdraw`),
  reassign: (id, assigneeId) => http.post(`/tenant/sharing/task/${id}/reassign`, null, { params: { assigneeId } }),
  returnTask: (id, reason) => http.post(`/tenant/sharing/task/${id}/return`, null, { params: { reason } }),
  voucherGenerate: (billId) => http.post(`/tenant/sharing/voucher/generate/${billId}`),
  // D-DEC-168 契约备查：端点 SharingController.voucherPage 目前只绑 pageNum/pageSize/status/sourceType，
  // companyId/periodYear/periodMonth 需后端补形参后才生效（前端已按 GlLedger 表头条件送出，后端未落地前静默忽略、不报错）。
  voucherPage: (params) => http.get('/tenant/sharing/voucher/page', { params }),
  // A12 执行结果视图（RA-ROOT-09）：审批通过后自动制证失败事件，纯只读
  voucherGenFailurePage: (params) => http.get('/tenant/sharing/voucher/gen-failure/page', { params }),
  voucherGenFailureStats: (params) => http.get('/tenant/sharing/voucher/gen-failure/stats', { params }),
  voucherLines: (id) => http.get(`/tenant/sharing/voucher/${id}/lines`),
  voucherLinesUpdate: (id, lines) => http.post(`/tenant/sharing/voucher/${id}/lines/update`, lines),
voucherPost: (id) => http.post(`/tenant/sharing/voucher/${id}/post`),
  voucherBatchPost: (ids) => http.post('/tenant/sharing/voucher/batch-post', ids),
}

// ---------------- 预算管理（需求 3 章） ----------------
export const budgetApi = {
  schemePage: (params) => http.get('/tenant/budget/scheme/page', { params }),
  schemeCreate: (body) => http.post('/tenant/budget/scheme/create', body),
  schemeUpdate: (body) => http.post('/tenant/budget/scheme/update', body),
  schemeEnable: (id, enable) => http.post(`/tenant/budget/scheme/${id}/enable`, null, { params: { enable } }),
  // 预算审批流：提交审批(DRAFT→APPROVING)与下达(APPROVING→ISSUED)
  schemeSubmit: (schemeId) => http.post('/tenant/budget/scheme/submit', { schemeId }),
  schemeIssue: (schemeId) => http.post('/tenant/budget/scheme/issue', { schemeId }),
  itemPage: (params) => http.get('/tenant/budget/item/page', { params }),
  itemUpsert: (body) => http.post('/tenant/budget/item/upsert', body),
  adjustPage: (params) => http.get('/tenant/budget/adjust/page', { params }),
  adjustCreate: (body) => http.post('/tenant/budget/adjust/create', body),
  adjustLines: (adjustId) => http.get(`/tenant/budget/adjust/lines/${adjustId}`),
  adjustUpdate: (body) => http.post('/tenant/budget/adjust/update', body),
  adjustApprove: (id) => http.post(`/tenant/budget/adjust/approve/${id}`),
  occupyPage: (params) => http.get('/tenant/budget/occupy/page', { params }),
  occupyCheck: (body) => http.post('/tenant/budget/occupy/check', body),
  occupyOccupy: (body) => http.post('/tenant/budget/occupy/occupy', body),
  occupyRelease: (body) => http.post('/tenant/budget/occupy/release', body),
  occupyWriteOff: (body) => http.post('/tenant/budget/occupy/write-off', body),
  /** 预算执行明细：params={ period?, deptId?, subjectId? }，data={ list: [{ itemName, period, occupy, writeOff, balance }] } */
  execution: (params) => http.get('/tenant/budget/execution', { params }),
  // 预算明细 Excel 导入/导出（2026-09-17）
  itemExport: (params) => http.get('/tenant/budget/item/export', { params, responseType: 'blob' }),
  itemImport: (schemeId, file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/tenant/budget/item/import?schemeId=' + schemeId, fd, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 })
  },
}

// ---------------- 发票管理（验真 / 补录 / 绑定） ----------------
export const invoiceApi = {
  page: (params, cfg) => http.get('/tenant/invoice/page', { params, ...cfg }),
  create: (body) => http.post('/tenant/invoice/create', body),
  // 销售发票开具（OTC02 INVOICE_ISSUE）：价税分离校验后发起开票审批流
  issue: (body) => http.post('/tenant/invoice/issue', body),
  bind: (body) => http.post('/tenant/invoice/bind', body),
  // 反核销（D-V3-135 配套 · 单据反操作链第一环）：解除勾稽+解锁发票；body={billNo,invoiceId}
  unbind: (body) => http.post('/tenant/invoice/unbind', body),
  // cfg：移动端（H5 审批/提单页）把"本单已勾稽发票"当补充信息读，无权/失败时不该打断主流程，
  // 故允许传 { silent: true }（缺省不传＝行为与原来完全一致）。
  billInvoices: (billId, cfg) => http.get('/tenant/invoice/bill-invoices', { params: { billId }, ...(cfg || {}) }),
  // 发票「关联单据」候选单据（D-DEC-113）：按操作权限查询、不限提单人，附本单已勾稽发票额与缺口
  bindableBills: (params) => http.get('/tenant/invoice/bindable-bills', { params }),
  // 验真增强
  verifyPage: (params) => http.get('/tenant/invoice/enhance/verify/page', { params }),
  verifyRun: (body) => http.post('/tenant/invoice/enhance/verify/run', body || {}),
  verifyNow: (invoiceId, result, msg) => http.post('/tenant/invoice/enhance/verify/now', { invoiceId, result, msg }),
  verifyBatch: (invoiceIds) => http.post('/tenant/invoice/enhance/verify/batch', invoiceIds),
  lineList: (params) => http.get('/tenant/invoice/enhance/line/list', { params }),
  lineCreate: (body) => http.post('/tenant/invoice/enhance/line/create', body),
  lineReplace: (body) => http.post('/tenant/invoice/enhance/line/replace', body),
  lineDelete: (id) => http.post(`/tenant/invoice/enhance/line/${id}/delete`),
  // P1-5 进项发票全生命周期（认证勾选 / 取消勾选 / 进项转出 / 票账比对申报汇总）
  certify: (ids, period) => http.post('/tenant/invoice/certify', { ids, period }),
  cancelCertify: (ids) => http.post('/tenant/invoice/certify/cancel', { ids }),
  transferOut: (ids) => http.post('/tenant/invoice/certify/transfer-out', { ids }),
  filingSummary: (period) => http.get('/tenant/invoice/filing-summary', { params: { period } }),
  // 销项发票作废：body={ invoiceId, reason }（原因必填由前端校验）；后端校验当月/核销引用等条件，失败原样提示
  voidInvoice: (body) => http.post('/tenant/invoice/void', body),
  // 销项发票红冲：body={ invoiceId, reason }
  redFlush: (body) => http.post('/tenant/invoice/red-flush', body),
}

// ---------------- GL总账 ----------------
export const glApi = {
  /** 总账分录分页查询（R5-D-149：orderBy/order 走后端白名单排序，可选） */
  entry: (pageNum, pageSize, companyId, year, month, subjectId, orderBy, order) =>
    http.get('/tenant/op/gl/entry', { params: { pageNum, pageSize, companyId, year, month, subjectId, orderBy, order } }),
  /** 科目余额表 */
  balance: (companyId, year, month) =>
    http.get('/tenant/op/gl/balance', { params: { companyId, year, month } }),
  /** 试算平衡（includeUnposted=true 时并入本期间未过账凭证；默认只统计已过账分录） */
  trial: (companyId, year, month, includeUnposted) =>
    http.get('/tenant/op/gl/trial', { params: { companyId, year, month, includeUnposted } }),
  /** 查询凭证是否已落账 */
  posted: (voucherId) =>
    http.get('/tenant/op/gl/posted', { params: { voucherId } }),
  /** 手动重新落账 */
  voidVoucher: (id) => http.post(`/tenant/sharing/voucher/void/${id}`),
  reverseVoucher: (id, reason) => http.post(`/tenant/sharing/voucher/reverse/${id}`, { reason }),
  repost: (voucherId) =>
    http.post('/tenant/op/gl/repost', null, { params: { voucherId } }),
  /**
   * 凭证批量过账：body=voucherIds 数组，响应 data={ success: n, fail: [{ id, reason }] }。
   * silent：接口由后端并行部署中，404 时由调用方（GlLedger 凭证台账）给出友好提示，对齐 wfApi.batchApprove 模式。
   *
   * D-DEC-165：端点签名就是 @RequestBody List<Long> ids，body.comment（过账说明）发出去也会被丢弃——
   * 因此调用方不得再把它设为必填；入账理由要记录需后端把值写进 op_voucher.remark（红字冲销已在用该列）。
   * D-DEC-166（2026-10-01 整改，待编译部署验证）：制审分离已改由后端强制——
   * VoucherService.post() 校验"过账人 ≠ 凭证 create_by"，自制自过直接拒绝（批量口逐张回显 reason 进 failed[]）。
   * GlLedger.vue 的 canPostRow/postableRows 因此降级为体验层（少点几次按钮），不再是唯一校验；
   * 此前的「制审分离由前端勾选控制，后端二次校验」在整改前属不实声明。
   */
  // 整改(20260918, 契约断裂修复): 批量过账统一走已实现的后端端点 /tenant/sharing/voucher/batch-post,
  // 并将 Map<id,msg> 结果适配为凭证台账期望的 { success, failed:[{id,msg}] } 形态(原 gl 命名空间端点从未部署, 404)。
  // 兼容两种入参：数组，或 { voucherIds, comment }（comment 会被后端丢弃，见上方 D-DEC-165）。
  batchPostVoucher: async (body) => {
    const ids = body && Array.isArray(body.voucherIds) ? body.voucherIds : body
    const data = await http.post('/tenant/sharing/voucher/batch-post', ids)
    const entries = Object.entries(data || {})
    const failed = entries.filter(([, m]) => m !== 'OK').map(([id, m]) => ({ id, msg: m }))
    return { success: entries.length - failed.length, failed }
  },
  /** 反结账：body={ period }；后端校验权限与下一期间状态，失败原样提示（回退后该期间重新开放记账） */
  monthUnclose: (period) => http.post('/tenant/gl/month/unclose', { period }),
  /** 损益结转：body={ period }；去重拒绝时后端返回「该期间已结转」，调用方原样提示 */
  profitCarry: (period) => http.post('/tenant/gl/profit-carry', { period }),
}

// ---------------- GL专项流程（税金计提/薪酬发放/内部往来抵消） ----------------
export const glexApi = {
  /* 税金计提 */
  taxPage: (pageNum, pageSize, period, taxType, keyword) =>
    http.get('/tenant/glex/tax-provision/page', { params: { pageNum, pageSize, period, taxType, keyword } }),
  taxProvision: (period) =>
    http.post('/tenant/glex/tax-provision/provision', null, { params: { period } }),
  // D-DEC-440（X-42 甲）：原 taxProvisionPay(/glex/tax-provision/pay → GL_TAX_PAY 独立缴款流) 已随端点作废删除；
  // 税金缴款走对公付款通道（付款单 / 付款批次），不再由税金页起流。
  /* 薪酬发放 */
  salaryPage: (pageNum, pageSize, period, departmentId, keyword) =>
    http.get('/tenant/glex/salary/page', { params: { pageNum, pageSize, period, departmentId, keyword } }),
  salaryCalculate: (period, deptId, empCount) =>
    http.post('/tenant/glex/salary/calculate', null, { params: { period, deptId, empCount } }),
  salaryPay: (id) =>
    http.post(`/tenant/glex/salary/${id}/pay`),
  // D-DEC-143（12-30:171）：人员级工资明细（应发/其他扣除/社保/个税/实发 到人）。
  // 载体表 gl_salary_run_detail 的建表 DDL 尚为注释态未执行，服务端未建表时返回空集（不是报错），
  // 调用方按"暂无人员级明细"渲染即可。列表页 GlSpecial.vue 属他役在修面，本行只接 API，不落展开。
  salaryDetail: (id) =>
    http.get(`/tenant/glex/salary/${id}/detail`),
  /* 内部往来抵消 */
  interPage: (pageNum, pageSize, period, interFlag, keyword) =>
    http.get('/tenant/glex/inter-offset/page', { params: { pageNum, pageSize, period, interFlag, keyword } }),
  interGenerate: (period) =>
    http.post('/tenant/glex/inter-offset/generate', null, { params: { period } }),
  interPost: (id) =>
    http.post(`/tenant/glex/inter-offset/${id}/post`),
  // X-52（D-DEC-294）手工成对录入：body 必须是 [来账(应收), 往账(应付)] 两条镜像分录，
  // 条数≠2 / 金额不成对相等 / 金额≤0 / 方向非 RECEIVABLE|PAYABLE 都由后端拒（GlInterOffsetController:44-51
  // + GlInterOffsetService.create:155-166），权限码 gl:inter:offset，成功后自动发起 GL_OFFSET 审批。
  interCreate: (pair) =>
    http.post('/tenant/glex/inter-offset/create', pair),
  /* 一般总账记账（G1：ACCRUAL/DEFER/MANUAL/OTHER_RECEIPT） */
  journalPage: (pageNum, pageSize, period, entryType, keyword) =>
    http.get('/tenant/glex/journal/page', { params: { pageNum, pageSize, period, entryType, keyword } }),
  journalGenerate: (period, entryType, summary, amount, debitSubjectId, creditSubjectId) =>
    http.post('/tenant/glex/journal/generate', null, { params: { period, entryType, summary, amount, debitSubjectId, creditSubjectId } }),
  // R5-D-167：记账域冲销（红字反向凭证 / 草稿作废）——GlJournalController:58 POST /{id}/reverse，
  // reason 为可选查询参数（后端 GlJournalService.reverse 校验已过账方可冲销）。
  journalReverse: (id, reason) =>
    http.post(`/tenant/glex/journal/${id}/reverse`, null, { params: { reason } }),
  /* 月结（G1） */
  monthClosePage: (pageNum, pageSize, period, keyword) =>
    http.get('/tenant/glex/month-close/page', { params: { pageNum, pageSize, period, keyword } }),
  monthClose: (period, companyId) =>
    http.post('/tenant/glex/month-close/close', null, { params: { period, companyId } }),
  /* GL 主数据维护（G1） */
  sharedMasterPage: (pageNum, pageSize, period, type, keyword) =>
    http.get('/tenant/glex/shared-master/page', { params: { pageNum, pageSize, period, type, keyword } }),
  sharedMasterSubmit: (period, type, action, subjectCode, subjectName, description) =>
    http.post('/tenant/glex/shared-master/submit', null, { params: { period, type, action, subjectCode, subjectName, description } }),
  /* 合并报表（GL_MERGE）：创建底稿 / 汇总合并即发起合并审批流 */
  consolidatePage: (pageNum, pageSize, period, status, keyword) =>
    http.get('/tenant/glex/consolidate/page', { params: { pageNum, pageSize, period, status, keyword } }),
  consolidateCreate: (body) =>
    http.post('/tenant/glex/consolidate/create', body),
  consolidateRun: (period) =>
    http.post('/tenant/glex/consolidate/consolidate', null, { params: { period } }),
  consolidateAdjust: (id, rawRows) =>
    http.post('/tenant/glex/consolidate/' + id + '/adjust', null, { params: { rawRows } }),
  /* 税务退税三场景（出口免抵退 / 留抵退税 / 即征即退） */
  taxRefundPage: (pageNum, pageSize, refundType, status, period, keyword) =>
    http.get('/tenant/glex/tax-refund/page', { params: { pageNum, pageSize, refundType, status, period, keyword } }),
  taxRefundCreate: (body) =>
    http.post('/tenant/glex/tax-refund/create', body),
  taxRefundSubmit: (id) =>
    http.post('/tenant/glex/tax-refund/submit', { id }),
  taxRefundStatus: (id, to, remark, refundedAmount) =>
    http.post('/tenant/glex/tax-refund/status', { id, to, remark, refundedAmount }),
  taxRefundCreditQuota: () =>
    http.get('/tenant/glex/tax-refund/credit-quota'),
}

// ---------------- 月末外币汇兑重估（CAS 19，P1-7 四步化：试算→确认→凭证→反冲）----------------
export const fxRevalApi = {
  page: (params) => http.get('/tenant/glex/fx-reval/page', { params }),
  run: (period) => http.post('/tenant/glex/fx-reval/run', null, { params: { period } }),
  trial: (period) => http.get('/tenant/glex/fx-reval/trial', { params: { period } }),
  reverse: (id) => http.post(`/tenant/glex/fx-reval/${id}/reverse`),
}

// ---------------- 系统健康探活（免鉴权 /api/health，P0-4 运行状态真实探活）----------------
export const healthApi = {
  get: () => http.get('/health', { silent: true }),
}

// ---------------- 管理报表 ----------------
export const dashApi = {
  stats: (billType) => http.get('/tenant/dashboard/expense/stats', { params: { billType } }),
  trend: (months) => http.get('/tenant/dashboard/expense/trend', { params: { months } }),
  budget: () => http.get('/tenant/dashboard/budget/execution'),
  pay: () => http.get('/tenant/dashboard/pay/progress'),
  deptRanking: () => http.get('/tenant/dashboard/dept/ranking'),
  approvalEfficiency: () => http.get('/tenant/dashboard/approval/efficiency'),
  // WS-4 看板完整性：合同/发票/档案三卡进度（在途/今日完成/超时）
  contractProgress: () => http.get('/tenant/dashboard/contract/progress'),
  invoiceProgress: () => http.get('/tenant/dashboard/invoice/progress'),
  archiveProgress: () => http.get('/tenant/dashboard/archive/progress'),
}
// AI 平权 P3：问数入口（服务端编排→AI 网关→目录内只读动作）
export const aiApi = {
  ask: (question, sessionId) => http.post('/tenant/ai/ask', { question, sessionId }, { timeout: 60000, silent: true }),
  actions: () => http.get('/tenant/ai/ask/actions', { silent: true }),
  // 指令通道：只读动作即时执行，写动作返回待确认票据（不执行）
  instruct: (question, sessionId) => http.post('/tenant/ai/ask/instruct', { question, sessionId }, { timeout: 60000, silent: true }),
  // 执行一律走网关那套校验（红线/权限/去重/票据），前端不自行调业务端点
  execute: (payload, idempotencyKey) => http.post('/ai/gateway/execute', payload, {
    timeout: 60000, silent: true, headers: idempotencyKey ? { 'X-Idempotency-Key': idempotencyKey } : {},
  }),
  myTickets: () => http.get('/tenant/ai/confirm/mine', { silent: true }),
  pendingTickets: () => http.get('/tenant/ai/confirm/pending', { silent: true }),
  approveTicket: (id, approve, reason) => http.post(`/tenant/ai/confirm/${id}`, { approve, reason }, { silent: true }),
  cancelTicket: (id) => http.post(`/tenant/ai/confirm/${id}/cancel`, {}, { silent: true }),
  skills: () => http.get('/tenant/ai/skills', { silent: true }),
  health: () => http.get('/tenant/ai/health', { silent: true }),
  feedback: (payload) => http.post('/tenant/ai/feedback', payload, { silent: true }),
}
export const reportApi = {
  bills: () => http.get('/tenant/report/export/bills', { responseType: 'blob' }),
  deptRanking: () => http.get('/tenant/report/export/dept-ranking', { responseType: 'blob' }),
  budgetExecution: () => http.get('/tenant/report/export/budget-execution', { responseType: 'blob' }),
  loanBalance: () => http.get('/tenant/report/loan-balance'),
  contractSummary: (cfg) => http.get('/tenant/report/contract-summary', cfg || {}),
  contractSummaryPage: (params) => http.get('/tenant/report/contract-summary/page', { params }),
  expenseDetail: (kw) => http.get('/tenant/report/expense-detail', { params: { kw } }),
  opsStatistics: () => http.get('/tenant/report/ops-statistics'),
  expenseSummary: (p) => http.get('/tenant/report/expense-summary', { params: p }),
  monthlyTrend: () => http.get('/tenant/report/monthly-trend'),
  expenseComposition: (itemName) => http.get('/tenant/report/expense-composition', { params: { itemName } }),
  // ---- 扩展报表 ----
  apAging: () => http.get('/tenant/report/ap-aging'),
  arAging: () => http.get('/tenant/report/ar-aging'),
  cashFlowSummary: () => http.get('/tenant/report/cash-flow'),
  bankBalance: () => http.get('/tenant/report/bank-balance'),
  supplierPayment: () => http.get('/tenant/report/supplier-payment'),
  inputInvoiceStats: () => http.get('/tenant/report/input-invoice'),
  outputInvoiceStats: () => http.get('/tenant/report/output-invoice'),
  taxBurdenAnalysis: () => http.get('/tenant/report/tax-burden'),
  opsEfficiency: () => http.get('/tenant/report/ops-efficiency'),
  qualityPassRate: () => http.get('/tenant/report/quality-pass-rate'),
  imageStatistics: () => http.get('/tenant/report/image-statistics'),
  contractExecution: () => http.get('/tenant/report/contract-execution'),
  budgetOverrunAlert: () => http.get('/tenant/report/budget-overrun'),
  loanOverdue: () => http.get('/tenant/report/loan-overdue'),
  voucherStats: () => http.get('/tenant/report/voucher-stats'),
}

// ---------------- P0-6 OTC 订单到收款：应收单 / 收款核销 / 催收待办 / 坏账计提 ----------------
export const receivableApi = {
  // 应收单
  page: (p) => http.get('/tenant/receivable/page', { params: p }),
  create: (b) => http.post('/tenant/receivable/create', b),
  listByCustomer: (customerId) => http.get('/tenant/receivable/listByCustomer', { params: { customerId } }),
  // D-DEC-407：逾期批处理补可选账期入参 { asOfDate: 'yyyy-MM-dd' } 或 { period: 'yyyyMM'/'yyyy-MM' }；
  // 不传＝后端按系统当前日跑（与旧调用逐字等价）。业务期间晚于系统日时必须传账期，否则逾期集恒空。
  accrueOverdue: (baseline) => http.post('/tenant/receivable/accrueOverdue', null, { params: baseline || {} }),
  // 应收管理分析报告（OTC05 AR_REPORT）：按期间发起审批流
  report: (period) => http.post('/tenant/receivable/report', null, { params: { period } }),
  // 收款核销（先进先出）
  receiptList: () => http.get('/tenant/receipt/list'),
  receiptCreate: (b) => http.post('/tenant/receipt/create', b),
  receiptApply: (id) => http.post(`/tenant/receipt/${id}/apply`),
  // 催收待办
  collectList: () => http.get('/tenant/collect-todo/list'),
  // D-DEC-407：days 仍是逾期天数阈值，baseline 为可选账期 { asOfDate }／{ period }（留空＝系统当前日）
  collectCreateOverdue: (days, baseline) => http.post('/tenant/collect-todo/createOverdue', null, { params: { days, ...(baseline || {}) } }),
  collectSolve: (id) => http.post(`/tenant/collect-todo/${id}/solve`),
  // 坏账计提
  badDebtList: (period) => http.get('/tenant/bad-debt/list', { params: { period } }),
  badDebtProvision: (period) => http.post('/tenant/bad-debt/provision', null, { params: { period } }),
  badDebtConfirm: (id, reason) => http.post(`/tenant/bad-debt/${id}/confirm`, null, { params: { reason } }),
  // 坏账转回/核销（蓝图流程22）
  badDebtReverse: (id) => http.post(`/tenant/bad-debt/${id}/reverse`),
  badDebtWriteoff: (id, b) => http.post(`/tenant/bad-debt/${id}/writeoff`, b),
  badDebtWriteoffExcel: (formData) => http.post('/tenant/bad-debt/writeoff-import-excel', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  // 应收账款对账（OTC04 AR_RECON）：发起对账审批流
  reconStart: (period, customerId) => http.post('/tenant/receivable/recon/start', null, { params: { period, customerId } }),
}

// ---------------- P0-7 FA 投资到资产：资产卡片 / 资产类别 / 存货收发存 ----------------
export const assetApi = {
  page: (p) => http.get('/tenant/asset/page', { params: p }),
  create: (b) => http.post('/tenant/asset/create', b),
  update: (b) => http.post('/tenant/asset/update', b),
  detail: (id) => http.get(`/tenant/asset/${id}`),
  delete: (id) => http.post(`/tenant/asset/${id}/delete`),
  scrap: (id) => http.post(`/tenant/asset/${id}/scrap`),
  depreciate: (id) => http.post(`/tenant/asset/${id}/depreciate`),
  depreciateMonth: () => http.post('/tenant/asset/depreciate/batch'),
  netValue: (id) => http.get(`/tenant/asset/${id}/netValue`),
  // X-06（D-DEC-431）读取环节：单卡变动台账（原值调整/盘点差异的原因＋前后镜像＋审批最终状态）
  changeLogs: (id) => http.get(`/tenant/asset/${id}/change-logs`),
  // FA 业务审批流入口（P1 补齐）：调拨/外销处置/盘点差异/原值调整/减值 → 均发起对应审批流
  transfer: (id, targetDeptId, targetCompanyId, remark) => http.post(`/tenant/asset/${id}/transfer`, null, { params: { targetDeptId, targetCompanyId, remark } }),
  disposeSell: (id, sellAmount) => http.post(`/tenant/asset/${id}/disposeSell`, null, { params: { sellAmount } }),
  checkDiff: (id, diffAmount, remark) => http.post(`/tenant/asset/${id}/checkDiff`, null, { params: { diffAmount, remark } }),
  adjustValue: (id, newOriginalAmount, reason) => http.post(`/tenant/asset/${id}/adjustValue`, null, { params: { newOriginalAmount, reason } }),
  impair: (id, impairmentAmount) => http.post(`/tenant/asset/${id}/impair`, null, { params: { impairmentAmount } }),
  // 在建工程转固定资产（FA_CIP_TO_ASSET）：CIP 状态资产转固并发起审批
  cipToAsset: (id, transferDate, remark) => http.post(`/tenant/asset/${id}/cipToAsset`, null, { params: { transferDate, remark } }),
  categoryPage: (p) => http.get('/tenant/asset/category/page', { params: p }),
  categoryCreate: (b) => http.post('/tenant/asset/category/create', b),
  categoryUpdate: (b) => http.post('/tenant/asset/category/update', b),
  categoryDelete: (id) => http.post(`/tenant/asset/category/${id}/delete`),
  inventoryPage: (p) => http.get('/tenant/inventory/page', { params: p }),
  // R5-D-189：出入库流水（stock_move 只读回放；此前该表只写不读）
  inventoryMoves: (p) => http.get('/tenant/inventory/moves/page', { params: p }),
  inventoryInStock: (item, qty, price) => http.post('/tenant/inventory/inStock', item, { params: { qty, price } }),
  inventoryOutStock: (item, qty) => http.post('/tenant/inventory/outStock', item, { params: { qty } }),
  inventoryBalance: (item) => http.post('/tenant/inventory/balance', item),
  // FA 资本性支出（FA_CER）：立项申请发起 CER 审批；budgetItemId 可选（预算集成，空则自动取综合预算）
  capitalRequest: (amount, assetName, deptId, companyId, remark, budgetItemId) =>
    http.post('/tenant/asset/capitalRequest', null, { params: { amount, assetName, deptId, companyId, remark, budgetItemId } }),
  // 存货领用出库（INV_ISSUE_PROJECT 项目内 / INV_ISSUE_NONPROJECT 项目外）/ 处置 3 阶段 / 内部销售：均以物料 key + 数量发起对应审批
  inventoryIssue: (item, qty, purpose, projectType) => http.post('/tenant/inventory/issue', item, { params: { qty, purpose, projectType } }),
  inventoryDisposal: (item, qty, remark) => http.post('/tenant/inventory/disposal', item, { params: { qty, remark } }),
  inventoryDisposalCheck: (item, qty, remark) => http.post('/tenant/inventory/disposalCheck', item, { params: { qty, remark } }),
  inventoryDisposalExec: (item, qty, remark) => http.post('/tenant/inventory/disposalExec', item, { params: { qty, remark } }),
  inventoryInternalSale: (item, qty, targetWarehouseId) => http.post('/tenant/inventory/internalSale', item, { params: { qty, targetWarehouseId } }),
  // 存货报废（INV_DISPOSE）/ 盘点差异（INV_PHYSICAL_CHECK）/ 跌价准备（INV_DEVAL）：P1 补齐入口
  inventoryDisposeScrap: (item, qty) => http.post('/tenant/inventory/disposeScrap', item, { params: { qty } }),
  inventoryPhysicalCheck: (item, diffQty, remark) => http.post('/tenant/inventory/physicalCheck', item, { params: { checkedQty: diffQty, remark } }),
  inventoryDevalue: (item, devalueAmount) => http.post('/tenant/inventory/devalue', item, { params: { provisionAmount: devalueAmount } }),
}

// ---------------- FA_DEPRECIATION 折旧计提批单（月度计提两阶段：批单+审批） ----------------
export const deprBatchApi = {
  create: (period) => http.post('/tenant/asset/depr-batch/create', null, { params: { period } }),
  submit: (period) => http.post('/tenant/asset/depr-batch/submit', null, { params: { period } }),
  submitById: (id) => http.post(`/tenant/asset/depr-batch/${id}/submit`),
  execute: (id) => http.post(`/tenant/asset/depr-batch/${id}/execute`),
  page: (p) => http.get('/tenant/asset/depr-batch/page', { params: p }),
  detail: (id) => http.get(`/tenant/asset/depr-batch/${id}`),
}

// ---------------- P0-8 TR 资金管理：资金计划 / 收款(渠道流水) / 资金调拨 / 票据结算 ----------------
export const treasuryApi = {
  fundPlanPage: (p) => http.get('/tenant/treasury/fund-plan/page', { params: p }),
  fundPlanCreate: (b) => http.post('/tenant/treasury/fund-plan/create', b),
  fundPlanSelect: (id) => http.post(`/tenant/treasury/fund-plan/${id}/select`),
  /** 批次1：驳回资金计划编辑重提 REJECTED → DRAFT 并重发 TR_PLAN */
  fundPlanResubmit: (id) => http.post(`/tenant/treasury/fund-plan/${id}/resubmit`),
  fundPlanCombine: (id, incomeActual, outcomeActual) =>
    http.post(`/tenant/treasury/fund-plan/${id}/combine-actual`, null, { params: { incomeActual, outcomeActual } }),
  // T1：资金计划调整（TR_PLAN_ADJUST）
  fundPlanAdjust: (id, incomeAdjust, outcomeAdjust) =>
    http.post(`/tenant/treasury/fund-plan/${id}/adjust`, null, { params: { incomeAdjust, outcomeAdjust } }),
  creditBillPage: (p) => http.get('/tenant/treasury/credit-bill/page', { params: p }),
  creditBillReceive: (b) => http.post('/tenant/treasury/credit-bill/receive', b),
  creditBillAutoRecon: () => http.post('/tenant/treasury/credit-bill/auto-recon'),
  creditBillConfirmRecon: (id) => http.post(`/tenant/treasury/credit-bill/${id}/confirm-recon`),
  transferPage: (p) => http.get('/tenant/treasury/transfer/page', { params: p }),
  transferCreate: (b) => http.post('/tenant/treasury/transfer/create', b),
  /** 批次1：提交/重提调拨审批（驳回保持 DRAFT，重提起新实例）；flow=TR_UPLOAD 重提上存单 */
  transferSubmit: (id, flow) => http.post(`/tenant/treasury/transfer/${id}/submit`, null, { params: { flow: flow || 'TR_TRANSFER' } }),
  // T1：资金上存（TR_UPLOAD）
  transferUpload: (b) => http.post('/tenant/treasury/transfer/upload', b),
  transferApprove: (id) => http.post(`/tenant/treasury/transfer/${id}/approve`),
  transferExecute: (id) => http.post(`/tenant/treasury/transfer/${id}/execute`),
  billSettlePage: (p) => http.get('/tenant/treasury/bill-settle/page', { params: p }),
  billSettleCreate: (b) => http.post('/tenant/treasury/bill-settle/create', b),
  billSettleEndorse: (id) => http.post(`/tenant/treasury/bill-settle/${id}/endorse`),
  billSettleDiscount: (id) => http.post(`/tenant/treasury/bill-settle/${id}/discount`),
  billSettleCollection: (id) => http.post(`/tenant/treasury/bill-settle/${id}/collection`),
  // P1-4：资金头寸视图（账户余额 + 待付 + 应收未收 + 当期计划 → 净头寸）
  position: (params) => http.get('/tenant/treasury/position', { params }),
}

// ---------------- P3 FM 档案管理：调阅申请 / 销毁申请 ----------------
export const archiveAccessApi = {
  request: (b) => http.post('/tenant/compliance/archive-access/request', b),
  approve: (id, approverId, approved) => http.post(`/tenant/compliance/archive-access/${id}/approve`, { approverId, approved }),
  grant: (id) => http.post(`/tenant/compliance/archive-access/${id}/grant`),
  byArchive: (archiveId) => http.get(`/tenant/compliance/archive-access/by-archive/${archiveId}`),
  myRequests: (requesterId) => http.get('/tenant/compliance/archive-access/my-requests', { params: { requesterId } }),
  pending: () => http.get('/tenant/compliance/archive-access/pending'),
  pendingPage: (params) => http.get('/tenant/compliance/archive-access/pending/page', { params }),
  detail: (id) => http.get(`/tenant/compliance/archive-access/${id}`),
  remove: (id) => http.delete(`/tenant/compliance/archive-access/${id}`),
}
export const archiveDestroyApi = {
  // R5-D-200：销毁申请＝起流程类长耗时写端点（实测 20s 断连时服务端已建 wf_instance），端点级放宽超时
  create: (archiveIds, reason, applicantId) => http.post('/tenant/compliance/archive-destroy/create', { archiveIds, reason, applicantId }, { ...longWrite }),
  approve: (id, approverId, approved) => http.post(`/tenant/compliance/archive-destroy/${id}/approve`, { approverId, approved }),
  // R5-D-200：销毁执行是重事务长写，同族放宽（超时按「已提交，处理中」规则）
  execute: (id) => http.post(`/tenant/compliance/archive-destroy/${id}/execute`, null, { ...longWrite }),
  list: (approvalStatus) => http.get('/tenant/compliance/archive-destroy/list', { params: { approvalStatus } }),
  listPage: (params) => http.get('/tenant/compliance/archive-destroy/list/page', { params }),
  detail: (id) => http.get(`/tenant/compliance/archive-destroy/${id}`),
}

// ---------------- 平台运营端（/api/platform/**，仅 subjectType=PLATFORM 可访问） ----------------
// 路径与出入参逐一对照后端 Controller 真实签名：
//  PlatformDashboardController / PlatformTenantController / PlatformPackageController
//  PlatformTenantPackageController / TenantExportController
export const platformApi = {
  /** 平台健康看板 GET /api/platform/dashboard */
  dashboard: () => http.get('/platform/dashboard'),
  /** 租户分页 GET /api/platform/tenant/page?keyword=&status= */
  tenantPage: (params) => http.get('/platform/tenant/page', { params }),
  /** 审核通过入驻申请 POST /api/platform/tenant/approve/{id} */
  tenantApprove: (id) => http.post(`/platform/tenant/approve/${id}`),
  /** 拒绝入驻 POST /api/platform/tenant/reject/{id}  body:{reason} */
  tenantReject: (id, reason) => http.post(`/platform/tenant/reject/${id}`, { reason }),
  /** 冻结租户 POST /api/platform/tenant/freeze/{id}  body:{reason} */
  tenantFreeze: (id, reason) => http.post(`/platform/tenant/freeze/${id}`, { reason }),
  /** 解冻租户 POST /api/platform/tenant/unfreeze/{id} */
  tenantUnfreeze: (id) => http.post(`/platform/tenant/unfreeze/${id}`),
  /** 套餐列表（status=1 启用） GET /api/platform/package/list */
  packageList: () => http.get('/platform/package/list'),
  /** 套餐功能开关矩阵 GET /api/platform/tenant-package/features?packageId= */
  packageFeatures: (packageId) => http.get('/platform/tenant-package/features', { params: { packageId } }),
  /** 保存套餐功能开关 POST /api/platform/tenant-package/set-features  body:{tenantId,packageId,features:[{code,enabled:0|1}]} */
  setFeatures: (body) => http.post('/platform/tenant-package/set-features', body),
  /** 租户数据导出 ZIP（Blob 下载，仿 Dashboard.vue exportXlsx，走 fetch 带 Bearer） */
  exportUrl: (tenantId) => `/api/platform/tenant/${tenantId}/export`,
}

// ---------------- 启动自愈：会话校验 + 后端连通性探测 ----------------
/**
 * 连通性探测：未登录时命中 /auth/profile 会拿到 401 —— 401 恰恰是"后端在线"的证据。
 * 后端不可达（无响应/502/503）由响应拦截器统一置 backend.online=false。
 * @returns {Promise<boolean>} 后端是否在线
 */
export async function probeBackend() {
  if (store.token) {
    // 已有 token：走完整会话校验（顺带把 401 的过期 token 清掉）
    await verifySession()
    return backend.online
  }
  suppressKick = true
  try {
    // silent：探活失败由全局横幅提示，不弹 toast（App.vue 断线期间每 10s 重探一次）
    await http.post('/auth/profile', null, { timeout: 5000, silent: true })
    setBackend(true)
  } catch (e) {
    const m = String(e?.message || '')
    if (/401|未登录|过期/.test(m)) setBackend(true)
  } finally {
    suppressKick = false
  }
  return backend.online
}

/**
 * 校验本地 token 是否仍然有效。
 * 401/过期 → 清 token（随后 router 守卫自然把用户送去 /login）
 * 网络不可达 → 保留 token（避免误伤），仅标记后端离线
 * @returns {Promise<boolean>} 会话是否有效
 */
export async function verifySession() {
  if (!store.token) return false
  try {
    // silent：会话校验是启动自愈逻辑，失败走 router 守卫/横幅，不弹 toast
    const p = await http.post('/auth/profile', null, { timeout: 5000, silent: true })
    // /auth/profile 返回 ProfileVo{ user: SysUser, perms, roles, features }（嵌套结构），
    // 旧逻辑读 p.id/p.userId 恒为 undefined，导致 store.user 永远缺失 id/userName 等字段。
    // 正确做法：把内层 user（SysUser 全字段）展开合并，并补顶层 perms/roles/features。
    if (p && typeof p === 'object') {
      const base = store.user || {}
      const detail = (p.user && typeof p.user === 'object') ? p.user : {}
      store.user = {
        ...base,
        ...detail,
        userId: detail.id ?? base.userId ?? detail.userId,
        username: detail.userName ?? base.username ?? detail.username,
        realName: detail.nickName ?? base.realName ?? detail.realName,
        perms: p.perms || base.perms || detail.perms,
        features: p.features || base.features || detail.features,
        roles: p.roles || base.roles || detail.roles,
        loginAt: detail.loginAt ?? base.loginAt,
      }
    }
    setBackend(true)
    return true
  } catch (e) {
    const m = String(e?.message || '')
    if (/401|未登录|过期/.test(m)) {
      store.logout()
      return false
    }
    return true // 后端不可达：保留会话，交由全局横幅提示
  }
}

/** 应用挂载前调用：先探活 + 校验会话，避免残留 token 让用户直接跳进 /dashboard */
export async function bootstrapSession() {
  if (store.token) return verifySession()
  return probeBackend()
}

// ---------------- 应收应付蓝图落地（apflow/arflow，任务2/3） ----------------
export const apFlowApi = {
  page: (p) => http.get('/tenant/ap-request/page', { params: p }),
  get: (id) => http.get(`/tenant/ap-request/${id}`),
  create: (b) => http.post('/tenant/ap-request/create', b),
  repull: (id, b) => http.post(`/tenant/ap-request/${id}/repull`, b),
  dispatch: (id) => http.post(`/tenant/ap-request/${id}/dispatch`),
  claim: (id, user) => http.post(`/tenant/ap-request/${id}/claim`, { user }),
  assign: (id, user) => http.post(`/tenant/ap-request/${id}/assign`, { user }),
  confirm: (id, b) => http.post(`/tenant/ap-request/${id}/confirm`, b),
  groupReview: (id, b) => http.post(`/tenant/ap-request/${id}/group-review`, b),
  post: (id) => http.post(`/tenant/ap-request/${id}/post`),
  pay: (id) => http.post(`/tenant/ap-request/${id}/pay`),
  void: (id, reason) => http.post(`/tenant/ap-request/${id}/void`, { reason }),
  close: (id) => http.post(`/tenant/ap-request/${id}/close`),
  rules: () => http.get('/tenant/ap-request/dispatch-rules'),
  saveRule: (b) => http.post('/tenant/ap-request/dispatch-rules', b),
  importPlan: (formData) => http.post('/tenant/ap-request/import-plan', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  monthCloseList: () => http.get('/tenant/ap-month-close/list'),
  monthCloseCreate: (b) => http.post('/tenant/ap-month-close/create', b),
  monthCloseDispatch: (id, members) => http.post(`/tenant/ap-month-close/${id}/dispatch`, { members }),
  monthCloseItem: (id, b) => http.post(`/tenant/ap-month-close/${id}/complete-item`, b),
  monthCloseClose: (id) => http.post(`/tenant/ap-month-close/${id}/close-period`),
  // P0-2 反关账（重开期间，原因必填记录）；P1-6 关账前置自动检查
  monthCloseReopen: (id, reason) => http.post(`/tenant/ap-month-close/${id}/reopen`, { reason }),
  monthCloseAutoCheck: (id) => http.post(`/tenant/ap-month-close/${id}/auto-check`),
  // R5-D-218 作废（最终状态 VOID，原因必填记录；解冻协同、行止不删）
  monthCloseVoid: (id, reason) => http.post(`/tenant/ap-month-close/${id}/void`, { reason }),
}

// ---------------- 退货管理（采购退货单/销售退货单，BCP勾选式对账一期退货数据环节） ----------------
export const returnApi = {
  // 采购退货单
  purPage: (p) => http.get('/tenant/pur-returns', { params: p }),
  purGet: (id) => http.get(`/tenant/pur-returns/${id}`),
  purCreate: (b) => http.post('/tenant/pur-returns', b),
  purUpdate: (id, b) => http.put(`/tenant/pur-returns/${id}`, b),
  purSubmit: (id) => http.post(`/tenant/pur-returns/${id}/submit`),
  purApprove: (id) => http.post(`/tenant/pur-returns/${id}/approve`),
  purReject: (id, reason) => http.post(`/tenant/pur-returns/${id}/reject`, { reason }),
  purVoid: (id, reason) => http.post(`/tenant/pur-returns/${id}/void`, { reason }),
  purDelete: (id) => http.delete(`/tenant/pur-returns/${id}`),
  purExport: (p) => http.get('/tenant/pur-returns/export', { params: p }),
  // 销售退货单
  salePage: (p) => http.get('/tenant/sale-returns', { params: p }),
  saleGet: (id) => http.get(`/tenant/sale-returns/${id}`),
  saleCreate: (b) => http.post('/tenant/sale-returns', b),
  saleUpdate: (id, b) => http.put(`/tenant/sale-returns/${id}`, b),
  saleSubmit: (id) => http.post(`/tenant/sale-returns/${id}/submit`),
  saleApprove: (id) => http.post(`/tenant/sale-returns/${id}/approve`),
  saleReject: (id, reason) => http.post(`/tenant/sale-returns/${id}/reject`, { reason }),
  saleVoid: (id, reason) => http.post(`/tenant/sale-returns/${id}/void`, { reason }),
  saleDelete: (id) => http.delete(`/tenant/sale-returns/${id}`),
  saleExport: (p) => http.get('/tenant/sale-returns/export', { params: p }),
}

export const arFlowApi = {
  statements: (p) => http.get('/tenant/ar-flow/statements', { params: p }),
  importStatement: (b) => http.post('/tenant/ar-flow/statements', b),
  importStatementExcel: (formData) => http.post('/tenant/ar-flow/statements/import-excel', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  checkStatement: (id) => http.post(`/tenant/ar-flow/statements/${id}/check`),
  adjustStatement: (id, note) => http.post(`/tenant/ar-flow/statements/${id}/adjust`, { note }),
  preInvoices: (p) => http.get('/tenant/ar-flow/pre-invoices', { params: p }),
  generate: (reconBillNo) => http.post('/tenant/ar-flow/pre-invoices/generate', null, { params: { reconBillNo } }),
  bizConfirm: (id, confirmer) => http.post(`/tenant/ar-flow/pre-invoices/${id}/biz-confirm`, { confirmer }),
  bizReject: (id, reason) => http.post(`/tenant/ar-flow/pre-invoices/${id}/biz-reject`, { reason }),
  pushTax: (id) => http.post(`/tenant/ar-flow/pre-invoices/${id}/push-tax`),
  writeback: (id, b) => http.post(`/tenant/ar-flow/pre-invoices/${id}/writeback`, b),
  print: (id) => http.post(`/tenant/ar-flow/pre-invoices/${id}/print`),
  void: (id, reason) => http.post(`/tenant/ar-flow/pre-invoices/${id}/void`, { reason }),
  red: (id, redNoticeNo) => http.post(`/tenant/ar-flow/pre-invoices/${id}/red`, { redNoticeNo }),
  exchange: (id, reason) => http.post(`/tenant/ar-flow/pre-invoices/${id}/exchange`, { reason }),
  preCheck: (id, b) => http.post(`/tenant/ar-flow/pre-invoices/${id}/pre-check`, b),
  claims: (p) => http.get('/tenant/ar-flow/claims', { params: p }),
  createClaim: (b) => http.post('/tenant/ar-flow/claims', b),
  claimApprove: (id) => http.post(`/tenant/ar-flow/claims/${id}/ekp-approve`),
  claimAssign: (id, user) => http.post(`/tenant/ar-flow/claims/${id}/assign`, { user }),
  claimReview: (id) => http.post(`/tenant/ar-flow/claims/${id}/review`),
  claimAccounts: (id, b) => http.post(`/tenant/ar-flow/claims/${id}/accounts`, b),
  claimReject: (id, reason) => http.post(`/tenant/ar-flow/claims/${id}/reject`, { reason }),
  claimReverse: (id) => http.post(`/tenant/ar-flow/claims/${id}/reverse-est`),
  claimComplete: (id) => http.post(`/tenant/ar-flow/claims/${id}/complete`),
  mailPackages: (p) => http.get('/tenant/ar-flow/mail-packages', { params: p }),
  createMail: (b, invoiceNos) => http.post('/tenant/ar-flow/mail-packages', b, { params: { invoiceNos } }),
  sendMail: (id, b) => http.post(`/tenant/ar-flow/mail-packages/${id}/send`, b),
  notifyMail: (id) => http.post(`/tenant/ar-flow/mail-packages/${id}/notify`),
  signMail: (id) => http.post(`/tenant/ar-flow/mail-packages/${id}/sign`),
  archiveMail: (id) => http.post(`/tenant/ar-flow/mail-packages/${id}/archive`),
  mailRecipients: (p) => http.get('/tenant/ar-flow/mail-recipients', { params: p }),
  createMailRecipient: (b) => http.post('/tenant/ar-flow/mail-recipients', b),
  updateMailRecipient: (b) => http.post('/tenant/ar-flow/mail-recipients/update', b),
  deleteMailRecipient: (id) => http.post(`/tenant/ar-flow/mail-recipients/${id}/delete`),
  mailNotifyLog: (p) => http.get('/tenant/ar-flow/mail-notify-log', { params: p }),
  subjects: (p) => http.get('/tenant/ar-flow/subjects', { params: p }),
  invoiceRules: () => http.get('/tenant/ar-flow/invoice-rules'),
  saveRule: (b) => http.post('/tenant/ar-flow/invoice-rules', b),
  importReceiptExcel: (formData) => http.post('/tenant/ar-flow/receipts/import-excel', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
}

// ---------------- 应收应付蓝图缺口落地（票据登记/核销工作台/AR月结/统计报表/基础配置） ----------------
export const arGapApi = {
  // 票据登记（蓝图9.5：电子/纸质+影像扫描、贴现、托收）
  notes: (p) => http.get('/tenant/ar-flow/notes', { params: p }),
  dueNotes: () => http.get('/tenant/ar-flow/notes/due-list'),
  registerNote: (b) => http.post('/tenant/ar-flow/notes', b),
  noteReview: (id) => http.post(`/tenant/ar-flow/notes/${id}/review`),
  noteHandover: (id, keeper) => http.post(`/tenant/ar-flow/notes/${id}/handover`, { keeper }),
  noteEndorse: (id, transferee) => http.post(`/tenant/ar-flow/notes/${id}/endorse`, { transferee }),
  noteDiscount: (id, b) => http.post(`/tenant/ar-flow/notes/${id}/discount`, b),
  noteMarkDue: (id) => http.post(`/tenant/ar-flow/notes/${id}/mark-due`),
  noteCollect: (id) => http.post(`/tenant/ar-flow/notes/${id}/collect`),
  noteVoid: (id, reason) => http.post(`/tenant/ar-flow/notes/${id}/void`, { reason }),
  // 核销工作台（蓝图9.4：资金池/待核销发票/手动匹配/自动核销/对冲/核销查询）
  funds: (p) => http.get('/tenant/ar-flow/writeoff/funds', { params: p }),
  registerFund: (b) => http.post('/tenant/ar-flow/writeoff/funds', b),
  pendingInvoices: (p) => http.get('/tenant/ar-flow/writeoff/pending-invoices', { params: p }),
  autoMatch: (fundId) => http.post('/tenant/ar-flow/writeoff/auto-match', { fundId }),
  manualMatch: (fundId, lines) => http.post('/tenant/ar-flow/writeoff/manual-match', { fundId, lines }),
  offsetWriteoff: (b) => http.post('/tenant/ar-flow/writeoff/offset', b),
  writeoffRecords: (p) => http.get('/tenant/ar-flow/writeoff/records', { params: p }),
  // 应收月结（应收月结/成本月结六步）
  monthCloseList: (p) => http.get('/tenant/ar-flow/month-close/list', { params: p }),
  monthCloseCreate: (b) => http.post('/tenant/ar-flow/month-close/create', b),
  monthCloseClearInterface: (id) => http.post(`/tenant/ar-flow/month-close/${id}/clear-interface`),
  monthClosePullData: (id) => http.post(`/tenant/ar-flow/month-close/${id}/pull-data`),
  monthCloseAdjust: (id, note) => http.post(`/tenant/ar-flow/month-close/${id}/adjust`, { note }),
  monthClosePostGl: (id) => http.post(`/tenant/ar-flow/month-close/${id}/post-gl`),
  monthCloseReconcile: (id) => http.post(`/tenant/ar-flow/month-close/${id}/reconcile`),
  monthCloseClose: (id) => http.post(`/tenant/ar-flow/month-close/${id}/close-period`),
  // P0-2 同源整改：反月结（重开账期，原因必填记录）
  monthCloseReopen: (id, reason) => http.post(`/tenant/ar-flow/month-close/${id}/reopen`, { reason }),
  // R5-D-218 作废（最终状态 VOID，原因必填记录；未关闭账期可作废）
  monthCloseVoid: (id, reason) => http.post(`/tenant/ar-flow/month-close/${id}/void`, { reason }),
  // 统计报表（开票/核销/票据/坏账/月结 5 张）
  reportInvoicing: (p) => http.get('/tenant/ar-flow/reports/invoicing', { params: p }),
  reportWriteoff: (p) => http.get('/tenant/ar-flow/reports/writeoff', { params: p }),
  reportNote: () => http.get('/tenant/ar-flow/reports/note'),
  reportBadDebt: (p) => http.get('/tenant/ar-flow/reports/bad-debt', { params: p }),
  reportMonthClose: (p) => http.get('/tenant/ar-flow/reports/month-close', { params: p }),
  // AR派工规则（蓝图5.2）
  dispatchRules: () => http.get('/tenant/ar-flow/dispatch-rules'),
  saveDispatchRule: (b) => http.post('/tenant/ar-flow/dispatch-rules', b),
  deleteDispatchRule: (id) => http.post(`/tenant/ar-flow/dispatch-rules/delete`, null, { params: { id } }),
  // AR接口2/4
  pushEbsAdjust: (b) => http.post('/tenant/ar-flow/ebs-adjustments', b),
  ebsAdjusts: (p) => http.get('/tenant/ar-flow/ebs-adjustments', { params: p }),
  pushRedInvoice: (b) => http.post('/tenant/ar-flow/red-invoices/push', b),
  importOffsetExcel: (formData) => http.post('/tenant/ar-flow/claims/import-offset-excel', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
}

// ---------------- P3 SCP/CCP 协同平台打通（蓝图打通点1~7 + AR接口1/2/3） ----------------
export const collabApi = {
  scpBills: (p) => http.get('/tenant/collab/scp/bills', { params: p }),
  scpRecons: (p) => http.get('/tenant/collab/scp/recons', { params: p }),
  scpImportGoods: (b) => http.post('/tenant/collab/scp/recons', b),
  scpObjection: (id, note) => http.post(`/tenant/collab/scp/recons/${id}/objection`, { note }),
  scpResolveObjection: (id, note) => http.post(`/tenant/collab/scp/recons/${id}/resolve-objection`, { note }),
  scpConfirmRecon: (id, b) => http.post(`/tenant/collab/scp/recons/${id}/confirm`, b),
  scpVoidRecon: (id, reason) => http.post(`/tenant/collab/scp/recons/${id}/void`, { reason }),
  scpApplyInvoice: (id, b) => http.post(`/tenant/collab/scp/recons/${id}/apply-invoice`, b || {}),
  scpManualMatch: (id, b) => http.post(`/tenant/collab/scp/bills/${id}/manual-match`, b),
  scpForceApply: (id, reason) => http.post(`/tenant/collab/scp/bills/${id}/force-apply`, { reason }),
  scpForceApprove: (id) => http.post(`/tenant/collab/scp/bills/${id}/force-approve`),
  scpForceReject: (id, reason) => http.post(`/tenant/collab/scp/bills/${id}/force-reject`, { reason }),
  scpPreCertify: (id) => http.post(`/tenant/collab/scp/bills/${id}/pre-certify`),
  scpCreate: (b) => http.post('/tenant/collab/scp/bills', b),
  scpMatch: (id) => http.post(`/tenant/collab/scp/bills/${id}/match`),
  scpCancelMatch: (id) => http.post(`/tenant/collab/scp/bills/${id}/cancel-match`),
  scpSend: (id, b) => http.post(`/tenant/collab/scp/bills/${id}/send`, b),
  scpSign: (id) => http.post(`/tenant/collab/scp/bills/${id}/sign`),
  scpScan: (id, b) => http.post(`/tenant/collab/scp/bills/${id}/scan`, b || {}),
  scpRematch: (id) => http.post(`/tenant/collab/scp/bills/${id}/rematch`),
  scpPost: (id) => http.post(`/tenant/collab/scp/bills/${id}/post`),
  scpCertify: (id, result) => http.post(`/tenant/collab/scp/bills/${id}/certify`, { result }),
  scpPayWriteback: (id, b) => http.post(`/tenant/collab/scp/bills/${id}/pay-writeback`, b),
  shippings: (p) => http.get('/tenant/collab/ccp/shippings', { params: p }),
  pushShipping: (b) => http.post('/tenant/collab/ccp/shippings', b),
  reconSync: (statementNo) => http.post('/tenant/collab/ccp/recon-sync', null, { params: { statementNo } }),
  invoiceWriteback: (preInvoiceId) => http.post(`/tenant/collab/ccp/invoice-writeback/${preInvoiceId}`),
  freezeStatus: (p) => http.get('/tenant/collab/freeze-status', { params: p }),
  syncLogs: (p) => http.get('/tenant/collab/sync-logs', { params: p }),
}

// ---------------- BCP 门户行级异议审核（/api/tenant/bcp-objection/**） ----------------
// 供应商（AP）/ 客户（AR）在 BCP 门户对账明细行提起的数量、单价异议；FSSC 侧 AP/AR 岗
// 经本组端点拉取待审清单并回写结论。后端再以 HMAC-SHA256 签名调用 BCP 门户的开放通道
// （/api/open/linku/**），因此本组端点的可用性取决于两侧共享密钥是否同值。
// 读 = bcp:objection:list，写 = bcp:objection:review。
export const bcpObjectionApi = {
  pending: (params) => http.get('/tenant/bcp-objection/pending', { params }),
  review: (id, approve, remark) => http.post(`/tenant/bcp-objection/${id}/review`, { approve, remark }),
}

// ---------------- D-DEC-320 流程收尾动作配置面（wf_event_action_config） ----------------
// 表原本只有引擎读环节、无任何端点与页面；这里补齐只读清单 + 字段级编辑（启用状态 / 配置说明）。
// 读 = wf:config:list，写 = wf:config:update（沿用 wf 域既有码，不发明新码）。
export const wfEventActionApi = {
  page: (params) => http.get('/tenant/wf/event-action-config/page', { params }),
  detail: (id) => http.get(`/tenant/wf/event-action-config/detail/${id}`),
  options: (cfg) => http.get('/tenant/wf/event-action-config/options', { ...(cfg || {}) }),
  runtimeSnapshot: (cfg) => http.get('/tenant/wf/event-action-config/runtime-snapshot', { ...(cfg || {}) }),
  update: (body) => http.post('/tenant/wf/event-action-config/update', body),
}
