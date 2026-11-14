package com.fssc.asset.controller;

import com.fssc.asset.entity.MdAsset;
import com.fssc.asset.entity.MdAssetChangeLog;
import com.fssc.asset.service.MdAssetService;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 资产卡片（/api/tenant/asset/**）：建档/报废/逐月折旧/净值（FA 投资到资产-固定资产） */
@RestController
@RequestMapping("/api/tenant/asset")
@RequiredArgsConstructor
public class MdAssetController {

    private final MdAssetService service;

    @RequiresPerm("asset:list")
    @GetMapping("/page")
    public Result<PageResult<MdAsset>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("asset:create")
    @OperLog(module = "ASSET", operType = "INSERT", desc = "资产建档")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdAsset e) {
        service.requireManualCard(e);
        return Result.success(service.create(e));
    }

    @RequiresPerm("asset:list")
    @GetMapping("/{id}")
    public Result<MdAsset> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    /** 单卡变动台账（原值调整/盘点差异的原因、前后镜像、审批最终状态），最新在前 */
    @RequiresPerm("asset:list")
    @GetMapping("/{id}/change-logs")
    public Result<List<MdAssetChangeLog>> changeLogs(@PathVariable Long id) {
        return Result.success(service.changeLogs(id));
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "更新资产")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdAsset e) {
        service.requireManualCard(e);
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "DELETE", desc = "删除资产")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "资产报废")
    @PostMapping("/{id}/scrap")
    public Result<Void> scrap(@PathVariable Long id) {
        service.scrap(id);
        return Result.success();
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "计提本月折旧")
    @PostMapping("/{id}/depreciate")
    public Result<MdAsset> depreciate(@PathVariable Long id) {
        return Result.success(service.depreciate(id));
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "月度折旧批处理")
    @PostMapping("/depreciate/batch")
    public Result<Integer> depreciateMonth() {
        return Result.success(service.depreciateMonth());
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "资产调拨")
    @PostMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id, Long targetDeptId, Long targetCompanyId, String remark) {
        service.transfer(id, targetDeptId, targetCompanyId, remark);
        return Result.success();
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "资产对外销售处置")
    @PostMapping("/{id}/disposeSell")
    public Result<Void> disposeSell(@PathVariable Long id, BigDecimal sellAmount) {
        service.disposeSell(id, sellAmount);
        return Result.success();
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "资产盘点差异处理")
    @PostMapping("/{id}/checkDiff")
    public Result<Void> checkDiff(@PathVariable Long id, BigDecimal diffAmount, String remark) {
        service.checkDiff(id, diffAmount, remark);
        return Result.success();
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "资产原值调整")
    @PostMapping("/{id}/adjustValue")
    public Result<Void> adjustValue(@PathVariable Long id, BigDecimal newOriginalAmount, String reason) {
        service.adjustValue(id, newOriginalAmount, reason);
        return Result.success();
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "资产减值准备")
    @PostMapping("/{id}/impair")
    public Result<Void> impair(@PathVariable Long id, BigDecimal impairmentAmount) {
        service.impair(id, impairmentAmount);
        return Result.success();
    }

    @RequiresPerm("asset:update")
    @OperLog(module = "ASSET", operType = "UPDATE", desc = "在建工程转固定资产")
    @PostMapping("/{id}/cipToAsset")
    public Result<Void> cipToAsset(@PathVariable Long id, String transferDate, String remark) {
        service.cipToAsset(id, transferDate, remark);
        return Result.success();
    }

    @RequiresPerm("asset:create")
    @OperLog(module = "ASSET", operType = "INSERT", desc = "资本性支出申请")
    @PostMapping("/capitalRequest")
    public Result<Void> capitalRequest(BigDecimal amount, String assetName, Long deptId, Long companyId, String remark,
                                       Long budgetItemId) {
        // budgetItemId 可选（为空自动取综合预算兜底科目）；发起即硬控校验，审批通过后占用
        service.capitalRequest(amount, assetName, deptId, companyId, remark, budgetItemId);
        return Result.success();
    }

    @RequiresPerm("asset:list")
    @GetMapping("/{id}/netValue")
    public Result<BigDecimal> netValue(@PathVariable Long id) {
        return Result.success(service.netValue(id));
    }

    /**
     * #16 税会差异清单（A105080 规则）：仅返回会计年限与税法年限不同、存在折旧差异的资产。
     * diffAmount > 0（会计折旧 > 税法折旧）→ 纳税调增；< 0 → 纳税调减。
     */
    @RequiresPerm("asset:list")
    @GetMapping("/tax-diff")
    public Result<Object> taxDiff(@RequestParam(required = false) Integer pageNum,
                                  @RequestParam(required = false) Integer pageSize) {
        return Result.success(service.taxDiff(pageNum, pageSize));
    }
}