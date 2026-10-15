package com.fssc.asset.controller;

import com.fssc.asset.entity.AssetDeprBatch;
import com.fssc.asset.service.AssetDeprBatchService;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 折旧计提批单（/api/tenant/asset/depr-batch/**，FA_DEPRECIATION 审批载体）。
 *
 * <p>月度折旧计提两阶段：先生成批单并发起 FA_DEPRECIATION 审批流（create/submit），
 * 审批通过事件回调自动执行实际计提；execute 为幂等兜底入口。</p>
 */
@RestController
@RequestMapping("/api/tenant/asset/depr-batch")
@RequiredArgsConstructor
public class AssetDeprBatchController {

    private final AssetDeprBatchService service;

    /** 生成草稿批单（不发起审批） */
    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "INSERT", desc = "生成折旧计提批单")
    @PostMapping("/create")
    public Result<AssetDeprBatch> create(@RequestParam(required = false) String period) {
        requirePeriod(period);
        return Result.success(service.create(period));
    }

    /** 生成批单并发起 FA_DEPRECIATION 审批流（月度计提发起入口） */
    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "INSERT", desc = "发起月度折旧计提审批")
    @PostMapping("/submit")
    public Result<AssetDeprBatch> submit(@RequestParam(required = false) String period) {
        requirePeriod(period);
        return Result.success(service.createAndSubmit(period));
    }

    /**
     * 计提期间必输（必填项治理批次 2B）：留空时 {@code AssetDeprBatchService.create} 会静默按当前月补值，
     * 跨月补提的人以为提的是上月折旧、实际落在本月。守卫只放在人工 HTTP 入口——
     * 月度 Job（AssetDeprAutoJob）与 MdAssetService.depreciateMonth 走服务层传 null 表示"当月"，属正常语义。
     */
    private void requirePeriod(String period) {
        if (period == null || period.isBlank() || !period.matches("\\d{4}-\\d{2}")) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "请选择计提期间（格式 yyyy-MM）");
        }
    }

    /** 对既有草稿批单发起审批 */
    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "发起折旧批单审批")
    @PostMapping("/{id}/submit")
    public Result<Void> submitById(@PathVariable Long id) {
        service.submit(id);
        return Result.success();
    }

    /** 幂等兜底：审批通过后手动执行计提落账 */
    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "执行折旧批单计提")
    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable Long id) {
        service.executeDepr(id);
        return Result.success();
    }

    @RequiresPerm("asset:list")
    @GetMapping("/page")
    public Result<PageResult<AssetDeprBatch>> page(PageQuery query) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize()));
    }

    @RequiresPerm("asset:list")
    @GetMapping("/{id}")
    public Result<AssetDeprBatch> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }
}