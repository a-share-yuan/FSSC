// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.image.vo;

import com.fssc.image.entity.OcrRecord;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OCR 识别记录 VO（带批次号和文件名）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OcrRecordVO extends OcrRecord {

    /** 批次号（来自 img_batch.batch_no） */
    private String batchNo;

    /** 文件名（来自 sys_file.file_name） */
    private String fileName;
}
