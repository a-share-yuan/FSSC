package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.framework.cache.TenantCache;
import com.fssc.masterdata.entity.MdCurrency;
import com.fssc.masterdata.mapper.MdCurrencyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 主数据 Currency 服务（带 tenant_id，租户拦截器自动隔离）
 * <p>P1-1：全量列表缓存（币种高频只读，变更很少） */
@Service
@RequiredArgsConstructor
public class CurrencyService {

    /** 缓存域：币种全量列表（高频只读，如币种下拉框每次报价都刷新） */
    private static final String DOMAIN_LIST = "currencyList";

    private final MdCurrencyMapper mapper;

    /** 全量列表（页码固定 pageSize=200 拉全量，用于下拉选项）。P1-1：Caffeine 缓存 5min TTL。 */
    @Cacheable(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:list'")
    public PageResult<MdCurrency> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdCurrency> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdCurrency::getCurrencyName, keyword).or().like(MdCurrency::getCurrencyCode, keyword));
        }
        qw.orderByDesc(MdCurrency::getId);
        Page<MdCurrency> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:list'")
    public Long create(MdCurrency e) {
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        mapper.insert(e);
        return e.getId();
    }

    @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:list'")
    public void update(MdCurrency e) {
        mapper.updateById(e);
    }

    @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:list'")
    public void enable(Long id, boolean enable) {
        MdCurrency u = new MdCurrency();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
    }

    @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:list'")
    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
