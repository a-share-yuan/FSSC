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
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 主数据 Currency 服务（带 tenant_id，租户拦截器自动隔离）
 * <p>P1-1：全量列表缓存（币种高频只读，变更很少） */
@Service
@RequiredArgsConstructor
public class CurrencyService {

    /** 缓存域：币种全量列表（高频只读，如币种下拉框每次报价都刷新） */
    private static final String DOMAIN_LIST = "currencyList";

    /** 下拉选项投影（免权限通道专用，字段最小化）。record 不可变，天然线程安全，适合直接进缓存。 */
    public record CurrencyOption(String currencyCode, String currencyName, String symbol,
                                 Integer decimalDigit, Integer isBase, Integer sort) {}

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

    /**
     * 【2026-10-18 币种免权限通道的数据源】币种下拉选项。
     *
     * 与 {@link #page} 的三处刻意差异：
     *   ① **独立缓存 key** {@code :currency:options} —— 不能复用 page 的 {@code :currency:list}：
     *      同 key 下缓存值类型从 PageResult 变成 List，Spring Cache 命中时会直接 ClassCastException；
     *   ② **只取启用项**（status=NORMAL）：停用币种若仍出现在下拉，会造成"下拉能选、提交被拒"；
     *      del_flag 由 {@code @TableLogic} 自动追加过滤，无需手写；
     *   ③ **返回投影 record 而非实体**：不把 remark / 审计列 / createBy 外泄给免权限通道。
     */
    @Cacheable(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:options'")
    public List<CurrencyOption> listOptions() {
        List<MdCurrency> list = mapper.selectList(new LambdaQueryWrapper<MdCurrency>()
                .eq(MdCurrency::getStatus, "NORMAL")
                .orderByAsc(MdCurrency::getSort)
                .orderByAsc(MdCurrency::getId));
        return list.stream().map(c -> new CurrencyOption(c.getCurrencyCode(), c.getCurrencyName(),
                c.getSymbol(), c.getDecimalDigit(), c.getIsBase(), c.getSort())).toList();
    }

    /** 币种写操作后同时失效「管理列表」与「免权限下拉」两份缓存。 */
    @Caching(evict = {
            @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:list'"),
            @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:options'")})
    public Long create(MdCurrency e) {
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        mapper.insert(e);
        return e.getId();
    }

    /** @see #create */
    @Caching(evict = {
            @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:list'"),
            @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:options'")})
    public void update(MdCurrency e) {
        mapper.updateById(e);
    }

    /** @see #create */
    @Caching(evict = {
            @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:list'"),
            @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:options'")})
    public void enable(Long id, boolean enable) {
        MdCurrency u = new MdCurrency();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
    }

    /** @see #create */
    @Caching(evict = {
            @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:list'"),
            @CacheEvict(value = "masterData", key = "T(com.fssc.framework.tenant.TenantContext).getTenantId() + ':currency:options'")})
    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
