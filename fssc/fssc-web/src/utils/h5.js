// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 移动报销端（H5）专用公共件 —— 只服务 /h5 路由族，PC 端不引用，避免把移动端收窄策略外溢。
 *
 * 落这里的都是"三页共用 + 需要注释清楚判据来源"的东西；页面内只留业务逻辑。
 */

// 单据类型中文名走全局枚举表（constants/enums.js 明确「禁止各页重复定义」），这里不再另写一份。
import { BILL_TYPE } from '@/constants/enums.js'

/** 金额显示：千分位 + 两位小数，缺值给 0.00（移动端列表与合计栏共用，禁止各页再写一份 toFixed） */
export function money(v) {
  const n = Number(v || 0)
  return (isNaN(n) ? 0 : n).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/**
 * 后端 Date 序列化兼容解析。
 * 实测（10-03 用 Y00FINAR02 只读拉 /wf/todo/page）待办行的 createTime 是
 * **Java Date.toString() 串**：`"Sat Oct 03 15:23:10 CST 2026"`；
 * 而业务表接口（/budget/item/pick 等）是 ISO 串 `"2026-09-29T11:14:07"`。两套都要能吃。
 *
 *  不能用 new Date(str) 默认处理：V8 把 "CST" 认成**美国中部标准时间（UTC−6）**，
 * 实测 `new Date("Sat Oct 03 15:23:10 CST 2026")` 解出 10-04 05:23（本地 +08:00），
 * 差 14 小时 ⇒ 停留时长会算成负数。这里按「钟点即本机时区」直接取年月日时分秒，
 * 前提是服务端与浏览器同时区（本演示环境＝Asia/Shanghai，成立）；跨时区部署时该规则要重定。
 */
const MONTHS = { Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5, Jul: 6, Aug: 7, Sep: 8, Oct: 9, Nov: 10, Dec: 11 }
export function parseServerTime(s) {
  if (s == null || s === '') return null
  if (typeof s === 'number') return new Date(s)
  const str = String(s).trim()
  const java = /^[A-Z][a-z]{2} ([A-Z][a-z]{2}) (\d{2}) (\d{2}):(\d{2}):(\d{2}) \S+ (\d{4})$/.exec(str)
  if (java) {
    const mo = MONTHS[java[1]]
    if (mo === undefined) return null
    return new Date(Number(java[6]), mo, Number(java[2]), Number(java[3]), Number(java[4]), Number(java[5]))
  }
  const iso = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(str)
  if (iso) {
    if (/[Z+]$/.test(str) || /[+-]\d{2}:?\d{2}$/.test(str)) {
      const d = new Date(str)
      return isNaN(d) ? null : d
    }
    return new Date(Number(iso[1]), Number(iso[2]) - 1, Number(iso[3]), Number(iso[4]), Number(iso[5]), Number(iso[6] || 0))
  }
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(str)
  if (day) return new Date(Number(day[1]), Number(day[2]) - 1, Number(day[3]))
  const d = new Date(str)
  return isNaN(d) ? null : d
}

/**
 * 待办停留时长（§4.2-①：超 24h 标橙、超 72h 标红；沿用既有超时治理规则，不新造阈值）。
 * 解不出时间 ⇒ 返回 null，调用方**不显示**该字段，绝不显示"0 小时"或假数。
 */
export function stayOf(createTime, now = Date.now()) {
  const d = parseServerTime(createTime)
  if (!d) return null
  const hours = (now - d.getTime()) / 3600000
  if (hours < 0 || !isFinite(hours)) return null
  const text = hours < 1 ? '刚刚' : hours < 24 ? `${Math.floor(hours)} 小时前` : `${Math.floor(hours / 24)} 天前`
  return { hours, text, cls: hours >= 72 ? 'h5-stay--over' : hours >= 24 ? 'h5-stay--late' : '' }
}

/** 把日期显示成 YYYY-MM-DD（本地钟口，不用 toISOString，避免东八区 0~8 点差一天） */
export function dayText(v) {
  const d = parseServerTime(v)
  if (!d) return String(v || '—')
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/**
 * 「权限不足」与「真的没数据」分立（AD-13 / UX-05；§5 三态行）。
 * 本窗用提报岗 Y000147 实测：/invoice/page、/invoice/enhance/verify/page、
 * /wf/query/history/page 三条都回 code:4030，前端此前一律渲染成「暂无 X」⇒ 假空。
 * 判据优先认 code（api.js 已把业务码挂到 Error.code），文案默认处理认关键字，
 * 因为老封装可能只透出 msg。
 */
export function permDeniedOf(e) {
  const code = e?.code
  const msg = String(e?.msg || e?.message || '')
  const isPerm = code === 4030 || /无该功能|缺少权限|无权限|没有权限/.test(msg)
  if (!isPerm) return null
  const key = (/缺少权限[:：]\s*([\w:.\-]+)/.exec(msg) || [])[1] || ''
  return { msg: msg || '当前账号无该功能权限', permKey: key }
}

/**
 * bizType 是否以 `exp_bill` 为单据载体（决定待办能否进 /h5/approval/:billId）。
 * 真值来源＝后端 BillRouteRegistry.EXP_BILL_FILLED（BillRouteRegistry.java:357-359，
 * 流程变量带/不带 EXP_ 前缀两种写法都收，见 WfTaskController:504-512）；
 * 派单类 INVOICE_MATCH/PTP03 的 bizId 同样是 exp_bill.id（BillRouteRegistry:361-368），能看单。
 *
 * 为什么前端要这道判据：/wf/todo 的 billId 是**按 bizType 回到各自的表**取的主键
 * （实测 AR_RECON 待办的 billId=7 指向 ar_recon_record，不是 exp_bill）。
 * 改造前 H5 对所有待办一律跳 #/h5/approval/{billId} ⇒ 拿 AR_RECON 的 7 去查 exp_bill 7，
 * 是**串到别人的单**上审批，比"看不了"更严重。非 exp_bill 载体一律明示去电脑端。
 */
const EXP_BILL_CARRIERS = ['CLAIM', 'CLAIM_TRAVEL', 'LOAN', 'REPAY', 'PAY', 'TRAVEL', 'TRAIN', 'PREPAY', 'SETTLE', 'APPLY', 'INVOICE_MATCH', 'INVOICE_MATCH_MANUAL', 'PTP03']
export function isExpBillCarrier(bizType) {
  const t = String(bizType || '').replace(/^EXP_/, '')
  return EXP_BILL_CARRIERS.includes(t)
}

/** 型＋子型 → 清单键（`CLAIM|`、`CLAIM|TRAVEL`……）；与 exp_mobile_template 的 bill_type/bill_sub_type 同码 */
export function docTypeKey(billType, billSubType) {
  return `${billType || ''}|${billSubType || ''}`
}

/**
 * 移动端提报「启用清单」的**默认处理常量**（§4.2-② 的乙案前端半边；零新表、零重启、零授权）。
 * 判据＝"该类型在移动端能交出一张不缺件的单"：
 *  - CLAIM 的表头/明细/附件/发票四环节在 H5 已齐；
 *  - CLAIM_TRAVEL 缺差旅明细段（exp_travel）、APPLY 缺事由与关联预算环节、LOAN 缺冲销与还款计划规则
 *    ⇒ 交了就是脏单；PAY/REPAY 的收款方与付款通道、还款计划段也不在移动端 ⇒ 同样未启用。
 * 未启用类型明示列在灰区并给"去电脑端"的出口，而不是从下拉里静默消失（看不见 ≠ 不存在）。
 *
 *  10-04 P2-3 甲案：这份清单的**权威来源已改成库里表 `exp_mobile_template`**
 * （接口 `/api/tenant/expense/mobile/templates`，见 api.js#mobileTemplates）。本常量只在
 * 「接口未上线（404）／读失败／返回空清单」时默认处理——所以它的取值域与排列规则必须和 seed
 * 逐条对得上（六档、只有 CLAIM 主型启用）：配置接口一上线界面就变样＝又一个假功能。
 * 中文名不在这里写第二套：取 constants/enums.js#BILL_TYPE（全仓禁止重复定义枚举标签）；
 * pcPath 与 constants/billStatus.js#editBillPath（:205-211）同一张表，不另造路由。
 */
const H5_DOC_TYPE_KEYS = [
  ['CLAIM', ''],
  ['CLAIM', 'TRAVEL'],
  ['APPLY', ''],
  ['LOAN', ''],
  ['PAY', ''],
  ['REPAY', ''],
]
const H5_PC_PATH = { CLAIM: '/expense/claim', APPLY: '/expense/apply', LOAN: '/expense/loan', REPAY: '/expense/repay', PAY: '/corp-pay/settle' }

export const H5_DOC_TYPES = H5_DOC_TYPE_KEYS.map(([billType, billSubType], i) => ({
  key: docTypeKey(billType, billSubType),
  value: billType,
  subType: billSubType,
  label: (billSubType && BILL_TYPE[billSubType]) || BILL_TYPE[billType] || billType,
  mobile: billType === 'CLAIM' && !billSubType,
  capable: billType === 'CLAIM' && !billSubType,
  serverEnabled: billType === 'CLAIM' && !billSubType,
  pcPath: billType === 'CLAIM' && billSubType === 'TRAVEL' ? '/expense/claim-travel' : H5_PC_PATH[billType],
  // 移动端表单落位：只有费用报销这一张表单（H5ExpenseCreate）；没有它的类型一律不进可选区。
  h5Path: billType === 'CLAIM' && !billSubType ? '/h5/expense/new' : '',
  sort: i + 1,
}))

/**
 * H5 侧真正「有表单可填」的类型（清单被启用 ≠ 移动端做得完）。
 * 目前只有费用报销一张表单（H5ExpenseCreate 在 :876 显式拒绝非 CLAIM 单）。
 * 库里把别的型改成启用，前端只会把它挪进明示区并写明「移动端表单未覆盖」，
 * 不会凭空生成一个点进去填不全的按钮。
 */
export const H5_FORM_CAPABLE_KEYS = [docTypeKey('CLAIM', '')]

/**
 * 把 `/expense/mobile/templates` 的返回规整成与 H5_DOC_TYPES 同形的数组；不可用一律返回空数组，由调用方回落常量。
 * 三条硬规矩：
 *  ①库里冒出常量没有的型/子型 ⇒ 整行丢弃——不给未知类型渲染按钮，也不给一行看不懂的灰区文案；
 *  ②label／pcPath 只取前端这张表（本表刻意无 label 列，见 ExpMobileTemplate 类注释），服务端只出「启用位＋顺序」；
 *  ③启用位认 `mobileEnabled === 1`，其余（0／null／其它值）一律按未启用 ⇒ 读不准时宁可少开，不可多开。
 */
export function normalizeMobileTemplates(rows) {
  if (!Array.isArray(rows)) return []
  const out = []
  rows.forEach((r, i) => {
    if (!r || !r.billType) return
    const key = docTypeKey(r.billType, r.billSubType)
    const meta = H5_DOC_TYPES.find((d) => d.key === key)
    if (!meta) return
    const serverEnabled = Number(r.mobileEnabled) === 1
    const capable = H5_FORM_CAPABLE_KEYS.includes(key)
    out.push({
      ...meta,
      serverEnabled,
      capable,
      mobile: serverEnabled && capable,
      sort: Number(r.sort) || i + 1,
    })
  })
  return out.sort((a, b) => a.sort - b.sort)
}

/**
 * 明示区（「以下类型请在电脑端提报」）的行间文案：三种成因要分开说，否则用户以为是自己没权限。
 *  - 库里未启用（含常量默认处理）⇒ 说"移动端暂未开放"，给电脑端去向；
 *  - 库里已启用但 H5 没表单 ⇒ 说"移动端表单未覆盖"，别让人误以为是配置坏了；
 *  - 接口没上线走常量 ⇒ 与第一种同文案，不向用户暴露"配置口未上线"这种开发规则。
 */
export function docTypeDenyReason(d) {
  if (d.serverEnabled && !d.capable) return '移动端表单未覆盖该类型'
  return '移动端暂未开放'
}


// ---------------- 本地草稿（UX-06/07：填一半被返回就没了） ----------------
const DRAFT_PREFIX = 'fssc.h5.draft.v1.'
const RECENT_PREFIX = 'fssc.h5.recent-items.v1.'

function draftKey(userId, docType) { return DRAFT_PREFIX + (userId ?? 'anon') + '.' + (docType || 'CLAIM') }

/** 草稿落本地（含附件元数据；File 对象不可序列化，故只存已上传的 fileId 清单） */
export function saveLocalDraft(userId, docType, payload) {
  try { localStorage.setItem(draftKey(userId, docType), JSON.stringify({ ...payload, savedAt: Date.now() })) } catch { /* 隐私模式/配额满＝静默，不阻断填写 */ }
}
export function loadLocalDraft(userId, docType) {
  try {
    const raw = localStorage.getItem(draftKey(userId, docType))
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch { return null }
}
export function clearLocalDraft(userId, docType) {
  try { localStorage.removeItem(draftKey(userId, docType)) } catch { /* 同上 */ }
}

/** 常用费用项 Top5（§4.2-②「费用项（带最近使用 Top5）」）：按提交次数累计，取前 5 */
export function pushRecentItems(userId, itemIds) {
  if (!userId || !itemIds?.length) return
  try {
    const k = RECENT_PREFIX + userId
    const cur = JSON.parse(localStorage.getItem(k) || '{}')
    itemIds.filter((x) => x != null).forEach((id) => { cur[String(id)] = (Number(cur[String(id)]) || 0) + 1 })
    localStorage.setItem(k, JSON.stringify(cur))
  } catch { /* 静默 */ }
}
export function recentItemIds(userId, limit = 5) {
  if (!userId) return []
  try {
    const cur = JSON.parse(localStorage.getItem(RECENT_PREFIX + userId) || '{}')
    return Object.keys(cur).sort((a, b) => (Number(cur[b]) || 0) - (Number(cur[a]) || 0)).slice(0, limit)
  } catch { return [] }
}

/**
 * 图像上传前压缩（§5 上传行：单张压缩后 ≤500KB、长边 1600、质量 0.8）。
 * 只处理 image/*；非图片、解码失败、产物反而更大 ⇒ 原样返回，绝不因为压缩失败丢用户的票。
 */
export function compressImage(file, { maxEdge = 1600, quality = 0.8, targetBytes = 500 * 1024 } = {}) {
  if (!file || !/^image\//.test(file.type || '')) return Promise.resolve(file)
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      try {
        const ratio = Math.min(1, maxEdge / Math.max(img.naturalWidth || maxEdge, img.naturalHeight || maxEdge))
        const w = Math.max(1, Math.round((img.naturalWidth || 0) * ratio))
        const h = Math.max(1, Math.round((img.naturalHeight || 0) * ratio))
        const canvas = document.createElement('canvas')
        canvas.width = w; canvas.height = h
        const ctx = canvas.getContext('2d')
        ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, w, h)
        ctx.drawImage(img, 0, 0, w, h)
        const toBlob = (q) => new Promise((r) => canvas.toBlob(r, 'image/jpeg', q))
        // 质量二分：0.8 起，超 500KB 依次降到 0.6/0.45/0.3，仍超则用最后一次产物
        ;(async () => {
          let best = null
          for (const q of [quality, 0.6, 0.45, 0.3]) {
            const b = await toBlob(q)
            if (!b) break
            best = b
            if (b.size <= targetBytes) break
          }
          URL.revokeObjectURL(url)
          if (!best || best.size >= file.size) { resolve(file); return }
          const base = (file.name || 'IMG').replace(/\.[^.]+$/, '')
          resolve(new File([best], `${base}.jpg`, { type: 'image/jpeg', lastModified: Date.now() }))
        })()
      } catch { URL.revokeObjectURL(url); resolve(file) }
    }
    img.onerror = () => { URL.revokeObjectURL(url); resolve(file) }
    img.src = url
  })
}
