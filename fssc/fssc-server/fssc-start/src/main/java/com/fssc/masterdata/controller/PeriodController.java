package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdPeriod;
import com.fssc.masterdata.service.PeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 Period（/api/tenant/masterdata/period/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/period")
@RequiredArgsConstructor
public class PeriodController {

    private final PeriodService service;

    /**
     * R5-D-042：列表补公司维度。{@code companyId} 是"显式筛选"入参，服务端仍叠加登录人
     * data_scope 谓词（见 {@code PeriodService.page}），因此传别的公司号也不会放宽可见范围。
     */
    @RequiresPerm("md:period:list")
    @GetMapping("/page")
    public Result<PageResult<MdPeriod>> page(PageQuery query, String keyword,
                                             @RequestParam(required = false) Long companyId) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword, companyId));
    }

    @RequiresPerm("md:period:create")
    @OperLog(module = "PERIOD", operType = "INSERT", desc = "新增Period")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdPeriod e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:period:update")
    @OperLog(module = "PERIOD", operType = "UPDATE", desc = "更新Period")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdPeriod e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:period:update")
    @OperLog(module = "PERIOD", operType = "UPDATE", desc = "Period启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:period:delete")
    @OperLog(module = "PERIOD", operType = "DELETE", desc = "删除会计期间")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @RequiresPerm("md:period:close")
    @OperLog(module = "PERIOD", operType = "UPDATE", desc = "会计期间结账")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        service.close(id);
        return Result.success();
    }

    @RequiresPerm("md:period:close")
    @OperLog(module = "PERIOD", operType = "UPDATE", desc = "会计期间反结账")
    @PostMapping("/{id}/reopen")
    public Result<Void> reopen(@PathVariable Long id) {
        service.reopen(id);
        return Result.success();
    }
}
