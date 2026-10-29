// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.budget.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.budget.entity.BgOccupy;
import com.fssc.budget.mapper.BgOccupyMapper;
import com.fssc.budget.service.BudgetService;
import com.fssc.budget.vo.BgOccupyVO;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.exception.BizException;
import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预算占用操作（/api/tenant/budget/occupy/**）。
 * 报销单提交时占用、撤回时释放、审批通过后核销。流水只增不改。
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant/budget/occupy")
@RequiredArgsConstructor
@Validated
public class BudgetOccupyController {

    private final BudgetService budgetService;
    private final BgOccupyMapper bgOccupyMapper;
    private final SysUserMapper sysUserMapper;
    /** 补销/释放需要读单据状态，费用单据只读查询。 */
    private final ExpBillMapper expBillMapper;

    /** 占用流水查询（需求 3.10 查看占用明细，真分页）。支持 budgetItemId 穿透过滤 */
    @RequiresPerm("budget:occupy:list")
    @GetMapping("/page")
    public Result<PageResult<BgOccupyVO>> page(PageQuery query,
                                             @RequestParam(required = false) Long budgetItemId,
                                             @RequestParam(required = false) String operType,
                                             @RequestParam(required = false) String bizType,
                                             @RequestParam(required = false) String keyword) {
        IPage<BgOccupyVO> p = bgOccupyMapper.searchPageWithItemName(
                Page.of(query.getPageNum(), query.getPageSize()),
                budgetItemId, operType, bizType, keyword);
        fillOperatorNames(p.getRecords());
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), query.getPageNum(), query.getPageSize()));
    }

    /**
     * 分页结果由后端一次批查 sys_user，把裸 user_id 补成「姓名/工号」可读规则（与审计轨迹同法）。
     * 查不到或异常时留空——前端回退显示裸 id，绝不臆造姓名。
     */
    private void fillOperatorNames(List<BgOccupyVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (BgOccupyVO r : rows) {
            if (r.getOperUserId() != null) {
                ids.add(r.getOperUserId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        try {
            Map<Long, SysUser> userMap = new HashMap<>();
            for (SysUser u : sysUserMapper.selectBatchIds(ids)) {
                userMap.put(u.getId(), u);
            }
            for (BgOccupyVO r : rows) {
                SysUser u = userMap.get(r.getOperUserId());
                if (u == null) {
                    continue;
                }
                r.setOperUserName(u.getNickName() != null ? u.getNickName() : u.getUserName());
                r.setOperUserNo(u.getEmpNo());
            }
        } catch (Exception e) {
            log.warn("预算占用操作人姓名回填失败，降级显示用户ID", e);
        }
    }

    /**
     * 预算准入判定（乙）：不占用、不打挂提交——超出只回结论与金额（available/over/overAmount），
     * 提交继续走 {@code /occupy} 与单据占用环节，那里才按 bg_scheme.control_type 档位执行扣减校验。
     */
    @RequiresPerm("budget:occupy:check")
    @PostMapping("/check")
    public Result<BudgetService.OccupyCheckResult> check(@Validated @RequestBody OccupyReq req) {
        return Result.success(budgetService.checkOccupyResult(req.getBudgetItemId(), req.getAmount()));
    }

    @RequiresPerm("budget:occupy:do")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "占用预算")
    @PostMapping("/occupy")
    public Result<Void> occupy(@Validated @RequestBody OccupyReq req) {
        budgetService.occupy(req.getBudgetItemId(), req.getAmount(), req.getBizType(), req.getBizId(), req.getBizNo());
        return Result.success();
    }

    @RequiresPerm("budget:occupy:do")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "释放预算")
    @PostMapping("/release")
    public Result<Void> release(@Validated @RequestBody OccupyReq req) {
        // 释放只要求单据存在且未作废（占用本就该在作废/驳回时退回），不要求已 PAID。
        assertBizBillState(req.getBizType(), req.getBizNo(), null);
        budgetService.release(req.getBudgetItemId(), req.getAmount(), req.getBizType(), req.getBizId(), req.getBizNo());
        return Result.success();
    }

    @RequiresPerm("budget:occupy:do")
    @OperLog(module = "BUDGET", operType = "UPDATE", desc = "核销预算")
    @PostMapping("/write-off")
    public Result<Void> writeOff(@Validated @RequestBody OccupyReq req) {
        // 手工补销口必须校验单据状态。流程内部核销（PayBatchBizAction 付款执行、
        // ExpenseWfEventListener 报销核销）走 Service 直调，不经此入口，不受影响。
        assertBizBillState(req.getBizType(), req.getBizNo(), "PAID");
        budgetService.writeOff(req.getBudgetItemId(), req.getAmount(), req.getBizType(), req.getBizId(), req.getBizNo());
        return Result.success();
    }

    /**
     * 预算占用三动作的单据状态前置校验。
     *
     * <p>背景：补销口原先只认 {@code bizId} 落流水，完全不读单据状态，于是对
     * {@code APPROVED}（钱尚未付）的对公结算单也能核销成功 ⇒ 预算在未付时转「已用」，
     * 而 {@code writeOff} 只进不出，付款失败/退款没有回滚路径。
     *
     * <p>{@code requiredStatus} 为 null 时只做「存在 + 非作废」校验（释放动作适用：
     * 占用本就该在单据作废/驳回时释放，PAID 不成立）。
     */
    private void assertBizBillState(String bizType, String bizNo, String requiredStatus) {
        if (bizType == null || !bizType.startsWith("EXP_") || bizNo == null || bizNo.isBlank()) {
            return; // 非费用域或未给单号，交由原有校验处理
        }
        ExpBill bill = expBillMapper.selectOne(
                new LambdaQueryWrapper<ExpBill>()
                        .eq(ExpBill::getBillNo, bizNo)
                        .last("LIMIT 1"));
        if (bill == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单据不存在：" + bizNo + "，无法执行预算" + ("PAID".equals(requiredStatus) ? "核销" : "释放"));
        }
        String st = bill.getStatus();
        if ("VOID".equals(st) || "CANCELLED".equals(st)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单据 " + bizNo + " 已作废（status=" + st + "），不允许再动预算；如需修正请先恢复单据");
        }
        if (requiredStatus != null && !requiredStatus.equals(st)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单据 " + bizNo + " 当前状态为「" + st + "」，预算核销要求已「" + requiredStatus
                            + "」：钱未付出去就核销会让预算被提前占用且无回滚路径。请先完成付款，或改用「释放」退回占用");
        }
    }

    @Data
    public static class OccupyReq {
        @NotNull(message = "budgetItemId 必填")
        private Long budgetItemId;
        @NotNull(message = "amount 必填")
        private BigDecimal amount;
        @NotBlank(message = "bizType 必填")
        private String bizType;
        /**
         * {@code bizId} 实为必传（{@code writeOff}/{@code release} 都靠它归属占用流水，
         * 缺传时在 Service 里对 null 解引用抛 NPE ⇒ 全局处理器包成 HTTP 500，调用方无从判断是参数问题）。
         * 补上 {@code @NotNull} 让缺参在入口就被 400 挡下。
         */
        @NotNull(message = "bizId 必填（单据主键，用于归属占用流水）")
        private Long bizId;
        private String bizNo;
    }
}
