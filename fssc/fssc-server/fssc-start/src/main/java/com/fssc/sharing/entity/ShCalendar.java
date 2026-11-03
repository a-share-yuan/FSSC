// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.sharing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 共享工作日历（.3：工作日历，休息日/工作日/在岗时间定义）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("op_calendar")
public class ShCalendar extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    /** 日期 */
    private LocalDate calDate;

    /** 日期类型 WORKDAY工作日/REST休息日/HOLIDAY节假日 */
    private String dayType;

    /** 年 */
    private Integer year;

    /** 月 */
    private Integer month;

    /** 在岗开始时间 HH:mm */
    private String workStart;

    /** 在岗结束时间 HH:mm */
    private String workEnd;

    /** 备注 */
    private String remark;

}
