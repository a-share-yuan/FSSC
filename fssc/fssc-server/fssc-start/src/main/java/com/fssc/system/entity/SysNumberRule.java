package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 单据编号规则（sys_number_rule，带 tenant_id）。
 * 规则编码租户内唯一（uk_tenant_rule_code），如 EXP_CLAIM/BX。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_number_rule")
public class SysNumberRule extends TenantAuditBase {

    /** 规则编码，如 EXP_CLAIM */
    private String ruleCode;
    private String ruleName;
    /** 前缀，如 BX */
    private String prefix;
    /** 日期格式，如 yyyyMMdd，为空表示不含日期 */
    private String dateFormat;
    /** 流水号位数 */
    private Integer seqLength;
    /** 流水重置周期：NONE不重置/DAY按日/MONTH按月/YEAR按年 */
    private String resetCycle;
    /** 步长 */
    private Integer step;
    /** NORMAL/DISABLED */
    private String status;
    private String remark;
}
