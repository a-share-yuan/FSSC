// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.facade.event;

import lombok.Getter;

/**
 * 影像状态变更事件（断点1）。
 *
 * 影像域 ImageService 在上传完成 / 删除批次时发布此事件，
 * 核心域（expense）监听后回写 exp_bill.image_status，
 * 使 ImageService 不再直接依赖 ExpBillMapper。
 */
@Getter
public class BillImageEvent {

    private final Long billId;
    private final String imageStatus;
    private final String bizType;

    public BillImageEvent(Long billId, String imageStatus, String bizType) {
        this.billId = billId;
        this.imageStatus = imageStatus;
        this.bizType = bizType;
    }
}
