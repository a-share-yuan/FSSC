package com.fssc.ai.resolve;

import com.fssc.framework.security.LoginUser;
import com.fssc.masterdata.entity.MdExpenseItem;
import com.fssc.masterdata.service.ExpenseItemService;
import com.fssc.system.entity.SysCompany;
import com.fssc.system.entity.SysDept;
import com.fssc.system.service.CompanyService;
import com.fssc.system.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体解析：把用户话里的"公司/部门/费用项"落到主数据 ID。
 *
 * <p>规则：文本包含主数据名称/编码即视为候选（不做模糊臆测）；
 * 多命中返回候选列表由人选择，零命中明确说"没找到"——<b>绝不自动取第一个</b>。
 * 数据源与页面下拉同源（CompanyService/DeptService/ExpenseItemService），可见范围照常受租户与数据权限约束。
 */
@Component
@RequiredArgsConstructor
public class EntityResolver {

    private static final int MAX_CANDIDATES = 5;

    private final CompanyService companyService;
    private final DeptService deptService;
    private final ExpenseItemService expenseItemService;

    public record Entity(String type, Long id, String name, String code, String extra) {
    }

    public record Resolution(String type, List<Entity> candidates) {
        public boolean none() { return candidates.isEmpty(); }
        public boolean unique() { return candidates.size() == 1; }
        public boolean ambiguous() { return candidates.size() > 1; }
        public Entity first() { return candidates.get(0); }
        public String names() {
            return String.join("、", candidates.stream().map(Entity::name).toList());
        }
    }

    public List<Resolution> resolveInText(String text, LoginUser user) {
        List<Resolution> out = new ArrayList<>();
        if (text == null || text.isBlank() || user == null) {
            return out;
        }
        out.add(company(text, user));
        out.add(dept(text, user));
        out.add(expenseItem(text));
        return out;
    }

    private Resolution company(String text, LoginUser user) {
        List<SysCompany> visible = companyService.visibleForUser(companyService.tree(), null);
        List<Entity> hits = new ArrayList<>();
        for (SysCompany c : visible) {
            if (matches(text, c.getCompanyName(), 4) || matches(text, c.getCompanyCode(), 3)) {
                hits.add(new Entity("company", c.getId(), c.getCompanyName(), c.getCompanyCode(), null));
            }
        }
        return new Resolution("company", trim(hits));
    }

    private Resolution dept(String text, LoginUser user) {
        if (user.getCompanyId() == null) {
            return new Resolution("dept", List.of());
        }
        List<SysDept> all = deptService.treeByCompany(user.getCompanyId());
        List<Entity> hits = new ArrayList<>();
        for (SysDept d : all) {
            if (matches(text, d.getDeptName(), 2) || matches(text, d.getDeptCode(), 3)) {
                hits.add(new Entity("dept", d.getId(), d.getDeptName(), d.getDeptCode(), null));
            }
        }
        return new Resolution("dept", trim(hits));
    }

    private Resolution expenseItem(String text) {
        List<MdExpenseItem> all = expenseItemService.tree();
        List<Entity> hits = new ArrayList<>();
        for (MdExpenseItem it : all) {
            if (matches(text, it.getItemName(), 3) || matches(text, it.getItemCode(), 3)) {
                hits.add(new Entity("expense_item", it.getId(), it.getItemName(), it.getItemCode(),
                        it.getBillType()));
            }
        }
        return new Resolution("expense_item", trim(hits));
    }

    private static boolean matches(String text, String candidate, int minLen) {
        return candidate != null && candidate.trim().length() >= minLen && text.contains(candidate.trim());
    }

    /** 把"长名包含短名"的候选去掉（如同时命中「上海公司」与「上海公司研发中心」时只留长者）。 */
    private static List<Entity> trim(List<Entity> hits) {
        Map<Long, Entity> distinct = new LinkedHashMap<>();
        for (Entity e : hits) {
            distinct.putIfAbsent(e.id(), e);
        }
        List<Entity> list = new ArrayList<>(distinct.values());
        List<Entity> kept = new ArrayList<>();
        for (Entity e : list) {
            boolean covered = list.stream().anyMatch(o -> !o.id().equals(e.id())
                    && o.name() != null && e.name() != null && o.name().contains(e.name()));
            if (!covered) {
                kept.add(e);
            }
        }
        return kept.size() > MAX_CANDIDATES ? kept.subList(0, MAX_CANDIDATES) : kept;
    }

    /** 供预览展示：多命中时候选名列表。 */
    public static String candidatesText(Resolution r) {
        Set<String> names = new LinkedHashSet<>();
        for (Entity e : r.candidates()) {
            names.add(e.name() + (e.code() == null ? "" : "（" + e.code() + "）"));
        }
        return String.join("、", names);
    }
}
