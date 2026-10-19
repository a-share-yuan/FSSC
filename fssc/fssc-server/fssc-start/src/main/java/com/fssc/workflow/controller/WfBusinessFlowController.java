package com.fssc.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.entity.WfBusinessFlow;
import com.fssc.workflow.mapper.WfBusinessFlowMapper;
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
 * 业务流程（/api/tenant/wf/biz-flow/**）。
 * 业务流程节点存数据库；按业务单据类型生成初始 BPMN 模板供可视化设计器加载。
 */
@RestController
@RequestMapping("/api/tenant/wf/biz-flow")
@RequiredArgsConstructor
public class WfBusinessFlowController {

    private final WfBusinessFlowMapper flowMapper;
    private final com.fssc.workflow.service.WfBpmnTemplateService bpmnTemplateService;

    @RequiresPerm("wf:bizflow:list")
    @GetMapping("/page")
    public Result<PageResult<WfBusinessFlow>> page(PageQuery q, String bizType, String keyword) {
        LambdaQueryWrapper<WfBusinessFlow> w = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) w.eq(WfBusinessFlow::getBizType, bizType);
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(WfBusinessFlow::getFlowName, keyword).or().like(WfBusinessFlow::getFlowCode, keyword));
        }
        w.orderByAsc(WfBusinessFlow::getId);
        Page<WfBusinessFlow> p = flowMapper.selectPage(Page.of(q.getPageNum(), q.getPageSize()), w);
        return Result.success(PageResult.of(p.getRecords(), p.getTotal(), q.getPageNum(), q.getPageSize()));
    }

    @RequiresPerm("wf:bizflow:list")
    @GetMapping("/by-type")
    public Result<WfBusinessFlow> byType(@RequestParam String bizType) {
        return Result.success(flowMapper.selectOne(new LambdaQueryWrapper<WfBusinessFlow>()
                .eq(WfBusinessFlow::getBizType, bizType).last("limit 1")));
    }

    @RequiresPerm("wf:bizflow:list")
    @GetMapping("/types")
    public Result<List<WfBusinessFlow>> types() {
        return Result.success(flowMapper.selectList(new LambdaQueryWrapper<WfBusinessFlow>()
                .eq(WfBusinessFlow::getStatus, "NORMAL").orderByAsc(WfBusinessFlow::getId)));
    }

    @RequiresPerm("wf:bizflow:update")
    @OperLog(module = "WF_BIZ_FLOW", operType = "INSERT", desc = "新增业务流程")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody WfBusinessFlow f) {
        if (bizTypeTaken(f.getBizType(), null)) return duplicateBizType();
        flowMapper.insert(f);
        return Result.success(f.getId());
    }

    @RequiresPerm("wf:bizflow:update")
    @OperLog(module = "WF_BIZ_FLOW", operType = "UPDATE", desc = "更新业务流程")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody WfBusinessFlow f) {
        if (bizTypeTaken(f.getBizType(), f.getId())) return duplicateBizType();
        flowMapper.updateById(f);
        return Result.success();
    }

    @RequiresPerm("wf:bizflow:update")
    @OperLog(module = "WF_BIZ_FLOW", operType = "DELETE", desc = "删除业务流程")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        WfBusinessFlow exist = flowMapper.selectById(id);
        if (exist == null) return Result.success();
        // 逻辑删行仍占用 biz_type，会让「删除后重建同类型流程」撞唯一键；退场时改写为历史态让位可用
        String bizType = exist.getBizType();
        if (bizType != null && !bizType.contains("#")) {
            exist.setBizType(bizType + "#" + exist.getId());
            flowMapper.updateById(exist);
        }
        flowMapper.deleteById(id);
        return Result.success();
    }

    /** 业务单据类型在本租户内存活集唯一（租户条件由 MP 拦截器叠加，软删行由 @TableLogic 自动过滤） */
    private boolean bizTypeTaken(String bizType, Long excludeId) {
        if (bizType == null || bizType.isBlank()) {
            return false;
        }
        List<WfBusinessFlow> hits = flowMapper.selectList(new LambdaQueryWrapper<WfBusinessFlow>()
                .eq(WfBusinessFlow::getBizType, bizType));
        for (WfBusinessFlow hit : hits) {
            if (excludeId == null || !java.util.Objects.equals(hit.getId(), excludeId)) {
                return true;
            }
        }
        return false;
    }

    private <T> Result<T> duplicateBizType() {
        return Result.fail(com.fssc.common.core.ErrorCode.DATA_ALREADY_EXISTS.getCode(),
                com.fssc.common.core.ErrorCode.DATA_ALREADY_EXISTS.getMessage());
    }

    /** 按业务单据类型生成初始 BPMN 模板（业务流程节点 → BPMN XML 含 DI，供可视化设计器加载） */
    @RequiresPerm("wf:bizflow:list")
    @GetMapping("/template")
    public Result<String> template(@RequestParam String bizType) {
        WfBusinessFlow f = flowMapper.selectOne(new LambdaQueryWrapper<WfBusinessFlow>()
                .eq(WfBusinessFlow::getBizType, bizType).last("limit 1"));
        if (f == null || f.getNodeJson() == null || f.getNodeJson().isBlank()) {
            return Result.success(bpmnTemplateService.build(bizType, bizType, "[\"开始\"]"));
        }
        return Result.success(bpmnTemplateService.build(f.getFlowName() == null ? bizType : f.getFlowName(), bizType, f.getNodeJson()));
    }
}
