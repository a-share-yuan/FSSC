package com.fssc.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.util.AssertUtil;
import com.fssc.framework.cache.TenantCache;
import com.fssc.framework.security.LoginUser;
import com.fssc.framework.security.UserContext;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.entity.MdSubject;
import com.fssc.masterdata.mapper.MdExpenseItemMapper;
import com.fssc.masterdata.mapper.MdSubjectMapper;
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
    private final MdSubjectMapper subjectMapper;

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
        assertSubjectUsable(e.getSubjectId(), e.getItemName());
        mapper.insert(e);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_TREE));
        return e.getId();
    }

    public void update(MdExpenseItem e) {
        // updateById 只写非空字段；仅当本次确实改到科目时校验，避免旧脏值把整单编辑卡死
        if (e.getSubjectId() != null) {
            assertSubjectUsable(e.getSubjectId(), e.getItemName());
        }
        mapper.updateById(e);
        TenantCache.evictPrefix(TenantCache.mdPrefix(DOMAIN_TREE));
    }

    /**
     * 费用项→会计科目映射的写侧校验：科目须存在、启用、末级（且实际无子节点）、属于本公司或租户共用。
     * <p>在保存时就拦，避免「配上了却不可记账」，直到制证阶段才暴露（EXP-VOUCHER-001 的根因之一）。
     * 科目为空允许保存：借款等项目不产生费用分录。
     */
    private void assertSubjectUsable(Long subjectId, String itemName) {
        if (subjectId == null) {
            return;
        }
        String label = StringUtils.hasText(itemName) ? "费用项目【" + itemName + "】" : "该费用项目";
        MdSubject s = subjectMapper.selectById(subjectId);
        AssertUtil.notNull(s, ErrorCode.PARAM_INVALID, label + "选择的会计科目不存在，请重新选择");
        AssertUtil.isTrue("NORMAL".equals(s.getStatus()), ErrorCode.PARAM_INVALID,
                label + "选择的会计科目「" + s.getSubjectCode() + " " + s.getSubjectName() + "」已停用，请选择启用状态的科目");
        long children = subjectMapper.countChildren(subjectId);
        AssertUtil.isTrue(s.getIsLeaf() != null && s.getIsLeaf() == 1 && children == 0, ErrorCode.PARAM_INVALID,
                label + "只能对应末级会计科目：当前选择「" + s.getSubjectCode() + " " + s.getSubjectName()
                        + "」下设明细，请选择具体明细科目");
        LoginUser lu = UserContext.get();
        Long companyId = lu == null ? null : lu.getCompanyId();
        AssertUtil.isTrue(companyId == null || s.getCompanyId() == null || companyId.equals(s.getCompanyId()),
                ErrorCode.PARAM_INVALID,
                label + "不能使用其他公司的会计科目「" + s.getSubjectCode() + " " + s.getSubjectName()
                        + "」，请选择本公司科目或租户共用科目");
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
