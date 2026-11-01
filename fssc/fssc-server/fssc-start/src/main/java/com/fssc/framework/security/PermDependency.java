// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.framework.security;

import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限项依赖元数据。
 *
 * <p>规则形态：<b>查看是其余权限的前置权限</b>——「当查看设置为无权，则允许其余权限设置为无权或禁止，
 * 但<b>不能设置为有权</b>」（业务对象功能授权）；权限项自带"依赖信息，是指对其它权限项的依赖"
 * 元数据位（行业元数据规范）；「功能权限是指节点的查看和打开的权限」（开发指南）。
 * 我方没有权限项档案（sys_menu 无依赖列，见 §七 请示的只读普查），故把依赖关系<b>声明在代码里</b>，
 * 三层共用同一份真值：①本类 ②角色授权保存期校验（{@code RoleService.assignMenus}）
 * ③端点运行时兜底（{@code ArchiveAccessService.assertApproverCanList}）。
 *
 * <p>逐条显式声明，不做"按码名猜前缀"的通用规则——猜出来的依赖会把无关权限码一起拦掉，
 * 且每条都要能指回一句规则原文或一次现网核对。新增条目须带出处。
 */
public final class PermDependency {

    /** 权限码 → 授予它之前必须同时持有的前置权限码 */
    private static final Map<String, List<String>> DEPENDS = Map.of(
            // 现网核对：approve 与 list 持有人零交集 ⇒ 有 approve 的人列不出申请却能批准放行
            "compliance:archiveaccess:approve", List.of("compliance:archiveaccess:list"),
            // 唯一持 op:bank:import 的角色（SHARING_SUPPORT，role_id=37）无 op:bank:list，
            // 导入成功后自读流水列表吃 4030（Y001767/user_id=3834 实证）；同权岗 FUND_STAFF/FUND_PAY_STAFF/CASHIER
            // 均「银行流水(108)+银行流水-查询(3695)」双持——读是写的最小配套，授导入必须带查看
            // （「查看是其余权限的前置」同向：查看无权则其余权限不得有权）。
            "op:bank:import", List.of("op:bank:list"),
            // 银企对账读写分离——写端点（自动对账/手工匹配/收款认领/回单归档/余额调整）
            // 用执行码 bank:reconcile:execute，查询仍用 bank:reconcile:list。
            // 执行是查看的后置：能执行对账的人必然要先能查对账结果（同向规则）。
            "bank:reconcile:execute", List.of("bank:reconcile:list"));

    private PermDependency() {
    }

    public static List<String> requiredOf(String perm) {
        List<String> dep = DEPENDS.get(perm);
        return dep == null ? List.of() : dep;
    }

    /**
     * 配置期校验：给定"角色授权保存后的权限码全集"，逐条核对依赖是否同批授予。
     * 违反则整单拒绝并列出全部违规项（避免改一次报一次），与 {@code assertReportDomainProjection} 同一风格。
     */
    public static void assertGrantClosure(Set<String> futurePerms) {
        if (futurePerms == null || futurePerms.isEmpty()) {
            return;
        }
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : DEPENDS.entrySet()) {
            if (!futurePerms.contains(e.getKey())) {
                continue;
            }
            for (String need : e.getValue()) {
                if (!futurePerms.contains(need)) {
                    violations.add(e.getKey() + " 需同时授予前置权限 " + need);
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED,
                    "权限项依赖不闭合（查看是其余权限的前置）：" + String.join("；", violations));
        }
    }

    /** 运行时兜底：登录人持有 perm，就必须持有其全部前置权限（通配码按 LoginUser.hasPerm 放行） */
    public static void assertHolds(LoginUser user, String perm) {
        List<String> need = requiredOf(perm);
        if (user == null || need.isEmpty()) {
            return;
        }
        Set<String> missing = new LinkedHashSet<>();
        for (String dep : need) {
            if (!user.hasPerm(dep)) {
                missing.add(dep);
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.PERMISSION_DENIED,
                    "执行 " + perm + " 需同时具备前置权限：" + String.join("、", missing));
        }
    }
}
