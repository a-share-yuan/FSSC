package com.fssc.system.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysNotice;
import com.fssc.system.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 通知公告（/api/tenant/system/notice/**）。 */
@RestController
@RequestMapping("/api/tenant/system/notice")
@RequiredArgsConstructor
public class TenantNoticeController {

    private final NoticeService noticeService;

    @RequiresPerm("system:notice:list")
    @GetMapping("/page")
    public Result<PageResult<SysNotice>> page(PageQuery query, String keyword, String status) {
        return Result.success(noticeService.page(query.getPageNum(), query.getPageSize(), keyword, status));
    }

    @RequiresPerm("system:notice:list")
    @GetMapping("/{id}")
    public Result<SysNotice> detail(@PathVariable Long id) {
        return Result.success(noticeService.detail(id));
    }

    @RequiresPerm("system:notice:create")
    @OperLog(module = "SYSTEM_NOTICE", operType = "INSERT", desc = "新增公告")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody SysNotice notice) {
        return Result.success(noticeService.create(notice));
    }

    @RequiresPerm("system:notice:update")
    @OperLog(module = "SYSTEM_NOTICE", operType = "UPDATE", desc = "更新公告")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody SysNotice notice) {
        noticeService.update(notice);
        return Result.success();
    }

    @RequiresPerm("system:notice:update")
    @OperLog(module = "SYSTEM_NOTICE", operType = "UPDATE", desc = "发布公告")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        noticeService.publish(id);
        return Result.success();
    }

    @RequiresPerm("system:notice:update")
    @OperLog(module = "SYSTEM_NOTICE", operType = "UPDATE", desc = "关闭公告")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        noticeService.close(id);
        return Result.success();
    }

    @RequiresPerm("system:notice:delete")
    @OperLog(module = "SYSTEM_NOTICE", operType = "DELETE", desc = "删除公告")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.success();
    }

    /** 获取已发布的有效公告（用于弹窗提醒，无需登录） */
    @GetMapping("/active")
    public Result<List<SysNotice>> active() {
        return Result.success(noticeService.getActiveNotices());
    }
}
