package com.fssc.framework.facade.event;

import lombok.Getter;

/**
 * 单据最终状态事件（断点3 基础设施）。
 *
 * 核心域（expense）在单据状态变为 APPROVED / PAID / CLOSED 时发布此事件，
 * 档案域监听后收集为待归档候选，替代档案域反向查询 ExpBill 表。
 *
 * 当前为基础设施预埋，实际发布点在断点7（ExpenseWfEventListener）实现。
 */
@Getter
public class BillTerminalEvent {

    private final Long billId;
    private final String billNo;
    private final String title;
    private final String billType;
    private final String status;
    private final Long companyId;
    private final Integer periodYear;
    private final Integer periodMonth;
    private final Long tenantId;
    private final String imageStatus;

    public BillTerminalEvent(Long billId, String billNo, String title, String billType,
                              String status, Long companyId, Integer periodYear,
                              Integer periodMonth, Long tenantId, String imageStatus) {
        this.billId = billId;
        this.billNo = billNo;
        this.title = title;
        this.billType = billType;
        this.status = status;
        this.companyId = companyId;
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
        this.tenantId = tenantId;
        this.imageStatus = imageStatus;
    }
}
