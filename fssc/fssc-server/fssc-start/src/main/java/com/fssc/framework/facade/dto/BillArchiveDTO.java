// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 单据归档 DTO：替代 ExpBill 实体在档案域中的传递，
 * 使档案域不直接依赖 expense 包的实体类。
 */
@Data
public class BillArchiveDTO {

    private Long id;
    private String billNo;
    private String title;
    private String billType;
    private String status;
    private String archiveStatus;
    private String imageStatus;
    /** 实物流交付状态（NULL/UNSIGNED/SIGNED/NOT_REQUIRED），归档前置校验读取 */
    private String deliveryStatus;
    private Long companyId;
    private Integer periodYear;
    private Integer periodMonth;
    private Long tenantId;
    /**  电子归档：关联凭证号（exp_bill.voucher_no），归档目录记录单据+凭证闭环用 */
    private String voucherNo;
    /** 单据审批流程实例ID，档案反查"由哪次审批产生"用 */
    private String procInstId;
    /** B1：归档时间与归档人随状态一并回显，"已归档"才可核对到谁归的、何时归的 */
    private LocalDateTime archiveTime;
    /** 归档人；定时任务代写时取不到登录人 ⇒ 保持 null，界面按"系统自动归档"明示，不造假人 */
    private Long archiveBy;
    /** 待归档列表的「金额」列此前无字段可绑，恒显 0.00 ⇒ 随单带出表头总额 */
    private BigDecimal totalAmount;
    /** 「审批完成时间」列取 exp_bill.approve_time，不要用 create_time 顶替 */
    private LocalDateTime approveTime;
}
