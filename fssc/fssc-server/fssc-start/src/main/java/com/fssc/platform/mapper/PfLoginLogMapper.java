// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.platform.mapper;

import com.fssc.platform.entity.PfLoginLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 平台登录日志（pf_login_log）。只增不删。 */
@Mapper
public interface PfLoginLogMapper {

    @Insert("insert into pf_login_log(username, login_ip, login_location, browser, os, login_type, status, msg, login_time) " +
            "values(#{username}, #{loginIp}, #{loginLocation}, #{browser}, #{os}, #{loginType}, #{status}, #{msg}, #{loginTime})")
    int insert(PfLoginLog log);
}
