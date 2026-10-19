package com.fssc.compliance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SLA 配置实体（P1-5 共享运营实质化）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_sla_config")
public class OpSlaConfig extends TenantAuditBase {

    private String billType;
    private String billTypeName;
    private Integer targetHours;
    private Integer warningHours;
    private Long escalationGroupId;
    private Integer escalationEnabled;
    private Integer priority;
    private String status;
}
