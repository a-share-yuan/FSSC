// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户端菜单（sys_menu，全局表无 tenant_id）。
 * 菜单授权经 sys_role_menu（带 tenant_id）落地，查询在租户上下文中执行由拦截器注入。
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 用户经角色关联的菜单ID集合（直接角色 ∪ 用户组派生角色；租户条件由拦截器注入）
     * <p>A-01a/A-01b：与 {@code SysUserMapper.selectPerms} 同一规则 —— 只认启用角色
     * 挂在启用且未软删菜单上的授权，避免「菜单树已过滤、勾选回显仍带出死节点」。
     * <p>10-29 改写：原写法是 {@code rm.role_id in (子查询 union 子查询)}，优化器把它当依赖子查询，
     * 对 r×m 的连接结果（实测 66 角色 × 600 菜单＝39,600 行）逐行重算 union ⇒ 单发 221 ms、
     * 每次进页面拉菜单树都吃这一发（占该端点 SQL 总耗 73%）。改成两条分支各自 join 再 union，
     * union 只物化一次；同一用户在改前/改后的菜单ID集合 MD5 一致。
     */
    @Select("select distinct rm.menu_id from sys_user_role ur " +
            "join sys_role_menu rm on rm.role_id = ur.role_id and rm.tenant_id = ur.tenant_id " +
            "join sys_menu m on m.id = rm.menu_id and m.status = 1 and m.del_flag = 0 " +
            "join sys_role r on r.id = rm.role_id and r.status = 'NORMAL' and r.tenant_id = ur.tenant_id " +
            "where ur.user_id = #{userId} " +
            "union " +
            "select distinct rm.menu_id from sys_user_group_rel ugr " +
            "join sys_group_role gr on gr.group_id = ugr.group_id and gr.tenant_id = ugr.tenant_id " +
            "join sys_role_menu rm on rm.role_id = gr.role_id and rm.tenant_id = ugr.tenant_id " +
            "join sys_menu m on m.id = rm.menu_id and m.status = 1 and m.del_flag = 0 " +
            "join sys_role r on r.id = rm.role_id and r.status = 'NORMAL' and r.tenant_id = ugr.tenant_id " +
            "where ugr.user_id = #{userId}")
    List<Long> selectMenuIdsByUser(@Param("userId") Long userId);

    /** 角色的菜单ID集合（角色管理授权用） */
    @Select("select menu_id from sys_role_menu where role_id = #{roleId}")
    List<Long> selectMenuIdsByRole(@Param("roleId") Long roleId);

    /**
     * 按角色ID集合取去重权限码（菜单 perms 字段，逗号分隔；租户条件由拦截器注入）
     * <p>与 {@code SysUserMapper.selectPerms} 取码规则对齐＝只认启用的功能节点
     * （{@code status=1 且 del_flag=0}）。本查询连 status 都不看的话，SoD 职责分离判定
     * 与实际登录权限面双规则（软删节点的码仍参与冲突判定）。
     * <p>上线前置校验同 ／记录，须先证明「有持有人却无活载体的码＝0 行」。
     */
    @Select("<script>select distinct m.perms from sys_menu m " +
            "join sys_role_menu rm on m.id = rm.menu_id " +
            "where m.perms is not null and m.perms &lt;&gt; '' and m.status = 1 and m.del_flag = 0 and rm.role_id in " +
            "<foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<String> selectPermsByRoleIds(@Param("roleIds") List<Long> roleIds);
}
