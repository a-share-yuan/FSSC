package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysGroupRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysGroupRoleMapper extends BaseMapper<SysGroupRole> {

    /** 用户经其所属用户组派生出的角色ID集合（租户条件由拦截器注入 sys_group_role/sys_user_group_rel） */
    @Select("select gr.role_id from sys_group_role gr " +
            "join sys_user_group_rel ugr on ugr.group_id = gr.group_id " +
            "where ugr.user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
}
