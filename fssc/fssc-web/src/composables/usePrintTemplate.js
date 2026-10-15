/**
 * usePrintTemplate - 打印模板引擎 composable (R5-10)
 *
 * 支持占位符 {{field}} 替换、条件渲染 {{#if field}}...{{/if}}、
 * 循环 {{#each items}}...{{/each}}，以及 HTML 到浏览器打印。
 *
 * 用法：
 *   import { usePrintTemplate } from '@/composables/usePrintTemplate'
 *   const { renderTemplate, printHtml } = usePrintTemplate()
 *   const html = renderTemplate(templateString, data)
 *   printHtml(html, '单据打印')
 */

import { ref } from 'vue'

// 内置模板定义
const TEMPLATES = {
  // 报销单打印模板
  EXPENSE_CLAIM: `<div class="print-doc">
    <h1 style="text-align:center">费用报销单</h1>
    <table class="print-meta">
      <tr><td>单据编号：{{billNo}}</td><td>申请日期：{{applyDate}}</td></tr>
      <tr><td>申请人：{{applyUserName}}</td><td>部门：{{deptName}}</td></tr>
      <tr><td>币种：{{currencyCode}}</td><td>金额：{{totalAmount}}</td></tr>
    </table>
    <h3>费用明细</h3>
    <table class="print-detail">
      <tr><th>科目</th><th>摘要</th><th>金额</th></tr>
      {{#each lines}}
      <tr><td>{{subjectName}}</td><td>{{summary}}</td><td>{{amount}}</td></tr>
      {{/each}}
    </table>
    <div class="print-sign">
      <span>制单：{{applyUserName}}</span>
      <span>审批：{{approveUserName}}</span>
      <span>财务：________</span>
    </div>
  </div>`,

  // 付款单打印模板
  CORP_PAY: `<div class="print-doc">
    <h1 style="text-align:center">付款申请单</h1>
    <table class="print-meta">
      <tr><td>单据编号：{{billNo}}</td><td>日期：{{applyDate}}</td></tr>
      <tr><td>收款方：{{payeeName}}</td><td>开户行：{{payeeBank}}</td></tr>
      <tr><td>收款账号：{{payeeAccount}}</td><td>付款金额：{{amount}}</td></tr>
    </table>
    <div class="print-reason">付款事由：{{reason}}</div>
    <div class="print-sign">
      <span>经办：________</span>
      <span>审批：{{approveUserName}}</span>
      <span>财务：________</span>
    </div>
  </div>`,

  // 合同打印模板
  CONTRACT: `<div class="print-doc">
    <h1 style="text-align:center">合同登记表</h1>
    <table class="print-meta">
      <tr><td>合同编号：{{contractNo}}</td><td>签订日期：{{signDate}}</td></tr>
      <tr><td>合同名称：{{contractName}}</td><td>供应商：{{supplierName}}</td></tr>
      <tr><td>合同金额：{{contractAmount}}</td><td>币种：{{currencyCode}}</td></tr>
    </table>
    <div class="print-reason">合同内容摘要：{{summary}}</div>
  </div>`,

  // 发票登记打印模板
  INVOICE: `<div class="print-doc">
    <h1 style="text-align:center">发票登记表</h1>
    <table class="print-meta">
      <tr><td>发票代码：{{invoiceCode}}</td><td>发票号码：{{invoiceNo}}</td></tr>
      <tr><td>开票日期：{{invoiceDate}}</td><td>金额：{{amount}}</td></tr>
      <tr><td>销方名称：{{sellerName}}</td><td>购方名称：{{buyerName}}</td></tr>
    </table>
  </div>`,

  // 借款单打印模板
  LOAN: `<div class="print-doc">
    <h1 style="text-align:center">借款申请单</h1>
    <table class="print-meta">
      <tr><td>单据编号：{{billNo}}</td><td>日期：{{applyDate}}</td></tr>
      <tr><td>借款人：{{applyUserName}}</td><td>部门：{{deptName}}</td></tr>
      <tr><td>借款金额：{{loanAmount}}</td><td>预计还款日：{{repayDate}}</td></tr>
    </table>
    <div class="print-reason">借款事由：{{reason}}</div>
    <div class="print-sign">
      <span>借款人：{{applyUserName}}</span>
      <span>审批：________</span>
      <span>财务：________</span>
    </div>
  </div>`,
}

const PRINT_STYLES = `
  <style>
    @page { margin: 15mm; }
    body { font-family: 'SimSun', 'PingFang SC', serif; font-size: 14px; color: #333; }
    .print-doc { max-width: 700px; margin: 0 auto; }
    .print-doc h1 { font-size: 22px; margin: 0 0 20px; }
    .print-meta { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
    .print-meta td { border: 1px solid #ccc; padding: 6px 12px; font-size: 13px; }
    .print-detail { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
    .print-detail th, .print-detail td { border: 1px solid #ccc; padding: 6px 12px; font-size: 13px; }
    .print-detail th { background: #f5f7fa; font-weight: bold; text-align: center; }
    .print-sign { display: flex; justify-content: space-around; margin-top: 40px; }
    .print-sign span { display: inline-block; min-width: 120px; text-align: center; }
    .print-reason { margin: 12px 0; line-height: 1.8; }
    @media print { .no-print { display: none !important; } }
  </style>
`

export function usePrintTemplate() {
  const isPrinting = ref(false)

  /**
   * 渲染模板字符串
   * @param {string} template 模板 HTML 字符串
   * @param {object} data 数据对象
   * @returns {string} 渲染后的 HTML
   */
  function renderTemplate(template, data) {
    if (!template || !data) return template || ''

    let html = template

    // 处理 {{#each items}}...{{/each}} 循环
    html = html.replace(/\{\{#each\s+(\w+)\}\}([\s\S]*?)\{\{\/each\}\}/g, (_, key, body) => {
      const arr = data[key]
      if (!Array.isArray(arr)) return ''
      return arr.map((item) => renderTemplate(body, { ...data, ...item })).join('')
    })

    // 处理 {{#if field}}...{{/if}} 条件渲染
    html = html.replace(/\{\{#if\s+(\w+)\}\}([\s\S]*?)\{\{\/if\}\}/g, (_, key, body) => {
      return data[key] ? body : ''
    })

    // 处理 {{field}} 占位符
    html = html.replace(/\{\{(\w+)\}\}/g, (_, key) => {
      const val = data[key]
      if (val === null || val === undefined) return ''
      return String(val)
    })

    return html
  }

  /**
   * 获取内置模板
   * @param {string} templateName TEMPLATES key
   * @param {object} data 数据
   * @returns {string} 渲染后的完整 HTML
   */
  function renderPreset(templateName, data) {
    const tmpl = TEMPLATES[templateName]
    if (!tmpl) throw new Error(`未知模板类型: ${templateName}`)
    return renderTemplate(tmpl, data)
  }

  /**
   * 打印 HTML 内容
   * @param {string} html HTML 字符串
   * @param {string} title 打印窗口标题
   */
  function printHtml(html, title = '打印') {
    isPrinting.value = true
    const win = window.open('', '_blank', 'width=900,height=700')
    if (!win) {
      console.error('[printTemplate] 弹窗被浏览器拦截')
      isPrinting.value = false
      return
    }
    win.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>${title}</title>${PRINT_STYLES}</head><body>${html}<script>window.onload=function(){window.print();}<\/script></body></html>`)
    win.document.close()
    setTimeout(() => { isPrinting.value = false }, 1000)
  }

  /**
   * 一步完成：渲染预设模板并打印
   * @param {string} templateName
   * @param {object} data
   * @param {string} title
   */
  function printPreset(templateName, data, title) {
    const html = renderPreset(templateName, data)
    printHtml(html, title || templateName)
  }

  return {
    isPrinting,
    TEMPLATES,
    renderTemplate,
    renderPreset,
    printHtml,
    printPreset,
  }
}
