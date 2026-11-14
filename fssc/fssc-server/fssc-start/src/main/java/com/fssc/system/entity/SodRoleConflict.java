package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SoD 不相容职务分离互斥规则（sod_role_conflict）。
 * tenant_id NULL = 平台级规则（全租户生效）；非空 = 租户自定义覆盖规则。
 * 互斥对无序：检查时 role_code_a / role_code_b 任一方向命中即视为冲突。
 * 【全局表】已登记到 {@code FsscTenantLineHandler} 忽略名单，不自动追加 tenant_id，由 SoDService 手工取规则。
 */
@Data
@TableName("sod_role_conflict")
public class SodRoleConflict implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String roleCodeA;
    private String roleCodeB;
    private String reason;
    private LocalDateTime createTime;
}
