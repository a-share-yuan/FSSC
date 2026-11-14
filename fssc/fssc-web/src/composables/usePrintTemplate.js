/**
 * usePrintTemplate - 打印/套打引擎 composable（V1.4.81）
 *
 * ============================ 变更说明（对齐差距②） ============================
 * 旧版：5 套模板（EXPENSE_CLAIM/CORP_PAY/CONTRACT/INVOICE/LOAN）硬编码在本文件
 *       的 TEMPLATES 常量里 —— 新增一种单据的打印样式 = 改前端代码 + 发版。
 * 新版：模板落在后端 md_print_template / md_print_template_item，
 *       本 composable 只做「取模板 → 传数据 → 渲染打印」三步。
 *       新增单据打印样式 = 后台建模板数据，**不改代码不发版**。
 *
 * 后端接口
 *   GET  /api/tenant/masterdata/print/template/biz/{bizType}  取模板（含毫米坐标/字段属性）
 *   GET  /api/tenant/masterdata/print/template/{templateCode}  取单个模板
 *   GET  /api/tenant/masterdata/print/set/{setCode}           取模板套
 *   POST /api/tenant/masterdata/print/render                  渲染（返回 HTML + 必录校验）
 *
 * 必录属性（字段必录 + 锁定属性）：
 *   后端 required=1 的字段缺值时返回 blocked=true 与 missingRequired 清单，
 *   本 composable 默认<b>拦截不打印</b>，可用 force 强制放行。
 * =============================================================================
 */

import { ref } from 'vue'
import request from '@/utils/request'

/**
 * 离线兜底模板（后端不可达时用）。
 * 仅在网络异常时启用，正常路径一律走后端 —— 这是降级，不是主路径。
 */
const FALLBACK_TEMPLATES = {
  EXPENSE_CLAIM: {
    templateCode: 'PT_EXPENSE_CLAIM', templateName: '费用报销单',
    paperSize: 'A4', orientation: 'PORTRAIT',
    widthMm: 210, heightMm: 297,
    marginTopMm: 15, marginBottomMm: 15, marginLeftMm: 15, marginRightMm: 15,
    gridRows: 30, gridCols: 12,
    items: [
      { itemKey: 'title', label: '费用报销单', rowIndex: 1, colIndex: 1, colSpan: 12, fontSize: 22, fontWeight: 'BOLD', align: 'CENTER', locked: 1 },
      { itemKey: 'billNo', label: '单据编号', dataSource: 'head.billNo', rowIndex: 3, colIndex: 1, colSpan: 4, required: 1 },
      { itemKey: 'applyDate', label: '申请日期', dataSource: 'head.applyDate', rowIndex: 3, colIndex: 5, colSpan: 4, required: 1, formatType: 'DATE' },
      { itemKey: 'applyUserName', label: '申请人', dataSource: 'head.applyUserName', rowIndex: 4, colIndex: 1, colSpan: 4, required: 1 },
      { itemKey: 'deptName', label: '部门', dataSource: 'head.deptName', rowIndex: 4, colIndex: 5, colSpan: 4 },
      { itemKey: 'totalAmount', label: '金额', dataSource: 'head.totalAmount', rowIndex: 5, colIndex: 5, colSpan: 4, required: 1, formatType: 'NUMBER' },
      { itemKey: 'lines', label: '费用明细', dataSource: 'lines', itemType: 'LINE_DETAIL', rowIndex: 8, colIndex: 1, colSpan: 12 },
    ],
  },
  CORP_PAY: {
    templateCode: 'PT_CORP_PAY', templateName: '付款申请单',
    paperSize: 'A4', orientation: 'PORTRAIT',
    widthMm: 210, heightMm: 297,
    marginTopMm: 15, marginBottomMm: 15, marginLeftMm: 15, marginRightMm: 15,
    gridRows: 30, gridCols: 12,
    items: [
      { itemKey: 'title', label: '付款申请单', rowIndex: 1, colIndex: 1, colSpan: 12, fontSize: 22, fontWeight: 'BOLD', align: 'CENTER', locked: 1 },
      { itemKey: 'billNo', label: '单据编号', dataSource: 'head.billNo', rowIndex: 3, colIndex: 1, colSpan: 6, required: 1 },
      { itemKey: 'payeeName', label: '收款方', dataSource: 'head.payeeName', rowIndex: 4, colIndex: 1, colSpan: 6, required: 1, locked: 1 },
      { itemKey: 'amount', label: '付款金额', dataSource: 'head.amount', rowIndex: 5, colIndex: 7, colSpan: 6, required: 1, formatType: 'NUMBER' },
      { itemKey: 'reason', label: '付款事由', dataSource: 'head.reason', rowIndex: 7, colIndex: 1, colSpan: 12 },
    ],
  },
}

const PRINT_STYLES = `
  <style>
    @page { margin: 0; }
    body { margin: 0; font-family: 'SimSun', 'PingFang SC', serif; color: #000; }
    .pt-page { margin: 0 auto; }
    .pt-cell { overflow: hidden; }
    .pt-locked { background: #f0f0f0; color: #555; }
    .pt-divider { border: none !important; font-weight: bold; border-bottom: 0.3mm solid #333 !important; }
    .pt-lines table { width: 100%; border-collapse: collapse; }
    .pt-missing { margin: 8px 0; padding: 8px; border: 1px solid #f56c6c; background: #fef0f0; color: #f56c6c; font-size: 13px; }
    @media print { .no-print { display: none !important; } .pt-missing { display: none !important; } }
  </style>
`

export function usePrintTemplate() {
  const isPrinting = ref(false)
  /** 后端模板缓存：bizType -> TemplateView */
  const templateCache = {}

  /** 取模板（带缓存）。失败时降级到内置兜底模板并告警。 */
  async function fetchTemplate(bizType, templateCode) {
    const ck = templateCode || ('biz:' + bizType)
    if (templateCache[ck]) return templateCache[ck]
    try {
      const url = templateCode
        ? `/masterdata/print/template/${templateCode}`
        : `/masterdata/print/template/biz/${bizType}`
      const res = await request.get(url)
      const data = res?.data
      let tpl = Array.isArray(data) ? data[0] : data
      if (!tpl) throw new Error('后端未返回模板')
      templateCache[ck] = tpl
      return tpl
    } catch (e) {
      console.warn('[printTemplate] 后端模板不可达，降级内置模板:', e?.message)
      const fb = FALLBACK_TEMPLATES[bizType]
      if (!fb) throw new Error(`打印模板获取失败且无兜底模板: ${bizType}`)
      return fb
    }
  }

  /** 预热模板（页面加载时调用，避免打印瞬间才去后端取） */
  async function preloadTemplate(bizType) {
    try { await fetchTemplate(bizType); return true } catch { return false }
  }

  /**
   * 调后端渲染。
   * @param {string} bizType 业务类型（EXPENSE_CLAIM/CORP_PAY/...）
   * @param {object} data {head:{}, lines:[]}
   * @param {object} opts {templateCode, setCode, force}
   * @returns {{html, blocked, missingRequired, template}}
   */
  async function renderByBiz(bizType, data, opts = {}) {
    const body = {
      bizType,
      templateCode: opts.templateCode || null,
      setCode: opts.setCode || null,
      data,
      renderHtml: true,
    }
    const res = await request.post('/masterdata/print/render', body)
    const result = res?.data
    // 传 setCode 时后端返回数组
    if (Array.isArray(result)) {
      const blockedList = result.filter((r) => r.blocked)
      return {
        html: result.map((r) => r.html).join('<div class="pt-page-break" style="page-break-after:always"></div>'),
        blocked: blockedList.length > 0 && !opts.force,
        missingRequired: blockedList.flatMap((r) => r.missingRequired || []),
        template: result[0]?.template,
      }
    }
    return {
      html: result?.html || '',
      blocked: result?.blocked && !opts.force,
      missingRequired: result?.missingRequired || [],
      template: result?.template,
    }
  }

  /**
   * 按业务类型打印（主入口）。
   *
   * @param {string} bizType 业务单据类型
   * @param {object} data {head, lines}
   * @param {object} opts {templateCode, setCode, title, force, onBlocked}
   */
  async function printByBiz(bizType, data, opts = {}) {
    isPrinting.value = true
    try {
      const r = await renderByBiz(bizType, data, opts)
      if (r.blocked) {
        const names = r.missingRequired.map((m) => m.label || m.itemKey).join('、')
        console.warn('[printTemplate] 必录字段缺失，已拦截打印:', names)
        if (typeof opts.onBlocked === 'function') {
          opts.onBlocked(r.missingRequired)
        } else {
          window.alert(`以下必录字段未填写，无法打印：\n${names}`)
        }
        return { printed: false, missingRequired: r.missingRequired }
      }
      printHtml(r.html, opts.title || '打印', r.template)
      return { printed: true, missingRequired: [] }
    } finally {
      setTimeout(() => { isPrinting.value = false }, 1000)
    }
  }

  /**
   * 打印 HTML 内容。
   * @param {string} html 已渲染 HTML
   * @param {string} title 打印窗口标题
   * @param {object} template 模板视图（决定 @page 尺寸）
   */
  function printHtml(html, title = '打印', template) {
    const pageStyle = template
      ? `@page { size: ${template.paperSize || 'A4'} ${template.orientation === 'LANDSCAPE' ? 'landscape' : 'portrait'}; }`
      : ''
    const win = window.open('', '_blank', 'width=900,height=700')
    if (!win) {
      console.error('[printTemplate] 弹窗被浏览器拦截')
      return
    }
    win.document.write(
      `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${title}</title>` +
      `<style>${pageStyle}</style>${PRINT_STYLES}</head>` +
      `<body>${html}<script>window.onload=function(){window.print();}<\/script></body></html>`
    )
    win.document.close()
  }

  /**
   * 本地兜底渲染（后端不可达时用兜底模板自绘）。
   * 注意：这是降级路径，必录校验能力弱于后端，仅保证能打印。
   */
  function renderLocal(template, data) {
    if (!template || !data) return ''
    const pageW = (template.widthMm || 210) - (template.marginLeftMm || 15) - (template.marginRightMm || 15)
    const cellW = pageW / (template.gridCols || 12)
    const cellH = ((template.heightMm || 297) - (template.marginTopMm || 15) - (template.marginBottomMm || 15)) / (template.gridRows || 30)
    const head = data.head || {}
    const lines = data.lines || []
    let html = `<div class="pt-doc" style="position:relative;width:${pageW}mm;height:${((template.heightMm || 297) - 30)}mm;font-family:'SimSun',serif;">`
    for (const it of template.items || []) {
      if (it.itemType === 'LINE_DETAIL') continue
      const text = it.dataSource
        ? (it.dataSource.startsWith('head.') ? (head[it.dataSource.slice(5)] ?? it.label) : it.label)
        : it.label
      const x = (template.marginLeftMm || 15) + cellW * ((it.colIndex || 1) - 1)
      const y = (template.marginTopMm || 15) + cellH * ((it.rowIndex || 1) - 1)
      const w = it.widthMm || cellW * (it.colSpan || 1)
      const h = it.heightMm || cellH * (it.rowSpan || 1)
      html += `<div style="position:absolute;left:${x}mm;top:${y}mm;width:${w}mm;height:${h}mm;` +
        `font-size:${it.fontSize || 12}pt;font-weight:${it.fontWeight === 'BOLD' ? 'bold' : 'normal'};` +
        `text-align:${(it.align || 'LEFT').toLowerCase()};border:0.2mm solid #333;padding:1mm 2mm;box-sizing:border-box;">${text}</div>`
    }
    if (lines.length) {
      const first = lines[0]
      const keys = Object.keys(first)
      const det = (template.items || []).find((i) => i.itemType === 'LINE_DETAIL')
      const x = (template.marginLeftMm || 15) + cellW * ((det?.colIndex || 1) - 1)
      const y = (template.marginTopMm || 15) + cellH * ((det?.rowIndex || 8) - 1)
      html += `<table style="position:absolute;left:${x}mm;top:${y}mm;width:${cellW * 12}mm;border-collapse:collapse;font-size:11pt;">`
      html += '<thead><tr>' + keys.map((k) => `<th style="border:0.2mm solid #333;padding:1mm;">${k}</th>`).join('') + '</tr></thead><tbody>'
      for (const row of lines) {
        html += '<tr>' + keys.map((k) => `<td style="border:0.2mm solid #333;padding:1mm;">${row[k] ?? ''}</td>`).join('') + '</tr>'
      }
      html += '</tbody></table>'
    }
    return html + '</div>'
  }

  /** 清模板缓存（模板改版后调用，保证下次打印取新模板） */
  function clearCache() {
    Object.keys(templateCache).forEach((k) => delete templateCache[k])
  }

  return {
    isPrinting,
    templateCache,
    fetchTemplate,
    preloadTemplate,
    renderByBiz,
    printByBiz,
    printHtml,
    renderLocal,
    clearCache,
    FALLBACK_TEMPLATES,
  }
}
