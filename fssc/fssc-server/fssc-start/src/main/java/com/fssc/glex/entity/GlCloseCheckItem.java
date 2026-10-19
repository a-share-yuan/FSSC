// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.glex.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * "阶段/任务/责任人/计划完成时间"）。
 * close() 硬校验：该公司当期清单存在且全部 DONE 才允许月结；缺行/未勾齐一律拦截。
 */
/** D-V4R-60 同族：gl_close_check_item 的 create_by/update_by 是 bigint ⇒ 审计基类取 TenantAuditBase（Long），否则「生成检查清单」插行即 1366。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_close_check_item")
public class GlCloseCheckItem extends TenantAuditBase {

    /** 公司 */
    private Long companyId;

    /** 会计期间 yyyyMM */
    private String period;

    /** 检查项名称（如：全部凭证已过账／银行对账已平／费用预提已完成） */
    private String itemName;

    /** 责任角色（role_code，如 FIN_GL/CASHIER/FIN_AR/FA_ASSET_ACCOUNT） */
    private String ownerRole;

    /** 计划完成时间（K129"计划完成时间"） */
    private LocalDateTime planTime;

    /** 状态：PENDING 未完成 / DONE 已完成 */
    private String status;

    /** 完成时间 */
    private LocalDateTime checkTime;

    /** 完成人（user_name） */
    private String checker;

    /** 备注 */
    private String remark;
}
