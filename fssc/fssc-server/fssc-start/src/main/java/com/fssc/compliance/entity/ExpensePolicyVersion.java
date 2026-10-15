package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 费用政策版本（expense_policy_version，带 tenant_id）。政策变更的版本快照与审批记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("expense_policy_version")
public class ExpensePolicyVersion extends TenantAuditBase {

    /** 政策ID */
    private Long policyId;

    /** 版本号（VARCHAR(16)，如：V1.0、V2.1） */
    private String version;

    /** 快照数据（JSON 格式） */
    private String snapshotData;

    /** 变更日志 */
    private String changeLog;

    /** 审批人ID */
    private Long approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    /** 状态：DRAFT-草稿 APPROVED-已审批 ACTIVATED-已激活 ARCHIVED-已归档 */
    private String status;
}