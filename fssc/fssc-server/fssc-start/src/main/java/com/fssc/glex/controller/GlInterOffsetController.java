package com.fssc.glex.controller;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.GlInterOffset;
import com.fssc.glex.service.GlInterOffsetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GL 内部往来抵消（/api/tenant/glex/inter-offset/**） */
@RestController
@RequestMapping("/api/tenant/glex/inter-offset")
@RequiredArgsConstructor
public class GlInterOffsetController {

    private final GlInterOffsetService service;

    @RequiresPerm("gl:inter:list")
    @GetMapping("/page")
    public Result<PageResult<GlInterOffset>> page(PageQuery query, String period, String interFlag, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), period, interFlag, keyword));
    }

    @RequiresPerm("gl:inter:offset")
    @OperLog(module = "GL_INTER_OFFSET", operType = "INSERT", desc = "内部往来抵消生成")
    @PostMapping("/generate")
    public Result<List<GlInterOffset>> generate(@RequestParam String period) {
        return Result.success(service.generateOffset(period));
    }

    @RequiresPerm("gl:inter:offset")
    @OperLog(module = "GL_INTER_OFFSET", operType = "INSERT", desc = "内部往来抵消手工录入")
    @PostMapping("/create")
    public Result<List<GlInterOffset>> create(@RequestBody List<GlInterOffset> pair) {
        if (pair == null || pair.size() != 2) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "需成对提交来账(应收)与往账(应付)两条分录");
        }
        return Result.success(service.create(pair.get(0), pair.get(1)));
    }

    @RequiresPerm("gl:inter:offset")
    @OperLog(module = "GL_INTER_OFFSET", operType = "UPDATE", desc = "抵消记账")
    @PostMapping("/{id}/post")
    public Result<Void> post(@PathVariable Long id) {
        service.post(id);
        return Result.success();
    }
}