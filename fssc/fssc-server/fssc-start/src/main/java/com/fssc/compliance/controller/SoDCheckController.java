// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.controller;

import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.service.SoDService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * R5-D-034：职责分离（SoD）「执行检查 + 结果查询」入口。
 *
 * <p>既有端点只按单一维度返回裸数组且字段与页面列不对齐，本控制器返回 {@link PageResult}
 * （前端分页契约 list/total），行字段为用户可读的 用户/工号/冲突岗位/冲突描述/严重度/检测时间。
 * 检查为无状态实时计算：sod_role_conflict / sod_perm_conflict 是互斥【规则定义表】而非结果表，
 * 故本链路不需要新建表或列。</p>
 *
 * <p>权限码：{@code system:sod:list} 与 {@code system:sod:scan} 两个码都按「多码 OR、新旧同认」
 * 同时挂在查询与执行端点上。实测 sys_menu 中两码均有在用行——
 * {@code system:sod:list} 挂在菜单 3621（逗号打包码）；{@code system:sod:scan} 已播种为按钮菜单 3762
 * （del_flag=0）。仅持有 3762 的角色（如合规管理岗 99031）若只认 list 会在首屏查询即 403，
 * 故两个端点都并认两码。旧的菜单 3080（system:sod:list）已 del_flag=1，不再作为依据。</p>
 */
@RestController
@RequestMapping("/api/tenant/compliance/sod")
@RequiredArgsConstructor
public class SoDCheckController {

    private final SoDService soDService;

    /** R5-D-034：SoD 检查结果分页查询（dimension=ALL/ROLE/PERM） */
    @RequiresPerm({"system:sod:list", "system:sod:scan"})
    @GetMapping("/check/list")
    public Result<PageResult<SoDService.SodCheckRow>> checkList(@RequestParam(required = false) String dimension,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(defaultValue = "1") long pageNum,
                                                                @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(soDService.checkPage(dimension, keyword, pageNum, pageSize));
    }

    /** R5-D-034：执行 SoD 检查（两维度一次跑完，操作留痕） */
    @RequiresPerm({"system:sod:scan", "system:sod:list"})
    @OperLog(module = "COMPLIANCE", operType = "OTHER", desc = "执行职责分离检查")
    @PostMapping("/check/run")
    public Result<PageResult<SoDService.SodCheckRow>> checkRun(@RequestParam(required = false) String dimension,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(defaultValue = "1") long pageNum,
                                                              @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(soDService.checkPage(dimension, keyword, pageNum, pageSize));
    }
}
