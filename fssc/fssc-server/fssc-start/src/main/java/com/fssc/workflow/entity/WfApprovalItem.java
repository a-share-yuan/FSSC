// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程节点「审批项」（wf_approval_item，带 tenant_id）。
 *
 * <p><b>为什么要有它</b>（改造方案 §三①）：原先引擎语义只有 {@code approved=true/false} 二值，
 * 所有审批结果都被压成「同意 / 驳回」两档，{@code wf_task_history.action} 也只是文案。
 * 业务上真实存在的第三种结果——「终止/作废」「转交某岗位复核」——无处表达。
 * 本表把「审批结果」提升为一等公民：一个节点可以有 N 个自定义审批项，各自带类别与去向。
 *
 * <p><b>与其它配置的关系（勿重复造）</b>：
 * <ul>
 *  <li>{@code wf_node_config} 管「谁来办」（参与人、多签、超时、抄送）；本表管「办出什么结果」；</li>
 *  <li>{@code reject_scope}（节点级）+ {@code wf.reject.free.enabled}（总开关）仍是驳回回节点的
 *      <b>前置校验</b>——本表的 {@code target_node} 只在校验放行时才真正搬执行，
 *      否则落回原「终止本流程」路径（与原先逐字一致）；</li>
 *  <li>本表<b>不</b>替代 {@code rejectToNodeKey} 运行时入参，二者同源同一执行面（
 *      {@code WfEngineFacade.complete(..., rejectToNodeKey)}），不新造第二条驳回通道。</li>
 * </ul>
 *
 * <p><b>零影响边界</b>：库里没有任何 {@code wf_approval_item} 行时，{@code complete()} 收不到
 * {@code approvalItemCode}，全部行为与原先逐字一致（不读表、不写变量、不记审批项）。
 *
 * <p><b>为什么不把项目名直接写进 {@code wf_task_history.action}</b>：该列是 {@code varchar(20) NOT NULL}，
 * 且 {@code WfQueryService:676} 用 {@code "REJECT".equals(action)} 反推实例状态——写文案会直接
 * 把「已驳回」误判成「已办结」。故 action 保持枚举，审批项名称落在意见前缀与流程变量（见
 * {@code WfEngineFacade}），语义等值且不破坏既有消费点。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_approval_item")
public class WfApprovalItem extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 正向结果：审批通过并继续流转（正向审批类型） */
    public static final String CAT_POSITIVE = "POSITIVE";
    /** 驳回结果：驳回（驳回审批类型，可逆向运行） */
    public static final String CAT_REJECT = "REJECT";
    /** 终止结果：终止本流程实例（终止审批类型） */
    public static final String CAT_TERMINATE = "TERMINATE";

    /** 流程定义 key */
    private String modelKey;
    /** 节点 ID（BPMN userTask id），与 wf_node_config.node_key 同一规则：精确匹配，孤儿行不生效 */
    private String nodeKey;
    /** 审批项编码（同一节点内唯一，落流程变量 ActionResult） */
    private String itemCode;
    /** 审批项名称（审批弹窗按钮文案，落流程变量 ActionResultName 与审批意见前缀） */
    private String itemName;
    /** 类别：POSITIVE 正向 / REJECT 驳回 / TERMINATE 终止 */
    private String category;
    /**
     * 关联表单操作（审批项可关联表单操作）：SAVE 保存 / AUDIT 审核 / UNAUDIT 反审核 / SUBMIT 提交。
     *
     * <p><b>能力边界（如实标注）</b>：本列目前只落流程变量 {@code ActionOper} 供订阅方与审计读取，
     * <b>引擎侧不执行表单操作</b>——单据状态机的权威在业务域（如 {@code ExpenseWfEventListener}），
     * 让流程配置去直改单据字段会形成第二个真值。运营需要该语义时，由业务域监听
     * {@code ActionOper} 变量实现，而不是把写操作塞进流程配置。
     */
    private String linkedOper;
    /**
     * 目标节点 key（仅 REJECT 类别有意义）：非空且驳回校验放行时把在办执行退回该上游节点；
     * 为空则按类别默认语义（REJECT → 终止本流程；POSITIVE → 继续流转）。
     */
    private String targetNode;
    private Integer sort;
    /** 1 启用 / 0 停用（停用的项不出现在审批弹窗，也解析不到） */
    private Integer enabled;
    private String remark;
}