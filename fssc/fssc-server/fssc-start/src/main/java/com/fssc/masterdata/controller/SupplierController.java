package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据 Supplier（/api/tenant/masterdata/supplier/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;

    @RequiresPerm("md:supplier:list")
    @GetMapping("/page")
    public Result<PageResult<MdSupplier>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    /** D-BIZ-08 供应商下拉选择（供资产建档/采购等业务页选择供应商，不需要 md:supplier:list 权限）。
     *  与 /page 同服务逻辑，但不挂 @RequiresPerm——资产建档页面选择供应商是基本功能，
     *  不应要求用户持有供应商主数据管理权限。 */
    @GetMapping("/select")
    public Result<PageResult<MdSupplier>> select(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("md:supplier:create")
    @OperLog(module = "SUPPLIER", operType = "INSERT", desc = "新增Supplier")
    @PostMapping("/create")
    public Result<Long> create(@Validated @RequestBody MdSupplier e) {
        return Result.success(service.create(e));
    }

    /** V2.2.38 B-14 状态位核准（K130：未核准→核准）：PENDING→ACTIVE，零审批流 */
    @RequiresPerm("md:supplier:approve")
    @OperLog(module = "MD_SUPPLIER", operType = "UPDATE", desc = "供应商状态位核准")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        service.approve(id);
        return Result.success();
    }

    @RequiresPerm("md:supplier:update")
    @OperLog(module = "SUPPLIER", operType = "UPDATE", desc = "更新Supplier")
    @PostMapping("/update")
    public Result<Void> update(@Validated @RequestBody MdSupplier e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:supplier:update")
    @OperLog(module = "SUPPLIER", operType = "UPDATE", desc = "Supplier启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:supplier:delete")
    @OperLog(module = "SUPPLIER", operType = "DELETE", desc = "删除Supplier")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    // ================= 41号审查 P1-8：专属页子表(银行账户/联系人/资质证照) =================

    @RequiresPerm("md:supplier:list")
    @GetMapping("/{id}/banks")
    public Result<java.util.List<com.fssc.masterdata.entity.MdSupplierBank>> banks(@PathVariable Long id) {
        return Result.success(service.banks(id));
    }

    @RequiresPerm("md:supplier:update")
    @OperLog(module = "SUPPLIER", operType = "INSERT", desc = "供应商新增银行账户")
    @PostMapping("/{id}/banks")
    public Result<Long> addBank(@PathVariable Long id, @Validated @RequestBody com.fssc.masterdata.entity.MdSupplierBank b) {
        b.setSupplierId(id);
        return Result.success(service.addBank(b));
    }

    @RequiresPerm("md:supplier:update")
    @OperLog(module = "SUPPLIER", operType = "DELETE", desc = "供应商删除银行账户")
    @PostMapping("/banks/{bankId}/delete")
    public Result<Void> deleteBank(@PathVariable Long bankId) {
        service.deleteBank(bankId);
        return Result.success();
    }

    @RequiresPerm("md:supplier:list")
    @GetMapping("/{id}/contacts")
    public Result<java.util.List<com.fssc.masterdata.entity.MdSupplierContact>> contacts(@PathVariable Long id) {
        return Result.success(service.contacts(id));
    }

    @RequiresPerm("md:supplier:update")
    @OperLog(module = "SUPPLIER", operType = "INSERT", desc = "供应商新增联系人")
    @PostMapping("/{id}/contacts")
    public Result<Long> addContact(@PathVariable Long id, @Validated @RequestBody com.fssc.masterdata.entity.MdSupplierContact c) {
        c.setSupplierId(id);
        return Result.success(service.addContact(c));
    }

    @RequiresPerm("md:supplier:update")
    @OperLog(module = "SUPPLIER", operType = "DELETE", desc = "供应商删除联系人")
    @PostMapping("/contacts/{contactId}/delete")
    public Result<Void> deleteContact(@PathVariable Long contactId) {
        service.deleteContact(contactId);
        return Result.success();
    }

    @RequiresPerm("md:supplier:list")
    @GetMapping("/{id}/quals")
    public Result<java.util.List<com.fssc.masterdata.entity.MdSupplierQualification>> quals(@PathVariable Long id) {
        return Result.success(service.quals(id));
    }

    @RequiresPerm("md:supplier:update")
    @OperLog(module = "SUPPLIER", operType = "INSERT", desc = "供应商新增资质证照")
    @PostMapping("/{id}/quals")
    public Result<Long> addQual(@PathVariable Long id, @RequestBody com.fssc.masterdata.entity.MdSupplierQualification q) {
        q.setSupplierId(id);
        return Result.success(service.addQual(q));
    }

    @RequiresPerm("md:supplier:update")
    @OperLog(module = "SUPPLIER", operType = "DELETE", desc = "供应商删除资质证照")
    @PostMapping("/quals/{qualId}/delete")
    public Result<Void> deleteQual(@PathVariable Long qualId) {
        service.deleteQual(qualId);
        return Result.success();
    }
}
