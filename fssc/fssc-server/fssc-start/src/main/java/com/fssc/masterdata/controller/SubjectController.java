package com.fssc.masterdata.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 主数据 Subject（/api/tenant/masterdata/subject/**） */
@RestController
@RequestMapping("/api/tenant/masterdata/subject")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService service;

    /**
     * 顺带修 转注态①：列表/树补 companyId 显式筛选入参，
     * 照 PeriodController 的现成模式——服务端仍叠加登录人 data_scope 谓词（见 {@code SubjectService.page/tree}），
     * 传别的公司号也不会放宽可见范围。
     */
    @RequiresPerm("md:subject:list")
    @GetMapping("/page")
    public Result<PageResult<MdSubject>> page(PageQuery query, String keyword,
                                              @RequestParam(required = false) Long companyId) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword, companyId));
    }

    @RequiresPerm("md:subject:list")
    @GetMapping("/tree")
    public Result<List<MdSubject>> tree(@RequestParam(required = false) Long companyId) {
        return Result.success(service.tree(companyId));
    }

    /**
     * 可选叶子科目（启用＋末级＋本公司/租户共用）：费用项目「对应会计科目」下拉的数据源。
     * 同时放开费用项目维护权限，否则只有科目权限的人能配、能配费用项的人反而看不到选项。
     */
    @RequiresPerm({"md:subject:list", "md:expenseitem:create", "md:expenseitem:update"})
    @GetMapping("/options")
    public Result<List<MdSubject>> options(@RequestParam(required = false) Long companyId,
                                           @RequestParam(required = false) String keyword) {
        return Result.success(service.options(companyId, keyword));
    }
    @RequiresPerm("md:subject:create")
    @OperLog(module = "SUBJECT", operType = "INSERT", desc = "新增Subject")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody MdSubject e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("md:subject:update")
    @OperLog(module = "SUBJECT", operType = "UPDATE", desc = "更新Subject")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody MdSubject e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("md:subject:update")
    @OperLog(module = "SUBJECT", operType = "UPDATE", desc = "Subject启用/停用")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id, @RequestParam boolean enable) {
        service.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm("md:subject:delete")
    @OperLog(module = "SUBJECT", operType = "DELETE", desc = "删除会计科目")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
