package com.fssc.treasury.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.treasury.entity.TrBillSettle;
import com.fssc.treasury.service.TrBillSettleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 票据结算管理（/api/tenant/treasury/bill-settle/**）
 * <p>扩展：资金域写端点补操作审计记录（原全类零 {@code @OperLog}）。
 */
@RestController
@RequestMapping("/api/tenant/treasury/bill-settle")
@RequiredArgsConstructor
public class TrBillSettleController {

    private final TrBillSettleService service;

    @RequiresPerm("treasury:billsettle:list")
    @GetMapping("/page")
    public Result<PageResult<TrBillSettle>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 String status) {
        return Result.success(service.page(pageNum, pageSize, status));
    }

    @RequiresPerm("treasury:billsettle:list")
    @GetMapping("/{id}")
    public Result<TrBillSettle> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }

    @RequiresPerm("treasury:billsettle:create")
    @OperLog(module = "TREASURY", operType = "INSERT", desc = "票据结算-登记票据")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody TrBillSettle settle) {
        return Result.success(service.create(settle));
    }

    /** 背书转让 */
    @RequiresPerm("treasury:billsettle:update")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "票据结算-背书转让")
    @PostMapping("/{id}/endorse")
    public Result<Void> endorse(@PathVariable Long id) {
        service.endorse(id);
        return Result.success();
    }

    /** 贴现 */
    @RequiresPerm("treasury:billsettle:update")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "票据结算-贴现")
    @PostMapping("/{id}/discount")
    public Result<Void> discount(@PathVariable Long id) {
        service.discount(id);
        return Result.success();
    }

    /** 托收到期收妥 */
    @RequiresPerm("treasury:billsettle:update")
    @OperLog(module = "TREASURY", operType = "UPDATE", desc = "票据结算-托收")
    @PostMapping("/{id}/collection")
    public Result<Void> collection(@PathVariable Long id) {
        service.collection(id);
        return Result.success();
    }
}