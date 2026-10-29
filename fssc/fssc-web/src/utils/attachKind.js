// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * 附件类型判定：发票影像（要识别）vs 普通附件（不识别）。
 *
 * ## 为什么需要这个文件
 * 「选择文件」按钮的 accept 只是文件选择器的过滤器，
 * 用户可以切到「所有文件」、也可以拖拽/粘贴进来，Office、压缩包照样能传上去。
 * 而附件列表的 OCR 角标若不看文件类型，就一律默认「待识别」——
 * 一份 .docx 合同、一个 .zip 附件挂个「待识别」，等于让用户去等一个永远不会发生的识别。
 *
 * ## 判定规则（必须与后端逐字一致）
 * 后端 `ExpenseAttachController.upload()`：只有
 *   `OcrLocalParsers.isLocalFormat(fileName)`（pdf / ofd / xml）
 *   或 `IMAGE_EXTS`（jpg / jpeg / png / bmp / webp / tif / tiff）
 * 才回 `attachType=INVOICE`，其余一律 `attachType=OTHER`、`invoice=null`。
 * 前端改这里而不同步后端，就会出现「界面说有票、后端根本没解析」的规则分裂。
 *
 * ## 取值优先级
 * 优先用后端返回的 `attachType`（权威，单据写入数据库后重进页面也能拿到）；
 * 缺失时才按扩展名兜底（老数据 / 手工构造的行可能没有该字段）。
 */

/** 走图像 OCR 通道的图片类发票（与后端 IMAGE_EXTS 一致） */
export const IMAGE_INVOICE_EXTS = ['jpg', 'jpeg', 'png', 'bmp', 'webp', 'tif', 'tiff']
/** 走本地版式解析的版式文件（与后端 OcrLocalParsers 一致） */
export const LAYOUT_INVOICE_EXTS = ['pdf', 'ofd', 'xml']
/** 发票影像扩展名全集 */
export const INVOICE_EXTS = [...LAYOUT_INVOICE_EXTS, ...IMAGE_INVOICE_EXTS]
/** 普通附件常见扩展名（仅用于文件选择器放行，不参与识别判定） */
export const PLAIN_ATTACH_EXTS = [
  'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'csv', 'txt', 'rtf',
  'zip', 'rar', '7z', 'eml', 'msg',
]

/** 取扩展名（小写，不含点）；无扩展名返回 '' */
export function fileExtOf(name) {
  const n = String(name || '')
  const dot = n.lastIndexOf('.')
  return dot < 0 ? '' : n.slice(dot + 1).toLowerCase()
}

/** 按扩展名判断是否发票影像（= 该识别） */
export function isInvoiceFile(name) {
  return INVOICE_EXTS.includes(fileExtOf(name))
}

/** 按扩展名判断是否图片（用于缩略图与图片 OCR 入口） */
export function isImageFile(name) {
  return IMAGE_INVOICE_EXTS.includes(fileExtOf(name))
}

/** 是否 Office / 压缩包 / 纯文本这类「只能当附件看」的文件 */
export function isPlainAttachFile(name) {
  return PLAIN_ATTACH_EXTS.includes(fileExtOf(name))
}

/**
 * 附件定性：'INVOICE' 需识别 / 'OTHER' 普通附件。
 * @param {object|string} attach 附件行（含 attachType/fileName）或直接传文件名
 */
export function attachKindOf(attach) {
  if (!attach) return 'OTHER'
  const row = typeof attach === 'string' ? { fileName: attach } : attach
  if (row.attachType) return row.attachType === 'INVOICE' ? 'INVOICE' : 'OTHER'
  return isInvoiceFile(row.fileName) ? 'INVOICE' : 'OTHER'
}

/** 附件定性文案（列表/详情共用，避免各处自造词） */
export const ATTACH_KIND_TEXT = { INVOICE: '发票', OTHER: '普通附件' }

/**
 * 文件选择器 accept：发票影像 + 常见普通附件。
 * accept 只是过滤器、不是校验——用户可以切「所有文件」或拖拽绕过，
 *    真正定性一律走 attachKindOf / 后端 attachType。
 */
export const ATTACH_ACCEPT = '.' + [...INVOICE_EXTS, ...PLAIN_ATTACH_EXTS].join(',.')
