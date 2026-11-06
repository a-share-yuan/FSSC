// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fssc.common.core.Result;
import com.fssc.framework.security.UserContext;
import com.fssc.glex.entity.GlCloseCheckItem;
import com.fssc.glex.mapper.GlCloseCheckItemMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.fssc.common.exception.BizException;
import com.fssc.common.core.ErrorCode;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.glex.entity.GlMonthClose;
import com.fssc.glex.service.GlCloseCheckService;
import com.fssc.glex.service.GlMonthCloseService;
import com.fssc.glex.service.GlProfitCarryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 总账期间操作（反结账 / 损益结转独立动作）。
 * <p>
 * 端点规则按需求指定：
 * <ul>
 *  <li>POST /api/tenant/gl/month/unclose — 反结账（CFO/租户管理员）</li>
 *  <li>POST /api/tenant/gl/profit-carry — 损益结转独立动作</li>
 * </ul>
 * 权限沿用月结既有权限码 gl:monthclose:close（月结/反结账同权），未新增权限码。
 */
@RestController
@RequestMapping("/api/tenant/gl")
@RequiredArgsConstructor
public class GlPeriodOpsController {

    private final GlMonthCloseService monthCloseService;
    private final GlCloseCheckItemMapper closeCheckItemMapper;
    private final GlProfitCarryService profitCarryService;
    /** 月结清单里系统可判的 4 项（凭证已过账/税金计提/内部往来/应生成未生成凭证） */
    private final GlCloseCheckService closeCheckService;

    /** 反结账：期间 CLOSED 回退开账；下一期间须未结账（逐期回退）；仅财务总监(CFO)/总账会计(FIN_GL)/租户管理员。body: {"period":"202609"} */
    @RequiresPerm("gl:monthclose:close")
    @OperLog(module = "GL_MONTH_CLOSE", operType = "UPDATE", desc = "月结反结账")
    @PostMapping("/month/unclose")
    public Result<GlMonthClose> unclose(@RequestBody PeriodReq req) {
        return Result.success(monthCloseService.unclose(req.getPeriod(), req.getCompanyId()));
    }

    /** 损益结转独立动作：结转期间损益类科目余额至本年利润 4103 并过账。body: {"period":"202609"} */
    @RequiresPerm("gl:monthclose:close")
    @OperLog(module = "GL_MONTH_CLOSE", operType = "INSERT", desc = "损益结转独立执行")
    @PostMapping("/profit-carry")
    public Result<Map<String, Object>> profitCarry(@RequestBody PeriodReq req) {
        return Result.success(profitCarryService.carry(req.getPeriod(), req.getCompanyId()));
    }

    /** 期间操作请求体 */
    @lombok.Data
    public static class PeriodReq {
        /** 会计期间 yyyyMM */
        private String period;
        /** 结账/结转公司（空则默认 1，与月结 close 同一规则） */
        private Long companyId;
    }

    // 月结检查校验清单

    /** 检查校验清单：GET /close-check/list?period=202611&companyId=1（公司缺省取登录上下文，与月结 close 同一规则） */
    @RequiresPerm("gl:monthclose:close")
    @GetMapping("/close-check/list")
    public Result<List<GlCloseCheckItem>> closeCheckList(
            @RequestParam String period,
            @RequestParam(required = false) Long companyId) {
        String p = requireCheckPeriod(period);
        Long cid = resolveCheckCompany(companyId);
        List<GlCloseCheckItem> rows = closeCheckItemMapper.selectList(
                new LambdaQueryWrapper<GlCloseCheckItem>()
                        .eq(GlCloseCheckItem::getCompanyId, cid)
                        .eq(GlCloseCheckItem::getPeriod, p)
                        .orderByAsc(GlCloseCheckItem::getId));
        // 存量行项名含英文状态码，读面统一汉化；存储键不动，勾选时仍按原值做系统校验
        rows.forEach(r -> r.setItemName(GlCloseCheckService.displayName(r.getItemName())));
        return Result.success(rows);
    }

    /** 生成当期默认清单（9 项，已存在则跳过）：POST /close-check/seed?period=202611&companyId=1 */
    @RequiresPerm("gl:monthclose:close")
    @PostMapping("/close-check/seed")
    public Result<Integer> closeCheckSeed(
            @RequestParam String period,
            @RequestParam(required = false) Long companyId) {
        Long cid = resolveCheckCompany(companyId);
        String p = requireCheckPeriod(period);
        String[][] defaults = {
                {"全部凭证已过账（无 DRAFT）", "FIN_GL"},
                {"银行对账已平衡", "CASHIER"},
                {"费用预提已完成", "FIN_GL"},
                {"税金计提已完成", "FIN_GL"},
                {"薪酬计提已完成", "FIN_GL"},
                {"坏账计提已复核", "FIN_AR"},
                {"资产折旧已计提", "FA_ASSET_ACCOUNT"},
                {"内部往来已抵消", "FIN_GL"},
                {"应生成未生成凭证检查", "FIN_GL"},
        };
        int n = 0;
        for (String[] d : defaults) {
            boolean exists = closeCheckItemMapper.selectCount(
                    new LambdaQueryWrapper<GlCloseCheckItem>()
                            .eq(GlCloseCheckItem::getCompanyId, cid)
                            .eq(GlCloseCheckItem::getPeriod, p)
                            .eq(GlCloseCheckItem::getItemName, d[0])) > 0;
            if (exists) continue;
            GlCloseCheckItem it = new GlCloseCheckItem();
            it.setCompanyId(cid);
            it.setPeriod(p);
            it.setItemName(d[0]);
            it.setOwnerRole(d[1]);
            it.setStatus("PENDING");
            closeCheckItemMapper.insert(it);
            n++;
        }
        return Result.success(n);
    }

    /** 勾项/回退：POST /close-check/check body={id, done, remark} */
    @RequiresPerm("gl:monthclose:close")
    @PostMapping("/close-check/check")
    public Result<Void> closeCheckTick(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(String.valueOf(body.get("id")));
        boolean done = Boolean.parseBoolean(String.valueOf(body.getOrDefault("done", "true")));
        GlCloseCheckItem it = closeCheckItemMapper.selectById(id);
        if (it == null) throw new BizException(ErrorCode.PARAM_INVALID, "检查项不存在: " + id);
        // 系统可判的 4 项不允许「人工勾上」——勾不上就是勾不上。
        // 其余 5 项（银行对账/费用预提/薪酬/坏账/折旧）保留人工判断。
        if (done && GlCloseCheckService.isSystemChecked(it.getItemName())) {
            String err = closeCheckService.verifyOne(it.getItemName(), it.getPeriod(), it.getCompanyId());
            if (err != null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "「" + GlCloseCheckService.displayName(it.getItemName()) + "」系统校验未通过：" + err);
            }
        }
        it.setStatus(done ? "DONE" : "PENDING");
        it.setCheckTime(done ? LocalDateTime.now() : null);
        it.setChecker(done ? (UserContext.get() != null
                ? UserContext.get().getUserName() : null) : null);
        if (body.get("remark") != null) it.setRemark(String.valueOf(body.get("remark")));
        closeCheckItemMapper.updateById(it);
        return Result.success();
    }

    /**
     * 系统可判项预检：GET /close-check/verify?period=202611&companyId=1
     * 只读，返回每项是否通过及具体原因，供清单页显示"系统判"标签。
     */
    @RequiresPerm("gl:monthclose:close")
    @GetMapping("/close-check/verify")
    public Result<List<Map<String, Object>>> closeCheckVerify(
            @RequestParam String period,
            @RequestParam(required = false) Long companyId) {
        return Result.success(closeCheckService.verifyAll(requireCheckPeriod(period), resolveCheckCompany(companyId)));
    }

    /**
     * 检查校验清单的建/查公司范围与月结 close 逐字对齐（GlMonthCloseService:248 comp = companyId != null ? companyId : 1L）：
     * 显式入参 → 1。此处若改成取登录主体公司，清单会建在 A 公司、校验口查的是 B 公司，校验永远过不去。
     */
    private Long resolveCheckCompany(Long companyId) {
        return companyId != null ? companyId : 1L;
    }

    /** 期间必输且必须是 6 位 yyyyMM（gl_close_check_item.period varchar(6)，与月结入库规则一致） */
    private String requireCheckPeriod(String period) {
        String p = period == null ? "" : period.trim();
        if (!p.matches("\\d{6}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "会计期间格式不正确，应为 6 位 yyyyMM（如 202610）");
        }
        return p;
    }

}
