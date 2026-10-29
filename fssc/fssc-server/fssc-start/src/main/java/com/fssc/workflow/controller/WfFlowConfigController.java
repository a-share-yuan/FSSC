// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.common.util.AssertUtil;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.entity.WfFlowConfig;
import com.fssc.workflow.mapper.WfFlowConfigMapper;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程配置（/api/tenant/wf/flow-config/**）。
 * 需求31 附录：金额阈值 / 超时处理 / 审批动作 / 外部集成。
 */
@RestController
@RequestMapping("/api/tenant/wf/flow-config")
@RequiredArgsConstructor
public class WfFlowConfigController {

    private final WfFlowConfigMapper configMapper;

    @RequiresPerm("wf:config:list")
    @GetMapping("/page")
    public Result<PageResult<WfFlowConfig>> page(PageQuery q, @RequestParam(required = false) String cfgType, String keyword) {
        LambdaQueryWrapper<WfFlowConfig> w = new LambdaQueryWrapper<>();
        if (cfgType != null && !cfgType.isBlank()) w.eq(WfFlowConfig::getCfgType, cfgType);
        if (keyword != null && !keyword.isBlank()) w.and(x -> x.like(WfFlowConfig::getCfgName, keyword).or().like(WfFlowConfig::getCfgCode, keyword));
        w.orderByAsc(WfFlowConfig::getSort);
        Page<WfFlowConfig> p = configMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("wf:config:list")
    @GetMapping("/by-type")
    public Result<List<WfFlowConfig>> byType(@RequestParam String cfgType) {
        return Result.success(configMapper.selectList(new LambdaQueryWrapper<WfFlowConfig>()
                .eq(WfFlowConfig::getCfgType, cfgType).eq(WfFlowConfig::getStatus, "NORMAL")
                .orderByAsc(WfFlowConfig::getSort)));
    }

    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_FLOW_CONFIG", operType = "INSERT", desc = "新增流程配置")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody WfFlowConfig c) {
        validate(c);
        if (cfgKeyTaken(c, null)) return duplicateCfg();
        configMapper.insert(c);
        return Result.success(c.getId());
    }

    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_FLOW_CONFIG", operType = "UPDATE", desc = "更新流程配置")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody WfFlowConfig c) {
        validate(c);
        if (cfgKeyTaken(c, c.getId())) return duplicateCfg();
        configMapper.updateById(c);
        return Result.success();
    }

    /** 必填办结（批次 2C，与前端 FlowConfig「全局配置」/ GenericList 流程配置弹窗同一规则）。 */
    private void validate(WfFlowConfig c) {
        AssertUtil.notBlank(c.getCfgType(),
                ErrorCode.PARAM_INVALID, "请选择配置类型");
        AssertUtil.notBlank(c.getCfgCode(),
                ErrorCode.PARAM_INVALID, "请输入配置编码");
        AssertUtil.notBlank(c.getCfgName(),
                ErrorCode.PARAM_INVALID, "请输入配置名称");
    }

    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_FLOW_CONFIG", operType = "DELETE", desc = "删除流程配置")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        WfFlowConfig exist = configMapper.selectById(id);
        if (exist == null) return Result.success();
        // 键是复合的（cfg_type＋cfg_code），逻辑删行仍占组合位；退场时把 cfg_code 改写为历史态（取负不需要，本表编码为字符串）
        String cfgCode = exist.getCfgCode();
        if (cfgCode != null && !cfgCode.contains("#")) {
            exist.setCfgCode(cfgCode + "#" + exist.getId());
            configMapper.updateById(exist);
        }
        configMapper.deleteById(id);
        return Result.success();
    }

    /** 配置键＝(cfg_type, cfg_code) 复合；租户条件由 MP 拦截器叠加，软删行由 @TableLogic 自动过滤 */
    private boolean cfgKeyTaken(WfFlowConfig c, Long excludeId) {
        List<WfFlowConfig> hits = configMapper.selectList(new LambdaQueryWrapper<WfFlowConfig>()
                .eq(WfFlowConfig::getCfgType, c.getCfgType())
                .eq(WfFlowConfig::getCfgCode, c.getCfgCode()));
        for (WfFlowConfig hit : hits) {
            if (excludeId == null || !Objects.equals(hit.getId(), excludeId)) {
                return true;
            }
        }
        return false;
    }

    private <T> Result<T> duplicateCfg() {
        return Result.fail(ErrorCode.DATA_ALREADY_EXISTS.getCode(),
                ErrorCode.DATA_ALREADY_EXISTS.getMessage());
    }
}
