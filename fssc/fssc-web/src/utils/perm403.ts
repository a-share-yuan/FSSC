/**
 * G12（2026-10-09 权限治理）：403/4030 错误文案层治理。
 *
 * 问题：后端 4030 业务码的 msg 形如「缺少权限：treasury:creditbill:list」，
 * 直接弹给用户＝技术黑话，用户不知道发生了什么、该找谁。
 *
 * 方案：把原始权限码翻译成人话，同时保留「查看原因」入口（权限码写入 console，
 * toast 文案带可展开的权限码尾段）。
 */

// ── 权限码 → 中文名字典 ──
const DOMAIN_LABELS: Record<string, string> = {
  report: '报表',
  inv: '发票',
  wf: '审批流程',
  md: '主数据',
  system: '系统管理',
  sharing: '共享任务',
  pay: '付款',
  exp: '报销',
  treasury: '资金',
  compliance: '合规',
  dashboard: '仪表盘',
  auth: '认证',
  monitor: '监控',
  file: '文件',
  notify: '通知',
}

const ACTION_LABELS: Record<string, string> = {
  list: '查看',
  view: '查看',
  query: '查询',
  create: '新增',
  add: '新增',
  update: '修改',
  edit: '修改',
  delete: '删除',
  remove: '删除',
  approve: '审批',
  reject: '驳回',
  certify: '认证',
  verify: '验真',
  export: '导出',
  import: '导入',
  submit: '提交',
  test: '测试',
  enable: '启用',
  disable: '停用',
  dispatch: '派单',
  delegate: '委托',
  config: '配置',
  publish: '发布',
  audit: '审核',
  review: '复核',
  settle: '结算',
  match: '匹配',
  enhance: '增强',
}

// ── 权限码中间段（资源名）字典：只补"会弹给用户"的那几枚，其余原样透出 ──
const RESOURCE_LABELS: Record<string, string> = {
  share: '分摊',
  file: '附件',
}

/**
 * 把权限码 `domain:resource:action` 翻译为中文标签。
 * 例：`report:finance:view` →「报表·财务·查看」
 *     `md:supplier:create` →「主数据·供应商·新增」
 */
export function permCodeToLabel(code: string): string {
  if (!code) return ''
  const parts = code.split(':')
  const domain = DOMAIN_LABELS[parts[0]] || parts[0]
  const resource = RESOURCE_LABELS[parts[1]] || parts[1] || ''
  const action = parts[2] ? (ACTION_LABELS[parts[2]] || parts[2]) : ''
  const segs = [domain]
  if (resource) segs.push(resource)
  if (action) segs.push(action)
  return segs.join('·')
}

/**
 * 从原始错误消息中提取权限码。
 * 后端格式：「缺少权限：treasury:creditbill:list」或「无该功能/按钮权限：缺少权限：sharing:task:list」
 */
export function extractPermCode(rawMsg: string): string {
  if (!rawMsg) return ''
  // 匹配「缺少权限：xxx:xxx:xxx」格式
  const m = /缺少权限[:：]\s*([\w:.\-]+)/.exec(rawMsg)
  if (m?.[1]) return m[1]
  // 匹配裸权限码格式（如「无该功能：report:finance:view」）
  const m2 = /无该功能[^：:]*[:：]\s*([\w:.\-]+)/.exec(rawMsg)
  if (m2?.[1]) return m2[1]
  return ''
}

/**
 * 判断是否为权限不足错误（业务码 4030 或 HTTP 403）。
 */
export function isPermDenied(code?: number, status?: number, msg?: string): boolean {
  if (code === 4030) return true
  if (status === 403) return true
  if (msg && /无该功能|缺少权限|无权限|没有权限/.test(msg)) return true
  return false
}

/**
 * 把 403/4030 原始错误消息翻译为用户友好文案。
 *
 * 返回值：
 * - `friendly`: 用户看到的友好文案，如「您没有「资金·信贷单·查看」权限，请联系管理员开通」
 * - `permCode`: 提取出的权限码（用于「查看原因」）
 * - `rawMsg`: 原始消息（保留用于调试）
 */
export function friendlyPermError(rawMsg: string, code?: number, status?: number): {
  friendly: string
  permCode: string
  rawMsg: string
} {
  const permCode = extractPermCode(rawMsg)

  if (permCode) {
    const label = permCodeToLabel(permCode)
    return {
      friendly: `您没有「${label}」权限，请联系管理员开通`,
      permCode,
      rawMsg,
    }
  }

  // 无法提取权限码时，用通用文案
  if (isPermDenied(code, status, rawMsg)) {
    return {
      friendly: '您没有该功能的操作权限，请联系管理员开通',
      permCode: '',
      rawMsg,
    }
  }

  // 非权限错误，原样返回
  return { friendly: rawMsg, permCode: '', rawMsg }
}
