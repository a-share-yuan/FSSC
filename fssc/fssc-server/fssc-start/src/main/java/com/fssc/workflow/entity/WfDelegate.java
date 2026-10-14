// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程委托代理（wf_delegate，带 tenant_id）。
 * 出差/休假期间由他人代办。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_delegate")
public class WfDelegate extends TenantAuditBase {

    /** 委托人用户 ID */
    private Long fromUserId;
    /** 受托人用户 ID */
    private Long toUserId;
    /** 委托单据类型，逗号分隔，空表示全部 */
    private String bizTypes;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private String reason;
    /**
     * NORMAL 生效 / DISABLED 人工取消 / EXPIRED 到期自动回收（{@code WfDelegateExpireJob}）。
     *
     * <p>注意：**生效判定看的是时间区间**（`begin_time &lt;= now &lt;= end_time`，见
     * {@code WfDelegateService.activeDelegateTo}），不看本列——因此即便本列尚未被 Job 刷成
     * EXPIRED，过期委托也不会生效。本列只用于展示与审计口径（前端 `MyDelegate.vue`
     * 对 EXPIRED 显示「已过期」）。
     */
    private String status;

    /**
     * 委托人姓名——<b>不入库</b>，由 {@code WfDelegateService.page} 批量回填。
     * 实体本身只存 fromUserId/toUserId，此前无人回填 ⇒ 前端「委托人/代理人」两列恒空白
     * （治理文档 §11.6-4）。
     */
    @TableField(exist = false)
    private String fromUserName;

    /** 受托人姓名，同上（不入库，列表展示用）。 */
    @TableField(exist = false)
    private String toUserName;
}
