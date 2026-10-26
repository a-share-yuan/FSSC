// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.gateway;

import com.fssc.system.entity.IntegrationCallLog;
import com.fssc.system.mapper.IntegrationCallLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集成外呼记录写入器。
 *
 * <p>P0-3「失败记录」的关键点：外呼多发生在业务事务内（如金税开票、ERP 记账），失败会抛异常回滚整个事务。
 * 若日志与业务同事务，失败记录会一起被回滚 →「失败不可查、重试无依据」。
 * 因此单独成类并以 {@code REQUIRES_NEW} 独立提交，与 {@code CollabSyncService.log} 同一范式。</p>
 */
@Service
@RequiredArgsConstructor
public class IntegrationCallLogWriter {

    private final IntegrationCallLogMapper callLogMapper;

    /** 独立事务写入一条外呼记录：不随主事务回滚，失败记录。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void write(IntegrationCallLog logEntry) {
        callLogMapper.insert(logEntry);
    }
}
