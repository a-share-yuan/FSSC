// SPDX-License-Identifier: Apache-2.0
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
 * 权限项依赖元数据（RA-D258a · A4 第 ① 层，2026-09-27 人工裁决第三轮）。
 *
 * <p>厂商形态：<b>查看是其余权限的前置权限</b>——「当查看设置为无权，则允许其余权限设置为无权或禁止，
 * 我方没有权限项档案（sys_menu 无依赖列，见 §七 请示的只读普查），故把依赖关系<b>声明在代码里</b>，
 * 三层共用同一份真值：①本类 ②角色授权保存期校验（{@code RoleService.assignMenus}）
 * ③端点运行时兜底（{@code ArchiveAccessService.assertApproverCanList}）。
 *
 * <p>逐条显式声明，不做"按码名猜前缀"的通用规则——猜出来的依赖会把无关权限码一起拦掉，
 * 且每条都要能指回一句厂商原文或一次现网取证。新增条目须带出处。
 */
public final class PermDependency {

    /** 权限码 → 授予它之前必须同时持有的前置权限码 */
    private static final Map<String, List<String>> DEPENDS = Map.of(
            // 现网取证：approve 与 list 持有人零交集 ⇒ 有 approve 的人列不出申请却能批准放行（D-DEC-258）
            "compliance:archiveaccess:approve", List.of("compliance:archiveaccess:list"),
            // R5-D-173：唯一持 op:bank:import 的角色（SHARING_SUPPORT，role_id=37）无 op:bank:list，
            // 导入成功后自读流水列表吃 4030（Y001767/user_id=3834 实证）；同权岗 FUND_STAFF/FUND_PAY_STAFF/CASHIER
            // 均「银行流水(108)+银行流水-查询(3695)」双持——读是写的最小配套，授导入必须带查看
            "op:bank:import", List.of("op:bank:list"));

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
