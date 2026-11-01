// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.PageResult;
import com.fssc.framework.cache.TenantCache;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.List;

/** 主数据 ExpenseItem 服务（带 tenant_id，租户拦截器自动隔离） */
@Service
@RequiredArgsConstructor
public class ExpenseItemService {

    /** 缓存域：费用项树（210 节点级高频只读，进程内 TTL 缓存） */
    private static final String DOMAIN_TREE = "expenseItemTree";

    private final MdExpenseItemMapper mapper;

    public PageResult<MdExpenseItem> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MdExpenseItem> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(MdExpenseItem::getItemName, keyword).or().like(MdExpenseItem::getItemCode, keyword));
        }
        qw.orderByDesc(MdExpenseItem::getId);
        Page<MdExpenseItem> p = mapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 树形数据：按祖先路径+排序返回全量（前端组树）。进程内 TTL 缓存，调用方只读。 */
    public List<MdExpenseItem> tree() {
        return TenantCache.get(TenantCache.mdKey(DOMAIN_TREE), () -> mapper.selectList(
                new LambdaQueryWrapper<MdExpenseItem>()
                        // 过滤 itemName 为空/空白/未命名的脏数据，避免前端下拉出现空白选项
                        .isNotNull(MdExpenseItem::getItemName)
                        .ne(MdExpenseItem::getItemName, "")
                        .notLike(MdExpenseItem::getItemName, "（未命名）")
                        .orderByAsc(MdExpenseItem::getAncestors).orderByAsc(MdExpenseItem::getSort)));
    }
    public Long create(MdExpenseItem e) {
        e.setId(null);
        if (e.getStatus() == null) {
            e.setStatus("NORMAL");
        }
        mapper.insert(e);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_TREE));
        return e.getId();
    }

    public void update(MdExpenseItem e) {
        mapper.updateById(e);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_TREE));
    }

    public void enable(Long id, boolean enable) {
        MdExpenseItem u = new MdExpenseItem();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        mapper.updateById(u);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_TREE));
    }

    public void delete(Long id) {
        mapper.deleteById(id);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_TREE));
    }
}
