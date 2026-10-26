// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.entity.WfApprovalItem;
import com.fssc.workflow.service.WfApprovalItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程审批项（{@code /api/tenant/wf/approval-item/**}）。
 *
 * <p><b>权限码的选择</b>：审批项是流程节点配置的一部分，与 {@code wf_node_config} 同族，
 * 故复用 {@code wf:config:list} / {@code wf:config:update}，<b>不新造</b> {@code wf:approval-item:*}
 * （新造码意味着上线后所有角色都拿不到、页面直接 403）。
 *
 * <p>查询接口 {@code /by-node} 同时服务两处：管理端「流程节点配置」页签，以及审批弹窗
 * （按当前任务的 modelKey+nodeKey 取本节点可用审批项，渲染自定义按钮）。
 */
@RestController
@RequestMapping("/api/tenant/wf/approval-item")
@RequiredArgsConstructor
@Validated
public class WfApprovalItemController {

    private final WfApprovalItemService approvalItemService;

    /** 分页（管理端配置页） */
    @RequiresPerm("wf:config:list")
    @GetMapping("/page")
    public Result<PageResult<WfApprovalItem>> page(PageQuery q,
                                                   @RequestParam(required = false) String modelKey,
                                                   @RequestParam(required = false) String nodeKey,
                                                   @RequestParam(required = false) String keyword) {
        return Result.success(approvalItemService.page(q, modelKey, nodeKey, keyword));
    }

    /**
     * 某节点启用的审批项（按 sort 升序）。无配置返回空数组 —— 前端据此决定是否渲染自定义按钮，
     * 未配置审批项的节点审批弹窗与改造前完全一致（只有同意/驳回）。
     */
    @RequiresPerm("wf:config:list")
    @GetMapping("/by-node")
    public Result<List<WfApprovalItem>> byNode(@RequestParam String modelKey, @RequestParam String nodeKey) {
        return Result.success(approvalItemService.listEnabled(modelKey, nodeKey));
    }

    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_APPROVAL_ITEM", operType = "INSERT", desc = "新增审批项")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody WfApprovalItem item) {
        return Result.success(approvalItemService.create(item));
    }

    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_APPROVAL_ITEM", operType = "UPDATE", desc = "更新审批项")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody WfApprovalItem item) {
        approvalItemService.update(item);
        return Result.success();
    }

    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_APPROVAL_ITEM", operType = "DELETE", desc = "删除审批项")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        approvalItemService.delete(id);
        return Result.success();
    }
}