// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.util.AssertUtil;
import com.fssc.framework.cache.TenantCache;
import com.fssc.masterdata.entity.MdExpenseStandard;
import com.fssc.masterdata.mapper.MdExpenseStandardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 主数据 ExpenseStandard 服务（带 tenant_id，租户拦截器自动隔离） */
@Service
@RequiredArgsConstructor
public class ExpenseStandardService {

    /** 缓存域：报销标准（读取侧 ExpenseBillService.findExpenseStandard 按此域缓存，写入口全清） */
    private static final String DOMAIN_STANDARD = "expenseStandard";

    private final MdExpenseStandardMapper mapper;

    public PageResult<MdExpenseStandard> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdExpenseStandard> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdExpenseStandard::getStdName, keyword).or().like(MdExpenseStandard::getStdCode, keyword));
        }
        qw.orderByDesc(MdExpenseStandard::getId);
        Page<MdExpenseStandard> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(MdExpenseStandard e) {
        // 必填办结（与前端红星同一规则）；std_code 上有唯一索引 uk_tenant_std_code，先查重给业务提示而非抛 SQL 错
        AssertUtil.notBlank(e.getStdCode(), ErrorCode.PARAM_INVALID, "标准编码必填");
        AssertUtil.notBlank(e.getStdName(), ErrorCode.PARAM_INVALID, "标准名称必填");
        AssertUtil.notNull(e.getItemId(), ErrorCode.PARAM_INVALID, "费用类型必填");
        AssertUtil.notNull(e.getAmount(), ErrorCode.PARAM_INVALID, "标准金额必填");
        assertCodeUnique(e.getStdCode(), null);
        e.setId(null);
        mapper.insert(e);
        // 标准变更：同域 key（租户:md:expenseStandard:*）全清
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_STANDARD));
        return e.getId();
    }

    public void update(MdExpenseStandard e) {
        AssertUtil.notBlank(e.getStdName(), ErrorCode.PARAM_INVALID, "标准名称必填");
        if (StringUtils.hasText(e.getStdCode())) {
            assertCodeUnique(e.getStdCode(), e.getId());
        }
        mapper.updateById(e);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_STANDARD));
    }

    private void assertCodeUnique(String code, Long selfId) {
        Long n = mapper.selectCount(new LambdaQueryWrapper<MdExpenseStandard>()
                .eq(MdExpenseStandard::getStdCode, code)
                .ne(selfId != null, MdExpenseStandard::getId, selfId));
        AssertUtil.isTrue(n == null || n == 0, ErrorCode.PARAM_INVALID, "标准编码已存在: " + code);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_STANDARD));
    }
}
