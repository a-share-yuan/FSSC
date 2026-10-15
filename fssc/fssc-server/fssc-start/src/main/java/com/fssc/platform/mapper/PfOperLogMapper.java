package com.fssc.platform.mapper;

import com.fssc.platform.entity.PfOperLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台操作日志（pf_oper_log）。
 * 【审计铁律】：只提供 insert，不提供任何 delete —— 日志不可删。
 */
@Mapper
public interface PfOperLogMapper {

    @Insert("insert into pf_oper_log(module, oper_type, oper_desc, method, request_method, request_uri, " +
            "request_param, response_data, oper_user_id, oper_username, oper_ip, oper_location, " +
            "status, error_msg, cost_ms, oper_time) " +
            "values(#{module}, #{operType}, #{operDesc}, #{method}, #{requestMethod}, #{requestUri}, " +
            "#{requestParam}, #{responseData}, #{operUserId}, #{operUsername}, #{operIp}, #{operLocation}, " +
            "#{status}, #{errorMsg}, #{costMs}, #{operTime})")
    int insert(PfOperLog log);
}
