package com.fssc.sharing.controller;

import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.sharing.service.VoucherPushService;
import com.fssc.sharing.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 凭证推送（/api/tenant/voucher/push/**）：推送凭证至 同类系统 / IFS 外部财务系统 */
@RestController
@RequestMapping("/api/tenant/voucher/push")
@RequiredArgsConstructor
@Slf4j
public class VoucherPushController {

    private final VoucherPushService voucherPushService;
    private final VoucherService voucherService;

    /**
     * 推送单张凭证到外部系统。
     */
    @RequiresPerm("sharing:voucher:post")
    @PostMapping("/{voucherId}")
    public Result<Map<String, Object>> push(@PathVariable Long voucherId) {
        log.info("推送凭证到外部系统: voucherId={}", voucherId);
        Map<String, Object> result = voucherPushService.pushToExternal(voucherId);
        return Result.success(result);
    }

    /**
     * 批量推送凭证到外部系统。
     * 请求体格式：{"voucherIds": [1, 2, 3]}
     */
    @RequiresPerm("sharing:voucher:post")
    @PostMapping("/batch")
    public Result<List<Map<String, Object>>> pushBatch(@RequestBody Map<String, List<Long>> request) {
        List<Long> voucherIds = request.get("voucherIds");
        if (voucherIds == null || voucherIds.isEmpty()) {
            return Result.fail(400, "voucherIds不能为空");
        }
        log.info("批量推送凭证到外部系统: voucherIds={}", voucherIds);
        List<Map<String, Object>> results = voucherPushService.pushBatch(voucherIds);
        return Result.success(results);
    }

    /**
     * 查询凭证推送状态。
     */
    @GetMapping("/status/{voucherId}")
    public Result<Map<String, Object>> getPushStatus(@PathVariable Long voucherId) {
        Map<String, Object> status = voucherPushService.getPushStatus(voucherId);
        return Result.success(status);
    }
}