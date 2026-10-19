// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

// 票据类型编码与中文标签（报销场景）。编码与后端 OcrLocalParsers.inferType 返回值对齐。
// 新增类型需同步修改本文件 与 后端 OcrLocalParsers 的 inferType/常量。
import { dictLabelOf } from '../composables/useDict.js'

/** 本词表对应的字典类型（V1.4.72 起为文案权威源） */
export const INVOICE_DICT_TYPE = 'enum_invoice_type'

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

/**
 * 【V1.4.72】字典优先取词。
 *
 * 为什么必须改走字典：此前本文件（13 型，供下拉）与 enums.js 的 INVOICE_TYPE（8 型，
 * 供 enumLabel 取表格中文）是两份互不引用的词表，后果是——下拉选「增值税专用发票」存库后，
 * 列表列显示成「增值税专用」；而高铁/火车票/机票/出租车/网约车/定额/停车/医疗这 8 类
 * 在 enums.js 里根本没有，enumLabel 兜底直接把 RAIL_HIGH、RIDE_HALLING 等英文码显示给用户。
 * V1.4.72 把两份词表合并到字典 enum_invoice_type（13 正式型 + 6 历史码），两端同读一处。
 *
 * 边界：INVOICE_TYPES 的**键集合**仍由本地常量定（新增票据类型必须后端改 OcrLocalParsers，
 * 必然发版）；字典只负责文案，运营改词后下拉与列表同步生效。
 *
 * fail-soft：字典未加载/为空时回落 MAP，行为与改造前逐字一致，绝不白屏、绝不返回空串。
 */
export function invoiceTypeLabel(code) {
  if (!code) return '—'
  const fromDict = dictLabelOf(INVOICE_DICT_TYPE, code)
  if (fromDict) return fromDict
  return MAP[code] || code
}

/** el-table-column :formatter 适配器，供发票类型列直接挂载，免各页再手写一份词表。 */
export const invoiceTypeFormatter = (_row, _col, cellValue) => invoiceTypeLabel(cellValue)
