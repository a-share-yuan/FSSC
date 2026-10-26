// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.masterdata.entity.MdCity;
import com.fssc.masterdata.mapper.MdCityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 主数据 City 服务（带 tenant_id，租户拦截器自动隔离） */
@Service
@RequiredArgsConstructor
public class CityService {

    private final MdCityMapper mapper;

    public PageResult<MdCity> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdCity> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdCity::getCityName, keyword).or().like(MdCity::getCityCode, keyword));
        }
        qw.orderByDesc(MdCity::getId);
        Page<MdCity> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(MdCity e) {
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        mapper.insert(e);
        return e.getId();
    }

    public void update(MdCity e) {
        mapper.updateById(e);
    }

    public void enable(Long id, boolean enable) {
        MdCity u = new MdCity();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /**
     * 下拉专用：返回全部 NORMAL 城市（不分页、不过滤关键字）。
     * 用于单据发起页的城市选择，避免复用带分页的 /page 接口导致低 id 城市被分页截断，
     * 同时只暴露启用中的城市（DISABLED 不进下拉）。排序：sort 升序 → id 升序。
     */
    public List<MdCity> listNormal() {
        LambdaQueryWrapper<MdCity> qw = new LambdaQueryWrapper<>();
        qw.eq(MdCity::getStatus, "NORMAL");
        qw.orderByAsc(MdCity::getSort).orderByAsc(MdCity::getId);
        return mapper.selectList(qw);
    }
}
