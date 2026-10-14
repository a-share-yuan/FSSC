// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import org.springframework.transaction.annotation.Transactional;
import com.fssc.common.core.PageResult;
import com.fssc.common.util.AssertUtil;
import com.fssc.common.util.StrUtil;
import com.fssc.customer.entity.CrmCustomer;
import com.fssc.customer.mapper.CrmCustomerMapper;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.service.NumberRuleService;
import com.fssc.workflow.service.WfEngineFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 客户/往来单位服务（带 tenant_id，租户拦截器自动隔离）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CrmCustomerMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final NumberRuleService numberRuleService;

    public PageResult<CrmCustomer> page(long pn, long ps, String kw) {
        LambdaQueryWrapper<CrmCustomer> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(kw)) {
            qw.and(w -> w.like(CrmCustomer::getCustomerCode, kw)
                    .or().like(CrmCustomer::getCustomerName, kw)
                    .or().like(CrmCustomer::getContactName, kw));
        }
        qw.orderByDesc(CrmCustomer::getId);
        Page<CrmCustomer> p = mapper.selectPage(Page.of(pn, ps), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    public Long create(CrmCustomer e) {
        AssertUtil.notBlank(e.getCustomerName(), ErrorCode.PARAM_INVALID, "客户名称必填");
        AssertUtil.notBlank(e.getCustomerType(), ErrorCode.PARAM_INVALID, "往来类型必填");
        // 客户编码＝系统取号（Q1 口径：人工只读），杜绝空编码/重复编码建档
        if (StrUtil.isBlank(e.getCustomerCode())) {
            e.setCustomerCode(numberRuleService.next("CRM_CUSTOMER"));
        } else {
            assertCodeUnique(e.getCustomerCode(), null);
        }
        e.setId(null);
        if (e.getStatus() == null || "NORMAL".equals(e.getStatus())) e.setStatus("PENDING");
        e.setTenantId(TenantContext.getTenantId());
        mapper.insert(e);
        // V2.2.38 B-14：审批流退役，建档即 PENDING 状态位，核准走 approve() 翻转
        return e.getId();
    }

    /** 当前登录用户（未登录返回 null，审批按候选组角色派单） */
    private Long userId() {
        return UserContext.get() == null ? null : UserContext.get().getUserId();
    }

    public void update(CrmCustomer e) {
        AssertUtil.notBlank(e.getCustomerName(), ErrorCode.PARAM_INVALID, "客户名称必填");
        if (StrUtil.isNotBlank(e.getCustomerCode())) {
            assertCodeUnique(e.getCustomerCode(), e.getId());
        }
        mapper.updateById(e);
    }

    /** 编码唯一性：租户拦截器已隔离 tenant_id，故按当前租户查重即可 */
    private void assertCodeUnique(String code, Long selfId) {
        Long n = mapper.selectCount(new LambdaQueryWrapper<CrmCustomer>()
                .eq(CrmCustomer::getCustomerCode, code)
                .ne(selfId != null, CrmCustomer::getId, selfId));
        AssertUtil.isTrue(n == null || n == 0, ErrorCode.DATA_ALREADY_EXISTS, "客户编码已存在: " + code);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    public void enable(Long id, boolean enable) {
        CrmCustomer u = new CrmCustomer();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
    }
    /** V2.2.38 B-14 状态位核准：PENDING→ACTIVE（幂等） */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        CrmCustomer cust = mapper.selectById(id);
        if (cust == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "客户不存在: " + id);
        }
        if ("ACTIVE".equals(cust.getStatus())) return;
        CrmCustomer upd = new CrmCustomer();
        upd.setId(id);
        upd.setStatus("ACTIVE");
        mapper.updateById(upd);
    }

}
