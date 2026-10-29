// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.contract.service;

import com.fssc.contract.entity.ContContract;
import com.fssc.contract.mapper.ContContractMapper;
import com.fssc.document.service.ArchiveCarrierResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 合同作为非单据载体进档案登记簿（{@code archive_type='CONTRACT'}，
 * {@code ref_id=cont_contract.id}）——合同表零加列，归档结论只落在 op_archive。
 */
@Component
@RequiredArgsConstructor
public class ContractArchiveCarrierResolver implements ArchiveCarrierResolver {

    private final ContContractMapper contractMapper;

    @Override
    public Set<String> archiveTypes() {
        return Set.of("CONTRACT");
    }

    @Override
    public Carrier resolve(Long refId) {
        if (refId == null || refId <= 0L) {
            return null;
        }
        ContContract c = contractMapper.selectById(refId);
        return c == null ? null : new Carrier(c.getContractNo(), c.getContractName(), c.getCompanyId());
    }
}
