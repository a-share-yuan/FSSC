// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.audit.service.AuditTrailService;
import com.fssc.receivable.entity.RcBadDebt;
import com.fssc.receivable.mapper.RcBadDebtMapper;
import com.fssc.sharing.service.VoucherService;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 坏账审批收尾（事件编排总线）。
 * <p>
 * BAD_DEBT_PROV → rc_bad_debt.status: PROVISIONED（已计提）+ 凭证：借 信用减值损失(6113)/贷 坏账准备(1231)
 * BAD_DEBT_CONFIRM → rc_bad_debt.status: CONFIRMED（已确认核销）+ 凭证：借 坏账准备(1231)/贷 应收账款(1122)
 */
@Slf4j
@Component("badDebtBizAction")
@RequiredArgsConstructor
public class BadDebtBizAction implements WfBizAction {

    private final RcBadDebtMapper rcBadDebtMapper;
    private final VoucherService voucherService;
    /** 审计扩面：审批最终状态状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("BAD_DEBT_PROV", "BAD_DEBT_CONFIRM");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long badDebtId = event.getBizId();
        if (badDebtId == null) return;
        RcBadDebt badDebt = rcBadDebtMapper.selectById(badDebtId);
        if (badDebt == null) {
            log.warn("坏账收尾: 不存在的坏账记录 {}", badDebtId);
            return;
        }
        try {
            if (!Boolean.TRUE.equals(event.getApproved())) {
                log.info("坏账 {} 审批驳回，状态不变", badDebtId);
                return;
            }
            String targetStatus = "BAD_DEBT_PROV".equals(event.getBizType()) ? "PROVISIONED" : "CONFIRMED";
            // 审计扩面：改状态前采集旧值（rc_bad_debt 无业务单号，bizNo 传 null）
            String oldStatus = badDebt.getStatus();
            RcBadDebt upd = new RcBadDebt();
            upd.setId(badDebtId);
            upd.setStatus(targetStatus);
            rcBadDebtMapper.updateById(upd);
            Map<String, Object> auditBefore = new LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            Map<String, Object> auditAfter = new LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            auditTrailService.recordUpdate(event.getBizType(), badDebtId, null, "rc_bad_debt",
                    String.valueOf(badDebtId), auditBefore, auditAfter,
                    "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("坏账 {} 审批通过: → {}", badDebtId, targetStatus);
            // 流程最终状态生成凭证：计提/核销均为会计事件，审批通过即生成凭证（失败不阻断状态迁移）
            try {
                Long vid = voucherService.generateBadDebtVoucher(badDebt, event.getBizType());
                log.info("坏账 {} 自动生成{}凭证: {}", badDebt.getPeriod(),
                        "BAD_DEBT_PROV".equals(event.getBizType()) ? "计提" : "核销", vid);
            } catch (Exception ex) {
                log.error("坏账 {} 自动生成凭证失败（已置 {}，可后续补生成）: {}",
                        badDebtId, targetStatus, ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("坏账 {} 审批收尾失败: {}", badDebtId, ex.getMessage(), ex);
        }
    }
}
