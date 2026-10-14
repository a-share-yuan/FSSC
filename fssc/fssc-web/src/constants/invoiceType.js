// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

// 票据类型编码与中文标签（报销场景）。编码与后端 OcrLocalParsers.inferType 返回值对齐。
// 新增类型需同步修改本文件 与 后端 OcrLocalParsers 的 inferType/常量。
export const INVOICE_TYPES = [
  { code: 'VAT_SPECIAL', label: '增值税专用发票' },
  { code: 'VAT_NORMAL', label: '增值税普通发票' },
  { code: 'ELECTRONIC', label: '电子发票' },
  { code: 'RAIL_HIGH', label: '高铁 / 动车' },
  { code: 'TRAIN', label: '火车票' },
  { code: 'AIR', label: '航空运输电子客票' },
  { code: 'TAXI', label: '出租车' },
  { code: 'RIDE_HALLING', label: '网约车' },
  { code: 'QUOTA', label: '定额发票' },
  { code: 'TOLL', label: '通行费 / ETC' },
  { code: 'PARKING', label: '停车费' },
  { code: 'MEDICAL', label: '医疗收费票据' },
  { code: 'OTHER', label: '其他' },
]

// code → 中文标签；兼容历史编码（VAT/VATN/TRAIN 为旧值）
const LEGACY = { VAT: '增值税专票', VATN: '增值税普票', TRAIN: '火车票', 增专: '增值税专用发票', 增普: '增值税普通发票' } // R5-D-101：历史中文值兜底显示（新写入统一字典码 VAT_SPECIAL/VAT_NORMAL）

const MAP = INVOICE_TYPES.reduce((m, it) => { m[it.code] = it.label; return m }, {})
Object.assign(MAP, LEGACY)

export function invoiceTypeLabel(code) {
  if (!code) return '—'
  return MAP[code] || code
}

/** el-table-column :formatter 适配器，供发票类型列直接挂载，免各页再手写一份词表。 */
export const invoiceTypeFormatter = (_row, _col, cellValue) => invoiceTypeLabel(cellValue)