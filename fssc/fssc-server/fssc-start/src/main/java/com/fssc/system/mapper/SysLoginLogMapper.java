// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fssc.system.entity.SysLoginLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 租户登录日志（sys_login_log，带 tenant_id）。只增不删。 */
@Mapper
public interface SysLoginLogMapper {

    @Insert("insert into sys_login_log(tenant_id, user_name, ipaddr, login_location, browser, os, login_type, status, msg, login_time) " +
            "values(#{tenantId}, #{userName}, #{ipaddr}, #{loginLocation}, #{browser}, #{os}, #{loginType}, #{status}, #{msg}, #{loginTime})")
    int insert(SysLoginLog log);

    /**
     * 登录日志分页查询（只读，按 id 倒序）。
     * keyword 匹配用户名/IP；status 0=成功、1=失败；租户隔离由框架租户拦截器自动注入。
     */
    @Select("<script>select * from sys_login_log <where>" +
            "<if test='keyword != null and keyword != \"\"'>and (user_name like concat('%', #{keyword}, '%') or ipaddr like concat('%', #{keyword}, '%'))</if>" +
            "<if test='status != null'>and status = #{status}</if>" +
            "</where> order by id desc</script>")
    IPage<SysLoginLog> searchPage(IPage<SysLoginLog> page, @Param("keyword") String keyword, @Param("status") Integer status);
}
