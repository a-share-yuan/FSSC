package com.fssc.customer.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.service.CustomerService;
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

/** 客户/往来单位管理（/api/tenant/customer/**） */
@RestController
@RequestMapping("/api/tenant/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    // 只读查询放开给所有登录租户用户：对公付款等单据发起页往来单位下拉必用（写操作仍需权限）
    @GetMapping("/page")
    public Result<PageResult<CrmCustomer>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("customer:create")
    @OperLog(module = "CUSTOMER", operType = "INSERT", desc = "新增客户")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody CrmCustomer e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("customer:update")
    @OperLog(module = "CUSTOMER", operType = "UPDATE", desc = "更新客户")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody CrmCustomer e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("customer:update")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @RequiresPerm("customer:update")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    /**
     * V2.2.38 B-14：建档即 PENDING，核准走状态位翻转（PENDING → ACTIVE，幂等）。
     * D-V3-139：前端【核准】按钮自 B-14 上线即调用本端点，但路由一直未登记——
     * service.approve() 成了无暴露路径的死代码，核准通路断头（实测 404 落"目录外动作不可调用"）。
     * 权限沿用 customer:update（核准属档案维护动作，不新设权限点，避免目录漂移）。
     */
    @RequiresPerm("customer:update")
    @OperLog(module = "CUSTOMER", operType = "UPDATE", desc = "核准客户（PENDING→ACTIVE）")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        service.approve(id);
        return Result.success();
    }
}
