// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 档案信息 DTO：替代 OpArchive 实体在跨域场景中的传递，
 * 字段与 OpArchive 保持一致，前端序列化字段名不变。
 */
@Data
public class ArchiveInfoDTO {

    private Long id;
    private String archiveNo;
    private String archiveCode;
    private String archiveType;
    private Integer archiveYear;
    private String archivePeriod;
    private Long refId;
    private Long billId;
    /** 来源单据的审批流程实例ID（非费用域联查的唯一档案关联点） */
    private String procInstId;
    /**
     * 本卷<b>归档审批流（FM_ARCHIVE）自身</b>的实例号（读侧补充，非表列）。
     * 与上一列语义不同，跨域联查两侧都拿得到，不必再靠 {@code op_archive.proc_inst_id} 反推。
     */
    private String archiveProcInstId;
    /** 归档审批流自身状态 RUNNING/APPROVED/REJECTED/CANCELLED（未起流则空） */
    private String archiveFlowStatus;
    private Long voucherId;
    private String relatedIds;
    private String refNo;
    private String title;
    private Long companyId;
    private Integer periodYear;
    private Integer periodMonth;
    private Long fileId;
    private Long filingUserId;
    private LocalDateTime filingTime;
    private Integer retentionYears;
    private LocalDate destroyDate;
    private String integrityHash;
    private String integrityStatus;
    private String archiveStatus;
    private LocalDateTime archivedAt;
    private LocalDateTime destroyedAt;
    private String status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
