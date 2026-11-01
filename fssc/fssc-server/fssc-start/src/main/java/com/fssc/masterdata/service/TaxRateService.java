// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.masterdata.entity.MdTaxRate;
import com.fssc.masterdata.mapper.MdTaxRateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 主数据 TaxRate 服务（带 tenant_id，租户拦截器自动隔离） */
@Service
@RequiredArgsConstructor
public class TaxRateService {

    private final MdTaxRateMapper mapper;

    public PageResult<MdTaxRate> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdTaxRate> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdTaxRate::getRateName, keyword).or().like(MdTaxRate::getRateCode, keyword));
        }
        qw.orderByDesc(MdTaxRate::getId);
        Page<MdTaxRate> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(MdTaxRate e) {
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        mapper.insert(e);
        return e.getId();
    }

    public void update(MdTaxRate e) {
        mapper.updateById(e);
    }

    public void enable(Long id, boolean enable) {
        MdTaxRate u = new MdTaxRate();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
