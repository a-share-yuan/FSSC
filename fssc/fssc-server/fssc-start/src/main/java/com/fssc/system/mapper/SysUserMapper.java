// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户用户（sys_user，带 tenant_id）。
 * 登录时在 TenantContext.callAs(tenantId) 内执行，
 * 租户拦截器会为 sys_user/sys_role/sys_user_role/sys_menu/sys_role_menu 自动追加 tenant_id 条件，
 * SQL 里【严禁手写 tenant_id】。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按登录账号查用户（租户条件由拦截器注入） */
    @Select("select * from sys_user where user_name = #{userName}")
    SysUser findByUserName(@Param("userName") String userName);

    /** 用户数据权限：多角色取最宽（min），查不到返回 null（外层兜底 5） */
    @Select("select min(r.data_scope) from sys_role r " +
            "join sys_user_role ur on r.id = ur.role_id " +
            "where ur.user_id = #{userId} and r.status = 'NORMAL'")
    Integer selectDataScope(@Param("userId") Long userId);

    /** 用户角色码列表（直接角色 ∪ 用户组派生角色；租户条件由拦截器注入 sys_role/sys_user_role/sys_group_role/sys_user_group_rel） */
    @Select("select distinct r.role_code from sys_role r " +
            "join (" +
            "  select role_id from sys_user_role where user_id = #{userId} " +
            "  union " +
            "  select gr.role_id from sys_group_role gr " +
            "    join sys_user_group_rel ugr on ugr.group_id = gr.group_id " +
            "    where ugr.user_id = #{userId}" +
            ") x on r.id = x.role_id " +
            "where r.status = 'NORMAL' and r.role_code is not null")
    List<String> selectRoleCodes(@Param("userId") Long userId);

    /**
     * 用户权限码集合（菜单+按钮 perms，去重；直接角色 ∪ 用户组派生角色）
     * <p>★D-DEC-468：追加 {@code m.del_flag = 0} —— 停用的功能节点不再参与鉴权。
     * 口径＝权限项必须挂在启用的功能节点上，授权与节点生命周期一致；
     * 此前只有 status=1 条件，软删（del_flag=1）菜单仍持续供码，导致
     * 「树上看不见→角色保存即被摘除、但登录仍生效」的双口径漂移（D-DEC-467 普查＝
     * 127 枚软删载体挂 736 条授权）。
     * <p>上线前置闸＝必须先跑「活载体归位」数据腿（D-DEC-467），并用
     * {@code 临时脚本/菜单治理/D-DEC-468_部署前闸.sql} 证明「有持有人却无活载体的码＝0 行」，
     * 否则本条件会瞬时缩权。
     */
    @Select("select distinct m.perms from sys_menu m " +
            "join sys_role_menu rm on m.id = rm.menu_id " +
            "where m.perms is not null and m.perms <> '' and m.status = 1 and m.del_flag = 0 " +
            "  and rm.role_id in (" +
            "    select role_id from sys_user_role where user_id = #{userId} " +
            "    union " +
            "    select gr.role_id from sys_group_role gr " +
            "      join sys_user_group_rel ugr on ugr.group_id = gr.group_id " +
            "      where ugr.user_id = #{userId}" +
            ")")
    List<String> selectPerms(@Param("userId") Long userId);

    /** 用户角色名称列表（直接角色 ∪ 用户组派生角色） */
    @Select("select r.role_name from sys_role r " +
            "join (" +
            "  select role_id from sys_user_role where user_id = #{userId} " +
            "  union " +
            "  select gr.role_id from sys_group_role gr " +
            "    join sys_user_group_rel ugr on ugr.group_id = gr.group_id " +
            "    where ugr.user_id = #{userId}" +
            ") x on r.id = x.role_id " +
            "where r.status = 'NORMAL'")
    List<String> selectRoles(@Param("userId") Long userId);

    /**
     * G2 传阅（2026-09-23）：按角色编码集合反查<b>在职</b>用户ID。
     *
     * @deprecated 2026-10-15 起不再使用（唯一调用方 {@code WfCcService} 已改为走
     * {@code NodeOrgResolver.listRoleCandidatesInScope} 按 {@code org_scope} + 单据公司收敛）。
     * 本方法<b>无任何 company/scope 条件</b>，直接调用会造成「审批收敛、抄送撒全网」的跨公司过度知会
     * （不变量 II）。保留声明仅为历史追溯，<b>禁止在新代码中调用</b>。
     */
    @Deprecated
    @Select("<script>"
            + "select distinct u.id from sys_user u join ("
            + "  select ur.user_id as uid from sys_user_role ur "
            + "    join sys_role r on r.id = ur.role_id "
            + "    where r.status = 'NORMAL' and r.role_code in "
            + "      <foreach collection='roleCodes' item='rc' open='(' separator=',' close=')'>#{rc}</foreach> "
            + "  union "
            + "  select ugr.user_id as uid from sys_group_role gr "
            + "    join sys_user_group_rel ugr on ugr.group_id = gr.group_id "
            + "    join sys_role r2 on r2.id = gr.role_id "
            + "    where r2.status = 'NORMAL' and r2.role_code in "
            + "      <foreach collection='roleCodes' item='rc' open='(' separator=',' close=')'>#{rc}</foreach> "
            + ") x on x.uid = u.id "
            + "where u.status = 'NORMAL'"
            + "</script>")
    List<Long> selectUserIdsByRoleCodes(@Param("roleCodes") List<String> roleCodes);

    /** 物理删除（绕过 @TableLogic，供管理端删除后释放唯一键） */
    @org.apache.ibatis.annotations.Delete("DELETE FROM `sys_user` WHERE id = #{id}")
    int hardDelete(@org.apache.ibatis.annotations.Param("id") Long id);
}
