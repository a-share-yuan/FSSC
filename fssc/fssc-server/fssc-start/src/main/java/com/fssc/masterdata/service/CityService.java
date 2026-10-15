package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.masterdata.entity.MdCity;
import com.fssc.masterdata.mapper.MdCityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
}
