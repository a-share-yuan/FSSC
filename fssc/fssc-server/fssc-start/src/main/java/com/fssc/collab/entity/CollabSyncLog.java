// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.collab.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 协同平台接口同步日志（蓝图异常处理范式：接口失败记日志，定时重试/人工处理）。
 * 覆盖：SCP打通点3(三单匹配记账)/5(付款回写)/6(认证)/7(关账冻结通知)、
 * AR接口1(ERP发运推送)/接口2-3(发票回写ERP)/对账状态同步。
 */
@Data
@TableName("collab_sync_log")
public class CollabSyncLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 接口编码：SCP_IF03_MATCH_POST/SCP_IF05_PAY/SCP_IF06_CERTIFY/SCP_IF07_FREEZE/SCP_IF07_UNFREEZE/AR_IF01_SHIPPING/AR_IF02_INVOICE_WB/AR_IF10_RECON_SYNC */
    private String ifaceCode;
    /** SCP_TO_EBS / EBS_TO_SHARED / SHARED_TO_EBS / AP_TO_SCP / CCP_TO_SHARED */
    private String direction;
    /** 关联单号 */
    private String refNo;
    /** SUCCESS / FAILED / MOCK（未配置真实通道时模拟成功） */
    private String status;
    private String message;
    private String payload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public static final String ST_SUCCESS = "SUCCESS";
    public static final String ST_FAILED = "FAILED";
    public static final String ST_MOCK = "MOCK";

    public static final String IF_SCP_MATCH_POST = "SCP_IF03_MATCH_POST";
    public static final String IF_SCP_PAY = "SCP_IF05_PAY";
    public static final String IF_SCP_CERTIFY = "SCP_IF06_CERTIFY";
    public static final String IF_SCP_FREEZE = "SCP_IF07_FREEZE";
    public static final String IF_SCP_UNFREEZE = "SCP_IF07_UNFREEZE";
    public static final String IF_AR_SHIPPING = "AR_IF01_SHIPPING";
    public static final String IF_AR_INVOICE_WB = "AR_IF02_INVOICE_WB";
    public static final String IF_AR_RECON_SYNC = "AR_IF10_RECON_SYNC";
}
