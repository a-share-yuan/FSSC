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
 * 数据一致性对账异常流水（audit_reconcile，只追加，无 del_flag，禁止物理删除）。
 * 长链路一致性对账：单据总额 = 凭证借贷合计 = 已付 + 未付，不一致落此表。
 */
@TableName("audit_reconcile")
public class AuditReconcile implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 单据类型 APPLY/CLAIM/LOAN/REPAY/PAY */
    private String billType;
    private Long billId;
    private String billNo;
    /** 核对项 VOUCHER 凭证/PAYMENT 付款/STATUS 状态 */
    private String checkItem;
    private String expectValue;
    private String actualValue;
    private String diffDesc;
    /** OPEN 未处理 / RESOLVED 已处理 */
    private String status;
    private Long resolveBy;
    private LocalDateTime resolveTime;
    private String resolveRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getBillType() { return billType; }
    public void setBillType(String billType) { this.billType = billType; }

    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }

    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }

    public String getCheckItem() { return checkItem; }
    public void setCheckItem(String checkItem) { this.checkItem = checkItem; }

    public String getExpectValue() { return expectValue; }
    public void setExpectValue(String expectValue) { this.expectValue = expectValue; }

    public String getActualValue() { return actualValue; }
    public void setActualValue(String actualValue) { this.actualValue = actualValue; }

    public String getDiffDesc() { return diffDesc; }
    public void setDiffDesc(String diffDesc) { this.diffDesc = diffDesc; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getResolveBy() { return resolveBy; }
    public void setResolveBy(Long resolveBy) { this.resolveBy = resolveBy; }

    public LocalDateTime getResolveTime() { return resolveTime; }
    public void setResolveTime(LocalDateTime resolveTime) { this.resolveTime = resolveTime; }

    public String getResolveRemark() { return resolveRemark; }
    public void setResolveRemark(String resolveRemark) { this.resolveRemark = resolveRemark; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
