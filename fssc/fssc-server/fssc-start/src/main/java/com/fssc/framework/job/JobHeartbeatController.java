// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.job;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 定时任务心跳（/api/tenant/system/job-heartbeat/**，供「定时任务健康」只读页签）。 */
@RestController
@RequestMapping("/api/tenant/system/job-heartbeat")
@RequiredArgsConstructor
public class JobHeartbeatController {

    private final JobHeartbeatService jobHeartbeatService;

    @RequiresPerm("system:job:list")
    @GetMapping("/page")
    public Result<PageResult<OpJobHeartbeat>> page(PageQuery query) {
        return Result.success(jobHeartbeatService.page(query.getPageNum(), query.getPageSize()));
    }
}
