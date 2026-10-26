// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.facade;

import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.facade.event.BillImageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 影像状态变更事件监听器（断点1）。
 *
 * 监听 BillImageEvent，在核心域内回写 exp_bill.image_status，
 * 替代 ImageService 直接注入 ExpBillMapper。
 *
 * 分库改造：@EventListener 同步监听改为 @TransactionalEventListener(AFTER_COMMIT)。
 * 发布方 ImageService 已切到 fssc_image 库的独立事务（REQUIRES_NEW），
 * 同步监听会在 image 事务连接上回写 exp_bill（master 表）导致跨库写错库；
 * 提交后触发时无事务上下文，按默认 master 路由新建连接回写，语义与
 * 断点7 BillTerminalEventListener 的场景1 修复一致。fallbackExecution=true
 * 保证无事务发布时事件不丢失。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillImageEventListener {

    private final ExpBillMapper expBillMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBillImageEvent(BillImageEvent event) {
        // 缺陷修复（H-0）：原仅处理 EXP_CLAIM，报销域其他单据类型
        // （CLAIM/TRAVEL/PREPAY/SETTLE/PAY 等，均落 exp_bill 表）影像状态不回写。
        // 统一按 exp_bill 单据类型集合判定（与 ImageService.isExpBillBizType 同一规则）。
        if (event.getBillId() == null || !com.fssc.image.service.ImageService.isExpBillBizType(event.getBizType())) {
            return;
        }
        ExpBill update = new ExpBill();
        update.setId(event.getBillId());
        update.setImageStatus(event.getImageStatus());
        expBillMapper.updateById(update);
        log.info("影像状态回写: billId={}, status={}", event.getBillId(), event.getImageStatus());
    }
}
