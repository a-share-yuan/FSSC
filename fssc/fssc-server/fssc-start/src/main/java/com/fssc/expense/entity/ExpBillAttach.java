// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 报销单附件（exp_bill_attach，带 tenant_id）。file_id 指向影像/文件服务。 */
@Data
@TableName("exp_bill_attach")
public class ExpBillAttach implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    private Long billId;
    private Long lineId;
    /** 文件ID（影像/文件存储） */
    private Long fileId;
    /** INVOICE发票/IMAGE影像/OTHER */
    private String attachType;
    private Integer sort;
    private Long uploadBy;
    private LocalDateTime createTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

    /** 原始文件名（sys_file.file_name）：附件表只存 file_id，回显时按 id 反查，前端据此出缩略图与扩展名 */
    @TableField(exist = false)
    private String fileName;

    /** 显示面：上传人姓名。upload_by 存 sys_user.id，直接给前端只显数字——回显时批量反查 sys_user 填充 */
    @TableField(exist = false)
    private String uploadByName;
}
