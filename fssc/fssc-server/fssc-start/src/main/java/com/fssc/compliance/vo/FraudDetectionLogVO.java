// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.compliance.vo;

import com.fssc.compliance.entity.FraudDetectionLog;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 反欺诈检测日志VO（补充关联字段，避免前端直接显示数字ID）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FraudDetectionLogVO extends FraudDetectionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 员工姓名（从 sys_user 关联） */
    private String employeeName;

    /** 单据编号（从 exp_bill 关联） */
    private String billNo;
}
