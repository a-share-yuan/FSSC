package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 租户参数配置（sys_config，带 tenant_id）。editable=0 为平台下发只读参数。 */
/** D-V4R-60：sys_config 的 create_by/update_by 是 bigint（存 sys_user.id），故审计基类取 TenantAuditBase（Long）而非 BaseEntity（String）——
 *  用 String 基类时自动填充会把工号写进整型列，MySQL 严格模式报 1366，凡走本实体的写口（系统参数增改、AR 月结 PeriodLockService.lock）全部 500。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends TenantAuditBase {

    /** 参数键，如 expense.invoice.required */
    private String configKey;
    private String configName;
    private String configValue;
    /** STRING/NUMBER/BOOLEAN/JSON/DATE */
    private String valueType;
    /** 1 租户可改 0 只读 */
    private Integer editable;
}
