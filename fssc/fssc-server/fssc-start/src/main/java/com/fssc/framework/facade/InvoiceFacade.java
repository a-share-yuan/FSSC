package com.fssc.framework.facade;

import com.fssc.framework.facade.dto.InvoicePoolDTO;

/**
 * 发票池 Facade：影像域（及未来的文档域服务）通过此接口把 OCR 识别到的票面入池，
 * 不直接依赖 InvInvoice 实体与 InvoiceService。
 *
 * <p><b>服务化断点</b>：OcrService 位于 image 库路由下（类级 {@code @FsscDataSource("image")}），
 * 而 inv_invoice 在 master 库——这是当前唯一的"影像域 → 主域"跨域调用点。收敛到本接口后，
 * 影像档案独立部署时只需把实现换成 HTTP 客户端，OcrService 零改动。
 */
public interface InvoiceFacade {

    /**
     * 将一张识别完成的票面录入发票池（复用主域全校验链：价税强校验、租户内硬查重、
     * 全局跨租户查重预警、异常检测）。
     *
     * @param dto 票面数据（含 tenantId）
     * @return 新建发票 ID
     * @throws com.fssc.common.exception.BizException 校验不通过或发票号重复（调用方应 catch，
     *                                                入池失败不得回滚 OCR 识别记录）
     */
    Long poolFromOcr(InvoicePoolDTO dto);

    /**
     * D-DEC-463（幂等腿②）：入池前先问一次「这张票是不是已经在池」。
     *
     * <p>改前 OCR 链每次识别都无条件调 {@link #poolFromOcr}，同一票面重复上传（客户端断开后重试／
     * 双提交／同一张票传两次）时由主域 {@code InvoiceService#create} 的查重抛
     * {@code BizException「已存在于发票池」}，而 {@code OcrService} 把这个 catch 当"入池失败"处理
     * ⇒ 一张好票被记成坏票、界面还会提示"要素不全"。现由调用方先问后决定：命中即按"已在池"如实透出，
     * 不再走 {@code poolFromOcr}。</p>
     *
     * <p>口径与 {@code create} 的租户内硬查重同源：同租户 + 同 发票代码（全电票为空串）+ 同发票号码，
     * {@code del_flag=0} 由实体的 {@code @TableLogic} 自动过滤，租户维度由 {@code FsscTenantLineHandler}
     * 自动追加——本方法不做跨租户全局查重（那是 {@code inv_invoice_global} 的预警口径，不阻断入池）。</p>
     *
     * @param dto 票面数据（用到 invoiceCode／invoiceNo 两列；发票号码为空＝无法判定，直接返回 false）
     * @return true＝该票面已在发票池
     */
    boolean existsInPool(InvoicePoolDTO dto);
}
