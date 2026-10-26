// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.controller;

import com.fssc.common.core.PageQuery;
import com.fssc.common.core.PageResult;
import com.fssc.common.core.Result;
import com.fssc.framework.log.OperLog;
import com.fssc.framework.security.annotation.RequiresPerm;
import com.fssc.workflow.entity.WfDelegate;
import com.fssc.workflow.entity.WfModel;
import com.fssc.workflow.entity.WfNodeConfig;
import com.fssc.workflow.entity.WfProcessConfig;
import com.fssc.workflow.service.NodeOrgResolver;
import com.fssc.workflow.service.WfDelegateService;
import com.fssc.workflow.service.WfModelService;
import com.fssc.workflow.service.WfNodeConfigService;
import com.fssc.workflow.service.WfProcessConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 流程建模与配置（/api/tenant/wf/config/**）：
 * 模型草稿、单据类型流程绑定、节点审批人、委托代理。
 *
 * <p><b>G4 治理：三权分立</b>——本类横跨三中心，权限码按中心归位：
 * <ul>
 *   <li><b>设计中心</b>（改 BPMN/模型）：{@code wf:design:*}&nbsp;＋旧码 {@code wf:model:*};</li>
 *   <li><b>配置中心</b>（节点配置/绑定/规则）：{@code wf:config:*}&nbsp;＋旧码 {@code wf:node:*};</li>
 *   <li><b>管理中心</b>（在途干预/异常处理）：{@code wf:manage:intervene}&nbsp;＋旧码 {@code wf:config:resolve}。</li>
 * </ul>
 * 全部走 {@code @RequiresPerm} 的<b>多码 OR</b> 并同时保留旧码 ⇒ 上线不出现大面积 403；
 * 新码授权到位后再摘旧码（见 {@code sql/V2.2.0_g4_governance_20260924.sql}）。</p>
 */
@RestController
@RequestMapping("/api/tenant/wf/config")
@RequiredArgsConstructor
public class WfConfigController {

    private final WfModelService modelService;
    private final WfProcessConfigService processConfigService;
    private final WfNodeConfigService nodeConfigService;
    private final WfDelegateService delegateService;
    private final NodeOrgResolver nodeOrgResolver;
    /** G2 参与人解析（wf_assignee_scheme / wf_assignee_dim）：只读预览接口用 */
    private final com.fssc.workflow.service.WfAssigneeResolver assigneeResolver;

    // ---------- 流程模型 ----------

    @RequiresPerm({"wf:design:list", "wf:model:list"})
    @GetMapping("/model/page")
    public Result<PageResult<WfModel>> modelPage(PageQuery query, String keyword, String category, String status) {
        return Result.success(modelService.page(query.getPageNum(), query.getPageSize(), keyword, category, status));
    }

    @RequiresPerm({"wf:design:list", "wf:model:list"})
    @GetMapping("/model/{id}")
    public Result<WfModel> modelDetail(@PathVariable Long id) {
        return Result.success(modelService.detail(id));
    }

    @RequiresPerm({"wf:design:create", "wf:model:create"})
    @OperLog(module = "WF_MODEL", operType = "INSERT", desc = "新增流程模型")
    @PostMapping("/model/create")
    public Result<Long> modelCreate(@RequestBody WfModel model) {
        return Result.success(modelService.create(model));
    }

    @RequiresPerm({"wf:design:update", "wf:model:update"})
    @OperLog(module = "WF_MODEL", operType = "UPDATE", desc = "更新流程模型")
    @PostMapping("/model/update")
    public Result<Void> modelUpdate(@RequestBody WfModel model) {
        modelService.update(model);
        return Result.success();
    }

    @RequiresPerm({"wf:design:update", "wf:model:update"})
    @OperLog(module = "WF_MODEL", operType = "UPDATE", desc = "模型部署/停用")
    @PostMapping("/model/{id}/enable")
    public Result<Void> modelEnable(@PathVariable Long id, @RequestParam boolean enable) {
        modelService.enable(id, enable);
        return Result.success();
    }

    @RequiresPerm({"wf:design:delete", "wf:model:delete"})
    @OperLog(module = "WF_MODEL", operType = "DELETE", desc = "删除流程模型")
    @PostMapping("/model/{id}/delete")
    public Result<Void> modelDelete(@PathVariable Long id) {
        modelService.delete(id);
        return Result.success();
    }

    // ---------- 单据类型流程绑定 ----------

    @RequiresPerm("wf:config:list")
    @GetMapping("/process/page")
    public Result<PageResult<WfProcessConfig>> processPage(PageQuery query, String bizType, String keyword, String modelKey) {
        return Result.success(processConfigService.page(query.getPageNum(), query.getPageSize(), bizType, keyword, modelKey));
    }

    @RequiresPerm("wf:config:create")
    @OperLog(module = "WF_PROCESS_CONFIG", operType = "INSERT", desc = "新增流程绑定")
    @PostMapping("/process/create")
    public Result<Long> processCreate(@RequestBody WfProcessConfig cfg) {
        return Result.success(processConfigService.create(cfg));
    }

    @RequiresPerm("wf:config:update")
    @OperLog(module = "WF_PROCESS_CONFIG", operType = "UPDATE", desc = "更新流程绑定")
    @PostMapping("/process/update")
    public Result<Void> processUpdate(@RequestBody WfProcessConfig cfg) {
        processConfigService.update(cfg);
        return Result.success();
    }

    @RequiresPerm("wf:config:delete")
    @OperLog(module = "WF_PROCESS_CONFIG", operType = "DELETE", desc = "删除流程绑定")
    @PostMapping("/process/{id}/delete")
    public Result<Void> processDelete(@PathVariable Long id) {
        processConfigService.delete(id);
        return Result.success();
    }

    /** 路由解析：按单据类型+金额命中流程定义 key（业务发起流程时调用） */
    @RequiresPerm("wf:config:resolve")
    @PostMapping("/process/resolve")
    public Result<String> processResolve(@RequestBody Map<String, Object> body) {
        String bizType = (String) body.get("bizType");
        Number amount = (Number) body.get("amount");
        Number companyId = (Number) body.get("companyId");
        Number deptId = (Number) body.get("deptId");
        String key = processConfigService.resolveModelKey(bizType,
                amount == null ? null : java.math.BigDecimal.valueOf(amount.doubleValue()),
                companyId == null ? null : companyId.longValue(),
                deptId == null ? null : deptId.longValue());
        return Result.success(key);
    }

    // ---------- 节点审批人配置 ----------

    @RequiresPerm({"wf:config:list", "wf:node:list"})
    @GetMapping("/node/list")
    public Result<List<WfNodeConfig>> nodeList(@RequestParam(required = false) String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return Result.success(java.util.Collections.emptyList());
        }
        return Result.success(nodeConfigService.listByModel(modelKey));
    }

    @RequiresPerm({"wf:config:create", "wf:node:create"})
    @OperLog(module = "WF_NODE_CONFIG", operType = "INSERT", desc = "新增节点配置")
    @PostMapping("/node/create")
    public Result<Long> nodeCreate(@RequestBody WfNodeConfig cfg) {
        return Result.success(nodeConfigService.create(cfg));
    }

    @RequiresPerm({"wf:config:update", "wf:node:update"})
    @OperLog(module = "WF_NODE_CONFIG", operType = "UPDATE", desc = "更新节点配置")
    @PostMapping("/node/update")
    public Result<Void> nodeUpdate(@RequestBody WfNodeConfig cfg) {
        nodeConfigService.update(cfg);
        return Result.success();
    }

    @RequiresPerm({"wf:config:delete", "wf:node:delete"})
    @OperLog(module = "WF_NODE_CONFIG", operType = "DELETE", desc = "删除节点配置")
    @PostMapping("/node/{id}/delete")
    public Result<Void> nodeDelete(@PathVariable Long id) {
        nodeConfigService.delete(id);
        return Result.success();
    }

    /**
     * 节点角色持有人体检（方案 §11.2 C-1，只读）：配置页选完审批角色后即时出红/黄字，
     * 避免"ROLE 节点挂了个该公司 0 人的角色"要到运行期才暴露。
     */
    @RequiresPerm({"wf:config:list", "wf:node:list"})
    @GetMapping("/node/role-holders")
    public Result<Map<String, Object>> nodeRoleHolders(@RequestParam(required = false) String roleCode) {
        return Result.success(nodeOrgResolver.roleHolderMatrix(roleCode));
    }

    /**
     * 节点参与人只读解析预览（G2 2026-09-24）：回答"这个节点现在会解析到谁"。
     *
     * <p>入参两种给法：{@code modelKey + nodeKey}（配置页视角，没有在办任务也能看）；
     * 或 {@code taskId}（待办视角，缺的 modelKey/nodeKey 由任务反推，且 EXCLUDE 组合模式需要它取候选组）。
     * <b>纯查询</b>：不指派、不改流程、不写任何表。
     *
     * <p>返回里逐套方案、逐个维度回显命中人数与 userIds，以及是否降级（{@code hit=false}）；
     * 全部方案都不中时给出 notice 说明运行时会抛业务异常——与执行面（applyNodeConfig 的
     * default 分支）同源，避免"预览说有人、运行时没人"。
     */
    @RequiresPerm("wf:config:list")
    @GetMapping("/node/assignee-preview")
    public Result<Map<String, Object>> nodeAssigneePreview(@RequestParam(required = false) String modelKey,
                                                          @RequestParam(required = false) String nodeKey,
                                                          @RequestParam(required = false) String taskId,
                                                          @RequestParam(required = false) String procInstId) {
        return Result.success(assigneeResolver.previewByTask(modelKey, nodeKey, procInstId, taskId));
    }

    // ---------- 委托代理 ----------

    @RequiresPerm("wf:delegate:list")
    @GetMapping("/delegate/page")
    public Result<PageResult<WfDelegate>> delegatePage(PageQuery query,
                                                       @RequestParam(required = false) Long fromUserId,
                                                       @RequestParam(required = false) Long toUserId,
                                                       @RequestParam(required = false) String scope) {
        // scope 缺省 = mine（本人委托）。E-12：改造前不带任何身份条件，普通员工可翻全租户授权关系。
        return Result.success(delegateService.page(query.getPageNum(), query.getPageSize(),
                fromUserId, toUserId, scope));
    }

    @RequiresPerm("wf:delegate:create")
    @OperLog(module = "WF_DELEGATE", operType = "INSERT", desc = "新增委托")
    @PostMapping("/delegate/create")
    public Result<Long> delegateCreate(@RequestBody WfDelegate delegate) {
        return Result.success(delegateService.create(delegate));
    }

    @RequiresPerm("wf:delegate:update")
    @OperLog(module = "WF_DELEGATE", operType = "UPDATE", desc = "取消委托")
    @PostMapping("/delegate/{id}/cancel")
    public Result<Void> delegateCancel(@PathVariable Long id) {
        delegateService.cancel(id);
        return Result.success();
    }
}
