// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.masterdata.entity.MdPayee;
import com.fssc.masterdata.mapper.MdPayeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 主数据 Payee 服务（带 tenant_id，租户拦截器自动隔离） */
@Service
@RequiredArgsConstructor
public class PayeeService {

    private final MdPayeeMapper mapper;
    /** P4-3 审计扩面（D 类）：付款账户变更记录 */
    private final com.fssc.audit.service.AuditTrailService auditTrailService;

    public PageResult<MdPayee> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdPayee> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdPayee::getPayeeName, keyword).or().like(MdPayee::getBankAccount, keyword));
        }
        qw.orderByDesc(MdPayee::getId);
        Page<MdPayee> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(MdPayee e) {
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        mapper.insert(e);
        return e.getId();
    }

    /**
     * P4-3 审计扩面（D 类·付款账户变更）：改前采集旧值，**仅当银行账户/开户行实际变化时**记录
     * （规则：before/after 只含本次实际变化字段；取不到旧实体则不写、不伪造）。
     */
    public void update(MdPayee e) {
        MdPayee old = e.getId() == null ? null : mapper.selectById(e.getId());
        mapper.updateById(e);
        if (old == null) {
            return;
        }
        java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
        if (e.getBankAccount() != null && !java.util.Objects.equals(old.getBankAccount(), e.getBankAccount())) {
            before.put("bankAccount", old.getBankAccount());
            after.put("bankAccount", e.getBankAccount());
        }
        if (e.getBankName() != null && !java.util.Objects.equals(old.getBankName(), e.getBankName())) {
            before.put("bankName", old.getBankName());
            after.put("bankName", e.getBankName());
        }
        if (before.isEmpty()) {
            return; // 本次未改银行账户字段，不写无意义审计
        }
        auditTrailService.recordUpdate("PAYEE", e.getId(), old.getPayeeName(), "md_payee",
                String.valueOf(e.getId()), before, after, "付款方银行账户变更");
    }

    public void enable(Long id, boolean enable) {
        MdPayee u = new MdPayee();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
