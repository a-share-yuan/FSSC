package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 员工信用体系实体（共享运营实质化）。
 * <p>
 * 信用等级：
 * A(850-1000) 免审额度5000，自动通过优先
 * B(700-849) 免审额度2000，正常审核
 * C(500-699) 免审额度500，加强审核
 * D(0-499) 免审额度0，强制人工+质检必检
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("employee_credit")
public class EmployeeCredit extends TenantAuditBase {

    private Long userId;
    private String userName;
    private Integer creditScore;
    private String creditLevel;
    private BigDecimal autoApproveLimit;
    private Integer totalSubmitted;
    private Integer totalApproved;
    private Integer totalRejected;
    private Integer violationCount;
    private String lastAdjustReason;
    private LocalDateTime lastAdjustTime;
}
