package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.masterdata.entity.MdProject;
import com.fssc.masterdata.mapper.MdProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 主数据 Project 服务（带 tenant_id，租户拦截器自动隔离） */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final MdProjectMapper mapper;

    public PageResult<MdProject> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdProject> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdProject::getProjectName, keyword).or().like(MdProject::getProjectCode, keyword));
        }
        qw.orderByDesc(MdProject::getId);
        Page<MdProject> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(MdProject e) {
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        mapper.insert(e);
        return e.getId();
    }

    public void update(MdProject e) {
        mapper.updateById(e);
    }

    public void enable(Long id, boolean enable) {
        MdProject u = new MdProject();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
