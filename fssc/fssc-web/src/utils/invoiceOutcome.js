// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

/**
 * ＝票面识别结果的唯一分档口：「已识别」≠「已入发票池」。
 *
 * 原始缺陷＝后端 `OcrService.recognizeAndRecord` 把入池异常吞成一条 `log.warn`，返回值仍是 `ok=true`，
 * 前端只看 `invoice.ok` 就弹「已识别发票」，而 `inv_invoice` 里根本没有这张票 ⇒
 * 用户以为票进了票夹、提交后在「发票」里找不到它。
 *
 * 现在后端把结论写进 `invoice.inPool`（true／false／null）与 `invoice.errorMsg`，本文件只读
 * `ok`／`inPool`／`errorMsg` 三格分档。不在前端重算"金额自洽／发票号有无"——
 * 判据两处各写一份迟早漂移。
 *
 * 四档（③ 是本地版式解析支，其余三档如实透出后端结论）：
 *  1. POOLED ok=true 且 inPool===true ⇒ 票已进发票池，可在「发票」里勾选引用
 *  2. NOT_POOLED ok=true 且 inPool===false ⇒ 拿到票面但没进池（要素不全／金额不自洽／入池被拒），原因见 errorMsg
 *  3. PARSED_ONLY ok=true 且 inPool 为 null ⇒ 版式文件（OFD／XML／带文本层 PDF）本地解析：只回填票面、不走票池
 *  4. FAILED ok=false 或响应无 invoice ⇒ 未识别，附件已保存并带「发票」标记
 *  5. POOL_UNDETERMINED＝中间态兜底档，见文末说明（严格说是 ③ 的限定版，不改变 ①~④ 的判定）
 *
 * 档位判定一律用 `=== true`／`=== false`，不要写成 `!invoice.inPool` 之类的真值判断
 *   ＝把"没走写入数据库链"（③，正常路径）说成"要素不全"（②，要用户去补录），两端文案会互相打脸。
 *
 * 端差＝`end`：PC 有「已解析发票」清单（勾选即填明细行），手机端没有这张清单、
 *   只有票夹（＝发票池）勾选口 ⇒ 同一档位给两条不同的出路，不要共用一句"去下方清单勾选"。
 *
 * 中间态＝`POOL_UNDETERMINED`：`inPool` 是后加的字段，只有新后端会透出来。
 *   前端先热更、后端还没发布的那段时间里，所有 `ok=true` 的响应都拿不到 `inPool`。
 *   若照 `inPool==null ⇒ 版式文件本地解析` 直接分档，图片票明明已被旧版后端入池，
 *   却会被报成「版式文件走本地解析，不入发票池」＝一条假文案。
 *   ⇒ 判据＝`inPool` 缺失且文件名是图片扩展名（图片票永远不走本地版式解析）时走本档，
 *   只说"标记未透出、以发票列表为准"，不宣称入池、不宣称未入池；版式文件仍走 ③。
 *
 * 本档现状＝旧代响应／回滚窗兜底专用：后端早已随包透出 `inPool`，图片票正常路径
 *   必得 `true`／`false`，本档不再命中。保留不删的理由是回滚链里还挂着旧代 jar，
 *   一旦回滚本档重新成为唯一不给假文案的档；别把它当"现役中间态"再写进验收出证。
 */

export const INVOICE_TIERS = {
  POOLED: 'POOLED',
  NOT_POOLED: 'NOT_POOLED',
  PARSED_ONLY: 'PARSED_ONLY',
  FAILED: 'FAILED',
  POOL_UNDETERMINED: 'POOL_UNDETERMINED',
}

/** 图片扩展名＝必定走图像识别通道、必定不会有本地版式解析支 */
const IMAGE_EXT_RE = /\.(jpe?g|png|bmp|gif|webp|tiff?)$/i

/**
 * @param invoice 响应体 `rec.invoice`（OcrResult 的 JSON），可为 null
 * @param opts `{ fileName, end: 'pc' | 'h5' }`
 * @returns {{tier: string, tone: 'success'|'warning'|'info', message: string}}
 */
export function invoiceOutcome(invoice, opts = {}) {
  const name = opts.fileName || ''
  const h5 = opts.end === 'h5'

  if (!invoice || invoice.ok !== true) {
    const reason = invoice && invoice.errorMsg ? `（${invoice.errorMsg}）` : ''
    return {
      tier: INVOICE_TIERS.FAILED,
      tone: 'warning',
      message: '附件已保存并标记为「发票」，票面未识别出来' + reason + '。'
        + '可改传 OFD／XML 版式文件重新上传，或在费用明细里手工填写金额后提交',
    }
  }

  const no = invoice.invoiceNo || name || '这张票'

  if (invoice.inPool === true) {
    return {
      tier: INVOICE_TIERS.POOLED,
      tone: 'success',
      message: `已识别并入发票池：${no}` + (h5
        ? '｜可在「发票」里勾选这张票'
        : '｜票已在发票池，可在「发票」字段或「已解析发票」清单里引用'),
    }
  }

  if (invoice.inPool === false) {
    return {
      tier: INVOICE_TIERS.NOT_POOLED,
      tone: 'warning',
      message: `已识别但没进发票池：${no}（${invoice.errorMsg || '票面要素不全'}）｜`
        + (h5
          ? '手机端只能引用发票池里的票，这张请在费用明细里核对金额，或到电脑端「发票管理 › 发票补录」补录'
          : '票面已回填「已解析发票」清单，可勾选填明细；要进发票池请到「发票管理 › 发票补录」补录'),
    }
  }

  // 旧代响应／回滚窗兜底专用（现役 jar 已透出 `inPool`，见文件头 段）：
  // 只在"后端回了旧代响应体、这一格缺失"时命中；不据此判定入池与否。
  if (invoice.inPool == null && IMAGE_EXT_RE.test(name)) {
    return {
      tier: INVOICE_TIERS.POOL_UNDETERMINED,
      tone: 'info',
      message: `已识别票面：${no}｜本次响应未带「是否已入发票池」的标记，`
        + (h5
          ? '请先在「发票」列表刷新核对这张票，别重复上传同一张票面'
          : '请先在「发票管理」或「已解析发票」清单里核对这张票，别重复上传同一张票面'),
    }
  }

  return {
    tier: INVOICE_TIERS.PARSED_ONLY,
    tone: 'success',
    message: `已解析票面：${no}｜版式文件走本地解析，只回填票面、不入发票池` + (h5
      ? '，请在费用明细里核对金额'
      : '，请在「已解析发票」清单勾选以填入费用明细'),
  }
}

/**
 * ＝客户端断开后的结果未知明示。
 *
 * 原始缺陷＝「上传＋识别」在同一请求里串行，而后端识别链是 `REQUIRES_NEW` 独立事务 ⇒
 * 浏览器关掉／请求中断不会终止服务端写入（关掉页面后仍会落 `ocr_record`、仍入池
 * ＝一张零响应的幽灵票）。前端把这种情况报成"上传失败"，用户再传一次 ⇒ 叠记录。
 *
 * 文案规则＝只说"结果未知"＋给核对路径，不谎报失败、不承诺"已取消"（服务端根本没收到取消信号）。
 */
export function abortedOutcome(fileName) {
  return {
    tier: 'ABORTED',
    tone: 'warning',
    message: `已中断等待：${fileName || '本次上传'}｜服务端可能仍在识别并写入，结果未知。`
      + '请先在「发票」列表刷新核对是否已有这张票，别重复上传同一张票面',
  }
}
