package com.fssc.image.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 影像异常（image_exception，：6 类异常人工处理）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("image_exception")
public class ImgException extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    private Long imgBatchId;
    private String batchNo;
    private String bizNo;
    /** 异常类型：DUPLICATE_BILL单据重复/DUPLICATE_INVOICE不同单据发票重复/TAX_NO_MISMATCH法人税号不一致/OCR_INCOMPLETE识别不完整/VERIFY_FAIL验真失败/INVOICE_DUP_IN_BILL单据内发票重复 */
    private String exceptionType;
    private String exceptionDesc;
    /** OPEN待处理/FIXED已处理 */
    private String status;
    private Long handleUserId;
    private LocalDateTime handleTime;
    private String remark;
}
