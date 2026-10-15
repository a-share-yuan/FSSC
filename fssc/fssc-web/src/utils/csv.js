/**
 * CSV 导出工具（P2-2 交互一致性：消除 26 页各自 copy-paste 的 CSV 逻辑）。
 * 所有自造 CRUD 页面统一调用此函数。
 */

import { ElMessage } from 'element-plus'

/**
 * 导出 CSV 文件。
 * @param {Array} rows       数据行
 * @param {Array} cols       列定义 [{ prop, label }]
 * @param {String} filename  文件名（不含扩展名）
 */
export function exportCsv(rows, cols, filename) {
  if (!rows || !rows.length) {
    ElMessage.warning('无数据可导出')
    return
  }
  const header = cols.map((c) => c.label).join(',')
  const lines = rows.map((row) =>
    cols.map((c) => {
      const v = row?.[c.prop]
      if (v == null) return ''
      let s = String(v)
      if (s.includes(',') || s.includes('"') || s.includes('\n')) {
        s = '"' + s.replace(/"/g, '""') + '"'
      }
      return s
    }).join(',')
  )
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${filename}_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 CSV')
}

/**
 * 批量导出选中行（通用列，自动从行数据提取列名）。
 * @param {Array} selRows    选中行
 * @param {String} filename  文件名
 */
export function exportBatchCsv(selRows, filename) {
  if (!selRows || !selRows.length) {
    ElMessage.warning('请先勾选要导出的记录')
    return
  }
  const keys = selRows[0] ? Object.keys(selRows[0]) : []
  if (!keys.length) {
    ElMessage.warning('无法获取列信息')
    return
  }
  const header = keys.join(',')
  const lines = selRows.map((row) =>
    keys.map((k) => {
      const v = row[k]
      if (v == null) return ''
      let s = String(v)
      if (s.includes(',') || s.includes('"') || s.includes('\n')) {
        s = '"' + s.replace(/"/g, '""') + '"'
      }
      return s
    }).join(',')
  )
  const csv = '\ufeff' + [header, ...lines].join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${filename}_${new Date().toISOString().slice(0, 10)}_batch.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${selRows.length} 条记录`)
}

/** 数字格式化（千分位 + 2 位小数） */
export function fmtNum(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}
