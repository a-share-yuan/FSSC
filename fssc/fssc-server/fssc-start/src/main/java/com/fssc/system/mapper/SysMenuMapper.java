// SPDX-License-Identifier: Apache-2.0
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

    /** 用户经角色关联的菜单ID集合（直接角色 ∪ 用户组派生角色；租户条件由拦截器注入） */
    @Select("select distinct rm.menu_id from sys_role_menu rm " +
            "where rm.role_id in (" +
            "  select role_id from sys_user_role where user_id = #{userId} " +
            "  union " +
            "  select gr.role_id from sys_group_role gr " +
            "    join sys_user_group_rel ugr on ugr.group_id = gr.group_id " +
            "    where ugr.user_id = #{userId}" +
            ")")
    List<Long> selectMenuIdsByUser(@Param("userId") Long userId);

    /** 角色的菜单ID集合（角色管理授权用） */
    @Select("select menu_id from sys_role_menu where role_id = #{roleId}")
    List<Long> selectMenuIdsByRole(@Param("roleId") Long roleId);

    /**
     * 按角色ID集合取去重权限码（菜单 perms 字段，逗号分隔；租户条件由拦截器注入）
     * <p>★D-DEC-468：与 {@code SysUserMapper.selectPerms} 取码口径对齐＝只认启用的功能节点
     * （{@code status=1 且 del_flag=0}）。此前本查询连 status 都不看，SoD 职责分离判定
     * 与实际登录权限面双口径（软删节点的码仍参与冲突判定）。
     * <p>上线前置闸同 D-DEC-467／D-DEC-468 记录，须先证明「有持有人却无活载体的码＝0 行」。
     */
    @Select("<script>select distinct m.perms from sys_menu m " +
            "join sys_role_menu rm on m.id = rm.menu_id " +
            "where m.perms is not null and m.perms &lt;&gt; '' and m.status = 1 and m.del_flag = 0 and rm.role_id in " +
            "<foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<String> selectPermsByRoleIds(@Param("roleIds") List<Long> roleIds);
}
