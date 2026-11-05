// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.controller;

import com.fssc.budget.entity.BgItem;
import com.fssc.budget.entity.BgScheme;
import com.fssc.budget.service.BudgetItemExcelService;
import com.fssc.budget.service.BudgetService;
import com.fssc.budget.service.BudgetSwitchService;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 预算方案与明细（/api/tenant/budget/scheme、/api/tenant/budget/item） */
@RestController
@RequestMapping("/api/tenant/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetItemExcelService budgetItemExcelService;
    /** 预算口径开关：预提/待摊占用、合同占用 */
    private final BudgetSwitchService budgetSwitchService;

    // 预算执行明细（占用/核销/余额）
    @RequiresPerm("budget:scheme:list")
    @GetMapping("/execution")
    public Result<List<Map<String, Object>>> execution(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "deptId", required = false) Long deptId) {
        return Result.success(budgetService.executionSummary(period, deptId));
    }

    // 方案

    @RequiresPerm("budget:scheme:list")
    @GetMapping("/scheme/page")
    public Result<PageResult<BgScheme>> schemePage(PageQuery query, String keyword) {
        return Result.success(budgetService.schemePage(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("budget:scheme:create")
    @OperLog(module = "BUDGET", operType = "INSERT", desc = "新增预算方案")
    @PostMapping("/scheme/create")
    public Result<Long> schemeCreate(@RequestBody BgScheme scheme) {
        return Result.success(budgetService.schemeCreate(scheme));
    }

    @RequiresPerm("budget:scheme:update")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "更新预算方案")
    @PostMapping("/scheme/update")
    public Result<Void> schemeUpdate(@RequestBody BgScheme scheme) {
        budgetService.schemeUpdate(scheme);
        return Result.success();
    }

    @RequiresPerm("budget:scheme:update")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "方案启用/关闭")
    @PostMapping("/scheme/{id}/enable")
    public Result<Void> schemeEnable(@PathVariable Long id, @RequestParam boolean enable) {
        budgetService.schemeEnable(id, enable);
        return Result.success();
    }

    /**
     * 预算方案提交审批：DRAFT → APPROVING，启动 BG_APPROVE 流（预算管理员初审→财务总监终审）。
     * body: {"schemeId": 1}
     */
    @RequiresPerm("budget:scheme:update")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "预算方案提交审批")
    @PostMapping("/scheme/submit")
    public Result<Void> schemeSubmit(@RequestBody SchemeIdReq req) {
        budgetService.schemeSubmit(req.getSchemeId());
        return Result.success();
    }

    /**
     * 预算方案下达：置 ISSUED 并记录下达时间/人（APPROVED 审批通过后下达，或 DRAFT 免审直下达）。
     * body: {"schemeId": 1}
     */
    @RequiresPerm("budget:scheme:update")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "预算方案下达")
    @PostMapping("/scheme/issue")
    public Result<Void> schemeIssue(@RequestBody SchemeIdReq req) {
        budgetService.schemeIssue(req.getSchemeId());
        return Result.success();
    }

    /**  提交/下达请求体 */
    @lombok.Data
    public static class SchemeIdReq {
        private Long schemeId;
    }

    // 明细

    // 只读查询放开给所有登录租户用户：单据发起页的预算占用预览必用（写操作仍需权限）
    // ids：逗号分隔的预算明细主键，供单据页精确取回"自动映射科目未在前 200 行预载内"的额度
    // （用逗号串而非数组参数：axios 1.x 会把数组序列化成 ids[]=，Spring 的 List 绑定不到）
    @GetMapping("/item/page")
    public Result<PageResult<BgItem>> itemPage(PageQuery query, Long schemeId, Long deptId, Long itemId,
                                               @RequestParam(required = false) String ids) {
        List<Long> idList = null;
        if (ids != null && !ids.isBlank()) {
            idList = new ArrayList<>();
            for (String s : ids.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    idList.add(Long.valueOf(t));
                }
            }
        }
        return Result.success(budgetService.itemPage(query.getPageNum(), query.getPageSize(), schemeId, deptId, itemId, idList));
    }

    /**
     * 单据发起页「预算科目」红星下拉查询端点。
     * <p>与 {@code /item/page} 的差别只在数据范围规则：本端点按<b>公司/法人</b>维度放行
     * （{@code BudgetService#itemPickPage}），不再套个人 data_scope 五级——普通员工
     * （COMMON_EMP data_scope=4 本部门）在旧规则下 {@code bg_item.dept_id} 恒 NULL，命中 0 行、
     * 红星永远选不出合规科目。管理侧列表仍走 {@code /item/page}（保持 {@code @DataScope}）。
     * <p>跨公司依旧隔离（OrgGuard.enforceCompany），只读、无写权限变化。
     */
    @GetMapping("/item/pick")
    public Result<PageResult<BgItem>> itemPick(PageQuery query, Long schemeId, Long deptId, Long itemId,
                                               @RequestParam(required = false) Integer budgetYear,
                                               @RequestParam(required = false) String ids) {
        List<Long> idList = null;
        if (ids != null && !ids.isBlank()) {
            idList = new ArrayList<>();
            for (String s : ids.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    idList.add(Long.valueOf(t));
                }
            }
        }
        return Result.success(budgetService.itemPickPage(query.getPageNum(), query.getPageSize(),
                schemeId, deptId, itemId, idList, budgetYear));
    }

    @RequiresPerm("budget:item:create")
    @OperLog(module = "BUDGET", operType = "INSERT", desc = "录入预算明细")
    @PostMapping("/item/upsert")
    public Result<Void> itemUpsert(@RequestBody BgItem item) {
        budgetService.itemUpsert(item);
        return Result.success();
    }

    // 明细 Excel 导入/导出

    /** 导出预算明细 xlsx（可选按方案/部门/费用项过滤） */
    @RequiresPerm("budget:scheme:list")
    @GetMapping("/item/export")
    public void itemExport(HttpServletResponse response,
                           @RequestParam(required = false) Long schemeId,
                           @RequestParam(required = false) Long deptId,
                           @RequestParam(required = false) Long itemId) throws IOException {
        byte[] data = budgetItemExcelService.exportItems(schemeId, deptId, itemId);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String name = URLEncoder.encode("预算明细.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + name);
        response.setContentLength(data.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(data);
        }
    }

    /** 导入预算明细 xlsx（方案由页面选择随 schemeId 传入），返回 新增/更新/错误行 */
    @RequiresPerm("budget:item:create")
    @OperLog(module = "BUDGET", operType = "INSERT", desc = "导入预算明细")
    @PostMapping("/item/import")
    public Result<BudgetItemExcelService.ImportResult> itemImport(            @RequestParam("file") MultipartFile file,
            @RequestParam("schemeId") Long schemeId) {
        return Result.success(budgetItemExcelService.importItems(schemeId, file));
    }

    /**
     * 实际数导入：JSON 批量导入，命中预算行即扣减已用（写 WRITE_OFF 流水留痕）。
     * body: {"bizNo":"可选批次号","rows":[{"budgetItemId":1,"amount":100.00}, {"itemId":5,"companyId":1,"deptId":2,"amount":200}]}
     */
    @RequiresPerm("budget:item:create")
    @OperLog(module = "BUDGET", operType = "INSERT", desc = "预算实际数导入")
    @PostMapping("/actual/import")
    public Result<java.util.Map<String, Object>> actualImport(@RequestBody java.util.Map<String, Object> req) {
        String bizNo = req.get("bizNo") == null ? null : String.valueOf(req.get("bizNo"));
        Object raw = req.get("rows");
        if (!(raw instanceof java.util.List)) {
            throw new com.fssc.common.exception.BizException(
                    com.fssc.common.core.ErrorCode.PARAM_INVALID, "rows 必须是数组");
        }
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> rows = (java.util.List<java.util.Map<String, Object>>) raw;
        return Result.success(budgetService.importActual(rows, bizNo));
    }

    /** 结余结转：把上年可用余额结转到下年同维度行 */
    @RequiresPerm("budget:scheme:update")
    @OperLog(module = "BUDGET", operType = "INSERT", desc = "预算结余结转")
    @PostMapping("/carry-over")
    public Result<java.util.Map<String, Object>> carryOver(@RequestParam int fromYear, @RequestParam int toYear) {
        return Result.success(budgetService.carryOver(fromYear, toYear));
    }

    /**
     * 冻结/解冻预算行：冻结后拒绝新增占用，释放与核销照常。
     * <p>冻结必须填原因（写进审计轨迹）——历史上出现过演练冻结后没人知道为什么、也没有入口改回来的情况。
     * <p>权限码 2026-11-05 拆细：新码 {@code budget:item:freeze} 与旧码 {@code budget:item:create} 取 OR，
     * 授权数据迁移完成前两套码都能通行，按钮不会因为换码整片消失。
     */
    @RequiresPerm({"budget:item:freeze", "budget:item:create"})
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "冻结/解冻预算行")
    @PostMapping("/item/{id}/freeze")
    public Result<Void> freeze(@PathVariable Long id, @RequestParam boolean freeze,
                               @RequestParam(required = false) String reason) {
        budgetService.setFreeze(id, freeze, reason);
        return Result.success();
    }

    // 预算口径开关

    /**
     * 读取预算口径开关：预提/待摊是否占预算、合同是否占预算。
     * 缺省均为「开」，与改造前行为一致；权限复用方案读权限，预算岗即可见。
     */
    @RequiresPerm("budget:scheme:list")
    @GetMapping("/switch")
    public Result<Map<String, Boolean>> switchGet() {
        return Result.success(budgetSwitchService.snapshot());
    }

    /**
     * 保存预算口径开关。只写显式传入的项（null＝不改），避免前端少传字段把另一项误关。
     * <p>改完即时生效（ConfigService 带缓存失效）；<b>关闭不追溯</b>，已占额度仍按原路径释放。</p>
     */
    @RequiresPerm("budget:scheme:update")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "预算口径开关")
    @PostMapping("/switch")
    public Result<Void> switchSave(@RequestBody SwitchReq req) {
        budgetSwitchService.save(req.getAccrualOccupy(), req.getContractOccupy());
        return Result.success();
    }

    /** 预算口径开关入参：字段为 null 表示本次不改该项 */
    @lombok.Data
    public static class SwitchReq {
        private Boolean accrualOccupy;
        private Boolean contractOccupy;
    }
}
