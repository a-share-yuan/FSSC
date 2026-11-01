// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 影像页 DTO：替代 ImgFile 实体在跨域场景中的传递，
 * 字段与 ImgFile 保持一致，前端序列化字段名不变。
 */
@Data
public class ImageFileDTO {

    private Long id;
    private Long batchId;
    private Long fileId;
    private String pageType;
    private Integer pageNo;
    private String ocrStatus;
    private String ocrText;
    private String bizType;
    private Long bizId;
    private String verifyStatus;
    private String remark;
    private LocalDateTime createTime;
}
