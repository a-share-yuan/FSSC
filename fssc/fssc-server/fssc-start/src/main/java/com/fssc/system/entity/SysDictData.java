// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 租户字典数据（sys_dict_data，带 tenant_id） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends TenantAuditBase {

    /** 字典类型编码 */
    private String dictType;
    /** 展示标签 */
    private String dictLabel;
    /** 存储值 */
    private String dictValue;
    /** primary/success/warning/danger/info */
    private String cssClass;
    private Integer sort;
    /** NORMAL/DISABLED */
    private String status;
}
