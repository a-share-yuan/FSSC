package com.fssc.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fssc.system.entity.SysFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 文件（带 tenant_id） */
@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {

    /** 按租户统计文件总大小（字节） */
    @Select("select sum(file_size) from sys_file where tenant_id = #{tenantId} and del_flag = 0")
    Long selectSumFileSizeByTenant(@Param("tenantId") Long tenantId);
}
