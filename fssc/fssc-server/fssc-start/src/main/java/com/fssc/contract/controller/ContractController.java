package com.fssc.contract.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.contract.entity.ContContract;
import com.fssc.contract.entity.ContPayCondition;
import com.fssc.contract.entity.ContPayRecord;
import com.fssc.contract.mapper.ContPayConditionMapper;
import com.fssc.contract.mapper.ContPayRecordMapper;
import com.fssc.contract.service.ContractService;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 合同管理（/api/tenant/contract/**）：合同类型/新增审批/变更/补充协议 */
@RestController
@RequestMapping("/api/tenant/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService service;
    private final ContPayConditionMapper conditionMapper;
    private final ContPayRecordMapper recordMapper;

    // 只读查询放开给所有登录租户用户：单据发起页合同下拉必用（写操作仍需权限）
    @GetMapping("/page")
    public Result<PageResult<ContContract>> page(PageQuery query, String keyword) {
        return Result.success(service.page(query.getPageNum(), query.getPageSize(), keyword));
    }

    @RequiresPerm("contract:create")
    @OperLog(module = "CONTRACT", operType = "INSERT", desc = "新增合同")
    @PostMapping("/create")
    public Result<Long> create(@RequestBody ContContract e) {
        return Result.success(service.create(e));
    }

    @RequiresPerm("contract:update")
    @OperLog(module = "CONTRACT", operType = "UPDATE", desc = "更新合同")
    @PostMapping("/update")
    public Result<Void> update(@RequestBody ContContract e) {
        service.update(e);
        return Result.success();
    }

    @RequiresPerm("contract:update")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @RequiresPerm("contract:update")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        service.submit(id);
        return Result.success();
    }

    /** 驳回合同编辑重提：REJECTED → DRAFT；修改后重新提交产生新流程实例。 */
    @RequiresPerm("contract:update")
    @OperLog(module = "CONTRACT", operType = "UPDATE", desc = "驳回合同转编辑重提")
    @PostMapping("/{id}/edit")
    public Result<Void> editForResubmit(@PathVariable Long id) {
        service.returnToEdit(id);
        return Result.success();
    }

    @RequiresPerm("contract:update")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        service.approve(id);
        return Result.success();
    }

    @RequiresPerm("contract:update")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        service.reject(id);
        return Result.success();
    }

    @RequiresPerm("contract:update")
    @OperLog(module = "CONTRACT", operType = "UPDATE", desc = "合同变更")
    @PostMapping("/{id}/change")
    public Result<Long> change(@PathVariable Long id) {
        return Result.success(service.change(id));
    }

    @RequiresPerm("contract:update")
    @OperLog(module = "CONTRACT", operType = "UPDATE", desc = "手工关闭合同")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        service.close(id);
        return Result.success();
    }

    @RequiresPerm("contract:update")
    @PostMapping("/{id}/supplement")
    public Result<Long> supplement(@PathVariable Long id) {
        return Result.success(service.supplement(id));
    }

    // ---------------- 付款条件 / 支付记录（需求 5.2） ----------------

    @RequiresPerm("contract:list")
    @GetMapping("/{id}/conditions")
    public Result<List<ContPayCondition>> conditions(@PathVariable Long id) {
        return Result.success(conditionMapper.selectList(new LambdaQueryWrapper<ContPayCondition>()
                .eq(ContPayCondition::getContractId, id).orderByAsc(ContPayCondition::getPlanDate)));
    }

    @RequiresPerm("contract:update")
    @OperLog(module = "CONTRACT", operType = "INSERT", desc = "新增合同付款条件")
    @PostMapping("/condition/create")
    public Result<Long> conditionCreate(@RequestBody ContPayCondition c) {
        conditionMapper.insert(c);
        return Result.success(c.getId());
    }

    @RequiresPerm("contract:update")
    @OperLog(module = "CONTRACT", operType = "DELETE", desc = "删除合同付款条件")
    @PostMapping("/condition/{id}/delete")
    public Result<Void> conditionDelete(@PathVariable Long id) {
        conditionMapper.deleteById(id);
        return Result.success();
    }

    @RequiresPerm("contract:list")
    @GetMapping("/{id}/records")
    public Result<List<ContPayRecord>> records(@PathVariable Long id) {
        return Result.success(recordMapper.selectList(new LambdaQueryWrapper<ContPayRecord>()
                .eq(ContPayRecord::getContractId, id).orderByDesc(ContPayRecord::getPayDate)));
    }

    @RequiresPerm("contract:update")
    @OperLog(module = "CONTRACT", operType = "INSERT", desc = "新增合同支付记录")
    @PostMapping("/record/create")
    public Result<Long> recordCreate(@RequestBody ContPayRecord r) {
        recordMapper.insert(r);
        return Result.success(r.getId());
    }

    @RequiresPerm("contract:update")
    @OperLog(module = "CONTRACT", operType = "DELETE", desc = "删除合同支付记录")
    @PostMapping("/record/{id}/delete")
    public Result<Void> recordDelete(@PathVariable Long id) {
        recordMapper.deleteById(id);
        return Result.success();
    }
}
