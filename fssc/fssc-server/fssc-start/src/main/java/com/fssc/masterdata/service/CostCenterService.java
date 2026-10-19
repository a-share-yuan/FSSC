// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.util.AssertUtil;
import com.fssc.framework.cache.TenantCache;
import com.fssc.masterdata.entity.MdCostCenter;
import com.fssc.masterdata.mapper.MdCostCenterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 主数据 CostCenter 服务（带 tenant_id，租户拦截器自动隔离）
 * <p>P1-1：tree() 全量列表缓存（高频只读，变更很少） */
@Service
@RequiredArgsConstructor
public class CostCenterService {

    private final MdCostCenterMapper mapper;

    public PageResult<MdCostCenter> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdCostCenter> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdCostCenter::getCcName, keyword).or().like(MdCostCenter::getCcCode, keyword));
        }
        qw.orderByDesc(MdCostCenter::getId);
        Page<MdCostCenter> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 树形数据：按编码排序返回全量（前端按 parentId 组树）。P1-1：Caffeine 缓存 5min TTL。 */
    @Cacheable(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':costCenter:tree'")
    public List<MdCostCenter> tree() {
        return mapper.selectList(
                new LambdaQueryWrapper<MdCostCenter>()
                        .orderByAsc(MdCostCenter::getCcCode).orderByAsc(MdCostCenter::getSort));
    }

    @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':costCenter:tree'")
    public Long create(MdCostCenter e) {
        // 必填收口（批次 2C，与前端 MasterDataTree 的 costCenterRules 同口径）：编码/名称是人工维护的业务键
        AssertUtil.notBlank(e.getCcCode(), ErrorCode.PARAM_INVALID, "请输入编码");
        AssertUtil.notBlank(e.getCcName(), ErrorCode.PARAM_INVALID, "请输入名称");
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        mapper.insert(e);
        return e.getId();
    }

    @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':costCenter:tree'")
    public void update(MdCostCenter e) {
        // 整单更新（前端提交完整对象），故与 create 同口径拦空；enable() 走独立对象不受影响
        AssertUtil.notBlank(e.getCcCode(), ErrorCode.PARAM_INVALID, "请输入编码");
        AssertUtil.notBlank(e.getCcName(), ErrorCode.PARAM_INVALID, "请输入名称");
        mapper.updateById(e);
    }

    @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':costCenter:tree'")
    public void enable(Long id, boolean enable) {
        MdCostCenter u = new MdCostCenter();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
    }

    @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':costCenter:tree'")
    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
