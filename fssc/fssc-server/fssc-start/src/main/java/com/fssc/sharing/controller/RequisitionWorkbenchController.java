// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.sharing.service.MyDocumentService;
import com.fssc.sharing.service.RequisitionWorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 统一收单工作台（/api/tenant/workbench/**）
 * 统一收单大厅：场景卡片发起所有单据 + 我的单据/待办/已办三视图 + 单据详情内联展示全链路
 */
@RestController
@RequestMapping("/api/tenant/workbench")
@RequiredArgsConstructor
public class RequisitionWorkbenchController {

    private final RequisitionWorkbenchService workbenchService;
    private final MyDocumentService myDocumentService;

    /** 场景卡片列表（按业务域分组，前端渲染为卡片网格） */
    @GetMapping("/scenes")
    public Result<List<Map<String, Object>>> scenes() {
        return Result.success(workbenchService.getScenes());
    }

    /** 概览看板（我的待办数 / 我发起的单据数 / 任务池 / 付款待执行） */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(workbenchService.getDashboard());
    }

    /**
     * 我的单据（<b>费用域</b> exp_bill 一表，含 10+ 种 bill_type）
     * 全类型（合同/采购/预算/总账/档案…）见 {@link #myDocuments}。本接口保留一个版本给旧入口，
     * 不要写成"全类型"：那会让注释承诺与规则不一致。
     */
    @GetMapping("/my-bills")
    public Result<PageResult<Map<String, Object>>> myBills(PageQuery query,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String status) {
        return Result.success(workbenchService.myBills(query, billType, status));
    }

    /**
     * 我登记的全类型单据。按 {@code BillRouteRegistry} 的
     * "bizType→业务主表→归属人列"逐表查询后内存归并；无归属人列的类型走 {@code unsupported}
     * 明示，而不是静默少数据。
     */
    @GetMapping("/my-documents")
    public Result<Map<String, Object>> myDocuments(PageQuery query,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status) {
        return Result.success(myDocumentService.myDocs(query, bizType, status));
    }

    /** 我的待办（委托 WfQueryService.myTodo） */
    @GetMapping("/my-todo")
    public Result<PageResult<Map<String, Object>>> myTodo(PageQuery query) {
        return Result.success(workbenchService.myTodo(query));
    }

    /** 我的已办（委托 WfQueryService.taskPage status=DONE） */
    @GetMapping("/my-done")
    public Result<PageResult<Map<String, Object>>> myDone(PageQuery query) {
        return Result.success(workbenchService.myDone(query));
    }

    /**
     * 单据详情（全链路：单据基础 + 流程轨迹 + 凭证 + 付款 + 影像 + 档案）
     *
     * <p>{@code bizType} 用于挡住跨域同 id：本详情按 {@code exp_bill} 主键查询，而"我的单据"已扩到
     * 全类型（合同 id=7 与报销单 id=7 会撞号）⇒ 传了非费用域类型就直接拒绝，不回错数据（附带项）。
     */
    @GetMapping("/bill-detail/{billId}")
    public Result<Map<String, Object>> billDetail(@PathVariable Long billId,
            @RequestParam(required = false) String bizType) {
        return Result.success(workbenchService.getBillDetail(billId, bizType));
    }
}
