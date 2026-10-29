// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导入导出模板头。
 *
 * <p>把「新增一种导入模板 = 复制一支手写 ExcelService 再改列头」变成「插两行数据」。
 * 已迁移 6 支手写 Service 的列头（见）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("md_io_template")
public class MdIoTemplate extends TenantAuditBase {

    /** 模板编码（租户内唯一，含版本） */
    private String templateCode;

    private String templateName;

    /** 业务类型：AR_STATEMENT/AR_RECEIPT/AR_OFFSET/AP_PLAN/BUDGET_ITEM/RC_BAD_DEBT */
    private String bizType;

    /** IMPORT 导入 / EXPORT 导出 / BOTH 双向 */
    private String direction;

    /** 写入数据库/查询目标表（EXPORT 必填） */
    private String targetTable;

    /** Excel 工作表名 */
    private String sheetName;

    /** 表头所在行（1-based，允许前导说明行） */
    private Integer headerRow;

    /** 单次最大数据行数（防打爆内存） */
    private Integer maxRows;

    private Integer version;

    /** 1-系统预置（禁删） 0-租户自建 */
    private Integer isSystem;

    private String status;

    private String remark;
}
