package com.fssc.image.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 影像批次（img_batch，带 tenant_id）。扫描/上传一批影像页面。 */
@Data
@TableName("img_batch")
public class ImgBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private String batchNo;
    private Long scanUserId;
    /** H5/PC/SCANNER */
    private String source;
    private Integer docCount;
    private String bizType;
    private Long bizId;
    /** 业务单号冗余（影像任务） */
    private String bizNo;
    /** UPLOADED已上传 / CHECKED已质检 */
    private String status;
    /** 封单状态 SEALED已封单 / UNSEALED未封单 */
    private String sealStatus;
    /** 调阅审核 PENDING待审核 / OK已审核 / REJECTED已拒绝 */
    private String reviewStatus;
    private Long checkUserId;
    private LocalDateTime checkTime;
    private String remark;
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
