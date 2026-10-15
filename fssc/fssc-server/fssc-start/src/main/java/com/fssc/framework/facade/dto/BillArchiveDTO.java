package com.fssc.framework.facade.dto;

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
    /** R5-D-244：实物流交付状态（NULL/UNSIGNED/SIGNED/NOT_REQUIRED），归档前置闸读取 */
    private String deliveryStatus;
    private Long companyId;
    private Integer periodYear;
    private Integer periodMonth;
    private Long tenantId;
    /**  电子归档：关联凭证号（exp_bill.voucher_no），归档目录记录单据+凭证闭环用 */
    private String voucherNo;
    /** D-TRC-05：单据审批流程实例ID，档案反查"由哪次审批产生"用 */
    private String procInstId;
    /** D-DEC-276b（B1）：归档时间与归档人随状态一并回显，"已归档"才可核对到谁归的、何时归的 */
    private java.time.LocalDateTime archiveTime;
    /** 归档人；定时任务代写时取不到登录人 ⇒ 保持 null，界面按"系统自动归档"明示，不造假人 */
    private Long archiveBy;
}
