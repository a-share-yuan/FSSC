// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade.dto;

import lombok.Data;

/**
 * 档案登记 DTO：外部域（如 bank 域）向档案中心登记档案记录时使用。
 * tenantId / filingTime / createTime 由档案域实现侧填充。
 */
@Data
public class ArchiveRegisterDTO {

    private String archiveNo;
    private String archiveType;
    private Long refId;
    private Long billId;
    private String refNo;
    private String title;
    private Long fileId;
    private Long filingUserId;
    /** R5-D-210：来源公司（回单立卷等跨域登记必须带公司，档案读面按公司数据域收口） */
    private Long companyId;
    private String archiveStatus;
    private String status;
    private String integrityStatus;
    private String remark;
}
