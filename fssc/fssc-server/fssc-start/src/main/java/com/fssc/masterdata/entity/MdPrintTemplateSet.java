// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打印模板套：一次打印多个模板的组合，如「报销单套 = 主单 + 明细页 + 审批页」。
 *
 * <p>单模板满足不了实际打印场景：财务一次要打印的主单、明细附页、审批意见页往往版式不同。
 * 模板套把「一次打印要出什么」也变成配置，避免前端为每个业务写死一套打印编排。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_print_template_set")
public class MdPrintTemplateSet extends TenantAuditBase {

    /** 模板套编码（租户内唯一） */
    private String setCode;

    /** 模板套名称 */
    private String setName;

    /** 业务单据类型 */
    private String bizType;

    /** 套内打印顺序 */
    private Integer sortOrder;

    /** ENABLED / DISABLED */
    private String status;

    private String remark;
}
