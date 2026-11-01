// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.service;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import com.fssc.framework.tenant.TenantContext;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

/**
 * 账期锁定服务（AR 蓝图规则6.5：月结关闭账期后不允许再录入当月业务）。
 * 锁标记落 sys_config（key = ar.period.lock.{companyId}.{yyyy-MM}），
 * 放在 system 域使 receivable/arflow 等各业务域可无反向依赖地接入判定。
 */
@Service
@RequiredArgsConstructor
public class PeriodLockService {

    public static final String KEY_PREFIX = "ar.period.lock.";

    private final ConfigService configService;

    private static String key(Long companyId, String period) {
        return KEY_PREFIX + (companyId == null ? 1L : companyId) + "." + period;
    }

    /** 账期是否已锁定：月结关账置 1。 */
    public boolean isLocked(Long companyId, String period) {
        if (period == null || period.isBlank()) {
            return false;
        }
        return "1".equals(configService.getValue(key(companyId, period)));
    }

    /** 锁定账期（AR月结/成本月结关闭账期时调用）。 */
    public void lock(Long companyId, String period) {
        if (period == null || period.isBlank()) {
            return;
        }
        configService.setValue(key(companyId, period), "1",
                "账期锁定-公司" + (companyId == null ? 1L : companyId) + " " + period);
    }

    /** 解锁账期（月结单作废/重开场景预留）。 */
    public void unlock(Long companyId, String period) {
        if (period == null || period.isBlank()) {
            return;
        }
        configService.setValue(key(companyId, period), "0",
                "账期锁定-公司" + (companyId == null ? 1L : companyId) + " " + period);
    }

    /** 账期锁定校验（蓝图规则6.5）：已锁定则抛业务异常。period 形如 yyyy-MM。 */
    public void assertNotLocked(Long companyId, String period, String action) {
        if (isLocked(companyId, period)) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "账期 " + period + " 已被月结关闭，不允许再" + action + "（蓝图规则6.5）");
        }
    }

    /** 按单据日期判定所属账期是否锁定（yyyy-MM）。 */
    public boolean isLockedByDate(Long companyId, LocalDate billDate) {
        if (billDate == null) {
            return false;
        }
        return isLocked(companyId, YearMonth.from(billDate).toString());
    }
}
