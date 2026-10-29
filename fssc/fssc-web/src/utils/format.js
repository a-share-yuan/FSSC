// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 通用格式化工具：日期 + 金额 + 数量。
 * - 纯日期列显示 YYYY-MM-DD
 * - 含时间的列显示 YYYY-MM-DD HH:mm
 * - 金额列统一千分位 + 2 位小数
 */
import { statusLabel } from '../constants/billStatus'
import {
  enumLabel, bizTypeLabel, billTypeLabel, taskTypeLabel, actionLabel,
  DIRECTION, PERIOD_TYPE, CONTROL_TYPE, ADJUST_TYPE, OPER_TYPE,
  ASSIGN_TYPE, MULTI_TYPE, TIMEOUT_ACTION, REJECT_TO, ORG_SCOPE, verifyStatusLabel,
} from '../constants/enums'

/**
 * 格式化日期为 YYYY-MM-DD
 * 输入可能是 ISO 字符串、时间戳(ms)、Date 对象或 null
 */
export function formatDate(val) {
  if (!val) return ''
  // 后端主格式是 ISO 'T' 分隔，先原样解析（'/' 替换法会把它打成 Invalid Date），
  // 失败再回退老写法（同 formatDateTime 的处理）。
  if (typeof val === 'string' && val.includes('T')) {
    const native = new Date(val)
    if (!isNaN(native.getTime())) {
      const y = native.getFullYear()
      const m = String(native.getMonth() + 1).padStart(2, '0')
      const day = String(native.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    }
  }
  const d = typeof val === 'string' ? new Date(val.replace(/-/g, '/')) : new Date(val)
  if (isNaN(d.getTime())) return String(val)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/**
 * 格式化日期时间为 YYYY-MM-DD HH:mm
 */
export function formatDateTime(val) {
  if (!val) return ''
  // 先原样解析：后端主格式是 ISO 'T' 分隔，而把 '-' 一律换成 '/' 的老写法会把它打成 Invalid Date，
  // 于是走 String(val) 默认处理、界面上留下裸 ISO 时间（体检 E-12）。'/' 分支只作失败回退。
  const native = new Date(val)
  const d = isNaN(native.getTime()) ? new Date(String(val).replace(/-/g, '/')) : native
  if (isNaN(d.getTime())) return String(val)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}`
}

/**
 * 金额格式化：千分位 + 固定 2 位小数。
 * 全系统金额列统一走此函数，禁止再手写 toFixed/replace 正则。
 *
 * @param {number|string|null|undefined} val 金额
 * @param {string} [empty='-'] 空值占位符（列表用 '-'，汇总卡片可传 '0.00'）
 * @returns {string} 例：1234567.8 -> "1,234,567.80"
 */
export function formatMoney(val, empty = '-') {
  if (val === null || val === undefined || val === '') return empty
  const n = Number(val)
  if (isNaN(n)) return String(val)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/**
 * 数量格式化：千分位，小数位按需（默认 0~4 位）。
 * 用于库存、数量、天数等非金额数值列。
 */
export function formatNumber(val, digits = 4, empty = '-') {
  if (val === null || val === undefined || val === '') return empty
  const n = Number(val)
  if (isNaN(n)) return String(val)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: digits })
}

/**
 * 百分比格式化：传入 0~1 的小数或已乘 100 的值。
 * @param {number} val 数值
 * @param {boolean} [isRatio=true] true 表示 val 是 0~1 比例（会 ×100）
 * @param {number} [digits=2] 小数位
 */
export function formatPercent(val, isRatio = true, digits = 2) {
  if (val === null || val === undefined || val === '') return '-'
  let n = Number(val)
  if (isNaN(n)) return String(val)
  if (isRatio) n = n * 100
  return `${n.toFixed(digits)}%`
}

/** 金额千分位（无小数限制），用于汇总/统计卡片 */
export function formatMoneyShort(val, empty = '-') {
  if (val === null || val === undefined || val === '') return empty
  const n = Number(val)
  if (isNaN(n)) return String(val)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/**
 * 成本中心展示标签：后端主数据字段为 ccCode/ccName（部分旧字段是 costCenterName）。
 * 若无匹配 option，el-select 会回退显示原始 ID 数字 —— 统一用名称（带编码）避免此类问题。
 */
export function ccLabel(c) {
  if (!c) return ''
  return ((c.ccCode || '') + ' ' + (c.ccName || c.costCenterName || '')).trim()
}

/** 项目展示标签：代码 + 名称（同样避免回退显示 ID 数字） */
export function projectLabel(p) {
  if (!p) return ''
  return ((p.projectCode || '') + ' ' + (p.projectName || '')).trim()
}

/**
 * 明细行 ext_json 解析（字符串/对象两种写入数据库形态都要认）。
 * 招待三项的 ext_json 镜像就放在这一列里。
 */
export function parseLineExt(row) {
  const e = row?.extJson
  if (!e) return {}
  if (typeof e === 'string') { try { return JSON.parse(e || '{}') || {} } catch { return {} } }
  return typeof e === 'object' ? e : {}
}

/**
 * 招待三项（接待对象 / 接待人数 / 人均标准）查询，供单据详情与费用明细表回显。
 * 优先真实列 exp_bill_line.attendees / person_count / entertain_type（落列），
 * 缺失回退行 ext_json（entertainTarget / entertainCount / entertainType / perCapita）；
 * 人均标准两处都没有时按 金额 ÷ 人数 现算，使历史单据（ext_json={}）也能按金额与人数复核。
 */
export function entertainOfLine(row) {
  const ext = parseLineExt(row)
  const count = row?.personCount ?? ext.entertainCount ?? null
  const amount = Number(row?.amount || 0)
  const perCapita = ext.perCapita != null ? Number(ext.perCapita)
    : (Number(count) >= 1 && amount > 0 ? Math.round((amount / Number(count)) * 100) / 100 : null)
  return {
    target: row?.attendees || ext.entertainTarget || '',
    count: count != null ? Number(count) : null,
    perCapita,
    type: row?.entertainType || ext.entertainType || '',
  }
}

/** 明细集合中是否存在招待三项数据：全无时不渲染这三列，避免非招待类单据凭空多出空白列 */
export function hasEntertainLines(lines) {
  return (lines || []).some((r) => {
    const e = entertainOfLine(r)
    return !!(e.target || e.count != null || e.type)
  })
}

/**
 * 预算科目展示标签：把预算明细的维度串拼成可读名称（如 "上海公司 · 总裁办公室 · 行政办公费"）。
 * dimKey 有两套写法都要认：写入数据库规则为连字符式 `2026-COMPANY33-DEPT1311-ITEM331`
 * （bg_item 现存 1324 行全部是这一种），早期规则为冒号式 `DEPT:1_ITEM:5`。
 * 只认冒号式会让整列退化成原始 dimKey 串，用户无法判断选的是哪条预算线。
 * 解析失败再回退到 外部财务系统科目码或 dimKey。
 *
 * @param ctx 可选 {companies, projects}，用于解析 COMPANY/PROJECT 段
 */
export function budgetLabel(items, depts, costCenters, bg, ctx) {
  if (!bg) return ''
  const pools = {
    COMPANY: [(ctx && ctx.companies) || [], (x) => x.name || x.companyName],
    DEPT: [depts || [], (x) => x.name || x.deptName],
    CC: [costCenters || [], (x) => x.ccName || x.costCenterName],
    COST_CENTER: [costCenters || [], (x) => x.ccName || x.costCenterName],
    ITEM: [items || [], (x) => x.itemName || x.name],
    PROJECT: [(ctx && ctx.projects) || [], (x) => x.projectName || x.name],
  }
  const names = []
  const seen = new Set()
  const segRe = /([A-Z]+)[:\-]?(\d+)/g
  let m
  while ((m = segRe.exec(String(bg.dimKey || ''))) !== null) {
    const kind = m[1]
    const id = m[2]
    const pool = pools[kind]
    let label = ''
    if (pool) {
      const hit = pool[0].find((x) => String(x.id) === id)
      label = hit ? (pool[1](hit) || '') : ''
    }
    const key = kind + ':' + id
    if (label && !seen.has(key)) { names.push(label); seen.add(key) }
  }
  if (names.length) return names.join(' · ')
  return bg.ncAccount || bg.ifsAccount || bg.dimKey || ''
}

/**
 * 选中的预算明细不在可见列表内（跨公司/部门、方案未下达）时的占位标签。
 * el-select 找不到匹配 option 会退回显示原始主键（用户看到的就是"20"这种数字），
 * 补一个说明性 option 才能把"这行为什么不可选"讲清楚。
 */
export function budgetUnseenLabel(id) {
  return id == null || id === '' ? '' : `预算明细 #${id}（非本单位可见，请重选）`
}

/**
 * 通用「行详情」弹窗（替代各页 Object.entries(row) 原始字段 dump 的调试式弹窗）。
 * 友好化：隐藏内部/技术性字段，常见业务字段映射中文标签，金额用千分位，两列定义列表渲染。
 *
 * @param {object} row 行数据
 * @param {object} [opts] { title?, labels?: {field: '中文名'}, enums?: {field: (v) => '中文值'} }
 */

// 常见业务字段 → 中文标签（全系统共用，避免行详情弹窗显示英文键名"看着像 JSON"）
const COMMON_LABELS = {
  billNo: '单号', bizNo: '单号', bill_no: '单号', adjustNo: '调整单号', batchNo: '批次号',
  billId: '单据ID', billType: '单据类型', bizType: '业务类型',
  title: '标题', summary: '摘要', remark: '备注', reason: '原因',
  amount: '金额', totalAmount: '总金额', paidAmount: '已付金额', useAmount: '使用金额',
  receivedAmount: '已收金额', remainAmount: '剩余金额', dueDate: '到期日',
  currency: '币种', currencyCode: '币种', exchangeRate: '汇率',
  status: '状态', billStatus: '单据状态', poolStatus: '任务状态', taskType: '作业项', delFlag: '删除标记',
  applyDate: '申请日期', billDate: '单据日期', receiveTime: '认领时间', slaTime: 'SLA 截止',
  createTime: '创建时间', createBy: '创建人', updateTime: '更新时间', updateBy: '更新人',
  lineNo: '行号', direction: '方向', subjectCode: '科目编码', subjectName: '科目名称',
  deptName: '部门', deptCode: '部门编码', companyName: '公司', companyCode: '公司编码',
  payeeName: '收款方', bankAccount: '账号', bankName: '开户行',
  itemName: '费用项', itemCode: '费用项编码', projectName: '项目', projectCode: '项目编码',
  period: '期间', periodType: '周期类型', budgetYear: '预算年度', schemeName: '方案名称', schemeCode: '方案编码',
  supplierName: '供应商', supplierCode: '供应商编码', contractNo: '合同号', invoiceNo: '发票号', invoiceCode: '发票代码',
  sourceDocType: '来源单据类型',
  userName: '用户名', realName: '姓名', nickName: '姓名', mobile: '手机号', email: '邮箱', roleName: '角色',
  currentNode: '当前节点', currentAssignee: '当前处理人', initiatorName: '发起人', approverName: '审批人',
  approveTime: '审批时间', comment: '审批意见', duration: '耗时', urgentFlag: '紧急',
  expenseDate: '费用日期', occurDate: '发生日期', payDate: '付款日期', invoiceDate: '开票日期',
  quantity: '数量', qty: '数量', taxRate: '税率', taxAmount: '税额', noTaxAmount: '不含税金额',
  settleMode: '结算方式', employeeName: '员工', departmentName: '部门',
  modelKey: '流程编码', modelVersion: '流程版本', procInstId: '流程实例ID', taskId: '任务ID',
  // 申请类单据（出差行程 / 培训人员 / 会议信息 / 分摊）行详情
  // 这些键此前没有中文标签，行详情弹窗会走 prettyKey() 回落成
  // "Traffic Type / Allowance Amount / Round Trip" 这种英文标题，
  // 中文界面里看着像半截 JSON。统一在此登记，凡是走 showRowDetail 的页面一律变中文。
  travelerName: '出差人', personName: '姓名', employeeNo: '工号', empNo: '工号',
  beginDate: '开始日期', endDate: '结束日期', startDate: '开始日期',
  days: '天数', tripDays: '出差天数', nights: '住宿晚数',
  trafficType: '交通方式', trafficClass: '舱位/席别', roundTrip: '是否往返', overStd: '是否超标',
  hotelAmount: '住宿费', trafficAmount: '长途交通费', localTrafficAmount: '市内交通费',
  allowanceAmount: '餐饮补贴', otherAmount: '其他费用', stdAmount: '标准金额',
  hotelFee: '住宿费', trafficFee: '长途交通费', localTrafficFee: '市内交通费',
  allowanceFee: '餐饮补贴', trainFee: '培训费', otherFee: '其他费用',
  tripMode: '行程方式', seatClass: '舱位/席别',
  trainType: '培训类型', trainName: '培训名称', trainPlace: '培训地点',
  planFlag: '计划内', travelFlag: '含差旅', abroadFlag: '境外',
  meetingName: '会议名称', meetingPlace: '会议地点', meetingAmount: '会议费用',
  topic: '会议主题', attendees: '参会人数', personCount: '人数',
  shareType: '分摊方式', shareValue: '分摊值', shareAmount: '分摊金额', sharePercent: '分摊比例',
}

// 字段 → 枚举字典：行详情弹窗的值若为枚举码，翻译成中文而不是原样输出英文
// 默认处理文案与 composables/useBaseDict.js 的 TRAFFIC_LOCAL / SEAT_LOCAL / TRAINING_LOCAL 逐字一致；
// 页面若已加载后台字典，可通过 opts.enums 传入实时文案覆盖（见 showRowDetail）
const TRAFFIC_MODE_LABEL = { AIR: '飞机', RAIL: '高铁', TRAIN: '火车', CAR: '汽车', SHIP: '轮船' }
const SEAT_CLASS_LABEL = { ECONOMY: '经济舱', BUSINESS: '商务舱', FIRST: '头等舱', SECOND: '二等座', FIRST_SEAT: '一等座' }
const TRAINING_TYPE_LABEL = { INTERNAL: '内训', EXTERNAL: '外训', PUBLIC: '公开课' }
const yesNoLabel = (v) => (v === true || v === 1 || v === '1' || v === 'true' ? '是'
  : v === false || v === 0 || v === '0' || v === 'false' ? '否' : '')

const VALUE_ENUM_BY_KEY = {
  status: statusLabel, billStatus: statusLabel, poolStatus: statusLabel,
  verifyStatus: verifyStatusLabel,
  bizType: bizTypeLabel, billType: billTypeLabel, taskType: taskTypeLabel, action: actionLabel,
  direction: (v) => enumLabel(DIRECTION, v), periodType: (v) => enumLabel(PERIOD_TYPE, v),
  controlType: (v) => enumLabel(CONTROL_TYPE, v), adjustType: (v) => enumLabel(ADJUST_TYPE, v),
  operType: (v) => enumLabel(OPER_TYPE, v), assignType: (v) => enumLabel(ASSIGN_TYPE, v),
  multiType: (v) => enumLabel(MULTI_TYPE, v), timeoutAction: (v) => enumLabel(TIMEOUT_ACTION, v),
  rejectTo: (v) => enumLabel(REJECT_TO, v), orgScope: (v) => enumLabel(ORG_SCOPE, v),
  // 申请类单据：交通方式 / 舱位席别 / 培训类型（字典 traffic_mode / seat_class / training_type）
  trafficType: (v) => TRAFFIC_MODE_LABEL[v] || '', tripMode: (v) => TRAFFIC_MODE_LABEL[v] || '',
  trafficClass: (v) => SEAT_CLASS_LABEL[v] || '', seatClass: (v) => SEAT_CLASS_LABEL[v] || '',
  trainType: (v) => TRAINING_TYPE_LABEL[v] || '',
  // 布尔语义标志位：数据库/接口给的是 true/false 或 1/0
  roundTrip: yesNoLabel, planFlag: yesNoLabel, travelFlag: yesNoLabel, abroadFlag: yesNoLabel,
  overStd: (v) => (Number(v) === 1 ? '超标' : '合规'),
}

// 技术性字段：无业务意义，行详情弹窗一律隐藏（页面通过 opts.labels 显式指定的除外）
const TECH_KEY_RE = /^(id|.*_id|.*Id|.*Ids|createBy|updateBy|createById|updateById|updateTime|deleted|version|tenantId|tenant_id|sort|sortNo|seq|orderNo|extJson|ext_json|activeFlag|active|statusCode|code)$/
function isTechField(k) {
  return TECH_KEY_RE.test(k)
}

// 行详情弹窗：数字值只有键名形如金额（*amount/*money/*price/*fee）才按金额格式化，
// 其余数字（行号/数量/标志位等）原样字符串输出——曾有 create_by=3800 被写成「3,800.00」
// fee：出差行程/培训人员行的 hotelFee/trafficFee/allowanceFee 等键名不带 amount，
// 此前只落 String(v)，2400 显示成「2400」没有千分位与两位小数，与同屏 amount 列规则不一
const MONEY_KEY_RE = /(amount|money|price|fee)/i
// 行详情弹窗：字符串日期识别（纯日期 → formatDate；ISO 日期时间 → formatDateTime）
const DATE_ONLY_RE = /^\d{4}-\d{2}-\d{2}$/
const DATETIME_RE = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/

export async function showRowDetail(row, opts = {}) {
  if (!row || Object.keys(row).length === 0) {
    return // 空行不弹窗
  }
  const { title = '行详情' } = opts
  const items = rowDetailItems(row, opts)
  if (!items.length) return
  const [{ ElMessageBox }, { h }] = await Promise.all([import('element-plus'), import('vue')])
  // Element Plus 契约：alert 的 message 直接传 VNode（传入 {__isVNode, default} 对象会被
  // JSON.stringify 成原始 VNode 树渲染——曾导致所有行详情弹窗显示一屏节点 JSON）
  ElMessageBox.alert(
    h('div', { class: 'row-detail' },
      items.map(([k, v]) => h('div', { class: 'row-detail-item' }, [
        h('span', { class: 'rd-key' }, k + '：'),
        h('span', { class: 'rd-val' }, v),
      ]))),
    title, { customClass: 'row-detail-dialog' })
}

/**
 * 行详情弹窗的「字段名 + 值」列表（纯函数，供 showRowDetail 与单测共用）。
 * 抽出来是为了能脱离 Element Plus 断言渲染结果——曾出现「编译通过但弹窗里仍是
 * Traffic Type / RAIL / true」的英文裸码，只有断言渲染文本才拦得住。
 */
export function rowDetailItems(row, opts = {}) {
  if (!row || Object.keys(row).length === 0) return []
  const { labels = {}, enums = {} } = opts
  return Object.entries(row)
    .filter(([k, v]) => {
      if (k.startsWith('_')) return false // 内部字段
      if (v === null || v === undefined || v === '') return false
      if (typeof v === 'object') return false // 数组/对象
      if (labels[k] || COMMON_LABELS[k]) return true // 有中文标签的业务字段保留
      return !isTechField(k) // 隐藏技术字段，其余保留
    })
    .map(([k, v]) => {
      const key = labels[k] || COMMON_LABELS[k] || prettyKey(k)
      // 页面显式传入的字典文案（opts.enums）优先；字典未加载/缺键时回落内置映射，
      // 两处都翻不出来才原样输出码值（保证后台改字典后详情跟着变，且不因字典未到而退化成英文）
      const dictEnum = enums[k]
      const builtinEnum = VALUE_ENUM_BY_KEY[k]
      const translated = (dictEnum && typeof dictEnum === 'function' ? dictEnum(v) : '')
        || (builtinEnum ? builtinEnum(v) : '')
      let val
      if (translated) {
        val = translated // 枚举/布尔码已翻译成中文，不再按金额或原码输出
      } else if (typeof v === 'number') {
        // 仅金额键走千分位金额，其余数字原样输出（行号/数量/标志位不再是「x.xx 金额」）
        val = MONEY_KEY_RE.test(k) ? formatMoneyShort(v) : String(v)
      } else if (typeof v === 'boolean') {
        val = v ? '是' : '否'
      } else if (typeof v === 'string' && DATETIME_RE.test(v)) {
        val = formatDateTime(v)
      } else if (typeof v === 'string' && DATE_ONLY_RE.test(v)) {
        val = formatDate(v)
      } else {
        val = String(v)
      }
      return [key, val]
    })
}

/** 把 snake_case/camelCase 键转成中文习惯的空格分隔 */
function prettyKey(key) {
  const spaced = key
    .replace(/([a-z\d])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}
