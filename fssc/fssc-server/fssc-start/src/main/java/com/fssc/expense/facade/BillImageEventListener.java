// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.expense.facade;

import com.fssc.expense.entity.ExpBill;
import com.fssc.expense.mapper.ExpBillMapper;
import com.fssc.framework.datasource.FsscDataSource;
import com.fssc.framework.facade.event.BillImageEvent;
import com.fssc.image.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 影像状态变更事件监听器（断点1）。
 *
 * 监听 BillImageEvent，在核心域内回写 exp_bill.image_status，
 * 替代 ImageService 直接注入 ExpBillMapper。
 *
 * 分库改造：@EventListener 同步监听改为 @TransactionalEventListener(AFTER_COMMIT)。
 * 发布方 ImageService 的写入是 fssc_image 库上的 REQUIRES_NEW 事务，AFTER_COMMIT 回调触发时
 * 那条事务的提交清理还没做完、连接仍绑在影像库上——此时直接 update 会落到 fssc_image.exp_bill
 * （表不存在）。因此回写必须自带独立事务：REQUIRES_NEW 把外层连接挂起，再按本类声明的 master
 * 路由另开主库连接。fallbackExecution=true 保证无事务发布时事件不丢失。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FsscDataSource("master")
public class BillImageEventListener {

    private final ExpBillMapper expBillMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBillImageEvent(BillImageEvent event) {
        // 缺陷修复（H-0）：原仅处理 EXP_CLAIM，报销域其他单据类型
        // （CLAIM/TRAVEL/PREPAY/SETTLE/PAY 等，均落 exp_bill 表）影像状态不回写。
        // 统一按 exp_bill 单据类型集合判定（与 ImageService.isExpBillBizType 同一规则）。
        if (event.getBillId() == null || !ImageService.isExpBillBizType(event.getBizType())) {
            return;
        }
        ExpBill update = new ExpBill();
        update.setId(event.getBillId());
        update.setImageStatus(event.getImageStatus());
        expBillMapper.updateById(update);
        log.info("影像状态回写: billId={}, status={}", event.getBillId(), event.getImageStatus());
    }
}
