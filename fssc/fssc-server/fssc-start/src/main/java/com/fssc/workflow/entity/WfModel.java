// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程模型（wf_model，BPMN 设计器草稿，带 tenant_id）。
 * 注意：wf_* 表 create_by/update_by 在 DDL 中为 bigint，继承 TenantAuditBase（Long 审计）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_model")
public class WfModel extends TenantAuditBase {

    /** 模型标识（即流程定义 key） */
    private String modelKey;
    private String modelName;
    /** EXPENSE费用/BUDGET预算/CONTRACT合同/PAYMENT付款 */
    private String category;
    /** 业务单据类型（模板模型） */
    private String bizType;
    /** 来源 TEMPLATE 需求模板 / USER 用户自定义 */
    private String source;
    /** 生效组织范围 ALL全集团 / ORG指定公司（UC01） */
    private String orgScope;
    /** 适用公司ID列表（逗号分隔，orgScope=ORG 时生效，UC01） */
    private String orgIds;
    /** 允许撤回 1是/0否（UC07） */
    private Boolean allowWithdraw;
    /** 驳回策略 TO_INITIATOR驳回至发起人/TO_PREV驳回至上一节点（UC07） */
    private String rejectStrategy;
    /** 开启代理审批 1是/0否（UC07） */
    private Boolean enableDelegate;
    /** 排除组织公司ID列表（逗号分隔，UC07） */
    private String excludeOrgIds;
    /** BPMN2.0 XML */
    private String bpmnXml;
    /** 表单/节点配置 JSON（设计器产物） */
    private String formJson;
    /** 模型版本 */
    private Integer version;
    /** 最近一次部署 ID（Flowable） */
    private String deployId;
    /** 最近一次流程定义 ID（Flowable） */
    private String procDefId;
    /** DRAFT草稿/DEPLOYED已部署/DISABLED已停用 */
    private String status;
    private String remark;
}
