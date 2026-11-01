// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.security;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表业务域（报表权限整改：报表权限 = 业务域权限的投影）。
 *
 * <p>每个报表归属唯一业务域；域判据码 = 该域核心业务查询权限码。
 * 用户在某个域没有业务权限（角色权限集不含任何判据码），则该域报表：
 * <ul>
 *   <li>授权侧：{@code RoleService.assignMenus} 卡点拒绝授予该域报表菜单；</li>
 *   <li>接口侧：{@code @ReportDomain("EXP")} + {@link ReportDomainAspect} 运行期拦截。</li>
 * </ul>
 *
 * <p>例外：持有 {@code report:audit:all}（报表审计阅示，挂于内审/董事会等监督岗的
 * 隐藏 F 型菜单）视为全域放行；白名单角色进入季度权限复核清单。
 */
@Getter
public enum ReportDomain {

    EXP("EXP", "费用", Arrays.asList("exp:bill:list", "exp:bill:audit", "exp:share:list")),
    AR("AR", "应收", Arrays.asList("receivable:list")),
    P2P("P2P", "采购应付", Arrays.asList("po:order:list", "inv:list", "pay:match:list", "pay:recon:list")),
    CASH("CASH", "资金", Arrays.asList("op:pay:list", "treasury:fundplan:list", "treasury:creditbill:list",
            "bank:reconcile:list", "op:bank:list")),
    ASSET("ASSET", "资产", Arrays.asList("asset:list", "inventory:list")),
    GL("GL", "总账", Arrays.asList("gl:entry:list", "voucher:list:page", "md:subject:list")),
    BUDGET("BUDGET", "预算", Arrays.asList("budget:scheme:list", "budget:adjust:list",
            "budget:occupy:list", "budget:item:list")),
    CONTRACT("CONTRACT", "合同", Arrays.asList("contract:list")),
    SHARING("SHARING", "共享运营", Arrays.asList("sharing:task:list", "op:quality:list", "op:archive:list")),
    APPROVAL("APPROVAL", "审批", Arrays.asList("exp:bill:audit", "sharing:task:list")),
    IMAGE("IMAGE", "影像", Arrays.asList("image:batch:list", "image:review")),
    TAX("TAX", "税务", Arrays.asList("gl:taxrefund:list", "inv:certify", "gl:taxprov:list"));

    /** 审计阅示通配码：白名单监督岗持有即全域放行（配合季度复核） */
    public static final String AUDIT_ALL = "report:audit:all";

    private final String key;
    private final String label;
    private final List<String> judgeCodes;

    ReportDomain(String key, String label, List<String> judgeCodes) {
        this.key = key;
        this.label = label;
        this.judgeCodes = Collections.unmodifiableList(judgeCodes);
    }

    /** 判定权限集是否命中该域（任一判据码命中即视为持有该域业务权限） */
    public boolean hit(Set<String> perms) {
        if (perms == null || perms.isEmpty()) {
            return false;
        }
        for (String c : judgeCodes) {
            if (perms.contains(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 接口侧读判定：业务判据码 ∪ 域报表查看码（report:&lt;key&gt;:view）。
     * <p>域报表查看码经授权流程授予（如管理层阅示），视为该域报表的合法读凭证；
     * 仅用于 {@code ReportDomainAspect} 接口拦截，<b>不用于</b> assignMenus 授权卡点
     * （卡点须用 {@link #hit}，否则报表菜单自身域码会循环通过导致卡点失效）。
     */
    public boolean hitForRead(Set<String> perms) {
        if (hit(perms)) {
            return true;
        }
        return perms != null && perms.contains("report:" + key.toLowerCase() + ":view");
    }

    /** 按域 key 解析枚举；未知 key 抛 IllegalArgumentException */
    public static ReportDomain of(String key) {
        for (ReportDomain d : values()) {
            if (d.key.equalsIgnoreCase(key)) {
                return d;
            }
        }
        throw new IllegalArgumentException("未知报表业务域: " + key);
    }

    /** key → 中文名（用于提示语） */
    public static Map<String, String> labelMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ReportDomain d : values()) {
            m.put(d.key, d.label);
        }
        return m;
    }
}
