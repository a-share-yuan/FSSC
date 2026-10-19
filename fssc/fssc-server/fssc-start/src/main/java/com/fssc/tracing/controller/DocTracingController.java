package com.fssc.tracing.controller;

import com.fssc.common.core.Result;
import com.fssc.tracing.dto.DocTraceChain;
import com.fssc.tracing.service.DocTracingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 单据全链路溯源控制器（P2-1 页面勾稽联查）。
 */
@RestController
@RequestMapping("/api/tenant/doc-tracing")
@RequiredArgsConstructor
public class DocTracingController {

    private final DocTracingService docTracingService;

    /**
     * 全链路联查：单据→发票→凭证→付款→银行对账→档案。
     *
     * @param sourceDocType 来源单据类型（EXP_BILL/PO_ORDER/INV_INVOICE/RC_RECEIVABLE等）
     * @param sourceDocId   来源单据ID
     */
    @GetMapping("/trace")
    public Result<DocTraceChain> trace(@RequestParam String sourceDocType,
                                       @RequestParam Long sourceDocId) {
        return Result.success(docTracingService.trace(sourceDocType, sourceDocId));
    }
}
