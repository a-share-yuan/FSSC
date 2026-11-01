// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 合并报表底稿（gl_consolidate，带 tenant_id）。多公司报表底稿汇总合并，DRAFT → CONSOLIDATED。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_consolidate")
public class GlConsolidate extends TenantAuditBase {

    /** 报表号 */
    private String reportNo;
    /** 会计期间 yyyyMM */
    private String period;
    /** 纳入合并的公司 id 集合（逗号分隔） */
    private String companyIds;
    /** 报表底稿原始行 JSON 数组 */
    private String rawRows;
    /** DRAFT/CONSOLIDATED */
    private String status;
}