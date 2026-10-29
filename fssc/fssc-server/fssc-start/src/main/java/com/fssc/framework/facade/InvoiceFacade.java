// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade;

import com.fssc.common.exception.BizException;
import com.fssc.framework.facade.dto.InvoicePoolDTO;

/**
 * 发票池 Facade：影像域、费用域、共享域、合规域等通过此接口访问发票能力，
 * 不直接依赖 InvInvoiceMapper / InvBillInvoiceMapper / InvoiceService。
 *
 * <p><b>服务化断点</b>：OcrService 位于 image 库路由下（类级 {@code @FsscDataSource("image")}），
 * 而 inv_invoice 在发票域——这是当前唯一的"影像域 → 发票域"跨域调用点。收敛到本接口后，
 * 发票域独立部署时只需把实现换成 HTTP 客户端，OcrService 零改动。
 *
 * <p><b>本接口只保留「入池通道」两个方法</b>（影像域唯一入口）：发票域的查询/勾稽读写已拆到
 * {@link InvoiceDomainFacade}，这样影像服务独立部署时的占位实现不必跟着域内接口膨胀。
 */
public interface InvoiceFacade {

    // 入池（影像域 → 发票域）

    /**
     * 将一张识别完成的票面录入发票池（复用发票域全校验链：价税强校验、租户内硬查重、
     * 全局跨租户查重预警、异常检测）。
     *
     * @param dto 票面数据（含 tenantId）
     * @return 新建发票 ID
     * @throws BizException 校验不通过或发票号重复（调用方应 catch，
     *                                                入池失败不得回滚 OCR 识别记录）
     */
    Long poolFromOcr(InvoicePoolDTO dto);

    /**
     * 去重环节②：入池前先问一次「这张票是不是已经在池」。
     *
     * <p>原先 OCR 链每次识别都无条件调 {@link #poolFromOcr}，同一票面重复上传（客户端断开后重试／
     * 双提交／同一张票传两次）时由发票域 {@code InvoiceService#create} 的查重抛
     * {@code BizException「已存在于发票池」}，而 {@code OcrService} 把这个 catch 当"入池失败"处理
     * ⇒ 一张好票被记成坏票、界面还会提示"要素不全"。现由调用方先问后决定：命中即按"已在池"如实透出，
     * 不再走 {@code poolFromOcr}。</p>
     *
     * <p>规则与 {@code create} 的租户内硬查重同源：同租户 + 同 发票代码（全电票为空串）+ 同发票号码，
     * {@code del_flag=0} 由实体的 {@code @TableLogic} 自动过滤，租户维度由 {@code FsscTenantLineHandler}
     * 自动追加——本方法不做跨租户全局查重（那是 {@code inv_invoice_global} 的预警规则，不阻断入池）。</p>
     *
     * @param dto 票面数据（用到 invoiceCode／invoiceNo 两列；发票号码为空＝无法判定，直接返回 false）
     * @return true＝该票面已在发票池
     */
    boolean existsInPool(InvoicePoolDTO dto);
}
