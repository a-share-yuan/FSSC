// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

import { describe, it, expect } from 'vitest'
import {
  attachKindOf, isInvoiceFile, isImageFile, isPlainAttachFile, fileExtOf, ATTACH_ACCEPT,
} from './attachKind'

/**
 * 附件定性口径：发票影像（要识别）vs 普通附件（不识别）。
 * 与后端 ExpenseAttachController 一致 = pdf/ofd/xml + jpg/jpeg/png/bmp/webp/tif/tiff。
 * 这个用例是为了守住「上传 Office 后显示待识别」这类回归。
 */
describe('attachKind - 按后缀/后端类型定性', () => {
  it('发票影像格式判定为 INVOICE', () => {
    for (const n of ['a.pdf', 'a.OFD', 'a.xml', 'a.jpg', 'a.JPEG', 'a.png', 'a.bmp', 'a.webp', 'a.tif', 'a.tiff']) {
      expect(isInvoiceFile(n), n).toBe(true)
      expect(attachKindOf({ fileName: n }), n).toBe('INVOICE')
    }
  })

  it('Office / 压缩包 / 文本等普通附件判定为 OTHER', () => {
    for (const n of ['合同.docx', '明细.xlsx', '汇报.pptx', '说明.txt', '数据.csv', '资料.zip', 'a.rar', 'a.7z', 'a.doc']) {
      expect(isInvoiceFile(n), n).toBe(false)
      expect(attachKindOf({ fileName: n }), n).toBe('OTHER')
      expect(isPlainAttachFile(n), n).toBe(true)
    }
  })

  it('后端 attachType 优先于后缀（权威值不被文件名推翻）', () => {
    expect(attachKindOf({ attachType: 'INVOICE', fileName: 'x.docx' })).toBe('INVOICE')
    expect(attachKindOf({ attachType: 'OTHER', fileName: 'x.pdf' })).toBe('OTHER')
  })

  it('attachType 缺失时按后缀兜底（老数据）', () => {
    expect(attachKindOf({ fileName: 'invoice.pdf' })).toBe('INVOICE')
    expect(attachKindOf({ fileName: 'contract.docx' })).toBe('OTHER')
    expect(attachKindOf({ fileName: 'noext' })).toBe('OTHER')
    expect(attachKindOf(null)).toBe('OTHER')
  })

  it('图片识别（缩略图用）与发票图片同口径', () => {
    expect(isImageFile('a.JPG')).toBe(true)
    expect(isImageFile('a.pdf')).toBe(false)
  })

  it('扩展名解析容错', () => {
    expect(fileExtOf('A.PDF')).toBe('pdf')
    expect(fileExtOf('archive.tar.gz')).toBe('gz')
    expect(fileExtOf('')).toBe('')
  })

  it('accept 同时放行发票与普通附件', () => {
    expect(ATTACH_ACCEPT).toContain('.pdf')
    expect(ATTACH_ACCEPT).toContain('.docx')
    expect(ATTACH_ACCEPT).toContain('.zip')
  })
})
