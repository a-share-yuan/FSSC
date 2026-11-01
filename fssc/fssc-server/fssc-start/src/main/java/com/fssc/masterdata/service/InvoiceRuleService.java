// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.framework.cache.TenantCache;
import com.fssc.masterdata.entity.MdInvoiceRule;
import com.fssc.masterdata.mapper.MdInvoiceRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 发票智能匹配规则服务（带 tenant_id，租户拦截器自动隔离）。
 * 报销页勾选发票时按商品/服务名称命中费用类型预填。
 */
@Service
@RequiredArgsConstructor
public class InvoiceRuleService {

    /** 缓存域：全量启用规则（高频只读，TTL 缓存，写入口失效） */
    private static final String DOMAIN_RULES = "invoiceRule";

    private final MdInvoiceRuleMapper mapper;

    public PageResult<MdInvoiceRule> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdInvoiceRule> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdInvoiceRule::getKeyword, keyword).or().like(MdInvoiceRule::getItemName, keyword));
        }
        qw.orderByAsc(MdInvoiceRule::getSort).orderByAsc(MdInvoiceRule::getId);
        Page<MdInvoiceRule> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 全量启用规则（按 sort 升序），进程内 TTL 缓存，调用方只读 */
    private List<MdInvoiceRule> enabledAll() {
        return TenantCache.get(TenantCache.mdKey(DOMAIN_RULES), () -> mapper.selectList(
                new LambdaQueryWrapper<MdInvoiceRule>()
                        .eq(MdInvoiceRule::getStatus, "NORMAL")
                        .orderByAsc(MdInvoiceRule::getSort).orderByAsc(MdInvoiceRule::getId)));
    }

    /**
     * 匹配：输入商品/服务名称，返回命中的规则（含费用类型），未命中返回 null。
     * 优先级：精确(EXACT) 优先于 包含(CONTAINS)；同精度内按 sort 升序取最先命中。
     * 报销页未命中时费用类型留空待填。
     */
    public MdInvoiceRule match(String commodityName) {
        if (!StringUtils.hasText(commodityName)) {
            return null;
        }
        String name = commodityName.trim();
        List<MdInvoiceRule> rules = enabledAll()
                .stream().filter(r -> r.getExpenseItemId() != null).toList();
        // 第一优先：精确命中
        for (MdInvoiceRule r : rules) {
            if ("EXACT".equals(r.getMatchType()) && containsAny(r.getKeyword(), name, true)) {
                return r;
            }
        }
        // 第二优先：包含命中（按 sort 升序）
        for (MdInvoiceRule r : rules) {
            if ("CONTAINS".equals(r.getMatchType()) && containsAny(r.getKeyword(), name, false)) {
                return r;
            }
        }
        return null;
    }

    /** keyword 以 / 分隔；exact=true 时任一词与目标相等，否则任一词被目标包含 */
    private boolean containsAny(String keyword, String target, boolean exact) {
        if (!StringUtils.hasText(keyword)) {
            return false;
        }
        String[] words = keyword.split("/");
        if (exact) {
            for (String w : words) {
                if (w.trim().equals(target)) {
                    return true;
                }
            }
        } else {
            for (String w : words) {
                if (StringUtils.hasText(w.trim()) && target.contains(w.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Long create(MdInvoiceRule e) {
        e.setId(null);
        if (e.getMatchType() == null) {
            e.setMatchType("CONTAINS");
        }
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        if (e.getSort() == null) {
            e.setSort(0);
        }
        mapper.insert(e);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_RULES));
        return e.getId();
    }

    public void update(MdInvoiceRule e) {
        // 用 UpdateWrapper 显式写字段：MyBatis-Plus updateById 默认忽略 null，
        // 会让「费用类型清空/解除绑定」不生效（expenseItemId 允许置空）。整对象更新，null 即清空。
        LambdaUpdateWrapper<MdInvoiceRule> uw = new LambdaUpdateWrapper<>();
        uw.eq(MdInvoiceRule::getId, e.getId());
        uw.set(MdInvoiceRule::getKeyword, e.getKeyword());
        uw.set(MdInvoiceRule::getMatchType, e.getMatchType());
        uw.set(MdInvoiceRule::getExpenseItemId, e.getExpenseItemId());
        uw.set(MdInvoiceRule::getItemName, e.getItemName());
        uw.set(MdInvoiceRule::getSort, e.getSort());
        uw.set(MdInvoiceRule::getStatus, e.getStatus());
        uw.set(MdInvoiceRule::getRemark, e.getRemark());
        mapper.update(null, uw);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_RULES));
    }

    public void enable(Long id, boolean enable) {
        MdInvoiceRule u = new MdInvoiceRule();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_RULES));
    }

    public void delete(Long id) {
        mapper.deleteById(id);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_RULES));
    }
}