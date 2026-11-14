package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fssc.system.entity.SysOperLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户操作日志（sys_oper_log，带 tenant_id）。
 * 【审计铁律】：只提供 insert/查询，不提供 update/delete；tenant_id 由 MetaObjectHandler 从 TenantContext 填充。
 *
 * <p>sys_oper_log 25 个月分区、PK=(id, oper_time)，按时间排序的
 * 分页（order by id desc / oper_time desc）在最左列不匹配时走全分区扫＋filesort；当前行量小，
 * 不构成缺陷。行量到十万级再对
 * `ORDER BY <时间> DESC LIMIT` 端点建 (tenant_id, oper_time) 覆盖排序索引。</p>
 */
@Mapper
public interface SysOperLogMapper {

    @Insert("insert into sys_oper_log(tenant_id, trace_id, title, business_type, method, request_method, oper_url, " +
            "oper_ip, oper_location, oper_user_id, oper_user_name, oper_dept_name, " +
            "oper_param, json_result, status, error_msg, cost_time, oper_time) " +
            "values(#{tenantId}, #{traceId}, #{title}, #{businessType}, #{method}, #{requestMethod}, #{operUrl}, " +
            "#{operIp}, #{operLocation}, #{operUserId}, #{operUserName}, #{operDeptName}, " +
            "#{operParam}, #{jsonResult}, #{status}, #{errorMsg}, #{costTime}, #{operTime})")
    int insert(SysOperLog log);

    /** 日志真分页查询（IPage 触发 MP 分页插件；租户拦截器自动注入 tenant_id；审计表不提供删除） */
    @Select("<script>select * from sys_oper_log " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>and (title like concat('%',#{keyword},'%') " +
            "or oper_user_name like concat('%',#{keyword},'%') or oper_url like concat('%',#{keyword},'%'))</if> " +
            "<if test='businessType != null and businessType != \"\"'>and business_type = #{businessType}</if> " +
            "<if test='status != null'>and status = #{status}</if> " +
            "</where> order by id desc</script>")
    IPage<SysOperLog> searchPage(
            IPage<SysOperLog> page,
            @Param("keyword") String keyword,
            @Param("businessType") String businessType,
            @Param("status") Integer status);

    /**
     * 执行结果视图：按标题倒序取最近 {@code limit} 条失败事件。
     * 只读——本 Mapper 的审计铁律仍是「只提供 insert/查询」；tenant_id 由租户拦截器注入。
     * limit 是扫描上限而非精确规则，调用方须把 scanned/truncated 如实回给界面。
     */
    @Select("select id, tenant_id, title, business_type, method, oper_user_id, oper_user_name, " +
            "status, error_msg, oper_time from sys_oper_log " +
            "where title = #{title} order by id desc limit #{limit}")
    List<SysOperLog> selectByTitleRecent(@Param("title") String title, @Param("limit") int limit);
}
