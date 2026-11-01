// SPDX-License-Identifier: AGPL-3.0-or-later
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
import com.fssc.masterdata.entity.MdSupplier;
import com.fssc.masterdata.mapper.MdSupplierMapper;
import com.fssc.framework.security.UserContext;
import com.fssc.framework.tenant.TenantContext;
import com.fssc.system.service.NumberRuleService;
import com.fssc.workflow.service.WfEngineFacade;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 客户/往来单位服务（带 tenant_id，租户拦截器自动隔离）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    /**
     * 关键字过滤的第四臂＝供应商侧联查。影子行的联系人已不再落 crm 列（清历史后全空），
     * 不要让「按联系人姓名/电话/地址搜客户」这条既有手感在清列那天静默失效。
     * 参数一律走 {0} 占位（不要拼接用户输入）；两侧都锁自家 tenant_id，不依赖拦截器是否会下推进子查询。
     */
    private static final String SUPPLIER_CONTACT_EXISTS =
            "exists (select 1 from md_supplier ms where ms.del_flag = 0"
                    + " and ms.tenant_id = crm_customer.tenant_id"
                    + " and ms.supplier_code = crm_customer.customer_code"
                    + " and (ms.contact_name like concat('%', {0}, '%')"
                    + " or ms.contact_phone like concat('%', {0}, '%')"
                    + " or ms.address like concat('%', {0}, '%')))";

    private final CrmCustomerMapper mapper;
    private final WfEngineFacade wfEngineFacade;
    private final NumberRuleService numberRuleService;
    private final MdSupplierMapper supplierMapper;

    public PageResult<CrmCustomer> page(long pn, long ps, String kw) {
        LambdaQueryWrapper<CrmCustomer> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(kw)) {
            qw.and(w -> w.like(CrmCustomer::getCustomerCode, kw)
                    .or().like(CrmCustomer::getCustomerName, kw)
                    .or().like(CrmCustomer::getContactName, kw)
                    .or().apply(SUPPLIER_CONTACT_EXISTS, kw));
        }
        qw.orderByDesc(CrmCustomer::getId);
        Page<CrmCustomer> p = mapper.selectPage(Page.of(pn, ps), qw);
        fillContactFromSupplier(p.getRecords());
        return PageResult.of(p.getRecords(), p.getTotal(), pn, ps);
    }

    public Long create(CrmCustomer e) {
        AssertUtil.notBlank(e.getCustomerName(), ErrorCode.PARAM_INVALID, "客户名称必填");
        AssertUtil.notBlank(e.getCustomerType(), ErrorCode.PARAM_INVALID, "往来类型必填");
        // 客户编码＝系统取号（Q1 规则：人工只读），杜绝空编码/重复编码建档
        if (StrUtil.isBlank(e.getCustomerCode())) {
            e.setCustomerCode(numberRuleService.next("CRM_CUSTOMER"));
        } else {
            assertCodeUnique(e.getCustomerCode(), null);
        }
        e.setId(null);
        // 客户"未核准/核准/禁用"状态位：建档即 PENDING，核准走 approve()
        if (e.getStatus() == null || "NORMAL".equals(e.getStatus())) e.setStatus("PENDING");
        e.setTenantId(TenantContext.getTenantId());
        mapper.insert(e);
        // 审批流已停用，建档即 PENDING 状态位，核准走 approve() 翻转
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
    /** 状态位核准：PENDING→ACTIVE（去重） */
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

    /**
     * 供应商影子行的联系人三列（contactName/contactPhone/address）按编码回查 md_supplier 补齐。
     * crm_customer 建档不再拷这三列（同编码 51 行三列逐值全同＝纯冗余；该数为现盘快照，库可被整体重建⇒以现盘为准），出参规则保持不变，界面「带出联系人」照旧。
     * 每页只发一条 IN 查询（客户下拉每页都走这里，不要逐行回查）；只填空缺，人工在客户档案上改过的值不覆盖。
     * 关键字侧另有第四臂＝SUPPLIER_CONTACT_EXISTS 联查供应商侧三列，影子行清空后按 联系人/电话/地址 仍搜得到。
     */
    private void fillContactFromSupplier(List<CrmCustomer> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (CrmCustomer c : rows) {
            boolean anyBlank = StrUtil.isBlank(c.getContactName())
                    || StrUtil.isBlank(c.getContactPhone())
                    || StrUtil.isBlank(c.getAddress());
            if (anyBlank && StrUtil.isNotBlank(c.getCustomerCode())) {
                codes.add(c.getCustomerCode().trim());
            }
        }
        if (codes.isEmpty()) {
            return;
        }
        Map<String, MdSupplier> byCode = supplierMapper.selectList(new LambdaQueryWrapper<MdSupplier>()
                        .in(MdSupplier::getSupplierCode, codes))
                .stream()
                .filter(s -> s.getSupplierCode() != null)
                .collect(Collectors.toMap(s -> s.getSupplierCode().trim(), s -> s, (a, b) -> a));
        for (CrmCustomer c : rows) {
            MdSupplier s = c.getCustomerCode() == null ? null : byCode.get(c.getCustomerCode().trim());
            if (s == null) {
                continue;
            }
            if (StrUtil.isBlank(c.getContactName())) {
                c.setContactName(s.getContactName());
            }
            if (StrUtil.isBlank(c.getContactPhone())) {
                c.setContactPhone(s.getContactPhone());
            }
            if (StrUtil.isBlank(c.getAddress())) {
                c.setAddress(s.getAddress());
            }
        }
    }
}
