// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.controller;

import com.fssc.common.core.Result;
import com.fssc.expense.entity.ExpMobileTemplate;
import com.fssc.expense.service.ExpMobileTemplateService;
import com.fssc.framework.security.annotation.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 移动端单据启用清单（/api/tenant/expense/mobile/templates）：只读，无入参。
 *
 * <p>给 H5 提报页下发「哪些类型能在手机上提、哪些要回电脑端」，取代前端硬编码常量
 * {@code H5_DOC_TYPES}。返回<b>全量</b>清单——含 {@code mobileEnabled=0}
 * 的行，页面「以下类型请在电脑端提报」明示区正是用这些行渲染的。</p>
 *
 * <p><b>权限码沿用</b> {@code exp:bill:list}（与 {@code ExpenseBillController#myPage}、
 * {@code ExpenseMineStatController} 同码）：这是移动端提单入口本来就需要的权限，
 * 能看自己单据列表的人就能看提单清单——<b>不新增授权项</b>、不为移动端另发权限。</p>
 *
 * <p><b>无 {@code @OperLog}</b>：读一次清单不是业务动作，记进操作日志只会淹没真实记录。</p>
 */
@RestController
@RequestMapping("/api/tenant/expense/mobile")
@RequiredArgsConstructor
public class ExpMobileTemplateController {

    private final ExpMobileTemplateService expMobileTemplateService;

    /** 移动端单据启用清单（全量，含未启用行；读失败降级为空列表，见服务类注释）。 */
    @RequiresPerm("exp:bill:list")
    @GetMapping("/templates")
    public Result<List<ExpMobileTemplate>> templates() {
        return Result.success(expMobileTemplateService.listForMobile());
    }
}
