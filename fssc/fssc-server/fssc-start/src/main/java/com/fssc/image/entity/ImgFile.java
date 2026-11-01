// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

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

/** 影像页面（img_file，带 tenant_id）。批次内的每一页，含 OCR 状态与识别文本。 */
@Data
@TableName("img_file")
public class ImgFile implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long batchId;
    /** 文件ID（文件存储服务） */
    private Long fileId;
    /** NORMAL/COVER封面 */
    private String pageType;
    private Integer pageNo;
    /** NONE/PENDING/SUCCESS/FAILED */
    private String ocrStatus;
    private String ocrText;
    private String bizType;
    private Long bizId;
    /** NONE/PASS/REJECT */
    private String verifyStatus;
    private String remark;
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
