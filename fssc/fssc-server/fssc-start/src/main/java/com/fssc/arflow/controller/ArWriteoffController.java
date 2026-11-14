package com.fssc.arflow.controller;

import com.fssc.arflow.entity.ArWriteoffFund;
import com.fssc.arflow.entity.ArWriteoffRecord;
import com.fssc.arflow.service.ArWriteoffService;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.receivable.entity.RcReceiptLine;
import com.fssc.receivable.entity.RcReceivable;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 核销工作台 OpenAPI（/api/tenant/ar-flow/writeoff/**，蓝图核销管理 9.4）：
 * 待核销发票 × 收款/票据资金池 → 手动匹配 / 自动核销(FIFO) → 生成收款核销单并冲减；
 * 对冲核销（借2202/贷1122，债务抵消协议）；核销查询。
 */
@RestController
@RequestMapping("/api/tenant/ar-flow/writeoff")
@RequiredArgsConstructor
public class ArWriteoffController {

    private final ArWriteoffService writeoffService;

    // 资金池

    @RequiresPerm("arflow:writeoff:manage")
    @GetMapping("/funds")
    public Result<List<ArWriteoffFund>> funds(@RequestParam(required = false) String fundType,
                                              @RequestParam(required = false) String status) {
        return Result.success(writeoffService.funds(fundType, status));
    }

    /** 资金登记（收款/票据来源，票据核销带 noteId） */
    @OperLog(module = "AR_FLOW", operType = "ADD", desc = "核销资金登记")
    @RequiresPerm("arflow:writeoff:manage")
    @PostMapping("/funds")
    public Result<ArWriteoffFund> registerFund(@RequestBody ArWriteoffFund fund) {
        return Result.success(writeoffService.registerFund(fund));
    }

    // 待核销发票

    @RequiresPerm("arflow:writeoff:manage")
    @GetMapping("/pending-invoices")
    public Result<List<RcReceivable>> pendingInvoices(@RequestParam(required = false) Long customerId,
                                                      @RequestParam(required = false) String keyword) {
        return Result.success(writeoffService.pendingInvoices(customerId, keyword));
    }

    /** 自动核销（FIFO 按到期日）：{fundId} */
    @OperLog(module = "AR_FLOW", operType = "UPDATE", desc = "自动核销(FIFO)")
    @RequiresPerm("arflow:writeoff:manage")
    @PostMapping("/auto-match")
    public Result<ArWriteoffRecord> autoMatch(@RequestBody Map<String, Object> body) {
        return Result.success(writeoffService.autoMatch(Long.valueOf(String.valueOf(body.get("fundId")))));
    }

    /** 手动匹配核销：{fundId, lines:[{receivableId, offsetAmount}]} */
    @OperLog(module = "AR_FLOW", operType = "UPDATE", desc = "手动匹配核销")
    @RequiresPerm("arflow:writeoff:manage")
    @PostMapping("/manual-match")
    public Result<ArWriteoffRecord> manualMatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawLines = (List<Map<String, Object>>) body.get("lines");
        List<RcReceiptLine> lines = new ArrayList<>();
        if (rawLines != null) {
            for (Map<String, Object> raw : rawLines) {
                RcReceiptLine l = new RcReceiptLine();
                l.setReceivableId(Long.valueOf(String.valueOf(raw.get("receivableId"))));
                l.setOffsetAmount(new BigDecimal(String.valueOf(raw.get("offsetAmount"))));
                lines.add(l);
            }
        }
        return Result.success(writeoffService.manualMatch(
                Long.valueOf(String.valueOf(body.get("fundId"))), lines));
    }

    /** 对冲核销：{customerId, customerName, amount, agreementNo, lines?} */
    @OperLog(module = "AR_FLOW", operType = "UPDATE", desc = "应收应付对冲核销")
    @RequiresPerm("arflow:writeoff:manage")
    @PostMapping("/offset")
    public Result<ArWriteoffRecord> offset(@RequestBody Map<String, Object> body) {
        return Result.success(writeoffService.offsetWriteoff(
                Long.valueOf(String.valueOf(body.get("customerId"))),
                (String) body.get("customerName"),
                new BigDecimal(String.valueOf(body.get("amount"))),
                (String) body.get("agreementNo"),
                null));
    }

    // 核销查询

    @RequiresPerm("arflow:writeoff:manage")
    @GetMapping("/records")
    public Result<List<ArWriteoffRecord>> records(@RequestParam(required = false) String writeoffType,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String keyword) {
        return Result.success(writeoffService.records(writeoffType, status, keyword));
    }
}
