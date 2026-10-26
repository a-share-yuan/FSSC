// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.sharing.entity.OpDispatchRule;
import com.fssc.sharing.entity.ShCalendar;
import com.fssc.sharing.entity.ShGroup;
import com.fssc.sharing.entity.ShJob;
import com.fssc.sharing.mapper.OpDispatchRuleMapper;
import com.fssc.sharing.mapper.ShCalendarMapper;
import com.fssc.sharing.mapper.ShGroupMapper;
import com.fssc.sharing.mapper.ShJobMapper;
import com.fssc.system.entity.SysUser;
import com.fssc.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 共享运营-基础配置（需求 8.1）：作业岗位设置 / 分组管理 / 工作日历。
 * 简单配置类 CRUD，直接走 MP（租户拦截器 + MetaObjectHandler 自动 tenant_id/审计）。
 */
@RestController
@RequestMapping("/api/tenant/sharing/config")
@RequiredArgsConstructor
public class ShOpsConfigController {

    private final ShJobMapper jobMapper;
    private final ShGroupMapper groupMapper;
    private final ShCalendarMapper calendarMapper;
    private final OpDispatchRuleMapper dispatchRuleMapper;
    private final SysUserMapper sysUserMapper;

    // ---------------- 作业岗位设置 ----------------

    @RequiresPerm("sharing:config")
    @GetMapping("/job/page")
    public Result<PageResult<ShJob>> jobPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<ShJob> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(ShJob::getJobName, keyword).or().like(ShJob::getJobCode, keyword));
        }
        w.orderByDesc(ShJob::getId);
        Page<ShJob> p = jobMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "INSERT", desc = "新增作业岗位")
    @PostMapping("/job/create")
    public Result<Long> jobCreate(@RequestBody ShJob job) {
        jobMapper.insert(job);
        return Result.success(job.getId());
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "UPDATE", desc = "更新作业岗位")
    @PostMapping("/job/update")
    public Result<Void> jobUpdate(@RequestBody ShJob job) {
        jobMapper.updateById(job);
        return Result.success();
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "DELETE", desc = "删除作业岗位")
    @PostMapping("/job/{id}/delete")
    public Result<Void> jobDelete(@PathVariable Long id) {
        jobMapper.deleteById(id);
        return Result.success();
    }

    // ---------------- 分组管理 ----------------

    @RequiresPerm("sharing:config")
    @GetMapping("/group/page")
    public Result<PageResult<ShGroup>> groupPage(PageQuery q, String keyword) {
        LambdaQueryWrapper<ShGroup> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(ShGroup::getGroupName, keyword).or().like(ShGroup::getGroupCode, keyword));
        }
        w.orderByDesc(ShGroup::getId);
        Page<ShGroup> p = groupMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        // Batch-fill leaderName from sys_user
        List<ShGroup> groups = p.getRecords();
        if (groups != null && !groups.isEmpty()) {
            Set<Long> leaderIds = groups.stream()
                    .map(ShGroup::getLeaderId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            if (!leaderIds.isEmpty()) {
                List<SysUser> users = sysUserMapper.selectList(
                        new QueryWrapper<SysUser>().in("id", leaderIds).select("id", "nick_name"));
                Map<Long, String> idToName = users.stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getNickName, (a, b) -> a));
                for (ShGroup g : groups) {
                    if (g.getLeaderId() != null) {
                        g.setLeaderName(idToName.get(g.getLeaderId()));
                    }
                }
            }
        }
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "INSERT", desc = "新增业务分组")
    @PostMapping("/group/create")
    public Result<Long> groupCreate(@RequestBody ShGroup group) {
        groupMapper.insert(group);
        return Result.success(group.getId());
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "UPDATE", desc = "更新业务分组")
    @PostMapping("/group/update")
    public Result<Void> groupUpdate(@RequestBody ShGroup group) {
        groupMapper.updateById(group);
        return Result.success();
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "DELETE", desc = "删除业务分组")
    @PostMapping("/group/{id}/delete")
    public Result<Void> groupDelete(@PathVariable Long id) {
        groupMapper.deleteById(id);
        return Result.success();
    }

    // ---------------- 工作日历 ----------------

    @RequiresPerm("sharing:config")
    @GetMapping("/calendar/page")
    public Result<PageResult<ShCalendar>> calendarPage(PageQuery q, Integer year, Integer month) {
        LambdaQueryWrapper<ShCalendar> w = new LambdaQueryWrapper<>();
        if (year != null) w.eq(ShCalendar::getYear, year);
        if (month != null) w.eq(ShCalendar::getMonth, month);
        w.orderByDesc(ShCalendar::getCalDate);
        Page<ShCalendar> p = calendarMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "INSERT", desc = "新增工作日历")
    @PostMapping("/calendar/create")
    public Result<Long> calendarCreate(@RequestBody ShCalendar cal) {
        calendarMapper.insert(cal);
        return Result.success(cal.getId());
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "UPDATE", desc = "更新工作日历")
    @PostMapping("/calendar/update")
    public Result<Void> calendarUpdate(@RequestBody ShCalendar cal) {
        calendarMapper.updateById(cal);
        return Result.success();
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "DELETE", desc = "删除工作日历")
    @PostMapping("/calendar/{id}/delete")
    public Result<Void> calendarDelete(@PathVariable Long id) {
        calendarMapper.deleteById(id);
        return Result.success();
    }

    // ---------------- 抢派单规则（需求 8.2.1） ----------------

    @RequiresPerm("sharing:config")
    @GetMapping("/dispatch-rule/page")
    public Result<PageResult<OpDispatchRule>> rulePage(PageQuery q, String keyword) {
        LambdaQueryWrapper<OpDispatchRule> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(OpDispatchRule::getRuleName, keyword).or().like(OpDispatchRule::getRuleCode, keyword));
        }
        w.orderByAsc(OpDispatchRule::getPriority).orderByDesc(OpDispatchRule::getId);
        Page<OpDispatchRule> p = dispatchRuleMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        // Fill targetGroupAutoDispatch from ShGroup
        for (OpDispatchRule rule : p.getRecords()) {
            if (rule.getTargetGroupId() != null) {
                ShGroup group = groupMapper.selectById(rule.getTargetGroupId());
                if (group != null && group.getIsAutoDispatch() != null && group.getIsAutoDispatch() == 1) {
                    rule.setTargetGroupAutoDispatch("开启");
                } else {
                    rule.setTargetGroupAutoDispatch("关闭");
                }
            } else {
                rule.setTargetGroupAutoDispatch("-");
            }
        }
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "INSERT", desc = "新增抢派单规则")
    @PostMapping("/dispatch-rule/create")
    public Result<Long> ruleCreate(@RequestBody OpDispatchRule rule) {
        if (ruleCodeDuplicated(rule.getRuleCode(), null)) {
            return Result.fail(ErrorCode.DATA_ALREADY_EXISTS.getCode(), ErrorCode.DATA_ALREADY_EXISTS.getMessage());
        }
        dispatchRuleMapper.insert(rule);
        return Result.success(rule.getId());
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "UPDATE", desc = "更新抢派单规则")
    @PostMapping("/dispatch-rule/update")
    public Result<Void> ruleUpdate(@RequestBody OpDispatchRule rule) {
        if (ruleCodeDuplicated(rule.getRuleCode(), rule.getId())) {
            return Result.fail(ErrorCode.DATA_ALREADY_EXISTS.getCode(), ErrorCode.DATA_ALREADY_EXISTS.getMessage());
        }
        dispatchRuleMapper.updateById(rule);
        return Result.success();
    }

    @RequiresPerm("sharing:config")
    @OperLog(module = "SHARING_CONFIG", operType = "DELETE", desc = "删除抢派单规则")
    @PostMapping("/dispatch-rule/{id}/delete")
    public Result<Void> ruleDelete(@PathVariable Long id) {
        OpDispatchRule exist = dispatchRuleMapper.selectById(id);
        if (exist == null) {
            return Result.success();
        }
        // 软删行仍占用 rule_code，会让「删除后用同码重建」撞唯一键，故退场时把编码改成不可复用的历史态
        if (exist.getRuleCode() != null && !exist.getRuleCode().contains("#")) {
            exist.setRuleCode(exist.getRuleCode() + "#" + exist.getId());
            dispatchRuleMapper.updateById(exist);
        }
        dispatchRuleMapper.deleteById(id);
        return Result.success();
    }

    /** 存活集内的编码查重（租户维度由 MP 拦截器承担）；excludeId 为改码时的自身行 */
    private boolean ruleCodeDuplicated(String ruleCode, Long excludeId) {
        if (ruleCode == null || ruleCode.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<OpDispatchRule> w = new LambdaQueryWrapper<>();
        w.eq(OpDispatchRule::getRuleCode, ruleCode);
        if (excludeId != null) {
            w.ne(OpDispatchRule::getId, excludeId);
        }
        return dispatchRuleMapper.selectCount(w) > 0;
    }
}
