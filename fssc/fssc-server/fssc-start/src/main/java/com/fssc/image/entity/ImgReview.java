package com.fssc.image.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 影像调阅评价（image_review，需求 6.5：整单重采集/整单作废/补采集）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("image_review")
public class ImgReview extends TenantAuditBase {

    private static final long serialVersionUID = 1L;

    private Long imgBatchId;
    private String batchNo;
    private Long bizId;
    private String bizNo;
    private String bizType;
    /** PENDING待评价/OK通过/RE_COLLECT整单重采集/RESCAN补采集/REJECT整单作废 */
    private String reviewStatus;
    private Long reviewUserId;
    private LocalDateTime reviewTime;
    private String remark;
}
