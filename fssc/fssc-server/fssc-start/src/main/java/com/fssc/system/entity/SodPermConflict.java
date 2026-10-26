// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SoD 权限码级互斥规则（sod_perm_conflict）。
 * tenant_id NULL = 平台级规则（全租户生效）；非空 = 租户自定义覆盖规则。
 * 与 sod_role_conflict（角色码两两互斥）互补：本表按"权限码正则"做用户级并集冲突校验，
 * 能捕获"单看每个角色都合法、组合起来违规"的隐蔽场景。
 * 【全局表】登记在 FsscTenantLineHandler 忽略名单，不自动追加 tenant_id，
 * 由 SoDService 手工按 (tenant_id IS NULL OR tenant_id = 当前租户) 读取。
 */
@Data
@TableName("sod_perm_conflict")
public class SodPermConflict implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String ruleCode;
    private String ruleName;
    private String risk;
    private String patternA;
    private String patternB;
    private String note;
    private String status;
    private LocalDateTime createTime;
}
