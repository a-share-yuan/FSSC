// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.sharing.entity.SupplierRecon;
import com.fssc.sharing.mapper.SupplierReconMapper;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 供应商对账确认（RECON_ACTIVE）收尾腿（P0-1 事件编排总线策略实现）。
 *
 * <p>D-DEC-319（批次0 P-4 / A2 回滚腿）：此前 RECON_ACTIVE 是"无收尾腿"七条之一——
 * {@code SupplierReconService.supplierConfirm} 先把对账单置 CONFIRMED 才发起（同事务），审批驳回后
 * 无人把状态洗回＝脏账。本动作按发起时写入的流程变量快照（prevStatus / prevSupplierConfirmStatus）
 * 在驳回侧恢复确认前状态并清空确认时间戳；通过侧 CONFIRMED 已在发起时落库（幂等维持），无需重复动作。
 *
 * <p>批次3（T-PT-4）给通过侧补"差异核对+FIN_GL 过账"完整腿时，在本类扩展，不得另起炉灶。
 */
@Slf4j
@Component("reconBizAction")
@RequiredArgsConstructor
public class ReconBizAction implements WfBizAction {

    private final SupplierReconMapper reconMapper;
    private final HistoryService historyService;
    /** P4-3 审计扩面：审批终态状态跃迁留痕 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("RECON_ACTIVE");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long reconId = event.getBizId();
        if (reconId == null) return;
        SupplierRecon recon = reconMapper.selectById(reconId);
        if (recon == null) {
            log.warn("供应商对账收尾: 不存在的对账单 {}", reconId);
            return;
        }
        try {
            if (Boolean.TRUE.equals(event.getApproved())) {
                // CONFIRMED 在 supplierConfirm 落库（同事务）；这里只补一条通过留痕，不重复改状态
                java.util.Map<String, Object> same = new java.util.LinkedHashMap<>();
                same.put("status", recon.getStatus());
                auditTrailService.recordUpdate(event.getBizType(), reconId, recon.getReconNo(), "supplier_recon",
                        String.valueOf(reconId), same, same,
                        "对账确认审批通过" + (event.getComment() == null || event.getComment().isBlank()
                                ? "" : "：" + event.getComment()));
                log.info("供应商对账 {} 审批通过，状态 {}（维持发起时落库的 CONFIRMED）", reconId, recon.getStatus());
                return;
            }
            // 驳回：按快照洗回确认前状态
            String prevStatus = readStringVar(event.getProcInstId(), "prevStatus");
            String prevConfirmStatus = readStringVar(event.getProcInstId(), "prevSupplierConfirmStatus");
            if (prevStatus == null || prevStatus.isBlank()) {
                // 无快照＝历史在途单（D-DEC-319 前发起），保守洗回 DIFF 人工档并留痕
                log.warn("对账单 {} 驳回但无确认前快照（历史在途单），按 DIFF 人工档处理", reconId);
                prevStatus = "DIFF";
            }
            java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
            before.put("status", recon.getStatus());
            before.put("supplierConfirmStatus", recon.getSupplierConfirmStatus());
            // 清空确认时间戳必须走 UpdateWrapper：updateById 默认跳过 null 字段，set null 不生效
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SupplierRecon> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            uw.eq(SupplierRecon::getId, reconId)
                    .set(SupplierRecon::getStatus, prevStatus)
                    .set(SupplierRecon::getSupplierConfirmStatus,
                            prevConfirmStatus == null || prevConfirmStatus.isBlank() ? null : prevConfirmStatus)
                    .set(SupplierRecon::getSupplierConfirmTime, null)
                    .set(SupplierRecon::getLetterReceivedTime, null)
                    .set(SupplierRecon::getSupplierConfirmRemark,
                            "审批驳回自动回滚" + (event.getComment() == null || event.getComment().isBlank()
                                    ? "" : "：" + event.getComment()))
                    .set(SupplierRecon::getUpdateTime, java.time.LocalDateTime.now());
            reconMapper.update(null, uw);
            java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
            after.put("status", prevStatus);
            after.put("supplierConfirmStatus", prevConfirmStatus);
            auditTrailService.recordUpdate(event.getBizType(), reconId, recon.getReconNo(), "supplier_recon",
                    String.valueOf(reconId), before, after,
                    "审批驳回，回滚至确认前状态" + (event.getComment() == null || event.getComment().isBlank()
                            ? "" : "：" + event.getComment()));
            log.info("供应商对账 {} 审批驳回，状态 {} → {}（快照回滚）", reconId, recon.getStatus(), prevStatus);
        } catch (Exception ex) {
            log.error("供应商对账 {} 审批收尾失败: {}", reconId, ex.getMessage(), ex);
        }
    }

    private String readStringVar(String procInstId, String name) {
        if (procInstId == null || procInstId.isBlank()) return null;
        try {
            HistoricVariableInstance v = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(procInstId)
                    .variableName(name)
                    .singleResult();
            return v == null || v.getValue() == null ? null : String.valueOf(v.getValue());
        } catch (Exception ex) {
            log.warn("读取流程变量 {} 失败 procInstId={}: {}", name, procInstId, ex.getMessage());
            return null;
        }
    }
}
