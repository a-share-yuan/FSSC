// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.workflow.action;

import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.mapper.MdSupplierMapper;
import com.fssc.workflow.event.WfBizAction;
import com.fssc.workflow.event.WfProcessEndedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 主数据变更审批收尾（P0-1 事件编排总线）。
 * <p>
 * SUPPLIER_ADD → md_supplier.status: PENDING → ACTIVE
 * CUSTOMER_MAINT → crm_customer.status: PENDING → ACTIVE
 */
@Slf4j
@Component("masterDataBizAction")
@RequiredArgsConstructor
public class MasterDataBizAction implements WfBizAction {

    private final MdSupplierMapper mdSupplierMapper;
    private final CrmCustomerMapper crmCustomerMapper;
    /** P4-3 审计扩面：审批最终状态状态跃迁记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    @Override
    public Set<String> bizTypes() {
        // D-DEC-324（批次5 B 案 T-MD-1）：移域合一——银行账户/印鉴(TR_SEAL_CHANGE)/GL 主数据三档并入主数据核准载体，
        // 事件 bizType 不变；三档无独立业务状态机（K133：银行账户靠分配＋挂科目），通过侧仅记录确认。
        return Set.of("SUPPLIER_ADD", "CUSTOMER_MAINT", "TR_BANK_MASTER", "TR_SEAL_CHANGE", "GL_SHARED_MASTER");
    }

    @Override
    public void execute(WfProcessEndedEvent event) {
        Long bizId = event.getBizId();
        if (bizId == null) return;
        String bizType = event.getBizType();
        try {
            if (!Boolean.TRUE.equals(event.getApproved())) {
                log.info("主数据变更 {} 审批驳回，状态不变", bizId);
                return;
            }
            switch (bizType) {
                case "SUPPLIER_ADD" -> {
                    MdSupplier supplier = mdSupplierMapper.selectById(bizId);
                    if (supplier != null) {
                        // P4-3 审计扩面：改状态前采集旧值
                        String oldStatus = supplier.getStatus();
                        MdSupplier upd = new MdSupplier();
                        upd.setId(bizId);
                        upd.setStatus("ACTIVE");
                        mdSupplierMapper.updateById(upd);
                        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
                        auditBefore.put("status", oldStatus);
                        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
                        auditAfter.put("status", upd.getStatus());
                        auditTrailService.recordUpdate(event.getBizType(), bizId, supplier.getSupplierCode(), "md_supplier",
                                String.valueOf(bizId), auditBefore, auditAfter,
                                "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                        log.info("供应商 {} 审批通过: PENDING → ACTIVE", supplier.getSupplierName());
                    }
                }
                case "TR_BANK_MASTER", "TR_SEAL_CHANGE", "GL_SHARED_MASTER" -> {
                    // 无独立状态机的主数据档（K130/K133 状态位/分配规则）：通过侧仅审计记录
                    java.util.Map<String, Object> same = new java.util.LinkedHashMap<>();
                    same.put("bizType", bizType);
                    auditTrailService.recordUpdate(bizType, bizId, String.valueOf(bizId), "master_data",
                            String.valueOf(bizId), same, same,
                            "主数据核准通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                    log.info("主数据档 {} {} 审批通过（状态位/分配口径，无状态机）", bizType, bizId);
                }
                case "CUSTOMER_MAINT" -> {
                    CrmCustomer customer = crmCustomerMapper.selectById(bizId);
                    if (customer != null) {
                        // P4-3 审计扩面：改状态前采集旧值
                        String oldStatus = customer.getStatus();
                        CrmCustomer upd = new CrmCustomer();
                        upd.setId(bizId);
                        upd.setStatus("ACTIVE");
                        crmCustomerMapper.updateById(upd);
                        java.util.Map<String, Object> auditBefore = new java.util.LinkedHashMap<>();
                        auditBefore.put("status", oldStatus);
                        java.util.Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
                        auditAfter.put("status", upd.getStatus());
                        auditTrailService.recordUpdate(event.getBizType(), bizId, customer.getCustomerCode(), "crm_customer",
                                String.valueOf(bizId), auditBefore, auditAfter,
                                "审批通过" + (event.getComment() == null || event.getComment().isBlank() ? "" : "：" + event.getComment()));
                        log.info("客户 {} 审批通过: PENDING → ACTIVE", customer.getCustomerName());
                    }
                }
                default -> log.warn("主数据收尾: 未知的 bizType {}", bizType);
            }
        } catch (Exception ex) {
            log.error("主数据变更 {} 审批收尾失败: {}", bizId, ex.getMessage(), ex);
        }
    }
}
