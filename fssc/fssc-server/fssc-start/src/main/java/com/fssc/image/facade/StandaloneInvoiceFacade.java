// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.facade;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.facade.InvoiceFacade;
import com.fssc.framework.facade.dto.InvoicePoolDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 影像服务独立部署时的发票池占位实现（fssc.image.standalone=true 才装配）。
 *
 * <p>影像域识别完成后需要把票面写入主域发票池 {@code inv_invoice}，这一步在单体里
 * 由 invoice 域的 {@code InvoiceFacadeImpl} 完成。独立进程不加载 invoice 域，
 * 因此在此显式失败而不是静默返回：识别结果照常落 {@code ocr_record}，
 * 但页面/日志会得到「未入池」结论，不会出现"已识别却以为已入池"的假象。</p>
 *
 * <p>下一步（影像→发票池服务化）落地后本类删除，改为远程调用。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "fssc.image", name = "standalone", havingValue = "true")
public class StandaloneInvoiceFacade implements InvoiceFacade {

    @Override
    public Long poolFromOcr(InvoicePoolDTO dto) {
        String invoiceNo = dto == null ? null : dto.getInvoiceNo();
        log.warn("影像服务独立运行：发票池服务未接入，票号 {} 仅落 ocr_record", invoiceNo);
        throw new BizException(ErrorCode.SYSTEM_ERROR, "影像服务未接入发票池服务，发票暂未入池（识别记录已保存）");
    }

    @Override
    public boolean existsInPool(InvoicePoolDTO dto) {
        return false;
    }
}
