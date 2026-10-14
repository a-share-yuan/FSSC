// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.receivable.entity.ArReconRecord;
import com.fssc.receivable.mapper.ArReconRecordMapper;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 应收域流程收尾（P0-1 事件编排总线）。
 * <p>
 * 覆盖 AR_RECON（应收账款对账 OTC04）/ AR_REPORT（应收管理分析报告 OTC05）。
 * 二者为纯审批流（无资金收付、无总账过账），收尾即翻转 ar_recon_record 状态：
 * 审批通过 → CONFIRMED（对账/报告已确认），驳回 → REJECTED。
 * <p>
 * 兼容历史以 bizId=NULL 发起的流程：无业务记录可翻转时仅记录闭环日志，不报错。
 */
@Slf4j
@Component("arReconBizAction")
@RequiredArgsConstructor
public class ArReconBizAction implements WfBizAction {

    private final ArReconRecordMapper arReconRecordMapper;
    /** P4-3 审计扩面：审批终态状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("AR_RECON", "AR_REPORT");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        String bizType = event.getBizType();
        String cn = "AR_RECON".equals(bizType) ? "应收对账" : "应收分析报告";
        boolean approved = Boolean.TRUE.equals(event.getApproved());
        Long id = event.getBizId();
        if (id == null) {
            // 历史以 bizId=NULL 发起的流程：无业务记录可翻转，仅记录闭环（流程完成已在 wf_instance 留痕）
            log.info("{} 流程结束（无业务记录 bizId=NULL）：approved={}，无状态需翻转", cn, approved);
            return;
        }
        ArReconRecord rec = arReconRecordMapper.selectById(id);
        if (rec == null) {
            log.warn("{} 收尾：记录 {} 不存在（可能已删除）", cn, id);
            return;
        }
        try {
            // P4-3 审计扩面：改状态前采集旧值（ar_recon_record 无业务单号，bizNo 传 null）
            String oldStatus = rec.getStatus();
            ArReconRecord upd = new ArReconRecord();
            upd.setId(id);
            upd.setStatus(approved ? "CONFIRMED" : "REJECTED");
            arReconRecordMapper.updateById(upd);
            java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            auditTrailService.recordUpdate(event.getBizType(), id, null, "ar_recon_record",
                    String.valueOf(id), auditBefore, auditAfter,
                    (approved ? "审批通过" : "审批驳回")
                            + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            log.info("{} 记录 {}（期间={}）审批{}：RUNNING → {}",
                    cn, id, rec.getPeriod(), approved ? "通过" : "驳回", upd.getStatus());
        } catch (Exception ex) {
            log.error("{} 记录 {} 收尾失败: {}", cn, id, ex.getMessage(), ex);
        }
    }
}
