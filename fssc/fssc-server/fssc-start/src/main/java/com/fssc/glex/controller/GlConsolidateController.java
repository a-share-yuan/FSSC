package com.fssc.glex.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.GlConsolidate;
import com.fssc.glex.service.GlConsolidateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GL 合并报表底稿（/api/tenant/glex/consolidate/**） */
@RestController
@RequestMapping("/api/tenant/glex/consolidate")
@RequiredArgsConstructor
public class GlConsolidateController {

    private final GlConsolidateService service;

    @RequiresPerm("gl:consolidate:list")
    @GetMapping("/page")
    public Result<PageResult<GlConsolidate>> page(PageQuery query, String period, String status, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), period, status, keyword));
    }

    @RequiresPerm("gl:consolidate:create")
    @OperLog(module = "GL_CONSOLIDATE", operType = "INSERT", desc = "创建合并底稿")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody GlConsolidate e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("gl:consolidate:consolidate")
    @OperLog(module = "GL_CONSOLIDATE", operType = "UPDATE", desc = "汇总合并")
    @PostMapping("/consolidate")
    public Result<Void> consolidate(@RequestParam String period) {
        service.consolidate(period);
        return Result.success();
    }

    @RequiresPerm("gl:consolidate:adjust")
    @OperLog(module = "GL_CONSOLIDATE", operType = "UPDATE", desc = "合并调整分录")
    @PostMapping("/{id}/adjust")
    public Result<Void> adjust(@PathVariable Long id, @RequestParam(required = false) String rawRows) {
        service.adjust(id, rawRows);
        return Result.success();
    }
}