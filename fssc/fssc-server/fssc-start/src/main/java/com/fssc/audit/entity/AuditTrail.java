// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 关键变更审计流水（audit_trail，只追加，无 del_flag，禁止物理删除）。
 * P0-3 审计记录：凭证修改/付款账户变更/审批意见/预算调整/校验和失败等关键变更 before/after 记录。
 */
@TableName("audit_trail")
public class AuditTrail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 业务类型（单据/凭证/付款/预算） */
    private String bizType;
    private Long bizId;
    private String bizNo;
    /** 变更对象类型（表名/类名） */
    private String objType;
    private String objId;
    /** INSERT/UPDATE/APPROVE/PAY/REJECT/CHECKSUM/WITHDRAW */
    private String action;
    private String beforeJson;
    private String afterJson;
    /** 操作人用户ID */
    private Long operator;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 展示字段：操作人「姓名（工号）」，由查询侧联表回填，不写入数据库 */
    @TableField(exist = false)
    private String operatorName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }

    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }

    public String getBizNo() { return bizNo; }
    public void setBizNo(String bizNo) { this.bizNo = bizNo; }

    public String getObjType() { return objType; }
    public void setObjType(String objType) { this.objType = objType; }

    public String getObjId() { return objId; }
    public void setObjId(String objId) { this.objId = objId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }

    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }

    public Long getOperator() { return operator; }
    public void setOperator(Long operator) { this.operator = operator; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
}
