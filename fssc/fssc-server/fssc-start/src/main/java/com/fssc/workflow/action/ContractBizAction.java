// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.audit.service.AuditTrailService;
import com.fssc.contract.entity.ContContract;
import com.fssc.contract.mapper.ContContractMapper;
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
 * 合同审批收尾（事件编排总线 + 凭证引擎接线）。
 * <p>
 * CONTRACT_APPROVE 审批通过 → cont_contract.status: DRAFT → APPROVED + 生成合同凭证
 */
@Slf4j
@Component("contractBizAction")
@RequiredArgsConstructor
public class ContractBizAction implements WfBizAction {

    private final ContContractMapper contContractMapper;
    private final VoucherService voucherService;
    /** 审计扩面：审批最终状态状态跃迁记录 */
    private final AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        return Set.of("CONTRACT_APPROVE");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long contractId = event.getBizId();
        if (contractId == null) return;
        ContContract contract = contContractMapper.selectById(contractId);
        if (contract == null) {
            log.warn("合同收尾: 不存在的合同 {}", contractId);
            return;
        }
        try {
            // 流程收尾环节：这张单若已由「手工直批环节」给出结论（EFFECTIVE 已生效／CLOSED 已关闭），
            // 收尾一律不回写、不出证。两条审批链并存是现状（不归一、各补互斥校验）：直批位有 fail-closed
            // 在途校验（ContractService.assertNotInFlow），但反向只能靠本判据——否则流程的撤回/驳回事件会把
            // "已生效并冻结了预算、已出过凭证"的合同改写回 APPROVED/REJECTED，并再出一张同号合同凭证。
            String concluded = contract.getStatus();
            if ("EFFECTIVE".equals(concluded) || "CLOSED".equals(concluded)) {
                log.info("合同 {} 已由直批腿给出结论（当前状态 {}），流程收尾（approved={}, withdrawn={}）不回写状态、不重复出证",
                        contract.getContractNo(), concluded, event.getApproved(), event.withdrawn());
                return;
            }
            // 审计扩面：改状态前采集旧值
            String oldStatus = contract.getStatus();
            ContContract upd = new ContContract();
            upd.setId(contractId);
            upd.setStatus(Boolean.TRUE.equals(event.getApproved()) ? "APPROVED" : "REJECTED");
            contContractMapper.updateById(upd);
            Map<String, Object> auditBefore = new LinkedHashMap<>();
            auditBefore.put("status", oldStatus);
            Map<String, Object> auditAfter = new LinkedHashMap<>();
            auditAfter.put("status", upd.getStatus());
            auditTrailService.recordUpdate(event.getBizType(), contractId, contract.getContractNo(), "cont_contract",
                    String.valueOf(contractId), auditBefore, auditAfter,
                    (Boolean.TRUE.equals(event.getApproved()) ? "审批通过" : "审批驳回")
                            + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
            // 生成合同凭证（generateByContract 原先没有调用方）
            if (Boolean.TRUE.equals(event.getApproved())) {
                try {
                    // ②：带回流程实例号 ⇒ 凭证 proc_inst_id 回溯列不再留 NULL
                    voucherService.generateByContract(contractId, event.getProcInstId());
                } catch (Exception vex) {
                    log.error("合同 {} 凭证生成失败（不影响状态更新）: {}", contractId, vex.getMessage(), vex);
                }
            }
            log.info("合同 {} 审批{}: DRAFT → {}", contract.getContractNo(),
                    Boolean.TRUE.equals(event.getApproved()) ? "通过" : "驳回",
                    Boolean.TRUE.equals(event.getApproved()) ? "APPROVED" : "REJECTED");
        } catch (Exception ex) {
            log.error("合同 {} 审批收尾失败: {}", contractId, ex.getMessage(), ex);
        }
    }
}
