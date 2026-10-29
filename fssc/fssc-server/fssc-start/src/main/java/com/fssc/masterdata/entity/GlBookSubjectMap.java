// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fssc.common.entity.TenantAuditBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 账簿×科目映射——多准则出多套核算数据的桥梁。
 *
 * <p>不同会计准则科目体系不同（如 ASBE「应收账款 1122」vs SME 简表），
 * 没有映射表就只能靠代码 if-else 判断 ⇒ 又回到"改代码才能改准则"的老路。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gl_book_subject_map")
public class GlBookSubjectMap extends TenantAuditBase {

    private Long bookId;

    private Long standardId;

    /** 本准则下的科目 md_subject.id */
    private Long localSubjectId;

    private String localSubjectCode;

    private String localSubjectName;

    /** 对应通用科目 md_subject.id（跨准则归一） */
    private Long commonSubjectId;

    /** DIRECT 直接对应 / RECLASS 重分类 / NOT_APPLICABLE 本准则不适用 */
    private String mappingType;

    private String status;

    private String remark;
}
